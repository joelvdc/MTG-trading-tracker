package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.BackupFile
import com.mtgtrader.data.BackupRestore
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.Finish
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.OrderFiles
import com.mtgtrader.data.OrderFormat
import com.mtgtrader.data.OrderLine
import com.mtgtrader.data.OrderedCard
import com.mtgtrader.data.Orders
import com.mtgtrader.data.Precons
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.PriceSource
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.Pricing
import com.mtgtrader.data.ProfitLoss
import com.mtgtrader.data.PurchaseOrder
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncDeletion
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.SyncStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

private fun ref(name: String, set: String = "tst", number: String = "1", product: Int? = null, eur: Double? = null, eurFoil: Double? = null) =
    CardRef("$name|$set|$number", name, set, "Set $set", number, "rare", null, product, eur, eurFoil, true, true)

private fun price(product: Int, trend: Double, foil: Double? = null) = PriceEntity(product, trend, trend, trend, trend, trend, trend, foil, foil, foil, foil, foil, foil)

/** Condition-adjusted values: off by default, and everywhere a copy's value is worked out once on. */
class ConditionValuesTest {
    private fun row(cond: String, eur: Double = 10.0, qty: Int = 1, paid: Double? = null) =
        CollectionRow(CollectionItem(card = ref("Card $cond", product = 1), foil = false, quantity = qty, condition = cond, purchasePrice = paid), price(1, eur))

    @After fun reset() {
        Pricing.conditionPct = null
        Pricing.source = PriceSource.CARDMARKET
    }

    @Test fun offUntilSwitchedOn() {
        assertEquals(10.0, row("PL").unitPrice(PriceType.TREND)!!, 1e-9)
        assertNull(row("PL").conditionNote)
        assertEquals(1.0, Pricing.conditionFactor("PO"), 1e-9)
    }

    @Test fun aCopyCountsForItsPercentage() {
        Pricing.conditionPct = Settings.DEFAULT_CONDITION_PCTS
        assertEquals(10.0, row("NM").unitPrice(PriceType.TREND)!!, 1e-9)
        assertEquals(8.5, row("EX").unitPrice(PriceType.TREND)!!, 1e-9)
        assertEquals(3.0, row("PO").unitPrice(PriceType.TREND)!!, 1e-9)
        // An adjusted price says so; a full one doesn't.
        assertEquals("EX 85%", row("EX").conditionNote)
        assertNull(row("NM").conditionNote)
        assertNull(row("MT").conditionNote)
        // Totals follow: the collection's value and what a source's total falls back to.
        val rows = listOf(row("NM", qty = 2), row("GD"))
        assertEquals(27.0, com.mtgtrader.data.ValueHistory.totalValue(rows, PriceType.TREND), 1e-9)
        assertEquals(27.0, com.mtgtrader.data.ValueHistory.sourceTotal(rows, PriceSource.TCGPLAYER, PriceType.TREND), 1e-9)
    }

    @Test fun percentagesAreKeptAsText() {
        val changed = Settings.DEFAULT_CONDITION_PCTS + ("EX" to 90) + ("PO" to 10)
        assertEquals(changed, Settings.decodePcts(Settings.encodePcts(changed)))
        // What's missing or unreadable keeps its default; nothing leaves 0 to 100.
        assertEquals(Settings.DEFAULT_CONDITION_PCTS, Settings.decodePcts(null))
        assertEquals(Settings.DEFAULT_CONDITION_PCTS + ("GD" to 100), Settings.decodePcts("GD=250,XX=5,rubbish"))
    }

