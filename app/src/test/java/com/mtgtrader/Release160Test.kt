package com.mtgtrader

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.Binder
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.DisplayCurrency
import com.mtgtrader.data.ExchangeRates
import com.mtgtrader.data.Finish
import com.mtgtrader.data.Marks
import com.mtgtrader.data.Money
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.OrderLine
import com.mtgtrader.data.Precons
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.PurchasePrices
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.SyncStack
import com.mtgtrader.data.TradeItem
import com.mtgtrader.data.TypedPrice
import com.mtgtrader.data.WishlistItem
import com.mtgtrader.ui.MoneyInput
import com.mtgtrader.ui.WishToCollection
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

/** 1.60: purchase prices (and agreed trade prices) remember the currency they were typed in; they're still kept in euros. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release160Test {
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
        Money.rates = RATES
    }

    @After
    fun tearDown() {
        db.close()
        Money.display = DisplayCurrency()
        Money.rates = null
    }

    private fun ref(name: String, id: String = name.lowercase().replace(' ', '-'), set: String = "m21") =
        CardRef(id, name, set, set.uppercase(), "1", "rare", null, null, 1.0, 2.0, true, true)

    private fun showKroner(perEuro: Double = 7.46) {
        Money.display = DisplayCurrency(AppCurrency.DKK, perEuro)
    }

    // ---- Typing and showing a price ----------------------------------------------------------

    @Test
    fun aPriceTypedInKronerIsKeptInEurosAndShownBackAsTyped() {
        showKroner()
        val input = MoneyInput("75", AppCurrency.DKK)
        assertEquals(75 / 7.46, input.eur!!, 0.0001)
        assertEquals(TypedPrice(75.0, AppCurrency.DKK), input.typedPrice)
        val eur = input.eur!!

        // A month later the krone moved a little: 75 kr. still shows as 75,00 kr., not 74,98 kr.
        showKroner(7.4612)
        assertEquals("75,00 kr.", Money.format(eur, input.typedPrice))
        assertTrue(Money.format(eur) != "75,00 kr.")
        // Shown in euros, it's the euros kept.
        Money.display = DisplayCurrency()
        assertEquals(Money.format(eur), Money.format(eur, input.typedPrice))

        // Opening it again: the field shows 75.00 in kroner and saves the same euros back.
        val again = MoneyInput.of(eur, input.typedPrice)
        assertEquals(AppCurrency.DKK, again.currency)
        assertEquals(75.0, again.typed!!, 0.0)
        assertEquals(eur, again.eur!!, 0.0)
        assertEquals(input.typedPrice, again.typedPrice)
    }

    @Test
    fun aPriceTypedInEurosNeedsNothingMore() {
        showKroner()
        val input = MoneyInput("10", AppCurrency.EUR)
        assertEquals(10.0, input.eur!!, 0.0)
        assertNull(input.typedPrice)
        // Saved fields with euros, or without an amount, aren't a typed price.
        assertNull(TypedPrice.of(10.0, "EUR"))
        assertNull(TypedPrice.of(null, "DKK"))
        assertNull(TypedPrice.of(10.0, null))
    }

    @Test
    fun aStackForgetsTheTypedPriceWithItsPrice() {
        val item = CollectionItem(card = ref("Sol Ring"), foil = false, quantity = 1)
            .withPaid(10.05, TypedPrice(75.0, AppCurrency.DKK))
        assertEquals(TypedPrice(75.0, AppCurrency.DKK), item.typedPaid)
        // A price from euros (an order, Archidekt) clears it.
        val fromEuros = item.withPaid(9.0)
        assertNull(fromEuros.typedPaid)
        assertNull(fromEuros.paidTyped)
        assertNull(fromEuros.paidCurrency)
        // No price, no typed price.
        assertNull(item.withPaid(null, TypedPrice(75.0, AppCurrency.DKK)).typedPaid)
        assertNull(item.copy(purchasePrice = null).typedPaid)
    }

    @Test
    fun anAgreedTradePriceKeepsItsCurrencyToo() {
        showKroner()
        val line = TradeItem(tradeId = 1, side = "GET", card = ref("Sol Ring"), foil = false, quantity = 2)
            .withCustom(4.0, TypedPrice(30.0, AppCurrency.DKK))
        assertEquals(TypedPrice(30.0, AppCurrency.DKK), line.typedCustom)
        // The trade's balance counts the euros kept.
        assertEquals(8.0, line.lineTotal(com.mtgtrader.data.PriceType.TREND), 0.0)
        assertNull(line.withCustom(null, TypedPrice(30.0, AppCurrency.DKK)).typedCustom)
    }

    // ---- Stacks joining ------------------------------------------------------------------------

    @Test
    fun joiningPricesTypedInTheSameCurrencyAveragesWhatWasTyped() {
        val dkk = AppCurrency.DKK
        // 2 copies at 75 kr. and 1 at 30 kr.: 60 kr. each.
        val (eur, typed) = PurchasePrices.joinedTyped(2, 75 / 7.46, TypedPrice(75.0, dkk), 1, 30 / 7.46, TypedPrice(30.0, dkk))
        assertEquals(TypedPrice(60.0, dkk), typed)
        assertEquals((2 * 75 / 7.46 + 30 / 7.46) / 3, eur!!, 0.01)
        assertTrue(!PurchasePrices.mixes(1.0, TypedPrice(75.0, dkk), 1.0, TypedPrice(30.0, dkk)))
        // Euros and euros: nothing typed.
        assertNull(PurchasePrices.joinedTyped(1, 2.0, null, 1, 4.0, null).second)
    }

    @Test
    fun joiningPricesTypedInTwoCurrenciesKeepsTheOneChosen() {
        Money.display = DisplayCurrency()
        val dkk = AppCurrency.DKK
        // 1 copy at 74,60 kr. (€10) joined by 1 bought for €20.
        assertTrue(PurchasePrices.mixes(10.0, TypedPrice(74.6, dkk), 20.0, null))
        // Kept in kroner (the stack's, by default): 74,60 kr. and €20 at 7.46 = 149,20 kr., so 111,90 kr. each.
        val (eur, inKroner) = PurchasePrices.joinedTyped(1, 10.0, TypedPrice(74.6, dkk), 1, 20.0, null)
        assertEquals(15.0, eur!!, 0.0)
        assertEquals(TypedPrice(111.9, dkk), inKroner)
        // Kept in euros: just the euros.
        assertNull(PurchasePrices.joinedTyped(1, 10.0, TypedPrice(74.6, dkk), 1, 20.0, null, AppCurrency.EUR).second)
        // Without a price on one side there's nothing to mix.
        assertTrue(!PurchasePrices.mixes(null, null, 20.0, TypedPrice(149.2, dkk)))
        assertEquals(20.0 to TypedPrice(149.2, dkk), PurchasePrices.joinedTyped(0, null, null, 1, 20.0, TypedPrice(149.2, dkk)))
    }

    @Test
    fun aReceivedCardPaidInKronerAsksWhenItJoinsAStackInEuros() = runBlocking {
        Money.display = DisplayCurrency()
        val card = ref("Sol Ring")
        val stack = repo.addToCollection(card, Finish.NONFOIL, "NM", "EN", 1).itemId
        db.collectionDao().update(db.collectionDao().byId(stack)!!.withPaid(10.0))
        val wish = db.wishlistDao().insert(WishlistItem(card = card, receivedAt = 1L))
        val v = WishToCollection.of(db.wishlistDao().byId(wish)!!).copy(price = 20.0, typed = TypedPrice(149.2, AppCurrency.DKK))
        val bought = listOf(MtgRepository.Bought(card, v.finish, v.condition, v.language, Marks.NONE, v.price, v.typed))

        assertEquals(listOf(AppCurrency.EUR, AppCurrency.DKK), repo.joinCurrencies(bought, BinderChoice()))
        // Another binder has no stack to join; a new binder neither.
        assertEquals(emptyList<AppCurrency>(), repo.joinCurrencies(bought, BinderChoice(binderId = 99)))
        assertEquals(emptyList<AppCurrency>(), repo.joinCurrencies(bought, BinderChoice(newName = "New one")))

        repo.wishToCollection(wish, v.card!!, v.finish, v.condition, v.language, v.quantity, Binder.UNSORTED, v.price, v.typed, AppCurrency.DKK)
        val s = db.collectionDao().byId(stack)!!
        assertEquals(15.0, s.purchasePrice!!, 0.0)
        assertEquals(TypedPrice(111.9, AppCurrency.DKK), s.typedPaid)
    }

    @Test
    fun editingSeveralStacksSetsTheTypedPriceOnEach() = runBlocking {
        val a = repo.addToCollection(ref("Sol Ring"), Finish.NONFOIL, "NM", "EN", 1).itemId
        val b = repo.addToCollection(ref("Arcane Signet"), Finish.NONFOIL, "NM", "EN", 2).itemId
        val typed = TypedPrice(20.0, AppCurrency.USD)
        val (n, undo) = repo.editStacks(listOf(a, b), null, null, 18.5, typed)
        assertEquals(2, n)
        for (id in listOf(a, b)) {
            assertEquals(18.5, db.collectionDao().byId(id)!!.purchasePrice!!, 0.0)
            assertEquals(typed, db.collectionDao().byId(id)!!.typedPaid)
        }
        undo()
        assertNull(db.collectionDao().byId(a)!!.typedPaid)
    }

    @Test
    fun pricesFromOrdersAveragedIntoKronerStayInKronerUnlessEurosAreChosen() {
        val item = CollectionItem(id = 1, card = ref("Sol Ring"), foil = false, quantity = 2)
            .withPaid(10.0, TypedPrice(74.6, AppCurrency.DKK))
        val line = OrderLine(setCode = "m21", setName = "Core Set 2021", name = "Sol Ring", number = "1", quantity = 1, price = 20.0, condition = "NM", language = "EN", foil = false, signed = false, altered = false)
        val row = PurchasePrices.Row(item, listOf(line), 1, 20.0, 15.0, PurchasePrices.Change.AVERAGED)
        assertTrue(row.mixesCurrencies)
        assertEquals(TypedPrice(111.9, AppCurrency.DKK), row.updated().typedPaid)
        assertEquals(15.0, row.updated().purchasePrice!!, 0.0)
        assertNull(row.updated(AppCurrency.EUR).typedPaid)
        // Replaced by the order's price: euros.
        val replaced = row.copy(change = PurchasePrices.Change.REPLACED, newPrice = 20.0)
        assertTrue(!replaced.mixesCurrencies)
        assertNull(replaced.updated().typedPaid)
        assertEquals(20.0, replaced.updated().purchasePrice!!, 0.0)
    }

    // ---- Precons -------------------------------------------------------------------------------

    @Test
    fun aPreconPaidInKronerSplitsTheKronerToo() {
        val card = { n: String, worth: Double -> Triple(ref(n), Precons.Card(name = n, count = 1), worth as Double?) }
        val list = listOf(card("Sol Ring", 3.0), card("Arcane Signet", 1.0), card("Command Tower", 0.5))
        val shares = Precons.split(40.21, list, TypedPrice(300.0, AppCurrency.DKK))
        assertEquals(40.21, shares.sumOf { it.price * it.quantity }, 0.001)
        assertEquals(300.0, shares.sumOf { it.typed!!.amount * it.quantity }, 0.001)
        assertTrue(shares.all { it.typed!!.currency == AppCurrency.DKK })
        // The same split: the most valuable card gets the most of both.
        assertEquals("Sol Ring", shares.first().card.name)
        assertEquals(shares.first().price / 40.21, shares.first().typed!!.amount / 300.0, 0.001)
        assertTrue(Precons.split(40.21, list, null).all { it.typed == null })
    }

    // ---- CSV and sync ------------------------------------------------------------------------

    @Test
    fun theCsvExportCarriesThePriceAsTyped() = runBlocking {
        val id = repo.addToCollection(ref("Sol Ring"), Finish.NONFOIL, "NM", "EN", 1).itemId
        db.collectionDao().update(db.collectionDao().byId(id)!!.withPaid(10.05, TypedPrice(75.0, AppCurrency.DKK)))
        val csv = repo.exportCollectionCsv(com.mtgtrader.data.PriceType.TREND)
        val header = csv.lines().first()
        assertTrue(header.endsWith("Purchase price as typed,Typed currency"))
        val row = csv.lines()[1]
        assertTrue(row, row.endsWith(",75.00,DKK"))
        // Euros stay in the ManaBox columns.
        assertTrue(row, ",10.05,EUR," in row)
    }

    @Test
    fun theSyncFileCarriesThePriceAsTyped() {
        val item = CollectionItem(card = ref("Sol Ring"), foil = false, quantity = 1, uid = "a")
            .withPaid(10.05, TypedPrice(75.0, AppCurrency.DKK))
        val file = SyncFile(data = SyncData(collection = listOf(SyncStack(item))))
        val back = SyncFile.decode(SyncFile.encode(file)).data.collection.single().item
        assertEquals(TypedPrice(75.0, AppCurrency.DKK), back.typedPaid)
        assertEquals(10.05, back.purchasePrice!!, 0.0)
        // Older versions would write it back without, so they stop syncing until updated.
        assertEquals(12, SyncFile.FORMAT)
    }

    private companion object {
        val RATES = ExchangeRates("2026-10-26", mapOf("DKK" to 7.46, "USD" to 1.08))
    }
}
