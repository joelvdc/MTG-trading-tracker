package com.mtgtrader

import com.mtgtrader.data.BracketTag
import com.mtgtrader.data.CommanderSaltApi
import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltCombo
import com.mtgtrader.data.StatsCard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 1.46: combos that don't count for the bracket, and when a deck can cast its commander. */
class Release146ComboTest {
    // Commander Salt's answer for Auntie Plague (archidekt.com/decks/20263351, Oct 2026), cut down to the combos.
    private val body = """
        {"id": "x", "powerLevelRating": 6.4, "bracketRating": 3,
         "cards": {"devoted_druid": {"name": "Devoted Druid"}, "mikaeus_the_unhallowed": {"name": "Mikaeus, the Unhallowed"},
                   "blowfly_infestation": {"name": "Blowfly Infestation"}, "hapatra_vizier_of_poisons": {"name": "Hapatra, Vizier of Poisons"},
                   "obelisk_spider": {"name": "Obelisk Spider"}, "yawgmoth_thran_physician": {"name": "Yawgmoth, Thran Physician"},
                   "tree_of_perdition": {"name": "Tree of Perdition"}, "soul_immolation": {"name": "Soul Immolation"}},
         "details": {
           "brackets": {"wotcBracket": 4, "csBracket": 3, "categories": {
               "twoCardCombos": {"count": 1, "list": ["devoted_druid_mikaeus_the_unhallowed"]},
               "earlyGameInfiniteCombos": {"count": 0, "list": []}}},
           "combos": {"list": [
               {"id": "devoted_druid_mikaeus_the_unhallowed", "cards": ["devoted_druid", "mikaeus_the_unhallowed"],
                "categories": ["infinite-etb", "infinite-ltb", "infinite-lifeloss"], "complexity": {"requiresCardsOnBoard": false},
                "spellbookUri": "https://commanderspellbook.com/combo/628-4762/"},
               {"id": "blowfly_infestation_hapatra_vizier_of_poisons", "cards": ["blowfly_infestation", "hapatra_vizier_of_poisons"],
                "categories": ["infinite-etb"], "complexity": {"requiresCardsOnBoard": true}, "spellbookUri": "https://commanderspellbook.com/combo/2228-4994/"},
               {"id": "hapatra_obelisk_yawgmoth", "cards": ["hapatra_vizier_of_poisons", "obelisk_spider", "yawgmoth_thran_physician"],
                "categories": ["infinite-lifeloss", "opponents-locked"], "complexity": {"requiresCardsOnBoard": true}},
               {"id": "soul_immolation_tree_of_perdition", "cards": ["soul_immolation", "tree_of_perdition"],
                "categories": ["infinite-damage", "win-game"], "complexity": {"requiresCardsOnBoard": true}},
               {"id": "devoted_druid_obelisk_yawgmoth", "cards": ["devoted_druid", "obelisk_spider", "yawgmoth_thran_physician"], "categories": []}
           ]}}}
    """.trimIndent()

    private val card = CommanderSaltApi.parse(body).card!!

    @Test
    fun readsEveryComboAndWhetherItCounts() {
        assertEquals(5, card.allCombos.size)
        assertEquals(1, card.allCombos.count { it.counts })
        assertEquals(listOf("Devoted Druid", "Mikaeus, the Unhallowed"), card.allCombos.first().cards)
        val tree = card.allCombos.first { "Tree of Perdition" in it.cards }
        assertFalse(tree.counts)
        assertTrue(tree.needsBoard)
        assertEquals("infinite damage, wins the game", tree.resultText)
        assertEquals("infinite ETB, infinite LTB, infinite life loss", card.allCombos.first().resultText)
        assertTrue(card.current)
    }