    @Test fun paidAgainstWorth() {
        val rows = listOf(
            row("NM", eur = 10.0, qty = 2, paid = 4.0),   // +6.00 a copy
            row("NM", eur = 1.0, paid = 3.0),             // -2.00
            row("NM", eur = 50.0),                        // no purchase price: left out
        )
        val p = ProfitLoss.of(rows, PriceType.TREND)
        assertEquals(11.0, p.paid, 1e-9)
        assertEquals(21.0, p.worth, 1e-9)
        assertEquals(10.0, p.gain, 1e-9)
        assertEquals(3, p.counted)
        assertEquals(4, p.copies)
        assertEquals(6.0, p.best.single().gain, 1e-9)
        assertEquals(150.0, p.best.single().pct, 1e-9)
        assertEquals(-2.0, p.worst.single().gain, 1e-9)
    }
}

class OrdersTest {
    private val monthly = """
        Shipment nr.;Date of purchase;Article;Product ID;Localized Product Name;Expansion;Category;Amount;Article Value;Total;Currency;Comments
        111;2024-12-02 16:38:27;Essence Flux;289154;Essence Flux;Shadows over Innistrad;Magic Single;1;2.38;2.38;EUR;
        111;2024-12-02 16:38:27;Cavalier of Dawn;378651;Cavalier of Dawn;Core 2020;Magic Single;3;2.55;7.65;EUR;
        222;2024-12-20 09:00:00;Force of Will;7894;Force of Will;Alliances;Magic Single;1;45;45;EUR;
    """.trimIndent()

    @After fun reset() {
        Pricing.conditionPct = null
    }

    @Test fun aMonthIsOneOrderPerShipment() {
        val orders = Orders.fromFile(OrderFiles.read(monthly.toByteArray()), "2026-10-13") { _, _ -> null }
        assertEquals(listOf("111", "222"), orders.map { it.number })
        val first = orders[0]
        assertEquals("Cardmarket", first.shop)
        assertEquals("Cardmarket shipment 111", first.title)
        assertEquals("2024-12-02", first.date)
        assertEquals(4, first.cards)
        assertEquals(10.03, first.paid, 1e-9)
        val cards = Orders.lines(first)
        assertEquals(listOf(289154, 378651), cards.map { it.cardmarketId })
        // The monthly file says neither condition nor language.
        assertNull(cards[0].condition)
        assertNull(cards[0].language)
    }

