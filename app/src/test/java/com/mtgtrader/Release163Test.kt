package com.mtgtrader

import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.63: battles (Siege enter effects), planeswalker ultimates, proliferate and poison in the win turn. */
class Release163Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "", loyalty: String? = null) =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost.substringBefore(" // ")).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt, loyalty)

    private val swamp = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", produced = "B", quantity = 20)
    private val forest = card("Forest", "Basic Land — Forest", "", "({T}: Add {G}.)", produced = "G", quantity = 18)
    private val leader = card("Leader", "Legendary Creature — Human", "{8}{B}{G}", "", "0/1", commander = true)
    private fun blanks(n: Int) = card("Blank", "Sorcery", "{B}", "You gain 1 life.", quantity = n)
    private fun odds(cards: List<StatsCard>, games: Int = 2000): WinOdds =
        WinOddsCalc.calculate(listOf(leader, swamp, forest) + cards, "BG", emptyList(), games = games)!!
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, setOf('B', 'G'), emptyList(), emptySet())

    private val zendikar = card(
        "Invasion of Zendikar // Awakened Skyclave", "Battle — Siege // Creature — Elemental", "{3}{G} // ",
        "(As a Siege enters, choose an opponent to protect it. You and others can attack it. When it's defeated, exile it, then cast it transformed.)\n" +
            "When this Siege enters, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle." +
            CardInfo.FACE_BREAK + "Vigilance, haste\nAs long as this creature is on the battlefield, it's a land in addition to its other types.\n{T}: Add one mana of any color.",
        produced = "BG", quantity = 10,
    )
    private val ikoria = card(
        "Invasion of Ikoria // Zilortha, Apex of Ikoria", "Battle — Siege // Legendary Creature — Dinosaur", "{X}{G}{G} // ",
        "(As a Siege enters, choose an opponent to protect it. You and others can attack it. When it's defeated, exile it, then cast it transformed.)\n" +
            "When this Siege enters, search your library and/or graveyard for a non-Human creature card with mana value X or less and put it onto the battlefield. If you search your library this way, shuffle." +
            CardInfo.FACE_BREAK + "Reach\nFor each non-Human creature you control, you may have that creature assign its combat damage as though it weren't blocked.",
        quantity = 10,
    )

    @Test fun invasionOfZendikarRampsAndIsNoManaCreature() {
        val mana = ManaOddsCalc.classify(zendikar, setOf('B', 'G'), emptyList())
        assertEquals(ManaOddsCalc.Kind.RAMP, mana.kind)
        assertEquals(2, mana.amount)
        assertTrue(mana.tapped)
        assertEquals(setOf("Battle"), classify(zendikar).types)
    }

    @Test fun invasionOfIkoriaBringsOutABigCreature() {
        val w = classify(ikoria)
        assertEquals("non-human", w.xCreature)
        assertTrue(w.xCreatureGraveyard)
        val giant = card("Giant", "Creature — Giant", "{6}{B}{G}", "", "12/12", quantity = 12)
        val with = odds(listOf(ikoria, giant, card("Bear", "Creature — Bear", "{1}{G}", "", "2/2", quantity = 10)))
        val without = odds(listOf(blanks(10), giant, card("Bear", "Creature — Bear", "{1}{G}", "", "2/2", quantity = 10)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() > without.byTurn.sum() + 20)
    }

    @Test fun ultimatesAreRead() {
        val elspeth = classify(card(
            "Elspeth, Sun's Champion", "Legendary Planeswalker — Elspeth", "{4}{W}{W}",
            "+1: Create three 1/1 white Soldier creature tokens.\n−3: Destroy all creatures with power 4 or greater.\n" +
                "−7: You get an emblem with \"Creatures you control get +2/+2 and have flying.\"",
            loyalty = "4",
        ))
        assertEquals(2, elspeth.loyalties.single { it.first == -7 }.second.emblemAnthem)
        val windgrace = classify(card(
            "Lord Windgrace", "Legendary Planeswalker — Windgrace", "{2}{B}{R}{G}",
            "+2: Discard a card, then draw a card. If a land card is discarded this way, draw an additional card.\n" +
                "−3: Return up to two target land cards from your graveyard to the battlefield.\n" +
                "−11: Destroy up to six target nonland permanents, then create six 2/2 green Cat Warrior creature tokens with forestwalk.",
            loyalty = "5",
        ))
        assertEquals(6, windgrace.loyalties.single { it.first == -11 }.second.tokens)
    }

    private val vraska = card(
        "Vraska, Betrayal's Sting", "Legendary Planeswalker — Vraska", "{4}{B}{B/P}",
        "Compleated ({B/P} can be paid with {B} or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)\n" +
            "0: You draw a card and lose 1 life. Proliferate.\n" +
            "−2: Target creature becomes a Treasure artifact with \"{T}, Sacrifice this artifact: Add one mana of any color\" and loses all other card types and abilities.\n" +
            "−9: If target player has fewer than nine poison counters, they get a number of poison counters equal to the difference.",
        loyalty = "6", quantity = 8,
    )
    private val affliction = card(
        "Grim Affliction", "Instant", "{2}{B}",
        "Put a -1/-1 counter on target creature, then proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)",
        quantity = 20,
    )

    @Test fun vraskaPoisonsAndProliferateFinishes() {
        val w = classify(vraska)
        val zero = w.loyalties.single { it.first == 0 }.second
        assertEquals(1, zero.proliferate)
        assertEquals(1, zero.draw)
        assertEquals(9, w.loyalties.single { it.first == -9 }.second.poisonTo)
        assertEquals(1, classify(affliction).onEnter.proliferate)
        // No creatures at all: only poison can win.
        val poisoned = odds(listOf(vraska, affliction))
        assertTrue("${poisoned.byTurn}", poisoned.byTurn.last() > 0)
        assertEquals(0, odds(listOf(vraska.copy(oracleText = vraska.oracleText!!.substringBefore("\n−9")), affliction)).byTurn.last())
    }

    @Test fun proliferateOnLandfallAndOnEnteringOrAttacking() {
        assertEquals(1, classify(card("Evolution Sage", "Creature — Elf Druid", "{2}{G}", "Landfall — Whenever a land you control enters, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)", "3/2")).landfallProliferate)
        val frank = classify(card(
            "Agent Frank Horrigan", "Legendary Creature — Mutant Warrior", "{5}{B}{G}",
            "Trample\nAgent Frank Horrigan has indestructible as long as it attacked this turn.\nWhenever Agent Frank Horrigan enters or attacks, proliferate twice. (To proliferate, choose any number of permanents and/or players, then give each another counter of each kind already there.)",
            "4/7",
        ))
        assertEquals(2, frank.onAttack.proliferate)
        assertEquals(2, frank.onEnter.proliferate)
    }
}
