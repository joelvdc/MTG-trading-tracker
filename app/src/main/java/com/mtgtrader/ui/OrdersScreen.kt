package com.mtgtrader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.OrderFile
import com.mtgtrader.data.OrderFiles
import com.mtgtrader.data.OrderWorth
import com.mtgtrader.data.OrderedCard
import com.mtgtrader.data.Orders
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.Pricing
import com.mtgtrader.data.PurchaseOrder
import com.mtgtrader.data.ScryfallApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/** Today as the app stores an order's day. */
internal fun todayIso(): String = LocalDate.now().toString()

internal fun isIsoDate(s: String): Boolean = runCatching { LocalDate.parse(s.trim()) }.isSuccess

/** "23 Jun 2026" from "2026-06-23"; the text itself when it isn't a date. */
internal fun orderDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)

/** The day an order was placed, typed as 2026-06-23: asked for when the order's file doesn't carry one. */
@Composable
internal fun OrderDateField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '-' }.take(10)) },
        label = { Text("Order date (year-month-day)") },
        isError = !isIsoDate(value),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The order history: every order the app has read (imports, package checks, added precons, past
 * order files), with what was paid and what its cards are worth now. Opened from the collection's
 * menu. Since 1.33.
 */
@Composable
fun OrdersScreen(nav: NavController) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val orders by remember { c.db.orderDao().observeAll() }.collectAsStateWithLifecycle(null)
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val lines = remember(orders) { orders.orEmpty().associate { it.id to Orders.lines(it) } }
    val prices by produceState(emptyMap<Int, PriceEntity>(), lines) {
        val ids = lines.values.flatten().mapNotNull { it.cardmarketId }.distinct()
        value = ids.chunked(900).flatMap { c.db.priceDao().getMany(it) }.associateBy { it.idProduct }
    }
    // Read here so the list redraws when the price source or the condition percentages change.
    val pricing = Triple(Pricing.source, Pricing.conditionPct, Pricing.others)
    val worth = remember(lines, prices, priceType, pricing) { lines.mapValues { (_, cards) -> Orders.worth(cards, prices, priceType) } }
    var opened by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = "Reading ${uris.size} file${if (uris.size == 1) "" else "s"}…"
        scope.launch {
            val files = ArrayList<OrderFile>()
            val own = ArrayList<PurchaseOrder>()
            val bad = ArrayList<String>()
            for (uri in uris) {
                val name = displayName(context, uri)
                try {
                    val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: throw IllegalStateException("couldn't be opened")
                    if (Orders.isExport(bytes)) own += Orders.import(bytes.toString(Charsets.UTF_8)) else files += withContext(Dispatchers.Default) { OrderFiles.read(bytes, name) }
                } catch (e: Exception) {
                    bad += name ?: "a file"
                }
            }
            val message = try {
                busy = "Finding the cards…"
                val read = Orders.fromFiles(files, c.db.collectionDao().all(), todayIso()) { keys ->
                    c.scryfall.collection(keys.map { (set, number) -> ScryfallApi.setNumberIdentifier(set, number) })
                        .associate { (it.set.lowercase() to it.collectorNumber.lowercase()) to it.toRef() }
                        .let { found -> keys.mapNotNull { k -> found[k.first.lowercase() to k.second.lowercase()]?.let { k to it } }.toMap() }
                } + own
                val added = c.repo.recordOrders(read)
                listOfNotNull(
                    "$added order${if (added == 1) "" else "s"} added",
                    (read.size - added).takeIf { it > 0 }?.let { "$it already there" },
                    bad.takeIf { it.isNotEmpty() }?.let { "couldn't read ${it.joinToString(", ")}" },
                ).joinToString(" · ")
            } catch (e: Exception) {
                "Couldn't add the orders: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = null
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orders") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = busy == null) { Icon(Icons.Default.Add, "Add order files") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val list = orders
        Column(Modifier.padding(pad)) {
            busy?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            when {
                list == null -> {}
                list.isEmpty() -> EmptyState(
                    "No orders yet",
                    "Orders you import, packages you check and precons you add are remembered here, with what you paid and what the cards " +
                        "are worth now. Tap + to add the files of orders from before (CardTrader's Excel, Cardmarket's CSV): they only go " +
                        "into this list, your collection isn't changed.",
                )
                else -> OrderList(list, worth, onOpen = { opened = it.id })
            }
        }
    }

    orders?.firstOrNull { it.id == opened }?.let { order ->
        OrderDialog(
            order, lines[order.id].orEmpty(), prices, priceType,
            onDismiss = { opened = null },
            onDelete = {
                opened = null
                scope.launch {
                    c.repo.deleteOrder(order.id)
                    if (snackbar.showUndo("“${order.title}” removed from the orders")) c.repo.recordOrders(listOf(order))
                }
            },
            onDate = { d -> scope.launch { c.repo.setOrderDate(order.id, d) } },
        )
    }
}

