package com.mtgtrader

import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.50: low life starts at a quarter of the starting life. */
class Release150Test {
    private fun seat(life: Int, out: Boolean = false) = LiveSeat(seat = 0, name = "P0", life = life, out = out)

    @Test
    fun aQuarterOfTheStartingLifeIsLowLife() {
        assertTrue(LiveGames.lowLife(seat(10), 40))
        assertFalse(LiveGames.lowLife(seat(11), 40))
        assertTrue(LiveGames.lowLife(seat(5), 20))
        assertFalse(LiveGames.lowLife(seat(6), 20))
        assertTrue(LiveGames.lowLife(seat(7), 30))
        assertFalse(LiveGames.lowLife(seat(8), 30))
        assertTrue(LiveGames.lowLife(seat(1), 40))
    }

    @Test
    fun noLowLifeAtZeroOrWhenOut() {
        assertFalse(LiveGames.lowLife(seat(0), 40))
        assertFalse(LiveGames.lowLife(seat(-3), 40))
        assertFalse(LiveGames.lowLife(seat(10, out = true), 40))
    }
}
