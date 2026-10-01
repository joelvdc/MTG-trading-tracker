package com.mtgtrader.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume

/** What edhpowerlevel.com reports for a deck. */
data class EdhReading(val power: Double, val imported: Int?)

/** Building the edhpowerlevel.com link for a deck, and reading its result from the page text. */
object EdhPowerLevelLink {
    /** "Cut // Ribbons" → "Cut": the site only knows front faces. */
    fun frontFace(name: String) = name.substringBefore(" // ").trim()

    /** The site's shared-list link: "Commander" and "Mainboard" sections, one "qty name" per line, lines joined by "~". */
    fun url(commanders: List<String>, mainboard: List<Pair<String, Int>>): String {
        val lines = listOf("Commander") + commanders.map { "1 ${frontFace(it)}" } +
            listOf("", "Mainboard") + mainboard.map { (name, qty) -> "$qty ${frontFace(name)}" }
        return "https://edhpowerlevel.com/?d=" + lines.joinToString("~") { URLEncoder.encode(it, "UTF-8") } + "~Z~"
    }

    private val powerRe = Regex("""Power Level\s*([\d.]+)\s*/\s*10\b""", RegexOption.IGNORE_CASE)
    private val importedRe = Regex("""(\d+) total cards imported""", RegexOption.IGNORE_CASE)

    /** The result in the page's text, or null while it's still working. */
    fun parse(pageText: String): EdhReading? {
        val power = powerRe.find(pageText)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return EdhReading(power, importedRe.find(pageText)?.groupValues?.get(1)?.toIntOrNull())
    }
}

/**
 * edhpowerlevel.com has no API: its calculator runs in the web page. So the app opens the deck's
 * link in an invisible web view, waits for the page to finish, and reads the power level off it.
 * One deck at a time.
 */
class EdhPowerLevelApi(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    /** Rates the deck at [url]; [commander] must show up in the result (so it's this deck's, not the sample's). */
    suspend fun rate(url: String, commander: String): EdhReading = mutex.withLock {
        withContext(Dispatchers.Main) {
            val web = newWebView()
            var failed: String? = null
            web.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failed = error.description?.toString() ?: "page didn't load"
                }
            }
            try {
                web.loadUrl(url)
                var previous: EdhReading? = null
                repeat(TIMEOUT_S) {
                    delay(1_000)
                    failed?.let { throw IOException("Couldn't reach edhpowerlevel.com: $it") }
                    val text = web.evaluate("document.body ? document.body.innerText : ''")
                    val reading = EdhPowerLevelLink.parse(text)?.takeIf { text.contains(commander, ignoreCase = true) }
                    // The numbers are worked out in the page; take them once two reads in a row agree.
                    if (reading != null && reading == previous) return@withContext reading
                    previous = reading
                }
                throw IOException("edhpowerlevel.com didn't finish rating the deck")
            } finally {
                web.stopLoading()
                web.destroy()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView() = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Only the numbers are needed, not the pictures.
        settings.blockNetworkImage = true
        // Laid out at a phone's size, though never shown.
        measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2000, android.view.View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, 1080, 2000)
    }

    private suspend fun WebView.evaluate(script: String): String = suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { result ->
            // The result comes back as a JSON value (here a string).
            val text = runCatching { (Json.parseToJsonElement(result ?: "null") as? JsonPrimitive)?.content }.getOrNull()
            if (cont.isActive) cont.resume(text.orEmpty())
        }
    }

    private companion object {
        const val TIMEOUT_S = 60
    }
}
