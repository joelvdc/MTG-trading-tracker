package com.mtgtrader

import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.Finish
import com.mtgtrader.data.OrderCandidate
import com.mtgtrader.data.OrderFiles
import com.mtgtrader.data.OrderFormat
import com.mtgtrader.data.OrderLine
import com.mtgtrader.data.OrderShop
import com.mtgtrader.data.PackageCheck
import com.mtgtrader.data.PackageCheck.Status
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.PurchasePrices
import com.mtgtrader.data.PurchasePrices.Change
import com.mtgtrader.data.ScannedCard
import com.mtgtrader.data.SortField
import com.mtgtrader.data.SortLevel
import com.mtgtrader.data.SortSpec
import com.mtgtrader.data.Spreadsheet
import com.mtgtrader.data.ValueHistory
import com.mtgtrader.ui.suggestedBinderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun card(name: String, set: String = "tst", number: String = "1", product: Int? = null, foilProduct: Int? = null) =
    CardRef("$name|$set|$number", name, set, "Set $set", number, "rare", null, product, null, null, true, true, cardmarketFoilId = foilProduct)

/** Sorting the collection by how a card's price moved, and the value screen's lists per card. */
class ValueChangeSortTest {
    private fun row(name: String, qty: Int, trend: Double?, avg30: Double?) = CollectionRow(
        CollectionItem(card = card(name, product = 1), foil = false, quantity = qty),
        if (trend == null) null else PriceEntity(1, trend, trend, trend, trend, trend, avg30, null, null, null, null, null, null),
    )

    private val rows = listOf(
        row("Doubled", 1, 2.0, 1.0),      // +1.00, +100%
        row("Big", 4, 60.0, 50.0),        // +10.00, +20%
        row("Fell", 1, 6.0, 8.0),         // -2.00, -25%
        row("Halved", 3, 0.5, 1.0),       // -0.50, -50%
        row("No average", 1, 5.0, null),
        row("No price", 1, null, null),
    )

    private fun sorted(field: SortField, reversed: Boolean) =
        rows.sortedWith(SortSpec(listOf(SortLevel(field, reversed))).comparator(PriceType.TREND)).map { it.item.card.name }

    @Test fun byPercent() {
        assertEquals(listOf("Doubled", "Big", "Fell", "Halved", "No average", "No price"), sorted(SortField.CHANGE_PCT, false))
        // The fallers first; cards without a change still go last.
        assertEquals(listOf("Halved", "Fell", "Big", "Doubled", "No average", "No price"), sorted(SortField.CHANGE_PCT, true))
    }

    @Test fun byMoneyPerCardNotPerStack() {
        // Big gained 10.00 a copy; its four copies don't count four times, nor do Halved's three.
        assertEquals(listOf("Big", "Doubled", "Halved", "Fell", "No average", "No price"), sorted(SortField.CHANGE_MONEY, false))
        assertEquals(listOf("Fell", "Halved", "Doubled", "Big", "No average", "No price"), sorted(SortField.CHANGE_MONEY, true))
    }

    @Test fun valueScreenListsAreperCardToo() {
        val up = ValueHistory.biggestMoves(rows, up = true)
        assertEquals(listOf("Big", "Doubled"), up.map { it.row.item.card.name })
        assertEquals(10.0, up.first().change, 1e-9)
        val down = ValueHistory.biggestMoves(rows, up = false)
        assertEquals(listOf("Fell", "Halved"), down.map { it.row.item.card.name })
        assertEquals(-2.0, down.first().change, 1e-9)
    }

    @Test fun anOlderVersionsSortStillReads() {
        val spec = SortSpec(listOf(SortLevel(SortField.CHANGE_MONEY, reversed = true), SortLevel(SortField.NAME)))
        assertEquals(spec, SortSpec.decode(spec.encode()))
        assertEquals("Change in money → Name", spec.label)
    }
}

