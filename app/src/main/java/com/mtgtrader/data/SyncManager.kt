package com.mtgtrader.data

import android.content.Context
import android.os.Build
import androidx.room.InvalidationTracker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgtrader.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the Settings screen shows about sync. */
data class SyncStatus(
    val server: String? = null,
    val user: String? = null,
    val running: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastError: String? = null,
    /** Set up and synced at least once; until then automatic sync waits. */
    val ready: Boolean = false,
    /** Connected, but both this phone and the account hold data: the user picks a [FirstSync] first. */
    val firstChoice: FirstChoice? = null,
    val autoSync: Boolean = true,
    val wifiOnly: Boolean = false,
    /** This phone synced with Nextcloud, which the app stopped doing in 1.82: it should log in to an MTG Trader account. */
    val nextcloudEnded: Boolean = false,
    /** Another phone that last synced with a version too old to read this one's file. Since 1.27. */
    val olderPhone: OlderPhone? = null,
    /** Syncing through an MTG Trader account on the website ([server] is the website). Since 1.79; the only way since 1.82. */
    val website: Boolean = false,
) {
    val connected get() = server != null
}

data class FirstChoice(val phone: SyncSummary, val nextcloud: SyncSummary)

/**
 * A phone ([name]) seen syncing with a version from before signed and altered cards: it can't sync
 * until it's updated. [acknowledged] once the user has seen the warning. Since 1.27.
 */
data class OlderPhone(val name: String, val seenAt: Long, val acknowledged: Boolean = false)

/** What a copy of the data holds, to help pick a [FirstSync]. */
data class SyncSummary(val cards: Int, val binders: Int, val decks: Int, val trades: Int, val scans: Int) {
    override fun toString() = listOfNotNull(
        count(cards, "card"),
        binders.takeIf { it > 0 }?.let { count(it, "binder") },
        decks.takeIf { it > 0 }?.let { count(it, "deck") },
        trades.takeIf { it > 0 }?.let { count(it, "trade") },
        scans.takeIf { it > 0 }?.let { count(it, "scanned card") },
    ).joinToString(", ")

    private fun count(n: Int, what: String) = "$n $what" + if (n == 1) "" else "s"

    companion object {
        fun of(d: SyncData) = SyncSummary(d.collection.sumOf { it.item.quantity }, d.binders.size, d.decks.size, d.trades.size, d.scans.sumOf { it.quantity })
    }
}

/** Where the sync file lives: the user's MTG Trader account on the website (since 1.79; Nextcloud until 1.81). */
private sealed interface SyncPlace {
    data class Web(val login: WebLogin) : SyncPlace
}

/**
 * Keeps this phone in sync with the others through one file in the user's MTG Trader account on the
 * website (since 1.79; until 1.81 also on the user's Nextcloud, which the website now backs up to). With
 * automatic sync on, it syncs when the app opens, ~30 s after a change, when the app goes to
 * the background and every hour (only on Wi-Fi if chosen). "Sync now" always runs.
 */
class SyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val store: SyncStore,
    private val client: NextcloudClient,
    private val web: WebAccountClient,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var debounce: Job? = null

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<SyncStatus> = _status

    init {
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("collection", "binders", "trades", "trade_items", "decks", "deck_cards", "scans", "wishlist", "players", "player_decks", "games", "paused_games", "sync_deletions", "trade_skips")) {
            override fun onInvalidated(tables: Set<String>) = onDataChanged()
        })
    }

    private fun readStatus() = SyncStatus(
        // Not connected if the token can't be read (e.g. app data restored onto another phone).
        server = if (webLogin() != null) WebAccountClient.HOST else null,
        user = webLogin()?.username,
        website = webLogin() != null,
        nextcloudEnded = webLogin() == null && prefs.contains(K_SERVER),
        lastSyncAt = prefs.getLong(K_LAST_SYNC, 0),
        lastError = prefs.getString(K_ERROR, null),
        ready = prefs.getBoolean(K_FIRST_DONE, false),
        autoSync = prefs.getBoolean(K_AUTO, true),
        wifiOnly = prefs.getBoolean(K_WIFI, false),
        olderPhone = prefs.getString(K_OLDER, null)?.let { OlderPhone(it, prefs.getLong(K_OLDER_AT, 0), prefs.getBoolean(K_OLDER_SEEN, false)) },
    )

    /** The Nextcloud account a phone synced with before 1.82: kept only to remove the app's password from it. */
    private fun account(): NextcloudAccount? {
        val server = prefs.getString(K_SERVER, null) ?: return null
        val login = prefs.getString(K_USER, null) ?: return null
        val pw = prefs.getString(K_PASSWORD, null)?.let(SecretStore::decrypt) ?: return null
        return NextcloudAccount(server, login, pw, prefs.getString(K_USER_ID, null) ?: login, prefs.getString(K_FOLDER, null) ?: NextcloudClient.FOLDER)
    }

    /** This phone's website account login, if it syncs through one (also used for the website's prices, since 1.81). */
    fun webLogin(): WebLogin? {
        val user = prefs.getString(K_WEB_USER, null) ?: return null
        val token = prefs.getString(K_WEB_TOKEN, null)?.let(SecretStore::decrypt) ?: return null
        return WebLogin(user, token)
    }

    private fun place(): SyncPlace? = webLogin()?.let { SyncPlace.Web(it) }

    private suspend fun SyncPlace.get(etag: String?) = when (this) {
        is SyncPlace.Web -> web.get(login, etag)
    }

    private suspend fun SyncPlace.put(bytes: ByteArray, ifMatch: String?) = when (this) {
        is SyncPlace.Web -> web.put(login, bytes, ifMatch)
    }

    private fun SyncPlace.name() = "the MTG Trader website"

    private val firstDone get() = prefs.getBoolean(K_FIRST_DONE, false)

    /** Runs before the first sync with a folder changes anything on this phone (a backup). Since 1.21. */
    var beforeFirstSync: (suspend () -> Unit)? = null

    /** Whether an automatic sync may run now. */
    private fun autoAllowed(): Boolean {
        val s = _status.value
        return s.connected && firstDone && s.autoSync && (!s.wifiOnly || network.onUnmeteredNetwork())
    }

    // ---- Connecting ------------------------------------------------------------------------

    /** Logs in to an MTG Trader account on the website and syncs with it from now on, instead of Nextcloud. Since 1.79. */
    suspend fun loginWeb(username: String, password: String) = useWeb(web.login(username, password, device()))

    /** Makes an MTG Trader account with an invite code and syncs with it. Since 1.79. */
    suspend fun registerWeb(invite: String, username: String, password: String) = useWeb(web.register(invite, username, password, device()))

    /** Forgets Nextcloud (if this phone synced there before 1.82), then the first sync with the account follows ([prepareFirst]). */
    private suspend fun useWeb(login: WebLogin) {
        val old = account()
        val revoke = prefs.getBoolean(K_REVOKE, false)
        mutex.withLock {
            prefs.edit()
                .remove(K_SERVER).remove(K_USER).remove(K_USER_ID).remove(K_PASSWORD).remove(K_REVOKE).remove(K_FOLDER)
                .putString(K_WEB_USER, login.username)
                .putString(K_WEB_TOKEN, SecretStore.encrypt(login.token))
                .putBoolean(K_FIRST_DONE, false)
                .remove(K_ETAG).remove(K_STAMP).remove(K_PREFS_AT).remove(K_ERROR).remove(K_LAST_SYNC)
                .apply()
            _status.value = readStatus()
        }
        schedule()
        // The app password this app made on Nextcloud isn't needed any more (the website keeps its own for its backups).
        if (old != null && revoke) scope.launch { runCatching { client.revoke(old) } }
        prepareFirst()
    }

    /** Looks at both sides before the first sync; runs it right away unless the user has to choose. */
    suspend fun prepareFirst() {
        val place = place() ?: return
        val remote = try {
            when (val r = place.get(null)) {
                is NextcloudClient.Remote.Found -> SyncFile.decode(r.bytes).data
                else -> null
            }
        } catch (e: SyncException) {
            _status.value = _status.value.copy(lastError = e.message)
            return
        } catch (e: IOException) {
            _status.value = _status.value.copy(lastError = "Couldn't reach ${place.name()}: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        val (local, _) = store.snapshot()
        if (!local.isEmpty) beforeFirstSync?.invoke()
        // Only ask when this phone holds something the account doesn't (e.g. not when reconnecting the same phone).
        val now = System.currentTimeMillis()
        val phoneAddsSomething = remote != null && SyncMerge.merge(local, remote, now, FirstSync.MERGE) != SyncMerge.merge(remote, null, now)
        if (remote != null && !remote.isEmpty && !local.isEmpty && phoneAddsSomething) {
            _status.value = _status.value.copy(firstChoice = FirstChoice(SyncSummary.of(local), SyncSummary.of(remote)))
        } else {
            sync(FirstSync.MERGE)
        }
    }

    fun cancelFirstChoice() {
        _status.value = _status.value.copy(firstChoice = null)
    }

    /** The user's answer to [SyncStatus.firstChoice]. */
    fun chooseFirst(choice: FirstSync) {
        _status.value = _status.value.copy(firstChoice = null)
        scope.launch { sync(choice) }
    }

    /** Logs out (and removes the app password from Nextcloud if this phone synced there before 1.82). Data on the phone stays. */
    fun disconnect() {
        val account = account()
        val login = webLogin()
        val revoke = prefs.getBoolean(K_REVOKE, false)
        prefs.edit().clear().putBoolean(K_AUTO, _status.value.autoSync).putBoolean(K_WIFI, _status.value.wifiOnly).apply()
        _status.value = readStatus()
        schedule()
        if (account != null && revoke) scope.launch { runCatching { client.revoke(account) } }
        if (login != null) scope.launch { runCatching { web.logout(login) } }
    }

    fun setAutoSync(on: Boolean) {
        prefs.edit().putBoolean(K_AUTO, on).apply()
        _status.value = _status.value.copy(autoSync = on)
        schedule()
    }

    fun setWifiOnly(on: Boolean) {
        prefs.edit().putBoolean(K_WIFI, on).apply()
        _status.value = _status.value.copy(wifiOnly = on)
        schedule()
    }

    // ---- When to sync ----------------------------------------------------------------------

    /** On opening the app. */
    fun onAppStart() {
        schedule()
        if (autoAllowed()) scope.launch { sync() }
    }

    private fun onDataChanged() {
        if (!autoAllowed() || mutex.isLocked) return
        debounce?.cancel()
        debounce = scope.launch {
            delay(DEBOUNCE_MS)
            if (hasLocalChanges()) sync()
        }
    }

    /** On leaving the app: changes not synced yet go out in the background, even if Android closes the app. */
    fun onAppBackground() {
        val s = _status.value
        if (!s.connected || !firstDone || !s.autoSync) return
        scope.launch {
            if (!hasLocalChanges()) return@launch
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_ONCE, ExistingWorkPolicy.REPLACE, request)
        }
    }

    private suspend fun hasLocalChanges(): Boolean =
        store.lastChange() != prefs.getLong(K_STAMP, -1) || store.prefsUpdatedAt() != prefs.getLong(K_PREFS_AT, -1)

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(if (_status.value.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    /** Sets up (or cancels) the hourly background sync to match the settings. */
    fun schedule() {
        val wm = WorkManager.getInstance(context)
        val s = _status.value
        if (!s.connected || !s.autoSync) {
            wm.cancelUniqueWork(WORK_PERIODIC)
            wm.cancelUniqueWork(WORK_ONCE)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(constraints()).build()
        wm.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** For the background worker. */
    suspend fun autoSync() {
        if (autoAllowed()) sync()
    }

    /** The user has read the warning about [SyncStatus.olderPhone]; Settings keeps a note until it's updated. */
    fun acknowledgeOlderPhone() {
        prefs.edit().putBoolean(K_OLDER_SEEN, true).apply()
        _status.value = _status.value.copy(olderPhone = _status.value.olderPhone?.copy(acknowledged = true))
    }

    /** Forgets [SyncStatus.olderPhone] (e.g. that phone isn't used any more). */
    fun forgetOlderPhone() {
        prefs.edit().remove(K_OLDER).remove(K_OLDER_AT).remove(K_OLDER_SEEN).apply()
        _status.value = _status.value.copy(olderPhone = null)
    }

    /** Notes a phone still on an older version in [file], or that the one noted before has been updated. */
    private fun checkWriter(file: SyncFile, etag: String?) {
        val older = SyncFile.olderWriter(file, etag, prefs.getString(K_ETAG, null), device())
        val noted = prefs.getString(K_OLDER, null)
        when {
            older != null && older != noted -> {
                prefs.edit().putString(K_OLDER, older).putLong(K_OLDER_AT, file.writtenAt).putBoolean(K_OLDER_SEEN, false).apply()
                _status.value = _status.value.copy(olderPhone = OlderPhone(older, file.writtenAt))
            }
            older == null && noted != null && file.format >= SyncFile.FORMAT && file.writtenBy == noted -> forgetOlderPhone()
        }
    }

    private fun device() = "${Build.MANUFACTURER} ${Build.MODEL}"

    // ---- Syncing ---------------------------------------------------------------------------

    fun syncNow() {
        scope.launch { sync() }
    }

    /**
     * Syncs everything now, even if nothing seems to have changed: after writes made with change
     * tracking paused (restoring a backup), which the usual "anything new?" check can't see. Since 1.21.
     */
    fun syncAllNow() {
        prefs.edit().remove(K_STAMP).remove(K_ETAG).remove(K_PREFS_AT).apply()
        scope.launch { sync() }
    }

    /**
     * The website's share link for a deck, game, binder or the collection (see [WebAccountClient.share]), after syncing
     * so the website has the latest data. Throws [SyncException] with a message for the user when that can't be done.
     */
    suspend fun share(kind: String, target: String): WebAccountClient.ShareLink {
        val login = webLogin() ?: throw SyncException("Sharing needs an MTG Trader account (Settings → Sync).")
        if (!sync()) throw SyncException(_status.value.lastError ?: "Sync first (Settings → Sync), then share.")
        return web.share(login, kind, target)
    }

    /** Syncs once; errors end up in [SyncStatus.lastError]. Returns whether it succeeded. */
    suspend fun sync(first: FirstSync? = null): Boolean = mutex.withLock {
        val place = place() ?: return false
        if (first == null && !firstDone) return false
        _status.value = _status.value.copy(running = true)
        val error = try {
            runSync(place, first)
            null
        } catch (e: SyncException) {
            e.message
        } catch (e: IOException) {
            "Couldn't reach ${place.name()}: ${e.message ?: e.javaClass.simpleName}"
        } catch (e: kotlinx.serialization.SerializationException) {
            "The sync file on ${place.name()} couldn't be read."
        }
        val now = System.currentTimeMillis()
        prefs.edit().apply {
            if (error == null) putLong(K_LAST_SYNC, now).remove(K_ERROR) else putString(K_ERROR, error)
        }.apply()
        _status.value = _status.value.copy(
            running = false, lastError = error, ready = firstDone,
            lastSyncAt = if (error == null) now else _status.value.lastSyncAt,
        )
        if (error == null && place is SyncPlace.Web) sendValueHistoryOnce(place.login)
        error == null
    }

    /**
     * Once per website account: the phone's value history goes to the account, so the website's chart (Stats → Value
     * over time) starts from it; the website keeps the days it saved itself. Tried again after the next sync if it
     * fails. Since 1.80.
     */
    private suspend fun sendValueHistoryOnce(login: WebLogin) {
        if (prefs.getString(K_VALUES_SENT, null) == login.username) return
        runCatching {
            val uids = db.binderDao().all().associate { it.id to it.uid }
            val days = db.valueHistoryDao().all().mapNotNull { s ->
                val values = runCatching { Json.decodeFromString<Map<String, Double>>(s.values) }.getOrNull() ?: return@mapNotNull null
                WebAccountClient.ValueDay(s.day, s.cards, ValueHistory.forWebsite(values, uids))
            }
            if (days.isNotEmpty()) web.sendValueHistory(login, days)
            prefs.edit().putString(K_VALUES_SENT, login.username).apply()
        }
    }

    private suspend fun runSync(place: SyncPlace, first: FirstSync?) {
        repeat(ATTEMPTS) {
            val (local, stamp) = store.snapshot()
            val unchanged = first == null && stamp == prefs.getLong(K_STAMP, -1) && local.prefs.updatedAt == prefs.getLong(K_PREFS_AT, -1)
            val remote = place.get(if (unchanged) prefs.getString(K_ETAG, null) else null)
            if (remote is NextcloudClient.Remote.NotModified) return
            val found = remote as? NextcloudClient.Remote.Found
            val remoteFile = found?.let { f -> SyncFile.decode(f.bytes).also { checkWriter(it, f.etag) } }
            val remoteData = remoteFile?.data
            val now = System.currentTimeMillis()
            val merged = SyncMerge.merge(local, remoteData, now, first)

            if (merged != local && !store.apply(local, merged, stamp)) return@repeat // edited meanwhile: start over
            var etag = found?.etag
            // A file from an older version is rewritten in this one's format even if nothing changed, so phones still on it stop and ask to be updated.
            if (merged != remoteData || (remoteFile != null && remoteFile.format < SyncFile.FORMAT)) {
                val file = SyncFile(writtenAt = now, writtenBy = device(), data = merged)
                when (val put = place.put(SyncFile.encode(file), found?.etag)) {
                    NextcloudClient.PutResult.Conflict -> return@repeat // another phone wrote meanwhile: start over
                    is NextcloudClient.PutResult.Ok -> etag = put.etag
                }
            }
            prefs.edit()
                .putString(K_ETAG, etag)
                .putLong(K_STAMP, store.lastChange())
                .putLong(K_PREFS_AT, merged.prefs.updatedAt)
                .putBoolean(K_FIRST_DONE, true)
                .apply()
            return
        }
        throw SyncException("Sync kept colliding with other changes; it will try again later.")
    }

    private companion object {
        const val K_SERVER = "server"
        const val K_USER = "user"
        const val K_USER_ID = "userId"
        const val K_PASSWORD = "password"
        const val K_REVOKE = "revokeOnDisconnect"
        const val K_FIRST_DONE = "firstDone"
        const val K_ETAG = "etag"
        const val K_STAMP = "syncedStamp"
        const val K_PREFS_AT = "syncedPrefsAt"
        const val K_LAST_SYNC = "lastSync"
        const val K_VALUES_SENT = "valueHistorySentTo"
        const val K_ERROR = "lastError"
        const val K_AUTO = "auto"
        const val K_WIFI = "wifiOnly"
        const val K_FOLDER = "folder"
        const val K_OLDER = "olderPhone"
        const val K_OLDER_AT = "olderPhoneAt"
        const val K_OLDER_SEEN = "olderPhoneSeen"
        const val K_WEB_USER = "webUser"
        const val K_WEB_TOKEN = "webToken"
        const val WORK_PERIODIC = "nextcloud-sync"
        const val WORK_ONCE = "nextcloud-sync-once"
        const val DEBOUNCE_MS = 30_000L
        const val ATTEMPTS = 4
    }
}

/** Background sync; see [SyncManager.schedule] and [SyncManager.onAppBackground]. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.container.sync.autoSync()
        return Result.success()
    }
}
