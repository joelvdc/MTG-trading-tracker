@file:OptIn(ExperimentalJsExport::class)

package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Counts the games played (the website's copy of WinOdds.kt calls [game] as each one starts), for a progress bar, and
 * hears a batch that was just played ([finished]), for the website's workers (see [winBatchJson]).
 */
internal object WebProgress {
    /** Hears each game, with its batch's tallies so far (before this game). */
    var onGame: ((WinOddsCalc.Batch) -> Unit)? = null

    /** Hears a batch that was played here, with its final tallies. */
    var onFinished: ((WinOddsCalc.Batch) -> Unit)? = null

    fun game(r: WinOddsCalc.Batch) {
        onGame?.invoke(r)
    }

    fun finished(r: WinOddsCalc.Batch) {
        onFinished?.invoke(r)
    }
}

/**
 * One batch of the app's games ([WinOddsCalc.Batch], which doesn't keep how many of its games it played): [games] of
 * them, and the tallies, so a Web Worker can hand them to the page ([winBatchJson]) and [winMergeJson] can add them up.
 */
@Serializable
internal class WebTally(
    val games: Int,
    val won: List<Int>,
    val byCombo: List<Int>,
    val byMaze: List<Int>,
    val dealt: List<Long>,
    val wins: Int,
    val knockouts: Int,
) {
    companion object {
        /** [batch]'s tallies, after [games] of its games were played. */
        fun of(batch: WinOddsCalc.Batch, games: Int): WebTally = WebTally(
            games, batch.won.toList(), batch.byCombo.toList(), batch.byMaze.toList(), batch.dealt.toList(), batch.wins, batch.knockouts,
        )
    }

    /** These tallies as the batch they came from (as many turns as the list of turns played). */
    fun batch(): WinOddsCalc.Batch {
        val r = WinOddsCalc.Batch(won.size - 1)
        won.forEachIndexed { t, v -> if (t < r.won.size) r.won[t] = v }
        byCombo.forEachIndexed { t, v -> if (t < r.byCombo.size) r.byCombo[t] = v }
        byMaze.forEachIndexed { t, v -> if (t < r.byMaze.size) r.byMaze[t] = v }
        dealt.forEachIndexed { i, d -> if (i < r.dealt.size) r.dealt[i] = d }
        r.wins = wins
        r.knockouts = knockouts
        return r
    }
}

/**
 * Which of the app's batches of games ([WinOddsCalc.calculate]'s batches) play here, for the website's Web Workers:
 * [only] when one of them plays in this worker ([winBatchJson]), [given] when they were played elsewhere and their
 * tallies are to be added up here ([winMergeJson]).
 */
internal object WebBatches {
    /** The one batch that plays here; the others answer empty tallies (see [given]). */
    var only: Int? = null

    /** The tallies of the batches played elsewhere, in batch order; null to play them here (see [given]). */
    var given: List<WinOddsCalc.Batch>? = null

    /** Batch [b]'s tallies when it doesn't play here, or null for the batch that plays (its [turns] for an empty one). */
    fun given(b: Int, turns: Int): WinOddsCalc.Batch? {
        given?.let { return it.getOrNull(b) }
        if (only != null && b != only) return WinOddsCalc.Batch(turns)
        return null
    }
}

/** [odds] as the deck page's JSON (see [winOddsJson]); [salt] is no longer read (the power level went in 1.116). */
private fun webWinOddsJson(odds: WinOdds, salt: SaltCard?): String {
    val window = odds.window
    return json.encodeToString(
        WebWinOdds.serializer(),
        WebWinOdds(
            odds, odds.label, odds.fourInTen, odds.usual, odds.nineInTen, odds.firstWin, window.first, window.last,
            odds.damageText, odds.comboShare, odds.mazeShare,
        ),
    )
}

/**
 * The app's win turn for a deck ([WinOddsCalc.calculate], same games and seed as the app), as JSON, or "null" for a
 * deck without a commander. [cardsJson] is a list of [WebCard]s in the deck's order; [saltCard] the deck's Commander
 * Salt JSON (its combos and ratings), if any. [games] is for the tests (fewer games, quicker); the app plays 10,000.
 * [onProgress] hears the number of games started, every 100 games (it doesn't change the result).
 */
@JsExport
fun winOddsJson(cardsJson: String, identity: String, saltCard: String?, games: Int? = null, onProgress: ((Int) -> Unit)? = null): String {
    val cards = json.decodeFromString<List<WebCard>>(cardsJson).map { it.stats() }
    val salt = SaltCard.decode(saltCard)
    var started = 0
    WebProgress.onGame = onProgress?.let { report -> { started++; if (started % 100 == 0) report(started) } }
    val odds = try {
        WinOddsCalc.calculate(cards, identity, salt?.allCombos.orEmpty(), games ?: WinOddsCalc.GAMES)
    } finally {
        WebProgress.onGame = null
    } ?: return "null"
    return webWinOddsJson(odds, salt)
}

/**
 * One of the app's batches of games ([WinOddsCalc.calculate] plays [games] in four batches, each with its own seed), so
 * four Web Workers can play them side by side: this worker plays batch [batch] only and answers its tallies as JSON
 * ([WebTally]), or "null" for a deck without a commander; [winMergeJson] adds the four up, for [winOddsJson]'s answer.
 * The arguments are [winOddsJson]'s. [snapshotAt] also answers the tallies after that many games (through [onSnapshot],
 * for an estimate while the rest are played), and [onProgress] hears the number of games started, every 100.
 */
@JsExport
fun winBatchJson(
    cardsJson: String,
    identity: String,
    saltCard: String?,
    batch: Int,
    games: Int? = null,
    snapshotAt: Int? = null,
    onProgress: ((Int) -> Unit)? = null,
    onSnapshot: ((String) -> Unit)? = null,
): String {
    val cards = json.decodeFromString<List<WebCard>>(cardsJson).map { it.stats() }
    val salt = SaltCard.decode(saltCard)
    var started = 0
    var played = 0
    var finished: WebTally? = null
    WebBatches.only = batch
    WebProgress.onGame = { r ->
        if (played == snapshotAt) onSnapshot?.invoke(json.encodeToString(WebTally.serializer(), WebTally.of(r, played)))
        played++
        started++
        if (started % 100 == 0) onProgress?.invoke(started)
    }
    WebProgress.onFinished = { r -> finished = WebTally.of(r, played) }
    try {
        WinOddsCalc.calculate(cards, identity, salt?.allCombos.orEmpty(), games ?: WinOddsCalc.GAMES) ?: return "null"
    } finally {
        WebBatches.only = null
        WebProgress.onGame = null
        WebProgress.onFinished = null
    }
    return finished?.let { json.encodeToString(WebTally.serializer(), it) } ?: "null"
}

/**
 * The app's win turn for a deck whose games were played in separate Web Workers: [talliesJson] is their tallies (a list
 * of [WebTally], from [winBatchJson]), which are added up here, so the answer is exactly what [winOddsJson] gives for
 * the same deck (same batches, same seeds, same games). The other arguments are [winOddsJson]'s.
 */
@JsExport
fun winMergeJson(cardsJson: String, identity: String, saltCard: String?, talliesJson: String): String {
    val cards = json.decodeFromString<List<WebCard>>(cardsJson).map { it.stats() }
    val salt = SaltCard.decode(saltCard)
    val tallies = json.decodeFromString<List<WebTally>>(talliesJson)
    WebBatches.given = tallies.map { it.batch() }
    val odds = try {
        WinOddsCalc.calculate(cards, identity, salt?.allCombos.orEmpty(), tallies.sumOf { it.games })
    } finally {
        WebBatches.given = null
    } ?: return "null"
    return webWinOddsJson(odds, salt)
}

/**
 * Commander Salt's answer for a deck (its whole JSON) as the app keeps it with the deck ([SaltCardParser], the deck's
 * saltCard), so decks scored on the website show the same rule-zero cards and combos; null when it can't be read.
 */
@JsExport
fun saltCardJson(body: String): String? = runCatching { SaltCardParser.parse(Json.parseToJsonElement(body).jsonObject).encode() }.getOrNull()
