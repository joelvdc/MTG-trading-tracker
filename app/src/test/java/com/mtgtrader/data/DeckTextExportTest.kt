package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Copying a deck as text, against the cases shared with the website (`web/test/deckexport.test.ts`). */
class DeckTextExportTest {
    @Serializable
    private class ExportLine(
        val quantity: Int,
        val name: String,
        val setCode: String,
        val number: String,
        val foil: Boolean,
        val etched: Boolean,
        val categories: List<String>,
        val commander: Boolean,
        val maybeboard: Boolean,
    )

    @Serializable
    private class ExportCase(val format: String, val categories: Boolean, val maybeboard: Boolean, val text: String)

    @Serializable
    private class Cases(val lines: List<ExportLine>, val cases: List<ExportCase>)

    private val cases = Json { ignoreUnknownKeys = true }
        .decodeFromString<Cases>(File("../web/test/fixtures/deckexport-cases.json").readText())

    private fun line(l: ExportLine) = DeckText.ExportLine(
        l.quantity, l.name, l.setCode, l.number, l.foil, l.etched, l.categories, l.commander, l.maybeboard,
    )

    @Test
    fun writesEverySharedCaseExactly() {
        assertTrue(cases.cases.isNotEmpty())
        val lines = cases.lines.map(::line)
        for (c in cases.cases) {
            val format = DeckText.Format.valueOf(c.format.uppercase())
            assertEquals(c.text, DeckText.export(lines, format, c.categories, c.maybeboard))
        }
    }

    @Test
    fun readsEveryExportBack() {
        assertTrue(cases.cases.isNotEmpty())
        val lines = cases.lines.map(::line)
        for (c in cases.cases) {
            val format = DeckText.Format.valueOf(c.format.uppercase())
            val parsed = DeckText.parse(DeckText.export(lines, format, c.categories, c.maybeboard))
            assertEquals("${c.format} categories=${c.categories} maybeboard=${c.maybeboard}", emptyList<String>(), parsed.unreadable)
            val expected = cases.lines
                .filter { !it.maybeboard || c.maybeboard }
                .map { l ->
                    val board = if (l.maybeboard) "MAYBE" else if (l.commander) "COMMANDER" else "MAIN"
                    // Arena and plain lists hold no tags, even when the deck's categories were asked for.
                    val tags = if (c.categories && (c.format == "moxfield" || c.format == "archidekt")) l.categories else emptyList()
                    "${l.quantity}|${l.name}|$board|${tags.joinToString(",")}"
                }
                .sorted()
            val back = parsed.lines.map { "${it.quantity}|${it.name}|${it.board}|${it.tags.joinToString(",")}" }.sorted()
            assertEquals("${c.format} categories=${c.categories} maybeboard=${c.maybeboard}", expected, back)
        }
    }
}
