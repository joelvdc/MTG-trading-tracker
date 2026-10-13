package com.mtgtrader.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.OrderFile
import com.mtgtrader.data.OrderFiles
import com.mtgtrader.data.OrderLine
import com.mtgtrader.data.PurchasePrices
import com.mtgtrader.data.PurchasePrices.Change
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The order files picked in the collection's menu: several, so they don't fit in a route. */
internal object PickedOrderFiles {
    var uris: List<Uri> = emptyList()
}

/**
 * Sets what was paid for cards already in the collection, from one or several order files
 * (CardTrader's Excel, Cardmarket's shipment or monthly CSV). Nothing changes until the button is
 * pressed: the list shows every card that would get a price, lines that need a choice, and lines for
 * cards no longer owned. Since 1.32.
 */
@Composable
fun PurchasePricesScreen(nav: NavController) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val uris = remember { PickedOrderFiles.uris }
    var files by remember { mutableStateOf<List<OrderFile>?>(null) }
    var failed by remember { mutableStateOf(emptyList<String>()) }
    var stacks by remember { mutableStateOf<List<CollectionItem>?>(null) }
    var overwrite by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf(mapOf<Int, Set<Long>>()) }
    var skipped by remember { mutableStateOf(setOf<Long>()) }
    var showKept by remember { mutableStateOf(false) }
    var showNotOwned by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val binders = rememberBinders()

    LaunchedEffect(uris) {
        val read = ArrayList<OrderFile>()
        val bad = ArrayList<String>()
        for (uri in uris) {
            val name = displayName(context, uri)
            try {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: throw IllegalStateException("couldn't be opened")
                read += withContext(Dispatchers.Default) { OrderFiles.read(bytes, name) }
            } catch (e: Exception) {
                bad += "${name ?: "A file"}: " + if (e is OrderFiles.NotAnOrder) "not an order file the app knows" else (e.message ?: e.javaClass.simpleName)
            }
        }
        // Cardmarket's shipment file has product numbers only: the app's Cardmarket list gives them names for the screen.
        val nameless = read.flatMap { it.lines }.filter { it.name.isEmpty() }.mapNotNull { it.productId }.distinct()
        val names = nameless.chunked(500).flatMap { c.db.catalogDao().getMany(it) }.associate { it.idProduct to it.name }
        stacks = c.db.collectionDao().all()
        failed = bad
        files = read.map { f -> f.copy(lines = f.lines.map { l -> if (l.name.isEmpty()) l.copy(name = names[l.productId].orEmpty()) else l }) }
    }

    val lines = remember(files) { files.orEmpty().flatMap { it.lines } }
    val owned = stacks
    val plan = remember(lines, owned, overwrite, choices) { if (files == null || owned == null) null else PurchasePrices.plan(lines, owned, overwrite, choices) }
    val toSet = plan?.changing.orEmpty().filter { it.item.id !in skipped }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Purchase prices from orders") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (plan != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                val n = c.repo.setPurchasePrices(toSet.associate { it.item.id to it.newPrice })
                                c.repo.reportImport("Purchase price set on $n card${if (n == 1) "" else "s"}")
                                nav.safePopBackStack()
                            }
                        },
                        enabled = !saving && toSet.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        val n = toSet.size
                        Text(if (saving) "Saving…" else if (n == 0) "Nothing to change" else "Set the price on $n card${if (n == 1) "" else "s"}")
                    }
                }
            }
        },
    ) { pad ->
        when {
            plan == null -> Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.padding(8.dp))
                Text("Reading the order files…")
            }
            lines.isEmpty() -> EmptyState(
                "No order lines found",
                failed.joinToString("\n").ifEmpty { "The files have no card lines in them." },
                Modifier.padding(pad),
            )
            else -> PurchasePricePlan(
                plan = plan,
                files = files.orEmpty(),
                failed = failed,
                binders = binders,
                overwrite = overwrite,
                onOverwrite = { overwrite = it },
                choices = choices,
                onChoice = { line, stack, on -> choices = choices + (line to (if (on) choices[line].orEmpty() + stack else choices[line].orEmpty() - stack)) },
                skipped = skipped,
                onSkip = { id, skip -> skipped = if (skip) skipped + id else skipped - id },
                showKept = showKept,
                onShowKept = { showKept = it },
                showNotOwned = showNotOwned,
                onShowNotOwned = { showNotOwned = it },
                modifier = Modifier.padding(pad),
            )
        }
    }
}

