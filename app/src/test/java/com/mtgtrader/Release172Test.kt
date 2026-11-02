package com.mtgtrader

import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.LogEntry
import com.mtgtrader.data.SeatChoice
import com.mtgtrader.data.TableSetup
import com.mtgtrader.data.TurnTime
import com.mtgtrader.ui.swapSeats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 1.72: seats trade places on the New game screen and during a game; everything a player has goes with them. */
class Release172Test {
    private fun seat(i: Int, name: String, life: Int = 40, cmd: Map<String, Int> = emptyMap(), poison: Int = 0, counters: Map<String, Int> = emptyMap()) =
        LiveSeat(seat = i, name = name, life = life, colour = i, cmd = cmd, poison = poison, counters = counters, casts = i)

    private val game = LiveGame(
        uid = "g", startedAt = 0, turnsOn = true,
        seats = listOf(
            seat(0, "Robin", 33, cmd = mapOf("1" to 7, "3:2" to 2)),
            seat(1, "Mads", 40, cmd = mapOf("0" to 4)),
            seat(2, "Sofie", 28, poison = 3, counters = mapOf("energy" to 2)),
            seat(3, "Anders", 40, cmd = mapOf("1" to 5)),
        ),
        firstSeat = 1, active = 2, round = 3, monarch = 1, initiative = 3,
        turns = listOf(TurnTime(1, 1, 60_000), TurnTime(1, 2, 50_000)),
        log = listOf(LogEntry(0, 1, "start"), LogEntry(10, 0, "cmd", 7, 7, from = "1"), LogEntry(20, 0, "cmd", 2, 2, from = "3:2")),
    )

    @Test fun playersTakeEverythingWithThem() {
        val g = LiveGames.swapSeats(game, 1, 3)
        assertEquals(listOf("Robin", "Anders", "Sofie", "Mads"), g.seats.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), g.seats.map { it.seat })
        val mads = g.seats[3]
        assertEquals(40, mads.life); assertEquals(1, mads.colour); assertEquals(1, mads.casts)
        assertEquals(mapOf("0" to 4), mads.cmd)
        // Damage dealt by Mads (was seat 1) and Anders' partner (was 3:2) now names their new seats.
        assertEquals(mapOf("3" to 7, "1:2" to 2), g.seats[0].cmd)
        assertEquals(mapOf("3" to 5), g.seats[1].cmd)
        assertEquals(3, g.firstSeat)
        assertEquals(2, g.active)
        assertEquals(3, g.monarch)
        assertEquals(1, g.initiative)
        assertEquals(listOf(3, 2), g.turns.map { it.seat })
        assertEquals(listOf(3, 0, 0), g.log.map { it.seat })
        assertEquals(listOf(null, "3", "1:2"), g.log.map { it.from })
        assertEquals("Mads", g.seats.first { it.seat == g.monarch }.name)
    }

    @Test fun swappingBackRestoresTheGame() = assertEquals(game, LiveGames.swapSeats(LiveGames.swapSeats(game, 0, 2), 2, 0))

    @Test fun theSameSeatChangesNothing() = assertSame(game, LiveGames.swapSeats(game, 2, 2))

    @Test fun theTurnStillGoesClockwiseFromTheNewSeats() {
        val g = LiveGames.nextTurn(LiveGames.swapSeats(game, 2, 3), 1_000_000)
        // Sofie (now seat 3) had the turn: next clockwise is Robin at seat 0.
        assertEquals("Robin", g.seats[g.active].name)
    }

    @Test fun setupSwapKeepsTheStarter() {
        val t = TableSetup(seats = listOf("A", "B", "C").map { SeatChoice(name = it) }, firstSeat = 0)
        val s = swapSeats(t, 0, 2)
        assertEquals(listOf("C", "B", "A"), s.seats.map { it.name })
        assertEquals(2, s.firstSeat)
        assertEquals(1, swapSeats(t.copy(firstSeat = 1), 0, 2).firstSeat)
        assertSame(t, swapSeats(t, 0, 5))
    }
}
