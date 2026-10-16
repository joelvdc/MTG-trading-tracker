package com.mtgtrader.data

/**
 * Bought cards against the wishlist (since 1.39): after an order import or a package check, which
 * wishlist cards the order's copies are, so they can be marked as received from that order (and then
 * aren't added to the collection a second time) or taken off the wishlist.
 */
object WishOrders {
    /** [WishlistItem.receivedFrom] of cards whose copies went into the collection when marked as received. */
    const val ADDED = "added to the collection"

    /** Copies of one printing that went into the collection. */
    data class Bought(val card: CardRef, val foil: Boolean, val quantity: Int)

    /** A wishlist card and how many of its wanted copies the order covers. */
    data class Match(val item: WishlistItem, val quantity: Int)

    enum class Choice(val label: String) {
        RECEIVED("Mark as received"),
        REMOVE("Take off the wishlist"),
        LEAVE("Leave it"),
    }

    private fun nameKey(name: String) = name.substringBefore(" // ").trim().lowercase()

    /**
     * The wishlist cards (to buy, ordered or received, but not yet linked to the collection) that [bought]
     * covers: "this printing only" cards by printing and finish, the others by name in any printing and
     * finish. Each copy bought counts once: exact cards take theirs first, the rest goes to name matches,
     * oldest wishes first.
     */
    fun match(wishlist: List<WishlistItem>, bought: List<Bought>): List<Match> {
        val left = bought.map { it to intArrayOf(it.quantity) }
        // Cards added offline match by name too: they want any printing.
        val open = wishlist.filter { it.receivedFrom == null }
            .sortedWith(compareBy({ it.anyPrinting }, { it.addedAt }, { it.id }))
        val out = ArrayList<Match>()
        for (w in open) {
            var want = w.quantity
            var got = 0
            for ((b, rem) in left) {
                if (want == 0) break
                if (rem[0] == 0) continue
                val fits = if (w.anyPrinting) nameKey(b.card.name) == nameKey(w.card.name) else b.card.scryfallId == w.card.scryfallId && b.foil == w.foil
                if (!fits) continue
                val take = minOf(want, rem[0])
                rem[0] -= take
                want -= take
                got += take
            }
            if (got > 0) out += Match(w, got)
        }
        return out.sortedBy { it.item.card.name.lowercase() }
    }

    /** "CardTrader order #12345", or without a number when the order had several or none. */
    fun label(shop: String, orderNumbers: Collection<String?>): String {
        val numbers = orderNumbers.filterNotNull().filter { it.isNotBlank() }.distinct()
        return "$shop order" + (numbers.singleOrNull()?.let { " #$it" } ?: "")
    }
}
