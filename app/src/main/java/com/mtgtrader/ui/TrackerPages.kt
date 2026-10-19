package com.mtgtrader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.CardLayout
import com.mtgtrader.data.GameRecord
import com.mtgtrader.data.Games
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LowLifeStyle
import com.mtgtrader.data.SeatChoice
import com.mtgtrader.data.TableSetup
import com.mtgtrader.data.WhenDown
import com.mtgtrader.data.WinType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val Muted = Color(0xFF8C8C8C)
private val Line = Color(0xFF2A2A2A)
private val Raised = Color(0xFF2B2B2B)

/**
 * A page from the middle bar (since 1.48), as Lifetap's: black, upright, with the edges of the panels peeking in at
 * the top and the bottom; back closes it.
 */
@Composable
private fun TrackerPageFrame(g: LiveGame, title: String, onClose: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val rows = arrangement(g.seats.size, g.ends)
    Column(
        Modifier.fillMaxSize().background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Slivers(g, rows.first())
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Back",
                Modifier.align(Alignment.CenterStart).clip(CircleShape).clickable(onClick = onClose).padding(10.dp), tint = Color.White,
            )
            Text(title, color = Color.White, fontSize = 22.sp, modifier = Modifier.align(Alignment.Center))
        }
        HorizontalDivider(color = Line)
        Column(
            Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 18.dp),
            content = content,
        )
        Slivers(g, rows.last())
    }
}

@Composable
private fun Slivers(g: LiveGame, row: List<Int>) = Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    row.distinct().forEach { i ->
        Box(Modifier.weight(row.count { it == i }.toFloat()).height(22.dp).clip(RoundedCornerShape(14.dp)).background(seatBrush(g.seats[i].colour)))
    }
}

@Composable
private fun SectionHead(t: String) = Row(Modifier.padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(t, color = Muted, fontSize = 18.sp)
    Box(Modifier.padding(start = 12.dp).weight(1f).height(1.dp).background(Color(0xFF333333)))
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color(0xFFBDBDBD))
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (sub != null) Text(sub, color = Muted, fontSize = 13.sp)
        }
        trailing()
    }
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) = Switch(
    on, onChange,
    colors = SwitchDefaults.colors(
        checkedThumbColor = Color(0xFF0B0B0B), checkedTrackColor = TrackerAccent, checkedBorderColor = TrackerAccent,
        uncheckedThumbColor = Color(0xFF8A8A8A), uncheckedTrackColor = Color(0xFF262626), uncheckedBorderColor = Color(0xFF262626),
    ),
)

@Composable
private fun SwitchSetting(icon: ImageVector, title: String, sub: String? = null, on: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(icon, title, sub, onClick = { onChange(!on) }) { Toggle(on, onChange) }

/** A choice shown as its current value with ⌄, opening a menu. */
@Composable
private fun <T> Drop(value: T, options: List<T>, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label(value), color = Color(0xFFBDBDBD), fontSize = 15.sp)
            Icon(Icons.Outlined.ExpandMore, null, tint = Color(0xFFBDBDBD))
        }
        ScreenMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(label(o)) }, onClick = { open = false; onPick(o) }) }
        }
    }
}

@Composable
private fun Circles(values: List<Int>, selected: Int?, onPick: (Int) -> Unit) = Row(
    Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xFF1C1C1C)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
) {
    values.forEach { v ->
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF2E2E2E)).then(if (v == selected) Modifier.border(2.5.dp, TrackerAccent, CircleShape) else Modifier)
                .clickable { onPick(v) },
            contentAlignment = Alignment.Center,
        ) { Text("$v", color = Color.White, fontSize = 15.sp) }
    }
}