/** Made-up files in the shops' formats: the owner's own orders stay out of the repository. */
class OrderFilesTest {
    private val shipment = """
        idProduct;groupCount;price;idLanguage;condition;isFoil;isSigned;isAltered;isPlayset;isReverseHolo;isFirstEd;isFullArt;isUberRare;isWithDie
        315288;1;6.98;1;3;;;;;;;;;
        788181;2;2.98;1;2;1;;;;;;;;
        378864;1;48;4;2;;;;1;;;;;
        264381;1;0,50;7;2;;1;1;;;;;;
        555;1;1.00;42;2;;;;;;;;;
    """.trimIndent()

    private val monthly = """
        Shipment nr.;Date of purchase;Article;Product ID;Localized Product Name;Expansion;Category;Amount;Article Value;Total;Currency;Comments
        1185249514;2024-12-02 16:38:27;Essence Flux;289154;Essence Flux;Shadows over Innistrad;Magic Single;1;2.38;2.38;EUR;289154
        1185249514;2024-12-02 16:38:27;Cavalier of Dawn;378651;暁の騎兵;Core 2020;Magic Single;3;2.55;7.65;EUR;
        1185249600;2024-12-05 09:00:00;80 KMC Hyper mat Sleeves (Black);4321;80 KMC Hyper mat Sleeves (Black);;Sleeves;3;5.99;17.97;EUR;
        1185249600;2024-12-05 09:00:00;Charizard GX;5555;Charizard GX;Hidden Fates;Pokémon Single;1;4;4;EUR;
    """.trimIndent()

    @Test fun readsSemicolonFiles() {
        val rows = Spreadsheet.read(shipment.toByteArray())
        assertEquals(14, rows[0].size)
        assertEquals("315288", rows[1][0])
        assertEquals("6.98", rows[1][2])
    }

    @Test fun cardmarketShipment() {
        val file = OrderFiles.read(shipment.toByteArray(), "ArticlesFromShipment1284353365.csv")
        assertEquals(OrderFormat.CARDMARKET_SHIPMENT, file.format)
        assertEquals(OrderShop.CARDMARKET, file.shop)
        assertEquals(5, file.lines.size)
        val (journey, marvin, cavalier, signed, odd) = file.lines
        assertEquals(315288, journey.productId)
        assertEquals("EX", journey.condition)
        assertEquals("EN", journey.language)
        assertEquals(6.98, journey.price!!, 1e-9)
        assertFalse(journey.foil)
        assertEquals("1284353365", journey.order)
        assertEquals("Cardmarket product 315288", journey.label)
        assertTrue(marvin.foil)
        assertEquals(2, marvin.quantity)
        assertEquals("NM", marvin.condition)
        // A playset is four cards for the article's price.
        assertTrue(cavalier.playset)
        assertEquals(4, cavalier.quantity)
        assertEquals(12.0, cavalier.price!!, 1e-9)
        assertEquals("ES", cavalier.language)
        assertTrue(signed.signed && signed.altered)
        assertEquals("JA", signed.language)
        assertEquals(0.5, signed.price!!, 1e-9)
        // A language number the app doesn't know isn't guessed.
        assertFalse(odd.languageKnown)
        assertTrue(journey.languageKnown && journey.versionKnown)
    }

    @Test fun cardmarketMonth() {
        val file = OrderFiles.read(monthly.toByteArray(), "Purchased Articles-byPurchaseDate-2024-12-01_2024-12-31.csv")
        assertEquals(OrderFormat.CARDMARKET_MONTHLY, file.format)
        assertEquals(2, file.lines.size)
        assertEquals(2, file.otherItems)
        val flux = file.lines[0]
        assertEquals("Essence Flux", flux.name)
        assertEquals("Shadows over Innistrad", flux.setName)
        assertEquals(289154, flux.productId)
        assertEquals("2024-12-02", flux.date)
        assertEquals("1185249514", flux.order)
        assertFalse(flux.versionKnown)
        assertEquals(3, file.lines[1].quantity)
        assertEquals(2.55, file.lines[1].price!!, 1e-9)
    }

