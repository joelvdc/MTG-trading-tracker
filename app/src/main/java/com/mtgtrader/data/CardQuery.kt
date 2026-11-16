package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlin.math.floor

/*
 * A deck's "syntax filter" in Scryfall's search syntax (https://scryfall.com/docs/syntax), answered locally for the
 * common keys. Keys it can't answer are listed by CardQuery.unsupportedKeys and ignored by CardQuery.matches (the
 * server route /api/cards/query asks Scryfall for those). The website's lib/cardquery.ts does the same; both are
 * tested against the shared cases in web/test/fixtures/cardquery-cases.json, so the phone and the website agree.
 */

/** What the filter needs to know about a card. Split and double-faced cards are named "Fire // Ice". */
@Serializable
data class QueryCard(
    val name: String,
    val typeLine: String,
    /** All faces' text, joined with "\n". */
    val oracleText: String,
    /** "{1}{U} // {2}{U}" for split and double-faced cards. */
    val manaCost: String,
    val mv: Double,
    val colors: List<String>,
    val colorIdentity: List<String>,
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    /** "common", "uncommon", "rare" or "mythic". */
    val rarity: String? = null,
    val setCode: String,
    val commander: Boolean,
    val gameChanger: Boolean,
    val priceEur: Double? = null,
    /** The deck line's category. */
    val category: String,
    val tags: List<String> = emptyList(),
)

/** The deck filter, in Scryfall's search syntax. */
object CardQuery {
    /** The result of [parse]: a [Query], or an [Error] with a message ready to show next to the field. */
    sealed interface Parsed

    /** A filter that parsed: [root] is the tree of terms, [unsupported] the keys only Scryfall can answer. */
    data class Query(val root: Node, val unsupported: List<String>) : Parsed

    /** A filter that didn't parse. */
    data class Error(val error: String) : Parsed

    /** A filter's terms, combined with and, or and not. */
    sealed interface Node {
        data class And(val items: List<Node>) : Node
        data class Or(val items: List<Node>) : Node
        data class Not(val item: Node) : Node

        /** A single term; [test] answers null when this term can't be answered locally. */
        data class Term(val test: (QueryCard) -> Boolean?) : Node
    }

    /** Parses a filter. Errors are short plain-English messages ready to show next to the field. */
    fun parse(text: String): Parsed = try {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) Query(Node.And(emptyList()), emptyList()) else Parser(tokens).parse()
    } catch (e: SyntaxProblem) {
        Error(e.message.orEmpty())
    }

    /**
     * Whether the card fits the filter. Terms this can't answer (see [unsupportedKeys]) count as "unknown", which
     * doesn't exclude a card.
     */
    fun matches(q: Query, card: QueryCard): Boolean = evaluate(q.root, card) != false

    /** The keys (and unknown `is:` values) in the filter that only Scryfall can answer. */
    fun unsupportedKeys(q: Query): List<String> = q.unsupported

    /** Whether [parse] gave an error instead of a filter. */
    fun isError(p: Parsed): Boolean = p is Error

    /**
     * Scryfall `q` strings that check [query] against [names] in groups: `(query) (!"A" or !"B")`, each short enough
     * for its URL (encoded) to stay under [maxLength] characters (a single very long name still gets its own group).
     */
    fun scryfallBatches(query: String, names: List<String>, maxLength: Int = 1500): List<String> {
        val head = "(${query.trimJs()}) ("
        val budget = maxLength - "https://api.scryfall.com/cards/search?unique=cards&q=".length
        fun size(s: String) = encodeUriComponent(s).length
        val out = mutableListOf<String>()
        var group = mutableListOf<String>()
        var used = size(head) + size(")")
        fun flush() {
            if (group.isNotEmpty()) out.add(head + group.joinToString(" or ") + ")")
            group = mutableListOf()
            used = size(head) + size(")")
        }
        for (name in names.distinct()) {
            val part = quoteName(name)
            val add = size(part) + if (group.isNotEmpty()) size(" or ") else 0
            if (group.isNotEmpty() && used + add > budget) flush()
            group.add(part)
            used += size(part) + if (group.size > 1) size(" or ") else 0
        }
        flush()
        return out
    }
}

/** A problem with the filter's text, as opposed to a bug: its message is shown to the owner as it is. */
private class SyntaxProblem(message: String) : Exception(message)

