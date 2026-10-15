package com.mtgtrader.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
 * The worst condition a CardTrader offer may be in to count, on CardTrader's own scale (its "Slightly
 * Played" is Cardmarket's Excellent, see [CardTraderOrder.condition]). Since 1.37.
 */
enum class CtCondition(val key: String, val label: String, val rank: Int) {
    NEAR_MINT("NM", "Near Mint or better", 1),
    SLIGHTLY_PLAYED("SP", "Slightly Played (Excellent) or better", 2),
    MODERATELY_PLAYED("MP", "Moderately Played (Light Played) or better", 3),
    PLAYED("PL", "Played or better", 4),
    ANY("ANY", "Any condition", Int.MAX_VALUE);

    companion object {
        val DEFAULT = SLIGHTLY_PLAYED
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DEFAULT

        /** CardTrader's conditions, best first; one it doesn't list counts as the worst. */
        fun rankOf(condition: String?): Int = when (condition?.trim()?.lowercase()) {
            "mint" -> 0
            "near mint" -> 1
            "slightly played" -> 2
            "moderately played" -> 3
            "played" -> 4
            "heavily played" -> 5
            else -> 6
        }

        /** "SP" for "Slightly Played": short, for a card's price line. */
        fun short(condition: String?): String = when (condition?.trim()?.lowercase()) {
            "mint" -> "M"
            "near mint" -> "NM"
            "slightly played" -> "SP"
            "moderately played" -> "MP"
            "played" -> "PL"
            "heavily played" -> "HP"
            "poor" -> "PO"
            else -> condition.orEmpty()
        }
    }
}

/** The languages CardTrader lists Magic cards in (its codes); "" is any language. Since 1.37. */
val CT_LANGUAGES = listOf(
    "" to "Any language",
    "en" to "English",
    "de" to "German",
    "fr" to "French",
    "it" to "Italian",
    "es" to "Spanish",
    "pt" to "Portuguese",
    "jp" to "Japanese",
    "kr" to "Korean",
    "ru" to "Russian",
    "zh-CN" to "Chinese (simplified)",
    "zh-TW" to "Chinese (traditional)",
)

/** A Scryfall printing's CardTrader blueprint (its product page), kept on the phone. Since 1.37. */
@Entity(tableName = "ct_blueprints")
data class CtBlueprint(@PrimaryKey val scryfallId: String, val blueprintId: Long, val expansionId: Long)

@Dao
interface CtBlueprintDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putAll(rows: List<CtBlueprint>)

    @Query("SELECT * FROM ct_blueprints WHERE scryfallId = :id")
    suspend fun get(id: String): CtBlueprint?
}

/** One offer on CardTrader's marketplace, as its API describes it. */
data class CtListing(
    val cents: Long,
    val currency: String,
    val condition: String?,
    val language: String?,
    val foil: Boolean,
    val signed: Boolean = false,
    val altered: Boolean = false,
    val graded: Boolean = false,
    val onVacation: Boolean = false,
    val bundleSize: Int = 1,
    val quantity: Int = 1,
    /** Can be bought through CardTrader Zero (CardTrader collects it and ships it with your other CT Zero cards). */
    val zero: Boolean = false,
)

/** What counts as an offer for a wishlist card: language ("" any), worst condition, CardTrader Zero only. */
data class CtFilter(val language: String, val condition: CtCondition, val zeroOnly: Boolean) {
    /** Stored with a found price, so a price found with another filter (or finish) isn't used. */
    fun key(foil: Boolean) = "${language.ifEmpty { "any" }}|${condition.key}|${if (zeroOnly) "zero" else "all"}|${if (foil) "foil" else "normal"}"
}

/** CardTrader's cheapest matching offer for a wishlist card, in euros. */
data class CtOffer(val eur: Double, val blueprintId: Long, val setCode: String, val condition: String?, val language: String?)

/**
 * What CardTrader says about a wishlist card: the cheapest offer that fits ([offer]), a blueprint to
 * link to, and with "only CardTrader Zero" also the cheapest offer from any seller ([anySeller]) and,
 * when no CT Zero offer was among the offers CardTrader shows, what a CT Zero copy costs at least
 * ([zeroOver]; see [CardTraderParse.pick]).
 */
data class CtResult(val offer: CtOffer? = null, val blueprintId: Long? = null, val anySeller: Double? = null, val zeroOver: Double? = null)

