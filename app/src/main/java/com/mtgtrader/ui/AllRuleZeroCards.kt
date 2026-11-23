package com.mtgtrader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckGroup
import com.mtgtrader.data.DeckSorting
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltSum
import com.mtgtrader.data.WinOdds
import com.mtgtrader.data.WinTurns
import kotlin.math.roundToInt

/**
 * The bracket cards of all the owner's own decks (the Archidekt and Local sections), in the Decks tab's order, as
 * small cards in one list; tapping one opens the full card, swiping between decks. Decks without the website's
 * estimate are left out and counted at the bottom. Since 1.46; bracket cards only since 1.116.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRuleZeroCardsScreen(nav: NavController) {
    val c = LocalContext.current.container
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(null)
    val ownUser by c.settings.ownArchidektUser.collectAsStateWithLifecycle()
    val owner = c.settings.deckOwner(ownUser)
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val own = decks?.let { list ->
        remember(list, owner, WinTurns.odds) {
            DeckSorting.sort(list, c.settings.deckSort, c.settings.deckSortReversed)
                .filter { DeckGroup.of(it, owner) !in setOf(DeckGroup.OTHERS, DeckGroup.RETIRED) }
        }
    }
    val shown = own?.filter { it.rated }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Bracket cards of all decks", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                // The cards as an A4 PDF to print and cut out (since 1.65).
                actions = { IconButton(onClick = { nav.navigate("decks/print") }) { Icon(Icons.Default.Print, "Print cards as PDF") } },
            )
        },
    ) { pad ->
        if (shown == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shown.isEmpty()) {
                item(key = "empty") { EmptyState("No rated decks yet", "Needs a connection to the website") }
            }
            items(shown, key = { it.archidektId }) { deck ->
                val win by rememberWinOdds(deck)
                val salt = rememberSaltSum(deck.archidektId, deck.updatedAt)
                SmallBracketCard(deck, deck.estimate, win, salt, onClick = { open = shown.indexOf(deck) })
            }
            val unrated = own!!.filter { it !in shown }
            if (unrated.isNotEmpty()) {
                item(key = "unrated") {
                    Text(
                        "Not rated yet: " + unrated.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    open?.let { start -> shown?.takeIf { it.isNotEmpty() }?.let { AllCardsDialog(it, start.coerceIn(0, it.lastIndex)) { open = null } } }
}

@Composable
internal fun SmallBracketCard(deck: Deck, data: SaltCard?, win: WinOdds? = null, saltSum: com.mtgtrader.data.SaltSum? = null, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Box(Modifier.fillMaxWidth().height(54.dp)) {
            DeckArt(deck, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(HEADER_TINT))
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(deck.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(deck.commanders, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ColorPips(deck.colorIdentity)
            }
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallBracketTile("Bracket", data?.bracket)
            val (bg, fg) = saltColors(saltSum?.level)
            Column(
                Modifier.width(62.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // A whole number on the bracket cards (the deck page keeps one decimal).
                Text(saltSum?.let { "${it.total.roundToInt()}" } ?: "—", color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, maxLines = 1)
                Text("Salt", color = fg, style = MaterialTheme.typography.labelSmall)
            }
            // The turn 4 in 10 games are won by (since 1.54; half the games in 1.52); "…" while it's worked out.
            if (deck.commanders.isNotBlank()) SpeedTile(win)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (data == null) {
                    Text("Not rated yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Criterion("Game changers", data.gameChangers.size)
                    Criterion("Two-card combos", data.twoCardCombos.size)
                    Criterion("Extra turns", data.extraTurns.size)
                    Criterion("Land denial", data.massLandDenial.size)
                }
            }
        }
    }
}

@Composable
private fun SpeedTile(win: WinOdds?) {
    Column(
        Modifier.width(62.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(win?.label ?: "…", fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, maxLines = 1)
        Text("Wins by", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SmallBracketTile(label: String, bracket: Int?) {
    val (bg, fg) = bracketColors(bracket)
    Column(
        Modifier.width(62.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(bracket?.let { "B$it" } ?: "—", color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Criterion(label: String, n: Int) {
    val faint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (n > 0) MaterialTheme.colorScheme.onSurface else faint, modifier = Modifier.weight(1f))
        Text("$n", style = MaterialTheme.typography.bodySmall, fontWeight = if (n > 0) FontWeight.Bold else null, color = if (n > 0) MaterialTheme.colorScheme.error else faint)
    }
}

/** The full cards of all [decks], swiping from one deck to the next, starting at [start]. */
@Composable
private fun AllCardsDialog(decks: List<Deck>, start: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            (view.parent as? DialogWindowProvider)?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
            onDispose { view.keepScreenOn = false }
        }
        val pager = rememberPagerState(initialPage = start) { decks.size }
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${decks.getOrNull(pager.currentPage)?.name.orEmpty()} · ${pager.currentPage + 1} of ${decks.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Back to the list", tint = Color.White) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                val card = rememberPrintCard(decks[page])
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    if (card == null) CircularProgressIndicator(color = Color.White) else BracketCardImage(card)
                }
            }
        }
    }
}
