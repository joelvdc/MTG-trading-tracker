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
}

@Serializable
data class SortLevel(val field: SortField, val reversed: Boolean = false)

/** Sorting in layers: by the first level, then the next among equals, and so on. */
@Serializable
data class SortSpec(val levels: List<SortLevel> = listOf(SortLevel(SortField.NAME))) {
    fun encode(): String = json.encodeToString(serializer(), this)

    val label: String get() = levels.joinToString(" → ") { it.field.label }

    fun comparator(priceType: PriceType): Comparator<CollectionRow> {
        val parts = levels.map { level ->
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
        }
    }
}

/** How chosen colours match. */
enum class ColorMatch(val label: String) {
    ANY("Has any of them"),
    EXACT("Exactly these"),
    WITHIN("Fits in this color identity"),
}

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
) {
    val isEmpty get() = this == CollectionFilter()

    val count: Int
        get() = listOf(colors.isNotEmpty(), types.isNotEmpty(), rarities.isNotEmpty(), sets.isNotEmpty(), finishes.isNotEmpty(),
            conditions.isNotEmpty(), languages.isNotEmpty(), minPrice != null || maxPrice != null, decks != DeckFilter.ANY).count { it }

    fun encode(): String = json.encodeToString(serializer(), this)

    fun matches(row: CollectionRow, priceType: PriceType, deckNames: Set<String>): Boolean {
        val i = row.item
        if (colors.isNotEmpty() && !colorsMatch(row.info)) return false
        if (types.isNotEmpty()) {
            val t = row.info?.typeLine ?: return false
            if (types.none { it in t }) return false
        }
        if (rarities.isNotEmpty() && i.card.rarity !in rarities) return false
        if (sets.isNotEmpty() && i.card.setCode.lowercase() !in sets) return false
        if (finishes.isNotEmpty() && i.finish !in finishes) return false
        if (conditions.isNotEmpty() && i.condition !in conditions) return false
        if (languages.isNotEmpty() && i.language !in languages) return false
        if (minPrice != null || maxPrice != null) {
            val p = row.unitPrice(priceType) ?: return false
            if (minPrice != null && p < minPrice) return false
            if (maxPrice != null && p > maxPrice) return false
        }
        val inDeck = i.card.name.substringBefore(" // ").lowercase() in deckNames
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
