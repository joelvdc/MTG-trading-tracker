package com.mtgtrader

import com.mtgtrader.data.ArchidektExport
import com.mtgtrader.data.ArchidektFarewell
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.Finish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 1.78: the collection goes to Archidekt as a CSV file for its "ManaBox – Single folder" importer. */
class Release178Test {
    @get:Rule val tmp = TemporaryFolder()

    private fun ref(id: String, name: String, set: String = "cmr") =
        CardRef(id, name, set, "Commander Legends", "12", "rare", null, 1, 1.0, null, true, true)

    private fun item(id: String, name: String, qty: Int, foil: Boolean = false, binder: Long = 0, condition: String = "NM", language: String = "EN") =
        CollectionItem(card = ref(id, name), foil = foil, quantity = qty, binderId = binder, condition = condition, language = language)

    @Test
    fun copiesInDifferentBindersBecomeOneLine() {
        val lines = ArchidektExport.lines(
            listOf(item("sol", "Sol Ring", 1, binder = 1), item("sol", "Sol Ring", 2, binder = 2), item("sol", "Sol Ring", 1, foil = true), item("arc", "Arcane Signet", 1))
        )
        assertEquals(listOf("Arcane Signet", "Sol Ring", "Sol Ring"), lines.map { it.name })
        assertEquals(3, lines.first { it.name == "Sol Ring" && it.finish == Finish.NONFOIL }.quantity)
        assertEquals(1, lines.first { it.finish == Finish.FOIL }.quantity)
    }

    @Test
    fun theColumnsArchidektsPresetReads() {
        val csv = ArchidektExport.csv(ArchidektExport.lines(listOf(item("abc-123", "Sol Ring", 2, foil = true, condition = "LP", language = "DE"))))
        val rows = csv.trim().lines()
        assertEquals(2, rows.size)
        assertEquals(18, rows[0].split(",").size)
        val f = rows[1].split(",")
        assertEquals(18, f.size)
        assertEquals("Sol Ring", f[0]) // 1 name
        assertEquals("CMR", f[1]) // 2 set code
        assertEquals("Commander Legends", f[2]) // 3 set name
        assertEquals("12", f[3]) // 4 collector number
        assertEquals("foil", f[4]) // 5 foil
        assertEquals("2", f[6]) // 7 quantity
        assertEquals("abc-123", f[8]) // 9 Scryfall ID
        assertEquals("light_played", f[13]) // 14 condition
        assertEquals("de", f[14]) // 15 language
    }

    @Test
    fun onlyWhatsNewSinceTheLastExport() {
        val dir = tmp.root
        assertNull(ArchidektExport.lastExport(dir))
        val first = ArchidektExport.lines(listOf(item("sol", "Sol Ring", 1), item("arc", "Arcane Signet", 1)))
        // Without an earlier export, everything is new.
        assertEquals(first, ArchidektExport.since(first, null))
        ArchidektExport.saveExport(dir, ArchidektExport.snapshotOf(first, at = 42))
        val last = ArchidektExport.lastExport(dir)!!
        assertEquals(42L, last.at)
        // A second Sol Ring and a new card; the Arcane Signet was removed (not in the file).
        val now = ArchidektExport.lines(listOf(item("sol", "Sol Ring", 2), item("cult", "Cultivate", 1)))
        val new = ArchidektExport.since(now, last)
        assertEquals(listOf("Cultivate" to 1, "Sol Ring" to 1), new.map { it.name to it.quantity })
        // Saving again replaces the file.
        ArchidektExport.saveExport(dir, ArchidektExport.snapshotOf(now, at = 43))
        assertEquals(43L, ArchidektExport.lastExport(dir)!!.at)
        assertTrue(ArchidektExport.since(now, ArchidektExport.lastExport(dir)).isEmpty())
    }

    @Test
    fun theOldLoginGivesTheUsername() {
        assertEquals("joelvdc", ArchidektFarewell.usernameOf("""{"username": "joelvdc", "access": "x"}"""))
        assertNull(ArchidektFarewell.usernameOf("""{"username": " "}"""))
        assertNull(ArchidektFarewell.usernameOf("not json"))
    }
}
