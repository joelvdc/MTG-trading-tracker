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

    /** Copies owned per card name, over every printing, finish and binder. */
    @Query("SELECT name, SUM(quantity) AS qty FROM collection GROUP BY name")
    suspend fun ownedByName(): List<NameCount>

    @Query(
        """SELECT * FROM collection WHERE scryfallId = :sid AND foil = :foil AND etched = :etched
           AND condition = :cond AND language = :lang AND binderId = :binderId LIMIT 1"""
    )
    suspend fun find(sid: String, foil: Boolean, etched: Boolean, cond: String, lang: String, binderId: Long): CollectionItem?

    /** Every stack of this printing and finish, in any binder. */
    @Query("SELECT * FROM collection WHERE scryfallId = :sid AND foil = :foil AND etched = :etched ORDER BY quantity DESC")
    suspend fun findAny(sid: String, foil: Boolean, etched: Boolean): List<CollectionItem>

    @Query("SELECT * FROM collection WHERE binderId = :binderId")
    suspend fun inBinder(binderId: Long): List<CollectionItem>

    @Query("DELETE FROM collection WHERE binderId = :binderId")
    suspend fun deleteBinderCards(binderId: Long)

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

    /** Trades not yet applied to the collection, newest first. */
    @Query("SELECT * FROM trades WHERE applied = 0 ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<Trade>>
}

