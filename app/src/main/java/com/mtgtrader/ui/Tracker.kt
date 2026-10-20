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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.mtgtrader.container
import com.mtgtrader.data.CardLayout
import com.mtgtrader.data.Games
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.LowLifeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal val TurnGold = Color(0xFFFFC94D)
/** The tracker's own highlight colour, as Lifetap's (since 1.48). */
internal val TrackerAccent = Color(0xFF6DD3FF)
/** Text on the soft panel colours. */
internal val PanelInk = Color(0xFF111111)

/** A seat's colour: a soft fade from [LiveGames.PALETTE] to [LiveGames.PALETTE_END]. */
internal fun seatBrush(colour: Int): Brush {
    val i = colour.mod(LiveGames.PALETTE.size)
    return Brush.linearGradient(listOf(Color(LiveGames.PALETTE[i]), Color(LiveGames.PALETTE_END[i])))
}

/** How the panels look and react: the tracker's settings. */
internal data class PanelLook(
    val pictures: Boolean = false,
    val aboveBelow: Boolean = false,
    val lowLife: LowLifeStyle = LowLifeStyle.GHOST,
    val layout: CardLayout = CardLayout.DEFAULT,
    val colourGrids: Boolean = true,
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
 * With [ends] (4 and 6 players, since 1.48) the first player sits at the top end and one more at the bottom end.
 */
internal fun seatTurn(players: Int, index: Int, ends: Boolean = false): Int = when {
    ends && players == 4 -> when (index) { 0 -> 180; 1 -> -90; 2 -> 0; else -> 90 }
    ends && players == 6 -> when (index) { 0 -> 180; 1, 2 -> -90; 3 -> 0; else -> 90 }
    players == 2 -> if (index == 0) 180 else 0
    players == 3 -> when (index) { 0 -> 90; 1 -> -90; else -> 0 }
    players == 4 -> if (index == 0 || index == 3) 90 else -90
    players == 5 -> when (index) { 0, 4 -> 90; 3 -> 0; else -> -90 }
    else -> if (index == 0 || index >= 4) 90 else -90
}

/** The seats as they lie on the screen: rows of seat indexes, top to bottom (a seat twice in a row spans it). */
internal fun arrangement(players: Int, ends: Boolean = false): List<List<Int>> = when {
    ends && players == 4 -> listOf(listOf(0, 0), listOf(3, 1), listOf(2, 2))
    ends && players == 6 -> listOf(listOf(0, 0), listOf(5, 1), listOf(4, 2), listOf(3, 3))
    players <= 1 -> listOf(listOf(0))
    players == 2 -> listOf(listOf(0), listOf(1))
    players == 3 -> listOf(listOf(0, 1), listOf(2, 2))
    players == 4 -> listOf(listOf(0, 1), listOf(3, 2))
    players == 5 -> listOf(listOf(0, 1), listOf(4, 2), listOf(3, 3))
    else -> listOf(listOf(0, 1), listOf(5, 2), listOf(4, 3))
}

/** The row the middle bar sits under: the gap nearest the middle of the screen. */
internal fun barAfterRow(players: Int, ends: Boolean): Int = if (ends && players == 6) 1 else 0

private fun rowWeight(players: Int, ends: Boolean, row: Int, rows: Int): Float = when {
    ends && players in setOf(4, 6) && (row == 0 || row == rows - 1) -> 0.75f
    players == 3 && row == 0 -> 1.4f
    else -> 1f
}

/** Which of the tracker's overlays is open: a player's sheet, a page from the middle bar, the end of the game. */
private data class SheetFor(val seat: Int, val counters: Boolean)

internal enum class TrackerPage { SETTINGS, DICE, MANA, HISTORY }

/** A die roll, coin flip or pick shown over the table. */
internal data class Roll(val title: String, val value: String, val sides: Int = 0)

/** The life tracker (since 1.47; laid out as Lifetap since 1.48): one panel per player, a menu bar across the middle. */
@Composable
fun TrackerScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val game by c.games.live.collectAsStateWithLifecycle()
    val canUndo by c.games.canUndo.collectAsStateWithLifecycle()
    val pictures by c.settings.trackerPictures.collectAsStateWithLifecycle()
    val aboveBelow by c.settings.trackerAboveBelow.collectAsStateWithLifecycle()
    val holdOne by c.settings.trackerHoldOne.collectAsStateWithLifecycle()
    val lowLife by c.settings.trackerLowLife.collectAsStateWithLifecycle()
    val cardLayout by c.settings.trackerCardLayout.collectAsStateWithLifecycle()
    val colourGrids by c.settings.trackerFlag("trackerColourGrids").collectAsStateWithLifecycle()
    val haptics by c.settings.trackerFlag("trackerHaptics").collectAsStateWithLifecycle()
    val autoHide by c.settings.trackerFlag("trackerAutoHide").collectAsStateWithLifecycle()
    val animations by c.settings.trackerFlag("trackerAnimations").collectAsStateWithLifecycle()
    val keepAwake by c.settings.trackerFlag("trackerKeepAwake").collectAsStateWithLifecycle()
    val hideBars by c.settings.trackerFlag("trackerHideBars").collectAsStateWithLifecycle()
    FullScreen(keepAwake, hideBars)
    // Set when the game ends from here, so the screen doesn't step back on its own meanwhile.
    var leaving by remember { mutableStateOf(false) }
    val g = game
    if (g == null) {
        if (!leaving) LaunchedEffect(Unit) { nav.safePopBackStack() }
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
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        c.games.update { LiveGames.changeLife(it, seat, by) }
    }
    var sheet by remember { mutableStateOf<SheetFor?>(null) }
    var page by remember { mutableStateOf<TrackerPage?>(null) }
    var ending by remember { mutableStateOf(false) }
    var roll by remember { mutableStateOf<Roll?>(null) }
    var spin by remember { mutableStateOf<Int?>(null) }
    var banner by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var barUntil by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = sheet != null || page != null || ending || roll != null) {
        when {
            roll != null -> roll = null
            ending -> ending = false
            sheet != null -> sheet = null
            else -> page = null
        }
    }
    /** A run around the table (when animations are on) that slows down and stops on [pick], then [label] on its panel. */
    fun spinTo(order: List<Int>, pick: Int, label: String) {
        scope.launch {
            if (animations && order.size > 1) {
                spinDelays(spinSteps(order.size, order.indexOf(pick).coerceAtLeast(0))).forEachIndexed { i, wait ->
                    spin = order[i % order.size]
                    delay(wait)
                }
            }
            spin = null
            banner = pick to label
            delay(1_800)
            banner = null
        }
    }
    // Who starts, picked at random: the run around the table stops on the first player.
    LaunchedEffect(g.uid) {
        if (g.turns.isEmpty() && g.round == 1 && g.elapsed(System.currentTimeMillis()) < 4_000 && g.seats.size > 1) {
            val first = g.seats.firstOrNull { it.seat == g.firstSeat } ?: return@LaunchedEffect
            spinTo(g.seats.map { it.seat }, first.seat, "${first.name} starts!")
        }
    }
    val look = PanelLook(pictures, aboveBelow, lowLife, cardLayout, colourGrids)
    TrackerTable(
        g, look, now,
        delta = { seat -> deltas[seat]?.takeIf { now - it.second < DELTA_MS }?.first },
        highlight = spin,
        banner = banner,
        bar = {
            MenuBar(
                g, now, hidden = autoHide && now > barUntil,
                onShow = { barUntil = System.currentTimeMillis() + BAR_SHOWN_MS },
                onEnd = { ending = true },
                onSettings = { page = TrackerPage.SETTINGS },
                onPause = { c.games.update(undoable = false) { if (it.paused) LiveGames.resume(it, System.currentTimeMillis()) else LiveGames.pause(it, System.currentTimeMillis()) } },
                onDayNight = { c.games.update { LiveGames.toggleDayNight(it) } },
                onHistory = { page = TrackerPage.HISTORY },
                onUndo = { if (c.games.undo() && haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onMana = { page = TrackerPage.MANA },
                onDice = { page = TrackerPage.DICE },
                onQuickRoll = { roll = Roll("d20", "${Random.nextInt(1, 21)}", 20) },
            )
        },
        onLife = ::changeLife,
        holdOne = holdOne,
        onSheet = { seat, counters -> sheet = SheetFor(seat, counters) },
        onDead = { seat, yes -> c.games.update { if (yes) LiveGames.knockOut(it, seat, System.currentTimeMillis()) else LiveGames.notDead(it, seat) } },
        onBack = { seat -> c.games.update { LiveGames.bringBack(it, seat) } },
        onNextTurn = { c.games.update { LiveGames.nextTurn(it, System.currentTimeMillis()) } },
    )
    sheet?.let { sh ->
        if (g.seats.none { it.seat == sh.seat }) sheet = null
        else PlayerSheet(g, sh.seat, look, startOnCounters = sh.counters, onClose = { sheet = null })
    }
    when (page) {
        TrackerPage.SETTINGS -> GameSettingsPage(g, onClose = { page = null })
        TrackerPage.DICE -> DicePage(
            g,
            onClose = { page = null },
            onRoll = { page = null; roll = it },
            onRandomPlayer = {
                page = null
                g.alive.randomOrNull()?.let { p -> spinTo(g.alive.map { it.seat }, p.seat, "${p.name}!") }
            },
            onRandomOpponent = {
                page = null
                // An opponent for the player whose turn it is (or, without turns, for the phone's owner).
                val forSeat = if (g.turnsOn) g.active else g.seats.firstOrNull { it.me }?.seat
                val pool = g.alive.filter { it.seat != forSeat }.ifEmpty { g.alive }
                pool.randomOrNull()?.let { p ->
                    val who = g.seats.firstOrNull { it.seat == forSeat }?.name
                    spinTo(pool.map { it.seat }, p.seat, if (who != null) "$who's opponent: ${p.name}" else "${p.name}!")
                }
            },
        )
        TrackerPage.MANA -> ManaPage(g, onClose = { page = null })
        TrackerPage.HISTORY -> HistoryPage(g, now, canUndo, nav, onClose = { page = null })
        null -> {}
    }
    if (ending) EndGameDialog(g, onCancel = { ending = false }, onLeave = { leaving = true }, nav = nav, onDone = { ending = false })
    roll?.let { RollOverlay(it, animate = animations, onAgain = { roll = it.copy(value = reroll(it)) }) { roll = null } }
}

private const val DELTA_MS = 2_500L

/** How long the run around the table takes (since 1.49, 2 seconds more than before). */
internal const val SPIN_MS = 3_300L

/** The steps of a run around [players] seats that ends on the seat at [pickIndex]: at least a dozen, whole laps first. */
internal fun spinSteps(players: Int, pickIndex: Int): Int {
    val laps = maxOf(2, (12 + players - 1) / players)
    return laps * players + pickIndex + 1
}

/** How long each of [steps] stays lit: fast at first, slowing down like a wheel (the last ten times the first), [SPIN_MS] in all. */
internal fun spinDelays(steps: Int): List<Long> {
    if (steps <= 0) return emptyList()
    val weights = List(steps) { i -> val x = if (steps == 1) 1.0 else i / (steps - 1.0); 1.0 + 9.0 * x * x * x }
    val unit = SPIN_MS / weights.sum()
    return weights.map { (it * unit).toLong() }
}
private const val BAR_SHOWN_MS = 6_000L

/** The same kind of roll again: a die, the coin, or nothing for a pick. */
internal fun reroll(r: Roll): String = when {
    r.sides == 2 -> if (Random.nextBoolean()) "Heads" else "Tails"
    r.sides > 2 -> "${Random.nextInt(1, r.sides + 1)}"
    else -> r.value
}

/** Hides the status and navigation bars and keeps the screen on while the tracker shows (each can be switched off). */
@Composable
private fun FullScreen(keepAwake: Boolean, hideBars: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepAwake, hideBars) {
        view.keepScreenOn = keepAwake
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideBars) controller?.hide(WindowInsetsCompat.Type.systemBars()) else controller?.show(WindowInsetsCompat.Type.systemBars())
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

/** The table: every player's panel, with the menu bar across the middle. */
@Composable
internal fun TrackerTable(
    g: LiveGame,
    look: PanelLook,
    now: Long,
    delta: (Int) -> Int? = { null },
    highlight: Int? = null,
    banner: Pair<Int, String>? = null,
    bar: @Composable () -> Unit = {},
    onLife: (Int, Int) -> Unit = { _, _ -> },
    holdOne: Boolean = false,
    onSheet: (Int, Boolean) -> Unit = { _, _ -> },
    onDead: (Int, Boolean) -> Unit = { _, _ -> },
    onBack: (Int) -> Unit = {},
    onNextTurn: () -> Unit = {},
) {
    val seats = g.seats
    val n = seats.size
    val rows = arrangement(n, g.ends)
    val barAfter = barAfterRow(n, g.ends)
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 2.dp, vertical = 4.dp)) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.weight(rowWeight(n, g.ends, r, rows.size)).fillMaxWidth()) {
                row.distinct().forEach { i ->
                    val s = seats[i]
                    Box(Modifier.weight(row.count { it == i }.toFloat()).fillMaxHeight().turned(seatTurn(n, i, g.ends))) {
                        PlayerPanel(
                            s, g, look, now,
                            delta = delta(s.seat),
                            highlight = highlight == s.seat,
                            banner = banner?.takeIf { it.first == s.seat }?.second,
                            holdOne = holdOne,
                            onLife = { onLife(s.seat, it) },
                            onSheet = { onSheet(s.seat, it) },
                            onDead = { onDead(s.seat, it) },
                            onBack = { onBack(s.seat) },
                            onNextTurn = onNextTurn,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            if (r == barAfter) bar()
        }
    }
}

/**
 * The bar across the middle of the table: end the game, the game's settings (the number of players), the clock
 * (tap to pause), day/night, history (hold: undo), the mana pool and the dice (hold: a d20 at once).
 */
@Composable
internal fun MenuBar(
    g: LiveGame,
    now: Long,
    hidden: Boolean = false,
    onShow: () -> Unit = {},
    onEnd: () -> Unit = {},
    onSettings: () -> Unit = {},
    onPause: () -> Unit = {},
    onDayNight: () -> Unit = {},
    onHistory: () -> Unit = {},
    onUndo: () -> Unit = {},
    onMana: () -> Unit = {},
    onDice: () -> Unit = {},
    onQuickRoll: () -> Unit = {},
) {
    if (hidden) {
        Box(Modifier.fillMaxWidth().height(16.dp).clickable(onClick = onShow), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(Color(0xFF555555)))
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        BarIcon(Icons.Outlined.Replay, "End the game", onTap = onEnd)
        BarIcon(Icons.Outlined.Settings, "Game settings", onTap = onSettings)
        if (g.turnsOn || g.clock) {
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF555555), RoundedCornerShape(14.dp)).clickable(onClick = onPause)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (g.paused) Icon(Icons.Default.Pause, "Paused", Modifier.size(15.dp), tint = TurnGold)
                Text(
                    listOfNotNull(if (g.turnsOn) "R${g.round}" else null, if (g.clock) Games.clock(g.elapsed(now)) else null).joinToString(" · "),
                    color = Color.White, fontSize = 13.sp,
                )
            }
        }
        if (g.dayNight != null) BarIcon(if (g.dayNight == "night") Icons.Default.DarkMode else Icons.Default.WbSunny, "It's ${g.dayNight}: tap to change", size = 26.dp, onTap = onDayNight)
        BarIcon(Icons.Outlined.History, "History (hold: undo)", onTap = onHistory, onHold = onUndo)
        ManaDots(Modifier.clip(CircleShape).clickable(onClick = onMana))
        BarIcon(Icons.Outlined.Casino, "Dice (hold: roll a d20)", onTap = onDice, onHold = onQuickRoll)
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, size: Dp = 32.dp, onTap: () -> Unit, onHold: (() -> Unit)? = null) {
    Icon(
        icon, label,
        Modifier.clip(CircleShape).pointerInput(onTap, onHold) { detectTapGestures(onTap = { onTap() }, onLongPress = onHold?.let { h -> { h() } }) }.padding(4.dp).size(size),
        tint = Color.White,
    )
}

