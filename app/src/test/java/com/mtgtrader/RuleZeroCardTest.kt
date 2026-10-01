package com.mtgtrader

import com.mtgtrader.data.BracketAxes
import com.mtgtrader.data.CommanderSaltApi
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckSort
import com.mtgtrader.data.DeckSorting
import com.mtgtrader.data.EdhPowerLevelLink
import com.mtgtrader.data.EdhReading
import com.mtgtrader.data.PowerSource
import com.mtgtrader.data.SaltCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val card = CommanderSaltApi.parse(body).card!!
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
    fun edhPowerLevelLinkUsesFrontFacesAndSections() {
        val url = EdhPowerLevelLink.url(listOf("Auntie Ool, Cursewretch"), listOf("Malakir Rebirth // Malakir Mire" to 1, "Swamp" to 5))
        assertEquals(
            "https://edhpowerlevel.com/?d=Commander~1+Auntie+Ool%2C+Cursewretch~~Mainboard~1+Malakir+Rebirth~5+Swamp~Z~",
            url,
        )
    }

    @Test
    fun readsThePowerLevelOffThePage() {
        val page = "Reset All\nAuntie Ool, Cursewretch\n100 total cards imported.\n⚡\nPower Level\n6.70 / 10\n⚖️\nTipping Point\n4"
        assertEquals(EdhReading(6.70, 100), EdhPowerLevelLink.parse(page))
        assertNull(EdhPowerLevelLink.parse("Scan your deck\nAnalyze List"))
    }

    @Test
    fun sortingFollowsThePowerSource() {
        fun deck(name: String, salt: Double?, edh: Double?) =
            Deck(name.hashCode().toLong(), name, "", "", null, null, "", 100, powerLevel = salt, edhPowerLevel = edh)
        val decks = listOf(deck("A", 4.4, 6.7), deck("B", 6.4, 5.1), deck("C", 5.4, null))
        assertEquals(listOf("B", "C", "A"), DeckSorting.sort(decks, DeckSort.POWER, false, PowerSource.COMMANDER_SALT).map { it.name })
        // Decks edhpowerlevel.com hasn't rated yet go last.
        assertEquals(listOf("A", "B", "C"), DeckSorting.sort(decks, DeckSort.POWER, false, PowerSource.EDH_POWER_LEVEL).map { it.name })
    }
}
