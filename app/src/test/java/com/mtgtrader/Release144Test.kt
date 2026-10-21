package com.mtgtrader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.ColourCount
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckStats
import com.mtgtrader.data.ScryCard
import com.mtgtrader.data.ScryFace
import com.mtgtrader.data.ScryPart
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.SyncFile
import com.mtgtrader.data.SyncSchema
import com.mtgtrader.data.TokenPart
import com.mtgtrader.data.toInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.44: deck stats (mana curve, colours in the costs, mana sources) and the deck's tokens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release144Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A deck card with its Scryfall details, as the deck page reads them. */
    private fun card(
        name: String,
        typeLine: String,
        makes: String = "",
        text: String = "",
        cost: String = "",
        cmc: Double = 0.0,
        qty: Int = 1,
        commander: Boolean = false,
        tokens: List<TokenPart> = emptyList(),
    ) = StatsCard(name, name.lowercase(), qty, cmc, typeLine.substringBefore(" — "), commander, typeLine, cost, makes, text, TokenPart.encode(tokens))

    private fun counts(sources: List<com.mtgtrader.data.ColourSources>) = sources.associate { it.colour to it.count }

    // Lands from the owner's Auntie Plague (Jund), with Scryfall's own colours.
    private val swamp = card("Swamp", "Basic Land — Swamp", "B", qty = 9)
    private val mountain = card("Mountain", "Basic Land — Mountain", "R", qty = 4)
    private val forest = card("Forest", "Basic Land — Forest", "G", qty = 6)
    private val twilightMire = card("Twilight Mire", "Land", "BGC", "{T}: Add {C}.\n{B/G}, {T}: Add {B}{B}, {B}{G}, or {G}{G}.")
    private val fabled = card("Fabled Passage", "Land", "", "{T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
    private val overgrownTomb = card("Overgrown Tomb", "Land — Swamp Forest", "BG", "({T}: Add {B} or {G}.)")
    private val bloodstained = card("Bloodstained Mire", "Land", "", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Swamp or Mountain card, put it onto the battlefield, then shuffle.")
    private val commandTower = card("Command Tower", "Land", "WUBRG", "{T}: Add one mana of any color in your commander's color identity.")
    private val solRing = card("Sol Ring", "Artifact", "C", "{T}: Add {C}{C}.", "{1}", 1.0)
    private val signet = card("Arcane Signet", "Artifact", "WUBRG", "{T}: Add one mana of any color in your commander's color identity.", "{2}", 2.0)

    // ---- Mana production: sources ----------------------------------------------------------------------

    @Test
    fun eachCardCountsOncePerColourItMakes() {
        // Twilight Mire makes BB, BG or GG, but is one black and one green source (and one colourless); Sol Ring one colourless.
        val s = DeckStats.sources(listOf(twilightMire, solRing, swamp), "BRG", landsOnly = false)
        assertEquals(mapOf('B' to 10, 'G' to 1, 'C' to 2), counts(s))
        // Copies count: nine Swamps are nine sources.
        assertEquals(9, s.single { it.colour == 'B' }.cards.single { it.name == "Swamp" }.quantity)
    }

    @Test
    fun fetchLandsCountForWhatTheyCanFindInTheDeck() {
        val deck = listOf(fabled, bloodstained, swamp, mountain, forest, overgrownTomb)
        val s = DeckStats.sources(deck, "BRG", landsOnly = true)
        // Fabled Passage finds the basics: black, red and green.
        for (colour in "BRG") assertTrue(s.single { it.colour == colour }.cards.any { it.name == "Fabled Passage" })
        assertEquals(listOf("Forest"), s.single { it.colour == 'G' }.cards.single { it.name == "Fabled Passage" }.finds)
        // Bloodstained Mire finds Swamps and Mountains, Overgrown Tomb (a Swamp Forest) too, so it's a green source as well.
        val green = s.single { it.colour == 'G' }.cards.single { it.name == "Bloodstained Mire" }
        assertEquals(listOf("Overgrown Tomb"), green.finds)
        assertEquals(listOf("Mountain"), s.single { it.colour == 'R' }.cards.single { it.name == "Bloodstained Mire" }.finds)
        // Without anything to find, a fetch land is no source at all.
        assertTrue(DeckStats.sources(listOf(bloodstained, forest), "BRG", landsOnly = true).none { s2 -> s2.cards.any { it.name == "Bloodstained Mire" } })
        // A basic fetch doesn't find Overgrown Tomb.
        assertTrue(DeckStats.fetches(fabled, listOf(fabled, overgrownTomb)).isEmpty())
    }

    @Test
    fun anyColourCardsCountOnlyForTheCommandersColours() {
        assertEquals(mapOf('B' to 1, 'R' to 1, 'G' to 1), counts(DeckStats.sources(listOf(commandTower), "BRG", landsOnly = true)))
        // A deck without a commander keeps every colour.
        assertEquals(5, DeckStats.sources(listOf(commandTower), null, landsOnly = true).size)
        // A colourless commander: only colourless sources.
        assertEquals(mapOf('C' to 1), counts(DeckStats.sources(listOf(commandTower, solRing), "", landsOnly = false)))
    }

    @Test
    fun rampSpellsAndOtherPlayersSearchesDontCount() {
        val farseek = card("Farseek", "Sorcery", text = "Search your library for a Plains, Island, Swamp, or Mountain card, put it onto the battlefield tapped, then shuffle.", cost = "{1}{G}", cmc = 2.0)
        val trophy = card("Assassin's Trophy", "Instant", text = "Destroy target permanent an opponent controls. Its controller may search their library for a basic land card, put it onto the battlefield, then shuffle.", cost = "{B}{G}", cmc = 2.0)
        val s = DeckStats.sources(listOf(farseek, trophy, swamp, mountain), "BRG", landsOnly = false)
        assertTrue(s.none { c -> c.cards.any { it.name == "Farseek" || it.name == "Assassin's Trophy" } })
    }

    @Test
    fun landsOnlyAndColourlessSwitches() {
        val deck = listOf(signet, solRing, swamp, twilightMire)
        assertEquals(mapOf('B' to 11, 'R' to 1, 'G' to 2, 'C' to 2), counts(DeckStats.sources(deck, "BRG", landsOnly = false)))
        assertEquals(mapOf('B' to 10, 'G' to 1, 'C' to 1), counts(DeckStats.sources(deck, "BRG", landsOnly = true)))
        assertEquals(mapOf('B' to 10, 'G' to 1), counts(DeckStats.sources(deck, "BRG", landsOnly = true, colourless = false)))
        // Colours come in WUBRG order, colourless last.
        assertEquals(listOf('B', 'R', 'G', 'C'), DeckStats.sources(deck, "BRG", landsOnly = false).map { it.colour })
    }

    @Test
    fun doubleFacedCardsWithALandFaceAreLands() {
        val malakir = card(
            "Malakir Rebirth // Malakir Mire", "Instant // Land", "B",
            "Choose target creature. You lose 2 life. Until end of turn, that creature gains \"When this creature dies, return it to the battlefield tapped under its owner's control.\"" +
                CardInfo.FACE_BREAK + "As Malakir Mire enters, you may pay 3 life. If you don't, it enters tapped.\n{T}: Add {B}.",
            "{B} // ", 1.0,
        )
        // The land face makes it a land: a source with "lands only", and left out of the curve.
        assertEquals(mapOf('B' to 1), counts(DeckStats.sources(listOf(malakir), "BRG", landsOnly = true)))
        assertEquals(0, DeckStats.curve(listOf(malakir)).spells)
        // Its spell's cost still counts.
        assertEquals(listOf(ColourCount('B', 1)), DeckStats.costs(listOf(malakir)))
        // A spell face that searches for a land doesn't make the card a fetch land.
        val ramp = card("Search Spell // Some Land", "Sorcery // Land", "", "Search your library for a basic land card." + CardInfo.FACE_BREAK + "This land enters tapped.")
        assertTrue(DeckStats.fetches(ramp, listOf(ramp, swamp)).isEmpty())
        // A land on the back of a creature card (Archidekt's types before Scryfall's are known) too.
        assertTrue(card("Creature // Land", "Creature — Elf // Land").isLand)
        assertTrue(!card("Delver of Secrets // Insectile Aberration", "Creature — Human Wizard // Creature — Human Insect").isLand)
    }

    // ---- Mana cost and the curve -----------------------------------------------------------------------------

    @Test
    fun colouredSymbolsInTheCosts() {
        val cards = listOf(
            card("Danse Macabre", "Sorcery", cost = "{3}{B}{B}", cmc = 5.0),
            card("Kitchen Finks", "Creature — Ouphe", cost = "{1}{G/W}{G/W}", cmc = 3.0, qty = 2),
            card("Dismember", "Instant", cost = "{1}{B/P}{B/P}", cmc = 3.0),
            card("Spectral Procession", "Sorcery", cost = "{2/W}{2/W}{2/W}", cmc = 6.0),
            card("Fire // Ice", "Instant // Instant", cost = "{1}{R} // {1}{U}", cmc = 4.0),
            card("Thought-Knot Seer", "Creature — Eldrazi", cost = "{3}{C}", cmc = 4.0),
            swamp,
        )
        // Hybrid counts for both colours, Phyrexian for its colour, generic not at all; copies count.
        assertEquals(
            listOf(ColourCount('W', 7), ColourCount('U', 1), ColourCount('B', 4), ColourCount('R', 1), ColourCount('G', 4), ColourCount('C', 1)),
            DeckStats.costs(cards),
        )
    }

    @Test
    fun theCurveLeavesLandsOut() {
        val cards = listOf(
            card("Sol Ring", "Artifact", cmc = 1.0),
            card("Cultivate", "Sorcery", cmc = 3.0, qty = 2),
            card("Auntie Ool", "Legendary Creature — Faerie", cmc = 5.0, commander = true),
            card("Craterhoof Behemoth", "Creature — Beast", cmc = 8.0),
            card("Ancestral Recall", "Instant", cmc = 0.0, qty = 0),
            swamp,
        )
        val curve = DeckStats.curve(cards)
        assertEquals(listOf(0, 1, 0, 2, 0, 1, 0, 1), curve.counts)
        assertEquals(5, curve.spells)
        assertEquals((1 + 3 + 3 + 5 + 8) / 5.0, curve.average, 1e-9)
    }

    // ---- Scryfall's details --------------------------------------------------------------------------------------

    @Test
    fun scryfallsDetailsAreKeptForTheStats() {
        val mire = ScryCard(id = "tm", name = "Twilight Mire", set = "shm", typeLine = "Land", oracleText = "{T}: Add {C}.", producedMana = listOf("B", "C", "G"))
        assertEquals("BGC", mire.toInfo().producedMana)
        // None (a fetch land) is known as none, so it isn't asked for again.
        assertEquals("", ScryCard(id = "fp", name = "Fabled Passage", set = "w17").toInfo().producedMana)
        // Double-faced cards keep each face's rules text, in order.
        val malakir = ScryCard(
            id = "mr", name = "Malakir Rebirth // Malakir Mire", set = "znr", typeLine = "Instant // Land", producedMana = listOf("B"),
            cardFaces = listOf(ScryFace(name = "Malakir Rebirth", oracleText = "Choose target creature."), ScryFace(name = "Malakir Mire", oracleText = "{T}: Add {B}.")),
        )
        assertEquals(listOf("Choose target creature.", "{T}: Add {B}."), malakir.toInfo().oracleText!!.split(CardInfo.FACE_BREAK))
        // Tokens and helper cards (The Monarch), not the card itself or other linked cards.
        val marchesa = ScryCard(
            id = "qm", name = "Queen Marchesa", set = "cn2", power = "3", toughness = "3",
            allParts = listOf(
                ScryPart("as", "token", "Assassin", "Token Creature — Assassin"),
                ScryPart("mo", "combo_piece", "The Monarch", "Card"),
                ScryPart("qm", "combo_piece", "Queen Marchesa", "Legendary Creature — Human Assassin"),
                ScryPart("xx", "combo_piece", "Some Combo Card", "Creature — Elf"),
            ),
        )
        assertEquals(listOf("Assassin", "The Monarch"), TokenPart.decode(marchesa.toInfo().tokens).map { it.name })
        assertEquals("3/3", marchesa.toInfo().powerToughness)
        assertNull(ScryCard(id = "s", name = "Swamp", set = "fdn").toInfo().tokens)
    }

    @Test
    fun tokensAreGroupedAcrossPrintings() {
        val treasureA = TokenPart("t1", "Treasure", "Token Artifact — Treasure")
        val treasureB = TokenPart("t2", "Treasure", "Token Artifact — Treasure")
        val insect = TokenPart("i1", "Insect", "Token Creature — Insect")
        val cards = listOf(
            card("Scuzzback Scrounger", "Creature — Goblin Warrior", tokens = listOf(treasureA)),
            card("Village Pillagers", "Creature — Human Rogue", tokens = listOf(treasureB)),
            card("Nest of Scarabs", "Enchantment", tokens = listOf(insect)),
        )
        val details = mapOf(
            "t1" to CardInfo("t1", "treasure-oracle", "", "", "Token Artifact — Treasure", 0.0, null),
            "t2" to CardInfo("t2", "treasure-oracle", "", "", "Token Artifact — Treasure", 0.0, null),
            "i1" to CardInfo("i1", "insect-oracle", "B", "", "Token Creature — Insect", 0.0, null, powerToughness = "1/1"),
        )
        val tokens = DeckStats.tokens(DeckStats.tokenParts(cards), details)
        assertEquals(listOf("Insect 1/1", "Treasure"), tokens.map { it.label })
        assertEquals(listOf("Scuzzback Scrounger", "Village Pillagers"), tokens[1].makers)
        assertEquals("treasure-oracle", tokens[1].oracleId)
        // Before the tokens' own details are known, the same name and type still go together.
        assertEquals(2, DeckStats.tokens(DeckStats.tokenParts(cards), emptyMap()).size)
    }

    // ---- Filling the details in ---------------------------------------------------------------------------------

    private fun ref(name: String, id: String = name.lowercase().replace(' ', '-')) =
        CardRef(id, name, "m21", "M21", "1", "rare", null, null, 1.0, 2.0, true, true)

    @Test
    fun decksCardsAreLookedUpAgainForTheStats() = runBlocking {
        db.deckDao().upsert(Deck(7, "Auntie Plague", "joelvdc", "Auntie Ool", null, null, "BRG", 2))
        db.deckDao().insertCards(listOf(
            DeckCard(deckId = 7, card = ref("Sol Ring"), quantity = 1, category = "Ramp", types = "Artifact", cmc = 1.0, commander = false, foil = false),
            DeckCard(deckId = 7, card = ref("Swamp"), quantity = 30, category = "Lands", types = "Land", cmc = 0.0, commander = false, foil = false),
            DeckCard(deckId = 7, card = ref("The Ozolith"), quantity = 1, category = "Maybeboard", types = "Artifact", cmc = 1.0, commander = false, foil = false, maybeboard = true),
        ))
        // Saved by 1.43 (a cost, but not the mana made): the deck's cards are asked for again, other cards aren't.
        db.cardInfoDao().putAll(listOf(
            CardInfo("sol-ring", null, "", "", "Artifact", 1.0, null, manaCost = "{1}"),
            CardInfo("swamp", null, "", "", "Basic Land — Swamp", 0.0, null, manaCost = "", producedMana = "B"),
            CardInfo("not-in-a-deck", null, "", "", "Sorcery", 1.0, null, manaCost = "{B}"),
        ))
        assertEquals(listOf("sol-ring", "the-ozolith"), db.cardInfoDao().missing().sorted())
        // The stats read the deck's own cards with their details; the maybeboard is left out.
        val stats = db.cardInfoDao().observeDeckStats(7).first()
        assertEquals(setOf("Sol Ring", "Swamp"), stats.map { it.name }.toSet())
        val swamp = stats.single { it.name == "Swamp" }
        assertEquals(30, swamp.quantity)
        assertTrue(swamp.isLand && swamp.isBasic)
        assertNull(stats.single { it.name == "Sol Ring" }.producedMana)
    }

    // ---- The update ------------------------------------------------------------------------------------------

    /** A version 20 database (app 1.43) exactly as Room made it, from the exported schema. */
    private fun createVersion20(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/com.mtgtrader.data.AppDatabase/20.json").readText()).jsonObject.getValue("database").jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (e in schema.getValue("entities").jsonArray) {
            val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
            sql.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e.jsonObject["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        for (q in schema.getValue("setupQueries").jsonArray) sql.execSQL(q.jsonPrimitive.content)
        sql.execSQL("INSERT INTO card_info (scryfallId, oracleId, colors, colorIdentity, typeLine, cmc, edhrecRank, fetchedAt, manaCost) VALUES ('sr', NULL, '', '', 'Artifact', 1.0, 1, 5, '{1}')")
        sql.version = 20
        sql.close()
    }

    @Test
    fun updatingFromVersion20KeepsTheCardDetails() = runBlocking {
        val file = context.getDatabasePath("update-144.db").apply { parentFile?.mkdirs(); delete() }
        createVersion20(file)
        // Room checks the updated database against what this version expects, and refuses it if it differs.
        val updated = Room.databaseBuilder(context, AppDatabase::class.java, "update-144.db")
            .addMigrations(AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24, AppDatabase.MIGRATION_24_25)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) = SyncSchema.install(db)
            })
            .build()
        try {
            val sr = updated.cardInfoDao().getMany(listOf("sr")).single()
            assertEquals("{1}", sr.manaCost)
            assertNull(sr.producedMana)
            assertNull(sr.tokens)
        } finally {
            updated.close()
        }
        // The card details aren't synced: the sync file stays as 1.43 wrote it, so the phones keep syncing.
        assertTrue(SyncFile.FORMAT >= 8)
    }
}