    @Test fun quotesInAShopsFreeTextAreJustText() {
        // An inch mark in a seller's comment mustn't swallow the lines after it.
        val text = monthly.lines().toMutableList().also { it[1] = it[1] + "said 5\" tall" }.joinToString("\n")
        val file = OrderFiles.read(text.toByteArray())
        assertEquals(2, file.lines.size)
        assertEquals(2, file.otherItems)
        assertEquals(listOf("a \"b\" c", "d"), com.mtgtrader.data.Csv.parse("a \"b\" c;d", ';').single())
        assertEquals(listOf("a;b", "c"), com.mtgtrader.data.Csv.parse("\"a;b\";c", ';').single())
    }

    @Test fun cardTraderKeepsWorkingAndGetsItsDate() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("cardtrader/order_sample.xls")!!.readBytes()
        val file = OrderFiles.read(bytes, "cardtrader_order_20260623hxrahx.xls")
        assertEquals(OrderFormat.CARDTRADER, file.format)
        assertEquals(9, file.lines.size)
        assertTrue(file.lines.all { it.date == "2026-06-23" && it.order == "20260623hxrahx" && it.productId == null })
    }

    @Test(expected = OrderFiles.NotAnOrder::class) fun otherFilesAreRefused() {
        OrderFiles.read("Name,Set code,Quantity\nBrainstorm,ICE,1\n".toByteArray())
    }

    @Test fun binderNamesPerShop() {
        assertTrue(suggestedBinderName("cardtrader_order_20260623hxrahx.xls").startsWith("CardTrader 23 "))
        // Cardmarket's file name has a shipment number that could pass for a date: today's is used.
        assertTrue(suggestedBinderName("ArticlesFromShipment20260623.csv", OrderShop.CARDMARKET).startsWith("Cardmarket "))
    }

    @Test fun shopsSetCodes() {
        assertTrue(OrderFiles.sameSet("CRVR", "rvr"))
        assertTrue(OrderFiles.sameSet("ltrh", "ltr"))
        assertTrue(OrderFiles.sameSet("fdns", "fdn"))
        assertTrue(OrderFiles.sameSet("predgm", "pdgm"))
        assertTrue(OrderFiles.sameSet("ltcp", "pltc"))
        assertTrue(OrderFiles.sameSet("plist2", "plst"))
        assertFalse(OrderFiles.sameSet("iko", "m3c"))
        assertEquals("105", OrderFiles.plainNumber("APC-105"))
        assertEquals("546", OrderFiles.plainNumber("546e"))
        assertEquals(setOf("thing in the ice", "awoken horror", "thing in the ice awoken horror"), OrderFiles.names("Thing in the Ice / Awoken Horror"))
        assertEquals(setOf("sol ring"), OrderFiles.names("Sol Ring (V.2)"))
        assertTrue(OrderFiles.isBasicLandName("Plains (403 - Retro Frame)"))
        assertTrue(OrderFiles.isBasicLandName("Snow-Covered Island"))
        assertFalse(OrderFiles.isBasicLandName("Island Sanctuary"))
    }
}

class PurchasePricesTest {
    private var nextId = 1L

    private fun stack(card: CardRef, qty: Int = 1, foil: Boolean = false, lang: String = "EN", price: Double? = null, binder: Long = 0, cond: String = "NM") =
        CollectionItem(id = nextId++, card = card, foil = foil, quantity = qty, language = lang, purchasePrice = price, binderId = binder, condition = cond)

    private fun cm(product: Int, price: Double, qty: Int = 1, name: String = "", monthly: Boolean = true, foil: Boolean = false, lang: String = "EN") =
        OrderLine("", "", name, "", qty, price, "NM", lang, foil, false, false, productId = product, versionKnown = !monthly)

    private fun ct(name: String, set: String, number: String, price: Double, qty: Int = 1, foil: Boolean = false, lang: String = "EN", cond: String = "NM") =
        OrderLine(set, "Set $set", name, number, qty, price, cond, lang, foil, false, false)

    @Test fun averageWeightedByCopies() {
        val snap = stack(card("Snapcaster Mage", product = 10))
        val plan = PurchasePrices.plan(listOf(cm(10, 28.0), cm(10, 46.0), cm(10, 25.0, qty = 2)), listOf(snap), overwrite = false)
        val row = plan.rows.single()
        assertEquals(4, row.bought)
        assertEquals(31.0, row.newPrice, 1e-9)
        assertEquals(Change.NEW, row.change)
        assertTrue(plan.open.isEmpty() && plan.notOwned.isEmpty())
    }

