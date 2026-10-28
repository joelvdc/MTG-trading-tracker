package com.mtgtrader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import com.mtgtrader.data.Deck
import com.mtgtrader.data.PowerSource
import com.mtgtrader.data.RuleZeroCard
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltEffect
import com.mtgtrader.data.WinOdds
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One card to print: the deck, Commander Salt's data, its win turn and (unless saving ink) its cover art. Since 1.65. */
data class PrintCard(
    val kind: RuleZeroCard,
    val deck: Deck,
    val data: SaltCard?,
    val win: WinOdds?,
    val source: PowerSource,
    val art: Bitmap? = null,
)

/**
 * The bracket and power level cards as A4 sheets to print and cut (since 1.65): cards the size of a Magic card
 * (63 × 88 mm), 3 × 3 to a page, touching, with cut marks in the margins. Drawn straight onto a canvas, so the
 * PDF holds sharp text and shapes; the same drawing makes the screenshot tests' pictures.
 */
object CardSheets {
    const val PAGE_W = 210f
    const val PAGE_H = 297f
    const val CARD_W = 63f
    const val CARD_H = 88f
    const val COLS = 3
    const val ROWS = 3
    const val PER_PAGE = COLS * ROWS
    const val LEFT = (PAGE_W - COLS * CARD_W) / 2
    const val TOP = (PAGE_H - ROWS * CARD_H) / 2

    /** A4 in PDF points (1/72 inch). */
    const val PDF_W = 595
    const val PDF_H = 842
    const val POINTS_PER_MM = 72f / 25.4f

    /** The top left corner of the card in [slot] (0–8, row by row), in mm from the page's top left. */
    fun origin(slot: Int): Pair<Float, Float> = (LEFT + slot % COLS * CARD_W) to (TOP + slot / COLS * CARD_H)

    /** Bracket cards first, then power level cards, each kind starting on a new page. */
    fun <T> pages(bracket: List<T>, power: List<T>): List<List<T>> = bracket.chunked(PER_PAGE) + power.chunked(PER_PAGE)

    /** "Bracket cards · page 1 of 2" at the foot of each page. */
    fun footer(cards: List<PrintCard>, page: Int, pageCount: Int): String {
        val kind = if (cards.firstOrNull()?.kind == RuleZeroCard.POWER) "Power level cards" else "Bracket cards"
        return "MTG Trader · $kind · page ${page + 1} of $pageCount · print at 100% (actual size)"
    }

