package com.mtgtrader.data

import kotlin.random.Random

/** The chance (in whole percent) a commander can be cast by each turn, turn 1 first. Since 1.46. */
data class CastOdds(val name: String, val manaCost: String, val manaValue: Int, val byTurn: List<Int>)

/**
 * When the deck can cast its commander and has its colours (since 1.46), from [games] shuffles of the deck.
 * [colours] is each colour of the deck's identity with the chance it's there by each turn; [allColours] the
 * same for all of them together (null for decks of one colour or none).
 */
data class ManaOdds(
    val games: Int,
    val commanders: List<CastOdds>,
    val colours: List<Pair<Char, List<Int>>>,
    val allColours: List<Int>?,
    val lands: Int,
    val rocks: Int,
    val creatures: Int,
    val ramp: Int,
) {
    val turns get() = commanders.firstOrNull()?.byTurn?.size ?: colours.firstOrNull()?.second?.size ?: 0
}

/**
 * Plays a deck alone many times ("goldfishing") to see when it can cast its commander and has its colours,
 * the way mana bases are judged (Frank Karsten's method, with the deck's own cards instead of a table):
 * - the 99 are shuffled and a hand is kept the way Karsten models Commander: a free mulligan, then the
 *   London mulligan, keeping hands of two to five lands and bottoming towards three lands;
 * - each turn a card is drawn (also on turn 1, as in multiplayer games), a land is played, and mana rocks,
 *   mana creatures and land ramp spells (Cultivate, Farseek…) costing up to 4 are cast when they can be;
 * - the land played adds a missing colour first ({C} isn't one, since 1.71); since 1.71 a land that enters
 *   tapped goes first when an untapped one's mana wouldn't be used this turn (turn 1, mostly);
 * - lands that enter tapped come in tapped; since 1.71 "unless …" lands check their condition (slow, fast,
 *   check and battle lands, Game Trail's reveal), lands that cost life come in untapped, and other conditions
 *   count as met; mana creatures can tap from the next turn (at once with haste), and ramp spells' lands come
 *   in tapped when the card says so;
 *   ramp spells count every land they put onto the battlefield (two for Explosive Vegetation), and Cultivate's
 *   second land goes to the hand (since 1.55);
 * - lands make what they tap for (since 1.69): Lotus Field three mana (once two lands are there to sacrifice to it), the
 *   depletion lands two for two turns, Ancient Tomb two; lands with a condition on their mana one.
 * Nothing else is counted: no opponents, no removal, no Treasure, rituals or cost reducers. It's a best case
 * to compare decks and changes by, not a promise.
 */
object ManaOddsCalc {
    const val GAMES = 10_000
    const val TURNS = 7

