package com.mtgtrader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One line of the price-alert history: a wishlist card's price reached its level. The pieces are kept
 * as fields; the text shown for a line is built by [AlertHistory.line]. Since 1.77.
 */
@Serializable
data class AlertEvent(
    /** The wishlist item the alert was on. */
    val wishId: Long,
    val cardName: String,
    /** When the level was reached, in epoch millis. */
    val at: Long,
    /** The price in euros when the level was reached. */
    val price: Double,
    /** The alert level in euros at the time. */
    val level: Double,
    /** Whether the price that reached the level was on CardTrader. */
    val onCardTrader: Boolean = false,
    /** CardTrader's offer: its set, condition and language (as in [AlertNotifier.show]). */
    val ctSetCode: String? = null,
    val ctCondition: String? = null,
    val ctLanguage: String? = null,
    /** What the price was ([PriceAlerts.basis]), shown with [PriceAlerts.basisLong]. */
    val basis: String = "cardmarket:low",
    /** The printing's set, for "any printing" cards. */
    val setCode: String? = null,
)

/**
 * The price alerts that were reached, kept on this phone only (like [WishPrice]: each phone checks
 * prices itself), in SharedPreferences as JSON: at most [MAX_LINES] lines, nothing older than 366
 * days, pruned on each write. Not in the database, so it isn't synced. Since 1.77.
 */
class AlertHistory(context: Context) {

    private val prefs = context.getSharedPreferences("alert_history", Context.MODE_PRIVATE)

    private val _events = MutableStateFlow(decode(prefs.getString(KEY, null)))
    /** Every line, newest first. */
    val events: StateFlow<List<AlertEvent>> = _events

    /** Writes a line down, pruning what has become too many or too old. */
    fun add(event: AlertEvent) {
        val next = prune(listOf(event) + _events.value, System.currentTimeMillis())
        _events.value = next
        prefs.edit().putString(KEY, encode(next)).apply()
    }

    /** Every line, newest first. */
    fun all(): List<AlertEvent> = _events.value

    /** This card's lines, newest first. */
    fun forItem(id: Long): List<AlertEvent> = _events.value.filter { it.wishId == id }

    companion object {

        /** At most this many lines are kept. */
        const val MAX_LINES = 200
        /** Nothing older than this is kept: a year, plus a day for leap years. */
        const val MAX_AGE_MS = 366L * 24 * 3600 * 1000

        private const val KEY = "events"
        private val list = ListSerializer(AlertEvent.serializer())
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** The lines to keep: nothing older than 366 days, at most [MAX_LINES], newest first. */
        fun prune(events: List<AlertEvent>, now: Long): List<AlertEvent> =
            events.filter { now - it.at <= MAX_AGE_MS }.sortedByDescending { it.at }.take(MAX_LINES)

        fun encode(events: List<AlertEvent>): String = json.encodeToString(list, events)
        fun decode(s: String?): List<AlertEvent> = s?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() } ?: emptyList()

        /** Where the price was: "CardTrader (2X2, SP, EN)", or "Cardmarket's lowest listing (MH2)". */
        fun where(e: AlertEvent): String = if (e.onCardTrader) {
            "CardTrader" + listOfNotNull(e.ctSetCode?.uppercase(), CtCondition.short(e.ctCondition).ifEmpty { null }, e.ctLanguage?.uppercase()).joinToString(", ", " (", ")")
        } else {
            PriceAlerts.basisLong(e.basis) + (e.setCode?.let { " (${it.uppercase()})" } ?: "")
        }

        /**
         * The line shown for [e]: "Reached €11.90 on 2 Nov · your level €12.00 · Cardmarket's lowest
         * listing". [date] and [money] format the pieces, so this stays testable without Android.
         */
        fun line(e: AlertEvent, date: (Long) -> String, money: (Double) -> String): String =
            "Reached ${money(e.price)} on ${date(e.at)} · your level ${money(e.level)} · ${where(e)}"
    }
}
