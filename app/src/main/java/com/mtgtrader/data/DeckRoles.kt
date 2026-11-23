package com.mtgtrader.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * What a deck's cards do for it (since 1.97), for the line under the deck's header: how many lands it has and how
 * many cards ramp, draw, remove and so on.
 *
 * A card's tags are its own tags when the user gave it any (kept per card name in [Settings.cardTags], so they count
 * in every deck), else the roles its categories and the website's auto categories name. The website works it out the
 * same way, from the same rules.
 */
object DeckRoles {
    /** The roles, in the order the deck page shows them. */
    val ROLES = listOf("Ramp", "Draw", "Removal", "Wipes", "Tutor", "Protection", "Recursion")

    /** How many of each the deck wants (both ends included); everything else has no guideline. */
    private val RANGES = mapOf(
        "Lands" to 35..38,
        "Ramp" to 10..12,
        "Draw" to 10..12,
        "Removal" to 8..12,
        "Wipes" to 2..4,
    )

    /** The most characters in one tag, and the most tags on one card. */
    const val MAX_TAG = 30
    const val MAX_TAGS = 10

    /** The categories and auto categories that stand for a role, lower case. */
    private val ROLE_OF = buildMap<String, String> {
        for ((role, names) in listOf(
            "Ramp" to listOf("ramp", "mana rock", "mana rocks", "mana"),
            "Draw" to listOf("draw", "card draw", "card advantage"),
            "Removal" to listOf("removal", "spot removal", "interaction", "counterspell", "counterspells"),
            "Wipes" to listOf("wipe", "wipes", "board wipe", "board wipes", "sweeper", "sweepers", "wrath", "wraths"),
            "Tutor" to listOf("tutor", "tutors"),
            "Protection" to listOf("protection"),
            "Recursion" to listOf("recursion"),
        )) {
            names.forEach { put(it, role) }
        }
    }

    /** A card as the counts need it: its line and its tags. */
    data class RoleCard(
        val name: String,
        val types: String,
        val quantity: Int,
        val commander: Boolean,
        val maybeboard: Boolean,
        val categories: List<String>,
        /** The card's own tags ([Settings.cardTags]), or null when it has none. */
        val ownTags: List<String>?,
        /** The website's automatic categories for the card's name. */
        val auto: List<String>,
        /** Scryfall's type line (faces joined with " // "), which the lands count from; null until it's known. */
        val typeLine: String? = null,
    )

    /**
     * One line under the deck's header: [role] (a role's name or an own tag), how many cards, and how that compares
     * to the guideline. [extra] is the double-faced cards whose back is a land (MDFCs), only for "Lands", shown after
     * its count as "37+2".
     */
    data class RoleCount(val role: String, val count: Int, val status: String, val extra: Int = 0)

    /**
     * A card's tags: its own tags when it has any (as spelled), else the roles its [categories] and [auto] name, in
     * the role order and without duplicates. A card with no tags of its own and nothing that names a role has none.
     */
    fun tagsOf(name: String, categories: List<String>, ownTags: List<String>?, auto: List<String>): List<String> {
        if (ownTags != null) return ownTags
        val found = (auto + categories).mapNotNull { ROLE_OF[it.trim().lowercase()] }.toSet()
        return ROLES.filter { it in found }
    }

    /**
     * Whether a card is a land: the first face of its type line (Scryfall's when known, else its stored [RoleCard.types])
     * counts once, so a pathway ("Land // Land") is a land. The types before the dash ("Legendary Land — Desert").
     */
    fun isLand(card: RoleCard): Boolean {
        val line = card.typeLine?.takeIf { it.isNotEmpty() } ?: card.types
        return "Land" in line.split(" // ").first().substringBefore(" — ").split(' ')
    }

    /** Whether a card is an MDFC, a spell on the front and a land on a later face ("Instant // Land", [MdfcLands]). */
    fun isMdfc(card: RoleCard): Boolean = MdfcLands.backIsLand(card.typeLine?.takeIf { it.isNotEmpty() } ?: card.types)

    /**
     * The counts line for [cards]: lands, then the roles in their order, then up to five own tags that aren't roles
     * (the most used first). The maybeboard and the commanders don't count.
     */
    fun counts(cards: List<RoleCard>): List<RoleCount> {
        val main = cards.filterNot { it.maybeboard || it.commander }
        val lands = main.filter(::isLand).sumOf { it.quantity }
        val mdfcs = main.filter(::isMdfc).sumOf { it.quantity }
        val roles = LinkedHashMap<String, Int>()
        // An own tag that isn't a role's name, by its lower case form to its spelling and copies.
        val other = LinkedHashMap<String, Pair<String, Int>>()
        for (card in main) {
            val tags = tagsOf(card.name, card.categories, card.ownTags, card.auto).map { it.trim() }.filter { it.isNotEmpty() }
            for (tag in tags.distinctBy { it.lowercase() }) {
                val role = ROLES.firstOrNull { it.equals(tag, ignoreCase = true) }
                if (role != null) {
                    roles[role] = (roles[role] ?: 0) + card.quantity
                } else {
                    val seen = other[tag.lowercase()]
                    other[tag.lowercase()] = (seen?.first ?: tag) to ((seen?.second ?: 0) + card.quantity)
                }
            }
        }
        return buildList {
            // The guideline colour goes by the lands alone; the MDFCs are added as "+N".
            add(count("Lands", lands).copy(extra = mdfcs))
            for (role in listOf("Ramp", "Draw", "Removal")) add(count(role, roles[role] ?: 0))
            for (role in listOf("Wipes", "Tutor", "Protection", "Recursion")) roles[role]?.takeIf { it > 0 }?.let { add(count(role, it)) }
            other.values.filter { it.second > 0 }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first })
                .take(5)
                .forEach { add(count(it.first, it.second)) }
        }
    }

    private fun count(role: String, count: Int): RoleCount {
        val range = RANGES[role] ?: return RoleCount(role, count, "none")
        val status = when {
            count < range.first -> "low"
            count > range.last -> "high"
            else -> "ok"
        }
        return RoleCount(role, count, status)
    }

    /** A card's own tags ([Settings.cardTags]) from its JSON text; empty when it has none or it's unreadable. */
    fun parseCardTags(json: String?): Map<String, List<String>> {
        if (json.isNullOrBlank()) return emptyMap()
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, List<String>>()
        for ((name, value) in obj) {
            val key = DeckNotes.nameKey(name)
            val tags = normaliseTags((value as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content })
            if (key.isNotEmpty() && tags.isNotEmpty()) out[key] = tags
        }
        return out.toSortedMap()
    }

    /** [tags] as [Settings.cardTags] JSON: keys sorted, a card without tags left out; "{}" when there are none. */
    fun encodeCardTags(tags: Map<String, List<String>>): String {
        val clean = tags.entries.mapNotNull { (name, list) ->
            val key = DeckNotes.nameKey(name)
            val normalised = normaliseTags(list)
            if (key.isEmpty() || normalised.isEmpty()) null else key to normalised
        }.sortedBy { it.first }
        return buildJsonObject {
            for ((key, list) in clean) put(key, buildJsonArray { list.forEach { add(it) } })
        }.toString()
    }

    /** [tags] trimmed, each cut to [MAX_TAG], duplicates (ignoring case) left out with the first spelling kept, at most [MAX_TAGS]. */
    fun normaliseTags(tags: List<String>): List<String> = tags.asSequence()
        .map { it.trim().take(MAX_TAG).trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(MAX_TAGS)
        .toList()
}
