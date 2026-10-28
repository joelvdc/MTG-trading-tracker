package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.70: Currency Converter, Renewed Solidarity, spree, "when this dies", Wilderness Reclamation, lands' token abilities. */
class Release170Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    private val plains = card("Plains", "Basic Land — Plains", "", "({T}: Add {W}.)", produced = "W", quantity = 19)
    private val mountain = card("Mountain", "Basic Land — Mountain", "", "({T}: Add {R}.)", produced = "R", quantity = 19)
    private val leader = card("Leader", "Legendary Creature — Human", "{8}{R}{W}", "", "0/1", commander = true)
    private fun blanks(n: Int) = card("Blank", "Sorcery", "{W}", "You gain 1 life.", quantity = n)
    private fun odds(cards: List<StatsCard>, games: Int = 2000): WinOdds =
        WinOddsCalc.calculate(listOf(leader, plains, mountain) + cards, "RW", emptyList(), games = games)!!
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, "WUBRG".toSet(), emptyList(), emptySet())

    private val converter = card(
        "Currency Converter", "Artifact", "{1}",
        "Whenever you discard a card, you may exile that card from your graveyard.\n{2}, {T}: Draw a card, then discard a card.\n" +
            "{T}: Put a card exiled with this artifact into its owner's graveyard. If it's a land card, create a Treasure token. If it's a nonland card, create a 2/2 black Rogue creature token.",
    )

    @Test fun currencyConverterNeedsExiledCards() {
        val c = classify(converter)
        assertTrue(c.exilesDiscards)
        val rogue = c.taps.single { it.fromExiled }
        assertEquals(0, rogue.treasures)
        assertEquals(1, rogue.tokens)
        // Alone, it makes a Rogue at most every other turn (its loot and its Rogue share the {T}); before 1.70 one every turn.
        val with = odds(listOf(converter.copy(quantity = 20), blanks(20)))
        val rogues = card("Rogue Maker", "Artifact", "{1}", "{T}: Create a 2/2 black Rogue creature token.", quantity = 20)
        val everyTurn = odds(listOf(rogues, blanks(20)))
        assertTrue("${with.byTurn} vs ${everyTurn.byTurn}", with.byTurn.sum() < everyTurn.byTurn.sum())
    }

    @Test fun renewedSolidarityCopiesOnlyNewTokens() {
        val solidarity = card(
            "Renewed Solidarity", "Enchantment", "{2}{W}",
            "As this enchantment enters, choose a creature type.\nCreatures you control of the chosen type get +1/+0.\n" +
                "At the beginning of your end step, for each token you control of the chosen type that entered this turn, create a token that's a copy of it.",
        )
        val s = classify(solidarity)
        assertTrue(s.copyNewTokens)
        assertEquals(1, s.chosenPump)
        assertTrue(s.scaled.isEmpty())
        val maker = card("Soldier Maker", "Enchantment", "{1}{W}", "At the beginning of your upkeep, create a 1/1 white Soldier creature token.", quantity = 10)
        val with = odds(listOf(maker, solidarity.copy(quantity = 5), blanks(15)))
        val without = odds(listOf(maker, blanks(20)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() > without.byTurn.sum())
        // Copies of new tokens only: the board doesn't double every turn.
        assertTrue("${with.byTurn}", with.byTurn[7] < 50)
    }

    @Test fun spreePaysForItsModes() {
        val heist = classify(card(
            "Great Train Heist", "Instant", "{R}",
            "Spree (Choose one or more additional costs.)\n+ {2}{R} — Untap all creatures you control. If it's your combat phase, there is an additional combat phase after this phase.\n" +
                "+ {2} — Creatures you control get +1/+0 and gain first strike until end of turn.\n+ {R} — Choose target opponent. Whenever a creature you control deals combat damage to that player this turn, create a tapped Treasure token.",
        ))
        assertTrue(heist.onEnter.extraCombat)
        assertTrue(heist.cost.manaValue >= 4)
        val steps = classify(card(
            "Three Steps Ahead", "Instant", "{U}",
            "Spree (Choose one or more additional costs.)\n+ {1}{U} — Counter target spell.\n+ {3} — Create a token that's a copy of target artifact or creature you control.\n+ {2} — Draw two cards, then discard a card.",
        ))
        assertEquals(1, steps.onEnter.copies)
        // The counterspell isn't paid for.
        assertEquals(6, steps.cost.manaValue)
    }

    @Test fun deathTriggers() {
        val traveler = classify(card("Doomed Traveler", "Creature — Human Soldier", "{W}", "When this creature dies, create a 1/1 white Spirit creature token with flying.", "1/1"))
        assertEquals(1, traveler.onDeath.tokens)
        val atsushi = classify(card(
            "Atsushi, the Blazing Sky", "Legendary Creature — Dragon Spirit", "{2}{R}{R}",
            "Flying, trample\nWhen Atsushi dies, choose one —\n• Exile the top two cards of your library. Until the end of your next turn, you may play those cards.\n• Create three Treasure tokens.", "4/4",
        ))
        assertEquals(3, atsushi.onDeath.treasures)
        assertEquals(0, atsushi.onDeath.tokens)
        assertTrue(classify(card("Vaultborn Tyrant", "Creature — Dinosaur", "{5}{G}{G}", "Trample\nWhen this creature dies, if it's not a token, create a token that's a copy of it, except it's an artifact in addition to its other types.", "6/6")).deathCopy)
        val wilhelt = classify(card("Wilhelt, the Rotcleaver", "Legendary Creature — Zombie Warlock", "{2}{U}{B}", "Whenever another Zombie you control dies, if it didn't have decayed, create a 2/2 black Zombie creature token with decayed.", "3/3"))
        assertEquals("zombie", wilhelt.deathToken!!.first)
        assertTrue(wilhelt.deathTokenDecayed)
    }

    @Test fun landsAndWildernessReclamation() {
        assertTrue(classify(card("Wilderness Reclamation", "Enchantment", "{3}{G}", "At the beginning of your end step, untap all lands you control.")).untapLandsEnd)
        val castle = classify(card("Castle Ardenvale", "Land", "", "This land enters tapped unless you control a Plains.\n{T}: Add {W}.\n{2}{W}{W}, {T}: Create a 1/1 white Human creature token.", produced = "W"))
        assertEquals(4, castle.landTaps.single().cost.manaValue)
        val mirrex = classify(card("Mirrex", "Land — Sphere", "", "{T}: Add {C}.\n{T}: Add one mana of any color. Activate only if this land entered this turn.\n{3}, {T}: Create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and \"This token can't block.\"", produced = "WUBRGC"))
        assertEquals(1, mirrex.landTaps.single().token!!.toxic)
        // Lands making creatures win games a deck of plain lands can't.
        val castles = card("Castle", "Land", "", "{T}: Add {W}.\n{1}, {T}: Create a 2/2 white Knight creature token.", produced = "W", quantity = 30)
        val deck = WinOddsCalc.calculate(listOf(leader, castles, mountain.copy(quantity = 8), blanks(61)), "RW", emptyList(), games = 1000)!!
        assertTrue("${deck.byTurn}", deck.byTurn.last() > 0)
        assertFalse(classify(card("Plain Land", "Land", "", "{T}: Add {W}.", produced = "W")).landTaps.isNotEmpty())
    }
}
