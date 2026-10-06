package com.mtgtrader.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Set code → expansion symbol (SVG) URL. Promo, token and other sub-sets reuse their parent's
 * symbol, so the URL can't be derived from the code; the list comes from Scryfall and is cached on disk.
 */
class SetIcons(context: Context, private val scryfall: ScryfallApi, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "set_icons.tsv")
    private val datesFile = File(context.filesDir, "set_dates.tsv")
    private val lock = Mutex()
    private val refreshedForMissing = AtomicBoolean(false)
    private val _icons = MutableStateFlow<Map<String, String>>(emptyMap())
    val icons: StateFlow<Map<String, String>> = _icons

    private val _dates = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Set code → release date ("2013-11-01"), for the collection stats. Since 1.21. */
    val dates: StateFlow<Map<String, String>> = _dates

    /** Reads the cached list, then refreshes it from Scryfall if it is missing or older than a week. */
    suspend fun load() {
        val cached = withContext(Dispatchers.IO) { if (file.exists()) decode(file.readText()) else emptyMap() }
        if (cached.isNotEmpty()) _icons.value = cached
        val dates = withContext(Dispatchers.IO) { if (datesFile.exists()) decode(datesFile.readText()) else emptyMap() }
        if (dates.isNotEmpty()) _dates.value = dates
        if (cached.isEmpty() || dates.isEmpty() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) refresh()
    }

    /** A card's set isn't in the cached list (e.g. a set released since): refetch, at most once per app run. */
    fun onMissing() {
        if (_icons.value.isEmpty() || !refreshedForMissing.compareAndSet(false, true)) return
        scope.launch { refresh() }
    }

    private suspend fun refresh() = lock.withLock {
        try {
            val sets = scryfall.sets()
            val map = sets.mapNotNull { s -> s.iconSvgUri?.let { s.code.lowercase() to it } }.toMap()
            if (map.isEmpty()) return@withLock
            val dates = sets.mapNotNull { s -> s.releasedAt?.let { s.code.lowercase() to it } }.toMap()
            _icons.value = map
            _dates.value = dates
            withContext(Dispatchers.IO) {
                file.writeText(encode(map))
                datesFile.writeText(encode(dates))
            }
        } catch (e: Exception) {
            // Offline: keep whatever is cached; symbols are only a visual aid.
        }
    }

    companion object {
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        fun encode(map: Map<String, String>): String = map.entries.joinToString("\n") { "${it.key}\t${it.value}" }

        fun decode(text: String): Map<String, String> = text.lineSequence()
            .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 && it[0].isNotEmpty() }?.let { it[0] to it[1] } }
            .toMap()
    }
}
