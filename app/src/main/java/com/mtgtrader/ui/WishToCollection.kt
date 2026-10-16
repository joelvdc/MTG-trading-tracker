package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgtrader.container
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CONDITIONS
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.Finish
import com.mtgtrader.data.LANGUAGES
import com.mtgtrader.data.Money
import com.mtgtrader.data.WishOrders
import com.mtgtrader.data.WishlistItem

// A received wishlist card into the collection, and an imported order against the wishlist. Since 1.39.

/** What "Add to collection" fills in for a wishlist card, before anything is changed. */
data class WishToCollection(
    val card: CardRef?,
    val finish: Finish,
    val condition: String = "NM",
    val language: String = "EN",
    val quantity: Int,
    val binder: BinderChoice = BinderChoice(),
    val price: Double? = null,
) {
    companion object {
        /** From the wishlist card: its printing (none for a card added offline), finish and the copies wanted. */
        fun of(w: WishlistItem) = WishToCollection(
            card = w.card.takeIf { !it.isPending },
            finish = if (w.card.isPending) Finish.NONFOIL else w.card.resolveFinish(if (w.foil) Finish.FOIL else Finish.NONFOIL),
            quantity = w.quantity,
        )
    }
}

/**
 * The stack in [collection] the copies would join: the same printing, finish, condition, language and
 * binder (and not signed or altered); null if they'd make a new one.
 */
fun joiningStack(collection: List<CollectionItem>, v: WishToCollection, binderId: Long?): CollectionItem? {
    val card = v.card ?: return null
    binderId ?: return null
    return collection.firstOrNull {
        it.card.scryfallId == card.scryfallId && it.finish == v.finish && it.condition == v.condition && it.language == v.language &&
            it.binderId == binderId && !it.marks.any
    }
}

/**
 * Moving a received wishlist card into the collection: the printing (to confirm for "any printing" cards,
 * to pick for cards added offline), finish, condition, language, copies, binder and what was paid. When
 * the card's copies may be in the collection already ([gotSince] > 0, e.g. from an order imported before
 * 1.39), it warns and offers to just take the card off the wishlist.
 */
@Composable
fun AddWishToCollectionDialog(
    item: WishlistItem,
    gotSince: Int,
    collection: List<CollectionItem>,
    onDismiss: () -> Unit,
    onTakeOff: () -> Unit,
    onAdd: (WishToCollection) -> Unit,
) {
    val binders = rememberBinders()
    var v by remember { mutableStateOf(WishToCollection.of(item)) }
    val priceInput = remember { MoneyInput.of(null) }
    var picking by remember { mutableStateOf(false) }
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${item.card.displayName} to the collection", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (gotSince > 0) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "You've got $gotSince cop${if (gotSince == 1) "y" else "ies"} of this card since adding it to the wishlist, " +
                                    "e.g. from an order import. If that's this one, it's in the collection already.",
                                style = small,
                            )
                            TextButton(onClick = onTakeOff) { Text("Just take it off the wishlist") }
                        }
                    }
                }
                // The printing.
                val card = v.card
                Column {
                    Text("Printing", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(card?.let { "${it.setLabel} · ${it.setName}" } ?: "Choose the printing…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        when {
                            card == null -> "Added offline: pick the printing you got (needs internet)."
                            item.anyPrinting -> "Any printing was wanted: check this is the one you got."
                            else -> "The printing on the wishlist."
                        },
                        style = small, color = muted,
                    )
                }
                card?.takeIf { it.finishes.size > 1 }?.let { c ->
                    DropdownSelector("Finish", v.finish, c.finishes, { c.finishName(it) }, { v = v.copy(finish = it) }, Modifier.fillMaxWidth())
                }
                DropdownSelector("Condition", v.condition, CONDITIONS.map { it.first }, { k -> CONDITIONS.first { it.first == k }.let { "${it.second} (${it.first})" } }, { v = v.copy(condition = it) }, Modifier.fillMaxWidth())
                DropdownSelector("Language", v.language, LANGUAGES.map { it.first }, { k -> LANGUAGES.firstOrNull { it.first == k }?.second ?: k }, { v = v.copy(language = it) }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Copies", Modifier.weight(1f))
                    QuantityStepper(v.quantity, { v = v.copy(quantity = it) })
                }
                BinderPicker("Put it in", v.binder, { v = v.copy(binder = it) })
                MoneyField(priceInput, "Paid per copy (optional)", Modifier.fillMaxWidth()) { v = v.copy(price = priceInput.eur?.takeIf { p -> p >= 0 }) }
                joiningStack(collection, v, v.binder.takeIf { it.newName == null }?.binderId)?.let { s ->
                    Text(
                        "You have ${s.quantity} of this version in ${binderName(s.binderId, binders)} already: these join that stack" +
                            if (s.purchasePrice != null || v.price != null) ", and its purchase price becomes the average of all copies." else ".",
                        style = small, color = muted,
                    )
                }
                Text("The card then leaves the wishlist.", style = small, color = muted)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(v) }, enabled = v.card != null && v.binder.isValid) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        PrintingListDialog(item.card.name, onDismiss = { picking = false }) { picked ->
            picking = false
            v = v.copy(card = picked, finish = picked.resolveFinish(v.finish))
        }
    }
}

