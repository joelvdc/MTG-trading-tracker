package com.mtgtrader.data

/**
 * "Find these cards" (since 1.104): where the copies of a pasted list (or a deck) are stored, grouped by binder so
 * they're emptied one binder at a time, with what's only in other decks and what isn't owned at all.
 *
 * [FindCards.plan] is the pure core, ported by the website (see `web/test/fixtures/findcards-cases.json`);
 * [CardFinder] builds its input from the database.
 */
data class FindWanted(val name: String, val qty: Int, val scryfallId: String? = null)

/** A stack of copies in a binder. */
data class FindStack(
    val name: String,
    val scryfallId: String,
    val set: String,
    val number: String,
    val foil: Boolean,
    val etched: Boolean,
    val binderKey: String,
    val binderName: String,
    val copies: Int,
)

/**
 * A binder (or Unsorted, whose key is [FindCards.UNSORTED]): where it stands in the binder bar ([isDeckBinder] and
 * [isTradeBinder] for the order copies are picked in).
 */
data class FindBinder(val key: String, val name: String, val isDeckBinder: Boolean = false, val isTradeBinder: Boolean = false)

/**
 * How copies are picked when a list needs some of them: the plain binders first, then the trade binder, then deck
 * binders, which hold cards their deck needs.
 */
fun pickOrder(b: FindBinder) = when {
    !b.isDeckBinder && !b.isTradeBinder -> 0
    b.isTradeBinder -> 1
    else -> 2
}

/** What a deck uses (not its maybeboard): nameKey → copies. */
data class FindDeck(val id: Long, val name: String, val cards: Map<String, Int>)

data class FindInput(
    val wanted: List<FindWanted>,
    val stacks: List<FindStack>,
    /** In binder-bar order. */
    val binders: List<FindBinder>,
    val decks: List<FindDeck> = emptyList(),
    /** The deck the list came from: its own use doesn't count against the list. */
    val fromDeck: Long? = null,
    val skipBasics: Boolean = true,
)

data class FindLine(
    val lineKey: String,
    val name: String,
    val copies: Int,
    val set: String,
    val number: String,
    val foil: Boolean,
    val etched: Boolean,
    /** These copies are the printing the list names (set and number). */
    val wantedPrinting: Boolean,
)

/** The copies to pull out of one binder. */
data class FindGroup(val binderKey: String, val binderName: String, val lines: List<FindLine>)

/** Copies the list needs that other decks use. */
data class FindInDeck(val name: String, val copies: Int, val decks: List<String>)

data class FindMissing(val name: String, val copies: Int)

data class FindTotal(val wanted: Int, val found: Int, val inDecks: Int, val notOwned: Int)

data class FindResult(
    /** Binders in bar order, only those with copies to find. */
    val groups: List<FindGroup>,
    val onlyInDecks: List<FindInDeck>,
    val notOwned: List<FindMissing>,
    val total: FindTotal,
) {
    /** Every line to tick off. */
    val ticks: Set<String> get() = groups.flatMap { g -> g.lines.map { it.lineKey } }.toSet()
}

object FindCards {
    /** The Unsorted key in [FindBinder]s and [FindStack]s (the same as [BinderOrder.UNSORTED]). */
    const val UNSORTED = BinderOrder.UNSORTED

    /** A card's name for matching: the front face, lowercased and trimmed ("A // B" → "a"). */
    fun nameKey(name: String) = TradeBinderPlanner.nameKey(name)

    fun lineKey(name: String, scryfallId: String, foil: Boolean, etched: Boolean, binderKey: String) =
        "${nameKey(name)}|$scryfallId|$foil|$etched|$binderKey"

    /** Lines naming the same card added together; the first one says which printing is wanted. */
    private fun merged(wanted: List<FindWanted>): List<Pair<FindWanted, String?>> {
        val sums = LinkedHashMap<String, Pair<FindWanted, String?>>()
        for (w in wanted) {
            val key = nameKey(w.name)
            val prev = sums[key]
            sums[key] = if (prev == null) FindWanted(w.name, w.qty, null) to w.scryfallId
            else prev.first.copy(qty = prev.first.qty + w.qty) to (prev.second ?: w.scryfallId)
        }
        return sums.values.toList()
    }

    fun plan(input: FindInput): FindResult {
        val barIndex = HashMap<String, Int>()
        input.binders.forEachIndexed { i, b -> barIndex[b.key] = i }
        val binderByKey = input.binders.associateBy { it.key }
        val linesOrder = compareBy<FindLine, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.set }.thenBy { it.number }

