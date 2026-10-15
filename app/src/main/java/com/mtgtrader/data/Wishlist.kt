package com.mtgtrader.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * A card you want. By default any printing will do (it's wanted by name); with [anyPrinting] off,
 * only this exact printing. Shown as the Wishlist in the Collection tab, not counted in the
 * collection's value. Since 1.16.
 */
@Serializable
@Entity(tableName = "wishlist", indices = [Index("name"), Index("uid")])
data class WishlistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val card: CardRef,
    val foil: Boolean = false,
    val quantity: Int = 1,
    val anyPrinting: Boolean = true,
    val notes: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    /** Copies you already owned when it was added, so only copies you get later take it off the list. */
    @ColumnInfo(defaultValue = "0") val ownedAtAdd: Int = 0,
    /** Sync identity and last change; filled in by database triggers (see [SyncSchema]). */
    val uid: String? = null,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

/**
 * A wishlist card added while offline, known only by its name (from Cardmarket's product list on
 * the phone). Its printing, picture and prices are filled in from Scryfall once the phone is back
 * online. The placeholder id has Scryfall's id format, so older versions on another phone only
 * see a card without details. Since 1.34.
 */
object PendingCard {
    const val ID_PREFIX = "00000000-0000-0000-0000-"

    fun isPending(scryfallId: String) = scryfallId.startsWith(ID_PREFIX)

    fun ref(name: String) = CardRef(
        scryfallId = ID_PREFIX + "%012x".format(name.lowercase().hashCode().toLong() and 0xffffffffL),
        name = name,
        setCode = "",
        setName = "",
        collectorNumber = "",
        rarity = "",
        imageUrl = null,
        cardmarketId = null,
        fallbackEur = null,
        fallbackEurFoil = null,
        hasNonFoil = true,
        hasFoil = false,
    )

    /** Cardmarket names double-faced cards "Front / Back"; Scryfall (and the rest of the app) "Front // Back". */
    fun cardName(cardmarketName: String) = cardmarketName.replace(Regex("(?<!/) / (?!/)"), " // ")

    /** Names on Cardmarket's list matching what was typed: names starting with it first. Art cards are left out. */
    fun suggestions(typed: String, starting: List<String>, containing: List<String>, max: Int = 20): List<String> =
        (starting + containing).filterNot { it.startsWith("Art Series: ") }.map(::cardName).distinctBy { it.lowercase() }.take(max)
}

/** Wishlist entry joined with today's price guide entry. */
data class WishlistRow(
    @Embedded val item: WishlistItem,
    @Embedded(prefix = "pr_") val price: PriceEntity?,
) {
    fun unitPrice(type: PriceType): Double? =
        Pricing.unit(item.card.scryfallId, if (item.foil) Finish.FOIL else Finish.NONFOIL, "NM", price?.toSet(item.foil)?.best(type) ?: item.card.fallback(item.foil))
}

/** A wishlist entry's standing against the collection: copies owned now, and copies got since it was added. */
data class WishlistOwned(val owned: Int, val gotSince: Int)

@Dao
interface WishlistDao {
    @Query(
        """SELECT w.*, p.idProduct AS pr_idProduct, p.avg AS pr_avg, p.low AS pr_low, p.trend AS pr_trend,
           p.avg1 AS pr_avg1, p.avg7 AS pr_avg7, p.avg30 AS pr_avg30, p.avgFoil AS pr_avgFoil,
           p.lowFoil AS pr_lowFoil, p.trendFoil AS pr_trendFoil, p.avg1Foil AS pr_avg1Foil,
           p.avg7Foil AS pr_avg7Foil, p.avg30Foil AS pr_avg30Foil
           FROM wishlist w LEFT JOIN prices p ON p.idProduct =
             CASE WHEN w.foil = 1 AND w.cardmarketFoilId IS NOT NULL THEN w.cardmarketFoilId ELSE w.cardmarketId END
           ORDER BY w.name COLLATE NOCASE"""
    )
    fun observeAll(): Flow<List<WishlistRow>>

    @Query("SELECT * FROM wishlist")
    suspend fun all(): List<WishlistItem>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM wishlist")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM wishlist WHERE id = :id")
    suspend fun byId(id: Long): WishlistItem?

    @Query("SELECT * FROM wishlist WHERE scryfallId LIKE '00000000-0000-0000-0000-%'")
    suspend fun pending(): List<WishlistItem>

    @Query("SELECT * FROM wishlist WHERE name = :name COLLATE NOCASE")
    suspend fun byName(name: String): List<WishlistItem>

    @Insert
    suspend fun insert(item: WishlistItem): Long

    @Update
    suspend fun update(item: WishlistItem)

    @Query("DELETE FROM wishlist WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM wishlist WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>)
}

/** The collection's value on one day, for each price type (JSON: price type key → EUR). Since 1.16. */
@Entity(tableName = "value_history")
data class ValueSnapshot(
    /** "2026-10-04" */
    @PrimaryKey val day: String,
    val cards: Int,
    val values: String,
)

@Dao
interface ValueHistoryDao {
    @Query("SELECT * FROM value_history ORDER BY day")
    fun observeAll(): Flow<List<ValueSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: ValueSnapshot)
}
