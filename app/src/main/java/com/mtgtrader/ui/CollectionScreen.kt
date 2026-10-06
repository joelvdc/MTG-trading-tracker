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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.CollectionView
import com.mtgtrader.data.CsvImportProgress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.WishlistRow
import android.content.Intent
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import kotlinx.coroutines.CoroutineScope
import com.mtgtrader.data.CollectionFilter
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.height

/** A binder-bar selection: null is "All cards", [Binder.UNSORTED] is cards outside binders, [WISHLIST] the wishlist. */
private typealias BinderSel = Long?

/** The wishlist's place in the binder bar (binder ids are positive). */
private const val WISHLIST = -2L

@Composable
fun CollectionScreen(nav: NavController) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val rows by remember { c.db.collectionDao().observeAll() }.collectAsStateWithLifecycle(null)
    val binders = rememberBinders()
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    val sortSpec by c.settings.collectionSort.collectAsStateWithLifecycle()
    var sorting by remember { mutableStateOf(false) }
    var filterText by rememberSaveable { mutableStateOf("") }
    val cardFilter = remember(filterText) { CollectionFilter.decode(filterText) }
    var filtering by remember { mutableStateOf(false) }
    val deckNameList by remember { c.db.deckDao().observeCardNames() }.collectAsStateWithLifecycle(emptyList())
    val deckNames = remember(deckNameList) { deckNameList.map { it.substringBefore(" // ").lowercase() }.toSet() }
    val details by c.cardDetails.progress.collectAsStateWithLifecycle()
    var tradeBinderId by remember { mutableStateOf(c.settings.tradeBinderId) }
    LaunchedEffect(binders) { tradeBinderId = c.tradeBinder.binder()?.id ?: 0L }
    val wishRows by remember { c.db.wishlistDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    var editingWish by remember { mutableStateOf<WishlistRow?>(null) }
    var selected by rememberSaveable { mutableStateOf<BinderSel>(null) }
    // Opened from the stats screen with a filter.
    val jump by c.collectionJump.collectAsStateWithLifecycle()
    LaunchedEffect(jump) {
        val j = jump ?: return@LaunchedEffect
        c.collectionJump.value = null
        filterText = j.filter.encode()
        filter = ""
        selected = j.binderId
    }
    var viewMenu by remember { mutableStateOf(false) }
    val view by c.settings.collectionView.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CollectionRow?>(null) }
    var naming by remember { mutableStateOf<Binder?>(null) }
    var creating by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // A deleted binder (or one merged away) drops back to "All cards".
    LaunchedEffect(binders, selected) {
        val s = selected
        if (s != null && s != Binder.UNSORTED && s != WISHLIST && binders.isNotEmpty() && binders.none { it.id == s }) selected = null
    }
    val currentBinder = binders.firstOrNull { it.id == selected }
    val wish = selected == WISHLIST
    val addTarget = if (wish) CardTarget.Wishlist else CardTarget.Collection(selected ?: Binder.UNSORTED)
    val wishOwned = remember(wishRows, rows) { c.repo.wishlistOwned(wishRows.map { it.item }, rows.orEmpty().map { it.item }) }

    val csvImport by c.repo.csvImport.collectAsStateWithLifecycle()
    val csvImportResult by c.repo.csvImportResult.collectAsStateWithLifecycle()
    LaunchedEffect(csvImportResult) {
        val msg = csvImportResult ?: return@LaunchedEffect
        c.repo.consumeCsvImportResult()
        snackbar.showSnackbar(msg)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val appContext = context.applicationContext
            val started = c.repo.startCsvImport(
                readText = {
                    withContext(Dispatchers.IO) {
                        appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    } ?: ""
                },
                defaultBinder = selected ?: Binder.UNSORTED,
            )
            if (!started) scope.launch { snackbar.showSnackbar("An import is already running") }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val csv = c.repo.exportCollectionCsv(priceType, selected)
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
                    IconButton(onClick = { viewMenu = true }) { Icon(viewIcon(view), "View") }
                    DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                        CollectionView.entries.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.label, fontWeight = if (v == view) FontWeight.Bold else null) },
                                leadingIcon = { Icon(viewIcon(v), null) },
                                onClick = { viewMenu = false; c.settings.setCollectionView(v) },
                            )
                        }
                    }
                    IconButton(onClick = { filtering = true }) {
                        BadgedBox(badge = { if (cardFilter.count > 0) Badge { Text("${cardFilter.count}") } }) { Icon(Icons.Default.FilterList, "Filter") }
                    }
                    IconButton(onClick = { sorting = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (wish) {
                            DropdownMenuItem(
                                text = { Text("Remove the cards I got since adding them") },
                                leadingIcon = { Icon(Icons.Default.TaskAlt, null) },
                                enabled = wishOwned.values.any { it.gotSince > 0 },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        val (n, undo) = c.repo.removeOwnedFromWishlist()
                                        if (snackbar.showUndo("Took $n card(s) off the wishlist")) undo()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share the wishlist") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                enabled = wishRows.isNotEmpty(),
                                onClick = {
                                    menu = false
                                    val text = wishRows.joinToString("\n") { "${it.item.quantity} ${it.item.card.name}" }
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_SUBJECT, "MTG wishlist").putExtra(Intent.EXTRA_TEXT, text)
                                    context.startActivity(Intent.createChooser(send, "Share the wishlist"))
                                },
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("New binder") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            onClick = { menu = false; creating = true },
                        )
                        if (currentBinder != null) {
                            DropdownMenuItem(
                                text = { Text("Rename “${currentBinder.name}”") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menu = false; naming = currentBinder },
                            )
                            DropdownMenuItem(
                                text = { Text("Merge “${currentBinder.name}” into…") },
                                leadingIcon = { Icon(Icons.Default.CallMerge, null) },
                                onClick = { menu = false; merging = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete “${currentBinder.name}”") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; deleting = true },
                            )
                        }
                        if (selected == Binder.UNSORTED) {
                            DropdownMenuItem(
                                text = { Text("Move all Unsorted cards to…") },
                                leadingIcon = { Icon(Icons.Default.CallMerge, null) },
                                onClick = { menu = false; merging = true },
                            )
                        }
                        if (!wish) HorizontalDivider()
                        if (!wish) DropdownMenuItem(
                            text = { Text(if (tradeBinderId > 0) "Update trade binder…" else "Make a trade binder…") },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                            onClick = { menu = false; nav.navigate("tradebinder") },
                        )
                        if (!wish) DropdownMenuItem(
                            text = { Text("Collection value over time") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ShowChart, null) },
                            onClick = { menu = false; nav.navigate("value") },
                        )
                        if (!wish) DropdownMenuItem(
                            text = { Text("Import CSV (ManaBox…)") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                            onClick = { menu = false; importer.launch(arrayOf("*/*")) },
                        )
                        if (!wish) DropdownMenuItem(
                            text = { Text(if (selected == null) "Export CSV" else "Export this binder as CSV") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = {
                                menu = false
                                val name = if (selected == null) "mtg-collection" else "mtg-" + binderName(selected ?: 0, binders).replace(Regex("[^A-Za-z0-9]+"), "-").lowercase()
                                exporter.launch("$name.csv")
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { nav.openScanner(addTarget) }) {
                    Icon(Icons.Default.CameraAlt, "Scan cards")
                }
                ExtendedFloatingActionButton(
                    onClick = { nav.openSearch(addTarget) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add card") },
                )
            }
        },
    ) { pad ->
        val all = rows
        if (all == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val counts = remember(all) { all.groupBy { it.item.binderId }.mapValues { (_, r) -> r.sumOf { it.item.quantity } } }
        val wishById = remember(wishRows) { wishRows.associateBy { it.item.id } }
        val inView = remember(all, wishRows, filter, selected) {
            val f = filter.trim()
            val source = if (wish) wishRows.map { it.asCollectionRow() } else all
            source.filter { r ->
                (wish || selected == null || r.item.binderId == selected) && (
                    f.isEmpty() || r.item.card.name.contains(f, true) || r.item.card.flavorName?.contains(f, true) == true ||
                        r.item.card.setCode.equals(f, true) || r.item.card.setName.contains(f, true) ||
                        (r.item.foil && r.item.card.finishName(r.item.finish).contains(f, true))
                    )
            }
        }
        val shown = remember(inView, cardFilter, sortSpec, priceType, deckNames) {
            val filtered = if (cardFilter.isEmpty) inView else inView.filter { cardFilter.matches(it, priceType, deckNames) }
            filtered.sortedWith(sortSpec.comparator(priceType))
        }
        val ownedSets = remember(all) {
            all.groupBy { it.item.card.setCode.lowercase() }.map { (code, r) -> OwnedSet(code, r.first().item.card.setName, r.sumOf { it.item.quantity }) }
                .sortedBy { it.name.lowercase() }
        }
        val languages = remember(all) { all.map { it.item.language }.distinct().sorted() }
        if (filtering) {
            FilterDialog(
                cardFilter, ownedSets, languages,
                count = { f -> inView.filter { f.matches(it, priceType, deckNames) }.sumOf { it.item.quantity } },
                onDismiss = { filtering = false },
            ) { filtering = false; filterText = if (it.isEmpty) "" else it.encode() }
        }
        val totalCards = shown.sumOf { it.item.quantity }
        val totalValue = shown.sumOf { (it.unitPrice(priceType) ?: 0.0) * it.item.quantity }

        Column(Modifier.padding(pad)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "all") {
                    FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("All · ${counts.values.sum()}") })
                }
                item(key = "unsorted") {
                    FilterChip(
                        selected = selected == Binder.UNSORTED,
                        onClick = { selected = Binder.UNSORTED },
                        label = { Text("${Binder.UNSORTED_NAME} · ${counts[Binder.UNSORTED] ?: 0}") },
                    )
                }
                item(key = "wishlist") {
                    FilterChip(
                        selected = wish,
                        onClick = { selected = WISHLIST },
                        label = { Text("Wishlist · ${wishRows.sumOf { it.item.quantity }}") },
                        leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) },
                    )
                }
                if (tradeBinderId > 0) binders.firstOrNull { it.id == tradeBinderId }?.let { b ->
                    item(key = "trade") {
                        FilterChip(
                            selected = selected == b.id,
                            onClick = { selected = b.id },
                            label = { Text("${b.name} · ${counts[b.id] ?: 0}") },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                items(binders.filter { it.id != tradeBinderId }, key = { it.id }) { b ->
                    FilterChip(selected = selected == b.id, onClick = { selected = b.id }, label = { Text("${b.name} · ${counts[b.id] ?: 0}") })
                }
                item(key = "new") {
                    AssistChip(
                        onClick = { creating = true },
                        label = { Text("New binder") },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }
            csvImport?.let { p -> CsvImportCard(p) }
            details?.let { (d, t) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    Text(
                        "Getting card details for sorting and filters: ${"%,d".format(d)} of ${"%,d".format(t)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(progress = { if (t == 0) 0f else d.toFloat() / t }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                }
            }
            if (wish) {
                Text(
                    "$totalCards card(s) wanted · ${Fmt.money(totalValue)} to buy them (${priceType.short})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("value") }.padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // One line whatever the numbers: the longest wording that fits.
                    val cards = "%,d".format(totalCards)
                    val unique = "%,d".format(shown.size)
                    FittingText(
                        listOf(
                            "$cards card(s) · $unique unique · ${Fmt.money(totalValue)} (${priceType.short})",
                            "$cards cards · $unique unique · ${Fmt.money(totalValue)}",
                            "$cards cards · $unique unique · ${Fmt.wholeMoney(totalValue)}",
                            "$cards cards · ${Fmt.wholeMoney(totalValue)}",
                            "$cards cards · ${Fmt.shortMoney(totalValue)}",
                            "$cards · ${Fmt.shortMoney(totalValue)}",
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected != null && selected == tradeBinderId) {
                        Spacer(Modifier.width(6.dp))
                        SmallAction("Update", Icons.Default.SwapHoriz) { nav.navigate("tradebinder") }
                    }
                    IconButton(onClick = { nav.navigate("value") }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, "Value over time", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { nav.navigate("stats") }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PieChart, "Collection stats", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            SearchField(
                filter, { filter = it }, "Filter by name, set or foil type",
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            ActiveFilterChips(cardFilter, ownedSets, onChange = { filterText = if (it.isEmpty) "" else it.encode() }, onEdit = { filtering = true })
            when {
                wish && wishRows.isEmpty() -> EmptyState(
                    "Your wishlist is empty",
                    "Add cards you want with “Add card” or the scanner, or from a deck's “Cards I'm missing”. " +
                        "Cards on your wishlist get a ★ when someone offers them in a trade.",
                )
                all.isEmpty() && !wish -> EmptyState(
                    "Your collection is empty",
                    "Add cards with search or the scanner, import a ManaBox CSV from the ⋮ menu, or complete a trade to fill it automatically.",
                )
                shown.isEmpty() && !cardFilter.isEmpty -> EmptyState(
                    "No cards match the filter",
                    "Remove a filter chip above, or tap one to change the filter.",
                )
                shown.isEmpty() && filter.isBlank() -> EmptyState(
                    if (selected == Binder.UNSORTED) "No unsorted cards" else "This binder is empty",
                    "Add cards here with search or the scanner, send scanned cards here from the Scan tab, or move cards in from another binder.",
                )
                else -> {
                    fun binderOf(row: CollectionRow) = when {
                        wish -> wishById[row.item.id]?.let { wishLabel(it.item, wishOwned[row.item.id]) }
                        selected == null && row.item.binderId != Binder.UNSORTED -> binderName(row.item.binderId, binders)
                        else -> null
                    }
                    fun open(row: CollectionRow) {
                        if (wish) editingWish = wishById[row.item.id] else editing = row
                    }
                    when (view) {
                        CollectionView.LIST -> LazyColumn(
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(shown, key = { it.item.id }) { row -> CollectionRowView(row, priceType, binderOf(row)) { open(row) } }
                        }
                        CollectionView.COMPACT -> LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 150.dp)) {
                            items(shown, key = { it.item.id }) { row ->
                                CompactRow(row, priceType, binderOf(row)) { open(row) }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                        CollectionView.GRID -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(112.dp),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 150.dp),
                        ) {
                            gridItems(shown, key = { it.item.id }) { row -> CardTile(row, priceType, binderOf(row)) { open(row) } }
                        }
                    }
                }
            }
        }
    }

    editing?.let { row -> CollectionCardDialog(row, nav, snackbar, scope) { editing = null } }
    if (sorting) SortDialog(sortSpec, onDismiss = { sorting = false }) { sorting = false; c.settings.setCollectionSort(it) }

    editingWish?.let { row ->
        WishlistDialog(
            row, wishOwned[row.item.id], priceType,
            onDismiss = { editingWish = null },
            onSave = { updated ->
                editingWish = null
                scope.launch { c.repo.updateWishlistItem(updated.copy(notes = updated.notes?.trim()?.ifEmpty { null })) }
            },
            onDelete = {
                editingWish = null
                scope.launch {
                    c.repo.deleteWishlistItem(row.item.id)
                    if (snackbar.showUndo("${row.item.card.displayName} taken off the wishlist")) c.repo.restoreWishlistItem(row.item)
                }
            },
        )
    }

    if (creating) {
        BinderNameDialog(null, onDismiss = { creating = false }) { name ->
            creating = false
            scope.launch { selected = c.repo.createBinder(name) }
        }
    }
    naming?.let { b ->
        BinderNameDialog(b, onDismiss = { naming = null }) { name ->
            naming = null
            scope.launch { c.repo.renameBinder(b.id, name) }
        }
    }
    if (merging) {
        val from = selected ?: Binder.UNSORTED
        val fromName = binderName(from, binders)
        MergeBinderDialog(from, rows?.filter { it.item.binderId == from }?.sumOf { it.item.quantity } ?: 0, onDismiss = { merging = false }) { into, deleteSource ->
            merging = false
            scope.launch {
                c.repo.mergeBinder(from, into, deleteSource)
                selected = into
                snackbar.showSnackbar("Moved the cards of $fromName into ${binderName(into, binders)}")
            }
        }
    }
    if (deleting && currentBinder != null) {
        val b = currentBinder
        DeleteBinderDialog(b, rows?.filter { it.item.binderId == b.id }?.sumOf { it.item.quantity } ?: 0, onDismiss = { deleting = false }) { deleteCards ->
            deleting = false
            scope.launch {
                c.repo.deleteBinder(b.id, deleteCards)
                selected = null
                snackbar.showSnackbar("Binder “${b.name}” deleted")
            }
        }
    }
}

/** Progress of a running CSV import; lookups are paced to Scryfall's rate limit, so big files take a minute or two. */
@Composable
private fun CsvImportCard(p: CsvImportProgress) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                if (p.total == 0) "Importing CSV…"
                else "Importing CSV: looking up cards on Scryfall, ${"%,d".format(p.done)} of ${"%,d".format(p.total)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (p.total == 0) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            else LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text(
                "You can keep using the app meanwhile.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun viewIcon(v: CollectionView): ImageVector = when (v) {
    CollectionView.LIST -> Icons.AutoMirrored.Filled.ViewList
    CollectionView.COMPACT -> Icons.Default.ViewHeadline
    CollectionView.GRID -> Icons.Default.GridView
}

/** One text line per stack: quantity, name, set and finish, price of one card (and the stack total). */
@Composable
private fun CompactRow(row: CollectionRow, priceType: PriceType, binder: String?, onClick: () -> Unit) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${item.quantity}×",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(item.card.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                item.card.setLabel,
                if (item.foil) item.card.finishName(item.finish) else null,
                item.condition.takeIf { it != "NM" && it.isNotEmpty() },
                item.language.takeIf { it != "EN" },
                binder,
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(unit), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (item.quantity > 1) {
                Text("${Fmt.money(unit?.let { it * item.quantity })} total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A big card picture with its quantity and finish, price of one card, trend and name underneath. */
@Composable
private fun CardTile(row: CollectionRow, priceType: PriceType, binder: String?, onClick: () -> Unit) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Column(Modifier.clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text("🃏", fontSize = 32.sp)
            item.card.imageUrl?.let { RetryingImage(it, Modifier.fillMaxSize(), contentDescription = item.card.displayName, contentScale = ContentScale.Crop) }
            if (item.quantity > 1) {
                Text(
                    "×${item.quantity}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            if (item.foil) Box(Modifier.align(Alignment.BottomStart).padding(4.dp)) { FinishTag(item.card, item.finish) }
        }
        Text(Fmt.money(unit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        TrendBadge(row.trend)
        if (item.quantity > 1) {
            Text("×${item.quantity} · ${Fmt.money(unit?.let { it * item.quantity })}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(item.card.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(item.card.setLabel, binder).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectionRowView(row: CollectionRow, priceType: PriceType, binder: String?, onClick: () -> Unit) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardThumb(item.card.imageUrl, width = 40)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${item.quantity}× ${item.card.displayName}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SetLine(item.card)
                    FinishTag(item.card, item.finish)
                    if (item.condition.isNotEmpty()) Tag(item.condition)
                    if (item.language != "EN") Tag(item.language)
                }
                binder?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(unit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TrendBadge(row.trend)
                if (item.quantity > 1) Text("${Fmt.money(unit?.let { it * item.quantity })} total", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
