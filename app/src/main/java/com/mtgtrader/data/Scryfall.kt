package com.mtgtrader.data

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class ScryImageUris(val small: String? = null, val normal: String? = null)

@Serializable
data class ScryFace(
    val name: String = "",
    @SerialName("image_uris") val imageUris: ScryImageUris? = null,
    @SerialName("type_line") val typeLine: String = "",
    @SerialName("oracle_text") val oracleText: String? = null,
    val colors: List<String>? = null,
)

@Serializable
data class ScryPrices(
    val eur: String? = null,
    @SerialName("eur_foil") val eurFoil: String? = null,
)

@Serializable
data class ScryCard(
    val id: String,
    val name: String,
    val set: String,
    @SerialName("set_name") val setName: String = "",
    @SerialName("collector_number") val collectorNumber: String = "",
    val rarity: String = "",
    val lang: String = "en",
    @SerialName("released_at") val releasedAt: String? = null,
    @SerialName("cardmarket_id") val cardmarketId: Int? = null,
    @SerialName("image_uris") val imageUris: ScryImageUris? = null,
    @SerialName("card_faces") val cardFaces: List<ScryFace>? = null,
    val prices: ScryPrices = ScryPrices(),
    val finishes: List<String> = emptyList(),
    @SerialName("promo_types") val promoTypes: List<String> = emptyList(),
    val digital: Boolean = false,
    /** Name printed on this card instead of its real name, e.g. "Barrow-Downs" for Bojuka Bog (LTC). */
    @SerialName("flavor_name") val flavorName: String? = null,
    @SerialName("type_line") val typeLine: String = "",
    val cmc: Double = 0.0,
    @SerialName("oracle_id") val oracleId: String? = null,
    val colors: List<String>? = null,
    @SerialName("color_identity") val colorIdentity: List<String> = emptyList(),
    /** EDHREC's popularity rank (1 = most played in Commander). */
    @SerialName("edhrec_rank") val edhrecRank: Int? = null,
    /** This printing isn't the card's first. */
    val reprint: Boolean = false,
    @SerialName("oracle_text") val oracleText: String? = null,
) {
    /** Rules text of every face. */
    val fullText: String get() = oracleText ?: cardFaces?.joinToString("\n") { it.oracleText.orEmpty() }.orEmpty()

    val image: String? get() = imageUris?.normal ?: cardFaces?.firstOrNull()?.imageUris?.normal

    /** "Barrow-Downs (Bojuka Bog)" for cards printed under another name, else just the name. */
    val displayName get() = flavorName?.let { "$it ($name)" } ?: name
    val hasFoil get() = "foil" in finishes
    val hasEtched get() = "etched" in finishes
    val hasNonFoil get() = "nonfoil" in finishes || finishes.isEmpty()

    fun toRef() = CardRef(
        scryfallId = id,
        name = name,
        setCode = set,
        setName = setName,
        collectorNumber = collectorNumber,
        rarity = rarity,
        imageUrl = image,
        cardmarketId = cardmarketId,
        fallbackEur = prices.eur?.toDoubleOrNull(),
        fallbackEurFoil = prices.eurFoil?.toDoubleOrNull(),
        hasNonFoil = hasNonFoil,
        hasFoil = hasFoil,
        foilType = FoilTypes.pick(promoTypes),
        hasEtched = hasEtched,
        flavorName = flavorName,
    )
}

/** Scryfall's published rate limits (scryfall.com/docs/api/rate-limits). */
object ScryfallLimits {
    const val MAX_RETRIES = 2

    /** Scryfall shuts an app out for 30 seconds after a 429. */
    private const val DEFAULT_WAIT_MS = 30_000L

    private val slowPaths = listOf("/cards/search", "/cards/named", "/cards/random", "/cards/collection")

    /** Endpoints limited to 2 requests/second; the rest allow 10/second. */
    fun isSlow(path: String) = slowPaths.any { path == it || path.startsWith("$it/") }

