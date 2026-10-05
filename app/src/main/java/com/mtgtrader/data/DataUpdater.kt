package com.mtgtrader.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtgtrader.container
import java.util.concurrent.TimeUnit

/**
 * Keeps Cardmarket prices fresh (daily, ~26 MB). With automatic updates on, this runs when the
 * app opens, when the phone reconnects, and in the background every few hours — only on Wi-Fi
 * if the user chose so. "Update prices now" in Settings always runs.
 */
class DataUpdater(
    private val context: Context,
    private val settings: Settings,
    private val prices: PriceGuideRepository,
    private val network: NetworkMonitor,
    private val catalog: CardmarketCatalog,
    private val history: ValueHistory,
    private val cardDetails: CardDetails,
) {
    /** Whether automatic updates may run right now. */
    fun allowedNow(): Boolean = settings.autoUpdate.value && (!settings.wifiOnly.value || network.onUnmeteredNetwork())

    /**
     * Updates prices (daily) and Cardmarket's product list (weekly) if they're out of date and
     * automatic updates are allowed right now; then fills in missing Cardmarket links and saves
     * today's collection value.
     */
    suspend fun autoUpdate() {
        if (allowedNow()) {
            prices.refreshIfStale()
            if (catalog.isStale) catalog.refresh()
        }
        afterUpdate()
    }

    /** After prices changed (or on opening the app): missing links, today's value. */
    suspend fun afterUpdate() {
        runCatching { catalog.repair() }
        runCatching { history.record() }
        // Colours, types and popularity for sorting, filters and the trade binder (once per card).
        runCatching { cardDetails.fillMissing() }
    }

    /** Sets up (or cancels) the background update to match the settings. */
    fun schedule() {
        val wm = WorkManager.getInstance(context)
        if (!settings.autoUpdate.value) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "cardmarket-update"
    }
}

/** Background price update; see [DataUpdater.schedule]. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.container.updater.autoUpdate()
        return Result.success()
    }
}
