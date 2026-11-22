package com.mtgtrader

import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.Binder
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CardKind
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionSorter
import com.mtgtrader.data.DeckCopies
import com.mtgtrader.data.Finish
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.SortMove
import com.mtgtrader.data.Together
import com.mtgtrader.data.UNSORTED_KEY
import com.mtgtrader.data.levelKey
import com.mtgtrader.data.stacksOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 1.110: the sort and the trade binder find a card's stacks with one query instead of loading the whole collection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release1110Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var repo: MtgRepository
    private lateinit var sorter: CollectionSorter

    @Before
    fun setUp() {
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        val container = AppContainer(context)
        db = container.db
        repo = container.repo
        sorter = container.sorter
    }

    @After
    fun tearDown() = db.close()

    private val card = CardRef("sol-ring", "Sol Ring", "cmm", "A set", "400", "rare", null, null, 1.0, 1.0, true, true)

    @Test
    fun stacksOfFindsOnlyThatKindInEveryBinder() = runBlocking {
        val other = repo.createBinder("Other")
        repo.addToCollection(card, Finish.NONFOIL, "NM", "EN", 3, Binder.UNSORTED)
        repo.addToCollection(card, Finish.NONFOIL, "NM", "EN", 1, other)
        repo.addToCollection(card, Finish.FOIL, "NM", "EN", 1, Binder.UNSORTED)
        repo.addToCollection(card, Finish.NONFOIL, "LP", "EN", 1, Binder.UNSORTED)
        val key = CardKind("sol-ring", false, false, "NM", "EN").key
        val stacks = db.collectionDao().stacksOf(key)
        assertEquals(setOf(Binder.UNSORTED to 3, other to 1), stacks.map { it.binderId to it.quantity }.toSet())
        assertTrue(db.collectionDao().stacksOf("broken").isEmpty())
    }

    @Test
    fun sortMovesPartOfAStack() = runBlocking {
        sorter.setUp(
            levels = listOf(Triple(BinderChoice(newName = "Best"), 0, null)),
            decks = null,
            mix = 50, weekly = 0, together = Together.NAME, outlierRatio = 3.0, outlierGap = 5.0, deckCopies = DeckCopies.BEST,
        )
        val best = sorter.levels().first().second.id
        repo.addToCollection(card, Finish.NONFOIL, "NM", "EN", 3, Binder.UNSORTED)
        val key = CardKind("sol-ring", false, false, "NM", "EN").key
        sorter.apply(listOf(SortMove(key, "Sol Ring", UNSORTED_KEY, levelKey(0), 2, 1.0, 1.0, "for testing")), emptyList())
        val stacks = db.collectionDao().stacksOf(key)
        assertEquals(setOf(Binder.UNSORTED to 1, best to 2), stacks.map { it.binderId to it.quantity }.toSet())
    }
}
