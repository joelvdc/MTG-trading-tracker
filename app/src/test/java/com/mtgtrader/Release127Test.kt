package com.mtgtrader

import com.mtgtrader.data.AppSide
import com.mtgtrader.data.ArchidektEntry
import com.mtgtrader.data.ArchidektPlanner
import com.mtgtrader.data.ArchidektSync
import com.mtgtrader.data.ArchidektTag
import com.mtgtrader.data.BackupFile
import com.mtgtrader.data.CardKey
import com.mtgtrader.data.CardKind
import com.mtgtrader.data.CardLabel
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionFilter
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.Finish
import com.mtgtrader.data.MarkFilter
import com.mtgtrader.data.MarkTags
import com.mtgtrader.data.Marks
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.SnapEntry
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncException
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncStack
import com.mtgtrader.data.TradeBinderPlanner
import com.mtgtrader.data.TradeBinderRules
import com.mtgtrader.data.csvFlag
import com.mtgtrader.ui.marksText
import com.mtgtrader.ui.splitNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** 1.27: signed and altered copies. */
class Release127Test {
    private val ref = CardRef("bs", "Brainstorm", "ice", "Ice Age", "61", "common", null, 1, 1.0, null, true, false)

    // ---- Archidekt -------------------------------------------------------------------------

    private val tags = listOf(ArchidektTag(7, "Signed"), ArchidektTag(8, "altered "), ArchidektTag(9, "Trade binder"))
    private fun entry(id: Long, n: Int, vararg tagIds: Long) =
        ArchidektEntry(id, 100, "bs", "Brainstorm", "ICE", "61", Finish.NONFOIL, "NM", "EN", n, tags = tagIds.toList())

    @Test
    fun archidektLabelsMarkTheEntries() {
        val marks = MarkTags.of(tags)
        assertEquals(CardKey("bs", Finish.NONFOIL, "NM", "EN"), CardKey.of(entry(1, 1, 9), marks))
        assertEquals(CardKey("bs", Finish.NONFOIL, "NM", "EN", signed = true), CardKey.of(entry(1, 1, 7, 9), marks))
        assertEquals(CardKey("bs", Finish.NONFOIL, "NM", "EN", signed = true, altered = true), CardKey.of(entry(1, 1, 8, 7), marks))
        assertEquals(listOf(7L, 8L), marks.idsFor(CardKey("bs", Finish.NONFOIL, "NM", "EN", signed = true, altered = true)))
        assertEquals(emptyList<Long>(), marks.idsFor(CardKey("bs", Finish.NONFOIL, "NM", "EN")))
        // Plain keys read and print as before, so earlier agreements still match.
        assertEquals("bs|NONFOIL|NM|EN", CardKey("bs", Finish.NONFOIL, "NM", "EN").toString())
        assertEquals("bs|NONFOIL|NM|EN|signed", CardKey("bs", Finish.NONFOIL, "NM", "EN", signed = true).toString())
        val item = CollectionItem(card = ref, foil = false, quantity = 1, signed = true)
        assertEquals(CardKey("bs", Finish.NONFOIL, "NM", "EN", signed = true), CardKey.of(item))
        assertEquals("Brainstorm (ICE 61), NM, EN, signed", ArchidektSync.describe(CardLabel("Brainstorm", "ICE", "61"), CardKey.of(item)))
    }

    @Test
    fun oneOfFourMarkedSignedBecomesTwoArchidektEntries() {
        val plain = CardKey("bs", Finish.NONFOIL, "NM", "EN")
        val signed = plain.copy(signed = true)
        val app = mapOf(plain to AppSide(3, null, CardLabel("Brainstorm")), signed to AppSide(1, null, CardLabel("Brainstorm")))
        val arch = listOf(entry(1, 4)).groupBy { CardKey.of(it, MarkTags.of(tags)) }
        val p = ArchidektPlanner.plan(app, arch, mapOf(plain to SnapEntry(plain, 4, entryIds = listOf(1))))
        assertFalse(p.waitsForUser)
        val byKey = p.all.associateBy { it.key }
        assertEquals(2, byKey.size)
        assertEquals(3, byKey.getValue(plain).archTarget)
        assertEquals(1, byKey.getValue(signed).archTarget)
        assertEquals(0, p.appAdded + p.appRemoved)
    }

    @Test
    fun aSignedLabelAddedOnArchidektMarksTheCopiesInTheApp() {
        val plain = CardKey("bs", Finish.NONFOIL, "NM", "EN")
        val signed = plain.copy(signed = true)
        val arch = listOf(entry(1, 1, 7)).groupBy { CardKey.of(it, MarkTags.of(tags)) }
        val p = ArchidektPlanner.plan(mapOf(plain to AppSide(1, null, CardLabel("Brainstorm"))), arch, mapOf(plain to SnapEntry(plain, 1, entryIds = listOf(1))))
        val byKey = p.all.associateBy { it.key }
        assertEquals(0, byKey.getValue(plain).appTarget)
        assertEquals(1, byKey.getValue(signed).appTarget)
        // The same entry, edited: the copy keeps its binder in the app.
        assertEquals(plain, byKey.getValue(signed).movedFrom)
    }

    // ---- Nextcloud -------------------------------------------------------------------------

    private fun gz(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }

