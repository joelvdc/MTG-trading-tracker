package com.mtgtrader

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardmarketCatalog
import com.mtgtrader.data.CmProduct
import com.mtgtrader.data.CmSetExpansions
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncSchema
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

/**
 * 1.66: the Cardmarket price guide and product list saved in batches, so the collection isn't kept waiting while
 * they're saved. (The app's own power level, which this file tested, left in 1.116.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release166Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase

    /** What the fake Cardmarket answers. */
    private var answer = ""
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(answer.toResponseBody("application/json".toMediaType())).build()
    }.build()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun guide(vararg prices: Pair<Int, Double>) =
        """{"version":1,"createdAt":"2026-10-28T02:00:00+0100","priceGuides":[""" +
            prices.joinToString(",") { (id, trend) -> """{"idProduct":$id,"idCategory":1,"avg":$trend,"low":null,"trend":$trend}""" } + "]}"

    @Test fun thePriceGuideReplacesTheOldPricesBatchByBatch() = runBlocking {
        db.priceDao().insertAll((1..3).map { PriceEntity(it, 1.0, null, 1.0, null, null, null, null, null, null, null, null, null) })
        // More prices than one batch holds.
        answer = guide(*(2..12_001).map { it to 2.5 }.toTypedArray())
        assertTrue(PriceGuideRepository(context, http, db, Settings(context)).refresh())
        val ids = db.priceDao().allIds().toSet()
        assertEquals(12_000, ids.size)
        assertFalse(1 in ids)
        assertEquals(2.5, db.priceDao().get(2)!!.trend!!, 0.001)
        assertEquals(2.5, db.priceDao().get(12_001)!!.trend!!, 0.001)
    }

    @Test fun anEmptyPriceGuideKeepsTheOldPrices() = runBlocking {
        db.priceDao().insertAll(listOf(PriceEntity(1, 1.0, null, 1.0, null, null, null, null, null, null, null, null, null)))
        answer = guide()
        assertFalse(PriceGuideRepository(context, http, db, Settings(context)).refresh())
        assertEquals(listOf(1), db.priceDao().allIds())
    }

    @Test fun theProductListReplacesTheOldOne() = runBlocking {
        db.catalogDao().insertAll(listOf(CmProduct(1, "Old", 10), CmProduct(2, "Kept", 10)))
        db.catalogDao().putSetExpansions(CmSetExpansions("plst", "10"))
        answer = """{"version":1,"createdAt":"x","products":[""" +
            (2..6_001).joinToString(",") { """{"idProduct":$it,"name":"Card $it","idCategory":1,"idExpansion":20}""" } + "]}"
        assertTrue(CardmarketCatalog(context, http, db, Settings(context), ScryfallApi(http)).refresh())
        assertEquals(6_000, db.catalogDao().count())
        assertTrue(db.catalogDao().getMany(listOf(1)).isEmpty())
        assertEquals("Card 2", db.catalogDao().getMany(listOf(2)).single().name)
        assertNull(db.catalogDao().setExpansions("plst"))
    }
}
