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
    data class ReplaceTradeItem(val itemId: Long) : CardTarget
    data class ReplaceCollectionItem(val itemId: Long) : CardTarget
    data class ReplaceScan(val itemId: Long) : CardTarget

    val isReplace get() = this is ReplaceTradeItem || this is ReplaceCollectionItem || this is ReplaceScan

    fun encode(): String = when (this) {
        is TradeSide -> "trade:$tradeId:$side"
        is Collection -> "collection:$binderId"
        Scans -> "scans"
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
                "rtrade" -> ReplaceTradeItem(p[1].toLong())
                "rcoll" -> ReplaceCollectionItem(p[1].toLong())
                "rscan" -> ReplaceScan(p[1].toLong())
                else -> Collection(p.getOrNull(1)?.toLongOrNull() ?: Binder.UNSORTED)
            }
        }
    }
}

enum class AddedTo { TRADE, COLLECTION, SCANS }

/** Identifies one added copy so the scanner can undo it. */
data class AddResult(val itemId: Long, val addedTo: AddedTo) {
    /** Where to send "choose a different printing" for this copy. */
    val replaceTarget: CardTarget
        get() = when (addedTo) {
            AddedTo.TRADE -> CardTarget.ReplaceTradeItem(itemId)
            AddedTo.COLLECTION -> CardTarget.ReplaceCollectionItem(itemId)
            AddedTo.SCANS -> CardTarget.ReplaceScan(itemId)
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

    // ---- trades --------------------------------------------------------------------------

    suspend fun newTrade(): Long = trades.insert(Trade())

    suspend fun deleteEmptyDrafts() = trades.deleteEmptyDrafts()

    suspend fun deleteTrade(id: Long) = trades.delete(id)

    suspend fun setPartner(id: Long, partner: String) = trades.setPartner(id, partner)

    suspend fun setNotes(id: Long, notes: String) = trades.setNotes(id, notes)

    suspend fun snapshot(card: CardRef, foil: Boolean): PriceSet {
        val guide = prices.priceFor(card.cardmarketId, foil)
        return if (guide?.best(PriceType.TREND) != null) guide else PriceSet(trend = card.fallback(foil))
    }

    suspend fun add(target: CardTarget, card: CardRef, finish: Finish, language: String = "EN", exactPrinting: Boolean = true): AddResult? =
        when (target) {
            is CardTarget.TradeSide -> addToTrade(target.tradeId, target.side, card, finish, language)
            is CardTarget.Collection -> addToCollection(card, finish, "NM", language, 1, target.binderId)
            CardTarget.Scans -> addToScans(card, finish, language, exactPrinting)
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
        }
    }

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
    ): AddResult {
        val existing = coll.find(card.scryfallId, finish.foil, finish.etched, condition, language, binderId)
        if (existing != null) {
            coll.update(existing.copy(quantity = existing.quantity + qty, card = card))
            return AddResult(existing.id, AddedTo.COLLECTION)
        }
        val id = coll.insert(
            CollectionItem(
                card = card, foil = finish.foil, etched = finish.etched, condition = condition, language = language,
                quantity = qty, binderId = binderId,
            )
        )
        return AddResult(id, AddedTo.COLLECTION)
    }

    private data class Removal(val removed: Int, val fromBinder: Long)

    /**
     * Removes up to [qty] copies of a printing, taking stacks with the exact condition and language
     * first, and within those the [prefer] binder first.
     */
    private suspend fun removeFromCollection(
        sid: String, finish: Finish, condition: String, language: String, qty: Int, prefer: Long = Binder.UNSORTED,
    ): Removal {
        var remaining = qty
        var from: Long? = null
        val candidates = coll.findAny(sid, finish.foil, finish.etched).sortedWith(
            compareBy({ if (it.condition == condition && it.language == language) 0 else 1 }, { if (it.binderId == prefer) 0 else 1 })
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

    /** Saves an edited row, merging it into an existing row if it now has the same card/finish/condition/language/binder. */
    suspend fun updateCollectionItem(updated: CollectionItem) = db.withTransaction {
        val clash = coll.find(updated.card.scryfallId, updated.foil, updated.etched, updated.condition, updated.language, updated.binderId)
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
     */
    suspend fun saveCollectionEdit(updated: CollectionItem, binderId: Long, move: Int) = db.withTransaction {
        val n = move.coerceIn(0, updated.quantity)
        when {
            binderId == updated.binderId || n == 0 -> updateCollectionItem(updated)
            n >= updated.quantity -> updateCollectionItem(updated.copy(binderId = binderId))
            else -> {
                updateCollectionItem(updated.copy(quantity = updated.quantity - n))
                addToCollection(updated.card, updated.finish, updated.condition, updated.language, n, binderId)
            }
        }
    }

    suspend fun deleteCollectionItem(id: Long) = coll.deleteById(id)

    /** Undo for [deleteCollectionItem]; merges into a matching row if the same card was added again meanwhile. */
    suspend fun restoreCollectionItem(item: CollectionItem) = db.withTransaction {
        val clash = coll.find(item.card.scryfallId, item.foil, item.etched, item.condition, item.language, item.binderId)
        when {
            clash != null -> coll.update(clash.copy(quantity = clash.quantity + item.quantity))
            coll.byId(item.id) == null -> coll.insert(item)
            else -> coll.insert(item.copy(id = 0))
        }
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

        data class Line(
            val ident: JsonObject, val key: String, val qty: Int, val finish: Finish, val cond: String, val lang: String,
            val binder: String? = null,
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
            when {
                id != null -> Line(ScryfallApi.idIdentifier(id), "id:$id", qty, finish, cond, lang, binder)
                set != null && num != null ->
                    Line(ScryfallApi.setNumberIdentifier(set, num), "sn:${set.lowercase()}|${num.lowercase()}", qty, finish, cond, lang, binder)
                name != null -> Line(ScryfallApi.nameIdentifier(name), "n:${name.lowercase()}", qty, finish, cond, lang, binder)
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
                addToCollection(ref, ref.resolveFinish(l.finish), l.cond, l.lang, l.qty, binder)
                imported += l.qty
            }
        }
        return ImportResult(imported, notFound)
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
     * only that binder's cards (or Unsorted's, for [Binder.UNSORTED]) are exported.
     */
    suspend fun exportCollectionCsv(type: PriceType, binderId: Long? = null): String {
        val items = coll.all().filter { binderId == null || it.binderId == binderId }
        val binderNames = binders.all().associate { it.id to it.name }
        val priceMap = prices.pricesFor(items.mapNotNull { it.card.cardmarketId })
        val sb = StringBuilder()
        sb.appendLine(Csv.row("Binder Name", "Binder Type", "Name", "Set code", "Set name", "Collector number", "Foil", "Rarity", "Quantity", "Scryfall ID", "Condition", "Language", "Price EUR (${type.short})"))
        for (i in items) {
            val price = priceMap[i.card.cardmarketId]?.toSet(i.foil)?.best(type) ?: i.card.fallback(i.foil)
            val binder = binderNames[i.binderId]
            sb.appendLine(
                Csv.row(
                    binder ?: "", if (binder != null) "binder" else "",
                    i.card.name, i.card.setCode.uppercase(), i.card.setName, i.card.collectorNumber,
                    manaBoxFinish(i.finish), i.card.rarity, i.quantity, i.card.scryfallId,
                    manaBoxCondition(i.condition), i.language.lowercase(), price,
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
