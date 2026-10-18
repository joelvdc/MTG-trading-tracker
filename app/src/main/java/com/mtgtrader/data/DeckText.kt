package com.mtgtrader.data

/**
 * Reads a decklist pasted as text (since 1.46): Moxfield's export ("1 Sol Ring (CMR) 263 *F* #Ramp"), Archidekt's
 * ("1x Sol Ring (cmr) 263 [Ramp]"), MTG Arena's (with "Commander" and "Deck" headings) and plain lists
 * ("1 Sol Ring"). Headings such as "Commander", "Sideboard" or "Maybeboard" say where the following cards go.
 */
object DeckText {
    enum class Board { MAIN, COMMANDER, MAYBE }

    data class Line(
        val quantity: Int,
        val name: String,
        val setCode: String? = null,
        val number: String? = null,
        val foil: Boolean = false,
        val etched: Boolean = false,
        /** Moxfield's tags or Archidekt's categories, in their order. */
        val tags: List<String> = emptyList(),
        val board: Board = Board.MAIN,
    )

    data class Result(val lines: List<Line>, val unreadable: List<String>) {
        val hasTags get() = lines.any { it.tags.isNotEmpty() }
        val cardCount get() = lines.filter { it.board != Board.MAYBE }.sumOf { it.quantity }
    }

    private val heading = Regex(
        "^(?://\\s*)?(commanders?|deck|main|mainboard|main deck|sideboard|maybeboard|considering|companions?|tokens?|about)\\s*:?\\s*(?:\\(\\d+\\))?$",
        RegexOption.IGNORE_CASE,
    )
    private val quantity = Regex("^(\\d+)\\s*[xX]?\\s+(.+)$")
    private val hashTag = Regex("(?:^|\\s)#!?([^\\s#]+)")
    private val bracket = Regex("\\[([^\\]]*)]")
    private val finishMark = Regex("\\s\\*([A-Za-z]{1,3})\\*")
    private val setNumber = Regex("\\s\\(([A-Za-z0-9]{2,8})\\)(?:\\s+(\\S+))?\\s*$")

    fun parse(text: String): Result {
        val lines = mutableListOf<Line>()
        val unreadable = mutableListOf<String>()
        var board = Board.MAIN
        var sawHeading = false
        val blocks = mutableListOf<MutableList<Int>>(mutableListOf())
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) {
                if (blocks.last().isNotEmpty()) blocks.add(mutableListOf())
                continue
            }
            if (line.startsWith("Name ", ignoreCase = true) && lines.isEmpty() && !sawHeading) continue
            val m = heading.matchEntire(line)
            if (m != null) {
                sawHeading = true
                board = when (m.groupValues[1].lowercase()) {
                    "commander", "commanders" -> Board.COMMANDER
                    "sideboard", "maybeboard", "considering", "tokens", "token", "about" -> Board.MAYBE
                    else -> Board.MAIN
                }
                continue
            }
            if (line.startsWith("//") || line.startsWith("#")) continue
            val parsed = readLine(line, board)
            if (parsed == null) unreadable += line
            else {
                blocks.last() += lines.size
                lines += parsed
            }
        }
        // Moxfield's plain text has no headings: a first block of one or two cards set apart by a blank line is the commander.
        if (!sawHeading && lines.none { it.board == Board.COMMANDER }) {
            val first = blocks.firstOrNull { it.isNotEmpty() }
            if (first != null && first.size <= 2 && blocks.count { it.isNotEmpty() } > 1 && first.all { lines[it].quantity == 1 }) {
                first.forEach { lines[it] = lines[it].copy(board = Board.COMMANDER) }
            }
        }
        return Result(lines, unreadable)
    }

    private fun readLine(line: String, board: Board): Line? {
        val m = quantity.matchEntire(line) ?: return null
        val qty = m.groupValues[1].toIntOrNull()?.takeIf { it in 1..999 } ?: return null
        var rest = " " + m.groupValues[2]
        val tags = mutableListOf<String>()
        var lineBoard = board
        bracket.findAll(rest).forEach { b ->
            b.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { c ->
                val name = c.substringBefore('{').trim()
                if ("{noDeck}" in c && lineBoard == Board.MAIN) lineBoard = Board.MAYBE
                if (name.equals(DeckEdits.COMMANDER, true) && lineBoard == Board.MAIN) lineBoard = Board.COMMANDER
                if (name.isNotEmpty()) tags += name
            }
        }
        rest = bracket.replace(rest, " ")
        hashTag.findAll(rest).forEach { tags += it.groupValues[1] }
        rest = hashTag.replace(rest, " ")
        var foil = false
        var etched = false
        finishMark.findAll(rest).forEach { f ->
            when (f.groupValues[1].uppercase()) {
                "E" -> etched = true
                else -> foil = true
            }
        }
        rest = finishMark.replace(rest, " ").trimEnd()
        var set: String? = null
        var number: String? = null
        setNumber.find(rest)?.let { s ->
            set = s.groupValues[1].lowercase()
            number = s.groupValues[2].takeIf { it.isNotEmpty() }
            rest = rest.substring(0, s.range.first)
        }
        val name = rest.trim()
        if (name.isEmpty()) return null
        return Line(qty, name, set, number, foil || etched, etched, tags.distinct(), lineBoard)
    }
}
