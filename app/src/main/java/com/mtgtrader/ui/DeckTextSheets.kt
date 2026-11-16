package com.mtgtrader.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mtgtrader.container
import com.mtgtrader.data.DeckEditor
import com.mtgtrader.data.DeckPrintings
import com.mtgtrader.data.DeckText
import com.mtgtrader.data.UndoAction
import kotlinx.coroutines.launch

/**
 * A deck as text (since 1.96): the format, whether its categories and maybeboard come along, the text itself, and
 * buttons to copy it or share it. The same text the website writes (DeckText).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CopyDeckTextDialog(lines: List<DeckText.ExportLine>, deckName: String, onDismiss: () -> Unit, onCopied: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // Kept by name, so the choice survives rotation (rememberSaveable).
    var formatName by rememberSaveable { mutableStateOf(DeckText.Format.MOXFIELD.name) }
    var categories by rememberSaveable { mutableStateOf(true) }
    var maybeboard by rememberSaveable { mutableStateOf(true) }
    val format = DeckText.Format.entries.firstOrNull { it.name == formatName } ?: DeckText.Format.MOXFIELD
    val text = remember(lines, format, categories, maybeboard) { DeckText.export(lines, format, categories, maybeboard) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy $deckName as text", maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeckText.Format.entries.forEach { option ->
                        FilterChip(selected = option == format, onClick = { formatName = option.name }, label = { Text(option.label) })
                    }
                }
                SwitchLine("Categories", categories) { categories = it }
                SwitchLine("Maybeboard", maybeboard) { maybeboard = it }
                // The text in a box about 12 lines tall, scrolling inside it (long lines scroll sideways too).
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 192.dp).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Text(
                        text.ifEmpty { "No cards to copy." },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        softWrap = false,
                        modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    onCopied()
                    onDismiss()
                },
                enabled = text.isNotEmpty(),
            ) { Text("Copy") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, deckName)
                            .putExtra(Intent.EXTRA_TEXT, text)
                        runCatching { context.startActivity(Intent.createChooser(send, "Share $deckName as text")) }
                    },
                    enabled = text.isNotEmpty(),
                ) { Text("Share") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

/**
 * Pasting cards into a local deck (since 1.96): one card per line, a board, and the list's tags as categories when it
 * has any. The list is checked on Scryfall first, so what it can't find and what's already there show before it's added.
 */
@Composable
fun PasteCardsDialog(deckId: Long, onDismiss: () -> Unit, onAdded: (message: String, undo: UndoAction) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var maybeboard by rememberSaveable { mutableStateOf(false) }
    var useTags by rememberSaveable { mutableStateOf(true) }
    var preview by remember { mutableStateOf<DeckEditor.PastePreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The switch only matters when the pasted list has tags (Moxfield's #tags or Archidekt's [categories]).
    val hasTags = remember(text) { runCatching { DeckText.parse(text).hasTags }.getOrDefault(false) }
    val board = if (maybeboard) DeckText.Board.MAYBE else DeckText.Board.MAIN
    val checked = preview
    // What "Add" would add: the found lines the deck (or maybeboard) hasn't got yet; basic lands add their copies.
    val toAdd = checked?.let { pv ->
        val skipped = pv.inDeck.map { DeckPrintings.nameKey(it) }.toSet()
        pv.text.result.lines.sumOf { l ->
            val card = pv.text.found[l]
            if (card != null && DeckPrintings.nameKey(card.name) !in skipped) l.quantity else 0
        }
    } ?: 0

    fun check() {
        val body = text
        if (body.isBlank()) return
        scope.launch {
            busy = true
            error = null
            preview = null
            runCatching { c.deckEditor.previewPaste(deckId, body, board) }
                .onSuccess { if (body == text) preview = it }
                .onFailure { error = "Couldn't check the cards: " + (it.message ?: "no internet?") }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste cards") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    // Changing the list makes the check out of date, so its answer goes.
                    onValueChange = { text = it; preview = null; error = null },
                    placeholder = { Text("One card per line, e.g. 1 Sol Ring. Moxfield, Archidekt and Arena lists work too.") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !maybeboard, onClick = { maybeboard = false; preview = null }, label = { Text("Deck") })
                    FilterChip(selected = maybeboard, onClick = { maybeboard = true; preview = null }, label = { Text("Maybeboard") })
                }
                if (hasTags) SwitchLine("Use the list's tags as categories", useTags) { useTags = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::check, enabled = text.isNotBlank() && !busy) { Text("Check") }
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                checked?.let { pv ->
                    Text("$toAdd card" + (if (toAdd == 1) "" else "s") + " found", style = MaterialTheme.typography.bodyMedium)
                    if (pv.inDeck.isNotEmpty()) {
                        Text(
                            "Already in the deck (skipped): " + pv.inDeck.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pv.text.notFound.isNotEmpty()) {
                        Text(
                            "Not found: " + pv.text.notFound.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val pv = checked ?: return@TextButton
                    val n = toAdd
                    scope.launch {
                        adding = true
                        error = null
                        runCatching { c.deckEditor.addPasted(deckId, pv, board, useTags) }
                            .onSuccess { undo -> onAdded(if (n == 1) "Added 1 card" else "Added $n cards", undo) }
                            .onFailure { error = "Couldn't add the cards: " + (it.message ?: "no internet?") }
                        adding = false
                    }
                },
                enabled = checked != null && toAdd > 0 && !busy && !adding,
            ) {
                Text(
                    when {
                        checked == null -> "Add"
                        checked.inDeck.isNotEmpty() -> "Add the new cards"
                        else -> if (toAdd == 1) "Add 1 card" else "Add $toAdd cards"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A switch with its label in one row. */
@Composable
private fun SwitchLine(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(on, onChange)
    }
}
