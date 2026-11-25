package com.mtgtrader

import com.mtgtrader.data.BracketTag
import com.mtgtrader.data.LocalRating
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltCombo
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.119 (combo pieces): a combo that also needs "any card of this kind" (a man-land) counts that piece. */
class ComboExtrasTest {
    private val nantuko = SaltCombo(
        cards = listOf("Springheart Nantuko", "Tireless Provisioner"),
        extras = listOf("Man-Land that Enters Untapped"),
        spellbookUri = "https://commanderspellbook.com/combo/3875-5702--131/",
    )

    @Test
    fun readsTheExtraPieceAndNamesIt() {
        val json = """{"version":${SaltCard.CURRENT},"source":"app","ratingVersion":3,"combos":[{"cards":["Springheart Nantuko","Tireless Provisioner"],"extras":["Man-Land that Enters Untapped"],"counts":false}]}"""
        val combo = SaltCard.decode(json)!!.combos.single()
        assertEquals(listOf("Man-Land that Enters Untapped"), combo.extras)
        assertEquals(3, combo.pieces)
        assertEquals("Tireless Provisioner + any Man-Land that Enters Untapped", combo.piecesText(listOf("Tireless Provisioner")))
    }

    @Test
    fun twoCardsAndAnExtraPieceAreTaggedAsThreePieces() {
        val card = SaltCard(combos = listOf(nantuko), version = SaltCard.CURRENT)
        assertEquals(setOf(BracketTag.OTHER_COMBO_3), card.tags("Springheart Nantuko"))
    }

    @Test
    fun olderCombosHaveNoExtraPieces() {
        val combo = SaltCard.decode("""{"combos":[{"cards":["A","B"],"counts":true}]}""")!!.combos.single()
        assertEquals(2, combo.pieces)
        assertEquals("A + B", combo.piecesText())
    }

    @Test
    fun ratingsFromBeforeAreAskedForAgain() {
        assertEquals(3, LocalRating.RATING_VERSION)
    }
}