    @Test fun moreCopiesThanBoughtAllGetThePrice() {
        val four = stack(card("Sign in Blood", product = 11), qty = 5)
        val row = PurchasePrices.plan(listOf(cm(11, 0.28)), listOf(four), overwrite = false).rows.single()
        assertEquals(0.28, row.newPrice, 1e-9)
        assertEquals(Change.NEW, row.change)
    }

    @Test fun existingPrices() {
        val covered = stack(card("Covered", product = 20), qty = 1, price = 3.0)
        val more = stack(card("More", product = 21), qty = 4, price = 2.0)
        val lines = listOf(cm(20, 5.0), cm(21, 4.0))
        val keep = PurchasePrices.plan(lines, listOf(covered, more), overwrite = false)
        // The orders cover the one copy: its price stays.
        assertEquals(Change.KEPT, keep.rows.single { it.item.id == covered.id }.change)
        assertEquals(3.0, keep.rows.single { it.item.id == covered.id }.newPrice, 1e-9)
        // 4 copies at 2.00, 1 of them now known to have cost 4.00: 2.50.
        val averaged = keep.rows.single { it.item.id == more.id }
        assertEquals(Change.AVERAGED, averaged.change)
        assertEquals(2.5, averaged.newPrice, 1e-9)
        assertEquals(listOf(more.id), keep.changing.map { it.item.id })

        val over = PurchasePrices.plan(lines, listOf(covered, more), overwrite = true)
        assertEquals(listOf(5.0, 4.0), over.rows.sortedBy { it.item.id }.map { it.newPrice })
        assertTrue(over.rows.all { it.change == Change.REPLACED })
        // The same price again is no change.
        assertEquals(Change.KEPT, PurchasePrices.plan(listOf(cm(20, 3.0)), listOf(covered), overwrite = true).rows.single().change)
    }

    @Test fun monthlyFileAndSeveralVersions() {
        val c = card("Godless Shrine", product = 30)
        val english = stack(c)
        val portuguese = stack(c, lang = "PT")
        val other = stack(card("Watery Grave", product = 31), binder = 1)
        val otherToo = stack(card("Watery Grave", product = 31), binder = 2)
        val lines = listOf(cm(30, 9.0, name = "Godless Shrine"), cm(31, 12.0, name = "Watery Grave"), cm(99, 45.0, qty = 3, name = "Force of Will"))
        val plan = PurchasePrices.plan(lines, listOf(english, portuguese, other, otherToo), overwrite = false)
        // The same version in two binders: both get it.
        assertEquals(setOf(other.id, otherToo.id), plan.rows.map { it.item.id }.toSet())
        // Two languages and the file doesn't say: the owner chooses.
        val open = plan.open.single()
        assertEquals(0, open.index)
        assertEquals(setOf(english.id, portuguese.id), open.candidates.map { it.id }.toSet())
        assertEquals("Force of Will", plan.notOwned.single().name)

        val chosen = PurchasePrices.plan(lines, listOf(english, portuguese, other, otherToo), overwrite = false, choices = mapOf(0 to setOf(portuguese.id)))
        assertEquals(9.0, chosen.rows.single { it.item.id == portuguese.id }.newPrice, 1e-9)
        assertTrue(chosen.rows.none { it.item.id == english.id })
        assertEquals(1, chosen.open.size)
    }