    @Test
    fun redForCombosThatCountBlueForTheOthersAndTheSmallestOfEach() {
        // In a counted two-card combo and an uncounted three-card one: one flag of each colour.
        assertEquals(setOf(BracketTag.COMBO, BracketTag.OTHER_COMBO_3), card.tags("Devoted Druid"))
        // In an uncounted two-card and three-card combo: only the smallest.
        assertEquals(setOf(BracketTag.OTHER_COMBO), card.tags("Hapatra, Vizier of Poisons"))
        assertEquals(setOf(BracketTag.OTHER_COMBO_3), card.tags("Yawgmoth, Thran Physician"))
        assertEquals("COMBO 2", BracketTag.COMBO.label)
        assertEquals("COMBO 3+", BracketTag.OTHER_COMBO_3.label)
        assertTrue(BracketTag.COMBO.strict)
        assertFalse(BracketTag.OTHER_COMBO.strict)
        // The card page lists the counted one first.
        assertEquals(listOf(true, false), card.combosOf("Devoted Druid").map { it.counts })
        assertEquals(listOf(listOf("Mikaeus, the Unhallowed")), card.combosWith("Devoted Druid"))
    }

    @Test
    fun earlyGameCombosOfThreeCardsCountRed() {
        val c = SaltCard(earlyCombos = listOf(listOf("A", "B", "C")), version = SaltCard.CURRENT)
        assertEquals(setOf(BracketTag.COMBO_3), c.tags("B"))
        // Data saved by 1.45 has only the counted combos and is fetched again.
        val old = SaltCard(twoCardCombos = listOf(listOf("X", "Y")), version = 2)
        assertFalse(old.current)
        assertEquals(listOf(SaltCombo(listOf("X", "Y"), counts = true)), old.allCombos)
    }

    @Test
    fun shortLabelsForTheCompactView() {
        assertEquals("C2", com.mtgtrader.ui.DeckMarks.short(BracketTag.OTHER_COMBO))
        assertEquals("C3+", com.mtgtrader.ui.DeckMarks.short(BracketTag.COMBO_3))
        assertEquals("COMBO 3+", com.mtgtrader.ui.DeckMarks.full(BracketTag.COMBO_3))
    }
}

class Release146OddsTest {
    @Serializable
    private data class Row(
        val name: String, val scryfallId: String, val quantity: Int, val cmc: Double, val types: String, val commander: Boolean,
        val typeLine: String?, val manaCost: String?, val producedMana: String?, val oracleText: String?,
    )

    private fun stats(r: Row) = StatsCard(r.name, r.scryfallId, r.quantity, r.cmc, r.types, r.commander, r.typeLine, r.manaCost, r.producedMana, r.oracleText, null)

    private fun auntiePlague(): List<StatsCard> {
        val text = javaClass.classLoader!!.getResourceAsStream("auntie_plague_stats.json")!!.bufferedReader().readText()
        return Json { ignoreUnknownKeys = true }.decodeFromString<List<Row>>(text).map(::stats)
    }

    @Test
    fun readsManaCosts() {
        val c = ManaOddsCalc.cost("{2}{B}{G/U}{2/W}{B/P}{X}")
        assertEquals(2, c.generic)
        assertEquals(listOf(setOf('B'), setOf('G', 'U'), setOf('W')), c.pips)
        assertEquals(5, c.manaValue)
        assertEquals(1, ManaOddsCalc.cost("{1}{G} // {3}{U}").generic)
    }

    @Test
    fun paysColouredPipsWithTheRightSources() {
        val cost = ManaOddsCalc.cost("{1}{B}{R}{G}")
        val brg = listOf(setOf('B') to 1, setOf('R', 'G') to 1, setOf('G') to 1, setOf('C') to 1)
        assertTrue(ManaOddsCalc.canPay(brg, cost))
        assertFalse(ManaOddsCalc.canPay(listOf(setOf('B') to 1, setOf('G') to 1, setOf('G') to 1, setOf('C') to 1), cost))
        assertFalse(ManaOddsCalc.canPay(brg.take(3), cost))
        assertTrue(ManaOddsCalc.canPay(listOf(setOf('C') to 2, setOf('B', 'R', 'G') to 1), ManaOddsCalc.cost("{2}{B}")))
    }

