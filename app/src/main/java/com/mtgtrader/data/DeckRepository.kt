package com.mtgtrader.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** An import or refresh in progress: which deck (once known) and what it's doing. */
data class DeckJob(val deckId: Long?, val message: String)

/** How the last import or refresh ended, for the screen to report. [deckId] is null if nothing was saved. */
data class DeckJobResult(val deckId: Long?, val message: String, val imported: Boolean)

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
    private val scope: CoroutineScope,
) {
    private val dao = db.deckDao()
    private val cardDir = File(context.filesDir, "rulezero")

    private val _job = MutableStateFlow<DeckJob?>(null)
    val job: StateFlow<DeckJob?> = _job

    private val _result = MutableStateFlow<DeckJobResult?>(null)
    val result: StateFlow<DeckJobResult?> = _result

    fun consumeResult() {
        _result.value = null
    }

    /** Starts importing the deck in [link]; false if no Archidekt deck can be found in it or another import is running. */
    fun import(link: String): Boolean {
        val id = DeckLinks.archidektId(link) ?: return false
        return start(id, imported = true)
    }

    /** Re-reads the deck from Archidekt and has it scored again. */
    fun refresh(deckId: Long): Boolean = start(deckId, imported = false)

    private fun start(id: Long, imported: Boolean): Boolean {
        synchronized(this) {
            if (_job.value != null) return false
            _job.value = DeckJob(id.takeIf { !imported }, "Loading the decklist from Archidekt…")
        }
        scope.launch {
            _result.value = try {
                val deck = load(id)
                _job.value = DeckJob(id, "Scoring on Commander Salt…")
                val scored = score(deck)
                val message = when {
                    scored.scoreError != null -> "${deck.name} saved, but Commander Salt couldn't score it: ${scored.scoreError}"
                    imported -> "Imported ${deck.name}"
                    else -> "${deck.name} is up to date"
                }
                DeckJobResult(id, message, imported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeckJobResult(null, e.message ?: "Import failed", imported)
            } finally {
                _job.value = null
            }
        }
        return true
    }

    /** Fetches the list and saves it, keeping any scores from before until new ones arrive. */
    private suspend fun load(id: Long): Deck {
        val a = archidekt.deck(id)
        if (a.cards.isEmpty()) throw IllegalStateException("${a.name.ifBlank { "This deck" }} has no cards")
        _job.value = DeckJob(id, "Looking up ${a.cards.size} cards…")
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

    private suspend fun score(deck: Deck): Deck {
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
            _job.value = DeckJob(deck.archidektId, "Downloading the rule-zero cards…")
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
}
