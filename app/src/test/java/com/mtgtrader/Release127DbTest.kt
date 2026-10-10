package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.Finish
import com.mtgtrader.data.Marks
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncSchema
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.27 against a real database: the update from version 11, and signed/altered stacks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release127DbTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var repo: MtgRepository
    private val ref = CardRef("bs", "Brainstorm", "ice", "Ice Age", "61", "common", null, 1, 1.0, null, true, false)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        val http = OkHttpClient()
        repo = MtgRepository(db, ScryfallApi(http), PriceGuideRepository(context, http, db, Settings(context)), CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun stacks() = db.collectionDao().all().sortedWith(compareBy({ it.binderId }, { it.signed }, { it.altered }))

    // ---- The database update ----------------------------------------------------------------

    /** A version 11 database (app 1.18–1.26) exactly as Room made it, from the exported schema. */
    private fun createVersion11(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/11.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL(
            "INSERT INTO collection (id, foil, condition, language, quantity, addedAt, etched, binderId, uid, updatedAt, scryfallId, name, setCode, " +
                "setName, collectorNumber, rarity, imageUrl, cardmarketId, fallbackEur, fallbackEurFoil, hasNonFoil, hasFoil, foilType, hasEtched, " +
                "flavorName, notes, purchasePrice, cardmarketFoilId) VALUES (1, 0, 'NM', 'EN', 4, 1, 0, 0, 'u1', 5, 'bs', 'Brainstorm', 'ice', " +
                "'Ice Age', '61', 'common', NULL, 1, 1.0, NULL, 1, 0, NULL, 0, NULL, 'from a friend', 0.5, NULL)"
        )
        sql.version = 11
        sql.close()
    }

    @Test
    fun updatingFromVersion11KeepsTheCollection() = runBlocking {
        val file = context.getDatabasePath("update-test.db").apply { parentFile?.mkdirs(); delete() }
        createVersion11(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-test.db")
            .addMigrations(AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val item = updated.collectionDao().all().single()
            assertEquals(4, item.quantity)
            assertEquals("u1", item.uid)
            assertEquals("from a friend", item.notes)
            assertFalse(item.signed || item.altered)
            // The update doesn't count as a change: nothing to sync until the user edits something.
            assertEquals(5, item.updatedAt)
            // A signed copy of the same card in the same binder is a stack of its own now.
            updated.collectionDao().insert(item.copy(id = 0, uid = null, quantity = 1, signed = true))
            assertEquals(2, updated.collectionDao().all().size)
        } finally {
            updated.close()
        }
    }

    // ---- Stacks -----------------------------------------------------------------------------

    @Test
    fun signedCopiesAreAStackOfTheirOwn() = runBlocking {
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 3)
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 1, marks = Marks(signed = true))
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 1, marks = Marks(signed = true))
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 1, marks = Marks(altered = true))
        val s = stacks()
        assertEquals(listOf(3 to Marks.NONE, 1 to Marks(altered = true), 2 to Marks(signed = true)), s.map { it.quantity to it.marks })
    }

    @Test
    fun markingOneOfFourSplitsTheStack() = runBlocking {
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 4)
        val before = db.collectionDao().all().single().copy(notes = "from a friend", purchasePrice = 0.5).also { db.collectionDao().update(it) }
        val uid = db.collectionDao().byId(before.id)!!.uid
        repo.saveCollectionEdit(before.copy(signed = true), before.binderId, move = 4, keepMarks = 3, previous = Marks.NONE)
        val (plain, signed) = stacks()
        assertEquals(3, plain.quantity)
        assertFalse(plain.signed)
        // The existing stack (and its sync id) keeps the plain copies.
        assertEquals(before.id, plain.id)
        assertEquals(uid, plain.uid)
        assertEquals(1, signed.quantity)
        assertTrue(signed.signed)
        assertEquals("from a friend", signed.notes)
        assertEquals(0.5, signed.purchasePrice!!, 0.001)
        assertNotNull(signed.uid)
        assertTrue(signed.uid != uid)
    }

    @Test
    fun splittingIntoAnotherBinderMovesBothParts() = runBlocking {
        val binder = repo.createBinder("Signed stuff")
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 4)
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 1, binder, Marks(signed = true))
        val item = db.collectionDao().all().first { !it.signed }
        repo.saveCollectionEdit(item.copy(signed = true), binder, move = 1, keepMarks = 2, previous = Marks.NONE)
        val s = stacks()
        assertTrue(s.all { it.binderId == binder })
        // The two newly signed copies joined the signed one already there.
        assertEquals(listOf(2 to false, 3 to true), s.map { it.quantity to it.signed })
    }

    @Test
    fun markingTheWholeStackMergesWithTheMarkedOne() = runBlocking {
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 2)
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 1, marks = Marks(signed = true))
        val plain = db.collectionDao().all().first { !it.signed }
        repo.saveCollectionEdit(plain.copy(signed = true), plain.binderId, move = 2)
        val s = stacks()
        assertEquals(1, s.size)
        assertEquals(3, s.single().quantity)
        assertTrue(s.single().signed)

        // And back: unticking keeps them apart from nothing, so it's a plain stack again.
        repo.saveCollectionEdit(s.single().copy(signed = false), Binder.UNSORTED, move = 3)
        assertEquals(listOf(3 to Marks.NONE), stacks().map { it.quantity to it.marks })
    }

    @Test
    fun movingSomeSignedCopiesKeepsThemSigned() = runBlocking {
        val binder = repo.createBinder("Showcase")
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 3, marks = Marks(signed = true))
        val item = db.collectionDao().all().single()
        repo.saveCollectionEdit(item, binder, move = 1)
        val s = stacks()
        assertEquals(listOf(Binder.UNSORTED to 2, binder to 1), s.map { it.binderId to it.quantity })
        assertTrue(s.all { it.signed })
    }

    @Test
    fun exportHasSignedAndAlteredColumns() = runBlocking {
        repo.addToCollection(ref, Finish.NONFOIL, "NM", "EN", 1, marks = Marks(signed = true))
        val lines = repo.exportCollectionCsv(PriceType.TREND).trim().lines()
        assertTrue(lines[0].endsWith(",Notes,Signed,Altered"))
        assertTrue(lines[1], lines[1].endsWith(",true,false"))
    }
}
