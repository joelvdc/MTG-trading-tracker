package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the collection can be sorted by, with each order's natural direction first. Since 1.18. */
enum class SortField(val label: String, val forward: String, val backward: String) {
    NAME("Name", "A to Z", "Z to A"),
    COLOR("Color", "W, U, B, R, G, multicolor, colorless, lands", "Reversed"),
    TYPE("Type", "Creatures first", "Lands first"),
    MANA_VALUE("Mana value", "Low to high", "High to low"),
    RARITY("Rarity", "Mythic first", "Common first"),
    SET("Set", "A to Z", "Z to A"),
    NUMBER("Collector number", "Low to high", "High to low"),
    VALUE("Value per card", "Highest first", "Lowest first"),
    RECENT("Date added", "Newest first", "Oldest first"),
    QUANTITY("Copies", "Most first", "Fewest first"),

    /** One copy's change lately ([CollectionRow.change]), in percent and in money. Since 1.32. */
    CHANGE_PCT("Change in %", "Biggest rise first", "Biggest fall first"),
    CHANGE_MONEY("Change in money", "Biggest rise first", "Biggest fall first"),

    /** The first active deck that uses the card, by name; cards in no deck last both ways. Since 1.107. */
    DECK("Deck", "A to Z, cards in no deck last", "Z to A, cards in no deck last");

    /** Sorts by the price change, which not every card has. */
    val isChange get() = this == CHANGE_PCT || this == CHANGE_MONEY
}

/** A card's front face in lowercase, as the decks' cards are matched (see [SortSpec.deckComparator] and [CollectionFilter.matches]). */
fun frontName(name: String) = name.substringBefore(" // ").lowercase()

/**
 * Which active decks use each card: a card's front-face lowercase name → its decks' names, A to Z (as the collection's
 * deck filter, its Deck column and its Deck sort use them). Since 1.107.
 */
object DeckUses {
    /** The decks [card] ([frontName]) is in, A to Z; empty when none. */
    fun decksOf(card: String, use: Map<String, List<String>>): List<String> = use[card].orEmpty()

    /** The first deck that uses [card], or null for a card in no deck. */
    fun deckOf(card: String, use: Map<String, List<String>>): String? = decksOf(card, use).firstOrNull()

    /** Whether [deckName]'s deck uses [card]. */
    fun deckUses(card: String, deckName: String, use: Map<String, List<String>>): Boolean = decksOf(card, use).any { it.equals(deckName, true) }

    /** [rows] (a deck per card) as [decksOf] wants it: each card's decks, A to Z. */
    fun of(rows: List<ActiveDeckCard>): Map<String, List<String>> = rows.groupBy({ frontName(it.name) }, { it.deck })
        .mapValues { (_, decks) -> decks.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER) }
}

@Serializable
data class SortLevel(val field: SortField, val reversed: Boolean = false)

/** Sorting in layers: by the first level, then the next among equals, and so on. */
@Serializable
data class SortSpec(val levels: List<SortLevel> = listOf(SortLevel(SortField.NAME))) {
    fun encode(): String = json.encodeToString(serializer(), this)

    val label: String get() = levels.joinToString(" → ") { it.field.label }

