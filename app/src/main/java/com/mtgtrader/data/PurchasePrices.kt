package com.mtgtrader.data

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Purchase prices for cards already in the collection, from order files: each order line is matched
 * to the stacks it bought, and a stack gets the average paid, weighted by copies. Nothing is looked
 * up online: Cardmarket's lines carry the product number the collection stores per printing, and
 * CardTrader's the set and collector number. Since 1.32.
 */
object PurchasePrices {
    enum class Change {
        /** The stack had no price. */
        NEW,

        /** The stack had a price and holds more copies than were bought: the rest keep theirs, averaged in. */
        AVERAGED,

        /** The stack had a price, and "overwrite" was chosen. */
        REPLACED,

        /** The stack had a price and the orders cover every copy: it stays (unless "overwrite"). */
        KEPT,
    }

    /** A stack and what the orders say was paid for it. [newPrice] is per copy, in EUR. */
    data class Row(val item: CollectionItem, val purchases: List<OrderLine>, val bought: Int, val paid: Double, val newPrice: Double, val change: Change) {
        val changes get() = change != Change.KEPT

        /** Averaging euros from the orders into a price typed in kroner or dollars. Since 1.60. */
        val mixesCurrencies get() = change == Change.AVERAGED && item.typedPaid != null

        /** The stack with its new price: from the orders in euros, or averaged, kept in [currency] (by default its own). Since 1.60. */
        fun updated(currency: AppCurrency? = null): CollectionItem = when (change) {
            Change.KEPT -> item
            Change.AVERAGED -> {
                val typed = joinedTyped(item.quantity - bought, item.purchasePrice, item.typedPaid, bought, paid, null, currency).second
                item.withPaid(newPrice, typed)
            }
            else -> item.withPaid(newPrice)
        }
    }

    /** A line the files don't pin to one stack: the owner ticks which of [candidates] it bought, if any. */
    data class Open(val index: Int, val line: OrderLine, val candidates: List<CollectionItem>, val why: String)

    data class Plan(val rows: List<Row>, val open: List<Open>, val notOwned: List<OrderLine>, val withoutPrice: Int) {
        val changing get() = rows.filter { it.changes }
    }

    private class Found(val stacks: List<CollectionItem>, val sure: Boolean, val why: String = "")

    /** The collection, looked up the ways the shops name a card. */
    class Index(stacks: List<CollectionItem>) {
        val byProduct = HashMap<Int, MutableList<CollectionItem>>()
        val bySetNumber = HashMap<String, MutableList<CollectionItem>>()
        val byName = HashMap<String, MutableList<CollectionItem>>()

        init {
            for (s in stacks) {
                s.card.cardmarketId?.let { byProduct.getOrPut(it) { mutableListOf() } += s }
                s.card.cardmarketFoilId?.takeIf { it != s.card.cardmarketId }?.let { byProduct.getOrPut(it) { mutableListOf() } += s }
                bySetNumber.getOrPut(key(s.card.setCode, s.card.collectorNumber)) { mutableListOf() } += s
                for (n in OrderFiles.names(s.card.name)) byName.getOrPut(n) { mutableListOf() } += s
            }
        }

        fun named(name: String): List<CollectionItem> = OrderFiles.names(name).flatMap { byName[it].orEmpty() }.distinctBy { it.id }

        companion object {
            fun key(set: String, number: String) = "${set.lowercase()}|${number.lowercase()}"
        }
    }

    private fun finishWord(foil: Boolean) = if (foil) "foil" else "non-foil"

    /** Among stacks of the right printing: the finish, marks and language the line says, the condition as a tie-breaker. */
    private fun version(line: OrderLine, printing: List<CollectionItem>, foil: Boolean = line.foil): Found {
        val finish = printing.filter { it.foil == foil }
        if (finish.isEmpty()) return Found(printing, false, "Bought as ${finishWord(foil)}, in the collection as ${finishWord(!foil)}")
        val marks = finish.filter { it.signed == line.signed && it.altered == line.altered }.ifEmpty { finish }
        val language = if (line.languageKnown) marks.filter { it.language == line.language } else marks
        if (language.isEmpty()) {
            return Found(marks, false, "Bought in ${line.language}, in the collection in ${marks.map { it.language }.distinct().joinToString(", ")}")
        }
        return Found(language.filter { it.condition == line.condition }.ifEmpty { language }, true)
    }