    @Test fun cardTraderOrderFromItsFile() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("cardtrader/order_sample.xls")!!.readBytes()
        val file = OrderFiles.read(bytes, "cardtrader_order_20260623hxrahx.xls")
        val known = ref("Echocasting Symposium", "sos", "44", product = 9001)
        val order = Orders.fromFile(file, "2026-10-13") { _, l -> known.takeIf { l.name == "Echocasting Symposium" } }.single()
        assertEquals("CardTrader order 20260623hxrahx", order.title)
        assertEquals("2026-06-23", order.date)
        assertEquals(file.lines.sumOf { it.quantity }, order.cards)
        val cards = Orders.lines(order)
        assertEquals(9001, cards.single { it.name == "Echocasting Symposium" }.cardmarketId)
        assertEquals("NM", cards.single { it.name == "Echocasting Symposium" }.condition)
        // A card nobody could identify is still listed, without a price of today.
        assertNull(cards.first { it.name != "Echocasting Symposium" }.cardmarketId)
    }

    @Test fun worthToday() {
        val cards = listOf(
            OrderedCard("Bought cheap", quantity = 2, price = 1.0, cardmarketId = 1),
            OrderedCard("Foil", quantity = 1, price = 5.0, foil = true, cardmarketId = 2),
            OrderedCard("Played", quantity = 1, price = 2.0, condition = "PL", cardmarketId = 1),
            OrderedCard("Unknown", quantity = 3, price = 0.5),
        )
        val prices = mapOf(1 to price(1, 4.0), 2 to price(2, 1.0, foil = 9.0))
        val order = Orders.of("CardTrader", "x", "Test", "2026-01-01", cards)
        assertEquals(10.5, order.paid, 1e-9)
        assertEquals(7, order.cards)
        val w = Orders.worth(order, prices, PriceType.TREND)
        assertEquals(2 * 4.0 + 9.0 + 4.0, w.value, 1e-9)
        assertEquals(4, w.priced)
        assertEquals(3, w.unpriced)
        // What the priced copies cost, so the three unknown ones don't count as a loss.
        assertEquals(9.0, w.paid, 1e-9)
        // With condition-adjusted values on, the played copy counts for less here too.
        Pricing.conditionPct = Settings.DEFAULT_CONDITION_PCTS
        assertEquals(2 * 4.0 + 9.0 + 4.0 * 0.45, Orders.worth(order, prices, PriceType.TREND).value, 1e-9)
    }

    @Test fun pastOrdersGetTheirPrintingsFromTheCollectionThenALookup() = runBlocking {
        val ct = listOf(
            OrderLine("SOS", "Secrets of Strixhaven", "Echocasting Symposium", "044", 1, 2.31, "NM", "EN", false, false, false, date = "2026-06-23", order = "abc"),
            OrderLine("BLB", "Bloomburrow", "Clifftop Lookout", "168", 2, 0.24, "NM", "EN", false, false, false, date = "2026-06-23", order = "abc"),
            OrderLine("XYZ", "Nowhere", "Mystery Card", "007", 1, 1.0, "NM", "EN", false, false, false, date = "2026-06-23", order = "abc"),
        )
        val file = com.mtgtrader.data.OrderFile(OrderFormat.CARDTRADER, ct)
        val owned = CollectionItem(id = 1, card = ref("Echocasting Symposium", "sos", "44", product = 9001), foil = false, quantity = 1)
        var asked = emptyList<Pair<String, String>>()
        val order = Orders.fromFiles(listOf(file), listOf(owned), "2026-10-13") { keys ->
            asked = keys
            mapOf(("blb" to "168") to ref("Clifftop Lookout", "blb", "168", product = 9002))
        }.single()
        // Only what the collection doesn't know is looked up.
        assertEquals(listOf("blb" to "168", "xyz" to "7"), asked)
        assertEquals(listOf(9001, 9002, null), Orders.lines(order).map { it.cardmarketId })
    }

    @Test fun theAppsOwnOrdersFile() {
        val precon = Orders.precon("Deep Clue Sea", "2024-02-09", listOf(OrderedCard("Bennie Bracks, Zoologist", "mkc", "57", 1, 4.57, scryfallId = "x", cardmarketId = 7)))
        val text = Orders.export(listOf(precon))
        assertTrue(Orders.isExport(text.toByteArray()))
        assertFalse(Orders.isExport("idProduct;groupCount;price".toByteArray()))
        val back = Orders.import(text).single()
        assertEquals(precon.copy(addedAt = 0), back.copy(addedAt = 0))
        assertEquals("Precon", back.shop)
        assertEquals("Deep Clue Sea 2024-02-09", back.number)
        // The same deck bought again another day is another order.
        assertFalse(Orders.same(precon, Orders.precon("Deep Clue Sea", "2025-01-01", Orders.lines(precon))))
        assertTrue(Orders.same(precon, back))
    }

    @Test fun ordersSyncLikeEverythingElse() {
        fun order(uid: String, number: String?, title: String, at: Long) = Orders.of("Cardmarket", number, title, "2026-01-01", listOf(OrderedCard("A", price = 1.0))).copy(uid = uid, updatedAt = at)
        val now = 1_000_000L
        val local = SyncData(orders = listOf(order("a", "1", "Old title", 100), order("b", "2", "Mine", 100), order("gone", "3", "Deleted", 100)))
        val remote = SyncData(orders = listOf(order("a", "1", "New title", 200), order("c", "2", "Same order, read on the other phone", 300)), deletions = listOf(SyncDeletion("gone", 150)))
        val m = SyncMerge.merge(local.sorted(), remote.sorted(), now)
        // Newest change wins; a deletion wins over older changes; the same shop order read twice is kept once.
        assertEquals(listOf("New title", "Same order, read on the other phone"), m.orders.map { it.title })
        assertTrue(m.deletions.any { it.uid == "b" } && m.deletions.any { it.uid == "gone" })
        assertEquals(m, SyncMerge.merge(m, m, now))
        // The file carries them, in format 3 (4 since 1.35).
        val back = SyncFile.decode(SyncFile.encode(SyncFile(data = m)))
        assertTrue(back.format >= 3)
        assertEquals(m.orders, back.data.orders)
    }

    @Test fun anOlderBackupLeavesTheOrdersAlone() {
        val order = Orders.of("Cardmarket", "1", "Order", "2026-01-01", listOf(OrderedCard("A", price = 1.0))).copy(uid = "o1", updatedAt = 5)
        val current = SyncData(orders = listOf(order))
        // A backup from before 1.33 has no orders in it.
        val restored = BackupRestore.target(current, SyncData(), now = 99)
        assertEquals(listOf(order), restored.orders)
        assertTrue(restored.deletions.none { it.uid == "o1" })
        // A backup with orders puts those back.
        val other = order.copy(uid = "o2", title = "From the backup")
        val fromNew = BackupRestore.target(current, SyncData(orders = listOf(other)), now = 99)
        assertEquals(listOf("From the backup"), fromNew.orders.map { it.title })
        assertTrue(fromNew.deletions.any { it.uid == "o1" })
        assertTrue(BackupFile.FORMAT >= 3)
    }
}

