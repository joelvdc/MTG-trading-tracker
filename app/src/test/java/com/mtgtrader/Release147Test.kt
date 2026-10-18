package com.mtgtrader

import com.mtgtrader.data.ArchidektCategory
import com.mtgtrader.data.DeckEditItems
import com.mtgtrader.data.DeckEdit
import com.mtgtrader.data.DeckEdits
import com.mtgtrader.data.DeckOrder
import com.mtgtrader.data.GameRecord
import com.mtgtrader.data.GameSeat
import com.mtgtrader.data.GameStats
import com.mtgtrader.data.Games
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.OutReason
import com.mtgtrader.data.Player
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncDeletion
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.WinType
import com.mtgtrader.data.BackupRestore
import com.mtgtrader.ui.categoryRows
import com.mtgtrader.ui.seatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp

/** 1.47: the life tracker's rules, the game stats, their sync, and Manage categories. */
class Release147Test {
    private fun seat(i: Int, life: Int = 40, name: String = "P$i") = LiveSeat(seat = i, name = name, life = life, colour = i)

    private fun game(n: Int = 4, turns: Boolean = true, first: Int = 0) =
        LiveGame(uid = "g", startedAt = 0, seats = (0 until n).map { seat(it) }, turnsOn = turns, firstSeat = first, active = first)

    @Test
    fun commanderDamageCountsAndTakesLife() {
        var g = game()
        g = LiveGames.commanderDamage(g, 1, LiveGames.cmdKey(0), 7)
        g = LiveGames.commanderDamage(g, 1, LiveGames.cmdKey(0), 7)
        assertEquals(26, g.seats[1].life)
        assertEquals(14, g.seats[1].cmd["0"])
        // Taking it back gives the life back, and never goes below nothing.
        g = LiveGames.commanderDamage(g, 1, LiveGames.cmdKey(0), -20)
        assertEquals(40, g.seats[1].life)
        assertTrue(g.seats[1].cmd.isEmpty())
        // A partner's damage counts on its own.
        g = LiveGames.commanderDamage(g, 1, LiveGames.cmdKey(2, second = true), 21)
        assertEquals(21, g.seats[1].cmd["2:2"])
        assertEquals(OutReason.COMMANDER, LiveGames.outCause(g.seats[1]))
    }

    @Test
    fun asksDeadAtZeroTenPoisonOr21CommanderDamage() {
        var g = game()
        assertFalse(LiveGames.asksDead(g.seats[2]))
        g = LiveGames.changeLife(g, 2, -40)
        assertTrue(LiveGames.asksDead(g.seats[2]))
        assertEquals(OutReason.LIFE, LiveGames.outCause(g.seats[2]))
        // "No": not asked again until something changes.
        g = LiveGames.notDead(g, 2)
        assertFalse(LiveGames.asksDead(g.seats[2]))
        g = LiveGames.changeLife(g, 2, -1)
        assertTrue(LiveGames.asksDead(g.seats[2]))
        g = LiveGames.changeLife(g, 2, 5)
        assertFalse(LiveGames.asksDead(g.seats[2]))
        repeat(10) { g = LiveGames.poison(g, 3, 1) }
        assertEquals(OutReason.POISON, LiveGames.outCause(g.seats[3]))
    }

    @Test
    fun lowLifeIsBelowAQuarterOfTheStartingLife() {
        assertFalse(LiveGames.lowLife(seat(0, 10), 40))
        assertTrue(LiveGames.lowLife(seat(0, 9), 40))
        assertFalse(LiveGames.lowLife(seat(0, 0), 40))
        assertTrue(LiveGames.lowLife(seat(0, 4), 20))
    }

    @Test
    fun turnsGoClockwiseSkipPlayersOutAndCountRounds() {
        var g = game(first = 2)
        var t = 0L
        fun next() { t += 60_000; g = LiveGames.nextTurn(g, t) }
        next(); assertEquals(3, g.active); assertEquals(1, g.round)
        next(); assertEquals(0, g.active); assertEquals(1, g.round)
        next(); assertEquals(1, g.active); assertEquals(1, g.round)
        next(); assertEquals(2, g.active); assertEquals(2, g.round)
        assertEquals(4, g.turns.size)
        assertEquals(60_000, g.turns.first().ms)
        // The first player goes out: the next round still starts when play passes their seat.
        g = LiveGames.knockOut(g, 2, t, OutReason.CONCEDED)
        assertEquals(3, g.active)
        next(); next(); assertEquals(1, g.active); assertEquals(2, g.round)
        next(); assertEquals(3, g.active); assertEquals(3, g.round)
    }

    @Test
    fun pausesDontCountAsPlayingTime() {
        var g = game().copy(startedAt = 1_000)
        g = LiveGames.pause(g, 61_000)
        assertEquals(60_000, g.elapsed(121_000))
        g = LiveGames.resume(g, 121_000)
        assertEquals(90_000, g.elapsed(151_000))
    }

