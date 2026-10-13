package com.mtgtrader.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CardTraderOrder
import com.mtgtrader.data.Finish
import com.mtgtrader.data.OrderCandidate
import com.mtgtrader.data.OrderFile
import com.mtgtrader.data.OrderFiles
import com.mtgtrader.data.OrderFormat
import com.mtgtrader.data.OrderShop
import com.mtgtrader.data.ScryCard
import com.mtgtrader.data.Spreadsheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** An order file read and its cards found on Scryfall. */
class LoadedOrder(val file: OrderFile, val candidates: List<OrderCandidate>)

/**
 * Reads the order file at [uri] (any shop's, see [OrderFiles]) and finds its cards on Scryfall.
 * Throws [OrderFiles.NotAnOrder] or [Spreadsheet.Unreadable] with a message for the user.
 */
internal suspend fun loadOrder(context: android.content.Context, uri: Uri, onProgress: (Int, Int) -> Unit): LoadedOrder {
    val c = context.container
    val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
        ?: throw Spreadsheet.Unreadable("The file couldn't be opened")
    val file = withContext(Dispatchers.Default) { OrderFiles.read(bytes, displayName(context, uri)) }
    if (file.lines.isEmpty()) throw Spreadsheet.Unreadable("The order has no cards in it")
    onProgress(0, file.lines.size)
    val names: suspend (List<Int>) -> Map<Int, String> = { ids ->
        ids.chunked(500).flatMap { c.db.catalogDao().getMany(it) }.associate { it.idProduct to it.name }
    }
    return LoadedOrder(file, OrderFiles.match(file, c.scryfall, names, onProgress))
}

/** What the file leaves out, to say above the list. */
internal fun orderFileNote(file: OrderFile): String? = when (file.format) {
    OrderFormat.CARDMARKET_MONTHLY ->
        "Cardmarket's monthly file doesn't say foil, language or condition: the cards are taken as non-foil, English and Near Mint. " +
            "A shipment's own file (“ArticlesFromShipment”) has those."
    else -> null
}

/**
 * Import an order (CardTrader's Excel export, or a Cardmarket file): every card is looked up on
 * Scryfall, then shown with a tick box, switches to leave out tokens and basic lands, and a binder
 * to put them in. Since 1.26; Cardmarket since 1.32.
 */
@Composable
fun OrderImportScreen(nav: NavController, uri: Uri) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<OrderCandidate>?>(null) }
    var order by remember { mutableStateOf<OrderFile?>(null) }
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    var skipTokens by remember { mutableStateOf(true) }
    var skipBasics by remember { mutableStateOf(true) }
    var binder by remember { mutableStateOf(BinderChoice()) }
    var choosing by remember { mutableStateOf<OrderCandidate?>(null) }
    var importing by remember { mutableStateOf(false) }
    val owned by remember { c.db.collectionDao().observeOwned() }.collectAsStateWithLifecycle(emptyList())
    val ownedMap = remember(owned) { owned.associate { it.scryfallId to it.qty } }
    val fileName = remember(uri) { displayName(context, uri) }
    val binders = rememberBinders()

    LaunchedEffect(uri) {
        try {
            val loaded = loadOrder(context, uri) { done, total -> progress = done to total }
            val found = loaded.candidates
            order = loaded.file
            candidates = found
            checked = found.filter { it.card != null && !it.token && !it.basicLand }.map { it.index }.toSet()
        } catch (e: CardTraderOrder.NotAnOrder) {
            error = e.message
        } catch (e: OrderFiles.NotAnOrder) {
            error = e.message
        } catch (e: Spreadsheet.Unreadable) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't read the order: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    val list = candidates
    val shop = order?.shop
    val chosen = list.orEmpty().filter { it.index in checked && it.card != null }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (shop == null) "Import an order" else "Import ${shop.label} order")
                        fileName?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (list != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BinderPicker("Put them in", binder, { binder = it }, suggestedName = suggestedBinderName(fileName, shop ?: OrderShop.CARDTRADER))
                        Button(
                            onClick = {
                                importing = true
                                scope.launch {
                                    val added = c.repo.importOrder(chosen.map { it.card!! to it.line }, binder)
                                    val where = binder.newName?.trim() ?: binderName(binder.binderId, binders)
                                    c.repo.reportImport(importSummary(added, where, list, checked))
                                    nav.safePopBackStack()
                                }
                            },
                            enabled = !importing && chosen.isNotEmpty() && binder.isValid,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val n = chosen.sumOf { it.line.quantity }
                            val paid = chosen.sumOf { (it.line.price ?: 0.0) * it.line.quantity }
                            Text(if (importing) "Adding…" else "Add $n card${if (n == 1) "" else "s"} · paid ${Fmt.money(paid)}")
                        }
                    }
                }
            }
        },
    ) { pad ->
        when {
            error != null -> EmptyState("Can't import this file", error!!, Modifier.padding(pad))
            list == null -> Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.padding(8.dp))
                val p = progress
                Text(if (p == null) "Reading the order…" else "Finding the cards on Scryfall… ${p.first} of ${p.second}")
                if (p != null && p.second > 0) LinearProgressIndicator(progress = { p.first.toFloat() / p.second }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            else -> OrderReview(
                candidates = list,
                checked = checked,
                onToggle = { i -> checked = if (i in checked) checked - i else checked + i },
                skipTokens = skipTokens,
                onSkipTokens = { on ->
                    skipTokens = on
                    val tokens = list.filter { it.token && it.card != null }.map { it.index }
                    checked = if (on) checked - tokens.toSet() else checked + tokens
                },
                skipBasics = skipBasics,
                onSkipBasics = { on ->
                    skipBasics = on
                    val basics = list.filter { it.basicLand && it.card != null }.map { it.index }
                    checked = if (on) checked - basics.toSet() else checked + basics
                },
                owned = ownedMap,
                onChoose = { choosing = it },
                modifier = Modifier.padding(pad),
                note = order?.let(::orderFileNote),
            )
        }
    }

    choosing?.let { cand ->
        ChoosePrintingDialog(
            cand,
            onDismiss = { choosing = null },
            onPick = { card ->
                choosing = null
                candidates = list.orEmpty().map {
                    if (it.index == cand.index) it.copy(
                        card = card.toRef(), guessed = false,
                        token = CardTraderOrder.isToken(card), basicLand = CardTraderOrder.isBasicLand(card),
                    ) else it
                }
                checked = checked + cand.index
            },
        )
    }
}

