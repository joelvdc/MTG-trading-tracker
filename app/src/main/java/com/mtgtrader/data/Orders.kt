package com.mtgtrader.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/** One card line of a remembered order: what was bought, at what price, and the product to price it by today. */
@Serializable
data class OrderedCard(
    val name: String,
    val setCode: String = "",
    val number: String = "",
    val quantity: Int = 1,
    /** What one copy cost, in EUR. */
    val price: Double? = null,
    val foil: Boolean = false,
    /** As the order said; null when it didn't (Cardmarket's monthly file). */
    val condition: String? = null,
    val language: String? = null,
    val scryfallId: String? = null,
    /** Cardmarket's product number for the copy bought (the foil product, where Cardmarket has one). */
    val cardmarketId: Int? = null,
)

/**
 * An order the app remembers: where and when it was bought, what was paid, and its cards (as JSON in
 * [lines], so an order syncs as one row). A preconstructed deck is an order too, with [shop] "Precon".
 * Since 1.33.
 */
@Serializable
@Entity(tableName = "orders", indices = [Index("uid")])
data class PurchaseOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "CardTrader", "Cardmarket", "Precon". */
    val shop: String,
    /** The shop's order number, when the file had one: the same order read again replaces the first reading. */
    val number: String? = null,
    val title: String,
    /** yyyy-MM-dd */
    val date: String,
    /** What the cards cost together, in EUR. */
    val paid: Double,
    val cards: Int,
    val lines: String,
    val addedAt: Long = System.currentTimeMillis(),
    /** Sync identity and last change; filled in by database triggers (see [SyncSchema]). */
    val uid: String? = null,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<PurchaseOrder>>

    @Query("SELECT * FROM orders")
    suspend fun all(): List<PurchaseOrder>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun byId(id: Long): PurchaseOrder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: PurchaseOrder): Long

    @Update
    suspend fun update(order: PurchaseOrder)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun delete(id: Long)
}

/**
 * What an order's cards are worth today. [unpriced] copies have no known price: they are left out of
 * [value], and of [paid], what the priced copies cost, so a gain or loss compares like with like.
 */
data class OrderWorth(val value: Double, val priced: Int, val unpriced: Int, val paid: Double = 0.0)

object Orders {
    const val PRECON = "Precon"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** For the app's own orders file: its marker field has to be written even though it never changes. */
    private val fileJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lineList = ListSerializer(OrderedCard.serializer())

    fun lines(order: PurchaseOrder): List<OrderedCard> = runCatching { json.decodeFromString(lineList, order.lines) }.getOrDefault(emptyList())

    private fun cents(v: Double) = (v * 100).roundToLong() / 100.0

    fun of(shop: String, number: String?, title: String, date: String, cards: List<OrderedCard>) = PurchaseOrder(
        shop = shop, number = number, title = title, date = date,
        paid = cents(cards.sumOf { (it.price ?: 0.0) * it.quantity }), cards = cards.sumOf { it.quantity },
        lines = json.encodeToString(lineList, cards),
    )

    /** Whether [b] is the same order as [a] read again: the shop's number, or without one the same day, cards and amount. */
    fun same(a: PurchaseOrder, b: PurchaseOrder): Boolean =
        a.shop == b.shop && if (a.number != null || b.number != null) a.number == b.number else a.date == b.date && a.cards == b.cards && a.paid == b.paid

    /**
     * The product to price an order line by: Cardmarket's own number from its files; for CardTrader the
     * card's (its foil product for a foil, where Cardmarket lists foils apart).
     */
    fun card(line: OrderLine, card: CardRef?): OrderedCard = OrderedCard(
        name = card?.name ?: line.label,
        setCode = card?.setCode ?: line.setCode.lowercase(),
        number = card?.collectorNumber ?: line.number,
        quantity = line.quantity,
        price = line.price,
        foil = line.foil,
        condition = line.condition.takeIf { line.versionKnown },
        language = line.language.takeIf { line.versionKnown && line.languageKnown },
        scryfallId = card?.scryfallId,
        cardmarketId = line.productId ?: card?.let { c -> c.cardmarketFoilId?.takeIf { line.foil } ?: c.cardmarketId },
    )

