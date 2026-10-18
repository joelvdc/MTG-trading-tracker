package com.mtgtrader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.mtgtrader.container
import com.mtgtrader.data.Games
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.LowLifeStyle
import com.mtgtrader.data.OutReason
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Gold = Color(0xFFFFC94D)
private val SheetBg = Color(0xFF211E28)

/** How the panels look and react, from Settings → Life tracker. */
internal data class PanelLook(
    val pictures: Boolean = true,
    val aboveBelow: Boolean = false,
    val symbols: Boolean = false,
    val lowLife: LowLifeStyle = LowLifeStyle.GHOST,
)

/** Turns a box a quarter (or half) turn, swapping its width and height for a quarter turn. */
internal fun Modifier.turned(degrees: Int) = layout { m, c ->
    if (degrees % 180 == 0) {
        val p = m.measure(c)
        layout(p.width, p.height) { p.placeWithLayer(0, 0) { rotationZ = degrees.toFloat() } }
    } else {
        val p = m.measure(Constraints(c.minHeight, c.maxHeight, c.minWidth, c.maxWidth))
        layout(p.height, p.width) { p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = degrees.toFloat() } }
    }
}

/**
 * Where each seat sits around a phone lying flat in the middle of the table, clockwise from the first seat: the
 * panel's turn (so it faces its player) for 2 to 6 players. 90° faces a player on the left side, −90° the right.
 */
internal fun seatTurn(players: Int, index: Int): Int = when (players) {
    2 -> if (index == 0) 180 else 0
    3 -> when (index) { 0 -> 90; 1 -> -90; else -> 0 }
    4 -> if (index == 0 || index == 3) 90 else -90
    5 -> when (index) { 0, 4 -> 90; 3 -> 0; else -> -90 }
    else -> if (index == 0 || index >= 4) 90 else -90
}

/** The life tracker (since 1.47): the whole screen split into one panel per player, like Lifetap. */
@Composable
fun TrackerScreen(nav: NavController) {
    val c = LocalContext.current.container
    val game by c.games.live.collectAsStateWithLifecycle()
    val canUndo by c.games.canUndo.collectAsStateWithLifecycle()
    val pictures by c.settings.trackerPictures.collectAsStateWithLifecycle()
    val aboveBelow by c.settings.trackerAboveBelow.collectAsStateWithLifecycle()
    val holdOne by c.settings.trackerHoldOne.collectAsStateWithLifecycle()
    val symbols by c.settings.trackerSymbols.collectAsStateWithLifecycle()
    val lowLife by c.settings.trackerLowLife.collectAsStateWithLifecycle()
    FullScreen()
    val g = game
    if (g == null) {
        LaunchedEffect(Unit) { nav.safePopBackStack() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    // The change since the last pause in tapping, per seat: shown next to the total for a moment.
    val deltas = remember { mutableStateMapOf<Int, Pair<Int, Long>>() }
    fun changeLife(seat: Int, by: Int) {
        val t = System.currentTimeMillis()
        val was = deltas[seat]?.takeIf { t - it.second < DELTA_MS }?.first ?: 0
        deltas[seat] = (was + by) to t
        c.games.update { LiveGames.changeLife(it, seat, by) }
    }
    var sheetFor by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var spin by remember { mutableStateOf<Int?>(null) }
    var spinDone by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = sheetFor != null || menu) { sheetFor = null; menu = false }
    // Who starts, picked at random: a short run around the table that stops on the first player.
    LaunchedEffect(g.uid) {
        if (g.turns.isEmpty() && g.round == 1 && g.elapsed(System.currentTimeMillis()) < 4_000 && g.seats.size > 1) {
            val order = g.seats.map { it.seat }
            val steps = order.size * 2 + order.indexOf(g.firstSeat).coerceAtLeast(0) + 1
            for (i in 0 until steps) {
                spin = order[i % order.size]
                delay(70L + i * 12L)
            }
            spin = null
            spinDone = g.firstSeat
            delay(1_600)
            spinDone = null
        }
    }
    val look = PanelLook(pictures && g.seats.all { it.artUrl != null }, aboveBelow, symbols, lowLife)
    TrackerTable(
        g, look, now,
        delta = { seat -> deltas[seat]?.takeIf { now - it.second < DELTA_MS }?.first },
        highlight = spin ?: spinDone,
        starts = spinDone,
        centre = {
            CentreButton(
                Modifier.align(if (g.seats.size == 2) Alignment.CenterEnd else Alignment.Center).padding(end = if (g.seats.size == 2) 8.dp else 0.dp),
                round = if (g.turnsOn) "R${g.round}" else null,
                clock = if (g.clock) Games.clock(g.elapsed(now)) else null,
                paused = g.paused,
            ) { menu = true }
        },
        onLife = ::changeLife,
        holdOne = holdOne,
        onMore = { sheetFor = it },
        onDead = { seat, yes -> c.games.update { if (yes) LiveGames.knockOut(it, seat, System.currentTimeMillis()) else LiveGames.notDead(it, seat) } },
        onBack = { seat -> c.games.update { LiveGames.bringBack(it, seat) } },
        onNextTurn = { c.games.update { LiveGames.nextTurn(it, System.currentTimeMillis()) } },
    )
    sheetFor?.let { seat ->
        val s = g.seats.firstOrNull { it.seat == seat }
        if (s != null) {
            val turn = seatTurn(g.seats.size, g.seats.indexOf(s))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { sheetFor = null }) {
                Box(Modifier.fillMaxSize().turned(turn).padding(12.dp)) {
                    CountersSheet(g, s, onClose = { sheetFor = null })
                }
            }
        }
    }
    if (menu) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { menu = false }, contentAlignment = Alignment.Center) {
            TableMenu(
                g, now, canUndo,
                onClose = { menu = false },
                onUndo = { c.games.undo() },
                onEnd = { menu = false; nav.navigate("games/end") },
            )
        }
    }
}

