package com.mtgtrader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mtgtrader.data.GameRecord
import com.mtgtrader.data.GameSeat
import com.mtgtrader.data.GameStats
import com.mtgtrader.data.Games
import com.mtgtrader.data.GuestCommander
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.SeatChoice
import com.mtgtrader.data.TableSetup
import com.mtgtrader.data.WinType
import com.mtgtrader.ui.Buzz
import com.mtgtrader.ui.SeatDeck
import com.mtgtrader.ui.changeSeat
import com.mtgtrader.ui.firstBadgeSeat
import com.mtgtrader.ui.seatChoiceOf
import com.mtgtrader.ui.seatDeck
import com.mtgtrader.ui.setupOf
import com.mtgtrader.ui.startGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 1.58: the tracker's own vibration, the "1st" badge, Drain / burn, "Anonymous" in the stats, guests' commanders, and
 * changing the players and decks of a past game.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Release158Test {
    private fun seat(i: Int) = LiveSeat(seat = i, name = "P$i", life = 40, colour = i)
    private fun game(turns: Boolean = false, first: Int = 2) =
        LiveGame(uid = "g", startedAt = 0, seats = (0 until 4).map { seat(it) }, turnsOn = turns, firstSeat = first, active = first)

    @Test fun theBuzzRunsTheVibrationMotor() {
        val vibrator = Buzz.vibrator(ApplicationProvider.getApplicationContext<Context>())!!
        Buzz(vibrator).tap()
        assertEquals(Buzz.TAP_MS, shadowOf(vibrator).milliseconds)
        Buzz(vibrator).roll()
        assertEquals(Buzz.ROLL_MS, shadowOf(vibrator).milliseconds)
        // Off: nothing at all (and no crash without a motor).
        Buzz.OFF.tap()
    }

    @Test fun theFirstPlayerKeepsTheBadgeUntilSomethingHappens() {
        val g = game()
        assertEquals(2, firstBadgeSeat(g))
        assertNull(firstBadgeSeat(LiveGames.changeLife(g, 0, -1)))
        assertNull(firstBadgeSeat(LiveGames.commanderDamage(g, 1, LiveGames.cmdKey(0), 2)))
        assertNull(firstBadgeSeat(LiveGames.poison(g, 3, 1)))
        // With turns on the turn badge already shows who starts.
        assertNull(firstBadgeSeat(game(turns = true)))
    }

    @Test fun drainIsAWayToWin() {
        assertEquals(WinType.DRAIN, WinType.fromKey("drain"))
        assertEquals("Drain / burn", WinType.DRAIN.label)
    }

    private fun rec(id: String, vararg seats: GameSeat) =
        GameRecord(startedAt = id.hashCode().toLong(), endedAt = 0, durationMs = 60_000, seats = Games.encodeSeats(seats.toList()), uid = id)

    @Test fun seatsWithoutADeckAreAnonymousAndAGuestsCommanderIsADeck() {
        val games = listOf(
            rec("a", GameSeat(0, name = "Player 1", place = 1), GameSeat(1, name = "Player 2", commander = "Atraxa, Praetors' Voice", place = 2)),
            rec("b", GameSeat(0, name = "Player 1", place = 2), GameSeat(1, name = "Gæst", commander = "Atraxa, Praetors' Voice", place = 1)),
        )
        val decks = GameStats.decks(games)
        val anon = decks.single { it.anonymous }
        assertEquals("Anonymous", anon.name)
        assertEquals(2, anon.games)
        val atraxa = decks.single { !it.anonymous }
        assertEquals("Atraxa, Praetors' Voice", atraxa.name)
        assertEquals(2 to 1, atraxa.games to atraxa.wins)
    }

    @Test fun aGuestsCommanderPlaysLikeADeck() {
        val guest = GuestCommander("Thrasios, Triton Hero", "t-id", "Tymna the Weaver", "WUBG")
        val setup = TableSetup(seats = listOf(SeatChoice(name = "Joel", me = true), SeatChoice(name = "Player 2", guest = guest)))
        val g = startGame(setup, emptyList(), emptyList(), emptyList(), 0, first = 0)
        val s = g.seats[1]
        assertEquals("Thrasios, Triton Hero", s.commander)
        assertEquals("Tymna the Weaver", s.partner)
        // Partners: two commander damage counts, on their own.
        assertTrue(s.hasPartners)
        assertEquals("WUBG", s.colorIdentity)
        // A rematch keeps it; a player (not a guest) has no guest commander.
        assertEquals(guest, setupOf(g, null).seats[1].guest)
        assertNull(seatDeck(SeatChoice(playerUid = "p", name = "Mads", guest = guest), emptyList(), emptyList()))
        // Recorded, it's the commander pair, and that's its row in the stats.
        val rec = LiveGames.record(g, 60_000, 1, null, null)
        assertEquals("Thrasios, Triton Hero & Tymna the Weaver", rec.seatList[1].commander)
        assertEquals("Thrasios, Triton Hero & Tymna the Weaver", GameStats.decks(listOf(rec)).single { it.key != GameStats.deckKey(rec.seatList[0]) }.name)
        // Back from the record (to change the past game): still the guest's pair.
        assertEquals(guest.copy(scryfallId = null), seatChoiceOf(rec.seatList[1]).guest?.copy(scryfallId = null))
    }

    @Test fun changingAPastSeatKeepsHowItWent() {
        val old = GameSeat(1, name = "Player 2", place = 1, started = true, outReason = null, finalLife = 12)
        // A player picked for the guest: the result stays theirs.
        val mads = changeSeat(old, SeatChoice(playerUid = "mads", name = "Mads"), null)
        assertEquals("mads" to "Mads", mads.playerUid to mads.name)
        assertEquals(Triple(1, true, 12), Triple(mads.place, mads.started, mads.finalLife))
        // Their deck: its scores of today.
        val atraxa = SeatDeck("Atraxa", "Atraxa, Praetors' Voice", null, "a-id", "WUBG", null, 4, 8.0)
        val withDeck = changeSeat(mads, SeatChoice(playerUid = "mads", name = "Mads", friendDeckUid = "f1"), atraxa)
        assertEquals("f1" to 4, withDeck.friendDeckUid to withDeck.bracket)
        // The same deck again (only the player changes): its scores from the day stay.
        val then = withDeck.copy(bracket = 2)
        val sofie = changeSeat(then, SeatChoice(playerUid = "sofie", name = "Sofie", friendDeckUid = "f1"), atraxa)
        assertEquals("Sofie" to 2, sofie.name to sofie.bracket)
        // No deck any more.
        val none = changeSeat(sofie, SeatChoice(playerUid = "sofie", name = "Sofie"), null)
        assertNull(none.friendDeckUid)
        assertEquals("", none.commander)
        assertFalse(none.place == null)
    }
}
