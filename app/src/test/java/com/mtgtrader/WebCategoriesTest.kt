package com.mtgtrader

import com.mtgtrader.data.WebAccountClient
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
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** The app's call to the website's table of auto categories (by card name), against a fake website. */
class WebCategoriesTest {
    /** Knows "Sol Ring" (Ramp) and "Cultivate" (Ramp, Tutor); answers [code] when it isn't 200. */
    private class FakeWebsite(var code: Int = 200, var body: String? = null) : Interceptor {
        val seen = mutableListOf<Request>()
        val sentNames = mutableListOf<List<String>>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            seen += req
            val sent = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
            val names = Json.parseToJsonElement(sent).jsonObject["names"]!!.jsonArray.map { it.jsonPrimitive.content }
            sentNames += names
            val answer = body ?: buildString {
                append("""{"categories":{""")
                append(names.mapNotNull { n ->
                    when (n) {
                        "Sol Ring" -> "\"Sol Ring\":[\"Ramp\"]"
                        "Cultivate" -> "\"Cultivate\":[\"Ramp\",\"Tutor\"]"
                        else -> null
                    }
                }.joinToString(","))
                append("}}")
            }
            return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("")
                .body(answer.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private val site = FakeWebsite()
    private val client = WebAccountClient(OkHttpClient.Builder().addInterceptor(site).build(), "https://web.test/")

    @Test
    fun sendsNamesAndReadsTheCategories() = runBlocking {
        val got = client.autoCategories(listOf("Sol Ring", "Cultivate", "Unknown Card"))
        assertEquals(mapOf("Sol Ring" to listOf("Ramp"), "Cultivate" to listOf("Ramp", "Tutor")), got)
        val req = site.seen.single()
        assertEquals("POST", req.method)
        assertEquals("/api/cards/categories", req.url.encodedPath)
        assertEquals(listOf("Sol Ring", "Cultivate", "Unknown Card"), site.sentNames.single())
        assertEquals(null, req.header("Authorization"))
    }

    @Test
    fun asksInChunksOf500AndMergesTheAnswers() = runBlocking {
        val names = (1..1200).map { "Card $it" } + "Sol Ring"
        val got = client.autoCategories(names)
        assertEquals(listOf(500, 500, 201), site.sentNames.map { it.size })
        assertEquals(mapOf("Sol Ring" to listOf("Ramp")), got)
    }

    @Test
    fun throwsWhenTheWebsiteFailsOrAnswersBadly() = runBlocking {
        site.code = 500
        try {
            client.autoCategories(listOf("Sol Ring"))
            fail()
        } catch (_: IOException) {
        }
        site.code = 200
        site.body = "not json"
        try {
            client.autoCategories(listOf("Sol Ring"))
            fail()
        } catch (_: Exception) {
        }
    }
}