internal fun gainText(paid: Double, now: Double): String {
    val diff = now - paid
    val pct = if (paid > 0) " (%+.0f%%)".format(diff / paid * 100) else ""
    return (if (diff >= 0) "+" else "−") + Fmt.money(abs(diff)) + pct
}

/** The totals, then one row per order, newest first. Split out for the screenshot tests. */
@Composable
fun OrderList(orders: List<PurchaseOrder>, worth: Map<Long, OrderWorth>, onOpen: (PurchaseOrder) -> Unit, modifier: Modifier = Modifier) {
    val paid = orders.sumOf { it.paid }
    val now = orders.sumOf { worth[it.id]?.value ?: 0.0 }
    val paidPriced = orders.sumOf { worth[it.id]?.paid ?: 0.0 }
    val unpriced = orders.sumOf { worth[it.id]?.unpriced ?: 0 }
    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "summary") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${orders.size} order${if (orders.size == 1) "" else "s"} · ${orders.sumOf { it.cards }} cards", style = MaterialTheme.typography.titleSmall)
                    Row {
                        Text("Paid", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(Fmt.money(paid), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Row {
                        Text("Worth now", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(Fmt.money(now), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(gainText(paidPriced, now), color = if (now >= paidPriced) TrendColors.up else TrendColors.down, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "What the cards of each order are worth today, whether you still have them or not." +
                            if (unpriced > 0) " $unpriced card${if (unpriced == 1) " has" else "s have"} no price today: left out of “worth now” and of the gain or loss." else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(orders, key = { it.id }) { o ->
            val w = worth[o.id]
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(o) },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(o.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${orderDate(o.date)} · ${o.cards} card${if (o.cards == 1) "" else "s"}" + if (o.shop == Orders.PRECON) " · precon" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Fmt.money(o.paid), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (w != null && w.priced > 0) {
                            Text("now ${Fmt.money(w.value)}", style = MaterialTheme.typography.bodySmall)
                            Text(gainText(w.paid, w.value), style = MaterialTheme.typography.labelSmall, color = if (w.value >= w.paid) TrendColors.up else TrendColors.down)
                        }
                    }
                }
            }
        }
    }
}

/** An order's cards, most valuable first, with what each cost and is worth; the date can be changed and the order removed. */
@Composable
private fun OrderDialog(
    order: PurchaseOrder, cards: List<OrderedCard>, prices: Map<Int, PriceEntity>, type: PriceType,
    onDismiss: () -> Unit, onDelete: () -> Unit, onDate: (String) -> Unit,
) {
    var date by remember(order.id) { mutableStateOf(order.date) }
    var deleting by remember { mutableStateOf(false) }
    val rows = remember(cards, prices, type) { cards.map { it to Orders.unit(it, prices, type) }.sortedByDescending { (c, now) -> (now ?: c.price ?: 0.0) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(order.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderDateField(date, { date = it })
                Text("${order.cards} cards · paid ${Fmt.money(order.paid)}", style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows.size) { i ->
                        val (card, now) = rows[i]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((if (card.quantity > 1) "${card.quantity}× " else "") + card.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        listOf(card.setCode.uppercase(), card.number.takeIf { it.isNotEmpty() }?.let { "#$it" }.orEmpty()).filter { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() },
                                        "foil".takeIf { card.foil }, card.condition?.takeIf { it != "NM" }, card.language?.takeIf { it != "EN" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.money(card.price), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (now == null) "no price today" else "now ${Fmt.money(now)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (now == null || card.price == null) MaterialTheme.colorScheme.onSurfaceVariant else if (now >= card.price) TrendColors.up else TrendColors.down,
                                )
                            }
                        }
                    }
                }
                if (deleting) Text("Remove this order from the list? Your collection and its purchase prices stay as they are.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (date != order.date && isIsoDate(date)) onDate(date.trim()); onDismiss() }, enabled = isIsoDate(date)) {
                Text(if (date != order.date) "Save" else "Close")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (deleting) onDelete() else deleting = true }) {
                Text(if (deleting) "Yes, remove" else "Remove", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
