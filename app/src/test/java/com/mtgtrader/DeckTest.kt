package com.mtgtrader

import com.mtgtrader.data.ArchidektApi
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CommanderSaltApi
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckCardRow
import com.mtgtrader.data.DeckGroupBy
import com.mtgtrader.data.DeckGrouping
import com.mtgtrader.data.DeckLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DeckLinksTest {
    @Test
    fun findsDeckIdInLinksAndSharedText() {
        assertEquals(20263351L, DeckLinks.archidektId("https://archidekt.com/decks/20263351/auntie_plague"))
        assertEquals(20263351L, DeckLinks.archidektId("archidekt.com/decks/20263351"))
        assertEquals(20263351L, DeckLinks.archidektId("https://www.archidekt.com/decks/20263351#maybeboard"))
        assertEquals(20263351L, DeckLinks.archidektId("https://archidekt.com/api/decks/20263351/"))
        assertEquals(20263351L, DeckLinks.archidektId("Check out my deck Auntie Plague https://archidekt.com/decks/20263351/auntie_plague"))
        assertEquals(20263351L, DeckLinks.archidektId(" 20263351 "))
    }

    @Test
    fun rejectsOtherText() {
        assertNull(DeckLinks.archidektId("https://moxfield.com/decks/abc123"))
        assertNull(DeckLinks.archidektId("Auntie Plague"))
        assertNull(DeckLinks.archidektId(""))
    }
}

class DeckGroupingTest {
    private fun row(name: String, category: String, types: String, commander: Boolean = false, qty: Int = 1) = DeckCardRow(
        DeckCard(
            deckId = 1,
            card = CardRef(name, name, "tst", "Test", "1", "rare", null, null, null, null, true, false),
            quantity = qty,
            category = category,
            types = types,
            cmc = 0.0,
            commander = commander,
            foil = false,
        ),
        null,
    )

    @Test
    fun primaryTypeFollowsDeckbuilderOrder() {
        assertEquals("Creature", DeckGrouping.primaryType("Artifact Creature"))
        assertEquals("Creature", DeckGrouping.primaryType("Land Creature"))
        assertEquals("Land", DeckGrouping.primaryType("Artifact Land"))
        assertEquals("Instant", DeckGrouping.primaryType("Kindred Instant"))
        assertEquals("Other", DeckGrouping.primaryType(""))
    }

    @Test
    fun commanderComesFirstThenCategoriesAlphabetically() {
        val rows = listOf(
            row("Swamp", "Land", "Land", qty = 10),
            row("Sol Ring", "Ramp", "Artifact"),
            row("Auntie Ool", "Commander", "Creature", commander = true),
            row("Arcane Signet", "Ramp", "Artifact"),
            row("Blight Mound", "Creatures", "Creature"),
        )
        val sections = DeckGrouping.sections(rows, DeckGroupBy.CATEGORY)
        assertEquals(listOf("Commander", "Creatures", "Land", "Ramp"), sections.map { it.title })
        assertEquals(listOf("Arcane Signet", "Sol Ring"), sections[3].rows.map { it.item.card.name })
        assertEquals(10, sections[2].count)
    }

    @Test
    fun typeGroupingUsesTypeOrder() {
        val rows = listOf(
            row("Swamp", "Land", "Land"),
            row("Sol Ring", "Ramp", "Artifact"),
            row("Auntie Ool", "Commander", "Creature", commander = true),
            row("Blight Mound", "Creatures", "Creature"),
            row("Malakir Rebirth", "Removal", "Instant"),
        )
        val titles = DeckGrouping.sections(rows, DeckGroupBy.TYPE).map { it.title }
        assertEquals(listOf("Commander", "Creatures", "Lands", "Instants", "Artifacts"), titles)
    }
}