    /**
     * The orders in a file: one, or for Cardmarket's monthly file one per shipment. [date] is used where
     * the file has none. [card] finds a line's printing (from Scryfall, or the collection), if known.
     */
    fun fromFile(file: OrderFile, date: String, card: (Int, OrderLine) -> CardRef?): List<PurchaseOrder> {
        val indexed = file.lines.withIndex().toList()
        return indexed.groupBy { it.value.order }.map { (number, lines) ->
            val first = lines.first().value
            val title = when (file.format) {
                OrderFormat.CARDTRADER -> "CardTrader order" + (number?.let { " $it" } ?: "")
                else -> "Cardmarket shipment" + (number?.let { " $it" } ?: "")
            }
            of(file.shop.label, number, title, first.date ?: date, lines.map { (i, l) -> card(l, card(i, l)) })
        }
    }

    /**
     * A preconstructed deck as an order: each card with its share of what the deck cost. Its "number" is
     * the deck and the day, so the same deck bought again later is an order of its own.
     */
    fun precon(deck: String, date: String, cards: List<OrderedCard>) = of(PRECON, "$deck $date", deck, date, cards)

    /**
     * Past orders from files, for the history only (nothing is added to the collection). Cardmarket's
     * lines carry the product number that prices them; CardTrader's printings come from the collection
     * where the card is owned ([stacks]), and from [lookup] (set code and number to card, e.g. Scryfall)
     * for the rest. Lines found by neither are kept without a price of today.
     */
    suspend fun fromFiles(
        files: List<OrderFile>,
        stacks: List<CollectionItem>,
        today: String,
        lookup: suspend (List<Pair<String, String>>) -> Map<Pair<String, String>, CardRef> = { emptyMap() },
    ): List<PurchaseOrder> {
        val index = PurchasePrices.Index(stacks)
        val owned = LinkedHashMap<OrderLine, CardRef?>()
        for (f in files) for (l in f.lines) owned.getOrPut(l) { PurchasePrices.printing(l, index) }
        val missing = owned.filter { (l, card) -> card == null && l.productId == null }.keys.mapNotNull { l -> CardTraderOrder.firstLookup(l)?.let { l to it } }
        val found = if (missing.isEmpty()) emptyMap() else lookup(missing.map { it.second }.distinct())
        for ((l, key) in missing) found[key]?.let { owned[l] = it }
        return files.flatMap { f -> fromFile(f, today) { _, l -> owned[l] } }
    }

    /**
     * What the order's cards are worth now, from Cardmarket's price guide ([prices], by product number) in
     * [type], through the chosen price source and condition percentages like any other value in the app.
     */
    fun worth(order: PurchaseOrder, prices: Map<Int, PriceEntity>, type: PriceType): OrderWorth = worth(lines(order), prices, type)

    fun worth(cards: List<OrderedCard>, prices: Map<Int, PriceEntity>, type: PriceType): OrderWorth {
        var value = 0.0
        var paid = 0.0
        var priced = 0
        var unpriced = 0
        for (c in cards) {
            val unit = unit(c, prices, type)
            if (unit == null) unpriced += c.quantity else {
                value += unit * c.quantity
                paid += (c.price ?: 0.0) * c.quantity
                priced += c.quantity
            }
        }
        return OrderWorth(value, priced, unpriced, paid)
    }

    fun unit(c: OrderedCard, prices: Map<Int, PriceEntity>, type: PriceType): Double? {
        val cm = c.cardmarketId?.let { prices[it] }?.toSet(c.foil)?.best(type)
        val condition = c.condition ?: "NM"
        return c.scryfallId?.let { Pricing.unit(it, if (c.foil) Finish.FOIL else Finish.NONFOIL, condition, cm) } ?: Pricing.fallback(condition, cm)
    }

    // ---- The app's own orders file (to carry orders that came from no shop file, like past precons) ----

    @Serializable
    data class Export(val mtgTraderOrders: Int = 1, val orders: List<ExportOrder> = emptyList())

    @Serializable
    data class ExportOrder(val shop: String, val number: String? = null, val title: String, val date: String, val cards: List<OrderedCard>)

    fun isExport(bytes: ByteArray): Boolean = bytes.take(200).toByteArray().toString(Charsets.UTF_8).let { it.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("{") && "mtgTraderOrders" in it }

    fun export(orders: List<PurchaseOrder>): String =
        fileJson.encodeToString(Export.serializer(), Export(orders = orders.map { ExportOrder(it.shop, it.number, it.title, it.date, lines(it)) }))

    fun import(text: String): List<PurchaseOrder> =
        fileJson.decodeFromString(Export.serializer(), text.removePrefix("\uFEFF")).orders.map { of(it.shop, it.number, it.title, it.date, it.cards) }
}
