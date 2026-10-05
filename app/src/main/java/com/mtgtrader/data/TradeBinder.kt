package com.mtgtrader.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import kotlin.math.roundToInt

/** A trade binder suggestion the user turned down, so it isn't suggested again. Since 1.18. */
@Entity(tableName = "trade_skips")
data class TradeSkip(@PrimaryKey val key: String, val at: Long = System.currentTimeMillis())

@Dao
interface TradeSkipDao {
    @Query("SELECT `key` FROM trade_skips")
    suspend fun keys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(rows: List<TradeSkip>)

    /** Forgets turned-down suggestions (not the copies picked with Swap). */
    @Query("DELETE FROM trade_skips WHERE `key` NOT LIKE 'prefer|%'")
    suspend fun clear()

    @Query("DELETE FROM trade_skips WHERE `key` LIKE :prefix || '%'")
    suspend fun deletePrefix(prefix: String)
}

/**
 * What the trade binder may hold. With [keepBestForDecks], cards a deck uses offer their cheapest
 * spare copies and the best (most valuable, fanciest) stay home; [keepBestAlways] does so for every
 * card. Otherwise the most valuable spare copy is offered.
 */
data class TradeBinderRules(
    val maxCards: Int = 100,
    val keepOne: Boolean = true,
    val minValue: Double = 1.0,
    val keepBestForDecks: Boolean = true,
    val keepBestAlways: Boolean = false,
)

/** One copy-kind of a card: the same printing, finish, condition and language. */
data class CardKind(val scryfallId: String, val foil: Boolean, val etched: Boolean, val condition: String, val language: String) {
    val key get() = "$scryfallId|$foil|$etched|$condition|$language"

    companion object {
        fun of(i: CollectionItem) = CardKind(i.card.scryfallId, i.foil, i.etched, i.condition, i.language)
    }
}

/** A suggested change to the trade binder: [copies] of a card in or out, and why. */
data class TradeChange(
    val add: Boolean,
    val kind: CardKind,
    val card: CardRef,
    val copies: Int,
    val unitPrice: Double?,
    val reason: String,
    val score: Double,
) {
    val skipKey get() = (if (add) "add|" else "remove|") + kind.key
}

/**
 * Works out the trade binder: the spare copies (beyond what the decks use, and the one copy to keep
 * if asked) worth the most to trade, by value and by how much Commander players want them (EDHREC
 * rank), with a nudge for rising prices. The result is a list of changes against what the binder
 * holds now, for the user to accept or not. Since 1.18.
 */
object TradeBinderPlanner {
    /** How much popularity in Commander adds to a card's trade appeal. */
    fun popularity(rank: Int?): Double = when {
        rank == null -> 0.0
        rank <= 100 -> 1.0
        rank <= 500 -> 0.6
        rank <= 2_000 -> 0.35
        rank <= 8_000 -> 0.15
        else -> 0.0
    }

    fun score(price: Double, rank: Int?, trendPct: Double?): Double =
        price * (1 + popularity(rank)) * (1 + ((trendPct ?: 0.0) / 100).coerceIn(-0.2, 0.25))

    fun why(price: Double?, rank: Int?, trendPct: Double?): String = listOfNotNull(
        price?.let { "€%.2f".format(it) },
        rank?.takeIf { it <= 8_000 }?.let { r -> "top ${listOf(100, 500, 1_000, 2_000, 5_000, 8_000).first { r <= it }} on EDHREC" },
        trendPct?.takeIf { it >= 5 }?.let { "▲ ${it.roundToInt()}%" },
    ).joinToString(" · ")

    fun nameKey(name: String) = name.substringBefore(" // ").trim().lowercase()

    /** The skip-table key remembering that, for this card, the user wants [kind] in the binder (Swap). */
    fun preferKey(name: String, kind: CardKind) = "prefer|${nameKey(name)}|${kind.key}"

