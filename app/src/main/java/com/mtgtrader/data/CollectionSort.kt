package com.mtgtrader.data

import androidx.room.withTransaction
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * The three places of the sort of 1.104 to 1.106, still used for their synced settings' keys. Since 1.104.
 * The sort itself works with the owner's own levels (see [CollectionSortPlanner]).
 */
enum class SortPlace(val key: String, val label: String) {
    TOP("top", "Collection binder"),
    MID("mid", "Not bulk"),
    BULK("bulk", "Bulk");

    companion object {
        /** The order the places were filled in, and the order moves were listed in. */
        val ORDER = listOf(TOP, MID, BULK)

        fun fromKey(key: String): SortPlace? = entries.firstOrNull { it.key == key }
    }
}

/** Unsorted's key in the planner: never synced, it's the app's built-in Unsorted (binder id 0). */
const val UNSORTED_KEY = "unsorted"

/** Any binder that isn't one of the places (a deck's, the trade binder, …): never touched, only counted. */
const val OTHER_KEY = "other"

/** The Decks binder's key in the plan. Since 1.107. */
const val DECKS_KEY = "decks"

/** A level's key: "l1" is the best one. */
fun levelKey(i: Int) = "l${i + 1}"

/** Whether [place] is one of the levels ("l1", "l2"…). */
fun isLevelKey(place: String) = Regex("^l[0-9]+$").matches(place)

/** The places in order, from the highest (Decks) down; [orderOf] lists them for a number of levels, [idx] looks one up. */
fun orderOf(levelCount: Int): List<String> = listOf(DECKS_KEY) + (0 until max(0, levelCount)).map { levelKey(it) } + UNSORTED_KEY

/** [place]'s place in [order]; a place that isn't one (a deck's binder, the trade binder) comes last. */
fun idx(place: String, order: List<String>): Int = order.indexOf(place).let { if (it < 0) order.size else it }

/** One level: how many copies its binder holds, and the cheapest card it takes. Since 1.107. */
data class SortLevelRule(
    val key: String,
    /** The copies it holds; 0 on the LAST level = no limit, 0 elsewhere = holds nothing. */
    val max: Int,
    /** Under this price (or without a price) a card never goes to this level (the trade binder's minimum's money). */
    val minPrice: Double?,
)

/**
 * How the sort works: the levels, how copies go together, how it weighs cards and how far it goes. Since 1.107
 * (the three limits and the floor became [levels]).
 */
data class SortRules(
    val levels: List<SortLevelRule> = listOf(
        SortLevelRule(levelKey(0), 200, 0.5),
        SortLevelRule(levelKey(1), 1_000, 0.5),
        SortLevelRule(levelKey(2), 0, null),
    ),
    /** 0 = by value only, 50 = like the trade binder, 100 = popularity counts most. */
    val mix: Int = 50,
    /** Most copies that may move in seven days once the first sort is done. */
    val weekly: Int = 10,
    val together: Together = Together.NAME,
    /** A copy this many times as valuable as the others is much more valuable … */
    val outlierRatio: Double = 3.0,
    /** … and that many euros or more above them. */
    val outlierGap: Double = 5.0,
    val deckCopies: DeckCopies = DeckCopies.BEST,
)

/** One card's copies in one place: a stack of the collection. [fixed] = signed or altered. Since 1.104. */
data class SortStack(
    val key: String,
    val name: String,
    /** A level's key, [DECKS_KEY], [UNSORTED_KEY], or [OTHER_KEY] (a deck's binder, the trade binder…). */
    val place: String,
    val copies: Int,
    val price: Double? = null,
    val rank: Int? = null,
    val fixed: Boolean = false,
)

/** A suggested move of [copies] of a card from one place to another, and why. Since 1.104. */
data class SortMove(
    val key: String,
    val name: String,
    val from: String,
    val to: String,
    val copies: Int,
    val price: Double?,
    val score: Double,
    val reason: String,
) {
    /** This move, turned down: it isn't suggested again. */
    val skipKey get() = "$key>$to"
}

