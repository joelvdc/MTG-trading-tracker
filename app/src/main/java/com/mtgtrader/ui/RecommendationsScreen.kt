package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.Deck
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.RecCard
import com.mtgtrader.data.RecResult
import com.mtgtrader.data.RecSection
import com.mtgtrader.data.RecSource
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.IOException
import kotlin.math.roundToInt

/** The card types to filter recommendations by, as the decklist groups them. */
private val TYPE_FILTERS = listOf(
    null to "All types", "Creature" to "Creatures", "Instant" to "Instants", "Sorcery" to "Sorceries", "Artifact" to "Artifacts",
    "Enchantment" to "Enchantments", "Planeswalker" to "Planeswalkers", "Land" to "Lands", "Battle" to "Battles",
)

private enum class Owned(val label: String) { ALL("Owned or not"), MINE("I own"), NOT_MINE("I don't own") }

/** What every recommendation list filters by. */
private data class RecFilter(val type: String? = null, val owned: Owned = Owned.ALL, val newOnly: Boolean = false) {
    fun keep(card: RecCard, ownedNames: Set<String>) =
        (type == null || card.primaryType == type) &&
            (!newOnly || card.isNew) &&
            when (owned) {
                Owned.ALL -> true
                Owned.MINE -> nameKey(card.name) in ownedNames
                Owned.NOT_MINE -> nameKey(card.name) !in ownedNames
            }
}

private fun nameKey(name: String) = name.substringBefore(" // ").trim().lowercase()

/**
 * Card recommendations for a deck from EDHREC (in its own sections, with a longer list of recent
 * releases) or recommander.cards (in its categories, tuned to the decklist), filtered by card type,
 * by whether you own the card and by new cards. Since 1.18.
 */
