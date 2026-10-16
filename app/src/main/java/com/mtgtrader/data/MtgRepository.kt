package com.mtgtrader.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where a card picked in search or the scanner goes. */
sealed interface CardTarget {
    data class TradeSide(val tradeId: Long, val side: String) : CardTarget
    data class Collection(val binderId: Long = Binder.UNSORTED) : CardTarget
    /** The Scan tab's list of cards waiting for a decision. */
    data object Scans : CardTarget
    /** The wishlist (cards you want). Since 1.16. */
    data object Wishlist : CardTarget
    data class ReplaceTradeItem(val itemId: Long) : CardTarget
    data class ReplaceCollectionItem(val itemId: Long) : CardTarget
    data class ReplaceScan(val itemId: Long) : CardTarget

    val isReplace get() = this is ReplaceTradeItem || this is ReplaceCollectionItem || this is ReplaceScan

    fun encode(): String = when (this) {
        is TradeSide -> "trade:$tradeId:$side"
        is Collection -> "collection:$binderId"
        Scans -> "scans"
        Wishlist -> "wishlist"
        is ReplaceTradeItem -> "rtrade:$itemId"
        is ReplaceCollectionItem -> "rcoll:$itemId"
        is ReplaceScan -> "rscan:$itemId"
    }

    companion object {
        fun decode(s: String): CardTarget {
            val p = s.split(':')
            return when (p[0]) {
                "trade" -> TradeSide(p[1].toLong(), p[2])
                "scans" -> Scans
                "wishlist" -> Wishlist
                "rtrade" -> ReplaceTradeItem(p[1].toLong())
                "rcoll" -> ReplaceCollectionItem(p[1].toLong())
                "rscan" -> ReplaceScan(p[1].toLong())
                else -> Collection(p.getOrNull(1)?.toLongOrNull() ?: Binder.UNSORTED)
            }
        }
    }
}

enum class AddedTo { TRADE, COLLECTION, SCANS, WISHLIST }

/** Identifies one added copy so the scanner can undo it. */
data class AddResult(val itemId: Long, val addedTo: AddedTo) {
    /** Where to send "choose a different printing" for this copy. */
    val replaceTarget: CardTarget
        get() = when (addedTo) {
            AddedTo.TRADE -> CardTarget.ReplaceTradeItem(itemId)
            AddedTo.COLLECTION -> CardTarget.ReplaceCollectionItem(itemId)
            AddedTo.SCANS -> CardTarget.ReplaceScan(itemId)
            AddedTo.WISHLIST -> CardTarget.Wishlist
        }
}

/** Reverses a bulk action, e.g. from an Undo button. */
typealias UndoAction = suspend () -> Unit

data class ImportResult(val imported: Int, val notFound: Int)

/** A running CSV import: cards looked up on Scryfall so far, of [total] (0 while the file is read). */
data class CsvImportProgress(val done: Int, val total: Int)

