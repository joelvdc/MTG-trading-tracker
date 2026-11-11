package com.mtgtrader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/*
 * Is a deck legal in Commander? The website's lib/legality.ts does the same, and both are tested against the shared
 * cases in web/test/fixtures/legality-cases.json, so the phone and the website always agree.
 */

/** What the check needs to know about a card (by its front-face name), taken from its Scryfall card ([DeckLegality.ruleOf]). */
@Serializable
data class LegalRule(
    /** Colour identity in WUBRG order, "" for colourless. */
    val ci: String,
    /** The front face's type line. */
    val type: String,
    /** Scryfall's Commander legality: "legal", "banned", "not_legal" or "restricted". */
    val legal: String,
    /** Copies a deck may have: 1, 7 or 9 for "up to … cards named", 0 for any number (basic lands, Relentless Rats…). */
    val copies: Int,
    /** May be a commander on its own: a legendary creature, or "can be your commander". */
    val command: Boolean,
    /**
     * How it may pair with a second commander: "partner", "with:<other name>", "group:<partner group>", "friends",
     * "choose-bg", "bg", "companion" (Doctor's companion), "doctor" (Time Lord Doctor).
     */
    val pair: List<String>,
)

enum class ProblemKind(val id: String) {
    COMMANDER("commander"), PAIR("pair"), COUNT("count"), BANNED("banned"), NOTLEGAL("notlegal"), COLOUR("colour"), COPIES("copies"),
}

data class LegalProblem(
    val kind: ProblemKind,
    /** One line for the deck page. */
    val text: String,
    /** The cards it's about, by name as the deck has them, alphabetically. */
    val cards: List<String>,
)

data class Legality(
    val problems: List<LegalProblem>,
    /** Cards whose rules weren't known (not looked up yet, or Scryfall doesn't know them): left out of the check. */
    val unchecked: List<String>,
)

/** A deck line as the check needs it (the maybeboard is left out by the caller). */
@Serializable
data class LegalLine(val name: String, val quantity: Int, val commander: Boolean)

/** The parts of a Scryfall card the rules are read from. */
@Serializable
data class ScryRuleCard(
    val name: String,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    @SerialName("color_identity") val colorIdentity: List<String>? = null,
    val keywords: List<String>? = null,
    val legalities: Map<String, String>? = null,
    @SerialName("card_faces") val cardFaces: List<Face>? = null,
) {
    @Serializable
    data class Face(
        val name: String? = null,
        @SerialName("type_line") val typeLine: String? = null,
        @SerialName("oracle_text") val oracleText: String? = null,
    )
}

/** The parts of a Scryfall card the legality check reads. */
fun ScryCard.toRuleCard() = ScryRuleCard(
    name = name,
    typeLine = typeLine,
    oracleText = oracleText,
    colorIdentity = colorIdentity,
    legalities = legalities,
    cardFaces = cardFaces?.map { ScryRuleCard.Face(it.name, it.typeLine, it.oracleText) },
)

object DeckLegality {
    /** "Fire // Ice" → "fire": cards are checked by front-face name. */
    fun ruleKey(name: String) = name.split(" // ")[0].trim().lowercase()

