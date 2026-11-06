package com.mtgtrader

import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.CardRules
import com.mtgtrader.data.CardRulesRepository
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.toRuleFaces
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** 1.81: card text, legalities and rulings from Scryfall, kept in files on the phone for 30 days. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CardRulesTest {
    companion object {
        private val parser = Json { ignoreUnknownKeys = true }
        const val ID ="11111111-2222-3333-4444-555555555555"

        /** A double-faced planeswalker with legalities, as Scryfall writes it (shortened). */
        val CARD_JSON = """
            {"object":"card","id":"$ID","name":"Jace, Vryn's Prodigy // Jace, Telepath Unbound","set":"ori","set_name":"Magic Origins",
             "collector_number":"60","layout":"transform","type_line":"Legendary Creature — Human Wizard // Legendary Planeswalker — Jace",
             "legalities":{"standard":"not_legal","commander":"legal","modern":"banned","vintage":"restricted","brawl":"legal"},
             "card_faces":[
              {"name":"Jace, Vryn's Prodigy","mana_cost":"{1}{U}","type_line":"Legendary Creature — Human Wizard",
               "oracle_text":"{T}: Draw a card, then discard a card.","power":"0","toughness":"2"},
              {"name":"Jace, Telepath Unbound","mana_cost":"","type_line":"Legendary Planeswalker — Jace",
               "oracle_text":"+1: Up to one target creature gets -2/-0 until your next turn.","loyalty":"5"}]}
        """.trimIndent()

        val RULINGS_JSON = """
            {"object":"list","has_more":false,"data":[
             {"source":"wotc","published_at":"2015-06-22","comment":"Older ruling."},
             {"source":"wotc","published_at":"2020-01-01","comment":"Newer {T} ruling."}]}
        """.trimIndent()
    }

    private val dir = File(RuntimeEnvironment.getApplication().cacheDir, "card-rules-test-${System.nanoTime()}")
    private var requests = 0
    private var offline = false
    private var clock = 1_000_000_000_000L
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        requests++
        if (offline) throw IOException("offline")
        val path = chain.request().url.encodedPath
        val body = if (path.endsWith("/rulings")) RULINGS_JSON else CARD_JSON
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    private val repo get() = CardRulesRepository(dir, ScryfallApi(http)) { clock }

    @Test
    fun aTwoFacedCardWithLegalitiesAndRulingsIsParsed() = runBlocking {
        val rules = repo.get(ID)!!
        assertEquals(2, rules.faces.size)
        val front = rules.faces[0]
        assertEquals("Jace, Vryn's Prodigy", front.name)
        assertEquals("{1}{U}", front.manaCost)
        assertEquals("0/2", front.powerToughness)
        assertNull(front.loyalty)
        assertEquals("5", rules.faces[1].loyalty)
        assertEquals("Legendary Planeswalker — Jace", rules.faces[1].typeLine)
        assertEquals("banned", rules.legalities["modern"])
        assertEquals("restricted", rules.legalities["vintage"])
        assertEquals("legal", rules.legalities["commander"])
        // Newest first.
        assertEquals(listOf("2020-01-01", "2015-06-22"), rules.rulings.map { it.date })
        assertEquals("Newer {T} ruling.", rules.rulings[0].text)
    }

    @Test
    fun aFreshFileIsUsedWithoutTheNetwork() = runBlocking {
        repo.get(ID)
        assertEquals(2, requests)
        offline = true
        clock += 29L * 24 * 60 * 60 * 1000
        val again = repo.get(ID)!!
        assertEquals(2, requests)
        assertEquals(2, again.faces.size)
    }

    @Test
    fun aStaleFileIsAskedForAgain() = runBlocking {
        repo.get(ID)
        clock += CardRulesRepository.MAX_AGE_MS + 1
        val again = repo.get(ID)!!
        assertEquals(4, requests)
        assertEquals(clock, again.fetchedAt)
        assertEquals(clock, repo.cached(ID)!!.fetchedAt)
    }

    @Test
    fun aFailedRefreshKeepsTheSavedFile() = runBlocking {
        val first = repo.get(ID)!!
        clock += CardRulesRepository.MAX_AGE_MS + 1
        offline = true
        val again = repo.get(ID)
        assertEquals(first, again)
        assertEquals(first, repo.cached(ID))
    }

    @Test
    fun withNothingSavedAndNoNetworkThereIsNoAnswer() = runBlocking {
        offline = true
        assertNull(repo.get(ID))
        assertNull(repo.cached(ID))
        assertTrue(!File(dir, "$ID.json").exists())
    }

    @Test
    fun theSavedFileSurvivesBeingReadBack() = runBlocking {
        val saved = repo.get(ID)!!
        val json = File(dir, "$ID.json").readText()
        assertEquals(saved, parser.decodeFromString<CardRules>(json))
        assertNotNull(repo.cached(ID))
    }

    @Test
    fun detailsAlreadyOnThePhoneGiveTheFacesForTheMeantime() {
        val info = CardInfo(
            ID, null, "U", "U", "Legendary Creature — Human Wizard // Legendary Planeswalker — Jace", 2.0, null,
            manaCost = "{1}{U} // ", oracleText = "{T}: Draw." + CardInfo.FACE_BREAK + "+1: Do.", powerToughness = "0/2", loyalty = "5",
        )
        val faces = info.toRuleFaces("Jace, Vryn's Prodigy // Jace, Telepath Unbound")
        assertEquals(listOf("Jace, Vryn's Prodigy", "Jace, Telepath Unbound"), faces.map { it.name })
        assertEquals("{1}{U}", faces[0].manaCost)
        assertEquals("", faces[1].manaCost)
        assertEquals("0/2", faces[0].powerToughness)
        assertNull(faces[1].powerToughness)
        assertEquals("+1: Do.", faces[1].oracleText)
    }
}
