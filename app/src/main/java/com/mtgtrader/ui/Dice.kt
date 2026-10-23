package com.mtgtrader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgtrader.R
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveSeat
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Dicefont by Franco Ponticelli (SIL Open Font License; see assets/licenses/dicefont.txt), as Lifetap's dice. Since 1.59. */
private val DiceFont = FontFamily(Font(R.font.dicefont))

/** The dice Dicefont draws with their numbers on (d4, d6 with dots, d8, d10, d12, d20) and the coin (a d2). */
internal val GLYPH_DICE = setOf(2, 4, 6, 8, 10, 12, 20)

/**
 * Dicefont's glyph for a [sides]-sided die showing [value] (since 1.59): the d6 with dots, the others with the number
 * on the die. Its glyphs are numbered by their names in alphabetical order ("d20-1", "d20-10", … "d20-19", "d20-2", …).
 * Null for a die the font hasn't got, or a value it can't show.
 */
internal fun dieGlyph(sides: Int, value: Int): String? {
    if (sides !in GLYPH_DICE || value !in 1..sides) return null
    if (sides == 6) return (if (value == 6) 0xF18F else 0xF13F + value - 1).toChar().toString()
    val base = when (sides) {
        2 -> 0xF117
        4 -> 0xF12D
        8 -> 0xF137
        10 -> 0xF101
        12 -> 0xF10B
        else -> 0xF119
    }
    return (base + (1..sides).map { "$it" }.sorted().indexOf("$value")).toChar().toString()
}

