package com.mtgtrader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
private data class ArchOwner(val username: String = "")

@Serializable
private data class ArchCategory(val name: String, val includedInDeck: Boolean = true, val isPremier: Boolean = false)

@Serializable
private data class ArchEdition(val editioncode: String = "", val editionname: String = "")

@Serializable
private data class ArchOracle(
    val name: String,
    val cmc: Double = 0.0,
    val types: List<String> = emptyList(),
    val colorIdentity: List<String> = emptyList(),
    val defaultCategory: String? = null,
    val gameChanger: Boolean = false,
)

@Serializable
private data class ArchCard(
    val uid: String,
    val collectorNumber: String = "",
    val edition: ArchEdition = ArchEdition(),
    val oracleCard: ArchOracle,
)

@Serializable
private data class ArchEntry(
    val categories: List<String>? = null,
    val quantity: Int = 1,
    val modifier: String = "Normal",
    val card: ArchCard,
)

@Serializable
private data class ArchDeck(
    val id: Long,
    val name: String = "",
    val owner: ArchOwner = ArchOwner(),
    val featured: String? = null,
    val customFeatured: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val categories: List<ArchCategory> = emptyList(),
    val cards: List<ArchEntry> = emptyList(),
    val updatedAt: String? = null,
)

@Serializable
private data class ArchDeckListItem(
    val id: Long,
    val name: String = "",
    val size: Int = 0,
    val deckFormat: Int? = null,
    val featured: String? = null,
    val customFeatured: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val colors: Map<String, Int> = emptyMap(),
    val parentFolderName: String? = null,
    val updatedAt: String? = null,
)

@Serializable
private data class ArchDeckList(val count: Int = 0, val next: String? = null, val results: List<ArchDeckListItem> = emptyList())

/** One line of an Archidekt decklist, before the card is looked up on Scryfall. */
data class ArchidektCard(
    val scryfallId: String,
    val name: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val quantity: Int,
    val category: String,
    val types: String,
    val cmc: Double,
    val commander: Boolean,
    val foil: Boolean,
    val etched: Boolean,
    val gameChanger: Boolean,
    val colorIdentity: List<String>,
) {
    /** Scryfall serves every card image at a path made from its id. */
    val imageUrl get() = "https://cards.scryfall.io/normal/front/${scryfallId[0]}/${scryfallId[1]}/$scryfallId.jpg"
}

data class ArchidektDeck(
    val id: Long,
    val name: String,
    val owner: String,
    val artUrl: String?,
    /** Only the cards in the deck itself: the maybeboard and other excluded categories are left out. */
    val cards: List<ArchidektCard>,
    /** When the deck was last changed on Archidekt. */
    val updatedAt: Long? = null,
) {
    val commanders get() = cards.filter { it.commander }
    val cardCount get() = cards.sumOf { it.quantity }

    /** Colour identity of the commander(s), in WUBRG order. */
    val colorIdentity: String
        get() {
            val names = commanders.flatMap { it.colorIdentity }.toSet()
            return listOf("White" to 'W', "Blue" to 'U', "Black" to 'B', "Red" to 'R', "Green" to 'G')
                .filter { it.first in names }.map { it.second }.joinToString("")
        }
}

/** Reads public decks through the same JSON API Archidekt's website uses. */
class ArchidektApi(private val http: OkHttpClient) {
    suspend fun deck(id: Long): ArchidektDeck = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://archidekt.com/api/decks/$id/").header("Accept", "application/json").build()
        val body = http.newCall(req).execute().use { r ->
            when {
                r.isSuccessful -> r.body?.string() ?: throw IOException("Archidekt sent an empty reply")
                r.code == 404 || r.code == 403 -> throw IOException("Archidekt deck $id doesn't exist or isn't public")
                else -> throw IOException("Archidekt error ${r.code}")
            }
        }
        parse(body)
    }

    /** The public decks of [username], most recently updated first. */
    suspend fun userDecks(username: String): List<ArchidektDeckSummary> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ArchidektDeckSummary>()
        var url: String? = "https://archidekt.com/api/decks/v3/?ownerUsername=${java.net.URLEncoder.encode(username, "UTF-8")}&pageSize=50"
        var pages = 0
        while (url != null && pages < 20) {
            val req = Request.Builder().url(url).header("Accept", "application/json").build()
            val body = http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Archidekt error ${r.code}")
                r.body?.string() ?: throw IOException("Archidekt sent an empty reply")
            }
            val page = parseDeckList(body)
            out += page.first
            // Archidekt's "next" links use http://; the app only talks https.
            url = page.second?.replaceFirst("http://", "https://")
            pages++
        }
        out
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

        /** One page of a user's deck list, and the address of the next page if there is one. */
        fun parseDeckList(body: String): Pair<List<ArchidektDeckSummary>, String?> {
            val list = json.decodeFromString<ArchDeckList>(body)
            val decks = list.results.filterNot { it.isPrivate }.map { d ->
                ArchidektDeckSummary(
                    id = d.id,
                    name = d.name,
                    size = d.size,
                    artUrl = d.customFeatured?.takeIf { it.isNotBlank() } ?: d.featured?.takeIf { it.isNotBlank() },
                    colors = "WUBRG".filter { (d.colors[it.toString()] ?: 0) > 0 },
                    commanderFormat = d.deckFormat == 3,
                    folder = d.parentFolderName?.takeIf { it.isNotBlank() },
                    updatedAt = DeckSorting.parseTime(d.updatedAt),
                )
            }
            return decks to list.next
        }

        fun parse(body: String): ArchidektDeck {
            val d = json.decodeFromString<ArchDeck>(body)
            val excluded = d.categories.filterNot { it.includedInDeck }.map { it.name }.toSet()
            val premier = d.categories.filter { it.isPremier }.map { it.name }.toSet() + "Commander"
            val cards = d.cards.mapNotNull { e ->
                val cats = e.categories.orEmpty()
                val primary = cats.firstOrNull() ?: e.card.oracleCard.defaultCategory ?: DeckGrouping.primaryType(e.card.oracleCard.types.joinToString(" "))
                if (primary in excluded || e.card.uid.length < 2) return@mapNotNull null
                val o = e.card.oracleCard
                ArchidektCard(
                    scryfallId = e.card.uid,
                    name = o.name,
                    setCode = e.card.edition.editioncode,
                    setName = e.card.edition.editionname,
                    collectorNumber = e.card.collectorNumber,
                    quantity = e.quantity,
                    category = primary,
                    types = o.types.joinToString(" "),
                    cmc = o.cmc,
                    commander = cats.any { it in premier },
                    foil = e.modifier.equals("Foil", true) || e.modifier.equals("Etched", true),
                    etched = e.modifier.equals("Etched", true),
                    gameChanger = o.gameChanger,
                    colorIdentity = o.colorIdentity,
                )
            }
            return ArchidektDeck(
                id = d.id,
                name = d.name,
                owner = d.owner.username,
                artUrl = d.customFeatured?.takeIf { it.isNotBlank() } ?: d.featured?.takeIf { it.isNotBlank() },
                cards = cards,
                updatedAt = DeckSorting.parseTime(d.updatedAt),
            )
        }
    }
}