    fun writePdf(file: File, pages: List<List<PrintCard>>, inkSaver: Boolean) {
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { i, cards ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(PDF_W, PDF_H, i + 1).create())
                drawPage(page.canvas, POINTS_PER_MM, cards, footer(cards, i, pages.size), inkSaver)
                pdf.finishPage(page)
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    /** Draws one A4 page on [canvas], at [perMm] canvas units to the millimetre. */
    fun drawPage(canvas: Canvas, perMm: Float, cards: List<PrintCard>, footer: String, inkSaver: Boolean) {
        canvas.save()
        canvas.scale(perMm / U, perMm / U)
        val p = Painter(canvas)
        p.fill(0f, 0f, PAGE_W, PAGE_H, WHITE)
        cutMarks(p, cards.size)
        cards.take(PER_PAGE).forEachIndexed { slot, card ->
            val (x, y) = origin(slot)
            canvas.save()
            canvas.translate(x * U, y * U)
            canvas.clipRect(0f, 0f, CARD_W * U, CARD_H * U)
            when (card.kind) {
                RuleZeroCard.BRACKET -> BracketPrint(p, card, inkSaver).draw()
                RuleZeroCard.POWER -> PowerPrint(p, card, inkSaver).draw()
            }
            canvas.restore()
            p.frame(x, y, CARD_W, CARD_H, LINE, 0.15f)
        }
        p.text(footer, PAGE_W / 2, PAGE_H - 5f, 6.5f, GREY, align = Paint.Align.CENTER)
        canvas.restore()
    }

    /** Short lines in the margins on the lines between the cards (only where there are cards). */
    private fun cutMarks(p: Painter, count: Int) {
        if (count == 0) return
        val rows = ceil(count.coerceAtMost(PER_PAGE) / COLS.toFloat()).toInt()
        val cols = count.coerceAtMost(COLS)
        val xs = (0..cols).map { LEFT + it * CARD_W }
        val ys = (0..rows).map { TOP + it * CARD_H }
        // Each mark 6.5 mm long, 1.5 mm off the cards, so they stay clear of the footer.
        val gap = 1.5f
        val len = 6.5f
        xs.forEach { x ->
            p.line(x, ys.first() - gap - len, x, ys.first() - gap, MARK, 0.15f)
            p.line(x, ys.last() + gap, x, ys.last() + gap + len, MARK, 0.15f)
        }
        ys.forEach { y ->
            p.line(xs.first() - gap - len, y, xs.first() - gap, y, MARK, 0.15f)
            p.line(xs.last() + gap, y, xs.last() + gap + len, y, MARK, 0.15f)
        }
    }

    // ---- Drawing helpers, in mm (the canvas works in tenths of a mm, so text lays out finely) -------------

    /** Canvas units per mm inside [drawPage]. */
    private const val U = 10f

    internal const val WHITE = 0xFFFFFFFF.toInt()
    internal const val INK = 0xFF1C1B1F.toInt()
    internal const val GREY = 0xFF5F5C66.toInt()
    internal const val LINE = 0xFFD6D3DC.toInt()
    internal const val MARK = 0xFF888888.toInt()
    internal const val BAR = 0xFF7F77DD.toInt()
    internal const val TRACK = 0xFFE6E3EC.toInt()
    internal const val GOLD = 0xFF9A6A00.toInt()
    internal const val GREEN = 0xFF1E7A52.toInt()
    internal const val AMBER = 0xFFBA7517.toInt()
    internal const val PILL = 0xFFEEEDFE.toInt()
    internal const val PILL_TEXT = 0xFF3C3489.toInt()

    /** Text size in mm for a size in typographic points. */
    private fun ptToMm(pt: Float) = pt * 25.4f / 72f

    // Loaded when first drawn, so the layout numbers above work without Android (plain unit tests).
    private val REGULAR by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
    private val MEDIUM by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val BOLD by lazy { Typeface.create("sans-serif", Typeface.BOLD) }

    enum class Weight { REGULAR, MEDIUM, BOLD }

    class Painter(val canvas: Canvas) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

        fun fill(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 0f) {
            paint.style = Paint.Style.FILL
            paint.color = color
            if (radius > 0f) canvas.drawRoundRect(RectF(x * U, y * U, (x + w) * U, (y + h) * U), radius * U, radius * U, paint)
            else canvas.drawRect(x * U, y * U, (x + w) * U, (y + h) * U, paint)
        }

        fun frame(x: Float, y: Float, w: Float, h: Float, color: Int, stroke: Float, radius: Float = 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke * U
            paint.color = color
            val r = RectF(x * U, y * U, (x + w) * U, (y + h) * U)
            if (radius > 0f) canvas.drawRoundRect(r, radius * U, radius * U, paint) else canvas.drawRect(r, paint)
        }

        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, stroke: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke * U
            paint.color = color
            canvas.drawLine(x1 * U, y1 * U, x2 * U, y2 * U, paint)
        }

        fun circle(cx: Float, cy: Float, r: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(cx * U, cy * U, r * U, paint)
        }