/** Five coloured dots, as Lifetap's mana button. */
@Composable
private fun ManaDots(modifier: Modifier) = Canvas(modifier.size(40.dp).padding(2.dp)) {
    val r = size.width * 0.11f
    val colours = listOf(Color.White, Color(0xFF4A9DE0), Color(0xFF888888), Color(0xFFE5483C), Color(0xFF52B05A))
    for (k in 0 until 5) {
        val a = Math.toRadians(-90.0 + k * 72.0)
        val at = Offset(center.x + cos(a).toFloat() * size.width * 0.32f, center.y + sin(a).toFloat() * size.width * 0.32f)
        if (k == 2) drawCircle(Color.White, r, at, style = Stroke(2f)) else drawCircle(colours[k], r, at)
    }
}

/** The badge icons of a player's counters, as on the counter tiles. */
internal fun counterIcon(key: String): ImageVector = when (key) {
    "energy" -> Icons.Outlined.Bolt
    "experience" -> Icons.Outlined.AutoAwesome
    "treasure" -> Icons.Outlined.Diamond
    "storm" -> Icons.Outlined.Thunderstorm
    LiveGames.ASCEND -> Icons.Outlined.AccountBalance
    else -> Icons.Outlined.Star
}

/**
 * A player's panel (as Lifetap's since 1.48): their name on a label (tap: their sheet), the life total with room
 * around it (tap and hold to change), − and + at the ends, the commander damage mini-grid (tap: their counters) and
 * badges for counters in use; "Dead?", the turn and low life on top.
 */
