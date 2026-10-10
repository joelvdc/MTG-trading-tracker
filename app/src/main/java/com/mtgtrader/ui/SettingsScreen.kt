package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.BuildConfig
import com.mtgtrader.container
import com.mtgtrader.data.PowerSource
import com.mtgtrader.data.ThemeMode
import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.ExchangeRates
import com.mtgtrader.data.PriceSource
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.PriceUpdateState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private val typeHelp = mapOf(
    PriceType.TREND to "Cardmarket's smoothed recent sale price. The usual choice for trades.",
    PriceType.AVG to "Average price of recent sales.",
    PriceType.AVG30 to "Average over the last 30 days — steadier for volatile cards.",
    PriceType.AVG7 to "Average over the last 7 days.",
    PriceType.AVG1 to "Average of yesterday's sales — can jump around.",
    PriceType.LOW to "Cheapest current listing (any condition) — tends to be low.",
)

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalContext.current.container
    val archidekt by c.archidekt.status.collectAsStateWithLifecycle()
    val unsynced by androidx.compose.runtime.produceState<Int?>(null, archidekt.lastSyncAt, archidekt.running) { value = c.archidekt.unsyncedCopies() }
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val currency by c.settings.currency.collectAsStateWithLifecycle()
    val priceSource by c.settings.priceSource.collectAsStateWithLifecycle()
    val sourceStatus by c.priceSources.status.collectAsStateWithLifecycle()
    val rates by c.exchangeRates.rates.collectAsStateWithLifecycle()
    val tolerance by c.settings.tolerancePct.collectAsStateWithLifecycle()
    val lastFetch by c.settings.lastPriceFetch.collectAsStateWithLifecycle()
    val guideDate by c.settings.priceGuideDate.collectAsStateWithLifecycle()
    val count by c.prices.count.collectAsStateWithLifecycle(0)
    val state by c.prices.state.collectAsStateWithLifecycle()
    val autoUpdate by c.settings.autoUpdate.collectAsStateWithLifecycle()
    val wifiOnly by c.settings.wifiOnly.collectAsStateWithLifecycle()
    val powerSource by c.settings.powerSource.collectAsStateWithLifecycle()
    val themeMode by c.settings.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            PriceSourceSection(priceSource, sourceStatus, c.settings::setPriceSource)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(if (priceSource == PriceSource.CARDMARKET) "Price used for valuing cards" else "Cardmarket price", style = MaterialTheme.typography.titleMedium)
            if (priceSource != PriceSource.CARDMARKET) {
                Text(
                    "Used for cards ${priceSource.label} has no price for (shown with ≈), and in the comparisons.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PriceType.entries.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { c.settings.setPriceType(t) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = t == priceType, onClick = { c.settings.setPriceType(t) })
                    Column {
                        Text(t.label)
                        Text(typeHelp[t] ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            CurrencySection(currency, rates, c.settings::setCurrency)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Fair trade margin: ±$tolerance%", style = MaterialTheme.typography.titleMedium)
            Text(
                "Trades whose sides differ by no more than this are shown as fair.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = tolerance.toFloat(),
                onValueChange = { c.settings.setTolerance(it.roundToInt()) },
                valueRange = 0f..20f,
                steps = 19,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Cardmarket price data", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Price guide date: ${formatGuideDate(guideDate)}")
            Text("Products with prices: $count")
            Text("Last downloaded: ${if (lastFetch == 0L) "never" else Fmt.dateTime(lastFetch)}")
            when (val s = state) {
                is PriceUpdateState.Running -> Text(s.message, color = MaterialTheme.colorScheme.primary)
                is PriceUpdateState.Failed -> Text("Last update failed: ${s.message}", color = MaterialTheme.colorScheme.error)
                PriceUpdateState.Idle -> {}
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    c.appScope.launch {
                        val ok = c.prices.refresh()
                        runCatching { c.priceSources.refresh() }
                        if (ok) c.updater.afterUpdate()
                    }
                },
                enabled = state !is PriceUpdateState.Running,
            ) { Text("Update prices now") }
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = "Update automatically",
                body = "Prices once a day (about 26 MB), when you open the app and in the background.",
                checked = autoUpdate,
                onChange = { c.settings.setAutoUpdate(it); c.updater.schedule() },
            )
            SwitchRow(
                title = "Only on Wi-Fi",
                body = "Automatic updates wait for Wi-Fi, so they don't use mobile data. “Update prices now” always works.",
                checked = wifiOnly,
                enabled = autoUpdate,
                onChange = { c.settings.setWifiOnly(it); c.updater.schedule() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Cards that already sit in a trade keep the price they had when added — use “Refresh prices” in a trade to update them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            ThemeMode.entries.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { c.settings.setThemeMode(m) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = m == themeMode, onClick = { c.settings.setThemeMode(m) })
                    Text(m.label)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Commander deck power level", style = MaterialTheme.typography.titleMedium)
            Text(
                "Where the power level on the Decks tab, the deck pages and the power card comes from (also used for sorting). " +
                    "Brackets and everything else on the rule-zero cards come from Commander Salt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PowerSource.entries.forEach { src ->
                val pick = {
                    if (src != powerSource) {
                        c.settings.setPowerSource(src)
                        if (src.external) c.decks.rateMissingPower()
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = pick).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = src == powerSource, onClick = pick)
                    Column {
                        Text(src.label)
                        Text(
                            when (src) {
                                PowerSource.COMMANDER_SALT -> "commandersalt.com scores the deck when it's imported."
                                PowerSource.EDH_POWER_LEVEL -> "The app has edhpowerlevel.com work out each deck's power level (a few seconds per deck)."
                                PowerSource.SCROLLVAULT -> "The app has ScrollVault's bracket calculator analyse each deck (about 10 seconds per deck). " +
                                    "Its power level comes with a margin, a typical winning turn and a line to tell your pod."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SyncSection()

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Archidekt collection", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !archidekt.connected -> "Keep your Archidekt collection the same as the app's, both ways."
                    archidekt.review != null || archidekt.conflicts.isNotEmpty() -> "Logged in as ${archidekt.username} · waiting for you"
                    archidekt.lastSyncAt == 0L -> "Logged in as ${archidekt.username} · not synced yet"
                    (unsynced ?: 0) > 0 -> "Logged in as ${archidekt.username} · $unsynced card(s) changed since the last sync"
                    else -> "Logged in as ${archidekt.username} · last synced ${Fmt.dateTime(archidekt.lastSyncAt)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (archidekt.review != null || archidekt.conflicts.isNotEmpty() || archidekt.lastError != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { nav.navigate("archidekt") }) { Text(if (archidekt.connected) "Archidekt sync…" else "Set up Archidekt sync…") }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Backups", style = MaterialTheme.typography.titleMedium)
            Text(
                if (c.backups.lastBackupAt == 0L) "Restore points of all your data, on Nextcloud or on this phone." else "Last backup: ${Fmt.dateTime(c.backups.lastBackupAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { nav.navigate("backups") }) { Text("Backups and restore…") }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "Card data and pictures: Scryfall. Prices: Cardmarket's public daily price guide (EUR). " +
                    "Decklists: Archidekt. Brackets and deck scores: Commander Salt; power level from the source chosen above.\n\n" +
                    "All of it is looked up live, so new sets show up without an app update.\n\n" +
                    "Unofficial fan app, not affiliated with Wizards of the Coast, Scryfall, Cardmarket, Archidekt, Commander Salt, " +
                    "EDH Power Level or ScrollVault.\n\nVersion ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun formatGuideDate(raw: String?): String {
    if (raw == null) return "not downloaded yet"
    return try {
        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(raw)
        if (d != null) Fmt.dateTime(d.time) else raw
    } catch (e: Exception) {
        raw
    }
}

/** Settings → Currency: what prices are shown in, and the rates used. Since 1.28. */
@Composable
internal fun CurrencySection(currency: AppCurrency, rates: ExchangeRates?, onPick: (AppCurrency) -> Unit) {
    Text("Currency", style = MaterialTheme.typography.titleMedium)
    Text(
        "Prices come from Cardmarket in euros and are converted with the European Central Bank's daily rates. " +
            "Purchase prices you type are in this currency too. CSV files and Archidekt keep euros.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AppCurrency.entries.forEach { cur ->
        Row(Modifier.fillMaxWidth().clickable { onPick(cur) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = cur == currency, onClick = { onPick(cur) })
            Text(cur.label)
        }
    }
    if (currency != AppCurrency.EUR) {
        Text(
            rateLine(currency, rates),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "1 € = 7.4603 DKK · rates of 9 Oct 2026 (ECB)". */
internal fun rateLine(currency: AppCurrency, rates: ExchangeRates?): String {
    val rate = rates?.rate(currency)
    return when {
        rate != null -> {
            val day = runCatching {
                java.time.LocalDate.parse(rates.date).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
            }.getOrDefault(rates.date)
            "1 € = %.4f %s · rates of %s (European Central Bank)".format(rate, currency.name, day)
        }
        currency == AppCurrency.DKK -> "1 € ≈ 7.46 DKK (the krone's fixed rate) until the first download of the daily rates."
        else -> "Waiting for the daily exchange rates: prices show in euros until then."
    }
}

/** Settings → Price source: where a card's price comes from. Since 1.29. */
@Composable
internal fun PriceSourceSection(source: PriceSource, status: com.mtgtrader.data.PriceSourceStore.SourceStatus, onPick: (PriceSource) -> Unit) {
    Text("Price source", style = MaterialTheme.typography.titleMedium)
    Text(
        "Where card prices come from: the collection total, sorting, filters, trades and the trade binder use it. " +
            "The value screen, the stats and each card's page compare all three. TCGplayer's and Card Kingdom's dollar prices are converted with the daily exchange rates.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val help = mapOf(
        PriceSource.CARDMARKET to "Europe's market, in euros. Has price types and trend arrows.",
        PriceSource.TCGPLAYER to "America's largest market: its market price (one price per card, any condition).",
        PriceSource.CARD_KINGDOM to "An American shop: its selling price for each copy's condition.",
    )
    PriceSource.entries.forEach { s ->
        Row(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = s == source, onClick = { onPick(s) })
            Column {
                Text(s.label)
                Text(help[s].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val last = listOfNotNull(
        status.tcgplayerAt.takeIf { it > 0 }?.let { "TCGplayer ${Fmt.dateTime(it)}" },
        status.cardKingdomAt.takeIf { it > 0 }?.let { "Card Kingdom ${Fmt.dateTime(it)}" },
    )
    Text(
        when {
            status.running -> "Downloading TCGplayer's and Card Kingdom's prices…"
            last.isEmpty() -> "TCGplayer's and Card Kingdom's prices download with the next price update."
            else -> "Last downloaded: " + last.joinToString(" · ")
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (status.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    status.error?.let { Text("Last update failed: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}
