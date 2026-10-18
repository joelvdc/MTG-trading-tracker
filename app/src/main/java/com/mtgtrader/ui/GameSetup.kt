package com.mtgtrader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.mtgtrader.container
import com.mtgtrader.data.Deck
import com.mtgtrader.data.FriendDeck
import com.mtgtrader.data.GameRecord
import com.mtgtrader.data.GameSeat
import com.mtgtrader.data.GameStore
import com.mtgtrader.data.Games
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.LogEntry
import com.mtgtrader.data.Player
import com.mtgtrader.data.SeatChoice
import com.mtgtrader.data.TableSetup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val setupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** A seat's deck, as the app knows it: one of the owner's decks or a friend's deck. */
internal data class SeatDeck(val name: String, val commander: String, val partner: String?, val scryfallId: String?, val colorIdentity: String, val artUrl: String?, val bracket: Int?, val power: Double?)

internal fun seatDeck(choice: SeatChoice, decks: List<Deck>, friendDecks: List<FriendDeck>): SeatDeck? {
    choice.deckId?.let { id ->
        val d = decks.firstOrNull { it.archidektId == id } ?: return null
        val names = d.commanders.split(" & ").map { it.trim() }.filter { it.isNotEmpty() }
        return SeatDeck(
            d.name, names.firstOrNull().orEmpty(), names.getOrNull(1), d.commanderScryfallId, d.colorIdentity,
            GameStore.artCrop(d.commanderScryfallId) ?: d.artUrl, d.bracketRealistic, d.powerLevel,
        )
    }
    choice.friendDeckUid?.let { uid ->
        val f = friendDecks.firstOrNull { it.uid == uid } ?: return null
        return SeatDeck(f.name, f.commander, f.partner, f.scryfallId, f.colorIdentity, GameStore.artCrop(f.scryfallId), null, null)
    }
    return null
}

/** A new table with [seats], the starting life of the last game and the game settings last used (since 1.48). */
internal fun newTable(settings: com.mtgtrader.data.Settings, last: TableSetup?, seats: List<SeatChoice>) = TableSetup(
    seats = seats, startingLife = last?.startingLife ?: 40,
    commanderDamage = settings.trackerFlag("trackerCommanderDamage").value, turnsOn = settings.trackerTurns.value, clock = settings.trackerClock.value,
    cmdLifeLoss = settings.trackerFlag("trackerCmdLifeLoss").value, whenDown = settings.trackerWhenDown.value.key,
    ends = settings.trackerFlag("trackerEnds").value, logOn = settings.trackerFlag("trackerLog").value,
)

/** The game that starts from a table setup: everyone at the starting life, a background and colour each, the first player. */
internal fun startGame(setup: TableSetup, decks: List<Deck>, friendDecks: List<FriendDeck>, randomArt: List<String>, now: Long, first: Int): LiveGame {
    var spare = randomArt
    val used = setup.seats.mapNotNull { it.colour }.toMutableSet()
    val seats = setup.seats.mapIndexed { i, ch ->
        val d = seatDeck(ch, decks, friendDecks)
        val art = d?.artUrl ?: ch.artUrl ?: spare.firstOrNull()?.also { spare = spare.drop(1) }
        // The seat's own colour (as the New game screen shows it), unless another seat picked it.
        val colour = ch.colour ?: (listOf(i % LiveGames.PALETTE.size) + LiveGames.PALETTE.indices).first { it !in used }.also { used += it }
        LiveSeat(
            seat = i, playerUid = ch.playerUid, name = ch.name.ifBlank { "Player ${i + 1}" }, me = ch.me,
            deckId = ch.deckId, friendDeckUid = ch.friendDeckUid, deckName = d?.name.orEmpty(), commander = d?.commander.orEmpty(),
            scryfallId = d?.scryfallId, partner = d?.partner, colorIdentity = d?.colorIdentity.orEmpty(), bracket = d?.bracket, power = d?.power,
            artUrl = art, colour = colour, life = setup.startingLife,
        )
    }
    return LiveGame(
        uid = UUID.randomUUID().toString(), startedAt = now, startingLife = setup.startingLife, commanderDamage = setup.commanderDamage,
        turnsOn = setup.turnsOn, clock = setup.clock, counters = setup.counters.filter { it.startsWith(LiveGames.CUSTOM) }, seats = seats,
        firstSeat = first, active = first, cmdLifeLoss = setup.cmdLifeLoss, whenDown = setup.whenDown, ends = setup.ends && seats.size in setOf(4, 6),
        logOn = setup.logOn, log = if (setup.logOn) listOf(LogEntry(0, first, "start")) else emptyList(),
    )
}

