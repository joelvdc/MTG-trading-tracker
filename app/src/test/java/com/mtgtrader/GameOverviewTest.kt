package com.mtgtrader

import com.mtgtrader.data.GameOverview
import com.mtgtrader.data.GameRecord
import com.mtgtrader.data.GameSeat
import com.mtgtrader.data.Games
import com.mtgtrader.data.StatsPeriod
import com.mtgtrader.data.WayCount
import com.mtgtrader.data.WinType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** The game stats' Overview tab and period, the same numbers as the website (web/test/games.test.ts). */
class GameOverviewTest {
    private val utc = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 11, 8, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
    private val day = 86_400_000L

    private fun me(place: Int?, started: Boolean = false) = GameSeat(0, "p1", "Joel", me = true, deckId = 1, deckName = "Vohar", place = place, started = started)
    private fun anna(place: Int?, started: Boolean = false) = GameSeat(1, "p2", "Anna", deckName = "Atraxa", place = place, started = started)
    private fun bo(place: Int?, started: Boolean = false) = GameSeat(2, "p3", "Bo", place = place, started = started)

    private fun rec(uid: String, daysAgo: Int, seats: List<GameSeat>, rounds: Int? = null, win: String? = null, minutes: Long = 60, draw: Boolean = false): GameRecord {
        val at = now - daysAgo * day
        return GameRecord(
            startedAt = at, endedAt = at + minutes * 60_000, durationMs = minutes * 60_000, turnsOn = rounds != null, rounds = rounds,
            winType = win, draw = draw, seats = Games.encodeSeats(seats), uid = uid,
        )
    }

    private val g1 = rec("g1", 200, listOf(me(1, true), anna(2), bo(3)), 8, "combo", 90)
    private val g2 = rec("g2", 40, listOf(me(2), anna(1, true), bo(3)), 10, "combat")
    private val g3 = rec("g3", 10, listOf(me(1), anna(3), bo(2, true)), 6, "combo", 30)
    private val games = listOf(g3, g2, g1)

    @Test
    fun filtersByPeriodAndSumsUp() {
        assertEquals(listOf("g3", "g2"), GameOverview.inPeriod(games, StatsPeriod.QUARTER, now, utc).map { it.uid })
        assertEquals(3, GameOverview.inPeriod(games, StatsPeriod.YEAR, now, utc).size)
        assertEquals(3, GameOverview.inPeriod(games, StatsPeriod.ALL, now, utc).size)
        val o = GameOverview.overview(GameOverview.inPeriod(games, StatsPeriod.QUARTER, now, utc))
        assertEquals(2, o.games)
        assertEquals(0.5, o.myWinRate!!, 1e-9)
        assertEquals(45 * 60_000L, o.avgMs)
        assertEquals(8.0, o.avgRounds!!, 1e-9)
    }

    @Test
    fun overviewOfNothingIsEmpty() {
        val o = GameOverview.overview(emptyList())
        assertEquals(0, o.games)
        assertNull(o.myWinRate)
        assertNull(o.avgMs)
        assertNull(o.avgRounds)
    }

    @Test
    fun countsGamesPerMonthHowTheyWereWonAndTheStartersWins() {
        val months = GameOverview.perMonth(games, zone = utc)
        assertEquals(3, months.sumOf { it.games })
        assertTrue(months.size >= 7)
        assertEquals(months.map { it.month }.sorted(), months.map { it.month })
        assertEquals(2, GameOverview.perMonth(games, 2, utc).size)
        assertEquals(listOf(WayCount(WinType.COMBO, 2), WayCount(WinType.COMBAT, 1)), GameOverview.winsByType(games))
        val f = GameOverview.firstPlayer(games)
        assertEquals(3, f.games)
        assertEquals(2, f.wins)
        assertEquals(1.0 / 3, f.fairShare, 1e-9)
    }

    @Test
    fun wonWithoutATypeIsNotToldAndADrawIsLeftOut() {
        val untold = rec("g4", 5, listOf(me(1), anna(2)))
        val draw = rec("g5", 4, listOf(me(null), anna(null)), win = "combo", draw = true)
        assertEquals(
            listOf(WayCount(WinType.COMBO, 2), WayCount(WinType.COMBAT, 1), WayCount(null, 1)),
            GameOverview.winsByType(games + untold + draw),
        )
        assertEquals(emptyList<WayCount>(), GameOverview.winsByType(listOf(draw)))
    }
}
