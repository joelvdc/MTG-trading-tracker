package com.mtgtrader

import com.mtgtrader.data.SaltCombo
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.53: combos read by what they do (outright wins, loops with a life-loss card, endless tokens), and tutors. */
class Release153Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, com.mtgtrader.data.ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    private val swamp = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", quantity = 40, produced = "B")
    private val commander = card("Plain Commander", "Legendary Creature — Human", "{3}{B}", "", "0/4", commander = true)
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, setOf('B', 'G', 'R'), listOf(swamp), emptySet())

    private val druid = card("Devoted Druid", "Creature — Elf Druid", "{1}{G}", "{T}: Add {G}.\nPut a -1/-1 counter on this creature: Untap this creature.", "0/2")
    private val mikaeus = card("Mikaeus, the Unhallowed", "Legendary Creature — Zombie Cleric", "{3}{B}{B}{B}", "Other non-Human creatures you control get +1/+1 and have undying.", "5/5")
    private val obelisk = card("Obelisk Spider", "Creature — Spider", "{1}{B}{G}", "Reach\nWhenever you put one or more -1/-1 counters on a creature, each opponent loses 1 life and you gain 1 life.", "1/4")
    private val morcant = card("High Perfect Morcant", "Legendary Creature — Elf Noble", "{2}{B}{G}", "Whenever High Perfect Morcant or another Elf you control enters, each opponent blights 1. (They each put a -1/-1 counter on a creature they control.)", "4/4")
    private val defenses = card("Flourishing Defenses", "Enchantment", "{4}{G}", "Whenever a -1/-1 counter is put on a creature, you may create a 1/1 green Elf Warrior creature token.")

    @Test fun loopsNeedACardThatTurnsThemIntoLifeLoss() {
        val loop = WinOddsCalc.plan(SaltCombo(listOf("Devoted Druid", "Mikaeus, the Unhallowed"), results = listOf("infinite-etb", "infinite-ltb", "infinite-lifeloss")))!!
        // Without a payoff it can't win.
        assertFalse(WinOddsCalc.settle(loop, listOf(classify(druid), classify(mikaeus))))
        // Druid puts -1/-1 counters itself, so Obelisk Spider turns the loop into life loss.
        assertTrue(WinOddsCalc.settle(loop, listOf(classify(druid), classify(mikaeus), classify(obelisk))))
        assertEquals(WinOddsCalc.ComboKind.LOOP, loop.kind)
        assertEquals(setOf("obelisk spider"), loop.payoffs)
    }

    @Test fun opponentsPuttingCountersDoesntTriggerObeliskSpider() {
        val tokens = WinOddsCalc.plan(
            SaltCombo(listOf("Flourishing Defenses", "High Perfect Morcant"), results = listOf("infinite-etb", "infinite-tokens"), prerequisites = listOf("an opponent controls a creature.")),
        )!!
        assertTrue(tokens.opponentCreature)
        assertTrue(WinOddsCalc.settle(tokens, listOf(classify(morcant), classify(defenses), classify(obelisk))))
        assertEquals(WinOddsCalc.ComboKind.TOKENS, tokens.kind)
    }

    @Test fun readsPrerequisites() {
        val p = WinOddsCalc.plan(
            SaltCombo(
                listOf("Blowfly Infestation", "Nest of Scarabs"), results = listOf("infinite-etb", "infinite-ltb", "infinite-lifeloss"),
                prerequisites = listOf("you control at least two other creatures with toughness equal to 1", "you have a way to put a -1/-1 counter on a creature."),
            ),
        )!!
        assertEquals(2, p.creatures)
        assertEquals(listOf(listOf("-1/-1 counter", "blight", "wither")), p.enablers)
    }

    @Test fun readsTutorsButNotLandSearches() {
        val demonic = classify(card("Demonic Tutor", "Sorcery", "{1}{B}", "Search your library for a card, put that card into your hand, then shuffle."))
        assertEquals(emptySet<String>(), demonic.tutor)
        assertFalse(demonic.tutorToTop)
        val vampiric = classify(card("Vampiric Tutor", "Instant", "{B}", "Search your library for a card, then shuffle and put that card on top. You lose 2 life."))
        assertNotNull(vampiric.tutor)
        val worldly = classify(card("Worldly Tutor", "Instant", "{G}", "Search your library for a creature card, reveal it, then shuffle and put the card on top."))
        assertEquals(setOf("Creature"), worldly.tutor)
        assertEquals(null, classify(card("Nature's Lore", "Sorcery", "{1}{G}", "Search your library for a Forest card, put that card onto the battlefield, then shuffle.")).tutor)
    }

    /** Two single combo pieces in 99 cards come together sooner when the deck also plays tutors. */
    @Test fun tutorsFindMissingPieces() {
        val a = card("Piece A", "Artifact", "{2}", "")
        val b = card("Piece B", "Artifact", "{2}", "")
        val combo = SaltCombo(listOf("Piece A", "Piece B"), results = listOf("win-game"))
        val filler = card("Filler", "Sorcery", "{1}", "Draw a card.", quantity = 57)
        val tutor = card("Demonic Tutor", "Sorcery", "{1}{B}", "Search your library for a card, put that card into your hand, then shuffle.", quantity = 10)
        val without = WinOddsCalc.calculate(listOf(commander, swamp, a, b, filler.copy(quantity = 57)), "B", listOf(combo), games = 2000)!!
        val with = WinOddsCalc.calculate(listOf(commander, swamp, a, b, filler.copy(quantity = 47), tutor), "B", listOf(combo), games = 2000)!!
        assertTrue("without ${without.byCombo}, with ${with.byCombo}", with.byCombo[7] > without.byCombo[7] + 15)
        assertEquals(1, with.counted.tutors)
    }

    /** Tree of Perdition's {T} needs it out since the turn before: Soul Immolation can't follow on the same turn. */
    @Test fun tapPiecesWaitATurn() {
        val tree = card("Tree of Perdition", "Creature — Plant", "{3}{B}", "Defender\n{T}: Exchange target opponent's life total with this creature's toughness.", "0/13")
        assertTrue(classify(tree).tapPiece)
        assertFalse(classify(druid).tapPiece)
        val immolation = card("Soul Immolation", "Sorcery", "{3}{B}", "As an additional cost to cast this spell, blight X.\nSoul Immolation deals X damage to each opponent and each creature they control.")
        val combo = SaltCombo(listOf("Tree of Perdition", "Soul Immolation"), results = listOf("infinite-damage", "win-game"), needsBoard = true)
        // Plenty of both (and a commander too expensive to compete for the mana): Tree on turn 4 at the earliest, then it
        // has to wait a turn, so no win before turn 5.
        val late = card("Late Commander", "Legendary Creature — Human", "{7}{B}", "", "0/4", commander = true)
        val odds = WinOddsCalc.calculate(listOf(late, swamp.copy(quantity = 59), tree.copy(quantity = 15), immolation.copy(quantity = 25)), "B", listOf(combo), games = 500)!!
        assertEquals("${odds.byCombo}", 0, odds.byCombo[3])
        assertTrue("${odds.byCombo}", odds.byCombo[4] > 50)
    }
}
