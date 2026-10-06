package com.mtgtrader.data

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgtrader.container
import com.mtgtrader.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Where cards that arrive from Archidekt go in the app. */
enum class PullTo(val label: String) {
    UNSORTED("Unsorted"),
    FROM_ARCHIDEKT("A “From Archidekt” binder"),
}

/** Which binders become labels on Archidekt. */
enum class TagMode(val label: String, val help: String) {
    NONE("None", "Archidekt's labels are left as they are."),
    TRADE_BINDER("Trade binder only", "Cards with copies in the trade binder get a “Trade binder” label."),
    ALL("All binders", "Each card gets a label for every binder its copies are in."),
}

/** One line of a sync report. */
@Serializable
data class ReportLine(val kind: Kind, val text: String) {
    enum class Kind { APP_ADDED, APP_REMOVED, APP_PRICE, ARCH_ADDED, ARCH_REMOVED, ARCH_CHANGED, FAILED, CONFLICT, NOTE }
}

@Serializable
data class SyncReport(val at: Long, val summary: String, val lines: List<ReportLine> = emptyList(), val error: String? = null)

data class ArchidektStatus(
    val username: String? = null,
    val running: Boolean = false,
    val progress: String? = null,
    val lastSyncAt: Long = 0,
    val lastError: String? = null,
    /** A sync that waits for the user: the first sync's choice, or big removals to confirm. Nothing was changed yet. */
    val review: Plan? = null,
    /** Cards changed on both sides since the last sync, waiting for a decision (everything else was synced). */
    val conflicts: List<KeyChange> = emptyList(),
    val lastReport: SyncReport? = null,
    val auto: Boolean = false,
    val wifiOnly: Boolean = false,
    val pullTo: PullTo = PullTo.UNSORTED,
    val tagMode: TagMode = TagMode.NONE,
    /** Synced at least once, so there's an agreement to compare against. */
    val ready: Boolean = false,
) {
    val connected get() = username != null
}

/**
 * Keeps the app's collection and the user's Archidekt collection the same, both ways (see
 * [ArchidektPlanner]). With Nextcloud sync on, the agreement lives next to the sync file and each
 * Archidekt sync runs between two Nextcloud syncs, holding a lock, so several phones can take turns
 * syncing with the same Archidekt account. Since 1.21.
 */
