package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.55: the win turn reads ramp, Treasures, tapping instead of attacking, team and tribal pumps, damage in longer
 * sentences, the graveyard (looting, reanimation, "cards leave your graveyard"), copies and flicker, polymorph, Sneak
 * Attack, planeswalkers, Clues and the second card drawn each turn, +1/+1 counters, and the opponents' draw steps.
 * The cards are the owner's (Ruby, Haunted Ganax, Vohar Danse, Tawnos' Army, Teval, Quintorius, Poly Jace, Deep Clue
 * Sea, Turtle Power, The Council of Four, Cloud, Zombies), with Scryfall's rules text.
 */
class Release155Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "", loyalty: String? = null) =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt, loyalty)

    private val forest = card("Forest", "Basic Land — Forest", "", "({T}: Add {G}.)", produced = "G")
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, setOf('W', 'U', 'B', 'R', 'G'), listOf(forest), emptySet())

    @Test fun rampCountsEveryLand() {
        val vegetation = classify(card("Explosive Vegetation", "Sorcery", "{3}{G}", "Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle."))
        assertEquals(ManaOddsCalc.Kind.RAMP, vegetation.mana.kind)
        assertEquals(2, vegetation.mana.amount)
        val cultivate = classify(card("Cultivate", "Sorcery", "{2}{G}", "Search your library for up to two basic land cards, reveal those cards, put one onto the battlefield tapped and the other into your hand, then shuffle."))
        assertEquals(1, cultivate.mana.amount)
        assertTrue(cultivate.mana.toHand)
        val ruby = classify(card("Ruby, Daring Tracker", "Legendary Creature — Human Scout", "{R}{G}",
            "Haste (This creature can attack and {T} as soon as it comes under your control.)\nWhenever Ruby attacks while you control a creature with power 4 or greater, Ruby gets +2/+2 until end of turn.\n{T}: Add {R} or {G}.", "1/2", produced = "GR"))
        assertTrue(ruby.mana.haste)
        assertEquals(2, ruby.onAttack.selfPump)
        assertEquals(4, ruby.onAttack.needsPower)
        val rivaz = classify(card("Rivaz of the Claw", "Legendary Creature — Lizard Warlock", "{1}{B}{R}",
            "Menace\n{T}: Add two mana in any combination of colors. Spend this mana only to cast Dragon creature spells.\nOnce during each of your turns, you may cast a Dragon creature spell from your graveyard.", "3/3", produced = "BGRUW"))
        assertEquals(2, rivaz.mana.amount)
        assertEquals("dragon", rivaz.castFromGraveyard)
        // A Treasure's reminder text isn't a mana ability of the card's own.
        val lotho = classify(card("Lotho, Corrupt Shirriff", "Legendary Creature — Halfling Rogue", "{W}{B}",
            "Whenever a player casts their second spell each turn, you lose 1 life and create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "2/1", produced = "BGRUW"))
        assertEquals(ManaOddsCalc.Kind.SPELL, lotho.mana.kind)
        val gilanra = classify(card("Gilanra, Caller of Wirewood", "Legendary Creature — Elf Druid", "{2}{G}",
            "{T}: Add {G}. When you spend this mana to cast a spell with mana value 6 or greater, draw a card.\nPartner", "1/2", produced = "G"))
        assertEquals(6, gilanra.manaDrawAt)
    }

    @Test fun commanderOddsCountBothLandsOfARampSpell() {
        // Seven-mana commander: with two-land ramp it's cast sooner than with one-land ramp.
        val commander = card("Big", "Legendary Creature — Beast", "{5}{G}{G}", "", "7/7", commander = true)
        val two = card("Two", "Sorcery", "{2}{G}", "Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.", quantity = 10)
        val one = card("One", "Sorcery", "{2}{G}", "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", quantity = 10)
        val filler = card("Filler", "Instant", "{U}", quantity = 50)
        val lands = forest.copy(quantity = 38)
        val withTwo = ManaOddsCalc.calculate(listOf(commander, two, filler, lands), "G", games = 2000)!!.commanders.single().byTurn
        val withOne = ManaOddsCalc.calculate(listOf(commander, one, filler, lands), "G", games = 2000)!!.commanders.single().byTurn
        assertTrue("$withTwo vs $withOne", withTwo[4] > withOne[4] + 5)
    }

    @Test fun readsGanaxsTreasuresAndHauntedOne() {
        val ganax = classify(card("Ganax, Astral Hunter", "Legendary Creature — Dragon", "{4}{R}",
            "Flying\nWhenever Ganax or another Dragon you control enters, create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")\nChoose a Background", "3/4", produced = "BGRUW"))
        assertEquals(1, ganax.kindTriggers.single().treasures)
        assertTrue(ganax.kindTriggers.single().self)
        val haunted = classify(card("Haunted One", "Legendary Enchantment — Background", "{2}{B}",
            "Commander creatures you own have \"Whenever this creature becomes tapped, it and other creatures you control that share a creature type with it each get +2/+0 and gain undying until end of turn.\""))
        assertEquals(2, haunted.background)
        val tempest = classify(card("Dragon Tempest", "Enchantment", "{1}{R}",
            "Whenever a creature you control with flying enters, it gains haste until end of turn.\nWhenever a Dragon you control enters, it deals X damage to any target, where X is the number of Dragons you control."))
        assertEquals("dragon", tempest.kindTriggers.single().perOne)
        assertEquals("flying", tempest.hasteFor)
        val goldspan = classify(card("Goldspan Dragon", "Creature — Dragon", "{3}{R}{R}",
            "Flying, haste\nWhenever this creature attacks or becomes the target of a spell, create a Treasure token.\nTreasures you control have \"{T}, Sacrifice this artifact: Add two mana of any one color.\"", "4/4"))
        assertTrue(goldspan.treasureDouble)
        assertEquals(1, goldspan.onAttack.treasures)
        assertEquals(listOf("dragon" to 2), classify(card("Dragonspeaker Shaman", "Creature — Human Barbarian Shaman", "{1}{R}{R}", "Dragon spells you cast cost {2} less to cast.", "2/2")).reducers)
        val utvara = classify(card("Utvara Hellkite", "Creature — Dragon", "{6}{R}{R}", "Flying\nWhenever a Dragon you control attacks, create a 6/6 red Dragon creature token with flying.", "6/6"))
        assertEquals("dragon", utvara.kindAttack)
        assertEquals(6, utvara.onKindAttack.token?.power)
    }

    /** Treasures make mana: a 3-drop that makes two Treasures casts a 6-mana dragon on turn 4 instead of turn 6. */
    @Test fun treasuresAreMana() {
        val commander = card("Leader", "Legendary Creature — Human", "{G}", "", "0/1", commander = true)
        val maker = card("Maker", "Creature — Goblin", "{2}", "When this creature enters, create two Treasure tokens.", "0/1", quantity = 30)
        val plain = card("Plain", "Creature — Goblin", "{2}", "", "0/1", quantity = 30)
        val giant = card("Giant", "Creature — Giant", "{6}", "Haste", "30/30", quantity = 29)
        val lands = forest.copy(quantity = 40)
        val with = WinOddsCalc.calculate(listOf(commander, maker, giant, lands), "G", emptyList(), games = 1000)!!
        val without = WinOddsCalc.calculate(listOf(commander, plain, giant, lands), "G", emptyList(), games = 1000)!!
        assertTrue(with.byTurn[5] > without.byTurn[5] + 10)
    }

    @Test fun pumpsAndLords() {
        val hoof = classify(card("Craterhoof Behemoth", "Creature — Beast", "{5}{G}{G}{G}",
            "Haste\nWhen this creature enters, creatures you control gain trample and get +X/+X until end of turn, where X is the number of creatures you control.", "5/5"))
        assertTrue(hoof.overrunPerCreature)
        assertEquals(2, classify(card("End-Raze Forerunners", "Creature — Boar", "{5}{G}{G}{G}",
            "Vigilance, trample, haste\nWhen this creature enters, other creatures you control get +2/+2 and gain vigilance and trample until end of turn.", "7/7")).overrun)
        val quintorius = classify(card("Quintorius, Field Historian", "Legendary Creature — Elephant Cleric", "{3}{R}{W}",
            "Spirits you control get +1/+0.\nWhenever one or more cards leave your graveyard, create a 3/2 red and white Spirit creature token.", "2/4"))
        assertEquals(WinOddsCalc.Lord("spirit", 1, false), quintorius.lords.single())
        assertEquals(setOf("Spirit"), quintorius.leaveToken?.subtypes)
        val lathliss = classify(card("Lathliss, Dragon Queen", "Legendary Creature — Dragon", "{4}{R}{R}",
            "Flying\nWhenever another nontoken Dragon you control enters, create a 5/5 red Dragon creature token with flying.\n{1}{R}: Dragons you control get +1/+0 until end of turn.", "6/6"))
        assertEquals("dragon", lathliss.pumps.single().kind)
        assertEquals("nontoken", classify(card("Rhythm of the Wild", "Enchantment", "{1}{R}{G}",
            "Creature spells you control can't be countered.\nNontoken creatures you control have riot. (They enter with your choice of a +1/+1 counter or haste.)")).hasteFor)
    }

    /** Craterhoof's +X/+X wins a turn earlier than the same 5/5 without it. */
    @Test fun craterhoofPumpsTheTeam() {
        val commander = card("Leader", "Legendary Creature — Human", "{G}", "", "1/1", commander = true)
        val tokens = card("Tokens", "Sorcery", "{1}{G}", "Create three 1/1 green Saproling creature tokens.", quantity = 40)
        val hoof = card("Craterhoof Behemoth", "Creature — Beast", "{5}{G}{G}{G}",
            "Haste\nWhen this creature enters, creatures you control gain trample and get +X/+X until end of turn, where X is the number of creatures you control.", "5/5", quantity = 20)
        val plain = card("Beast", "Creature — Beast", "{5}{G}{G}{G}", "Haste", "5/5", quantity = 20)
        val lands = forest.copy(quantity = 39)
        val with = WinOddsCalc.calculate(listOf(commander, tokens, hoof, lands), "G", emptyList(), games = 500)!!
        val without = WinOddsCalc.calculate(listOf(commander, tokens, plain, lands), "G", emptyList(), games = 500)!!
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.usual!! < without.usual!!)
    }

    @Test fun damageInLongerSentences() {
        assertEquals(2, classify(card("Arbiter of Woe", "Creature — Demon", "{4}{B}{B}",
            "As an additional cost to cast this spell, sacrifice a creature.\nFlying\nWhen this creature enters, each opponent discards a card and loses 2 life. You draw a card and gain 2 life.", "5/4")).drainOnce)
        // "Bad Breath —" is an ability word.
        assertEquals(2, classify(card("Malboro", "Creature — Plant Horror", "{4}{B}{B}",
            "Bad Breath — When this creature enters, each opponent discards a card, loses 2 life, and exiles the top three cards of their library.", "4/4")).drainOnce)
        val archon = classify(card("Archon of Cruelty", "Creature — Archon", "{6}{B}{B}",
            "Flying\nWhenever this creature enters or attacks, target opponent sacrifices a creature or planeswalker of their choice, discards a card, and loses 3 life. You draw a card and gain 3 life.", "6/6"))
        assertEquals(3, archon.oneOnce)
        assertEquals(3, archon.onAttack.drainOne)
        assertEquals('B', classify(card("Gray Merchant of Asphodel", "Creature — Zombie", "{3}{B}{B}",
            "When this creature enters, each opponent loses X life, where X is your devotion to black. You gain life equal to the life lost this way.", "2/4")).devotion)
        assertEquals(WinOddsCalc.EnterTrigger(land = false, another = true, each = false, amount = -2), classify(card("Terror of the Peaks", "Creature — Dragon", "{3}{R}{R}",
            "Flying\nWhenever another creature you control enters, this creature deals damage equal to that creature's power to any target.", "5/4")).enterTriggers.single())
        val omnath = classify(card("Omnath, Locus of Rage", "Legendary Creature — Elemental", "{3}{R}{R}{G}{G}",
            "Landfall — Whenever a land you control enters, create a 5/5 red and green Elemental creature token.", "5/5"))
        assertEquals(1, omnath.landfallTokens)
        assertEquals(setOf("Elemental"), omnath.landfallToken?.subtypes)
    }

    @Test fun commandersThatTapForAbilities() {
        val vohar = classify(card("Vohar, Vodalian Desecrator", "Legendary Creature — Phyrexian Merfolk Wizard", "{U}{B}",
            "{T}: Draw a card, then discard a card. If you discarded an instant or sorcery card this way, each opponent loses 1 life and you gain 1 life.", "1/2"))
        val loot = vohar.taps.single()
        assertEquals(1, loot.lootDraw)
        assertEquals(1, loot.lootDiscard)
        assertEquals(1, loot.lootDrain)
        assertEquals(0, loot.drainEach)
        val tawnos = classify(card("Tawnos, Solemn Survivor", "Legendary Creature — Human Artificer", "{1}{U}",
            "{2}, {T}: Create a token that's a copy of up to one target artifact token you control. Mill two cards.\n" +
                "{1}{W}{U}{B}, {T}, Sacrifice two artifact tokens, Exile an artifact or creature card from your graveyard: Create a token that's a copy of the exiled card, except it's an artifact in addition to its other types. Activate only as a sorcery.", "1/3"))
        assertTrue(tawnos.taps[0].copyArtifactToken)
        assertEquals(2, tawnos.taps[0].mill)
        assertTrue(tawnos.taps[1].copyExiled)
        assertEquals(4, tawnos.taps[1].cost.manaValue)
    }

    /** A commander whose tap ability drains doesn't also attack: no commander damage. */
    @Test fun aTappingCommanderStaysBack() {
        val pinger = card("Pinger", "Legendary Creature — Human Wizard", "{G}", "{T}: Each opponent loses 1 life.", "3/3", commander = true)
        val lands = forest.copy(quantity = 99)
        val odds = WinOddsCalc.calculate(listOf(pinger, lands), "G", emptyList(), games = 50)!!
        assertNull(odds.damage[WinOddsCalc.COMMANDER])
    }

    @Test fun readsTheGraveyard() {
        val teval = classify(card("Teval, the Balanced Scale", "Legendary Creature — Spirit Dragon", "{1}{B}{G}{U}",
            "Flying\nWhenever Teval attacks, mill three cards. Then you may return a land card from your graveyard to the battlefield tapped.\nWhenever one or more cards leave your graveyard, create a 2/2 black Zombie Druid creature token.", "4/4"))
        assertEquals(3, teval.onAttack.mill)
        assertTrue(teval.onAttack.landBack)
        assertEquals(2, teval.leaveToken?.power)
        assertEquals(1, classify(card("Reanimate", "Sorcery", "{B}", "Put target creature card from a graveyard onto the battlefield under your control. You lose life equal to that card's mana value.")).onEnter.reanimate)
        val death = classify(card("Living Death", "Sorcery", "{3}{B}{B}",
            "Each player exiles all creature cards from their graveyard, then sacrifices all creatures they control, then puts all cards they exiled this way onto the battlefield.")).onEnter
        assertEquals(WinOddsCalc.ALL, death.reanimate)
        assertTrue(death.reanimateEverything)
        assertEquals("chosen", classify(card("Patriarch's Bidding", "Sorcery", "{3}{B}{B}",
            "Each player chooses a creature type. Each player returns all creature cards of a type chosen this way from their graveyard to the battlefield.")).onEnter.reanimateKind)
        assertEquals("nonlegendary", classify(card("Persist", "Sorcery", "{1}{B}",
            "Return target nonlegendary creature card from your graveyard to the battlefield with a -1/-1 counter on it.")).onEnter.reanimateKind)
        val varina = classify(card("Varina, Lich Queen", "Legendary Creature — Zombie Wizard", "{1}{W}{U}{B}",
            "Whenever you attack with one or more Zombies, draw that many cards, then discard that many cards. You gain that much life.\n{2}, Exile two cards from your graveyard: Create a tapped 2/2 black Zombie creature token.", "4/4"))
        assertTrue(varina.onYouAttack.thatMany)
        assertEquals("zombie", varina.onYouAttack.perKind)
        assertEquals(2, varina.activated.single().exileGraveyard)
    }

    /** A looting commander with Reanimate in the deck brings back a discarded fatty and wins sooner. */
    @Test fun reanimationBringsBackWhatWasDiscarded() {
        val looter = card("Looter", "Legendary Creature — Human Wizard", "{B}", "{T}: Draw a card, then discard a card.", "0/1", commander = true)
        val fatty = card("Fatty", "Creature — Horror", "{9}{B}{B}", "", "20/20", quantity = 20)
        val reanimate = card("Reanimate", "Sorcery", "{B}", "Put target creature card from a graveyard onto the battlefield under your control. You lose life equal to that card's mana value.", quantity = 20)
        val filler = card("Filler", "Sorcery", "{B}", "", quantity = 20)
        val swamps = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", quantity = 39, produced = "B")
        val with = WinOddsCalc.calculate(listOf(looter, fatty, reanimate, swamps), "B", emptyList(), games = 500)!!
        val without = WinOddsCalc.calculate(listOf(looter, fatty, filler, swamps), "B", emptyList(), games = 500)!!
        assertTrue(with.byTurn[7] > without.byTurn[7] + 20)
        assertTrue(with.counted.graveyard > 0)
    }

    @Test fun copiesFlickerAndCheating() {
        assertEquals(2, classify(card("Ghostly Flicker", "Instant", "{2}{U}",
            "Exile two target artifacts, creatures, and/or lands you control, then return those cards to the battlefield under your control.")).onEnter.flicker)
        assertEquals(2, classify(card("Replication Technique", "Sorcery", "{4}{U}",
            "Demonstrate (When you cast this spell, you may copy it. If you do, choose an opponent to also copy it.)\nCreate a token that's a copy of target permanent you control.")).onEnter.copies)
        // Copies of an opponent's permanent aren't counted.
        assertEquals(0, classify(card("Venser, Fervent Forger", "Legendary Creature — Human Sorcerer", "{4}{R}{R}",
            "Flash\nWhen Venser enters, choose one —\n• Copy target instant or sorcery spell an opponent controls twice.\n• Create two tokens that are copies of target permanent an opponent controls. They gain haste.", "5/3")).onEnter.copies)
        assertNotNull(classify(card("Sneak Attack", "Enchantment", "{3}{R}",
            "{R}: You may put a creature card from your hand onto the battlefield. That creature gains haste. Sacrifice the creature at the beginning of the next end step.")).sneakCost)
        val secure = classify(card("Secure the Wastes", "Instant", "{X}{W}", "Create X 1/1 white Warrior creature tokens."))
        assertEquals(1, secure.xTokens)
    }

    @Test fun planeswalkersUseLoyaltyAbilities() {
        val jace = classify(card("Jace, Multiverse Architect", "Legendary Planeswalker — Jace", "{1}{W}{U}{B}{R}",
            "+1: Draw two cards, then put a card from your hand on the bottom of your library.\n" +
                "−3: Exile another target planeswalker or creature you control. Reveal cards from the top of your library until you reveal a creature or planeswalker card. Put that card onto the battlefield and the rest on the bottom of your library in a random order.",
            loyalty = "4"))
        assertEquals(4, jace.loyalty)
        assertEquals(listOf(1, -3), jace.loyalties.map { it.first })
        assertEquals(2, jace.loyalties[0].second.draw)
        assertEquals(1, jace.loyalties[1].second.polymorph)
        val quintorius = classify(card("Quintorius, History Chaser", "Legendary Planeswalker — Quintorius", "{2}{R}{W}",
            "Whenever one or more cards leave your graveyard, create a 3/2 red and white Spirit creature token.\n+1: You may discard a card. If you do, draw two cards, then mill a card.\n−4: Spirits you control gain double strike and vigilance until end of turn.",
            loyalty = "5"))
        assertEquals(5, quintorius.loyalty)
        assertEquals("spirit", quintorius.loyalties[1].second.doubleStrikeFor)
        assertNotNull(quintorius.leaveToken)
        // Before 1.55 a planeswalker commander was never cast: now it is.
        val pw = card("Quintorius, History Chaser", "Legendary Planeswalker — Quintorius", "{2}{R}{W}",
            "+1: Create a 3/2 red and white Spirit creature token.", commander = true, loyalty = "5")
        val odds = WinOddsCalc.calculate(listOf(pw, card("Plateau", "Land — Mountain Plains", "", "({T}: Add {R} or {W}.)", quantity = 99, produced = "RW")), "RW", emptyList(), games = 200)!!
        assertEquals(1, odds.counted.planeswalkers)
        assertNotNull(odds.usual)
    }

    @Test fun cluesAndTheSecondCardDrawn() {
        val morska = classify(card("Morska, Undersea Sleuth", "Legendary Creature — Vedalken Fish Detective", "{G}{W}{U}",
            "You have no maximum hand size.\nAt the beginning of your upkeep, investigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")\nWhenever you draw your second card each turn, put two +1/+1 counters on Morska.", "2/3"))
        assertEquals(mapOf("clue" to 1), morska.madeTurn)
        assertEquals(2, morska.onSecondDraw.selfCounters)
        assertEquals(6, classify(card("Tangletrove Kelp", "Artifact Creature — Clue Plant", "{5}{U}{U}",
            "At the beginning of each combat, other Clues you control become 6/6 Plant creatures in addition to their other types until end of turn.", "6/6")).animateClues)
        assertTrue(classify(card("Mechanized Production", "Enchantment — Aura", "{2}{U}{U}",
            "Enchant artifact you control\nAt the beginning of your upkeep, create a token that's a copy of enchanted artifact. Then if you control eight or more artifacts with the same name as one another, you win the game.")).mechanized)
    }

    @Test fun countersAndTokens() {
        val leonardo = classify(card("Leonardo, the Balance", "Legendary Creature — Mutant Ninja Turtle", "{3}{W}",
            "Whenever a token you control enters, you may put a +1/+1 counter on each creature you control. Do this only once each turn.", "3/3"))
        val t = leonardo.kindTriggers.single()
        assertEquals("token", t.kind)
        assertEquals(1, t.counters)
        assertTrue(t.oncePerTurn)
        assertTrue(classify(card("Corpsejack Menace", "Creature — Fungus", "{2}{B}{G}",
            "If one or more +1/+1 counters would be put on a creature you control, twice that many +1/+1 counters are put on it instead.", "4/4")).counterTimes)
        // A Leonardo deck making a token a turn grows its board: it wins sooner than without Leonardo's trigger.
        val maker = card("Maker", "Enchantment", "{1}{W}", "At the beginning of your upkeep, create a 1/1 white Soldier creature token.", quantity = 30)
        val plains = card("Plains", "Basic Land — Plains", "", "", quantity = 69, produced = "W")
        val leo = card("Leonardo, the Balance", "Legendary Creature — Mutant Ninja Turtle", "{3}{W}",
            "Whenever a token you control enters, you may put a +1/+1 counter on each creature you control. Do this only once each turn.", "3/3", commander = true)
        val plain = leo.copy(name = "Plain Turtle", oracleText = "")
        val with = WinOddsCalc.calculate(listOf(leo, maker, plains), "W", emptyList(), games = 300)!!
        val without = WinOddsCalc.calculate(listOf(plain, maker, plains), "W", emptyList(), games = 300)!!
        assertTrue(with.usual!! < without.usual!!)
    }

    @Test fun opponentsDrawToo() {
        val council = classify(card("The Council of Four", "Legendary Creature — Human Noble", "{3}{W}{U}",
            "Whenever a player draws their second card during their turn, you draw a card.\nWhenever a player casts their second spell during their turn, you create a 2/2 white Knight creature token.", "0/8"))
        assertEquals(1, council.onSecondDraw.draw)
        assertEquals(1, council.onOppSecondDraw.draw)
        assertTrue(council.oppSecondDrawTheirTurn)
        assertEquals(1, council.onSecondSpell.tokens)
        assertEquals(1, classify(card("Howling Mine", "Artifact", "{2}",
            "At the beginning of each player's draw step, if this artifact is untapped, that player draws an additional card.")).eachDrawStep)
    }

    @Test fun cloudsAttackTrigger() {
        val cloud = classify(card("Cloud, Ex-SOLDIER", "Legendary Creature — Human Soldier Mercenary", "{2}{R}{G}{W}",
            "Haste\nWhen Cloud enters, attach up to one target Equipment you control to it.\nWhenever Cloud attacks, draw a card for each equipped attacking creature you control. Then if Cloud has power 7 or greater, create two Treasure tokens.", "4/4"))
        assertTrue(cloud.attachOnEnter)
        assertTrue(cloud.onAttack.drawPerEquipped)
        assertEquals(2, cloud.onAttack.treasures)
        assertEquals(7, cloud.onAttack.powerAtLeast)
        assertFalse(cloud.drawTurn > 0)
    }

    @Test fun cascadeDiscoverAndParadigm() {
        assertEquals(4, classify(card("Apex Devastator", "Creature — Chimera Hydra", "{8}{G}{G}",
            "Cascade, cascade, cascade, cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order. Multiple instances of cascade each trigger separately.)", "10/10")).cascade)
        val phoenix = classify(card("Aurora Phoenix", "Creature — Phoenix", "{4}{R}{R}",
            "Flying\nCascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)\nWhenever you cast a spell with cascade, return this card from your graveyard to your hand.", "5/3"))
        assertEquals(1, phoenix.cascade)
        assertTrue(phoenix.cascadeReturn)
        assertEquals(5, classify(card("Monstrous Vortex", "Enchantment", "{2}{R}{G}",
            "Whenever you cast a creature spell with power 5 or greater, discover X, where X is that spell's mana value. (Exile cards from the top of your library until you exile a nonland card with that mana value or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)")).discoverPower)
        assertEquals(5, classify(card("Trumpeting Carnosaur", "Creature — Dinosaur", "{4}{R}{R}",
            "Trample\nWhen this creature enters, discover 5.\n{2}{R}, Discard this card: It deals 3 damage to target creature or planeswalker.", "7/6")).discover)
        assertEquals(3, classify(card("Sweet-Gum Recluse", "Creature — Spider", "{4}{G}{G}",
            "Flash\nCascade\nReach\nWhen this creature enters, put three +1/+1 counters on each of any number of target creatures that entered this turn.", "0/3")).newCounters)
        assertTrue(classify(card("Maelstrom Nexus", "Enchantment", "{W}{U}{B}{R}{G}",
            "The first spell you cast each turn has cascade. (When you cast your first spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)")).cascadeFirst)
        val capstone = classify(card("Improvisation Capstone", "Sorcery — Lesson", "{5}{R}{R}",
            "Exile cards from the top of your library until you exile cards with total mana value 4 or greater. You may cast any number of spells from among them without paying their mana costs.\nParadigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)"))
        assertEquals(4, capstone.capstone)
        assertTrue(capstone.paradigm)

        // A deck of big cascade creatures wins sooner than the same deck without cascade.
        val commander = card("Leader", "Legendary Creature — Human", "{G}", "", "1/1", commander = true)
        val small = card("Small", "Creature — Beast", "{2}{G}", "", "3/3", quantity = 25)
        val big = card("Big", "Creature — Beast", "{4}{G}{G}", "Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less.)", "5/5", quantity = 25)
        val lands = forest.copy(quantity = 49)
        val cascading = WinOddsCalc.calculate(listOf(commander, small, big, lands), "G", emptyList(), games = 1000)!!
        val plain = WinOddsCalc.calculate(listOf(commander, small, big.copy(oracleText = ""), lands), "G", emptyList(), games = 1000)!!
        assertTrue("${cascading.byTurn} vs ${plain.byTurn}", cascading.byTurn[9] > plain.byTurn[9] + 5)

        // Paradigm: once resolved, a free copy every turn after.
        val lesson = card("Lesson", "Sorcery — Lesson", "{2}{B}", "Each opponent loses 4 life.\nParadigm (Then exile this spell.)", quantity = 4)
        val swamps = card("Swamp", "Basic Land — Swamp", "", "", quantity = 45, produced = "B")
        val vanilla = card("Vanilla", "Creature — Human", "{1}{B}", "", "2/2", quantity = 50)
        val boss = card("Boss", "Legendary Creature — Human", "{B}", "", "1/1", commander = true)
        val withParadigm = WinOddsCalc.calculate(listOf(boss, lesson, vanilla, swamps), "B", emptyList(), games = 1000)!!
        val without = WinOddsCalc.calculate(listOf(boss, lesson.copy(oracleText = "Each opponent loses 4 life."), vanilla, swamps), "B", emptyList(), games = 1000)!!
        assertTrue("${withParadigm.byTurn} vs ${without.byTurn}", withParadigm.byTurn[9] > without.byTurn[9] + 5)
    }
}
