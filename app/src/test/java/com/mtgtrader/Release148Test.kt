package com.mtgtrader

import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.LogEntry
import com.mtgtrader.data.OutReason
import com.mtgtrader.data.SeatChoice
import com.mtgtrader.data.TableSetup
import com.mtgtrader.data.WhenDown
import com.mtgtrader.ui.SeatDeck
import com.mtgtrader.ui.arrangement
import com.mtgtrader.ui.barAfterRow
import com.mtgtrader.ui.reseat
import com.mtgtrader.ui.seatTurn
import com.mtgtrader.ui.setupOf
import com.mtgtrader.ui.startGame
import com.mtgtrader.ui.untouched
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.48: the life tracker as Lifetap: the log, partners, out at once, the mana pool, layouts and changes during a game. */
class Release148Test {
    private fun seat(i: Int, life: Int = 40, partner: String? = null) =
        LiveSeat(seat = i, name = "P$i", life = life, colour = i, commander = "C$i", partner = partner)

    private fun game(n: Int = 4) = LiveGame(uid = "g", startedAt = 0, seats = (0 until n).map { seat(it) }, turnsOn = true)

    private fun LiveGame.then(at: Long, f: (LiveGame) -> LiveGame) = LiveGames.settle(this, f(this), at)

    @Test
    fun theLogMergesChangesInARow() {
        var g = game()
        g = g.then(1_000) { LiveGames.changeLife(it, 1, -1) }
        g = g.then(2_000) { LiveGames.changeLife(it, 1, -1) }
        g = g.then(3_000) { LiveGames.changeLife(it, 1, -1) }
        assertEquals(listOf(LogEntry(3_000, 1, "life", -3, 37)), g.log)
        assertEquals("P1 −3  (40 → 37)", LiveGames.logText(g.log.single(), g))
        // Taken back within the same moment: nothing is left of it.
        g = g.then(4_000) { LiveGames.changeLife(it, 1, 3) }
        assertTrue(g.log.isEmpty())
        // Long after: a line of its own; another player: another line.
        g = g.then(4_500) { LiveGames.changeLife(it, 1, -2) }
        g = g.then(20_000) { LiveGames.changeLife(it, 1, -2) }
        g = g.then(20_100) { LiveGames.changeLife(it, 2, 5) }
        assertEquals(listOf(-2, -2, 5), g.log.map { it.by })
    }

    @Test
    fun commanderDamageIsLoggedOnceNotAsLifeToo() {
        var g = game().then(1_000) { LiveGames.commanderDamage(it, 0, LiveGames.cmdKey(1), 5) }
        assertEquals(35, g.seats[0].life)
        assertEquals(listOf("cmd"), g.log.map { it.kind })
        assertEquals("P0: +5 commander damage from C1 (P1), 5 in all", LiveGames.logText(g.log.single(), g))
        // Without the life loss, commander damage takes no life.
        g = game().copy(cmdLifeLoss = false).then(1_000) { LiveGames.commanderDamage(it, 0, LiveGames.cmdKey(1), 5) }
        assertEquals(40, g.seats[0].life)
        assertEquals(5, g.seats[0].maxCommanderDamage)
    }

    @Test
    fun turnsTaxCountersAndTheTableAreLogged() {
        var g = game()
        g = g.then(1_000) { LiveGames.casts(it, 2, 1) }
        g = g.then(9_000) { LiveGames.setMonarch(it, 3) }
        g = g.then(10_000) { LiveGames.toggleDayNight(it) }
        g = g.then(11_000) { LiveGames.toggleAscend(it, 1) }
        g = g.then(30_000) { LiveGames.nextTurn(it, 30_000) }
        assertEquals(listOf("tax", "monarch", "day", "counter", "turn"), g.log.map { it.kind })
        assertEquals(
            listOf("P2: commander tax +2, now +2", "P3 is the monarch", "It's day", "P1 has the city's blessing", "Round 1 · P1's turn"),
            g.log.map { LiveGames.logText(it, g) },
        )
    }

    @Test
    fun outAtOnceWhenTheGameSaysSo() {
        val asked = game().then(1_000) { LiveGames.changeLife(it, 2, -40) }
        assertFalse(asked.seats[2].out)
        assertTrue(LiveGames.asksDead(asked, asked.seats[2]))

        var g = game().copy(whenDown = WhenDown.AUTO.key).then(1_000) { LiveGames.changeLife(it, 2, -40) }
        assertTrue(g.seats[2].out)
        assertEquals(OutReason.LIFE.key, g.seats[2].outReason)
        assertEquals(listOf("life", "out"), g.log.map { it.kind })
        assertFalse(LiveGames.asksDead(g, g.seats[2]))
        // "Undo" on the panel brings them back, and they stay in until something changes.
        g = g.then(2_000) { LiveGames.bringBack(it, 2) }
        assertFalse(g.seats[2].out)
        g = g.then(3_000) { LiveGames.poison(it, 1, 10) }
        assertTrue(g.seats[1].out)
        assertFalse(g.seats[2].out)

        val off = game().copy(whenDown = WhenDown.OFF.key).then(1_000) { LiveGames.changeLife(it, 2, -40) }
        assertFalse(off.seats[2].out)
        assertFalse(LiveGames.asksDead(off, off.seats[2]))
    }

