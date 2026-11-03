package com.mtgtrader.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.abs

/** One of the "Collection value" widget's biggest movers: the card and its change, written out. Since 1.77. */
@Serializable
data class WidgetMover(val name: String, val change: String, val up: Boolean)

/** One of the "Wishlist" widget's cards: its price and alert level, written out, and whether the level is reached. Since 1.77. */
@Serializable
data class WidgetWish(val id: Long, val name: String, val price: String, val level: String, val reached: Boolean)

/**
 * What the home-screen widgets show, worked out by the app whenever it updates prices (the widgets
 * never load prices or the database themselves). Amounts are written out in the display currency
 * of the moment. Since 1.77.
 */
@Serializable
data class WidgetSnapshot(
    val value: String,
    /** "Cardmarket trend" or "TCGplayer": the Value screen's price, named short. */
    val source: String,
    /** "+€135.67 · 7 days" for the small widget, "+€135.67 (+1.1%) this week" for the wide one; null without a week of history. */
    val changeSmall: String? = null,
    val changeWide: String? = null,
    val up: Boolean = true,
    /** The last week's daily values, oldest first, for the chart. */
    val chart: List<Double> = emptyList(),
    val movers: List<WidgetMover> = emptyList(),
    val wishes: List<WidgetWish> = emptyList(),
    val madeAt: Long = 0,
)

/** The widgets' snapshot, kept in SharedPreferences as JSON. Since 1.77. */
class WidgetData(context: Context) {
    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun save(s: WidgetSnapshot) = prefs.edit().putString(KEY, encode(s)).apply()

    /** The last snapshot, or null before the app has made one. */
    fun load(): WidgetSnapshot? = decode(prefs.getString(KEY, null))

    companion object {
        private const val KEY = "snapshot"
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(s: WidgetSnapshot): String = json.encodeToString(WidgetSnapshot.serializer(), s)
        fun decode(s: String?): WidgetSnapshot? = s?.let { runCatching { json.decodeFromString(WidgetSnapshot.serializer(), it) }.getOrNull() }

        /** "+€135.67" or "−€4.80" (the real minus sign). */
        fun signed(v: Double, money: (Double) -> String) = (if (v >= 0) "+" else "−") + money(abs(v))

        /** The small and wide widgets' change texts, from today's value and the value a week ago. */
        fun changeTexts(now: Double, weekAgo: Double, money: (Double) -> String): Pair<String, String> {
            val diff = now - weekAgo
            val pct = if (weekAgo > 0) diff / weekAgo * 100 else 0.0
            return "${signed(diff, money)} · 7 days" to "${signed(diff, money)} (%+.1f%%) this week".format(pct)
        }

        /**
         * The snapshot: the collection's value at [source] and [type] (the Value screen's big number), its change
         * since the newest of [points] at least a week old, the last week's [points] for the chart, the three cards
         * whose price moved most, and up to four wishlist cards closest to their alert level (reached ones first).
         */
        fun build(
            rows: List<CollectionRow>,
            points: List<ValuePoint>,
            source: PriceSource,
            type: PriceType,
            wishes: List<WishlistItem>,
            wishPrices: Map<Long, WishPrice>,
            basis: String,
            ctFilter: CtFilter?,
            today: LocalDate = LocalDate.now(),
            now: Long = System.currentTimeMillis(),
            money: (Double) -> String = { Money.format(it) },
        ): WidgetSnapshot {
            val value = ValueHistory.sourceTotal(rows, source, type)
            val weekAgo = points.filter { !it.day.isAfter(today.minusDays(7)) }.maxByOrNull { it.day }?.value
            val change = weekAgo?.let { changeTexts(value, it, money) }
            val chart = points.filter { it.day.isAfter(today.minusDays(7)) && it.day.isBefore(today) }.sortedBy { it.day }.map { it.value } + value
            val movers = (ValueHistory.biggestMoves(rows, up = true, count = 3) + ValueHistory.biggestMoves(rows, up = false, count = 3))
                .sortedByDescending { abs(it.change) }.take(3)
                .map { WidgetMover(it.row.item.card.name, signed(it.change, money), it.change >= 0) }
            val wished = wishes.filter { it.receivedAt == null && it.orderedAt == null }.mapNotNull { item ->
                val level = item.levelEur?.takeIf { it > 0 } ?: return@mapNotNull null
                val best = PriceAlerts.best(wishPrices[item.id], basis, ctFilter?.key(item.foil))?.first ?: return@mapNotNull null
                Triple(item, best, level)
            }.sortedBy { (_, best, level) -> best / level }.take(4).map { (item, best, level) ->
                WidgetWish(item.id, item.card.displayName, money(best), money(level), best <= level + 0.000_1)
            }
            return WidgetSnapshot(
                value = money(value),
                source = if (source == PriceSource.CARDMARKET) "Cardmarket ${type.short.lowercase()}" else source.label,
                changeSmall = change?.first,
                changeWide = change?.second,
                up = weekAgo == null || value >= weekAgo,
                chart = if (chart.size >= 2) chart else emptyList(),
                movers = movers,
                wishes = wished,
                madeAt = now,
            )
        }
    }
}
