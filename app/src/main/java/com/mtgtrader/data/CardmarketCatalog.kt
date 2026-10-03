package com.mtgtrader.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

/** One Magic single from Cardmarket's public product list. Since 1.16. */
@Entity(tableName = "cm_products", indices = [Index("idExpansion"), Index("name")])
data class CmProduct(@PrimaryKey val idProduct: Int, val name: String, val idExpansion: Int)

/** Which Cardmarket expansions a Scryfall set is ("plst" → several "The List" expansions), comma separated. */
@Entity(tableName = "cm_set_expansions")
data class CmSetExpansions(@PrimaryKey val setCode: String, val idExpansions: String)

@Dao
interface CatalogDao {
    @Query("DELETE FROM cm_products")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CmProduct>)

    @Query("SELECT * FROM cm_products WHERE idProduct IN (:ids)")
    suspend fun getMany(ids: List<Int>): List<CmProduct>

    @Query("SELECT * FROM cm_products WHERE name = :name COLLATE NOCASE")
    suspend fun named(name: String): List<CmProduct>

    @Query("SELECT COUNT(*) FROM cm_products")
    suspend fun count(): Int

    @Query("SELECT idExpansions FROM cm_set_expansions WHERE setCode = :setCode")
    suspend fun setExpansions(setCode: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetExpansions(row: CmSetExpansions)

    @Query("DELETE FROM cm_set_expansions")
    suspend fun clearSetExpansions()
}

/** The matching rules, kept apart so they can be tested. */
object CatalogMatch {
    fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Cardmarket names double-faced cards by both faces or just the front one. */
    fun sameCard(cardName: String, productName: String): Boolean {
        val p = normalize(productName)
        return p == normalize(cardName) || p == normalize(cardName.substringBefore(" // "))
    }

    /**
     * A set's Cardmarket expansions, from how many of its cards (that Scryfall does link) are in
     * each: every expansion with at least two, or the only one there is.
     */
    fun expansions(votes: Map<Int, Int>): Set<Int> =
        if (votes.size == 1) votes.keys else votes.filterValues { it >= 2 }.keys

    /** The one product with the card's name in the set's expansions; null when there's none or several. */
    fun pick(products: List<CmProduct>, cardName: String, expansions: Set<Int>): Int? =
        products.filter { it.idExpansion in expansions && sameCard(cardName, it.name) }.map { it.idProduct }.distinct().singleOrNull()

    /**
     * Cardmarket's separate foil product for a printing whose own product has no foil price (e.g.
     * Lord of the Rings' silver-foil scrolls): the product right after it, with the same name in
     * the same expansion, priced only as foil (its "low" can still be filled: Cardmarket gives the
     * lowest offer of any finish there).
     */
    fun foilCompanion(main: CmProduct, sameName: List<CmProduct>, prices: Map<Int, PriceEntity>): Int? {
        if (prices[main.idProduct]?.toSet(true)?.isEmpty == false) return null
        val next = sameName.firstOrNull { it.idProduct == main.idProduct + 1 && it.idExpansion == main.idExpansion } ?: return null
        val p = prices[next.idProduct] ?: return null
        return next.idProduct.takeIf { !p.toSet(true).isEmpty && p.trend == null && p.avg == null }
    }
}

/**
 * Cardmarket's public list of Magic singles, downloaded about once a week. Scryfall links almost
 * every printing to its Cardmarket product, but not all (The List reprints, some surge foils, …);
 * this list lets the app find those, and the separate foil products of special foils.
 */