/** A layout's little picture: side by side, or one at each end. */
@Composable
private fun LayoutIcon(players: Int, ends: Boolean, selected: Boolean, onClick: () -> Unit) {
    val grey = Color(0xFF7A7A7A)
    Box(
        Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).then(if (selected) Modifier.border(2.5.dp, TrackerAccent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            arrangement(players, ends).forEachIndexed { r, row ->
                Row(Modifier.weight(if (ends && (r == 0 || r == arrangement(players, ends).size - 1)) 0.7f else 1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.distinct().forEach { i -> Box(Modifier.weight(row.count { it == i }.toFloat()).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(grey)) }
                }
            }
        }
    }
}

@Composable
private fun CardLayoutThumb(layout: CardLayout, selected: Boolean, onClick: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        Modifier.size(84.dp, 48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2E2E2E))
            .then(if (selected) Modifier.border(2.5.dp, TrackerAccent, RoundedCornerShape(10.dp)) else Modifier).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (layout != CardLayout.BIG) Text("Player", color = Color.White, fontSize = 8.sp)
            Text("40", color = Color.White, fontSize = if (layout == CardLayout.BIG) 22.sp else 13.sp)
            if (layout == CardLayout.DEFAULT) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(2) { Box(Modifier.size(5.dp).background(Muted)) } }
        }
    }
    Text(layout.label, color = Muted, fontSize = 12.sp)
}

/** Whether nothing has happened in [g] yet, so it can start over without asking. */
internal fun untouched(g: LiveGame): Boolean = g.turns.isEmpty() &&
    g.seats.all { it.life == g.startingLife && it.cmd.isEmpty() && it.poison == 0 && it.casts == 0 && it.partnerCasts == 0 && it.counters.isEmpty() && !it.out }

/**
 * The game's settings (since 1.48; Lifetap's Settings): this game's players, layout, starting life and seats; how
 * it's played; the controls; how it looks; the phone. Game choices become the defaults for the next games.
 */
