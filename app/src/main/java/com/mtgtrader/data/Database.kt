package com.mtgtrader.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Query("DELETE FROM prices")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<PriceEntity>)

    @Query("SELECT * FROM prices WHERE idProduct = :id")
    suspend fun get(id: Int): PriceEntity?

    @Query("SELECT * FROM prices WHERE idProduct IN (:ids)")
    suspend fun getMany(ids: List<Int>): List<PriceEntity>

    @Query("SELECT COUNT(*) FROM prices")
    fun count(): Flow<Int>
}

@Dao
interface CollectionDao {
    @Query(
        """SELECT c.*, p.idProduct AS pr_idProduct, p.avg AS pr_avg, p.low AS pr_low, p.trend AS pr_trend,
           p.avg1 AS pr_avg1, p.avg7 AS pr_avg7, p.avg30 AS pr_avg30, p.avgFoil AS pr_avgFoil,
           p.lowFoil AS pr_lowFoil, p.trendFoil AS pr_trendFoil, p.avg1Foil AS pr_avg1Foil,
           p.avg7Foil AS pr_avg7Foil, p.avg30Foil AS pr_avg30Foil
           FROM collection c LEFT JOIN prices p ON p.idProduct = c.cardmarketId
           ORDER BY c.name COLLATE NOCASE"""
    )
    fun observeAll(): Flow<List<CollectionRow>>

    @Query("SELECT scryfallId, SUM(quantity) AS qty FROM collection GROUP BY scryfallId")
    fun observeOwned(): Flow<List<OwnedCount>>

    @Query(
        """SELECT * FROM collection WHERE scryfallId = :sid AND foil = :foil AND etched = :etched
           AND condition = :cond AND language = :lang LIMIT 1"""
    )
    suspend fun find(sid: String, foil: Boolean, etched: Boolean, cond: String, lang: String): CollectionItem?

    @Query("SELECT * FROM collection WHERE scryfallId = :sid AND foil = :foil AND etched = :etched ORDER BY quantity DESC")
    suspend fun findAny(sid: String, foil: Boolean, etched: Boolean): List<CollectionItem>

    @Query("SELECT * FROM collection WHERE id = :id")
    suspend fun byId(id: Long): CollectionItem?

    @Query("SELECT * FROM collection ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<CollectionItem>

    @Insert
    suspend fun insert(item: CollectionItem): Long

    @Update
    suspend fun update(item: CollectionItem)

    @Query("DELETE FROM collection WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(item: CollectionItem)
}

@Dao
interface TradeDao {
    @Transaction
    @Query("SELECT * FROM trades ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TradeWithItems>>

    @Transaction
    @Query("SELECT * FROM trades WHERE id = :id")
    fun observe(id: Long): Flow<TradeWithItems?>

    @Transaction
    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun get(id: Long): TradeWithItems?

    @Transaction
    @Query("SELECT * FROM trades ORDER BY createdAt DESC")
    suspend fun all(): List<TradeWithItems>

    @Insert
    suspend fun insert(trade: Trade): Long

    @Update
    suspend fun update(trade: Trade)

    @Query("UPDATE trades SET partner = :partner WHERE id = :id")
    suspend fun setPartner(id: Long, partner: String)

    @Query("UPDATE trades SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String)

    @Query("DELETE FROM trades WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """DELETE FROM trades WHERE applied = 0 AND partner = '' AND notes = ''
           AND id NOT IN (SELECT DISTINCT tradeId FROM trade_items)"""
    )
    suspend fun deleteEmptyDrafts()

    @Insert
    suspend fun insertItem(item: TradeItem): Long

    @Update
    suspend fun updateItem(item: TradeItem)

    @Query("DELETE FROM trade_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM trade_items WHERE id = :id")
    suspend fun item(id: Long): TradeItem?

    @Query(
        """SELECT * FROM trade_items WHERE tradeId = :tradeId AND side = :side AND scryfallId = :sid
           AND foil = :foil AND etched = :etched AND language = :lang AND customPrice IS NULL LIMIT 1"""
    )
    suspend fun findSame(tradeId: Long, side: String, sid: String, foil: Boolean, etched: Boolean, lang: String): TradeItem?

    @Query("SELECT * FROM trade_items")
    suspend fun allItems(): List<TradeItem>
}

@Database(
    entities = [PriceEntity::class, CollectionItem::class, Trade::class, TradeItem::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun priceDao(): PriceDao
    abstract fun collectionDao(): CollectionDao
    abstract fun tradeDao(): TradeDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mtgtrader.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Version 2 (app 1.2): special foil type, etched finish, and etched copies kept apart in the collection. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("collection", "trade_items")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `foilType` TEXT")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `hasEtched` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `etched` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("DROP INDEX IF EXISTS `index_collection_scryfallId_foil_condition_language`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_collection_scryfallId_foil_etched_condition_language` " +
                        "ON `collection` (`scryfallId`, `foil`, `etched`, `condition`, `language`)"
                )
            }
        }
    }
}