    /** The owner's Auntie Plague, against the same model run in Python when it was planned (39 / 79 / 91 %). */
    @Test
    fun matchesThePlannedNumbersForAuntiePlague() {
        val odds = ManaOddsCalc.calculate(auntiePlague(), "BRG")
        assertNotNull(odds)
        odds!!
        val auntie = odds.commanders.single()
        assertEquals("Auntie Ool, Cursewretch", auntie.name)
        assertEquals(4, auntie.manaValue)
        assertEquals(0, auntie.byTurn[1])
        assertTrue("turn 3: ${auntie.byTurn[2]}", abs(auntie.byTurn[2] - 39) <= 5)
        assertTrue("turn 4: ${auntie.byTurn[3]}", abs(auntie.byTurn[3] - 79) <= 4)
        assertTrue("turn 5: ${auntie.byTurn[4]}", abs(auntie.byTurn[4] - 91) <= 3)
        assertEquals(listOf('B', 'R', 'G'), odds.colours.map { it.first })
        assertTrue(odds.allColours!![3] in 85..95)
        assertEquals(38, odds.lands)
        assertEquals(3, odds.ramp)
        assertEquals(2, odds.rocks)
        // Never less likely on a later turn.
        assertTrue(auntie.byTurn.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun aDeckOfSwampsAlwaysCastsAOneDropOnTurnOne() {
        val swamp = StatsCard("Swamp", "s", 99, 0.0, "Basic Land — Swamp", false, "Basic Land — Swamp", "", "B", "({T}: Add {B}.)", null)
        val cmd = StatsCard("Commander", "c", 1, 1.0, "Legendary Creature", true, "Legendary Creature", "{B}", "", "", null)
        val odds = ManaOddsCalc.calculate(listOf(cmd, swamp), "B", games = 200)!!
        assertEquals(100, odds.commanders.single().byTurn[0])
        assertEquals(null, odds.allColours)
    }
}

class Release146TextTest {
    @Test
    fun readsMoxfieldsExportWithTagsAndFinishes() {
        val r = com.mtgtrader.data.DeckText.parse(
            """
            1 Kenrith, the Returned King (ELD) 303

            1 Sol Ring (CMR) 263 #Ramp
            1 Swords to Plowshares (STA) 10 *F* #Removal #!Staple
            2 Island (UST) 213

            SIDEBOARD:
            1 Rhystic Study (PCY) 45
            """.trimIndent()
        )
        assertTrue(r.unreadable.isEmpty())
        val kenrith = r.lines.first()
        assertEquals(com.mtgtrader.data.DeckText.Board.COMMANDER, kenrith.board)
        assertEquals("eld", kenrith.setCode)
        assertEquals("303", kenrith.number)
        val swords = r.lines.first { it.name == "Swords to Plowshares" }
        assertTrue(swords.foil)
        assertEquals(listOf("Removal", "Staple"), swords.tags)
        assertEquals(2, r.lines.first { it.name == "Island" }.quantity)
        assertEquals(com.mtgtrader.data.DeckText.Board.MAYBE, r.lines.last().board)
        assertTrue(r.hasTags)
        assertEquals(5, r.cardCount)
    }

    @Test
    fun readsArchidektAndArenaLists() {
        val archidekt = com.mtgtrader.data.DeckText.parse(
            "1x Auntie Ool, Cursewretch (ecl) 201 [Commander{top}]\n1x Sol Ring (cmr) 263 [Ramp]\n1x Lightning Greaves (c21) 247 [Maybeboard{noDeck}{noPrice},Protection]"
        )
        assertEquals(com.mtgtrader.data.DeckText.Board.COMMANDER, archidekt.lines[0].board)
        assertEquals(listOf("Ramp"), archidekt.lines[1].tags)
        assertEquals(com.mtgtrader.data.DeckText.Board.MAYBE, archidekt.lines[2].board)
        val arena = com.mtgtrader.data.DeckText.parse("Commander\n1 Teval, the Balanced Scale (TDM) 230\n\nDeck\n1 Sol Ring\n35 Forest")
        assertEquals(com.mtgtrader.data.DeckText.Board.COMMANDER, arena.lines[0].board)
        assertEquals(null, arena.lines[1].setCode)
        assertEquals(35, arena.lines[2].quantity)
        assertEquals(37, arena.cardCount)
        // A plain list without a commander block guesses none.
        val plain = com.mtgtrader.data.DeckText.parse("1 Sol Ring\n1 Arcane Signet\nthis isn't a card line")
        assertTrue(plain.lines.none { it.board == com.mtgtrader.data.DeckText.Board.COMMANDER })
        assertEquals(listOf("this isn't a card line"), plain.unreadable)
    }
}

class Release146ItemsTest {
    private val ref = com.mtgtrader.data.CardRef("sr", "Sol Ring", "cmr", "Commander Legends", "263", "uncommon", null, 1, 1.0, null, true, true)
    private fun line(rel: Long?, cardId: Long? = 77, qty: Int = 1, foil: Boolean = false) = com.mtgtrader.data.DeckCard(
        deckId = 5, card = ref, quantity = qty, category = "Ramp", types = "Artifact", cmc = 1.0, commander = false, foil = foil,
        categories = "Ramp\nStaples", archidektCardId = cardId, relationId = rel, lineUid = "u1",
    )
    private fun edit(rel: Long?) = com.mtgtrader.data.DeckEdit(deckId = 5, lineUid = "u1", relationId = rel, archidektCardId = 77, name = "Sol Ring", categories = "Ramp")

    @Test
    fun addsModifiesAndRemovesLikeArchidektsEditor() {
        val add = com.mtgtrader.data.DeckEditItems.item(edit(null), line(null, qty = 2, foil = true))!!
        assertEquals("add", add["action"]!!.jsonPrimitive.content)
        assertEquals(77, add["cardid"]!!.jsonPrimitive.content.toInt())
        assertEquals("u1", add["patchId"]!!.jsonPrimitive.content)
        assertEquals(listOf("Ramp", "Staples"), add["categories"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("2", add["modifications"]!!.jsonObject["quantity"]!!.jsonPrimitive.content)
        assertEquals("Foil", add["modifications"]!!.jsonObject["modifier"]!!.jsonPrimitive.content)
        assertTrue("deckRelationId" !in add)
        val modify = com.mtgtrader.data.DeckEditItems.item(edit(900), line(900))!!
        assertEquals("modify", modify["action"]!!.jsonPrimitive.content)
        assertEquals("900", modify["deckRelationId"]!!.jsonPrimitive.content)
        val remove = com.mtgtrader.data.DeckEditItems.item(edit(900), null)!!
        assertEquals("remove", remove["action"]!!.jsonPrimitive.content)
        assertEquals("900", remove["deckRelationId"]!!.jsonPrimitive.content)
        // Never on Archidekt and gone again, or a printing Archidekt doesn't know: nothing to send.
        assertEquals(null, com.mtgtrader.data.DeckEditItems.item(edit(null), null))
        assertEquals(null, com.mtgtrader.data.DeckEditItems.item(edit(null), line(null, cardId = null)))
    }

    @Test
    fun readsTheIdArchidektGaveAnAddedLine() {
        val answer = Json.parseToJsonElement("""{"add": [{"patchId": "u1", "deckRelationId": 4242, "createdAt": "x"}], "createdCategories": []}""").jsonObject
        assertEquals(4242L, com.mtgtrader.data.DeckEditItems.addedRelation(answer, "u1"))
        assertEquals(null, com.mtgtrader.data.DeckEditItems.addedRelation(answer, "other"))
    }

    @Test
    fun movingKeepsTheOtherCategories() {
        val moved = line(1).inCategory("Mana", maybe = false)
        assertEquals(listOf("Mana", "Staples"), moved.categoryList)
        val maybe = line(1).inCategory(com.mtgtrader.data.DeckEdits.MAYBEBOARD, maybe = true)
        assertTrue(maybe.maybeboard)
        assertEquals("Maybeboard", maybe.category)
    }
}