    @Test
    fun syncFilesCarryTheMarksAndOldOnesReadAsPlain() {
        val data = SyncData(collection = listOf(SyncStack(CollectionItem(card = ref, foil = false, quantity = 1, signed = true, uid = "u1"))))
        val back = SyncFile.decode(SyncFile.encode(SyncFile(data = data)))
        assertEquals(2, back.format)
        assertTrue(back.data.collection.single().item.signed)
        assertFalse(back.data.collection.single().item.altered)

        // A file written by 1.26 (format 1, no signed/altered fields).
        val old = SyncFile.encode(SyncFile(data = data)).let { SyncFile.decode(it) }.copy(format = 1)
        val oldJson = String(java.util.zip.GZIPInputStream(SyncFile.encode(old).inputStream()).readBytes())
            .replace(",\"signed\":true", "").replace(",\"altered\":false", "")
        assertFalse("signed" in oldJson)
        val read = SyncFile.decode(gz(oldJson))
        assertEquals(1, read.format)
        assertFalse(read.data.collection.single().item.signed)
    }

    @Test
    fun newerFilesAreRefused() {
        try {
            SyncFile.decode(gz("""{"format":3,"app":"MTG Trader"}"""))
            fail()
        } catch (e: SyncException) {
            assertTrue("newer version" in e.message!!)
        }
        try {
            BackupFile.decode(gz("""{"format":3,"app":"MTG Trader"}"""))
            fail()
        } catch (e: SyncException) {
            assertTrue("newer version" in e.message!!)
        }
        assertEquals(2, BackupFile.FORMAT)
    }

    @Test
    fun noticesAnotherPhoneStillOnAnOlderVersion() {
        val old = SyncFile(format = 1, writtenBy = "Google Pixel 7")
        // Another phone wrote it since this one's last sync.
        assertEquals("Google Pixel 7", SyncFile.olderWriter(old, "e2", "e1", "Samsung SM-S911B"))
        // This phone wrote it itself before it was updated.
        assertNull(SyncFile.olderWriter(old.copy(writtenBy = "Samsung SM-S911B"), "e2", "e1", "Samsung SM-S911B"))
        // The file this phone already knows.
        assertNull(SyncFile.olderWriter(old, "e1", "e1", "Samsung SM-S911B"))
        // Written by an updated phone.
        assertNull(SyncFile.olderWriter(old.copy(format = 2), "e2", "e1", "Samsung SM-S911B"))
        assertEquals("Another phone", SyncFile.olderWriter(old.copy(writtenBy = ""), "e2", null, "Samsung SM-S911B"))
    }

    // ---- Filter, CSV, trade binder, texts ----------------------------------------------------

    private fun row(id: Long, qty: Int, signed: Boolean = false, altered: Boolean = false, binder: Long = 0) =
        CollectionRow(CollectionItem(id = id, card = ref, foil = false, quantity = qty, binderId = binder, signed = signed, altered = altered), null)

    @Test
    fun filterBySignedOrAltered() {
        val rows = listOf(row(1, 1), row(2, 1, signed = true), row(3, 1, altered = true), row(4, 1, signed = true, altered = true))
        fun ids(vararg m: MarkFilter) = rows.filter { CollectionFilter(marks = m.toSet()).matches(it, PriceType.TREND, emptySet()) }.map { it.item.id }
        assertEquals(listOf(2L, 4L), ids(MarkFilter.SIGNED))
        assertEquals(listOf(3L, 4L), ids(MarkFilter.ALTERED))
        assertEquals(listOf(1L), ids(MarkFilter.PLAIN))
        assertEquals(listOf(1L, 2L, 4L), ids(MarkFilter.PLAIN, MarkFilter.SIGNED))
        assertEquals(1, CollectionFilter(marks = setOf(MarkFilter.SIGNED)).count)
        // Saved filters from before 1.27 still read.
        assertEquals(setOf("rare"), CollectionFilter.decode("""{"rarities":["rare"]}""").rarities)
    }

    @Test
    fun csvFlags() {
        listOf("true", "TRUE", "yes", "1", "x", "Signed").forEach { assertTrue(it, csvFlag(it)) }
        listOf(null, "", "false", "no", "0").forEach { assertFalse(it.toString(), csvFlag(it)) }
    }

    @Test
    fun signedCopiesAreNeverOfferedForTrade() {
        // Five Brainstorms worth €5, one of them signed: the four plain ones minus the one kept can go.
        val pricey = ref.copy(fallbackEur = 5.0)
        val rows = listOf(
            CollectionRow(CollectionItem(id = 1, card = pricey, foil = false, quantity = 4), null),
            CollectionRow(CollectionItem(id = 2, card = pricey, foil = false, quantity = 1, signed = true), null),
        )
        val changes = TradeBinderPlanner.plan(rows, emptyMap(), emptySet(), 9L, TradeBinderRules(minValue = 1.0), PriceType.TREND, emptySet())
        assertEquals(1, changes.size)
        assertEquals(CardKind("bs", false, false, "NM", "EN"), changes.single().kind)
        assertEquals(4, changes.single().copies)
        // Skipped suggestions saved before 1.27 keep their keys.
        assertEquals("bs|false|false|NM|EN", CardKind.of(rows[0].item).key)
        assertEquals("bs|false|false|NM|EN|signed", CardKind.of(rows[1].item).key)

        // A signed copy already in the trade binder (put there by hand) stays.
        val inBinder = rows.map { if (it.item.signed) it.copy(item = it.item.copy(binderId = 9L)) else it }
        assertTrue(TradeBinderPlanner.plan(inBinder, emptyMap(), emptySet(), 9L, TradeBinderRules(), PriceType.TREND, emptySet()).none { it.kind.signed })
    }

    @Test
    fun wording() {
        assertEquals("signed and altered", Marks(signed = true, altered = true).label)
        assertEquals("not signed or altered", marksText(Marks.NONE))
        assertEquals("1 of 4 copies will be a separate signed stack; 3 stay not signed or altered.", splitNote(1, 4, Marks(signed = true), Marks.NONE))
        assertEquals("2 of 3 copies will be a separate plain stack; 1 stays altered.", splitNote(2, 3, Marks.NONE, Marks(altered = true)))
    }
}
