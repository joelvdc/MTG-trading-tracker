package com.mtgtrader

import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.DisplayCurrency
import com.mtgtrader.data.ExchangeRates
import com.mtgtrader.data.Money
import com.mtgtrader.ui.MoneyInput
import com.mtgtrader.ui.TypedCurrency
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.41: choosing another currency in a price field converts the amount. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release141Test {
    @After
    fun reset() {
        Money.display = DisplayCurrency()
        Money.rates = null
        TypedCurrency.last = null
    }

    private fun amount(input: MoneyInput) = input.typed!!

    @Test
    fun anAlertLevelKeepsItsValueWhenTheCurrencyChanges() {
        Money.display = DisplayCurrency(AppCurrency.DKK, 7.4612)
        Money.rates = ExchangeRates("2026-10-16", mapOf("DKK" to 7.4612, "USD" to 1.12))
        val level = MoneyInput("300,00", AppCurrency.DKK)
        level.switchTo(AppCurrency.EUR)
        assertEquals(AppCurrency.EUR, level.currency)
        assertEquals(40.21, amount(level), 0.0)
        assertEquals(300 / 7.4612, level.eur!!, 1e-9)
        // Through dollars and back: exactly what was typed, not a cent off.
        level.switchTo(AppCurrency.USD)
        assertEquals(45.03, amount(level), 0.0)
        level.switchTo(AppCurrency.DKK)
        assertEquals(300.0, amount(level), 0.0)
        // Typing again starts from the new number.
        level.text = "40"
        level.switchTo(AppCurrency.EUR)
        assertEquals(5.36, amount(level), 0.0)
        assertEquals(40 / 7.4612, level.eur!!, 1e-9)
    }

    @Test
    fun aSavedPriceConvertsFromWhatWasSaved() {
        Money.display = DisplayCurrency(AppCurrency.DKK, 7.4612)
        val paid = MoneyInput.of(10.0)
        assertEquals(74.61, amount(paid), 0.0)
        assertEquals(10.0, paid.eur!!, 0.0)
        paid.switchTo(AppCurrency.EUR)
        assertEquals(10.0, amount(paid), 0.0)
        assertEquals(10.0, paid.eur!!, 0.0)
    }

    @Test
    fun anEmptyFieldOnlyChangesItsCurrency() {
        val empty = MoneyInput("", AppCurrency.EUR)
        empty.switchTo(AppCurrency.DKK)
        assertEquals(AppCurrency.DKK, empty.currency)
        assertEquals("", empty.text)
        assertNull(empty.eur)
        // Not a number: left as it is.
        val junk = MoneyInput("abc", AppCurrency.EUR)
        junk.switchTo(AppCurrency.USD)
        assertEquals("abc", junk.text)
    }
}
