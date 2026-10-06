package com.mtgtrader

import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionFilter
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.CollectionStats
import com.mtgtrader.data.ColorMatch
import com.mtgtrader.data.PriceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionStatsTest {
    private var n = 0
    private fun row(
        name: String, colors: String, identity: String = colors, type: String = "Creature", cmc: Double = 2.0,
        rarity: String = "common", set: String = "tst", qty: Int = 1, price: Double = 1.0, foil: Boolean = false, rank: Int? = null,
    ): CollectionRow {
        val id = "id-${n++}"
        return CollectionRow(
            CollectionItem(
                id = n.toLong(), card = CardRef(id, name, set, "Set $set", "1", rarity, null, null, price, price, true, true),
                foil = foil, quantity = qty, addedAt = 1,
            ),
            price = null,
            info = CardInfo(id, null, colors, identity, type, cmc, rank, 0),
        )
    }

    private val rows = listOf(
        row("Llanowar Elves", "G", qty = 3, cmc = 1.0, rank = 50),
        row("Lightning Bolt", "R", type = "Instant", cmc = 1.0, rarity = "uncommon", price = 2.0, rank = 10),
        row("Assassin's Trophy", "BG", type = "Instant", rarity = "rare", price = 5.0),
        row("Sol Ring", "", type = "Artifact", cmc = 1.0, rarity = "uncommon", set = "c13", qty = 2, price = 1.5, rank = 1),
        row("Overgrown Tomb", "", identity = "BG", type = "Land — Swamp Forest", cmc = 0.0, rarity = "rare", price = 10.0, foil = true),
    )
    private val stats = CollectionStats.compute(rows, PriceType.TREND, emptyMap(), setOf("sol ring"), setOf("sol ring"), mapOf("c13" to "2013-11-01", "tst" to "2020-01-01"))

    @Test
    fun overview() {
        assertEquals(8, stats.copies)
        assertEquals(5, stats.uniqueCards)
        assertEquals(3 + 2 + 5 + 3 + 10.0, stats.value, 0.001)
        assertEquals(1, stats.foilCopies)
    }

    @Test
    fun colorPieKeepsLandsApartFromColorless() {
        val byKey = stats.colors.associateBy { it.key }
        assertEquals(3, byKey["G"]!!.copies)
        assertEquals(1, byKey["M"]!!.copies)
        assertEquals(2, byKey["C"]!!.copies)
        assertEquals(1, byKey["L"]!!.copies)
        assertEquals(listOf("R", "G", "M", "C", "L"), stats.colors.map { it.key }) // WUBRG order
    }

    @Test
    fun identityGroupsAreNamedAndFilterExactly() {
        val golgari = stats.identities.single { it.label == "Golgari" }
        assertEquals(2, golgari.copies) // Assassin's Trophy and Overgrown Tomb
        assertEquals(CollectionFilter(colors = setOf('B', 'G'), colorMatch = ColorMatch.IDENTITY), golgari.filter)
        assertTrue(golgari.filter!!.matches(rows[4], PriceType.TREND, emptySet()))
        assertTrue(!golgari.filter!!.matches(rows[0], PriceType.TREND, emptySet()))
    }

    @Test
    fun curveTypesAndCommanderLists() {
        assertEquals(6, stats.curve.single { it.label == "1" }.copies) // elves 3, bolt, ring 2; the land isn't counted
        assertEquals(2, stats.types.single { it.key == "Instant" }.copies)
        assertEquals(2, stats.inDecks.copies)
        assertEquals(listOf("Sol Ring"), stats.gameChangers.map { it.row.item.card.name })
        assertEquals("Sol Ring", stats.edhrecTop.first().row.item.card.name)
        assertEquals("Overgrown Tomb", stats.mostValuable.first().row.item.card.name)
        assertEquals("2013", stats.oldest!!.note)
    }
}