/** What a sort would do, how many copies each place holds afterwards, and what waits for next week. Since 1.104. */
data class SortPlan(
    val moves: List<SortMove>,
    /** Every level's key, [DECKS_KEY] and [UNSORTED_KEY], 0 when empty. */
    val after: Map<String, Int>,
    /** Copies whose move waits for next week (the weekly cap). */
    val heldBack: Int,
    /** Copies that moved in the last seven days, against the cap. */
    val weekUsed: Int,
    /** How many copies may move now (-1 before the first sort, or when the cap is ignored). */
    val budget: Int,
    /** Copies the active decks need that you don't own. Since 1.107. */
    val deckShort: Int = 0,
)

/**
 * Splits the collection into the owner's levels (one binder each, best first), an optional Decks binder and
 * Unsorted, with the best cards first. Value and how much Commander players want a card (EDHREC rank) say where it
 * goes. Once the first sort is done it keeps the result steady: a card gives up its place only to one that's clearly
 * better, rests eight weeks after a move, and only so many copies move a week. Copies of a card may go together
 * (all printings of it, or one printing), except a copy that's much more valuable, which goes its own way.
 *
 * The pure core (the website's `planSort`): it works on [SortStack]s, so it's tested (and ported to the website)
 * without the database.
 */
object CollectionSortPlanner {
    /** A moved card rests this many days (eight weeks). */
    const val REST_DAYS = 56

    /** Log lines older than this many days are forgotten. */
    const val LOG_DAYS = 70

    private const val OTHER = OTHER_KEY

    /** A copy's appeal: its value, plus more for cards Commander players want when [mix] is high. */
    fun score(price: Double?, rank: Int?, mix: Int): Double =
        (price ?: 0.0) * (1 + TradeBinderPlanner.popularity(rank) * (mix / 50.0).pow(2))

    /** The day of [nowMs], counted in UTC days since 1970, as the log keeps it. */
    fun epochDay(nowMs: Long): Long = floor(nowMs / 86_400_000.0).toLong()

    /** A log line for [copies] of [key] moving on [day]; [exempt]: it doesn't count against the weekly limit. */
    fun logLine(day: Long, key: String, copies: Int, exempt: Boolean) = "$day|$key|$copies|${if (exempt) 1 else 0}"

    /** Whether a log line is from before the last [LOG_DAYS] days, so it can be forgotten. */
    fun isOld(line: String, today: Long) = (line.substringBefore('|').toLongOrNull() ?: 0L) < today - LOG_DAYS

