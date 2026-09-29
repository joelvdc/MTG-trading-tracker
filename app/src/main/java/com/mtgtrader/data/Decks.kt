package com.mtgtrader.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A Commander deck imported from Archidekt, with the scores Commander Salt gave it. Since version 1.6. */
@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey val archidektId: Long,
    val name: String,
    val owner: String,
    /** "Auntie Ool, Cursewretch", or both names joined with " & " for partners. */
    val commanders: String,
    /** Scryfall id of the (first) commander, for its card image. */
    val commanderScryfallId: String?,
    /** The deck's featured art on Archidekt. */
    val artUrl: String?,
    /** Colour identity in WUBRG order, e.g. "BRG"; empty for colourless. */
    val colorIdentity: String,
    val cardCount: Int,
    val importedAt: Long = System.currentTimeMillis(),
    /** Commander Salt's id for the deck, once it has been scored there. */
    val saltId: String? = null,
    val powerLevel: Double? = null,
    /** Where the deck actually plays (Commander Salt's own reading). */
    val bracketRealistic: Int? = null,
    /** What the deck qualifies for by the letter of WotC's bracket rules. */
    val bracketBaseline: Int? = null,
    val saltPercent: Double? = null,
    val archetype: String? = null,
    val scoredAt: Long? = null,
    /** Why the last scoring attempt failed; null after a successful one. */
    val scoreError: String? = null,
) {
    val archidektUrl get() = "https://archidekt.com/decks/$archidektId"
    val saltUrl get() = saltId?.let { "https://commandersalt.com/details/deck/$it" }
    val scored get() = powerLevel != null || bracketRealistic != null
}

/** One line of a deck's list (a card can have several, e.g. one normal and one foil copy). */
@Entity(
    tableName = "deck_cards",
    foreignKeys = [ForeignKey(entity = Deck::class, parentColumns = ["archidektId"], childColumns = ["deckId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("deckId")],
)
data class DeckCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    @Embedded val card: CardRef,
    val quantity: Int,
    /** The card's (first) Archidekt category, e.g. "Ramp". */
    val category: String,
    /** Card types, e.g. "Artifact Creature". */
    val types: String,
    val cmc: Double,
    val commander: Boolean,
    val foil: Boolean,
    @ColumnInfo(defaultValue = "0") val etched: Boolean = false,
    val gameChanger: Boolean = false,
) {
    val finish get() = Finish.of(foil, etched)
    val primaryType get() = DeckGrouping.primaryType(types)
}

/** Deck card joined with today's price guide entry. */
data class DeckCardRow(
    @Embedded val item: DeckCard,
    @Embedded(prefix = "pr_") val price: PriceEntity?,
) {
    fun unitPrice(type: PriceType): Double? =
        price?.toSet(item.foil)?.best(type) ?: item.card.fallback(item.foil)

    val trend: PriceTrend? get() = price?.toSet(item.foil)?.trendChange
}

/** The two rule-zero cards Commander Salt draws for a deck. */
enum class RuleZeroCard(val label: String, val endpoint: String) {
    BRACKET("Bracket", "generatebracket"),
    POWER("Power level", "generateadvanced"),
}

object Brackets {
    /** WotC's names for the five Commander brackets. */
    fun name(bracket: Int?): String? = when (bracket) {
        1 -> "Exhibition"
        2 -> "Core"
        3 -> "Upgraded"
        4 -> "Optimized"
        5 -> "cEDH"
        else -> null
    }
}

object DeckLinks {
    private val deckUrl = Regex("""archidekt\.com/(?:api/)?decks/(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * The Archidekt deck id in a link such as "https://archidekt.com/decks/20263351/auntie_plague",
     * including one inside shared text ("Check out my deck: https://…"), or a bare id. Null otherwise.
     */
    fun archidektId(text: String): Long? {
        val t = text.trim()
        deckUrl.find(t)?.let { return it.groupValues[1].toLongOrNull() }
        return t.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
    }
}

enum class DeckGroupBy(val label: String) { CATEGORY("Category"), TYPE("Card type") }

data class DeckSection(val title: String, val rows: List<DeckCardRow>) {
    val count get() = rows.sumOf { it.item.quantity }
}

object DeckGrouping {
    /** Order of type sections, as deckbuilders list them. A card goes under the first type it has. */
    val TYPE_ORDER = listOf("Creature", "Planeswalker", "Battle", "Land", "Instant", "Sorcery", "Artifact", "Enchantment", "Kindred")

    private val plural = mapOf(
        "Creature" to "Creatures", "Planeswalker" to "Planeswalkers", "Battle" to "Battles", "Land" to "Lands",
        "Instant" to "Instants", "Sorcery" to "Sorceries", "Artifact" to "Artifacts", "Enchantment" to "Enchantments",
        "Kindred" to "Kindred", "Other" to "Other",
    )

    fun primaryType(types: String): String {
        val words = types.split(' ')
        return TYPE_ORDER.firstOrNull { it in words } ?: "Other"
    }

    /** Commander(s) first, then the categories alphabetically or the card types in [TYPE_ORDER]; cards by name. */
    fun sections(rows: List<DeckCardRow>, by: DeckGroupBy): List<DeckSection> {
        val (commanders, rest) = rows.partition { it.item.commander }
        val byName = compareBy<DeckCardRow, String>(String.CASE_INSENSITIVE_ORDER) { it.item.card.name }
        val groups = when (by) {
            DeckGroupBy.CATEGORY -> rest.groupBy { it.item.category }.toSortedMap(String.CASE_INSENSITIVE_ORDER).toList()
            DeckGroupBy.TYPE -> rest.groupBy { it.item.primaryType }.toList()
                .sortedBy { (type, _) -> TYPE_ORDER.indexOf(type).let { if (it < 0) TYPE_ORDER.size else it } }
                .map { (type, list) -> (plural[type] ?: type) to list }
        }
        return buildList {
            if (commanders.isNotEmpty()) add(DeckSection(if (commanders.size > 1) "Commanders" else "Commander", commanders.sortedWith(byName)))
            groups.forEach { (title, list) -> add(DeckSection(title, list.sortedWith(byName))) }
        }
    }
}
