package com.mtgtrader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.Money

// Typing a price in the currency it was paid in, whatever currency the app shows. Since 1.40.

/**
 * The currency last chosen in a price field, so the next one starts on it (kept per phone, see
 * [com.mtgtrader.data.Settings.typedCurrency]). Until one is chosen, fields start on the display currency.
 */
object TypedCurrency {
    var last by mutableStateOf<AppCurrency?>(null)

    /** Saves a choice; set up by the app. */
    var save: (AppCurrency) -> Unit = {}

    fun pick(c: AppCurrency) {
        last = c
        save(c)
    }

    /** The currency an empty price field starts on. */
    val start: AppCurrency get() = last ?: Money.display.currency
}

/** "€", "$", "kr.". */
fun currencySymbol(c: AppCurrency) = when (c) {
    AppCurrency.EUR -> "€"
    AppCurrency.USD -> "$"
    AppCurrency.DKK -> "kr."
}

/** A price being typed: the text and the currency it's in. */
@Stable
class MoneyInput(text: String, currency: AppCurrency) {
    private var typedText by mutableStateOf(text)

    /** The amount in euros before rounding to cents, until the text is edited (see [switchTo]). */
    private var exactEur: Double? = null

    var text: String
        get() = typedText
        set(value) {
            if (value != typedText) exactEur = null
            typedText = value
        }

    var currency by mutableStateOf(currency)
        private set

    /** The amount as typed, in [currency]; null when empty or not a number. */
    val typed: Double? get() = Fmt.parseMoney(text)

    /** In euros (how the app keeps prices) at today's rate; null when empty or the rate isn't known. */
    val eur: Double? get() = exactEur ?: typed?.let { Money.eurOf(it, currency.name) }

    /**
     * Shows the price in [c] instead, converted at today's rate, so it keeps its value: 300 kr. becomes
     * 40,12 €, and back again 300,00 kr. (not 299,99). An empty field, or one whose rate isn't known, only
     * changes its currency. Since 1.41; before, the number stayed and only the currency changed.
     */
    fun switchTo(c: AppCurrency) {
        if (c == currency) return
        val inEur = eur
        val amount = inEur?.let { Money.amountIn(it, c) }
        if (amount != null) {
            typedText = "%.2f".format(amount)
            exactEur = inEur
        }
        currency = c
    }

    companion object {
        /**
         * For a price kept in euros ([savedEur]): one already saved shows in the display currency; an empty
         * field starts on the currency last chosen.
         */
        fun of(savedEur: Double?): MoneyInput =
            if (savedEur != null) {
                MoneyInput(Money.input(savedEur), Money.display.currency).also { it.exactEur = savedEur }
            } else {
                MoneyInput("", TypedCurrency.start)
            }
    }
}

@Composable
fun rememberMoneyInput(savedEur: Double?, key: Any? = Unit): MoneyInput = remember(key) { MoneyInput.of(savedEur) }

/** The small "€ ▾" button at the end of a price field, choosing the currency typed in. */
@Composable
fun CurrencyButton(currency: AppCurrency, enabled: Boolean = true, onPick: (AppCurrency) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("${currencySymbol(currency)} ▾", fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppCurrency.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.label, fontWeight = if (c == currency) FontWeight.Bold else null) },
                    onClick = { open = false; onPick(c) },
                )
            }
        }
    }
}

/** What a price typed in another currency comes to in the one shown: "≈ 74,61 kr."; null when it's the same. */
fun convertedHint(input: MoneyInput): String? =
    input.eur?.takeIf { input.currency != Money.display.currency }?.let { "≈ ${Money.format(it)}" }

/**
 * A price field with its currency button: type the amount as paid, in € , kr. or $ (choosing another
 * converts what's there); [onChange] runs after each change of the text or the currency. Shown in another currency than the app's, the field
 * says what it comes to.
 */
@Composable
fun MoneyField(
    input: MoneyInput,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    onChange: () -> Unit = {},
) {
    val hint = convertedHint(input)
    OutlinedTextField(
        value = input.text,
        onValueChange = { input.text = it; onChange() },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = {
            CurrencyButton(input.currency, enabled) { c ->
                input.switchTo(c)
                TypedCurrency.pick(c)
                onChange()
            }
        },
        supportingText = hint?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
