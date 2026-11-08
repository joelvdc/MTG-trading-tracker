package com.mtgtrader

import androidx.room.Room
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckGroup
import com.mtgtrader.data.SyncFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 1.83: retired decks keep their page and games, but leave the groups, the pickers and the collection checks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Release183Test {
    private fun ref(id: String, name: String) = CardRef(id, name, "cmr", "Commander Legends", "1", "rare", null, 1, 1.0, null, true, true)
    private fun line(deck: Long, id: String, name: String) =
        DeckCard(deckId = deck, card = ref(id, name), quantity = 1, category = "Ramp", types = "Artifact", cmc = 1.0, commander = false, foil = false)

    @Test fun retiredDecksDontCountForTheCollection() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) = com.mtgtrader.data.SyncSchema.install(db)
            })
            .build()
        val dao = db.deckDao()
        dao.upsert(Deck(1, "Vohar Danse", "joelvdc", "Vohar", null, null, "UB", 100))
        dao.upsert(Deck(2, "Old Krenko", "joelvdc", "Krenko", null, null, "R", 100))
        dao.insertCards(listOf(line(1, "sol", "Sol Ring"), line(2, "sol", "Sol Ring"), line(2, "goblin", "Goblin Bombardment")))
        dao.setRetired(2, true)
        val old = dao.get(2)!!
        assertTrue(old.retired)
        assertTrue("retiring stamps the deck for sync", old.updatedAt > 0)
        assertEquals(listOf("Sol Ring" to 1), dao.cardCounts().map { it.name to it.qty })
        assertEquals(listOf("Sol Ring"), dao.observeCardNames().first())
        assertEquals(listOf(1L), dao.observeUsage().first().map { it.deckId })
        assertEquals(DeckGroup.RETIRED, DeckGroup.of(old, "joelvdc"))
        // A reload from Archidekt keeps it retired (the import copies the old row); bringing it back counts it again.
        dao.setRetired(2, false)
        assertEquals(2, dao.cardCounts().single { it.name == "Sol Ring" }.qty)
        db.close()
    }

    @Test fun theSyncFileCarriesRetired() {
        assertEquals(13, SyncFile.FORMAT)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(Deck.serializer(), Deck(2, "Old Krenko", "", "", null, null, "R", 1, retired = true))
        assertTrue(json.contains("\"retired\":true"))
    }
}
