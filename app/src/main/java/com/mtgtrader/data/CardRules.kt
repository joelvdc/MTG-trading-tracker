package com.mtgtrader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** One face of a card as the card windows show it: name, cost, type, rules text and its number box. Since 1.81. */
@Serializable
data class RuleFace(
    val name: String,
    val manaCost: String = "",
    val typeLine: String = "",
    val oracleText: String = "",
    /** "2/3" for a creature. */
    val powerToughness: String? = null,
    val loyalty: String? = null,
    val defense: String? = null,
)

/** A ruling, its date as Scryfall writes it ("2024-01-01"). */
@Serializable
data class Ruling(val date: String, val text: String)

/** What the card windows' Rulings tab and card text block show about a card; saved on the phone by [CardRulesRepository]. Since 1.81. */
@Serializable
data class CardRules(
    val faces: List<RuleFace>,
    /** Format → "legal", "not_legal", "banned" or "restricted". */
    val legalities: Map<String, String>,
    /** Newest first. */
    val rulings: List<Ruling>,
    val fetchedAt: Long,
) {
    companion object {
        fun of(card: ScryCard, rulings: List<ScryRuling>, fetchedAt: Long): CardRules {
            val faces = card.cardFaces?.takeIf { it.isNotEmpty() }?.map { f ->
                RuleFace(
                    name = f.name, manaCost = f.manaCost.orEmpty(), typeLine = f.typeLine, oracleText = f.oracleText.orEmpty(),
                    powerToughness = pt(f.power, f.toughness), loyalty = f.loyalty, defense = f.defense,
                )
            } ?: listOf(
                RuleFace(
                    name = card.name, manaCost = card.manaCost.orEmpty(), typeLine = card.typeLine, oracleText = card.oracleText.orEmpty(),
                    powerToughness = pt(card.power, card.toughness), loyalty = card.loyalty, defense = card.defense,
                ),
            )
            return CardRules(
                faces = faces,
                legalities = card.legalities,
                rulings = rulings.filter { it.comment.isNotBlank() }.map { Ruling(it.publishedAt, it.comment) }.sortedByDescending { it.date },
                fetchedAt = fetchedAt,
            )
        }

        private fun pt(power: String?, toughness: String?) = power?.let { "$it/${toughness.orEmpty()}" }
    }
}

/** The faces of a card from the details already saved on the phone, for showing something while Scryfall answers. */
fun CardInfo.toRuleFaces(cardName: String): List<RuleFace> {
    if (typeLine.isEmpty()) return emptyList()
    val types = typeLine.split(" // ")
    val costs = manaCost.orEmpty().split(" // ")
    val texts = oracleText.orEmpty().split(CardInfo.FACE_BREAK)
    val names = cardName.split(" // ")
    return types.mapIndexed { i, type ->
        RuleFace(
            name = names.getOrNull(i) ?: cardName, manaCost = costs.getOrElse(i) { "" },
            typeLine = type, oracleText = texts.getOrElse(i) { "" },
            powerToughness = powerToughness.takeIf { i == 0 }, loyalty = loyalty?.takeIf { i == 0 && it.isNotEmpty() },
        )
    }
}

/**
 * Card text, legalities and rulings per printing, kept in `card-rules/<scryfallId>.json` in [dir] and asked for
 * again from Scryfall once they are [MAX_AGE_MS] old. Rulings and legalities belong to the card, not the
 * printing, but one file per printing keeps this simple. Since 1.81.
 */
class CardRulesRepository(
    private val dir: File,
    private val scryfall: ScryfallApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(id: String) = File(dir, "$id.json")

    /** What is saved for the printing, however old; null when nothing is (or the file is unreadable). */
    suspend fun cached(id: String): CardRules? = withContext(Dispatchers.IO) {
        val f = file(id)
        if (!f.isFile) return@withContext null
        try { json.decodeFromString<CardRules>(f.readText()) } catch (_: SerializationException) { null } catch (_: IOException) { null }
    }

    fun isFresh(rules: CardRules) = now() - rules.fetchedAt in 0 until MAX_AGE_MS

    /** Asks Scryfall and saves the answer; null when it can't be reached or doesn't know the printing. */
    suspend fun refresh(id: String): CardRules? {
        val rules = try {
            val card = scryfall.byId(id) ?: return null
            CardRules.of(card, scryfall.rulings(id), now())
        } catch (_: IOException) {
            return null
        } catch (_: SerializationException) {
            return null
        }
        withContext(Dispatchers.IO) {
            try {
                dir.mkdirs()
                file(id).writeText(json.encodeToString(rules))
            } catch (_: IOException) {
            }
        }
        return rules
    }

    /** The saved answer while it's fresh, else a new one from Scryfall; when that fails, the old saved one (or null). */
    suspend fun get(id: String): CardRules? {
        val old = cached(id)
        if (old != null && isFresh(old)) return old
        return refresh(id) ?: old
    }

    companion object {
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
