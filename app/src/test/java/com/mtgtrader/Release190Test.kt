package com.mtgtrader

import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionFilter
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.DeckFilter
import com.mtgtrader.data.FaceKind
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.ScryCard
import com.mtgtrader.data.toInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.90: the collection filter's Faces part (MDFC, transforming, split), from Scryfall's layout. */
class Release190Test {
    private var nextId = 1L

    private fun row(name: String, layout: String?): CollectionRow {
        val ref = CardRef(name, name, "tst", "TST", "1", "rare", null, 1, null, null, true, false)
        return CollectionRow(
            CollectionItem(id = nextId++, card = ref, foil = false, quantity = 1, binderId = 0),
            null,
            CardInfo(name, null, "", "", "Instant", 2.0, null, layout = layout),
        )
    }

    private val valki = row("Valki, God of Lies // Tibalt, Cosmic Impostor", "modal_dfc")
    private val delver = row("Delver of Secrets // Insectile Aberration", "transform")
    private val fireIce = row("Fire // Ice", "split")
    private val bonecrusher = row("Bonecrusher Giant // Stomp", "adventure")
    private val bolt = row("Lightning Bolt", "normal")
    private val old = row("Agadeem's Awakening // Agadeem, the Undercrypt", null)

    @Test
    fun kindsFromLayout() {
        assertEquals(FaceKind.MDFC, FaceKind.of("modal_dfc"))
        assertEquals(FaceKind.TRANSFORM, FaceKind.of("transform"))
        assertEquals(FaceKind.TRANSFORM, FaceKind.of("meld"))
        assertEquals(FaceKind.SPLIT, FaceKind.of("adventure"))
        assertNull(FaceKind.of("normal"))
        assertNull(FaceKind.of(null))
    }

    @Test
    fun mdfcOnly() {
        val f = CollectionFilter(faces = setOf(FaceKind.MDFC))
        val shown = listOf(valki, delver, fireIce, bonecrusher, bolt, old).filter { f.matches(it, PriceType.TREND, emptySet()) }
        assertEquals(listOf(valki), shown)
        assertEquals(1, f.count)
    }

    @Test
    fun severalKindsAndDecks() {
        val f = CollectionFilter(faces = setOf(FaceKind.TRANSFORM, FaceKind.SPLIT))
        assertTrue(f.matches(delver, PriceType.TREND, emptySet()) && f.matches(fireIce, PriceType.TREND, emptySet()) && f.matches(bonecrusher, PriceType.TREND, emptySet()))
        assertFalse(f.matches(valki, PriceType.TREND, emptySet()))
        // MDFCs in a deck: deck names are front faces in lower case.
        val inDecks = CollectionFilter(faces = setOf(FaceKind.MDFC), decks = DeckFilter.IN_DECKS)
        assertTrue(inDecks.matches(valki, PriceType.TREND, setOf("valki, god of lies")))
        assertFalse(inDecks.matches(valki, PriceType.TREND, setOf("lightning bolt")))
    }

    @Test
    fun savedFilterKeepsFaces() {
        val f = CollectionFilter(faces = setOf(FaceKind.MDFC))
        assertEquals(f, CollectionFilter.decode(f.encode()))
        // A filter saved by 1.89 (no faces) still reads.
        assertEquals(CollectionFilter(), CollectionFilter.decode("""{"decks":"ANY"}"""))
    }

    @Test
    fun detailsKeepTheLayout() {
        val card = ScryCard(id = "x", name = "Valki", set = "khm", layout = "modal_dfc")
        assertEquals("modal_dfc", card.toInfo().layout)
    }
}
