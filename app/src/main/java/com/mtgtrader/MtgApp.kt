package com.mtgtrader

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
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
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { container.http }
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
        .build()
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
    val scryfall = ScryfallApi(http)
    val network = NetworkMonitor(context)
    val prices = PriceGuideRepository(context, http, db, settings)
    val setIcons = SetIcons(context, scryfall, appScope)
    val repo = MtgRepository(db, scryfall, prices, appScope)
    val catalog = CardmarketCatalog(context, http, db, settings, scryfall)
    val history = ValueHistory(db)
    val cardDetails = com.mtgtrader.data.CardDetails(db, scryfall)
    val symbols = SetSymbolMatcher(context, http, setIcons, scryfall)
    val updater = DataUpdater(context, settings, prices, network, catalog, history, cardDetails)
    private val browser = HiddenBrowser(context)
    val decks = DeckRepository(context, db, scryfall, ArchidektApi(http), CommanderSaltApi(http), repo, settings, EdhPowerLevelApi(browser), ScrollVaultApi(browser), appScope)
    val recommendations = com.mtgtrader.data.Recommendations(db, scryfall, com.mtgtrader.data.EdhrecApi(http), com.mtgtrader.data.RecommanderApi(http), appScope)
    val tradeBinder = com.mtgtrader.data.TradeBinder(db, repo, settings)
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
        sync.beforeFirstSync = { backups.before(com.mtgtrader.data.BackupReason.FIRST_NEXTCLOUD) }
        repo.beforeCsvImport = { backups.before(com.mtgtrader.data.BackupReason.CSV_IMPORT) }
    }

    /** A trade deleted on its own screen, so the trade list can offer Undo once it's back on screen. */
    @Volatile
    var deletedTrade: TradeWithItems? = null

    val gameChangers = com.mtgtrader.data.GameChangers(context, scryfall)

    /** A filtered view of the collection the stats screen asked for, until the collection screen shows it. Since 1.21. */
    val collectionJump = MutableStateFlow<CollectionJump?>(null)

    /** Text shared to the app (e.g. a deck link from Archidekt), waiting for the screens to handle it. */
    val sharedText = MutableStateFlow<String?>(null)
}

val Context.container: AppContainer get() = (applicationContext as MtgApp).container

/** Show the collection with this [filter] (and binder: null = all cards). */
data class CollectionJump(val filter: com.mtgtrader.data.CollectionFilter, val binderId: Long? = null)
