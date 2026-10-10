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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.Marks
import com.mtgtrader.data.PriceType
import com.mtgtrader.ui.CardTile
import com.mtgtrader.ui.CollectionRowView
import com.mtgtrader.ui.CompactRow
import com.mtgtrader.ui.MarksEditor
import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.DisplayCurrency
import com.mtgtrader.data.ExchangeRates
import com.mtgtrader.data.Money
import com.mtgtrader.ui.CurrencySection
import com.mtgtrader.data.Finish
import com.mtgtrader.data.OtherPrices
import com.mtgtrader.data.PriceSource
import com.mtgtrader.data.PriceSourceStore
import com.mtgtrader.data.Pricing
import com.mtgtrader.data.SourcePrice
import com.mtgtrader.ui.OtherPrices
import com.mtgtrader.ui.PriceGapList
import com.mtgtrader.ui.PriceSourceSection
import com.mtgtrader.ui.SourceComparison
import com.mtgtrader.ui.priceGaps
import com.mtgtrader.ui.sourceTotals
import com.mtgtrader.ui.SourceStatusStrip
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

    // 1.28: the same chart in kroner (about 7.5 times the amounts in euros).
    @Test fun pieByValueInKroner() = inCurrency(DisplayCurrency(AppCurrency.DKK, 7.4612)) { shoot(colors(scale = 10.0), StatMode.VALUE, "pie_value_dkk") }

    private fun inCurrency(d: DisplayCurrency, block: () -> Unit) {
        Money.display = d
        try { block() } finally { Money.display = DisplayCurrency() }
    }

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

    /** 1.27: signed and altered copies in the list, the compact list and the grid, and the card page's checkboxes during a split. */
    @Test fun signedAndAltered() {
        fun row(name: String, set: String, number: String, qty: Int, foil: Boolean = false, signed: Boolean = false, altered: Boolean = false, lang: String = "EN", cond: String = "NM") =
            CollectionRow(
                CollectionItem(card = ref(name, set, number, "rare").copy(fallbackEur = 3.5, fallbackEurFoil = 9.0), foil = foil, quantity = qty, signed = signed, altered = altered, language = lang, condition = cond),
                null,
            )
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CollectionRowView(row("Brainstorm", "ice", "61", 3), PriceType.TREND, "Blue binder") {}
                        CollectionRowView(row("Brainstorm", "ice", "61", 1, signed = true), PriceType.TREND, "Blue binder") {}
                        CollectionRowView(row("Sol Ring", "c21", "263", 1, foil = true, signed = true, altered = true, lang = "DE", cond = "EX"), PriceType.TREND, null) {}
                        CompactRow(row("Counterspell", "mh2", "267", 2, altered = true), PriceType.TREND, "Trade binder") {}
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.width(110.dp)) { CardTile(row("Lightning Bolt", "m10", "146", 1, signed = true), PriceType.TREND, null) {} }
                            Box(Modifier.width(110.dp)) { CardTile(row("Sol Ring", "c21", "263", 1, foil = true, altered = true), PriceType.TREND, null) {} }
                        }
                        MarksEditor(Marks(signed = true), Marks.NONE, quantity = 4, keepMarks = 3, onToggle = {}, onChangeSplit = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/signed_altered.png")
    }

    /** 1.28: collection rows in dollars, and the currency choice in Settings. */
    @Test fun currencies() = inCurrency(DisplayCurrency(AppCurrency.USD, 1.1206)) {
        fun row(name: String, set: String, number: String, qty: Int, eur: Double) =
            CollectionRow(CollectionItem(card = ref(name, set, number, "rare").copy(fallbackEur = eur), foil = false, quantity = qty), null)
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CollectionRowView(row("Brainstorm", "ice", "61", 3, 3.5), PriceType.TREND, "Blue binder") {}
                        CollectionRowView(row("The One Ring", "ltr", "246", 1, 64.9), PriceType.TREND, null) {}
                        CompactRow(row("Counterspell", "mh2", "267", 2, 1.2), PriceType.TREND, "Trade binder") {}
                        CurrencySection(AppCurrency.USD, ExchangeRates("2026-10-09", mapOf("USD" to 1.1206, "DKK" to 7.4751))) {}
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/currencies.png")
    }

    /** 1.29: the price sources compared, the biggest gaps, a card's other prices and the Settings choice. */
    @Test fun priceSources() {
        Pricing.usdPerEuro = 1.12
        fun prices(id: String, tcg: Double, ck: Double) = id to OtherPrices.of(
            listOf(
                SourcePrice(id, "tcgplayer", "NONFOIL", tcg, url = "https://tcgplayer.com"),
                SourcePrice(id, "cardkingdom", "NONFOIL", ck, nm = ck, ex = ck * 0.8, vg = ck * 0.7, g = ck * 0.5, buy = ck * 0.4, url = "https://cardkingdom.com"),
            ),
        )
        Pricing.others = mapOf(prices("ice61", 2.54, 2.99), prices("ltr246", 95.0, 119.99), prices("mh2267", 1.1, 1.49))
        fun row(name: String, set: String, number: String, qty: Int, eur: Double, cond: String = "NM") =
            CollectionRow(CollectionItem(card = ref(name, set, number, "rare").copy(fallbackEur = eur), foil = false, quantity = qty, condition = cond), null)
        val rows = listOf(row("Brainstorm", "ice", "61", 4, 1.62), row("The One Ring", "ltr", "246", 1, 64.9, "EX"), row("Counterspell", "mh2", "267", 2, 1.2), row("Island", "blb", "268", 8, 0.05))
        try {
            compose.setContent {
                MtgColors(dark = false) {
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SourceComparison(sourceTotals(rows, PriceType.TREND), PriceSource.CARDMARKET, ratesKnown = true, selected = PriceSource.TCGPLAYER) {}
                            PriceGapList(priceGaps(rows, PriceType.TREND, count = 3))
                            OtherPrices("ltr246", Finish.NONFOIL, "EX") {}
                        }
                    }
                }
            }
            compose.onRoot().captureRoboImage("src/test/screenshots/price_sources.png")
        } finally {
            Pricing.usdPerEuro = null
            Pricing.others = emptyMap()
        }
    }

    /** 1.30: Settings → Price source, with each source's last download (Card Kingdom's from the GitHub copy). */
    @Test fun priceSourceSettings() {
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        PriceSourceSection(
                            PriceSource.CARD_KINGDOM,
                            PriceSourceStore.SourceStatus(1_760_000_000_000, 1_760_000_000_000, cardKingdomVia = "GitHub copy", cardKingdomListDate = "2026-10-10 03:05:18"),
                        ) {}
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/price_source_settings.png")
    }

    /** 1.30: the price source download bar above the tabs (running, then failed). */
    @Test fun sourceDownloadBar() {
        compose.setContent {
            MtgColors(dark = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceStatusStrip(PriceSourceStore.SourceStatus(running = true, message = "Getting TCGplayer's prices… 1,050 of 2,400 cards", progress = 0.44f))
                    SourceStatusStrip(PriceSourceStore.SourceStatus(running = true, message = "Downloading Card Kingdom's prices… 2.1 of 4.7 MB (from GitHub)", progress = 0.45f))
                    SourceStatusStrip(PriceSourceStore.SourceStatus(cardKingdomError = "HTTP 403; GitHub copy: timeout"))
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/source_download_bar.png")
    }
}