@Composable
internal fun PlayerPanel(
    s: LiveSeat,
    g: LiveGame,
    look: PanelLook,
    now: Long,
    delta: Int? = null,
    highlight: Boolean = false,
    banner: String? = null,
    holdOne: Boolean = false,
    onLife: (Int) -> Unit = {},
    onSheet: (counters: Boolean) -> Unit = {},
    onDead: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    onNextTurn: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(modifier.padding(3.dp).clip(shape)) {
        val small = minOf(maxWidth, maxHeight)
        val wide = maxWidth > maxHeight * 1.3f
        val picture = (s.picture ?: look.pictures) && s.artUrl != null
        val ink = if (picture) Color.White else PanelInk
        val pill = if (picture) Color.Black.copy(alpha = if (s.contrast) 0.62f else 0.38f) else Color.White.copy(alpha = if (s.contrast) 0.85f else 0.5f)
        // Background
        if (s.out) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A3F47), Color(0xFF25282E)))))
        } else {
            Box(Modifier.fillMaxSize().background(seatBrush(s.colour)))
            if (picture) {
                AsyncImage(s.artUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (s.contrast) 0.58f else 0.36f)))
            }
        }
        // Where to tap: − and + at the ends, with room for them beside the total.
        val symbol = (small.value * 0.17f).coerceIn(22f, 40f).sp
        val sideRoom = (symbol.value * 1.3f + 18f).dp
        // A short, wide panel (at an end of a 5–6 player table) has the mini-grid beside the total, so the total keeps
        // its room. Since 1.51 only when it really is short: the long-side panels of a narrower phone stay centred.
        val beside = look.layout == CardLayout.DEFAULT && g.commanderDamage && wide && maxHeight < miniGridSize(g, s).second + 110.dp
        val low = LiveGames.lowLife(s, g.startingLife)
        if (low && look.lowLife == LowLifeStyle.EDGE) PulsingEdge(small)
        if (low && look.lowLife == LowLifeStyle.GHOST) DriftingGhost(small, if (picture) Color.White else PanelInk, sideRoom, onPlusSide = !beside)
        val active = g.turnsOn && g.active == s.seat && !s.out
        if (active) Box(Modifier.fillMaxSize().border(4.dp, TurnGold, shape))
        if (highlight) Box(Modifier.fillMaxSize().border(6.dp, if (picture) Color.White else PanelInk, shape))

        if (s.out) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Skull(Modifier.size(small * 0.36f))
                Text(
                    "${s.name} is out" + (s.outRound?.takeIf { g.turnsOn }?.let { " · round $it" } ?: ""),
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
            Text(
                "Undo", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 5.dp),
            )
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
        if (!look.aboveBelow) {
            Text("−", color = ink, fontSize = symbol, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
            Text("+", color = ink, fontSize = symbol, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
        } else {
            Text("+", color = ink, fontSize = symbol, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 6.dp))
            Text("−", color = ink, fontSize = symbol, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 6.dp))
        }
        // The label, the total and the mini-grid, one above the other with room between them.
        val panelWidth = maxWidth
        Column(Modifier.fillMaxSize().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (look.layout == CardLayout.BIG) {
                Box(Modifier.clip(CircleShape).clickable { onSheet(false) }.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Box(Modifier.size(34.dp, 5.dp).clip(CircleShape).background(ink.copy(alpha = 0.35f)))
                }
            } else {
                // Whose turn it is shows at the start of the label (since 1.52; it was in the corner, over the label).
                NameLabel(s, g, ink, pill, panelWidth * 0.72f, roomy = small > 150.dp, turn = if (active) turnText(g, now) else null, onNextTurn = onNextTurn) { onSheet(false) }
            }
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = sideRoom), verticalAlignment = Alignment.CenterVertically) {
            // Beside: the total stays in the middle of the panel, the grid right after it (since 1.51; it was by the +).
            if (beside) Spacer(Modifier.weight(1f))
            BoxWithConstraints((if (beside) Modifier else Modifier.weight(1f)).fillMaxHeight().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                val big = look.layout == CardLayout.BIG
                val digits = "${s.life}".length.coerceAtLeast(2)
                val font = minOf(maxHeight * if (big) 1f else 0.9f, maxWidth / (0.6f * digits), small * if (big) 0.62f else 0.42f).coerceAtLeast(16.dp)
                val fontSp = with(LocalDensity.current) { font.toSp() }
                Box {
                    Text(
                        "${s.life}", color = ink, fontSize = fontSp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
                        style = TextStyle(
                            lineHeight = fontSp, platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        ),
                    )
                    if (low && look.lowLife == LowLifeStyle.CRACK) Cracks(Modifier.matchParentSize())
                    delta?.takeIf { it != 0 }?.let {
                        Text(
                            if (it > 0) "+$it" else "−${-it}",
                            color = if (picture) (if (it > 0) Color(0xFFB7F5C4) else Color(0xFFFFC2C2)) else (if (it > 0) Color(0xFF1E7A3A) else Color(0xFFB3261E)),
                            fontSize = (fontSp.value * 0.24f).sp, fontWeight = FontWeight.Bold,
                            // Beside the grid it goes on the total's other side, where there's room.
                            modifier = if (beside) Modifier.align(Alignment.TopStart).offset(x = -(font * 0.36f), y = -(font * 0.05f))
                            else Modifier.align(Alignment.TopEnd).offset(x = font * 0.36f, y = -(font * 0.05f)),
                        )
                    }
                }
            }
            if (beside) Box(Modifier.weight(1f).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
                // Counter badges may reach a little into the room by the +, rather than being squeezed.
                Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true)) { BottomRow(s, g, look, ink, pill, beside = true) { onSheet(true) } }
            }
            }
            if (look.layout == CardLayout.DEFAULT && !beside) BottomRow(s, g, look, ink, pill) { onSheet(true) }
        }
        // On a wide panel (a player at a long side) these go beside the total, else below it.
        val asidePlace = if (wide) Modifier.align(Alignment.CenterEnd).padding(end = sideRoom) else Modifier.align(Alignment.Center).offset(y = small * 0.28f)
        if (banner != null) {
            Text(
                banner, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).widthIn(max = maxWidth * 0.8f).background(Color(0xE0111111), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (LiveGames.asksDead(g, s)) {
            Column(
                asidePlace.clip(RoundedCornerShape(16.dp)).background(Color(0xE6111111)).padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Dead?", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Yes", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDead(true) }.padding(10.dp))
                    Text("No", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDead(false) }.padding(10.dp))
                }
            }
        }
        // The big-number layout has no name label: there the badge stays in the corner.
        if (active && look.layout == CardLayout.BIG) {
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(TurnGold).clickable(onClick = onNextTurn)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (g.clock) "Turn ${turnText(g, now)}" else "Your turn", color = Color(0xFF231A00), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.SkipNext, "Next turn", Modifier.size(16.dp), tint = Color(0xFF231A00))
            }
        }
    }
}

