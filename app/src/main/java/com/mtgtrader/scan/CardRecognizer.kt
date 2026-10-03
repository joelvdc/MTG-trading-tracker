package com.mtgtrader.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgtrader.data.ScryCard
import com.mtgtrader.data.ScryfallApi
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Turns OCR clues into a concrete Scryfall printing. Results (including misses) are cached per clue. */
class CardRecognizer(private val api: ScryfallApi) {
    private val cache = HashMap<String, ScryCard?>()

    private suspend fun lookup(key: String, fetch: suspend () -> ScryCard?): ScryCard? {
        if (cache.containsKey(key)) return cache[key]
        val result = try {
            fetch()
        } catch (e: IOException) {
            return null // network hiccup: don't cache, try again on a later frame
        }
        cache[key] = result
        return result
    }

    suspend fun identify(c: ScanClues): ScryCard? = identifyPrinting(c)?.card

    /** Like [identify], but also says whether the exact printing (set + number) was confirmed. */
    suspend fun identifyPrinting(c: ScanClues): Identified? {
        val set = c.setCode?.lowercase()
        val num = c.collectorNumber
        val byNumber = if (set != null && num != null) lookup("sn:$set/$num") { api.bySetNumber(set, num) } else null

        var byName: ScryCard? = null
        var nameInSet = false
        for (n in c.names) {
            if (set != null) {
                byName = lookup("n:$n|$set") { api.fuzzy(n, set) }
                nameInSet = byName != null
            }
            if (byName == null) byName = lookup("n:$n") { api.fuzzy(n) }
            if (byName != null) break
        }

        val result = when {
            byNumber != null && byName != null ->
                if (byNumber.name.equals(byName.name, ignoreCase = true)) Identified(byNumber, true)
                else Identified(byName, nameInSet)
            byName != null -> Identified(byName, nameInSet)
            // Non-English cards: the name won't match, but set + number + language is a strong signal.
            byNumber != null && c.language != null -> Identified(byNumber, true)
            else -> null
        }
        return result?.takeIf { !it.card.digital }
    }
}

/** A recognised card; [exactPrinting] is false when only the name could be read (older frames). */
data class Identified(val card: ScryCard, val exactPrinting: Boolean)

/** CameraX analyzer running ML Kit on-device text recognition; reports parsed clues on the main thread. */
class CardTextAnalyzer(private val onClues: (ScanClues) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val paused = AtomicBoolean(false)

    /** Set when a card's printing was only guessed from its name: the next frame's set symbol is cut out into [symbol]. */
    val symbolWanted = AtomicBoolean(false)

    /** The last set symbol cut out of a frame, with when (elapsed realtime). */
    @Volatile
    var symbol: Pair<Long, List<Silhouette>>? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || paused.get()) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val w = if (rotation % 180 == 0) proxy.width else proxy.height
        val h = if (rotation % 180 == 0) proxy.height else proxy.width
        val frame = if (symbolWanted.get()) runCatching { upright(proxy.toBitmap(), rotation) }.getOrNull() else null
        recognizer.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { b ->
                    b.lines.mapNotNull { l ->
                        l.boundingBox?.let { r -> OcrLine(l.text, r.left, r.top, r.right, r.bottom) }
                    }
                }
                val guide = ScanGuide.boxFor(w.toFloat(), h.toFloat())
                if (frame != null) {
                    runCatching { SetSymbolMatcher.fromFrame(frame, lines, guide) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                        symbol = SystemClock.elapsedRealtime() to it
                        symbolWanted.set(false)
                    }
                    frame.recycle()
                }
                onClues(CardTextParser.parse(lines, guide))
            }
            .addOnFailureListener { frame?.recycle() }
            .addOnCompleteListener { proxy.close() }
    }

    fun close() = recognizer.close()

    private fun upright(b: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return b
        val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (r !== b) b.recycle()
        return r
    }
}