    private fun otherPrinting(line: OrderLine, index: Index): Found {
        // Basic lands are owned in a dozen printings; the name alone says nothing.
        if (line.name.isEmpty() || OrderFiles.isBasicLandName(line.name)) return Found(emptyList(), false)
        val named = index.named(line.name)
        return Found(named, false, if (named.isEmpty()) "" else "You own this card as another printing")
    }

    private fun find(line: OrderLine, index: Index): Found {
        val product = line.productId
        if (product != null) {
            val printing = index.byProduct[product].orEmpty()
            if (printing.isEmpty()) return otherPrinting(line, index)
            if (!line.versionKnown) {
                // Cardmarket's monthly file says neither finish nor language: fine while only one version is owned.
                val versions = printing.groupBy { listOf(it.foil, it.etched, it.language, it.signed, it.altered) }
                return if (versions.size == 1) Found(printing, true)
                else Found(printing, false, "You own this printing in several versions, and the file doesn't say which was bought")
            }
            // A product that only exists in foil (Cardmarket lists some foils apart) is foil whatever the flag says.
            val foilOnly = printing.all { it.card.cardmarketFoilId == product && it.card.cardmarketId != product }
            return version(line, printing, line.foil || foilOnly)
        }
        val set = line.setCode.lowercase()
        val token = CardTraderOrder.tokenNumber(line.number)
        val names = OrderFiles.names(line.name)
        val exact = (if (token != null) index.bySetNumber[Index.key("t$set", token)] else index.bySetNumber[Index.key(set, CardTraderOrder.cleanNumber(line.number))])
            .orEmpty().filter { s -> OrderFiles.names(s.card.name).any { it in names } }
        val printing = exact.ifEmpty {
            // CardTrader's own codes for a set's collector, promo or The List cards.
            val related = index.named(line.name).filter { OrderFiles.sameSet(set, it.card.setCode) }
            val number = OrderFiles.plainNumber(line.number)
            val sameNumber = related.filter { number.isNotEmpty() && OrderFiles.plainNumber(it.card.collectorNumber) == number }
            if (OrderFiles.isListSet(set)) sameNumber.ifEmpty { related } else sameNumber
        }
        if (printing.isEmpty()) return otherPrinting(line, index)
        return version(line, printing)
    }

    /** The printing an order line bought, when the collection holds it (whatever finish or language): its card data. */
    fun printing(line: OrderLine, index: Index): CardRef? {
        val product = line.productId
        if (product != null) return index.byProduct[product]?.firstOrNull()?.card
        val found = find(line, index)
        // "Another printing" is exactly what this isn't.
        return found.stacks.firstOrNull()?.card?.takeIf { found.sure || found.why.startsWith("Bought") }
    }

    private fun cents(v: Double) = (v * 100).roundToLong() / 100.0