private fun fail(message: String): Nothing = throw SyntaxProblem(message)

// ---- Tokens -------------------------------------------------------------------------------------

private data class Token(val kind: Kind, val neg: Boolean = false, val text: String = "") {
    enum class Kind { OPEN, CLOSE, OR, TERM }
}

/** JavaScript's `\s`: the characters the tokens are split on and trimmed away. */
private fun isSpace(c: Char): Boolean = when (c) {
    ' ', '\t', '\n', '\u000B', '\u000C', '\r', '\u00A0', '\u1680', '\u2028', '\u2029', '\u202F', '\u205F', '\u3000', '\uFEFF' -> true
    else -> c in '\u2000'..'\u200A'
}

/** JavaScript's String.trim, with the same spaces. */
private fun String.trimJs(): String {
    var start = 0
    var end = length
    while (start < end && isSpace(this[start])) start++
    while (end > start && isSpace(this[end - 1])) end--
    return substring(start, end)
}

private fun tokenize(text: String): List<Token> {
    val out = mutableListOf<Token>()
    val n = text.length
    var i = 0
    while (i < n) {
        if (isSpace(text[i])) {
            i++
            continue
        }
        var neg = false
        if (text[i] == '-' && i + 1 < n && !isSpace(text[i + 1])) {
            neg = true
            i++
        }
        if (text[i] == '(') {
            out.add(Token(Token.Kind.OPEN, neg))
            i++
            continue
        }
        if (text[i] == ')') {
            if (neg) fail("A - must be followed by a search term.")
            out.add(Token(Token.Kind.CLOSE))
            i++
            continue
        }
        val start = i
        while (i < n && !isSpace(text[i]) && text[i] != ')') {
            if (text[i] == '"') {
                i++
                while (i < n && text[i] != '"') i += if (text[i] == '\\') 2 else 1
                if (i >= n) fail("Missing closing \" after ${text.substring(start).take(30)}")
            }
            i++
        }
        val word = text.substring(start, i)
        val lower = word.lowercase()
        if (!neg && lower == "or") out.add(Token(Token.Kind.OR))
        else if (!neg && lower == "and") continue
        else out.add(Token(Token.Kind.TERM, neg, word))
    }
    return out
}

// ---- Values -------------------------------------------------------------------------------------

