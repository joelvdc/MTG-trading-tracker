package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.Money
import com.mtgtrader.data.Precons
import com.mtgtrader.data.ScryfallApi
import kotlinx.coroutines.launch

/**
 * Add a preconstructed Commander deck: pick the deck, then the binder, the price paid and the day.
 * Its cards (no basic lands) go into the binder, each with its share of the price, and the deck is
 * remembered as an order. Since 1.33.
 */
@Composable
fun PreconScreen(nav: NavController, local: Boolean = false) {
    val c = LocalContext.current.container
    var decks by remember { mutableStateOf<List<Precons.Info>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<Precons.Info?>(null) }

    LaunchedEffect(Unit) {
        try {
            decks = c.precons.list()
        } catch (e: Exception) {
            error = "Couldn't get the list of decks from MTGJSON: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (local) "Deck from a precon" else "Add a preconstructed deck") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val all = decks
        when {
            error != null -> EmptyState("No deck list", error!!, Modifier.padding(pad))
            all == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(Modifier.padding(pad)) {
                SearchField(query, { query = it }, "Deck name or set code", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
                val shown = remember(all, query) { Precons.search(all, query) }
                PreconList(shown, onPick = { picked = it }, forDeck = local)
            }
        }
    }

    if (local) picked?.let { info ->
        val scope = rememberCoroutineScope()
        var making by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!making) picked = null },
            title = { Text(info.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Makes a local deck with this precon's cards (in their printings), each in Archidekt's auto category. Your collection isn't changed.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (making) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Getting the deck's cards…")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !making, onClick = {
                    making = true
                    scope.launch {
                        try {
                            val id = c.deckEditor.fromPrecon(info)
                            picked = null
                            nav.navigate("deck/$id") { popUpTo("decks") }
                        } catch (e: Exception) {
                            error = "Couldn't make the deck: ${e.message ?: e.javaClass.simpleName}"
                            making = false
                        }
                    }
                }) { Text("Make deck") }
            },
            dismissButton = { TextButton(onClick = { picked = null }, enabled = !making) { Text("Cancel") } },
        )
    } else picked?.let { info ->
        AddPreconDialog(info, onDismiss = { picked = null }) { message ->
            picked = null
            c.repo.reportImport(message)
            nav.safePopBackStack()
        }
    }
}

/** Commander decks, newest first. Split out for the screenshot tests. */
@Composable
fun PreconList(decks: List<Precons.Info>, onPick: (Precons.Info) -> Unit, modifier: Modifier = Modifier, forDeck: Boolean = false) {
    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "note") {
            Text(
                if (forDeck) "Commander decks, newest first (the lists come from MTGJSON). Pick one to make a local deck of it."
                else "Commander decks, newest first (the lists come from MTGJSON). The deck's cards are added in their own printing, without basic lands; " +
                    "what you paid is split over them by what each is worth today.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (decks.isEmpty()) item(key = "none") { Text("No deck with that name.", style = MaterialTheme.typography.bodyMedium) }
        items(decks, key = { it.fileName }) { d ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth().clickable { onPick(d) }) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(d.code.takeIf { it.isNotEmpty() }, d.releaseDate?.let(::orderDate)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPreconDialog(info: Precons.Info, onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val binders = rememberBinders()
    var cards by remember { mutableStateOf<List<Triple<CardRef, Precons.Card, Double?>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var binder by remember { mutableStateOf(BinderChoice()) }
    val paidInput = remember { MoneyInput.of(null) }
    var date by remember { mutableStateOf(info.releaseDate?.takeIf(::isIsoDate) ?: todayIso()) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(info.fileName) {
        try {
            val list = Precons.cards(c.precons.deck(info))
            val found = c.scryfall.collection(list.map { ScryfallApi.idIdentifier(it.identifiers.scryfallId!!) }).associateBy { it.id }
            // Scryfall's euro price is Cardmarket's of today; the finish the deck has it in, else the other.
            cards = list.mapNotNull { card ->
                val ref = found[card.identifiers.scryfallId]?.toRef() ?: return@mapNotNull null
                Triple(ref, card, ref.fallback(card.isFoil) ?: ref.fallback(!card.isFoil))
            }
        } catch (e: Exception) {
            error = "Couldn't get the deck: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    val list = cards
    val paid = paidInput.eur?.takeIf { it >= 0 }
    val count = list.orEmpty().sumOf { it.second.count }
    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text(info.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    list == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Getting the deck's cards…")
                    }
                    else -> {
                        val worth = list.sumOf { (it.third ?: 0.0) * it.second.count }
                        Text("$count cards without the basic lands, worth ${Fmt.money(worth)} today.", style = MaterialTheme.typography.bodyMedium)
                        MoneyField(paidInput, "What you paid", Modifier.fillMaxWidth())
                        OrderDateField(date, { date = it })
                        BinderPicker("Binder", binder, { binder = it }, suggestedName = info.name)
                        if (paid != null && paid > 0 && list.isNotEmpty()) {
                            val top = Precons.split(paid, list).take(3)
                            Text(
                                "Most of it goes to " + top.joinToString(", ") { "${it.card.name} (${Fmt.money(it.price)})" } + ".",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !adding && list != null && list.isNotEmpty() && paid != null && isIsoDate(date) && binder.isValid,
                onClick = {
                    adding = true
                    scope.launch {
                        val added = c.repo.addPrecon(info.name, date.trim(), Precons.split(paid!!, list!!), binder)
                        val where = binder.newName?.trim() ?: binderName(binder.binderId, binders)
                        onAdded("Added $added cards of ${info.name} to $where")
                    }
                },
            ) { Text(if (adding) "Adding…" else "Add $count cards") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !adding) { Text("Cancel") } },
    )
}
