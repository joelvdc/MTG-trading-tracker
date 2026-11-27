package com.mtgtrader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/** Logged in to an MTG Trader account on the website: the user name and this phone's device token. Since 1.79. */
data class WebLogin(val username: String, val token: String)

/** The account has two-step login on: the code from its authenticator app (or a recovery code) is needed too. */
class NeedCodeException : Exception("Enter the code from your authenticator app.")

/**
 * Talks to the MTG Trader website (mtgtrader.vercel.app) for syncing through a website account
 * instead of Nextcloud: logging in or making an account (with an invite code), and reading and
 * writing the account's sync file. The file and its ETag work like the one on Nextcloud, so
 * [SyncManager] treats both the same way. Since 1.79.
 */
class WebAccountClient(http: OkHttpClient, private val base: String = BASE) {
    private val http = http.newBuilder()
        .addInterceptor { it.proceed(it.request().newBuilder().header("User-Agent", "MTG Trader (Android)").build()) }
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Logs this phone in ([device] names it in the website's list of devices). [code]: the two-step code from the
     * account's authenticator app or a recovery code; without it, an account with two-step login on gets [NeedCodeException].
     */
    suspend fun login(username: String, password: String, device: String, code: String? = null): WebLogin =
        account("api/app/login", buildJsonObject {
            put("username", username.trim()); put("password", password); put("device", device)
            if (!code.isNullOrBlank()) put("code", code.trim())
        })

    /** Makes a new account with an invite code from the owner, and logs this phone in. */
    suspend fun register(invite: String, username: String, password: String, email: String, device: String): WebLogin =
        account("api/app/register", buildJsonObject {
            put("invite", invite.trim()); put("username", username.trim()); put("password", password)
            put("email", email.trim()); put("device", device)
        })

    /** Logs this phone out on the website (its token stops working). */
    suspend fun logout(login: WebLogin) = call {
        http.newCall(authed(login, "api/app/logout").post(ByteArray(0).toRequestBody()).build()).execute().close()
    }

    /** The account's sync file; [NextcloudClient.Remote.NotModified] if it's still the version with [etag]. */
    suspend fun get(login: WebLogin, etag: String?): NextcloudClient.Remote = call {
        val req = authed(login, "api/sync").get().apply { if (etag != null) header("If-None-Match", etag) }.build()
        http.newCall(req).execute().use { r ->
            when {
                r.code == 304 -> NextcloudClient.Remote.NotModified
                r.code == 404 -> NextcloudClient.Remote.Missing
                r.isSuccessful -> NextcloudClient.Remote.Found(r.body!!.bytes(), r.header("ETag"))
                else -> throw failure(r)
            }
        }
    }

    /** Saves the file if it's still the version with [ifMatch] (or there's none yet when null). */
    suspend fun put(login: WebLogin, bytes: ByteArray, ifMatch: String?): NextcloudClient.PutResult = call {
        val req = authed(login, "api/sync")
            .put(bytes.toRequestBody("application/gzip".toMediaType()))
            .apply { if (ifMatch != null) header("If-Match", ifMatch) else header("If-None-Match", "*") }
            .build()
        http.newCall(req).execute().use { r ->
            when {
                r.code == 412 -> NextcloudClient.PutResult.Conflict
                r.isSuccessful -> NextcloudClient.PutResult.Ok(r.header("ETag"))
                else -> throw failure(r)
            }
        }
    }

    /** One printing's TCGplayer prices from the website: normal, foil, etched (US dollars), its page, when looked up. Since 1.93. */
    data class WebTcgplayer(val usd: Double?, val usdFoil: Double?, val usdEtched: Double?, val url: String?, val at: Long)

    /**
     * The TCGplayer prices the website keeps for [ids] (Scryfall ids), refreshed by its daily job; printings it doesn't
     * keep are left out. Since 1.93.
     */
    suspend fun tcgplayer(login: WebLogin, ids: Collection<String>): Map<String, WebTcgplayer> = call {
        val out = HashMap<String, WebTcgplayer>()
        for (chunk in ids.distinct().chunked(TCGPLAYER_CHUNK)) {
            val body = buildJsonObject { put("ids", JsonArray(chunk.map { JsonPrimitive(it) })) }
            val req = authed(login, "api/app/tcgplayer").post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw failure(r)
                val cards = json.parseToJsonElement(r.body!!.string()).jsonObject["cards"]?.jsonObject
                    ?: throw IOException("The website's answer has no cards")
                for ((id, v) in cards) {
                    val o = v.jsonObject
                    val u = (o["u"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content.toDoubleOrNull()?.takeIf { p -> p > 0 } }
                    out[id] = WebTcgplayer(
                        u.getOrNull(0), u.getOrNull(1), u.getOrNull(2),
                        o["tu"]?.jsonPrimitive?.content?.takeIf { it != "null" && it.isNotBlank() },
                        o["at"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    )
                }
            }
        }
        out
    }

    /** The request for the website's Card Kingdom file (gzipped JSON, read by [readCardKingdomWeb]), unless it's still [etag]. Since 1.93. */
    fun cardKingdomRequest(login: WebLogin, etag: String?): Request =
        authed(login, "api/app/cardkingdom").get().apply { if (etag != null) header("If-None-Match", etag) }.build()

    /**
     * A deck's win turn as the website keeps it (GET/POST /api/winturns, since 1.123): what it was worked out from (the
     * list's [LocalRating.listKey], the simulator's version, the colours and the rating's [LocalRating.saltHash]) and
     * the result, in [WinOddsStore.toWebJson]'s shape.
     */
    data class WebWinTurn(val deckId: Long, val listKey: String, val version: Int, val identity: String, val saltHash: String, val result: String)

    /** Every deck's win turn the website has for its current list. */
    suspend fun winTurns(login: WebLogin): List<WebWinTurn> = call {
        http.newCall(authed(login, "api/winturns").get().build()).execute().use { r ->
            if (!r.isSuccessful) throw failure(r)
            val decks = json.parseToJsonElement(r.body!!.string()).jsonObject["decks"] as? JsonArray ?: throw IOException("The website's answer has no decks")
            decks.mapNotNull { e ->
                runCatching {
                    val o = e.jsonObject
                    WebWinTurn(
                        o["deckId"]!!.jsonPrimitive.content.toLong(), o.str("listKey"), o["version"]!!.jsonPrimitive.content.toInt(),
                        o.str("identity"), o.str("saltHash"), o.str("result"),
                    )
                }.getOrNull()
            }
        }
    }

    /** Sends win turns worked out on the phone, so the website doesn't play them again; answers the deck ids it kept. */
    suspend fun sendWinTurns(login: WebLogin, turns: List<WebWinTurn>): List<Long> = call {
        val body = buildJsonObject {
            put("results", JsonArray(turns.map { t ->
                buildJsonObject {
                    put("deckId", t.deckId)
                    put("listKey", t.listKey)
                    put("version", t.version)
                    put("identity", t.identity)
                    put("saltHash", t.saltHash)
                    put("result", t.result)
                }
            }))
        }
        val req = authed(login, "api/winturns").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw failure(r)
            (json.parseToJsonElement(r.body!!.string()).jsonObject["kept"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.content.toLongOrNull() }
        }
    }

    /** One day of the phone's value history, for [sendValueHistory]. */
    data class ValueDay(val day: String, val cards: Int, val values: Map<String, Double>)

    /** Sends the phone's value history to the account (once, since 1.80), so the website's chart starts from it. */
    suspend fun sendValueHistory(login: WebLogin, days: List<ValueDay>) = call {
        val body = buildJsonObject {
            put("days", JsonArray(days.map { d ->
                buildJsonObject {
                    put("day", d.day)
                    put("cards", d.cards)
                    put("values", JsonObject(d.values.mapValues { JsonPrimitive(it.value) }))
                }
            }))
        }
        val req = authed(login, "api/app/valuehistory").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r -> if (!r.isSuccessful) throw failure(r) }
    }

    /** A read-only page on the website for something shared from the app ([on] is whether the link works). */
    data class ShareLink(val url: String, val on: Boolean)

    /**
     * Asks the website for the share link of a deck, game, binder, the whole collection or the wishlist ([kind]: "deck",
     * "game", "binder", "collection" or "wishlist" (since 1.91); [target]: the deck's id, the game's or binder's uid, "~"
     * for Unsorted, "" for the collection and the wishlist). Throws [SyncException] with the website's own words when it has nothing to share yet.
     */
    suspend fun share(login: WebLogin, kind: String, target: String): ShareLink = call {
        val body = buildJsonObject { put("kind", kind); put("target", target) }
        val req = authed(login, "api/app/share").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val o = runCatching { json.parseToJsonElement(r.body!!.string()).jsonObject }.getOrNull()
            val url = o?.get("url")?.jsonPrimitive?.content
            if (r.isSuccessful && url != null) return@call ShareLink(url, o["on"]?.jsonPrimitive?.content != "false")
            if (r.code == 401) throw failure(r)
            val error = o?.get("error")?.jsonPrimitive?.content
            if (error != null) throw SyncException(error)
            throw IOException("The website answered ${r.code}")
        }
    }

