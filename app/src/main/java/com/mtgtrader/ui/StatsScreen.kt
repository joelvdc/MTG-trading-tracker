package com.mtgtrader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.CollectionJump
import com.mtgtrader.container
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CollectionFilter
import com.mtgtrader.data.CollectionStats
import com.mtgtrader.data.CollectionStatsResult
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.StatCard
import com.mtgtrader.data.StatEntry
import com.mtgtrader.data.StatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The pie's colours for White, Blue, Black, Red, Green, multicoloured, colourless and lands. */
private val PieColors = mapOf(
    "W" to Color(0xFFE9DFB3), "U" to Color(0xFF3F8FD8), "B" to Color(0xFF55505C), "R" to Color(0xFFD9534F),
    "G" to Color(0xFF3C9A5F), "M" to Color(0xFFD4A82A), "C" to Color(0xFFA7A7AE), "L" to Color(0xFF9C7B57),
)

/** Collection statistics: colours, identities, rarity, types, curve, sets and more. Since 1.21. */
@Composable
fun StatsScreen(nav: NavController) {
    val c = LocalContext.current.container
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val rows by remember { c.db.collectionDao().observeAll() }.collectAsStateWithLifecycle(null)
    val binders = rememberBinders()
    val deckNames by remember { c.db.deckDao().observeCardNames() }.collectAsStateWithLifecycle(emptyList())
    val gameChangers by c.gameChangers.names.collectAsStateWithLifecycle()
    val setDates by c.setIcons.dates.collectAsStateWithLifecycle()
    var binder by rememberSaveable { mutableStateOf<Long?>(null) }
    var mode by rememberSaveable { mutableStateOf(StatMode.CARDS) }

    val stats by produceState<CollectionStatsResult?>(null, rows, binder, priceType, binders, deckNames, gameChangers, setDates) {
        val all = rows ?: return@produceState
        value = withContext(Dispatchers.Default) {
            CollectionStats.compute(
                rows = if (binder == null) all else all.filter { it.item.binderId == binder },
                priceType = priceType,
                binderNames = binders.associate { it.id to it.name },
                deckCardNames = deckNames.map { it.substringBefore(" // ").lowercase() }.toSet(),
                gameChangerNames = gameChangers,
                setDates = setDates,
            )
        }
    }
    fun show(filter: CollectionFilter?) {
        filter ?: return
        c.collectionJump.value = CollectionJump(filter, binder)
        nav.safePopBackStack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Collection stats") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val s = stats
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "binders") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = binder == null, onClick = { binder = null }, label = { Text("All cards") }) }
                    item { FilterChip(selected = binder == Binder.UNSORTED, onClick = { binder = Binder.UNSORTED }, label = { Text(Binder.UNSORTED_NAME) }) }
                    items(binders, key = { it.id }) { b -> FilterChip(selected = binder == b.id, onClick = { binder = b.id }, label = { Text(b.name) }) }
                }
            }
            item(key = "overview") { Overview(s, priceType) }
            item(key = "mode") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StatMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = mode == m, onClick = { mode = m }, shape = SegmentedButtonDefaults.itemShape(i, StatMode.entries.size)) {
                            Text("Charts by ${m.label.lowercase()}")
                        }
                    }
                }
            }
            item(key = "colors") {
                Section("Colors", "Each card once: its one color, multicolor, colorless or land. Tap a line to see those cards.") {
                    PieChart(s.colors, mode, ::show)
                }
            }
            item(key = "identity") {
                Section("Color identity", "For Commander: the colors a card can be played in.") { Bars(s.identities, mode, limit = 10, onPick = ::show) }
            }
            item(key = "rarity") { Section("Rarity") { Bars(s.rarities, mode, keepOrder = true, onPick = ::show) } }
            item(key = "types") { Section("Card types", "A card with two types counts for both.") { Bars(s.types, mode, onPick = ::show) } }
            item(key = "curve") { Section("Mana value", "Non-land cards.") { Columns(s.curve, mode) } }
            item(key = "sets") {
                Section("Top sets") { Bars(s.topSets.sortedByDescending { it.amount(mode) }.take(10), mode, onPick = ::show) }
            }
            if (s.years.size > 1) {
                item(key = "years") {
                    Section("By release year", s.oldest?.let { "Oldest: ${it.row.item.card.name} (${it.row.item.card.setName}, ${it.note})" }) {
                        Columns(s.years, mode, labelEvery = if (s.years.size > 12) 5 else 1)
                    }
                }
            }
            item(key = "decks") {
                Section("Decks", "Cards whose name is in one of your decks.") { Bars(listOf(s.inDecks, s.notInDecks), mode, keepOrder = true, onPick = ::show) }
            }
            item(key = "finish") { Section("Finish") { Bars(s.finishes, mode, keepOrder = true, onPick = ::show) } }
            item(key = "condition") { Section("Condition") { Bars(s.conditions, mode, keepOrder = true, onPick = ::show) } }
            item(key = "language") { Section("Language") { Bars(s.languages, mode, limit = 6, onPick = ::show) } }
            if (binder == null && s.binders.size > 1) {
                item(key = "binderValue") { Section("Binders") { Bars(s.binders, mode) { } } }
            }
            item(key = "valuable") { Section("Most valuable") { CardList(s.mostValuable, priceType, showValue = true) } }
            if (s.edhrecTop.isNotEmpty()) {
                item(key = "edhrec") { Section("Most played on EDHREC", "Your cards that are in the most Commander decks.") { CardList(s.edhrecTop, priceType) } }
            }
            item(key = "gc") {
                Section(
                    "Game Changers: ${s.gameChangers.size}",
                    if (s.gameChangers.isEmpty()) "None of the cards on Wizards' Game Changers list." else "Cards on Wizards' Game Changers list (they raise a deck's bracket).",
                ) { CardList(s.gameChangers, priceType, limit = 6) }
            }
        }
    }
}

