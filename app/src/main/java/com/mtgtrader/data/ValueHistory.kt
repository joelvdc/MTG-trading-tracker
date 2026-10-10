package com.mtgtrader.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** One day's collection value in the chosen price type. */
data class ValuePoint(val day: LocalDate, val value: Double, val cards: Int)

/** A card whose price moved: Cardmarket's trend vs its 30-day average, times the copies you own. */
data class PriceMove(val row: CollectionRow, val change: Double, val pct: Double)

/**
 * The collection's value over time: once a day (when the app opens and after each price update)
 * the total is worked out for every price type and kept, so switching the price type keeps the
 * history. Since 1.16. Each binder's value is kept too (since 1.25), as extra "b:<binder>:<type>" entries
 * that older versions ignore.
 */
class ValueHistory(private val db: AppDatabase) {
    private val dao = db.valueHistoryDao()
    private val json = Json

    /** Saves today's value (replacing an earlier one from today). Nothing to save without prices. */
    suspend fun record(today: LocalDate = LocalDate.now()) {
        if (db.priceDao().countNow() == 0) return
        val rows = db.collectionDao().allWithPrices()
        if (rows.isEmpty()) return
        val binders = db.binderDao().all().map { it.id } + Binder.UNSORTED
        val values = snapshotValues(rows, binders)
        dao.put(ValueSnapshot(today.toString(), rows.sumOf { it.item.quantity }, json.encodeToString(values)))
    }

    /**
     * The whole collection's value per day, or one [binder]'s (days before 1.25 have no binder values),
     * at [source] (days before 1.29 only have Cardmarket's).
     */
    fun observe(type: PriceType, binder: Long? = null, source: PriceSource = PriceSource.CARDMARKET): Flow<List<ValuePoint>> = dao.observeAll().map { rows ->
        rows.mapNotNull { r ->
            val values = runCatching { json.decodeFromString<Map<String, Double>>(r.values) }.getOrNull() ?: return@mapNotNull null
            point(LocalDate.parse(r.day), r.cards, values, sourceKey(type, source), binder)
        }
    }

    companion object {
        fun binderKey(binder: Long, what: String) = "b:$binder:$what"

        /** "trend" for Cardmarket (as before 1.29), "trend@tcgplayer" for the others. */
        fun sourceKey(type: PriceType, source: PriceSource) = if (source == PriceSource.CARDMARKET) type.key else "${type.key}@${source.key}"

        /** What Card Kingdom would pay for the lot (its buylist). */
        const val CK_PAYS = "pays@cardkingdom"

        /**
         * The sources with prices for any of [rows] (needs the dollar rate too): only these get a value
         * for the day, so a day saved before their prices loaded doesn't show Cardmarket's as theirs.
         */
        fun sourcesWithPrices(rows: List<CollectionRow>): List<PriceSource> = PriceSource.entries.filter { s ->
            s == PriceSource.CARDMARKET || rows.any { it.priceAt(s, PriceType.TREND) != null }
        }

        /** A day's numbers: the total per price type, and per binder its card count and value per price type. */
        fun snapshotValues(rows: List<CollectionRow>, binders: List<Long>, sources: List<PriceSource> = sourcesWithPrices(rows)): Map<String, Double> {
            val out = LinkedHashMap<String, Double>()
            fun totals(rs: List<CollectionRow>, key: (String) -> String) {
                for (s in sources) PriceType.entries.forEach { t -> out[key(sourceKey(t, s))] = sourceTotal(rs, s, t) }
                if (PriceSource.CARD_KINGDOM in sources) out[key(CK_PAYS)] = cardKingdomPays(rs)
            }
            totals(rows) { it }
            val byBinder = rows.groupBy { it.item.binderId }
            // Every binder, an empty one too, so its chart drops to zero instead of stopping.
            for (b in (binders + byBinder.keys).distinct()) {
                val rs = byBinder[b].orEmpty()
                out[binderKey(b, "cards")] = rs.sumOf { it.item.quantity }.toDouble()
                totals(rs) { binderKey(b, it) }
            }
            return out
        }

        /** The value at [source], with Cardmarket's [type] price for cards it has none for. Since 1.29. */
        fun sourceTotal(rows: List<CollectionRow>, source: PriceSource, type: PriceType) =
            rows.sumOf { r -> (r.priceAt(source, type) ?: r.cardmarketPrice(type) ?: 0.0) * r.item.quantity }

        /** Copies [source] has a price for. */
        fun covered(rows: List<CollectionRow>, source: PriceSource) = rows.filter { it.priceAt(source, PriceType.TREND) != null }.sumOf { it.item.quantity }

        /** What Card Kingdom would pay for every copy it buys. */
        fun cardKingdomPays(rows: List<CollectionRow>) =
            rows.sumOf { r -> (Pricing.cardKingdomPays(r.item.card.scryfallId, r.item.finish) ?: 0.0) * r.item.quantity }

        /** Reads one point back from a day's numbers; null when the day has none for [binder]. */
        fun point(day: LocalDate, cards: Int, values: Map<String, Double>, type: PriceType, binder: Long?): ValuePoint? =
            point(day, cards, values, type.key, binder)

        fun point(day: LocalDate, cards: Int, values: Map<String, Double>, key: String, binder: Long?): ValuePoint? =
            if (binder == null) values[key]?.let { ValuePoint(day, it, cards) }
            else values[binderKey(binder, key)]?.let { v -> ValuePoint(day, v, values[binderKey(binder, "cards")]?.toInt() ?: 0) }

        fun totalValue(rows: List<CollectionRow>, type: PriceType) = rows.sumOf { (it.unitPrice(type) ?: 0.0) * it.item.quantity }

        /** The cards that gained (or lost) the most value lately, in EUR over all copies you own. */
        fun biggestMoves(rows: List<CollectionRow>, up: Boolean, count: Int = 5): List<PriceMove> =
            rows.mapNotNull { r ->
                val p = r.price?.toSet(r.item.foil) ?: return@mapNotNull null
                val trend = p.trend ?: return@mapNotNull null
                val avg30 = p.avg30?.takeIf { it > 0 } ?: return@mapNotNull null
                PriceMove(r, (trend - avg30) * r.item.quantity, (trend - avg30) / avg30 * 100)
            }
                .filter { if (up) it.change > 0 else it.change < 0 }
                .sortedBy { if (up) -it.change else it.change }
                .take(count)
    }
}
