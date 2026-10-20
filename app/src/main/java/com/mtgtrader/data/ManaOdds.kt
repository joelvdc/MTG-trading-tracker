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
 * - each turn a card is drawn (also on turn 1, as in multiplayer games), a land is played (one that enters
 *   untapped and adds a missing colour first), and mana rocks, mana creatures and land ramp spells
 *   (Cultivate, Farseek…) costing up to 4 are cast when they can be;
 * - lands that enter tapped come in tapped ("unless …" lands are counted as untapped), mana creatures can
 *   tap from the next turn, and ramp spells' lands come in tapped when the card says so.
 * Nothing else is counted: no opponents, no removal, no Treasure, rituals or cost reducers. It's a best case
 * to compare decks and changes by, not a promise.
 */
object ManaOddsCalc {
    const val GAMES = 10_000
    const val TURNS = 7

    private const val COLOURS = "WUBRG"
    private val BASIC_TYPES = mapOf("plains" to 'W', "island" to 'U', "swamp" to 'B', "mountain" to 'R', "forest" to 'G')
    private val symbol = Regex("\\{([^}]+)\\}")
    private val tapAdd = Regex("\\{T\\}[^:.]*: Add", RegexOption.IGNORE_CASE)
    private val rampSearch = Regex(
        "search your library for (up to )?(a|an|one|two)[^.]*(land|plains|island|swamp|mountain|forest) cards?[^.]*onto the battlefield",
        RegexOption.IGNORE_CASE,
    )

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

    /** What a card does for the mana: also used by the win turn ([WinOddsCalc], since 1.52). */
    internal class Card(val kind: Kind, val colours: Set<Char>, val tapped: Boolean, val cost: Cost, val amount: Int)

    /** A mana source on the battlefield: what it makes, how much, and from which turn. */
    private class Source(val colours: Set<Char>, val amount: Int, val from: Int)

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
                playLand(hand, sources, turn)
                val ready = sources.filter { it.from <= turn }
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
            val tapped = if (fetch) "tapped" in faces
            else ("enters tapped" in faces || "enters the battlefield tapped" in faces) && "unless" !in faces
            return Card(Kind.LAND, colours.ifEmpty { setOf('C') }, tapped, Cost(0, emptyList()), 1)
        }
        val type = c.typeLine ?: c.types
        val produced = c.producedMana.orEmpty().filter { it in own || it == 'C' }.toSet()
        if (produced.isNotEmpty() && c.cmc <= 4 && tapAdd.containsMatchIn(text) && ("Artifact" in type || "Creature" in type)) {
            val amount = if (Regex("Add \\{C\\}\\{C\\}").containsMatchIn(text)) 2 else 1
            return Card(if ("Creature" in type) Kind.CREATURE else Kind.ROCK, produced, false, cost(c.manaCost), amount)
        }
        if (c.cmc <= 4 && ("Sorcery" in type || "Instant" in type) && rampSearch.containsMatchIn(text)) {
            val what = rampSearch.find(text)!!.value.lowercase()
            val named = BASIC_TYPES.filterKeys { it in what }.values.filter { it in own }.toSet()
            val colours = named.ifEmpty { own }.ifEmpty { setOf('C') }
            return Card(Kind.RAMP, colours, "tapped" in what || "tapped" in text.lowercase(), cost(c.manaCost), 1)
        }
        return Card(Kind.SPELL, emptySet(), false, Cost(0, emptyList()), 0)
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

    private fun playLand(hand: MutableList<Card>, sources: MutableList<Source>, turn: Int) {
        val lands = hand.filter { it.kind == Kind.LAND }
        if (lands.isEmpty()) return
        val have = sources.flatMap { it.colours }.toSet()
        val land = lands.sortedWith(compareBy<Card> { it.tapped }.thenByDescending { (it.colours - have).size }).first()
        hand.remove(land)
        sources += Source(land.colours, 1, if (land.tapped) turn + 1 else turn)
    }

    /** Casts the cheapest mana makers that can be paid for with what's left this turn. */
    private fun castRamp(hand: MutableList<Card>, sources: MutableList<Source>, ready: List<Source>, turn: Int, own: Set<Char>) {
        val pool = ready.map { it.colours to it.amount }.toMutableList()
        for (card in hand.filter { it.kind == Kind.ROCK || it.kind == Kind.CREATURE || it.kind == Kind.RAMP }.sortedBy { it.cost.manaValue }) {
            val left = pay(pool, card.cost) ?: continue
            pool.clear()
            pool += left
            hand.remove(card)
            sources += when (card.kind) {
                Kind.RAMP -> Source(card.colours, 1, if (card.tapped) turn + 1 else turn)
                Kind.CREATURE -> Source(card.colours, card.amount, turn + 1)
                else -> Source(card.colours, card.amount, turn)
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
