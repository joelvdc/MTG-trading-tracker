package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.64: deaths and sacrifice outlets, -1/-1 counters, Animate Dead, amass, Mana Geyser, Sunbird's Invocation, Approach. */
class Release164Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    private val swamp = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", produced = "B", quantity = 20)
    private val forest = card("Forest", "Basic Land — Forest", "", "({T}: Add {G}.)", produced = "G", quantity = 18)
    private val leader = card("Leader", "Legendary Creature — Human", "{8}{B}{G}", "", "0/1", commander = true)
    private fun blanks(n: Int) = card("Blank", "Sorcery", "{B}", "You gain 1 life.", quantity = n)
    private fun odds(cards: List<StatsCard>, games: Int = 2000): WinOdds =
        WinOddsCalc.calculate(listOf(leader, swamp, forest) + cards, "BG", emptyList(), games = games)!!
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, setOf('B', 'G'), emptyList(), emptySet())

    private val zombies = card("Zombie Maker", "Enchantment", "{1}{B}", "At the beginning of your upkeep, create a 1/1 black Zombie creature token.", quantity = 15)

    @Test fun deathTriggersAreRead() {
        val meathook = classify(card(
            "The Meathook Massacre", "Legendary Enchantment", "{X}{B}{B}",
            "When The Meathook Massacre enters, each creature gets -X/-X until end of turn.\nWhenever a creature you control dies, each opponent loses 1 life.\n" +
                "Whenever a creature an opponent controls dies, you gain 1 life.",
        ))
        val d = meathook.deathDrains.single()
        assertTrue(d.each)
        assertEquals(1, d.amount)
        val artist = classify(card("Blood Artist", "Creature — Vampire", "{1}{B}", "Whenever this creature or another creature dies, target player loses 1 life and you gain 1 life.", "0/1"))
        assertTrue(artist.deathDrains.single().self)
        assertFalse(artist.deathDrains.single().each)
        // "Whenever … dies, draw a card" was a card every turn before 1.64.
        val reaper = classify(card("Midnight Reaper", "Creature — Zombie Knight", "{2}{B}", "Whenever a nontoken creature you control dies, this creature deals 1 damage to you and you draw a card.", "3/2"))
        assertEquals(0, reaper.drawTurn)
        assertEquals(1, reaper.deathDrains.single().draw)
        val belcher = classify(card("Plague Belcher", "Creature — Zombie Beast", "{2}{B}", "Menace\nWhen this creature enters, put two -1/-1 counters on target creature you control.\nWhenever another Zombie you control dies, each opponent loses 1 life.", "5/4"))
        assertEquals("zombie", belcher.deathDrains.single().kind)
        assertEquals(2, belcher.onEnter.minusOwn)
        // An attacking creature dying (Kardur) doesn't happen here.
        assertTrue(classify(card("Kardur", "Legendary Creature — Demon", "{2}{B}{R}", "Whenever an attacking creature dies, each opponent loses 1 life and you gain 1 life.", "4/3")).deathDrains.isEmpty())
    }

    @Test fun sacrificeOutletsTurnDeathsIntoDamage() {
        val altar = card("Ashnod's Altar", "Artifact", "{3}", "Sacrifice a creature: Add {C}{C}.", quantity = 10)
        assertNotNull(classify(altar).freeSac)
        val feeder = classify(card("Carrion Feeder", "Creature — Zombie", "{B}", "This creature can't block.\nSacrifice a creature: Put a +1/+1 counter on this creature.", "1/1"))
        assertEquals(1, feeder.freeSac!!.selfCounters)
        val massacre = card("Massacre", "Enchantment", "{2}{B}", "Whenever a creature you control dies, each opponent loses 1 life.", quantity = 10)
        val with = odds(listOf(zombies, altar, massacre))
        val without = odds(listOf(zombies, blanks(10), massacre))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() > without.byTurn.sum() + 20)
    }

    private val nest = card("Nest of Scarabs", "Enchantment", "{2}{B}", "Whenever you put one or more -1/-1 counters on a creature, create that many 1/1 black Insect creature tokens.", quantity = 10)

    @Test fun minusCountersAndTheirPayoffs() {
        assertNotNull(classify(nest).minusTokens)
        val ool = classify(card(
            "Auntie Ool, Cursewretch", "Legendary Creature — Goblin Warlock", "{2}{B}{G}",
            "Ward—Blight 2. (To blight 2, a player puts two -1/-1 counters on a creature they control.)\n" +
                "Whenever one or more -1/-1 counters are put on a creature, draw a card if you control that creature. If you don't control it, its controller loses 1 life.",
            "4/4",
        ))
        assertTrue(ool.minusDraw)
        assertEquals(0, ool.drawTurn)
        assertEquals(0, ool.onEnter.minusOwn)
        val spider = classify(card("Obelisk Spider", "Creature — Spider", "{1}{B}{G}", "Reach\nWhenever this creature deals combat damage to a creature, put a -1/-1 counter on that creature.\nWhenever you put one or more -1/-1 counters on a creature, each opponent loses 1 life and you gain 1 life.", "1/4"))
        assertEquals(1, spider.minusDrain)
        assertTrue(spider.counterPayoff)
        assertEquals(3, classify(card("Skinrender", "Creature — Phyrexian Zombie", "{2}{B}{B}", "When this creature enters, put three -1/-1 counters on target creature.", "3/3")).onEnter.minusTarget)
        assertEquals(1, classify(card("Harbinger of Night", "Creature — Spirit", "{2}{B}{B}", "At the beginning of your upkeep, put a -1/-1 counter on each creature.", "2/3")).eachTurn.minusEach)
        // A sweeper isn't cast.
        assertEquals(0, classify(card("Black Sun's Zenith", "Sorcery", "{X}{B}{B}", "Put X -1/-1 counters on each creature. Shuffle Black Sun's Zenith into its owner's library.")).onEnter.minusEach)
        assertTrue(classify(card("Blowfly Infestation", "Enchantment", "{2}{B}", "Whenever a creature dies, if it had a -1/-1 counter on it, put a -1/-1 counter on target creature.")).minusDeathSpread)
        // Blight each turn makes Insects with Nest of Scarabs out.
        val blighter = card("Gnarlbark", "Creature — Treefolk", "{1}{B}", "At the beginning of your end step, blight 1. (Put a -1/-1 counter on a creature you control.)", "1/5", quantity = 10)
        val with = odds(listOf(nest, blighter, blanks(10)))
        val without = odds(listOf(blanks(10), blighter, blanks(10)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() > without.byTurn.sum() + 20)
    }

    @Test fun allWillBeOneBurnsForCounters() {
        val awbo = card(
            "All Will Be One", "Enchantment", "{3}{B}{B}",
            "Whenever you put one or more counters on a permanent or player, this enchantment deals that much damage to target opponent, creature an opponent controls, or planeswalker an opponent controls.",
            quantity = 10,
        )
        assertTrue(classify(awbo).counterBurn)
        val grower = card("Grower", "Creature — Elf", "{1}{G}", "At the beginning of your upkeep, put a +1/+1 counter on each creature you control.", "1/1", quantity = 12)
        val with = odds(listOf(awbo, grower))
        val without = odds(listOf(blanks(10), grower))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() > without.byTurn.sum())
    }

    @Test fun animateDeadIsReanimationNotGear() {
        val animate = classify(card(
            "Animate Dead", "Enchantment — Aura", "{1}{B}",
            "Enchant creature card in a graveyard\nWhen this Aura enters, if it's on the battlefield, it loses \"enchant creature card in a graveyard\" and gains \"enchant creature put onto the battlefield with this Aura.\" Return enchanted creature card to the battlefield under your control and attach this Aura to it. When this Aura leaves the battlefield, that creature's controller sacrifices it.\nEnchanted creature gets -1/-0.",
        ))
        assertFalse(animate.aura)
        assertEquals(1, animate.onEnter.reanimate)
    }

    @Test fun tapAbilitiesCountPermanents() {
        val shrine = classify(card("Shrine Drainer", "Enchantment — Shrine", "{2}{B}", "{2}, {T}: Each opponent loses 1 life for each Shrine you control."))
        assertEquals("shrine", shrine.taps.single().drainPer)
        assertEquals(1, shrine.taps.single().drainEach)
    }

    @Test fun amassMakesAnArmyThatGrows() {
        val invasion = card(
            "Dreadhorde Invasion", "Enchantment", "{1}{B}",
            "At the beginning of your upkeep, you lose 1 life and amass Zombies 1. (Put a +1/+1 counter on an Army you control. It's also a Zombie. If you don't control an Army, create a 0/0 black Zombie Army creature token first.)\nWhenever a Zombie token you control with power 6 or greater attacks, it gains lifelink until end of turn.",
            quantity = 10,
        )
        assertEquals(1, classify(invasion).eachTurn.amass)
        // Nothing else in the deck deals damage.
        assertTrue(odds(listOf(invasion, blanks(20))).byTurn.last() > 0)
    }

    @Test fun approachOfTheSecondSunWinsTheSecondTime() {
        val approach = card(
            "Approach of the Second Sun", "Sorcery", "{6}{W}",
            "If this spell was cast from your hand and you've cast another spell named Approach of the Second Sun this game, you win the game. Otherwise, put Approach of the Second Sun into its owner's library seventh from the top and you gain 7 life.",
            quantity = 2,
        )
        assertTrue(classify(approach).approach)
        val plains = card("Plains", "Basic Land — Plains", "", "({T}: Add {W}.)", produced = "W", quantity = 38)
        val white = card("Leader", "Legendary Creature — Human", "{9}{W}", "", "0/1", commander = true)
        val draw = card("Divination", "Sorcery", "{2}{W}", "Draw two cards.", quantity = 20)
        val odds = WinOddsCalc.calculate(listOf(white, plains, approach, draw, blanks(39)), "W", emptyList(), games = 2000)!!
        assertTrue("${odds.byTurn}", odds.byTurn.last() > 0)
    }

    @Test fun manaGeyserAndSunbirdAreRead() {
        val geyser = classify(card("Mana Geyser", "Sorcery", "{3}{R}{R}", "Add {R} for each tapped land your opponents control."))
        assertTrue(geyser.ritualOppLands)
        assertEquals(setOf('R'), geyser.ritual.single().first)
        assertTrue(classify(card(
            "Sunbird's Invocation", "Enchantment", "{5}{R}",
            "Whenever you cast a spell from your hand, reveal the top X cards of your library, where X is that spell's mana value. You may cast a spell with mana value X or less from among cards revealed this way without paying its mana cost. Put the rest on the bottom of your library in a random order.",
        )).sunbird)
    }
}
