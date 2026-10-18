package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.ArchidektApi
import com.mtgtrader.data.BackupFile
import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckToCollection
import com.mtgtrader.data.Finish
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncDeck
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncSchema
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.42: the maybeboard, adding cards from the deck page, compact and folded deck sections. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release142Test {
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
    }

    private fun ref(name: String, id: String = name.lowercase().replace(' ', '-'), set: String = "m21") =
        CardRef(id, name, set, set.uppercase(), "1", "rare", null, null, 1.0, 2.0, true, true)

    private fun deck(id: Long = 7) = Deck(id, "Auntie Plague", "joelvdc", "Auntie Ool", null, null, "BRG", 2)

    private fun card(name: String, maybeboard: Boolean = false, addedInApp: Boolean = false, category: String = "Ramp", qty: Int = 1) =
        DeckCard(
            deckId = 7, card = ref(name), quantity = qty, category = category, types = "Artifact", cmc = 1.0,
            commander = false, foil = false, addedInApp = addedInApp, maybeboard = maybeboard,
        )

    // ---- Reading the maybeboard from Archidekt ------------------------------------------------

    private val sample = """
        {"id": 20263351, "name": "Auntie Plague", "owner": {"id": 1, "username": "joelvdc"},
         "categories": [
           {"name": "Commander", "isPremier": true, "includedInDeck": true},
           {"name": "Ramp", "isPremier": false, "includedInDeck": true},
           {"name": "Maybeboard", "isPremier": false, "includedInDeck": false},
           {"name": "Sideboard", "isPremier": false, "includedInDeck": false}
         ],
         "cards": [
           {"categories": ["Commander"], "quantity": 1, "modifier": "Normal",
            "card": {"uid": "9a2e4252", "oracleCard": {"name": "Auntie Ool, Cursewretch", "types": ["Creature"], "colorIdentity": ["Black", "Red", "Green"]}}},
           {"categories": ["Ramp"], "quantity": 1, "modifier": "Normal",
            "card": {"uid": "abc12345", "oracleCard": {"name": "Sol Ring", "cmc": 1, "types": ["Artifact"]}}},
           {"categories": ["Maybeboard"], "quantity": 2, "modifier": "Foil",
            "card": {"uid": "def67890", "edition": {"editioncode": "c21", "editionname": "Commander 2021"},
                     "oracleCard": {"name": "The Ozolith", "cmc": 1, "types": ["Legendary", "Artifact"]}}},
           {"categories": ["Sideboard", "Commander"], "quantity": 1, "modifier": "Normal",
            "card": {"uid": "fed09876", "oracleCard": {"name": "Prossh, Skyraider of Kher", "types": ["Creature"]}}}
         ]}
    """.trimIndent()

    @Test
    fun theMaybeboardIsReadApartFromTheDeck() {
        val d = ArchidektApi.parse(sample)
        // The deck itself is as before: its cards and count leave the maybeboard out.
        assertEquals(listOf("Auntie Ool, Cursewretch", "Sol Ring"), d.cards.map { it.name })
        assertEquals(2, d.cardCount)
        // Every category left out of the deck is kept as the maybeboard, under its own name.
        assertEquals(listOf("The Ozolith", "Prossh, Skyraider of Kher"), d.maybeboard.map { it.name })
        val ozolith = d.maybeboard[0]
        assertEquals("Maybeboard", ozolith.category)
        assertEquals(2, ozolith.quantity)
        assertTrue(ozolith.foil)
        // A maybeboard card that's also tagged as commander isn't one of the deck's commanders.
        assertEquals("Sideboard", d.maybeboard[1].category)
        assertFalse(d.maybeboard[1].commander)
        assertEquals(listOf("Auntie Ool, Cursewretch"), d.commanders.map { it.name })
    }

    // ---- Kept apart in the database ---------------------------------------------------------------

    @Test
    fun maybeboardCardsDontCountAsTheDecksCards() = runBlocking {
        val dao = db.deckDao()
        dao.upsert(deck())
        dao.insertCards(listOf(card("Sol Ring"), card("The Ozolith", maybeboard = true, category = "Maybeboard", qty = 2), card("Arcane Signet", addedInApp = true)))
        assertEquals(listOf("Arcane Signet", "Sol Ring"), dao.cards(7).map { it.card.name }.sorted())
        assertEquals(listOf("Arcane Signet", "Sol Ring"), dao.observeCards(7).first().map { it.item.card.name }.sorted())
        assertEquals(listOf("The Ozolith"), dao.observeMaybeboard(7).first().map { it.item.card.name })
        // What the collection must keep for the decks, the names shown as "in a deck" and "cards in several decks".
        assertFalse(dao.cardCounts().any { it.name == "The Ozolith" })
        assertFalse("The Ozolith" in dao.observeCardNames().first())
        assertFalse(dao.observeUsage().first().any { it.name == "The Ozolith" })
        assertEquals(listOf("Arcane Signet"), dao.cardsAddedInApp(7).map { it.card.name })
    }

    @Test
    fun reloadingReplacesTheMaybeboardButKeepsCardsAddedInTheApp() = runBlocking {
        val dao = db.deckDao()
        dao.upsert(deck())
        dao.insertCards(listOf(card("Sol Ring"), card("The Ozolith", maybeboard = true), card("Arcane Signet", addedInApp = true)))
        dao.replace(deck(), listOf(card("Sol Ring"), card("Mana Vault", maybeboard = true)))
        assertEquals(listOf("Arcane Signet", "Sol Ring"), dao.cards(7).map { it.card.name }.sorted())
        assertEquals(listOf("Mana Vault"), dao.observeMaybeboard(7).first().map { it.item.card.name })
    }

    // ---- Adding cards from the deck page -------------------------------------------------------------

    @Test
    fun theDeckIsATargetForSearchAndTheScanner() {
        assertEquals("deck:7", CardTarget.ToDeck(7).encode())
        assertEquals(CardTarget.ToDeck(7), CardTarget.decode("deck:7"))
        assertFalse(CardTarget.ToDeck(7).isReplace)
    }

    @Test
    fun addingFromTheDeckPageMakesACardAddedInTheApp() = runBlocking {
        db.deckDao().upsert(deck())
        // Its type groups it in the deck; it comes from the details saved for the card (else from Scryfall).
        db.cardInfoDao().putAll(listOf(CardInfo("arcane-signet", null, "", "", "Artifact — Equipment", 2.0, null)))
        val first = repo.add(CardTarget.ToDeck(7), ref("Arcane Signet"), Finish.NONFOIL)!!
        val again = repo.add(CardTarget.ToDeck(7), ref("Arcane Signet"), Finish.NONFOIL)!!
        assertEquals(first.itemId, again.itemId)
        val added = db.deckDao().cardsAddedInApp(7).single()
        assertEquals(2, added.quantity)
        assertEquals(DeckToCollection.ADDED_IN_APP, added.category)
        assertEquals("Artifact", added.types)
        assertEquals(2.0, added.cmc, 0.0)
        assertFalse(added.maybeboard)
        // A foil copy is its own line.
        repo.add(CardTarget.ToDeck(7), ref("Arcane Signet"), Finish.FOIL)
        assertEquals(2, db.deckDao().cardsAddedInApp(7).size)
        // The scanner's undo takes one copy off, then the line.
        repo.undoAdd(again)
        assertEquals(1, db.deckDao().card(first.itemId)!!.quantity)
        repo.undoAdd(first)
        assertNull(db.deckDao().card(first.itemId))
        assertNull(first.replaceTarget)
    }

    @Test
    fun aCardAddedInTheAppDoesntJoinAMaybeboardCopy() = runBlocking {
        db.deckDao().upsert(deck())
        db.cardInfoDao().putAll(listOf(CardInfo("the-ozolith", null, "", "", "Legendary Artifact", 1.0, null)))
        db.deckDao().insertCards(listOf(card("The Ozolith", maybeboard = true, addedInApp = false)))
        repo.add(CardTarget.ToDeck(7), ref("The Ozolith"), Finish.NONFOIL)
        assertEquals(1, db.deckDao().cards(7).size)
        assertEquals(1, db.deckDao().observeMaybeboard(7).first().size)
    }

    // ---- Settings for the deck page --------------------------------------------------------------------

    @Test
    fun compactViewMaybeboardAndFoldedSectionsAreRemembered() {
        val s = Settings(context)
        assertFalse(s.deckCompact.value)
        assertFalse(s.deckMaybeboard.value)
        s.setDeckCompact(true)
        s.setDeckMaybeboard(true)
        s.setDeckCollapsed(setOf("Lands", "maybeboard:Maybeboard"))
        val again = Settings(context)
        assertTrue(again.deckCompact.value)
        assertTrue(again.deckMaybeboard.value)
        assertEquals(setOf("Lands", "maybeboard:Maybeboard"), again.deckCollapsed.value)
    }

    // ---- Sync, backups and the update -------------------------------------------------------------------

    @Test
    fun theMaybeboardGoesThroughSync() {
        val data = SyncData(decks = listOf(SyncDeck(deck(), listOf(card("Sol Ring"), card("The Ozolith", maybeboard = true)))))
        val back = SyncFile.decode(SyncFile.encode(SyncFile(data = data))).data.decks.single().cards
        assertEquals(listOf(false, true), back.map { it.maybeboard })
        // An older version would count the maybeboard as part of the deck: it refuses format 8 and asks to be updated.
        assertEquals(8, SyncFile.FORMAT)
        assertEquals(8, BackupFile.FORMAT)
    }

    /** A version 18 database (app 1.39–1.41) exactly as Room made it, from the exported schema. */
    private fun createVersion18(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/18.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL(
            "INSERT INTO decks (archidektId, name, owner, commanders, colorIdentity, cardCount, importedAt, updatedAt) " +
                "VALUES (7, 'Auntie Plague', 'joelvdc', 'Auntie Ool', 'BRG', 1, 1, 5)"
        )
        sql.execSQL(
            "INSERT INTO deck_cards (id, deckId, quantity, category, types, cmc, commander, foil, etched, gameChanger, addedInApp, " +
                "scryfallId, name, setCode, setName, collectorNumber, rarity, hasNonFoil, hasFoil, hasEtched) " +
                "VALUES (1, 7, 1, 'Ramp', 'Artifact', 1.0, 0, 0, 0, 1, 0, 'sr', 'Sol Ring', 'c21', 'Commander 2021', '263', 'uncommon', 1, 0, 0)"
        )
        sql.version = 18
        sql.close()
    }

    @Test
    fun updatingFromVersion18KeepsEveryCardInTheDeck() = runBlocking {
        val file = context.getDatabasePath("update-142.db").apply { parentFile?.mkdirs(); delete() }
        createVersion18(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-142.db")
            .addMigrations(AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val cards = updated.deckDao().cards(7)
            assertEquals(listOf("Sol Ring"), cards.map { it.card.name })
            assertFalse(cards.single().maybeboard)
            // The update doesn't count as a change: nothing to sync until the user edits something.
            assertEquals(5, updated.deckDao().get(7)!!.updatedAt)
        } finally {
            updated.close()
        }
    }
}