    fun plan(
        rows: List<CollectionRow>,
        deckNeeds: Map<String, Int>,
        wishlist: Set<String>,
        tradeBinderId: Long?,
        rules: TradeBinderRules,
        priceType: PriceType,
        skipped: Set<String>,
    ): List<TradeChange> {
        // Swap picks: card name → the copy-kind to offer first.
        val preferred = skipped.filter { it.startsWith("prefer|") }.associate { k ->
            val rest = k.removePrefix("prefer|")
            rest.substringBefore('|') to rest.substringAfter('|')
        }
        data class Kind(val kind: CardKind, val card: CardRef, val total: Int, val inBinder: Int, val price: Double?, val rank: Int?, val trend: Double?, val land: Boolean, val type: String)
        val kinds = rows.groupBy { CardKind.of(it.item) }.map { (k, list) ->
            val r = list.first()
            Kind(
                k, r.item.card, list.sumOf { it.item.quantity }, list.filter { it.item.binderId == tradeBinderId }.sumOf { it.item.quantity },
                r.unitPrice(priceType), r.info?.edhrecRank, r.trend?.pct, r.info?.isLand == true, r.info?.typeLine.orEmpty(),
            )
        }
        // Copies of each card that may go in the binder.
        val desired = HashMap<CardKind, Int>()
        val reasonOut = HashMap<CardKind, String>()
        val candidates = mutableListOf<Triple<Kind, Int, Double>>() // kind, copies, score per copy
        for ((name, group) in kinds.groupBy { nameKey(it.card.name) }) {
            val owned = group.sumOf { it.total }
            val needed = deckNeeds[name] ?: 0
            val keep = maxOf(needed, if (rules.keepOne) 1 else 0)
            var spare = owned - keep
            val blocked = when {
                DeckToCollection.isBasic(name) -> "basic land"
                name in wishlist -> "on your wishlist"
                "Token" in group.first().type || "Emblem" in group.first().type -> "token"
                else -> null
            }
            // Which copies go first: the one picked with Swap; then, keeping the best at home, the cheapest;
            // otherwise the copies already in the binder (so it doesn't churn), then the most valuable.
            val keepBest = rules.keepBestAlways || (rules.keepBestForDecks && needed > 0)
            val pick = preferred[name]
            val order = compareByDescending<Kind> { it.kind.key == pick }.then(
                if (keepBest) compareBy { it.price ?: Double.MAX_VALUE }
                else compareByDescending<Kind> { it.inBinder > 0 }.thenByDescending { it.price ?: 0.0 }
            )
            var offeredOther = false
            for (k in group.sortedWith(order)) {
                val price = k.price
                val why = when {
                    blocked != null -> blocked
                    spare <= 0 && offeredOther && pick != null && k.kind.key != pick -> "you picked another copy to trade"
                    spare <= 0 && offeredOther && keepBest -> "a cheaper copy goes in instead; the best stays home"
                    spare <= 0 -> if (needed > 0) "your decks use ${if (needed == 1) "it" else "$needed"}" else "the copy you keep"
                    price == null || price < rules.minValue -> "under €%.2f now".format(rules.minValue)
                    else -> null
                }
                if (why != null) {
                    if (k.inBinder > 0) reasonOut[k.kind] = why
                    continue
                }
                val n = minOf(spare, k.total)
                spare -= n
                offeredOther = true
                candidates += Triple(k, n, score(price!!, k.rank, k.trend))
            }
        }
        // Turned-down removals stay in; turned-down additions stay out.
        val pinned = kinds.filter { it.inBinder > 0 && "remove|${it.kind.key}" in skipped }
        var room = rules.maxCards - pinned.sumOf { it.inBinder }
        pinned.forEach { desired[it.kind] = it.inBinder }
        for ((k, n, _) in candidates.filter { "add|${it.first.kind.key}" !in skipped || it.first.inBinder > 0 }.sortedByDescending { it.third }) {
            if (k.kind in desired) continue
            val take = minOf(n, room).coerceAtLeast(0)
            if (take <= 0) {
                if (k.inBinder > 0) reasonOut.putIfAbsent(k.kind, "made room for more valuable cards")
                continue
            }
            desired[k.kind] = take
            room -= take
            if (take < k.inBinder) reasonOut.putIfAbsent(k.kind, "made room for more valuable cards")
        }
        val changes = mutableListOf<TradeChange>()
        for (k in kinds) {
            val want = desired[k.kind] ?: 0
            val diff = want - k.inBinder
            if (diff == 0) continue
            val s = k.price?.let { score(it, k.rank, k.trend) } ?: 0.0
            changes += if (diff > 0) {
                if ("add|${k.kind.key}" in skipped) continue
                TradeChange(true, k.kind, k.card, diff, k.price, why(k.price, k.rank, k.trend), s)
            } else {
                if ("remove|${k.kind.key}" in skipped) continue
                TradeChange(false, k.kind, k.card, -diff, k.price, reasonOut[k.kind] ?: "made room for more valuable cards", s)
            }
        }
        return changes.sortedWith(compareByDescending<TradeChange> { it.add }.thenByDescending { it.score })
    }
}

