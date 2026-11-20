package com.mtgtrader

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.DisplayCurrency
import com.mtgtrader.data.ExchangeRates
import com.mtgtrader.data.Money
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.OrderedCard
import com.mtgtrader.data.Orders
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.PurchaseOrder
import com.mtgtrader.data.ScannedCard
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.TypedPrice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 1.103: scanned cards get a purchase price (database 31, sync format 16), and each card's orders can be looked up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release1103Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var repo: MtgRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        val http = OkHttpClient()
        repo = MtgRepository(db, ScryfallApi(http), PriceGuideRepository(context, http, db, Settings(context)), CoroutineScope(Dispatchers.Unconfined))
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        Money.display = DisplayCurrency()
        Money.rates = RATES
    }

    @After
    fun tearDown() {
        db.close()
        Money.display = DisplayCurrency()
        Money.rates = null
    }

    private fun ref(name: String = "Sol Ring", id: String = name.lowercase().replace(' ', '-')) =
        CardRef(id, name, "cmm", "Commander Masters", "400", "uncommon", null, null, 1.0, 2.0, true, true)

    private fun scan(qty: Int = 1, price: Double? = null, name: String = "Sol Ring", id: String = name.lowercase().replace(' ', '-')) =
        if (price == null) ScannedCard(card = ref(name, id), foil = false, quantity = qty)
        else ScannedCard(card = ref(name, id), foil = false, quantity = qty).withPaid(price)

    private fun order(date: String, vararg lines: OrderedCard) =
        Orders.of("Cardmarket", "o-$date-${lines.size}", "Cardmarket shipment", date, lines.toList())

    private fun line(id: String?, qty: Int = 1, price: Double? = 1.0, foil: Boolean = false, condition: String? = "NM") =
        OrderedCard(name = "Sol Ring", setCode = "cmm", number = "400", quantity = qty, price = price, foil = foil, condition = condition, scryfallId = id)

    // ---- Orders.hits --------------------------------------------------------------------------

    @Test
    fun hitsFindThePrintingInItsFinish() {
        val sol = "sol"
        val orders = listOf(
            order("2026-01-10", line(sol, foil = true)),
            order("2026-02-10", line(sol)),
            order("2026-03-10", line("other")),
        )
        val plain = Orders.hits(orders, sol, foil = false)
        assertEquals(listOf("2026-02-10"), plain.map { it.order.date })
        assertEquals(1, plain.single().quantity)
        assertEquals(1.0, plain.single().price!!, 1e-9)
        assertTrue(plain.single().finishKnown)
        assertEquals(listOf("2026-01-10"), Orders.hits(orders, sol, foil = true).map { it.order.date })
        assertTrue(Orders.hits(orders, "unknown", foil = false).isEmpty())
        assertTrue(Orders.hits(orders, sol, foil = true).single().order.number != plain.single().order.number)
    }

    @Test
    fun aLineWithoutFinishCountsForBothAndIsNotKnown() {
        // Cardmarket's monthly file leaves the condition null, so it doesn't say foil or not.
        val monthly = order("2026-01-10", line("sol", condition = null))
        for (foil in listOf(false, true)) {
            val hit = Orders.hits(listOf(monthly), "sol", foil).single()
            assertFalse(hit.finishKnown)
        }
        // A known non-foil line and an unknown one in the same order: still not known.
        val mixed = order("2026-02-10", line("sol", foil = false), line("sol", condition = null))
        val hit = Orders.hits(listOf(mixed), "sol", foil = false).single()
        assertEquals(2, hit.quantity)
        assertFalse(hit.finishKnown)
    }

    @Test
    fun linesOfOneOrderAddUpWithTheWeightedAveragePrice() {
        val twoOrders = listOf(
            order("2026-01-10", line("sol", qty = 1, price = 2.0)),
            order("2026-02-10", line("sol", qty = 1, price = 2.0), line("sol", qty = 2, price = 5.0)),
        )
        val hits = Orders.hits(twoOrders, "sol", foil = false)
        // Newest order first.
        assertEquals(listOf("2026-02-10", "2026-01-10"), hits.map { it.order.date })
        assertEquals(3, hits[0].quantity)
        assertEquals((2.0 + 2 * 5.0) / 3, hits[0].price!!, 1e-9)
        assertEquals(1, hits[1].quantity)
        // Without a price on any line there's none.
        val unpriced = Orders.hits(listOf(order("2026-03-10", line("sol", price = null))), "sol", foil = false).single()
        assertNull(unpriced.price)
    }

    @Test
    fun ordersOfTheSameDayComeNewestFirst() {
        val older = order("2026-02-10", line("sol")).let { it.copy(id = 1, number = "old") }
        val newer = order("2026-02-10", line("sol")).let { it.copy(id = 2, number = "new") }
        assertEquals(listOf("new", "old"), Orders.hits(listOf(older, newer), "sol", foil = false).map { it.order.number })
    }

    // ---- updateScan ---------------------------------------------------------------------------

    @Test
    fun mergingAnEditedScanAveragesThePrice() = runBlocking {
        val first = db.scanDao().insert(scan(qty = 1, price = 2.0))
        val second = db.scanDao().insert(scan(qty = 1, price = 4.0))
        repo.updateScan(db.scanDao().byId(second)!!)
        val merged = db.scanDao().byId(first)!!
        assertEquals(2, merged.quantity)
        assertEquals(3.0, merged.purchasePrice!!, 1e-9)
        assertNull(db.scanDao().byId(second))
    }

    @Test
    fun mergingWithoutAPriceOnOneSideKeepsTheOther() = runBlocking {
        val first = db.scanDao().insert(scan(qty = 2))
        val second = db.scanDao().insert(scan(qty = 1, price = 4.0))
        repo.updateScan(db.scanDao().byId(second)!!)
        val merged = db.scanDao().byId(first)!!
        assertEquals(3, merged.quantity)
        assertEquals(4.0, merged.purchasePrice!!, 1e-9)

        // And the other way round: the one saved keeps its price when the one merged in hasn't one.
        val a = db.scanDao().insert(scan(qty = 1, price = 6.0, name = "Arcane Signet"))
        val b = db.scanDao().insert(scan(qty = 1, name = "Arcane Signet"))
        repo.updateScan(db.scanDao().byId(b)!!)
        val signet = db.scanDao().byId(a)!!
        assertEquals(2, signet.quantity)
        assertEquals(6.0, signet.purchasePrice!!, 1e-9)
    }

    // ---- scansToCollection --------------------------------------------------------------------

    @Test
    fun aScannedCardsPriceGoesIntoTheCollectionAndTheUndoBringsBothBack() = runBlocking {
        val stack = repo.addToCollection(ref(), com.mtgtrader.data.Finish.NONFOIL, "NM", "EN", 1).itemId
        db.collectionDao().update(db.collectionDao().byId(stack)!!.withPaid(4.0))
        val id = db.scanDao().insert(scan(qty = 1, price = 2.0))

        val undo = repo.scansToCollection(listOf(id), Binder.UNSORTED, keep = false)
        val joined = db.collectionDao().byId(stack)!!
        assertEquals(2, joined.quantity)
        assertEquals(3.0, joined.purchasePrice!!, 1e-9)
        assertNull("the scan left the list", db.scanDao().byId(id))

        undo()
        val back = db.collectionDao().byId(stack)!!
        assertEquals(1, back.quantity)
        assertEquals(4.0, back.purchasePrice!!, 1e-9)
        val scanBack = db.scanDao().byId(id)!!
        assertEquals(1, scanBack.quantity)
        assertEquals(2.0, scanBack.purchasePrice!!, 1e-9)
    }

    @Test
    fun aNewStackFromAScanKeepsItsPriceAndTheUndoRemovesIt() = runBlocking {
        val id = db.scanDao().insert(scan(qty = 2, price = 3.5, name = "Arcane Signet"))
        val undo = repo.scansToCollection(listOf(id), Binder.UNSORTED, keep = true)
        val stack = db.collectionDao().all().single { it.card.name == "Arcane Signet" }
        assertEquals(2, stack.quantity)
        assertEquals(3.5, stack.purchasePrice!!, 1e-9)
        assertTrue("kept", db.scanDao().byId(id) != null)
        undo()
        assertTrue(db.collectionDao().all().none { it.card.name == "Arcane Signet" })
        assertTrue(db.scanDao().byId(id) != null)
    }

    // ---- Sync ---------------------------------------------------------------------------------

    @Test
    fun aScansPriceRoundTripsThroughTheSyncFile() {
        val typed = TypedPrice(30.0, AppCurrency.DKK)
        val scanned = scan(qty = 1, price = 4.0).withPaid(4.0, typed)
        val file = SyncFile(data = SyncData(scans = listOf(scanned)))
        val back = SyncFile.decode(SyncFile.encode(file)).data.scans.single()
        assertEquals(4.0, back.purchasePrice!!, 1e-9)
        assertEquals(typed, back.typedPaid)
        assertNull(scan(qty = 1, price = 4.0).withPaid(4.0).typedPaid)
        // Saved fields: the amount and currency as typed.
        val json = Json { encodeDefaults = true }.encodeToString(ListSerializer(ScannedCard.serializer()), listOf(scanned))
        assertTrue(json, "\"purchasePrice\":4.0" in json)
        assertTrue(json, "\"paidTyped\":30.0" in json)
        assertTrue(json, "\"paidCurrency\":\"DKK\"" in json)
        // Older versions would write the price back without, so they stop syncing until updated.
        assertEquals(16, SyncFile.FORMAT)
    }

    @Test
    fun aScanWithoutAPriceHasNoneAsTyped() {
        assertNull(scan(qty = 1).typedPaid)
        // Clearing the amount clears what was typed with it.
        val cleared = scan(qty = 1, price = 2.0).withPaid(2.0, TypedPrice(15.0, AppCurrency.DKK)).withPaid(null)
        assertNull(cleared.purchasePrice)
        assertNull(cleared.paidTyped)
        assertNull(cleared.paidCurrency)
    }

    private companion object {
        val RATES = ExchangeRates("2026-11-20", mapOf("DKK" to 7.46, "USD" to 1.08))
    }
}