private const val DELTA_MS = 2_500L

/** Hides the status and navigation bars and keeps the screen on while the tracker shows. */
@Composable
private fun FullScreen() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** The table: every player's panel around the centre button. */
@Composable
internal fun TrackerTable(
    g: LiveGame,
    look: PanelLook,
    now: Long,
    delta: (Int) -> Int? = { null },
    highlight: Int? = null,
    starts: Int? = null,
    centre: @Composable BoxScope.() -> Unit = {},
    onLife: (Int, Int) -> Unit = { _, _ -> },
    holdOne: Boolean = false,
    onMore: (Int) -> Unit = {},
    onDead: (Int, Boolean) -> Unit = { _, _ -> },
    onBack: (Int) -> Unit = {},
    onNextTurn: () -> Unit = {},
) {
    val seats = g.seats
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        @Composable
        fun pl(i: Int, m: Modifier) {
            val s = seats[i]
            Box(m.turned(seatTurn(seats.size, i))) {
                PlayerPanel(
                    s, g, look, now,
                    delta = delta(s.seat),
                    highlight = highlight == s.seat,
                    starts = starts == s.seat,
                    holdOne = holdOne,
                    onLife = { onLife(s.seat, it) },
                    onMore = { onMore(s.seat) },
                    onDead = { onDead(s.seat, it) },
                    onBack = { onBack(s.seat) },
                    onNextTurn = onNextTurn,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        when (seats.size) {
            1, 2 -> Column(Modifier.fillMaxSize()) {
                seats.indices.forEach { i -> pl(i, Modifier.weight(1f).fillMaxWidth()) }
            }
            3 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1.6f)) {
                    pl(0, Modifier.weight(1f).fillMaxHeight())
                    pl(1, Modifier.weight(1f).fillMaxHeight())
                }
                pl(2, Modifier.weight(1f).fillMaxWidth())
            }
            4 -> Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) { pl(0, Modifier.weight(1f).fillMaxWidth()); pl(3, Modifier.weight(1f).fillMaxWidth()) }
                Column(Modifier.weight(1f)) { pl(1, Modifier.weight(1f).fillMaxWidth()); pl(2, Modifier.weight(1f).fillMaxWidth()) }
            }
            5 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(2.2f)) {
                    Column(Modifier.weight(1f)) { pl(0, Modifier.weight(1f).fillMaxWidth()); pl(4, Modifier.weight(1f).fillMaxWidth()) }
                    Column(Modifier.weight(1f)) { pl(1, Modifier.weight(1f).fillMaxWidth()); pl(2, Modifier.weight(1f).fillMaxWidth()) }
                }
                pl(3, Modifier.weight(1f).fillMaxWidth())
            }
            else -> Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) {
                    pl(0, Modifier.weight(1f).fillMaxWidth()); pl(5, Modifier.weight(1f).fillMaxWidth()); pl(4, Modifier.weight(1f).fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    pl(1, Modifier.weight(1f).fillMaxWidth()); pl(2, Modifier.weight(1f).fillMaxWidth()); pl(3, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        centre()
    }
}

/** The round button in the middle: the round and the game's clock; opens the table menu. */
@Composable
internal fun CentreButton(modifier: Modifier, round: String?, clock: String?, paused: Boolean = false, size: Dp = 70.dp, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color(0xFF15131A)).border(3.dp, Color(0xFFE0A526), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (paused) Icon(Icons.Default.Pause, "Paused", Modifier.size(16.dp), tint = Gold)
            if (round != null) Text(round, color = Color(0xFFFFDF9E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (clock != null) Text(clock, color = Color.White, fontSize = 11.sp)
            if (round == null && clock == null && !paused) Icon(Icons.Default.Menu, "Table menu", tint = Color.White)
        }
    }
}

/** A player's panel: picture or colour, name and deck, life (tap and hold to change), counters, "Dead?". */
@Composable
internal fun PlayerPanel(
    s: LiveSeat,
    g: LiveGame,
    look: PanelLook,
    now: Long,
    delta: Int? = null,
    highlight: Boolean = false,
    starts: Boolean = false,
    holdOne: Boolean = false,
    onLife: (Int) -> Unit = {},
    onMore: () -> Unit = {},
    onDead: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    onNextTurn: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.padding(3.dp).clip(RoundedCornerShape(18.dp))) {
        val small = minOf(maxWidth, maxHeight)
        val lifeSize = (small.value * 0.42f).coerceIn(28f, 130f).sp
        val roomy = small > 150.dp
        // Background
        if (look.pictures && s.artUrl != null) {
            Box(Modifier.fillMaxSize().background(Color(LiveGames.PALETTE[s.colour % LiveGames.PALETTE.size])))
            AsyncImage(s.artUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
        } else {
            Box(Modifier.fillMaxSize().background(Color(LiveGames.PALETTE[s.colour % LiveGames.PALETTE.size])))
        }
        val low = LiveGames.lowLife(s, g.startingLife)
        if (low && look.lowLife == LowLifeStyle.EDGE) PulsingEdge(small)
        if (low && look.lowLife == LowLifeStyle.GHOST) DriftingGhost(small)
        val active = g.turnsOn && g.active == s.seat && !s.out
        if (active) Box(Modifier.fillMaxSize().border(4.dp, Gold, RoundedCornerShape(18.dp)))
        if (highlight) Box(Modifier.fillMaxSize().border(6.dp, Color.White, RoundedCornerShape(18.dp)))

        if (s.out) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)))
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Skull(Modifier.size(small * 0.4f))
                Text(
                    "${s.name} is out" + (s.outRound?.takeIf { g.turnsOn }?.let { " · round $it" } ?: ""),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
            Text(s.name, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
            ) { Text("Undo", fontSize = 12.sp) }
            return@BoxWithConstraints
        }

        // Taps and holds change life: left/right or above/below the total, as the player sees it.
        Box(
            Modifier.fillMaxSize().pointerInput(s.seat, look.aboveBelow, holdOne) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val plus = if (look.aboveBelow) down.position.y < size.height / 2f else down.position.x > size.width / 2f
                    val sign = if (plus) 1 else -1
                    val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() ?: Unit }
                    if (tapped != null) {
                        if (tapped !is Unit) onLife(sign)
                        return@awaitEachGesture
                    }
                    while (true) {
                        onLife(sign * if (holdOne) 1 else 10)
                        val released = withTimeoutOrNull(if (holdOne) 140L else 700L) { waitForUpOrCancellation() ?: Unit }
                        if (released != null) break
                    }
                }
            },
        )
        // Name and deck
        Column(Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 40.dp, end = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (g.monarch == s.seat) Icon(Icons.Default.WorkspacePremium, "Monarch", Modifier.padding(end = 3.dp).size(16.dp), tint = Gold)
                if (g.initiative == s.seat) Icon(Icons.Default.Explore, "Initiative", Modifier.padding(end = 3.dp).size(16.dp), tint = Gold)
                Text(s.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (roomy && s.deckName.isNotBlank()) Text(s.deckName, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Where to tap
        if (look.symbols) {
            val tint = Color.White.copy(alpha = 0.55f)
            if (!look.aboveBelow) {
                Text("−", color = tint, fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
                Text("+", color = tint, fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
            } else {
                Text("+", color = tint, fontSize = 34.sp, modifier = Modifier.align(Alignment.Center).offset(y = -(small * 0.36f)))
                Text("−", color = tint, fontSize = 34.sp, modifier = Modifier.align(Alignment.Center).offset(y = small * 0.32f))
            }
        }
        // Life
        Box(Modifier.align(Alignment.Center)) {
            Text("${s.life}", color = Color.White, fontSize = lifeSize, fontWeight = FontWeight.Bold, lineHeight = lifeSize)
            if (low && look.lowLife == LowLifeStyle.CRACK) Cracks(Modifier.matchParentSize())
            delta?.takeIf { it != 0 }?.let {
                Text(
                    if (it > 0) "+$it" else "−${-it}",
                    color = if (it > 0) Color(0xFFB7F5C4) else Color(0xFFFFC2C2),
                    fontSize = (lifeSize.value * 0.24f).sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (lifeSize.value * 0.45f).dp, y = 4.dp),
                )
            }
        }
        // On a wide panel (a player at a long side) these go beside the total, else below it, clear of the counters.
        val wide = maxWidth > maxHeight * 1.3f
        val asidePlace = if (wide) Modifier.align(Alignment.CenterEnd).padding(end = 12.dp) else Modifier.align(Alignment.Center).offset(y = small * 0.26f)
        if (starts) {
            Text(
                "${s.name} starts!", color = Color.Black, fontWeight = FontWeight.Bold,
                modifier = asidePlace.background(Color.White, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        if (LiveGames.asksDead(s)) {
            val bg = Modifier.background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(20.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            if (wide) Column(asidePlace.then(bg), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Dead?", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = { onDead(true) }) { Text("Yes", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold) }
                TextButton(onClick = { onDead(false) }) { Text("No", color = Color.White) }
            } else Row(asidePlace.then(bg), verticalAlignment = Alignment.CenterVertically) {
                Text("Dead?", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                TextButton(onClick = { onDead(true) }) { Text("Yes", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold) }
                TextButton(onClick = { onDead(false) }) { Text("No", color = Color.White) }
            }
        }
        if (active) {
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Gold).clickable(onClick = onNextTurn)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (g.clock) "Turn ${Games.clock(g.turnElapsed(now))}" else "Your turn", color = Color(0xFF231A00), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.SkipNext, "Next turn", Modifier.size(16.dp), tint = Color(0xFF231A00))
            }
        }
        // Counters, and ⋯ for the rest
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onMore).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sz = if (roomy) 16.dp else 13.dp
            if (g.commanderDamage) CounterBit(Icons.Default.Shield, "${s.maxCommanderDamage}", sz)
            CounterBit(Icons.Default.WaterDrop, "${s.poison}", sz)
            CounterBit(Icons.Default.AddCircleOutline, "${s.tax}", sz)
            g.counters.filter { it in LiveGames.EXTRA_COUNTERS || it.startsWith(LiveGames.CUSTOM) }.forEach { k ->
                s.counters[k]?.let { CounterBit(if (k == "energy") Icons.Default.Bolt else Icons.Default.Star, "$it", sz) }
            }
            Icon(Icons.Default.MoreHoriz, "${s.name}: counters", Modifier.size(sz + 8.dp), tint = Color.White)
        }
    }
}

@Composable
private fun CounterBit(icon: ImageVector, value: String, size: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(size), tint = Color.White.copy(alpha = 0.9f))
        Spacer(Modifier.width(2.dp))
        Text(value, color = Color.White, fontSize = (size.value * 0.8f).sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Low life: a faint ghost drifting up through the panel, over and over. */
@Composable
private fun BoxScope.DriftingGhost(small: Dp) {
    val t = rememberInfiniteTransition(label = "ghost")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4_500, easing = LinearEasing)), label = "rise")
    Ghost(
        Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)
            .graphicsLayer { translationY = -p * (small.toPx() * 1.1f); alpha = (if (p < 0.15f) p / 0.15f else 1f) * (1f - p) }
            .size(small * 0.26f, small * 0.34f),
    )
}

