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
    private val settings: Settings,
    private val edh: EdhPowerLevelApi,
    private val scrollVault: ScrollVaultApi,
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

    @Volatile
    private var powerStopped = false

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

    /**
     * Has Commander Salt score the deck again (brackets and its power level) and, when another site
     * is the power level source, that site too. If the deck changed on Archidekt since it was
     * loaded, its list is reloaded first, so the scores and the list shown always belong together.
     */
    fun rescore(deckId: Long): Boolean = start(DeckJob(deckId, "Checking the decklist on Archidekt…")) {
        val deck = dao.get(deckId) ?: throw IllegalStateException("The deck is gone")
        val source = settings.powerSource.value
        // When Archidekt can't be reached, the deck is scored as it is.
        val fetched = runCatching { archidekt.deck(deckId) }.getOrNull()
        val changed = fetched != null && listChanged(deck, fetched.updatedAt)
        val done = if (changed) importOne(deckId, "", fetched = fetched).first
        else score(deck) { _job.value = DeckJob(deckId, it) }
        val problems = listOfNotNull(
            done.scoreError?.let { "Commander Salt couldn't score it: $it" },
            done.powerError(source)?.takeIf { source.external }?.let { "${source.site} couldn't rate it: $it" },
        )
        val what = if (changed) "Decklist reloaded (it changed on Archidekt)" else null
        DeckJobResult(deckId, listOfNotNull(what, if (problems.isEmpty()) "Bracket and power level updated" else null).plus(problems).joinToString(" · "))
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

    /** Re-reads the deck from Archidekt (whether it changed or not) and has it scored again. */
    fun refresh(deckId: Long): Boolean = start(DeckJob(deckId, "Loading the decklist from Archidekt…")) {
        val (deck, error) = importOne(deckId, "")
        DeckJobResult(deckId, error?.let { "${deck.name} saved, but Commander Salt couldn't score it: $it" } ?: "Reloaded ${deck.name} from Archidekt and scored it again")
    }

    /** The public decks of an Archidekt user, for choosing which to import. */
    suspend fun userDecks(username: String) = archidekt.userDecks(username)

    suspend fun importedIds(): Set<Long> = dao.ids().toSet()

    /**
     * Fills in Archidekt's last-modified date for decks imported before version 1.8, using one deck-list
     * call per owner instead of downloading every deck again. Quietly does nothing when offline.
     */
    suspend fun fillMissingUpdateDates() {
        val missing = dao.withoutUpdateDate()
        for ((owner, decks) in missing.groupBy { it.owner }) {
            if (owner.isBlank()) continue
            val dates = runCatching { archidekt.userDecks(owner) }.getOrNull()?.associate { it.id to it.updatedAt } ?: continue
            decks.forEach { d -> dates[d.archidektId]?.let { dao.setUpdatedAt(d.archidektId, it) } }
        }
    }

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

    /**
     * Checks every deck against Archidekt (one deck-list call per owner, plus the deck itself for
     * decks not in a public list) and reloads and re-scores those changed there since they were
     * imported. Decks that never got a score are scored again too. Can be stopped between decks.
     */
    fun updateAll(): Boolean = start(DeckJob(null, "Checking your decks on Archidekt…", canStop = true)) {
        val decks = dao.all()
        val source = settings.powerSource.value
        val listed = archidektDates(decks)
        var updated = 0
        var rescored = 0
        val unscored = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var busy = false
        for ((i, d) in decks.withIndex()) {
            if (stopRequested) break
            val prefix = "Deck ${i + 1} of ${decks.size} · ${d.name}: "
            _job.value = DeckJob(d.archidektId, prefix + "checking…", !stopRequested)
            try {
                val fetched = if (listed[d.archidektId] == null) archidekt.deck(d.archidektId) else null
                val remoteAt = listed[d.archidektId] ?: fetched?.updatedAt
                val changed = listChanged(d, remoteAt)
                val needsPower = source.external && d.power(source) == null
                if (!changed && d.scored && d.scoreError == null && !needsPower) continue
                // A pause between decks that hit Commander Salt, so it isn't flooded.
                if (busy) delay(1_500)
                busy = true
                val error = if (changed) {
                    importOne(d.archidektId, prefix, canStop = true, fetched = fetched).second.also { updated++ }
                } else if (d.scored && d.scoreError == null) {
                    ratePower(d) { _job.value = DeckJob(d.archidektId, prefix + it, !stopRequested) }.powerError(source).also { rescored++ }
                } else {
                    _job.value = DeckJob(d.archidektId, prefix + "Scoring on Commander Salt…", !stopRequested)
                    score(d) { _job.value = DeckJob(d.archidektId, prefix + it, !stopRequested) }.scoreError.also { rescored++ }
                }
                if (error != null) unscored += d.name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += d.name
            }
        }
        val parts = buildList {
            add(
                when {
                    updated == 0 && rescored == 0 -> "All ${decks.size} decks are up to date"
                    else -> "Updated $updated changed deck" + (if (updated == 1) "" else "s") +
                        (if (rescored > 0) ", scored $rescored more" else "")
                }
            )
            if (unscored.isNotEmpty()) add("${unscored.size} couldn't be scored: ${unscored.joinToString()}")
            if (failed.isNotEmpty()) add("${failed.size} failed: ${failed.joinToString()}")
        }
        DeckJobResult(null, parts.joinToString(" · ") + if (stopRequested) " · stopped" else "")
    }

    /**
     * Has Commander Salt (and the chosen power level site) score every deck again, e.g. after it
     * changed its scoring. Decks that changed on Archidekt are reloaded first, so the scores and
     * the lists shown belong together. Can be stopped between decks.
     */
    fun rescoreAll(): Boolean = start(DeckJob(null, "Checking your decks on Archidekt…", canStop = true)) {
        val decks = dao.all()
        val listed = archidektDates(decks)
        var scored = 0
        var reloaded = 0
        val unscored = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for ((i, d) in decks.withIndex()) {
            if (stopRequested) break
            if (i > 0) delay(1_500)
            val prefix = "Deck ${i + 1} of ${decks.size} · ${d.name}: "
            _job.value = DeckJob(d.archidektId, prefix + "checking…", !stopRequested)
            try {
                val fetched = if (listed[d.archidektId] == null) runCatching { archidekt.deck(d.archidektId) }.getOrNull() else null
                val remoteAt = listed[d.archidektId] ?: fetched?.updatedAt
                // Unreachable on Archidekt (e.g. made private): score what the app has.
                val changed = (listed.containsKey(d.archidektId) || fetched != null) && listChanged(d, remoteAt)
                val result = if (changed) {
                    reloaded++
                    importOne(d.archidektId, prefix, canStop = true, fetched = fetched).first
                } else {
                    _job.value = DeckJob(d.archidektId, prefix + "Scoring on Commander Salt…", !stopRequested)
                    score(d) { _job.value = DeckJob(d.archidektId, prefix + it, !stopRequested) }
                }
                if (result.scoreError == null) scored++ else unscored += d.name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += d.name
            }
        }
        val parts = buildList {
            add("Scored $scored of ${decks.size} decks" + if (reloaded > 0) " ($reloaded changed on Archidekt and " + (if (reloaded == 1) "was" else "were") + " reloaded first)" else "")
            if (unscored.isNotEmpty()) add("${unscored.size} couldn't be scored: ${unscored.joinToString()}")
            if (failed.isNotEmpty()) add("${failed.size} failed: ${failed.joinToString()}")
        }
        DeckJobResult(null, parts.joinToString(" · ") + if (stopRequested) " · stopped" else "")
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
    private suspend fun importOne(id: Long, prefix: String, canStop: Boolean = false, fetched: ArchidektDeck? = null): Pair<Deck, String?> {
        fun step(msg: String) {
            _job.value = DeckJob(id, prefix + msg, canStop && !stopRequested)
        }
        step("Loading the decklist from Archidekt…")
        val deck = load(id, ::step, fetched)
        step("Scoring on Commander Salt…")
        val scored = score(deck, ::step)
        return scored to scored.scoreError
    }

    /** Fetches the list and saves it, keeping any scores from before until new ones arrive. */
    private suspend fun load(id: Long, step: (String) -> Unit, fetched: ArchidektDeck? = null): Deck {
        val a = fetched ?: archidekt.deck(id)
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
            archidektUpdatedAt = a.updatedAt ?: old?.archidektUpdatedAt,
        ).let {
            // The list may have changed: the chosen site's power level is redone right after; the other site's is dropped.
            val source = settings.powerSource.value
            var d = it
            if (source != PowerSource.EDH_POWER_LEVEL) d = d.copy(edhPowerLevel = null, edhPowerAt = null, edhPowerError = null)
            if (source != PowerSource.SCROLLVAULT) d = d.copy(svPowerLevel = null, scrollVault = null, svAt = null, svError = null)
            d
        }
        dao.replace(deck, cards)
        return deck
    }

    private suspend fun score(deck: Deck, step: (String) -> Unit): Deck {
        val scored = try {
            val s = salt.score(deck.archidektId)
            deck.copy(
                saltCard = s.card?.encode() ?: deck.saltCard,
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
        // The rule-zero cards are drawn in the app now; Commander Salt's own images are fetched only when asked for.
        RuleZeroCard.entries.forEach { cardFile(scored.archidektId, it).delete() }
        return ratePower(scored, step)
    }

    /** The deck's link on edhpowerlevel.com (its list as on Archidekt, without cards added in the app). */
    suspend fun edhUrl(deckId: Long): String? {
        val cards = dao.cards(deckId).filter { !it.addedInApp }
        val commanders = cards.filter { it.commander }.map { it.card.name }.distinct()
        if (commanders.isEmpty()) return null
        val main = cards.filter { !it.commander }.groupBy { it.card.name }.map { (name, lines) -> name to lines.sumOf { it.quantity } }
        return EdhPowerLevelLink.url(commanders, main)
    }

    /** Has the chosen site (if not Commander Salt) rate the deck's power level; failures are kept on the deck rather than thrown. */
    private suspend fun ratePower(deck: Deck, step: (String) -> Unit): Deck = when (settings.powerSource.value) {
        PowerSource.COMMANDER_SALT -> deck
        PowerSource.EDH_POWER_LEVEL -> rateEdh(deck, step)
        PowerSource.SCROLLVAULT -> rateScrollVault(deck, step)
    }

    private suspend fun rateScrollVault(deck: Deck, step: (String) -> Unit): Deck {
        val rated = try {
            step("Getting the power level from ScrollVault…")
            val r = scrollVault.rate(deck.archidektUrl)
            deck.copy(svPowerLevel = r.power, scrollVault = r.encode(), svAt = System.currentTimeMillis(), svError = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deck.copy(svError = e.message ?: "unknown error")
        }
        dao.update(rated)
        return rated
    }

    private suspend fun rateEdh(deck: Deck, step: (String) -> Unit): Deck {
        val rated = try {
            val url = edhUrl(deck.archidektId) ?: throw IllegalStateException("the deck has no commander")
            val commander = EdhPowerLevelLink.frontFace(deck.commanders.substringBefore(" & "))
            step("Getting the power level from edhpowerlevel.com…")
            val r = edh.rate(url, commander)
            deck.copy(edhPowerLevel = r.power, edhPowerAt = System.currentTimeMillis(), edhPowerError = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deck.copy(edhPowerError = e.message ?: "unknown error")
        }
        dao.update(rated)
        return rated
    }

    /**
     * Gets the chosen site's power level for the decks that don't have one yet (after choosing it
     * as the source). Decks it failed on before are left for "Re-score all" or the deck's refresh.
     */
    fun rateMissingPower(): Boolean {
        val source = settings.powerSource.value
        if (!source.external) return false
        return start(DeckJob(null, "Getting power levels from ${source.site}…", canStop = true)) {
            powerStopped = false
            val decks = dao.all().filter { it.power(source) == null && it.powerError(source) == null }
            var rated = 0
            val failed = mutableListOf<String>()
            for ((i, d) in decks.withIndex()) {
                if (stopRequested) break
                val prefix = "Deck ${i + 1} of ${decks.size} · ${d.name}: "
                val r = ratePower(d) { _job.value = DeckJob(d.archidektId, prefix + it, !stopRequested) }
                if (r.powerError(source) == null) rated++ else failed += d.name
            }
            // Stopped: don't start again by itself until asked (Settings) or the app restarts.
            if (stopRequested) powerStopped = true
            val parts = buildList {
                add("Got $rated power level" + (if (rated == 1) "" else "s") + " from ${source.site}")
                if (failed.isNotEmpty()) add("${failed.size} failed: ${failed.joinToString()}")
            }
            DeckJobResult(null, parts.joinToString(" · ") + if (stopRequested) " · stopped" else "")
        }
    }

    /** Whether some decks still need the chosen site's power level. */
    suspend fun powerMissing(): Boolean {
        val source = settings.powerSource.value
        return !powerStopped && source.external && dao.all().any { it.power(source) == null && it.powerError(source) == null }
    }

    /** Fetches the rule-zero card details for decks scored before 1.12 (without scoring them again). Quietly does nothing when offline. */
    suspend fun fillMissingCardData() {
        for (d in dao.all().filter { it.saltId != null && it.saltCard == null }) loadCardData(d)
    }

    /** The deck's rule-zero card details, fetching them from Commander Salt when they aren't saved yet. */
    suspend fun loadCardData(deck: Deck): SaltCard? {
        SaltCard.decode(deck.saltCard)?.let { return it }
        val saltId = deck.saltId ?: return null
        val card = runCatching { salt.cardData(saltId) }.getOrNull() ?: return null
        dao.get(deck.archidektId)?.let { dao.update(it.copy(saltCard = card.encode())) }
        return card
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
