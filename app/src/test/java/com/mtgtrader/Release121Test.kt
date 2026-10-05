package com.mtgtrader

import com.mtgtrader.data.AppSide
import com.mtgtrader.data.ArchidektCodes
import com.mtgtrader.data.ArchidektEntry
import com.mtgtrader.data.ArchidektPlanner
import com.mtgtrader.data.BackupInfo
import com.mtgtrader.data.BackupPlace
import com.mtgtrader.data.BackupRestore
import com.mtgtrader.data.BackupRetention
import com.mtgtrader.data.Backups
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardKey
import com.mtgtrader.data.CardLabel
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Choice
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.Decision
import com.mtgtrader.data.Finish
import com.mtgtrader.data.NextcloudClient
import com.mtgtrader.data.SnapEntry
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncDeletion
import com.mtgtrader.data.SyncStack
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchidektPlannerTest {
    private fun key(id: String, cond: String = "NM", finish: Finish = Finish.NONFOIL, lang: String = "EN") = CardKey(id, finish, cond, lang)
    private fun app(vararg p: Pair<CardKey, Int>) = p.associate { (k, n) -> k to AppSide(n, null, CardLabel(k.scryfallId)) }
    private var nextId = 1L
    private fun entry(k: CardKey, n: Int, id: Long = nextId++, price: Double? = null) =
        ArchidektEntry(id, 100, k.scryfallId, k.scryfallId, "tst", "1", k.finish, k.condition, k.language, n, price)
    private fun arch(vararg e: ArchidektEntry) = e.groupBy { CardKey.of(it) }
    private fun snap(vararg p: Pair<CardKey, Int>) = p.associate { (k, n) -> k to SnapEntry(k, n) }

    private val a = key("a")
    private val b = key("b")
    private val c = key("c")

    @Test
    fun firstSyncCopiesToAnEmptySide() {
        val toArch = ArchidektPlanner.plan(app(a to 2, b to 1), emptyMap(), null)
        assertFalse(toArch.waitsForUser)
        assertEquals(3, toArch.archAdded)
        assertEquals(0, toArch.appAdded)

        val toApp = ArchidektPlanner.plan(emptyMap(), arch(entry(a, 4)), null)
        assertFalse(toApp.waitsForUser)
        assertEquals(4, toApp.appAdded)
    }

    @Test
    fun firstSyncWithTheSameCardsChangesNothing() {
        val p = ArchidektPlanner.plan(app(a to 2), arch(entry(a, 1), entry(a, 1)), null)
        assertFalse(p.waitsForUser)
        assertTrue(p.changes.isEmpty())
    }

    @Test
    fun firstSyncThatDiffersWaitsForTheUser() {
        val p = ArchidektPlanner.plan(app(a to 2, b to 1), arch(entry(a, 2), entry(c, 1)), null)
        assertTrue(p.firstNeedsChoice)
        assertEquals(setOf(b, c), p.conflicts.map { it.key }.toSet())
        // "The app replaces Archidekt": c goes from Archidekt, b goes onto it.
        val decided = ArchidektPlanner.plan(
            app(a to 2, b to 1), arch(entry(a, 2), entry(c, 1)), null,
            p.conflicts.associate { it.key to Decision(Choice.APP, it.app, it.arch) },
        )
        assertFalse(decided.waitsForUser)
        assertEquals(1, decided.archAdded)
        assertEquals(1, decided.archRemoved)
        assertEquals(0, decided.appAdded + decided.appRemoved)

        // Deciding only some cards: those go ahead, the rest wait as ordinary conflicts.
        val partly = ArchidektPlanner.plan(app(a to 2, b to 1), arch(entry(a, 2), entry(c, 1)), null, mapOf(b to Decision(Choice.APP, 1, 0)))
        assertFalse(partly.waitsForUser)
        assertEquals(listOf(c), partly.conflicts.map { it.key })
        assertEquals(1, partly.archAdded)
    }

    @Test
    fun oneSidedChangesGoToTheOtherSide() {
        val s = snap(a to 2, b to 1, c to 3)
        // App: one more a, b sold. Archidekt: c down to 1.
        val p = ArchidektPlanner.plan(app(a to 3, c to 3), arch(entry(a, 2), entry(b, 1), entry(c, 1)), s)
        val byKey = p.all.associateBy { it.key }
        assertEquals(3, byKey[a]!!.archTarget)
        assertEquals(0, byKey[b]!!.archTarget)
        assertEquals(1, byKey[c]!!.appTarget)
        assertTrue(p.conflicts.isEmpty())
        assertFalse(p.needsReview)
    }

    @Test
    fun theSameChangeOnBothSidesIsAgreed() {
        val p = ArchidektPlanner.plan(app(a to 4), arch(entry(a, 4)), snap(a to 2))
        assertTrue(p.changes.isEmpty())
    }

    @Test
    fun differentChangesOnBothSidesAreAConflictUntilDecided() {
        val s = snap(a to 2)
        val p = ArchidektPlanner.plan(app(a to 3), arch(entry(a, 1, id = 9)), s)
        assertEquals(listOf(a), p.conflicts.map { it.key })
        assertFalse(p.changesApp || p.changesArchidekt)

        // Both: +1 in the app and -1 on Archidekt → 2.
        val both = ArchidektPlanner.plan(app(a to 3), arch(entry(a, 1, id = 9)), s, mapOf(a to Decision(Choice.BOTH, 3, 1)))
        assertEquals(2, both.all.single().appTarget)
        assertEquals(2, both.all.single().archTarget)

        // A decision made on other numbers doesn't count.
        val stale = ArchidektPlanner.plan(app(a to 4), arch(entry(a, 1, id = 9)), s, mapOf(a to Decision(Choice.APP, 3, 1)))
        assertEquals(1, stale.conflicts.size)
    }

    @Test
    fun bigRemovalsWaitForApproval() {
        val keys = (1..40).map { key("k$it") }
        val s = keys.associateWith { SnapEntry(it, 1) }
        // Archidekt lost 30 of 40 cards.
        val archLeft = keys.take(10).map { entry(it, 1) }.groupBy { CardKey.of(it) }
        val appAll = keys.associate { it to AppSide(1, null, CardLabel()) }
        val p = ArchidektPlanner.plan(appAll, archLeft, s)
        assertEquals(30, p.appRemoved)
        assertTrue(p.needsReview)
        val ok = ArchidektPlanner.plan(appAll, archLeft, s, approvedRemovals = 30 to 0)
        assertFalse(ok.needsReview)
        // A few removals go through on their own.
        val few = ArchidektPlanner.plan(appAll, keys.drop(3).map { entry(it, 1) }.groupBy { CardKey.of(it) }, s)
        assertFalse(few.needsReview)
    }

    @Test
    fun anEntryEditedOnArchidektIsRecognised() {
        val lp = key("a", "LP")
        val s = mapOf(a to SnapEntry(a, 2, entryIds = listOf(77)))
        val p = ArchidektPlanner.plan(app(a to 2), arch(entry(lp, 2, id = 77)), s)
        val arrived = p.all.single { it.key == lp }
        assertEquals(a, arrived.movedFrom)
        assertEquals(2, arrived.appDelta)
        assertEquals(-2, p.all.single { it.key == a }.appDelta)
    }

    @Test
    fun purchasePricesFollowTheSideThatChanged() {
        val s = mapOf(a to SnapEntry(a, 1, price = 2.0))
        val appSide = mapOf(a to AppSide(1, 2.0, CardLabel()))
        val fromArch = ArchidektPlanner.plan(appSide, arch(entry(a, 1, price = 3.5)), s).all.single()
        assertTrue(fromArch.setAppPrice)
        assertEquals(3.5, fromArch.price!!, 0.001)

        val fromApp = ArchidektPlanner.plan(mapOf(a to AppSide(1, 4.0, CardLabel())), arch(entry(a, 1, price = 2.0)), s).all.single()
        assertTrue(fromApp.setArchPrice)
        assertEquals(4.0, fromApp.price!!, 0.001)
    }

    @Test
    fun theAgreementKeepsConflictsAndFailuresAsTheyWere() {
        val s = snap(a to 2, b to 1)
        val archNow = arch(entry(a, 1), entry(b, 1))
        val p = ArchidektPlanner.plan(app(a to 3, b to 2), archNow, s)
        // a: conflict (app 3, Archidekt 1, was 2). b: pushed, but say it failed.
        val snapAfter = ArchidektPlanner.newSnapshot(p, archNow, failed = setOf(b), old = s, appPrices = emptyMap())
        assertEquals(2, snapAfter.single { it.key == a }.quantity)
        assertEquals(1, snapAfter.single { it.key == b }.quantity)
    }
}

class ArchidektCodesTest {
    @Test
    fun conditionsMapBothWays() {
        assertEquals("NM", ArchidektCodes.archCondition("MT"))
        assertEquals("LP", ArchidektCodes.archCondition("EX"))
        assertEquals("MP", ArchidektCodes.archCondition("LP"))
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
