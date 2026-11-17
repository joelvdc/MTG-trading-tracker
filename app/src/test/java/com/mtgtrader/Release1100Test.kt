package com.mtgtrader

import com.mtgtrader.data.MdfcLands
import com.mtgtrader.data.StatsCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.100: the Land section's heading counts the modal double-faced lands from the deck's other sections too,
 * the way Archidekt does ("Land (37, 40 with MDFCs)"). `web/src/lib/mdfclands.ts` reads the type lines the
 * same way, so both sides find the same cards.
 */
class Release1100Test {
    private fun card(name: String, typeLine: String?, quantity: Int = 1) = StatsCard(
        name = name,
        scryfallId = name.lowercase(),
        quantity = quantity,
        cmc = 0.0,
        types = typeLine.orEmpty().substringBefore(" — "),
        commander = false,
        typeLine = typeLine,
        manaCost = null,
        producedMana = null,
        oracleText = null,
        tokens = null,
    )

    @Test
    fun aSpellOnTheFrontAndALandOnTheBack() {
        // Sink into Stupor // Soporific Springs: the card the heading is about.
        assertTrue(MdfcLands.backIsLand("Instant // Land"))
        assertTrue(MdfcLands.backIsLand("Instant // Land — Plains"))
        assertTrue(MdfcLands.backIsLand("Creature — Elf // Land"))
        assertTrue(MdfcLands.backIsLand("Legendary Creature — God // Legendary Land"))
    }

    @Test
    fun aLandOnTheFrontIsNotAnMdfcLand() {
        // A land on the front is in the land section already, whatever its other face is.
        assertFalse(MdfcLands.backIsLand("Land"))
        assertFalse(MdfcLands.backIsLand("Land // Land"))
        assertFalse(MdfcLands.backIsLand("Basic Land — Forest"))
        // Neither is a card that's no land at all, or one face only.
        assertFalse(MdfcLands.backIsLand("Sorcery"))
        assertFalse(MdfcLands.backIsLand("Instant // Sorcery"))
    }

    @Test
    fun onlyTheLandSection() {
        assertTrue(MdfcLands.isLandSection("Land"))
        assertTrue(MdfcLands.isLandSection("Lands"))
        assertTrue(MdfcLands.isLandSection(" land "))
        assertTrue(MdfcLands.isLandSection("LANDS"))
        assertFalse(MdfcLands.isLandSection("Creatures"))
        assertFalse(MdfcLands.isLandSection("Land destruction"))
        assertFalse(MdfcLands.isLandSection(""))
    }

    @Test
    fun theCopiesOutsideTheLandSectionAddUp() {
        val deck = listOf(
            card("Sink into Stupor", "Instant // Land", quantity = 2),
            card("Malakir Rebirth", "Instant // Land"),
            card("Forest", "Basic Land — Forest", quantity = 30),
            card("Sol Ring", "Artifact"),
            card("Unknown", null, quantity = 3),
            card("Blank", "", quantity = 5),
        )
        // The two spells' copies: the Forest sits in the land section already, and the two cards without
        // Scryfall's type line are left out.
        assertEquals(3, MdfcLands.copies(deck, setOf("forest")))
        // Without the section given, every MDFC land counts.
        assertEquals(3, MdfcLands.copies(deck))
        // A card the land section counts itself isn't added twice.
        assertEquals(1, MdfcLands.copies(deck, setOf("forest", "sink into stupor")))
        assertEquals(0, MdfcLands.copies(deck, setOf("forest", "sink into stupor", "malakir rebirth")))
        assertEquals(0, MdfcLands.copies(emptyList()))
    }
}