        data class Pick(val stack: FindStack, val copies: Int, val wanted: Boolean)

        val groups = sortedMapOf<Int, FindGroup>()
        val onlyInDecks = mutableListOf<FindInDeck>()
        val notOwned = mutableListOf<FindMissing>()
        var wantedTotal = 0
        var found = 0
        var inDecksTotal = 0
        var notOwnedTotal = 0

        // Duplicates of a name are added together; the first line says which printing is wanted.
        for ((w, printing) in merged(input.wanted)) {
            val key = nameKey(w.name)
            val name = w.name
            val qty = w.qty
            if (input.skipBasics && DeckToCollection.isBasic(key)) continue
            wantedTotal += qty

            val ownedStacks = input.stacks.filter { nameKey(it.name) == key }
            val owned = ownedStacks.sumOf { it.copies }
            val otherUse = input.decks.filter { it.id != input.fromDeck }.sumOf { it.cards[key] ?: 0 }
            val available = maxOf(0, owned - otherUse)
            val take = minOf(qty, available)

            // A printing the list names is only preferred when it's a printing we know.
            val wantedId = printing?.takeIf { id -> ownedStacks.any { it.scryfallId == id } }
            // The wanted printing first, then plain binders, the trade binder, deck binders; bar order; bigger first.
            val order = compareByDescending<FindStack> { wantedId != null && it.scryfallId == wantedId }
                .thenBy { pickOrder(binderByKey[it.binderKey] ?: FindBinder(it.binderKey, it.binderName)) }
                .thenBy { barIndex[it.binderKey] ?: Int.MAX_VALUE }
                .thenByDescending { it.copies }
            val picks = mutableListOf<Pick>()
            var left = take
            for (s in ownedStacks.sortedWith(order)) {
                if (left <= 0) break
                // Each stack gives all its copies: a name is worked out once, so nothing else has picked from it.
                val n = minOf(left, s.copies)
                if (n <= 0) continue
                picks += Pick(s, n, wantedId != null && s.scryfallId == wantedId)
                left -= n
            }
            found += take

            // One line per printing and binder, in name order.
            for ((binderKey, binderPicks) in picks.groupBy { it.stack.binderKey }) {
                val i = barIndex[binderKey] ?: Int.MAX_VALUE
                val b = binderByKey[binderKey]
                val lines = binderPicks.groupBy { Triple(it.stack.scryfallId, it.stack.foil, it.stack.etched) }.map { (printing, list) ->
                    val (scryfallId, foil, etched) = printing
                    val s = list.first().stack
                    FindLine(
                        lineKey = lineKey(s.name, scryfallId, foil, etched, binderKey),
                        name = s.name,
                        copies = list.sumOf { it.copies },
                        set = s.set,
                        number = s.number,
                        foil = foil,
                        etched = etched,
                        wantedPrinting = list.any { it.wanted },
                    )
                }.sortedWith(linesOrder)
                val old = groups[i]
                groups[i] = FindGroup(
                    binderKey = binderKey,
                    binderName = old?.binderName ?: b?.name ?: binderPicks.first().stack.binderName,
                    lines = (old?.lines.orEmpty() + lines).sortedWith(linesOrder),
                )
            }

            // What's missing: the other decks' copies first, then what isn't owned at all.
            val rest = qty - take
            val inDecks = minOf(rest, maxOf(0, owned - take))
            if (inDecks > 0) {
                val names = input.decks.filter { it.id != input.fromDeck && (it.cards[key] ?: 0) > 0 }
                    .map { it.name }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
                onlyInDecks += FindInDeck(name, inDecks, names)
                inDecksTotal += inDecks
            }
            if (rest - inDecks > 0) {
                notOwned += FindMissing(name, rest - inDecks)
                notOwnedTotal += rest - inDecks
            }
        }

        return FindResult(
            groups = groups.values.toList(),
            onlyInDecks = onlyInDecks,
            notOwned = notOwned,
            total = FindTotal(wantedTotal, found, inDecksTotal, notOwnedTotal),
        )
    }
}

/**
 * Builds [FindInput] from the collection, the binders and the decks, and runs [FindCards.plan]:
 * for a pasted list (read by [DeckText]) or a deck's own cards (not its maybeboard).
 */
class CardFinder(private val db: AppDatabase, private val settings: Settings) {
    /** Builds the input from the collection, the binders and the decks and runs [FindCards.plan]. */
    suspend fun plan(wanted: List<FindWanted>, fromDeck: Long? = null, skipBasics: Boolean = true): FindResult {
        val bar = binders()
        return FindCards.plan(
            FindInput(
                wanted = wanted,
                stacks = stacks(bar),
                binders = bar,
                decks = decks(fromDeck),
                fromDeck = fromDeck,
                skipBasics = skipBasics,
            ),
        )
    }

