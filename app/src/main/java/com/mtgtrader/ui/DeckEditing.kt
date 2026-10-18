package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgtrader.container
import com.mtgtrader.data.ArchidektCollectionClient
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckEditor
import com.mtgtrader.data.DeckEdits
import kotlinx.coroutines.launch

/** The top bar while cards are selected on an editable deck (since 1.46). [inMaybeboard]: all selected are there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(count: Int, inMaybeboard: Boolean, onClose: () -> Unit, onMove: () -> Unit, onBoard: () -> Unit, onRemove: () -> Unit) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Stop selecting") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        actions = {
            IconButton(onClick = onMove) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Move to a category") }
            IconButton(onClick = onBoard) { Icon(if (inMaybeboard) Icons.Default.Style else Icons.Default.Inventory2, if (inMaybeboard) "Put in the deck" else "Put in the maybeboard") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove from the deck") }
        },
    )
}

/**
 * Where to move cards: "Auto" (each card's Archidekt category), one of the deck's categories (with how many cards
 * are in it), or a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    title: String,
    categories: List<Pair<String, Int>>,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    current: String? = null,
    autoLabel: String = "Auto (Archidekt's category for each card)",
    onPick: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Never taller than the room above the phone's buttons, so the last line ("New category…") always shows.
        Column(Modifier.navigationBarsPadding().heightIn(max = menuMaxHeight()).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp))
            SheetItem(Icons.Default.AutoAwesome, autoLabel, null) { onPick(DeckEditor.AUTO) }
            categories.forEach { (name, n) ->
                val now = current?.equals(name, true) == true
                SheetItem(if (now) Icons.Default.Check else Icons.AutoMirrored.Filled.Label, name, n, bold = now) { if (now) onDismiss() else onPick(name) }
            }
            ListItem(
                headlineContent = { Text("New category…", color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onNew),
            )
        }
    }
}

@Composable
private fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, count: Int?, bold: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, fontWeight = if (bold) FontWeight.Bold else null) },
        trailingContent = { count?.let { Text("$it") } },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** A one-line text, e.g. a category's or a deck's new name. */
