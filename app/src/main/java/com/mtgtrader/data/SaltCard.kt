package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * What the app's own rule-zero cards show, taken from Commander Salt's deck analysis (the same
 * numbers its PNG cards are drawn from). Kept with the deck as JSON, so it syncs and works offline.
 */
@Serializable
data class SaltCard(
    // Bracket card
    val gameChangers: List<String> = emptyList(),
    /** Each combo as its card names. */
    val twoCardCombos: List<List<String>> = emptyList(),
    val earlyCombos: List<List<String>> = emptyList(),
    val extraTurns: List<String> = emptyList(),
    val massLandDenial: List<String> = emptyList(),
    /** Commander Salt's 0–1 ratings behind the "realistic bracket" bars; see [BracketAxes]. */
    val axes: Map<String, Double> = emptyMap(),
    /** Commander Salt's rule-zero talking points, e.g. "Redundant combo wins". */
    val notes: List<SaltNote> = emptyList(),
    // Power card
    /** "Casual", "Focused", … */
    val playStyle: String? = null,
    val saltIntensity: String? = null,
    /** What makes the deck salty, e.g. "Board wipes". */
    val saltDriver: String? = null,
    val manaFixing: Int? = null,
    val onCurve: Int? = null,
    val manaQuality: Int? = null,
    /** Counts per [SaltEffect] key. */
    val effects: Map<String, Int> = emptyMap(),
    /** Win conditions besides combos (Commander Salt's "non-combo wincons"). */
    val wincons: List<String> = emptyList(),
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun decode(text: String?): SaltCard? = text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

@Serializable
data class SaltNote(val label: String, val why: String = "")

/** The interaction and effect counts on the power card, in Commander Salt's order. */
enum class SaltEffect(val key: String, val label: String) {
    FAST_MANA("fastmana", "Fast mana"),
    TUTORS("tutors", "Tutors"),
    COUNTERS("counters", "Counterspells"),
    SPOT_REMOVAL("spotRemoval", "Spot removal"),
    BOARD_WIPES("boardWipes", "Board wipes"),
    GROUP_HUG("grouphug", "Group hug"),
    GROUP_SLUG("groupslug", "Group slug"),
    EXTRA_TURNS("extraTurns", "Extra turns"),
    STAX("stax", "Stax"),
    TAXES("taxes", "Taxes"),
    MLD("mld", "Land denial"),
    INFINITE_COMBOS("combos", "Infinite combos"),
}

/** The five "how it plays" axes of Commander Salt's realistic bracket. */
enum class BracketAxis(val key: String, val label: String) {
    CONSISTENCY("consistency", "Consistency"),
    EFFICIENCY("efficiency", "Efficiency"),
    INTERACTION("interaction", "Interaction"),
    MANABASE("manabase", "Manabase"),
    WIN_CONDITIONS("winConditions", "Win conditions"),
}

object BracketAxes {
    /**
     * The 1–5 bar Commander Salt draws for a 0–1 rating. It doesn't publish the mapping; these
     * steps reproduce every bar on its cards for the decks it was checked against (see the tests).
     */
    fun level(rating: Double): Int = when {
        rating >= 0.92 -> 5
        rating >= 0.80 -> 4
        rating >= 0.55 -> 3
        else -> 2
    }
}

/** Pulls [SaltCard] out of Commander Salt's deck JSON. Missing parts are left empty rather than failing. */
object SaltCardParser {
    fun parse(root: JsonObject): SaltCard {
        val cards = root.obj("cards")
        fun name(id: String) = cards?.obj(id)?.str("name") ?: id.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        val details = root.obj("details")
        val brackets = details?.obj("brackets")
        val categories = brackets?.obj("categories")
        val combosById = details?.obj("combos")?.arr("list").orEmpty().mapNotNull { it as? JsonObject }
            .associate { c -> c.str("id").orEmpty() to c.arr("cards").orEmpty().mapNotNull(::idOf) }
        fun cardList(key: String) = categories?.obj(key)?.arr("list").orEmpty().mapNotNull(::idOf).map(::name)
        fun comboList(key: String) = categories?.obj(key)?.arr("list").orEmpty().mapNotNull(::idOf).map { id ->
            combosById[id]?.map(::name) ?: listOf(name(id))
        }

        val power = details?.obj("powerLevel")
        val spike = power?.obj("ratings")?.obj("spike")
        val scoring = power?.obj("scoring")
        val salt = details?.obj("salt")?.obj("profile")?.obj("headline")
        val mana = details?.obj("manabase")?.obj("percentages")
        val notes = brackets?.obj("profile")?.arr("ruleZero").orEmpty().mapNotNull { it as? JsonObject }
            // The two-card combos are named on the card already.
            .filter { it.str("id") != "twoCardCombo" }
            .mapNotNull { n -> n.str("label")?.let { SaltNote(it, n.str("why").orEmpty()) } }
        // Commander Salt's card lists the stompy and burn finishers as its "non-combo wincons".
        val wincons = listOf("wincon_stompy", "wincon_burn")
            .flatMap { scoring?.obj(it)?.obj("list")?.keys.orEmpty() }
            .map(::name).distinct().sortedBy { it.lowercase() }

        return SaltCard(
            gameChangers = cardList("gameChangers"),
            twoCardCombos = comboList("twoCardCombos"),
            earlyCombos = comboList("earlyGameInfiniteCombos"),
            extraTurns = cardList("extraTurns"),
            massLandDenial = cardList("massLandDenial"),
            axes = BracketAxis.entries.mapNotNull { a -> spike?.num(a.key)?.let { a.key to it } }.toMap(),
            notes = notes,
            playStyle = power?.obj("ratings")?.str("inferredTypeLabel"),
            saltIntensity = salt?.str("intensityLabel"),
            saltDriver = salt?.str("dominantFamilyLabel"),
            manaFixing = mana?.num("manaFixing")?.roundToInt(),
            onCurve = mana?.num("curve")?.roundToInt(),
            manaQuality = mana?.num("quality")?.roundToInt(),
            effects = SaltEffect.entries.mapNotNull { e -> scoring?.obj(e.key)?.let { e.key to (it.obj("list")?.size ?: 0) } }.toMap(),
            wincons = wincons,
        )
    }

    private fun idOf(e: JsonElement): String? = when (e) {
        is JsonPrimitive -> e.content
        is JsonObject -> e.str("id")
        else -> null
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.arr(key: String) = this[key] as? JsonArray
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull
}
