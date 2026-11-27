package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset

/**
 * A deck's snapshots (since 1.124): the whole decklist saved under a name, with the bracket, the win turn and the price
 * it had at that moment, so it can be compared later with another snapshot or with the deck as it is now. Both the app
 * and the website write it, so the two agree on it.
 *
 * Only the deck itself counts (not the maybeboard). A deck keeps its newest [MAX] snapshots; taking one more drops the
 * oldest. Comparing two lists adds up the copies per card name, the same rule as the change history ([DeckHistory.diff]).
 */
object DeckSnapshots {
    /** Snapshots kept; taking one more drops the oldest. */
    const val MAX = 25

    /** Longest snapshot name. */
    const val MAX_NAME = 60

    /** One line of a snapshot's list: the card's name, its copies and printing, and whether it was the commander. */
    @Serializable
    data class SnapCard(
        val name: String,
        val quantity: Int,
        val set: String,
        val number: String,
        val commander: Boolean,
    )

    /**
     * A snapshot: [name] ("Before upgrade"), [at] when it was taken (ms), its [cards] (the main deck, sorted by name,
     * then set, then number), and the deck's [bracket], [winTurn] label and [eur] price then, when they were known.
     */
    @Serializable
    data class Snapshot(
        val id: String,
        val name: String,
        val at: Long,
        val cards: List<SnapCard>,
        val bracket: Int? = null,
        val winTurn: String? = null,
        val eur: Double? = null,
    )

    private val snapshots = ListSerializer(Snapshot.serializer())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val HEX = "0123456789abcdef"
    private val random = java.util.Random()

    /** A new snapshot's id: 12 random lowercase hex characters, as the website makes them too. */
    fun newId(): String = buildString(12) { repeat(12) { append(HEX[random.nextInt(HEX.length)]) } }

    /** The snapshots in [text] ([Deck.snapshots]), newest first; empty when there are none or it's unreadable. */
    fun parse(text: String?): List<Snapshot> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(snapshots, text) }.getOrDefault(emptyList())
    }

    /** A deck's [lines] the way a snapshot keeps them: the main deck (no maybeboard), by name, then set, then number. */
    fun cardsOf(lines: List<DeckCard>): List<SnapCard> =
        lines.filterNot { it.maybeboard }
            .map {
                SnapCard(
                    name = it.card.name,
                    quantity = it.quantity,
                    set = it.card.setCode.lowercase(),
                    number = it.card.collectorNumber,
                    commander = it.commander,
                )
            }
            .sortedWith(compareBy<SnapCard, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.set }.thenBy { it.number })

    /**
     * [text] with a snapshot of [cards] in front, taken at [now] under [name] (trimmed, cut to [MAX_NAME]; blank is
     * [now]'s UTC date), with the deck's [bracket], [winTurn] label and [eur] price then. The oldest goes at [MAX].
     */
    fun take(
        text: String?,
        now: Long,
        name: String,
        cards: List<SnapCard>,
        bracket: Int?,
        winTurn: String?,
        eur: Double?,
        id: String = newId(),
    ): String = encode((listOf(Snapshot(id, nameOf(name, now), now, cards, bracket, winTurn, eur)) + parse(text)).take(MAX))!!

    /**
     * [text] with the snapshot [id] called [name] (trimmed, cut to [MAX_NAME]; blank keeps the name it has); null when
     * no snapshots are left.
     */
    fun rename(text: String?, id: String, name: String): String? {
        val renamed = name.trim().take(MAX_NAME)
        return parse(text).map { if (it.id == id && renamed.isNotEmpty()) it.copy(name = renamed) else it }.let(::encode)
    }

    /** [text] without the snapshot [id]; null when no snapshots are left. */
    fun delete(text: String?, id: String): String? = parse(text).filterNot { it.id == id }.let(::encode)

    /**
     * What changed from [old] to [new]: the copies of each card name added up on both sides, [DeckHistory.Change.added]
     * the names that went up (with the difference) and [DeckHistory.Change.removed] those that went down.
     */
    fun compare(old: List<SnapCard>, new: List<SnapCard>): DeckHistory.Change {
        fun totals(cards: List<SnapCard>): Map<String, Int> =
            cards.groupBy { it.name }.mapValues { (_, same) -> same.sumOf { it.quantity } }
        val was = totals(old)
        val now = totals(new)
        val added = LinkedHashMap<String, Int>()
        val removed = LinkedHashMap<String, Int>()
        for (name in (was.keys + now.keys).sorted()) {
            val delta = (now[name] ?: 0) - (was[name] ?: 0)
            if (delta > 0) added[name] = delta else if (delta < 0) removed[name] = -delta
        }
        return DeckHistory.Change(added, removed)
    }

    /**
     * A snapshot's list as text ("1 Sol Ring (CMM) 400"), the commanders first, for a new deck made from it (see
     * [DeckEditor.fromText]).
     */
    fun decklistText(s: Snapshot): String =
        (s.cards.filter { it.commander } + s.cards.filterNot { it.commander }).joinToString("\n") { c ->
            "${c.quantity} ${c.name} (${c.set.uppercase()})" + if (c.number.isEmpty()) "" else " ${c.number}"
        }

    /** The list as JSON, or null when it's empty (a deck without snapshots keeps null). */
    private fun encode(list: List<Snapshot>): String? =
        if (list.isEmpty()) null else json.encodeToString(snapshots, list)

    /** [name] trimmed and cut to [MAX_NAME]; blank is [now]'s UTC date ("2026-11-27"). */
    private fun nameOf(name: String, now: Long): String = name.trim().take(MAX_NAME).ifBlank { utcDate(now) }

    /** The UTC date of [ms], as a snapshot's name and the change history write it. */
    private fun utcDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
}
