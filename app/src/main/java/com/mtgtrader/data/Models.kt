package com.mtgtrader.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlin.math.abs
import kotlin.math.max

/** Which Cardmarket price-guide figure is used to value cards. */
enum class PriceType(val key: String, val label: String, val short: String) {
    TREND("trend", "Trend price", "Trend"),
    AVG("avg", "Average sell price", "Average"),
    AVG30("avg30", "30-day average", "30-day avg"),
    AVG7("avg7", "7-day average", "7-day avg"),
    AVG1("avg1", "1-day average", "1-day avg"),
    LOW("low", "Lowest listing", "Low");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: TREND
    }
}

/** One finish's (normal or foil) set of Cardmarket price-guide numbers, in EUR. */
data class PriceSet(
    val trend: Double? = null,
    val avg: Double? = null,
    val low: Double? = null,
    val avg1: Double? = null,
    val avg7: Double? = null,
    val avg30: Double? = null,
) {
    fun get(type: PriceType): Double? = when (type) {
        PriceType.TREND -> trend
        PriceType.AVG -> avg
        PriceType.LOW -> low
        PriceType.AVG1 -> avg1
        PriceType.AVG7 -> avg7
        PriceType.AVG30 -> avg30
    }

    /** The requested figure, falling back to the most stable one available. */
    fun best(type: PriceType): Double? = get(type) ?: trend ?: avg ?: avg30 ?: avg7 ?: avg1 ?: low
}

/** A row of Cardmarket's daily price guide, keyed by Cardmarket product id. */
@Entity(tableName = "prices")
data class PriceEntity(
    @PrimaryKey val idProduct: Int,
    val avg: Double?,
    val low: Double?,
    val trend: Double?,
    val avg1: Double?,
    val avg7: Double?,
    val avg30: Double?,
    val avgFoil: Double?,
    val lowFoil: Double?,
    val trendFoil: Double?,
    val avg1Foil: Double?,
    val avg7Foil: Double?,
    val avg30Foil: Double?,
) {
    fun toSet(foil: Boolean): PriceSet =
        if (foil) PriceSet(trend = trendFoil, avg = avgFoil, low = lowFoil, avg1 = avg1Foil, avg7 = avg7Foil, avg30 = avg30Foil)
        else PriceSet(trend = trend, avg = avg, low = low, avg1 = avg1, avg7 = avg7, avg30 = avg30)
}

/** The printing-specific card data we keep, copied from Scryfall. */
data class CardRef(
    val scryfallId: String,
    val name: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val rarity: String,
    val imageUrl: String?,
    val cardmarketId: Int?,
    /** Scryfall's EUR prices (sourced from Cardmarket) — used when the price guide has no entry. */
    val fallbackEur: Double?,
    val fallbackEurFoil: Double?,
    val hasNonFoil: Boolean,
    val hasFoil: Boolean,
) {
    fun fallback(foil: Boolean) = if (foil) fallbackEurFoil else fallbackEur
    val setLabel get() = "${setCode.uppercase()} #$collectorNumber"

    /** Picks a finish that exists for this printing, preferring [wantFoil]. */
    fun resolveFoil(wantFoil: Boolean) = when {
        wantFoil && hasFoil -> true
        !hasNonFoil && hasFoil -> true
        else -> false
    }
}

@Entity(
    tableName = "collection",
    indices = [
        Index(value = ["scryfallId", "foil", "condition", "language"], unique = true),
        Index("name"),
    ],
)
data class CollectionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val card: CardRef,
    val foil: Boolean,
    val condition: String = "NM",
    val language: String = "EN",
    val quantity: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

/** Collection row joined with today's price guide entry. */
data class CollectionRow(
    @Embedded val item: CollectionItem,
    @Embedded(prefix = "pr_") val price: PriceEntity?,
) {
    fun unitPrice(type: PriceType): Double? =
        price?.toSet(item.foil)?.best(type) ?: item.card.fallback(item.foil)
}

@Entity(tableName = "trades")
data class Trade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val partner: String = "",
    val notes: String = "",
    val applied: Boolean = false,
    val appliedAt: Long? = null,
)

object Side {
    /** Cards you receive. */
    const val GET = "GET"

    /** Cards you hand over. */
    const val GIVE = "GIVE"
}

@Entity(
    tableName = "trade_items",
    foreignKeys = [ForeignKey(entity = Trade::class, parentColumns = ["id"], childColumns = ["tradeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tradeId")],
)
data class TradeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tradeId: Long,
    val side: String,
    @Embedded val card: CardRef,
    val foil: Boolean,
    val condition: String = "NM",
    val language: String = "EN",
    val quantity: Int = 1,
    /** Price guide values captured when the card was added (or last refreshed). */
    @Embedded(prefix = "p_") val prices: PriceSet = PriceSet(),
    /** Optional manually agreed price per copy, overriding the price guide. */
    val customPrice: Double? = null,
    /** How many copies were actually added (+) / removed (−) from the collection when the trade was applied. */
    val appliedDelta: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun unitPrice(type: PriceType): Double? = customPrice ?: prices.best(type) ?: card.fallback(foil)
    fun lineTotal(type: PriceType): Double = (unitPrice(type) ?: 0.0) * quantity
}

data class TradeWithItems(
    @Embedded val trade: Trade,
    @Relation(parentColumn = "id", entityColumn = "tradeId") val items: List<TradeItem>,
) {
    val give get() = items.filter { it.side == Side.GIVE }
    val get get() = items.filter { it.side == Side.GET }
    fun balance(type: PriceType, tolerancePct: Int) = Balance.of(items, type, tolerancePct)
}

data class OwnedCount(val scryfallId: String, val qty: Int)

enum class Verdict { EMPTY, FAIR, FAVORS_YOU, FAVORS_THEM }

/** Value of both sides of a trade and how far apart they are. */
data class Balance(val give: Double, val get: Double, val tolerancePct: Int) {
    /** Positive when you receive more value than you hand over. */
    val diff get() = get - give

    /** Difference relative to the bigger side, in percent. */
    val pct: Double
        get() {
            val m = max(give, get)
            return if (m > 0) diff / m * 100 else 0.0
        }

    val verdict: Verdict
        get() = when {
            give == 0.0 && get == 0.0 -> Verdict.EMPTY
            abs(pct) <= tolerancePct -> Verdict.FAIR
            diff > 0 -> Verdict.FAVORS_YOU
            else -> Verdict.FAVORS_THEM
        }

    companion object {
        fun of(items: List<TradeItem>, type: PriceType, tolerancePct: Int) = Balance(
            give = items.filter { it.side == Side.GIVE }.sumOf { it.lineTotal(type) },
            get = items.filter { it.side == Side.GET }.sumOf { it.lineTotal(type) },
            tolerancePct = tolerancePct,
        )
    }
}

val CONDITIONS = listOf(
    "MT" to "Mint",
    "NM" to "Near Mint",
    "EX" to "Excellent",
    "GD" to "Good",
    "LP" to "Light Played",
    "PL" to "Played",
    "PO" to "Poor",
)

val LANGUAGES = listOf(
    "EN" to "English", "DE" to "German", "FR" to "French", "IT" to "Italian", "ES" to "Spanish",
    "PT" to "Portuguese", "JA" to "Japanese", "KO" to "Korean", "RU" to "Russian",
    "ZHS" to "Chinese (S)", "ZHT" to "Chinese (T)",
)
