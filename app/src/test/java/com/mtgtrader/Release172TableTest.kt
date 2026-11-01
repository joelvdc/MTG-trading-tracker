package com.mtgtrader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mtgtrader.data.TableView
import com.mtgtrader.ui.MtgColors
import com.mtgtrader.ui.SeatTable
import com.mtgtrader.ui.TableSeat
import com.mtgtrader.ui.tableColumns
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 1.72: where the seats go in the table drawing. */
class Release172TableTest {
    @Test fun fourPlayersTiltedLeftSitClockwiseFromBottomLeft() {
        // Left column from the top: seat 2 then seat 1; right column: seat 3 then seat 4.
        assertEquals(listOf(listOf(1, 0), listOf(2, 3)), tableColumns(4, false, TableView.LEFT))
    }

    @Test fun fourPlayersTiltedRightSitClockwiseFromTopRight() {
        // Left column from the top: seat 4 then seat 3; right column: seat 1 then seat 2.
        assertEquals(listOf(listOf(3, 2), listOf(0, 1)), tableColumns(4, false, TableView.RIGHT))
    }

    @Test fun uprightIsTheTrackersRows() {
        assertEquals(listOf(listOf(0, 1), listOf(3, 2)), tableColumns(4, false, TableView.UPRIGHT))
        assertEquals(listOf(listOf(0, 1), listOf(2)), tableColumns(3, false, TableView.UPRIGHT))
        assertEquals(listOf(listOf(0), listOf(3, 1), listOf(2)), tableColumns(4, true, TableView.UPRIGHT))
    }

    @Test fun threePlayersTiltedLeftHaveSeatThreeAloneOnTheRight() {
        assertEquals(listOf(listOf(1, 0), listOf(2)), tableColumns(3, false, TableView.LEFT))
    }

    @Test fun fourWithEndsTiltedLeftHaveSeatOneAndSeatThreeAtTheEnds() {
        assertEquals(listOf(listOf(0), listOf(1, 3), listOf(2)), tableColumns(4, true, TableView.LEFT))
    }

    @Test fun twoPlayersFaceEachOtherAcrossTheTable() {
        assertEquals(listOf(listOf(0), listOf(1)), tableColumns(2, false, TableView.LEFT))
        assertEquals(listOf(listOf(1), listOf(0)), tableColumns(2, false, TableView.RIGHT))
        assertEquals(listOf(listOf(0), listOf(1)), tableColumns(2, false, TableView.UPRIGHT))
    }
}

/** 1.72: a held seat dragged onto another swaps them; a tap picks the seat. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xhdpi")
class Release172TableDragTest {
    @get:Rule val compose = createComposeRule()

    private val swaps = mutableListOf<Pair<Int, Int>>()
    private val taps = mutableListOf<Int>()

    private fun table() = compose.setContent {
        MtgColors(dark = false) {
            Box(Modifier.padding(16.dp)) {
                SeatTable(
                    seats = (1..4).map { TableSeat("Player $it", "No deck picked", null, it - 1) },
                    ends = false, view = TableView.LEFT, onTap = { taps += it }, onSwap = { a, b -> swaps += a to b },
                )
            }
        }
    }

    @Test fun holdingSeatFourAndDraggingItOntoSeatThreeSwapsThem() {
        table()
        val target = compose.onNodeWithTag("seat2").fetchSemanticsNode().boundsInRoot.center
        val source = compose.onNodeWithTag("seat3").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("seat3").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveTo(center + (target - source) / 2f)
            moveTo(center + (target - source))
            up()
        }
        assertEquals(listOf(3 to 2), swaps)
        assertEquals(emptyList<Int>(), taps)
    }

    @Test fun droppingOnNothingSwapsNothing() {
        table()
        compose.onNodeWithTag("seat3").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveTo(center + Offset(0f, 5f))
            up()
        }
        assertEquals(emptyList<Pair<Int, Int>>(), swaps)
    }

    @Test fun aTapPicksTheSeat() {
        table()
        compose.onNodeWithTag("seat1").performClick()
        assertEquals(listOf(1), taps)
    }
}