    fun plan(
        stacks: List<SortStack>,
        deckNeeds: Map<String, Int>,
        rules: SortRules,
        skips: Set<String>,
        log: List<String>,
        settled: Boolean,
        today: Long,
        decksPlace: Boolean = false,
        ignoreCap: Boolean = false,
    ): SortPlan {
        val levels = rules.levels
        val last = levels.size - 1
        val order = orderOf(levels.size)

        // One unit per copy; cards in other binders (a deck's, the trade binder) only count for the decks' needs.
        val units = ArrayList<Unit>()
        for (s in stacks) {
            if (s.place == OTHER) continue
            repeat(max(0, s.copies)) {
                units += Unit(s.key, s.name, s.place, s.price, s.rank, score(s.price, s.rank, rules.mix), s.fixed)
            }
        }

        // Pinned: the signed and altered copies stay where they are.
        val pinned = LinkedHashMap<Unit, String>()
        for (u in units) if (u.fixed) pinned[u] = u.place

        // The decks' needs. Copies already in other binders (a deck's own) count for them.
        val outside = HashMap<String, Int>()
        for (s in stacks) if (s.place == OTHER) {
            val n = TradeBinderPlanner.nameKey(s.name)
            outside[n] = (outside[n] ?: 0) + s.copies
        }
        val deckUnits = HashSet<Unit>()
        var deckShort = 0
        for ((name, need) in deckNeeds) {
            var reserve = max(0, need - (outside[name] ?: 0))
            if (reserve <= 0) continue
            val same = units.filter { TradeBinderPlanner.nameKey(it.name) == name }
            if (!decksPlace) {
                // Exactly the rule of 1.104: the most valuable copies stay home; you're short of the rest.
                same.sortedWith(byValue(order).thenComparator { a, b -> byKey.compare(a, b) }).take(reserve).forEach { pinned[it] = it.place }
                deckShort += max(0, reserve - same.size)
                continue
            }
            // A signed copy already in Decks does for one of them.
            for (u in same) if (u.fixed && u.place == DECKS_KEY && reserve > 0) reserve = max(0, reserve - 1)
            // The most valuable copies (or the cheapest), then the copy already in Decks, then the highest place
            // (by card, and the copy already in Decks before one that isn't: two copies of a card read the same).
            val byWorth = if (rules.deckCopies == DeckCopies.BEST) byWorthDesc(settled) else byWorthAsc(settled)
            val candidates = same.filter { !it.fixed && it !in pinned }
                .sortedWith(
                    byWorth.thenBy { idx(it.place, order) }.thenBy { it.key }.thenBy { if (it.place == DECKS_KEY) 0 else 1 },
                )
            for (u in candidates.take(reserve)) {
                deckUnits += u
                pinned[u] = DECKS_KEY
            }
            deckShort += max(0, reserve - candidates.size)
        }

        // Cards that moved less than eight weeks ago rest where they are (in a level; a deck's copies have their place).
        if (settled) {
            val recent = HashSet<String>()
            for (line in log) {
                val p = line.split('|')
                val day = p.getOrNull(0)?.toLongOrNull()
                if (day != null && day > today - REST_DAYS) p.getOrNull(1)?.let { recent += it }
            }
            for (u in units) if (isLevelKey(u.place) && u !in pinned && u.key in recent) pinned[u] = u.place
        }

        // What moved in the last seven days, and how many copies may move now.
        val weekUsed = if (!settled) 0 else log.sumOf { line ->
            val p = line.split('|')
            val day = p.getOrNull(0)?.toLongOrNull() ?: return@sumOf 0
            val copies = p.getOrNull(2)?.toIntOrNull() ?: return@sumOf 0
            if (today - day < 7 && p.getOrNull(3) == "0") copies else 0
        }
        val budget = if (settled && !ignoreCap) max(0, rules.weekly - weekUsed) else -1

        /** Fills the levels and works out the moves; [held] also holds the copies the weekly cap keeps back. */
        fun fill(held: Map<Unit, String>): Run {
            // Copies that go together, in the order their first copy comes in.
            val packs = ArrayList<Pack>()
            val packAt = HashMap<String, Pack>()
            for (u in units) {
                if (u in held || u in deckUnits) continue
                val id = when (rules.together) {
                    Together.OFF -> "${u.key}\u0000${packs.size}"
                    Together.PRINTING -> u.key
                    Together.NAME -> TradeBinderPlanner.nameKey(u.name)
                }
                val pack = packAt[id]
                if (pack != null) pack.units += u
                else {
                    val made = Pack(mutableListOf(u), u.key)
                    packAt[id] = made
                    packs += made
                }
            }
            // Much more valuable copies are outliers: they go their own way, as packs of their own, right after the
            // pack they left (in the order their first copy comes in).
            val pool = ArrayList<Pack>()
            for (pack in packs) {
                if (rules.together == Together.OFF || pack.units.size < 2) {
                    pool += pack
                    continue
                }
                val usual = lowerMedian(pack.units.map { it.price })
                val own = LinkedHashMap<String, Pack>()
                val outliers = HashSet<Unit>()
                for (u in pack.units) {
                    val price = u.price ?: continue
                    if (!(price >= rules.outlierRatio * usual && price - usual >= rules.outlierGap)) continue
                    own.getOrPut(u.key) { Pack(mutableListOf(), u.key) }.units += u
                    outliers += u
                }
                pool += Pack(pack.units.filterTo(mutableListOf()) { it !in outliers }, pack.key)
                pool += own.values
            }
            for (pack in pool) {
                pack.price = if (pack.units.isEmpty() || pack.units.all { it.price == null }) null else lowerMedian(pack.units.map { it.price })
                pack.score = pack.price?.let { score(it, pack.units.first().rank, rules.mix) } ?: 0.0
            }
            val filled = LinkedHashMap<Unit, String>()
            val taken = HashSet<Pack>()
            val m = if (settled) 1.25 else 1.0
            // The copies already in a level (signed ones, the decks' copies) fill it first: they leave less room.
            val heldIn = held.values.groupingBy { it }.eachCount()
            for (i in levels.indices) {
                val rule = levels[i]
                val place = levelKey(i)
                val unlimited = i == last && rule.max == 0
                val limit = max(0, rule.max - (heldIn[place] ?: 0))
                if (limit == 0 && !unlimited) continue
                // What may go in: a pack whose copies weren't all turned down for this level, and that is worth it.
                val here = ArrayList<Pack>()
                for (pack in pool) {
                    if (pack.units.isEmpty() || pack in taken) continue
                    if (pack.units.any { "${it.key}>$place" in skips }) continue
                    val anchored = pack.units.any { it.place == place }
                    val minPrice = rule.minPrice
                    if (minPrice != null) {
                        val price = pack.price
                        if (price == null || !(price >= minPrice || (settled && anchored && price >= minPrice / m))) continue
                    }
                    pack.anchored = anchored
                    pack.adjusted = if (anchored) pack.score * m else pack.score
                    here += pack
                }
                // The best appeal first, then the copies already here, then by card.
                here.sortWith(comesBefore)
                var used = 0
                val chosen = ArrayList<Pack>()
                for (pack in here) {
                    val size = pack.units.size
                    if (unlimited || used + size <= limit) {
                        chosen += pack
                        taken += pack
                        used += size
                        continue
                    }
                    // A pack with a copy already in this level and copies of its own to bring makes room for them:
                    // without its newcomers it would have fitted, and the packs not of this level, taken last, make
                    // the room.
                    if (!settled || !pack.anchored) continue
                    val incumbents = pack.units.count { it.place == place }
                    if (incumbents == pack.units.size || used + incumbents > limit) continue
                    val room = limit - used
                    val turnedOut = ArrayList<Pack>()
                    var freed = 0
                    var k = chosen.size - 1
                    while (k >= 0 && freed + room < size) {
                        if (!chosen[k].anchored) {
                            turnedOut += chosen[k]
                            freed += chosen[k].units.size
                        }
                        k--
                    }
                    if (freed + room < size) continue
                    for (p in turnedOut) {
                        chosen -= p
                        taken -= p
                        used -= p.units.size
                    }
                    chosen += pack
                    taken += pack
                    used += size
                }
                for (pack in chosen) for (u in pack.units) filled[u] = place
            }
            // What's left goes to Unsorted, unless that was turned down: then it stays where it is.
            for (u in units) if (u !in filled && u !in held && u !in deckUnits && "${u.key}>$UNSORTED_KEY" !in skips) filled[u] = UNSORTED_KEY
            val assigned = LinkedHashMap<Unit, String>(filled)
            assigned.putAll(held)
            for (u in deckUnits) assigned[u] = DECKS_KEY
            return Run(units, assigned, held, group(units, assigned, rules, order))
        }

        var run = fill(pinned)
        var heldBack = 0
        if (budget >= 0) {
            // The cap: the most important moves stay as far as they fit; the copies cut wait for next week and stay
            // where they are while the levels are filled again.
            val waiting = HashSet<Unit>()
            fun cappedOf(r: Run) = r.moves.filter { isLevelKey(it.from) && it.to != DECKS_KEY }
            fun countCut(cut: List<Unit>) {
                for (u in cut) if (waiting.add(u)) heldBack++
            }
            for (round in 0 until 20) {
                if (cappedOf(run).sumOf { it.copies } <= budget) break
                val keep = run.keepMostImportant(cappedOf(run), budget, order)
                if (keep.cut.isEmpty()) break
                countCut(keep.cut)
                run = fill(run.pinsWith(keep.cut))
            }
            // After twenty rounds the moves that fit still go; the copies left over wait for next week.
            if (cappedOf(run).sumOf { it.copies } > budget) {
                val keep = run.keepMostImportant(cappedOf(run), budget, order)
                if (keep.cut.isEmpty()) {
                    run = run.withMoves(group(units, run.assigned, rules, order))
                } else {
                    countCut(keep.cut)
                    // The cut copies stay where they are.
                    val assigned = LinkedHashMap<Unit, String>(run.assigned)
                    for (u in keep.cut) assigned[u] = u.place
                    run = run.withMoves(group(units, assigned, rules, order), assigned = assigned)
                }
            }
        }

        val after = LinkedHashMap<String, Int>()
        for (place in levels.indices.map { levelKey(it) } + listOf(DECKS_KEY, UNSORTED_KEY)) after[place] = 0
        for (u in units) {
            val place = run.assigned[u] ?: u.place
            after[place] = (after[place] ?: 0) + 1
        }
        return SortPlan(run.moves, after, heldBack, weekUsed, budget, deckShort)
    }