    private const val WUBRG = "WUBRG"
    private val NUMBERS = mapOf("two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)

    private val BASIC = Regex("""\bBasic\b""")
    private val ANY_NUMBER = Regex("a deck can have any number of cards named", RegexOption.IGNORE_CASE)
    private val UP_TO = Regex("""a deck can have up to (\w+) cards named""", RegexOption.IGNORE_CASE)
    private val PARTNER = Regex("""^Partner(\s*\(|$)""")
    private val PARTNER_WITH = Regex("""^Partner with ([^(]+?)\s*(\(|$)""")
    private val PARTNER_GROUP = Regex("""^Partner\s*[—-]\s*([^(]+?)\s*(\(|$)""")
    private val FRIENDS = Regex("""^Friends forever(\s*\(|$)""", RegexOption.IGNORE_CASE)
    private val CHOOSE_BG = Regex("""^Choose a Background(\s*\(|$)""", RegexOption.IGNORE_CASE)
    private val COMPANION = Regex("""^Doctor['’]s companion(\s*\(|$)""", RegexOption.IGNORE_CASE)
    private val BACKGROUND = Regex("""\bBackground\b""")
    private val TIME_LORD = Regex("""\bTime Lord Doctor\b""")
    private val LEGENDARY = Regex("""\bLegendary\b""")
    private val CREATURE = Regex("""\bCreature\b""")
    private val CAN_COMMAND = Regex("can be your commander", RegexOption.IGNORE_CASE)
    private val BASICS = Regex("^(snow-covered )?(plains|island|swamp|mountain|forest|wastes)$")

    /** The rules of a card from its Scryfall card. */
    fun ruleOf(c: ScryRuleCard): LegalRule {
        val faces = c.cardFaces.orEmpty()
        val type = faces.firstOrNull()?.typeLine ?: c.typeLine ?: ""
        val text = (listOf(c.oracleText ?: "") + faces.map { it.oracleText ?: "" }).joinToString("\n")
        val ci = WUBRG.filter { it.toString() in c.colorIdentity.orEmpty() }
        var copies = 1
        if (BASIC.containsMatchIn(type) || ANY_NUMBER.containsMatchIn(text)) copies = 0
        else UP_TO.find(text)?.let { m ->
            val word = m.groupValues[1]
            copies = NUMBERS[word.lowercase()] ?: (word.toIntOrNull()?.takeIf { it != 0 } ?: 1)
        }
        val pair = mutableListOf<String>()
        for (l in text.split("\n").map { it.trim() }) {
            if (PARTNER.containsMatchIn(l)) pair.add("partner")
            else if (PARTNER_WITH.find(l) != null) pair.add("with:" + ruleKey(PARTNER_WITH.find(l)!!.groupValues[1]))
            else if (PARTNER_GROUP.find(l) != null) pair.add("group:" + PARTNER_GROUP.find(l)!!.groupValues[1].trim().lowercase())
            else if (FRIENDS.containsMatchIn(l)) pair.add("friends")
            else if (CHOOSE_BG.containsMatchIn(l)) pair.add("choose-bg")
            else if (COMPANION.containsMatchIn(l)) pair.add("companion")
        }
        if (BACKGROUND.containsMatchIn(type)) pair.add("bg")
        if (TIME_LORD.containsMatchIn(type)) pair.add("doctor")
        val command = (LEGENDARY.containsMatchIn(type) && CREATURE.containsMatchIn(type)) || CAN_COMMAND.containsMatchIn(text)
        return LegalRule(ci, type, c.legalities?.get("commander") ?: "legal", copies, command, pair.distinct())
    }

    /** Whether two commanders may lead a deck together (they are [a] and [b], with their names). */
    fun canPair(a: LegalRule, aName: String, b: LegalRule, bName: String): Boolean {
        fun has(r: LegalRule, t: String) = t in r.pair
        if (has(a, "partner") && has(b, "partner")) return true
        if (has(a, "with:" + ruleKey(bName)) && has(b, "with:" + ruleKey(aName))) return true
        if (a.pair.any { it.startsWith("group:") && has(b, it) }) return true
        if (has(a, "friends") && has(b, "friends")) return true
        if ((has(a, "choose-bg") && has(b, "bg")) || (has(b, "choose-bg") && has(a, "bg"))) return true
        if ((has(a, "companion") && has(b, "doctor")) || (has(b, "companion") && has(a, "doctor"))) return true
        return false
    }

    private fun byName(a: String, b: String) = a.lowercase().compareTo(b.lowercase())

    private val ByName = Comparator<String> { a, b -> byName(a, b) }

    private class Entry(val name: String, var quantity: Int, var commander: Boolean, val rule: LegalRule?)

    /** Checks a deck's main lines against Commander's deck rules. */
    fun checkLegality(lines: List<LegalLine>, rules: Map<String, LegalRule>): Legality {
        val problems = mutableListOf<LegalProblem>()
        // The same card on several lines counts once, by front-face name; the name shown is the first line's.
        val cards = LinkedHashMap<String, Entry>()
        for (l in lines) {
            if (l.quantity <= 0) continue
            val k = ruleKey(l.name)
            val c = cards[k]
            if (c != null) {
                c.quantity += l.quantity
                c.commander = c.commander || l.commander
            } else cards[k] = Entry(l.name, l.quantity, l.commander, rules[k])
        }
        val unchecked = cards.values.filter { it.rule == null }.map { it.name }.toSet()

        // The commanders.
        val commanders = cards.values.filter { it.commander }
        if (commanders.isEmpty()) problems.add(LegalProblem(ProblemKind.COMMANDER, "No commander is marked.", emptyList()))
        else if (commanders.size > 2) problems.add(
            LegalProblem(ProblemKind.COMMANDER, "${commanders.size} commanders are marked; a deck has one, or two that may pair up.", commanders.map { it.name }.sortedWith(ByName)),
        )
        else if (commanders.size == 1) {
            val c = commanders[0]
            if (c.rule != null && !c.rule.command) problems.add(LegalProblem(ProblemKind.COMMANDER, "${c.name} can't be a commander.", listOf(c.name)))
        } else {
            val (a, b) = commanders
            if (a.rule != null && b.rule != null) {
                // A Background may only lead next to a commander that chooses one.
                fun alone(x: Entry, y: Entry) = !x.rule!!.command && !("bg" in x.rule.pair && "choose-bg" in y.rule!!.pair)
                val bad = listOf(a, b).filter { alone(it, if (it === a) b else a) }
                if (bad.isNotEmpty()) {
                    for (x in bad.sortedWith { p, q -> byName(p.name, q.name) })
                        problems.add(LegalProblem(ProblemKind.COMMANDER, "${x.name} can't be a commander.", listOf(x.name)))
                } else if (!canPair(a.rule, a.name, b.rule, b.name)) {
                    problems.add(LegalProblem(ProblemKind.PAIR, "${a.name} and ${b.name} can't be commanders together.", listOf(a.name, b.name).sortedWith(ByName)))
                }
            }
        }

        // Exactly 100 cards, the commanders included.
        val total = cards.values.sumOf { it.quantity }
        if (total != 100) problems.add(LegalProblem(ProblemKind.COUNT, "The deck has $total cards; a Commander deck has exactly 100.", emptyList()))

        fun listed(kind: ProblemKind, label: String, pick: (Entry) -> String?) {
            val names = cards.values.mapNotNull(pick).sortedWith(ByName)
            if (names.isNotEmpty()) {
                problems.add(LegalProblem(kind, "$label: ${names.joinToString(", ")}.", cards.values.filter { pick(it) != null }.map { it.name }.sortedWith(ByName)))
            }
        }
        listed(ProblemKind.BANNED, "Banned in Commander") { if (it.rule?.legal == "banned") it.name else null }
        listed(ProblemKind.NOTLEGAL, "Not legal in Commander") { if (it.rule != null && it.rule.legal != "legal" && it.rule.legal != "banned") it.name else null }

        // The commanders' colours (only when every commander's are known).
        if (commanders.isNotEmpty() && commanders.all { it.rule != null }) {
            val allowed = commanders.flatMap { it.rule!!.ci.toList() }.toSet()
            listed(ProblemKind.COLOUR, "Outside the commander's colours") { if (it.rule != null && it.rule.ci.any { x -> x !in allowed }) it.name else null }
        }

        // One copy of each card, except basic lands and cards that allow more.
        listed(ProblemKind.COPIES, "More copies than allowed") {
            val limit = if (it.rule != null) it.rule.copies else if (BASICS.matches(ruleKey(it.name))) 0 else 1
            if (limit > 0 && it.quantity > limit) "${it.name} ×${it.quantity}" else null
        }

        return Legality(problems, unchecked.sortedWith(ByName))
    }
}

/**
 * The Commander rules of cards ([LegalRule], by front-face name), kept in a file with the time each was fetched from
 * Scryfall's collection lookup (by Scryfall id) and fetched again after a week. Offline or failing lookups keep the old
 * rules; cards Scryfall doesn't find stay unchecked. Since the legality check.
 */
class LegalRules(context: Context, private val scryfall: ScryfallApi, private val now: () -> Long = System::currentTimeMillis) {
    @Serializable
    private data class Saved(val at: Long, val rule: LegalRule)

