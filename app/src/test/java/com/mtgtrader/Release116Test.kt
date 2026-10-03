package com.mtgtrader

import com.mtgtrader.data.BracketTag
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CatalogMatch
import com.mtgtrader.data.CmProduct
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.DeckUsage
import com.mtgtrader.data.DeckUse
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.ValueHistory
import com.mtgtrader.scan.SetSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cardmarket product matching for printings Scryfall doesn't link (The List, surge foils, LTR scrolls). */
class CatalogMatchTest {
    private fun price(id: Int, normal: Double?, foil: Double?) =
        PriceEntity(id, normal, normal, normal, normal, normal, normal, foil, foil, foil, foil, foil, foil)

    @Test
    fun namesMatchIgnoringPunctuationAndBackFaces() {
        assertTrue(CatalogMatch.sameCard("Urza's Saga", "Urza's Saga"))
        assertTrue(CatalogMatch.sameCard("Urza's Saga", "Urzas Saga"))
        assertTrue(CatalogMatch.sameCard("Delver of Secrets // Insectile Aberration", "Delver of Secrets"))
        assertTrue(!CatalogMatch.sameCard("Saga", "Urza's Saga"))
    }

    @Test
    fun setExpansionsComeFromVotes() {
        assertEquals(setOf(3494, 5911), CatalogMatch.expansions(mapOf(3494 to 40, 5911 to 12, 77 to 1)))
        assertEquals(setOf(77), CatalogMatch.expansions(mapOf(77 to 1)))
    }

    @Test
    fun picksOnlyAUniqueProduct() {
        val products = listOf(CmProduct(685626, "Urza's Saga", 3494), CmProduct(555, "Urza's Saga", 1), CmProduct(556, "Urza's Saga", 3494))
        assertNull(CatalogMatch.pick(products, "Urza's Saga", setOf(3494)))
        assertEquals(685626, CatalogMatch.pick(products.take(2), "Urza's Saga", setOf(3494, 5911)))
    }

    @Test
    fun foilCompanionIsTheNextProductPricedOnlyAsFoil() {
        val main = CmProduct(738122, "Peregrin Took", 9)
        val same = listOf(main, CmProduct(738123, "Peregrin Took", 9))
        // As in the real guide: the foil product's "low" is filled although it has no normal price.
        val prices = mapOf(738122 to price(738122, 0.2, null), 738123 to price(738123, null, 3.9).copy(low = 1.79))
        assertEquals(738123, CatalogMatch.foilCompanion(main, same, prices))
        // The main product has a foil price of its own: nothing to add.
        assertNull(CatalogMatch.foilCompanion(main, same, prices + (738122 to price(738122, 0.2, 1.0))))
    }
}

class BracketTagTest {
    private val card = SaltCard(
        gameChangers = listOf("Rhystic Study"),
        twoCardCombos = listOf(listOf("Thassa's Oracle", "Demonic Consultation")),
        extraTurns = listOf("Time Warp"),
        tutors = listOf("Demonic Tutor", "Demonic Consultation"),
        fastMana = listOf("Sol Ring"),
        version = SaltCard.CURRENT,
    )

    @Test
    fun tagsCardsByName() {
        assertEquals(setOf(BracketTag.GAME_CHANGER), card.tags("rhystic study"))
        assertEquals(setOf(BracketTag.COMBO, BracketTag.TUTOR), card.tags("Demonic Consultation"))
        assertEquals(setOf(BracketTag.FAST_MANA), card.tags("Sol Ring"))
        assertTrue(card.tags("Island").isEmpty())
        assertEquals(listOf(listOf("Demonic Consultation")), card.combosWith("Thassa's Oracle"))
    }

    @Test
    fun doubleFacedCardsMatchByFrontFace() {
        val c = SaltCard(gameChangers = listOf("Fable of the Mirror-Breaker // Reflection of Kiki-Jiki"))
        assertEquals(setOf(BracketTag.GAME_CHANGER), c.tags("Fable of the Mirror-Breaker"))
    }

