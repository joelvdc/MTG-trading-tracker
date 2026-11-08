package com.mtgtrader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtgtrader.data.GameOverview
import com.mtgtrader.data.GameRecord
import com.mtgtrader.data.Games
import com.mtgtrader.data.StatsPeriod
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val monthLabel = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)

/** The game stats' Overview tab (since 1.67): totals, games per month, how games were won and who started. */
fun LazyListScope.gameOverviewTab(shown: List<GameRecord>, period: StatsPeriod, anyGames: Boolean) {
    if (!anyGames) return
    if (shown.isEmpty()) {
        item(key = "ov_none") { Text("No games in this period.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp)) }
        return
    }
    val o = GameOverview.overview(shown)
    item(key = "ov_tiles") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${o.games}", "games played", Modifier.weight(1f))
                StatTile(o.myWinRate?.let { "${(it * 100).roundToInt()} %" } ?: "–", "you won", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(o.avgMs?.let { Games.duration(it) } ?: "–", "average game", Modifier.weight(1f))
                StatTile(o.avgRounds?.let { "%.1f".format(Locale.ENGLISH, it) } ?: "–", "average turns", Modifier.weight(1f))
            }
        }
    }
    item(key = "ov_months") {
        val max = when (period) { StatsPeriod.YEAR -> 13; StatsPeriod.QUARTER -> 4; StatsPeriod.ALL -> 24 }
        OverviewCard("Games per month") { MonthChart(GameOverview.perMonth(shown, max)) }
    }
    item(key = "ov_ways") {
        OverviewCard("How games were won") {
            val ways = GameOverview.winsByType(shown)
            if (ways.isEmpty()) Text("No winners recorded.", style = MaterialTheme.typography.bodyMedium)
            else {
                val top = ways.maxOf { it.games }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ways.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(w.type?.label ?: "Not told", Modifier.width(140.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.weight(1f)) {
                                Box(
                                    Modifier.fillMaxWidth(w.games.toFloat() / top).height(14.dp).clip(RoundedCornerShape(4.dp))
                                        .background(if (w.type == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary),
                                )
                            }
                            Text("${w.games}", Modifier.width(32.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
    }
    item(key = "ov_first") {
        OverviewCard("Starting first") {
            val f = GameOverview.firstPlayer(shown)
            if (f.games == 0) Text("No games with a known starting player and winner yet.", style = MaterialTheme.typography.bodyMedium)
            else {
                Text("${(f.wins * 100.0 / f.games).roundToInt()} %", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("of games won by the player who started", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${f.wins} of ${f.games} games. With these tables an equal chance would be about ${(f.fairShare * 100).roundToInt()} %.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun MonthChart(months: List<com.mtgtrader.data.MonthCount>) {
    val top = (months.maxOfOrNull { it.games } ?: 0).coerceAtLeast(1)
    // With many months only every second or third label fits; the last month always has one.
    val step = when { months.size > 13 -> 3; months.size > 6 -> 2; else -> 1 }
    Row(Modifier.fillMaxWidth().height(126.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        months.forEachIndexed { i, m ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (m.games > 0) Text("${m.games}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height((70.dp * m.games / top).coerceAtLeast(if (m.games > 0) 2.dp else 0.dp)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(MaterialTheme.colorScheme.primary))
                Box(Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
                    if ((months.size - 1 - i) % step == 0) {
                        Text(
                            m.month.format(monthLabel), Modifier.wrapContentWidth(unbounded = true), fontSize = 9.sp, maxLines = 1, softWrap = false,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
