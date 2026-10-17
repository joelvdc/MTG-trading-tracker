package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.WishlistItem
import com.mtgtrader.data.WishlistOwned
import com.mtgtrader.data.WishlistRow
import com.mtgtrader.ui.asCollectionRow
import com.mtgtrader.ui.wishLabel
import com.mtgtrader.ui.wishShareText
import com.mtgtrader.ui.wishTotalLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.35: wishlist cards marked as ordered (on their way). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release135Test {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun ref(name: String, eur: Double) = CardRef(name.lowercase(), name, "ice", "Ice Age", "61", "common", null, null, eur, null, true, false)

    private fun wish(id: Long, name: String, eur: Double, qty: Int = 1, orderedAt: Long? = null) =
        WishlistRow(WishlistItem(id = id, card = ref(name, eur), quantity = qty, orderedAt = orderedAt), null)

    // 15 October 2026, 12:00 UTC (the tests run in UTC with English formats).
    private val oct15 = 1_792_065_600_000L

    @Test
    fun theRowSaysWhenItWasOrdered() {
        val item = wish(1, "Brainstorm", 1.0, orderedAt = oct15).item
        assertEquals("Any printing · 📦 ordered Oct 15 · you own 1", wishLabel(item, WishlistOwned(owned = 1, gotSince = 0), now = oct15))
        // 1.36: the year only shows for an order from an earlier year.
        assertEquals("Any printing · 📦 ordered Oct 15, 2026", wishLabel(item, null, now = oct15 + 100L * 24 * 3600 * 1000))
        assertEquals("Any printing", wishLabel(item.copy(orderedAt = null), null))
    }

    @Test
    fun orderedCardsAreCountedButNotStillToBuy() {
        val rows = listOf(wish(1, "Brainstorm", 2.0, qty = 2), wish(2, "Sol Ring", 10.0, qty = 3, orderedAt = oct15))
        val ordered = rows.filter { it.item.orderedAt != null }.map { it.item.id }.toSet()
        val line = wishTotalLine(rows.map { it.asCollectionRow() }, { it in ordered }, PriceType.TREND)
        assertTrue(line, line.startsWith("5 card(s) wanted · 3 ordered · "))
        assertTrue(line, "4.00" in line && "still to buy" in line)
        // Without ordered cards, the line reads as before.
        val before = wishTotalLine(rows.map { it.asCollectionRow() }, { false }, PriceType.TREND)
        assertTrue(before, before.startsWith("5 card(s) wanted · ") && "34.00" in before && "to buy them" in before)
    }

    @Test
    fun theSharedListLeavesOrderedCardsOut() {
        val rows = listOf(wish(1, "Brainstorm", 2.0, qty = 2), wish(2, "Sol Ring", 10.0, orderedAt = oct15))
        assertEquals("2 Brainstorm", wishShareText(rows))
    }

    @Test
    fun theMarkGoesThroughSyncAndTheNewerChangeWins() {
        val mine = wish(1, "Sol Ring", 10.0).item.copy(uid = "w1", updatedAt = 10)
        val ticked = mine.copy(orderedAt = oct15, updatedAt = 20)
        val back = SyncFile.decode(SyncFile.encode(SyncFile(data = SyncData(wishlist = listOf(ticked)))))
        assertEquals(oct15, back.data.wishlist.single().orderedAt)
        val merged = SyncMerge.merge(SyncData(wishlist = listOf(mine)), back.data, System.currentTimeMillis())
        assertEquals(oct15, merged.wishlist.single().orderedAt)
        // A file from before 1.35 has no mark: its cards simply aren't ordered.
        assertTrue(SyncFile.FORMAT >= 4)
    }

    /** A version 14 database (app 1.33–1.34) exactly as Room made it, from the exported schema. */
    private fun createVersion14(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/14.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL(
            "INSERT INTO wishlist (id, foil, quantity, anyPrinting, notes, addedAt, ownedAtAdd, uid, updatedAt, scryfallId, name, setCode, setName, " +
                "collectorNumber, rarity, imageUrl, cardmarketId, fallbackEur, fallbackEurFoil, hasNonFoil, hasFoil, foilType, hasEtched, flavorName, " +
                "cardmarketFoilId) VALUES (1, 0, 2, 1, 'for the cube', 1, 0, 'w1', 5, 'sr', 'Sol Ring', 'c21', 'Commander 2021', '263', 'uncommon', " +
                "NULL, 1, 1.0, NULL, 1, 0, NULL, 0, NULL, NULL)"
        )
        sql.version = 14
        sql.close()
    }

    @Test
    fun updatingFromVersion14KeepsTheWishlistUnordered() = runBlocking {
        val file = context.getDatabasePath("update-135.db").apply { parentFile?.mkdirs(); delete() }
        createVersion14(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-135.db")
            .addMigrations(AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val item = updated.wishlistDao().all().single()
            assertEquals(2, item.quantity)
            assertEquals("for the cube", item.notes)
            assertNull(item.orderedAt)
            // The update doesn't count as a change: nothing to sync until the user edits something.
            assertEquals(5, item.updatedAt)
            // Ticking the box is a change to sync.
            updated.wishlistDao().update(item.copy(orderedAt = oct15))
            val ticked = updated.wishlistDao().byId(1)!!
            assertEquals(oct15, ticked.orderedAt)
            assertTrue(ticked.updatedAt > 5)
            assertEquals(oct15, updated.wishlistDao().observeAll().first().single().item.orderedAt)
        } finally {
            updated.close()
        }
    }
}
