package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.71: which land is played ({C} isn't a colour, tapped lands when the mana isn't used) and "enters tapped unless …". */
class Release171Test {
    private fun card(name: String, type: String, cost: String, text: String = "", quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, null)

    private val own = setOf('R', 'G')
    private fun land(name: String, text: String, produced: String = "RG", type: String = "Land", quantity: Int = 1) =
        card(name, type, "", text, quantity = quantity, produced = produced)
    private fun read(c: StatsCard) = ManaOddsCalc.classify(c, own, emptyList())

    private val forest = land("Forest", "({T}: Add {G}.)", "G", "Basic Land — Forest", 12)
    private val mountain = land("Mountain", "({T}: Add {R}.)", "R", "Basic Land — Mountain", 11)
    private val kessig = land("Kessig Wolf Run", "{T}: Add {C}.\n{X}{R}{G}, {T}: Target creature gets +X/+0 and gains trample until end of turn.", "C")
    private val thicket = land("Sheltered Thicket", "({T}: Add {R} or {G}.)\nThis land enters tapped.\nCycling {2} ({2}, Discard this card: Draw a card.)", type = "Land — Mountain Forest")
    private val rockfall = land("Rockfall Vale", "This land enters tapped unless you control two or more other lands.\n{T}: Add {R} or {G}.")
    private val rootbound = land("Rootbound Crag", "This land enters tapped unless you control a Mountain or a Forest.\n{T}: Add {R} or {G}.")
    private val cinder = land("Cinder Glade", "({T}: Add {R} or {G}.)\nThis land enters tapped unless you control two or more basic lands.", type = "Land — Mountain Forest")
    private val gameTrail = land("Game Trail", "As this land enters, you may reveal a Mountain or Forest card from your hand. If you don't, this land enters tapped.\n{T}: Add {R} or {G}.")
    private val copperline = land("Copperline Gorge", "This land enters tapped unless you control two or fewer other lands.\n{T}: Add {R} or {G}.")
    private val bugbear = land("Den of the Bugbear", "If you control two or more other lands, this land enters tapped.\n{T}: Add {R}.\n{3}{R}: Until end of turn, this land becomes a 3/2 red Goblin creature.", "R")
    private val stomping = land("Stomping Ground", "({T}: Add {R} or {G}.)\nAs this land enters, you may pay 2 life. If you don't, it enters tapped.", type = "Land — Mountain Forest")
    private val spire = land("Spire Garden", "This land enters tapped unless you have two or more opponents.\n{T}: Add {R} or {G}.")

    @Test fun conditionsAreRead() {
        assertEquals(2, read(rockfall).untap!!.min)
        assertEquals(setOf("mountain", "forest"), read(rootbound).untap!!.types)
        assertEquals(2, read(cinder).untap!!.basics)
        assertTrue(read(gameTrail).untap!!.reveal)
        assertEquals(2, read(copperline).untap!!.max)
        assertEquals(1, read(bugbear).untap!!.max)
        for (l in listOf(rockfall, rootbound, cinder, gameTrail, copperline, bugbear)) assertTrue(l.name, read(l).tapped)
        // Always tapped, or never.
        assertTrue(read(thicket).tapped)
        assertNull(read(thicket).untap)
        assertFalse(read(stomping).tapped)
        assertFalse(read(spire).tapped)
        assertTrue(read(forest).basic)
        assertEquals(setOf("mountain", "forest"), read(thicket).types)
    }

    @Test fun conditionsAreChecked() {
        val none = ManaOddsCalc.LandsOut(0, 0, emptySet())
        val twoBasics = ManaOddsCalc.LandsOut(2, 2, setOf("forest"))
        val threeOther = ManaOddsCalc.LandsOut(3, 0, setOf("mountain", "forest"))
        fun up(l: StatsCard, out: ManaOddsCalc.LandsOut, hand: List<StatsCard> = emptyList()) = ManaOddsCalc.entersUntapped(read(l), out, hand.map(::read))
        assertFalse(up(rockfall, none))
        assertTrue(up(rockfall, twoBasics))
        assertFalse(up(rootbound, none))
        assertTrue(up(rootbound, twoBasics))
        assertTrue(up(cinder, twoBasics))
        assertFalse(up(cinder, threeOther))
        assertTrue(up(copperline, twoBasics))
        assertFalse(up(copperline, threeOther))
        assertTrue(up(bugbear, ManaOddsCalc.LandsOut(1, 1, emptySet())))
        assertFalse(up(bugbear, twoBasics))
        assertFalse(up(gameTrail, none, listOf(kessig)))
        assertTrue(up(gameTrail, none, listOf(forest)))
        assertFalse(up(thicket, threeOther))
    }

    @Test fun colourlessIsNotAMissingColour() {
        val lands = listOf(read(kessig), read(forest))
        val ruby = ManaOddsCalc.cost("{R}{G}")
        // With a Mountain out, the Forest makes Ruby castable; Kessig Wolf Run's {C} used to count as a new colour.
        val pick = ManaOddsCalc.chooseLand(lands, { it }, { true }, setOf('R'), listOf(setOf('R') to 1), listOf(ruby))
        assertEquals(setOf('G'), pick.colours)
        // On turn 1 with nothing to cast, a coloured land still beats a colourless one.
        assertEquals(setOf('G'), ManaOddsCalc.chooseLand(lands, { it }, { true }, emptySet(), emptyList(), listOf(ruby)).colours)
    }

    @Test fun tappedLandWhenTheManaIsNotUsed() {
        val f = read(forest)
        val t = read(thicket)
        val ruby = ManaOddsCalc.cost("{R}{G}")
        fun pick(pool: List<Pair<Set<Char>, Int>>, have: Set<Char>) =
            ManaOddsCalc.chooseLand(listOf(f, t), { it }, { !it.tapped }, have, pool, listOf(ruby))
        // Turn 1: nothing to cast for one mana, so the tapped land goes down.
        assertEquals(t, pick(emptyList(), emptySet()))
        // Turn 2 with a Mountain out: the Forest casts Ruby now.
        assertEquals(f, pick(listOf(setOf('R') to 1), setOf('R')))
        // Ruby already cast and nothing else to cast: the tapped land again.
        assertEquals(t, ManaOddsCalc.chooseLand(listOf(f, t), { it }, { !it.tapped }, setOf('R'), listOf(setOf('R') to 1), emptyList()))
        // Of two lands entering tapped, the one that could come in untapped later waits.
        val r = read(rockfall)
        assertEquals(t, ManaOddsCalc.chooseLand(listOf(r, t), { it }, { false }, emptySet(), emptyList(), listOf(ruby)))
    }

    @Test fun rubyOnTurnTwo() {
        val ruby = card("Ruby, Daring Tracker", "Legendary Creature — Human Ranger", "{R}{G}", "Haste\n{T}: Add {R} or {G}.", commander = true)
        val blanks = card("Blank", "Sorcery", "{5}{G}", "You gain 1 life.", quantity = 64)
        val deck = listOf(ruby, forest, mountain, kessig.copy(quantity = 3), thicket.copy(quantity = 3), rockfall.copy(quantity = 2), rootbound.copy(quantity = 2), cinder.copy(quantity = 2), blanks)
        assertEquals(100, deck.sumOf { it.quantity })
        val odds = ManaOddsCalc.calculate(deck, "RG", games = 4000)!!.commanders.single().byTurn
        // 1.70 played Kessig Wolf Run and the like early and tapped lands on turn 2 (64% here).
        assertTrue("$odds", odds[1] >= 80)
    }
}
