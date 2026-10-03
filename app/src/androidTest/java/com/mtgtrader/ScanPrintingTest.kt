package com.mtgtrader

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.Finish
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Choosing another printing for a card the scanner added (it guessed the set from the name). */
@RunWith(AndroidJUnit4::class)
class ScanPrintingTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: MtgRepository

    private fun printing(id: String, set: String, foil: Boolean = true) = CardRef(
        scryfallId = id, name = "Sol Ring", setCode = set, setName = set.uppercase(), collectorNumber = "1", rarity = "uncommon",
        imageUrl = null, cardmarketId = null, fallbackEur = 1.0, fallbackEurFoil = 2.0, hasNonFoil = true, hasFoil = foil,
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val http = OkHttpClient()
        repo = MtgRepository(db, ScryfallApi(http), PriceGuideRepository(context, http, db, Settings(context)), CoroutineScope(Dispatchers.Default))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun stacks() = db.collectionDao().all().associate { it.card.setCode to it.quantity }

    @Test
    fun swapsOnlyTheScannedCopy() = runBlocking {
        val target = CardTarget.Collection(Binder.UNSORTED)
        val guessed = printing("a", "c21")
        // Two copies of the guessed printing were already owned; the scan added a third.
        repo.addToCollection(guessed, Finish.NONFOIL, "NM", "EN", 2, Binder.UNSORTED)
        val scanned = repo.add(target, guessed, Finish.NONFOIL, "EN", exactPrinting = false)!!
        assertEquals(mapOf("c21" to 3), stacks())

        repo.changeAddedPrinting(scanned, target, printing("b", "lea"), Finish.NONFOIL, "EN")
        assertEquals(mapOf("c21" to 2, "lea" to 1), stacks())
    }

    @Test
    fun keepsTheFinishWhenThePrintingHasIt() = runBlocking {
        val target = CardTarget.Scans
        val scanned = repo.add(target, printing("a", "c21"), Finish.FOIL, "EN", exactPrinting = false)!!
        val r = repo.changeAddedPrinting(scanned, target, printing("b", "cmm"), Finish.FOIL, "EN")!!
        val scan = db.scanDao().byId(r.itemId)!!
        assertEquals("cmm", scan.card.setCode)
        assertEquals(true, scan.foil)
        assertEquals(true, scan.exactPrinting)
        // A printing without foil gets its normal finish.
        val r2 = repo.changeAddedPrinting(r, target, printing("c", "lea", foil = false), Finish.FOIL, "EN")!!
        assertEquals(false, db.scanDao().byId(r2.itemId)!!.foil)
    }
}
