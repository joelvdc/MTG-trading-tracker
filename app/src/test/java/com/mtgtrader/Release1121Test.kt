package com.mtgtrader

import com.mtgtrader.data.LocalRating
import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltCombo
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOddsCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.121: a must-enter combo piece already out starts its combo again when a blink makes it re-enter, and every
 * starting zone Commander Spellbook lists counts (graveyard, library, exiled by another piece).
 */
class Release1121Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt)

    private fun plains(n: Int) = card("Plains", "Basic Land — Plains", "", "({T}: Add {W}.)", quantity = n, produced = "W")
    private val commander = card("Late Commander", "Legendary Creature — Human", "{7}{W}", "", "0/4", commander = true)
    private val filler = card("Filler", "Sorcery", "{W}", "Draw a card.")
    private val circle = card("Teleportation Circle", "Enchantment", "{3}{W}",
        "At the beginning of your end step, exile up to one target artifact or creature you control, then return that card to the battlefield under its owner's control.")
    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, setOf('W', 'U', 'B', 'R', 'G'), listOf(plains(1)), emptySet())

    private fun combo(a: String, b: String, zones: List<String>, reenters: List<String> = emptyList(), exiledBy: List<String> = emptyList()) =
        SaltCombo(listOf(a, b), results = listOf("win-game"), zones = zones, reenters = reenters, exiledBy = exiledBy)

    @Test fun ratingVersionAndPlan() {
        assertEquals(5, LocalRating.RATING_VERSION)
        assertEquals(17, WinOddsCalc.VERSION)
        val p = WinOddsCalc.plan(
            SaltCombo(
                listOf("High Perfect Morcant", "Isochron Scepter", "Dramatic Reversal"), results = listOf("win-game"),
                zones = listOf("HC", "H", "E"), reenters = listOf("High Perfect Morcant"), exiledBy = listOf("", "", "Isochron Scepter"),
                prerequisites = listOf("At least six other cards in your graveyard."),
            ),
        )!!
        assertEquals(setOf("high perfect morcant", "isochron scepter"), p.handKeys)
        assertEquals(setOf("high perfect morcant"), p.reenterKeys)
        assertEquals(mapOf("dramatic reversal" to "isochron scepter"), p.exiledBy)
        assertEquals(6, p.graveyardCards)
        assertTrue(p.offHand("dramatic reversal") && !p.offHand("isochron scepter"))
    }

    @Test fun readsMoreBlinks() {
        assertEquals(1, classify(card("Ephemerate", "Instant", "{W}",
            "Exile target creature you control, then return it to the battlefield under its owner's control.\nRebound")).onEnter.flicker)
        assertEquals(1, classify(card("Thassa, Deep-Dwelling", "Legendary Enchantment Creature — God", "{3}{U}",
            "Indestructible\nAt the beginning of your end step, exile up to one other target creature you control, then return that card to the battlefield under your control.", "6/5")).eachTurn.flicker)
        assertEquals(1, classify(circle).eachTurn.flicker)
    }

    /** A cheap must-enter creature cast too early comes back with a blink when its combo starts by entering. */
    @Test fun aBlinkRestartsAReenteringPiece() {
        val h = card("Piece H", "Creature — Kor Cleric", "{W}", "", "1/1", quantity = 25)
        val b = card("Piece B", "Enchantment", "{4}{W}", "")
        val deck = listOf(commander, plains(40), h, b, circle.copy(quantity = 10), filler.copy(quantity = 22))
        val castOnly = WinOddsCalc.calculate(deck, "W", listOf(combo("Piece H", "Piece B", listOf("H", "B"))), games = 2000)!!
        val blinked = WinOddsCalc.calculate(deck, "W", listOf(combo("Piece H", "Piece B", listOf("H", "B"), reenters = listOf("Piece H"))), games = 2000)!!
        assertTrue("cast only ${castOnly.byCombo}, blinked ${blinked.byCombo}", blinked.byCombo.last() > castOnly.byCombo.last() * 2 + 5)
    }

    /** A blink instant in hand works too: it's cast with the combo. */
    @Test fun aBlinkSpellInHandCounts() {
        val h = card("Piece H", "Creature — Kor Cleric", "{W}", "", "1/1", quantity = 25)
        val b = card("Piece B", "Enchantment", "{4}{W}", "")
        val ephemerate = card("Ephemerate", "Instant", "{W}", "Exile target creature you control, then return it to the battlefield under its owner's control.", quantity = 10)
        val deck = listOf(commander, plains(40), h, b, ephemerate, filler.copy(quantity = 22))
        val castOnly = WinOddsCalc.calculate(deck, "W", listOf(combo("Piece H", "Piece B", listOf("H", "B"))), games = 2000)!!
        val blinked = WinOddsCalc.calculate(deck, "W", listOf(combo("Piece H", "Piece B", listOf("H", "B"), reenters = listOf("Piece H"))), games = 2000)!!
        assertTrue("cast only ${castOnly.byCombo}, blinked ${blinked.byCombo}", blinked.byCombo.last() > castOnly.byCombo.last() * 2 + 5)
    }

    /** A piece that starts in the library (Kiki-Jiki found by Goblin Recruiter) needs only to be still in it. */
    @Test fun aLibraryPieceStaysInTheLibrary() {
        val l = card("Piece L", "Creature — Goblin", "{2}{R}{R}", "", "2/2")
        val b = card("Piece B", "Enchantment", "{3}{W}", "", quantity = 20)
        val deck = listOf(commander, plains(40), l, b, filler.copy(quantity = 38))
        val fromHand = WinOddsCalc.calculate(deck, "W", listOf(combo("Piece L", "Piece B", listOf("H", "B"))), games = 1000)!!
        val fromLibrary = WinOddsCalc.calculate(deck, "W", listOf(combo("Piece L", "Piece B", listOf("L", "B"))), games = 1000)!!
        assertTrue("hand ${fromHand.byCombo}, library ${fromLibrary.byCombo}", fromLibrary.byCombo[7] > 60 && fromHand.byCombo[7] < 20)
    }

    /** A graveyard-only piece counts once it's milled, and never while there's no way to get it there. */
    @Test fun aGraveyardPieceHasToBeInTheGraveyard() {
        val g = card("Piece G", "Creature — Zombie", "{1}{W}", "", "2/2", quantity = 10)
        val b = card("Piece B", "Enchantment", "{1}{W}", "", quantity = 20)
        val mill = card("Self Mill", "Sorcery", "{W}", "Mill four cards.", quantity = 20)
        val noWay = WinOddsCalc.calculate(listOf(commander, plains(40), g, b, filler.copy(quantity = 28)), "W", listOf(combo("Piece G", "Piece B", listOf("G", "B"))), games = 1000)!!
        val milled = WinOddsCalc.calculate(listOf(commander, plains(40), g, b, mill, filler.copy(quantity = 8)), "W", listOf(combo("Piece G", "Piece B", listOf("G", "B"))), games = 1000)!!
        assertEquals("${noWay.byCombo}", 0, noWay.byCombo.last())
        assertTrue("${milled.byCombo}", milled.byCombo.last() > 30)
    }

    /** Dramatic Reversal exiled by Isochron Scepter: both in hand, the Scepter cast; not once the Scepter is out. */
    @Test fun aCardExiledByAnotherPiece() {
        val scepter = card("Isochron Scepter", "Artifact", "{2}", "Imprint", quantity = 15)
        val reversal = card("Dramatic Reversal", "Instant", "{1}{U}", "Untap all nonland permanents you control.", quantity = 15)
        val deck = listOf(commander, plains(40), scepter, reversal, filler.copy(quantity = 29))
        val exiled = WinOddsCalc.calculate(deck, "W", listOf(combo("Isochron Scepter", "Dramatic Reversal", listOf("H", "E"), exiledBy = listOf("", "Isochron Scepter"))), games = 1000)!!
        val unknown = WinOddsCalc.calculate(deck, "W", listOf(combo("Isochron Scepter", "Dramatic Reversal", listOf("H", "E"))), games = 1000)!!
        assertTrue("${exiled.byCombo}", exiled.byCombo[5] > 40)
        assertEquals("${unknown.byCombo}", 0, unknown.byCombo.last())
    }

    @Test fun keepsTheNewFields() {
        val text = """{"combos":[{"cards":["A","B"],"zones":["H","E"],"reenters":["A"],"exiledBy":["","A"]}]}"""
        val c = SaltCard.decode(text)!!.combos.single()
        assertEquals(listOf("A"), c.reenters)
        assertEquals(listOf("", "A"), c.exiledBy)
        val old = SaltCard.decode("""{"combos":[{"cards":["A","B"]}]}""")!!.combos.single()
        assertEquals(emptyList<String>(), old.reenters + old.exiledBy)
    }
}