    /**
     * The website's auto categories for cards, by card name (no login needed): the first of each list is the card's
     * category, the others are extra categories. Cards the website doesn't know are left out. Throws when the website
     * can't be reached or answers badly.
     */
    suspend fun autoCategories(names: Collection<String>): Map<String, List<String>> = call {
        val result = HashMap<String, List<String>>()
        for (chunk in names.distinct().chunked(CATEGORY_CHUNK)) {
            val body = buildJsonObject { put("names", JsonArray(chunk.map { JsonPrimitive(it) })) }
            val req = Request.Builder().url(base + "api/cards/categories")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("The website answered ${r.code}")
                val categories = json.parseToJsonElement(r.body!!.string()).jsonObject["categories"]?.jsonObject
                    ?: throw IOException("The website's answer has no categories")
                for ((name, list) in categories) {
                    val cats = (list as JsonArray).map { it.jsonPrimitive.content }.filter { it.isNotBlank() }
                    if (cats.isNotEmpty()) result[name] = cats
                }
            }
        }
        result
    }

    /**
     * The website's estimated bracket and salt for a deck's list (no login needed; local decks, which Commander Salt
     * can't score): the [SaltCard] it works out, as JSON. Sends the deck's own lines ([identity] is the deck's colour
     * identity, e.g. "WUB"); throws [IOException] when the website can't rate it yet or answers with something that
     * isn't a rating.
     */
    suspend fun rate(cards: List<DeckCard>, identity: String): String = call {
        val body = buildJsonObject {
            put("cards", JsonArray(cards.filter { !it.maybeboard }.map { c ->
                buildJsonObject {
                    put("name", c.card.name)
                    put("quantity", c.quantity)
                    put("cmc", c.cmc)
                    put("types", c.types)
                    put("commander", c.commander)
                }
            }))
            put("identity", identity)
        }
        val req = Request.Builder().url(base + "api/rate")
            .header("x-requested-with", "mtgtrader")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("The website answered ${r.code}")
            val card = runCatching { json.parseToJsonElement(r.body!!.string()).jsonObject["saltCard"]?.jsonPrimitive?.content }.getOrNull()
                ?: throw IOException("The website's answer has no rating")
            if (SaltCard.decode(card) == null) throw IOException("The website's rating can't be read")
            card
        }
    }

    sealed interface PriceDownload {        /** The website's file is still the one with the ETag sent. */
        data object NotModified : PriceDownload
        data class Saved(val etag: String?) : PriceDownload
    }

    /**
     * The website's daily Cardmarket price file (gzipped JSON, about 2 MB instead of Cardmarket's 27 MB) into [target],
     * unless it's still the version with [etag]. [onProgress] gets the bytes done and the total (-1 if unknown). Since 1.81.
     */
    suspend fun prices(login: WebLogin, etag: String?, target: java.io.File, onProgress: (Long, Long) -> Unit = { _, _ -> }): PriceDownload = call {
        val req = authed(login, "api/app/prices").get().apply { if (etag != null) header("If-None-Match", etag) }.build()
        http.newCall(req).execute().use { r ->
            when {
                r.code == 304 -> PriceDownload.NotModified
                r.isSuccessful -> {
                    val body = r.body ?: throw IOException("The website sent no prices")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        target.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                onProgress(done, total)
                            }
                        }
                    }
                    PriceDownload.Saved(r.header("ETag"))
                }
                else -> throw failure(r)
            }
        }
    }

    private suspend fun account(path: String, body: JsonObject): WebLogin = call {
        val req = Request.Builder().url(base + path).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val o = runCatching { json.parseToJsonElement(r.body!!.string()).jsonObject }.getOrNull()
            val token = o?.get("token")?.jsonPrimitive?.content
            if (r.isSuccessful && token != null) return@call WebLogin(o["username"]?.jsonPrimitive?.content ?: body.str("username"), token)
            // The account has two-step login on and no code was sent: the app asks for it and logs in again.
            if (r.isSuccessful && o?.get("needCode")?.jsonPrimitive?.content == "true") throw NeedCodeException()
            val error = o?.get("error")?.jsonPrimitive?.content
            // The website's own words for wrong passwords, used invite codes and the like.
            if (r.code in 400..499 && error != null) throw SyncException(error)
            throw IOException("The website answered ${r.code}")
        }
    }

    private fun authed(login: WebLogin, path: String) = Request.Builder().url(base + path).header("Authorization", "Bearer ${login.token}")

    private fun failure(r: Response) = when (r.code) {
        401 -> SyncException("The website no longer accepts this phone's login (a new password, or logged out there). Log out here and log in again.")
        400 -> SyncException("The website didn't accept the sync file.")
        else -> IOException("The website answered ${r.code}")
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content.orEmpty()

    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        const val BASE = "https://mtgtrader.vercel.app/"
        const val HOST = "mtgtrader.vercel.app"

        /** The most card names the website answers in one call. */
        const val CATEGORY_CHUNK = 500

        /** The most printings asked for in one /api/app/tcgplayer request (the website takes up to 20,000). */
        const val TCGPLAYER_CHUNK = 5000
    }
}