    /**
     * Groups the copies that change place by card and from/to, with their reason: from the highest place first,
     * then to, then the most valuable card.
     */
    private fun group(units: List<Unit>, finalPlace: Map<Unit, String>, rules: SortRules, order: List<String>): List<SortMove> {
        val byMove = LinkedHashMap<Triple<String, String, String>, MutableList<Unit>>()
        for (u in units) {
            val to = finalPlace[u] ?: continue
            if (to == u.place) continue
            byMove.getOrPut(Triple(u.key, u.place, to)) { mutableListOf() } += u
        }
        fun nameOf(u: Unit) = TradeBinderPlanner.nameKey(u.name)
        // The cards whose copies leave Decks (they went to a better copy), and whose go in.
        val leavingDeck = units.filter { it.place == DECKS_KEY && (finalPlace[it] ?: it.place) != DECKS_KEY }.map(::nameOf).toSet()
        val intoDeck = units.filter { it.place != DECKS_KEY && finalPlace[it] == DECKS_KEY }.map(::nameOf).toSet()
        fun levelAt(place: String): SortLevelRule? = rules.levels.indices.firstOrNull { levelKey(it) == place }?.let { rules.levels[it] }
        return byMove.map { (k, list) ->
            val (key, from, to) = k
            val first = list.first()
            val name = nameOf(first)
            val reason = when {
                to == DECKS_KEY -> if (name in leavingDeck) "upgrade for your decks" else "for your decks"
                from == DECKS_KEY -> (if (name in intoDeck) "swapped for a better copy" else "no longer in an active deck") +
                    (if (to == UNSORTED_KEY) " · no room" else "")
                to == UNSORTED_KEY -> "no room"
                // Moving down a card under the level's minimum (or without a price): the minimum says why.
                else -> {
                    val rule = levelAt(from)
                    when {
                        idx(to, order) > idx(from, order) && rule?.minPrice != null && (first.price == null || first.price < rule.minPrice) ->
                            "under ${Money.format(rule.minPrice)}"
                        idx(to, order) < idx(from, order) -> TradeBinderPlanner.why(first.price, first.rank, null)
                        else -> "made room for more valuable cards"
                    }
                }
            }
            SortMove(key, first.name, from, to, list.size, first.price, first.score, reason)
        }.sortedWith(moveOrder(order).thenComparator { a, b -> a.key.compareTo(b.key) })
    }

