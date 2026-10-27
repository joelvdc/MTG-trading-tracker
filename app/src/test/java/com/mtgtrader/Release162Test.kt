package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.62: the win turn's trigger timing (upkeep, combat, end step), rituals, mana for each of a kind, cost reducers,
 * extra land drops, fetch lands, "choose one" modes and spell // land cards.
 */
class Release162Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt, null)

    private val swamp = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", produced = "B", quantity = 20)
    private val mountain = card("Mountain", "Basic Land — Mountain", "", "({T}: Add {R}.)", produced = "R", quantity = 18)
    private val leader = card("Leader", "Legendary Creature — Human", "{8}{B}{R}", "", "0/1", commander = true)
    private fun bears(n: Int) = card("Bear", "Creature — Bear", "{1}{B}", "", "2/2", quantity = n)
    private fun blanks(n: Int) = card("Blank", "Sorcery", "{B}", "You gain 1 life.", quantity = n)

    private fun odds(cards: List<StatsCard>, lands: List<StatsCard> = listOf(swamp, mountain), games: Int = 2000): WinOdds =
        WinOddsCalc.calculate(listOf(leader) + lands + cards, "BR", emptyList(), games = games)!!

    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, setOf('B', 'R'), emptyList(), emptySet())

    private fun WinOdds.wonBy(turn: Int) = byTurn[turn - 1]

    @Test fun upkeepTriggersWaitAndEndStepTriggersGoTheTurnTheCardComesIn() {
        val upkeep = card("Upkeep Drainer", "Enchantment", "{2}{B}", "At the beginning of your upkeep, each opponent loses 1 life.", quantity = 15)
        val endStep = card("End Drainer", "Enchantment", "{2}{B}", "At the beginning of your end step, each opponent loses 1 life.", quantity = 15)
        assertTrue(classify(upkeep).earlyTrigger)
        assertTrue(classify(endStep).lateTrigger)
        val early = odds(listOf(upkeep, bears(25)))
        val late = odds(listOf(endStep, bears(25)))
        // Before 1.62 both went the turn they were cast: the same games.
        assertTrue("${early.byTurn} vs ${late.byTurn}", late.byTurn.sum() > early.byTurn.sum())
    }

    @Test fun eachEndStepCountsUnlessItHasACondition() {
        assertEquals(1, classify(card("A", "Enchantment", "{2}{B}", "At the beginning of each end step, each opponent loses 1 life.")).drainTurn)
        assertEquals(0, classify(card("B", "Enchantment", "{2}{B}", "At the beginning of each end step, if a creature died this turn, each opponent loses 1 life.")).drainTurn)
        assertEquals(1, classify(card("C", "Enchantment", "{2}{B}", "At the beginning of each combat, each opponent loses 1 life.")).drainTurn)
        val share = "At the beginning of each end step, draw a card for each opponent who drew two or more cards this turn, then create a Treasure " +
            "token for each opponent who had two or more lands enter the battlefield under their control this turn."
        assertEquals(0, classify(card("Smuggler's Share", "Enchantment", "{2}{W}", share)).drawTurn)
    }

    @Test fun legionWarbossGoblinsAttackTheTurnTheyAreMade() {
        val text = "Mentor\nAt the beginning of combat on your turn, create a 1/1 red Goblin creature token. That token gains haste until end of turn and attacks this combat if able."
        val warboss = classify(card("Legion Warboss", "Creature — Goblin Soldier", "{2}{R}", text, "2/2"))
        assertTrue(warboss.lateTrigger)
        assertEquals(1, warboss.tokensTurn)
        assertTrue(warboss.token!!.haste)
        val hasty = odds(listOf(card("Legion Warboss", "Creature — Goblin Soldier", "{2}{R}", text, "2/2", quantity = 20), bears(10)))
        val slow = odds(listOf(card("Slow Boss", "Creature — Goblin Soldier", "{2}{R}", "At the beginning of combat on your turn, create a 1/1 red Goblin creature token.", "2/2", quantity = 20), bears(10)))
        assertTrue("${hasty.byTurn} vs ${slow.byTurn}", hasty.byTurn.sum() > slow.byTurn.sum())
        assertFalse(classify(card("Q", "Creature", "{2}{R}", "When this creature enters, create a 1/1 red Goblin creature token with \"This creature has haste.\"", "1/1")).token!!.haste)
    }

    @Test fun ritualsCastWhatTheyPayFor() {
        val ritual = classify(card("Dark Ritual", "Instant", "{B}", "Add {B}{B}{B}."))
        assertEquals(listOf(setOf('B') to 3), ritual.ritual)
        assertEquals(5, classify(card("Seething Song", "Instant", "{2}{R}", "Add {R}{R}{R}{R}{R}.")).ritualMana)
        assertEquals(3, classify(card("Cabal Ritual", "Instant", "{1}{B}", "Add {B}{B}{B}.\nThreshold — Add {B}{B}{B}{B}{B} instead if seven or more cards are in your graveyard.")).ritualMana)
        val giant = card("Giant", "Creature — Giant", "{4}{B}", "", "7/7", quantity = 25)
        val with = odds(listOf(giant, card("Dark Ritual", "Instant", "{B}", "Add {B}{B}{B}.", quantity = 15)))
        val without = odds(listOf(giant, blanks(15)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.wonBy(10) > without.wonBy(10) + 20)
    }

    @Test fun landsAndCreaturesThatTapForEachOfAKind() {
        val cradle = classify(card("Gaea's Cradle", "Legendary Land", "", "{T}: Add {G} for each creature you control.", produced = "G"))
        assertEquals("creature" to 'G', cradle.manaPer)
        val coffers = classify(card("Cabal Coffers", "Land", "", "{2}, {T}: Add {B} for each Swamp you control.", produced = "B"))
        assertEquals("swamp" to 'B', coffers.manaPer)
        assertEquals(2, coffers.manaPerCost)
        val priest = classify(card("Priest of Titania", "Creature — Elf Druid", "{1}{G}", "{T}: Add {G} for each Elf on the battlefield.", "1/1", produced = "G"))
        assertEquals("elf" to 'G', priest.manaPer)
        // Coffers with twenty Swamps casts a 15-mana giant much sooner.
        val giant = card("Huge", "Creature — Giant", "{14}{B}", "", "40/40", quantity = 20)
        val with = odds(listOf(giant, card("Cabal Coffers", "Land", "", "{2}, {T}: Add {B} for each Swamp you control.", produced = "B", quantity = 5), bears(5)))
        val without = odds(listOf(giant, card("Wastes", "Basic Land", "", "{T}: Add {C}.", produced = "C", quantity = 5), bears(5)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.wonBy(15) > without.wonBy(15) + 20)
    }

    @Test fun costReducersForAllSpellsAndOtherThanTheHand() {
        assertEquals(listOf("spell" to 1), classify(card("Helm", "Artifact", "{2}", "Spells you cast cost {1} less to cast.")).reducers)
        val notHand = classify(card("Ghost", "Creature — Spirit", "{2}{B}", "Spells you cast from anywhere other than your hand cost {1} less to cast.", "2/2")).reducers
        assertEquals(1, notHand.single().second)
        assertEquals(listOf("commander" to 2), classify(card("Medallion", "Artifact", "{2}", "Commander spells you cast cost {2} less to cast.")).reducers)
        assertEquals(listOf("creature" to 1), classify(card("Goreclaw", "Creature", "{3}{G}", "Creature spells you cast cost {1} less to cast.", "4/3")).reducers)
    }

    @Test fun extraLandDropsAndFetchLandsSetOffLandfallMore() {
        assertEquals(2, classify(card("Azusa", "Legendary Creature — Human Monk", "{2}{G}", "You may play two additional lands on each of your turns.", "1/2")).extraLands)
        assertEquals(1, classify(card("Explore", "Sorcery", "{1}{G}", "You may play an additional land this turn.\nDraw a card.")).extraLandsNow)
        val fetch = card("Bloodstained Mire", "Land", "", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Swamp or Mountain card, put it onto the battlefield, then shuffle.", quantity = 10)
        assertTrue(classify(fetch).fetchLand)
        val ruin = card("Ruin", "Creature — Elemental", "{1}{R}", "Landfall — Whenever a land you control enters, this creature deals 1 damage to each opponent.", "1/1", quantity = 15)
        val fetches = odds(listOf(ruin, bears(10)), listOf(swamp, mountain.copy(quantity = 8), fetch))
        val basics = odds(listOf(ruin, bears(10)), listOf(swamp, mountain))
        assertTrue("${fetches.byTurn} vs ${basics.byTurn}", fetches.byTurn.sum() > basics.byTurn.sum())
        val exploration = card("Exploration", "Enchantment", "{B}", "You may play an additional land on each of your turns.", quantity = 8)
        // Lands are played at once (not held back for landfall later), so it shows in a deck short of mana.
        val giant = card("Giant", "Creature — Giant", "{6}{B}", "", "9/9", quantity = 17)
        val explored = odds(listOf(giant, exploration), listOf(swamp.copy(quantity = 28), mountain.copy(quantity = 18)))
        val plain = odds(listOf(giant, blanks(8)), listOf(swamp.copy(quantity = 28), mountain.copy(quantity = 18)))
        assertTrue("${explored.byTurn} vs ${plain.byTurn}", explored.byTurn.sum() > plain.byTurn.sum())
    }

    @Test fun chooseOneReadsTheBestModeOnly() {
        val charm = classify(card("Charm", "Instant", "{B}{R}", "Choose one —\n• Each opponent loses 3 life.\n• Draw two cards.\n• Create a 1/1 black Rat creature token."))
        assertEquals(3, charm.drainOnce)
        assertEquals(0, charm.drawOnce)
        assertEquals(0, charm.tokensOnce)
        val command = classify(card("Command", "Sorcery", "{2}{B}{R}", "Choose two —\n• Each opponent loses 3 life.\n• Draw two cards.\n• You gain 2 life.\n• Create a 1/1 black Rat creature token."))
        assertEquals(3, command.drainOnce)
        assertEquals(2, command.drawOnce)
        assertEquals(0, command.tokensOnce)
        val trigger = classify(card("Trigger", "Creature — Human", "{3}{B}", "When this creature enters, choose one —\n• Each opponent loses 2 life.\n• Draw a card.", "2/2"))
        assertEquals(2, trigger.drainOnce)
        assertEquals(0, trigger.drawOnce)
        // A token that grows beats a card (Urza's Command in an artifact deck).
        val urza = classify(card(
            "Urza's Command", "Instant", "{1}{U}",
            "Choose one —\n• Creatures your opponents control get -2/-0 until end of turn.\n" +
                "• Create a 0/0 colorless Construct artifact creature token with \"This token gets +1/+1 for each artifact you control.\"\n" +
                "• Scry 1. Draw a card.",
        ))
        assertEquals(1, urza.tokensOnceCount)
        assertEquals(0, urza.drawOnce)
        // "One or more": all of them, as before.
        val all = classify(card("Escalated", "Sorcery", "{B}", "Choose one or more —\n• Each opponent loses 1 life.\n• Draw a card."))
        assertEquals(1, all.drainOnce)
        assertEquals(1, all.drawOnce)
    }

    @Test fun spellLandCardsAreCastAsTheSpellWithLandsEnough() {
        val rebirth = StatsCard(
            "Malakir Rebirth // Malakir Mire", "mr", 15, 1.0, "Instant // Land", false, "Instant // Land", "{B} // ", "B",
            "Choose target creature. When that creature dies this turn, return it to the battlefield.\u0000Malakir Mire enters tapped.\n{T}: Add {B}.", null,
        )
        val bolt = StatsCard(
            "Bolt // Ridge", "br", 15, 2.0, "Instant // Land", false, "Instant // Land", "{1}{R} // ", "R",
            "This spell deals 3 damage to each opponent.\u0000Ridge enters tapped.\n{T}: Add {R}.", null,
        )
        val w = WinOddsCalc.classify(bolt.copy(oracleText = bolt.oracleText!!.replace("\u0000", com.mtgtrader.data.CardInfo.FACE_BREAK)), setOf('B', 'R'), emptyList(), emptySet())
        assertTrue(w.land)
        assertTrue("Land" in w.types)
        assertNotNull(w.spellSide)
        assertEquals(3, w.spellSide!!.drainOnce)
        assertNull(classify(swamp).spellSide)
        val faces = { c: StatsCard -> c.copy(oracleText = c.oracleText!!.replace("\u0000", com.mtgtrader.data.CardInfo.FACE_BREAK)) }
        val withBolts = odds(listOf(faces(bolt), bears(25)), listOf(swamp.copy(quantity = 23)))
        val asLands = odds(listOf(faces(rebirth), bears(25)), listOf(swamp.copy(quantity = 23)))
        assertTrue("${withBolts.damage}", (withBolts.damage[WinOddsCalc.DRAIN] ?: 0) > 0)
        assertTrue("${withBolts.byTurn} vs ${asLands.byTurn}", withBolts.byTurn.sum() > asLands.byTurn.sum())
    }
}
