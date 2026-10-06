package com.mtgtrader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CONDITIONS
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.LANGUAGES
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A collection stack's card page: the card and its price, quantity, finish, binder, condition,
 * language, purchase price and notes in a compact form, the decks it's in, the other copies of the
 * card you own (tap one to open it), and Cardmarket's prices. Also opened from the value screen.
 * [scope] must outlive the page (the screen's), so saving isn't cancelled when it closes.
 * Since 1.21 (before, the shared [EditCardDialog]).
 */
@Composable
fun CollectionCardDialog(row: CollectionRow, nav: NavController, snackbar: SnackbarHostState, scope: CoroutineScope, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val binders = rememberBinders()
    val rows by remember(row.item.card.name) { c.db.collectionDao().observeByName(row.item.card.name) }.collectAsStateWithLifecycle(listOf(row))
    var currentId by remember { mutableLongStateOf(row.item.id) }
    val current = rows.firstOrNull { it.item.id == currentId } ?: row.takeIf { it.item.id == currentId } ?: rows.firstOrNull() ?: row
    val item = current.item
    fun initialFor(r: CollectionRow) = r.item.let { EditValues(it.quantity, it.finish, it.condition, it.language, null, it.binderId, it.quantity, it.notes, it.purchasePrice) }
    var v by remember(item.id) { mutableStateOf(initialFor(current)) }
    var paidText by remember(item.id) { mutableStateOf(item.purchasePrice?.let { "%.2f".format(it) } ?: "") }
    var pricesOpen by remember { mutableStateOf(false) }
    var switchTo by remember { mutableStateOf<CollectionRow?>(null) }
    val uriHandler = LocalUriHandler.current
    val card = item.card
    val finishOptions = remember(card, item.finish) { (card.finishes + item.finish).distinct() }
    val prices: (Boolean) -> PriceSet? = { f -> current.price?.toSet(f) ?: PriceSet(trend = card.fallback(f)) }
    // The same printing first (other binders, conditions, finishes), then the rest by value.
    val others = rows.filter { it.item.id != item.id }.sortedWith(
        compareBy<CollectionRow> { if (it.item.card.scryfallId == card.scryfallId) 0 else 1 }
            .thenByDescending { it.unitPrice(priceType) ?: 0.0 }
            .thenBy { it.item.card.setCode },
    )
    val othersOpen by c.settings.cardOthersOpen.collectAsStateWithLifecycle()
    // In the trade binder, another copy can take this one's place (since 1.22).
    var tradeId by remember { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(binders) { tradeId = c.tradeBinder.binder()?.id }
    val inTrade = tradeId != null && item.binderId == tradeId
    var swapping by remember { mutableStateOf<CollectionRow?>(null) }
    val dirty = v != initialFor(current)

    fun save(after: () -> Unit = {}) {
        val values = v
        scope.launch {
            c.repo.saveCollectionEdit(
                item.copy(
                    quantity = values.quantity, foil = values.finish.foil, etched = values.finish.etched, condition = values.condition,
                    language = values.language, notes = values.notes?.trim()?.ifEmpty { null }, purchasePrice = values.purchasePrice,
                ),
                values.binderId,
                values.move,
            )
            after()
        }
    }
    fun open(other: CollectionRow) {
        if (dirty) switchTo = other else currentId = other.item.id
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp),
        ) {
            Column(Modifier.padding(top = 16.dp)) {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // The card: picture, name, printing, price, and its two actions.
                    Row {
                        CardThumb(card.imageUrl, width = 76, enlargeable = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(card.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            SetLine(card, " · ${card.rarity}")
                            Text(card.setName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Fmt.money(prices(v.finish.foil)?.best(priceType)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                TrendBadge(prices(v.finish.foil)?.trendChange)
                                if (v.quantity > 1) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("· ${Fmt.money(prices(v.finish.foil)?.best(priceType)?.times(v.quantity))} for ${v.quantity}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("Other printing", Icons.Default.SwapHoriz) {
                            onDismiss()
                            nav.openSearch(CardTarget.ReplaceCollectionItem(item.id), card.name)
                        }
                        SmallAction("Cardmarket", Icons.AutoMirrored.Filled.OpenInNew) { runCatching { uriHandler.openUri(card.cardmarketUrl) } }
                    }

                    // What you have: compact fields, two to a row.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactBox("Quantity", Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StepButton(Icons.Default.Remove, "Less", v.quantity > 1) { v = v.copy(quantity = v.quantity - 1, move = minOf(v.move, v.quantity - 1).coerceAtLeast(1)) }
                                Text("${v.quantity}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                StepButton(Icons.Default.Add, "More", true) { v = v.copy(quantity = v.quantity + 1) }
                            }
                        }
                        if (finishOptions.size > 1) {
                            CompactDropdown("Finish", v.finish, finishOptions, card::finishName, { v = v.copy(finish = it) }, Modifier.weight(1f))
                        } else {
                            CompactBox("Finish", Modifier.weight(1f)) {
                                Text(card.finishName(v.finish), color = if (v.finish.foil) FoilColor else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    CompactDropdown(
                        "Binder", v.binderId, listOf(Binder.UNSORTED) + binders.map { it.id }, { binderName(it, binders) },
                        { v = v.copy(binderId = it, move = v.quantity) }, Modifier.fillMaxWidth(),
                    )
                    if (v.binderId != item.binderId && v.quantity > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Copies to move", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            StepButton(Icons.Default.Remove, "Less", v.move > 1) { v = v.copy(move = v.move - 1) }
                            Text("${v.move.coerceIn(1, v.quantity)}", Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            StepButton(Icons.Default.Add, "More", v.move < v.quantity) { v = v.copy(move = v.move + 1) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactDropdown(
                            "Condition", v.condition, CONDITIONS.map { it.first }, { c -> CONDITIONS.first { it.first == c }.second },
                            { v = v.copy(condition = it) }, Modifier.weight(1f), menuLabel = { c -> "$c · " + CONDITIONS.first { it.first == c }.second },
                        )
                        CompactDropdown(
                            "Language", v.language, LANGUAGES.map { it.first }, { l -> LANGUAGES.firstOrNull { it.first == l }?.second ?: l },
                            { v = v.copy(language = it) }, Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactTextField(
                            "Paid per copy", paidText, {
                                paidText = it
                                v = v.copy(purchasePrice = Fmt.parseMoney(it))
                            },
                            Modifier.weight(0.38f), placeholder = "€", keyboardType = KeyboardType.Decimal,
                        )
                        CompactTextField("Notes", v.notes ?: "", { v = v.copy(notes = it.ifBlank { null }) }, Modifier.weight(0.62f), placeholder = "Where from, condition…", singleLine = false)
                    }
                    val paid = v.purchasePrice
                    val now = prices(v.finish.foil)?.get(priceType)
                    if (paid != null && paid > 0 && now != null) {
                        val diff = (now - paid) * v.quantity
                        Text(
                            "Paid ${Fmt.money(paid * v.quantity)} · now ${Fmt.money(now * v.quantity)} · " +
                                (if (diff >= 0) "+" else "−") + Fmt.money(kotlin.math.abs(diff)) + " (%+.0f%%)".format((now - paid) / paid * 100),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (diff >= 0) TrendColors.up else TrendColors.down,
                        )
                    }
                    DeckUsageLine(card.name)

                    // Every other copy of this card you own: other printings, finishes, conditions and binders.
                    if (others.isNotEmpty()) {
                        HorizontalDivider()
                        val printings = (others.map { it.item.card.scryfallId } - card.scryfallId).toSet().size
                        Row(
                            Modifier.fillMaxWidth().clickable { c.settings.setCardOthersOpen(!othersOpen) }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("You also own ${others.sumOf { it.item.quantity }} more", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOfNotNull(
                                        printings.takeIf { it > 0 }?.let { if (it == 1) "1 other printing" else "$it other printings" },
                                        if (inTrade) "Swap in puts it in the trade binder instead" else "tap a card to open it",
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(if (othersOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (othersOpen) "Show fewer" else "Show all")
                        }
                        val shown = if (othersOpen) others else others.take(OTHERS_FOLDED)
                        Column {
                            shown.forEach { o ->
                                OtherCopyRow(
                                    o, priceType, binderName(o.item.binderId, binders), sameCard = o.item.card.scryfallId == card.scryfallId,
                                    onSwap = if (inTrade && o.item.binderId != tradeId) ({ swapping = o }) else null,
                                ) { open(o) }
                            }
                        }
                        if (others.size > OTHERS_FOLDED) {
                            TextButton(onClick = { c.settings.setCardOthersOpen(!othersOpen) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text(if (othersOpen) "Show fewer" else "Show all ${others.size}")
                            }
                        }
                    }

                    // Cardmarket's numbers, folded away.
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { pricesOpen = !pricesOpen }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val p = prices(v.finish.foil)
                        Column(Modifier.weight(1f)) {
                            Text("Cardmarket prices", style = MaterialTheme.typography.titleSmall)
                            if (!pricesOpen && p != null) {
                                Text(
                                    "Trend ${Fmt.money(p.trend)} · Avg ${Fmt.money(p.avg)} · 30 days ${Fmt.money(p.avg30)} · Low ${Fmt.money(p.low)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(if (pricesOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (pricesOpen) "Fewer prices" else "All prices")
                    }
                    if (pricesOpen) PriceTable(prices(v.finish.foil), priceType, if (v.finish.foil) card.finishName(v.finish) else "Non-foil")
                    Spacer(Modifier.width(1.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        onDismiss()
                        scope.launch {
                            c.repo.deleteCollectionItem(item.id)
                            if (snackbar.showUndo("${card.displayName} removed")) c.repo.restoreCollectionItem(item)
                        }
                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onDismiss(); save() }, enabled = dirty) { Text("Save") }
                }
            }
        }
    }

    swapping?.let { other ->
        val max = minOf(item.quantity, other.item.quantity)
        var n by remember(other.item.id) { mutableStateOf(max) }
        AlertDialog(
            onDismissRequest = { swapping = null },
            title = { Text("Swap the copy in the trade binder?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${other.item.card.setLabel} (${binderName(other.item.binderId, binders)}) goes into the trade binder; " +
                            "${card.setLabel} goes to Unsorted. The next trade binder update keeps this choice.",
                    )
                    if (max > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Copies", Modifier.weight(1f))
                            StepButton(Icons.Default.Remove, "Less", n > 1) { n-- }
                            Text("$n", Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            StepButton(Icons.Default.Add, "More", n < max) { n++ }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    swapping = null
                    onDismiss()
                    val outgoing = item
                    scope.launch {
                        c.tradeBinder.swapIn(outgoing, other.item, n)
                        snackbar.showSnackbar("${other.item.card.setLabel} is in the trade binder now; ${outgoing.card.setLabel} went to Unsorted")
                    }
                }) { Text("Swap") }
            },
            dismissButton = { TextButton(onClick = { swapping = null }) { Text("Cancel") } },
        )
    }

    switchTo?.let { other ->
        AlertDialog(
            onDismissRequest = { switchTo = null },
            title = { Text("Save your changes?") },
            text = { Text("You changed this copy of ${card.displayName}. Save before opening the other one?") },
            confirmButton = {
                TextButton(onClick = {
                    switchTo = null
                    save { currentId = other.item.id }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { switchTo = null; currentId = other.item.id }) { Text("Discard") }
                    TextButton(onClick = { switchTo = null }) { Text("Cancel") }
                }
            },
        )
    }
}

/** Other copies shown before "Show all". */
private const val OTHERS_FOLDED = 3

/** One other copy of the card: printing, finish, condition, language, binder, how many and their value. */
@Composable
private fun OtherCopyRow(row: CollectionRow, priceType: PriceType, binder: String, sameCard: Boolean, onSwap: (() -> Unit)? = null, onClick: () -> Unit) {
    val item = row.item
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        CardThumb(item.card.imageUrl, width = 30)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                SetLine(item.card)
                FinishTag(item.card, item.finish)
                Tag(item.condition)
                if (item.language != "EN") Tag(item.language)
            }
            Text(
                if (sameCard) "$binder · same printing" else binder,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${item.quantity}×", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(Fmt.money(row.unitPrice(priceType)), style = MaterialTheme.typography.bodySmall)
        }
        if (onSwap != null) {
            Spacer(Modifier.width(6.dp))
            SmallAction("Swap in", Icons.Default.SwapHoriz, onSwap)
        }
    }
}

@Composable
private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) { Icon(icon, description, Modifier.size(18.dp)) }
}

/** A small outlined field: its label on top, the value below. */
@Composable
private fun CompactBox(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .heightIn(min = 52.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
        trailing?.invoke()
    }
}

@Composable
private fun <T> CompactDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    menuLabel: (T) -> String = optionLabel,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        CompactBox(label, Modifier.fillMaxWidth(), onClick = { open = true }, trailing = { Icon(Icons.Default.ArrowDropDown, null) }) {
            Text(optionLabel(selected), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(menuLabel(o), fontWeight = if (o == selected) FontWeight.Bold else null) },
                    onClick = { open = false; onSelect(o) },
                )
            }
        }
    }
}

@Composable
private fun CompactTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    CompactBox(label, modifier) {
        Box {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            )
        }
    }
}

