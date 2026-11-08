package com.mtgtrader.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** The period the game stats look at (the website's Period). */
enum class StatsPeriod(val label: String) {
    ALL("All time"),
    YEAR("Last 12 months"),
    QUARTER("Last 3 months"),
}

data class Overview(val games: Int, val myWinRate: Double?, val avgMs: Long?, val avgRounds: Double?)

data class MonthCount(val month: YearMonth, val games: Int)

/** [type] null = won, but not told how. */
data class WayCount(val type: WinType?, val games: Int)

/** Games where the starting player and the winner are known; [wins] by the one who started; [fairShare] an equal chance. */
data class FirstPlayer(val games: Int, val wins: Int, val fairShare: Double)

/** The game stats' overview tab and period, the same as on the website (gameshared.ts, "The website's extras"). */
object GameOverview {
    /** The games started in [period] up to [now] (calendar months back, like JavaScript's setMonth). */
    fun inPeriod(games: List<GameRecord>, period: StatsPeriod, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<GameRecord> {
        val months = when (period) {
            StatsPeriod.ALL -> return games
            StatsPeriod.YEAR -> 12L
            StatsPeriod.QUARTER -> 3L
        }
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        // Day of month overflows into the next month (31 March minus one month = 3 March), as setMonth does.
        val back = YearMonth.from(local).minusMonths(months).atDay(1).plusDays(local.dayOfMonth - 1L).atTime(local.toLocalTime())
        val from = back.atZone(zone).toInstant().toEpochMilli()
        return games.filter { it.startedAt >= from }
    }

    fun overview(games: List<GameRecord>): Overview {
        val rounds = games.mapNotNull { it.rounds }
        return Overview(
            games = games.size,
            myWinRate = GameStats.myWinRate(games),
            avgMs = if (games.isEmpty()) null else (games.sumOf { it.durationMs }.toDouble() / games.size).toLong(),
            avgRounds = if (rounds.isEmpty()) null else rounds.sum().toDouble() / rounds.size,
        )
    }

    /** Games per calendar month, oldest first, months without games included; at most the last [max]. */
    fun perMonth(games: List<GameRecord>, max: Int = 24, zone: ZoneId = ZoneId.systemDefault()): List<MonthCount> {
        if (games.isEmpty()) return emptyList()
        fun month(at: Long) = YearMonth.from(Instant.ofEpochMilli(at).atZone(zone))
        val counts = games.groupingBy { month(it.startedAt) }.eachCount()
        val first = month(games.minOf { it.startedAt })
        val last = month(games.maxOf { it.startedAt })
        val out = ArrayList<MonthCount>()
        var m = first
        while (!m.isAfter(last)) {
            out += MonthCount(m, counts[m] ?: 0)
            m = m.plusMonths(1)
        }
        return out.takeLast(max)
    }

    /** How the won games were won, most first; games won without saying how come last as "not told" (type null). */
    fun winsByType(games: List<GameRecord>): List<WayCount> {
        val won = games.filter { g -> !g.draw && g.seatList.any { it.place == 1 } }
        val told = won.mapNotNull { WinType.fromKey(it.winType) }
        val counts = LinkedHashMap<WinType, Int>()
        told.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val out = counts.entries.sortedByDescending { it.value }.map { WayCount(it.key, it.value) }
        val untold = won.size - told.size
        return if (untold > 0) out + WayCount(null, untold) else out
    }

    /** How the player who took the first turn did, in games with a starter and a winner. */
    fun firstPlayer(games: List<GameRecord>): FirstPlayer {
        val known = games.mapNotNull { g ->
            val seats = g.seatList
            val w = g.winner
            if (w != null && seats.any { it.started }) seats to w else null
        }
        return FirstPlayer(
            games = known.size,
            wins = known.count { (_, w) -> w.started },
            fairShare = if (known.isEmpty()) 0.0 else known.sumOf { (s, _) -> 1.0 / s.size } / known.size,
        )
    }
}