    /** The most valuable copies first; ties by place (highest first) and then by card. */
    private fun byValue(order: List<String>): Comparator<Unit> =
        compareByDescending<Unit> { it.price ?: -1.0 }.thenBy { idx(it.place, order) }

    /** The decks' copies by what they're worth (a copy without a price: -1), dearest first; once [settled], a copy already in Decks is worth a quarter more. */
    private fun byWorthDesc(settled: Boolean): Comparator<Unit> =
        compareByDescending { u -> (u.price ?: -1.0) * (if (settled && u.place == DECKS_KEY) 1.25 else 1.0) }

    /** The decks' copies by what they cost (a copy without a price: 0), cheapest first; once [settled], a copy already in Decks costs a quarter less. */
    private fun byWorthAsc(settled: Boolean): Comparator<Unit> =
        compareBy { u -> (u.price ?: 0.0) / (if (settled && u.place == DECKS_KEY) 1.25 else 1.0) }

    /** Moves from the highest place first, then to the highest, then the most valuable card (then by card). */
    private fun moveOrder(order: List<String>): Comparator<SortMove> = Comparator { a, b ->
        val byFrom = idx(a.from, order) - idx(b.from, order)
        if (byFrom != 0) byFrom
        else {
            val byTo = idx(a.to, order) - idx(b.to, order)
            if (byTo != 0) byTo else compareValues(b.score, a.score)
        }
    }

    /** Whether [a] goes in before [b]: the better appeal, then the copies already here, then by card. */
    private val comesBefore = Comparator<Pack> { a, b ->
        when {
            a.adjusted != b.adjusted -> compareValues(b.adjusted, a.adjusted)
            a.anchored != b.anchored -> if (a.anchored) -1 else 1
            else -> a.key.compareTo(b.key)
        }
    }

    private val byKey = Comparator<Unit> { a, b -> a.key.compareTo(b.key) }

