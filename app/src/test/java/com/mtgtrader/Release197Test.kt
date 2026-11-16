package com.mtgtrader

import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckHistory
import com.mtgtrader.data.DeckMerge
import com.mtgtrader.data.DeckNotes
import com.mtgtrader.data.DeckPackage
import com.mtgtrader.data.DeckPackages
import com.mtgtrader.data.DeckRoles
import com.mtgtrader.data.PackageCard
import com.mtgtrader.data.SyncDeck
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * 1.97: a deck's primer, its cards' notes and its change history, and cards' own tags and roles. The shared cases in
 * `web/test/fixtures/` are the website's too, so both sides behave the same; the hand-written ones test the same
 * rules here while those files are being written.
 */
class Release197Test {
    private val fixtures = File("../web/test/fixtures")
    private val json = Json { ignoreUnknownKeys = true }

    /** 2026-11-16 and 2026-11-17 in UTC, at 10:00. */
    private val day1 = Instant.parse("2026-11-16T10:00:00Z").toEpochMilli()
    private val day2 = Instant.parse("2026-11-17T10:00:00Z").toEpochMilli()

    private fun line(name: String, quantity: Int = 1, maybeboard: Boolean = false) = DeckCard(
        deckId = 1,
        card = CardRef(
            scryfallId = "id-${name.lowercase().replace(' ', '-')}", name = name, setCode = "tst", setName = "Test",
            collectorNumber = "1", rarity = "common", imageUrl = null, cardmarketId = null,
            fallbackEur = null, fallbackEurFoil = null, hasNonFoil = true, hasFoil = false,
        ),
        quantity = quantity,
        category = "Deck",
        types = "Artifact",
        cmc = 1.0,
        commander = false,
        foil = false,
        maybeboard = maybeboard,
    )

    private fun deck(
        primer: String? = null,
        cardNotes: String? = null,
        notesAt: Long? = null,
        history: String? = null,
        importedAt: Long = 0,
    ) = Deck(
        7, "Deck", "me", "Commander", null, null, "", 0,
        importedAt = importedAt, primer = primer, cardNotes = cardNotes, notesAt = notesAt, history = history,
    )

    // ---- Shared cases -------------------------------------------------------------------------------

    @Serializable
    private class NotesCase(val name: String, val description: String? = null, val primer: String? = null)

    @Test
    fun sharedPrimerCases() {
        val file = File(fixtures, "decknotes-cases.json")
        assumeTrue("web/test/fixtures/decknotes-cases.json isn't in this copy yet", file.exists())
        val cases = json.decodeFromString<List<NotesCase>>(file.readText())
        assertTrue(cases.isNotEmpty())
        for (c in cases) assertEquals(c.name, c.primer, DeckNotes.primerFromArchidekt(c.description))
    }

    @Serializable
    private class HistoryLine(val name: String, val quantity: Int = 1, val maybeboard: Boolean = false) {
        fun toCard() = DeckCard(
            deckId = 1,
            card = CardRef(
                scryfallId = "id-${name.lowercase().replace(' ', '-')}", name = name, setCode = "tst", setName = "Test",
                collectorNumber = "1", rarity = "common", imageUrl = null, cardmarketId = null,
                fallbackEur = null, fallbackEurFoil = null, hasNonFoil = true, hasFoil = false,
            ),
            quantity = quantity, category = "Deck", types = "Artifact", cmc = 1.0, commander = false, foil = false,
            maybeboard = maybeboard,
        )
    }

    @Serializable
    private class HistoryStep(
        val now: Long,
        val source: String,
        val before: List<HistoryLine> = emptyList(),
        val after: List<HistoryLine> = emptyList(),
    )

    @Serializable
    private class HistoryCase(
        val name: String,
        val start: String? = null,
        val steps: List<HistoryStep> = emptyList(),
        val result: String? = null,
    )

