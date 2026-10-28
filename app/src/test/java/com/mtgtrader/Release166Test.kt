package com.mtgtrader

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.AppPower
import com.mtgtrader.data.AppPowerLevel
import com.mtgtrader.data.CardmarketCatalog
import com.mtgtrader.data.CmProduct
import com.mtgtrader.data.CmSetExpansions
import com.mtgtrader.data.Deck
import com.mtgtrader.data.PowerSource
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.WinCounted
import com.mtgtrader.data.WinOdds
import com.mtgtrader.ui.Scores
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
 * 1.66: the app's own power level (the win turn plus Commander Salt's ratings, a whole number), and the Cardmarket
 * price guide and product list saved in batches, so the collection isn't kept waiting while they're saved.
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

    private fun odds(vararg byTurn: Int) = WinOdds(10_000, byTurn.toList(), byTurn.map { 0 }, emptyMap(), 0, WinCounted())

    private fun salt(interaction: Double, consistency: Double, efficiency: Double, manabase: Double) = SaltCard(
        axes = mapOf("interaction" to interaction, "consistency" to consistency, "efficiency" to efficiency, "manabase" to manabase, "winConditions" to 0.5),
    )

    @Test fun theTurnHalfTheGamesAreWonBySitsBetweenWholeTurns() {
        // 38% by turn 8, 62% by turn 9: half the games halfway between.
        assertEquals(8.5, AppPowerLevel.turnFor(listOf(0, 0, 0, 0, 0, 0, 10, 38, 62, 90), 50)!!, 0.001)
        assertEquals(9.0, AppPowerLevel.turnFor(listOf(0, 0, 0, 0, 0, 0, 10, 38, 50, 90), 50)!!, 0.001)
        assertEquals(0.5, AppPowerLevel.turnFor(listOf(100), 50)!!, 0.001)
        assertNull(AppPowerLevel.turnFor(listOf(0, 10, 40), 50))
    }

    @Test fun theGuidesTurnScale() {
        assertEquals(10.0, AppPowerLevel.speed(1.0), 0.001)
        assertEquals(9.0, AppPowerLevel.speed(3.0), 0.001)
        assertEquals(8.0, AppPowerLevel.speed(6.0), 0.001)
        assertEquals(7.0, AppPowerLevel.speed(8.0), 0.001)
        assertEquals(6.5, AppPowerLevel.speed(9.25), 0.001)
        assertEquals(6.0, AppPowerLevel.speed(10.5), 0.001)
        assertEquals(5.0, AppPowerLevel.speed(13.0), 0.001)
        assertEquals(4.5, AppPowerLevel.speed(15.0), 0.001)
        assertEquals(10.0, AppPowerLevel.speed(0.4), 0.001)
    }

    @Test fun commanderSaltsRatingsMoveItAtMostOnePoint() {
        // A middling deck: no change.
        assertEquals(0.0, AppPowerLevel.adjust(salt(0.25, 0.45, 0.30, 0.90).axes)!!, 0.001)
        // Council of Four (1.66 comparison): +0.46 interaction, +0.04, +0.03, +0.08.
        assertEquals(0.61, AppPowerLevel.adjust(salt(0.48, 0.49, 0.32, 0.98).axes)!!, 0.01)
        assertEquals(1.0, AppPowerLevel.adjust(salt(1.0, 1.0, 1.0, 1.0).axes)!!, 0.001)
        assertEquals(-1.0, AppPowerLevel.adjust(salt(0.0, 0.0, 0.0, 0.0).axes)!!, 0.001)
        assertNull(AppPowerLevel.adjust(emptyMap()))
    }

    @Test fun thePowerLevelIsAWholeNumber() {
        // Turtle Power (1.66 comparison): half by turn 7.7 → 7.15, Commander Salt −0.5 → 6.6 → 7.
        val turtle = AppPowerLevel.of(odds(0, 0, 0, 0, 0, 1, 19, 61, 91, 98, 99, 99, 99, 99, 99), salt(0.19, 0.42, 0.18, 0.68))
        assertEquals(7.7, turtle.turn!!, 0.05)
        assertEquals(7.1, turtle.speed, 0.05)
        assertEquals(7, turtle.power)
        // Ruby: half by turn 9.5 → 6.4, Commander Salt −0.6 → 5.8 → 6.
        val ruby = AppPowerLevel.of(odds(0, 0, 0, 0, 0, 0, 2, 10, 32, 66, 87, 95, 98, 99, 99), salt(0.13, 0.27, 0.20, 0.84))
        assertEquals(6, ruby.power)
        // Without Commander Salt's ratings: the win turn alone.
        val alone = AppPowerLevel.of(odds(0, 0, 0, 0, 0, 0, 2, 10, 32, 66, 87, 95, 98, 99, 99), null)
        assertNull(alone.adjust)
        assertEquals(6, alone.power)
        // Not half the games by the last turn: by how many are won at all.
        assertEquals(4, AppPowerLevel.of(odds(0, 0, 0, 0, 0, 0, 0, 0, 2, 5, 10, 20, 30, 40, 45), null).power)
        assertEquals(2, AppPowerLevel.of(odds(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3), null).power)
        assertEquals(1, AppPowerLevel.of(odds(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), null).power)
    }

    @Test fun decksShowTheAppsPowerLevelOnceWorkedOut() {
        val deck = Deck(-7166, "Deck", "joelvdc", "Commander", null, null, "G", 100, powerLevel = 4.3)
        assertNull(deck.power(PowerSource.APP))
        assertNull(deck.powerError(PowerSource.APP))
        AppPower.put(deck.archidektId, AppPowerLevel.of(odds(0, 0, 0, 0, 0, 1, 19, 61, 91, 98), null))
        assertEquals(7.0, deck.power(PowerSource.APP)!!, 0.001)
        assertEquals("7", Scores.power(deck.power(PowerSource.APP)!!, PowerSource.APP))
        assertEquals(4.3, deck.power(PowerSource.COMMANDER_SALT)!!, 0.001)
        AppPower.put(deck.archidektId, null)
        assertNull(deck.power(PowerSource.APP))
        assertTrue(deck.powerError(PowerSource.APP) != null)
        // Not one of the sites asked over the internet, but still not Commander Salt's.
        assertFalse(PowerSource.APP.external)
        assertTrue(PowerSource.APP.separate)
        // An older version reading this setting falls back to Commander Salt; this one knows it.
        assertEquals(PowerSource.APP, PowerSource.fromKey("APP"))
    }

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
