package com.mtgtrader

import com.mtgtrader.data.Balance
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Csv
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.SetIcons
import com.mtgtrader.data.Side
import com.mtgtrader.data.TradeItem
import com.mtgtrader.data.Verdict
import com.mtgtrader.data.parseCondition
import com.mtgtrader.data.parseLanguage
import com.mtgtrader.scan.Box
import com.mtgtrader.scan.CardTextParser
import com.mtgtrader.scan.OcrLine
import com.mtgtrader.scan.ScanGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardTextParserTest {
    // A 960x1280 upright frame; the guide box is where the card sits.
    private val guide: Box = ScanGuide.boxFor(960f, 1280f)

    /** Places a line at fractional card coordinates (0..1) inside the guide. */
    private fun line(text: String, fx: Float, fy: Float, fw: Float = 0.4f, fh: Float = 0.03f): OcrLine {
        val l = guide.left + guide.width * fx
        val t = guide.top + guide.height * fy
        return OcrLine(text, l.toInt(), t.toInt(), (l + guide.width * fw).toInt(), (t + guide.height * fh).toInt())
    }

    @Test
    fun readsNameSetAndSlashNumber() {
        val clues = CardTextParser.parse(
            listOf(
                line("Lightning Bolt", 0.06f, 0.05f),
                line("Instant", 0.06f, 0.56f),
                line("Lightning Bolt deals 3 damage to any target.", 0.06f, 0.65f, 0.8f),
                line("146/280 C", 0.04f, 0.925f, 0.2f, 0.02f),
                line("M11 • EN  Christopher Moeller", 0.04f, 0.95f, 0.45f, 0.02f),
            ),
            guide,
        )
        assertEquals(listOf("Lightning Bolt"), clues.names)
        assertEquals("M11", clues.setCode)
        assertEquals("146", clues.collectorNumber)
        assertEquals("EN", clues.language)
    }

    @Test
    fun readsNewStyleNumberAndIgnoresPowerToughness() {
        val clues = CardTextParser.parse(
            listOf(
                line("Tarmogoyf", 0.06f, 0.05f),
                line("0232 R", 0.04f, 0.925f, 0.15f, 0.02f),
                line("MH2 * DE Justin Murray", 0.04f, 0.95f, 0.4f, 0.02f),
                line("4/5", 0.84f, 0.90f, 0.08f, 0.03f), // P/T box, bottom right
            ),
            guide,
        )
        assertEquals("MH2", clues.setCode)
        assertEquals("232", clues.collectorNumber)
        assertEquals("DE", clues.language)
    }

    /** Lines exactly as ML Kit read them off a German MKM card on the emulator. */
    @Test
    fun realOcrGermanCard() {
        val clues = CardTextParser.parse(
            listOf(
                line("Pollenanalyse", 0.06f, 0.05f),
                line("jene Karte offen vor, nimm sie auf deine Hand und", 0.06f, 0.82f, 0.86f, 0.02f),
                line("R O15o Story-Schlüsselmoment", 0.04f, 0.89f, 0.44f, 0.02f),
                line("MKM •DE ANNA CHRISTENSON", 0.04f, 0.91f, 0.44f, 0.02f),
                line("TM &© 2024 Wizards of the Coast", 0.55f, 0.91f, 0.38f, 0.02f),
            ),
            guide,
        )
        assertEquals("MKM", clues.setCode)
        assertEquals("DE", clues.language)
        assertEquals("150", clues.collectorNumber)
    }

    /** Rules text ending in "between" must not be read as set "BETWE" + language "EN". */
    @Test
    fun realOcrCurrentFrame() {
        val clues = CardTextParser.parse(
            listOf(
                line("Sire of Seven Deaths", 0.06f, 0.05f),
                line("Born of the infinite void between realities, the", 0.06f, 0.81f, 0.88f, 0.02f),
                line("M O001", 0.04f, 0.89f, 0.2f, 0.02f),
                line("FDN • EN >LIUS LASAHIDO", 0.04f, 0.91f, 0.4f, 0.02f),
                line("7/7", 0.8f, 0.87f, 0.06f, 0.03f),
            ),
            guide,
        )
        assertEquals("FDN", clues.setCode)
        assertEquals("1", clues.collectorNumber)
    }

    /** Pre-2014 frames have no set code; the copyright years must not become a collector number. */
    @Test
    fun realOcrOldFrameHasNoPrintingClues() {
        val clues = CardTextParser.parse(
            listOf(
                line("Inferno Titan", 0.06f, 0.05f),
                line("Kev Walker", 0.2f, 0.82f, 0.2f, 0.02f),
                line("a993-2010 Wiards of the Goast LLCH62 49", 0.1f, 0.84f, 0.6f, 0.02f),
            ),
            guide,
        )
        assertEquals(listOf("Inferno Titan"), clues.names)
        assertNull(clues.setCode)
        assertNull(clues.collectorNumber)
    }

    @Test
    fun ignoresTextOutsideTheCard() {
        val clues = CardTextParser.parse(listOf(OcrLine("Playmat Logo", 0, 0, 100, 20)), guide)
        assertEquals(emptyList<String>(), clues.names)
        assertNull(clues.setCode)
    }

    @Test
    fun cleansManaSymbolNoise() {
        assertEquals("Counterspell", CardTextParser.cleanName("Counterspell @@ U"))
        assertEquals("Jace, the Mind Sculptor", CardTextParser.cleanName("Jace, the Mind Sculptor 2UU"))
        assertNull(CardTextParser.cleanName("{2}"))
    }
}

