package com.mtgtrader

import com.mtgtrader.data.AlertEvent
import com.mtgtrader.data.AlertHistory
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.77: the price-alert history on the phone: JSON, pruning, order, and the line shown for a reached alert. */
class Release177AlertHistoryTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000

    private fun event(id: Long, at: Long, price: Double = 11.9, level: Double = 12.0) = AlertEvent(
        wishId = id, cardName = "Card $id", at = at, price = price, level = level,
        onCardTrader = false, basis = "cardmarket:low", setCode = "mh2",
    )

    @Test
    fun jsonRoundTrip() {
        val events = listOf(
            event(1, now, 11.9, 12.0),
            AlertEvent(2, "Smothering Tithe", now - 1000, 13.9, 14.0, onCardTrader = true, ctSetCode = "2x2", ctCondition = "Slightly Played", ctLanguage = "en", basis = "tcgplayer"),
            AlertEvent(3, "Arcane Signet", now - 2000, 0.55, 0.6, onCardTrader = false, basis = "cardmarket:trend"),
        )
        assertEquals(events, AlertHistory.decode(AlertHistory.encode(events)))
        assertEquals(emptyList<AlertEvent>(), AlertHistory.decode(null))
        assertEquals(emptyList<AlertEvent>(), AlertHistory.decode("<html>Not JSON</html>"))
    }

    @Test
    fun prunedToTwoHundredLines() {
        val events = (1..201).map { event(it.toLong(), now - it * 1000L) }
        val pruned = AlertHistory.prune(events, now)
        assertEquals(200, pruned.size)
        // The newest line stays, the oldest goes.
        assertEquals(1L, pruned.first().wishId)
        assertEquals(200L, pruned.last().wishId)
    }

    @Test
    fun olderThan366DaysDropped() {
        val events = listOf(
            event(1, now),
            event(2, now - 366 * day),   // exactly a year old: kept
            event(3, now - 366 * day - 1), // older: dropped
            event(4, now - 400 * day),
        )
        assertEquals(listOf(1L, 2L), AlertHistory.prune(events, now).map { it.wishId })
    }

    @Test
    fun newestFirst() {
        val events = listOf(event(1, now - 5000), event(2, now), event(3, now - 1000))
        assertEquals(listOf(2L, 3L, 1L), AlertHistory.prune(events, now).map { it.wishId })
    }

    @Test
    fun cardmarketLine() {
        val e = AlertEvent(1, "Ragavan, Nimble Pilferer", 0, 11.90, 12.0, onCardTrader = false, basis = "cardmarket:low", setCode = "mh2")
        assertEquals(
            "Reached €11.90 on 2 Nov · your level €12.00 · Cardmarket's lowest listing (MH2)",
            AlertHistory.line(e, { "2 Nov" }, { "€%.2f".format(it) }),
        )
    }

    @Test
    fun cardTraderLine() {
        val e = AlertEvent(2, "Smothering Tithe", 0, 13.90, 14.0, onCardTrader = true, ctSetCode = "2x2", ctCondition = "Slightly Played", ctLanguage = "en", basis = "cardmarket:low")
        assertEquals(
            "Reached €13.90 on 2 Nov · your level €14.00 · CardTrader (2X2, SP, EN)",
            AlertHistory.line(e, { "2 Nov" }, { "€%.2f".format(it) }),
        )
    }
}
