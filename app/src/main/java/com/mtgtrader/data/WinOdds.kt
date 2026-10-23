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
    /** Cards that fill or use the graveyard: mill, looting, reanimation, casting from it (since 1.55). */
    val graveyard: Int = 0,
    /** Cards that copy or flicker creatures, or cheat them in (polymorph, Sneak Attack) (since 1.55). */
    val copies: Int = 0,
    /** Planeswalkers, with the loyalty abilities the win turn reads (since 1.55). */
    val planeswalkers: Int = 0,
    /** Cascade, discover and paradigm cards (since 1.55). */
    val cascade: Int = 0,
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
 *   unless they have haste; each attacker hits one opponent (since 1.56);
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
 * Since 1.55 also:
 * - mana: ramp spells' every land, Treasures as one-use mana (Ganax, Goldspan, Xorn), cost reducers ("Dragon spells
 *   you cast cost {2} less", affinity), mana creatures with haste; lands and rocks pay first, and a creature that taps
 *   for mana or for an ability (Vohar, Tawnos) doesn't attack;
 * - pumps: team pumps from creatures entering or attacking, +X/+X for the creatures you control (Craterhoof), lords of
 *   a creature type, pumps paid with the mana left, an attacker's own pump (Ruby), Haunted One, haste for many,
 *   myriad;
 * - life loss inside longer sentences, "enters or attacks", X for the number of Dragons, devotion, Terror of the Peaks,
 *   landfall tokens and counters, token and death drains, attack triggers only when the creature attacks (Cloud);
 * - a graveyard: mill, looting (the discard picked by rules: a creature to bring back in a deck that reanimates,
 *   then spare lands), surveil; reanimation (the creature worth most, mass reanimation when it's worth it), casting
 *   from the graveyard, flashback, "whenever cards leave your graveyard" (Teval, Quintorius), discard payoffs;
 * - copies and flicker of the creature worth most on entering, Tawnos's abilities, polymorph (Jace), Sneak Attack,
 *   X spells making tokens; planeswalkers using one loyalty ability a turn;
 * - cascade and discover (the spell found cast for free, with its own triggers), Improvisation Capstone, paradigm (a
 *   free copy every turn after).
 * Nothing else is counted: no opponents, removal or abilities the text patterns don't recognise, so it's a best case
 * to compare decks by, not a promise. Since 1.52.
 */
object WinOddsCalc {
    const val GAMES = 10_000
    const val TURNS = 15

    /**
     * Raised when the rules change, so results kept on the phone are worked out again. 2: combos and tutors (1.53).
     * 3: counting Shrines and Gates, Maze's End, sacrifice decks and X spells (1.54). 4: ramp, Treasures, pumps, the
     * graveyard, copies and planeswalkers (1.55). 5: each attacker hits one opponent (1.56).
     */
    const val VERSION = 5

    private const val OPPONENTS = 3
    private const val LIFE = 40
    private const val COMMANDER_LETHAL = 21
    private const val MANA_VALUE = -1
    /** Damage equal to the entering creature's power (Terror of the Peaks). Since 1.55. */
    private const val POWER = -2
    /** Every one (Living Death, Mass Polymorph, Zombie Apocalypse). Since 1.55. */
    const val ALL = 99
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

    private val numbers = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10,
    )
    private fun number(s: String?) = s?.lowercase()?.let { numbers[it] ?: it.toIntOrNull() } ?: 0

    private const val N = "(\\d+|x|a|an|one|two|three|four|five|six)"
    private val I = RegexOption.IGNORE_CASE
    private val create = Regex("create $N (?:tapped )?(?:[\\w-]+ ){0,4}?(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
    private val drawCards = Regex("draws? $N cards?", RegexOption.IGNORE_CASE)
    // "Each opponent discards a card and loses 2 life", "target opponent sacrifices …, discards a card, and loses 3
    // life" (since 1.55: the life loss inside a longer sentence).
    private const val ALSO = "(?:(?:sacrifices|discards|exiles|mills)[^,.]*?(?:, and |, | and ))*"
    private val eachOpponent = Regex("each opponent ${ALSO}loses (\\d+|x) life|deals? (\\d+|x) damage to each opponent", RegexOption.IGNORE_CASE)
    private val oneOpponent = Regex(
        "deals? (\\d+|x) damage to (?:any target|target (?:player|opponent)|that player|target opponent or planeswalker|target player or planeswalker|the chosen player)" +
            "|target (?:player|opponent) ${ALSO}loses (\\d+|x) life",
        RegexOption.IGNORE_CASE,
    )
    private val manaValueDamage = Regex("(?:deals? damage|loses life) equal to (?:that spell's|its) mana value", RegexOption.IGNORE_CASE)
    private val castTrigger = Regex(
        "whenever (you|a player) casts? (your first spell each turn|their first spell each turn|a spell|an instant or sorcery spell|a noncreature spell|a creature spell)",
        RegexOption.IGNORE_CASE,
    )
    private val enterTrigger = Regex("^whenever (another )?(nontoken )?(creature|land)( card)? (you control )?enters(?! the battlefield under an opponent)", RegexOption.IGNORE_CASE)
    private val keywordLine = Regex("^[a-z ,'-]+$")
    private val discoverPower = Regex("^whenever you cast a creature spell with power (\\d+) or greater, discover x, where x is that spell's mana value")
    private val discoverEnter = Regex("^when [^,]*enters[^,]*, discover (\\d+)\\.?$")
    private val capstone = Regex("^exile cards from the top of your library until you exile cards with total mana value (\\d+) or greater\\. you may cast any number of spells from among them")
    private val newCounters = Regex("^when [^,]*enters, put (\\w+) \\+1/\\+1 counters on each of any number of target creatures that entered this turn")
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
    private val artifactToken = Regex("create $N (?:tapped )?(food|treasure|clue|blood|map|powerstone|gold|junk|lander|incubator|mutagen) tokens?", RegexOption.IGNORE_CASE)
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
    private val colourWords = setOf("white", "blue", "black", "red", "green", "colorless", "legendary", "tapped", "creature", "and")
    private val combatDraw = Regex("deals combat damage to a player, [^.]*?draws? (a card|that many cards)", RegexOption.IGNORE_CASE)

    // Since 1.55.
    private val perKindX = Regex("where x is the number of ([a-z]+?)s? you control", I)
    private val devotionText = Regex("each opponent loses x life, where x is your devotion to (white|blue|black|red|green)", I)
    private val powerDamage = Regex("deals damage equal to that creature's power to (?:any target|target (?:player|opponent)|each opponent)", I)
    private val lordText = Regex("^(other )?((?:[a-z]+ )*?)(creatures|[a-z]+s)(?: you control)?(?: that are ([a-z]+)s(?: and/or tokens)?)? get \\+(\\d+)/\\+\\d+", I)
    private val twoLords = Regex("^([a-z]+)s you control and other ([a-z]+)s you control get \\+(\\d+)/\\+\\d+", I)
    private val pumpPerCreature = Regex("creatures you control [^.]*?get \\+x/\\+x until end of turn, where x is the number of creatures you control", I)
    private val activatedPump = Regex(
        "^((?:\\{[^}]+\\})+): (?:(other )?((?:[a-z]+ )*?)(creatures|[a-z]+s)(?: you control)?|(this creature|[a-z ,'-]+?)) gets? \\+(\\d+)/\\+\\d+ until end of turn\\.?$",
        I,
    )
    private val hasteFor = Regex("^(?:other )?([a-z]+?)s? you control (?:get [^.]*? and )?(?:have|gain) (?:[a-z]+, )*(?:[a-z]+ and )?haste(?! until)", I)
    private val backgroundPump = Regex(
        "commander creatures you own have \"whenever this creature becomes tapped, it and other creatures you control that share a creature type with it each get \\+(\\d+)/",
        I,
    )
    private val reducer = Regex("^((?:[a-z]+ (?:and |and/or ))*[a-z]+) spells you cast cost \\{(\\d+)\\} less to cast", I)
    private val affinity = Regex("affinity for ([a-z]+?)s?\\b|this spell costs \\{1\\} less to cast for each ([a-z ]+?) you control", I)
    private val manaDraw = Regex("when you spend this mana to cast a spell with mana value (\\d+) or greater, draw a card", I)
    private val tokenDrain = Regex("^whenever you create or sacrifice a token, each opponent loses (\\d+) life", I)
    private val tokenBatchDrain = Regex("^whenever one or more tokens you control enter, each opponent loses (\\d+) life", I)
    private val dieDrain = Regex("^whenever another creature or artifact you control is put into a graveyard from the battlefield, each opponent loses (\\d+) life", I)
    private val attackKindToken = Regex("^whenever (?:a|another) ([a-z]+) you control attacks, create (?:a|an) (\\d+)/(\\d+)", I)
    private val equippedAttack = Regex("^whenever equipped creature attacks", I)
    private val youAttack = Regex("^whenever you attack(?: with one or more ([a-z]+?)s?)?(?: a player)?,", I)
    private val landfallCounters = Regex("put a \\+1/\\+1 counter on each ([a-z]+) (?:creature )?you control", I)
    private val attachOnEnter = Regex("when [^,]+ enters, attach (?:up to one )?target equipment you control to it", I)
    private val attachEntering = Regex("whenever [^,]* or another equipment you control enters, you may attach that equipment", I)
    private val equipLess = Regex("equip abilities you activate of other equipment cost \\{(\\d)\\} less", I)
    private val loyaltyLine = Regex("^([+−-]?)(\\d+|x): (.*)$", I)
    private val sneak = Regex("^((?:\\{[^}]+\\})+): you may put a creature card from your hand onto the battlefield\\. that creature gains haste\\. sacrifice", I)
    private val xTokens = Regex("create x (?:[\\w-]+ ){0,4}?(\\d+)/(\\d+)", I)
    private val leaveToken = Regex("^whenever one or more cards leave your graveyard(?: during your turn)?, create (?:a|an) (\\d+)/(\\d+)", I)
    private val leaveDraw = Regex("^whenever (?:a card|one or more cards) leaves? your graveyard(?: during your turn)?, (?:you )?draw a card", I)
    private val endStepExile = Regex("^at the beginning of your end step, exile target ([a-z, ]+?) card from your graveyard\\. create", I)
    private val castFromGraveyard = Regex("you may cast (?:an? |target )?([a-z]+ )?creature (?:spell|card) from your graveyard", I)
    private val selfFromGraveyard = Regex("you may cast this card from your graveyard as long as you control an? ([a-z]+)", I)
    private val flashback = Regex("^(?:flashback ((?:\\{[^}]+\\})+)|jump-start)", setOf(I, RegexOption.MULTILINE))
    private val discardCreatureToken = Regex("^whenever you discard a creature card, create (?:a|an) (\\d+)/", I)
    private val danse = Regex("each player sacrifices a nontoken creature[^/]*?return a creature card put into a graveyard this way to the battlefield", setOf(I, RegexOption.DOT_MATCHES_ALL))
    private val enterCopy = Regex("enter as a copy of (?:any|an?) (?:artifact or )?creature", I)
    private val copyReanimated = Regex("if they entered or were cast from a graveyard, create a token that's a copy of one of them", I)
    // What an ability, a spell or a trigger does with the graveyard, copies and cheating (see [Ability]).
    private val lootText = Regex("draws? (\\w+) cards?(?:,|\\.)? then discards? (\\w+) cards?", I)
    private val discardThenDraw = Regex("discard (\\w+) cards?\\. if you do, draw (\\w+) cards?", I)
    private val lootThatMany = Regex("draw that many cards, then discard that many cards", I)
    private val millText = Regex("(?:^|[.,:—] |then |and |you )(?:each player )?mills? (\\w+) cards?", I)
    private val surveilText = Regex("surveil (\\d+)", I)
    private val reanimateText = Regex(
        "(?:return|put) (?:up to (\\w+) )?target ((?:non-)?[a-z]+ )?((?:creature|permanent) )?cards? (?:with [^,.]+? )?from (?:your|a) graveyard (?:to|onto) the battlefield",
        I,
    )
    private val reanimateSome = Regex("return (a|two) creature cards? from your graveyard to the battlefield", I)
    private val reanimateChosen = Regex("choose (two|a) target creature cards? in your graveyard\\. sacrifice a creature\\. if you do, return the chosen cards to the battlefield", I)
    private val reanimateEach = Regex("choose a creature (?:or planeswalker )?card in that player's graveyard\\. put those cards onto the battlefield", I)
    private val reanimateAll = Regex(
        "(?:return|returns) all (?:([a-z]+) )?creature cards (of a type chosen this way )?from (?:your|their) graveyard to the battlefield" +
            "|(exiles all creature cards from their graveyard, then sacrifices all creatures they control, then puts all cards they exiled this way onto the battlefield)",
        I,
    )
    private val toHandText = Regex("return (?:up to (\\w+) )?target ([a-z ]*?)cards? from your graveyard to your hand", I)
    private val flickerText = Regex("exile (?:up to )?(\\w+) target [^.]*?you control, then return (?:those cards|them|that card|it) to the battlefield", I)
    private val copyText = Regex(
        "create (a|two) tokens? that(?:'s| are) (?:a )?cop(?:y|ies) of (?:up to one )?(?:target )?(?:(?:nonlegendary |another )?(?:artifact or creature|creature|permanent)(?: you control)?|it)\\b",
        I,
    )
    private val copyArtifactToken = Regex("create a token that's a copy of (?:up to one )?target artifact token you control", I)
    private val copyExiled = Regex("create a token that's a copy of the exiled card", I)
    private val polymorph = Regex("reveals? cards from the top of (?:your|their) library until (?:you|they) reveal (a creature|that many creature)", I)
    private val doubleStrikeFor = Regex("([a-z]+?)s? you control gain double strike", I)
    private val sacrificeForToken = Regex("target creature's controller sacrifices it, then that player creates a (\\d+)/\\d+", I)
    private val exileFromGraveyard = Regex("exile target (?:creature )?card from a graveyard|target player exiles a card from their graveyard", I)
    private val landBack = Regex("return a land card from your graveyard to the battlefield", I)
    private val landRamp = Regex("search your library for a basic land card, put it onto the battlefield", I)
    private val powerCondition = Regex("if [^,]+ has power (\\d+) or greater, create (\\w+) treasure", I)
    private val whileCondition = Regex("while you control a creature with power (\\d+) or greater", I)
    // Items 9–11 of 1.55: cards drawn each turn, Clues, +1/+1 counters, opponents' draw steps.
    private val reminder = Regex("\\s*\\([^)]*\\)")
    private val secondDraw = Regex("^whenever (you|a player|an opponent) draws? (?:your|their) second card (?:each turn|during their turn), (.*)$", I)
    private val oppSecondDrawDraws = Regex("^whenever an opponent [^.]*draws their second card each turn[^.]*, you draw a card", I)
    private val oppDrawDrain = Regex("^whenever an opponent draws a card, that player loses (\\d+) life", I)
    private val oppDrawTreasure = Regex("^whenever an opponent draws a card, if it isn't that player's turn, create a tapped treasure", I)
    private val eachDrawStep = Regex("^at the beginning of each player's draw step, (?:if [^,]+, )?that player draws an additional card", I)
    private val eachUpkeepDraw = Regex("^at the beginning of each player's upkeep, that player draws a card for each \\+1/\\+1 counter", I)
    private val secondSpell = Regex("^whenever (?:a player|you) casts? (?:their|your) second spell (?:during their turn|each turn), (.*)$", I)
    private val castMakes = Regex("^whenever you cast an? (?:([a-z]+) )?spell, (.*)$", I)
    private val creatureEnterDoes = Regex("^whenever (another )?(nontoken )?creature you control enters, (.*)$", I)
    private val anyHit = Regex("^whenever one or more (?:([a-z, /]+?) )?(?:creatures )?you control deal combat damage to a player, (.*)$", I)
    private val clueSac = Regex("^whenever you sacrifice a clue, (.*)$", I)
    private val entersWith = Regex("enters with (a|an|one|two|three|four|five) \\+1/\\+1 counters? on it", I)
    private val secondMain = Regex("^at the beginning of your second main phase, (if you attacked this turn, )?(.*)$", I)
    private val eachUpkeepLine = Regex("^at the beginning of each upkeep, (.*)$", I)

    enum class Filter { ANY, CREATURE, NONCREATURE }
    enum class ForEach { COLOURS, HAND, ARTIFACTS_ENCHANTMENTS, ENCHANTMENTS, ONE }

    /** Damage when a spell is cast: [amount] or the spell's mana value ([MANA_VALUE]), to each opponent or one. */
    data class CastTrigger(val firstOnly: Boolean, val filter: Filter, val each: Boolean, val amount: Int)

    /**
     * "{T}, Sacrifice up to [count] permanents: if there were [types] or more card types among them, each opponent loses
     * [drain] life … and you draw [draw] cards" (Baba Lysaga, Night Witch). Since 1.52.
     */
    data class SacrificeAbility(val count: Int, val types: Int, val drain: Int, val draw: Int)

    /** Damage when a creature or land of the deck enters: [amount], or the creature's power ([POWER], since 1.55). */
    data class EnterTrigger(val land: Boolean, val another: Boolean, val each: Boolean, val amount: Int)

    /**
     * A token's card types and subtypes ("Shrine enchantment creature"), so it counts as a Shrine. Since 1.54. Since 1.55
     * also whether it flies, and what it gets +1/+1 for ("for each artifact you control": Urza's Construct).
     */
    data class TokenKind(val power: Int, val types: Set<String>, val subtypes: Set<String>, val flying: Boolean = false, val per: String? = null)

    enum class Effect { DRAIN, TOKENS, DRAW, COUNTERS, PUMP }

    /**
     * An effect that counts permanents of a kind ([per]: "shrine", "gate", "creature"…) times [factor]: once when the
     * card comes in, or each turn ([turn]). Since 1.54.
     */
    data class Scaled(val what: Effect, val turn: Boolean, val per: String, val factor: Int = 1, val each: Boolean = true, val token: TokenKind? = null)

    /**
     * "Whenever (this or) another [kind] you control enters": what it does (since 1.54). [self]: the card itself counts;
     * [nontoken]: tokens don't. Since 1.55 also Treasures, and damage for the number of a kind ([perOne], [perEach]:
     * Dragon Tempest, Scourge of Valkas).
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
        val treasures: Int = 0,
        val perOne: String? = null,
        val perEach: String? = null,
        val made: Map<String, Int> = emptyMap(),
        val selfCounters: Int = 0,
        val oncePerTurn: Boolean = false,
    )

    /** "Other Zombies you control get +1/+1": [kind] null for all creatures, "a|b" for either. Since 1.55. */
    data class Lord(val kind: String?, val amount: Int, val other: Boolean)

    /** "{1}{R}: Dragons you control get +1/+0 until end of turn" ([kind]), or the creature itself ([kind] null). Since 1.55. */
    data class ActivatedPump(val cost: ManaOddsCalc.Cost, val kind: String?, val amount: Int)

    /**
     * What a spell, a trigger or an ability does with the graveyard, copies and cheating, and for abilities (tap,
     * loyalty, attack triggers…, [full]) also cards, damage and tokens. Since 1.55.
     */
    class Ability {
        var cost = ManaOddsCalc.Cost(0, emptyList())
        var draw = 0
        var drainEach = 0
        var drainOne = 0
        var tokens = 0
        var token: TokenKind? = null
        var artifacts = 0
        var treasures = 0
        var mill = 0
        var surveil = 0
        var lootDraw = 0
        var lootDiscard = 0
        /** "Draw that many cards, then discard that many cards" (Varina): one per attacker of [perKind]. */
        var thatMany = false
        /** Vohar: each opponent loses this much if an instant or sorcery was discarded. */
        var lootDrain = 0
        /** Jace's +1: cards put from the hand on the bottom of the library. */
        var toBottom = 0
        var reanimate = 0
        var reanimateKind: String? = null
        var reanimateSacrifice = false
        var reanimateEverything = false
        var toHand = 0
        var toHandKind: String? = null
        var flicker = 0
        var copies = 0
        var copyArtifactToken = false
        var copyExiled = false
        var exileGraveyard = 0
        var polymorph = 0
        var doubleStrikeFor: String? = null
        var sacrificeForToken = 0
        var landBack = false
        var landRamp = 0
        var perKind: String? = null
        var drawPerEquipped = false
        var selfPump = 0
        var teamPump = 0
        var needsPower = 0
        var powerAtLeast = 0
        var discardCost = 0
        // Items 9–11: Clues, Mutagens and other artifact tokens by kind; +1/+1 counters; group draw; extra combats.
        val made = HashMap<String, Int>()
        var selfCounters = 0
        var countersAll = 0
        var countersTarget = 0
        var countersHit = 0
        var hitKinds: String? = null
        var doubleCounters = false
        var drawPerCounters = false
        var copySelf = false
        var landFromHand = false
        var oncePerTurn = false
        var attackingTokens = false
        var eachPlayerDraws = 0
        var oppDraws = 0
        var extraCombat = false
        var copyFromGraveyard = false
        /** "Create X tokens, where X is the number of Soldiers you control" (Myrel). */
        var tokensPer: String? = null

        val graveyard get() = mill + surveil + lootDiscard + reanimate + toHand + exileGraveyard + flicker > 0 || copyExiled || landBack || thatMany
        val copying get() = copies + polymorph > 0 || copyArtifactToken || copyExiled || copyFromGraveyard
        /** Set once the card is read ([freeze]), as [any] is asked a lot during the games. */
        private var frozen: Boolean? = null
        fun freeze() { frozen = null; frozen = any }
        val any: Boolean get() = frozen ?: anything()
        private fun anything() = draw + drainEach + drainOne + tokens + artifacts + treasures + selfPump + teamPump + landRamp + sacrificeForToken > 0 ||
                graveyard || copying || doubleStrikeFor != null || drawPerEquipped || toBottom > 0 || made.isNotEmpty() ||
                selfCounters + countersAll + countersTarget + countersHit + eachPlayerDraws + oppDraws > 0 || doubleCounters || tokensPer != null ||
                drawPerCounters || copySelf || landFromHand || extraCombat || copyFromGraveyard
    }

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
        /** Food, Clue, Treasure… tokens it makes once, each turn, or for each land entering; the Treasures among them (1.55). */
        var artifactsOnce = 0
        var artifactsTurn = 0
        var artifactsLandfall = 0
        var treasuresOnce = 0
        var treasuresTurn = 0
        var treasuresLandfall = 0
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

        // Since 1.55.
        var legendary = false
        var planeswalker = false
        var flying = false
        var myriad = false
        var loyalty = 0
        val loyalties = ArrayList<Pair<Int, Ability>>()
        /** What it does when it enters or is cast besides the 1.52–1.54 effects; each turn; when it (or the creature it's
         *  equipped to) attacks; when you attack; when a creature of [kindAttack]'s kind attacks; when it deals combat damage. */
        val onEnter = Ability()
        val eachTurn = Ability()
        val onAttack = Ability()
        var onAttackEquipped = false
        val onYouAttack = Ability()
        var kindAttack: String? = null
        val onKindAttack = Ability()
        val onHit = Ability()
        val taps = ArrayList<Ability>()
        /** Abilities without {T} used with the mana left (Varina's Zombies). */
        val activated = ArrayList<Ability>()
        val lords = ArrayList<Lord>()
        val pumps = ArrayList<ActivatedPump>()
        var attackOverrun = 0
        var overrunPerCreature = false
        var hasteFor: String? = null
        var myriadFor: String? = null
        var background = 0
        var reducers: List<Pair<String, Int>> = emptyList()
        var affinityFor: String? = null
        var manaDrawAt = 0
        var treasureDouble = false
        var treasureExtra = false
        var devotion: Char? = null
        var landfallTokens = 0
        var landfallToken: TokenKind? = null
        var landfallCounters: String? = null
        var tokenDrain = 0
        var tokenBatchDrain = 0
        var dieDrain = 0
        var attachOnEnter = false
        var attachEntering = false
        var equipZero = false
        var equipLess = 0
        var firstEquipFree = false
        var sneakCost: ManaOddsCalc.Cost? = null
        var xTokens = 0
        var xToken: TokenKind? = null
        var xCap = 0
        var leaveToken: TokenKind? = null
        var leaveDraw = false
        var tokensNeed: String? = null
        var castFromGraveyard: String? = null
        var selfFromGraveyard: String? = null
        var flashbackCost: ManaOddsCalc.Cost? = null
        var crucible = false
        var monument = false
        var discardToken = 0
        var entersAsCopy = false
        var copyReanimated = false
        var demonstrate = false
        /** Artifact tokens by kind ("clue", "treasure", "mutagen"…) it makes once, each turn, for each land entering. */
        val madeOnce = HashMap<String, Int>()
        val madeTurn = HashMap<String, Int>()
        val madeLandfall = HashMap<String, Int>()
        // Items 9–11 of 1.55.
        val onSecondDraw = Ability()
        val onOppSecondDraw = Ability()
        var oppSecondDrawTheirTurn = false
        var oppDrawDrain = 0
        var oppDrawTreasure = false
        var eachDrawStep = 0
        var eachUpkeepDraws = false
        val eachUpkeep = Ability()
        val onSecondSpell = Ability()
        val onCast = ArrayList<Pair<String?, Ability>>()
        val onCreatureEnter = Ability()
        var creatureEnterAnother = false
        var creatureEnterNontoken = false
        val onAnyHit = Ability()
        val onClueSac = Ability()
        val postCombat = Ability()
        var postCombatNeedsAttack = false
        var animateClues = 0
        var mechanized = false
        var artifactWin = 0
        var manufactor = false
        var extraFood = false
        var extraMutagen = false
        var investigateExtra = false
        var manaPer: Pair<String, Char>? = null
        var improvise = false
        var foodMana = false
        var tapTwoDraw = false
        var drawnAnthem = false
        var combatCountersDrawn = false
        var drawDouble = false
        var endDrawIfToken = false
        var counterPlus = 0
        var counterTimes = false
        var counterDamage = false
        var counterDraw = false
        var drawCounter = false
        var doubleCountered = false
        var attackersDouble = false
        var entersWithCounters = 0
        var auraCounter = 0
        var counteredHitDraw = false
        var truce = false
        /** "You become the monarch": a card each end step (nobody takes it back here). */
        var monarch = false
        /** Cyberdrive Awakener: noncreature artifacts become 4/4s this turn. Rise and Shine: one becomes a creature with four counters. */
        var animateArtifacts = 0
        var riseAndShine = 0
        /**
         * Cascade (once for each), "the first spell you cast each turn has cascade" (Maelstrom Nexus), discover when it
         * enters, discover for a creature spell with this power or more (Monstrous Vortex), the mana value Improvisation
         * Capstone exiles, back to the hand when a cascade spell is cast (Aurora Phoenix), and counters on each creature
         * that entered this turn (Sweet-Gum Recluse). Paradigm: once it has resolved, a free copy each turn.
         */
        var cascade = 0
        var cascadeFirst = false
        var discover = 0
        var discoverPower = 0
        var capstone = 0
        var cascadeReturn = false
        var newCounters = 0
        var paradigm = false

        val land get() = mana.kind == ManaOddsCalc.Kind.LAND
        val gearBonus get() = bonus > 0 || forEach != null || base != null || grantsDouble || grantsHaste
        val pumpSpell get() = !permanent && (pump > 0 || pumpX || pumpDouble)
        val dealsDamage get() = drainOnce + drainTurn + oneOnce + oneTurn > 0 || sacrifice != null || xEach || artifactDrain || devotion != null || dieDrain + tokenDrain + tokenBatchDrain > 0

        /** It does one of the things the win turn reads since 1.54, so it's worth casting. */
        val newUse by lazy { scaled.isNotEmpty() || kindTriggers.any { it.drainEach + it.drainOne + it.tokens + it.draw + it.counters + it.treasures > 0 || it.land || it.perOne != null || it.perEach != null } ||
                powerPer != null || fetchKind != null || gateRamp + gateToHand > 0 || landToHand || gatePing || animates != null ||
                untapsLands > 0 || abilityHaste || untapsOwn || untapsOther || copiesAbilities || artifactDrain || liquimetal ||
                landLeavesToken + landSacToken > 0 || sacCounters || copiesFirst
        }

        /** It does one of the things the win turn reads since 1.55. */
        val use55 by lazy { onEnter.any || eachTurn.any || onAttack.any || onYouAttack.any || onKindAttack.any || onHit.any || taps.any { it.any } ||
                activated.any { it.any } || (planeswalker && loyalties.isNotEmpty()) || lords.isNotEmpty() || pumps.isNotEmpty() ||
                attackOverrun > 0 || overrunPerCreature || hasteFor != null || myriadFor != null || background > 0 || reducers.isNotEmpty() ||
                treasuresOnce + treasuresTurn + treasuresLandfall > 0 || treasureDouble || treasureExtra || landfallTokens > 0 ||
                landfallCounters != null || attachEntering || equipZero || equipLess > 0 || firstEquipFree || sneakCost != null ||
                xTokens > 0 || leaveToken != null || leaveDraw || castFromGraveyard != null || crucible || monument || discardToken > 0 ||
                entersAsCopy || copyReanimated || onSecondDraw.any || onOppSecondDraw.any || oppDrawDrain > 0 || oppDrawTreasure ||
                eachDrawStep > 0 || eachUpkeepDraws || eachUpkeep.any || onSecondSpell.any || onCast.isNotEmpty() || onCreatureEnter.any ||
                onAnyHit.any || onClueSac.any || postCombat.any || animateClues > 0 || mechanized || artifactWin > 0 || manufactor ||
                extraFood || extraMutagen || investigateExtra || manaPer != null || improvise || foodMana || tapTwoDraw || drawnAnthem ||
                combatCountersDrawn || drawDouble || endDrawIfToken || counterPlus > 0 || counterTimes || counterDamage || counterDraw ||
                drawCounter || doubleCountered || attackersDouble || entersWithCounters > 0 || counteredHitDraw || madeOnce.isNotEmpty() ||
                madeTurn.isNotEmpty() || madeLandfall.isNotEmpty() || truce || animateArtifacts > 0 || riseAndShine > 0 || monarch ||
                cascade > 0 || cascadeFirst || discover > 0 || discoverPower > 0 || capstone > 0 || newCounters > 0 || paradigm
        }

        /** Every ability it has (to freeze them once it's read). */
        fun abilities(): List<Ability> = listOf(onEnter, eachTurn, onAttack, onYouAttack, onKindAttack, onHit, onSecondDraw, onOppSecondDraw,
            eachUpkeep, onSecondSpell, onCreatureEnter, onAnyHit, onClueSac, postCombat) + taps + activated + loyalties.map { it.second } + onCast.map { it.second }

        /** Fills or uses the graveyard (for the "How?"). */
        val graveyardUse
            get() = onEnter.graveyard || eachTurn.graveyard || onAttack.graveyard || onYouAttack.graveyard || taps.any { it.graveyard } ||
                activated.any { it.graveyard } || loyalties.any { it.second.graveyard } || leaveToken != null || leaveDraw ||
                castFromGraveyard != null || selfFromGraveyard != null || flashbackCost != null || crucible || monument || discardToken > 0
        val copyUse
            get() = onEnter.copying || taps.any { it.copying } || loyalties.any { it.second.copying } || entersAsCopy || copyReanimated || sneakCost != null
    }

    internal fun classify(c: StatsCard, own: Set<Char>, lands: List<StatsCard>, comboKeys: Set<String>): WinCard {
        val mana = ManaOddsCalc.classify(c, own, lands)
        val w = WinCard(c.name, mana, ManaOddsCalc.cost(c.manaCost), c.cmc.toInt())
        w.combo = w.key in comboKeys
        val type = (c.typeLine?.takeIf { it.isNotEmpty() } ?: c.types).substringBefore(" // ")
        w.types = cardTypes.filter { it in type.substringBefore(" — ").split(' ') }.toSet()
        w.subtypes = type.substringAfter(" — ", "").split(' ').filter { it.isNotBlank() }.toSet()
        w.legendary = "Legendary" in type
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
        w.planeswalker = "Planeswalker" in type
        w.enchantment = "Enchantment" in type
        w.artifactOrEnchantment = w.permanent && !w.creature && ("Artifact" in type || "Enchantment" in type)
        w.equipment = "Equipment" in type
        w.power = c.powerToughness?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
        if (w.planeswalker) w.loyalty = c.loyalty?.toIntOrNull() ?: (w.manaValue - 1).coerceAtLeast(1)
        val text = c.oracleText.orEmpty().split(CardInfo.FACE_BREAK).first()
        w.aura = "Aura" in type && "enchant creature" in text.lowercase()
        w.text = text.lowercase()
        // The modes under a trigger ("At the beginning of your first main phase, choose one or more — • …") are read with
        // the trigger; a Class's later levels aren't counted (they cost more). Since 1.55.
        var prefix = ""
        for (raw in text.split('\n')) {
            val line = raw.trim()
            if ("Class" in w.subtypes && Regex("^(?:\\{[^}]+\\})+: level \\d", I).containsMatchIn(line)) break
            if (line.startsWith("•") && prefix.isNotEmpty()) {
                readLine(w, prefix + line.removePrefix("•").trim().replace(Regex("^[^—:{]{1,40} — "), ""))
                continue
            }
            prefix = Regex("^((?:when|whenever|at the beginning of)[^,]*, )choose (?:one|two|one or more) —$", I).find(line)?.groupValues?.get(1) ?: ""
            readLine(w, line)
        }
        if (w.equipment || w.aura) readGear(w, text)
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
        read55(w, text)
        w.abilities().forEach { it.freeze() }
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
        if ("{X}" in cost) xTokens.find(t)?.let { m ->
            w.xTokens = m.groupValues[1].toIntOrNull() ?: 0
            w.xToken = tokenKind(t.substring(m.range.first))
            // "If X is 5 or more, destroy all other creatures" (Martial Coup): kept under 5.
            if (Regex("if x is (\\d+) or more, destroy all other creatures").containsMatchIn(t)) w.xCap = 4
            if (w.xTokens == 0) w.xTokens = 1
        }
    }

    /** What the win turn reads since 1.55 from the whole text. */
    private fun read55(w: WinCard, text: String) {
        val t = w.text
        w.flying = Regex("^flying\\b", setOf(I, RegexOption.MULTILINE)).containsMatchIn(text)
        w.myriad = Regex("^myriad\\b", setOf(I, RegexOption.MULTILINE)).containsMatchIn(text)
        if ("nonlegendary artifact creatures you control have myriad" in t) w.myriadFor = "artifact"
        backgroundPump.find(t)?.let { w.background = it.groupValues[1].toInt() }
        if ("nontoken creatures you control have riot" in t) w.hasteFor = "nontoken"
        if ("whenever a creature you control with flying enters, it gains haste" in t) w.hasteFor = "flying"
        if ("if you didn't cast it from your hand, it gains haste" in t) w.hasteFor = "cheated"
        affinity.find(t)?.let { m -> w.affinityFor = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim().removeSuffix("s") }
        if (Regex("^improvise\\b", RegexOption.MULTILINE).containsMatchIn(t)) w.affinityFor = "artifact"
        manaDraw.find(t)?.let { w.manaDrawAt = it.groupValues[1].toInt() }
        w.treasureDouble = "treasures you control have \"{t}, sacrifice this artifact: add two mana" in t
        w.treasureExtra = "if you would create one or more treasure tokens, instead create those tokens plus an additional treasure token" in t
        devotionText.find(t)?.let { m -> w.devotion = mapOf("white" to 'W', "blue" to 'U', "black" to 'B', "red" to 'R', "green" to 'G')[m.groupValues[1]] }
        if (w.devotion != null) { w.drainOnce = 0 }
        w.attachOnEnter = attachOnEnter.containsMatchIn(t)
        w.attachEntering = attachEntering.containsMatchIn(t)
        w.equipZero = "equipment you control have equip {0} as long as you control three or more artifacts" in t
        w.equipLess = equipLess.find(t)?.groupValues?.get(1)?.toInt() ?: 0
        w.firstEquipFree = "rather than pay the equip cost of the first equip ability you activate during each of your turns" in t
        w.crucible = "you may play lands from your graveyard" in t
        w.monument = "whenever you discard a card, choose one that hasn't been chosen this turn" in t
        castFromGraveyard.find(t)?.let { w.castFromGraveyard = it.groupValues[1].trim().ifEmpty { "creature" } }
        selfFromGraveyard.find(t)?.let { w.selfFromGraveyard = it.groupValues[1] }
        flashback.find(text)?.let { m ->
            w.flashbackCost = if ("jump-start" in m.value.lowercase()) w.cost else ManaOddsCalc.cost(m.groupValues[1])
        }
        w.entersAsCopy = enterCopy.containsMatchIn(t)
        w.copyReanimated = copyReanimated.containsMatchIn(t)
        w.demonstrate = Regex("^demonstrate\\b", RegexOption.MULTILINE).containsMatchIn(t)
        if (danse.containsMatchIn(t)) w.onEnter.flicker = maxOf(w.onEnter.flicker, 1)
        if (!w.permanent) {
            // A spell: what it does goes with what it does when cast.
            readAbility(w.onEnter, t, w, full = false)
        }
    }

    /** A token's kind from its words: "1/1 colorless Shrine enchantment creature token". */
    private fun tokenKind(line: String): TokenKind? {
        val m = tokenWords.find(line) ?: return null
        val words = m.groupValues[3].trim().split(' ').filter { it.isNotBlank() }
        val types = setOf("Creature") + words.filter { it.lowercase() in setOf("artifact", "enchantment") }.map { it.lowercase().replaceFirstChar(Char::uppercase) }
        val subtypes = words.filter { it.lowercase() !in colourWords && it.lowercase() !in setOf("artifact", "enchantment") }
            .map { w -> w.replaceFirstChar(Char::uppercase) }.toSet()
        val after = line.substring(m.range.last + 1).substringBefore('.').lowercase()
        val per = Regex("gets \\+1/\\+1 for each ([a-z]+?)s? you control").find(line.substring(m.range.first).substringBefore("\n"))?.groupValues?.get(1)
        return TokenKind(m.groupValues[1].toIntOrNull() ?: 0, types, subtypes, flying = "with flying" in after || "flying" in after.substringBefore(" and ").take(20), per = per)
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
            pumpPerCreature.containsMatchIn(low) -> Scaled(Effect.PUMP, turn, per, factor)
            "each opponent loses x life" in low || "x damage to each opponent" in low || "each opponent loses life equal to the number" in low -> Scaled(Effect.DRAIN, turn, per, factor)
            Regex("deals? (?:x damage to (?:target player|any target|target opponent)|damage to (?:any target|target player|target opponent)[^.]*equal to the number)").containsMatchIn(low) ->
                Scaled(Effect.DRAIN, turn, per, factor, each = false)
            "token" in low && "create" in low -> Scaled(Effect.TOKENS, turn, per, factor, token = tokenKind(line))
            Regex("draws? (?:a card for each|x cards|cards equal to the number)").containsMatchIn(low) -> Scaled(Effect.DRAW, turn, per, factor)
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
        val per = perKindX.find(rest)?.groupValues?.get(1)?.lowercase()
        val eachAmount = e?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        val oneAmount = o?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        w.kindTriggers += KindTrigger(
            kind = kind,
            self = self,
            nontoken = m.groupValues[2].isNotEmpty(),
            drainEach = number(eachAmount),
            drainOne = if (e == null) number(oneAmount) else 0,
            token = c?.let { tokenKind(rest) },
            tokens = c?.let { number(it.groupValues[1]) } ?: 0,
            draw = d?.let { number(it.groupValues[1]) } ?: 0,
            counters = if (Regex("put a \\+1/\\+1 counter on each creature you control", RegexOption.IGNORE_CASE).containsMatchIn(rest)) 1 else 0,
            land = Regex("search your library for a basic land card, put it onto the battlefield", RegexOption.IGNORE_CASE).containsMatchIn(rest),
            treasures = artifactToken.find(rest)?.takeIf { it.groupValues[2].equals("treasure", true) }?.let { number(it.groupValues[1]) } ?: 0,
            perEach = per?.takeIf { eachAmount.equals("x", true) },
            perOne = per?.takeIf { e == null && oneAmount.equals("x", true) },
            made = artifactToken.findAll(rest).filter { !it.groupValues[2].equals("treasure", true) }.associate { it.groupValues[2].lowercase() to number(it.groupValues[1]) },
            selfCounters = if (Regex("put (?:a|two) \\+1/\\+1 counters? on (?:this creature|it\\b)", RegexOption.IGNORE_CASE).containsMatchIn(rest) &&
                !Regex("on each creature", RegexOption.IGNORE_CASE).containsMatchIn(rest)) 1 else 0,
            oncePerTurn = "only once each turn" in rest.lowercase(),
        )
        return true
    }

    private fun readArtifacts(w: WinCard, line: String) {
        val m = artifactToken.find(line) ?: return
        val low = line.lowercase()
        val n = number(m.groupValues[1])
        val kind = m.groupValues[2].lowercase()
        val t = if (kind == "treasure") n else 0
        fun add(map: HashMap<String, Int>) { map[kind] = (map[kind] ?: 0) + n }
        when {
            low.startsWith("landfall") || (low.startsWith("whenever a land") && "enters" in low) -> { w.artifactsLandfall += n; w.treasuresLandfall += t; add(w.madeLandfall) }
            low.startsWith("whenever") && " attacks" in low -> Unit
            low.startsWith("at the beginning of your") && !low.startsWith("at the beginning of your second main") -> { w.artifactsTurn += n; w.treasuresTurn += t; add(w.madeTurn) }
            low.startsWith("when ") && "enters" in low && "at the beginning of your upkeep" in low -> {
                w.artifactsOnce += n; w.artifactsTurn += n; w.treasuresOnce += t; w.treasuresTurn += t; add(w.madeOnce); add(w.madeTurn)
            }
            low.startsWith("when ") && "enters" in low || !w.permanent -> { w.artifactsOnce += n; w.treasuresOnce += t; add(w.madeOnce) }
        }
    }

    /**
     * What an ability's or a trigger's text does (since 1.55): with the graveyard, copies and cheating, and when [full]
     * also cards, damage, tokens, Treasures and pumps (for abilities and attack triggers, whose 1.52–1.54 effects aren't read
     * elsewhere).
     */
    private fun readAbility(a: Ability, text: String, w: WinCard, full: Boolean) {
        val low = text.lowercase()
        lootText.find(low)?.let { a.lootDraw += number(it.groupValues[1]); a.lootDiscard += number(it.groupValues[2]) }
        discardThenDraw.find(low)?.let { a.lootDiscard += number(it.groupValues[1]); a.lootDraw += number(it.groupValues[2]) }
        if (lootThatMany.containsMatchIn(low)) a.thatMany = true
        if (a.lootDiscard > 0 && Regex("if you discarded an instant or sorcery card this way, each opponent loses (\\d+)").find(low)?.let { a.lootDrain = it.groupValues[1].toInt() } != null) Unit
        millText.findAll(low).forEach { m -> if ("target player" !in low.substring(maxOf(0, m.range.first - 14), m.range.first + 1)) a.mill += number(m.groupValues[1]) }
        surveilText.find(low)?.let { a.surveil += it.groupValues[1].toInt() }
        if ("from your hand on the bottom of your library" in low) a.toBottom += 1
        if ("opponent's turn" !in low) {
            reanimateText.find(low)?.let { m ->
                a.reanimate += m.groupValues[1].let { if (it.isEmpty()) 1 else if (it == "x") OPPONENTS else number(it) }
                val kind = m.groupValues[2].trim()
                a.reanimateKind = kind.takeIf { it.isNotEmpty() && it !in setOf("creature", "permanent") }
            }
        }
        if (a.reanimate == 0) reanimateSome.find(low)?.let { a.reanimate += number(it.groupValues[1]) }
        reanimateChosen.find(low)?.let { a.reanimate = number(it.groupValues[1]); a.reanimateSacrifice = true }
        if (a.reanimate == 0 && reanimateEach.containsMatchIn(low)) a.reanimate = 1
        reanimateAll.find(low)?.let { m ->
            a.reanimate = ALL
            a.reanimateKind = when {
                m.groupValues[2].isNotEmpty() -> "chosen"
                else -> m.groupValues[1].ifEmpty { null }
            }
            a.reanimateEverything = m.groupValues[3].isNotEmpty()
        }
        toHandText.find(low)?.let { m ->
            a.toHand += m.groupValues[1].let { if (it.isEmpty()) 1 else number(it) }
            a.toHandKind = m.groupValues[2].trim().ifEmpty { null }
        }
        flickerText.find(low)?.let { a.flicker += number(it.groupValues[1]).coerceAtLeast(1) }
        copyText.find(low)?.let { m ->
            if ("from a graveyard" !in low && "an opponent controls" !in low.substring(m.range.first).substringBefore('.')) a.copies += number(m.groupValues[1]) * (if (w.demonstrate) 2 else 1)
        }
        if (copyArtifactToken.containsMatchIn(low)) a.copyArtifactToken = true
        if (copyExiled.containsMatchIn(low) && "artifact token" in low) a.copyExiled = true
        polymorph.find(low)?.let { m -> if ("onto the battlefield" in low) a.polymorph = if ("that many" in m.value) ALL else 1 }
        doubleStrikeFor.find(low)?.let { a.doubleStrikeFor = it.groupValues[1] }
        sacrificeForToken.find(low)?.let { a.sacrificeForToken = it.groupValues[1].toInt() }
        if (exileFromGraveyard.containsMatchIn(low) && !Regex("create a token that's a copy of it").containsMatchIn(low)) a.exileGraveyard = maxOf(a.exileGraveyard, 1)
        // The Scarab God: a creature card from a graveyard comes back as a token copy (not "put there this turn").
        if (Regex("exile target creature card from a graveyard\\. create a token that's a copy of it").containsMatchIn(low) && "this turn" !in low) a.copyFromGraveyard = true
        if (landBack.containsMatchIn(low)) a.landBack = true
        Regex("put (a|two|three|x) \\+1/\\+1 counters? on (this creature|${Regex.escape(w.name.lowercase())}|${Regex.escape(w.name.substringBefore(',').lowercase())})\\b").find(low)?.let { a.selfCounters += number(it.groupValues[1]).coerceAtLeast(1) }
        Regex("put (a|two|three) \\+1/\\+1 counters? on each (?:other )?creature you control").find(low)?.let { a.countersAll += number(it.groupValues[1]) }
        Regex("put (a|two|three) \\+1/\\+1 counters? on (?:another )?target (?:attacking )?creature").find(low)?.let { a.countersTarget += number(it.groupValues[1]) }
        if ("put a +1/+1 counter on each of those creatures" in low) { if (full) a.countersHit = 1 else a.countersAll += 1 }
        if ("double the number of +1/+1 counters on it" in low) a.doubleCounters = true
        if (Regex("draw x cards[^.]*where x is the number of counters on").containsMatchIn(low)) a.drawPerCounters = true
        if ("create a token that's a copy of this creature" in low) a.copySelf = true
        if ("put a land card from your hand onto the battlefield" in low) a.landFromHand = true
        if ("only once each turn" in low) a.oncePerTurn = true
        if ("there is an additional combat phase" in low) a.extraCombat = true
        artifactToken.findAll(low).forEach { m -> if (m.groupValues[2] != "treasure") a.made[m.groupValues[2]] = (a.made[m.groupValues[2]] ?: 0) + number(m.groupValues[1]) }
        if (!full) return
        val eachPlayer = Regex("each player (?:may )?draws? a card").containsMatchIn(low)
        if (eachPlayer) a.eachPlayerDraws = 1
        if (Regex("you and target opponent each draw a card|two target players each draw a card").containsMatchIn(low)) { a.draw += 1; a.oppDraws += 1 }
        if (a.lootDraw == 0 && !a.thatMany && !eachPlayer && a.oppDraws == 0 && !a.drawPerCounters) {
            drawCards.find(low)?.let { m -> if ("opponent" !in low.substring(0, m.range.first)) a.draw += number(m.groupValues[1]) }
        }
        if ("draw a card for each equipped attacking creature" in low) { a.drawPerEquipped = true; a.draw = 0 }
        if (a.lootDrain == 0) eachOpponent.find(low)?.let { a.drainEach += number(it.groupValues[1].ifEmpty { it.groupValues[2] }) }
        if (a.drainEach == 0) oneOpponent.find(low)?.let { a.drainOne += number(it.groupValues[1].ifEmpty { it.groupValues[2] }) }
        if (a.copies == 0 && !a.copyArtifactToken && !a.copyExiled && !a.copySelf && !a.copyFromGraveyard) create.find(low)?.let { m ->
            a.tokens += if ("for each opponent, create" in low) OPPONENTS else number(m.groupValues[1])
            a.token = tokenKind(text.substring(m.range.first))
            if (m.groupValues[1].equals("x", true)) perKindX.find(low)?.let { a.tokensPer = it.groupValues[1] }
            if ("tapped and attacking" in low) a.attackingTokens = true
        }
        artifactToken.findAll(low).forEach { m ->
            val n = number(m.groupValues[1])
            powerCondition.find(low)?.takeIf { m.groupValues[2] == "treasure" }?.let { a.powerAtLeast = it.groupValues[1].toInt() }
            // "A Treasure for each artifact that player controls" (Cavern-Hoard Dragon): opponents have none here.
            if ("for each artifact that player controls" in low) return@forEach
            if (m.groupValues[2] == "treasure") a.treasures += n else a.artifacts += n
        }
        if (landRamp.containsMatchIn(low)) a.landRamp += 1
        Regex("(?:it|this creature|${Regex.escape(w.name.lowercase())}|${Regex.escape(w.name.substringBefore(',').lowercase())}) gets \\+(\\d+)/\\+\\d+ until end of turn").find(low)?.let { a.selfPump += it.groupValues[1].toInt() }
        whileCondition.find(low)?.let { a.needsPower = it.groupValues[1].toInt() }
        Regex("(?:other )?creatures you control get \\+(\\d+)/\\+\\d+ until end of turn").find(low)?.let { a.teamPump += it.groupValues[1].toInt() }
    }

    /** The cost of an activated ability (before the colon), and whether it taps; null when it can't be read. Since 1.55. */
    private fun abilityCost(head: String, a: Ability): Boolean? {
        val low = head.lowercase()
        var rest = low.replace(Regex("\\{[^}]+\\}"), "").replace(",", " ").trim()
        if ("sacrifice two artifact tokens" in low) rest = rest.replace("sacrifice two artifact tokens", "")
        if ("exile an artifact or creature card from your graveyard" in low) rest = rest.replace("exile an artifact or creature card from your graveyard", "")
        Regex("exile (\\w+) cards? from your graveyard").find(low)?.let { a.exileGraveyard = number(it.groupValues[1]); rest = rest.replace(it.value, "") }
        if ("discard a card" in low) { a.discardCost = 1; rest = rest.replace("discard a card", "") }
        if (rest.replace(Regex("\\s+"), "").isNotEmpty()) return null
        a.cost = ManaOddsCalc.cost(head)
        return "{t}" in low
    }

    private fun readLine(w: WinCard, raw: String) {
        // Since 1.55: reminder text left out (a Treasure's "{T}, Sacrifice this token: Add one mana" isn't the card's own),
        // ability words too ("Bad Breath — When…", not "Landfall —"), and investigating read as making a Clue.
        var line = raw.replace(reminder, "").trim()
        if (!line.lowercase().startsWith("landfall")) line = line.replace(Regex("^[^—:{]{1,40} — (?=(?:when|whenever|at|if|as|each|creatures|other|this|you|tap|\\{))", I), "")
        if (Regex("^whenever you investigate for the first time each turn, investigate an additional time", I).containsMatchIn(line)) { w.investigateExtra = true; return }
        line = line.replace(Regex("\\binvestigate x times, where x is the number of opponents you have", I), "create three Clue tokens")
            .replace(Regex("\\binvestigate twice", I), "create two Clue tokens")
            .replace(Regex("\\binvestigate(?! that many| an additional| for the first)\\b", I), "create a Clue token")
        if (line.isEmpty()) return
        val low = line.lowercase()
        // A planeswalker's loyalty abilities (since 1.55).
        if (w.planeswalker) loyaltyLine.find(line)?.let { m ->
            if (m.groupValues[2].equals("x", true)) return
            val cost = m.groupValues[2].toInt() * if (m.groupValues[1] == "+" || m.groupValues[1].isEmpty() && m.groupValues[2] == "0") 1 else -1
            val a = Ability()
            readAbility(a, m.groupValues[3], w, full = true)
            if (cost <= -6 && a.doubleStrikeFor == null) return
            w.loyalties += cost to a
            return
        }
        readArtifacts(w, line)
        sacrificeAbility.find(line)?.let { m ->
            w.sacrifice = SacrificeAbility(number(m.groupValues[1]), number(m.groupValues[2]), m.groupValues[3].toInt(), number(m.groupValues[4]))
            return
        }
        if (keywordLine.matches(low)) {
            val words = low.split(", ")
            if ("haste" in words) w.haste = true
            if ("double strike" in words) w.double = true
            w.cascade += words.count { it == "cascade" }
            if ("paradigm" in words) w.paradigm = true
            return
        }
        if (readSpecial(w, line, low)) return
        val recurring = low.startsWith("at the beginning of your") || low.startsWith("at the beginning of combat on your")
        val tap = tapAbility.containsMatchIn(low) && "sacrifice" !in low.substringBefore(':')
        val once = !w.permanent || (low.startsWith("when ") && "enters" in low)
        val plain = line.replace(abilityWord, "")
        if (readKindTrigger(w, plain, plain.lowercase())) return
        // Since 1.55: abilities with {T} or a mana cost are used when it pays, not every turn for free.
        if (w.permanent && Regex("^(?:\\{[^}]+\\}(?:, )?)+[^:]*:").containsMatchIn(low)) {
            if (readActivated(w, line, low)) return
        }
        countOf.find(low)?.let { m ->
            // "Create a 0/0 Construct token with "This token gets +1/+1 for each artifact you control"": one token that grows.
            if ("\"this token gets +1/+1 for each" in low || "\"this creature gets +1/+1 for each" in low) return@let
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
                !land && powerDamage.containsMatchIn(line) -> w.enterTriggers += EnterTrigger(false, "another" in head, "each opponent" in low, POWER)
            }
            // Landfall tokens (Omnath, Locus of Rage) and counters (Avenger of Zendikar's Plants), since 1.55.
            if (land) {
                create.find(line)?.let { m ->
                    w.landfallTokens += number(m.groupValues[1])
                    w.landfallToken = tokenKind(line.substring(m.range.first))
                }
                landfallCounters.find(line)?.let { w.landfallCounters = it.groupValues[1] }
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
            if (w.token == null) w.token = tokenKind(line.substring(m.range.first))
            // A 0/0 that grows ("for each artifact you control") counts as power 1 so it's made (since 1.55).
            val per = tokenKind(line.substring(m.range.first))?.per != null
            val p = number(m.groupValues[1]) * ((m.groupValues[2].toIntOrNull() ?: 0).takeIf { it > 0 || !per } ?: 1)
            if (recurring || tap) w.tokensTurn += p else if (once) w.tokensOnce += p
            // Quintorius, Loremaster: only with such a card in the graveyard to exile.
            endStepExile.find(line)?.let { w.tokensNeed = it.groupValues[1] }
        }
        drawCards.find(line)?.let { m ->
            if ("opponent" in low.substring(0, m.range.first) || "each player" in low || lootText.containsMatchIn(low) || discardThenDraw.containsMatchIn(low)) return@let
            val n = number(m.groupValues[1])
            when {
                recurring || tap -> w.drawTurn += n
                low.startsWith("whenever") -> w.drawTurn += minOf(n, 1)
                once -> w.drawOnce += n
            }
        }
        val lord = readLords(w, low)
        if (!lord) lordText.find(low)?.let { m ->
            val n = m.groupValues[5].toInt()
            if ("until end of turn" in low) {
                if ("creatures" == m.groupValues[3]) w.overrun += n
            } else if (w.permanent && !low.startsWith("when")) w.anthem += n
        }
        if (w.permanent && (recurring || once)) readAbility(if (once) w.onEnter else w.eachTurn, line, w, full = false)
        if (!w.permanent) {
            pumpText.find(line)?.let { m -> if (m.groupValues[1].lowercase() == "x") w.pumpX = true else if (w.pump == 0) w.pump = m.groupValues[1].toInt() }
            if (pumpDouble.containsMatchIn(line)) w.pumpDouble = true
        } else {
            if (attackDouble.containsMatchIn(line)) w.attackDouble = true
            targetedBonus.find(line)?.let { w.targetedBonus += it.groupValues[1].toInt() }
            combatDraw.find(line)?.let { w.combatDraw = if ("many" in it.groupValues[1].lowercase()) MANA_VALUE else 1 }
        }
    }

    /** Lords: "(Other) Zombies/Zombie creatures/artifact creatures you control get +1/+1"; true when the line is one. Since 1.55. */
    private fun readLords(w: WinCard, low: String): Boolean {
        if (!w.permanent || low.startsWith("when") || low.startsWith("at the beginning") || "until end of turn" in low) return false
        twoLords.find(low)?.let { m ->
            val n = m.groupValues[3].toInt()
            w.lords += Lord(m.groupValues[1], n, false)
            w.lords += Lord(m.groupValues[2], n, true)
            w.anthem += n
            return true
        }
        val m = lordText.find(low) ?: return false
        val n = m.groupValues[5].toInt()
        val words = m.groupValues[2].trim().split(' ').filter { it.isNotBlank() && it !in colourWords && it != "nontoken" }
        val noun = m.groupValues[3]
        val kind = when {
            m.groupValues[4].isNotEmpty() -> m.groupValues[4] + if ("and/or tokens" in m.value) "|token" else ""
            noun != "creatures" -> noun.removeSuffix("s")
            else -> words.lastOrNull()
        }
        w.lords += Lord(kind, n, m.groupValues[1].isNotEmpty())
        w.anthem += n
        if (kind != null && Regex("and have [^.]*haste").containsMatchIn(low)) w.hasteFor = kind
        return true
    }

    /**
     * Lines read whole since 1.55: attack triggers, damage-on-hit triggers, team pumps, lords' haste, drains on tokens
     * and deaths, Sneak Attack, cost reducers, abilities paid with mana. True when the line was read.
     */
    private fun readSpecial(w: WinCard, line: String, low: String): Boolean {
        val short = Regex.escape(w.name.substringBefore(',').lowercase())
        val full = Regex.escape(w.name.lowercase())
        fun after(m: MatchResult, group: Int) = line.substring(m.groups[group]!!.range.first)
        // Cascade and discover (since 1.55).
        if ("the first spell you cast each turn has cascade" in low) { w.cascadeFirst = true; return true }
        discoverPower.find(low)?.let { w.discoverPower = it.groupValues[1].toInt(); return true }
        discoverEnter.find(low)?.let { w.discover = it.groupValues[1].toInt(); return true }
        capstone.find(low)?.let { w.capstone = it.groupValues[1].toInt(); return true }
        if ("whenever you cast a spell with cascade, return this card from your graveyard to your hand" in low) { w.cascadeReturn = true; return true }
        newCounters.find(low)?.let { w.newCounters = number(it.groupValues[1]); return true }
        // Items 9–11 (since 1.55): the second card drawn each turn, opponents drawing, group draw, casting a kind of
        // spell, creatures entering, combat damage, Clues, counters, second main phase.
        secondDraw.find(low)?.let { m ->
            when (m.groupValues[1]) {
                "you" -> readAbility(w.onSecondDraw, after(m, 2), w, full = true)
                "a player" -> { readAbility(w.onSecondDraw, after(m, 2), w, full = true); readAbility(w.onOppSecondDraw, after(m, 2), w, full = true); w.oppSecondDrawTheirTurn = true }
                else -> readAbility(w.onOppSecondDraw, after(m, 2), w, full = true)
            }
            return true
        }
        if (oppSecondDrawDraws.containsMatchIn(low)) { w.onOppSecondDraw.draw += 1; return true }
        oppDrawDrain.find(low)?.let { w.oppDrawDrain = it.groupValues[1].toInt(); return true }
        if (oppDrawTreasure.containsMatchIn(low)) { w.oppDrawTreasure = true; return true }
        if (eachDrawStep.containsMatchIn(low)) { w.eachDrawStep += 1; return true }
        if (eachUpkeepDraw.containsMatchIn(low)) { w.eachUpkeepDraws = true; return true }
        secondSpell.find(low)?.let { m -> readAbility(w.onSecondSpell, after(m, 1), w, full = true); return true }
        castMakes.find(low)?.let { m ->
            val rest = m.groupValues[2]
            if (("create" in rest || "draw" in rest) && eachOpponent.find(rest) == null && oneOpponent.find(rest) == null && "damage" !in rest) {
                val a = Ability()
                readAbility(a, after(m, 2), w, full = true)
                if (a.any) { w.onCast += m.groupValues[1].ifEmpty { null } to a; return true }
            }
        }
        creatureEnterDoes.find(low)?.let { m ->
            val rest = m.groupValues[3]
            if (eachOpponent.find(rest) == null && oneOpponent.find(rest) == null && !powerDamage.containsMatchIn(rest) && ("create" in rest || "counter" in rest)) {
                readAbility(w.onCreatureEnter, after(m, 3), w, full = true)
                w.creatureEnterAnother = m.groupValues[1].isNotEmpty()
                w.creatureEnterNontoken = m.groupValues[2].isNotEmpty()
                return true
            }
        }
        if (Regex("^whenever a creature you control with a counter on it deals combat damage to a player, draw a card").containsMatchIn(low)) { w.counteredHitDraw = true; return true }
        anyHit.find(low)?.let { m ->
            readAbility(w.onAnyHit, after(m, 2), w, full = true)
            w.onAnyHit.hitKinds = m.groupValues[1].takeIf { it.isNotBlank() }?.split(Regex(",? (?:and/or |and |or )?|, "))
                ?.map { it.trim().removeSuffix("s") }?.filter { it.isNotBlank() && it != "creature" }?.joinToString("|")?.ifEmpty { null }
            return true
        }
        clueSac.find(low)?.let { m -> readAbility(w.onClueSac, after(m, 1), w, full = true); return true }
        secondMain.find(low)?.let { m ->
            readAbility(w.postCombat, after(m, 2), w, full = true)
            w.postCombatNeedsAttack = m.groupValues[1].isNotEmpty()
            return true
        }
        eachUpkeepLine.find(low)?.let { m -> readAbility(w.eachUpkeep, after(m, 1), w, full = true); return true }
        Regex("other clues you control become (\\d+)/\\d+").find(low)?.let { w.animateClues = it.groupValues[1].toInt(); return true }
        if ("create a token that's a copy of enchanted artifact" in low && "eight or more artifacts with the same name" in low) { w.mechanized = true; return true }
        if ("if you control twenty or more artifacts, you win the game" in low) { w.artifactWin = 20; return true }
        if ("if you would create a clue, food, or treasure token, instead create one of each" in low) { w.manufactor = true; return true }
        if ("those tokens plus an additional food token are created instead" in low) { w.extraFood = true; return true }
        if ("those tokens plus a mutagen token are created instead" in low) { w.extraMutagen = true; return true }
        Regex("^\\{t\\}: add \\{([wubrg])\\} for each ([a-z]+?)s? you control").find(low)?.let { w.manaPer = it.groupValues[2] to it.groupValues[1].uppercase()[0]; return true }
        Regex("^tokens you control have \"\\{t\\}: add \\{([wubrg])\\}").find(low)?.let { w.manaPer = "token" to it.groupValues[1].uppercase()[0]; return true }
        Regex("^tap an untapped artifact you control: add \\{([wubrg])\\}").find(low)?.let { w.manaPer = "artifact" to it.groupValues[1].uppercase()[0]; return true }
        if ("nonartifact spells you cast have improvise" in low) { w.improvise = true; return true }
        if (Regex("^when [^,]+ enters, you become the monarch").containsMatchIn(low)) { w.monarch = true; return true }
        if (Regex("^foods you control have \"\\{t\\}, sacrifice this artifact: add one mana").containsMatchIn(low)) { w.foodMana = true; return true }
        if ("tap two untapped artifacts you control: draw a card" in low) { w.tapTwoDraw = true; return true }
        if ("creatures you control get +x/+x, where x is the number of cards you've drawn this turn" in low) { w.drawnAnthem = true; return true }
        if ("where x is the number of cards you've drawn this turn minus one" in low) { w.combatCountersDrawn = true; return true }
        if ("except the first one you draw in each of your draw steps, draw two cards instead" in low) { w.drawDouble = true; return true }
        if (Regex("^at the beginning of each end step, if you created a token this turn, draw a card").containsMatchIn(low)) { w.endDrawIfToken = true; return true }
        if ("that many plus one +1/+1 counters are put on it instead" in low) { w.counterPlus += 1; return true }
        if ("twice that many +1/+1 counters are put on it instead" in low) { w.counterTimes = true; return true }
        if (Regex("^whenever you put one or more \\+1/\\+1 counters on a creature you control, [^,]* deals that much damage").containsMatchIn(low)) { w.counterDamage = true; return true }
        if (Regex("^whenever you put a counter on a creature you control, draw a card").containsMatchIn(low)) { w.counterDraw = true; return true }
        if (Regex("^whenever you draw a card, put a \\+1/\\+1 counter on").containsMatchIn(low)) { w.drawCounter = true; return true }
        if ("double all damage that creatures you control with counters on them would deal" in low) { w.doubleCountered = true; return true }
        if ("attacking creatures you control have double strike" in low) { w.attackersDouble = true; return true }
        entersWith.find(low)?.let { w.entersWithCounters = number(it.groupValues[1]); return true }
        if (Regex("^when this aura enters, put a \\+1/\\+1 counter on enchanted creature").containsMatchIn(low)) { w.auraCounter = 1; return true }
        if (Regex("^enchanted creature has \"whenever this creature attacks, double the number of \\+1/\\+1 counters on it").containsMatchIn(low)) {
            w.onAttack.doubleCounters = true
            w.onAttackEquipped = true
            return true
        }
        if (Regex("for each other opponent, create a token that's a copy of [^.]* tapped and attacking").containsMatchIn(low)) { w.myriad = true; return true }
        if ("at the beginning of enchanted opponent's end step, you and that player each draw a card" in low) { w.truce = true; return true }
        Regex("each noncreature artifact you control becomes a (\\d+)/\\d+ artifact creature until end of turn").find(low)?.let { w.animateArtifacts = it.groupValues[1].toInt(); return true }
        if ("noncreature artifact you control becomes a 0/0 artifact creature" in low) { w.riseAndShine = 4; return true }
        tokenDrain.find(low)?.let { w.tokenDrain = it.groupValues[1].toInt(); return true }
        tokenBatchDrain.find(low)?.let { w.tokenBatchDrain = it.groupValues[1].toInt(); return true }
        dieDrain.find(low)?.let { w.dieDrain = it.groupValues[1].toInt(); return true }
        leaveToken.find(low)?.let { m -> w.leaveToken = tokenKind(line) ?: TokenKind(m.groupValues[1].toInt(), CREATURE_TYPES, emptySet()); return true }
        if (leaveDraw.containsMatchIn(low)) { w.leaveDraw = true; return true }
        discardCreatureToken.find(low)?.let { w.discardToken = it.groupValues[1].toInt(); return true }
        reducer.find(low)?.let { m ->
            w.reducers = m.groupValues[1].split(Regex(" and/or | and ")).map { it.trim() to m.groupValues[2].toInt() }
            return true
        }
        sneak.find(low)?.let { w.sneakCost = ManaOddsCalc.cost(it.groupValues[1]); return true }
        hasteFor.find(low)?.let { m -> if (w.permanent && !low.startsWith("when")) w.hasteFor = m.groupValues[1] }
        if (pumpPerCreature.containsMatchIn(low) && (low.startsWith("when") || !w.permanent)) {
            w.overrunPerCreature = true
            return true
        }
        activatedPump.find(low)?.let { m ->
            val self = m.groupValues[5].isNotEmpty()
            val words = m.groupValues[3].trim().split(' ').filter { it.isNotBlank() && it !in colourWords }
            val kind = if (self) null else if (m.groupValues[4] != "creatures") m.groupValues[4].removeSuffix("s") else words.lastOrNull() ?: "creature"
            w.pumps += ActivatedPump(ManaOddsCalc.cost(m.groupValues[1]), kind, m.groupValues[6].toInt())
            return true
        }
        // Attack triggers: when it attacks (Teval, Cloud, Ruby, Archon "enters or attacks"), when equipped creature
        // attacks (Sword of the Animist), when you attack (Varina, Temmet), when a Dragon attacks (Utvara).
        val selfAttack = Regex("^whenever (?:this creature|this permanent|$short|$full)(?: and at least one other creature)?( enters or)? attacks").find(low)
        val equipped = equippedAttack.containsMatchIn(low)
        val you = youAttack.find(low)
        val kind = attackKindToken.find(low)
        if (selfAttack != null || equipped || you != null || kind != null) {
            if (attackDouble.containsMatchIn(line)) w.attackDouble = true
            val a = when {
                you != null -> w.onYouAttack.also { it.perKind = you.groupValues[1].ifEmpty { null } }
                kind != null -> w.onKindAttack.also { w.kindAttack = kind.groupValues[1] }
                else -> w.onAttack.also { w.onAttackEquipped = equipped }
            }
            readAbility(a, line.substringAfter(',').trim(), w, full = true)
            whileCondition.find(low)?.let { a.needsPower = it.groupValues[1].toInt() }
            if (Regex("(?:other )?creatures you control get \\+(\\d+)/").find(low)?.let { w.attackOverrun += it.groupValues[1].toInt() } != null) a.teamPump = 0
            // "Whenever this creature enters or attacks" (Archon of Cruelty) also does it when it enters.
            if (selfAttack?.groupValues?.get(1)?.isNotEmpty() == true) {
                w.drainOnce += a.drainEach
                w.oneOnce += a.drainOne
                w.drawOnce += a.draw
                if (a.tokens > 0) { w.tokensOnce += a.tokens * maxOf(1, a.token?.power ?: 1); if (w.token == null) w.token = a.token }
                a.made.forEach { (k, n) -> w.madeOnce[k] = (w.madeOnce[k] ?: 0) + n; w.artifactsOnce += n }
                if (a.treasures > 0) { w.madeOnce["treasure"] = (w.madeOnce["treasure"] ?: 0) + a.treasures; w.artifactsOnce += a.treasures; w.treasuresOnce += a.treasures }
            }
            return true
        }
        if (Regex("^whenever (?:this creature|$short|$full|equipped creature) deals combat damage to a player").containsMatchIn(low) &&
            !combatDraw.containsMatchIn(line)
        ) {
            readAbility(w.onHit, line.substringAfter(','), w, full = true)
            return w.onHit.any
        }
        val afterComma = low.substringAfter(", ")
        if (w.permanent && low.startsWith("when") && "until end of turn" in afterComma) lordText.find(afterComma)?.takeIf { it.groupValues[3] == "creatures" }?.let { m ->
            // "When this creature enters / When you cast this spell, creatures you control get +2/+2 until end of turn"
            // (End-Raze Forerunners, Jubilation, Decimator): counted the turn it comes in.
            w.overrun += m.groupValues[5].toInt()
            return true
        }
        return false
    }

    /** An activated ability: a sacrifice-free {T} or mana ability the win turn uses when it pays (since 1.55). */
    private fun readActivated(w: WinCard, line: String, low: String): Boolean {
        val head = line.substringBefore(':')
        if ("add {" in low.substringAfter(':').take(8) || " add " in low.substringAfter(':').take(6)) return false
        val a = Ability()
        val taps = abilityCost(head, a) ?: return false
        val effect = line.substringAfter(':')
        readAbility(a, effect, w, full = true)
        if (copyExiled.containsMatchIn(effect.lowercase()) && "sacrifice two artifact tokens" in head.lowercase()) a.copyExiled = true
        if (!a.any) return false
        if (taps) w.taps += a else w.activated += a
        return true
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
        c.land || c.pumpSpell || c.xEach || c.xTokens > 0 -> false
        // An instant or sorcery that's a combo piece waits for the rest of the combo.
        c.combo && !c.permanent -> false
        spellTriggers -> true
        fodder && c.permanent -> true
        creatureTriggers && c.creature -> true
        else -> c.creature && c.power > 0 || c.tokensOnce + c.tokensTurn > 0 || c.dealsDamage || c.anthem + c.overrun > 0 ||
            c.drawOnce + c.drawTurn > 0 || c.combo && c.permanent || c.castTriggers.isNotEmpty() || c.enterTriggers.isNotEmpty() ||
            (c.equipment || c.aura) && c.gearBonus || c.attackDouble || c.targetedBonus > 0 || c.combatDraw != 0 ||
            c.sacrifice != null || c.artifactsOnce + c.artifactsTurn + c.artifactsLandfall > 0 || c.tutorUseful || c.newUse || c.use55 ||
            c.creature && c.mana.kind == ManaOddsCalc.Kind.CREATURE
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
        val features = Features((kinds + leaders).toSet())
        // The games are played in [BATCHES] batches side by side (on phones with more cores), each with its own seed
        // from [random], so the results don't depend on the phone. Since 1.55.
        val batches = minOf(BATCHES, games)
        val seeds = List(batches) { random.nextLong() }
        val results = arrayOfNulls<Batch>(batches)
        val failed = arrayOfNulls<Throwable>(batches)
        val threads = (0 until batches).map { b ->
            Thread {
                try {
                    results[b] = playBatch(games / batches + if (b < games % batches) 1 else 0, Random(seeds[b]), deck, leaders, own, plans, features, turns)
                } catch (e: Throwable) {
                    failed[b] = e
                }
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        failed.firstOrNull { it != null }?.let { throw it }
        for (r in results.filterNotNull()) {
            for (t in 0..turns) {
                won[t] += r.won[t]
                byCombo[t] += r.byCombo[t]
                byMaze[t] += r.byMaze[t]
            }
            r.dealt.forEachIndexed { i, d -> dealt[i] += d }
            wins += r.wins
            knockouts += r.knockouts
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
                tokenMakers = all.count { it.tokensOnce + it.tokensTurn + it.landfallTokens + it.xTokens > 0 || it.leaveToken != null },
                triggers = all.count { it.castTriggers.isNotEmpty() || it.enterTriggers.isNotEmpty() },
                drains = all.count { it.dealsDamage },
                gear = all.count { (it.equipment || it.aura) && it.gearBonus },
                pumps = all.count { it.pumpSpell || it.overrun + it.attackOverrun > 0 || it.overrunPerCreature || it.pumps.isNotEmpty() || it.background > 0 },
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
                xSpells = all.count { it.xEach || it.xTokens > 0 },
                mazeEnd = all.any { it.mazeEnd },
                graveyard = all.count { it.graveyardUse },
                copies = all.count { it.copyUse },
                planeswalkers = all.count { it.planeswalker && it.loyalties.isNotEmpty() },
                cascade = all.count { it.cascade > 0 || it.cascadeFirst || it.discover + it.discoverPower + it.capstone > 0 || it.paradigm },
            ),
            byMaze = if (byMaze.any { it > 0 }) cumulative(byMaze) else emptyList(),
        )
    }

    private val CREATURE_TYPES = setOf("Creature")

    /** How many batches of games are played side by side (see [calculate]). */
    private const val BATCHES = 4

    private fun playBatch(
        count: Int,
        random: Random,
        deck: List<WinCard>,
        leaders: List<WinCard>,
        own: Set<Char>,
        plans: List<ComboPlan>,
        features: Features,
        turns: Int,
    ): Batch {
        val r = Batch(turns)
        repeat(count) {
            val (hand, rest) = ManaOddsCalc.keepHand(deck, random) { it.mana }
            val game = Game(hand, rest.toMutableList(), leaders, own, plans, features)
            val turn = game.play(turns)
            if (turn > 0) {
                r.won[turn]++
                r.wins++
                if (game.byCombo) r.byCombo[turn]++
                if (game.byMaze) r.byMaze[turn]++
                game.dealt.forEachIndexed { i, d -> r.dealt[i] += d }
                if (game.knockout) r.knockouts++
            }
        }
        return r
    }

    /** One batch's tallies. */
    private class Batch(turns: Int) {
        val won = IntArray(turns + 1)
        val byCombo = IntArray(turns + 1)
        val byMaze = IntArray(turns + 1)
        val dealt = LongArray(SOURCES.size)
        var wins = 0
        var knockouts = 0
    }

    private class Source(val colours: Set<Char>, val amount: Int, val from: Int, val creature: Boolean = false)

    /** A creature, token or other permanent on the battlefield; [gear] are the equipment and auras on it. */
    private class Entry(
        val card: WinCard?,
        val turn: Int,
        var power: Int,
        var creature: Boolean,
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

        fun isA(kind: String) = kind == "token" && token || subtypes.any { it.equals(kind, true) } || types.any { it.equals(kind, true) } ||
            art != null && art == kind
        val gear = ArrayList<WinCard>()
        var on: Entry? = null
        /** The mana it makes, gone when it's sacrificed. */
        var source: Source? = null
        /** The turn it was tapped for an ability or mana (it doesn't attack then). */
        var tapped = 0

        // Since 1.55.
        /** A token (a copy of a card has its card). */
        var token = card == null
        var treasure = false
        /** An artifact token's kind: "treasure", "clue", "food", "mutagen"… (since 1.55). */
        var art: String? = null
        /** The turn a token joined combat tapped and attacking (Endless Foot Assault). */
        var joins = 0
        var flying = card?.flying == true
        var per: String? = null
        /** Put onto the battlefield without being cast from the hand (Chainer's haste). */
        var cheated = false
        var hasteNow = false
        /** Sacrificed at the end of the turn (Sneak Attack). */
        var sacrificeAtEnd = false
        var loyalty = card?.loyalty ?: 0
        /** The card that goes to the graveyard (a Phyrexian Metamorph copying something stays a Metamorph). */
        var self: WinCard? = card
    }

    /** What the deck's cards can do at all, so a game skips the checks for the rest (they'd scan the battlefield). Since 1.55. */
    private class Features(cards: Set<WinCard>) {
        val myriadFor = cards.any { it.myriadFor != null }
        val lords = cards.any { it.lords.isNotEmpty() }
        val hasteFor = cards.any { it.hasteFor != null }
        val drawTriggers = cards.any { it.drawCounter || it.onSecondDraw.any }
        val counterMods = cards.any { it.counterPlus > 0 || it.counterTimes || it.counterDamage || it.counterDraw }
        val tokenTriggers = cards.any { it.tokenDrain + it.tokenBatchDrain > 0 || it.extraFood || it.extraMutagen }
        val opponentTurns = cards.any { it.onOppSecondDraw.any || it.oppDrawDrain > 0 || it.oppDrawTreasure || it.eachDrawStep > 0 || it.eachUpkeepDraws || it.eachUpkeep.any || it.truce }
        val castTriggers55 = cards.any { it.onSecondSpell.any || it.onCast.isNotEmpty() }
        val creatureEnter = cards.any { it.onCreatureEnter.any }
        val kindTriggers = cards.any { it.kindTriggers.isNotEmpty() }
        val enterTriggers = cards.any { it.enterTriggers.isNotEmpty() }
        val pool55 = cards.any { it.manaPer != null || it.improvise }
        val foodMana = cards.any { it.foodMana }
        val costChanges = cards.any { it.reducers.isNotEmpty() || it.affinityFor != null }
        val dorks = cards.any { it.mana.kind == ManaOddsCalc.Kind.CREATURE }
        val manaTokens = cards.any {
            it.treasuresOnce + it.treasuresTurn + it.treasuresLandfall > 0 || it.foodMana || it.manufactor || it.monument ||
                it.kindTriggers.any { k -> k.treasures > 0 } || it.oppDrawTreasure || it.abilities().any { a -> a.treasures > 0 }
        }
        val reanimator = cards.any { c ->
            listOf(c.onEnter, c.eachTurn, c.onHit).any { it.reanimate > 0 || it.copyExiled } || c.taps.any { it.reanimate > 0 || it.copyExiled } ||
                c.loyalties.any { it.second.reanimate > 0 } || c.castFromGraveyard != null
        }
        val mazeDeck = cards.any { it.mazeEnd }
        val fodder = cards.any { it.sacrifice != null }
        val eachUpkeep = cards.any { it.eachUpkeep.any || it.eachUpkeepDraws || it.mechanized || it.artifactWin > 0 }
        val eachTurn = cards.any { it.eachTurn.any }
        val animate = cards.any { it.animateClues > 0 || it.animateArtifacts > 0 }
        val pumps = cards.any { it.pumps.isNotEmpty() }
        val attack55 = cards.any { it.onAttack.any || it.onYouAttack.any || it.onKindAttack.any || it.attackOverrun > 0 }
        val afterCombat = cards.any { it.onAnyHit.any || it.counteredHitDraw || it.onHit.any || it.postCombat.any || it.endDrawIfToken || it.monarch || it.sneakCost != null }
        val clues = cards.any { it.onClueSac.any } || cards.any { c -> (c.madeOnce + c.madeTurn + c.madeLandfall).containsKey("clue") || c.abilities().any { it.made.containsKey("clue") } || c.kindTriggers.any { it.made.containsKey("clue") } }
        val mutagens = cards.any { c -> c.extraMutagen || (c.madeOnce + c.madeTurn + c.madeLandfall).containsKey("mutagen") || c.abilities().any { it.made.containsKey("mutagen") } || c.kindTriggers.any { it.made.containsKey("mutagen") } }
        val drawnBonus = cards.any { it.drawnAnthem || it.combatCountersDrawn || it.tapTwoDraw }
        val doubles = cards.any { it.attackersDouble || it.doubleCountered || it.background > 0 || it.attackDouble || it.targetedBonus > 0 }
        val graveyardCasts = cards.any { it.castFromGraveyard != null || it.selfFromGraveyard != null || it.flashbackCost != null }
        val planeswalkers = cards.any { it.planeswalker && it.loyalties.isNotEmpty() }
        val abilities = cards.any { it.taps.isNotEmpty() || it.activated.isNotEmpty() }
        val sneak = cards.any { it.sneakCost != null }
        val xTokens = cards.any { it.xTokens > 0 }
        val drawDouble = cards.any { it.drawDouble }
        val cascade = cards.any { it.cascadeFirst || it.discoverPower > 0 || it.cascadeReturn }
    }

    /** One game, played alone turn by turn. */
    private class Game(
        val hand: MutableList<WinCard>,
        val rest: MutableList<WinCard>,
        val leaders: List<WinCard>,
        val own: Set<Char>,
        val combos: List<ComboPlan>,
        val f: Features,
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
        val mazeDeck = f.mazeDeck
        var instantsCast = 0

        /** The deck has a sacrifice ability (in the command zone or the library): its permanents are fodder. */
        val fodder = f.fodder

        // Since 1.55.
        val graveyard = ArrayList<WinCard>()
        /** The deck brings creatures back from the graveyard, so creatures are worth discarding. */
        val reanimator = f.reanimator
        var overrun = 0
        val doubleFor = HashSet<String>()
        var discardsThisTurn = 0
        var leaveDrawn = false
        var graveyardCasts = 0
        var equipFreeUsed = false
        /** How deep cards entering have set off more of them (a guard against loops). */
        var entering = 0
        /** The same for cascade and discover. */
        var cascading = 0
        /** Paradigm spells that have resolved: a copy of each is cast for free every first main phase. */
        val paradigms = ArrayList<WinCard>()

        fun mainOptions() = (command + hand).filter { useful(it, spellTriggers, creatureTriggers, fodder) && ready(it) }
            .sortedWith(compareByDescending<WinCard> { it in command }.thenByDescending { it.manaValue })

        // Items 9–11 of 1.55: cards drawn this turn, opponents' draws, triggers once each turn.
        var drawnThisTurn = 0
        var firstOfDrawStep = false
        val oppDrawn = IntArray(OPPONENTS)
        /** Whose turn it is among the opponents (-1: ours). */
        var opponentTurn = -1
        var tokensThisTurn = false
        val firedThisTurn = HashSet<Any>()
        var extraGuard = false
        var extraCombat = false
        /** Cyberdrive Awakener's 4/4 artifacts this turn. */
        var animateNow = 0

        fun newTurn() {
            drawnThisTurn = 0
            oppDrawn.fill(0)
            firedThisTurn.clear()
            tokensThisTurn = false
        }

        /** Draws [n] cards: twice as many with Teferi's Ageless Insight (but the first of the draw step). */
        fun draw(n: Int) = repeat(n) {
            val first = firstOfDrawStep
            firstOfDrawStep = false
            repeat(if (!first && f.drawDouble && board.any { it.card?.drawDouble == true }) 2 else 1) { drawOne() }
        }

        fun drawOne() {
            if (drawn >= rest.size) return
            hand += rest[drawn++]
            drawnThisTurn++
            if (f.drawTriggers) for (b in board.toList()) b.card?.let { c ->
                if (c.drawCounter) addCounters(b, 1)
                if (drawnThisTurn == 2 && c.onSecondDraw.any && !(c.oppSecondDrawTheirTurn && opponentTurn >= 0)) use(c.onSecondDraw, b)
            }
        }

        /** An opponent draws: Scrawling Crawler, Tataru Taru, and "an opponent draws their second card" (Council, Faerie Mastermind). */
        fun oppDraw(i: Int, n: Int) = repeat(n) {
            if (life[i] <= 0) return@repeat
            oppDrawn[i]++
            for (b in board.toList()) b.card?.let { c ->
                if (c.oppDrawDrain > 0) { life[i] -= c.oppDrawDrain; dealt[3] += c.oppDrawDrain.toLong() }
                if (c.oppDrawTreasure && opponentTurn != i && firedThisTurn.add(b to "treasure")) make("treasure", 1)
                if (oppDrawn[i] == 2 && c.onOppSecondDraw.any && (!c.oppSecondDrawTheirTurn || opponentTurn == i)) use(c.onOppSecondDraw, b)
            }
        }

        /**
         * The opponents' turns, for what they set off: their upkeep and draw step with Howling Mine and the like (each
         * player draws), Walking Archive, "at the beginning of each upkeep", Tenuous Truce. They do nothing else.
         */
        fun opponentTurns() {
            if (!f.opponentTurns) return
            for (i in life.indices) {
                if (life[i] <= 0) continue
                opponentTurn = i
                newTurn()
                board.filter { it.card?.eachUpkeepDraws == true }.sumOf { it.counters }.takeIf { it > 0 }?.let { oppDraw(i, it) }
                board.toList().forEach { b -> b.card?.let { if (it.eachUpkeep.any) use(it.eachUpkeep, b) } }
                oppDraw(i, 1 + board.sumOf { it.card?.eachDrawStep ?: 0 })
                if (life[i] > 0 && i == life.indices.firstOrNull { life[it] > 0 } && board.any { it.card?.truce == true }) { draw(1); oppDraw(i, 1) }
            }
            opponentTurn = -1
        }

        /** +1/+1 counters on a creature of ours: High Score, Michelangelo, Corpsejack Menace; Casey Jones, Mikey & Leo. */
        fun addCounters(e: Entry, n: Int) {
            if (n <= 0) return
            if (!f.counterMods) { e.counters += n; return }
            var k = n + board.sumOf { it.card?.counterPlus ?: 0 }
            repeat(board.count { it.card?.counterTimes == true }) { k *= 2 }
            e.counters += k
            for (b in board.toList()) b.card?.let { c ->
                if (c.counterDamage) one(k, 3)
                if (c.counterDraw && firedThisTurn.add(b to "counter")) draw(1)
            }
        }

        /** The creature that gets a counter or a pump: the commander, else the biggest that can attack. */
        fun bestForCounters(): Entry? = board.filter { it.creature && (it.turn < turn || hasHaste(it)) && it.card in leaders }.maxByOrNull { it.power }
            ?: board.filter { it.creature && (it.turn < turn || hasHaste(it)) }.maxByOrNull { it.power + it.counters }
            ?: board.filter { it.creature }.maxByOrNull { it.power + it.counters }

        /** Artifact tokens of a kind: Xorn, Erdwal Illuminator, Academy Manufactor; "whenever a token enters" (Leonardo). */
        fun make(kind: String, n: Int) {
            if (n <= 0) return
            var count = n
            if (kind == "treasure" && board.any { it.card?.treasureExtra == true }) count++
            if (kind == "clue" && board.any { it.card?.investigateExtra == true } && firedThisTurn.add("investigate")) count++
            val kinds = if (kind in setOf("clue", "food", "treasure") && board.any { it.card?.manufactor == true }) listOf("clue", "food", "treasure") else listOf(kind)
            for (k in kinds) repeat(count) {
                val e = Entry(null, turn, 0, false, false, false, ARTIFACT_TOKEN).apply { art = k; treasure = k == "treasure" }
                board += e
                arrived(e)
            }
            tokenMade(count * kinds.size)
        }

        fun makeAll(kinds: Map<String, Int>) = kinds.forEach { (k, n) -> make(k, n) }

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
            if (!f.enterTriggers) return
            for (b in board.toList()) b.card?.enterTriggers?.forEach { t ->
                if (t.land == land && !(t.another && b === self)) {
                    val n = if (t.amount == POWER) self?.let { (it.power + it.counters).coerceAtLeast(0) } ?: 0 else t.amount
                    if (t.each) each(n, 2) else one(n, 2)
                }
            }
        }

        fun token(power: Int, kind: TokenKind? = null): Entry =
            Entry(null, turn, power, true, false, false, kind?.types ?: CREATURE_TOKEN, kind?.subtypes.orEmpty()).also {
                it.flying = kind?.flying == true
                it.per = kind?.per
                board += it
                tokenMade(1)
                entered(it, false)
                creatureEntered(it)
                arrived(it)
            }

        /** Tokens just made: Mirkwood Bats and Kambal; Donatello's Mutagen and Peregrin Took's Food (since 1.55). */
        fun tokenMade(n: Int) {
            if (n <= 0) return
            tokensThisTurn = true
            if (!f.tokenTriggers) return
            for (b in board) b.card?.let { c ->
                each(c.tokenDrain * n, 3)
                each(c.tokenBatchDrain, 3)
            }
            if (!extraGuard) {
                extraGuard = true
                if (board.any { it.card?.extraMutagen == true }) make("mutagen", 1)
                if (board.any { it.card?.extraFood == true }) make("food", 1)
                extraGuard = false
            }
        }

        /** "Whenever another nontoken creature you control enters" (Lonis, Rocksteady). */
        fun creatureEntered(e: Entry) {
            if (!f.creatureEnter) return
            for (b in board.toList()) b.card?.let { c ->
                if (c.onCreatureEnter.any && !(c.creatureEnterAnother && b === e) && !(c.creatureEnterNontoken && e.token)) use(c.onCreatureEnter, b)
            }
        }

        /** How many permanents of a kind are on the battlefield ("shrine", "gate", "creature"…). Since 1.54. */
        fun count(kind: String) = board.count { it.isA(kind) }

        /** The Gates on the battlefield with different names, for Maze's End. */
        fun gateNames() = board.filter { it.isA("gate") }.mapNotNull { it.card?.name }.toSet()

        /** How deep "enters" triggers have set off more of them (a guard against loops). */
        var depth = 0

        /** "Whenever another Shrine you control enters" and the like (since 1.54). */
        fun arrived(e: Entry) {
            if (depth > 3 || !f.kindTriggers) return
            depth++
            for (b in board.toList()) b.card?.kindTriggers?.forEach { t ->
                if (!e.isA(t.kind) || (!t.self && b === e) || (t.nontoken && e.token)) return@forEach
                if (t.oncePerTurn && !firedThisTurn.add(b to t)) return@forEach
                makeAll(t.made)
                if (t.selfCounters > 0) addCounters(b, t.selfCounters)
                each(t.drainEach, 3)
                one(t.drainOne, 3)
                t.perEach?.let { each(count(it), 3) }
                t.perOne?.let { one(count(it), 3) }
                repeat(t.tokens) { token(t.token?.power ?: 1, t.token) }
                treasures(t.treasures)
                draw(t.draw)
                if (t.counters > 0) board.filter { it.creature }.forEach { addCounters(it, t.counters) }
                if (t.land) sources += Source(own, 1, turn + 1)
            }
            depth--
        }

        /** An effect counting permanents (since 1.54). */
        fun scaled(s: Scaled) {
            // "Choose a creature type. Draw a card for each permanent you control of that type": the most common type.
            val n = (if (s.per == "permanent") board.filter { it.creature }.flatMap { it.subtypes }.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
            else count(s.per)) * s.factor
            if (n <= 0) return
            when (s.what) {
                Effect.DRAIN -> if (s.each) each(n, 3) else one(n, 3)
                Effect.TOKENS -> repeat(minOf(n, 30)) { token(s.token?.power ?: 1, s.token) }
                Effect.DRAW -> draw(n)
                Effect.COUNTERS -> board.filter { it.creature }.forEach { addCounters(it, n) }
                Effect.PUMP -> overrun += n
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
            landEntered(e)
        }

        /** A land entered: landfall (damage, Food and Treasure, tokens, counters) and "whenever a Gate enters". Since 1.55 for every land. */
        fun landEntered(e: Entry?) {
            entered(null, true)
            e?.let { arrived(it) }
            for (b in board.toList()) b.card?.let { c ->
                makeAll(c.madeLandfall)
                repeat(c.landfallTokens) { token(c.landfallToken?.power ?: 1, c.landfallToken) }
                c.landfallCounters?.let { k -> board.filter { it.creature && it.isA(k) }.forEach { addCounters(it, 1) } }
            }
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

        /** An X spell in hand, with the X the mana allows (since 1.54; tokens since 1.55). */
        fun xSpell(pool: List<Pair<Set<Char>, Int>>, tokens: Boolean = false): Pair<WinCard, Int>? {
            val c = hand.filter { if (tokens) it.xTokens > 0 else it.xEach }.minByOrNull { it.manaValue } ?: return null
            val left = ManaOddsCalc.pay(pool + treasurePool(), costOf(c)) ?: return null
            val x = left.sumOf { it.second }
            return c to if (c.xCap > 0) minOf(x, c.xCap) else x
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

        fun treasures(n: Int) = make("treasure", n)

        /** Who carries the equipment and auras and gets the pump spells: the commander, else the biggest creature. */
        fun carrier(): Entry? = board.filter { it.creature && it.card in leaders }.maxByOrNull { it.power }
            ?: board.filter { it.creature }.maxByOrNull { it.power + it.gear.size }

        /** A card's cost with Dragonspeaker Shaman and the like, and affinity (since 1.55). */
        fun costOf(c: WinCard): ManaOddsCalc.Cost {
            if (!f.costChanges) return c.cost
            var less = 0
            for (b in board) b.card?.reducers?.forEach { (kind, n) ->
                val colour = COLOUR_WORDS[kind]
                if (kind == "spell" || colour != null && c.cost.pips.any { colour in it } || c.subtypes.any { it.equals(kind, true) } || c.types.any { it.equals(kind, true) }) less += n
            }
            c.affinityFor?.let { k -> less += if (k == "token") board.count { it.token } else count(k) }
            if (less == 0) return c.cost
            return ManaOddsCalc.Cost((c.cost.generic - less).coerceAtLeast(0), c.cost.pips)
        }

        /** Treasures as mana (Goldspan's make two), for checking what can be paid. */
        /** Treasures, and Foods with Ninja Pizza: one-use mana. */
        fun manaToken(e: Entry) = e.treasure || f.foodMana && e.art == "food" && board.any { it.card?.foodMana == true }

        fun treasurePool(): List<Pair<Set<Char>, Int>> {
            if (!f.manaTokens) return emptyList()
            val n = board.count { manaToken(it) }
            if (n == 0) return emptyList()
            val two = board.any { it.card?.treasureDouble == true }
            return List(n) { own.ifEmpty { setOf('C') } to if (two) 2 else 1 }
        }

        /** Mana creatures that can still tap now. */
        fun dorks() = if (!f.dorks) emptyList() else board.filter { e -> e.creature && e.source?.creature == true && e.source!!.from <= turn && e.tapped != turn }

        fun canSpend(pool: List<Pair<Set<Char>, Int>>, cost: ManaOddsCalc.Cost) =
            ManaOddsCalc.pay(pool + dorks().map { it.source!!.colours to it.source!!.amount } + treasurePool(), cost) != null

        /**
         * Pays [cost] from the lands and rocks first; then taps mana creatures (the smallest attackers first; they don't
         * attack then) and sacrifices Treasures as needed. Gilanra taps for any spell of 6 or more and draws. The mana
         * left, or null when it can't be paid (nothing is used then). Since 1.55.
         */
        fun spend(pool: List<Pair<Set<Char>, Int>>, cost: ManaOddsCalc.Cost, manaValue: Int = cost.manaValue): List<Pair<Set<Char>, Int>>? {
            val drawers = dorks().filter { manaValue >= (it.card?.manaDrawAt ?: 0) && (it.card?.manaDrawAt ?: 0) > 0 }
            if (drawers.isEmpty()) ManaOddsCalc.pay(pool, cost)?.let { return it }
            if (!f.dorks && !f.manaTokens) return null
            val extras = (drawers + dorks().filter { it !in drawers }.sortedBy { it.power + it.counters }).toMutableList<Entry>()
            val two = board.any { it.card?.treasureDouble == true }
            extras += board.filter { manaToken(it) }
            var p = pool
            val used = ArrayList<Entry>()
            for (e in extras) {
                p = p + (if (manaToken(e)) own.ifEmpty { setOf('C') } to (if (two && e.treasure) 2 else 1) else e.source!!.colours to e.source!!.amount)
                used += e
                ManaOddsCalc.pay(p, cost)?.let { left ->
                    for (u in used) {
                        if (manaToken(u)) {
                            board.remove(u)
                            left(u, artifact = true)
                        } else {
                            u.tapped = turn
                            if ((u.card?.manaDrawAt ?: 0) in 1..manaValue) draw(1)
                        }
                    }
                    return left
                }
            }
            return null
        }

        /** The mana creatures and Treasures that could pay, for the combo check (nothing is used). */
        fun potential(pool: List<Pair<Set<Char>, Int>>) = pool + dorks().map { it.source!!.colours to it.source!!.amount } + treasurePool()

        /** How much a creature card is worth on entering: its power and what it does when it enters. Since 1.55. */
        fun worth(c: WinCard): Int = (if (c.creature) c.power else 0) + enterWorth(c)

        fun enterWorth(c: WinCard): Int = 3 * c.drainOnce + c.oneOnce + (if (c.devotion != null) 6 else 0) + c.tokensOnce + 2 * c.drawOnce +
            3 * c.onEnter.reanimate.coerceAtMost(3) + 2 * c.onEnter.copies + 2 * c.overrun + (if (c.overrunPerCreature) board.count { it.creature } else 0) +
            c.scaled.count { !it.turn } * 3 + c.kindTriggers.size + c.fabricate

        /** Whether a card is worth casting now (since 1.55: reanimation needs something to bring back, and so on). */
        fun ready(c: WinCard): Boolean {
            if (c.permanent) return true
            val a = c.onEnter
            val other = c.drainOnce + c.oneOnce + c.tokensOnce + c.drawOnce + c.artifactsOnce + c.overrun > 0 || c.dealsDamage ||
                c.scaled.isNotEmpty() || c.tutorUseful || c.combo || c.mana.kind == ManaOddsCalc.Kind.RAMP || spellTriggers
            if (other || !c.use55) return true
            return a.reanimate > 0 && a.reanimateKind == "land" && graveyard.any { it.land } ||
                a.reanimate > 0 && reanimationWorth(a) >= (if (a.reanimate == ALL) 12 else 5) &&
                (!a.reanimateSacrifice || board.any { it.creature && it.card !in leaders }) ||
                a.flicker > 0 && board.any { e -> e.creature && !e.token && e.card?.let { enterWorth(it) >= 3 } == true } ||
                a.copies > 0 && board.any { e -> e.creature && e.card?.let { worth(it) >= 4 } == true } ||
                a.polymorph > 0 && board.count { it.creature && it.token } >= (if (a.polymorph == ALL) 2 else 1) ||
                a.toHand > 0 && graveyard.any { toHandOk(it, a.toHandKind) } ||
                a.lootDraw + a.mill + a.surveil > 0 || a.sacrificeForToken > 0
        }

        fun toHandOk(c: WinCard, kind: String?) = when (kind) {
            null, "" -> true
            "instant or sorcery" -> !c.permanent
            "land" -> c.land
            else -> c.types.any { it.equals(kind, true) } || c.subtypes.any { it.equals(kind, true) }
        }

        fun reanimateKindOk(c: WinCard, kind: String?, chosen: String?) = c.creature && when (kind) {
            null -> true
            "chosen" -> chosen == null || c.subtypes.contains(chosen)
            else -> if (kind.startsWith("non")) !c.isKind(kind.removePrefix("non").removePrefix("-")) else c.isKind(kind)
        }

        /** A card's type, subtype, colour ("white", "multicolored") or "legendary". */
        fun WinCard.isKind(k: String): Boolean = when {
            k == "legendary" -> legendary
            k == "multicolored" -> cost.pips.flatten().toSet().size >= 2
            k in COLOUR_WORDS -> cost.pips.any { COLOUR_WORDS.getValue(k) in it }
            else -> subtypes.any { it.equals(k, true) } || types.any { it.equals(k, true) }
        }

        /** For mass reanimation of a chosen type (Patriarch's Bidding): the type most creatures in the graveyard share. */
        fun chosenType(): String? = graveyard.filter { it.creature }.flatMap { it.subtypes }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        fun reanimationWorth(a: Ability): Int {
            val chosen = chosenType()
            val ok = graveyard.filter { reanimateKindOk(it, a.reanimateKind, chosen) }.map { worth(it) }.sortedDescending()
            return if (a.reanimate == ALL) ok.sum() - (if (a.reanimateEverything) board.filter { it.creature }.sumOf { it.power + it.counters } else 0)
            else ok.take(a.reanimate).sum()
        }

        /** Cards leaving the graveyard: Teval's and Quintorius's tokens, Kishla Skimmer (since 1.55). */
        fun leftGraveyard(cards: Collection<WinCard>) {
            if (cards.isEmpty()) return
            cards.forEach { graveyard.remove(it) }
            for (b in board.toList()) b.card?.let { c ->
                c.leaveToken?.let { token(it.power, it) }
                if (c.leaveDraw && !leaveDrawn) { leaveDrawn = true; draw(1) }
            }
        }

        /** Mills [n] cards. */
        fun mill(n: Int) = repeat(n) { if (drawn < rest.size) graveyard += rest[drawn++] }

        /** Surveil: a creature worth bringing back (in a deck that reanimates) or a spare land goes to the graveyard. */
        fun surveil(n: Int) {
            for (k in 0 until n) {
                val i = drawn + k
                if (i >= rest.size) break
                val c = rest[i]
                if (reanimator && c.creature && worth(c) >= 5 || c.land && hand.count { it.land } >= 2) {
                    graveyard += rest.removeAt(i)
                }
            }
        }

        /**
         * Discards [n] cards, picked by rules: in a deck that reanimates, a creature it can't cast soon (the best first);
         * with Vohar, an instant or sorcery the deck can't use; then spare lands, cards the deck can't use, the cheapest.
         * Returns the instants and sorceries discarded. Since 1.55.
         */
        fun discard(n: Int, spellsWanted: Boolean = false): Int {
            var spellsOut = 0
            val manaSoon = sources.count { !it.creature } + 2
            repeat(n) {
                if (hand.isEmpty()) return spellsOut
                val lands = hand.filter { it.land }
                val c = (if (reanimator) hand.filter { it.creature && it.manaValue > manaSoon }.maxByOrNull { worth(it) } else null)
                    ?: (if (spellsWanted) hand.firstOrNull { !it.permanent && !useful(it, spellTriggers, creatureTriggers, fodder) } else null)
                    ?: (if (lands.size > 2 || lands.size > 1 && sources.size >= 6) lands.minBy { it.mana.colours.size } else null)
                    ?: hand.firstOrNull { !it.land && !useful(it, spellTriggers, creatureTriggers, fodder) }
                    ?: hand.filter { !it.land }.minByOrNull { it.manaValue }
                    ?: lands.first()
                hand.remove(c)
                graveyard += c
                if (!c.permanent) spellsOut++
                discarded(c)
            }
            return spellsOut
        }

        /** A card the deck can spare to discard as a cost: a third land, or one it can't use. */
        fun spareCard(): WinCard? = hand.filter { it.land }.takeIf { it.size > 2 }?.first()
            ?: hand.firstOrNull { !it.land && !useful(it, spellTriggers, creatureTriggers, fodder) }
            ?: if (reanimator) hand.filter { it.creature && it.manaValue > sources.count { s -> !s.creature } + 2 }.maxByOrNull { worth(it) } else null

        /** Discard payoffs: Monument to Endurance, Bone Miser (since 1.55). */
        fun discarded(c: WinCard) {
            discardsThisTurn++
            for (b in board.toList()) b.card?.let { p ->
                if (p.monument) when (discardsThisTurn) {
                    1 -> each(3, 3)
                    2 -> draw(1)
                    3 -> treasures(1)
                }
                if (p.discardToken > 0 && c.creature) token(p.discardToken)
            }
        }

        /** Brings creatures back from the graveyard (the ones worth most), with their enter effects. Since 1.55. */
        fun reanimate(a: Ability) {
            // Lands back from the graveyard (Titania, Lord Windgrace).
            if (a.reanimateKind == "land") {
                val lands = graveyard.filter { it.land }.take(if (a.reanimate == ALL) 99 else a.reanimate)
                leftGraveyard(lands)
                lands.forEach { landOnto(it, tapped = false) }
                return
            }
            val chosen = chosenType()
            var ok = graveyard.filter { reanimateKindOk(it, a.reanimateKind, chosen) }.sortedByDescending { worth(it) }
            if (a.reanimate != ALL) ok = ok.take(a.reanimate)
            if (ok.isEmpty()) return
            if (a.reanimateSacrifice) sacrificeWorst() ?: return
            if (a.reanimateEverything) board.filter { it.creature && it.card !in leaders }.forEach { e -> board.remove(e); left(e, artifact = "Artifact" in e.types) }
            leftGraveyard(ok)
            val back = ok.map { enter(it, cheated = true) }
            if (back.isNotEmpty() && board.any { it.card?.copyReanimated == true }) back.maxByOrNull { e -> e.card?.let { worth(it) } ?: 0 }?.card?.let { enter(it, cheated = true, token = true) }
        }

        /** Sacrifices the creature worth least (a token first); null when there's none. */
        fun sacrificeWorst(): Entry? {
            val e = board.filter { it.creature && it.card !in leaders }.minByOrNull { (if (it.token) 0 else 3) + it.power + it.counters + (it.card?.let { c -> enterWorth(c) } ?: 0) } ?: return null
            board.remove(e)
            left(e, artifact = "Artifact" in e.types)
            return e
        }

        /** What an ability, spell or trigger does (since 1.55); [user] is the permanent using it, [attackers] for "that many". */
        fun use(a: Ability, user: Entry? = null, attackers: List<Entry> = emptyList()) {
            if (!a.any) return
            draw(a.draw)
            each(a.drainEach, 3)
            one(a.drainOne, 3)
            a.tokensPer?.let { k -> repeat(minOf(count(k), 20)) { token(a.token?.power ?: 1, a.token) } }
            if (a.tokens > 0) repeat(a.tokens) { token(a.token?.power ?: 1, a.token).also { t -> if (a.attackingTokens) { t.joins = turn; t.hasteNow = true } } }
            makeAll(a.made)
            if (a.powerAtLeast == 0 || user?.let { gearPower(it).first >= a.powerAtLeast } == true) make("treasure", a.treasures)
            if (a.selfCounters > 0 && user != null) addCounters(user, a.selfCounters)
            if (a.countersAll > 0) board.filter { it.creature }.forEach { addCounters(it, a.countersAll) }
            if (a.countersTarget > 0) bestForCounters()?.let { addCounters(it, a.countersTarget) }
            if (a.doubleCounters && user != null) addCounters(user, user.counters)
            if (a.drawPerCounters && user != null) draw(user.counters)
            if (a.copySelf && user?.card != null) enter(user.card, cheated = true, token = true)
            if (a.landFromHand) hand.firstOrNull { it.land }?.let { l -> hand.remove(l); landOnto(l, tapped = true) }
            if (a.eachPlayerDraws > 0) { draw(a.eachPlayerDraws); life.indices.filter { life[it] > 0 }.forEach { oppDraw(it, a.eachPlayerDraws) } }
            if (a.oppDraws > 0) life.indices.firstOrNull { life[it] > 0 }?.let { oppDraw(it, a.oppDraws) }
            if (a.copyFromGraveyard) graveyard.filter { it.creature }.maxByOrNull { worth(it) }?.let { leftGraveyard(listOf(it)); enter(it, cheated = true, token = true) }
            if (a.extraCombat) extraCombat = true
            if (a.drawPerEquipped) draw(attackers.count { it.gear.any { g -> g.equipment } })
            repeat(a.landRamp) { rampLand(own, tapped = true) }
            mill(a.mill)
            surveil(a.surveil)
            val many = if (a.thatMany) attackers.count { e -> a.perKind?.let { e.isA(it) } ?: true } else 0
            if (a.lootDraw + many > 0) {
                draw(a.lootDraw + many)
                val spells = discard(a.lootDiscard + many, spellsWanted = a.lootDrain > 0)
                if (spells > 0) each(a.lootDrain, 3)
            }
            repeat(a.toBottom) { hand.filter { it.land }.takeIf { it.size > 2 }?.first()?.let { hand.remove(it) } ?: hand.minByOrNull { it.manaValue }?.let { hand.remove(it) } }
            if (a.reanimate > 0) reanimate(a)
            if (a.toHand > 0) {
                val back = graveyard.filter { toHandOk(it, a.toHandKind) }.sortedByDescending { if (it.land) 1 else worth(it) + it.manaValue }.take(a.toHand)
                leftGraveyard(back)
                hand += back
            }
            if (a.exileGraveyard > 0 && graveyard.size >= a.exileGraveyard) {
                // Cards the deck won't bring back: lands and spells first.
                leftGraveyard(graveyard.sortedBy { if (it.creature) worth(it) + 10 else 0 }.take(a.exileGraveyard))
            }
            if (a.landBack) graveyard.firstOrNull { it.land }?.let { l ->
                leftGraveyard(listOf(l))
                val source = Source(l.mana.colours, 1, turn + 1)
                sources += source
                val e = Entry(l, turn, 0, false, false, false).also { it.source = source }
                board += e
                landEntered(e)
            }
            repeat(a.flicker) { flickerBest() }
            repeat(a.copies) { copyBest() }
            if (a.copyArtifactToken) board.filter { it.token && "Artifact" in it.types }.maxByOrNull { if (it.creature) 10 + it.power else if (it.treasure) 1 else 0 }?.let { t ->
                if (t.creature) token(t.power, TokenKind(t.power, t.types, t.subtypes, t.flying, t.per))
                else make(t.art ?: "other", 1)
            }
            if (a.copyExiled) {
                val tokens = board.filter { it.token && "Artifact" in it.types && !it.creature }.take(2).ifEmpty { null }
                    ?: board.filter { it.token && "Artifact" in it.types }.sortedBy { it.power }.take(2)
                val target = graveyard.filter { it.creature || "Artifact" in it.types }.maxByOrNull { worth(it) }
                if (tokens.size == 2 && target != null) {
                    tokens.forEach { board.remove(it); left(it, artifact = true) }
                    leftGraveyard(listOf(target))
                    enter(target, cheated = true, token = true)
                }
            }
            if (a.polymorph > 0) {
                val targets = board.filter { it.creature && it.token }.let { if (a.polymorph == ALL) it else it.take(1) }
                    .ifEmpty { if (a.polymorph == ALL) emptyList() else board.filter { it.creature && it.card !in leaders && it !== user }.minByOrNull { it.power }?.let { listOf(it) }.orEmpty() }
                for (t in targets) {
                    board.remove(t)
                    t.source?.let { sources.remove(it) }
                    val i = (drawn until rest.size).firstOrNull { rest[it].creature || a === user?.card?.loyalties?.firstOrNull { l -> l.second === a }?.second && rest[it].planeswalker } ?: continue
                    enter(rest.removeAt(i), cheated = true)
                }
            }
            a.doubleStrikeFor?.let { doubleFor += it }
            if (a.sacrificeForToken > 0) sacrificeWorst()?.let { token(a.sacrificeForToken, TokenKind(a.sacrificeForToken, CREATURE_TOKEN, setOf("Dragon"), flying = true)) }
            if (a.teamPump > 0) overrun += a.teamPump
        }

        /** Exiles and returns the nontoken creature worth most on entering: its enter effects happen again. */
        fun flickerBest() {
            val e = board.filter { it.creature && !it.token && it.card != null }.maxByOrNull { enterWorth(it.card!!) }?.takeIf { enterWorth(it.card!!) > 0 } ?: return
            board.remove(e)
            e.source?.let { sources.remove(it) }
            board.filter { it.on === e }.forEach { g -> g.on = null; if (g.card?.aura == true) board.remove(g) }
            enter(e.card!!, cheated = true)
        }

        /** A token copy of the creature worth most (not legendary if there's another). */
        fun copyBest() {
            val e = board.filter { it.creature && it.card != null }.maxByOrNull { worth(it.card!!) - if (it.card.legendary) 4 else 0 } ?: return
            enter(e.card!!, cheated = true, token = true)
        }

        /** A basic land from a ramp effect (tapped or not), with landfall. */
        fun rampLand(colours: Set<Char>, tapped: Boolean) {
            val s = Source(colours, 1, if (tapped && !board.any { it.card?.untapsLands == 1 }) turn + 1 else turn)
            sources += s
            val e = Entry(null, turn, 0, false, false, false, LAND).apply { source = s; token = false }
            board += e
            landEntered(e)
        }

        fun cast(c: WinCard, free: Boolean = false, x: Int = 0, fromGraveyard: Boolean = false, copy: Boolean = false): Int {
            if (!copy && !command.remove(c)) if (!hand.remove(c) && fromGraveyard) leftGraveyard(listOf(c))
            if (!free) for (b in board) b.card?.castTriggers?.forEach { t ->
                if (t.firstOnly && spells > 0) return@forEach
                if (t.filter == Filter.CREATURE && !c.creature || t.filter == Filter.NONCREATURE && c.creature) return@forEach
                val n = if (t.amount == MANA_VALUE) c.manaValue + x else t.amount
                if (t.each) each(n, 2) else one(n, 2)
            }
            if (!free) {
                spells++
                if (f.castTriggers55) for (b in board.toList()) b.card?.let { p ->
                    if (spells == 2 && p.onSecondSpell.any) use(p.onSecondSpell, b)
                    for ((kind, a) in p.onCast) if (castKind(c, kind) && (!a.oncePerTurn || firedThisTurn.add(b to a))) use(a, b)
                }
                // Cascade and discover (since 1.55): Apex Devastator four times, Maelstrom Nexus on the first spell each turn,
                // Monstrous Vortex on a creature spell with power 5 or more. Aurora Phoenix comes back to the hand.
                val cascades = c.cascade + if (f.cascade && spells == 1 && board.any { it.card?.cascadeFirst == true }) 1 else 0
                if (cascades > 0 && f.cascade) graveyard.filter { it.cascadeReturn }.takeIf { it.isNotEmpty() }?.let { back -> leftGraveyard(back); hand += back }
                if (f.cascade && c.creature) for (b in board.toList()) if (b.card != null && b.card.discoverPower in 1..c.power) reveal(c.manaValue + x)
                repeat(cascades) { reveal(c.manaValue + x - 1) }
            }
            // In a Maze's End deck, Gate searches bring real Gates (since 1.54).
            if (mazeDeck) {
                repeat(c.gateRamp) { putGate() }
                repeat(c.gateToHand) { fetchToHand(::wantedGate) }
                if (c.landToHand) fetchToHand { l -> if (board.none { it.card?.mazeEnd == true } && hand.none { it.mazeEnd }) l.mazeEnd else wantedGate(l) }
            }
            if (c.mana.kind == ManaOddsCalc.Kind.RAMP && !(mazeDeck && c.gateRamp > 0)) {
                // Every land it puts onto the battlefield, with landfall; Cultivate's other land goes to the hand (since 1.55).
                repeat(c.mana.amount) { rampLand(c.mana.colours, c.mana.tapped) }
                if (c.mana.toHand) fetchToHand { it.land && it.subtypes.any { s -> s in BASIC_TYPES } }
            }
            if (!c.permanent) {
                // Double Vision copies the first instant or sorcery each turn (since 1.54).
                val copies = if (instantsCast++ == 0 && board.any { it.card?.copiesFirst == true }) 2 else 1
                repeat(copies) {
                    each(c.drainOnce, 3)
                    one(c.oneOnce, 3)
                    if (c.xEach) each(x, 3)
                }
                draw(c.drawOnce)
                c.tutor?.takeIf { c.tutorUseful }?.let { tutor(it, c.tutorToTop) }
                if (c.tokensOnce > 0) token(c.tokensOnce, c.token)
                if (c.xTokens > 0) repeat(minOf(x, 30)) { token(c.xTokens, c.xToken) }
                makeAll(c.madeOnce)
                c.scaled.filter { !it.turn }.forEach { scaled(it) }
                if (c.overrunPerCreature) overrun += board.count { it.creature }
                // Improvisation Capstone: the cards exiled until their mana values add up; the spells among them cast for free.
                if (c.capstone > 0 && cascading <= 6) {
                    var total = 0
                    val exiled = ArrayList<WinCard>()
                    while (total < c.capstone && drawn < rest.size) rest.removeAt(drawn).let { exiled += it; total += it.manaValue }
                    cascading++
                    exiled.filter { !it.land }.forEach { cast(it).let { o -> overrun += o } }
                    cascading--
                }
                if (c.riseAndShine > 0) board.filter { it.isA("artifact") && !it.creature && !it.isA("land") }.maxByOrNull { if (it.token) 1 else 0 }?.let {
                    it.creature = true
                    it.power = 0
                    addCounters(it, c.riseAndShine)
                }
                use(c.onEnter)
                // Flashback and jump-start cards are exiled; the rest go to the graveyard. Paradigm: exiled, and a copy
                // cast every turn after (since 1.55).
                if (c.paradigm) { if (!copy && paradigms.none { it.name == c.name }) paradigms += c } else if (!fromGraveyard && !copy) graveyard += c
                return c.overrun
            }
            enter(c, cheated = free)
            return 0
        }

        /**
         * A permanent coming onto the battlefield (cast, reanimated, flickered, copied or cheated in): its mana, its
         * enter effects and the triggers it sets off. Since 1.55 (before, part of [cast]).
         */
        fun enter(c: WinCard, cheated: Boolean, token: Boolean = false, haste: Boolean = false): Entry {
            // Phyrexian Metamorph and Auton Soldier come in as a copy of the creature worth most.
            val target = if (c.entersAsCopy && !token) board.filter { it.creature && it.card != null }.maxByOrNull { worth(it.card!!) - if (it.card.legendary) 4 else 0 }?.card else null
            val card = target ?: c
            val source = when (card.mana.kind) {
                ManaOddsCalc.Kind.CREATURE -> Source(card.mana.colours, card.mana.amount, if (card.mana.haste) turn else turn + 1, creature = true)
                ManaOddsCalc.Kind.ROCK -> Source(card.mana.colours, card.mana.amount, turn)
                else -> null
            }
            source?.let { sources += it }
            val e = Entry(card, turn, card.power, card.creature, card.haste, card.double)
            e.token = token
            e.self = c
            e.cheated = cheated
            e.hasteNow = haste
            e.source = source
            board += e
            addCounters(e, card.fabricate + card.entersWithCounters)
            if (card.aura && card.auraCounter > 0) carrier()?.let { addCounters(it, card.auraCounter) }
            if (card.animateArtifacts > 0) animateNow = card.animateArtifacts
            if (card.castTriggers.isNotEmpty()) spellTriggers = true
            if (card.castTriggers.any { it.firstOnly }) firstSpellTrigger = true
            if (card.enterTriggers.any { !it.land }) creatureTriggers = true
            if (entering > 6) return e
            entering++
            if (card.creature) { entered(e, false); creatureEntered(e) }
            arrived(e)
            card.fetchKind?.takeIf { !card.fetchTurn }?.let { fetch(it) }
            val germ = card.equipsToken
            if (card.equipment && germ != null) {
                token(germ).also { it.gear += card; e.on = it }
            } else if (card.aura) {
                carrier()?.let { it.gear += card; e.on = it }
            } else if (card.equipment && board.any { it.card?.attachEntering == true }) {
                carrier()?.let { it.gear += card; e.on = it }
            }
            // Cloud: an Equipment onto it for free (the best one not already on it).
            if (card.attachOnEnter) board.filter { g -> g.card?.equipment == true && g.on !== e }.maxByOrNull { g -> g.card!!.bonus + (g.card.base ?: 0) + if (g.card.grantsDouble) 10 else 0 }?.let { g ->
                g.on?.gear?.remove(g.card)
                e.gear += g.card!!
                g.on = e
            }
            each(card.drainOnce, 3)
            one(card.oneOnce, 3)
            card.devotion?.let { colour -> each(board.sumOf { b -> b.card?.cost?.pips?.count { colour in it } ?: 0 }, 3) }
            draw(card.drawOnce)
            card.tutor?.takeIf { card.tutorUseful }?.let { tutor(it, card.tutorToTop) }
            if (card.tokensOnce > 0) token(card.tokensOnce, card.token)
            makeAll(card.madeOnce)
            card.scaled.filter { !it.turn }.forEach { scaled(it) }
            overrun += card.overrun
            if (card.overrunPerCreature) overrun += board.count { it.creature }
            use(card.onEnter, e)
            if (card.newCounters > 0) board.filter { it.creature && it.turn == turn }.forEach { addCounters(it, card.newCounters) }
            if (card.discover > 0) reveal(card.discover)
            entering--
            return e
        }

        /**
         * Cascade and discover (since 1.55): cards from the top of the library until a nonland card with mana value
         * [most] or less, which is cast for free; the cards passed go to the bottom.
         */
        fun reveal(most: Int) {
            if (cascading > 6) return
            val i = (drawn until rest.size).firstOrNull { !rest[it].land && rest[it].manaValue <= most } ?: return
            val hit = rest.removeAt(i)
            val passed = rest.subList(drawn, i)
            val bottom = passed.toList()
            passed.clear()
            rest += bottom
            cascading++
            cast(hit).let { overrun += it }
            cascading--
        }

        fun pool(): List<Pair<Set<Char>, Int>> {
            val base = sources.filter { it.from <= turn && !it.creature }.map { it.colours to it.amount }.toMutableList()
            // Five Hundred Year Diary ({U} for each Clue), Jaheira (tokens tap for {G}), Urza (artifacts tap for {U}),
            // Inspiring Statuary (artifacts pay generic mana). Since 1.55.
            if (!f.pool55) return base
            for (b in board) {
                val c = b.card ?: continue
                val (kind, colour) = c.manaPer ?: continue
                if (b.turn >= turn && c.mana.kind == ManaOddsCalc.Kind.ROCK) continue
                val n = when (kind) {
                    "token" -> board.count { it.token && !it.creature && !manaToken(it) }
                    "artifact" -> spareArtifacts()
                    else -> board.count { it.isA(kind) }
                } - (if (c.mana.kind == ManaOddsCalc.Kind.ROCK) c.mana.amount else 0)
                if (n > 0) base += setOf(colour) to n
            }
            if (board.any { it.card?.improvise == true }) spareArtifacts().takeIf { it > 0 }?.let { base += setOf('C') to it }
            return base
        }

        /** Artifacts that tap for nothing else (Clues, Food, Mutagens, equipment…). */
        fun spareArtifacts() = board.count { it.isA("artifact") && !it.creature && it.source == null && !manaToken(it) }

        fun castKind(c: WinCard, kind: String?) = when (kind) {
            null -> true
            "historic" -> c.isKind("artifact") || c.legendary || c.isKind("saga")
            "noncreature" -> !c.creature
            else -> c.isKind(kind)
        }

        /** A land from the hand or the graveyard onto the battlefield (On the Trail), with landfall. */
        fun landOnto(l: WinCard, tapped: Boolean) {
            val source = Source(l.mana.colours, 1, if (tapped && board.none { it.card?.untapsLands == 1 }) turn + 1 else turn)
            sources += source
            val e = Entry(l, turn, 0, false, false, false).also { it.source = source }
            board += e
            landEntered(e)
        }

        fun gearPower(b: Entry): Pair<Int, Boolean> {
            var base = b.power + b.counters + (b.card?.powerPer?.let { count(it) } ?: 0) + (b.per?.let { count(it) } ?: 0)
            var plus = 0
            var double = b.double
            for (g in b.gear) {
                g.base?.let { base = maxOf(base, it) }
                plus += g.bonus
                plus += when (g.forEach) {
                    ForEach.COLOURS -> own.size
                    ForEach.HAND -> hand.size
                    ForEach.ARTIFACTS_ENCHANTMENTS -> board.count { it.card?.artifactOrEnchantment == true || it.isA("artifact") && !it.creature }
                    ForEach.ENCHANTMENTS -> board.count { it.card?.enchantment == true }
                    ForEach.ONE -> 1
                    null -> 0
                }
                double = double || g.grantsDouble
            }
            return base + plus + lordBonus(b) to double
        }

        /** What the lords on the battlefield give this creature (since 1.55). */
        fun lordBonus(b: Entry): Int = if (!f.lords) 0 else board.sumOf { l ->
            l.card?.lords?.sumOf { lord ->
                val fits = lord.kind == null || lord.kind.split('|').any { k -> if (k == "token") b.token else if (k == "commander") b.card in leaders && !b.token else b.isA(k) }
                if (fits && !(lord.other && l === b)) lord.amount else 0
            } ?: 0
        }

        /** Haste from the creature, its gear, or another card ("creatures you control have haste", riot, Chainer…). */
        fun hasHaste(b: Entry) = b.haste || b.hasteNow || b.gear.any { it.grantsHaste } || f.hasteFor && board.any { h ->
            when (val k = h.card?.hasteFor) {
                null -> false
                "creature" -> true
                "nontoken" -> !b.token
                "flying" -> b.flying
                "cheated" -> b.cheated
                else -> b.isA(k)
            }
        }

        /** Plays up to [turns] turns; the turn the game is won, or 0. */
        fun play(turns: Int): Int {
            for (t in 1..turns) {
                turn = t
                spells = 0
                overrun = 0
                doubleFor.clear()
                discardsThisTurn = 0
                leaveDrawn = false
                graveyardCasts = 0
                equipFreeUsed = false
                extraCombat = false
                animateNow = 0
                newTurn()
                // A stun counter comes off instead of untapping (Baloth Prime).
                board.filter { it.stun > 0 && it.turn < t }.forEach { it.stun--; it.stunTurn = t }
                // The upkeep (since 1.55): Mechanized Production and Hellkite Tyrant win; Walking Archive; "each upkeep".
                if (f.eachUpkeep) for (m in board.filter { it.card?.mechanized == true }.toList()) {
                    val kind = board.filter { it.art != null }.groupingBy { it.art!! }.eachCount().maxByOrNull { it.value }?.key ?: continue
                    make(kind, 1)
                    if (board.count { it.art == kind } >= 8) { byCombo = true; return t }
                }
                if (f.eachUpkeep) {
                    board.mapNotNull { it.card?.artifactWin?.takeIf { w -> w > 0 } }.minOrNull()?.let { if (count("artifact") >= it) { byCombo = true; return t } }
                    board.filter { it.card?.eachUpkeepDraws == true }.sumOf { it.counters }.takeIf { it > 0 }?.let { draw(it) }
                    board.toList().forEach { b -> b.card?.let { if (it.eachUpkeep.any) use(it.eachUpkeep, b) } }
                }
                firstOfDrawStep = true
                draw(1 + board.sumOf { (it.card?.drawTurn ?: 0) + (it.card?.eachDrawStep ?: 0) })
                firstOfDrawStep = false
                board.toList().forEach { b -> b.card?.let { c -> c.scaled.filter { it.turn && it.what == Effect.DRAW }.forEach { scaled(it) } } }
                board.toList().forEach { b -> b.card?.takeIf { it.fetchTurn }?.fetchKind?.let { fetch(it) } }
                if (f.eachTurn) board.toList().forEach { b -> b.card?.let { if (it.eachTurn.any) use(it.eachTurn, b) } }
                // Paradigm (since 1.55): a free copy of each one that has resolved, at the beginning of the first main phase.
                for (c in paradigms.toList()) cast(c, copy = true).let { overrun += it }
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
                instantsCast = 0
                // With a "first spell each turn" trigger, the biggest spell goes first: an X spell with all the mana
                // when that's bigger (since 1.54).
                val bigX = if (firstSpellTrigger) xSpell(pool) else null
                val biggest = if (bigX == null) 0 else (hand + command).filter { !it.land && canSpend(pool, costOf(it)) && !it.xEach }.maxOfOrNull { it.manaValue } ?: 0
                if (bigX != null && bigX.second >= 2 && bigX.first.manaValue + bigX.second > biggest) {
                    spend(pool, costOf(bigX.first))
                    board.filter { manaToken(it) }.forEach { board.remove(it); left(it, artifact = true) }
                    cast(bigX.first, x = bigX.second)
                    pool = emptyList()
                    if (life.all { it <= 0 }) return t
                } else if (firstSpellTrigger) {
                    for (c in (hand + command).filter { !it.land && !it.xEach }.sortedByDescending { it.manaValue }) {
                        val left = spend(pool, costOf(c), c.manaValue) ?: continue
                        pool = left
                        cast(c).let { overrun += it }
                        break
                    }
                }
                for (c in hand.filter { it.mana.kind in RAMP_KINDS }.sortedBy { it.manaValue }) {
                    if (c !in hand) continue
                    val left = spend(pool, costOf(c), c.manaValue) ?: continue
                    pool = left
                    cast(c)
                }
                when (comboReady(potential(pool))) {
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
                        board.filter { manaToken(it) }.forEach { board.remove(it); left(it, artifact = true) }
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
                    val left = if (c in hand || c in command) spend(pool, costOf(c), c.manaValue) else null
                    if (left == null) {
                        i++
                        continue
                    }
                    pool = left
                    val before = hand.size
                    val triggers = spellTriggers to creatureTriggers
                    cast(c).let { overrun += it }
                    if (hand.size >= before || triggers != (spellTriggers to creatureTriggers)) {
                        options = mainOptions()
                        i = 0
                    } else {
                        i++
                    }
                }
                if (f.graveyardCasts) pool = fromGraveyard(pool)
                // What's left of the mana goes into an X spell (since 1.54), or one making tokens (since 1.55).
                for (tokens in if (f.xTokens) listOf(false, true) else listOf(false)) xSpell(pool, tokens)?.takeIf { it.second >= (if (tokens) 2 else 3) }?.let { (c, x) ->
                    board.filter { manaToken(it) }.forEach { board.remove(it); left(it, artifact = true) }
                    cast(c, x = x)
                    pool = emptyList()
                    if (life.all { it <= 0 }) return t
                }
                if (f.planeswalkers) pool = loyalty(pool)
                if (f.abilities) pool = abilities(pool)
                if (f.sneak) pool = sneakAttack(pool)
                pool = equip(pool)
                if (life.all { it <= 0 }) return t
                // The cards on the battlefield (not lands or plain tokens), looked through by what follows (since 1.55).
                val live = board.filter { it.card != null && !it.card.land }
                for (b in live) {
                    val c = b.card ?: continue
                    each(c.drainTurn, 3)
                    one(c.oneTurn, 3)
                    // Quintorius, Loremaster: its token needs such a card in the graveyard to exile.
                    val need = c.tokensNeed
                    if (c.tokensTurn > 0 && (need == null || graveyard.any { g -> g.permanent.not() || (!g.creature && !g.land) }.also { ok ->
                            if (ok) leftGraveyard(listOf(graveyard.first { g -> !g.creature && !g.land }))
                        })
                    ) token(c.tokensTurn, c.token)
                    makeAll(c.madeTurn)
                    c.scaled.filter { it.turn && it.what != Effect.DRAW }.forEach { scaled(it) }
                }
                // Crackling Perimeter: the Gates not needed for mana deal 1 damage each (since 1.54).
                if (mazeDeck && board.any { it.card?.gatePing == true }) {
                    val n = minOf(count("gate"), pool.sumOf { it.second })
                    each(n, 2)
                    pool = ManaOddsCalc.pay(pool, ManaOddsCalc.Cost(n, emptyList())) ?: pool
                }
                // Mutagens and Clues with the mana left, Shimmer Dragon's draws (since 1.55).
                if (f.mutagens) pool = useMutagens(pool)
                if (f.clues) pool = crackClues(pool)
                if (f.drawnBonus && board.any { it.card?.tapTwoDraw == true }) draw(minOf(4, spareArtifacts() / 2))
                sacrificeForValue(t)
                // Tangletrove Kelp's Clues and Cyberdrive Awakener's artifacts attack as creatures this turn.
                val kelp = if (!f.animate) 0 else board.mapNotNull { it.card?.animateClues?.takeIf { n -> n > 0 } }.maxOrNull() ?: 0
                val animated = if (!f.animate) emptyList() else board.filter { e ->
                    !e.creature && e.isA("artifact") && !e.isA("land") && e.turn < t && (kelp > 0 && e.art == "clue" || animateNow > 0 && e.source == null && !manaToken(e))
                }
                animated.forEach { it.creature = true; it.power = if (kelp > 0 && it.art == "clue") kelp else animateNow }
                val attackers = board.filter { b ->
                    b.creature && b.tapped != t && b.stun == 0 && b.stunTurn != t && (b.turn < t || hasHaste(b))
                }
                val extra = HashMap<Entry, Int>()
                val doubled = HashSet<Entry>()
                if (attackers.isNotEmpty()) {
                    val carrier = carrier()
                    val hit = carrier?.takeIf { it in attackers } ?: attackers.maxBy { it.power + it.gear.size }
                    if (f.doubles && live.any { it.card?.attackDouble == true }) doubled += hit
                    val targeted = if (f.doubles) live.sumOf { it.card?.targetedBonus ?: 0 } else 0
                    // Basilisk Gate: {2}, {T}: +X/+X, X the Gates (since 1.54).
                    if (mazeDeck && board.any { it.card?.gatePump == true }) ManaOddsCalc.pay(pool, ManaOddsCalc.Cost(3, emptyList()))?.let {
                        pool = it
                        extra[hit] = (extra[hit] ?: 0) + count("gate")
                    }
                    for (c in hand.filter { it.pumpSpell }.sortedWith(compareByDescending<WinCard> { it.pumpDouble }.thenByDescending { it.pump })) {
                        var left = spend(pool, costOf(c)) ?: continue
                        val x = if (c.pumpX) left.sumOf { it.second }.also { left = emptyList() } else 0
                        pool = left
                        cast(c)
                        extra[hit] = (extra[hit] ?: 0) + c.pump + x + targeted
                        if (c.pumpDouble) doubled += hit
                    }
                    // Pumps paid with the mana left (Lathliss, Bladewing, firebreathing), since 1.55.
                    if (f.pumps) pool = activatedPumps(pool, attackers, extra)
                    // Attack triggers (since 1.55): the attacker's own (Teval, Cloud, Archon), equipped creature's, any
                    // creature of a kind's (Utvara), and "whenever you attack" (Varina, Temmet).
                    if (f.attack55) for (b in attackers) {
                        b.card?.let { c ->
                            if (!c.onAttackEquipped) use(c.onAttack, b, attackers)
                            overrun += c.attackOverrun
                            if (c.onAttack.selfPump > 0 && (c.onAttack.needsPower == 0 || board.any { o -> o.creature && gearPower(o).first >= c.onAttack.needsPower })) {
                                extra[b] = (extra[b] ?: 0) + c.onAttack.selfPump
                            }
                        }
                        b.gear.filter { it.onAttackEquipped }.forEach { g -> use(g.onAttack, b, attackers) }
                    }
                    if (f.attack55) for (s in live) s.card?.let { c ->
                        if (c.onYouAttack.any && (c.onYouAttack.perKind == null || attackers.any { a -> a.isA(c.onYouAttack.perKind!!) })) use(c.onYouAttack, s, attackers)
                        c.kindAttack?.let { k -> attackers.filter { it.isA(k) }.forEach { _ -> use(c.onKindAttack, s, attackers) } }
                    }
                }
                // Tokens that came in tapped and attacking (Endless Foot Assault) fight too; Proft's Eidetic Memory's
                // counters, Knowledge Is Power, Raphael (since 1.55).
                val fighters = attackers + board.filter { it.joins == t && it !in attackers }
                if (f.drawnBonus && fighters.isNotEmpty() && drawnThisTurn > 1) board.filter { it.card?.combatCountersDrawn == true }.forEach { _ -> bestForCounters()?.let { addCounters(it, drawnThisTurn - 1) } }
                val drawnBonus = if (f.drawnBonus) live.count { it.card?.drawnAnthem == true } * drawnThisTurn else 0
                val allDouble = f.doubles && live.any { it.card?.attackersDouble == true }
                val counteredDouble = f.doubles && live.any { it.card?.doubleCountered == true }
                // Each attacker's damage, kept apart: a creature attacks one player (since 1.56).
                val hits = ArrayList<Int>()
                // Sage of the Maze: a land becomes a 2X/2X with haste, X the Gates (since 1.54).
                live.firstNotNullOfOrNull { b -> b.card?.animates?.takeIf { b.turn < t && b.tapped != t } }?.let { hits += count(it.per) * it.factor }
                var draws = 0
                val fromCommanders = IntArray(leaders.size)
                // Haunted One: a commander attacking gives it and the creatures sharing a type with it +2/+0 (since 1.55).
                val background = if (f.doubles) live.sumOf { it.card?.background ?: 0 } else 0
                val pumpedTypes = if (background > 0) attackers.filter { it.card in leaders }.flatMap { it.subtypes }.toSet() else emptySet()
                val myriadKinds = if (f.myriadFor) live.mapNotNull { it.card?.myriadFor } else emptyList()
                for (b in fighters) {
                    val (power, double) = gearPower(b)
                    val bg = if (pumpedTypes.isNotEmpty() && b.subtypes.any { it in pumpedTypes }) background else 0
                    val ds = double || allDouble || b in doubled || doubleFor.any { k -> k == "creature" || b.isA(k) }
                    val once = (power + overrun + bg + drawnBonus + (extra[b] ?: 0)).coerceAtLeast(0) * (if (ds) 2 else 1) *
                        (if (counteredDouble && b.counters > 0) 2 else 1)
                    val p = once * (if (extraCombat) 2 else 1)
                    val myriad = b.card?.myriad == true || myriadKinds.any { b.isA(it) && b.card?.legendary != true }
                    val k = b.card?.let { leaders.indexOf(it) }?.takeIf { !b.token } ?: -1
                    when {
                        k >= 0 -> fromCommanders[k] += p
                        // Myriad: it and its copies attack each opponent once.
                        myriad -> life.indices.filter { life[it] > 0 }.forEach { i -> life[i] -= p; dealt[0] += p.toLong() }
                        // An extra combat: it attacks again, maybe someone else.
                        extraCombat -> { hits += once; hits += once }
                        else -> hits += p
                    }
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
                // Until 1.56 all of it was added up and spread over the opponents, so one huge creature (Jumbo Cactuar)
                // knocked out all three. Now the biggest go first, each onto the opponent with the most life it finishes,
                // or else the one with the least life.
                for (p in hits.sortedDescending()) {
                    if (p <= 0) break
                    val open = life.indices.filter { life[it] > 0 }
                    if (open.isEmpty()) break
                    val i = open.filter { life[it] <= p }.maxByOrNull { life[it] } ?: open.minBy { life[it] }
                    life[i] -= p
                    dealt[0] += p.toLong()
                }
                animated.forEach { it.creature = false; it.power = 0 }
                if (life.all { it <= 0 }) return t
                // "Whenever one or more creatures you control deal combat damage to a player" (Professional Face-Breaker,
                // Ongoing Investigation, Heroes in a Half Shell), Ray Fillet (since 1.55).
                if (f.afterCombat && fighters.isNotEmpty()) for (s in live) s.card?.let { c ->
                    if (c.onAnyHit.any) {
                        val hitters = fighters.filter { f -> c.onAnyHit.hitKinds?.split('|')?.any { f.isA(it) } ?: true }
                        if (hitters.isNotEmpty()) {
                            use(c.onAnyHit, s, fighters)
                            if (c.onAnyHit.countersHit > 0) hitters.filter { it in board }.forEach { addCounters(it, c.onAnyHit.countersHit) }
                        }
                    }
                    if (c.counteredHitDraw) draws += fighters.count { it.counters > 0 }
                }
                draw(minOf(draws, MAX_COMBAT_DRAW))
                // After combat: what attackers do on dealing combat damage (Archpriest of Shadows), the second main phase
                // (Michelangelo), then Sneak Attack's creatures go and end-step draws (since 1.55).
                if (f.afterCombat) {
                    for (b in attackers) b.card?.let { c -> if (c.onHit.any) use(c.onHit, b, attackers) }
                    for (b in live) b.card?.let { c -> if (c.postCombat.any && (!c.postCombatNeedsAttack || fighters.isNotEmpty())) use(c.postCombat, b) }
                    board.filter { it.sacrificeAtEnd }.forEach { e -> board.remove(e); e.source?.let { sources.remove(it) }; left(e, artifact = "Artifact" in e.types) }
                    if (tokensThisTurn) draw(live.count { it.card?.endDrawIfToken == true })
                    if (live.any { it.card?.monarch == true }) draw(1)
                }
                if (life.all { it <= 0 }) return t
                // The opponents' turns: their draw steps with Howling Mine and the like, and what that sets off.
                if (t < turns) opponentTurns()
                if (life.all { it <= 0 }) return t
            }
            return 0
        }

        /** Mutagens: {1}, {T}, sacrifice: a +1/+1 counter on the creature that attacks best (Mutagen Man: free). Since 1.55. */
        fun useMutagens(start: List<Pair<Set<Char>, Int>>): List<Pair<Set<Char>, Int>> {
            var pool = start
            val free = board.any { it.card?.text?.contains("activated abilities of artifact tokens you control cost {1} less") == true }
            for (e in board.filter { it.art == "mutagen" }.toList()) {
                val target = bestForCounters() ?: break
                if (!free) pool = ManaOddsCalc.pay(pool, ManaOddsCalc.Cost(1, emptyList())) ?: break
                board.remove(e)
                left(e, artifact = true)
                addCounters(target, 1)
            }
            return pool
        }

        /**
         * Clues: {2}, sacrifice: draw a card, with the mana left (not with Tangletrove Kelp or Mechanized Production, which
         * want them). Tireless Tracker, Ulvenwald Mysteries and the like. Since 1.55.
         */
        fun crackClues(start: List<Pair<Set<Char>, Int>>): List<Pair<Set<Char>, Int>> {
            var pool = start
            if (board.any { (it.card?.animateClues ?: 0) > 0 || it.card?.mechanized == true }) return pool
            for (e in board.filter { it.art == "clue" }.toList()) {
                pool = ManaOddsCalc.pay(pool, ManaOddsCalc.Cost(2, emptyList())) ?: break
                board.remove(e)
                left(e, artifact = true)
                for (b in board.toList()) b.card?.let { c -> if (c.onClueSac.any) use(c.onClueSac, b) }
                draw(1)
            }
            return pool
        }

        /** Casting creatures from the graveyard (Rivaz, Chainer, Gravecrawler) and flashback, with the mana left. Since 1.55. */
        fun fromGraveyard(start: List<Pair<Set<Char>, Int>>): List<Pair<Set<Char>, Int>> {
            var pool = start
            val allowed = board.mapNotNull { it.card?.castFromGraveyard }
            for (c in graveyard.sortedByDescending { worth(it) + it.manaValue }.toList()) {
                val viaPermission = c.creature && graveyardCasts < allowed.size && allowed.any { k -> k == "creature" || c.isKind(k) }
                val self = c.selfFromGraveyard?.let { k -> board.any { it.isA(k) } } == true
                val flash = c.flashbackCost
                val cost = when {
                    viaPermission || self -> costOf(c)
                    flash != null && ready(c) && useful(c, spellTriggers, creatureTriggers, fodder) -> flash
                    else -> continue
                }
                if (!useful(c, spellTriggers, creatureTriggers, fodder) && !self) continue
                val left = spend(pool, cost, c.manaValue) ?: continue
                pool = left
                if (viaPermission) graveyardCasts++
                if (flash != null && !viaPermission && !self && c.flashbackCost === c.cost) discard(1)
                cast(c, fromGraveyard = true)
                if (c.permanent || viaPermission || self) Unit
            }
            return pool
        }

        /** Each planeswalker uses one loyalty ability: a − one when it pays off now, else the + one (since 1.55). */
        fun loyalty(pool: List<Pair<Set<Char>, Int>>): List<Pair<Set<Char>, Int>> {
            for (pw in board.filter { it.card?.planeswalker == true && it.card.loyalties.isNotEmpty() }.toList()) {
                val options = pw.card!!.loyalties.filter { (cost, a) -> pw.loyalty + cost >= 0 && abilityReady(a, pw) }
                val plus = options.filter { it.first >= 0 }.maxByOrNull { value(it.second) }
                val minus = options.filter { it.first < 0 && (pw.loyalty + it.first > 0 || it.second.doubleStrikeFor != null) }.maxByOrNull { value(it.second) }
                val pick = if (minus != null && (plus == null || value(minus.second) >= value(plus.second) + 3)) minus else plus ?: continue
                pw.loyalty += pick.first
                use(pick.second, pw)
                if (pw.loyalty <= 0) { board.remove(pw); graveyard += pw.card }
            }
            return pool
        }

        /** Whether an ability can do something now. */
        fun abilityReady(a: Ability, user: Entry): Boolean = when {
            a.polymorph > 0 -> board.any { it.creature && it.token } || a.polymorph != ALL && board.any { it.creature && it !== user && it.card !in leaders && it.power <= 2 }
            a.reanimate > 0 -> if (a.reanimateKind == "land") graveyard.any { it.land } else reanimationWorth(a) >= 4
            a.copyExiled -> board.count { it.token && "Artifact" in it.types } >= 2 && graveyard.any { it.creature || "Artifact" in it.types }
            a.copyArtifactToken -> board.any { it.token && "Artifact" in it.types }
            a.doubleStrikeFor != null -> board.filter { it.creature && it.isA(a.doubleStrikeFor!!) && (it.turn < turn || hasHaste(it)) }.sumOf { gearPower(it).first } >= 6
            a.exileGraveyard > 0 && !a.copyExiled -> graveyard.size >= a.exileGraveyard && (board.any { it.card?.leaveToken != null || it.card?.leaveDraw == true } || a.tokens > 0)
            a.sacrificeForToken > 0 -> board.any { it.creature && it.card !in leaders && it.power < a.sacrificeForToken }
            a.flicker > 0 -> board.any { it.creature && !it.token && it.card?.let { c -> enterWorth(c) > 0 } == true }
            a.copies > 0 -> board.any { it.creature }
            else -> a.any
        }

        /** Roughly what an ability is worth (to pick one). */
        fun value(a: Ability): Int = 2 * a.draw + 3 * a.drainEach + a.drainOne + a.tokens * maxOf(1, a.token?.power ?: 1) + a.treasures + a.artifacts +
            a.lootDraw + a.mill / 2 + (if (a.reanimate > 0) reanimationWorth(a) else 0) + a.toHand * 2 +
            (if (a.copyExiled) graveyard.filter { it.creature || "Artifact" in it.types }.maxOfOrNull { worth(it) } ?: 0 else 0) +
            (if (a.copyArtifactToken) 2 else 0) + (if (a.polymorph > 0) 6 else 0) + a.copies * 4 + a.flicker * 3 +
            (if (a.doubleStrikeFor != null) board.filter { it.creature && it.isA(a.doubleStrikeFor!!) }.sumOf { gearPower(it).first } else 0) +
            (if (a.sacrificeForToken > 0) a.sacrificeForToken - 1 else 0) + a.exileGraveyard * (if (board.any { it.card?.leaveToken != null }) 2 else 0)

        /**
         * Tap abilities and abilities paid with mana (since 1.55): each permanent uses its best one when it pays; a
         * creature that does doesn't attack (unless the ability is worth less than its power). Abilities without {T}
         * (Varina's Zombies) go again while the mana lasts.
         */
        fun abilities(start: List<Pair<Set<Char>, Int>>): List<Pair<Set<Char>, Int>> {
            var pool = start
            val hasteAbilities = board.any { it.card?.abilityHaste == true }
            for (b in board.toList()) {
                val c = b.card ?: continue
                if (b !in board || c.taps.isEmpty() || b.tapped == turn) continue
                if (b.creature && b.turn >= turn && !hasHaste(b) && !hasteAbilities) continue
                val attack = if (b.creature) gearPower(b).first else 0
                val pick = c.taps.filter { abilityReady(it, b) && (it.discardCost == 0 || spareCard() != null) && canSpend(pool, it.cost) }.maxByOrNull { value(it) } ?: continue
                if (b.creature && attack > 2 && value(pick) < attack) continue
                pool = spend(pool, pick.cost) ?: continue
                if (pick.discardCost > 0) discard(pick.discardCost)
                b.tapped = turn
                use(pick, b)
            }
            for (b in board.toList()) {
                val c = b.card ?: continue
                for (a in c.activated) repeat(3) {
                    if (!abilityReady(a, b) || a.discardCost > 0 && spareCard() == null) return@repeat
                    if (a.exileGraveyard > 0 && graveyard.size < a.exileGraveyard) return@repeat
                    pool = spend(pool, a.cost) ?: return@repeat
                    if (a.discardCost > 0) discard(a.discardCost)
                    if (a.exileGraveyard > 0) leftGraveyard(graveyard.sortedBy { if (it.creature) worth(it) + 10 else 0 }.take(a.exileGraveyard))
                    val copy = Ability().also { x -> x.tokens = a.tokens; x.token = a.token; x.draw = a.draw; x.drainEach = a.drainEach; x.drainOne = a.drainOne; x.treasures = a.treasures; x.artifacts = a.artifacts }
                    use(if (a.exileGraveyard > 0) copy else a, b)
                }
            }
            return pool
        }

        /** Sneak Attack: a creature from the hand that couldn't be cast comes in with haste and goes at the end of the turn. */
        fun sneakAttack(start: List<Pair<Set<Char>, Int>>): List<Pair<Set<Char>, Int>> {
            var pool = start
            val cost = board.firstNotNullOfOrNull { it.card?.sneakCost } ?: return pool
            for (c in hand.filter { it.creature && (it.power >= 3 || enterWorth(it) >= 3) }.sortedByDescending { worth(it) }) {
                pool = spend(pool, cost) ?: break
                hand.remove(c)
                enter(c, cheated = true, haste = true).sacrificeAtEnd = true
            }
            return pool
        }

        /** Pumps paid with the mana left, the most damage per mana first (since 1.55). */
        fun activatedPumps(start: List<Pair<Set<Char>, Int>>, attackers: List<Entry>, extra: HashMap<Entry, Int>): List<Pair<Set<Char>, Int>> {
            var pool = start
            val pumps = board.flatMap { b -> b.card?.pumps.orEmpty().map { b to it } }
            if (pumps.isEmpty()) return pool
            repeat(20) {
                val best = pumps.map { (b, p) ->
                    val gain = if (p.kind == null) (if (b in attackers) p.amount else 0) else p.amount * attackers.count { it.isA(p.kind) }
                    Triple(b, p, gain)
                }.filter { it.third > 0 && canSpend(pool, it.second.cost) }.maxByOrNull { it.third * 10 / maxOf(1, it.second.cost.manaValue) } ?: return pool
                pool = spend(pool, best.second.cost) ?: return pool
                val (b, p, _) = best
                if (p.kind == null) extra[b] = (extra[b] ?: 0) + p.amount
                else attackers.filter { it.isA(p.kind) }.forEach { extra[it] = (extra[it] ?: 0) + p.amount }
            }
            return pool
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
                e.token && !e.creature && LAND_TYPE !in e.types -> 0
                e.token && e.creature -> 1 + (e.power + e.counters) / 2
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

        /** A sacrificed permanent: what the deck's cards do about it (since 1.54); its card goes to the graveyard (1.55). */
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
                if (c.sacCounters) board.filter { it.creature }.forEach { addCounters(it, 1) }
                if (e.token) each(c.tokenDrain, 3)
                if ((e.creature || artifact) && b !== e) each(c.dieDrain, 3)
            }
            e.card?.leavesToken?.takeIf { it > 0 }?.let { token(it) }
            if (!e.token) e.self?.let { if (it in leaders) command += it else graveyard += it }
        }

        fun playLand() {
            // An untapped land first, then the one adding the most missing colours (as for the commander odds).
            val have = HashSet<Char>()
            sources.forEach { have += it.colours }
            val lands = hand.filter { it.land }
            val names = if (mazeDeck) gateNames() else emptySet()
            // A Maze's End deck plays Maze's End, then Gates with new names (since 1.54). With Crucible of Worlds a land
            // can come from the graveyard (since 1.55).
            val fromYard = if (lands.isEmpty() && board.any { it.card?.crucible == true }) graveyard.firstOrNull { it.land } else null
            val land = (if (mazeDeck) lands.firstOrNull { it.mazeEnd } ?: lands.firstOrNull { "Gate" in it.subtypes && it.name !in names } else null)
                ?: lands.minByOrNull { l -> (if (l.mana.tapped) 10 else 0) - l.mana.colours.count { it !in have } } ?: fromYard ?: return
            if (land === fromYard) leftGraveyard(listOf(land)) else hand.remove(land)
            val source = Source(land.mana.colours, 1, if (untapped(land)) turn else turn + 1)
            sources += source
            val e = Entry(land, turn, 0, false, false, false).also { it.source = source }
            board += e
            makeAll(land.madeOnce)
            landEntered(e)
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
                for (c in cast.sortedByDescending { it.manaValue }) p = p?.let { ManaOddsCalc.pay(it, costOf(c)) }
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

        /**
         * Moves equipment onto the carrier, the biggest bonus first, while the mana lasts. Since 1.55 with Puresteel
         * Paladin's equip {0}, Bladehold War-Whip's {1} less and Forge Anew's first equip for {0}.
         */
        fun equip(start: List<Pair<Set<Char>, Int>>): List<Pair<Set<Char>, Int>> {
            var pool = start
            val target = carrier() ?: return pool
            val loose = board.filter { e -> e.card?.let { it.equipment && it.gearBonus && it.equip != null } == true && e.on !== target }
                .sortedByDescending { e -> e.card!!.let { it.bonus + (if (it.grantsDouble) 10 else 0) + (it.base ?: 0) } }
            val zero = board.any { it.card?.equipZero == true } && board.count { it.isA("artifact") } >= 3
            for (e in loose) {
                val card = e.card ?: continue
                var cost = card.equip ?: continue
                cost -= board.filter { it !== e }.sumOf { it.card?.equipLess ?: 0 }
                if (zero) cost = 0
                if (cost > 0 && !equipFreeUsed && board.any { it.card?.firstEquipFree == true }) { cost = 0; equipFreeUsed = true }
                if (cost > 0) pool = spend(pool, ManaOddsCalc.Cost(cost, emptyList())) ?: continue
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
            val BASIC_TYPES = setOf("Plains", "Island", "Swamp", "Mountain", "Forest")
            val COLOUR_WORDS = mapOf("white" to 'W', "blue" to 'U', "black" to 'B', "red" to 'R', "green" to 'G')
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
