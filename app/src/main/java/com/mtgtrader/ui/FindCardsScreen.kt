package com.mtgtrader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.Deck
import com.mtgtrader.data.FindGroup
import com.mtgtrader.data.FindLine
import com.mtgtrader.data.FindResult
import kotlinx.coroutines.launch

/**
 * "Find these cards" (since 1.104): pulls the cards of a pasted list (or a deck) out of the binders, one binder at a
 * time, with what's only in other decks and what isn't owned. Ticked cards go to the bottom of their binder.
 * [deck] is the deck it was opened from (0: none).
 */
@Composable
fun FindCardsScreen(nav: NavController, deck: Long) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var reload by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<FindResult?>(null) }
    var computing by remember { mutableStateOf(false) }
    // The list as pasted, or the deck it was opened from (kept on the phone: leaving and coming back keeps it).
    var list by remember { mutableStateOf("") }
    var deckId by remember { mutableStateOf(deck.takeIf { it > 0 }) }
    var deckName by remember { mutableStateOf<String?>(null) }
    var deckGone by remember { mutableStateOf(false) }
    var ticks by remember { mutableStateOf(setOf<String>()) }
    var skipBasics by remember { mutableStateOf(true) }
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(reload) {
        if (reload == 0) {
            // The list it was last left with (a pasted one, or a deck), unless it was opened from a deck.
            val saved = c.settings.findList
            if (deck == 0L) {
                if (saved.startsWith("deck:")) saved.removePrefix("deck:").toLongOrNull()?.let { deckId = it } else list = saved
            }
        }
        ticks = c.settings.findTicks.split('\n').filter { it.isNotBlank() }.toSet()
        computing = true
        // A deck that's gone leaves nothing to look for.
        val id = deckId
        val deckNow = id?.let { runCatching { c.db.deckDao().get(it) }.getOrNull() }
        deckGone = id != null && deckNow == null
        deckName = deckNow?.name
        result = when {
            id == null -> if (list.isBlank()) null else c.cardFinder.find(list, skipBasics)
            deckNow == null -> null
            else -> c.cardFinder.find(id, skipBasics)
        }
        computing = false
        // One line for this screen's list, so that leaving and coming back keeps it.
        c.settings.findList = deckId?.let { "deck:$it" } ?: list
    }

    fun rememberList() {
        c.settings.findList = deckId?.let { "deck:$it" } ?: list
        c.settings.findTicks = ticks.joinToString("\n")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Find these cards") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(
                        onClick = {
                            list = ""
                            deckId = null
                            deckName = null
                            ticks = emptySet()
                            result = null
                            c.settings.findList = ""
                            c.settings.findTicks = ""
                        },
                    ) { Text("Clear") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (computing) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val r = result
        if (deckGone) {
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This deck is gone.")
                TextButton(onClick = {
                    deckId = null
                    deckName = null
                    deckGone = false
                    rememberList()
                }) { Text("Paste a list instead") }
            }
            return@Scaffold
        }
        if (r == null) {
            // Nothing to look for yet: paste a list, or pick a deck.
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (deckId != null) Text("This deck has no cards of its own yet.")
                OutlinedTextField(
                    value = list,
                    onValueChange = { list = it },
                    label = { Text("Paste a list") },
                    placeholder = { Text("1 Sol Ring\n1 Arcane Signet …") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp),
                )
                Button(
                    onClick = {
                        deckId = null
                        deckName = null
                        ticks = emptySet()
                        // The list it looked for, kept for the way back to this screen.
                        c.settings.findList = list
                        c.settings.findTicks = ""
                        scope.launch { reload++ }
                    },
                    enabled = list.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Find") }
                if (decks.isNotEmpty()) {
                    Text("or pick a deck", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DeckPicker(decks) { d ->
                        list = ""
                        deckId = d.archidektId
                        deckName = d.name
                        ticks = emptySet()
                        c.settings.findList = "deck:${d.archidektId}"
                        c.settings.findTicks = ""
                        scope.launch { reload++ }
                    }
                }
            }
            return@Scaffold
        }
        FindCardsList(
            pad = pad,
            result = r,
            fromDeck = deckName,
            ticks = ticks,
            skipBasics = skipBasics,
            onToggle = { key, on ->
                ticks = if (on) ticks + key else ticks - key
                rememberList()
            },
            onSkipBasics = { on ->
                skipBasics = on
                scope.launch { reload++ }
            },
            onAddMissing = { missing ->
                scope.launch {
                    // Wanted by name, any printing: like adding a card the collection doesn't hold.
                    var n = 0
                    for ((name, copies) in missing) {
                        runCatching { c.repo.addToWishlist(com.mtgtrader.data.PendingCard.ref(name), com.mtgtrader.data.Finish.NONFOIL, copies) }
                            .onSuccess { n += copies }
                    }
                    snackbar.showSnackbar(if (n > 0) "Added $n cards to the wishlist" else "Couldn't add the cards to the wishlist")
                }
            },
        )
    }
}

