package com.mtgtrader

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.75: the app tells every site its version and where to find it. */
class Release175Test {
    @Test
    fun userAgentNamesVersionAndHome() {
        assertEquals("MTGTraderAndroid/${BuildConfig.VERSION_NAME} (+https://github.com/joelvdc/MTG-trading-tracker)", USER_AGENT)
    }
}
