package com.mtgtrader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgtrader.data.CardTrader
import com.mtgtrader.data.CardTraderParse
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CtCondition
import com.mtgtrader.data.CtFilter
import com.mtgtrader.data.Money
import com.mtgtrader.data.levelEur
import com.mtgtrader.data.PriceAlerts
import com.mtgtrader.data.WishPrice
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

/** What a wishlist row says under the card: which printings will do, whether it's ordered and whether you got it. */
fun wishLabel(item: WishlistItem, owned: WishlistOwned?, now: Long = System.currentTimeMillis()): String = listOfNotNull(
    if (item.anyPrinting) "Any printing" else "This printing only",
    // A received card shows when it came (since 1.38) instead of when it was ordered.
    item.receivedAt?.let { "✓ received ${Fmt.shortDate(it, now)}" + (item.receivedFrom?.let { f -> " · $f" } ?: "") }
        ?: item.orderedAt?.let { "📦 ordered ${Fmt.shortDate(it, now)}" },
    when {
        owned == null -> null
        owned.gotSince > 0 -> "✓ got ${owned.gotSince} since adding it"
        owned.owned > 0 -> "you own ${owned.owned}"
        else -> null
    },
).joinToString(" · ")

/**
 * The line above the wishlist: cards wanted, how many of them are ordered (and on their way), and what
 * the rest still cost. Since 1.35.
 */
fun wishTotalLine(rows: List<CollectionRow>, isOrdered: (Long) -> Boolean, priceType: PriceType): String {
    val cards = rows.sumOf { it.item.quantity }
    val (ordered, rest) = rows.partition { isOrdered(it.item.id) }
    val toBuy = rest.sumOf { (it.unitPrice(priceType) ?: 0.0) * it.item.quantity }
    return if (ordered.isEmpty()) "$cards card(s) wanted · ${Fmt.money(toBuy)} to buy them (${priceType.short})"
    else "$cards card(s) wanted · ${ordered.sumOf { it.item.quantity }} ordered · ${Fmt.money(toBuy)} still to buy (${priceType.short})"
}

/**
 * The wishlist row's price line (since 1.37): the alert level (✓ once reached), the cheapest printing's
 * price for the alert ("Low from €3.20 (M21)", for "any printing" cards or ones with a level) and
 * CardTrader's cheapest offer while CardTrader is switched on. Null when there's nothing to say.
 */
fun wishPriceLine(item: WishlistItem, wp: WishPrice?, basis: String, ctFilter: CtFilter?): String? {
    // Received cards are a record now, not wanted: no prices to watch (since 1.38).
    if (item.receivedAt != null) return null
    val ctKey = ctFilter?.key(item.foil)
    val best = PriceAlerts.best(wp, basis, ctKey)?.first
    val level = item.levelEur?.let { "🔔 ${Fmt.money(it)}" + if (best != null && best <= it + 0.000_1) " ✓" else "" }
    // Ordered cards aren't checked any more: only their level shows.
    if (item.orderedAt != null) return level
    val from = if (item.anyPrinting) "from " else ""
    val source = wp?.price?.takeIf { wp.basis == basis && (item.anyPrinting || item.alertBelow != null) }?.let { p ->
        "${PriceAlerts.basisLabel(basis)} $from${Fmt.money(p)}" + (wp.setCode?.takeIf { item.anyPrinting }?.let { " (${it.uppercase()})" } ?: "")
    }
    val ct = wp?.takeIf { ctKey != null && it.ctFilter == ctKey }?.let { r ->
        val name = if (ctFilter!!.zeroOnly) "CT Zero" else "CardTrader"
        when {
            r.ctPrice != null -> "$name $from${Fmt.money(r.ctPrice)}"
            r.ctZeroOver != null -> "$name over ${Fmt.money(r.ctZeroOver)}"
            else -> null
        }
    }
    return listOfNotNull(level, source, ct).joinToString(" · ").ifEmpty { null }
}

/** "2X2 · SP · EN": what CardTrader's offer is. */
fun ctOfferLine(wp: WishPrice): String = listOfNotNull(
    wp.ctSetCode?.uppercase(),
    CtCondition.short(wp.ctCondition).ifEmpty { null },
    wp.ctLanguage?.uppercase(),
).joinToString(" · ")

/**
 * The card window's CardTrader lines: the offer that counts, and with "only CardTrader Zero" the
 * cheapest from any seller, or what a CT Zero copy costs at least when none was among the offers
 * CardTrader shows. Since 1.37.
 */
fun ctDetailLines(wp: WishPrice, zeroOnly: Boolean, anyPrinting: Boolean): List<String> {
    val from = if (anyPrinting) "from " else ""
    val name = if (zeroOnly) "CardTrader Zero" else "CardTrader"
    val main = when {
        wp.ctPrice != null -> "$name: $from${Fmt.money(wp.ctPrice)} (${ctOfferLine(wp)})"
        wp.ctZeroOver != null -> "$name: over ${Fmt.money(wp.ctZeroOver)} (none among the ${CardTraderParse.PAGE} cheapest offers CardTrader shows)"
        else -> "$name: no offer that matches your settings"
    }
    val any = wp.ctAnyPrice?.takeIf { zeroOnly && (wp.ctPrice == null || it < wp.ctPrice - 0.004) }?.let { "Any seller: $from${Fmt.money(it)} (shipped by the seller)" }
    return listOfNotNull(main, any)
}

/** The wishlist as a plain list ("1 Card name" per line) to share; ordered cards are left out, so they aren't bought twice. */
fun wishShareText(rows: List<WishlistRow>): String =
    rows.filter { it.item.orderedAt == null && it.item.receivedAt == null }.joinToString("\n") { "${it.item.quantity} ${it.item.card.name}" }

