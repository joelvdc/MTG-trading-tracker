package com.mtgtrader

import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.LocalRating
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltSum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.115: the deck's salt sum, added up from EDHREC's salt per card as the website does (lands, basics and the commander
 * included, the maybeboard left out), and since 1.116 the stale rating that every deck follows.
 */
class Release115Test {
    // ---- SaltSum.of ------------------------------------------------------------------------------

    private fun card(name: String, quantity: Int = 1, types: String = "Artifact", commander: Boolean = false, maybeboard: Boolean = false) =
        DeckCard(
            deckId = -5, card = CardRef("id-$name", name, "tst", "Test", "1", "rare", null, null, null, null, true, true),
            quantity = quantity, category = "Ramp", types = types, cmc = 1.0, commander = commander, foil = false,
            maybeboard = maybeboard,
        )

    private val scores = mapOf(
        "Sol Ring" to 1.0,
        "Atraxa, Praetors' Voice" to 2.5,
        "Winter Orb" to 3.5,
        "Armageddon" to 2.0,
        "Rhystic Study" to 1.5,
        "Fire" to 0.4,
        "Island" to 0.0,
        "Swamp" to 0.0,
        "Smothering Tithe" to 1.0,
        "Static Orb" to 2.5,
        "Stasis" to 2.5,
        "Grand Abolisher" to 1.0,
        "Tangle Wire" to 0.5,
    )

    @Test
    fun landsBasicsAndTheCommanderCountAndTheMaybeboardDoesNot() {
        val sum = SaltSum.of(
            listOf(
                card("Sol Ring", commander = true),
                card("Island", quantity = 10, types = "Basic Land — Island"),
                card("Winter Orb"),
                card("Armageddon", maybeboard = true),
            ),
            scores,
        )!!
        // 1.0 + 10 × 0.0 + 3.5; the maybeboard's Armageddon stays out.
        assertEquals(4.5, sum.total, 0.0)
        assertEquals("Mild", sum.level)
    }

    @Test
    fun theQuantityCountsForEveryLine() {
        val sum = SaltSum.of(listOf(card("Winter Orb", quantity = 2), card("Armageddon", quantity = 3)), scores)!!
        assertEquals(13.0, sum.total, 0.0)
    }

    @Test
    fun aSplitCardFallsBackToItsFrontFace() {
        // "Fire" is scored; the full name isn't.
        assertEquals(0.4, SaltSum.of(listOf(card("Fire // Ice")), scores)!!.total, 0.0)
        // The full name wins when both are there.
        assertEquals(1.0, SaltSum.of(listOf(card("Fire // Ice")), scores + ("Fire // Ice" to 1.0))!!.total, 0.0)
    }

    @Test
    fun anUnscoredCardAddsNothingAndAWholeDeckOfThemHasNoSum() {
        assertEquals(3.5, SaltSum.of(listOf(card("Winter Orb"), card("Consider")), scores)!!.total, 0.0)
        assertNull(SaltSum.of(listOf(card("Consider"), card("Counterspell")), scores))
        assertNull(SaltSum.of(emptyList(), scores))
        // The maybeboard alone doesn't give a sum either.
        assertNull(SaltSum.of(listOf(card("Winter Orb", maybeboard = true)), scores))
    }

    @Test
    fun theTotalIsRoundedToADecimal() {
        val odd = mapOf("A" to 1.04, "B" to 1.05, "C" to 0.333)
        assertEquals(2.4, SaltSum.of(listOf(card("A"), card("B"), card("C")), odd)!!.total, 0.0)
    }

    @Test
    fun theLevels() {
        assertEquals("Mild", SaltSum.level(29.9))
        assertEquals("Moderate", SaltSum.level(30.0))
        assertEquals("Moderate", SaltSum.level(44.9))
        assertEquals("High", SaltSum.level(45.0))
        assertEquals("Mild", SaltSum.level(0.0))
    }

