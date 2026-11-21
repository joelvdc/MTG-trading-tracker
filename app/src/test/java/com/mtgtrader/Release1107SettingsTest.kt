package com.mtgtrader

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckCopies
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SortLevelSetting
import com.mtgtrader.data.SortLevelsCodec
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.Together
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 1.107: the sort's levels and options, synced; only active decks (yours, or local) hold cards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release1107SettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun clearPrefs() = context.getSharedPreferences("settings", 0).edit().clear().commit()

    private fun ref(id: String, name: String) = CardRef(id, name, "cmr", "Commander Legends", "1", "rare", null, 1, 1.0, null, true, true)

    private fun line(deck: Long, id: String, name: String) =
        DeckCard(deckId = deck, card = ref(id, name), quantity = 1, category = "Ramp", types = "Artifact", cmc = 1.0, commander = false, foil = false)

    // ---- The levels' lines ------------------------------------------------------------------------

    @Test
    fun levelsRoundTripThroughTheirLines() {
        val levels = listOf(
            SortLevelSetting("7", 200, 0.5),
            SortLevelSetting("8", 1_000, 2.0),
            SortLevelSetting("9", 0, null),
        )
        val lines = SortLevelsCodec.encode(levels)
        assertEquals("7|200|0.5\n8|1000|2.0\n9|0|", lines)
        assertEquals(levels, SortLevelsCodec.decode(lines))
        // Whole numbers keep a .0, which the website reads too.
        assertEquals("7|200|5.0", SortLevelsCodec.encode(listOf(SortLevelSetting("7", 200, 5.0))))
    }

    @Test
    fun badLevelLinesAreSkipped() {
        val lines = listOf(
            "7|200|0.5", // good
            "",
            "|200|0.5", // no binder
            "8|x|", // max not a number
            "8|-1|", // max below zero
            "9|10|abc", // min price not a number
            "9|10|-2", // min price below zero
            "10|50",
        ).joinToString("\n")
        assertEquals(listOf(SortLevelSetting("7", 200, 0.5), SortLevelSetting("10", 50, null)), SortLevelsCodec.decode(lines))
        assertTrue(SortLevelsCodec.decode(null).isEmpty())
        assertTrue(SortLevelsCodec.decode("").isEmpty())
    }

    @Test
    fun atMostSixLevels() {
        val many = (1..9).map { SortLevelSetting("$it", 100, null) }
        assertEquals(6, SortLevelsCodec.decode(SortLevelsCodec.encode(many)).size)
        assertEquals(6, SortLevelsCodec.decode(many.joinToString("\n") { "${it.binder}|100|" }).size)
    }

    @Test
    fun theOldThreeBindersBecomeLevels() {
        assertEquals(
            listOf(SortLevelSetting("3", 200, 0.5), SortLevelSetting("4", 1_000, 0.5), SortLevelSetting("5", 0, null)),
            SortLevelsCodec.legacy("3", "4", "5", 200, 1_000, 0, 0.5),
        )
        // Binders that aren't set leave no level.
        assertEquals(listOf(SortLevelSetting("3", 200, 1.0)), SortLevelsCodec.legacy("3", "0", null, 200, 1_000, 0, 1.0))
        assertEquals(listOf(SortLevelSetting("5", 0, null)), SortLevelsCodec.legacy("", "0", "5", 200, 1_000, 0, 0.5))
        assertTrue(SortLevelsCodec.legacy(null, null, null, 200, 1_000, 0, 0.5).isEmpty())
    }

    // ---- Settings ---------------------------------------------------------------------------------

    @Test
    fun theLevelsFallBackToTheOldBinders() {
        clearPrefs()
        val s = Settings(context)
        assertTrue(s.sortLevels.isEmpty())
        s.sortTopId = 7
        s.sortMidId = 8
        // The limits and floor older versions saved (they have no levels).
        val prefs = context.getSharedPreferences("settings", 0)
        prefs.edit().putInt("sortMaxTop", 50).putInt("sortMaxMid", 300).putInt("sortMaxBulk", 5)
            .putFloat("sortFloor", 1.25f).apply()
        assertEquals(listOf(SortLevelSetting("7", 50, 1.25), SortLevelSetting("8", 300, 1.25)), s.sortLevels)
        s.sortBulkId = 9
        assertEquals(listOf(SortLevelSetting("7", 50, 1.25), SortLevelSetting("8", 300, 1.25), SortLevelSetting("9", 5, null)), s.sortLevels)
    }

    @Test
    fun threeLevelsKeepTheOldKeysInStep() {
        clearPrefs()
        val s = Settings(context)
        s.sortLevels = listOf(SortLevelSetting("11", 40, 2.0), SortLevelSetting("12", 500, 2.0), SortLevelSetting("13", 0, null))
        assertEquals(listOf(11L, 12L, 13L), listOf(s.sortTopId, s.sortMidId, s.sortBulkId))
        // The old keys, which versions before 1.107 read.
        val prefs = context.getSharedPreferences("settings", 0)
        assertEquals(40, prefs.getInt("sortMaxTop", -1))
        assertEquals(500, prefs.getInt("sortMaxMid", -1))
        assertEquals(0, prefs.getInt("sortMaxBulk", -1))
        assertEquals(2.0f, prefs.getFloat("sortFloor", -1f), 1e-6f)
        assertEquals(3, s.sortLevels.size)
    }

    @Test
    fun twoLevelsLeaveTheOldKeysAlone() {
        clearPrefs()
        val s = Settings(context)
        s.sortTopId = 7
        s.sortMidId = 8
        s.sortBulkId = 9
        s.sortLevels = listOf(SortLevelSetting("11", 40, 2.0), SortLevelSetting("12", 0, null))
        assertEquals(listOf(7L, 8L, 9L), listOf(s.sortTopId, s.sortMidId, s.sortBulkId))
        assertEquals(listOf(SortLevelSetting("11", 40, 2.0), SortLevelSetting("12", 0, null)), s.sortLevels)
    }

    @Test
    fun theNewOptionsFollowThroughSync() {
        clearPrefs()
        val s = Settings(context)
        assertEquals(Together.NAME, s.sortTogether)
        assertEquals(3.0, s.sortOutlierRatio, 1e-9)
        assertEquals(5.0, s.sortOutlierGap, 1e-9)
        assertEquals(DeckCopies.BEST, s.sortDeckCopies)
        assertEquals(0L, s.sortDecksId)
        s.sortLevels = listOf(SortLevelSetting("21", 100, null), SortLevelSetting("22", 0, null))
        s.sortDecksId = 23
        s.sortTogether = Together.PRINTING
        s.sortOutlierRatio = 1.5
        s.sortOutlierGap = 12.0
        s.sortDeckCopies = DeckCopies.CHEAPEST

        val prefs = s.syncedPrefs()
        for (key in listOf("sortTogether", "sortOutlierRatio", "sortOutlierGap", "sortDeckCopies")) assertTrue(key, key in prefs.values)

        clearPrefs()
        val other = Settings(context)
        other.applySyncedPrefs(prefs)
        assertEquals(Together.PRINTING, other.sortTogether)
        assertEquals(1.5, other.sortOutlierRatio, 1e-9)
        assertEquals(12.0, other.sortOutlierGap, 1e-9)
        assertEquals(DeckCopies.CHEAPEST, other.sortDeckCopies)
        // The levels and the Decks binder go by their binders' sync ids (SyncStore), not here.
        assertTrue(other.sortLevels.isEmpty())
        assertEquals(0L, other.sortDecksId)

        // Values out of range or unknown are left out.
        clearPrefs()
        val fresh = Settings(context)
        fresh.applySyncedPrefs(prefs.copy(values = prefs.values + mapOf("sortOutlierRatio" to "0.5", "sortOutlierGap" to "-1", "sortTogether" to "nope", "sortDeckCopies" to "nope")))
        assertEquals(Together.NAME, fresh.sortTogether)
        assertEquals(3.0, fresh.sortOutlierRatio, 1e-9)
        assertEquals(5.0, fresh.sortOutlierGap, 1e-9)
        assertEquals(DeckCopies.BEST, fresh.sortDeckCopies)
    }

    @Test
    fun yourUsernameAndNoOtherDecidesTheOwner() {
        clearPrefs()
        val s = Settings(context)
        s.archidektUser = "importedFrom"
        assertNull(s.deckOwner())
        s.setOwnArchidektUser("joelvdc")
        assertEquals("joelvdc", s.deckOwner())
        s.setOwnArchidektUser("  ")
        assertNull(s.deckOwner())
    }

    // ---- Only active decks hold cards -------------------------------------------------------------

    @Test
    fun onlyYourDecksAndYourLocalOnesHoldCards() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val dao = db.deckDao()
            dao.upsert(Deck(1, "Mine", "joelvdc", "A", null, null, "U", 100))
            dao.upsert(Deck(2, "Followed", "someoneelse", "B", null, null, "R", 100))
            dao.upsert(Deck(3, "Retired", "joelvdc", "C", null, null, "G", 100))
            dao.upsert(Deck(4, "Local", "", "D", null, null, "W", 100).copy(archidektId = -4))
            dao.upsert(Deck(5, "MineInOtherCase", "JoelVDC", "E", null, null, "B", 100))
            dao.insertCards(
                listOf(
                    line(1, "sol", "Sol Ring"),
                    line(2, "goblin", "Goblin Bombardment"),
                    line(3, "krenko", "Krenko, Mob Boss"),
                    line(-4, "signet", "Arcane Signet"),
                    line(5, "emblem", "Emblem"),
                ),
            )
            dao.setRetired(3, true)

            assertEquals(
                listOf("Arcane Signet", "Emblem", "Sol Ring"),
                dao.cardCounts("joelvdc").map { it.name }.sorted(),
            )
            assertEquals(listOf("Arcane Signet", "Emblem", "Sol Ring"), dao.observeCardNames("joelvdc").first().sorted())
            // Without a username only the local deck counts.
            assertEquals(listOf("Arcane Signet"), dao.cardCounts("").map { it.name })
            assertEquals(listOf("Arcane Signet"), dao.observeCardNames("").first())
        } finally {
            db.close()
        }
    }
}