    @Test
    fun placesAndTheRecord() {
        var g = game(turns = true)
        g = LiveGames.knockOut(LiveGames.changeLife(g, 3, -40), 3, 10_000)
        g = g.copy(round = 5)
        g = LiveGames.knockOut(LiveGames.poison(g, 1, 10), 1, 20_000)
        // Two still in: the winner is picked; then the one still in, then the last one out.
        val places = LiveGames.places(g, winnerSeat = 2)
        assertEquals(mapOf(2 to 1, 0 to 2, 1 to 3, 3 to 4), places)
        val r = LiveGames.record(g, 30_000, 2, WinType.COMBO, " ")
        assertFalse(r.draw)
        assertNull(r.note)
        assertEquals("combo", r.winType)
        val seats = r.seatList
        assertEquals("poison", seats.first { it.seat == 1 }.outReason)
        assertEquals(5, seats.first { it.seat == 1 }.outRound)
        assertTrue(seats.first { it.seat == 0 }.started)
        // Nobody picked with two still in: a draw.
        assertTrue(LiveGames.record(g, 30_000, null, null, null).draw)
        // The last one standing wins by themselves.
        val last = LiveGames.knockOut(g, 0, 25_000)
        assertEquals(1, LiveGames.places(last, null)[2])
    }

    @Test
    fun liveGameSurvivesBeingSaved() {
        val g = LiveGames.commanderDamage(game(), 1, "0", 5)
        assertEquals(g, LiveGames.decode(LiveGames.encode(g)))
    }

    @Test
    fun panelsFaceTheirPlayers() {
        assertEquals(listOf(180, 0), (0 until 2).map { seatTurn(2, it) })
        assertEquals(listOf(90, -90, -90, 90), (0 until 4).map { seatTurn(4, it) })
        assertEquals(listOf(90, -90, -90, 0, 90), (0 until 5).map { seatTurn(5, it) })
        assertEquals(listOf(90, -90, -90, -90, 90, 90), (0 until 6).map { seatTurn(6, it) })
    }

    // ---- Stats -----------------------------------------------------------------------------------

    private fun rec(id: String, start: Long, minutes: Long, rounds: Int?, winType: WinType?, vararg seats: GameSeat) = GameRecord(
        startedAt = start, endedAt = start + minutes * 60_000, durationMs = minutes * 60_000, turnsOn = rounds != null, rounds = rounds,
        winType = winType?.key, seats = Games.encodeSeats(seats.toList()), uid = id,
    )

    private fun me(place: Int, deck: Long = 1, started: Boolean = false, bracket: Int? = 3) =
        GameSeat(0, "me", "Joel", me = true, deckId = deck, deckName = "Auntie Plague", commander = "Auntie Ool", place = place, started = started, bracket = bracket, power = 6.4)

    private fun mads(place: Int) = GameSeat(1, "mads", "Mads", friendDeckUid = "atraxa", deckName = "Atraxa", commander = "Atraxa", place = place, bracket = 4, power = 8.0)
    private fun sofie(place: Int) = GameSeat(2, "sofie", "Sofie", deckName = "Ruby", commander = "Ruby", place = place)

    private val history = listOf(
        rec("a", 1, 70, 11, WinType.COMBO, me(1, started = true), mads(2), sofie(3)),
        rec("b", 2, 50, 9, WinType.COMBAT, me(3), mads(1), sofie(2)),
        rec("c", 3, 60, 8, WinType.COMBAT, me(1), mads(3), sofie(2)),
        rec("d", 4, 40, null, null, me(2, deck = 2), mads(3), sofie(1)),
    )

    @Test
    fun deckResults() {
        val decks = GameStats.decks(history).associateBy { it.key }
        val auntie = decks.getValue("d:1")
        assertEquals(3, auntie.games)
        assertEquals(2, auntie.wins)
        assertEquals(60 * 60_000L, auntie.avgMs)
        assertEquals(65 * 60_000L, auntie.avgWinMs)
        assertEquals(28.0 / 3, auntie.avgRounds!!, 0.001)
        assertEquals(8, auntie.usualWinRound)
        assertEquals(listOf(WinType.COMBAT to 1, WinType.COMBO to 1), auntie.winTypes.sortedBy { it.first.ordinal })
        assertEquals(1, auntie.startedGames)
        assertEquals(1, auntie.startedWins)
        assertEquals(4.0, auntie.oppBracket!!, 0.001)
        assertTrue(auntie.mine)
        assertEquals(4, decks.size)
        // Mads's Atraxa played all four games; Auntie Plague won the most.
        assertEquals("f:atraxa", GameStats.sortDecks(decks.values.toList(), DeckOrder.GAMES).first().key)
        assertEquals("d:1", GameStats.sortDecks(decks.values.toList(), DeckOrder.WINS).first().key)
    }

    @Test
    fun playersAndHeadToHead() {
        val players = GameStats.players(history)
        assertEquals("Joel", players.first().name)
        assertEquals(2, players.first().wins)
        val h2h = GameStats.headToHead(history).associateBy { it.name }
        assertEquals(3, h2h.getValue("Mads").ahead)
        assertEquals(1, h2h.getValue("Mads").behind)
        assertEquals(2, h2h.getValue("Sofie").ahead)
        assertEquals(2, h2h.getValue("Sofie").behind)
        assertEquals(0.5, GameStats.myWinRate(history)!!, 0.001)
        assertEquals(listOf("c", "b", "a"), GameStats.gamesOfDeck(history, 1).map { it.uid })
    }

