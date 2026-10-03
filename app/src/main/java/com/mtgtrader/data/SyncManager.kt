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
    /** Connected, but both this phone and Nextcloud hold data: the user picks a [FirstSync] first. */
    val firstChoice: FirstChoice? = null,
    val autoSync: Boolean = true,
    val wifiOnly: Boolean = false,
) {
    val connected get() = server != null
}

data class FirstChoice(val phone: SyncSummary, val nextcloud: SyncSummary)

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

/**
 * Keeps this phone in sync with the others through one file on the user's Nextcloud. With
 * automatic sync on, it syncs when the app opens, ~30 s after a change, when the app goes to
 * the background and every hour (only on Wi-Fi if chosen). "Sync now" always runs.
 */
class SyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val store: SyncStore,
    private val client: NextcloudClient,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var debounce: Job? = null

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<SyncStatus> = _status

    init {
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("collection", "binders", "trades", "trade_items", "decks", "deck_cards", "scans", "wishlist", "sync_deletions")) {
            override fun onInvalidated(tables: Set<String>) = onDataChanged()
        })
    }

    private fun readStatus() = SyncStatus(
        // Not connected if the password can't be read (e.g. app data restored onto another phone).
        server = prefs.getString(K_SERVER, null)?.takeIf { account() != null },
        user = prefs.getString(K_USER, null),
        lastSyncAt = prefs.getLong(K_LAST_SYNC, 0),
        lastError = prefs.getString(K_ERROR, null),
        ready = prefs.getBoolean(K_FIRST_DONE, false),
        autoSync = prefs.getBoolean(K_AUTO, true),
        wifiOnly = prefs.getBoolean(K_WIFI, false),
    )

    private fun account(): NextcloudAccount? {
        val server = prefs.getString(K_SERVER, null) ?: return null
        val login = prefs.getString(K_USER, null) ?: return null
        val pw = prefs.getString(K_PASSWORD, null)?.let(SecretStore::decrypt) ?: return null
        return NextcloudAccount(server, login, pw, prefs.getString(K_USER_ID, null) ?: login)
    }

    private val firstDone get() = prefs.getBoolean(K_FIRST_DONE, false)

    /** Whether an automatic sync may run now. */
    private fun autoAllowed(): Boolean {
        val s = _status.value
        return s.connected && firstDone && s.autoSync && (!s.wifiOnly || network.onUnmeteredNetwork())
    }

    // ---- Connecting ------------------------------------------------------------------------

    suspend fun startLogin(server: String) = client.startLogin(server)
    suspend fun pollLogin(start: NextcloudClient.LoginStart) = client.pollLogin(start)

    /**
     * Saves the account (after checking it works) and does the first sync, unless both sides
     * hold data: then [SyncStatus.firstChoice] asks the user what to do.
     */
    suspend fun connect(account: NextcloudAccount, fromLoginFlow: Boolean) {
        val checked = client.verify(account)
        prefs.edit()
            .putString(K_SERVER, checked.server)
            .putString(K_USER, checked.login)
            .putString(K_USER_ID, checked.userId)
            .putString(K_PASSWORD, SecretStore.encrypt(checked.password))
            .putBoolean(K_REVOKE, fromLoginFlow)
            .putBoolean(K_FIRST_DONE, false)
            .remove(K_ETAG).remove(K_STAMP).remove(K_PREFS_AT).remove(K_ERROR).remove(K_LAST_SYNC)
            .apply()
        _status.value = readStatus()
        schedule()
        prepareFirst()
    }

    /** Looks at both sides before the first sync; runs it right away unless the user has to choose. */
    suspend fun prepareFirst() {
        val account = account() ?: return
        val remote = try {
            when (val r = client.get(account, null)) {
                is NextcloudClient.Remote.Found -> SyncFile.decode(r.bytes).data
                else -> null
            }
        } catch (e: IOException) {
            _status.value = _status.value.copy(lastError = "Couldn't reach Nextcloud: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        val (local, _) = store.snapshot()
        // Only ask when this phone holds something Nextcloud doesn't (e.g. not when reconnecting the same phone).
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

    /** Forgets the account (and removes its app password from Nextcloud if this app created it). Data on the phone stays. */
    fun disconnect() {
        val account = account()
        val revoke = prefs.getBoolean(K_REVOKE, false)
        prefs.edit().clear().putBoolean(K_AUTO, _status.value.autoSync).putBoolean(K_WIFI, _status.value.wifiOnly).apply()
        _status.value = readStatus()
        schedule()
        if (account != null && revoke) scope.launch { runCatching { client.revoke(account) } }
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

    // ---- Syncing ---------------------------------------------------------------------------

    fun syncNow() {
        scope.launch { sync() }
    }

    /** Syncs once; errors end up in [SyncStatus.lastError]. Returns whether it succeeded. */
    suspend fun sync(first: FirstSync? = null): Boolean = mutex.withLock {
        val account = account() ?: return false
        if (first == null && !firstDone) return false
        _status.value = _status.value.copy(running = true)
        val error = try {
            runSync(account, first)
            null
        } catch (e: SyncException) {
            e.message
        } catch (e: IOException) {
            "Couldn't reach Nextcloud: ${e.message ?: e.javaClass.simpleName}"
        } catch (e: kotlinx.serialization.SerializationException) {
            "The file on Nextcloud couldn't be read."
        }
        val now = System.currentTimeMillis()
        prefs.edit().apply {
            if (error == null) putLong(K_LAST_SYNC, now).remove(K_ERROR) else putString(K_ERROR, error)
        }.apply()
        _status.value = _status.value.copy(
            running = false, lastError = error, ready = firstDone,
            lastSyncAt = if (error == null) now else _status.value.lastSyncAt,
        )
        error == null
    }

    private suspend fun runSync(account: NextcloudAccount, first: FirstSync?) {
        repeat(ATTEMPTS) {
            val (local, stamp) = store.snapshot()
            val unchanged = first == null && stamp == prefs.getLong(K_STAMP, -1) && local.prefs.updatedAt == prefs.getLong(K_PREFS_AT, -1)
            val remote = client.get(account, if (unchanged) prefs.getString(K_ETAG, null) else null)
            if (remote is NextcloudClient.Remote.NotModified) return
            val found = remote as? NextcloudClient.Remote.Found
            val remoteData = found?.let { SyncFile.decode(it.bytes).data }
            val now = System.currentTimeMillis()
            val merged = SyncMerge.merge(local, remoteData, now, first)

            if (merged != local && !store.apply(local, merged, stamp)) return@repeat // edited meanwhile: start over
            var etag = found?.etag
            if (merged != remoteData) {
                val file = SyncFile(writtenAt = now, writtenBy = "${Build.MANUFACTURER} ${Build.MODEL}", data = merged)
                when (val put = client.put(account, SyncFile.encode(file), found?.etag)) {
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
        const val K_ERROR = "lastError"
        const val K_AUTO = "auto"
        const val K_WIFI = "wifiOnly"
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