@Composable
fun TextInputDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Deleting a category: where its cards go (their auto categories, or another category). */
@Composable
fun DeleteCategoryDialog(name: String, count: Int, others: List<String>, onDismiss: () -> Unit, onArchidekt: Boolean = false, onConfirm: (moveTo: String?) -> Unit) {
    var auto by remember { mutableStateOf(true) }
    var to by remember { mutableStateOf(others.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$name\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (count == 0) "It holds no cards." else "Its $count card(s) stay in the deck. Move them to:", style = MaterialTheme.typography.bodyMedium)
                if (count > 0) {
                    RadioRow("Their auto categories", auto) { auto = true }
                    if (others.isNotEmpty()) {
                        RadioRow("Another category", !auto) { auto = false }
                        if (!auto) DropdownSelector("Category", to, others, { it.orEmpty() }, { to = it }, Modifier.fillMaxWidth())
                    }
                }
                if (onArchidekt) Text("The category is deleted on Archidekt too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(if (auto) null else to) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The card page's editing controls on an editable deck: its category (with its auto category), deck or
 * maybeboard, and how many copies. Since 1.47 the labels sit above the controls, so they keep their room when the
 * phone's text is larger, and the category opens the list from the bottom ([onPickCategory]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditControls(
    category: String,
    autoCategory: String?,
    maybeboard: Boolean,
    quantity: Int,
    onPickCategory: () -> Unit,
    onBoard: (Boolean) -> Unit,
    onQuantity: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Category", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = onPickCategory, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 8.dp)) {
            Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        autoCategory?.takeIf { !it.equals(category, true) }?.let {
            Text("Auto category: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
        }
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!maybeboard, { if (maybeboard) onBoard(false) }, SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { Text("In the deck", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            SegmentedButton(maybeboard, { if (!maybeboard) onBoard(true) }, SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { Text("Maybeboard", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Copies", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onQuantity(quantity - 1) }, enabled = quantity > 1) { Icon(Icons.Default.Remove, "One fewer") }
            Text("$quantity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp))
            FilledTonalIconButton(onClick = { onQuantity(quantity + 1) }) { Icon(Icons.Default.Add, "One more") }
        }
    }
}

/** Edits waiting to be sent to Archidekt: why (offline, not logged in) and a button to send them now. */
@Composable
fun WaitingBanner(count: Int, offline: Boolean, loggedIn: Boolean, sending: Boolean, onSend: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (offline) Icons.Default.CloudOff else Icons.Default.CloudUpload, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(
                "$count edit" + (if (count == 1) "" else "s") + when {
                    !loggedIn -> " saved on the phone: log in to Archidekt (Settings) to send them."
                    offline -> " saved on the phone, sent to Archidekt when online."
                    sending -> " being sent to Archidekt…"
                    else -> " waiting to be sent to Archidekt."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (sending) CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(18.dp), strokeWidth = 2.dp)
            else if (loggedIn && !offline) TextButton(onClick = onSend) { Text("Send now") }
        }
    }
}

/** The visibility choice for an Archidekt deck: private, unlisted or public. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilityChoice(value: String, onChange: (String) -> Unit) {
    val options = listOf(
        ArchidektCollectionClient.VISIBILITY_PRIVATE to "Private",
        ArchidektCollectionClient.VISIBILITY_UNLISTED to "Unlisted",
        ArchidektCollectionClient.VISIBILITY_PUBLIC to "Public",
    )
    Text("Who can see it", style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (key, label) ->
            SegmentedButton(
                value == key, { onChange(key) }, SegmentedButtonDefaults.itemShape(i, options.size),
                icon = { if (key == ArchidektCollectionClient.VISIBILITY_PRIVATE && value == key) Icon(Icons.Default.Lock, null, Modifier.size(16.dp)) },
            ) { Text(label) }
        }
    }
    Text(
        "Private: only you (Commander Salt can't read it, so no bracket or power level). Unlisted: anyone with the link. Public: listed on Archidekt.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The user's Archidekt folders, loaded when the dialog opens. */
@Composable
private fun FolderChoice(folders: List<Pair<Long, String>>?, error: String?, selected: Long?, onSelect: (Long, String) -> Unit) {
    when {
        error != null -> Text("Couldn't read your folders: $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        folders == null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Reading your folders on Archidekt…", style = MaterialTheme.typography.bodySmall)
        }
        folders.isEmpty() -> Text("No folders found; the deck goes into your top folder.", style = MaterialTheme.typography.bodySmall)
        else -> DropdownSelector(
            "Folder",
            folders.firstOrNull { it.first == selected },
            folders,
            { it?.second.orEmpty() },
            { it?.let { f -> onSelect(f.first, f.second) } },
            Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Sends a local deck to Archidekt: as a new deck of the logged-in user (name, folder, visibility; unlisted by
 * default, as the owner chose), or into one of their decks (adding its cards or replacing that deck's list).
 */
@Composable
fun SendToArchidektDialog(deck: Deck, ownDecks: List<Deck>, onDismiss: () -> Unit, onDone: (Long, String) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var intoDeck by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(deck.name) }
    var visibility by rememberSaveable { mutableStateOf(ArchidektCollectionClient.VISIBILITY_UNLISTED) }
    var folders by remember { mutableStateOf<List<Pair<Long, String>>?>(null) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var folder by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var target by remember { mutableStateOf(ownDecks.firstOrNull()) }
    var replace by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            folders = c.deckEditor.folders().also { list -> folder = list.firstOrNull { it.second.contains("Brew", true) } ?: list.firstOrNull() }
        } catch (e: Exception) {
            folderError = e.message ?: "unknown error"
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Send ${deck.name} to Archidekt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                @OptIn(ExperimentalMaterial3Api::class)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!intoDeck, { intoDeck = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("New deck") }
                    SegmentedButton(intoDeck, { intoDeck = true }, SegmentedButtonDefaults.itemShape(1, 2), enabled = ownDecks.isNotEmpty()) { Text("Into a deck") }
                }
                if (!intoDeck) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name on Archidekt") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FolderChoice(folders, folderError, folder?.first) { id, path -> folder = id to path }
                    VisibilityChoice(visibility) { visibility = it }
                    Text(
                        "Afterwards the deck is in the Archidekt section, editable, and changes are sent to Archidekt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DropdownSelector("Your Archidekt deck", target, ownDecks, { it?.name.orEmpty() }, { target = it }, Modifier.fillMaxWidth())
                    RadioRow("Add its cards (a card in both keeps the higher number)", !replace) { replace = false }
                    RadioRow("Replace that deck's list with this one", replace) { replace = true }
                    Text("This local deck is then gone; the Archidekt deck holds the result.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Sending the cards to Archidekt…", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (if (intoDeck) target != null else name.isNotBlank()),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val id = if (intoDeck) c.deckEditor.sendInto(deck.archidektId, target!!.archidektId, replace)
                            else c.deckEditor.sendAsNew(deck.archidektId, name.trim(), folder?.first, folder?.second?.substringAfterLast(" › "), visibility)
                            onDone(id, if (intoDeck) "Sent to ${target!!.name} on Archidekt" else "Made ${name.trim()} on Archidekt")
                        } catch (e: Exception) {
                            error = e.message ?: "Couldn't send the deck"
                            busy = false
                        }
                    }
                },
            ) { Text(if (intoDeck) "Send" else "Create deck", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** One of the user's own Archidekt decks: its folder and who can see it. */
@Composable
fun FolderVisibilityDialog(deck: Deck, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var visibility by rememberSaveable { mutableStateOf(deck.visibility ?: ArchidektCollectionClient.VISIBILITY_PUBLIC) }
    var folders by remember { mutableStateOf<List<Pair<Long, String>>?>(null) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var folder by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            folders = c.deckEditor.folders().also { list -> folder = list.firstOrNull { it.first == deck.folderId } }
        } catch (e: Exception) {
            folderError = e.message ?: "unknown error"
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("${deck.name} on Archidekt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FolderChoice(folders, folderError, folder?.first) { id, path -> folder = id to path }
                VisibilityChoice(visibility) { visibility = it }
                Text("Folders are made, renamed and deleted on Archidekt's website.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val f = folder
                        if (f != null && f.first != deck.folderId) c.deckEditor.moveToFolder(deck.archidektId, f.first, f.second)
                        if (visibility != deck.visibility) c.deckEditor.setVisibility(deck.archidektId, visibility)
                        onDone("Saved on Archidekt")
                    } catch (e: Exception) {
                        error = e.message ?: "Archidekt didn't take the change"
                        busy = false
                    }
                }
            }) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** "Maybeboard" first among the categories a card can be moved to, the rest alphabetically. */
fun categoryChoices(names: Collection<String>): List<String> =
    (names.filter { it.isNotBlank() && !it.equals(DeckEdits.COMMANDER, true) }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() })
        .let { l -> (listOf(DeckEdits.MAYBEBOARD) + l.filterNot { it.equals(DeckEdits.MAYBEBOARD, true) }) + DeckEdits.COMMANDER }
