package com.mtgtrader.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
 * guide, and the power level, brackets and rule-zero cards from Commander Salt. Imports run in
 * the app scope, so they finish even if the user leaves the screen; one runs at a time.
 */
class DeckRepository(
    context: Context,
    private val db: AppDatabase,
    private val scryfall: ScryfallApi,
    private val archidekt: ArchidektApi,
    private val salt: CommanderSaltApi,
    private val repo: MtgRepository,
    private val scope: CoroutineScope,
) {
    private val dao = db.deckDao()
    private val cardDir = File(context.filesDir, "rulezero")

    private val _job = MutableStateFlow<DeckJob?>(null)
    val job: StateFlow<DeckJob?> = _job

    private val _result = MutableStateFlow<DeckJobResult?>(null)
    val result: StateFlow<DeckJobResult?> = _result

    @Volatile
    private var stopRequested = false

    fun consumeResult() {
        _result.value = null
    }

    /** Asks a batch import to stop after the deck it's working on. */
    fun stop() {
        stopRequested = true
        _job.value = _job.value?.copy(message = "Stopping after this deck…", canStop = false)
    }

    /** Starts importing the deck in [link]; false if no Archidekt deck can be found in it or another import is running. */
    fun import(link: String): Boolean {
        val id = DeckLinks.archidektId(link) ?: return false
        return start(DeckJob(null, "Loading the decklist from Archidekt…")) {
            val (deck, error) = importOne(id, "")
            DeckJobResult(id, error?.let { "${deck.name} saved, but Commander Salt couldn't score it: $it" } ?: "Imported ${deck.name}", openDeck = id)
        }
    }

    /** Re-reads the deck from Archidekt and has it scored again. */
    fun refresh(deckId: Long): Boolean = start(DeckJob(deckId, "Loading the decklist from Archidekt…")) {
        val (deck, error) = importOne(deckId, "")
        DeckJobResult(deckId, error?.let { "${deck.name} saved, but Commander Salt couldn't score it: $it" } ?: "${deck.name} is up to date")
    }

    /** The public decks of an Archidekt user, for choosing which to import. */
    suspend fun userDecks(username: String) = archidekt.userDecks(username)

    suspend fun importedIds(): Set<Long> = dao.ids().toSet()

    /**
     * Imports several decks one after the other, with a short pause between them so Commander Salt
     * isn't flooded. Can be stopped between decks with [stop].
     */
    fun importMany(decks: List<ArchidektDeckSummary>): Boolean {
        if (decks.isEmpty()) return false
        return start(DeckJob(null, "Importing ${decks.size} decks…", canStop = true)) {
            var imported = 0
            val unscored = mutableListOf<String>()
            val failed = mutableListOf<String>()
            for ((i, d) in decks.withIndex()) {
                if (stopRequested) break
                if (i > 0) delay(1_500)
                try {
                    val (_, error) = importOne(d.id, "Deck ${i + 1} of ${decks.size} · ${d.name}: ", canStop = true)
                    imported++
                    if (error != null) unscored += d.name
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += d.name
                }
            }
            val parts = buildList {
                add("Imported $imported of ${decks.size} decks")
                if (unscored.isNotEmpty()) add("${unscored.size} couldn't be scored (refresh them later)")
                if (failed.isNotEmpty()) add("${failed.size} failed: ${failed.joinToString()}")
            }
            DeckJobResult(null, parts.joinToString(" · ") + if (stopRequested) " · stopped" else "")
        }
    }

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

    /** Loads, saves and scores one deck. Returns the deck and why scoring failed, if it did. */
    private suspend fun importOne(id: Long, prefix: String, canStop: Boolean = false): Pair<Deck, String?> {
        fun step(msg: String) {
            _job.value = DeckJob(id, prefix + msg, canStop && !stopRequested)
        }
        step("Loading the decklist from Archidekt…")
        val deck = load(id, ::step)
        step("Scoring on Commander Salt…")
        val scored = score(deck, ::step)
        return scored to scored.scoreError
    }

    /** Fetches the list and saves it, keeping any scores from before until new ones arrive. */
    private suspend fun load(id: Long, step: (String) -> Unit): Deck {
        val a = archidekt.deck(id)
        if (a.cards.isEmpty()) throw IllegalStateException("${a.name.ifBlank { "This deck" }} has no cards")
        step("Looking up ${a.cards.size} cards…")
        val found = runCatching {
            scryfall.collection(a.cards.map { it.scryfallId }.distinct().map(ScryfallApi::idIdentifier)).associateBy { it.id }
        }.getOrDefault(emptyMap())
        val cards = a.cards.map { c ->
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
            DeckCard(
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
            )
        }
        val old = dao.get(a.id)
        val deck = (old ?: Deck(a.id, "", "", "", null, null, "", 0)).copy(
            name = a.name,
            owner = a.owner,
            commanders = a.commanders.map { it.name }.distinct().joinToString(" & "),
            commanderScryfallId = a.commanders.firstOrNull()?.scryfallId,
            artUrl = a.artUrl,
            colorIdentity = a.colorIdentity,
            cardCount = a.cardCount,
            importedAt = System.currentTimeMillis(),
        )
        dao.replace(deck, cards)
        return deck
    }

    private suspend fun score(deck: Deck, step: (String) -> Unit): Deck {
        val scored = try {
            val s = salt.score(deck.archidektId)
            deck.copy(
                saltId = s.saltId.ifEmpty { CommanderSaltApi.deckId(deck.archidektId) },
                powerLevel = s.powerLevel,
                bracketRealistic = s.bracketRealistic,
                bracketBaseline = s.bracketBaseline,
                saltPercent = s.saltPercent,
                archetype = s.archetype,
                scoredAt = System.currentTimeMillis(),
                scoreError = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deck.copy(scoreError = e.message ?: "unknown error")
        }
        dao.update(scored)
        if (scored.scoreError == null) {
            step("Downloading the rule-zero cards…")
            RuleZeroCard.entries.forEach { runCatching { downloadCard(scored, it) } }
        }
        return scored
    }

    /** Where a deck's rule-zero card is kept, so it can be shown without a connection. */
    fun cardFile(deckId: Long, card: RuleZeroCard) = File(cardDir, "${deckId}_${card.name.lowercase()}.png")

    /** Fetches the rule-zero card from Commander Salt and saves it. Throws if that fails. */
    suspend fun downloadCard(deck: Deck, card: RuleZeroCard): File {
        val saltId = deck.saltId ?: throw IllegalStateException("The deck hasn't been scored yet")
        val bytes = salt.ruleZeroCard(saltId, card)
        return withContext(Dispatchers.IO) {
            cardDir.mkdirs()
            val target = cardFile(deck.archidektId, card)
            val tmp = File(cardDir, target.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
            target
        }
    }

    suspend fun delete(deckId: Long) {
        dao.delete(deckId)
        withContext(Dispatchers.IO) { RuleZeroCard.entries.forEach { cardFile(deckId, it).delete() } }
    }

    // ---- deck ↔ collection -----------------------------------------------------------------

    private suspend fun ownedByName() = db.collectionDao().ownedByName().associate { it.name.lowercase() to it.qty }

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

    /**
     * Adds scanned cards to a deck as cards "Added in app" (Archidekt itself isn't changed); they're
     * kept when the deck is refreshed. Unless [keep], the scans leave the scan list.
     */
    suspend fun scansToDeck(scanIds: List<Long>, deckId: Long, keep: Boolean): UndoAction {
        val scans = repo.scansById(scanIds)
        // Card types group the cards in the deck; the scan doesn't have them, Scryfall does.
        val info = runCatching {
            scryfall.collection(scans.map { it.card.scryfallId }.distinct().map(ScryfallApi::idIdentifier)).associateBy { it.id }
        }.getOrDefault(emptyMap())
        val inserted = mutableListOf<Long>()
        val grown = mutableListOf<Pair<Long, Int>>()
        db.withTransaction {
            val existing = dao.cardsAddedInApp(deckId)
            for (s in scans) {
                val same = existing.firstOrNull { it.card.scryfallId == s.card.scryfallId && it.foil == s.foil && it.etched == s.etched }
                if (same != null) {
                    dao.addQuantity(same.id, s.quantity)
                    grown += same.id to s.quantity
                } else {
                    val sc = info[s.card.scryfallId]
                    inserted += dao.insertCard(
                        DeckCard(
                            deckId = deckId,
                            card = s.card,
                            quantity = s.quantity,
                            category = DeckToCollection.ADDED_IN_APP,
                            types = sc?.typeLine?.substringBefore(" — ") ?: "",
                            cmc = sc?.cmc ?: 0.0,
                            commander = false,
                            foil = s.foil,
                            etched = s.etched,
                            addedInApp = true,
                        )
                    )
                }
            }
            if (!keep) repo.deleteScans(scanIds)
        }
        return {
            db.withTransaction {
                dao.deleteCardsById(inserted)
                for ((id, n) in grown) dao.addQuantity(id, -n)
                if (!keep) repo.restoreScans(scans)
            }
        }
    }

    /** Removes a card that was added to the deck in the app. */
    suspend fun removeAddedCard(cardId: Long) = dao.deleteCard(cardId)
}
