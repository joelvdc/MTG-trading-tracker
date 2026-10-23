package com.mtgtrader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mtgtrader.container
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveGames
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.data.OutReason

internal val SheetInk = Color(0xFF15171C)
private val SheetMuted = Color(0xFF6B7280)
private val SheetBrush = Brush.linearGradient(listOf(Color(0xFFEAEEF8), Color(0xFFD3D9E8)))

/**
 * A player's sheet (since 1.48), facing them, as Lifetap's: Counters (commander damage taken on a seating grid,
 * and tiles for the other counters) and Player (who they are, their deck, their background).
 */
@Composable
internal fun PlayerSheet(g: LiveGame, seat: Int, look: PanelLook, startOnCounters: Boolean, onClose: () -> Unit, startFocus: Int? = null) {
    val c = LocalContext.current.container
    val s = g.seats.first { it.seat == seat }
    val turn = seatTurn(g.seats.size, g.seats.indexOf(s), g.ends)
    var counters by remember(seat) { mutableStateOf(startOnCounters) }
    var focus by remember(seat) { mutableStateOf(startFocus) }
    var picking by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    BackHandler(enabled = focus != null) { focus = null }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        val sideways = turn % 180 != 0
        // The sheet's size as its player sees it.
        val pw = minOf(480.dp, if (sideways) maxHeight * 0.62f else maxWidth - 20.dp)
        val ph = minOf(410.dp, if (sideways) maxWidth - 20.dp else maxHeight * 0.5f)
        Box(Modifier.align(Alignment.Center).size(if (sideways) ph else pw, if (sideways) pw else ph)) {
            Box(Modifier.fillMaxSize().turned(turn)) {
                Column(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(SheetBrush).pointerInput(Unit) { detectTapGestures { } }.padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (counters && focus != null) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).clickable { focus = null }.padding(4.dp).size(24.dp), tint = SheetInk)
                        }
                        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFC9CFDB)).padding(3.dp)) {
                            listOf("Player" to false, "Counters" to true).forEach { (label, k) ->
                                Text(
                                    label, color = SheetInk, fontSize = 15.sp, fontWeight = if (counters == k) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(if (counters == k) Color.White else Color.Transparent)
                                        .clickable { counters = k; focus = null }.padding(horizontal = 18.dp, vertical = 7.dp),
                                )
                            }
                        }
                        Text(s.name, color = SheetMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        Icon(Icons.Outlined.Close, "Close", Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(4.dp).size(26.dp), tint = SheetInk)
                    }
                    Spacer(Modifier.height(10.dp))
                    val f = focus
                    when {
                        !counters -> PlayerTab(g, s, look, onPick = { picking = true }, onClose = onClose)
                        f != null -> DamageDetail(g, s, f) { focus = it }
                        else -> CountersTab(g, s, onFocus = { focus = it }, onCustom = { custom = true }, onClose = onClose)
                    }
                }
            }
        }
    }
    if (picking) ReseatPicker(g, seat, look.pictures) { picking = false }
    if (custom) {
        TextInputDialog("Custom counter", "Its name", "", "Add", onDismiss = { custom = false }) { name ->
            custom = false
            if (name.isNotBlank()) c.games.update { it.copy(counters = (it.counters + (LiveGames.CUSTOM + name.trim())).distinct()) }
        }
    }
}

/** Picks another player or deck for [seat] during the game (the New game screen's seat picker). */
@Composable
internal fun ReseatPicker(g: LiveGame, seat: Int, pictures: Boolean, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val players by c.games.players.collectAsStateWithLifecycle(emptyList())
    val friendDecks by c.games.friendDecks.collectAsStateWithLifecycle(emptyList())
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val s = g.seats.firstOrNull { it.seat == seat } ?: return
    SeatPickerSheet(
        seatIndex = g.seats.indexOf(s),
        choice = seatChoiceOf(s),
        taken = g.seats.filter { it.seat != seat }.mapNotNull { it.playerUid }.toSet(),
        players = players, decks = decks, friendDecks = friendDecks, pictures = pictures, onDismiss = onDismiss, showLook = false,
    ) { choice ->
        c.games.update { reseat(it, seat, choice, seatDeck(choice, decks, friendDecks)) }
        c.games.live.value?.let { c.games.keepTable(setupOf(it, c.games.lastTable?.firstSeat)) }
    }
}

