package com.mtgtrader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.LaunchedEffect
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
import com.mtgtrader.data.PowerSource
import com.mtgtrader.data.RuleZeroCard
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.WinOdds
import kotlin.math.roundToInt

/**
 * The bracket or power level cards of all the owner's own decks (the Archidekt and Local sections), in the
 * Decks tab's order, as small cards in one list; tapping one opens the full cards, swiping between decks.
 * Decks Commander Salt hasn't scored are left out and counted at the bottom. Since 1.46.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRuleZeroCardsScreen(nav: NavController, kind: RuleZeroCard) {
    val c = LocalContext.current.container
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(null)
    val source by c.settings.powerSource.collectAsStateWithLifecycle()
    val account by c.archidekt.status.collectAsStateWithLifecycle()
    val owner = account.username ?: c.settings.archidektUser.takeIf { it.isNotBlank() }
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    // Decks scored before their card details were kept get them now.
    LaunchedEffect(Unit) { c.decks.fillMissingCardData() }
    val own = decks?.let { list ->
        remember(list, owner, source) {
            DeckSorting.sort(list, c.settings.deckSort, c.settings.deckSortReversed, source)
                .filter { DeckGroup.of(it, owner) != DeckGroup.OTHERS }
        }
    }
    val shown = own?.filter { it.scored && (kind == RuleZeroCard.BRACKET || it.power(source) != null) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (kind == RuleZeroCard.BRACKET) "Bracket cards of all decks" else "Power level cards of all decks", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
                item(key = "empty") { EmptyState("No scored decks yet", "Your own decks get these cards once Commander Salt has scored them.") }
            }
            items(shown, key = { it.archidektId }) { deck ->
                val data = remember(deck.saltCard) { SaltCard.decode(deck.saltCard) }
                val onClick = { open = shown.indexOf(deck) }
                if (kind == RuleZeroCard.BRACKET) {
                    val win by rememberWinOdds(deck)
                    SmallBracketCard(deck, data, win, onClick)
                } else {
                    SmallPowerCard(deck, data, source, onClick)
                }
            }
            val unscored = own!!.filter { it !in shown }
            if (unscored.isNotEmpty()) {
                item(key = "unscored") {
                    Text(
                        "${unscored.size} deck(s) not scored by " + (if (kind == RuleZeroCard.POWER && source.external) source.site else "Commander Salt") +
                            " yet: " + unscored.joinToString(", ") { it.name } + ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    open?.let { start -> shown?.takeIf { it.isNotEmpty() }?.let { AllCardsDialog(it, start.coerceIn(0, it.lastIndex), kind, source) { open = null } } }
}

@Composable
internal fun SmallBracketCard(deck: Deck, data: SaltCard?, win: WinOdds? = null, onClick: () -> Unit) {
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
            SmallBracketTile("Baseline", deck.bracketBaseline)
            SmallBracketTile("Realistic", deck.bracketRealistic)
            // The turn the deck usually wins by (since 1.52); "…" while it's worked out.
            if (deck.commanders.isNotBlank()) SpeedTile(win)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (data == null) {
                    Text("Details not loaded yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Criterion("Game changers", data.gameChangers.size)
                    Criterion("Two-card combos", data.twoCardCombos.size)
                    Criterion("Early-game combos", data.earlyCombos.size)
                    Criterion("Extra turns · land denial", data.extraTurns.size + data.massLandDenial.size)
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

@Composable
internal fun SmallPowerCard(deck: Deck, data: SaltCard?, source: PowerSource, onClick: () -> Unit) {
    val power = deck.power(source)
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DeckArt(deck, Modifier.width(76.dp).height(56.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(deck.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    ColorPips(deck.colorIdentity, size = 10)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    METER.forEachIndexed { i, colour ->
                        Box(
                            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (power != null && i < power.roundToInt()) colour else MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        data?.playStyle?.lowercase(),
                        deck.saltPercent?.let { "salt ${it.roundToInt()}%" },
                        data?.manaFixing?.let { "fixing $it%" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(power?.let { Scores.power(it, source) } ?: "—", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                Text("/ 10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The full cards of all [decks], swiping from one deck to the next, starting at [start]. */
@Composable
private fun AllCardsDialog(decks: List<Deck>, start: Int, kind: RuleZeroCard, source: PowerSource, onDismiss: () -> Unit) {
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
                val deck = decks[page]
                val data = remember(deck.saltCard) { SaltCard.decode(deck.saltCard) }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (data == null) {
                        Text("Getting the card details from Commander Salt…", color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else when (kind) {
                        RuleZeroCard.BRACKET -> {
                            val win by rememberWinOdds(deck)
                            BracketCardView(deck, data, win = win)
                        }
                        RuleZeroCard.POWER -> PowerCardView(deck, data, source)
                    }
                }
            }
        }
    }
}
