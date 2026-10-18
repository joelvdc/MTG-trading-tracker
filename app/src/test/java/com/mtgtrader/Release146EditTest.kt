package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.ArchidektCategory
import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckEditItems
import com.mtgtrader.data.DeckEdits
import com.mtgtrader.data.DeckGroup
import com.mtgtrader.data.Finish
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/** 1.46 against a real database: editing decks, local decks, the queue of edits for Archidekt, the database update. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release146EditTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var c: AppContainer

    private fun ref(id: String, name: String) = CardRef(id, name, "cmr", "Commander Legends", "1", "rare", null, 1, 1.0, null, true, true)

    @Before
    fun setUp() {
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        context.deleteDatabase("mtgtrader.db")
        c = AppContainer(context)
        runBlocking {
            // Card details as if fetched before, so nothing needs Scryfall.
            c.db.cardInfoDao().putAll(
                listOf(
                    CardInfo("sol", null, "", "", "Artifact", 1.0, 1),
                    CardInfo("cult", null, "G", "G", "Sorcery", 3.0, 1),
                )
            )
        }
    }

    /** One of someone's decks from Archidekt, as 1.46 loads it (lines with their Archidekt ids). */
    private suspend fun archidektDeck(): Long {
        val id = 777L
        c.db.deckDao().upsert(
            Deck(
                id, "Auntie Plague", "someone", "Auntie Ool", null, null, "BRG", 2,
                categoryInfo = DeckEdits.encode(listOf(ArchidektCategory("Ramp"), ArchidektCategory("Maybeboard", included = false))),
            )
        )
        c.db.deckDao().insertCards(
            listOf(
                DeckCard(deckId = id, card = ref("sol", "Sol Ring"), quantity = 1, category = "Ramp", types = "Artifact", cmc = 1.0, commander = false, foil = false,
                    categories = "Ramp", archidektCardId = 11, relationId = 900, lineUid = "l-sol"),
                DeckCard(deckId = id, card = ref("cult", "Cultivate"), quantity = 1, category = "Ramp", types = "Sorcery", cmc = 3.0, commander = false, foil = false,
                    categories = "Ramp", archidektCardId = 12, relationId = 901, lineUid = "l-cult"),
            )
        )
        return id
    }

    @Test
    fun editsWaitForArchidektAndUndoPutsThemBack() = runBlocking {
        val id = archidektDeck()
        val undo = c.deckEditor.move(id, listOf("l-cult"), "Land Ramp")
        val lines = c.db.deckDao().allLines(id).associateBy { it.lineUid }
        assertEquals("Land Ramp", lines.getValue("l-cult").category)
        assertEquals(901L, lines.getValue("l-cult").relationId)
        // One edit waits, naming the line: sent as Archidekt's "modify" with the new category.
        val waiting = c.db.deckEditDao().forDeck(id).single()
        assertEquals("l-cult", waiting.lineUid)
        val item = DeckEditItems.item(waiting, lines["l-cult"])!!
        assertEquals("modify", item["action"]!!.jsonPrimitive.content)
        assertEquals(listOf("Land Ramp"), item["categories"]!!.jsonArray.map { it.jsonPrimitive.content })
        // Undo: back in Ramp, still one edit waiting for the line (it's sent as it is when sending).
        undo()
        assertEquals("Ramp", c.db.deckDao().allLines(id).first { it.lineUid == "l-cult" }.category)
        assertEquals(1, c.db.deckEditDao().count(id))
    }

    @Test
    fun removingSendsArchidektsRemovalAndTheMaybeboardIsLeftOut() = runBlocking {
        val id = archidektDeck()
        c.deckEditor.toBoard(id, listOf("l-sol"), maybeboard = true)
        val sol = c.db.deckDao().allLines(id).first { it.lineUid == "l-sol" }
        assertTrue(sol.maybeboard)
        assertEquals(1, c.db.deckDao().get(id)!!.cardCount)
        c.deckEditor.remove(id, listOf("l-sol"))
        val edit = c.db.deckEditDao().forDeck(id).single { it.lineUid == "l-sol" }
        val remove = DeckEditItems.item(edit, null)!!
        assertEquals("remove", remove["action"]!!.jsonPrimitive.content)
        assertEquals("900", remove["deckRelationId"]!!.jsonPrimitive.content)
    }

    @Test
    fun localDecksAreEditedWithoutWaitingEdits() = runBlocking {
        val id = c.deckEditor.newEmpty("Brew")
        assertTrue(id < 0)
        val deck = c.db.deckDao().get(id)!!
        assertTrue(deck.local)
        assertEquals(DeckGroup.LOCAL, DeckGroup.of(deck, "joelvdc"))
        c.deckEditor.addCard(id, ref("sol", "Sol Ring"), Finish.NONFOIL, category = "Ramp")
        c.deckEditor.addCard(id, ref("sol", "Sol Ring"), Finish.NONFOIL)
        val sol = c.db.deckDao().allLines(id).single()
        assertEquals(2, sol.quantity)
        assertEquals("Ramp", sol.category)
        c.deckEditor.setQuantity(id, sol.lineUid, 1)
        assertEquals(1, c.db.deckDao().get(id)!!.cardCount)
        assertEquals(0, c.db.deckEditDao().count(id))
        // Renaming a category moves its cards; an empty one is kept until a card goes in.
        c.deckEditor.renameCategory(id, "Ramp", "Mana")
        assertEquals("Mana", c.db.deckDao().allLines(id).single().category)
        c.deckEditor.addCategory(id, "Wincons")
        assertTrue(DeckEdits.categories(c.db.deckDao().get(id)!!.categoryInfo).any { it.name == "Wincons" })
        // The commander category makes a card the commander.
        c.deckEditor.move(id, listOf(sol.lineUid), DeckEdits.COMMANDER)
        assertEquals("Sol Ring", c.db.deckDao().get(id)!!.commanders)
    }

    @Test
    fun detachingMakesALocalCopyAndCopiesKeepTheCards() = runBlocking {
        val id = archidektDeck()
        val local = c.deckEditor.detach(id)
        assertTrue(local < 0)
        assertNull(c.db.deckDao().get(id))
        val lines = c.db.deckDao().allLines(local)
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.relationId == null })
        assertEquals("", c.db.deckDao().get(local)!!.owner)
        val copy = c.deckEditor.copyOf(local, "Copy")
        assertEquals(2, c.db.deckDao().allLines(copy).size)
        assertEquals("Copy of Auntie Plague", c.db.deckDao().get(copy)!!.origin)
    }

    @Test
    fun deletingALocalDeckCanBeUndone() = runBlocking {
        val id = c.deckEditor.newEmpty("Brew")
        c.deckEditor.addCard(id, ref("sol", "Sol Ring"), Finish.NONFOIL, category = "Ramp")
        c.deckEditor.deleteLocal(id)
        assertNull(c.db.deckDao().get(id))
        val (name, undo) = c.deckEditor.deleted.value!!
        assertEquals("Brew", name)
        undo()
        assertEquals(1, c.db.deckDao().allLines(id).size)
    }

    @Test
    fun theDecksTabsSections() {
        val own = Deck(1, "A", "joelvdc", "", null, null, "", 0)
        assertEquals(DeckGroup.OWN, DeckGroup.of(own, "JoelVDC"))
        assertEquals(DeckGroup.OTHERS, DeckGroup.of(own.copy(owner = "friend"), "joelvdc"))
        assertEquals(DeckGroup.OTHERS, DeckGroup.of(own, null))
        // Not logged in and never edited by 1.46: not ready to edit.
        assertFalse(c.deckEditor.canEdit(own))
        assertFalse(c.deckEditor.ready(own))
    }

    // ---- The database update ----------------------------------------------------------------

    private fun createVersion21(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/21.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL("INSERT INTO decks (archidektId, name, owner, commanders, commanderScryfallId, artUrl, colorIdentity, cardCount, importedAt, updatedAt) VALUES (5, 'Baba', 'joelvdc', 'Baba', NULL, NULL, 'BRG', 1, 1, 7)")
        sql.execSQL(
            "INSERT INTO deck_cards (id, deckId, scryfallId, name, setCode, setName, collectorNumber, rarity, imageUrl, cardmarketId, fallbackEur, fallbackEurFoil, " +
                "hasNonFoil, hasFoil, hasEtched, quantity, category, types, cmc, commander, foil, etched, gameChanger, addedInApp, maybeboard) " +
                "VALUES (1, 5, 'sol', 'Sol Ring', 'cmr', 'CMR', '1', 'rare', NULL, 1, 1.0, NULL, 1, 1, 0, 1, 'Ramp', 'Artifact', 1.0, 0, 0, 0, 0, 0, 0)"
        )
        sql.version = 21
        sql.close()
    }

    @Test
    fun updatingFromVersion21KeepsTheDecks() = runBlocking {
        val file = context.getDatabasePath("update-146.db").apply { parentFile?.mkdirs(); delete() }
        createVersion21(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-146.db")
            .addMigrations(AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val deck = updated.deckDao().get(5)!!
            // The update doesn't count as a change: nothing to sync until the user edits something.
            assertEquals(7, deck.updatedAt)
            assertNull(deck.categoryInfo)
            val line = updated.deckDao().allLines(5).single()
            assertEquals("", line.lineUid)
            assertEquals(listOf("Ramp"), line.categoryList)
            assertNull(line.relationId)
            assertEquals(0, updated.deckEditDao().count(5))
        } finally {
            updated.close()
        }
        // Local decks and deck editing don't fit older versions' sync files (9 in 1.46; 10 since 1.47's games).
        assertEquals(10, SyncFile.FORMAT)
    }
}