    /** The lower median of some prices (null counts as 0): the sorted list's index (n - 1) / 2. */
    private fun lowerMedian(prices: List<Double?>): Double {
        val sorted = prices.map { it ?: 0.0 }.sorted()
        return sorted[(sorted.size - 1) / 2]
    }

    /** One copy, on its way to a place. */
    private class Unit(val key: String, val name: String, val place: String, val price: Double?, val rank: Int?, val score: Double, val fixed: Boolean)

    /** Copies that go together, with the price and score that a level's floor and order use. */
    private class Pack(val units: MutableList<Unit>, val key: String) {
        /** Null when every copy has no price. */
        var price: Double? = null
        var score = 0.0

        /** This level's score for them: a quarter more when some of them are already there (once settled). */
        var adjusted = 0.0
        var anchored = false
    }

    /** One pass of filling and grouping, with what's needed to weigh and cut its moves. */
    private class Run(
        val units: List<Unit>,
        val assigned: Map<Unit, String>,
        val pinned: Map<Unit, String>,
        val moves: List<SortMove>,
    ) {
        /** The lowest score among the [place]'s copies, or null when it holds none. */
        private fun lowest(place: String): Double? = units.filter { (assigned[it] ?: it.place) == place }.minOfOrNull { it.score }

        /**
         * How much a move matters: how far apart its card's appeal is from the lowest score among the
         * copies of the higher of its two places (0 when that place holds nothing).
         */
        private fun importance(move: SortMove, order: List<String>): Double {
            val higher = minOf(idx(move.from, order), idx(move.to, order)).coerceAtLeast(0)
            val ref = order.getOrNull(higher)?.let { lowest(it) } ?: 0.0
            return abs(ln((move.score + 0.01) / (ref + 0.01)))
        }

        /** The copies of [move]: the same card, from the same place, going to the same place. The least valuable first. */
        private fun cutUnits(move: SortMove, keep: Int): List<Unit> = units.filter {
            it.key == move.key && it.place == move.from && assigned[it] == move.to
        }.sortedWith(compareBy<Unit> { it.score }.thenComparator { a, b -> byKey.compare(a, b) }).take(move.copies - keep)

        /** The moves that fit in [budget], the most important first (ties by card), and the copies they cut. */
        fun keepMostImportant(moves: List<SortMove>, budget: Int, order: List<String>): Kept {
            var left = budget
            val kept = ArrayList<SortMove>()
            val cut = ArrayList<Unit>()
            for (move in moves.sortedWith(compareByDescending<SortMove> { importance(it, order) }.thenBy { it.key })) {
                val take = minOf(left, move.copies)
                left -= take
                if (take > 0) kept += move.copy(copies = take)
                if (take < move.copies) cut += cutUnits(move, take)
            }
            return Kept(kept.sortedWith(Comparator { a, b ->
                val byFrom = idx(a.from, order) - idx(b.from, order)
                if (byFrom != 0) byFrom
                else {
                    val byTo = idx(a.to, order) - idx(b.to, order)
                    if (byTo != 0) byTo else a.key.compareTo(b.key)
                }
            }), cut)
        }

        /** These copies stay where they are for now. */
        fun pinsWith(extra: List<Unit>) = pinned + extra.associateWith { it.place }

        /** Only [newMoves] still move ([assigned] overrides where every copy ends up); the [cut] copies stay where they were. */
        fun withMoves(newMoves: List<SortMove>, cut: List<Unit> = emptyList(), assigned: Map<Unit, String> = this.assigned): Run {
            val kept = LinkedHashMap<Unit, String>(assigned)
            for (u in cut) kept[u] = u.place
            return Run(units, kept, pinned, newMoves)
        }
    }

    private class Kept(val moves: List<SortMove>, val cut: List<Unit>)
}

/**
 * Sorts the collection: works out where every card goes (see [CollectionSortPlanner]), and moves the
 * copies the user accepts.
 */
class CollectionSorter(private val db: AppDatabase, private val repo: MtgRepository, private val settings: Settings) {
    /**
     * The levels that exist, best first, with their binders and settings. The keys are by position in this list
     * (as the website's live levels), not by position in the saved settings. Since 1.107.
     */
    suspend fun levels(): List<Pair<SortLevelSetting, Binder>> {
        val dao = db.binderDao()
        return settings.sortLevels.mapNotNull { l -> l.binder.toLongOrNull()?.let { dao.get(it) }?.let { l to it } }
    }