class BalanceTest {
    private val card = CardRef("id", "X", "set", "Set", "1", "rare", null, 1, null, null, true, true)

    private fun item(side: String, price: Double, qty: Int = 1, custom: Double? = null) =
        TradeItem(tradeId = 1, side = side, card = card, foil = false, quantity = qty, prices = PriceSet(trend = price, avg = price * 2), customPrice = custom)

    @Test
    fun fairWithinTolerance() {
        val b = Balance.of(listOf(item(Side.GIVE, 10.0), item(Side.GET, 10.4)), PriceType.TREND, 5)
        assertEquals(Verdict.FAIR, b.verdict)
    }

    @Test
    fun favoursThemWhenYouGiveMore() {
        val b = Balance.of(listOf(item(Side.GIVE, 10.0, qty = 2), item(Side.GET, 12.0)), PriceType.TREND, 5)
        assertEquals(20.0, b.give, 1e-9)
        assertEquals(Verdict.FAVORS_THEM, b.verdict)
        assertEquals(-8.0, b.diff, 1e-9)
    }

    @Test
    fun customPriceAndPriceTypeAreRespected() {
        val items = listOf(item(Side.GIVE, 10.0, custom = 5.0), item(Side.GET, 10.0))
        assertEquals(5.0, Balance.of(items, PriceType.TREND, 5).give, 1e-9)
        assertEquals(20.0, Balance.of(items, PriceType.AVG, 5).get, 1e-9)
        // Missing figure falls back to trend.
        assertEquals(10.0, Balance.of(items, PriceType.LOW, 5).get, 1e-9)
    }
}

class CsvTest {
    @Test
    fun roundTripsQuotedFields() {
        val text = Csv.row("Name", "Set") + "\n" + Csv.row("Borborygmos, \"Enraged\"", "RTR") + "\r\n"
        val rows = Csv.parse(text)
        assertEquals(listOf("Borborygmos, \"Enraged\"", "RTR"), rows[1])
    }

    @Test
    fun mapsConditionsAndLanguages() {
        assertEquals("NM", parseCondition("near_mint"))
        assertEquals("LP", parseCondition("light_played"))
        assertEquals("PL", parseCondition("played"))
        assertEquals("MT", parseCondition("mint"))
        assertEquals("EX", parseCondition("Excellent"))
        assertEquals("JA", parseLanguage("jp"))
        assertEquals("DE", parseLanguage("German"))
    }
}

class SetIconsTest {
    @Test
    fun roundTripsCachedSymbolList() {
        val map = mapOf("mkm" to "https://svgs.scryfall.io/sets/mkm.svg?1706", "pmkm" to "https://svgs.scryfall.io/sets/mkm.svg?1706")
        assertEquals(map, SetIcons.decode(SetIcons.encode(map)))
        // Blank or malformed lines are skipped.
        assertEquals(mapOf("m11" to "u"), SetIcons.decode("\nm11\tu\nbroken\n"))
    }
}
