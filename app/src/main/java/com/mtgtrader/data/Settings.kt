package com.mtgtrader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ways to show the collection: the regular list, one text line per card, or a grid of card pictures. */
enum class CollectionView(val label: String) {
    LIST("List"),
    COMPACT("Compact (text only)"),
    GRID("Cards (big pictures)");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.name == key } ?: LIST
    }
}

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

    /** Order of the Decks tab. */
    var deckSort: DeckSort
        get() = DeckSort.fromKey(prefs.getString("deckSort", null))
        set(v) = prefs.edit().putString("deckSort", v.name).apply()

    var deckSortReversed: Boolean
        get() = prefs.getBoolean("deckSortReversed", false)
        set(v) = prefs.edit().putBoolean("deckSortReversed", v).apply()

    /** Update prices by themselves (on opening the app and in the background). Since 1.11. */
    private val _autoUpdate = MutableStateFlow(prefs.getBoolean("autoUpdate", true))
    val autoUpdate: StateFlow<Boolean> = _autoUpdate

    /** Automatic updates only on Wi-Fi (or another unmetered connection). */
    private val _wifiOnly = MutableStateFlow(prefs.getBoolean("wifiOnly", false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly

    /** How the Collection tab shows cards. */
    private val _collectionView = MutableStateFlow(CollectionView.fromKey(prefs.getString("collectionView", null)))
    val collectionView: StateFlow<CollectionView> = _collectionView

    fun setAutoUpdate(on: Boolean) {
        _autoUpdate.value = on
        prefs.edit().putBoolean("autoUpdate", on).apply()
    }

    fun setWifiOnly(on: Boolean) {
        _wifiOnly.value = on
        prefs.edit().putBoolean("wifiOnly", on).apply()
    }

    fun setCollectionView(v: CollectionView) {
        _collectionView.value = v
        prefs.edit().putString("collectionView", v.name).apply()
    }

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
