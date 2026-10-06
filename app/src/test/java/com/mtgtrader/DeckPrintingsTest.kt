package com.mtgtrader

import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckCardRow
import com.mtgtrader.data.DeckPrintings
import com.mtgtrader.data.Finish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckPrintingsTest {
    private fun ref(name: String, sid: String) = CardRef(sid, name, "s-$sid", "Set $sid", "1", "rare", null, null, 1.0, 2.0, true, true)
    private var nextId = 1L
    private fun deckRow(name: String, sid: String) = DeckCardRow(DeckCard(id = nextId++, deckId = 9, card = ref(name, sid), quantity = 1, category = "", types = "", cmc = 1.0, commander = false, foil = false), null)
    private fun owned(name: String, sid: String, qty: Int = 1, binder: Long = 0, foil: Boolean = false) =
        CollectionRow(CollectionItem(id = nextId++, card = ref(name, sid), foil = foil, quantity = qty, binderId = binder, addedAt = 1), null)

    private val ring = deckRow("Sol Ring", "arch")

    @Test
    fun keepsArchidektsPrintingWhenOwnedOrNotOwnedAtAll() {
        val same = DeckPrintings.match(listOf(ring), listOf(owned("Sol Ring", "arch"), owned("Sol Ring", "other", qty = 5)), null, null, emptyMap())
        assertEquals("arch", same.rows.single().item.card.scryfallId)
        assertTrue(same.original.isEmpty())
        val none = DeckPrintings.match(listOf(ring), emptyList(), null, null, emptyMap())
        assertEquals("arch", none.rows.single().item.card.scryfallId)
    }

    @Test
    fun prefersTheDecksBinderThenMostCopiesOutsideTheTradeBinder() {
        val copies = listOf(owned("Sol Ring", "a", qty = 3, binder = 7), owned("Sol Ring", "b", qty = 1), owned("Sol Ring", "c", qty = 2, binder = 5))
        assertEquals("c", DeckPrintings.match(listOf(ring), copies, deckBinderId = 5, tradeBinderId = 7, picks = emptyMap()).rows.single().item.card.scryfallId)
        // No deck binder: "a" has most copies but sits in the trade binder.
        val r = DeckPrintings.match(listOf(ring), copies, deckBinderId = null, tradeBinderId = 7, picks = emptyMap())
        assertEquals("c", r.rows.single().item.card.scryfallId)
        assertEquals("arch", r.original.values.single().scryfallId)
    }

    @Test
    fun picksWinAndBasicsAreLeftAlone() {
        val copies = listOf(owned("Sol Ring", "a", qty = 3), owned("Sol Ring", "b", foil = true))
        val picked = DeckPrintings.match(listOf(ring), copies, null, null, mapOf("sol ring" to DeckPrintings.pickValue("b", Finish.FOIL)))
        assertEquals("b", picked.rows.single().item.card.scryfallId)
        assertTrue(picked.rows.single().item.foil)
        val archidekt = DeckPrintings.match(listOf(ring), copies, null, null, mapOf("sol ring" to DeckPrintings.ARCHIDEKT))
        assertEquals("arch", archidekt.rows.single().item.card.scryfallId)
        val forest = deckRow("Forest", "f1")
        assertEquals("f1", DeckPrintings.match(listOf(forest), listOf(owned("Forest", "f2", qty = 30)), null, null, emptyMap()).rows.single().item.card.scryfallId)
    }
}
