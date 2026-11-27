package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.124: the win turn's reading fixes: Felidar Retreat's landfall "choose one", Planar Genesis, Emergency Eject's
 * Lander, Will of the Abzan, Saw in Half, Coiling Rebirth, landfall counters and mana, Ghostly Flicker's two creatures.
 */
class Release1124SimTest {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    private val plains = card("Plains", "Basic Land — Plains", "", "({T}: Add {W}.)", produced = "W", quantity = 19)
    private val island = card("Island", "Basic Land — Island", "", "({T}: Add {U}.)", produced = "U", quantity = 19)
    private val leader = card("Leader", "Legendary Creature — Human", "{8}{W}{U}", "", "0/1", commander = true)
    private fun blanks(n: Int) = card("Blank", "Sorcery", "{W}", "You gain 1 life.", quantity = n)
    private fun odds(cards: List<StatsCard>, games: Int = 2000): WinOdds =
        WinOddsCalc.calculate(listOf(leader, plains, island) + cards, "WU", emptyList(), games = games)!!
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, "WUBRG".toSet(), emptyList(), emptySet())

    private val retreat = card(
        "Felidar Retreat", "Enchantment", "{3}{W}",
        "Landfall — Whenever a land you control enters, choose one —\n" +
            "• Create a 2/2 white Cat Beast creature token.\n" +
            "• Put a +1/+1 counter on each creature you control. Those creatures gain vigilance until end of turn.",
    )

    /** The landfall trigger keeps its ability word, so only one of its modes may count (the token one). */
    @Test fun felidarRetreatTakesOneLandfallMode() {
        val r = classify(retreat)
        assertEquals(1, r.landfallTokens)
        assertEquals(2, r.landfallToken!!.power)
        assertEquals(2, r.landfallToken!!.toughness)
        assertNull(r.landfallCounters)
    }

    @Test fun planarGenesisLooksAtTheTopFour() {
        val genesis = classify(card(
            "Planar Genesis", "Instant", "{G}{U}",
            "Look at the top four cards of your library. You may put a land card from among them onto the battlefield tapped. " +
                "If you don't, put a card from among them into your hand. Put the rest on the bottom of your library in a random order.",
        ))
        assertEquals(4, genesis.onEnter.digLand)
        // Not a plain draw, and no lands looked for in the library.
        assertEquals(0, genesis.onEnter.draw)
        assertEquals(ManaOddsCalc.Kind.SPELL, genesis.mana.kind)
    }

    /** The Lander token's reminder text isn't the spell's own ramp. */
    @Test fun emergencyEjectsLanderIsNoRamp() {
        val eject = classify(card(
            "Emergency Eject", "Instant", "{2}{W}",
            "Destroy target nonland permanent. Its controller creates a Lander token. (It's an artifact with \"{2}, {T}, " +
                "Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\")",
        ))
        assertEquals(ManaOddsCalc.Kind.SPELL, eject.mana.kind)
        val cultivate = classify(card(
            "Cultivate", "Sorcery", "{2}{G}",
            "Search your library for up to two basic land cards, reveal those cards, put one onto the battlefield tapped and the other into your hand, then shuffle.",
        ))
        assertEquals(ManaOddsCalc.Kind.RAMP, cultivate.mana.kind)
    }

    @Test fun willOfTheAbzanDrainsEachOpponent() {
        val will = classify(card(
            "Will of the Abzan", "Sorcery", "{3}{B}",
            "Choose one. If you control a commander as you cast this spell, you may choose both instead.\n" +
                "• Any number of target opponents each sacrifice a creature with the greatest power among creatures that player controls and lose 3 life.\n" +
                "• Return target creature card from your graveyard to the battlefield.",
        ))
        assertEquals(3, will.drainOnce)
        assertEquals(1, will.onEnter.reanimate)
    }

    @Test fun sawInHalfCopiesOurCreature() {
        val saw = classify(card(
            "Saw in Half", "Instant", "{2}{B}",
            "Destroy target creature. If that creature dies this way, its controller creates two tokens that are copies of that creature, " +
                "except their power is half that creature's power and their toughness is half that creature's toughness. Round up each time.",
        ))
        assertTrue(saw.onEnter.sawInHalf)
        assertEquals(0, saw.onEnter.copies)
    }

    @Test fun coilingRebirthsGiftIsACopy() {
        val rebirth = classify(card(
            "Coiling Rebirth", "Sorcery", "{3}{B}{B}",
            "Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)\n" +
                "Return target creature card from your graveyard to the battlefield. Then if the gift was promised and that creature isn't " +
                "legendary, create a token that's a copy of that creature, except it's 1/1.",
        ))
        assertEquals(1, rebirth.onEnter.reanimate)
        assertTrue(rebirth.onEnter.reanimateCopy)
    }

    @Test fun landfallCountersGoOnOneCreature() {
        val amanda = classify(card(
            "Amanda Grayson", "Legendary Creature — Human Advisor", "{3}{G}",
            "Landfall — Whenever a land you control enters, put two +1/+1 counters on target creature you control.", "2/3",
        ))
        assertEquals(2, amanda.landfallTargetCounters)
        assertNull(amanda.landfallCounters)
    }

    @Test fun lotusCobraMakesManaOnLandfall() {
        val cobra = classify(card(
            "Lotus Cobra", "Creature — Snake", "{1}{G}",
            "Landfall — Whenever a land you control enters, add one mana of any color.", "2/1",
        ))
        assertEquals(1, cobra.landfallMana)
    }

    /** Felidar Retreat's Cats win games a deck of blanks can't. */
    @Test fun felidarRetreatsWinSoonerThanBlanks() {
        val with = odds(listOf(retreat.copy(quantity = 10), blanks(10)))
        val without = odds(listOf(blanks(20)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() > without.byTurn.sum())
    }

    /** A flicker that has to pick two different creatures still blinks our drain creature. */
    @Test fun ghostlyFlickerStillBlindsTheDrainCreature() {
        val merchant = card("Gray Merchant", "Creature — Zombie", "{1}{U}", "When this creature enters, each opponent loses 2 life.", "2/1", quantity = 10)
        val flicker = card(
            "Ghostly Flicker", "Instant", "{2}{U}",
            "Exile two target artifacts, creatures, and/or lands you control, then return those cards to the battlefield tapped.",
            quantity = 5,
        )
        val with = odds(listOf(merchant, flicker, blanks(15)))
        val without = odds(listOf(merchant, blanks(20)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() >= without.byTurn.sum())
    }

    private val broodship = card(
        "Exploration Broodship", "Artifact — Spacecraft", "{G}",
        "Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only " +
            "as a sorcery. It's an artifact creature at 8+.)\n" +
            "3+ | You may play an additional land on each of your turns.\n" +
            "8+ | Flying",
        "4/4",
    )

    private val enterprise = card(
        "Starship Enterprise", "Legendary Artifact — Spacecraft", "{5}",
        "Landfall — Whenever a land you control enters, put four charge counters on Starship Enterprise.\n" +
            "Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only " +
            "as a sorcery. It's an artifact creature at 16+.)\n" +
            "16+ | Flying",
        "4/6",
    )

    private val farragut = card(
        "U.S.S. Farragut, Bold Explorer", "Legendary Artifact — Spacecraft", "{6}",
        "Landfall — Whenever a land you control enters, you may put a creature card from your hand onto the battlefield. That " +
            "creature gains haste. At the beginning of the next end step, put it on the bottom of your library. When you do, draw a card.\n" +
            "Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only " +
            "as a sorcery. It's an artifact creature at 7+.)\n7+| Flying",
        "4/4",
    )

    /** Broodship's extra land drop waits for three charge counters, and it loses its "always on" reading. */
    @Test fun explorationBroodshipReadsItsStation() {
        val r = classify(broodship)
        assertEquals(3, r.stationLands!!)
        assertEquals(8, r.stationCreature!!)
        assertEquals(0, r.extraLands)
    }

    @Test fun starshipEnterpriseChargesOnLandfall() {
        val r = classify(enterprise)
        assertEquals(4, r.landfallCharge)
        assertEquals(16, r.stationCreature!!)
    }

    /** The threshold on its own line ("7+| Flying" or a line that is just "7+") counts too. */
    @Test fun farragutSneaksInACreature() {
        val r = classify(farragut)
        assertTrue(r.landfallSneak)
        assertEquals(7, r.stationCreature!!)
        assertTrue(r.flying)
        val split = classify(card(
            "U.S.S. Farragut, Bold Explorer", "Legendary Artifact — Spacecraft", "{6}",
            "Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. " +
                "It's an artifact creature at 7+.)\n7+\nFlying",
            "4/4",
        ))
        assertEquals(7, split.stationCreature!!)
        assertTrue(split.flying)
    }

    @Test fun mutavaultBecomesACreature() {
        val r = classify(vault)
        assertEquals(1, r.manland!!.first.generic)
        assertEquals(2, r.manland!!.second)
    }

    @Test fun horizonExplorerStillMakesLanders() {
        assertTrue(classify(explorer).abilities().any { it.made.containsKey("lander") })
    }

    private val explorer = card(
        "Horizon Explorer", "Creature — Insect Scout", "{2}{G}",
        "Lands you control enter untapped.\nWhenever you attack a player, create a Lander token. (It's an artifact with " +
            "\"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield " +
            "tapped, then shuffle.\")",
        "2/4", quantity = 10,
    )
    private val forest = card("Forest", "Basic Land — Forest", "", "({T}: Add {G}.)", produced = "G", quantity = 10)
    private val vault = card(
        "Mutavault", "Land", "",
        "{T}: Add {C}.\n{1}: This land becomes a 2/2 creature with all creature types until end of turn. It's still a land.",
        produced = "C", quantity = 10,
    )

    /** A deck of the leader and [cards], for the green cards the WU base can't cast. */
    private fun deck(cards: List<StatsCard>, identity: String = "WU", turns: Int = 15, games: Int = 2000): WinOdds =
        WinOddsCalc.calculate(listOf(leader) + cards, identity, emptyList(), games = games, turns = turns)!!

    /**
     * The same expensive creatures and the same thin mana: the deck whose Lander makers fetch lands brings the big
     * creatures down sooner than the one with blanks.
     */
    @Test fun landersAreUsed() {
        val threat = card("Big Green Beast", "Creature — Beast", "{6}{G}", "Trample", "8/8", quantity = 15)
        val makers = card("Lander Maker", "Creature — Insect", "{G}", "When this enters, create a Lander token.", "0/1", quantity = 15)
        val thin = forest.copy(quantity = 14)
        val more = deck(listOf(threat, makers, thin), "G", turns = 20, games = 3000)
        val less = deck(listOf(threat, blanks(15), thin), "G", turns = 20, games = 3000)
        assertTrue("${more.byTurn} vs ${less.byTurn}", more.byTurn.sum() > 0)
        assertTrue("${more.byTurn} vs ${less.byTurn}", more.byTurn.sum() > less.byTurn.sum())
    }

    /** Mutavaults attack, so ten of them win sooner than ten basic lands (which never win at all). */
    @Test fun mutavaultsWinSoonerThanBasicLands() {
        val with = deck(listOf(vault, plains.copy(quantity = 5), island.copy(quantity = 5)), turns = 22)
        val without = deck(listOf(plains.copy(quantity = 10), island.copy(quantity = 10)), turns = 22)
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn.sum() > without.byTurn.sum())
    }

    /** Charge counters from our creatures turn a station card into a creature; the game copes and wins. */
    @Test fun broodshipsStationAndWin() {
        val bears = card("Bear", "Creature — Bear", "{1}{W}", "", "2/2", quantity = 10)
        val with = deck(listOf(broodship.copy(quantity = 10), bears, forest, plains), "WG", turns = 20)
        assertTrue("${with.byTurn}", with.byTurn.sum() > 0)
    }
}
