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

/** Light or dark look: as the phone is set, or always one of them. Since 1.16. */
enum class ThemeMode(val label: String) {
    SYSTEM("Same as the phone"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _priceType = MutableStateFlow(PriceType.fromKey(prefs.getString("priceType", null)))
    val priceType: StateFlow<PriceType> = _priceType

    private val _priceSource = MutableStateFlow(PriceSource.fromKey(prefs.getString("priceSource", null)))
    /** Where the price of a card comes from (synced). Since 1.29. */
    val priceSource: StateFlow<PriceSource> = _priceSource

    fun setPriceSource(s: PriceSource) {
        _priceSource.value = s
        prefs.edit().putString("priceSource", s.key).stamp().apply()
    }

    private val _currency = MutableStateFlow(AppCurrency.fromKey(prefs.getString("currency", null)))
    /** The currency prices are shown in (synced). Since 1.28. */
    val currency: StateFlow<AppCurrency> = _currency

    fun setCurrency(c: AppCurrency) {
        _currency.value = c
        prefs.edit().putString("currency", c.name).stamp().apply()
    }

    private val _tolerance = MutableStateFlow(prefs.getInt("tolerancePct", 5))
    val tolerancePct: StateFlow<Int> = _tolerance

    private val _lastFetch = MutableStateFlow(prefs.getLong("lastPriceFetch", 0L))
    val lastPriceFetch: StateFlow<Long> = _lastFetch

    private val _guideDate = MutableStateFlow(prefs.getString("priceGuideDate", null))
    val priceGuideDate: StateFlow<String?> = _guideDate

    private val _themeMode = MutableStateFlow(ThemeMode.fromKey(prefs.getString("themeMode", null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(v: ThemeMode) {
        _themeMode.value = v
        prefs.edit().putString("themeMode", v.name).apply()
    }

    private val _deckPrintingsOff = MutableStateFlow(decodeIds(prefs.getString("deckPrintingsOff", null)))
    /** Decks shown in Archidekt's printings rather than the ones you own (on by default). Since 1.22. */
    val deckPrintingsOff: StateFlow<Set<Long>> = _deckPrintingsOff

    fun setDeckShowsMyPrintings(deckId: Long, on: Boolean) {
        val v = if (on) _deckPrintingsOff.value - deckId else _deckPrintingsOff.value + deckId
        _deckPrintingsOff.value = v
        prefs.edit().putString("deckPrintingsOff", v.joinToString(",")).stamp().apply()
    }

    private val _deckPicks = MutableStateFlow(decodePicks(prefs.getString("deckPicks", null)))
    /** Deck id → card name (see [DeckPrintings.nameKey]) → the copy you picked to show ([DeckPrintings.pickValue] or [DeckPrintings.ARCHIDEKT]). Since 1.22. */
    val deckPicks: StateFlow<Map<Long, Map<String, String>>> = _deckPicks

    fun setDeckPick(deckId: Long, nameKey: String, value: String?) {
        val forDeck = _deckPicks.value[deckId].orEmpty().let { if (value == null) it - nameKey else it + (nameKey to value) }
        val v = if (forDeck.isEmpty()) _deckPicks.value - deckId else _deckPicks.value + (deckId to forDeck)
        _deckPicks.value = v
        prefs.edit().putString("deckPicks", encodePicks(v)).stamp().apply()
    }

    private val _cardOthersOpen = MutableStateFlow(prefs.getBoolean("cardOthersOpen", false))
    /** The card page lists all your other copies of the card, not just the first few. Since 1.21. */
    val cardOthersOpen: StateFlow<Boolean> = _cardOthersOpen

    fun setCardOthersOpen(v: Boolean) {
        _cardOthersOpen.value = v
        prefs.edit().putBoolean("cardOthersOpen", v).apply()
    }

    private val _tradeColumns = MutableStateFlow(prefs.getBoolean("tradeColumns", false))
    /** Show a trade's two sides next to each other (give left, get right) instead of one above the other. Since 1.21. */
    val tradeColumns: StateFlow<Boolean> = _tradeColumns

    fun setTradeColumns(v: Boolean) {
        _tradeColumns.value = v
        prefs.edit().putBoolean("tradeColumns", v).apply()
    }

    /** The language the scanner adds cards in ("" = as read from the card, else e.g. "DE"). Since 1.16. */
    var scanLanguage: String
        get() = prefs.getString("scanLanguage", "") ?: ""
        set(v) = prefs.edit().putString("scanLanguage", v).apply()

    /** When Cardmarket's product list was last downloaded; see [CardmarketCatalog]. Since 1.16. */
    var catalogFetchedAt: Long
        get() = prefs.getLong("catalogFetchedAt", 0L)
        set(v) = prefs.edit().putLong("catalogFetchedAt", v).apply()

    /** How far saved cards have been upgraded; see [MtgRepository.backfillFinishDetails]. */
    var cardDetailsVersion: Int
        get() = prefs.getInt("cardDetailsVersion", 0)
        set(v) = prefs.edit().putInt("cardDetailsVersion", v).apply()

    /** The Archidekt username last used to import decks from. */
    var archidektUser: String
        get() = prefs.getString("archidektUser", "") ?: ""
        set(v) = prefs.edit().putString("archidektUser", v).stamp().apply()

    /** The trade binder (see [TradeBinder]); 0 until it's made. Since 1.18. */
    var tradeBinderId: Long
        get() = prefs.getLong("tradeBinderId", 0)
        set(v) = prefs.edit().putLong("tradeBinderId", v).apply()

    var tradeRules: TradeBinderRules
        get() = TradeBinderRules(
            prefs.getInt("tradeMax", 100), prefs.getBoolean("tradeKeepOne", true), prefs.getFloat("tradeMinValue", 1f).toDouble(),
            prefs.getBoolean("tradeKeepBestDecks", true), prefs.getBoolean("tradeKeepBestAll", false),
        )
        set(v) = prefs.edit().putInt("tradeMax", v.maxCards).putBoolean("tradeKeepOne", v.keepOne).putFloat("tradeMinValue", v.minValue.toFloat())
            .putBoolean("tradeKeepBestDecks", v.keepBestForDecks).putBoolean("tradeKeepBestAll", v.keepBestAlways).stamp().apply()

    /** Where deck recommendations come from. Since 1.18. */
    private val _recSource = MutableStateFlow(RecSource.fromKey(prefs.getString("recSource", null)))
    val recSource: StateFlow<RecSource> = _recSource

    fun setRecSource(v: RecSource) {
        _recSource.value = v
        prefs.edit().putString("recSource", v.key).stamp().apply()
    }

    /** The collection's sort levels. Since 1.18. */
    private val _collectionSort = MutableStateFlow(SortSpec.decode(prefs.getString("collectionSort", null)))
    val collectionSort: StateFlow<SortSpec> = _collectionSort

    fun setCollectionSort(v: SortSpec) {
        _collectionSort.value = v
        prefs.edit().putString("collectionSort", v.encode()).stamp().apply()
    }

    /** Order of the Decks tab. */
    var deckSort: DeckSort
        get() = DeckSort.fromKey(prefs.getString("deckSort", null))
        set(v) = prefs.edit().putString("deckSort", v.name).stamp().apply()

    var deckSortReversed: Boolean
        get() = prefs.getBoolean("deckSortReversed", false)
        set(v) = prefs.edit().putBoolean("deckSortReversed", v).stamp().apply()

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

    /** Where deck power levels come from. Since 1.12. */
    private val _powerSource = MutableStateFlow(PowerSource.fromKey(prefs.getString("powerSource", null)))
    val powerSource: StateFlow<PowerSource> = _powerSource

    fun setPowerSource(v: PowerSource) {
        _powerSource.value = v
        prefs.edit().putString("powerSource", v.name).stamp().apply()
    }

    fun setCollectionView(v: CollectionView) {
        _collectionView.value = v
        prefs.edit().putString("collectionView", v.name).stamp().apply()
    }

    fun setPriceType(t: PriceType) {
        _priceType.value = t
        prefs.edit().putString("priceType", t.key).stamp().apply()
    }

    fun setTolerance(pct: Int) {
        _tolerance.value = pct
        prefs.edit().putInt("tolerancePct", pct).stamp().apply()
    }

    /** Preferences that follow the user to their other phone through sync (with when they last changed). */
    fun syncedPrefs() = SyncPrefs(
        updatedAt = prefs.getLong(PREFS_UPDATED_AT, 0L),
        values = buildMap {
            put("priceType", priceType.value.key)
            put("currency", currency.value.name)
            put("priceSource", priceSource.value.key)
            put("tolerancePct", tolerancePct.value.toString())
            put("archidektUser", archidektUser)
            put("deckSort", deckSort.name)
            put("deckSortReversed", deckSortReversed.toString())
            put("collectionView", collectionView.value.name)
            put("powerSource", powerSource.value.name)
            put("deckPrintingsOff", _deckPrintingsOff.value.joinToString(","))
            put("deckPicks", encodePicks(_deckPicks.value))
        },
    )

    /** Takes over preferences that came in through sync (without stamping them as changed here). */
    fun applySyncedPrefs(p: SyncPrefs) {
        val v = p.values
        val e = prefs.edit()
        v["priceType"]?.let { _priceType.value = PriceType.fromKey(it); e.putString("priceType", it) }
        v["priceSource"]?.let { _priceSource.value = PriceSource.fromKey(it); e.putString("priceSource", it) }
        v["currency"]?.let { _currency.value = AppCurrency.fromKey(it); e.putString("currency", it) }
        v["tolerancePct"]?.toIntOrNull()?.let { _tolerance.value = it; e.putInt("tolerancePct", it) }
        v["archidektUser"]?.let { e.putString("archidektUser", it) }
        v["deckSort"]?.let { e.putString("deckSort", it) }
        v["deckSortReversed"]?.toBooleanStrictOrNull()?.let { e.putBoolean("deckSortReversed", it) }
        v["collectionView"]?.let { _collectionView.value = CollectionView.fromKey(it); e.putString("collectionView", it) }
        v["powerSource"]?.let { _powerSource.value = PowerSource.fromKey(it); e.putString("powerSource", it) }
        v["deckPrintingsOff"]?.let { _deckPrintingsOff.value = decodeIds(it); e.putString("deckPrintingsOff", it) }
        v["deckPicks"]?.let { _deckPicks.value = decodePicks(it); e.putString("deckPicks", it) }
        e.putLong(PREFS_UPDATED_AT, p.updatedAt).apply()
    }

    private fun android.content.SharedPreferences.Editor.stamp() = putLong(PREFS_UPDATED_AT, System.currentTimeMillis())

    fun setPriceGuideFetched(createdAt: String?, at: Long) {
        _lastFetch.value = at
        _guideDate.value = createdAt
        prefs.edit().putLong("lastPriceFetch", at).putString("priceGuideDate", createdAt).apply()
    }

    private companion object {
        const val PREFS_UPDATED_AT = "syncedPrefsUpdatedAt"
        private val picksSerializer = kotlinx.serialization.builtins.MapSerializer(
            kotlinx.serialization.serializer<String>(),
            kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), kotlinx.serialization.serializer<String>()),
        )

        fun decodeIds(s: String?): Set<Long> = s.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

        fun encodePicks(p: Map<Long, Map<String, String>>): String =
            kotlinx.serialization.json.Json.encodeToString(picksSerializer, p.mapKeys { it.key.toString() })

        fun decodePicks(s: String?): Map<Long, Map<String, String>> = runCatching {
            kotlinx.serialization.json.Json.decodeFromString(picksSerializer, s!!).mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }.toMap()
        }.getOrDefault(emptyMap())
    }
}
