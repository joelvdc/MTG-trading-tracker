package com.mtgtrader.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

/**
 * EDHREC's salt score of every card, from the website's file (gzipped JSON, "Card Name": salt), kept on the phone and
 * read again a week after it was downloaded. The app adds the salt of a deck's lines up itself (see [SaltSum]), so a
 * deck shows the same number on the phone as on the website. Since 1.115.
 */
class SaltScores(private val context: Context, private val http: OkHttpClient) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()

    /** The parsed scores and the file they were read from (by its last change), so a file is read once. */
    @Volatile
    private var cache: Pair<Long, Map<String, Double>>? = null

    /** Every card's salt, by name as the file writes it; empty when there's no file yet and it can't be had. */
    suspend fun scores(): Map<String, Double> = withContext(Dispatchers.IO) {
        mutex.withLock { scoresLocked() }
    }

    /**
     * The deck's salt sum (see [SaltSum]); null when none of its cards has a salt score. Since 1.115.
     */
    suspend fun sumFor(cards: List<DeckCard>): SaltSum? = SaltSum.of(cards, scores())

    private suspend fun scoresLocked(): Map<String, Double> {
        val modified = file.lastModified()
        if (modified != 0L && file.length() > 0) {
            cache?.let { (cachedAt, map) -> if (cachedAt == modified) return map }
            if (System.currentTimeMillis() - modified < MAX_AGE_MS) {
                val map = read(file) ?: emptyMap()
                cache = modified to map
                return map
            }
        }
        download()
        val now = file.lastModified()
        if (now == 0L || file.length() == 0L) return cache?.second ?: emptyMap()
        val map = read(file)
        if (map == null) return cache?.second ?: emptyMap()
        cache = now to map
        return map
    }

    /** Gets a newer file; a failed download keeps the old one. */
    private fun download() {
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            val response = http.newCall(Request.Builder().url(URL).build()).execute()
            response.use { r ->
                if (!r.isSuccessful) return
                val body = r.body ?: return
                tmp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) return
            }
            cache = null
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            // Offline or the file is broken: the old one stays.
        } finally {
            tmp.delete()
        }
    }

    /** The file's scores, or null when it can't be read. */
    private fun read(file: File): Map<String, Double>? {
        if (!file.exists()) return null
        return try {
            val text = GZIPInputStream(file.inputStream().buffered()).bufferedReader().use { it.readText() }
            val scores = HashMap<String, Double>()
            for ((name, value) in (Json.parseToJsonElement(text) as? JsonObject).orEmpty()) {
                val salt = (value as? JsonPrimitive)?.doubleOrNull ?: continue
                if (salt.isFinite()) scores[name] = salt
            }
            scores
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val URL = "https://raw.githubusercontent.com/joelvdc/MTG-trading-tracker/card-salt/card-salt.json.gz"
        const val FILE_NAME = "card-salt.json.gz"

        /** A week, after which the file is downloaded again. */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * A deck's saltiness, added up like the website does: the salt of each line as many times as it has copies (lands, basic
 * lands and the commander included; the maybeboard left out). Null when none of its cards has a salt score.
 * Since 1.115.
 */
data class SaltSum(val total: Double, val level: String, val saltiest: List<SaltyCard>) {
    companion object {
        /** How many of the saltiest cards are named. */
        const val SALTIEST = 5

        fun of(cards: List<DeckCard>, scores: Map<String, Double>): SaltSum? {
            val counted = cards.filter { !it.maybeboard }.map { it to saltOf(it.card.name, scores) }
            if (counted.none { it.second != null }) return null
            val total = counted.sumOf { (line, salt) -> line.quantity * (salt ?: 0.0) }
            val saltiest = counted.filter { (line, salt) -> salt != null && !isBasicLand(line) }
                .groupBy { DeckPrintings.nameKey(it.first.card.name) }
                .map { (_, lines) -> lines.first().first.card.name to lines.maxOf { it.second!! } }
                .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first.lowercase() })
                .take(SALTIEST)
                .map { SaltyCard(it.first, it.second) }
            return SaltSum(total = round(total), level = level(total), saltiest = saltiest)
        }

        /** "Mild" below 30, "Moderate" below 45, else "High" (as the website says it). */
        fun level(total: Double): String = when {
            total < 30 -> "Mild"
            total < 45 -> "Moderate"
            else -> "High"
        }

        /** A card's salt: its name, else its front face ("Fire // Ice"), else none. */
        private fun saltOf(name: String, scores: Map<String, Double>): Double? =
            scores[name] ?: scores[name.substringBefore(" // ")]

        /** A basic land by its type line, else by its name (Landfall's "snow covered island"). */
        private fun isBasicLand(line: DeckCard) = "Basic" in line.types || OrderFiles.isBasicLandName(line.card.name)

        private fun round(v: Double) = (v * 10).roundToInt() / 10.0
    }
}

/** The salt sum as the website writes it: "20.9" (Locale.US). */
fun saltTotal(total: Double): String = String.format(java.util.Locale.US, "%.1f", total)

/** "Salt 20.9 · mild": the deck page's line, and the Decks tab's without the word. */
fun saltLine(sum: SaltSum?, total: Boolean = true) = sum?.let { (if (total) "Salt " else "") + saltTotal(it.total) + " · " + it.level.lowercase() } ?: "—"
