package com.mtgtrader

import com.mtgtrader.data.Deck
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.108: only active decks (yours on Archidekt, local, not retired) are offered for game seats. */
class Release1108Test {
    private fun deck(id: Long, owner: String, retired: Boolean = false) =
        Deck(id, "Deck $id", owner, "", null, null, "", 100).copy(retired = retired)

    @Test
    fun onlyActiveDecksAreOfferedForSeats() {
        val decks = listOf(deck(1, "joelvdc"), deck(2, "someoneelse"), deck(3, "joelvdc", retired = true), deck(-4, ""), deck(5, "JoelVDC"))
        assertEquals(listOf(1L, -4L, 5L), decks.filter { it.isActive(" joelvdc ") }.map { it.archidektId })
        // Without a username only local decks are yours.
        assertEquals(listOf(-4L), decks.filter { it.isActive("") }.map { it.archidektId })
    }
}
