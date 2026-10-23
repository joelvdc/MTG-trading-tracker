package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.56: a creature attacks one player, so one huge attacker (Ruby's Jumbo Cactuar with its +9999/+0) knocks out one
 * opponent, not all three. Myriad still hits each opponent.
 */
class Release156Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt, null)

    private val forest = card("Forest", "Basic Land — Forest", "", "({T}: Add {G}.)", produced = "G", quantity = 49)
    private val commander = card("Leader", "Legendary Creature — Human", "{8}{G}{G}", "", "0/1", commander = true)

    @Test fun oneAttackerHitsOneOpponent() {
        val giant = card("Giant", "Creature — Giant", "{G}", "Haste", "999/999", quantity = 50)
        val odds = WinOddsCalc.calculate(listOf(commander, giant, forest), "G", emptyList(), games = 500)!!
        // Turn 1: one Giant knocks out one opponent; turn 2: two more Giants finish the other two.
        assertEquals(0, odds.byTurn[0])
        assertTrue("${odds.byTurn}", odds.byTurn[1] > 50)
    }

    @Test fun myriadHitsEachOpponent() {
        val giant = card("Giant", "Creature — Giant", "{G}", "Haste\nMyriad (Whenever this creature attacks, for each opponent other than defending player, you may create a token that's a copy of it that's tapped and attacking that player or a planeswalker they control. Exile the tokens at end of combat.)", "999/999", quantity = 50)
        val odds = WinOddsCalc.calculate(listOf(commander, giant, forest), "G", emptyList(), games = 500)!!
        assertTrue("${odds.byTurn}", odds.byTurn[0] > 50)
    }
}
