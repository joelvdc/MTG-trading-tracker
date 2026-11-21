package com.mtgtrader.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.Binder
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.Money
import com.mtgtrader.data.SortMove
import com.mtgtrader.data.SortPlace
import com.mtgtrader.data.SortPlan
import com.mtgtrader.data.SortRules
import com.mtgtrader.data.UNSORTED_KEY
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sorts the collection: splits it into a Collection binder, Not bulk and Bulk (by value and Commander popularity),
 * keeping Unsorted for what doesn't fit. Every other binder is left alone. Since 1.104.
 */
@Composable
fun SortScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var reload by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf<SortPlan?>(null) }
    var placed by remember { mutableStateOf<List<Pair<SortPlace, Binder>>>(emptyList()) }
    var settled by remember { mutableStateOf(c.settings.sortSettled) }
    var rules by remember { mutableStateOf(c.settings.sortRules) }
    var setUp by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmingStartOver by remember { mutableStateOf(false) }
    // The moves ticked off: by default every one the app suggests.
    var unticked by remember { mutableStateOf(setOf<String>()) }
    val details by c.cardDetails.progress.collectAsStateWithLifecycle()

    LaunchedEffect(reload) {
        plan = null
        unticked = emptySet()
        settled = c.settings.sortSettled
        rules = c.settings.sortRules
        // Popularity comes with the card details; fetch any still missing first.
        runCatching { c.cardDetails.fillMissing() }
        val now = c.sorter.places()
        setUp = SortPlace.ORDER.any { now[it] == null }
        placed = SortPlace.ORDER.mapNotNull { p -> now[p]?.let { p to it } }
        plan = c.sorter.suggest()
    }

    val moves = plan?.moves.orEmpty()
    val kept = moves.filter { it.rowKey !in unticked }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (setUp) "Sort the collection" else "Set up the sort") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    ScreenMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!setUp) {
                            DropdownMenuItem(text = { Text("Settings…") }, onClick = { menu = false; setUp = true })
                        } else {
                            DropdownMenuItem(text = { Text("Settings…") }, onClick = { menu = false; setUp = true })
                            DropdownMenuItem(
                                text = { Text("Suggest declined moves again") },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        c.settings.sortSkips = emptySet()
                                        reload++
                                    }
                                },
                            )
                            DropdownMenuItem(text = { Text("Start over") }, onClick = { menu = false; confirmingStartOver = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!setUp && moves.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Tick what you moved. Unticked moves won't be suggested again.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Button(
                            onClick = {
                                applying = true
                                scope.launch {
                                    val accepted = kept
                                    val declined = moves.filter { it.rowKey in unticked }
                                    c.sorter.apply(accepted, declined)
                                    applying = false
                                    snackbar.showSnackbar(
                                        if (accepted.isEmpty()) "Nothing moved; ${declined.size} move(s) won't be suggested again"
                                        else "Moved ${accepted.sumOf { it.copies }} card(s)" + if (declined.isNotEmpty()) ", declined ${declined.size}" else "",
                                    )
                                    reload++
                                }
                            },
                            enabled = !applying,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (kept.size == moves.size) "Done" else "Done (${kept.size} of ${moves.size} moves)") }
                    }
                }
            }
        },
    ) { pad ->
        val p = plan
        if (p == null || placed.isEmpty() && !setUp) {
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
        if (setUp) {
            SortSetUpList(pad, rules, placed.toMap()) { saved ->
                c.settings.sortRules = saved
                scope.launch {
                    snackbar.showSnackbar("Settings saved")
                    reload++
                }
            }
            return@Scaffold
        }
        SortSuggestions(
            pad = pad,
            settled = settled,
            rules = rules,
            plan = p,
            placeNames = placed.associate { (place, b) -> place.key to b.name },
            unticked = unticked,
            onToggle = { key, on -> unticked = if (on) unticked - key else unticked + key },
        )
    }

    if (confirmingStartOver) {
        AlertDialog(
            onDismissRequest = { confirmingStartOver = false },
            title = { Text("Start over?") },
            text = {
                Text("Sort everything again from scratch? The weekly limit and the 8-week rest start over.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingStartOver = false
                    scope.launch {
                        c.sorter.startOver()
                        snackbar.showSnackbar("Starting over")
                        reload++
                    }
                }) { Text("Start over") }
            },
            dismissButton = { TextButton(onClick = { confirmingStartOver = false }) { Text("Cancel") } },
        )
    }
}

/** The suggestions' own key: a card's kind and the two places ("<kind>><from>><to>"). */
internal val SortMove.rowKey get() = "$key>$from>$to"

