package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.mtgtrader.data.CONDITIONS
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
import com.mtgtrader.data.CT_LANGUAGES
import com.mtgtrader.data.CardTrader
import com.mtgtrader.data.CtCondition
import com.mtgtrader.data.PowerSource
import com.mtgtrader.data.ReceivedClear
import com.mtgtrader.data.SwipeAction
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
    val conditionAdjust by c.settings.conditionAdjust.collectAsStateWithLifecycle()
    val conditionPcts by c.settings.conditionPcts.collectAsStateWithLifecycle()
    val lastFetch by c.settings.lastPriceFetch.collectAsStateWithLifecycle()
    val guideDate by c.settings.priceGuideDate.collectAsStateWithLifecycle()
    val count by c.prices.count.collectAsStateWithLifecycle(0)
    val state by c.prices.state.collectAsStateWithLifecycle()
    val autoUpdate by c.settings.autoUpdate.collectAsStateWithLifecycle()
    val wifiOnly by c.settings.wifiOnly.collectAsStateWithLifecycle()
    val offlineImages by c.settings.offlineImages.collectAsStateWithLifecycle()
    val powerSource by c.settings.powerSource.collectAsStateWithLifecycle()
    val themeMode by c.settings.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
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
            ConditionSection(conditionAdjust, conditionPcts, priceSource, c.settings::setConditionAdjust, c.settings::setConditionPct)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            val alertType by c.settings.alertPriceType.collectAsStateWithLifecycle()
            val ctOn by c.settings.cardTraderOn.collectAsStateWithLifecycle()
            val ctStatus by c.cardTrader.status.collectAsStateWithLifecycle()
            val ctLanguage by c.settings.ctLanguage.collectAsStateWithLifecycle()
            val ctCondition by c.settings.ctCondition.collectAsStateWithLifecycle()
            val ctZeroOnly by c.settings.ctZeroOnly.collectAsStateWithLifecycle()
            // Asked again whenever the screen comes back (e.g. from the phone's notification settings).
            var allowed by remember { mutableStateOf(c.wishWatch.notifier.allowed()) }
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                allowed = c.wishWatch.notifier.allowed()
                onPauseOrDispose {}
            }
            val context = LocalContext.current
            val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
                allowed = c.wishWatch.notifier.allowed()
                // Asked before and refused: only the phone's settings can turn them on now.
                if (!allowed) runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            }
            val recheck = { c.wishWatch.checkSoon(c.appScope) }
            WishlistAlertsSection(
                AlertSettings(priceSource, alertType, autoUpdate, allowed, ctOn, ctStatus, ctLanguage, ctCondition, ctZeroOnly),
                AlertSettingsActions(
                    onAlertType = { c.settings.setAlertPriceType(it); recheck() },
                    onAllowNotifications = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    },
                    onCardTrader = { c.settings.setCardTraderOn(it); recheck() },
                    onToken = { t ->
                        c.cardTrader.check(t).onSuccess {
                            c.cardTrader.setToken(t)
                            recheck()
                        }
                    },
                    onForgetToken = { c.cardTrader.setToken(null) },
                    onLanguage = { c.settings.setCtLanguage(it); recheck() },
                    onCondition = { c.settings.setCtCondition(it); recheck() },
                    onZeroOnly = { c.settings.setCtZeroOnly(it); recheck() },
                ),
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            val receivedClear by c.settings.receivedClear.collectAsStateWithLifecycle()
            val receivedToCollection by c.settings.receivedToCollection.collectAsStateWithLifecycle()
            ReceivedCardsSection(
                receivedClear, receivedToCollection,
                onClear = { v -> c.settings.setReceivedClear(v); c.appScope.launch { runCatching { c.repo.autoClearReceived(v) } } },
                onToCollection = c.settings::setReceivedToCollection,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            val swipeRight by c.settings.swipeRight.collectAsStateWithLifecycle()
            val swipeLeft by c.settings.swipeLeft.collectAsStateWithLifecycle()
            SwipeSection(swipeRight, swipeLeft, c.settings::setSwipe)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            TrackerSection(nav)

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
                enabled = state !is PriceUpdateState.Running && !sourceStatus.running,
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
            SwitchRow(
                title = "Save deck and wishlist pictures for offline use",
                body = "On Wi-Fi, the app saves the pictures of your decks (their cards and covers) and of your wishlist, " +
                    "so they show without internet too. About 10–15 MB per deck.",
                checked = offlineImages,
                onChange = c.settings::setOfflineImages,
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

            // Editing decks (since 1.46).
            val autoCategories by c.settings.deckAutoCategories.collectAsStateWithLifecycle()
            SwitchRow(
                "Auto categories for new cards",
                "Cards added to a deck you can edit go into Archidekt's auto category for them (Ramp, Draw, Removal…), " +
                    "or by card type when they have none. Off: by card type.",
                autoCategories,
                c.settings::setDeckAutoCategories,
            )

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
                    "Unofficial fan app, not affiliated with Wizards of the Coast, Scryfall, Cardmarket, CardTrader, Archidekt, Commander Salt, " +
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
/**
 * Condition-adjusted values: a switch, and what a copy in each condition counts for. Since 1.33.
 */
@Composable
internal fun ConditionSection(on: Boolean, pcts: Map<String, Int>, source: PriceSource, onSwitch: (Boolean) -> Unit, onPct: (String, Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSwitch(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text("Count a card's condition in its value", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = on, onCheckedChange = onSwitch)
    }
    Text(
        "The price guides have one price per card, whatever its condition. With this on, a copy counts for the percentage " +
            "below everywhere a value is shown (collection, stats, trades, the trade binder), and the price is marked, e.g. “EX 85%”." +
            if (source == PriceSource.CARD_KINGDOM) " Card Kingdom's own prices per condition are used as they are." else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!on) return
    for ((code, label) in CONDITIONS) {
        val pct = pcts[code] ?: 100
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$label ($code)", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { onPct(code, pct - 5) }, enabled = pct > 0) { Icon(Icons.Default.Remove, "Less for $label") }
            Text("$pct%", Modifier.width(52.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            IconButton(onClick = { onPct(code, pct + 5) }, enabled = pct < 100) { Icon(Icons.Default.Add, "More for $label") }
        }
    }
}

@Composable
internal fun CurrencySection(currency: AppCurrency, rates: ExchangeRates?, onPick: (AppCurrency) -> Unit) {
    Text("Currency", style = MaterialTheme.typography.titleMedium)
    Text(
        "Prices come from Cardmarket in euros and are converted with the European Central Bank's daily rates. " +
            "Prices you type can be in any of these: the € ▾ button in each price field picks the currency you paid in. CSV files and Archidekt keep euros.",
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
    val small = MaterialTheme.typography.bodySmall
    if (status.running) {
        Text(status.message ?: "Downloading TCGplayer's and Card Kingdom's prices…", style = small, color = MaterialTheme.colorScheme.primary)
    }
    // Each source on its own line: when it last downloaded, and why the last try failed (since 1.30).
    Text(
        "TCGplayer: " + (if (status.tcgplayerAt > 0) "downloaded ${Fmt.dateTime(status.tcgplayerAt)}" else "not downloaded yet"),
        style = small, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    status.tcgplayerError?.let { Text("Last try failed: $it", style = small, color = MaterialTheme.colorScheme.error) }
    val listDay = status.cardKingdomListDate?.let { d -> runCatching { Fmt.date(java.time.LocalDate.parse(d.substringBefore(' ')).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) }.getOrNull() }
    Text(
        "Card Kingdom: " + (
            if (status.cardKingdomAt > 0) "downloaded ${Fmt.dateTime(status.cardKingdomAt)}" +
                (listDay?.let { ", its list of $it" } ?: "") +
                (if (status.cardKingdomVia == "GitHub copy") " (GitHub's copy, as Card Kingdom refused this phone)" else "")
            else "not downloaded yet"
            ),
        style = small, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Both download once a day, with Cardmarket's prices.", style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
    status.cardKingdomError?.let { Text("Last try failed: $it", style = small, color = MaterialTheme.colorScheme.error) }
}

/**
 * The Cardmarket prices an alert can compare against, each with what it means for an alert. The
 * lowest listing is the default. Since 1.37.
 */
internal val alertTypeHelp = linkedMapOf(
    PriceType.LOW to "The cheapest copy for sale right now. Reacts fastest, but counts every condition and language, so a damaged or foreign copy can set it off.",
    PriceType.TREND to "Cardmarket's smoothed “what it really sells for”. One odd cheap copy won't set it off.",
    PriceType.AVG7 to "What it actually sold for this past week. Steady and honest.",
    PriceType.AVG1 to "What it sold for yesterday. Quick to react, but jumpy for cards that sell only a few times a day.",
)

/**
 * A dropdown whose options each come with an explanation. The menu shows every option's explanation
 * while choosing; the closed field only the chosen one's, so the screen stays short. Since 1.37.
 */
@Composable
internal fun <T> ExplainedDropdown(label: String, selected: T, options: Map<T, String>, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(optionLabel(selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        ScreenMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(max = 340.dp)) {
            options.forEach { (o, help) ->
                DropdownMenuItem(
                    text = { ExplainedOption(optionLabel(o), help, o == selected) },
                    leadingIcon = { RadioButton(selected = o == selected, onClick = null) },
                    onClick = { onSelect(o); open = false },
                )
            }
        }
    }
    options[selected]?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
    }
}

/** One option in an [ExplainedDropdown]'s menu: its name, and what it means underneath. */
@Composable
internal fun ExplainedOption(label: String, help: String, selected: Boolean) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else null)
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Everything Settings → Wishlist price alerts shows, so it can be drawn without the app (screenshots). Since 1.37. */
data class AlertSettings(
    val source: PriceSource = PriceSource.CARDMARKET,
    val alertType: PriceType = PriceType.LOW,
    val autoUpdate: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val cardTraderOn: Boolean = false,
    val ctStatus: CardTrader.Status = CardTrader.Status(),
    val ctLanguage: String = "en",
    val ctCondition: CtCondition = CtCondition.DEFAULT,
    val ctZeroOnly: Boolean = true,
)

/** What the section's controls do. */
class AlertSettingsActions(
    val onAlertType: (PriceType) -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onCardTrader: (Boolean) -> Unit = {},
    /** Saves a token after CardTrader accepted it; the result says why not otherwise. */
    val onToken: suspend (String) -> Result<Unit> = { Result.success(Unit) },
    val onForgetToken: () -> Unit = {},
    val onLanguage: (String) -> Unit = {},
    val onCondition: (CtCondition) -> Unit = {},
    val onZeroOnly: (Boolean) -> Unit = {},
)

/** Settings → Wishlist price alerts: what alerts compare against, notifications, and CardTrader. Since 1.37. */
@Composable
internal fun WishlistAlertsSection(s: AlertSettings, act: AlertSettingsActions) {
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text("Wishlist price alerts", style = MaterialTheme.typography.titleMedium)
    Text(
        "Open a card on the wishlist and set a price: you get a notification when it costs that much or less " +
            "(for “any printing” cards, the cheapest printing). Prices are checked when they update, a few times a day.",
        style = small, color = muted,
    )
    if (!s.autoUpdate) {
        Text(
            "“Update automatically” is off (below), so prices, and alerts, only update when you tap “Update prices now”.",
            style = small, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp),
        )
    }
    if (!s.notificationsAllowed) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifications are off for MTG Trader.", Modifier.weight(1f), style = small, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = act.onAllowNotifications) { Text("Allow") }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (s.source == PriceSource.CARDMARKET) {
        ExplainedDropdown("Cardmarket price for alerts", s.alertType, alertTypeHelp, { it.label }, act.onAlertType)
    } else {
        Text(
            "Alerts use ${s.source.label}'s price, the price source chosen at the top.",
            style = small, color = muted,
        )
    }
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        title = "Also check CardTrader",
        body = "Each wishlist card also shows CardTrader's cheapest offer, with a link to the card there, and alerts count it too.",
        checked = s.cardTraderOn,
        onChange = act.onCardTrader,
    )
    if (!s.cardTraderOn) return
    CardTraderTokenRow(s.ctStatus, act)
    SwitchRow(
        title = "Only CardTrader Zero offers",
        body = "Cards CardTrader ships together from its hub, in one parcel. Off: every seller's offer counts, each shipping on its own. " +
            "The cheapest offer from any seller shows next to it. CardTrader only shows a printing's 25 cheapest offers: " +
            "when none of them is CT Zero, the card says what a CT Zero copy costs at least (“CT Zero over €…”).",
        checked = s.ctZeroOnly,
        onChange = act.onZeroOnly,
    )
    Spacer(Modifier.height(4.dp))
    DropdownSelector("Language on CardTrader", s.ctLanguage, CT_LANGUAGES.map { it.first }, { code -> CT_LANGUAGES.firstOrNull { it.first == code }?.second ?: code }, act.onLanguage, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    DropdownSelector("Condition on CardTrader", s.ctCondition, CtCondition.entries.toList(), { it.label }, act.onCondition, Modifier.fillMaxWidth())
    Text(
        "Signed, altered and graded copies, bundles and sellers on holiday are left out. Prices don't include shipping.",
        style = small, color = muted, modifier = Modifier.padding(top = 4.dp),
    )
}

/** The CardTrader API token: typed in and checked with CardTrader, or saved (and how the last lookups went). */
@Composable
private fun CardTraderTokenRow(status: CardTrader.Status, act: AlertSettingsActions) {
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(!status.hasToken) }
    var text by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (status.hasToken && !editing) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("API token saved on this phone", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        status.error != null -> "Last lookup failed: ${status.error}"
                        status.okAt > 0 -> "Last lookup ${Fmt.dateTime(status.okAt)}"
                        else -> "Prices are looked up with the next check."
                    },
                    style = small, color = if (status.error != null) MaterialTheme.colorScheme.error else muted,
                )
            }
            TextButton(onClick = { text = ""; error = null; editing = true }) { Text("Change") }
            TextButton(onClick = act.onForgetToken) { Text("Remove") }
        }
        return
    }
    Text(
        "CardTrader needs your personal API token: on cardtrader.com, open your profile's settings and copy the token from its API section. " +
            "It's kept encrypted on this phone only (not synced): enter it on each phone.",
        style = small, color = muted,
    )
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.trim(); error = null },
        label = { Text("CardTrader API token") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                checking = true
                scope.launch {
                    val r = act.onToken(text)
                    checking = false
                    r.onSuccess { editing = false; text = "" }.onFailure { error = it.message ?: "CardTrader didn't answer" }
                }
            },
            enabled = text.length > 10 && !checking,
        ) { Text(if (checking) "Checking…" else "Save and check") }
        if (status.hasToken) TextButton(onClick = { editing = false }) { Text("Cancel") }
    }
}

