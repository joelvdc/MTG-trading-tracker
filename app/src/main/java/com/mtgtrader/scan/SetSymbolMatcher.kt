package com.mtgtrader.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.mtgtrader.data.ScryCard
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.SetIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** A printing chosen by its set symbol, with how sure the match is. */
data class SymbolMatch(val card: ScryCard, val score: Float, val margin: Float)

/**
 * Tells printings apart by their expansion symbol when the scanner could only read the card's name
 * (cards from before 2015 have no set code printed): the symbol photographed at the end of the type
 * line is compared with Scryfall's symbol of every set the card was printed in. Since 1.16.
 */
class SetSymbolMatcher(context: Context, private val http: OkHttpClient, private val icons: SetIcons, private val scryfall: ScryfallApi) {
    private val dir = File(context.cacheDir, "set-symbols")
    private val shapes = HashMap<String, Silhouette?>()
    private val lock = Mutex()

    /**
     * The printing of [name] whose symbol matches the [photos] (cut-outs of one symbol), or null
     * without a clear winner (or when every printing has the same symbol). Within the winning
     * symbol, [preferId] is kept if it's there.
     */
    suspend fun match(name: String, photos: List<Silhouette>, preferId: String? = null): SymbolMatch? {
        val prints = runCatching { scryfall.prints(name) }.getOrNull()?.filter { !it.digital }.orEmpty()
        if (icons.icons.value.isEmpty()) icons.load()
        val map = icons.icons.value
        val byIcon = prints.groupBy { map[it.set.lowercase()] }.filterKeys { it != null }
        if (byIcon.size < 2) return null
        val scored = byIcon.mapNotNull { (url, cards) -> shape(url!!)?.let { s -> Triple(url, cards, photos.maxOf { SetSymbol.similarity(it, s) }) } }
            .sortedByDescending { it.third }
        val best = scored.firstOrNull() ?: return null
        val margin = best.third - (scored.getOrNull(1)?.third ?: 0f)
        if (best.third < MIN_SCORE || margin < MIN_MARGIN) return null
        val cards = best.second
        // Promo sets borrow their parent's symbol: prefer the set the symbol belongs to.
        val card = cards.firstOrNull { it.id == preferId }
            ?: cards.firstOrNull { "/sets/${it.set.lowercase()}.svg" in best.first }
            ?: cards.minByOrNull { it.releasedAt ?: "9999" }!!
        return SymbolMatch(card, best.third, margin)
    }

    /** Every symbol's score against the photo, best first; for testing and tuning. */
    suspend fun scores(name: String, photos: List<Silhouette>): List<Pair<String, Float>> {
        val prints = scryfall.prints(name).filter { !it.digital }
        if (icons.icons.value.isEmpty()) icons.load()
        val map = icons.icons.value
        return prints.groupBy { map[it.set.lowercase()] }.filterKeys { it != null }
            .mapNotNull { (url, cards) -> shape(url!!)?.let { s -> cards.joinToString("/") { c -> c.set } to photos.maxOf { SetSymbol.similarity(it, s) } } }
            .sortedByDescending { it.second }
    }

    private suspend fun shape(url: String): Silhouette? = lock.withLock {
        if (shapes.containsKey(url)) return shapes[url]
        val s = runCatching { render(svgText(url)) }.getOrNull()
        shapes[url] = s
        s
    }

    private suspend fun svgText(url: String): String = withContext(Dispatchers.IO) {
        val file = File(dir, url.substringAfterLast('/').substringBefore('?').ifEmpty { url.hashCode().toString() })
        if (file.exists()) return@withContext file.readText()
        val text = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            r.body!!.string()
        }
        dir.mkdirs()
        file.writeText(text)
        text
    }

    companion object {
        const val MIN_SCORE = 0.55f
        const val MIN_MARGIN = 0.05f
        private const val RENDER = 128

        private val TYPE_WORDS = listOf(
            "creature", "kreatur", "créature", "creatura", "criatura", "instant", "spontanzauber", "éphémère", "istantaneo", "instantáneo",
            "instantânea", "sorcery", "hexerei", "rituel", "stregoneria", "conjuro", "feitiço", "artifact", "artefakt", "artefact", "artefatto",
            "artefacto", "artefato", "enchant", "verzauberung", "incantesimo", "encantamiento", "encantamento", "land", "terrain", "terra",
            "tierra", "planeswalker", "legendary", "legendär", "légendaire", "leggendari", "legendari", "lendári", "tribal", "kindred",
            "battle", "basic", "summon", "interrupt",
        )

        fun render(svgText: String): Silhouette? {
            val svg = SVG.getFromString(svgText)
            val bmp = Bitmap.createBitmap(RENDER, RENDER, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bmp), RenderOptions().viewPort(0f, 0f, RENDER.toFloat(), RENDER.toFloat()))
            val px = IntArray(RENDER * RENDER)
            bmp.getPixels(px, 0, RENDER, 0, 0, RENDER, RENDER)
            bmp.recycle()
            return SetSymbol.fromAlpha(IntArray(px.size) { px[it] ushr 24 }, RENDER, RENDER)
        }

        /** The OCR'd type line ("Creature — Elf"), in the middle of the card. */
        fun typeLine(lines: List<OcrLine>, guide: Box): OcrLine? = lines
            .filter { it.cy in (guide.top + guide.height * 0.45f)..(guide.top + guide.height * 0.72f) && it.left < guide.left + guide.width * 0.3f }
            // It starts with the card type ("Legendary Creature — Elf"); rules text below may mention types too.
            .filter { l -> val t = l.text.lowercase().trimStart(); TYPE_WORDS.any { t.startsWith(it) } }
            .minByOrNull { it.top }

        /** The symbol in an upright camera frame, cut out at the right end of the type line; null if there's no type line. */
        fun fromFrame(frame: Bitmap, lines: List<OcrLine>, guide: Box): List<Silhouette> {
            val line = typeLine(lines, guide) ?: return emptyList()
            val lh = max(line.height.toFloat(), guide.height * 0.025f)
            val y0 = (line.cy - lh * 1.25f).roundToInt().coerceIn(0, frame.height - 1)
            val y1 = (line.cy + lh * 1.25f).roundToInt().coerceIn(y0 + 1, frame.height)
            val x0 = (guide.left + guide.width * 0.74f).roundToInt().coerceIn(0, frame.width - 1)
            val x1 = (guide.left + guide.width * 0.97f).roundToInt().coerceIn(x0 + 1, frame.width)
            return fromRegion(frame, x0, y0, x1 - x0, y1 - y0)
        }

        fun fromRegion(frame: Bitmap, x: Int, y: Int, w: Int, h: Int): List<Silhouette> {
            // Small crops are scaled up so thin outlines stay connected; big ones down for speed.
            val scale = (64f / h).coerceIn(0.5f, 3f)
            val sw = max(8, (w * scale).roundToInt())
            val sh = max(8, (h * scale).roundToInt())
            val crop = Bitmap.createBitmap(frame, x, y, w, h)
            val scaled = Bitmap.createScaledBitmap(crop, sw, sh, true)
            val px = IntArray(sw * sh)
            scaled.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (scaled !== crop) scaled.recycle()
            if (crop !== frame) crop.recycle()
            val luma = IntArray(px.size) { i ->
                val p = px[i]
                ((p shr 16 and 255) * 299 + (p shr 8 and 255) * 587 + (p and 255) * 114) / 1000
            }
            return SetSymbol.candidates(luma, sw, sh)
        }
    }
}
