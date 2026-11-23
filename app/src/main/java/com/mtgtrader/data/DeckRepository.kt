package com.mtgtrader.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** An import or refresh in progress: which deck (once known) and what it's doing. [canStop] for batches. */
data class DeckJob(val deckId: Long?, val message: String, val canStop: Boolean = false)

/**
 * How the last import or refresh ended, for the screen to report. [openDeck] is the deck to show
 * straight away (a single fresh import); otherwise [message] is shown.
 */
data class DeckJobResult(val deckId: Long?, val message: String, val openDeck: Long? = null)

/** What adding a deck to the collection would add, for the confirmation dialog. */
data class DeckCollectionCounts(val all: Int, val missing: Int, val allNoBasics: Int, val missingNoBasics: Int) {
    fun count(onlyMissing: Boolean, skipBasics: Boolean) = when {
        onlyMissing && skipBasics -> missingNoBasics
        onlyMissing -> missing
        skipBasics -> allNoBasics
        else -> all
    }
}

/**
 * Commander decks: the list comes from Archidekt, prices from Scryfall + the Cardmarket price
 * guide, and the bracket and salt from the website's estimate (for every deck since 1.116). Imports run in
 * the app scope, so they finish even if the user leaves the screen; one runs at a time.
 */
class DeckRepository(
    context: Context,
    private val db: AppDatabase,
    private val scryfall: ScryfallApi,
    private val archidekt: ArchidektApi,
    private val repo: MtgRepository,
    private val settings: Settings,
    private val scope: CoroutineScope,
    private val winOdds: WinOddsStore,
    /** The website's estimate of every deck's bracket and salt (see [rateLocal]). */
    private val web: WebAccountClient,
) {
    companion object {
        /** Why a deck with changes that never reached Archidekt isn't reloaded. Since 1.78. */
        /** A deck the website couldn't rate (offline, not logged in, or its card facts not ready). */
        const val NOT_RATED = "The bracket couldn't be worked out now (no connection to the website?)"

        const val UNSENT = "This deck has changes from this phone that never reached Archidekt. Make a local copy to keep them, or discard them, then reload"
    }

    private val dao = db.deckDao()

    private val _job = MutableStateFlow<DeckJob?>(null)
    val job: StateFlow<DeckJob?> = _job

    private val _result = MutableStateFlow<DeckJobResult?>(null)
    val result: StateFlow<DeckJobResult?> = _result

    @Volatile
    private var stopRequested = false

    /**
     * Set by [DeckEditor] (since 1.46): whether a deck may be reloaded from Archidekt (false while it holds changes
     * from before 1.78 that never reached Archidekt, so a reload can't lose them), and turning an imported deck into
     * a local one.
     */
    var editSender: (suspend (Long) -> Boolean)? = null
    var detacher: (suspend (Long) -> Long)? = null

    fun consumeResult() {
        _result.value = null
    }

    /** Asks a batch import to stop after the deck it's working on. */
    fun stop() {
        stopRequested = true
        _job.value = _job.value?.copy(message = "Stopping after this deck…", canStop = false)
    }

    /**
     * Starts importing the deck in [link]; false if no Archidekt deck can be found in it or another import is running.
     * With [local] (since 1.46) it becomes a local deck right after: editable, no longer linked to Archidekt.
     */
    fun import(link: String, local: Boolean = false): Boolean {
        val id = DeckLinks.archidektId(link) ?: return false
        return start(DeckJob(null, "Loading the decklist from Archidekt…")) {
            val deck = importOne(id, "")
            val opened = if (local) detacher?.invoke(id) ?: id else id
            DeckJobResult(opened, "Imported ${deck.name}" + if (local) " as a local deck" else "", openDeck = opened)
        }
    }

    /** Reads a deck from Archidekt as anyone can (public and unlisted decks; the app has no Archidekt login since 1.78). */
    private suspend fun readDeck(id: Long): ArchidektDeck = archidekt.deck(id)

    /**
     * Reloads a deck's list from Archidekt without rating it again. Not while it holds changes that never reached
     * Archidekt. Since 1.46.
     */
    suspend fun reloadList(deckId: Long) {
        val deck = dao.get(deckId) ?: return
        if (deck.local) return
        if (editSender?.invoke(deckId) == false) throw IOException(UNSENT)
        load(deckId, {})
    }

    /**
     * Rates the deck again (its bracket and salt, see [rateLocal]). A deck from Archidekt that changed there since it
     * was loaded has its list reloaded first, so the rating and the list shown always belong together.
     */
    fun rescore(deckId: Long): Boolean = start(DeckJob(deckId, "Rating the deck…")) {
        val deck = dao.get(deckId) ?: throw IllegalStateException("The deck is gone")
        // When Archidekt can't be reached, the deck is rated as it is.
        val fetched = if (deck.local) null else runCatching { readDeck(deckId) }.getOrNull()
        val changed = fetched != null && listChanged(deck, fetched.updatedAt)
        if (changed) importOne(deckId, "", fetched = fetched) else rateLocal(deckId, force = true)
        val rated = dao.get(deckId)?.rated == true
        val what = if (changed) "Decklist reloaded (it changed on Archidekt)" else null
        DeckJobResult(deckId, listOfNotNull(what, if (rated) "Bracket updated" else NOT_RATED).joinToString(" · "))
    }

    /** True when Archidekt's version of the deck ([remoteAt]) isn't the one the app loaded. */
    private fun listChanged(deck: Deck, remoteAt: Long?) = remoteAt == null || remoteAt != deck.archidektUpdatedAt

    /** When each deck last changed on Archidekt: one deck-list call per owner (public decks only). */
    private suspend fun archidektDates(decks: List<Deck>): Map<Long, Long?> {
        val listed = HashMap<Long, Long?>()
        for (owner in decks.map { it.owner }.filter { it.isNotBlank() }.distinct()) {
            if (stopRequested) break
            runCatching { archidekt.userDecks(owner) }.getOrNull()?.forEach { listed[it.id] = it.updatedAt }
        }
        return listed
    }

    /** Re-reads the deck from Archidekt (whether it changed or not) and rates it again when its list changed. */
    fun refresh(deckId: Long): Boolean = start(DeckJob(deckId, "Loading the decklist from Archidekt…")) {
        if (deckId < 0) throw IllegalStateException("A local deck isn't on Archidekt")
        val deck = importOne(deckId, "")
        DeckJobResult(deckId, "Reloaded ${deck.name} from Archidekt")
    }

    /** The public decks of an Archidekt user, for choosing which to import. */
    suspend fun userDecks(username: String) = archidekt.userDecks(username)

    suspend fun importedIds(): Set<Long> = dao.ids().toSet()

    /**
     * Fills in Archidekt's last-modified date for decks imported before version 1.8, using one deck-list
     * call per owner instead of downloading every deck again. Quietly does nothing when offline.
     */
    suspend fun fillMissingUpdateDates() {
        val missing = dao.withoutUpdateDate().filter { !it.local }
        for ((owner, decks) in missing.groupBy { it.owner }) {
            if (owner.isBlank()) continue
            val dates = runCatching { archidekt.userDecks(owner) }.getOrNull()?.associate { it.id to it.updatedAt } ?: continue
            decks.forEach { d -> dates[d.archidektId]?.let { dao.setUpdatedAt(d.archidektId, it) } }
        }
    }

    /** Imports several decks one after the other. Can be stopped between decks with [stop]. */
    fun importMany(decks: List<ArchidektDeckSummary>): Boolean {
        if (decks.isEmpty()) return false
        return start(DeckJob(null, "Importing ${decks.size} decks…", canStop = true)) {
            var imported = 0
            val failed = mutableListOf<String>()
            for ((i, d) in decks.withIndex()) {
                if (stopRequested) break
                try {
                    importOne(d.id, "Deck ${i + 1} of ${decks.size} · ${d.name}: ", canStop = true)
                    imported++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += d.name
                }
            }
            val parts = buildList {
                add("Imported $imported of ${decks.size} decks")
                if (failed.isNotEmpty()) add("${failed.size} failed: ${failed.joinToString()}")
            }
            DeckJobResult(null, parts.joinToString(" · ") + if (stopRequested) " · stopped" else "")
        }
    }

    /**
     * Checks every deck against Archidekt (one deck-list call per owner, plus the deck itself for decks not in a public
     * list) and reloads those changed there since they were imported; then rates every deck whose rating is out of
     * date (see [LocalRating.stale]). Can be stopped between decks.
     */
    fun updateAll(): Boolean = start(DeckJob(null, "Checking your decks on Archidekt…", canStop = true)) {
        // Decks imported before 1.8 learn when they last changed on Archidekt (until 1.78 done on opening the Decks tab).
        fillMissingUpdateDates()
        val decks = dao.all().filter { !it.local && !it.retired }
        val listed = archidektDates(decks)
        var updated = 0
        val failed = mutableListOf<String>()
        for ((i, d) in decks.withIndex()) {
            if (stopRequested) break
            val prefix = "Deck ${i + 1} of ${decks.size} · ${d.name}: "
            _job.value = DeckJob(d.archidektId, prefix + "checking…", !stopRequested)
            try {
                val fetched = if (listed[d.archidektId] == null) readDeck(d.archidektId) else null
                val remoteAt = listed[d.archidektId] ?: fetched?.updatedAt
                if (!listChanged(d, remoteAt)) continue
                _job.value = DeckJob(d.archidektId, prefix + "Loading the decklist from Archidekt…", !stopRequested)
                load(d.archidektId, { _job.value = DeckJob(d.archidektId, prefix + it, !stopRequested) }, fetched)
                updated++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += d.name
            }
        }
        val rated = rateStale()
        val parts = buildList {
            add(if (updated == 0) "All ${decks.size} decks are up to date" else "Updated $updated changed deck" + if (updated == 1) "" else "s")
            if (failed.isNotEmpty()) add("${failed.size} failed: ${failed.joinToString()}")
            if (rated > 0) add("rated $rated deck" + if (rated == 1) "" else "s")
        }
        DeckJobResult(null, parts.joinToString(" · ") + if (stopRequested) " · stopped" else "")
    }

    /** Rates every deck whose rating is out of date (see [LocalRating.stale]). Can be stopped between decks. Since 1.115. */
    fun refreshRatings(): Boolean = start(DeckJob(null, "Rating decks…", canStop = true)) {
        val rated = rateStale()
        DeckJobResult(null, "Rated $rated deck" + (if (rated == 1) "" else "s") + if (stopRequested) " · stopped" else "")
    }

    /** The decks not retired whose rating is out of date, rated one after the other; how many were rated. */
    private suspend fun rateStale(): Int {
        val decks = dao.all().filter { !it.retired }
        var rated = 0
        var failed = 0
        for ((i, d) in decks.withIndex()) {
            // Three failures in a row: the website can't be reached now; the rest wait for the next time.
            if (stopRequested || failed >= 3) break
            if (!LocalRating.stale(d, dao.cards(d.archidektId))) continue
            _job.value = DeckJob(d.archidektId, "Deck ${i + 1} of ${decks.size} · ${d.name}: rating…", !stopRequested)
            if (rateLocal(d.archidektId)) { rated++; failed = 0 } else failed++
        }
        return rated
    }

    /** Whether any deck (not retired) has no rating or a stale one for its list (see [LocalRating.stale]). */
    suspend fun ratingsStale(): Boolean = dao.all().any { !it.retired && LocalRating.stale(it, dao.cards(it.archidektId)) }

    private fun start(first: DeckJob, work: suspend () -> DeckJobResult): Boolean {
        synchronized(this) {
            if (_job.value != null) return false
            stopRequested = false
            _job.value = first
        }
        scope.launch {
            _result.value = try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeckJobResult(null, e.message ?: "Import failed")
            } finally {
                _job.value = null
            }
        }
        return true
    }

    /** Loads, saves and rates one deck. */
    private suspend fun importOne(id: Long, prefix: String, canStop: Boolean = false, fetched: ArchidektDeck? = null): Deck {
        fun step(msg: String) {
            _job.value = DeckJob(id, prefix + msg, canStop && !stopRequested)
        }
        // Changes from before 1.78 that never reached Archidekt aren't overwritten without asking (see the deck page).
        if (dao.get(id) != null && editSender?.invoke(id) == false) throw IOException(UNSENT)
        step("Loading the decklist from Archidekt…")
        val deck = load(id, ::step, fetched)
        step("Rating the deck…")
        rateLocal(id)
        return dao.get(id) ?: deck
    }

    /**
     * Fetches the list and saves it, keeping any scores from before until new ones arrive. The primer, the cards'
     * notes and when they changed are kept; a deck without a primer takes Archidekt's description, and a reload of a
     * deck that was already here adds what changed to its history (since 1.97).
     */
    private suspend fun load(id: Long, step: (String) -> Unit, fetched: ArchidektDeck? = null): Deck {
        val a = fetched ?: readDeck(id)
        if (a.cards.isEmpty()) throw IllegalStateException("${a.name.ifBlank { "This deck" }} has no cards")
        step("Looking up ${a.cards.size + a.maybeboard.size} cards…")
        val found = runCatching {
            scryfall.collection((a.cards + a.maybeboard).map { it.scryfallId }.distinct().map(ScryfallApi::idIdentifier)).associateBy { it.id }
        }.getOrDefault(emptyMap())
        fun deckCard(c: ArchidektCard, maybeboard: Boolean): DeckCard {
            val ref = found[c.scryfallId]?.toRef() ?: CardRef(
                scryfallId = c.scryfallId,
                name = c.name,
                setCode = c.setCode,
                setName = c.setName,
                collectorNumber = c.collectorNumber,
                rarity = "",
                imageUrl = c.imageUrl,
                cardmarketId = null,
                fallbackEur = null,
                fallbackEurFoil = null,
                hasNonFoil = true,
                hasFoil = c.foil,
                hasEtched = c.etched,
            )
            return DeckCard(
                deckId = a.id,
                card = ref,
                quantity = c.quantity,
                category = c.category,
                types = c.types,
                cmc = c.cmc,
                commander = c.commander,
                foil = c.foil,
                etched = c.etched,
                gameChanger = c.gameChanger,
                maybeboard = maybeboard,
                categories = c.categories.joinToString("\n"),
                archidektCardId = c.archidektCardId,
                relationId = c.relationId,
                label = c.label,
            )
        }
        val cards = a.cards.map { deckCard(it, false) } + a.maybeboard.map { deckCard(it, true) }
        val old = dao.get(a.id)
        // The lines as they are now, read before the reload replaces them: they say what changed (since 1.97).
        val oldCards = if (old == null) emptyList() else dao.cards(a.id)
        val now = System.currentTimeMillis()
        var deck = (old ?: Deck(a.id, "", "", "", null, null, "", 0)).copy(
            name = a.name,
            owner = a.owner,
            commanders = a.commanders.map { it.name }.distinct().joinToString(" & "),
            commanderScryfallId = a.commanders.firstOrNull()?.scryfallId,
            artUrl = a.artUrl,
            colorIdentity = a.colorIdentity,
            cardCount = a.cardCount,
            importedAt = now,
            archidektUpdatedAt = a.updatedAt ?: old?.archidektUpdatedAt,
            folderId = a.folderId ?: old?.folderId,
            folderName = old?.folderName?.takeIf { old.folderId == a.folderId },
            visibility = a.visibility,
            categoryInfo = DeckEdits.encode(a.categories),
        )
        // Archidekt's description becomes the primer of a deck that never had one ("" means the user cleared it).
        if (deck.primer == null) DeckNotes.primerFromArchidekt(a.description)?.let { deck = deck.copy(primer = it, notesAt = now) }
        // A reload of a deck that was here before adds what the new list changed to its history (a first import has none).
        if (old != null) deck = deck.copy(history = DeckHistory.record(old.history, now, DeckHistory.SOURCE_ARCHIDEKT, DeckHistory.diff(oldCards, cards)))
        dao.replace(deck, cards)
        return deck
    }

    /**
     * Has the website estimate a deck's bracket and salt when that's stale for its list (see [LocalRating.stale]), or
     * with [force] in any case, and keeps it as the deck's rule-zero data. True when it rated the deck. Quietly false
     * when the website can't be reached or can't rate it yet.
     */
    suspend fun rateLocal(deckId: Long, force: Boolean = false): Boolean {
        val deck = dao.get(deckId) ?: return false
        val cards = dao.cards(deckId)
        if (cards.isEmpty() || !force && !LocalRating.stale(deck, cards)) return false
        val card = try {
            SaltCard.decode(web.rate(cards, deck.colorIdentity)) ?: return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        dao.update(
            deck.copy(
                saltCard = card.encode(),
                bracketBaseline = card.bracket,
                bracketRealistic = null,
                scoredAt = System.currentTimeMillis(),
                scoreError = null,
            )
        )
        return true
    }

    suspend fun delete(deckId: Long) {
        dao.delete(deckId)
        db.deckEditDao().clear(deckId)
    }

    // ---- deck ↔ collection -----------------------------------------------------------------

    suspend fun ownedByName() = db.collectionDao().ownedByName().associate { it.name.lowercase() to it.qty }

    suspend fun collectionCounts(deckId: Long): DeckCollectionCounts {
        val cards = dao.cards(deckId)
        val owned = ownedByName()
        fun n(onlyMissing: Boolean, skipBasics: Boolean) = DeckToCollection.plan(cards, owned, onlyMissing, skipBasics).sumOf { it.second }
        return DeckCollectionCounts(n(false, false), n(true, false), n(false, true), n(true, true))
    }

    /** Adds the deck's cards (the exact printings and finishes) to the collection. Returns copies added and an undo. */
    suspend fun addDeckToCollection(deckId: Long, onlyMissing: Boolean, skipBasics: Boolean, binderId: Long): Pair<Int, UndoAction> {
        val plan = DeckToCollection.plan(dao.cards(deckId), ownedByName(), onlyMissing, skipBasics)
        val added = db.withTransaction {
            plan.map { (c, n) -> repo.addToCollection(c.card, c.finish, "NM", "EN", n, binderId) to n }
        }
        val undo: UndoAction = {
            db.withTransaction {
                for ((result, n) in added) {
                    val item = db.collectionDao().byId(result.itemId) ?: continue
                    if (item.quantity > n) db.collectionDao().update(item.copy(quantity = item.quantity - n))
                    else db.collectionDao().deleteById(item.id)
                }
            }
        }
        return added.sumOf { it.second } to undo
    }

    /** Removes a card that was added to the deck in the app. */
    suspend fun removeAddedCard(cardId: Long) = dao.deleteCard(cardId)
}
