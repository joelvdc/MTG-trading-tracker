package com.mtgtrader.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * The app's own power level for a deck (since 1.66): how fast the win turn says it wins, on the turn scale of
 * "A Guide to Power Levels in EDH" (r/EDH, 2020), plus what the win turn can't see, from Commander Salt's ratings.
 * [power] is the whole number shown; [exact] the unrounded one. [turn] is the turn by which half the games are won,
 * with decimals (8.5: between turn 8 and 9), or null when that's not by the last turn, then [wonByEnd] counts.
 * [speed] is what the turn alone gives; [adjust] Commander Salt's part, null when it hasn't scored the deck.
 */
data class AppPowerReading(
    val power: Int,
    val exact: Double,
    val turn: Double?,
    val wonByEnd: Int,
    val speed: Double,
    val adjust: Double?,
)

object AppPowerLevel {
    /** The guide's turns and power levels, slid between: a win by turn 8 is 7, by turn 10.5 is 6. */
    private val SCALE = listOf(1.0 to 10.0, 3.0 to 9.0, 6.0 to 8.0, 8.0 to 7.0, 10.5 to 6.0, 13.0 to 5.0, 15.0 to 4.5)

    /** The turn by which [percent] of the games are won, between whole turns (since the share won each turn is known). */
    fun turnFor(byTurn: List<Int>, percent: Int): Double? {
        val i = byTurn.indexOfFirst { it >= percent }
        if (i < 0) return null
        val before = if (i == 0) 0 else byTurn[i - 1]
        return i + (percent - before).toDouble() / (byTurn[i] - before)
    }

    /** The guide's power level for a deck winning half its games by [turn]. */
    fun speed(turn: Double): Double {
        for ((a, b) in SCALE.zipWithNext()) {
            if (turn <= b.first) return a.second + (b.second - a.second) * (turn.coerceAtLeast(a.first) - a.first) / (b.first - a.first)
        }
        return SCALE.last().second
    }

    /** Not half the games by the last turn: by how many games are won at all. */
    private fun slow(wonByEnd: Int): Double = when {
        wonByEnd >= 35 -> 4.0
        wonByEnd >= 15 -> 3.0
        wonByEnd >= 1 -> 2.0
        else -> 1.0
    }

    /**
     * What the win turn can't see, from Commander Salt's 0–1 ratings ([SaltCard.axes]): interaction (it plays
     * alone), consistency (tutors, card draw), efficiency and the mana base, each against the middle of a casual
     * pod's decks, together at most one point either way. Null without the ratings.
     */
    fun adjust(axes: Map<String, Double>): Double? {
        val interaction = axes[BracketAxis.INTERACTION.key] ?: return null
        val consistency = axes[BracketAxis.CONSISTENCY.key] ?: return null
        val efficiency = axes[BracketAxis.EFFICIENCY.key] ?: return null
        val manabase = axes[BracketAxis.MANABASE.key] ?: return null
        val sum = (interaction - 0.25) * 2 + (consistency - 0.45) + (efficiency - 0.30) * 1.5 + (manabase - 0.90)
        return sum.coerceIn(-1.0, 1.0)
    }

    fun of(odds: WinOdds, salt: SaltCard?): AppPowerReading {
        val turn = turnFor(odds.byTurn, 50)
        val wonByEnd = odds.byTurn.lastOrNull() ?: 0
        val speed = turn?.let(::speed) ?: slow(wonByEnd)
        val adjust = salt?.axes?.let(::adjust)
        val exact = (speed + (adjust ?: 0.0)).coerceIn(1.0, 10.0)
        return AppPowerReading(exact.roundToInt(), exact, turn, wonByEnd, speed, adjust)
    }
}

/**
 * The app's power levels worked out so far, by deck id (kept while the app runs; the win turns behind them are
 * kept on the phone by [WinOddsStore]). A deck mapped to null can't have one: it has no win turn (no commander).
 */
object AppPower {
    var readings by mutableStateOf<Map<Long, AppPowerReading?>>(emptyMap())
        private set

    fun put(deckId: Long, reading: AppPowerReading?) {
        if (readings[deckId] != reading || deckId !in readings) readings = readings + (deckId to reading)
    }

    fun of(deckId: Long): AppPowerReading? = readings[deckId]

    fun known(deckId: Long) = deckId in readings
}
