package com.mtgtrader.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** Where a price comes from. Cardmarket's are in euros; the others in US dollars. Since 1.29. */
enum class PriceSource(val key: String, val label: String) {
    CARDMARKET("cardmarket", "Cardmarket"),
    TCGPLAYER("tcgplayer", "TCGplayer"),
    CARD_KINGDOM("cardkingdom", "Card Kingdom");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: CARDMARKET
    }
}

/**
 * One printing's price in one finish at TCGplayer or Card Kingdom, in US dollars, kept on the phone
 * (not synced). TCGplayer has one market price ([price]); Card Kingdom a price per condition
 * ([nm], [ex], [vg], [g]) and what it pays ([buy]). Since 1.29.
 */
@Entity(tableName = "source_prices", primaryKeys = ["scryfallId", "source", "finish"])
data class SourcePrice(
    val scryfallId: String,
    val source: String,
    /** [Finish] name. */
    val finish: String,
    val price: Double?,
    val nm: Double? = null,
    val ex: Double? = null,
    val vg: Double? = null,
    val g: Double? = null,
    val buy: Double? = null,
    /** The card's page there. */
    val url: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface SourcePriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(rows: List<SourcePrice>)

    @Query("SELECT * FROM source_prices WHERE scryfallId IN (:ids)")
    suspend fun forIds(ids: List<String>): List<SourcePrice>

    @Query("DELETE FROM source_prices WHERE source = :source AND updatedAt < :before")
    suspend fun deleteOlder(source: String, before: Long)

    @Query("SELECT COUNT(*) FROM source_prices WHERE source = :source")
    suspend fun count(source: String): Int
}

/** A printing's prices at TCGplayer and Card Kingdom (US dollars), by finish. */
data class OtherPrices(val rows: Map<Pair<PriceSource, Finish>, SourcePrice> = emptyMap()) {
    fun row(source: PriceSource, finish: Finish) = rows[source to finish]

    companion object {
        fun of(rows: List<SourcePrice>): OtherPrices = OtherPrices(
            rows.mapNotNull { r ->
                val s = PriceSource.fromKey(r.source).takeIf { it != PriceSource.CARDMARKET } ?: return@mapNotNull null
                val f = runCatching { Finish.valueOf(r.finish) }.getOrNull() ?: return@mapNotNull null
                (s to f) to r
            }.toMap(),
        )
    }
}

/**
 * The price of one copy, from the source chosen in Settings, in euros. Card Kingdom's follows the
 * copy's condition; TCGplayer and Cardmarket have one price per card. A card the chosen source
 * has no price for falls back to Cardmarket's ([isApprox]). Compose state, so screens redraw
 * when the source or the prices change. Since 1.29.
 */
object Pricing {
    var source by mutableStateOf(PriceSource.CARDMARKET)

    /** How many US dollars one euro buys; null until the exchange rates are known. */
    var usdPerEuro by mutableStateOf<Double?>(null)

    /** Scryfall id → its prices at the other sources (the cards loaded so far). */
    var others by mutableStateOf<Map<String, OtherPrices>>(emptyMap())

    /** Card Kingdom's grades (NM, EX, VG, G) for the app's, by way of the TCGplayer scale Archidekt uses too. */
    fun ckCondition(condition: String): String = when (ArchidektCodes.archCondition(condition)) {
        "NM" -> "nm"
        "LP" -> "ex"
        "MP" -> "vg"
        else -> "g"
    }

    private fun toEur(usd: Double?): Double? {
        val rate = usdPerEuro ?: return null
        return usd?.takeIf { it > 0 }?.div(rate)
    }

    /** A copy's price at [source], in euros; null when it has none. [cardmarket] is Cardmarket's price for that copy. */
    fun at(source: PriceSource, scryfallId: String, finish: Finish, condition: String, cardmarket: Double?): Double? = when (source) {
        PriceSource.CARDMARKET -> cardmarket
        PriceSource.TCGPLAYER -> toEur(others[scryfallId]?.row(source, finish)?.price)
        PriceSource.CARD_KINGDOM -> {
            val r = others[scryfallId]?.row(source, finish)
            toEur(
                when (ckCondition(condition)) {
                    "nm" -> r?.nm
                    "ex" -> r?.ex
                    "vg" -> r?.vg
                    else -> r?.g
                } ?: r?.price,
            )
        }
    }

    /** The price of one copy from the chosen source, or Cardmarket's when that source has none. */
    fun unit(scryfallId: String, finish: Finish, condition: String, cardmarket: Double?): Double? =
        at(source, scryfallId, finish, condition, cardmarket) ?: cardmarket

    /** True when [unit] fell back to Cardmarket's price (shown with "≈"). */
    fun isApprox(scryfallId: String, finish: Finish, condition: String): Boolean =
        source != PriceSource.CARDMARKET && at(source, scryfallId, finish, condition, null) == null

