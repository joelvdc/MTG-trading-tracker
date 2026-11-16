package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The deck filter against the cases shared with the website (`web/test/cardquery.test.ts`). */
class CardQueryTest {
    @Serializable
    private class FilterCase(
        val query: String,
        /** The names of the cards it matches, in the cards' order. */
        val matches: List<String>? = null,
        val error: String? = null,
        /** The keys only Scryfall can answer. */
        val unsupported: List<String>? = null,
    )

    @Serializable
    private class Cases(val cards: List<QueryCard>, val cases: List<FilterCase>)

    private val cases = Json { ignoreUnknownKeys = true }
        .decodeFromString<Cases>(File("../web/test/fixtures/cardquery-cases.json").readText())

    @Test
    fun checksEverySharedCase() {
        assertTrue(cases.cards.isNotEmpty())
        assertTrue(cases.cases.isNotEmpty())
        for (c in cases.cases) {
            val about = "filter \"${c.query}\""
            val p = CardQuery.parse(c.query)
            if (c.error != null) {
                assertTrue("$about should fail", p is CardQuery.Error)
                assertEquals(about, c.error, (p as CardQuery.Error).error)
                continue
            }
            assertTrue("$about should parse", p is CardQuery.Query)
            val q = p as CardQuery.Query
            if (c.unsupported != null) assertEquals(about, c.unsupported, CardQuery.unsupportedKeys(q))
            if (c.matches != null) assertEquals(about, c.matches, cases.cards.filter { CardQuery.matches(q, it) }.map { it.name })
        }
    }
}
