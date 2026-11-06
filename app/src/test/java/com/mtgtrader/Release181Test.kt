package com.mtgtrader

import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.readWebPrices
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.81: Cardmarket's prices from the website's nightly file (web/test/prices.test.ts makes the same file). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release181Test {
    @Test
    fun readsTheWebsitePriceFile() = runBlocking {
        val rows = mutableListOf<PriceEntity>()
        val head = javaClass.getResourceAsStream("/webprices/prices.json")!!.use { readWebPrices(it) { p -> rows += p } }
        assertEquals("2026-11-06T02:42:30+0100", head.createdAt)
        assertEquals(1794029400000L, head.madeAt)
        assertEquals("2026-11-05", head.rates!!.date)
        assertEquals(7.4604, head.rates!!.perEuro.getValue("DKK"), 0.0)
        val byId = rows.associateBy { it.idProduct }
        assertEquals(setOf(1001, 1002, 1003), byId.keys)

        val plain = byId.getValue(1001)
        assertEquals(listOf(1.25, 1.4, 0.9, 1.3, 1.35, 1.2), listOf(plain.trend, plain.avg, plain.low, plain.avg1, plain.avg7, plain.avg30))
        assertNull(plain.trendFoil)

        val foilOnly = byId.getValue(1002)
        assertNull(foilOnly.trend)
        assertEquals(listOf(12.5, 13.0, 10.0, 12.0, 12.25, 12.75), listOf(foilOnly.trendFoil, foilOnly.avgFoil, foilOnly.lowFoil, foilOnly.avg1Foil, foilOnly.avg7Foil, foilOnly.avg30Foil))

        // A zero is "no price", as in Cardmarket's own file.
        val cheap = byId.getValue(1003)
        assertNull(cheap.avg1)
        assertNull(cheap.avgFoil)
        assertEquals(0.05, cheap.lowFoil!!, 0.0)
    }
}
