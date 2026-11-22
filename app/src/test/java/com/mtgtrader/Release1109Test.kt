package com.mtgtrader

import com.mtgtrader.data.Binder
import com.mtgtrader.data.TradeBinderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the trade binder takes a spare copy from (since 1.109): Unsorted, then other binders, then the sort's levels from the last. */
class TradeBinderSourceRankTest {
    // The sort's three levels best first, its Decks binder, and a binder named after a deck.
    private val best = 10L
    private val middle = 11L
    private val bulk = 12L
    private val decks = 13L
    private val deckBinder = 14L
    private val other = 15L
    private val levels = listOf(best, middle, bulk)

    private fun rank(binderId: Long, levelIds: List<Long> = levels, decksId: Long? = decks, deckBinders: Set<Long> = setOf(deckBinder)) =
        TradeBinderPlanner.sourceRank(binderId, levelIds, decksId, deckBinders)

    @Test
    fun unsortedThenOtherBindersThenTheLastLevelToTheFirst() {
        assertEquals(0, rank(Binder.UNSORTED))
        assertEquals(1, rank(other))
        // Last level = 2, the one before = 3, the first (best) = 1 + n.
        assertEquals(2, rank(bulk))
        assertEquals(3, rank(middle))
        assertEquals(4, rank(best))
    }

    @Test
    fun theDecksBinderAndDeckNamedBindersComeLast() {
        assertEquals(2 + levels.size, rank(decks))
        assertEquals(3 + levels.size, rank(deckBinder))
        assertTrue(rank(best) < rank(decks))
        assertTrue(rank(decks) < rank(deckBinder))
        // Every rank in the order.
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), listOf(Binder.UNSORTED, other, bulk, middle, best, decks, deckBinder).map { rank(it) })
    }

    @Test
    fun noSortSetUpKeepsTheOlderOrder() {
        // No levels and no Decks binder: Unsorted 0, other binders 1, deck-named binders last (as before 1.109).
        assertEquals(listOf(0, 1, 2), listOf(Binder.UNSORTED, other, deckBinder).map { rank(it, levelIds = emptyList(), decksId = null) })
        // Levels without a Decks binder: the deck-named binders still come last (3 + n), after every level.
        assertEquals(3 + levels.size, rank(deckBinder, decksId = null))
        assertEquals(2, rank(bulk, decksId = null))
    }

    @Test
    fun aBinderThatIsBothALevelAndNamedAfterADeckKeepsItsLevel() {
        assertEquals(2, rank(bulk, deckBinders = setOf(bulk)))
    }
}
