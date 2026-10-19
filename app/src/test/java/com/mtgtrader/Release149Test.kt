package com.mtgtrader

import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.OutReason
import com.mtgtrader.ui.SPIN_MS
import com.mtgtrader.ui.miniCellText
import com.mtgtrader.ui.spinDelays
import com.mtgtrader.ui.spinSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.49: a slower run around the table, commander damage from your own commander. */
class Release149Test {
    private fun game(n: Int = 4, partnerAt: Int? = null) = LiveGame(
        uid = "g", startedAt = 0,
        seats = (0 until n).map { LiveSeat(seat = it, name = "P$it", life = 40, colour = it, commander = "C$it", partner = if (it == partnerAt) "Q$it" else null) },
    )

    @Test
    fun theRunGoesAroundAtLeastTwiceAndStopsOnThePick() {
        for (players in 2..6) for (pick in 0 until players) {
            val steps = spinSteps(players, pick)
            assertEquals(pick, (steps - 1) % players)
            assertTrue(steps >= 12)
            assertTrue(steps >= players * 2)
        }
    }

    @Test
    fun theRunTakesAboutThreeSecondsAndSlowsDown() {
        for (steps in listOf(13, 16, 18)) {
            val d = spinDelays(steps)
            assertEquals(steps, d.size)
            assertTrue(d.sum() in SPIN_MS - steps..SPIN_MS)
            assertTrue(d.zipWithNext().all { (a, b) -> b >= a })
            assertTrue(d.last() >= d.first() * 9)
            assertTrue(d.first() in 40L..90L)
        }
        assertTrue(spinDelays(0).isEmpty())
    }

    @Test
    fun yourOwnCommanderDamageCountsLikeAnyOther() {
        var g = LiveGames.commanderDamage(game(), 0, LiveGames.cmdKey(0), 5)
        assertEquals(35, g.seats[0].life)
        assertEquals(5, g.seats[0].maxCommanderDamage)
        g = LiveGames.commanderDamage(g, 0, LiveGames.cmdKey(0), 16)
        assertEquals(OutReason.COMMANDER, LiveGames.outCause(g.seats[0]))
        assertTrue(LiveGames.asksDead(g.seats[0]))
    }

    @Test
    fun theOwnBoxSaysMeUntilItCounts() {
        var g = game(partnerAt = 0)
        assertEquals("me", miniCellText(g, g.seats[0], 0))
        g = LiveGames.commanderDamage(g, 0, LiveGames.cmdKey(0, second = true), 3)
        assertEquals("0|3", miniCellText(g, g.seats[0], 0))
        g = game()
        g = LiveGames.commanderDamage(g, 1, LiveGames.cmdKey(1), 2)
        assertEquals("2", miniCellText(g, g.seats[1], 1))
        assertEquals("0", miniCellText(g, g.seats[1], 0))
    }

    @Test
    fun theLogSaysItWasTheirOwnCommander() {
        val g = LiveGames.settle(game(), LiveGames.commanderDamage(game(), 2, LiveGames.cmdKey(2), 4), 1_000)
        assertEquals("P2: +4 commander damage from their own C2, 4 in all", LiveGames.logText(g.log.single(), g))
    }
}
