package com.mtgtrader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mtgtrader.ui.tapOrHoldSteps
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 1.57: in the commander damage detail a hold on − or + changes the damage by 10, as on a life panel (in 1.56 a hold
 * did nothing there). Both use the same taps and holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Release157Test {
    @get:Rule val compose = createComposeRule()

    private val steps = mutableListOf<Int>()

    private fun area(holdOne: Boolean = false) = compose.setContent {
        Box(Modifier.testTag("area").size(200.dp, 100.dp).pointerInput(holdOne) {
            tapOrHoldSteps(holdOne, plusAt = { it.x >= size.width / 2f }, onStep = { steps += it })
        })
    }

    @Test fun aTapStepsByOne() {
        area()
        compose.onNodeWithTag("area").performTouchInput { click(centerRight) }
        compose.onNodeWithTag("area").performTouchInput { click(centerLeft) }
        compose.waitForIdle()
        assertEquals(listOf(1, -1), steps)
    }

    @Test fun aHoldStepsByTenAndKeepsGoing() {
        area()
        compose.onNodeWithTag("area").performTouchInput {
            down(centerRight)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            move()
            advanceEventTime(800)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(10, 10), steps)
        steps.clear()
        compose.onNodeWithTag("area").performTouchInput { longClick(centerLeft) }
        compose.waitForIdle()
        assertEquals(listOf(-10), steps)
    }

    @Test fun byOneFastWhenSetSo() {
        area(holdOne = true)
        compose.onNodeWithTag("area").performTouchInput { longClick(centerRight) }
        compose.waitForIdle()
        assertEquals(1, steps.first())
        assert(steps.all { it == 1 })
    }
}