/** The commander damage grid and the counter tiles. Tap a grid box to add 1, hold it for −, partners and more. */
@Composable
private fun CountersTab(g: LiveGame, s: LiveSeat, onFocus: (Int) -> Unit, onCustom: () -> Unit, onClose: () -> Unit) {
    val c = LocalContext.current.container
    val tiles = counterTiles(g, s, onCustom) {
        c.games.update { LiveGames.knockOut(it, s.seat, System.currentTimeMillis(), LiveGames.outCause(s) ?: OutReason.OTHER) }
        onClose()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tileW = if (maxWidth > 420.dp) 62.dp else 56.dp
        if (!g.commanderDamage) {
            val cols = ((maxWidth + 6.dp) / (tileW + 6.dp)).toInt().coerceAtLeast(3)
            TileGrid(tiles, cols, tileW, Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val tilesW = tileW * 3 + 12.dp
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Commander damage taken", color = SheetInk, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    SeatGrid(g, s.seat, maxWidth, maxHeight, colourise = true, ink = SheetInk, gap = 6.dp, radius = 14.dp) { j ->
                        BigCell(g, s, j, focused = false, onFocus = onFocus)
                    }
                }
                Text("Tap a box to add 1. Hold it for −, partners and more.", color = SheetMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(10.dp))
            TileGrid(tiles, 3, tileW, Modifier.width(tilesW).fillMaxHeight())
        }
    }
}

/** One player's commander damage (an opponent's, or since 1.49 the player's own), big, with − and +; partners split it in two. */
@Composable
private fun DamageDetail(g: LiveGame, s: LiveSeat, focus: Int, onFocus: (Int?) -> Unit) {
    val c = LocalContext.current.container
    val o = g.seats.firstOrNull { it.seat == focus } ?: return
    Column(Modifier.fillMaxSize()) {
        Text(if (o.seat == s.seat) "Commander damage from your own commander" else "Commander damage from ${o.name}", color = SheetInk, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            SeatGrid(g, s.seat, maxWidth, maxHeight, colourise = true, ink = SheetInk, gap = 8.dp, radius = 16.dp, focus = focus) { j ->
                BigCell(g, s, j, focused = g.seats[j].seat == focus, onFocus = { onFocus(it) })
            }
        }
        val holdOne by c.settings.trackerHoldOne.collectAsStateWithLifecycle()
        Text(if (holdOne) "Tap − or + for 1, hold to keep going." else "Tap − or + for 1, hold for 10.", color = SheetMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 6.dp).fillMaxWidth().clickable { c.games.update { LiveGames.setPartners(it, o.seat, !o.hasPartners) } }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.People, null, Modifier.size(22.dp), tint = SheetInk)
            Text("  Partner commanders (two counts)", color = SheetInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(
                o.hasPartners, { on -> c.games.update { LiveGames.setPartners(it, o.seat, on) } },
                colors = sheetSwitch(),
            )
        }
    }
}

/**
 * A box of the big grid: the damage taken from that player's commander(s); the player's own box counts damage from
 * their own commander (since 1.49), say when another player controls it. In the grid a tap adds 1 and a hold opens
 * the detail; [focused] (the detail) taps on the left take 1 off, on the right add 1, and holds 10 (since 1.57).
 */