    /** What Card Kingdom pays for one copy (its buylist), in euros. */
    fun cardKingdomPays(scryfallId: String, finish: Finish): Double? = toEur(others[scryfallId]?.row(PriceSource.CARD_KINGDOM, finish)?.buy)

    fun url(source: PriceSource, scryfallId: String, finish: Finish): String? =
        others[scryfallId]?.let { it.row(source, finish) ?: it.row(source, Finish.NONFOIL) ?: it.row(source, Finish.FOIL) }?.url
}

/**
 * Downloads TCGplayer's and Card Kingdom's prices and keeps them in [SourcePrice] rows:
 * Card Kingdom's public price list (every card, ~10 MB a day) and TCGplayer's market prices,
 * which come with Scryfall's card data (for the cards you have, plus every card Scryfall sends
 * the app anyway, e.g. in a search). Since 1.29.
 */
class PriceSourceStore(context: Context, private val http: OkHttpClient, private val db: AppDatabase, private val scryfall: ScryfallApi) {
    private val dao = db.sourcePriceDao()
    private val prefs = context.getSharedPreferences("price_sources", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<SourceStatus> = _status

    data class SourceStatus(val tcgplayerAt: Long = 0, val cardKingdomAt: Long = 0, val running: Boolean = false, val error: String? = null)

    private fun readStatus() = SourceStatus(prefs.getLong(K_TCG_AT, 0), prefs.getLong(K_CK_AT, 0))

    val isStale get() = System.currentTimeMillis() - minOf(prefs.getLong(K_TCG_AT, 0), prefs.getLong(K_CK_AT, 0)) > MAX_AGE_MS

    /** Loads the saved prices of the cards you have (collection, wishlist, trades, scans) into [Pricing]. */
    suspend fun loadOwned() {
        val ids = ownedIds()
        load(ids)
    }

    /** Loads the saved prices of [ids] into [Pricing] (for cards shown elsewhere, e.g. search results). */
    suspend fun load(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val rows = ids.distinct().chunked(900).flatMap { dao.forIds(it) }
        val byId = rows.groupBy { it.scryfallId }
        val loaded = ids.associateWith { OtherPrices.of(byId[it].orEmpty()) }
        Pricing.others = Pricing.others + loaded
    }

    private suspend fun ownedIds(): List<String> {
        val d = db.syncDao()
        return (d.collection().map { it.card.scryfallId } + d.wishlist().map { it.card.scryfallId } +
            d.tradeItems().map { it.card.scryfallId } + d.scans().map { it.card.scryfallId }).distinct()
    }

    /** TCGplayer prices from cards Scryfall sent for another reason: saved, and shown right away. */
    suspend fun fromScryfall(cards: List<ScryCard>) {
        val rows = cards.flatMap(::tcgplayerRows)
        if (rows.isNotEmpty()) dao.putAll(rows)
        load(cards.map { it.id })
    }

    /** Fetches both sources if they're over a day old. */
    suspend fun refreshIfStale() {
        if (isStale) refresh()
    }

    /** Fetches TCGplayer's prices for the cards you have, and Card Kingdom's whole list. */
    suspend fun refresh(): Boolean = lock.withLock {
        _status.value = _status.value.copy(running = true, error = null)
        val errors = mutableListOf<String>()
        try {
            val ids = ownedIds()
            val cards = scryfall.collection(ids.map { ScryfallApi.idIdentifier(it) })
            val start = System.currentTimeMillis()
            dao.putAll(cards.flatMap(::tcgplayerRows))
            prefs.edit().putLong(K_TCG_AT, start).apply()
        } catch (e: Exception) {
            errors += "TCGplayer: ${e.message ?: e.javaClass.simpleName}"
        }
        try {
            val start = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(CARD_KINGDOM_URL).header("Accept-Encoding", "gzip").build()
                http.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                    val body = r.body ?: throw java.io.IOException("empty answer")
                    // OkHttp unzips by itself unless the header was set by hand, as here: unzip if it's zipped.
                    val raw = body.byteStream().buffered()
                    raw.mark(2)
                    val zipped = raw.read() == 0x1f && raw.read() == 0x8b
                    raw.reset()
                    val stream = if (zipped) GZIPInputStream(raw) else raw
                    val batch = ArrayList<SourcePrice>(2000)
                    db.withTransaction {
                        readCardKingdom(stream, start) { row ->
                            batch += row
                            if (batch.size >= 2000) {
                                dao.putAll(batch.toList())
                                batch.clear()
                            }
                        }
                        if (batch.isNotEmpty()) dao.putAll(batch)
                        // Cards Card Kingdom no longer lists.
                        dao.deleteOlder(PriceSource.CARD_KINGDOM.key, start)
                    }
                }
            }
            prefs.edit().putLong(K_CK_AT, start).apply()
        } catch (e: Exception) {
            errors += "Card Kingdom: ${e.message ?: e.javaClass.simpleName}"
        }
        loadOwned()
        _status.value = readStatus().copy(error = errors.joinToString("; ").ifEmpty { null })
        errors.isEmpty()
    }

    private companion object {
        const val CARD_KINGDOM_URL = "https://api.cardkingdom.com/api/v2/pricelist"
        const val K_TCG_AT = "tcgplayerAt"
        const val K_CK_AT = "cardKingdomAt"
        const val MAX_AGE_MS = 20L * 60 * 60 * 1000
    }
}

