package com.mtgtrader

import android.Manifest
import android.app.NotificationManager
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.BackupFile
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CardTrader
import com.mtgtrader.data.CardTraderAuthException
import com.mtgtrader.data.CardTraderParse
import com.mtgtrader.data.CtCondition
import com.mtgtrader.data.CtFilter
import com.mtgtrader.data.CtListing
import com.mtgtrader.data.Finish
import com.mtgtrader.data.PriceAlerts
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceSource
import com.mtgtrader.data.PriceSourceStore
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncData
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncMerge
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.WishPrice
import com.mtgtrader.data.WishPrinting
import com.mtgtrader.data.WishlistItem
import com.mtgtrader.data.WishlistWatch
import com.mtgtrader.ui.wishPriceLine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** 1.37: wishlist price alerts, with CardTrader's prices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release137Test {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun ref(name: String, id: String = name.lowercase(), cm: Int? = null, foil: Boolean = false) =
        CardRef(id, name, "m21", "Core Set 2021", "1", "rare", null, cm, null, null, true, foil)

    // --- CardTrader's answers ---

    /** The marketplace answer from CardTrader's API reference, plus offers that must be left out. */
    private val products = """
        {"10050": [
          {"id": 1, "blueprint_id": 10050, "name_en": "Dragon Fodder", "quantity": 1, "price": {"cents": 2, "currency": "EUR"},
           "properties_hash": {"condition": "Moderately Played", "signed": false, "mtg_foil": false, "mtg_language": "en", "altered": false},
           "expansion": {"id": 92, "code": "ptkdf"}, "user": {"id": 41687, "can_sell_via_hub": false, "country_code": "FI"},
           "graded": false, "on_vacation": false, "bundle_size": 1},
          {"id": 2, "blueprint_id": 10050, "quantity": 3, "price": {"cents": 5, "currency": "EUR"},
           "properties_hash": {"condition": "Near Mint", "mtg_foil": false, "mtg_language": "de"},
           "user": {"can_sell_via_hub": true}, "graded": false, "on_vacation": false, "bundle_size": 1},
          {"id": 3, "blueprint_id": 10050, "quantity": 1, "price": {"cents": 7, "currency": "EUR"},
           "properties_hash": {"condition": "Near Mint", "mtg_foil": false, "mtg_language": "en", "signed": true},
           "user": {"can_sell_via_hub": true}, "graded": false, "on_vacation": false, "bundle_size": 1},
          {"id": 4, "blueprint_id": 10050, "quantity": 1, "price": {"cents": 9, "currency": "EUR"},
           "properties_hash": {"condition": "Slightly Played", "mtg_foil": false, "mtg_language": "en"},
           "user": {"can_sell_via_hub": false}, "graded": false, "on_vacation": false, "bundle_size": 1},
          {"id": 5, "blueprint_id": 10050, "quantity": 1, "price": {"cents": 11, "currency": "EUR"},
           "properties_hash": {"condition": "Near Mint", "mtg_foil": false, "mtg_language": "en"},
           "user": {"can_sell_via_hub": true}, "graded": false, "on_vacation": true, "bundle_size": 1},
          {"id": 6, "blueprint_id": 10050, "quantity": 1, "price": {"cents": 12, "currency": "EUR"},
           "properties_hash": {"condition": "Slightly Played", "mtg_foil": false, "mtg_language": "en"},
           "user": {"can_sell_via_hub": true}, "graded": false, "on_vacation": false, "bundle_size": 1},
          {"id": 7, "blueprint_id": 10050, "quantity": 1, "price": {"cents": 4, "currency": "EUR"},
           "properties_hash": {"condition": "Near Mint", "mtg_foil": true, "mtg_language": "en"},
           "user": {"can_sell_via_hub": true}, "graded": false, "on_vacation": false, "bundle_size": 1}
        ]}
    """.trimIndent()

    private val eur = { amount: Double, currency: String -> if (currency == "EUR") amount else null }

    @Test
    fun readsTheMarketplaceAnswer() {
        val l = CardTraderParse.listings(products)
        assertEquals(7, l.size)
        val first = l.first()
        assertEquals(CtListing(2, "EUR", "Moderately Played", "en", foil = false, quantity = 1, zero = false), first)
        assertTrue(l[1].zero)
        assertTrue(l[2].signed)
        assertTrue(l[4].onVacation)
        assertTrue(l[6].foil)
    }

    @Test
    fun picksTheCheapestOfferThatFits() {
        val l = CardTraderParse.listings(products)
        fun pick(language: String, condition: CtCondition, zero: Boolean, foil: Boolean = false) =
            CardTraderParse.cheapest(l, CtFilter(language, condition, zero), foil, eur)?.first?.cents
        // CardTrader Zero, English, Slightly Played or better: the German, signed and played ones, and the
        // seller on holiday, are left out.
        assertEquals(12L, pick("en", CtCondition.SLIGHTLY_PLAYED, zero = true))
        // Every seller: the Slightly Played copy of a seller who isn't on CT Zero.
        assertEquals(9L, pick("en", CtCondition.SLIGHTLY_PLAYED, zero = false))
        // Any condition, any seller: the Moderately Played one.
        assertEquals(2L, pick("en", CtCondition.ANY, zero = false))
        // Any language: the German Near Mint one, on CT Zero.
        assertEquals(5L, pick("", CtCondition.NEAR_MINT, zero = true))
        // Foil.
        assertEquals(4L, pick("en", CtCondition.NEAR_MINT, zero = true, foil = true))
        // Nothing fits.
        assertNull(pick("jp", CtCondition.ANY, zero = false))
        // A price in a currency that can't be converted is left out.
        assertNull(CardTraderParse.cheapest(l.map { it.copy(currency = "XYZ") }, CtFilter("", CtCondition.ANY, false), false, eur))
    }

    @Test
    fun ctZeroWhenItIsntAmongTheCheapest() {
        fun offer(cents: Long, zero: Boolean) = CtListing(cents, "EUR", "Near Mint", "en", foil = false, zero = zero)
        val filter = CtFilter("en", CtCondition.NEAR_MINT, zeroOnly = true)
        // A full page (25 offers) without CT Zero: a CT Zero copy costs at least the dearest one shown.
        val full = (1..25).map { offer(100L + it, zero = false) }
        val p = CardTraderParse.pick(full, filter, false, eur)
        assertNull(p.best)
        assertEquals(1.01, p.anySeller!!.second, 0.001)
        assertEquals(1.25, p.zeroOver!!, 0.001)
        // Fewer than 25 is every offer there is: no CT Zero copy at all, and nothing to guess.
        assertNull(CardTraderParse.pick(full.take(10), filter, false, eur).zeroOver)
        // A CT Zero offer among them: that one, and the cheaper one from any seller next to it.
        val mixed = CardTraderParse.pick(full.take(10) + offer(500, zero = true), filter, false, eur)
        assertEquals(5.0, mixed.best!!.second, 0.001)
        assertEquals(1.01, mixed.anySeller!!.second, 0.001)
        assertNull(mixed.zeroOver)
        // Every seller counts: no separate "any seller" price, no guess.
        val all = CardTraderParse.pick(full, filter.copy(zeroOnly = false), false, eur)
        assertEquals(1.01, all.best!!.second, 0.001)
        assertNull(all.zeroOver)
    }

    @Test
    fun readsExpansionsAndBlueprints() {
        val expansions = CardTraderParse.expansions(
            """[{"id": 1, "game_id": 1, "code": "gnt", "name": "Game Night"}, {"id": 3, "game_id": 1, "code": "PGRN", "name": "Guilds of Ravnica Promos"},
               {"id": 9, "game_id": 5, "code": "sv1", "name": "A Pokémon set"}]""",
        )
        assertEquals(mapOf("gnt" to 1L, "pgrn" to 3L), expansions)
        val blueprints = CardTraderParse.blueprints(
            """[{"id": 90, "name": "Beast // Plant", "scryfall_id": "381854e2"}, {"id": 89, "name": "Beast // Plant", "scryfall_id": "381854e2"},
               {"id": 91, "name": "Booster box", "scryfall_id": null}, {"id": 92, "name": "No id"}]""",
            8,
        )
        assertEquals(1, blueprints.size)
        assertEquals(89L, blueprints.single().blueprintId)
        assertEquals(8L, blueprints.single().expansionId)
    }

    /** A CardTrader that answers from here instead of the internet. */
    private fun fakeCardTrader(db: AppDatabase, calls: MutableList<String>, answer: (String) -> Pair<Int, String>): CardTrader {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            calls += url.encodedPath.substringAfter("/api/v2/") + (url.query?.let { "?$it" } ?: "")
            assertEquals("Bearer secret-token", chain.request().header("Authorization"))
            val (code, body) = answer(url.encodedPath.substringAfter("/api/v2/") + (url.query?.let { "?$it" } ?: ""))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return CardTrader(context, http, db)
    }

    @Test
    fun findsTheCheapestPrintingOnCardTrader() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val calls = mutableListOf<String>()
        val ct = fakeCardTrader(db, calls) { path ->
            when {
                path == "expansions" -> 200 to """[{"id": 10, "game_id": 1, "code": "m21"}, {"id": 20, "game_id": 1, "code": "2x2"}]"""
                path == "blueprints/export?expansion_id=10" -> 200 to """[{"id": 1001, "scryfall_id": "a"}]"""
                path == "blueprints/export?expansion_id=20" -> 200 to """[{"id": 2002, "scryfall_id": "b"}]"""
                // CardTrader doesn't take this language code: asked again without.
                "language=jp" in path -> 422 to "{}"
                path.startsWith("marketplace/products?blueprint_id=1001") -> 200 to
                    """{"1001": [{"price": {"cents": 350, "currency": "EUR"}, "properties_hash": {"condition": "Near Mint", "mtg_language": "jp"}, "user": {"can_sell_via_hub": true}}]}"""
                path.startsWith("marketplace/products?blueprint_id=2002") -> 200 to
                    """{"2002": [{"price": {"cents": 300, "currency": "EUR"}, "properties_hash": {"condition": "Near Mint", "mtg_language": "jp"}, "user": {"can_sell_via_hub": true}}]}"""
                else -> 404 to "{}"
            }
        }
        val printings = listOf(WishPrinting("a", "m21"), WishPrinting("b", "2x2"), WishPrinting("c", "zzz"))
        val r = ct.cheapest("secret-token", printings, false, CtFilter("jp", CtCondition.NEAR_MINT, true), eur)
        val offer = r.offer
        val blueprint = r.blueprintId
        assertEquals(3.0, offer!!.eur, 0.001)
        assertEquals(2002L, blueprint)
        assertEquals("2x2", offer.setCode)
        assertEquals("https://www.cardtrader.com/en-EU/cards/2002", CardTrader.pageUrl(blueprint!!))
        // The blueprints are kept: asking again only asks the marketplace.
        calls.clear()
        ct.cheapest("secret-token", printings.take(1), false, CtFilter("", CtCondition.NEAR_MINT, true), eur)
        assertEquals(listOf("marketplace/products?blueprint_id=1001&foil=false"), calls)
        db.close()
    }

    @Test
    fun aRefusedTokenSaysSo() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val ct = fakeCardTrader(db, mutableListOf()) { 401 to """{"error_code": "unauthorized"}""" }
        try {
            ct.cheapest("secret-token", listOf(WishPrinting("a", "m21")), false, CtFilter("en", CtCondition.ANY, false), eur)
            fail()
        } catch (e: CardTraderAuthException) {
            assertTrue("token" in e.message!!)
        }
        assertTrue(ct.check("secret-token").isFailure)
        db.close()
    }

    // --- The rules of alerts ---

    @Test
    fun notifiesOnceUntilThePriceIsBackAbove() {
        var alerted: Double? = null
        fun at(best: Double?, level: Double? = 5.0): Boolean = PriceAlerts.step(level, best, alerted).also { alerted = it.alertedLevel }.notify
        assertFalse(at(6.0))
        assertTrue(at(5.0)) // "at or below"
        assertFalse(at(4.0)) // still below: quiet
        assertFalse(at(null)) // no price now: nothing changes
        assertEquals(5.0, alerted!!, 0.0)
        assertFalse(at(5.5)) // back above: armed again
        assertNull(alerted)
        assertTrue(at(4.5))
        // A new level (here: higher, still above the price) notifies again.
        assertTrue(at(4.5, level = 6.0))
        // No level: nothing to notify, nothing remembered.
        assertFalse(at(1.0, level = null))
        assertNull(alerted)
    }

    @Test
    fun theCheapestPrintingInTheWishedFinish() {
        val cm = mapOf(
            1 to PriceEntity(1, null, 4.0, 5.0, null, null, null, null, 9.0, 12.0, null, null, null),
            2 to PriceEntity(2, null, 2.5, 6.0, null, null, null, null, 1.0, 2.0, null, null, null),
            3 to PriceEntity(3, null, 1.0, 1.5, null, null, null, null, null, null, null, null, null),
        )
        val printings = listOf(
            WishPrinting("a", "m21", cm = 1, foil = true),
            WishPrinting("b", "2x2", cm = 2, foil = true),
            // Only made in foil: doesn't count for a non-foil wish.
            WishPrinting("c", "sld", cm = 3, nonfoil = false, foil = true),
        )
        fun cheapest(foil: Boolean, type: PriceType) =
            PriceAlerts.ranked(printings) { PriceAlerts.priceOf(it, foil, PriceSource.CARDMARKET, type, cm) { _, _ -> null } }.first()
        assertEquals("b" to 2.5, cheapest(false, PriceType.LOW).let { it.first.id to it.second })
        assertEquals("a" to 5.0, cheapest(false, PriceType.TREND).let { it.first.id to it.second })
        // Foil: "c" has no foil price, so the 2X2 foil's lowest listing.
        assertEquals("b" to 1.0, cheapest(true, PriceType.LOW).let { it.first.id to it.second })
        // Another source: its price for the finish.
        val tcg = PriceAlerts.ranked(printings) {
            PriceAlerts.priceOf(it, false, PriceSource.TCGPLAYER, PriceType.LOW, cm) { id, finish -> if (finish == Finish.NONFOIL && id == "a") 3.0 else null }
        }
        assertEquals("a", tcg.first().first.id)
        assertNull(tcg.last().second)
    }

    @Test
    fun cardTraderCountsOnlyWithTheSameFilter() {
        val filter = CtFilter("en", CtCondition.SLIGHTLY_PLAYED, true)
        val row = WishPrice(1, basis = "cardmarket:low", price = 5.0, ctPrice = 4.0, ctFilter = filter.key(false))
        assertEquals(4.0 to true, PriceAlerts.best(row, "cardmarket:low", filter.key(false)))
        // CardTrader switched off, or another language chosen since: only Cardmarket's.
        assertEquals(5.0 to false, PriceAlerts.best(row, "cardmarket:low", null))
        assertEquals(5.0 to false, PriceAlerts.best(row, "cardmarket:low", filter.copy(language = "de").key(false)))
        // Settings changed to the trend price since: CardTrader's only, until the next check.
        assertEquals(4.0 to true, PriceAlerts.best(row, "cardmarket:trend", filter.key(false)))
        assertNull(PriceAlerts.best(row, "cardmarket:trend", null))
        assertEquals("Low", PriceAlerts.basisLabel("cardmarket:low"))
        assertEquals("TCGplayer", PriceAlerts.basisLabel("tcgplayer"))
        assertEquals("Cardmarket's lowest listing", PriceAlerts.basisLong("cardmarket:low"))
    }

    @Test
    fun theWishlistRowSaysWhereThePriceIs() {
        val filter = CtFilter("en", CtCondition.SLIGHTLY_PLAYED, true)
        val item = WishlistItem(id = 1, card = ref("Ragavan, Nimble Pilferer"), alertBelow = 40.0)
        val row = WishPrice(1, basis = "cardmarket:low", price = 41.5, setCode = "mh2", ctPrice = 38.5, ctFilter = filter.key(false))
        assertEquals("🔔 €40.00 ✓ · Low from €41.50 (MH2) · CT Zero from €38.50", wishPriceLine(item, row, "cardmarket:low", filter))
        // Every seller counts: "CardTrader".
        val all = filter.copy(zeroOnly = false)
        assertEquals("🔔 €40.00 ✓ · Low from €41.50 (MH2) · CardTrader from €38.50", wishPriceLine(item, row.copy(ctFilter = all.key(false)), "cardmarket:low", all))
        // No CT Zero offer among the 25 CardTrader shows: what one costs at least.
        assertEquals(
            "🔔 €40.00 · Low from €41.50 (MH2) · CT Zero over €45.00",
            wishPriceLine(item, row.copy(ctPrice = null, ctZeroOver = 45.0, ctAnyPrice = 30.0), "cardmarket:low", filter),
        )
        assertEquals("🔔 €40.00 · Low from €41.50 (MH2)", wishPriceLine(item, row, "cardmarket:low", null))
        // This printing only: no "from", no set.
        assertEquals("🔔 €40.00 · Low €41.50", wishPriceLine(item.copy(anyPrinting = false), row, "cardmarket:low", null))
        // No level, this printing only: the row's own price says it all.
        assertNull(wishPriceLine(item.copy(anyPrinting = false, alertBelow = null), row, "cardmarket:low", null))
        // Ordered: only the level.
        assertEquals("🔔 €40.00 ✓", wishPriceLine(item.copy(orderedAt = 1), row, "cardmarket:low", filter))
    }

    // --- Sync, backups and the database ---

    @Test
    fun theLevelGoesThroughSync() {
        val mine = WishlistItem(id = 1, card = ref("Sol Ring"), uid = "w1", updatedAt = 10)
        val set = mine.copy(alertBelow = 1.5, updatedAt = 20)
        val back = SyncFile.decode(SyncFile.encode(SyncFile(data = SyncData(wishlist = listOf(set)))))
        assertEquals(1.5, back.data.wishlist.single().alertBelow!!, 0.0)
        val merged = SyncMerge.merge(SyncData(wishlist = listOf(mine)), back.data, System.currentTimeMillis())
        assertEquals(1.5, merged.wishlist.single().alertBelow!!, 0.0)
        // An older version would write the file back without levels: it refuses format 5 and asks to be updated.
        assertTrue(SyncFile.FORMAT >= 5)
        assertTrue(BackupFile.FORMAT >= 5)
    }

    @Test
    fun alertSettingsGoThroughSync() {
        val s = Settings(context)
        assertEquals(PriceType.LOW, s.alertPriceType.value)
        assertFalse(s.cardTraderOn.value)
        assertEquals("en", s.ctLanguage.value)
        assertEquals(CtCondition.SLIGHTLY_PLAYED, s.ctCondition.value)
        assertTrue(s.ctZeroOnly.value)
        s.setAlertPriceType(PriceType.TREND)
        s.setCardTraderOn(true)
        s.setCtLanguage("")
        s.setCtCondition(CtCondition.NEAR_MINT)
        s.setCtZeroOnly(false)
        val prefs = s.syncedPrefs()
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        val other = Settings(context)
        other.applySyncedPrefs(prefs)
        assertEquals(PriceType.TREND, other.alertPriceType.value)
        assertTrue(other.cardTraderOn.value)
        assertEquals("", other.ctLanguage.value)
        assertEquals(CtCondition.NEAR_MINT, other.ctCondition.value)
        assertFalse(other.ctZeroOnly.value)
    }

    /** A version 15 database (app 1.35–1.36) exactly as Room made it, from the exported schema. */
    private fun createVersion15(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/15.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL(
            "INSERT INTO wishlist (id, foil, quantity, anyPrinting, notes, addedAt, ownedAtAdd, orderedAt, uid, updatedAt, scryfallId, name, setCode, setName, " +
                "collectorNumber, rarity, imageUrl, cardmarketId, fallbackEur, fallbackEurFoil, hasNonFoil, hasFoil, foilType, hasEtched, flavorName, " +
                "cardmarketFoilId) VALUES (1, 0, 2, 1, 'for the cube', 1, 0, 7, 'w1', 5, 'sr', 'Sol Ring', 'c21', 'Commander 2021', '263', 'uncommon', " +
                "NULL, 1, 1.0, NULL, 1, 0, NULL, 0, NULL, NULL)"
        )
        sql.version = 15
        sql.close()
    }

    @Test
    fun updatingFromVersion15KeepsTheWishlistWithoutAlerts() = runBlocking {
        val file = context.getDatabasePath("update-137.db").apply { parentFile?.mkdirs(); delete() }
        createVersion15(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-137.db")
            .addMigrations(AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val item = updated.wishlistDao().all().single()
            assertEquals("for the cube", item.notes)
            assertEquals(7L, item.orderedAt)
            assertNull(item.alertBelow)
            // The update doesn't count as a change: nothing to sync until the user edits something.
            assertEquals(5, item.updatedAt)
            updated.wishlistDao().update(item.copy(alertBelow = 0.5))
            val set = updated.wishlistDao().byId(1)!!
            assertEquals(0.5, set.alertBelow!!, 0.0)
            assertTrue(set.updatedAt > 5)
            // The new tables work.
            updated.wishPriceDao().put(WishPrice(1, price = 1.0))
            assertEquals(1.0, updated.wishPriceDao().get(1)!!.price!!, 0.0)
        } finally {
            updated.close()
        }
    }

    // --- The check itself ---

    private lateinit var db: AppDatabase

    @Before fun openDb() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            }).build()
        context.getSharedPreferences("settings", 0).edit().clear().commit()
    }

    @After fun closeDb() = db.close()

    private fun notifications() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun theCheckNotifiesWhenACardDropsToItsLevel() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val http = OkHttpClient()
        val scryfall = ScryfallApi(http)
        val watch = WishlistWatch(context, db, Settings(context), scryfall, PriceSourceStore(context, http, db, scryfall), CardTrader(context, http, db)) { null }
        fun price(low: Double) = runBlocking { db.priceDao().insertAll(listOf(PriceEntity(100, null, low, 9.0, null, null, null, null, null, null, null, null, null))) }
        val id = db.wishlistDao().insert(WishlistItem(card = ref("Bolas's Citadel", cm = 100), anyPrinting = false, alertBelow = 5.0))
        // No level on this one: looked at, never notified.
        db.wishlistDao().insert(WishlistItem(card = ref("Sol Ring", cm = 100), anyPrinting = false))

        price(6.0)
        watch.check(online = false)
        assertEquals(0, notifications().size)
        assertEquals(6.0, db.wishPriceDao().get(id)!!.price!!, 0.0)

        price(4.5)
        watch.check(online = false)
        assertEquals(1, notifications().size)
        val n = shadowOf(notifications().single())
        assertEquals("Bolas's Citadel: €4.50", n.contentTitle)
        assertEquals("Cardmarket's lowest listing · your level: €5.00", n.contentText)

        // Still below: quiet.
        context.getSystemService(NotificationManager::class.java).cancelAll()
        watch.check(online = false)
        assertEquals(0, notifications().size)

        // Back above, then down again: notified again.
        price(7.0)
        watch.check(online = false)
        price(4.0)
        watch.check(online = false)
        assertEquals(1, notifications().size)

        // A level set while the card is already below it: noted without a notification (you're looking at it).
        context.getSystemService(NotificationManager::class.java).cancelAll()
        val item = db.wishlistDao().byId(id)!!
        db.wishlistDao().update(item.copy(alertBelow = 4.25))
        watch.check(notify = false, only = setOf(id), online = false)
        watch.check(online = false)
        assertEquals(0, notifications().size)

        // Ordered cards stay quiet, and a card taken off the wishlist loses its prices.
        db.wishlistDao().update(db.wishlistDao().byId(id)!!.copy(alertBelow = 4.1, orderedAt = 1))
        watch.check(online = false)
        assertEquals(0, notifications().size)
        db.wishlistDao().delete(id)
        watch.check(online = false)
        assertNull(db.wishPriceDao().get(id))
    }
}
