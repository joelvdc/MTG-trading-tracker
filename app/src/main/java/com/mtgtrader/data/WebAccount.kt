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

    /** Logs this phone in ([device] names it in the website's list of devices). */
    suspend fun login(username: String, password: String, device: String): WebLogin =
        account("api/app/login", buildJsonObject { put("username", username.trim()); put("password", password); put("device", device) })

    /** Makes a new account with an invite code from the owner, and logs this phone in. */
    suspend fun register(invite: String, username: String, password: String, device: String): WebLogin =
        account("api/app/register", buildJsonObject {
            put("invite", invite.trim()); put("username", username.trim()); put("password", password); put("device", device)
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
     * Asks the website for the share link of a deck, game, binder or the whole collection ([kind]: "deck", "game",
     * "binder" or "collection"; [target]: the deck's id, the game's or binder's uid, "~" for Unsorted, "" for the
     * collection). Throws [SyncException] with the website's own words when it has nothing to share yet.
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

    sealed interface PriceDownload {
        /** The website's file is still the one with the ETag sent. */
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
    }
}
