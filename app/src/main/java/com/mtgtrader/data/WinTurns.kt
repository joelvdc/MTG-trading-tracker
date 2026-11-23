package com.mtgtrader.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The win turns worked out so far, by deck id (kept while the app runs; [WinOddsStore] keeps them on the phone), for
 * the lists that sort or show them without working them out. A deck mapped to null has none (no commander). Since 1.116
 * (the app's power level, [AppPower] until 1.115, followed them).
 */
object WinTurns {
    var odds by mutableStateOf<Map<Long, WinOdds?>>(emptyMap())
        private set

    fun put(deckId: Long, win: WinOdds?) {
        if (odds[deckId] != win || deckId !in odds) odds = odds + (deckId to win)
    }

    fun of(deckId: Long): WinOdds? = odds[deckId]

    /** The turn 4 in 10 games are won by, or null when unknown (or not by the last turn). */
    fun turn(deckId: Long): Int? = odds[deckId]?.fourInTen

    fun known(deckId: Long) = deckId in odds
}