@Composable
private fun BoxScope.BigCell(g: LiveGame, me: LiveSeat, j: Int, focused: Boolean, onFocus: (Int) -> Unit) {
    val c = LocalContext.current.container
    val holdOne by c.settings.trackerHoldOne.collectAsStateWithLifecycle()
    val buzz = rememberBuzz()
    val o = g.seats[j]
    val parts = if (o.hasPartners) listOf(false, true) else listOf(false)
    val big = if (g.seats.size >= 5) 24.sp else 32.sp
    Row(Modifier.fillMaxSize()) {
        parts.forEachIndexed { k, second ->
            if (k > 0) Box(Modifier.width(1.5.dp).fillMaxHeight().padding(vertical = 6.dp).background(SheetInk.copy(alpha = 0.55f)))
            val key = LiveGames.cmdKey(o.seat, second)
            val value = me.cmd[key] ?: 0
            fun add(by: Int) { buzz.tap(); c.games.update { LiveGames.commanderDamage(it, me.seat, key, by) } }
            Box(
                Modifier.weight(1f).fillMaxHeight().pointerInput(key, focused, holdOne) {
                    // In the detail − and + work as on a life panel (a hold gives ±10, since 1.57).
                    if (focused) tapOrHoldSteps(holdOne, plusAt = { at -> at.x >= size.width / 2f }, onStep = ::add)
                    else detectTapGestures(onTap = { add(1) }, onLongPress = { onFocus(o.seat) })
                },
            ) {
                val name = if (second) o.partner ?: "Partner" else o.commander.ifBlank { o.name }
                val who = if (o.seat == me.seat) "me" else o.name
                Text(
                    if (parts.size == 1 && (o.commander.isNotBlank() || o.seat == me.seat)) "$who · $name" else name,
                    color = SheetInk.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp, start = 4.dp, end = 4.dp),
                )
                Text("$value", color = SheetInk, fontSize = big, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
                if (focused) {
                    Text("−", color = SheetInk, fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
                    Text("+", color = SheetInk, fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
                }
            }
        }
    }
}

/** A counter tile: what it shows and what a tap and a hold do. */
private class Tile(val icon: ImageVector?, val drawn: String?, val label: String, val value: String?, val onTap: () -> Unit, val onHold: () -> Unit = {})

/** The tiles of [s]'s sheet: poison, tax, the table's monarch, initiative and day/night, the other counters, K.O. */
@Composable
private fun counterTiles(g: LiveGame, s: LiveSeat, onCustom: () -> Unit, onKo: () -> Unit): List<Tile> {
    val c = LocalContext.current.container
    val buzz = rememberBuzz()
    fun up(f: (LiveGame) -> LiveGame) { buzz.tap(); c.games.update(change = f) }
    fun count(key: String, label: String, icon: ImageVector) = Tile(
        icon, null, label, s.counters[key]?.takeIf { it > 0 }?.toString(),
        onTap = { up { LiveGames.counter(it, s.seat, key, 1) } }, onHold = { up { LiveGames.counter(it, s.seat, key, -1) } },
    )
    return buildList {
        add(Tile(Icons.Outlined.WaterDrop, null, "Poison", s.poison.takeIf { it > 0 }?.toString(), { up { LiveGames.poison(it, s.seat, 1) } }, { up { LiveGames.poison(it, s.seat, -1) } }))
        add(
            Tile(
                Icons.Outlined.AddCircleOutline, null, if (s.hasPartners) "Tax ${s.commander.substringBefore(',').ifBlank { "1" }}" else "C. tax",
                s.tax.takeIf { it > 0 }?.let { "+$it" }, { up { LiveGames.casts(it, s.seat, 1) } }, { up { LiveGames.casts(it, s.seat, -1) } },
            ),
        )
        if (s.hasPartners) add(
            Tile(
                Icons.Outlined.AddCircleOutline, null, "Tax ${s.partner?.substringBefore(',') ?: "2"}", s.partnerTax.takeIf { it > 0 }?.let { "+$it" },
                { up { LiveGames.casts(it, s.seat, 1, partner = true) } }, { up { LiveGames.casts(it, s.seat, -1, partner = true) } },
            ),
        )
        add(Tile(Icons.Outlined.WorkspacePremium, null, "Monarch", if (g.monarch == s.seat) "" else null, { up { LiveGames.setMonarch(it, if (it.monarch == s.seat) null else s.seat) } }))
        add(Tile(Icons.Outlined.Explore, null, "Initiative", if (g.initiative == s.seat) "" else null, { up { LiveGames.setInitiative(it, if (it.initiative == s.seat) null else s.seat) } }))
        add(
            Tile(
                if (g.dayNight == "night") Icons.Default.DarkMode else Icons.Outlined.WbSunny, null, g.dayNight?.replaceFirstChar { it.uppercase() } ?: "Day/night",
                g.dayNight?.let { "" }, { up { LiveGames.toggleDayNight(it) } }, { up { LiveGames.clearDayNight(it) } },
            ),
        )
        add(Tile(Icons.Outlined.AccountBalance, null, "Ascend", if (LiveGames.ASCEND in s.counters) "" else null, { up { LiveGames.toggleAscend(it, s.seat) } }))
        add(count("energy", "Energy", Icons.Outlined.Bolt))
        add(count("experience", "Exp", Icons.Outlined.AutoAwesome))
        add(Tile(null, "rad", "Rad", s.counters["rad"]?.takeIf { it > 0 }?.toString(), { up { LiveGames.counter(it, s.seat, "rad", 1) } }, { up { LiveGames.counter(it, s.seat, "rad", -1) } }))
        add(count("treasure", "Treasure", Icons.Outlined.Diamond))
        g.counters.filter { it.startsWith(LiveGames.CUSTOM) }.forEach { add(count(it, LiveGames.counterLabel(it), Icons.Outlined.Star)) }
        add(Tile(null, "ko", "K.O.", null, onKo))
        add(Tile(Icons.Outlined.Add, null, "Custom", null, onCustom))
    }
}

@Composable
private fun TileGrid(tiles: List<Tile>, cols: Int, tileW: Dp, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { TileView(it, tileW) } }
        }
        Text("Hold a tile to take one off.", color = SheetMuted, fontSize = 10.sp)
    }
}

