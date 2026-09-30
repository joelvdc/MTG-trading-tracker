package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.ScanRow
import com.mtgtrader.data.Side
import kotlinx.coroutines.launch

private enum class ScanAction { BINDER, TRADE, DECK }

/**
 * The Scan tab: cards scanned here wait in a list until the user sends them to a binder, a trade
 * or a deck, or discards them.
 */
@Composable
fun ScansScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val rows by remember { c.db.scanDao().observeAll() }.collectAsStateWithLifecycle(null)
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    var unselected by remember { mutableStateOf(setOf<Long>()) }
    var action by remember { mutableStateOf<ScanAction?>(null) }
    var editing by remember { mutableStateOf<ScanRow?>(null) }

    val all = rows
    val selectedIds = all?.map { it.item.id }?.filterNot { it in unselected }.orEmpty()
    val selectedRows = all?.filter { it.item.id in selectedIds }.orEmpty()
    val selectedCount = selectedRows.sumOf { it.item.quantity }

    fun report(message: String, undo: (suspend () -> Unit)?) = scope.launch {
        if (undo == null) {
            snackbar.showSnackbar(message)
        } else if (snackbar.showUndo(message)) {
            undo()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Scanned cards") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { nav.openSearch(CardTarget.Scans) }) { Icon(Icons.Default.Search, "Add by name") }
                ExtendedFloatingActionButton(
                    onClick = { nav.openScanner(CardTarget.Scans) },
                    icon = { Icon(Icons.Default.CameraAlt, null) },
                    text = { Text("Scan") },
                    expanded = all.isNullOrEmpty(),
                )
            }
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                        ActionButton("To binder", Icons.Default.CollectionsBookmark, Modifier.weight(1f)) { action = ScanAction.BINDER }
                        ActionButton("To trade", Icons.Default.SwapHoriz, Modifier.weight(1f)) { action = ScanAction.TRADE }
                        ActionButton("To deck", Icons.Default.Style, Modifier.weight(1f)) { action = ScanAction.DECK }
                        ActionButton("Discard", Icons.Default.Delete, Modifier.weight(1f)) {
                            val ids = selectedIds
                            val n = selectedCount
                            scope.launch {
                                val undo = c.repo.discardScans(ids)
                                report("Discarded $n card(s)", undo)
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (all == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (all.isEmpty()) {
            Column(Modifier.padding(pad)) {
                EmptyState(
                    "Nothing scanned yet",
                    "Scan a pile of cards here, then decide what to do with them: put them in a binder, add them to a trade " +
                        "or a deck, or discard them.",
                )
            }
            return@Scaffold
        }
        val total = all.sumOf { it.item.quantity }
        val value = all.sumOf { (it.unitPrice(priceType) ?: 0.0) * it.item.quantity }
        Column(Modifier.padding(pad)) {
            Text(
                "$total card(s) · ${Fmt.money(value)} (${priceType.short})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$selectedCount selected", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(onClick = { unselected = emptySet() }) { Text("Select all") }
                TextButton(onClick = { unselected = all.map { it.item.id }.toSet() }) { Text("Select none") }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(all, key = { it.item.id }) { row ->
                    val id = row.item.id
                    ScanRowView(
                        row, priceType, checked = id !in unselected,
                        onCheck = { on -> unselected = if (on) unselected - id else unselected + id },
                        onClick = { editing = row },
                    )
                }
            }
        }
    }

    editing?.let { row ->
        val item = row.item
        EditCardDialog(
            card = item.card,
            initial = EditValues(item.quantity, item.finish, item.condition, item.language, null),
            prices = { f -> row.price?.toSet(f) ?: PriceSet(trend = item.card.fallback(f)) },
            priceType = priceType,
            allowCustomPrice = false,
            enabled = true,
            onDismiss = { editing = null },
            onSave = { v ->
                editing = null
                scope.launch {
                    c.repo.updateScan(item.copy(quantity = v.quantity, foil = v.finish.foil, etched = v.finish.etched, condition = v.condition, language = v.language))
                }
            },
            onDelete = {
                editing = null
                scope.launch {
                    c.repo.deleteScan(item.id)
                    if (snackbar.showUndo("${item.card.displayName} removed")) c.repo.restoreScans(listOf(item))
                }
            },
            onChangePrinting = {
                editing = null
                nav.openSearch(CardTarget.ReplaceScan(item.id), item.card.name)
            },
        )
    }

    when (action) {
        ScanAction.BINDER -> ToBinderDialog(selectedCount, onDismiss = { action = null }) { choice, keep ->
            action = null
            val ids = selectedIds
            scope.launch {
                val binderId = c.repo.resolve(choice)
                val undo = c.repo.scansToCollection(ids, binderId, keep)
                val name = choice.newName ?: binderName(binderId, c.db.binderDao().all())
                report("Added $selectedCount card(s) to $name", undo)
            }
        }
        ScanAction.TRADE -> ToTradeDialog(selectedCount, onDismiss = { action = null }) { tradeId, side, keep ->
            action = null
            val ids = selectedIds
            scope.launch {
                val id = c.repo.scansToTrade(ids, tradeId, side, keep)
                nav.navigate("trade/$id")
            }
        }
        ScanAction.DECK -> ToDeckDialog(selectedCount, onDismiss = { action = null }) { deckId, deckName, keep ->
            action = null
            val ids = selectedIds
            scope.launch {
                val undo = c.decks.scansToDeck(ids, deckId, keep)
                report("Added $selectedCount card(s) to $deckName", undo)
            }
        }
        null -> {}
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ScanRowView(row: ScanRow, priceType: PriceType, checked: Boolean, onCheck: (Boolean) -> Unit, onClick: () -> Unit) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheck)
            CardThumb(item.card.imageUrl, width = 36)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (item.quantity > 1) "${item.quantity}× " else "") + item.card.displayName,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SetLine(item.card)
                    FinishTag(item.card, item.finish)
                    if (item.condition != "NM") Tag(item.condition)
                    if (item.language != "EN") Tag(item.language)
                }
                if (!item.exactPrinting) {
                    Text("Printing guessed — tap to check the set", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(unit?.let { it * item.quantity }), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TrendBadge(row.trend)
            }
        }
    }
}