/** Low life: a slowly pulsing dark red edge. */
@Composable
private fun PulsingEdge(small: Dp) {
    val t = rememberInfiniteTransition(label = "edge")
    val a by t.animateFloat(0.35f, 0.85f, infiniteRepeatable(tween(1_100), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = a }.background(Brush.radialGradient(listOf(Color.Transparent, Color(0xFFB00020)), radius = small.value * 2.2f)))
}

@Composable
internal fun Ghost(modifier: Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(0f, h * 0.45f)
        arcTo(Rect(0f, 0f, w, w), 180f, 180f, false)
        lineTo(w, h)
        val n = 4
        val step = w / n
        for (i in 0 until n) {
            val x = w - i * step
            quadraticBezierTo(x - step / 2, h - step * 0.6f, x - step, h)
        }
        close()
    }
    drawPath(path, Color.White.copy(alpha = 0.32f))
    drawOval(Color.Black.copy(alpha = 0.45f), Offset(w * 0.25f, w * 0.35f), Size(w * 0.16f, w * 0.22f))
    drawOval(Color.Black.copy(alpha = 0.45f), Offset(w * 0.58f, w * 0.35f), Size(w * 0.16f, w * 0.22f))
}

/** Low life: cracks across the life total. */
@Composable
private fun Cracks(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "crack")
    val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "crack")
    Canvas(modifier.graphicsLayer { alpha = a }) {
        val w = size.width
        val h = size.height
        val crack = Path().apply {
            moveTo(w * 0.15f, h * 0.2f); lineTo(w * 0.38f, h * 0.45f); lineTo(w * 0.3f, h * 0.55f); lineTo(w * 0.62f, h * 0.8f); lineTo(w * 0.85f, h * 0.9f)
            moveTo(w * 0.38f, h * 0.45f); lineTo(w * 0.55f, h * 0.38f)
        }
        drawPath(crack, Color.Black.copy(alpha = 0.75f), style = Stroke(width = 7f))
        drawPath(crack, Color.White.copy(alpha = 0.3f), style = Stroke(width = 2f))
    }
}

