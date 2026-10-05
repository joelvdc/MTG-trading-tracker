package com.mtgtrader

import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.CardKind
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.TradeBinderPlanner
import com.mtgtrader.data.TradeBinderRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which copy of a card the trade binder offers: the best one stays home for decks, or the copy picked with Swap. */
class TradeBinderCopiesTest {
    private var id = 1L

    private fun copy(printing: String, price: Double, qty: Int = 1, binder: Long = 0): CollectionRow {
        val ref = CardRef(printing, "Sol Ring", printing, printing.uppercase(), "1", "uncommon", null, 1, null, null, true, false)
        return CollectionRow(
            CollectionItem(id = id++, card = ref, foil = false, quantity = qty, binderId = binder),
            PriceEntity(1, price, price, price, price, price, price, null, null, null, null, null, null),
            CardInfo(printing, null, "", "", "Artifact", 1.0, 1),
        )
    }

    // Three copies: a fancy €40 one and two plain €1.50 ones; one deck uses Sol Ring.
    private val rows = listOf(copy("fancy", 40.0), copy("plain", 1.5, qty = 2))
    private val needs = mapOf("sol ring" to 1)

    private fun plan(rules: TradeBinderRules, skipped: Set<String> = emptySet(), binderRows: List<CollectionRow> = rows) =
        TradeBinderPlanner.plan(binderRows, needs, emptySet(), 9L, rules, PriceType.TREND, skipped)

    @Test
    fun theBestCopyStaysWithTheDeck() {
        val changes = plan(TradeBinderRules(keepBestForDecks = true))
        assertEquals(listOf("plain"), changes.map { it.card.scryfallId })
        assertEquals(2, changes.single().copies)
    }

    @Test
    fun withoutTheRuleTheMostValuableIsOffered() {
        val changes = plan(TradeBinderRules(keepBestForDecks = false))
        assertEquals(setOf("fancy", "plain"), changes.map { it.card.scryfallId }.toSet())
        assertEquals(1, changes.single { it.card.scryfallId == "plain" }.copies)
    }

    @Test
    fun swapPicksTheCopyToTrade() {
        val pick = TradeBinderPlanner.preferKey("Sol Ring", CardKind("fancy", false, false, "NM", "EN"))
        val changes = plan(TradeBinderRules(keepBestForDecks = true), setOf(pick))
        assertEquals(setOf("fancy", "plain"), changes.map { it.card.scryfallId }.toSet())
    }

    @Test
    fun turningTheRuleOnSwapsAnExpensiveCopyOut() {
        val inBinder = listOf(copy("fancy", 40.0, binder = 9L), copy("plain", 1.5, qty = 2))
        val changes = plan(TradeBinderRules(keepBestForDecks = true), binderRows = inBinder)
        val out = changes.single { !it.add }
        assertEquals("fancy", out.card.scryfallId)
        assertTrue(out.reason, "best stays home" in out.reason)
        assertEquals("plain", changes.single { it.add }.card.scryfallId)
    }

    @Test
    fun anyValueIncludesCheapCards() {
        val cheap = listOf(copy("plain", 0.1, qty = 3))
        assertTrue(plan(TradeBinderRules(minValue = 1.0), binderRows = cheap).isEmpty())
        assertEquals(2, plan(TradeBinderRules(minValue = 0.0), binderRows = cheap).single().copies)
    }
}
