package com.mtgtrader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mtgtrader.data.AppCurrency
import com.mtgtrader.data.Binder
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
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

    /** 1.35: wishlist cards marked as ordered, and the line above the wishlist. */
    @Test fun wishlistOrdered() {
        val oct14 = 1_792_065_600_000L
        fun wish(id: Long, name: String, set: String, number: String, qty: Int, eur: Double, orderedAt: Long? = null, anyPrinting: Boolean = true) =
            WishlistRow(WishlistItem(id = id, card = ref(name, set, number, "rare").copy(fallbackEur = eur), quantity = qty, anyPrinting = anyPrinting, orderedAt = orderedAt), null)
        val rows = listOf(
            wish(1, "Rhystic Study", "jmp", "169", 1, 38.0, orderedAt = oct14),
            wish(2, "Smothering Tithe", "rna", "22", 1, 17.5),
            wish(3, "Arcane Signet", "cmm", "367", 3, 0.6, orderedAt = oct14, anyPrinting = false),
        )
        compose.setContent {
            MtgColors(dark = false) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            wishTotalLine(rows.map { it.asCollectionRow() }, { id -> rows.first { it.item.id == id }.item.orderedAt != null }, PriceType.TREND),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        for (r in rows) CollectionRowView(r.asCollectionRow(), PriceType.TREND, wishLabel(r.item, if (r.item.id == 2L) WishlistOwned(1, 0) else null)) {}
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/wishlist_ordered.png", roborazziOptions = screenshotOptions)
    }
}