    /** How long to wait after a 429: the server's Retry-After (seconds) when given, else 30 seconds. */
    fun retryDelayMs(retryAfter: String?): Long =
        retryAfter?.trim()?.toLongOrNull()?.takeIf { it in 1..300 }?.times(1000) ?: DEFAULT_WAIT_MS
}

/** A name suggestion: a card name, or a flavor name ("Barrow-Downs") printed on some copies of [realName]. */
data class NameSuggestion(val label: String, val realName: String? = null)

@Serializable
private data class ScryList(
    val data: List<ScryCard> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_page") val nextPage: String? = null,
)

@Serializable
private data class ScryCatalog(val data: List<String> = emptyList())

@Serializable
data class ScrySet(
    val code: String,
    @SerialName("icon_svg_uri") val iconSvgUri: String? = null,
)

@Serializable
private data class ScrySetList(val data: List<ScrySet> = emptyList())

/** Keeps calls at least [gapMs] apart. */
private class Throttle(private val gapMs: Long) {
    private val lock = Mutex()
    private var lastCall = 0L

    suspend fun waitTurn() = lock.withLock {
        val wait = lastCall + gapMs - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        lastCall = SystemClock.elapsedRealtime()
    }
}

/**
 * Minimal Scryfall REST client with a descriptive User-Agent, as Scryfall asks. Its rate limits
 * are 2 requests/second for search, named, random and collection lookups and 10/second for
 * everything else, so each group has its own throttle. A 429 (too many requests) is waited out
 * and retried rather than failing straight away.
 */
