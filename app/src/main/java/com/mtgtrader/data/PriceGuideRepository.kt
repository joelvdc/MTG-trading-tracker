package com.mtgtrader.data

import android.content.Context
import com.mtgtrader.USER_AGENT
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** The website price file's header: Cardmarket's date for the guide, when the website made the file, and the ECB rates. */
data class WebPriceHead(val createdAt: String?, val madeAt: Long, val rates: ExchangeRates?)

/**
 * Reads the website's price file (web/src/lib/prices.ts PriceFile: {"createdAt", "madeAt", "rates": {"date",
 * "perEuro"}, "products": {"<id>": [trend, avg, low, avg1, avg7, avg30, then the same for foil]}}), one product at a
 * time. Zero or missing prices become null, as in Cardmarket's own file. Since 1.81.
 */
suspend fun readWebPrices(input: InputStream, emit: suspend (PriceEntity) -> Unit): WebPriceHead {
    var createdAt: String? = null
    var madeAt = 0L
    var rates: ExchangeRates? = null
    JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "createdAt" -> createdAt = if (r.peek() == JsonToken.STRING) r.nextString() else { r.skipValue(); null }
                "madeAt" -> madeAt = if (r.peek() == JsonToken.NUMBER) r.nextLong() else { r.skipValue(); 0L }
                "rates" -> rates = readRates(r)
                "products" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        val id = r.nextName().toIntOrNull()
                        val v = arrayOfNulls<Double>(12)
                        if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); continue }
                        r.beginArray()
                        var i = 0
                        while (r.hasNext()) {
                            val x = if (r.peek() == JsonToken.NUMBER) r.nextDouble().takeIf { it > 0 } else { r.skipValue(); null }
                            if (i < 12) v[i] = x
                            i++
                        }
                        r.endArray()
                        if (id != null) emit(
                            PriceEntity(
                                idProduct = id,
                                trend = v[0], avg = v[1], low = v[2], avg1 = v[3], avg7 = v[4], avg30 = v[5],
                                trendFoil = v[6], avgFoil = v[7], lowFoil = v[8], avg1Foil = v[9], avg7Foil = v[10], avg30Foil = v[11],
                            )
                        )
                    }
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
    }
    return WebPriceHead(createdAt, madeAt, rates)
}

private fun readRates(r: JsonReader): ExchangeRates? {
    if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return null }
    var date = ""
    val perEuro = HashMap<String, Double>()
    r.beginObject()
    while (r.hasNext()) {
        when (r.nextName()) {
            "date" -> date = if (r.peek() == JsonToken.STRING) r.nextString() else { r.skipValue(); "" }
            "perEuro" -> {
                r.beginObject()
                while (r.hasNext()) {
                    val k = r.nextName()
                    if (r.peek() == JsonToken.NUMBER) r.nextDouble().takeIf { it > 0 }?.let { perEuro[k] = it } else r.skipValue()
                }
                r.endObject()
            }
            else -> r.skipValue()
        }
    }
    r.endObject()
    return if (date.isNotBlank() && perEuro.isNotEmpty()) ExchangeRates(date, perEuro) else null
}

sealed interface PriceUpdateState {
    data object Idle : PriceUpdateState
    data class Running(val message: String) : PriceUpdateState
    data class Failed(val message: String) : PriceUpdateState
}

/**
 * Downloads Cardmarket's public daily price guide for Magic (all products, keyed by Cardmarket id)
 * and stores it locally. New sets appear here automatically — no app update needed.
 */
