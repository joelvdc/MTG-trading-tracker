package com.mtgtrader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mtgtrader.data.TableView

/** What one seat shows on the table drawing (since 1.72): [name] already says "(you)" when needed, [deck] is the line under it. */
internal data class TableSeat(val name: String, val deck: String, val artUrl: String?, val colour: Int)

/**
 * Where the seats go in the drawing (since 1.72), as lists of seat indexes. For [TableView.UPRIGHT] these are the
 * tracker's rows, top to bottom, each left to right. For the tilted views they are columns, left to right, each
 * from the top (far side of the table) to the bottom (your side); a list of one seat is an end of the table.
 */
internal fun tableColumns(players: Int, ends: Boolean, view: TableView): List<List<Int>> {
    val rows = arrangement(players, ends).map { it.distinct() }
    return when (view) {
        TableView.UPRIGHT -> rows
        // Top of the phone to your left: the first row is the left column, its right-hand seat the far one.
        TableView.LEFT -> rows.map { it.reversed() }
        // Top of the phone to your right: the first row is the right column, its left-hand seat the far one.
        TableView.RIGHT -> rows.reversed()
    }
}

/** The tile rectangles and the phone's centre, in pixels, for a table of [w] by [h] pixels at density [d]. */
internal class TableGeometry(val rects: Map<Int, Rect>, val phone: Offset)

private val StackBelow = 120.dp

/** Works out [TableGeometry]; [barRow] is the tracker's [barAfterRow], where the phone sits in the upright view. */
internal fun tableGeometry(columns: List<List<Int>>, view: TableView, w: Float, h: Float, d: Float, barRow: Int): TableGeometry {
    val pad = 10 * d
    val gap = 8 * d
    val rects = mutableMapOf<Int, Rect>()
    if (view == TableView.UPRIGHT) {
        val tile = 62 * d
        val pitch = 72 * d
        val rows = columns.size
        val top = (barRow + 1).coerceIn(1, (rows - 1).coerceAtLeast(1))
        columns.forEachIndexed { r, row ->
            val y = if (r < top) pad + r * pitch else h - pad - tile - (rows - 1 - r) * pitch
            val tw = (w - 2 * pad - gap * (row.size - 1)) / row.size
            row.forEachIndexed { c, seat ->
                val x = pad + c * (tw + gap)
                rects[seat] = Rect(x, y, x + tw, y + tile)
            }
        }
        val topEnd = pad + (top - 1) * pitch + tile
        val bottomStart = h - pad - tile - (rows - top - 1) * pitch
        return TableGeometry(rects, Offset(w / 2, (topEnd + bottomStart) / 2))
    }
    val weights = columns.map { if (it.size == 1) 0.75f else 1f }
    // Without a pair of seats in a column (two players) the phone gets its own room in the middle.
    val room = if (columns.all { it.size == 1 }) 90 * d else 0f
    val avail = w - 2 * pad - gap * (columns.size - 1) - room
    var x = pad
    var pairLeft = 0f
    var pairRight = 0f
    columns.forEachIndexed { c, col ->
        val cw = avail * weights[c] / weights.sum()
        if (col.size == 1) {
            val th = 130 * d
            rects[col[0]] = Rect(x, (h - th) / 2, x + cw, (h - th) / 2 + th)
        } else {
            // Narrow columns stack the picture above the text, which needs a taller tile.
            val th = (if (cw < StackBelow.value * d) 88 else 62) * d
            rects[col[0]] = Rect(x, pad, x + cw, pad + th)
            rects[col[1]] = Rect(x, h - pad - th, x + cw, h - pad)
            if (pairRight == 0f) pairLeft = x
            pairRight = x + cw
        }
        x += cw + gap + if (c == 0) room else 0f
    }
    // The phone sits in the middle of the columns that have room between their seats.
    return TableGeometry(rects, Offset(if (pairRight > 0f) (pairLeft + pairRight) / 2 else w / 2, h / 2))
}

/** The colours of a table: the app's theme, or the tracker's black page ([dark]). */
private class TableColors(val table: Color, val border: Color, val tile: Color, val text: Color, val muted: Color, val phone: Color, val accent: Color)

/**
 * The table as a small drawing (since 1.72): the seats where they really sit around the phone, as the tracker shows
 * them in [view]. Tap a seat for [onTap]; hold it and drag it onto another seat to swap them with [onSwap]. Used
 * by the New game screen and (with [dark]) the tracker's own game settings.
 */
