package com.mtgtrader

import com.mtgtrader.data.Deck
import com.mtgtrader.data.SaltCard
import com.mtgtrader.ui.CardSheets
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.65: the bracket cards as an A4 PDF, at the size of a Magic card, to print and cut (the only rule-zero card since 1.116). */
class Release165Test {
    private fun deck(id: Long, rated: Boolean = true) =
        Deck(id, "Deck $id", "joelvdc", "Commander", null, null, "G", 100, saltCard = if (rated) SaltCard(source = "app", bracket = 3).encode() else null)

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

    @Test fun nineBracketCardsToAPage() {
        val pages = CardSheets.pages((1..10).map { "b$it" })
        assertEquals(listOf(9, 1), pages.map { it.size })
        assertEquals("b10", pages[1].single())
        assertEquals(emptyList<List<String>>(), CardSheets.pages(emptyList<String>()))
        // Only decks the website rated get a card.
        val decks = listOf(deck(1), deck(2, rated = false), deck(3))
        assertEquals(listOf(1L, 3L), decks.filter { it.rated }.map { it.archidektId })
    }
}