class PriceGuideRepository(
    private val context: Context,
    private val http: OkHttpClient,
    private val db: AppDatabase,
    private val settings: Settings,
    /** The website and this phone's login there, when it syncs through a website account (since 1.81). */
    private val web: () -> Pair<WebAccountClient, WebLogin>? = { null },
    private val rates: ExchangeRateStore? = null,
) {
    companion object {
        const val URL = "https://downloads.s3.cardmarket.com/productCatalog/priceGuide/price_guide_1.json"
        const val VIA_WEBSITE = "website"
        const val VIA_CARDMARKET = "cardmarket"
        private const val MAX_AGE_MS = 20L * 60 * 60 * 1000
        /** The website makes its file every night; one older than this means its job is stuck: go to Cardmarket. */
        private const val WEB_MAX_AGE_MS = 36L * 60 * 60 * 1000
        /** Prices saved per transaction while importing (see [import]). */
        private const val BATCH = 5000
    }

    private val dao = db.priceDao()
    private val lock = Mutex()
    private val _state = MutableStateFlow<PriceUpdateState>(PriceUpdateState.Idle)
    val state: StateFlow<PriceUpdateState> = _state
    val count = dao.count()

    val isStale get() = System.currentTimeMillis() - settings.lastPriceFetch.value > MAX_AGE_MS

    /**
     * Updates prices if they're out of date. Several triggers can ask at once (opening the app, the
     * phone reporting its network, the background job), so the age is checked again once the
     * previous update has finished: then the ones that waited find fresh prices and skip.
     */
    suspend fun refreshIfStale(): Boolean = refresh(onlyIfStale = true)

    suspend fun refresh(onlyIfStale: Boolean = false): Boolean = lock.withLock {
        if (onlyIfStale && !isStale) return@withLock true
        withContext(Dispatchers.IO) {
            // Logged in to a website account: the website's small nightly copy first, Cardmarket's own file if that fails.
            if (fromWebsite()) return@withContext true
            val tmp = File(context.cacheDir, "price_guide.json")
            try {
                // Mobile connections drop or switch networks mid-download; retry a few times.
                var attempt = 1
                while (true) {
                    try {
                        download(tmp)
                        break
                    } catch (e: IOException) {
                        if (attempt >= 3) throw e
                        _state.value = PriceUpdateState.Running("Connection lost, retrying…")
                        delay(2000L * attempt)
                        attempt++
                    }
                }
                _state.value = PriceUpdateState.Running("Saving prices…")
                val createdAt = import(tmp)
                settings.setPriceGuideFetched(createdAt, System.currentTimeMillis(), VIA_CARDMARKET)
                _state.value = PriceUpdateState.Idle
                true
            } catch (e: Exception) {
                _state.value = PriceUpdateState.Failed(e.message ?: e.javaClass.simpleName)
                false
            } finally {
                tmp.delete()
            }
        }
    }

    /**
     * The prices from the website's nightly file (since 1.81): asks only for a newer file than the one saved. False when
     * not logged in, when the website can't be reached, or when its file is over [WEB_MAX_AGE_MS] old; the caller then
     * downloads Cardmarket's own file.
     */
    private suspend fun fromWebsite(): Boolean {
        val (client, login) = web() ?: return false
        val tmp = File(context.cacheDir, "web_prices.json.gz")
        try {
            _state.value = PriceUpdateState.Running("Downloading prices from the website…")
            val etag = settings.webPriceEtag.takeIf { settings.priceVia.value == VIA_WEBSITE }
            suspend fun fetch() = client.prices(login, etag, tmp) { done, total ->
                val of = if (total > 0) " of ${(total + 524_288) / 1_048_576} MB" else ""
                _state.value = PriceUpdateState.Running("Downloading prices from the website… ${done / 1_048_576} MB$of")
            }
            // One more try when the connection drops (not when the website refuses the login).
            val got = try {
                fetch()
            } catch (e: IOException) {
                delay(2000)
                fetch()
            }
            val now = System.currentTimeMillis()
            when (got) {
                WebAccountClient.PriceDownload.NotModified -> {
                    if (now - settings.webPriceMadeAt > WEB_MAX_AGE_MS) return false
                    settings.setPriceGuideFetched(settings.priceGuideDate.value, now, VIA_WEBSITE)
                }
                is WebAccountClient.PriceDownload.Saved -> {
                    _state.value = PriceUpdateState.Running("Saving prices…")
                    val listed = HashSet<Int>()
                    val batch = ArrayList<PriceEntity>(BATCH)
                    val head = GZIPInputStream(tmp.inputStream().buffered()).use { input ->
                        readWebPrices(input) { p ->
                            batch += p
                            listed += p.idProduct
                            if (batch.size >= BATCH) {
                                dao.insertAll(batch)
                                batch.clear()
                            }
                        }
                    }
                    if (batch.isNotEmpty()) dao.insertAll(batch)
                    if (listed.isEmpty()) throw IOException("The website's price file was empty")
                    dao.allIds().filter { it !in listed }.chunked(900).forEach { dao.deleteIds(it) }
                    head.rates?.let { rates?.save(it) }
                    settings.webPriceEtag = got.etag
                    settings.webPriceMadeAt = head.madeAt
                    settings.setPriceGuideFetched(head.createdAt, now, VIA_WEBSITE)
                    // Saved, but from a stuck job: Cardmarket's own file is newer.
                    if (now - head.madeAt > WEB_MAX_AGE_MS) return false
                }
            }
            _state.value = PriceUpdateState.Idle
            return true
        } catch (e: Exception) {
            // Cardmarket's own file comes next; its result is what Settings shows.
            return false
        } finally {
            tmp.delete()
        }
    }

    private fun download(target: File) {
        _state.value = PriceUpdateState.Running("Downloading Cardmarket prices…")
        val req = Request.Builder().url(URL).header("User-Agent", USER_AGENT).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastMb = -1L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val mb = done / (1024 * 1024)
                        if (mb != lastMb) {
                            lastMb = mb
                            val of = if (total > 0) " of ${total / (1024 * 1024)} MB" else " MB"
                            _state.value = PriceUpdateState.Running("Downloading Cardmarket prices… $mb$of")
                        }
                    }
                }
            }
        }
    }

    /**
     * Replaces the price table with the file's contents. Returns the guide's createdAt. Since 1.66 the prices are
     * saved in batches, each in a transaction of its own, and the products no longer listed are removed at the end:
     * one transaction for the whole guide held the database, and the collection, opened meanwhile, showed only a
     * loading circle until it was done.
     */
    private suspend fun import(file: File): String? {
        var createdAt: String? = null
        val listed = HashSet<Int>()
        JsonReader(InputStreamReader(file.inputStream().buffered(), Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "createdAt" -> createdAt = reader.nextString()
                    "priceGuides" -> {
                        val batch = ArrayList<PriceEntity>(BATCH)
                        reader.beginArray()
                        while (reader.hasNext()) {
                            readEntry(reader)?.let { batch += it; listed += it.idProduct }
                            if (batch.size >= BATCH) {
                                dao.insertAll(batch)
                                batch.clear()
                            }
                        }
                        reader.endArray()
                        if (batch.isNotEmpty()) dao.insertAll(batch)
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        // An empty guide would leave no prices at all: keep the old ones instead.
        if (listed.isEmpty()) throw IOException("The price guide was empty")
        dao.allIds().filter { it !in listed }.chunked(900).forEach { dao.deleteIds(it) }
        return createdAt
    }

    private fun readEntry(r: JsonReader): PriceEntity? {
        var id: Int? = null
        val v = HashMap<String, Double?>(16)
        r.beginObject()
        while (r.hasNext()) {
            val name = r.nextName()
            if (r.peek() == JsonToken.NULL) {
                r.nextNull()
                continue
            }
            when (name) {
                "idProduct" -> id = r.nextInt()
                "avg", "low", "trend", "avg1", "avg7", "avg30",
                "avg-foil", "low-foil", "trend-foil", "avg1-foil", "avg7-foil", "avg30-foil" ->
                    v[name] = if (r.peek() == JsonToken.NUMBER) r.nextDouble().takeIf { it > 0 } else { r.skipValue(); null }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return id?.let {
            PriceEntity(
                idProduct = it,
                avg = v["avg"], low = v["low"], trend = v["trend"],
                avg1 = v["avg1"], avg7 = v["avg7"], avg30 = v["avg30"],
                avgFoil = v["avg-foil"], lowFoil = v["low-foil"], trendFoil = v["trend-foil"],
                avg1Foil = v["avg1-foil"], avg7Foil = v["avg7-foil"], avg30Foil = v["avg30-foil"],
            )
        }
    }

    suspend fun priceFor(cardmarketId: Int?, foil: Boolean): PriceSet? =
        cardmarketId?.let { dao.get(it) }?.toSet(foil)

    suspend fun pricesFor(ids: Collection<Int>): Map<Int, PriceEntity> =
        ids.distinct().chunked(500).flatMap { dao.getMany(it) }.associateBy { it.idProduct }
}
