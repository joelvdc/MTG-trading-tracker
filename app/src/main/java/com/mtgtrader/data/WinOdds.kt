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
 * combo by then, and [byMaze] with Maze's End (since 1.54). [damage] is each [WinOddsCalc] damage source's share of
 * the damage dealt in the games won, and [commanderKnockouts] the share of games won where commander damage knocked out
 * at least one player.
 */
@Serializable
data class WinOdds(
    val games: Int,
    val byTurn: List<Int>,
    val byCombo: List<Int>,
    val damage: Map<String, Int>,
    val commanderKnockouts: Int,
    val counted: WinCounted,
    val byMaze: List<Int> = emptyList(),
) {
    val turns get() = byTurn.size

    /** The first turn by which [percent] of the games are won, or null when not by the last turn. */
    fun turnFor(percent: Int): Int? = byTurn.indexOfFirst { it >= percent }.takeIf { it >= 0 }?.plus(1)

    /** The turn 4 in 10 games are won by: the one the bracket cards show (since 1.54; half the games before). */
    val fourInTen get() = turnFor(40)
    /** The turn the deck usually wins by (half the games or more). */
    val usual get() = turnFor(50)
    val nineInTen get() = turnFor(90)

    /** The first turn the deck wins at all: 1% of the games or more (since 1.54). */
    val firstWin get() = turnFor(1)

    /** "T11", or "T15+" when not 4 in 10 games by the last turn. */
    val label get() = fourInTen?.let { "T$it" } ?: "T$turns+"

    /** From the first win to 9 in 10 games: 8 to 10 turns (since 1.54). */
    val window: IntRange
        get() {
            val start = (firstWin ?: (turns - 7)).coerceIn(1, maxOf(1, turns - 7))
            val end = (nineInTen ?: turns).coerceIn(minOf(start + 7, turns), minOf(turns, start + 9))
            return start..end
        }

    /** "Combat 53%, commander damage 46%." (the sources doing at least 3% of the damage). */
    val damageText: String
        get() = damage.entries.filter { it.value >= 3 }.sortedByDescending { it.value }
            .joinToString(", ") { (k, v) -> "${WinOddsCalc.sourceLabel(k)} $v%" }
            .replaceFirstChar(Char::uppercase).let { if (it.isEmpty()) it else "$it." }

    /** The share of games won with a combo (by the last turn). */
    val comboShare get() = byCombo.lastOrNull() ?: 0

    /** The share of games won with Maze's End (by the last turn). Since 1.54. */
    val mazeShare get() = byMaze.lastOrNull() ?: 0
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
    /** The combos that can win, each as "A + B", with what else they need ("with Obelisk Spider"). */
    val combos: List<String> = emptyList(),
    /** Tutors that fetch a missing combo piece (since 1.53). */
    val tutors: Int = 0,
    /** Cards counting what's on the battlefield or triggering on a kind entering ("for each Shrine", since 1.54). */
    val counting: Int = 0,
    /** Cards that make sacrifices pay (untaps, Marionette Master, Titania…) and X spells (since 1.54). */
    val sacrifice: Int = 0,
    val xSpells: Int = 0,
    /** The deck has Maze's End (since 1.54). */
    val mazeEnd: Boolean = false,
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
 * Combos (since 1.53, see [plan]): those that win the game or deal infinite damage; endless loops of deaths,
 * creatures entering or -1/-1 counters when the deck has a card that turns them into life loss (Obelisk Spider,
 * Blood Artist, Purphoros); endless tokens, winning the turn after. Their cards must be in hand, on the battlefield
 * or in the command zone with the mana to cast the missing ones, and what Commander Spellbook lists as needed
 * first must be there (other creatures, a card that can start the loop, an opponent's creature from turn 4).
 * Tutors fetch a missing combo piece. Since 1.54 also:
 * - effects that count permanents ("for each Shrine you control", "where X is the number of Gates…") and triggers on a
 *   kind of permanent entering ("whenever another Shrine you control enters"), with tokens keeping their types;
 * - Maze's End: ten Gates with different names win; Gate searches bring real Gates, and lands may enter untapped;
 * - sacrifice abilities (Baba Lysaga) used at once and again with untaps and Marvin, Liquimetal, and their payoffs;
 * - X spells draining each opponent, and Double Vision copying the first instant or sorcery.
 * Nothing else is counted: no opponents, removal or abilities the text patterns don't recognise, so it's a best case
 * to compare decks by, not a promise. Since 1.52.
 */
object WinOddsCalc {
    const val GAMES = 10_000
    const val TURNS = 15

    /**
     * Raised when the rules change, so results kept on the phone are worked out again. 2: combos and tutors (1.53).
     * 3: counting Shrines and Gates, Maze's End, sacrifice decks and X spells (1.54).
     */
    const val VERSION = 3

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

    /** How a combo wins (since 1.53). */
    enum class ComboKind {
        /** It wins the game or deals infinite damage. */
        WINS,
        /** Endless deaths, creatures entering or leaving, or -1/-1 counters: it wins with a card that turns them into life loss. */
        LOOP,
        /** Endless tokens: they attack and win the turn after. */
        TOKENS,
    }

    /** What an endless loop repeats, for the cards that turn it into life loss. */
    enum class LoopEvent { DEATH, ENTER, COUNTER }

    /**
     * A combo as the win turn reads it: whether it [wins] outright, what it [loops], whether it makes endless
     * [tokens]; and what it needs besides its cards: [creatures] other creatures on the battlefield, an opponent's
     * creature ([opponentCreature], taken as there from turn 4), and for each "you have a way to …" another card of
     * the deck on the battlefield whose text has one of the [enablers] words. [kind] and [payoffs] are settled per
     * deck ([settle]).
     */
    class ComboPlan(
        val names: List<String>,
        val wins: Boolean,
        val loops: Set<LoopEvent> = emptySet(),
        val tokens: Boolean = false,
        val creatures: Int = 0,
        val opponentCreature: Boolean = false,
        val enablers: List<List<String>> = emptyList(),
    ) {
        val keys = names.map(SaltCard::key).toSet()
        var kind = if (wins) ComboKind.WINS else ComboKind.TOKENS
        /** The deck's cards that turn this loop into life loss. */
        var payoffs: Set<String> = emptySet()
    }

    private val loopResults = mapOf("infinite-ltb" to LoopEvent.DEATH, "infinite-etb" to LoopEvent.ENTER, "infinite-counters" to LoopEvent.COUNTER)
    private val putsCounters = Regex("\\bput (?:a|one or more) -1/-1 counters? on", RegexOption.IGNORE_CASE)
    private val atLeastCreatures = Regex("you control at least (\\w+) (?:other |additional )?creatures?", RegexOption.IGNORE_CASE)
    private val wayTo = Regex("you have a way to (.*)", RegexOption.IGNORE_CASE)

    /** How the win turn reads a combo of Commander Salt's (since 1.53), or null when it can't win. */
    fun plan(combo: SaltCombo): ComboPlan? {
        val wins = combo.results.any { it == "win-game" || it == "infinite-damage" }
        val loops = combo.results.mapNotNull { loopResults[it] }.toSet()
        val tokens = "infinite-tokens" in combo.results
        if (!wins && loops.isEmpty() && !tokens) return null
        var creatures = 0
        var opponent = false
        val enablers = ArrayList<List<String>>()
        for (line in combo.prerequisites.map { it.lowercase() }) {
            atLeastCreatures.find(line)?.let { creatures = maxOf(creatures, number(it.groupValues[1])) }
            if ("opponent controls" in line || "opponents control" in line) opponent = true
            wayTo.find(line)?.let { m ->
                val what = m.groupValues[1]
                enablers += when {
                    "-1/-1 counter" in what -> listOf("-1/-1 counter", "blight", "wither")
                    "+1/+1 counter" in what -> listOf("+1/+1 counter")
                    "damage" in what -> listOf("damage")
                    "sacrifice" in what -> listOf("sacrifice")
                    "untap" in what -> listOf("untap")
                    else -> listOf("")
                }
            }
        }
        return ComboPlan(combo.cards, wins, loops, tokens, creatures, opponent, enablers)
    }

    /**
     * Settles how a combo wins in this deck: outright; as a loop when the deck has a card for what it repeats (a
     * death, a creature entering, a -1/-1 counter you put: a loop whose pieces put -1/-1 counters also works with
     * Obelisk Spider); else with endless tokens the turn after. False when it can't win in this deck.
     */
    internal fun settle(p: ComboPlan, cards: List<WinCard>): Boolean {
        if (p.wins) return true
        val pieces = cards.filter { it.key in p.keys }
        // Reminder text in brackets says what others do ("They each put a -1/-1 counter…"): left out.
        val own = pieces.any { putsCounters.containsMatchIn(it.text.replace(Regex("\\([^)]*\\)"), "")) }
        val loops = p.loops + if (p.loops.isNotEmpty() && own) setOf(LoopEvent.COUNTER) else emptySet()
        p.payoffs = cards.filter { c ->
            LoopEvent.DEATH in loops && c.deathPayoff || LoopEvent.ENTER in loops && c.enterPayoff || LoopEvent.COUNTER in loops && c.counterPayoff
        }.map { it.key }.toSet()
        p.kind = when {
            p.payoffs.isNotEmpty() -> ComboKind.LOOP
            p.tokens -> ComboKind.TOKENS
            else -> return false
        }
        return true
    }

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
    private val cardTypes = listOf("Artifact", "Creature", "Enchantment", "Land", "Planeswalker", "Battle", "Instant", "Sorcery")
    private val deathPayoff = Regex(
        "whenever (?:a|another|one or more)[^.]*creatures?[^.]*(?:dies|die|is put into a graveyard)[^.]*(?:each opponent loses|target (?:player|opponent) loses|deals? \\d+ damage to each opponent)",
        RegexOption.IGNORE_CASE,
    )
    private val counterPayoff = Regex("whenever you put [^.]*counters? on [^.]*each opponent loses", RegexOption.IGNORE_CASE)
    private val enterPayoff = Regex("whenever (?:a|another)[^.]*creature[^.]*enters[^.]*(?:each opponent loses|damage to each opponent)", RegexOption.IGNORE_CASE)
    private val tutorText = Regex("search your library for (?:a|an|up to one) ((?:[a-z]+ )*?)card", RegexOption.IGNORE_CASE)
    private val tutorTypes = listOf("creature", "artifact", "enchantment", "instant", "sorcery", "planeswalker")
    private val tapAbility = Regex("^\\{t\\}(, \\{\\d+\\})?:")
    // Since 1.54: effects that count what's on the battlefield ("for each Shrine you control"), triggers on a kind of
    // permanent entering ("whenever another Shrine you control enters"), Maze's End and the Gates.
    private val countOf = Regex("(?:where x is (twice )?the number of|for each|equal to the number of) ([a-z]+?)s? you control", RegexOption.IGNORE_CASE)
    private val abilityWord = Regex("^[a-z]+(?: [a-z]+)? — ", RegexOption.IGNORE_CASE)
    private val kindEnter = Regex("^whenever (?:[^,]*? or )?(another |an? )(nontoken )?([a-z]+) you control enters", RegexOption.IGNORE_CASE)
    private val tokenWords = Regex("(\\d+)/(\\d+) ([a-z ]*?)tokens?\\b", RegexOption.IGNORE_CASE)
    private val putKind = Regex("(?:search your library[^.]*? for|until you reveal) an? ([a-z]+) card[^.]*?(?:\\. [^.]*?)?onto the battlefield", RegexOption.IGNORE_CASE)
    private val colourWords = setOf("white", "blue", "black", "red", "green", "colorless", "legendary", "tapped", "creature")
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

    /** A token's card types and subtypes ("Shrine enchantment creature"), so it counts as a Shrine. Since 1.54. */
    data class TokenKind(val power: Int, val types: Set<String>, val subtypes: Set<String>)

    enum class Effect { DRAIN, TOKENS, DRAW, COUNTERS }

    /**
     * An effect that counts permanents of a kind ([per]: "shrine", "gate", "creature"…) times [factor]: once when the
     * card comes in, or each turn ([turn]). Since 1.54.
     */
    data class Scaled(val what: Effect, val turn: Boolean, val per: String, val factor: Int = 1, val each: Boolean = true, val token: TokenKind? = null)

    /**
     * "Whenever (this or) another [kind] you control enters": what it does (since 1.54). [self]: the card itself counts;
     * [nontoken]: tokens don't.
     */
    data class KindTrigger(
        val kind: String,
        val self: Boolean,
        val nontoken: Boolean,
        val drainEach: Int = 0,
        val drainOne: Int = 0,
        val token: TokenKind? = null,
        val tokens: Int = 0,
        val draw: Int = 0,
        val counters: Int = 0,
        val land: Boolean = false,
    )

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
        /** Its rules text in lower case, for a combo's "you have a way to …". */
        var text = ""
        /** It turns endless deaths, creatures entering or -1/-1 counters you put into life loss (Blood Artist, Purphoros, Obelisk Spider). */
        var deathPayoff = false
        var enterPayoff = false
        var counterPayoff = false
        /** A combo piece with a {T} ability other than mana (Tree of Perdition): it has to have been out since last turn. */
        var tapPiece = false
        /** A tutor: the card types it can find (empty: any card), and whether the card goes on top of the library. */
        var tutor: Set<String>? = null
        var tutorToTop = false
        /** A tutor in a deck with combos to find pieces for. */
        var tutorUseful = false
        /** Its subtypes (Shrine, Gate, Elf…) and the kind of token it makes. Since 1.54. */
        var subtypes: Set<String> = emptySet()
        var token: TokenKind? = null
        val scaled = ArrayList<Scaled>()
        val kindTriggers = ArrayList<KindTrigger>()
        /** "This creature gets +1/+1 for each Gate you control". */
        var powerPer: String? = null
        /** Puts a card of a kind onto the battlefield: once (Hei Bai) or each turn (Sanctum of All). */
        var fetchKind: String? = null
        var fetchTurn = false
        /** Maze's End; Gates it puts onto the battlefield (Circuitous Route) or into the hand (Open the Gates). */
        var mazeEnd = false
        var gateRamp = 0
        var gateToHand = 0
        var landToHand = false
        /** Crackling Perimeter: tapping Gates deals damage. Basilisk Gate: a Gate's +X/+X. Sage of the Maze: a land becomes 2X/2X. */
        var gatePing = false
        var gatePump = false
        var animates: Scaled? = null
        /** 1: lands enter untapped (Amulet of Vigor, Spelunking); 2: Gates do (Gond Gate). */
        var untapsLands = 0
        /** Sacrifice decks (since 1.54): abilities usable at once (Thousand-Year Elixir); untapping the creature it
         *  enchants (Instill Energy) or another (Patriar's Seal, Tyvar); Marvin's copied abilities. */
        var abilityHaste = false
        var untapsOwn = false
        var untapsOther = false
        var copiesAbilities = false
        /** Marionette Master: an artifact going to the graveyard costs an opponent life equal to its power. */
        var artifactDrain = false
        var fabricate = 0
        /** Tokens when a land goes to the graveyard (Titania), when a land is sacrificed (Baloth Prime), or itself (Dunes of the Dead). */
        var landLeavesToken = 0
        var landSacToken = 0
        var leavesToken = 0
        /** Mazirek: each sacrifice puts a +1/+1 counter on each creature. */
        var sacCounters = false
        /** Liquimetal Coating: makes a permanent an artifact. */
        var liquimetal = false
        /** Stun counters it enters with (Baloth Prime). */
        var stun = 0
        /** An X spell draining each opponent (Exsanguinate, Jaya's Immolating Inferno). */
        var xEach = false
        /** Double Vision: the first instant or sorcery each turn is copied. */
        var copiesFirst = false

        val land get() = mana.kind == ManaOddsCalc.Kind.LAND
        val gearBonus get() = bonus > 0 || forEach != null || base != null || grantsDouble || grantsHaste
        val pumpSpell get() = !permanent && (pump > 0 || pumpX || pumpDouble)
        val dealsDamage get() = drainOnce + drainTurn + oneOnce + oneTurn > 0 || sacrifice != null || xEach || artifactDrain

        /** It does one of the things the win turn reads since 1.54, so it's worth casting. */
        val newUse
            get() = scaled.isNotEmpty() || kindTriggers.any { it.drainEach + it.drainOne + it.tokens + it.draw + it.counters > 0 || it.land } ||
                powerPer != null || fetchKind != null || gateRamp + gateToHand > 0 || landToHand || gatePing || animates != null ||
                untapsLands > 0 || abilityHaste || untapsOwn || untapsOther || copiesAbilities || artifactDrain || liquimetal ||
                landLeavesToken + landSacToken > 0 || sacCounters || copiesFirst
    }

    internal fun classify(c: StatsCard, own: Set<Char>, lands: List<StatsCard>, comboKeys: Set<String>): WinCard {
        val mana = ManaOddsCalc.classify(c, own, lands)
        val w = WinCard(c.name, mana, ManaOddsCalc.cost(c.manaCost), c.cmc.toInt())
        w.combo = w.key in comboKeys
        val type = (c.typeLine?.takeIf { it.isNotEmpty() } ?: c.types).substringBefore(" // ")
        w.types = cardTypes.filter { it in type.substringBefore(" — ").split(' ') }.toSet()
        w.subtypes = type.substringAfter(" — ", "").split(' ').filter { it.isNotBlank() }.toSet()
        val front = c.oracleText.orEmpty().split(CardInfo.FACE_BREAK).first()
        val lower = front.lowercase()
        w.untapsLands = when {
            "lands you control enter untapped" in lower || "whenever a permanent you control enters tapped, untap it" in lower ||
                "whenever a land you control enters tapped" in lower -> 1
            "gates you control enter untapped" in lower -> 2
            else -> 0
        }
        if (w.land) {
            // A land's own abilities can still make tokens to sacrifice (Treasure Vault aside).
            front.split('\n').forEach { readArtifacts(w, it.trim()) }
            w.mazeEnd = "gates with different names, you win the game" in lower
            w.leavesToken = leavesTokenText.find(lower)?.groupValues?.get(1)?.toInt() ?: 0
            w.gatePump = Regex("target creature gets \\+x/\\+x[^.]*where x is the number of gates you control").containsMatchIn(lower)
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
        w.text = text.lowercase()
        w.deathPayoff = deathPayoff.containsMatchIn(text)
        w.enterPayoff = enterPayoff.containsMatchIn(text) || w.enterTriggers.any { !it.land }
        w.counterPayoff = counterPayoff.containsMatchIn(text)
        w.tapPiece = text.split('\n').any { line -> line.trim().startsWith("{T}") && "Add {" !in line.substringBefore('.') }
        tutorText.find(text)?.let { m ->
            val what = m.groupValues[1].lowercase()
            val once = !w.permanent || Regex("when [^.]*enters[^.]*search your library", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val landish = listOf("land", "basic", "plains", "island", "swamp", "mountain", "forest").any { it in what }
            if (once && !landish && w.mana.kind != ManaOddsCalc.Kind.RAMP) {
                w.tutor = tutorTypes.filter { it in what }.map { it.replaceFirstChar(Char::uppercase) }.toSet()
                w.tutorToTop = "on top of your library" in text.lowercase() && "into your hand" !in text.lowercase()
            }
        }
        readSacrifice(w, c.manaCost.orEmpty())
        w.leavesToken = leavesTokenText.find(w.text)?.groupValues?.get(1)?.toInt() ?: 0
        if ("gate card" in w.text && "search your library" in w.text) {
            when {
                "onto the battlefield tapped and the other into your hand" in w.text -> { w.gateRamp = 1; w.gateToHand = 1 }
                "onto the battlefield" in w.text -> w.gateRamp = Regex("search your library for up to (\\w+)").find(w.text)?.let { number(it.groupValues[1]) } ?: 1
                "into your hand" in w.text -> w.gateToHand = 1
            }
        }
        if (Regex("search your library for a land card[^.]*into your hand").containsMatchIn(w.text)) w.landToHand = true
        if ("tap an untapped gate you control:" in w.text && "damage to each opponent" in w.text) w.gatePing = true
        putKind.find(text)?.let { m ->
            val kind = m.groupValues[1].lowercase()
            if (kind !in setOf("land", "basic", "creature", "artifact", "enchantment", "gate") && w.mana.kind != ManaOddsCalc.Kind.RAMP) {
                w.fetchKind = kind
                w.fetchTurn = "at the beginning of your" in m.value.lowercase() || Regex("at the beginning of your [^.]*search your library").containsMatchIn(w.text)
            }
        }
        return w
    }

    private val leavesTokenText = Regex("when (?:this \\w+|[^,.]+) is put into a graveyard from the battlefield, create (?:a|an) (?:tapped )?(\\d+)/\\d+")

    /** What sacrifice decks and X spells need (since 1.54). */
    private fun readSacrifice(w: WinCard, cost: String) {
        val t = w.text
        w.abilityHaste = "activate abilities of creatures you control as though those creatures had haste" in t
        w.untapsOwn = w.aura && "untap enchanted creature" in t
        w.untapsOther = Regex("untap (?:up to one )?target (?:legendary )?creature").containsMatchIn(t)
        w.copiesAbilities = "has all activated abilities of creatures you control" in t
        w.artifactDrain = Regex("whenever an artifact you control is put into a graveyard from the battlefield, target opponent loses life equal to (?:this creature's|its) power").containsMatchIn(t)
        w.fabricate = Regex("fabricate (\\d+)").find(t)?.groupValues?.get(1)?.toInt() ?: 0
        w.landLeavesToken = Regex("whenever a land you control is put into a graveyard from the battlefield, create (?:a|an) (\\d+)/").find(t)?.groupValues?.get(1)?.toInt() ?: 0
        w.landSacToken = Regex("whenever you sacrifice a land, create (?:a|an) (?:tapped )?(\\d+)/").find(t)?.groupValues?.get(1)?.toInt() ?: 0
        w.sacCounters = "sacrifices another permanent, put a +1/+1 counter on each creature you control" in t
        w.liquimetal = Regex("target (?:nonland )?permanent becomes an artifact in addition").containsMatchIn(t)
        w.stun = Regex("enters tapped with (\\w+) stun counters").find(t)?.let { number(it.groupValues[1]) } ?: 0
        w.xEach = !w.permanent && "{X}" in cost &&
            Regex("each opponent loses x life|deals x damage to each of up to three targets|deals x damage to each opponent").containsMatchIn(t)
        w.copiesFirst = "whenever you cast your first instant or sorcery spell each turn, copy that spell" in t
    }

    /** A token's kind from its words: "1/1 colorless Shrine enchantment creature token". */
    private fun tokenKind(line: String): TokenKind? {
        val m = tokenWords.find(line) ?: return null
        val words = m.groupValues[3].trim().split(' ').filter { it.isNotBlank() }
        val types = setOf("Creature") + words.filter { it.lowercase() in setOf("artifact", "enchantment") }.map { it.lowercase().replaceFirstChar(Char::uppercase) }
        val subtypes = words.filter { it.lowercase() !in colourWords && it.lowercase() !in setOf("artifact", "enchantment") }.toSet()
        return TokenKind(m.groupValues[1].toIntOrNull() ?: 0, types, subtypes)
    }

    /** An effect that counts permanents ([per]); false when the line isn't one the win turn reads. Since 1.54. */
    private fun readScaled(w: WinCard, line: String, low: String, per: String, factor: Int, turn: Boolean, once: Boolean): Boolean {
        if (Regex("(?:this creature|${Regex.escape(w.name.lowercase())}) gets \\+1/\\+1 for each").containsMatchIn(low)) {
            w.powerPer = per
            return true
        }
        if ("becomes an x/x" in low && "land" in low) {
            w.animates = Scaled(Effect.TOKENS, true, per, factor)
            return true
        }
        if (!turn && !once) return false
        val what = when {
            "each opponent loses x life" in low || "x damage to each opponent" in low || "each opponent loses life equal to the number" in low -> Scaled(Effect.DRAIN, turn, per, factor)
            Regex("deals? (?:x damage to (?:target player|any target|target opponent)|damage to (?:any target|target player|target opponent)[^.]*equal to the number)").containsMatchIn(low) ->
                Scaled(Effect.DRAIN, turn, per, factor, each = false)
            "token" in low && "create" in low -> Scaled(Effect.TOKENS, turn, per, factor, token = tokenKind(line))
            Regex("draws? (?:a card for each|x cards)").containsMatchIn(low) -> Scaled(Effect.DRAW, turn, per, factor)
            Regex("put x \\+1/\\+1 counters on each creature you control").containsMatchIn(low) -> Scaled(Effect.COUNTERS, turn, per, factor)
            else -> return false
        }
        w.scaled += what
        return true
    }

    /** "Whenever (this or) another Shrine you control enters, …": what it does. Since 1.54. */
    private fun readKindTrigger(w: WinCard, line: String, low: String): Boolean {
        val m = kindEnter.find(low) ?: return false
        val kind = m.groupValues[3]
        if (kind in setOf("creature", "land", "nontoken")) return false
        val self = m.groupValues[1] != "another " || " or another" in m.value
        val rest = line.substring(m.range.last + 1)
        val e = eachOpponent.find(rest)
        val o = oneOpponent.find(rest)
        val c = create.find(rest)
        val d = drawCards.find(rest)
        w.kindTriggers += KindTrigger(
            kind = kind,
            self = self,
            nontoken = m.groupValues[2].isNotEmpty(),
            drainEach = e?.let { number(it.groupValues[1].ifEmpty { it.groupValues[2] }) } ?: 0,
            drainOne = if (e == null) o?.let { number(it.groupValues[1].ifEmpty { it.groupValues[2] }) } ?: 0 else 0,
            token = c?.let { tokenKind(rest) },
            tokens = c?.let { number(it.groupValues[1]) } ?: 0,
            draw = d?.let { number(it.groupValues[1]) } ?: 0,
            counters = if (Regex("put a \\+1/\\+1 counter on each creature you control", RegexOption.IGNORE_CASE).containsMatchIn(rest)) 1 else 0,
            land = Regex("search your library for a basic land card, put it onto the battlefield", RegexOption.IGNORE_CASE).containsMatchIn(rest),
        )
        return true
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
        val plain = line.replace(abilityWord, "")
        if (readKindTrigger(w, plain, plain.lowercase())) return
        countOf.find(low)?.let { m ->
            if (readScaled(w, line, low, m.groupValues[2], if (m.groupValues[1].isNotEmpty()) 2 else 1, recurring || tap, once)) return
        }
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
        // "Then if you control three or more Deserts, create…": a condition the win turn can't check.
        if (Regex("if you control (?:two|three|four|five|\\d+) or more [a-z]+s, create", RegexOption.IGNORE_CASE).containsMatchIn(line)) return
        create.find(line)?.let { m ->
            if (w.token == null) w.token = tokenKind(line)
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
        c.land || c.pumpSpell || c.xEach -> false
        // An instant or sorcery that's a combo piece waits for the rest of the combo.
        c.combo && !c.permanent -> false
        spellTriggers -> true
        fodder && c.permanent -> true
        creatureTriggers && c.creature -> true
        else -> c.creature && c.power > 0 || c.tokensOnce + c.tokensTurn > 0 || c.dealsDamage || c.anthem + c.overrun > 0 ||
            c.drawOnce + c.drawTurn > 0 || c.combo && c.permanent || c.castTriggers.isNotEmpty() || c.enterTriggers.isNotEmpty() ||
            (c.equipment || c.aura) && c.gearBonus || c.attackDouble || c.targetedBonus > 0 || c.combatDraw != 0 ||
            c.sacrifice != null || c.artifactsOnce + c.artifactsTurn + c.artifactsLandfall > 0 || c.tutorUseful || c.newUse
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
        val candidates = combos.mapNotNull(::plan)
        val comboKeys = candidates.flatMap { it.keys }.toSet()
        val deck = ArrayList<WinCard>()
        val kinds = ArrayList<WinCard>()
        for (c in library) {
            val w = classify(c, own, lands, comboKeys)
            kinds += w
            repeat(c.quantity.coerceAtLeast(0)) { deck += w }
        }
        val leaders = commanders.map { classify(it, own, lands, comboKeys) }
        // A loop only counts when the deck has a card that turns it into life loss (or it makes endless tokens).
        val plans = candidates.filter { settle(it, kinds + leaders) }
        val payoffKeys = plans.filter { it.kind == ComboKind.LOOP }.flatMap { it.payoffs }.toSet()
        (kinds + leaders).forEach { if (it.key in payoffKeys) it.combo = true }
        if (plans.isNotEmpty()) (kinds + leaders).forEach { if (it.tutor != null) it.tutorUseful = true }
        val won = IntArray(turns + 1)
        val byCombo = IntArray(turns + 1)
        val byMaze = IntArray(turns + 1)
        val dealt = LongArray(SOURCES.size)
        var knockouts = 0
        var wins = 0
        repeat(games) {
            val (hand, rest) = ManaOddsCalc.keepHand(deck, random) { it.mana }
            val game = Game(hand, rest.toMutableList(), leaders, own, plans)
            val turn = game.play(turns)
            if (turn > 0) {
                won[turn]++
                wins++
                if (game.byCombo) byCombo[turn]++
                if (game.byMaze) byMaze[turn]++
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
                combos = plans.map { p ->
                    p.names.joinToString(" + ") + when (p.kind) {
                        ComboKind.WINS -> ""
                        ComboKind.TOKENS -> " (endless tokens: wins the turn after)"
                        ComboKind.LOOP -> (kinds + leaders).filter { it.key in p.payoffs && it.key !in p.keys }.map { it.name }.distinct()
                            .takeIf { it.isNotEmpty() && p.keys.none { it in p.payoffs } }
                            ?.let { names -> " (with " + names.take(3).joinToString(" or ") + if (names.size > 3) "…)" else ")" } ?: ""
                    }
                },
                tutors = all.count { it.tutorUseful },
                counting = all.count { it.scaled.isNotEmpty() || it.kindTriggers.any { k -> k.drainEach + k.drainOne + k.tokens + k.draw + k.counters > 0 } || it.powerPer != null },
                sacrifice = all.count { it.untapsOwn || it.untapsOther || it.abilityHaste || it.copiesAbilities || it.artifactDrain || it.liquimetal ||
                    it.landLeavesToken + it.landSacToken + it.leavesToken > 0 || it.sacCounters },
                xSpells = all.count { it.xEach },
                mazeEnd = all.any { it.mazeEnd },
            ),
            byMaze = if (byMaze.any { it > 0 }) cumulative(byMaze) else emptyList(),
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
        val subtypes: Set<String> = card?.subtypes.orEmpty(),
    ) {
        /** +1/+1 counters on it (since 1.54). */
        var counters = 0
        /** Stun counters, and the turn the last one came off (it stays tapped that turn). */
        var stun = card?.stun ?: 0
        var stunTurn = 0

        fun isA(kind: String) = subtypes.any { it.equals(kind, true) } || types.any { it.equals(kind, true) }
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
        val rest: MutableList<WinCard>,
        val leaders: List<WinCard>,
        val own: Set<Char>,
        val combos: List<ComboPlan>,
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
        /** Won with Maze's End (since 1.54). */
        var byMaze = false
        val mazeDeck = (hand + rest).any { it.mazeEnd }
        var instantsCast = 0

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

        fun token(power: Int, kind: TokenKind? = null): Entry =
            Entry(null, turn, power, true, false, false, kind?.types ?: CREATURE_TOKEN, kind?.subtypes.orEmpty()).also {
                board += it
                entered(it, false)
                arrived(it)
            }

        /** How many permanents of a kind are on the battlefield ("shrine", "gate", "creature"…). Since 1.54. */
        fun count(kind: String) = board.count { it.isA(kind) }

        /** The Gates on the battlefield with different names, for Maze's End. */
        fun gateNames() = board.filter { it.isA("gate") }.mapNotNull { it.card?.name }.toSet()

        /** How deep "enters" triggers have set off more of them (a guard against loops). */
        var depth = 0

        /** "Whenever another Shrine you control enters" and the like (since 1.54). */
        fun arrived(e: Entry) {
            if (depth > 3) return
            depth++
            for (b in board.toList()) b.card?.kindTriggers?.forEach { t ->
                if (!e.isA(t.kind) || (!t.self && b === e) || (t.nontoken && e.card == null)) return@forEach
                each(t.drainEach, 3)
                one(t.drainOne, 3)
                repeat(t.tokens) { token(t.token?.power ?: 1, t.token) }
                draw(t.draw)
                if (t.counters > 0) board.filter { it.creature }.forEach { it.counters += t.counters }
                if (t.land) sources += Source(own, 1, turn + 1)
            }
            depth--
        }

        /** An effect counting permanents (since 1.54). */
        fun scaled(s: Scaled) {
            val n = count(s.per) * s.factor
            if (n <= 0) return
            when (s.what) {
                Effect.DRAIN -> if (s.each) each(n, 3) else one(n, 3)
                Effect.TOKENS -> repeat(minOf(n, 30)) { token(s.token?.power ?: 1, s.token) }
                Effect.DRAW -> draw(n)
                Effect.COUNTERS -> board.filter { it.creature }.forEach { it.counters += n }
            }
        }

        /** Whether a land coming in now is untapped: its own text, or Amulet of Vigor, Spelunking, Gond Gate… */
        fun untapped(land: WinCard?) = land?.mana?.tapped != true ||
            board.any { b -> b.card?.untapsLands?.let { it == 1 || it == 2 && land.subtypes.contains("Gate") } == true }

        /** A Gate with a new name from the library onto the battlefield (Maze's End, Circuitous Route). */
        fun putGate() {
            val names = gateNames()
            val i = (drawn until rest.size).firstOrNull { rest[it].land && "Gate" in rest[it].subtypes && rest[it].name !in names } ?: return
            val gate = rest.removeAt(i)
            val source = Source(gate.mana.colours, 1, if (untapped(gate)) turn else turn + 1)
            sources += source
            val e = Entry(gate, turn, 0, false, false, false).also { it.source = source }
            board += e
            entered(null, true)
            arrived(e)
        }

        /** A card with a new name, of what [want] picks, from the library into the hand. */
        fun fetchToHand(want: (WinCard) -> Boolean) {
            val i = (drawn until rest.size).firstOrNull { want(rest[it]) } ?: return
            hand += rest.removeAt(i)
        }

        fun wantedGate(c: WinCard) = c.land && "Gate" in c.subtypes && c.name !in gateNames() && hand.none { it.name == c.name }

        /** A card of a kind from the library onto the battlefield (Sanctum of All, Hei Bai). */
        fun fetch(kind: String) {
            val i = (drawn until rest.size).firstOrNull { i -> rest[i].permanent && !rest[i].land && rest[i].subtypes.any { it.equals(kind, true) } } ?: return
            val c = rest.removeAt(i)
            hand += c
            cast(c, free = true)
        }

        /** An X spell in hand, with the X the mana allows (since 1.54). */
        fun xSpell(pool: List<Pair<Set<Char>, Int>>): Pair<WinCard, Int>? {
            val c = hand.filter { it.xEach }.minByOrNull { it.manaValue } ?: return null
            val left = ManaOddsCalc.pay(pool, c.cost) ?: return null
            return c to left.sumOf { it.second }
        }

        /**
         * Maze's End: {3}, {T}, back to the hand: a Gate with a new name onto the battlefield; ten Gates with different
         * names win. Returns the mana left, or null when it can't be done now.
         */
        fun maze(pool: List<Pair<Set<Char>, Int>>): List<Pair<Set<Char>, Int>>? {
            val m = board.firstOrNull { it.card?.mazeEnd == true && (it.source?.from ?: Int.MAX_VALUE) <= turn } ?: return null
            // Its own {C} can't help pay: it taps for the ability.
            val left = ManaOddsCalc.pay(pool, ManaOddsCalc.Cost(4, emptyList())) ?: return null
            board.remove(m)
            m.source?.let { sources.remove(it) }
            hand += m.card!!
            putGate()
            if (gateNames().size >= MAZE_GATES) byMaze = true
            return left
        }

        fun artifacts(n: Int) = repeat(n) { board += Entry(null, turn, 0, false, false, false, ARTIFACT_TOKEN) }

        /** Who carries the equipment and auras and gets the pump spells: the commander, else the biggest creature. */
        fun carrier(): Entry? = board.filter { it.creature && it.card in leaders }.maxByOrNull { it.power }
            ?: board.filter { it.creature }.maxByOrNull { it.power + it.gear.size }

        fun cast(c: WinCard, free: Boolean = false, x: Int = 0): Int {
            if (!command.remove(c)) hand.remove(c)
            if (!free) for (b in board) b.card?.castTriggers?.forEach { t ->
                if (t.firstOnly && spells > 0) return@forEach
                if (t.filter == Filter.CREATURE && !c.creature || t.filter == Filter.NONCREATURE && c.creature) return@forEach
                val n = if (t.amount == MANA_VALUE) c.manaValue + x else t.amount
                if (t.each) each(n, 2) else one(n, 2)
            }
            if (!free) spells++
            // In a Maze's End deck, Gate searches bring real Gates (since 1.54).
            if (mazeDeck) {
                repeat(c.gateRamp) { putGate() }
                repeat(c.gateToHand) { fetchToHand(::wantedGate) }
                if (c.landToHand) fetchToHand { l -> if (board.none { it.card?.mazeEnd == true } && hand.none { it.mazeEnd }) l.mazeEnd else wantedGate(l) }
            }
            val source = if (mazeDeck && c.gateRamp > 0) null else when (c.mana.kind) {
                ManaOddsCalc.Kind.RAMP -> Source(c.mana.colours, 1, if (c.mana.tapped) turn + 1 else turn).also {
                    board += Entry(null, turn, 0, false, false, false, LAND).apply { this.source = it }
                }
                ManaOddsCalc.Kind.CREATURE -> Source(c.mana.colours, c.mana.amount, turn + 1)
                ManaOddsCalc.Kind.ROCK -> Source(c.mana.colours, c.mana.amount, turn)
                else -> null
            }
            source?.let { sources += it }
            // Double Vision copies the first instant or sorcery each turn (since 1.54).
            val copies = if (!c.permanent && instantsCast++ == 0 && board.any { it.card?.copiesFirst == true }) 2 else 1
            repeat(copies) {
                each(c.drainOnce, 3)
                one(c.oneOnce, 3)
                if (c.xEach) each(x, 3)
            }
            draw(c.drawOnce)
            c.tutor?.takeIf { c.tutorUseful }?.let { tutor(it, c.tutorToTop) }
            if (c.permanent) {
                val e = Entry(c, turn, c.power, c.creature, c.haste, c.double)
                e.counters = c.fabricate
                if (c.mana.kind != ManaOddsCalc.Kind.RAMP) e.source = source
                board += e
                if (c.castTriggers.isNotEmpty()) spellTriggers = true
                if (c.castTriggers.any { it.firstOnly }) firstSpellTrigger = true
                if (c.enterTriggers.any { !it.land }) creatureTriggers = true
                if (c.creature) entered(e, false)
                arrived(e)
                c.fetchKind?.takeIf { !c.fetchTurn }?.let { fetch(it) }
                val germ = c.equipsToken
                if (c.equipment && germ != null) {
                    token(germ).also { it.gear += c; e.on = it }
                } else if (c.aura) {
                    carrier()?.let { it.gear += c; e.on = it }
                }
            }
            if (c.tokensOnce > 0) token(c.tokensOnce, c.token)
            artifacts(c.artifactsOnce)
            c.scaled.filter { !it.turn }.forEach { scaled(it) }
            return if (c.permanent) 0 else c.overrun
        }

        fun pool() = sources.filter { it.from <= turn }.map { it.colours to it.amount }

        fun gearPower(b: Entry): Pair<Int, Boolean> {
            var base = b.power + b.counters + (b.card?.powerPer?.let { count(it) } ?: 0)
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
                // A stun counter comes off instead of untapping (Baloth Prime).
                board.filter { it.stun > 0 && it.turn < t }.forEach { it.stun--; it.stunTurn = t }
                draw(1 + board.sumOf { it.card?.drawTurn ?: 0 })
                board.toList().forEach { b -> b.card?.let { c -> c.scaled.filter { it.turn && it.what == Effect.DRAW }.forEach { scaled(it) } } }
                board.toList().forEach { b -> b.card?.takeIf { it.fetchTurn }?.fetchKind?.let { fetch(it) } }
                // Maze's End goes first, so it can come back as this turn's land.
                // Paid again from this turn's pool once the land is down (its own {C} is gone with it).
                var mazeSpent = 0
                if (mazeDeck) maze(pool())?.let { mazeSpent = 3 }
                if (byMaze) return t
                playLand()
                var pool = pool()
                if (mazeSpent > 0) pool = ManaOddsCalc.pay(pool, ManaOddsCalc.Cost(mazeSpent, emptyList())) ?: emptyList()
                // With Amulet of Vigor and the like it comes in untapped and can go again.
                if (mazeDeck && mazeSpent > 0) maze(pool)?.let { pool = it }
                if (byMaze) return t
                var overrun = 0
                instantsCast = 0
                // With a "first spell each turn" trigger, the biggest spell goes first: an X spell with all the mana
                // when that's bigger (since 1.54).
                val bigX = if (firstSpellTrigger) xSpell(pool) else null
                val biggest = (hand + command).filter { !it.land && ManaOddsCalc.pay(pool, it.cost) != null && !it.xEach }.maxOfOrNull { it.manaValue } ?: 0
                if (bigX != null && bigX.second >= 2 && bigX.first.manaValue + bigX.second > biggest) {
                    cast(bigX.first, x = bigX.second)
                    pool = emptyList()
                    if (life.all { it <= 0 }) return t
                } else if (firstSpellTrigger) {
                    for (c in (hand + command).filter { !it.land && !it.xEach }.sortedByDescending { it.manaValue }) {
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
                when (comboReady(pool)) {
                    ComboKind.WINS, ComboKind.LOOP -> {
                        byCombo = true
                        return t
                    }
                    // Endless tokens attack and win the turn after.
                    ComboKind.TOKENS -> {
                        byCombo = true
                        return if (t < turns) t + 1 else 0
                    }
                    null -> Unit
                }
                // An X spell that finishes the opponents goes first (since 1.54).
                xSpell(pool)?.let { (c, x) ->
                    val copies = if (board.any { it.card?.copiesFirst == true } && instantsCast == 0) 2 else 1
                    if (x * copies >= life.filter { it > 0 }.maxOrNull() ?: 0) {
                        cast(c, x = x)
                        pool = emptyList()
                        if (life.all { it <= 0 }) return t
                    }
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
                // What's left of the mana goes into an X spell (since 1.54).
                xSpell(pool)?.takeIf { it.second >= 3 }?.let { (c, x) ->
                    cast(c, x = x)
                    pool = emptyList()
                    if (life.all { it <= 0 }) return t
                }
                pool = equip(pool)
                val anthem = board.sumOf { it.card?.anthem ?: 0 }
                for (b in board.toList()) {
                    val c = b.card ?: continue
                    each(c.drainTurn, 3)
                    one(c.oneTurn, 3)
                    if (c.tokensTurn > 0) token(c.tokensTurn, c.token)
                    artifacts(c.artifactsTurn)
                    c.scaled.filter { it.turn && it.what != Effect.DRAW }.forEach { scaled(it) }
                }
                // Crackling Perimeter: the Gates not needed for mana deal 1 damage each (since 1.54).
                if (board.any { it.card?.gatePing == true }) {
                    val n = minOf(count("gate"), pool.sumOf { it.second })
                    each(n, 2)
                    pool = ManaOddsCalc.pay(pool, ManaOddsCalc.Cost(n, emptyList())) ?: pool
                }
                sacrificeForValue(t)
                val attackers = board.filter { b ->
                    b.creature && b.tapped != t && b.stun == 0 && b.stunTurn != t && (b.turn < t || b.haste || b.gear.any { it.grantsHaste })
                }
                val extra = HashMap<Entry, Int>()
                val doubled = HashSet<Entry>()
                if (attackers.isNotEmpty()) {
                    val carrier = carrier()
                    val hit = carrier?.takeIf { it in attackers } ?: attackers.maxBy { it.power + it.gear.size }
                    if (board.any { it.card?.attackDouble == true }) doubled += hit
                    val targeted = board.sumOf { it.card?.targetedBonus ?: 0 }
                    // Basilisk Gate: {2}, {T}: +X/+X, X the Gates (since 1.54).
                    if (board.any { it.card?.gatePump == true }) ManaOddsCalc.pay(pool, ManaOddsCalc.Cost(3, emptyList()))?.let {
                        pool = it
                        extra[hit] = (extra[hit] ?: 0) + count("gate")
                    }
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
                // Sage of the Maze: a land becomes a 2X/2X with haste, X the Gates (since 1.54).
                board.firstNotNullOfOrNull { b -> b.card?.animates?.takeIf { b.turn < t && b.tapped != t } }?.let { combat += count(it.per) * it.factor }
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
            val hasteAbilities = board.any { it.card?.abilityHaste == true }
            // Marvin has the abilities of the other creatures (since 1.54).
            val shared = board.firstNotNullOfOrNull { it.card?.sacrifice }
            var untaps = board.count { it.card?.untapsOther == true }
            var liquimetal = board.count { it.card?.liquimetal == true }
            for (user in board.toList()) {
                val ability = user.card?.sacrifice ?: shared?.takeIf { user.card?.copiesAbilities == true } ?: continue
                if (user !in board || !(user.turn < t || user.haste || hasteAbilities || user.gear.any { it.grantsHaste })) continue
                // Instill Energy on it untaps it once; Patriar's Seal, Thousand-Year Elixir or Tyvar untap one creature each.
                var uses = 1 + (if (user.gear.any { it.untapsOwn }) 1 else 0)
                while (uses > 0) {
                    val usedLiquimetal = sacrificeOnce(user, ability, liquimetal > 0) ?: break
                    if (usedLiquimetal) liquimetal--
                    uses--
                    if (uses == 0 && untaps > 0) {
                        untaps--
                        uses++
                    }
                }
            }
        }

        /**
         * One use of a sacrifice ability: whether Liquimetal had to make one of the permanents an artifact, or null when
         * there's nothing good enough to sacrifice.
         */
        fun sacrificeOnce(user: Entry, ability: SacrificeAbility, liquimetal: Boolean): Boolean? {
            val t = turn
            val lands = board.count { LAND_TYPE in it.types }
            fun cost(e: Entry): Int = when {
                e.card == null && !e.creature && LAND_TYPE !in e.types -> 0
                e.card == null && e.creature -> 1 + (e.power + e.counters) / 2
                LAND_TYPE in e.types && !e.creature -> if (lands > 5) 2 else 8
                else -> 3 + (e.card?.manaValue ?: 0) + e.power + e.counters + 4 * e.gear.size +
                    (if (e.card?.let { it.anthem > 0 || it.castTriggers.isNotEmpty() || it.enterTriggers.isNotEmpty() || it.drawTurn > 0 || it.artifactDrain || it.sacCounters } == true) 4 else 0)
            }
            val candidates = board.filter { it !== user && it.types.isNotEmpty() && it.card !in leaders && it.on == null }
                .sortedBy(::cost).take(8)
            val artifactBit = 1 shl cardTypes.indexOf("Artifact")
            // Liquimetal Coating can make one of them an artifact.
            val free = if (liquimetal) artifactBit else 0
            val masks = IntArray(candidates.size) { i -> candidates[i].types.fold(0) { m, ty -> m or (1 shl cardTypes.indexOf(ty)) } }
            if (Integer.bitCount(masks.fold(free) { a, m -> a or m }) < ability.types) return null
            val costs = IntArray(candidates.size) { cost(candidates[it]) }
            // The cheapest set of up to [count] permanents covering enough types (at most 8 to choose from).
            var best = 0
            var bestCost = Int.MAX_VALUE
            var bestMask = 0
            fun search(from: Int, picked: Int, mask: Int, total: Int, size: Int) {
                if (total >= bestCost) return
                if (Integer.bitCount(mask or free) >= ability.types) {
                    best = picked
                    bestCost = total
                    bestMask = mask
                    return
                }
                if (size == ability.count) return
                for (i in from until candidates.size) search(i + 1, picked or (1 shl i), mask or masks[i], total + costs[i], size + 1)
            }
            search(0, 0, 0, 0, 0)
            if (bestCost == Int.MAX_VALUE) return null
            val usedLiquimetal = liquimetal && Integer.bitCount(bestMask) < ability.types
            val chosen = candidates.filterIndexed { i, _ -> best and (1 shl i) != 0 }
            for (e in chosen) {
                board.remove(e)
                e.source?.let { sources.remove(it) }
                board.filter { it.on === e }.forEach { g -> g.on = null; if (g.card?.aura == true) board.remove(g) }
                left(e, artifact = "Artifact" in e.types || usedLiquimetal && e === chosen.first())
            }
            user.tapped = t
            each(ability.drain, 3)
            draw(ability.draw)
            return usedLiquimetal
        }

        /** A sacrificed permanent: what the deck's cards do about it (since 1.54). */
        fun left(e: Entry, artifact: Boolean) {
            val land = LAND_TYPE in e.types
            for (b in board.toList()) {
                val c = b.card ?: continue
                if (artifact && c.artifactDrain) one(b.power + b.counters, 3)
                if (land && c.landLeavesToken > 0) token(c.landLeavesToken)
                if (land && c.landSacToken > 0) {
                    token(c.landSacToken)
                    // "… and untap this creature": a stun counter comes off instead.
                    if (b.stun > 0) { b.stun--; b.stunTurn = turn }
                }
                if (c.sacCounters) board.filter { it.creature }.forEach { it.counters++ }
            }
            e.card?.leavesToken?.takeIf { it > 0 }?.let { token(it) }
        }

        fun playLand() {
            // An untapped land first, then the one adding the most missing colours (as for the commander odds).
            val have = HashSet<Char>()
            sources.forEach { have += it.colours }
            val lands = hand.filter { it.land }
            val names = gateNames()
            // A Maze's End deck plays Maze's End, then Gates with new names (since 1.54).
            val land = (if (mazeDeck) lands.firstOrNull { it.mazeEnd } ?: lands.firstOrNull { "Gate" in it.subtypes && it.name !in names } else null)
                ?: lands.minByOrNull { l -> (if (l.mana.tapped) 10 else 0) - l.mana.colours.count { it !in have } } ?: return
            hand.remove(land)
            val source = Source(land.mana.colours, 1, if (untapped(land)) turn else turn + 1)
            sources += source
            val e = Entry(land, turn, 0, false, false, false).also { it.source = source }
            board += e
            artifacts(land.artifactsOnce)
            entered(null, true)
            arrived(e)
            board.toList().forEach { b -> b.card?.let { artifacts(it.artifactsLandfall) } }
        }

        /**
         * The first combo that can be finished now: its missing cards at hand (or in the command zone) with the mana to
         * cast them, its {T} pieces out since last turn, the creatures and enablers it needs on the battlefield, and for
         * a loop a card that turns it into life loss (castable with it when it isn't out yet). Null when there's none.
         */
        fun comboReady(pool: List<Pair<Set<Char>, Int>>): ComboKind? {
            if (combos.isEmpty()) return null
            val there = board.mapNotNull { it.card?.key }.toSet()
            val at = (hand + command).associateBy { it.key }
            for (combo in combos) {
                val need = combo.keys - there
                if (!need.all { it in at }) continue
                if (combo.opponentCreature && turn < OPPONENTS_HAVE_CREATURES) continue
                if (combo.keys.any { k -> at[k]?.tapPiece == true && k !in there }) continue
                if (board.any { e -> e.card?.let { it.key in combo.keys && it.tapPiece } == true && e.turn >= turn && !e.haste }) continue
                if (board.count { it.creature && it.card?.key !in combo.keys } < combo.creatures) continue
                if (combo.enablers.any { words ->
                        board.none { e -> e.card?.let { c -> c.key !in combo.keys && !c.land && words.any { it in c.text } } == true }
                    }
                ) continue
                val cast = need.map { at.getValue(it) }.toMutableList()
                if (combo.kind == ComboKind.LOOP && combo.keys.none { it in combo.payoffs } && combo.payoffs.none { it in there }) {
                    cast += hand.filter { it.key in combo.payoffs }.minByOrNull { it.manaValue } ?: continue
                }
                var p: List<Pair<Set<Char>, Int>>? = pool
                for (c in cast.sortedByDescending { it.manaValue }) p = p?.let { ManaOddsCalc.pay(it, c.cost) }
                if (p != null) return combo.kind
            }
            return null
        }

        /**
         * A tutor finds the missing piece (of the types it can find) of the combo closest to done; for a loop also the
         * card that makes it win. Into the hand, or on top of the library for Vampiric Tutor and the like.
         */
        fun tutor(types: Set<String>, toTop: Boolean) {
            val have = (board.mapNotNull { it.card } + hand + command)
            val haveKeys = have.map { it.key }.toSet()
            val wanted = combos.map { p ->
                val missing = (p.keys - haveKeys).toMutableSet()
                if (p.kind == ComboKind.LOOP && p.keys.none { it in p.payoffs } && haveKeys.none { it in p.payoffs }) missing += PAYOFF
                p to missing
            }.filter { it.second.isNotEmpty() }.sortedBy { it.second.size }
            for ((plan, missing) in wanted) {
                fun wantedPayoffs(c: WinCard) = c.key in plan.payoffs
                val i = (drawn until rest.size).firstOrNull { i ->
                    val c = rest[i]
                    (c.key in missing || PAYOFF in missing && wantedPayoffs(c)) && (types.isEmpty() || c.types.any { it in types })
                } ?: continue
                val card = rest.removeAt(i)
                if (toTop) rest.add(drawn, card) else hand += card
                return
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
            /** Opponents are taken to have a creature (for combos that need one) from this turn. */
            const val OPPONENTS_HAVE_CREATURES = 4
            const val MAZE_GATES = 10
            /** Stands for "a card that turns the loop into life loss" among a combo's missing cards. */
            const val PAYOFF = "\u0000payoff"
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
                combos.forEach { append(it.cards).append(it.results).append(it.needsBoard).append(it.prerequisites).append('\n') }
            }
            return MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
