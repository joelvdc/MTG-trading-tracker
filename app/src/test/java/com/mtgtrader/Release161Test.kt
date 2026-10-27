package com.mtgtrader

import com.mtgtrader.data.ManaOddsCalc
import com.mtgtrader.data.PriceSourceStore
import com.mtgtrader.data.StatsCard
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinOddsCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 1.61: the collection opens while Card Kingdom's list downloads; the win turn's sacrifice payoffs (Marionette Master,
 * Mirkwood Bats), Impact Tremors, populate, tokens one by one, the city's blessing and Urza, Prince of Kroog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release161Test {
    private fun card(name: String, type: String, cost: String, text: String = "", pt: String? = null, quantity: Int = 1, commander: Boolean = false, produced: String = "") =
        StatsCard(name, name, quantity, ManaOddsCalc.cost(cost).manaValue.toDouble(), type, commander, type, cost, produced, text, null, pt, null)

    private val plains = card("Plains", "Basic Land — Plains", "", "({T}: Add {W}.)", produced = "W", quantity = 19)
    private val island = card("Island", "Basic Land — Island", "", "({T}: Add {U}.)", produced = "U", quantity = 10)
    private val swamp = card("Swamp", "Basic Land — Swamp", "", "({T}: Add {B}.)", produced = "B", quantity = 10)
    private val leader = card("Leader", "Legendary Creature — Human", "{8}{W}{U}{B}", "", "0/1", commander = true)
    private fun bears(n: Int) = card("Bear", "Creature — Bear", "{1}{W}", "", "2/2", quantity = n)

    private val master = card(
        "Marionette Master", "Creature — Human Artificer", "{4}{B}{B}",
        "Fabricate 3 (When this creature enters, put three +1/+1 counters on it or create three 1/1 colorless Servo artifact creature tokens.)\n" +
            "Whenever an artifact you control is put into a graveyard from the battlefield, target opponent loses life equal to this creature's power.",
        "1/3", quantity = 10,
    )
    private val plainMaster = card("Plain Master", "Creature — Human Artificer", "{4}{B}{B}", "Fabricate 3", "1/3", quantity = 10)
    private val whirler = card(
        "Whirler Rogue", "Creature — Human Rogue Artificer", "{2}{U}{U}",
        "When this creature enters, create two 1/1 colorless Thopter artifact creature tokens with flying.\nTap two untapped artifacts you control: Target creature can't be blocked this turn.",
        "2/2", quantity = 10,
    )

    private fun odds(cards: List<StatsCard>, commander: StatsCard = leader, games: Int = 1000): WinOdds =
        WinOddsCalc.calculate(listOf(commander, plains, island, swamp) + cards, "WUB", emptyList(), games = games)!!

    private fun classify(c: StatsCard) = WinOddsCalc.classify(c, setOf('W', 'U', 'B'), emptyList(), emptySet())

    @Test fun marionetteMasterDrainsWithTheTreasuresLeftOver() {
        val treasurer = card("Treasurer", "Creature — Goblin", "{2}{W}", "When this creature enters, create a Treasure token.", "2/2", quantity = 20)
        val with = odds(listOf(master, treasurer, bears(21)))
        val without = odds(listOf(plainMaster, treasurer, bears(21)))
        // Before 1.61 Treasures were only sacrificed when their mana was needed: 1% of the damage.
        assertTrue("${with.damage}", (with.damage[WinOddsCalc.DRAIN] ?: 0) >= 5)
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn[9] > without.byTurn[9])
    }

    @Test fun foodIsCrackedForMarionetteMasterAndNukaColaMakesTreasures() {
        val foodie = card("Foodie", "Creature — Human", "{2}{W}", "When this creature enters, create a Food token.", "2/2", quantity = 20)
        assertEquals(0, odds(listOf(plainMaster, foodie, bears(21))).damage[WinOddsCalc.DRAIN] ?: 0)
        assertTrue((odds(listOf(master, foodie, bears(21))).damage[WinOddsCalc.DRAIN] ?: 0) > 0)
        val nuka = card(
            "Nuka-Cola Vending Machine", "Artifact", "{3}",
            "{1}, {T}: Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\n" +
                "Whenever you sacrifice a Food, create a tapped Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
        )
        assertTrue(classify(nuka).foodTreasure)
    }

    @Test fun tawnosSacrificingTwoArtifactTokensSetsOffMarionetteMaster() {
        val tawnosText = "{2}, {T}: Create a token that's a copy of up to one target artifact token you control. Mill two cards.\n" +
            "{1}{W}{U}{B}, {T}, Sacrifice two artifact tokens, Exile an artifact or creature card from your graveyard: Create a token that's a copy of the exiled card, except it's an artifact in addition to its other types. Activate only as a sorcery."
        val tawnos = card("Tawnos, Solemn Survivor", "Legendary Creature — Human Artificer", "{1}{U}", tawnosText, "1/3", commander = true)
        // Without its second ability nothing here puts an artifact into the graveyard: Thopters only attack.
        val copyOnly = card("Tawnos, Solemn Survivor", "Legendary Creature — Human Artificer", "{1}{U}", tawnosText.substringBefore('\n'), "1/3", commander = true)
        val deck = listOf(master, whirler, bears(21))
        assertEquals(0, odds(deck, copyOnly).damage[WinOddsCalc.DRAIN] ?: 0)
        assertTrue("${odds(deck, tawnos).damage}", (odds(deck, tawnos).damage[WinOddsCalc.DRAIN] ?: 0) > 0)
    }

    @Test fun tawnosCopiesAreArtifacts() {
        val tawnos = card(
            "Tawnos, Solemn Survivor", "Legendary Creature — Human Artificer", "{1}{U}",
            "{2}, {T}: Create a token that's a copy of up to one target artifact token you control. Mill two cards.\n" +
                "{1}{W}{U}{B}, {T}, Sacrifice two artifact tokens, Exile an artifact or creature card from your graveyard: Create a token that's a copy of the exiled card, except it's an artifact in addition to its other types. Activate only as a sorcery.",
            "1/3", commander = true,
        )
        val treasurer = card("Treasurer", "Creature — Goblin", "{2}{W}", "When this creature enters, create a Treasure token.", "2/2", quantity = 20)
        // Only Tawnos brings it in: milled, then copied as an artifact.
        val huge = card("Huge", "Creature — Giant", "{20}", "", "20/20", quantity = 15)
        val squadron = card("Cybermen Squadron", "Artifact Creature — Cyberman", "{7}", "Nonlegendary artifact creatures you control have myriad.", "5/5", quantity = 6)
        val ownMyriad = card("Squadron", "Artifact Creature — Cyberman", "{7}", "Myriad", "5/5", quantity = 6)
        val with = odds(listOf(treasurer, huge, squadron, bears(18)), tawnos)
        val without = odds(listOf(treasurer, huge, ownMyriad, bears(18)), tawnos)
        // The Giant copy is an artifact, so Cybermen Squadron gives it myriad.
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn[9] > without.byTurn[9])
    }

    @Test fun saiSacrificesSpareArtifactsAndWorldwalkerHelmMakesMaps() {
        val sai = classify(
            card(
                "Sai, Master Thopterist", "Legendary Creature — Human Artificer", "{2}{U}",
                "Whenever you cast an artifact spell, create a 1/1 colorless Thopter artifact creature token with flying.\n{1}{U}, Sacrifice two artifacts: Draw a card.", "1/4",
            ),
        )
        assertEquals(2, sai.sacArtifactsDraw?.second)
        assertEquals(2, sai.sacArtifactsDraw?.first?.manaValue)
        val helm = card(
            "Worldwalker Helm", "Artifact", "{2}{U}",
            "If you would create one or more artifact tokens, instead create those tokens plus an additional Map token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Target creature you control explores. Activate only as a sorcery.\")\n" +
                "{1}{U}, {T}: Create a token that's a copy of target artifact token you control.",
            quantity = 10,
        )
        assertTrue(classify(helm).extraMap)
        // Maps from Whirler Rogue's Thopters, sacrificed for Marionette Master.
        assertTrue((odds(listOf(master, whirler, helm, bears(11))).damage[WinOddsCalc.DRAIN] ?: 0) > 0)
    }

    @Test fun mirkwoodBatsCountsEachThopter() {
        val w = classify(whirler)
        assertEquals(2, w.tokensOnceCount)
        assertEquals(2, w.tokensOnce)
        val bats = card("Mirkwood Bats", "Creature — Bat", "{3}{W}", "Flying\nWhenever you create or sacrifice a token, each opponent loses 1 life.", "2/3", quantity = 10)
        val plainBats = card("Plain Bats", "Creature — Bat", "{3}{W}", "Flying", "2/3", quantity = 10)
        val with = odds(listOf(whirler, bats, bears(21)))
        assertTrue("${with.damage}", (with.damage[WinOddsCalc.DRAIN] ?: 0) > 0)
        assertTrue(with.byTurn[10] > odds(listOf(whirler, plainBats, bears(21))).byTurn[10])
    }

    @Test fun impactTremorsDealsDamageWhenCreaturesEnter() {
        val tremors = card("Impact Tremors", "Enchantment", "{1}{W}", "Whenever a creature you control enters, this enchantment deals 1 damage to each opponent.", quantity = 10)
        val t = classify(tremors)
        assertEquals(1, t.enterTriggers.size)
        assertEquals(false, t.enterTriggers[0].another)
        val odds = odds(listOf(tremors, bears(41)))
        assertTrue("${odds.damage}", (odds.damage[WinOddsCalc.TRIGGERS] ?: 0) > 0)
        // "Whenever a creature you control enters, draw a card" is read as before.
        assertEquals(1, classify(card("Drawer", "Enchantment", "{2}{W}", "Whenever a creature you control enters, draw a card.")).drawTurn)
    }

    @Test fun nestingDovehawkPopulatesAndGrows() {
        val text = "Flying\nAt the beginning of combat on your turn, populate. (Create a token that's a copy of a creature token you control.)\n" +
            "Whenever a creature token you control enters, put a +1/+1 counter on this creature."
        val dovehawk = card("Nesting Dovehawk", "Creature — Bird", "{3}{W}", text, "2/2", quantity = 10)
        val d = classify(dovehawk)
        assertEquals(1, d.eachTurn.populate)
        assertTrue(d.tokenEnterCounter)
        val plain = card("Plain Bird", "Creature — Bird", "{3}{W}", "Flying", "2/2", quantity = 10)
        val with = odds(listOf(whirler, dovehawk, bears(21)))
        val without = odds(listOf(whirler, plain, bears(21)))
        assertTrue("${with.byTurn} vs ${without.byTurn}", with.byTurn[10] > without.byTurn[10])
    }

    @Test fun wanderglyphNeedsTheCitysBlessing() {
        val wanderglyph = card(
            "Illustrious Wanderglyph", "Artifact Creature — Golem", "{4}{W}",
            "Ascend (If you control ten or more permanents, you get the city's blessing for the rest of the game.)\n" +
                "Other artifact creatures you control get +2/+2 as long as you have the city's blessing.\n" +
                "At the beginning of each upkeep, create a 1/1 colorless Gnome artifact creature token.",
            "2/2", quantity = 10,
        )
        val w = classify(wanderglyph)
        assertTrue(w.lords.single().ascend)
        assertEquals(1, odds(listOf(wanderglyph, bears(31))).counted.tokenMakers)
    }

    @Test fun urzaPumpsArtifactsWithoutPowerAndCopiesArtifacts() {
        val urza = card(
            "Urza, Prince of Kroog", "Legendary Creature — Human Artificer", "{2}{W}{U}",
            "Artifact creatures you control get +2/+2.\n{6}: Create a token that's a copy of target artifact you control, except it's a 1/1 Soldier creature in addition to its other types.",
            "2/3",
        )
        val u = classify(urza)
        assertTrue(u.activated.single().copyArtifact)
        val ornithopter = card("Ornithopter", "Artifact Creature — Thopter", "{0}", "Flying", "0/2", quantity = 60)
        // Before 1.61 the 0/2s were never cast: the deck never won.
        val odds = odds(listOf(ornithopter), commander = urza.copy(commander = true))
        assertTrue("${odds.byTurn}", odds.byTurn.last() > 50)
    }

    @Test fun theCollectionOpensWhileCardKingdomDownloads() = runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.mtgtrader.data.AppDatabase::class.java).build()
        context.getSharedPreferences("price_sources", 0).edit().clear().commit()
        val body = javaClass.getResourceAsStream("/cardkingdom/pricelist-copy.tsv.gz")!!.readBytes()
        val halfSent = CountDownLatch(1)
        val goOn = CountDownLatch(1)
        val server = java.net.ServerSocket(0)
        Thread {
            server.accept().use { s ->
                val reader = s.getInputStream().bufferedReader()
                while (true) if ((reader.readLine() ?: break).isEmpty()) break
                val out = s.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                out.write(body, 0, body.size / 2)
                out.flush()
                halfSent.countDown()
                goOn.await(10, TimeUnit.SECONDS)
                out.write(body, body.size / 2, body.size - body.size / 2)
                out.flush()
            }
        }.start()
        try {
            val http = okhttp3.OkHttpClient()
            val store = PriceSourceStore(
                context, http, db, com.mtgtrader.data.ScryfallApi(http),
                cardKingdomUrl = "http://127.0.0.1:1/refused", cardKingdomCopyUrl = "http://127.0.0.1:${server.localPort}/list",
            )
            val refresh = async(Dispatchers.IO) { store.refresh() }
            assertTrue(halfSent.await(10, TimeUnit.SECONDS))
            // Halfway through the download the collection can be read (before 1.61 it waited for the whole list).
            val rows = withContext(Dispatchers.IO) { withTimeout(3000) { db.collectionDao().observeAll().first() } }
            assertNotNull(rows)
            goOn.countDown()
            refresh.await()
            assertEquals(1203, db.sourcePriceDao().count("cardkingdom"))
        } finally {
            goOn.countDown()
            server.close()
            db.close()
        }
    }
}
