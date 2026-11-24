package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.117: Commander Spock's "enters or attacks" trigger casts the top card for free when its mana value is at most the
 * number of differently named lands, puts a land onto the battlefield tapped, and only otherwise draws it. Before, the
 * win turn read it as "draw a card".
 */
class Release1117Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt, null)

    private val spockText = "Strange new worlds — Whenever Commander Spock enters or attacks, reveal the top card of your library. You may cast " +
        "that card without paying its mana cost if that spell's mana value is equal to or less than the number of differently named lands you control. " +
        "If it's a land card, you may put it onto the battlefield tapped. If that card is still on top of your library, draw a card."

    private fun spock(text: String) = card("Commander Spock", "Legendary Creature — Vulcan Human Scientist", "{2}{G}{W}{U}", text, "3/5", commander = true)

    // Twelve differently named lands, so the free spell's limit grows as they come in.
    private val deck = (1..12).map { card("Land $it", "Land", "", "({T}: Add {G}, {W} or {U}.)", produced = "GWU", quantity = 3) } + listOf(
        card("Bear", "Creature — Bear", "{1}{G}", "", "2/2", quantity = 25),
        card("Beast", "Creature — Beast", "{4}{G}", "", "5/5", quantity = 27),
    )

    private fun byTurn10(text: String) = WinOddsCalc.calculate(listOf(spock(text)) + deck, "GWU", emptyList(), games = 2000)!!.byTurn[9]

    @Test fun spockBeatsADrawTrigger() {
        val real = byTurn10(spockText)
        val draw = byTurn10("Whenever Commander Spock enters or attacks, draw a card.")
        assertTrue("Spock $real% by turn 10, a draw trigger $draw%", real >= draw + 10)
    }
}
