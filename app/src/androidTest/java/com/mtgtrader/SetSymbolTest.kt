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
import com.mtgtrader.scan.OcrLine
import com.mtgtrader.scan.ScanGuide
import com.mtgtrader.scan.SetSymbolMatcher
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The set-symbol matching on real card scans from Scryfall, drawn into a camera-sized frame where a
 * user would hold the card: OCR finds the type line, the symbol is cut out and compared with the
 * symbols of every set the card was printed in. Needs a connection.
 */
@RunWith(AndroidJUnit4::class)
class SetSymbolTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MtgApp
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Older printings (no set code on the card): set code and name. */
    private val cases = listOf(
        "m10" to "Lightning Bolt",
        "tmp" to "Counterspell",
        "10e" to "Serra Angel",
        "7ed" to "Llanowar Elves",
        "m11" to "Inferno Titan",
        "10e" to "Wrath of God",
        "lrw" to "Shriekmaw",
        "isd" to "Delver of Secrets",
        "zen" to "Lotus Cobra",
        "som" to "Mox Opal",
        "ice" to "Swords to Plowshares",
        "usg" to "Duress",
        "mir" to "Pacifism",
        "ons" to "Naturalize",
        "7ed" to "Terror",
        "mmq" to "Brainstorm",
        "rtr" to "Abrupt Decay",
        "mrd" to "Thoughtcast",
        "chk" to "Sensei's Divining Top",
        "tor" to "Basking Rootwalla",
    )

    private fun frameFor(set: String, name: String, inset: Float): Bitmap? {
        val card = runBlocking { app.container.scryfall.fuzzy(name, set) }?.takeIf { it.set == set } ?: return null
        val url = card.image!!.replace("/normal/", "/large/")
        val bytes = app.container.http.newCall(Request.Builder().url(url).build()).execute().use { it.body!!.bytes() }
        val img = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val frame = Bitmap.createBitmap(1440, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        canvas.drawColor(Color.rgb(60, 50, 45))
        val g = ScanGuide.boxFor(1440f, 1920f)
        canvas.drawBitmap(img, null, RectF(g.left + g.width * inset, g.top + g.height * inset, g.right - g.width * inset, g.bottom - g.height * inset), Paint(Paint.FILTER_BITMAP_FLAG))
        return frame
    }

    @Test
    fun matchesTheSetOfOlderPrintings() {
        var right = 0
        var wrong = 0
        for (inset in listOf(0.04f, 0.1f)) for ((setNum, name) in cases) {
            val frame = frameFor(setNum, name, inset) ?: continue.also { Log.i("SymbolTest", "$setNum $name: not on Scryfall") }
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(frame, 0)))
            val lines = text.textBlocks.flatMap { b -> b.lines.mapNotNull { l -> l.boundingBox?.let { OcrLine(l.text, it.left, it.top, it.right, it.bottom) } } }
            val g = ScanGuide.boxFor(1440f, 1920f)
            val type = SetSymbolMatcher.typeLine(lines, g)
            val photo = SetSymbolMatcher.fromFrame(frame, lines, g)
            // The cut-out region, for looking at when tuning: adb pull /sdcard/Android/data/com.mtgtrader/files/symbols
            type?.let { t ->
                val lh = maxOf(t.height.toFloat(), g.height * 0.025f)
                val y0 = (t.cy - lh * 1.25f).toInt()
                val x0 = (g.left + g.width * 0.74f).toInt()
                val crop = Bitmap.createBitmap(frame, x0, y0, (g.width * 0.23f).toInt(), (lh * 2.5f).toInt())
                val dir = java.io.File(app.getExternalFilesDir(null), "symbols").apply { mkdirs() }
                java.io.File(dir, "$setNum-${name.replace(' ', '_')}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val n = com.mtgtrader.scan.SetSymbol.N
                Log.i("SymbolTest", "$setNum masks:\n" + (0 until n).joinToString("\n") { y ->
                    photo.joinToString("  ") { p -> (0 until n).joinToString("") { x -> if (p.cells[y * n + x]) "#" else "." } }
                })
            }
            if (photo.isEmpty()) {
                Log.i("SymbolTest", "$setNum $name: no symbol (type line: ${type?.text})")
                continue
            }
            val scores = runBlocking { app.container.symbols.scores(name, photo) }
            val match = runBlocking { app.container.symbols.match(name, photo) }
            val expected = setNum
            Log.i("SymbolTest", "$setNum $name (type line: ${type?.text}) -> ${match?.card?.set} " + scores.take(4).joinToString { "${it.first}=${"%.2f".format(it.second)}" })
            when {
                match == null -> {}
                scores.first().first.split('/').contains(expected) -> right++
                else -> wrong++
            }
        }
        Log.i("SymbolTest", "right $right, wrong $wrong of ${cases.size}")
        // Unsure matches are left alone (the printing stays "choose printing"); wrong ones must be rare.
        assertTrue("right $right, wrong $wrong", right >= cases.size && wrong <= 1)
    }
}