@Composable
internal fun GameSettingsPage(g: LiveGame, onClose: () -> Unit) {
    val c = LocalContext.current.container
    val s = c.settings
    val friendDecks by c.games.friendDecks.collectAsStateWithLifecycle(emptyList())
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val pictures by s.trackerPictures.collectAsStateWithLifecycle()
    val aboveBelow by s.trackerAboveBelow.collectAsStateWithLifecycle()
    val holdOne by s.trackerHoldOne.collectAsStateWithLifecycle()
    val lowLife by s.trackerLowLife.collectAsStateWithLifecycle()
    val cardLayout by s.trackerCardLayout.collectAsStateWithLifecycle()
    val haptics by s.trackerFlag("trackerHaptics").collectAsStateWithLifecycle()
    val colourGrids by s.trackerFlag("trackerColourGrids").collectAsStateWithLifecycle()
    val autoHide by s.trackerFlag("trackerAutoHide").collectAsStateWithLifecycle()
    val animations by s.trackerFlag("trackerAnimations").collectAsStateWithLifecycle()
    val keepAwake by s.trackerFlag("trackerKeepAwake").collectAsStateWithLifecycle()
    val hideBars by s.trackerFlag("trackerHideBars").collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var customLife by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Int?>(null) }

    /** Changes this game (not undoable: it's a setting) and keeps it for the rematch. */
    fun game(change: (LiveGame) -> LiveGame) {
        c.games.update(undoable = false, change = change)
        c.games.live.value?.let { c.games.keepTable(setupOf(it, c.games.lastTable?.firstSeat)) }
    }
    fun restart(setup: TableSetup) {
        val now = System.currentTimeMillis()
        val first = setup.firstSeat ?: setup.seats.indices.random()
        val art = c.games.randomArt(setup.seats.size, setup.seats.mapNotNull { it.artUrl }.toSet()).map { it.url }
        c.games.start(startGame(setup, decks, friendDecks, art, now, first), setup)
    }
    fun withRestart(what: String, setup: TableSetup) {
        if (untouched(g)) restart(setup) else confirm = what to { restart(setup) }
    }
    val base = setupOf(g, c.games.lastTable?.firstSeat)
    fun startingLife(life: Int) {
        if (untouched(g)) game { it.copy(startingLife = life, seats = it.seats.map { p -> p.copy(life = life) }) }
        else confirm = "Start the game over at $life life?" to { restart(base.copy(startingLife = life)) }
    }

    TrackerPageFrame(g, "Game settings", onClose) {
        SectionHead("This game")
        SettingRow(Icons.Outlined.Group, "Players") {
            Circles((2..6).toList(), g.seats.size) { n ->
                if (n != g.seats.size) withRestart(
                    "Start a new game with $n players?",
                    base.copy(seats = if (n <= base.seats.size) base.seats.take(n) else base.seats + (base.seats.size + 1..n).map { SeatChoice(name = "Player $it") }),
                )
            }
        }
        if (g.seats.size == 4 || g.seats.size == 6) {
            SettingRow(Icons.Outlined.GridOn, "Layout", "Side by side, or one at each end") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LayoutIcon(g.seats.size, false, !g.ends) { game { it.copy(ends = false) }; s.setTracker("trackerEnds", false) }
                    LayoutIcon(g.seats.size, true, g.ends) { game { it.copy(ends = true) }; s.setTracker("trackerEnds", true) }
                }
            }
        }
        SettingRow(Icons.Outlined.FavoriteBorder, "Starting life") {
            Column(horizontalAlignment = Alignment.End) {
                Circles(listOf(20, 25, 30, 40, 60), g.startingLife) { if (it != g.startingLife) startingLife(it) }
                Text(
                    if (g.startingLife in setOf(20, 25, 30, 40, 60)) "Custom" else "Custom: ${g.startingLife}", color = Muted, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { customLife = true }.padding(4.dp),
                )
            }
        }
        g.seats.forEach { p ->
            SettingRow(
                Icons.Outlined.EventSeat, p.name + if (p.me) " (you)" else "",
                listOfNotNull(p.deckName.ifBlank { null }, listOfNotNull(p.commander.ifBlank { null }, p.partner).joinToString(" & ").ifBlank { null }).distinct().joinToString(" · ").ifBlank { "No deck" },
                onClick = { picking = p.seat },
            ) { Icon(Icons.Outlined.ChevronRight, null, tint = Muted) }
        }
        SectionHead("Gameplay")
        SwitchSetting(Icons.Outlined.Shield, "Commander damage", "The mini-grids on the panels", g.commanderDamage) { on ->
            game { it.copy(commanderDamage = on) }; s.setTracker("trackerCommanderDamage", on)
        }
        SwitchSetting(Icons.Outlined.HeartBroken, "Commander damage life loss", "Commander damage also takes life", g.cmdLifeLoss) { on ->
            game { it.copy(cmdLifeLoss = on) }; s.setTracker("trackerCmdLifeLoss", on)
        }
        SettingRow(Icons.Outlined.HighlightOff, "When a player is down", "0 life, 10 poison or 21 commander damage") {
            Drop(WhenDown.fromKey(g.whenDown), WhenDown.entries.toList(), { it.label }) { w -> game { it.copy(whenDown = w.key) }; s.setTrackerWhenDown(w) }
        }
        val turnChoices = listOf(false to false, false to true, true to false, true to true)
        SettingRow(Icons.Outlined.Timer, "Turns and clock", "Whose turn it is, rounds, and the time") {
            Drop(g.turnsOn to g.clock, turnChoices, { (t, k) -> when { t && k -> "Turns and clock"; t -> "Turns"; k -> "Clock"; else -> "Off" } }) { (t, k) ->
                game { it.copy(turnsOn = t, clock = k, turnStartMs = if (t && !it.turnsOn) it.elapsed(System.currentTimeMillis()) else it.turnStartMs) }
                s.setTracker("trackerTurns", t); s.setTracker("trackerClock", k)
            }
        }
        SwitchSetting(Icons.Outlined.BarChart, "Game log", "What happened, in the history (bar: the clock with an arrow)", g.logOn) { on ->
            game { it.copy(logOn = on) }; s.setTracker("trackerLog", on)
        }
        SectionHead("Controls")
        SettingRow(Icons.Outlined.TouchApp, "Changing life", if (aboveBelow) "Tap above the total for +, below for −" else "Tap left of the total for −, right for +") {
            Drop(aboveBelow, listOf(false, true), { if (it) "Above / below" else "Left / right" }) { s.setTracker("trackerAboveBelow", it) }
        }
        SettingRow(Icons.Outlined.PanTool, "Holding", "Keep a finger on the panel") {
            Drop(holdOne, listOf(false, true), { if (it) "By 1, fast" else "By 10" }) { s.setTracker("trackerHoldOne", it) }
        }
        SwitchSetting(Icons.Outlined.Vibration, "Haptic feedback", on = haptics) { s.setTracker("trackerHaptics", it) }
        SectionHead("Appearance")
        SettingRow(Icons.Outlined.Image, "Backgrounds", "Each player can change their own on their panel") {
            Drop(pictures, listOf(false, true), { if (it) "Commander pictures" else "Colours" }) { s.setTracker("trackerPictures", it) }
        }
        SwitchSetting(Icons.Outlined.WaterDrop, "Colourise commander mini-grids", "Mini-grids show the players' colours", colourGrids) { s.setTracker("trackerColourGrids", it) }
        SettingRow(Icons.Outlined.Dashboard, "Player card layout")
        Row(Modifier.fillMaxWidth().padding(start = 38.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            CardLayout.entries.forEach { l -> CardLayoutThumb(l, cardLayout == l) { s.setTrackerCardLayout(l) } }
        }
        SettingRow(Icons.Outlined.MonitorHeart, "Low life", "A quarter of the starting life or less") {
            Drop(lowLife, LowLifeStyle.entries.toList(), { it.label }) { s.setTrackerLowLife(it) }
        }
        SwitchSetting(Icons.Outlined.VisibilityOff, "Auto-hide menu", "Hides the middle bar until you tap the gap", autoHide) { s.setTracker("trackerAutoHide", it) }
        SwitchSetting(Icons.Outlined.Animation, "Dice & randomiser animations", on = animations) { s.setTracker("trackerAnimations", it) }
        SectionHead("Device")
        SwitchSetting(Icons.Outlined.LightMode, "Keep display awake", on = keepAwake) { s.setTracker("trackerKeepAwake", it) }
        SwitchSetting(Icons.Outlined.Fullscreen, "Hide navigation and status bars", on = hideBars) { s.setTracker("trackerHideBars", it) }
        Text(
            "These settings follow you to your other phone. The game's own choices (players, turns, commander damage…) are kept for the next game too.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        )
    }
    confirm?.let { (what, action) ->
        ConfirmBox(what, "The game so far isn't saved.", "Start over", onCancel = { confirm = null }) { confirm = null; action(); onClose() }
    }
    if (customLife) {
        TextInputDialog("Starting life", "Life", "${g.startingLife}", "OK", onDismiss = { customLife = false }) { v ->
            customLife = false
            v.trim().toIntOrNull()?.takeIf { it in 1..999 && it != g.startingLife }?.let { startingLife(it) }
        }
    }
    picking?.let { seat -> ReseatPicker(g, seat, pictures) { picking = null } }
}

