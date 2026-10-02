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
import androidx.compose.material.icons.filled.LibraryAdd
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
import androidx.compose.runtime.produceState
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
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.Brackets
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCollectionCounts
import com.mtgtrader.data.DeckCardRow
import com.mtgtrader.data.DeckGroupBy
import com.mtgtrader.data.DeckGrouping
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.RuleZeroCard
import com.mtgtrader.data.PowerSource
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

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
    val source by c.settings.powerSource.collectAsStateWithLifecycle()
    var groupBy by rememberSaveable { mutableStateOf(DeckGroupBy.CATEGORY) }
    var menu by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf<RuleZeroCard?>(null) }
    var selected by remember { mutableStateOf<DeckCardRow?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    var addingToCollection by remember { mutableStateOf(false) }

    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        if (r.deckId != deckId) return@LaunchedEffect
        c.decks.consumeResult()
        // A fresh import lands here already; its page shows whether scoring failed.
        // In the screen's scope: clearing the result restarts this effect, which would cancel the snackbar.
        if (r.openDeck == null) scope.launch { snackbar.showSnackbar(r.message) }
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
                    IconButton(onClick = ::refresh, enabled = job == null) { Icon(Icons.Default.Refresh, "Reload decklist from Archidekt") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Reload decklist from Archidekt") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = job == null,
                            onClick = { menu = false; refresh() },
                        )
                        HorizontalDivider()
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
                            DropdownMenuItem(
                                text = { Text("Open on edhpowerlevel.com") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = {
                                    menu = false
                                    scope.launch { c.decks.edhUrl(d.archidektId)?.let { runCatching { uriHandler.openUri(it) } } }
                                },
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
                            text = { Text("Add to collection…") },
                            leadingIcon = { Icon(Icons.Default.LibraryAdd, null) },
                            onClick = { menu = false; addingToCollection = true },
                        )
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
            item(key = "header") { DeckHeader(d, rows.sumOf { it.item.quantity }, value, priceType) }
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
            item(key = "scores") {
                ScoresCard(d, source, busy = job != null, onRetry = ::refresh) {
                    if (!c.decks.rescore(deckId)) scope.launch { snackbar.showSnackbar("Wait for the current import to finish") }
                }
            }
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
    selected?.let { row ->
        DeckCardDialog(
            row, priceType,
            onRemove = if (row.item.addedInApp) {
                {
                    selected = null
                    scope.launch { c.decks.removeAddedCard(row.item.id) }
                }
            } else null,
        ) { selected = null }
    }
    if (addingToCollection) {
        deck?.let { d ->
            AddDeckToCollectionDialog(d, onDismiss = { addingToCollection = false }) { onlyMissing, skipBasics, choice ->
                addingToCollection = false
                scope.launch {
                    val binderId = c.repo.resolve(choice)
                    val (n, undo) = c.decks.addDeckToCollection(deckId, onlyMissing, skipBasics, binderId)
                    val where = choice.newName ?: binderName(binderId, c.db.binderDao().all())
                    if (n == 0) snackbar.showSnackbar("Nothing to add: you already own every card")
                    else if (snackbar.showUndo("Added $n card(s) to $where")) undo()
                }
            }
        }
    }
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
private fun DeckHeader(deck: Deck, cardCount: Int, value: Double, priceType: PriceType) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeckArt(deck, Modifier.fillMaxWidth().height(150.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorPips(deck.colorIdentity, size = 14)
            Spacer(Modifier.width(8.dp))
            Text(deck.commanders, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "${cardCount.takeIf { it > 0 } ?: deck.cardCount} cards · ${Fmt.money(value)} (${priceType.short})" + if (deck.owner.isNotBlank()) " · by ${deck.owner}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScoresCard(deck: Deck, source: PowerSource, busy: Boolean, onRetry: () -> Unit, onRescore: () -> Unit) {
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
                ScoreColumn("Power level", deck.power(source)?.let { Scores.power(it, source) }, "out of 10", Modifier.weight(1f))
                ScoreColumn("Realistic bracket", deck.bracketRealistic?.toString(), Brackets.name(deck.bracketRealistic), Modifier.weight(1f))
                ScoreColumn("Baseline bracket", deck.bracketBaseline?.toString(), Brackets.name(deck.bracketBaseline), Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            val extras = listOfNotNull(deck.saltPercent?.let { "Salt ${it.roundToInt()}%" }, deck.archetype)
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Realistic: how the deck actually plays. Baseline: WotC's bracket rules to the letter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (source.external) {
                val power = deck.power(source)
                val error = deck.powerError(source)
                val sv = deck.scrollVaultReading.takeIf { source == PowerSource.SCROLLVAULT && power != null }
                val other = deck.powerLevel?.let { " (Commander Salt: ${Scores.power(it)})" } ?: ""
                Text(
                    when {
                        power != null -> "Power level from ${source.site}" + (sv?.margin?.let { " (±${Scores.power(it)})" } ?: "") +
                            (deck.powerAt(source)?.let { ", ${Fmt.dateTime(it)}" } ?: "") + other
                        error != null -> "${source.site} couldn't rate this deck ($error); refresh the bracket and power level to try again." + other
                        else -> "Waiting for ${source.site}'s power level…$other"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (power == null && error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                sv?.let { r ->
                    val facts = listOfNotNull(
                        r.typicalWin?.let { "typical win turn $it" + (r.earliestWin?.let { e -> " (earliest $e)" } ?: "") },
                        r.bracket?.let { "its bracket $it" + (r.borderline?.let { b -> ", borderline $b" } ?: "") },
                    )
                    if (facts.isNotEmpty()) Text("ScrollVault: " + facts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
            if (!busy) {
                TextButton(onClick = onRescore, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh bracket and power level")
                }
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
                if (item.addedInApp) Tag("ADDED IN APP")
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(unit?.let { it * item.quantity }), style = MaterialTheme.typography.bodyMedium)
            TrendBadge(row.trend, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AddDeckToCollectionDialog(deck: Deck, onDismiss: () -> Unit, onConfirm: (onlyMissing: Boolean, skipBasics: Boolean, BinderChoice) -> Unit) {
    val c = LocalContext.current.container
    val counts by produceState<DeckCollectionCounts?>(null, deck.archidektId) { value = c.decks.collectionCounts(deck.archidektId) }
    var onlyMissing by remember { mutableStateOf(true) }
    var skipBasics by remember { mutableStateOf(true) }
    var choice by remember { mutableStateOf(BinderChoice(newName = deck.name)) }
    // Adding the same deck again goes into its binder from last time.
    LaunchedEffect(Unit) { c.db.binderDao().byName(deck.name)?.let { choice = BinderChoice(it.id) } }
    val n = counts?.count(onlyMissing, skipBasics)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${deck.name} to the collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Adds the exact printings and finishes from the decklist, as Near Mint English copies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RadioRow("Only cards I don't own yet (any printing counts)" + (counts?.let { " · ${it.count(true, skipBasics)}" } ?: ""), onlyMissing) { onlyMissing = true }
                RadioRow("All cards" + (counts?.let { " · ${it.count(false, skipBasics)}" } ?: ""), !onlyMissing) { onlyMissing = false }
                CheckRow("Skip basic lands", skipBasics) { skipBasics = it }
                BinderPicker("Put them in", choice, { choice = it }, suggestedName = deck.name, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(onlyMissing, skipBasics, choice) }, enabled = n != null && choice.isValid) {
                Text(if (n == null) "Add" else "Add $n card(s)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeckCardDialog(row: DeckCardRow, priceType: PriceType, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
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
        dismissButton = onRemove?.let { remove ->
            { TextButton(onClick = remove) { Text("Remove from deck", color = MaterialTheme.colorScheme.error) } }
        },
    )
}
