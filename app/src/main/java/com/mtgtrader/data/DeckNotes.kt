package com.mtgtrader.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * A deck's notes (since 1.97): its primer, plain text with light markdown, and a note per card. The website keeps
 * them the same way, so both sides read and write one sync file.
 *
 * Archidekt's deck description is a Quill delta: JSON text holding text and image pieces with formatting such as
 * headings and bold. [primerFromArchidekt] turns one into a primer, so a description written on Archidekt shows up
 * here as text (and the other way round for a deck's first import).
 */
object DeckNotes {
    /** The longest primer kept, in characters (the website's limit too). */
    const val MAX_PRIMER = 20_000

    /** The longest note on one card, in characters. */
    const val MAX_NOTE = 500

    /** A card's name as notes and tags know it: the whole name, trimmed and lower case ("A // B" included). */
    fun nameKey(name: String): String = name.trim().lowercase()

    /**
     * Archidekt's [description] as a primer: headings become "# " lines, bullet and ordered list items "- " and
     * "1. ", bold pieces "**…**", and images are left out. A description that isn't a Quill delta is taken as text.
     * Null when there's nothing to show.
     */
    fun primerFromArchidekt(description: String?): String? {
        if (description.isNullOrBlank()) return null
        val ops = runCatching { Json.parseToJsonElement(description).jsonObject["ops"] as? JsonArray }.getOrNull()
        return tidy(if (ops == null) description else textOf(ops))
    }

    /** A Quill delta's ops as plain text, one line per Quill line. */
    private fun textOf(ops: JsonArray): String {
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        for (element in ops) {
            val op = element as? JsonObject ?: continue
            val insert = op["insert"] as? JsonPrimitive ?: continue
            // An image, a video or anything else that isn't text has no place in a primer.
            if (!insert.isString) continue
            val attributes = op["attributes"] as? JsonObject
            val bold = (attributes?.get("bold") as? JsonPrimitive)?.booleanOrNull == true
            val prefix = prefixOf(attributes)
            val parts = insert.content.split("\n")
            for ((i, part) in parts.withIndex()) {
                if (part.isNotEmpty()) line.append(if (bold) "**$part**" else part)
                // Every newline ends the line it's in, in that piece's format.
                if (i < parts.size - 1) {
                    lines += if (line.isEmpty()) "" else prefix + line.toString()
                    line.clear()
                }
            }
        }
        // Text after the last newline is the last line.
        if (line.isNotEmpty()) lines += line.toString()
        return lines.joinToString("\n")
    }

    /** What a line's own attributes put in front of it: a heading's "# ", a bullet or a number. */
    private fun prefixOf(attributes: JsonObject?): String {
        if (attributes == null) return ""
        val header = (attributes["header"] as? JsonPrimitive)?.content?.toIntOrNull()
        if (header != null && header in 1..6) return "#".repeat(header) + " "
        return when ((attributes["list"] as? JsonPrimitive)?.content) {
            "bullet" -> "- "
            "ordered" -> "1. "
            else -> ""
        }
    }

    /** The last tidy-up: no trailing spaces, at most two newlines in a row, trimmed, cut to [MAX_PRIMER]. */
    private fun tidy(text: String): String? {
        val lines = text.split("\n").map { it.trimEnd() }
        val joined = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        return joined.take(MAX_PRIMER).ifEmpty { null }
    }

    /** A deck's card notes ([Deck.cardNotes]) as a map of [nameKey] to note; empty when there are none or it's unreadable. */
    fun parseCardNotes(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for ((key, value) in obj) {
            val note = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.take(MAX_NOTE) ?: continue
            if (note.isEmpty()) continue
            out[nameKey(key)] = note
        }
        return out.toSortedMap()
    }

    /**
     * [notes] as [Deck.cardNotes]: keys sorted, notes trimmed and cut to [MAX_NOTE], a card without a note left out.
     * Null when there are no notes at all.
     */
    fun encodeCardNotes(notes: Map<String, String>): String? {
        val clean = notes.entries.mapNotNull { (name, note) ->
            val key = nameKey(name)
            val text = note.trim().take(MAX_NOTE)
            if (key.isEmpty() || text.isEmpty()) null else key to text
        }.distinctBy { it.first }.sortedBy { it.first }
        if (clean.isEmpty()) return null
        return buildJsonObject { clean.forEach { (key, note) -> put(key, note) } }.toString()
    }
}