@Composable
internal fun SeatTable(
    seats: List<TableSeat>,
    ends: Boolean,
    view: TableView,
    dark: Boolean = false,
    modifier: Modifier = Modifier,
    onTap: (Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
) {
    val n = seats.size
    val scheme = MaterialTheme.colorScheme
    val colors = if (dark) TableColors(Color(0xFF141414), Color(0xFF333333), Color(0xFF2B2B2B), Color.White, Color(0xFF8C8C8C), Color(0xFF6A6A6A), TrackerAccent)
    else TableColors(scheme.surfaceContainerLow, scheme.outlineVariant, scheme.surfaceContainerHigh, scheme.onSurface, scheme.onSurfaceVariant, scheme.outline, scheme.primary)
    val columns = remember(n, ends, view) { tableColumns(n, ends, view) }
    val tallSeats = remember(columns, view) { if (view == TableView.UPRIGHT) emptySet() else columns.filter { it.size == 1 }.map { it[0] }.toSet() }
    val height = if (view == TableView.UPRIGHT) (columns.size * 72 + 100).dp else 250.dp
    // The drag: which seat is lifted, how far it moved, and which seat is under the finger. Reset when the table changes.
    val bounds = remember(n, ends, view) { arrayOfNulls<Rect>(n) }
    var from by remember(n, ends, view) { mutableStateOf<Int?>(null) }
    var drag by remember(n, ends, view) { mutableStateOf(Offset.Zero) }
    var fingerStart by remember(n, ends, view) { mutableStateOf(Offset.Zero) }
    var hover by remember(n, ends, view) { mutableStateOf<Int?>(null) }
    val tapNow by rememberUpdatedState(onTap)
    val swapNow by rememberUpdatedState(onSwap)
    val haptic = LocalHapticFeedback.current
    val tileShape = RoundedCornerShape(12.dp)

    Layout(
        content = {
            // The phone first, so the seats are drawn above it. Its dot marks the top edge.
            val landscape = view != TableView.UPRIGHT
            Box(Modifier.size(if (landscape) 74.dp else 40.dp, if (landscape) 40.dp else 74.dp).border(2.dp, colors.phone, RoundedCornerShape(8.dp))) {
                Box(
                    Modifier.align(
                        when (view) {
                            TableView.LEFT -> Alignment.CenterStart
                            TableView.RIGHT -> Alignment.CenterEnd
                            TableView.UPRIGHT -> Alignment.TopCenter
                        },
                    ).padding(4.dp).size(6.dp).clip(CircleShape).background(colors.accent),
                )
            }
            // Every tile stays in this same place while it is lifted: only its layer, border and background change.
            seats.forEachIndexed { i, seat ->
                val lifted = from == i
                val target = hover == i
                val tileColor = if (target) colors.accent.copy(alpha = 0.18f).compositeOver(colors.tile) else colors.tile
                Box(
                    Modifier
                        .testTag("seat$i")
                        .zIndex(if (lifted) 1f else 0f)
                        .onGloballyPositioned { bounds[i] = it.boundsInParent() }
                        .pointerInput(i, n, ends, view) { detectTapGestures(onTap = { tapNow(i) }) }
                        .pointerInput(i, n, ends, view) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { at ->
                                    from = i
                                    drag = Offset.Zero
                                    hover = null
                                    fingerStart = (bounds[i]?.topLeft ?: Offset.Zero) + at
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    val to = hover
                                    from = null; drag = Offset.Zero; hover = null
                                    if (to != null && to != i) swapNow(i, to)
                                },
                                onDragCancel = { from = null; drag = Offset.Zero; hover = null },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag += amount
                                    val finger = fingerStart + drag
                                    hover = bounds.indices.firstOrNull { it != i && bounds[it]?.contains(finger) == true }
                                },
                            )
                        }
                        .graphicsLayer {
                            if (lifted) {
                                translationX = drag.x
                                translationY = drag.y
                                scaleX = 1.08f
                                scaleY = 1.08f
                                shadowElevation = 12.dp.toPx()
                                shape = tileShape
                                clip = false
                            }
                        }
                        .clip(tileShape)
                        .background(tileColor)
                        .border(2.dp, if (target) colors.accent else Color.Transparent, tileShape)
                        .padding(6.dp),
                ) {
                    SeatTile(i, seat, i in tallSeats, colors)
                }
            }
        },
        modifier = modifier.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(20.dp)).background(colors.table).border(1.dp, colors.border, RoundedCornerShape(20.dp)),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val geometry = tableGeometry(columns, view, w.toFloat(), h.toFloat(), density, barAfterRow(n, ends))
        val phone = measurables[0].measure(Constraints())
        val tiles = seats.indices.map { i ->
            val r = geometry.rects.getValue(i)
            measurables[i + 1].measure(Constraints.fixed(r.width.toInt(), r.height.toInt()))
        }
        layout(w, h) {
            phone.place((geometry.phone.x - phone.width / 2f).toInt(), (geometry.phone.y - phone.height / 2f).toInt())
            tiles.forEachIndexed { i, p ->
                val r = geometry.rects.getValue(i)
                p.place(IntOffset(r.left.toInt(), r.top.toInt()))
            }
        }
    }
}

/** What is inside one seat's tile: the numbered picture, the name and the deck; stacked in tall or narrow tiles. */
@Composable
private fun SeatTile(i: Int, seat: TableSeat, tall: Boolean, colors: TableColors) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val stacked = tall || maxWidth + 12.dp < StackBelow
        @Composable
        fun picture() = Box(contentAlignment = Alignment.Center) {
            SeatPicture(seat.artUrl, seat.colour, 36.dp)
            Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PanelInk)
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                picture()
                Spacer(Modifier.height(4.dp))
                Text(seat.name, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(seat.deck, color = colors.muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = if (tall) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                picture()
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(seat.name, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(seat.deck, color = colors.muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The small "Turn the table" button (since 1.72): shows how the phone lies and cycles to the next view. */
@Composable
internal fun TableViewButton(view: TableView, tint: Color, onChange: (TableView) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { onChange(view.next) }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(view.label, fontSize = 13.sp, color = tint)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ScreenRotation, "Turn the table", Modifier.size(22.dp), tint = tint)
    }
}
