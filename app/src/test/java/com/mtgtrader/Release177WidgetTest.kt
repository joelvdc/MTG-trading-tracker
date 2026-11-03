package com.mtgtrader

import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.PriceAlerts
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceSource
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.ValuePoint
import com.mtgtrader.data.WidgetData
import com.mtgtrader.data.WishPrice
import com.mtgtrader.data.WishlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.77: the home-screen widgets' snapshot. */
class Release177WidgetTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val basis = PriceAlerts.basis(PriceSource.CARDMARKET, PriceType.LOW)
    private fun money(v: Double) = "€%.2f".format(v)

    private fun ref(name: String) = CardRef(name.lowercase(), name, "m21", "Core Set 2021", "1", "rare", null, null, null, null, true, true)

    /** [qty] copies at Cardmarket's trend [trend], with 30-day average [avg30]. */
    private fun row(name: String, qty: Int, trend: Double, avg30: Double? = null) = CollectionRow(
        CollectionItem(card = ref(name), foil = false, quantity = qty),
        PriceEntity(1, trend, trend, trend, trend, trend, avg30, null, null, null, null, null, null),
    )

    private fun point(daysAgo: Long, v: Double) = ValuePoint(today.minusDays(daysAgo), v, 1)

    private fun build(
        rows: List<CollectionRow> = listOf(row("Sol Ring", 1, 100.0)),
        points: List<ValuePoint> = emptyList(),
        wishes: List<WishlistItem> = emptyList(),
        prices: Map<Long, WishPrice> = emptyMap(),
    ) = WidgetData.build(rows, points, PriceSource.CARDMARKET, PriceType.TREND, wishes, prices, basis, null, today, 0, ::money)

    @Test fun weekChange() {
        // The newest point at least a week old (8 days ago) counts, not the older one.
        val s = build(points = listOf(point(10, 50.0), point(8, 80.0), point(3, 90.0), point(1, 95.0)))
        assertEquals("€100.00", s.value)
        assertEquals("+€20.00 · 7 days", s.changeSmall)
        assertEquals("+€20.00 (+25.0%) this week", s.changeWide)
        assertTrue(s.up)
        // The chart: this week's days before today, and today's value.
        assertEquals(listOf(90.0, 95.0, 100.0), s.chart)
        assertEquals("Cardmarket trend", s.source)
    }

    @Test fun noChangeWithoutAWeekOfHistory() {
        val s = build(points = listOf(point(3, 90.0)))
        assertNull(s.changeSmall)
        assertNull(s.changeWide)
        assertEquals(listOf(90.0, 100.0), s.chart)
        // Only today: no chart.
        assertTrue(build().chart.isEmpty())
    }

    @Test fun fallingValue() {
        val (small, wide) = WidgetData.changeTexts(90.0, 100.0, ::money)
        assertEquals("−€10.00 · 7 days", small)
        assertEquals("−€10.00 (-10.0%) this week", wide)
        assertFalse(build(points = listOf(point(7, 120.0))).up)
    }

    @Test fun biggestMoversEitherWay() {
        val rows = listOf(
            row("Up a little", 1, 11.0, 10.0),
            row("Up a lot", 1, 20.0, 10.0),
            row("Down a lot", 1, 5.0, 12.0),
            row("Down a little", 1, 9.5, 10.0),
            row("Flat", 1, 3.0, 3.0),
        )
        val m = build(rows = rows).movers
        assertEquals(listOf("Up a lot", "Down a lot", "Up a little"), m.map { it.name })
        assertEquals(listOf("+€10.00", "−€7.00", "+€1.00"), m.map { it.change })
        assertEquals(listOf(true, false, true), m.map { it.up })
    }

    @Test fun wishlistClosestFirst() {
        fun wish(id: Long, name: String, level: Double?, ordered: Long? = null, received: Long? = null) =
            WishlistItem(id = id, card = ref(name), anyPrinting = false, alertBelow = level, orderedAt = ordered, receivedAt = received)
        val wishes = listOf(
            wish(1, "Rhystic Study", 30.0),
            wish(2, "Smothering Tithe", 12.0),
            wish(3, "Esper Sentinel", 8.0),
            wish(4, "No level", null),
            wish(5, "Ordered", 10.0, ordered = 1),
            wish(6, "Received", 10.0, received = 1),
            wish(7, "No price yet", 10.0),
        )
        val prices = mapOf(1L to 38.5, 2L to 11.9, 3L to 9.4, 4L to 1.0, 5L to 1.0, 6L to 1.0)
            .mapValues { (id, p) -> WishPrice(id, basis = basis, price = p) }
        val w = build(wishes = wishes, prices = prices).wishes
        assertEquals(listOf("Smothering Tithe", "Esper Sentinel", "Rhystic Study"), w.map { it.name })
        assertEquals(listOf(true, false, false), w.map { it.reached })
        assertEquals("€11.90", w[0].price)
        assertEquals("€12.00", w[0].level)
        assertEquals(2L, w[0].id)
    }

    @Test fun jsonRoundTrip() {
        val s = build(points = listOf(point(8, 80.0), point(1, 95.0)), rows = listOf(row("Up", 2, 20.0, 10.0)))
        assertEquals(s, WidgetData.decode(WidgetData.encode(s)))
        assertNull(WidgetData.decode("not json"))
        assertNull(WidgetData.decode(null))
    }
}
