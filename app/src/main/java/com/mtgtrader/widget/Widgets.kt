package com.mtgtrader.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mtgtrader.MainActivity
import com.mtgtrader.R
import com.mtgtrader.container
import com.mtgtrader.data.AlertNotifier
import com.mtgtrader.data.CtFilter
import com.mtgtrader.data.Pricing
import com.mtgtrader.data.PriceAlerts
import com.mtgtrader.data.WidgetData
import com.mtgtrader.data.WidgetSnapshot
import com.mtgtrader.ui.TrendColors
import com.mtgtrader.ui.widgetDarkColors
import com.mtgtrader.ui.widgetLightColors
import kotlinx.coroutines.flow.first

/** The home-screen widgets (since 1.77): "Collection value" and "Wishlist", drawn from the app's [WidgetSnapshot]. */
object MtgWidgets {
    const val ACTION_OPEN_SCREEN = "com.mtgtrader.OPEN_SCREEN"
    const val EXTRA_SCREEN = "screen"

    /** Works out the widgets' snapshot from what the app has loaded, saves it and redraws both widgets. */
    suspend fun refresh(context: Context) {
        val c = context.container
        val type = c.settings.priceType.value
        val source = Pricing.source
        val ctFilter = if (c.settings.cardTraderOn.value && c.cardTrader.status.value.hasToken)
            CtFilter(c.settings.ctLanguage.value, c.settings.ctCondition.value, c.settings.ctZeroOnly.value) else null
        val snapshot = WidgetData.build(
            rows = c.db.collectionDao().allWithPrices(),
            points = c.history.observe(type, null, source).first(),
            source = source,
            type = type,
            wishes = c.db.wishlistDao().all(),
            wishPrices = c.db.wishPriceDao().observeAll().first().associateBy { it.wishId },
            basis = PriceAlerts.basis(c.settings.priceSource.value, c.settings.alertPriceType.value),
            ctFilter = ctFilter,
        )
        WidgetData(context).save(snapshot)
        ValueWidget().updateAll(context)
        WishlistWidget().updateAll(context)
    }

    internal fun openScreen(context: Context, screen: String): Action = actionStartActivity(
        Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_SCREEN).putExtra(EXTRA_SCREEN, screen)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

    /** Opens one wishlist card, the way a price alert's notification does. */
    internal fun openWish(context: Context, id: Long): Action = actionStartActivity(
        Intent(context, MainActivity::class.java).setAction(AlertNotifier.ACTION_OPEN_WISH).putExtra(AlertNotifier.EXTRA_WISH_ID, id)
            .setData(android.net.Uri.parse("mtgtrader://wish/$id"))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
}

private val colors = ColorProviders(light = widgetLightColors, dark = widgetDarkColors)
// The app's card colour (surfaceContainer), light or dark with the phone.
private val background = androidx.glance.color.ColorProvider(day = widgetLightColors.surfaceContainer, night = widgetDarkColors.surfaceContainer)
private val up = ColorProvider(TrendColors.up)
private val down = ColorProvider(TrendColors.down)

@Composable
private fun Frame(onClick: Action, content: @Composable () -> Unit) = GlanceTheme(colors) {
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp).background(background)
            .padding(14.dp).clickable(onClick),
    ) { content() }
}

@Composable
private fun Label(text: String) =
    Text(text, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium))

@Composable
private fun NotYet() = Text("Open MTG Trader once to fill this in.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))

/** "Collection value": small, the value and its change over 7 days; wide, with a week's chart and the biggest movers. */
class ValueWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData(context).load()
        provideContent {
            val ctx = LocalContext.current
            Frame(MtgWidgets.openScreen(ctx, "value")) {
                if (s == null) NotYet() else if (LocalSize.current.width >= WIDE.width) Wide(ctx, s) else Small(s)
            }
        }
    }

    @Composable
    private fun Small(s: WidgetSnapshot) {
        Label("Collection")
        Text(s.value, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold))
        s.changeSmall?.let { Text(it, maxLines = 1, style = TextStyle(color = if (s.up) up else down, fontSize = 12.sp, fontWeight = FontWeight.Medium)) }
    }

    @Composable
    private fun Wide(context: Context, s: WidgetSnapshot) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Label("Collection value · ${s.source}")
                Text(s.value, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold))
                s.changeWide?.let { Text(it, maxLines = 1, style = TextStyle(color = if (s.up) up else down, fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
            }
            if (s.chart.size >= 2) {
                Image(ImageProvider(chart(context, s.chart, if (s.up) TrendColors.up else TrendColors.down)), null, GlanceModifier.size(CHART_W.dp, CHART_H.dp))
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        for (m in s.movers) {
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    (if (m.up) "▲ " else "▼ ") + m.name, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                )
                Text(m.change, style = TextStyle(color = if (m.up) up else down, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 40.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        private const val CHART_W = 110
        private const val CHART_H = 44

        /** A week's values as a line with a dot on today's: Glance can't draw lines, so it's a picture. */
        private fun chart(context: Context, values: List<Double>, color: Color): Bitmap {
            val d = context.resources.displayMetrics.density
            val w = (CHART_W * d).toInt()
            val h = (CHART_H * d).toInt()
            val pad = 4 * d
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val lo = values.min()
            val span = (values.max() - lo).takeIf { it > 0 } ?: 1.0
            fun x(i: Int) = pad + (w - 2 * pad) * i / (values.size - 1)
            fun y(v: Double) = (h - pad - (h - 2 * pad) * ((v - lo) / span)).toFloat()
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); style = Paint.Style.STROKE; strokeWidth = 2 * d; strokeJoin = Paint.Join.ROUND }
            Canvas(bmp).apply {
                drawPath(path, paint)
                drawCircle(x(values.size - 1), y(values.last()), 3 * d, paint.apply { style = Paint.Style.FILL })
            }
            return bmp
        }
    }
}

/** "Wishlist": the cards closest to their alert price, reached ones first (✓). */
class WishlistWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetData(context).load()
        provideContent {
            val ctx = LocalContext.current
            Frame(MtgWidgets.openScreen(ctx, "wishlist")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.ic_notification), null, GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
                    Spacer(GlanceModifier.width(6.dp))
                    Label("Wishlist · closest to your price")
                }
                Spacer(GlanceModifier.height(4.dp))
                when {
                    s == null -> NotYet()
                    s.wishes.isEmpty() -> Text("No wishlist card has a price alert.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                    else -> for (w in s.wishes) {
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(MtgWidgets.openWish(ctx, w.id)), verticalAlignment = Alignment.CenterVertically) {
                            Text(w.name, maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
                            Text(w.price, style = TextStyle(color = if (w.reached) up else GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                            Text(if (w.reached) "  ✓ ${w.level}" else "  / ${w.level}", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
                        }
                    }
                }
            }
        }
    }
}

class ValueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ValueWidget()
}

class WishlistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WishlistWidget()
}
