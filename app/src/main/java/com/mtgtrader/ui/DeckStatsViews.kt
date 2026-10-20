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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.OutlinedButton
import com.mtgtrader.data.scryfallImage
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
private fun Note(text: String, modifier: Modifier = Modifier) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

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
 * A token large, with a button to its other printings (since 1.45): pick one and the deck shows it from then
 * on (remembered per deck, synced). Tapping the picture opens it full screen. The printings come from
 * Scryfall, so choosing needs internet.
 */
@Composable
internal fun TokenDialog(token: DeckToken, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    // Every printing, newest first; null while loading, empty when Scryfall can't be reached.
    val prints by produceState<List<ScryCard>?>(null, token.oracleId) {
        value = token.oracleId?.let { id -> runCatching { c.scryfall.printsOfOracle(id) }.getOrNull() } ?: emptyList()
    }
    fun where(id: String) = prints?.firstOrNull { it.id == id }?.let { "${it.setName} · #${it.collectorNumber}" }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!choosing) {
                    Text(token.label, style = MaterialTheme.typography.titleLarge)
                    Note("Made by " + token.makers.joinToString())
                    val url = scryfallImage(token.id)
                    Box(
                        Modifier.height(400.dp).aspectRatio(63f / 88f).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer).clickable { zoom = url },
                    ) {
                        RetryingImage(url, Modifier.fillMaxSize(), contentDescription = token.label, contentScale = ContentScale.Fit)
                    }
                    where(token.id)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (token.picked) Text("Your pick for this deck", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { choosing = true }, enabled = token.oracleId != null) {
                            Icon(Icons.Default.Style, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Other printings")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                } else {
                    Text("${token.name} · choose a printing", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    val list = prints
                    when {
                        list == null -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Note("Looking up its printings on Scryfall…")
                        }
                        list.isEmpty() -> Note("Its printings can't be looked up right now: choosing one needs internet.")
                        else -> {
                            Note("${list.size} printings on Scryfall, newest first. The one you pick shows for this deck.")
                            // The cards' own printing first, then the rest.
                            val ids = listOf(token.defaultId) + list.map { it.id }.filter { it != token.defaultId }
                            LazyVerticalGrid(
                                GridCells.Fixed(3),
                                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(ids, key = { it }) { id ->
                                    PrintingTile(
                                        id,
                                        set = list.firstOrNull { it.id == id }?.setName.orEmpty(),
                                        number = list.firstOrNull { it.id == id }?.collectorNumber,
                                        own = id == token.defaultId,
                                        chosen = id == token.id,
                                    ) {
                                        onPick(id.takeIf { it != token.defaultId })
                                        choosing = false
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        if (token.picked) {
                            TextButton(onClick = { onPick(null); choosing = false }) { Text("Use the cards' own printing") }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { choosing = false }) { Text("Cancel") }
                    }
                }
            }
        }
    }
    zoom?.let { url -> CardImageDialog(url) { zoom = null } }
}

/** A printing to choose: its picture, set and number; the chosen one has a gold border and a tick. */
@Composable
internal fun PrintingTile(id: String, set: String, number: String?, own: Boolean, chosen: Boolean, onClick: () -> Unit) {
    val gold = MaterialTheme.colorScheme.primary
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (chosen) Modifier.border(3.dp, gold, RoundedCornerShape(6.dp)) else Modifier),
            ) {
                RetryingImage(scryfallImage(id, "small"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (chosen) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(gold), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, "Chosen", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Text(set, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(number?.let { "#$it" }, if (own) "the cards' own" else null).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * When the deck can cast its commander and has its colours (since 1.46): a table of the chance by each turn,
 * the commander(s) first, then each colour and all colours together. [odds] is null while it's worked out.
 */
@Composable
internal fun CommanderOddsCard(odds: com.mtgtrader.data.ManaOdds?, missing: Int) = StatsCardBox("Casting the commander") {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var about by rememberSaveable { mutableStateOf(false) }
    if (odds == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Note("Shuffling the deck…")
        }
        return@StatsCardBox
    }
    odds.commanders.forEach { c ->
        val turn = c.byTurn.indexOfFirst { it >= 50 }.takeIf { it >= 0 }?.plus(1)
        Text(
            "${c.name} (${c.manaValue} mana): " + if (turn == null) "not by turn ${odds.turns} in most games"
            else "turn $turn in ${c.byTurn[turn - 1]}% of games",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    val labelWidth = 92.dp
    Row {
        Spacer(Modifier.width(labelWidth))
        (1..odds.turns).forEach {
            Text(
                "T$it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    odds.commanders.forEach { c ->
        OddsRow(labelWidth, odds.commanders.size > 1, { Text(if (odds.commanders.size > 1) c.name else "Commander", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }, c.byTurn)
    }
    odds.colours.forEach { (colour, values) ->
        OddsRow(labelWidth, false, { ManaCostView("{$colour}", size = 18.dp) }, values)
    }
    odds.allColours?.let { values -> OddsRow(labelWidth, false, { Text("All colours", style = MaterialTheme.typography.bodyMedium) }, values) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Note(
            "% of games, from ${"%,d".format(java.util.Locale.ROOT, odds.games)} shuffles of this deck (playing alone, a best case).",
            Modifier.weight(1f),
        )
        TextButton(onClick = { about = !about }) { Text(if (about) "Less" else "How?") }
    }
    if (about) {
        Note(
            "The app plays the deck alone: it draws (also on turn 1), keeps a hand like Commander players do (a free " +
                "mulligan, then London mulligans), plays a land each turn and casts mana makers when it can. Counted: " +
                "${odds.lands} lands, ${odds.rocks} mana rocks, ${odds.creatures} mana creatures (from the turn after) and " +
                "${odds.ramp} land ramp spells, all costing up to 4. Lands that enter tapped come in tapped. Not counted: " +
                "opponents, removal, Treasure, rituals and cost reducers. The method follows Frank Karsten's mana base " +
                "articles, with this deck's own cards instead of his tables."
        )
        TextButton(onClick = {
            runCatching { uriHandler.openUri("https://www.tcgplayer.com/content/article/How-Many-Sources-Do-You-Need-to-Consistently-Cast-Your-Spells-A-2022-Update/dc23a7d2-0a16-4c0b-ad36-586fcca03ad8/") }
        }) { Text("Frank Karsten's article") }
    }
    if (missing > 0) Note("$missing card(s) are still waiting for their details from Scryfall, so they count as plain spells for now.")
}

@Composable
private fun OddsRow(labelWidth: androidx.compose.ui.unit.Dp, small: Boolean, label: @Composable () -> Unit, values: List<Int>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(labelWidth).padding(end = 4.dp)) { label() }
        val primary = MaterialTheme.colorScheme.primary
        values.forEach { v ->
            val strength = (v / 100f).coerceIn(0.08f, 1f)
            Box(
                Modifier.weight(1f).padding(1.dp).height(if (small) 24.dp else 26.dp).clip(RoundedCornerShape(4.dp))
                    .background(primary.copy(alpha = strength * 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$v",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (v > 55) InkOnTint else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * When the deck can win (since 1.52): the turn it usually wins by and the turn of 9 in 10 games, the share of games
 * won by each turn (and by a combo, when it has one that wins), where the damage came from, and what was counted.
 * [win] is null while it's worked out.
 */
@Composable
internal fun WinningCard(win: com.mtgtrader.data.WinOdds?, missing: Int) = StatsCardBox("Winning the game") {
    var about by rememberSaveable { mutableStateOf(false) }
    if (win == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Note("Playing the deck 10,000 times…")
        }
        return@StatsCardBox
    }
    Text(winHeadline(win), style = MaterialTheme.typography.bodyMedium)
    val turns = win.window
    val labelWidth = 72.dp
    Row {
        Spacer(Modifier.width(labelWidth))
        turns.forEach {
            Text(
                "T$it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    OddsRow(labelWidth, false, { Text("Win", style = MaterialTheme.typography.bodyMedium) }, turns.map { win.byTurn[it - 1] })
    if (win.comboShare > 0) {
        OddsRow(labelWidth, true, { Text("by combo", style = MaterialTheme.typography.bodyMedium) }, turns.map { win.byCombo[it - 1] })
    }
    Note(winDamageNote(win))
    if (win.usual == null) Note(WIN_SLOW_NOTE)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Note(
            "% of games won by each turn, from ${"%,d".format(java.util.Locale.ROOT, win.games)} shuffles of this deck " +
                "(playing alone against three players at 40 life, a best case).",
            Modifier.weight(1f),
        )
        TextButton(onClick = { about = !about }) { Text(if (about) "Less" else "How?") }
    }
    if (about) {
        val n = win.counted
        Note(
            "The app plays the deck alone, with the mana as for casting the commander. Each turn it casts its commander, " +
                "creatures, token makers, equipment and auras and the cards that deal damage, the most expensive first; it puts " +
                "the equipment and auras on its commander (or else its biggest creature), keeps pump spells for combat and " +
                "attacks with everything that can (nobody blocks). It wins when all three opponents are at 0, a commander has " +
                "dealt 21 damage to each, or a combo wins. Counted: ${n.creatures} creatures, ${n.tokenMakers} token makers, " +
                "${n.triggers} damage triggers (casting spells, creatures or lands entering), ${n.drains} drain or burn cards, " +
                "${n.gear} equipment and auras, ${n.pumps} pump spells" +
                (if (n.combos.isEmpty()) ", and no combo that wins on its own." else ", and combos that win on their own: ${n.combos.joinToString("; ")}.") +
                " Not counted: removal, blockers, tutors, sacrifice outlets and abilities the app can't read from the card text."
        )
    }
    if (missing > 0) Note("$missing card(s) are still waiting for their details from Scryfall, so they don't count yet.")
}

/** "Usually wins by turn 11; 9 in 10 games by turn 13. Fastest 1 in 10: turn 9." */
internal fun winHeadline(win: com.mtgtrader.data.WinOdds): String {
    val usual = win.usual ?: return "Not by turn ${win.turns} in most games: ${win.byTurn.last()}% of games by turn ${win.turns}."
    return "Usually wins by turn $usual" + (win.nineInTen?.let { "; 9 in 10 games by turn $it" } ?: "") + "." +
        (win.fastest?.takeIf { it < usual }?.let { " Fastest 1 in 10: turn $it." } ?: "")
}

/** "Damage: combat 52%, commander damage 48%. A combo wins 25% of games. A commander knocked a player out in 79% of the wins." */
internal fun winDamageNote(win: com.mtgtrader.data.WinOdds): String = buildString {
    if (win.damageText.isNotEmpty()) append("Damage: ").append(win.damageText.replaceFirstChar(Char::lowercase))
    if (win.comboShare > 0) append(" A combo wins ${win.comboShare}% of games.")
    if (win.commanderKnockouts >= 5) append(" Commander damage knocked a player out in ${win.commanderKnockouts}% of the wins.")
}.trim()

internal const val WIN_SLOW_NOTE = "Much of this deck's damage may come from what the app can't read yet (sacrifice triggers, " +
    "its commander's own ability…), so it's slower here than at the table."
