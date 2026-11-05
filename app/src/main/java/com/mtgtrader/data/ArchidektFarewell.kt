package com.mtgtrader.data

import android.content.Context
import androidx.work.WorkManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 1.78 stopped logging in to Archidekt (no collection sync, no sending deck edits). Once, on the first start of
 * 1.78 or later: the logged-in username becomes "your Archidekt username" (if none is set yet), the saved login
 * and the collection sync's settings are removed from the phone, and a background sync still queued is cancelled.
 * The collection sync's other files are left alone: they're small and harmless.
 */
object ArchidektFarewell {
    private const val PREFS = "archidekt"
    private const val K_LOGIN = "login"
    private const val K_DONE = "farewell178"
    private const val WORK = "archidekt-sync"

    fun run(context: Context, settings: Settings) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(K_DONE, false)) return
        val username = prefs.getString(K_LOGIN, null)?.let(SecretStore::decrypt)?.let(::usernameOf)
        if (username != null && settings.ownArchidektUser.value.isBlank()) settings.setOwnArchidektUser(username)
        prefs.edit().clear().putBoolean(K_DONE, true).apply()
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
    }

    /** The username in a saved login (JSON with "username"), or null. */
    fun usernameOf(loginJson: String): String? = runCatching {
        Json.parseToJsonElement(loginJson).jsonObject["username"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
