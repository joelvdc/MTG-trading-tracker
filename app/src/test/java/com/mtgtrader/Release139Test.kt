package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.BackupFile
import com.mtgtrader.data.Binder
import com.mtgtrader.data.BinderOrder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.DisplayCurrency
import com.mtgtrader.data.ExchangeRates
import com.mtgtrader.data.Finish
import com.mtgtrader.data.Money
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.PriceAlerts
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.WishOrders
import com.mtgtrader.data.WishlistItem
import com.mtgtrader.data.levelEur
import com.mtgtrader.ui.WishToCollection
import com.mtgtrader.ui.joiningStack
import com.mtgtrader.ui.wishLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.39: alert levels in their own currency, received cards into the collection, orders against the wishlist, arranging binders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release139Test {
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
    fun tearDown() {
        db.close()
        Money.display = DisplayCurrency()
        Money.rates = null
    }

    private fun ref(name: String, id: String = name.lowercase().replace(' ', '-'), set: String = "m21") =
        CardRef(id, name, set, set.uppercase(), "1", "rare", null, null, 1.0, 2.0, true, true)

    private val oct15 = 1_792_065_600_000L

    // ---- The alert level's currency ---------------------------------------------------------

    @Test
    fun aLevelInKronerStaysThatWhateverTheRate() {
        val item = WishlistItem(card = ref("Ragavan"), alertBelow = 300.0, alertCurrency = "DKK")
        // Yesterday's rate, then today's: the level shows as typed both days.
        for (rate in listOf(7.4612, 7.4608)) {
            Money.display = DisplayCurrency(AppCurrency.DKK, rate)
            assertEquals("300,00 kr.", Money.format(item.levelEur!!))
        }
        // In euros it follows the rate (the price compared is in euros).
        assertEquals(300.0 / 7.4608, item.levelEur!!, 1e-9)
        // Shown in euros or dollars, it's converted with the day's rates.
        Money.rates = ExchangeRates("2026-10-16", mapOf("DKK" to 7.46, "USD" to 1.1))
        Money.display = DisplayCurrency(AppCurrency.EUR, 1.0)
        assertEquals(300.0 / 7.46, item.levelEur!!, 1e-9)
        // A level from before 1.39 has no currency: it was kept in euros.
        assertEquals(40.0, WishlistItem(card = ref("Sol Ring"), alertBelow = 40.0).levelEur!!, 0.0)
    }

    @Test
    fun aNewDaysRateDoesntNotifyAgain() {
        // Notified at the level as typed (300); the next day the level in euros is a hair different.
        val first = PriceAlerts.step(300.0 / 7.4612, 40.0, null, key = 300.0)
        assertTrue(first.notify)
        assertEquals(300.0, first.alertedLevel!!, 0.0)
        val next = PriceAlerts.step(300.0 / 7.4608, 40.0, first.alertedLevel, key = 300.0)
        assertFalse(next.notify)
        // A new level as typed does.
        assertTrue(PriceAlerts.step(310.0 / 7.4608, 40.0, first.alertedLevel, key = 310.0).notify)
    }

    // ---- Orders against the wishlist ----------------------------------------------------------

    @Test
    fun anOrdersCardsMatchTheWishlistOnce() {
        val any = WishlistItem(id = 1, card = ref("Sol Ring", id = "sr-c21", set = "c21"), quantity = 3, addedAt = 1)
        val exact = WishlistItem(id = 2, card = ref("Sol Ring", id = "sr-cmm", set = "cmm"), anyPrinting = false, quantity = 1, addedAt = 2)
        val foil = WishlistItem(id = 3, card = ref("Brainstorm", id = "bs"), anyPrinting = false, foil = true, addedAt = 3)
        val linked = WishlistItem(id = 4, card = ref("Counterspell"), receivedAt = 5, receivedFrom = "CardTrader order #1")
        val offline = WishlistItem(id = 5, card = com.mtgtrader.data.PendingCard.ref("Rhystic Study"), addedAt = 5)
        val bought = listOf(
            WishOrders.Bought(ref("Sol Ring", id = "sr-cmm", set = "cmm"), foil = false, quantity = 2),
            WishOrders.Bought(ref("Brainstorm", id = "bs"), foil = false, quantity = 1),
            WishOrders.Bought(ref("Counterspell"), foil = false, quantity = 1),
            WishOrders.Bought(ref("Rhystic Study", id = "rs-jmp"), foil = false, quantity = 1),
        )
        val m = WishOrders.match(listOf(any, exact, foil, linked, offline), bought).associate { it.item.id to it.quantity }
        // The CMM Sol Ring goes to the exact wish first; the other copy to "any printing". A non-foil
        // Brainstorm isn't the foil wanted. A card already linked to an order isn't matched again.
        assertEquals(mapOf(1L to 1, 2L to 1, 5L to 1), m)
        assertEquals("CardTrader order #123", WishOrders.label("CardTrader", listOf("123", "123", null)))
        assertEquals("Cardmarket order", WishOrders.label("Cardmarket", listOf("1", "2")))
    }

    @Test
    fun cardsFromAnOrderAreMarkedReceivedOrTakenOff() = runBlocking {
        val sol = db.wishlistDao().insert(WishlistItem(card = ref("Sol Ring"), quantity = 3, orderedAt = 1))
        val storm = db.wishlistDao().insert(WishlistItem(card = ref("Brainstorm")))
        val tithe = db.wishlistDao().insert(WishlistItem(card = ref("Smothering Tithe")))
        val rhystic = db.wishlistDao().insert(WishlistItem(card = ref("Rhystic Study"), quantity = 2))
        val matches = repo.wishMatches(
            listOf(
                WishOrders.Bought(ref("Sol Ring"), false, 2), WishOrders.Bought(ref("Brainstorm"), false, 1),
                WishOrders.Bought(ref("Smothering Tithe"), false, 1), WishOrders.Bought(ref("Rhystic Study"), false, 1),
            ),
        ).associateBy { it.item.id }
        val choices = listOf(
            matches.getValue(sol) to WishOrders.Choice.RECEIVED,
            matches.getValue(storm) to WishOrders.Choice.RECEIVED,
            matches.getValue(tithe) to WishOrders.Choice.LEAVE,
            matches.getValue(rhystic) to WishOrders.Choice.REMOVE,
        )
        val (n, undo) = repo.applyOrderToWishlist(choices, "CardTrader order #9", at = oct15)
        assertEquals(3, n)
        val all = db.wishlistDao().all()
        // Brainstorm: received from the order.
        val s = all.single { it.id == storm }
        assertEquals(oct15, s.receivedAt)
        assertEquals("CardTrader order #9", s.receivedFrom)
        // Sol Ring: 2 of 3 bought: an entry of 2 received from the order, 1 still wanted and ordered.
        assertEquals(1, all.single { it.id == sol }.quantity)
        assertNull(all.single { it.id == sol }.receivedAt)
        val got = all.single { it.card.name == "Sol Ring" && it.receivedAt != null }
        assertEquals(2, got.quantity)
        assertEquals("CardTrader order #9", got.receivedFrom)
        // Smothering Tithe left as it was; Rhystic Study: 1 of 2 taken off.
        assertNull(all.single { it.id == tithe }.receivedAt)
        assertEquals(1, all.single { it.id == rhystic }.quantity)
        assertEquals("Any printing · ✓ received Oct 15 · CardTrader order #9", wishLabel(s, null, now = oct15))

        undo()
        val back = db.wishlistDao().all()
        assertEquals(4, back.size)
        assertEquals(3, back.single { it.id == sol }.quantity)
        assertTrue(back.all { it.receivedAt == null && it.receivedFrom == null })
    }

    // ---- A received card into the collection -------------------------------------------------

    @Test
    fun aReceivedCardJoinsTheSameVersionsStack() = runBlocking {
        val card = ref("Sol Ring")
        // 2 copies already, bought at €10 each.
        val stack = repo.addToCollection(card, Finish.NONFOIL, "NM", "EN", 2).itemId
        db.collectionDao().update(db.collectionDao().byId(stack)!!.copy(purchasePrice = 10.0))
        val wish = db.wishlistDao().insert(WishlistItem(card = card, receivedAt = oct15))
        val v = WishToCollection.of(db.wishlistDao().byId(wish)!!).copy(price = 16.0)
        // The window tells it'll join that stack.
        assertEquals(stack, joiningStack(db.collectionDao().all(), v, Binder.UNSORTED)?.id)
        assertNull(joiningStack(db.collectionDao().all(), v.copy(condition = "EX"), Binder.UNSORTED))

        val undo = repo.wishToCollection(wish, v.card!!, v.finish, v.condition, v.language, v.quantity, Binder.UNSORTED, v.price)
        val s = db.collectionDao().byId(stack)!!
        assertEquals(3, s.quantity)
        // The average of all copies: (2 × 10 + 16) / 3.
        assertEquals(12.0, s.purchasePrice!!, 0.001)
        assertNull(db.wishlistDao().byId(wish))

        undo()
        assertEquals(2, db.collectionDao().byId(stack)!!.quantity)
        assertEquals(10.0, db.collectionDao().byId(stack)!!.purchasePrice!!, 0.0)
        assertTrue(db.wishlistDao().byId(wish) != null)
    }

    @Test
    fun aCardAddedOfflineNeedsItsPrintingFirst() {
        val offline = WishlistItem(card = com.mtgtrader.data.PendingCard.ref("Brainstorm"), quantity = 2)
        val v = WishToCollection.of(offline)
        assertNull(v.card)
        assertEquals(2, v.quantity)
        // A foil wish keeps its finish.
        assertEquals(Finish.FOIL, WishToCollection.of(WishlistItem(card = ref("Sol Ring"), foil = true)).finish)
    }

    @Test
    fun cardsAddedWhenReceivedArentAddedAgain() = runBlocking {
        val sol = db.wishlistDao().insert(WishlistItem(card = ref("Sol Ring"), orderedAt = 1))
        val offline = db.wishlistDao().insert(WishlistItem(card = com.mtgtrader.data.PendingCard.ref("Brainstorm")))
        repo.receiveWishlist(listOf(sol, offline), toCollection = true)
        assertEquals(WishOrders.ADDED, db.wishlistDao().byId(sol)!!.receivedFrom)
        // Nothing could be added for the card without a printing: it isn't linked.
        assertNull(db.wishlistDao().byId(offline)!!.receivedFrom)
        // An order with that card now doesn't offer it again.
        assertTrue(repo.wishMatches(listOf(WishOrders.Bought(ref("Sol Ring"), false, 1))).none { it.item.id == sol })
    }

    // ---- Arranging binders ---------------------------------------------------------------------

    @Test
    fun theBinderBarKeepsItsArrangedOrder() {
        val a = Binder(id = 1, name = "Zendikar", uid = "u1")
        val b = Binder(id = 2, name = "Alpha", uid = "u2")
        val trade = Binder(id = 3, name = "Trade binder", uid = "u3")
        // The usual order: Unsorted, the wishlist, the trade binder, then by name.
        assertEquals(listOf("unsorted", "wishlist", "b:u3", "b:u2", "b:u1"), BinderOrder.arrange(listOf(a, b, trade), emptyList(), tradeBinderId = 3))
        // Arranged: the wishlist first. A new binder goes after the arranged ones; a deleted one is dropped.
        val arranged = listOf("wishlist", "b:u1", "unsorted", "b:gone")
        val c = Binder(id = 4, name = "Commander", uid = "u4")
        assertEquals(listOf("wishlist", "b:u1", "unsorted", "b:u3", "b:u2", "b:u4"), BinderOrder.arrange(listOf(a, b, trade, c), arranged, tradeBinderId = 3))
        assertEquals(listOf("b:u1", "wishlist", "unsorted"), BinderOrder.move(listOf("wishlist", "b:u1", "unsorted"), 1, -1))
        assertEquals(listOf("wishlist", "b:u1"), BinderOrder.move(listOf("wishlist", "b:u1"), 1, 1))
        // Synced with the settings.
        val s = Settings(context)
        s.setBinderOrder(arranged)
        val prefs = s.syncedPrefs()
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        val other = Settings(context)
        other.applySyncedPrefs(prefs)
        assertEquals(arranged, other.binderOrder.value)
    }

    // ---- Sync and the database -----------------------------------------------------------------

    @Test
    fun theNewFieldsGoThroughSync() {
        val w = WishlistItem(id = 1, card = ref("Sol Ring"), uid = "w1", alertBelow = 300.0, alertCurrency = "DKK", receivedAt = 2, receivedFrom = "CardTrader order #9")
        val back = SyncFile.decode(SyncFile.encode(SyncFile(data = SyncData(wishlist = listOf(w))))).data.wishlist.single()
        assertEquals("DKK", back.alertCurrency)
        assertEquals("CardTrader order #9", back.receivedFrom)
        // An older version would read the level as euros and lose the link: it refuses format 7 and asks to be updated.
        assertTrue(SyncFile.FORMAT >= 7)
        assertTrue(BackupFile.FORMAT >= 7)
    }

    /** A version 17 database (app 1.38) exactly as Room made it, from the exported schema. */
    private fun createVersion17(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/17.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL(
            "INSERT INTO wishlist (id, foil, quantity, anyPrinting, notes, addedAt, ownedAtAdd, orderedAt, alertBelow, receivedAt, uid, updatedAt, " +
                "scryfallId, name, setCode, setName, collectorNumber, rarity, imageUrl, cardmarketId, fallbackEur, fallbackEurFoil, hasNonFoil, hasFoil, " +
                "foilType, hasEtched, flavorName, cardmarketFoilId) VALUES (1, 0, 2, 1, NULL, 1, 0, NULL, 40.2, NULL, 'w1', 5, 'sr', 'Sol Ring', 'c21', " +
                "'Commander 2021', '263', 'uncommon', NULL, 1, 1.0, NULL, 1, 0, NULL, 0, NULL, NULL)"
        )
        sql.version = 17
        sql.close()
    }

    @Test
    fun updatingFromVersion17KeepsLevelsInEuros() = runBlocking {
        val file = context.getDatabasePath("update-139.db").apply { parentFile?.mkdirs(); delete() }
        createVersion17(file)
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-139.db")
            .addMigrations(AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val item = updated.wishlistDao().all().single()
            // Levels set before keep meaning euros (typed again, they keep their currency).
            assertNull(item.alertCurrency)
            assertEquals(40.2, item.levelEur!!, 0.0)
            assertNull(item.receivedFrom)
            // The update doesn't count as a change: nothing to sync until the user edits something.
            assertEquals(5, item.updatedAt)
        } finally {
            updated.close()
        }
    }
}
