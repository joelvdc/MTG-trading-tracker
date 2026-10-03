package com.mtgtrader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.Finish
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.WishlistItem
import com.mtgtrader.data.WishlistOwned
import com.mtgtrader.data.WishlistRow

/** The wishlist shows with the collection's own card views; condition is left empty so none is shown. */
fun WishlistRow.asCollectionRow() = CollectionRow(
    CollectionItem(id = item.id, card = item.card, foil = item.foil, condition = "", quantity = item.quantity, addedAt = item.addedAt),
    price,
)

/** What a wishlist row says under the card: which printings will do and whether you got it. */
fun wishLabel(item: WishlistItem, owned: WishlistOwned?): String = listOfNotNull(
    if (item.anyPrinting) "Any printing" else "This printing only",
    when {
        owned == null -> null
        owned.gotSince > 0 -> "✓ got ${owned.gotSince} since adding it"
        owned.owned > 0 -> "you own ${owned.owned}"
        else -> null
    },
).joinToString(" · ")

/** Edits a wishlist entry: how many, whether any printing will do (and the finish otherwise), notes. */
@Composable
fun WishlistDialog(
    row: WishlistRow,
    owned: WishlistOwned?,
    priceType: PriceType,
    onDismiss: () -> Unit,
    onSave: (WishlistItem) -> Unit,
    onDelete: () -> Unit,
) {
    val card = row.item.card
    var item by remember { mutableStateOf(row.item) }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardThumb(card.imageUrl, width = 72, enlargeable = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.setName, style = MaterialTheme.typography.bodyMedium)
                        SetLine(card, " · ${card.rarity}")
                        owned?.let {
                            Text(
                                if (it.owned > 0) "You own ${it.owned}" + (if (it.gotSince > 0) " (${it.gotSince} got since adding it)" else "") else "Not in your collection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { runCatching { uriHandler.openUri(card.cardmarketUrl) } }) {
                            Text("Open on Cardmarket")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Copies wanted", Modifier.weight(1f))
                    QuantityStepper(item.quantity, { item = item.copy(quantity = it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Any printing will do")
                        Text(
                            if (item.anyPrinting) "Prices shown are for this printing" else "Only this printing counts as got",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = item.anyPrinting, onCheckedChange = { item = item.copy(anyPrinting = it) })
                }
                if (card.hasFoil && card.hasNonFoil) {
                    DropdownSelector(
                        "Finish", item.foil, listOf(false, true),
                        { f -> card.finishName(if (f) Finish.FOIL else Finish.NONFOIL) },
                        { item = item.copy(foil = it) }, Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = item.notes ?: "",
                    onValueChange = { item = item.copy(notes = it.ifBlank { null }) },
                    label = { Text("Notes (for which deck, max price…)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                DeckUsageLine(card.name)
                HorizontalDivider()
                PriceTable(
                    row.price?.toSet(item.foil) ?: PriceSet(trend = card.fallback(item.foil)),
                    priceType,
                    if (item.foil) "Cardmarket prices (foil)" else "Cardmarket prices",
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(item) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
