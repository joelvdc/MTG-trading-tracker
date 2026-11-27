package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckHistory
import com.mtgtrader.data.DeckMerge
import com.mtgtrader.data.DeckSnapshots
import com.mtgtrader.data.SyncDeck
import com.mtgtrader.data.SyncSchema
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
import java.time.Instant

/**
 * 1.124: a deck's snapshots. The phone and the website write the same fields, so the tests here are about the format
 * (newest first, at most 25, the same comparison rule as the change history) and about the merge.
 */
class Release1124SnapshotTest {
    /** 2026-11-27 at 10:00 in UTC. */
    private val day = Instant.parse("2026-11-27T10:00:00Z").toEpochMilli()

    private fun line(name: String, quantity: Int = 1, set: String = "cmm", number: String = "400", commander: Boolean = false, maybeboard: Boolean = false) =
        DeckCard(
            deckId = 1,
            card = CardRef(
                scryfallId = "id-${name.lowercase().replace(' ', '-')}", name = name, setCode = set, setName = "Test",
                collectorNumber = number, rarity = "common", imageUrl = null, cardmarketId = null,
                fallbackEur = null, fallbackEurFoil = null, hasNonFoil = true, hasFoil = false,
            ),
            quantity = quantity,
            category = "Deck",
            types = "Artifact",
            cmc = 1.0,
            commander = commander,
            foil = false,
            maybeboard = maybeboard,
        )

    private fun card(name: String, quantity: Int = 1, set: String = "cmm", number: String = "400", commander: Boolean = false) =
        DeckSnapshots.SnapCard(name, quantity, set, number, commander)

    private fun deck(snapshots: String? = null, snapshotsAt: Long? = null, importedAt: Long = 0) =
        Deck(
            7, "Deck", "me", "Commander", null, null, "", 0,
            importedAt = importedAt, snapshots = snapshots, snapshotsAt = snapshotsAt,
        )

    // ---- Taking, renaming and deleting ---------------------------------------------------------

    @Test
    fun takeKeepsTheNewestTwentyFive() {
        var text: String? = null
        for (i in 1..26) text = DeckSnapshots.take(text, day + i, "snapshot $i", listOf(card("Sol Ring")), null, null, null, id = "id$i")
        val snapshots = DeckSnapshots.parse(text)
        assertEquals(DeckSnapshots.MAX, snapshots.size)
        // Newest first, and the one that fell off is the oldest.
        assertEquals("id26", snapshots.first().id)
        assertEquals("id2", snapshots.last().id)
        assertTrue(snapshots.none { it.id == "id1" })
    }

    @Test
    fun takeNamesASnapshotAndKeepsWhatTheDeckShowed() {
        val text = DeckSnapshots.take(null, day, "  Before upgrade  ", listOf(card("Sol Ring")), 3, "T11", 263.28, id = "abc")
        val s = DeckSnapshots.parse(text).single()
        assertEquals("Before upgrade", s.name)
        assertEquals(day, s.at)
        assertEquals(3, s.bracket)
        assertEquals("T11", s.winTurn)
        assertEquals(263.28, s.eur!!, 0.0001)
        assertEquals(listOf(card("Sol Ring")), s.cards)
        // A blank name is the UTC date, and a long one is cut.
        assertEquals("2026-11-27", DeckSnapshots.parse(DeckSnapshots.take(null, day, "  ", emptyList(), null, null, null)).single().name)
        assertEquals(DeckSnapshots.MAX_NAME, DeckSnapshots.parse(DeckSnapshots.take(null, day, "x".repeat(200), emptyList(), null, null, null)).single().name.length)
    }

    @Test
    fun renameAndDelete() {
        val text = DeckSnapshots.take(DeckSnapshots.take(null, day, "one", emptyList(), null, null, null, id = "a"), day, "two", emptyList(), null, null, null, id = "b")
        val renamed = DeckSnapshots.parse(DeckSnapshots.rename(text, "a", "  first  ")).first { it.id == "a" }
        assertEquals("first", renamed.name)
        // A blank name keeps the one it has.
        assertEquals("first", DeckSnapshots.parse(DeckSnapshots.rename(DeckSnapshots.rename(text, "a", "first"), "a", "   ")).first { it.id == "a" }.name)
        // Deleting one leaves the other; deleting the last leaves nothing.
        val one = DeckSnapshots.delete(text, "a")
        assertEquals(listOf("b"), DeckSnapshots.parse(one).map { it.id })
        assertNull(DeckSnapshots.delete(one, "b"))
    }

    @Test
    fun unreadableOrUnknownSnapshotsReadAsNothing() {
        assertTrue(DeckSnapshots.parse(null).isEmpty())
        assertTrue(DeckSnapshots.parse("not json at all").isEmpty())
        assertTrue(DeckSnapshots.parse("""{"id":"a"}""").isEmpty())
        // Unknown keys are ignored, as the website may write more later.
        val text = """[{"id":"a","name":"x","at":1,"cards":[],"bracket":3,"fromTheWebsite":true}]"""
        assertEquals("x", DeckSnapshots.parse(text).single().name)
    }