@Composable
private fun Overview(s: CollectionStatsResult, priceType: PriceType) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Figure("Cards", "%,d".format(s.copies), Modifier.weight(1f))
                Figure("Unique", "%,d".format(s.uniqueCards), Modifier.weight(1f))
                Figure("Printings", "%,d".format(s.printings), Modifier.weight(1f))
            }
            Row {
                Figure("Value (${priceType.short})", Fmt.money(s.value), Modifier.weight(1f))
                Figure("Per card", Fmt.money(s.averageValue), Modifier.weight(1f))
                Figure("Foil", if (s.copies > 0) "%.0f%%".format(s.foilCopies * 100.0 / s.copies) else "–", Modifier.weight(1f))
            }
            val notes = listOfNotNull(
                s.unpriced.takeIf { it > 0 }?.let { "$it without a price" },
                s.withoutDetails.takeIf { it > 0 }?.let { "$it whose colors and types aren't loaded yet" },
            )
            if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

private fun amountText(e: StatEntry, mode: StatMode) = if (mode == StatMode.CARDS) "%,d".format(e.copies) else Fmt.money(e.value)

/** A donut chart with a legend; tapping a legend line shows those cards. */
@Composable
private fun PieChart(entries: List<StatEntry>, mode: StatMode, onPick: (CollectionFilter?) -> Unit) {
    val total = entries.sumOf { it.amount(mode) }
    if (total <= 0) {
        Text("Nothing to show yet.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(140.dp)) {
            val stroke = size.minDimension * 0.22f
            val inset = stroke / 2
            var start = -90f
            for (e in entries) {
                val sweep = (e.amount(mode) / total * 360).toFloat()
                if (sweep <= 0f) continue
                drawArc(
                    color = PieColors[e.key] ?: Color.Gray,
                    startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(e.filter) }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).background(PieColors[e.key] ?: Color.Gray, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(e.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        amountText(e, mode) + "  " + "%.0f%%".format(e.amount(mode) / total * 100),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Horizontal bars, longest first (or in the given order); tapping one shows those cards. */
@Composable
private fun Bars(entries: List<StatEntry>, mode: StatMode, keepOrder: Boolean = false, limit: Int = Int.MAX_VALUE, onPick: (CollectionFilter?) -> Unit) {
    val max = entries.maxOfOrNull { it.amount(mode) }?.takeIf { it > 0 } ?: 1.0
    val sorted = if (keepOrder) entries else entries.sortedByDescending { it.amount(mode) }
    var all by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (if (all) sorted else sorted.take(limit)).forEach { e ->
            Column(Modifier.fillMaxWidth().clickable(enabled = e.filter != null) { onPick(e.filter) }) {
                Row {
                    Text(e.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(amountText(e, mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                    Box(
                        Modifier.fillMaxWidth((e.amount(mode) / max).toFloat().coerceIn(0.01f, 1f)).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        ShowAllButton(sorted.size, limit, all) { all = !all }
    }
}

/** "Show all 32" / "Show fewer" under a list cut at [limit]; nothing when the list is short enough. */
@Composable
private fun ShowAllButton(size: Int, limit: Int, all: Boolean, onToggle: () -> Unit) {
    if (size <= limit) return
    androidx.compose.material3.TextButton(onClick = onToggle) { Text(if (all) "Show fewer" else "Show all $size") }
}

/** Vertical bars (mana curve, years), labelled underneath. */
@Composable
private fun Columns(entries: List<StatEntry>, mode: StatMode, labelEvery: Int = 1) {
    val max = entries.maxOfOrNull { it.amount(mode) }?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        entries.forEachIndexed { i, e ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (entries.size <= 12) {
                    Text(
                        if (mode == StatMode.CARDS) "${e.copies}" else "€%.0f".format(e.value),
                        style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight((e.amount(mode) / max).toFloat().coerceIn(0.01f, 0.8f))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
                if (labelEvery == 1) Text(e.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
    if (labelEvery > 1 && entries.isNotEmpty()) {
        Row(Modifier.fillMaxWidth()) {
            Text(entries.first().label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(entries[entries.size / 2].label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(entries.last().label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CardList(cards: List<StatCard>, priceType: PriceType, showValue: Boolean = false, limit: Int = Int.MAX_VALUE) {
    var all by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (if (all) cards else cards.take(limit)).forEach { sc ->
            val item = sc.row.item
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardThumb(item.card.imageUrl, width = 30, enlargeable = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.card.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SetLine(item.card)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(sc.note, style = MaterialTheme.typography.labelMedium)
                    if (showValue) Text(Fmt.money(sc.row.unitPrice(priceType)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        ShowAllButton(cards.size, limit, all) { all = !all }
    }
}
