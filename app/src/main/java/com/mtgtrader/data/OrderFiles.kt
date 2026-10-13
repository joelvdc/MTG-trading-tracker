package com.mtgtrader.data

enum class OrderShop(val label: String) { CARDTRADER("CardTrader"), CARDMARKET("Cardmarket") }

/** The order files the app reads. Since 1.32. */
enum class OrderFormat(val shop: OrderShop, val label: String) {
    /** CardTrader's order export (Excel): one row per copy, with set, number, condition, language and finish. */
    CARDTRADER(OrderShop.CARDTRADER, "CardTrader order"),

    /** Cardmarket's "ArticlesFromShipment" file: product numbers with condition, language and finish, no names. */
    CARDMARKET_SHIPMENT(OrderShop.CARDMARKET, "Cardmarket shipment"),

    /** Cardmarket's "Purchased Articles" of a month: names and product numbers, no condition, language or finish. */
    CARDMARKET_MONTHLY(OrderShop.CARDMARKET, "Cardmarket purchases"),
}

/** An order file's card lines. [otherItems] counts what was left out: other games, sleeves, sealed products. */
data class OrderFile(val format: OrderFormat, val lines: List<OrderLine>, val otherItems: Int = 0, val fileName: String? = null) {
    val shop get() = format.shop
}

/**
 * One reader for every shop's order file: it recognises the file by its column names and turns it
 * into the same [OrderLine]s, so importing an order, checking a package and setting purchase prices
 * work for all of them. Since 1.32.
 */
object OrderFiles {
    class NotAnOrder : Exception(
        "This isn't an order file the app knows. It reads CardTrader's order export (Excel) and Cardmarket's " +
            "“ArticlesFromShipment” and “Purchased Articles” files (CSV).",
    )

    /** Cardmarket's condition numbers, 1 to 7. */
    private val CM_CONDITIONS = listOf("MT", "NM", "EX", "GD", "LP", "PL", "PO")

    /** Cardmarket's language numbers. */
    private val CM_LANGUAGES = mapOf(
        1 to "EN", 2 to "FR", 3 to "DE", 4 to "ES", 5 to "IT", 6 to "ZHS", 7 to "JA", 8 to "PT", 9 to "RU", 10 to "KO", 11 to "ZHT",
    )

    fun cardmarketCondition(number: Int?): String = number?.let { CM_CONDITIONS.getOrNull(it - 1) } ?: "NM"

    fun cardmarketLanguage(number: Int?): String? = CM_LANGUAGES[number]

    fun read(bytes: ByteArray, fileName: String? = null): OrderFile = parse(Spreadsheet.read(bytes), fileName)

    fun parse(rows: List<List<String>>, fileName: String? = null): OrderFile {
        for ((at, row) in rows.withIndex()) {
            val h = row.map { it.trim().lowercase() }
            return when {
                "idproduct" in h -> shipment(h, rows.drop(at + 1), fileName)
                "product id" in h && "article value" in h -> monthly(h, rows.drop(at + 1), fileName)
                "item name" in h -> cardTrader(rows, fileName)
                else -> continue
            }
        }
        throw NotAnOrder()
    }

    private fun number(s: String?) = s?.replace(',', '.')?.toDoubleOrNull()

    private fun merged(lines: List<OrderLine>) =
        lines.groupBy { it.copy(quantity = 0) }.map { (key, same) -> key.copy(quantity = same.sumOf { it.quantity }) }

    private fun cardTrader(rows: List<List<String>>, fileName: String?): OrderFile {
        // "cardtrader_order_20260623hxrahx.xls": the day it was placed, and its code.
        val stamp = fileName?.let { Regex("""cardtrader_order_((20\d{2})(\d{2})(\d{2})\w*)""", RegexOption.IGNORE_CASE).find(it) }
        val date = stamp?.let { "${it.groupValues[2]}-${it.groupValues[3]}-${it.groupValues[4]}" }
        val lines = CardTraderOrder.parse(rows).map { it.copy(date = date, order = stamp?.groupValues?.get(1)) }
        return OrderFile(OrderFormat.CARDTRADER, lines, 0, fileName)
    }

