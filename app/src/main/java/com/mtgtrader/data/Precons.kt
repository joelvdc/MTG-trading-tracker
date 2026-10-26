package com.mtgtrader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.roundToLong

/**
 * Preconstructed Commander decks, from MTGJSON's deck lists (Scryfall has none): which cards a deck
 * holds, in which printing and finish. Adding one to the collection splits the price paid over its
 * cards in proportion to what each is worth today, so the cards add up to what the deck cost. Basic
 * lands (and tokens, which the lists leave out anyway) get nothing and aren't added. Since 1.33.
 */
object Precons {
    const val LIST_URL = "https://mtgjson.com/api/v5/DeckList.json"
    fun deckUrl(fileName: String) = "https://mtgjson.com/api/v5/decks/$fileName.json"

    const val TYPE = "Commander Deck"

    /** A card that is worth less than this still gets this much weight, so no card comes out at nothing. */
    const val FLOOR = 0.05

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Info(val code: String = "", val fileName: String, val name: String, val releaseDate: String? = null, val type: String = "")

    @Serializable
    private data class ListFile(val data: List<Info> = emptyList())

    @Serializable
    data class Ids(val scryfallId: String? = null)

    @Serializable
    data class Card(
        val name: String,
        val count: Int = 1,
        val isFoil: Boolean = false,
        val setCode: String = "",
        val number: String = "",
        val identifiers: Ids = Ids(),
        val types: List<String> = emptyList(),
        val supertypes: List<String> = emptyList(),
    ) {
        val isBasicLand get() = ("Basic" in supertypes && "Land" in types) || OrderFiles.isBasicLandName(name)
    }

    @Serializable
    data class Deck(
        val name: String,
        val code: String = "",
        val releaseDate: String? = null,
        val commander: List<Card> = emptyList(),
        val mainBoard: List<Card> = emptyList(),
        val sideBoard: List<Card> = emptyList(),
    )

    @Serializable
    private data class DeckFile(val data: Deck)

    /** The Commander decks in MTGJSON's list, newest first. */
    fun parseList(text: String): List<Info> = json.decodeFromString(ListFile.serializer(), text).data
        .filter { it.type == TYPE }
        .sortedWith(compareByDescending<Info> { it.releaseDate.orEmpty() }.thenBy { it.name })

    fun parseDeck(text: String): Deck = json.decodeFromString(DeckFile.serializer(), text).data

    /** The deck's cards without basic lands, each printing and finish once with its count. */
    fun cards(deck: Deck): List<Card> = (deck.commander + deck.mainBoard + deck.sideBoard)
        .filter { !it.isBasicLand && it.identifiers.scryfallId != null }
        .groupBy { it.identifiers.scryfallId to it.isFoil }
        .map { (_, same) -> same.first().copy(count = same.sumOf { it.count }) }

    /** Decks whose name or set code contains every word of [query]. */
    fun search(all: List<Info>, query: String): List<Info> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return all.filter { d -> words.all { it in d.name.lowercase() || it == d.code.lowercase() } }
    }

    /**
     * A deck card ready to add: its printing, and its share of the deck's price per copy, in EUR, and as typed
     * when the price was typed in kroner or dollars ([typed], since 1.60).
     */
    data class PricedCard(val card: CardRef, val finish: Finish, val quantity: Int, val price: Double, val worth: Double, val typed: TypedPrice? = null) {
        val ordered get() = OrderedCard(
            name = card.name, setCode = card.setCode, number = card.collectorNumber, quantity = quantity, price = price, foil = finish.foil,
            condition = "NM", language = "EN", scryfallId = card.scryfallId,
            cardmarketId = card.cardmarketFoilId?.takeIf { finish.foil } ?: card.cardmarketId,
        )
    }

    private fun cents(v: Double) = (v * 100).roundToLong() / 100.0

    /**
     * Splits [paid] over [cards] (each with today's price of one copy, null when unknown) in proportion
     * to their worth, with [FLOOR] as the least a copy weighs. Shares are whole cents; the cents lost or
     * gained by rounding go to the most valuable card, so the shares add up to [paid] exactly.
     */
    fun split(paid: Double, cards: List<Triple<CardRef, Card, Double?>>): List<PricedCard> {
        if (cards.isEmpty()) return emptyList()
        fun weight(worth: Double?) = maxOf(worth ?: 0.0, FLOOR)
        val total = cards.sumOf { (_, c, worth) -> weight(worth) * c.count }
        val shares = cards.map { (ref, c, worth) ->
            PricedCard(ref, ref.resolveFinish(if (c.isFoil) Finish.FOIL else Finish.NONFOIL), c.count, cents(paid * weight(worth) / total), worth ?: 0.0)
        }.sortedByDescending { it.worth }
        val off = cents(paid - shares.sumOf { it.price * it.quantity })
        val top = shares.first()
        // Only a card there is one of can take odd cents without the sum drifting again.
        val taker = shares.firstOrNull { it.quantity == 1 } ?: top
        return shares.map { if (it === taker) it.copy(price = cents(maxOf(0.0, it.price + off / it.quantity))) else it }
    }

    /** [split], with each share also as typed in [typed]'s currency (split the same way, so they add up to it). Since 1.60. */
    fun split(paid: Double, cards: List<Triple<CardRef, Card, Double?>>, typed: TypedPrice?): List<PricedCard> {
        val shares = split(paid, cards)
        if (typed == null) return shares
        return shares.zip(split(typed.amount, cards)) { s, t -> s.copy(typed = TypedPrice(t.price, typed.currency)) }
    }
}

/** Fetches MTGJSON's deck lists; the list of decks is kept while the app runs. */
class PreconStore(private val http: OkHttpClient, private val listUrl: String = Precons.LIST_URL, private val deckUrl: (String) -> String = Precons::deckUrl) {
    private val lock = Mutex()
    private var decks: List<Precons.Info>? = null

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("User-Agent", "MTGTraderAndroid/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("MTGJSON answered ${r.code}")
            r.body?.string() ?: throw IOException("MTGJSON sent nothing")
        }
    }

    suspend fun list(): List<Precons.Info> = lock.withLock {
        decks ?: withContext(Dispatchers.Default) { Precons.parseList(get(listUrl)) }.also { decks = it }
    }

    suspend fun deck(info: Precons.Info): Precons.Deck = withContext(Dispatchers.Default) { Precons.parseDeck(get(deckUrl(info.fileName))) }
}
