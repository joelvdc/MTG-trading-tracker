package com.mtgtrader

import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.ValueHistory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BinderValueTest {
    private fun owned(price: Double, qty: Int, binder: Long): CollectionRow = CollectionRow(
        CollectionItem(card = CardRef("id-$price", "X", "tst", "Test", "1", "rare", null, null, price, price, true, false), foil = false, quantity = qty, binderId = binder),
        null,
    )

    @Test fun keepsEachBindersValue() {
        val rows = listOf(owned(10.0, 2, 5L), owned(1.0, 3, Binder.UNSORTED))
        val values = ValueHistory.snapshotValues(rows, binders = listOf(5L, 7L, Binder.UNSORTED))
        val day = LocalDate.of(2026, 10, 9)
        assertEquals(23.0, ValueHistory.point(day, 5, values, PriceType.TREND, null)!!.value, 1e-9)
        val five = ValueHistory.point(day, 5, values, PriceType.TREND, 5L)!!
        assertEquals(20.0, five.value, 1e-9)
        assertEquals(2, five.cards)
        assertEquals(0.0, ValueHistory.point(day, 5, values, PriceType.TREND, 7L)!!.value, 1e-9)
        assertNull(ValueHistory.point(day, 5, mapOf("trend" to 23.0), PriceType.TREND, 5L))
    }

    @Test fun olderVersionsStillReadTheTotal() {
        // Versions before 1.25 read the saved text as a flat list of numbers and pick their price type from it.
        val text = Json.encodeToString(ValueHistory.snapshotValues(listOf(owned(10.0, 2, 5L)), listOf(5L)))
        assertEquals(20.0, Json.decodeFromString<Map<String, Double>>(text)["trend"]!!, 1e-9)
    }
}
