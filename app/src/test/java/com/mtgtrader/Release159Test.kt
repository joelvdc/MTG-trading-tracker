package com.mtgtrader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mtgtrader.data.LiveGame
import com.mtgtrader.data.LiveSeat
import com.mtgtrader.ui.DieRing
import com.mtgtrader.ui.DieRingOverlay
import com.mtgtrader.ui.RING_RADIUS
import com.mtgtrader.ui.RingChoice
import com.mtgtrader.ui.dieGlyph
import com.mtgtrader.ui.dieRingGesture
import com.mtgtrader.ui.newRoll
import com.mtgtrader.ui.opponentPool
import com.mtgtrader.ui.ringChoiceAt
import com.mtgtrader.ui.ringPositions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin

/** 1.59: Dicefont's dice, the ring of dice behind the held die, and asking who you are for a random opponent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Release159Test {
    @get:Rule val compose = createComposeRule()

    private fun code(s: String?) = s?.single()?.code

    @Test fun eachDieShowsItsValueWithDicefont() {
        // As Dicefont's stylesheet numbers them.
        assertEquals(0xF11E, code(dieGlyph(20, 14)))
        assertEquals(0xF125, code(dieGlyph(20, 20)))
        assertEquals(0xF119, code(dieGlyph(20, 1)))
        assertEquals(0xF102, code(dieGlyph(10, 10)))
        assertEquals(0xF103, code(dieGlyph(10, 2)))
        assertEquals(0xF10B, code(dieGlyph(12, 1)))
        assertEquals(0xF116, code(dieGlyph(12, 9)))
        assertEquals(0xF13E, code(dieGlyph(8, 8)))
        assertEquals(0xF12F, code(dieGlyph(4, 3)))
        assertEquals(0xF118, code(dieGlyph(2, 2)))
        // The d6 with dots.
        assertEquals(0xF140, code(dieGlyph(6, 2)))
        assertEquals(0xF18F, code(dieGlyph(6, 6)))
        // No glyph for other dice or values off the die.
        assertNull(dieGlyph(100, 7))
        assertNull(dieGlyph(20, 21))
        repeat(50) { assertTrue(newRoll(6).value.toInt() in 1..6) }
        assertTrue(newRoll(2).value in setOf("Heads", "Tails"))
    }

    @Test fun theRingGoesFromAboveRoundToBelow() {
        val p = ringPositions(Offset(500f, 500f), 100f, RingChoice.entries.size, toLeft = true)
        assertEquals(Offset(500f, 400f), p.first().round())
        assertEquals(Offset(400f, 500f), p[4].round())
        assertEquals(Offset(500f, 600f), p.last().round())
        // Toward the middle: on the right when the die is on the left.
        assertEquals(Offset(600f, 500f), ringPositions(Offset(500f, 500f), 100f, 9, toLeft = false)[4].round())
        assertEquals(3, ringChoiceAt(p, p[3] + Offset(10f, -5f), 30f))
        assertNull(ringChoiceAt(p, Offset(500f, 500f), 30f))
    }

    private fun Offset.round() = Offset(Math.round(x).toFloat(), Math.round(y).toFloat())

    private val picks = mutableListOf<RingChoice>()
    private var taps = 0

    private fun table(ring: DieRing) = compose.setContent {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.CenterEnd).size(48.dp).testTag("die").dieRingGesture(ring, onTap = { taps++ }, onOpen = {}, onPick = { picks += it }))
            DieRingOverlay(ring)
        }
    }

    @Test fun holdSlideAndLetGoRollsThatDie() {
        val ring = DieRing()
        table(ring)
        compose.onNodeWithTag("die").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            move()
        }
        compose.waitForIdle()
        assertTrue(ring.open)
        // Slide to the d8: the fourth choice, 67.5° round from straight above, on the left.
        compose.onNodeWithTag("die").performTouchInput {
            val r = RING_RADIUS.toPx()
            val a = Math.toRadians(67.5)
            moveTo(center + Offset(-r * sin(a).toFloat(), -r * cos(a).toFloat()))
        }
        compose.waitForIdle()
        assertEquals(RingChoice.D8, ring.lit())
        compose.onNodeWithTag("die").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(RingChoice.D8), picks)
        assertFalse(ring.open)
        assertEquals(0, taps)
    }

    @Test fun lettingGoElsewhereDoesNothingAndATapOpensThePage() {
        val ring = DieRing()
        table(ring)
        compose.onNodeWithTag("die").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            move()
            up()
        }
        compose.waitForIdle()
        assertTrue(picks.isEmpty())
        assertFalse(ring.open)
        compose.onNodeWithTag("die").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, taps)
    }

    @Test fun theOpponentIsNeverYouNorSomeoneOut() {
        val g = LiveGame(
            uid = "g", startedAt = 0,
            seats = (0 until 4).map { LiveSeat(seat = it, name = "P$it", life = 40, out = it == 3) },
        )
        assertEquals(listOf(0, 2), opponentPool(g, 1).map { it.seat })
    }
}