    private fun shipment(h: List<String>, rows: List<List<String>>, fileName: String?): OrderFile {
        fun col(name: String) = h.indexOf(name).takeIf { it >= 0 }
        val iProduct = col("idproduct") ?: throw NotAnOrder()
        val iCount = col("groupcount")
        val iPrice = col("price")
        val iLang = col("idlanguage")
        val iCond = col("condition")
        val iFoil = col("isfoil")
        val iSigned = col("issigned")
        val iAltered = col("isaltered")
        val iPlayset = col("isplayset")
        // "ArticlesFromShipment1284353365.csv"
        val order = fileName?.let { Regex("""Shipment\D*(\d{4,})""", RegexOption.IGNORE_CASE).find(it) }?.groupValues?.get(1)
        val lines = rows.mapNotNull { r ->
            fun v(i: Int?) = i?.let { r.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
            fun flag(i: Int?) = v(i).let { it != null && it != "0" && !it.equals("false", ignoreCase = true) }
            val product = v(iProduct)?.toIntOrNull() ?: return@mapNotNull null
            val playset = flag(iPlayset)
            val groups = number(v(iCount))?.toInt()?.takeIf { it > 0 } ?: 1
            val language = cardmarketLanguage(v(iLang)?.toIntOrNull())
            OrderLine(
                setCode = "", setName = "", name = "", number = "",
                quantity = groups * if (playset) 4 else 1,
                // A playset is one article: its price covers the four cards.
                price = number(v(iPrice))?.let { if (playset) it / 4 else it },
                condition = cardmarketCondition(v(iCond)?.toIntOrNull()),
                language = language ?: "EN",
                foil = flag(iFoil), signed = flag(iSigned), altered = flag(iAltered),
                productId = product, languageKnown = language != null, playset = playset, order = order,
            )
        }
        return OrderFile(OrderFormat.CARDMARKET_SHIPMENT, merged(lines), 0, fileName)
    }

    private fun monthly(h: List<String>, rows: List<List<String>>, fileName: String?): OrderFile {
        fun col(name: String) = h.indexOf(name).takeIf { it >= 0 }
        val iProduct = col("product id") ?: throw NotAnOrder()
        val iName = col("article")
        val iSet = col("expansion")
        val iCategory = col("category")
        val iAmount = col("amount")
        val iValue = col("article value")
        val iOrder = col("shipment nr.")
        val iDate = col("date of purchase")
        var other = 0
        val lines = rows.mapNotNull { r ->
            fun v(i: Int?) = i?.let { r.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
            val product = v(iProduct)?.toIntOrNull() ?: return@mapNotNull null
            // The same file lists sleeves, sealed products and other games.
            if (v(iCategory)?.equals("Magic Single", ignoreCase = true) == false) {
                other++
                return@mapNotNull null
            }
            OrderLine(
                setCode = "", setName = v(iSet).orEmpty(), name = v(iName).orEmpty(), number = "",
                quantity = number(v(iAmount))?.toInt()?.takeIf { it > 0 } ?: 1,
                price = number(v(iValue)),
                condition = "NM", language = "EN", foil = false, signed = false, altered = false,
                productId = product, versionKnown = false,
                date = v(iDate)?.take(10), order = v(iOrder),
            )
        }
        return OrderFile(OrderFormat.CARDMARKET_MONTHLY, merged(lines), other, fileName)
    }

    /**
     * Finds every line's printing on Scryfall, for the import and the package check. CardTrader's lines
     * go by set and number ([CardTraderOrder.match]); Cardmarket's by product number, one request each,
     * and for a product Scryfall doesn't know by the name in the file or in the app's Cardmarket product
     * list ([productNames]), marked as guessed. [onProgress] gets (done, total).
     */
    suspend fun match(
        file: OrderFile,
        scryfall: ScryfallApi,
        productNames: suspend (List<Int>) -> Map<Int, String> = { emptyMap() },
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<OrderCandidate> {
        if (file.format == OrderFormat.CARDTRADER) return CardTraderOrder.match(file.lines, scryfall, onProgress)
        val ids = file.lines.mapNotNull { it.productId }.distinct()
        val names = productNames(ids)
        val cards = HashMap<Int, Pair<ScryCard, Boolean>>()
        ids.forEachIndexed { i, id ->
            val exact = scryfall.byCardmarketId(id)
            if (exact != null) {
                cards[id] = exact to false
            } else {
                val name = file.lines.first { it.productId == id }.name.ifEmpty { names[id].orEmpty() }
                val clean = CardTraderOrder.cleanName(name).substringBefore(" // ").substringBefore(" / ")
                if (clean.isNotEmpty()) runCatching { scryfall.fuzzy(clean) }.getOrNull()?.let { cards[id] = it to true }
            }
            onProgress(i + 1, ids.size)
        }
        return file.lines.mapIndexed { i, l ->
            val hit = cards[l.productId]
            val card = hit?.first
            OrderCandidate(
                index = i,
                line = when {
                    card != null && hit?.second == false -> l.copy(
                        name = l.name.ifEmpty { card.name }, setCode = card.set.uppercase(),
                        setName = l.setName.ifEmpty { card.setName }, number = card.collectorNumber,
                    )
                    else -> l.copy(name = l.name.ifEmpty { names[l.productId].orEmpty() })
                },
                card = card?.toRef(),
                token = card?.let(CardTraderOrder::isToken) ?: false,
                basicLand = card?.let(CardTraderOrder::isBasicLand) ?: false,
                guessed = hit?.second ?: false,
            )
        }
    }

    // ---- Card names and set codes, as the shops write them --------------------------------------

    /** "Sol Ring (V.2)" and "Sol Ring" compare equal; so do "Æther Vial" and "Aether Vial". */
    fun normalName(name: String): String =
        CardTraderOrder.cleanName(CardTraderOrder.cleanName(name)).lowercase().replace("æ", "ae").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** A card's name and each face's: Cardmarket's older files write "A / B" where Scryfall has "A // B". */
    fun names(name: String): Set<String> = (listOf(name) + name.split(Regex(" //? "))).map(::normalName).filter { it.isNotEmpty() }.toSet()

    private val LIST_SETS = setOf("mb1", "mb2", "fmb1", "plist", "plist2", "plst", "ulst", "cmb1", "cmb2")
    private val BASICS = setOf("plains", "island", "swamp", "mountain", "forest", "wastes")

    fun isBasicLandName(name: String) = names(name).any { n -> n in BASICS || n.removePrefix("snow covered ") in BASICS }

    /**
     * Whether CardTrader's set code [shop] is Scryfall's [own]: the same, one letter more (CRVR is
     * Ravnica Remastered's collector cards, LTRH the hobbits' share of LTR, FDNS, DCLB), "PRE" + set
     * for prerelease cards (Scryfall: "P" + set), the letters in another order (LTCP, PLTC), or any
     * two of The List's and Mystery Booster's many codes.
     */
    fun sameSet(shop: String, own: String): Boolean {
        val a = shop.lowercase()
        val b = own.lowercase()
        if (a == b || a.toList().sorted() == b.toList().sorted()) return true
        if (a.length == b.length + 1 && (a.startsWith(b) || a.endsWith(b))) return true
        if (b.startsWith("p") && a == "pre" + b.drop(1)) return true
        return a in LIST_SETS && b in LIST_SETS
    }

    fun isListSet(code: String) = code.lowercase() in LIST_SETS

    /** "044" and "44", "546e" and "546", "APC-105" and "105" are the same number. */
    fun plainNumber(number: String): String = number.substringAfterLast('-').filter(Char::isDigit).trimStart('0')
}
