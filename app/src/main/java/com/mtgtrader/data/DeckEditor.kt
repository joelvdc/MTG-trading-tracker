package com.mtgtrader.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.UUID

/** How a send of waiting edits went: [skipped] are the cards Archidekt refused (their edits were dropped). */
data class DeckSendReport(val deckId: Long, val sent: Int, val skipped: List<String>, val error: String? = null) {
    val message: String
        get() = buildList {
            if (sent > 0) add("Sent $sent change" + (if (sent == 1) "" else "s") + " to Archidekt")
            if (skipped.isNotEmpty()) add("Archidekt refused ${skipped.size} (skipped): ${skipped.joinToString()}")
            if (error != null) add("Not sent yet: $error")
        }.joinToString(" · ")
}

/**
 * Editing decks (since 1.46): the logged-in Archidekt user's own decks and local decks (made in the app or
 * detached from Archidekt). An edit shows at once: it's written to the phone, and for a deck on Archidekt the
 * changed card lines wait in [DeckEdit]s until this phone sends them, right away when it's online and logged
 * in, else when it's back online. What's sent is each line as it is then, the way Archidekt's own deck editor
 * sends it (modifyCards/v2: "add", "modify" or "remove"); a change Archidekt refuses (a card already removed
 * there) is skipped and reported. Every edit returns an undo.
 */