/** The big skull on an out player's panel. */
@Composable
internal fun Skull(modifier: Modifier, colour: Color = Color.White.copy(alpha = 0.85f)) = Canvas(modifier) {
    val w = size.width
    drawCircle(colour, w * 0.36f, Offset(w / 2, w * 0.4f))
    drawRoundRect(colour, Offset(w * 0.3f, w * 0.55f), Size(w * 0.4f, w * 0.32f), CornerRadius(w * 0.07f))
    drawOval(Color.Black, Offset(w * 0.27f, w * 0.33f), Size(w * 0.18f, w * 0.2f))
    drawOval(Color.Black, Offset(w * 0.55f, w * 0.33f), Size(w * 0.18f, w * 0.2f))
    val nose = Path().apply { moveTo(w * 0.5f, w * 0.56f); lineTo(w * 0.45f, w * 0.66f); lineTo(w * 0.55f, w * 0.66f); close() }
    drawPath(nose, Color.Black)
    for (i in 1..3) {
        val x = w * (0.3f + i * 0.1f)
        drawLine(Color.Black, Offset(x, w * 0.74f), Offset(x, w * 0.87f), strokeWidth = w * 0.025f)
    }
}

/** A player's counters (⋯ on the panel), facing them: commander damage taken, poison, tax and the extras. */
@Composable
private fun CountersSheet(g: LiveGame, s: LiveSeat, onClose: () -> Unit) {
    val c = LocalContext.current.container
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(SheetBg).clickable(enabled = false) {}.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s.name} · counters", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 480.dp
            val left: @Composable ColumnScope.() -> Unit = {
                if (g.commanderDamage) {
                    SheetTitle("Commander damage taken")
                    g.seats.filter { it.seat != s.seat }.forEach { o ->
                        val label = o.commander.ifBlank { o.name } + " (${o.name})"
                        Stepper(label, "${s.cmd[LiveGames.cmdKey(o.seat)] ?: 0}") { by -> c.games.update { LiveGames.commanderDamage(it, s.seat, LiveGames.cmdKey(o.seat), by) } }
                        o.partner?.let { p ->
                            Stepper("$p (${o.name})", "${s.cmd[LiveGames.cmdKey(o.seat, true)] ?: 0}") { by -> c.games.update { LiveGames.commanderDamage(it, s.seat, LiveGames.cmdKey(o.seat, true), by) } }
                        }
                    }
                    Text("Combat damage from a commander; it takes life too. 21 from one commander: out.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
            val right: @Composable ColumnScope.() -> Unit = {
                SheetTitle("Counters")
                Stepper("Poison", "${s.poison}") { by -> c.games.update { LiveGames.poison(it, s.seat, by) } }
                Stepper("Commander tax" + (s.commander.takeIf { s.partner != null && it.isNotBlank() }?.let { " ($it)" } ?: ""), "+${s.tax}") { by -> c.games.update { LiveGames.casts(it, s.seat, by) } }
                s.partner?.let { p -> Stepper("Commander tax ($p)", "+${s.partnerTax}") { by -> c.games.update { LiveGames.casts(it, s.seat, by, partner = true) } } }
                g.counters.filter { it in LiveGames.EXTRA_COUNTERS || it.startsWith(LiveGames.CUSTOM) }.forEach { k ->
                    Stepper(LiveGames.counterLabel(k), "${s.counters[k] ?: 0}") { by -> c.games.update { LiveGames.counter(it, s.seat, k, by) } }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { c.games.update { LiveGames.knockOut(it, s.seat, System.currentTimeMillis(), OutReason.CONCEDED) }; onClose() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("${s.name} concedes") }
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = left)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = right)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    left()
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    right()
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) = Text(text, color = Color(0xFFFFDF9E), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))

@Composable
private fun Stepper(label: String, value: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        FilledTonalIconButton(onClick = { onChange(-1) }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Remove, "One less") }
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
        FilledTonalIconButton(onClick = { onChange(1) }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Add, "One more") }
    }
}