/** The table setup a game in progress stands for: its seats as they are now, and its settings (for a rematch). */
internal fun setupOf(g: LiveGame, firstSeat: Int?): TableSetup = TableSetup(
    seats = g.seats.map { SeatChoice(it.playerUid, it.name, it.me, it.deckId, it.friendDeckUid, it.artUrl, it.colour) },
    startingLife = g.startingLife, commanderDamage = g.commanderDamage, turnsOn = g.turnsOn, clock = g.clock,
    counters = g.counters, firstSeat = firstSeat?.takeIf { it < g.seats.size }, cmdLifeLoss = g.cmdLifeLoss, whenDown = g.whenDown,
    ends = g.ends, logOn = g.logOn,
)

/** [seat] with another player or deck, picked during the game (since 1.48); their damage, counters and life stay. */
internal fun reseat(g: LiveGame, seat: Int, ch: SeatChoice, d: SeatDeck?): LiveGame = g.copy(
    seats = g.seats.map { s ->
        if (s.seat != seat) s else s.copy(
            playerUid = ch.playerUid, name = ch.name.ifBlank { "Player ${seat + 1}" }, me = ch.me, deckId = ch.deckId, friendDeckUid = ch.friendDeckUid,
            deckName = d?.name.orEmpty(), commander = d?.commander.orEmpty(), scryfallId = d?.scryfallId, partner = d?.partner,
            colorIdentity = d?.colorIdentity.orEmpty(), bracket = d?.bracket, power = d?.power, artUrl = d?.artUrl ?: ch.artUrl ?: s.artUrl,
            partners = null,
        )
    },
)

