package com.mtgtrader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.DeckEdits
import com.mtgtrader.data.DeckToCollection
import com.mtgtrader.data.UndoAction
import kotlinx.coroutines.launch

/** One of a deck's categories on the "Manage categories" screen. */
internal data class CategoryRow(val name: String, val cards: Int, val shown: Boolean)

/**
 * A deck's categories for "Manage categories": every category the deck knows or its cards use, except the
 * commander's, the maybeboard and the cards "Added in app"; alphabetical.
 */
internal fun categoryRows(lineCategories: Map<String, Int>, known: List<String>, hidden: Set<String>): List<CategoryRow> {
    val skip = setOf(DeckEdits.COMMANDER.lowercase(), DeckEdits.MAYBEBOARD.lowercase(), DeckToCollection.ADDED_IN_APP.lowercase())
    val counts = lineCategories.entries.groupBy({ it.key.lowercase() }, { it.value }).mapValues { it.value.sum() }
    return (lineCategories.keys + known).filter { it.isNotBlank() && it.lowercase() !in skip }.distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
        .map { CategoryRow(it, counts[it.lowercase()] ?: 0, it.lowercase() !in hidden) }
}

/**
 * "Manage categories" (since 1.47, in place of "Add a category…"): add categories, choose which are shown on the
 * deck page and offered when moving cards, and rename or delete them (on Archidekt too).
 */
@Composable
fun DeckCategoriesScreen(nav: NavController, deckId: Long) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deck by remember { c.db.deckDao().observe(deckId) }.collectAsStateWithLifecycle(null)
    val cards by remember { c.db.deckDao().observeCards(deckId) }.collectAsStateWithLifecycle(emptyList())
    val maybe by remember { c.db.deckDao().observeMaybeboard(deckId) }.collectAsStateWithLifecycle(emptyList())
    val hiddenAll by c.settings.deckHidden.collectAsStateWithLifecycle()
    val hidden = hiddenAll[deckId].orEmpty().keys
    val lineCategories = remember(cards, maybe) {
        (cards + maybe).filter { !it.item.commander }.groupBy { it.item.category }.mapValues { (_, l) -> l.sumOf { it.item.quantity } }
    }
    val rows = remember(lineCategories, deck?.categoryInfo, hidden) {
        categoryRows(lineCategories, DeckEdits.categories(deck?.categoryInfo).map { it.name }, hidden)
    }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<CategoryRow?>(null) }
    fun edit(message: String, op: suspend () -> UndoAction) {
        scope.launch {
            val undo = op()
            if (snackbar.showUndo(message)) undo()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Categories")
                        deck?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 4.dp, bottom = 24.dp)) {
            item(key = "add") {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 8.dp)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add a category")
                }
            }
            item(key = "head") {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Shown", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(48.dp))
                }
            }
            items(rows, key = { it.name.lowercase() }) { row ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge, color = if (row.shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (row.cards == 0) "empty" else "${row.cards} card" + if (row.cards == 1) "" else "s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(row.shown, { c.settings.setDeckHidden(deckId, row.name, !it) })
                    var open by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "${row.name}: more") }
                        ScreenMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("Rename…") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { open = false; renaming = row.name })
                            DropdownMenuItem(text = { Text("Delete…") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { open = false; deleting = row })
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(end = 12.dp))
            }
            item(key = "note") {
                Text(
                    "Switched off: left out of the deck page (its cards go to “$HIDDEN_TITLE” at the bottom) and of the " +
                        "category lists when moving cards. Empty categories are never shown on the deck page, only offered " +
                        "in those lists." + if (deck?.local == false) " Adding, renaming and deleting happen on Archidekt too." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, end = 12.dp),
                )
            }
        }
    }

    if (adding) {
        TextInputDialog("Add a category", "Category name", "", "Add", onDismiss = { adding = false }) { name ->
            adding = false
            if (rows.any { it.name.equals(name, true) } || name.equals(DeckEdits.COMMANDER, true) || name.equals(DeckEdits.MAYBEBOARD, true)) {
                scope.launch { snackbar.showSnackbar("The deck already has a category called $name") }
            } else scope.launch {
                c.deckEditor.addCategory(deckId, name)
                snackbar.showSnackbar("Added $name: move cards into it from a card's page or with “Move to category”")
            }
        }
    }
    renaming?.let { old ->
        TextInputDialog("Rename \"$old\"", "Category name", old, "Rename", onDismiss = { renaming = null }) { name ->
            renaming = null
            when {
                name == old -> {}
                rows.any { it.name.equals(name, true) && !it.name.equals(old, true) } ->
                    scope.launch { snackbar.showSnackbar("The deck already has a category called $name") }
                else -> edit("Renamed $old to $name") { c.deckEditor.renameCategory(deckId, old, name) }
            }
        }
    }
    deleting?.let { row ->
        DeleteCategoryDialog(
            row.name,
            row.cards,
            rows.map { it.name }.filterNot { it.equals(row.name, true) },
            onDismiss = { deleting = null },
            onArchidekt = deck?.local == false,
        ) { to ->
            deleting = null
            edit("Deleted the category ${row.name}") { c.deckEditor.deleteCategory(deckId, row.name, to) }
        }
    }
}
