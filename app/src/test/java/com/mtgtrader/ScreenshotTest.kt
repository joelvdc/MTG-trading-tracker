package com.mtgtrader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.graphics.asImageBitmap
import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.DisplayCurrency
import com.mtgtrader.data.ExchangeRates
import com.mtgtrader.data.Finish
import com.mtgtrader.data.Marks
import com.mtgtrader.data.Money
import com.mtgtrader.data.OrderCandidate
import com.mtgtrader.data.OrderFile
import com.mtgtrader.data.OrderFormat
import com.mtgtrader.data.OrderLine
import com.mtgtrader.data.OrderWorth
import com.mtgtrader.data.OtherPrices
import com.mtgtrader.data.PackageCheck
import com.mtgtrader.data.Precons
import com.mtgtrader.data.PriceSource
import com.mtgtrader.data.PriceSourceStore
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.Pricing
import com.mtgtrader.data.ProfitLoss
import com.mtgtrader.data.PurchaseOrder
import com.mtgtrader.data.PurchasePrices
import com.mtgtrader.data.ScannedCard
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SourcePrice
import com.mtgtrader.data.StatEntry
import com.mtgtrader.data.StatMode
import com.mtgtrader.data.WishlistItem
import com.mtgtrader.data.WishlistOwned
import com.mtgtrader.data.WishlistRow
import com.mtgtrader.ui.CardTile
import com.mtgtrader.ui.CollectionRowView
import com.mtgtrader.ui.CompactRow
import com.mtgtrader.ui.ConditionSection
import com.mtgtrader.ui.CurrencySection
import com.mtgtrader.ui.MarksEditor
import com.mtgtrader.ui.MtgColors
import com.mtgtrader.ui.OrderList
import com.mtgtrader.ui.OrderReview
import com.mtgtrader.ui.OtherPrices
import com.mtgtrader.ui.PackageCheckList
import com.mtgtrader.ui.PieChart
import com.mtgtrader.ui.PreconList
import com.mtgtrader.ui.PriceGapList
import com.mtgtrader.ui.PriceSourceSection
import com.mtgtrader.ui.ProfitCard
import com.mtgtrader.ui.PurchasePricePlan
import com.mtgtrader.ui.SourceComparison
import com.mtgtrader.ui.SourceStatusStrip
import com.mtgtrader.ui.asCollectionRow
import com.mtgtrader.ui.priceGaps
import com.mtgtrader.ui.sourceTotals
import com.mtgtrader.ui.wishLabel
import com.mtgtrader.ui.wishTotalLine
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
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png", roborazziOptions = screenshotOptions)
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
        compose.onRoot().captureRoboImage("src/test/screenshots/order_review.png", roborazziOptions = screenshotOptions)
    }

    /** 1.32: a Cardmarket order in the same review: a playset, a language number the app doesn't know, a product without a card. */
    @Test fun orderReviewCardmarket() {
        fun cm(name: String, product: Int, price: Double, qty: Int = 1, condition: String = "NM", lang: String = "EN", foil: Boolean = false, playset: Boolean = false, languageKnown: Boolean = true) =
            OrderLine("", "", name, "", qty, price, condition, lang, foil, false, false, productId = product, playset = playset, languageKnown = languageKnown)
        val cands = listOf(
            OrderCandidate(0, cm("Journey to Eternity", 315288, 6.98, condition = "EX"), ref("Journey to Eternity // Atzal, Cave of Eternity", "rix", "160", "rare"), false, false),
            OrderCandidate(1, cm("Marvin, Murderous Mimic", 788181, 2.98, foil = true), ref("Marvin, Murderous Mimic", "dsk", "253", "rare"), false, false),
            OrderCandidate(2, cm("Cavalier of Thorns", 378864, 12.0, qty = 4, lang = "ES", playset = true), ref("Cavalier of Thorns", "m20", "167", "mythic"), false, false),
            OrderCandidate(3, cm("Cavalier of Dawn", 378651, 2.55, languageKnown = false), ref("Cavalier of Dawn", "m20", "10", "mythic"), false, false),
            OrderCandidate(4, cm("Vanquish the Horde (V.1)", 575682, 3.0), ref("Vanquish the Horde", "mid", "41", "rare"), false, false, guessed = true),
            OrderCandidate(5, cm("", 999001, 1.5), null, false, false),
        )
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    OrderReview(
                        candidates = cands, checked = setOf(0, 1, 2, 3), onToggle = {}, skipTokens = true, onSkipTokens = {},
                        skipBasics = true, onSkipBasics = {}, owned = emptyMap(), onChoose = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/order_review_cardmarket.png", roborazziOptions = screenshotOptions)
    }

    /** 1.32: scanned cards against an order: problems first, then extras, what to check by hand, and the rest folded away. */
    @Test fun packageCheck() {
        fun scan(id: Long, name: String, set: String, number: String, qty: Int = 1, lang: String = "EN", exact: Boolean = true) =
            ScannedCard(id = id, card = ref(name, set, number, "rare"), foil = false, quantity = qty, language = lang, exactPrinting = exact)
        val cands = listOf(
            OrderCandidate(0, order("Sol Ring", "cmm", "400", 1.20, qty = 2), ref("Sol Ring", "cmm", "400", "uncommon"), false, false),
            OrderCandidate(1, order("Counterspell", "dmr", "045", 1.50), ref("Counterspell", "dmr", "45", "uncommon"), false, false),
            OrderCandidate(2, order("Thran Turbine", "usg", "311", 1.74), ref("Thran Turbine", "usg", "311", "uncommon"), false, false),
            OrderCandidate(3, order("Crop Rotation", "ulg", "098", 3.00), ref("Crop Rotation", "ulg", "98"), false, false),
            OrderCandidate(4, order("Rhystic Study", "pcy", "045", 30.0, condition = "EX", foil = true), ref("Rhystic Study", "pcy", "45"), false, false),
            OrderCandidate(5, order("Brainstorm", "ice", "061", 0.90, qty = 3), ref("Brainstorm", "ice", "61"), false, false),
            OrderCandidate(6, order("Swords to Plowshares", "sta", "010", 2.10), ref("Swords to Plowshares", "sta", "10", "uncommon"), false, false),
        )
        val scans = listOf(
            scan(1, "Sol Ring", "cmm", "400", qty = 2), scan(2, "Thran Turbine", "usg", "311", lang = "DE"), scan(3, "Crop Rotation", "ddr", "7"),
            scan(4, "Rhystic Study", "pcy", "45"), scan(5, "Brainstorm", "ice", "61"), scan(6, "Brainstorm", "mmq", "61", exact = false),
            scan(7, "Lightning Bolt", "m10", "146", qty = 2),
        )
        val result = PackageCheck.check(cands, scans, accepted = setOf(6))
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    PackageCheckList(result, scanned = scans.sumOf { it.quantity }, showReceived = false, onShowReceived = {}, onAccept = {}, onUndo = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/package_check.png", roborazziOptions = screenshotOptions)
    }

    /** 1.32: purchase prices from order files, before anything is changed. */
    @Test fun purchasePrices() {
        var id = 1L
        fun stack(name: String, set: String, number: String, product: Int, qty: Int = 1, foil: Boolean = false, lang: String = "EN", price: Double? = null, binder: Long = 1) =
            CollectionItem(id = id++, card = ref(name, set, number, "rare").copy(cardmarketId = product), foil = foil, quantity = qty, language = lang, purchasePrice = price, binderId = binder)
        fun bought(name: String, product: Int, price: Double, qty: Int = 1, date: String = "2023-05-19") =
            OrderLine("", "Set", name, "", qty, price, "NM", "EN", false, false, false, productId = product, versionKnown = false, date = date)
        val stacks = listOf(
            stack("Snapcaster Mage", "isd", "78", 10), stack("Sign in Blood", "m15", "114", 11, qty = 5), stack("Hallowed Fountain", "rtr", "241", 12, qty = 4, price = 2.0),
            stack("Godless Shrine", "gtc", "242", 13), stack("Godless Shrine", "gtc", "242", 13, lang = "PT", binder = 2), stack("Temple of Malady", "jou", "165", 14, price = 29.16),
        )
        val lines = listOf(
            bought("Snapcaster Mage", 10, 28.0, date = "2014-11-23"), bought("Snapcaster Mage", 10, 46.0, date = "2018-04-27"), bought("Sign in Blood", 11, 0.28),
            bought("Hallowed Fountain", 12, 4.0), bought("Godless Shrine", 13, 9.0, date = "2023-01-21"), bought("Temple of Malady", 14, 29.16),
            bought("Force of Will", 99, 45.0, qty = 3, date = "2014-11-20"),
        )
        val plan = PurchasePrices.plan(lines, stacks, overwrite = false)
        val binders = listOf(Binder(id = 1, name = "The Box"), Binder(id = 2, name = "Trade binder"))
        val files = listOf(OrderFile(OrderFormat.CARDMARKET_MONTHLY, lines), OrderFile(OrderFormat.CARDTRADER, emptyList()))
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    PurchasePricePlan(
                        plan = plan, files = files, failed = listOf("holiday.jpg: not an order file the app knows"), binders = binders, overwrite = false, onOverwrite = {},
                        choices = emptyMap(), onChoice = { _, _, _ -> }, skipped = emptySet(), onSkip = { _, _ -> },
                        showKept = true, onShowKept = {}, showNotOwned = true, onShowNotOwned = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/purchase_prices.png", roborazziOptions = screenshotOptions)
    }

    /** 1.33: the order history, with a precon and an order whose cards have no price today. */
    @Test fun orderHistory() {
        fun order(id: Long, shop: String, title: String, date: String, paid: Double, cards: Int) =
            PurchaseOrder(id = id, shop = shop, number = title, title = title, date = date, paid = paid, cards = cards, lines = "[]")
        val orders = listOf(
            order(1, "CardTrader", "CardTrader order 20260623hxrahx", "2026-06-23", 54.20, 35),
            order(2, "Precon", "Deep Clue Sea", "2024-02-09", 46.92, 86),
            order(3, "Cardmarket", "Cardmarket shipment 1095364978", "2022-11-20", 48.00, 4),
            order(4, "Cardmarket", "Cardmarket shipment 6625697", "2014-08-09", 6.24, 12),
        )
        val worth = mapOf(1L to OrderWorth(61.75, 35, 0, 54.20), 2L to OrderWorth(82.93, 86, 0, 46.92), 3L to OrderWorth(2.40, 4, 0, 48.00), 4L to OrderWorth(0.0, 0, 12, 0.0))
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) { OrderList(orders, worth, onOpen = {}) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/order_history.png", roborazziOptions = screenshotOptions)
    }

    /** 1.33: paid against worth on the value screen, the condition setting, an adjusted price in the list, and the precon list. */
    @Test fun profitConditionsAndPrecons() {
        fun row(name: String, cond: String) = CollectionRow(
            CollectionItem(card = ref(name, "usg", "311", "rare").copy(fallbackEur = 10.0), foil = false, quantity = 2, condition = cond), null,
        )
        Pricing.conditionPct = Settings.DEFAULT_CONDITION_PCTS
        try {
            compose.setContent {
                MtgColors(dark = false) {
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfitCard(ProfitLoss(paid = 7580.48, worth = 9471.06, counted = 4876, copies = 5819, best = emptyList(), worst = emptyList()))
                            ConditionSection(on = true, pcts = Settings.DEFAULT_CONDITION_PCTS, source = PriceSource.CARDMARKET, onSwitch = {}, onPct = { _, _ -> })
                            CollectionRowView(row("Thran Turbine", "EX"), PriceType.TREND, "The Box") {}
                            CollectionRowView(row("Grim Monolith", "NM"), PriceType.TREND, "The Box") {}
                            PreconList(
                                listOf(
                                    Precons.Info("FRC", "MultiverseReforged_FRC", "Multiverse Reforged", "2026-10-02", Precons.TYPE),
                                    Precons.Info("MKC", "DeepClueSea_MKC", "Deep Clue Sea", "2024-02-09", Precons.TYPE),
                                ),
                                onPick = {},
                            )
                        }
                    }
                }
            }
            compose.onRoot().captureRoboImage("src/test/screenshots/profit_conditions_precons.png", roborazziOptions = screenshotOptions)
        } finally {
            Pricing.conditionPct = null
        }
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
        compose.onRoot().captureRoboImage("src/test/screenshots/signed_altered.png", roborazziOptions = screenshotOptions)
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
        compose.onRoot().captureRoboImage("src/test/screenshots/currencies.png", roborazziOptions = screenshotOptions)
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
            compose.onRoot().captureRoboImage("src/test/screenshots/price_sources.png", roborazziOptions = screenshotOptions)
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
        compose.onRoot().captureRoboImage("src/test/screenshots/price_source_settings.png", roborazziOptions = screenshotOptions)
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
        compose.onRoot().captureRoboImage("src/test/screenshots/source_download_bar.png", roborazziOptions = screenshotOptions)
    }

    /** 1.35: wishlist cards marked as ordered, and the line above the wishlist; 1.36: the "Ordered" box on each card. */
    @Test fun wishlistOrdered() {
        val oct15 = 1_792_065_600_000L
        fun wish(id: Long, name: String, set: String, number: String, qty: Int, eur: Double, orderedAt: Long? = null, anyPrinting: Boolean = true) =
            WishlistRow(WishlistItem(id = id, card = ref(name, set, number, "rare").copy(fallbackEur = eur), quantity = qty, anyPrinting = anyPrinting, orderedAt = orderedAt), null)
        val rows = listOf(
            wish(1, "Rhystic Study", "jmp", "169", 1, 38.0, orderedAt = oct15),
            wish(2, "Smothering Tithe", "rna", "22", 1, 17.5),
            wish(3, "Arcane Signet", "cmm", "367", 3, 0.6, orderedAt = oct15, anyPrinting = false),
        )
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            wishTotalLine(rows.map { it.asCollectionRow() }, { id -> rows.first { it.item.id == id }.item.orderedAt != null }, PriceType.TREND),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        for (r in rows) {
                            val label = wishLabel(r.item, if (r.item.id == 2L) WishlistOwned(1, 0) else null, now = oct15)
                            CollectionRowView(r.asCollectionRow(), PriceType.TREND, label, r.item.orderedAt != null, {}) {}
                        }
                        // 1.36: the box in the compact list and on grid tiles too.
                        for (r in rows.take(2)) CompactRow(r.asCollectionRow(), PriceType.TREND, null, r.item.orderedAt != null, {}) {}
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (r in rows.take(2)) Box(Modifier.width(110.dp)) { CardTile(r.asCollectionRow(), PriceType.TREND, null, r.item.orderedAt != null, {}) {} }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/wishlist_ordered.png", roborazziOptions = screenshotOptions)
    }

    /** 1.37: wishlist price alerts on the list (all three views) and in the card's window. */
    @Test fun wishlistAlerts() {
        val filter = com.mtgtrader.data.CtFilter("en", com.mtgtrader.data.CtCondition.SLIGHTLY_PLAYED, zeroOnly = true)
        fun wish(id: Long, name: String, set: String, number: String, eur: Double, level: Double?, anyPrinting: Boolean = true) =
            WishlistRow(WishlistItem(id = id, card = ref(name, set, number, "rare").copy(fallbackEur = eur), anyPrinting = anyPrinting, alertBelow = level), null)
        val rows = listOf(
            wish(1, "Ragavan, Nimble Pilferer", "mh2", "138", 52.0, 40.0),
            wish(2, "Smothering Tithe", "rna", "22", 17.5, null),
            wish(3, "Arcane Signet", "cmm", "367", 0.6, 0.5, anyPrinting = false),
        )
        val prices = mapOf(
            1L to com.mtgtrader.data.WishPrice(1, "cardmarket:low", 41.5, setCode = "mh2", ctPrice = 38.5, ctSetCode = "2x2", ctCondition = "Slightly Played", ctLanguage = "en", ctAnyPrice = 36.9, ctFilter = filter.key(false), checkedAt = 1_792_065_600_000L),
            2L to com.mtgtrader.data.WishPrice(2, "cardmarket:low", 14.2, setCode = "rna", ctZeroOver = 18.0, ctAnyPrice = 13.9, ctFilter = filter.key(false)),
            3L to com.mtgtrader.data.WishPrice(3, "cardmarket:low", 0.55, ctPrice = 0.65, ctFilter = filter.key(false)),
        )
        fun note(r: WishlistRow) = com.mtgtrader.ui.wishPriceLine(r.item, prices[r.item.id], "cardmarket:low", filter)
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (r in rows) CollectionRowView(r.asCollectionRow(), PriceType.TREND, wishLabel(r.item, null), false, {}, note(r)) {}
                        for (r in rows.take(2)) CompactRow(r.asCollectionRow(), PriceType.TREND, null, false, {}, note(r)) {}
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (r in rows.take(2)) Box(Modifier.width(110.dp)) { CardTile(r.asCollectionRow(), PriceType.TREND, null, false, {}, note(r)) {} }
                        }
                        androidx.compose.material3.HorizontalDivider()
                        // The card window's "Price alert" part.
                        com.mtgtrader.ui.PriceAlertFields(rows[0].item, com.mtgtrader.ui.MoneyInput("40.00", AppCurrency.EUR), com.mtgtrader.ui.WishAlertInfo(prices[1L], "cardmarket:low", filter)) {}
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/wishlist_alerts.png", roborazziOptions = screenshotOptions)
    }

    /** 1.37: Settings → Wishlist price alerts with CardTrader on, and the price menu's explanations. */
    @Test fun wishlistAlertSettings() {
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        com.mtgtrader.ui.WishlistAlertsSection(
                            com.mtgtrader.ui.AlertSettings(cardTraderOn = true, ctStatus = com.mtgtrader.data.CardTrader.Status(hasToken = true, okAt = 1_792_065_600_000L)),
                            com.mtgtrader.ui.AlertSettingsActions(),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/wishlist_alert_settings.png", roborazziOptions = screenshotOptions)
    }

    /** 1.37: the alert price menu opened: each price with what it means for an alert (drawn as the menu shows it). */
    @Test fun wishlistAlertPriceMenu() {
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        androidx.compose.material3.Card(Modifier.width(340.dp)) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                com.mtgtrader.ui.alertTypeHelp.forEach { (t, help) ->
                                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.RadioButton(selected = t == PriceType.LOW, onClick = null)
                                        Spacer(Modifier.width(12.dp))
                                        com.mtgtrader.ui.ExplainedOption(t.label, help, t == PriceType.LOW)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/wishlist_alert_price_menu.png", roborazziOptions = screenshotOptions)
    }

    /** 1.37: the same section waiting for a token, with notifications and automatic updates off, on TCGplayer prices. */
    @Test fun wishlistAlertSettingsToken() {
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        com.mtgtrader.ui.WishlistAlertsSection(
                            com.mtgtrader.ui.AlertSettings(
                                source = com.mtgtrader.data.PriceSource.TCGPLAYER, autoUpdate = false, notificationsAllowed = false, cardTraderOn = true,
                                ctLanguage = "", ctCondition = com.mtgtrader.data.CtCondition.NEAR_MINT, ctZeroOnly = false,
                            ),
                            com.mtgtrader.ui.AlertSettingsActions(),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/wishlist_alert_settings_token.png", roborazziOptions = screenshotOptions)
    }

    /** 1.38: the wishlist in sections (To buy, Ordered, Received), with what's behind a card being swiped. */
    @Test fun wishlistSections() {
        val oct15 = 1_792_065_600_000L
        fun wish(id: Long, name: String, set: String, number: String, eur: Double, orderedAt: Long? = null, receivedAt: Long? = null) =
            WishlistItem(id = id, card = ref(name, set, number, "rare").copy(fallbackEur = eur), orderedAt = orderedAt, receivedAt = receivedAt)
        val items = listOf(
            wish(1, "Smothering Tithe", "rna", "22", 17.5),
            wish(2, "Rhystic Study", "jmp", "169", 38.0, orderedAt = oct15 - 86_400_000L),
            wish(3, "Arcane Signet", "cmm", "367", 0.6, orderedAt = oct15 - 3 * 86_400_000L, receivedAt = oct15),
        ).associateBy { it.id }
        val rows = items.values.map { WishlistRow(it, null).asCollectionRow() }
        val sections = com.mtgtrader.ui.wishSections(rows, { items[it] }, sections = true)
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (sec in sections) {
                            sec.title?.let { com.mtgtrader.ui.SectionHeader(it, sec.rows.sumOf { r -> r.item.quantity }, folded = false) {} }
                            for (r in sec.rows) {
                                val w = items.getValue(r.item.id)
                                CollectionRowView(r, PriceType.TREND, wishLabel(w, null, now = oct15), if (w.receivedAt != null) null else w.orderedAt != null, {}) {}
                            }
                        }
                        Text("Swiping a card:", style = MaterialTheme.typography.labelSmall)
                        val (right, left) = com.mtgtrader.ui.wishSwipes(items.getValue(2), onRight = {}, onLeft = {})
                        com.mtgtrader.ui.SwipeBackground(right, toRight = true, Modifier.fillMaxWidth().height(56.dp))
                        com.mtgtrader.ui.SwipeBackground(left, toRight = false, Modifier.fillMaxWidth().height(56.dp))
                        com.mtgtrader.ui.SectionHeader("Received", 4, folded = true) {}
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/wishlist_sections.png", roborazziOptions = screenshotOptions)
    }

    /** 1.38: selecting cards in the collection: the selection bar, and picked cards in the three views. */
    @Test fun collectionSelection() {
        fun row(id: Long, name: String, set: String, number: String, qty: Int, eur: Double) =
            CollectionRow(CollectionItem(id = id, card = ref(name, set, number, "rare").copy(fallbackEur = eur), foil = false, quantity = qty), null)
        val rows = listOf(row(1, "Sol Ring", "c21", "263", 3, 1.2), row(2, "Brainstorm", "ice", "61", 1, 0.8), row(3, "Counterspell", "mh2", "267", 2, 1.5))
        val picked = setOf(1L, 3L)
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.mtgtrader.ui.SelectionTopBar(5, 6.6, onClose = {}) {
                            androidx.compose.material3.IconButton(onClick = {}) { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Move") }
                            androidx.compose.material3.IconButton(onClick = {}) { androidx.compose.material3.Icon(Icons.Default.SwapHoriz, "Trade") }
                            androidx.compose.material3.IconButton(onClick = {}) { androidx.compose.material3.Icon(Icons.Default.Delete, "Delete") }
                            androidx.compose.material3.IconButton(onClick = {}) { androidx.compose.material3.Icon(Icons.Default.MoreVert, "More") }
                        }
                        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (r in rows) CollectionRowView(r, PriceType.TREND, null, picked = r.item.id in picked, onLongClick = {}) {}
                            for (r in rows) CompactRow(r, PriceType.TREND, null, picked = r.item.id in picked, onLongClick = {}) {}
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (r in rows) Box(Modifier.width(110.dp)) { CardTile(r, PriceType.TREND, null, picked = r.item.id in picked, onLongClick = {}) {} }
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/collection_selection.png", roborazziOptions = screenshotOptions)
    }

    /** 1.38: Settings for received wishlist cards and for swiping, and what's behind each swipe action. */
    @Test fun receivedAndSwipeSettings() {
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        com.mtgtrader.ui.ReceivedCardsSection(com.mtgtrader.data.ReceivedClear.NEVER, false, {}, {})
                        Spacer(Modifier.height(16.dp))
                        com.mtgtrader.ui.SwipeSection(com.mtgtrader.data.SwipeAction.MOVE_TO_BINDER, com.mtgtrader.data.SwipeAction.ADD_TO_TRADE) { _, _ -> }
                        Spacer(Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (a in com.mtgtrader.data.SwipeAction.entries.filter { it != com.mtgtrader.data.SwipeAction.NOTHING }) {
                                val (icon, color) = com.mtgtrader.ui.swipeLook(a)
                                com.mtgtrader.ui.SwipeBackground(com.mtgtrader.ui.SwipeSpec(a.label.removeSuffix("…"), icon, color) {}, toRight = a.ordinal % 2 == 0, Modifier.fillMaxWidth().height(44.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/received_swipe_settings.png", roborazziOptions = screenshotOptions)
    }

    /** 1.39: after an order import, the order's cards that are on the wishlist (a window, so the whole screen is captured). */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun orderWishlistCheck() {
        fun wish(id: Long, name: String, qty: Int, ordered: Boolean = false, any: Boolean = true) =
            WishlistItem(id = id, card = ref(name, "m21", "1", "rare"), quantity = qty, orderedAt = if (ordered) 1L else null, anyPrinting = any)
        val matches = listOf(
            com.mtgtrader.data.WishOrders.Match(wish(1, "Rhystic Study", 1, ordered = true), 1),
            com.mtgtrader.data.WishOrders.Match(wish(2, "Sol Ring", 3, ordered = true), 2),
            com.mtgtrader.data.WishOrders.Match(wish(3, "Smothering Tithe", 1, any = false), 1),
        )
        compose.setContent { MtgColors(dark = false) { com.mtgtrader.ui.OrderWishlistDialog(matches, "CardTrader order #12345") {} } }
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/screenshots/order_wishlist_check.png", roborazziOptions = screenshotOptions)
    }

    /** 1.39: "Arrange binders…", with the wishlist moved first. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun arrangeBinders() {
        val labels = mapOf("wishlist" to "★ Wishlist", "unsorted" to "Unsorted", "b:1" to "⇄ Trade binder", "b:2" to "Commander decks", "b:3" to "New cards")
        compose.setContent { MtgColors(dark = false) { com.mtgtrader.ui.ArrangeBindersDialog(labels.keys.toList(), { labels.getValue(it) }, {}) {} } }
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/screenshots/arrange_binders.png", roborazziOptions = screenshotOptions)
    }

    /** 1.40: price fields with their currency button; one typed in euros while the app shows kroner says what it comes to. */
    @Test fun moneyFields() = inCurrency(DisplayCurrency(AppCurrency.DKK, 7.4612)) {
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.mtgtrader.ui.MoneyField(com.mtgtrader.ui.MoneyInput("10", AppCurrency.EUR), "Purchase price per copy (optional)", Modifier.fillMaxWidth())
                        com.mtgtrader.ui.MoneyField(com.mtgtrader.ui.MoneyInput("74.61", AppCurrency.DKK), "Purchase price per copy (optional)", Modifier.fillMaxWidth())
                        com.mtgtrader.ui.MoneyField(com.mtgtrader.ui.MoneyInput("", AppCurrency.DKK), "Agreed price per copy (optional)", Modifier.fillMaxWidth(), placeholder = "Uses Cardmarket price if empty")
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/money_fields.png", roborazziOptions = screenshotOptions)
    }

    /** 1.42/1.43: a deck in the one-line compact view with mana costs, a folded section, and the maybeboard under the deck. */
    @Test fun deckCompactAndMaybeboard() {
        fun row(id: Long, name: String, set: String, number: String, eur: Double, qty: Int = 1, category: String = "Ramp", foil: Boolean = false, added: Boolean = false, maybe: Boolean = false) =
            com.mtgtrader.data.DeckCardRow(
                com.mtgtrader.data.DeckCard(
                    id = id, deckId = 7, card = ref(name, set, number, "rare").copy(fallbackEur = eur, fallbackEurFoil = eur * 2), quantity = qty, category = category,
                    types = "Artifact", cmc = 1.0, commander = false, foil = foil, addedInApp = added, maybeboard = maybe,
                ),
                null,
            )
        val gc = com.mtgtrader.data.BracketTag.GAME_CHANGER
        val combo = com.mtgtrader.data.BracketTag.COMBO
        val tutor = com.mtgtrader.data.BracketTag.TUTOR
        val fast = com.mtgtrader.data.BracketTag.FAST_MANA
        val turn = com.mtgtrader.data.BracketTag.EXTRA_TURN
        val danse = listOf(
            Triple(row(1, "Reanimate", "ema", "105", 4.1), "{B}", setOf(tutor)),
            Triple(row(2, "Grave Researcher // Reanimate", "dsk", "99", 0.2), "{2}{B}", emptySet()),
            Triple(row(3, "Saw in Half", "2x2", "93", 0.9, foil = true), "{2}{B}", emptySet()),
            Triple(row(4, "Demonic Tutor", "uma", "93", 38.0), "{1}{B}", setOf(gc, tutor)),
            Triple(row(5, "Dismember", "nph", "57", 1.8), "{1}{B/P}{B/P}", emptySet()),
            Triple(row(6, "Kitchen Finks", "shm", "229", 0.4, added = true), "{1}{G/W}{G/W}", emptySet()),
            // Long names: the labels shorten (TURN, FAST) or become dots so the name stays whole.
            Triple(row(7, "Temporal Manipulation of the Multiverse", "ema", "77", 3.2), "{3}{U}{U}", setOf(turn)),
            Triple(row(8, "Chrome Mox of the Very Long Name", "mrd", "152", 24.0), "{0}", setOf(gc, fast)),
            Triple(row(9, "Thassa's Oracle and Something Even Longer", "thb", "73", 9.5, foil = true), "{U}{U}", setOf(gc, combo)),
            Triple(row(10, "Delver of Secrets // Insectile Aberration", "isd", "51", 0.3), "{U} // ", emptySet()),
        )
        val maybe = listOf(row(20, "The Ozolith", "c21", "68", 6.5, category = "Maybeboard", maybe = true), row(21, "Lightning Greaves", "c21", "247", 0.4, qty = 2, category = "Maybeboard", maybe = true))
        compose.setContent {
            MtgColors(dark = true) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        com.mtgtrader.ui.SectionHeading(com.mtgtrader.data.DeckSection("Danse!", danse.map { it.first }), 82.5, folded = false) {}
                        for ((r, cost, tags) in danse) com.mtgtrader.ui.DeckCompactRow(r, PriceType.TREND, tags, cost) {}
                        com.mtgtrader.ui.SectionHeading(com.mtgtrader.data.DeckSection("Lands", listOf(row(30, "Swamp", "c21", "283", 0.05, qty = 30))), 1.5, folded = true) {}
                        com.mtgtrader.ui.SectionHeading(com.mtgtrader.data.DeckSection("Maybeboard", maybe), 7.3, folded = false) {}
                        com.mtgtrader.ui.DeckCompactRow(maybe[0], PriceType.TREND, emptySet(), "{3}") {}
                        com.mtgtrader.ui.DeckCompactRow(maybe[1], PriceType.TREND, emptySet(), "{2}") {}
                        Spacer(Modifier.height(12.dp))
                        // The picture view: the mana cost where the set and number were.
                        com.mtgtrader.ui.DeckCardRowView(danse[3].first, PriceType.TREND, danse[3].third, danse[3].second) {}
                        com.mtgtrader.ui.DeckCardRowView(danse[5].first, PriceType.TREND, emptySet(), danse[5].second) {}
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/deck_compact.png", roborazziOptions = screenshotOptions)
    }
    /** 1.44: the deck stats of the owner's Auntie Plague: mana curve, colours in the costs, and lands as sources (colourless hidden). */
    @Test fun deckStats() {
        val cc = { c: Char, n: Int -> com.mtgtrader.data.ColourCount(c, n) }
        val line = { name: String, n: Int, finds: List<String> -> com.mtgtrader.data.SourceLine(name, n, finds) }
        val sources = listOf(
            com.mtgtrader.data.ColourSources('B', List(23) { line("Black source $it", 1, emptyList()) }),
            com.mtgtrader.data.ColourSources('R', listOf(line("Mountain", 4, emptyList()), line("Fabled Passage", 1, listOf("Mountain"))) + List(14) { line("Red source $it", 1, emptyList()) }),
            com.mtgtrader.data.ColourSources('G', List(23) { line("Green source $it", 1, emptyList()) }),
        )
        compose.setContent {
            MtgColors(dark = true) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        com.mtgtrader.ui.StatsHeading("Stats", folded = false) {}
                        com.mtgtrader.ui.ManaCurveCard(com.mtgtrader.data.ManaCurve(listOf(0, 3, 17, 14, 12, 9, 6, 1), 62, 3.4677))
                        com.mtgtrader.ui.ColoursCard(
                            costs = listOf(cc('B', 63), cc('R', 16), cc('G', 22)),
                            sources = sources,
                            landsOnly = true,
                            colourless = false,
                            landCount = 38,
                            detailsMissing = 0,
                            onLandsOnly = {},
                            onColourless = {},
                        )
                        com.mtgtrader.ui.StatsHeading("Tokens (6)", folded = true) {}
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/deck_stats.png", roborazziOptions = screenshotOptions)
    }
    /** 1.45: choosing a token's printing for a deck: the cards' own first, the chosen one with a gold border and a tick. */
    @Test fun tokenPrintings() {
        compose.setContent {
            MtgColors(dark = true) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { com.mtgtrader.ui.PrintingTile("ac4384b7", "Edge of Eternities Commander Tokens", "5", own = true, chosen = false) {} }
                        Box(Modifier.weight(1f)) { com.mtgtrader.ui.PrintingTile("d1892b78", "The Hobbit Tokens", "13", own = false, chosen = true) {} }
                        Box(Modifier.weight(1f)) { com.mtgtrader.ui.PrintingTile("0bb11342", "Secret Lair Drop", "2820", own = false, chosen = false) {} }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/token_printings.png", roborazziOptions = screenshotOptions)
    }

    /** 1.46: combo flags (red counts for the bracket, blue doesn't; 2 or 3+ cards), shortened for long names, and the card page's combo lines. */
    @Test fun comboFlags() {
        fun row(id: Long, name: String, eur: Double) = com.mtgtrader.data.DeckCardRow(
            com.mtgtrader.data.DeckCard(
                id = id, deckId = 7, card = ref(name, "shm", "$id", "rare").copy(fallbackEur = eur), quantity = 1, category = "Combo",
                types = "Creature", cmc = 2.0, commander = false, foil = false,
            ),
            null,
        )
        val t = com.mtgtrader.data.BracketTag.entries
        val c2 = com.mtgtrader.data.BracketTag.COMBO
        val o2 = com.mtgtrader.data.BracketTag.OTHER_COMBO
        val o3 = com.mtgtrader.data.BracketTag.OTHER_COMBO_3
        val gc = com.mtgtrader.data.BracketTag.GAME_CHANGER
        val rows = listOf(
            Triple(row(1, "Devoted Druid", 1.1), "{1}{G}", setOf(c2, o3)),
            Triple(row(2, "Mikaeus, the Unhallowed", 9.8), "{2}{B}{B}{B}", setOf(c2)),
            Triple(row(3, "Blowfly Infestation", 0.4), "{2}{B}", setOf(o2)),
            Triple(row(4, "Yawgmoth, Thran Physician", 8.9), "{2}{B}{B}", setOf(gc, o3)),
            Triple(row(5, "Hapatra, Vizier of Poisons and a Longer Name", 1.2), "{B}{G}", setOf(o2, o3)),
            Triple(row(6, "Everlasting Torment of the Very Long Name Indeed", 0.9), "{2}{B/R}", setOf(o2)),
        )
        val combos = listOf(
            com.mtgtrader.data.SaltCombo(listOf("Devoted Druid", "Mikaeus, the Unhallowed"), counts = true, results = listOf("infinite-etb", "infinite-lifeloss"), spellbookUri = "x"),
            com.mtgtrader.data.SaltCombo(listOf("Devoted Druid", "Obelisk Spider", "Yawgmoth, Thran Physician"), counts = false, results = listOf("infinite-lifeloss"), needsBoard = true, spellbookUri = "y"),
        )
        check(t.isNotEmpty())
        compose.setContent {
            MtgColors(dark = true) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        com.mtgtrader.ui.BracketSummary(rows.map { it.third }, combos + List(6) { combos[1] }, only = false) {}
                        com.mtgtrader.ui.SectionHeading(com.mtgtrader.data.DeckSection("Combo", rows.map { it.first }), 22.3, folded = false) {}
                        for ((r, cost, tags) in rows) com.mtgtrader.ui.DeckCompactRow(r, PriceType.TREND, tags, cost) {}
                        Spacer(Modifier.height(8.dp))
                        com.mtgtrader.ui.DeckCardRowView(rows[0].first, PriceType.TREND, rows[0].third, rows[0].second) {}
                        Spacer(Modifier.height(8.dp))
                        com.mtgtrader.ui.BracketTags(rows[0].third)
                        combos.forEach { com.mtgtrader.ui.ComboLine("Devoted Druid", it) {} }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/combo_flags.png", roborazziOptions = screenshotOptions)
    }

    /** 1.46: when Auntie Plague can cast its commander and has its colours (the table the owner picked). */
    @Test fun commanderOdds() {
        val odds = com.mtgtrader.data.ManaOdds(
            games = 10_000,
            commanders = listOf(com.mtgtrader.data.CastOdds("Auntie Ool, Cursewretch", "{1}{B}{R}{G}", 4, listOf(0, 0, 39, 79, 91, 95, 97))),
            colours = listOf('B' to listOf(67, 88, 96, 98, 99, 99, 99), 'R' to listOf(58, 83, 92, 95, 97, 98, 98), 'G' to listOf(74, 89, 94, 97, 98, 99, 99)),
            allColours = listOf(16, 66, 84, 90, 94, 97, 98),
            lands = 38, rocks = 2, creatures = 4, ramp = 3,
        )
        compose.setContent {
            MtgColors(dark = true) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 12.dp)) { com.mtgtrader.ui.CommanderOddsCard(odds, missing = 0) }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/commander_odds.png", roborazziOptions = screenshotOptions)
    }

    /** 1.46: the bracket and power level cards of all decks, small. */
    @Test fun allDecksCards() {
        fun deck(id: Long, name: String, cmd: String, colours: String, base: Int, real: Int, power: Double) =
            com.mtgtrader.data.Deck(id, name, "joelvdc", cmd, null, null, colours, 100, powerLevel = power, bracketBaseline = base, bracketRealistic = real, saltPercent = 31.0, saltId = "s")
        val card = com.mtgtrader.data.SaltCard(
            gameChangers = listOf("A", "B", "C"), twoCardCombos = listOf(listOf("X", "Y"), listOf("Z", "W")), playStyle = "Focused", manaFixing = 86,
        )
        compose.setContent {
            MtgColors(dark = true) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Since 1.52 with the turn each deck wins by ("…" while it's worked out); 4 in 10 games since 1.54.
                        com.mtgtrader.ui.SmallBracketCard(deck(1, "Auntie Plague", "Auntie Ool, Cursewretch", "BRG", 4, 3, 6.4), card, win(cloudTurns)) {}
                        com.mtgtrader.ui.SmallBracketCard(deck(2, "Baba", "Baba Lysaga, Night Witch", "BRG", 2, 3, 5.9), com.mtgtrader.data.SaltCard(), win(babaTurns)) {}
                        com.mtgtrader.ui.SmallBracketCard(deck(4, "Sarge", "Sergeant John Benton", "GW", 3, 3, 6.1), com.mtgtrader.data.SaltCard()) {}
                        com.mtgtrader.ui.SmallPowerCard(deck(1, "Auntie Plague", "Auntie Ool, Cursewretch", "BRG", 4, 3, 6.4), card, com.mtgtrader.data.PowerSource.COMMANDER_SALT) {}
                        com.mtgtrader.ui.SmallPowerCard(deck(3, "Cult of Athreos (LC)", "Athreos, God of Passage", "WB", 2, 2, 4.8), null, com.mtgtrader.data.PowerSource.COMMANDER_SALT) {}
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/all_decks_cards.png", roborazziOptions = screenshotOptions)
    }

    // 1.52: win turns from the owner's decks (Cloud, Baba and Sarge on 2026-10-20, 10,000 games each), and a made-up slow deck.
    private val cloudTurns = listOf(0, 0, 0, 0, 0, 0, 0, 1, 8, 36, 71, 90, 97, 99, 99)
    private val babaTurns = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 9, 19, 31, 45, 59)
    private val slowTurns = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 7, 15, 25, 35, 47)
    private val sargeTurns = listOf(0, 0, 0, 0, 0, 2, 23, 60, 86, 95, 98, 99, 99, 99, 99)
    // 1.54: Auntie Plague and Shrine Gate Win on 2026-10-21 (Shrines counted, Maze's End).
    private val auntieTurns = listOf(0, 0, 0, 0, 0, 2, 5, 9, 17, 38, 68, 87, 95, 98, 99)
    private val auntieCombo = listOf(0, 0, 0, 0, 0, 2, 5, 9, 13, 18, 23, 26, 27, 28, 28)
    private val shrineTurns = listOf(0, 0, 0, 0, 0, 0, 1, 6, 22, 45, 66, 82, 91, 95, 97)
    private val shrineMaze = listOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 7, 12, 16, 18, 20)
    private fun win(
        turns: List<Int>,
        damage: Map<String, Int> = mapOf("combat" to 52, "commander" to 48),
        knockouts: Int = 79,
        combo: List<Int> = List(15) { 0 },
        maze: List<Int> = emptyList(),
    ) = com.mtgtrader.data.WinOdds(
        10_000, turns, combo, damage, knockouts,
        com.mtgtrader.data.WinCounted(creatures = 19, tokenMakers = 4, triggers = 1, drains = 1, gear = 18, pumps = 1, mazeEnd = maze.isNotEmpty()),
        byMaze = maze,
    )

    /**
     * 1.52: the Stats' "Winning the game": a deck that wins (with a combo row), one the app can't read well, one being
     * worked out. Since 1.54 with the first win (green) and 4 in 10 games (gold) highlighted.
     */
    @Test fun winningTheGame() {
        compose.setContent {
            MtgColors(dark = true) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        com.mtgtrader.ui.WinningCard(win(auntieTurns, mapOf("combat" to 77, "commander" to 23), 20, combo = auntieCombo), missing = 0)
                        com.mtgtrader.ui.WinningCard(win(slowTurns, mapOf("combat" to 72, "commander" to 28), 21), missing = 2)
                        com.mtgtrader.ui.WinningCard(null, missing = 0)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/winning_the_game.png", roborazziOptions = screenshotOptions)
    }

    /** 1.54: a Maze's End deck in the light theme (the green and gold highlights on a light card). */
    @Test fun winningWithMazesEnd() {
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        com.mtgtrader.ui.WinningCard(win(shrineTurns, mapOf("combat" to 67, "drain" to 19, "commander" to 13), 12, maze = shrineMaze), missing = 0)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/winning_mazes_end.png", roborazziOptions = screenshotOptions)
    }

    /** 1.52: the bracket card's Speed section (Sarge: commander damage with pump spells); 4 in 10 games and the first win since 1.54. */
    @Test @Config(qualifiers = "w411dp-h1200dp-xhdpi") fun bracketCardSpeed() {
        val deck = com.mtgtrader.data.Deck(5, "Sarge", "joelvdc", "Sergeant John Benton", null, null, "GW", 100, powerLevel = 6.1, bracketBaseline = 3, bracketRealistic = 3, saltPercent = 22.0, saltId = "s")
        val card = com.mtgtrader.data.SaltCard(gameChangers = listOf("Smothering Tithe"), axes = mapOf("speed" to 0.55, "consistency" to 0.6))
        compose.setContent {
            MtgColors(dark = true) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        com.mtgtrader.ui.BracketCardView(deck, card, win = win(sargeTurns, mapOf("combat" to 17, "commander" to 83), 100))
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/bracket_card_speed.png", roborazziOptions = screenshotOptions)
    }

    /** 1.46: editing a deck: waiting edits, drag handles, selected cards, the card page's controls, the Decks tab's sections. */
    @Test fun deckEditing() {
        fun row(id: Long, name: String, cost: Double, category: String = "Ramp", added: Boolean = false) = com.mtgtrader.data.DeckCardRow(
            com.mtgtrader.data.DeckCard(
                id = id, deckId = 7, card = ref(name, "cmr", "$id").copy(fallbackEur = cost), quantity = 1, category = category,
                types = "Artifact", cmc = 1.0, commander = false, foil = false, addedInApp = added, lineUid = "u$id",
            ),
            null,
        )
        val handle: @androidx.compose.runtime.Composable () -> Unit = {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.DragIndicator, null, Modifier.padding(start = 4.dp))
        }
        compose.setContent {
            MtgColors(dark = true) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        com.mtgtrader.ui.DeckRow(
                            com.mtgtrader.data.Deck(-5, "Kenrith brew", "", "Kenrith, the Returned King", null, null, "WUBRG", 100, origin = "Pasted decklist"),
                            com.mtgtrader.data.PowerSource.COMMANDER_SALT, showModified = false, waiting = 0,
                        ) {}
                        Spacer(Modifier.height(6.dp))
                        com.mtgtrader.ui.DeckRow(
                            com.mtgtrader.data.Deck(5, "Auntie Plague", "joelvdc", "Auntie Ool, Cursewretch", null, null, "BRG", 100, powerLevel = 6.4, bracketRealistic = 3, bracketBaseline = 4),
                            com.mtgtrader.data.PowerSource.COMMANDER_SALT, showModified = false, waiting = 2,
                        ) {}
                        com.mtgtrader.ui.WaitingBanner(2, offline = true, loggedIn = true, sending = false) {}
                        com.mtgtrader.ui.SectionHeading(com.mtgtrader.data.DeckSection("Ramp", emptyList()), 4.3, folded = false, count = 3, highlight = true, menu = {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.MoreVert, null)
                        }) {}
                        com.mtgtrader.ui.DeckCompactRow(row(1, "Sol Ring", 1.9), PriceType.TREND, emptySet(), "{1}", com.mtgtrader.ui.RowEditing(handle = handle)) {}
                        com.mtgtrader.ui.DeckCompactRow(row(2, "Cultivate", 0.3), PriceType.TREND, emptySet(), "{2}{G}", com.mtgtrader.ui.RowEditing(selected = true)) {}
                        com.mtgtrader.ui.DeckCompactRow(row(3, "Three Visits", 2.4), PriceType.TREND, emptySet(), "{1}{G}", com.mtgtrader.ui.RowEditing(selected = false)) {}
                        Spacer(Modifier.height(12.dp))
                        com.mtgtrader.ui.CardEditControls(
                            category = "Combo", autoCategory = "Ramp",
                            maybeboard = false, quantity = 1, onPickCategory = {}, onBoard = {}, onQuantity = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/deck_editing.png", roborazziOptions = screenshotOptions)
    }

    /**
     * 1.47, as Lifetap since 1.48: four players, a turn running, the monarch, partners, "Dead?" and a player out, the
     * middle bar; since 1.49 the mini-grid centred in its frame, and damage from the player's own commander.
     */
    @Test fun lifeTracker() = tracker("life_tracker") { g -> trackerTable(g) }

    /** 1.48: three players, and six with one at each end. */
    @Test fun lifeTrackerThree() = tracker("life_tracker_3", sampleGame(3)) { g -> trackerTable(g) }

    @Test fun lifeTrackerSixEnds() = tracker("life_tracker_6_ends", sampleGame(6).copy(ends = true)) { g -> trackerTable(g) }

    /**
     * 1.51: a narrower phone (as the owner's Samsung, 384 dp wide) keeps the name, total and grid centred on the
     * long sides; five players; the end seats' grid right beside the total (in the six-player picture above).
     */
    @Test @Config(qualifiers = "w384dp-h832dp-xhdpi") fun lifeTrackerNarrowPhone() = tracker("life_tracker_narrow", width = 384, height = 832) { g -> trackerTable(g) }

    @Test fun lifeTrackerFive() = tracker("life_tracker_5", sampleGame(5)) { g -> trackerTable(g) }

    /** 1.51: the Games tab's paused games, each with its date, players, round and time, Continue and ⋮ (Drop it). */
    @Test fun pausedGames() {
        fun paused(at: Long, players: Int, round: Int) = com.mtgtrader.data.PausedGame(
            pausedAt = at, uid = "p$at",
            game = com.mtgtrader.data.LiveGames.encode(sampleGame(players).let { g -> com.mtgtrader.data.LiveGames.pause(g.copy(round = round), g.startedAt + 2_100_000) }),
        )
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Paused games", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                        androidx.compose.material3.Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            listOf(paused(1_792_430_040_000, 4, 7), paused(1_792_368_000_000, 3, 4)).forEachIndexed { i, p ->
                                if (i > 0) androidx.compose.material3.HorizontalDivider()
                                com.mtgtrader.ui.PausedGameRow(p, onContinue = {}, onDrop = {})
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/paused_games.png", roborazziOptions = screenshotOptions)
    }

    /**
     * 1.50: low life (a quarter of the starting life or less) with the ghost, part-way up: between the total and the
     * +, and on the − side where the mini-grid sits beside the total (the end seats).
     */
    @Test fun trackerLowLife() = tracker("tracker_low_life", lowLifeGame(4), advanceMs = 1_600) { g -> lowLifeTable(g) }

    @Test fun trackerLowLifeSixEnds() = tracker("tracker_low_life_6_ends", lowLifeGame(6).copy(ends = true), advanceMs = 1_600) { g -> lowLifeTable(g) }

    private fun lowLifeGame(players: Int): com.mtgtrader.data.LiveGame {
        val lives = listOf(10, 4, 23, 7, 3, 9)
        val g = sampleGame(players)
        return g.copy(seats = g.seats.map { it.copy(life = lives[it.seat], out = false, outRound = null) })
    }

    @androidx.compose.runtime.Composable
    private fun lowLifeTable(g: com.mtgtrader.data.LiveGame) = com.mtgtrader.ui.TrackerTable(
        g, com.mtgtrader.ui.PanelLook(), now = 2_102_000, bar = { com.mtgtrader.ui.MenuBar(g, 2_102_000) },
    )

    /** 1.48: a player's sheet: the commander damage grid and the counter tiles; the Player tab. */
    @Test fun trackerCounters() = tracker("tracker_counters") { g ->
        trackerTable(g)
        com.mtgtrader.ui.PlayerSheet(g, 0, com.mtgtrader.ui.PanelLook(), startOnCounters = true, onClose = {})
    }

    @Test fun trackerPlayer() = tracker("tracker_player") { g ->
        trackerTable(g)
        com.mtgtrader.ui.PlayerSheet(g, 0, com.mtgtrader.ui.PanelLook(), startOnCounters = false, onClose = {})
    }

    /** 1.48: holding an opponent's box: their commander damage big, partners split in two. */
    @Test fun trackerDamageDetail() = tracker("tracker_damage_detail") { g ->
        trackerTable(g)
        com.mtgtrader.ui.PlayerSheet(g, 0, com.mtgtrader.ui.PanelLook(), startOnCounters = true, onClose = {}, startFocus = 2)
    }

    /** 1.48: the game's settings, from the middle bar. */
    @Test @Config(qualifiers = "w411dp-h2100dp-xhdpi") fun trackerSettings() = tracker("tracker_settings", height = 2100) { g ->
        com.mtgtrader.ui.GameSettingsPage(g, onClose = {})
    }

    /** 1.58: the "1st" badge on the player who starts, until something happens (turns off). */
    @Test fun trackerFirstBadge() {
        val g0 = sampleGame()
        val fresh = g0.copy(
            turnsOn = false, firstSeat = 1, monarch = null, initiative = null,
            seats = g0.seats.map { it.copy(life = g0.startingLife, cmd = emptyMap(), poison = 0, casts = 0, partnerCasts = 0, counters = emptyMap(), out = false) },
        )
        tracker("tracker_first_badge", fresh) { g ->
            com.mtgtrader.ui.TrackerTable(
                g, com.mtgtrader.ui.PanelLook(lowLife = com.mtgtrader.data.LowLifeStyle.OFF), now = 1_000,
                first = com.mtgtrader.ui.firstBadgeSeat(g), bar = { com.mtgtrader.ui.MenuBar(g, 1_000) },
            )
        }
    }

    /** 1.58: Settings → Life tracker settings, without a game. */
    @Test @Config(qualifiers = "w411dp-h1500dp-xhdpi") fun trackerSettingsOutsideAGame() = tracker("tracker_settings_outside", height = 1500) {
        com.mtgtrader.ui.TrackerSettingsScreen(androidx.navigation.compose.rememberNavController())
    }

    @Test fun trackerDice() = tracker("tracker_dice") { g -> com.mtgtrader.ui.DicePage(g, {}, {}, {}, {}) }

    @Test fun trackerMana() = tracker("tracker_mana") { g -> com.mtgtrader.ui.ManaPage(g) {} }

    @Test fun trackerHistory() = tracker("tracker_history") { g ->
        com.mtgtrader.ui.HistoryPage(g, now = 2_102_000, canUndo = true, nav = androidx.navigation.compose.rememberNavController()) {}
    }

    @Test fun trackerEndGame() = tracker("tracker_end_game") { g ->
        trackerTable(g)
        com.mtgtrader.ui.EndGameDialog(g, onCancel = {}, onLeave = {}, nav = androidx.navigation.compose.rememberNavController(), onDone = {})
    }

    @Test fun trackerRoll() = tracker("tracker_roll") { g ->
        trackerTable(g)
        com.mtgtrader.ui.RollOverlay(com.mtgtrader.ui.Roll("d20", "17", 20), animate = false, onAgain = {}) {}
    }

    /** 1.59: a d6 rolled on the Dice page, which stays behind it (dots, as Lifetap's). */
    @Test fun trackerRollOnDicePage() = tracker("tracker_roll_d6_page") { g ->
        com.mtgtrader.ui.DicePage(g, {}, {}, {}, {})
        com.mtgtrader.ui.RollOverlay(com.mtgtrader.ui.Roll("d6", "2", 6), animate = false, onAgain = {}) {}
    }

    /** 1.59: the coin, over the table. */
    @Test fun trackerRollCoin() = tracker("tracker_roll_coin") { g ->
        trackerTable(g)
        com.mtgtrader.ui.RollOverlay(com.mtgtrader.ui.Roll("Coin", "Tails", 2), animate = false, onAgain = {}) {}
    }

    /** 1.59: the ring of dice behind the held die, the finger on the d8. */
    @Test fun trackerDieRing() {
        val ring = com.mtgtrader.ui.DieRing()
        val g = sampleGame()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MtgColors(dark = true) {
                Box(Modifier.width(411.dp).height(900.dp)) {
                    com.mtgtrader.ui.TrackerTable(
                        g, com.mtgtrader.ui.PanelLook(lowLife = com.mtgtrader.data.LowLifeStyle.OFF), now = 2_102_000,
                        bar = { com.mtgtrader.ui.MenuBar(g, 2_102_000, ring = ring) },
                    )
                    com.mtgtrader.ui.DieRingOverlay(ring)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val die = compose.onNode(androidx.compose.ui.test.hasContentDescription("Dice", substring = true)).fetchSemanticsNode().boundsInRoot.center
        compose.runOnIdle { ring.centre = die }
        compose.mainClock.advanceTimeByFrame()
        // The d8: the fourth choice, 67.5° round from straight above, on the left of the die.
        val r = with(compose.density) { com.mtgtrader.ui.RING_RADIUS.toPx() }
        val a = Math.toRadians(67.5)
        compose.runOnIdle { ring.finger = die + androidx.compose.ui.geometry.Offset(-r * kotlin.math.sin(a).toFloat(), -r * kotlin.math.cos(a).toFloat()) }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/screenshots/tracker_die_ring.png", roborazziOptions = screenshotOptions)
    }

    /** 1.59: random opponent asks who you are; Anders is out. */
    @Test fun trackerWhoAmI() = tracker("tracker_who_am_i") { g ->
        com.mtgtrader.ui.TrackerTable(
            g, com.mtgtrader.ui.PanelLook(lowLife = com.mtgtrader.data.LowLifeStyle.OFF), now = 2_102_000,
            whoAmI = {}, bar = { com.mtgtrader.ui.MenuBar(g, 2_102_000) },
        )
    }

    /** A game in progress for the tracker's pictures: [players] at the table, things happening. */
    private fun sampleGame(players: Int = 4): com.mtgtrader.data.LiveGame {
        val names = listOf("Joel" to "Auntie Plague", "Mads" to "Atraxa", "Sofie" to "Kraum & Tymna", "Anders" to "Korvold", "Freja" to "Edgar Markov", "Kasper" to "Yuriko")
        fun seat(i: Int) = com.mtgtrader.data.LiveSeat(
            seat = i, name = names[i].first, deckName = names[i].second, me = i == 0, life = 40, colour = i,
            commander = if (i == 2) "Kraum, Ludevic's Opus" else names[i].second, partner = if (i == 2) "Tymna the Weaver" else null,
        )
        var g = com.mtgtrader.data.LiveGame(
            uid = "g", startedAt = 0, turnsOn = true, firstSeat = 0, active = 0, round = 7, turnStartMs = 2_000_000,
            seats = (0 until players).map(::seat), monarch = 0, counters = listOf(com.mtgtrader.data.LiveGames.CUSTOM + "Rings"),
        )
        fun step(t: Long, f: (com.mtgtrader.data.LiveGame) -> com.mtgtrader.data.LiveGame) { g = com.mtgtrader.data.LiveGames.settle(g, f(g), t) }
        step(60_000) { com.mtgtrader.data.LiveGames.casts(it, 0, 1) }
        step(400_000) { com.mtgtrader.data.LiveGames.commanderDamage(it, 0, "1", 6) }
        step(450_000) { com.mtgtrader.data.LiveGames.commanderDamage(it, 0, "0", 2) }
        if (players >= 3) step(700_000) { com.mtgtrader.data.LiveGames.commanderDamage(it, 0, com.mtgtrader.data.LiveGames.cmdKey(2, true), 4) }
        step(900_000) { com.mtgtrader.data.LiveGames.counter(com.mtgtrader.data.LiveGames.poison(it, 1, 3), 1, "energy", 4) }
        step(1_000_000) { com.mtgtrader.data.LiveGames.changeLife(it, 1, -13) }
        if (players == 4) {
            step(1_500_000) { com.mtgtrader.data.LiveGames.changeLife(it, 2, -40) }
            step(1_800_000) { com.mtgtrader.data.LiveGames.knockOut(com.mtgtrader.data.LiveGames.changeLife(it, 3, -40), 3, 1_800_000) }
        }
        step(2_101_000) { com.mtgtrader.data.LiveGames.changeLife(it, 0, 3) }
        return g
    }

    @androidx.compose.runtime.Composable
    private fun trackerTable(g: com.mtgtrader.data.LiveGame) = com.mtgtrader.ui.TrackerTable(
        g, com.mtgtrader.ui.PanelLook(lowLife = com.mtgtrader.data.LowLifeStyle.OFF), now = 2_102_000,
        delta = { if (it == 0) 3 else null },
        bar = { com.mtgtrader.ui.MenuBar(g, 2_102_000) },
    )

    private fun tracker(file: String, game: com.mtgtrader.data.LiveGame = sampleGame(), width: Int = 411, height: Int = 900, advanceMs: Long = 0, content: @androidx.compose.runtime.Composable (com.mtgtrader.data.LiveGame) -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MtgColors(dark = true) {
                Box(Modifier.width(width.dp).height(height.dp)) { content(game) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        if (advanceMs > 0) compose.mainClock.advanceTimeBy(advanceMs)
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png", roborazziOptions = screenshotOptions)
    }

    /** 1.47: the deck page's Games section. */
    @Test fun deckGames() {
        fun seat(place: Int, deck: Long?, name: String, rounds: Int?) = com.mtgtrader.data.GameSeat(0, "me", name, me = deck != null, deckId = deck, deckName = "Auntie Plague", place = place, bracket = 3, power = 6.4, started = place == 1)
        fun rec(at: Long, minutes: Long, rounds: Int?, place: Int, win: com.mtgtrader.data.WinType?) = com.mtgtrader.data.GameRecord(
            startedAt = at, endedAt = at, durationMs = minutes * 60_000, turnsOn = rounds != null, rounds = rounds, winType = win?.key,
            seats = com.mtgtrader.data.Games.encodeSeats(listOf(seat(place, 1, "Joel", rounds), com.mtgtrader.data.GameSeat(1, "mads", "Mads", deckName = "Atraxa", place = if (place == 1) 2 else 1, bracket = 4, power = 7.5))),
            uid = "u$at",
        )
        val games = listOf(
            rec(1_792_000_000_000, 74, 11, 1, com.mtgtrader.data.WinType.COMBO),
            rec(1_791_900_000_000, 52, null, 2, null),
            rec(1_791_000_000_000, 58, 8, 1, com.mtgtrader.data.WinType.COMBAT),
        )
        val r = com.mtgtrader.data.GameStats.decks(games).first { it.deckId == 1L }
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) { com.mtgtrader.ui.DeckGamesCard(r, games, 1L) {} }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/deck_games.png", roborazziOptions = screenshotOptions)
    }

    // 1.65: the cards as an A4 PDF to print and cut (Magic card size), the choices and the preview.
    private fun printDeck(id: Long, name: String, cmd: String, colours: String, base: Int, real: Int, power: Double, salt: Double) =
        com.mtgtrader.data.Deck(
            id, name, "joelvdc", cmd, null, null, colours, 100, powerLevel = power, bracketBaseline = base, bracketRealistic = real,
            saltPercent = salt, saltId = "s", scoredAt = 1_793_000_000_000L,
        )

    private val printCards = listOf(
        Triple(printDeck(1, "Auntie Plague", "Auntie Ool, Cursewretch", "BRG", 4, 3, 6.4, 31.0), com.mtgtrader.data.SaltCard(
            gameChangers = listOf("Rhystic Study", "Demonic Tutor", "Jeska's Will"), twoCardCombos = listOf(listOf("Exquisite Blood", "Sanguine Bond")),
            playStyle = "Focused", saltIntensity = "Mild", saltDriver = "Community Salt Ratings", manaFixing = 86, onCurve = 74,
            effects = mapOf("spotRemoval" to 9, "boardWipes" to 4, "tutors" to 5, "fastmana" to 3, "counters" to 0),
            wincons = listOf("Commander damage", "Drain", "Wide tokens", "Aristocrats"),
        ), auntieTurns),
        Triple(printDeck(2, "Baba", "Baba Lysaga, Night Witch", "BRG", 2, 3, 5.9, 18.0), com.mtgtrader.data.SaltCard(
            playStyle = "Casual", manaFixing = 78, onCurve = 70, effects = mapOf("spotRemoval" to 7, "boardWipes" to 2, "tutors" to 1),
            wincons = listOf("Sacrifice drain", "Combat"),
        ), babaTurns),
        Triple(printDeck(3, "Sarge", "Sergeant John Benton", "GW", 3, 3, 6.1, 22.0), com.mtgtrader.data.SaltCard(
            gameChangers = listOf("Smothering Tithe"), playStyle = "Focused", manaFixing = 91, onCurve = 80,
            effects = mapOf("spotRemoval" to 6, "boardWipes" to 3, "grouphug" to 1), wincons = listOf("Commander damage", "Equipment voltron"),
        ), sargeTurns),
        Triple(printDeck(4, "Elves", "Lathril, Blade of the Elves", "BG", 4, 4, 7.6, 34.0), com.mtgtrader.data.SaltCard(
            gameChangers = listOf("Survival of the Fittest", "Gaea's Cradle", "Seedborn Muse", "Vampiric Tutor"),
            twoCardCombos = listOf(listOf("Ashaya, Soul of the Wild", "Quirion Ranger"), listOf("Priest of Titania", "Umbral Mantle"), listOf("Devoted Druid", "Vizier of Remedies")),
            playStyle = "Optimized", manaFixing = 83, effects = mapOf("tutors" to 7, "fastmana" to 6, "spotRemoval" to 5, "boardWipes" to 1),
            wincons = listOf("Craterhoof", "Drain"),
        ), listOf(0, 0, 0, 3, 11, 27, 48, 69, 84, 92, 96, 98, 99, 99, 99)),
        Triple(printDeck(5, "Shrine Gate Win", "Go-Shintai of Life's Origin", "WUBRG", 3, 2, 5.2, 15.0), com.mtgtrader.data.SaltCard(
            gameChangers = listOf("Cyclonic Rift"), playStyle = "Casual", manaFixing = 95, effects = mapOf("spotRemoval" to 4, "boardWipes" to 3),
            wincons = listOf("Maze's End", "Shrine drain"),
        ), shrineTurns),
        Triple(printDeck(6, "Kenrith brew", "Kenrith, the Returned King", "WUBRG", 2, 2, 5.0, 9.0), null, slowTurns),
    )

    /** With [source] ScrollVault the power cards' footer takes two lines (since 1.67). */
    private fun printSheet(
        kind: com.mtgtrader.data.RuleZeroCard, inkSaver: Boolean, perMm: Float = 6f,
        source: com.mtgtrader.data.PowerSource = com.mtgtrader.data.PowerSource.COMMANDER_SALT,
    ): android.graphics.Bitmap {
        val cards = printCards.map { (deck, data, turns) ->
            com.mtgtrader.ui.PrintCard(kind, deck.copy(svPowerLevel = deck.powerLevel), data, win(turns), source)
        }
        val bitmap = android.graphics.Bitmap.createBitmap((210 * perMm).toInt(), (297 * perMm).toInt(), android.graphics.Bitmap.Config.ARGB_8888)
        com.mtgtrader.ui.CardSheets.drawPage(android.graphics.Canvas(bitmap), perMm, cards, com.mtgtrader.ui.CardSheets.footer(cards, 0, 2), inkSaver)
        return bitmap
    }

    @Test fun printSheetBracket() =
        printSheet(com.mtgtrader.data.RuleZeroCard.BRACKET, inkSaver = false).captureRoboImage("src/test/screenshots/print_bracket_sheet.png", roborazziOptions = screenshotOptions)

    @Test fun printSheetPowerInkSaver() =
        printSheet(com.mtgtrader.data.RuleZeroCard.POWER, inkSaver = true, source = com.mtgtrader.data.PowerSource.SCROLLVAULT).captureRoboImage("src/test/screenshots/print_power_sheet_ink_saver.png", roborazziOptions = screenshotOptions)

    @Test fun printChoices() {
        val decks = printCards.map { it.first }
        compose.setContent {
            MtgColors(dark = true) {
                com.mtgtrader.ui.PrintPicker(
                    decks = decks, source = com.mtgtrader.data.PowerSource.COMMANDER_SALT, choice = com.mtgtrader.ui.PrintChoice.BOTH, onChoice = {},
                    inkSaver = true, onInkSaver = {}, unticked = setOf(2L), onUnticked = {}, busy = false, error = null,
                    onBack = {}, onPreview = {}, onPrint = {}, onShare = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/print_choices.png", roborazziOptions = screenshotOptions)
    }

    @Test fun printPreview() {
        compose.setContent {
            MtgColors(dark = true) {
                com.mtgtrader.ui.PdfPreview(
                    pageCount = 2,
                    render = { _, width -> printSheet(com.mtgtrader.data.RuleZeroCard.BRACKET, inkSaver = true, perMm = width / 210f).asImageBitmap() },
                    onBack = {}, onPrint = {}, onShare = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/print_preview.png", roborazziOptions = screenshotOptions)
    }
}
