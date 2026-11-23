package com.mtgtrader.data

/**
 * The estimated bracket and salt the website rates local decks with (POST /api/rate): the same rating Commander Salt
 * gives decks it has, worked out from the list. Kept with the deck as a [SaltCard] whose [SaltCard.source] is "app",
 * stamped with the [listKey] it was made for and the [RATING_VERSION] the website used, so a changed list or a newer
 * rating is asked for again. Since 1.116 every deck is rated this way.
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
     * Ratings made before the website's full card facts were built (23 Nov 2026 17:50 UTC, as RATED_SINCE in
     * web/src/lib/deckrating.ts) counted land tutors: each is made again once.
     */
    const val RATED_SINCE = 1_795_456_200_000L

    /**
     * Whether the deck's rating needs asking for again (every deck since 1.116): it never got the website's estimate
     * (or holds Commander Salt's data from before), its cards no longer match the rating kept on it, the website made
     * it with another version, or before [RATED_SINCE]. A deck without cards is never stale.
     */
    fun stale(deck: Deck, cards: List<DeckCard>): Boolean {
        if (cards.isEmpty()) return false
        val card = SaltCard.decode(deck.saltCard) ?: return true
        if (!card.fromApp) return true
        if ((deck.scoredAt ?: 0L) < RATED_SINCE) return true
        return card.listKey != listKey(cards) || card.ratingVersion != RATING_VERSION
    }

    /** A card's name as the fingerprint writes it: its front face, trimmed, lower case, single spaces. */
    private fun nameKey(name: String) = name.substringBefore(" // ").trim().lowercase().replace(Regex("\\s+"), " ")
}
