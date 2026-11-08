package com.mtgtrader

import com.mtgtrader.data.Deck
import com.mtgtrader.data.GameRecord
import com.mtgtrader.data.GameSeat
import com.mtgtrader.data.GameStats
import com.mtgtrader.data.Games
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.SeatChoice
import com.mtgtrader.data.TableSetup
import com.mtgtrader.ui.seatChoiceOf
import com.mtgtrader.ui.startGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.82: friends and guests can borrow the owner's decks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Release182Test {
    private val vohar = Deck(archidektId = 7, name = "Vohar Danse", owner = "joelvdc", commanders = "Vohar, Vodalian Desecrator", commanderScryfallId = null, artUrl = null, colorIdentity = "UB", cardCount = 100, importedAt = 0, bracketRealistic = 3, powerLevel = 6.5)

    private fun rec(id: String, at: Long, vararg seats: GameSeat) =
        GameRecord(startedAt = at, endedAt = at, durationMs = 60_000, seats = Games.encodeSeats(seats.toList()), uid = id)

    @Test fun aGuestCanPlayOneOfMyDecksAndItIsRecordedAsLent() {
        val setup = TableSetup(seats = listOf(SeatChoice(playerUid = "me", name = "Joel", me = true), SeatChoice(name = "Kim", deckId = 7)))
        val g = startGame(setup, listOf(vohar), emptyList(), emptyList(), 0, first = 0)
        val kim = g.seats[1]
        assertEquals("Kim" to 7L, kim.name to kim.deckId)
        assertEquals("Vohar, Vodalian Desecrator", kim.commander)
        assertEquals(3, kim.bracket)
        val seat = LiveGames.record(g, 60_000, 1, null, null).seatList[1]
        assertTrue(GameStats.lent(seat))
        assertFalse(seat.me)
        // Changing the past game: still Kim with my deck, not a guest commander.
        val back = seatChoiceOf(seat)
        assertEquals(7L, back.deckId)
        assertNull(back.guest)
    }

    @Test fun lentGamesCountForTheDeckAndForTheBorrower() {
        val me = GameSeat(0, playerUid = "me", name = "Joel", me = true, deckId = 7, deckName = "Vohar Danse")
        val anna = GameSeat(1, playerUid = "anna", name = "Anna", friendDeckUid = "f1", deckName = "Atraxa")
        val games = listOf(
            rec("a", 1, me.copy(place = 1), anna.copy(place = 2)),
            // Lent: Kim plays Vohar and wins; Joel plays nothing that game.
            rec("b", 2, GameSeat(0, name = "Kim", deckId = 7, deckName = "Vohar Danse", place = 1), anna.copy(place = 2)),
            rec("c", 3, GameSeat(0, playerUid = "anna", name = "Anna", deckId = 7, deckName = "Vohar Danse", place = 2), GameSeat(1, playerUid = "bo", name = "Bo", place = 1)),
        )
        val vohar = GameStats.decks(games).single { it.deckId == 7L }
        assertEquals(3 to 2, vohar.games to vohar.wins)
        assertEquals(2 to 1, vohar.lentGames to vohar.lentWins)
        // Still one of my decks, though Anna played it last.
        assertTrue(vohar.mine)
        // The borrower's result is theirs: Kim won once; my own win rate only counts the game I played.
        assertEquals(1, GameStats.players(games).single { it.name == "Kim" }.wins)
        assertEquals(1.0, GameStats.myWinRate(games)!!, 0.0)
        assertEquals(listOf("anna"), GameStats.headToHead(games).map { it.key })
        assertEquals(3, GameStats.gamesOfDeck(games, 7).size)
    }
}