@Composable
private fun TileView(t: Tile, w: Dp) {
    val on = t.value != null
    val tint = if (on) SheetInk else Color(0xFF9AA0AC)
    Column(
        Modifier.width(w).height(58.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Color.White else Color.White.copy(alpha = 0.45f))
            .then(if (on) Modifier.border(1.5.dp, Color(0xFF8FB4E8), RoundedCornerShape(12.dp)) else Modifier)
            .pointerInput(t) { detectTapGestures(onTap = { t.onTap() }, onLongPress = { t.onHold() }) }.padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                t.icon != null -> Icon(t.icon, null, Modifier.size(22.dp), tint = tint)
                t.drawn == "rad" -> Canvas(Modifier.size(22.dp)) {
                    for (k in 0 until 3) drawArc(tint, -120f + k * 120f, 60f, true, Offset.Zero, size)
                    drawCircle(if (on) Color.White else Color(0xFFE4E8F1), size.width * 0.2f)
                    drawCircle(tint, size.width * 0.12f)
                }
                else -> Skull(Modifier.size(22.dp), tint)
            }
            if (!t.value.isNullOrEmpty()) Text(" ${t.value}", color = SheetInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(t.label, color = tint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Who the player is and their deck (both can be changed), their background, contrast, and conceding. */
@Composable
private fun PlayerTab(g: LiveGame, s: LiveSeat, look: PanelLook, onPick: () -> Unit, onClose: () -> Unit) {
    val c = LocalContext.current.container
    val picture = (s.picture ?: look.pictures) && s.artUrl != null
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).clickable(onClick = onPick).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Person, null, tint = SheetMuted)
                Text("  " + s.name + if (s.me) " (you)" else "", color = SheetInk, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.SwapHoriz, "Change player", tint = SheetInk)
            }
            Row(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).clickable(onClick = onPick).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(seatBrush(s.colour))) {
                    if (s.deckName.isNotBlank() && s.artUrl != null) AsyncImage(s.artUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(s.deckName.ifBlank { "No deck" }, color = SheetInk, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(s.commander.ifBlank { null }, s.partner).joinToString(" & ").ifBlank { "Pick a deck to record it" },
                        color = SheetMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("Change", color = Color(0xFF2C6FB7), fontSize = 13.sp)
            }
            Row(Modifier.clickable { c.games.update(undoable = false) { LiveGames.setContrast(it, s.seat, !s.contrast) } }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Contrast, null, Modifier.size(20.dp), tint = SheetInk)
                Text("  Increase contrast", color = SheetInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    s.contrast, { on -> c.games.update(undoable = false) { LiveGames.setContrast(it, s.seat, on) } },
                    colors = sheetSwitch(),
                )
            }
            Text(
                "${s.name} concedes", color = SheetInk, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF9AA0AC), RoundedCornerShape(12.dp))
                    .clickable { c.games.update { LiveGames.knockOut(it, s.seat, System.currentTimeMillis(), OutReason.CONCEDED) }; onClose() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(150.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Background", color = SheetInk, fontSize = 13.sp)
            Box(
                Modifier.padding(top = 4.dp).size(146.dp, 80.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF3B3F4A))
                    .then(if (picture) Modifier.border(3.dp, Color(0xFF2C6FB7), RoundedCornerShape(12.dp)) else Modifier)
                    .clickable(enabled = s.artUrl != null) { c.games.update(undoable = false) { LiveGames.setBackground(it, s.seat, true) } },
            ) {
                if (s.artUrl != null) AsyncImage(s.artUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Outlined.Image, null, Modifier.align(Alignment.Center).size(28.dp), tint = Color.White.copy(alpha = 0.7f))
                Text(
                    if (s.artUrl != null) "Commander picture" else "No picture (offline?)", color = Color.White, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(vertical = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (s.deckName.isBlank()) {
                Row(
                    Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        c.games.randomArt(1, setOfNotNull(s.artUrl)).firstOrNull()?.let { art ->
                            c.games.update(undoable = false) { g2 -> g2.copy(seats = g2.seats.map { if (it.seat == s.seat) it.copy(artUrl = art.url, picture = true) else it }) }
                        }
                    }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp), tint = Color(0xFF2C6FB7))
                    Text(" Another picture", color = Color(0xFF2C6FB7), fontSize = 12.sp)
                }
            }
            Text("or a colour", color = SheetMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            LiveGames.PALETTE.indices.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { i ->
                        val on = !picture && s.colour == i
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(seatBrush(i))
                                .border(if (on) 3.dp else 1.dp, if (on) Color(0xFF2C6FB7) else Color(0x33000000), RoundedCornerShape(10.dp))
                                .clickable { c.games.update(undoable = false) { LiveGames.setBackground(it, s.seat, false, i) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun sheetSwitch() = SwitchDefaults.colors(
    checkedTrackColor = Color(0xFF7FB2D3), checkedThumbColor = Color.White, checkedBorderColor = Color(0xFF7FB2D3),
    uncheckedTrackColor = Color(0xFFC9CFDB), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color(0xFFB0B7C3),
)