@Composable
fun RecommendationsScreen(nav: NavController, deckId: Long) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deck by remember { c.db.deckDao().observe(deckId) }.collectAsStateWithLifecycle(null)
    val source by c.settings.recSource.collectAsStateWithLifecycle()
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val ownedList by remember { c.db.collectionDao().observeOwnedByName() }.collectAsStateWithLifecycle(emptyList())
    val owned = remember(ownedList) { ownedList.groupBy { nameKey(it.name) }.mapValues { (_, v) -> v.sumOf { it.qty } } }
    var result by remember { mutableStateOf<RecResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var force by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(RecFilter()) }
    var opened by remember { mutableStateOf<RecCard?>(null) }
    var gallery by remember { mutableStateOf<Pair<List<RecCard>, Int>?>(null) }
    val prices = rememberPrices(result)

    LaunchedEffect(deck?.archidektId, source, refresh) {
        val d = deck ?: return@LaunchedEffect
        error = null
        // Saved ones show straight away; ⟳ asks the source again.
        result = if (!force) c.recommendations.cached(deckId, source) else null
        force = false
        if (result != null) return@LaunchedEffect
        loading = true
        result = try {
            c.recommendations.fetch(d, source)
        } catch (e: IOException) {
            error = e.message ?: "Couldn't get recommendations"
            c.recommendations.cached(deckId, source)
        } finally {
            loading = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recommendations")
                        Text(deck?.name ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { force = true; refresh++ }, enabled = !loading) { Icon(Icons.Default.Refresh, "Get fresh recommendations") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            SourceAndFilters(source, { c.settings.setRecSource(it) }, filter) { filter = it }
            val r = result
            when {
                loading -> Loading(if (source == RecSource.EDHREC) "Asking EDHREC…" else "Asking recommander.cards…")
                r == null -> EmptyState("No recommendations", error ?: "Pull them with the ⟳ button.")
                else -> {
                    val sections = r.sections.map { s -> s.copy(cards = s.cards.filter { filter.keep(it, owned.keys) }) }.filter { it.cards.isNotEmpty() }
                    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
                        item(key = "about") {
                            Text(
                                (error?.let { "$it — showing the ones from ${Fmt.dateTime(r.fetchedAt)}. " } ?: "From ${Fmt.dateTime(r.fetchedAt)}. ") +
                                    (r.note?.let { "$it " } ?: "") +
                                    if (source == RecSource.EDHREC) "Cards in the deck are left out; the share is of the EDHREC decks with this commander that could play the card."
                                    else "recommander.cards ranks cards for this decklist; cards in the deck are left out.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                        if (sections.isEmpty()) item(key = "none") { EmptyState("Nothing left", "No recommendation matches these filters.") }
                        // Swiping through enlarged pictures goes through the cards in the order shown.
                        val flat = sections.flatMap { it.cards }.distinctBy { it.name }
                        sections.forEach { s ->
                            recSection(s, owned, prices, priceType, onImage = { card -> gallery = flat to flat.indexOfFirst { it.name == card.name }.coerceAtLeast(0) }) { opened = it }
                        }
                    }
                }
            }
        }
    }

    gallery?.let { (cards, start) -> RecGallery(cards, start, source, owned, prices, priceType) { gallery = null } }
    opened?.let { card ->
        RecCardDialog(
            card, source, owned[nameKey(card.name)] ?: 0, prices[card.card?.cardmarketId], priceType,
            decks = deck?.let { listOf(it) }.orEmpty(),
            onDismiss = { opened = null },
            onAddToDeck = { d ->
                opened = null
                scope.launch {
                    c.recommendations.addToDeck(d.archidektId, card)
                    snackbar.showSnackbar("${card.name} added to ${d.name} (in the app; kept when the deck reloads)")
                }
            },
            onWishlist = {
                opened = null
                scope.launch {
                    card.card?.let { c.repo.addToWishlist(it) }
                    snackbar.showSnackbar("${card.name} added to the wishlist")
                }
            },
        )
    }
}

/** Recommendations over all decks: the cards you own that fit them, and the cards several decks want. */
@Composable
fun AllDeckRecommendationsScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val source by c.settings.recSource.collectAsStateWithLifecycle()
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val decks by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val job by c.recommendations.job.collectAsStateWithLifecycle()
    val ownedList by remember { c.db.collectionDao().observeOwnedByName() }.collectAsStateWithLifecycle(emptyList())
    val owned = remember(ownedList) { ownedList.groupBy { nameKey(it.name) }.mapValues { (_, v) -> v.sumOf { it.qty } } }
    var results by remember { mutableStateOf<Map<Long, RecResult>?>(null) }
    var filter by remember { mutableStateOf(RecFilter(owned = Owned.MINE)) }
    var view by rememberSaveable { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf<Pair<RecCard, List<Deck>>?>(null) }
    var gallery by remember { mutableStateOf<Pair<List<RecCard>, Int>?>(null) }

    LaunchedEffect(source, job == null) { if (job == null) results = c.recommendations.allCached(source) }
    val all = results
    val prices = rememberPrices(all?.values?.firstOrNull()?.let { first -> RecResult(first.source, 0, all.values.flatMap { it.sections }) })

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Recommendations for all decks") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            SourceAndFilters(source, { c.settings.setRecSource(it) }, filter) { filter = it }
            job?.let { j ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Getting recommendations: ${j.current} (${j.done + 1} of ${j.total})", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { if (j.total == 0) 0f else j.done.toFloat() / j.total }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            val have = all.orEmpty()
            val missing = decks.count { it.archidektId !in have }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (decks.isEmpty()) "No decks yet." else "${have.size} of ${decks.size} decks have ${source.label} recommendations" +
                        (have.values.minOfOrNull { it.fetchedAt }?.let { " (oldest ${Fmt.date(it)})" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { c.recommendations.fetchAll(source) }, enabled = job == null && decks.isNotEmpty()) {
                    Text(if (missing == decks.size) "Get them" else "Refresh all")
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = view == 0, onClick = { view = 0 }, label = { Text("By card") })
                FilterChip(selected = view == 1, onClick = { view = 1 }, label = { Text("For several decks") })
            }
            if (all == null) {
                Loading("Loading…")
                return@Column
            }
            val deckById = decks.associateBy { it.archidektId }
            // Each card once, with the decks it's recommended for (best ranked first).
            val byCard = remember(all, filter, owned) {
                all.flatMap { (id, r) -> r.allCards.map { it to id } }
                    .filter { (card, _) -> filter.keep(card, owned.keys) }
                    .groupBy { nameKey(it.first.name) }
                    .map { (_, list) -> list.first().first to list.mapNotNull { deckById[it.second] }.distinctBy { it.archidektId } }
                    .filter { it.second.isNotEmpty() }
            }
            val shown = if (view == 1) byCard.filter { it.second.size > 1 }.sortedByDescending { it.second.size } else byCard.sortedByDescending { it.second.size }
            if (shown.isEmpty()) {
                EmptyState(
                    "Nothing to show",
                    when {
                        all.isEmpty() -> "Tap “Get them” to ask ${source.label} about every deck (a few seconds per deck)."
                        filter.owned == Owned.MINE -> "None of the recommended cards are in your collection. Change the filter to see the others."
                        else -> "No recommendation matches these filters."
                    },
                )
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
                item(key = "count") {
                    Text(
                        "${shown.size} cards" + if (filter.owned == Owned.MINE) " you own that fit your decks" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                items(shown, key = { nameKey(it.first.name) }) { (card, forDecks) ->
                    RecRow(
                        card, owned[nameKey(card.name)] ?: 0, prices[card.card?.cardmarketId], priceType, "For " + forDecks.joinToString { it.name },
                        onImage = { gallery = shown.map { it.first } to shown.indexOfFirst { it.first.name == card.name }.coerceAtLeast(0) },
                    ) {
                        opened = card to forDecks
                    }
                }
            }
        }
    }

    gallery?.let { (cards, start) -> RecGallery(cards, start, source, owned, prices, priceType) { gallery = null } }
    opened?.let { (card, forDecks) ->
        RecCardDialog(
            card, source, owned[nameKey(card.name)] ?: 0, prices[card.card?.cardmarketId], priceType, forDecks,
            onDismiss = { opened = null },
            onAddToDeck = { d ->
                opened = null
                scope.launch {
                    c.recommendations.addToDeck(d.archidektId, card)
                    snackbar.showSnackbar("${card.name} added to ${d.name}")
                }
            },
            onWishlist = {
                opened = null
                scope.launch {
                    card.card?.let { c.repo.addToWishlist(it) }
                    snackbar.showSnackbar("${card.name} added to the wishlist")
                }
            },
        )
    }
}

/** Today's Cardmarket prices for the recommended cards. */
@Composable
private fun rememberPrices(result: RecResult?): Map<Int, PriceEntity> {
    val c = LocalContext.current.container
    var map by remember { mutableStateOf<Map<Int, PriceEntity>>(emptyMap()) }
    LaunchedEffect(result) {
        val ids = result?.sections?.flatMap { s -> s.cards.mapNotNull { it.card?.cardmarketId } }?.distinct().orEmpty()
        map = if (ids.isEmpty()) emptyMap() else ids.chunked(500).flatMap { c.db.priceDao().getMany(it) }.associateBy { it.idProduct }
    }
    return map
}

@Composable
private fun SourceAndFilters(source: RecSource, onSource: (RecSource) -> Unit, filter: RecFilter, onFilter: (RecFilter) -> Unit) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(RecSource.entries) { s -> FilterChip(selected = s == source, onClick = { onSource(s) }, label = { Text(s.label) }) }
            item { Spacer(Modifier.width(8.dp)) }
            item { FilterChip(selected = filter.newOnly, onClick = { onFilter(filter.copy(newOnly = !filter.newOnly)) }, label = { Text("New cards") }) }
            items(Owned.entries) { o -> FilterChip(selected = filter.owned == o, onClick = { onFilter(filter.copy(owned = o)) }, label = { Text(o.label) }) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(TYPE_FILTERS) { (t, label) -> FilterChip(selected = filter.type == t, onClick = { onFilter(filter.copy(type = t)) }, label = { Text(label) }) }
        }
    }
}