    /** The cards of a pasted list (its maybeboard and sideboard left out: the deck itself is what's needed). */
    suspend fun find(text: String, skipBasics: Boolean = true): FindResult {
        val wanted = DeckText.parse(text).lines.filter { it.board != DeckText.Board.MAYBE }
            .map { FindWanted(it.name, it.quantity, null) }
        return plan(wanted, skipBasics = skipBasics)
    }

    /**
     * A deck's own cards (commander and main deck; not the maybeboard). Null when there's no such deck. A retired
     * deck's cards are used too: it's being looked up, so its cards aren't spare.
     */
    suspend fun find(deckId: Long, skipBasics: Boolean = true): FindResult? {
        val deck = db.deckDao().get(deckId) ?: return null
        // One line per card, in the deck's own order; the first line says the printing it needs.
        val wanted = LinkedHashMap<String, FindWanted>()
        for (c in db.deckDao().cards(deckId)) {
            val key = FindCards.nameKey(c.card.name)
            val prev = wanted[key]
            wanted[key] = FindWanted(c.card.name, (prev?.qty ?: 0) + c.quantity, prev?.scryfallId ?: c.card.scryfallId)
        }
        return plan(wanted.values.toList(), fromDeck = deck.archidektId, skipBasics = skipBasics)
    }

    /**
     * Every stack of the collection with its binder's name (Unsorted included). [binders] is the binder bar
     * ([binders] gives it); without it, it's worked out here.
     */
    suspend fun stacks(binders: List<FindBinder>? = null): List<FindStack> {
        val inBar = binders ?: binders()
        val binderByKey = inBar.associateBy { it.key }
        val rows = db.collectionDao().all()
        val allBinders = db.binderDao().all()
        val keyOf = HashMap<Long, String>()
        for (b in allBinders) keyOf[b.id] = BinderOrder.key(b)
        keyOf[Binder.UNSORTED] = BinderOrder.UNSORTED
        return rows.map { item ->
            val binderKey = keyOf[item.binderId] ?: BinderOrder.UNSORTED
            FindStack(
                name = item.card.name,
                scryfallId = item.card.scryfallId,
                set = item.card.setCode,
                number = item.card.collectorNumber,
                foil = item.foil,
                etched = item.etched,
                binderKey = binderKey,
                binderName = binderByKey[binderKey]?.name ?: Binder.UNSORTED_NAME,
                copies = item.quantity,
            )
        }
    }

    /** The binders in the binder bar's order, with the trade binder and deck binders marked. */
    suspend fun binders(): List<FindBinder> {
        val all = db.binderDao().all()
        // The same trade binder as TradeBinder.binder(): the remembered one, else one called "Trade binder".
        val tradeBinderId = settings.tradeBinderId.takeIf { it > 0 } ?: all.firstOrNull { it.name.equals(TradeBinder.NAME, true) }?.id ?: 0L
        // A binder named after a deck holds that deck's cards (a retired deck's binder is just a binder).
        val deckNames = db.deckDao().all().filter { !it.retired }.map { it.name.lowercase() }.toSet()
        return BinderOrder.arrange(all, settings.binderOrder.value, tradeBinderId).mapNotNull { key ->
            when {
                // Not storage: what's on the wishlist isn't owned.
                key == BinderOrder.WISHLIST -> null
                key == BinderOrder.UNSORTED -> FindBinder(key, Binder.UNSORTED_NAME)
                else -> BinderOrder.binderOf(key, all)?.let { b ->
                    FindBinder(key, b.name, isDeckBinder = b.name.lowercase() in deckNames, isTradeBinder = b.id == tradeBinderId)
                }
            }
        }
    }

    /**
     * What each deck uses (not the maybeboard), by nameKey. Retired decks are left out, like [DeckDao.cardCounts]
     * does: their cards are spare. The deck being looked up itself still counts (see [find]).
     */
    suspend fun decks(fromDeck: Long? = null): List<FindDeck> = db.deckDao().all().filter { !it.retired || it.archidektId == fromDeck }.map { deck ->
        val cards = HashMap<String, Int>()
        for (c in db.deckDao().cards(deck.archidektId)) {
            val key = FindCards.nameKey(c.card.name)
            cards[key] = (cards[key] ?: 0) + c.quantity
        }
        FindDeck(deck.archidektId, deck.name, cards)
    }
}
