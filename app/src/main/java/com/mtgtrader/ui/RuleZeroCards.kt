package com.mtgtrader.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mtgtrader.container
import com.mtgtrader.data.BracketAxes
import com.mtgtrader.data.BracketAxis
import com.mtgtrader.data.Brackets
import com.mtgtrader.data.Deck
import com.mtgtrader.data.PowerSource
import com.mtgtrader.data.RuleZeroCard
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * The rule-zero cards full screen, for showing the table: swipe between bracket and power level.
 * They're drawn by the app from Commander Salt's data (the power level from the chosen source);
 * Commander Salt's own images are one menu tap away. The screen stays on at full brightness.
 */
@Composable
fun RuleZeroDialog(deck: Deck, start: RuleZeroCard, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            (view.parent as? DialogWindowProvider)?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
            onDispose { view.keepScreenOn = false }
        }
        val context = LocalContext.current
        val c = context.container
        val source by c.settings.powerSource.collectAsStateWithLifecycle()
        val pager = rememberPagerState(initialPage = start.ordinal) { RuleZeroCard.entries.size }
        val scope = rememberCoroutineScope()
        var original by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf(false) }
        val layers = remember { mutableStateMapOf<RuleZeroCard, GraphicsLayer>() }
        val data by produceState(SaltCard.decode(deck.saltCard), deck.saltCard) {
            if (value == null) value = c.decks.loadCardData(deck)
        }
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabRow(
                    selectedTabIndex = pager.currentPage,
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                ) {
                    RuleZeroCard.entries.forEachIndexed { i, card ->
                        Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(card.label) })
                    }
                }
                IconButton(onClick = {
                    val current = RuleZeroCard.entries[pager.currentPage]
                    scope.launch {
                        val file = if (original) {
                            c.decks.cardFile(deck.archidektId, current).takeIf { it.exists() }
                        } else {
                            layers[current]?.let { saveLayer(context, it, deck, current) }
                        }
                        file?.let { shareCard(context, it, deck, current, original, source) }
                    }
                }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (original) "Show the app's cards" else "Show Commander Salt's original") },
                            onClick = { menu = false; original = !original },
                        )
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                val card = RuleZeroCard.entries[page]
                when {
                    original -> OriginalCardPage(deck, card)
                    data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (deck.saltId == null) "Commander Salt hasn't scored this deck yet." else "Getting the card details from Commander Salt…",
                            color = Color.White,
                        )
                    }
                    else -> {
                        val layer = rememberGraphicsLayer()
                        LaunchedEffect(layer) { layers[card] = layer }
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val capture = Modifier.drawWithContent {
                                layer.record { this@drawWithContent.drawContent() }
                                drawLayer(layer)
                            }
                            when (card) {
                                RuleZeroCard.BRACKET -> BracketCardView(deck, data!!, capture)
                                RuleZeroCard.POWER -> PowerCardView(deck, data!!, source, capture)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- The cards --------------------------------------------------------------------------------

private val HEADER_TINT = Color(0xCC26215C)
private val BAR = Color(0xFF7F77DD)

/** Fixed tile colours per bracket (light tints with dark text, readable on light and dark themes). */
private fun bracketColors(b: Int?): Pair<Color, Color> = when (b) {
    1 -> Color(0xFFE1F5EE) to Color(0xFF085041)
    2 -> Color(0xFFEAF3DE) to Color(0xFF27500A)
    3 -> Color(0xFFFAEEDA) to Color(0xFF633806)
    4 -> Color(0xFFFAECE7) to Color(0xFF712B13)
    5 -> Color(0xFFFCEBEB) to Color(0xFF791F1F)
    else -> Color(0xFFF1EFE8) to Color(0xFF444441)
}

/** Power meter colours, green (1) to red (10). */
private val METER = listOf(
    0xFF5DCAA5, 0xFF5DCAA5, 0xFF97C459, 0xFF97C459, 0xFFEF9F27,
    0xFFEF9F27, 0xFFD85A30, 0xFFD85A30, 0xFFE24B4A, 0xFFA32D2D,
).map(::Color)

@Composable
fun BracketCardView(deck: Deck, data: SaltCard, modifier: Modifier = Modifier) {
    CardFrame(deck, "Bracket", modifier) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BracketTile("Baseline · WotC rules", deck.bracketBaseline, Modifier.weight(1f))
            BracketTile("Realistic · how it plays", deck.bracketRealistic, Modifier.weight(1f))
        }
        Section("Bracket criteria") {
            val criteria = listOf(
                "Two-card combos" to data.twoCardCombos.size,
                "Game changers" to data.gameChangers.size,
                "Early-game combos" to data.earlyCombos.size,
                "Extra turns" to data.extraTurns.size,
                "Mass land denial" to data.massLandDenial.size,
            ).sortedByDescending { it.second > 0 }
            criteria.forEach { (label, n) -> CriterionRow(label, n) }
        }
        if (data.gameChangers.isNotEmpty()) {
            Section("Game changers") { Text(data.gameChangers.joinToString(", "), style = MaterialTheme.typography.bodyMedium) }
        }
        if (data.axes.isNotEmpty()) {
            Section("How it plays (1–5)") {
                BracketAxis.entries.forEach { axis ->
                    data.axes[axis.key]?.let { rating ->
                        val level = BracketAxes.level(rating)
                        BarRow(axis.label, level / 5f, level.toString())
                    }
                }
            }
        }
        val combos = data.twoCardCombos + data.earlyCombos
        if (combos.isNotEmpty() || data.notes.isNotEmpty()) {
            Section("Rule zero: worth mentioning") {
                combos.distinct().forEach { Text(it.joinToString(" + "), style = MaterialTheme.typography.bodyMedium) }
                data.notes.forEach { note ->
                    Text(
                        note.why.ifBlank { note.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Footer("Data: Commander Salt", deck)
    }
}

@Composable
fun PowerCardView(deck: Deck, data: SaltCard, source: PowerSource, modifier: Modifier = Modifier) {
    CardFrame(deck, "Power level", modifier) {
        val power = deck.power(source)
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(power?.let { Scores.power(it, source) } ?: "—", fontSize = 44.sp, fontWeight = FontWeight.SemiBold, lineHeight = 46.sp)
                Spacer(Modifier.width(8.dp))
                Text("/ 10", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp).weight(1f))
                deck.archetype?.let { Box(Modifier.padding(bottom = 8.dp)) { Pill(it) } }
            }
            Text(
                if (source == PowerSource.COMMANDER_SALT) listOfNotNull(data.playStyle?.lowercase(), source.site).joinToString(" · ") else "by ${source.site}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Meter(power ?: 0.0, Modifier.padding(top = 8.dp))
            if (power == null && source == PowerSource.EDH_POWER_LEVEL) {
                Text(
                    deck.edhPowerError?.let { "edhpowerlevel.com couldn't rate this deck: $it" } ?: "Not rated by edhpowerlevel.com yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Section(null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Label("Saltiness")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(deck.saltPercent?.let { "${it.roundToInt()}%" } ?: "—", fontWeight = FontWeight.SemiBold)
                        data.saltIntensity?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(it.lowercase(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    data.saltDriver?.let { Text("mostly ${it.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Column(Modifier.weight(1f)) {
                    Label("Manabase")
                    data.manaFixing?.let { Text("Fixing $it%", style = MaterialTheme.typography.bodySmall) }
                    data.onCurve?.let { Text("On curve $it%", style = MaterialTheme.typography.bodySmall) }
                    data.manaQuality?.let { Text("Quality $it%", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (data.effects.isNotEmpty()) {
            Section("Interaction and effects") {
                val counts = SaltEffect.entries.mapNotNull { e -> data.effects[e.key]?.let { e to it } }
                val shown = counts.filter { it.second > 0 }.sortedByDescending { it.second }
                val max = shown.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                shown.forEach { (e, n) -> BarRow(e.label, n / max.toFloat(), n.toString()) }
                val none = counts.filter { it.second == 0 }.map { it.first.label.lowercase() }
                if (none.isNotEmpty()) {
                    Text(
                        "None: " + none.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (data.wincons.isNotEmpty()) {
            Section("Win conditions besides combos") {
                data.wincons.chunked(2).forEach { pair ->
                    Row {
                        pair.forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Footer(
            if (source == PowerSource.COMMANDER_SALT) "Data: Commander Salt" else "Power level: edhpowerlevel.com · data: Commander Salt",
            deck,
        )
    }
}

@Composable
private fun CardFrame(deck: Deck, kind: String, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            Box(Modifier.fillMaxWidth().height(116.dp)) {
                deckArtUrl(deck)?.let { url ->
                    AsyncImage(
                        // Software bitmaps, so the card can be saved as an image for sharing.
                        model = ImageRequest.Builder(LocalContext.current).data(url).allowHardware(false).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(Modifier.fillMaxSize().background(HEADER_TINT))
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(kind, color = Color(0xFFAFA9EC), style = MaterialTheme.typography.labelMedium)
                    Text(deck.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            deck.commanders,
                            color = Color(0xFFCECBF6),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        ColorPips(deck.colorIdentity)
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun BracketTile(label: String, bracket: Int?, modifier: Modifier) {
    val (bg, fg) = bracketColors(bracket)
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
        Text(bracket?.toString() ?: "—", color = fg, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, lineHeight = 46.sp)
        Text(Brackets.name(bracket) ?: "", color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CriterionRow(label: String, count: Int) {
    val hit = count > 0
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (hit) Icons.Default.Warning else Icons.Default.Check,
            null,
            tint = if (hit) Color(0xFFBA7517) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (hit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (hit) FontWeight.SemiBold else null,
            color = if (hit) Color(0xFFBA7517) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BarRow(label: String, fraction: Float, value: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp), maxLines = 1)
        Box(Modifier.weight(1f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(9.dp).clip(RoundedCornerShape(5.dp)).background(BAR))
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun Meter(power: Double, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 10) {
            val fill = (power - i).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (fill > 0f) Box(Modifier.fillMaxWidth(fill).height(12.dp).background(METER[i]))
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF3C3489),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEEDFE)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    HorizontalDivider(thickness = 0.5.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        title?.let { Label(it) }
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun Footer(credit: String, deck: Deck) {
    HorizontalDivider(thickness = 0.5.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(credit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        val by = listOfNotNull(deck.owner.takeIf { it.isNotBlank() }, deck.scoredAt?.let(Fmt::date)).joinToString(" · ")
        Text(by, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The deck's featured art from Archidekt, or its commander's art. */
fun deckArtUrl(deck: Deck): String? =
    deck.artUrl ?: deck.commanderScryfallId?.let { id -> "https://cards.scryfall.io/art_crop/front/${id[0]}/${id[1]}/$id.jpg" }

// ---- Commander Salt's own images --------------------------------------------------------------

@Composable
private fun OriginalCardPage(deck: Deck, card: RuleZeroCard) {
    val repo = LocalContext.current.container.decks
    val file = remember(deck.archidektId, card) { repo.cardFile(deck.archidektId, card) }
    // Bumped after each download so the image reloads; the card is fetched again only when it's missing.
    var loadedAt by remember(card) { mutableLongStateOf(if (file.exists()) file.lastModified() else 0L) }
    var error by remember(card) { mutableStateOf<String?>(null) }
    var attempt by remember(card) { mutableIntStateOf(0) }
    LaunchedEffect(card, attempt) {
        if (loadedAt != 0L) return@LaunchedEffect
        error = null
        try {
            loadedAt = repo.downloadCard(deck, card).lastModified()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message ?: "download failed"
        }
    }
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        when {
            loadedAt != 0L -> AsyncImage(
                model = file,
                contentDescription = "${card.label} rule-zero card for ${deck.name}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(360f / 504f).clip(RoundedCornerShape(12.dp)),
            )
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't get the ${card.label.lowercase()} card: $error", color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { attempt++ }) { Text("Try again") }
            }
            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}

// ---- Sharing ----------------------------------------------------------------------------------

/** Saves the card as drawn on screen as a PNG, next to Commander Salt's images (which the share provider covers). */
private suspend fun saveLayer(context: Context, layer: GraphicsLayer, deck: Deck, card: RuleZeroCard): File? {
    val bitmap = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull() ?: return null
    return withContext(Dispatchers.IO) {
        val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val dir = File(context.filesDir, "rulezero").apply { mkdirs() }
        File(dir, "${deck.archidektId}_${card.name.lowercase()}_app.png").also { f ->
            f.outputStream().use { soft.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

private fun shareCard(context: Context, file: File, deck: Deck, card: RuleZeroCard, original: Boolean, source: PowerSource) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val credit = when {
        original -> "commandersalt.com"
        card == RuleZeroCard.POWER && source == PowerSource.EDH_POWER_LEVEL -> "edhpowerlevel.com, commandersalt.com"
        else -> "commandersalt.com"
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "${deck.name}: ${card.label.lowercase()} rule-zero card ($credit)")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share rule-zero card"))
}
