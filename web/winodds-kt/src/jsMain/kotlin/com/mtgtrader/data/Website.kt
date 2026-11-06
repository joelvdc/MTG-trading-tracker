@file:OptIn(ExperimentalJsExport::class)

package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Java's Math.round, which the app's code calls (half up). */
internal object Math {
    fun round(x: Double): Long = kotlin.math.floor(x + 0.5).toLong()
}

/** Java's Integer.bitCount. */
internal object Integer {
    fun bitCount(i: Int): Int = i.countOneBits()
}

/** Java's sorted set (a TreeSet on the phone): kept in order. */
internal fun <T : Comparable<T>> sortedSetOf(): MutableSet<T> = SortedSet()

private class SortedSet<T : Comparable<T>> : AbstractMutableSet<T>() {
    private val items = ArrayList<T>()
    override val size get() = items.size
    override fun iterator(): MutableIterator<T> = items.iterator()
    override fun add(element: T): Boolean {
        val i = items.binarySearch(element)
        if (i >= 0) return false
        items.add(-i - 1, element)
        return true
    }
}

/** The app's TokenPart (CardInfo.kt), for the deck stats. */
@Serializable
data class TokenPart(val id: String, val name: String, val typeLine: String) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(s: String?): List<TokenPart> = s?.let { runCatching { json.decodeFromString<List<TokenPart>>(it) }.getOrNull() }.orEmpty()
    }
}

/** The one part of CardInfo the deck stats need. */
internal object CardInfo {
    const val FACE_BREAK = "\n//\n"
}

/** A deck card as the website sends it: the app's [StatsCard]. */
@Serializable
internal class WebCard(
    val name: String,
    val scryfallId: String,
    val quantity: Int,
    val cmc: Double,
    val types: String,
    val commander: Boolean,
    val typeLine: String? = null,
    val manaCost: String? = null,
    val producedMana: String? = null,
    val oracleText: String? = null,
    val tokens: String? = null,
    val powerToughness: String? = null,
    val loyalty: String? = null,
) {
    fun stats() = StatsCard(name, scryfallId, quantity, cmc, types, commander, typeLine, manaCost, producedMana, oracleText, tokens, powerToughness, loyalty)
}

/** What the deck page shows, worked out by the app's own code. */
@Serializable
internal class WebWinOdds(
    val odds: WinOdds,
    val label: String,
    val fourInTen: Int?,
    val usual: Int?,
    val nineInTen: Int?,
    val firstWin: Int?,
    val windowStart: Int,
    val windowEnd: Int,
    val damageText: String,
    val comboShare: Int,
    val mazeShare: Int,
    val power: WebPower,
)

@Serializable
internal class WebPower(val power: Int, val exact: Double, val turn: Double?, val wonByEnd: Int, val speed: Double, val adjust: Double?)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * The app's win turn for a deck ([WinOddsCalc.calculate], same games and seed as the app), as JSON, or "null" for a
 * deck without a commander. [cardsJson] is a list of [WebCard]s in the deck's order; [saltCard] the deck's Commander
 * Salt JSON (its combos and ratings), if any. [games] is for the tests (fewer games, quicker); the app plays 10,000.
 */
@JsExport
fun winOddsJson(cardsJson: String, identity: String, saltCard: String?, games: Int? = null): String {
    val cards = json.decodeFromString<List<WebCard>>(cardsJson).map { it.stats() }
    val salt = SaltCard.decode(saltCard)
    val odds = WinOddsCalc.calculate(cards, identity, salt?.allCombos.orEmpty(), games ?: WinOddsCalc.GAMES) ?: return "null"
    val window = odds.window
    return json.encodeToString(
        WebWinOdds.serializer(),
        WebWinOdds(
            odds, odds.label, odds.fourInTen, odds.usual, odds.nineInTen, odds.firstWin, window.first, window.last,
            odds.damageText, odds.comboShare, odds.mazeShare, AppPowerLevel.of(odds, salt).let { WebPower(it.power, it.exact, it.turn, it.wonByEnd, it.speed, it.adjust) },
        ),
    )
}