class ArchidektParseTest {
    private val sample = """
        {"id": 20263351, "name": "Auntie Plague", "owner": {"id": 1, "username": "joelvdc"},
         "featured": "https://example.com/art.jpg", "customFeatured": "", "private": false,
         "categories": [
           {"name": "Commander", "isPremier": true, "includedInDeck": true},
           {"name": "Ramp", "isPremier": false, "includedInDeck": true},
           {"name": "Maybeboard", "isPremier": false, "includedInDeck": false}
         ],
         "cards": [
           {"categories": ["Commander"], "quantity": 1, "modifier": "Normal",
            "card": {"uid": "9a2e4252-9fbc-4d43-8935-db2cafaa7b5f", "collectorNumber": "1",
                     "edition": {"editioncode": "ecc", "editionname": "Commander"},
                     "oracleCard": {"name": "Auntie Ool, Cursewretch", "cmc": 5, "types": ["Creature"],
                                    "colorIdentity": ["Black", "Red", "Green"], "gameChanger": false}}},
           {"categories": ["Ramp", "Mana"], "quantity": 1, "modifier": "Foil",
            "card": {"uid": "abc12345", "collectorNumber": "2", "edition": {"editioncode": "cmm", "editionname": "Masters"},
                     "oracleCard": {"name": "Sol Ring", "cmc": 1, "types": ["Artifact"], "colorIdentity": [], "gameChanger": true}}},
           {"categories": ["Maybeboard"], "quantity": 1, "modifier": "Normal",
            "card": {"uid": "def67890", "oracleCard": {"name": "The Ozolith", "types": ["Artifact"]}}},
           {"categories": [], "quantity": 7, "modifier": "Normal",
            "card": {"uid": "0123abcd", "oracleCard": {"name": "Swamp", "types": ["Land"], "defaultCategory": "Land"}}}
         ]}
    """.trimIndent()

    @Test
    fun keepsDeckCardsAndMarksCommander() {
        val d = ArchidektApi.parse(sample)
        assertEquals("Auntie Plague", d.name)
        assertEquals("joelvdc", d.owner)
        assertEquals("https://example.com/art.jpg", d.artUrl)
        assertEquals(listOf("Auntie Ool, Cursewretch", "Sol Ring", "Swamp"), d.cards.map { it.name })
        assertEquals(9, d.cardCount)
        assertEquals("BRG", d.colorIdentity)
        assertEquals(listOf("Auntie Ool, Cursewretch"), d.commanders.map { it.name })
        val sol = d.cards[1]
        assertTrue(sol.foil)
        assertFalse(sol.etched)
        assertTrue(sol.gameChanger)
        assertEquals("Ramp", sol.category)
        assertEquals("Land", d.cards[2].category)
        assertEquals("https://cards.scryfall.io/normal/front/9/a/9a2e4252-9fbc-4d43-8935-db2cafaa7b5f.jpg", d.cards[0].imageUrl)
    }
}

class CommanderSaltTest {
    @Test
    fun readsScoresFromDeckReply() {
        val body = """
            {"id": "aee8080ad777055f553ef746883373fa", "powerLevelRating": 4.432166, "bracketRating": 3,
             "archetypeLabel": "Combo / Control", "keyMap": {"keyMap": {"gg": "a", "gG": "b"}},
             "details": {"brackets": {"csBracket": 2, "wotcBracket": 3, "displayBracket": 3},
                         "salt": {"profile": {"headline": {"saltPercentage": 51.4}}},
                         "powerLevel": {"ratings": {"overall": 4.43}}}}
        """.trimIndent()
        val s = CommanderSaltApi.parse(body)
        assertEquals("aee8080ad777055f553ef746883373fa", s.saltId)
        assertEquals(4.432166, s.powerLevel!!, 1e-9)
        assertEquals(2, s.bracketRealistic)
        assertEquals(3, s.bracketBaseline)
        assertEquals(51.4, s.saltPercent!!, 1e-9)
        assertEquals("Combo / Control", s.archetype)
        assertTrue(s.complete)
    }

    @Test(expected = IOException::class)
    fun unknownDeckIsAnError() {
        CommanderSaltApi.parse("null")
    }

    @Test
    fun deckIdIsMd5OfArchidektApiUrl() {
        assertEquals("aee8080ad777055f553ef746883373fa", CommanderSaltApi.deckId(20263351))
    }

    @Test
    fun errorMessageUsesServerText() {
        assertEquals(
            "Deck URL invalid, or deck is not publicly visible",
            CommanderSaltApi.errorMessage("""{"message":"ERROR: Deck URL invalid, or deck is not publicly visible"}""", 404),
        )
        assertEquals("Commander Salt error 502", CommanderSaltApi.errorMessage("<html>bad gateway</html>", 502))
    }

    @Test
    fun recognisesPng() {
        assertTrue(CommanderSaltApi.isPng(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0)))
        assertFalse(CommanderSaltApi.isPng("{\"message\":\"x\"}".toByteArray()))
    }
}
