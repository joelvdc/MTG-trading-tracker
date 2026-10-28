package com.mtgtrader

import com.mtgtrader.data.Deck
import com.mtgtrader.data.PowerSource
import com.mtgtrader.ui.CardSheets
import com.mtgtrader.ui.PrintChoice
import com.mtgtrader.ui.PrintPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.65: the bracket and power level cards as an A4 PDF, at the size of a Magic card, to print and cut. */
class Release165Test {
    private fun deck(id: Long, power: Double? = 6.0) = Deck(id, "Deck $id", "joelvdc", "Commander", null, null, "G", 100, powerLevel = power, bracketRealistic = 3)

    @Test fun nineMagicSizedCardsFitOnA4WithMargins() {
        assertEquals(63f, CardSheets.CARD_W)
        assertEquals(88f, CardSheets.CARD_H)
        assertEquals(10.5f to 16.5f, CardSheets.origin(0))
        assertEquals(73.5f to 16.5f, CardSheets.origin(1))
        assertEquals(10.5f to 104.5f, CardSheets.origin(3))
        val (x, y) = CardSheets.origin(8)
        assertEquals(199.5f, x + CardSheets.CARD_W, 0.001f)
        assertEquals(280.5f, y + CardSheets.CARD_H, 0.001f)
        // The same margin on both sides, so a printer centring the page keeps the cards whole.
        assertEquals(CardSheets.PAGE_W - (x + CardSheets.CARD_W), CardSheets.LEFT, 0.001f)
        assertEquals(CardSheets.PAGE_H - (y + CardSheets.CARD_H), CardSheets.TOP, 0.001f)
    }

    @Test fun thePdfPageIsA4() {
        assertEquals(210.0, CardSheets.PDF_W / CardSheets.POINTS_PER_MM.toDouble(), 0.5)
        assertEquals(297.0, CardSheets.PDF_H / CardSheets.POINTS_PER_MM.toDouble(), 0.5)
    }

    @Test fun bracketCardsComeFirstAndEachKindStartsANewPage() {
        val pages = CardSheets.pages((1..10).map { "b$it" }, (1..4).map { "p$it" })
        assertEquals(listOf(9, 1, 4), pages.map { it.size })
        assertEquals("b10", pages[1].single())
        assertTrue(pages[2].all { it.startsWith("p") })
        assertEquals(emptyList<List<String>>(), CardSheets.pages(emptyList<String>(), emptyList()))
    }

    @Test fun powerCardsOnlyForRatedDecks() {
        val decks = listOf(deck(1), deck(2, power = null), deck(3))
        val both = PrintPlan.of(decks, PrintChoice.BOTH, PowerSource.COMMANDER_SALT)
        assertEquals(3, both.bracket.size)
        assertEquals(listOf(1L, 3L), both.power.map { it.archidektId })
        assertEquals(5, both.cards)
        assertEquals(2, both.pages)
        assertEquals(0, PrintPlan.of(decks, PrintChoice.BRACKET, PowerSource.COMMANDER_SALT).power.size)
        assertEquals(0, PrintPlan.of(decks, PrintChoice.POWER, PowerSource.COMMANDER_SALT).bracket.size)
        // Another power level source that hasn't rated the decks: no power cards.
        assertEquals(0, PrintPlan.of(decks, PrintChoice.POWER, PowerSource.SCROLLVAULT).cards)
    }
}