class ScryfallApi(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val fast = Throttle(110)
    private val slow = Throttle(550)
    private val base = "https://api.scryfall.com/".toHttpUrl()

    /** Returns the body, or null on 404. Throws [IOException] on other failures. */
    private suspend fun call(url: HttpUrl, postJson: String? = null): String? {
        val throttle = if (ScryfallLimits.isSlow(url.encodedPath)) slow else fast
        var retries = 0
        while (true) {
            throttle.waitTurn()
            val builder = Request.Builder().url(url)
                .header("User-Agent", "MTGTraderAndroid/1.0")
                .header("Accept", "application/json")
            if (postJson != null) builder.post(postJson.toRequestBody("application/json".toMediaType()))
            val (code, body, retryAfter) = withContext(Dispatchers.IO) {
                http.newCall(builder.build()).execute().use { r ->
                    Triple(r.code, if (r.isSuccessful) r.body?.string() else null, r.header("Retry-After"))
                }
            }
            when {
                code in 200..299 -> return body
                code == 404 -> return null
                code == 429 && retries < ScryfallLimits.MAX_RETRIES -> {
                    retries++
                    delay(ScryfallLimits.retryDelayMs(retryAfter))
                }
                code == 429 -> throw IOException("Scryfall is busy (too many requests); try again in a minute")
                else -> throw IOException("Scryfall error $code")
            }
        }
    }

    private fun url(path: String, vararg query: Pair<String, String?>): HttpUrl {
        val b = base.newBuilder()
        path.split('/').filter { it.isNotEmpty() }.forEach { b.addPathSegment(it) }
        query.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build()
    }

    suspend fun autocomplete(query: String): List<String> {
        val body = call(url("cards/autocomplete", "q" to query)) ?: return emptyList()
        return json.decodeFromString<ScryCatalog>(body).data
    }

    /**
     * Flavor names containing [query], with the real card name, e.g. "Barrow" → ("Barrow-Downs", "Bojuka Bog").
     * Autocomplete only knows real names, but Scryfall's `name:` search also matches flavor names.
     */
    suspend fun flavorNames(query: String): List<NameSuggestion> {
        val q = query.replace("\"", "").trim()
        if (q.length < 3) return emptyList()
        val body = call(url("cards/search", "q" to "name:\"$q\" game:paper", "unique" to "prints")) ?: return emptyList()
        return json.decodeFromString<ScryList>(body).data
            .mapNotNull { c -> c.flavorName?.takeIf { it.contains(q, ignoreCase = true) }?.let { NameSuggestion(it, c.name) } }
            .distinctBy { it.label.lowercase() }
    }

    /** All paper printings of a card, newest first. */
    suspend fun prints(name: String): List<ScryCard> {
        val all = printsExact(name)
        // Double-faced cards: exact search may only match the front face's name.
        return if (all.isEmpty() && " // " in name) printsExact(name.substringBefore(" // ")) else all
    }

    private suspend fun printsExact(name: String): List<ScryCard> {
        val out = mutableListOf<ScryCard>()
        var next: HttpUrl? = url(
            "cards/search",
            "q" to "!\"$name\" game:paper",
            "unique" to "prints",
            "order" to "released",
            "dir" to "desc",
            "include_extras" to "true",
        )
        var pages = 0
        while (next != null && pages < 5) {
            val body = call(next) ?: break
            val list = json.decodeFromString<ScryList>(body)
            out += list.data
            next = if (list.hasMore) list.nextPage?.toHttpUrl() else null
            pages++
        }
        return out
    }

    /** Free-form Scryfall search (e.g. `set:mkm`), first page only. */
    suspend fun search(query: String): List<ScryCard> {
        val body = call(url("cards/search", "q" to "$query game:paper", "order" to "name")) ?: return emptyList()
        return json.decodeFromString<ScryList>(body).data
    }

    /** Every card (one printing each) matching a search, over up to [maxPages] pages of 175. */
    suspend fun searchAll(query: String, maxPages: Int = 6): List<ScryCard> {
        val out = mutableListOf<ScryCard>()
        var next: HttpUrl? = url("cards/search", "q" to "$query game:paper", "unique" to "cards")
        var pages = 0
        while (next != null && pages < maxPages) {
            val body = call(next) ?: break
            val list = json.decodeFromString<ScryList>(body)
            out += list.data
            next = if (list.hasMore) list.nextPage?.toHttpUrl() else null
            pages++
        }
        return out
    }

    /** Every set Scryfall knows, including promo and token sets. */
    suspend fun sets(): List<ScrySet> {
        val body = call(url("sets")) ?: return emptyList()
        return json.decodeFromString<ScrySetList>(body).data
    }

    suspend fun fuzzy(name: String, set: String? = null): ScryCard? {
        val body = call(url("cards/named", "fuzzy" to name, "set" to set)) ?: return null
        return json.decodeFromString<ScryCard>(body)
    }

    suspend fun bySetNumber(set: String, number: String): ScryCard? {
        val body = call(url("cards/${set.lowercase()}/$number")) ?: return null
        return json.decodeFromString<ScryCard>(body)
    }

    /**
     * Batch lookup (max 75 identifiers per call). Each identifier is one of
     * {"id"}, {"set","collector_number"} or {"name"} as Scryfall expects.
     * [onProgress] gets (identifiers looked up so far, total) after each batch.
     */
    suspend fun collection(identifiers: List<JsonObject>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<ScryCard> {
        val out = mutableListOf<ScryCard>()
        var done = 0
        for (chunk in identifiers.chunked(75)) {
            val payload = buildJsonObject { put("identifiers", JsonArray(chunk)) }.toString()
            call(url("cards/collection"), payload)?.let { out += json.decodeFromString<ScryList>(it).data }
            done += chunk.size
            onProgress(done, identifiers.size)
        }
        return out
    }

    companion object {
        fun idIdentifier(id: String) = buildJsonObject { put("id", id) }
        fun setNumberIdentifier(set: String, number: String) = buildJsonObject {
            put("set", set.lowercase()); put("collector_number", number)
        }
        fun nameIdentifier(name: String) = buildJsonObject { put("name", name) }
        fun oracleIdentifier(oracleId: String) = buildJsonObject { put("oracle_id", oracleId) }
    }
}
