package com.mtgtrader.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.CardTraderOrder
import com.mtgtrader.data.Finish
import com.mtgtrader.data.OrderFiles
import com.mtgtrader.data.OrderShop
import com.mtgtrader.data.Orders
import com.mtgtrader.data.PackageCheck
import com.mtgtrader.data.PackageCheck.Status
import com.mtgtrader.data.ScannedCard
import com.mtgtrader.data.Spreadsheet
import kotlinx.coroutines.launch

/**
 * The order read for the last package check, kept while the app runs: going to the scanner and
 * coming back shouldn't look every card up on Scryfall again.
 */
private object CheckedOrder {
    var uri: String? = null
    var order: LoadedOrder? = null
}

/**
 * Checks the scanned cards against an order file (CardTrader's or Cardmarket's), started from the
 * scanned cards list: what arrived, what came in another language or printing, what's missing and
 * what wasn't ordered. Scanning more updates the check; then the received cards go to a binder with
 * the order's price, condition and finish. Since 1.32.
 */
@Composable
fun PackageCheckScreen(nav: NavController, uri: Uri) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var order by remember { mutableStateOf(CheckedOrder.order.takeIf { CheckedOrder.uri == uri.toString() }) }
    var accepted by rememberSaveable { mutableStateOf(intArrayOf()) }
    var showReceived by rememberSaveable { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val scanRows by remember { c.db.scanDao().observeAll() }.collectAsStateWithLifecycle(null)
    val fileName = remember(uri) { displayName(context, uri) }
    val binders = rememberBinders()

    LaunchedEffect(uri) {
        if (order != null) return@LaunchedEffect
        try {
            val loaded = loadOrder(context, uri) { d, t -> progress = d to t }
            CheckedOrder.uri = uri.toString()
            CheckedOrder.order = loaded
            order = loaded
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

    val loaded = order
    val scans = scanRows?.map { it.item }
    val result = remember(loaded, scans, accepted) {
        if (loaded == null || scans == null) null else PackageCheck.check(loaded.candidates, scans, accepted.toSet())
    }
    val shop = loaded?.file?.shop ?: OrderShop.CARDTRADER
    val orderName = "${shop.label} order" + (loaded?.file?.lines?.firstNotNullOfOrNull { it.order }?.let { " $it" } ?: fileName?.let { " ($it)" }.orEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Check the package")
                        fileName?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (result != null) IconButton(onClick = {
                        clipboard.setText(AnnotatedString(PackageCheck.report(result, orderName)))
                        scope.launch { snackbar.showSnackbar("Problem report copied") }
                    }) { Icon(Icons.Default.ContentCopy, "Copy the problem report") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (result != null) {
                val n = PackageCheck.additions(result, extras = true).sumOf { it.quantity }
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { nav.openScanner(CardTarget.Scans) }) {
                            Icon(Icons.Default.CameraAlt, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Scan more")
                        }
                        Button(onClick = { confirming = true }, enabled = n > 0 && !adding, modifier = Modifier.weight(1f)) {
                            Text(if (adding) "Adding…" else "Add to collection…")
                        }
                    }
                }
            }
        },
    ) { pad ->
        when {
            error != null -> EmptyState("Can't check against this file", error!!, Modifier.padding(pad))
            result == null -> Column(
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
            else -> PackageCheckList(
                result = result,
                scanned = scans.orEmpty().sumOf { it.quantity },
                showReceived = showReceived,
                onShowReceived = { showReceived = it },
                onAccept = { i -> accepted = (accepted.toSet() + i).toIntArray() },
                onUndo = { i -> accepted = (accepted.toSet() - i).toIntArray() },
                note = loaded?.file?.let(::orderFileNote),
                modifier = Modifier.padding(pad),
            )
        }
    }

    if (confirming && result != null) {
        AddCheckedDialog(
            result = result,
            suggestedBinder = suggestedBinderName(fileName, shop),
            askDate = loaded?.file?.lines?.none { it.date != null } == true,
            onDismiss = { confirming = false },
            onConfirm = { binder, extras, date ->
                confirming = false
                adding = true
                scope.launch {
                    val additions = PackageCheck.additions(result, extras)
                    val used = HashMap<Long, Int>()
                    for (l in result.lines) for (p in l.parts) p.scan?.let { used.merge(it.id, p.quantity, Int::plus) }
                    if (extras) for ((scan, n) in result.extras) used.merge(scan.id, n, Int::plus)
                    val added = c.repo.addCheckedPackage(additions, binder, used)
                    loaded?.let { o -> c.repo.recordOrders(Orders.fromFile(o.file, date) { i, _ -> o.candidates.firstOrNull { it.index == i }?.card }) }
                    val where = binder.newName?.trim() ?: binderName(binder.binderId, binders)
                    val missing = result.count(Status.MISSING)
                    adding = false
                    done = "Added $added card${if (added == 1) "" else "s"} to $where, with what the order says was paid." +
                        if (missing > 0) " $missing ordered card${if (missing == 1) " is" else "s are"} missing." else ""
                }
            },
        )
    }

    done?.let { message ->
        AlertDialog(
            onDismissRequest = { done = null; nav.safePopBackStack() },
            title = { Text("Package added") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { done = null; nav.safePopBackStack() }) { Text("OK") } },
            dismissButton = {
                if (result != null && result.problems.isNotEmpty()) TextButton(onClick = {
                    clipboard.setText(AnnotatedString(PackageCheck.report(result, orderName)))
                    scope.launch { snackbar.showSnackbar("Problem report copied") }
                }) { Text("Copy the problem report") }
            },
        )
    }
}

