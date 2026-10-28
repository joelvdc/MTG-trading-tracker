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

    /** Deck pages as one slim line per card, like the collection's compact view (this phone only). Since 1.42. */
    private val _deckCompact = MutableStateFlow(prefs.getBoolean("deckCompact", false))
    val deckCompact: StateFlow<Boolean> = _deckCompact

    fun setDeckCompact(on: Boolean) {
        _deckCompact.value = on
        prefs.edit().putBoolean("deckCompact", on).apply()
    }

    /** Deck pages list the maybeboard under the deck (this phone only). Since 1.42. */
    private val _deckMaybeboard = MutableStateFlow(prefs.getBoolean("deckMaybeboard", false))
    val deckMaybeboard: StateFlow<Boolean> = _deckMaybeboard

    fun setDeckMaybeboard(on: Boolean) {
        _deckMaybeboard.value = on
        prefs.edit().putBoolean("deckMaybeboard", on).apply()
    }

    /**
     * Section headings folded away on deck pages, e.g. "Lands", for every deck that has them (this phone
     * only). Since 1.42.
     */
    private val _deckCollapsed = MutableStateFlow(prefs.getStringSet("deckCollapsed", null).orEmpty().toSet())
    val deckCollapsed: StateFlow<Set<String>> = _deckCollapsed

    fun setDeckCollapsed(titles: Set<String>) {
        _deckCollapsed.value = titles
        prefs.edit().putStringSet("deckCollapsed", titles).apply()
    }

    /** Which of the deck pages' "Stats" and "Tokens" sections are open ([DeckStats.STATS], [DeckStats.TOKENS]); folded until opened (this phone only). Since 1.44. */
    private val _deckStatsOpen = MutableStateFlow(prefs.getStringSet("deckStatsOpen", null).orEmpty().toSet())
    val deckStatsOpen: StateFlow<Set<String>> = _deckStatsOpen

    fun setDeckStatsOpen(keys: Set<String>) {
        _deckStatsOpen.value = keys
        prefs.edit().putStringSet("deckStatsOpen", keys).apply()
    }

    /** Cards added to a deck in the app go into Archidekt's auto category (this phone only). Since 1.46. */
    private val _deckAutoCategories = MutableStateFlow(prefs.getBoolean("deckAutoCategories", true))
    val deckAutoCategories: StateFlow<Boolean> = _deckAutoCategories

    fun setDeckAutoCategories(on: Boolean) {
        _deckAutoCategories.value = on
        prefs.edit().putBoolean("deckAutoCategories", on).apply()
    }

    /** The deck stats' mana production counts lands only (this phone only). Since 1.44. */
    private val _deckLandsOnly = MutableStateFlow(prefs.getBoolean("deckLandsOnly", false))
    val deckLandsOnly: StateFlow<Boolean> = _deckLandsOnly

    fun setDeckLandsOnly(on: Boolean) {
        _deckLandsOnly.value = on
        prefs.edit().putBoolean("deckLandsOnly", on).apply()
    }

    /** The deck stats' mana production shows colourless sources (this phone only). Since 1.44. */
    private val _deckColourless = MutableStateFlow(prefs.getBoolean("deckColourless", true))
    val deckColourless: StateFlow<Boolean> = _deckColourless

    fun setDeckColourless(on: Boolean) {
        _deckColourless.value = on
        prefs.edit().putBoolean("deckColourless", on).apply()
    }

    private val _deckTokenPicks = MutableStateFlow(decodePicks(prefs.getString("deckTokenPicks", null)))
    /** Deck id → token ([DeckToken.key]) → the printing (Scryfall id) picked to show for that deck; synced. Since 1.45. */
    val deckTokenPicks: StateFlow<Map<Long, Map<String, String>>> = _deckTokenPicks

    fun setDeckTokenPick(deckId: Long, tokenKey: String, printingId: String?) {
        val forDeck = _deckTokenPicks.value[deckId].orEmpty().let { if (printingId == null) it - tokenKey else it + (tokenKey to printingId) }
        val v = if (forDeck.isEmpty()) _deckTokenPicks.value - deckId else _deckTokenPicks.value + (deckId to forDeck)
        _deckTokenPicks.value = v
        prefs.edit().putString("deckTokenPicks", encodePicks(v)).stamp().apply()
    }

    private val _deckHidden = MutableStateFlow(decodePicks(prefs.getString("deckHidden", null)))
    /**
     * Deck id → categories switched off in "Manage categories" (lower case → the name): left out of the deck page's
     * category sections (their cards go to "Hidden categories") and of the category lists; synced. Since 1.47.
     */
    val deckHidden: StateFlow<Map<Long, Map<String, String>>> = _deckHidden

    fun isDeckHidden(deckId: Long, category: String) = _deckHidden.value[deckId]?.containsKey(category.lowercase()) == true

    fun setDeckHidden(deckId: Long, category: String, hidden: Boolean) {
        val key = category.lowercase()
        val forDeck = _deckHidden.value[deckId].orEmpty().let { if (hidden) it + (key to category) else it - key }
        if (forDeck == _deckHidden.value[deckId].orEmpty()) return
        val v = if (forDeck.isEmpty()) _deckHidden.value - deckId else _deckHidden.value + (deckId to forDeck)
        _deckHidden.value = v
        prefs.edit().putString("deckHidden", encodePicks(v)).stamp().apply()
    }

    /** A deck that got a new id (detached, or a local deck sent to Archidekt) keeps its hidden categories. */
    fun moveDeckHidden(from: Long, to: Long) {
        val forDeck = _deckHidden.value[from] ?: return
        val v = _deckHidden.value - from + (to to forDeck)
        _deckHidden.value = v
        prefs.edit().putString("deckHidden", encodePicks(v)).stamp().apply()
    }

    /** A renamed category stays hidden under its new name. */
    fun renameDeckHidden(deckId: Long, from: String, to: String) {
        if (!isDeckHidden(deckId, from)) return
        setDeckHidden(deckId, from, false)
        setDeckHidden(deckId, to, true)
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

    /** The currency last chosen for typing a price (per phone, not synced; null until chosen). Since 1.40. */
    var typedCurrency: String?
        get() = prefs.getString("typedCurrency", null)
        set(v) = prefs.edit().putString("typedCurrency", v).apply()

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

    /** Printing the rule-zero cards (per phone): which cards ("BRACKET", "POWER" or "BOTH") and ink saver. Since 1.65. */
    var printCards: String
        get() = prefs.getString("printCards", null) ?: "BOTH"
        set(v) = prefs.edit().putString("printCards", v).apply()

    var printInkSaver: Boolean
        get() = prefs.getBoolean("printInkSaver", false)
        set(v) = prefs.edit().putBoolean("printInkSaver", v).apply()

    /** Update prices by themselves (on opening the app and in the background). Since 1.11. */
    private val _autoUpdate = MutableStateFlow(prefs.getBoolean("autoUpdate", true))
    val autoUpdate: StateFlow<Boolean> = _autoUpdate

    /** Automatic updates only on Wi-Fi (or another unmetered connection). */
    private val _wifiOnly = MutableStateFlow(prefs.getBoolean("wifiOnly", false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly

    /** Save the pictures of decks (cards and covers) and the wishlist ahead, on Wi-Fi, so they show offline. Since 1.34. */
    private val _offlineImages = MutableStateFlow(prefs.getBoolean("offlineImages", true))
    val offlineImages: StateFlow<Boolean> = _offlineImages

    fun setOfflineImages(on: Boolean) {
        _offlineImages.value = on
        prefs.edit().putBoolean("offlineImages", on).apply()
    }

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

    private val _conditionAdjust = MutableStateFlow(prefs.getBoolean("conditionAdjust", false))
    /** Whether values count a copy's condition (synced). Off unless switched on. Since 1.33. */
    val conditionAdjust: StateFlow<Boolean> = _conditionAdjust

    private val _conditionPcts = MutableStateFlow(decodePcts(prefs.getString("conditionPcts", null)))
    /** Percent of the price per condition (synced). Since 1.33. */
    val conditionPcts: StateFlow<Map<String, Int>> = _conditionPcts

    fun setConditionAdjust(on: Boolean) {
        _conditionAdjust.value = on
        prefs.edit().putBoolean("conditionAdjust", on).stamp().apply()
    }

    fun setConditionPct(condition: String, pct: Int) {
        _conditionPcts.value = _conditionPcts.value + (condition to pct.coerceIn(0, 100))
        prefs.edit().putString("conditionPcts", encodePcts(_conditionPcts.value)).stamp().apply()
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

    private val _alertPriceType = MutableStateFlow(prefs.getString("alertPriceType", null)?.let(PriceType::fromKey) ?: PriceType.LOW)
    /** Cardmarket's price that wishlist alerts compare against (synced); the lowest listing unless changed. Since 1.37. */
    val alertPriceType: StateFlow<PriceType> = _alertPriceType

    fun setAlertPriceType(t: PriceType) {
        _alertPriceType.value = t
        prefs.edit().putString("alertPriceType", t.key).stamp().apply()
    }

    private val _cardTraderOn = MutableStateFlow(prefs.getBoolean("cardTraderOn", false))
    /** Wishlist cards also show (and alert on) CardTrader's cheapest offer (synced; the token isn't). Since 1.37. */
    val cardTraderOn: StateFlow<Boolean> = _cardTraderOn

    fun setCardTraderOn(on: Boolean) {
        _cardTraderOn.value = on
        prefs.edit().putBoolean("cardTraderOn", on).stamp().apply()
    }

    private val _ctLanguage = MutableStateFlow(prefs.getString("ctLanguage", null) ?: "en")
    /** The language CardTrader offers must be in ([CT_LANGUAGES]; "" any), synced. Since 1.37. */
    val ctLanguage: StateFlow<String> = _ctLanguage

    fun setCtLanguage(v: String) {
        _ctLanguage.value = v
        prefs.edit().putString("ctLanguage", v).stamp().apply()
    }

    private val _ctCondition = MutableStateFlow(CtCondition.fromKey(prefs.getString("ctCondition", null)))
    /** The worst condition a CardTrader offer may be in (synced). Since 1.37. */
    val ctCondition: StateFlow<CtCondition> = _ctCondition

    fun setCtCondition(v: CtCondition) {
        _ctCondition.value = v
        prefs.edit().putString("ctCondition", v.key).stamp().apply()
    }

    private val _ctZeroOnly = MutableStateFlow(prefs.getBoolean("ctZeroOnly", true))
    /** Only CardTrader Zero offers count (synced; on unless switched off). Since 1.37. */
    val ctZeroOnly: StateFlow<Boolean> = _ctZeroOnly

    fun setCtZeroOnly(on: Boolean) {
        _ctZeroOnly.value = on
        prefs.edit().putBoolean("ctZeroOnly", on).stamp().apply()
    }

    // ---- The life tracker (since 1.47; synced; most of it set in the game's own settings since 1.48) ----

    /** The tracker's on/off choices and their defaults. */
    private val trackerFlagDefaults = linkedMapOf(
        // Panels show commander pictures; off (the default since 1.48): plain colours. A player can switch their own.
        "trackerPictures" to false,
        // Life changes by tapping above (+) / below (−) the total; off: left (−) / right (+).
        "trackerAboveBelow" to false,
        // Holding counts by 1 (repeating); off: by 10, as Lifetap does.
        "trackerHoldOne" to false,
        // The defaults for new games, changed when the game's settings change (since 1.48 for most of them).
        "trackerTurns" to false,
        "trackerClock" to true,
        "trackerCommanderDamage" to true,
        "trackerCmdLifeLoss" to true,
        "trackerLog" to true,
        "trackerEnds" to false,
        // How the tracker looks and behaves (since 1.48).
        "trackerHaptics" to true,
        "trackerColourGrids" to true,
        "trackerAutoHide" to false,
        "trackerAnimations" to true,
        "trackerKeepAwake" to true,
        "trackerHideBars" to true,
    )
    private val trackerFlags = trackerFlagDefaults.mapValues { (k, d) -> MutableStateFlow(prefs.getBoolean(k, d)) }

    /** One of the tracker's on/off choices ([trackerFlagDefaults] keys). */
    fun trackerFlag(key: String): StateFlow<Boolean> = trackerFlags.getValue(key)

    val trackerPictures: StateFlow<Boolean> get() = trackerFlag("trackerPictures")
    val trackerAboveBelow: StateFlow<Boolean> get() = trackerFlag("trackerAboveBelow")
    val trackerHoldOne: StateFlow<Boolean> get() = trackerFlag("trackerHoldOne")
    val trackerTurns: StateFlow<Boolean> get() = trackerFlag("trackerTurns")
    val trackerClock: StateFlow<Boolean> get() = trackerFlag("trackerClock")

    private val _trackerLowLife = MutableStateFlow(LowLifeStyle.fromKey(prefs.getString("trackerLowLife", null)))
    /** The animation on a panel at low life. */
    val trackerLowLife: StateFlow<LowLifeStyle> = _trackerLowLife

    private val _trackerCardLayout = MutableStateFlow(CardLayout.fromKey(prefs.getString("trackerCardLayout", null)))
    /** What a player's panel shows (since 1.48). */
    val trackerCardLayout: StateFlow<CardLayout> = _trackerCardLayout

    private val _trackerWhenDown = MutableStateFlow(WhenDown.fromKey(prefs.getString("trackerWhenDown", null)))
    /** What happens when a player is down, for new games (since 1.48). */
    val trackerWhenDown: StateFlow<WhenDown> = _trackerWhenDown

    fun setTracker(key: String, on: Boolean) {
        val f = trackerFlags[key] ?: return
        f.value = on
        prefs.edit().putBoolean(key, on).stamp().apply()
    }

    fun setTrackerLowLife(v: LowLifeStyle) {
        _trackerLowLife.value = v
        prefs.edit().putString("trackerLowLife", v.key).stamp().apply()
    }

    fun setTrackerCardLayout(v: CardLayout) {
        _trackerCardLayout.value = v
        prefs.edit().putString("trackerCardLayout", v.key).stamp().apply()
    }

    fun setTrackerWhenDown(v: WhenDown) {
        _trackerWhenDown.value = v
        prefs.edit().putString("trackerWhenDown", v.key).stamp().apply()
    }

    private val _wishSections = MutableStateFlow(prefs.getBoolean("wishSections", true))
    /** The wishlist in sections (To buy, Ordered, Received); off: one list with ordered cards last (synced). Since 1.38. */
    val wishSections: StateFlow<Boolean> = _wishSections

    fun setWishSections(on: Boolean) {
        _wishSections.value = on
        prefs.edit().putBoolean("wishSections", on).stamp().apply()
    }

    private val _receivedClear = MutableStateFlow(ReceivedClear.fromKey(prefs.getString("receivedClear", null)))
    /** When received wishlist cards are cleared by themselves (synced). Since 1.38. */
    val receivedClear: StateFlow<ReceivedClear> = _receivedClear

    fun setReceivedClear(v: ReceivedClear) {
        _receivedClear.value = v
        prefs.edit().putString("receivedClear", v.key).stamp().apply()
    }

    private val _receivedToCollection = MutableStateFlow(prefs.getBoolean("receivedToCollection", false))
    /** A wishlist card marked as received is also added to the collection, in Unsorted (synced; off by default). Since 1.38. */
    val receivedToCollection: StateFlow<Boolean> = _receivedToCollection

    fun setReceivedToCollection(on: Boolean) {
        _receivedToCollection.value = on
        prefs.edit().putBoolean("receivedToCollection", on).stamp().apply()
    }

    private val _swipeRight = MutableStateFlow(SwipeAction.fromKey(prefs.getString("swipeRight", null), SwipeAction.DEFAULT_RIGHT))
    /** What swiping a collection card to the right does (synced). Since 1.38. */
    val swipeRight: StateFlow<SwipeAction> = _swipeRight

    private val _swipeLeft = MutableStateFlow(SwipeAction.fromKey(prefs.getString("swipeLeft", null), SwipeAction.DEFAULT_LEFT))
    /** What swiping a collection card to the left does (synced). Since 1.38. */
    val swipeLeft: StateFlow<SwipeAction> = _swipeLeft

    fun setSwipe(right: Boolean, v: SwipeAction) {
        if (right) _swipeRight.value = v else _swipeLeft.value = v
        prefs.edit().putString(if (right) "swipeRight" else "swipeLeft", v.key).stamp().apply()
    }

    private val _binderOrder = MutableStateFlow(BinderOrder.decode(prefs.getString("binderOrder", null)))
    /** The binder bar's order, as arranged ([BinderOrder] keys; synced). Empty until arranged. Since 1.39. */
    val binderOrder: StateFlow<List<String>> = _binderOrder

    fun setBinderOrder(keys: List<String>) {
        _binderOrder.value = keys
        prefs.edit().putString("binderOrder", BinderOrder.encode(keys)).stamp().apply()
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
            put("deckTokenPicks", encodePicks(_deckTokenPicks.value))
            put("deckHidden", encodePicks(_deckHidden.value))
            put("conditionAdjust", conditionAdjust.value.toString())
            put("conditionPcts", encodePcts(conditionPcts.value))
            put("alertPriceType", alertPriceType.value.key)
            put("cardTraderOn", cardTraderOn.value.toString())
            put("ctLanguage", ctLanguage.value)
            put("ctCondition", ctCondition.value.key)
            put("ctZeroOnly", ctZeroOnly.value.toString())
            put("wishSections", wishSections.value.toString())
            put("receivedClear", receivedClear.value.key)
            put("receivedToCollection", receivedToCollection.value.toString())
            put("swipeRight", swipeRight.value.key)
            put("swipeLeft", swipeLeft.value.key)
            put("binderOrder", BinderOrder.encode(binderOrder.value))
            trackerFlags.forEach { (k, f) -> put(k, f.value.toString()) }
            put("trackerLowLife", trackerLowLife.value.key)
            put("trackerCardLayout", trackerCardLayout.value.key)
            put("trackerWhenDown", trackerWhenDown.value.key)
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
        v["deckTokenPicks"]?.let { _deckTokenPicks.value = decodePicks(it); e.putString("deckTokenPicks", it) }
        v["deckHidden"]?.let { _deckHidden.value = decodePicks(it); e.putString("deckHidden", it) }
        v["conditionAdjust"]?.toBooleanStrictOrNull()?.let { _conditionAdjust.value = it; e.putBoolean("conditionAdjust", it) }
        v["conditionPcts"]?.let { _conditionPcts.value = decodePcts(it); e.putString("conditionPcts", it) }
        v["alertPriceType"]?.let { _alertPriceType.value = PriceType.fromKey(it); e.putString("alertPriceType", it) }
        v["cardTraderOn"]?.toBooleanStrictOrNull()?.let { _cardTraderOn.value = it; e.putBoolean("cardTraderOn", it) }
        v["ctLanguage"]?.let { _ctLanguage.value = it; e.putString("ctLanguage", it) }
        v["ctCondition"]?.let { _ctCondition.value = CtCondition.fromKey(it); e.putString("ctCondition", it) }
        v["ctZeroOnly"]?.toBooleanStrictOrNull()?.let { _ctZeroOnly.value = it; e.putBoolean("ctZeroOnly", it) }
        v["wishSections"]?.toBooleanStrictOrNull()?.let { _wishSections.value = it; e.putBoolean("wishSections", it) }
        v["receivedClear"]?.let { _receivedClear.value = ReceivedClear.fromKey(it); e.putString("receivedClear", it) }
        v["receivedToCollection"]?.toBooleanStrictOrNull()?.let { _receivedToCollection.value = it; e.putBoolean("receivedToCollection", it) }
        v["swipeRight"]?.let { _swipeRight.value = SwipeAction.fromKey(it, SwipeAction.DEFAULT_RIGHT); e.putString("swipeRight", it) }
        v["swipeLeft"]?.let { _swipeLeft.value = SwipeAction.fromKey(it, SwipeAction.DEFAULT_LEFT); e.putString("swipeLeft", it) }
        v["binderOrder"]?.let { _binderOrder.value = BinderOrder.decode(it); e.putString("binderOrder", it) }
        trackerFlags.forEach { (k, f) -> v[k]?.toBooleanStrictOrNull()?.let { f.value = it; e.putBoolean(k, it) } }
        v["trackerLowLife"]?.let { _trackerLowLife.value = LowLifeStyle.fromKey(it); e.putString("trackerLowLife", it) }
        v["trackerCardLayout"]?.let { _trackerCardLayout.value = CardLayout.fromKey(it); e.putString("trackerCardLayout", it) }
        v["trackerWhenDown"]?.let { _trackerWhenDown.value = WhenDown.fromKey(it); e.putString("trackerWhenDown", it) }
        e.putLong(PREFS_UPDATED_AT, p.updatedAt).apply()
    }

    private fun android.content.SharedPreferences.Editor.stamp() = putLong(PREFS_UPDATED_AT, System.currentTimeMillis())

    fun setPriceGuideFetched(createdAt: String?, at: Long) {
        _lastFetch.value = at
        _guideDate.value = createdAt
        prefs.edit().putLong("lastPriceFetch", at).putString("priceGuideDate", createdAt).apply()
    }

    companion object {
        /** What a copy in each condition counts for until the owner changes it, in percent of the card's price. */
        val DEFAULT_CONDITION_PCTS = linkedMapOf("MT" to 100, "NM" to 100, "EX" to 85, "GD" to 70, "LP" to 60, "PL" to 45, "PO" to 30)

        fun encodePcts(p: Map<String, Int>): String = p.entries.joinToString(",") { "${it.key}=${it.value}" }

        /** "EX=85,GD=70": conditions it doesn't name keep their default. */
        fun decodePcts(s: String?): Map<String, Int> = DEFAULT_CONDITION_PCTS + s.orEmpty().split(',').mapNotNull { part ->
            val (k, v) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            v.trim().toIntOrNull()?.let { k.trim().uppercase() to it.coerceIn(0, 100) }
        }.filter { it.first in DEFAULT_CONDITION_PCTS }

        private const val PREFS_UPDATED_AT = "syncedPrefsUpdatedAt"
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
