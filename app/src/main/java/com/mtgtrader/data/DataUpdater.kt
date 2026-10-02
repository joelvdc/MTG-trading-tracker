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
) {
    /** Whether automatic updates may run right now. */
    fun allowedNow(): Boolean = settings.autoUpdate.value && (!settings.wifiOnly.value || network.onUnmeteredNetwork())

    /** Updates prices if they're out of date and automatic updates are allowed right now. */
    suspend fun autoUpdate() {
        if (allowedNow()) prices.refreshIfStale()
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