/** The centre button's menu: turns, pause, undo, table items, a die, a coin, who starts, ending the game. */
@Composable
private fun TableMenu(g: LiveGame, now: Long, canUndo: Boolean, onClose: () -> Unit, onUndo: () -> Unit, onEnd: () -> Unit) {
    val c = LocalContext.current.container
    var picking by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.widthIn(max = 340.dp).padding(16.dp).clip(RoundedCornerShape(24.dp)).background(SheetBg).clickable(enabled = false) {}.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            listOfNotNull(if (g.turnsOn) "Round ${g.round}" else null, "game ${Games.clock(g.elapsed(now))}").joinToString(" · "),
            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        )
        if (g.turnsOn) g.seats.firstOrNull { it.seat == g.active }?.let {
            Text("${it.name}'s turn ${Games.clock(g.turnElapsed(now))}", color = Color(0xFFFFDF9E), fontSize = 13.sp)
        }
        result?.let { Text(it, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        val pick = picking
        if (pick != null) {
            Text(if (pick == LiveGames.MONARCH) "Who's the monarch?" else "Who has the initiative?", color = Color.White, modifier = Modifier.padding(top = 10.dp))
            g.alive.forEach { s ->
                TextButton(onClick = {
                    c.games.update { if (pick == LiveGames.MONARCH) LiveGames.setMonarch(it, s.seat) else LiveGames.setInitiative(it, s.seat) }
                    picking = null
                }) { Text(s.name, color = Color.White) }
            }
            TextButton(onClick = {
                c.games.update { if (pick == LiveGames.MONARCH) LiveGames.setMonarch(it, null) else LiveGames.setInitiative(it, null) }
                picking = null
            }) { Text("Nobody", color = Color.White.copy(alpha = 0.7f)) }
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        val buttons = buildList<Triple<ImageVector, String, () -> Unit>> {
            if (g.turnsOn) add(Triple(Icons.Default.SkipNext, "Next turn") { c.games.update { LiveGames.nextTurn(it, System.currentTimeMillis()) } })
            add(
                if (g.paused) Triple(Icons.Default.PlayArrow, "Go on") { c.games.update(undoable = false) { LiveGames.resume(it, System.currentTimeMillis()) } }
                else Triple(Icons.Default.Pause, "Pause") { c.games.update(undoable = false) { LiveGames.pause(it, System.currentTimeMillis()) } }
            )
            if (canUndo) add(Triple(Icons.AutoMirrored.Filled.Undo, "Undo") { onUndo() })
            if (LiveGames.MONARCH in g.counters) add(Triple(Icons.Default.WorkspacePremium, "Monarch") { picking = LiveGames.MONARCH })
            if (LiveGames.INITIATIVE in g.counters) add(Triple(Icons.Default.Explore, "Initiative") { picking = LiveGames.INITIATIVE })
            if (LiveGames.DAY_NIGHT in g.counters) add(
                Triple(if (g.dayNight == "night") Icons.Default.DarkMode else Icons.Default.WbSunny, when (g.dayNight) { null -> "Make it day"; "day" -> "Day → night"; else -> "Night → day" }) {
                    c.games.update { LiveGames.toggleDayNight(it) }
                }
            )
            add(Triple(Icons.Default.Casino, "Roll a die") { result = "🎲 ${Random.nextInt(1, 7)}" })
            add(Triple(Icons.Default.Casino, "Roll a d20") { result = "d20: ${Random.nextInt(1, 21)}" })
            add(Triple(Icons.Default.Paid, "Flip a coin") { result = if (Random.nextBoolean()) "Heads" else "Tails" })
            add(Triple(Icons.Default.Shuffle, "Random player") { result = g.alive.randomOrNull()?.name })
        }
        buttons.chunked(3).forEach { row ->
            Row {
                row.forEach { (icon, label, action) ->
                    Column(Modifier.width(92.dp).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledTonalIconButton(onClick = action, modifier = Modifier.size(52.dp)) { Icon(icon, null) }
                        Text(label, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
        if (g.dayNight != null && LiveGames.DAY_NIGHT in g.counters) {
            Text("It's ${g.dayNight}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Flag, null)
            Spacer(Modifier.width(6.dp))
            Text("End the game")
        }
        TextButton(onClick = onClose) { Text("Back to the game") }
    }
}
