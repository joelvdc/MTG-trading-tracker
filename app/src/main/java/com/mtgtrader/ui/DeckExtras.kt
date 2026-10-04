package com.mtgtrader.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.DeckToCollection
import com.mtgtrader.data.DeckUsage
import com.mtgtrader.data.DeckUse
import kotlinx.coroutines.launch

/**
 * The deck's cards you don't own in any printing (copies you own count once, however many decks
 * use them), with what buying them would cost; they can go on the wishlist or be shared as a
 * plain list for Cardmarket's wants list. Since 1.16.
 */
@Composable
fun MissingCardsScreen(nav: NavController, deckId: Long) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val deck by remember { c.db.deckDao().observe(deckId) }.collectAsStateWithLifecycle(null)
    val rows by remember { c.db.deckDao().observeCards(deckId) }.collectAsStateWithLifecycle(null)
    val ownedList by remember { c.db.collectionDao().observeOwnedByName() }.collectAsStateWithLifecycle(null)
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    var skipBasics by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Cards I'm missing", maxLines = 1) },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val all = rows
        val ownedRaw = ownedList
        if (all == null || ownedRaw == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val owned = remember(ownedRaw) {
            ownedRaw.groupBy { it.name.lowercase() }.mapValues { (_, v) -> v.sumOf { it.qty } }
        }
        val byId = remember(all) { all.associateBy { it.item.id } }
        val missing = remember(all, owned, skipBasics) {
            DeckToCollection.plan(all.map { it.item }, owned, onlyMissing = true, skipBasics = skipBasics)
                .sortedBy { it.first.card.name.lowercase() }
        }
        val cost = missing.sumOf { (card, n) -> (byId[card.id]?.unitPrice(priceType) ?: 0.0) * n }
        val copies = missing.sumOf { it.second }
        val listText = missing.joinToString("\n") { (card, n) -> "$n ${card.card.name}" }

        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp)) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(deck?.name ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (copies == 0) "You own every card of this deck (any printing counts)."
                        else "$copies card(s) missing · about ${Fmt.money(cost)} (${priceType.short}, at the decklist's printings)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = skipBasics, onClick = { skipBasics = !skipBasics }, label = { Text("Skip basic lands") })
                    }
                    if (copies > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    val (n, undo) = c.repo.addManyToWishlist(missing)
                                    if (snackbar.showUndo("Added $n card(s) to the wishlist")) undo()
                                }
                            }) {
                                Icon(Icons.Default.Star, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add to wishlist")
                            }
                            OutlinedButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, "Missing cards: ${deck?.name ?: ""}")
                                    .putExtra(Intent.EXTRA_TEXT, listText)
                                context.startActivity(Intent.createChooser(send, "Share the list"))
                            }) {
                                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            }
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(listText))
                                scope.launch { snackbar.showSnackbar("List copied") }
                            }) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                            }
                        }
                        Text(
                            "The shared list (\"1 Card name\" per line) can be pasted into a Cardmarket wants list or Archidekt.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(Modifier.padding(top = 4.dp))
                }
            }
            items(missing, key = { it.first.id }) { (card, n) ->
                val row = byId[card.id]
                val have = owned[card.card.name.lowercase()] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardThumb(card.card.imageUrl, width = 34, enlargeable = true)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$n× ${card.card.displayName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            SetLine(card.card)
                            FinishTag(card.card, card.finish)
                        }
                        if (have > 0) Text("You own $have of ${card.quantity}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Fmt.money(row?.unitPrice(priceType)?.let { it * n }), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Cards that are in more than one of your decks, with how many copies those decks need against how many you own. Since 1.16. */
@Composable
fun SharedCardsScreen(nav: NavController) {
    val c = LocalContext.current.container
    val uses by remember { c.db.deckDao().observeUsage() }.collectAsStateWithLifecycle(null)
    val ownedList by remember { c.db.collectionDao().observeOwnedByName() }.collectAsStateWithLifecycle(null)
    var onlyShort by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Cards in several decks") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val u = uses
        val o = ownedList
        if (u == null || o == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val owned = remember(o) { o.groupBy { it.name.lowercase() }.mapValues { (_, v) -> v.sumOf { it.qty } } }
        val shared = remember(u) {
            DeckUsage.byName(u).filterValues { it.size > 1 }.toList()
                .sortedWith(compareByDescending<Pair<String, List<DeckUse>>> { it.second.size }.thenBy { it.first })
        }
        val shown = if (onlyShort) shared.filter { (k, v) -> (owned[k] ?: 0) < v.sumOf { it.quantity } } else shared
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp)) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${shared.size} cards are in more than one deck (basic lands left out). " +
                            "Without enough copies you'll be moving them between decks.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilterChip(selected = onlyShort, onClick = { onlyShort = !onlyShort }, label = { Text("Only cards I don't have enough of") })
                    HorizontalDivider()
                }
            }
            if (shown.isEmpty()) item(key = "none") { EmptyState("Nothing to show", if (shared.isEmpty()) "No card is in more than one of your decks." else "You own enough copies of every shared card.") }
            items(shown, key = { it.first }) { (key, list) ->
                val need = list.sumOf { it.quantity }
                val have = owned[key] ?: 0
                Column(Modifier.fillMaxWidth().clickable { open = if (open == key) null else key }.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardThumb(list.firstNotNullOfOrNull { it.imageUrl }, width = 34)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(list.first().name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(
                                "In ${list.size} decks: " + list.joinToString { it.deckName },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (open == key) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("own $have / $need", color = if (have < need) TrendColors.down else TrendColors.up, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (open == key) {
                        list.forEach { d ->
                            Text(
                                "${d.quantity}× in ${d.deckName}  ›",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().clickable { nav.navigate("deck/${d.deckId}") }.padding(start = 44.dp, top = 6.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Also in: …" — the other decks a card is in, for card dialogs; nothing when there are none. */
@Composable
fun DeckUsageLine(cardName: String, exceptDeck: Long? = null, prefix: String = "In your decks") {
    val c = LocalContext.current.container
    val uses by remember { c.db.deckDao().observeUsage() }.collectAsStateWithLifecycle(emptyList())
    val here = remember(uses, cardName, exceptDeck) {
        if (DeckToCollection.isBasic(cardName)) emptyList()
        else uses.filter { it.name.equals(cardName, true) && it.deckId != exceptDeck }
    }
    if (here.isEmpty()) return
    Text(
        "$prefix: " + here.joinToString { if (it.quantity > 1) "${it.deckName} (${it.quantity})" else it.deckName },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
