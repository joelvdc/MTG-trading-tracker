package com.mtgtrader.data

import java.util.Locale

/**
 * Checks a scanned package against its order: which of the ordered cards were scanned, which came in
 * another language or printing, which are missing, and what was scanned that nobody ordered. The
 * camera can't tell foil, condition, signed or altered; lines where the order says one of those are
 * marked to check by hand. Since 1.32.
 */
object PackageCheck {
    enum class Status(val label: String, val problem: Boolean) {
        RECEIVED("Received", false),

        /** The owner said the card is there although the scans don't show it (the scan was wrong, or it wasn't scanned). */
        BY_HAND("Marked as received", false),

        /** Same card, but the scanner (or the order's lookup) only knew the name, so the printing can't be compared. */
        PRINTING_UNSURE("Received, printing not sure", false),
        WRONG_LANGUAGE("Wrong language", true),
        WRONG_PRINTING("Wrong printing", true),
        MISSING("Missing", true),
    }

    /** [quantity] copies of an order line, and the scan they were matched to (none for missing copies). */
    data class Part(val status: Status, val quantity: Int, val scan: ScannedCard?)

    data class Line(val cand: OrderCandidate, val parts: List<Part>) {
        val line get() = cand.line

        /** The worst thing about the line, for sorting and its colour. */
        val status: Status get() = parts.maxByOrNull { it.status.ordinal }?.status ?: Status.RECEIVED
        val problem get() = status.problem
        fun count(status: Status) = parts.filter { it.status == status }.sumOf { it.quantity }

        /** What the camera can't see and the order says: worth a look at the card. */
        val byHand: List<String>
            get() = listOfNotNull(
                "foil".takeIf { line.foil && line.versionKnown },
                line.condition.takeIf { it != "NM" && line.versionKnown },
                "signed".takeIf { line.signed },
                "altered".takeIf { line.altered },
            )
    }

    /** [extras]: scanned copies that match no order line, with how many. */
    data class Result(val lines: List<Line>, val extras: List<Pair<ScannedCard, Int>>) {
        fun count(status: Status) = lines.sumOf { it.count(status) }
        val ordered get() = lines.sumOf { it.line.quantity }
        val problems get() = lines.filter { it.problem }
        val extraCount get() = extras.sumOf { it.second }
    }

    private fun sameCard(cand: OrderCandidate, scan: ScannedCard): Boolean {
        val names = OrderFiles.names(scan.card.name)
        return (OrderFiles.names(cand.line.name) + cand.card?.let { OrderFiles.names(it.name) }.orEmpty()).any { it in names }
    }

    /**
     * Compares the order with the scanned cards. [accepted] holds the lines the owner marked as received
     * by hand. Exact matches are handed out first over the whole order, then same printing in another
     * language, then same card in another printing, so a wrong card can't take a right one's scan.
     */
    fun check(candidates: List<OrderCandidate>, scans: List<ScannedCard>, accepted: Set<Int> = emptySet()): Result {
        val left = scans.associate { it.id to it.quantity }.toMutableMap()
        val need = candidates.associate { it.index to it.line.quantity }.toMutableMap()
        val parts = candidates.associate { it.index to mutableListOf<Part>() }

        fun pass(status: (OrderCandidate, ScannedCard) -> Status?) {
            for (cand in candidates) for (scan in scans) {
                val want = need.getValue(cand.index)
                val have = left.getValue(scan.id)
                if (want <= 0 || have <= 0) continue
                val s = status(cand, scan) ?: continue
                val n = minOf(want, have)
                parts.getValue(cand.index) += Part(s, n, scan)
                need[cand.index] = want - n
                left[scan.id] = have - n
            }
        }
        // The monthly Cardmarket file and unknown language numbers don't say the language: nothing to compare.
        fun languageSaid(c: OrderCandidate) = c.line.versionKnown && c.line.languageKnown
        fun samePrinting(c: OrderCandidate, s: ScannedCard) = c.card != null && !c.guessed && s.exactPrinting && c.card.scryfallId == s.card.scryfallId
        pass { c, s -> Status.RECEIVED.takeIf { samePrinting(c, s) && (!languageSaid(c) || c.line.language == s.language) } }
        pass { c, s -> Status.WRONG_LANGUAGE.takeIf { samePrinting(c, s) } }
        pass { c, s ->
            when {
                !sameCard(c, s) -> null
                c.card == null || c.guessed || !s.exactPrinting -> Status.PRINTING_UNSURE
                else -> Status.WRONG_PRINTING
            }
        }
        val lines = candidates.map { cand ->
            val found = parts.getValue(cand.index)
            val missing = need.getValue(cand.index)
            val all = if (missing > 0) found + Part(Status.MISSING, missing, null) else found
            Line(cand, if (cand.index in accepted) all.map { it.copy(status = if (it.status == Status.RECEIVED) Status.RECEIVED else Status.BY_HAND) } else all)
        }
        return Result(lines, scans.mapNotNull { s -> left.getValue(s.id).takeIf { it > 0 }?.let { s to it } })
    }

