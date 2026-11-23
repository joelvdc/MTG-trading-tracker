package com.mtgtrader

import com.mtgtrader.data.BracketAxes
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckSort
import com.mtgtrader.data.DeckSorting
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltCardParser
import com.mtgtrader.data.WinCounted
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinTurns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleZeroCardTest {
    /** Commander Salt's 0–1 ratings and the bars its own cards draw for them (Auntie Plague, Baba, Taranika band). */
    @Test
    fun barsMatchCommanderSaltsCards() {
        val seen = listOf(
            0.384 to 2, 0.223 to 2, 0.286 to 2, 0.869 to 4, 0.361 to 2,
            0.601 to 3, 0.385 to 2, 0.378 to 2, 1.0 to 5, 0.751 to 3,
            0.465 to 2, 0.315 to 2, 0.407 to 2, 0.983 to 5, 0.082 to 2,
        )
        seen.forEach { (rating, bar) -> assertEquals("rating $rating", bar, BracketAxes.level(rating)) }
    }

    @Test
    fun parsesTheCardDetails() {
        val body = """
            {"id": "abc", "powerLevelRating": 4.43, "archetypeLabel": "Combo / Control",
             "cards": {"sol_ring": {"name": "Sol Ring"}, "devoted_druid": {"name": "Devoted Druid"},
                       "mikaeus_the_unhallowed": {"name": "Mikaeus, the Unhallowed"}, "carnifex_demon": {"name": "Carnifex Demon"},
                       "grave_venerations": {"name": "Grave Venerations"}, "nest_of_scarabs": {"name": "Nest of Scarabs"},
                       "field_of_the_dead": {"name": "Field of the Dead"}},
             "details": {
               "brackets": {"csBracket": 2, "wotcBracket": 3,
                 "categories": {"gameChangers": {"list": ["field_of_the_dead"]},
                                "twoCardCombos": {"list": ["devoted_druid_mikaeus_the_unhallowed"]},
                                "extraTurns": {"list": []}},
                 "profile": {"ruleZero": [{"id": "twoCardCombo", "label": "Two-card infinite combo", "why": "named already"},
                                          {"id": "comboRedundancy", "label": "Redundant combo wins", "why": "8 combo wins across 4 lines."}]}},
               "combos": {"list": [{"id": "devoted_druid_mikaeus_the_unhallowed", "cards": ["devoted_druid", "mikaeus_the_unhallowed"]}]},
               "powerLevel": {
                 "ratings": {"inferredTypeLabel": "Casual", "spike": {"consistency": 0.38, "efficiency": 0.22, "interaction": 0.29, "manabase": 0.87, "winConditions": 0.36}},
                 "scoring": {"fastmana": {"list": {"sol_ring": {}}}, "tutors": {"list": {}},
                             "wincon_stompy": {"list": {"carnifex_demon": {}}}, "wincon_burn": {"list": {"grave_venerations": {}}},
                             "wincon_tokens": {"list": {"nest_of_scarabs": {}}}}},
               "salt": {"profile": {"headline": {"saltPercentage": 51.4, "intensityLabel": "High", "dominantFamilyLabel": "Board wipes"}}},
               "manabase": {"percentages": {"curve": 99.85, "manaFixing": 92.997, "quality": 66.19}}}}
        """.trimIndent()
        val card = SaltCardParser.parse(Json.parseToJsonElement(body).jsonObject)
        assertEquals(listOf("Field of the Dead"), card.gameChangers)
        assertEquals(listOf(listOf("Devoted Druid", "Mikaeus, the Unhallowed")), card.twoCardCombos)
        assertEquals(listOf("Redundant combo wins"), card.notes.map { it.label })
        assertEquals(0.87, card.axes["manabase"]!!, 1e-9)
        assertEquals("Casual", card.playStyle)
        assertEquals("High", card.saltIntensity)
        assertEquals(93, card.manaFixing)
        assertEquals(100, card.onCurve)
        assertEquals(66, card.manaQuality)
        assertEquals(mapOf("fastmana" to 1, "tutors" to 0), card.effects)
        // Stompy and burn finishers only, like Commander Salt's own card.
        assertEquals(listOf("Carnifex Demon", "Grave Venerations"), card.wincons)
        // Survives being saved with the deck.
        assertEquals(card, SaltCard.decode(card.encode()))
    }

    @Test
    fun sortingFollowsTheWinTurn() {
        fun deck(name: String) = Deck(name.hashCode().toLong(), name, "", "", null, null, "", 100)
        val decks = listOf(deck("A"), deck("B"), deck("C"))
        // The turn 4 in 10 games are won by (10% by turn 7, 40% by turn 9, 60% by turn 8): as worked out by WinOdds.
        fun odds(byTurn: List<Int>) = WinOdds(10_000, byTurn, byTurn.map { 0 }, emptyMap(), 0, WinCounted())
        WinTurns.put(decks[0].archidektId, odds(listOf(0, 0, 0, 0, 0, 0, 10, 60, 60)))
        WinTurns.put(decks[1].archidektId, odds(listOf(0, 0, 0, 0, 0, 0, 0, 20, 40)))
        WinTurns.put(decks[2].archidektId, null)
        assertEquals(listOf("A", "B", "C"), DeckSorting.sort(decks, DeckSort.WIN_TURN, false).map { it.name })
        // Decks without a win turn go last, whichever way round.
        assertEquals(listOf("B", "A", "C"), DeckSorting.sort(decks, DeckSort.WIN_TURN, true).map { it.name })
        // A setting saved by an older version still means the win turn.
        assertEquals(DeckSort.WIN_TURN, DeckSort.fromKey("POWER"))
    }
}