class PreconsTest {
    private val list = """{"meta":{},"data":[
        {"code":"MKC","fileName":"DeepClueSea_MKC","name":"Deep Clue Sea","releaseDate":"2024-02-09","type":"Commander Deck"},
        {"code":"FRC","fileName":"MultiverseReforged_FRC","name":"Multiverse Reforged","releaseDate":"2026-10-02","type":"Commander Deck"},
        {"code":"10E","fileName":"ArcanisSGuile_10E","name":"Arcanis's Guile","releaseDate":"2007-07-13","type":"Theme Deck"}]}"""

    private val deck = """{"data":{"name":"Deep Clue Sea","code":"MKC","releaseDate":"2024-02-09","type":"Commander Deck",
        "commander":[{"name":"Morska, Undersea Sleuth","count":1,"isFoil":true,"setCode":"MKC","number":"3","identifiers":{"scryfallId":"morska"},"types":["Creature"],"supertypes":["Legendary"]}],
        "mainBoard":[
          {"name":"Bennie Bracks, Zoologist","count":1,"isFoil":false,"setCode":"MKC","number":"57","identifiers":{"scryfallId":"bennie"},"types":["Creature"],"supertypes":["Legendary"]},
          {"name":"Sol Ring","count":1,"isFoil":false,"setCode":"MKC","number":"238","identifiers":{"scryfallId":"ring"},"types":["Artifact"],"supertypes":[]},
          {"name":"Island","count":7,"isFoil":false,"setCode":"MKM","number":"274","identifiers":{"scryfallId":"island"},"types":["Land"],"supertypes":["Basic"]},
          {"name":"Magnifying Glass","count":2,"isFoil":false,"setCode":"MKM","number":"255","identifiers":{"scryfallId":"glass"},"types":["Artifact"],"supertypes":[]}],
        "sideBoard":[],"tokens":[{"name":"Clue"}]}}"""

    @Test fun commanderDecksNewestFirst() {
        val decks = Precons.parseList(list)
        assertEquals(listOf("Multiverse Reforged", "Deep Clue Sea"), decks.map { it.name })
        assertEquals(listOf("Deep Clue Sea"), Precons.search(decks, "clue").map { it.name })
        assertEquals(listOf("Deep Clue Sea"), Precons.search(decks, "mkc").map { it.name })
        assertEquals(2, Precons.search(decks, " ").size)
    }

