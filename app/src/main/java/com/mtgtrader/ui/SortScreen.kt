package com.mtgtrader.ui

import androidx.compose.foundation.layout.Arrangement
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
import com.mtgtrader.data.DECKS_KEY
import com.mtgtrader.data.DeckCopies
import com.mtgtrader.data.Money
import com.mtgtrader.data.SortLevelRule
import com.mtgtrader.data.SortLevelSetting
import com.mtgtrader.data.SortMove
import com.mtgtrader.data.SortPlan
import com.mtgtrader.data.Together
import com.mtgtrader.data.UNSORTED_KEY
import com.mtgtrader.data.isLevelKey
import com.mtgtrader.data.levelKey
import com.mtgtrader.data.orderOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sorts the collection: splits it into the owner's levels (one binder each, best first), the Decks binder and
 * Unsorted (by value and Commander popularity), keeping Unsorted for what doesn't fit. Every other binder is left
 * alone. Since 1.104; its levels since 1.107.
 */
@Composable
fun SortScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var reload by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf<SortPlan?>(null) }
    var levels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var decks by remember { mutableStateOf<String?>(null) }
    var settled by remember { mutableStateOf(c.settings.sortSettled) }
    var rules by remember { mutableStateOf(c.settings.sortLevels) }
    var mix by remember { mutableStateOf(c.settings.sortMix) }
    var weekly by remember { mutableStateOf(c.settings.sortWeekly) }
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
        rules = c.settings.sortLevels
        mix = c.settings.sortMix
        weekly = c.settings.sortWeekly
        val live = c.sorter.levels()
        setUp = live.isEmpty()
        levels = live.mapIndexed { i, (_, b) -> levelKey(i) to b.name }
        decks = c.sorter.decksBinder()?.name
        if (!setUp) {
            // Popularity comes with the card details; fetch any still missing first.
            runCatching { c.cardDetails.fillMissing() }
            plan = c.sorter.suggest()
        }
    }

    val moves = plan?.moves.orEmpty()
    val kept = moves.filter { it.rowKey !in unticked }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (setUp) "Set up the sort" else "Sort the collection") },
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
        if (p == null || levels.isEmpty() && !setUp) {
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
            // Saving (or a problem with it) shows in the setup itself; a save that worked says so and sorts.
            SortSetUpList(pad, rules, mix, weekly, levels) {
                scope.launch {
                    snackbar.showSnackbar("Settings saved")
                    reload++
                }
            }
            return@Scaffold
        }
        val names = levels.toMap() + listOfNotNull(decks?.let { DECKS_KEY to it })
        SortSuggestions(
            pad = pad,
            settled = settled,
            levels = rules,
            weekly = weekly,
            plan = p,
            placeNames = names,
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

/** "Collection binder 187/200 · Not bulk 640/1,000 · Decks 54 · Unsorted 31" (a level without a limit has no "/"). */
internal fun sortCountsLine(plan: SortPlan, placeNames: Map<String, String>, levels: List<SortLevelSetting>): String {
    val parts = orderOf(levels.size).mapNotNull { place ->
        val n = plan.after[place] ?: 0
        if (place == UNSORTED_KEY && n == 0) return@mapNotNull null
        val name = placeName(place, placeNames)
        val level = levels.getOrNull(place.drop(1).toIntOrNull()?.minus(1) ?: -1)
        val max = if (isLevelKey(place)) level?.max ?: 0 else 0
        if (max > 0) "$name ${"%,d".format(n)}/${"%,d".format(max)}" else "$name ${"%,d".format(n)}"
    }
    return parts.joinToString(" · ")
}

/** The name of [place] as the screen shows it: its binder's, "Decks", or Unsorted. */
internal fun placeName(place: String, placeNames: Map<String, String>) = placeNames[place] ?: when {
    place == UNSORTED_KEY -> Binder.UNSORTED_NAME
    place == DECKS_KEY -> "Decks"
    else -> "Level ${place.drop(1)}"
}

/**
 * The suggested moves, grouped by where the cards are now: the counts after the moves on top, this week's
 * allowance, then the moves themselves ([settled] false: the first sort, everything at once). Since 1.104.
 */
@Composable
fun SortSuggestions(
    pad: PaddingValues,
    settled: Boolean,
    levels: List<SortLevelSetting>,
    weekly: Int,
    plan: SortPlan,
    placeNames: Map<String, String>,
    unticked: Set<String>,
    onToggle: (key: String, on: Boolean) -> Unit,
) {
    // The same order as the planner's moves: from the Decks binder first, then the levels, then Unsorted.
    val fromOrder = orderOf(levels.size)
    val groups = fromOrder.mapNotNull { from ->
        val list = plan.moves.filter { it.from == from }
        if (list.isEmpty()) null else from to list
    }
    LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    sortCountsLine(plan, placeNames, levels),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                if (plan.deckShort > 0) {
                    Text(
                        "Your decks miss ${plan.deckShort} card(s) you don't own",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (settled) {
                    Text(
                        "This week: ${plan.weekUsed} of $weekly moves used" + if (plan.heldBack > 0) " · ${plan.heldBack} more wait for next week" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "First sort: everything is placed at once. After this, cards only move when they clearly belong " +
                            "elsewhere, at most $weekly a week.",
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
            val title = "From " + placeName(from, placeNames)
            item(key = "h_$from") { SortSectionHeader("$title (${list.sumOf { it.copies }})") }
            items(list, key = { it.rowKey }) { move ->
                SortMoveRow(
                    move = move,
                    to = placeName(move.to, placeNames),
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

/** "Collection binder", "Not bulk", "Bulk": what each level's binder is called in the setup. */
private fun placeLabel(i: Int): String = when (i) {
    0 -> "Collection binder"
    1 -> "Not bulk"
    2 -> "Bulk"
    else -> "Level ${i + 1}"
}

/**
 * The sort's setup: three levels (the first two with the old floor as their minimum price), how cards are weighed
 * and the weekly limit. [levelNames] are the levels' binders now, best first, or empty for none. Since 1.104;
 * the levels' own screens come with the setup of 1.107.
 */
@Composable
private fun SortSetUpList(
    pad: PaddingValues,
    levels: List<SortLevelSetting>,
    mixNow: Int,
    weeklyNow: Int,
    levelNames: List<Pair<String, String>>,
    onSave: () -> Unit,
) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    // The form's three levels: the saved ones (the last one may have no limit), or the usual three.
    var maxTop by remember { mutableStateOf((levels.getOrNull(0)?.max ?: 200).toString()) }
    var maxMid by remember { mutableStateOf((levels.getOrNull(1)?.max ?: 1_000).toString()) }
    var maxBulk by remember {
        mutableStateOf(if (levels.isEmpty()) "0" else (levels.getOrNull(2)?.max ?: 0).toString())
    }
    var floor by remember(levels.getOrNull(0)?.minPrice) { mutableStateOf(Money.input(levels.getOrNull(0)?.minPrice ?: 0.5)) }
    val bulkSymbol = Money.symbol
    var mix by remember { mutableStateOf(mixNow) }
    var weekly by remember { mutableStateOf(weeklyNow.toString()) }
    // The problem of the last save (cleared when the next one starts).
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
        if (levelNames.isNotEmpty()) {
            item(key = "levels") {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((i, level) in levelNames.withIndex()) {
                        Text("${placeLabel(i)}: ${level.second}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    saving = true
                    problem = null
                    scope.launch {
                        // The three levels' binders: the ones they have now, or a new one named as the place is.
                        val levelChoices = (0..2).map { i ->
                            val now = c.settings.sortLevels.getOrNull(i)
                            Triple(
                                now?.let { BinderChoice(binderId = it.binder.toLongOrNull() ?: 0) } ?: BinderChoice(newName = placeLabel(i)),
                                when (i) {
                                    0 -> nTop!!
                                    1 -> nMid!!
                                    else -> nBulk!!
                                },
                                if (i < 2) floorEur!! else null,
                            )
                        }
                        val failures = levelChoices.mapNotNull { (choice, _, _) ->
                            choice.newName?.let { name -> c.repo.binderNameProblem(name) }
                        }
                        if (failures.isNotEmpty()) {
                            problem = failures.first()
                            saving = false
                            return@launch
                        }
                        val result = runCatching {
                            c.sorter.setUp(
                                levelChoices, decks = null, mix = mix, weekly = nWeekly!!, together = Together.NAME,
                                outlierRatio = 3.0, outlierGap = 5.0, deckCopies = DeckCopies.BEST,
                            )
                        }
                        problem = result.exceptionOrNull()?.let { it.message ?: "That didn't work" }
                        saving = false
                        if (result.exceptionOrNull() == null) onSave()
                    }
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) { Text("Save and suggest") }
            problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
