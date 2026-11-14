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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
    PriceType.TREND to "Smoothed recent sales. The usual choice.",
    PriceType.AVG to "Recent sales.",
    PriceType.AVG30 to "Last 30 days. Steadier.",
    PriceType.AVG7 to "Last 7 days.",
    PriceType.AVG1 to "Yesterday's sales. Jumpy.",
    PriceType.LOW to "Cheapest listing, any condition.",
)

/**
 * The groups of Settings (since 1.82): the main page lists them with a summary of their current values, and each
 * opens its own page ("settings/<key>"). Games and Backups open their existing pages.
 */
enum class SettingsCategory(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String) {
    ACCOUNT("Account & sync", Icons.Default.AccountCircle, "settings/account"),
    PRICES("Prices", Icons.Default.Euro, "settings/prices"),
    COLLECTION("Collection & trades", Icons.Default.Style, "settings/collection"),
    WISHLIST("Wishlist", Icons.Default.Favorite, "settings/wishlist"),
    DECKS("Decks", Icons.Default.ViewAgenda, "settings/decks"),
    GAMES("Games", Icons.Default.Casino, "games/settings"),
    BACKUPS("Backups", Icons.Default.Restore, "backups"),
    APPEARANCE("Appearance & offline", Icons.Default.Palette, "settings/appearance"),
    ABOUT("About", Icons.Default.Info, "settings/about"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.route == "settings/$key" }
    }
}

/** One line per category with what it's set to now, for the main Settings page. */
@Composable
internal fun settingsSummaries(): Map<SettingsCategory, String> {
    val c = LocalContext.current.container
    val sync by c.sync.status.collectAsStateWithLifecycle()
    val priceSource by c.settings.priceSource.collectAsStateWithLifecycle()
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val currency by c.settings.currency.collectAsStateWithLifecycle()
    val autoUpdate by c.settings.autoUpdate.collectAsStateWithLifecycle()
    val swipeRight by c.settings.swipeRight.collectAsStateWithLifecycle()
    val tolerance by c.settings.tolerancePct.collectAsStateWithLifecycle()
    val ctOn by c.settings.cardTraderOn.collectAsStateWithLifecycle()
    val alertType by c.settings.alertPriceType.collectAsStateWithLifecycle()
    val powerSource by c.settings.powerSource.collectAsStateWithLifecycle()
    val ownArchidekt by c.settings.ownArchidektUser.collectAsStateWithLifecycle()
    val themeMode by c.settings.themeMode.collectAsStateWithLifecycle()
    val offlineImages by c.settings.offlineImages.collectAsStateWithLifecycle()
    return SettingsSummary(
        account = when {
            sync.connected -> "Logged in as ${sync.user}" + if (sync.autoSync) ", syncs automatically" else ", sync by hand"
            sync.nextcloudEnded -> "Nextcloud sync has ended: log in with your MTG Trader account"
            else -> "Not syncing: log in with your MTG Trader account"
        },
        prices = listOf(priceSource.label, priceType.label, currency.name, if (autoUpdate) "daily updates" else "updates by hand").joinToString(" · "),
        collection = "Swipe right: ${swipeRight.label.replaceFirstChar { it.lowercase() }} · fair trade ±$tolerance%",
        wishlist = "Price alerts on ${alertType.label}" + if (ctOn) " · CardTrader" else "",
        decks = "Power level: ${powerSource.label}" + (ownArchidekt.takeIf { it.isNotBlank() }?.let { " · Archidekt: $it" } ?: ""),
        games = "Life tracker controls, look and vibration",
        backups = if (c.backups.lastBackupAt == 0L) "Restore points of all your data" else "Last backup: ${Fmt.dateTime(c.backups.lastBackupAt)}",
        appearance = themeMode.label + if (offlineImages) " · pictures saved for offline use" else "",
        about = "Version ${BuildConfig.VERSION_NAME}, data sources",
    ).map
}

/** The texts of [settingsSummaries], also used by the screenshot test. */
internal data class SettingsSummary(
    val account: String, val prices: String, val collection: String, val wishlist: String, val decks: String,
    val games: String, val backups: String, val appearance: String, val about: String,
) {
    val map get() = mapOf(
        SettingsCategory.ACCOUNT to account, SettingsCategory.PRICES to prices, SettingsCategory.COLLECTION to collection,
        SettingsCategory.WISHLIST to wishlist, SettingsCategory.DECKS to decks, SettingsCategory.GAMES to games,
        SettingsCategory.BACKUPS to backups, SettingsCategory.APPEARANCE to appearance, SettingsCategory.ABOUT to about,
    )
}

@Composable
fun SettingsScreen(nav: NavController) {
    val summaries = settingsSummaries()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        SettingsCategoryList(summaries, Modifier.padding(pad)) { nav.navigate(it.route) { launchSingleTop = true } }
    }
}