    /** [deckUse] says which decks use each card (a front-face lowercase name, see [frontName]): for [SortField.DECK]. Since 1.107. */
    fun comparator(priceType: PriceType, deckUse: Map<String, List<String>> = emptyMap()): Comparator<CollectionRow> {
        val parts = levels.map { level ->
            if (level.field.isChange) return@map changeComparator(level.field, level.reversed)
            // Cards in no deck go last both ways: the direction only comes before the names.
            if (level.field == SortField.DECK) return@map deckComparator(deckUse, level.reversed)
            val c = fieldComparator(level.field, priceType)
            if (level.reversed) c.reversed() else c
        }
        // Name breaks remaining ties, so the order never depends on the database.
        return (parts + compareBy(String.CASE_INSENSITIVE_ORDER) { it.item.card.name }).reduce { a, b -> a.then(b) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(s: String?): SortSpec = s?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
            ?.takeIf { it.levels.isNotEmpty() } ?: SortSpec()

        private val RARITY = listOf("mythic", "rare", "uncommon", "common", "special", "bonus")

        fun typeRank(typeLine: String?): Int {
            if (typeLine == null) return 99
            val front = typeLine.substringBefore(" // ").substringBefore(" — ")
            return DeckGrouping.TYPE_ORDER.indexOf(DeckGrouping.primaryType(front)).let { if (it < 0) 98 else it }
        }

        private fun number(n: String) = n.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE

        /**
         * By the first deck that uses the card (its name, A to Z or Z to A); cards in no deck go last either way.
         * Since 1.107.
         */
        fun deckComparator(deckUse: Map<String, List<String>>, reversed: Boolean): Comparator<CollectionRow> {
            val order = String.CASE_INSENSITIVE_ORDER
            fun deckOf(row: CollectionRow): String? = DeckUses.deckOf(frontName(row.item.card.name), deckUse)
            val byDeck = Comparator<CollectionRow> { a, b ->
                val x = deckOf(a) ?: return@Comparator 0
                val y = deckOf(b) ?: return@Comparator 0
                order.compare(x, y)
            }
            return compareBy<CollectionRow> { deckOf(it) == null }.then(if (reversed) byDeck.reversed() else byDeck)
        }

        fun fieldComparator(f: SortField, priceType: PriceType): Comparator<CollectionRow> = when (f) {
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.item.card.name }
            SortField.COLOR -> compareBy { MtgColors.sortKey(it.info?.colors, it.info?.isLand == true) }
            SortField.TYPE -> compareBy { typeRank(it.info?.typeLine) }
            // Cards without details yet go last.
            SortField.MANA_VALUE -> compareBy { it.info?.cmc ?: 99.0 }
            SortField.RARITY -> compareBy { RARITY.indexOf(it.item.card.rarity).let { r -> if (r < 0) 99 else r } }
            SortField.SET -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.item.card.setName }
            SortField.NUMBER -> compareBy<CollectionRow> { it.item.card.setName }.thenBy { number(it.item.card.collectorNumber) }
            SortField.VALUE -> compareByDescending { it.unitPrice(priceType) ?: -1.0 }
            SortField.RECENT -> compareByDescending { it.item.addedAt }
            SortField.QUANTITY -> compareByDescending { it.item.quantity }
            // Needs the decks (see [comparator]); on its own, by name.
            SortField.CHANGE_PCT, SortField.CHANGE_MONEY -> changeComparator(f, fallersFirst = false)
            SortField.DECK -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.item.card.name }
        }

        /** Biggest rise (or fall) first; cards without a change go last either way. Since 1.32. */
        fun changeComparator(f: SortField, fallersFirst: Boolean): Comparator<CollectionRow> {
            fun value(r: CollectionRow) = r.change?.let { if (f == SortField.CHANGE_PCT) it.pct else it.money }
            val byChange = if (fallersFirst) compareBy<CollectionRow> { value(it) ?: 0.0 } else compareByDescending { value(it) ?: 0.0 }
            return compareBy<CollectionRow> { value(it) == null }.then(byChange)
        }
    }
}

/** How chosen colours match. */
enum class ColorMatch(val label: String) {
    ANY("Has any of them"),
    EXACT("Exactly these"),
    WITHIN("Fits in this color identity"),
    /** Since 1.21, for the stats screen's colour identity groups. */
    IDENTITY("Exactly this color identity"),
}

/** Signed, altered, or neither (plain copies). Since 1.27. */
enum class MarkFilter(val label: String) { SIGNED("Signed"), ALTERED("Altered"), PLAIN("Neither") }

enum class DeckFilter(val label: String) { ANY("All cards"), IN_DECKS("Only cards in my decks"), NOT_IN_DECKS("Only cards in no deck") }

/**
 * The collection's filter button: the things awkward to type in the search field. All set parts
 * must match; within a part, any of the chosen values does. Since 1.18.
 */
