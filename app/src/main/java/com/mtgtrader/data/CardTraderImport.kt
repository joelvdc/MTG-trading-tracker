package com.mtgtrader.data

import kotlinx.serialization.json.JsonObject

/** One card line of a CardTrader order (rows for the same copy are merged, quantities added up). Since 1.26. */
data class OrderLine(
    val setCode: String,
    val setName: String,
    val name: String,
    /** As printed in the file: "044", "T 04/02". */
    val number: String,
    val quantity: Int,
    /** What one copy cost, in EUR. */
    val price: Double?,
    /** The app's condition code (NM, EX…). */
    val condition: String,
    val language: String,
    val foil: Boolean,
    val signed: Boolean,
    val altered: Boolean,
)

/** An order line and the card it was matched to (null when Scryfall didn't know it). */
data class OrderCandidate(
    val index: Int,
    val line: OrderLine,
    val card: CardRef?,
    val token: Boolean,
    val basicLand: Boolean,
    /** Matched by name only, so the printing may not be the one bought. */
    val guessed: Boolean = false,
)

/**
 * CardTrader's order export (an Excel sheet with one row per copy bought): reading the rows and
 * finding each card on Scryfall. Since 1.26.
 */
object CardTraderOrder {
    class NotAnOrder : Exception("This doesn't look like a CardTrader order (no \"Item Name\" and \"Set Code\" columns).")

    /**
     * CardTrader's conditions on the app's (Cardmarket's) scale. "Slightly Played" is Cardmarket's
     * Excellent rather than Played.
     */
    fun condition(raw: String?): String = when (raw?.trim()?.lowercase()) {
        "mint" -> "MT"
        "near mint" -> "NM"
        "slightly played" -> "EX"
        "moderately played" -> "LP"
        "played" -> "PL"
        "heavily played", "poor", "damaged" -> "PO"
        else -> parseCondition(raw)
    }

    fun parse(rows: List<List<String>>): List<OrderLine> {
        val headerAt = rows.indexOfFirst { r -> r.any { it.equals("Item Name", true) } }
        if (headerAt < 0) throw NotAnOrder()
        val header = rows[headerAt].map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val iName = col("item name") ?: throw NotAnOrder()
        val iSet = col("set code") ?: throw NotAnOrder()
        val iSetName = col("set name")
        val iNum = col("collector number")
        val iPrice = col("price in eur cents")
        val iQty = col("quantity")
        val iCond = col("condition")
        val iLang = col("language")
        val iFoil = col("foil/reverse", "foil")
        val iSigned = col("signed")
        val iAltered = col("altered")
        val iGame = col("game")

        fun flag(s: String?) = s?.trim()?.lowercase() in setOf("1", "true", "yes", "foil")
        val lines = rows.drop(headerAt + 1).mapNotNull { r ->
            fun v(i: Int?) = i?.let { r.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
            // Orders can mix games; only Magic cards belong in this app.
            if (v(iGame)?.contains("magic", ignoreCase = true) == false) return@mapNotNull null
            val name = v(iName) ?: return@mapNotNull null
            OrderLine(
                setCode = v(iSet).orEmpty(),
                setName = v(iSetName).orEmpty(),
                name = name,
                number = v(iNum).orEmpty(),
                quantity = v(iQty)?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 } ?: 1,
                price = v(iPrice)?.toDoubleOrNull()?.let { it / 100 },
                condition = condition(v(iCond)),
                language = parseLanguage(v(iLang)),
                foil = flag(v(iFoil)),
                signed = flag(v(iSigned)),
                altered = flag(v(iAltered)),
            )
        }
        // The same copy bought twice shows up as two rows.
        return lines.groupBy { it.copy(quantity = 0) }.map { (key, ls) -> key.copy(quantity = ls.sumOf { it.quantity }) }
    }

    /** "T 04/02" (token 4 of 2) → "4"; anything else null. */
    fun tokenNumber(number: String): String? =
        Regex("""^T\s*0*(\d+[a-z]?)\s*(/.*)?$""", RegexOption.IGNORE_CASE).find(number.trim())?.groupValues?.get(1)

    /** "044" → "44" (Scryfall has no leading zeros); "0" and "12a" stay as they are. */
    fun cleanNumber(number: String): String = number.trim().replaceFirst(Regex("^0+(?=\\d)"), "")

