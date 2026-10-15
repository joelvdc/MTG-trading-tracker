package com.mtgtrader

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CardmarketCatalog
import com.mtgtrader.data.CmProduct
import com.mtgtrader.data.CollectionItem
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.PendingCard
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.deckCoverUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/** 1.34: deck and wishlist pictures saved for offline use, and adding to the wishlist offline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release134Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase

    /** Scryfall's answer to a name lookup; null means offline. */
    private var scryfallAnswer: String? = null
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val body = scryfallAnswer ?: throw IOException("offline")
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    private lateinit var repo: MtgRepository
    private lateinit var catalog: CardmarketCatalog

    private fun ref(id: String, name: String, image: String?) = CardRef(id, name, "ice", "Ice Age", "61", "common", image, 1, 1.0, null, true, false)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        val scryfall = ScryfallApi(http)
        repo = MtgRepository(db, scryfall, PriceGuideRepository(context, http, db, Settings(context)), CoroutineScope(Dispatchers.Unconfined))
        catalog = CardmarketCatalog(context, http, db, Settings(context), scryfall)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun offlineNamesComeFromCardmarketsList() = runBlocking {
        db.catalogDao().insertAll(
            listOf(
                CmProduct(1, "Delver of Secrets / Insectile Aberration", 10),
                CmProduct(2, "Art Series: Delver of Secrets", 11),
                CmProduct(3, "Dungeon Delver", 12),
                CmProduct(4, "Lightning Bolt", 13),
                CmProduct(5, "Lightning Bolt", 14),
                CmProduct(6, "Fire // Ice", 15),
            ),
        )
        // Names starting with what was typed first, in Scryfall's spelling; no art cards.
        assertEquals(listOf("Delver of Secrets // Insectile Aberration", "Dungeon Delver"), catalog.offlineNames("delver"))
        assertEquals(listOf("Lightning Bolt"), catalog.offlineNames("Lightning"))
        assertEquals(listOf("Fire // Ice"), catalog.offlineNames("fire /"))
        // Typed wildcards are plain characters.
        assertEquals(emptyList<String>(), catalog.offlineNames("%"))
        assertEquals(emptyList<String>(), catalog.offlineNames("_"))
    }

    @Test
    fun cardsAddedOfflineGetTheirDetailsOnceOnline() = runBlocking {
        db.collectionDao().insert(CollectionItem(card = ref("old", "Brainstorm", null), foil = false, quantity = 2))
        scryfallAnswer = null
        repo.addPendingToWishlist("Brainstorm")
        repo.addPendingToWishlist("Brainstorm")
        val pending = db.wishlistDao().all().single()
        assertTrue(pending.card.isPending)
        assertEquals(2, pending.quantity)
        assertEquals(2, pending.ownedAtAdd)
        assertTrue(pending.anyPrinting)
        assertEquals("Details follow when you're online", pending.card.setLabel)
        assertNull(pending.card.imageUrl)
        // Its placeholder has Scryfall's id format but isn't looked up for details.
        assertEquals(36, pending.card.scryfallId.length)
        assertEquals(listOf("old"), db.cardInfoDao().missing())

        // Still offline: nothing changes.
        assertEquals(0, repo.completePendingWishlist())
        assertTrue(db.wishlistDao().all().single().card.isPending)

        scryfallAnswer = """{"id":"bs-new","name":"Brainstorm","set":"mh3","set_name":"Modern Horizons 3","collector_number":"1",
            "rarity":"common","cardmarket_id":777,"finishes":["nonfoil","foil"],
            "image_uris":{"normal":"https://cards.scryfall.io/normal/front/b/s/bs-new.jpg"},"prices":{"eur":"0.50"}}"""
        assertEquals(1, repo.completePendingWishlist())
        val filled = db.wishlistDao().all().single()
        assertFalse(filled.card.isPending)
        assertEquals("bs-new", filled.card.scryfallId)
        assertEquals("https://cards.scryfall.io/normal/front/b/s/bs-new.jpg", filled.card.imageUrl)
        assertEquals(777, filled.card.cardmarketId)
        assertEquals(pending.uid, filled.uid)
        assertEquals(2, filled.quantity)
        assertEquals(2, filled.ownedAtAdd)
        assertEquals(0, repo.completePendingWishlist())
    }

    @Test
    fun deckAndWishlistPicturesAreSavedAhead() = runBlocking {
        db.deckDao().upsert(Deck(1, "Elves", "me", "Lathril", "abcdef", null, "BG", 100))
        db.deckDao().upsert(Deck(2, "Faeries", "me", "Alela", null, "https://storage.googleapis.com/archidekt-card-images/x_art_crop.jpg", "WUB", 100))
        db.deckDao().insertCards(
            listOf(DeckCard(deckId = 1, card = ref("a", "Llanowar Elves", "https://img/a.jpg"), quantity = 1, category = "Ramp", types = "Creature", cmc = 1.0, commander = false, foil = false)),
        )
        // Your own printing of a deck card (decks show those), and a card in no deck.
        db.collectionDao().insert(CollectionItem(card = ref("a2", "llanowar elves", "https://img/a2.jpg"), foil = false, quantity = 1))
        db.collectionDao().insert(CollectionItem(card = ref("z", "Island", "https://img/z.jpg"), foil = false, quantity = 1))
        repo.addToWishlist(ref("w", "Sol Ring", "https://img/w.jpg"))
        repo.addPendingToWishlist("Mana Crypt")

        val dao = db.offlineImagesDao()
        assertEquals(setOf("https://img/a.jpg", "https://img/a2.jpg", "https://img/w.jpg"), dao.observeCardImages().first().toSet())
        assertEquals(
            setOf("https://cards.scryfall.io/art_crop/front/a/b/abcdef.jpg", "https://storage.googleapis.com/archidekt-card-images/x_art_crop.jpg"),
            dao.observeCovers().first().mapNotNull { deckCoverUrl(it.artUrl, it.commanderScryfallId) }.toSet(),
        )
        assertNull(deckCoverUrl(null, null))
    }

    @Test
    fun cardmarketDoubleFacedNamesUseScryfallsSpelling() {
        assertEquals("Delver of Secrets // Insectile Aberration", PendingCard.cardName("Delver of Secrets / Insectile Aberration"))
        assertEquals("Fire // Ice", PendingCard.cardName("Fire // Ice"))
        assertEquals("Lightning Bolt", PendingCard.cardName("Lightning Bolt"))
    }
}