/**
 * New game (since 1.47): players, who sits where and plays which deck, and who starts; since 1.48 the game's own
 * settings (starting life, turns, counters…) are set in the game. A rematch starts from the last table. With
 * [addPast], it records a game played without the tracker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameSetupScreen(nav: NavController, rematch: Boolean, addPast: Boolean = false) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val players by c.games.players.collectAsStateWithLifecycle(emptyList())
    val friendDecks by c.games.friendDecks.collectAsStateWithLifecycle(emptyList())
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val pictures by c.settings.trackerPictures.collectAsStateWithLifecycle()
    var setupText by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        c.games.refreshArt()
        if (setupText == null) {
            val me = c.games.me()
            val last = c.games.lastTable
            val start = if (rematch && last != null) last else newTable(c.settings, last, listOf(SeatChoice(me.uid, me.name, me = true)) + (2..4).map { SeatChoice(name = "Player $it") })
            setupText = setupJson.encodeToString(TableSetup.serializer(), start)
        }
    }
    val setup = setupText?.let { setupJson.decodeFromString(TableSetup.serializer(), it) }
    fun change(f: (TableSetup) -> TableSetup) {
        setup?.let { setupText = setupJson.encodeToString(TableSetup.serializer(), f(it)) }
    }
    var picking by remember { mutableStateOf<Int?>(null) }
    var pastDate by rememberSaveable { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var pastMinutes by rememberSaveable { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (addPast) "Add a past game" else "New game") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val s = setup ?: return@Scaffold
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Players")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (2..6).forEach { n ->
                    SegmentedButton(s.seats.size == n, {
                        change { t ->
                            val seats = if (n <= t.seats.size) t.seats.take(n) else t.seats + (t.seats.size + 1..n).map { SeatChoice(name = "Player $it") }
                            t.copy(seats = seats, firstSeat = t.firstSeat?.takeIf { it < n })
                        }
                    }, SegmentedButtonDefaults.itemShape(n - 2, 5), icon = {}) { Text("$n") }
                }
            }
            Label("Seats (clockwise)")
            s.seats.forEachIndexed { i, ch ->
                val d = seatDeck(ch, decks, friendDecks)
                Card(onClick = { picking = i }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(20.dp))
                        SeatPicture(d?.artUrl ?: ch.artUrl, ch.colour ?: i, 44.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(ch.name.ifBlank { "Player ${i + 1}" } + if (ch.me) " (you)" else "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                d?.let { listOf(it.name, listOfNotNull(it.commander, it.partner).joinToString(" & ")).filter { x -> x.isNotBlank() }.distinct().joinToString(" · ") } ?: "No deck picked",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (s.firstSeat == i) Icon(Icons.Default.PlayArrow, "Starts", tint = MaterialTheme.colorScheme.primary)
                        if (i > 0) IconButton(onClick = {
                            change { t ->
                                val l = t.seats.toMutableList()
                                l.add(i - 1, l.removeAt(i))
                                t.copy(seats = l, firstSeat = t.firstSeat?.let { f -> when (f) { i -> i - 1; i - 1 -> i; else -> f } })
                            }
                        }) { Icon(Icons.Default.ArrowUpward, "Move up a seat") }
                    }
                }
            }
            if (!addPast) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Who starts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    FilterChip(s.firstSeat == null, { change { it.copy(firstSeat = null) } }, label = { Text("Random") }, leadingIcon = { Icon(Icons.Default.Shuffle, null, Modifier.size(16.dp)) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.seats.forEachIndexed { i, ch -> FilterChip(s.firstSeat == i, { change { it.copy(firstSeat = i) } }, label = { Text(ch.name.ifBlank { "Player ${i + 1}" }) }) }
                }
                Text(
                    "Starting life, turns, commander damage and the counters are set in the game: tap the circle with the number of players in the middle of the table.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pictures && s.seats.any { seatDeck(it, decks, friendDecks)?.artUrl == null && it.artUrl == null } && c.games.storedArt().isEmpty()) {
                    Text(
                        "Players without a commander get a random commander's picture; none are stored yet (the app fetches them when online), so this game uses plain colours.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val first = s.firstSeat ?: s.seats.indices.random()
                        val except = s.seats.mapNotNull { it.artUrl }.toSet()
                        val art = c.games.randomArt(s.seats.size, except).map { it.url }
                        val game = startGame(s, decks, friendDecks, art, now, first)
                        c.games.start(game, s)
                        nav.navigate("games/play") { popUpTo("games") }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 0.dp),
                ) { Text("Start the game") }
            } else {
                HorizontalDivider()
                OutlinedTextField(pastDate, { pastDate = it }, label = { Text("Date (year-month-day)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pastMinutes, { pastMinutes = it.filter(Char::isDigit) }, label = { Text("How long, in minutes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val at = runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$pastDate 20:00")!!.time }.getOrNull()
                        if (at == null) {
                            scope.launch { snackbar.showSnackbar("Write the date as year-month-day, e.g. 2026-10-17") }
                            return@Button
                        }
                        val ms = (pastMinutes.toLongOrNull() ?: 0) * 60_000
                        val seats = s.seats.mapIndexed { i, ch ->
                            val d = seatDeck(ch, decks, friendDecks)
                            GameSeat(
                                seat = i, playerUid = ch.playerUid, name = ch.name.ifBlank { "Player ${i + 1}" }, me = ch.me, deckId = ch.deckId,
                                friendDeckUid = ch.friendDeckUid, deckName = d?.name.orEmpty(), commander = listOfNotNull(d?.commander?.ifBlank { null }, d?.partner).joinToString(" & "),
                                scryfallId = d?.scryfallId, colorIdentity = d?.colorIdentity.orEmpty(), bracket = d?.bracket, power = d?.power,
                            )
                        }
                        val uid = UUID.randomUUID().toString()
                        scope.launch {
                            c.games.saveGame(GameRecord(startedAt = at, endedAt = at + ms, durationMs = ms, manual = true, seats = Games.encodeSeats(seats), uid = uid))
                            nav.navigate("games/game/$uid?fresh=true") { popUpTo("games") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Next: who won") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    picking?.let { i ->
        val s = setup ?: return@let
        SeatPickerSheet(
            seatIndex = i,
            choice = s.seats[i],
            taken = s.seats.filterIndexed { j, _ -> j != i }.mapNotNull { it.playerUid }.toSet(),
            players = players,
            decks = decks,
            friendDecks = friendDecks,
            pictures = pictures,
            onDismiss = { picking = null },
        ) { new -> change { t -> t.copy(seats = t.seats.mapIndexed { j, ch -> if (j == i) new else ch }) } }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))

/** A seat's picture: the commander's art, or the panel's colour. */
@Composable
internal fun SeatPicture(url: String?, colour: Int, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(seatBrush(colour))) {
        if (url != null) AsyncImage(url, null, Modifier.size(size), contentScale = ContentScale.Crop)
    }
}

