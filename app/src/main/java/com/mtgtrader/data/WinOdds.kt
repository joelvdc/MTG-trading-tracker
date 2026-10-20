package com.mtgtrader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/**
 * When the deck can win (since 1.52), from [games] games played alone against three players at 40 life.
 * [byTurn] is the share of games (whole percent) won by each turn, turn 1 first; [byCombo] the share won with a
 * combo by then. [damage] is each [WinOddsCalc] damage source's share of the damage dealt in the games won, and
 * [commanderKnockouts] the share of games won where commander damage knocked out at least one player.
 */
@Serializable
data class WinOdds(
    val games: Int,
    val byTurn: List<Int>,
    val byCombo: List<Int>,
    val damage: Map<String, Int>,
    val commanderKnockouts: Int,
    val counted: WinCounted,
) {
    val turns get() = byTurn.size

    /** The first turn by which [percent] of the games are won, or null when not by the last turn. */
    fun turnFor(percent: Int): Int? = byTurn.indexOfFirst { it >= percent }.takeIf { it >= 0 }?.plus(1)

    /** The turn the deck usually wins by (half the games or more). */
    val usual get() = turnFor(50)
    val nineInTen get() = turnFor(90)
    val fastest get() = turnFor(10)

    /** "T11", or "T15+" when most games aren't won by the last turn. */
    val label get() = usual?.let { "T$it" } ?: "T$turns+"

    /** Eight turns around the usual one, for the table of turns. */
    val window: IntRange
        get() {
            val end = (nineInTen ?: turns).coerceIn(minOf(8, turns), turns)
            return (end - 7).coerceAtLeast(1)..end
        }

    /** "Combat 53%, commander damage 46%." (the sources doing at least 3% of the damage). */
    val damageText: String
        get() = damage.entries.filter { it.value >= 3 }.sortedByDescending { it.value }
            .joinToString(", ") { (k, v) -> "${WinOddsCalc.sourceLabel(k)} $v%" }
            .replaceFirstChar(Char::uppercase).let { if (it.isEmpty()) it else "$it." }

    /** The share of games won with a combo (by the last turn). */
    val comboShare get() = byCombo.lastOrNull() ?: 0
}

/** What the win turn counted in the deck, for its "How?". */
@Serializable
data class WinCounted(
    val creatures: Int = 0,
    val tokenMakers: Int = 0,
    val triggers: Int = 0,
    val drains: Int = 0,
    val gear: Int = 0,
    val pumps: Int = 0,
    /** The combos that win on their own, each as "A + B". */
    val combos: List<String> = emptyList(),
)

/**
 * Plays a Commander deck alone many times ("goldfishing") to see by which turn it wins: all three opponents
 * from 40 to 0, 21 commander damage from one commander knocking a player out, or a combo that wins on its own.
 * The mana works as for the commander odds ([ManaOddsCalc]: Karsten's mulligans, a land a turn, mana rocks,
 * creatures and land ramp). Then each turn the deck:
 * - casts its commander, creatures, token makers, equipment, auras and the cards that deal damage, the most
 *   expensive it can pay for first (with a "first spell each turn" trigger, the biggest spell leads);
 * - puts equipment and auras on its commander (or else its biggest creature) and keeps pump spells for combat;
 * - attacks with everything that can (no blockers: it plays alone), creatures from the turn after they came in
 *   unless they have haste;
 * - counts damage triggers read from the cards' rules text: casting spells (Vial Smasher, The Lord of Pain),
 *   creatures or lands entering (Purphoros, landfall), the start of the turn, attacking, and tap abilities.
 * A combo counts when Commander Salt says it wins the game (or deals infinite damage) and it needs nothing more
 * on the battlefield (the owner's choice, 2026-10-20); its cards must be in hand, on the battlefield or in the
 * command zone, and the mana there to cast the missing ones. Nothing else is counted: no opponents, removal,
 * tutors, sacrifice outlets or abilities the text patterns don't recognise, so it's a best case to compare decks
 * by, not a promise. Since 1.52.
 */
object WinOddsCalc {
    const val GAMES = 10_000
    const val TURNS = 15

    /** Raised when the rules change, so results kept on the phone are worked out again. */
    const val VERSION = 1

    private const val OPPONENTS = 3
    private const val LIFE = 40
    private const val COMMANDER_LETHAL = 21
    private const val MANA_VALUE = -1
    private const val MAX_COMBAT_DRAW = 10

    const val COMBAT = "combat"
    const val COMMANDER = "commander"
    const val TRIGGERS = "triggers"
    const val DRAIN = "drain"
    private val SOURCES = listOf(COMBAT, COMMANDER, TRIGGERS, DRAIN)

    fun sourceLabel(key: String) = when (key) {
        COMMANDER -> "commander damage"
        TRIGGERS -> "damage triggers"
        DRAIN -> "drain and burn"
        else -> key
    }

    /** The combos the win turn counts: they win (or deal infinite damage) without needing more on the battlefield. */
    fun wins(combo: SaltCombo) = !combo.needsBoard && combo.results.any { it == "win-game" || it == "infinite-damage" }

