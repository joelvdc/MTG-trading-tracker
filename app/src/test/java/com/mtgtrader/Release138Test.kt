package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.Binder
import com.mtgtrader.data.BackupFile
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.Finish
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.ReceivedClear
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.Side
import com.mtgtrader.data.SwipeAction
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.WishPrice
import com.mtgtrader.data.WishlistItem
import com.mtgtrader.data.WishlistRow
import com.mtgtrader.ui.wishLabel
import com.mtgtrader.ui.wishPriceLine
import com.mtgtrader.ui.wishSections
import com.mtgtrader.ui.wishShareText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.38: the wishlist's Received section and sections, swiping and selecting cards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release138Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var repo: MtgRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        val http = OkHttpClient()
        repo = MtgRepository(db, ScryfallApi(http), PriceGuideRepository(context, http, db, Settings(context)), CoroutineScope(Dispatchers.Unconfined))
        context.getSharedPreferences("settings", 0).edit().clear().commit()
    }

    @After
    fun tearDown() = db.close()

    private fun ref(name: String, id: String = name.lowercase().replace(' ', '-'), set: String = "m21") =
        CardRef(id, name, set, set.uppercase(), "1", "rare", null, null, 1.0, 2.0, true, true)

    // 15 October 2026, 12:00 UTC (the tests run in UTC with English formats).
    private val oct15 = 1_792_065_600_000L
    private val day = 24L * 3600 * 1000

    // ---- The wishlist ---------------------------------------------------------------------

    @Test
    fun receivedCardsLeaveTheWishlistButStayAsARecord() = runBlocking {
        val sol = db.wishlistDao().insert(WishlistItem(card = ref("Sol Ring"), quantity = 2, orderedAt = oct15))
        val tithe = db.wishlistDao().insert(WishlistItem(card = ref("Smothering Tithe")))
        assertEquals(3, db.wishlistDao().observeCount().first())

        val (n, undo) = repo.receiveWishlist(listOf(sol), toCollection = false, at = oct15 + day)
        assertEquals(1, n)
        val got = db.wishlistDao().byId(sol)!!
        assertEquals(oct15 + day, got.receivedAt)
        // Not counted, not wanted any more: no ★ in trades, not merged into when added again.
        assertEquals(1, db.wishlistDao().observeCount().first())
        assertEquals(mapOf("smothering tithe" to 1), repo.wishlistNames())
        assertTrue(db.wishlistDao().byName("Sol Ring").isEmpty())
        repo.addToWishlist(ref("Sol Ring"))
        assertEquals(2, db.wishlistDao().all().count { it.card.name == "Sol Ring" })
        // Nothing went into the collection.
        assertTrue(db.collectionDao().all().isEmpty())

        undo()
        assertNull(db.wishlistDao().byId(sol)!!.receivedAt)
        // Received twice is once.
        repo.receiveWishlist(listOf(sol, tithe), toCollection = false)
        assertEquals(0, repo.receiveWishlist(listOf(sol, tithe), toCollection = false).first)
        // Back on the wishlist.
        assertEquals(2, repo.unreceiveWishlist(listOf(sol, tithe)).first)
        assertTrue(db.wishlistDao().all().filter { it.id in setOf(sol, tithe) }.all { it.receivedAt == null })
    }

    @Test
    fun receivedCardsCanGoIntoTheCollection() = runBlocking {
        val sol = db.wishlistDao().insert(WishlistItem(card = ref("Sol Ring"), quantity = 2, foil = true))
        // A card added offline has no printing yet: only marked.
        val pending = db.wishlistDao().insert(WishlistItem(card = com.mtgtrader.data.PendingCard.ref("Brainstorm")))
        val (n, undo) = repo.receiveWishlist(listOf(sol, pending), toCollection = true)
        assertEquals(2, n)
        val stack = db.collectionDao().all().single()
        assertEquals("Sol Ring", stack.card.name)
        assertEquals(2, stack.quantity)
        assertTrue(stack.foil)
        assertEquals(Binder.UNSORTED, stack.binderId)
        assertEquals("NM", stack.condition)
        // Undo takes them out again.
        undo()
        assertTrue(db.collectionDao().all().isEmpty())
        assertNull(db.wishlistDao().byId(sol)!!.receivedAt)
    }

    @Test
    fun batchActionsOnTheWishlistCanBeUndone() = runBlocking {
        val a = db.wishlistDao().insert(WishlistItem(card = ref("Sol Ring")))
        val b = db.wishlistDao().insert(WishlistItem(card = ref("Arcane Signet")))
        val c = db.wishlistDao().insert(WishlistItem(card = ref("Rhystic Study"), receivedAt = oct15))
        val (ordered, undoOrdered) = repo.setWishlistOrdered(listOf(a, b, c), oct15)
        // A received card isn't ordered again.
        assertEquals(2, ordered)
        assertNull(db.wishlistDao().byId(c)!!.orderedAt)
        assertEquals(listOf(a, b).sorted(), repo.orderedWishlistIds().sorted())
        undoOrdered()
        assertTrue(repo.orderedWishlistIds().isEmpty())

        val (deleted, undoDelete) = repo.deleteWishlistItems(listOf(a, c))
        assertEquals(2, deleted)
        assertEquals(listOf(b), db.wishlistDao().all().map { it.id })
        undoDelete()
        assertEquals(3, db.wishlistDao().all().size)
        // The deleted cards' sync ids are back: no deletion goes out to the other phone.
        assertTrue(db.syncDao().deletions().none { d -> db.wishlistDao().all().any { it.uid == d.uid } })
    }

    @Test
    fun cardsYouNowOwnBecomeReceived() = runBlocking {
        // Wanted 3 Sol Rings with none owned; now 1 is in the collection. 1 Brainstorm wanted, now owned.
        val sol = db.wishlistDao().insert(WishlistItem(card = ref("Sol Ring"), quantity = 3))
        val storm = db.wishlistDao().insert(WishlistItem(card = ref("Brainstorm")))
        db.wishlistDao().insert(WishlistItem(card = ref("Counterspell")))
        repo.addToCollection(ref("Sol Ring", set = "c21"), Finish.NONFOIL, "NM", "EN", 1)
        repo.addToCollection(ref("Brainstorm"), Finish.NONFOIL, "NM", "EN", 2)

        val got = repo.ownedSinceAdding().associate { it.first.card.name to it.second }
        assertEquals(mapOf("Sol Ring" to 1, "Brainstorm" to 1), got)

        val (n, undo) = repo.receiveOwned(at = oct15)
        assertEquals(2, n)
        val all = db.wishlistDao().all()
        // Brainstorm: received. Sol Ring: 2 still wanted, and the 1 got is a received entry of its own.
        assertEquals(oct15, all.single { it.id == storm }.receivedAt)
        assertEquals(2, all.single { it.id == sol }.quantity)
        assertNull(all.single { it.id == sol }.receivedAt)
        assertEquals(1, all.single { it.card.name == "Sol Ring" && it.receivedAt != null }.quantity)
        // Nothing more is owned since adding now.
        assertTrue(repo.ownedSinceAdding().isEmpty())
        // The collection isn't touched.
        assertEquals(3, db.collectionDao().all().sumOf { it.quantity })

        undo()
        val back = db.wishlistDao().all()
        assertEquals(3, back.size)
        assertEquals(3, back.single { it.id == sol }.quantity)
        assertTrue(back.all { it.receivedAt == null })
    }

    @Test
    fun receivedCardsAreClearedByHandOrAfterTheChosenTime() = runBlocking {
        val now = oct15
        db.wishlistDao().insert(WishlistItem(card = ref("Old"), receivedAt = now - 40 * day))
        db.wishlistDao().insert(WishlistItem(card = ref("New"), receivedAt = now - 5 * day))
        db.wishlistDao().insert(WishlistItem(card = ref("Wanted")))
        // Kept forever unless chosen.
        assertEquals(ReceivedClear.NEVER, Settings(context).receivedClear.value)
        assertEquals(0, repo.autoClearReceived(ReceivedClear.NEVER, now))
        assertEquals(1, repo.autoClearReceived(ReceivedClear.MONTH, now))
        assertEquals(listOf("New", "Wanted"), db.wishlistDao().all().map { it.card.name }.sorted())
        val (n, undo) = repo.clearReceived()
        assertEquals(1, n)
        assertEquals(listOf("Wanted"), db.wishlistDao().all().map { it.card.name })
        undo()
        assertEquals(2, db.wishlistDao().all().size)
    }

    @Test
    fun theWishlistInSections() {
        val items = listOf(
            WishlistItem(id = 1, card = ref("A")),
            WishlistItem(id = 2, card = ref("B"), orderedAt = oct15),
            WishlistItem(id = 3, card = ref("C")),
            WishlistItem(id = 4, card = ref("D"), receivedAt = oct15 - day),
            WishlistItem(id = 5, card = ref("E"), receivedAt = oct15),
        ).associateBy { it.id }
        val rows = items.values.map { CollectionRow(CollectionItem(id = it.id, card = it.card, foil = false, condition = "", quantity = 1), null) }
        val on = wishSections(rows, { items[it] }, sections = true)
        assertEquals(listOf("To buy" to listOf(1L, 3L), "Ordered" to listOf(2L), "Received" to listOf(5L, 4L)), on.map { it.title to it.rows.map { r -> r.item.id } })
        // Sections off: one list with the ordered cards last, and Received still apart.
        val off = wishSections(rows, { items[it] }, sections = false)
        assertEquals(listOf(null to listOf(1L, 3L, 2L), "Received" to listOf(5L, 4L)), off.map { it.title to it.rows.map { r -> r.item.id } })
        // Empty sections are left out.
        assertEquals(listOf("To buy"), wishSections(rows.take(1), { items[it] }, true).map { it.title })
    }

    @Test
    fun aReceivedRowSaysWhenItCame() {
        val item = WishlistItem(id = 1, card = ref("Sol Ring"), orderedAt = oct15 - day, receivedAt = oct15, alertBelow = 1.0)
        assertEquals("Any printing · ✓ received Oct 15", wishLabel(item, null, now = oct15))
        // No prices to watch, not shared.
        assertNull(wishPriceLine(item, WishPrice(1, basis = "cardmarket:low", price = 0.5), "cardmarket:low", null))
        assertEquals("", wishShareText(listOf(WishlistRow(item, null))))
    }

    // ---- The collection -------------------------------------------------------------------

    private suspend fun stack(name: String, qty: Int, binder: Long = Binder.UNSORTED, condition: String = "NM", set: String = "m21") =
        repo.addToCollection(ref(name, set = set), Finish.NONFOIL, condition, "EN", qty, binder).itemId

    @Test
    fun movingStacksMergesAndUndoPutsThemBack() = runBlocking {
        val decks = repo.createBinder("Decks")
        val a = stack("Sol Ring", 2)
        val b = stack("Sol Ring", 1, binder = decks)
        val c = stack("Brainstorm", 4)
        val (n, undo) = repo.moveStacks(listOf(a, c), decks)
        assertEquals(6, n)
        val inDecks = db.collectionDao().all().filter { it.binderId == decks }
        assertEquals(mapOf("Sol Ring" to 3, "Brainstorm" to 4), inDecks.associate { it.card.name to it.quantity })
        undo()
        val back = db.collectionDao().all().associateBy { it.id }
        assertEquals(2, back.getValue(a).quantity)
        assertEquals(Binder.UNSORTED, back.getValue(a).binderId)
        assertEquals(1, back.getValue(b).quantity)
        assertEquals(Binder.UNSORTED, back.getValue(c).binderId)
        assertEquals(3, back.size)
        // Part of a stack.
        val (moved, _) = repo.moveCopies(c, decks, 1)
        assertEquals(1, moved)
        assertEquals(listOf(3, 1), db.collectionDao().all().filter { it.card.name == "Brainstorm" }.map { it.quantity }.sortedDescending())
    }

    @Test
    fun editingSeveralStacksAtOnce() = runBlocking {
        val a = stack("Sol Ring", 2, condition = "EX")
        val b = stack("Sol Ring", 1, condition = "NM")
        val c = stack("Brainstorm", 1, condition = "GD")
        val (n, undo) = repo.editStacks(listOf(a, b, c), condition = "NM", language = null, purchasePrice = 1.5)
        assertEquals(3, n)
        val now = db.collectionDao().all()
        // The two Sol Ring stacks are the same now: one stack of 3.
        assertEquals(mapOf("Sol Ring" to 3, "Brainstorm" to 1), now.associate { it.card.name to it.quantity })
        assertTrue(now.all { it.condition == "NM" && it.purchasePrice == 1.5 && it.language == "EN" })
        undo()
        assertEquals(setOf("EX", "NM", "GD"), db.collectionDao().all().map { it.condition }.toSet())
        assertEquals(3, db.collectionDao().all().size)
    }

    @Test
    fun deletingSeveralStacksMakesARestorePointFirst() = runBlocking {
        var backups = 0
        repo.beforeBatchDelete = { backups++ }
        val a = stack("Sol Ring", 2)
        val b = stack("Brainstorm", 1)
        stack("Counterspell", 1)
        val (n, undo) = repo.deleteStacks(listOf(a, b))
        assertEquals(3, n)
        assertEquals(1, backups)
        assertEquals(listOf("Counterspell"), db.collectionDao().all().map { it.card.name })
        undo()
        assertEquals(3, db.collectionDao().all().size)
        // The restored stacks keep their sync ids: nothing is deleted on the other phone.
        val uids = db.collectionDao().all().mapNotNull { it.uid }.toSet()
        assertTrue(db.syncDao().deletions().none { it.uid in uids })
    }

    @Test
    fun oneCopyMoreOrLess() = runBlocking {
        val a = stack("Sol Ring", 1)
        repo.changeQuantity(a, 1)
        assertEquals(2, db.collectionDao().byId(a)!!.quantity)
        repo.changeQuantity(a, -1)
        val (_, undo) = repo.changeQuantity(a, -1)
        assertNull(db.collectionDao().byId(a))
        undo()
        assertEquals(1, db.collectionDao().byId(a)!!.quantity)
    }

    @Test
    fun stacksGoOnTheGiveSideOfATrade() = runBlocking {
        val a = stack("Sol Ring", 3)
        val b = stack("Brainstorm", 2)
        // A new trade with every copy; Undo deletes the trade again.
        val (trade, n, undo) = repo.stacksToTrade(listOf(a, b), null)
        assertEquals(5, n)
        val items = db.tradeDao().get(trade)!!.items
        assertTrue(items.all { it.side == Side.GIVE })
        assertEquals(mapOf("Sol Ring" to 3, "Brainstorm" to 2), items.associate { it.card.name to it.quantity })
        undo()
        assertNull(db.tradeDao().get(trade))
        // One copy into an open trade; Undo takes just that out.
        val open = repo.newTrade()
        repo.addToTrade(open, Side.GIVE, ref("Sol Ring"), Finish.NONFOIL)
        val (_, one, undoOne) = repo.stacksToTrade(listOf(a), open, copies = 1)
        assertEquals(1, one)
        assertEquals(2, db.tradeDao().get(open)!!.items.single().quantity)
        undoOne()
        assertEquals(1, db.tradeDao().get(open)!!.items.single().quantity)
        // The collection itself isn't changed until the trade is applied.
        assertEquals(5, db.collectionDao().all().sumOf { it.quantity })
    }

    @Test
    fun selectedStacksAsAList() = runBlocking {
        stack("Sol Ring", 2, set = "c21")
        val text = repo.stacksText(db.collectionDao().all())
        assertEquals("2 Sol Ring (C21 #1)", text)
        // Exported as CSV: only the selected stacks.
        val keep = stack("Brainstorm", 1)
        val csv = repo.exportCollectionCsv(com.mtgtrader.data.PriceType.TREND, null, setOf(keep))
        assertEquals(2, csv.trim().lines().size)
        assertTrue("Brainstorm" in csv)
    }

    // ---- Settings, sync and the database ----------------------------------------------------

    @Test
    fun newSettingsGoThroughSync() {
        val s = Settings(context)
        assertTrue(s.wishSections.value)
        assertFalse(s.receivedToCollection.value)
        assertEquals(SwipeAction.MOVE_TO_BINDER, s.swipeRight.value)
        assertEquals(SwipeAction.ADD_TO_TRADE, s.swipeLeft.value)
        s.setWishSections(false)
        s.setReceivedClear(ReceivedClear.SIX_MONTHS)
        s.setReceivedToCollection(true)
        s.setSwipe(right = true, SwipeAction.ONE_MORE)
        s.setSwipe(right = false, SwipeAction.NOTHING)
        val prefs = s.syncedPrefs()
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        val other = Settings(context)
        other.applySyncedPrefs(prefs)
        assertFalse(other.wishSections.value)
        assertEquals(ReceivedClear.SIX_MONTHS, other.receivedClear.value)
        assertTrue(other.receivedToCollection.value)
        assertEquals(SwipeAction.ONE_MORE, other.swipeRight.value)
        assertEquals(SwipeAction.NOTHING, other.swipeLeft.value)
    }

    @Test
    fun receivedGoesThroughSync() {
        val mine = WishlistItem(id = 1, card = ref("Sol Ring"), uid = "w1", updatedAt = 10)
        val got = mine.copy(receivedAt = oct15, updatedAt = 20)
        val back = SyncFile.decode(SyncFile.encode(SyncFile(data = SyncData(wishlist = listOf(got)))))
        assertEquals(oct15, back.data.wishlist.single().receivedAt)
        assertEquals(oct15, SyncMerge.merge(SyncData(wishlist = listOf(mine)), back.data, System.currentTimeMillis()).wishlist.single().receivedAt)
        // An older version would show received cards as wanted again: it refuses format 6 and asks to be updated.
        assertTrue(SyncFile.FORMAT >= 6)
        assertTrue(BackupFile.FORMAT >= 6)
    }

    /** A version 16 database (app 1.37) exactly as Room made it, from the exported schema. */
    private fun createVersion16(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/16.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL(
            "INSERT INTO wishlist (id, foil, quantity, anyPrinting, notes, addedAt, ownedAtAdd, orderedAt, alertBelow, uid, updatedAt, scryfallId, name, " +
                "setCode, setName, collectorNumber, rarity, imageUrl, cardmarketId, fallbackEur, fallbackEurFoil, hasNonFoil, hasFoil, foilType, hasEtched, " +
                "flavorName, cardmarketFoilId) VALUES (1, 0, 2, 1, 'for the cube', 1, 0, 7, 1.5, 'w1', 5, 'sr', 'Sol Ring', 'c21', 'Commander 2021', " +
                "'263', 'uncommon', NULL, 1, 1.0, NULL, 1, 0, NULL, 0, NULL, NULL)"
        )
        sql.version = 16
        sql.close()
    }

    @Test
    fun updatingFromVersion16KeepsTheWishlistWanted() = runBlocking {
        val file = context.getDatabasePath("update-138.db").apply { parentFile?.mkdirs(); delete() }
        createVersion16(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-138.db")
            .addMigrations(AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val item = updated.wishlistDao().all().single()
            assertEquals(1.5, item.alertBelow!!, 0.0)
            assertNull(item.receivedAt)
            assertEquals(2, updated.wishlistDao().observeCount().first())
            // The update doesn't count as a change: nothing to sync until the user edits something.
            assertEquals(5, item.updatedAt)
            updated.wishlistDao().update(item.copy(receivedAt = 9))
            val got = updated.wishlistDao().byId(1)!!
            assertNotNull(got.receivedAt)
            assertTrue(got.updatedAt > 5)
            assertEquals(0, updated.wishlistDao().observeCount().first())
        } finally {
            updated.close()
        }
    }
}