    @Test fun shipmentFileKnowsFinishAndLanguage() {
        val c = card("Jaheira", product = 40)
        val plain = stack(c)
        val foil = stack(c, foil = true)
        val italian = stack(card("Trinisphere", product = 41), lang = "IT")
        val foilOnly = stack(card("Scroll", product = 50, foilProduct = 51), foil = true)
        val lines = listOf(
            cm(40, 1.95, monthly = false), cm(40, 4.11, monthly = false, foil = true),
            cm(41, 6.95, monthly = false, lang = "EN"), cm(51, 3.0, monthly = false),
        )
        val plan = PurchasePrices.plan(lines, listOf(plain, foil, italian, foilOnly), overwrite = false)
        assertEquals(1.95, plan.rows.single { it.item.id == plain.id }.newPrice, 1e-9)
        assertEquals(4.11, plan.rows.single { it.item.id == foil.id }.newPrice, 1e-9)
        // A product Cardmarket only sells in foil is the foil stack, whatever the flag.
        assertEquals(3.0, plan.rows.single { it.item.id == foilOnly.id }.newPrice, 1e-9)
        // Bought as English, owned in Italian: asked, not assumed.
        assertEquals(italian.id, plan.open.single().candidates.single().id)
        assertTrue("Bought in EN" in plan.open.single().why)
    }

    @Test fun cardTraderBySetAndNumber() {
        val exact = stack(card("Echocasting Symposium", "sos", "44"))
        val retro = stack(card("Putrefy", "rvr", "378"))
        val listed = stack(card("Wargate", "plst", "ARB-129"))
        val token = stack(card("Dragon", "trvr", "7"))
        val wrongCard = stack(card("Esper Sentinel", "mh2", "12"))
        val foilOnly = stack(card("Maze's End", "pdgm", "152★"), foil = true)
        val german = stack(card("Thran Turbine", "usg", "311"), lang = "DE")
        val reprint = stack(card("Crop Rotation", "ddr", "7"))
        val twoConditions = listOf(stack(card("Mystic Monastery", "ktk", "236"), cond = "NM"), stack(card("Mystic Monastery", "ktk", "236"), cond = "EX", binder = 3))
        val lines = listOf(
            ct("Echocasting Symposium", "SOS", "044", 2.31),
            ct("Putrefy (Retro Frame)", "CRVR", "378", 0.5),
            ct("Wargate", "MB1", "129", 0.2),
            ct("Dragon (6/6)", "RVR", "T 07", 0.14),
            ct("Golem (4/4)", "MH2", "012", 0.1),
            ct("Maze's End", "PREDGM", "152", 1.0, foil = true),
            ct("Thran Turbine", "USG", "311", 1.74),
            ct("Crop Rotation", "MB1", "007", 0.82),
            ct("Mystic Monastery", "KTK", "236", 0.79, cond = "EX"),
            ct("Plains (403 - Retro Frame)", "CDMR", "403", 0.1),
        )
        val all = listOf(exact, retro, listed, token, wrongCard, foilOnly, german, reprint) + twoConditions
        val plan = PurchasePrices.plan(lines, all, overwrite = false)
        val priced = plan.rows.associate { it.item.card.name to it.newPrice }
        assertEquals(2.31, priced["Echocasting Symposium"]!!, 1e-9)
        assertEquals(0.5, priced["Putrefy"]!!, 1e-9)
        assertEquals(0.2, priced["Wargate"]!!, 1e-9)
        assertEquals(0.14, priced["Dragon"]!!, 1e-9)
        assertEquals(1.0, priced["Maze's End"]!!, 1e-9)
        // "MH2 012" is a token in the shop's numbering, not the card with that number.
        assertNull(priced["Esper Sentinel"])
        // The condition breaks the tie between two stacks of the printing.
        assertEquals(listOf(twoConditions[1].id), plan.rows.filter { it.item.card.name == "Mystic Monastery" }.map { it.item.id })
        assertEquals(setOf("Thran Turbine", "Crop Rotation"), plan.open.map { it.line.name }.toSet())
        assertTrue("another printing" in plan.open.single { it.line.name == "Crop Rotation" }.why)
        // Tokens and basic lands the owner doesn't keep are simply not there.
        assertEquals(setOf("Golem (4/4)", "Plains (403 - Retro Frame)"), plan.notOwned.map { it.name }.toSet())
    }

    @Test fun joiningCopies() {
        assertEquals(2.5, PurchasePrices.joined(3, 2.0, 1, 4.0)!!, 1e-9)
        assertEquals(4.0, PurchasePrices.joined(0, null, 1, 4.0)!!, 1e-9)
        // Copies without a known price take the new one, so every copy carries a price.
        assertEquals(4.0, PurchasePrices.joined(3, null, 1, 4.0)!!, 1e-9)
        assertEquals(2.0, PurchasePrices.joined(3, 2.0, 1, null)!!, 1e-9)
        assertNull(PurchasePrices.joined(3, null, 1, null))
    }
}