    @Test
    fun partnersFollowTheDeckUnlessSwitched() {
        var g = game().copy(seats = listOf(seat(0), seat(1, partner = "Tymna")))
        assertFalse(g.seats[0].hasPartners)
        assertTrue(g.seats[1].hasPartners)
        g = LiveGames.setPartners(g, 0, true)
        g = LiveGames.setPartners(g, 1, false)
        assertTrue(g.seats[0].hasPartners)
        assertFalse(g.seats[1].hasPartners)
        // Each partner's damage counts on its own towards 21.
        g = LiveGames.setPartners(g, 1, true)
        g = LiveGames.commanderDamage(g, 0, LiveGames.cmdKey(1), 15)
        g = LiveGames.commanderDamage(g, 0, LiveGames.cmdKey(1, true), 15)
        assertNull(LiveGames.outCause(g.seats[0].copy(life = 40)))
    }

    @Test
    fun theManaPool() {
        var g = game()
        g = LiveGames.mana(g, "G", 3)
        g = LiveGames.mana(g, "U", 1)
        g = LiveGames.mana(g, "U", -2)
        g = LiveGames.storm(g, 4)
        assertEquals(mapOf("G" to 3), g.mana)
        assertEquals(4, g.storm)
        g = LiveGames.emptyPool(g)
        assertTrue(g.mana.isEmpty())
        assertEquals(0, g.storm)
        assertEquals(g, LiveGames.decode(LiveGames.encode(g)))
    }

    @Test
    fun oneAtEachEndOfTheTable() {
        assertEquals(listOf(180, -90, 0, 90), (0 until 4).map { seatTurn(4, it, ends = true) })
        assertEquals(listOf(180, -90, -90, 0, 90, 90), (0 until 6).map { seatTurn(6, it, ends = true) })
        // Clockwise: the top end, down the right side, the bottom end, up the left side.
        assertEquals(listOf(listOf(0, 0), listOf(3, 1), listOf(2, 2)), arrangement(4, ends = true))
        assertEquals(listOf(listOf(0, 0), listOf(5, 1), listOf(4, 2), listOf(3, 3)), arrangement(6, ends = true))
        // Every seat is on the screen once; the bar is in the middle.
        (2..6).forEach { n -> assertEquals((0 until n).toSet(), arrangement(n).flatten().toSet()) }
        assertEquals(1, barAfterRow(6, ends = true))
        assertEquals(0, barAfterRow(4, ends = true))
        // Without ends: as in 1.47.
        assertEquals(listOf(90, -90, -90, 90), (0 until 4).map { seatTurn(4, it) })
    }

    @Test
    fun aNewGameStartsWithItsSettingsAndALogLine() {
        val setup = TableSetup(
            seats = listOf(SeatChoice(name = "Joel", me = true), SeatChoice(name = "Mads"), SeatChoice(name = "Sofie"), SeatChoice(name = "Anders")),
            startingLife = 30, cmdLifeLoss = false, whenDown = WhenDown.AUTO.key, ends = true, counters = listOf("energy", LiveGames.CUSTOM + "Rings"),
        )
        val g = startGame(setup, emptyList(), emptyList(), emptyList(), 0, first = 2)
        assertEquals(30, g.seats.first().life)
        assertFalse(g.cmdLifeLoss)
        assertEquals(WhenDown.AUTO.key, g.whenDown)
        assertTrue(g.ends)
        assertEquals(listOf(LiveGames.CUSTOM + "Rings"), g.counters)
        assertEquals("Game started · Sofie starts", LiveGames.logText(g.log.single(), g))
        assertTrue(untouched(g))
        assertFalse(untouched(LiveGames.poison(g, 0, 1)))
        // Ends only for 4 and 6 players.
        assertFalse(startGame(setup.copy(seats = setup.seats.take(3)), emptyList(), emptyList(), emptyList(), 0, 0).ends)
        // The table it stands for, for a rematch.
        val back = setupOf(g, null)
        assertEquals(setup.seats.map { it.name }, back.seats.map { it.name })
        assertEquals(30, back.startingLife)
        assertTrue(back.ends)
    }

    @Test
    fun anotherPlayerOrDeckDuringTheGame() {
        var g = LiveGames.commanderDamage(game(), 1, LiveGames.cmdKey(0), 4)
        g = LiveGames.setPartners(g, 0, true)
        val deck = SeatDeck("Auntie Plague", "Auntie Blyte", null, "abc", "B", "https://x/art.jpg", 3, 6.5)
        g = reseat(g, 0, SeatChoice("uid-joel", "Joel", me = true, deckId = 7), deck)
        val s = g.seats[0]
        assertEquals("Joel", s.name)
        assertEquals("Auntie Plague", s.deckName)
        assertEquals("Auntie Blyte", s.commander)
        assertEquals("https://x/art.jpg", s.artUrl)
        assertNull(s.partners)
        // Life and the damage done stay.
        assertEquals(40, s.life)
        assertEquals(4, g.seats[1].cmd[LiveGames.cmdKey(0)])
    }
}
