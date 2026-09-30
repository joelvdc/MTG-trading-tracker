package com.mtgtrader

import com.mtgtrader.data.ScryfallLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScryfallLimitsTest {
    @Test
    fun searchNamedRandomAndCollectionAreRateLimited() {
        assertTrue(ScryfallLimits.isSlow("/cards/collection"))
        assertTrue(ScryfallLimits.isSlow("/cards/search"))
        assertTrue(ScryfallLimits.isSlow("/cards/named"))
        assertTrue(ScryfallLimits.isSlow("/cards/random"))
        assertFalse(ScryfallLimits.isSlow("/cards/autocomplete"))
        assertFalse(ScryfallLimits.isSlow("/cards/mkm/123"))
        assertFalse(ScryfallLimits.isSlow("/cards/searchable-ish"))
        assertFalse(ScryfallLimits.isSlow("/sets"))
    }

    @Test
    fun waitsAsLongAsScryfallSays() {
        assertEquals(5_000L, ScryfallLimits.retryDelayMs("5"))
        assertEquals(30_000L, ScryfallLimits.retryDelayMs(null))
        assertEquals(30_000L, ScryfallLimits.retryDelayMs("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(30_000L, ScryfallLimits.retryDelayMs("0"))
    }
}