class CardmarketCatalog(
    private val context: Context,
    private val http: OkHttpClient,
    private val db: AppDatabase,
    private val settings: Settings,
    private val scryfall: ScryfallApi,
) {
    companion object {
        const val URL = "https://downloads.s3.cardmarket.com/productCatalog/productList/products_singles_1.json"
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private val CARD_TABLES = listOf("collection", "trade_items", "scans", "deck_cards", "wishlist")
    }

    private val dao = db.catalogDao()
    private val lock = Mutex()

    val isStale get() = System.currentTimeMillis() - settings.catalogFetchedAt > MAX_AGE_MS

    suspend fun refresh(): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "cm_products.json")
            try {
                var attempt = 1
                while (true) {
                    try {
                        download(tmp)
                        break
                    } catch (e: IOException) {
                        if (attempt >= 3) throw e
                        delay(2000L * attempt)
                        attempt++
                    }
                }
                import(tmp)
                settings.catalogFetchedAt = System.currentTimeMillis()
                true
            } catch (e: Exception) {
                false
            } finally {
                tmp.delete()
            }
        }
    }

    private fun download(target: File) {
        http.newCall(Request.Builder().url(URL).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response")
            body.byteStream().use { input -> target.outputStream().use { input.copyTo(it, 64 * 1024) } }
        }
    }

    private suspend fun import(file: File) {
        db.withTransaction {
            dao.clear()
            dao.clearSetExpansions()
            JsonReader(InputStreamReader(file.inputStream().buffered(), Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() != "products") { reader.skipValue(); continue }
                    val batch = ArrayList<CmProduct>(2000)
                    reader.beginArray()
                    while (reader.hasNext()) {
                        readProduct(reader)?.let { batch += it }
                        if (batch.size >= 2000) { dao.insertAll(batch); batch.clear() }
                    }
                    reader.endArray()
                    if (batch.isNotEmpty()) dao.insertAll(batch)
                }
                reader.endObject()
            }
        }
    }

    private fun readProduct(r: JsonReader): CmProduct? {
        var id: Int? = null
        var name: String? = null
        var expansion: Int? = null
        r.beginObject()
        while (r.hasNext()) {
            val key = r.nextName()
            if (r.peek() == JsonToken.NULL) { r.nextNull(); continue }
            when (key) {
                "idProduct" -> id = r.nextInt()
                "name" -> name = r.nextString()
                "idExpansion" -> expansion = r.nextInt()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return if (id != null && name != null && expansion != null) CmProduct(id, name, expansion) else null
    }

    /** The set's Cardmarket expansions: remembered, or worked out from cards of the set that Scryfall links. */
    private suspend fun expansionsFor(setCode: String): Set<Int> {
        dao.setExpansions(setCode)?.let { s -> return s.split(',').mapNotNull { it.toIntOrNull() }.toSet() }
        val linked = runCatching { scryfall.search("e:$setCode") }.getOrDefault(emptyList()).mapNotNull { it.cardmarketId }
        if (linked.isEmpty()) return emptySet()
        val votes = linked.chunked(500).flatMap { dao.getMany(it) }.groupingBy { it.idExpansion }.eachCount()
        return CatalogMatch.expansions(votes).also { dao.putSetExpansions(CmSetExpansions(setCode, it.joinToString(","))) }
    }

    /**
     * Fills in Cardmarket links Scryfall doesn't give, everywhere cards are kept (collection,
     * trades, scans, decks, wishlist), and links special foils to their separate foil product.
     * Returns how many printings got a link. Does nothing until the list has been downloaded.
     */
    suspend fun repair(): Int {
        if (dao.count() == 0) return 0
        val w = db.openHelper.writableDatabase
        var fixed = 0
        // Printings without any Cardmarket link, by set and name.
        val missing = CARD_TABLES.flatMap { t ->
            w.query("SELECT DISTINCT setCode, name FROM `$t` WHERE cardmarketId IS NULL").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            }
        }.distinct()
        for ((set, name) in missing) {
            val expansions = expansionsFor(set)
            if (expansions.isEmpty()) continue
            val id = CatalogMatch.pick(dao.named(name) + dao.named(name.substringBefore(" // ")), name, expansions) ?: continue
            db.withTransaction {
                for (t in CARD_TABLES) w.execSQL("UPDATE `$t` SET cardmarketId = ? WHERE cardmarketId IS NULL AND setCode = ? AND name = ?", arrayOf(id, set, name))
            }
            fixed++
        }
        // Foil copies whose product has no foil price: maybe Cardmarket sells that foil as its own product.
        val foilLinks = CARD_TABLES.flatMap { t ->
            w.query("SELECT DISTINCT cardmarketId FROM `$t` WHERE foil = 1 AND cardmarketId IS NOT NULL AND cardmarketFoilId IS NULL").use { c ->
                buildList { while (c.moveToNext()) add(c.getInt(0)) }
            }
        }.distinct()
        if (foilLinks.isNotEmpty()) {
            val products = foilLinks.chunked(500).flatMap { dao.getMany(it) }
            val prices = db.priceDao().getMany((foilLinks + foilLinks.map { it + 1 }).distinct())
                .associateBy { it.idProduct }
            for (main in products) {
                if (prices[main.idProduct]?.toSet(true)?.isEmpty == false) continue
                val companion = CatalogMatch.foilCompanion(main, dao.named(main.name), prices) ?: continue
                db.withTransaction {
                    for (t in CARD_TABLES) w.execSQL("UPDATE `$t` SET cardmarketFoilId = ? WHERE cardmarketId = ? AND cardmarketFoilId IS NULL", arrayOf(companion, main.idProduct))
                }
                fixed++
            }
        }
        return fixed
    }
}