    @Test
    fun sharedHistoryCases() {
        val file = File(fixtures, "deckhistory-cases.json")
        assumeTrue("web/test/fixtures/deckhistory-cases.json isn't in this copy yet", file.exists())
        val cases = json.decodeFromString<List<HistoryCase>>(file.readText())
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            var history = c.start
            for (step in c.steps) {
                val change = DeckHistory.diff(step.before.map { it.toCard() }, step.after.map { it.toCard() })
                history = DeckHistory.record(history, step.now, step.source, change)
            }
            assertEquals(c.name, c.result, history)
        }
    }

    @Serializable
    private class RoleCaseCard(
        val name: String,
        val types: String = "",
        val quantity: Int = 1,
        val commander: Boolean = false,
        val maybeboard: Boolean = false,
        val categories: List<String> = emptyList(),
        val ownTags: List<String>? = null,
        val auto: List<String> = emptyList(),
    )

    @Serializable
    private class RoleCaseCount(val role: String, val count: Int, val status: String)

    @Serializable
    private class RolesCase(
        val name: String,
        val cards: List<RoleCaseCard> = emptyList(),
        val tags: Map<String, List<String>> = emptyMap(),
        val counts: List<RoleCaseCount> = emptyList(),
    )

    @Test
    fun sharedRoleCases() {
        val file = File(fixtures, "deckroles-cases.json")
        assumeTrue("web/test/fixtures/deckroles-cases.json isn't in this copy yet", file.exists())
        val cases = json.decodeFromString<List<RolesCase>>(file.readText())
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            for ((name, expected) in c.tags) {
                val card = c.cards.firstOrNull { it.name == name } ?: continue
                assertEquals("${c.name}: tags of $name", expected, DeckRoles.tagsOf(card.name, card.categories, card.ownTags, card.auto))
            }
            val cards = c.cards.map { DeckRoles.RoleCard(it.name, it.types, it.quantity, it.commander, it.maybeboard, it.categories, it.ownTags, it.auto) }
            assertEquals(
                c.name,
                c.counts.map { Triple(it.role, it.count, it.status) },
                DeckRoles.counts(cards).map { Triple(it.role, it.count, it.status) },
            )
        }
    }

    // ---- The primer from Archidekt ----------------------------------------------------------------

    @Test
    fun primerFromArchidekt() {
        assertNull(DeckNotes.primerFromArchidekt(null))
        assertNull(DeckNotes.primerFromArchidekt(""))
        assertNull(DeckNotes.primerFromArchidekt("   "))
        assertEquals("Just some text.", DeckNotes.primerFromArchidekt("Just some text."))
        // Blank lines in a row are cut to one gap.
        assertEquals("One\n\nTwo", DeckNotes.primerFromArchidekt("One\n\n\n\n\nTwo"))
        // Trailing spaces go.
        assertEquals("A line\nanother", DeckNotes.primerFromArchidekt("A line   \nanother  "))

        // A header line keeps its heading, bold pieces their stars, and an image is left out.
        val delta = """{"ops":[{"insert":"Game plan"},{"attributes":{"header":1},"insert":"\n"},""" +
            """{"attributes":{"bold":true},"insert":"Ramp"},{"insert":" early.\n"},""" +
            """{"attributes":{"list":"bullet"},"insert":"Sol Ring\n"},""" +
            """{"attributes":{"list":"ordered"},"insert":"Mana Crypt\n"},""" +
            """{"insert":{"image":"//:0"}}]}"""
        assertEquals("# Game plan\n**Ramp** early.\n- Sol Ring\n1. Mana Crypt", DeckNotes.primerFromArchidekt(delta))

        // A second header level.
        assertEquals("## Sideboards", DeckNotes.primerFromArchidekt("""{"ops":[{"insert":"Sideboards"},{"attributes":{"header":2},"insert":"\n"}]}"""))

        // Text after the last newline is a line too.
        assertEquals("A\nB", DeckNotes.primerFromArchidekt("""{"ops":[{"insert":"A\nB"}]}"""))

        // Text that isn't a delta is taken as it is.
        assertEquals("{not json and still text", DeckNotes.primerFromArchidekt("{not json and still text"))
        assertEquals("{\"a\":1}", DeckNotes.primerFromArchidekt("{\"a\":1}"))
    }

    // ---- Change history ---------------------------------------------------------------------------

    @Test
    fun historyOfOneDay() {
        val added = DeckHistory.record(null, day1, DeckHistory.SOURCE_APP, DeckHistory.diff(emptyList(), listOf(line("Sol Ring"))))
        assertEquals("""[{"day":"2026-11-16","sources":["app"],"added":{"Sol Ring":1},"removed":{}}]""", added)

        // The same day from the other side joins the entry: sources in the order they came, names sorted.
        val both = DeckHistory.record(added, day1 + 60_000, DeckHistory.SOURCE_WEBSITE, DeckHistory.diff(emptyList(), listOf(line("Arcane Signet"))))
        assertEquals(
            """[{"day":"2026-11-16","sources":["app","website"],"added":{"Arcane Signet":1,"Sol Ring":1},"removed":{}}]""",
            both,
        )

        // A card added and taken out again the same day leaves nothing.
        assertNull(DeckHistory.record(added, day1 + 3_600_000, DeckHistory.SOURCE_ARCHIDEKT, DeckHistory.diff(listOf(line("Sol Ring")), emptyList())))

        // Three copies in, one out again the same day: two the better.
        val net = DeckHistory.record(
            DeckHistory.record(null, day1, "app", DeckHistory.diff(listOf(line("Sol Ring")), listOf(line("Sol Ring", 3)))),
            day1 + 60_000,
            "app",
            DeckHistory.diff(listOf(line("Sol Ring", 3)), listOf(line("Sol Ring", 2))),
        )
        assertEquals("""[{"day":"2026-11-16","sources":["app"],"added":{"Sol Ring":2},"removed":{}}]""", net)
    }

    @Test
    fun historyAcrossDays() {
        val first = DeckHistory.record(null, day1, "app", DeckHistory.diff(emptyList(), listOf(line("Sol Ring"))))
        val next = DeckHistory.record(first, day2, "archidekt", DeckHistory.diff(listOf(line("Sol Ring")), listOf(line("Arcane Signet"))))
        assertEquals(
            """[{"day":"2026-11-17","sources":["archidekt"],"added":{"Arcane Signet":1},"removed":{"Sol Ring":1}},""" +
                """{"day":"2026-11-16","sources":["app"],"added":{"Sol Ring":1},"removed":{}}]""",
            next,
        )
        assertEquals(listOf("2026-11-17", "2026-11-16"), DeckHistory.parse(next).map { it.day })
    }

    @Test
    fun historyKeepsTheLastHundredDays() {
        var history: String? = null
        for (i in 0 until 101) {
            history = DeckHistory.record(history, day1 + i * 86_400_000L, "app", DeckHistory.diff(emptyList(), listOf(line("Card $i"))))
        }
        val entries = DeckHistory.parse(history)
        assertEquals(100, entries.size)
        assertEquals("2027-02-24", entries.first().day)
        assertEquals("2026-11-17", entries.last().day)
    }

    @Test
    fun historyIgnoresTheMaybeboardAndUnreadableText() {
        // A maybeboard card isn't part of the deck.
        assertNull(DeckHistory.record(null, day1, "app", DeckHistory.diff(listOf(line("Sol Ring", maybeboard = true)), emptyList())))
        // Nothing changed at all.
        assertNull(DeckHistory.record(null, day1, "app", DeckHistory.diff(listOf(line("Sol Ring")), listOf(line("Sol Ring")))))
        // Unreadable history starts again.
        val h = DeckHistory.record("{\"not\":\"a list\"}", day1, "app", DeckHistory.diff(emptyList(), listOf(line("Sol Ring"))))
        assertEquals(1, DeckHistory.parse(h).size)
    }

    @Test
    fun diffAddsUpLinesWithTheSameName() {
        assertEquals(DeckHistory.Change(mapOf("Sol Ring" to 2), emptyMap()), DeckHistory.diff(listOf(line("Sol Ring")), listOf(line("Sol Ring", 3))))
        assertEquals(DeckHistory.Change(emptyMap(), mapOf("Sol Ring" to 2)), DeckHistory.diff(listOf(line("Sol Ring", 3)), listOf(line("Sol Ring"))))
        // One normal line and one foil line of the same card: three copies either way.
        assertTrue(DeckHistory.diff(listOf(line("Sol Ring"), line("Sol Ring", 2)), listOf(line("Sol Ring", 3))).isEmpty)
    }

    // ---- Merging two phones' decks ------------------------------------------------------------------

    @Test
    fun deckMergeKeepsTheNewerNotes() {
        val a = SyncDeck(deck(primer = "A", cardNotes = "{\"a\":\"x\"}", notesAt = 10, history = "ha"))
        val b = SyncDeck(deck(primer = "B", cardNotes = "{\"b\":\"y\"}", notesAt = 20, history = "hb"))
        val fromB = DeckMerge.merge(a, b).deck
        assertEquals("B", fromB.primer)
        assertEquals("{\"b\":\"y\"}", fromB.cardNotes)
        assertEquals(20L, fromB.notesAt)
        assertEquals("hb", fromB.history)
        // Whichever copy is the newer list, the newer notes win; the history follows the list.
        val newerList = SyncDeck(deck(primer = "A", notesAt = 10, history = "ha", importedAt = 1_000))
        val olderList = SyncDeck(deck(primer = "B", notesAt = 20, history = "hb", importedAt = 500))
        val merged = DeckMerge.merge(newerList, olderList).deck
        assertEquals("B", merged.primer)
        assertEquals(20L, merged.notesAt)
        assertEquals("ha", merged.history)
    }

    @Test
    fun deckMergeTakesTheListCopyOnATie() {
        val a = SyncDeck(deck(primer = "A", history = "ha"))
        val b = SyncDeck(deck(primer = "B", history = "hb"))
        assertEquals("B", DeckMerge.merge(a, b).deck.primer)
        assertEquals("hb", DeckMerge.merge(a, b).deck.history)
        assertEquals("A", DeckMerge.merge(b, a).deck.primer)
        assertEquals("ha", DeckMerge.merge(b, a).deck.history)
    }

    // ---- Packages ----------------------------------------------------------------------------------

    @Test
    fun packageUseFindsWholeAndPartlyDecks() {
        val pkg = DeckPackage(
            "p1", "Mana rocks",
            listOf(PackageCard("Sol Ring", "id-1", 1), PackageCard("Arcane Signet", "id-2", 1), PackageCard("Mana Vault", "id-3", 1)),
        )
        val decks = sortedMapOf(
            1L to listOf(line("Sol Ring"), line("Arcane Signet"), line("Mana Vault")),
            2L to listOf(line("Sol Ring")),
            3L to listOf(line("Sol Ring", maybeboard = true), line("Mana Vault")),
            4L to listOf(line("Llanowar Elves")),
        )
        val use = DeckPackages.use(pkg, decks)
        assertEquals(listOf(1L), use.full)
        assertEquals(listOf(2L, 3L), use.partly)
        // A package with no cards is in no deck.
        assertEquals(DeckPackages.Use(emptyList(), emptyList()), DeckPackages.use(DeckPackage("p2", "Empty", emptyList()), decks))
    }

    @Test
    fun packagesSurviveEncoding() {
        val pkg = DeckPackage("p1", "  Mana rocks  ", listOf(PackageCard("Sol Ring", "id-1", 1)))
        val text = DeckPackages.encode(listOf(pkg))
        assertEquals("""[{"id":"p1","name":"Mana rocks","cards":[{"name":"Sol Ring","scryfallId":"id-1","quantity":1}]}]""", text)
        assertEquals(text, DeckPackages.encode(DeckPackages.parse(text)))
        assertEquals(emptyList<DeckPackage>(), DeckPackages.parse("not json"))
    }

    // ---- Card notes and tags -----------------------------------------------------------------------

    @Test
    fun cardNotesAreSortedTrimmedAndCut() {
        assertNull(DeckNotes.encodeCardNotes(emptyMap()))
        val text = DeckNotes.encodeCardNotes(mapOf("  Sol Ring " to "  ramp  ", "Arcane Signet" to "  ", "Rhystic Study" to "x".repeat(600)))
        assertEquals("""{"rhystic study":"${"x".repeat(500)}","sol ring":"ramp"}""", text)
        val notes = DeckNotes.parseCardNotes(text)
        assertEquals(listOf("rhystic study", "sol ring"), notes.keys.toList())
        assertEquals("ramp", notes.getValue("sol ring"))
        assertEquals(emptyMap<String, String>(), DeckNotes.parseCardNotes("[]"))
        assertEquals("A // B", DeckNotes.nameKey("A // B"))
        assertEquals("a // b", DeckNotes.nameKey(" A // B  "))
    }

    @Test
    fun tagsAreNormalisedAndSorted() {
        assertEquals(listOf("Ramp", "Combo piece"), DeckRoles.normaliseTags(listOf(" Ramp ", "ramp", "Combo piece", "  ")))
        assertEquals((1..10).map { "Tag $it" }, DeckRoles.normaliseTags((1..12).map { "Tag $it" }))
        assertEquals(listOf("x".repeat(DeckRoles.MAX_TAG)), DeckRoles.normaliseTags(listOf("x".repeat(40))))

        val text = DeckRoles.encodeCardTags(mapOf("Sol Ring" to listOf(" Ramp ", "ramp", "Combo piece"), "Arcane Signet" to emptyList()))
        assertEquals("""{"sol ring":["Ramp","Combo piece"]}""", text)
        assertEquals(mapOf("sol ring" to listOf("Ramp", "Combo piece")), DeckRoles.parseCardTags(text))
        assertEquals("{}", DeckRoles.encodeCardTags(emptyMap()))
        assertEquals(emptyMap<String, List<String>>(), DeckRoles.parseCardTags("{}"))
        assertEquals(emptyMap<String, List<String>>(), DeckRoles.parseCardTags("not json"))
    }

    @Test
    fun ownTagsWinOverCategories() {
        // The card's own tags are the answer, as spelled, even when the categories name other roles.
        assertEquals(listOf("My tag"), DeckRoles.tagsOf("Sol Ring", listOf("Ramp"), listOf("My tag"), listOf("Ramp")))
        // Without own tags the categories and auto categories name the roles, in role order.
        assertEquals(
            listOf("Ramp", "Removal", "Wipes"),
            DeckRoles.tagsOf("Sol Ring", listOf("Board wipe", "mana rock", "Spot Removal"), null, emptyList()),
        )
        assertEquals(listOf("Draw", "Wipes"), DeckRoles.tagsOf("Wrath", emptyList(), null, listOf("card advantage", "Wraths")))
        assertEquals(emptyList<String>(), DeckRoles.tagsOf("Sol Ring", listOf("Artifact"), null, emptyList()))
    }

    @Test
    fun countsFollowTheGuidelines() {
        fun card(name: String, quantity: Int = 1, types: String = "Artifact", categories: List<String> = emptyList(), own: List<String>? = null, auto: List<String> = emptyList(), commander: Boolean = false, maybeboard: Boolean = false) =
            DeckRoles.RoleCard(name, types, quantity, commander, maybeboard, categories, own, auto)

        val cards = listOf(
            card("Island", 36, types = "Basic Land"),
            card("Sol Ring", categories = listOf("Ramp")),
            card("Arcane Signet", 9, auto = listOf("Ramp")),
            card("Command Tower", 2, types = "Land"),
            card("Rhystic Study", 8, categories = listOf("Card draw")),
            card("Cyclonic Rift", categories = listOf("Board wipe")),
            card("Wrath of God", categories = listOf("Wraths")),
            card("Demonic Tutor", categories = listOf("Tutor")),
            card("My Commander", commander = true, categories = listOf("Ramp")),
            card("Maybeboard Rock", maybeboard = true, categories = listOf("Ramp")),
            card("Pet Card", 2, own = listOf("Pet cards"), types = "Creature"),
            card("Other Pet", 2, own = listOf("pet cards")),
        )
        val counts = DeckRoles.counts(cards).map { Triple(it.role, it.count, it.status) }
        assertEquals(
            listOf(
                Triple("Lands", 38, "ok"),
                Triple("Ramp", 10, "ok"),
                Triple("Draw", 8, "low"),
                Triple("Removal", 0, "low"),
                Triple("Wipes", 2, "ok"),
                Triple("Tutor", 1, "none"),
                // The same tag in another spelling is one tag, with the spelling met first.
                Triple("Pet cards", 4, "none"),
            ),
            counts,
        )
        // Over the range.
        assertEquals("high", DeckRoles.counts(listOf(card("Forest", 40, types = "Land"))).first().status)
    }
}
