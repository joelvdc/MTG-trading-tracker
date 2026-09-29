package com.mtgtrader.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.mtgtrader.container
import com.mtgtrader.data.Brackets
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCardRow
import com.mtgtrader.data.DeckGroupBy
import com.mtgtrader.data.DeckGrouping
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.RuleZeroCard
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DeckScreen(nav: NavController, deckId: Long) {
    val c = LocalContext.current.container
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deck by remember { c.db.deckDao().observe(deckId) }.collectAsStateWithLifecycle(null)
    val rows by remember { c.db.deckDao().observeCards(deckId) }.collectAsStateWithLifecycle(emptyList())
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val job by c.decks.job.collectAsStateWithLifecycle()
    val result by c.decks.result.collectAsStateWithLifecycle()
    var groupBy by rememberSaveable { mutableStateOf(DeckGroupBy.CATEGORY) }
    var menu by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf<RuleZeroCard?>(null) }
    var selected by remember { mutableStateOf<DeckCardRow?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        if (r.imported || r.deckId != deckId) return@LaunchedEffect
        c.decks.consumeResult()
        snackbar.showSnackbar(r.message)
    }
    fun refresh() {
        if (!c.decks.refresh(deckId)) scope.launch { snackbar.showSnackbar("Wait for the current import to finish") }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(deck?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = ::refresh, enabled = job == null) { Icon(Icons.Default.Refresh, "Update from Archidekt") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val other = if (groupBy == DeckGroupBy.CATEGORY) DeckGroupBy.TYPE else DeckGroupBy.CATEGORY
                        DropdownMenuItem(
                            text = { Text("Group by ${other.label.lowercase()}") },
                            leadingIcon = { Icon(Icons.Default.ViewList, null) },
                            onClick = { menu = false; groupBy = other },
                        )
                        deck?.let { d ->
                            DropdownMenuItem(
                                text = { Text("Open on Archidekt") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = { menu = false; runCatching { uriHandler.openUri(d.archidektUrl) } },
                            )
                            d.saltUrl?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("Open on Commander Salt") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                    onClick = { menu = false; runCatching { uriHandler.openUri(url) } },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Delete deck") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val d = deck
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val sections = remember(rows, groupBy) { DeckGrouping.sections(rows, groupBy) }
        val value = remember(rows, priceType) { rows.sumOf { (it.unitPrice(priceType) ?: 0.0) * it.item.quantity } }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp)) {
            job?.takeIf { it.deckId == deckId }?.let { j -> item(key = "job") { Box(Modifier.padding(bottom = 8.dp)) { JobCard(j) } } }
            item(key = "header") { DeckHeader(d, value, priceType) }
            item(key = "rulezero") {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showing = RuleZeroCard.BRACKET }, enabled = d.saltId != null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Shield, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bracket card")
                    }
                    Button(onClick = { showing = RuleZeroCard.POWER }, enabled = d.saltId != null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Bolt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Power card")
                    }
                }
            }
            item(key = "scores") { ScoresCard(d, busy = job != null, onRetry = ::refresh) }
            sections.forEach { section ->
                item(key = "h_${section.title}") {
                    Text(
                        "${section.title} (${section.count})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(section.rows, key = { "c_${it.item.id}" }) { row -> DeckCardRowView(row, priceType) { selected = row } }
            }
        }
    }

    showing?.let { card -> deck?.let { d -> RuleZeroDialog(d, card) { showing = null } } }
    selected?.let { row -> DeckCardDialog(row, priceType) { selected = null } }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${deck?.name ?: "deck"}?") },
            text = { Text("The deck is only removed from this app; it stays on Archidekt and Commander Salt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        c.decks.delete(deckId)
                        nav.popBackStack()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DeckHeader(deck: Deck, value: Double, priceType: PriceType) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeckArt(deck, Modifier.fillMaxWidth().height(150.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorPips(deck.colorIdentity, size = 14)
            Spacer(Modifier.width(8.dp))
            Text(deck.commanders, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "${deck.cardCount} cards · ${Fmt.money(value)} (${priceType.short})" + if (deck.owner.isNotBlank()) " · by ${deck.owner}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScoresCard(deck: Deck, busy: Boolean, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (!deck.scored) {
                Text("Not scored yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    deck.scoreError?.let { "Commander Salt couldn't score this deck: $it" }
                        ?: "Commander Salt scores the deck when it's imported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!busy) TextButton(onClick = onRetry) { Text("Try again") }
                return@Column
            }
            Row(Modifier.fillMaxWidth()) {
                ScoreColumn("Power level", deck.powerLevel?.let(Scores::power), "out of 10", Modifier.weight(1f))
                ScoreColumn("Realistic bracket", deck.bracketRealistic?.toString(), Brackets.name(deck.bracketRealistic), Modifier.weight(1f))
                ScoreColumn("Baseline bracket", deck.bracketBaseline?.toString(), Brackets.name(deck.bracketBaseline), Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            val extras = listOfNotNull(deck.saltPercent?.let { "Salt ${it.toInt()}%" }, deck.archetype)
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Realistic: how the deck actually plays. Baseline: WotC's bracket rules to the letter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            deck.scoredAt?.let {
                Text("Scored by Commander Salt, ${Fmt.dateTime(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            deck.scoreError?.let {
                Text(
                    "The last update couldn't be scored ($it), so these are the earlier scores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ScoreColumn(label: String, value: String?, caption: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text(value ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(caption ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeckCardRowView(row: DeckCardRow, priceType: PriceType, onClick: () -> Unit) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardThumb(item.card.imageUrl, width = 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (item.quantity > 1) "${item.quantity}× " else "") + item.card.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                SetLine(item.card)
                FinishTag(item.card, item.finish)
                if (item.gameChanger) Tag("GAME CHANGER")
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(unit?.let { it * item.quantity }), style = MaterialTheme.typography.bodyMedium)
            TrendBadge(row.trend, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DeckCardDialog(row: DeckCardRow, priceType: PriceType, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val uriHandler = LocalUriHandler.current
    val item = row.item
    val owned by remember { c.db.collectionDao().observeOwned() }.collectAsStateWithLifecycle(emptyList())
    val ownedHere = owned.firstOrNull { it.scryfallId == item.card.scryfallId }?.qty ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.card.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardThumb(item.card.imageUrl, width = 72, enlargeable = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.card.setName, style = MaterialTheme.typography.bodyMedium)
                        SetLine(item.card)
                        Text("${item.types} · ${item.category}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (ownedHere > 0) "$ownedHere of this printing in your collection" else "Not in your collection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { runCatching { uriHandler.openUri(item.card.cardmarketUrl) } }) {
                            Text("Open on Cardmarket")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
                HorizontalDivider()
                PriceTable(
                    row.price?.toSet(item.foil) ?: PriceSet(trend = item.card.fallback(item.foil)),
                    priceType,
                    if (item.foil) "Cardmarket prices (${item.card.finishName(item.finish).lowercase()})" else "Cardmarket prices",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * The rule-zero cards full screen, for showing the table: swipe between bracket and power level.
 * The screen stays on at full brightness while it's open.
 */
@Composable
private fun RuleZeroDialog(deck: Deck, start: RuleZeroCard, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            (view.parent as? DialogWindowProvider)?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
            onDispose { view.keepScreenOn = false }
        }
        val context = LocalContext.current
        val pager = rememberPagerState(initialPage = start.ordinal) { RuleZeroCard.entries.size }
        val scope = rememberCoroutineScope()
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
                    val file = context.container.decks.cardFile(deck.archidektId, current)
                    if (file.exists()) shareCard(context, file, deck, current)
                }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                RuleZeroPage(deck, RuleZeroCard.entries[page])
            }
        }
    }
}

@Composable
private fun RuleZeroPage(deck: Deck, card: RuleZeroCard) {
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

private fun shareCard(context: Context, file: File, deck: Deck, card: RuleZeroCard) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "${deck.name}: ${card.label.lowercase()} rule-zero card (commandersalt.com)")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share rule-zero card"))
}
