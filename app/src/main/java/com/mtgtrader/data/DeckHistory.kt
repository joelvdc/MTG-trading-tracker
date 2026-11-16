package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset

/**
 * A deck's changes (since 1.97), newest first: one entry per day, saying which cards went in or out (by name, with
 * the copies) and where the changes were made. Both the app and the website write it, so the two agree on it.
 *
 * Only the deck itself counts (not the maybeboard or the commanders' lines as such: a commander is a main-deck line
 * like any other). A change made the same day as the newest entry is added to it, so a card added and removed again
 * that day leaves nothing behind.
 */
object DeckHistory {
    /** A change made in the app (an edit in [DeckEditor]). */
    const val SOURCE_APP = "app"

    /** A change that came with the decklist from Archidekt (a reload). */
    const val SOURCE_ARCHIDEKT = "archidekt"

    /** A change made on the website. */
    const val SOURCE_WEBSITE = "website"

    /** Days kept; the oldest entries are dropped. */
    const val MAX_ENTRIES = 100

    /**
     * One day's changes: [day] is the UTC date ("2026-11-16"), [sources] where the changes were made and the cards
     * that came in ([added]) and went out ([removed]) with their copies.
     */
    @Serializable
    data class Entry(val day: String, val sources: List<String>, val added: Map<String, Int>, val removed: Map<String, Int>)

    /** The cards that went up ([added]) or down ([removed]) between two lists of lines, by name. */
    data class Change(val added: Map<String, Int>, val removed: Map<String, Int>) {
        val isEmpty get() = added.isEmpty() && removed.isEmpty()
    }

    private val entries = ListSerializer(Entry.serializer())

    /** The entries in [text] ([Deck.history]); empty when there are none or it's unreadable. */
    fun parse(text: String?): List<Entry> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { Json.decodeFromString(entries, text) }.getOrDefault(emptyList())
    }

    /**
     * What changed between [before] and [after]: the deck's main-deck lines compared by card name, the quantities of
     * lines with the same name added up. Both lists may hold only the lines that changed, which comes to the same.
     */
    fun diff(before: List<DeckCard>, after: List<DeckCard>): Change {
        fun totals(lines: List<DeckCard>): Map<String, Int> =
            lines.filterNot { it.maybeboard }.groupBy { it.card.name }.mapValues { (_, same) -> same.sumOf { it.quantity } }
        val was = totals(before)
        val now = totals(after)
        val added = LinkedHashMap<String, Int>()
        val removed = LinkedHashMap<String, Int>()
        for (name in (was.keys + now.keys).sorted()) {
            val delta = (now[name] ?: 0) - (was[name] ?: 0)
            if (delta > 0) added[name] = delta else if (delta < 0) removed[name] = -delta
        }
        return Change(added, removed)
    }

    /**
     * [history] with [change] (made at [now], by [source]) in it: added to the newest entry when it's the same day,
     * else a new entry in front. Unchanged when nothing changed; null when no changes are left at all.
     */
    fun record(history: String?, now: Long, source: String, change: Change): String? {
        if (change.isEmpty) return history
        val day = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toString()
        val list = parse(history).toMutableList()
        val first = list.firstOrNull()
        if (first != null && first.day == day) {
            val added = LinkedHashMap<String, Int>()
            val removed = LinkedHashMap<String, Int>()
            for (name in (first.added.keys + first.removed.keys + change.added.keys + change.removed.keys).sorted()) {
                val net = (first.added[name] ?: 0) - (first.removed[name] ?: 0) + (change.added[name] ?: 0) - (change.removed[name] ?: 0)
                if (net > 0) added[name] = net else if (net < 0) removed[name] = -net
            }
            if (added.isEmpty() && removed.isEmpty()) {
                // Back where the day started: the day's entry has nothing to say any more.
                list.removeAt(0)
            } else {
                list[0] = Entry(day, first.sources + listOfNotNull(source.takeIf { it !in first.sources }), added, removed)
            }
        } else {
            list.add(0, Entry(day, listOf(source), sorted(change.added), sorted(change.removed)))
        }
        val kept = list.take(MAX_ENTRIES)
        if (kept.isEmpty()) return null
        return Json.encodeToString(entries, kept)
    }

    /** The same map with its keys sorted, so the JSON text is the same on both sides. */
    private fun sorted(cards: Map<String, Int>): Map<String, Int> =
        cards.entries.sortedBy { it.key }.associateTo(LinkedHashMap<String, Int>()) { it.key to it.value }
}