    @Test fun aDecksCardsWithoutBasicLands() {
        val cards = Precons.cards(Precons.parseDeck(deck))
        assertEquals(listOf("Morska, Undersea Sleuth", "Bennie Bracks, Zoologist", "Sol Ring", "Magnifying Glass"), cards.map { it.name })
        assertTrue(cards.first().isFoil)
        assertEquals(2, cards.last().count)
    }

    @Test fun thePriceIsSplitByWorthAndAddsUp() {
        val cards = Precons.cards(Precons.parseDeck(deck))
        val worth = mapOf("morska" to 1.0, "bennie" to 8.0, "ring" to 1.0, "glass" to null)
        val split = Precons.split(46.92, cards.map { Triple(ref(it.name, it.setCode.lowercase(), it.number, eur = worth[it.identifiers.scryfallId]), it, worth[it.identifiers.scryfallId]) })
        // Every cent of the deck's price lands on a card.
        assertEquals(46.92, split.sumOf { it.price * it.quantity }, 1e-9)
        val by = split.associateBy { it.card.name }
        // Eight times the worth, eight times the share (give or take the rounding cent).
        assertEquals(8.0, by.getValue("Bennie Bracks, Zoologist").price / by.getValue("Sol Ring").price, 0.02)
        // A card without a price still gets a little, per copy.
        assertTrue(by.getValue("Magnifying Glass").price > 0)
        assertEquals(2, by.getValue("Magnifying Glass").quantity)
        assertEquals(Finish.FOIL, by.getValue("Morska, Undersea Sleuth").finish)
        // As an order line: priced as a Near Mint English copy.
        assertEquals("NM", by.getValue("Sol Ring").ordered.condition)
        assertTrue(Precons.split(10.0, emptyList()).isEmpty())
    }
}

