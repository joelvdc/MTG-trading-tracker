package com.mtgtrader

import com.mtgtrader.data.BackupRestore
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.PausedGame
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncDeletion
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.SyncStore
import com.mtgtrader.data.TableSetup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 1.51: pausing a game to continue it later on any phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release151Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var c: AppContainer

    @Before
    fun setUp() {
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        context.getSharedPreferences("games", 0).edit().clear().commit()
        context.deleteDatabase("mtgtrader.db")
        c = AppContainer(context)
    }

    private fun game(uid: String = "g1", startedAt: Long = System.currentTimeMillis() - 30 * 60_000) = LiveGame(
        uid = uid, startedAt = startedAt, turnsOn = true, round = 4,
        seats = (0 until 4).map { LiveSeat(seat = it, name = "P$it", life = 40 - it, colour = it) },
    )

    private fun paused(uid: String, at: Long) = PausedGame(pausedAt = at, game = LiveGames.encode(game(uid)), uid = uid, updatedAt = at)

    @Test
    fun pauseAndLeaveKeepsTheGameAndClosesIt() = runBlocking {
        c.games.start(game(), TableSetup())
        c.games.pauseAndLeave()
        assertNull(c.games.live.value)
        val p = c.games.pausedGames.first().single()
        assertEquals("g1", p.uid)
        val g = p.live!!
        // The clock stopped when it was paused, and everything else is as it was.
        assertTrue(g.paused)
        assertEquals(4, g.round)
        assertEquals(listOf(40, 39, 38, 37), g.seats.map { it.life })
        assertFalse(p.clockStopped)
    }

    @Test
    fun continuingTakesItOffTheListEverywhere() = runBlocking {
        c.games.start(game(), TableSetup())
        c.games.pauseAndLeave()
        val p = c.games.pausedGames.first().single()
        assertTrue(c.games.continuePaused(p))
        val g = c.games.live.value!!
        assertEquals("g1", g.uid)
        // The clock goes on, and the time away doesn't count as playing time.
        assertFalse(g.paused)
        assertTrue(g.elapsed(System.currentTimeMillis()) in 29 * 60_000L..31 * 60_000L)
        assertTrue(c.games.pausedGames.first().isEmpty())
        // The deletion goes to the other phones, so it leaves their lists too.
        assertTrue(c.db.syncDao().deletions().any { it.uid == "g1" })
    }

    @Test
    fun aClockStoppedByHandStaysStopped() = runBlocking {
        val g = game()
        c.games.start(LiveGames.pause(g, System.currentTimeMillis() - 60_000), TableSetup())
        c.games.pauseAndLeave()
        val p = c.games.pausedGames.first().single()
        assertTrue(p.clockStopped)
        c.games.continuePaused(p)
        assertTrue(c.games.live.value!!.paused)
    }

    @Test
    fun pausingTheSameGameAgainBringsItBack() = runBlocking {
        c.games.start(game(), TableSetup())
        c.games.pauseAndLeave()
        c.games.continuePaused(c.games.pausedGames.first().single())
        c.games.pauseAndLeave()
        assertEquals("g1", c.games.pausedGames.first().single().uid)
        assertFalse(c.db.syncDao().deletions().any { it.uid == "g1" })
    }

    @Test
    fun droppingCanBeUndone() = runBlocking {
        c.games.start(game(), TableSetup())
        c.games.pauseAndLeave()
        val undo = c.games.dropPaused(c.games.pausedGames.first().single())
        assertTrue(c.games.pausedGames.first().isEmpty())
        undo()
        assertNotNull(c.games.pausedGames.first().single().live)
    }

    @Test
    fun pausedGamesSyncAndAContinuedOneIsGoneOnBothPhones() {
        val now = System.currentTimeMillis()
        // This phone paused two games; the other phone continued one of them after that, and paused one of its own.
        val local = SyncData(pausedGames = listOf(paused("a", now - 5_000), paused("b", now - 5_000)))
        val remote = SyncData(pausedGames = listOf(paused("b", now - 5_000), paused("c", now - 2_000)), deletions = listOf(SyncDeletion("a", now - 1_000)))
        val merged = SyncMerge.merge(local, remote, now)
        assertEquals(listOf("b", "c"), merged.pausedGames.map { it.uid })
        // Merging again from the other side gives the same.
        assertEquals(merged.pausedGames, SyncMerge.merge(remote, merged, now).pausedGames)
        // Through the file and back.
        assertEquals(merged.pausedGames, SyncFile.decode(SyncFile.encode(SyncFile(data = merged))).data.pausedGames)
        assertEquals(11, SyncFile.FORMAT)
    }

    @Test
    fun syncWritesPausedGamesToThisPhone() = runBlocking {
        val now = System.currentTimeMillis()
        val store = SyncStore(c.db, c.settings)
        val (current, last) = store.snapshot()
        assertTrue(current.pausedGames.isEmpty())
        assertTrue(store.apply(current, current.copy(pausedGames = listOf(paused("x", now))), last))
        val p = c.games.pausedGames.first().single()
        assertEquals("x", p.uid)
        // Written by the sync, it isn't a change of this phone's own.
        assertEquals(now, p.updatedAt)
    }

    @Test
    fun restoringABackupLeavesPausedGamesAlone() {
        val now = System.currentTimeMillis()
        val current = SyncData(pausedGames = listOf(paused("now", now)))
        val backup = SyncData(pausedGames = listOf(paused("old", now - 99_000)))
        val restored = BackupRestore.target(current, backup, now)
        assertEquals(listOf("now"), restored.pausedGames.map { it.uid })
        assertFalse(restored.deletions.any { it.uid == "now" })
    }
}
