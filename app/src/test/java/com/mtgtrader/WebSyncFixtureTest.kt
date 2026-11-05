package com.mtgtrader

import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncDeck
import com.mtgtrader.data.SyncDeletion
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.SyncPrefs
import com.mtgtrader.data.SyncStack
import com.mtgtrader.data.WishlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Keeps the website (`web/`) and the app reading and writing the same Nextcloud sync file.
 *
 * - [appFileMatchesTheWebsitesCopy]: the website's tests read `web/test/fixtures/app-sync.json`, a file as this
 *   app writes it. When the app's sync format changes, this test fails until the copy is rewritten (run the tests
 *   with WRITE_WEB_FIXTURES=1) and the website is updated to match.
 * - [websiteEditsMergeOnThePhone]: `web/test/fixtures/web-edited-sync.json` is that file after the website's tests
 *   made wishlist edits; a phone must read it and take the edits over. Since the website (2026-11).
 */
class WebSyncFixtureTest {
    private val fixtures = File("../web/test/fixtures")

    private fun card(id: String, name: String, set: String, number: String, cm: Int?, eur: Double?, eurFoil: Double?) = CardRef(
        scryfallId = id, name = name, setCode = set, setName = "Set $set", collectorNumber = number, rarity = "rare",
        imageUrl = "https://cards.scryfall.io/normal/front/$id.jpg", cardmarketId = cm, fallbackEur = eur,
        fallbackEurFoil = eurFoil, hasNonFoil = true, hasFoil = eurFoil != null,
    )

    private val solRing = card("11111111-1111-1111-1111-111111111111", "Sol Ring", "c21", "263", 556001, 1.5, null)
    private val rhystic = card("22222222-2222-2222-2222-222222222222", "Rhystic Study", "jmp", "175", 489000, 30.0, 45.0)
    private val rift = card("33333333-3333-3333-3333-333333333333", "Cyclonic Rift", "mm3", "35", 300000, 20.0, 60.0)

    /** A phone's data: a binder, a card, three wishlist entries (to buy, ordered with an alert, received), a deck. */
    private fun appData() = SyncData(
        binders = listOf(Binder(name = "Main", createdAt = 1_700_000_000_000, uid = "b0000000000000000000000000000001", updatedAt = 1_700_000_000_000)),
        collection = listOf(
            SyncStack(
                CollectionItem(card = solRing, foil = false, quantity = 1, addedAt = 1_700_000_000_000, uid = "c0000000000000000000000000000001", updatedAt = 1_700_000_000_000),
                "b0000000000000000000000000000001",
            ),
        ),
        decks = listOf(
            SyncDeck(
                Deck(10710772, "Vohar Danse", "joelvdc", "Vohar", null, null, "UB", 100, importedAt = 1_700_000_000_000, updatedAt = 1_700_000_000_000),
                listOf(DeckCard(deckId = 10710772, card = solRing, quantity = 1, category = "Ramp", types = "Artifact", cmc = 1.0, commander = false, foil = false, lineUid = "d0000000-0000-0000-0000-000000000001")),
            ),
        ),
        wishlist = listOf(
            WishlistItem(card = solRing, quantity = 2, anyPrinting = true, addedAt = 1_700_000_100_000, ownedAtAdd = 1, uid = "a0000000000000000000000000000001", updatedAt = 1_700_000_100_000),
            WishlistItem(
                card = rhystic, foil = true, quantity = 1, anyPrinting = false, notes = "for Vohar", addedAt = 1_700_000_200_000,
                orderedAt = 1_700_000_300_000, alertBelow = 300.0, alertCurrency = "DKK", uid = "a0000000000000000000000000000002", updatedAt = 1_700_000_300_000,
            ),
            WishlistItem(
                card = rift, quantity = 1, addedAt = 1_700_000_400_000, receivedAt = 1_700_000_500_000, receivedFrom = "added to the collection",
                uid = "a0000000000000000000000000000003", updatedAt = 1_700_000_500_000,
            ),
        ),
        deletions = listOf(SyncDeletion("a0000000000000000000000000000009", 1_700_000_000_000)),
        prefs = SyncPrefs(1_700_000_000_000, mapOf("priceType" to "trend", "priceSource" to "cardmarket", "currency" to "DKK")),
    ).sorted()

    private fun json(file: SyncFile): String =
        GZIPInputStream(ByteArrayInputStream(SyncFile.encode(file))).use { it.readBytes() }.toString(Charsets.UTF_8)

    private fun gzip(text: String): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) } }.toByteArray()

    @Test
    fun appFileMatchesTheWebsitesCopy() {
        val text = json(SyncFile(writtenAt = 1_700_000_600_000, writtenBy = "Test phone", data = appData())) + "\n"
        val copy = File(fixtures, "app-sync.json")
        if (System.getenv("WRITE_WEB_FIXTURES") == "1") copy.writeText(text)
        assertEquals("web/test/fixtures/app-sync.json is out of date: run the tests with WRITE_WEB_FIXTURES=1 and update the website", copy.readText(), text)
    }

    @Test
    fun websiteEditsMergeOnThePhone() {
        val edited = SyncFile.decode(gzip(File(fixtures, "web-edited-sync.json").readText()))
        assertEquals("MTG Trader website", edited.writtenBy)
        val phone = appData()
        val merged = SyncMerge.merge(phone, edited.data, now = 1_750_000_100_000)
        val byName = merged.wishlist.associateBy { it.card.name }

        // Added on the website.
        val bolt = byName.getValue("Lightning Bolt")
        assertTrue(bolt.uid!!.matches(Regex("[0-9a-f]{32}")))
        assertEquals(4, bolt.quantity)
        assertEquals(0L, bolt.id)
        // Edited on the website: more copies, ordered, an alert level.
        val sol = byName.getValue("Sol Ring")
        assertEquals(3, sol.quantity)
        assertTrue(sol.orderedAt != null)
        assertEquals(2.5, sol.alertBelow)
        assertEquals("EUR", sol.alertCurrency)
        // Removed on the website.
        assertNull(byName["Rhystic Study"])
        assertTrue(merged.deletions.any { it.uid == "a0000000000000000000000000000002" })
        // Untouched.
        assertEquals(phone.wishlist.first { it.card.name == "Cyclonic Rift" }, byName.getValue("Cyclonic Rift"))
        assertEquals(phone.decks, merged.decks)
        assertEquals(phone.collection, merged.collection)
        // Syncing again changes nothing.
        assertEquals(merged, SyncMerge.merge(merged, merged, now = 1_750_000_100_000))
    }
}