    private val numbers = mapOf("a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6)
    private fun number(s: String?) = s?.lowercase()?.let { numbers[it] ?: it.toIntOrNull() } ?: 0

    private const val N = "(\\d+|x|a|an|one|two|three|four|five|six)"
    private val create = Regex("create $N (?:tapped )?(?:[\\w-]+ ){0,4}?(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
    private val drawCards = Regex("draws? $N cards?", RegexOption.IGNORE_CASE)
    private val anthemText = Regex("(other )?creatures you control get \\+(\\d+)/\\+\\d+", RegexOption.IGNORE_CASE)
    private val eachOpponent = Regex("each opponent loses (\\d+|x) life|deals? (\\d+|x) damage to each opponent", RegexOption.IGNORE_CASE)
    private val oneOpponent = Regex(
        "deals? (\\d+|x) damage to (?:any target|target (?:player|opponent)|that player|target opponent or planeswalker|target player or planeswalker|the chosen player)" +
            "|target (?:player|opponent) loses (\\d+|x) life",
        RegexOption.IGNORE_CASE,
    )
    private val manaValueDamage = Regex("(?:deals? damage|loses life) equal to (?:that spell's|its) mana value", RegexOption.IGNORE_CASE)
    private val castTrigger = Regex(
        "whenever (you|a player) casts? (your first spell each turn|their first spell each turn|a spell|an instant or sorcery spell|a noncreature spell|a creature spell)",
        RegexOption.IGNORE_CASE,
    )
    private val enterTrigger = Regex("^whenever (another )?(nontoken )?(creature|land)( card)? (you control )?enters(?! the battlefield under an opponent)", RegexOption.IGNORE_CASE)
    private val keywordLine = Regex("^[a-z ,'-]+$")
    private val gearGets = Regex("(?:equipped|enchanted) creature gets \\+(\\d+)/\\+\\d+(?! for each)", RegexOption.IGNORE_CASE)
    private val gearForEach = Regex("(?:equipped|enchanted) creature gets \\+1/\\+\\d+ for each ([^.]*)", RegexOption.IGNORE_CASE)
    private val gearBase = Regex("(?:equipped|enchanted) creature has base power and toughness (\\d+)/\\d+", RegexOption.IGNORE_CASE)
    private val gearDouble = Regex("(?:equipped|enchanted) creature has [^.]*double strike", RegexOption.IGNORE_CASE)
    private val gearHaste = Regex("(?:equipped|enchanted) creature has [^.]*haste", RegexOption.IGNORE_CASE)
    private val equipCost = Regex("^equip(?: legendary creature)? \\{(\\d+)\\}", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val pumpText = Regex("target creature(?: you control)? gets \\+(\\d+|x)/\\+(?:\\d+|x)", RegexOption.IGNORE_CASE)
    private val pumpDouble = Regex("target (?:attacking )?creature(?: you control)? gains double strike until end of turn", RegexOption.IGNORE_CASE)
    private val attackDouble = Regex("whenever [^.]*attack[^.]*double strike", RegexOption.IGNORE_CASE)
    private val targetedBonus = Regex("becomes the target of an? (?:instant or sorcery )?spell[^.]*gets \\+(\\d+)/\\+\\d+", RegexOption.IGNORE_CASE)
    private val sacrificeAbility = Regex(
        "^\\{t\\}, sacrifice up to $N permanents: if there were $N or more card types among the sacrificed permanents, each opponent loses (\\d+) life(?:[^.]*?draw $N cards?)?",
        RegexOption.IGNORE_CASE,
    )
    private val artifactToken = Regex("create $N (?:tapped )?(?:food|treasure|clue|blood|map|powerstone|gold|junk|lander|incubator) tokens?", RegexOption.IGNORE_CASE)
    private val cardTypes = listOf("Artifact", "Creature", "Enchantment", "Land", "Planeswalker", "Battle")
    private val tapAbility = Regex("^\\{t\\}(, \\{\\d+\\})?:")
    private val combatDraw = Regex("deals combat damage to a player, [^.]*?draws? (a card|that many cards)", RegexOption.IGNORE_CASE)

    enum class Filter { ANY, CREATURE, NONCREATURE }
    enum class ForEach { COLOURS, HAND, ARTIFACTS_ENCHANTMENTS, ENCHANTMENTS, ONE }

    /** Damage when a spell is cast: [amount] or the spell's mana value ([MANA_VALUE]), to each opponent or one. */
    data class CastTrigger(val firstOnly: Boolean, val filter: Filter, val each: Boolean, val amount: Int)

    /**
     * "{T}, Sacrifice up to [count] permanents: if there were [types] or more card types among them, each opponent loses
     * [drain] life … and you draw [draw] cards" (Baba Lysaga, Night Witch). Since 1.52.
     */
    data class SacrificeAbility(val count: Int, val types: Int, val drain: Int, val draw: Int)

    /** Damage when a creature or land of the deck enters. */
    data class EnterTrigger(val land: Boolean, val another: Boolean, val each: Boolean, val amount: Int)

    /** What a card does in the game, read once per card from its type, power and rules text. */
    internal class WinCard(val name: String, val mana: ManaOddsCalc.Card, val cost: ManaOddsCalc.Cost, val manaValue: Int) {
        val key = SaltCard.key(name)
        var permanent = true
        var creature = false
        var artifactOrEnchantment = false
        var enchantment = false
        var power = 0
        var haste = false
        var double = false
        var tokensOnce = 0
        var tokensTurn = 0
        var drainOnce = 0
        var drainTurn = 0
        var oneOnce = 0
        var oneTurn = 0
        var anthem = 0
        var overrun = 0
        var drawOnce = 0
        var drawTurn = 0
        val castTriggers = ArrayList<CastTrigger>()
        val enterTriggers = ArrayList<EnterTrigger>()
        var equipment = false
        var aura = false
        var bonus = 0
        var forEach: ForEach? = null
        var base: Int? = null
        var grantsDouble = false
        var grantsHaste = false
        var equip: Int? = null
        /** The token For Mirrodin! (2/2) or living weapon (0/0) makes and equips, by its power. */
        var equipsToken: Int? = null
        var pump = 0
        var pumpX = false
        var pumpDouble = false
        var attackDouble = false
        var targetedBonus = 0
        /** Cards drawn when it deals combat damage to a player: 1, or the damage ([MANA_VALUE]). */
        var combatDraw = 0
        var combo = false
        /** The card types of its front face (Artifact, Creature, Enchantment, Land…), for sacrifice abilities. */
        var types: Set<String> = emptySet()
        var sacrifice: SacrificeAbility? = null
        /** Food, Clue, Treasure… tokens it makes once, each turn, or for each land entering. */
        var artifactsOnce = 0
        var artifactsTurn = 0
        var artifactsLandfall = 0

        val land get() = mana.kind == ManaOddsCalc.Kind.LAND
        val gearBonus get() = bonus > 0 || forEach != null || base != null || grantsDouble || grantsHaste
        val pumpSpell get() = !permanent && (pump > 0 || pumpX || pumpDouble)
        val dealsDamage get() = drainOnce + drainTurn + oneOnce + oneTurn > 0 || sacrifice != null
    }

    internal fun classify(c: StatsCard, own: Set<Char>, lands: List<StatsCard>, comboKeys: Set<String>): WinCard {
        val mana = ManaOddsCalc.classify(c, own, lands)
        val w = WinCard(c.name, mana, ManaOddsCalc.cost(c.manaCost), c.cmc.toInt())
        w.combo = w.key in comboKeys
        val type = (c.typeLine?.takeIf { it.isNotEmpty() } ?: c.types).substringBefore(" // ")
        w.types = cardTypes.filter { it in type.substringBefore(" — ").split(' ') }.toSet()
        if (w.land) {
            // A land's own abilities can still make tokens to sacrifice (Treasure Vault aside).
            c.oracleText.orEmpty().split(CardInfo.FACE_BREAK).first().split('\n').forEach { readArtifacts(w, it.trim()) }
            return w
        }
        w.permanent = "Instant" !in type && "Sorcery" !in type
        w.creature = "Creature" in type
        w.enchantment = "Enchantment" in type
        w.artifactOrEnchantment = w.permanent && !w.creature && ("Artifact" in type || "Enchantment" in type)
        w.equipment = "Equipment" in type
        w.power = c.powerToughness?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
        val text = c.oracleText.orEmpty().split(CardInfo.FACE_BREAK).first()
        w.aura = "Aura" in type && "enchant creature" in text.lowercase()
        for (line in text.split('\n')) readLine(w, line.trim())
        if (w.equipment || w.aura) readGear(w, text)
        return w
    }

    private fun readArtifacts(w: WinCard, line: String) {
        val m = artifactToken.find(line) ?: return
        val low = line.lowercase()
        val n = number(m.groupValues[1])
        when {
            low.startsWith("landfall") || (low.startsWith("whenever a land") && "enters" in low) -> w.artifactsLandfall += n
            low.startsWith("at the beginning of your") || (low.startsWith("whenever") && " attacks" in low) -> w.artifactsTurn += n
            low.startsWith("when ") && "enters" in low && "at the beginning of your upkeep" in low -> { w.artifactsOnce += n; w.artifactsTurn += n }
            low.startsWith("when ") && "enters" in low || !w.permanent -> w.artifactsOnce += n
        }
    }

    private fun readLine(w: WinCard, line: String) {
        val low = line.lowercase()
        readArtifacts(w, line)
        sacrificeAbility.find(line)?.let { m ->
            w.sacrifice = SacrificeAbility(number(m.groupValues[1]), number(m.groupValues[2]), m.groupValues[3].toInt(), number(m.groupValues[4]))
            return
        }
        if (keywordLine.matches(low)) {
            val words = low.split(", ")
            if ("haste" in words) w.haste = true
            if ("double strike" in words) w.double = true
            return
        }
        val recurring = low.startsWith("at the beginning of your") || low.startsWith("at the beginning of combat on your") ||
            (low.startsWith("whenever") && " attacks" in low)
        val tap = tapAbility.containsMatchIn(low) && "sacrifice" !in low.substringBefore(':')
        val once = !w.permanent || (low.startsWith("when ") && "enters" in low)
        castTrigger.find(line)?.let { m ->
            val what = m.groupValues[2].lowercase()
            if (m.groupValues[1].lowercase() == "a player" && "their first spell" !in what) return
            val filter = when {
                "noncreature" in what || "instant or sorcery" in what -> Filter.NONCREATURE
                "creature spell" in what -> Filter.CREATURE
                else -> Filter.ANY
            }
            val first = "first spell" in what
            val e = eachOpponent.find(line)
            val o = oneOpponent.find(line)
            when {
                manaValueDamage.containsMatchIn(line) -> w.castTriggers += CastTrigger(first, filter, "each opponent" in low, MANA_VALUE)
                e != null -> number(e.groupValues[1].ifEmpty { e.groupValues[2] }).takeIf { it > 0 }?.let { w.castTriggers += CastTrigger(first, filter, true, it) }
                o != null -> number(o.groupValues[1].ifEmpty { o.groupValues[2] }).takeIf { it > 0 }?.let { w.castTriggers += CastTrigger(first, filter, false, it) }
            }
            return
        }
        val enters = enterTrigger.find(line)
        if (enters != null || low.startsWith("landfall")) {
            val head = low.substringBefore(',')
            val land = enters?.groupValues?.get(3)?.lowercase() == "land" || low.startsWith("landfall")
            val e = eachOpponent.find(line)
            val o = oneOpponent.find(line)
            when {
                e != null -> number(e.groupValues[1].ifEmpty { e.groupValues[2] }).takeIf { it > 0 }?.let { w.enterTriggers += EnterTrigger(land, "another" in head, true, it) }
                o != null -> number(o.groupValues[1].ifEmpty { o.groupValues[2] }).takeIf { it > 0 }?.let { w.enterTriggers += EnterTrigger(land, "another" in head, false, it) }
            }
            return
        }
        val e = eachOpponent.find(line)
        val o = oneOpponent.find(line)
        if (e != null || o != null) {
            val n = if (e != null) number(e.groupValues[1].ifEmpty { e.groupValues[2] }) else number(o!!.groupValues[1].ifEmpty { o.groupValues[2] })
            when {
                n <= 0 -> Unit
                recurring || tap -> if (e != null) w.drainTurn += n else w.oneTurn += n
                once -> if (e != null) w.drainOnce += n else w.oneOnce += n
            }
        }
        create.find(line)?.let { m ->
            val p = number(m.groupValues[1]) * (m.groupValues[2].toIntOrNull() ?: 0)
            if (recurring || tap) w.tokensTurn += p else if (once) w.tokensOnce += p
        }
        drawCards.find(line)?.let { m ->
            if ("opponent" in low.substring(0, m.range.first) || "each player" in low) return@let
            val n = number(m.groupValues[1])
            when {
                recurring || tap -> w.drawTurn += n
                low.startsWith("whenever") -> w.drawTurn += minOf(n, 1)
                once -> w.drawOnce += n
            }
        }
        anthemText.find(line)?.let { m ->
            val n = m.groupValues[2].toInt()
            if ("until end of turn" in low) w.overrun += n else if (w.permanent && !low.startsWith("when")) w.anthem += n
        }
        if (!w.permanent) {
            pumpText.find(line)?.let { m -> if (m.groupValues[1].lowercase() == "x") w.pumpX = true else if (w.pump == 0) w.pump = m.groupValues[1].toInt() }
            if (pumpDouble.containsMatchIn(line)) w.pumpDouble = true
        } else {
            if (attackDouble.containsMatchIn(line)) w.attackDouble = true
            targetedBonus.find(line)?.let { w.targetedBonus += it.groupValues[1].toInt() }
            combatDraw.find(line)?.let { w.combatDraw = if ("many" in it.groupValues[1].lowercase()) MANA_VALUE else 1 }
        }
    }

    private fun readGear(w: WinCard, text: String) {
        gearGets.find(text)?.let { w.bonus = it.groupValues[1].toInt() }
        gearForEach.find(text)?.let { m ->
            val what = m.groupValues[1].lowercase()
            w.forEach = when {
                "color" in what -> ForEach.COLOURS
                "card in your hand" in what -> ForEach.HAND
                "artifact" in what -> ForEach.ARTIFACTS_ENCHANTMENTS
                "enchantment" in what -> ForEach.ENCHANTMENTS
                else -> ForEach.ONE
            }
        }
        gearBase.find(text)?.let { w.base = it.groupValues[1].toInt() }
        w.grantsDouble = gearDouble.containsMatchIn(text)
        w.grantsHaste = gearHaste.containsMatchIn(text)
        if (w.equipment) {
            w.equip = equipCost.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.minOrNull()
            val low = text.lowercase()
            w.equipsToken = when {
                "for mirrodin!" in low -> 2
                "living weapon" in low -> 0
                else -> null
            }
        }
    }

    /**
     * Whether the deck casts this card when it has the mana (pump spells wait for combat). With spell triggers every
     * spell helps; with a sacrifice ability (Baba Lysaga) every permanent is something to sacrifice.
     */
    private fun useful(c: WinCard, spellTriggers: Boolean, creatureTriggers: Boolean, fodder: Boolean): Boolean = when {
        c.land || c.pumpSpell -> false
        spellTriggers -> true
        fodder && c.permanent -> true
        creatureTriggers && c.creature -> true
        else -> c.creature && c.power > 0 || c.tokensOnce + c.tokensTurn > 0 || c.dealsDamage || c.anthem + c.overrun > 0 ||
            c.drawOnce + c.drawTurn > 0 || c.combo || c.castTriggers.isNotEmpty() || c.enterTriggers.isNotEmpty() ||
            (c.equipment || c.aura) && c.gearBonus || c.attackDouble || c.targetedBonus > 0 || c.combatDraw != 0 ||
            c.sacrifice != null || c.artifactsOnce + c.artifactsTurn + c.artifactsLandfall > 0
    }

    fun calculate(
        cards: List<StatsCard>,
        identity: String,
        combos: List<SaltCombo>,
        games: Int = GAMES,
        turns: Int = TURNS,
        random: Random = Random(52),
    ): WinOdds? {
        val commanders = cards.filter { it.commander }
        val library = cards.filterNot { it.commander }
        if (commanders.isEmpty() || library.sumOf { it.quantity } < 8) return null
        val own = identity.filter { it in "WUBRG" }.toSet()
        val lands = library.filter { it.isLand }
        val winning = combos.filter(::wins)
        val comboSets = winning.map { c -> c.cards.map(SaltCard::key).toSet() }
        val comboKeys = comboSets.flatten().toSet()
        val deck = ArrayList<WinCard>()
        val kinds = ArrayList<WinCard>()
        for (c in library) {
            val w = classify(c, own, lands, comboKeys)
            kinds += w
            repeat(c.quantity.coerceAtLeast(0)) { deck += w }
        }
        val leaders = commanders.map { classify(it, own, lands, comboKeys) }
        val won = IntArray(turns + 1)
        val byCombo = IntArray(turns + 1)
        val dealt = LongArray(SOURCES.size)
        var knockouts = 0
        var wins = 0
        repeat(games) {
            val (hand, rest) = ManaOddsCalc.keepHand(deck, random) { it.mana }
            val game = Game(hand, rest, leaders, own, comboSets)
            val turn = game.play(turns)
            if (turn > 0) {
                won[turn]++
                wins++
                if (game.byCombo) byCombo[turn]++
                game.dealt.forEachIndexed { i, d -> dealt[i] += d }
                if (game.knockout) knockouts++
            }
        }
        fun cumulative(counts: IntArray): List<Int> {
            var sum = 0
            return (1..turns).map { t ->
                sum += counts[t]
                val p = Math.round(100.0 * sum / games).toInt()
                if (p == 100 && sum < games) 99 else p
            }
        }
        val total = dealt.sum().coerceAtLeast(1)
        val all = kinds + leaders
        return WinOdds(
            games = games,
            byTurn = cumulative(won),
            byCombo = cumulative(byCombo),
            damage = SOURCES.mapIndexed { i, k -> k to Math.round(100.0 * dealt[i] / total).toInt() }.filter { it.second > 0 }.toMap(),
            commanderKnockouts = if (wins == 0) 0 else Math.round(100.0 * knockouts / wins).toInt(),
            counted = WinCounted(
                creatures = deck.count { it.creature && it.power > 0 } + leaders.count { it.creature && it.power > 0 },
                tokenMakers = all.count { it.tokensOnce + it.tokensTurn > 0 },
                triggers = all.count { it.castTriggers.isNotEmpty() || it.enterTriggers.isNotEmpty() },
                drains = all.count { it.dealsDamage },
                gear = all.count { (it.equipment || it.aura) && it.gearBonus },
                pumps = all.count { it.pumpSpell },
                combos = winning.map { it.cards.joinToString(" + ") },
            ),
        )
    }

    private class Source(val colours: Set<Char>, val amount: Int, val from: Int)

    /** A creature, token or other permanent on the battlefield; [gear] are the equipment and auras on it. */
    private class Entry(
        val card: WinCard?,
        val turn: Int,
        val power: Int,
        val creature: Boolean,
        val haste: Boolean,
        val double: Boolean,
        /** Its card types; tokens and lands from ramp spells have no card. */
        val types: Set<String> = card?.types.orEmpty(),
    ) {
        val gear = ArrayList<WinCard>()
        var on: Entry? = null
        /** The mana it makes, gone when it's sacrificed. */
        var source: Source? = null
        /** The turn it was tapped for an ability (it doesn't attack then). */
        var tapped = 0
    }

    /** One game, played alone turn by turn. */
    private class Game(
        val hand: MutableList<WinCard>,
        val rest: List<WinCard>,
        val leaders: List<WinCard>,
        val own: Set<Char>,
        val combos: List<Set<String>>,
    ) {
        val command = leaders.toMutableList()
        val sources = ArrayList<Source>()
        val board = ArrayList<Entry>()
        val life = IntArray(OPPONENTS) { LIFE }
        val commanderDamage = Array(leaders.size) { IntArray(OPPONENTS) }
        val dealt = LongArray(SOURCES.size)
        var drawn = 0
        var turn = 0
        var spells = 0
        var byCombo = false
        var knockout = false
        var spellTriggers = false
        var creatureTriggers = false
        var firstSpellTrigger = false

        /** The deck has a sacrifice ability (in the command zone or the library): its permanents are fodder. */
        val fodder = leaders.any { it.sacrifice != null } || rest.any { it.sacrifice != null } || hand.any { it.sacrifice != null }

        fun mainOptions() = (command + hand).filter { useful(it, spellTriggers, creatureTriggers, fodder) }
            .sortedWith(compareByDescending<WinCard> { it in command }.thenByDescending { it.manaValue })

        fun draw(n: Int) = repeat(n) { if (drawn < rest.size) hand += rest[drawn++] }

        fun each(n: Int, source: Int) {
            if (n <= 0) return
            for (i in life.indices) if (life[i] > 0) { life[i] -= n; dealt[source] += n.toLong() }
        }

        fun one(n: Int, source: Int) {
            if (n <= 0) return
            val i = life.indices.filter { life[it] > 0 }.minByOrNull { life[it] } ?: return
            life[i] -= n
            dealt[source] += n.toLong()
        }

        fun entered(self: Entry?, land: Boolean) {
            for (b in board) b.card?.enterTriggers?.forEach { t ->
                if (t.land == land && !(t.another && b === self)) if (t.each) each(t.amount, 2) else one(t.amount, 2)
            }
        }

        fun token(power: Int): Entry = Entry(null, turn, power, true, false, false, CREATURE_TOKEN).also { board += it; entered(it, false) }

        fun artifacts(n: Int) = repeat(n) { board += Entry(null, turn, 0, false, false, false, ARTIFACT_TOKEN) }

        /** Who carries the equipment and auras and gets the pump spells: the commander, else the biggest creature. */
        fun carrier(): Entry? = board.filter { it.creature && it.card in leaders }.maxByOrNull { it.power }
            ?: board.filter { it.creature }.maxByOrNull { it.power + it.gear.size }

        fun cast(c: WinCard): Int {
            if (!command.remove(c)) hand.remove(c)
            for (b in board) b.card?.castTriggers?.forEach { t ->
                if (t.firstOnly && spells > 0) return@forEach
                if (t.filter == Filter.CREATURE && !c.creature || t.filter == Filter.NONCREATURE && c.creature) return@forEach
                val n = if (t.amount == MANA_VALUE) c.manaValue else t.amount
                if (t.each) each(n, 2) else one(n, 2)
            }
            spells++
            val source = when (c.mana.kind) {
                ManaOddsCalc.Kind.RAMP -> Source(c.mana.colours, 1, if (c.mana.tapped) turn + 1 else turn).also {
                    board += Entry(null, turn, 0, false, false, false, LAND).apply { this.source = it }
                }
                ManaOddsCalc.Kind.CREATURE -> Source(c.mana.colours, c.mana.amount, turn + 1)
                ManaOddsCalc.Kind.ROCK -> Source(c.mana.colours, c.mana.amount, turn)
                else -> null
            }
            source?.let { sources += it }
            each(c.drainOnce, 3)
            one(c.oneOnce, 3)
            draw(c.drawOnce)
            if (c.permanent) {
                val e = Entry(c, turn, c.power, c.creature, c.haste, c.double)
                if (c.mana.kind != ManaOddsCalc.Kind.RAMP) e.source = source
                board += e
                if (c.castTriggers.isNotEmpty()) spellTriggers = true
                if (c.castTriggers.any { it.firstOnly }) firstSpellTrigger = true
                if (c.enterTriggers.any { !it.land }) creatureTriggers = true
                if (c.creature) entered(e, false)
                val germ = c.equipsToken
                if (c.equipment && germ != null) {
                    token(germ).also { it.gear += c; e.on = it }
                } else if (c.aura) {
                    carrier()?.let { it.gear += c; e.on = it }
                }
            }
            if (c.tokensOnce > 0) token(c.tokensOnce)
            artifacts(c.artifactsOnce)
            return if (c.permanent) 0 else c.overrun
        }

        fun pool() = sources.filter { it.from <= turn }.map { it.colours to it.amount }

        fun gearPower(b: Entry): Pair<Int, Boolean> {
            var base = b.power
            var plus = 0
            var double = b.double
            for (g in b.gear) {
                g.base?.let { base = maxOf(base, it) }
                plus += g.bonus
                plus += when (g.forEach) {
                    ForEach.COLOURS -> own.size
                    ForEach.HAND -> hand.size
                    ForEach.ARTIFACTS_ENCHANTMENTS -> board.count { it.card?.artifactOrEnchantment == true }
                    ForEach.ENCHANTMENTS -> board.count { it.card?.enchantment == true }
                    ForEach.ONE -> 1
                    null -> 0
                }
                double = double || g.grantsDouble
            }
            return base + plus to double
        }

        /** Plays up to [turns] turns; the turn the game is won, or 0. */
        fun play(turns: Int): Int {
            for (t in 1..turns) {
                turn = t
                spells = 0
                draw(1 + board.sumOf { it.card?.drawTurn ?: 0 })
                playLand()
                var pool = pool()
                var overrun = 0
                // With a "first spell each turn" trigger, the biggest spell goes first.
                if (firstSpellTrigger) {
                    for (c in (hand + command).filter { !it.land }.sortedByDescending { it.manaValue }) {
                        val left = ManaOddsCalc.pay(pool, c.cost) ?: continue
                        pool = left
                        overrun += cast(c)
                        break
                    }
                }
                for (c in hand.filter { it.mana.kind in RAMP_KINDS }.sortedBy { it.manaValue }) {
                    val left = ManaOddsCalc.pay(pool, c.cost) ?: continue
                    pool = left
                    cast(c)
                }
                if (comboReady(pool)) {
                    byCombo = true
                    return t
                }
                // Main phase: everything that helps, the most expensive first; the commander before the rest. The
                // mana only goes down, so one pass is enough unless a card drew more or brought a new trigger.
                var options = mainOptions()
                var i = 0
                while (i < options.size) {
                    val c = options[i]
                    val left = ManaOddsCalc.pay(pool, c.cost)
                    if (left == null) {
                        i++
                        continue
                    }
                    pool = left
                    val before = hand.size
                    val triggers = spellTriggers to creatureTriggers
                    overrun += cast(c)
                    if (hand.size >= before || triggers != (spellTriggers to creatureTriggers)) {
                        options = mainOptions()
                        i = 0
                    } else {
                        i++
                    }
                }
                pool = equip(pool)
                val anthem = board.sumOf { it.card?.anthem ?: 0 }
                for (b in board.toList()) {
                    val c = b.card ?: continue
                    each(c.drainTurn, 3)
                    one(c.oneTurn, 3)
                    if (c.tokensTurn > 0) token(c.tokensTurn)
                    artifacts(c.artifactsTurn)
                }
                sacrificeForValue(t)
                val attackers = board.filter { b -> b.creature && b.tapped != t && (b.turn < t || b.haste || b.gear.any { it.grantsHaste }) }
                val extra = HashMap<Entry, Int>()
                val doubled = HashSet<Entry>()
                if (attackers.isNotEmpty()) {
                    val carrier = carrier()
                    val hit = carrier?.takeIf { it in attackers } ?: attackers.maxBy { it.power + it.gear.size }
                    if (board.any { it.card?.attackDouble == true }) doubled += hit
                    val targeted = board.sumOf { it.card?.targetedBonus ?: 0 }
                    for (c in hand.filter { it.pumpSpell }.sortedWith(compareByDescending<WinCard> { it.pumpDouble }.thenByDescending { it.pump })) {
                        var left = ManaOddsCalc.pay(pool, c.cost) ?: continue
                        val x = if (c.pumpX) left.sumOf { it.second }.also { left = emptyList() } else 0
                        pool = left
                        cast(c)
                        extra[hit] = (extra[hit] ?: 0) + c.pump + x + targeted
                        if (c.pumpDouble) doubled += hit
                    }
                }
                var combat = 0
                var draws = 0
                val fromCommanders = IntArray(leaders.size)
                for (b in attackers) {
                    val (power, double) = gearPower(b)
                    val p = (power + anthem + overrun + (extra[b] ?: 0)).coerceAtLeast(0) * if (double || b in doubled) 2 else 1
                    val k = b.card?.let { leaders.indexOf(it) } ?: -1
                    if (k >= 0) fromCommanders[k] += p else combat += p
                    b.card?.combatDraw?.takeIf { it != 0 && p > 0 }?.let { draws += if (it == MANA_VALUE) p else it }
                }
                // Each commander keeps hitting the player it has hurt most; the rest finishes whoever is lowest.
                fromCommanders.forEachIndexed { k, p ->
                    if (p <= 0) return@forEachIndexed
                    val i = life.indices.filter { life[it] > 0 }.maxByOrNull { commanderDamage[k][it] } ?: return@forEachIndexed
                    life[i] -= p
                    commanderDamage[k][i] += p
                    dealt[1] += p.toLong()
                    if (commanderDamage[k][i] >= COMMANDER_LETHAL) {
                        life[i] = 0
                        knockout = true
                    }
                }
                dealt[0] += combat.toLong()
                for (i in life.indices.filter { life[it] > 0 }.sortedBy { life[it] }) {
                    val used = minOf(combat, life[i])
                    life[i] -= used
                    combat -= used
                }
                if (life.all { it <= 0 }) return t
                draw(minOf(draws, MAX_COMBAT_DRAW))
            }
            return 0
        }

        /**
         * Sacrifice abilities (Baba Lysaga): tapped once a turn when they can be, sacrificing the cheapest permanents that
         * cover enough card types: Food, Clue and Treasure tokens first, then creature tokens, spare lands, then cards.
         */
        fun sacrificeForValue(t: Int) {
            for (user in board.toList()) {
                val ability = user.card?.sacrifice ?: continue
                if (user !in board || !(user.turn < t || user.haste)) continue
                val lands = board.count { LAND_TYPE in it.types }
                fun cost(e: Entry): Int = when {
                    e.card == null && !e.creature && LAND_TYPE !in e.types -> 0
                    e.card == null && e.creature -> 1 + e.power / 2
                    LAND_TYPE in e.types && !e.creature -> if (lands > 5) 2 else 8
                    else -> 3 + (e.card?.manaValue ?: 0) + e.power + 4 * e.gear.size +
                        (if (e.card?.let { it.anthem > 0 || it.castTriggers.isNotEmpty() || it.enterTriggers.isNotEmpty() || it.drawTurn > 0 } == true) 4 else 0)
                }
                val candidates = board.filter { it !== user && it.types.isNotEmpty() && it.card !in leaders && it.on == null }
                    .sortedBy(::cost).take(8)
                val masks = IntArray(candidates.size) { i -> candidates[i].types.fold(0) { m, t -> m or (1 shl cardTypes.indexOf(t)) } }
                if (Integer.bitCount(masks.fold(0) { a, m -> a or m }) < ability.types) continue
                val costs = IntArray(candidates.size) { cost(candidates[it]) }
                // The cheapest set of up to [count] permanents covering enough types (at most 8 to choose from).
                var best = 0
                var bestCost = Int.MAX_VALUE
                fun search(from: Int, picked: Int, mask: Int, total: Int, size: Int) {
                    if (total >= bestCost) return
                    if (Integer.bitCount(mask) >= ability.types) {
                        best = picked
                        bestCost = total
                        return
                    }
                    if (size == ability.count) return
                    for (i in from until candidates.size) search(i + 1, picked or (1 shl i), mask or masks[i], total + costs[i], size + 1)
                }
                search(0, 0, 0, 0, 0)
                if (bestCost == Int.MAX_VALUE) continue
                val chosen = candidates.filterIndexed { i, _ -> best and (1 shl i) != 0 }
                for (e in chosen) {
                    board.remove(e)
                    e.source?.let { sources.remove(it) }
                    board.filter { it.on === e }.forEach { g -> g.on = null; if (g.card?.aura == true) board.remove(g) }
                }
                user.tapped = t
                each(ability.drain, 3)
                draw(ability.draw)
            }
        }

        fun playLand() {
            // An untapped land first, then the one adding the most missing colours (as for the commander odds).
            val have = HashSet<Char>()
            sources.forEach { have += it.colours }
            val land = hand.filter { it.land }.minByOrNull { l -> (if (l.mana.tapped) 10 else 0) - l.mana.colours.count { it !in have } } ?: return
            hand.remove(land)
            val source = Source(land.mana.colours, 1, if (land.mana.tapped) turn + 1 else turn)
            sources += source
            board += Entry(land, turn, 0, false, false, false).also { it.source = source }
            artifacts(land.artifactsOnce)
            entered(null, true)
            board.toList().forEach { b -> b.card?.let { artifacts(it.artifactsLandfall) } }
        }

        /** Whether a winning combo's missing cards are at hand and there's the mana to cast them. */
        fun comboReady(pool: List<Pair<Set<Char>, Int>>): Boolean {
            if (combos.isEmpty()) return false
            val there = board.mapNotNull { it.card?.key }.toSet()
            val at = (hand + command).associateBy { it.key }
            return combos.any { combo ->
                val need = combo - there
                if (!need.all { it in at }) return@any false
                var p: List<Pair<Set<Char>, Int>>? = pool
                for (k in need.sortedByDescending { at.getValue(it).manaValue }) p = p?.let { ManaOddsCalc.pay(it, at.getValue(k).cost) }
                p != null
            }
        }

        /** Moves equipment onto the carrier, the biggest bonus first, while the mana lasts. */
        fun equip(start: List<Pair<Set<Char>, Int>>): List<Pair<Set<Char>, Int>> {
            var pool = start
            val target = carrier() ?: return pool
            val loose = board.filter { e -> e.card?.let { it.equipment && it.gearBonus && it.equip != null } == true && e.on !== target }
                .sortedByDescending { e -> e.card!!.let { it.bonus + (if (it.grantsDouble) 10 else 0) + (it.base ?: 0) } }
            for (e in loose) {
                val card = e.card ?: continue
                val cost = card.equip ?: continue
                if (cost > 0) pool = ManaOddsCalc.pay(pool, ManaOddsCalc.Cost(cost, emptyList())) ?: continue
                e.on?.gear?.remove(card)
                target.gear += card
                e.on = target
            }
            return pool
        }

        companion object {
            val RAMP_KINDS = setOf(ManaOddsCalc.Kind.ROCK, ManaOddsCalc.Kind.CREATURE, ManaOddsCalc.Kind.RAMP)
            const val LAND_TYPE = "Land"
            val LAND = setOf(LAND_TYPE)
            val CREATURE_TOKEN = setOf("Creature")
            val ARTIFACT_TOKEN = setOf("Artifact")
        }
    }
}

/**
 * The win turns, kept in a file on the phone (not synced, not in the database) by deck, with a fingerprint of
 * what they were worked out from, so they're only worked out again when the deck, its card details or its
 * combos change. Since 1.52.
 */
class WinOddsStore(private val dir: File, private val db: AppDatabase) {
    @Serializable
    private data class Saved(val key: String, val odds: WinOdds)

    private val file get() = File(dir, "win_odds.json")
    private val mutex = Mutex()
    private var saved: MutableMap<String, Saved>? = null
    private val serializer = MapSerializer(String.serializer(), Saved.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    /** The win turn of these cards, worked out in the background unless kept from before; null for decks without a commander. */
    suspend fun get(deckId: Long, cards: List<StatsCard>, identity: String, salt: SaltCard?): WinOdds? {
        if (cards.none { it.commander }) return null
        val combos = salt?.allCombos.orEmpty()
        val key = fingerprint(cards, identity, combos)
        load()[deckId.toString()]?.takeIf { it.key == key }?.let { return it.odds }
        val odds = withContext(Dispatchers.Default) { WinOddsCalc.calculate(cards, identity, combos) } ?: return null
        mutex.withLock {
            val all = saved ?: mutableMapOf()
            all[deckId.toString()] = Saved(key, odds)
            withContext(Dispatchers.IO) { runCatching { file.writeText(json.encodeToString(serializer, all)) } }
        }
        return odds
    }

    /** The same for a deck that isn't on screen (the bracket cards), reading its cards from the database. */
    suspend fun forDeck(deck: Deck): WinOdds? =
        get(deck.archidektId, db.cardInfoDao().deckStats(deck.archidektId), deck.colorIdentity, SaltCard.decode(deck.saltCard))

    private suspend fun load(): Map<String, Saved> = mutex.withLock {
        saved ?: withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(serializer, file.readText()).toMutableMap() }.getOrDefault(mutableMapOf())
        }.also { saved = it }
    }

    companion object {
        fun fingerprint(cards: List<StatsCard>, identity: String, combos: List<SaltCombo>): String {
            val text = buildString {
                append(WinOddsCalc.VERSION).append('|').append(identity).append('|')
                cards.sortedWith(compareBy({ it.name }, { it.scryfallId })).forEach { append(it.toString()).append('\n') }
                combos.forEach { append(it.cards).append(it.results).append(it.needsBoard).append('\n') }
            }
            return MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