/** The turn's time for the turn badge ("1:42"), or "Turn" without the clock. */
private fun turnText(g: LiveGame, now: Long) = if (g.clock) Games.clock(g.turnElapsed(now)) else "Turn"

/**
 * The name on its label (with the deck, when there's room); the monarch's crown and the initiative go before it.
 * [turn]: the gold turn badge at the start, while it's this player's turn (tap: next turn); the deck name gives way
 * to it on small panels. Since 1.52.
 */
@Composable
private fun NameLabel(
    s: LiveSeat,
    g: LiveGame,
    ink: Color,
    pill: Color,
    maxWidth: Dp,
    roomy: Boolean,
    turn: String? = null,
    onNextTurn: () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        Modifier.widthIn(max = maxWidth).clip(RoundedCornerShape(14.dp)).background(pill).clickable(onClick = onClick)
            .padding(start = if (turn != null) 5.dp else 14.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (turn != null) {
            Row(
                Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp)).background(TurnGold).clickable(onClick = onNextTurn)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(turn, color = Color(0xFF231A00), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Icon(Icons.Default.SkipNext, "Next turn", Modifier.size(15.dp), tint = Color(0xFF231A00))
            }
        }
        if (g.monarch == s.seat) Icon(Icons.Default.WorkspacePremium, "Monarch", Modifier.padding(end = 4.dp).size(17.dp), tint = ink)
        if (g.initiative == s.seat) Icon(Icons.Default.Explore, "Initiative", Modifier.padding(end = 4.dp).size(17.dp), tint = ink)
        Text(s.name, color = ink, fontSize = 16.sp, fontWeight = if (s.contrast) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = maxWidth * 0.55f))
        if (roomy && s.deckName.isNotBlank()) {
            Text("  ${s.deckName}", color = ink.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The mini-grid's size as [s]'s player sees it (the grid itself lies as the table does). */
private fun miniGridSize(g: LiveGame, s: LiveSeat): Pair<Dp, Dp> {
    val n = g.seats.size
    val rows = arrangement(n, g.ends)
    val cell = if (n >= 5) 19.dp else 22.dp
    val sw = cell * rows.maxOf { it.size } + 2.dp * (rows.maxOf { it.size } - 1)
    val sh = cell * rows.size + 2.dp * (rows.size - 1)
    val sideways = seatTurn(n, g.seats.indexOf(s), g.ends) % 180 != 0
    return if (sideways) sh to sw else sw to sh
}

/** The space around the mini-grid inside its frame (since 1.49, as Lifetap's). */
private val GRID_FRAME = 5.dp

/**
 * The mini-grid in its frame, centred under the total, and the badges of the counters in use hanging beside it (since
 * 1.49; [beside]: both next to the total, on a short, wide panel). Tap either for the counters.
 */
@Composable
private fun BottomRow(s: LiveSeat, g: LiveGame, look: PanelLook, ink: Color, pill: Color, beside: Boolean = false, onClick: () -> Unit) {
    val badges = buildList {
        if (s.poison > 0) add(Icons.Outlined.WaterDrop to "${s.poison}")
        if (s.casts > 0 || s.partnerCasts > 0) add(Icons.Outlined.AddCircleOutline to (if (s.hasPartners) "+${s.tax}/+${s.partnerTax}" else "+${s.tax}"))
        s.counters.filter { it.value > 0 }.toSortedMap().forEach { (k, v) -> add(counterIcon(k) to if (k == LiveGames.ASCEND) "" else "$v") }
    }
    if (!g.commanderDamage && badges.isEmpty()) return
    val tap = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
    val grid: @Composable () -> Unit = {
        val n = g.seats.size
        val (w, h) = miniGridSize(g, s)
        Box(tap.background(pill).padding(GRID_FRAME)) {
            SeatGrid(g, s.seat, w, h, look.colourGrids, ink) { j ->
                MiniCell(g, s, j, if (look.colourGrids && j != s.seat) PanelInk else ink, if (n >= 5) 9.sp else 11.sp)
            }
        }
    }
    val side: @Composable () -> Unit = {
        if (badges.isNotEmpty()) Row(tap.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            badges.chunked(3).forEach { column ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    column.forEach { (icon, v) ->
                        Row(Modifier.clip(RoundedCornerShape(8.dp)).background(pill).padding(horizontal = 5.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, Modifier.size(14.dp), tint = ink)
                            if (v.isNotEmpty()) Text(" $v", color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    if (!g.commanderDamage) return side()
    if (beside) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { grid(); side() }
        return
    }
    // The grid on the panel's middle line; the badges just after it, moved in only when they'd run off the panel.
    Layout(content = { grid(); side() }) { measurables, c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val gridP = measurables[0].measure(loose)
        val sideP = measurables.getOrNull(1)?.measure(loose)
        val gap = 6.dp.roundToPx()
        val sideW = sideP?.let { it.width + gap } ?: 0
        val width = if (c.hasBoundedWidth) c.maxWidth else gridP.width + 2 * sideW
        val height = maxOf(gridP.height, sideP?.height ?: 0)
        layout(width, height) {
            val x = minOf((width - gridP.width) / 2, width - gridP.width - sideW).coerceAtLeast(0)
            gridP.place(x, (height - gridP.height) / 2)
            sideP?.place(x + gridP.width + gap, (height - sideP.height) / 2)
        }
    }
}

/**
 * A mini-grid box: the damage taken from that player's commander ("3|0" for partners); on the player's own seat "me"
 * until their own commander has dealt them damage (since 1.49).
 */
@Composable
private fun BoxScope.MiniCell(g: LiveGame, me: LiveSeat, j: Int, ink: Color, font: TextUnit) {
    val text = miniCellText(g, me, j)
    Text(
        text, color = if (text == "me") ink.copy(alpha = 0.45f) else ink, fontSize = if (text.length > 3) (font.value * 0.8f).sp else font,
        fontWeight = if (text == "me") FontWeight.Normal else FontWeight.SemiBold, maxLines = 1, modifier = Modifier.align(Alignment.Center),
    )
}

internal fun miniCellText(g: LiveGame, me: LiveSeat, j: Int): String {
    val o = g.seats[j]
    val first = me.cmd[LiveGames.cmdKey(o.seat)] ?: 0
    val second = me.cmd[LiveGames.cmdKey(o.seat, true)] ?: 0
    return when {
        o.seat == me.seat && first == 0 && second == 0 -> "me"
        o.hasPartners -> "$first|$second"
        else -> "$first"
    }
}

/**
 * The table in miniature, as it lies on the screen, so every player sees the same picture; each box faces [me]
 * (its content is drawn the way [me] reads). [w] × [h] is the size as [me] sees it. Boxes are in their player's
 * colour ([colourise]); with [focus] the others fade.
 */
@Composable
internal fun SeatGrid(
    g: LiveGame,
    me: Int,
    w: Dp,
    h: Dp,
    colourise: Boolean,
    ink: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 2.dp,
    radius: Dp = 5.dp,
    focus: Int? = null,
    cell: @Composable BoxScope.(Int) -> Unit,
) {
    val n = g.seats.size
    val turn = seatTurn(n, g.seats.indexOfFirst { it.seat == me }.coerceAtLeast(0), g.ends)
    val sideways = turn % 180 != 0
    Box(modifier.turned(-turn)) {
        Column(Modifier.size(if (sideways) h else w, if (sideways) w else h), verticalArrangement = Arrangement.spacedBy(gap)) {
            arrangement(n, g.ends).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.distinct().forEach { j ->
                        val seat = g.seats[j]
                        val bg = if (seat.seat == me || !colourise) Modifier.background(ink.copy(alpha = 0.13f)) else Modifier.background(seatBrush(seat.colour))
                        Box(Modifier.weight(row.count { it == j }.toFloat()).fillMaxHeight().alpha(if (focus != null && focus != seat.seat) 0.25f else 1f).clip(RoundedCornerShape(radius)).then(bg)) {
                            Box(Modifier.fillMaxSize().turned(turn)) { cell(j) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Low life: a faint ghost drifting up through the panel, over and over. Since 1.50 it rises between the total and
 * the + ([sideRoom] in from the edge), clear of the + and the mini-grid; where the grid sits beside the total it
 * takes the − side instead.
 */
@Composable
private fun BoxScope.DriftingGhost(small: Dp, colour: Color, sideRoom: Dp, onPlusSide: Boolean) {
    val t = rememberInfiniteTransition(label = "ghost")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4_500, easing = LinearEasing)), label = "rise")
    Ghost(
        colour = colour,
        modifier = (if (onPlusSide) Modifier.align(Alignment.BottomEnd).padding(end = sideRoom) else Modifier.align(Alignment.BottomStart).padding(start = sideRoom))
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
internal fun Ghost(modifier: Modifier, colour: Color = Color.White) = Canvas(modifier) {
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
    drawPath(path, colour.copy(alpha = if (colour == Color.White) 0.32f else 0.22f))
    drawOval((if (colour == Color.White) Color.Black else Color.White).copy(alpha = 0.45f), Offset(w * 0.25f, w * 0.35f), Size(w * 0.16f, w * 0.22f))
    drawOval((if (colour == Color.White) Color.Black else Color.White).copy(alpha = 0.45f), Offset(w * 0.58f, w * 0.35f), Size(w * 0.16f, w * 0.22f))
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