/** 1.33 against a real database: the update from version 13, and orders being remembered and synced. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release133DbTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase
    private lateinit var repo: MtgRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        val http = OkHttpClient()
        repo = MtgRepository(db, ScryfallApi(http), PriceGuideRepository(context, http, db, Settings(context)), CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun tearDown() = db.close()

    /** A version 13 database (app 1.29–1.32) exactly as Room made it, from the exported schema. */
    private fun createVersion13(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/13.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL(
            "INSERT INTO collection (id, foil, condition, language, quantity, addedAt, etched, binderId, uid, updatedAt, scryfallId, name, setCode, " +
                "setName, collectorNumber, rarity, imageUrl, cardmarketId, fallbackEur, fallbackEurFoil, hasNonFoil, hasFoil, foilType, hasEtched, " +
                "flavorName, notes, purchasePrice, cardmarketFoilId, signed, altered) VALUES (1, 0, 'NM', 'EN', 4, 1, 0, 0, 'u1', 5, 'bs', 'Brainstorm', 'ice', " +
                "'Ice Age', '61', 'common', NULL, 1, 1.0, NULL, 1, 0, NULL, 0, NULL, 'from a friend', 0.5, NULL, 1, 0)"
        )
        sql.version = 13
        sql.close()
    }

    @Test
    fun updatingFromVersion13KeepsEverythingAndAddsOrders() = runBlocking {
        val file = context.getDatabasePath("update-133.db").apply { parentFile?.mkdirs(); delete() }
        createVersion13(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-133.db")
            .addMigrations(AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val item = updated.collectionDao().all().single()
            assertEquals(4, item.quantity)
            assertEquals(0.5, item.purchasePrice!!, 1e-9)
            assertTrue(item.signed)
            // The update doesn't count as a change: nothing to sync until the user edits something.
            assertEquals(5, item.updatedAt)
            assertTrue(updated.orderDao().all().isEmpty())
            val id = updated.orderDao().insert(Orders.of("Cardmarket", "1", "First order", "2026-10-13", listOf(OrderedCard("A", price = 1.0))))
            // The triggers give a new order its sync identity.
            assertNotNull(updated.orderDao().byId(id)!!.uid)
        } finally {
            updated.close()
        }
    }

    @Test
    fun ordersAreRememberedOnceAndGoThroughSync() = runBlocking {
        val first = Orders.of("CardTrader", "20260623hxrahx", "CardTrader order 20260623hxrahx", "2026-06-23", listOf(OrderedCard("A", quantity = 2, price = 1.0)))
        assertEquals(1, repo.recordOrders(listOf(first)))
        val saved = db.orderDao().all().single()
        assertNotNull(saved.uid)
        // The same order read again (now with a card found) replaces the first reading, keeping its identity.
        val again = Orders.of("CardTrader", "20260623hxrahx", "CardTrader order 20260623hxrahx", "2026-06-23", listOf(OrderedCard("A", quantity = 2, price = 1.0, cardmarketId = 5)))
        assertEquals(0, repo.recordOrders(listOf(again)))
        val after = db.orderDao().all().single()
        assertEquals(saved.uid, after.uid)
        assertEquals(5, Orders.lines(after).single().cardmarketId)
        assertTrue(after.updatedAt > saved.updatedAt)
        // An order without cards isn't worth remembering.
        assertEquals(0, repo.recordOrders(listOf(Orders.of("Cardmarket", "9", "Empty", "2026-01-01", emptyList()))))

        // Another phone's order comes in through sync, and a deleted one goes out as a deletion.
        val store = SyncStore(db, Settings(context))
        val (local, stamp) = store.snapshot()
        assertEquals(1, local.orders.size)
        val theirs = Orders.of("Cardmarket", "77", "Their order", "2026-07-01", listOf(OrderedCard("B", price = 2.0))).copy(uid = "theirs", updatedAt = 10)
        val merged = SyncMerge.merge(local, SyncData(orders = listOf(theirs)), System.currentTimeMillis())
        assertTrue(store.apply(local, merged, stamp))
        assertEquals(setOf("CardTrader order 20260623hxrahx", "Their order"), db.orderDao().all().map { it.title }.toSet())
        repo.deleteOrder(db.orderDao().all().first { it.uid == "theirs" }.id)
        assertTrue(store.snapshot().first.deletions.any { it.uid == "theirs" })
    }

    @Test
    fun aPreconGoesIntoABinderWithItsPricesAndBecomesAnOrder() = runBlocking {
        val bennie = ref("Bennie Bracks, Zoologist", "mkc", "57", product = 11, eur = 8.0)
        val ring = ref("Sol Ring", "mkc", "238", product = 12, eur = 1.0)
        fun card(name: String, n: String, id: String) = Precons.Card(name, 1, false, "MKC", n, Precons.Ids(id))
        // One Sol Ring is already there, bought for 2.00.
        repo.addToCollection(ring, Finish.NONFOIL, "NM", "EN", 1)
        db.collectionDao().all().single().let { db.collectionDao().update(it.copy(purchasePrice = 2.0)) }
        val split = Precons.split(9.0, listOf(Triple(bennie, card("Bennie Bracks, Zoologist", "57", "b"), 8.0), Triple(ring, card("Sol Ring", "238", "r"), 1.0)))
        assertEquals(2, repo.addPrecon("Deep Clue Sea", "2024-02-09", split, BinderChoice()))
        val stacks = db.collectionDao().all().associateBy { it.card.name }
        assertEquals(8.0, stacks.getValue("Bennie Bracks, Zoologist").purchasePrice!!, 1e-9)
        // Two Sol Rings now: the average of 2.00 and its 1.00 share.
        assertEquals(2, stacks.getValue("Sol Ring").quantity)
        assertEquals(1.5, stacks.getValue("Sol Ring").purchasePrice!!, 1e-9)
        val order = db.orderDao().all().single()
        assertEquals("Precon", order.shop)
        assertEquals("Deep Clue Sea", order.title)
        assertEquals(9.0, order.paid, 1e-9)
        assertEquals("2024-02-09", order.date)
    }
}
