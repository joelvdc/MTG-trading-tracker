package com.mtgtrader.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Editing local decks (made in the app, or a copy of a deck from Archidekt). An edit shows at once and returns an
 * undo. Until 1.78 the logged-in Archidekt user's own decks could be edited too, the changes waiting in [DeckEdit]s
 * until sent to Archidekt; decks from Archidekt are read-only since, and edits from before that never reached
 * Archidekt are only counted ([waiting]), so the deck page can offer to keep them in a local copy or discard them.
 */
class DeckEditor(
    private val db: AppDatabase,
    private val scryfall: ScryfallApi,
    /** The website's table of auto categories (by card name). */
    private val web: WebAccountClient,
    private val decks: DeckRepository,
    private val settings: Settings,
    private val precons: PreconStore,
    private val repo: MtgRepository,
) {
    private val dao = db.deckDao()
    private val edits = db.deckEditDao()
    private val autoCache = HashMap<String, List<String>>()

    /** Changes from before 1.78 that never reached Archidekt, per deck. */
    val waiting: Flow<Map<Long, Int>> = edits.observeCounts().map { l -> l.associate { it.deckId to it.count } }

    /** The Archidekt user whose decks count as "own": your Archidekt username, else the one decks were last imported from. */
    fun owner(): String? = settings.deckOwner()

    /** Whether the deck can be edited here: only local decks since 1.78 (decks from Archidekt are edited there). */
    fun canEdit(deck: Deck): Boolean = deck.local

    /**
     * Whether a deck from Archidekt is ready to be edited: loaded by 1.46 or later, so every line knows its id on
     * Archidekt (else a change would be sent as a new card). Such a load keeps the deck's categories, which older
     * versions didn't; [prepare] reloads the list when they're missing.
     */
    fun ready(deck: Deck): Boolean = deck.local || deck.categoryInfo != null

    // ---- Getting a deck ready -----------------------------------------------------------------

    /**
     * Before the first edit: gives lines from before 1.46 an id of their own. Returns why it couldn't, if so (never,
     * since decks from Archidekt aren't edited here any more).
     */
    suspend fun prepare(deckId: Long): String? {
        val deck = dao.get(deckId) ?: return "The deck is gone"
        val lines = dao.allLines(deckId)
        val nameless = lines.filter { it.lineUid.isEmpty() }
        if (nameless.isNotEmpty()) db.withTransaction { nameless.forEach { dao.setLineUid(it.id, UUID.randomUUID().toString()) } }
        return null
    }

    // ---- Edits -----------------------------------------------------------------------------------

    /**
     * Replaces the deck's lines [before] (by their lineUid) with [after]: lines only in [before] are removed, lines only
     * in [after] are added. Returns the undo.
     */
    private suspend fun change(deckId: Long, before: List<DeckCard>, after: List<DeckCard>): UndoAction {
        val deck = dao.get(deckId) ?: return {}
        val uids = (before.map { it.lineUid } + after.map { it.lineUid }).distinct()
        db.withTransaction {
            dao.deleteLines(deckId, uids)
            dao.insertCards(after.map { it.copy(id = 0, deckId = deckId) })
        }
        refreshSummary(deckId)
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
     * Moves lines to [category]: [AUTO] for each card's auto categories from the website, [DeckEdits.MAYBEBOARD] (or another
     * category left out of the deck) to the maybeboard, [DeckEdits.COMMANDER] to make them the commander.
     */
    suspend fun move(deckId: Long, uids: Collection<String>, category: String): UndoAction {
        val before = lines(deckId, uids)
        val deck = dao.get(deckId) ?: return {}
        val info = DeckEdits.categories(deck.categoryInfo)
        val auto = if (category == AUTO) autoCategories(before.associate { it.card.scryfallId to it.card.name }) else emptyMap()
        val after = before.map { line ->
            val cats = auto[line.card.scryfallId]
            val to = if (category == AUTO) cats?.firstOrNull() ?: fallbackCategory(line.types) else category
            val moved = line.inCategory(to, maybe = !DeckEdits.included(to, info))
            (if (category == AUTO && cats != null) moved.copy(categories = cats.joinToString("\n")) else moved).copy(
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
        move(deckId, dao.cardsAddedInApp(deckId).filter { !it.maybeboard }.map { it.lineUid }, DeckEdits.MAYBEBOARD)

    /**
     * Renames a category: its cards move to [to]. On Archidekt the category itself is renamed first (since 1.47), so
     * it keeps its place there instead of an emptied old one staying behind.
     */
    suspend fun renameCategory(deckId: Long, from: String, to: String): UndoAction {
        val before = dao.allLines(deckId).filter { it.category.equals(from, true) }
        val deck = dao.get(deckId) ?: return {}
        val old = DeckEdits.categories(deck.categoryInfo).firstOrNull { it.name.equals(from, true) }
        renameInfo(deck, from, to)
        settings.renameDeckHidden(deckId, from, to)
        val info = DeckEdits.categories(dao.get(deckId)?.categoryInfo)
        val undo = change(deckId, before, before.map { it.inCategory(to, maybe = !DeckEdits.included(to, info)) })
        return {
            undo()
            dao.get(deckId)?.let { d ->
                val now = DeckEdits.categories(d.categoryInfo)
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
        val wasHidden = settings.isDeckHidden(deckId, name)
        settings.setDeckHidden(deckId, name, false)
        return {
            undo()
            dao.get(deckId)?.let { d ->
                val now = DeckEdits.categories(d.categoryInfo)
                val back = old ?: ArchidektCategory(name)
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
    }

    private suspend fun renameInfo(deck: Deck, from: String, to: String) {
        val info = DeckEdits.categories(deck.categoryInfo)
        val old = info.firstOrNull { it.name.equals(from, true) }
        val kept = info.filterNot { it.name.equals(from, true) || it.name.equals(to, true) }
        dao.update(deck.copy(categoryInfo = DeckEdits.encode(kept + ArchidektCategory(to, old?.included ?: true, old?.id))))
    }

    /** Every card of the deck (not the commander or the maybeboard) into its auto categories. */
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
        val cats = if (category == null && settings.deckAutoCategories.value) autoCategories(mapOf(card.scryfallId to card.name))[card.scryfallId] else null
        val to = category ?: cats?.firstOrNull() ?: fallbackCategory(types)
        val deck = dao.get(deckId)
        val line = DeckCard(
            deckId = deckId,
            card = card,
            quantity = quantity,
            category = to,
            categories = (cats ?: listOf(to)).joinToString("\n"),
            types = types,
            cmc = info?.cmc ?: 0.0,
            commander = to.equals(DeckEdits.COMMANDER, true),
            foil = f.foil,
            etched = f.etched,
            maybeboard = !DeckEdits.included(to, DeckEdits.categories(deck?.categoryInfo)),
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

    /**
     * The website's auto categories of each card ([cards]: Scryfall id to card name), where it has any: the first is
     * the card's category, the others are extra categories. Looked up once per card name; empty when the website
     * can't be reached (cards then go by card type).
     */
    suspend fun autoCategories(cards: Map<String, String>): Map<String, List<String>> {
        val missing = cards.values.filter { it !in autoCache }.distinct()
        if (missing.isNotEmpty()) runCatching { web.autoCategories(missing) }.getOrNull()?.let { found ->
            // Names the website doesn't know are remembered too, so they aren't asked again.
            missing.forEach { autoCache[it] = found[it].orEmpty() }
        }
        return cards.mapNotNull { (id, name) -> autoCache[name]?.takeIf { it.isNotEmpty() }?.let { id to it } }.toMap()
    }

    // ---- Changes that never reached Archidekt (from before 1.78) --------------------------------

    suspend fun waitingCount(deckId: Long) = edits.count(deckId)

    /** Forgets a deck's changes that never reached Archidekt (before reloading it from there). */
    suspend fun discardWaiting(deckId: Long) = edits.clear(deckId)

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

    /** A local deck from a preconstructed Commander deck (MTGJSON's list), with the website's auto categories. */
    suspend fun fromPrecon(info: Precons.Info): Long {
        val deck = precons.deck(info)
        val commanders = deck.commander.map { it.name }.toSet()
        // The whole deck this time, basic lands too (the sideboard holds extras, not deck cards).
        val cards = (deck.commander + deck.mainBoard).filter { it.identifiers.scryfallId != null }
        val found = scryfall.collection(cards.mapNotNull { it.identifiers.scryfallId }.distinct().map(ScryfallApi::idIdentifier)).associateBy { it.id }
        val auto = if (settings.deckAutoCategories.value) autoCategories(found.mapValues { it.value.name }) else emptyMap()
        val lines = cards.groupBy { (it.identifiers.scryfallId ?: it.name) to it.isFoil }.mapNotNull { (_, same) ->
            val c = same.first()
            val sc = c.identifiers.scryfallId?.let { found[it] } ?: return@mapNotNull null
            val commander = c.name in commanders
            val cats = if (commander) emptyList() else auto[sc.id].orEmpty()
            val category = if (commander) DeckEdits.COMMANDER else cats.firstOrNull() ?: fallbackCategory(sc.typeLine.substringBefore(" — "))
            lineFor(sc, same.sumOf { it.count }, c.isFoil, false, category, commander, extra = cats.drop(1))
        }
        return createLocal(deck.name, lines, "Precon: ${deck.name}" + (deck.releaseDate?.take(4)?.let { " ($it)" } ?: ""), identityFromCards(found.values, commanders))
    }

    /** A local deck from scanned cards; unless [keep], the scans leave the scan list. */
    suspend fun fromScans(scanIds: List<Long>, name: String, keep: Boolean): Long {
        val scans = repo.scansById(scanIds)
        val found = runCatching { scryfall.collection(scans.map { it.card.scryfallId }.distinct().map(ScryfallApi::idIdentifier)).associateBy { it.id } }.getOrDefault(emptyMap())
        val auto = if (settings.deckAutoCategories.value) autoCategories(found.mapValues { it.value.name }) else emptyMap()
        val lines = scans.groupBy { Triple(it.card.scryfallId, it.foil, it.etched) }.map { (k, same) ->
            val sc = found[k.first]
            val types = sc?.typeLine?.substringBefore(" — ") ?: ""
            val cats = auto[k.first] ?: listOf(fallbackCategory(types))
            DeckCard(
                deckId = 0, card = same.first().card, quantity = same.sumOf { it.quantity },
                category = cats.first(), categories = cats.joinToString("\n"), types = types, cmc = sc?.cmc ?: 0.0,
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

    /** A local deck from a pasted decklist: categories from its tags ([useTags]) or the website's auto categories. */
    suspend fun fromText(name: String, preview: TextPreview, useTags: Boolean, commanderOverride: String? = null): Long {
        val auto = if (!useTags || !preview.result.hasTags) autoCategories(preview.found.values.associate { it.id to it.name }) else emptyMap()
        val commanderNames = commanderOverride?.let { setOf(it) } ?: preview.commanders.toSet()
        val lines = preview.result.lines.mapNotNull { l ->
            val sc = preview.found[l] ?: return@mapNotNull null
            val commander = sc.name in commanderNames
            val category = when {
                commander -> DeckEdits.COMMANDER
                l.board == DeckText.Board.MAYBE -> DeckEdits.MAYBEBOARD
                useTags && l.tags.isNotEmpty() -> l.tags.first()
                else -> auto[sc.id]?.firstOrNull() ?: fallbackCategory(sc.typeLine.substringBefore(" — "))
            }
            val extra = if (useTags && l.tags.isNotEmpty()) l.tags.drop(1) else if (!commander && l.board != DeckText.Board.MAYBE) auto[sc.id].orEmpty().drop(1) else emptyList()
            lineFor(sc, l.quantity, l.foil, l.etched, category, commander, extra = extra)
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
        )
    }

    companion object {
        /** Stands for "each card's auto categories from the website" when moving cards. */
        const val AUTO = "\u0000auto"

        /** The category a card without an auto category goes into: its card type, as Archidekt does. */
        fun fallbackCategory(types: String) = DeckGrouping.primaryType(types).let { if (it == "Other") "Other" else it }
    }
}
