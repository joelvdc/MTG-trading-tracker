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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
    /**
     * The mana cost as Scryfall writes it, e.g. "{2}{B}", with " // " between the faces of a double-faced
     * card ("{2}{B} // {B}"); empty for lands. Null for details saved before 1.43. Since 1.43.
     */
    val manaCost: String? = null,
    /**
     * The colours of mana the card can make, in WUBRGC order (C for colourless), each once, as Scryfall lists them
     * ("BGC" for Twilight Mire); empty for none. Null for details saved before 1.44. Since 1.44.
     */
    val producedMana: String? = null,
    /** The rules text, faces separated by [FACE_BREAK]. Since 1.44. */
    val oracleText: String? = null,
    /** The tokens and helper cards (The Monarch…) the card makes, as [TokenPart] JSON; null for none. Since 1.44. */
    val tokens: String? = null,
    /** Power and toughness, e.g. "1/1" (the front face's); for naming tokens. Since 1.44. */
    val powerToughness: String? = null,
) {
    val isLand get() = "Land" in typeLine.substringBefore(" // ")

    companion object {
        /** Between the faces' rules text in [oracleText]. */
        const val FACE_BREAK = "\n//\n"
    }
}

/** A token or helper card a card makes, as Scryfall links them. Since 1.44. */
@Serializable
data class TokenPart(val id: String, val name: String, val typeLine: String) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(parts: List<TokenPart>): String? = parts.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }

        fun decode(s: String?): List<TokenPart> = s?.let { runCatching { json.decodeFromString<List<TokenPart>>(it) }.getOrNull() }.orEmpty()

        /**
         * The tokens among a card's linked parts, and the helper cards Scryfall links as "combo pieces" with the
         * type "Card" (The Monarch, Poison Counter, The Initiative…).
         */
        fun of(card: ScryCard): List<TokenPart> = card.allParts.orEmpty()
            .filter { p -> p.id != card.id && (p.component == "token" || (p.component == "combo_piece" && p.typeLine.split(" // ").all { it == "Card" })) }
            .map { TokenPart(it.id, it.name, it.typeLine) }
            .distinctBy { it.id }
    }
}

@Dao
interface CardInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(rows: List<CardInfo>)

    @Query("SELECT * FROM card_info WHERE scryfallId IN (:ids)")
    suspend fun getMany(ids: List<String>): List<CardInfo>

    /**
     * Printings in the collection, wishlist or decks without details yet (not wishlist cards still waiting for
     * theirs), and decks' printings whose details were saved before mana costs (1.43) or the mana they make (1.44) were.
     */
    @Query(
        """SELECT DISTINCT scryfallId FROM (
             SELECT scryfallId FROM collection UNION SELECT scryfallId FROM wishlist UNION SELECT scryfallId FROM deck_cards
           ) WHERE scryfallId NOT IN (SELECT scryfallId FROM card_info) AND scryfallId NOT LIKE '00000000-0000-0000-0000-%'
           UNION SELECT scryfallId FROM card_info WHERE (manaCost IS NULL OR producedMana IS NULL) AND scryfallId IN (SELECT scryfallId FROM deck_cards)"""
    )
    suspend fun missing(): List<String>

    /** The deck's own cards (not the maybeboard) with what the deck stats need. Since 1.44. */
    @Query(
        """SELECT d.name AS name, d.scryfallId AS scryfallId, d.quantity AS quantity, d.cmc AS cmc, d.types AS types, d.commander AS commander,
                  c.typeLine AS typeLine, c.manaCost AS manaCost, c.producedMana AS producedMana, c.oracleText AS oracleText, c.tokens AS tokens,
                  c.powerToughness AS powerToughness
           FROM deck_cards d LEFT JOIN card_info c ON c.scryfallId = d.scryfallId
           WHERE d.deckId = :deckId AND d.maybeboard = 0"""
    )
    fun observeDeckStats(deckId: Long): kotlinx.coroutines.flow.Flow<List<StatsCard>>

    /** The same, once (for the win turn of decks not on screen). Since 1.52. */
    @Query(
        """SELECT d.name AS name, d.scryfallId AS scryfallId, d.quantity AS quantity, d.cmc AS cmc, d.types AS types, d.commander AS commander,
                  c.typeLine AS typeLine, c.manaCost AS manaCost, c.producedMana AS producedMana, c.oracleText AS oracleText, c.tokens AS tokens,
                  c.powerToughness AS powerToughness
           FROM deck_cards d LEFT JOIN card_info c ON c.scryfallId = d.scryfallId
           WHERE d.deckId = :deckId AND d.maybeboard = 0"""
    )
    suspend fun deckStats(deckId: Long): List<StatsCard>

    /** Each card name in a deck with its mana cost (once known), for the deck page. Since 1.43. */
    @Query(
        """SELECT DISTINCT d.name AS name, c.manaCost AS manaCost FROM deck_cards d JOIN card_info c ON c.scryfallId = d.scryfallId
           WHERE d.deckId = :deckId AND c.manaCost IS NOT NULL"""
    )
    fun observeDeckCosts(deckId: Long): kotlinx.coroutines.flow.Flow<List<NameCost>>

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
                val gone = (chunk.toSet() - found.map { it.scryfallId }.toSet()).map { CardInfo(it, null, "", "", "", 0.0, null, manaCost = "", producedMana = "") }
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
    manaCost = manaCost?.takeIf { it.isNotEmpty() || cardFaces.isNullOrEmpty() }
        ?: cardFaces?.map { it.manaCost.orEmpty() }?.takeIf { f -> f.any { it.isNotEmpty() } }?.joinToString(" // ")
        ?: "",
    producedMana = MtgColors.ORDER.plus('C').filter { it.toString() in producedMana.orEmpty() },
    oracleText = oracleText ?: cardFaces?.joinToString(CardInfo.FACE_BREAK) { it.oracleText.orEmpty() },
    tokens = TokenPart.encode(TokenPart.of(this)),
    powerToughness = (power ?: cardFaces?.firstOrNull()?.power)?.let { p -> "$p/${toughness ?: cardFaces?.firstOrNull()?.toughness.orEmpty()}" },
)

/** A card name and its mana cost. Since 1.43. */
data class NameCost(val name: String, val manaCost: String)
