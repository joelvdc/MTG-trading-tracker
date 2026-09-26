package com.mtgtrader.scan

/** One OCR'd line with its bounding box in upright image coordinates. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    val height get() = bottom - top
}

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** What we could read off a card: candidate names (top of card) and set/number/language (bottom-left). */
data class ScanClues(
    val names: List<String>,
    val setCode: String?,
    val collectorNumber: String?,
    val language: String?,
) {
    val hasAnything get() = names.isNotEmpty() || setCode != null
}

/** The on-screen frame the card should be aligned to; shared by the overlay and the analyzer. */
object ScanGuide {
    const val CARD_ASPECT = 63f / 88f

    fun boxFor(width: Float, height: Float): Box {
        var gw = width * 0.82f
        var gh = gw / CARD_ASPECT
        if (gh > height * 0.94f) {
            gh = height * 0.94f
            gw = gh * CARD_ASPECT
        }
        val l = (width - gw) / 2
        val t = (height - gh) / 2
        return Box(l, t, l + gw, t + gh)
    }
}

object CardTextParser {
    private const val LANGS = "EN|ES|FR|DE|IT|PT|JA|JP|KO|RU|ZHS|ZHT|CS|CT|PH"

    // e.g. "MKM • EN", "M21*EN", "DMU - EN". A separator or space is required so words like
    // "BETWEEN" / "OFFEN" in rules text don't read as set code + language.
    private val setLang = Regex("""(?<![A-Z0-9])([A-Z0-9]{3,5})(?:\s*[•·*.\-+:,°º]\s*|\s+)($LANGS)(?![A-Z])""")

    // e.g. "123/280 R"
    private val slashNumber = Regex("""(?<!\d)(\d{1,4})\s*/\s*\d{1,4}(?!\d)""")

    // e.g. "0123" or "R 0123" (2023+ frames print the number without a total)
    private val bareNumber = Regex("""(?<![\d/])(\d{1,4}[a-z]?)(?![\d/])""")

    fun parse(lines: List<OcrLine>, guide: Box): ScanClues {
        // Slightly larger than the guide so a card held a bit off-centre still counts.
        val padX = guide.width * 0.08f
        val padY = guide.height * 0.06f
        val area = Box(guide.left - padX, guide.top - padY, guide.right + padX, guide.bottom + padY)
        val inside = lines.filter { area.contains(it.cx, it.cy) }

        val names = inside
            .filter { it.cy < guide.top + guide.height * 0.30f }
            .sortedBy { it.top }
            .mapNotNull { cleanName(it.text) }
            .distinct()
            .take(2)

        val bottom = inside
            .filter { it.cy > guide.top + guide.height * 0.82f && it.cx < guide.left + guide.width * 0.62f }
            .sortedBy { it.top }

        // The set line is the lowest text on the left, below any rules/flavour text: search bottom-up.
        var set: String? = null
        var lang: String? = null
        var setLine: OcrLine? = null
        for (l in bottom.asReversed()) {
            val m = setLang.find(l.text.uppercase()) ?: continue
            val code = m.groupValues[1]
            if (code.any { it.isLetter() }) {
                set = code
                lang = m.groupValues[2].let { if (it == "JP") "JA" else it }
                setLine = l
                break
            }
        }

        // The collector number sits on the line just above the set line (or on it, in some frames).
        // Without a set code it's useless for lookup, and older frames only have copyright years there.
        var number: String? = null
        val sl = setLine
        if (sl != null) {
            val candidates = bottom.filter { it.top <= sl.top && it.bottom >= sl.top - sl.height * 3 }
                .sortedByDescending { it.top }
                .map { normalizeDigits(it.text) }
            number = candidates.firstNotNullOfOrNull { t -> slashNumber.find(t)?.groupValues?.get(1) }
                ?: candidates.firstNotNullOfOrNull { t ->
                    if ('©' in t || "WIZARDS" in t.uppercase()) null
                    else bareNumber.findAll(setLang.replace(t.uppercase(), " ").lowercase()).map { it.groupValues[1] }
                        .firstOrNull { !(it.length == 4 && (it.startsWith("19") || it.startsWith("20"))) }
                }
            number = number?.trimStart('0')?.ifEmpty { "0" }
        }
        return ScanClues(names, set, number, lang)
    }

    /** OCR often reads the leading zeros of "0150" as the letter O; fix tokens that are clearly numbers. */
    private fun normalizeDigits(text: String): String = text.split(' ').joinToString(" ") { tok ->
        if (tok.any { it.isDigit() } && tok.all { it.isDigit() || it in "oOIl/" })
            tok.map { c -> when (c) { 'o', 'O' -> '0'; 'I', 'l' -> '1'; else -> c } }.joinToString("")
        else tok
    }

    /** Strips mana-cost garbage and punctuation; returns null if too little is left to be a name. */
    fun cleanName(raw: String): String? {
        // Tokens containing digits or braces are mana costs ("2UU", "{3}{R}"), never part of a name.
        val tokens = raw.split(' ').filter { t -> t.isNotBlank() && t.none { it.isDigit() || it == '{' || it == '}' } }
        val kept = tokens.joinToString(" ").map { c -> if (c.isLetter() || c == ' ' || c == ',' || c == '\'' || c == '-') c else ' ' }.joinToString("")
        var words = kept.split(' ').filter { it.isNotBlank() }
        // Mana symbols are often read as stray letters ("U", "WW") after the name.
        fun isManaNoise(w: String) = w.length == 1 || w.all { it in "WUBRGCX" }
        while (words.size > 1 && isManaNoise(words.last())) words = words.dropLast(1)
        while (words.size > 1 && words.first().length == 1) words = words.drop(1)
        val name = words.joinToString(" ").trim(',', '-', '\'', ' ')
        return name.takeIf { n -> n.count { it.isLetter() } >= 3 }
    }
}
