package com.mtgtrader

import com.mtgtrader.data.ArchidektGuard
import com.mtgtrader.data.ArchidektSync
import com.mtgtrader.data.RemoteSwitches
import com.mtgtrader.data.RequestPacer
import com.mtgtrader.data.Switches
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

/** 1.76: Archidekt requests at a safe pace, and switches on GitHub that can turn Archidekt features off. */
class Release176Test {
    private var clock = 0L
    private val waits = mutableListOf<Long>()
    private var switches = Switches()

    /** A client whose last step answers 200 without going online, behind the guard. */
    private fun client(): OkHttpClient {
        val guard = ArchidektGuard({ switches }, RequestPacer(ArchidektGuard.GAP_MS) { clock }) { waits += it }
        return OkHttpClient.Builder().addInterceptor(guard).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("{}".toResponseBody()).build()
        }.build()
    }

    private fun get(c: OkHttpClient, url: String, token: Boolean = false) {
        val b = Request.Builder().url(url)
        if (token) b.header("Authorization", "JWT x")
        c.newCall(b.build()).execute().close()
    }

    @Test
    fun pacerSpacesRequestsAndQueuesThem() {
        var t = 0L
        val p = RequestPacer(1500) { t }
        assertEquals(0, p.reserve())
        assertEquals(1500, p.reserve())
        assertEquals(3000, p.reserve())
        t = 10_000
        assertEquals(0, p.reserve())
        t = 10_500
        assertEquals(1000, p.reserve())
    }

    @Test
    fun onlyArchidektApiRequestsArePaced() {
        val c = client()
        get(c, "https://archidekt.com/api/decks/1/")
        get(c, "https://archidekt.com/api/decks/2/")
        get(c, "https://archidekt.com/api/decks/3/")
        get(c, "https://storage.googleapis.com/archidekt/cover.jpg")
        get(c, "https://api.scryfall.com/cards/named?exact=Sol+Ring")
        get(c, "https://archidekt.com/decks/1")
        assertEquals(listOf(1500L, 3000L), waits)
    }

    @Test
    fun atMostFortyArchidektRequestsAMinute() {
        val c = client()
        repeat(41) { get(c, "https://archidekt.com/api/decks/$it/") }
        // The 41st waits a whole minute after the first.
        assertEquals(60_000L, waits.last())
    }

    @Test
    fun switchedOffAccountBlocksLoginAndTokenRequestsOnly() {
        switches = Switches(archidektAccount = false, message = "Archidekt asked to pause it.")
        val c = client()
        for ((url, token) in listOf("https://archidekt.com/api/rest-auth/login/" to false, "https://archidekt.com/api/collection/v2/" to true)) {
            try {
                get(c, url, token)
                fail("$url went through")
            } catch (e: IOException) {
                assertEquals("Logging in to Archidekt is switched off in MTG Trader for now. Archidekt asked to pause it.", e.message)
            }
        }
        get(c, "https://archidekt.com/api/decks/1/")
    }

    @Test
    fun switchedOffDecksBlocksPublicReadsOnly() {
        switches = Switches(archidektDecks = false)
        val c = client()
        try {
            get(c, "https://archidekt.com/api/decks/1/")
            fail("went through")
        } catch (e: IOException) {
            assertEquals("Reading decks from Archidekt is switched off in MTG Trader for now.", e.message)
        }
        get(c, "https://archidekt.com/api/collection/v2/", token = true)
    }

    @Test
    fun switchesFileReadsLeniently() {
        assertEquals(Switches(), RemoteSwitches.parse("{}"))
        assertEquals(Switches(archidektAccount = false), RemoteSwitches.parse("""{"archidektAccount": false, "somethingNew": 1}"""))
        assertEquals(null, RemoteSwitches.parse("<html>Not found</html>"))
        assertEquals(null, RemoteSwitches.parse(null))
    }

    @Test
    fun theRepositorysSwitchesFileHasEverythingOn() {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "remote-switches.json") }.first { it.exists() }
        val s = RemoteSwitches.parse(f.readText())!!
        assertTrue(s.archidektAccount)
        assertTrue(s.archidektDecks)
        assertFalse(f.readText().contains("archidektAcount"))
    }

    @Test
    fun timeLeftAtArchidektsPace() {
        assertEquals("", ArchidektSync.timeLeft(30))
        assertEquals(" (about 1 min left)", ArchidektSync.timeLeft(40))
        assertEquals(" (about 25 min left)", ArchidektSync.timeLeft(1000))
        assertEquals(" (about 2 h left)", ArchidektSync.timeLeft(5000))
    }
}