class PackageCheckTest {
    private fun line(name: String, set: String, number: String, qty: Int = 1, price: Double = 1.0, lang: String = "EN", foil: Boolean = false, cond: String = "NM") =
        OrderLine(set.uppercase(), "Set $set", name, number, qty, price, cond, lang, foil, false, false)

    private fun ordered(index: Int, l: OrderLine, guessed: Boolean = false, found: Boolean = true) =
        OrderCandidate(index, l, if (found) card(l.name, l.setCode.lowercase(), l.number) else null, token = false, basicLand = false, guessed = guessed)

    private fun scan(id: Long, name: String, set: String, number: String, qty: Int = 1, lang: String = "EN", exact: Boolean = true) =
        ScannedCard(id = id, card = card(name, set, number), foil = false, quantity = qty, language = lang, exactPrinting = exact)

    private val order = listOf(
        ordered(0, line("Sol Ring", "cmm", "400", qty = 2)),
        ordered(1, line("Counterspell", "dmr", "45", price = 1.5)),
        ordered(2, line("Thran Turbine", "usg", "311", price = 1.74)),
        ordered(3, line("Crop Rotation", "ulg", "98", price = 3.0)),
        ordered(4, line("Rhystic Study", "pcy", "45", price = 30.0, cond = "EX", foil = true)),
        ordered(5, line("Brainstorm", "ice", "61", qty = 3, price = 0.9)),
    )
    private val scans = listOf(
        scan(1, "Sol Ring", "cmm", "400", qty = 2),
        scan(2, "Thran Turbine", "usg", "311", lang = "DE"),
        scan(3, "Crop Rotation", "ddr", "7"),
        scan(4, "Rhystic Study", "pcy", "45"),
        scan(5, "Brainstorm", "ice", "61"),
        scan(6, "Brainstorm", "mmq", "61", exact = false),
        scan(7, "Lightning Bolt", "m10", "146", qty = 2),
    )

    @Test fun tellsWhatArrived() {
        val r = PackageCheck.check(order, scans)
        fun at(i: Int) = r.lines.single { it.cand.index == i }
        assertEquals(Status.RECEIVED, at(0).status)
        assertEquals(Status.MISSING, at(1).status)
        assertEquals(Status.WRONG_LANGUAGE, at(2).status)
        assertEquals(Status.WRONG_PRINTING, at(3).status)
        assertEquals(Status.RECEIVED, at(4).status)
        assertEquals(listOf("foil", "EX"), at(4).byHand)
        // One exact, one the scanner only read the name of, one not scanned.
        assertEquals(listOf(Status.RECEIVED to 1, Status.PRINTING_UNSURE to 1, Status.MISSING to 1), at(5).parts.map { it.status to it.quantity })
        assertEquals(9, r.ordered)
        assertEquals(4, r.count(Status.RECEIVED))
        assertEquals(2, r.count(Status.MISSING))
        assertEquals(listOf(7L to 2), r.extras.map { it.first.id to it.second })
        assertEquals(listOf(1, 2, 3, 5), r.problems.map { it.cand.index })
    }

    @Test fun anExactScanIsNotTakenByAnotherLine() {
        // Two lines of the same card in different printings; the scans come in the other order.
        val two = listOf(ordered(0, line("Opt", "xln", "65")), ordered(1, line("Opt", "dom", "60")))
        val r = PackageCheck.check(two, listOf(scan(1, "Opt", "dom", "60"), scan(2, "Opt", "xln", "65")))
        assertTrue(r.lines.all { it.status == Status.RECEIVED })
        assertTrue(r.extras.isEmpty())
    }

