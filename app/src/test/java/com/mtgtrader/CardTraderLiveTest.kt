package com.mtgtrader

import com.mtgtrader.data.CardTraderOrder
import com.mtgtrader.data.ScryfallApi
import com.mtgtrader.data.Spreadsheet
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Matches a real CardTrader order against the live Scryfall, to check the import by hand:
 * CARDTRADER_ORDER=/path/to/order.xls gradlew testDebugUnitTest --tests '*CardTraderLiveTest*' -i
 * Skipped otherwise (it needs the internet and an order file that isn't in the repository).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardTraderLiveTest {
    @Test fun matchesARealOrder() {
        val path = System.getenv("CARDTRADER_ORDER")
        assumeTrue(path != null)
        val http = OkHttpClient.Builder().addInterceptor { c ->
            c.proceed(c.request().newBuilder().header("User-Agent", "MTGTraderAndroid/1.0").build())
        }.build()
        val lines = CardTraderOrder.parse(Spreadsheet.read(File(path!!).readBytes()))
        val matched = runBlocking { CardTraderOrder.match(lines, ScryfallApi(http)) }
        for (m in matched) {
            val c = m.card
            println(
                "MATCH ${m.line.setCode} ${m.line.number} ${m.line.name} -> " +
                    (c?.let { "${it.setCode} ${it.collectorNumber} ${it.name}" } ?: "NOT FOUND") +
                    (if (m.token) " [token]" else "") + (if (m.basicLand) " [basic land]" else "") + (if (m.guessed) " [guessed]" else ""),
            )
        }
    }
}
