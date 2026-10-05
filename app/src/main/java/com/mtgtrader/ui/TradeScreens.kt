package com.mtgtrader.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.Binder
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.Finish
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.Side
import com.mtgtrader.data.Trade
import com.mtgtrader.data.TradeItem
import com.mtgtrader.data.TradeWithItems
import com.mtgtrader.data.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Trade list ----------------------------------------------------------------------------

@Composable
fun TradesListScreen(nav: NavController) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val trades by remember { c.db.tradeDao().observeAll() }.collectAsStateWithLifecycle(null)
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val tolerance by c.settings.tolerancePct.collectAsStateWithLifecycle()
    var creatingTrade by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        c.repo.deleteEmptyDrafts()
        // A trade was just deleted on its own screen: offer to bring it back.
        val deleted = c.deletedTrade ?: return@LaunchedEffect
        c.deletedTrade = null
        if (snackbar.showUndo("Trade deleted")) c.repo.restoreTrade(deleted)
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val csv = c.repo.exportTradesCsv(priceType)
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } }
            Toast.makeText(context, "Trades exported", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Trades") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export trades (CSV)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = { menu = false; exporter.launch("mtg-trades.csv") },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // One trade per tap, even when the phone is slow to react right after starting.
                    if (!creatingTrade) {
                        creatingTrade = true
                        scope.launch {
                            try {
                                nav.navigate("trade/${c.repo.newTrade()}") { launchSingleTop = true }
                            } finally {
                                creatingTrade = false
                            }
                        }
                    }
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New trade") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val list = trades
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState(
                "No trades yet",
                "Tap “New trade”, then add the cards each side is giving — by search or with the camera scanner. " +
                    "The app compares their Cardmarket value so you can keep the trade fair.",
                Modifier.padding(pad),
            )
            else -> LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.trade.id }) { t ->
                    TradeCard(t, priceType, tolerance) { nav.navigate("trade/${t.trade.id}") }
                }
            }
        }
    }
}

