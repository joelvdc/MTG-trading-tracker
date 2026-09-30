package com.mtgtrader

import com.mtgtrader.data.ArchidektApi
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.CommanderSaltApi
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckSort
import com.mtgtrader.data.DeckSorting
import com.mtgtrader.data.DeckToCollection
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

    @Test
    fun findsUsernameInProfileLinks() {
        assertEquals("joelvdc", DeckLinks.archidektUser("https://archidekt.com/u/joelvdc"))
        assertEquals("joelvdc", DeckLinks.archidektUser("archidekt.com/u/joelvdc?tab=decks"))
        assertEquals("joelvdc", DeckLinks.archidektUser(" joelvdc "))
        assertEquals("some_user.1", DeckLinks.archidektUser("@some_user.1"))
        // Numeric /user/ links carry an id, not a name.
        assertNull(DeckLinks.archidektUser("https://archidekt.com/user/229624"))
        assertNull(DeckLinks.archidektUser("https://archidekt.com/decks/20263351/auntie_plague"))
        assertNull(DeckLinks.archidektUser("two words"))
    }
}

class CardTargetTest {
    @Test
    fun encodesAndDecodesEveryTarget() {
        val targets = listOf(
            CardTarget.TradeSide(3, "GET"), CardTarget.Collection(), CardTarget.Collection(12), CardTarget.Scans,
            CardTarget.ReplaceTradeItem(4), CardTarget.ReplaceCollectionItem(5), CardTarget.ReplaceScan(6),
        )
        targets.forEach { assertEquals(it, CardTarget.decode(it.encode())) }
        // Links saved before binders existed.
        assertEquals(CardTarget.Collection(), CardTarget.decode("collection"))
    }
}

class DeckToCollectionTest {
    private fun card(name: String, qty: Int) = DeckCard(
        deckId = 1,
        card = CardRef("id-$name", name, "tst", "Test", "1", "rare", null, null, null, null, true, false),
        quantity = qty, category = "x", types = "", cmc = 0.0, commander = false, foil = false,
    )

    @Test
    fun onlyMissingSubtractsOwnedCopiesByName() {
        val deck = listOf(card("Sol Ring", 1), card("Swamp", 10), card("Forest", 8), card("Blight Mound", 1), card("Snow-Covered Swamp", 2))
        val owned = mapOf("sol ring" to 3, "swamp" to 4)
        fun plan(onlyMissing: Boolean, skipBasics: Boolean) =
            DeckToCollection.plan(deck, owned, onlyMissing, skipBasics).associate { it.first.card.name to it.second }
        assertEquals(mapOf("Swamp" to 6, "Forest" to 8, "Blight Mound" to 1, "Snow-Covered Swamp" to 2), plan(true, false))
        assertEquals(mapOf("Blight Mound" to 1), plan(true, true))
        assertEquals(mapOf("Sol Ring" to 1, "Blight Mound" to 1), plan(false, true))
        assertEquals(22, plan(false, false).values.sum())
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

class DeckSortingTest {
    private fun deck(name: String, power: Double?, realistic: Int?, baseline: Int?, updated: Long?) =
        Deck(archidektId = name.hashCode().toLong(), name = name, owner = "", commanders = "", commanderScryfallId = null,
            artUrl = null, colorIdentity = "", cardCount = 100, powerLevel = power, bracketRealistic = realistic,
            bracketBaseline = baseline, archidektUpdatedAt = updated)

    private val decks = listOf(
        deck("Baba", 6.4, 3, 3, 300),
        deck("Auntie Plague", 4.4, 2, 3, 200),
        deck("Taranika", 5.4, 3, 2, null),
        deck("Unscored", null, null, null, 100),
    )

    private fun names(sort: DeckSort, reverse: Boolean = false) = DeckSorting.sort(decks, sort, reverse).map { it.name }

    @Test
    fun sortsStrongestAndNewestFirstWithUnknownsLast() {
        assertEquals(listOf("Auntie Plague", "Baba", "Taranika", "Unscored"), names(DeckSort.NAME))
        assertEquals(listOf("Unscored", "Taranika", "Baba", "Auntie Plague"), names(DeckSort.NAME, reverse = true))
        assertEquals(listOf("Baba", "Taranika", "Auntie Plague", "Unscored"), names(DeckSort.POWER))
        assertEquals(listOf("Auntie Plague", "Taranika", "Baba", "Unscored"), names(DeckSort.POWER, reverse = true))
        // Same realistic bracket 3: the higher baseline bracket wins.
        assertEquals(listOf("Baba", "Taranika", "Auntie Plague", "Unscored"), names(DeckSort.BRACKET))
        assertEquals(listOf("Baba", "Auntie Plague", "Unscored", "Taranika"), names(DeckSort.MODIFIED))
        assertEquals(listOf("Unscored", "Auntie Plague", "Baba", "Taranika"), names(DeckSort.MODIFIED, reverse = true))
    }

    @Test
    fun parsesArchidektTimestamps() {
        assertEquals(1781527803042L, DeckSorting.parseTime("2026-06-15T12:50:03.042057Z"))
        assertNull(DeckSorting.parseTime("yesterday"))
        assertNull(DeckSorting.parseTime(null))
    }
}

class ArchidektUserDecksTest {
    @Test
    fun readsDeckListPage() {
        val body = """
            {"count": 3, "next": "http://archidekt.com/api/decks/v3/?ownerUsername=joelvdc&page=2",
             "results": [
               {"id": 20558888, "name": "Baba", "size": 100, "deckFormat": 3, "featured": "https://x/art.jpg", "customFeatured": "",
                "private": false, "colors": {"W": 0, "U": 0, "B": 23, "R": 0, "G": 36}, "parentFolderName": null},
               {"id": 1, "name": "Pauper list", "size": 60, "deckFormat": 1, "featured": "", "customFeatured": "https://x/custom.jpg",
                "private": false, "colors": {"W": 5}, "parentFolderName": "Old"},
               {"id": 2, "name": "Secret", "size": 100, "deckFormat": 3, "private": true}
             ]}
        """.trimIndent()
        val (decks, next) = ArchidektApi.parseDeckList(body)
        assertEquals(listOf(20558888L, 1L), decks.map { it.id })
        assertEquals("BG", decks[0].colors)
        assertEquals("https://x/art.jpg", decks[0].artUrl)
        assertTrue(decks[0].commanderFormat)
        assertFalse(decks[1].commanderFormat)
        assertEquals("https://x/custom.jpg", decks[1].artUrl)
        assertEquals("Old", decks[1].folder)
        assertEquals("http://archidekt.com/api/decks/v3/?ownerUsername=joelvdc&page=2", next)
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