/** "Added 31 cards to The Box · 4 tokens left out · 1 marked signed/altered". */
internal fun importSummary(added: Int, where: String, all: List<OrderCandidate>, checked: Set<Int>): String {
    val left = all.filter { it.index !in checked || it.card == null }
    val tokens = left.filter { it.token }.sumOf { it.line.quantity }
    val basics = left.filter { it.basicLand }.sumOf { it.line.quantity }
    val unmatched = left.filter { it.card == null }.sumOf { it.line.quantity }
    val special = all.filter { it.index in checked && it.card != null && (it.line.signed || it.line.altered) }.sumOf { it.line.quantity }
    return listOfNotNull(
        "Added $added card${if (added == 1) "" else "s"} to $where",
        tokens.takeIf { it > 0 }?.let { "$it token${if (it == 1) "" else "s"} left out" },
        basics.takeIf { it > 0 }?.let { "$it basic land${if (it == 1) "" else "s"} left out" },
        unmatched.takeIf { it > 0 }?.let { "$it not found" },
        special.takeIf { it > 0 }?.let { "$it marked signed/altered" },
    ).joinToString(" · ")
}

/**
 * "CardTrader 23 Jun 2026" from an export named like "cardtrader_order_20260623hxrahx.xls"; else today's
 * date (Cardmarket's file names carry a shipment number, no date).
 */
internal fun suggestedBinderName(fileName: String?, shop: OrderShop = OrderShop.CARDTRADER): String {
    val stamp = fileName?.takeIf { shop == OrderShop.CARDTRADER }?.let { Regex("""(20\d{2})(\d{2})(\d{2})""").find(it) }?.let { m ->
        runCatching { SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { isLenient = false }.parse(m.value) }.getOrNull()
    } ?: Date()
    return shop.label + " " + SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(stamp)
}

internal fun displayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
        if (cur.moveToFirst()) cur.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment

