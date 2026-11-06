package com.mtgtrader

import com.mtgtrader.data.NextcloudClient
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncException
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.SyncPrefs
import com.mtgtrader.data.WebAccountClient
import com.mtgtrader.data.WebLogin
import kotlinx.coroutines.runBlocking
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
}
