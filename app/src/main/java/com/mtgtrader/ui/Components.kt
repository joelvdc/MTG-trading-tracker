package com.mtgtrader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import coil.network.HttpException
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mtgtrader.container
import com.mtgtrader.data.Balance
import com.mtgtrader.data.CONDITIONS
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Finish
import com.mtgtrader.data.LANGUAGES
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceTrend
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.Verdict
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Fmt {
    private val eur = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = Currency.getInstance("EUR")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun money(v: Double?): String = if (v == null) "—" else eur.format(v)
    fun signedMoney(v: Double): String = (if (v > 0.004) "+" else if (v < -0.004) "−" else "") + eur.format(abs(v))
    fun date(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    fun dateTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

    /** Parses "3,50" or "3.50"; null for blank/invalid. */
    fun parseMoney(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
}

/** Card image; with [enlargeable], tapping it opens the full-screen [CardImageDialog]. */
@Composable
fun CardThumb(url: String?, modifier: Modifier = Modifier, width: Int = 44, enlargeable: Boolean = false) {
    var enlarged by remember { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier
            .width(width.dp)
            .height((width * 88 / 63).dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (enlargeable && url != null) Modifier.clickable(onClickLabel = "Enlarge card image") { enlarged = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // No image, or it couldn't be loaded (yet): show a card symbol rather than an empty box.
        if (url == null || failed) Text("🃏", fontSize = (width / 2.5f).sp)
        if (url != null) RetryingImage(url, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) { failed = it }
    }
    if (enlarged && url != null) CardImageDialog(url) { enlarged = false }
}

private data class ImageFailure(val missing: Boolean, val atReconnect: Int)

/** Pauses between retries of a failed image; the last one repeats while the image is on screen. */
private val RETRY_DELAYS_MS = longArrayOf(1_500, 3_000, 6_000, 12_000, 30_000, 60_000)

/**
 * Loads an image and keeps trying if that fails: after a growing pause, and at once when the phone
 * reconnects. On its own the image loader never retries a failed load (and while offline it only
 * answers from cache), so a short network drop used to leave cards and set symbols blank. An image
 * that doesn't exist on the server (HTTP 404) isn't retried.
 */
@Composable
fun RetryingImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
    onFailedChange: (Boolean) -> Unit = {},
) {
    val reconnects by LocalContext.current.container.network.reconnects.collectAsStateWithLifecycle()
    var attempt by remember(url) { mutableIntStateOf(0) }
    var failure by remember(url) { mutableStateOf<ImageFailure?>(null) }
    LaunchedEffect(failure, reconnects) {
        val f = failure ?: return@LaunchedEffect
        if (f.missing) return@LaunchedEffect
        if (reconnects == f.atReconnect) delay(RETRY_DELAYS_MS[minOf(attempt, RETRY_DELAYS_MS.lastIndex)])
        failure = null
        attempt++
    }
    key(url, attempt) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = modifier,
            onSuccess = { onFailedChange(false) },
            onError = { state ->
                val missing = (state.result.throwable as? HttpException)?.response?.code == 404
                failure = ImageFailure(missing, reconnects)
                onFailedChange(true)
            },
        )
    }
}

/** Scryfall serves every card image in several sizes under the same path; "large" is 672×936. */
fun largeImageUrl(url: String): String = url.replace("/normal/", "/large/")

/** Full-screen card image. Pinch or double-tap to zoom, drag to pan, tap to close. */
@Composable
fun CardImageDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var imageSize by remember { mutableStateOf(IntSize.Zero) }
        fun clamp(o: Offset): Offset {
            val maxX = imageSize.width * (scale - 1) / 2
            val maxY = imageSize.height * (scale - 1) / 2
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = clamp(offset + pan)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .padding(16.dp)
                    .aspectRatio(63f / 88f)
                    .onSizeChanged { imageSize = it }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                // The small image is usually cached already, so it shows at once while the sharper one loads.
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                RetryingImage(largeImageUrl(url), Modifier.fillMaxSize(), contentDescription = "Card image")
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
    }
}

