package com.mtgtrader.data

/**
 * The estimated bracket and salt the website rates local decks with (POST /api/rate): the same rating Commander Salt
 * gives decks it has, worked out from the list. Kept with the deck as a [SaltCard] whose [SaltCard.source] is "app",
 * stamped with the [listKey] it was made for and the [RATING_VERSION] the website used, so a changed list or a newer
 * rating is asked for again. Since 1.115 every deck is rated this way when brackets come from the app.
 */
object LocalRating {
    /** The rating's version: must equal the website's (web/src/lib/localratings.ts), which is kept in each rating's text. */
    const val RATING_VERSION = 2

    /**
     * A short fingerprint of a deck's list, worked out exactly like the website's rating keeps it: one line per card in
     * the deck (not the maybeboard), "quantity name" with "*" after a commander's, the name on its front face in lower
     * case with single spaces; sorted, joined, and hashed with 32-bit FNV-1a, as 8 hex digits.
     */
    fun listKey(cards: List<DeckCard>): String {
        val text = cards.filter { !it.maybeboard }
            .map { "${it.quantity} ${nameKey(it.card.name)}${if (it.commander) "*" else ""}" }
            .sorted()
            .joinToString("\n")
        var h = 0x811c9dc5u
        for (c in text) {
            h = h xor c.code.toUInt()
            h *= 0x01000193u
        }
        return h.toString(16).padStart(8, '0')
    }

    /**
     * Whether the deck's rating needs asking for again: a local deck, or any deck when brackets come from the app
     * ([source] is [BracketSource.APP]), whose cards no longer match the rating kept on it (or that never got an
     * estimate), or whose rating the website made with another version. A deck without cards is never stale, nor a
     * deck from Archidekt when brackets are Commander Salt's (its own data is the rating).
     */
    fun stale(deck: Deck, cards: List<DeckCard>, source: BracketSource): Boolean {
        if (cards.isEmpty()) return false
        // Local decks are the app's estimate; for the others only when brackets come from the app.
        if (!deck.local && source != BracketSource.APP) return false
        val card = SaltCard.decode(deck.saltCard) ?: return true
        if (!card.fromApp) return !deck.local
        return card.listKey != listKey(cards) || card.ratingVersion != RATING_VERSION
    }

    /**
     * Whether a deck from Archidekt should have Commander Salt score it before the app can estimate its bracket: it
     * holds the app's estimate but never Commander Salt's own data. Since 1.115.
     */
    fun needsCommanderSalt(deck: Deck, source: BracketSource): Boolean =
        !deck.local && source == BracketSource.COMMANDER_SALT && SaltCard.decode(deck.saltCard)?.fromApp == true

    /** A card's name as the fingerprint writes it: its front face, trimmed, lower case, single spaces. */
    private fun nameKey(name: String) = name.substringBefore(" // ").trim().lowercase().replace(Regex("\\s+"), " ")
}
