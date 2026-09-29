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
)

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

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

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
            )
        }
    }
}
