package com.mtgtrader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.OrderCandidate
import com.mtgtrader.data.OrderLine
import com.mtgtrader.data.StatEntry
import com.mtgtrader.data.StatMode
import com.mtgtrader.ui.MtgColors
import com.mtgtrader.ui.OrderReview
import com.mtgtrader.ui.PieChart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screens drawn on the computer with sample data, compared with the pictures in app/src/test/screenshots.
 *   gradlew recordRoborazziDebug   – (re)draws the reference pictures after an intended change
 *   gradlew verifyRoborazziDebug   – fails when a screen looks different from its picture
 * Plain `testDebugUnitTest` runs these without comparing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    // The colour groups of a real collection (Oct 2026): copies and value.
    private fun colors(scale: Double = 1.0) = listOf(
        StatEntry("W", "White", 745, 1562.63 * scale), StatEntry("U", "Blue", 689, 1096.30 * scale),
        StatEntry("B", "Black", 699, 1441.55 * scale), StatEntry("R", "Red", 528, 866.02 * scale),
        StatEntry("G", "Green", 660, 1117.16 * scale), StatEntry("M", "Multicolor", 726, 1053.50 * scale),
        StatEntry("C", "Colorless", 670, 1874.40 * scale), StatEntry("L", "Lands", 1102, 2181.67 * scale),
    )

    private fun shoot(entries: List<StatEntry>, mode: StatMode, file: String) {
        compose.setContent {
            MtgColors(dark = false) {
                // The pie sits in a card on the stats screen: 16 dp screen margin + 14 dp card padding.
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(30.dp)) { PieChart(entries, mode) {} }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test fun pieByCards() = shoot(colors(), StatMode.CARDS, "pie_cards")

    @Test fun pieByValue() = shoot(colors(), StatMode.VALUE, "pie_value")

    @Test fun pieByValueLarge() = shoot(colors(scale = 10.0), StatMode.VALUE, "pie_value_large")

    // Phones set to bigger text.
    @Test @Config(fontScale = 1.3f) fun pieByValueBigText() = shoot(colors(scale = 10.0), StatMode.VALUE, "pie_value_big_text")

    private fun ref(name: String, set: String, number: String, rarity: String = "common") =
        CardRef(set + number, name, set, set.uppercase(), number, rarity, null, null, 1.0, 2.0, true, true)

    private fun order(name: String, set: String, number: String, price: Double?, qty: Int = 1, condition: String = "NM", foil: Boolean = false, signed: Boolean = false) =
        OrderLine(set.uppercase(), "Set $set", name, number, qty, price, condition, "EN", foil, signed, false)

    @Test fun orderReview() {
        val cands = listOf(
            OrderCandidate(0, order("Echocasting Symposium", "sos", "044", 2.31), ref("Echocasting Symposium", "sos", "44", "rare"), false, false),
            OrderCandidate(1, order("Decorum Dissertation", "sos", "078", 6.42, condition = "EX", foil = true), ref("Decorum Dissertation", "sos", "78", "mythic"), false, false),
            OrderCandidate(2, order("Clifftop Lookout", "blb", "168", 0.24, qty = 2), ref("Clifftop Lookout", "blb", "168"), false, false),
            OrderCandidate(3, order("Lander // Human Soldier", "eoe", "T 04/02", 0.28), ref("Lander", "teoe", "4"), true, false),
            OrderCandidate(4, order("Island", "blb", "268", 0.05, qty = 4, signed = true), ref("Island", "blb", "268"), false, true),
            OrderCandidate(5, order("Beast // Kavu", "dmc", "T16/12", 0.24), ref("Beast", "tkld", "1"), true, false, guessed = true),
            OrderCandidate(6, order("Mystery Card", "xyz", "007", 1.00), null, false, false),
        )
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    OrderReview(
                        candidates = cands, checked = setOf(0, 1, 2), onToggle = {}, skipTokens = true, onSkipTokens = {},
                        skipBasics = true, onSkipBasics = {}, owned = mapOf("blb168" to 3), onChoose = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/order_review.png")
    }
}
