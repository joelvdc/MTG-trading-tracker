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

    @Query("SELECT COUNT(*) FROM prices")
    suspend fun countNow(): Int
}

@Dao
interface CollectionDao {
    @Query(
        """SELECT c.*, p.idProduct AS pr_idProduct, p.avg AS pr_avg, p.low AS pr_low, p.trend AS pr_trend,
           p.avg1 AS pr_avg1, p.avg7 AS pr_avg7, p.avg30 AS pr_avg30, p.avgFoil AS pr_avgFoil,
           p.lowFoil AS pr_lowFoil, p.trendFoil AS pr_trendFoil, p.avg1Foil AS pr_avg1Foil,
           p.avg7Foil AS pr_avg7Foil, p.avg30Foil AS pr_avg30Foil,
           ci.scryfallId AS ci_scryfallId, ci.oracleId AS ci_oracleId, ci.colors AS ci_colors, ci.colorIdentity AS ci_colorIdentity, ci.typeLine AS ci_typeLine, ci.cmc AS ci_cmc, ci.edhrecRank AS ci_edhrecRank, ci.fetchedAt AS ci_fetchedAt
           FROM collection c LEFT JOIN prices p ON p.idProduct =
             CASE WHEN c.foil = 1 AND c.cardmarketFoilId IS NOT NULL THEN c.cardmarketFoilId ELSE c.cardmarketId END
           LEFT JOIN card_info ci ON ci.scryfallId = c.scryfallId
           ORDER BY c.name COLLATE NOCASE"""
    )
    fun observeAll(): Flow<List<CollectionRow>>

    @Query(
        """SELECT c.*, p.idProduct AS pr_idProduct, p.avg AS pr_avg, p.low AS pr_low, p.trend AS pr_trend,
           p.avg1 AS pr_avg1, p.avg7 AS pr_avg7, p.avg30 AS pr_avg30, p.avgFoil AS pr_avgFoil,
           p.lowFoil AS pr_lowFoil, p.trendFoil AS pr_trendFoil, p.avg1Foil AS pr_avg1Foil,
           p.avg7Foil AS pr_avg7Foil, p.avg30Foil AS pr_avg30Foil,
           ci.scryfallId AS ci_scryfallId, ci.oracleId AS ci_oracleId, ci.colors AS ci_colors, ci.colorIdentity AS ci_colorIdentity, ci.typeLine AS ci_typeLine, ci.cmc AS ci_cmc, ci.edhrecRank AS ci_edhrecRank, ci.fetchedAt AS ci_fetchedAt
           FROM collection c LEFT JOIN prices p ON p.idProduct =
             CASE WHEN c.foil = 1 AND c.cardmarketFoilId IS NOT NULL THEN c.cardmarketFoilId ELSE c.cardmarketId END
           LEFT JOIN card_info ci ON ci.scryfallId = c.scryfallId
           ORDER BY c.name COLLATE NOCASE"""
    )
    suspend fun allWithPrices(): List<CollectionRow>

    @Query("SELECT scryfallId, SUM(quantity) AS qty FROM collection GROUP BY scryfallId")
    fun observeOwned(): Flow<List<OwnedCount>>

    /** Copies owned per card name, over every printing, finish and binder. */
    @Query("SELECT name, SUM(quantity) AS qty FROM collection GROUP BY name")
    suspend fun ownedByName(): List<NameCount>

    @Query("SELECT name, SUM(quantity) AS qty FROM collection GROUP BY name")
    fun observeOwnedByName(): Flow<List<NameCount>>

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
           FROM scans s LEFT JOIN prices p ON p.idProduct =
             CASE WHEN s.foil = 1 AND s.cardmarketFoilId IS NOT NULL THEN s.cardmarketFoilId ELSE s.cardmarketId END
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
           FROM deck_cards d LEFT JOIN prices p ON p.idProduct =
             CASE WHEN d.foil = 1 AND d.cardmarketFoilId IS NOT NULL THEN d.cardmarketFoilId ELSE d.cardmarketId END
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

