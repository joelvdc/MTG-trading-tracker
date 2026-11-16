package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One card of a package: its name, the printing to add it in and how many copies. Since 1.97. */
@Serializable
data class PackageCard(val name: String, val scryfallId: String, val quantity: Int)

/**
 * A saved group of cards to add to decks, e.g. a mana rock suite (since 1.97). Kept in the synced setting
 * `deckPackages` in the order the user made them, so it's there on every phone and on the website.
 */
@Serializable
data class DeckPackage(val id: String, val name: String, val cards: List<PackageCard>)

/** Where a package is used: [full] are the decks holding all its cards, [partly] the decks holding some of them. */
object DeckPackages {
    /** The most cards in one package, and the most packages. */
    const val MAX_CARDS = 200
    const val MAX_PACKAGES = 100

    /** The most characters in a package's name. */
    const val MAX_NAME = 60

    /** Where a package is used, by deck id. */
    data class Use(val full: List<Long>, val partly: List<Long>)

    private val json = Json { ignoreUnknownKeys = true }
    private val packages = ListSerializer(DeckPackage.serializer())

    /** The packages in [text] (the setting's value); empty when there are none or it's unreadable. */
    fun parse(text: String?): List<DeckPackage> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(packages, text) }.getOrDefault(emptyList()).take(MAX_PACKAGES)
    }

    /** [list] as the setting's value, with each package within the limits. */
    fun encode(list: List<DeckPackage>): String = json.encodeToString(packages, list.take(MAX_PACKAGES).map { clean(it) })

    /** The package with its name trimmed and its cards cut to [MAX_CARDS]. */
    fun clean(pkg: DeckPackage): DeckPackage = pkg.copy(name = pkg.name.trim().take(MAX_NAME), cards = pkg.cards.take(MAX_CARDS))

    /** Which of [decks] (deck id to its lines) hold all of [pkg]'s cards, and which hold some of them. */
    fun use(pkg: DeckPackage, decks: Map<Long, List<DeckCard>>): Use {
        val keys = pkg.cards.map { DeckNotes.nameKey(it.name) }.filter { it.isNotEmpty() }.toSet()
        if (keys.isEmpty()) return Use(emptyList(), emptyList())
        val full = mutableListOf<Long>()
        val partly = mutableListOf<Long>()
        for ((deckId, lines) in decks) {
            val have = lines.filterNot { it.maybeboard }.map { DeckNotes.nameKey(it.card.name) }.toSet()
            val found = keys.count { it in have }
            when {
                found == keys.size -> full += deckId
                found > 0 -> partly += deckId
            }
        }
        return Use(full, partly)
    }
}
