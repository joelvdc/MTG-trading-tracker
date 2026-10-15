package com.mtgtrader

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.MtgRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 1.36: ticking "Ordered" straight from the wishlist, and undoing it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release136Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var repo: MtgRepository
    private val ref = CardRef("vff", "Venser, Fervent Forger", "frc", "Final Fantasy Commander", "9", "rare", null, 1, 0.02, null, true, false)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        val http = OkHttpClient()
        repo = MtgRepository(db, ScryfallApi(http), PriceGuideRepository(context, http, db, Settings(context)), CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun tickingFromTheListIsAChangeToSyncAndUndoPutsItBack() = runBlocking {
        val id = db.wishlistDao().insert(WishlistItem(card = ref, notes = "for Venser's deck"))
        val added = db.wishlistDao().byId(id)!!
        assertNull(added.orderedAt)

        repo.setWishlistOrdered(id, 1_792_065_600_000L)
        val ticked = db.wishlistDao().byId(id)!!
        assertEquals(1_792_065_600_000L, ticked.orderedAt)
        assertEquals("for Venser's deck", ticked.notes)
        assertTrue(ticked.updatedAt > added.updatedAt)

        // Undo: back to how it was, and stamped again so the other phone hears of it too.
        repo.setWishlistOrdered(id, added.orderedAt)
        val undone = db.wishlistDao().byId(id)!!
        assertNull(undone.orderedAt)
        assertTrue(undone.updatedAt > ticked.updatedAt)

        // Ticking a card that has gone (taken off on the other phone) does nothing.
        db.wishlistDao().delete(id)
        repo.setWishlistOrdered(id, 1L)
        assertNull(db.wishlistDao().byId(id))
    }
}
