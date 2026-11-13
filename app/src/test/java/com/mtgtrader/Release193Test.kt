package com.mtgtrader

import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.PriceSourceStore
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.SourcePrice
import com.mtgtrader.data.WebAccountClient
import com.mtgtrader.data.WebLogin
import com.mtgtrader.data.readCardKingdomCopy
import com.mtgtrader.data.readCardKingdomWeb
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 1.93: phones logged in to an account get TCGplayer's and Card Kingdom's prices from the website first, like
 * Cardmarket's since 1.81, with Scryfall, Card Kingdom and the GitHub copy as before when the website can't help.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release193Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var net: FakeNet
    private val now = System.currentTimeMillis()

    /** The website, Scryfall, Card Kingdom and GitHub in one: answers by address and remembers what was asked. */
    private inner class FakeNet : Interceptor {
        val asked = mutableListOf<String>()
        val scryfallIds = mutableListOf<String>()
        var tcgplayer: String = """{"cards":{}}"""
        var tcgplayerCode = 200
        var ckFile: ByteArray = webCardKingdomFile()
        var ckCode = 200
        val ckEtag = "\"ck1\""

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val path = req.url.encodedPath
            asked += "${req.url.host}$path"
            fun answer(code: Int, body: ByteArray = ByteArray(0), type: String = "application/json", etag: String? = null) = Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(code).message("")
                .apply { if (etag != null) header("ETag", etag) }
                .body(body.toResponseBody(type.toMediaType())).build()
            return when {
                req.url.host == "fake.test" && req.header("Authorization") != "Bearer tok" -> answer(401)
                path == "/api/app/tcgplayer" -> answer(tcgplayerCode, tcgplayer.toByteArray())
                path == "/api/app/cardkingdom" -> when {
                    ckCode != 200 -> answer(ckCode)
                    req.header("If-None-Match") == ckEtag -> answer(304)
                    else -> answer(200, ckFile, "application/gzip", ckEtag)
                }
                path == "/cards/collection" -> {
                    val sent = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
                    Regex(""""id":"([^"]+)"""").findAll(sent).forEach { scryfallIds += it.groupValues[1] }
                    answer(200, """{"object":"list","not_found":[],"data":[]}""".toByteArray())
                }
                path == "/ck-direct" -> answer(403)
                path == "/ck-copy" -> answer(200, resource("pricelist-copy.tsv.gz"), "application/gzip")
                else -> answer(404)
            }
        }
    }

    private fun resource(name: String) = javaClass.getResourceAsStream("/cardkingdom/$name")!!.readBytes()

    private fun copyRows(): List<SourcePrice> = runBlocking {
        val rows = mutableListOf<SourcePrice>()
        readCardKingdomCopy(GZIPInputStream(resource("pricelist-copy.tsv.gz").inputStream()), 7L) { rows += it }
        rows
    }

    /** The GitHub copy in the website's layout (web/src/lib/cardkingdom.ts CkFile), as the website stores it. */
    private fun webCardKingdomFile(): ByteArray {
        fun num(d: Double?) = d?.let { JsonPrimitive(it) } ?: JsonNull
        val file = buildJsonObject {
            put("listDate", "2026-10-10 03:05:18")
            put("madeAt", 1L)
            put("rows", buildJsonObject {
                for (r in copyRows()) put(
                    "${r.scryfallId}|${if (r.finish == "FOIL") "F" else "N"}",
                    JsonArray(listOf(num(r.price), num(r.buy), num(r.nm), num(r.ex), num(r.vg), num(r.g), JsonPrimitive(r.url?.removePrefix("https://www.cardkingdom.com/") ?: ""))),
                )
            })
        }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(file.toString().toByteArray()) }
        return out.toByteArray()
    }

    @Before
    fun setUp() {
        context.getSharedPreferences("price_sources", 0).edit().clear().commit()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        net = FakeNet()
        runBlocking {
            for (id in listOf("fresh", "old", "unknown")) db.collectionDao().insert(
                CollectionItem(card = CardRef(id, "Card $id", "tst", "Test", "1", "rare", null, null, null, null, true, true), foil = false, quantity = 1),
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun store(loggedIn: Boolean = true): PriceSourceStore {
        val http = OkHttpClient.Builder().addInterceptor(net).build()
        val web = WebAccountClient(http, base = "https://fake.test/")
        return PriceSourceStore(
            context, http, db, ScryfallApi(http), cardKingdomUrl = "https://ck.test/ck-direct", cardKingdomCopyUrl = "https://gh.test/ck-copy",
            web = { if (loggedIn) web to WebLogin("joel", "tok") else null },
        )
    }

    @Test
    fun theWebsitesCardKingdomFileHoldsTheSamePricesAsTheCopy() = runBlocking {
        var date: String? = null
        val rows = mutableListOf<SourcePrice>()
        readCardKingdomWeb(GZIPInputStream(webCardKingdomFile().inputStream()), 7L, { date = it }) { rows += it }
        assertEquals("2026-10-10 03:05:18", date)
        assertEquals(copyRows().associateBy { it.scryfallId to it.finish }, rows.associateBy { it.scryfallId to it.finish })
    }

    @Test
    fun tcgplayerFromTheWebsiteAndOnlyTheRestFromScryfall() = runBlocking {
        val old = now - 3 * 24 * 3_600_000L
        net.tcgplayer = """{"cards":{"fresh":{"u":[1.25,4.5,null],"tu":"https://tcgplayer.example/fresh","at":$now},"old":{"u":[9.0,null,null],"tu":null,"at":$old}}}"""
        assertTrue(store().refresh())
        val fresh = db.sourcePriceDao().forIds(listOf("fresh")).filter { it.source == "tcgplayer" }.associateBy { it.finish }
        assertEquals(1.25, fresh.getValue("NONFOIL").price!!, 0.0)
        assertEquals(4.5, fresh.getValue("FOIL").price!!, 0.0)
        assertEquals("https://tcgplayer.example/fresh", fresh.getValue("NONFOIL").url)
        assertEquals(now, fresh.getValue("NONFOIL").updatedAt)
        // Too old on the website, or not there: Scryfall, as before.
        assertEquals(listOf("old", "unknown"), net.scryfallIds.sorted())
        assertTrue(db.sourcePriceDao().forIds(listOf("old")).none { it.source == "tcgplayer" })
    }

    @Test
    fun scryfallAnswersEverythingWhenTheWebsiteCantOrThereIsNoAccount() = runBlocking {
        net.tcgplayerCode = 500
        assertTrue(store().refresh())
        assertEquals(listOf("fresh", "old", "unknown"), net.scryfallIds.sorted())

        net.scryfallIds.clear()
        net.asked.clear()
        net.tcgplayerCode = 200
        assertTrue(store(loggedIn = false).refresh())
        assertEquals(listOf("fresh", "old", "unknown"), net.scryfallIds.sorted())
        assertTrue(net.asked.none { it.startsWith("fake.test") })
    }

    @Test
    fun cardKingdomFromTheWebsiteAndNotAgainWhenUnchanged() = runBlocking {
        val s = store()
        assertTrue(s.refresh())
        assertEquals(1203, db.sourcePriceDao().count("cardkingdom"))
        assertEquals(PriceSourceStore.WEBSITE, s.status.value.cardKingdomVia)
        assertEquals("2026-10-10 03:05:18", s.status.value.cardKingdomListDate)
        assertTrue(net.asked.none { it.startsWith("ck.test") || it.startsWith("gh.test") })

        // The next update: the website says the phone has its file (304), nothing is downloaded or removed.
        net.ckFile = ByteArray(0)
        assertTrue(s.refresh())
        assertEquals(1203, db.sourcePriceDao().count("cardkingdom"))
        assertEquals(PriceSourceStore.WEBSITE, s.status.value.cardKingdomVia)
        assertEquals("2026-10-10 03:05:18", s.status.value.cardKingdomListDate)
    }

    @Test
    fun cardKingdomFallsBackWhenTheWebsitesFileIsOutOfDate() = runBlocking {
        net.ckCode = 503
        val s = store()
        assertTrue(s.refresh())
        // Card Kingdom refuses (403 here), so GitHub's copy, as before 1.93.
        assertTrue(net.asked.contains("ck.test/ck-direct"))
        assertEquals("GitHub copy", s.status.value.cardKingdomVia)
        assertEquals(1203, db.sourcePriceDao().count("cardkingdom"))
    }
}