@Dao
interface BinderDao {
    @Query("SELECT * FROM binders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Binder>>

    @Query("SELECT * FROM binders ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Binder>

    @Query("SELECT * FROM binders WHERE id = :id")
    suspend fun get(id: Long): Binder?

    @Query("SELECT * FROM binders WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): Binder?

    @Insert
    suspend fun insert(binder: Binder): Long

    @Query("UPDATE binders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM binders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ScanDao {
    @Query(
        """SELECT s.*, p.idProduct AS pr_idProduct, p.avg AS pr_avg, p.low AS pr_low, p.trend AS pr_trend,
           p.avg1 AS pr_avg1, p.avg7 AS pr_avg7, p.avg30 AS pr_avg30, p.avgFoil AS pr_avgFoil,
           p.lowFoil AS pr_lowFoil, p.trendFoil AS pr_trendFoil, p.avg1Foil AS pr_avg1Foil,
           p.avg7Foil AS pr_avg7Foil, p.avg30Foil AS pr_avg30Foil
           FROM scans s LEFT JOIN prices p ON p.idProduct = s.cardmarketId
           ORDER BY s.scannedAt DESC"""
    )
    fun observeAll(): Flow<List<ScanRow>>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM scans")
    fun observeCount(): Flow<Int>

    @Query(
        """SELECT * FROM scans WHERE scryfallId = :sid AND foil = :foil AND etched = :etched
           AND condition = :cond AND language = :lang LIMIT 1"""
    )
    suspend fun find(sid: String, foil: Boolean, etched: Boolean, cond: String, lang: String): ScannedCard?

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun byId(id: Long): ScannedCard?

    @Query("SELECT * FROM scans WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<ScannedCard>

    @Insert
    suspend fun insert(item: ScannedCard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(items: List<ScannedCard>)

    @Update
    suspend fun update(item: ScannedCard)

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scans WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>)
}

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Deck>>

    @Query("SELECT * FROM decks WHERE archidektId = :id")
    fun observe(id: Long): Flow<Deck?>

    @Query("SELECT * FROM decks WHERE archidektId = :id")
    suspend fun get(id: Long): Deck?

    @Query(
        """SELECT d.*, p.idProduct AS pr_idProduct, p.avg AS pr_avg, p.low AS pr_low, p.trend AS pr_trend,
           p.avg1 AS pr_avg1, p.avg7 AS pr_avg7, p.avg30 AS pr_avg30, p.avgFoil AS pr_avgFoil,
           p.lowFoil AS pr_lowFoil, p.trendFoil AS pr_trendFoil, p.avg1Foil AS pr_avg1Foil,
           p.avg7Foil AS pr_avg7Foil, p.avg30Foil AS pr_avg30Foil
           FROM deck_cards d LEFT JOIN prices p ON p.idProduct = d.cardmarketId
           WHERE d.deckId = :deckId"""
    )
    fun observeCards(deckId: Long): Flow<List<DeckCardRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deck: Deck)

    @Update
    suspend fun update(deck: Deck)

    @Insert
    suspend fun insertCards(cards: List<DeckCard>)

    @Query("DELETE FROM deck_cards WHERE deckId = :deckId")
    suspend fun deleteCards(deckId: Long)

    @Query("SELECT * FROM deck_cards WHERE deckId = :deckId AND addedInApp = 1")
    suspend fun cardsAddedInApp(deckId: Long): List<DeckCard>

    @Query("SELECT * FROM deck_cards WHERE deckId = :deckId")
    suspend fun cards(deckId: Long): List<DeckCard>

    @Query("DELETE FROM deck_cards WHERE id = :id")
    suspend fun deleteCard(id: Long)

    @Query("DELETE FROM deck_cards WHERE id IN (:ids)")
    suspend fun deleteCardsById(ids: List<Long>)

    @Insert
    suspend fun insertCard(card: DeckCard): Long

    @Query("UPDATE deck_cards SET quantity = quantity + :n WHERE id = :id")
    suspend fun addQuantity(id: Long, n: Int)

    @Query("SELECT archidektId FROM decks")
    suspend fun ids(): List<Long>

    @Query("DELETE FROM decks WHERE archidektId = :id")
    suspend fun delete(id: Long)

    /** Saves a freshly imported deck, replacing its Archidekt list but keeping cards added in the app. */
    @Transaction
    suspend fun replace(deck: Deck, cards: List<DeckCard>) {
        val kept = cardsAddedInApp(deck.archidektId)
        upsert(deck)
        deleteCards(deck.archidektId)
        insertCards(cards + kept.map { it.copy(id = 0) })
    }
}

@Database(
    entities = [
        PriceEntity::class, CollectionItem::class, Trade::class, TradeItem::class, Deck::class, DeckCard::class,
        Binder::class, ScannedCard::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun priceDao(): PriceDao
    abstract fun collectionDao(): CollectionDao
    abstract fun tradeDao(): TradeDao
    abstract fun deckDao(): DeckDao
    abstract fun binderDao(): BinderDao
    abstract fun scanDao(): ScanDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mtgtrader.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /** Version 5 (app 1.7): binders, the Scan tab's waiting list, and cards added to decks in the app. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `collection` ADD COLUMN `binderId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_collection_scryfallId_foil_etched_condition_language`")
                db.execSQL("ALTER TABLE `trades` ADD COLUMN `binderId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `trade_items` ADD COLUMN `appliedBinderId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `deck_cards` ADD COLUMN `addedInApp` INTEGER NOT NULL DEFAULT 0")
                V5_TABLES_SQL.forEach(db::execSQL)
            }
        }

        /** Version 4 (app 1.6): Commander decks imported from Archidekt. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DECK_TABLES_SQL.forEach(db::execSQL)
            }
        }

        /** Version 3 (app 1.5): the flavor name printed on cards like "Barrow-Downs" (Bojuka Bog). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("collection", "trade_items")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `flavorName` TEXT")
                }
            }
        }

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

/** New tables and index of version 5, exactly as Room creates them (copied from the generated AppDatabase_Impl). */
private val V5_TABLES_SQL = listOf(
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_collection_scryfallId_foil_etched_condition_language_binderId` " +
        "ON `collection` (`scryfallId`, `foil`, `etched`, `condition`, `language`, `binderId`)",
    "CREATE TABLE IF NOT EXISTS `binders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`createdAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `scans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `foil` INTEGER NOT NULL, " +
        "`etched` INTEGER NOT NULL, `condition` TEXT NOT NULL, `language` TEXT NOT NULL, `quantity` INTEGER NOT NULL, " +
        "`exactPrinting` INTEGER NOT NULL, `scannedAt` INTEGER NOT NULL, `scryfallId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
        "`setCode` TEXT NOT NULL, `setName` TEXT NOT NULL, `collectorNumber` TEXT NOT NULL, `rarity` TEXT NOT NULL, " +
        "`imageUrl` TEXT, `cardmarketId` INTEGER, `fallbackEur` REAL, `fallbackEurFoil` REAL, `hasNonFoil` INTEGER NOT NULL, " +
        "`hasFoil` INTEGER NOT NULL, `foilType` TEXT, `hasEtched` INTEGER NOT NULL DEFAULT 0, `flavorName` TEXT)",
)

/** The deck tables exactly as Room creates them for a new install (copied from the generated AppDatabase_Impl). */
private val DECK_TABLES_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `decks` (`archidektId` INTEGER NOT NULL, `name` TEXT NOT NULL, `owner` TEXT NOT NULL, " +
        "`commanders` TEXT NOT NULL, `commanderScryfallId` TEXT, `artUrl` TEXT, `colorIdentity` TEXT NOT NULL, " +
        "`cardCount` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL, `saltId` TEXT, `powerLevel` REAL, " +
        "`bracketRealistic` INTEGER, `bracketBaseline` INTEGER, `saltPercent` REAL, `archetype` TEXT, `scoredAt` INTEGER, " +
        "`scoreError` TEXT, PRIMARY KEY(`archidektId`))",
    "CREATE TABLE IF NOT EXISTS `deck_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deckId` INTEGER NOT NULL, " +
        "`quantity` INTEGER NOT NULL, `category` TEXT NOT NULL, `types` TEXT NOT NULL, `cmc` REAL NOT NULL, " +
        "`commander` INTEGER NOT NULL, `foil` INTEGER NOT NULL, `etched` INTEGER NOT NULL DEFAULT 0, " +
        "`gameChanger` INTEGER NOT NULL, `scryfallId` TEXT NOT NULL, `name` TEXT NOT NULL, `setCode` TEXT NOT NULL, " +
        "`setName` TEXT NOT NULL, `collectorNumber` TEXT NOT NULL, `rarity` TEXT NOT NULL, `imageUrl` TEXT, " +
        "`cardmarketId` INTEGER, `fallbackEur` REAL, `fallbackEurFoil` REAL, `hasNonFoil` INTEGER NOT NULL, " +
        "`hasFoil` INTEGER NOT NULL, `foilType` TEXT, `hasEtched` INTEGER NOT NULL DEFAULT 0, `flavorName` TEXT, " +
        "FOREIGN KEY(`deckId`) REFERENCES `decks`(`archidektId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_deck_cards_deckId` ON `deck_cards` (`deckId`)",
)