/** The preview. Split out for the screenshot tests. */
@Composable
fun PurchasePricePlan(
    plan: PurchasePrices.Plan,
    files: List<OrderFile>,
    failed: List<String>,
    binders: List<Binder>,
    overwrite: Boolean,
    onOverwrite: (Boolean) -> Unit,
    choices: Map<Int, Set<Long>>,
    onChoice: (line: Int, stack: Long, on: Boolean) -> Unit,
    skipped: Set<Long>,
    onSkip: (Long, Boolean) -> Unit,
    showKept: Boolean,
    onShowKept: (Boolean) -> Unit,
    showNotOwned: Boolean,
    onShowNotOwned: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val changing = plan.changing
    val kept = plan.rows.filter { !it.changes }
    val bought = files.sumOf { f -> f.lines.sumOf { it.quantity } }
    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${files.size} file${if (files.size == 1) "" else "s"} with $bought card${if (bought == 1) "" else "s"} bought (" +
                        files.groupBy { it.format.label }.map { (label, fs) -> "${fs.size}× $label" }.joinToString(", ") + ").",
                    style = MaterialTheme.typography.bodyMedium,
                )
                for (f in failed) Text(f, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text(
                    "A card gets the average paid for it, weighted by copies; every copy in the stack gets that price. " +
                        "Nothing changes until you press the button, and a backup is made first.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (changing.any { it.change == Change.AVERAGED }) {
                    Text(
                        "Cards that already have a price and more copies than were bought get the average of both. " +
                            "Reading the same order twice would average it in twice: untick those cards if you did.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth().clickable { onOverwrite(!overwrite) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Overwrite the prices cards already have", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = overwrite, onCheckedChange = onOverwrite)
                }
            }
        }
        if (plan.open.isNotEmpty()) {
            item(key = "openHeader") {
                Column {
                    Text("Your choice (${plan.open.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "The file doesn't pin these to one of your cards. Tick the ones each purchase was for, or leave them.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(plan.open, key = { "o${it.index}" }) { o -> OpenRow(o, binders, choices[o.index].orEmpty()) { stack, on -> onChoice(o.index, stack, on) } }
        }
        item(key = "changeHeader") { Text("Will get a price (${changing.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
        items(changing, key = { "c${it.item.id}" }) { r -> PriceRow(r, binders, checked = r.item.id !in skipped) { on -> onSkip(r.item.id, !on) } }
        if (kept.isNotEmpty()) {
            item(key = "keptHeader") { Toggle("Keep the price they have (${kept.size})", showKept, onShowKept) }
            if (showKept) items(kept, key = { "k${it.item.id}" }) { r -> PriceRow(r, binders, checked = null) {} }
        }
        if (plan.notOwned.isNotEmpty()) {
            item(key = "goneHeader") { Toggle("Not in the collection (${plan.notOwned.sumOf { it.quantity }})", showNotOwned, onShowNotOwned) }
            if (showNotOwned) items(plan.notOwned.size, key = { "g$it" }) { i ->
                val l = plan.notOwned[i]
                Text("${l.quantity}× ${l.label} · ${purchaseText(l)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Toggle(text: String, shown: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!shown) }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(if (shown) "Hide" else "Show", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

/** "€2.42 · Cardmarket 15 Feb 2024" with whatever the file knows. */
private fun purchaseText(l: OrderLine): String = listOfNotNull(
    Fmt.money(l.price),
    if (l.productId != null) "Cardmarket" else "CardTrader",
    l.date,
).joinToString(" · ")

@Composable
private fun StackLine(item: CollectionItem, binders: List<Binder>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        SetLine(item.card)
        FinishTag(item.card, item.finish)
        if (item.language != "EN") Tag(item.language)
        if (item.condition != "NM") Tag(item.condition)
        MarksTags(item.signed, item.altered)
    }
    Text(
        "${item.quantity} in ${binderName(item.binderId, binders)}",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Why the price is what it is: "Bought 3 for €2.10 on average · you own 4, the 4th keeps its €5.00". */
internal fun priceRowNote(r: PurchasePrices.Row): String {
    val item = r.item
    val had = item.purchasePrice
    val bought = "Bought ${r.bought}" + if (r.purchases.size > 1 && r.purchases.map { it.price }.distinct().size > 1) " for ${Fmt.money(r.paid)} on average" else " for ${Fmt.money(r.paid)}"
    return when (r.change) {
        Change.NEW -> bought + if (r.bought < item.quantity) " · you own ${item.quantity}, all get this price" else ""
        Change.AVERAGED -> "$bought · the other ${item.quantity - r.bought} keep${if (item.quantity - r.bought == 1) "s" else ""} ${Fmt.money(had)}, averaged"
        Change.REPLACED -> "$bought · replaces ${Fmt.money(had)}"
        Change.KEPT -> "$bought · has ${Fmt.money(had)} already"
    }
}

@Composable
private fun PriceRow(r: PurchasePrices.Row, binders: List<Binder>, checked: Boolean?, onCheck: (Boolean) -> Unit) {
    val item = r.item
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(enabled = checked != null) { onCheck(checked != true) },
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 4.dp, start = if (checked == null) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            if (checked != null) Checkbox(checked = checked, onCheckedChange = onCheck)
            CardThumb(item.card.imageUrl, width = 36)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.card.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StackLine(item, binders)
                Text(priceRowNote(r), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(r.newPrice), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (r.changes && item.purchasePrice != null) Text("was ${Fmt.money(item.purchasePrice)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OpenRow(o: PurchasePrices.Open, binders: List<Binder>, chosen: Set<Long>, onChoose: (Long, Boolean) -> Unit) {
    val l = o.line
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
            Text("${l.quantity}× ${l.label}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(orderLineWhere(l).takeIf { it.isNotEmpty() }, purchaseText(l)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(o.why, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            for (s in o.candidates) {
                Row(Modifier.fillMaxWidth().clickable { onChoose(s.id, s.id !in chosen) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s.id in chosen, onCheckedChange = { onChoose(s.id, it) })
                    Column(Modifier.weight(1f)) { StackLine(s, binders) }
                    s.purchasePrice?.let { Text("has ${Fmt.money(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