/** A dark question over the tracker, kept in its full-screen look. */
@Composable
private fun ConfirmBox(title: String, text: String, yes: String, onCancel: () -> Unit, onYes: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 360.dp).padding(20.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF2E2F33)).pointerInput(Unit) { detectTapGestures { } }.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Color(0xFFBDBDBD), fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                DarkButton("Cancel", onClick = onCancel)
                DarkButton(yes, colour = Color(0xFF5A3142), textColour = Color(0xFFFF8FB1), onClick = onYes)
            }
        }
    }
}

@Composable
private fun DarkButton(text: String, icon: ImageVector? = null, colour: Color = Raised, textColour: Color = Color.White, outlined: Boolean = false, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(12.dp))
            .then(if (outlined) Modifier.border(1.dp, Color(0xFF6A6A6A), RoundedCornerShape(12.dp)) else Modifier.background(colour))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = textColour); Spacer(Modifier.width(8.dp)) }
        Text(text, color = textColour, fontSize = 16.sp)
    }
}

/** A die drawn as an outline, like Dicefont's (the number goes on top). */
@Composable
private fun Die(sides: Int) = Canvas(Modifier.size(62.dp)) {
    val w = size.width
    val st = Stroke(w * 0.05f)
    val thin = Stroke(w * 0.03f)
    fun poly(vararg pts: Pair<Float, Float>) = Path().apply { moveTo(pts[0].first * w, pts[0].second * w); pts.drop(1).forEach { lineTo(it.first * w, it.second * w) }; close() }
    when (sides) {
        4 -> drawPath(poly(0.5f to 0.08f, 0.95f to 0.88f, 0.05f to 0.88f), Color.White, style = st)
        6 -> {
            drawRect(Color.White, Offset(w * 0.12f, w * 0.12f), Size(w * 0.76f, w * 0.76f), style = st)
            listOf(0.32f, 0.5f, 0.68f).forEach { y -> listOf(0.33f, 0.67f).forEach { x -> drawCircle(Color.White, w * 0.06f, Offset(x * w, y * w)) } }
        }
        8 -> { drawPath(poly(0.5f to 0.04f, 0.92f to 0.27f, 0.92f to 0.73f, 0.5f to 0.96f, 0.08f to 0.73f, 0.08f to 0.27f), Color.White, style = st); drawPath(poly(0.5f to 0.04f, 0.92f to 0.73f, 0.08f to 0.73f), Color.White, style = thin) }
        10 -> { drawPath(poly(0.5f to 0.04f, 0.95f to 0.45f, 0.5f to 0.96f, 0.05f to 0.45f), Color.White, style = st); drawPath(poly(0.5f to 0.04f, 0.78f to 0.6f, 0.22f to 0.6f), Color.White, style = thin) }
        12 -> { drawPath(poly(0.5f to 0.04f, 0.95f to 0.36f, 0.78f to 0.92f, 0.22f to 0.92f, 0.05f to 0.36f), Color.White, style = st); drawPath(poly(0.5f to 0.22f, 0.76f to 0.42f, 0.66f to 0.74f, 0.34f to 0.74f, 0.24f to 0.42f), Color.White, style = thin) }
        else -> { drawPath(poly(0.5f to 0.04f, 0.92f to 0.27f, 0.92f to 0.73f, 0.5f to 0.96f, 0.08f to 0.73f, 0.08f to 0.27f), Color.White, style = st); drawPath(poly(0.5f to 0.18f, 0.84f to 0.72f, 0.16f to 0.72f), Color.White, style = thin) }
    }
}

