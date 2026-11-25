package com.mtgtrader

import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckCardRow
import com.mtgtrader.data.DeckOwned
import com.mtgtrader.data.DeckOwnership
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.118: "Show cards I own" on the deck page, green / blue / none like the website's collection tracker. */
class DeckOwnershipTest {
    private fun ref(name: String, sid: String) = CardRef(sid, name, "s-$sid", "Set $sid", "1", "rare", null, null, 1.0, 2.0, true, true)
    private var nextId = 1L
    private fun deckRow(name: String, sid: String, qty: Int = 1, foil: Boolean = false) =
        DeckCardRow(DeckCard(id = nextId++, deckId = 9, card = ref(name, sid), quantity = qty, category = "", types = "", cmc = 1.0, commander = false, foil = foil), null)
    private fun owned(name: String, sid: String, qty: Int = 1, foil: Boolean = false) =
        CollectionRow(CollectionItem(id = nextId++, card = ref(name, sid), foil = foil, quantity = qty, binderId = 0, addedAt = 1), null)

    @Test
    fun greenForTheDecksPrintingBlueForAnotherNoneWhenNotOwned() {
        val ring = deckRow("Sol Ring", "arch")
        val bolt = deckRow("Lightning Bolt", "b1")
        val path = deckRow("Swords to Plowshares", "p1")
        val got = DeckOwnership.of(listOf(ring, bolt, path), listOf(owned("Sol Ring", "arch"), owned("Lightning Bolt", "b2", qty = 4)))
        assertEquals(DeckOwned.EXACT, got[ring.item.id])
        assertEquals(DeckOwned.OTHER, got[bolt.item.id])
        assertEquals(DeckOwned.NONE, got[path.item.id])
    }

    @Test
    fun anotherFinishOrTooFewCopiesIsBlue() {
        val foilRing = deckRow("Sol Ring", "arch", foil = true)
        val twoBolts = deckRow("Lightning Bolt", "b1", qty = 2)
        val got = DeckOwnership.of(listOf(foilRing, twoBolts), listOf(owned("Sol Ring", "arch"), owned("Lightning Bolt", "b1")))
        assertEquals(DeckOwned.OTHER, got[foilRing.item.id])
        assertEquals(DeckOwned.OTHER, got[twoBolts.item.id])
        // Copies spread over several collection lines add up.
        val enough = DeckOwnership.of(listOf(twoBolts), listOf(owned("Lightning Bolt", "b1"), owned("Lightning Bolt", "b1")))
        assertEquals(DeckOwned.EXACT, enough[twoBolts.item.id])
    }

    @Test
    fun doubleFacedNamesMatchAndBasicsGetNoColour() {
        val dfc = deckRow("Search for Azcanta // Azcanta, the Sunken Ruin", "a1")
        val island = deckRow("Island", "i1")
        val got = DeckOwnership.of(listOf(dfc, island), listOf(owned("Search for Azcanta", "a2"), owned("Island", "i1")))
        assertEquals(DeckOwned.OTHER, got[dfc.item.id])
        assertEquals(DeckOwned.NONE, got[island.item.id])
    }
}