    /** Copies to add to the collection: the card that is physically there, with the order's details. */
    data class Addition(val card: CardRef, val finish: Finish, val condition: String, val language: String, val quantity: Int, val price: Double?, val marks: Marks)

    /**
     * What goes into the collection after a check: every scanned copy, with what the order knows and the
     * camera can't (price paid, condition, finish, signed, altered) for copies that matched a line. A copy
     * in the wrong language or printing is added as scanned, since that is the card in hand; a line marked
     * as received by hand is added as ordered. [extras] adds the scanned cards that weren't in the order too.
     */
    fun additions(result: Result, extras: Boolean): List<Addition> {
        val out = ArrayList<Addition>()
        for (l in result.lines) for (p in l.parts) {
            val line = l.line
            val scan = p.scan
            val ordered = l.cand.card?.takeIf { !l.cand.guessed }
            val asOrdered = p.status == Status.RECEIVED || p.status == Status.BY_HAND || p.status == Status.PRINTING_UNSURE
            val card = (if (asOrdered) ordered ?: scan?.card ?: l.cand.card else scan?.card) ?: continue
            if (p.status == Status.MISSING) continue
            val known = line.versionKnown
            out += Addition(
                card = card,
                finish = card.resolveFinish(if (known) (if (line.foil) Finish.FOIL else Finish.NONFOIL) else scan?.finish ?: Finish.NONFOIL),
                condition = if (known) line.condition else scan?.condition ?: "NM",
                language = if (asOrdered && known && line.languageKnown) line.language else scan?.language ?: line.language,
                quantity = p.quantity,
                price = line.price,
                marks = Marks(line.signed, line.altered),
            )
        }
        if (extras) for ((scan, n) in result.extras) {
            out += Addition(scan.card, scan.finish, scan.condition, scan.language, n, null, Marks.NONE)
        }
        return out
    }

    private fun eur(v: Double?) = v?.let { String.format(Locale.US, "%.2f EUR", it) } ?: "?"

    private fun describe(l: Line): String {
        val line = l.line
        val where = l.cand.card?.takeIf { !l.cand.guessed }?.let { "${it.setName} (${it.setCode.uppercase()}) #${it.collectorNumber}" }
            ?: listOf(line.setName, line.setCode.takeIf { it.isNotEmpty() }?.let { "(${it.uppercase()})" }, line.number.takeIf { it.isNotEmpty() }?.let { "#$it" })
                .filter { !it.isNullOrEmpty() }.joinToString(" ")
        val version = listOfNotNull(
            line.language.takeIf { line.versionKnown && line.languageKnown },
            line.condition.takeIf { line.versionKnown },
            "foil".takeIf { line.foil },
            "signed".takeIf { line.signed },
            "altered".takeIf { line.altered },
        ).joinToString(", ")
        return listOf(line.label, where, version).filter { it.isNotEmpty() }.joinToString(", ")
    }

    /** A plain-text list of what's wrong, to send to the shop. Prices in EUR, as the shops bill. */
    fun report(result: Result, orderName: String): String = buildString {
        appendLine("Problems with $orderName")
        fun section(title: String, status: Status, detail: (Line, Part) -> String = { _, _ -> "" }) {
            val rows = result.lines.flatMap { l -> l.parts.filter { it.status == status }.map { l to it } }
            if (rows.isEmpty()) return
            appendLine()
            appendLine("$title (${rows.sumOf { it.second.quantity }}):")
            for ((l, p) in rows) appendLine("- ${p.quantity}x ${describe(l)}, paid ${eur(l.line.price)} each${detail(l, p)}")
        }
        section("Missing", Status.MISSING)
        section("Wrong language", Status.WRONG_LANGUAGE) { _, p -> "; received in ${p.scan?.language}" }
        section("Wrong printing", Status.WRONG_PRINTING) { _, p ->
            p.scan?.card?.let { "; received ${it.setName} (${it.setCode.uppercase()}) #${it.collectorNumber}" }.orEmpty()
        }
        if (result.problems.isEmpty()) {
            appendLine()
            appendLine("Nothing: every ordered card was received.")
        }
    }.trim()
}
