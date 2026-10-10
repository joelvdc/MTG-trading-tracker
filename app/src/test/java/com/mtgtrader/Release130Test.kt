package com.mtgtrader

import com.mtgtrader.data.PriceSourceStore
import com.mtgtrader.data.SourcePrice
import com.mtgtrader.data.readCardKingdom
import com.mtgtrader.data.readCardKingdomCopy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** 1.30: Card Kingdom through the GitHub copy, and each price source on its own schedule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release130Test {
    private fun resource(name: String): InputStream = GZIPInputStream(javaClass.getResourceAsStream("/cardkingdom/$name")!!)

    private fun read(parse: suspend (InputStream, Long, (String) -> Unit, suspend (SourcePrice) -> Unit) -> Unit, name: String): Pair<String?, Map<Pair<String, String>, SourcePrice>> = runBlocking {
        var date: String? = null
        val rows = mutableListOf<SourcePrice>()
        parse(resource(name), 7L, { date = it }) { rows += it }
        // Saved with REPLACE: the last row for a printing and finish wins.
        date to rows.associateBy { it.scryfallId to it.finish }
    }

    @Test
    fun theGitHubCopyHoldsTheSamePrices() {
        // pricelist-copy.tsv.gz is what scripts/card_kingdom_prices.py (the daily GitHub job) makes of pricelist.json.gz.
        val (directDate, direct) = read(::readCardKingdom, "pricelist.json.gz")
        val (copyDate, copy) = read(::readCardKingdomCopy, "pricelist-copy.tsv.gz")
        assertEquals("2026-10-10 03:05:18", directDate)
        assertEquals(directDate, copyDate)
        assertEquals(1203, direct.size)
        assertEquals(direct, copy)
        val bs = copy.getValue("bs" to "NONFOIL")
        assertEquals(listOf(2.99, 2.39, 2.09, 1.50, 1.00), listOf(bs.nm, bs.ex, bs.vg, bs.g, bs.buy))
        assertEquals("https://www.cardkingdom.com/mtg/ice-age/brainstorm", bs.url)
        // The original printing wins over The List's copy, in both.
        assertEquals(0.39, copy.getValue("salvage" to "NONFOIL").price!!, 0.0)
    }

    @Test
    fun eachSourceDownloadsOnceADayAndWaitsAfterAFailure() {
        val hour = 60 * 60 * 1000L
        val now = 1_000 * hour
        assertTrue(PriceSourceStore.isDue(lastOk = 0, lastFailed = 0, now = now)) // never downloaded
        assertFalse(PriceSourceStore.isDue(lastOk = now - 2 * hour, lastFailed = 0, now = now)) // fresh
        assertTrue(PriceSourceStore.isDue(lastOk = now - 21 * hour, lastFailed = 0, now = now)) // a day old
        // Failed an hour ago: no new try on every app start, only after a few hours.
        assertFalse(PriceSourceStore.isDue(lastOk = 0, lastFailed = now - hour, now = now))
        assertTrue(PriceSourceStore.isDue(lastOk = 0, lastFailed = now - 7 * hour, now = now))
    }
}
