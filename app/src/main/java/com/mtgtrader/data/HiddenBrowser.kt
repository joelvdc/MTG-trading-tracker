package com.mtgtrader.data

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Runs a web tool that has no API (edhpowerlevel.com, ScrollVault) in an invisible web view and
 * reads its result off the page. One page at a time; each gets a fresh web view.
 */
class HiddenBrowser(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    /**
     * Loads [url]; if [start] is given, runs it every second until it returns "started" (e.g. fill in
     * a form and press its button). Then reads the page text every second and returns [parse]'s
     * result once two reads in a row agree. With [allowedHosts], requests to other sites (ads,
     * trackers) are answered empty: nobody sees this page, so it shouldn't count as a visit to them.
     */
    suspend fun <T : Any> read(
        url: String,
        site: String,
        allowedHosts: Set<String>? = null,
        start: String? = null,
        timeoutS: Int = 60,
        parse: (String) -> T?,
    ): T = mutex.withLock {
        withContext(Dispatchers.Main) {
            val web = newWebView()
            var failed: String? = null
            web.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failed = error.description?.toString() ?: "page didn't load"
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val host = request.url.host ?: return null
                    if (allowedHosts == null || allowedHosts.any { host == it || host.endsWith(".$it") }) return null
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
            try {
                web.loadUrl(url)
                var started = start == null
                var previous: T? = null
                repeat(timeoutS) {
                    delay(1_000)
                    failed?.let { throw IOException("Couldn't reach $site: $it") }
                    if (!started) {
                        started = web.evaluate(start!!) == "started"
                        return@repeat
                    }
                    val reading = parse(web.evaluate(TEXT))
                    if (reading != null && reading == previous) return@withContext reading
                    previous = reading
                }
                throw IOException(if (started) "$site didn't finish rating the deck" else "$site's page didn't load properly")
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
        measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY))
        layout(0, 0, 1080, 2000)
    }

    private suspend fun WebView.evaluate(script: String): String = suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { result ->
            // The result comes back as a JSON value (here a string).
            val text = runCatching { (Json.parseToJsonElement(result ?: "null") as? JsonPrimitive)?.takeIf { it.isString }?.content }.getOrNull()
            if (cont.isActive) cont.resume(text.orEmpty())
        }
    }

    private companion object {
        const val TEXT = "(document.querySelector('main') || document.body || {innerText: ''}).innerText"
    }
}
