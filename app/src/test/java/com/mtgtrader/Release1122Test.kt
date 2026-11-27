package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltCombo
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOddsCalc
import com.mtgtrader.ui.WinInput
import com.mtgtrader.ui.deckCombos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.122: the deck page's Combos section, and the win turn worked out on opening and with ↻, not on every edit. */
class Release1122Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    private val deck = listOf(
        card("Commander", "Legendary Creature — Human", "{2}{W}", "", "3/3", commander = true),
        card("Plains", "Basic Land — Plains", "", "({T}: Add {W}.)", quantity = 40, produced = "W"),
        card("Bear", "Creature — Bear", "{1}{W}", "", "2/2", quantity = 59),
    )

    @Test fun combosThatCountComeFirst() {
        val a = SaltCombo(listOf("A", "B"), counts = false)
        val b = SaltCombo(listOf("C", "D"), counts = true)
        val c = SaltCombo(listOf("E", "F"), counts = false)
        val d = SaltCombo(listOf("G", "H"), counts = true)
        assertEquals(listOf(b, d, a, c), deckCombos(SaltCard(combos = listOf(a, b, c, d))))
        assertEquals(emptyList<SaltCombo>(), deckCombos(null))
    }

    /** Card details arriving or a new rating for the same list are taken in by themselves; an edit isn't. */
    @Test fun onlyAnEditMakesTheWinTurnStale() {
        val opened = WinInput(deck.map { it.copy(oracleText = null) }, "W", null)
        assertTrue(opened.sameList(WinInput(deck, "W", SaltCard(bracket = 2))))
        assertTrue(opened.sameList(WinInput(deck.reversed(), "W", null)))
        val edited = deck.mapIndexed { i, c -> if (i == 2) c.copy(quantity = 58) else c }
        assertFalse(opened.sameList(WinInput(edited, "W", null)))
        assertFalse(opened.sameList(WinInput(deck + card("Elf", "Creature — Elf", "{G}", "", "1/1"), "W", null)))
        assertFalse(opened.sameList(WinInput(deck, "WG", null)))
    }

    /** Leaving the page stops the games: nothing comes back to keep. */
    @Test fun aStoppedRunGivesNothing() {
        assertNull(WinOddsCalc.calculate(deck, "W", emptyList(), games = 400, stop = { true }))
        assertNotNull(WinOddsCalc.calculate(deck, "W", emptyList(), games = 400))
    }
}