    @Test
    fun csvHasOneLinePerPlayerPerGame() {
        val csv = GameStats.csv(history).trim().lines()
        assertEquals(1 + 12, csv.size)
        assertTrue(csv[0].startsWith("Date,Game,Minutes"))
        assertTrue(csv[1].contains("Joel,Auntie Plague,Auntie Ool,1,yes,yes"))
    }

    // ---- Sync and backups --------------------------------------------------------------------------

    @Test
    fun gamesSyncLikeOtherItemsAndTheOwnerIsKeptOnce() {
        val mine = Player(name = "Me", me = true, createdAt = 5, uid = "me1", updatedAt = 5)
        val theirs = Player(name = "Joel", me = true, createdAt = 9, uid = "me2", updatedAt = 9)
        val local = SyncData(players = listOf(mine), games = listOf(history[0].copy(updatedAt = 10)))
        val remote = SyncData(
            players = listOf(theirs, Player(name = "Mads", uid = "mads", updatedAt = 3)),
            games = listOf(history[0].copy(note = "newer", updatedAt = 20), history[1].copy(updatedAt = 4)),
            deletions = listOf(SyncDeletion("c", 50)),
        )
        val merged = SyncMerge.merge(local, remote, now = 100)
        assertEquals(listOf("mads", "me1"), merged.players.map { it.uid })
        assertEquals("newer", merged.games.first { it.uid == "a" }.note)
        assertEquals(2, merged.games.size)
        assertTrue(merged.deletions.any { it.uid == "me2" })
    }

    @Test
    fun aBackupFromBeforeGamesLeavesThemAlone() {
        val current = SyncData(games = listOf(history[0]), players = listOf(Player(name = "Me", me = true, uid = "me")))
        val restored = BackupRestore.target(current, SyncData(), now = 99)
        assertEquals(1, restored.games.size)
        assertEquals(1, restored.players.size)
        val fromNew = BackupRestore.target(current, SyncData(games = listOf(history[1])), now = 99)
        assertEquals(listOf("b"), fromNew.games.map { it.uid })
        assertTrue(fromNew.deletions.any { it.uid == "a" })
    }

    // ---- Manage categories --------------------------------------------------------------------------

    @Test
    fun manageCategoriesListsEveryCategoryButTheSpecialOnes() {
        val rows = categoryRows(
            mapOf("Ramp" to 10, "ramp" to 1, "Commander" to 1, "Maybeboard" to 4, "Added in app" to 2, "Wither" to 2),
            known = listOf("Proliferate", "Ramp"),
            hidden = setOf("wither"),
        )
        assertEquals(listOf("Proliferate", "Ramp", "Wither"), rows.map { it.name })
        assertEquals(listOf(0, 11, 2), rows.map { it.cards })
        assertEquals(listOf(true, true, false), rows.map { it.shown })
    }

    @Test
    fun categoryChangesAreQueuedApartFromCardLines() {
        val e = DeckEdit(deckId = 1, lineUid = DeckEdits.CATEGORY_PREFIX + "x", relationId = 77, archidektCardId = null, name = "Wither", categories = DeckEdits.renameOp("Poison"))
        assertTrue(e.lineUid.startsWith(DeckEdits.CATEGORY_PREFIX))
        assertEquals("Poison", e.categories!!.removePrefix(DeckEdits.OP_RENAME))
        // Never mistaken for a card line: without a line it isn't a removal either.
        assertNull(DeckEditItems.item(e.copy(relationId = null), null))
        assertEquals(listOf(ArchidektCategory("Ramp", true, 5)), DeckEdits.categories(DeckEdits.encode(listOf(ArchidektCategory("Ramp", true, 5)))))
    }
}

/** 1.47: a card can be dragged by its handle (in 1.46 the drag was cancelled as soon as it began). */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class Release147DragTest {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()

    @Test
    fun aDragKeepsGoingUntilTheFingerLifts() {
        var lift by androidx.compose.runtime.mutableStateOf<Float?>(null)
        var ended = false
        var cancelled = false
        compose.setContent {
            com.mtgtrader.ui.LiftableRow(lift) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.testTag("handle")
                        .then(androidx.compose.ui.Modifier.size(200.dp, 60.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { lift = 0f },
                                onDrag = { change, amount -> change.consume(); lift = (lift ?: 0f) + amount.y },
                                onDragEnd = { ended = true },
                                onDragCancel = { cancelled = true },
                            )
                        },
                )
            }
        }
        compose.onNodeWithTag("handle").performTouchInput {
            down(center)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, 30f)) }
        }
        compose.waitForIdle()
        // Still being dragged (the row was lifted in the middle of it) and following the finger.
        val moved = lift ?: 0f
        assertFalse(cancelled)
        compose.onNodeWithTag("handle").performTouchInput { up() }
        compose.waitForIdle()
        assertFalse(cancelled)
        assertTrue(ended)
        assertTrue("moved $moved", moved > 200f)
    }
}
