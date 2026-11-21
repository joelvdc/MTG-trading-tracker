package com.mtgtrader.data

import androidx.room.withTransaction
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/** The three places the collection is sorted into; Unsorted holds the rest. Since 1.104. */
enum class SortPlace(val key: String, val label: String) {
    TOP("top", "Collection binder"),
    MID("mid", "Not bulk"),
    BULK("bulk", "Bulk");

    companion object {
        /** The order the places are filled in, and the order moves are listed in. */
        val ORDER = listOf(TOP, MID, BULK)

        fun fromKey(key: String): SortPlace? = entries.firstOrNull { it.key == key }
    }
}

/** Unsorted's key in the planner: never synced, it's the app's built-in Unsorted (binder id 0). */
const val UNSORTED_KEY = "unsorted"

/** Any binder that isn't one of the four places (a deck's, the trade binder, …): never touched, only counted. */
const val OTHER_KEY = "other"

/** How many copies each place may hold, and how the sort weighs cards. Since 1.104. */
data class SortRules(
    val maxTop: Int = 200,
    val maxMid: Int = 1_000,
    /** 0 = as many as it takes. */
    val maxBulk: Int = 0,
    /** Under this price a card is always bulk (the same money as the trade binder's minimum). */
    val floor: Double = 0.5,
    /** 0 = by value only, 50 = like the trade binder, 100 = popularity counts most. */
    val mix: Int = 50,
    /** Most copies that may move in seven days once the first sort is done. */
    val weekly: Int = 10,
)

/** One card's copies in one place: a stack of the collection. [fixed] = signed or altered. Since 1.104. */
data class SortStack(
    val key: String,
    val name: String,
    /** A [SortPlace.key], [UNSORTED_KEY], or "other" (a deck's binder, the trade binder…). */
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
    val after: Map<String, Int>,
    /** Copies whose move waits for next week (the weekly cap). */
    val heldBack: Int,
    /** Copies that moved in the last seven days, against the cap. */
    val weekUsed: Int,
    /** How many copies may move now (-1 before the first sort, when there's no cap). */
    val budget: Int,
)

/**
 * Splits the collection into three binders, the best cards first, then the rest, then the bulk, with
 * Unsorted for what doesn't fit. Value and how much Commander players want a card (EDHREC rank) say
 * where it goes. Once the first sort is done it keeps the result steady: a card gives up its place only
 * to one that's clearly better, rests eight weeks after a move, and only so many copies move a week.
 *
 * The pure core: it works on [SortStack]s, so it's tested (and ported to the website) without the
 * database.
 */
object CollectionSortPlanner {
    /** A moved card rests this many days (eight weeks). */
    const val REST_DAYS = 56

    /** Log lines older than this many days are forgotten. */
    const val LOG_DAYS = 70

    /** The places in order, highest first. */
    val LEVELS = listOf(SortPlace.TOP.key, SortPlace.MID.key, SortPlace.BULK.key, UNSORTED_KEY)

    private const val OTHER = OTHER_KEY

    /** A copy's appeal: its value, plus more for cards Commander players want when [mix] is high. */
    fun score(price: Double?, rank: Int?, mix: Int): Double =
        (price ?: 0.0) * (1 + TradeBinderPlanner.popularity(rank) * (mix / 50.0).pow(2))

    /** The day of [nowMs], counted in UTC days since 1970, as the log keeps it. */
    fun epochDay(nowMs: Long): Long = floor(nowMs / 86_400_000.0).toLong()