        fun image(bitmap: Bitmap, x: Float, y: Float, w: Float, h: Float) {
            // Cropped to fill the box, keeping the middle of the picture.
            val want = w / h
            val have = bitmap.width.toFloat() / bitmap.height
            val src = if (have > want) {
                val cw = (bitmap.height * want).roundToInt()
                Rect((bitmap.width - cw) / 2, 0, (bitmap.width + cw) / 2, bitmap.height)
            } else {
                val ch = (bitmap.width / want).roundToInt()
                Rect(0, (bitmap.height - ch) / 2, bitmap.width, (bitmap.height + ch) / 2)
            }
            canvas.drawBitmap(bitmap, src, RectF(x * U, y * U, (x + w) * U, (y + h) * U), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        private fun paintFor(sizePt: Float, color: Int, weight: Weight): TextPaint = textPaint.apply {
            textSize = ptToMm(sizePt) * U
            this.color = color
            typeface = when (weight) {
                Weight.REGULAR -> REGULAR
                Weight.MEDIUM -> MEDIUM
                Weight.BOLD -> BOLD
            }
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }

        /** One line of text on [baseline], shortened with "…" to [maxWidth] mm. Returns its width in mm. */
        fun text(
            s: String, x: Float, baseline: Float, sizePt: Float, color: Int,
            weight: Weight = Weight.REGULAR, align: Paint.Align = Paint.Align.LEFT, maxWidth: Float? = null, spacing: Float = 0f,
        ): Float {
            val tp = paintFor(sizePt, color, weight)
            tp.letterSpacing = spacing
            val shown = maxWidth?.let { TextUtils.ellipsize(s, tp, it * U, TextUtils.TruncateAt.END).toString() } ?: s
            tp.textAlign = align
            canvas.drawText(shown, x * U, baseline * U, tp)
            return tp.measureText(shown) / U
        }

        fun width(s: String, sizePt: Float, weight: Weight = Weight.REGULAR): Float = paintFor(sizePt, INK, weight).measureText(s) / U

        /** Text wrapped to [width] mm in at most [maxLines] lines, from [top]; returns the height used in mm. */
        fun wrapped(s: String, x: Float, top: Float, width: Float, sizePt: Float, color: Int, maxLines: Int, lineHeightPt: Float): Float {
            val tp = paintFor(sizePt, color, Weight.REGULAR)
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, tp, (width * U).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
            val lines = layout.lineCount
            val lh = ptToMm(lineHeightPt)
            for (i in 0 until lines) {
                val start = layout.getLineStart(i)
                var end = layout.getLineEnd(i)
                var line = s.substring(start, end).trimEnd()
                if (i == lines - 1 && layout.getEllipsisCount(i) > 0) {
                    end = start + layout.getEllipsisStart(i)
                    line = s.substring(start, end).trimEnd() + "…"
                }
                text(line, x, top + lh * (i + 1) - lh * 0.25f, sizePt, color)
            }
            return lh * lines
        }

        fun lineHeight(pt: Float) = ptToMm(pt)
    }

    // ---- The cards ---------------------------------------------------------------------------------

    private const val PAD = 3f
    private const val INNER = CARD_W - 2 * PAD
    private const val BODY_PT = 6.2f
    private const val BODY_LH = 7.1f
    private const val LABEL_PT = 5.2f
    private const val CREDIT_LH = 2.1f

    private val PIP_COLOURS = mapOf(
        'W' to 0xFFF8F1D6.toInt(), 'U' to 0xFF3F8FD2.toInt(), 'B' to 0xFF3B3434.toInt(),
        'R' to 0xFFD9503F.toInt(), 'G' to 0xFF3E9A5B.toInt(),
    )

    private abstract class CardPrint(val p: Painter, val card: PrintCard, val ink: Boolean) {
        val deck get() = card.deck
        /** Where the next thing goes, in mm from the card's top. */
        var y = 0f

        abstract val title: String
        abstract fun body(data: SaltCard)
        /** Where the card's data comes from, a line each. */
        abstract val credit: List<String>

        fun draw() {
            p.fill(0f, 0f, CARD_W, CARD_H, WHITE)
            header()
            val data = card.data
            if (data == null) {
                p.text("Commander Salt's details aren't loaded yet.", PAD, y + 3f, BODY_PT, GREY, maxWidth = INNER)
            } else {
                body(data)
            }
            footer()
        }

        private fun header() {
            val nameColour: Int
            val subColour: Int
            val kindColour: Int
            val bottom: Float
            if (ink) {
                p.fill(0f, 0f, CARD_W, 1.2f, BAR)
                kindColour = 0xFF534AB7.toInt(); nameColour = INK; subColour = GREY
                bottom = 11.6f
            } else {
                val h = 16f
                p.fill(0f, 0f, CARD_W, h, 0xFF2E2766.toInt())
                card.art?.let { p.image(it, 0f, 0f, CARD_W, h) }
                p.fill(0f, 0f, CARD_W, h, 0xCC26215C.toInt())
                kindColour = 0xFFAFA9EC.toInt(); nameColour = WHITE; subColour = 0xFFCECBF6.toInt()
                bottom = h - 1.6f
            }
            val commanderBase = bottom - 0.5f
            val nameBase = commanderBase - 3.1f
            val kindBase = nameBase - 3.9f
            p.text(title.uppercase(), PAD, kindBase, 5.5f, kindColour, Weight.MEDIUM, spacing = 0.05f)
            p.text(deck.name, PAD, nameBase, 10f, nameColour, Weight.MEDIUM, maxWidth = INNER)
            val pips = deck.colorIdentity.mapNotNull { PIP_COLOURS[it] }.ifEmpty { listOf(0xFFB9B4B0.toInt()) }
            val r = 1.05f
            val pipsWidth = pips.size * 2 * r + (pips.size - 1) * 0.5f
            val w = p.text(deck.commanders, PAD, commanderBase, 6.5f, subColour, maxWidth = INNER - pipsWidth - 1.5f)
            var cx = PAD + w + 1.5f + r
            pips.forEach { colour ->
                p.circle(cx, commanderBase - 0.75f, r, colour)
                cx += 2 * r + 0.5f
            }
            y = if (ink) {
                p.line(PAD, 13f, CARD_W - PAD, 13f, LINE, 0.2f)
                14.2f
            } else {
                17.5f
            }
        }

        private fun footer() {
            val base = CARD_H - 1.8f
            // The credit's lines go up from the bottom one, next to the owner and date.
            credit.asReversed().forEachIndexed { i, line ->
                p.text(line, PAD, base - i * CREDIT_LH, 4.8f, GREY, maxWidth = INNER * 0.6f)
            }
            val by = listOfNotNull(deck.owner.takeIf { it.isNotBlank() }, deck.scoredAt?.let(Fmt::date)).joinToString(" · ")
            p.text(by, CARD_W - PAD, base, 4.8f, GREY, align = Paint.Align.RIGHT)
        }

        /** The room left above the footer, in mm. */
        val room get() = CARD_H - 4.5f - (credit.size - 1) * CREDIT_LH - y

        fun rule() {
            y += 0.9f
            p.line(PAD, y, CARD_W - PAD, y, LINE, 0.15f)
            y += 0.9f
        }

        fun label(s: String) {
            y += p.lineHeight(6.4f)
            p.text(s.uppercase(), PAD, y - 0.45f, LABEL_PT, GREY, Weight.MEDIUM, spacing = 0.04f)
        }

        fun bodyLine(s: String, x: Float = PAD, width: Float = INNER, colour: Int = INK, weight: Weight = Weight.REGULAR) {
            p.text(s, x, y + p.lineHeight(BODY_LH) - 0.55f, BODY_PT, colour, weight, maxWidth = width)
        }

        fun newLine() { y += p.lineHeight(BODY_LH) }

        fun wrapped(s: String, maxLines: Int) {
            y += p.wrapped(s, PAD, y, INNER, BODY_PT, INK, maxLines, BODY_LH)
        }
    }

    private class BracketPrint(p: Painter, card: PrintCard, ink: Boolean) : CardPrint(p, card, ink) {
        override val title = "Bracket"
        override val credit = listOf("Bracket data: Commander Salt")

        override fun body(data: SaltCard) {
            tiles()
            card.win?.let { rule(); speed(it) }
            rule()
            criteria(data)
            if (data.gameChangers.isNotEmpty()) {
                rule()
                label("Game changers")
                wrapped(data.gameChangers.joinToString(", "), 2)
            }
            val combos = (data.twoCardCombos + data.earlyCombos).distinct()
            if (combos.isNotEmpty() && room > 6f) {
                rule()
                label("Rule zero: combos")
                val lh = p.lineHeight(BODY_LH)
                val fit = ((room - 0.5f) / lh).toInt().coerceAtLeast(1)
                val shown = if (combos.size <= fit) combos else combos.take(fit - 1)
                shown.forEach { bodyLine(it.joinToString(" + ")); newLine() }
                if (shown.size < combos.size) { bodyLine("+${combos.size - shown.size} more", colour = GREY); newLine() }
            }
        }

        private fun tiles() {
            val gap = 1.5f
            val w = (INNER - 2 * gap) / 3
            // No line under the number since 1.67: shorter tiles (since 1.68), the label and number centred.
            val h = 9.8f
            fun tile(i: Int, bg: Int, fg: Int, label: String, big: String, bigColour: Int = fg) {
                val x = PAD + i * (w + gap)
                p.fill(x, y, w, h, bg, 1.5f)
                val cx = x + w / 2
                p.text(label, cx, y + 2.9f, 5f, fg, align = Paint.Align.CENTER)
                p.text(big, cx, y + 8.15f, 13f, bigColour, Weight.BOLD, align = Paint.Align.CENTER)
            }
            listOf("Baseline" to deck.bracketBaseline, "Realistic" to deck.bracketRealistic).forEachIndexed { i, (label, b) ->
                val (bg, fg) = bracketColors(b)
                tile(i, bg.toArgb(), fg.toArgb(), label, b?.toString() ?: "—")
            }
            // The turn 4 in 10 games are won by, in gold like its bar below (without saying so since 1.67).
            tile(2, 0xFFF1EFF6.toInt(), GREY, "Wins by", card.win?.label ?: "—", GOLD)
            y += h
        }

        /** The app's speed bars: % of games won by each turn, the first win (green) and 4 in 10 games (gold) outlined. */
        private fun speed(win: WinOdds) {
            label("Speed · % of games won by each turn")
            y += 0.5f
            val turns = win.window.toList()
            val gap = 0.5f
            val w = (INNER - gap * (turns.size - 1)) / turns.size
            val h = 3.6f
            turns.forEachIndexed { i, t ->
                val v = win.byTurn[t - 1]
                val mark = when (t) {
                    win.fourInTen -> GOLD
                    win.firstWin -> GREEN
                    else -> null
                }
                val x = PAD + i * (w + gap)
                val alpha = if (ink) (v / 100f * 0.35f).coerceIn(0.05f, 0.35f) else (v / 100f).coerceIn(0.1f, 1f)
                p.fill(x, y, w, h, withAlpha(BAR, alpha), 0.6f)
                mark?.let { p.frame(x + 0.15f, y + 0.15f, w - 0.3f, h - 0.3f, it, 0.3f, 0.5f) }
                p.text("$v", x + w / 2, y + h / 2 + 0.65f, 5.2f, if (!ink && v > 55) WHITE else INK, align = Paint.Align.CENTER)
                p.text("T$t", x + w / 2, y + h + 1.9f, 4.8f, mark ?: GREY, if (mark != null) Weight.BOLD else Weight.REGULAR, align = Paint.Align.CENTER)
            }
            y += h + 2.4f
        }

        private fun criteria(data: SaltCard) {
            label("Bracket criteria")
            val rows = listOf(
                "Game changers" to data.gameChangers.size,
                "Two-card combos" to data.twoCardCombos.size,
                "Early-game combos" to data.earlyCombos.size,
                "Extra turns" to data.extraTurns.size,
                "Land denial" to data.massLandDenial.size,
            ).sortedByDescending { it.second > 0 }
            val colW = (INNER - 3f) / 2
            rows.chunked(2).forEach { pair ->
                pair.forEachIndexed { i, (label, n) ->
                    val x = PAD + i * (colW + 3f)
                    val hit = n > 0
                    bodyLine((if (hit) "▲ " else "✓ ") + label, x, colW - 3f, if (hit) INK else GREY)
                    p.text("$n", x + colW, y + p.lineHeight(BODY_LH) - 0.55f, BODY_PT, if (hit) AMBER else GREY,
                        if (hit) Weight.MEDIUM else Weight.REGULAR, align = Paint.Align.RIGHT)
                }
                newLine()
            }
        }
    }

    private class PowerPrint(p: Painter, card: PrintCard, ink: Boolean) : CardPrint(p, card, ink) {
        override val title = "Power level"
        override val credit = powerCredit(card.source)

        override fun body(data: SaltCard) {
            val power = deck.power(card.source)
            val base = y + 6.2f
            val w = p.text(power?.let { Scores.power(it, card.source) } ?: "—", PAD, base, 22f, INK, Weight.MEDIUM)
            p.text(" / 10", PAD + w, base, 7f, GREY)
            data.playStyle?.let { style ->
                val tw = p.width(style, 6f)
                val pw = tw + 3f
                p.fill(CARD_W - PAD - pw, base - 4.2f, pw, 3.6f, PILL, 1.2f)
                p.text(style, CARD_W - PAD - pw / 2, base - 1.6f, 6f, PILL_TEXT, align = Paint.Align.CENTER)
            }
            y = base + 1.4f
            meter(power ?: 0.0)
            rule()
            saltAndMana(data)
            effects(data)
            if (data.wincons.isNotEmpty()) {
                rule()
                label("Win conditions besides combos")
                wrapped(data.wincons.joinToString(" · "), 2)
            }
        }

        private fun meter(power: Double) {
            val gap = 0.6f
            val w = (INNER - gap * 9) / 10
            for (i in 0 until 10) {
                val x = PAD + i * (w + gap)
                p.fill(x, y, w, 2f, TRACK, 0.5f)
                val fill = (power - i).coerceIn(0.0, 1.0).toFloat()
                if (fill > 0f) p.fill(x, y, w * fill, 2f, METER[i].toArgb(), 0.5f)
            }
            y += 2f
        }

        private fun saltAndMana(data: SaltCard) {
            val top = y
            val half = INNER / 2
            label("Saltiness")
            bodyLine(listOfNotNull(deck.saltPercent?.let { "${it.roundToInt()}%" }, data.saltIntensity?.lowercase()).joinToString(" ").ifEmpty { "—" }, width = half - 1f, weight = Weight.MEDIUM)
            newLine()
            // Wrapped since 1.67: "mostly community salt ratings" doesn't fit on one line of half the card.
            data.saltDriver?.let { y += p.wrapped("mostly ${it.lowercase()}", PAD, y, half - 1f, BODY_PT, GREY, 2, BODY_LH) }
            val left = y
            y = top
            y += p.lineHeight(6.4f)
            p.text("MANABASE", PAD + half, y - 0.45f, LABEL_PT, GREY, Weight.MEDIUM, spacing = 0.04f)
            listOfNotNull(data.manaFixing?.let { "Fixing $it%" }, data.onCurve?.let { "On curve $it%" }).ifEmpty { listOf("—") }.forEach {
                bodyLine(it, PAD + half, half)
                newLine()
            }
            y = maxOf(y, left)
        }

        private fun effects(data: SaltCard) {
            val shown = SaltEffect.entries.mapNotNull { e -> data.effects[e.key]?.takeIf { it > 0 }?.let { e to it } }
                .sortedByDescending { it.second }.take(5)
            if (shown.isEmpty()) return
            rule()
            label("Interaction and effects")
            val max = shown.maxOf { it.second }.coerceAtLeast(1)
            shown.forEach { (e, n) ->
                bodyLine(e.label, width = 20f)
                val bx = PAD + 21f
                val bw = INNER - 21f - 5f
                val by = y + 0.9f
                p.fill(bx, by, bw, 1.6f, TRACK, 0.8f)
                p.fill(bx, by, bw * n / max, 1.6f, if (ink) withAlpha(BAR, 0.55f) else BAR, 0.8f)
                p.text("$n", CARD_W - PAD, y + p.lineHeight(BODY_LH) - 0.55f, BODY_PT, GREY, align = Paint.Align.RIGHT)
                newLine()
            }
        }
    }

    private fun withAlpha(colour: Int, alpha: Float): Int = ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24) or (colour and 0xFFFFFF)
}
