package com.mtgtrader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mtgtrader.data.SortLevelSetting
import com.mtgtrader.data.SortLevelsCodec
import com.mtgtrader.data.SortMove
import com.mtgtrader.data.SortPlan
import com.mtgtrader.data.Together
import com.mtgtrader.data.UNSORTED_KEY
import com.mtgtrader.data.isLevelKey
import com.mtgtrader.data.levelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import kotlin.math.roundToInt

/**
 * Sorts the collection: splits it into the owner's levels (one binder each, best first), the Decks binder and
 * Unsorted (by value and Commander popularity), keeping Unsorted for what doesn't fit. Every other binder is left
 * alone. Since 1.104; its levels since 1.107, like the website's Sort page.
 */
@Composable
fun SortScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var reload by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf<SortPlan?>(null) }
    // The levels that exist, best first: their binder's name and id, and how many copies they hold (0 = no limit on the
    // last); taken together, so the settings' levels always line up with their binders.
    var levels by remember { mutableStateOf<List<SortLevelNow>>(emptyList()) }
    // The Decks binder's id and name, when there is one.
    var decks by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var settled by remember { mutableStateOf(c.settings.sortSettled) }
    var weekly by remember { mutableStateOf(c.settings.sortWeekly) }
    var setUp by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    // "Sort everything now": the plan without this week's limit.
    var ignoreCap by remember { mutableStateOf(false) }
    var loadingPlan by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmingStartOver by remember { mutableStateOf(false) }
    var askingUsername by remember { mutableStateOf(false) }
    // The moves ticked off: by default every one the app suggests.
    var unticked by remember { mutableStateOf(setOf<String>()) }
    val details by c.cardDetails.progress.collectAsStateWithLifecycle()
    val ownUser by c.settings.ownArchidektUser.collectAsStateWithLifecycle()

    /** The plan again, with or without this week's limit (as the website's loadPlan). */
    suspend fun loadPlan(ignore: Boolean) {
        loadingPlan = true
        // Off the main thread: with a big collection the plan takes a while and the screen must stay usable (1.108).
        runCatching { withContext(Dispatchers.Default) { c.sorter.suggest(ignoreCap = ignore) } }
            .onSuccess { plan = it; ignoreCap = ignore; unticked = emptySet() }
            .onFailure { snackbar.showSnackbar(it.message ?: "Could not load the sort.") }
        loadingPlan = false
    }

    LaunchedEffect(reload) {
        plan = null
        unticked = emptySet()
        ignoreCap = false
        settled = c.settings.sortSettled
        weekly = c.settings.sortWeekly
        val live = c.sorter.levels()
        setUp = live.isEmpty()
        levels = live.map { (l, b) -> SortLevelNow(name = b.name, max = l.max, binderId = b.id, minPrice = l.minPrice) }
        decks = c.sorter.decksBinder()?.let { it.id to it.name }
        if (!setUp) {
            // Popularity comes with the card details; fetch any still missing first.
            runCatching { c.cardDetails.fillMissing() }
            loadPlan(false)
        }
    }

    // The settings of an existing setup (opened from the menu), not a first setup.
    val editing = setUp && levels.isNotEmpty()
    BackHandler(enabled = editing) { setUp = false }

    val moves = plan?.moves.orEmpty()
    val kept = moves.filter { it.rowKey !in unticked }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (!setUp) "Sort the collection" else if (editing) "Sort settings" else "Set up the sort") },
                // Back from the settings of an existing setup goes to its suggestions (1.108).
                navigationIcon = { IconButton(onClick = { if (editing) setUp = false else nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!setUp) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    ScreenMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Settings…") }, onClick = { menu = false; setUp = true })
                        run {
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
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!setUp && moves.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
        if (setUp) {
            // Saving (or a problem with it) shows in the setup itself; a save that worked says so and sorts.
            // An existing setup starts from the live levels; a new one from the three usual binders.
            SortSetUpList(
                pad = pad,
                now = levels,
                decksNow = decks,
                mixNow = c.settings.sortMix,
                weeklyNow = c.settings.sortWeekly,
                togetherNow = c.settings.sortTogether,
                ratioNow = c.settings.sortOutlierRatio,
                gapNow = c.settings.sortOutlierGap,
                deckCopiesNow = c.settings.sortDeckCopies,
            ) {
                scope.launch {
                    snackbar.showSnackbar("Settings saved; the suggestions follow them.")
                    reload++
                }
            }
            return@Scaffold
        }
        if (p == null) {
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
        val names = levels.mapIndexed { i, l -> levelKey(i) to l.name }.toMap() +
            listOfNotNull(decks?.let { DECKS_KEY to it.second })
        SortSuggestions(
            pad = pad,
            settled = settled,
            levels = levels.mapIndexed { i, l -> SortLevelSetting(levelKey(i), l.max, l.minPrice) },
            weekly = weekly,
            plan = p,
            placeNames = names,
            unticked = unticked,
            onToggle = { key, on -> unticked = if (on) unticked - key else unticked + key },
            ownUserSet = ownUser.isNotBlank(),
            onSetUsername = { askingUsername = true },
            ignoreCap = ignoreCap,
            loadingPlan = loadingPlan,
            onIgnoreCap = { ignore -> scope.launch { loadPlan(ignore) } },
        )
    }

    if (askingUsername) UsernameDialog(onDismiss = { askingUsername = false })

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
                        snackbar.showSnackbar("Starting over: the next sort is a first sort.")
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

/**
 * "Best cards 187/200 · Good cards 640/1,000 · Bulk 1,204 · Decks 54 · Unsorted 31": a level without a limit has no
 * "/" (as the website's Sort page); [placeNames] says each place's binder. Since 1.107.
 */
internal fun sortCountsLine(plan: SortPlan, placeNames: Map<String, String>, levels: List<SortLevelSetting>, hasDecks: Boolean): String {
    fun n(x: Int) = "%,d".format(x)
    val parts = ArrayList<String>()
    for (i in 0 until levels.size) {
        val place = levelKey(i)
        val max = levels[i].max
        val count = plan.after[place] ?: 0
        parts += if (max > 0) "${placeName(place, placeNames)} ${n(count)}/${n(max)}" else "${placeName(place, placeNames)} ${n(count)}"
    }
    if (hasDecks) parts += "${placeName(DECKS_KEY, placeNames)} ${n(plan.after[DECKS_KEY] ?: 0)}"
    val unsorted = plan.after[UNSORTED_KEY] ?: 0
    if (unsorted > 0) parts += "${Binder.UNSORTED_NAME} ${n(unsorted)}"
    return parts.joinToString(" · ")
}

/** The name of [place] as the screen shows it: its binder's, the Decks binder's, or Unsorted. */
internal fun placeName(place: String, placeNames: Map<String, String>) = placeNames[place] ?: when {
    place == UNSORTED_KEY -> Binder.UNSORTED_NAME
    place == DECKS_KEY -> "Decks"
    else -> "Level ${place.drop(1)}"
}

/** A group of the suggestions: its title and its moves. */
internal data class SortGroup(val key: String, val title: String, val moves: List<SortMove>)

/**
 * The moves grouped as the website shows them: "For your decks" first (moves into or out of Decks), then each level
 * in order, then Unsorted. Since 1.107.
 */
internal fun sortGroups(moves: List<SortMove>, levels: Int, placeNames: Map<String, String>, hasDecks: Boolean): List<SortGroup> {
    val groups = ArrayList<SortGroup>()
    val deckMoves = moves.filter { it.to == DECKS_KEY || it.from == DECKS_KEY }
    if (deckMoves.isNotEmpty()) groups += SortGroup("decks", "For your decks", deckMoves)
    for (i in 0 until levels) {
        val key = levelKey(i)
        val list = moves.filter { it.from == key && it.to != DECKS_KEY }
        if (list.isNotEmpty()) groups += SortGroup(key, "From ${placeName(key, placeNames)}", list)
    }
    // (a move into Decks is in "For your decks" already, even when it comes out of Unsorted)
    val fromUnsorted = moves.filter { it.from == UNSORTED_KEY && it.to != DECKS_KEY }
    if (fromUnsorted.isNotEmpty()) groups += SortGroup(UNSORTED_KEY, "From ${Binder.UNSORTED_NAME}", fromUnsorted)
    return groups
}

/**
 * The suggested moves, grouped by where the cards are now: the counts after the moves on top, this week's
 * allowance (or "Sort everything now"), then the moves themselves ([settled] false: the first sort, everything at
 * once). Since 1.104; the decks' moves and the levels since 1.107.
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
    /** Whether your Archidekt username is set: without it only your local decks hold cards (since 1.107). */
    ownUserSet: Boolean = true,
    onSetUsername: (() -> Unit)? = null,
    /** "Sort everything now": the plan without this week's limit (since 1.107). */
    ignoreCap: Boolean = false,
    loadingPlan: Boolean = false,
    onIgnoreCap: ((ignore: Boolean) -> Unit)? = null,
) {
    val hasDecks = DECKS_KEY in placeNames
    val groups = sortGroups(plan.moves, levels.size, placeNames, hasDecks)
    // The cards the levels had no room for.
    // (cards under a level's minimum price go to Unsorted too, but they had a place; only the planner's own "no room"
    // moves count here)
    val noRoom = plan.moves.filter { it.to == UNSORTED_KEY && "no room" in it.reason }.sumOf { it.copies }
    LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)) {
        item(key = "head") {
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    sortCountsLine(plan, placeNames, levels, hasDecks),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                if (!ownUserSet && onSetUsername != null) {
                    TextButton(onClick = onSetUsername, contentPadding = PaddingValues(0.dp)) { Text("Set Archidekt username") }
                }
                if (settled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (ignoreCap) "No weekly limit" else "${plan.weekUsed}/$weekly this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (onIgnoreCap != null) {
                            TextButton(onClick = { onIgnoreCap(!ignoreCap) }, enabled = !loadingPlan) {
                                Text(if (ignoreCap) "Weekly limit" else "Sort all now")
                            }
                        }
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            item(key = "none") {
                EmptyState(if (settled) "Nothing to move" else "Everything is in place", "")
            }
        }
        for (group in groups) {
            item(key = "h_${group.key}") { SortSectionHeader("${group.title} (${group.moves.sumOf { it.copies }})") }
            items(group.moves, key = { "${group.key}/${it.rowKey}" }) { move ->
                SortMoveRow(
                    move = move,
                    to = placeName(move.to, placeNames),
                    checked = move.rowKey !in unticked,
                ) { on -> onToggle(move.rowKey, on) }
            }
        }
        if (noRoom > 0) {
            item(key = "noroom") {
                Text(
                    "${"%,d".format(noRoom)} no room",
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

/**
 * A new setup's three levels: their binders' names, how many copies they hold and the cheapest card they take (in the
 * app's money). Since 1.107.
 */
internal val SORT_NEW_LEVELS = listOf(
    Triple("Best cards", 200, "0.50"),
    Triple("Good cards", 1_000, "0.50"),
    Triple("Bulk", 0, ""),
)

/** A new setup's Decks binder (as the website's Sort page). Since 1.107. */
internal const val SORT_NEW_DECKS = "Decks"

/** One level of the setup as the form keeps it: its binder, how many copies it holds and the cheapest card (as typed). */
internal data class SortLevelForm(val choice: BinderChoice, val holds: String, val min: String)

/**
 * One level that exists, as the screen found it: its binder's name and id, how many copies it holds and its minimum
 * price. Since 1.107.
 */
internal data class SortLevelNow(val name: String, val max: Int, val binderId: Long, val minPrice: Double?)

/**
 * The sort's setup (or its saved settings): the levels (1–6, best first, each with its binder, how many copies it
 * holds and the cheapest card it takes), the Decks binder, how the decks' copies are picked, how copies go together
 * and how cards are weighed. [now] is an existing setup's levels (their binders' names, copies, ids and minimum
 * prices) and [decksNow] its Decks binder (id and name); both empty for a new one, which starts from the three usual
 * binders and a new "Decks". Throws nothing: the sort's own checks
 * ([com.mtgtrader.data.CollectionSorter.setUp]) and its problem show under the button. Since 1.104; the levels' own
 * screen since 1.107.
 */
@Composable
internal fun SortSetUpList(
    pad: PaddingValues,
    now: List<SortLevelNow> = emptyList(),
    decksNow: Pair<Long, String>? = null,
    mixNow: Int,
    weeklyNow: Int,
    togetherNow: Together,
    ratioNow: Double,
    gapNow: Double,
    deckCopiesNow: DeckCopies,
    onSave: () -> Unit,
) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val symbol = Money.symbol
    // An existing setup starts from its own levels and their binders; a new one from the three usual levels.
    val saved = now.isNotEmpty()
    val levels = remember(now) {
        mutableStateListOf<SortLevelForm>().apply {
            if (saved) {
                for (i in now.indices) {
                    add(
                        SortLevelForm(
                            BinderChoice(binderId = now[i].binderId),
                            now[i].max.toString(),
                            Money.input(now[i].minPrice),
                        ),
                    )
                }
            } else {
                SORT_NEW_LEVELS.forEach { (name, holds, min) -> add(SortLevelForm(BinderChoice(newName = name), holds.toString(), min)) }
            }
        }
    }
    fun setLevel(i: Int, form: SortLevelForm) { levels[i] = form }
    fun moveLevel(i: Int, by: Int) {
        val l = levels.removeAt(i)
        levels.add(i + by, l)
    }
    fun addLevel() {
        if (levels.size < SortLevelsCodec.MAX_LEVELS) levels.add(SortLevelForm(BinderChoice(newName = ""), "", ""))
    }
    fun removeLevel(i: Int) { if (levels.size > 1) levels.removeAt(i) }

    // The Decks binder: none, a new one, or one the app has (an existing setup's own binder, or a new setup's "Decks").
    val decksStart = remember(decksNow) {
        decksNow?.let { BinderChoice(binderId = it.first) } ?: BinderChoice(newName = SORT_NEW_DECKS)
    }
    var decksOn by remember { mutableStateOf(true) }
    // Turned off and on again, it starts from the same binder.
    var decksForm by remember(decksNow) { mutableStateOf(decksStart) }
    // On by default (1.108): a binder already called "Decks" rather than a second one of that name.
    LaunchedEffect(decksNow) {
        if (decksNow == null) {
            c.db.binderDao().observeAll().first().firstOrNull { it.name.trim().equals(SORT_NEW_DECKS, ignoreCase = true) }
                ?.let { decksForm = BinderChoice(binderId = it.id) }
        }
    }
    var deckCopies by remember { mutableStateOf(deckCopiesNow) }
    var together by remember { mutableStateOf(togetherNow) }
    var ratio by remember { mutableStateOf(String.format(java.util.Locale.ROOT, "%.2f", ratioNow).trimEnd('0').trimEnd('.')) }
    var gap by remember { mutableStateOf(Money.input(gapNow)) }
    var mix by remember { mutableStateOf(mixNow) }
    var weekly by remember { mutableStateOf(weeklyNow.toString()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val holds = levels.map { it.holds.toIntOrNull() }
    // null = no minimum (blank); a typed amount that isn't a number is caught by the check below.
    val mins = levels.map { if (it.min.isBlank()) null else Fmt.parseMoneyEur(it.min) }
    val nRatio = ratio.trim().replace(',', '.').toDoubleOrNull()
    val nGap = if (gap.isBlank()) null else Fmt.parseMoneyEur(gap)
    val nWeekly = weekly.toIntOrNull()
    val firstProblem: String? = when {
        levels.size > SortLevelsCodec.MAX_LEVELS -> "There can be at most six levels."
        levels.any { it.choice.newName?.isBlank() == true } -> "Give each new binder a name."
        levels.indices.any { i -> holds[i] == null } -> "Each level needs how many copies it holds (0 or more)."
        levels.indices.any { i -> levels[i].min.isNotBlank() && mins[i] == null } -> "The minimum prices must be amounts, like 0.50."
        levels.indices.any { i -> i < levels.size - 1 && holds[i] == 0 } -> "Only the last level may hold nothing (0 = no limit)."
        decksOn && decksForm.newName?.isBlank() == true -> "Give the Decks binder a name."
        nWeekly == null || nWeekly < 1 -> "Most moves a week is a whole number, 1 or more."
        together != Together.OFF && (nRatio == null || nRatio < 1) -> "“Unless ×” must be 1 or more."
        together != Together.OFF && (nGap == null || nGap < 0) -> "“and +${Money.symbol}” must be 0 or more."
        else -> null
    }

    LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)) {
        item(key = "levels-head") { SortFormHeading("Levels") }
        val shown = levels.toList()
        for ((i, form) in shown.withIndex()) {
            val last = i == shown.lastIndex
            item(key = "level-$i") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Level ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { moveLevel(i, -1) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, "Move level ${i + 1} up") }
                            IconButton(onClick = { moveLevel(i, 1) }, enabled = !last) { Icon(Icons.Default.ArrowDownward, "Move level ${i + 1} down") }
                            if (levels.size > 1) {
                                IconButton(onClick = { removeLevel(i) }) { Icon(Icons.Default.Delete, "Remove level ${i + 1}") }
                            }
                        }
                        BinderPicker(
                            "Level ${i + 1} binder", form.choice, { setLevel(i, form.copy(choice = it)) },
                            suggestedName = SORT_NEW_LEVELS.getOrNull(i)?.first ?: "", modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = form.holds, onValueChange = { v -> setLevel(i, form.copy(holds = v.filter(Char::isDigit).take(7))) },
                                label = { Text("Holds") }, singleLine = true,
                                isError = form.holds.isNotEmpty() && form.holds.toIntOrNull() == null,
                                supportingText = if (last) ({ Text("0 = no limit") }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = form.min, onValueChange = { v -> setLevel(i, form.copy(min = v)) },
                                label = { Text("Minimum price ($symbol)") }, singleLine = true, placeholder = { Text("None") },
                                isError = form.min.isNotBlank() && Fmt.parseMoneyEur(form.min) == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        if (levels.size < SortLevelsCodec.MAX_LEVELS) {
            item(key = "levels-add") { TextButton(onClick = { addLevel() }) { Text("Add a level") } }
        }
        item(key = "decks") {
            Column(Modifier.padding(top = 8.dp)) {
                SortFormHeading("Decks")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { decksOn = !decksOn },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = decksOn, onCheckedChange = { decksOn = it })
                    Text("Use a Decks binder")
                }
                if (decksOn) {
                    BinderPicker(
                        "Decks binder", decksForm, { decksForm = it }, suggestedName = SORT_NEW_DECKS,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
        item(key = "copies") {
            Column(Modifier.padding(top = 12.dp)) {
                SortFormHeading("Copies for decks")
                DeckCopies.entries.forEach { d -> SortRadioRow(d.label, deckCopies == d) { deckCopies = d } }
            }
        }
        item(key = "together") {
            Column(Modifier.padding(top = 12.dp)) {
                SortFormHeading("Keep copies together")
                Together.entries.forEach { t -> SortRadioRow(t.label, together == t) { together = t } }
                if (together != Together.OFF) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = ratio, onValueChange = { ratio = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                            label = { Text("Unless ×") }, singleLine = true,
                            isError = ratio.isNotBlank() && (nRatio == null || nRatio < 1),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = gap, onValueChange = { gap = it },
                            label = { Text("and +$symbol") }, singleLine = true,
                            isError = gap.isNotBlank() && (nGap == null || nGap < 0),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item(key = "mix") {
            Column(Modifier.padding(top = 12.dp)) {
                SortFormHeading("How cards are weighed")
                Slider(
                    value = mix.toFloat(), onValueChange = { mix = (it / 10).roundToInt() * 10 },
                    valueRange = 0f..100f, steps = 9, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Value", style = MaterialTheme.typography.bodySmall)
                    Text("Popularity", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "weekly") {
            OutlinedTextField(
                value = weekly, onValueChange = { weekly = it.filter(Char::isDigit).take(5) },
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
                        val levelOut = levels.mapIndexed { i, form -> Triple(form.choice, holds[i] ?: 0, mins[i]) }
                        // A new binder's name the app already has is that binder (it's taken as it is); a new one must
                        // fit. The Decks binder that stays on keeps the one it has.
                        val newNames = levels.mapIndexedNotNull { i, form -> form.choice.newName?.trim()?.let { i to it } } +
                            listOfNotNull((decksForm.newName?.trim()?.takeIf { decksOn })?.let { -1 to it })
                        val failures = newNames.mapNotNull { (_, name) ->
                            if (name.isBlank()) "Give each new binder a name." else c.repo.binderNameProblem(name)
                        }
                        if (failures.isNotEmpty()) {
                            problem = failures.first()
                            saving = false
                            return@launch
                        }
                        val decks = decksForm.takeIf { decksOn }
                        val result = runCatching {
                            c.sorter.setUp(
                                levelOut, decks, mix, nWeekly!!, together,
                                nRatio ?: 3.0, nGap ?: 0.0, deckCopies,
                            )
                        }
                        problem = result.exceptionOrNull()?.let { it.message ?: "That didn't work" }
                        saving = false
                        if (result.exceptionOrNull() == null) onSave()
                    }
                },
                enabled = firstProblem == null && !saving,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) { Text("Save and suggest") }
            (problem ?: firstProblem)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SortFormHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
}

@Composable
private fun SortRadioRow(label: String, selected: Boolean, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPick() }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onPick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