/** Symbols are drawn in their rarity's colour, like on the card. */
@Composable
fun rarityColor(rarity: String): Color = when (rarity) {
    "uncommon" -> Color(0xFF8AA0AC)
    "rare" -> Color(0xFFC9A03E)
    "mythic", "bonus" -> Color(0xFFE0572A)
    "special" -> Color(0xFF9B5CC4)
    else -> MaterialTheme.colorScheme.onSurface
}

/** The expansion symbol of [setCode]; takes no space until the symbol list has loaded. */
@Composable
fun SetSymbol(setCode: String, rarity: String, size: Dp = 16.dp) {
    val icons = LocalContext.current.container.setIcons
    val map by icons.icons.collectAsStateWithLifecycle()
    val url = map[setCode.lowercase()]
    LaunchedEffect(url == null, map.isEmpty()) { if (url == null) icons.onMissing() }
    if (url == null) return
    RetryingImage(url, Modifier.size(size), colorFilter = ColorFilter.tint(rarityColor(rarity)))
}

/** Expansion symbol followed by e.g. "MKM #123" and an optional [suffix]. */
@Composable
fun SetLine(card: CardRef, suffix: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SetSymbol(card.setCode, card.rarity)
        Text(card.setLabel + suffix, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** "FOIL", "ETCHED FOIL", "SURGE FOIL"…; nothing for non-foil copies. */
@Composable
fun FinishTag(card: CardRef, finish: Finish) {
    if (finish.foil) Tag(card.finishName(finish).uppercase(), FoilColor, Color.White)
}

@Composable
fun <T> DropdownSelector(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(optionLabel(selected), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@Composable
fun PriceTypeSelector(selected: PriceType, onSelect: (PriceType) -> Unit, modifier: Modifier = Modifier) {
    DropdownSelector("Cardmarket price", selected, PriceType.entries.toList(), { it.label }, onSelect, modifier)
}

@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit, min: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) {
            Icon(Icons.Default.Remove, "Less")
        }
        Text("$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = { onChange(value + 1) }) { Icon(Icons.Default.Add, "More") }
    }
}

/** Summary of both trade sides with the fairness verdict. */
@Composable
fun BalanceCard(balance: Balance, modifier: Modifier = Modifier) {
    val (color, verdictText) = when (balance.verdict) {
        Verdict.EMPTY -> MaterialTheme.colorScheme.outline to "Add cards to both sides"
        Verdict.FAIR -> VerdictColors.fair to "Fair trade"
        Verdict.FAVORS_YOU -> VerdictColors.favorsYou to "In your favour"
        Verdict.FAVORS_THEM -> VerdictColors.favorsThem to "In their favour"
    }
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("You give", style = MaterialTheme.typography.labelMedium)
                    Text(Fmt.money(balance.give), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("You get", style = MaterialTheme.typography.labelMedium)
                    Text(Fmt.money(balance.get), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Bar showing the share of total value on each side.
            val total = balance.give + balance.get
            val giveFrac = if (total > 0) (balance.give / total).toFloat() else 0.5f
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                if (giveFrac > 0f) Box(Modifier.weight(giveFrac.coerceAtLeast(0.001f)).fillMaxSize().background(VerdictColors.favorsThem.copy(alpha = 0.75f)))
                if (giveFrac < 1f) Box(Modifier.weight((1f - giveFrac).coerceAtLeast(0.001f)).fillMaxSize().background(VerdictColors.favorsYou.copy(alpha = 0.75f)))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                Spacer(Modifier.width(6.dp))
                Text(verdictText, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (balance.verdict != Verdict.EMPTY) {
                    Text(
                        "${Fmt.signedMoney(balance.diff)} (${"%+.0f".format(balance.pct)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (balance.verdict == Verdict.FAVORS_YOU || balance.verdict == Verdict.FAVORS_THEM) {
                val who = if (balance.diff > 0) "You could add" else "They could add"
                Text(
                    "$who ${Fmt.money(abs(balance.diff))} to even it out",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Shows "[message]" with an Undo button for 10 seconds. Returns true if Undo was tapped.
 * Replaces any message already showing, so repeated removals don't queue up.
 */
suspend fun SnackbarHostState.showUndo(message: String): Boolean {
    currentSnackbarData?.dismiss()
    return showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long) ==
        SnackbarResult.ActionPerformed
}

/** Price going up (green ▲), down (red ▼) or unchanged (▬), with the percentage; nothing without data. */
@Composable
fun TrendBadge(trend: PriceTrend?, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelMedium) {
    if (trend == null) return
    val color = when {
        trend.flat -> MaterialTheme.colorScheme.onSurfaceVariant
        trend.up -> TrendColors.up
        else -> TrendColors.down
    }
    Text(trend.label(), color = color, style = style, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = modifier)
}

@Composable
fun PriceTable(prices: PriceSet?, highlight: PriceType, title: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (prices == null || PriceType.entries.all { prices.get(it) == null }) {
            Text("No price guide data for this printing.", style = MaterialTheme.typography.bodySmall)
            return
        }
        prices.trendChange?.let { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Trend vs 30-day average", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TrendBadge(t, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PriceType.entries.forEach { t ->
            val hl = t == highlight
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(t.label, Modifier.weight(1f), fontWeight = if (hl) FontWeight.Bold else null, style = MaterialTheme.typography.bodyMedium)
                Text(Fmt.money(prices.get(t)), fontWeight = if (hl) FontWeight.Bold else null, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

data class EditValues(
    val quantity: Int,
    val finish: Finish,
    val condition: String,
    val language: String,
    val customPrice: Double?,
)

/** Shared editor for a trade item or collection row. */
@Composable
fun EditCardDialog(
    card: CardRef,
    initial: EditValues,
    prices: (foil: Boolean) -> PriceSet?,
    priceType: PriceType,
    allowCustomPrice: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (EditValues) -> Unit,
    onDelete: () -> Unit,
    onChangePrinting: () -> Unit,
) {
    var v by remember { mutableStateOf(initial) }
    val uriHandler = LocalUriHandler.current
    // Cards saved before version 1.2 may not list the finish they were saved with.
    val finishOptions = remember(card) { (card.finishes + initial.finish).distinct() }
    var customText by remember { mutableStateOf(initial.customPrice?.let { "%.2f".format(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardThumb(card.imageUrl, width = 72, enlargeable = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.setName, style = MaterialTheme.typography.bodyMedium)
                        SetLine(card, " · ${card.rarity}")
                        TextButton(onClick = onChangePrinting, enabled = enabled) { Text("Change printing") }
                        TextButton(onClick = { runCatching { uriHandler.openUri(card.cardmarketUrl) } }) {
                            Text("Open on Cardmarket")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
                if (!enabled) {
                    Text(
                        "This trade is applied to your collection. Undo that first to edit it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantity", Modifier.weight(1f))
                    if (enabled) QuantityStepper(v.quantity, { v = v.copy(quantity = it) }) else Text("${v.quantity}")
                }
                if (finishOptions.size > 1) {
                    DropdownSelector("Finish", v.finish, finishOptions, card::finishName, { v = v.copy(finish = it) }, Modifier.fillMaxWidth(), enabled)
                } else if (v.finish.foil) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Finish", Modifier.weight(1f))
                        Text(card.finishName(v.finish), color = FoilColor, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (card.etchedPriceIsApprox(v.finish)) {
                    Text(
                        "Cardmarket has no separate price for etched copies of this printing, so the regular foil price is used." +
                            if (allowCustomPrice) " Enter an agreed price if it differs." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector("Condition", v.condition, CONDITIONS.map { it.first }, { c -> CONDITIONS.first { it.first == c }.second }, { v = v.copy(condition = it) }, Modifier.weight(1f), enabled)
                    DropdownSelector("Language", v.language, LANGUAGES.map { it.first }, { l -> LANGUAGES.firstOrNull { it.first == l }?.second ?: l }, { v = v.copy(language = it) }, Modifier.weight(1f), enabled)
                }
                if (allowCustomPrice) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = {
                            customText = it
                            v = v.copy(customPrice = Fmt.parseMoney(it))
                        },
                        label = { Text("Agreed price per copy (optional)") },
                        placeholder = { Text("Uses Cardmarket price if empty") },
                        singleLine = true,
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
                PriceTable(
                    prices(v.finish.foil),
                    priceType,
                    if (v.finish.foil) "Cardmarket prices (${card.finishName(v.finish).lowercase()})" else "Cardmarket prices",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(v) }, enabled = enabled) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, enabled = enabled) { Text("Remove", color = if (enabled) MaterialTheme.colorScheme.error else Color.Unspecified) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
