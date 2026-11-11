package com.mtgtrader

import com.mtgtrader.data.NextcloudClient
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncException
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.SyncPrefs
import com.mtgtrader.data.ValueHistory
import com.mtgtrader.data.WebAccountClient
import com.mtgtrader.data.WebLogin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The app's side of syncing with an MTG Trader account on the website (since 1.79), against a fake website. */
class WebAccountTest {
    /** Acts like the website's /api/app/login and /api/sync: one account "joel", versions "v1", "v2"… */
    private class FakeWebsite : Interceptor {
        var bytes: ByteArray? = null
        var version = 0
        val seen = mutableListOf<Request>()
        var valueHistory: String? = null
        var shared: String? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            seen += req
            val path = req.url.encodedPath
            fun answer(code: Int, body: String = "", etag: String? = null, bin: ByteArray? = null) = Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(code).message("")
                .apply { if (etag != null) header("ETag", etag) }
                .body(bin?.toResponseBody("application/gzip".toMediaType()) ?: body.toResponseBody("application/json".toMediaType()))
                .build()
            if (path == "/api/app/login") {
                val sent = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
                return if (sent.contains("\"password\":\"a long password\"")) answer(200, """{"token":"tok123","username":"joel"}""")
                else answer(400, """{"error":"Wrong user name or password."}""")
            }
            if (req.header("Authorization") != "Bearer tok123") return answer(401, """{"error":"Log in first."}""")
            if (path == "/api/app/valuehistory") {
                valueHistory = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
                return answer(200, """{"ok":true}""")
            }
            if (path == "/api/app/share") {
                val sent = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
                shared = sent
                return if (sent.contains("\"target\":\"10710772\"")) answer(200, """{"url":"https://web.test/s/abc","on":true}""")
                else answer(404, """{"error":"The website doesn't have that deck yet."}""")
            }
            val tag = "\"v$version\""
            return when (req.method) {
                "GET" -> when {
                    bytes == null -> answer(404, """{"error":"No sync file yet."}""")
                    req.header("If-None-Match") == tag -> answer(304, etag = tag)
                    else -> answer(200, etag = tag, bin = bytes)
                }
                "PUT" -> {
                    val ok = if (req.header("If-None-Match") == "*") bytes == null else req.header("If-Match") == tag
                    if (!ok) answer(412, """{"error":"Changed meanwhile."}""")
                    else {
                        bytes = Buffer().also { req.body!!.writeTo(it) }.readByteArray()
                        version++
                        answer(if (version == 1) 201 else 204, etag = "\"v$version\"")
                    }
                }
                else -> answer(405)
            }
        }
    }

    private val site = FakeWebsite()
    private val client = WebAccountClient(OkHttpClient.Builder().addInterceptor(site).build(), "https://web.test/")

    @Test
    fun logsInAndSyncsLikeNextcloud() = runBlocking {
        val login = client.login(" joel ", "a long password", "Pixel 8")
        assertEquals(WebLogin("joel", "tok123"), login)

        assertEquals(NextcloudClient.Remote.Missing, client.get(login, null))
        val first = client.put(login, byteArrayOf(1, 2, 3), null) as NextcloudClient.PutResult.Ok
        assertEquals("\"v1\"", first.etag)
        assertEquals("*", site.seen.last().header("If-None-Match"))
        // Another phone made the file first: a second "new" save is refused, as on Nextcloud.
        assertEquals(NextcloudClient.PutResult.Conflict, client.put(login, byteArrayOf(9), null))

        val found = client.get(login, null) as NextcloudClient.Remote.Found
        assertArrayEquals(byteArrayOf(1, 2, 3), found.bytes)
        assertEquals(NextcloudClient.Remote.NotModified, client.get(login, found.etag))
        assertTrue(client.put(login, byteArrayOf(4), found.etag) is NextcloudClient.PutResult.Ok)
        assertEquals(NextcloudClient.PutResult.Conflict, client.put(login, byteArrayOf(5), found.etag))
    }

    @Test
    fun explainsWrongPasswordsAndLostLogins() = runBlocking {
        try {
            client.login("joel", "wrong", "Pixel 8")
            fail()
        } catch (e: SyncException) {
            assertEquals("Wrong user name or password.", e.message)
        }
        try {
            client.get(WebLogin("joel", "old-token"), null)
            fail()
        } catch (e: SyncException) {
            assertTrue(e.message!!.contains("no longer accepts"))
        }
    }

    @Test
    fun settingsOnlyOneSideKnowsAreKept() {
        // The other phone's settings are newer but come from a version without the trade binder setting.
        val mine = SyncData(prefs = SyncPrefs(1, mapOf("currency" to "EUR", SyncPrefs.TRADE_BINDER to "abc")))
        val theirs = SyncData(prefs = SyncPrefs(2, mapOf("currency" to "DKK")))
        val merged = SyncMerge.merge(mine, theirs, 3).prefs
        assertEquals(2L, merged.updatedAt)
        assertEquals(mapOf("currency" to "DKK", SyncPrefs.TRADE_BINDER to "abc"), merged.values)
        // Both the same: nothing to change.
        assertEquals(theirs.prefs, SyncMerge.merge(theirs, theirs, 3).prefs)
    }

    @Test
    fun sendsTheValueHistoryWithBindersBySyncId() = runBlocking {
        val values = mapOf("trend" to 12.5, "pays@cardkingdom" to 3.0, "b:0:trend" to 2.0, "b:7:cards" to 4.0, "b:7:trend" to 10.5, "b:9:trend" to 1.0)
        val web = ValueHistory.forWebsite(values, mapOf(7L to "B7"))
        // Unsorted becomes "~", binder 7 its uid; binder 9 is gone, so its entry is left out.
        assertEquals(mapOf("trend" to 12.5, "pays@cardkingdom" to 3.0, "b:~:trend" to 2.0, "b:B7:cards" to 4.0, "b:B7:trend" to 10.5), web)

        client.sendValueHistory(WebLogin("joel", "tok123"), listOf(WebAccountClient.ValueDay("2026-11-01", 20, web)))
        val day = Json.parseToJsonElement(site.valueHistory!!).jsonObject["days"]!!.jsonArray.single().jsonObject
        assertEquals("2026-11-01", day["day"]!!.jsonPrimitive.content)
        assertEquals(20, day["cards"]!!.jsonPrimitive.content.toInt())
        assertEquals(10.5, day["values"]!!.jsonObject["b:B7:trend"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }

    @Test
    fun asksForShareLinks() = runBlocking {
        val login = WebLogin("joel", "tok123")
        val link = client.share(login, "deck", "10710772")
        assertEquals(WebAccountClient.ShareLink("https://web.test/s/abc", true), link)
        val sent = Json.parseToJsonElement(site.shared!!).jsonObject
        assertEquals("deck", sent["kind"]!!.jsonPrimitive.content)
        assertEquals("10710772", sent["target"]!!.jsonPrimitive.content)
        try {
            client.share(login, "deck", "-5")
            fail()
        } catch (e: SyncException) {
            assertEquals("The website doesn't have that deck yet.", e.message)
        }
        try {
            client.share(WebLogin("joel", "old-token"), "collection", "")
            fail()
        } catch (e: SyncException) {
            assertTrue(e.message!!.contains("no longer accepts"))
        }
    }

    @Test
    fun declinedSuggestionsAndTradeRulesFollowTheNewerSettings() {
        // A phone on 1.80 declined a suggestion; another on 1.79 changed the currency later: the skips are kept.
        val mine = SyncData(prefs = SyncPrefs(1, mapOf("currency" to "EUR", "tradeMax" to "50", SyncPrefs.TRADE_SKIPS to "add|x|false|false|NM|EN")))
        val older = SyncData(prefs = SyncPrefs(2, mapOf("currency" to "DKK")))
        assertEquals(
            mapOf("currency" to "DKK", "tradeMax" to "50", SyncPrefs.TRADE_SKIPS to "add|x|false|false|NM|EN"),
            SyncMerge.merge(mine, older, 3).prefs.values,
        )
        // The website forgot the declined cards later: its empty list wins.
        val website = SyncData(prefs = SyncPrefs(4, mapOf("currency" to "DKK", "tradeMax" to "60", SyncPrefs.TRADE_SKIPS to "")))
        assertEquals(mapOf("currency" to "DKK", "tradeMax" to "60", SyncPrefs.TRADE_SKIPS to ""), SyncMerge.merge(mine, website, 5).prefs.values)
    }
}