/** A Scryfall card's TCGplayer market prices (normal, foil, etched), as [SourcePrice] rows. */
fun tcgplayerRows(c: ScryCard): List<SourcePrice> {
    val now = System.currentTimeMillis()
    val url = c.purchaseUris?.tcgplayer
    return listOfNotNull(
        c.prices.usd?.toDoubleOrNull()?.let { SourcePrice(c.id, PriceSource.TCGPLAYER.key, Finish.NONFOIL.name, it, url = url, updatedAt = now) },
        c.prices.usdFoil?.toDoubleOrNull()?.let { SourcePrice(c.id, PriceSource.TCGPLAYER.key, Finish.FOIL.name, it, url = url, updatedAt = now) },
        c.prices.usdEtched?.toDoubleOrNull()?.let { SourcePrice(c.id, PriceSource.TCGPLAYER.key, Finish.ETCHED.name, it, url = url, updatedAt = now) },
    )
}

/**
 * Reads Card Kingdom's price list ({"meta":…,"data":[{scryfall_id, is_foil, price_retail, price_buy,
 * url, variation, condition_values:{nm_price…}}…]}) one entry at a time, as [SourcePrice] rows. When
 * Card Kingdom lists a printing twice (a The List copy carries the original's Scryfall id), the
 * entry without a variation wins. Etched foils have no entry of their own, so they get none.
 */
suspend fun readCardKingdom(input: InputStream, now: Long, emit: suspend (SourcePrice) -> Unit) {
    val seen = HashMap<String, Boolean>() // id|finish → was a plain (no variation) entry
    val pending = LinkedHashMap<String, SourcePrice>()
    JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() != "data") {
                r.skipValue()
                continue
            }
            r.beginArray()
            while (r.hasNext()) {
                var id: String? = null
                var foil = false
                var retail: Double? = null
                var buy: Double? = null
                var url: String? = null
                var variation = ""
                val cond = HashMap<String, Double?>()
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "scryfall_id" -> id = r.stringOrNull()
                        "is_foil" -> foil = r.stringOrNull() == "true"
                        "price_retail" -> retail = r.stringOrNull()?.toDoubleOrNull()
                        "price_buy" -> buy = r.stringOrNull()?.toDoubleOrNull()
                        "url" -> url = r.stringOrNull()
                        "variation" -> variation = r.stringOrNull().orEmpty()
                        "condition_values" -> {
                            if (r.peek() == JsonToken.NULL) r.nextNull() else {
                                r.beginObject()
                                while (r.hasNext()) {
                                    val k = r.nextName()
                                    if (k.endsWith("_price")) cond[k.removeSuffix("_price")] = r.stringOrNull()?.toDoubleOrNull()?.takeIf { it > 0 } else r.skipValue()
                                }
                                r.endObject()
                            }
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                if (id.isNullOrBlank()) continue
                val finish = if (foil) Finish.FOIL else Finish.NONFOIL
                val key = "$id|$finish"
                val plain = variation.isBlank()
                if (seen[key] == true && !plain) continue
                if (seen[key] == false && !plain) continue
                seen[key] = plain
                pending[key] = SourcePrice(
                    id, PriceSource.CARD_KINGDOM.key, finish.name, retail?.takeIf { it > 0 },
                    nm = cond["nm"], ex = cond["ex"], vg = cond["vg"], g = cond["g"],
                    buy = buy?.takeIf { it > 0 }, url = url?.let { "https://www.cardkingdom.com/$it" }, updatedAt = now,
                )
                if (pending.size >= 4000) {
                    // Flush the oldest; a duplicate later in the list is rare and replaces it (REPLACE on insert).
                    val first = pending.keys.take(2000)
                    for (k in first) emit(pending.remove(k)!!)
                }
            }
            r.endArray()
        }
        r.endObject()
    }
    for (v in pending.values) emit(v)
}

private fun JsonReader.stringOrNull(): String? = when (peek()) {
    JsonToken.NULL -> { nextNull(); null }
    JsonToken.STRING, JsonToken.NUMBER -> nextString()
    JsonToken.BOOLEAN -> nextBoolean().toString()
    else -> { skipValue(); null }
}