    /** A log line for [copies] of [key] moving on [day], [fromUnsorted] or from a place. */
    fun logLine(day: Long, key: String, copies: Int, fromUnsorted: Boolean) = "$day|$key|$copies|${if (fromUnsorted) 1 else 0}"

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
    ): SortPlan {
        val max = mapOf(SortPlace.TOP.key to rules.maxTop, SortPlace.MID.key to rules.maxMid, SortPlace.BULK.key to rules.maxBulk)

        // One unit per copy; cards in other binders (a deck's, the trade binder) only count for the decks' needs.
        val units = ArrayList<Unit>()
        for (s in stacks) {
            if (s.place == OTHER) continue
            repeat(s.copies.coerceAtLeast(0)) {
                units += Unit(s.key, s.name, s.place, s.price, s.rank, score(s.price, s.rank, rules.mix), s.fixed)
            }
        }

        // Held units: signed and altered copies, and the copies the decks still need (the most valuable ones).
        val pinned = HashMap<Unit, String>()
        for (u in units) if (u.fixed) pinned[u] = u.place
        val outside = stacks.filter { it.place == OTHER }.groupBy { TradeBinderPlanner.nameKey(it.name) }.mapValues { (_, l) -> l.sumOf { it.copies } }
        for ((name, need) in deckNeeds) {
            val reserve = (need - (outside[name] ?: 0)).coerceAtLeast(0)
            if (reserve <= 0) continue
            units.filter { TradeBinderPlanner.nameKey(it.name) == name }.sortedWith(ByValue).take(reserve).forEach { pinned[it] = it.place }
        }

        // Cards that moved less than eight weeks ago rest where they are.
        if (settled) {
            val recent = log.mapNotNull { line ->
                val p = line.split('|')
                p.getOrNull(1)?.takeIf { (p.getOrNull(0)?.toLongOrNull() ?: Long.MIN_VALUE) > today - REST_DAYS }
            }.toSet()
            for (u in units) if (u.place != UNSORTED_KEY && u.key in recent) pinned[u] = u.place
        }

        // What moved in the last seven days, and how many copies may move now.
        val weekUsed = if (!settled) 0 else log.sumOf { line ->
            val p = line.split('|')
            val day = p.getOrNull(0)?.toLongOrNull() ?: return@sumOf 0
            val copies = p.getOrNull(2)?.toIntOrNull() ?: return@sumOf 0
            if (today - day < 7 && p.getOrNull(3) == "0") copies else 0
        }
        val budget = if (settled) (rules.weekly - weekUsed).coerceAtLeast(0) else -1

        /** Fills the places and works out the moves; [pinned] also holds the copies the weekly cap keeps back. */
        fun fill(pinned: Map<Unit, String>): Run {
            val assigned = HashMap<Unit, String>()
            val m = if (settled) 1.25 else 1.0
            for (place in SortPlace.ORDER) {
                // A bulk binder with no limit takes every candidate; the others take as many as they hold.
                val unlimited = place == SortPlace.BULK && max.getValue(place.key) == 0
                val limit = (max.getValue(place.key) - pinned.values.count { it == place.key }).coerceAtLeast(0)
                if (limit == 0 && !unlimited) continue
                val candidates = ArrayList<Pair<Unit, Double>>()
                for (u in units) {
                    if (u in assigned || u in pinned) continue
                    if ("${u.key}>${place.key}" in skips) continue
                    // The floor: under it (or without a price) a card is never taken by the top places.
                    if (place != SortPlace.BULK) {
                        val price = u.price
                        val qualifies = price != null && (price >= rules.floor || settled && u.place == place.key && price >= rules.floor / m)
                        if (!qualifies) continue
                    }
                    candidates += u to if (u.place == place.key) u.score * m else u.score
                }
                // The copies already in the place come first when their appeal is equal (even pinned ones).
                candidates.sortWith(compareByDescending<Pair<Unit, Double>> { it.second }.thenByDescending { it.first.place == place.key }.thenBy { it.first.key })
                for (i in 0 until (if (unlimited) candidates.size else minOf(limit, candidates.size))) assigned[candidates[i].first] = place.key
            }
            // What's left goes to Unsorted, unless that was turned down: then it stays where it is.
            for (u in units) if (u !in assigned && u !in pinned && "${u.key}>$UNSORTED_KEY" !in skips) assigned[u] = UNSORTED_KEY
            return Run(units, assigned + pinned, pinned, group(units, assigned + pinned, rules))
        }

        var run = fill(pinned)
        var heldBack = 0
        if (settled) {
            // The cap: the most important moves stay as far as they fit; the copies cut wait for next week
            // and stay where they are while the places are filled again.
            val waiting = HashSet<Unit>()
            for (round in 0 until 20) {
                val capped = run.moves.filter { it.from != UNSORTED_KEY }
                if (capped.sumOf { it.copies } <= budget) break
                val cut = run.keepMostImportant(capped, budget).cut
                if (cut.isEmpty()) break
                for (u in cut) if (waiting.add(u)) heldBack++
                run = fill(run.pinsWith(cut))
            }
            // After twenty rounds the moves that fit still go; the copies left over wait for next week.
            val capped = run.moves.filter { it.from != UNSORTED_KEY }
            if (capped.sumOf { it.copies } > budget) {
                val keep = run.keepMostImportant(capped, budget)
                if (keep.cut.isEmpty()) {
                    run = run.withMoves(keep.moves)
                } else {
                    for (u in keep.cut) if (waiting.add(u)) heldBack++
                    run = run.withMoves(keep.moves, keep.cut)
                }
            }
        }

        val after = LEVELS.associateWith { 0 }.toMutableMap()
        for (u in units) {
            val place = run.assigned[u] ?: u.place
            after[place] = (after[place] ?: 0) + 1
        }
        return SortPlan(run.moves, LEVELS.associateWith { after[it] ?: 0 }, heldBack, weekUsed, budget)
    }

    /**
     * Groups the copies that change place by card and from/to, with their reason, as steps 6, 8 and 9
     * say: from the highest place first, then to, then the most valuable card.
     */
    private fun group(units: List<Unit>, finalPlace: Map<Unit, String>, rules: SortRules): List<SortMove> {
        val byMove = LinkedHashMap<Triple<String, String, String>, MutableList<Unit>>()
        for (u in units) {
            val to = finalPlace[u] ?: continue
            if (to == u.place) continue
            byMove.getOrPut(Triple(u.key, u.place, to)) { mutableListOf() } += u
        }
        return byMove.map { (k, list) ->
            val (key, from, to) = k
            val first = list.first()
            val reason = when {
                to == UNSORTED_KEY -> "Bulk is full"
                // Moving down a card under the floor (or without a price): the floor says why.
                LEVELS.indexOf(to) > LEVELS.indexOf(from) && (first.price == null || first.price < rules.floor) ->
                    "under ${Money.format(rules.floor)}"
                LEVELS.indexOf(to) < LEVELS.indexOf(from) -> TradeBinderPlanner.why(first.price, first.rank, null)
                else -> "made room for more valuable cards"
            }

            SortMove(key, first.name, from, to, list.size, first.price, first.score, reason)
        }.sortedWith(MoveOrder)
    }

    /** Moves from the highest place first, then to the highest, then the most valuable card, then by card. */
    private val MoveOrder = compareBy<SortMove> { LEVELS.indexOf(it.from) }.thenBy { LEVELS.indexOf(it.to) }.thenByDescending { it.score }.thenBy { it.key }

    /** The most valuable copies first; ties by place (highest first) and then by card. */
    private val ByValue = compareByDescending<Unit> { it.price ?: -1.0 }.thenBy { LEVELS.indexOf(it.place) }.thenBy { it.key }

    /** One copy, on its way to a place. */
    private class Unit(val key: String, val name: String, val place: String, val price: Double?, val rank: Int?, val score: Double, val fixed: Boolean)

    /** One pass of filling and grouping, with what's needed to weigh and cut its moves. */
    private class Run(private val units: List<Unit>, val assigned: Map<Unit, String>, val pinned: Map<Unit, String>, val moves: List<SortMove>) {
        /** The lowest score among the [place]'s copies, or null when it holds none. */
        private fun lowest(place: String): Double? = units.filter { (assigned[it] ?: it.place) == place }.minOfOrNull { it.score }

        /**
         * How much a move matters: how far apart its card's appeal is from the lowest score among the
         * copies of the higher of its two places (0 when that place holds nothing).
         */
        fun importance(move: SortMove): Double {
            val higher = minOf(LEVELS.indexOf(move.from), LEVELS.indexOf(move.to)).coerceAtLeast(0)
            val ref = lowest(LEVELS[higher]) ?: 0.0
            return abs(ln((move.score + 0.01) / (ref + 0.01)))
        }

        private fun unitsOf(move: SortMove) = units.filter { it.key == move.key && it.place == move.from && assigned[it] == move.to }

        /** The copies of [move] that wait for next week: the least valuable, as many as were cut. */
        private fun cutUnits(move: SortMove, keep: Int) = unitsOf(move).sortedBy { it.score }.take(move.copies - keep)

        /** The moves that fit in [budget], the most important first (ties by card), and the copies they cut. */
        fun keepMostImportant(moves: List<SortMove>, budget: Int): Kept {
            var left = budget
            val kept = ArrayList<SortMove>()
            val cut = ArrayList<Unit>()
            for (move in moves.sortedWith(compareByDescending<SortMove> { importance(it) }.thenBy { it.key })) {
                val take = minOf(left, move.copies)
                left -= take
                if (take > 0) kept += move.copy(copies = take)
                if (take < move.copies) cut += cutUnits(move, take)
            }
            return Kept(kept.sortedWith(MoveOrder), cut)
        }

        /** These copies stay where they are for now. */
        fun pinsWith(extra: List<Unit>) = pinned + extra.associateWith { it.place }

        /** Only [newMoves] still move; the [cut] copies stay where they were. */
        fun withMoves(newMoves: List<SortMove>, cut: List<Unit> = emptyList()): Run {
            val kept = HashMap(assigned)
            if (cut.isNotEmpty()) for (u in cut) kept[u] = u.place
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
    /** The three binders, as far as they're set up. */
    suspend fun places(): Map<SortPlace, Binder?> {
        val dao = db.binderDao()
        return listOf(SortPlace.TOP to settings.sortTopId, SortPlace.MID to settings.sortMidId, SortPlace.BULK to settings.sortBulkId)
            .filter { it.second > 0 }.mapNotNull { (p, id) -> dao.get(id)?.let { p to it } }.toMap()
    }

    /** Makes the chosen binders and remembers the rules. */
    suspend fun setUp(choices: Map<SortPlace, BinderChoice>, rules: SortRules) {
        val ids = choices.mapValues { (_, c) -> repo.resolve(c) }
        settings.sortRules = rules
        ids[SortPlace.TOP]?.let { settings.sortTopId = it }
        ids[SortPlace.MID]?.let { settings.sortMidId = it }
        ids[SortPlace.BULK]?.let { settings.sortBulkId = it }
        settings.stampSyncedPrefs()
    }

    suspend fun suggest(): SortPlan {
        val priceType = settings.priceType.value
        val rows = db.collectionDao().allWithPrices()
        val placeOf = places().entries.mapNotNull { (p, b) -> b?.let { it.id to p.key } }.toMap()
        // Only Unsorted and the three binders take part; every other binder (a deck's, the trade binder) is
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
        val needs = db.deckDao().cardCounts().groupBy { TradeBinderPlanner.nameKey(it.name) }.mapValues { (_, v) -> v.sumOf { it.qty } }
        return CollectionSortPlanner.plan(
            stacks, needs, settings.sortRules, settings.sortSkips, settings.sortLog, settings.sortSettled,
            CollectionSortPlanner.epochDay(System.currentTimeMillis()),
        )
    }

    /**
     * Moves the [accepted] copies and remembers the [declined] moves, in one go. Once anything is
     * accepted, a card gives up its place only to one that's clearly better.
     */
    suspend fun apply(accepted: List<SortMove>, declined: List<SortMove>) = db.withTransaction {
        val bound = places().entries.mapNotNull { (p, b) -> b?.let { p.key to it.id } }.toMap()
        val today = CollectionSortPlanner.epochDay(System.currentTimeMillis())
        val lines = ArrayList<String>()
        for (move in accepted.sortedWith(compareBy<SortMove> { CollectionSortPlanner.LEVELS.indexOf(it.from) })) {
            val from = if (move.from == UNSORTED_KEY) Binder.UNSORTED else bound[move.from] ?: continue
            val to = if (move.to == UNSORTED_KEY) Binder.UNSORTED else bound[move.to] ?: continue
            if (from == to) continue
            var left = move.copies
            val stacks = db.collectionDao().all().filter { it.binderId == from && CardKind.of(it).key == move.key }
            for (s in stacks.sortedByDescending { it.quantity }) {
                if (left <= 0) break
                val n = minOf(left, s.quantity)
                repo.saveCollectionEdit(s, to, n)
                left -= n
            }
            if (left < move.copies) lines += CollectionSortPlanner.logLine(today, move.key, move.copies - left, move.from == UNSORTED_KEY)
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
}