class MtgRepository(
    private val db: AppDatabase,
    val scryfall: ScryfallApi,
    val prices: PriceGuideRepository,
    private val scope: CoroutineScope,
) {
    private val trades = db.tradeDao()
    private val coll = db.collectionDao()
    private val binders = db.binderDao()
    private val scans = db.scanDao()
    private val wishlist = db.wishlistDao()

    // ---- trades --------------------------------------------------------------------------

    suspend fun newTrade(): Long = trades.insert(Trade())

    suspend fun deleteEmptyDrafts() = trades.deleteEmptyDrafts()

    suspend fun deleteTrade(id: Long) = trades.delete(id)

    suspend fun setPartner(id: Long, partner: String) = trades.setPartner(id, partner)

    suspend fun setNotes(id: Long, notes: String) = trades.setNotes(id, notes)

    suspend fun snapshot(card: CardRef, foil: Boolean): PriceSet {
        val guide = prices.priceFor(card.productFor(foil), foil)
        return if (guide?.best(PriceType.TREND) != null) guide else PriceSet(trend = card.fallback(foil))
    }

    suspend fun add(target: CardTarget, card: CardRef, finish: Finish, language: String = "EN", exactPrinting: Boolean = true): AddResult? =
        when (target) {
            is CardTarget.TradeSide -> addToTrade(target.tradeId, target.side, card, finish, language)
            is CardTarget.Collection -> addToCollection(card, finish, "NM", language, 1, target.binderId)
            CardTarget.Scans -> addToScans(card, finish, language, exactPrinting)
            CardTarget.Wishlist -> addToWishlist(card, finish)
            else -> null
        }

    suspend fun addToTrade(
        tradeId: Long, side: String, card: CardRef, finish: Finish, language: String = "EN", qty: Int = 1, condition: String = "NM",
    ): AddResult {
        val existing = trades.findSame(tradeId, side, card.scryfallId, finish.foil, finish.etched, language)
        if (existing != null) {
            trades.updateItem(existing.copy(quantity = existing.quantity + qty))
            return AddResult(existing.id, AddedTo.TRADE)
        }
        val id = trades.insertItem(
            TradeItem(
                tradeId = tradeId, side = side, card = card, foil = finish.foil, etched = finish.etched, language = language,
                condition = condition, quantity = qty, prices = snapshot(card, finish.foil),
            )
        )
        return AddResult(id, AddedTo.TRADE)
    }

    suspend fun undoAdd(r: AddResult) {
        when (r.addedTo) {
            AddedTo.TRADE -> {
                val item = trades.item(r.itemId) ?: return
                if (item.quantity > 1) trades.updateItem(item.copy(quantity = item.quantity - 1)) else trades.deleteItem(item.id)
            }
            AddedTo.COLLECTION -> {
                val item = coll.byId(r.itemId) ?: return
                if (item.quantity > 1) coll.update(item.copy(quantity = item.quantity - 1)) else coll.deleteById(item.id)
            }
            AddedTo.SCANS -> {
                val item = scans.byId(r.itemId) ?: return
                if (item.quantity > 1) scans.update(item.copy(quantity = item.quantity - 1)) else scans.delete(item.id)
            }
            AddedTo.WISHLIST -> {
                val item = wishlist.byId(r.itemId) ?: return
                if (item.quantity > 1) wishlist.update(item.copy(quantity = item.quantity - 1)) else wishlist.delete(item.id)
            }
        }
    }

    // ---- wishlist ------------------------------------------------------------------------

    /** Adds [qty] wanted copies: to the entry for that card name (or that exact printing, if the entry wants only it). */
    suspend fun addToWishlist(card: CardRef, finish: Finish = Finish.NONFOIL, qty: Int = 1): AddResult {
        val same = wishlist.byName(card.name).firstOrNull { it.anyPrinting || (it.card.scryfallId == card.scryfallId && it.foil == finish.foil) }
        if (same != null) {
            wishlist.update(same.copy(quantity = same.quantity + qty))
            return AddResult(same.id, AddedTo.WISHLIST)
        }
        val item = WishlistItem(card = card, foil = finish.foil, quantity = qty)
        return AddResult(wishlist.insert(item.copy(ownedAtAdd = ownedFor(item, coll.all()))), AddedTo.WISHLIST)
    }

    /** Adds a card by name only, while offline (any printing will do); see [PendingCard]. Since 1.34. */
    suspend fun addPendingToWishlist(name: String): AddResult = addToWishlist(PendingCard.ref(name))

    /**
     * Fills in the wishlist cards added offline: Scryfall's usual printing of each name, with its
     * picture and prices. Cards Scryfall can't find stay as they are. Returns how many were filled in.
     */
    suspend fun completePendingWishlist(): Int {
        var done = 0
        for (w in wishlist.pending()) {
            val card = runCatching { scryfall.fuzzy(w.card.name) }.getOrNull() ?: continue
            val ref = card.toRef()
            db.withTransaction {
                val cur = wishlist.byId(w.id) ?: return@withTransaction
                // Owned copies were counted by the name typed offline; count again by Scryfall's name if it differs.
                val owned = if (ref.name == cur.card.name) cur.ownedAtAdd else ownedFor(cur.copy(card = ref), coll.all())
                wishlist.update(cur.copy(card = ref, ownedAtAdd = owned))
            }
            done++
        }
        return done
    }

    /** Adds several cards (e.g. a deck's missing ones) to the wishlist. Returns the copies added and an undo. */
    suspend fun addManyToWishlist(cards: List<Pair<DeckCard, Int>>): Pair<Int, UndoAction> {
        val before = wishlist.all()
        db.withTransaction { for ((c, n) in cards) addToWishlist(c.card, c.finish, n) }
        val undo: UndoAction = {
            db.withTransaction {
                val keep = before.associateBy { it.id }
                for (w in wishlist.all()) {
                    val old = keep[w.id]
                    if (old == null) wishlist.delete(w.id) else if (old.quantity != w.quantity) wishlist.update(w.copy(quantity = old.quantity))
                }
            }
        }
        return cards.sumOf { it.second } to undo
    }

    /** Copies of the entry's card in [all]: any printing by name, or only that printing and finish. */
    private fun ownedFor(w: WishlistItem, all: List<CollectionItem>) =
        if (w.anyPrinting) all.filter { it.card.name.equals(w.card.name, true) }.sumOf { it.quantity }
        else all.filter { it.card.scryfallId == w.card.scryfallId && it.foil == w.foil }.sumOf { it.quantity }

    suspend fun updateWishlistItem(item: WishlistItem) = wishlist.update(item)

    /**
     * Ticks or unticks "Ordered, on its way" straight from the list ([orderedAt] null unticks). Read
     * fresh, so it keeps the row's current updatedAt and the sync trigger stamps the change. Since 1.36.
     */
    suspend fun setWishlistOrdered(id: Long, orderedAt: Long?) {
        val w = wishlist.byId(id) ?: return
        if (w.orderedAt != orderedAt) wishlist.update(w.copy(orderedAt = orderedAt))
    }

    suspend fun deleteWishlistItem(id: Long) = wishlist.delete(id)

    suspend fun restoreWishlistItem(item: WishlistItem) {
        if (wishlist.byId(item.id) == null) wishlist.insert(item)
    }

    /** Each wishlist entry against the collection (any printing, or that printing and finish). */
    fun wishlistOwned(items: List<WishlistItem>, collection: List<CollectionItem>): Map<Long, WishlistOwned> =
        items.associate { w ->
            val owned = ownedFor(w, collection)
            w.id to WishlistOwned(owned, (owned - w.ownedAtAdd).coerceAtLeast(0))
        }

    /**
     * An undo for a change to the wishlist: the rows in [before] come back as they were (keeping each
     * row's current updatedAt, so the sync trigger stamps the change), and rows made since ([created]) go.
     */
    private fun wishUndo(before: List<WishlistItem>, created: List<Long> = emptyList()): UndoAction = {
        db.withTransaction {
            created.forEach { wishlist.delete(it) }
            val now = wishlist.byIds(before.map { it.id }).associateBy { it.id }
            for (w in before) {
                val cur = now[w.id]
                if (cur == null) wishlist.insert(w) else if (cur != w) wishlist.update(w.copy(updatedAt = cur.updatedAt))
            }
        }
    }

    /**
     * Ticks (or with null unticks) "Ordered" on several wishlist cards; received ones are left as they
     * are. Returns how many changed and an undo. Since 1.38.
     */
    suspend fun setWishlistOrdered(ids: Collection<Long>, orderedAt: Long?): Pair<Int, UndoAction> {
        val before = wishlist.byIds(ids.toList()).filter { it.receivedAt == null && (it.orderedAt == null) != (orderedAt == null) }
        db.withTransaction { before.forEach { wishlist.update(it.copy(orderedAt = orderedAt)) } }
        return before.size to wishUndo(before)
    }

    /**
     * Marks wishlist cards as received ([at]): they move to the Received section as a record. With
     * [toCollection], each card's copies are also added to the collection (Unsorted, near mint, its own
     * printing and finish); cards added offline, without a printing yet, are only marked. Returns how
     * many cards were marked and an undo (which takes the added copies out again too). Since 1.38.
     */
    suspend fun receiveWishlist(ids: Collection<Long>, toCollection: Boolean, at: Long = System.currentTimeMillis()): Pair<Int, UndoAction> {
        val before = wishlist.byIds(ids.toList()).filter { it.receivedAt == null }
        val added = mutableListOf<Pair<Long, Int>>()
        db.withTransaction {
            for (w in before) {
                // Copies added here are marked so (since 1.39): moving the card to the collection later won't add them again.
                val adding = toCollection && !w.card.isPending && w.receivedFrom == null
                wishlist.update(w.copy(receivedAt = at, receivedFrom = if (adding) WishOrders.ADDED else w.receivedFrom))
                if (adding) {
                    val finish = w.card.resolveFinish(if (w.foil) Finish.FOIL else Finish.NONFOIL)
                    added += addToCollection(w.card, finish, "NM", "EN", w.quantity).itemId to w.quantity
                }
            }
        }
        val undoWish = wishUndo(before)
        val undo: UndoAction = {
            undoWish()
            db.withTransaction {
                for ((id, n) in added) {
                    val item = coll.byId(id) ?: continue
                    if (item.quantity > n) coll.update(item.copy(quantity = item.quantity - n)) else coll.deleteById(item.id)
                }
            }
        }
        return before.size to undo
    }

    /**
     * Moves a received wishlist card into the collection (swiped right in Received, since 1.39): [qty]
     * copies of [card] in [finish], [condition] and [language] go into [binderId] (joining the same
     * version's stack there, whose purchase price becomes the average with [price]), and the card leaves
     * the wishlist. Returns an undo for both.
     */
    suspend fun wishToCollection(
        wishId: Long, card: CardRef, finish: Finish, condition: String, language: String, qty: Int, binderId: Long, price: Double?,
    ): UndoAction {
        val before = wishlist.byIds(listOf(wishId))
        val undoCollection = collectionChange(setOf(card.scryfallId)) {
            addBought(card, finish, condition, language, qty, binderId, Marks.NONE, price)
            wishlist.deleteMany(listOf(wishId))
        }
        val undoWish = wishUndo(before)
        return { undoCollection(); undoWish() }
    }

    /** The wishlist cards an order's copies match (see [WishOrders.match]). Since 1.39. */
    suspend fun wishMatches(bought: List<WishOrders.Bought>): List<WishOrders.Match> = WishOrders.match(wishlist.all(), bought)

    /**
     * After an order went into the collection: each matched wishlist card is marked as received from
     * that order ([label], e.g. "CardTrader order #123"; it then isn't added to the collection again),
     * taken off the wishlist, or left. Of a card bought fewer times than wanted, the bought copies become
     * an entry of their own and the rest stays as it was. Returns the cards changed and an undo. Since 1.39.
     */
    suspend fun applyOrderToWishlist(choices: List<Pair<WishOrders.Match, WishOrders.Choice>>, label: String, at: Long = System.currentTimeMillis()): Pair<Int, UndoAction> {
        val todo = choices.filter { it.second != WishOrders.Choice.LEAVE }
        val before = wishlist.byIds(todo.map { it.first.item.id })
        val byId = before.associateBy { it.id }
        val created = mutableListOf<Long>()
        db.withTransaction {
            for ((m, choice) in todo) {
                val w = byId[m.item.id] ?: continue
                val n = m.quantity.coerceAtMost(w.quantity)
                val all = n >= w.quantity
                // The copies bought are in the collection now: they no longer count as "got since adding" for what's still wanted.
                val rest = w.copy(quantity = w.quantity - n, ownedAtAdd = w.ownedAtAdd + n)
                when (choice) {
                    WishOrders.Choice.RECEIVED ->
                        if (all) wishlist.update(w.copy(receivedAt = w.receivedAt ?: at, receivedFrom = label))
                        else {
                            wishlist.update(rest)
                            created += wishlist.insert(w.copy(id = 0, uid = null, updatedAt = 0, quantity = n, receivedAt = at, receivedFrom = label))
                        }
                    WishOrders.Choice.REMOVE -> if (all) wishlist.delete(w.id) else wishlist.update(rest)
                    WishOrders.Choice.LEAVE -> {}
                }
            }
        }
        return todo.size to wishUndo(before, created)
    }

    /** Every card ticked "Ordered" and not received yet. Since 1.38. */
    suspend fun orderedWishlistIds(): List<Long> = wishlist.wanted().filter { it.orderedAt != null }.map { it.id }

    /** Puts received cards back on the wishlist (wanted again). Since 1.38. */
    suspend fun unreceiveWishlist(ids: Collection<Long>): Pair<Int, UndoAction> {
        val before = wishlist.byIds(ids.toList()).filter { it.receivedAt != null }
        db.withTransaction { before.forEach { wishlist.update(it.copy(receivedAt = null)) } }
        return before.size to wishUndo(before)
    }

    /** Deletes wishlist cards. Returns how many and an undo. Since 1.38. */
    suspend fun deleteWishlistItems(ids: Collection<Long>): Pair<Int, UndoAction> {
        val before = wishlist.byIds(ids.toList())
        wishlist.deleteMany(before.map { it.id })
        return before.size to wishUndo(before)
    }

    /**
     * The wanted cards you now own more copies of than when you added them (see [wishlistOwned]), with
     * how many of the wanted copies that covers. Since 1.38 (before, they were simply removed).
     */
    suspend fun ownedSinceAdding(): List<Pair<WishlistItem, Int>> {
        val wanted = wishlist.wanted()
        val owned = wishlistOwned(wanted, coll.all())
        return wanted.mapNotNull { w -> owned[w.id]?.gotSince?.takeIf { it > 0 }?.let { w to minOf(it, w.quantity) } }
    }

    /**
     * Marks the cards you now own as received ([ownedSinceAdding]); they're already in the collection,
     * so nothing is added there. Of a card you got only some of, those copies become a received entry
     * of their own and the rest stays wanted. Returns the copies marked and an undo. Since 1.38.
     */
    suspend fun receiveOwned(at: Long = System.currentTimeMillis()): Pair<Int, UndoAction> {
        val got = ownedSinceAdding()
        val created = mutableListOf<Long>()
        db.withTransaction {
            for ((w, n) in got) {
                if (n >= w.quantity) wishlist.update(w.copy(receivedAt = at))
                else {
                    wishlist.update(w.copy(quantity = w.quantity - n, ownedAtAdd = w.ownedAtAdd + n))
                    created += wishlist.insert(w.copy(id = 0, uid = null, updatedAt = 0, quantity = n, receivedAt = at))
                }
            }
        }
        return got.sumOf { it.second } to wishUndo(got.map { it.first }, created)
    }

    /** Clears received cards: all of them, or those received before [before]. Returns how many and an undo. Since 1.38. */
    suspend fun clearReceived(before: Long = Long.MAX_VALUE): Pair<Int, UndoAction> {
        val gone = wishlist.receivedBefore(before)
        if (gone.isEmpty()) return 0 to {}
        wishlist.deleteMany(gone.map { it.id })
        return gone.size to wishUndo(gone)
    }

    /** Clears received cards older than [clear] allows (Settings); nothing when they're kept. Since 1.38. */
    suspend fun autoClearReceived(clear: ReceivedClear, now: Long = System.currentTimeMillis()): Int =
        clear.cutoff(now)?.let { clearReceived(it).first } ?: 0

    /** Wishlist card names (lower case) with how many copies are wanted, for marking cards in trades. */
    suspend fun wishlistNames(): Map<String, Int> =
        wishlist.wanted().groupBy { it.card.name.lowercase() }.mapValues { (_, v) -> v.sumOf { it.quantity } }

    suspend fun updateTradeItem(updated: TradeItem) {
        val old = trades.item(updated.id) ?: return
        val fixed = if (old.foil != updated.foil) updated.copy(prices = snapshot(updated.card, updated.foil)) else updated
        trades.updateItem(fixed)
    }

    suspend fun deleteTradeItem(id: Long) = trades.deleteItem(id)

    /** Undo for [deleteTradeItem]: puts the item back exactly as it was (if its trade still exists). */
    suspend fun restoreTradeItem(item: TradeItem) {
        if (trades.get(item.tradeId) != null && trades.item(item.id) == null) trades.insertItem(item)
    }

    /** Undo for [deleteTrade]: puts the trade and all its cards back, including whether it was applied. */
    suspend fun restoreTrade(t: TradeWithItems) = db.withTransaction {
        if (trades.get(t.trade.id) != null) return@withTransaction
        trades.insert(t.trade)
        t.items.forEach { trades.insertItem(it) }
    }

    suspend fun refreshTradePrices(tradeId: Long) {
        val t = trades.get(tradeId) ?: return
        for (item in t.items) trades.updateItem(item.copy(prices = snapshot(item.card, item.foil)))
    }

    /**
     * Changes the printing of a copy the scanner just added: that copy is taken back and the
     * chosen printing added instead (in the same finish when it has it), so other copies of the
     * old printing are left alone. Returns what was added now.
     */
    suspend fun changeAddedPrinting(r: AddResult, target: CardTarget, card: CardRef, finish: Finish, language: String): AddResult? =
        db.withTransaction {
            undoAdd(r)
            add(target, card, card.resolveFinish(finish), language, exactPrinting = true)
        }

    /** Swaps an item's printing (e.g. after choosing the right set in search). */
    suspend fun replacePrinting(target: CardTarget, card: CardRef) {
        when (target) {
            is CardTarget.ReplaceTradeItem -> {
                val item = trades.item(target.itemId) ?: return
                val f = card.resolveFinish(item.finish)
                trades.updateItem(item.copy(card = card, foil = f.foil, etched = f.etched, prices = snapshot(card, f.foil)))
            }
            is CardTarget.ReplaceCollectionItem -> {
                val item = coll.byId(target.itemId) ?: return
                val f = card.resolveFinish(item.finish)
                updateCollectionItem(item.copy(card = card, foil = f.foil, etched = f.etched))
            }
            is CardTarget.ReplaceScan -> {
                val item = scans.byId(target.itemId) ?: return
                val f = card.resolveFinish(item.finish)
                updateScan(item.copy(card = card, foil = f.foil, etched = f.etched, exactPrinting = true))
            }
            else -> {}
        }
    }

    /**
     * Adds received cards to the collection (in [binderId]) and removes given cards from it, taking
     * them from Unsorted before other binders. Returns how many given copies weren't found.
     */
    suspend fun applyTrade(tradeId: Long, binderId: Long = Binder.UNSORTED): Int = db.withTransaction {
        val t = trades.get(tradeId) ?: return@withTransaction 0
        if (t.trade.applied) return@withTransaction 0
        var missing = 0
        for (item in t.items) {
            if (item.side == Side.GET) {
                addToCollection(item.card, item.finish, item.condition, item.language, item.quantity, binderId)
                trades.updateItem(item.copy(appliedDelta = item.quantity))
            } else {
                val r = removeFromCollection(item.card.scryfallId, item.finish, item.condition, item.language, item.quantity)
                missing += item.quantity - r.removed
                trades.updateItem(item.copy(appliedDelta = -r.removed, appliedBinderId = r.fromBinder))
            }
        }
        trades.update(t.trade.copy(applied = true, appliedAt = System.currentTimeMillis(), binderId = binderId))
        missing
    }

    /** Reverses what [applyTrade] did, so the trade can be edited again. */
    suspend fun revertTrade(tradeId: Long) = db.withTransaction {
        val t = trades.get(tradeId) ?: return@withTransaction
        if (!t.trade.applied) return@withTransaction
        for (item in t.items) {
            when {
                item.appliedDelta > 0 -> removeFromCollection(
                    item.card.scryfallId, item.finish, item.condition, item.language, item.appliedDelta, prefer = t.trade.binderId,
                )
                item.appliedDelta < 0 -> addToCollection(
                    item.card, item.finish, item.condition, item.language, -item.appliedDelta, existingBinder(item.appliedBinderId),
                )
            }
            trades.updateItem(item.copy(appliedDelta = 0, appliedBinderId = Binder.UNSORTED))
        }
        trades.update(t.trade.copy(applied = false, appliedAt = null))
    }

    // ---- collection ----------------------------------------------------------------------

    suspend fun addToCollection(
        card: CardRef, finish: Finish, condition: String, language: String, qty: Int, binderId: Long = Binder.UNSORTED,
        marks: Marks = Marks.NONE,
    ): AddResult {
        val existing = coll.find(card.scryfallId, finish.foil, finish.etched, condition, language, binderId, marks.signed, marks.altered)
        if (existing != null) {
            coll.update(existing.copy(quantity = existing.quantity + qty, card = card))
            return AddResult(existing.id, AddedTo.COLLECTION)
        }
        val id = coll.insert(
            CollectionItem(
                card = card, foil = finish.foil, etched = finish.etched, condition = condition, language = language,
                quantity = qty, binderId = binderId, signed = marks.signed, altered = marks.altered,
            )
        )
        return AddResult(id, AddedTo.COLLECTION)
    }

    /** Adds [item] as a new stack (notes and purchase price included), or to the identical stack if there is one. */
    private suspend fun addStack(item: CollectionItem) {
        val clash = coll.sameStack(item)
        if (clash != null) coll.update(clash.copy(quantity = clash.quantity + item.quantity))
        else coll.insert(item.copy(id = 0, uid = null, updatedAt = 0))
    }

    private data class Removal(val removed: Int, val fromBinder: Long)

    /**
     * Removes up to [qty] copies of a printing, taking stacks with the exact condition and language
     * first, and within those the [prefer] binder first. Signed and altered copies go last: a trade
     * line doesn't say whether the copy given was one.
     */
    private suspend fun removeFromCollection(
        sid: String, finish: Finish, condition: String, language: String, qty: Int, prefer: Long = Binder.UNSORTED,
    ): Removal {
        var remaining = qty
        var from: Long? = null
        val candidates = coll.findAny(sid, finish.foil, finish.etched).sortedWith(
            compareBy({ if (it.marks.any) 1 else 0 }, { if (it.condition == condition && it.language == language) 0 else 1 }, { if (it.binderId == prefer) 0 else 1 })
        )
        for (c in candidates) {
            if (remaining == 0) break
            val take = minOf(remaining, c.quantity)
            if (c.quantity - take <= 0) coll.deleteById(c.id) else coll.update(c.copy(quantity = c.quantity - take))
            remaining -= take
            if (from == null) from = c.binderId
        }
        return Removal(qty - remaining, from ?: prefer)
    }

    /** [id] if that binder still exists, else Unsorted. */
    private suspend fun existingBinder(id: Long): Long =
        if (id == Binder.UNSORTED || binders.get(id) != null) id else Binder.UNSORTED

    /** Saves an edited row, merging it into an existing row if it now has the same card/finish/condition/language/binder/marks. */
    suspend fun updateCollectionItem(updated: CollectionItem) = db.withTransaction {
        val clash = coll.sameStack(updated)
        if (clash != null && clash.id != updated.id) {
            coll.update(clash.copy(quantity = clash.quantity + updated.quantity))
            coll.deleteById(updated.id)
        } else {
            coll.update(updated)
        }
    }

    /**
     * Saves the card editor: the edited stack, of which [move] copies go to [binderId] when that
     * differs from the stack's binder (all of them if [move] is the whole stack).
     *
     * With [keepMarks] > 0, only part of the stack got [updated]'s signed/altered marks: that many
     * copies keep [previous] marks and stay a stack of their own (with the other changes, and in
     * [binderId] too, as the whole stack moves then). Since 1.27.
     */
    suspend fun saveCollectionEdit(
        updated: CollectionItem, binderId: Long, move: Int, keepMarks: Int = 0, previous: Marks = Marks.NONE,
    ) = db.withTransaction {
        val keep = keepMarks.coerceIn(0, updated.quantity - 1)
        if (keep > 0 && previous != updated.marks) {
            val moved = updated.copy(binderId = binderId)
            // The existing stack keeps its marks (and sync id); the marked copies become a new stack.
            updateCollectionItem(moved.copy(quantity = keep, signed = previous.signed, altered = previous.altered))
            addStack(moved.copy(quantity = updated.quantity - keep))
            return@withTransaction
        }
        val n = move.coerceIn(0, updated.quantity)
        when {
            binderId == updated.binderId || n == 0 -> updateCollectionItem(updated)
            n >= updated.quantity -> updateCollectionItem(updated.copy(binderId = binderId))
            else -> {
                updateCollectionItem(updated.copy(quantity = updated.quantity - n))
                addToCollection(updated.card, updated.finish, updated.condition, updated.language, n, binderId, updated.marks)
            }
        }
    }

    suspend fun deleteCollectionItem(id: Long) = coll.deleteById(id)

    /** Undo for [deleteCollectionItem]; merges into a matching row if the same card was added again meanwhile. */
    suspend fun restoreCollectionItem(item: CollectionItem) = db.withTransaction {
        val clash = coll.sameStack(item)
        when {
            clash != null -> coll.update(clash.copy(quantity = clash.quantity + item.quantity))
            coll.byId(item.id) == null -> coll.insert(item)
            else -> coll.insert(item.copy(id = 0))
        }
    }

    /**
     * Runs [change] on collection stacks of the printings [sids] and returns an undo that puts every
     * stack of those printings back as it was: stacks merged away come back, stacks made go. Since 1.38.
     */
    private suspend fun collectionChange(sids: Set<String>, change: suspend () -> Unit): UndoAction {
        val before = db.withTransaction {
            val rows = coll.all().filter { it.card.scryfallId in sids }
            change()
            rows
        }
        val ids = before.map { it.id }.toSet()
        return {
            db.withTransaction {
                for (now in coll.all().filter { it.card.scryfallId in sids && it.id !in ids }) coll.deleteById(now.id)
                for (b in before) {
                    val cur = coll.byId(b.id)
                    if (cur == null) coll.insert(b) else if (cur != b) coll.update(b.copy(updatedAt = cur.updatedAt))
                }
            }
        }
    }

    private suspend fun stacks(ids: Collection<Long>) = ids.mapNotNull { coll.byId(it) }

    /** Moves whole stacks to [binderId] (merging with the same card there). Returns the copies moved and an undo. Since 1.38. */
    suspend fun moveStacks(ids: Collection<Long>, binderId: Long): Pair<Int, UndoAction> {
        val items = stacks(ids).filter { it.binderId != binderId }
        val undo = collectionChange(items.map { it.card.scryfallId }.toSet()) {
            items.forEach { updateCollectionItem(it.copy(binderId = binderId)) }
        }
        return items.sumOf { it.quantity } to undo
    }

    /** Moves [n] copies of one stack to [binderId] (the rest stays). Since 1.38. */
    suspend fun moveCopies(id: Long, binderId: Long, n: Int): Pair<Int, UndoAction> {
        val item = coll.byId(id) ?: return 0 to {}
        if (item.binderId == binderId || n <= 0) return 0 to {}
        val undo = collectionChange(setOf(item.card.scryfallId)) { saveCollectionEdit(item, binderId, n) }
        return minOf(n, item.quantity) to undo
    }

    /**
     * Sets condition, language and/or purchase price (null leaves it as it is) on several stacks,
     * merging stacks that become the same. Returns the stacks changed and an undo. Since 1.38.
     */
    suspend fun editStacks(ids: Collection<Long>, condition: String?, language: String?, purchasePrice: Double?): Pair<Int, UndoAction> {
        val items = stacks(ids)
        val changed = items.map { it to it.copy(condition = condition ?: it.condition, language = language ?: it.language, purchasePrice = purchasePrice ?: it.purchasePrice) }
            .filter { (a, b) -> a != b }
        val undo = collectionChange(changed.map { it.first.card.scryfallId }.toSet()) {
            // Read again each time: an earlier one may have merged into this stack.
            for ((a, b) in changed) coll.byId(a.id)?.let { cur -> updateCollectionItem(cur.copy(condition = b.condition, language = b.language, purchasePrice = b.purchasePrice)) }
        }
        return changed.size to undo
    }

    /** Called before several stacks are deleted at once (a restore point). Since 1.38. */
    var beforeBatchDelete: (suspend () -> Unit)? = null

    /** Deletes several stacks (after a restore point). Returns the copies deleted and an undo. Since 1.38. */
    suspend fun deleteStacks(ids: Collection<Long>): Pair<Int, UndoAction> {
        val items = stacks(ids)
        if (items.isEmpty()) return 0 to {}
        runCatching { beforeBatchDelete?.invoke() }
        val undo = collectionChange(items.map { it.card.scryfallId }.toSet()) { items.forEach { coll.deleteById(it.id) } }
        return items.sumOf { it.quantity } to undo
    }

    /** One copy more ([delta] 1) or less (-1; the last copy removes the stack), with an undo. Since 1.38. */
    suspend fun changeQuantity(id: Long, delta: Int): Pair<CollectionItem?, UndoAction> {
        val item = coll.byId(id) ?: return null to {}
        val undo = collectionChange(setOf(item.card.scryfallId)) {
            val n = item.quantity + delta
            if (n <= 0) coll.deleteById(item.id) else coll.update(item.copy(quantity = n))
        }
        return item to undo
    }

    /**
     * Puts stacks on the "you give" side of trade [tradeId] (a new trade when null): [copies] of each, or
     * every copy. Returns the trade, the copies added and an undo (a new trade goes again). Since 1.38.
     */
    suspend fun stacksToTrade(ids: Collection<Long>, tradeId: Long?, copies: Int? = null): Triple<Long, Int, UndoAction> {
        val items = stacks(ids)
        val made = tradeId == null
        val id = tradeId ?: newTrade()
        val before = trades.get(id)?.items.orEmpty()
        var n = 0
        db.withTransaction {
            for (i in items) {
                val qty = copies?.coerceIn(1, i.quantity) ?: i.quantity
                addToTrade(id, Side.GIVE, i.card, i.finish, i.language, qty, i.condition)
                n += qty
            }
        }
        val undo: UndoAction = {
            db.withTransaction {
                if (made) trades.delete(id) else {
                    val keep = before.associateBy { it.id }
                    for (t in trades.get(id)?.items.orEmpty()) {
                        val old = keep[t.id]
                        if (old == null) trades.deleteItem(t.id) else if (old.quantity != t.quantity) trades.updateItem(t.copy(quantity = old.quantity))
                    }
                }
            }
        }
        return Triple(id, n, undo)
    }

    /** The selected stacks as a plain list, "2 Sol Ring (C21 #263, foil)" per line. Since 1.38. */
    fun stacksText(items: List<CollectionItem>): String = items.joinToString("\n") { i ->
        val extra = listOfNotNull("${i.card.setCode.uppercase()} #${i.card.collectorNumber}", i.card.finishName(i.finish).lowercase().takeIf { i.foil })
        "${i.quantity} ${i.card.name} (${extra.joinToString(", ")})"
    }

    // ---- binders -------------------------------------------------------------------------

    /** Why [name] can't be used for a binder, or null if it can. */
    suspend fun binderNameProblem(name: String, except: Long? = null): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Enter a name"
        if (n.equals(Binder.UNSORTED_NAME, ignoreCase = true)) return "“${Binder.UNSORTED_NAME}” is reserved for cards outside binders"
        val other = binders.byName(n)
        return if (other != null && other.id != except) "There's already a binder called “${other.name}”" else null
    }

    /** Creates the binder, or returns the existing one with that name. */
    suspend fun createBinder(name: String): Long {
        val n = name.trim()
        return binders.byName(n)?.id ?: binders.insert(Binder(name = n))
    }

    /** The binder a [BinderChoice] points to, creating it first if it's new. */
    suspend fun resolve(choice: BinderChoice): Long = choice.newName?.let { createBinder(it) } ?: choice.binderId

    suspend fun renameBinder(id: Long, name: String) = binders.rename(id, name.trim())

    /**
     * Moves every card of binder [from] (or Unsorted) into [into], merging identical stacks, and
     * deletes [from] afterwards if [deleteSource] (Unsorted itself is never deleted).
     */
    suspend fun mergeBinder(from: Long, into: Long, deleteSource: Boolean) = db.withTransaction {
        if (from == into) return@withTransaction
        for (item in coll.inBinder(from)) updateCollectionItem(item.copy(binderId = into))
        if (deleteSource && from != Binder.UNSORTED) binders.delete(from)
    }

    /** Deletes a binder, moving its cards to Unsorted or, with [deleteCards], removing them from the collection. */
    suspend fun deleteBinder(id: Long, deleteCards: Boolean) = db.withTransaction {
        if (id == Binder.UNSORTED) return@withTransaction
        if (deleteCards) coll.deleteBinderCards(id) else mergeBinder(id, Binder.UNSORTED, deleteSource = false)
        binders.delete(id)
    }

    // ---- scanned cards (Scan tab) --------------------------------------------------------

    private suspend fun addToScans(card: CardRef, finish: Finish, language: String, exactPrinting: Boolean): AddResult {
        val existing = scans.find(card.scryfallId, finish.foil, finish.etched, "NM", language)
        if (existing != null) {
            scans.update(existing.copy(quantity = existing.quantity + 1, scannedAt = System.currentTimeMillis()))
            return AddResult(existing.id, AddedTo.SCANS)
        }
        val id = scans.insert(
            ScannedCard(card = card, foil = finish.foil, etched = finish.etched, language = language, exactPrinting = exactPrinting)
        )
        return AddResult(id, AddedTo.SCANS)
    }

    /** Saves an edited scan, merging it into an identical one if there is one. */
    suspend fun updateScan(updated: ScannedCard) = db.withTransaction {
        val clash = scans.find(updated.card.scryfallId, updated.foil, updated.etched, updated.condition, updated.language)
        if (clash != null && clash.id != updated.id) {
            scans.update(clash.copy(quantity = clash.quantity + updated.quantity))
            scans.delete(updated.id)
        } else {
            scans.update(updated)
        }
    }

    suspend fun deleteScan(id: Long) = scans.delete(id)

    suspend fun restoreScans(items: List<ScannedCard>) = scans.restore(items)

    /** Removes scans from the list; the returned action puts them back. */
    suspend fun discardScans(ids: List<Long>): UndoAction {
        val items = scans.byIds(ids)
        scans.deleteMany(ids)
        return { scans.restore(items) }
    }

    /** Adds the scans to the collection in [binderId]; unless [keep], they leave the scan list. */
    suspend fun scansToCollection(ids: List<Long>, binderId: Long, keep: Boolean): UndoAction {
        val items = scans.byIds(ids)
        db.withTransaction {
            items.forEach { addToCollection(it.card, it.finish, it.condition, it.language, it.quantity, binderId) }
            if (!keep) scans.deleteMany(ids)
        }
        return {
            db.withTransaction {
                items.forEach { removeFromCollection(it.card.scryfallId, it.finish, it.condition, it.language, it.quantity, prefer = binderId) }
                if (!keep) scans.restore(items)
            }
        }
    }

    /** Adds the scans to a side of a trade ([tradeId] 0 starts a new trade). Returns the trade's id. */
    suspend fun scansToTrade(ids: List<Long>, tradeId: Long, side: String, keep: Boolean): Long = db.withTransaction {
        val id = if (tradeId == 0L) newTrade() else tradeId
        val items = scans.byIds(ids)
        items.forEach { addToTrade(id, side, it.card, it.finish, it.language, it.quantity, it.condition) }
        if (!keep) scans.deleteMany(ids)
        id
    }

    suspend fun scansById(ids: List<Long>) = scans.byIds(ids)

    suspend fun deleteScans(ids: List<Long>) = scans.deleteMany(ids)

    /**
     * Fills in the special foil type and etched finish (added in app 1.2) for cards saved before,
     * and moves foil copies of etched-only printings to the etched finish. Returns false if Scryfall
     * couldn't be reached, so it can be retried later.
     */
    suspend fun backfillFinishDetails(): Boolean {
        val collItems = coll.all()
        val tradeItems = trades.allItems()
        val ids = (collItems.map { it.card.scryfallId } + tradeItems.map { it.card.scryfallId }).distinct()
        if (ids.isEmpty()) return true
        val fresh = try {
            scryfall.collection(ids.map { ScryfallApi.idIdentifier(it) }).associate { it.id to it.toRef() }
        } catch (e: Exception) {
            return false
        }
        fun upgrade(card: CardRef): CardRef? = fresh[card.scryfallId]?.let {
            card.copy(hasNonFoil = it.hasNonFoil, hasFoil = it.hasFoil, foilType = it.foilType, hasEtched = it.hasEtched)
        }
        db.withTransaction {
            for (item in tradeItems) {
                val card = upgrade(item.card) ?: continue
                val f = card.resolveFinish(item.finish)
                trades.updateItem(item.copy(card = card, foil = f.foil, etched = f.etched))
            }
            // Re-read each row: an earlier merge may have removed or grown it.
            for (id in collItems.map { it.id }) {
                val item = coll.byId(id) ?: continue
                val card = upgrade(item.card) ?: continue
                val f = card.resolveFinish(item.finish)
                updateCollectionItem(item.copy(card = card, foil = f.foil, etched = f.etched))
            }
        }
        return true
    }

    // ---- CSV -----------------------------------------------------------------------------

    /**
     * Imports a CSV exported from ManaBox (or any CSV with a "Scryfall ID", or "Set code" +
     * "Collector number", or "Name" column). Quantities are added to the existing collection.
     * Rows with a "Binder Name" go into that binder (created if needed); others into [defaultBinder].
     */
    suspend fun importCollectionCsv(
        text: String,
        defaultBinder: Long = Binder.UNSORTED,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        val rows = Csv.parse(text.removePrefix("﻿")).filter { r -> r.any { it.isNotBlank() } }
        if (rows.size < 2) return ImportResult(0, 0)
        val header = rows[0].map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val iId = col("scryfall id", "scryfall_id", "scryfallid")
        val iSet = col("set code", "set_code", "edition code", "set")
        val iNum = col("collector number", "collector_number", "card number", "number")
        val iName = col("name", "card name", "card_name")
        val iQty = col("quantity", "qty", "count", "amount")
        val iFoil = col("foil", "finish", "printing")
        val iCond = col("condition")
        val iLang = col("language", "lang")
        val iBinder = col("binder name", "binder")
        val iPaid = col("purchase price", "purchase_price", "price paid")
        val iNotes = col("notes", "note")
        val iSigned = col("signed")
        val iAltered = col("altered")

        data class Line(
            val ident: JsonObject, val key: String, val qty: Int, val finish: Finish, val cond: String, val lang: String,
            val binder: String? = null, val paid: Double? = null, val notes: String? = null, val marks: Marks = Marks.NONE,
        )

        val lines = rows.drop(1).mapNotNull { r ->
            fun v(i: Int?) = i?.let { r.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
            val qty = v(iQty)?.toIntOrNull() ?: 1
            val finishText = v(iFoil)?.lowercase()
            val finish = when {
                finishText == "etched" -> Finish.ETCHED
                finishText in setOf("foil", "true", "yes", "1") -> Finish.FOIL
                else -> Finish.NONFOIL
            }
            val cond = parseCondition(v(iCond))
            val lang = parseLanguage(v(iLang))
            val id = v(iId)
            val set = v(iSet)
            val num = v(iNum)
            val name = v(iName)
            val binder = v(iBinder)
            val paid = v(iPaid)?.replace(",", ".")?.toDoubleOrNull()?.takeIf { it > 0 }
            val notes = v(iNotes)
            val marks = Marks(signed = csvFlag(v(iSigned)), altered = csvFlag(v(iAltered)))
            when {
                id != null -> Line(ScryfallApi.idIdentifier(id), "id:$id", qty, finish, cond, lang, binder, paid, notes, marks)
                set != null && num != null ->
                    Line(ScryfallApi.setNumberIdentifier(set, num), "sn:${set.lowercase()}|${num.lowercase()}", qty, finish, cond, lang, binder, paid, notes, marks)
                name != null -> Line(ScryfallApi.nameIdentifier(name), "n:${name.lowercase()}", qty, finish, cond, lang, binder, paid, notes, marks)
                else -> null
            }
        }
        // The same printing on several rows (other condition, language or binder) is looked up once.
        val cards = scryfall.collection(lines.map { it.ident }.distinct(), onProgress)
        val byKey = HashMap<String, ScryCard>()
        for (c in cards) {
            byKey["id:${c.id}"] = c
            byKey["sn:${c.set.lowercase()}|${c.collectorNumber.lowercase()}"] = c
            byKey.putIfAbsent("n:${c.name.lowercase()}", c)
            byKey.putIfAbsent("n:${c.name.substringBefore(" // ").lowercase()}", c)
        }
        var imported = 0
        var notFound = 0
        db.withTransaction {
            val binderIds = HashMap<String, Long>()
            for (l in lines) {
                val c = byKey[l.key]
                if (c == null) {
                    notFound += l.qty
                    continue
                }
                val binder = l.binder?.takeUnless { it.equals(Binder.UNSORTED_NAME, ignoreCase = true) }
                    ?.let { name -> binderIds.getOrPut(name.lowercase()) { createBinder(name) } }
                    ?: if (l.binder != null) Binder.UNSORTED else defaultBinder
                val ref = c.toRef()
                val r = addToCollection(ref, ref.resolveFinish(l.finish), l.cond, l.lang, l.qty, binder, l.marks)
                if (l.paid != null || l.notes != null) {
                    coll.byId(r.itemId)?.let { item ->
                        coll.update(item.copy(purchasePrice = item.purchasePrice ?: l.paid, notes = item.notes ?: l.notes))
                    }
                }
                imported += l.qty
            }
        }
        return ImportResult(imported, notFound)
    }

    /** Runs before a CSV import adds anything (a backup). Since 1.21. */
    var beforeCsvImport: (suspend () -> Unit)? = null

    /** Runs before an order file changes the collection: an import, a package check, purchase prices (a backup). Since 1.26. */
    var beforeOrderImport: (suspend () -> Unit)? = null

    /**
     * Adds copies bought at [price] each. A stack that already held copies gets the average paid,
     * weighted by copies (since 1.32; before, it kept the price it had); without a price on one side,
     * every copy carries the other's.
     */
    private suspend fun addBought(card: CardRef, finish: Finish, condition: String, language: String, qty: Int, binderId: Long, marks: Marks, price: Double?) {
        val r = addToCollection(card, finish, condition, language, qty, binderId, marks)
        if (price == null) return
        coll.byId(r.itemId)?.let { item ->
            val joined = PurchasePrices.joined(item.quantity - qty, item.purchasePrice, qty, price)
            if (joined != item.purchasePrice) coll.update(item.copy(purchasePrice = joined))
        }
    }

    /**
     * Adds the chosen lines of an order (CardTrader's or Cardmarket's) to the collection, in [binder],
     * with each copy's price as its purchase price. Signed and altered copies are marked so (since
     * 1.27). Returns how many copies were added. Since 1.26.
     */
    suspend fun importOrder(lines: List<Pair<CardRef, OrderLine>>, binder: BinderChoice): Int {
        if (lines.isEmpty()) return 0
        beforeOrderImport?.invoke()
        val target = resolve(binder)
        var added = 0
        db.withTransaction {
            for ((card, l) in lines) {
                val finish = card.resolveFinish(if (l.foil) Finish.FOIL else Finish.NONFOIL)
                addBought(card, finish, l.condition, l.language, l.quantity, target, Marks(l.signed, l.altered), l.price)
                added += l.quantity
            }
        }
        return added
    }

    /**
     * After a package check: adds the received cards to [binder] with the order's details, and takes
     * the scanned copies that were used ([scanUse]: scan id to copies) off the scanned list. Returns how
     * many copies were added. Since 1.32.
     */
    suspend fun addCheckedPackage(additions: List<PackageCheck.Addition>, binder: BinderChoice, scanUse: Map<Long, Int>): Int {
        if (additions.isEmpty()) return 0
        beforeOrderImport?.invoke()
        val target = resolve(binder)
        db.withTransaction {
            for (a in additions) addBought(a.card, a.finish, a.condition, a.language, a.quantity, target, a.marks, a.price)
            for (scan in scans.byIds(scanUse.keys.toList())) {
                val left = scan.quantity - scanUse.getValue(scan.id)
                if (left > 0) scans.update(scan.copy(quantity = left)) else scans.deleteMany(listOf(scan.id))
            }
        }
        return additions.sumOf { it.quantity }
    }

    /**
     * Remembers [orders] in the order history. An order read before (same shop and order number) is
     * replaced by the new reading, keeping its place and sync identity. Returns how many were new. Since 1.33.
     */
    suspend fun recordOrders(orders: List<PurchaseOrder>): Int {
        if (orders.isEmpty()) return 0
        var added = 0
        db.withTransaction {
            val dao = db.orderDao()
            val known = dao.all().toMutableList()
            for (o in orders) {
                if (o.cards <= 0) continue
                val old = known.firstOrNull { Orders.same(it, o) }
                if (old == null) {
                    known += o.copy(id = dao.insert(o.copy(id = 0, uid = null, updatedAt = 0)))
                    added++
                } else if (old.copy(addedAt = 0, updatedAt = 0) != o.copy(id = old.id, uid = old.uid, addedAt = 0, updatedAt = 0)) {
                    dao.update(o.copy(id = old.id, uid = old.uid, addedAt = old.addedAt, updatedAt = old.updatedAt))
                }
            }
        }
        return added
    }

    suspend fun deleteOrder(id: Long) = db.orderDao().delete(id)

    suspend fun setOrderDate(id: Long, date: String) {
        db.orderDao().byId(id)?.let { db.orderDao().update(it.copy(date = date)) }
    }

    /**
     * Adds a preconstructed deck's cards to [binder], each with its share of what the deck cost, and
     * remembers the deck as an order. Returns how many copies were added. Since 1.33.
     */
    suspend fun addPrecon(deck: String, date: String, cards: List<Precons.PricedCard>, binder: BinderChoice): Int {
        if (cards.isEmpty()) return 0
        beforeOrderImport?.invoke()
        val target = resolve(binder)
        db.withTransaction {
            for (c in cards) addBought(c.card, c.finish, "NM", "EN", c.quantity, target, Marks.NONE, c.price)
        }
        recordOrders(listOf(Orders.precon(deck, date, cards.map { it.ordered })))
        return cards.sumOf { it.quantity }
    }

    /** Sets what was paid per copy on the stacks in [prices] (stack id to EUR). Returns how many changed. Since 1.32. */
    suspend fun setPurchasePrices(prices: Map<Long, Double>): Int {
        if (prices.isEmpty()) return 0
        beforeOrderImport?.invoke()
        var changed = 0
        db.withTransaction {
            for ((id, price) in prices) {
                val item = coll.byId(id) ?: continue
                if (item.purchasePrice != price) {
                    coll.update(item.copy(purchasePrice = price))
                    changed++
                }
            }
        }
        return changed
    }

    private val _csvImport = MutableStateFlow<CsvImportProgress?>(null)

    /** The CSV import in progress, if any. */
    val csvImport: StateFlow<CsvImportProgress?> = _csvImport

    private val _csvImportResult = MutableStateFlow<String?>(null)

    /** How the last CSV import ended, until the screen has shown it ([consumeCsvImportResult]). */
    val csvImportResult: StateFlow<String?> = _csvImportResult

    fun consumeCsvImportResult() {
        _csvImportResult.value = null
    }

    /** Shows [message] on the collection screen like a CSV import's result (e.g. after a CardTrader import). Since 1.26. */
    fun reportImport(message: String) {
        _csvImportResult.value = message
    }

    /**
     * Imports a CSV in the app scope, so it carries on if the user leaves the screen (a big
     * collection takes a while: Scryfall allows two 75-card lookups per second). False if one is
     * already running.
     */
    fun startCsvImport(readText: suspend () -> String, defaultBinder: Long): Boolean {
        synchronized(this) {
            if (_csvImport.value != null) return false
            _csvImport.value = CsvImportProgress(0, 0)
        }
        scope.launch {
            _csvImportResult.value = try {
                beforeCsvImport?.invoke()
                val r = importCollectionCsv(readText(), defaultBinder) { done, total -> _csvImport.value = CsvImportProgress(done, total) }
                "Imported ${r.imported} card(s)" + if (r.notFound > 0) " · ${r.notFound} couldn't be matched" else ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            } finally {
                _csvImport.value = null
            }
        }
        return true
    }

    /**
     * ManaBox-compatible column names, so the file can be imported back into ManaBox. With [binderId],
     * only that binder's cards (or Unsorted's, for [Binder.UNSORTED]) are exported; with [ids], only
     * those stacks (the ones selected, since 1.38).
     */
    suspend fun exportCollectionCsv(type: PriceType, binderId: Long? = null, ids: Set<Long>? = null): String {
        val items = coll.all().filter { (binderId == null || it.binderId == binderId) && (ids == null || it.id in ids) }
        val binderNames = binders.all().associate { it.id to it.name }
        val priceMap = prices.pricesFor(items.mapNotNull { it.card.productFor(it.foil) })
        val sb = StringBuilder()
        sb.appendLine(Csv.row("Binder Name", "Binder Type", "Name", "Set code", "Set name", "Collector number", "Foil", "Rarity", "Quantity", "Scryfall ID", "Condition", "Language", "Price EUR (${type.short})", "Purchase price", "Purchase price currency", "Notes", "Signed", "Altered"))
        for (i in items) {
            val price = priceMap[i.card.productFor(i.foil)]?.toSet(i.foil)?.best(type) ?: i.card.fallback(i.foil)
            val binder = binderNames[i.binderId]
            sb.appendLine(
                Csv.row(
                    binder ?: "", if (binder != null) "binder" else "",
                    i.card.name, i.card.setCode.uppercase(), i.card.setName, i.card.collectorNumber,
                    manaBoxFinish(i.finish), i.card.rarity, i.quantity, i.card.scryfallId,
                    manaBoxCondition(i.condition), i.language.lowercase(), price,
                    i.purchasePrice ?: "", if (i.purchasePrice != null) "EUR" else "", i.notes ?: "",
                    i.signed, i.altered,
                )
            )
        }
        return sb.toString()
    }

    suspend fun exportTradesCsv(type: PriceType): String {
        // Trades are identified by when they were started; this format sorts correctly in spreadsheets.
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        val sb = StringBuilder()
        sb.appendLine(Csv.row("Trade started", "Partner", "Applied to collection", "Side", "Name", "Set code", "Collector number", "Finish", "Condition", "Language", "Quantity", "Unit price EUR (${type.short})", "Custom price", "Line total EUR", "Scryfall ID"))
        for (t in trades.all()) {
            for (i in t.items) {
                sb.appendLine(
                    Csv.row(
                        df.format(Date(t.trade.createdAt)), t.trade.partner, if (t.trade.applied) "yes" else "no",
                        if (i.side == Side.GET) "received" else "given", i.card.name, i.card.setCode.uppercase(),
                        i.card.collectorNumber, i.card.finishName(i.finish).lowercase(), i.condition, i.language, i.quantity,
                        i.unitPrice(type), i.customPrice, i.lineTotal(type), i.card.scryfallId,
                    )
                )
            }
        }
        return sb.toString()
    }
}
