package com.mtgtrader

import com.mtgtrader.data.CollectionSortPlanner
import com.mtgtrader.data.DisplayCurrency
import com.mtgtrader.data.Money
import com.mtgtrader.data.Settings
import com.mtgtrader.data.SortMove
import com.mtgtrader.data.SortPlace
import com.mtgtrader.data.SortRules
import com.mtgtrader.data.SortStack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 1.104: sorting the collection into three binders, against the cases shared with the website (`web/test/collsort.test.ts`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Release1104SortTest {
    @Serializable
    private class Stack(
        val key: String, val name: String, val place: String, val copies: Int,
        val price: Double? = null, val rank: Int? = null, val fixed: Boolean = false,
    )

    @Serializable
    private class Rules(
        val maxTop: Int = 200, val maxMid: Int = 1_000, val maxBulk: Int = 0,
        val floor: Double = 0.5, val mix: Int = 50, val weekly: Int = 10,
    ) {
        fun toRules() = SortRules(maxTop, maxMid, maxBulk, floor, mix, weekly)
    }

    @Serializable
    private class Input(
        val stacks: List<Stack>,
        val deckNeeds: Map<String, Int> = emptyMap(),
        val rules: Rules = Rules(),
        val skips: List<String> = emptyList(),
        val log: List<String> = emptyList(),
        val settled: Boolean = false,
        val today: Long,
    )

    @Serializable
    private class Move(
        val key: String, val name: String, val from: String, val to: String, val copies: Int,
        val price: Double? = null, val score: Double, val reason: String,
    )

    @Serializable
    private class Expected(
        val moves: List<Move>,
        val after: Map<String, Int>,
        val heldBack: Int,
        val weekUsed: Int,
        val budget: Int,
    )

    @Serializable
    private class Case(val name: String, val input: Input, val expected: Expected)

    private val cases = Json { ignoreUnknownKeys = true }
        .decodeFromString<List<Case>>(File("../web/test/fixtures/collsort-cases.json").readText())

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        // Money as the tests show it: euros, written the way the phone writes them ("€12.50").
        Money.display = DisplayCurrency()
    }

    private fun plan(input: Input) = CollectionSortPlanner.plan(
        input.stacks.map { SortStack(it.key, it.name, it.place, it.copies, it.price, it.rank, it.fixed) },
        input.deckNeeds,
        input.rules.toRules(),
        input.skips.toSet(),
        input.log,
        input.settled,
        input.today,
    )

    @Test
    fun thereAreEnoughCases() {
        assertTrue(cases.size >= 12)
    }

    @Test
    fun sortsLikeTheSharedCases() {
        for (c in cases) {
            val got = plan(c.input)
            fun about(what: String) = "${c.name}: $what"
            assertEquals(about("moves"), c.expected.moves.size, got.moves.size)
            c.expected.moves.forEach { want ->
                val move = got.moves.single { it.key == want.key && it.from == want.from && it.to == want.to }
                assertEquals(about("${want.key} name"), want.name, move.name)
                assertEquals(about("${want.key} copies"), want.copies, move.copies)
                assertEquals(about("${want.key} price"), want.price, move.price)
                assertEquals(about("${want.key} score"), want.score, move.score, SCORE)
                assertEquals(about("${want.key} reason"), want.reason, move.reason)
            }
            assertEquals(about("order"), c.expected.moves.map { it.line() }, got.moves.map { it.line() })
            assertEquals(about("after"), c.expected.after, got.after)
            assertEquals(about("heldBack"), c.expected.heldBack, got.heldBack)
            assertEquals(about("weekUsed"), c.expected.weekUsed, got.weekUsed)
            assertEquals(about("budget"), c.expected.budget, got.budget)
        }
    }

    @Test
    fun wordsTheReasonsAsTheCasesExpect() {
        for (c in cases) for (m in c.expected.moves) assertTrue("${c.name}: ${m.reason}", m.reason.isNotEmpty())
    }

    @Test
    fun theSortSettingsRoundTripThroughSync() {
        val s = Settings(context)
        assertEquals(SortRules(), s.sortRules)
        assertTrue(!s.sortSettled)
        assertTrue(s.sortSkips.isEmpty())
        assertTrue(s.sortLog.isEmpty())
        val rules = SortRules(maxTop = 50, maxMid = 300, maxBulk = 20, floor = 1.25, mix = 80, weekly = 3)
        s.sortRules = rules
        s.sortSettled = true
        s.sortSkips = setOf("abc|false|false|NM|en>mid", "def|false|false|NM|en>bulk")
        s.sortLog = listOf("19990|abc|2|0", "19999|def|1|1")
        s.setSortBinderFromSync(SortPlace.TOP, 7)

        val prefs = s.syncedPrefs()
        for (key in listOf("sortMaxTop", "sortMaxMid", "sortMaxBulk", "sortFloor", "sortMix", "sortWeekly", "sortSettled", "sortSkips", "sortLog")) {
            assertTrue(key, key in prefs.values)
        }
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        val other = Settings(context)
        other.applySyncedPrefs(prefs)
        assertEquals(rules, other.sortRules)
        assertTrue(other.sortSettled)
        assertEquals(s.sortSkips, other.sortSkips)
        assertEquals(s.sortLog, other.sortLog)

        // Numbers out of range are left out, like the trade binder's: only the accepted ones come through.
        val bad = prefs.copy(values = prefs.values + mapOf("sortMix" to "200", "sortWeekly" to "0", "sortFloor" to "-1", "sortMaxMid" to "x"))
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        val fresh = Settings(context)
        fresh.applySyncedPrefs(bad)
        assertEquals(SortRules(maxTop = 50, maxMid = SortRules().maxMid, maxBulk = 20, floor = SortRules().floor, mix = SortRules().mix, weekly = SortRules().weekly), fresh.sortRules)
    }

    private fun SortMove.line() = "$from>$to:$key:$copies"

    private fun Move.line() = "$from>$to:$key:$copies"

    private companion object {
        const val SCORE = 1e-9
    }
}
