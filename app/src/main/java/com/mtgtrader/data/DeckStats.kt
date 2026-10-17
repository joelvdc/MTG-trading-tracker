package com.mtgtrader.data

/** One of a deck's cards with what the deck stats need: its line in the deck and its details from Scryfall (once known). Since 1.44. */
data class StatsCard(
    val name: String,
    val scryfallId: String,
    val quantity: Int,
    val cmc: Double,
    /** Archidekt's card types, used until Scryfall's type line is known. */
    val types: String,
    val commander: Boolean,
    val typeLine: String?,
    val manaCost: String?,
    val producedMana: String?,
    val oracleText: String?,
    val tokens: String?,
) {
    private val faceTypes get() = (typeLine?.takeIf { it.isNotEmpty() } ?: types).split(" // ")

    /** Whether a face is a land: types before the dash, e.g. "Legendary Land — Desert". */
    private fun landFace(type: String) = "Land" in type.substringBefore(" — ").split(' ')

    /** A land on either face, so spell // land cards (Malakir Rebirth) count as lands. */
    val isLand get() = faceTypes.any(::landFace)

    /** The land faces' type lines and rules text. */
    val landFaces: List<Pair<String, String>>
        get() {
            val texts = oracleText.orEmpty().split(CardInfo.FACE_BREAK)
            return faceTypes.mapIndexedNotNull { i, t -> if (landFace(t)) t to texts.getOrElse(i) { "" } else null }
        }

    /** Land types of its land faces, e.g. Swamp and Forest for Overgrown Tomb. */
    val landTypes get() = landFaces.flatMap { (t, _) -> t.substringAfter(" — ", "").split(' ').filter { it.isNotEmpty() } }.toSet()

    val isBasic get() = landFaces.any { (t, _) -> "Basic" in t.substringBefore(" — ").split(' ') }
}

/** The mana curve: cards per mana value 0 to 7+ (lands left out), and their average. */
data class ManaCurve(val counts: List<Int>, val spells: Int, val average: Double)

/** A colour's share of a split bar: [colour] is one of WUBRGC. */
data class ColourCount(val colour: Char, val count: Int)

/** A card counted as a source of a colour; [finds] are the deck's lands a fetch land counts for this colour through. */
data class SourceLine(val name: String, val quantity: Int, val finds: List<String> = emptyList())

/** The sources of one colour. */
data class ColourSources(val colour: Char, val cards: List<SourceLine>) {
    val count get() = cards.sumOf { it.quantity }
}

/** A token (or helper card) the deck makes: one picture of it, and the deck's cards that make it. */
data class DeckToken(val id: String, val name: String, val label: String, val oracleId: String?, val makers: List<String>)

/**
 * The deck page's stats (since 1.44): the mana curve, the colours in the cards' costs, the mana the deck
 * makes and the tokens it makes.
 *
 * Mana production counts sources, the way mana bases are built: each card counts once for every colour it
 * can make, however much of it (Twilight Mire is one black and one green source, Sol Ring one colourless).
 * The colours are Scryfall's (`produced_mana`), and three things are added:
 * - fetch lands count for the colours of the lands they can find in this deck (Fabled Passage for the
 *   basics', Bloodstained Mire for every Swamp and Mountain, Overgrown Tomb included);
 * - "any colour" cards (Command Tower, Exotic Orchard, Arcane Signet) count only for the commander's colours;
 * - spells that search for lands (Farseek, Cultivate) don't count, only lands do that.
 */
object DeckStats {
    const val STATS = "stats"
    const val TOKENS = "tokens"

    private const val COLOURS = "WUBRGC"
    private val BASIC_TYPES = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
    private val symbol = Regex("\\{([^}]+)\\}")
    private val search = Regex("search your library for ([^.]*)", RegexOption.IGNORE_CASE)

    fun curve(cards: List<StatsCard>): ManaCurve {
        val spells = cards.filterNot { it.isLand }
        val counts = MutableList(8) { 0 }
        spells.forEach { c -> counts[c.cmc.toInt().coerceIn(0, 7)] += c.quantity }
        val n = spells.sumOf { it.quantity }
        return ManaCurve(counts, n, if (n == 0) 0.0 else spells.sumOf { it.cmc * it.quantity } / n)
    }

