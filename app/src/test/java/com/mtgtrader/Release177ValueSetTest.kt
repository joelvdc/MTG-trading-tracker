package com.mtgtrader

import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionFilter
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceSource
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.ValueHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.77: the value screen's "Value by set" section. */
class Release177ValueSetTest {

    private fun ref(name: String, set: String, setName: String = "", eur: Double? = null) =
        CardRef("$name|$set|1", name, set, setName, "1", "rare", null, null, eur, eur, true, true)

    /** A stack of [qty] copies at Cardmarket's trend price [trend] with 30-day average [avg30]; no price guide entry when [trend] is null. */
    private fun row(name: String, set: String, qty: Int, trend: Double? = null, avg30: Double? = null, setName: String = "", eur: Double? = null) =
        CollectionRow(
            CollectionItem(card = ref(name, set, setName, eur), foil = false, quantity = qty),
            if (trend == null) null else PriceEntity(1, trend, trend, trend, trend, trend, avg30, null, null, null, null, null, null),
        )

    @Test fun totalsPerSetWithQuantities() {
        val rows = listOf(
            row("A", "aaa", 2, trend = 10.0),
            row("B", "aaa", 3, trend = 4.0),
            row("C", "bbb", 1, trend = 1.0),
        )
        val sets = ValueHistory.bySet(rows, PriceSource.CARDMARKET, PriceType.TREND)
        assertEquals(2, sets.size)
        val aaa = sets.single { it.setCode == "aaa" }
        assertEquals(5, aaa.cards)
        assertEquals(32.0, aaa.value, 1e-9) // 2×10 + 3×4
        assertEquals("AAA", aaa.name) // no set name on the cards: the upper-case set code
    }

    @Test fun sortedByValueDescendingThenName() {
        val rows = listOf(
            row("A", "bbb", 1, trend = 5.0),
            row("B", "aaa", 1, trend = 5.0),
            row("C", "ccc", 1, trend = 9.0),
        )
        assertEquals(
            listOf("ccc", "aaa", "bbb"),
            ValueHistory.bySet(rows, PriceSource.CARDMARKET, PriceType.TREND).map { it.setCode },
        )
    }

    @Test fun setValuesAddUpToTheSourceTotal() {
        val rows = listOf(
            row("A", "aaa", 2, trend = 10.0, avg30 = 8.0),
            row("B", "bbb", 1, trend = 3.0, avg30 = null),
            row("C", "ccc", 4, eur = 1.5), // no price guide entry: Scryfall's own EUR price
        )
        val total = ValueHistory.sourceTotal(rows, PriceSource.CARDMARKET, PriceType.TREND)
        assertEquals(total, ValueHistory.bySet(rows, PriceSource.CARDMARKET, PriceType.TREND).sumOf { it.value }, 1e-9)
        // A source without prices of its own falls back to Cardmarket's, so its total is the same.
        assertEquals(
            ValueHistory.sourceTotal(rows, PriceSource.TCGPLAYER, PriceType.TREND),
            ValueHistory.bySet(rows, PriceSource.TCGPLAYER, PriceType.TREND).sumOf { it.value },
            1e-9,
        )
    }

    @Test fun changeSummedOverCopiesAndNullWhenNoRowHasOne() {
        val rows = listOf(
            row("A", "aaa", 2, trend = 12.0, avg30 = 10.0), // +2.00 a copy, two copies: +4.00
            row("B", "aaa", 1, trend = 9.0, avg30 = 10.0),  // −1.00
            row("C", "bbb", 3, trend = 5.0, avg30 = null),  // no 30-day average: no change
        )
        val sets = ValueHistory.bySet(rows, PriceSource.CARDMARKET, PriceType.TREND)
        assertEquals(3.0, sets.single { it.setCode == "aaa" }.change!!, 1e-9)
        assertNull(sets.single { it.setCode == "bbb" }.change)
    }

    @Test fun setCodesGroupCaseInsensitively() {
        val rows = listOf(row("A", "LTR", 1, trend = 5.0), row("B", "ltr", 2, trend = 1.0))
        val ltr = ValueHistory.bySet(rows, PriceSource.CARDMARKET, PriceType.TREND).single()
        assertEquals("ltr", ltr.setCode)
        assertEquals(3, ltr.cards)
        assertEquals(7.0, ltr.value, 1e-9)
    }

    @Test fun theSetKeyMatchesTheStatsTopSetsFilter() {
        val rows = listOf(row("A", "LTR", 1, trend = 5.0), row("B", "mkm", 1, trend = 2.0))
        val ltr = ValueHistory.bySet(rows, PriceSource.CARDMARKET, PriceType.TREND).single { it.setCode == "ltr" }
        val filter = CollectionFilter(sets = setOf(ltr.setCode)) // the same key CollectionStats' topSets use
        assertTrue(filter.matches(rows.single { it.item.card.setCode == "LTR" }, PriceType.TREND, emptySet()))
        assertFalse(filter.matches(rows.single { it.item.card.setCode == "mkm" }, PriceType.TREND, emptySet()))
    }

    @Test fun theSetNameComesFromTheCards() {
        val rows = listOf(row("A", "ltr", 1, trend = 5.0, setName = "The Lord of the Rings: Tales of Middle-earth"))
        assertEquals(
            "The Lord of the Rings: Tales of Middle-earth",
            ValueHistory.bySet(rows, PriceSource.CARDMARKET, PriceType.TREND).single().name,
        )
    }

    @Test fun emptyCollectionGivesNoSets() {
        assertTrue(ValueHistory.bySet(emptyList(), PriceSource.CARDMARKET, PriceType.TREND).isEmpty())
    }
}