class DeckEditor(
    private val db: AppDatabase,
    private val scryfall: ScryfallApi,
    private val archidekt: ArchidektSync,
    private val decks: DeckRepository,
    private val settings: Settings,
    private val network: NetworkMonitor,
    private val precons: PreconStore,
    private val repo: MtgRepository,
    private val scope: CoroutineScope,
) {
    private val dao = db.deckDao()
    private val edits = db.deckEditDao()
    private val sendLock = Mutex()
    private var sendJob: Job? = null
    private val autoCache = HashMap<String, ArchidektPrinting>()

    private val _report = MutableStateFlow<DeckSendReport?>(null)
    /** The last send that skipped something or failed, for the deck page to report; cleared when shown. */
    val report: StateFlow<DeckSendReport?> = _report

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    /** Edits waiting to be sent, per deck. */
    val waiting: Flow<Map<Long, Int>> = edits.observeCounts().map { l -> l.associate { it.deckId to it.count } }

    init {
        // Edits made offline go once the phone is back online.
        scope.launch { network.reconnects.drop(1).collect { sendSoon(0) } }
    }

    fun consumeReport() {
        _report.value = null
    }

    /** The Archidekt user whose decks count as "own": the one logged in, else the one decks were last imported from. */
    fun owner(): String? = archidekt.username ?: settings.archidektUser.takeIf { it.isNotBlank() }

    /** Whether the deck can be edited here: a local deck, or one of the logged-in Archidekt user's own decks. */
    fun canEdit(deck: Deck): Boolean = deck.local || archidekt.username?.equals(deck.owner, ignoreCase = true) == true

    /**
     * Whether a deck from Archidekt is ready to be edited: loaded by 1.46 or later, so every line knows its id on
     * Archidekt (else a change would be sent as a new card). Such a load keeps the deck's categories, which older
     * versions didn't; [prepare] reloads the list when they're missing.
     */
    fun ready(deck: Deck): Boolean = deck.local || deck.categoryInfo != null

    // ---- Getting a deck ready -----------------------------------------------------------------

    /**
     * Before the first edit: gives lines from before 1.46 an id of their own and, for a deck on Archidekt whose
     * lines don't know their Archidekt ids yet, reloads the list from Archidekt. Returns why it couldn't, if so.
     */
    suspend fun prepare(deckId: Long): String? {
        val deck = dao.get(deckId) ?: return "The deck is gone"
        val lines = dao.allLines(deckId)
        val nameless = lines.filter { it.lineUid.isEmpty() }
        if (nameless.isNotEmpty()) db.withTransaction { nameless.forEach { dao.setLineUid(it.id, UUID.randomUUID().toString()) } }
        if (deck.local || !canEdit(deck) || ready(deck)) return null
        if (edits.count(deckId) > 0) return null
        return runCatching { decks.reloadList(deckId) }.exceptionOrNull()?.let { it.message ?: "Archidekt couldn't be reached" }
    }

    // ---- Edits -----------------------------------------------------------------------------------

    /**
     * Replaces the deck's lines [before] (by their lineUid) with [after]: lines only in [before] are removed, lines only
     * in [after] are added. Queues them for Archidekt when the deck is there, and returns the undo.
     */
    private suspend fun change(deckId: Long, before: List<DeckCard>, after: List<DeckCard>): UndoAction {
        val deck = dao.get(deckId) ?: return {}
        val uids = (before.map { it.lineUid } + after.map { it.lineUid }).distinct()
        db.withTransaction {
            dao.deleteLines(deckId, uids)
            dao.insertCards(after.map { it.copy(id = 0, deckId = deckId) })
            if (!deck.local) {
                val old = before.associateBy { it.lineUid }
                edits.add(uids.mapNotNull { uid ->
                    val was = old[uid]
                    val now = after.firstOrNull { it.lineUid == uid }
                    // Cards "Added in app" aren't on Archidekt; only lines that are (or are meant to be) get sent.
                    if ((was == null || was.addedInApp) && (now == null || now.addedInApp)) return@mapNotNull null
                    DeckEdit(
                        deckId = deckId,
                        lineUid = uid,
                        relationId = was?.relationId ?: now?.relationId,
                        archidektCardId = was?.archidektCardId ?: now?.archidektCardId,
                        name = (now ?: was)!!.card.name,
                        categories = (was ?: now)?.categoryList?.joinToString("\n"),
                    )
                })
            }
        }
        refreshSummary(deckId)
        if (!deck.local) sendSoon()
        return { change(deckId, after, before) }
    }

    /** Keeps the deck's card count and commander(s) in step with its lines. */
    private suspend fun refreshSummary(deckId: Long) {
        val deck = dao.get(deckId) ?: return
        val lines = dao.cards(deckId)
        val commanders = lines.filter { it.commander }
        val names = commanders.map { it.card.name }.distinct().joinToString(" & ")
        dao.update(
            deck.copy(
                cardCount = lines.sumOf { it.quantity },
                commanders = names,
                commanderScryfallId = commanders.firstOrNull()?.card?.scryfallId,
                colorIdentity = if (names == deck.commanders) deck.colorIdentity else identityOf(commanders) ?: deck.colorIdentity,
            )
        )
    }

    /** The commanders' colour identity, from Scryfall; null when it can't be reached. */
    private suspend fun identityOf(commanders: List<DeckCard>): String? {
        if (commanders.isEmpty()) return ""
        val cards = runCatching { scryfall.collection(commanders.map { ScryfallApi.idIdentifier(it.card.scryfallId) }) }.getOrNull() ?: return null
        val colours = cards.flatMap { it.colorIdentity }.toSet()
        return MtgColors.ORDER.filter { it.toString() in colours }
    }

    private suspend fun lines(deckId: Long, uids: Collection<String>): List<DeckCard> = dao.allLines(deckId).filter { it.lineUid in uids }

    suspend fun setQuantity(deckId: Long, uid: String, quantity: Int): UndoAction {
        val line = lines(deckId, listOf(uid)).firstOrNull() ?: return {}
        return change(deckId, listOf(line), if (quantity <= 0) emptyList() else listOf(line.copy(quantity = quantity)))
    }

    suspend fun remove(deckId: Long, uids: Collection<String>): UndoAction = change(deckId, lines(deckId, uids), emptyList())

    /**
     * Moves lines to [category]: [AUTO] for each card's Archidekt auto category, [DeckEdits.MAYBEBOARD] (or another
     * category left out of the deck) to the maybeboard, [DeckEdits.COMMANDER] to make them the commander.
     */
    suspend fun move(deckId: Long, uids: Collection<String>, category: String): UndoAction {
        val before = lines(deckId, uids)
        val deck = dao.get(deckId) ?: return {}
        val info = DeckEdits.categories(deck.categoryInfo)
        val auto = if (category == AUTO) autoCategories(before.map { it.card.scryfallId }) else emptyMap()
        val after = before.map { line ->
            val to = if (category == AUTO) auto[line.card.scryfallId] ?: fallbackCategory(line.types) else category
            line.inCategory(to, maybe = !DeckEdits.included(to, info)).copy(
                commander = to.equals(DeckEdits.COMMANDER, true),
                addedInApp = false,
            )
        }
        return change(deckId, before, after)
    }

    /** Puts lines in the maybeboard, or back in the deck (into their auto category). */
    suspend fun toBoard(deckId: Long, uids: Collection<String>, maybeboard: Boolean): UndoAction =
        move(deckId, uids, if (maybeboard) DeckEdits.MAYBEBOARD else AUTO)

    /** "Send to maybeboard": the cards "Added in app" become maybeboard cards (on Archidekt too). */
    suspend fun addedToMaybeboard(deckId: Long): UndoAction =
        move(deckId, dao.cardsAddedInApp(deckId).map { it.lineUid }, DeckEdits.MAYBEBOARD)

    /**
     * Renames a category: its cards move to [to]. On Archidekt the category itself is renamed first (since 1.47), so
     * it keeps its place there instead of an emptied old one staying behind.
     */
    suspend fun renameCategory(deckId: Long, from: String, to: String): UndoAction {
        val before = dao.allLines(deckId).filter { it.category.equals(from, true) }
        val deck = dao.get(deckId) ?: return {}
        val old = DeckEdits.categories(deck.categoryInfo).firstOrNull { it.name.equals(from, true) }
        if (!deck.local && old?.id != null) queueCategory(deckId, old.id, from, DeckEdits.renameOp(to))
        renameInfo(deck, from, to)
        settings.renameDeckHidden(deckId, from, to)
        val info = DeckEdits.categories(dao.get(deckId)?.categoryInfo)
        val undo = change(deckId, before, before.map { it.inCategory(to, maybe = !DeckEdits.included(to, info)) })
        return {
            undo()
            dao.get(deckId)?.let { d ->
                val now = DeckEdits.categories(d.categoryInfo)
                if (!d.local && old?.id != null) queueCategory(deckId, old.id, to, DeckEdits.renameOp(from))
                dao.update(d.copy(categoryInfo = DeckEdits.encode(now.filterNot { it.name.equals(to, true) || it.name.equals(from, true) } + (old ?: ArchidektCategory(from)))))
                settings.renameDeckHidden(deckId, to, from)
            }
        }
    }

    /**
     * Deletes a category: its cards move to [moveTo], or to their auto categories when null, and since 1.47 the
     * emptied category is deleted on Archidekt too.
     */
    suspend fun deleteCategory(deckId: Long, name: String, moveTo: String?): UndoAction {
        val uids = dao.allLines(deckId).filter { it.category.equals(name, true) }.map { it.lineUid }
        val undo = move(deckId, uids, moveTo ?: AUTO)
        val deck = dao.get(deckId) ?: return undo
        val info = DeckEdits.categories(deck.categoryInfo)
        val old = info.firstOrNull { it.name.equals(name, true) }
        dao.update(deck.copy(categoryInfo = DeckEdits.encode(info.filterNot { it.name.equals(name, true) })))
        val queued = if (!deck.local && old?.id != null) queueCategory(deckId, old.id, name, DeckEdits.OP_DELETE) else null
        val wasHidden = settings.isDeckHidden(deckId, name)
        settings.setDeckHidden(deckId, name, false)
        return {
            // Not sent yet: the deletion is simply dropped; sent already: Archidekt makes the category again when
            // the cards go back into it.
            val notSent = queued != null && edits.forDeck(deckId).any { it.lineUid == queued }
            queued?.let { edits.deleteByUid(deckId, it) }
            undo()
            dao.get(deckId)?.let { d ->
                val now = DeckEdits.categories(d.categoryInfo)
                val back = old?.let { if (queued == null || notSent) it else it.copy(id = null) } ?: ArchidektCategory(name)
                if (now.none { it.name.equals(name, true) }) dao.update(d.copy(categoryInfo = DeckEdits.encode(now + back)))
            }
            settings.setDeckHidden(deckId, name, wasHidden)
        }
    }

    /** A new, empty category; on Archidekt it's made right away (since 1.47; before, only when a card went in). */
    suspend fun addCategory(deckId: Long, name: String) {
        val d = dao.get(deckId) ?: return
        val info = DeckEdits.categories(d.categoryInfo)
        if (info.any { it.name.equals(name, true) } || name.equals(DeckEdits.COMMANDER, true) || name.equals(DeckEdits.MAYBEBOARD, true)) return
        dao.update(d.copy(categoryInfo = DeckEdits.encode(info + ArchidektCategory(name))))
        if (!d.local) queueCategory(deckId, null, name, DeckEdits.OP_CREATE)
    }

    /** Queues a change to a category itself for Archidekt; returns the edit's key. */
    private suspend fun queueCategory(deckId: Long, categoryId: Long?, name: String, op: String): String {
        val uid = DeckEdits.CATEGORY_PREFIX + UUID.randomUUID()
        edits.add(listOf(DeckEdit(deckId = deckId, lineUid = uid, relationId = categoryId, archidektCardId = null, name = name, categories = op)))
        sendSoon()
        return uid
    }

    private suspend fun renameInfo(deck: Deck, from: String, to: String) {
        val info = DeckEdits.categories(deck.categoryInfo)
        val old = info.firstOrNull { it.name.equals(from, true) }
        val kept = info.filterNot { it.name.equals(from, true) || it.name.equals(to, true) }
        dao.update(deck.copy(categoryInfo = DeckEdits.encode(kept + ArchidektCategory(to, old?.included ?: true, old?.id))))
    }

    /** Every card of the deck (not the commander or the maybeboard) into its Archidekt auto category. */
    suspend fun autoCategoriseAll(deckId: Long): UndoAction {
        val uids = dao.cards(deckId).filter { !it.commander }.map { it.lineUid }
        return move(deckId, uids, AUTO)
    }

    /**
     * Adds a card to an editable deck (from the search or the scanner): into its auto category (or by card type
     * when auto categories are off or it has none), next to the same printing and finish if it's there already.
     */
    suspend fun addCard(deckId: Long, card: CardRef, finish: Finish, quantity: Int = 1, category: String? = null): AddResult {
        val f = card.resolveFinish(finish)
        val same = dao.cards(deckId).firstOrNull { it.card.scryfallId == card.scryfallId && it.foil == f.foil && it.etched == f.etched && !it.addedInApp }
        if (same != null && category == null) {
            change(deckId, listOf(same), listOf(same.copy(quantity = same.quantity + quantity)))
            return AddResult(dao.allLines(deckId).firstOrNull { it.lineUid == same.lineUid }?.id ?: 0, AddedTo.DECK)
        }
        val info = db.cardInfoDao().getMany(listOf(card.scryfallId)).firstOrNull()?.takeIf { it.typeLine.isNotEmpty() }
            ?: runCatching { scryfall.collection(listOf(ScryfallApi.idIdentifier(card.scryfallId))).firstOrNull()?.toInfo() }.getOrNull()
                ?.also { db.cardInfoDao().putAll(listOf(it)) }
        val types = info?.typeLine?.substringBefore(" — ") ?: ""
        val to = category ?: (if (settings.deckAutoCategories.value) autoCategories(listOf(card.scryfallId))[card.scryfallId] else null) ?: fallbackCategory(types)
        val deck = dao.get(deckId)
        val line = DeckCard(
            deckId = deckId,
            card = card,
            quantity = quantity,
            category = to,
            types = types,
            cmc = info?.cmc ?: 0.0,
            commander = to.equals(DeckEdits.COMMANDER, true),
            foil = f.foil,
            etched = f.etched,
            maybeboard = !DeckEdits.included(to, DeckEdits.categories(deck?.categoryInfo)),
            archidektCardId = autoCache[card.scryfallId]?.cardId,
        )
        change(deckId, emptyList(), listOf(line))
        return AddResult(dao.allLines(deckId).firstOrNull { it.lineUid == line.lineUid }?.id ?: 0, AddedTo.DECK)
    }

    /** Scanned cards into an editable deck (each in its auto category); unless [keep], they leave the scan list. */
    suspend fun addScans(scanIds: List<Long>, deckId: Long, keep: Boolean): UndoAction {
        val scans = repo.scansById(scanIds)
        val before = dao.allLines(deckId).associateBy { it.lineUid }
        for (s in scans) addCard(deckId, s.card, Finish.of(s.foil, s.etched), s.quantity)
        if (!keep) repo.deleteScans(scanIds)
        val changed = dao.allLines(deckId).filter { before[it.lineUid] != it.copy(id = before[it.lineUid]?.id ?: it.id) }
        return {
            change(deckId, changed, changed.mapNotNull { before[it.lineUid] })
            if (!keep) repo.restoreScans(scans)
        }
    }

    /** Undoes [addCard] (one copy). */
    suspend fun undoAdd(lineId: Long) {
        val line = dao.card(lineId) ?: return
        if (line.quantity > 1) change(line.deckId, listOf(line), listOf(line.copy(quantity = line.quantity - 1)))
        else change(line.deckId, listOf(line), emptyList())
    }

    /** Archidekt's auto category of each printing (by Scryfall id), where it has one; looked up once per card. */
    suspend fun autoCategories(scryfallIds: Collection<String>): Map<String, String> {
        val missing = scryfallIds.filter { it !in autoCache }.distinct()
        if (missing.isNotEmpty()) runCatching { archidekt.publicClient.printings(missing) }.getOrNull()?.let { autoCache.putAll(it) }
        return scryfallIds.mapNotNull { id -> autoCache[id]?.defaultCategory?.let { id to it } }.toMap()
    }

    // ---- Sending to Archidekt ----------------------------------------------------------------

    /** Sends the waiting edits in a moment (several quick edits go together). */
    fun sendSoon(wait: Long = 1_500) {
        if (archidekt.username == null) return
        sendJob?.cancel()
        sendJob = scope.launch {
            delay(wait)
            for (deckId in edits.deckIds()) {
                val r = send(deckId)
                if (r.skipped.isNotEmpty()) _report.value = r
            }
        }
    }

    /**
     * Sends a deck's waiting edits to Archidekt, oldest first, on top of Archidekt's current list. Network trouble
     * keeps them for later; a change Archidekt refuses is dropped and reported.
     */
    suspend fun send(deckId: Long): DeckSendReport = sendLock.withLock {
        val deck = dao.get(deckId)
        val waiting = edits.forDeck(deckId)
        if (deck == null || deck.local) {
            edits.clear(deckId)
            return DeckSendReport(deckId, 0, emptyList())
        }
        if (waiting.isEmpty()) return DeckSendReport(deckId, 0, emptyList())
        _sending.value = true
        try {
            val lines = dao.allLines(deckId).associateBy { it.lineUid }
            // Cards new to Archidekt need Archidekt's id for their printing.
            val unknown = waiting.mapNotNull { lines[it.lineUid] }.filter { it.archidektCardId == null }.map { it.card.scryfallId }
            if (unknown.isNotEmpty()) {
                val found = archidekt.publicClient.printings(unknown)
                autoCache.putAll(found)
                for (l in lines.values) if (l.archidektCardId == null) found[l.card.scryfallId]?.let { dao.setArchidektCardId(deckId, l.lineUid, it.cardId) }
            }
            val fresh = dao.allLines(deckId).associateBy { it.lineUid }
            var sent = 0
            val skipped = mutableListOf<String>()
            for (e in waiting) {
                if (e.lineUid.startsWith(DeckEdits.CATEGORY_PREFIX)) {
                    try {
                        sendCategory(deckId, e)
                        sent++
                    } catch (ex: IOException) {
                        if (ex.message?.startsWith("Archidekt answered 4") == true) skipped += "the category ${e.name}" else throw ex
                    }
                    edits.delete(e.id)
                    continue
                }
                val line = fresh[e.lineUid]
                val item = DeckEditItems.item(e, line)
                if (item == null) {
                    if (line != null && line.archidektCardId == null && !line.addedInApp) skipped += "${e.name} (Archidekt doesn't know this printing)"
                    edits.delete(e.id)
                    continue
                }
                try {
                    val answer = archidekt.withLogin { client, login -> client.modifyDeckCards(login, deckId, buildJsonArray { add(item) }) }
                    DeckEditItems.addedRelation(answer, e.lineUid)?.let { rel -> dao.setArchidektIds(deckId, e.lineUid, rel, line?.archidektCardId) }
                    sent++
                    edits.delete(e.id)
                } catch (ex: ArchidektAuthException) {
                    throw ex
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: IOException) {
                    // Archidekt refused this change (4xx): skip it; anything else (no connection, Archidekt down): try later.
                    if (ex.message?.startsWith("Archidekt answered 4") == true) {
                        skipped += e.name
                        edits.delete(e.id)
                    } else throw ex
                }
            }
            DeckSendReport(deckId, sent, skipped)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DeckSendReport(deckId, 0, emptyList(), e.message ?: "Archidekt couldn't be reached").also { _report.value = it }
        } finally {
            _sending.value = false
        }
    }

    /** Makes, renames or deletes a category on Archidekt (since 1.47); a new one's id is kept with the deck's categories. */
    private suspend fun sendCategory(deckId: Long, e: DeckEdit) {
        val op = e.categories.orEmpty()
        val id = e.relationId
        when {
            op == DeckEdits.OP_CREATE -> {
                val info = DeckEdits.categories(dao.get(deckId)?.categoryInfo)
                val cat = info.firstOrNull { it.name.equals(e.name, true) } ?: return
                if (cat.id != null) return
                val made = archidekt.withLogin { client, login -> client.createCategory(login, deckId, cat.name, cat.included) }
                if (made != null) dao.get(deckId)?.let { d ->
                    val now = DeckEdits.categories(d.categoryInfo).map { if (it.name.equals(cat.name, true) && it.id == null) it.copy(id = made) else it }
                    dao.update(d.copy(categoryInfo = DeckEdits.encode(now)))
                }
            }
            op == DeckEdits.OP_DELETE && id != null -> archidekt.withLogin { client, login -> Unit to client.deleteCategory(login, id) }
            op.startsWith(DeckEdits.OP_RENAME) && id != null -> {
                val to = op.removePrefix(DeckEdits.OP_RENAME)
                archidekt.withLogin { client, login -> Unit to client.renameCategory(login, id, to) }
            }
        }
    }

    /** Sends every deck's waiting edits now ("Send waiting edits now"). */
    suspend fun sendAll(): List<DeckSendReport> = edits.deckIds().map { send(it) }

    suspend fun waitingCount(deckId: Long) = edits.count(deckId)

    // ---- Local decks ---------------------------------------------------------------------------

    private val _deleted = MutableStateFlow<Pair<String, UndoAction>?>(null)
    /** A local deck just deleted, with its undo, for the Decks tab to offer. */
    val deleted: StateFlow<Pair<String, UndoAction>?> = _deleted

    fun consumeDeleted() {
        _deleted.value = null
    }

    /** Deletes a local deck for good, keeping an undo for the Decks tab. */
    suspend fun deleteLocal(deckId: Long) {
        val deck = dao.get(deckId) ?: return
        val lines = dao.allLines(deckId)
        dao.delete(deckId)
        edits.clear(deckId)
        _deleted.value = deck.name to {
            db.withTransaction {
                dao.upsert(deck)
                dao.insertCards(lines.map { it.copy(id = 0) })
            }
        }
    }

    suspend fun renameLocal(deckId: Long, name: String) {
        dao.get(deckId)?.takeIf { it.local }?.let { dao.update(it.copy(name = name)) }
    }

    /** Saves a new local deck with [lines]; returns its id. */
    private suspend fun createLocal(name: String, lines: List<DeckCard>, origin: String?, commanderIdentity: String? = null): Long {
        val id = DeckEdits.newLocalId()
        val commanders = lines.filter { it.commander }
        val deck = Deck(
            archidektId = id,
            name = name.ifBlank { "New deck" },
            owner = "",
            commanders = commanders.map { it.card.name }.distinct().joinToString(" & "),
            commanderScryfallId = commanders.firstOrNull()?.card?.scryfallId,
            artUrl = null,
            colorIdentity = commanderIdentity ?: identityOf(commanders).orEmpty(),
            cardCount = lines.filter { !it.maybeboard }.sumOf { it.quantity },
            origin = origin,
            archidektUpdatedAt = null,
        )
        db.withTransaction {
            dao.upsert(deck)
            dao.insertCards(lines.map { it.copy(id = 0, deckId = id, relationId = null, lineUid = UUID.randomUUID().toString()) })
        }
        return id
    }

    suspend fun newEmpty(name: String): Long = createLocal(name, emptyList(), "Made in the app")

    /** A local copy of another deck (its cards, categories and maybeboard; not its scores). */
    suspend fun copyOf(deckId: Long, name: String): Long {
        val src = dao.get(deckId) ?: throw IllegalStateException("The deck is gone")
        val id = createLocal(name, dao.allLines(deckId), "Copy of ${src.name}", src.colorIdentity)
        dao.get(id)?.let { dao.update(it.copy(artUrl = src.artUrl, categoryInfo = src.categoryInfo)) }
        return id
    }

    /** Turns a deck from Archidekt into a local deck: editable, no longer linked or reloaded. Returns its new id. */
    suspend fun detach(deckId: Long): Long {
        val src = dao.get(deckId) ?: throw IllegalStateException("The deck is gone")
        if (src.local) return deckId
        val id = DeckEdits.newLocalId()
        db.withTransaction {
            dao.upsert(
                src.copy(
                    archidektId = id,
                    owner = "",
                    origin = "Detached from Archidekt (${src.owner.ifBlank { "deck ${src.archidektId}" }})",
                    archidektUpdatedAt = null,
                    folderId = null,
                    folderName = null,
                    visibility = null,
                )
            )
            val lines = dao.allLines(deckId)
            dao.insertCards(lines.map { it.copy(id = 0, deckId = id, relationId = null, addedInApp = false, lineUid = it.lineUid.ifEmpty { UUID.randomUUID().toString() }) })
            dao.delete(deckId)
            edits.clear(deckId)
            settings.moveDeckHidden(deckId, id)
        }
        return id
    }

    /** A local deck from a preconstructed Commander deck (MTGJSON's list), with Archidekt's auto categories. */
    suspend fun fromPrecon(info: Precons.Info): Long {
        val deck = precons.deck(info)
        val commanders = deck.commander.map { it.name }.toSet()
        // The whole deck this time, basic lands too (the sideboard holds extras, not deck cards).
        val cards = (deck.commander + deck.mainBoard).filter { it.identifiers.scryfallId != null }
        val found = scryfall.collection(cards.mapNotNull { it.identifiers.scryfallId }.distinct().map(ScryfallApi::idIdentifier)).associateBy { it.id }
        val auto = if (settings.deckAutoCategories.value) autoCategories(found.keys) else emptyMap()
        val lines = cards.groupBy { (it.identifiers.scryfallId ?: it.name) to it.isFoil }.mapNotNull { (_, same) ->
            val c = same.first()
            val sc = c.identifiers.scryfallId?.let { found[it] } ?: return@mapNotNull null
            val commander = c.name in commanders
            val category = if (commander) DeckEdits.COMMANDER else auto[sc.id] ?: fallbackCategory(sc.typeLine.substringBefore(" — "))
            lineFor(sc, same.sumOf { it.count }, c.isFoil, false, category, commander)
        }
        return createLocal(deck.name, lines, "Precon: ${deck.name}" + (deck.releaseDate?.take(4)?.let { " ($it)" } ?: ""), identityFromCards(found.values, commanders))
    }

    /** A local deck from scanned cards; unless [keep], the scans leave the scan list. */
    suspend fun fromScans(scanIds: List<Long>, name: String, keep: Boolean): Long {
        val scans = repo.scansById(scanIds)
        val found = runCatching { scryfall.collection(scans.map { it.card.scryfallId }.distinct().map(ScryfallApi::idIdentifier)).associateBy { it.id } }.getOrDefault(emptyMap())
        val auto = if (settings.deckAutoCategories.value) autoCategories(found.keys) else emptyMap()
        val lines = scans.groupBy { Triple(it.card.scryfallId, it.foil, it.etched) }.map { (k, same) ->
            val sc = found[k.first]
            val types = sc?.typeLine?.substringBefore(" — ") ?: ""
            DeckCard(
                deckId = 0, card = same.first().card, quantity = same.sumOf { it.quantity },
                category = auto[k.first] ?: fallbackCategory(types), types = types, cmc = sc?.cmc ?: 0.0,
                commander = false, foil = k.second, etched = k.third,
            )
        }
        val id = createLocal(name, lines, "From ${scans.sumOf { it.quantity }} scanned cards")
        if (!keep) repo.deleteScans(scanIds)
        return id
    }

    /** What a pasted decklist holds, looked up on Scryfall, before the deck is made (see [fromText]). */
    data class TextPreview(
        val result: DeckText.Result,
        val found: Map<DeckText.Line, ScryCard>,
        val exact: Int,
        val byName: Int,
        val notFound: List<String>,
    ) {
        val commanders get() = result.lines.filter { it.board == DeckText.Board.COMMANDER }.mapNotNull { found[it]?.name }
    }

    suspend fun previewText(text: String): TextPreview {
        val result = DeckText.parse(text)
        fun ident(l: DeckText.Line) = if (l.setCode != null && l.number != null) ScryfallApi.setNumberIdentifier(l.setCode, l.number) else ScryfallApi.nameIdentifier(l.name)
        val cards = scryfall.collection(result.lines.map(::ident).distinct())
        val bySetNumber = cards.associateBy { it.set.lowercase() to it.collectorNumber.lowercase() }
        val byName = cards.groupBy { it.name.lowercase() }
        fun nameMatch(name: String): ScryCard? = byName[name.lowercase()]?.firstOrNull()
            ?: cards.firstOrNull { c -> c.name.lowercase().substringBefore(" // ") == name.lowercase().substringBefore(" // ") }
        val found = LinkedHashMap<DeckText.Line, ScryCard>()
        var exact = 0
        var named = 0
        val notFound = mutableListOf<String>()
        for (l in result.lines) {
            val bySet = if (l.setCode != null && l.number != null) bySetNumber[l.setCode to l.number.lowercase()] else null
            val c = bySet ?: nameMatch(l.name)
            if (c == null) notFound += l.name
            else {
                found[l] = c
                if (bySet != null) exact++ else named++
            }
        }
        return TextPreview(result, found, exact, named, notFound)
    }

    /** A local deck from a pasted decklist: categories from its tags ([useTags]) or Archidekt's auto categories. */
    suspend fun fromText(name: String, preview: TextPreview, useTags: Boolean, commanderOverride: String? = null): Long {
        val auto = if (!useTags || !preview.result.hasTags) autoCategories(preview.found.values.map { it.id }) else emptyMap()
        val commanderNames = commanderOverride?.let { setOf(it) } ?: preview.commanders.toSet()
        val lines = preview.result.lines.mapNotNull { l ->
            val sc = preview.found[l] ?: return@mapNotNull null
            val commander = sc.name in commanderNames
            val category = when {
                commander -> DeckEdits.COMMANDER
                l.board == DeckText.Board.MAYBE -> DeckEdits.MAYBEBOARD
                useTags && l.tags.isNotEmpty() -> l.tags.first()
                else -> auto[sc.id] ?: fallbackCategory(sc.typeLine.substringBefore(" — "))
            }
            lineFor(sc, l.quantity, l.foil, l.etched, category, commander, extra = if (useTags) l.tags.drop(1) else emptyList())
        }
        return createLocal(name, lines, "Pasted decklist", identityFromCards(preview.found.values, commanderNames))
    }

    private fun identityFromCards(cards: Collection<ScryCard>, commanders: Set<String>): String {
        val colours = cards.filter { it.name in commanders }.flatMap { it.colorIdentity }.toSet()
        return MtgColors.ORDER.filter { it.toString() in colours }
    }

    private fun lineFor(sc: ScryCard, quantity: Int, foil: Boolean, etched: Boolean, category: String, commander: Boolean, extra: List<String> = emptyList()): DeckCard {
        val types = sc.typeLine.substringBefore(" — ")
        return DeckCard(
            deckId = 0,
            card = sc.toRef(),
            quantity = quantity,
            category = category,
            types = types,
            cmc = sc.cmc,
            commander = commander,
            foil = foil,
            etched = etched,
            maybeboard = category.equals(DeckEdits.MAYBEBOARD, true),
            categories = (listOf(category) + extra.filterNot { it.equals(category, true) }).joinToString("\n"),
            archidektCardId = autoCache[sc.id]?.cardId,
        )
    }

    // ---- Local decks to Archidekt ----------------------------------------------------------------

    /**
     * Sends a local deck to Archidekt as a new deck of the logged-in user, in [folderId] and with [visibility].
     * The deck then lives on Archidekt (editable, in the "Archidekt" section) and is scored by Commander Salt
     * unless private. Returns its Archidekt id.
     */
    suspend fun sendAsNew(deckId: Long, name: String, folderId: Long?, folderName: String?, visibility: String): Long {
        val local = dao.get(deckId) ?: throw IllegalStateException("The deck is gone")
        val user = archidekt.username ?: throw ArchidektAuthException("Log in to Archidekt first (Settings → Archidekt).")
        val newId = archidekt.withLogin { client, login -> client.createDeck(login, name, folderId, visibility) }
        val lines = dao.allLines(deckId)
        db.withTransaction {
            dao.upsert(
                local.copy(
                    archidektId = newId, name = name, owner = user, origin = null, folderId = folderId, folderName = folderName,
                    visibility = visibility, importedAt = System.currentTimeMillis(), archidektUpdatedAt = null,
                    saltId = null, scoredAt = null, scoreError = null,
                )
            )
            dao.insertCards(lines.map { it.copy(id = 0, deckId = newId, relationId = null, addedInApp = false) })
            dao.delete(deckId)
            edits.add(lines.map { DeckEdit(deckId = newId, lineUid = it.lineUid, relationId = null, archidektCardId = it.archidektCardId, name = it.card.name) })
            // Categories without cards are made on Archidekt too (since 1.47); the others come with their cards.
            val used = lines.map { it.category.lowercase() }.toSet()
            val empty = DeckEdits.categories(local.categoryInfo).filter { it.name.lowercase() !in used }
            edits.add(empty.map { DeckEdit(deckId = newId, lineUid = DeckEdits.CATEGORY_PREFIX + UUID.randomUUID(), relationId = null, archidektCardId = null, name = it.name, categories = DeckEdits.OP_CREATE) })
            dao.get(newId)?.let { d -> dao.update(d.copy(categoryInfo = DeckEdits.encode(DeckEdits.categories(d.categoryInfo).map { it.copy(id = null) }))) }
        }
        settings.moveDeckHidden(deckId, newId)
        val r = send(newId)
        if (r.error != null) throw IOException(r.error)
        if (visibility != ArchidektCollectionClient.VISIBILITY_PRIVATE) decks.rescore(newId)
        return newId
    }

    /**
     * Sends a local deck into one of the user's Archidekt decks: adding its cards (a card in both keeps the higher
     * quantity) or replacing that deck's list. The local deck is then gone and the Archidekt deck holds the result.
     */
    suspend fun sendInto(deckId: Long, targetId: Long, replace: Boolean): Long {
        val local = dao.get(deckId) ?: throw IllegalStateException("The deck is gone")
        if (dao.get(targetId) == null) decks.importQuietly(targetId) else decks.reloadList(targetId)
        val target = dao.get(targetId) ?: throw IOException("Couldn't load that deck from Archidekt")
        val ours = dao.allLines(deckId)
        val theirs = dao.allLines(targetId)
        val before = mutableListOf<DeckCard>()
        val after = mutableListOf<DeckCard>()
        if (replace) {
            before += theirs
            after += ours.map { it.copy(id = 0, deckId = targetId, relationId = null, lineUid = UUID.randomUUID().toString()) }
        } else {
            for (l in ours) {
                val same = theirs.firstOrNull { it.card.scryfallId == l.card.scryfallId && it.foil == l.foil && it.etched == l.etched && it.maybeboard == l.maybeboard }
                if (same != null) {
                    if (l.quantity > same.quantity) {
                        before += same
                        after += same.copy(quantity = l.quantity)
                    }
                } else after += l.copy(id = 0, deckId = targetId, relationId = null, lineUid = UUID.randomUUID().toString())
            }
        }
        change(targetId, before, after)
        dao.delete(deckId)
        val r = send(targetId)
        if (r.error != null) throw IOException(r.error)
        if (target.visibility != ArchidektCollectionClient.VISIBILITY_PRIVATE) decks.rescore(targetId)
        return targetId
    }

    // ---- Folders and visibility ----------------------------------------------------------------

    /** The user's Archidekt folders as "Home › 02. Brewing" paths, found from their decks' folders. */
    suspend fun folders(): List<Pair<Long, String>> {
        val user = archidekt.username ?: return emptyList()
        val start = dao.all().firstOrNull { it.owner.equals(user, true) && it.folderId != null }?.folderId
            ?: decks.userDeckFolder(user) ?: return emptyList()
        // Up to the user's top folder ("Home")…
        var top = archidekt.withLogin { c, l -> c.folder(l, start) }
        var guard = 0
        while (top.parentId != null && guard++ < 6) top = archidekt.withLogin { c, l -> c.folder(l, top.parentId!!) }
        // …then down through the subfolders, three levels deep.
        val out = mutableListOf(top.id to top.name)
        var level = listOf(top.id to top.name)
        repeat(3) {
            val next = mutableListOf<Pair<Long, String>>()
            for ((id, path) in level) {
                val f = if (id == top.id) top else runCatching { archidekt.withLogin { c, l -> c.folder(l, id) } }.getOrNull() ?: continue
                for ((sid, sname) in f.subfolders.sortedBy { it.second.lowercase() }) next += sid to "$path › $sname"
            }
            out += next
            level = next
            if (out.size > 60) return out
        }
        return out
    }

    suspend fun moveToFolder(deckId: Long, folderId: Long, path: String) {
        archidekt.withLogin { c, l -> Unit to c.moveDeck(l, deckId, folderId) }
        dao.get(deckId)?.let { dao.update(it.copy(folderId = folderId, folderName = path.substringAfterLast(" › "))) }
    }

    suspend fun setVisibility(deckId: Long, visibility: String) {
        archidekt.withLogin { c, l -> Unit to c.setDeckVisibility(l, deckId, visibility) }
        dao.get(deckId)?.let { dao.update(it.copy(visibility = visibility)) }
    }

    companion object {
        /** Stands for "each card's Archidekt auto category" when moving cards. */
        const val AUTO = "\u0000auto"

        /** The category a card without an auto category goes into: its card type, as Archidekt does. */
        fun fallbackCategory(types: String) = DeckGrouping.primaryType(types).let { if (it == "Other") "Other" else it }
    }
}

