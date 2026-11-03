package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.77: land drops with mana sinks, more "enters tapped unless …" conditions, and a ramp spell's basic of one colour. */
class Release177LandTest {
    private fun card(name: String, type: String, cost: String, text: String = "", quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, null)

    private val own = setOf('R', 'G')
    private fun land(name: String, text: String, produced: String = "RG", type: String = "Land", quantity: Int = 1) =
        card(name, type, "", text, quantity = quantity, produced = produced)
    private fun read(c: StatsCard) = ManaOddsCalc.classify(c, own, emptyList())

    private val forest = land("Forest", "({T}: Add {G}.)", "G", "Basic Land — Forest", 12)
    private val thicket = land("Sheltered Thicket", "({T}: Add {R} or {G}.)\nThis land enters tapped.", type = "Land — Mountain Forest")
    private val moria = land("Mines of Moria", "Mines of Moria enters tapped unless you control a legendary creature.\n{T}: Add {R}.", "R", "Legendary Land")
    private val annex = land("Konstrari Annex", "This land enters tapped unless you control a planeswalker.\n{T}: Add {R} or {G}.")
    private val mine = land("Dwarven Mine", "({T}: Add {R}.)\nThis land enters tapped unless you control three or more other Mountains.\nWhen this land enters untapped, create a 1/1 red Dwarf creature token.", "R", "Land — Mountain")
    private val woods = land("Bleeding Woods", "This land enters tapped unless a player has 13 or less life.\n{T}: Add {R} or {G}.")

    @Test fun conditionsAreRead() {
        assertTrue(read(moria).untap!!.legendary)
        assertTrue(read(annex).untap!!.planeswalker)
        assertEquals(3, read(mine).untap!!.ofTypes)
        assertEquals(setOf("mountain"), read(mine).untap!!.types)
        assertEquals(13, read(woods).untap!!.lowLife)
        // They used to count as always untapped.
        for (l in listOf(moria, annex, mine, woods)) assertTrue(l.name, read(l).tapped)
    }

    @Test fun conditionsAreChecked() {
        fun up(l: StatsCard, out: ManaOddsCalc.LandsOut) = ManaOddsCalc.entersUntapped(read(l), out, emptyList())
        val none = ManaOddsCalc.LandsOut(2, 2, setOf("mountain"), mapOf("mountain" to 2))
        assertFalse(up(moria, none))
        assertTrue(up(moria, ManaOddsCalc.LandsOut(2, 2, setOf("mountain"), legendary = true)))
        assertFalse(up(annex, none))
        assertTrue(up(annex, ManaOddsCalc.LandsOut(2, 2, setOf("mountain"), planeswalker = true)))
        assertFalse(up(mine, none))
        assertTrue(up(mine, ManaOddsCalc.LandsOut(4, 3, setOf("mountain", "forest"), mapOf("mountain" to 3, "forest" to 1))))
        // Everyone starts at 40: Bleeding Woods comes in tapped until a player is at 13 or less.
        assertFalse(up(woods, none))
        assertTrue(up(woods, ManaOddsCalc.LandsOut(2, 2, emptySet(), lowestLife = 13)))
    }

    @Test fun rampBasicIsOneColour() {
        assertEquals(setOf('G'), ManaOddsCalc.rampColour(setOf('R', 'G'), listOf(setOf('R'))))
        assertEquals(setOf('R'), ManaOddsCalc.rampColour(setOf('R', 'G'), listOf(setOf('R'), setOf('G'), setOf('G'))))
        // A tie goes in WUBRG order.
        assertEquals(setOf('R'), ManaOddsCalc.rampColour(setOf('R', 'G'), emptyList()))
        assertEquals(setOf('C'), ManaOddsCalc.rampColour(setOf('C'), emptyList()))
    }

    @Test fun untappedLandWithAManaSink() {
        val f = read(forest)
        val t = read(thicket)
        // Nothing to cast: the tapped land, unless an X spell or an ability can use the mana.
        assertEquals(t, ManaOddsCalc.chooseLand(listOf(f, t), { it }, { !it.tapped }, setOf('R'), listOf(setOf('R') to 1), emptyList()))
        assertEquals(f, ManaOddsCalc.chooseLand(listOf(f, t), { it }, { !it.tapped }, setOf('R'), listOf(setOf('R') to 1), emptyList(), sink = true))
    }

    @Test fun mountainsForDwarvenMineAndTheCommanderForMoria() {
        val ruby = card("Ruby, Daring Tracker", "Legendary Creature — Human Ranger", "{R}{G}", "Haste\n{T}: Add {R} or {G}.", commander = true)
        val blanks = card("Blank", "Sorcery", "{5}{G}", "You gain 1 life.", quantity = 69)
        val deck = listOf(ruby, forest, land("Mountain", "({T}: Add {R}.)", "R", "Basic Land — Mountain", 12), moria.copy(quantity = 2), mine.copy(quantity = 2), woods, annex, blanks)
        assertEquals(100, deck.sumOf { it.quantity })
        // Runs with the new conditions and still casts Ruby by turn 3 most of the time.
        val odds = ManaOddsCalc.calculate(deck, "RG", games = 2000)!!.commanders.single().byTurn
        assertTrue("$odds", odds[2] >= 70)
    }
}