    /** The Decks binder, when it's set up and still there. Since 1.107. */
    suspend fun decksBinder(): Binder? = settings.sortDecksId.takeIf { it > 0 }?.let { db.binderDao().get(it) }

    /**
     * Makes the chosen binders and remembers the settings: a binder that isn't there yet is made. Throws an
     * [IllegalArgumentException] when the settings can't work (as the website's `sortSetUp`). Since 1.107.
     */
    suspend fun setUp(
        levels: List<Triple<BinderChoice, Int, Double?>>,
        decks: BinderChoice?,
        mix: Int,
        weekly: Int,
        together: Together,
        outlierRatio: Double,
        outlierGap: Double,
        deckCopies: DeckCopies,
    ) {
        if (levels.isEmpty()) throw IllegalArgumentException("Set up at least one level.")
        if (levels.size > SortLevelsCodec.MAX_LEVELS) throw IllegalArgumentException("There can be at most six levels.")
        for (i in levels.indices) {
            val max = levels[i].second
            if (max < 0) throw IllegalArgumentException("The copies a level holds can't be below 0.")
            if (max == 0 && i < levels.size - 1) throw IllegalArgumentException("Level ${i + 1} can't hold nothing: only the last level may have no limit.")
        }
        // One after the other, so that a new binder's id is never taken for an older one. A new name that's already
        // taken is that binder: the one of that name the app has (see [MtgRepository.resolve]), or one a level before
        // it made.
        val ids = levels.map { (choice, _, _) -> repo.resolve(choice) } + listOfNotNull(decks?.let { repo.resolve(it) })
        // The binders the app has: one that's gone since it was picked is as good as none.
        val known = db.binderDao().all().map { it.id }.toSet()
        if (ids.distinct().size != ids.size) throw IllegalArgumentException("Two places can't use the same binder.")
        fun kept(id: Long) = id in known

        val levelSettings = levels.indices.map { i ->
            val (_, max, minPrice) = levels[i]
            ids[i] to SortLevelSetting(ids[i].toString(), max, minPrice)
        }.filter { (id, _) -> kept(id) }.map { (_, l) -> l }
        val decksId = decks?.let { ids[levels.size] }?.takeIf { kept(it) } ?: 0L
        if (levelSettings.isEmpty()) throw IllegalArgumentException("Set up at least one level.")
        // A no-limit level whose binder has gone leaves a last level with no limit: the check above made sure that's the last too.
        if (levels.last().second == 0 && levelSettings.last().max != 0) throw IllegalArgumentException("The level with no limit must be the last one.")

        settings.sortLevels = levelSettings
        settings.setSortDecks(decksId)
        settings.sortMix = mix
        settings.sortWeekly = weekly
        settings.sortTogether = together
        settings.sortOutlierRatio = outlierRatio
        settings.sortOutlierGap = outlierGap
        settings.sortDeckCopies = deckCopies
        settings.stamp()
    }

    /** The planner's input from the collection, the decks' needs and the settings. */
    suspend fun suggest(ignoreCap: Boolean = false): SortPlan {
        val priceType = settings.priceType.value
        val rows = db.collectionDao().allWithPrices()
        val live = levels()
        val decks = decksBinder()
        val placeOf = buildMap {
            live.forEachIndexed { i, (_, b) -> put(b.id, levelKey(i)) }
            decks?.let { put(it.id, DECKS_KEY) }
        }
        // Only Unsorted, the levels and Decks take part; every other binder (a deck's, the trade binder) is
        // left alone, and its copies only count for what the decks still need.
        val stacks = rows.map { r ->
            SortStack(
                key = CardKind.of(r.item).key,
                name = r.item.card.name,
                place = placeOf[r.item.binderId] ?: if (r.item.binderId == Binder.UNSORTED) UNSORTED_KEY else OTHER_KEY,
                copies = r.item.quantity,
                price = r.unitPrice(priceType),
                rank = r.info?.edhrecRank,
                fixed = r.item.marks.any,
            )
        }
        // The decks' needs, as for the trade binder: the planner takes off what sits in other binders.
        val needs = db.deckDao().cardCounts(settings.ownArchidektUser.value.trim()).groupBy { TradeBinderPlanner.nameKey(it.name) }.mapValues { (_, v) -> v.sumOf { it.qty } }
        val rules = SortRules(
            levels = live.mapIndexed { i, (l, _) -> SortLevelRule(levelKey(i), l.max, l.minPrice) },
            mix = settings.sortMix,
            weekly = settings.sortWeekly,
            together = settings.sortTogether,
            outlierRatio = settings.sortOutlierRatio,
            outlierGap = settings.sortOutlierGap,
            deckCopies = settings.sortDeckCopies,
        )
        return CollectionSortPlanner.plan(
            stacks, needs, rules, skipMigration(settings.sortSkips, live.size), settings.sortLog, settings.sortSettled,
            CollectionSortPlanner.epochDay(System.currentTimeMillis()),
            decksPlace = decks != null,
            ignoreCap = ignoreCap,
        )
    }