/** "Collection binder 187/200 · Not bulk 640/1,000 · Bulk 1,204" (a place without a limit has no "/"). */
internal fun sortCountsLine(plan: SortPlan, placeNames: Map<String, String>, rules: SortRules): String {
    val parts = SortPlace.ORDER.map { place ->
        val n = plan.after[place.key] ?: 0
        val max = when (place) {
            SortPlace.TOP -> rules.maxTop
            SortPlace.MID -> rules.maxMid
            SortPlace.BULK -> rules.maxBulk
        }
        val name = placeNames[place.key] ?: place.label
        if (max > 0) "$name ${"%,d".format(n)}/${"%,d".format(max)}" else "$name ${"%,d".format(n)}"
    }
    val unsorted = plan.after[UNSORTED_KEY] ?: 0
    return (parts + if (unsorted > 0) listOf("${Binder.UNSORTED_NAME} ${"%,d".format(unsorted)}") else emptyList()).joinToString(" · ")
}

/**
 * The suggested moves, grouped by where the cards are now: the counts after the moves on top, this week's
 * allowance, then the moves themselves ([settled] false: the first sort, everything at once). Since 1.104.
 */
@Composable
fun SortSuggestions(
    pad: PaddingValues,
    settled: Boolean,
    rules: SortRules,
    plan: SortPlan,
    placeNames: Map<String, String>,
    unticked: Set<String>,
    onToggle: (key: String, on: Boolean) -> Unit,
) {
    // The same order as the planner's moves: from the Collection binder first, then Not bulk, Bulk, Unsorted.
    val fromOrder = SortPlace.ORDER.map { it.key } + UNSORTED_KEY
    val groups = fromOrder.mapNotNull { from ->
        val list = plan.moves.filter { it.from == from }
        if (list.isEmpty()) null else from to list
    }
    LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    sortCountsLine(plan, placeNames, rules),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                if (settled) {
                    Text(
                        "This week: ${plan.weekUsed} of ${rules.weekly} moves used" + if (plan.heldBack > 0) " · ${plan.heldBack} more wait for next week" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "First sort: everything is placed at once. After this, cards only move when they clearly belong " +
                            "elsewhere, at most ${rules.weekly} a week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (groups.isEmpty()) {
            item(key = "none") {
                EmptyState(
                    if (settled) "Nothing to move" else "Everything is in place",
                    if (settled) "The binders already hold the right cards for these rules."
                    else "Each card is already in the binder it belongs in.",
                )
            }
        }
        for ((from, list) in groups) {
            val title = "From " + (placeNames[from] ?: SortPlace.fromKey(from)?.label ?: Binder.UNSORTED_NAME)
            item(key = "h_$from") { SortSectionHeader("$title (${list.sumOf { it.copies }})") }
            items(list, key = { it.rowKey }) { move ->
                SortMoveRow(
                    move = move,
                    to = placeNames[move.to] ?: SortPlace.fromKey(move.to)?.label ?: Binder.UNSORTED_NAME,
                    checked = move.rowKey !in unticked,
                ) { on -> onToggle(move.rowKey, on) }
            }
        }
        if (plan.moves.isNotEmpty()) {
            item(key = "note") {
                Text(
                    "Cards your decks use, the trade binder and your other binders are left alone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SortSectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

/** A move: "2× Rhystic Study", its finish, where it goes, its price and why. */
@Composable
private fun SortMoveRow(move: SortMove, to: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    // "<name>|<foil>|<etched>|…" (see [CardKind.key]): the finish is the second and third part.
    val kind = move.key.split('|')
    val foil = kind.getOrNull(1) == "true"
    val etched = kind.getOrNull(2) == "true"
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheck)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (move.copies > 1) {
                    Text("${move.copies}× ", fontWeight = FontWeight.Medium)
                }
                Text(move.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (foil) Tag(if (etched) "ETCHED" else "FOIL", FoilColor, Color.White)
                Text("→ $to", style = MaterialTheme.typography.bodySmall)
            }
            Text(move.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Fmt.money(move.price), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Each place's binder as the setup starts: the one it has now, or a new one with the place's usual name. */
internal fun initialChoices(places: Map<SortPlace, Binder>): Map<SortPlace, BinderChoice> =
    SortPlace.ORDER.associateWith { place -> places[place]?.let { BinderChoice(binderId = it.id) } ?: BinderChoice(newName = place.label) }

/** "Collection binder", "Not bulk", "Bulk": what each place's binder is called in the setup. */
internal fun placeLabel(place: SortPlace): String = when (place) {
    SortPlace.TOP -> "Collection binder"
    SortPlace.MID -> "Not bulk"
    SortPlace.BULK -> "Bulk"
}

/** The sort's setup (or its saved settings): the three binders, the limits and how cards are weighed. */
@Composable
private fun SortSetUpList(pad: PaddingValues, rules: SortRules, places: Map<SortPlace, Binder>, onSave: (SortRules) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf(initialChoices(places)) }
    var maxTop by remember { mutableStateOf(rules.maxTop.toString()) }
    var maxMid by remember { mutableStateOf(rules.maxMid.toString()) }
    var maxBulk by remember { mutableStateOf(rules.maxBulk.toString()) }
    var floor by remember(rules.floor) { mutableStateOf(Money.input(rules.floor)) }
    val bulkSymbol = Money.symbol
    var mix by remember { mutableStateOf(rules.mix) }
    var weekly by remember { mutableStateOf(rules.weekly.toString()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val floorEur = remember(floor) { Fmt.parseMoneyEur(floor) }
    val nTop = maxTop.toIntOrNull()
    val nMid = maxMid.toIntOrNull()
    val nBulk = maxBulk.toIntOrNull()
    val nWeekly = weekly.toIntOrNull()
    val valid = nTop != null && nMid != null && nBulk != null && nWeekly != null && nWeekly >= 1 && floorEur != null
    LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)) {
        item(key = "intro") {
            Text(
                "Splits your collection into a Collection binder, Not bulk and Bulk by value and Commander popularity. " +
                    "Cards your decks use, the trade binder and your other binders are left alone.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item(key = "binders") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (place in SortPlace.ORDER) {
                    key(place) {
                        SortBinderField(place, choices.getValue(place)) { choices = choices + (place to it) }
                    }
                }
            }
        }
        item(key = "limits") {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("How many copies each binder holds (0 = no limit)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = maxTop, onValueChange = { maxTop = it.filter(Char::isDigit).take(5) },
                    label = { Text("Collection binder copies") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxMid, onValueChange = { maxMid = it.filter(Char::isDigit).take(5) },
                    label = { Text("Not bulk copies") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxBulk, onValueChange = { maxBulk = it.filter(Char::isDigit).take(6) },
                    label = { Text("Bulk copies (0 = no limit)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = floor, onValueChange = { floor = it },
                    label = { Text("Always bulk under ($bulkSymbol)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "mix") {
            Column(Modifier.padding(top = 14.dp)) {
                Text("How cards are weighed", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = mix.toFloat(), onValueChange = { mix = (it / 10).roundToInt() * 10 },
                    valueRange = 0f..100f, steps = 9, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("Value", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            mix < 50 -> "Value counts more"
                            mix > 50 -> "Commander popularity counts more"
                            else -> "Value and popularity, like the trade binder"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Commander popularity", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "weekly") {
            OutlinedTextField(
                value = weekly, onValueChange = { weekly = it.filter(Char::isDigit).take(4) },
                label = { Text("Most moves a week") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
        item(key = "save") {
            Button(
                onClick = {
                    val saved = SortRules(nTop!!, nMid!!, nBulk!!, floorEur!!, mix, nWeekly!!)
                    saving = true
                    scope.launch {
                        // A new binder may not repeat a name (the three places must end up as three different binders).
                        val problems = SortPlace.ORDER.mapNotNull { place ->
                            choices.getValue(place).newName?.let { name -> c.repo.binderNameProblem(name)?.let { placeLabel(place) to it } }
                        }
                        if (problems.isNotEmpty()) {
                            problem = problems.first().second
                            saving = false
                            return@launch
                        }
                        // One after the other, so that a new binder's id is never taken for an older one.
                        val failures = ArrayList<String>()
                        for (place in SortPlace.ORDER) {
                            runCatching { c.sorter.setUp(mapOf(place to choices.getValue(place)), saved) }
                                .onFailure { failures += "${placeLabel(place)}: ${it.message ?: "that didn't work"}" }
                        }
                        saving = false
                        problem = failures.firstOrNull()
                        if (failures.isEmpty()) onSave(saved)
                    }
                },
                enabled = valid && !saving && choices.values.all { it.isValid },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) { Text("Save and suggest") }
            problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

/** One place's binder: an existing binder, or a new one named here. */
@Composable
private fun SortBinderField(place: SortPlace, choice: BinderChoice, onChange: (BinderChoice) -> Unit) {
    BinderPicker(
        label = placeLabel(place),
        choice = choice,
        onChange = onChange,
        suggestedName = place.label,
        modifier = Modifier.fillMaxWidth(),
    )
}
