package com.mtgtrader

import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.ui.keepsStarter
import com.mtgtrader.ui.pickFirstNow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.74: back to the number of players Game settings opened with, the same player starts, without a new pick. */
class Release174Test {
    private fun game(uid: String, seats: Int) =
        LiveGame(uid = uid, startedAt = 0, seats = (0 until seats).map { LiveSeat(seat = it, name = "P$it", life = 40, colour = it) })

    @Test fun fourToFiveToFourKeepsTheStarter() {
        val g = game("third", 4)
        assertTrue(keepsStarter(g, startedInSettings = "third", openedSeats = 4))
    }

    @Test fun fourToFivePicksOnClosing() {
        val g = game("second", 5)
        assertFalse(keepsStarter(g, startedInSettings = "second", openedSeats = 4))
        assertTrue(pickFirstNow(g, 60_000, inSettings = false, picked = null, startedInSettings = "second"))
    }

    @Test fun aGameNotStartedInSettingsIsLeftAlone() {
        assertFalse(keepsStarter(game("old", 4), startedInSettings = null, openedSeats = 4))
        assertFalse(keepsStarter(game("old", 4), startedInSettings = "other", openedSeats = 4))
    }
}
