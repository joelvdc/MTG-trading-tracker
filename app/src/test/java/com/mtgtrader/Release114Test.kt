package com.mtgtrader

import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.LocalRating
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltCombo
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * 1.114: the app's estimate of a local deck's bracket and salt from the website (POST /api/rate), kept as a [SaltCard]
 * with source "app": the list's fingerprint, when the rating is stale, reading both ratings' JSON, the words that say
 * why a bracket, and the request the website gets.
 */
class Release114Test {
    // ---- listKey ---------------------------------------------------------------------------------

    private fun card(name: String, quantity: Int = 1, commander: Boolean = false, maybeboard: Boolean = false) = DeckCard(
        deckId = -5, card = CardRef("id-$name", name, "tst", "Test", "1", "rare", null, null, null, null, true, true),
        quantity = quantity, category = "Ramp", types = "Artifact", cmc = 1.0, commander = commander, foil = false,
        maybeboard = maybeboard,
    )

    /** Sol Ring ×1, Atraxa ×1 (commander), Fire // Ice ×2, Island ×30: the website's fingerprint for the same list. */
    private val sample = listOf(
        card("Sol Ring"),
        card("Atraxa, Praetors' Voice", commander = true),
        card("Fire // Ice", quantity = 2),
        card("Island", quantity = 30),
    )

    @Test
    fun theFingerprintMatchesTheWebsites() {
        assertEquals("1e63c951", LocalRating.listKey(sample))
        // The order of the lines doesn't matter, nor a card's other face's name in capitals; the maybeboard doesn't count.
        assertEquals("1e63c951", LocalRating.listKey(sample.reversed() + card("Consider", maybeboard = true)))
        assertEquals("1e63c951", LocalRating.listKey(sample.filter { !it.commander } + card("atraxa,  praetors'  voice", commander = true)))
        // A quantity or a commander changes it.
        assertTrue(LocalRating.listKey(sample.map { if (it.card.name == "Sol Ring") it.copy(quantity = 2) else it }) != "1e63c951")
        assertTrue(LocalRating.listKey(sample.map { if (it.commander) it.copy(commander = false) else it }) != "1e63c951")
    }

    // ---- stale -----------------------------------------------------------------------------------

    private fun deck(local: Boolean = true, saltCard: String? = null, scoredAt: Long = 2_000_000_000_000L) =
        Deck(if (local) -5 else 5, "Brew", "", "Atraxa, Praetors' Voice", null, null, "WUBG", 34, saltCard = saltCard, scoredAt = scoredAt)

    private fun rated(source: String? = "app", listKey: String? = "1e63c951", ratingVersion: Int? = LocalRating.RATING_VERSION) =
        SaltCard(source = source, bracket = 3, listKey = listKey, ratingVersion = ratingVersion).encode()

    @Test
    fun aRatingIsStaleOnlyWhileTheDeckNoLongerHoldsIt() {
        assertEquals(3, LocalRating.RATING_VERSION)
        assertTrue("no rating yet", LocalRating.stale(deck(), sample))
        assertFalse("a fresh rating", LocalRating.stale(deck(saltCard = rated()), sample))
        assertTrue("another list", LocalRating.stale(deck(saltCard = rated(listKey = "00000000")), sample))
        assertTrue("no fingerprint kept (older)", LocalRating.stale(deck(saltCard = rated(listKey = null)), sample))
        assertTrue("another rating version", LocalRating.stale(deck(saltCard = rated(ratingVersion = 0)), sample))
        // Since 1.116 every deck is rated the same way, local or from Archidekt; Commander Salt's own data (made local
        // from Archidekt) no longer counts and is made again.
        val own = SaltCard(gameChangers = listOf("Smothering Tithe"), version = SaltCard.CURRENT).encode()
        assertTrue("Commander Salt's own data", LocalRating.stale(deck(saltCard = own), sample))
        assertTrue("not rated before, local=false", LocalRating.stale(deck(local = false), sample))
        assertFalse("rated, local=false", LocalRating.stale(deck(local = false, saltCard = rated()), sample))
        // Made before the website counted land tutors right (RATED_SINCE): asked for again.
        assertTrue("rated before RATED_SINCE", LocalRating.stale(deck(saltCard = rated(), scoredAt = LocalRating.RATED_SINCE - 1), sample))
        assertTrue("never scored", LocalRating.stale(deck(saltCard = rated(), scoredAt = 0), sample))
        // Nor is a deck without cards.
        assertFalse(LocalRating.stale(deck(), emptyList()))
    }