    /**
     * Moves the [accepted] copies and remembers the [declined] moves, in one go. Once anything is
     * accepted, a card gives up its place only to one that's clearly better.
     */
    suspend fun apply(accepted: List<SortMove>, declined: List<SortMove>) = db.withTransaction {
        val live = levels()
        val decks = decksBinder()
        val order = orderOf(live.size)
        val today = CollectionSortPlanner.epochDay(System.currentTimeMillis())
        // Where a place points: Unsorted's id (Binder.UNSORTED), a level's or Decks' binder id, or null where not set up.
        fun binderOf(place: String): Long? = when {
            place == UNSORTED_KEY -> Binder.UNSORTED
            place == DECKS_KEY -> decks?.id
            isLevelKey(place) -> place.drop(1).toIntOrNull()?.let { i -> live.getOrNull(i - 1)?.second?.id }
            else -> null
        }
        // A place that isn't set up at all (a level past the ones there are, a Decks binder that's gone): its moves don't happen.
        fun setUp(place: String) = place == UNSORTED_KEY || place == DECKS_KEY || isLevelKey(place) && idx(place, order) < order.size
        val lines = ArrayList<String>()
        // A card moves out of a place before another card moves into it.
        val at = { place: String -> idx(place, order) }
        for (move in accepted.sortedWith(compareBy<SortMove> { at(it.from) }.thenBy { at(it.to) })) {
            if (!setUp(move.from) || !setUp(move.to)) continue
            val from = binderOf(move.from) ?: continue
            val to = binderOf(move.to) ?: continue
            if (from == to) continue
            var left = move.copies
            val stacks = db.collectionDao().all().filter { it.binderId == from && CardKind.of(it).key == move.key }
            for (s in stacks.sortedByDescending { it.quantity }) {
                if (left <= 0) break
                val n = minOf(left, s.quantity)
                repo.saveCollectionEdit(s, to, n)
                left -= n
            }
            // Out of Unsorted, out of Decks, and into Decks, the cap doesn't count.
            if (left < move.copies) lines += CollectionSortPlanner.logLine(
                today, move.key, move.copies - left,
                exempt = move.from == UNSORTED_KEY || move.from == DECKS_KEY || move.to == DECKS_KEY,
            )
        }
        if (lines.isNotEmpty()) settings.sortSettled = true
        if (declined.isNotEmpty()) settings.sortSkips = settings.sortSkips + declined.map { it.skipKey }
        if (lines.isNotEmpty() || declined.isNotEmpty()) {
            // The log is written with the moves, forgetting lines from more than ten weeks ago.
            settings.sortLog = (settings.sortLog + lines).filterNot { CollectionSortPlanner.isOld(it, today) }
            settings.stampSyncedPrefs() // synced since 1.104
        }
    }

    /** Forgets the first sort, the rest periods and the turned-down moves. */
    suspend fun startOver() {
        settings.sortSettled = false
        settings.sortLog = emptyList()
        settings.sortSkips = emptySet()
        settings.stampSyncedPrefs() // synced since 1.104
    }

    /** A declined move from before 1.107 ("<key>><top", ">mid", ">bulk") says the level it now is. */
    private fun skipMigration(skips: Set<String>, levelCount: Int): Set<String> {
        fun rename(place: String) = when (place) {
            "top" -> levelKey(0)
            "mid" -> levelKey(1)
            "bulk" -> levelKey(max(0, levelCount - 1))
            else -> place
        }
        return skips.map { k ->
            val at = k.lastIndexOf('>')
            if (at < 0) k else k.substring(0, at + 1) + rename(k.substring(at + 1))
        }.toSet()
    }
}
