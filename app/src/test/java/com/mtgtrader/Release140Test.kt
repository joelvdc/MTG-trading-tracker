package com.mtgtrader

import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.DisplayCurrency
import com.mtgtrader.data.ExchangeRates
import com.mtgtrader.data.Money
import com.mtgtrader.data.Settings
import com.mtgtrader.ui.MoneyInput
import com.mtgtrader.ui.TypedCurrency
import com.mtgtrader.ui.convertedHint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 1.40: prices typed in the currency they were paid in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release140Test {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun reset() {
        Money.display = DisplayCurrency()
        Money.rates = null
        TypedCurrency.last = null
        TypedCurrency.save = {}
    }

    @Test
    fun aPricePaidInEurosWhileTheAppShowsKroner() {
        Money.display = DisplayCurrency(AppCurrency.DKK, 7.4612)
        val paid = MoneyInput("10", AppCurrency.EUR)
        // Kept as typed: 10 euros, whatever the app shows.
        assertEquals(10.0, paid.eur!!, 0.0)
        assertEquals("≈ 74,61 kr.", convertedHint(paid))
        // Typed in kroner: converted at the day's rate; no hint, it's the currency shown.
        val kr = MoneyInput("74,61", AppCurrency.DKK)
        assertEquals(74.61 / 7.4612, kr.eur!!, 1e-9)
        assertNull(convertedHint(kr))
        // Dollars, while kroner show: through the euro, at the day's rates.
        Money.rates = ExchangeRates("2026-10-16", mapOf("DKK" to 7.4612, "USD" to 1.12))
        assertEquals(11.2 / 1.12, MoneyInput("11.2", AppCurrency.USD).eur!!, 1e-9)
        // Empty or not a number: no price.
        assertNull(MoneyInput("", AppCurrency.EUR).eur)
        assertNull(MoneyInput("abc", AppCurrency.EUR).eur)
    }

    @Test
    fun anEmptyFieldStartsOnTheCurrencyLastChosen() {
        Money.display = DisplayCurrency(AppCurrency.DKK, 7.46)
        // Nothing chosen yet: the currency shown.
        assertEquals(AppCurrency.DKK, MoneyInput.of(null).currency)
        var saved: AppCurrency? = null
        TypedCurrency.save = { saved = it }
        TypedCurrency.pick(AppCurrency.EUR)
        assertEquals(AppCurrency.EUR, saved)
        assertEquals(AppCurrency.EUR, MoneyInput.of(null).currency)
        // A price already saved shows in the currency shown, as before.
        val kept = MoneyInput.of(10.0)
        assertEquals(AppCurrency.DKK, kept.currency)
        assertEquals("74.60", kept.text)
        assertEquals(10.0, kept.eur!!, 1e-9)
    }

    @Test
    fun theChoiceIsKeptOnThisPhoneOnly() {
        val s = Settings(context)
        s.typedCurrency = "EUR"
        assertEquals("EUR", Settings(context).typedCurrency)
        // Not part of what syncs: each phone keeps its own.
        assertFalse("typedCurrency" in s.syncedPrefs().values)
    }
}