class ArchidektSync(
    private val context: Context,
    private val db: AppDatabase,
    private val repo: MtgRepository,
    private val settings: Settings,
    private val nextcloud: SyncManager,
    private val cloud: NextcloudClient,
    private val client: ArchidektCollectionClient,
    private val backups: Backups,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("archidekt", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<ArchidektStatus> = _status

    init {
        client.onTokens = { saveLogin(it) }
    }

    private fun readStatus() = ArchidektStatus(
        username = login()?.username,
        lastSyncAt = prefs.getLong(K_LAST, 0),
        lastError = prefs.getString(K_ERROR, null),
        lastReport = history().firstOrNull(),
        auto = prefs.getBoolean(K_AUTO, false),
        wifiOnly = prefs.getBoolean(K_WIFI, false),
        pullTo = runCatching { PullTo.valueOf(prefs.getString(K_PULL_TO, null) ?: "") }.getOrDefault(PullTo.UNSORTED),
        tagMode = runCatching { TagMode.valueOf(prefs.getString(K_TAGS, null) ?: "") }.getOrDefault(TagMode.NONE),
        ready = prefs.getBoolean(K_READY, false),
    )

    private fun login(): ArchidektLogin? =
        prefs.getString(K_LOGIN, null)?.let(SecretStore::decrypt)?.let { runCatching { json.decodeFromString<ArchidektLogin>(it) }.getOrNull() }

    private fun saveLogin(l: ArchidektLogin) {
        prefs.edit().putString(K_LOGIN, SecretStore.encrypt(json.encodeToString(ArchidektLogin.serializer(), l))).apply()
    }

    /** The address of Archidekt; only debug builds can point it at a test server. */
    var server: String
        get() = prefs.getString(K_SERVER, null) ?: ArchidektCollectionClient.DEFAULT_BASE
        set(v) = prefs.edit().putString(K_SERVER, v.trim().trimEnd('/').ifEmpty { null }).apply()

    // ---- Account and settings --------------------------------------------------------------

    /** Logs in (the password is used once and not kept). */
    suspend fun logIn(user: String, password: String) {
        val l = client.login(user, password)
        val old = login()
        saveLogin(l)
        if (old?.userId != l.userId) prefs.edit().putBoolean(K_READY, false).remove(K_LAST).remove(K_ERROR).apply()
        _status.value = readStatus()
    }

    fun logOut() {
        prefs.edit().remove(K_LOGIN).remove(K_ERROR).apply()
        _status.value = readStatus().copy(review = null, conflicts = emptyList())
    }

    fun setAuto(on: Boolean) = edit { putBoolean(K_AUTO, on) }
    fun setWifiOnly(on: Boolean) = edit { putBoolean(K_WIFI, on) }
    fun setPullTo(v: PullTo) = edit { putString(K_PULL_TO, v.name) }
    fun setTagMode(v: TagMode) = edit { putString(K_TAGS, v.name) }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        val s = _status.value
        _status.value = readStatus().copy(running = s.running, progress = s.progress, review = s.review, conflicts = s.conflicts)
    }

    fun dismissReview() {
        _status.value = _status.value.copy(review = null)
    }

    /** Whether this phone shares the agreement with other phones through Nextcloud. */
    val sharedThroughNextcloud get() = nextcloud.readyAccount() != null

    // ---- Automatic sync --------------------------------------------------------------------

    private fun autoAllowed(): Boolean {
        val s = _status.value
        return s.connected && s.ready && s.auto && s.review == null && (!s.wifiOnly || network.onUnmeteredNetwork())
    }

    /** On opening the app: sync if the last one was a while ago. */
    fun onAppStart() {
        if (autoAllowed() && System.currentTimeMillis() - _status.value.lastSyncAt > AUTO_EVERY_MS) scope.launch { sync() }
    }

    /** On leaving the app: changes to the collection go to Archidekt in the background. */
    fun onAppBackground() {
        if (!autoAllowed()) return
        scope.launch {
            if (db.syncDao().lastChange() == prefs.getLong(K_STAMP, -1)) return@launch
            val request = OneTimeWorkRequestBuilder<ArchidektWorker>().setConstraints(
                Constraints.Builder().setRequiredNetworkType(if (_status.value.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
            ).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }

    suspend fun autoSync() {
        if (autoAllowed()) sync()
    }

    // ---- Syncing ---------------------------------------------------------------------------

    fun syncNow(decisions: Map<CardKey, Decision> = emptyMap(), approved: Pair<Int, Int>? = null) {
        scope.launch { sync(decisions, approved) }
    }

    /**
     * One sync. [decisions] settle conflicts (and the first sync's differences); [approved] lets
     * through removals the user has looked at (up to that many copies from the app and from Archidekt).
     */
    suspend fun sync(decisions: Map<CardKey, Decision> = emptyMap(), approved: Pair<Int, Int>? = null): Boolean = mutex.withLock {
        var auth = login() ?: return false
        set { it.copy(running = true, progress = "Starting…", lastError = null, review = null) }
        var report: SyncReport? = null
        val error = try {
            val account = nextcloud.readyAccount()
            if (account != null) {
                set { it.copy(progress = "Syncing with Nextcloud first…") }
                if (!nextcloud.sync()) throw SyncException(
                    "Nextcloud sync didn't work (${nextcloud.status.value.lastError ?: "unknown error"}). The Archidekt sync waits for it, so your phones stay in step."
                )
                lock(account, auth.userId)
                finishPending(account, auth.userId)
            }
            var keepLock = false
            try {
                val state = loadState(account, auth.userId)
                set { it.copy(progress = "Reading your Archidekt collection…") }
                val (archData, a1) = client.collection(auth) { n, total -> set { it.copy(progress = "Reading your Archidekt collection… $n of $total") } }
                auth = a1
                val items = db.syncDao().collection()
                val appSide = appSides(items)
                val arch = archData.entries.groupBy { CardKey.of(it) }
                val snap = state?.entries?.associateBy { it.key }
                val plan = ArchidektPlanner.plan(appSide, arch, snap, decisions, approved)
                if (plan.waitsForUser) {
                    set { it.copy(review = plan) }
                    report = SyncReport(
                        System.currentTimeMillis(),
                        if (plan.firstNeedsChoice) "First sync: waiting for you to choose (${plan.conflicts.size} card(s) differ)"
                        else "Waiting for you: ${plan.appRemoved + plan.archRemoved} copies would be removed",
                    )
                } else {
                    val lines = mutableListOf<ReportLine>()
                    if (plan.changesApp) {
                        set { it.copy(progress = "Making a backup first…") }
                        backups.before(if (plan.first) BackupReason.ARCHIDEKT_FIRST else BackupReason.ARCHIDEKT)
                        set { it.copy(progress = "Updating the app…") }
                        lines += applyToApp(plan)
                    }
                    val (archAfter, failed, a2, archLines) = applyToArchidekt(plan, arch, archData.tags, auth)
                    auth = a2
                    lines += archLines
                    for (c in plan.conflicts) lines += ReportLine(ReportLine.Kind.CONFLICT, "${describe(c.label, c.key)}: app ${c.app}, Archidekt ${c.arch}" + (c.snap?.let { ", was $it" } ?: ""))

                    val newState = ArchidektState(
                        userId = auth.userId, savedAt = System.currentTimeMillis(), savedBy = device(),
                        entries = ArchidektPlanner.newSnapshot(plan, archAfter, failed, snap, appSide.mapValues { it.value.price }),
                    )
                    saveLocal(newState)
                    if (account != null) {
                        set { it.copy(progress = "Syncing with Nextcloud…") }
                        if (nextcloud.sync()) {
                            putState(account, newState)
                        } else {
                            // The other phones mustn't see the new agreement before the changes it agrees on: keep the lock and try again next time.
                            prefs.edit().putBoolean(K_PENDING, true).apply()
                            keepLock = true
                            lines += ReportLine(ReportLine.Kind.NOTE, "Nextcloud couldn't be reached at the end; the next sync finishes the job.")
                        }
                    }
                    prefs.edit()
                        .putBoolean(K_READY, true)
                        .putLong(K_LAST, System.currentTimeMillis())
                        .putLong(K_STAMP, db.syncDao().lastChange() ?: 0)
                        .apply()
                    val summary = summary(plan, failed)
                    report = SyncReport(System.currentTimeMillis(), summary, lines)
                    set { it.copy(conflicts = plan.conflicts, ready = true, lastSyncAt = System.currentTimeMillis()) }
                }
            } finally {
                if (account != null && !keepLock) runCatching { unlock(account, auth.userId) }
            }
            null
        } catch (e: ArchidektAuthException) {
            e.message
        } catch (e: SyncException) {
            e.message
        } catch (e: IOException) {
            e.message?.takeIf { "Archidekt" in it } ?: "Couldn't reach Nextcloud: ${e.message ?: e.javaClass.simpleName}"
        } catch (e: kotlinx.serialization.SerializationException) {
            "Archidekt answered something the app doesn't understand."
        }
        saveLogin(auth)
        if (error != null) report = SyncReport(System.currentTimeMillis(), "Failed", error = error)
        report?.let(::addHistory)
        prefs.edit().apply { if (error == null) remove(K_ERROR) else putString(K_ERROR, error) }.apply()
        set { it.copy(running = false, progress = null, lastError = error, lastReport = report ?: it.lastReport) }
        error == null
    }

    private fun summary(plan: Plan, failedKeys: Set<CardKey>): String {
        val failed = failedKeys.size
        val ok = plan.all.filter { it.key !in failedKeys }
        val archAdded = ok.sumOf { maxOf(0, it.archDelta) }
        val archRemoved = ok.sumOf { maxOf(0, -it.archDelta) }
        val parts = listOfNotNull(
            plan.appAdded.takeIf { it > 0 }?.let { "+$it in the app" },
            plan.appRemoved.takeIf { it > 0 }?.let { "−$it in the app" },
            archAdded.takeIf { it > 0 }?.let { "+$it on Archidekt" },
            archRemoved.takeIf { it > 0 }?.let { "−$it on Archidekt" },
            plan.conflicts.size.takeIf { it > 0 }?.let { "$it waiting for you" },
            failed.takeIf { it > 0 }?.let { "$it cards failed (tried again next time)" },
        )
        return if (parts.isEmpty()) "Already the same" else parts.joinToString(" · ")
    }

    private inline fun set(f: (ArchidektStatus) -> ArchidektStatus) {
        _status.value = f(_status.value)
    }

    private fun appSides(items: List<CollectionItem>): Map<CardKey, AppSide> =
        items.groupBy { CardKey.of(it) }.mapValues { (_, stacks) ->
            val main = stacks.maxBy { it.quantity }
            AppSide(
                stacks.sumOf { it.quantity },
                stacks.sortedByDescending { it.quantity }.firstNotNullOfOrNull { it.purchasePrice },
                CardLabel(main.card.name, main.card.setCode.uppercase(), main.card.collectorNumber),
            )
        }

    // ---- Into the app ----------------------------------------------------------------------

    /** Adds, removes and re-prices copies in the app as [plan] says; returns the report lines. */
    private suspend fun applyToApp(plan: Plan): List<ReportLine> {
        val lines = mutableListOf<ReportLine>()
        val changes = plan.all.filter { !it.conflict && (it.appDelta != 0 || it.setAppPrice) }
        // Cards the app doesn't have yet come from Scryfall.
        val known = db.syncDao().collection().associate { it.card.scryfallId to it.card }
        val missing = changes.filter { it.appDelta > 0 }.map { it.key.scryfallId }.filter { it !in known }.distinct()
        val fetched = if (missing.isEmpty()) emptyMap() else repo.scryfall.collection(missing.map { ScryfallApi.idIdentifier(it) }).associate { it.id to it.toRef() }
        val cards = known + fetched
        val binderNames = db.binderDao().all().associate { it.id to it.name }
        val deckNames = db.deckDao().all().map { it.name.trim().lowercase() }.toSet()
        val trade = settings.tradeBinderId.takeIf { it in binderNames } ?: binderNames.entries.firstOrNull { it.value.equals("Trade binder", true) }?.key
        val fromArchidekt = binderNames.entries.firstOrNull { it.value.equals(FROM_ARCHIDEKT, true) }?.key
        fun removeOrder(b: Long) = when {
            b == trade -> 0
            b == Binder.UNSORTED -> 1
            b == fromArchidekt -> 2
            binderNames[b]?.trim()?.lowercase() in deckNames -> 4
            else -> 3
        }
        fun binderName(b: Long) = binderNames[b] ?: Binder.UNSORTED_NAME

        db.withTransaction {
            val coll = db.collectionDao()
            var dest: Long? = null
            suspend fun destination(): Long = dest ?: when (_status.value.pullTo) {
                PullTo.UNSORTED -> Binder.UNSORTED
                PullTo.FROM_ARCHIDEKT -> repo.createBinder(FROM_ARCHIDEKT)
            }.also { dest = it }
            val all = db.syncDao().collection().toMutableList()
            fun matching(key: CardKey) = all.filter { CardKey.of(it) == key }
            // Where copies that left each key were, for copies moving to another key on Archidekt.
            val leftFrom = HashMap<CardKey, MutableList<Pair<CollectionItem, Int>>>()

            for (c in changes.filter { it.appDelta < 0 }) {
                var need = -c.appDelta
                val from = mutableListOf<String>()
                for (s in matching(c.key).sortedWith(compareBy({ removeOrder(it.binderId) }, { -it.quantity }))) {
                    if (need == 0) break
                    val take = minOf(need, s.quantity)
                    if (take == s.quantity) coll.deleteById(s.id) else coll.update(s.copy(quantity = s.quantity - take))
                    all.remove(s)
                    if (take < s.quantity) all += s.copy(quantity = s.quantity - take)
                    leftFrom.getOrPut(c.key) { mutableListOf() } += s to take
                    from += "$take from ${binderName(s.binderId)}"
                    need -= take
                }
                if (plan.all.none { it.movedFrom == c.key }) {
                    lines += ReportLine(ReportLine.Kind.APP_REMOVED, "−${-c.appDelta} ${describe(c.label, c.key)} (${from.joinToString(", ")})")
                }
            }

            for (c in changes.filter { it.appDelta > 0 }) {
                val card = cards[c.key.scryfallId]
                if (card == null) {
                    lines += ReportLine(ReportLine.Kind.FAILED, "${describe(c.label, c.key)}: Scryfall doesn't know this printing, so it wasn't added to the app")
                    continue
                }
                var need = c.appDelta
                val lang = ArchidektCodes.appLanguage(c.key.language)
                fun condFor(binder: Long, fallback: String?): String =
                    all.firstOrNull { it.binderId == binder && CardKey.of(it) == c.key }?.condition
                        ?: fallback?.takeIf { ArchidektCodes.archCondition(it) == c.key.condition }
                        ?: ArchidektCodes.appCondition(c.key.condition)
                val placed = mutableListOf<String>()
                // Copies whose Archidekt entry was edited stay in the binders they were in.
                c.movedFrom?.let { from ->
                    for ((stack, n) in leftFrom[from].orEmpty().toList()) {
                        if (need == 0) break
                        val take = minOf(need, n)
                        val r = repo.addToCollection(card, c.key.finish, condFor(stack.binderId, stack.condition), lang, take, stack.binderId)
                        coll.byId(r.itemId)?.let { added ->
                            if (added.notes == null && stack.notes != null || added.purchasePrice == null && stack.purchasePrice != null) {
                                coll.update(added.copy(notes = added.notes ?: stack.notes, purchasePrice = added.purchasePrice ?: stack.purchasePrice))
                            }
                        }
                        leftFrom[from]!!.remove(stack to n)
                        if (n > take) leftFrom[from]!!.add(stack to n - take)
                        placed += "$take in ${binderName(stack.binderId)}"
                        need -= take
                    }
                    lines += ReportLine(
                        ReportLine.Kind.ARCH_CHANGED,
                        "Changed on Archidekt: ${describe(c.label, from)} → ${ArchidektCodes.modifier(c.key.finish)}, ${c.key.condition}, ${c.key.language}",
                    )
                }
                if (need > 0) {
                    val b = destination()
                    repo.addToCollection(card, c.key.finish, condFor(b, null), lang, need, b)
                    placed += "$need in " + (binderNames[b] ?: if (b == Binder.UNSORTED) Binder.UNSORTED_NAME else FROM_ARCHIDEKT)
                }
                val now = db.syncDao().collection()
                all.clear(); all += now
                lines += ReportLine(ReportLine.Kind.APP_ADDED, "+${c.appDelta} ${describe(c.label, c.key)} (${placed.joinToString(", ")})")
            }

            for (c in changes.filter { it.setAppPrice }) {
                for (s in db.syncDao().collection().filter { CardKey.of(it) == c.key }) {
                    if (!ArchidektPlanner.samePrice(s.purchasePrice, c.price)) coll.update(s.copy(purchasePrice = c.price))
                }
                lines += ReportLine(ReportLine.Kind.APP_PRICE, "Purchase price of ${describe(c.label, c.key)} → ${c.price?.let(::price) ?: "none"}")
            }
        }
        return lines
    }

    // ---- Onto Archidekt --------------------------------------------------------------------

    private data class ArchResult(
        val after: Map<CardKey, List<ArchidektEntry>>,
        val failed: Set<CardKey>,
        val login: ArchidektLogin,
        val lines: List<ReportLine>,
    )

    private suspend fun applyToArchidekt(
        plan: Plan,
        arch: Map<CardKey, List<ArchidektEntry>>,
        tagsBefore: List<ArchidektTag>,
        login: ArchidektLogin,
    ): ArchResult {
        var auth = login
        val after = arch.mapValues { it.value.toMutableList() }.toMutableMap()
        val failed = HashSet<CardKey>()
        val lines = mutableListOf<ReportLine>()
        val changes = plan.all.filter { !it.conflict && (it.archDelta != 0 || it.setArchPrice) }
        val tagMode = _status.value.tagMode
        val tagged = if (tagMode == TagMode.NONE) emptyMap() else binderTags(tagMode)
        val tags = tagsBefore.toMutableList()
        val ourTagNames = if (tagMode == TagMode.NONE) emptySet() else db.binderDao().all().map { it.name.lowercase() }.toSet() + TRADE_TAG.lowercase()

        // Labels Archidekt doesn't have yet are made first, one by one (the rest runs several requests at once).
        for (name in tagged.values.flatten().toSortedSet(String.CASE_INSENSITIVE_ORDER)) {
            if (tags.none { it.name.equals(name, true) }) {
                val (t, a) = client.createTag(auth, name, TAG_COLOR)
                auth = a
                tags += t
            }
        }
        fun tagIds(key: CardKey): List<Long>? {
            if (tagMode == TagMode.NONE) return null
            return tagged[key].orEmpty().mapNotNull { name -> tags.firstOrNull { it.name.equals(name, true) }?.id }.sorted()
        }
        fun withTags(e: ArchidektEntry, ours: List<Long>?): ArchidektEntry {
            if (ours == null) return e
            val keep = e.tags.filter { id -> tags.firstOrNull { it.id == id }?.name?.lowercase() !in ourTagNames }
            return e.copy(tags = (keep + ours).distinct())
        }

        // Archidekt's card ids for printings it doesn't hold yet.
        val needIds = changes.filter { it.archDelta > 0 && after[it.key].isNullOrEmpty() }.map { it.key.scryfallId }.toSet()
        val printings = if (needIds.isEmpty()) emptyMap() else {
            set { it.copy(progress = "Looking up ${needIds.size} printings on Archidekt…") }
            client.printings(needIds)
        }
        val cardIdOf = HashMap<String, Long>()
        arch.values.flatten().forEach { cardIdOf[it.scryfallId] = it.cardId }
        printings.forEach { (sid, p) -> cardIdOf[sid] = p.cardId }

        // An entry edited in the app (condition, language or finish) moves as a whole.
        val handled = HashSet<CardKey>()
        val decreases = changes.filter { it.archTarget == 0 && it.arch > 0 && after[it.key]?.size == 1 }
        for (down in decreases) {
            val up = changes.firstOrNull {
                it.key !in handled && it.key.scryfallId == down.key.scryfallId && it.key != down.key &&
                    it.arch == 0 && it.archDelta == down.arch
            } ?: continue
            val e = after[down.key]!!.single()
            try {
                val moved = withTags(e.copy(finish = up.key.finish, condition = up.key.condition, language = up.key.language, purchasePrice = if (up.setArchPrice) up.price else e.purchasePrice), tagIds(up.key))
                auth = client.update(auth, moved)
                after[down.key] = mutableListOf()
                after[up.key] = mutableListOf(moved)
                handled += down.key; handled += up.key
                lines += ReportLine(ReportLine.Kind.ARCH_CHANGED, "On Archidekt: ${describe(down.label, down.key)} → ${ArchidektCodes.modifier(up.key.finish)}, ${up.key.condition}, ${up.key.language}")
            } catch (e: IOException) {
                failed += down.key; failed += up.key
                lines += ReportLine(ReportLine.Kind.FAILED, "${describe(down.label, down.key)}: ${e.message}")
            }
        }

        // Work out every request first: new entries, changed ones, and ones to delete.
        val planned = mutableListOf<Pair<CardKey, ReportLine>>()
        val creates = mutableListOf<Pair<KeyChange, ArchidektEntry>>()
        val updates = mutableListOf<Pair<CardKey, ArchidektEntry>>()
        val deletes = mutableListOf<Pair<CardKey, Long>>()
        for (c in changes) {
            if (c.key in handled) continue
            val list = after.getOrPut(c.key) { mutableListOf() }
            val d = c.archDelta
            when {
                d > 0 && list.isNotEmpty() -> {
                    val e = list[0]
                    updates += c.key to withTags(e.copy(quantity = e.quantity + d, purchasePrice = if (c.setArchPrice) c.price else e.purchasePrice), tagIds(c.key))
                    planned += c.key to ReportLine(ReportLine.Kind.ARCH_ADDED, "+$d ${describe(c.label, c.key)}")
                }
                d > 0 -> {
                    val cardId = cardIdOf[c.key.scryfallId]
                    if (cardId == null) {
                        failed += c.key
                        lines += ReportLine(ReportLine.Kind.FAILED, "${describe(c.label, c.key)}: Archidekt doesn't know this printing yet")
                        continue
                    }
                    creates += c to ArchidektEntry(
                        id = 0, cardId = cardId, scryfallId = c.key.scryfallId, name = c.label.name, setCode = c.label.setCode, number = c.label.number,
                        finish = c.key.finish, condition = c.key.condition, language = c.key.language, quantity = d,
                        purchasePrice = c.price.takeIf { c.setArchPrice },
                        tags = tagIds(c.key).orEmpty(),
                    )
                    planned += c.key to ReportLine(ReportLine.Kind.ARCH_ADDED, "+$d ${describe(c.label, c.key)}")
                }
                d < 0 -> {
                    var need = -d
                    val keep = list.toMutableList()
                    for (e in list.reversed()) {
                        if (need == 0) break
                        if (e.quantity <= need) {
                            deletes += c.key to e.id
                            need -= e.quantity
                            keep.remove(e)
                        } else {
                            val i = keep.indexOf(e)
                            keep[i] = e.copy(quantity = e.quantity - need)
                            need = 0
                        }
                    }
                    if (c.setArchPrice && keep.isNotEmpty()) keep[0] = keep[0].copy(purchasePrice = c.price)
                    for (e in keep) if (e != list.firstOrNull { it.id == e.id }) updates += c.key to e
                    planned += c.key to ReportLine(ReportLine.Kind.ARCH_REMOVED, "−${-d} ${describe(c.label, c.key)}")
                }
                c.setArchPrice && list.isNotEmpty() -> {
                    updates += c.key to list[0].copy(purchasePrice = c.price)
                    planned += c.key to ReportLine(ReportLine.Kind.ARCH_CHANGED, "Purchase price of ${describe(c.label, c.key)} on Archidekt → ${c.price?.let(::price) ?: "none"}")
                }
            }
        }
        // Binder labels on every card, not just the ones that changed.
        var relabelled = 0
        if (tagMode != TagMode.NONE) {
            val touched = updates.map { it.second.id }.toSet() + deletes.map { it.second }.toSet()
            for ((key, list) in after) {
                val ours = tagIds(key) ?: continue
                for (e in list) {
                    if (e.id in touched) continue
                    val want = withTags(e, ours)
                    if (want.tags.toSet() != e.tags.toSet()) {
                        updates += key to want
                        relabelled++
                    }
                }
            }
        }

        // Then send them, a few at a time.
        val total = creates.size + updates.size + deletes.size
        var done = 0
        val progress = Mutex()
        suspend fun step(n: Int = 1) = progress.withLock {
            done += n
            if (done % 10 == 0 || done == total) set { it.copy(progress = "Updating Archidekt… $done of $total") }
        }
        val failedLines = java.util.Collections.synchronizedList(mutableListOf<ReportLine>())
        val failedKeys = java.util.Collections.synchronizedSet(HashSet<CardKey>())
        val gate = Semaphore(PARALLEL)
        val tokens = Mutex()
        suspend fun <T> call(block: suspend (ArchidektLogin) -> Pair<T, ArchidektLogin>): T {
            val (result, a) = block(tokens.withLock { auth })
            tokens.withLock { auth = a }
            return result
        }
        coroutineScope {
            for ((c, e) in creates) launch {
                gate.withPermit {
                    try {
                        val made = call { client.create(it, e) }
                        progress.withLock { after.getOrPut(c.key) { mutableListOf() } += made }
                    } catch (ex: IOException) {
                        failedKeys += c.key
                        failedLines += ReportLine(ReportLine.Kind.FAILED, "${describe(c.label, c.key)}: ${ex.message}")
                    }
                    step()
                }
            }
            for ((key, e) in updates) launch {
                gate.withPermit {
                    try {
                        call { Unit to client.update(it, e) }
                        progress.withLock {
                            val list = after.getOrPut(key) { mutableListOf() }
                            val i = list.indexOfFirst { it.id == e.id }
                            if (i >= 0) list[i] = e else list += e
                        }
                    } catch (ex: IOException) {
                        failedKeys += key
                        failedLines += ReportLine(ReportLine.Kind.FAILED, "${e.name.ifEmpty { describe(CardLabel(), key) }}: ${ex.message}")
                    }
                    step()
                }
            }
        }
        for (chunk in deletes.chunked(100)) {
            try {
                auth = client.delete(auth, chunk.map { it.second })
                val ids = chunk.map { it.second }.toSet()
                for ((key, _) in chunk) after[key]?.removeAll { it.id in ids }
            } catch (ex: IOException) {
                chunk.forEach { failedKeys += it.first }
                failedLines += ReportLine(ReportLine.Kind.FAILED, "Deleting ${chunk.size} entries: ${ex.message}")
            }
            step(chunk.size)
        }
        failed += failedKeys
        lines += planned.filter { it.first !in failed }.map { it.second }
        lines += failedLines
        if (relabelled > 0) lines += ReportLine(ReportLine.Kind.NOTE, "Updated the binder labels of $relabelled Archidekt entries")
        return ArchResult(after, failed, auth, lines)
    }

    /** For each card, the binder labels it should carry on Archidekt. */
    private suspend fun binderTags(mode: TagMode): Map<CardKey, Set<String>> {
        val names = db.binderDao().all().associate { it.id to it.name }
        val trade = settings.tradeBinderId.takeIf { it in names } ?: names.entries.firstOrNull { it.value.equals("Trade binder", true) }?.key
        return db.syncDao().collection().groupBy { CardKey.of(it) }.mapValues { (_, stacks) ->
            stacks.mapNotNull { s ->
                when {
                    mode == TagMode.TRADE_BINDER -> if (s.binderId == trade) TRADE_TAG else null
                    s.binderId == Binder.UNSORTED -> null
                    else -> names[s.binderId]
                }
            }.toSet()
        }
    }

    // ---- The agreement and the lock ---------------------------------------------------------

    private fun stateName(userId: Long) = "archidekt-sync-$userId.json.gz"
    private fun lockName(userId: Long) = "archidekt-sync-$userId.lock"
    private val localState get() = File(context.filesDir, "archidekt-state.json.gz")

    private suspend fun loadState(account: NextcloudAccount?, userId: Long): ArchidektState? {
        if (account != null) {
            return when (val r = cloud.getIn(account, stateName(userId))) {
                is NextcloudClient.Remote.Found -> decodeState(r.bytes)?.takeIf { it.userId == userId }
                // Shared state not there (first sync on Nextcloud): this phone's own agreement, if it synced on its own before.
                else -> readLocal()?.takeIf { it.userId == userId && prefs.getBoolean(K_READY, false) }
            }
        }
        return readLocal()?.takeIf { it.userId == userId && prefs.getBoolean(K_READY, false) }
    }

    private suspend fun putState(account: NextcloudAccount, state: ArchidektState) {
        cloud.putIn(account, stateName(state.userId), encodeState(state), null)
        prefs.edit().remove(K_PENDING).apply()
    }

    /** The agreement of a sync whose last Nextcloud step failed goes up now (this phone still holds the lock). */
    private suspend fun finishPending(account: NextcloudAccount, userId: Long) {
        if (!prefs.getBoolean(K_PENDING, false)) return
        readLocal()?.takeIf { it.userId == userId }?.let { putState(account, it) }
        prefs.edit().remove(K_PENDING).apply()
    }

    private suspend fun readLocal(): ArchidektState? = withContext(Dispatchers.IO) {
        if (localState.exists()) decodeState(localState.readBytes()) else null
    }

    private suspend fun saveLocal(state: ArchidektState) = withContext(Dispatchers.IO) { localState.writeBytes(encodeState(state)) }

    private fun encodeState(s: ArchidektState): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.encodeToString(ArchidektState.serializer(), s).toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun decodeState(bytes: ByteArray): ArchidektState? = runCatching {
        json.decodeFromString(ArchidektState.serializer(), GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }.toString(Charsets.UTF_8))
    }.getOrNull()

    @Serializable
    private data class LockFile(val installId: String, val device: String, val at: Long)

    private val installId: String
        get() = prefs.getString(K_INSTALL, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(K_INSTALL, it).apply() }

    /** Takes the lock in the Nextcloud folder, so two phones don't sync with Archidekt at the same time. */
    private suspend fun lock(account: NextcloudAccount, userId: Long) {
        val name = lockName(userId)
        val mine = json.encodeToString(LockFile.serializer(), LockFile(installId, device(), System.currentTimeMillis())).toByteArray()
        val result = when (val r = cloud.getIn(account, name)) {
            is NextcloudClient.Remote.Found -> {
                val held = runCatching { json.decodeFromString(LockFile.serializer(), r.bytes.toString(Charsets.UTF_8)) }.getOrNull()
                if (held != null && held.installId != installId && System.currentTimeMillis() - held.at < LOCK_STALE_MS) {
                    throw SyncException("${held.device} is syncing with Archidekt (since ${Fmt.dateTime(held.at)}). Try again when it's done.")
                }
                cloud.putIn(account, name, mine, r.etag)
            }
            else -> cloud.putIn(account, name, mine, null, onlyNew = true)
        }
        if (result is NextcloudClient.PutResult.Conflict) throw SyncException("Another phone just started syncing with Archidekt. Try again in a minute.")
    }

    private suspend fun unlock(account: NextcloudAccount, userId: Long) = cloud.deleteIn(account, lockName(userId))

    // ---- History ---------------------------------------------------------------------------

    private val historyFile get() = File(context.filesDir, "archidekt-history.json")

    fun history(): List<SyncReport> = runCatching {
        json.decodeFromString(ListSerializer(SyncReport.serializer()), historyFile.readText())
    }.getOrDefault(emptyList())

    private fun addHistory(r: SyncReport) {
        runCatching { historyFile.writeText(json.encodeToString(ListSerializer(SyncReport.serializer()), listOf(r) + history().take(HISTORY - 1))) }
    }

    private fun device() = "${Build.MANUFACTURER} ${Build.MODEL}"

    companion object {
        const val FROM_ARCHIDEKT = "From Archidekt"
        const val TRADE_TAG = "Trade binder"
        private const val TAG_COLOR = "#2e7d32"
        private const val K_LOGIN = "login"
        private const val K_SERVER = "server"
        private const val K_LAST = "lastSync"
        private const val K_ERROR = "lastError"
        private const val K_AUTO = "auto"
        private const val K_WIFI = "wifiOnly"
        private const val K_PULL_TO = "pullTo"
        private const val K_TAGS = "tags"
        private const val K_READY = "ready"
        private const val K_STAMP = "stamp"
        private const val K_PENDING = "pendingState"
        private const val K_INSTALL = "installId"
        private const val WORK = "archidekt-sync"
        private const val HISTORY = 20
        /** Requests sent to Archidekt at the same time. */
        private const val PARALLEL = 4
        private const val AUTO_EVERY_MS = 60L * 60 * 1000
        private const val LOCK_STALE_MS = 6L * 60 * 60 * 1000

        private fun price(v: Double) = String.format(java.util.Locale.US, "%.2f", v)

        fun describe(label: CardLabel, key: CardKey): String {
            val finish = when (key.finish) { Finish.FOIL -> " foil"; Finish.ETCHED -> " etched"; Finish.NONFOIL -> "" }
            val name = label.name.ifEmpty { key.scryfallId.take(8) }
            val set = label.setCode.takeIf { it.isNotEmpty() }?.let { " (${it.uppercase()} ${label.number})" }.orEmpty()
            return "$name$set$finish, ${key.condition}, ${key.language}"
        }
    }
}

/** Background Archidekt sync after leaving the app; see [ArchidektSync.onAppBackground]. */
class ArchidektWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.container.archidekt.autoSync()
        return Result.success()
    }
}