@Composable
private fun TradeCard(t: TradeWithItems, priceType: PriceType, tolerance: Int, onClick: () -> Unit) {
    val b = t.balance(priceType, tolerance)
    val verdictColor = when (b.verdict) {
        Verdict.FAIR -> VerdictColors.fair
        Verdict.FAVORS_YOU -> VerdictColors.favorsYou
        Verdict.FAVORS_THEM -> VerdictColors.favorsThem
        Verdict.EMPTY -> MaterialTheme.colorScheme.outline
    }
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tradeTitle(t.trade),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (t.trade.partner.isNotBlank()) Text(Fmt.dateTime(t.trade.createdAt), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Give ${t.give.sumOf { it.quantity }} · ${Fmt.money(b.give)}   Get ${t.get.sumOf { it.quantity }} · ${Fmt.money(b.get)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (b.verdict != Verdict.EMPTY) {
                    Text(Fmt.signedMoney(b.diff), color = verdictColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.weight(1f))
                if (t.trade.applied) Tag("In collection") else Tag("Not applied", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Trade editor --------------------------------------------------------------------------

@Composable
fun TradeEditorScreen(nav: NavController, tradeId: Long) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val data by remember(tradeId) { c.db.tradeDao().observe(tradeId) }.collectAsStateWithLifecycle(null)
    val owned by remember { c.db.collectionDao().observeOwned() }.collectAsStateWithLifecycle(emptyList())
    val ownedMap = remember(owned) { owned.associate { it.scryfallId to it.qty } }
    val wishRows by remember { c.db.wishlistDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val tolerance by c.settings.tolerancePct.collectAsStateWithLifecycle()
    val columns by c.settings.tradeColumns.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var partner by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(data != null) {
        data?.let {
            if (partner == null) partner = it.trade.partner
            if (notes == null) notes = it.trade.notes
        }
    }
    var editing by remember { mutableStateOf<TradeItem?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }

    val t = data
    val applied = t?.trade?.applied == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (partner.isNullOrBlank()) "Trade" else "Trade with $partner", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        t?.let { Text(Fmt.dateTime(it.trade.createdAt), style = MaterialTheme.typography.bodySmall) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { c.settings.setTradeColumns(!columns) }) {
                        Icon(
                            if (columns) Icons.Default.ViewAgenda else Icons.Default.ViewColumn,
                            if (columns) "Show the sides one above the other" else "Show the sides next to each other",
                        )
                    }
                    IconButton(onClick = {
                        t?.let {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, tradeSummary(it, priceType, tolerance))
                            }
                            context.startActivity(Intent.createChooser(send, "Share trade"))
                        }
                    }) { Icon(Icons.Default.Share, "Share") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Refresh prices") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = !applied,
                            onClick = {
                                menu = false
                                scope.launch {
                                    c.repo.refreshTradePrices(tradeId)
                                    snackbar.showSnackbar("Prices updated from today's price guide")
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete trade") },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (t != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (applied) {
                            Icon(Icons.Default.CheckCircle, null, tint = VerdictColors.fair)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Applied to collection\n${t.trade.appliedAt?.let { Fmt.dateTime(it) } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = {
                                scope.launch {
                                    c.repo.revertTrade(tradeId)
                                    snackbar.showSnackbar("Collection changes undone — trade can be edited again")
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Undo")
                            }
                        } else {
                            Button(
                                onClick = { confirmApply = true },
                                enabled = t.items.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Complete trade & update collection") }
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (t == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val balance = t.balance(priceType, tolerance)
        val wanted = remember(wishRows) { wishRows.groupBy { it.item.card.name.lowercase() }.mapValues { (_, v) -> v.sumOf { it.item.quantity } } }
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { BalanceCard(balance, Modifier.fillMaxWidth()) }
            item { PriceTypeSelector(priceType, c.settings::setPriceType, Modifier.fillMaxWidth()) }
            item {
                OutlinedTextField(
                    value = partner ?: "",
                    onValueChange = { v -> partner = v; scope.launch { c.repo.setPartner(tradeId, v) } },
                    label = { Text("Trading with") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (columns) {
                item(key = "columns") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TradeColumn(
                            title = "You give", items = t.give, priceType = priceType, ownedMap = ownedMap, wanted = emptyMap(), locked = applied,
                            onSearch = { nav.openSearch(CardTarget.TradeSide(tradeId, Side.GIVE)) },
                            onScan = { nav.openScanner(CardTarget.TradeSide(tradeId, Side.GIVE)) },
                            onEdit = { editing = it },
                            modifier = Modifier.weight(1f),
                        )
                        TradeColumn(
                            title = "You get", items = t.get, priceType = priceType, ownedMap = null, wanted = wanted, locked = applied,
                            onSearch = { nav.openSearch(CardTarget.TradeSide(tradeId, Side.GET)) },
                            onScan = { nav.openScanner(CardTarget.TradeSide(tradeId, Side.GET)) },
                            onEdit = { editing = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                tradeSide(
                    title = "You get", side = Side.GET, items = t.get, priceType = priceType, ownedMap = null, locked = applied, wanted = wanted,
                    onSearch = { nav.openSearch(CardTarget.TradeSide(tradeId, Side.GET)) },
                    onScan = { nav.openScanner(CardTarget.TradeSide(tradeId, Side.GET)) },
                    onEdit = { editing = it },
                )
                tradeSide(
                    title = "You give", side = Side.GIVE, items = t.give, priceType = priceType, ownedMap = ownedMap, locked = applied,
                    onSearch = { nav.openSearch(CardTarget.TradeSide(tradeId, Side.GIVE)) },
                    onScan = { nav.openScanner(CardTarget.TradeSide(tradeId, Side.GIVE)) },
                    onEdit = { editing = it },
                )
            }
            item {
                OutlinedTextField(
                    value = notes ?: "",
                    onValueChange = { v -> notes = v; scope.launch { c.repo.setNotes(tradeId, v) } },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            item {
                Text(
                    "Started ${Fmt.dateTime(t.trade.createdAt)} · prices captured when each card was added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { item ->
        val entity by produceState<PriceEntity?>(null, item.card.cardmarketId) {
            value = item.card.productFor(item.foil)?.let { id -> c.prices.pricesFor(listOf(id))[id] }
        }
        EditCardDialog(
            card = item.card,
            initial = EditValues(item.quantity, item.finish, item.condition, item.language, item.customPrice),
            prices = { f -> if (f == item.foil) item.prices else entity?.toSet(f) },
            priceType = priceType,
            allowCustomPrice = true,
            enabled = !applied,
            onDismiss = { editing = null },
            onSave = { v ->
                editing = null
                scope.launch {
                    c.repo.updateTradeItem(
                        item.copy(
                            quantity = v.quantity, foil = v.finish.foil, etched = v.finish.etched, condition = v.condition,
                            language = v.language, customPrice = v.customPrice,
                        )
                    )
                }
            },
            onDelete = {
                editing = null
                scope.launch {
                    c.repo.deleteTradeItem(item.id)
                    if (snackbar.showUndo("${item.card.displayName} removed")) c.repo.restoreTradeItem(item)
                }
            },
            onChangePrinting = {
                editing = null
                nav.openSearch(CardTarget.ReplaceTradeItem(item.id), item.card.name)
            },
        )
    }

    if (confirmApply && t != null) {
        val getN = t.get.sumOf { it.quantity }
        val giveN = t.give.sumOf { it.quantity }
        var binder by remember { mutableStateOf(BinderChoice()) }
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            title = { Text("Update collection?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This adds the $getN card(s) you get to your collection and removes the $giveN card(s) you give " +
                            "(taken from ${Binder.UNSORTED_NAME} first).\n\nYou can undo this later from this screen."
                    )
                    if (getN > 0) {
                        BinderPicker(
                            label = "Put the cards you get in",
                            choice = binder,
                            onChange = { binder = it },
                            suggestedName = if (t.trade.partner.isBlank()) "Trade ${Fmt.date(t.trade.createdAt)}" else "Trade with ${t.trade.partner}",
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = binder.isValid, onClick = {
                    confirmApply = false
                    scope.launch {
                        val binderId = c.repo.resolve(binder)
                        val missing = c.repo.applyTrade(tradeId, binderId)
                        snackbar.showSnackbar(
                            if (missing == 0) "Collection updated"
                            else "Collection updated — $missing given card(s) weren't in your collection"
                        )
                    }
                }) { Text("Update collection") }
            },
            dismissButton = { TextButton(onClick = { confirmApply = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this trade?") },
            text = {
                Text(
                    if (applied) "The trade is removed from your history. Your collection is left as it is now (use Undo first if you want to reverse the collection changes)."
                    else "The trade and its card list will be removed."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        // The trade list shows the Undo message, since this screen closes.
                        c.deletedTrade = c.db.tradeDao().get(tradeId)
                        c.repo.deleteTrade(tradeId)
                        nav.popBackStack()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun LazyListScope.tradeSide(
    title: String,
    side: String,
    items: List<TradeItem>,
    priceType: PriceType,
    ownedMap: Map<String, Int>?,
    locked: Boolean,
    onSearch: () -> Unit,
    onScan: () -> Unit,
    onEdit: (TradeItem) -> Unit,
    wanted: Map<String, Int> = emptyMap(),
) {
    item(key = "header-$side") {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${items.sumOf { it.quantity }} card(s) · ${Fmt.money(items.sumOf { it.lineTotal(priceType) })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onSearch, enabled = !locked, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalButton(onClick = onScan, enabled = !locked, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Scan")
            }
        }
    }
    if (items.isEmpty()) {
        item(key = "empty-$side") {
            Text(
                "No cards yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    items(items, key = { it.id }) { item ->
        TradeItemRow(item, priceType, ownedMap?.let { it[item.card.scryfallId] ?: 0 }, wanted[item.card.name.lowercase()]) { onEdit(item) }
    }
}

@Composable
private fun TradeItemRow(item: TradeItem, priceType: PriceType, owned: Int?, wanted: Int?, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardThumb(item.card.imageUrl)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.card.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SetLine(item.card)
                    FinishTag(item.card, item.finish)
                    Tag(item.condition)
                    if (item.language != "EN") Tag(item.language)
                }
                if (wanted != null) {
                    Text("★ On your wishlist" + if (wanted > 1) " ($wanted wanted)" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (owned != null) {
                    Text(
                        if (owned > 0) "You own $owned" else "Not in your collection",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (owned >= item.quantity) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(item.lineTotal(priceType)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TrendBadge(item.prices.trendChange)
                if (item.quantity > 1) Text("${item.quantity} × ${Fmt.money(item.unitPrice(priceType))}", style = MaterialTheme.typography.bodySmall)
                if (item.customPrice != null) Text("agreed price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** One side of a trade as a narrow column, for the side-by-side layout. Since 1.21. */
@Composable
private fun TradeColumn(
    title: String,
    items: List<TradeItem>,
    priceType: PriceType,
    ownedMap: Map<String, Int>?,
    wanted: Map<String, Int>,
    locked: Boolean,
    onSearch: () -> Unit,
    onScan: () -> Unit,
    onEdit: (TradeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            "${items.sumOf { it.quantity }} card(s) · " + Fmt.money(items.sumOf { it.lineTotal(priceType) }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(onClick = onSearch, enabled = !locked, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("Add", maxLines = 1)
            }
            FilledTonalButton(onClick = onScan, enabled = !locked, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.CameraAlt, "Scan", Modifier.size(16.dp))
            }
        }
        if (items.isEmpty()) {
            Text("No cards yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        }
        items.forEach { item ->
            CompactTradeItem(item, priceType, ownedMap?.let { it[item.card.scryfallId] ?: 0 }, wanted[item.card.name.lowercase()]) { onEdit(item) }
        }
    }
}

@Composable
private fun CompactTradeItem(item: TradeItem, priceType: PriceType, owned: Int?, wanted: Int?, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(6.dp)) {
            CardThumb(item.card.imageUrl, width = 30)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (item.quantity > 1) "${item.quantity}× " else "") + item.card.displayName,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        item.card.setCode.uppercase(),
                        item.condition,
                        when (item.finish) { Finish.FOIL -> "Foil"; Finish.ETCHED -> "Etched"; Finish.NONFOIL -> null },
                        item.language.takeIf { it != "EN" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.money(item.lineTotal(priceType)), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    if (item.customPrice != null) Text(" agreed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (wanted != null) Text("★ wishlist", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (owned != null && owned < item.quantity) {
                    Text(if (owned > 0) "You own $owned" else "Not owned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Trades are named after their partner, or else when they were started. */
private fun tradeTitle(trade: Trade) = trade.partner.ifBlank { "Trade · ${Fmt.dateTime(trade.createdAt)}" }

private fun tradeSummary(t: TradeWithItems, type: PriceType, tolerance: Int): String {
    val b = t.balance(type, tolerance)
    fun lines(items: List<TradeItem>) = items.joinToString("\n") { i ->
        "  ${i.quantity}× ${i.card.displayName} (${i.card.setLabel})${if (i.foil) " " + i.card.finishName(i.finish).lowercase() else ""} ${i.condition} — ${Fmt.money(i.lineTotal(type))}"
    }
    return buildString {
        appendLine(if (t.trade.partner.isBlank()) "MTG trade — ${Fmt.date(t.trade.createdAt)}" else "MTG trade with ${t.trade.partner} — ${Fmt.date(t.trade.createdAt)}")
        appendLine()
        appendLine("I give (${Fmt.money(b.give)}):")
        appendLine(lines(t.give).ifEmpty { "  —" })
        appendLine()
        appendLine("I get (${Fmt.money(b.get)}):")
        appendLine(lines(t.get).ifEmpty { "  —" })
        appendLine()
        append("Difference: ${Fmt.signedMoney(b.diff)} (Cardmarket ${type.label.lowercase()})")
    }
}