@Composable
private fun Loading(text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.padding(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** A source section: header and its cards, the first 12 with the rest a tap away. */
private fun androidx.compose.foundation.lazy.LazyListScope.recSection(
    s: RecSection, owned: Map<String, Int>, prices: Map<Int, PriceEntity>, priceType: PriceType, onImage: (RecCard) -> Unit, onOpen: (RecCard) -> Unit,
) {
    item(key = "h_${s.key}") {
        Text("${s.title} (${s.cards.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    }
    item(key = "b_${s.key}") {
        var all by remember { mutableStateOf(false) }
        Column {
            (if (all) s.cards else s.cards.take(12)).forEach { card ->
                RecRow(card, owned[nameKey(card.name)] ?: 0, prices[card.card?.cardmarketId], priceType, null, onImage = { onImage(card) }) { onOpen(card) }
            }
            if (s.cards.size > 12) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all ${s.cards.size}") }
        }
    }
}

private fun RecCard.stats(): String = listOfNotNull(
    inclusionPct?.let { pct -> "in $pct% of ${"%,d".format(potentialDecks ?: 0)} decks" },
    synergy?.let { "synergy %+d%%".format((it * 100).roundToInt()) },
    score?.let { "score %.2f".format(it) },
    if (isNew) "new" else null,
).joinToString(" · ")

@Composable
private fun RecRow(card: RecCard, owned: Int, price: PriceEntity?, priceType: PriceType, extra: String?, onImage: () -> Unit, onClick: () -> Unit) {
    val unit = (price?.toSet(false)?.best(priceType) ?: card.card?.fallbackEur).let { cm -> card.card?.let { com.mtgtrader.data.Pricing.unit(it.scryfallId, com.mtgtrader.data.Finish.NONFOIL, "NM", cm) } ?: cm }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // The picture opens the swipeable gallery; the rest of the row the card's window.
        CardThumb(card.card?.imageUrl, Modifier.clickable(onClick = onImage), width = 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(card.typeLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.stats(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            extra?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(unit), style = MaterialTheme.typography.bodyMedium)
            if (owned > 0) Text("✓ own $owned", style = MaterialTheme.typography.labelSmall, color = TrendColors.up)
        }
    }
}

@Composable
private fun RecCardDialog(
    card: RecCard,
    source: RecSource,
    owned: Int,
    price: PriceEntity?,
    priceType: PriceType,
    decks: List<Deck>,
    onDismiss: () -> Unit,
    onAddToDeck: (Deck) -> Unit,
    onWishlist: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardThumb(card.card?.imageUrl, width = 96, enlargeable = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(card.typeLine, style = MaterialTheme.typography.bodySmall)
                        Text(card.stats(), style = MaterialTheme.typography.bodySmall)
                        Text(if (owned > 0) "You own $owned" else "Not in your collection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        card.releasedAt?.let { Text("Released $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                decks.forEach { d ->
                    Button(onClick = { onAddToDeck(d) }, enabled = card.card != null, modifier = Modifier.fillMaxWidth()) {
                        Text("Add to ${d.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(onClick = onWishlist, enabled = card.card != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Star, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to wishlist")
                }
                HorizontalDivider()
                PriceTable(price?.toSet(false) ?: PriceSet(trend = card.card?.fallbackEur), priceType, "Cardmarket prices")
                Row {
                    TextButton(onClick = {
                        val slug = card.name.substringBefore(" // ").lowercase().replace(Regex("[^a-z0-9 -]"), "").trim().replace(Regex("[ -]+"), "-")
                        runCatching { uriHandler.openUri("https://edhrec.com/cards/$slug") }
                    }) {
                        Text("EDHREC")
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                    }
                    card.card?.let { ref ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(ref.cardmarketUrl) } }) {
                            Text("Cardmarket")
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
                Text(
                    if (source == RecSource.EDHREC) "Recommendation data: EDHREC." else "Recommendations by recommander.cards.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Enlarged card pictures to swipe through, left and right, with the basics under each: type, the
 * source's numbers, price and how many you own.
 */
@Composable
private fun RecGallery(
    cards: List<RecCard>, start: Int, source: RecSource, owned: Map<String, Int>, prices: Map<Int, PriceEntity>, priceType: PriceType,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, (cards.size - 1).coerceAtLeast(0))) { cards.size }
        Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.98f))) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                Text("${pager.currentPage + 1} of ${cards.size} · ${source.label}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val card = cards[page]
                val unit = (prices[card.card?.cardmarketId]?.toSet(false)?.best(priceType) ?: card.card?.fallbackEur)
                    .let { cm -> card.card?.let { com.mtgtrader.data.Pricing.unit(it.scryfallId, com.mtgtrader.data.Finish.NONFOIL, "NM", cm) } ?: cm }
                val have = owned[nameKey(card.name)] ?: 0
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val hasBack = rememberHasBack(card.card?.imageUrl)
                    var showBack by remember(page) { mutableStateOf(false) }
                    val front = card.card?.imageUrl?.let(::largeImageUrl)
                    AsyncImage(
                        model = if (showBack) backImageUrl(front) ?: front else front,
                        contentDescription = card.name,
                        modifier = Modifier.fillMaxWidth().aspectRatio(63f / 88f),
                    )
                    if (hasBack) FlipButton(showBack, onFlip = { showBack = !showBack }, Modifier.padding(top = 8.dp))
                    Spacer(Modifier.padding(6.dp))
                    Text(card.name, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(card.typeLine, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Text(card.stats(), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Text(
                        Fmt.money(unit) + " (${priceType.short})" + if (have > 0) " · you own $have" else "",
                        color = if (have > 0) TrendColors.up else Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