    // ---- The deck's lines as a snapshot keeps them ---------------------------------------------

    @Test
    fun cardsOfLeavesTheMaybeboardOutAndSorts() {
        val cards = DeckSnapshots.cardsOf(
            listOf(line("Sol Ring", set = "cmm", number = "400"), line("Pet card", maybeboard = true), line("Arcane Signet", set = "c21", number = "263"), line("Atraxa", commander = true))
        )
        assertEquals(listOf("Arcane Signet", "Atraxa", "Sol Ring"), cards.map { it.name })
        assertTrue(cards.none { it.name == "Pet card" })
        assertEquals(listOf(false, true, false), cards.map { it.commander })
        assertEquals("cmm", cards.last().set)
        assertEquals("400", cards.last().number)
    }

    @Test
    fun decklistTextPutsTheCommandersFirst() {
        val snapshot = DeckSnapshots.parse(
            DeckSnapshots.take(
                null, day, "list",
                listOf(card("Sol Ring", 2, "cmm", "400"), card("Atraxa, Praetors' Voice", 1, "2xm", "200", commander = true)),
                null, null, null,
            )
        ).single()
        assertEquals("1 Atraxa, Praetors' Voice (2XM) 200\n2 Sol Ring (CMM) 400", DeckSnapshots.decklistText(snapshot))
    }

    // ---- Comparing ---------------------------------------------------------------------------

    @Test
    fun compareAddsUpTheCopiesByName() {
        val old = listOf(card("Sol Ring", 1, "cmm"), card("Sol Ring", 2, "c21"), card("Forest", 2, "ust"), card("Rhystic Study"))
        val new = listOf(card("Sol Ring", 4, "cmm"), card("Forest", 1, "ust"), card("Arcane Signet"))
        assertEquals(
            DeckHistory.Change(mapOf("Arcane Signet" to 1, "Sol Ring" to 1), mapOf("Forest" to 1, "Rhystic Study" to 1)),
            DeckSnapshots.compare(old, new),
        )
        // Nothing changed: one line and two lines of the same card come to the same.
        assertTrue(DeckSnapshots.compare(listOf(card("Sol Ring", 3)), listOf(card("Sol Ring", 1), card("Sol Ring", 2, "c21"))).isEmpty)
    }

    // ---- Merging two phones' decks -----------------------------------------------------------

    @Test
    fun deckMergeKeepsTheNewerSnapshots() {
        val a = SyncDeck(deck(snapshots = """[{"id":"a","name":"A","at":1,"cards":[]}]""", snapshotsAt = 10))
        val b = SyncDeck(deck(snapshots = """[{"id":"b","name":"B","at":2,"cards":[]}]""", snapshotsAt = 20))
        assertEquals("""[{"id":"b","name":"B","at":2,"cards":[]}]""", DeckMerge.merge(a, b).deck.snapshots)
        assertEquals(20L, DeckMerge.merge(a, b).deck.snapshotsAt)
        // Whichever copy is the newer list, the newer snapshots win.
        val newerList = SyncDeck(deck(snapshots = """[{"id":"a","name":"A","at":1,"cards":[]}]""", snapshotsAt = 10, importedAt = 1_000))
        val olderList = SyncDeck(deck(snapshots = """[{"id":"b","name":"B","at":2,"cards":[]}]""", snapshotsAt = 20, importedAt = 500))
        assertEquals(20L, DeckMerge.merge(newerList, olderList).deck.snapshotsAt)
        // A copy without snapshots counts as older than one with them, and a tie keeps the list's copy.
        assertEquals(20L, DeckMerge.merge(SyncDeck(deck()), b).deck.snapshotsAt)
        assertNull(DeckMerge.merge(SyncDeck(deck()), SyncDeck(deck())).deck.snapshotsAt)
    }
}

/**
 * 1.124 against a real database: a version 31 database (app 1.123) takes the snapshots' two columns, and every deck
 * starts without them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release1124DbTest {
    private val context get() = RuntimeEnvironment.getApplication()

    /** A version 31 database exactly as Room made it, from the exported schema. */
    private fun createVersion31(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/31.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.version = 31
        sql.close()
    }

    @Test
    fun updatingFromVersion31AddsTheSnapshotColumns() = runBlocking {
        val file = context.getDatabasePath("update-32.db").apply { parentFile?.mkdirs(); delete() }
        createVersion31(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-32.db")
            .addMigrations(AppDatabase.MIGRATION_31_32)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val dao = updated.deckDao()
            dao.upsert(Deck(7, "Deck", "me", "Commander", null, null, "", 0))
            val deck = dao.get(7)!!
            assertNull(deck.snapshots)
            assertNull(deck.snapshotsAt)
            // The columns are there and keep what's written in them.
            dao.update(deck.copy(snapshots = """[{"id":"a","name":"A","at":1,"cards":[]}]""", snapshotsAt = 5))
            assertEquals(5L, dao.get(7)!!.snapshotsAt)
            assertEquals(1, DeckSnapshots.parse(dao.get(7)!!.snapshots).size)
        } finally {
            updated.close()
        }
    }
}
