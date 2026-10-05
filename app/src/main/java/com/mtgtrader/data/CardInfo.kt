package com.mtgtrader.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the collection's sorting, filters and trade binder need to know about a printing beyond
 * [CardRef]: colours, type, mana value and how popular it is in Commander (EDHREC's rank, as
 * Scryfall gives it). A cache filled from Scryfall; not synced. Since 1.18.
 */
@Entity(tableName = "card_info")
data class CardInfo(
    @PrimaryKey val scryfallId: String,
    val oracleId: String?,
    /** The card's colours in WUBRG order, e.g. "BG"; empty for colourless. */
    val colors: String,
    val colorIdentity: String,
    val typeLine: String,
    val cmc: Double,
    /** EDHREC's popularity rank (1 = most played); null for cards EDHREC doesn't rank. */
    val edhrecRank: Int?,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    val isLand get() = "Land" in typeLine.substringBefore(" // ")
}

@Dao
interface CardInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(rows: List<CardInfo>)

    @Query("SELECT * FROM card_info WHERE scryfallId IN (:ids)")
    suspend fun getMany(ids: List<String>): List<CardInfo>

    /** Printings in the collection, wishlist or decks without details yet. */
    @Query(
        """SELECT DISTINCT scryfallId FROM (
             SELECT scryfallId FROM collection UNION SELECT scryfallId FROM wishlist UNION SELECT scryfallId FROM deck_cards
           ) WHERE scryfallId NOT IN (SELECT scryfallId FROM card_info)"""
    )
    suspend fun missing(): List<String>

    @Query("DELETE FROM card_info WHERE fetchedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

/** The five colours in their usual order, and helpers to sort and filter by them. */
object MtgColors {
    const val ORDER = "WUBRG"

    fun normalize(colors: Collection<String>) = ORDER.filter { it.toString() in colors }

    /**
     * Sort position for a colour group: white, blue, black, red, green, then multicoloured (in WUBRG
     * pair order), colourless, and lands last.
     */
    fun sortKey(colors: String?, isLand: Boolean): Int = when {
        colors == null -> 9_999
        isLand -> 9_000
        colors.isEmpty() -> 8_000
        colors.length == 1 -> ORDER.indexOf(colors[0])
        else -> 100 * colors.length + colors.sumOf { 1 shl ORDER.indexOf(it) }
    }
}

/**
 * Keeps [CardInfo] filled for every printing in the collection, wishlist and decks: missing ones
 * are looked up on Scryfall 75 at a time (about a minute for a 5,000-card collection, once).
 */
class CardDetails(private val db: AppDatabase, private val scryfall: ScryfallApi) {
    private val dao = db.cardInfoDao()
    private val lock = Mutex()

    /** Progress while details are fetched: (done, total); null when idle. */
    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress

    suspend fun fillMissing() = lock.withLock {
        val ids = dao.missing()
        if (ids.isEmpty()) return@withLock
        try {
            _progress.value = 0 to ids.size
            var base = 0
            // Saved in chunks, so a lost connection keeps what was fetched.
            for (chunk in ids.chunked(300)) {
                val cards = scryfall.collection(chunk.map(ScryfallApi::idIdentifier)) { done, _ -> _progress.value = (base + done) to ids.size }
                val found = cards.map { it.toInfo() }
                // Printings Scryfall no longer knows get an empty entry, so they aren't asked for on every start.
                val gone = (chunk.toSet() - found.map { it.scryfallId }.toSet()).map { CardInfo(it, null, "", "", "", 0.0, null) }
                dao.putAll(found + gone)
                base += chunk.size
            }
        } finally {
            _progress.value = null
        }
    }

    /** Details for printings, fetching the ones not known yet. */
    suspend fun forIds(ids: Collection<String>): Map<String, CardInfo> {
        val known = dao.getMany(ids.toList()).associateBy { it.scryfallId }
        val missing = ids.filter { it !in known }
        if (missing.isEmpty()) return known
        val fetched = runCatching { scryfall.collection(missing.map(ScryfallApi::idIdentifier)) }.getOrDefault(emptyList()).map { it.toInfo() }
        dao.putAll(fetched)
        return known + fetched.associateBy { it.scryfallId }
    }
}

fun ScryCard.toInfo() = CardInfo(
    scryfallId = id,
    oracleId = oracleId,
    colors = MtgColors.normalize(colors ?: cardFaces?.flatMap { it.colors.orEmpty() }.orEmpty()),
    colorIdentity = MtgColors.normalize(colorIdentity),
    typeLine = typeLine.ifEmpty { cardFaces?.joinToString(" // ") { it.typeLine } ?: "" },
    cmc = cmc,
    edhrecRank = edhrecRank,
)