/** A die drawn with Dicefont at [size], white unless told. */
@Composable
internal fun DieGlyph(glyph: String, size: Dp, modifier: Modifier = Modifier, colour: Color = Color.White) {
    val sp = with(LocalDensity.current) { size.toSp() }
    Text(
        glyph, color = colour, fontFamily = DiceFont, fontSize = sp, modifier = modifier,
        style = TextStyle(
            lineHeight = sp, platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
    )
}

/** Two crossed swords: the random opponent, as in Lifetap. */
@Composable
internal fun CrossedSwords(size: Dp, modifier: Modifier = Modifier, colour: Color = Color.White) = Canvas(modifier.size(size)) {
    val w = this.size.width
    val line = Stroke(w * 0.055f, join = StrokeJoin.Round)
    // One sword upright (point at the top), drawn twice, turned 45° each way.
    val blade = Path().apply {
        moveTo(0.5f * w, 0.02f * w); lineTo(0.56f * w, 0.12f * w); lineTo(0.56f * w, 0.66f * w)
        lineTo(0.44f * w, 0.66f * w); lineTo(0.44f * w, 0.12f * w); close()
    }
    listOf(45f, -45f).forEach { angle ->
        rotate(angle) {
            scale(0.92f) {
                drawPath(blade, colour, style = line)
                drawLine(colour, Offset(0.33f * w, 0.7f * w), Offset(0.67f * w, 0.7f * w), w * 0.07f, StrokeCap.Round)
                drawLine(colour, Offset(0.5f * w, 0.72f * w), Offset(0.5f * w, 0.88f * w), w * 0.075f, StrokeCap.Round)
                drawCircle(colour, w * 0.045f, Offset(0.5f * w, 0.93f * w))
            }
        }
    }
}

/** The quick roll's choices around the die in the middle bar (since 1.59, as Lifetap's), from the top round to the bottom. */
internal enum class RingChoice(val sides: Int, val label: String) {
    D20(20, "d20"), D12(12, "d12"), D10(10, "d10"), D8(8, "d8"), D6(6, "d6"), D4(4, "d4"), COIN(2, "Coin"),
    OPPONENT(0, "Random opponent"), PLAYER(0, "Random player"),
}

/** The choices' light grey, as in Lifetap (the held die stays white). */
private val RingGrey = Color(0xFFC8CBD0)

/** How far the choices are from the die, and how near the finger must be to one. */
internal val RING_RADIUS = 126.dp
internal val RING_REACH = 34.dp

/**
 * Where the [n] choices sit around the die at [centre] ([radius] away): a half circle from straight above to straight
 * below, on the side toward the middle of the screen ([toLeft]).
 */
internal fun ringPositions(centre: Offset, radius: Float, n: Int, toLeft: Boolean): List<Offset> = List(n) { k ->
    val a = Math.toRadians(if (n == 1) 90.0 else 180.0 * k / (n - 1))
    val x = radius * sin(a).toFloat()
    Offset(centre.x + if (toLeft) -x else x, centre.y - radius * cos(a).toFloat())
}

/** The choice under the finger at [finger] (the nearest within [reach]), or null. */
internal fun ringChoiceAt(positions: List<Offset>, finger: Offset, reach: Float): Int? =
    positions.withIndex().filter { hypot(it.value.x - finger.x, it.value.y - finger.y) <= reach }
        .minByOrNull { hypot(it.value.x - finger.x, it.value.y - finger.y) }?.index

/**
 * The ring while the die is held: where the die is and the finger (both in the window's coordinates, set by the die
 * button), and where the ring is drawn (set by [DieRingOverlay]).
 */
internal class DieRing {
    var centre by mutableStateOf<Offset?>(null)
    var finger by mutableStateOf<Offset?>(null)
    /** The overlay's top left corner in the window, its width, and the ring's sizes in pixels. */
    var origin by mutableStateOf(Offset.Zero)
    var width by mutableStateOf(0f)
    var radius = 0f
    var reach = 0f
    val open get() = centre != null

    /** The choices' centres, in the overlay. */
    fun positions(): List<Offset> {
        val local = (centre ?: return emptyList()) - origin
        return ringPositions(local, radius, RingChoice.entries.size, toLeft = local.x > width / 2f)
    }

    /** The choice under the finger, or null. */
    fun lit(): RingChoice? = finger?.let { f -> ringChoiceAt(positions(), f - origin, reach)?.let { RingChoice.entries[it] } }

    fun close() {
        centre = null
        finger = null
    }
}

/** The choice's picture: the die (with its top number) or the coin from Dicefont, the swords or the shuffle arrows. */
@Composable
internal fun RingChoiceIcon(c: RingChoice, size: Dp, colour: Color = Color.White) = when (c) {
    RingChoice.OPPONENT -> CrossedSwords(size * 0.95f, colour = colour)
    RingChoice.PLAYER -> Icon(Icons.Outlined.Shuffle, c.label, Modifier.size(size * 0.85f), tint = colour)
    else -> DieGlyph(dieGlyph(c.sides, c.sides)!!, size, colour = colour)
}

/**
 * The ring over the table (since 1.59): the table darkened, the choices gliding out of the held die into a half circle
 * around it, the one under the finger growing and turning the tracker's blue. Letting go is handled by the die
 * button ([dieRingGesture]).
 */
@Composable
internal fun DieRingOverlay(ring: DieRing, buzz: Buzz = Buzz.OFF) {
    if (!ring.open) return
    val density = LocalDensity.current
    ring.radius = with(density) { RING_RADIUS.toPx() }
    ring.reach = with(density) { RING_REACH.toPx() }
    // Opening: the table darkens and the choices glide out of the die.
    val opening = remember { Animatable(0f) }
    LaunchedEffect(Unit) { opening.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    val p = opening.value
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f * p)).onGloballyPositioned {
            ring.origin = it.localToRoot(Offset.Zero)
            ring.width = it.size.width.toFloat()
        },
    ) {
        val positions = ring.positions()
        if (positions.isEmpty()) return@Box
        val from = (ring.centre ?: return@Box) - ring.origin
        // The held die stays bright above the darkened table, as in Lifetap.
        val dieHalf = with(density) { 20.dp.toPx() }
        Icon(
            Icons.Outlined.Casino, null,
            Modifier.offset { IntOffset((from.x - dieHalf).roundToInt(), (from.y - dieHalf).roundToInt()) }.size(40.dp).padding(4.dp),
            tint = Color.White,
        )
        val lit = ring.lit()
        LaunchedEffect(lit) { if (lit != null) buzz.tap() }
        RingChoice.entries.forEachIndexed { i, c ->
            val on = c == lit
            val grow by animateFloatAsState(if (on) 1.3f else 1f, tween(140), label = "ring")
            val tint by animateColorAsState(if (on) TrackerAccent else RingGrey, tween(140), label = "ringTint")
            val box = 52.dp
            val half = with(density) { (box / 2).toPx() }
            val at = from + (positions[i] - from) * p
            Box(
                Modifier.offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }.size(box).scale(grow * (0.6f + 0.4f * p)).alpha(p),
                contentAlignment = Alignment.Center,
            ) { RingChoiceIcon(c, 40.dp, tint) }
        }
    }
}

/** The players a random opponent is picked from for [me] (since 1.59): everyone else still in the game. */
internal fun opponentPool(g: LiveGame, me: Int): List<LiveSeat> = g.alive.filter { it.seat != me }

/**
 * The die button's taps and holds (since 1.59, as Lifetap's): a tap [onTap]; a hold opens the [ring] around the button
 * ([onOpen]), sliding lights up the choice under the finger, and letting go on one picks it ([onPick]); letting go
 * anywhere else does nothing.
 */
internal fun Modifier.dieRingGesture(ring: DieRing, onTap: () -> Unit, onOpen: () -> Unit, onPick: (RingChoice) -> Unit): Modifier = composed {
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onGloballyPositioned { coords[0] = it }.pointerInput(ring) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var up: PointerInputChange? = null
            val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { up = waitForUpOrCancellation(); true }
            if (early != null) {
                if (up != null) onTap()
                return@awaitEachGesture
            }
            val c = coords[0] ?: return@awaitEachGesture
            ring.centre = c.localToRoot(Offset(size.width / 2f, size.height / 2f))
            ring.finger = c.localToRoot(down.position)
            onOpen()
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                ring.finger = c.localToRoot(change.position)
            }
            val pick = ring.lit()
            ring.close()
            pick?.let(onPick)
        }
    }
}
