package com.mtgtrader

import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinCounted
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.54: the turn of 4 in 10 games and the first win; the win turn reading Shrines and Gates ("for each Shrine you
 * control", "whenever another Shrine enters"), Maze's End, sacrifice decks (Baba Lysaga) and X spells.
 */
class Release154Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, com.mtgtrader.data.ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    private val swamp = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", quantity = 40, produced = "B")
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, setOf('W', 'U', 'B', 'R', 'G'), listOf(swamp), emptySet())
    private fun odds(turns: String) = WinOdds(10_000, turns.split(' ').map { it.toInt() }, List(15) { 0 }, emptyMap(), 0, WinCounted())

    // ---- The turns shown -------------------------------------------------------------------------

    @Test fun fourInTenAndFirstWin() {
        // Deep Clue Sea (2026-10-20): a combo wins 1% from turn 5, 4 in 10 by turn 12.
        val clue = odds("0 0 0 0 1 2 3 4 6 9 23 59 84 94 98")
        assertEquals(5, clue.firstWin)
        assertEquals(12, clue.fourInTen)
        assertEquals("T12", clue.label)
        // From the first win to 9 in 10 games, at most 10 turns.
        assertEquals(5..14, clue.window)
        // Taranika band: 40% exactly on turn 10 counts.
        assertEquals("T10", odds("0 0 0 0 0 0 0 2 15 40 64 81 90 95 98").label)
    }

    @Test fun windowIsEightToTenTurns() {
        // Sarge: first win turn 6, 9 in 10 by turn 10: eight turns from the first win.
        assertEquals(6..13, odds("0 0 0 0 0 2 23 60 86 95 98 99 99 99 99").window)
        // Baba: never 9 in 10, so it runs to the last turn.
        assertEquals(8..15, odds("0 0 0 0 0 0 0 0 1 3 9 20 32 46 59").window)
        // A turn-2 win and 9 in 10 by turn 14: ten turns, so the first win is still in view.
        assertEquals(2..11, odds("0 1 2 3 4 5 6 7 8 9 10 40 80 90 95").window)
        // Not 4 in 10 games by turn 15.
        val slow = odds("0 0 0 0 0 0 0 0 0 1 7 15 25 35 39")
        assertNull(slow.fourInTen)
        assertEquals("T15+", slow.label)
    }

    // ---- Reading the cards ---------------------------------------------------------------------

    private val honden = card("Honden of Infinite Rage", "Legendary Enchantment — Shrine", "{2}{R}",
        "At the beginning of your upkeep, Honden of Infinite Rage deals damage to any target equal to the number of Shrines you control.")
    private val fangs = card("Sanctum of Stone Fangs", "Legendary Enchantment — Shrine", "{1}{B}",
        "At the beginning of your first main phase, each opponent loses X life and you gain X life, where X is the number of Shrines you control.")
    private val origin = card("Go-Shintai of Life's Origin", "Legendary Enchantment Creature — Shrine", "{3}{G}",
        "{W}{U}{B}{R}{G}, {T}: Return target enchantment card from your graveyard to the battlefield.\n" +
            "Whenever Go-Shintai of Life's Origin or another nontoken Shrine you control enters, create a 1/1 colorless Shrine enchantment creature token.", "3/3")
    private val northern = card("Northern Air Temple", "Legendary Enchantment — Shrine", "{B}",
        "When Northern Air Temple enters, each opponent loses X life and you gain X life, where X is the number of Shrines you control.\n" +
            "Whenever another Shrine you control enters, each opponent loses 1 life and you gain 1 life.")

    @Test fun readsShrines() {
        val h = classify(honden).scaled.single()
        assertEquals(WinOddsCalc.Effect.DRAIN, h.what)
        assertEquals("shrine", h.per)
        assertTrue(h.turn)
        assertFalse(h.each)
        assertTrue(classify(fangs).scaled.single().each)
        val o = classify(origin).kindTriggers.single()
        assertEquals("shrine", o.kind)
        assertTrue(o.self)
        assertTrue(o.nontoken)
        // Its token is a Shrine too, so it counts for the others.
        assertEquals(setOf("Shrine"), o.token?.subtypes)
        assertTrue("Enchantment" in o.token!!.types)
        val n = classify(northern)
        assertFalse(n.scaled.single().turn)
        assertEquals(1, n.kindTriggers.single().drainEach)
        assertFalse(n.kindTriggers.single().self)
    }

    @Test fun readsGates() {
        val ram = classify(card("Gatebreaker Ram", "Creature — Sheep", "{2}{G}", "This creature gets +1/+1 for each Gate you control.\nAs long as you control two or more Gates, this creature has vigilance and trample.", "2/2"))
        assertEquals("gate", ram.powerPer)
        val sage = classify(card("Sage of the Maze", "Creature — Elf Wizard", "{2}{G}",
            "{T}: Add two mana in any combination of colors.\n{T}: Until end of turn, target land you control becomes an X/X Citizen creature with haste in addition to its other types, where X is twice the number of Gates you control. Activate only as a sorcery.", "1/1"))
        assertEquals(2, sage.animates?.factor)
        val maze = classify(card("Maze's End", "Land", "", "This land enters tapped.\n{T}: Add {C}.\n{3}, {T}, Return this land to its owner's hand: Search your library for a Gate card, put it onto the battlefield, then shuffle. If you control ten or more Gates with different names, you win the game.", produced = "C"))
        assertTrue(maze.mazeEnd)
        assertEquals(2, classify(card("Circuitous Route", "Sorcery", "{3}{G}", "Search your library for up to two basic land cards and/or Gate cards, put them onto the battlefield tapped, then shuffle.")).gateRamp)
        assertEquals(1, classify(card("Open the Gates", "Sorcery", "{G}", "Search your library for a basic land card or Gate card, reveal it, put it into your hand, then shuffle.")).gateToHand)
        assertEquals(2, classify(card("Gond Gate", "Land — Gate", "", "Gates you control enter untapped.\n{T}: Add {C}.", produced = "C")).untapsLands)
        assertEquals(1, classify(card("Amulet of Vigor", "Artifact", "{1}", "Whenever a permanent you control enters tapped, untap it.")).untapsLands)
    }

    @Test fun readsShrineFetchers() {
        val all = classify(card("Sanctum of All", "Legendary Enchantment — Shrine", "{W}{U}{B}{R}{G}",
            "At the beginning of your upkeep, you may search your library and/or graveyard for a Shrine card and put it onto the battlefield. If you search your library this way, shuffle."))
        assertEquals("shrine", all.fetchKind)
        assertTrue(all.fetchTurn)
        val heiBai = classify(card("Hei Bai, Forest Guardian", "Legendary Creature — Bear Spirit", "{3}{G}",
            "When Hei Bai enters, reveal cards from the top of your library until you reveal a Shrine card. You may put that card onto the battlefield. Then shuffle.", "4/4"))
        assertEquals("shrine", heiBai.fetchKind)
        assertFalse(heiBai.fetchTurn)
    }

    @Test fun desertTokensNeedDeserts() {
        val hour = classify(card("Hour of Promise", "Sorcery", "{4}{G}",
            "Search your library for up to two land cards, put them onto the battlefield tapped, then shuffle. Then if you control three or more Deserts, create two 2/2 black Zombie creature tokens."))
        assertEquals(0, hour.tokensOnce)
    }

    @Test fun readsSacrificeDecks() {
        assertTrue(classify(card("Instill Energy", "Enchantment — Aura", "{G}", "Enchant creature\nEnchanted creature can attack as though it had haste.\n{0}: Untap enchanted creature. Activate only during your turn and only once each turn.")).untapsOwn)
        val elixir = classify(card("Thousand-Year Elixir", "Artifact", "{3}", "You may activate abilities of creatures you control as though those creatures had haste.\n{1}, {T}: Untap target creature."))
        assertTrue(elixir.abilityHaste)
        assertTrue(elixir.untapsOther)
        assertTrue(classify(card("Patriar's Seal", "Artifact", "{3}", "{T}: Add one mana of any color.\n{1}, {T}: Untap target legendary creature you control.")).untapsOther)
        assertTrue(classify(card("Marvin, Murderous Mimic", "Legendary Artifact Creature — Toy", "{2}", "Marvin has all activated abilities of creatures you control that don't have the same name as this creature.", "2/2")).copiesAbilities)
        val marionette = classify(card("Marionette Master", "Creature — Human Artificer", "{4}{B}{B}",
            "Fabricate 3 (When this creature enters, put three +1/+1 counters on it or create three 1/1 colorless Servo artifact creature tokens.)\n" +
                "Whenever an artifact you control is put into a graveyard from the battlefield, target opponent loses life equal to this creature's power.", "1/1"))
        assertTrue(marionette.artifactDrain)
        assertEquals(3, marionette.fabricate)
        val baloth = classify(card("Baloth Prime", "Creature — Beast", "{3}{G}",
            "This creature enters tapped with six stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)\n" +
                "Whenever you sacrifice a land, create a tapped 4/4 green Beast creature token and untap this creature.\n{4}, Sacrifice a land: You gain 2 life.", "10/10"))
        assertEquals(6, baloth.stun)
        assertEquals(4, baloth.landSacToken)
        assertEquals(5, classify(card("Titania, Protector of Argoth", "Legendary Creature — Elemental", "{3}{G}{G}",
            "When Titania enters, return target land card from your graveyard to the battlefield.\nWhenever a land you control is put into a graveyard from the battlefield, create a 5/3 green Elemental creature token.", "5/3")).landLeavesToken)
        assertEquals(2, classify(card("Dunes of the Dead", "Land — Desert", "", "{T}: Add {C}.\nWhen this land is put into a graveyard from the battlefield, create a 2/2 black Zombie creature token.", produced = "C")).leavesToken)
        assertTrue(classify(card("Liquimetal Coating", "Artifact", "{2}", "{T}: Target permanent becomes an artifact in addition to its other types until end of turn.")).liquimetal)
        assertTrue(classify(card("Mazirek, Kraul Death Priest", "Legendary Creature — Insect Shaman", "{3}{B}{G}", "Flying\nWhenever a player sacrifices another permanent, put a +1/+1 counter on each creature you control.", "2/2")).sacCounters)
    }

    @Test fun readsXSpellsAndDoubleVision() {
        assertTrue(classify(card("Exsanguinate", "Sorcery", "{X}{B}{B}", "Each opponent loses X life. You gain life equal to the life lost this way.")).xEach)
        assertTrue(classify(card("Jaya's Immolating Inferno", "Legendary Sorcery", "{X}{R}{R}", "Jaya's Immolating Inferno deals X damage to each of up to three targets.")).xEach)
        assertFalse(classify(card("Fireball", "Sorcery", "{X}{R}", "This spell costs {1} more to cast for each target beyond the first.\nFireball deals X damage divided evenly, rounded down, among any number of targets.")).xEach)
        assertTrue(classify(card("Double Vision", "Enchantment", "{3}{R}{R}", "Whenever you cast your first instant or sorcery spell each turn, copy that spell. You may choose new targets for the copy.")).copiesFirst)
    }

    // ---- Playing ---------------------------------------------------------------------------------

    private val commander = card("Plain Commander", "Legendary Creature — Human", "{3}{B}", "", "0/4", commander = true)

    @Test fun shrinesCountEachOther() {
        // 59 drain Shrines: each one drains for all the Shrines out, so the deck wins long before turn 15.
        val shrines = WinOddsCalc.calculate(listOf(commander, swamp, fangs.copy(quantity = 59)), "B", emptyList(), games = 1000)!!
        assertNotNull(shrines.fourInTen)
        assertTrue("4 in 10 by ${shrines.fourInTen}", shrines.fourInTen!! <= 9)
        assertTrue(shrines.counted.counting > 0)
    }

    @Test fun mazesEndWins() {
        // 30 Gates with different names, Maze's End and a way to find it.
        val gates = (1..30).map { card("Gate $it", "Land — Gate", "", "This land enters tapped.\n{T}: Add {B}.", produced = "B") }
        val maze = card("Maze's End", "Land", "", "This land enters tapped.\n{T}: Add {C}.\n{3}, {T}, Return this land to its owner's hand: Search your library for a Gate card, put it onto the battlefield, then shuffle. If you control ten or more Gates with different names, you win the game.", produced = "C")
        val scrying = card("Sylvan Scrying", "Sorcery", "{1}{G}", "Search your library for a land card, reveal it, put it into your hand, then shuffle.", quantity = 4)
        val filler = card("Filler", "Sorcery", "{1}", "Scry 1.", quantity = 34)
        val swamps = swamp.copy(quantity = 30)
        val odds = WinOddsCalc.calculate(listOf(commander, swamps, maze, scrying, filler) + gates, "BG", emptyList(), games = 1000)!!
        assertTrue(odds.counted.mazeEnd)
        assertTrue("Maze's End wins ${odds.mazeShare}%", odds.mazeShare >= 10)
        // Not counted as a combo.
        assertEquals(0, odds.comboShare)
    }

    @Test fun xSpellsDrainWithTheManaLeft() {
        // Exsanguinate every turn with the lands out: X grows by one a turn until the opponents are gone.
        val exsanguinate = card("Exsanguinate", "Sorcery", "{X}{B}{B}", "Each opponent loses X life. You gain life equal to the life lost this way.", quantity = 59)
        val odds = WinOddsCalc.calculate(listOf(commander, swamp, exsanguinate), "B", emptyList(), games = 1000)!!
        assertNotNull("byTurn ${odds.byTurn}", odds.fourInTen)
        assertTrue("4 in 10 by ${odds.fourInTen}", odds.fourInTen!! <= 13)
        assertEquals(1, odds.counted.xSpells)
        // Double Vision copies the first one each turn: twice the drain, sooner.
        val vision = card("Double Vision", "Enchantment", "{3}{R}{R}", "Whenever you cast your first instant or sorcery spell each turn, copy that spell. You may choose new targets for the copy.", quantity = 10)
        val mountain = card("Mountain", "Basic Land — Mountain", "", "({T}: Add {R}.)", quantity = 20, produced = "R")
        val copied = WinOddsCalc.calculate(listOf(commander, swamp.copy(quantity = 20), mountain, exsanguinate.copy(quantity = 49), vision), "BR", emptyList(), games = 1000)!!
        val plain = WinOddsCalc.calculate(listOf(commander, swamp.copy(quantity = 20), mountain, exsanguinate.copy(quantity = 59)), "BR", emptyList(), games = 1000)!!
        assertTrue("copied ${copied.byTurn}, plain ${plain.byTurn}", copied.byTurn[10] > plain.byTurn[10])
    }

    @Test fun babaUsesHerAbilityMoreWithUntaps() {
        val baba = card("Baba Lysaga, Night Witch", "Legendary Creature — Human Warlock", "{1}{B}{G}",
            "{T}, Sacrifice up to three permanents: If there were three or more card types among the sacrificed permanents, each opponent loses 3 life, you gain 3 life, and you draw three cards.", "3/3", commander = true)
        // Each one is three card types on its own, so every use of Baba needs just one.
        val relic = card("Animated Relic", "Artifact Enchantment Creature — Construct", "{0}", "", "0/1", quantity = 39)
        val forest = card("Forest", "Basic Land — Forest", "", "({T}: Add {G}.)", quantity = 30, produced = "G")
        val swamps = swamp.copy(quantity = 30)
        val plain = WinOddsCalc.calculate(listOf(baba, forest, swamps, relic), "BG", emptyList(), games = 1000)!!
        val elixir = card("Thousand-Year Elixir", "Artifact", "{3}", "You may activate abilities of creatures you control as though those creatures had haste.\n{1}, {T}: Untap target creature.", quantity = 10)
        val untapped = WinOddsCalc.calculate(listOf(baba, forest, swamps, relic.copy(quantity = 29), elixir), "BG", emptyList(), games = 1000)!!
        // About 20 points apart over many games (1.55 checked it with 8,000), so the 1,000 here asks for 15.
        assertTrue("plain ${plain.byTurn}, untapped ${untapped.byTurn}", untapped.byTurn[14] > plain.byTurn[14] + 15)
        assertTrue(untapped.counted.sacrifice > 0)
    }
}