/** The review list: the two switches, then every order line with its tick box. Split out for the screenshot tests. */
@Composable
fun OrderReview(
    candidates: List<OrderCandidate>,
    checked: Set<Int>,
    onToggle: (Int) -> Unit,
    skipTokens: Boolean,
    onSkipTokens: (Boolean) -> Unit,
    skipBasics: Boolean,
    onSkipBasics: (Boolean) -> Unit,
    owned: Map<String, Int>,
    onChoose: (OrderCandidate) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val tokens = candidates.filter { it.token }.sumOf { it.line.quantity }
    val basics = candidates.filter { it.basicLand }.sumOf { it.line.quantity }
    val missing = candidates.filter { it.card == null }
    val toCheck = candidates.count { it.card != null && it.guessed }
    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "summary") {
            Text(
                "${candidates.sumOf { it.line.quantity }} cards in the order. Untick any you don't want to add." +
                    if (toCheck > 0) (if (toCheck == 1) " 1 card marked “check the printing” was" else " $toCheck cards marked “check the printing” were") + " found by name only." else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        item(key = "switches") {
            Column {
                SwitchRow("Leave out tokens ($tokens)", skipTokens, onSkipTokens)
                SwitchRow("Leave out basic lands ($basics)", skipBasics, onSkipBasics)
            }
        }
        if (missing.isNotEmpty()) {
            item(key = "missingHeader") {
                Text("Not found on Scryfall (${missing.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            }
            items(missing, key = { "m${it.index}" }) { cand -> OrderRow(cand, false, null, {}, onChoose) }
            item(key = "foundHeader") { Text("Found", style = MaterialTheme.typography.titleSmall) }
        }
        items(candidates.filter { it.card != null }, key = { "c${it.index}" }) { cand ->
            OrderRow(cand, cand.index in checked, owned[cand.card!!.scryfallId], { onToggle(cand.index) }, onChoose)
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun OrderRow(cand: OrderCandidate, checked: Boolean, owned: Int?, onToggle: () -> Unit, onChoose: (OrderCandidate) -> Unit) {
    val line = cand.line
    val card = cand.card
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(enabled = card != null, onClick = onToggle),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (card != null) Checkbox(checked = checked, onCheckedChange = { onToggle() })
            CardThumb(card?.imageUrl, width = 38, enlargeable = card != null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (if (line.quantity > 1) "${line.quantity}× " else "") + (card?.displayName ?: line.label),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (card != null) SetLine(card) else Text(orderLineWhere(line), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (card != null && line.foil) FinishTag(card, card.resolveFinish(Finish.FOIL))
                    OrderVersionTags(line)
                    if (cand.token) Tag("Token")
                    if (cand.basicLand) Tag("Basic land")
                    if (line.signed) Tag("Signed")
                    if (line.altered) Tag("Altered")
                    if (line.playset) Tag("Playset")
                }
                if (owned != null && owned > 0) Text("You own $owned already", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (card == null || cand.guessed) {
                    TextButton(onClick = { onChoose(cand) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(if (card == null) "Find the card" else "Check the printing", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            line.price?.let { Text(Fmt.money(it), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** "Zendikar Rising (ZNR) #180", with whatever of it the file has. */
internal fun orderLineWhere(line: com.mtgtrader.data.OrderLine): String = listOfNotNull(
    line.setName.takeIf { it.isNotEmpty() },
    line.setCode.takeIf { it.isNotEmpty() }?.let { "(${it.uppercase()})" },
    line.number.takeIf { it.isNotEmpty() }?.let { "#$it" },
    line.productId?.takeIf { line.setName.isEmpty() && line.name.isNotEmpty() }?.let { "Cardmarket product $it" },
).joinToString(" ")

/** Condition and language as the order has them: nothing for a file that doesn't say, "Language ?" for a number the app doesn't know. */
@Composable
internal fun OrderVersionTags(line: com.mtgtrader.data.OrderLine) {
    if (!line.versionKnown) return
    Tag(line.condition)
    if (!line.languageKnown) Tag("Language ?") else if (line.language != "EN") Tag(line.language)
}

/** Every paper printing of the card's name, to pick the one that was bought. */
@Composable
internal fun ChoosePrintingDialog(cand: OrderCandidate, onDismiss: () -> Unit, onPick: (ScryCard) -> Unit) {
    val c = LocalContext.current.container
    val name = CardTraderOrder.cleanName(cand.line.name).substringBefore(" // ").substringBefore(" / ")
    val prints by produceState<List<ScryCard>?>(null, name) {
        value = if (name.isEmpty()) emptyList() else runCatching { c.scryfall.prints(name) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name.ifEmpty { cand.line.label }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val p = prints
            when {
                p == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                name.isEmpty() -> Text("Neither Scryfall nor the app's Cardmarket list knows product ${cand.line.productId}. It can't be imported.")
                p.isEmpty() -> Text("Scryfall has no card called “$name”. It can't be imported.")
                else -> Column {
                    Text("Bought as ${orderLineWhere(cand.line)}", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(p, key = { it.id }) { card ->
                            Row(Modifier.fillMaxWidth().clickable { onPick(card) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                CardThumb(card.image, width = 34)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(card.setName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${card.set.uppercase()} #${card.collectorNumber}" + (card.releasedAt?.let { " · ${it.take(4)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
