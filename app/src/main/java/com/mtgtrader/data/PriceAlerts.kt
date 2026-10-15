package com.mtgtrader.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One printing a wishlist card can be bought as: what's needed to price it at every source. Since 1.37. */
@Serializable
data class WishPrinting(
    val id: String,
    val set: String,
    val cm: Int? = null,
    val cmFoil: Int? = null,
    val nonfoil: Boolean = true,
    val foil: Boolean = false,
    val etched: Boolean = false,
) {
    /** The finish a wished copy has in this printing (etched for a foil only made etched), or null if it isn't made so. */
    fun finish(wantFoil: Boolean): Finish? = when {
        !wantFoil -> Finish.NONFOIL.takeIf { nonfoil }
        foil -> Finish.FOIL
        etched -> Finish.ETCHED
        else -> null
    }

    companion object {
        fun of(c: CardRef) = WishPrinting(c.scryfallId, c.setCode, c.cardmarketId, c.cardmarketFoilId, c.hasNonFoil, c.hasFoil, c.hasEtched)
        fun of(c: ScryCard) = WishPrinting(c.id, c.set, c.cardmarketId, null, c.hasNonFoil, c.hasFoil, c.hasEtched)

        private val list = ListSerializer(serializer())
        fun encode(p: List<WishPrinting>): String = Json.encodeToString(list, p)
        fun decode(s: String?): List<WishPrinting>? = s?.let { runCatching { Json.decodeFromString(list, it) }.getOrNull() }
    }
}

/**
 * A wishlist card's latest prices for its alert, kept on this phone only (each phone looks prices up
 * itself, and notifies on its own). Since 1.37.
 */
@Entity(tableName = "wish_prices")
data class WishPrice(
    @PrimaryKey val wishId: Long,
    /** What [price] is ([PriceAlerts.basis]); after a change in Settings it's left unused until the next check. */
    val basis: String = "",
    /** The cheapest printing's price at the chosen source, in euros, and that printing. */
    val price: Double? = null,
    val scryfallId: String? = null,
    val setCode: String? = null,
    /** "Any printing": the card's paper printings ([WishPrinting], JSON), looked up daily for [printingsOf]. */
    val printings: String? = null,
    val printingsOf: String? = null,
    val printingsAt: Long = 0,
    /** CardTrader's cheapest matching offer, in euros, and where it is (a blueprint's page, see [CardTrader.pageUrl]). */
    val ctPrice: Double? = null,
    val ctBlueprintId: Long? = null,
    val ctSetCode: String? = null,
    val ctCondition: String? = null,
    val ctLanguage: String? = null,
    /** With "only CardTrader Zero": the cheapest offer from any seller, and (without a CT Zero offer) what one costs at least. */
    val ctAnyPrice: Double? = null,
    val ctZeroOver: Double? = null,
    /** The filter [ctPrice] was found with ([CtFilter.key]); after a change it's left unused until looked up again. */
    val ctFilter: String? = null,
    val ctAt: Long = 0,
    /** The level a notification went out for, until the price is above it again. */
    val alertedLevel: Double? = null,
    val checkedAt: Long = 0,
)

@Dao
interface WishPriceDao {
    @Query("SELECT * FROM wish_prices")
    fun observeAll(): Flow<List<WishPrice>>

    @Query("SELECT * FROM wish_prices WHERE wishId = :id")
    suspend fun get(id: Long): WishPrice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: WishPrice)

    @Query("DELETE FROM wish_prices WHERE wishId NOT IN (:ids)")
    suspend fun keepOnly(ids: List<Long>)

    @Query("DELETE FROM wish_prices")
    suspend fun clear()
}

/** The rules of price alerts, apart from the phone, so they can be tested. Since 1.37. */
object PriceAlerts {
    /** What a stored price is: "cardmarket:low" (with the price type) or the source's key. */
    fun basis(source: PriceSource, type: PriceType) = if (source == PriceSource.CARDMARKET) "cardmarket:${type.key}" else source.key

    /** How a stored price is named on a card: "Low", "Trend", "TCGplayer"… */
    fun basisLabel(basis: String): String = when {
        basis.startsWith("cardmarket:") -> PriceType.fromKey(basis.substringAfter(':')).short
        else -> PriceSource.fromKey(basis).label
    }

    /** "Cardmarket's lowest listing", "TCGplayer's market price"…: for a notification and the card's window. */
    fun basisLong(basis: String): String = when {
        basis.startsWith("cardmarket:") -> "Cardmarket's " + PriceType.fromKey(basis.substringAfter(':')).label.lowercase()
        basis == PriceSource.TCGPLAYER.key -> "TCGplayer's market price"
        else -> "${PriceSource.fromKey(basis).label}'s price"
    }

    /**
     * A printing's price at [source], in euros: Cardmarket's [type] from the price guide ([cm], by
     * product), the others' from [other] (scryfall id, finish → euros). Null when it has none, or
     * isn't made in the wished finish.
     */
    fun priceOf(
        p: WishPrinting,
        foil: Boolean,
        source: PriceSource,
        type: PriceType,
        cm: Map<Int, PriceEntity>,
        other: (String, Finish) -> Double?,
    ): Double? {
        val finish = p.finish(foil) ?: return null
        return when (source) {
            PriceSource.CARDMARKET -> (if (foil) p.cmFoil ?: p.cm else p.cm)?.let { cm[it] }?.toSet(foil)?.get(type)
            else -> other(p.id, finish)
        }?.takeIf { it > 0 }
    }