/** Edits a wishlist entry: how many, whether any printing will do (and the finish otherwise), notes. */
@Composable
fun WishlistDialog(
    row: WishlistRow,
    owned: WishlistOwned?,
    priceType: PriceType,
    alerts: WishAlertInfo,
    onDismiss: () -> Unit,
    onSave: (WishlistItem) -> Unit,
    onDelete: () -> Unit,
) {
    val card = row.item.card
    var item by remember { mutableStateOf(row.item) }
    // The level as typed when it's in the currency shown (since 1.39), else converted.
    var levelText by remember {
        mutableStateOf(row.item.alertBelow?.let { a -> if (row.item.alertCurrency == Money.display.currency.name) "%.2f".format(a) else Money.input(row.item.levelEur) } ?: "")
    }
    val uriHandler = LocalUriHandler.current
    val wp = alerts.price
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardThumb(card.imageUrl, width = 72, enlargeable = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (card.isPending) "Added offline" else card.setName, style = MaterialTheme.typography.bodyMedium)
                        SetLine(card, if (card.isPending) "" else " · ${card.rarity}")
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
                        // Since 1.37: the printing of CardTrader's cheapest offer (or the card's own printing).
                        val blueprint = wp?.ctBlueprintId?.takeIf { alerts.ctFilter != null }
                        if (blueprint != null) {
                            TextButton(onClick = { runCatching { uriHandler.openUri(CardTrader.pageUrl(blueprint)) } }) {
                                Text("Open on CardTrader")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                            }
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
                    // A card added offline has no printing yet to keep to.
                    Switch(checked = item.anyPrinting, onCheckedChange = { item = item.copy(anyPrinting = it) }, enabled = !card.isPending)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ordered, on its way")
                        Text(
                            item.orderedAt?.let { "Ticked ${Fmt.date(it)}" } ?: "Tick it once you've bought it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Unticking and ticking again before saving keeps the date it was ticked on.
                    Checkbox(
                        checked = item.orderedAt != null,
                        onCheckedChange = { on -> item = item.copy(orderedAt = if (on) row.item.orderedAt ?: System.currentTimeMillis() else null) },
                    )
                }
                // Since 1.38: received cards move to the Received section; unticking puts the card back on the wishlist.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Received")
                        Text(
                            item.receivedAt?.let { "Ticked ${Fmt.date(it)}" } ?: "Tick it once it has arrived",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = item.receivedAt != null,
                        onCheckedChange = { on -> item = item.copy(receivedAt = if (on) row.item.receivedAt ?: System.currentTimeMillis() else null) },
                    )
                }
                if (card.hasFoil && card.hasNonFoil) {
                    DropdownSelector(
                        "Finish", item.foil, listOf(false, true),
                        { f -> card.finishName(if (f) Finish.FOIL else Finish.NONFOIL) },
                        { item = item.copy(foil = it) }, Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
                PriceAlertFields(item, levelText, alerts) { text ->
                    levelText = text
                    val typed = Fmt.parseMoney(text)?.takeIf { it > 0 }
                    item = item.copy(alertBelow = typed, alertCurrency = typed?.let { Money.display.currency.name })
                }
                HorizontalDivider()
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

/** What the wishlist card window shows about alerts: the card's latest prices and what they're compared with. Since 1.37. */
data class WishAlertInfo(
    val price: WishPrice? = null,
    /** [PriceAlerts.basis] of the current settings. */
    val basis: String = "cardmarket:low",
    /** CardTrader's filter while it's switched on (with a token), else null. */
    val ctFilter: CtFilter? = null,
    /** CardTrader is switched on, but there's no token yet. */
    val ctNeedsToken: Boolean = false,
)

/** The card window's "Price alert" part: the level, and the prices it's compared with. Since 1.37. */
@Composable
internal fun PriceAlertFields(item: WishlistItem, levelText: String, alerts: WishAlertInfo, onLevel: (String) -> Unit) {
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val wp = alerts.price
    val ctKey = alerts.ctFilter?.key(item.foil)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Price alert", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = levelText,
            onValueChange = onLevel,
            label = { Text("Notify me at or below, ${Money.symbol}") },
            placeholder = { Text("No alert") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Compared with ${PriceAlerts.basisLong(alerts.basis)}" + (if (alerts.ctFilter != null) " and CardTrader" else "") +
                (if (item.anyPrinting) " (the cheapest printing)" else "") + ". Leave it empty for no alert.",
            style = small, color = muted,
        )
        if (item.orderedAt != null && item.alertBelow != null) Text("Ordered cards don't send alerts.", style = small, color = muted)
        val from = if (item.anyPrinting) "from " else ""
        wp?.price?.takeIf { wp.basis == alerts.basis }?.let { p ->
            Text(
                "Now: ${PriceAlerts.basisLabel(alerts.basis)} $from${Fmt.money(p)}" + (wp.setCode?.takeIf { item.anyPrinting }?.let { " (${it.uppercase()})" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (alerts.ctFilter != null && wp != null && wp.ctFilter == ctKey) {
            ctDetailLines(wp, alerts.ctFilter.zeroOnly, item.anyPrinting).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (alerts.ctNeedsToken) Text("CardTrader is switched on, but needs your API token (Settings → Wishlist price alerts).", style = small, color = muted)
        wp?.checkedAt?.takeIf { it > 0 }?.let { Text("Checked ${Fmt.dateTime(it)}", style = small, color = muted) }
    }
}
