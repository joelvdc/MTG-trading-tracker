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

    /** Every stack of a card (all printings, finishes and binders), for the card page. Since 1.21. */
    @Query(
        """SELECT c.*, p.idProduct AS pr_idProduct, p.avg AS pr_avg, p.low AS pr_low, p.trend AS pr_trend,
           p.avg1 AS pr_avg1, p.avg7 AS pr_avg7, p.avg30 AS pr_avg30, p.avgFoil AS pr_avgFoil,
           p.lowFoil AS pr_lowFoil, p.trendFoil AS pr_trendFoil, p.avg1Foil AS pr_avg1Foil,
           p.avg7Foil AS pr_avg7Foil, p.avg30Foil AS pr_avg30Foil,
           ci.scryfallId AS ci_scryfallId, ci.oracleId AS ci_oracleId, ci.colors AS ci_colors, ci.colorIdentity AS ci_colorIdentity, ci.typeLine AS ci_typeLine, ci.cmc AS ci_cmc, ci.edhrecRank AS ci_edhrecRank, ci.fetchedAt AS ci_fetchedAt
           FROM collection c LEFT JOIN prices p ON p.idProduct =
             CASE WHEN c.foil = 1 AND c.cardmarketFoilId IS NOT NULL THEN c.cardmarketFoilId ELSE c.cardmarketId END
           LEFT JOIN card_info ci ON ci.scryfallId = c.scryfallId
           WHERE c.name = :name COLLATE NOCASE
           ORDER BY c.setName, c.collectorNumber"""
    )
    fun observeByName(name: String): Flow<List<CollectionRow>>

    @Query("SELECT scryfallId, SUM(quantity) AS qty FROM collection GROUP BY scryfallId")
    fun observeOwned(): Flow<List<OwnedCount>>

    /** Copies owned per card name, over every printing, finish and binder. */
    @Query("SELECT name, SUM(quantity) AS qty FROM collection GROUP BY name")
    suspend fun ownedByName(): List<NameCount>

    @Query("SELECT name, SUM(quantity) AS qty FROM collection GROUP BY name")
    fun observeOwnedByName(): Flow<List<NameCount>>

    @Query(
        """SELECT * FROM collection WHERE scryfallId = :sid AND foil = :foil AND etched = :etched
           AND condition = :cond AND language = :lang AND binderId = :binderId AND signed = :signed AND altered = :altered LIMIT 1"""
    )
    suspend fun find(
        sid: String, foil: Boolean, etched: Boolean, cond: String, lang: String, binderId: Long, signed: Boolean, altered: Boolean,
    ): CollectionItem?

    /** The stack [item] would merge into: same card, finish, condition, language, binder and marks. */
    suspend fun sameStack(item: CollectionItem): CollectionItem? =
        find(item.card.scryfallId, item.foil, item.etched, item.condition, item.language, item.binderId, item.signed, item.altered)

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
           WHERE d.deckId = :deckId AND d.maybeboard = 0"""
    )
    fun observeCards(deckId: Long): Flow<List<DeckCardRow>>

    /** The deck's maybeboard (and other categories left out of the deck on Archidekt), with prices. Since 1.42. */
    @Query(
        """SELECT d.*, p.idProduct AS pr_idProduct, p.avg AS pr_avg, p.low AS pr_low, p.trend AS pr_trend,
           p.avg1 AS pr_avg1, p.avg7 AS pr_avg7, p.avg30 AS pr_avg30, p.avgFoil AS pr_avgFoil,
           p.lowFoil AS pr_lowFoil, p.trendFoil AS pr_trendFoil, p.avg1Foil AS pr_avg1Foil,
           p.avg7Foil AS pr_avg7Foil, p.avg30Foil AS pr_avg30Foil
           FROM deck_cards d LEFT JOIN prices p ON p.idProduct =
             CASE WHEN d.foil = 1 AND d.cardmarketFoilId IS NOT NULL THEN d.cardmarketFoilId ELSE d.cardmarketId END
           WHERE d.deckId = :deckId AND d.maybeboard = 1"""
    )
    fun observeMaybeboard(deckId: Long): Flow<List<DeckCardRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deck: Deck)

    @Update
    suspend fun update(deck: Deck)

    @Insert
    suspend fun insertCards(cards: List<DeckCard>)

    @Query("DELETE FROM deck_cards WHERE deckId = :deckId")
    suspend fun deleteCards(deckId: Long)

    @Query("SELECT * FROM deck_cards WHERE deckId = :deckId AND addedInApp = 1 AND maybeboard = 0")
    suspend fun cardsAddedInApp(deckId: Long): List<DeckCard>

    /** The cards in the deck itself (not the maybeboard). */
    @Query("SELECT * FROM deck_cards WHERE deckId = :deckId AND maybeboard = 0")
    suspend fun cards(deckId: Long): List<DeckCard>

    @Query("SELECT * FROM deck_cards WHERE id = :id")
    suspend fun card(id: Long): DeckCard?

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

    /** Every line of the deck, the maybeboard's too. Since 1.46. */
    @Query("SELECT * FROM deck_cards WHERE deckId = :deckId")
    suspend fun allLines(deckId: Long): List<DeckCard>

    @Query("DELETE FROM deck_cards WHERE deckId = :deckId AND lineUid IN (:uids)")
    suspend fun deleteLines(deckId: Long, uids: List<String>)

    @Query("UPDATE deck_cards SET lineUid = :uid WHERE id = :id")
    suspend fun setLineUid(id: Long, uid: String)

    @Query("UPDATE deck_cards SET relationId = :relationId, archidektCardId = :cardId WHERE deckId = :deckId AND lineUid = :uid")
    suspend fun setArchidektIds(deckId: Long, uid: String, relationId: Long?, cardId: Long?)

    @Query("UPDATE deck_cards SET archidektCardId = :cardId WHERE deckId = :deckId AND lineUid = :uid")
    suspend fun setArchidektCardId(deckId: Long, uid: String, cardId: Long)

    @Query("UPDATE deck_cards SET deckId = :to WHERE deckId = :from")
    suspend fun moveCards(from: Long, to: Long)

    /** Copies per card name over all decks (for what the collection must keep). */
    @Query("SELECT name, SUM(quantity) AS qty FROM deck_cards WHERE maybeboard = 0 GROUP BY name")
    suspend fun cardCounts(): List<NameCount>

    @Query("SELECT DISTINCT name FROM deck_cards WHERE maybeboard = 0")
    fun observeCardNames(): Flow<List<String>>

    /** Every card name with the decks it's in (and how many copies each), for "used in several decks". */
    @Query(
        """SELECT c.name AS name, c.deckId AS deckId, d.name AS deckName, SUM(c.quantity) AS quantity, MAX(c.imageUrl) AS imageUrl
           FROM deck_cards c JOIN decks d ON d.archidektId = c.deckId
           WHERE c.maybeboard = 0
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
        CardInfo::class, RecCache::class, TradeSkip::class, SourcePrice::class, PurchaseOrder::class,
        WishPrice::class, CtBlueprint::class, DeckEdit::class,
    ],
    version = 22,
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
    abstract fun sourcePriceDao(): SourcePriceDao
    abstract fun orderDao(): OrderDao
    abstract fun offlineImagesDao(): OfflineImagesDao
    abstract fun wishPriceDao(): WishPriceDao
    abstract fun ctBlueprintDao(): CtBlueprintDao
    abstract fun deckEditDao(): DeckEditDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mtgtrader.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
                })
                .build()

        /**
         * Version 22 (app 1.46): editing decks. Deck lines keep all their Archidekt categories, Archidekt's ids for
         * the printing and the line, its colour label and an id of their own; decks keep their Archidekt folder,
         * visibility and categories, and where a local deck came from (all synced; the sync file's format went to
         * 9 with them). Edits waiting to be sent to Archidekt are kept on this phone only. Adding the columns
         * doesn't touch the rows, so it doesn't count as a change to sync.
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `deck_cards` ADD COLUMN `categories` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `deck_cards` ADD COLUMN `archidektCardId` INTEGER")
                db.execSQL("ALTER TABLE `deck_cards` ADD COLUMN `relationId` INTEGER")
                db.execSQL("ALTER TABLE `deck_cards` ADD COLUMN `label` TEXT")
                db.execSQL("ALTER TABLE `deck_cards` ADD COLUMN `lineUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `folderId` INTEGER")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `folderName` TEXT")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `visibility` TEXT")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `categoryInfo` TEXT")
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `origin` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `deck_edits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deckId` INTEGER NOT NULL, " +
                        "`lineUid` TEXT NOT NULL, `relationId` INTEGER, `archidektCardId` INTEGER, `name` TEXT NOT NULL, `categories` TEXT, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_deck_edits_deckId_lineUid` ON `deck_edits` (`deckId`, `lineUid`)")
            }
        }

        /**
         * Version 21 (app 1.44): what the deck stats need in the card details kept from Scryfall: the mana a card
         * makes, its rules text, its tokens, and power and toughness (a cache on the phone, not synced, so the sync
         * file's format stays 8). Decks' cards get them at the next details update.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `card_info` ADD COLUMN `producedMana` TEXT")
                db.execSQL("ALTER TABLE `card_info` ADD COLUMN `oracleText` TEXT")
                db.execSQL("ALTER TABLE `card_info` ADD COLUMN `tokens` TEXT")
                db.execSQL("ALTER TABLE `card_info` ADD COLUMN `powerToughness` TEXT")
            }
        }

        /**
         * Version 20 (app 1.43): cards' mana costs, in the card details kept from Scryfall (a cache on the phone,
         * not synced, so the sync file's format stays 8). Decks' cards get theirs at the next details update.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `card_info` ADD COLUMN `manaCost` TEXT")
            }
        }

        /**
         * Version 19 (app 1.42): a deck's maybeboard cards, kept apart from the deck's own (synced with the deck;
         * the sync file's format went to 8 with it). Adding the column doesn't touch the rows, so it doesn't
         * count as a change to sync.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `deck_cards` ADD COLUMN `maybeboard` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Version 18 (app 1.39): the currency of a wishlist card's alert level, and where a received card went
         * into the collection (both synced; the sync file's format went to 7 with them). Adding the columns
         * doesn't touch the rows, so it doesn't count as a change to sync.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `wishlist` ADD COLUMN `alertCurrency` TEXT")
                db.execSQL("ALTER TABLE `wishlist` ADD COLUMN `receivedFrom` TEXT")
            }
        }

        /**
         * Version 17 (app 1.38): wishlist cards marked as received (synced; the sync file's format went to 6
         * with it). Adding the column doesn't touch the rows, so it doesn't count as a change to sync.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `wishlist` ADD COLUMN `receivedAt` INTEGER")
            }
        }

        /**
         * Version 16 (app 1.37): wishlist price alerts. The level is synced (the sync file's format went to
         * 5 with it); the prices found for it and CardTrader's blueprints stay on the phone. Adding the
         * column doesn't touch the rows, so it doesn't count as a change to sync.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `wishlist` ADD COLUMN `alertBelow` REAL")
                V16_TABLES_SQL.forEach(db::execSQL)
            }
        }

        /**
         * Version 15 (app 1.35): wishlist cards marked as ordered (synced; the sync file's format went to 4
         * with it). Adding the column doesn't touch the rows, so it doesn't count as a change to sync.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `wishlist` ADD COLUMN `orderedAt` INTEGER")
            }
        }

        /** Version 14 (app 1.33): remembered orders (synced; the sync file's format went to 3 with it). */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `orders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `shop` TEXT NOT NULL, `number` TEXT, " +
                        "`title` TEXT NOT NULL, `date` TEXT NOT NULL, `paid` REAL NOT NULL, `cards` INTEGER NOT NULL, `lines` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, `uid` TEXT, `updatedAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_uid` ON `orders` (`uid`)")
            }
        }

        /** Version 13 (app 1.29): TCGplayer's and Card Kingdom's prices, kept on the phone (not synced). */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `source_prices` (`scryfallId` TEXT NOT NULL, `source` TEXT NOT NULL, `finish` TEXT NOT NULL, " +
                        "`price` REAL, `nm` REAL, `ex` REAL, `vg` REAL, `g` REAL, `buy` REAL, `url` TEXT, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`scryfallId`, `source`, `finish`))"
                )
            }
        }

        /**
         * Version 12 (app 1.27): signed and altered copies, each kept as a stack of their own. Existing
         * cards are neither; adding the columns doesn't touch the rows, so it doesn't count as a change to sync.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `collection` ADD COLUMN `signed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `collection` ADD COLUMN `altered` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_collection_scryfallId_foil_etched_condition_language_binderId`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_collection_scryfallId_foil_etched_condition_language_binderId_signed_altered` " +
                        "ON `collection` (`scryfallId`, `foil`, `etched`, `condition`, `language`, `binderId`, `signed`, `altered`)"
                )
            }
        }

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

/** New tables of version 16, exactly as Room creates them (from the exported schema, schemas/…/16.json). */
private val V16_TABLES_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `wish_prices` (`wishId` INTEGER NOT NULL, `basis` TEXT NOT NULL, `price` REAL, `scryfallId` TEXT, " +
        "`setCode` TEXT, `printings` TEXT, `printingsOf` TEXT, `printingsAt` INTEGER NOT NULL, `ctPrice` REAL, `ctBlueprintId` INTEGER, " +
        "`ctSetCode` TEXT, `ctCondition` TEXT, `ctLanguage` TEXT, `ctAnyPrice` REAL, `ctZeroOver` REAL, `ctFilter` TEXT, `ctAt` INTEGER NOT NULL, `alertedLevel` REAL, " +
        "`checkedAt` INTEGER NOT NULL, PRIMARY KEY(`wishId`))",
    "CREATE TABLE IF NOT EXISTS `ct_blueprints` (`scryfallId` TEXT NOT NULL, `blueprintId` INTEGER NOT NULL, `expansionId` INTEGER NOT NULL, " +
        "PRIMARY KEY(`scryfallId`))",
)

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