    /** Each printing with its price (cheapest first, those without a price last). */
    fun ranked(printings: List<WishPrinting>, priceOf: (WishPrinting) -> Double?): List<Pair<WishPrinting, Double?>> =
        printings.map { it to priceOf(it) }.sortedWith(compareBy(nullsLast()) { it.second })

    /** Whether to notify, and the level notified for afterwards. */
    data class Step(val notify: Boolean, val alertedLevel: Double?)

    /**
     * One check of a card with alert [level] whose cheapest price is now [best]: notify once when it's
     * at or below the level, then stay quiet until it's been above the level again (or the level
     * changes). Without a price nothing changes; without a level there's nothing to notify.
     */
    fun step(level: Double?, best: Double?, alertedLevel: Double?): Step = when {
        level == null -> Step(false, null)
        best == null -> Step(false, alertedLevel)
        best <= level + 0.000_1 -> Step(alertedLevel != level, level)
        else -> Step(false, null)
    }

    /** The card's cheapest price now: the chosen source's and, when it counts, CardTrader's. */
    fun best(row: WishPrice?, basis: String, ctFilter: String?): Pair<Double, Boolean>? {
        row ?: return null
        val source = row.price?.takeIf { row.basis == basis }
        val ct = row.ctPrice?.takeIf { ctFilter != null && row.ctFilter == ctFilter }
        return when {
            source == null && ct == null -> null
            ct != null && (source == null || ct < source) -> ct to true
            else -> source!! to false
        }
    }
}

/**
 * Looks up the prices of wishlist cards for their alerts and sends the notifications (since 1.37). Runs
 * after each price update (when the app opens, and in the background every few hours), so alerts are
 * as fresh as the price data. For "any printing" cards it takes the cheapest printing: the printings
 * come from Scryfall (daily), Cardmarket's prices from the price guide on the phone, TCGplayer's and
 * Card Kingdom's from theirs; CardTrader is asked when it's switched on, at most every few hours a card.
 */
