package com.mtgtrader.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.mtgtrader.container
import com.mtgtrader.data.DeckOrder
import com.mtgtrader.data.DeckResult
import com.mtgtrader.data.FriendDeck
import com.mtgtrader.data.GameRecord
import com.mtgtrader.data.GameSeat
import com.mtgtrader.data.GameStats
import com.mtgtrader.data.GameStore
import com.mtgtrader.data.Games
import com.mtgtrader.data.HeadToHead
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.OutReason
import com.mtgtrader.data.Player
import com.mtgtrader.data.PlayerResult
import com.mtgtrader.data.WinType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Trophy = Color(0xFFE0A526)

private fun gameDate(ms: Long) = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

/** "Joel won with Auntie Plague", "A draw". */
internal fun gameTitle(g: GameRecord): String {
    val w = g.winner ?: return if (g.draw) "A draw" else "No winner told yet"
    return "${w.name} won" + (w.deckName.ifBlank { w.commander }.takeIf { it.isNotBlank() }?.let { " with $it" } ?: "")
}

internal fun gameLine(g: GameRecord): String = listOfNotNull(
    gameDate(g.startedAt),
    Games.duration(g.durationMs).takeIf { g.durationMs > 0 },
    g.rounds?.let { "$it turns" },
    "${g.seatList.size} players",
).joinToString(" · ")