    private const val COLOURS = "WUBRG"
    private val BASIC_TYPES = mapOf("plains" to 'W', "island" to 'U', "swamp" to 'B', "mountain" to 'R', "forest" to 'G')
    private val symbol = Regex("\\{([^}]+)\\}")
    // At the start of a line, so a Treasure's reminder text ("{T}, Sacrifice this token: Add one mana") doesn't count (since 1.55).
    private val tapAdd = Regex("^(?:\\{[^}]+\\}, )?\\{T\\}[^:.]*: Add", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val rampSearch = Regex(
        "search your library for (up to )?(a|an|one|two)[^.]*(land|plains|island|swamp|mountain|forest) cards?[^.]*onto the battlefield",
        RegexOption.IGNORE_CASE,
    )
    private val tapAddLine = Regex("^\\{T\\}: Add (.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val manaSymbol = Regex("\\{[WUBRGC]\\}")
    private val hasteLine = Regex("^haste\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    // A land's own mana abilities, also with a cost besides {T} ("{T}, Remove a depletion counter…"); since 1.69.
    private val landTapAddLine = Regex("^\\{T\\}(?:, [^:]*)?: Add (.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val depletion = Regex("enters tapped with (\\w+) depletion counters", RegexOption.IGNORE_CASE)
    private val sacrificeLands = Regex("when this land enters, sacrifice (\\w+) (?:untapped )?lands", RegexOption.IGNORE_CASE)
    private val conditional = Regex("activate only if|spend this mana only", RegexOption.IGNORE_CASE)
    // When a land that enters tapped comes in untapped (since 1.71), read from its lowercased text.
    private const val BASICS = "plains|island|swamp|mountain|forest"
    private val slowLand = Regex("enters (?:the battlefield )?tapped unless you control (\\w+) or more other lands")
    private val fastLand = Regex("enters (?:the battlefield )?tapped unless you control (\\w+) or fewer other lands")
    private val bugbear = Regex("if you control (\\w+) or more other lands, this land enters (?:the battlefield )?tapped")
    private val battleLand = Regex("enters (?:the battlefield )?tapped unless you control (\\w+) or more basic lands")
    private val checkLand = Regex("enters (?:the battlefield )?tapped unless you control an? ($BASICS)(?:,? or (?:an? )?($BASICS))?\\b")
    private val revealLand = Regex("you may reveal an? ($BASICS)(?: or (?:an? )?($BASICS))? card from your hand")
    private val payLife = Regex("you may pay \\d+ life\\. if you don't, it enters (?:the battlefield )?tapped")

    /** The most mana one "{T}: Add …" ability makes: {C}{C} or {R}{R} is 2, "two mana in any combination" 2. Since 1.55. */
    private fun manaAmount(text: String, line: Regex = tapAddLine): Int = line.findAll(text).maxOfOrNull { m ->
        // A land's mana with a condition (Temple of the False God, Eldrazi Temple) is taken as one.
        if (line === landTapAddLine && conditional.containsMatchIn(m.groupValues[1])) return@maxOfOrNull 1
        val what = m.groupValues[1].substringBefore('.').substringBefore(" or ")
        when {
            what.startsWith("two mana", true) -> 2
            what.startsWith("three mana", true) -> 3
            // Only symbols written together: "{C}{C}" is two mana, "{B}, {R}, or {G}" one of three.
            else -> Regex("^(?:\\{[WUBRGC]\\})+").find(what)?.value?.let { v -> manaSymbol.findAll(v).count() } ?: 1
        }
    }?.coerceAtLeast(1) ?: 1

    /** A mana cost as generic mana and coloured pips, each pip the colours that can pay it. */
    data class Cost(val generic: Int, val pips: List<Set<Char>>) {
        val manaValue get() = generic + pips.size
    }

    /**
     * Reads "{2}{B}{G/U}{2/W}{B/P}{X}": hybrids can be paid with either colour, {2/W} with its colour, Phyrexian
     * mana is left out (life pays it), X counts as 0 and snow as generic.
     */
    fun cost(text: String?): Cost {
        var generic = 0
        val pips = mutableListOf<Set<Char>>()
        for (m in symbol.findAll(text.orEmpty().substringBefore(" // "))) {
            val s = m.groupValues[1].uppercase()
            when {
                s.toIntOrNull() != null -> generic += s.toInt()
                s.length == 1 && (s[0] in COLOURS || s[0] == 'C') -> pips += setOf(s[0])
                s == "S" -> generic += 1
                '/' in s -> {
                    val parts = s.split('/')
                    if ("P" in parts) continue
                    val colours = parts.mapNotNull { p -> p.singleOrNull()?.takeIf { it in COLOURS || it == 'C' } }.toSet()
                    if (colours.isNotEmpty()) pips += colours
                }
            }
        }
        return Cost(generic, pips)
    }

    internal enum class Kind { LAND, ROCK, CREATURE, RAMP, SPELL }

    /**
     * What a card does for the mana: also used by the win turn ([WinOddsCalc], since 1.52). [amount]: the mana it
     * makes, or the lands a ramp spell puts onto the battlefield; [toHand]: a ramp spell also puts a land into the hand;
     * [haste]: a mana creature that can tap the turn it comes in (since 1.55). Since 1.69 a land can make more than one
     * mana (Lotus Field, the depletion lands); [uses]: the turns a land lasts (two depletion counters: 2; 0 for ever);
     * [sacrificesLands]: the lands it sacrifices when it enters (Lotus Field). Since 1.71 a land that enters [tapped]
     * comes in untapped when [untap] is met; [types] are its land types (lowercase), [basic] whether it's a basic land.
     */
    internal class Card(
        val kind: Kind,
        val colours: Set<Char>,
        val tapped: Boolean,
        val cost: Cost,
        val amount: Int,
        val toHand: Boolean = false,
        val haste: Boolean = false,
        val uses: Int = 0,
        val sacrificesLands: Int = 0,
        val untap: Untap? = null,
        val types: Set<String> = emptySet(),
        val basic: Boolean = false,
    )

    /**
     * When a land that enters tapped comes in untapped (since 1.71): with [min] to [max] other lands (slow and fast
     * lands, Den of the Bugbear), [basics] basic lands (battle lands), or a land of one of [types] on the battlefield
     * (check lands) or, with [reveal], in the hand (Game Trail).
     */
    internal data class Untap(val min: Int = 0, val max: Int = Int.MAX_VALUE, val basics: Int = 0, val types: Set<String> = emptySet(), val reveal: Boolean = false)

    /** The lands on the battlefield as an "enters tapped unless …" land sees them. Since 1.71. */
    internal class LandsOut(val count: Int, val basics: Int, val types: Set<String>)

    /** The basic land types a land of these colours has (a ramp spell's basics). */
    internal fun basicTypes(colours: Set<Char>) = BASIC_TYPES.filterValues { it in colours }.keys

    /** A mana source on the battlefield: what it makes, how much, and from which turn (until which, since 1.69). */
    private class Source(
        val colours: Set<Char>, val amount: Int, val from: Int, var until: Int = Int.MAX_VALUE, val land: Boolean = false,
        val types: Set<String> = emptySet(), val basic: Boolean = false,
    )

    fun calculate(cards: List<StatsCard>, identity: String, games: Int = GAMES, turns: Int = TURNS, random: Random = Random(46)): ManaOdds? {
        val commanders = cards.filter { it.commander }
        val library = cards.filterNot { it.commander }
        if (library.sumOf { it.quantity } < 8) return null
        val own = identity.filter { it in COLOURS }.toSet()
        val lands = library.filter { it.isLand }
        val deck = ArrayList<Card>()
        for (c in library) {
            val card = classify(c, own, lands)
            repeat(c.quantity.coerceAtLeast(0)) { deck += card }
        }
        val goals = commanders.map { it to cost(it.manaCost) }
        val castBy = goals.map { IntArray(turns + 1) }
        val colourBy = own.associateWith { IntArray(turns + 1) }
        val allBy = IntArray(turns + 1)
        repeat(games) {
            val (hand, rest) = keepHand(deck, random) { it }
            val sources = ArrayList<Source>()
            val castTurn = IntArray(goals.size)
            val seen = HashSet<Char>()
            var draw = 0
            for (turn in 1..turns) {
                if (draw < rest.size) hand += rest[draw++]
                playLand(hand, sources, turn, goals.filterIndexed { i, _ -> castTurn[i] == 0 }.map { it.second })
                val ready = sources.filter { it.from <= turn && turn <= it.until }
                ready.forEach { seen += it.colours }
                for (colour in own) if (colour in seen) colourBy.getValue(colour)[turn]++
                if (own.isNotEmpty() && seen.containsAll(own)) allBy[turn]++
                goals.forEachIndexed { i, (_, c) ->
                    if (castTurn[i] == 0 && canPay(ready.map { it.colours to it.amount }, c)) castTurn[i] = turn
                    if (castTurn[i] != 0) castBy[i][turn]++
                }
                castRamp(hand, sources, ready, turn, own)
            }
        }
        fun percent(counts: IntArray) = (1..turns).map { t ->
            val p = Math.round(100.0 * counts[t] / games).toInt()
            if (p == 100 && counts[t] < games) 99 else p
        }
        return ManaOdds(
            games = games,
            commanders = goals.mapIndexed { i, (c, cost) -> CastOdds(c.name, c.manaCost.orEmpty(), cost.manaValue, percent(castBy[i])) },
            colours = COLOURS.filter { it in own }.map { it to percent(colourBy.getValue(it)) },
            allColours = if (own.size > 1) percent(allBy) else null,
            lands = deck.count { it.kind == Kind.LAND },
            rocks = deck.count { it.kind == Kind.ROCK },
            creatures = deck.count { it.kind == Kind.CREATURE },
            ramp = deck.count { it.kind == Kind.RAMP },
        )
    }

    internal fun classify(c: StatsCard, own: Set<Char>, lands: List<StatsCard>): Card {
        val text = c.oracleText.orEmpty()
        if (c.isLand) {
            val faces = c.landFaces.joinToString("\n") { it.second }.lowercase()
            var colours = c.producedMana.orEmpty().filter { it in own || it == 'C' }.toSet()
            val fetch = colours.isEmpty() && "search your library" in faces
            if (fetch) colours = DeckStats.fetches(c, lands).keys.filter { it in own }.toSet()
            val untap = if (fetch) null else untapCondition(faces)
            // "Unless …" conditions that aren't read count as met (Spire Garden's two opponents); lands that cost life
            // (shock lands) come in untapped.
            val tapped = if (fetch) "tapped" in faces
            else ("enters tapped" in faces || "enters the battlefield tapped" in faces) && ("unless" !in faces || untap != null) &&
                !payLife.containsMatchIn(faces)
            val own0 = c.landFaces.firstOrNull()?.second.orEmpty()
            return Card(
                Kind.LAND, colours.ifEmpty { setOf('C') }, tapped, Cost(0, emptyList()), manaAmount(own0, landTapAddLine),
                uses = depletion.find(own0)?.let { WinOddsCalc.number(it.groupValues[1]) } ?: 0,
                sacrificesLands = sacrificeLands.find(own0)?.let { WinOddsCalc.number(it.groupValues[1]) } ?: 0,
                untap = untap, types = c.landTypes.map { it.lowercase() }.toSet(), basic = c.isBasic,
            )
        }
        // The face it's cast as (since 1.63: a battle's creature back, Invasion of Zendikar's, isn't a mana creature).
        val type = (c.typeLine?.takeIf { it.isNotEmpty() } ?: c.types).substringBefore(" // ")
        val face = text.split(CardInfo.FACE_BREAK).first()
        val produced = c.producedMana.orEmpty().filter { it in own || it == 'C' }.toSet()
        if (produced.isNotEmpty() && c.cmc <= 4 && tapAdd.containsMatchIn(face) && ("Artifact" in type || "Creature" in type)) {
            val creature = "Creature" in type
            val amount = if (Regex("Add \\{C\\}\\{C\\}").containsMatchIn(face)) 2 else manaAmount(face)
            return Card(if (creature) Kind.CREATURE else Kind.ROCK, produced, false, cost(c.manaCost), amount, haste = creature && hasteLine.containsMatchIn(face))
        }
        // A Siege's lands when it enters count as a ramp spell's (since 1.63).
        if (c.cmc <= 4 && ("Sorcery" in type || "Instant" in type || "Battle" in type) && rampSearch.containsMatchIn(face)) {
            val what = rampSearch.find(face)!!.value.lowercase()
            val named = BASIC_TYPES.filterKeys { it in what }.values.filter { it in own }.toSet()
            val colours = named.ifEmpty { own }.ifEmpty { setOf('C') }
            // "Put one onto the battlefield tapped and the other into your hand" (Cultivate); else up to two lands.
            val split = "put one onto the battlefield" in what
            val lands = if (!split && rampSearch.find(face)!!.groupValues[2].lowercase() == "two") 2 else 1
            return Card(Kind.RAMP, colours, "tapped" in what || "tapped" in face.lowercase(), cost(c.manaCost), lands, toHand = split)
        }
        return Card(Kind.SPELL, emptySet(), false, Cost(0, emptyList()), 0)
    }

    /** When a land that enters tapped comes in untapped, from its lowercased text; null when it doesn't say. Since 1.71. */
    internal fun untapCondition(text: String): Untap? {
        fun n(m: MatchResult) = WinOddsCalc.number(m.groupValues[1])
        fun types(m: MatchResult) = m.groupValues.drop(1).filter { it.isNotEmpty() }.toSet()
        slowLand.find(text)?.let { return Untap(min = n(it)) }
        fastLand.find(text)?.let { return Untap(max = n(it)) }
        bugbear.find(text)?.let { return Untap(max = n(it) - 1) }
        battleLand.find(text)?.let { return Untap(basics = n(it)) }
        checkLand.find(text)?.let { return Untap(types = types(it)) }
        revealLand.find(text)?.let { return Untap(types = types(it), reveal = true) }
        return null
    }

    /** Whether [land] comes in untapped now, with [out] on the battlefield and [hand] (without it) to reveal from. Since 1.71. */
    internal fun entersUntapped(land: Card, out: LandsOut, hand: List<Card>): Boolean {
        if (!land.tapped) return true
        val u = land.untap ?: return false
        if (out.count < u.min || out.count > u.max || out.basics < u.basics) return false
        if (u.types.isEmpty()) return true
        return if (u.reveal) hand.any { it.kind == Kind.LAND && it.types.any { t -> t in u.types } } else out.types.any { it in u.types }
    }

    /** The mana a turn spends from [pool] on [costs], dearest first: whether a land's mana would be used now. Since 1.71. */
    internal fun spend(pool: List<Pair<Set<Char>, Int>>, costs: List<Cost>): Int {
        var left = pool
        var spent = 0
        for (c in costs.sortedByDescending { it.manaValue }) {
            left = pay(left, c) ?: continue
            spent += c.manaValue
        }
        return spent
    }

    /**
     * The land to play from [lands] (since 1.71): one that adds a missing colour ({C} isn't one), untapped when its mana
     * would be used this turn on [costs] from [pool], else one that enters tapped (always-tapped lands before those that
     * could come in untapped later). [untapped] tells whether a land would come in untapped now.
     */
    internal fun <T> chooseLand(
        lands: List<T>, mana: (T) -> Card, untapped: (T) -> Boolean, have: Set<Char>,
        pool: List<Pair<Set<Char>, Int>>, costs: List<Cost>, last: (T) -> Boolean = { false },
    ): T {
        val up = lands.associateWith(untapped)
        val now = spend(pool, costs)
        val needed = lands.any { l -> up.getValue(l) && spend(pool + (mana(l).colours to mana(l).amount), costs) > now }
        return lands.sortedWith(
            compareBy<T> { if (needed) !up.getValue(it) else up.getValue(it) }
                .thenBy(last)
                .thenBy { !up.getValue(it) && mana(it).untap != null }
                .thenByDescending { (mana(it).colours - have - 'C').size }
                .thenByDescending { (mana(it).colours - 'C').size },
        ).first()
    }

    /**
     * Karsten's Commander mulligans: a free one, then London mulligans bottoming towards three lands. [mana] tells
     * what each card does for the mana (the win turn keeps more about its cards).
     */
    internal fun <T> keepHand(deck: List<T>, random: Random, mana: (T) -> Card): Pair<MutableList<T>, List<T>> {
        var hand: MutableList<T> = mutableListOf()
        var rest: List<T> = emptyList()
        for (attempt in 0..4) {
            val shuffled = deck.shuffled(random)
            hand = shuffled.take(7).toMutableList()
            rest = shuffled.drop(7)
            val lands = hand.count { mana(it).kind == Kind.LAND }
            if (attempt == 0 && lands in setOf(0, 1, 2, 6, 7)) continue
            if (attempt == 1 && lands in setOf(0, 1, 6, 7)) continue
            repeat((attempt - 1).coerceAtLeast(0)) {
                val (l, s) = hand.partition { mana(it).kind == Kind.LAND }
                when {
                    l.size > 3 -> hand.remove(l.minBy { mana(it).colours.size })
                    s.isNotEmpty() -> hand.remove(s.first())
                    else -> hand.remove(l.minBy { mana(it).colours.size })
                }
            }
            val kept = hand.count { mana(it).kind == Kind.LAND }
            if (attempt == 2 && kept in setOf(0, 1, 5, 6)) continue
            if (attempt == 3 && kept in setOf(0, 1, 5)) continue
            break
        }
        return hand to rest
    }

    /** Plays a land; [waiting] are the costs of the commanders not cast yet (whether an untapped land's mana is used). */
    private fun playLand(hand: MutableList<Card>, sources: MutableList<Source>, turn: Int, waiting: List<Cost>) {
        // Lotus Field waits until there are lands to sacrifice to it (since 1.69).
        val lands = hand.filter { it.kind == Kind.LAND && sources.count { s -> s.land && turn < s.until } >= it.sacrificesLands }
        if (lands.isEmpty()) return
        val have = sources.flatMap { it.colours }.toSet()
        val out = sources.filter { it.land && turn <= it.until }
            .let { l -> LandsOut(l.size, l.count { it.basic }, l.flatMap { it.types }.toSet()) }
        fun untapped(l: Card) = entersUntapped(l, out, hand.toMutableList().also { it.remove(l) })
        val pool = sources.filter { it.from <= turn && turn <= it.until }.map { it.colours to it.amount }
        val costs = waiting + hand.filter { it.kind == Kind.ROCK || it.kind == Kind.CREATURE || it.kind == Kind.RAMP }.map { it.cost }
        val land = chooseLand(lands, { it }, ::untapped, have, pool, costs)
        val up = untapped(land)
        hand.remove(land)
        // The lands sacrificed to it are tapped for mana first.
        repeat(land.sacrificesLands) { sources.filter { it.land && turn < it.until }.minByOrNull { it.colours.size }?.until = turn }
        val from = if (up) turn else turn + 1
        sources += Source(land.colours, land.amount, from, if (land.uses > 0) from + land.uses - 1 else Int.MAX_VALUE, land = true, types = land.types, basic = land.basic)
    }

    /** Casts the cheapest mana makers that can be paid for with what's left this turn. */
    private fun castRamp(hand: MutableList<Card>, sources: MutableList<Source>, ready: List<Source>, turn: Int, own: Set<Char>) {
        val pool = ready.map { it.colours to it.amount }.toMutableList()
        for (card in hand.filter { it.kind == Kind.ROCK || it.kind == Kind.CREATURE || it.kind == Kind.RAMP }.sortedBy { it.cost.manaValue }) {
            val left = pay(pool, card.cost) ?: continue
            pool.clear()
            pool += left
            hand.remove(card)
            when (card.kind) {
                Kind.RAMP -> {
                    // Basic lands (since 1.71 they count for "unless you control …" lands).
                    val types = basicTypes(card.colours)
                    repeat(card.amount) { sources += Source(card.colours, 1, if (card.tapped) turn + 1 else turn, land = true, types = types, basic = true) }
                    if (card.toHand) hand += Card(Kind.LAND, card.colours, false, Cost(0, emptyList()), 1, types = types, basic = true)
                }
                Kind.CREATURE -> sources += Source(card.colours, card.amount, if (card.haste) turn else turn + 1)
                else -> sources += Source(card.colours, card.amount, turn)
            }
        }
    }

    fun canPay(sources: List<Pair<Set<Char>, Int>>, cost: Cost): Boolean = pay(sources, cost) != null

    /** What's left of [sources] after paying [cost], or null when it can't be paid (coloured pips first). */
    internal fun pay(sources: List<Pair<Set<Char>, Int>>, cost: Cost): List<Pair<Set<Char>, Int>>? {
        // Not enough mana at all: no need to try (this is called a lot by the win turn).
        if (sources.sumOf { it.second } < cost.manaValue) return null
        val left = sources.map { it.first to it.second }.sortedBy { it.first.size }.toMutableList()
        val amounts = left.map { it.second }.toMutableList()
        val pips = cost.pips.sortedBy { it.size }
        fun match(i: Int): Boolean {
            if (i == pips.size) return true
            for (j in left.indices) {
                if (amounts[j] > 0 && left[j].first.any { it in pips[i] }) {
                    amounts[j]--
                    if (match(i + 1)) return true
                    amounts[j]++
                }
            }
            return false
        }
        if (!match(0)) return null
        var generic = cost.generic
        for (j in left.indices) {
            val use = minOf(generic, amounts[j])
            amounts[j] -= use
            generic -= use
        }
        if (generic > 0) return null
        return left.indices.filter { amounts[it] > 0 }.map { left[it].first to amounts[it] }
    }
}
