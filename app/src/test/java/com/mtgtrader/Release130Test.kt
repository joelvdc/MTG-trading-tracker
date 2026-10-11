package com.mtgtrader

import com.mtgtrader.data.PriceSourceStore
import com.mtgtrader.data.SourcePrice
import com.mtgtrader.data.readCardKingdom
import com.mtgtrader.data.readCardKingdomCopy
import kotlinx.coroutines.launch
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

    /** A tiny local web server that counts requests and answers each with [body] after [delayMs]. */
    private class FakeServer(private val body: ByteArray, private val delayMs: Long = 300) {
        val requests = java.util.concurrent.atomic.AtomicInteger()
        private val server = java.net.ServerSocket(0)
        val url get() = "http://127.0.0.1:${server.localPort}/list"

        init {
            Thread {
                while (!server.isClosed) {
                    val s = try { server.accept() } catch (e: java.io.IOException) { break }
                    Thread {
                        s.use {
                            val reader = it.getInputStream().bufferedReader()
                            while (true) if ((reader.readLine() ?: break).isEmpty()) break
                            requests.incrementAndGet()
                            Thread.sleep(delayMs)
                            val out = it.getOutputStream()
                            out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            out.write(body)
                            out.flush()
                        }
                    }.start()
                }
            }.start()
        }

        fun close() = server.close()
    }

    @Test
    fun twoUpdatesStartedTogetherDownloadOnlyOnce() = runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.mtgtrader.data.AppDatabase::class.java).build()
        context.getSharedPreferences("price_sources", 0).edit().clear().commit()
        val copy = javaClass.getResourceAsStream("/cardkingdom/pricelist-copy.tsv.gz")!!.readBytes()
        val server = FakeServer(copy)
        try {
            val http = okhttp3.OkHttpClient()
            val store = PriceSourceStore(context, http, db, com.mtgtrader.data.ScryfallApi(http), cardKingdomUrl = "http://127.0.0.1:1/refused", cardKingdomCopyUrl = server.url)
            // The app start, the "back online" check and the background worker, all at once.
            kotlinx.coroutines.coroutineScope {
                repeat(3) { launch(kotlinx.coroutines.Dispatchers.Default) { store.refreshIfStale() } }
            }
            assertEquals("Card Kingdom's list is downloaded once", 1, server.requests.get())
            assertEquals(1203, db.sourcePriceDao().count("cardkingdom"))
            assertEquals("GitHub copy", store.status.value.cardKingdomVia)
            // Later the same day: nothing is due.
            store.refreshIfStale()
            assertEquals(1, server.requests.get())
            // "Update prices now" always downloads.
            store.refresh()
            assertEquals(2, server.requests.get())
        } finally {
            server.close()
            db.close()
        }
    }
}
