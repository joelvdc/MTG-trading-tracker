package com.mtgtrader.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckJob
import com.mtgtrader.data.DeckLinks
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DecksScreen(nav: NavController) {
    val c = LocalContext.current.container
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(null)
    val job by c.decks.job.collectAsStateWithLifecycle()
    val result by c.decks.result.collectAsStateWithLifecycle()
    var importing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        c.decks.consumeResult()
        // A freshly imported deck opens straight away (its page says if scoring failed); anything else is reported here.
        if (r.imported && r.deckId != null) nav.navigate("deck/${r.deckId}")
        else snackbar.showSnackbar(r.message)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Commander decks") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { importing = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Import deck") },
                expanded = job == null,
            )
        },
    ) { pad ->
        val list = decks
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            job?.let { j -> item(key = "job") { JobCard(j) } }
            if (list.isEmpty() && job == null) {
                item(key = "empty") {
                    EmptyState(
                        "No decks yet",
                        "Import a public deck with its Archidekt link, or share it to this app from Archidekt. " +
                            "Commander Salt scores its power level and brackets and makes rule-zero cards you can show your table.",
                    )
                }
            }
            items(list, key = { it.archidektId }) { deck -> DeckRow(deck) { nav.navigate("deck/${deck.archidektId}") } }
        }
    }

    if (importing) {
        ImportDeckDialog(
            onDismiss = { importing = false },
            onImport = { link ->
                importing = false
                if (!c.decks.import(link)) scope.launch { snackbar.showSnackbar("Wait for the current import to finish") }
            },
        )
    }
}

@Composable
fun JobCard(job: DeckJob) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(job.message, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun DeckRow(deck: Deck, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DeckArt(deck, Modifier.width(88.dp).height(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorPips(deck.colorIdentity)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        deck.commanders,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (deck.scored) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        deck.powerLevel?.let { Tag("Power ${Scores.power(it)}") }
                        deck.bracketRealistic?.let { Tag("Realistic B$it") }
                        deck.bracketBaseline?.let { Tag("Baseline B$it") }
                    }
                } else {
                    Text("Not scored yet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The deck's featured art from Archidekt, or its commander's art. */
@Composable
fun DeckArt(deck: Deck, modifier: Modifier = Modifier) {
    val url = deck.artUrl ?: deck.commanderScryfallId?.let { id -> "https://cards.scryfall.io/art_crop/front/${id[0]}/${id[1]}/$id.jpg" }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Text("🃏")
        if (url != null) RetryingImage(url, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

private val PIP_COLORS = mapOf(
    'W' to Color(0xFFF8F1D6), 'U' to Color(0xFF3F8FD2), 'B' to Color(0xFF3B3434),
    'R' to Color(0xFFD9503F), 'G' to Color(0xFF3E9A5B),
)

/** Colour identity as mana-coloured dots; a grey one for colourless. */
@Composable
fun ColorPips(identity: String, size: Int = 12) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val pips = identity.mapNotNull { PIP_COLORS[it] }.ifEmpty { listOf(Color(0xFFB9B4B0)) }
        pips.forEach { color ->
            Box(Modifier.size(size.dp).clip(CircleShape).background(color).border(0.5.dp, Color.Gray.copy(alpha = 0.6f), CircleShape))
        }
    }
}

object Scores {
    /** Power level with one decimal, e.g. "4.4". */
    fun power(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)
}

@Composable
private fun ImportDeckDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    // Most people copy the link first, so offer what's on the clipboard when it's a deck link.
    val clip = remember {
        runCatching {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                ?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()?.takeIf { DeckLinks.archidektId(it) != null && "archidekt" in it.lowercase() }
    }
    var link by rememberSaveable { mutableStateOf(clip?.trim() ?: "") }
    val valid = DeckLinks.archidektId(link) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from Archidekt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste the link of a public Archidekt deck, e.g. archidekt.com/decks/12345/my_deck.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Deck link") },
                    singleLine = true,
                    isError = link.isNotBlank() && !valid,
                    supportingText = { if (link.isNotBlank() && !valid) Text("That isn't an Archidekt deck link") },
                    trailingIcon = { if (link.isNotEmpty()) IconButton(onClick = { link = "" }) { Icon(Icons.Default.Clear, "Clear") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (valid) onImport(link) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(link) }, enabled = valid) { Text("Import", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
