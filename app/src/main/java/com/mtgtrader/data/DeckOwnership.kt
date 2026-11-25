package com.mtgtrader.data

/** How much of a deck line the collection holds, for the deck page's "Show cards I own" (since 1.118). */
enum class DeckOwned {
    /** Enough copies of the deck's own printing and finish. */
    EXACT,
    /** The card in another printing or finish, or too few copies of the deck's own. */
    OTHER,
    /** Not in the collection at all. */
    NONE,
}

/** Like the website's collection tracker: compares each line with the printing the deck lists on Archidekt. */
object DeckOwnership {
    /** Deck card id → what you own of it. Basic lands always count as NONE (no colour), like on the website. */
    fun of(rows: List<DeckCardRow>, owned: List<CollectionRow>): Map<Long, DeckOwned> {
        val byName = owned.groupBy { DeckPrintings.nameKey(it.item.card.name) }
        val out = HashMap<Long, DeckOwned>()
        for (row in rows) {
            val item = row.item
            val key = DeckPrintings.nameKey(item.card.name)
            if (DeckToCollection.isBasic(key)) {
                out[item.id] = DeckOwned.NONE
                continue
            }
            val copies = byName[key].orEmpty()
            val exact = copies.filter { it.item.card.scryfallId == item.card.scryfallId && it.item.finish == item.finish }.sumOf { it.item.quantity }
            out[item.id] = when {
                exact >= item.quantity -> DeckOwned.EXACT
                copies.isNotEmpty() -> DeckOwned.OTHER
                else -> DeckOwned.NONE
            }
        }
        return out
    }
}