@Composable
private fun AddCheckedDialog(
    result: PackageCheck.Result, suggestedBinder: String, askDate: Boolean, onDismiss: () -> Unit, onConfirm: (BinderChoice, extras: Boolean, date: String) -> Unit,
) {
    var binder by remember { mutableStateOf(BinderChoice()) }
    var extras by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf(todayIso()) }
    val n = PackageCheck.additions(result, extras).sumOf { it.quantity }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $n card${if (n == 1) "" else "s"} to the collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Cards that match the order get its price paid, condition and finish. A card in another language or printing " +
                        "than ordered is added as scanned. They leave the scanned list.",
                    style = MaterialTheme.typography.bodySmall,
                )
                BinderPicker("Binder", binder, { binder = it }, suggestedName = suggestedBinder)
                if (askDate) OrderDateField(date, { date = it })
                if (result.extraCount > 0) {
                    CheckRow("Also the ${result.extraCount} scanned card${if (result.extraCount == 1) "" else "s"} that aren't in the order", extras) { extras = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(binder, extras, date.trim()) }, enabled = n > 0 && binder.isValid && isIsoDate(date)) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The check's result: the numbers, then the problems, the extra cards, what to check by hand, and the received cards. Split out for the screenshot tests. */
@Composable
fun PackageCheckList(
    result: PackageCheck.Result,
    scanned: Int,
    showReceived: Boolean,
    onShowReceived: (Boolean) -> Unit,
    onAccept: (Int) -> Unit,
    onUndo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val problems = result.problems
    val fine = result.lines.filter { !it.problem }
    val byHand = fine.filter { it.byHand.isNotEmpty() }
    val rest = fine.filter { it.byHand.isEmpty() }
    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "summary") { CheckSummary(result, scanned, note) }
        if (problems.isNotEmpty()) {
            item(key = "problemsHeader") { SectionHeader("Problems (${problems.size})", MaterialTheme.colorScheme.error) }
            items(problems, key = { "p${it.cand.index}" }) { l -> CheckLineRow(l, onAccept, onUndo) }
        }
        if (result.extras.isNotEmpty()) {
            item(key = "extrasHeader") { SectionHeader("Scanned, not in the order (${result.extraCount})") }
            items(result.extras, key = { "x${it.first.id}" }) { (scan, n) -> ExtraRow(scan, n) }
        }
        if (byHand.isNotEmpty()) {
            item(key = "handHeader") {
                Column {
                    SectionHeader("Check by hand (${byHand.size})")
                    Text(
                        "Received, but the camera can't tell foil, condition, signed or altered: have a look at these.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(byHand, key = { "h${it.cand.index}" }) { l -> CheckLineRow(l, onAccept, onUndo) }
        }
        if (rest.isNotEmpty()) {
            item(key = "receivedHeader") {
                Row(Modifier.fillMaxWidth().clickable { onShowReceived(!showReceived) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionHeader("Received (${rest.sumOf { it.line.quantity }})") }
                    Text(if (showReceived) "Hide" else "Show", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (showReceived) items(rest, key = { "r${it.cand.index}" }) { l -> CheckLineRow(l, onAccept, onUndo) }
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun CheckSummary(result: PackageCheck.Result, scanned: Int, note: String?) {
    val received = result.count(Status.RECEIVED) + result.count(Status.BY_HAND) + result.count(Status.PRINTING_UNSURE)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${result.ordered} cards ordered · $scanned scanned", style = MaterialTheme.typography.titleSmall)
            @Composable
            fun stat(label: String, n: Int, bad: Boolean) {
                if (n == 0 && bad) return
                Row {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("$n", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
            stat("Received", received, false)
            stat("Missing", result.count(Status.MISSING), true)
            stat("Wrong language", result.count(Status.WRONG_LANGUAGE), true)
            stat("Wrong printing", result.count(Status.WRONG_PRINTING), true)
            if (result.extraCount > 0) stat("Scanned, not in the order", result.extraCount, false)
            if (result.problems.isEmpty()) {
                Text("Everything ordered was scanned.", style = MaterialTheme.typography.bodySmall, color = TrendColors.up)
            } else {
                Text(
                    "If a card is there but the scanner got it wrong, tap “It's here”; or scan it again. The copy button at the top makes a list for the shop.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What happened to the copies of a line, one sentence per kind, and whether that is a problem. */
internal fun checkLineDetails(l: PackageCheck.Line): List<Pair<String, Boolean>> = l.parts.mapNotNull { p ->
    val n = p.quantity
    val all = n == l.line.quantity
    val text = when (p.status) {
        Status.RECEIVED -> if (all) null else "$n received"
        Status.BY_HAND -> if (all) "Marked as received" else "$n marked as received"
        Status.PRINTING_UNSURE -> (if (all) "Received" else "$n received") + ", printing not sure" +
            (p.scan?.card?.let { " (scanned as ${it.setCode.uppercase()} #${it.collectorNumber})" }.orEmpty())
        Status.WRONG_LANGUAGE -> (if (all) "Received" else "$n received") + " in ${p.scan?.language}, ordered in ${l.line.language}"
        Status.WRONG_PRINTING -> (if (all) "Received" else "$n received") + " as " + (p.scan?.card?.let { "${it.setName} (${it.setCode.uppercase()}) #${it.collectorNumber}" } ?: "another printing")
        Status.MISSING -> if (all) "Missing" else "$n missing"
    }
    text?.let { it to p.status.problem }
}

@Composable
private fun CheckLineRow(l: PackageCheck.Line, onAccept: (Int) -> Unit, onUndo: (Int) -> Unit) {
    val line = l.line
    val card = l.cand.card
    val byHand = l.parts.any { it.status == Status.BY_HAND }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            CardThumb(card?.imageUrl, width = 38, enlargeable = card != null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (if (line.quantity > 1) "${line.quantity}× " else "") + (card?.displayName ?: line.label),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (card != null && !l.cand.guessed) SetLine(card) else Text(orderLineWhere(line), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (card != null && line.foil) FinishTag(card, card.resolveFinish(Finish.FOIL))
                    OrderVersionTags(line)
                    MarksTags(line.signed, line.altered)
                }
                for ((text, bad) in checkLineDetails(l)) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!l.problem && l.byHand.isNotEmpty()) {
                    Text("Check by hand: ${l.byHand.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (l.problem) {
                    TextButton(onClick = { onAccept(l.cand.index) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("It's here") }
                } else if (byHand) {
                    TextButton(onClick = { onUndo(l.cand.index) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Undo “it's here”") }
                }
            }
            line.price?.let { Text(Fmt.money(it), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ExtraRow(scan: ScannedCard, n: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardThumb(scan.card.imageUrl, width = 38, enlargeable = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text((if (n > 1) "$n× " else "") + scan.card.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SetLine(scan.card)
                    FinishTag(scan.card, scan.finish)
                    if (scan.language != "EN") Tag(scan.language)
                }
                if (!scan.exactPrinting) Text("Printing guessed by the scanner", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
