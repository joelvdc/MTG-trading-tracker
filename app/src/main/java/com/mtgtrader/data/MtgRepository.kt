package com.mtgtrader.data

import androidx.room.withTransaction
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where a card picked in search or the scanner goes. */
sealed interface CardTarget {
    data class TradeSide(val tradeId: Long, val side: String) : CardTarget
    data object Collection : CardTarget
    data class ReplaceTradeItem(val itemId: Long) : CardTarget
    data class ReplaceCollectionItem(val itemId: Long) : CardTarget

    val isReplace get() = this is ReplaceTradeItem || this is ReplaceCollectionItem

    fun encode(): String = when (this) {
        is TradeSide -> "trade:$tradeId:$side"
        Collection -> "collection"
        is ReplaceTradeItem -> "rtrade:$itemId"
        is ReplaceCollectionItem -> "rcoll:$itemId"
    }

    companion object {
        fun decode(s: String): CardTarget {
            val p = s.split(':')
            return when (p[0]) {
                "trade" -> TradeSide(p[1].toLong(), p[2])
                "rtrade" -> ReplaceTradeItem(p[1].toLong())
                "rcoll" -> ReplaceCollectionItem(p[1].toLong())
                else -> Collection
            }
        }
    }
}

/** Identifies one added copy so the scanner can undo it. */
data class AddResult(val itemId: Long, val inTrade: Boolean)

data class ImportResult(val imported: Int, val notFound: Int)

