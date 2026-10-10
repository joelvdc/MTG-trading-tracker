package com.mtgtrader

import com.mtgtrader.data.CardTraderOrder
import com.mtgtrader.data.OrderCandidate
import com.mtgtrader.data.Spreadsheet
import com.mtgtrader.ui.importSummary
import com.mtgtrader.ui.suggestedBinderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class CardTraderImportTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream("cardtrader/$name")!!.readBytes()

    private val header = listOf(
        "Game", "Set Released At", "Set Name", "Set Code", "Item Name", "Price in EUR Cents", "Quantity", "Condition",
        "Language", "Foil/Reverse", "Signed", "Altered", "First Edition", "Collector Number",
    )

    @Test fun readsOldExcel() {
        val rows = Spreadsheet.read(resource("order_sample.xls"))
        assertEquals(header, rows[0])
        assertEquals(11, rows.size)
        assertEquals(listOf("Magic: the Gathering", "2026-04-24", "Secrets of Strixhaven", "SOS", "Echocasting Symposium", "231", "1", "Near Mint", "en", "0", "0", "0", "", "044"), rows[1])
        assertEquals("T 04/02", rows[4][13])
    }

    @Test fun readsNewExcelTheSameWay() {
        assertEquals(Spreadsheet.read(resource("order_sample.xls")), Spreadsheet.read(resource("order_sample.xlsx")))
    }

    @Test fun readsStringsSpreadOverSeveralRecords() {
        // 600 long names with "ó" and "☆": the shared-string table runs over several CONTINUE records.
        val rows = Spreadsheet.read(resource("big.xls"))
        assertEquals(601, rows.size)
        for (i in 1..600) {
            assertEquals(("Lórien Revealed ☆ %04d ".format(i) + "x".repeat(i % 37)).trim(), rows[i][0])
            assertEquals("$i", rows[i][1])
        }
    }

    @Test fun readsCsvToo() {
        val rows = Spreadsheet.read("Item Name,Set Code,Collector Number\nBrainstorm,ICE,61\n".toByteArray())
        assertEquals(listOf("Brainstorm", "ICE", "61"), rows[1])
    }

    @Test fun parsesAnOrder() {
        val lines = CardTraderOrder.parse(Spreadsheet.read(resource("order_sample.xls")))
        // The two Clifftop Lookout rows are one line of 2.
        assertEquals(9, lines.size)
        val lookout = lines.single { it.name == "Clifftop Lookout" }
        assertEquals(2, lookout.quantity)
        assertEquals(0.24, lookout.price!!, 1e-9)
        val decorum = lines.single { it.name == "Decorum Dissertation" }
        assertTrue(decorum.foil)
        assertEquals("EX", decorum.condition)
        val island = lines.single { it.name == "Island" }
        assertEquals(4, island.quantity)
        assertEquals("JA", island.language)
        assertTrue(island.signed)
        val fist = lines.single { it.name == "Fist of Suns" }
        assertTrue(fist.altered)
        assertEquals("DE", fist.language)
        assertEquals("PO", fist.condition)
    }

    @Test fun mapsConditionsAndNumbers() {
        assertEquals("NM", CardTraderOrder.condition("Near Mint"))
        assertEquals("EX", CardTraderOrder.condition("Slightly Played"))
        assertEquals("LP", CardTraderOrder.condition("Moderately Played"))
        assertEquals("PL", CardTraderOrder.condition("Played"))
        assertEquals("PO", CardTraderOrder.condition("Heavily Played"))
        assertEquals("44", CardTraderOrder.cleanNumber("044"))
        assertEquals("0", CardTraderOrder.cleanNumber("0"))
        assertEquals("4", CardTraderOrder.tokenNumber("T 04/02"))
        assertEquals("16", CardTraderOrder.tokenNumber("T16/12"))
        assertNull(CardTraderOrder.tokenNumber("168"))
        assertEquals("Qarsi Revenant", CardTraderOrder.cleanName("Qarsi Revenant (Borderless)"))
        assertEquals("tdm", CardTraderOrder.alternativeSet("CTDM"))
        assertNull(CardTraderOrder.alternativeSet("SOS"))
    }

    @Test fun firstLookupUsesTheTokenSet() {
        val lines = CardTraderOrder.parse(Spreadsheet.read(resource("order_sample.xls")))
        assertEquals("teoe" to "4", CardTraderOrder.firstLookup(lines.single { it.name.startsWith("Lander") }))
        assertEquals("sos" to "44", CardTraderOrder.firstLookup(lines.single { it.name == "Echocasting Symposium" }))
    }

    @Test fun rejectsOtherSheets() {
        var failed = false
        try { CardTraderOrder.parse(listOf(listOf("Name", "Qty"), listOf("Brainstorm", "1"))) } catch (e: CardTraderOrder.NotAnOrder) { failed = true }
        assertTrue(failed)
        assertFalse(CardTraderOrder.parse(Spreadsheet.read(resource("order_sample.xlsx"))).isEmpty())
    }

    /** A real order export, when one is given (it isn't kept in the repository): CARDTRADER_ORDER=/path/to/order.xls */
    @Test fun readsARealOrder() {
        val path = System.getenv("CARDTRADER_ORDER")
        assumeTrue(path != null)
        val lines = CardTraderOrder.parse(Spreadsheet.read(File(path!!).readBytes()))
        assertTrue(lines.isNotEmpty())
        println("Real order: ${lines.size} lines, ${lines.sumOf { it.quantity }} cards")
        lines.forEach { println("  $it") }
    }

    @Test fun summaryAndBinderName() {
        val lines = CardTraderOrder.parse(Spreadsheet.read(resource("order_sample.xls")))
        val card = com.mtgtrader.data.CardRef("x", "X", "x", "X", "1", "rare", null, null, null, null, true, true)
        val cands = lines.mapIndexed { i, l ->
            OrderCandidate(i, l, if (l.name == "Fist of Suns") null else card, token = l.number.startsWith("T"), basicLand = l.name == "Island")
        }
        val checked = cands.filter { !it.token && !it.basicLand && it.card != null }.map { it.index }.toSet()
        assertEquals("Added 6 cards to The Box · 1 token left out · 4 basic lands left out · 2 not found", importSummary(6, "The Box", cands, checked))
        assertEquals("Added 1 card to Unsorted · 1 token left out · 2 not found · 4 marked signed/altered",
            importSummary(1, "Unsorted", cands, checked + cands.single { it.line.name == "Island" }.index))
        assertTrue(suggestedBinderName("cardtrader_order_20260623hxrahx.xls").startsWith("CardTrader 23 Jun"))
        assertTrue(suggestedBinderName(null).startsWith("CardTrader "))
    }
}
