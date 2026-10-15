package com.mtgtrader

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.util.DebugLogger
import com.mtgtrader.data.AppDatabase
import com.mtgtrader.data.ArchidektApi
import com.mtgtrader.data.CommanderSaltApi
import com.mtgtrader.data.CardmarketCatalog
import com.mtgtrader.data.DataUpdater
import com.mtgtrader.data.ValueHistory
import com.mtgtrader.data.DeckRepository
import com.mtgtrader.data.EdhPowerLevelApi
import com.mtgtrader.data.HiddenBrowser
import com.mtgtrader.data.ScrollVaultApi
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.NetworkMonitor
import com.mtgtrader.data.PriceGuideRepository
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.SetIcons
import com.mtgtrader.data.Settings
import com.mtgtrader.data.NextcloudClient
import com.mtgtrader.data.SyncManager
import com.mtgtrader.data.SyncStore
import com.mtgtrader.data.TradeWithItems
import com.mtgtrader.scan.SetSymbolMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MtgApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // Card images come from Scryfall's CDN, which rejects OkHttp's default User-Agent.
    // Set symbols are SVGs.
    // A picture at a given address doesn't change, so a saved one is shown however old it is: Archidekt
    // says its deck covers may be kept for an hour or a day only, after which they wouldn't show offline.
    // The cache keeps the pictures of decks and the wishlist (see OfflineImages), so it gets a fixed size.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { container.http }
        .components { add(SvgDecoder.Factory()) }
        .respectCacheHeaders(false)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(IMAGE_CACHE_BYTES).build() }
        .crossfade(true)
        .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
        .build()

    private companion object {
        const val IMAGE_CACHE_BYTES = 600L * 1024 * 1024
    }
}

