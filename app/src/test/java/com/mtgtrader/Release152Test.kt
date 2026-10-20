package com.mtgtrader

import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.SaltCombo
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinOddsCalc
import com.mtgtrader.data.WinOddsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.52: when the deck can win (goldfish win turn), with commander damage, equipment, pump spells and triggers. */
class Release152Test {
    private fun card(
        name: String,
        type: String,
        cost: String,
        text: String = "",
        pt: String? = null,
        quantity: Int = 1,
        commander: Boolean = false,
        produced: String = "",
    ) = StatsCard(
        name, name, quantity, WinOddsCalc.run { com.mtgtrader.data.ManaOddsCalc.cost(cost).manaValue.toDouble() }, type, commander,
        type, cost, produced, text, null, pt,
    )

    private val mountain = card("Mountain", "Basic Land — Mountain", "", "({T}: Add {R}.)", quantity = 99, produced = "R")
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, setOf('R', 'G', 'W'), listOf(mountain), emptySet())

    @Test fun readsVialSmashersTrigger() {
        val vial = classify(
            card(
                "Vial Smasher the Fierce", "Legendary Creature — Goblin Berserker", "{1}{B}{R}",
                "Whenever you cast your first spell each turn, choose an opponent at random. Vial Smasher deals damage equal to that spell's mana value to that player or a planeswalker that player controls.\nPartner",
                "2/3",
            ),
        )
        val t = vial.castTriggers.single()
        assertTrue(t.firstOnly)
        assertEquals(WinOddsCalc.Filter.ANY, t.filter)
        assertFalse(t.each)
        assertEquals(2, vial.power)
    }

    @Test fun readsOtherDamageTriggers() {
        val purphoros = classify(card("Purphoros, God of the Forge", "Legendary Enchantment Creature — God", "{3}{R}", "Indestructible\nWhenever another creature you control enters, Purphoros deals 2 damage to each opponent.", "6/5"))
        assertEquals(WinOddsCalc.EnterTrigger(land = false, another = true, each = true, amount = 2), purphoros.enterTriggers.single())
        val landfall = classify(card("Rumbling Ruin", "Creature — Elemental", "{3}{R}", "Landfall — Whenever a land you control enters, this creature deals 1 damage to each opponent.", "3/3"))
        assertTrue(landfall.enterTriggers.single().land)
        val charm = classify(card("Boros Charm", "Instant", "{R}{W}", "Choose one —\n• Boros Charm deals 4 damage to target player or planeswalker.\n• Permanents you control gain indestructible until end of turn.\n• Target creature gains double strike until end of turn."))
        assertEquals(4, charm.oneOnce)
        assertTrue(charm.pumpDouble)
    }

    @Test fun readsEquipmentAurasAndPumps() {
        val hammer = classify(card("Colossus Hammer", "Artifact — Equipment", "{1}", "Equipped creature gets +10/+10 and loses flying.\nEquip {8}"))
        assertEquals(10, hammer.bonus)
        assertEquals(8, hammer.equip)
        val whip = classify(card("Bladehold War-Whip", "Artifact — Equipment", "{1}{R}{W}", "For Mirrodin! (When this Equipment enters, create a 2/2 red Rebel creature token, then attach this to it.)\nEquipped creature has double strike.\nEquip {3}"))
        assertTrue(whip.grantsDouble)
        assertEquals(2, whip.equipsToken)
        val greaves = classify(card("Lightning Greaves", "Artifact — Equipment", "{2}", "Equipped creature has haste and shroud.\nEquip {0}"))
        assertTrue(greaves.grantsHaste)
        assertEquals(0, greaves.equip)
        val armor = classify(card("Ethereal Armor", "Enchantment — Aura", "{W}", "Enchant creature\nEnchanted creature gets +1/+1 for each enchantment you control and has first strike."))
        assertTrue(armor.aura)
        assertEquals(WinOddsCalc.ForEach.ENCHANTMENTS, armor.forEach)
        assertEquals(3, classify(card("Giant Growth", "Instant", "{G}", "Target creature gets +3/+3 until end of turn.")).pump)
        assertEquals(2, classify(card("Blossoming Defense", "Instant", "{G}", "Target creature you control gets +2/+2 and gains hexproof until end of turn.")).pump)
        assertTrue(classify(card("Tyvar's Stand", "Instant", "{X}{G}", "Target creature you control gets +X/+X and gains hexproof and indestructible until end of turn.")).pumpX)
    }

    @Test fun readsKeywordsAndCombatDraw() {
        val sarge = classify(
            card(
                "Sergeant John Benton", "Legendary Creature — Human Soldier", "{1}{G}{W}",
                "Trample, haste\nShare Intelligence — Whenever Sergeant John Benton deals combat damage to a player, you and that player each draw that many cards.",
                "2/2", commander = true,
            ),
        )
        assertTrue(sarge.haste)
        assertFalse(sarge.double)
        assertTrue(sarge.combatDraw < 0)
    }

    @Test fun readsBabasSacrificeAbilityAndArtifactTokens() {
        val baba = classify(
            card(
                "Baba Lysaga, Night Witch", "Legendary Creature — Human Warlock", "{1}{B}{G}",
                "{T}, Sacrifice up to three permanents: If there were three or more card types among the sacrificed permanents, each opponent loses 3 life, you gain 3 life, and you draw three cards.",
                "3/3", commander = true,
            ),
        )
        assertEquals(WinOddsCalc.SacrificeAbility(count = 3, types = 3, drain = 3, draw = 3), baba.sacrifice)
        assertEquals(setOf("Creature"), baba.types)
        val provisioner = classify(card("Tireless Provisioner", "Creature — Elf Scout", "{2}{G}", "Landfall — Whenever a land you control enters, create a Food token or a Treasure token.", "3/2"))
        assertEquals(1, provisioner.artifactsLandfall)
        val pursuit = classify(card("Obsessive Pursuit", "Enchantment", "{B}", "When this enchantment enters and at the beginning of your upkeep, you lose 1 life and create a Clue token."))
        assertEquals(1, pursuit.artifactsOnce)
        assertEquals(1, pursuit.artifactsTurn)
        val vault = classify(card("Treasure Vault", "Artifact Land", "", "{T}: Add {C}.", produced = "C"))
        assertEquals(setOf("Artifact", "Land"), vault.types)
    }

    /** A sacrifice commander with lands, artifact lands and Food makers drains the table. */
    @Test fun sacrificeAbilityDrains() {
        val baba = card(
            "Baba Lysaga, Night Witch", "Legendary Creature — Human Warlock", "{1}{B}{G}",
            "{T}, Sacrifice up to three permanents: If there were three or more card types among the sacrificed permanents, each opponent loses 3 life, you gain 3 life, and you draw three cards.",
            "3/3", commander = true,
        )
        val forest = card("Forest", "Basic Land — Forest", "", "({T}: Add {G}.)", quantity = 30, produced = "G")
        val swamp = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", quantity = 10, produced = "B")
        val goose = card("Food Maker", "Creature — Bird", "{1}{G}", "When this creature enters, create a Food token.", "0/1", quantity = 30)
        val bear = card("Bear", "Creature — Bear", "{1}{G}", "", "2/2", quantity = 29)
        val odds = WinOddsCalc.calculate(listOf(baba, forest, swamp, goose, bear), "BG", emptyList(), games = 300)!!
        assertTrue("damage ${odds.damage}, counted ${odds.counted}", (odds.damage[WinOddsCalc.DRAIN] ?: 0) >= 10)
        assertEquals(1, odds.counted.drains)
    }

    @Test fun combosAreReadByWhatTheyDo() {
        assertEquals(WinOddsCalc.ComboKind.WINS, WinOddsCalc.plan(SaltCombo(listOf("A", "B"), results = listOf("infinite-damage", "win-game")))!!.kind)
        // Since 1.53 a loop counts too (with a card that turns it into life loss), and so do endless tokens.
        assertEquals(setOf(WinOddsCalc.LoopEvent.ENTER, WinOddsCalc.LoopEvent.DEATH), WinOddsCalc.plan(SaltCombo(listOf("Devoted Druid", "Mikaeus, the Unhallowed"), results = listOf("infinite-etb", "infinite-ltb", "infinite-lifeloss")))!!.loops)
        assertTrue(WinOddsCalc.plan(SaltCombo(listOf("A", "B"), results = listOf("infinite-tokens")))!!.tokens)
        assertNull(WinOddsCalc.plan(SaltCombo(listOf("A", "B"), results = listOf("infinite-mana"))))
    }

    /** A 7/7 commander with haste for {R} and only lands: 21 commander damage knocks out a player every third turn. */
    @Test fun commanderDamageKnocksPlayersOut() {
        val giant = card("Hasty Giant", "Legendary Creature — Giant", "{R}", "Haste", "7/7", commander = true)
        val odds = WinOddsCalc.calculate(listOf(giant, mountain), "R", emptyList(), games = 200)!!
        assertEquals(9, odds.usual)
        assertEquals(100, odds.byTurn[8])
        assertEquals(0, odds.byTurn[7])
        assertEquals(mapOf(WinOddsCalc.COMMANDER to 100), odds.damage)
        assertEquals(100, odds.commanderKnockouts)
        assertEquals("T9", odds.label)
    }

    /** The same commander with pump spells wins sooner. */
    @Test fun pumpSpellsGoOnTheCommander() {
        val giant = card("Hasty Giant", "Legendary Creature — Giant", "{R}", "Haste", "7/7", commander = true)
        val lands = mountain.copy(quantity = 60)
        // +4 makes 11 a hit: two hits for 21 commander damage instead of three.
        val growth = card("Brute Force", "Instant", "{R}", "Target creature gets +4/+4 until end of turn.", quantity = 39)
        val odds = WinOddsCalc.calculate(listOf(giant, lands, growth), "R", emptyList(), games = 300)!!
        assertTrue("usually by ${odds.usual}", odds.usual!! < 9)
    }

    @Test fun decksWithoutACommanderAreLeftOut() {
        assertNull(WinOddsCalc.calculate(listOf(mountain), "R", emptyList(), games = 10))
    }

    @Test fun labelsAndWindow() {
        val slow = WinOdds(10, List(15) { if (it == 14) 40 else 0 }, List(15) { 0 }, emptyMap(), 0, com.mtgtrader.data.WinCounted())
        assertEquals("T15+", slow.label)
        assertNull(slow.usual)
        assertEquals(8..15, slow.window)
        val fast = WinOdds(10, List(15) { t -> if (t >= 6) 100 else if (t >= 4) 60 else 0 }, List(15) { 0 }, mapOf("combat" to 70, "commander" to 30), 0, com.mtgtrader.data.WinCounted())
        assertEquals(5, fast.usual)
        assertEquals(7, fast.nineInTen)
        assertEquals(1..8, fast.window)
        assertEquals("Combat 70%, commander damage 30%.", fast.damageText)
    }

    @Test fun fingerprintFollowsTheCards() {
        val giant = card("Hasty Giant", "Legendary Creature — Giant", "{R}", "Haste", "7/7", commander = true)
        val a = WinOddsStore.fingerprint(listOf(giant, mountain), "R", emptyList())
        assertEquals(a, WinOddsStore.fingerprint(listOf(mountain, giant), "R", emptyList()))
        assertNotEquals(a, WinOddsStore.fingerprint(listOf(giant, mountain.copy(quantity = 98)), "R", emptyList()))
        assertNotEquals(a, WinOddsStore.fingerprint(listOf(giant, mountain), "R", listOf(SaltCombo(listOf("A", "B"), results = listOf("win-game")))))
        assertTrue(CardInfo.FACE_BREAK.isNotEmpty())
    }
}