/** A drop-down of the decks to look through. */
@Composable
private fun DeckPicker(decks: List<Deck>, onPick: (Deck) -> Unit) {
    DropdownSelector(
        label = "Deck",
        selected = decks.first(),
        options = decks,
        optionLabel = { it.name },
        onSelect = onPick,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The result: how much was found, then the copies binder by binder, what's only in other decks and what isn't
 * owned. Ticked lines are dimmed and struck through, at the bottom of their binder. Since 1.104.
 */
@Composable
fun FindCardsList(
    pad: PaddingValues,
    result: FindResult,
    fromDeck: String?,
    ticks: Set<String>,
    skipBasics: Boolean,
    onToggle: (key: String, on: Boolean) -> Unit,
    onSkipBasics: (Boolean) -> Unit,
    onAddMissing: (List<Pair<String, Int>>) -> Unit,
) {
    LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                if (fromDeck != null) {
                    Text(fromDeck, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                }
                Text(findSummary(result), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Skip basic lands")
                        Text(
                            "Basic lands (and Wastes, Snow-Covered…) are left out of the list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = skipBasics, onCheckedChange = onSkipBasics)
                }
            }
        }
        if (result.groups.isEmpty() && result.onlyInDecks.isEmpty() && result.notOwned.isEmpty()) {
            item(key = "none") {
                EmptyState("Nothing to find", "The list is empty, or every card in it is a basic land.")
            }
        }
        for (g in result.groups) findGroupSection(g, ticks, onToggle)
        if (result.onlyInDecks.isNotEmpty()) {
            item(key = "h_decks") { FindSectionHeader("Only in other decks (${result.onlyInDecks.sumOf { it.copies }})") }
            items(result.onlyInDecks, key = { "d_${it.name}" }) { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (line.copies > 1) "${line.copies}× " else "") + line.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "used by " + line.decks.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (result.notOwned.isNotEmpty()) {
            item(key = "h_missing") {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Not owned (${result.notOwned.sumOf { it.copies }})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAddMissing(result.notOwned.map { it.name to it.copies }) }) {
                        Text("Add all to wishlist")
                    }
                }
            }
            items(result.notOwned, key = { "m_${it.name}" }) { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (line.copies > 1) "${line.copies}× " else "") + line.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun LazyListScope.findGroupSection(g: FindGroup, ticks: Set<String>, onToggle: (String, Boolean) -> Unit) {
    item(key = "h_${g.binderKey}") { FindSectionHeader("${g.binderName} · ${g.lines.sumOf { it.copies }} cards") }
    // The copies found already go to the bottom of their binder.
    val ordered = g.lines.sortedBy { it.lineKey in ticks }
    items(ordered, key = { "l_${it.lineKey}" }) { line ->
        FindLineRow(line, checked = line.lineKey in ticks) { on -> onToggle(line.lineKey, on) }
    }
}

@Composable
private fun FindSectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

/** A copy to pull out: ticked off (dimmed, struck through) once it's found. */
@Composable
private fun FindLineRow(line: FindLine, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheck)
        Column(Modifier.weight(1f).alpha(if (checked) 0.5f else 1f)) {
            Text(
                (if (line.copies > 1) "${line.copies}× " else "") + line.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${line.set.uppercase()} #${line.number}", style = MaterialTheme.typography.bodySmall)
                if (line.foil) Tag(if (line.etched) "ETCHED" else "FOIL", FoilColor, Color.White)
                if (line.wantedPrinting) {
                    Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("wanted printing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** "Found 12 of 40 · 3 only in other decks · 2 not owned": the last two only when there are any. */
internal fun findSummary(r: FindResult): String = buildString {
    append("Found ${r.total.found} of ${r.total.wanted}")
    if (r.total.inDecks > 0) append(" · ${r.total.inDecks} only in other decks")
    if (r.total.notOwned > 0) append(" · ${r.total.notOwned} not owned")
}