class MtgRepository(
    private val db: AppDatabase,
    val scryfall: ScryfallApi,
    val prices: PriceGuideRepository,
) {
    private val trades = db.tradeDao()
    private val coll = db.collectionDao()

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

    suspend fun add(target: CardTarget, card: CardRef, finish: Finish, language: String = "EN"): AddResult? = when (target) {
        is CardTarget.TradeSide -> addToTrade(target.tradeId, target.side, card, finish, language)
        CardTarget.Collection -> addToCollection(card, finish, "NM", language, 1)
        else -> null
    }

    suspend fun addToTrade(tradeId: Long, side: String, card: CardRef, finish: Finish, language: String = "EN", qty: Int = 1): AddResult {
        val existing = trades.findSame(tradeId, side, card.scryfallId, finish.foil, finish.etched, language)
        if (existing != null) {
            trades.updateItem(existing.copy(quantity = existing.quantity + qty))
            return AddResult(existing.id, true)
        }
        val id = trades.insertItem(
            TradeItem(
                tradeId = tradeId, side = side, card = card, foil = finish.foil, etched = finish.etched, language = language,
                quantity = qty, prices = snapshot(card, finish.foil),
            )
        )
        return AddResult(id, true)
    }

    suspend fun undoAdd(r: AddResult) {
        if (r.inTrade) {
            val item = trades.item(r.itemId) ?: return
            if (item.quantity > 1) trades.updateItem(item.copy(quantity = item.quantity - 1)) else trades.deleteItem(item.id)
        } else {
            val item = coll.byId(r.itemId) ?: return
            if (item.quantity > 1) coll.update(item.copy(quantity = item.quantity - 1)) else coll.deleteById(item.id)
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
            else -> {}
        }
    }

    /**
     * Adds received cards to the collection and removes given cards from it.
     * Returns how many given copies weren't found in the collection.
     */
    suspend fun applyTrade(tradeId: Long): Int = db.withTransaction {
        val t = trades.get(tradeId) ?: return@withTransaction 0
        if (t.trade.applied) return@withTransaction 0
        var missing = 0
        for (item in t.items) {
            if (item.side == Side.GET) {
                addToCollection(item.card, item.finish, item.condition, item.language, item.quantity)
                trades.updateItem(item.copy(appliedDelta = item.quantity))
            } else {
                val removed = removeFromCollection(item.card.scryfallId, item.finish, item.condition, item.language, item.quantity)
                missing += item.quantity - removed
                trades.updateItem(item.copy(appliedDelta = -removed))
            }
        }
        trades.update(t.trade.copy(applied = true, appliedAt = System.currentTimeMillis()))
        missing
    }

    /** Reverses exactly what [applyTrade] did, so the trade can be edited again. */
    suspend fun revertTrade(tradeId: Long) = db.withTransaction {
        val t = trades.get(tradeId) ?: return@withTransaction
        if (!t.trade.applied) return@withTransaction
        for (item in t.items) {
            when {
                item.appliedDelta > 0 ->
                    removeFromCollection(item.card.scryfallId, item.finish, item.condition, item.language, item.appliedDelta)
                item.appliedDelta < 0 ->
                    addToCollection(item.card, item.finish, item.condition, item.language, -item.appliedDelta)
            }
            trades.updateItem(item.copy(appliedDelta = 0))
        }
        trades.update(t.trade.copy(applied = false, appliedAt = null))
    }

    // ---- collection ----------------------------------------------------------------------

    suspend fun addToCollection(card: CardRef, finish: Finish, condition: String, language: String, qty: Int): AddResult {
        val existing = coll.find(card.scryfallId, finish.foil, finish.etched, condition, language)
        if (existing != null) {
            coll.update(existing.copy(quantity = existing.quantity + qty, card = card))
            return AddResult(existing.id, false)
        }
        val id = coll.insert(
            CollectionItem(card = card, foil = finish.foil, etched = finish.etched, condition = condition, language = language, quantity = qty)
        )
        return AddResult(id, false)
    }

    /** Removes up to [qty] copies, preferring the exact condition/language. Returns copies removed. */
    private suspend fun removeFromCollection(sid: String, finish: Finish, condition: String, language: String, qty: Int): Int {
        var remaining = qty
        val exact = coll.find(sid, finish.foil, finish.etched, condition, language)
        val candidates = listOfNotNull(exact) + coll.findAny(sid, finish.foil, finish.etched).filter { it.id != exact?.id }
        for (c in candidates) {
            if (remaining == 0) break
            val take = minOf(remaining, c.quantity)
            if (c.quantity - take <= 0) coll.deleteById(c.id) else coll.update(c.copy(quantity = c.quantity - take))
            remaining -= take
        }
        return qty - remaining
    }

    /** Saves an edited row, merging it into an existing row if it now has the same card/finish/condition/language. */
    suspend fun updateCollectionItem(updated: CollectionItem) = db.withTransaction {
        val clash = coll.find(updated.card.scryfallId, updated.foil, updated.etched, updated.condition, updated.language)
        if (clash != null && clash.id != updated.id) {
            coll.update(clash.copy(quantity = clash.quantity + updated.quantity))
            coll.deleteById(updated.id)
        } else {
            coll.update(updated)
        }
    }

    suspend fun deleteCollectionItem(id: Long) = coll.deleteById(id)

    /** Undo for [deleteCollectionItem]; merges into a matching row if the same card was added again meanwhile. */
    suspend fun restoreCollectionItem(item: CollectionItem) = db.withTransaction {
        val clash = coll.find(item.card.scryfallId, item.foil, item.etched, item.condition, item.language)
        when {
            clash != null -> coll.update(clash.copy(quantity = clash.quantity + item.quantity))
            coll.byId(item.id) == null -> coll.insert(item)
            else -> coll.insert(item.copy(id = 0))
        }
    }

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
     */
    suspend fun importCollectionCsv(text: String): ImportResult {
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

        data class Line(val ident: JsonObject, val key: String, val qty: Int, val finish: Finish, val cond: String, val lang: String)

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
            when {
                id != null -> Line(ScryfallApi.idIdentifier(id), "id:$id", qty, finish, cond, lang)
                set != null && num != null ->
                    Line(ScryfallApi.setNumberIdentifier(set, num), "sn:${set.lowercase()}|${num.lowercase()}", qty, finish, cond, lang)
                name != null -> Line(ScryfallApi.nameIdentifier(name), "n:${name.lowercase()}", qty, finish, cond, lang)
                else -> null
            }
        }
        val cards = scryfall.collection(lines.map { it.ident })
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
            for (l in lines) {
                val c = byKey[l.key]
                if (c == null) {
                    notFound += l.qty
                    continue
                }
                val ref = c.toRef()
                addToCollection(ref, ref.resolveFinish(l.finish), l.cond, l.lang, l.qty)
                imported += l.qty
            }
        }
        return ImportResult(imported, notFound)
    }

    /** ManaBox-compatible column names, so the file can be imported back into ManaBox. */
    suspend fun exportCollectionCsv(type: PriceType): String {
        val items = coll.all()
        val priceMap = prices.pricesFor(items.mapNotNull { it.card.cardmarketId })
        val sb = StringBuilder()
        sb.appendLine(Csv.row("Name", "Set code", "Set name", "Collector number", "Foil", "Rarity", "Quantity", "Scryfall ID", "Condition", "Language", "Price EUR (${type.short})"))
        for (i in items) {
            val price = priceMap[i.card.cardmarketId]?.toSet(i.foil)?.best(type) ?: i.card.fallback(i.foil)
            sb.appendLine(
                Csv.row(
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