/** The Games tab (since 1.47): a new game or a rematch, the game in progress, the last games and the stats. */
@Composable
fun GamesScreen(nav: NavController) {
    val c = LocalContext.current.container
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by c.games.live.collectAsStateWithLifecycle()
    val games by c.games.games.collectAsStateWithLifecycle(emptyList())
    var menu by remember { mutableStateOf(false) }
    var confirmDrop by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { c.games.refreshArt() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(GameStats.csv(games).toByteArray()) } }.isSuccess
            Toast.makeText(context, if (ok) "Saved ${games.size} game(s)" else "Couldn't save the file", Toast.LENGTH_LONG).show()
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Games") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    ScreenMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Players and their decks") }, leadingIcon = { Icon(Icons.Default.People, null) }, onClick = { menu = false; nav.navigate("games/players") })
                        DropdownMenuItem(text = { Text("Add a past game…") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { menu = false; nav.navigate("games/add") })
                        DropdownMenuItem(
                            text = { Text("Export game history (CSV)") }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, enabled = games.isNotEmpty(),
                            onClick = { menu = false; exporter.launch("mtg-games.csv") },
                        )
                    }
                    SettingsButton(nav)
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nav.navigate("games/new?rematch=false") }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("New game")
                    }
                    OutlinedButton(onClick = { nav.navigate("games/new?rematch=true") }, enabled = c.games.lastTable != null, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.Replay, null); Spacer(Modifier.width(6.dp)); Text("Rematch")
                    }
                }
            }
            live?.let { g ->
                item(key = "live") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Game in progress", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull("${g.seats.size} players", if (g.turnsOn) "round ${g.round}" else null, Games.duration(g.elapsed(System.currentTimeMillis()))).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = { confirmDrop = true }, contentPadding = PaddingValues(0.dp)) { Text("Drop it") }
                            }
                            Button(onClick = { nav.navigate("games/play") }) { Text("Continue") }
                        }
                    }
                }
            }
            if (games.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No games yet. Start one with New game: the phone lies in the middle of the table, and each player taps their own side. " +
                            "Finished games are kept here, with stats per deck and per player.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item(key = "last_h") { Text("Last games", style = MaterialTheme.typography.titleMedium) }
                items(games.take(5), key = { "g_${it.id}" }) { g -> GameRow(g) { g.uid?.let { nav.navigate("games/game/$it") } } }
                if (games.size > 5) item(key = "all") { TextButton(onClick = { nav.navigate("games/all") }) { Text("All ${games.size} games") } }
                item(key = "stats") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Stats", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("${games.size}", if (games.size == 1) "game" else "games", Modifier.weight(1f))
                            StatTile(GameStats.myWinRate(games)?.let { "${(it * 100).toInt()} %" } ?: "–", "you won", Modifier.weight(1f))
                            StatTile(Games.duration(games.map { it.durationMs }.average().toLong()).replace(" min", ""), "average game", Modifier.weight(1f))
                        }
                        OutlinedButton(onClick = { nav.navigate("games/stats") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.BarChart, null); Spacer(Modifier.width(6.dp)); Text("Decks, players and head to head")
                        }
                    }
                }
            }
        }
    }
    if (confirmDrop) {
        AlertDialog(
            onDismissRequest = { confirmDrop = false },
            title = { Text("Drop the game in progress?") },
            text = { Text("It isn't saved; to keep it, continue it and end it from the centre button.") },
            confirmButton = { TextButton(onClick = { confirmDrop = false; c.games.abandon() }) { Text("Drop it", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDrop = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun GameRow(g: GameRecord, onClick: () -> Unit) {
    val w = g.winner
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SeatPicture(GameStore.artCrop(w?.scryfallId), w?.seat ?: 5, 40.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(gameTitle(g), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(gameLine(g), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (w?.me == true) Icon(Icons.Default.EmojiEvents, "You won", tint = Trophy)
    }
}

/** Every recorded game, newest first. */
@Composable
fun GamesListScreen(nav: NavController) {
    val c = LocalContext.current.container
    val games by c.games.games.collectAsStateWithLifecycle(emptyList())
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("All games (${games.size})") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(games, key = { it.id }) { g -> GameRow(g) { g.uid?.let { nav.navigate("games/game/$it") } } }
        }
    }
}

/** A seat's place on the results screens: "1", "2"… or "–" (none, e.g. a draw). */
private fun placeLabel(p: Int?) = p?.toString() ?: "–"

/**
 * The results of a game, to change: places (who won), how it was won and a note; used when a game ends
 * ([GameOverScreen]) and for a recorded game ([GameDetailScreen]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsForm(
    seats: List<GameSeat>,
    places: Map<Int, Int?>,
    onPlace: (Int, Int?) -> Unit,
    draw: Boolean,
    onDraw: (Boolean) -> Unit,
    winType: WinType?,
    onWinType: (WinType?) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    turnsOn: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Places", style = MaterialTheme.typography.titleSmall)
        seats.sortedBy { places[it.seat] ?: Int.MAX_VALUE }.forEach { s ->
            var open by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AssistChip(onClick = { open = true }, label = { Text(placeLabel(places[s.seat]), fontWeight = FontWeight.Bold) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) })
                    ScreenMenu(expanded = open, onDismissRequest = { open = false }) {
                        (listOf<Int?>(null) + (1..seats.size)).forEach { p ->
                            DropdownMenuItem(text = { Text(if (p == null) "No place" else "$p") }, onClick = { open = false; onPlace(s.seat, p) })
                        }
                    }
                }
                SeatPicture(GameStore.artCrop(s.scryfallId), s.seat, 36.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(listOf(s.name, s.deckName.ifBlank { s.commander }).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                    val out = listOfNotNull(
                        s.outRound?.takeIf { turnsOn }?.let { "out in round $it" },
                        OutReason.fromKey(s.outReason)?.label,
                        s.finalLife?.takeIf { s.outReason == null }?.let { "$it life" },
                    ).joinToString(" · ")
                    if (out.isNotBlank()) Text(out, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (places[s.seat] == 1 && !draw) Icon(Icons.Default.EmojiEvents, "Won", tint = Trophy)
            }
        }
        Row(Modifier.clickable { onDraw(!draw) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(draw, onDraw)
            Text("A draw (nobody won)")
        }
        if (!draw) {
            Text("How was it won? (optional)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WinType.entries.forEach { t -> FilterChip(winType == t, { onWinType(if (winType == t) null else t) }, label = { Text(t.label) }) }
            }
        }
        OutlinedTextField(note, onNote, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
    }
}

/** The game just ended: who won (the last one standing, or picked), how, and a note; saved to the game history. */
@Composable
fun GameOverScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val live by c.games.live.collectAsStateWithLifecycle()
    val g = live
    val endedAt = remember { System.currentTimeMillis() }
    if (g == null) {
        LaunchedEffect(Unit) { nav.safePopBackStack() }
        return
    }
    val base = remember(g.uid) { LiveGames.record(g, endedAt, null, null, null) }
    var places by remember(g.uid) { mutableStateOf(base.seatList.associate { it.seat to it.place }) }
    var draw by remember(g.uid) { mutableStateOf(base.draw) }
    var winType by remember { mutableStateOf<WinType?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    fun saved(): GameRecord {
        val seats = base.seatList.map { it.copy(place = places[it.seat]) }
        return base.copy(seats = Games.encodeSeats(seats), draw = draw || seats.none { it.place == 1 }, winType = winType?.key.takeIf { !draw }, note = note.ifBlank { null })
    }
    val winner = base.seatList.firstOrNull { places[it.seat] == 1 }.takeIf { !draw }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Game over") }, navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the game") } }) },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                g.seats.firstOrNull { it.seat == winner?.seat }?.artUrl?.let { AsyncImage(it, null, Modifier.fillMaxWidth().fillMaxHeight(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxWidth().fillMaxHeight().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (winner != null) { Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFC94D)); Spacer(Modifier.width(6.dp)) }
                        Text(winner?.let { "${it.name} wins" } ?: if (draw) "A draw" else "Who won?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        listOfNotNull(winner?.deckName?.ifBlank { null }, Games.duration(base.durationMs), base.rounds?.let { "$it turns" }).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            if (winner == null && !draw) Text("More than one player is still in: give the winner place 1, or tick “A draw”.", style = MaterialTheme.typography.bodySmall)
            ResultsForm(base.seatList, places, { seat, p -> places = places + (seat to p) }, draw, { draw = it }, winType, { winType = it }, note, { note = it }, base.turnsOn)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.safePopBackStack() }, modifier = Modifier.weight(1f)) { Text("Back to the game") }
                Button(onClick = {
                    scope.launch {
                        c.games.finish(saved())
                        nav.navigate("games") { popUpTo("games") { inclusive = true } }
                    }
                }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    c.games.finish(saved())
                    nav.navigate("games/new?rematch=true") { popUpTo("games") }
                }
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Replay, null); Spacer(Modifier.width(6.dp)); Text("Save and rematch") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A recorded game: its players and results, to change or delete. */
@Composable
fun GameDetailScreen(nav: NavController, uid: String, fresh: Boolean) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val games by c.games.games.collectAsStateWithLifecycle(emptyList())
    val g = games.firstOrNull { it.uid == uid }
    var places by remember(g?.id) { mutableStateOf(g?.seatList?.associate { it.seat to it.place }.orEmpty()) }
    var draw by remember(g?.id) { mutableStateOf(g?.draw == true) }
    var winType by remember(g?.id) { mutableStateOf(WinType.fromKey(g?.winType)) }
    var note by remember(g?.id) { mutableStateOf(g?.note.orEmpty()) }
    var minutes by remember(g?.id) { mutableStateOf(g?.durationMs?.let { (it / 60_000).toString() }.orEmpty()) }
    var rounds by remember(g?.id) { mutableStateOf(g?.rounds?.toString().orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(g?.let { gameDate(it.startedAt) } ?: "Game") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { confirmDelete = true }, enabled = g != null) { Icon(Icons.Default.Delete, "Delete the game") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (g == null) return@Scaffold
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (fresh) Text("Now say who won (place 1) and how.", style = MaterialTheme.typography.bodyMedium)
            Text(gameLine(g) + if (g.manual) " · added by hand" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ResultsForm(g.seatList, places, { seat, p -> places = places + (seat to p) }, draw, { draw = it }, winType, { winType = it }, note, { note = it }, g.turnsOn || g.manual)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minutes") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(rounds, { rounds = it.filter(Char::isDigit) }, label = { Text("Turns (rounds)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            g.turnList.takeIf { it.isNotEmpty() }?.let { turns ->
                Text("Turn times", style = MaterialTheme.typography.titleSmall)
                val bySeat = turns.groupBy { it.seat }
                g.seatList.forEach { s ->
                    val mine = bySeat[s.seat].orEmpty()
                    if (mine.isNotEmpty()) Text(
                        "${s.name}: ${mine.size} turns, ${Games.clock(mine.sumOf { it.ms } / mine.size)} on average, longest ${Games.clock(mine.maxOf { it.ms })}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(onClick = {
                val seats = g.seatList.map { it.copy(place = places[it.seat]) }
                val ms = minutes.toLongOrNull()?.times(60_000) ?: g.durationMs
                scope.launch {
                    c.games.saveGame(
                        g.copy(
                            seats = Games.encodeSeats(seats), draw = draw || seats.none { it.place == 1 }, winType = winType?.key.takeIf { !draw },
                            note = note.ifBlank { null }, durationMs = ms, endedAt = g.startedAt + ms, rounds = rounds.toIntOrNull(),
                            turnsOn = g.turnsOn || rounds.toIntOrNull() != null,
                        )
                    )
                    nav.safePopBackStack()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete && g != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this game?") },
            text = { Text("${gameTitle(g)}, ${gameLine(g)}. It's taken out of the stats on both phones.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        c.games.deleteGame(g)
                        nav.safePopBackStack()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Game stats (since 1.47): decks (sortable), players, and the owner against each other player. */
@Composable
fun GameStatsScreen(nav: NavController) {
    val c = LocalContext.current.container
    val games by c.games.games.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(0) }
    var order by rememberSaveable { mutableStateOf(DeckOrder.WIN_RATE) }
    var onlyMine by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val decks = remember(games) { GameStats.decks(games) }
    val players = remember(games) { GameStats.players(games) }
    val h2h = remember(games) { GameStats.headToHead(games) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Game stats") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp)) {
            item(key = "tabs") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    listOf("Decks", "Players", "Head to head").forEachIndexed { i, t ->
                        SegmentedButton(tab == i, { tab = i }, SegmentedButtonDefaults.itemShape(i, 3), icon = {}) { Text(t, maxLines = 1) }
                    }
                }
            }
            when (tab) {
                0 -> {
                    item(key = "sort") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sorted by", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(6.dp))
                            Box {
                                AssistChip(onClick = { sortMenu = true }, label = { Text(order.label) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                                ScreenMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    DeckOrder.entries.forEach { o -> DropdownMenuItem(text = { Text(o.label) }, onClick = { sortMenu = false; order = o }) }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            FilterChip(onlyMine, { onlyMine = !onlyMine }, label = { Text("My decks") })
                        }
                    }
                    item(key = "head") { DeckTableHead() }
                    items(GameStats.sortDecks(decks.filter { !onlyMine || it.mine }, order), key = { it.key }) { d ->
                        DeckTableRow(d) { d.deckId?.let { nav.navigate("deck/$it") } }
                        HorizontalDivider()
                    }
                    item(key = "foot") {
                        Text(
                            "Avg: average game length. Br.: the deck's bracket (Commander Salt), when the game was played.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                1 -> items(players, key = { it.key }) { p -> PlayerRow(p); HorizontalDivider() }
                else -> {
                    item(key = "h2h_h") { Text("You against …", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp)) }
                    items(h2h, key = { it.key }) { h -> HeadToHeadRow(h) }
                    item(key = "h2h_f") {
                        Text("Who finished ahead, in the games you both played.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

private val deckCols = listOf("Deck" to 2.6f, "Games" to 1f, "Wins" to 1.25f, "Avg" to 1.1f, "Br." to 0.7f)

@Composable
private fun DeckTableHead() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        deckCols.forEach { (t, w) ->
            Text(t, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(w), textAlign = if (t == "Deck") TextAlign.Start else TextAlign.End)
        }
    }
}

@Composable
private fun DeckTableRow(d: DeckResult, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(2.6f), verticalAlignment = Alignment.CenterVertically) {
            SeatPicture(GameStore.artCrop(d.scryfallId), 0, 28.dp)
            Spacer(Modifier.width(6.dp))
            Text(d.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        listOf(
            "${d.games}" to 1f, "${d.wins} · ${(d.winRate * 100).toInt()} %" to 1.25f,
            Games.duration(d.avgMs).replace(" min", "") to 1.1f, (d.bracket?.toString() ?: "–") to 0.7f,
        ).forEach { (t, w) -> Text(t, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(w)) }
    }
}

@Composable
private fun PlayerRow(p: PlayerResult) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.name + if (p.me) " (you)" else "", style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    "${p.games} games", "${p.wins} won (${(p.winRate * 100).toInt()} %)", p.avgPlace?.let { "average place %.1f".format(it) },
                    if (p.startedGames > 0) "started ${p.startedGames}, won ${p.startedWins} of those" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeadToHeadRow(h: HeadToHead) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(h.name, modifier = Modifier.width(90.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val tot = (h.ahead + h.behind).coerceAtLeast(1).toFloat()
        Row(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (h.ahead > 0) Box(Modifier.weight(h.ahead / tot).fillMaxHeight().background(Color(0xFF2E9E5B)))
            if (h.behind > 0) Box(Modifier.weight(h.behind / tot).fillMaxHeight().background(Color(0xFFD9534F)))
        }
        Text("${h.ahead}–${h.behind}", modifier = Modifier.padding(start = 8.dp))
    }
}

/** The deck page's "Games" section (since 1.47): the deck's results and its last games. */
@Composable
internal fun DeckGamesCard(r: DeckResult, last: List<GameRecord>, deckId: Long, onGame: (GameRecord) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    @Composable
                    fun big(v: String, l: String, m: Modifier) = Column(m) {
                        Text(v, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(l, style = MaterialTheme.typography.bodySmall)
                    }
                    big("${r.wins}–${r.losses}", "won–lost", Modifier.weight(1f))
                    big("${(r.winRate * 100).toInt()} %", "win rate", Modifier.weight(1f))
                    big(r.avgPlace?.let { "%.1f".format(it) } ?: "–", "average place", Modifier.weight(1f))
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                listOfNotNull(
                    "Average game" to Games.duration(r.avgMs),
                    r.avgWinMs?.let { "Average win" to Games.duration(it) },
                    r.avgRounds?.let { "Average turns" to "%.1f".format(it) },
                    r.usualWinRound?.let { "Usually wins on" to "turn $it" },
                    r.winTypes.takeIf { it.isNotEmpty() }?.let { l -> "Wins by" to l.joinToString(" · ") { "${it.first.label.lowercase()} ${it.second}" } },
                    r.startedGames.takeIf { it > 0 }?.let { "Started first" to "$it games, ${r.startedWins} won" },
                ).forEach { (k, v) ->
                    Row {
                        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (r.oppBracket != null || r.oppPower != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        listOfNotNull(r.bracket?.let { "Bracket $it" }, r.power?.let { "power %.1f".format(it) }).joinToString(" · ").let { if (it.isBlank()) "" else "$it. " } +
                            "The decks it played against: " + listOfNotNull(r.oppBracket?.let { "bracket %.1f".format(it) }, r.oppPower?.let { "power %.1f".format(it) }).joinToString(" · ") + " on average.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        last.take(5).forEach { g ->
            val mine = g.seatList.firstOrNull { it.deckId == deckId }
            val won = mine?.place == 1 && !g.draw
            Row(Modifier.fillMaxWidth().clickable { onGame(g) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (won) Icons.Default.EmojiEvents else Icons.Default.Remove, null, Modifier.size(18.dp), tint = if (won) Trophy else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(gameDate(g.startedAt), if (won) "won" else mine?.place?.let { ordinal(it) }, Games.duration(g.durationMs), g.rounds?.takeIf { won }?.let { "turn $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun ordinal(n: Int) = when (n) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th" }

/** Players and their decks (since 1.47): add, rename or delete friends, and add or delete their decks. */
@Composable
fun PlayersScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val players by c.games.players.collectAsStateWithLifecycle(emptyList())
    val friendDecks by c.games.friendDecks.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(Unit) { c.games.me() }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Player?>(null) }
    var deckFor by remember { mutableStateOf<Player?>(null) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Players") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 14.dp, end = 6.dp, bottom = 24.dp)) {
            item(key = "add") {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a player") }
            }
            items(players, key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name + if (p.me) " (you)" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "${p.name}: more") }
                            ScreenMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename…") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; renaming = p })
                                if (!p.me) {
                                    DropdownMenuItem(text = { Text("Add a deck…") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { menu = false; deckFor = p })
                                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = {
                                        menu = false
                                        scope.launch { val undo = c.games.deletePlayer(p); if (snackbar.showUndo("Deleted ${p.name}")) undo() }
                                    })
                                }
                            }
                        }
                    }
                    if (p.me) {
                        Text("Your decks are the app's decks (the Decks tab).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        friendDecks.filter { it.playerUid == p.uid }.forEach { d -> FriendDeckLine(d) { scope.launch { val undo = c.games.deleteFriendDeck(d); if (snackbar.showUndo("Deleted ${d.name}")) undo() } } }
                        TextButton(onClick = { deckFor = p }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add a deck") }
                    }
                    HorizontalDivider(Modifier.padding(end = 8.dp))
                }
            }
        }
    }
    if (adding) {
        TextInputDialog("New player", "Name", "", "Add", onDismiss = { adding = false }) { name ->
            adding = false
            scope.launch { c.games.addPlayer(name) }
        }
    }
    renaming?.let { p ->
        TextInputDialog("Rename ${p.name}", "Name", p.name, "Rename", onDismiss = { renaming = null }) { name ->
            renaming = null
            scope.launch { c.games.renamePlayer(p, name) }
        }
    }
    deckFor?.let { p ->
        FriendDeckDialog(p.name, onDismiss = { deckFor = null }) { name, commander, partner, link ->
            deckFor = null
            val uid = p.uid ?: return@FriendDeckDialog
            scope.launch {
                val d = runCatching { c.games.friendDeckFor(uid, name, commander, partner, link) }
                    .getOrElse { FriendDeck(playerUid = uid, name = name.ifBlank { commander }, commander = commander, partner = partner, archidektUrl = link) }
                c.games.saveFriendDeck(d)
            }
        }
    }
}

@Composable
private fun FriendDeckLine(d: FriendDeck, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        SeatPicture(GameStore.artCrop(d.scryfallId), 2, 36.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(d.commander, d.partner).joinToString(" & "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ColorPips(d.colorIdentity, 10)
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete ${d.name}") }
    }
}
