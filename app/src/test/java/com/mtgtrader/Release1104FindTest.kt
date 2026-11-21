package com.mtgtrader

import com.mtgtrader.data.FindBinder
import com.mtgtrader.data.FindCards
import com.mtgtrader.data.FindDeck
import com.mtgtrader.data.FindInput
import com.mtgtrader.data.FindLine
import com.mtgtrader.data.FindResult
import com.mtgtrader.data.FindStack
import com.mtgtrader.data.FindWanted
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.104: "Find these cards". The cases in `web/test/fixtures/findcards-cases.json` were worked out by hand from the
 * spec and are the website's too (`web/test/findcards.test.ts`), so both sides pick the same copies.
 */
class Release1104FindTest {
    @Serializable
    private class Wanted(val name: String, val qty: Int, val scryfallId: String? = null)

    @Serializable
    private class Stack(
        val name: String,
        val scryfallId: String,
        val set: String,
        val number: String,
        val foil: Boolean = false,
        val etched: Boolean = false,
        val binderKey: String,
        val binderName: String = "",
        val copies: Int,
    )

    @Serializable
    private class Binder(val key: String, val name: String, val isDeckBinder: Boolean = false, val isTradeBinder: Boolean = false)

    @Serializable
    private class Deck(val id: Long, val name: String, val cards: Map<String, Int>)

    @Serializable
    private class CaseInput(
        val wanted: List<Wanted>,
        val stacks: List<Stack>,
        val binders: List<Binder>,
        val decks: List<Deck> = emptyList(),
        val fromDeck: Long? = null,
        val skipBasics: Boolean = true,
    )

    @Serializable
    private class ExpectedLine(
        val lineKey: String,
        val name: String,
        val copies: Int,
        val set: String,
        val number: String,
        val foil: Boolean,
        val etched: Boolean,
        val wantedPrinting: Boolean,
    )

    @Serializable
    private class ExpectedGroup(val binderKey: String, val binderName: String, val lines: List<ExpectedLine>)

    @Serializable
    private class ExpectedInDeck(val name: String, val copies: Int, val decks: List<String>)

    @Serializable
    private class ExpectedMissing(val name: String, val copies: Int)

    @Serializable
    private class ExpectedTotal(val wanted: Int, val found: Int, val inDecks: Int, val notOwned: Int)

    @Serializable
    private class Expected(
        val groups: List<ExpectedGroup>,
        val onlyInDecks: List<ExpectedInDeck>,
        val notOwned: List<ExpectedMissing>,
        val total: ExpectedTotal,
    )

    @Serializable
    private class FindCase(val name: String, val input: CaseInput, val expected: Expected)

    private val cases = Json { ignoreUnknownKeys = true }
        .decodeFromString<List<FindCase>>(File("../web/test/fixtures/findcards-cases.json").readText())

    private fun run(c: FindCase): FindResult = FindCards.plan(
        FindInput(
            wanted = c.input.wanted.map { FindWanted(it.name, it.qty, it.scryfallId) },
            stacks = c.input.stacks.map {
                FindStack(it.name, it.scryfallId, it.set, it.number, it.foil, it.etched, it.binderKey, it.binderName, it.copies)
            },
            binders = c.input.binders.map { FindBinder(it.key, it.name, it.isDeckBinder, it.isTradeBinder) },
            decks = c.input.decks.map { FindDeck(it.id, it.name, it.cards) },
            fromDeck = c.input.fromDeck,
            skipBasics = c.input.skipBasics,
        ),
    )

    @Test
    fun picksTheCopiesTheSameWayAsTheSharedCases() {
        assertTrue(cases.size >= 8)
        for (c in cases) {
            val r = run(c)
            assertEquals(
                c.name,
                c.expected.groups.map { g -> g.binderKey to g.lines.map { listOf(it.lineKey, it.name, it.copies.toString(), it.set, it.number, it.foil.toString(), it.etched.toString(), it.wantedPrinting.toString()) } },
                r.groups.map { g -> g.binderKey to g.lines.map { listOf(it.lineKey, it.name, it.copies.toString(), it.set, it.number, it.foil.toString(), it.etched.toString(), it.wantedPrinting.toString()) } },
            )
            assertEquals(c.name, c.expected.groups.map { it.binderName }, r.groups.map { it.binderName })
            assertEquals(c.name, c.expected.onlyInDecks.map { it.name to (it.copies to it.decks) }, r.onlyInDecks.map { it.name to (it.copies to it.decks) })
            assertEquals(c.name, c.expected.notOwned.map { it.name to it.copies }, r.notOwned.map { it.name to it.copies })
            assertEquals(c.name, with(c.expected.total) { listOf(wanted, found, inDecks, notOwned) }, with(r.total) { listOf(wanted, found, inDecks, notOwned) })
        }
    }

    @Test
    fun wordsTheResultLikeTheSpecSays() {
        val stack = FindStack("Sol Ring", "s1", "cmr", "401", false, false, "b:a", "Alpha", 1)
        val binders = listOf(FindBinder("unsorted", "Unsorted"), FindBinder("b:a", "Alpha"))
        // The printing the list names is only preferred when it's a printing you own.
        val unknown = FindCards.plan(FindInput(listOf(FindWanted("Sol Ring", 1, "nope")), listOf(stack), binders))
        assertEquals(false, unknown.groups.single().lines.single().wantedPrinting)
        // The key names the printing and the binder: a foil copy is a line of its own.
        val foil = stack.copy(foil = true)
        val both = FindCards.plan(FindInput(listOf(FindWanted("Sol Ring", 2)), listOf(stack, foil), binders))
        assertEquals(listOf("sol ring|s1|false|false|b:a", "sol ring|s1|true|false|b:a"), both.groups.single().lines.map { it.lineKey })
        assertEquals(both.ticks, both.groups.single().lines.map { it.lineKey }.toSet())
        // Copies another deck uses aren't picked; they're only in that deck.
        val other = FindCards.plan(FindInput(listOf(FindWanted("Sol Ring", 1)), listOf(stack), binders, listOf(FindDeck(7, "Auntie Plague", mapOf("sol ring" to 1)))))
        assertEquals(listOf("Sol Ring" to 1), other.onlyInDecks.map { it.name to it.copies })
        assertEquals(emptyList<String>(), other.groups.map { it.binderKey })
    }
}