    /** Copies per card name over all decks (for what the collection must keep). */
    @Query("SELECT name, SUM(quantity) AS qty FROM deck_cards GROUP BY name")
    suspend fun cardCounts(): List<NameCount>

    @Query("SELECT DISTINCT name FROM deck_cards")
    fun observeCardNames(): Flow<List<String>>

    /** Every card name with the decks it's in (and how many copies each), for "used in several decks". */
    @Query(
        """SELECT c.name AS name, c.deckId AS deckId, d.name AS deckName, SUM(c.quantity) AS quantity, MAX(c.imageUrl) AS imageUrl
           FROM deck_cards c JOIN decks d ON d.archidektId = c.deckId
           GROUP BY c.name COLLATE NOCASE, c.deckId ORDER BY d.name COLLATE NOCASE"""
    )
    fun observeUsage(): Flow<List<DeckUse>>

    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Deck>

    @Query("SELECT * FROM decks WHERE archidektUpdatedAt IS NULL")
    suspend fun withoutUpdateDate(): List<Deck>

    @Query("UPDATE decks SET archidektUpdatedAt = :at WHERE archidektId = :id")
    suspend fun setUpdatedAt(id: Long, at: Long)

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
        Binder::class, ScannedCard::class, SyncDeletion::class, SyncControl::class,
        WishlistItem::class, ValueSnapshot::class, CmProduct::class, CmSetExpansions::class,
        CardInfo::class, RecCache::class, TradeSkip::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun priceDao(): PriceDao
    abstract fun collectionDao(): CollectionDao
    abstract fun tradeDao(): TradeDao
    abstract fun deckDao(): DeckDao
    abstract fun binderDao(): BinderDao
    abstract fun scanDao(): ScanDao
    abstract fun syncDao(): SyncDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun cardInfoDao(): CardInfoDao
    abstract fun recDao(): RecDao
    abstract fun tradeSkipDao(): TradeSkipDao
    abstract fun valueHistoryDao(): ValueHistoryDao
    abstract fun catalogDao(): CatalogDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mtgtrader.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
                })
                .build()

        /** Version 10 (app 1.16): wishlist, value history, notes and purchase price, Cardmarket's product list. */
        /** Version 11 (app 1.18): card details (colours, types, popularity), cached recommendations, skipped trade binder suggestions. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                V11_TABLES_SQL.forEach(db::execSQL)
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (t in listOf("collection", "trade_items", "scans", "deck_cards")) {
                    db.execSQL("ALTER TABLE `$t` ADD COLUMN `cardmarketFoilId` INTEGER")
                }
                db.execSQL("ALTER TABLE `collection` ADD COLUMN `notes` TEXT")
                db.execSQL("ALTER TABLE `collection` ADD COLUMN `purchasePrice` REAL")
                V10_TABLES_SQL.forEach(db::execSQL)
            }
        }

        /** Version 9 (app 1.12): power level from ScrollVault. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `svPowerLevel` REAL")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `scrollVault` TEXT")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `svAt` INTEGER")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `svError` TEXT")
            }
        }

        /** Version 8 (app 1.12 beta 2): power level from edhpowerlevel.com and the rule-zero card details. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `edhPowerLevel` REAL")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `edhPowerAt` INTEGER")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `edhPowerError` TEXT")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `saltCard` TEXT")
            }
        }

        /** Version 7 (app 1.12): sync ids, change times and deletions for syncing through Nextcloud. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (t in SyncSchema.UID_TABLES) {
                    db.execSQL("ALTER TABLE `$t` ADD COLUMN `uid` TEXT")
                    db.execSQL("ALTER TABLE `$t` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE `$t` SET uid = lower(hex(randomblob(16))), updatedAt = ${SyncSchema.NOW}")
                }
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `decks` SET updatedAt = ${SyncSchema.NOW}")
                V7_TABLES_SQL.forEach(db::execSQL)
            }
        }

        /** Version 6 (app 1.8): when each deck was last changed on Archidekt, for sorting. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `archidektUpdatedAt` INTEGER")
            }
        }

        /** Version 5 (app 1.7): binders, the Scan tab's waiting list, and cards added to decks in the app. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
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
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DECK_TABLES_SQL.forEach(db::execSQL)
            }
        }

        /** Version 3 (app 1.5): the flavor name printed on cards like "Barrow-Downs" (Bojuka Bog). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("collection", "trade_items")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `flavorName` TEXT")
                }
            }
        }

        /** Version 2 (app 1.2): special foil type, etched finish, and etched copies kept apart in the collection. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
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

/** New tables of version 11, exactly as Room creates them (from the exported schema, schemas/…/11.json). */
private val V11_TABLES_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `card_info` (`scryfallId` TEXT NOT NULL, `oracleId` TEXT, `colors` TEXT NOT NULL, " +
        "`colorIdentity` TEXT NOT NULL, `typeLine` TEXT NOT NULL, `cmc` REAL NOT NULL, `edhrecRank` INTEGER, " +
        "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`scryfallId`))",
    "CREATE TABLE IF NOT EXISTS `recommendations` (`deckId` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
        "`fetchedAt` INTEGER NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`deckId`, `source`))",
    "CREATE TABLE IF NOT EXISTS `trade_skips` (`key` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`key`))",
)

