package com.mtgtrader

import com.mtgtrader.data.ArchidektCodes
import com.mtgtrader.data.BackupInfo
import com.mtgtrader.data.BackupPlace
import com.mtgtrader.data.BackupRestore
import com.mtgtrader.data.BackupRetention
import com.mtgtrader.data.Backups
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.Finish
import com.mtgtrader.data.NextcloudClient
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncDeletion
import com.mtgtrader.data.SyncStack
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 1.21's Archidekt collection sync planner was removed in 1.78 with the sync (and its tests).

class ArchidektCodesTest {
    @Test
    fun conditionsMapBothWays() {
        assertEquals("NM", ArchidektCodes.archCondition("MT"))
        assertEquals("LP", ArchidektCodes.archCondition("EX"))
        assertEquals("LP", ArchidektCodes.archCondition("LP"))
        assertEquals("MP", ArchidektCodes.archCondition("GD"))
        assertEquals("LP", ArchidektCodes.appCondition("LP"))
        assertEquals("D", ArchidektCodes.archCondition("PO"))
        for ((arch, app) in ArchidektCodes.toAppCondition) assertEquals(arch, ArchidektCodes.archCondition(app))
    }

    @Test
    fun archidektSendsNumbers() {
        assertEquals("MP", ArchidektCodes.conditionCode(JsonPrimitive(3)))
        assertEquals("HP", ArchidektCodes.conditionCode(JsonPrimitive("Heavily Played")))
        assertEquals("DE", ArchidektCodes.languageCode(JsonPrimitive(3)))
        assertEquals("SP", ArchidektCodes.archLanguage("ES"))
        assertEquals("ZHS", ArchidektCodes.appLanguage("CS"))
        assertEquals(Finish.ETCHED, ArchidektCodes.finish("Etched"))
    }
}

class BackupTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun keepsTheLatestTenAndOnePerWeek() {
        val now = 1_000L * day
        val autos = (0 until 60).map { d -> BackupInfo(BackupPlace.PHONE, "a$d", "a$d", now - d * day - 1000, "Daily backup", false, 1) }
        val manual = BackupInfo(BackupPlace.PHONE, "m", "m", now - 90 * day, "Made by hand", true, 1)
        val gone = BackupRetention.toDelete(autos + manual, now).toSet()
        val kept = (autos + manual).filterNot { it in gone }
        assertTrue(manual in kept)
        assertTrue(autos.take(10).all { it in kept })
        // Days 0-9 cover weeks 0 and 1; weeks 2-7 keep their newest: days 14, 21, 28, 35, 42, 49.
        assertEquals(10 + 6 + 1, kept.size)
        assertTrue(autos[14] in kept && autos[49] in kept)
        assertFalse(autos[50] in kept)
    }

    @Test
    fun fileNamesRoundTrip() {
        val at = 1_760_000_000_000L
        val name = Backups.fileName(at, "Before an Archidekt sync", manual = false)
        val info = Backups.parse(name)!!
        assertEquals(at / 1000, info.createdAt / 1000)
        assertEquals("Before an Archidekt sync", info.reason)
        assertFalse(info.manual)
        assertTrue(Backups.parse(Backups.fileName(at, "Made by hand", manual = true))!!.manual)
        assertNull(Backups.parse("sync.json.gz"))
    }

    private fun stack(uid: String, qty: Int, updatedAt: Long = 5) = SyncStack(
        CollectionItem(
            card = CardRef(uid, uid, "tst", "Test", "1", "rare", null, 1, null, null, true, false),
            foil = false, quantity = qty, uid = uid, updatedAt = updatedAt, binderId = Binder.UNSORTED, addedAt = 1,
        ),
    )

    @Test
    fun restoringStampsChangesAndBuriesWhatsGone() {
        val now = 1000L
        val current = SyncData(collection = listOf(stack("a", 1), stack("b", 2), stack("new", 1)), deletions = listOf(SyncDeletion("c", 7)))
        val backup = SyncData(collection = listOf(stack("a", 1), stack("b", 5), stack("c", 1)))
        val t = BackupRestore.target(current, backup, now)
        val byUid = t.collection.associateBy { it.item.uid }
        assertEquals(5L, byUid["a"]!!.item.updatedAt) // unchanged: left alone
        assertEquals(now, byUid["b"]!!.item.updatedAt) // changed back: wins over other phones
        assertEquals(now, byUid["c"]!!.item.updatedAt) // deleted since: comes back
        assertEquals(5, byUid["b"]!!.item.quantity)
        assertEquals(setOf("new"), t.deletions.map { it.uid }.toSet()) // added since: deleted everywhere
    }

    @Test
    fun nextcloudFileListing() {
        val xml = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
            <d:response><d:href>/remote.php/dav/files/jo/MTG%20Trader/Backups/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
            <d:response><d:href>/remote.php/dav/files/jo/MTG%20Trader/Backups/2026-10-05%2014.30.15%20-%20Daily%20backup.json.gz</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>1234</d:getcontentlength><d:getlastmodified>Sun, 05 Oct 2026 12:30:16 GMT</d:getlastmodified></d:prop></d:propstat></d:response>
            </d:multistatus>"""
        val files = NextcloudClient.parseFiles(xml, "/remote.php/dav/files/jo/MTG%20Trader/Backups")
        assertEquals(1, files.size)
        assertEquals("2026-10-05 14.30.15 - Daily backup.json.gz", files[0].name)
        assertEquals(1234L, files[0].size)
        assertTrue(files[0].modified > 0)
    }
}
