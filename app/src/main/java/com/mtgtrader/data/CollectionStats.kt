package com.mtgtrader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Count cards (copies) or their value in the charts. */
enum class StatMode(val label: String) { CARDS("Cards"), VALUE("Value") }

/**
 * One slice or bar: its [label], how many copies and what they're worth, and the collection filter
 * that shows exactly those cards (null when there's no matching filter).
 */
data class StatEntry(
    val key: String,
    val label: String,
    val copies: Int,
    val value: Double,
    val filter: CollectionFilter? = null,
) {
    fun amount(mode: StatMode): Double = if (mode == StatMode.CARDS) copies.toDouble() else value
}

/** A card worth mentioning: most valuable, oldest, most played on EDHREC… */
data class StatCard(val row: CollectionRow, val note: String)

data class CollectionStatsResult(
    val copies: Int,
    val uniqueCards: Int,
    val printings: Int,
    val value: Double,
    val foilCopies: Int,
    val unpriced: Int,
    val withoutDetails: Int,
    val colors: List<StatEntry>,
    val identities: List<StatEntry>,
    val rarities: List<StatEntry>,
    val types: List<StatEntry>,
    /** Non-land cards by mana value: 0, 1, … 6, "7+". */
    val curve: List<StatEntry>,
    val topSets: List<StatEntry>,
    val years: List<StatEntry>,
    val finishes: List<StatEntry>,
    val conditions: List<StatEntry>,
    val languages: List<StatEntry>,
    val binders: List<StatEntry>,
    val inDecks: StatEntry,
    val notInDecks: StatEntry,
    val gameChangers: List<StatCard>,
    val edhrecTop: List<StatCard>,
    val mostValuable: List<StatCard>,
    val oldest: StatCard?,
) {
    val averageValue get() = if (copies > 0) value / copies else 0.0
}

/** Collection statistics for the stats screen. Since 1.21. */
object CollectionStats {
    /** Colour pie: the five colours (mono-coloured cards), multicoloured, colourless, lands. */
    val COLOR_GROUPS = listOf(
        "W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green",
        "M" to "Multicolor", "C" to "Colorless", "L" to "Lands",
    )

    /** Names of colour identities, keyed by WUBRG-ordered colours. */
    val IDENTITY_NAMES = mapOf(
        "" to "Colorless", "W" to "Mono-White", "U" to "Mono-Blue", "B" to "Mono-Black", "R" to "Mono-Red", "G" to "Mono-Green",
        "WU" to "Azorius", "UB" to "Dimir", "BR" to "Rakdos", "RG" to "Gruul", "WG" to "Selesnya",
        "WB" to "Orzhov", "UR" to "Izzet", "BG" to "Golgari", "WR" to "Boros", "UG" to "Simic",
        "WUB" to "Esper", "UBR" to "Grixis", "BRG" to "Jund", "WRG" to "Naya", "WUG" to "Bant",
        "WBG" to "Abzan", "WUR" to "Jeskai", "UBG" to "Sultai", "WBR" to "Mardu", "URG" to "Temur",
        "UBRG" to "Glint-Eye (no white)", "WBRG" to "Dune-Brood (no blue)", "WURG" to "Ink-Treader (no black)",
        "WUBG" to "Witch-Maw (no red)", "WUBR" to "Yore-Tiller (no green)", "WUBRG" to "Five colors",
    )

    private val TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Battle", "Land")
    private val RARITY_ORDER = listOf("common", "uncommon", "rare", "mythic", "special", "bonus")