    private val file = File(context.filesDir, "legal_rules.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var saved: MutableMap<String, Saved>? = null

    private fun read(): MutableMap<String, Saved> = saved ?: (
        runCatching { json.decodeFromString<Map<String, Saved>>(file.readText()).toMutableMap() }.getOrDefault(mutableMapOf())
        ).also { saved = it }

    /** The rules kept so far for these card names, however old (no network). */
    suspend fun saved(names: Collection<String>): Map<String, LegalRule> = lock.withLock {
        withContext(Dispatchers.IO) {
            val all = read()
            names.map { DeckLegality.ruleKey(it) }.mapNotNull { k -> all[k]?.let { k to it.rule } }.toMap()
        }
    }

    /** Whether the rules of every one of these cards are kept (no network). */
    suspend fun hasAll(names: Collection<String>): Boolean = saved(names).size == names.map { DeckLegality.ruleKey(it) }.toSet().size

    /**
     * The rules of these deck cards (name to Scryfall id): the kept ones, after looking up the cards that are missing or
     * older than a week. A lookup that fails leaves the kept rules as they are.
     */
    suspend fun forDeck(cards: Map<String, String>): Map<String, LegalRule> {
        val t = now()
        val wanted = lock.withLock {
            withContext(Dispatchers.IO) {
                val all = read()
                cards.entries.filter { (name, _) -> all[DeckLegality.ruleKey(name)].let { it == null || t - it.at > MAX_AGE_MS } }
            }
        }
        if (wanted.isNotEmpty()) {
            runCatching {
                val byId = wanted.groupBy({ it.value }, { DeckLegality.ruleKey(it.key) })
                val found = scryfall.collection(byId.keys.map { ScryfallApi.idIdentifier(it) })
                lock.withLock {
                    withContext(Dispatchers.IO) {
                        val all = read()
                        for (card in found) {
                            val rule = DeckLegality.ruleOf(card.toRuleCard())
                            for (k in byId[card.id].orEmpty() + DeckLegality.ruleKey(card.name)) all[k] = Saved(t, rule)
                        }
                        file.writeText(json.encodeToString(all))
                    }
                }
            }
        }
        return saved(cards.keys)
    }

    companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
