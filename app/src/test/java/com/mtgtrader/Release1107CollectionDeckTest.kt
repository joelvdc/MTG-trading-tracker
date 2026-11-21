package com.mtgtrader

import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionFilter
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.ActiveDeckCard
import com.mtgtrader.data.DeckUses
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.SortField
import com.mtgtrader.data.SortLevel
import com.mtgtrader.data.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.107: the collection's deck filter, Deck column order and Deck sort (as the website's collection). */
class Release1107CollectionDeckTest {
    private var id = 1L

    private fun row(name: String, price: Double = 1.0, set: String = "tst", number: String = "1"): CollectionRow {
        val ref = CardRef("id$id", name, set, set.uppercase(), number, "rare", null, 1, null, null, true, false)
        return CollectionRow(
            CollectionItem(id = id++, card = ref, foil = false, quantity = 1),
            PriceEntity(1, price, price, price, price, price, price, null, null, null, null, null, null),
        )
    }

    // "Atraxa" and "Burn" use Rhystic Study; "Burn" uses Lightning Bolt; Sol Ring and Brainstorm are in no deck.
    private val deckUse = DeckUses.of(
        listOf(
            ActiveDeckCard("Burn", "Rhystic Study"),
            ActiveDeckCard("Burn", "Lightning Bolt"),
            ActiveDeckCard("Atraxa", "Rhystic Study"),
            ActiveDeckCard("Burn", "Lightning Bolt"),
        ),
    )

    private val rows = listOf(
        row("Sol Ring"),
        row("Rhystic Study", 30.0),
        row("Lightning Bolt"),
        row("Brainstorm"),
    )

    private fun names(list: List<CollectionRow>) = list.map { it.item.card.name }

    private fun sort(field: SortField, reversed: Boolean = false) =
        names(rows.sortedWith(SortSpec(listOf(SortLevel(field, reversed))).comparator(PriceType.TREND, deckUse)))

    @Test
    fun theDeckMapListsEachCardsDecksAToZ() {
        assertEquals(listOf("Atraxa", "Burn"), deckUse["rhystic study"])
        assertEquals(listOf("Burn"), deckUse["lightning bolt"])
        assertEquals(null, deckUse["sol ring"])
        assertEquals(listOf("Burn"), DeckUses.decksOf("lightning bolt", deckUse))
        assertEquals("Atraxa", DeckUses.deckOf("rhystic study", deckUse))
        assertTrue(DeckUses.deckUses("Rhystic Study".lowercase(), "burn", deckUse))
        assertFalse(DeckUses.deckUses("sol ring", "Burn", deckUse))
    }

    @Test
    fun theDeckSortPutsCardsInNoDeckLastBothWays() {
        // A to Z by the first deck: Atraxa's Rhystic Study, then Burn's copies (by name), then the cards in no deck.
        assertEquals(listOf("Rhystic Study", "Lightning Bolt", "Brainstorm", "Sol Ring"), sort(SortField.DECK))
        // Z to A: the direction comes before the names, the cards in no deck stay last.
        assertEquals(listOf("Lightning Bolt", "Rhystic Study", "Brainstorm", "Sol Ring"), sort(SortField.DECK, reversed = true))
        // The deck is only the first thing sorted by: a second level (here value, dearest first) breaks its ties.
        val byDeckThenValue = SortSpec(listOf(SortLevel(SortField.DECK), SortLevel(SortField.VALUE)))
        assertEquals(
            listOf("Rhystic Study", "Lightning Bolt", "Brainstorm", "Sol Ring"),
            names(rows.sortedWith(byDeckThenValue.comparator(PriceType.TREND, deckUse))),
        )
    }

    @Test
    fun theDeckSortWithoutDecksIsByName() {
        val plain = names(rows.sortedWith(SortSpec(listOf(SortLevel(SortField.DECK))).comparator(PriceType.TREND)))
        assertEquals(listOf("Brainstorm", "Lightning Bolt", "Rhystic Study", "Sol Ring"), plain)
        assertEquals("A to Z, cards in no deck last", SortField.DECK.forward)
        assertEquals("Z to A, cards in no deck last", SortField.DECK.backward)
        // It survives the sort's saved form (the collection keeps its sort in the settings).
        val spec = SortSpec(listOf(SortLevel(SortField.DECK, reversed = true)))
        assertEquals(spec, SortSpec.decode(spec.encode()))
    }

    @Test
    fun theDeckFilterLeavesOneDecksCards() {
        val burn = CollectionFilter(deck = "Burn")
        assertEquals(listOf("Rhystic Study", "Lightning Bolt"), names(rows.filter { burn.matches(it, PriceType.TREND, emptySet(), deckUse) }))
        // The deck's name in any letter case.
        assertEquals(listOf("Rhystic Study", "Lightning Bolt"), names(rows.filter { burn.copy(deck = "burn").matches(it, PriceType.TREND, emptySet(), deckUse) }))
        // Atraxa uses only the Rhystic Study.
        assertEquals(listOf("Rhystic Study"), names(rows.filter { CollectionFilter(deck = "Atraxa").matches(it, PriceType.TREND, emptySet(), deckUse) }))
        // A deck the collection knows nothing of, and a card in no deck.
        assertTrue(rows.none { CollectionFilter(deck = "Unknown").matches(it, PriceType.TREND, emptySet(), deckUse) })
        // It counts in the filter button's badge, and it goes with the other filters.
        assertEquals(1, burn.count)
        assertEquals(0, CollectionFilter().count)
        assertEquals(2, burn.copy(finishes = setOf(com.mtgtrader.data.Finish.FOIL)).count)
        // Without the decks (a call that doesn't know them) it leaves nothing.
        assertTrue(rows.none { burn.matches(it, PriceType.TREND, emptySet()) })
    }
}
