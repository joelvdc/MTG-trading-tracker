package com.mtgtrader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.ArchidektDeckSummary
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckLinks
import kotlinx.coroutines.launch

/** Lists an Archidekt user's public decks so several can be picked and imported in one go. */
@Composable
fun ImportUserScreen(nav: NavController, initialUser: String?) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }
    var user by rememberSaveable { mutableStateOf(initialUser ?: c.settings.archidektUser) }
    var decks by remember { mutableStateOf<List<ArchidektDeckSummary>?>(null) }
    var imported by remember { mutableStateOf(emptySet<Long>()) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val username = DeckLinks.archidektUser(user)

    fun find() {
        val name = username ?: return
        keyboard?.hide()
        scope.launch {
            loading = true
            error = null
            try {
                val list = c.decks.userDecks(name)
                decks = list
                imported = c.decks.importedIds()
                selected = emptySet()
                c.settings.archidektUser = name
                if (list.isEmpty()) error = "$name has no public decks (or the name is misspelled)"
            } catch (e: Exception) {
                error = "Couldn't load the decks: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { if (username != null && initialUser != null) find() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from a user") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val list = decks
            if (!list.isNullOrEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (selected.isNotEmpty()) {
                            val minutes = (selected.size * 10 + 59) / 60
                            Text(
                                "Commander Salt scores one deck at a time: about $minutes min. It runs in the background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                val chosen = list.filter { it.id in selected }
                                if (c.decks.importMany(chosen)) nav.popBackStack()
                                else scope.launch { snackbar.showSnackbar("Wait for the current import to finish") }
                            },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (selected.isEmpty()) "Choose decks to import" else "Import ${selected.size} deck(s)") }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            SearchField(
                user, { user = it }, "Archidekt username or profile link",
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                onSearch = { find() },
                isError = user.isNotBlank() && username == null,
                trailing = {
                    TextButton(onClick = ::find, enabled = username != null && !loading) { Text("Find") }
                },
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            val list = decks
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list == null -> EmptyState(
                    "Find someone's decks",
                    "Enter an Archidekt username, e.g. from archidekt.com/u/username, to list their public decks and pick the ones to import.",
                )
                list.isNotEmpty() -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${list.size} public decks · ${selected.size} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        TextButton(onClick = { selected = list.map { it.id }.toSet() }) { Text("Select all") }
                        TextButton(onClick = { selected = emptySet() }) { Text("Select none") }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(list, key = { it.id }) { d ->
                            val on = d.id in selected
                            UserDeckRow(d, on, d.id in imported) { selected = if (on) selected - d.id else selected + d.id }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserDeckRow(deck: ArchidektDeckSummary, checked: Boolean, alreadyImported: Boolean, onToggle: () -> Unit) {
    Card(onClick = onToggle, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            // Reuses the deck art box; only the art matters here.
            DeckArt(Deck(deck.id, deck.name, "", "", null, deck.artUrl, deck.colors, deck.size), Modifier.width(64.dp).height(46.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(deck.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorPips(deck.colors, size = 10)
                    Text(
                        "${deck.size} cards" + (deck.folder?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (alreadyImported) Tag("IMPORTED · WILL UPDATE")
                    if (!deck.commanderFormat) Tag("NOT COMMANDER")
                }
            }
        }
    }
}