class WishlistWatch(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: Settings,
    private val scryfall: ScryfallApi,
    private val priceSources: PriceSourceStore,
    private val cardTrader: CardTrader,
    private val rates: () -> ExchangeRates?,
) {
    private val lock = Mutex()
    private val dao = db.wishPriceDao()

    val notifier = AlertNotifier(context)

    private var soon: Job? = null

    /** A check shortly (a setting just changed; quick changes in a row make one check). */
    fun checkSoon(scope: CoroutineScope) {
        soon?.cancel()
        soon = scope.launch {
            delay(1_500)
            check()
        }
    }

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    /** Euros for a CardTrader price in [currency] (CardTrader answers in the account's currency). */
    private fun toEur(amount: Double, currency: String): Double? = when (currency.uppercase()) {
        "EUR" -> amount
        else -> (rates()?.perEuro?.get(currency.uppercase()) ?: ExchangeRates.FALLBACK.perEuro[currency.uppercase()])?.let { amount / it }
    }

    /** The CardTrader filter while CardTrader is switched on and has a token, else null. */
    private fun ctFilter(): CtFilter? =
        if (settings.cardTraderOn.value && cardTrader.status.value.hasToken) CtFilter(settings.ctLanguage.value, settings.ctCondition.value, settings.ctZeroOnly.value) else null

    /**
     * Checks the wishlist ([only]: just these cards). With [notify] off (a level just set, or the card
     * just changed) a card already below its level is noted without a notification: you're looking at it.
     * [online] off skips the lookups (Scryfall, CardTrader) and works with what the phone has.
     */
    suspend fun check(notify: Boolean = true, only: Set<Long>? = null, online: Boolean = true) = lock.withLock {
        _running.value = true
        try {
            run(notify, only, online)
        } finally {
            _running.value = false
        }
    }

    private suspend fun run(notify: Boolean, only: Set<Long>?, online: Boolean) {
        val items = db.wishlistDao().all()
        if (only == null) {
            if (items.isEmpty()) dao.clear() else dao.keepOnly(items.map { it.id })
        }
        val source = settings.priceSource.value
        val type = settings.alertPriceType.value
        val basis = PriceAlerts.basis(source, type)
        val ctFilter = ctFilter()
        val token = ctFilter?.let { cardTrader.token }
        var ctUsable = token != null && online
        var ctError: Throwable? = null
        var ctCalls = 0
        val start = System.currentTimeMillis()
        // Cards with a level first, then those whose CardTrader price is oldest.
        val todo = items.filter { (only == null || it.id in only) && !it.card.isPending && it.orderedAt == null }
            .map { it to dao.get(it.id) }
            .sortedWith(compareBy({ it.first.alertBelow == null }, { it.second?.ctAt ?: 0L }))
        for ((item, saved) in todo) {
            var row = saved ?: WishPrice(item.id)
            val now = System.currentTimeMillis()
            val printings: List<WishPrinting> = if (!item.anyPrinting) listOf(WishPrinting.of(item.card)) else {
                val cached = WishPrinting.decode(row.printings)?.takeIf { row.printingsOf == item.card.name }
                if (cached != null && (!online || now - row.printingsAt < PRINTINGS_MAX_AGE_MS)) cached
                else {
                    val fetched = if (online) runCatching { scryfall.prints(item.card.name).filter { !it.digital }.map(WishPrinting::of) }.getOrNull() else null
                    if (!fetched.isNullOrEmpty()) {
                        row = row.copy(printings = WishPrinting.encode(fetched), printingsOf = item.card.name, printingsAt = now)
                        fetched
                    } else cached ?: listOf(WishPrinting.of(item.card))
                }
            }
            val cm = printings.flatMap { listOfNotNull(it.cm, it.cmFoil) }.distinct().chunked(900)
                .flatMap { db.priceDao().getMany(it) }.associateBy { it.idProduct }
            if (source != PriceSource.CARDMARKET) runCatching { priceSources.load(printings.map { it.id }) }
            val ranked = PriceAlerts.ranked(printings) { p ->
                PriceAlerts.priceOf(p, item.foil, source, type, cm) { id, finish -> Pricing.at(source, id, finish, "NM", null) }
            }
            val cheapest = ranked.firstOrNull()?.takeIf { it.second != null }
            row = row.copy(basis = basis, price = cheapest?.second, scryfallId = cheapest?.first?.id, setCode = cheapest?.first?.set)

            val key = ctFilter?.key(item.foil)
            if (ctUsable && key != null && (row.ctFilter != key || now - row.ctAt > CT_MAX_AGE_MS) && now - start < CT_BUDGET_MS) {
                try {
                    val r = cardTrader.cheapest(token!!, ranked.map { it.first }, item.foil, ctFilter, ::toEur)
                    val offer = r.offer
                    row = row.copy(
                        ctPrice = offer?.eur, ctBlueprintId = r.blueprintId, ctSetCode = offer?.setCode, ctCondition = offer?.condition,
                        ctLanguage = offer?.language, ctAnyPrice = r.anySeller, ctZeroOver = r.zeroOver, ctFilter = key, ctAt = now,
                    )
                    ctCalls++
                } catch (e: Exception) {
                    ctError = e
                    // A refused token stays refused; anything else may be this card only.
                    if (e is CardTraderAuthException) ctUsable = false
                }
            }

            val best = PriceAlerts.best(row, basis, key)
            val step = PriceAlerts.step(item.alertBelow, best?.first, row.alertedLevel)
            if (step.notify && notify && best != null) notifier.show(item, best.first, best.second, row, basis)
            row = row.copy(alertedLevel = step.alertedLevel, checkedAt = now)
            dao.put(row)
        }
        if (ctError != null || ctCalls > 0) cardTrader.record(ctError)
    }

    companion object {
        private const val PRINTINGS_MAX_AGE_MS = 20L * 3600 * 1000
        /** How often a card's CardTrader price is looked up again (each check, the oldest first). */
        private const val CT_MAX_AGE_MS = 5L * 3600 * 1000
        /** Time spent asking CardTrader per check; cards not reached are asked next time. */
        private const val CT_BUDGET_MS = 4L * 60 * 1000
    }
}

/** The "Price alerts" notifications: one per card, tapping it opens the card on the wishlist. Since 1.37. */
class AlertNotifier(private val context: Context) {
    /** Whether notifications can show (Android 13 and later ask first). */
    fun allowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun channel() {
        if (Build.VERSION.SDK_INT < 26) return
        val ch = NotificationChannel(CHANNEL, "Wishlist price alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a wishlist card drops to the price you set on it"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    fun show(item: WishlistItem, price: Double, onCardTrader: Boolean, row: WishPrice, basis: String) {
        if (!allowed()) return
        channel()
        val title = "${item.card.displayName}: ${Money.format(price)}"
        val where = if (onCardTrader) {
            "CardTrader" + listOfNotNull(row.ctSetCode?.uppercase(), CtCondition.short(row.ctCondition).ifEmpty { null }, row.ctLanguage?.uppercase()).joinToString(", ", " (", ")")
        } else {
            PriceAlerts.basisLong(basis) + (row.setCode?.takeIf { item.anyPrinting }?.let { " (${it.uppercase()})" } ?: "")
        }
        val text = "$where · your level: ${Money.format(item.alertBelow ?: price)}"
        val open = Intent(context, com.mtgtrader.MainActivity::class.java)
            .setAction(ACTION_OPEN_WISH).putExtra(EXTRA_WISH_ID, item.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, item.id.toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.mtgtrader.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + item.id.toInt(), n) }
    }

    companion object {
        const val CHANNEL = "price_alerts"
        const val ACTION_OPEN_WISH = "com.mtgtrader.OPEN_WISH"
        const val EXTRA_WISH_ID = "wishId"
        private const val NOTIFICATION_BASE = 10_000
    }
}