@Composable
private fun DiceButton(label: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)).background(Raised).clickable(onClick = onClick), contentAlignment = Alignment.Center, content = content)
    Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
}

/** Dice & random (since 1.48): a random player or opponent, a coin, a custom die and d4 to d20. */
@Composable
internal fun DicePage(g: LiveGame, onClose: () -> Unit, onRoll: (Roll) -> Unit, onRandomPlayer: () -> Unit, onRandomOpponent: () -> Unit) {
    var custom by rememberSaveable { mutableIntStateOf(100) }
    var setting by remember { mutableStateOf(false) }
    TrackerPageFrame(g, "Dice & random", onClose) {
        Spacer(Modifier.height(10.dp))
        listOf(
            Triple(Icons.Outlined.Shuffle, "Random player" to "Spins around the table", onRandomPlayer),
            Triple(Icons.Outlined.GpsFixed, "Random opponent" to (if (g.turnsOn) "For the player whose turn it is" else "One of the others, for you"), onRandomOpponent),
        ).forEach { (icon, text, action) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = action).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Raised), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }
                Column(Modifier.padding(start = 12.dp)) { Text(text.first, color = Color.White, fontSize = 17.sp); Text(text.second, color = Muted, fontSize = 13.sp) }
            }
        }
        Text("Coins & dice", color = Color.White, fontSize = 17.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp, bottom = 10.dp))
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            DiceButton("Flip coin", { onRoll(Roll("Coin", if (Random.nextBoolean()) "Heads" else "Tails", 2)) }) {
                Box(Modifier.size(50.dp).border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { Text("H", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
            DiceButton("Custom", { onRoll(Roll("1 – $custom", "${Random.nextInt(1, custom + 1)}", custom)) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "1 – $custom", color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black).clickable { setting = true }.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                    Text("Roll", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        listOf(listOf(4, 6, 8), listOf(10, 12, 20)).forEach { r ->
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                r.forEach { d ->
                    DiceButton("Roll d$d", { onRoll(Roll("d$d", "${Random.nextInt(1, d + 1)}", d)) }) {
                        Die(d)
                        if (d != 6) Text("$d", color = Color.White, fontSize = if (d >= 10) 16.sp else 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = if (d == 4) 8.dp else 3.dp))
                    }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp, bottom = 16.dp).fillMaxWidth().border(1.dp, Color(0xFF444444), RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bolt, null, tint = Color.White)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Quick roll", color = Color.White, fontSize = 16.sp)
                Text("Hold the die in the middle bar to roll a d20 without opening this page", color = Muted, fontSize = 13.sp)
            }
        }
    }
    if (setting) {
        TextInputDialog("Custom die", "Highest number", "$custom", "OK", onDismiss = { setting = false }) { v ->
            setting = false
            v.trim().toIntOrNull()?.takeIf { it in 2..1000 }?.let { custom = it }
        }
    }
}

/** The mana pool and the storm count (since 1.48), for the whole table, with the real mana symbols. */
@Composable
internal fun ManaPage(g: LiveGame, onClose: () -> Unit) {
    val c = LocalContext.current.container
    fun pool(f: (LiveGame) -> LiveGame) = c.games.update(undoable = false, change = f)
    @Composable
    fun line(v: Int, onBy: (Int) -> Unit, symbol: @Composable () -> Unit) = Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.RemoveCircleOutline, "One less", Modifier.clip(CircleShape).clickable { onBy(-1) }.padding(4.dp).size(34.dp), tint = Color(0xFFBDBDBD))
        Box(Modifier.padding(horizontal = 20.dp).alpha(if (v == 0) 0.35f else 1f).clip(CircleShape).clickable { onBy(1) }) { symbol() }
        Text("$v", color = if (v == 0) Color(0xFF4A4A4A) else Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(20.dp))
        Icon(Icons.Outlined.AddCircleOutline, "One more", Modifier.clip(CircleShape).clickable { onBy(1) }.padding(4.dp).size(34.dp), tint = Color(0xFFBDBDBD))
    }
    TrackerPageFrame(g, "Mana pool", onClose) {
        Spacer(Modifier.height(6.dp))
        LiveGames.MANA.forEach { m -> line(g.mana[m] ?: 0, { by -> pool { LiveGames.mana(it, m, by) } }) { ManaCostView("{$m}", 58.dp) } }
        Text("Storm count", color = Muted, fontSize = 19.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
        line(g.storm, { by -> pool { LiveGames.storm(it, by) } }) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFE0B341)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Bolt, null, Modifier.size(36.dp), tint = Color(0xFF3A2A00))
            }
        }
        Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DarkButton("Close", icon = Icons.Outlined.HighlightOff, onClick = onClose)
            DarkButton("Empty", colour = Color(0xFF3A1622), textColour = Color(0xFFFF8FB1), onClick = { pool { LiveGames.emptyPool(it) } })
        }
    }
}