/** One printing's offers, read: see [CardTraderParse.pick]. */
data class CtPick(val best: Pair<CtListing, Double>?, val anySeller: Pair<CtListing, Double>?, val zeroOver: Double?)

/** Reading CardTrader's API answers and picking offers: no phone or network needed, so it can be tested. Since 1.37. */
object CardTraderParse {
    private val json = Json { ignoreUnknownKeys = true }

    /** Magic's expansions (game 1): code (lower case, like Scryfall's set codes) → id. */
    fun expansions(body: String): Map<String, Long> = json.parseToJsonElement(body).jsonArray.mapNotNull { e ->
        val o = e.jsonObject
        if (o["game_id"]?.jsonPrimitive?.intOrNull != 1) return@mapNotNull null
        val code = o["code"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return@mapNotNull null
        val id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        code to id
    }.toMap()

    /** An expansion's blueprints that Scryfall knows (the first one where several share a printing). */
    fun blueprints(body: String, expansionId: Long): List<CtBlueprint> = json.parseToJsonElement(body).jsonArray.mapNotNull { e ->
        val o = e.jsonObject
        val scryfall = o["scryfall_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        CtBlueprint(scryfall, id, expansionId)
    }.sortedBy { it.blueprintId }.distinctBy { it.scryfallId }

    /** The offers in a marketplace answer: an object of blueprint id → its cheapest offers. */
    fun listings(body: String): List<CtListing> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        return root.values.flatMap { v -> (v as? JsonArray).orEmpty() }.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val price = o["price"] as? JsonObject
            val cents = price?.get("cents")?.jsonPrimitive?.longOrNull ?: o["price_cents"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val currency = price?.get("currency")?.jsonPrimitive?.contentOrNull ?: o["price_currency"]?.jsonPrimitive?.contentOrNull ?: "EUR"
            val props = o["properties_hash"] as? JsonObject
            fun prop(name: String) = props?.get(name)?.jsonPrimitive
            fun flag(el: kotlinx.serialization.json.JsonElement?) = (el as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: false
            CtListing(
                cents = cents,
                currency = currency,
                condition = prop("condition")?.contentOrNull,
                language = prop("mtg_language")?.contentOrNull,
                foil = prop("mtg_foil")?.booleanOrNull ?: false,
                signed = prop("signed")?.booleanOrNull ?: false,
                altered = prop("altered")?.booleanOrNull ?: false,
                graded = flag(o["graded"]),
                onVacation = flag(o["on_vacation"]),
                bundleSize = o["bundle_size"]?.jsonPrimitive?.intOrNull ?: 1,
                quantity = o["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                zero = flag((o["user"] as? JsonObject)?.get("can_sell_via_hub")),
            )
        }
    }

    /**
     * The cheapest offer that is what's wanted: the finish, language and condition of [filter], a
     * single plain copy (not graded, signed, altered or part of a bundle) from a seller who isn't on
     * holiday, and with [CtFilter.zeroOnly] one that can be bought through CardTrader Zero. [toEur]
     * converts the price (CardTrader answers in the account's currency); an offer it can't convert is
     * left out.
     */
    /** How many offers CardTrader shows per printing (the cheapest ones). */
    const val PAGE = 25

    /**
     * Reads one printing's offers (asked in the wished language and finish, so the [PAGE] cheapest are
     * all relevant). With "only CardTrader Zero": the cheapest CT Zero offer, and the cheapest from any
     * seller too. When none of a full page is CT Zero, a CT Zero copy can only be among the offers not
     * shown, which all cost at least the dearest one shown: that's [CtPick.zeroOver]. A page that isn't
     * full is every offer there is, so then there's simply no CT Zero copy.
     */
    fun pick(listings: List<CtListing>, filter: CtFilter, foil: Boolean, toEur: (Double, String) -> Double?): CtPick {
        val best = cheapest(listings, filter, foil, toEur)
        if (!filter.zeroOnly) return CtPick(best, best, null)
        val any = cheapest(listings, filter.copy(zeroOnly = false), foil, toEur)
        val over = if (best == null && listings.size >= PAGE) listings.mapNotNull { toEur(it.cents / 100.0, it.currency) }.maxOrNull() else null
        return CtPick(best, any, over)
    }

    fun cheapest(listings: List<CtListing>, filter: CtFilter, foil: Boolean, toEur: (Double, String) -> Double?): Pair<CtListing, Double>? =
        listings.asSequence()
            .filter { it.foil == foil && !it.graded && !it.signed && !it.altered && !it.onVacation && it.bundleSize <= 1 && it.quantity > 0 }
            .filter { filter.language.isEmpty() || it.language.equals(filter.language, ignoreCase = true) }
            .filter { CtCondition.rankOf(it.condition) <= filter.condition.rank }
            .filter { !filter.zeroOnly || it.zero }
            .mapNotNull { l -> toEur(l.cents / 100.0, l.currency)?.let { l to it } }
            .minByOrNull { it.second }
}

class CardTraderAuthException : IOException("CardTrader didn't accept the API token: check it in Settings → Wishlist alerts")

/**
 * CardTrader's prices for wishlist cards, through its API with the owner's personal token (kept
 * encrypted on this phone, never synced). A wishlist card's printings are found as CardTrader
 * "blueprints" by their Scryfall id (CardTrader's expansion codes are Scryfall's set codes); the
 * marketplace then gives each blueprint's 25 cheapest offers. Since 1.37.
 */
class CardTrader(
    context: Context,
    private val http: OkHttpClient,
    private val db: AppDatabase,
    private val baseUrl: String = BASE_URL,
) {
    private val prefs = context.getSharedPreferences("cardtrader", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<Status> = _status

    /** What Settings shows: whether a token is saved, and how the last lookups went. */
    data class Status(val hasToken: Boolean = false, val okAt: Long = 0, val error: String? = null)

    private fun readStatus() = Status(prefs.contains(K_TOKEN), prefs.getLong(K_OK_AT, 0), prefs.getString(K_ERROR, null))

    val token: String? get() = prefs.getString(K_TOKEN, null)?.let(SecretStore::decrypt)

    /** Saves (or with null/blank, forgets) the token. */
    fun setToken(t: String?) {
        val e = prefs.edit()
        if (t.isNullOrBlank()) e.remove(K_TOKEN) else e.putString(K_TOKEN, SecretStore.encrypt(t.trim()))
        e.remove(K_ERROR).remove(K_OK_AT).apply()
        _status.value = readStatus()
    }

    /** Asks CardTrader whether [t] works (its "test the authentication" call). */
    suspend fun check(t: String): Result<Unit> = runCatching { get(t, "info") }.map { }.also { r ->
        record(r.exceptionOrNull())
    }

    /** Remembers how the last call went, for Settings. */
    fun record(error: Throwable?) {
        val e = prefs.edit()
        if (error == null) e.putLong(K_OK_AT, System.currentTimeMillis()).remove(K_ERROR)
        else e.putString(K_ERROR, error.message ?: error.javaClass.simpleName)
        e.apply()
        _status.value = readStatus()
    }

    /**
     * CardTrader's cheapest offer among [printings] (most promising first; at most [MAX_PRINTINGS] are
     * looked up), and a blueprint to link to even when there's no offer (see [CtResult]). Throws on
     * network trouble or a refused token.
     */
    suspend fun cheapest(
        t: String,
        printings: List<WishPrinting>,
        foil: Boolean,
        filter: CtFilter,
        toEur: (Double, String) -> Double?,
    ): CtResult {
        var first: Long? = null
        var best: CtOffer? = null
        var any: Double? = null
        var over: Double? = null
        for (p in printings.take(MAX_PRINTINGS)) {
            if (foil && !p.foil && !p.etched) continue
            if (!foil && !p.nonfoil) continue
            val bp = blueprint(t, p) ?: continue
            if (first == null) first = bp
            val pick = CardTraderParse.pick(products(t, bp, foil, filter.language), filter, foil, toEur)
            pick.anySeller?.let { (_, eur) -> if (any == null || eur < any!!) any = eur }
            pick.zeroOver?.let { if (over == null || it < over!!) over = it }
            val (l, eur) = pick.best ?: continue
            if (best == null || eur < best.eur) best = CtOffer(eur, bp, p.set, l.condition, l.language)
        }
        // A CT Zero offer found is one you can buy; the "at least" only matters without one.
        return CtResult(best, best?.blueprintId ?: first, any.takeIf { filter.zeroOnly }, over.takeIf { best == null })
    }

    /** A printing's blueprint: from the phone, else from its expansion's list (fetched at most weekly). */
    private suspend fun blueprint(t: String, p: WishPrinting): Long? {
        val dao = db.ctBlueprintDao()
        dao.get(p.id)?.let { return it.blueprintId }
        val expansion = expansions(t)[p.set.lowercase()] ?: return null
        val key = "$K_BP_AT$expansion"
        if (System.currentTimeMillis() - prefs.getLong(key, 0) < WEEK_MS) return null
        dao.putAll(CardTraderParse.blueprints(get(t, "blueprints/export", "expansion_id" to "$expansion"), expansion))
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        return dao.get(p.id)?.blueprintId
    }

    /** Magic's expansions by code, kept for a week. */
    private suspend fun expansions(t: String): Map<String, Long> {
        val saved = prefs.getString(K_EXPANSIONS, null)
        if (saved != null && System.currentTimeMillis() - prefs.getLong(K_EXPANSIONS_AT, 0) < WEEK_MS) return decode(saved)
        val fresh = runCatching { CardTraderParse.expansions(get(t, "expansions")) }.getOrElse { e ->
            if (e is CardTraderAuthException || saved == null) throw e else return decode(saved)
        }
        prefs.edit().putString(K_EXPANSIONS, fresh.entries.joinToString(";") { "${it.key}=${it.value}" })
            .putLong(K_EXPANSIONS_AT, System.currentTimeMillis()).apply()
        return fresh
    }

    private fun decode(s: String): Map<String, Long> =
        s.split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toLongOrNull()?.let { k to it } } }.toMap()

    /**
     * A blueprint's cheapest offers in the finish, asked in [language] where one is chosen. Should
     * CardTrader not take that language code, it's asked without (the offers are filtered here anyway).
     */
    private suspend fun products(t: String, blueprintId: Long, foil: Boolean, language: String): List<CtListing> {
        val base = arrayOf("blueprint_id" to "$blueprintId", "foil" to "$foil")
        val body = if (language.isEmpty()) get(t, "marketplace/products", *base)
        else try {
            get(t, "marketplace/products", *base, "language" to language)
        } catch (e: CtHttpException) {
            if (e.code in 400..499) get(t, "marketplace/products", *base) else throw e
        }
        return CardTraderParse.listings(body)
    }

    private class CtHttpException(val code: Int) : IOException("CardTrader error $code")

    /**
     * One API call. Calls are spaced out (CardTrader allows 10 marketplace calls a second); a "too many
     * requests" answer slows them down further and is tried again, twice.
     */
    private suspend fun get(t: String, path: String, vararg query: Pair<String, String>): String {
        val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        var tries = 0
        while (true) {
            lock.withLock {
                val wait = lastCall + gapMs - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastCall = System.currentTimeMillis()
            }
            val request = Request.Builder().url(url).header("Authorization", "Bearer $t").header("Accept", "application/json").build()
            val (code, body) = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { r -> r.code to (if (r.isSuccessful) r.body?.string() else null) }
            }
            when {
                code in 200..299 -> return body.orEmpty()
                code == 401 || code == 403 -> throw CardTraderAuthException()
                code == 429 && tries < 2 -> {
                    tries++
                    gapMs = (gapMs * 2).coerceAtMost(MAX_GAP_MS)
                    delay(gapMs * 4)
                }
                else -> throw CtHttpException(code)
            }
        }
    }

    @Volatile private var lastCall = 0L
    @Volatile private var gapMs = START_GAP_MS

    companion object {
        const val BASE_URL = "https://api.cardtrader.com/api/v2"

        /** A blueprint's page on CardTrader's website. */
        fun pageUrl(blueprintId: Long) = "https://www.cardtrader.com/en-EU/cards/$blueprintId"

        /** Printings looked up per wishlist card, so a card with a hundred printings doesn't take minutes. */
        const val MAX_PRINTINGS = 25

        private const val START_GAP_MS = 250L
        private const val MAX_GAP_MS = 2_000L
        private const val WEEK_MS = 7L * 24 * 3600 * 1000
        private const val K_TOKEN = "token"
        private const val K_OK_AT = "okAt"
        private const val K_ERROR = "error"
        private const val K_EXPANSIONS = "expansions"
        private const val K_EXPANSIONS_AT = "expansionsAt"
        private const val K_BP_AT = "blueprintsAt_"
    }
}
