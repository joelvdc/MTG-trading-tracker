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
import com.mtgtrader.data.DataUpdater
import com.mtgtrader.data.DeckRepository
import com.mtgtrader.data.EdhPowerLevelApi
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
    val updater = DataUpdater(context, settings, prices, network)
    val decks = DeckRepository(context, db, scryfall, ArchidektApi(http), CommanderSaltApi(http), repo, settings, EdhPowerLevelApi(context), appScope)
    val sync = SyncManager(context, db, SyncStore(db, settings), NextcloudClient(http), network, appScope)

    /** A trade deleted on its own screen, so the trade list can offer Undo once it's back on screen. */
    @Volatile
    var deletedTrade: TradeWithItems? = null

    /** Text shared to the app (e.g. a deck link from Archidekt), waiting for the screens to handle it. */
    val sharedText = MutableStateFlow<String?>(null)
}

val Context.container: AppContainer get() = (applicationContext as MtgApp).container
