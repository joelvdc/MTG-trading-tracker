package com.mtgtrader

import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.TurnTime
import com.mtgtrader.ui.pickFirstNow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.73: the "who starts" pick waits while Game settings is open, and runs once when it closes. */
class Release173Test {

    private fun game(uid: String = "g", startedAt: Long = 0, seats: Int = 4, turns: List<TurnTime> = emptyList(), round: Int = 1) =
        LiveGame(
            uid = uid, startedAt = startedAt,
            seats = (0 until seats).map { LiveSeat(seat = it, name = "P$it", life = 40, colour = it) },
            turns = turns, round = round,
        )

    private val now = 1_000L

    @Test fun aNewGamePicksAtOnce() = assertTrue(pickFirstNow(game(), now, inSettings = false, picked = null, startedInSettings = null))

    @Test fun thePickWaitsWhileSettingsIsOpen() = assertFalse(pickFirstNow(game(), now, inSettings = true, picked = null, startedInSettings = "g"))

    @Test fun aGameStartedInSettingsPicksWhenSettingsCloses() =
        assertTrue(pickFirstNow(game(), nowMs = 60_000, inSettings = false, picked = null, startedInSettings = "g"))

    @Test fun thePickNeverRunsTwice() = assertFalse(pickFirstNow(game(), nowMs = 60_000, inSettings = false, picked = "g", startedInSettings = "g"))

    @Test fun anOldGameNeverPicks() = assertFalse(pickFirstNow(game(), nowMs = 60_000, inSettings = false, picked = null, startedInSettings = null))

    @Test fun aGameWithATurnOrInRoundTwoNeverPicks() {
        assertFalse(pickFirstNow(game(turns = listOf(TurnTime(1, 0, 500))), now, inSettings = false, picked = null, startedInSettings = null))
        assertFalse(pickFirstNow(game(round = 2), now, inSettings = false, picked = null, startedInSettings = null))
    }

    @Test fun aOnePlayerGameNeverPicks() = assertFalse(pickFirstNow(game(seats = 1), now, inSettings = false, picked = null, startedInSettings = null))
}
