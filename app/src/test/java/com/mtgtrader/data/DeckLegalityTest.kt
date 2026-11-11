package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Commander legality check against the cases shared with the website (`web/test/legality.test.ts`). */
class DeckLegalityTest {
    @Serializable
    private class CardCase(val scryfall: ScryRuleCard, val rule: LegalRule)

    @Serializable
    private class ExpectedProblem(val kind: String, val cards: List<String>)

    @Serializable
    private class DeckCase(val about: String, val lines: List<LegalLine>, val problems: List<ExpectedProblem>, val unchecked: List<String>)

    @Serializable
    private class Cases(val cards: List<CardCase>, val rules: Map<String, LegalRule>, val decks: List<DeckCase>)

    private val cases = Json { ignoreUnknownKeys = true }
        .decodeFromString<Cases>(File("../web/test/fixtures/legality-cases.json").readText())

    @Test
    fun readsTheRulesOfEveryCard() {
        assertTrue(cases.cards.isNotEmpty())
        for (c in cases.cards) assertEquals("rules of ${c.scryfall.name}", c.rule, DeckLegality.ruleOf(c.scryfall))
    }

    @Test
    fun checksEveryDeck() {
        assertTrue(cases.decks.isNotEmpty())
        for (d in cases.decks) {
            val r = DeckLegality.checkLegality(d.lines, cases.rules)
            assertEquals(d.about, d.problems.map { it.kind to it.cards }, r.problems.map { it.kind.id to it.cards })
            assertEquals(d.about, d.unchecked, r.unchecked)
            for (p in r.problems) assertTrue(p.text.isNotEmpty())
        }
    }

    @Test
    fun wordsTheProblemsForTheDeckPage() {
        val r = DeckLegality.checkLegality(cases.decks[2].lines, cases.rules)
        assertEquals(
            listOf(
                "Banned in Commander: Black Lotus.",
                "Not legal in Commander: Chaos Orb.",
                "Outside the commander's colours: Lightning Bolt, Mountain.",
                "More copies than allowed: Sol Ring ×2.",
            ),
            r.problems.map { it.text },
        )
    }
}