    /** "Qarsi Revenant (Borderless)" → "Qarsi Revenant": CardTrader adds the treatment to some names. */
    fun cleanName(name: String): String = name.replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim()

    fun isToken(card: ScryCard): Boolean =
        card.layout in setOf("token", "double_faced_token", "emblem") || card.typeLine.startsWith("Token")

    fun isBasicLand(card: ScryCard): Boolean = card.typeLine.contains("Basic") && card.typeLine.contains("Land")

    /** Where to look a line up first: its set and number, or the set's token set for "T…" numbers. */
    fun firstLookup(line: OrderLine): Pair<String, String>? {
        val set = line.setCode.lowercase().ifEmpty { return null }
        tokenNumber(line.number)?.let { return "t$set" to it }
        val n = cleanNumber(line.number).ifEmpty { return null }
        return set to n
    }

    /** CardTrader's own sub-set codes that Scryfall files under the main set: "CTDM" (Collectors) → "tdm". */
    fun alternativeSet(code: String): String? = code.lowercase().takeIf { it.length == 4 && it.startsWith("c") }?.drop(1)

    /**
     * Finds every line's card: by set and number in batches first, then one by one by name within the
     * set (The List, renamed sub-sets, tokens numbered differently), and last by name alone (marked as
     * guessed). [onProgress] gets (lines done, total).
     */
    suspend fun match(lines: List<OrderLine>, scryfall: ScryfallApi, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<OrderCandidate> {
        val found = HashMap<Int, Pair<ScryCard, Boolean>>()
        fun key(set: String, number: String) = "${set.lowercase()}|${number.lowercase()}"

        suspend fun batch(lookups: Map<Int, Pair<String, String>>) {
            if (lookups.isEmpty()) return
            val ids: List<JsonObject> = lookups.values.distinct().map { (s, n) -> ScryfallApi.setNumberIdentifier(s, n) }
            val byKey = scryfall.collection(ids).associateBy { key(it.set, it.collectorNumber) }
            for ((i, sn) in lookups) byKey[key(sn.first, sn.second)]?.let { found[i] = it to false }
        }

        val total = lines.size
        batch(lines.withIndex().mapNotNull { (i, l) -> firstLookup(l)?.let { i to it } }.toMap())
        onProgress(found.size, total)
        batch(
            lines.withIndex().filter { it.index !in found }.mapNotNull { (i, l) ->
                val alt = alternativeSet(l.setCode) ?: return@mapNotNull null
                i to (alt to cleanNumber(l.number))
            }.toMap(),
        )
        var done = found.size
        onProgress(done, total)
        for ((i, l) in lines.withIndex()) {
            if (i in found) continue
            val name = cleanName(l.name).substringBefore(" // ")
            val set = l.setCode.lowercase()
            val sets = listOfNotNull(set, alternativeSet(l.setCode), "t$set").filter { it.isNotEmpty() }
            val inSet = runCatching {
                scryfall.search("!\"$name\" (${sets.joinToString(" or ") { "e:$it" }}) include:extras unique:prints")
            }.getOrDefault(emptyList())
            val number = tokenNumber(l.number) ?: cleanNumber(l.number)
            // The List numbers its cards "ROE-190": prefer the printing whose number ends in the one bought.
            val exact = inSet.firstOrNull { number.isNotEmpty() && (it.collectorNumber == number || it.collectorNumber.endsWith("-$number")) }
            // Otherwise the set's first printing of that name: likely right, but worth a look ("158p" or "158s"?).
            val pick = exact ?: inSet.firstOrNull()
            if (pick != null) {
                found[i] = pick to (exact == null)
            } else {
                runCatching { scryfall.fuzzy(name) }.getOrNull()?.let { found[i] = it to true }
            }
            onProgress(++done, total)
        }
        return lines.mapIndexed { i, l ->
            val hit = found[i]
            OrderCandidate(
                index = i,
                line = l,
                card = hit?.first?.toRef(),
                token = hit?.first?.let(::isToken) ?: (tokenNumber(l.number) != null),
                basicLand = hit?.first?.let(::isBasicLand) ?: false,
                guessed = hit?.second ?: false,
            )
        }
    }
}
