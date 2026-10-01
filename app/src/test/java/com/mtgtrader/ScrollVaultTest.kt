package com.mtgtrader

import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckSort
import com.mtgtrader.data.DeckSorting
import com.mtgtrader.data.PowerSource
import com.mtgtrader.data.ScrollVaultReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollVaultTest {
    // The result as ScrollVault's page shows it (text of the Auntie Plague and Taranika band analyses).
    private val auntie = """
        Commander Bracket Calculator

        VERDICT

        B3

        Bracket 3
        UPGRADED

        Deck read. B3 Upgraded: 3 high-power 2-card combos. Fast-mana minimal, 36 lands; expected win around T8.

        EXHIBITION
        CORE
        UPGRADED
        OPTIMIZED
        CEDH
        borderline ↑ B4
        FIRM
        6.2 ±0.5
        POWER /10
        0
        GAME CHANGERS
        computed by ScrollVault method v=engine-v385 — what the deck can do, not how you play it
        0 Game Changers
        typical win ~T8 (earliest T6)
        100 cards
        TELL YOUR POD
        Bracket 3 (Upgraded) · 0 Game Changers · a late 2-card infinite combo · typical win ~T8
        Copy
    """.trimIndent()

    private val taranika = """
        VERDICT

        B2

        Bracket 2
        CORE

        borderline ↑ B3
        BANDED
        4.9 ±0.7
        POWER /10
        1
        GAME CHANGER
        seam: B2–B3, leans B2
        typical win ~T9 (earliest T7)
        TELL YOUR POD
        Bracket 2 (Core) · 0 Game Changers · typical win ~T9
    """.trimIndent()

    @Test
    fun readsTheVerdict() {
        val r = ScrollVaultReading.parse(auntie)!!
        assertEquals(6.2, r.power, 1e-9)
        assertEquals(0.5, r.margin!!, 1e-9)
        assertEquals(3, r.bracket)
        assertEquals(4, r.borderline)
        assertEquals(8, r.typicalWin)
        assertEquals(6, r.earliestWin)
        assertEquals(0, r.gameChangers)
        assertEquals("Bracket 3 (Upgraded) · 0 Game Changers · a late 2-card infinite combo · typical win ~T8", r.podLine)
        // Survives being saved with the deck.
        assertEquals(r, ScrollVaultReading.decode(r.encode()))
    }

    @Test
    fun readsSingularGameChangerAndBand() {
        val r = ScrollVaultReading.parse(taranika)!!
        assertEquals(4.9, r.power, 1e-9)
        assertEquals(0.7, r.margin!!, 1e-9)
        assertEquals(2, r.bracket)
        assertEquals(3, r.borderline)
        assertEquals(1, r.gameChangers)
        assertEquals(9, r.typicalWin)
    }

    @Test
    fun nothingBeforeTheAnalysisIsDone() {
        assertNull(ScrollVaultReading.parse("Commander Bracket Calculator\nDECK INPUT\nANALYZE DECK"))
        assertNull(ScrollVaultReading.parse("VERDICT\n\nB3\n\nBracket 3"))
    }

    @Test
    fun sortingUsesScrollVaultsNumbers() {
        fun deck(name: String, salt: Double, sv: Double?) =
            Deck(name.hashCode().toLong(), name, "", "", null, null, "", 100, powerLevel = salt, svPowerLevel = sv)
        val decks = listOf(deck("Baba", 6.4, 5.8), deck("Taranika", 5.4, 4.9), deck("Auntie", 4.4, 6.2))
        assertEquals(listOf("Auntie", "Baba", "Taranika"), DeckSorting.sort(decks, DeckSort.POWER, false, PowerSource.SCROLLVAULT).map { it.name })
        assertEquals(listOf("Baba", "Taranika", "Auntie"), DeckSorting.sort(decks, DeckSort.POWER, false, PowerSource.COMMANDER_SALT).map { it.name })
    }
}
