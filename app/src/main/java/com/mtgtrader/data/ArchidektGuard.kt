package com.mtgtrader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Lets requests through one at a time, at least [gapMs] apart (since 1.76): each caller reserves the next free
 * moment and waits for it, so callers on several threads queue up fairly instead of all going at once.
 */
class RequestPacer(private val gapMs: Long, private val now: () -> Long = System::currentTimeMillis) {
    private var next = 0L

    /** Reserves the next moment and returns how long to wait for it (0 when it's now). */
    @Synchronized
    fun reserve(): Long {
        val t = now()
        val at = maxOf(t, next)
        next = at + gapMs
        return at - t
    }
}

/**
 * The switches the app reads from `remote-switches.json` on this repository's main branch (since 1.76), so the
 * owner can turn Archidekt features off on every installed copy at once, without an app update (e.g. if Archidekt
 * asks). A missing or unreadable switch counts as on, and the last answer is kept until the next one is read.
 */
@Serializable
data class Switches(
    /** Logging in to Archidekt: the collection sync, editing decks, private decks. */
    val archidektAccount: Boolean = true,
    /** Reading public decks from Archidekt (importing, reloading, updating decks). */
    val archidektDecks: Boolean = true,
    /** Shown in the app when a switch is off, e.g. why. */
    val message: String = "",
)

class RemoteSwitches(context: Context) {
    private val prefs = context.getSharedPreferences("remote_switches", Context.MODE_PRIVATE)
    private val _switches = MutableStateFlow(parse(prefs.getString(K_JSON, null)) ?: Switches())
    val switches: StateFlow<Switches> = _switches
    val current get() = _switches.value

    /** Reads the switches once a day (on opening the app); a failure keeps the last answer. */
    suspend fun refreshIfDue(http: OkHttpClient) = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - prefs.getLong(K_CHECKED, 0) < CHECK_EVERY_MS) return@withContext
        val body = runCatching {
            http.newCall(Request.Builder().url(URL).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        }.getOrNull() ?: return@withContext
        val s = parse(body) ?: return@withContext
        prefs.edit().putString(K_JSON, body).putLong(K_CHECKED, System.currentTimeMillis()).apply()
        _switches.value = s
    }

    companion object {
        const val URL = "https://raw.githubusercontent.com/joelvdc/MTG-trading-tracker/main/remote-switches.json"
        private const val K_JSON = "json"
        private const val K_CHECKED = "checkedAt"
        private const val CHECK_EVERY_MS = 24L * 60 * 60 * 1000
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

        fun parse(body: String?): Switches? = body?.let { runCatching { json.decodeFromString<Switches>(it) }.getOrNull() }

        /** What the app says when a switch is off. */
        fun offText(s: Switches, what: String) =
            "$what is switched off in MTG Trader for now." + s.message.trim().takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
    }
}

/**
 * Every request to Archidekt's API passes here (since 1.76): it waits so that requests go at least
 * [ArchidektGuard.GAP_MS] apart (Archidekt starts limiting at about 40 a minute), and refuses them when the remote
 * switches turn that part off. Pictures and other addresses go straight through.
 */
class ArchidektGuard(
    private val switches: () -> Switches,
    private val pacer: RequestPacer = RequestPacer(GAP_MS),
    private val sleep: (Long) -> Unit = Thread::sleep,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (isArchidektApi(req)) {
            val s = switches()
            if (!s.archidektAccount && usesAccount(req)) throw IOException(RemoteSwitches.offText(s, "Logging in to Archidekt"))
            if (!s.archidektDecks && !usesAccount(req)) throw IOException(RemoteSwitches.offText(s, "Reading decks from Archidekt"))
            val wait = pacer.reserve()
            if (wait > 0) sleep(wait)
        }
        return chain.proceed(req)
    }

    companion object {
        /** 1.5 seconds apart: at most 40 requests a minute. */
        const val GAP_MS = 1500L

        fun isArchidektApi(req: Request): Boolean {
            val host = req.url.host
            return (host == "archidekt.com" || host.endsWith(".archidekt.com")) && req.url.encodedPath.startsWith("/api/")
        }

        /** A request made with the user's login (or logging in). */
        fun usesAccount(req: Request) = req.header("Authorization") != null || req.url.encodedPath.startsWith("/api/rest-auth/")
    }
}
