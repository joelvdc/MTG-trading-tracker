package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltCombo
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.120: combos follow Commander Spellbook's starting zones and the mana a combo needs; its bracket tag is kept. */
class Release1120Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    private fun plains(n: Int) = card("Plains", "Basic Land — Plains", "", "({T}: Add {W}.)", quantity = n, produced = "W")
    // A commander too expensive to compete for the mana.
    private val commander = card("Late Commander", "Legendary Creature — Human", "{7}{W}", "", "0/4", commander = true)
    private val filler = card("Filler", "Sorcery", "{W}", "Draw a card.")

    private fun combo(zones: List<String> = emptyList(), mana: String? = null) =
        SaltCombo(listOf("Piece H", "Piece B"), results = listOf("win-game"), zones = zones, manaNeeded = mana)

    @Test fun planReadsZonesAndMana() {
        val p = WinOddsCalc.plan(
            SaltCombo(listOf("Ondu Spiritdancer", "Secret Arcade // Dusty Parlor"), results = listOf("infinite-etb", "infinite-tokens"), zones = listOf("H", "B"), manaNeeded = "{4}{W}"),
        )!!
        assertEquals(setOf("ondu spiritdancer"), p.handKeys)
        assertEquals(5, p.manaNeeded!!.manaValue)
        // "HB" (either works) and missing zones put no piece in hand only; no mana listed means none needed.
        val loose = WinOddsCalc.plan(SaltCombo(listOf("A", "B"), results = listOf("win-game"), zones = listOf("HB")))!!
        assertEquals(emptySet<String>(), loose.handKeys)
        assertNull(loose.manaNeeded)
    }

    /** A cheap must-enter piece cast before the other piece is in sight can't start the combo any more. */
    @Test fun aMustEnterPieceAlreadyOutBlocksTheCombo() {
        val h = card("Piece H", "Creature — Kor Cleric", "{W}", "", "1/1", quantity = 30)
        val b = card("Piece B", "Enchantment", "{4}{W}", "")
        val deck = listOf(commander, plains(40), h, b, filler.copy(quantity = 27))
        val plain = WinOddsCalc.calculate(deck, "W", listOf(combo()), games = 2000)!!
        val ordered = WinOddsCalc.calculate(deck, "W", listOf(combo(listOf("H", "B"))), games = 2000)!!
        assertTrue("plain ${plain.byCombo}, ordered ${ordered.byCombo}", ordered.byCombo.last() * 3 < plain.byCombo.last())
    }

    /** With the other piece in hand, the must-enter piece waits instead of being cast as a creature. */
    @Test fun aMustEnterPieceWaitsForTheRest() {
        val h = card("Piece H", "Creature — Kor Cleric", "{1}{W}", "", "1/1", quantity = 25)
        val b = card("Piece B", "Enchantment", "{3}{W}", "", quantity = 25)
        val deck = listOf(commander, plains(40), h, b, filler.copy(quantity = 9))
        val ordered = WinOddsCalc.calculate(deck, "W", listOf(combo(listOf("H", "B"))), games = 2000)!!
        // Piece B on turn 4, Piece H from turn 5 (or both on turn 6): most games by turn 6.
        assertTrue("${ordered.byCombo}", ordered.byCombo[5] > 60)
    }

    /** The mana Commander Spellbook lists counts even when the pieces cost less (a land a turn, no ramp). */
    @Test fun aComboNeedsItsListedMana() {
        val h = card("Piece H", "Artifact", "{1}", "", quantity = 20)
        val b = card("Piece B", "Artifact", "{1}", "", quantity = 20)
        val deck = listOf(commander, plains(40), h, b, filler.copy(quantity = 19))
        val cheap = WinOddsCalc.calculate(deck, "W", listOf(combo()), games = 1000)!!
        val listed = WinOddsCalc.calculate(deck, "W", listOf(combo(mana = "{6}")), games = 1000)!!
        assertTrue("${cheap.byCombo}", cheap.byCombo[2] > 50)
        assertEquals("${listed.byCombo}", 0, listed.byCombo[4])
        assertTrue("${listed.byCombo}", listed.byCombo[7] > 50)
    }

    @Test fun keepsTheNewFields() {
        val text = """{"combos":[{"cards":["A","B"],"bracketTag":"S","zones":["H","B"],"manaNeeded":"{4}{W}"}],"twoCardCombos":[["A","B"]]}"""
        val card = SaltCard.decode(text)!!
        val c = card.combos.single()
        assertEquals("S", c.bracketTag)
        assertEquals(listOf("H", "B"), c.zones)
        assertEquals("{4}{W}", c.manaNeeded)
        assertEquals("Spicy", c.tagName)
        assertEquals(c, card.comboFor(listOf("b", "A")))
        // Older data has none of them.
        val old = SaltCard.decode("""{"combos":[{"cards":["A","B"]}]}""")!!.combos.single()
        assertNull(old.bracketTag)
        assertEquals(emptyList<String>(), old.zones)
        assertNull(old.tagName)
    }
}
