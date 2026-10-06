package com.mtgtrader.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

/** One entry of an Archidekt collection: some copies of one printing in one finish, condition and language. */
@Serializable
data class ArchidektEntry(
    val id: Long,
    val cardId: Long,
    val scryfallId: String,
    val name: String = "",
    val setCode: String = "",
    val number: String = "",
    val finish: Finish = Finish.NONFOIL,
    /** Archidekt's code, e.g. "NM". */
    val condition: String = "NM",
    /** Archidekt's code, e.g. "EN". */
    val language: String = "EN",
    val quantity: Int,
    val purchasePrice: Double? = null,
    val tags: List<Long> = emptyList(),
)

@Serializable
data class ArchidektTag(val id: Long, val name: String, val color: String = "")

/** A collection as read from Archidekt. */
data class ArchidektCollectionData(val entries: List<ArchidektEntry>, val tags: List<ArchidektTag>)

/** A printing as Archidekt knows it: its own card id and the finishes it comes in. */
data class ArchidektPrinting(val cardId: Long, val options: List<String>)

/** Logged in to Archidekt: the tokens its website uses (never the password). */
@Serializable
data class ArchidektLogin(val userId: Long, val username: String, val token: String, val refresh: String)

class ArchidektAuthException(message: String) : Exception(message)

/**
 * Archidekt's collection, through the same (unofficial) JSON API its website uses: logging in,
 * reading the collection, and adding, changing and deleting entries.
 */
