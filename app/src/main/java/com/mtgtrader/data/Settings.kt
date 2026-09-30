package com.mtgtrader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _priceType = MutableStateFlow(PriceType.fromKey(prefs.getString("priceType", null)))
    val priceType: StateFlow<PriceType> = _priceType

    private val _tolerance = MutableStateFlow(prefs.getInt("tolerancePct", 5))
    val tolerancePct: StateFlow<Int> = _tolerance

    private val _lastFetch = MutableStateFlow(prefs.getLong("lastPriceFetch", 0L))
    val lastPriceFetch: StateFlow<Long> = _lastFetch

    private val _guideDate = MutableStateFlow(prefs.getString("priceGuideDate", null))
    val priceGuideDate: StateFlow<String?> = _guideDate

    /** How far saved cards have been upgraded; see [MtgRepository.backfillFinishDetails]. */
    var cardDetailsVersion: Int
        get() = prefs.getInt("cardDetailsVersion", 0)
        set(v) = prefs.edit().putInt("cardDetailsVersion", v).apply()

    /** The Archidekt username last used to import decks from. */
    var archidektUser: String
        get() = prefs.getString("archidektUser", "") ?: ""
        set(v) = prefs.edit().putString("archidektUser", v).apply()

    fun setPriceType(t: PriceType) {
        _priceType.value = t
        prefs.edit().putString("priceType", t.key).apply()
    }

    fun setTolerance(pct: Int) {
        _tolerance.value = pct
        prefs.edit().putInt("tolerancePct", pct).apply()
    }

    fun setPriceGuideFetched(createdAt: String?, at: Long) {
        _lastFetch.value = at
        _guideDate.value = createdAt
        prefs.edit().putLong("lastPriceFetch", at).putString("priceGuideDate", createdAt).apply()
    }
}
