package com.mtgtrader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * How the app's card details translate to Archidekt's. Archidekt grades condition on the
 * TCGplayer scale (NM, LP, MP, HP, Damaged) where the app uses Cardmarket's seven grades, so two
 * app grades can share one Archidekt grade. Grades with the same letters (NM, LP) match each other.
 * Since 1.21; LP was mapped to MP in 1.21-beta.1 and -beta.2.
 */
object ArchidektCodes {
    /** App grade → Archidekt grade. */
    val toArchidektCondition = linkedMapOf("MT" to "NM", "NM" to "NM", "EX" to "LP", "LP" to "LP", "GD" to "MP", "PL" to "HP", "PO" to "D")

    /** Archidekt grade → the app grade a card from Archidekt gets. */
    val toAppCondition = linkedMapOf("NM" to "NM", "LP" to "LP", "MP" to "GD", "HP" to "PL", "D" to "PO")

    val conditionNames = mapOf("NM" to "Near Mint", "LP" to "Lightly Played", "MP" to "Moderately Played", "HP" to "Heavily Played", "D" to "Damaged")

    /** Archidekt's numbers for its grades and languages. */
    private val conditionNumber = mapOf("NM" to 1, "LP" to 2, "MP" to 3, "HP" to 4, "D" to 5)
    private val languageNumber = mapOf("EN" to 1, "CT" to 2, "DE" to 3, "FR" to 4, "IT" to 5, "JP" to 6, "KR" to 7, "PT" to 8, "RU" to 9, "CS" to 10, "SP" to 11)

    /** App language code ↔ Archidekt's. */
    private val languages = mapOf(
        "EN" to "EN", "DE" to "DE", "FR" to "FR", "IT" to "IT", "ES" to "SP", "PT" to "PT",
        "JA" to "JP", "KO" to "KR", "RU" to "RU", "ZHS" to "CS", "ZHT" to "CT",
    )
    private val appLanguages = languages.entries.associate { (a, b) -> b to a }

    fun archCondition(app: String) = toArchidektCondition[app.uppercase()] ?: "NM"
    fun appCondition(arch: String) = toAppCondition[arch.uppercase()] ?: "NM"
    fun archLanguage(app: String) = languages[app.uppercase()] ?: "EN"
    fun appLanguage(arch: String) = appLanguages[arch.uppercase()] ?: "EN"

    fun conditionNumber(code: String) = conditionNumber[code] ?: 1
    fun languageNumber(code: String) = languageNumber[code] ?: 1

    /** Archidekt sends grades and languages as numbers; older answers or the import use codes. */
    fun conditionCode(v: JsonElement?): String {
        val p = (v as? JsonPrimitive) ?: return "NM"
        p.intOrNull?.let { n -> return conditionNumber.entries.firstOrNull { it.value == n }?.key ?: "NM" }
        val s = p.contentOrNull?.uppercase() ?: return "NM"
        return when {
            s in conditionNumber -> s
            s.startsWith("NEAR") -> "NM"
            s.startsWith("LIGHT") -> "LP"
            s.startsWith("MODERATE") -> "MP"
            s.startsWith("HEAV") -> "HP"
            s.startsWith("DAMAGED") || s == "DMG" -> "D"
            else -> "NM"
        }
    }

    fun languageCode(v: JsonElement?): String {
        val p = (v as? JsonPrimitive) ?: return "EN"
        p.intOrNull?.let { n -> return languageNumber.entries.firstOrNull { it.value == n }?.key ?: "EN" }
        return p.contentOrNull?.uppercase()?.takeIf { it in languageNumber } ?: "EN"
    }

    fun modifier(f: Finish) = when (f) {
        Finish.NONFOIL -> "Normal"
        Finish.FOIL -> "Foil"
        Finish.ETCHED -> "Etched"
    }

    fun finish(modifier: String?) = when (modifier?.lowercase()) {
        "foil" -> Finish.FOIL
        "etched" -> Finish.ETCHED
        else -> Finish.NONFOIL
    }
}

/** A printing as Archidekt knows it: its own card id and the finishes it comes in. */
data class ArchidektPrinting(
    val cardId: Long,
    val options: List<String>,
    /** The card's "auto" category on Archidekt (Ramp, Draw, Removal…), if it has one. Since 1.46. */
    val defaultCategory: String? = null,
)

/**
 * Archidekt's public card lookup, through the same (unofficial) JSON API its website uses; it needs no login. Used
 * for the auto category of cards added to a local deck (the app now uses the website's table instead). Until 1.78 this client also logged in to sync the
 * collection and send deck edits; that went with the Archidekt login.
 */
class ArchidektCollectionClient(private val http: OkHttpClient, private val baseUrl: () -> String = { DEFAULT_BASE }) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    /** Archidekt's card ids for Scryfall printings, 60 at a time. Printings Archidekt doesn't know are left out. */
    suspend fun printings(scryfallIds: Collection<String>): Map<String, ArchidektPrinting> = io {
        val out = HashMap<String, ArchidektPrinting>()
        for (chunk in scryfallIds.distinct().chunked(60)) {
            var next: String? = url("/api/cards/v2/?uids=${chunk.joinToString(",")}&allEditions&includeTokens&includeEmblems&includeArtCards&includeOversized&includeDigital")
            var pages = 0
            while (next != null && pages < 5) {
                val req = Request.Builder().url(next).header("Accept", "application/json").get().build()
                val text = http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("Archidekt answered ${r.code} looking up cards")
                    r.body?.string().orEmpty()
                }
                val o = json.parseToJsonElement(text).jsonObject
                o["results"]?.jsonArray?.forEach { c ->
                    val co = c.jsonObject
                    val uid = co["uid"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val id = co["id"]?.jsonPrimitive?.longOrNull ?: return@forEach
                    val options = (co["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                    val auto = ((co["oracleCard"] as? JsonObject)?.get("defaultCategory") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    out[uid] = ArchidektPrinting(id, options, auto)
                }
                next = (o["next"] as? JsonPrimitive)?.contentOrNull?.let(::sameScheme)
                pages++
            }
        }
        out
    }

    private fun url(path: String) = baseUrl().trimEnd('/') + path

    /** Archidekt's "next" links say http://; keep to the scheme the app talks (https, or http for a test server). */
    private fun sameScheme(link: String): String {
        val base = baseUrl().toHttpUrl()
        val u = link.toHttpUrl()
        return if (u.host == base.host) u.newBuilder().scheme(base.scheme).port(base.port).build().toString() else link
    }

    /** Runs a request off the main thread; network errors say they came from Archidekt. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: java.net.SocketTimeoutException) {
            throw IOException("Archidekt didn't answer in time; try again in a moment", e)
        } catch (e: IOException) {
            if (e.message?.contains("Archidekt") == true) throw e
            throw IOException("Couldn't reach Archidekt (${e.message ?: e.javaClass.simpleName})", e)
        }
    }

    companion object {
        const val DEFAULT_BASE = "https://archidekt.com"
        /** A deck's visibility on Archidekt, as decks are read (kept on the deck). */
        const val VISIBILITY_PRIVATE = "private"
        const val VISIBILITY_UNLISTED = "unlisted"
        const val VISIBILITY_PUBLIC = "public"
    }
}
