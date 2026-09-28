package com.mtgtrader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mtgtrader.data.ScryCard
import com.mtgtrader.scan.CardRecognizer
import com.mtgtrader.scan.CardTextParser
import com.mtgtrader.scan.OcrLine
import com.mtgtrader.scan.ScanClues
import com.mtgtrader.scan.ScanGuide
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real OCR → parser → Scryfall identification on card images drawn into a
 * camera-sized frame roughly where a user would hold the card (a bit smaller than the guide).
 */
@RunWith(AndroidJUnit4::class)
class ScanPipelineTest {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MtgApp

    private fun frameWith(asset: String, inset: Float): Bitmap {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val card = ctx.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        val frame = Bitmap.createBitmap(1440, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        canvas.drawColor(Color.rgb(60, 50, 45))
        val g = ScanGuide.boxFor(1440f, 1920f)
        val dx = g.width * inset
        val dy = g.height * inset
        canvas.drawBitmap(card, null, RectF(g.left + dx, g.top + dy, g.right - dx, g.bottom - dy), Paint(Paint.FILTER_BITMAP_FLAG))
        return frame
    }

    private fun scan(asset: String, inset: Float = 0.04f): Pair<ScanClues, ScryCard?> {
        val bmp = frameWith(asset, inset)
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
        val lines = text.textBlocks.flatMap { b ->
            b.lines.mapNotNull { l -> l.boundingBox?.let { OcrLine(l.text, it.left, it.top, it.right, it.bottom) } }
        }
        val g = ScanGuide.boxFor(1440f, 1920f)
        lines.filter { it.cy > g.top + g.height * 0.8f }.forEach {
            Log.i("ScanTest", "  $asset bottom line @(${((it.cx - g.left) / g.width * 100).toInt()}%,${((it.cy - g.top) / g.height * 100).toInt()}%): ${it.text}")
        }
        val clues = CardTextParser.parse(lines, g)
        val card = runBlocking { CardRecognizer(app.container.scryfall).identify(clues) }
        Log.i("ScanTest", "$asset -> $clues -> ${card?.name} ${card?.set}/${card?.collectorNumber}")
        return clues to card
    }

    /** Pre-2014 frames print no set code, so only the card (not the exact printing) can be read. */
    @Test
    fun oldFrameEnglish() {
        val (_, card) = scan("old_frame.png")
        assertEquals("Inferno Titan", card?.name)
    }

    @Test
    fun currentFrameEnglishExactPrinting() {
        val (clues, card) = scan("new_frame.png")
        assertEquals("Sire of Seven Deaths", card?.name)
        assertEquals("fdn", card?.set)
        assertEquals("1", clues.collectorNumber)
    }

    @Test
    fun germanCardIdentifiedBySetAndNumber() {
        val (clues, card) = scan("german.png")
        assertEquals("DE", clues.language)
        assertEquals("Analyze the Pollen", card?.name)
        assertEquals("mkm", card?.set)
        assertEquals("150", card?.collectorNumber)
    }

    /** Universes Beyond cards printed under another name: "Barrow-Downs" is Bojuka Bog (LTC). */
    @Test
    fun flavorNamedCard() {
        val (_, card) = scan("barrow_downs_358.png")
        assertEquals("Bojuka Bog", card?.name)
        assertEquals("ltc", card?.set)
        assertEquals("358", card?.collectorNumber)
    }

    @Test
    fun flavorNamedCardBorderless() {
        val (_, card) = scan("barrow_downs_388.png")
        assertEquals("Bojuka Bog", card?.name)
        assertEquals("ltc", card?.set)
        assertEquals("388", card?.collectorNumber)
    }

    @Test
    fun cardHeldSmallerThanGuide() {
        val (_, card) = scan("old_frame.png", inset = 0.12f)
        assertEquals("Inferno Titan", card?.name)
    }
}
