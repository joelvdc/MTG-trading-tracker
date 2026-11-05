package com.mtgtrader

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Finish
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.PendingCard
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.WishlistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** "Change printing" in the wishlist card window (unreleased; next version). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WishlistPrintingTest {
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
    }

    @After
    fun tearDown() = db.close()

    private fun ref(name: String, id: String, set: String, nonFoil: Boolean = true, foil: Boolean = true) =
        CardRef(id, name, set, set.uppercase(), "1", "rare", null, null, 1.0, 2.0, nonFoil, foil)

    @Test
    fun anExactPrintingCountsGotSinceAddingFromTheNewPrinting() = runBlocking {
        val m21 = ref("Sol Ring", "sol-m21", "m21")
        val c21 = ref("Sol Ring", "sol-c21", "c21")
        repo.addToCollection(c21, Finish.NONFOIL, "NM", "EN", 2)
        val id = db.wishlistDao().insert(WishlistItem(card = m21, anyPrinting = false, quantity = 3, notes = "for Atraxa"))
        val before = db.wishlistDao().byId(id)!!

        repo.updateWishlistItem(before.copy(card = c21))
        val after = db.wishlistDao().byId(id)!!
        assertEquals("sol-c21", after.card.scryfallId)
        assertEquals(2, after.ownedAtAdd)
        assertEquals(3, after.quantity)
        assertEquals("for Atraxa", after.notes)
        assertEquals(before.uid, after.uid)
        assertTrue(after.updatedAt >= before.updatedAt)
        assertEquals(0, repo.wishlistOwned(listOf(after), db.collectionDao().all())[id]!!.gotSince)
    }

    @Test
    fun anyPrintingKeepsCountingByName() = runBlocking {
        repo.addToCollection(ref("Sol Ring", "sol-c21", "c21"), Finish.NONFOIL, "NM", "EN", 1)
        val id = db.wishlistDao().insert(WishlistItem(card = ref("Sol Ring", "sol-m21", "m21"), anyPrinting = true, ownedAtAdd = 0))
        repo.updateWishlistItem(db.wishlistDao().byId(id)!!.copy(card = ref("Sol Ring", "sol-cmr", "cmr")))
        // The copy got since adding still counts.
        assertEquals(0, db.wishlistDao().byId(id)!!.ownedAtAdd)
    }

    @Test
    fun aCardAddedOfflineCountsAgainByItsRealName() = runBlocking {
        repo.addToCollection(ref("Brainstorm", "bs-ice", "ice"), Finish.NONFOIL, "NM", "EN", 1)
        val id = db.wishlistDao().insert(WishlistItem(card = PendingCard.ref("brainstorm"), ownedAtAdd = 0))
        repo.updateWishlistItem(db.wishlistDao().byId(id)!!.copy(card = ref("Brainstorm", "bs-ice", "ice")))
        assertEquals(1, db.wishlistDao().byId(id)!!.ownedAtAdd)
    }

    @Test
    fun theFinishFollowsWhatTheNewPrintingHas() {
        val nonFoilOnly = ref("Sol Ring", "sol-c21", "c21", foil = false)
        val foilOnly = ref("Sol Ring", "sol-sld", "sld", nonFoil = false)
        assertEquals(Finish.NONFOIL, nonFoilOnly.resolveFinish(Finish.FOIL))
        assertEquals(Finish.FOIL, foilOnly.resolveFinish(Finish.NONFOIL))
        assertEquals(Finish.FOIL, ref("Sol Ring", "sol-m21", "m21").resolveFinish(Finish.FOIL))
    }
}
