package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.Finish
import com.mtgtrader.data.OtherPrices
import com.mtgtrader.data.PriceSource
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.Pricing
import com.mtgtrader.data.ScryCard
import com.mtgtrader.data.SourcePrice
import com.mtgtrader.data.ValueHistory
import com.mtgtrader.data.readCardKingdom
import com.mtgtrader.data.tcgplayerRows
import com.mtgtrader.ui.priceGaps
import com.mtgtrader.ui.sourceTotals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.29: TCGplayer and Card Kingdom prices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release129Test {
    @After
    fun reset() {
        Pricing.source = PriceSource.CARDMARKET
        Pricing.others = emptyMap()
        Pricing.usdPerEuro = null
    }

    // Card Kingdom's list as it comes (trimmed): a plain card, its foil, The List copy with the same Scryfall id, and odd values.
    private val cardKingdom = """
        {"meta":{"created_at":"2026-10-10 03:05:18","base_url":"https:\/\/www.cardkingdom.com\/"},"data":[
        {"id":20036,"sku":"ICE-061","scryfall_id":"bs","url":"mtg\/ice-age\/brainstorm","name":"Brainstorm","variation":"","edition":"Ice Age","is_foil":"false","price_retail":"2.99","qty_retail":40,"price_buy":"1.00","qty_buying":191,"condition_values":{"nm_price":"2.99","nm_qty":20,"ex_price":"2.39","ex_qty":20,"vg_price":"2.09","vg_qty":0,"g_price":"1.50","g_qty":0}},
        {"id":1,"sku":"MP02-145","scryfall_id":"salvage","url":"mtg\/mystery-booster-the-list\/salvage","name":"Salvage","variation":"Portal II","edition":"Mystery Booster\/The List","is_foil":"false","price_retail":"0.35","price_buy":"0.01","condition_values":{"nm_price":"0.35"}},
        {"id":2,"sku":"P02-145","scryfall_id":"salvage","url":"mtg\/portal-ii\/salvage","name":"Salvage","variation":"","edition":"Portal II","is_foil":"false","price_retail":"0.39","price_buy":"0.02","condition_values":{"nm_price":"0.39"}},
        {"id":3,"sku":"FOO","scryfall_id":"bs","url":"mtg\/foo\/brainstorm-foil","name":"Brainstorm","variation":"","edition":"Foo","is_foil":"true","price_retail":"12.00","price_buy":"0.00","condition_values":null},
        {"id":4,"sku":"X","scryfall_id":null,"url":"mtg\/x","name":"No id","variation":"","edition":"X","is_foil":"false","price_retail":"1.00","price_buy":"0.10"}
        ]}
    """.trimIndent()

    private fun parse(): List<SourcePrice> = runBlocking {
        val out = mutableListOf<SourcePrice>()
        readCardKingdom(cardKingdom.byteInputStream(), 5L) { out += it }
        // The app saves them with REPLACE: the last row for a key wins.
        out.associateBy { Triple(it.scryfallId, it.source, it.finish) }.values.toList()
    }

    @Test
    fun readsCardKingdomsList() {
        val rows = parse()
        assertEquals(3, rows.size)
        val bs = rows.single { it.scryfallId == "bs" && it.finish == "NONFOIL" }
        assertEquals(2.99, bs.price!!, 0.0)
        assertEquals(listOf(2.99, 2.39, 2.09, 1.50), listOf(bs.nm, bs.ex, bs.vg, bs.g))
        assertEquals(1.00, bs.buy!!, 0.0)
        assertEquals("https://www.cardkingdom.com/mtg/ice-age/brainstorm", bs.url)
        val foil = rows.single { it.scryfallId == "bs" && it.finish == "FOIL" }
        assertEquals(12.0, foil.price!!, 0.0)
        assertNull(foil.buy) // Card Kingdom doesn't buy it
        assertNull(foil.ex)
        // The original printing wins over The List's copy that carries its Scryfall id.
        assertEquals(0.39, rows.single { it.scryfallId == "salvage" }.price!!, 0.0)
    }

    private val ref = CardRef("bs", "Brainstorm", "ice", "Ice Age", "61", "common", null, 1, 1.0, null, true, false)

    private fun row(qty: Int, condition: String = "NM", cardmarket: Double = 1.0, id: String = "bs") =
        CollectionRow(CollectionItem(card = ref.copy(scryfallId = id, fallbackEur = cardmarket), foil = false, quantity = qty, condition = condition), null)

    private fun withPrices() {
        Pricing.usdPerEuro = 1.25
        Pricing.others = mapOf(
            "bs" to OtherPrices.of(
                listOf(
                    SourcePrice("bs", "tcgplayer", "NONFOIL", 2.50),
                    SourcePrice("bs", "cardkingdom", "NONFOIL", 3.00, nm = 3.00, ex = 2.50, vg = 2.00, g = 1.25, buy = 1.00),
                ),
            ),
        )
    }

    @Test
    fun pricesFollowTheSourceAndCondition() {
        withPrices()
        val nm = row(1)
        val played = row(1, condition = "EX") // Cardmarket's Excellent is Card Kingdom's EX
        val poor = row(1, condition = "PO")
        assertEquals(1.0, nm.unitPrice(PriceType.TREND)!!, 1e-9)
        assertFalse(nm.isApprox)

        Pricing.source = PriceSource.TCGPLAYER
        assertEquals(2.0, nm.unitPrice(PriceType.TREND)!!, 1e-9) // $2.50 at 1.25 $/€
        assertEquals(2.0, played.unitPrice(PriceType.TREND)!!, 1e-9) // one price for every condition

        Pricing.source = PriceSource.CARD_KINGDOM
        assertEquals(2.4, nm.unitPrice(PriceType.TREND)!!, 1e-9)
        assertEquals(2.0, played.unitPrice(PriceType.TREND)!!, 1e-9)
        assertEquals(1.0, poor.unitPrice(PriceType.TREND)!!, 1e-9)
        assertEquals(0.8, Pricing.cardKingdomPays("bs", Finish.NONFOIL)!!, 1e-9)

        // A card the source has no price for: Cardmarket's, marked.
        val other = row(1, cardmarket = 4.0, id = "unknown")
        assertEquals(4.0, other.unitPrice(PriceType.TREND)!!, 1e-9)
        assertTrue(other.isApprox)
        // No trend arrows while another source is chosen.
        assertNull(nm.trend)

        // Without the exchange rate the dollar prices can't be used yet.
        Pricing.usdPerEuro = null
        assertEquals(1.0, nm.unitPrice(PriceType.TREND)!!, 1e-9)
        assertTrue(nm.isApprox)
    }

    @Test
    fun conditionGrades() {
        assertEquals(listOf("nm", "nm", "ex", "ex", "vg", "g", "g"), listOf("MT", "NM", "EX", "LP", "GD", "PL", "PO").map(Pricing::ckCondition))
    }

    @Test
    fun valueHistoryKeepsEachSource() {
        val rows = listOf(row(2), row(1, cardmarket = 4.0, id = "unknown"))
        // Before any prices: only Cardmarket's values (as before 1.29).
        assertEquals(setOf("trend"), ValueHistory.snapshotValues(rows, emptyList()).keys.filter { !it.startsWith("b:") && it.startsWith("trend") }.toSet())
        withPrices()
        val v = ValueHistory.snapshotValues(rows, emptyList())
        assertEquals(6.0, v["trend"]!!, 1e-9) // Cardmarket: 2 × 1 + 4
        assertEquals(8.0, v["trend@tcgplayer"]!!, 1e-9) // 2 × 2 + Cardmarket's 4 for the unknown card
        assertEquals(8.8, v["trend@cardkingdom"]!!, 1e-9) // 2 × 2.40 + 4
        assertEquals(1.6, v[ValueHistory.CK_PAYS]!!, 1e-9)
        val totals = sourceTotals(rows, PriceType.TREND)
        assertEquals(listOf(3, 2, 2, 2), totals.map { it.covered })
        assertEquals("Card Kingdom would pay", totals.last().label)
    }

    @Test
    fun priceGapsBetweenEuropeAndTheUs() {
        withPrices()
        val rows = listOf(row(3), row(1, cardmarket = 4.0, id = "unknown"))
        val gaps = priceGaps(rows, PriceType.TREND)
        assertEquals(1, gaps.size)
        assertEquals(3.0, gaps.single().difference, 1e-9) // (2 − 1) × 3 copies
    }

    @Test
    fun tcgplayerPricesComeWithScryfallsCards() {
        val card = Json { ignoreUnknownKeys = true }.decodeFromString(
            ScryCard.serializer(),
            """{"id":"bs","name":"Brainstorm","set":"ice","prices":{"usd":"2.54","usd_foil":null,"usd_etched":"9.10","eur":"1.62"},
               "purchase_uris":{"tcgplayer":"https://www.tcgplayer.com/product/1"}}""",
        )
        val rows = tcgplayerRows(card)
        assertEquals(listOf("NONFOIL" to 2.54, "ETCHED" to 9.10), rows.map { it.finish to it.price })
        assertTrue(rows.all { it.source == "tcgplayer" && it.url == "https://www.tcgplayer.com/product/1" })
    }

    @Test
    fun updatingFromVersion12AddsThePriceTable() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath("update-13.db").apply { parentFile?.mkdirs(); delete() }
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/12.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.version = 12
        sql.close()
        // Room checks the result against what this version expects.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "update-13.db").addMigrations(AppDatabase.MIGRATION_12_13).build()
        try {
            db.sourcePriceDao().putAll(listOf(SourcePrice("bs", "tcgplayer", "NONFOIL", 2.5)))
            assertEquals(1, db.sourcePriceDao().count("tcgplayer"))
            assertEquals(1, db.sourcePriceDao().forIds(listOf("bs")).size)
        } finally {
            db.close()
        }
    }
}
