package com.mtgtrader

import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.Binder
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CardKind
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionSorter
import com.mtgtrader.data.DECKS_KEY
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckCopies
import com.mtgtrader.data.DisplayCurrency
import com.mtgtrader.data.Finish
import com.mtgtrader.data.Money
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceSource
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.Pricing
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SortMove
import com.mtgtrader.data.Together
import com.mtgtrader.data.UNSORTED_KEY
import com.mtgtrader.data.idx
import com.mtgtrader.data.levelKey
import com.mtgtrader.data.orderOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 1.107: the sort's levels, the Decks binder and the planner's glue (set up, suggest, apply, the old skips). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release1107SortTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var repo: MtgRepository
    private lateinit var settings: Settings
    private lateinit var sorter: CollectionSorter

    @Before
    fun setUp() {
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        // The app's own parts: its database (on the phone, Robolectric's own file), the repository and the sorter.
        val container = AppContainer(context)
        db = container.db
        repo = container.repo
        settings = container.settings
        sorter = container.sorter
        Money.display = DisplayCurrency()
        Pricing.source = PriceSource.CARDMARKET
        Pricing.conditionPct = null
    }

    @After
    fun tearDown() {
        db.close()
        Money.display = DisplayCurrency()
    }

    private val solId = "sol-ring"
    private val signetId = "arcane-signet"

    /**
     * Two levels and a Decks binder, with three cards: two printings of Sol Ring (Euros 10 and 40) and an Arcane
     * Signet (Euros 2), each in Unsorted, one copy.
     */
    private suspend fun setUpTwoLevelsAndDecks(): Pair<Long, Long> {
        sorter.setUp(
            levels = listOf(
                Triple(BinderChoice(newName = "Collection binder"), 200, 0.5),
                Triple(BinderChoice(newName = "Not bulk"), 0, null),
            ),
            decks = BinderChoice(newName = "Decks"),
            mix = 50, weekly = 10, together = Together.NAME, outlierRatio = 3.0, outlierGap = 5.0, deckCopies = DeckCopies.BEST,
        )
        addCard(solId, "Sol Ring", "cmm", "400", 10.0, binder = Binder.UNSORTED)
        addCard(solId, "Sol Ring", "lea", "232", 40.0, binder = Binder.UNSORTED)
        addCard(signetId, "Arcane Signet", "cmm", "367", 2.0, binder = Binder.UNSORTED)
        val levels = sorter.levels()
        assertEquals(2, levels.size)
        assertEquals(listOf("Collection binder", "Not bulk"), levels.map { it.second.name })
        return settings.sortLevels.first().binder.toLong() to settings.sortDecksId
    }

    private suspend fun addCard(sid: String, name: String, set: String, number: String, price: Double, binder: Long) {
        val card = CardRef(sid, name, set, "A set", number, "rare", null, null, price, price, true, true)
        repo.addToCollection(card, Finish.NONFOIL, "NM", "EN", 1, binder)
        // Cardmarket's price of that printing: the one the collection shows.
        db.priceDao().insertAll(listOf(PriceEntity(cardmarketIdOf(card), price, price, price, null, null, null, null, null, null, null, null, null)))
    }

    /** A distinct Cardmarket product per printing. */
    private fun cardmarketIdOf(card: CardRef) = (card.setCode + card.collectorNumber).hashCode() and 0x7fffffff

    private suspend fun binders(): Map<String, Long> = db.binderDao().all().associate { it.name to it.id }

    /** A deck of the owner's, with [cards] (name, quantity). */
    private suspend fun deckOfYourOwn(vararg cards: Pair<String, Int>) {
        db.deckDao().upsert(Deck(1, "Mine", "joelvdc", "A", null, null, "U", 100))
        db.deckDao().insertCards(
            cards.map { (name, qty) ->
                DeckCard(
                    deckId = 1, card = CardRef("deck-$name", name, "wwk", "A set", "1", "mythic", null, 1, 4.0, null, true, true),
                    quantity = qty, category = "Ramp", types = "Artifact", cmc = 2.0, commander = false, foil = false,
                )
            },
        )
        settings.setOwnArchidektUser("joelvdc")
    }

    private suspend fun items(): List<CollectionItem> = db.collectionDao().all()

    /** What the collection shows a copy of [item] as: its Cardmarket price at the chosen price type. */
    private suspend fun priceOf(item: CollectionItem): Double? =
        db.priceDao().get(cardmarketIdOf(item.card))?.toSet(item.foil)?.best(PriceType.TREND)

    // ---- The planner's glue -----------------------------------------------------------------------

    @Test
    fun aDeckCardGoesToTheDecksBinder() = runBlocking {
        val (l1, decksId) = setUpTwoLevelsAndDecks()
        deckOfYourOwn("Sol Ring" to 2)
        val plan = sorter.suggest()

        // The deck uses two Sol Rings: both printings go to the Decks binder, the dearest first.
        val toDecks = plan.moves.filter { it.to == DECKS_KEY }
        assertEquals(2, plan.after[DECKS_KEY])
        assertEquals(listOf("l1", "l2", DECKS_KEY, UNSORTED_KEY), plan.after.keys.toList())
        assertEquals(3, plan.after.values.sum())
        assertEquals(0, plan.deckShort)
        // One move per card kind: the two printings together, its first copy the dearest.
        assertEquals(1, toDecks.size)
        assertEquals("Sol Ring", toDecks.single().name)
        assertEquals(UNSORTED_KEY, toDecks.single().from)
        assertEquals(2, toDecks.single().copies)
        assertEquals(40.0, toDecks.single().price!!, 1e-9)

        sorter.apply(plan.moves, emptyList())
        val byBinder = items().groupBy { it.binderId }
        // The two stacks are one card kind, so one move takes both (the dearest copy first), printing by printing.
        val inDecks = items().filter { it.binderId == decksId }
        assertEquals(2, inDecks.sumOf { it.quantity })
        assertTrue(inDecks.all { it.card.name == "Sol Ring" })
        assertEquals(1, byBinder.getValue(l1).sumOf { it.quantity })
        assertEquals("Arcane Signet", byBinder.getValue(l1).single().card.name)
        assertEquals(3, items().sumOf { it.quantity })
        // The sort is settled now, and its moves are in the log; out of Unsorted and into Decks, they don't count.
        assertTrue(settings.sortSettled)
        // One line per move: Sol Ring (two copies) and the Arcane Signet, all exempt.
        assertEquals(2, settings.sortLog.size)
        assertTrue(settings.sortLog.all { it.endsWith("|1") })
        // Nothing moves the second time round: the deck's copies rest and the levels fit.
        assertTrue(sorter.suggest().moves.isEmpty())
    }

    @Test
    fun movesOutOfUnsortedIntoALevelAreExemptButOutOfALevelAreNot() = runBlocking {
        val (l1, _) = setUpTwoLevelsAndDecks()
        val key = CardKind.of(items().single { it.card.name == "Arcane Signet" }).key
        // A place that isn't set up: the move doesn't happen, and nothing counts.
        sorter.apply(listOf(SortMove(key, "Arcane Signet", UNSORTED_KEY, "l7", 1, 2.0, 2.0, "for testing")), emptyList())
        assertTrue(settings.sortLog.isEmpty())
        assertEquals(0L, items().single { it.card.name == "Arcane Signet" }.binderId)
        // Out of Unsorted: exempt.
        sorter.apply(listOf(SortMove(key, "Arcane Signet", UNSORTED_KEY, levelKey(0), 1, 2.0, 2.0, "for testing")), emptyList())
        assertEquals(l1, items().single { it.card.name == "Arcane Signet" }.binderId)
        assertEquals(listOf("1"), settings.sortLog.map { it.split('|').last() })
        assertNotNull(db.binderDao().get(l1))
        // Out of a level, into another: it counts against the week.
        sorter.apply(listOf(SortMove(key, "Arcane Signet", levelKey(0), levelKey(1), 1, 2.0, 2.0, "for testing")), emptyList())
        assertEquals(listOf("1", "0"), settings.sortLog.map { it.split('|').last() })
        assertTrue(items().single { it.card.name == "Arcane Signet" }.binderId == settings.sortLevels[1].binder.toLong())
        assertNotNull(db.binderDao().get(l1))
        // Now and then, into a level it's in already: no move, nothing logged.
        sorter.apply(listOf(SortMove(key, "Arcane Signet", levelKey(1), levelKey(1), 1, 2.0, 2.0, "for testing")), emptyList())
        assertEquals(2, settings.sortLog.size)
    }

    @Test
    fun theDecksNeedWhatYouDontOwn() = runBlocking {
        setUpTwoLevelsAndDecks()
        deckOfYourOwn("Jace, the Mind Sculptor" to 1)
        val plan = sorter.suggest()
        assertEquals(1, plan.deckShort)
        assertTrue(plan.moves.none { it.to == DECKS_KEY })
    }

    // ---- Deleted binders --------------------------------------------------------------------------

    @Test
    fun aDeletedLevelBinderShiftsTheKeys() = runBlocking {
        sorter.setUp(
            levels = listOf(
                Triple(BinderChoice(newName = "Collection binder"), 200, 0.5),
                Triple(BinderChoice(newName = "Not bulk"), 1_000, 0.5),
                Triple(BinderChoice(newName = "Bulk"), 0, null),
            ),
            decks = null, mix = 50, weekly = 10, together = Together.OFF,
            outlierRatio = 3.0, outlierGap = 5.0, deckCopies = DeckCopies.BEST,
        )
        addCard(signetId, "Arcane Signet", "cmm", "367", 2.0, Binder.UNSORTED)
        val all = binders()
        val bulk = all.getValue("Bulk")
        // The middle level's binder goes (as when a phone deletes one): the levels are now two, and "Bulk" is the second.
        db.binderDao().delete(all.getValue("Not bulk"))
        val levels = sorter.levels()
        assertEquals(listOf("Collection binder", "Bulk"), levels.map { it.second.name })

        val plan = sorter.suggest()
        assertEquals(listOf(levelKey(0), levelKey(1), DECKS_KEY, UNSORTED_KEY), plan.after.keys.toList())
        sorter.apply(plan.moves, emptyList())
        // The only copy is worth E2: the first level's floor of E0.50 takes it.
        assertEquals(1, items().count { it.binderId == all.getValue("Collection binder") })
        assertNotNull(db.binderDao().get(bulk))
    }

    // ---- The old skips ----------------------------------------------------------------------------

    @Test
    fun oldSkipsSayTheLevelsTheyAreNow() = runBlocking {
        sorter.setUp(
            levels = listOf(
                Triple(BinderChoice(newName = "Collection binder"), 200, 0.5),
                Triple(BinderChoice(newName = "Not bulk"), 1_000, 0.5),
                Triple(BinderChoice(newName = "Bulk"), 0, null),
            ),
            decks = null, mix = 50, weekly = 10, together = Together.OFF,
            outlierRatio = 3.0, outlierGap = 5.0, deckCopies = DeckCopies.BEST,
        )
        addCard(signetId, "Arcane Signet", "cmm", "367", 2.0, Binder.UNSORTED)
        val key = CardKind.of(items().single()).key
        // Turned down before 1.107: it may not go to the top or the bulk ("mid", l2, is open).
        settings.sortSkips = setOf("$key>top", "$key>bulk")
        assertEquals("l2", sorter.suggest().moves.single().to)
        // "mid" is l2 now, so the second level is closed too: Unsorted stays.
        settings.sortSkips = setOf("$key>top", "$key>mid", "$key>bulk")
        assertTrue(sorter.suggest().moves.isEmpty())
        // Only "bulk" (the last level, l3): "top" is l1 now, so the card goes there.
        settings.sortSkips = setOf("$key>bulk")
        assertEquals("l1", sorter.suggest().moves.single().to)
    }

    // ---- The setup's checks ----------------------------------------------------------------------

    private fun setUpFails(levels: List<Triple<BinderChoice, Int, Double?>>, decks: BinderChoice? = null): String {
        val error = runCatching { runBlocking { sorter.setUp(levels, decks, 50, 10, Together.NAME, 3.0, 5.0, DeckCopies.BEST) } }.exceptionOrNull()
        assertTrue("expected an error", error is IllegalArgumentException)
        return error!!.message.orEmpty()
    }

    @Test
    fun setUpChecksTheLevels() = runBlocking {
        assertEquals("Set up at least one level.", setUpFails(emptyList()))
        assertEquals("There can be at most six levels.", setUpFails((1..7).map { Triple(BinderChoice(newName = "Level $it"), 100, null) }))
        assertEquals(
            "The copies a level holds can't be below 0.",
            setUpFails(listOf(Triple(BinderChoice(newName = "All"), -1, null))),
        )
        // Two new levels of the same name would be one and the same binder.
        assertEquals(
            "Two places can't use the same binder.",
            setUpFails(listOf(Triple(BinderChoice(newName = "All"), 100, null), Triple(BinderChoice(newName = "All"), 0, null))),
        )
        assertEquals(
            "Level 1 can't hold nothing: only the last level may have no limit.",
            setUpFails(listOf(Triple(BinderChoice(newName = "All"), 0, null), Triple(BinderChoice(newName = "Rest"), 0, null))),
        )
        assertEquals(
            "Two places can't use the same binder.",
            setUpFails(listOf(Triple(BinderChoice(newName = "All"), 100, null)), decks = BinderChoice(newName = "All")),
        )
        // The same existing binder twice is caught too.
        val binder = repo.createBinder("Once")
        assertEquals(
            "Two places can't use the same binder.",
            setUpFails(listOf(Triple(BinderChoice(binderId = binder), 100, null), Triple(BinderChoice(binderId = binder), 0, null))),
        )
        // And as a level and Decks.
        assertEquals(
            "Two places can't use the same binder.",
            setUpFails(listOf(Triple(BinderChoice(binderId = binder), 0, null)), decks = BinderChoice(binderId = binder)),
        )
        // Nothing was saved by the failed attempts.
        assertTrue(settings.sortLevels.isEmpty())
    }

    @Test
    fun setUpSavesTheLevelsTheDecksAndTheOptions() = runBlocking {
        val (l1, decksId) = setUpTwoLevelsAndDecks()
        assertEquals(listOf(l1), settings.sortLevels.take(1).map { it.binder.toLong() })
        assertEquals(200, settings.sortLevels[0].max)
        assertEquals(0.5, settings.sortLevels[0].minPrice!!, 1e-9)
        assertEquals(0, settings.sortLevels[1].max)
        assertNull(settings.sortLevels[1].minPrice)
        assertEquals(decksId, settings.sortDecksId)
        assertEquals(DeckCopies.BEST, settings.sortDeckCopies)
        assertEquals(50, settings.sortMix)
        assertEquals(10, settings.sortWeekly)
        // No Decks binder leaves 0.
        sorter.setUp(
            listOf(Triple(BinderChoice(newName = "Only level"), 0, null)), null, 70, 20,
            Together.PRINTING, 2.0, 3.0, DeckCopies.CHEAPEST,
        )
        assertEquals(1, settings.sortLevels.size)
        assertEquals(0L, settings.sortDecksId)
        assertEquals(Together.PRINTING, settings.sortTogether)
        assertEquals(DeckCopies.CHEAPEST, settings.sortDeckCopies)
        assertEquals(70, settings.sortMix)
        assertEquals(20, settings.sortWeekly)
        assertEquals(2.0, settings.sortOutlierRatio, 1e-9)
        assertEquals(3.0, settings.sortOutlierGap, 1e-9)
    }

    // ---- The moves' order -------------------------------------------------------------------------

    @Test
    fun thePlacesAreInOrder() {
        assertEquals(listOf(DECKS_KEY, "l1", "l2", "unsorted"), orderOf(2))
        assertEquals(0, idx(DECKS_KEY, orderOf(2)))
        assertEquals(3, idx(UNSORTED_KEY, orderOf(2)))
        // A place that isn't one (a deck's binder) comes last.
        assertEquals(4, idx("0766", orderOf(2)))
    }
}
