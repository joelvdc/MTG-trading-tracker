package com.mtgtrader.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SortBy(val label: String) { NAME("Name"), VALUE("Value"), RECENT("Recently added"), SET("Set") }

@Composable
fun CollectionScreen(nav: NavController) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val rows by remember { c.db.collectionDao().observeAll() }.collectAsStateWithLifecycle(null)
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortBy.NAME) }
    var sortMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CollectionRow?>(null) }
    var busy by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: ""
                val r = c.repo.importCollectionCsv(text)
                snackbar.showSnackbar(
                    "Imported ${r.imported} card(s)" + if (r.notFound > 0) " · ${r.notFound} couldn't be matched" else ""
                )
            } catch (e: Exception) {
                snackbar.showSnackbar("Import failed: ${e.message}")
            } finally {
                busy = false
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val csv = c.repo.exportCollectionCsv(priceType)
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } }
            Toast.makeText(context, "Collection exported", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Collection") },
                actions = {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortBy.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label, fontWeight = if (s == sort) FontWeight.Bold else null) },
                                onClick = { sort = s; sortMenu = false },
                            )
                        }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import CSV (ManaBox…)") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                            onClick = { menu = false; importer.launch(arrayOf("*/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = { menu = false; exporter.launch("mtg-collection.csv") },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { nav.openScanner(CardTarget.Collection) }) {
                    Icon(Icons.Default.CameraAlt, "Scan cards")
                }
                ExtendedFloatingActionButton(
                    onClick = { nav.openSearch(CardTarget.Collection) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add card") },
                )
            }
        },
    ) { pad ->
        val all = rows
        if (all == null || busy) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val shown = remember(all, filter, sort, priceType) {
            val f = filter.trim()
            all.filter { r ->
                f.isEmpty() || r.item.card.name.contains(f, true) || r.item.card.setCode.equals(f, true) ||
                    r.item.card.setName.contains(f, true)
            }.let { list ->
                when (sort) {
                    SortBy.NAME -> list
                    SortBy.VALUE -> list.sortedByDescending { (it.unitPrice(priceType) ?: 0.0) * it.item.quantity }
                    SortBy.RECENT -> list.sortedByDescending { it.item.addedAt }
                    SortBy.SET -> list.sortedWith(compareBy({ it.item.card.setName }, { it.item.card.collectorNumber.filter(Char::isDigit).toIntOrNull() ?: 0 }))
                }
            }
        }
        val totalCards = shown.sumOf { it.item.quantity }
        val totalValue = shown.sumOf { (it.unitPrice(priceType) ?: 0.0) * it.item.quantity }

        Column(Modifier.padding(pad)) {
            Text(
                "$totalCards card(s) · ${shown.size} unique · ${Fmt.money(totalValue)} (${priceType.short})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Filter by name or set") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Default.Clear, "Clear") } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            if (all.isEmpty()) {
                EmptyState(
                    "Your collection is empty",
                    "Add cards with search or the scanner, import a ManaBox CSV from the ⋮ menu, or complete a trade to fill it automatically.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(shown, key = { it.item.id }) { row -> CollectionRowView(row, priceType) { editing = row } }
                }
            }
        }
    }

    editing?.let { row ->
        val item = row.item
        EditCardDialog(
            card = item.card,
            initial = EditValues(item.quantity, item.foil, item.condition, item.language, null),
            prices = { f -> row.price?.toSet(f) ?: PriceSet(trend = item.card.fallback(f)) },
            priceType = priceType,
            allowCustomPrice = false,
            enabled = true,
            onDismiss = { editing = null },
            onSave = { v ->
                editing = null
                scope.launch {
                    c.repo.updateCollectionItem(item.copy(quantity = v.quantity, foil = v.foil, condition = v.condition, language = v.language))
                }
            },
            onDelete = { editing = null; scope.launch { c.repo.deleteCollectionItem(item.id) } },
            onChangePrinting = {
                editing = null
                nav.openSearch(CardTarget.ReplaceCollectionItem(item.id), item.card.name)
            },
        )
    }
}

@Composable
private fun CollectionRowView(row: CollectionRow, priceType: PriceType, onClick: () -> Unit) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardThumb(item.card.imageUrl, width = 40)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${item.quantity}× ${item.card.name}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SetLine(item.card)
                    if (item.foil) FoilTag()
                    Tag(item.condition)
                    if (item.language != "EN") Tag(item.language)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(unit?.let { it * item.quantity }), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (item.quantity > 1) Text("${Fmt.money(unit)} each", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