    /**
     * What [lines] would do to [stacks]. [choices] holds, per line number, the stacks ticked for a line
     * that is [Open]. With [overwrite], a stack's existing price is replaced by what the orders say;
     * without, it stays, except that a stack with more copies than were bought gets the weighted average
     * (4 copies at 2.00 and an order for 1 copy at 4.00 make 2.50).
     */
    fun plan(lines: List<OrderLine>, stacks: List<CollectionItem>, overwrite: Boolean, choices: Map<Int, Set<Long>> = emptyMap()): Plan {
        val index = Index(stacks)
        val perStack = LinkedHashMap<Long, MutableList<OrderLine>>()
        val open = ArrayList<Open>()
        val notOwned = ArrayList<OrderLine>()
        var withoutPrice = 0
        lines.forEachIndexed { i, line ->
            if (line.price == null) {
                withoutPrice++
                return@forEachIndexed
            }
            val found = find(line, index)
            val bought = when {
                found.sure -> found.stacks
                found.stacks.isEmpty() -> {
                    notOwned += line
                    emptyList()
                }
                else -> {
                    open += Open(i, line, found.stacks, found.why)
                    found.stacks.filter { it.id in choices[i].orEmpty() }
                }
            }
            for (s in bought) perStack.getOrPut(s.id) { mutableListOf() } += line
        }
        val byId = stacks.associateBy { it.id }
        val rows = perStack.map { (id, purchases) ->
            val item = byId.getValue(id)
            val bought = purchases.sumOf { it.quantity }
            val paid = purchases.sumOf { it.quantity * it.price!! } / bought
            val had = item.purchasePrice
            val (price, change) = when {
                had == null -> paid to Change.NEW
                overwrite -> paid to Change.REPLACED
                bought < item.quantity -> ((item.quantity - bought) * had + bought * paid) / item.quantity to Change.AVERAGED
                else -> had to Change.KEPT
            }
            val rounded = cents(price)
            Row(item, purchases, bought, cents(paid), rounded, if (had != null && abs(rounded - had) < 0.005) Change.KEPT else change)
        }
        return Plan(rows.sortedBy { it.item.card.name.lowercase() }, open, notOwned, withoutPrice)
    }

    /**
     * The price of a stack after [added] copies bought at [paid] join [had] copies: the average weighted
     * by copies when both have a price, else whichever is known (so every copy carries a price).
     */
    fun joined(had: Int, hadPrice: Double?, added: Int, paid: Double?): Double? = when {
        paid == null -> hadPrice
        hadPrice == null || had <= 0 -> paid
        else -> cents((had * hadPrice + added * paid) / (had + added))
    }

    /** The currency a price was typed in: euros unless [typed] says otherwise; null without a price. */
    private fun currencyOf(eur: Double?, typed: TypedPrice?) = if (eur == null) null else typed?.currency ?: AppCurrency.EUR

    /**
     * Whether joining copies paid [paidEur] (typed as [paidTyped]) with a stack priced [hadEur] (typed as [hadTyped])
     * mixes two currencies, so the owner chooses which one the stack's price is kept in. Since 1.60.
     */
    fun mixes(hadEur: Double?, hadTyped: TypedPrice?, paidEur: Double?, paidTyped: TypedPrice?): Boolean {
        val a = currencyOf(hadEur, hadTyped)
        val b = currencyOf(paidEur, paidTyped)
        return a != null && b != null && a != b
    }

    /**
     * [joined], and the joined price as typed: the average of both sides in one currency, [currency] when they
     * were typed in different ones (by default the stack's), each side converted at today's rate unless it was
     * typed in it. Null as typed when that currency is euros. Since 1.60.
     */
    fun joinedTyped(
        had: Int, hadEur: Double?, hadTyped: TypedPrice?, added: Int, paidEur: Double?, paidTyped: TypedPrice?, currency: AppCurrency? = null,
    ): Pair<Double?, TypedPrice?> {
        val eur = joined(had, hadEur, added, paidEur)
        return when {
            paidEur == null -> eur to hadTyped
            hadEur == null || had <= 0 -> eur to paidTyped
            else -> {
                val target = if (mixes(hadEur, hadTyped, paidEur, paidTyped)) currency ?: currencyOf(hadEur, hadTyped)!! else currencyOf(hadEur, hadTyped)!!
                if (target == AppCurrency.EUR) return eur to null
                fun inTarget(e: Double, t: TypedPrice?) = if (t?.currency == target) t.amount else Money.amountIn(e, target)
                val a = inTarget(hadEur, hadTyped) ?: return eur to null
                val b = inTarget(paidEur, paidTyped) ?: return eur to null
                eur to TypedPrice(cents((had * a + added * b) / (had + added)), target)
            }
        }
    }
}