    /**
     * Coloured mana symbols in the cards' costs (every face that has one), copies counted. A hybrid symbol
     * ({B/G}) counts for both its colours, a Phyrexian one ({B/P}) for its colour; generic mana doesn't count.
     */
    fun costs(cards: List<StatsCard>): List<ColourCount> {
        val n = IntArray(COLOURS.length)
        for (c in cards) {
            for (m in symbol.findAll(c.manaCost.orEmpty())) {
                m.groupValues[1].uppercase().split('/').mapNotNull { p -> p.singleOrNull()?.takeIf { it in COLOURS } }.toSet()
                    .forEach { n[COLOURS.indexOf(it)] += c.quantity }
            }
        }
        return COLOURS.mapIndexedNotNull { i, ch -> if (n[i] > 0) ColourCount(ch, n[i]) else null }
    }

    /**
     * The sources of each colour (see [DeckStats]), in WUBRG then colourless order, colours without sources
     * left out. [identity] is the commander's colour identity (WUBRG letters); null for a deck without a
     * commander, where every colour counts. With [landsOnly] only lands count; without [colourless] the
     * colourless sources are left out.
     */
    fun sources(cards: List<StatsCard>, identity: String?, landsOnly: Boolean, colourless: Boolean = true): List<ColourSources> {
        val lands = cards.filter { it.isLand }
        val lines = COLOURS.associateWith { mutableListOf<SourceLine>() }
        for (c in cards) {
            if (!c.isLand && landsOnly) continue
            val own = c.producedMana.orEmpty().toSet()
            val found = if (c.isLand) fetches(c, lands) else emptyMap()
            for (colour in COLOURS) {
                if (colour != 'C' && identity != null && colour !in identity) continue
                val finds = found[colour].orEmpty()
                if (colour in own || finds.isNotEmpty()) lines.getValue(colour) += SourceLine(c.name, c.quantity, if (colour in own) emptyList() else finds)
            }
        }
        return lines.filter { (colour, l) -> l.isNotEmpty() && (colourless || colour != 'C') }
            .map { (colour, l) -> ColourSources(colour, l.groupBy { it.name }.map { (name, same) -> SourceLine(name, same.sumOf { it.quantity }, same.first().finds) }.sortedBy { it.name.lowercase() }) }
    }

    /**
     * What a fetch land finds in the deck, by colour: its land faces' "search your library for …" read as
     * which lands qualify (basic or not, which land types), then the colours those make.
     */
    fun fetches(card: StatsCard, lands: List<StatsCard>): Map<Char, List<String>> {
        val out = HashMap<Char, MutableSet<String>>()
        for ((_, text) in card.landFaces) {
            for (m in search.findAll(text)) {
                val what = m.groupValues[1]
                val types = BASIC_TYPES.filter { Regex("\\b$it\\b").containsMatchIn(what) }
                if (types.isEmpty() && !Regex("\\bland\\b", RegexOption.IGNORE_CASE).containsMatchIn(what)) continue
                val basic = Regex("\\bbasic\\b", RegexOption.IGNORE_CASE).containsMatchIn(what)
                for (l in lands) {
                    if (l.name == card.name) continue
                    if (basic && !l.isBasic) continue
                    if (types.isNotEmpty() && types.none { it in l.landTypes }) continue
                    l.producedMana.orEmpty().forEach { out.getOrPut(it) { sortedSetOf() } += l.name }
                }
            }
        }
        return out.mapValues { it.value.toList() }
    }

    /** The token parts the deck's cards link to: token id → the cards that make it. */
    fun tokenParts(cards: List<StatsCard>): Map<TokenPart, List<String>> {
        val out = LinkedHashMap<TokenPart, MutableSet<String>>()
        for (c in cards) TokenPart.decode(c.tokens).forEach { out.getOrPut(it) { sortedSetOf() } += c.name }
        return out.mapValues { it.value.toList() }
    }

    /**
     * The deck's tokens, one per token whatever printing each card links to (by Scryfall's oracle id from
     * [details], else by name and type), sorted by name. Each is labelled with its power and toughness when
     * it has them ("Insect 1/1").
     */
    fun tokens(parts: Map<TokenPart, List<String>>, details: Map<String, CardInfo>): List<DeckToken> =
        parts.entries.groupBy { (p, _) -> details[p.id]?.oracleId ?: "${p.name}|${p.typeLine}" }
            .map { (_, same) ->
                val first = same.first().key
                val info = details[first.id]
                DeckToken(
                    id = first.id,
                    name = first.name,
                    label = first.name + (info?.powerToughness?.let { " $it" } ?: ""),
                    oracleId = info?.oracleId,
                    makers = same.flatMap { it.value }.distinct().sorted(),
                )
            }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.label }))
}