    // ---- SaltCard.decode -------------------------------------------------------------------------

    @Test
    fun readsTheWebsitesRating() {
        val json = """
            {"gameChangers":["Smothering Tithe"],"twoCardCombos":[["Exquisite Blood","Sanguine Bond"]],"saltIntensity":"Mild",
             "combos":[{"cards":["Exquisite Blood","Sanguine Bond"],"counts":true,"manaValue":9.0,"wins":true}],
             "source":"app","bracket":4,"bracketWhy":["Two early combos"],"salt":7.3,
             "saltiest":[{"name":"Winter Orb","salt":2.4},{"name":"Armageddon","salt":2.1}],
             "listKey":"1e63c951","ratingVersion":1}
        """.trimIndent()
        val card = SaltCard.decode(json)!!
        assertTrue(card.fromApp)
        assertEquals(4, card.bracket)
        assertEquals(listOf("Two early combos"), card.bracketWhy)
        assertEquals(7.3, card.salt!!, 0.0)
        assertEquals(listOf("Winter Orb" to 2.4, "Armageddon" to 2.1), card.saltiest.map { it.name to it.salt })
        assertEquals("1e63c951", card.listKey)
        assertEquals(1, card.ratingVersion)
        assertEquals(9.0, card.combos.single().manaValue!!, 0.0)
        assertEquals(true, card.combos.single().wins)
    }

    @Test
    fun olderDataStillReads() {
        val card = SaltCard.decode("""{"gameChangers":["A"],"version":4}""")!!
        assertFalse(card.fromApp)
        assertNull(card.source)
        assertNull(card.bracket)
        assertNull(card.salt)
        assertNull(card.listKey)
        assertNull(card.ratingVersion)
        assertTrue(card.bracketWhy.isEmpty())
        assertTrue(card.saltiest.isEmpty())
        val combo = SaltCard.decode("""{"combos":[{"cards":["A","B"],"counts":true}]}""")!!.combos.single()
        assertNull(combo.manaValue)
        assertNull(combo.wins)
    }

    // ---- bracketReasons --------------------------------------------------------------------------

    @Test
    fun whyTheBracketInTheWebsitesWords() {
        // The website's own lines win.
        val app = SaltCard(source = "app", bracket = 3, bracketWhy = listOf("5 tutors"), tutors = List(6) { "Tutor $it" })
        assertEquals(listOf("5 tutors"), app.bracketReasons(3))
        // Nothing for a deck without a bracket.
        assertTrue(SaltCard().bracketReasons(null).isEmpty())
        assertTrue(SaltCard().bracketReasons(3).isEmpty())
        // Brackets 1 and 2.
        assertEquals(listOf("No Game Changers or two-card combos"), SaltCard(gameChangers = listOf("A")).bracketReasons(2))
        // Bracket 3: its two-card combos (what the app knows of them), the Game Changers, the tutors.
        val three = SaltCard(
            twoCardCombos = listOf(listOf("Exquisite Blood", "Sanguine Bond"), listOf("Dramatic Reversal", "Isochron Scepter"), listOf("A", "B"), listOf("C", "D")),
            combos = listOf(
                SaltCombo(listOf("Sanguine Bond", "Exquisite Blood"), counts = true, manaValue = 9.0, wins = true),
                SaltCombo(listOf("isochron scepter", "dramatic reversal"), counts = true, manaValue = 4.0, wins = false),
            ),
            gameChangers = listOf("Rhystic Study", "Demonic Tutor", "Jeska's Will"),
            tutors = listOf("Demonic Tutor", "Vampiric Tutor", "Enlightened Tutor", "Mystical Tutor", "Worldly Tutor"),
        )
        assertEquals(
            listOf(
                "Exquisite Blood + Sanguine Bond: wins, mana value 9",
                "Dramatic Reversal + Isochron Scepter: doesn't win",
                "A + B",
                "and 1 more two-card combos",
                "3 Game Changers: Rhystic Study, Demonic Tutor, Jeska's Will",
                "5 tutors",
            ),
            three.bracketReasons(3),
        )
        // Bracket 4: its early combos, Game Changers, mass land denial, extra turns.
        val four = SaltCard(
            earlyCombos = listOf(listOf("A", "B"), listOf("C", "D"), listOf("E", "F"), listOf("G", "H")),
            combos = listOf(SaltCombo(listOf("B", "A"), counts = true, manaValue = 6.0)),
            gameChangers = listOf("Smothering Tithe"),
            massLandDenial = listOf("Winter Orb", "Armageddon"),
            extraTurns = listOf("Time Warp", "Nexus of Fate", "Capture of Jingzhou"),
        )
        assertEquals(
            listOf(
                "A + B: wins, mana value 6",
                "C + D",
                "E + F",
                "and 1 more early combos",
                "1 Game Changer: Smothering Tithe",
                "Mass land denial: Winter Orb, Armageddon",
                "3 extra-turn cards: Time Warp, Nexus of Fate, Capture of Jingzhou",
            ),
            four.bracketReasons(4),
        )
        // Fewer than three extra-turn cards say nothing; so do fewer than five tutors at bracket 3.
        assertTrue(SaltCard(extraTurns = listOf("Time Warp", "Nexus of Fate")).bracketReasons(4).isEmpty())
        assertTrue(SaltCard(tutors = List(4) { "T$it" }).bracketReasons(3).isEmpty())
    }

