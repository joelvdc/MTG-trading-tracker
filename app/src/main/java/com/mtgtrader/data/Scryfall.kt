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
    val digital: Boolean = false,
) {
    val image: String? get() = imageUris?.normal ?: cardFaces?.firstOrNull()?.imageUris?.normal
    val hasFoil get() = "foil" in finishes || "etched" in finishes
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
    )
}

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

/**
 * Minimal Scryfall REST client. Scryfall asks for ≤10 requests/second and a descriptive
 * User-Agent, so every call goes through a small throttle.
 */
class ScryfallApi(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val throttle = Mutex()
    private var lastCall = 0L
    private val base = "https://api.scryfall.com/".toHttpUrl()

    private suspend fun waitTurn() = throttle.withLock {
        val wait = lastCall + 110 - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        lastCall = SystemClock.elapsedRealtime()
    }

    /** Returns the body, or null on 404. Throws [IOException] on other failures. */
    private suspend fun call(url: HttpUrl, postJson: String? = null): String? = withContext(Dispatchers.IO) {
        waitTurn()
        val builder = Request.Builder().url(url)
            .header("User-Agent", "MTGTraderAndroid/1.0")
            .header("Accept", "application/json")
        if (postJson != null) builder.post(postJson.toRequestBody("application/json".toMediaType()))
        http.newCall(builder.build()).execute().use { r ->
            when {
                r.isSuccessful -> r.body?.string()
                r.code == 404 -> null
                else -> throw IOException("Scryfall error ${r.code}")
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
     */
    suspend fun collection(identifiers: List<JsonObject>): List<ScryCard> {
        val out = mutableListOf<ScryCard>()
        for (chunk in identifiers.chunked(75)) {
            val payload = buildJsonObject { put("identifiers", JsonArray(chunk)) }.toString()
            val body = call(url("cards/collection"), payload) ?: continue
            out += json.decodeFromString<ScryList>(body).data
        }
        return out
    }

    companion object {
        fun idIdentifier(id: String) = buildJsonObject { put("id", id) }
        fun setNumberIdentifier(set: String, number: String) = buildJsonObject {
            put("set", set.lowercase()); put("collector_number", number)
        }
        fun nameIdentifier(name: String) = buildJsonObject { put("name", name) }
    }
}
