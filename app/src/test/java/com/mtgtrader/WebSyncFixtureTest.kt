package com.mtgtrader

import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.Side
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncDeck
import com.mtgtrader.data.SyncDeletion
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.SyncPrefs
import com.mtgtrader.data.SyncStack
import com.mtgtrader.data.SyncTrade
import com.mtgtrader.data.SyncTradeItem
import com.mtgtrader.data.Trade
import com.mtgtrader.data.TradeItem
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
 *   made wishlist and collection edits; a phone must read it and take the edits over. Since the website (2026-11).
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

    /**
     * A phone's data: a binder, a card, three wishlist entries (to buy, ordered with an alert, received), a deck, and a
     * trade not yet applied (one card given, one received at an agreed price; since the website's trades, 2026-11-13).
     */
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
        trades = listOf(
            SyncTrade(
                Trade(createdAt = 1_700_000_700_000, partner = "Bob", notes = "at the club", uid = "t0000000000000000000000000000001", updatedAt = 1_700_000_700_000),
                items = listOf(
                    SyncTradeItem(TradeItem(tradeId = 0, side = Side.GIVE, card = solRing, foil = false, quantity = 1, prices = PriceSet(trend = 1.4, avg = 1.5), addedAt = 1_700_000_700_000)),
                    SyncTradeItem(
                        TradeItem(
                            tradeId = 0, side = Side.GET, card = rhystic, foil = false, quantity = 1, prices = PriceSet(trend = 29.0, avg30 = 31.0),
                            customPrice = 26.81, customTyped = 200.0, customCurrency = "DKK", addedAt = 1_700_000_700_001,
                        ),
                    ),
                ),
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
        // Collection edits (website phase 2): a new binder with a new stack, and the Sol Ring stack changed.
        val trades = merged.binders.single { it.name == "Trades" }
        assertEquals("b0000000000000000000000000000002", trades.uid)
        val boltStack = merged.collection.single { it.item.card.name == "Lightning Bolt" && it.item.foil }
        assertEquals(trades.uid, boltStack.binder)
        assertEquals(2, boltStack.item.quantity)
        assertTrue(boltStack.item.foil && boltStack.item.signed && !boltStack.item.etched)
        assertEquals("EX", boltStack.item.condition)
        assertEquals("DE", boltStack.item.language)
        val solStack = merged.collection.single { it.item.card.name == "Sol Ring" }
        // 3 after the edit, then 1 given away in the trade with Anna below.
        assertEquals(2, solStack.item.quantity)
        assertEquals("from the website", solStack.item.notes)
        assertEquals(1.25, solStack.item.purchasePrice)
        assertEquals("b0000000000000000000000000000001", solStack.binder)
        // Trades (website, 2026-11-13): a new trade with Anna completed into "Trades", and Bob's trade changed.
        assertEquals(2, merged.trades.size)
        val anna = merged.trades.single { it.trade.partner == "Anna" }
        assertEquals("e0000000000000000000000000000001", anna.trade.uid)
        assertTrue(anna.trade.applied)
        assertEquals(1_750_000_000_000, anna.trade.appliedAt)
        assertEquals(trades.uid, anna.binder)
        val got = anna.items.single { it.item.side == Side.GET }
        assertEquals("Lightning Bolt", got.item.card.name)
        assertEquals(2, got.item.appliedDelta)
        assertEquals(1.2, got.item.prices.trend)
        val gave = anna.items.single { it.item.side == Side.GIVE }
        assertEquals(-1, gave.item.appliedDelta)
        assertEquals("b0000000000000000000000000000001", gave.appliedBinder)
        val boltIn = merged.collection.single { it.item.card.name == "Lightning Bolt" && !it.item.foil }
        assertEquals(2, boltIn.item.quantity)
        assertEquals(trades.uid, boltIn.binder)
        val bob = merged.trades.single { it.trade.uid == "t0000000000000000000000000000001" }
        assertEquals("Bob B.", bob.trade.partner)
        assertEquals(2, bob.items.single { it.item.card.name == "Rhystic Study" }.item.quantity)
        assertEquals(26.81, bob.items.single { it.item.card.name == "Rhystic Study" }.item.customPrice)
        assertTrue(bob.trade.updatedAt > phone.trades.single().trade.updatedAt)
        // Syncing again changes nothing.
        assertEquals(merged, SyncMerge.merge(merged, merged, now = 1_750_000_100_000))
    }
}