class ArchidektCollectionClient(private val http: OkHttpClient, private val baseUrl: () -> String) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val jsonType = "application/json".toMediaType()

    /** Called with new tokens after they were refreshed, so they can be saved. */
    var onTokens: ((ArchidektLogin) -> Unit)? = null

    suspend fun login(user: String, password: String): ArchidektLogin = io {
        val body = buildJsonObject {
            if ('@' in user && '.' in user.substringAfter('@')) put("email", user.trim()) else put("username", user.trim())
            put("password", password)
        }
        val req = Request.Builder().url(url("/api/rest-auth/login/")).header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonType)).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 400 || r.code == 401) throw ArchidektAuthException("Archidekt didn't accept that user name or password.")
            if (!r.isSuccessful) throw IOException("Archidekt answered ${r.code}")
            val o = json.parseToJsonElement(text).jsonObject
            val u = o["user"]?.jsonObject ?: throw IOException("Archidekt's answer had no user")
            ArchidektLogin(
                userId = u["id"]!!.jsonPrimitive.longOrNull ?: throw IOException("Archidekt's answer had no user id"),
                username = u["username"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                token = o["token"]?.jsonPrimitive?.contentOrNull ?: o["access"]?.jsonPrimitive?.contentOrNull ?: throw IOException("Archidekt sent no token"),
                refresh = o["refresh_token"]?.jsonPrimitive?.contentOrNull ?: o["refresh"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }

    /**
     * The whole collection, page by page. Archidekt's "next" link on the first page leaves out the
     * page number (it points at page 1 again), so the pages are asked for by number, up to the
     * "totalPages" Archidekt reports.
     */
    suspend fun collection(login: ArchidektLogin, onPage: (Int, Int) -> Unit = { _, _ -> }): Pair<ArchidektCollectionData, ArchidektLogin> {
        var auth = login
        val entries = LinkedHashMap<Long, ArchidektEntry>()
        val seen = HashSet<Long>()
        val tags = LinkedHashMap<Long, ArchidektTag>()
        var page = 1
        var totalPages = 1
        var total = 0
        while (page <= totalPages) {
            if (page > MAX_PAGES) throw IOException("The Archidekt collection is too big to read")
            val (text, a) = send(auth) { it.url(url("/api/collection/${login.userId}/v2/?game=1&pageSize=$PAGE&page=$page")).get() }
            auth = a
            val o = json.parseToJsonElement(text).jsonObject
            total = o["count"]?.jsonPrimitive?.intOrNull ?: total
            val results = o["results"]?.jsonArray.orEmpty()
            for (e in results) {
                (e as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull?.let(seen::add)
                // Entries without a Scryfall printing (custom cards) are left out, and so never touched.
                parseEntry(e.jsonObject)?.let { entries[it.id] = it }
            }
            (o["tags"] as? JsonArray)?.forEach { t -> parseTag(t.jsonObject)?.let { tags[it.id] = it } }
            totalPages = o["totalPages"]?.jsonPrimitive?.intOrNull
                ?: if (results.isNotEmpty() && (o["next"] as? JsonPrimitive)?.contentOrNull != null) page + 1 else page
            onPage(seen.size, total)
            if (results.isEmpty()) break
            page++
        }
        if (total > 0 && seen.size != total) {
            throw IOException("Archidekt said the collection has $total entries, but ${seen.size} came back. Nothing was changed; try again.")
        }
        return ArchidektCollectionData(entries.values.toList(), tags.values.toList()) to auth
    }

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
                    out[uid] = ArchidektPrinting(id, options)
                }
                next = (o["next"] as? JsonPrimitive)?.contentOrNull?.let(::sameScheme)
                pages++
            }
        }
        out
    }

    /** Adds an entry ([entry]'s id is ignored); returns it with Archidekt's id. */
    suspend fun create(login: ArchidektLogin, entry: ArchidektEntry): Pair<ArchidektEntry, ArchidektLogin> {
        val (text, a) = send(login) { it.url(url("/api/collection/v2/")).post(body(entry, withId = false).toString().toRequestBody(jsonType)) }
        val id = json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.longOrNull ?: throw IOException("Archidekt didn't say which entry it made")
        return entry.copy(id = id) to a
    }

    /** Changes an entry to [entry] (quantity, printing, finish, condition, language, price, tags). */
    suspend fun update(login: ArchidektLogin, entry: ArchidektEntry): ArchidektLogin =
        send(login) { it.url(url("/api/collection/v2/${entry.id}/")).patch(body(entry, withId = true).toString().toRequestBody(jsonType)) }.second

    suspend fun delete(login: ArchidektLogin, ids: List<Long>): ArchidektLogin {
        if (ids.isEmpty()) return login
        var a = login
        for (chunk in ids.chunked(100)) {
            val body = buildJsonObject { put("ids", buildJsonArray { chunk.forEach { add(JsonPrimitive(it)) } }) }
            a = send(a) { it.url(url("/api/collection/bulk/")).delete(body.toString().toRequestBody(jsonType)) }.second
        }
        return a
    }

    suspend fun createTag(login: ArchidektLogin, name: String, color: String): Pair<ArchidektTag, ArchidektLogin> {
        val body = buildJsonObject { put("tagname", name); put("tagcolor", color) }
        val (text, a) = send(login) { it.url(url("/api/collection/tags/")).post(body.toString().toRequestBody(jsonType)) }
        return (parseTag(json.parseToJsonElement(text).jsonObject) ?: throw IOException("Archidekt didn't make the label")) to a
    }

    private fun body(e: ArchidektEntry, withId: Boolean) = buildJsonObject {
        put("game", 1)
        if (withId) put("id", e.id)
        put("quantity", e.quantity)
        put("card", e.cardId)
        put("modifier", ArchidektCodes.modifier(e.finish))
        put("language", ArchidektCodes.languageNumber(e.language))
        put("condition", ArchidektCodes.conditionNumber(e.condition))
        put("tags", buildJsonArray { e.tags.forEach { add(JsonPrimitive(it)) } })
        put("purchasePrice", e.purchasePrice?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    private fun parseEntry(o: JsonObject): ArchidektEntry? {
        val card = o["card"] as? JsonObject ?: return null
        val uid = card["uid"]?.jsonPrimitive?.contentOrNull ?: return null
        val oracle = card["oracleCard"] as? JsonObject
        val edition = card["edition"] as? JsonObject
        return ArchidektEntry(
            id = o["id"]?.jsonPrimitive?.longOrNull ?: return null,
            cardId = card["id"]?.jsonPrimitive?.longOrNull ?: return null,
            scryfallId = uid,
            name = oracle?.get("name")?.jsonPrimitive?.contentOrNull ?: card["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            setCode = edition?.get("editioncode")?.jsonPrimitive?.contentOrNull.orEmpty(),
            number = card["collectorNumber"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            finish = ArchidektCodes.finish(o["modifier"]?.jsonPrimitive?.contentOrNull),
            condition = ArchidektCodes.conditionCode(o["condition"]),
            language = ArchidektCodes.languageCode(o["language"]),
            quantity = o["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
            purchasePrice = (o["purchasePrice"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }?.takeIf { it > 0 },
            tags = (o["tags"] as? JsonArray)?.mapNotNull { t ->
                (t as? JsonPrimitive)?.longOrNull ?: (t as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull
            }.orEmpty(),
        )
    }

    private fun parseTag(o: JsonObject): ArchidektTag? {
        val id = o["id"]?.jsonPrimitive?.longOrNull ?: return null
        val name = (o["name"] ?: o["tagname"])?.jsonPrimitive?.contentOrNull ?: return null
        return ArchidektTag(id, name, (o["color"] ?: o["tagcolor"])?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    /** One authorised call; refreshes the token once when it has expired. Returns the answer and the (possibly new) tokens. */
    private suspend fun send(login: ArchidektLogin, build: (Request.Builder) -> Request.Builder): Pair<String, ArchidektLogin> = io {
        var auth = if (expiresSoon(login.token)) refreshShared(login) else login
        var refreshed = false
        var waits = 0
        while (true) {
            val req = build(Request.Builder()).header("Accept", "application/json").header("Authorization", "JWT ${auth.token}").build()
            val answer = try {
                http.newCall(req).execute().use { r -> Triple(r.code, r.body?.string().orEmpty(), r.header("Retry-After")?.toLongOrNull()) }
            } catch (e: IOException) {
                // Archidekt answers deletions with "204 No Content" plus a short body, which OkHttp rejects
                // after the request went through: the deletion was done.
                if (e is java.net.ProtocolException && e.message?.startsWith("HTTP 204 had non-zero Content-Length") == true) return@io "" to auth
                // A timeout or dropped connection: ask again, unless it was a new entry (it may have been made already;
                // the next sync then finds it on Archidekt and counts it as agreed).
                if (req.method == "POST" || waits >= 3) throw e
                waits++
                delay(2000L * waits)
                continue
            }
            val (code, text, retryAfter) = answer
            when {
                code in 200..299 -> return@io text to auth
                code == 401 && !refreshed -> { auth = refreshShared(auth); refreshed = true }
                code == 401 || code == 403 -> throw ArchidektAuthException("Archidekt no longer accepts this login. Log in to Archidekt again.")
                // Too many requests, or a hiccup on Archidekt's side: wait a little and try again.
                (code == 429 || code in 500..599) && waits < 4 -> {
                    waits++
                    delay(((retryAfter ?: 0L) * 1000).coerceIn(2000L * waits, 60_000L))
                }
                code == 429 -> throw IOException("Archidekt asks to slow down; try again in a few minutes")
                else -> throw IOException("Archidekt answered $code" + text.take(200).takeIf { it.isNotBlank() && code in 400..499 }?.let { ": $it" }.orEmpty())
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    private val refreshLock = Mutex()
    @Volatile private var latest: ArchidektLogin? = null

    /** Refreshes the token once for all requests running at the same time. */
    private suspend fun refreshShared(stale: ArchidektLogin): ArchidektLogin = refreshLock.withLock {
        latest?.takeIf { it.userId == stale.userId && it.token != stale.token && !expiresSoon(it.token) } ?: refresh(stale).also { latest = it }
    }

    private fun refresh(login: ArchidektLogin): ArchidektLogin {
        if (login.refresh.isBlank()) throw ArchidektAuthException("Archidekt no longer accepts this login. Log in to Archidekt again.")
        val body = buildJsonObject { put("refresh", login.refresh) }
        val req = Request.Builder().url(url("/api/rest-auth/token/refresh/")).header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonType)).build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ArchidektAuthException("Archidekt no longer accepts this login. Log in to Archidekt again.")
            val o = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            val token = (o["access"] ?: o["token"])?.jsonPrimitive?.contentOrNull ?: throw ArchidektAuthException("Archidekt no longer accepts this login. Log in to Archidekt again.")
            login.copy(token = token, refresh = o["refresh"]?.jsonPrimitive?.contentOrNull ?: o["refresh_token"]?.jsonPrimitive?.contentOrNull ?: login.refresh)
                .also { onTokens?.invoke(it) }
        }
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
        private const val PAGE = 250
        private const val MAX_PAGES = 400

        /** Whether a JWT runs out within a minute (or can't be read, in which case the server will say). */
        fun expiresSoon(token: String, now: Long = System.currentTimeMillis()): Boolean {
            val exp = runCatching {
                val payload = token.split('.')[1]
                val text = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
                Json.parseToJsonElement(text).jsonObject["exp"]?.jsonPrimitive?.longOrNull
            }.getOrNull() ?: return false
            return exp * 1000 < now + 60_000
        }
    }
}
