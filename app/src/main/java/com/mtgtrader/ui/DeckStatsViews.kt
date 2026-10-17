package com.mtgtrader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtgtrader.container
import com.mtgtrader.data.ColourCount
import com.mtgtrader.data.ColourSources
import com.mtgtrader.data.DeckToken
import com.mtgtrader.data.ManaCurve
import com.mtgtrader.data.ScryCard
import kotlin.math.roundToInt

/** Scryfall's picture of a printing (a token's too), made from its id. */
fun scryfallImage(id: String) = "https://cards.scryfall.io/normal/front/${id[0]}/${id[1]}/$id.jpg"

private fun colourName(c: Char) = when (c) {
    'W' -> "white"
    'U' -> "blue"
    'B' -> "black"
    'R' -> "red"
    'G' -> "green"
    else -> "colourless"
}

private val InkOnTint = Color(0xFF111111)

/** A folding heading on the deck page for the stats and tokens, like the card sections' ones. Since 1.44. */
@Composable
internal fun StatsHeading(title: String, folded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = onToggle)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(
                if (folded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                if (folded) "Show $title" else "Hide $title",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp).size(20.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun StatsCardBox(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** The mana curve: a bar per mana value, 0 to 7+, with the count over it. */
@Composable
internal fun ManaCurveCard(curve: ManaCurve) = StatsCardBox("Mana curve") {
    val max = curve.counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val bar = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        curve.counts.forEachIndexed { i, n ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (n > 0) "$n" else "", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.height(90.dp), contentAlignment = Alignment.BottomCenter) {
                    if (n > 0) {
                        Box(
                            Modifier.width(26.dp).height((90f * n / max).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(bar),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(if (i == 7) "7+" else "$i", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Note(
        if (curve.spells == 0) "No spells yet (lands are left out)."
        else "${curve.spells} spells (lands left out) · average mana value ${"%.2f".format(java.util.Locale.ROOT, curve.average)}"
    )
}

/**
 * The colours in the costs and the deck's mana sources, each as one bar split by colour (as Archidekt draws
 * them), with the switches for lands only and colourless. Tapping a colour of the production bar lists its
 * sources.
 */
@Composable
internal fun ColoursCard(
    costs: List<ColourCount>,
    sources: List<ColourSources>,
    landsOnly: Boolean,
    colourless: Boolean,
    landCount: Int,
    detailsMissing: Int,
    onLandsOnly: (Boolean) -> Unit,
    onColourless: (Boolean) -> Unit,
) = StatsCardBox("Colours") {
    var open by rememberSaveable { mutableStateOf<Char?>(null) }
    Text("Mana cost", style = MaterialTheme.typography.titleSmall)
    if (costs.isEmpty()) Note("No coloured mana symbols in the costs.") else SplitBar(costs)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Mana production", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        SwitchChip("Lands only", landsOnly, onLandsOnly)
        Spacer(Modifier.width(6.dp))
        SwitchChip("Colourless", colourless, onColourless)
    }
    val counts = sources.map { ColourCount(it.colour, it.count) }
    if (counts.isEmpty()) Note(if (landsOnly) "No lands that make mana yet." else "No cards that make mana yet.")
    else SplitBar(counts, selected = open) { open = if (open == it) null else it }
    Note(
        "Cost: coloured symbols in the cards' costs (a hybrid counts for both colours). Production: sources " +
            (if (landsOnly) "among the deck's $landCount lands" else "among all the deck's cards") +
            ", each counted once for every colour it can make. Tap a colour to see its cards."
    )
    if (detailsMissing > 0) {
        Note("$detailsMissing card(s) are still waiting for their details from Scryfall; they're fetched when the phone is online.")
    }
    sources.firstOrNull { it.colour == open }?.let { s -> SourceList(s) }
}

@Composable
private fun SwitchChip(label: String, on: Boolean, onChange: (Boolean) -> Unit) = FilterChip(
    selected = on,
    onClick = { onChange(!on) },
    label = { Text(label) },
    leadingIcon = if (on) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null,
)

@Composable
private fun SourceList(s: ColourSources) {
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        ManaCostView("{${s.colour}}", size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text("${s.count} ${colourName(s.colour)} source" + if (s.count == 1) "" else "s", style = MaterialTheme.typography.titleSmall)
    }
    s.cards.forEach { line ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${line.quantity}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(24.dp))
            Text(line.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (line.finds.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "finds " + line.finds.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/**
 * One bar split by colour, as Archidekt draws it: slanted joins, each part with its symbol and count (when
 * it's wide enough), and under it every colour's count and share. [onTap] makes the colours tappable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitBar(parts: List<ColourCount>, selected: Char? = null, onTap: ((Char) -> Unit)? = null) {
    val total = parts.sumOf { it.count }.coerceAtLeast(1)
    val ring = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val slant = 7.dp.toPx()
            val gap = 3.dp.toPx()
            var x = 0f
            parts.forEachIndexed { i, p ->
                val w = size.width * p.count / total
                val l = if (i == 0) -slant else x + gap / 2
                val r = if (i == parts.lastIndex) size.width + slant else x + w - gap / 2
                val path = Path().apply {
                    moveTo(l + slant, 0f); lineTo(r + slant, 0f); lineTo(r - slant, size.height); lineTo(l - slant, size.height); close()
                }
                drawPath(path, ManaTint[p.colour.toString()] ?: GenericTint)
                if (p.colour == selected) drawPath(path, ring, style = Stroke(3.dp.toPx()))
                x += w
            }
        }
        Row(Modifier.fillMaxSize()) {
            parts.forEach { p ->
                BoxWithConstraints(
                    Modifier.weight(p.count.toFloat()).fillMaxSize().then(if (onTap != null) Modifier.clickable { onTap(p.colour) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    val w = maxWidth
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (w > 26.dp) ManaCostView("{${p.colour}}", size = 18.dp)
                        if (w > 62.dp) {
                            Spacer(Modifier.width(5.dp))
                            Text("${p.count}", style = MaterialTheme.typography.labelLarge, color = InkOnTint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEach { p ->
            Row(
                Modifier.then(if (onTap != null) Modifier.clickable { onTap(p.colour) } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ManaCostView("{${p.colour}}", size = 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    "${p.count} · ${(100.0 * p.count / total).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (p.colour == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A row of up to three tokens: picture, name, and the cards that make it. */
@Composable
internal fun TokenRow(tokens: List<DeckToken>, onOpen: (DeckToken) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tokens.forEach { t ->
            Column(Modifier.weight(1f).clickable { onOpen(t) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    RetryingImage(scryfallImage(t.id), Modifier.fillMaxSize(), contentDescription = t.label, contentScale = ContentScale.Crop)
                }
                Text(t.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text(
                    t.makers.joinToString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        repeat(3 - tokens.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * A token large, with its other printings to swipe through (fetched from Scryfall, so online only). The
 * printing the deck's cards link to comes first. Tapping the picture opens it full screen.
 */
@Composable
internal fun TokenDialog(token: DeckToken, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val others by produceState<List<ScryCard>?>(null, token.oracleId) {
        value = token.oracleId?.let { id -> runCatching { c.scryfall.printsOfOracle(id) }.getOrNull() } ?: emptyList()
    }
    val pages: List<Pair<String, String?>> = remember(others) {
        listOf(token.id to null) + others.orEmpty().filter { it.id != token.id }.map { it.id to "${it.setName} · #${it.collectorNumber}" }
    }
    val first = others?.firstOrNull { it.id == token.id }?.let { "${it.setName} · #${it.collectorNumber}" }
    var zoom by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(token.label, style = MaterialTheme.typography.titleLarge)
                Text("Made by " + token.makers.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                val pager = rememberPagerState { pages.size }
                HorizontalPager(pager, Modifier.fillMaxWidth().height(380.dp), pageSpacing = 12.dp) { i ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val url = scryfallImage(pages[i].first)
                        Box(Modifier.aspectRatio(63f / 88f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { zoom = url }) {
                            RetryingImage(url, Modifier.fillMaxSize(), contentDescription = token.label, contentScale = ContentScale.Fit)
                        }
                    }
                }
                val page = pages.getOrNull(pager.currentPage)
                Text(
                    (if (pager.currentPage == 0) first ?: "The printing the deck's cards link to" else page?.second.orEmpty()) +
                        if (pages.size > 1) "  ·  ${pager.currentPage + 1} of ${pages.size}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    others == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Note("Looking up its other printings…")
                    }
                    pages.size > 1 -> Note("Swipe to see its other printings.")
                    token.oracleId == null || others!!.isEmpty() -> Note("Other printings can't be looked up right now (they need internet).")
                    else -> Note("Scryfall knows no other printings of it.")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
    zoom?.let { url -> CardImageDialog(url) { zoom = null } }
}