/** The value's text without its surrounding quotes, with `\"` and `\\` unescaped. */
private fun unquote(s: String): String {
    if (s.length < 2 || !s.startsWith('"') || !s.endsWith('"')) return s
    val inner = s.substring(1, s.length - 1)
    val out = StringBuilder(inner.length)
    var i = 0
    while (i < inner.length) {
        val c = inner[i]
        if (c == '\\' && i + 1 < inner.length && (inner[i + 1] == '"' || inner[i + 1] == '\\')) {
            out.append(inner[i + 1])
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

private fun compare(a: Double, op: String, b: Double): Boolean = when (op) {
    ":", "=" -> a == b
    "!=" -> a != b
    "<" -> a < b
    "<=" -> a <= b
    ">" -> a > b
    else -> a >= b
}

private fun compare(a: Int, op: String, b: Int): Boolean = when (op) {
    ":", "=" -> a == b
    "!=" -> a != b
    "<" -> a < b
    "<=" -> a <= b
    ">" -> a > b
    else -> a >= b
}

private const val COLOR_LETTERS = "wubrg"

private val NAMED_COLORS = mapOf(
    "white" to "w", "blue" to "u", "black" to "b", "red" to "r", "green" to "g",
    "azorius" to "wu", "dimir" to "ub", "rakdos" to "br", "gruul" to "rg", "selesnya" to "gw", "orzhov" to "wb",
    "izzet" to "ur", "golgari" to "bg", "boros" to "rw", "simic" to "gu",
    "bant" to "gwu", "esper" to "wub", "grixis" to "ubr", "jund" to "brg", "naya" to "rgw",
    "abzan" to "wbg", "jeskai" to "urw", "sultai" to "bgu", "mardu" to "rwb", "temur" to "gur",
)

/** The colour set a query value means; null for multicolor (the empty set means colorless). */
private fun colorSet(value: String): Set<String>? {
    val v = value.lowercase()
    if (v == "m" || v == "multicolor" || v == "multicolour") return null
    if (v == "c" || v == "colorless" || v == "colourless") return emptySet()
    val letters = NAMED_COLORS[v] ?: v
    if (letters.any { it !in COLOR_LETTERS }) fail("\"$value\" isn't a colour (use w u b r g c, or a name like azorius).")
    return letters.map { it.toString() }.toSet()
}

private fun colorTest(key: String, op: String, value: String, field: (QueryCard) -> List<String>): (QueryCard) -> Boolean? {
    val q = colorSet(value)
    if (q == null) return { c -> field(c).toSet().size >= 2 }
    // "c:" means "at least these colours" ("c:c" colorless only), "id:" means "fits inside this identity".
    val eff = if (op != ":") op else when {
        key == "id" -> "<="
        q.isEmpty() -> "="
        else -> ">="
    }
    return { c ->
        val a = field(c).map { it.lowercase() }.toSet()
        val sub = a.all { it in q } // a is inside q
        val sup = q.all { it in a } // a covers q
        when (eff) {
            "=" -> sub && sup
            "!=" -> !(sub && sup)
            "<=" -> sub
            ">=" -> sup
            "<" -> sub && !sup
            else -> sup && !sub
        }
    }
}

private val FLOAT_PREFIX = Regex("""[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?""")

/** JavaScript's parseFloat: the number at the start of the text, or NaN. */
private fun parseFloatJs(s: String): Double {
    var start = 0
    while (start < s.length && isSpace(s[start])) start++
    val rest = s.substring(start)
    if (rest.startsWith("+Infinity") || rest.startsWith("Infinity")) return Double.POSITIVE_INFINITY
    if (rest.startsWith("-Infinity")) return Double.NEGATIVE_INFINITY
    val m = FLOAT_PREFIX.find(rest, 0)
    if (m == null || m.range.first != 0) return Double.NaN
    return m.value.toDoubleOrNull() ?: Double.NaN
}

/** A power, toughness or loyalty as a number: "*" counts as 0 ("1+*" as 1), and no number at all as null. */
private fun stat(s: String?): Double? {
    if (s == null) return null
    val n = parseFloatJs(s)
    return if (n.isNaN()) (if (s.contains('*')) 0.0 else null) else n
}

/** The number a key reads from a card, or null when the card has none. */
private fun numericValue(key: String, c: QueryCard): Double? = when (key) {
    "mv", "cmc" -> c.mv
    "pow", "power" -> stat(c.power)
    "tou", "toughness" -> stat(c.toughness)
    "loy", "loyalty" -> stat(c.loyalty)
    "eur", "price" -> c.priceEur
    else -> null
}

private val NUMERIC_KEYS = setOf("mv", "cmc", "pow", "power", "tou", "toughness", "loy", "loyalty", "eur", "price")
private val NUMBER = Regex("""-?\d+(\.\d+)?""")

private fun isInteger(d: Double): Boolean = d.isFinite() && d == floor(d)

private fun numberTest(key: String, op: String, value: String): (QueryCard) -> Boolean? {
    val v = value.lowercase()
    if ((v == "even" || v == "odd") && (key == "mv" || key == "cmc")) {
        val even = v == "even"
        return { c ->
            val whole = isInteger(c.mv)
            if (even) whole && c.mv % 2.0 == 0.0 else whole && c.mv % 2.0 != 0.0
        }
    }
    if (NUMBER.matchEntire(v) != null) {
        val b = v.toDouble()
        return { c -> numericValue(key, c)?.let { compare(it, op, b) } ?: false }
    }
    // Or another number of the card: pow>tou.
    if (v in NUMERIC_KEYS && v != "eur" && v != "price") {
        return { c ->
            val a = numericValue(key, c)
            val b = numericValue(v, c)
            a != null && b != null && compare(a, op, b)
        }
    }
    return fail("Expected a number after $key$op (not \"$value\").")
}

private val RARITIES = listOf("common", "uncommon", "rare", "mythic")
private val RARITY_LETTERS = mapOf("c" to "common", "u" to "uncommon", "r" to "rare", "m" to "mythic")

private fun rarityTest(op: String, value: String): (QueryCard) -> Boolean? {
    val v = value.lowercase()
    val rank = RARITIES.indexOf(RARITY_LETTERS[v] ?: v)
    if (rank < 0) fail("\"$value\" isn't a rarity (common, uncommon, rare, mythic).")
    return { c ->
        val a = c.rarity?.let { RARITIES.indexOf(it.lowercase()) } ?: -1
        a >= 0 && compare(a, op, rank)
    }
}

private val MANA_SYMBOL = Regex("""\{([^}]*)\}|(\d+|[a-zA-Z])""")

/** Mana symbols of a cost or query: "{2}{W}{W}" and "2WW" both give ["2", "W", "W"]. */
private fun symbols(s: String): List<String> = MANA_SYMBOL.findAll(s).map { m ->
    (m.groups[1]?.value ?: m.groups[2]?.value ?: "").uppercase()
}.toList()

private fun manaTest(op: String, value: String): (QueryCard) -> Boolean? {
    if (op != ":" && op != "=") fail("m: only works with : or =.")
    val want = symbols(value)
    if (want.isEmpty()) fail("\"$value\" has no mana symbols (like {G}{G}).")
    return { c ->
        val left = symbols(c.manaCost).toMutableList()
        want.all { symbol ->
            val i = left.indexOf(symbol)
            if (i < 0) return@all false
            left.removeAt(i)
            true
        }
    }
}

private fun containsTest(get: (QueryCard) -> String, value: String): (QueryCard) -> Boolean? {
    val v = value.lowercase()
    return { c -> get(c).lowercase().contains(v) }
}

/** Oracle text search, where `~` stands for the card's name. */
private fun oracleTest(value: String): (QueryCard) -> Boolean? {
    val v = value.lowercase()
    return { c -> c.oracleText.lowercase().contains(v.replace("~", c.name.lowercase())) }
}

private fun setTest(value: String): (QueryCard) -> Boolean? {
    val v = value.lowercase()
    return { c -> c.setCode.lowercase() == v }
}

private fun tagTest(value: String): (QueryCard) -> Boolean? {
    val v = value.lowercase()
    return { c -> c.tags.any { it.lowercase().contains(v) } }
}

private fun textOnly(key: String, op: String) {
    if (op != ":" && op != "=") fail("$key: only works with : (like $key:word).")
}

private val UNSUPPORTED: (QueryCard) -> Boolean? = { null }
private val COMMANDER: (QueryCard) -> Boolean? = { it.commander }
private val GAME_CHANGER: (QueryCard) -> Boolean? = { it.gameChanger }
private val SPLIT_OR_DFC: (QueryCard) -> Boolean? = { it.name.contains("//") }

// ---- Terms --------------------------------------------------------------------------------------

private val KEY_TERM = Regex("""^([a-zA-Z_]+)(>=|<=|!=|:|=|<|>)([\s\S]*)$""")

private fun compileTerm(text: String, unsupported: MutableSet<String>): (QueryCard) -> Boolean? {
    if (text.startsWith("!")) {
        val exact = unquote(text.substring(1)).lowercase()
        if (exact.isEmpty()) fail("Nothing after !.")
        return { c -> c.name.lowercase() == exact }
    }
    val m = KEY_TERM.matchEntire(text) ?: return containsTest({ it.name }, unquote(text))
    val key = m.groupValues[1].lowercase()
    val op = m.groupValues[2]
    val value = unquote(m.groupValues[3])
    if (value.isEmpty()) fail("Nothing after ${m.groupValues[1]}$op")
    return when (key) {
        "name" -> {
            textOnly(key, op)
            containsTest({ it.name }, value)
        }
        "o", "oracle" -> {
            textOnly(key, op)
            oracleTest(value)
        }
        "t", "type" -> {
            textOnly(key, op)
            containsTest({ it.typeLine }, value)
        }
        "c", "color", "colour" -> colorTest("c", op, value) { it.colors }
        "id", "identity", "ci" -> colorTest("id", op, value) { it.colorIdentity }
        "mv", "cmc", "pow", "power", "tou", "toughness", "loy", "loyalty", "eur", "price" -> numberTest(key, op, value)
        "r", "rarity" -> rarityTest(op, value)
        "s", "set", "e", "edition" -> {
            textOnly(key, op)
            setTest(value)
        }
        "m", "mana" -> manaTest(op, value)
        "cat", "category" -> {
            textOnly(key, op)
            containsTest({ it.category }, value)
        }
        "tag" -> {
            textOnly(key, op)
            tagTest(value)
        }
        "is" -> {
            val v = value.lowercase()
            when (v) {
                "commander" -> COMMANDER
                "gamechanger" -> GAME_CHANGER
                "split", "dfc" -> SPLIT_OR_DFC
                else -> {
                    unsupported.add("is:$v")
                    UNSUPPORTED
                }
            }
        }
        else -> {
            unsupported.add(key)
            UNSUPPORTED
        }
    }
}

// ---- Parser -------------------------------------------------------------------------------------

private class Parser(private val tokens: List<Token>) {
    private val unsupported = sortedSetOf<String>()
    private var pos = 0
    private var last = ""

    fun parse(): CardQuery.Query {
        val root = parseOr()
        if (pos < tokens.size) fail("Unexpected ) after ${quotedLast("the start")} (no matching \"(\").")
        return CardQuery.Query(root, unsupported.toList())
    }

    /** [last] in quotes for an error message, or [whenNone] when no term came before. */
    private fun quotedLast(whenNone: String): String = if (last.isEmpty()) whenNone else "\"$last\""

    private fun parseOr(): CardQuery.Node {
        val parts = mutableListOf(parseAnd())
        while (pos < tokens.size && tokens[pos].kind == Token.Kind.OR) {
            pos++
            if (pos >= tokens.size || tokens[pos].kind == Token.Kind.CLOSE) fail("Nothing after or.")
            parts.add(parseAnd())
        }
        return if (parts.size == 1) parts[0] else CardQuery.Node.Or(parts)
    }

    private fun parseAnd(): CardQuery.Node {
        val items = mutableListOf<CardQuery.Node>()
        while (true) {
            val t = tokens.getOrNull(pos) ?: break
            if (t.kind == Token.Kind.CLOSE || t.kind == Token.Kind.OR) break
            pos++
            val node = if (t.kind == Token.Kind.OPEN) {
                if (tokens.getOrNull(pos)?.kind == Token.Kind.CLOSE) fail("Empty ( ).")
                val inner = parseOr()
                if (tokens.getOrNull(pos)?.kind != Token.Kind.CLOSE) {
                    fail("Missing ) after ${quotedLast("(")}")
                }
                pos++
                inner
            } else {
                last = t.text
                CardQuery.Node.Term(compileTerm(last, unsupported))
            }
            items.add(if (t.neg) CardQuery.Node.Not(node) else node)
        }
        if (items.isEmpty()) {
            fail(if (tokens.getOrNull(pos)?.kind == Token.Kind.OR) "Nothing before or." else "Nothing to search for here.")
        }
        return if (items.size == 1) items[0] else CardQuery.Node.And(items)
    }
}

private fun evaluate(n: CardQuery.Node, c: QueryCard): Boolean? = when (n) {
    is CardQuery.Node.Term -> n.test(c)
    is CardQuery.Node.Not -> evaluate(n.item, c)?.let { !it }
    is CardQuery.Node.And -> evaluateAnd(n.items, c)
    is CardQuery.Node.Or -> evaluateOr(n.items, c)
}

private fun evaluateAnd(items: List<CardQuery.Node>, c: QueryCard): Boolean? {
    var unknown = false
    for (i in items) {
        val r = evaluate(i, c)
        if (r == false) return false
        if (r == null) unknown = true
    }
    return if (unknown) null else true
}

private fun evaluateOr(items: List<CardQuery.Node>, c: QueryCard): Boolean? {
    var unknown = false
    for (i in items) {
        val r = evaluate(i, c)
        if (r == true) return true
        if (r == null) unknown = true
    }
    return if (unknown) null else false
}

// ---- Asking Scryfall (for the unsupported keys) -------------------------------------------------

private fun quoteName(n: String): String {
    val escaped = n.replace("\\", "\\\\").replace("\"", "\\\"")
    return "!\"$escaped\""
}

private const val URI_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
private const val HEX = "0123456789ABCDEF"

/** JavaScript's encodeURIComponent, code point by code point. */
private fun encodeUriComponent(s: String): String {
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c in URI_SAFE) {
            out.append(c)
            i++
            continue
        }
        val codePoint = s.codePointAt(i)
        for (b in String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)) {
            val v = b.toInt() and 0xFF
            out.append('%').append(HEX[v shr 4]).append(HEX[v and 0xF])
        }
        i += Character.charCount(codePoint)
    }
    return out.toString()
}