    fun compute(
        rows: List<CollectionRow>,
        priceType: PriceType,
        binderNames: Map<Long, String>,
        deckCardNames: Set<String>,
        gameChangerNames: Set<String>,
        setDates: Map<String, String>,
    ): CollectionStatsResult {
        fun price(r: CollectionRow) = r.unitPrice(priceType) ?: 0.0
        fun value(r: CollectionRow) = price(r) * r.item.quantity

        /** Groups rows by [key] (null = left out) into entries, in [order] if given, else biggest first. */
        fun group(
            key: (CollectionRow) -> String?,
            label: (String) -> String = { it },
            filter: (String) -> CollectionFilter? = { null },
            order: List<String>? = null,
        ): List<StatEntry> {
            val groups = rows.groupBy(key).filterKeys { it != null }.map { (k, rs) ->
                StatEntry(k!!, label(k), rs.sumOf { it.item.quantity }, rs.sumOf(::value), filter(k))
            }
            return if (order != null) groups.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            else groups.sortedByDescending { it.copies }
        }

        val front = { r: CollectionRow -> r.item.card.name.substringBefore(" // ").lowercase() }

        val colors = group(
            key = { r ->
                val info = r.info ?: return@group null
                when {
                    "Land" in info.typeLine && info.colors.isEmpty() -> "L"
                    info.colors.isEmpty() -> "C"
                    info.colors.length > 1 -> "M"
                    else -> info.colors
                }
            },
            label = { k -> COLOR_GROUPS.first { it.first == k }.second },
            filter = { k ->
                when (k) {
                    "L" -> CollectionFilter(types = setOf("Land"))
                    "M" -> CollectionFilter(colors = setOf('M'))
                    "C" -> CollectionFilter(colors = setOf('C'), colorMatch = ColorMatch.EXACT)
                    else -> CollectionFilter(colors = setOf(k[0]), colorMatch = ColorMatch.EXACT)
                }
            },
            order = COLOR_GROUPS.map { it.first },
        )

        val identities = group(
            key = { r -> r.info?.let { MtgColors.normalize(it.colorIdentity.map(Char::toString)) } },
            label = { k -> IDENTITY_NAMES[k] ?: k },
            filter = { k -> CollectionFilter(colors = if (k.isEmpty()) setOf('C') else k.toSet(), colorMatch = if (k.isEmpty()) ColorMatch.EXACT else ColorMatch.IDENTITY) },
        )

        val rarities = group(
            key = { it.item.card.rarity.lowercase().ifEmpty { null } },
            label = { k -> k.replaceFirstChar(Char::uppercase) },
            filter = { k -> CollectionFilter(rarities = setOf(k)) },
            order = RARITY_ORDER,
        )

        // A card counts once for every type it has (an artifact creature is both).
        val types = TYPES.mapNotNull { t ->
            val rs = rows.filter { r -> r.info?.typeLine?.let { t in it } == true }
            if (rs.isEmpty()) null else StatEntry(t, t + if (t == "Sorcery") "" else "s", rs.sumOf { it.item.quantity }, rs.sumOf(::value), CollectionFilter(types = setOf(t)))
        }.map { if (it.key == "Sorcery") it.copy(label = "Sorceries") else it }

        val nonLand = rows.filter { r -> r.info != null && "Land" !in r.info.typeLine }
        val curve = (0..7).map { mv ->
            val rs = nonLand.filter { r -> val c = r.info!!.cmc.toInt(); if (mv == 7) c >= 7 else c == mv }
            StatEntry("$mv", if (mv == 7) "7+" else "$mv", rs.sumOf { it.item.quantity }, rs.sumOf(::value))
        }

        val sets = rows.groupBy { it.item.card.setCode.lowercase() }.map { (code, rs) ->
            StatEntry(code, rs.first().item.card.setName.ifEmpty { code.uppercase() }, rs.sumOf { it.item.quantity }, rs.sumOf(::value), CollectionFilter(sets = setOf(code)))
        }

        val years = rows.groupBy { r -> setDates[r.item.card.setCode.lowercase()]?.take(4) }
            .filterKeys { it != null }
            .map { (y, rs) -> StatEntry(y!!, y, rs.sumOf { it.item.quantity }, rs.sumOf(::value)) }
            .sortedBy { it.key }

        val finishes = group(
            key = { it.item.finish.name },
            label = { k -> when (Finish.valueOf(k)) { Finish.NONFOIL -> "Non-foil"; Finish.FOIL -> "Foil"; Finish.ETCHED -> "Etched" } },
            filter = { k -> CollectionFilter(finishes = setOf(Finish.valueOf(k))) },
            order = Finish.entries.map { it.name },
        )
        val conditions = group(
            key = { it.item.condition },
            label = { k -> CONDITIONS.firstOrNull { it.first == k }?.second ?: k },
            filter = { k -> CollectionFilter(conditions = setOf(k)) },
            order = CONDITIONS.map { it.first },
        )
        val languages = group(
            key = { it.item.language },
            label = { k -> LANGUAGES.firstOrNull { it.first == k }?.second ?: k },
            filter = { k -> CollectionFilter(languages = setOf(k)) },
        )
        val binders = group(
            key = { it.item.binderId.toString() },
            label = { k -> binderNames[k.toLong()] ?: Binder.UNSORTED_NAME },
        ).sortedByDescending { it.value }

        val (used, unused) = rows.partition { front(it) in deckCardNames }

        // One line per card name for the card lists.
        val byName = rows.groupBy(front)
        val gameChangers = byName.filterKeys { it in gameChangerNames }.values
            .map { rs -> StatCard(rs.maxBy(::price), "${rs.sumOf { it.item.quantity }}×") }
            .sortedBy { it.row.item.card.name }
        val edhrecTop = byName.values.mapNotNull { rs ->
            val rank = rs.firstNotNullOfOrNull { it.info?.edhrecRank } ?: return@mapNotNull null
            rank to rs
        }.sortedBy { it.first }.take(10).map { (rank, rs) -> StatCard(rs.maxBy(::price), "#$rank on EDHREC") }
        val mostValuable = rows.sortedByDescending(::price).take(10).map { StatCard(it, "${it.item.quantity}×") }
        val oldest = rows.mapNotNull { r -> setDates[r.item.card.setCode.lowercase()]?.let { it to r } }.minByOrNull { it.first }
            ?.let { (date, r) -> StatCard(r, date.take(4)) }

        return CollectionStatsResult(
            copies = rows.sumOf { it.item.quantity },
            uniqueCards = byName.size,
            printings = rows.map { it.item.card.scryfallId }.toSet().size,
            value = rows.sumOf(::value),
            foilCopies = rows.filter { it.item.foil }.sumOf { it.item.quantity },
            unpriced = rows.filter { it.unitPrice(priceType) == null }.sumOf { it.item.quantity },
            withoutDetails = rows.filter { it.info == null }.sumOf { it.item.quantity },
            colors = colors,
            identities = identities,
            rarities = rarities,
            types = types,
            curve = curve,
            topSets = sets,
            years = years,
            finishes = finishes,
            conditions = conditions,
            languages = languages,
            binders = binders,
            inDecks = StatEntry("in", "In a deck", used.sumOf { it.item.quantity }, used.sumOf(::value), CollectionFilter(decks = DeckFilter.IN_DECKS)),
            notInDecks = StatEntry("out", "In no deck", unused.sumOf { it.item.quantity }, unused.sumOf(::value), CollectionFilter(decks = DeckFilter.NOT_IN_DECKS)),
            gameChangers = gameChangers,
            edhrecTop = edhrecTop,
            mostValuable = mostValuable,
            oldest = oldest,
        )
    }
}

/**
 * The cards Wizards lists as Commander "Game Changers", from Scryfall (is:gamechanger), kept in a
 * file and refreshed weekly. Since 1.21.
 */
class GameChangers(context: Context, private val scryfall: ScryfallApi) {
    private val file = File(context.filesDir, "game_changers.txt")
    private val _names = MutableStateFlow<Set<String>>(emptySet())
    /** Lower-case front-face names. */
    val names: StateFlow<Set<String>> = _names

    suspend fun load() {
        val cached = withContext(Dispatchers.IO) { if (file.exists()) file.readLines().filter { it.isNotBlank() }.toSet() else emptySet() }
        if (cached.isNotEmpty()) _names.value = cached
        if (cached.isNotEmpty() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) return
        runCatching {
            val fresh = scryfall.searchAll("is:gamechanger", maxPages = 3).map { it.name.substringBefore(" // ").lowercase() }.toSet()
            if (fresh.isNotEmpty()) {
                _names.value = fresh
                withContext(Dispatchers.IO) { file.writeText(fresh.sorted().joinToString("\n")) }
            }
        }
    }

    private companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