/** The main Settings page: each category with its icon, name and current values. */
@Composable
internal fun SettingsCategoryList(summaries: Map<SettingsCategory, String>, modifier: Modifier = Modifier, onOpen: (SettingsCategory) -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SettingsCategory.entries.forEach { cat ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(cat) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(cat.title, style = MaterialTheme.typography.titleMedium)
                    summaries[cat]?.let {
                        Text(
                            it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One category's page: the settings that used to sit in the long Settings list (all unchanged). Since 1.82. */
@Composable
fun SettingsCategoryScreen(nav: NavController, cat: SettingsCategory) {

    val c = LocalContext.current.container
    val ownArchidekt by c.settings.ownArchidektUser.collectAsStateWithLifecycle()
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
    val priceVia by c.settings.priceVia.collectAsStateWithLifecycle()
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
                title = { Text(cat.title) },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (cat) {
                SettingsCategory.ACCOUNT -> {
                    SyncSection()
                }
                SettingsCategory.PRICES -> {
                    PriceSourceSection(priceSource, sourceStatus, c.settings::setPriceSource)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text(if (priceSource == PriceSource.CARDMARKET) "Price used for valuing cards" else "Cardmarket price", style = MaterialTheme.typography.titleMedium)
                    if (priceSource != PriceSource.CARDMARKET) {
                        Text(
                            "For cards ${priceSource.label} has no price for (≈).",
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
                    CurrencySection(currency, rates, c.settings::setCurrency)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text("Cardmarket price data", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Price guide date: ${formatGuideDate(guideDate)}")
                    Text("Products with prices: $count")
                    Text("Last downloaded: ${if (lastFetch == 0L) "never" else Fmt.dateTime(lastFetch)}")
                    when (priceVia) {
                        com.mtgtrader.data.PriceGuideRepository.VIA_WEBSITE -> Text("From: the MTG Trader website (about 2 MB)")
                        com.mtgtrader.data.PriceGuideRepository.VIA_CARDMARKET -> Text("From: Cardmarket (about 27 MB)")
                        else -> {}
                    }
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
                        body = "Once a day, also in the background.",
                        checked = autoUpdate,
                        onChange = { c.settings.setAutoUpdate(it); c.updater.schedule() },
                    )
                    SwitchRow(
                        title = "Only on Wi-Fi",
                        body = "Automatic updates wait for Wi-Fi.",
                        checked = wifiOnly,
                        enabled = autoUpdate,
                        onChange = { c.settings.setWifiOnly(it); c.updater.schedule() },
                    )
                }
                SettingsCategory.COLLECTION -> {
                    val swipeRight by c.settings.swipeRight.collectAsStateWithLifecycle()
                    val swipeLeft by c.settings.swipeLeft.collectAsStateWithLifecycle()
                    SwipeSection(swipeRight, swipeLeft, c.settings::setSwipe)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text("Fair trade margin: ±$tolerance%", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Trades within this margin show as fair.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = tolerance.toFloat(),
                        onValueChange = { c.settings.setTolerance(it.roundToInt()) },
                        valueRange = 0f..20f,
                        steps = 19,
                    )
                }
                SettingsCategory.WISHLIST -> {
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
                }
                SettingsCategory.DECKS -> {
                    Text("Commander deck power level", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Shown on the Decks tab and deck pages. Brackets come from Commander Salt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PowerSource.entries.forEach { src ->
                        val pick = {
                            if (src != powerSource) {
                                c.settings.setPowerSource(src)
                                if (src.separate) c.decks.rateMissingPower()
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
                                        PowerSource.COMMANDER_SALT -> "Scored by commandersalt.com on import."
                                        PowerSource.EDH_POWER_LEVEL -> "From edhpowerlevel.com, a few seconds per deck."
                                        PowerSource.SCROLLVAULT -> "From ScrollVault, about 10 seconds per deck, with a win turn."
                                        PowerSource.APP -> "On this phone, from the win turn and Commander Salt's ratings (1–10)."
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
                        "New cards go into categories like Ramp, Removal and Draw. Off: by card type.",
                        autoCategories,
                        c.settings::setDeckAutoCategories,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text("Archidekt", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Marks which public Archidekt decks are yours.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    var archidektName by remember(ownArchidekt) { mutableStateOf(ownArchidekt) }
                    OutlinedTextField(
                        value = archidektName,
                        onValueChange = { archidektName = it; c.settings.setOwnArchidektUser(it) },
                        label = { Text("Your Archidekt username") },
                        placeholder = { Text(c.settings.archidektUser.ifBlank { "e.g. from archidekt.com/u/username" }) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsCategory.APPEARANCE -> {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    ThemeMode.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { c.settings.setThemeMode(m) }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = m == themeMode, onClick = { c.settings.setThemeMode(m) })
                            Text(m.label)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        title = "Save deck and wishlist pictures for offline use",
                        body = "On Wi-Fi. About 10–15 MB per deck.",
                        checked = offlineImages,
                        onChange = c.settings::setOfflineImages,
                    )
                }
                SettingsCategory.ABOUT -> {
                    Text(
                        "Cards: Scryfall. Prices: Cardmarket, TCGplayer, Card Kingdom, CardTrader. Decks: Archidekt, EDHREC, " +
                            "recommander.cards, Commander Salt, Commander Spellbook, EDH Power Level, ScrollVault, MTGJSON. " +
                            "Exchange rates: European Central Bank.\n\n" +
                            "Not affiliated with any of these; prices are for guidance only.\n\n" +
                            "MTG Trader is unofficial Fan Content permitted under the Fan Content Policy. Not approved or endorsed by Wizards. " +
                            "Portions of the materials used are property of Wizards of the Coast. © Wizards of the Coast LLC.\n\n" +
                            "Privacy and attribution: mtgtrader.vercel.app/privacy and mtgtrader.vercel.app/legal.\n\nVersion ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsCategory.GAMES, SettingsCategory.BACKUPS -> {}
            }
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
        "A copy counts for the percentage below, marked e.g. “EX 85%”." +
            if (source == PriceSource.CARD_KINGDOM) " Card Kingdom has its own prices per condition." else "",
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
        "Converted from euros with the European Central Bank's daily rates.",
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
    val help = mapOf(
        PriceSource.CARDMARKET to "Europe, in euros, with price types and trends.",
        PriceSource.TCGPLAYER to "US market price, any condition.",
        PriceSource.CARD_KINGDOM to "US shop, a price per condition.",
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
                when (status.cardKingdomVia) {
                    "GitHub copy" -> " (GitHub's copy, as Card Kingdom refused this phone)"
                    com.mtgtrader.data.PriceSourceStore.WEBSITE -> " (from the MTG Trader website)"
                    else -> ""
                }
            else "not downloaded yet"
            ),
        style = small, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    status.cardKingdomError?.let { Text("Last try failed: $it", style = small, color = MaterialTheme.colorScheme.error) }
}

/**
 * The Cardmarket prices an alert can compare against, each with what it means for an alert. The
 * lowest listing is the default. Since 1.37.
 */
internal val alertTypeHelp = linkedMapOf(
    PriceType.LOW to "Cheapest copy now. Fastest, but any condition or language.",
    PriceType.TREND to "Smoothed sale price. One odd cheap copy won't set it off.",
    PriceType.AVG7 to "Last week's sales. Steady.",
    PriceType.AVG1 to "Yesterday's sales. Quick but jumpy.",
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
        "Set a price on a wishlist card to get a notification when it's that cheap or cheaper.",
        style = small, color = muted,
    )
    if (!s.autoUpdate) {
        Text(
            "Automatic price updates are off (Prices), so alerts only come after “Update prices now”.",
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
            "Alerts use ${s.source.label}'s prices (Prices → Price source).",
            style = small, color = muted,
        )
    }
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        title = "Also check CardTrader",
        body = "Shows CardTrader's cheapest offer; alerts count it too.",
        checked = s.cardTraderOn,
        onChange = act.onCardTrader,
    )
    if (!s.cardTraderOn) return
    CardTraderTokenRow(s.ctStatus, act)
    SwitchRow(
        title = "Only CardTrader Zero offers",
        body = "Shipped together from CardTrader's hub. Off: every seller counts.",
        checked = s.ctZeroOnly,
        onChange = act.onZeroOnly,
    )
    Spacer(Modifier.height(4.dp))
    DropdownSelector("Language on CardTrader", s.ctLanguage, CT_LANGUAGES.map { it.first }, { code -> CT_LANGUAGES.firstOrNull { it.first == code }?.second ?: code }, act.onLanguage, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    DropdownSelector("Condition on CardTrader", s.ctCondition, CtCondition.entries.toList(), { it.label }, act.onCondition, Modifier.fillMaxWidth())
    Text(
        "Leaves out signed, altered and graded copies. Without shipping.",
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
        "On cardtrader.com: your profile's settings → API. Kept on this phone only.",
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
        "Swipe an arrived card right: it moves to Received until cleared.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    DropdownSelector("Clear received cards automatically", clear, ReceivedClear.entries.toList(), { it.label }, onClear, Modifier.fillMaxWidth())
    Spacer(Modifier.height(4.dp))
    SwitchRow(
        title = "Add received cards to the collection",
        body = "Into Unsorted, near mint. Leave off if you import your orders.",
        checked = toCollection,
        onChange = onToCollection,
    )
}

/** Settings → Swiping cards in the collection: what a swipe to the right and to the left does. Since 1.38. */
@Composable
internal fun SwipeSection(right: SwipeAction, left: SwipeAction, onPick: (right: Boolean, SwipeAction) -> Unit) {
    Text("Swiping cards in the collection", style = MaterialTheme.typography.titleMedium)
    Text(
        "In the list and compact views. Every swipe can be undone.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    DropdownSelector("Swipe right →", right, SwipeAction.entries.toList(), { it.label }, { onPick(true, it) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    DropdownSelector("← Swipe left", left, SwipeAction.entries.toList(), { it.label }, { onPick(false, it) }, Modifier.fillMaxWidth())
}
