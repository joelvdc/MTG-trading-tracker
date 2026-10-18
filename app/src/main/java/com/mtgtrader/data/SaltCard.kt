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
    /** The tutors and fast mana Commander Salt counted, by name. Since 1.16. */
    val tutors: List<String> = emptyList(),
    val fastMana: List<String> = emptyList(),
    /** Every combo Commander Salt found, also those that don't count for the bracket. Since 1.46. */
    val combos: List<SaltCombo> = emptyList(),
    /** Which of these fields the data was read with; older data is fetched again (see [CURRENT]). */
    val version: Int = 0,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    val current get() = version >= CURRENT

    /**
     * Every combo, those that count for the bracket first. Data from before 1.46 only knows the ones
     * that count.
     */
    val allCombos: List<SaltCombo> by lazy {
        val counted = (twoCardCombos + earlyCombos).distinct()
        val known = combos.map { c -> c.cards.map(::key).toSet() }.toSet()
        val all = combos + counted.filter { it.map(::key).toSet() !in known }.map { SaltCombo(it, counts = true) }
        all.sortedWith(compareByDescending<SaltCombo> { it.counts }.thenBy { it.cards.size })
    }

    /** The bracket-relevant tags of each card, keyed by [key]. */
    val tagsByCard: Map<String, Set<BracketTag>> by lazy {
        val m = HashMap<String, MutableSet<BracketTag>>()
        fun tag(names: List<String>, t: BracketTag) = names.forEach { m.getOrPut(key(it)) { sortedSetOf() } += t }
        tag(gameChangers, BracketTag.GAME_CHANGER)
        // One combo flag of each colour per card: the smallest combo it's in.
        allCombos.forEach { combo -> tag(combo.cards, BracketTag.combo(combo.counts, combo.cards.size)) }
        m.values.forEach { tags ->
            if (BracketTag.COMBO in tags) tags -= BracketTag.COMBO_3
            if (BracketTag.OTHER_COMBO in tags) tags -= BracketTag.OTHER_COMBO_3
        }
        tag(extraTurns, BracketTag.EXTRA_TURN)
        tag(massLandDenial, BracketTag.LAND_DENIAL)
        tag(tutors, BracketTag.TUTOR)
        tag(fastMana, BracketTag.FAST_MANA)
        m
    }

    fun tags(cardName: String): Set<BracketTag> = tagsByCard[key(cardName)].orEmpty()

    /** The combos (each as its other cards) this card is part of that count for the bracket. */
    fun combosWith(cardName: String): List<List<String>> =
        combosOf(cardName).filter { it.counts }.map { combo -> combo.cards.filter { key(it) != key(cardName) } }

    /** Every combo this card is part of, those that count for the bracket first. */
    fun combosOf(cardName: String): List<SaltCombo> = allCombos.filter { combo -> combo.cards.any { key(it) == key(cardName) } }

    companion object {
        /** 2: tutors and fast mana by name (1.16). 3: every combo, also those that don't count (1.46). */
        const val CURRENT = 3
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        /** Cards are matched by their front face, case-insensitively ("Fable of the Mirror-Breaker // Reflection…"). */
        fun key(name: String) = name.substringBefore(" // ").trim().lowercase()

        fun decode(text: String?): SaltCard? = text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** A combo Commander Salt found in the deck. Since 1.46. */
@Serializable
data class SaltCombo(
    val cards: List<String>,
    /** Counted for the bracket (Commander Salt's two-card and early-game combos). */
    val counts: Boolean = false,
    /** What it does, as Commander Salt's labels ("infinite-etb", "win-game"…). */
    val results: List<String> = emptyList(),
    /** Needs more on the battlefield than its own cards. */
    val needsBoard: Boolean = false,
    val spellbookUri: String? = null,
) {
    /** "infinite ETB, infinite life loss". */
    val resultText get() = results.joinToString(", ") { r ->
        when (r) {
            "win-game" -> "wins the game"
            "opponents-locked" -> "locks opponents"
            else -> r.split('-').joinToString(" ") { w ->
                when (w) {
                    "etb", "ltb" -> w.uppercase()
                    "lifeloss" -> "life loss"
                    "lifegain" -> "life gain"
                    else -> w
                }
            }
        }
    }
}

/**
 * What makes a card matter for the deck's bracket, shown next to it in the decklist. [strict]: one of WotC's
 * bracket rules. The combo tags since 1.46: red ones count for the bracket, blue ones ([otherCombo]) don't;
 * "3" for combos of three or more cards.
 */
enum class BracketTag(val label: String, val strict: Boolean) {
    GAME_CHANGER("GAME CHANGER", true),
    COMBO("COMBO 2", true),
    COMBO_3("COMBO 3+", true),
    EXTRA_TURN("EXTRA TURN", true),
    LAND_DENIAL("LAND DENIAL", true),
    OTHER_COMBO("COMBO 2", false),
    OTHER_COMBO_3("COMBO 3+", false),
    TUTOR("TUTOR", false),
    FAST_MANA("FAST MANA", false);

    val combo get() = this == COMBO || this == COMBO_3 || otherCombo
    val otherCombo get() = this == OTHER_COMBO || this == OTHER_COMBO_3

    companion object {
        fun combo(counts: Boolean, cards: Int) = when {
            counts && cards <= 2 -> COMBO
            counts -> COMBO_3
            cards <= 2 -> OTHER_COMBO
            else -> OTHER_COMBO_3
        }
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
        val comboObjects = details?.obj("combos")?.arr("list").orEmpty().mapNotNull { it as? JsonObject }
        val combosById = comboObjects.associate { c -> c.str("id").orEmpty() to c.arr("cards").orEmpty().mapNotNull(::idOf) }
        fun cardList(key: String) = categories?.obj(key)?.arr("list").orEmpty().mapNotNull(::idOf).map(::name)
        fun comboList(key: String) = categories?.obj(key)?.arr("list").orEmpty().mapNotNull(::idOf).map { id ->
            combosById[id]?.map(::name) ?: listOf(name(id))
        }

        val countedIds = listOf("twoCardCombos", "earlyGameInfiniteCombos")
            .flatMap { categories?.obj(it)?.arr("list").orEmpty().mapNotNull(::idOf) }.toSet()
        val combos = comboObjects.map { c ->
            SaltCombo(
                cards = c.arr("cards").orEmpty().mapNotNull(::idOf).map(::name),
                counts = c.str("id") in countedIds,
                results = c.arr("categories").orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
                needsBoard = (c.obj("complexity")?.get("requiresCardsOnBoard") as? JsonPrimitive)?.content == "true",
                spellbookUri = c.str("spellbookUri"),
            )
        }.filter { it.cards.isNotEmpty() }

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
        fun scored(key: String) = scoring?.obj(key)?.obj("list")?.keys.orEmpty().map(::name).distinct().sortedBy { it.lowercase() }

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
            tutors = scored(SaltEffect.TUTORS.key),
            fastMana = scored(SaltEffect.FAST_MANA.key),
            combos = combos,
            version = SaltCard.CURRENT,
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