@Composable
private fun ToBinderDialog(count: Int, onDismiss: () -> Unit, onConfirm: (BinderChoice, keep: Boolean) -> Unit) {
    var choice by remember { mutableStateOf(BinderChoice()) }
    var keep by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $count card(s) to the collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BinderPicker("Binder", choice, { choice = it }, suggestedName = "New cards ${Fmt.date(System.currentTimeMillis())}")
                CheckRow("Keep them in the scanned list", keep) { keep = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(choice, keep) }, enabled = choice.isValid) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ToTradeDialog(count: Int, onDismiss: () -> Unit, onConfirm: (tradeId: Long, side: String, keep: Boolean) -> Unit) {
    val c = LocalContext.current.container
    val open by remember { c.db.tradeDao().observeOpen() }.collectAsStateWithLifecycle(emptyList())
    var tradeId by remember { mutableStateOf(0L) }
    var side by remember { mutableStateOf(Side.GIVE) }
    var keep by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $count card(s) to a trade") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Side", style = MaterialTheme.typography.labelLarge)
                RadioRow("You give", side == Side.GIVE) { side = Side.GIVE }
                RadioRow("You get", side == Side.GET) { side = Side.GET }
                Text("Trade", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                RadioRow("New trade", tradeId == 0L) { tradeId = 0L }
                open.forEach { t ->
                    RadioRow(t.partner.ifBlank { "Trade · ${Fmt.dateTime(t.createdAt)}" }, tradeId == t.id) { tradeId = t.id }
                }
                CheckRow("Keep them in the scanned list", keep) { keep = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(tradeId, side, keep) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ToDeckDialog(count: Int, onDismiss: () -> Unit, onConfirm: (deckId: Long, name: String, keep: Boolean) -> Unit) {
    val c = LocalContext.current.container
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    var deckId by remember { mutableStateOf<Long?>(null) }
    var keep by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $count card(s) to a deck") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (decks.isEmpty()) {
                    Text("Import a deck in the Decks tab first.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "The cards are added to the deck in this app only (Archidekt isn't changed), listed under " +
                            "“Added in app”. They stay when the deck is refreshed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    decks.forEach { d -> RadioRow(d.name, deckId == d.archidektId) { deckId = d.archidektId } }
                    CheckRow("Keep them in the scanned list", keep) { keep = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { deckId?.let { id -> onConfirm(id, decks.first { it.archidektId == id }.name, keep) } }, enabled = deckId != null) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