    @Test fun markedAsReceivedByHand() {
        val r = PackageCheck.check(order, scans, accepted = setOf(1, 2))
        assertEquals(Status.BY_HAND, r.lines.single { it.cand.index == 1 }.status)
        assertEquals(Status.BY_HAND, r.lines.single { it.cand.index == 2 }.status)
        assertEquals(listOf(3, 5), r.problems.map { it.cand.index })
        // The German scan still belongs to its line: it isn't an extra card.
        assertEquals(listOf(7L), r.extras.map { it.first.id })
    }

    @Test fun cardsFoundByNameOnlyAreNotCalledWrong() {
        val guessed = listOf(ordered(0, line("Opt", "xln", "65"), guessed = true), ordered(1, line("Shock", "m21", "159"), found = false))
        val r = PackageCheck.check(guessed, listOf(scan(1, "Opt", "dom", "60"), scan(2, "Shock", "m20", "160")))
        assertTrue(r.lines.all { it.status == Status.PRINTING_UNSURE })
        assertTrue(r.problems.isEmpty())
    }

    @Test fun cardmarketMonthSaysNoLanguage() {
        val monthly = OrderLine("", "Core 2020", "Cavalier of Dawn", "", 1, 2.55, "NM", "EN", false, false, false, productId = 378651, versionKnown = false)
        val cand = OrderCandidate(0, monthly, card("Cavalier of Dawn", "m20", "10"), token = false, basicLand = false)
        val r = PackageCheck.check(listOf(cand), listOf(scan(1, "Cavalier of Dawn", "m20", "10", lang = "JA")))
        assertEquals(Status.RECEIVED, r.lines.single().status)
        // The scan's language and finish are kept, the order's price is added.
        val add = PackageCheck.additions(r, extras = false).single()
        assertEquals("JA", add.language)
        assertEquals(2.55, add.price!!, 1e-9)
    }

    @Test fun whatGoesIntoTheCollection() {
        val r = PackageCheck.check(order, scans, accepted = setOf(1))
        val adds = PackageCheck.additions(r, extras = true)
        fun of(name: String) = adds.filter { it.card.name == name }
        // Received: the order's condition, finish and price.
        val study = of("Rhystic Study").single()
        assertEquals(Finish.FOIL, study.finish)
        assertEquals("EX", study.condition)
        assertEquals(30.0, study.price!!, 1e-9)
        // Marked by hand without a scan: added as ordered.
        assertEquals("dmr", of("Counterspell").single().card.setCode)
        // Wrong language and wrong printing: the card that is there, at the price paid.
        assertEquals("DE", of("Thran Turbine").single().language)
        assertEquals("ddr", of("Crop Rotation").single().card.setCode)
        assertEquals(3.0, of("Crop Rotation").single().price!!, 1e-9)
        // The name-only scan becomes the ordered printing; the missing copy isn't added.
        assertEquals(listOf("ice", "ice"), of("Brainstorm").map { it.card.setCode })
        assertEquals(2, of("Brainstorm").sumOf { it.quantity })
        // Extras as scanned, without a price.
        assertNull(of("Lightning Bolt").single().price)
        assertEquals(2, of("Lightning Bolt").single().quantity)
        assertTrue(PackageCheck.additions(r, extras = false).none { it.card.name == "Lightning Bolt" })
        assertEquals(10, adds.sumOf { it.quantity })
    }

    @Test fun reportForTheShop() {
        val text = PackageCheck.report(PackageCheck.check(order, scans), "CardTrader order 20260623hxrahx")
        assertEquals(
            """
            Problems with CardTrader order 20260623hxrahx

            Missing (2):
            - 1x Counterspell, Set dmr (DMR) #45, EN, NM, paid 1.50 EUR each
            - 1x Brainstorm, Set ice (ICE) #61, EN, NM, paid 0.90 EUR each

            Wrong language (1):
            - 1x Thran Turbine, Set usg (USG) #311, EN, NM, paid 1.74 EUR each; received in DE

            Wrong printing (1):
            - 1x Crop Rotation, Set ulg (ULG) #98, EN, NM, paid 3.00 EUR each; received Set ddr (DDR) #7
            """.trimIndent(),
            text,
        )
        val fine = PackageCheck.report(PackageCheck.check(order.take(1), scans.take(1)), "the order")
        assertTrue("every ordered card was received" in fine)
    }
}
