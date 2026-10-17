package com.mtgtrader.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Query
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A deck's cover, as stored. */
data class DeckCoverRow(val artUrl: String?, val commanderScryfallId: String?)

@Dao
interface OfflineImagesDao {
    /**
     * Card pictures decks show: their own printings and the printings you own of their cards (a deck
     * shows your printings by default), and the wishlist's.
     */
    @Query(
        """SELECT imageUrl FROM deck_cards WHERE imageUrl IS NOT NULL
           UNION SELECT imageUrl FROM collection WHERE imageUrl IS NOT NULL AND name COLLATE NOCASE IN (SELECT name FROM deck_cards)
           UNION SELECT imageUrl FROM wishlist WHERE imageUrl IS NOT NULL AND receivedAt IS NULL"""
    )
    fun observeCardImages(): Flow<List<String>>

    /** The tokens decks' cards make, as [TokenPart] JSON lists. Since 1.45. */
    @Query("SELECT DISTINCT c.tokens FROM deck_cards d JOIN card_info c ON c.scryfallId = d.scryfallId WHERE c.tokens IS NOT NULL")
    fun observeDeckTokens(): Flow<List<String>>

    @Query("SELECT artUrl, commanderScryfallId FROM decks")
    fun observeCovers(): Flow<List<DeckCoverRow>>
}

/**
 * Keeps the pictures of decks (their cards, covers and tokens) and of the wishlist in the image cache, so
 * they show offline even if they were never on screen. Runs on Wi-Fi only, when decks or the
 * wishlist change and when the phone connects; pictures already saved are skipped. Since 1.34.
 */
@OptIn(FlowPreview::class)
class OfflineImages(
    private val context: Context,
    db: AppDatabase,
    private val settings: Settings,
    private val network: NetworkMonitor,
    scope: CoroutineScope,
) {
    private val dao = db.offlineImagesDao()

    init {
        scope.launch {
            // The decks' tokens too, in the printings they show (since 1.45).
            val tokens = combine(dao.observeDeckTokens(), settings.deckTokenPicks) { lists, picks -> DeckStats.tokenImageIds(lists, picks).map { scryfallImage(it) } }
            combine(settings.offlineImages, network.reconnects, dao.observeCovers(), dao.observeCardImages(), tokens) { on, _, covers, cards, tokenUrls ->
                if (on) covers.mapNotNull { deckCoverUrl(it.artUrl, it.commanderScryfallId) } + cards + tokenUrls else emptyList()
            }
                .debounce(5_000)
                .collectLatest { urls -> if (urls.isNotEmpty() && network.onUnmeteredNetwork()) runCatching { save(urls.distinct()) } }
        }
    }

    /** Downloads the pictures not saved yet, a few at a time; stops when Wi-Fi goes. Returns how many were saved. */
    suspend fun save(urls: List<String>): Int = withContext(Dispatchers.IO) {
        val loader = context.imageLoader
        val cache = loader.diskCache ?: return@withContext 0
        val missing = urls.filter { url -> cache.openSnapshot(url)?.close() == null }
        var saved = 0
        for (chunk in missing.chunked(4)) {
            if (!network.onUnmeteredNetwork()) break
            saved += coroutineScope {
                chunk.map { url ->
                    async {
                        // Decoded small and kept out of memory: only the downloaded file in the disk cache matters.
                        val request = ImageRequest.Builder(context).data(url).size(64).memoryCachePolicy(CachePolicy.DISABLED).build()
                        loader.execute(request) is SuccessResult
                    }
                }.awaitAll().count { it }
            }
        }
        saved
    }
}
