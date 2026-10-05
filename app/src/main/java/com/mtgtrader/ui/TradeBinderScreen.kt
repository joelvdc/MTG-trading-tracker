package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.TradeBinderRules
import com.mtgtrader.data.TradeChange
import kotlinx.coroutines.launch

/**
 * Makes or updates the trade binder: the app suggests cards to put in (spare copies, by value and
 * Commander popularity) and to take out, with the reason for each; ticked suggestions are applied,
 * unticked ones aren't suggested again. Since 1.18.
 */
@Composable
fun TradeBinderScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var rules by remember { mutableStateOf(c.settings.tradeRules) }
    var changes by remember { mutableStateOf<List<TradeChange>?>(null) }
    val unticked = remember { mutableStateListOf<String>() }
    var exists by remember { mutableStateOf(false) }
    var editingRules by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val details by c.cardDetails.progress.collectAsStateWithLifecycle()

    LaunchedEffect(reload) {
        changes = null
        unticked.clear()
        exists = c.tradeBinder.binder() != null
        // Popularity comes with the card details; fetch any still missing first.
        runCatching { c.cardDetails.fillMissing() }
        changes = c.tradeBinder.suggest()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (exists) "Update trade binder" else "Make a trade binder") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { editingRules = true }) { Icon(Icons.Default.Tune, "Rules") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Suggest turned-down cards again") },
                            onClick = {
                                menu = false
                                scope.launch { c.tradeBinder.forgetSkipped(); reload++ }
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val list = changes
            if (!list.isNullOrEmpty()) {
                val n = list.count { it.skipKey !in unticked }
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = {
                            applying = true
                            scope.launch {
                                val accepted = list.filter { it.skipKey !in unticked }
                                val declined = list.filter { it.skipKey in unticked }
                                c.tradeBinder.apply(accepted, declined)
                                applying = false
                                snackbar.showSnackbar("Trade binder updated: ${accepted.filter { it.add }.sumOf { it.copies }} in, ${accepted.filterNot { it.add }.sumOf { it.copies }} out")
                                reload++
                            }
                        },
                        enabled = !applying,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(if (n == list.size) "Apply all $n changes" else "Apply $n of ${list.size} changes") }
                }
            }
        },
    ) { pad ->
        val list = changes
        if (list == null) {
            Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.padding(6.dp))
                Text(
                    details?.let { (d, t) -> "Getting card details: $d of $t" } ?: "Looking through the collection…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            return@Scaffold
        }
        val adds = list.filter { it.add }
        val removes = list.filterNot { it.add }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)) {
            item(key = "rules") {
                Card(
                    onClick = { editingRules = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Up to ${rules.maxCards} cards worth €%.2f or more".format(rules.minValue) + if (rules.keepOne) ", keeping 1 copy of each" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Only spare copies: what your decks use stays home, and wishlist cards and basic lands are left out. " +
                                "Ranked by value and how much Commander players want them (EDHREC), with a nudge for rising prices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (list.isEmpty()) {
                item(key = "none") {
                    EmptyState(
                        "Nothing to change",
                        if (exists) "The trade binder already holds the best spare cards for these rules." else "No spare cards match these rules. Try a lower minimum value.",
                    )
                }
            }
            if (adds.isNotEmpty()) item(key = "h_add") { SectionHeader("Put in (${adds.sumOf { it.copies }})") }
            items(adds, key = { it.skipKey }) { ch -> ChangeRow(ch, ch.skipKey !in unticked) { on -> if (on) unticked.remove(ch.skipKey) else unticked.add(ch.skipKey) } }
            if (removes.isNotEmpty()) item(key = "h_rem") { SectionHeader("Take out (${removes.sumOf { it.copies }})") }
            items(removes, key = { it.skipKey }) { ch -> ChangeRow(ch, ch.skipKey !in unticked) { on -> if (on) unticked.remove(ch.skipKey) else unticked.add(ch.skipKey) } }
            if (list.isNotEmpty()) {
                item(key = "note") {
                    Text(
                        "Unticked suggestions aren't made again (⋮ to undo that). Cards taken out go back to the binder with their other copies.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    if (editingRules) {
        TradeRulesDialog(rules, onDismiss = { editingRules = false }) {
            editingRules = false
            rules = it
            c.settings.tradeRules = it
            reload++
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun ChangeRow(ch: TradeChange, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheck(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheck)
        CardThumb(ch.card.imageUrl, width = 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text((if (ch.copies > 1) "${ch.copies}× " else "") + ch.card.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                SetLine(ch.card)
                FinishTag(ch.card, ch.kind.let { com.mtgtrader.data.Finish.of(it.foil, it.etched) })
                if (ch.kind.condition != "NM") Tag(ch.kind.condition)
                if (ch.kind.language != "EN") Tag(ch.kind.language)
            }
            Text(ch.reason, style = MaterialTheme.typography.labelSmall, color = if (ch.add) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Fmt.money(ch.unitPrice?.let { it * ch.copies }), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TradeRulesDialog(initial: TradeBinderRules, onDismiss: () -> Unit, onSave: (TradeBinderRules) -> Unit) {
    var max by remember { mutableStateOf(initial.maxCards.toString()) }
    var min by remember { mutableStateOf("%.2f".format(initial.minValue)) }
    var keepOne by remember { mutableStateOf(initial.keepOne) }
    val maxN = max.toIntOrNull()
    val minV = Fmt.parseMoney(min)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trade binder rules") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = max, onValueChange = { max = it.filter(Char::isDigit).take(4) }, label = { Text("Most cards in the binder") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = min, onValueChange = { min = it }, label = { Text("Only cards worth at least (€)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep at least 1 copy")
                        Text(
                            "One copy of each card stays in the collection, out of the trade binder (the copies your decks use count).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = keepOne, onCheckedChange = { keepOne = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = maxN != null && maxN > 0 && minV != null, onClick = { onSave(TradeBinderRules(maxN!!, keepOne, minV!!)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