    // ---- the request -----------------------------------------------------------------------------

    /** The website's /api/rate: answers [answer], or [code] when it isn't 200. */
    private class FakeRate(var code: Int = 200, var answer: String = "") : Interceptor {
        val seen = mutableListOf<Request>()
        val sent = mutableListOf<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            seen += req
            sent += Buffer().also { req.body!!.writeTo(it) }.readUtf8()
            return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("")
                .body(answer.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private val site = FakeRate()
    private val client = WebAccountClient(OkHttpClient.Builder().addInterceptor(site).build(), "https://web.test/")
    private val rating = SaltCard(source = "app", bracket = 3, salt = 4.2, listKey = "1e63c951", ratingVersion = 1)

    @Test
    fun theWebsiteIsAskedForTheDecksRating() = runBlocking {
        val quoted = rating.encode().replace("\\", "\\\\").replace("\"", "\\\"")
        site.answer = """{"saltCard":"$quoted"}"""
        val got = client.rate(sample + card("Consider", maybeboard = true), "WUBG")
        assertEquals(rating, SaltCard.decode(got))

        val req = site.seen.single()
        assertEquals("POST", req.method)
        assertEquals("/api/rate", req.url.encodedPath)
        assertEquals("mtgtrader", req.header("x-requested-with"))
        assertNull(req.header("Authorization"))
        val body = Json.parseToJsonElement(site.sent.single()).jsonObject
        assertEquals("WUBG", body["identity"]!!.jsonPrimitive.content)
        val sent = body["cards"]!!.jsonArray.map { it.jsonObject }
        // The maybeboard's card stays at home.
        assertEquals(listOf("Sol Ring", "Atraxa, Praetors' Voice", "Fire // Ice", "Island"), sent.map { it["name"]!!.jsonPrimitive.content })
        assertEquals(30, sent.last()["quantity"]!!.jsonPrimitive.content.toInt())
        assertTrue(sent[1]["commander"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(1.0, sent[0]["cmc"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals("Artifact", sent[0]["types"]!!.jsonPrimitive.content)
    }

    @Test
    fun aWebsiteThatCantRateIsAnError() = runBlocking {
        for ((code, answer) in listOf(503 to "", 403 to """{"error":"Bad request"}""", 200 to "not json", 200 to """{"other":1}""", 200 to """{"saltCard":"not a card"}""")) {
            site.code = code
            site.answer = answer
            try {
                client.rate(sample, "WUBG")
                fail("$code $answer should be an error")
            } catch (_: IOException) {
            }
        }
    }
}
