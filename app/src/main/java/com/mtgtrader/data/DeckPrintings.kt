package com.mtgtrader.data

/**
 * Shows a deck's cards in the printings you own instead of the ones on Archidekt (the deck on
 * Archidekt isn't changed). Since 1.22.
 */
object DeckPrintings {
    /** A pick that keeps Archidekt's printing for that card. */
    const val ARCHIDEKT = "archidekt"

    fun nameKey(name: String) = name.substringBefore(" // ").trim().lowercase()

    /** "scryfallId|FOIL" for a pick. */
    fun pickValue(scryfallId: String, finish: Finish) = "$scryfallId|${finish.name}"

    data class Result(
        /** The deck's rows, with owned printings swapped in. */
        val rows: List<DeckCardRow>,
        /** Deck card id → Archidekt's own printing, for the rows shown in another printing. */
        val original: Map<Long, CardRef>,
    )

    /**
     * For each non-basic card: your pick if you still own it; else a copy in the binder named after
     * the deck; else Archidekt's printing if you own it; else the printing you own most copies of,
     * outside the trade binder if possible. Cards you don't own keep Archidekt's printing.
     */
    fun match(
        rows: List<DeckCardRow>,
        owned: List<CollectionRow>,
        deckBinderId: Long?,
        tradeBinderId: Long?,
        picks: Map<String, String>,
    ): Result {
        val byName = owned.groupBy { nameKey(it.item.card.name) }
        val original = HashMap<Long, CardRef>()
        val out = rows.map { row ->
            val item = row.item
            val key = nameKey(item.card.name)
            if (DeckToCollection.isBasic(key)) return@map row
            val copies = byName[key].orEmpty()
            if (copies.isEmpty()) return@map row
            val pick = picks[key]
            if (pick == ARCHIDEKT) return@map row
            val choice: CollectionRow = pick?.let { p ->
                val sid = p.substringBefore('|')
                val finish = runCatching { Finish.valueOf(p.substringAfter('|')) }.getOrNull()
                copies.firstOrNull { it.item.card.scryfallId == sid && (finish == null || it.item.finish == finish) }
            } ?: copies.firstOrNull { deckBinderId != null && it.item.binderId == deckBinderId }
                ?: copies.firstOrNull { it.item.card.scryfallId == item.card.scryfallId && it.item.finish == item.finish }
                ?: copies.firstOrNull { it.item.card.scryfallId == item.card.scryfallId }
                ?: run {
                    val outside = copies.filter { it.item.binderId != tradeBinderId }.ifEmpty { copies }
                    outside.groupBy { it.item.card.scryfallId to it.item.finish }
                        .maxWith(compareBy<Map.Entry<Pair<String, Finish>, List<CollectionRow>>> { e -> e.value.sumOf { it.item.quantity } }.thenBy { it.key.first })
                        .value.first()
                }
            if (choice.item.card.scryfallId == item.card.scryfallId && choice.item.finish == item.finish) return@map row
            original[item.id] = item.card
            row.copy(
                item = item.copy(card = choice.item.card, foil = choice.item.foil, etched = choice.item.etched),
                price = choice.price,
            )
        }
        return Result(out, original)
    }
}