/** Settings → Received wishlist cards: when they're cleared, and whether they also go into the collection. Since 1.38. */
@Composable
internal fun ReceivedCardsSection(clear: ReceivedClear, toCollection: Boolean, onClear: (ReceivedClear) -> Unit, onToCollection: (Boolean) -> Unit) {
    Text("Received wishlist cards", style = MaterialTheme.typography.titleMedium)
    Text(
        "Swipe a wishlist card to the right (or tick “Received” in its window) once it has arrived: it moves to the wishlist's " +
            "Received section, as a record. They're kept until you clear them (⋮ → Clear received cards), or as chosen here.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    DropdownSelector("Clear received cards automatically", clear, ReceivedClear.entries.toList(), { it.label }, onClear, Modifier.fillMaxWidth())
    Spacer(Modifier.height(4.dp))
    SwitchRow(
        title = "Add received cards to the collection",
        body = "Each card goes into Unsorted (near mint, its printing). Leave it off when you import your orders: they add the cards already.",
        checked = toCollection,
        onChange = onToCollection,
    )
}

/** Settings → Life tracker (since 1.47): since 1.48 its settings are in the game itself, as in Lifetap; since 1.58 also here. */
@Composable
internal fun TrackerSection(nav: NavController) {
    Text("Life tracker", style = MaterialTheme.typography.titleMedium)
    Text(
        "Controls, look and vibration for every game. A game's own choices (players, starting life, turns) are in the game: tap ⚙ in the bar across the middle.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row {
        TextButton(onClick = { nav.navigate("games/settings") }) { Text("Life tracker settings") }
        TextButton(onClick = { nav.navigate("games/players") }) { Text("Players and their decks") }
    }
}

/** Settings → Swiping cards in the collection: what a swipe to the right and to the left does. Since 1.38. */
@Composable
internal fun SwipeSection(right: SwipeAction, left: SwipeAction, onPick: (right: Boolean, SwipeAction) -> Unit) {
    Text("Swiping cards in the collection", style = MaterialTheme.typography.titleMedium)
    Text(
        "In the list and compact views; every swipe can be undone. Press and hold a card to select several (in every view).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    DropdownSelector("Swipe right →", right, SwipeAction.entries.toList(), { it.label }, { onPick(true, it) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    DropdownSelector("← Swipe left", left, SwipeAction.entries.toList(), { it.label }, { onPick(false, it) }, Modifier.fillMaxWidth())
}