    @Test
    fun theSaltiestLeaveOutBasicsAndSortHighestFirst() {
        val sum = SaltSum.of(
            listOf(
                card("Island", quantity = 5, types = "Basic Land — Island"),
                card("Sol Ring"),
                card("Stasis"),
                card("Static Orb"),
                card("Winter Orb"),
                card("Armageddon"),
                card("Rhystic Study"),
                card("Smothering Tithe"),
                card("Consider"),
            ),
            scores,
        )!!
        // Winter Orb (3.5) first; ties (Static Orb, Stasis at 2.5) by name; five at most; no Island.
        assertEquals(
            listOf(
                "Winter Orb" to 3.5,
                "Stasis" to 2.5,
                "Static Orb" to 2.5,
                "Armageddon" to 2.0,
                "Rhystic Study" to 1.5,
            ),
            sum.saltiest.map { it.name to it.salt },
        )
        // Basics are left out of the list by their type line, or (snow-covered lands) by their name.
        val snow = SaltSum.of(listOf(card("Snow-Covered Forest", quantity = 4, types = "Land"), card("Winter Orb")), scores + ("Snow-Covered Forest" to 9.0))!!
        assertEquals(listOf("Winter Orb"), snow.saltiest.map { it.name })
    }

    // ---- stale ------------------------------------------------------------------------------------

    private fun deck(local: Boolean = true, saltCard: String? = null, scoredAt: Long = 2_000_000_000_000L) =
        Deck(if (local) -5 else 5, "Brew", "", "Atraxa, Praetors' Voice", null, null, "WUBG", 34, saltCard = saltCard, saltId = if (local) null else "s", scoredAt = scoredAt)

    // A card for the deck's list. It comes after [deck]: the member functions above it aren't set up when it's made.
    private fun listCard(name: String, quantity: Int = 1, commander: Boolean = false) =
        DeckCard(
            deckId = -5, card = CardRef("id-$name", name, "tst", "Test", "1", "rare", null, null, null, null, true, true),
            quantity = quantity, category = "Ramp", types = "Artifact", cmc = 1.0, commander = commander, foil = false,
        )

    private val cards = listOf(listCard("Sol Ring"), listCard("Atraxa, Praetors' Voice", commander = true))

    private fun app(listKey: String? = LocalRating.listKey(cards), ratingVersion: Int? = LocalRating.RATING_VERSION) =
        SaltCard(source = "app", bracket = 3, listKey = listKey, ratingVersion = ratingVersion).encode()

    private val own = SaltCard(gameChangers = listOf("Smothering Tithe"), version = SaltCard.CURRENT).encode()

    @Test
    fun everyDeckFollowsTheListSince116() {
        for (local in listOf(true, false)) {
            assertTrue("no card, local=$local", LocalRating.stale(deck(local = local), cards))
            // Commander Salt's own data is no longer what the cards show: made again.
            assertTrue("Commander Salt's card, local=$local", LocalRating.stale(deck(local = local, saltCard = own), cards))
            // The app's estimate, made for this list and this rating version, is kept.
            assertFalse("a fresh estimate, local=$local", LocalRating.stale(deck(local = local, saltCard = app()), cards))
            assertTrue("another list, local=$local", LocalRating.stale(deck(local = local, saltCard = app(listKey = "00000000")), cards))
            assertTrue("rating version 1, local=$local", LocalRating.stale(deck(local = local, saltCard = app(ratingVersion = 1)), cards))
            assertTrue("no fingerprint kept, local=$local", LocalRating.stale(deck(local = local, saltCard = app(listKey = null)), cards))
            assertTrue("rated before RATED_SINCE, local=$local", LocalRating.stale(deck(local = local, saltCard = app(), scoredAt = LocalRating.RATED_SINCE - 1), cards))
            assertFalse("no cards, local=$local", LocalRating.stale(deck(local = local), emptyList()))
        }
    }
}