/** Game history (since 1.48): this game's log, with undo and pause, and the games played before. */
@Composable
internal fun HistoryPage(g: LiveGame, now: Long, canUndo: Boolean, nav: NavController, onClose: () -> Unit) {
    val c = LocalContext.current.container
    val games by c.games.games.collectAsStateWithLifecycle(emptyList())
    var past by rememberSaveable { mutableStateOf(false) }
    TrackerPageFrame(g, "Game history", onClose) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF161616)).padding(4.dp)) {
            listOf("This game" to false, "Past games" to true).forEach { (label, p) ->
                Text(
                    label, color = if (past == p) Color.White else Muted, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (past == p) Color(0xFF2E2E2E) else Color.Transparent)
                        .clickable { past = p }.padding(vertical = 9.dp),
                )
            }
        }
        if (!past) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF121212))) {
                Box(Modifier.width(4.dp).height(92.dp).background(TrackerAccent))
                Column(Modifier.padding(14.dp).weight(1f)) {
                    Text(
                        listOfNotNull(if (g.turnsOn) "Round ${g.round}" else null, Games.clock(g.elapsed(now)) + if (g.paused) " (paused)" else "").joinToString(" · "),
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Started at " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(g.startedAt)) +
                            (g.seats.firstOrNull { it.seat == g.firstSeat }?.let { " · ${it.name} started" } ?: ""),
                        color = Muted, fontSize = 14.sp,
                    )
                    Text("${g.seats.size} players · ${g.startingLife} life", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkButton("Undo last", icon = Icons.AutoMirrored.Outlined.Undo, enabled = canUndo, onClick = { c.games.undo() })
                DarkButton(
                    if (g.paused) "Go on" else "Pause clock", icon = if (g.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    onClick = { c.games.update(undoable = false) { if (it.paused) LiveGames.resume(it, System.currentTimeMillis()) else LiveGames.pause(it, System.currentTimeMillis()) } },
                )
            }
            if (!g.logOn) Text("The game log is off (Game settings → Game log).", color = Muted, fontSize = 14.sp)
            else if (g.log.isEmpty()) Text("Nothing has happened yet.", color = Muted, fontSize = 14.sp)
            g.log.asReversed().forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Games.clock(e.at), color = Muted, fontSize = 13.sp, modifier = Modifier.width(52.dp))
                    val colour = g.seats.firstOrNull { it.seat == e.seat }?.colour
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (colour != null) seatBrush(colour) else androidx.compose.ui.graphics.SolidColor(Color(0xFF555555))))
                    Text("  " + LiveGames.logText(e, g), color = Color.White, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            if (games.isEmpty()) Text("No games saved yet.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            games.take(30).forEach { r -> PastGameRow(r) { r.uid?.let { nav.navigate("games/game/$it") } } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PastGameRow(r: GameRecord, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF121212)).clickable(onClick = onClick).padding(12.dp)) {
        Text(gameTitle(r), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(gameLine(r), color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Ending the game (since 1.48, in place of the Game over screen): who won (or a draw) and how, then save and
 * rematch at once, save and pick new players, or end without saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EndGameDialog(g: LiveGame, onCancel: () -> Unit, onLeave: () -> Unit, nav: NavController, onDone: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val friendDecks by c.games.friendDecks.collectAsStateWithLifecycle(emptyList())
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    var winner by remember(g.uid) { mutableStateOf(g.alive.singleOrNull()?.seat) }
    var draw by remember(g.uid) { mutableStateOf(false) }
    var how by remember(g.uid) { mutableStateOf<WinType?>(null) }
    var discard by remember { mutableStateOf(false) }
    fun record(): GameRecord = LiveGames.record(g, System.currentTimeMillis(), if (draw) null else winner, if (draw) null else how, null)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 380.dp).padding(16.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF2E2F33)).pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("End the game", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Who won? (optional)", color = Color(0xFFBDBDBD), fontSize = 15.sp)
            g.seats.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { p ->
                        val on = !draw && winner == p.seat
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF3B3C41))
                                .then(if (on) Modifier.border(2.dp, TrackerAccent, RoundedCornerShape(14.dp)) else Modifier)
                                .clickable { draw = false; winner = if (winner == p.seat) null else p.seat }.padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(p.name, color = if (p.out) Muted else Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(if (p.out) "out" else "${p.life}", color = if (p.out) Muted else Color.White, fontSize = 16.sp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF3B3C41))
                    .then(if (draw) Modifier.border(2.dp, TrackerAccent, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable { draw = !draw }.padding(12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) { Icon(Icons.Outlined.Handshake, null, tint = Color.White); Text("  Draw", color = Color.White, fontSize = 16.sp) }
            if (!draw) {
                Text("How? (optional)", color = Color(0xFFBDBDBD), fontSize = 15.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WinType.entries.forEach { t ->
                        val on = how == t
                        Text(
                            t.label, color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Color(0xFF2A5468) else Color(0xFF3B3C41))
                                .then(if (on) Modifier.border(1.5.dp, TrackerAccent, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable { how = if (on) null else t }.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Text(
                when {
                    draw -> "Saved as a draw; life goes back to ${g.startingLife}."
                    winner == null && g.alive.size > 1 -> "No winner picked: it's saved as a draw. Life goes back to ${g.startingLife}."
                    else -> "Saved to your games; life goes back to ${g.startingLife}."
                },
                color = Color(0xFFBDBDBD), fontSize = 14.sp,
            )
            DarkButton("Save and rematch", icon = Icons.Outlined.Replay, colour = Color(0xFF5A3142), textColour = Color(0xFFFF8FB1), modifier = Modifier.fillMaxWidth()) {
                onLeave()
                val r = record()
                scope.launch {
                    c.games.finish(r)
                    val setup = setupOf(g, c.games.lastTable?.firstSeat)
                    val now = System.currentTimeMillis()
                    val art = c.games.randomArt(setup.seats.size, setup.seats.mapNotNull { it.artUrl }.toSet()).map { it.url }
                    c.games.start(startGame(setup, decks, friendDecks, art, now, setup.firstSeat ?: setup.seats.indices.random()), setup)
                    onDone()
                }
            }
            DarkButton("Save, then new players", outlined = true, modifier = Modifier.fillMaxWidth()) {
                onLeave()
                val r = record()
                scope.launch {
                    c.games.finish(r)
                    nav.navigate("games/new?rematch=true") { popUpTo("games") }
                }
            }
            DarkButton("Cancel", icon = Icons.Outlined.HighlightOff, outlined = true, modifier = Modifier.fillMaxWidth(), onClick = onCancel)
            Text(
                "End without saving", color = Muted, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).clickable { discard = true }.padding(8.dp),
            )
        }
    }
    if (discard) {
        ConfirmBox("End without saving?", "This game won't be in your games or stats.", "End it", onCancel = { discard = false }) {
            onLeave()
            c.games.abandon()
            nav.navigate("games") { popUpTo("games") { inclusive = true } }
        }
    }
}

/** A roll, flip or pick over the table, written twice so both sides of the table can read it; tap to close. */
@Composable
internal fun RollOverlay(roll: Roll, animate: Boolean, onAgain: () -> Unit, onDismiss: () -> Unit) {
    var shown by remember(roll) { mutableStateOf(if (animate && roll.sides > 1) "" else roll.value) }
    LaunchedEffect(roll) {
        if (animate && roll.sides > 1) {
            repeat(9) { i ->
                shown = if (roll.sides == 2) (if (i % 2 == 0) "Heads" else "Tails") else "${Random.nextInt(1, roll.sides + 1)}"
                delay(45L + i * 10L)
            }
            shown = roll.value
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(min = 220.dp, max = 320.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF2E2F33)).pointerInput(Unit) { detectTapGestures { } }.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val big = if (shown.length > 3) 44.sp else 72.sp
            Text(shown, color = Color.White, fontSize = big, fontWeight = FontWeight.Bold, modifier = Modifier.rotate(180f))
            Text(roll.title, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
            Text(shown, color = Color.White, fontSize = big, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (roll.sides > 1) DarkButton("Again", icon = Icons.Outlined.Replay, onClick = onAgain)
                DarkButton("Close", icon = Icons.Outlined.HighlightOff, onClick = onDismiss)
            }
        }
    }
}