/** Builds and updates the trade binder from [TradeBinderPlanner]'s suggestions. */
class TradeBinder(private val db: AppDatabase, private val repo: MtgRepository, private val settings: Settings) {
    /** The trade binder, if made: the remembered one, or one called "Trade binder". */
    suspend fun binder(): Binder? =
        settings.tradeBinderId.takeIf { it > 0 }?.let { db.binderDao().get(it) } ?: db.binderDao().byName(NAME)

    suspend fun suggest(): List<TradeChange> {
        val rows = db.collectionDao().allWithPrices()
        val needs = db.deckDao().cardCounts().groupBy { it.name.substringBefore(" // ").trim().lowercase() }.mapValues { (_, v) -> v.sumOf { it.qty } }
        val wish = db.wishlistDao().all().map { it.card.name.substringBefore(" // ").trim().lowercase() }.toSet()
        return TradeBinderPlanner.plan(rows, needs, wish, binder()?.id, settings.tradeRules, settings.priceType.value, db.tradeSkipDao().keys().toSet())
    }

    /** Swap: offer [kind] of this card instead of the suggested copy ([insteadOf]), from now on. */
    suspend fun prefer(name: String, kind: CardKind, insteadOf: CardKind) {
        db.tradeSkipDao().deletePrefix("prefer|${TradeBinderPlanner.nameKey(name)}|")
        db.tradeSkipDao().putAll(listOf(TradeSkip(TradeBinderPlanner.preferKey(name, kind)), TradeSkip("add|${insteadOf.key}")))
    }

    /**
     * Applies the [accepted] changes (creating the binder if needed) and remembers the [declined]
     * ones so they aren't suggested again. Copies taken out go back to the binder holding most
     * other copies of the card, else to Unsorted.
     */
    suspend fun apply(accepted: List<TradeChange>, declined: List<TradeChange>) = db.withTransaction {
        val binderId = binder()?.id ?: repo.createBinder(NAME)
        settings.tradeBinderId = binderId
        // A binder named after a deck holds that deck's cards: take spare copies from Unsorted and other binders first.
        val deckNames = db.deckDao().all().map { it.name.lowercase() }.toSet()
        val deckBinders = db.binderDao().all().filter { it.name.lowercase() in deckNames }.map { it.id }.toSet()
        fun source(s: CollectionItem) = when (s.binderId) {
            Binder.UNSORTED -> 0
            in deckBinders -> 2
            else -> 1
        }
        for (c in accepted) {
            val stacks = db.collectionDao().all().filter { CardKind.of(it) == c.kind }
            var left = c.copies
            if (c.add) {
                for (s in stacks.filter { it.binderId != binderId }.sortedWith(compareBy<CollectionItem> { source(it) }.thenByDescending { it.quantity })) {
                    if (left <= 0) break
                    val n = minOf(left, s.quantity)
                    repo.saveCollectionEdit(s, binderId, n)
                    left -= n
                }
            } else {
                val home = db.collectionDao().all().filter { it.card.name == c.card.name && it.binderId != binderId }
                    .groupBy { it.binderId }.maxByOrNull { (_, v) -> v.sumOf { it.quantity } }?.key ?: Binder.UNSORTED
                for (s in stacks.filter { it.binderId == binderId }) {
                    if (left <= 0) break
                    val n = minOf(left, s.quantity)
                    repo.saveCollectionEdit(s, home, n)
                    left -= n
                }
            }
        }
        if (declined.isNotEmpty()) db.tradeSkipDao().putAll(declined.map { TradeSkip(it.skipKey) })
        binderId
    }

    /** Suggests the turned-down cards again. */
    suspend fun forgetSkipped() = db.tradeSkipDao().clear()

    companion object {
        const val NAME = "Trade binder"
    }
}