/** Hand-rolled dependency container; one instance for the whole app. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "MTGTraderAndroid/1.0").build())
        }
        .build()
    val db = AppDatabase.build(context)
    val settings = Settings(context)
    val exchangeRates = com.mtgtrader.data.ExchangeRateStore(context, http)
    val scryfall = ScryfallApi(http)
    val priceSources = com.mtgtrader.data.PriceSourceStore(context, http, db, scryfall)
    val network = NetworkMonitor(context)
    val prices = PriceGuideRepository(context, http, db, settings)
    val setIcons = SetIcons(context, scryfall, appScope)
    val repo = MtgRepository(db, scryfall, prices, appScope)
    val catalog = CardmarketCatalog(context, http, db, settings, scryfall)
    val history = ValueHistory(db)
    val precons = com.mtgtrader.data.PreconStore(http)
    val cardDetails = com.mtgtrader.data.CardDetails(db, scryfall)
    val symbols = SetSymbolMatcher(context, http, setIcons, scryfall)
    val updater = DataUpdater(context, settings, prices, network, catalog, history, cardDetails, priceSources)
    val cardTrader = com.mtgtrader.data.CardTrader(context, http, db)
    val wishWatch = com.mtgtrader.data.WishlistWatch(context, db, settings, scryfall, priceSources, cardTrader) { exchangeRates.rates.value }
    private val browser = HiddenBrowser(context)
    val decks = DeckRepository(context, db, scryfall, ArchidektApi(http), CommanderSaltApi(http), repo, settings, EdhPowerLevelApi(browser), ScrollVaultApi(browser), appScope)
    val recommendations = com.mtgtrader.data.Recommendations(db, scryfall, com.mtgtrader.data.EdhrecApi(http), com.mtgtrader.data.RecommanderApi(http), appScope)
    val tradeBinder = com.mtgtrader.data.TradeBinder(db, repo, settings)
    val offlineImages = com.mtgtrader.data.OfflineImages(context, db, settings, network, appScope)
    private val store = SyncStore(db, settings)
    private val nextcloud = NextcloudClient(http)
    val sync = SyncManager(context, db, store, nextcloud, network, appScope)
    val backups = com.mtgtrader.data.Backups(context, store, sync, nextcloud)
    val archidekt = com.mtgtrader.data.ArchidektSync(
        context, db, repo, settings, sync, nextcloud,
        com.mtgtrader.data.ArchidektCollectionClient(http) {
            context.getSharedPreferences("archidekt", Context.MODE_PRIVATE).getString("server", null) ?: com.mtgtrader.data.ArchidektCollectionClient.DEFAULT_BASE
        },
        backups, network, appScope,
    )

    init {
        // Prices show in the chosen currency at the latest rate (since 1.28), from the chosen source (since 1.29).
        // Set right away, so the first screen is right; afterwards the watchers below only pass on changes.
        val display = { cur: com.mtgtrader.data.AppCurrency, rates: com.mtgtrader.data.ExchangeRates? -> com.mtgtrader.data.DisplayCurrency.of(cur, rates) }
        val display0 = display(settings.currency.value, exchangeRates.rates.value)
        com.mtgtrader.data.Money.display = display0
        appScope.launch {
            kotlinx.coroutines.flow.combine(settings.currency, exchangeRates.rates, display)
                .dropWhile { it == display0 }
                .collect { com.mtgtrader.data.Money.display = it }
        }
        val source0 = settings.priceSource.value
        com.mtgtrader.data.Pricing.source = source0
        appScope.launch { settings.priceSource.dropWhile { it == source0 }.collect { com.mtgtrader.data.Pricing.source = it } }
        // Condition-adjusted values (since 1.33): the percentages while the setting is on, else nothing.
        val pcts = { on: Boolean, p: Map<String, Int> -> p.takeIf { on } }
        val pcts0 = pcts(settings.conditionAdjust.value, settings.conditionPcts.value)
        com.mtgtrader.data.Pricing.conditionPct = pcts0
        appScope.launch {
            kotlinx.coroutines.flow.combine(settings.conditionAdjust, settings.conditionPcts, pcts)
                .dropWhile { it == pcts0 }
                .collect { com.mtgtrader.data.Pricing.conditionPct = it }
        }
        val usd = { r: com.mtgtrader.data.ExchangeRates? -> r?.perEuro?.get("USD") }
        val usd0 = usd(exchangeRates.rates.value)
        com.mtgtrader.data.Pricing.usdPerEuro = usd0
        appScope.launch { exchangeRates.rates.map(usd).dropWhile { it == usd0 }.collect { com.mtgtrader.data.Pricing.usdPerEuro = it } }
        scryfall.onCards = { cards -> priceSources.fromScryfall(cards) }
        // Wishlist cards added offline get their details whenever the phone (re)connects.
        appScope.launch { network.reconnects.collect { runCatching { repo.completePendingWishlist() } } }
        // Wishlist price alerts after each price update (since 1.37).
        updater.onPrices = { online -> wishWatch.check(online = online) }
        sync.beforeFirstSync = { backups.before(com.mtgtrader.data.BackupReason.FIRST_NEXTCLOUD) }
        repo.beforeCsvImport = { backups.before(com.mtgtrader.data.BackupReason.CSV_IMPORT) }
        repo.beforeOrderImport = { backups.before(com.mtgtrader.data.BackupReason.ORDER_IMPORT) }
    }

    /** A trade deleted on its own screen, so the trade list can offer Undo once it's back on screen. */
    @Volatile
    var deletedTrade: TradeWithItems? = null

    val gameChangers = com.mtgtrader.data.GameChangers(context, scryfall)

    /** A filtered view of the collection the stats screen asked for, until the collection screen shows it. Since 1.21. */
    val collectionJump = MutableStateFlow<CollectionJump?>(null)

    /** A wishlist card to open (from a price alert's notification), until the collection screen shows it. Since 1.37. */
    val openWish = MutableStateFlow<Long?>(null)

    /** Text shared to the app (e.g. a deck link from Archidekt), waiting for the screens to handle it. */
    val sharedText = MutableStateFlow<String?>(null)
}

val Context.container: AppContainer get() = (applicationContext as MtgApp).container

/** Show the collection with this [filter] (and binder: null = all cards). */
data class CollectionJump(val filter: com.mtgtrader.data.CollectionFilter, val binderId: Long? = null)
