package com.mtgtrader

import com.mtgtrader.data.DeckNotes
import com.mtgtrader.data.DeckNotes.PrimerSegment
import com.mtgtrader.ui.primerFirstLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 1.99: card links in a deck's primer (`[[Card name]]`), tappable on the primer page. The shared cases in
 * `web/test/fixtures/primerlinks-cases.json` are the website's too, so both sides read a link the same way; the
 * hand-written cases test the same rules here while that file is being written.
 */
class Release199Test {
    private val fixtures = File("../web/test/fixtures")
    private val json = Json { ignoreUnknownKeys = true }

    // ---- Shared cases -------------------------------------------------------------------------------

    /** One segment as the shared file spells it: its kind, and its text or the card's name. */
    @Serializable
    private class SegmentCase(val kind: String, val text: String? = null, val name: String? = null)

    @Serializable
    private class SegmentsCase(val input: String, val output: List<SegmentCase> = emptyList())

    @Serializable
    private class MatchCase(val link: String, val card: String, val match: Boolean)

    @Serializable
    private class LinkCases(
        val segments: List<SegmentsCase> = emptyList(),
        val matches: List<MatchCase> = emptyList(),
    )

    /** [segments] in the shared file's shape, so a case compares as a whole. */
    private fun asWritten(segments: List<PrimerSegment>): List<SegmentCase> = segments.map {
        when (it) {
            is PrimerSegment.Text -> SegmentCase("text", text = it.text)
            is PrimerSegment.Card -> SegmentCase("card", name = it.name)
        }
    }

    @Test
    fun sharedLinkCases() {
        val file = File(fixtures, "primerlinks-cases.json")
        assumeTrue("web/test/fixtures/primerlinks-cases.json isn't in this copy yet", file.exists())
        val cases = json.decodeFromString<LinkCases>(file.readText())
        assertTrue(cases.segments.isNotEmpty() || cases.matches.isNotEmpty())
        for (c in cases.segments) assertEquals(c.input, c.output, asWritten(DeckNotes.linkSegments(c.input)))
        for (c in cases.matches) assertEquals("${c.link} in ${c.card}", c.match, DeckNotes.linkMatches(c.link, c.card))
    }

    // ---- The rules, by hand -------------------------------------------------------------------------

    @Test
    fun oneLinkSplitsIntoItsTextAndTheCard() {
        assertEquals(
            listOf(PrimerSegment.Text("Play "), PrimerSegment.Card("Sol Ring"), PrimerSegment.Text(" early")),
            DeckNotes.linkSegments("Play [[Sol Ring]] early"),
        )
        // The name is the inner text trimmed; nothing else of the brackets is kept.
        assertEquals(listOf(PrimerSegment.Card("Arcane Signet")), DeckNotes.linkSegments("[[  Arcane Signet  ]]"))
        // Two links in a row, with the text between them kept once.
        assertEquals(
            listOf(PrimerSegment.Card("A"), PrimerSegment.Text(" and "), PrimerSegment.Card("B")),
            DeckNotes.linkSegments("[[A]] and [[B]]"),
        )
    }

    @Test
    fun anUnclosedOrEmptyLinkStaysAsWritten() {
        assertEquals(listOf(PrimerSegment.Text("Play [[Sol Ring early")), DeckNotes.linkSegments("Play [[Sol Ring early"))
        assertEquals(listOf(PrimerSegment.Text("[[")), DeckNotes.linkSegments("[["))
        assertEquals(listOf(PrimerSegment.Text("a [[]] b")), DeckNotes.linkSegments("a [[]] b"))
        // A bracket inside the name, or a line break, isn't a link either.
        assertEquals(listOf(PrimerSegment.Text("[[a [b]]")), DeckNotes.linkSegments("[[a [b]]"))
        assertEquals(listOf(PrimerSegment.Text("[[a\nb]]")), DeckNotes.linkSegments("[[a\nb]]"))
        assertEquals(emptyList<PrimerSegment>(), DeckNotes.linkSegments(""))
    }

    @Test
    fun aBoldLinkIsReadOnceTheBoldMarksAreOff() {
        // The page takes the ** pairs off first, then reads each part for links, so this is a bold link.
        assertEquals(
            listOf(PrimerSegment.Text("**"), PrimerSegment.Card("Sol Ring"), PrimerSegment.Text("**")),
            DeckNotes.linkSegments("**[[Sol Ring]]**"),
        )
        // The deck page's one-line primer row shows the card's name, without its brackets.
        assertEquals("Sol Ring", primerFirstLine("**[[Sol Ring]]**"))
        assertEquals("Ramp into Sol Ring", primerFirstLine("# Ramp into [[Sol Ring]]"))
        assertEquals("Sol Ring", primerFirstLine("- [[Sol Ring]]"))
    }

    @Test
    fun aDoubleFacedCardMatchesByItsFrontName() {
        val card = "Delver of Secrets // Insectile Aberration"
        assertTrue(DeckNotes.linkMatches("Delver of Secrets", card))
        assertTrue(DeckNotes.linkMatches(" delver of secrets ", card))
        assertTrue(DeckNotes.linkMatches(card, card))
        // The back name alone doesn't: a link names the card, not its back.
        assertFalse(DeckNotes.linkMatches("Insectile Aberration", card))
        assertFalse(DeckNotes.linkMatches("Delver", card))
        // A card with one name needs it whole; spaces around it and case don't matter.
        assertTrue(DeckNotes.linkMatches(" sol ring ", "Sol Ring"))
        assertFalse(DeckNotes.linkMatches("Sol", "Sol Ring"))
    }
}