/** Every paper printing of [name], to pick one (looked up on Scryfall, so it needs internet). */
@Composable
fun PrintingListDialog(name: String, onDismiss: () -> Unit, onPick: (CardRef) -> Unit) {
    val c = LocalContext.current.container
    val prints by produceState<List<CardRef>?>(null, name) {
        value = runCatching { c.scryfall.prints(name).map { it.toRef() } }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which printing?") },
        text = {
            val list = prints
            when {
                list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(8.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Looking up the printings…")
                }
                list.isEmpty() -> Text("No printings found. Are you online?")
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.scryfallId }) { p ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardThumb(p.imageUrl, width = 36)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.setName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.setLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Fmt.money(p.fallbackEur ?: p.fallbackEurFoil), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Where a wishlist card is: "To buy", "Ordered" or "Received". */
fun wishStage(w: WishlistItem) = when {
    w.receivedAt != null -> "Received"
    w.orderedAt != null -> "Ordered"
    else -> "To buy"
}

/**
 * After an order went into the collection: the wishlist cards it covers, each to be marked as received
 * (the default: it stays as a record, linked to the order), taken off the wishlist, or left as it is.
 */
@Composable
fun OrderWishlistDialog(matches: List<WishOrders.Match>, label: String, onDone: (List<Pair<WishOrders.Match, WishOrders.Choice>>) -> Unit) {
    val choice = remember { mutableStateMapOf<Long, WishOrders.Choice>().apply { matches.forEach { put(it.item.id, WishOrders.Choice.RECEIVED) } } }
    fun result() = matches.map { it to (choice[it.item.id] ?: WishOrders.Choice.RECEIVED) }
    AlertDialog(
        onDismissRequest = { onDone(matches.map { it to WishOrders.Choice.LEAVE }) },
        title = { Text("${matches.size} card${if (matches.size == 1) " is" else "s are"} on your wishlist") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "These cards from the $label went into the collection. Marked as received, they stay on the wishlist as a record, " +
                        "linked to the order, and won't be added to the collection again.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("All:", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { matches.forEach { choice[it.item.id] = WishOrders.Choice.RECEIVED } }) { Text("Received") }
                    TextButton(onClick = { matches.forEach { choice[it.item.id] = WishOrders.Choice.REMOVE } }) { Text("Take off") }
                }
                HorizontalDivider()
                matches.forEach { m ->
                    val w = m.item
                    Column {
                        Text("${m.quantity}× ${w.card.displayName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            wishStage(w) + (if (m.quantity < w.quantity) " · ${w.quantity} wanted, the rest stays" else "") +
                                if (w.anyPrinting) " · any printing" else " · this printing",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (opt in listOf(WishOrders.Choice.RECEIVED, WishOrders.Choice.REMOVE, WishOrders.Choice.LEAVE)) {
                                FilterChip(
                                    selected = choice[w.id] == opt,
                                    onClick = { choice[w.id] = opt },
                                    label = { Text(if (opt == WishOrders.Choice.REMOVE) "Take off" else if (opt == WishOrders.Choice.RECEIVED) "Received" else "Leave") },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(result()) }) { Text("Done") } },
    )
}
