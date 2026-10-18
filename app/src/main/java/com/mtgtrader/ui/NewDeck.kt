package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mtgtrader.container
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckEditor
import kotlinx.coroutines.launch

/** The ways to start a new deck (since 1.46). */
enum class NewDeckWay { LINK, USER, EMPTY, PRECON, SCANS, COPY, TEXT }

/** "New deck": every way to start one, from Archidekt or as a local deck. Since 1.46. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDeckSheet(onDismiss: () -> Unit, onPick: (NewDeckWay) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("New deck", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, bottom = 4.dp))
            SheetLabel("From Archidekt")
            SheetOption(Icons.Default.Link, "Import an Archidekt link", "Your own decks can be edited; other people's stay read only") { onPick(NewDeckWay.LINK) }
            SheetOption(Icons.Default.Person, "Choose from a user's decks", "Several decks from someone's Archidekt profile") { onPick(NewDeckWay.USER) }
            SheetLabel("On this phone (a local deck)")
            SheetOption(Icons.Default.Add, "Empty deck", "Then add cards, and put the commander in the Commander category") { onPick(NewDeckWay.EMPTY) }
            SheetOption(Icons.Default.Inventory2, "From a preconstructed deck", "Any Commander precon (MTGJSON's lists)") { onPick(NewDeckWay.PRECON) }
            SheetOption(Icons.Default.CameraAlt, "From scanned cards", "The cards in the Scan tab") { onPick(NewDeckWay.SCANS) }
            SheetOption(Icons.Default.ContentCopy, "Copy of one of my decks", "Starts as an exact copy, then changes freely") { onPick(NewDeckWay.COPY) }
            SheetOption(Icons.Default.ContentPaste, "Paste a Moxfield list", "Moxfield's export text (also Archidekt, Arena or EDHREC text)") { onPick(NewDeckWay.TEXT) }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** A name for a new local deck (empty, or a copy of [copyOf] picked from [decks]). */
@Composable
fun NameDeckDialog(title: String, suggested: String, decks: List<Deck>? = null, onDismiss: () -> Unit, onCreate: (name: String, copyOf: Deck?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(suggested) }
    var from by remember { mutableStateOf(decks?.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (decks != null) {
                    DropdownSelector("Deck to copy", from, decks, { it?.name.orEmpty() }, { d ->
                        if (name.isBlank() || name == from?.let { "${it.name} (copy)" }) name = "${d?.name} (copy)"
                        from = d
                    }, Modifier.fillMaxWidth())
                }
                OutlinedTextField(name, { name = it }, label = { Text("Deck name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "A local deck lives in this app (and your other phone after a sync). You can send it to Archidekt later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), from) }, enabled = name.isNotBlank() && (decks == null || from != null)) {
                Text("Make deck", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A new local deck from the cards in the Scan tab. */
@Composable
fun ScansDeckDialog(count: Int, onDismiss: () -> Unit, onCreate: (name: String, keep: Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Scanned deck") }
    var keep by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deck from scanned cards") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (count == 0) "The Scan tab has no cards." else "$count card(s) from the Scan tab, each in its auto category.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(name, { name = it }, label = { Text("Deck name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep them in the Scan tab", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(keep, { keep = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim(), keep) }, enabled = count > 0 && name.isNotBlank()) { Text("Make deck", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "Paste a Moxfield list": the decklist as text, read and looked up on Scryfall first ("Read"), then made into a
 * local deck with the categories chosen (the list's tags, or Archidekt's auto categories).
 */
@Composable
fun PasteDeckDialog(onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<DeckEditor.TextPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var useTags by rememberSaveable { mutableStateOf(true) }
    var commander by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Paste a Moxfield list") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "On Moxfield: the deck's ⋯ menu → Export → copy the text (the \"Moxfield\" format keeps its tags).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(name, { name = it }, label = { Text("Deck name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    text,
                    { text = it; preview = null },
                    label = { Text("Decklist") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                val p = preview
                if (p != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val cmd = commander ?: p.commanders.firstOrNull()
                            Text("${p.result.cardCount} cards read" + (cmd?.let { " · commander $it" } ?: " · no commander found"), style = MaterialTheme.typography.bodyMedium)
                            Text("${p.exact} with their exact printing · ${p.byName} by name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val lost = p.notFound + p.result.unreadable
                            if (lost.isNotEmpty()) {
                                Text(
                                    "${lost.size} line(s) not found: " + lost.take(5).joinToString(", ") { "\"$it\"" } + if (lost.size > 5) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (p.commanders.isEmpty() || p.commanders.size > 2) {
                        val candidates = p.found.values.filter { "Legendary" in it.typeLine && ("Creature" in it.typeLine || "Planeswalker" in it.typeLine) }.map { it.name }.distinct()
                        if (candidates.isNotEmpty()) {
                            DropdownSelector("Commander", commander, listOf<String?>(null) + candidates, { it ?: "None" }, { commander = it }, Modifier.fillMaxWidth())
                        }
                    }
                    if (p.result.hasTags) {
                        Text("The list has tags. Use as categories:", style = MaterialTheme.typography.titleSmall)
                        RadioRow("The list's tags (a card's first tag)", useTags) { useTags = true }
                        RadioRow("Archidekt's auto categories", !useTags) { useTags = false }
                    }
                }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Looking up the cards…", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val p = preview
            if (p == null) {
                TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            preview = c.deckEditor.previewText(text)
                            if (name.isBlank()) name = preview?.commanders?.firstOrNull()?.let { "$it deck" } ?: "Pasted deck"
                        } catch (e: Exception) {
                            error = "Couldn't look up the cards: ${e.message ?: e.javaClass.simpleName}"
                        }
                        busy = false
                    }
                }) { Text("Read", fontWeight = FontWeight.SemiBold) }
            } else {
                TextButton(enabled = !busy && p.found.isNotEmpty() && name.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        try {
                            onCreated(c.deckEditor.fromText(name.trim(), p, useTags, commander))
                        } catch (e: Exception) {
                            error = e.message ?: "Couldn't make the deck"
                            busy = false
                        }
                    }
                }) { Text("Make deck", fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
