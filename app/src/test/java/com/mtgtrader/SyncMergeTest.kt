package com.mtgtrader

import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckMerge
import com.mtgtrader.data.FirstSync
import com.mtgtrader.data.ScannedCard
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private val now = 1_800_000_000_000L

    private fun card(id: String, name: String = id) = CardRef(id, name, "abc", "Set", "1", "rare", null, null, null, null, true, true)
    private fun stack(uid: String, card: String, qty: Int, at: Long, binder: String? = null) =
        SyncStack(CollectionItem(card = card(card), foil = false, quantity = qty, addedAt = 1, uid = uid, updatedAt = at), binder)
    private fun binder(uid: String, name: String, at: Long, created: Long = 1) = Binder(name = name, createdAt = created, uid = uid, updatedAt = at)
    private fun deck(id: Long, name: String, at: Long, cards: Int = 1) =
        SyncDeck(Deck(id, name, "me", "Cmdr", null, null, "G", 100, importedAt = 1, updatedAt = at), List(cards) { DeckCard(deckId = id, card = card("c$it"), quantity = 1, category = "Ramp", types = "Creature", cmc = 1.0, commander = false, foil = false) })

    private fun merge(local: SyncData, remote: SyncData?, first: FirstSync? = null) = SyncMerge.merge(local.sorted(), remote?.sorted(), now, first)

    /** A copy of the Vohar deck as one phone has it. */
    private fun vohar(
        archidektAt: Long, importedAt: Long, updatedAt: Long, land: String,
        scoredAt: Long? = null, power: Double? = null, svAt: Long? = null, sv: Double? = null,
    ) = SyncDeck(
        Deck(
            10710772, "Vohar Danse", "joelvdc", "Vohar", null, null, "UB", 100, importedAt = importedAt,
            archidektUpdatedAt = archidektAt, updatedAt = updatedAt, scoredAt = scoredAt, powerLevel = power, svAt = svAt, svPowerLevel = sv,
        ),
        listOf(DeckCard(deckId = 10710772, card = card(land), quantity = 1, category = "Land", types = "Land", cmc = 0.0, commander = false, foil = false)),
    )

    @Test
    fun newerArchidektListWinsOverLaterScoresOnTheOldList() {
        // Phone A reloaded the deck after River of Tears went to the maybeboard and Polluted Delta came in.
        val phoneA = vohar(archidektAt = 2_000, importedAt = 10_000, updatedAt = 10_005, land = "Polluted Delta", scoredAt = 10_004, power = 4.4)
        // Phone B still had the old list, but rated it on ScrollVault later.
        val phoneB = vohar(archidektAt = 1_000, importedAt = 5_000, updatedAt = 20_000, land = "River of Tears", scoredAt = 5_001, power = 4.3, svAt = 20_000, sv = 6.2)
        for ((a, b) in listOf(phoneA to phoneB, phoneB to phoneA)) {
            val m = DeckMerge.merge(a, b)
            assertEquals(listOf("Polluted Delta"), m.cards.map { it.card.name })
            assertEquals(2_000L, m.deck.archidektUpdatedAt)
            // Commander Salt's score from before phone A's reload belongs to the old list: phone A's stays.
            assertEquals(4.4, m.deck.powerLevel!!, 1e-9)
            // ScrollVault rated it after phone A's reload (it reads Archidekt itself): kept.
            assertEquals(6.2, m.deck.svPowerLevel!!, 1e-9)
            assertEquals(20_000L, m.deck.updatedAt)
        }
    }

    @Test
    fun deckMergeSettles() {
        val a = vohar(archidektAt = 2_000, importedAt = 10_000, updatedAt = 10_005, land = "Polluted Delta", scoredAt = 10_004, power = 4.4)
        val b = vohar(archidektAt = 1_000, importedAt = 5_000, updatedAt = 20_000, land = "River of Tears", svAt = 20_000, sv = 6.2)
        val once = DeckMerge.merge(a, b)
        assertEquals(once, DeckMerge.merge(once, once))
        assertEquals(once, DeckMerge.merge(b, once))
        assertEquals(once, DeckMerge.merge(once, a))
        // Through the whole sync too: the other phone ends up with the new list.
        val synced = merge(SyncData(decks = listOf(b)), SyncData(decks = listOf(a)))
        assertEquals(listOf("Polluted Delta"), synced.decks.single().cards.map { it.card.name })
    }

    @Test
    fun sameArchidektVersionTakesTheLaterChange() {
        // Both phones have the same Archidekt list; one added a scanned card to the deck afterwards.
        val plain = vohar(archidektAt = 2_000, importedAt = 10_000, updatedAt = 10_000, land = "Polluted Delta")
        val added = plain.copy(deck = plain.deck.copy(updatedAt = 12_000), cards = plain.cards + plain.cards[0].copy(card = card("Island"), addedInApp = true))
        assertEquals(2, DeckMerge.merge(plain, added).cards.size)
        assertEquals(2, DeckMerge.merge(added, plain).cards.size)
    }

    @Test
    fun newerEditWinsPerItem() {
        val local = SyncData(collection = listOf(stack("a", "sol", 2, at = 200), stack("b", "bolt", 1, at = 100)))
        val remote = SyncData(collection = listOf(stack("a", "sol", 5, at = 150), stack("b", "bolt", 4, at = 300)))
        val m = merge(local, remote)
        assertEquals(2, m.collection.first { it.item.uid == "a" }.item.quantity)
        assertEquals(4, m.collection.first { it.item.uid == "b" }.item.quantity)
    }

    @Test
    fun itemsOnOneSideAreKept() {
        val m = merge(SyncData(collection = listOf(stack("a", "sol", 1, 100))), SyncData(collection = listOf(stack("b", "bolt", 1, 100))))
        assertEquals(listOf("a", "b"), m.collection.map { it.item.uid })
    }

    @Test
    fun deletionWinsOverOlderEditButNotNewer() {
        val t = now - 1000
        val local = SyncData(collection = listOf(stack("a", "sol", 1, at = t + 100), stack("b", "bolt", 1, at = t + 500)))
        val remote = SyncData(deletions = listOf(SyncDeletion("a", t + 200), SyncDeletion("b", t + 300)))
        val m = merge(local, remote)
        assertEquals(listOf("b"), m.collection.map { it.item.uid })
        // "b" was edited after it was deleted elsewhere: it comes back and the deletion is forgotten.
        assertEquals(listOf("a"), m.deletions.map { it.uid })
    }

    @Test
    fun oldDeletionsAreForgotten() {
        val m = merge(SyncData(deletions = listOf(SyncDeletion("old", now - SyncMerge.KEEP_DELETIONS_MS - 1), SyncDeletion("new", now - 1000))), null)
        assertEquals(listOf("new"), m.deletions.map { it.uid })
    }

    @Test
    fun bindersWithTheSameNameAreFoldedTogether() {
        val local = SyncData(
            binders = listOf(binder("b1", "Main", at = 10, created = 5)),
            collection = listOf(stack("x", "sol", 1, at = 10, binder = "b1")),
        )
        val remote = SyncData(
            binders = listOf(binder("b2", "main ", at = 10, created = 3)),
            collection = listOf(stack("y", "bolt", 1, at = 10, binder = "b2")),
        )
        val m = merge(local, remote)
        assertEquals(listOf("b2"), m.binders.map { it.uid })
        assertEquals(listOf("b2", "b2"), m.collection.map { it.binder })
        assertTrue(m.deletions.any { it.uid == "b1" })
    }

    @Test
    fun sameCardInSameBinderIsKeptOnce() {
        val local = SyncData(collection = listOf(stack("a", "sol", 2, at = 100)))
        val remote = SyncData(collection = listOf(stack("b", "sol", 3, at = 200)))
        val m = merge(local, remote, FirstSync.MERGE)
        assertEquals(listOf("b"), m.collection.map { it.item.uid })
        assertTrue(m.deletions.any { it.uid == "a" })
    }

    @Test
    fun cardsInADeletedBinderGoToUnsorted() {
        val local = SyncData(collection = listOf(stack("a", "sol", 1, at = 300, binder = "gone")))
        val remote = SyncData(deletions = listOf(SyncDeletion("gone", 200)))
        assertNull(merge(local, remote).collection.single().binder)
    }

    @Test
    fun decksAreKeyedByArchidektIdAndReplacedWhole() {
        val local = SyncData(decks = listOf(deck(7, "Old name", at = 100, cards = 2)))
        val remote = SyncData(decks = listOf(deck(7, "New name", at = 200, cards = 3)))
        val m = merge(local, remote)
        assertEquals("New name", m.decks.single().deck.name)
        assertEquals(3, m.decks.single().cards.size)
        val deleted = merge(local, SyncData(deletions = listOf(SyncDeletion(SyncMerge.deckKey(7), 150))))
        assertTrue(deleted.decks.isEmpty())
    }

    @Test
    fun tradesKeepTheirBinderReferences() {
        val t = SyncTrade(
            Trade(partner = "Sam", uid = "t1", updatedAt = 100), binder = "b1",
            items = listOf(SyncTradeItem(TradeItem(tradeId = 0, side = "GIVE", card = card("sol"), foil = false), appliedBinder = "b1")),
        )
        val m = merge(SyncData(binders = listOf(binder("b1", "Trades", 50)), trades = listOf(t)), null)
        assertEquals("b1", m.trades.single().binder)
        assertEquals("b1", m.trades.single().items.single().appliedBinder)
    }

    @Test
    fun startFromThisPhoneDeletesWhatOnlyNextcloudHad() {
        val local = SyncData(collection = listOf(stack("a", "sol", 1, 100)))
        val remote = SyncData(collection = listOf(stack("b", "bolt", 1, 900)), scans = listOf(ScannedCard(card = card("x"), foil = false, uid = "s", updatedAt = 1)))
        val m = merge(local, remote, FirstSync.USE_LOCAL)
        assertEquals(listOf("a"), m.collection.map { it.item.uid })
        assertEquals(setOf("b", "s"), m.deletions.map { it.uid }.toSet())
        // The other phone, still holding "b", drops it on its next sync.
        val other = merge(SyncData(collection = listOf(stack("b", "bolt", 1, 900))), m)
        assertEquals(listOf("a"), other.collection.map { it.item.uid })
    }

    @Test
    fun useNextcloudCopyIgnoresThisPhone() {
        val m = merge(SyncData(collection = listOf(stack("a", "sol", 1, 999))), SyncData(collection = listOf(stack("b", "bolt", 1, 1))), FirstSync.USE_REMOTE)
        assertEquals(listOf("b"), m.collection.map { it.item.uid })
    }

    @Test
    fun newerPreferencesWin() {
        val m = merge(SyncData(prefs = SyncPrefs(100, mapOf("tolerancePct" to "5"))), SyncData(prefs = SyncPrefs(200, mapOf("tolerancePct" to "10"))))
        assertEquals("10", m.prefs.values["tolerancePct"])
    }

    @Test
    fun mergingTwiceChangesNothing() {
        val local = SyncData(
            binders = listOf(binder("b1", "Main", 10)),
            collection = listOf(stack("a", "sol", 2, 200, "b1"), stack("c", "bolt", 1, 50)),
            decks = listOf(deck(1, "D", 100)),
            deletions = listOf(SyncDeletion("zz", now - 5)),
        )
        val remote = SyncData(collection = listOf(stack("a", "sol", 5, 150, "b1"), stack("d", "ring", 1, 70)), deletions = listOf(SyncDeletion("c", 60)))
        val once = merge(local, remote)
        assertEquals(once, merge(once, once))
        // And the other phone ends up with the same.
        assertEquals(once, merge(remote, once))
    }

    @Test
    fun fileRoundTrips() {
        val data = merge(SyncData(binders = listOf(binder("b1", "Main", 10)), collection = listOf(stack("a", "sol", 2, 200, "b1")), decks = listOf(deck(3, "D", 5, cards = 2))), null)
        val back = SyncFile.decode(SyncFile.encode(SyncFile(writtenAt = 1, data = data)))
        assertEquals(data, back.data)
    }
}