@Serializable
data class CollectionFilter(
    /** W, U, B, R, G; C = colorless; M = multicolor. */
    val colors: Set<Char> = emptySet(),
    val colorMatch: ColorMatch = ColorMatch.ANY,
    val types: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val sets: Set<String> = emptySet(),
    val finishes: Set<Finish> = emptySet(),
    val conditions: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val decks: DeckFilter = DeckFilter.ANY,
    /** Since 1.27. */
    val marks: Set<MarkFilter> = emptySet(),
    /** MDFC, transforming, split (Scryfall's layout). Since 1.90. */
    val faces: Set<FaceKind> = emptySet(),
    /** Only the cards a deck uses, by the deck's name (any letter case). Since 1.107. */
    val deck: String? = null,
) {
    val isEmpty get() = this == CollectionFilter()

    val count: Int
        get() = listOf(colors.isNotEmpty(), types.isNotEmpty(), rarities.isNotEmpty(), sets.isNotEmpty(), finishes.isNotEmpty(),
            conditions.isNotEmpty(), languages.isNotEmpty(), minPrice != null || maxPrice != null, decks != DeckFilter.ANY, marks.isNotEmpty(),
            faces.isNotEmpty(), !deck.isNullOrBlank()).count { it }

    fun encode(): String = json.encodeToString(serializer(), this)

    /** [deckUse] says which decks use each card (see [DeckUses]): the "used by deck" filter needs it. Since 1.107. */
    fun matches(row: CollectionRow, priceType: PriceType, deckNames: Set<String>, deckUse: Map<String, List<String>> = emptyMap()): Boolean {
        val i = row.item
        if (colors.isNotEmpty() && !colorsMatch(row.info)) return false
        if (types.isNotEmpty()) {
            val t = row.info?.typeLine ?: return false
            if (types.none { it in t }) return false
        }
        if (faces.isNotEmpty() && row.info?.faces !in faces) return false
        if (rarities.isNotEmpty() && i.card.rarity !in rarities) return false
        if (sets.isNotEmpty() && i.card.setCode.lowercase() !in sets) return false
        if (finishes.isNotEmpty() && i.finish !in finishes) return false
        if (conditions.isNotEmpty() && i.condition !in conditions) return false
        if (languages.isNotEmpty() && i.language !in languages) return false
        if (marks.isNotEmpty() && !(
                (MarkFilter.SIGNED in marks && i.signed) || (MarkFilter.ALTERED in marks && i.altered) ||
                    (MarkFilter.PLAIN in marks && !i.signed && !i.altered)
                )
        ) return false
        if (minPrice != null || maxPrice != null) {
            val p = row.unitPrice(priceType) ?: return false
            if (minPrice != null && p < minPrice) return false
            if (maxPrice != null && p > maxPrice) return false
        }
        val card = frontName(i.card.name)
        if (deck != null && !DeckUses.deckUses(card, deck, deckUse)) return false
        val inDeck = card in deckNames
        return when (decks) {
            DeckFilter.ANY -> true
            DeckFilter.IN_DECKS -> inDeck
            DeckFilter.NOT_IN_DECKS -> !inDeck
        }
    }

    private fun colorsMatch(info: CardInfo?): Boolean {
        info ?: return false
        val wanted = colors.filter { it in MtgColors.ORDER }.toSet()
        return when (colorMatch) {
            ColorMatch.ANY ->
                (wanted.isNotEmpty() && info.colors.any { it in wanted }) ||
                    ('C' in colors && info.colors.isEmpty()) || ('M' in colors && info.colors.length > 1)
            ColorMatch.EXACT -> if ('C' in colors && wanted.isEmpty()) info.colors.isEmpty() else info.colors.toSet() == wanted
            ColorMatch.WITHIN -> info.colorIdentity.all { it in wanted }
            ColorMatch.IDENTITY -> info.colorIdentity.toSet() == wanted
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(s: String?): CollectionFilter =
            s?.takeIf { it.isNotEmpty() }?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: CollectionFilter()

        val TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle", "Legendary")
        val RARITIES = listOf("common" to "Common", "uncommon" to "Uncommon", "rare" to "Rare", "mythic" to "Mythic", "special" to "Special")
    }
}