/** New tables of version 10, exactly as Room creates them (from the exported schema, schemas/…/10.json). */
private val V10_TABLES_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `wishlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `foil` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, " +
        "`anyPrinting` INTEGER NOT NULL, `notes` TEXT, `addedAt` INTEGER NOT NULL, `ownedAtAdd` INTEGER NOT NULL DEFAULT 0, `uid` TEXT, `updatedAt` INTEGER NOT NULL DEFAULT 0, " +
        "`scryfallId` TEXT NOT NULL, `name` TEXT NOT NULL, `setCode` TEXT NOT NULL, `setName` TEXT NOT NULL, `collectorNumber` TEXT NOT NULL, " +
        "`rarity` TEXT NOT NULL, `imageUrl` TEXT, `cardmarketId` INTEGER, `fallbackEur` REAL, `fallbackEurFoil` REAL, `hasNonFoil` INTEGER NOT NULL, " +
        "`hasFoil` INTEGER NOT NULL, `foilType` TEXT, `hasEtched` INTEGER NOT NULL DEFAULT 0, `flavorName` TEXT, `cardmarketFoilId` INTEGER)",
    "CREATE INDEX IF NOT EXISTS `index_wishlist_name` ON `wishlist` (`name`)",
    "CREATE INDEX IF NOT EXISTS `index_wishlist_uid` ON `wishlist` (`uid`)",
    "CREATE TABLE IF NOT EXISTS `value_history` (`day` TEXT NOT NULL, `cards` INTEGER NOT NULL, `values` TEXT NOT NULL, PRIMARY KEY(`day`))",
    "CREATE TABLE IF NOT EXISTS `cm_products` (`idProduct` INTEGER NOT NULL, `name` TEXT NOT NULL, `idExpansion` INTEGER NOT NULL, PRIMARY KEY(`idProduct`))",
    "CREATE INDEX IF NOT EXISTS `index_cm_products_idExpansion` ON `cm_products` (`idExpansion`)",
    "CREATE INDEX IF NOT EXISTS `index_cm_products_name` ON `cm_products` (`name`)",
    "CREATE TABLE IF NOT EXISTS `cm_set_expansions` (`setCode` TEXT NOT NULL, `idExpansions` TEXT NOT NULL, PRIMARY KEY(`setCode`))",
)

/** New tables and index of version 7, exactly as Room creates them (copied from the generated AppDatabase_Impl). */
private val V7_TABLES_SQL = listOf(
    "CREATE INDEX IF NOT EXISTS `index_collection_uid` ON `collection` (`uid`)",
    "CREATE TABLE IF NOT EXISTS `sync_deletions` (`uid` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
    "CREATE TABLE IF NOT EXISTS `sync_control` (`id` INTEGER NOT NULL, `applying` INTEGER NOT NULL, `changes` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
)

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
