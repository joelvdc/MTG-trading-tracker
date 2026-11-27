package com.mtgtrader

import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.LocalRating
import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.SaltCombo
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WebAccountClient
import com.mtgtrader.data.WebLogin
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinOddsCalc
import com.mtgtrader.data.WinOddsStore
import com.mtgtrader.data.WinTurns
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.123: Spellbook's tag with its bracket on combo lines, and the win turns shared with the website. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release1123Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var dir: File

    @Before
    fun setUp() {
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dir = File(context.cacheDir, "winodds-1123").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() = db.close()

    private fun ref(name: String) = CardRef(name.lowercase(), name, "tst", "Test", "1", "common", null, null, null, null, true, false)

    private suspend fun deck(id: Long = 7L, saltCard: String? = null): Deck {
        val d = Deck(id, "Test deck", "joel", "Commander", null, null, "W", 100, saltCard = saltCard)
        db.deckDao().upsert(d)
        db.deckDao().insertCards(
            listOf(
                DeckCard(deckId = id, card = ref("Commander"), quantity = 1, category = "Commander", types = "Legendary Creature", cmc = 3.0, commander = true, foil = false),
                DeckCard(deckId = id, card = ref("Plains"), quantity = 40, category = "Land", types = "Basic Land", cmc = 0.0, commander = false, foil = false),
                DeckCard(deckId = id, card = ref("Bear"), quantity = 59, category = "Creature", types = "Creature", cmc = 2.0, commander = false, foil = false),
            ),
        )
        return d
    }

    private fun card(name: String, type: String, cost: String, pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "", text: String = "") =
        StatsCard(name, name.lowercase(), quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    /** A real result (few games, quick). */
    private val odds: WinOdds = WinOddsCalc.calculate(
        listOf(
            card("Commander", "Legendary Creature — Human", "{2}{W}", "3/3", commander = true),
            card("Plains", "Basic Land — Plains", "", quantity = 40, produced = "W", text = "({T}: Add {W}.)"),
            card("Bear", "Creature — Bear", "{1}{W}", "2/2", quantity = 59),
        ),
        "W", emptyList(), games = 200,
    )!!

    @Test fun combosShowTheirTagWithItsBracket() {
        assertEquals("Spicy (bracket 3-4)", SaltCombo(listOf("A", "B"), bracketTag = "S").tagText)
        assertEquals("Ruthless (bracket 4+)", SaltCombo(listOf("A", "B"), bracketTag = "R").tagText)
        assertEquals("Exhibition (any bracket)", SaltCombo(listOf("A", "B"), bracketTag = "E").tagText)
        assertNull(SaltCombo(listOf("A", "B")).tagText)
    }

    /** The rating's fingerprint is the website's (web/src/lib/winturns.ts saltHash, worked out there for this text). */
    @Test fun theRatingFingerprintMatchesTheWebsites() {
        assertEquals("811c9dc5", LocalRating.saltHash(null))
        assertEquals(LocalRating.saltHash(null), LocalRating.saltHash(""))
        assertEquals("9e6f1ceb", LocalRating.saltHash("""{"bracket":3,"combos":[]}"""))
    }

    @Test fun aWinTurnTravelsInTheWebsitesShape() {
        val text = WinOddsStore.toWebJson(odds)
        val o = Json.parseToJsonElement(text).jsonObject
        assertEquals(odds.label, o["label"]!!.jsonPrimitive.content)
        assertEquals(odds.fourInTen, o["fourInTen"]!!.jsonPrimitive.content.toIntOrNull())
        assertTrue("odds" in o && "windowStart" in o && "damageText" in o)
        assertEquals(odds, WinOddsStore.fromWebJson(text))
        assertNull(WinOddsStore.fromWebJson("null"))
        assertNull(WinOddsStore.fromWebJson("{\"label\":\"T5\"}"))
    }

    /** A website result is kept like one worked out here; only one worked out here is sent up. */
    @Test fun aWebsiteResultIsKeptAndNotSentBack() = runBlocking {
        val d = deck()
        val store = WinOddsStore(dir, db)
        val sent = mutableListOf<Long>()
        store.onWorkedOut = { id, _ -> sent += id }
        assertFalse(store.fillKept(d))
        assertTrue(store.adopt(d, odds))
        assertTrue(store.fillKept(d))
        assertEquals(odds, WinTurns.of(d.archidektId))
        assertEquals(odds, store.forDeck(d))
        assertEquals(emptyList<Long>(), sent)

        // Worked out here (another deck): heard, to be sent.
        val other = deck(8L)
        assertNotNull(WinOddsStore(dir, db).also { it.onWorkedOut = { id, _ -> sent += id } }.forDeck(other))
        assertEquals(listOf(8L), sent)
    }

    /** The website's answer is read, and the phone's results are sent in its shape. */
    @Test fun theWebsitesWinTurnAddress() = runBlocking {
        val asked = mutableListOf<String>()
        var posted = ""
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val req = chain.request()
            asked += "${req.method} ${req.url.encodedPath} ${req.header("Authorization")}"
            val body = if (req.method == "GET") {
                """{"version":${WinOddsCalc.VERSION},"decks":[{"deckId":7,"listKey":"abc","version":${WinOddsCalc.VERSION},"identity":"W","saltHash":"811c9dc5","result":"{\"odds\":{}}","at":1},{"bad":1}]}"""
            } else {
                posted = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
                """{"kept":[7]}"""
            }
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        val web = WebAccountClient(http, base = "https://fake.test/")
        val login = WebLogin("joel", "tok")
        val got = web.winTurns(login)
        assertEquals(listOf(WebAccountClient.WebWinTurn(7, "abc", WinOddsCalc.VERSION, "W", "811c9dc5", "{\"odds\":{}}")), got)
        assertEquals(listOf(7L), web.sendWinTurns(login, got))
        val item = Json.parseToJsonElement(posted).jsonObject["results"]!!.jsonArray.single().jsonObject
        assertEquals("abc", item["listKey"]!!.jsonPrimitive.content)
        assertEquals("811c9dc5", item["saltHash"]!!.jsonPrimitive.content)
        assertEquals(listOf("GET /api/winturns Bearer tok", "POST /api/winturns Bearer tok"), asked)
    }
}
