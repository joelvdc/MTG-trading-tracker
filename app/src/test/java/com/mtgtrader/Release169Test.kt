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

/** 1.69: lands making more mana, ability costs, finishers, Storm of Souls, Sagas, Vehicles, toxic, Twenty-Toed Toad. */
class Release169Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    private val swamp = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", produced = "B", quantity = 19)
    private val mountain = card("Mountain", "Basic Land — Mountain", "", "({T}: Add {R}.)", produced = "R", quantity = 19)
    private val leader = card("Leader", "Legendary Creature — Human", "{8}{B}{R}", "", "0/1", commander = true)
    private fun blanks(n: Int) = card("Blank", "Sorcery", "{B}", "You gain 1 life.", quantity = n)
    private fun odds(cards: List<StatsCard>, games: Int = 2000): WinOdds =
        WinOddsCalc.calculate(listOf(leader, swamp, mountain) + cards, "BR", emptyList(), games = games)!!
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, "WUBRG".toSet(), emptyList(), emptySet())

    @Test fun landsMakeWhatTheyTapFor() {
        val lotus = classify(card("Lotus Field", "Land", "", "Hexproof\nThis land enters tapped.\nWhen this land enters, sacrifice two lands.\n{T}: Add three mana of any one color.", produced = "WUBRG"))
        assertEquals(3, lotus.mana.amount)
        assertEquals(2, lotus.mana.sacrificesLands)
        val skerry = classify(card("Saprazzan Skerry", "Land", "", "This land enters tapped with two depletion counters on it.\n{T}, Remove a depletion counter from this land: Add {U}{U}. If there are no depletion counters on this land, sacrifice it.", produced = "U"))
        assertEquals(2, skerry.mana.amount)
        assertEquals(2, skerry.mana.uses)
        assertEquals(2, classify(card("Ancient Tomb", "Land", "", "{T}: Add {C}{C}. This land deals 2 damage to you.", produced = "C")).mana.amount)
        // Mana with a condition counts as one.
        assertEquals(1, classify(card("Temple of the False God", "Land", "", "{T}: Add {C}{C}. Activate only if you control five or more lands.", produced = "C")).mana.amount)
        // Filter lands only fix colours.
        assertEquals(1, classify(card("Fetid Heath", "Land", "", "{T}: Add {C}.\n{W/B}, {T}: Add {W}{W}, {W}{B}, or {B}{B}.", produced = "WBC")).mana.amount)
        val crypt = classify(card("Crypt of Agadeem", "Land", "", "This land enters tapped.\n{T}: Add {B}.\n{2}, {T}: Add {B} for each black creature card in your graveyard.", produced = "B"))
        assertNotNull(crypt.manaPer)
        assertEquals(2, crypt.manaPerCost)
    }

    @Test fun depletionLandsCastTheCommanderSooner() {
        val big = card("Big Leader", "Legendary Creature — Giant", "{3}{B}", "", "4/4", commander = true)
        val bog = card("Peat Bog", "Land", "", "This land enters tapped with two depletion counters on it.\n{T}, Remove a depletion counter from this land: Add {B}{B}. If there are no depletion counters on this land, sacrifice it.", produced = "B", quantity = 10)
        val plain = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", produced = "B", quantity = 26)
        fun cast(lands: List<StatsCard>) = ManaOddsCalc.calculate(listOf(big) + lands + blanks(63), "B", games = 2000)!!.commanders.single().byTurn
        val with = cast(listOf(bog, plain))
        val without = cast(listOf(plain.copy(quantity = 36)))
        // Two mana a turn for two turns: sometimes a 4-drop on turn 3 (they come in tapped, so untapped lands go first).
        assertTrue("$with vs $without", with[2] > without[2])
    }

    @Test fun abilityCostsAreRead() {
        val cauldron = classify(card("Cauldron of Essence", "Artifact", "{1}{B}{G}", "Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life.\n{1}{B}{G}, {T}, Sacrifice a creature: Return target creature card from your graveyard to the battlefield. Activate only as a sorcery."))
        assertEquals("creature", cauldron.taps.single().sacrificeCost)
        assertEquals(1, cauldron.taps.single().reanimate)
        val proft = classify(card("Alquist Proft, Master Sleuth", "Legendary Creature — Human Detective", "{1}{W}{U}", "Vigilance\nWhen Alquist Proft enters, investigate.\n{X}{W}{U}{U}, {T}, Sacrifice a Clue: You draw X cards and gain X life.", "3/3"))
        assertEquals("clue", proft.taps.single().sacrificeCost)
        assertTrue(proft.taps.single().drawX)
        val dissident = classify(card("Dawnhand Dissident", "Creature — Elf Warlock", "{B}", "{T}, Blight 1: Surveil 1.\n{T}, Blight 2: Exile target card from a graveyard.", "1/2"))
        assertEquals(1, dissident.taps.first().blightCost)
        val fain = classify(card("Fain, the Broker", "Legendary Creature — Human Warlock", "{2}{B}", "{T}, Sacrifice a creature: Put two +1/+1 counters on target creature.\n{T}, Remove a counter from a creature you control: Create a Treasure token.\n{T}, Sacrifice an artifact: Create a 2/1 white and black Inkling creature token with flying.\n{3}{B}: Untap Fain.", "3/3"))
        assertEquals(3, fain.taps.size)
        assertEquals(1, fain.taps[1].counterCost)
        assertEquals("artifact", fain.taps[2].sacrificeCost)
    }

    @Test fun finishersAreRead() {
        val akroma = classify(card("Akroma's Will", "Instant", "{3}{W}", "Choose one. If you control a commander as you cast this spell, you may choose both instead.\n• Creatures you control gain flying, vigilance, and double strike until end of turn.\n• Creatures you control gain lifelink, indestructible, and protection from each color until end of turn."))
        assertTrue(akroma.teamDouble)
        assertTrue(akroma.pumpSpell)
        val jeska = classify(card("Jeska's Will", "Sorcery", "{2}{R}", "Choose one. If you control a commander as you cast this spell, you may choose both instead.\n• Add {R} for each card in target opponent's hand.\n• Exile the top three cards of your library. You may play them this turn."))
        assertEquals(5, jeska.ritual.single().second)
        assertEquals(2, jeska.drawOnce)
        val immolation = classify(card("Soul Immolation", "Sorcery", "{3}{R}{R}", "As an additional cost to cast this spell, blight X. X can't be greater than the greatest toughness among creatures you control. (Put X -1/-1 counters on a creature you control.)\nSoul Immolation deals X damage to each opponent and each creature they control."))
        assertTrue(immolation.blightX)
        assertEquals(0, immolation.onEnter.minusOwn)
        val mizzix = classify(card("Mizzix's Mastery", "Sorcery", "{3}{R}", "Exile target card that's an instant or sorcery from your graveyard. For each card exiled this way, copy it, and you may cast the copy without paying its mana cost. Exile Mizzix's Mastery.\nOverload {5}{R}{R}{R} (You may cast this spell for its overload cost. If you do, change \"target\" in its text to \"each.\")"))
        assertTrue(mizzix.mizzix)
        assertEquals(8, mizzix.overload!!.manaValue)
        val storm = classify(card("Storm of Souls", "Sorcery", "{4}{W}{W}", "Return all creature cards from your graveyard to the battlefield. Each of them is a 1/1 Spirit with flying in addition to its other types. Exile Storm of Souls."))
        assertTrue(storm.onEnter.reanimateAsOne)
    }

    @Test fun soulImmolationWithBigToughness() {
        val wall = card("Wall", "Creature — Wall", "{2}{B}", "Defender", "1/12", quantity = 15)
        val immolation = card("Soul Immolation", "Sorcery", "{3}{R}{R}", "As an additional cost to cast this spell, blight X. X can't be greater than the greatest toughness among creatures you control.\nSoul Immolation deals X damage to each opponent and each creature they control.", quantity = 15)
        val with = odds(listOf(wall, immolation))
        val without = odds(listOf(wall, blanks(15)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() > without.byTurn.sum() + 50)
    }

    @Test fun sagaChapters() {
        val carnage = classify(card("Maximum Carnage", "Enchantment — Saga", "{4}{R}", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — Until your next turn, each creature attacks each combat if able and attacks a player other than you if able.\nII — Add {R}{R}{R}.\nIII — This Saga deals 5 damage to each opponent."))
        assertEquals(3, carnage.chapters.size)
        assertEquals(3, carnage.chapters[1].addMana.single().second)
        assertEquals(5, carnage.chapters[2].drainEach)
        val urza = classify(card("Urza's Saga", "Enchantment Land — Urza's Saga", "", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — This Saga gains \"{T}: Add {C}.\"\nII — This Saga gains \"{2}, {T}: Create a 0/0 colorless Construct artifact creature token with 'This token gets +1/+1 for each artifact you control.'\"\nIII — Search your library for an artifact card with mana cost {0} or {1}, put it onto the battlefield, then shuffle.", produced = "C"))
        assertTrue(urza.land)
        assertTrue(urza.chapters[1].granted)
        assertEquals(1, urza.chapters[1].tokens)
        assertTrue(urza.chapters[2].cheapArtifact)
        val sagas = card("Maximum Carnage", "Enchantment — Saga", "{4}{R}", "(Sacrifice after III.)\nI — Until your next turn, each creature attacks each combat if able.\nII — Add {R}{R}{R}.\nIII — This Saga deals 5 damage to each opponent.", quantity = 15)
        val with = odds(listOf(sagas, blanks(15)))
        assertTrue("${with.byTurn}", with.byTurn.last() > 0)
    }

    @Test fun vehiclesAttackWhenCrewed() {
        val shredder = card("Hedge Shredder", "Artifact — Vehicle", "{2}{B}", "Whenever this Vehicle attacks, you may mill two cards.\nCrew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)", "5/5", quantity = 10)
        assertEquals(1, classify(shredder).crew)
        val pilot = card("Pilot", "Creature — Human", "{B}", "", "1/1", quantity = 10)
        val with = odds(listOf(shredder, pilot, blanks(10)))
        val without = odds(listOf(blanks(10), pilot, blanks(10)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() > without.byTurn.sum() + 50)
    }

    @Test fun toxicAndTheToad() {
        val hive = classify(card("Skrelv's Hive", "Enchantment", "{1}{W}", "At the beginning of your upkeep, you lose 1 life and create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and \"This token can't block.\""))
        assertEquals(1, hive.token!!.toxic)
        assertEquals(1, classify(card("Skrelv, Defector Mite", "Legendary Artifact Creature — Phyrexian Mite", "{W}", "Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)\nSkrelv can't block.", "1/1")).toxic)
        val communion = classify(card("Necrogen Communion", "Enchantment — Aura", "{1}{B}", "Enchant creature you control\nEnchanted creature has toxic 2. (Players dealt combat damage by it also get two poison counters.)\nWhen enchanted creature dies, return that card to the battlefield under your control."))
        assertEquals(2, communion.gearToxic)
        assertTrue(communion.gearBonus)
        val toad = classify(card("Twenty-Toed Toad", "Creature — Frog Wizard", "{3}{U}", "Your maximum hand size is twenty.\nWhenever you attack with two or more creatures, put a +1/+1 counter on this creature and draw a card.\nWhenever this creature attacks, you win the game if there are twenty or more counters on it or you have twenty or more cards in hand.", "3/3"))
        assertTrue(toad.toadWin)
        // Toxic knocks players out sooner than their life alone.
        val mites = card("Mite", "Artifact Creature — Phyrexian Mite", "{B}", "Toxic 3", "1/1", quantity = 20)
        val plain = card("Mite", "Artifact Creature — Phyrexian Mite", "{B}", "", "1/1", quantity = 20)
        assertTrue(odds(listOf(mites)).byTurn.sum() > odds(listOf(plain)).byTurn.sum() + 50)
        assertFalse(classify(plain).toxic > 0)
    }
}