/** Who sits in a seat and which deck they play; friends and their decks can be added here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeatPickerSheet(
    seatIndex: Int,
    choice: SeatChoice,
    taken: Set<String>,
    players: List<Player>,
    decks: List<Deck>,
    friendDecks: List<FriendDeck>,
    pictures: Boolean,
    onDismiss: () -> Unit,
    /** The colour and background choices; in a game they're on the player's own sheet instead. */
    showLook: Boolean = true,
    onChange: (SeatChoice) -> Unit,
) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var newPlayer by remember { mutableStateOf(false) }
    var newDeck by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().heightIn(max = menuMaxHeight()).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("Seat ${seatIndex + 1}", style = MaterialTheme.typography.titleLarge)
            Text("Player", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                players.filter { it.uid != null && (it.uid !in taken || it.uid == choice.playerUid) }.forEach { p ->
                    FilterChip(choice.playerUid == p.uid, {
                        onChange(SeatChoice(p.uid, p.name, me = p.me, artUrl = choice.artUrl, colour = choice.colour))
                    }, label = { Text(p.name + if (p.me) " (you)" else "") }, leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(16.dp)) })
                }
                FilterChip(choice.playerUid == null, { onChange(SeatChoice(name = "Player ${seatIndex + 1}", artUrl = choice.artUrl, colour = choice.colour)) }, label = { Text("Guest") })
                FilterChip(false, { newPlayer = true }, label = { Text("+ New player") })
            }
            Text("Deck", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            @Composable
            fun deckRow(selected: Boolean, title: String, sub: String, art: String?, onClick: () -> Unit) = ListItem(
                headlineContent = { Text(title, fontWeight = if (selected) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { SeatPicture(art, seatIndex, 40.dp) },
                colors = ListItemDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
            )
            if (choice.me) {
                decks.sortedBy { it.name.lowercase() }.forEach { d ->
                    deckRow(choice.deckId == d.archidektId, d.name, d.commanders.ifBlank { "No commander" }, GameStore.artCrop(d.commanderScryfallId) ?: d.artUrl) {
                        onChange(choice.copy(deckId = d.archidektId, friendDeckUid = null)); onDismiss()
                    }
                }
                if (decks.isEmpty()) Text("Your decks are the app's decks (the Decks tab).", style = MaterialTheme.typography.bodySmall)
            } else if (choice.playerUid != null) {
                friendDecks.filter { it.playerUid == choice.playerUid }.forEach { f ->
                    deckRow(choice.friendDeckUid == f.uid, f.name, listOfNotNull(f.commander, f.partner).joinToString(" & "), GameStore.artCrop(f.scryfallId)) {
                        onChange(choice.copy(friendDeckUid = f.uid, deckId = null)); onDismiss()
                    }
                }
                TextButton(onClick = { newDeck = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("New deck for ${choice.name}") }
            } else {
                Text("A guest plays without a deck of their own; add them as a player to keep their decks.", style = MaterialTheme.typography.bodySmall)
            }
            if (choice.deckId != null || choice.friendDeckUid != null) {
                TextButton(onClick = { onChange(choice.copy(deckId = null, friendDeckUid = null)) }) { Text("No deck") }
            }
            if (showLook && !pictures) {
                Text("Colour", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveGames.PALETTE.indices.forEach { i ->
                        val on = (choice.colour ?: seatIndex) == i
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(LiveGames.PALETTE[i]))
                                .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable { onChange(choice.copy(colour = i)) },
                        )
                    }
                }
            }
            if (showLook && pictures && seatDeck(choice, decks, friendDecks)?.artUrl == null) {
                Text("Background", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeatPicture(choice.artUrl, seatIndex, 56.dp)
                    Text(
                        if (choice.artUrl == null) "A random commander's picture" else "Picked for this seat",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    TextButton(onClick = {
                        c.games.randomArt(1, setOfNotNull(choice.artUrl)).firstOrNull()?.let { onChange(choice.copy(artUrl = it.url)) }
                    }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Another") }
                }
            }
        }
    }
    if (newPlayer) {
        TextInputDialog("New player", "Name", "", "Add", onDismiss = { newPlayer = false }) { name ->
            newPlayer = false
            scope.launch {
                val p = c.games.addPlayer(name)
                onChange(SeatChoice(p.uid, p.name, artUrl = choice.artUrl, colour = choice.colour))
            }
        }
    }
    if (newDeck && choice.playerUid != null) {
        FriendDeckDialog(choice.name, onDismiss = { newDeck = false }) { name, commander, partner, link ->
            newDeck = false
            scope.launch {
                val d = c.games.saveFriendDeck(c.games.friendDeckFor(choice.playerUid, name, commander, partner, link))
                onChange(choice.copy(friendDeckUid = d.uid, deckId = null))
            }
        }
    }
}

/** A friend's new deck: a name, its commander (and partner) found on Scryfall, and optionally its Archidekt link. */
@Composable
internal fun FriendDeckDialog(owner: String, onDismiss: () -> Unit, onSave: (name: String, commander: String, partner: String?, link: String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var commander by rememberSaveable { mutableStateOf("") }
    var partner by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$owner's new deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CommanderField("Commander", commander) { commander = it }
                CommanderField("Partner (if any)", partner) { partner = it }
                OutlinedTextField(name, { name = it }, label = { Text("Deck name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(link, { link = it }, label = { Text("Archidekt link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, commander, partner.ifBlank { null }, link.ifBlank { null }) }, enabled = commander.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A commander's name, with Scryfall's suggestions while typing. */
@Composable
private fun CommanderField(label: String, value: String, onChange: (String) -> Unit) {
    val c = LocalContext.current.container
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var picked by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (picked || value.length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = runCatching { c.scryfall.autocomplete(value) }.getOrDefault(emptyList()).take(5)
    }
    Column {
        OutlinedTextField(value, { picked = false; onChange(it) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        suggestions.forEach { s ->
            Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().clickable { picked = true; suggestions = emptyList(); onChange(s) }.padding(vertical = 6.dp, horizontal = 8.dp))
        }
    }
}