    @Test
    fun olderDataIsFetchedAgain() {
        val saved = SaltCard.decode(SaltCard(gameChangers = listOf("X")).encode())!!
        assertTrue(!saved.current)
        assertTrue(SaltCard.decode(card.encode())!!.current)
    }
}

class DeckUsageTest {
    @Test
    fun groupsByNameWithoutBasics() {
        val uses = listOf(
            DeckUse("Sol Ring", 1, "A", 1, null), DeckUse("sol ring", 2, "B", 1, null),
            DeckUse("Island", 1, "A", 30, null), DeckUse("Island", 2, "B", 10, null),
        )
        val m = DeckUsage.byName(uses)
        assertEquals(2, m["sol ring"]?.size)
        assertNull(m["island"])
    }
}

class ValueHistoryTest {
    private fun row(name: String, qty: Int, trend: Double, avg30: Double) = CollectionRow(
        CollectionItem(card = CardRef(name, name, "tst", "Test", "1", "rare", null, 1, null, null, true, false), foil = false, quantity = qty),
        PriceEntity(1, trend, trend, trend, trend, trend, avg30, null, null, null, null, null, null),
    )

    @Test
    fun movesCountEveryCopy() {
        val rows = listOf(row("Up", 2, 12.0, 10.0), row("Down", 1, 5.0, 8.0), row("Flat", 4, 1.0, 1.0))
        val up = ValueHistory.biggestMoves(rows, up = true)
        assertEquals(listOf("Up"), up.map { it.row.item.card.name })
        assertEquals(4.0, up.first().change, 1e-9)
        assertEquals(listOf("Down"), ValueHistory.biggestMoves(rows, up = false).map { it.row.item.card.name })
        assertEquals(2 * 12.0 + 5.0 + 4 * 1.0, ValueHistory.totalValue(rows, PriceType.TREND), 1e-9)
    }
}

class SetSymbolShapeTest {
    private fun grid(vararg rows: String): Triple<BooleanArray, Int, Int> {
        val w = rows[0].length
        return Triple(BooleanArray(w * rows.size) { rows[it / w][it % w] == '#' }, w, rows.size)
    }

    @Test
    fun fillsEnclosedHolesOnly() {
        val (m, w, h) = grid(
            ".....",
            ".###.",
            ".#.#.",
            ".###.",
            "..#..",
        )
        val f = SetSymbol.fillHoles(m, w, h)
        assertTrue(f[2 * w + 2])
        assertTrue(!f[0])
    }

    @Test
    fun sameShapeAtAnySizeMatches() {
        fun disc(size: Int) = IntArray(size * size) { i ->
            val x = i % size - size / 2.0
            val y = i / size - size / 2.0
            if (x * x + y * y < (size / 3.0) * (size / 3.0)) 255 else 0
        }
        val small = SetSymbol.fromAlpha(disc(40), 40, 40)!!
        val big = SetSymbol.fromAlpha(disc(128), 128, 128)!!
        assertTrue(SetSymbol.similarity(small, big) > 0.9f)
        // A wide bar is a different symbol.
        val bar = SetSymbol.fromAlpha(IntArray(128 * 128) { i -> if (i / 128 in 54..74) 255 else 0 }, 128, 128)!!
        assertTrue(SetSymbol.similarity(big, bar) < 0.5f)
    }

    @Test
    fun findsADarkSymbolOnALightTypeLine() {
        // Light type-line box with "text" on the left and a dark diamond on the right.
        val w = 120
        val h = 60
        val luma = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val diamond = kotlin.math.abs(x - 95) + kotlin.math.abs(y - 30) < 18
            val text = x in 5..40 && y in 24..36 && x % 6 < 3
            if (diamond || text) 20 else 220
        }
        val s = SetSymbol.candidates(luma, w, h)
        assertTrue(s.isNotEmpty())
        val diamond = SetSymbol.fromAlpha(IntArray(64 * 64) { i -> if (kotlin.math.abs(i % 64 - 32) + kotlin.math.abs(i / 64 - 32) < 30) 255 else 0 }, 64, 64)!!
        assertTrue(s.maxOf { SetSymbol.similarity(it, diamond) } > 0.8f)
    }
}