/** Archidekt's modifyCards/v2 items for waiting edits, as its own deck editor builds them. Since 1.46. */
object DeckEditItems {
    private const val DEFAULT_LABEL = ",#656565"

    /**
     * The item for an edit: "remove" when the line is gone (and Archidekt has it), "modify" when Archidekt has the
     * line, else "add". Null when there's nothing to send (a line never sent and gone again, a card "Added in app",
     * or a printing Archidekt doesn't know).
     */
    fun item(edit: DeckEdit, line: DeckCard?): JsonObject? {
        if (line == null) {
            val rel = edit.relationId ?: return null
            return build("remove", edit.archidektCardId, edit.categories?.split('\n')?.filter { it.isNotBlank() }.orEmpty(), edit.lineUid, 1, "Normal", DEFAULT_LABEL, rel)
        }
        if (line.addedInApp) return null
        val cardId = line.archidektCardId ?: return null
        val modifier = when {
            line.etched -> "Etched"
            line.foil -> "Foil"
            else -> "Normal"
        }
        val action = if (line.relationId != null) "modify" else "add"
        return build(action, cardId, line.categoryList, line.lineUid, line.quantity, modifier, line.label ?: DEFAULT_LABEL, line.relationId)
    }

    private fun build(action: String, cardId: Long?, categories: List<String>, patchId: String, quantity: Int, modifier: String, label: String, relationId: Long?) =
        buildJsonObject {
            put("action", action)
            put("cardid", cardId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("customCardId", JsonNull)
            put("categories", buildJsonArray { categories.forEach { add(JsonPrimitive(it)) } })
            put("patchId", patchId)
            put("modifications", buildJsonObject {
                put("quantity", quantity)
                put("modifier", modifier)
                put("customCmc", JsonNull)
                put("companion", false)
                put("flippedDefault", false)
                put("label", label)
            })
            if (relationId != null) put("deckRelationId", relationId)
        }

    /** The id Archidekt gave a line it added, from its answer ("add": [{patchId, deckRelationId}]). */
    fun addedRelation(answer: JsonObject, patchId: String): Long? =
        (answer["add"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { (it["patchId"] as? JsonPrimitive)?.contentOrNull == patchId }
            ?.get("deckRelationId")?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
}
