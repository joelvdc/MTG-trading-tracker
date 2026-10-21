package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.ScryCard
import com.mtgtrader.data.ScryFace
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.toInfo
import com.mtgtrader.ui.DeckMarks
import com.mtgtrader.ui.ManaCost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.43: mana costs on deck pages, and the compact view's labels making room for long names. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release143Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ref(name: String, id: String = name.lowercase().replace(' ', '-')) =
        CardRef(id, name, "m21", "M21", "1", "rare", null, null, 1.0, 2.0, true, true)

    // With 1.44's fields filled in, so only the cost decides (1.44's own rule is tested in Release144Test).
    private fun info(id: String, cost: String?) = CardInfo(id, null, "", "", "Sorcery", 1.0, null, manaCost = cost, producedMana = "")

    // ---- Reading mana costs ----------------------------------------------------------------------

    @Test
    fun costsAreSplitIntoSymbolsPerFace() {
        assertEquals(listOf(listOf("2", "B", "B")), ManaCost.faces("{2}{B}{B}"))
        assertEquals(listOf(listOf("X", "B", "B")), ManaCost.faces("{X}{B}{B}"))
        assertEquals(listOf(listOf("1", "G/W", "G/W")), ManaCost.faces("{1}{G/W}{G/W}"))
        assertEquals(listOf(listOf("1", "B/P", "B/P")), ManaCost.faces("{1}{b/p}{b/p}"))
        // A double-faced card shows both faces; a back face without a cost is left out.
        assertEquals(listOf(listOf("2", "B"), listOf("B")), ManaCost.faces("{2}{B} // {B}"))
        assertEquals(listOf(listOf("U")), ManaCost.faces("{U} // "))
        // Lands, and cards whose cost isn't known yet, show nothing.
        assertTrue(ManaCost.faces("").isEmpty())
        assertTrue(ManaCost.faces(null).isEmpty())
    }

    @Test
    fun scryfallsCostIsKeptWithTheCardsDetails() {
        assertEquals("{3}{B}{B}", ScryCard(id = "a", name = "Danse Macabre", set = "fdn", manaCost = "{3}{B}{B}").toInfo().manaCost)
        // Split and adventure cards have both costs at the top already.
        assertEquals("{2}{B} // {B}", ScryCard(id = "b", name = "Grave Researcher // Reanimate", set = "dsk", manaCost = "{2}{B} // {B}").toInfo().manaCost)
        // Double-faced cards have them on their faces.
        val delver = ScryCard(
            id = "c", name = "Delver of Secrets // Insectile Aberration", set = "isd",
            cardFaces = listOf(ScryFace(name = "Delver of Secrets", manaCost = "{U}"), ScryFace(name = "Insectile Aberration", manaCost = "")),
        )
        assertEquals("{U} // ", delver.toInfo().manaCost)
        // A land has none, but it's known (so it isn't asked for again).
        assertEquals("", ScryCard(id = "d", name = "Swamp", set = "fdn", manaCost = "").toInfo().manaCost)
    }

    // ---- Filling them in -----------------------------------------------------------------------------

    @Test
    fun decksCardsWithoutACostAreLookedUpAgain() = runBlocking {
        db.deckDao().upsert(Deck(7, "Auntie Plague", "joelvdc", "Auntie Ool", null, null, "BRG", 2))
        db.deckDao().insertCards(listOf(
            DeckCard(deckId = 7, card = ref("Reanimate"), quantity = 1, category = "Danse!", types = "Sorcery", cmc = 1.0, commander = false, foil = false),
            DeckCard(deckId = 7, card = ref("Swamp"), quantity = 30, category = "Lands", types = "Land", cmc = 0.0, commander = false, foil = false),
        ))
        // Details saved before 1.43 have no cost: the deck's cards are asked for again, other cards aren't.
        db.cardInfoDao().putAll(listOf(info("reanimate", null), info("swamp", ""), info("not-in-a-deck", null)))
        assertEquals(listOf("reanimate"), db.cardInfoDao().missing())
        assertTrue(db.deckDao().cards(7).isNotEmpty())
        assertEquals(listOf("Swamp" to ""), db.cardInfoDao().observeDeckCosts(7).first().map { it.name to it.manaCost })
        db.cardInfoDao().putAll(listOf(info("reanimate", "{B}")))
        assertTrue(db.cardInfoDao().missing().isEmpty())
        assertEquals(mapOf("Reanimate" to "{B}", "Swamp" to ""), db.cardInfoDao().observeDeckCosts(7).first().associate { it.name to it.manaCost })
    }

    // ---- Labels and long names in the compact view ------------------------------------------------------

    @Test
    fun labelsGiveWayBeforeTheName() {
        // Room for everything: the labels in full.
        assertEquals(DeckMarks.Level.FULL, DeckMarks.level(available = 300, name = 120, extras = 10, full = 120, short = 60))
        // Not for the full labels, but for the short ones (TURN, FAST, LD).
        assertEquals(DeckMarks.Level.SHORT, DeckMarks.level(available = 220, name = 120, extras = 10, full = 120, short = 60))
        // Not even those: dots, and the name stays whole if it fits with them.
        assertEquals(DeckMarks.Level.DOTS, DeckMarks.level(available = 160, name = 120, extras = 10, full = 120, short = 60))
        // A card without labels never needs dots.
        assertEquals(DeckMarks.Level.FULL, DeckMarks.level(available = 100, name = 90, extras = 10, full = 0, short = 0))
        assertEquals("GC", DeckMarks.full(com.mtgtrader.data.BracketTag.GAME_CHANGER))
        assertEquals("EXTRA TURN", DeckMarks.full(com.mtgtrader.data.BracketTag.EXTRA_TURN))
        assertEquals("TURN", DeckMarks.short(com.mtgtrader.data.BracketTag.EXTRA_TURN))
        assertEquals("FAST", DeckMarks.short(com.mtgtrader.data.BracketTag.FAST_MANA))
    }

    // ---- The update ------------------------------------------------------------------------------------------

    /** A version 19 database (app 1.42) exactly as Room made it, from the exported schema. */
    private fun createVersion19(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/19.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL("INSERT INTO card_info (scryfallId, oracleId, colors, colorIdentity, typeLine, cmc, edhrecRank, fetchedAt) VALUES ('sr', NULL, '', '', 'Artifact', 1.0, 1, 5)")
        sql.version = 19
        sql.close()
    }

    @Test
    fun updatingFromVersion19KeepsTheCardDetails() = runBlocking {
        val file = context.getDatabasePath("update-143.db").apply { parentFile?.mkdirs(); delete() }
        createVersion19(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-143.db")
            .addMigrations(AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24, AppDatabase.MIGRATION_24_25)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val sr = updated.cardInfoDao().getMany(listOf("sr")).single()
            assertEquals("Artifact", sr.typeLine)
            assertNull(sr.manaCost)
        } finally {
            updated.close()
        }
        // The card details aren't synced: the sync file stays as 1.42 wrote it, so the phones keep syncing.
        assertTrue(SyncFile.FORMAT >= 8)
    }
}
