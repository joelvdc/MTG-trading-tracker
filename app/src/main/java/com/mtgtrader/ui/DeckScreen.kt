package com.mtgtrader.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.mtgtrader.container
import com.mtgtrader.data.BinderChoice
import com.mtgtrader.data.BracketTag
import com.mtgtrader.data.Brackets
import com.mtgtrader.data.SaltCard
import com.mtgtrader.data.SaltCombo
import com.mtgtrader.data.DeckEdits
import com.mtgtrader.data.DeckEditor
import com.mtgtrader.data.UndoAction
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.composed
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.DragIndicator
import com.mtgtrader.data.ManaOdds
import com.mtgtrader.data.ManaOddsCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilterChip
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckCollectionCounts
import com.mtgtrader.data.DeckCardRow
import com.mtgtrader.data.DeckGroupBy
import com.mtgtrader.data.DeckGrouping
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.RuleZeroCard
import com.mtgtrader.data.PowerSource
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Style
import com.mtgtrader.data.DeckPrintings
import com.mtgtrader.data.DeckToCollection
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.CollectionRow
import com.mtgtrader.data.Binder
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExtendedFloatingActionButton
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.DeckSection
import com.mtgtrader.data.CardInfo
import com.mtgtrader.data.DeckStats

@Composable
fun DeckScreen(nav: NavController, deckId: Long) {
    val c = LocalContext.current.container
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deck by remember { c.db.deckDao().observe(deckId) }.collectAsStateWithLifecycle(null)
    val archidektRows by remember { c.db.deckDao().observeCards(deckId) }.collectAsStateWithLifecycle(emptyList())
    val maybeRows by remember { c.db.deckDao().observeMaybeboard(deckId) }.collectAsStateWithLifecycle(emptyList())
    val compact by c.settings.deckCompact.collectAsStateWithLifecycle()
    val showMaybeboard by c.settings.deckMaybeboard.collectAsStateWithLifecycle()
    val collapsed by c.settings.deckCollapsed.collectAsStateWithLifecycle()
    // Mana costs by card name (since 1.43), from the card details kept from Scryfall.
    val costList by remember { c.db.cardInfoDao().observeDeckCosts(deckId) }.collectAsStateWithLifecycle(emptyList())
    val costs = remember(costList) { costList.associate { it.name to it.manaCost } }
    // Cards new to the app, or from before 1.43 / 1.44, get their details (cost, mana made, tokens) now when online.
    LaunchedEffect(deckId) { runCatching { c.cardDetails.fillMissing() } }
    // The deck stats and the deck's tokens (since 1.44).
    val statCards by remember { c.db.cardInfoDao().observeDeckStats(deckId) }.collectAsStateWithLifecycle(emptyList())
    val statsOpen by c.settings.deckStatsOpen.collectAsStateWithLifecycle()
    val allGames by c.games.games.collectAsStateWithLifecycle(emptyList())
    val deckGames = remember(allGames, deckId) { com.mtgtrader.data.GameStats.gamesOfDeck(allGames, deckId) }
    // The commander odds, worked out in the background when the Stats are open and whenever the deck's cards or
    // their details change; kept here, not in the list, whose rows start over when they scroll back into sight.
    val oddsWanted = DeckStats.STATS in statsOpen && statCards.any { it.commander }
    val odds by produceState<ManaOdds?>(null, statCards, deck?.colorIdentity, oddsWanted) {
        val identity = deck?.colorIdentity
        if (!oddsWanted || identity == null) return@produceState
        value = null
        value = withContext(Dispatchers.Default) { ManaOddsCalc.calculate(statCards, identity) }
    }
    val landsOnly by c.settings.deckLandsOnly.collectAsStateWithLifecycle()
    val colourless by c.settings.deckColourless.collectAsStateWithLifecycle()
    val tokenParts = remember(statCards) { DeckStats.tokenParts(statCards) }
    val tokenDetails by produceState(emptyMap<String, CardInfo>(), tokenParts.keys) {
        value = runCatching { c.cardDetails.forIds(tokenParts.keys.map { it.id }) }.getOrDefault(emptyMap())
    }
    // Token printings picked for this deck (since 1.45).
    val allTokenPicks by c.settings.deckTokenPicks.collectAsStateWithLifecycle()
    val tokenPicks = allTokenPicks[deckId].orEmpty()
    val tokens = remember(tokenParts, tokenDetails, tokenPicks) { DeckStats.tokens(tokenParts, tokenDetails, tokenPicks) }
    var openToken by rememberSaveable { mutableStateOf<String?>(null) }
    fun toggleStats(key: String) = c.settings.setDeckStatsOpen(if (key in statsOpen) statsOpen - key else statsOpen + key)
    // Shown in the printings you own (since 1.22), unless switched off for this deck.
    val owned by remember { c.db.collectionDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val binders = rememberBinders()
    val printingsOff by c.settings.deckPrintingsOff.collectAsStateWithLifecycle()
    val allPicks by c.settings.deckPicks.collectAsStateWithLifecycle()
    val myPrintings = deckId !in printingsOff
    val picks = allPicks[deckId].orEmpty()
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()
    val job by c.decks.job.collectAsStateWithLifecycle()
    val result by c.decks.result.collectAsStateWithLifecycle()
    val source by c.settings.powerSource.collectAsStateWithLifecycle()
    var groupBy by rememberSaveable { mutableStateOf(DeckGroupBy.CATEGORY) }
    var menu by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf<RuleZeroCard?>(null) }
    var selected by remember { mutableStateOf<DeckCardRow?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val matched = remember(archidektRows, owned, binders, picks, myPrintings, deck?.name) {
        if (!myPrintings) DeckPrintings.Result(archidektRows, emptyMap())
        else {
            val deckBinder = binders.firstOrNull { b -> deck?.name?.let { it.trim().equals(b.name.trim(), true) } == true }?.id
            val trade = c.settings.tradeBinderId.takeIf { id -> binders.any { it.id == id } } ?: binders.firstOrNull { it.name.equals("Trade binder", true) }?.id
            DeckPrintings.match(archidektRows, owned, deckBinder, trade, picks)
        }
    }
    val rows = matched.rows

    var addingToCollection by remember { mutableStateOf(false) }
    var onlyBracket by rememberSaveable { mutableStateOf(false) }
    // Bracket-relevant cards come from Commander Salt's analysis (fetched again when saved by an older version).
    val saltCard by produceState(SaltCard.decode(deck?.saltCard), deck?.saltCard) {
        val d = deck ?: return@produceState
        if (value?.current != true) c.decks.loadCardData(d)?.let { value = it }
    }
    fun tagsOf(row: DeckCardRow): Set<BracketTag> {
        val t = saltCard?.tags(row.item.card.name).orEmpty()
        return if (row.item.gameChanger) t + BracketTag.GAME_CHANGER else t
    }

    // When the deck can win (since 1.52): worked out with the commander odds, and again when the cards or combos change.
    val win by produceState<com.mtgtrader.data.WinOdds?>(null, statCards, deck?.colorIdentity, saltCard, oddsWanted) {
        val identity = deck?.colorIdentity
        if (!oddsWanted || identity == null) return@produceState
        value = null
        value = c.winOdds.get(deckId, statCards, identity, saltCard)
    }
    val tagged = remember(rows, saltCard) { rows.associate { it.item.id to tagsOf(it) }.filterValues { it.isNotEmpty() } }
    // Categories switched off in "Manage categories" (since 1.47): their cards go to one folded section at the bottom.
    val hiddenAll by c.settings.deckHidden.collectAsStateWithLifecycle()
    val hidden = hiddenAll[deckId].orEmpty().keys
    val allSections = remember(rows, groupBy, onlyBracket, tagged) {
        DeckGrouping.sections(if (onlyBracket) rows.filter { it.item.id in tagged } else rows, groupBy)
    }
    val sections = remember(allSections, hidden, groupBy) {
        if (groupBy != DeckGroupBy.CATEGORY) allSections else allSections.filter { it.title.lowercase() !in hidden }
    }
    val hiddenRows = remember(allSections, sections) {
        (allSections - sections.toSet()).flatMap { s -> s.rows }.sortedWith(compareBy({ it.item.category.lowercase() }, { it.item.card.name }))
    }
    // The maybeboard (and any other category left out of the deck on Archidekt), by its categories.
    val maybeSections = remember(maybeRows) { DeckGrouping.sections(maybeRows, DeckGroupBy.CATEGORY) }
    // Headings are folded by their title, for every deck; the maybeboard's apart from the deck's.
    val shownKeys = remember(sections, maybeSections, showMaybeboard) {
        sections.map { it.title } + if (showMaybeboard) maybeSections.map { maybeKey(it.title) } else emptyList()
    }
    fun sectionValue(section: DeckSection) = section.rows.sumOf { (it.unitPrice(priceType) ?: 0.0) * it.item.quantity }
    fun toggle(key: String) = c.settings.setDeckCollapsed(if (key in collapsed) collapsed - key else collapsed + key)

    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        if (r.deckId != deckId) return@LaunchedEffect
        c.decks.consumeResult()
        // A fresh import lands here already; its page shows whether scoring failed.
        // In the screen's scope: clearing the result restarts this effect, which would cancel the snackbar.
        if (r.openDeck == null) scope.launch { snackbar.showSnackbar(r.message) }
    }
    fun refresh() {
        if (!c.decks.refresh(deckId)) scope.launch { snackbar.showSnackbar("Wait for the current import to finish") }
    }

    // Editing (since 1.46): the logged-in Archidekt user's own decks and local decks.
    val account by c.archidekt.status.collectAsStateWithLifecycle()
    val ownDeck = deck?.let { d -> d.local || account.username?.equals(d.owner, true) == true } == true
    // An own Archidekt deck loaded before 1.46 is reloaded first (its lines need their Archidekt ids).
    val editable = ownDeck && deck?.let { c.deckEditor.ready(it) } == true
    val waitingMap by c.deckEditor.waiting.collectAsStateWithLifecycle(emptyMap())
    val waiting = waitingMap[deckId] ?: 0
    val sending by c.deckEditor.sending.collectAsStateWithLifecycle()
    val sendReport by c.deckEditor.report.collectAsStateWithLifecycle()
    val reconnects by c.network.reconnects.collectAsStateWithLifecycle()
    val online = remember(reconnects, waiting, sending) { c.network.online() }
    var selection by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var movingTo by remember { mutableStateOf<List<String>?>(null) }
    var categoryFor by remember { mutableStateOf<Pair<DeckCardRow, String?>?>(null) }
    var namingFor by remember { mutableStateOf<List<String>?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deletingCategory by remember { mutableStateOf<String?>(null) }
    var sendingDeck by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var renameDeck by remember { mutableStateOf(false) }
    var confirmDetach by remember { mutableStateOf(false) }
    var confirmAuto by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    val listState = rememberLazyListState()
    val categoryInfo = remember(deck?.categoryInfo) { DeckEdits.categories(deck?.categoryInfo) }
    LaunchedEffect(deckId, ownDeck) {
        if (ownDeck) c.deckEditor.prepare(deckId)?.let { snackbar.showSnackbar("To edit, the app needs the deck's latest list from Archidekt: $it") }
    }
    LaunchedEffect(sendReport) {
        val r = sendReport ?: return@LaunchedEffect
        if (r.deckId != deckId) return@LaunchedEffect
        c.deckEditor.consumeReport()
        scope.launch { snackbar.showSnackbar(r.message) }
    }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptyList() }
    fun edit(message: String, op: suspend () -> UndoAction) {
        scope.launch {
            val undo = op()
            if (snackbar.showUndo(message)) undo()
        }
    }
    fun toggleSelected(uid: String) {
        selection = if (uid in selection) selection - uid else selection + uid
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selection.isNotEmpty()) {
                val selectedLines = (archidektRows + maybeRows).filter { it.item.lineUid in selection }
                SelectionBar(
                    count = selectedLines.sumOf { it.item.quantity },
                    inMaybeboard = selectedLines.isNotEmpty() && selectedLines.all { it.item.maybeboard },
                    onClose = { selection = emptyList() },
                    onMove = { movingTo = selection },
                    onBoard = {
                        val toMaybe = !selectedLines.all { it.item.maybeboard }
                        val uids = selection
                        selection = emptyList()
                        edit(if (toMaybe) "Put ${uids.size} card(s) in the maybeboard" else "Put ${uids.size} card(s) in the deck") { c.deckEditor.toBoard(deckId, uids, toMaybe) }
                    },
                    onRemove = {
                        val uids = selection
                        selection = emptyList()
                        edit("Removed ${uids.size} card(s)") { c.deckEditor.remove(deckId, uids) }
                    },
                )
            } else TopAppBar(
                title = { Text(deck?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { c.settings.setDeckCompact(!compact) }) {
                        if (compact) Icon(Icons.Default.ViewList, "Show cards with pictures") else Icon(Icons.Default.ViewHeadline, "Compact view")
                    }
                    if (deck?.local != true) IconButton(onClick = ::refresh, enabled = job == null) { Icon(Icons.Default.Refresh, "Reload decklist from Archidekt") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    ScreenMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val local = deck?.local == true
                        if (!local) DropdownMenuItem(
                            text = { Text("Reload decklist from Archidekt") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = job == null,
                            onClick = { menu = false; refresh() },
                        )
                        // Editing (since 1.46).
                        if (local) {
                            DropdownMenuItem(
                                text = { Text("Send to Archidekt…") },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
                                enabled = account.username != null,
                                onClick = { menu = false; sendingDeck = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename deck") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menu = false; renameDeck = true },
                            )
                        } else if (editable) {
                            DropdownMenuItem(
                                text = { Text("Folder and visibility…") },
                                leadingIcon = { Icon(Icons.Default.Folder, null) },
                                onClick = { menu = false; folderDialog = true },
                            )
                        }
                        if (editable) {
                            DropdownMenuItem(
                                text = { Text("Manage categories…") },
                                leadingIcon = { Icon(Icons.Default.Category, null) },
                                onClick = { menu = false; nav.navigate("deck/$deckId/categories") },
                            )
                            DropdownMenuItem(
                                text = { Text("Auto-categorise all cards") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                                onClick = { menu = false; confirmAuto = true },
                            )
                        }
                        if (!local) DropdownMenuItem(
                            text = { Text("Detach (make it a local deck)") },
                            leadingIcon = { Icon(Icons.Default.LinkOff, null) },
                            onClick = { menu = false; confirmDetach = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (myPrintings) "Show Archidekt's printings" else "Show the printings I own") },
                            leadingIcon = { Icon(Icons.Default.Style, null) },
                            onClick = { menu = false; c.settings.setDeckShowsMyPrintings(deckId, !myPrintings) },
                        )
                        HorizontalDivider()
                        val other = if (groupBy == DeckGroupBy.CATEGORY) DeckGroupBy.TYPE else DeckGroupBy.CATEGORY
                        DropdownMenuItem(
                            text = { Text("Group by ${other.label.lowercase()}") },
                            leadingIcon = { Icon(Icons.Default.ViewList, null) },
                            onClick = { menu = false; groupBy = other },
                        )
                        DropdownMenuItem(
                            text = { Text(if (compact) "Show cards with pictures" else "Compact view") },
                            leadingIcon = { Icon(if (compact) Icons.Default.ViewList else Icons.Default.ViewHeadline, null) },
                            onClick = { menu = false; c.settings.setDeckCompact(!compact) },
                        )
                        DropdownMenuItem(
                            text = { Text("Show maybeboard") },
                            leadingIcon = { Icon(Icons.Default.Inventory2, null) },
                            trailingIcon = { if (showMaybeboard) Icon(Icons.Default.Check, "On") },
                            onClick = { menu = false; c.settings.setDeckMaybeboard(!showMaybeboard) },
                        )
                        val anyOpen = shownKeys.any { it !in collapsed }
                        DropdownMenuItem(
                            text = { Text(if (anyOpen) "Collapse all sections" else "Expand all sections") },
                            leadingIcon = { Icon(if (anyOpen) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore, null) },
                            onClick = {
                                menu = false
                                c.settings.setDeckCollapsed(if (anyOpen) collapsed + shownKeys else collapsed - shownKeys.toSet())
                            },
                        )
                        deck?.takeIf { !it.local }?.let { d ->
                            DropdownMenuItem(
                                text = { Text("Open on Archidekt") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = { menu = false; runCatching { uriHandler.openUri(d.archidektUrl) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Open on edhpowerlevel.com") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = {
                                    menu = false
                                    scope.launch { c.decks.edhUrl(d.archidektId)?.let { runCatching { uriHandler.openUri(it) } } }
                                },
                            )
                            d.saltUrl?.let { url ->
                                DropdownMenuItem(
                                    text = { Text("Open on Commander Salt") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                    onClick = { menu = false; runCatching { uriHandler.openUri(url) } },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Recommendations (EDHREC, recommander.cards)") },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                            onClick = { menu = false; nav.navigate("deck/$deckId/recs") },
                        )
                        DropdownMenuItem(
                            text = { Text("Cards I'm missing") },
                            leadingIcon = { Icon(Icons.Default.RemoveShoppingCart, null) },
                            onClick = { menu = false; nav.navigate("deck/$deckId/missing") },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to collection…") },
                            leadingIcon = { Icon(Icons.Default.LibraryAdd, null) },
                            onClick = { menu = false; addingToCollection = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete deck") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (deck != null) {
                ExtendedFloatingActionButton(
                    onClick = { nav.openSearch(CardTarget.ToDeck(deckId)) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add card") },
                )
            }
        },
    ) { pad ->
        val d = deck
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val value = remember(rows, priceType, com.mtgtrader.data.Pricing.source, com.mtgtrader.data.Pricing.others, com.mtgtrader.data.Pricing.usdPerEuro) { rows.sumOf { (it.unitPrice(priceType) ?: 0.0) * it.item.quantity } }
        // Where a dragged card would go: the category of the row or heading under it (since 1.46).
        val dropTargets = remember(sections, maybeSections, groupBy, categoryInfo) {
            if (groupBy != DeckGroupBy.CATEGORY) emptyMap()
            else buildMap<Any, String> {
                for (sct in sections) {
                    if (sct.title == DeckToCollection.ADDED_IN_APP) continue
                    put("h_${sct.title}", sct.title)
                    sct.rows.forEach { put("c_${it.item.id}", sct.title) }
                }
                for (sct in maybeSections) {
                    put("mh_${sct.title}", sct.title)
                    sct.rows.forEach { put("m_${it.item.id}", sct.title) }
                }
            }
        }
        // Read when the drag ends too, so always the latest list and targets (not the ones when the drag began).
        val targets by rememberUpdatedState(dropTargets)
        fun dropTarget(d: DragInfo): String? {
            val info = listState.layoutInfo.visibleItemsInfo
            val me = info.firstOrNull { it.key == d.key } ?: return null
            val center = me.offset + d.dy + me.size / 2
            return info.firstOrNull { it.key != d.key && center >= it.offset && center < it.offset + it.size }?.let { targets[it.key] }
        }
        val dropCategory: String? = drag?.let(::dropTarget)
        @Composable
        fun rowEditing(row: DeckCardRow, key: String): RowEditing? {
            if (!editable || row.item.commander) return null
            val uid = row.item.lineUid
            val dragging = groupBy == DeckGroupBy.CATEGORY && selection.isEmpty()
            return RowEditing(
                selected = if (selection.isEmpty()) null else uid in selection,
                onLongClick = { toggleSelected(uid) },
                handle = if (!dragging) null else {
                    {
                        Icon(
                            Icons.Default.DragIndicator,
                            "Drag to another category",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp).size(24.dp).pointerInput(uid) {
                                detectDragGestures(
                                    onDragStart = { drag = DragInfo(uid, key, row.item.category, row.item.card.name) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag = drag?.let { it.copy(dy = it.dy + amount.y) }
                                    },
                                    onDragEnd = {
                                        val d = drag
                                        val to = d?.let(::dropTarget)
                                        drag = null
                                        if (d != null && to != null && !to.equals(d.category, true)) {
                                            edit("Moved ${d.name} to $to") { c.deckEditor.move(deckId, listOf(d.uid), to) }
                                        }
                                    },
                                    onDragCancel = { drag = null },
                                )
                            },
                        )
                    }
                },
            )
        }
        fun onRow(row: DeckCardRow) {
            if (selection.isNotEmpty()) {
                if (!row.item.commander) toggleSelected(row.item.lineUid)
            } else selected = row
        }
        @Composable
        fun dragged(key: String, content: @Composable () -> Unit) = LiftableRow(drag?.takeIf { it.key == key }?.dy, content)
        @Composable
        fun categoryMenu(title: String, rows: List<DeckCardRow>): (@Composable () -> Unit)? {
            if (!editable || groupBy != DeckGroupBy.CATEGORY || title == DeckToCollection.ADDED_IN_APP || title.equals(DeckEdits.COMMANDER, true)) return null
            return {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "$title: more", Modifier.size(18.dp)) }
                    ScreenMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(text = { Text("Add a card here") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = {
                            open = false
                            nav.openSearch(CardTarget.ToDeck(deckId, title))
                        })
                        if (rows.isNotEmpty()) DropdownMenuItem(text = { Text("Select all ${rows.sumOf { it.item.quantity }}") }, leadingIcon = { Icon(Icons.Default.Checklist, null) }, onClick = {
                            open = false
                            selection = (selection + rows.filter { !it.item.commander }.map { it.item.lineUid }).distinct()
                        })
                        DropdownMenuItem(text = { Text("Rename…") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { open = false; renaming = title })
                        DropdownMenuItem(text = { Text("Delete category…") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { open = false; deletingCategory = title })
                    }
                }
            }
        }
        LazyColumn(Modifier.padding(pad), state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp)) {
            job?.takeIf { it.deckId == deckId }?.let { j -> item(key = "job") { Box(Modifier.padding(bottom = 8.dp)) { JobCard(j) } } }
            if (waiting > 0) {
                item(key = "waiting") {
                    WaitingBanner(waiting, offline = !online, loggedIn = account.username != null, sending = sending) {
                        scope.launch { snackbar.showSnackbar(c.deckEditor.send(deckId).message.ifEmpty { "Nothing to send" }) }
                    }
                }
            }
            item(key = "header") { DeckHeader(d, rows.sumOf { it.item.quantity }, value, priceType) }
            item(key = "printings") {
                val differ = matched.original.size
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            !myPrintings -> "Showing Archidekt's printings."
                            differ == 0 -> "Showing your printings: they match Archidekt's."
                            else -> "Showing the printings you own: $differ card(s) differ from Archidekt."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { c.settings.setDeckShowsMyPrintings(deckId, !myPrintings) }) {
                        Text(if (myPrintings) "Show Archidekt's" else "Show mine")
                    }
                }
            }
            item(key = "rulezero") {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showing = RuleZeroCard.BRACKET }, enabled = d.saltId != null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Shield, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bracket card")
                    }
                    Button(onClick = { showing = RuleZeroCard.POWER }, enabled = d.saltId != null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Bolt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Power card")
                    }
                }
            }
            item(key = "recs") {
                OutlinedButton(onClick = { nav.navigate("deck/$deckId/recs") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Card recommendations")
                }
            }
            item(key = "scores") {
                ScoresCard(d, source, busy = job != null, onRetry = ::refresh) {
                    if (!c.decks.rescore(deckId)) scope.launch { snackbar.showSnackbar("Wait for the current import to finish") }
                }
            }
            if (tagged.isNotEmpty()) {
                item(key = "bracketcards") { BracketSummary(tagged.values, saltCard?.allCombos.orEmpty(), onlyBracket) { onlyBracket = it } }
            }
            // The stats and tokens, folded until opened (remembered for every deck).
            val statsFolded = DeckStats.STATS !in statsOpen
            item(key = "stats_h") { StatsHeading("Stats", statsFolded) { toggleStats(DeckStats.STATS) } }
            if (!statsFolded) {
                // Only decks with a commander keep "any colour" sources to its colours.
                val identity = d.colorIdentity.takeIf { d.commanders.isNotBlank() }
                item(key = "stats_curve") { ManaCurveCard(remember(statCards) { DeckStats.curve(statCards) }) }
                item(key = "stats_colours") {
                    ColoursCard(
                        costs = remember(statCards) { DeckStats.costs(statCards) },
                        sources = remember(statCards, identity, landsOnly, colourless) { DeckStats.sources(statCards, identity, landsOnly, colourless) },
                        landsOnly = landsOnly,
                        colourless = colourless,
                        landCount = statCards.filter { it.isLand }.sumOf { it.quantity },
                        detailsMissing = statCards.count { it.producedMana == null },
                        onLandsOnly = c.settings::setDeckLandsOnly,
                        onColourless = c.settings::setDeckColourless,
                    )
                }
                if (d.commanders.isNotBlank()) {
                    item(key = "stats_odds") {
                        if (statCards.any { it.commander }) CommanderOddsCard(odds, statCards.count { it.oracleText == null })
                    }
                    item(key = "stats_win") {
                        if (statCards.any { it.commander }) WinningCard(win, statCards.count { it.oracleText == null })
                    }
                }
            }
            if (tokens.isNotEmpty()) {
                val tokensFolded = DeckStats.TOKENS !in statsOpen
                item(key = "tokens_h") { StatsHeading("Tokens (${tokens.size})", tokensFolded) { toggleStats(DeckStats.TOKENS) } }
                if (!tokensFolded) {
                    tokens.chunked(3).forEachIndexed { i, row -> item(key = "tokens_$i") { TokenRow(row) { openToken = it.key } } }
                    item(key = "tokens_note") {
                        Text(
                            "Tap a token to see it large and choose its printing for this deck.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            // The deck's games from the life tracker (since 1.47), folded until opened.
            if (deckGames.isNotEmpty()) {
                val gamesFolded = GAMES_OPEN !in statsOpen
                item(key = "games_h") { StatsHeading("Games (${deckGames.size})", gamesFolded) { toggleStats(GAMES_OPEN) } }
                if (!gamesFolded) item(key = "games_card") {
                    val r = remember(deckGames) { com.mtgtrader.data.GameStats.decks(deckGames).firstOrNull { it.deckId == deckId } }
                    r?.let { DeckGamesCard(it, deckGames, deckId) { g -> g.uid?.let { uid -> nav.navigate("games/game/$uid") } } }
                }
            }
            sections.forEach { section ->
                val folded = section.title in collapsed
                item(key = "h_${section.title}") {
                    SectionHeading(section, sectionValue(section), folded, highlight = dropCategory == section.title, menu = categoryMenu(section.title, section.rows)) { toggle(section.title) }
                }
                // Cards "Added in app" on an editable deck: into the deck, or to Archidekt's maybeboard (since 1.46).
                if (editable && section.title == DeckToCollection.ADDED_IN_APP && !folded) {
                    item(key = "added_actions") {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Only in this app so far.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                edit("Put ${section.count} card(s) in the deck") { c.deckEditor.move(deckId, section.rows.map { it.item.lineUid }, DeckEditor.AUTO) }
                            }) { Text("Into the deck") }
                            OutlinedButton(onClick = {
                                edit("Put ${section.count} card(s) in the maybeboard") { c.deckEditor.addedToMaybeboard(deckId) }
                            }) { Text("To maybeboard") }
                        }
                    }
                }
                if (!folded) {
                    items(section.rows, key = { "c_${it.item.id}" }) { row ->
                        val cost = costs[row.item.card.name]
                        val key = "c_${row.item.id}"
                        val editing = rowEditing(row, key)
                        dragged(key) {
                            if (compact) DeckCompactRow(row, priceType, tagged[row.item.id].orEmpty(), cost, editing) { onRow(row) }
                            else DeckCardRowView(row, priceType, tagged[row.item.id].orEmpty(), cost, editing) { onRow(row) }
                        }
                    }
                }
            }
            // Cards of the categories switched off in "Manage categories", folded until opened (since 1.47).
            // Categories without cards aren't shown at all.
            if (hiddenRows.isNotEmpty()) {
                val hiddenSection = com.mtgtrader.data.DeckSection(HIDDEN_TITLE, hiddenRows)
                val folded = HIDDEN_OPEN !in collapsed
                item(key = "hidden_h") { SectionHeading(hiddenSection, sectionValue(hiddenSection), folded) { toggle(HIDDEN_OPEN) } }
                if (!folded) {
                    items(hiddenRows, key = { "x_${it.item.id}" }) { row ->
                        val cost = costs[row.item.card.name]
                        val key = "x_${row.item.id}"
                        val editing = rowEditing(row, key)
                        dragged(key) {
                            if (compact) DeckCompactRow(row, priceType, tagged[row.item.id].orEmpty(), cost, editing) { onRow(row) }
                            else DeckCardRowView(row, priceType, tagged[row.item.id].orEmpty(), cost, editing) { onRow(row) }
                        }
                    }
                }
            }
            if (showMaybeboard) {
                item(key = "maybeboard") {
                    Column(Modifier.padding(top = 20.dp)) {
                        HorizontalDivider()
                        Text(
                            "Maybeboard" + if (maybeRows.isNotEmpty()) " · ${maybeRows.sumOf { it.item.quantity }} cards" else "",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Text(
                            if (maybeRows.isEmpty()) "No maybeboard cards. Decks loaded before version 1.42 get theirs when the decklist is reloaded from Archidekt (⟳)."
                            else "On Archidekt, but left out of the deck: not counted in its cards or value.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                maybeSections.forEach { section ->
                    val key = maybeKey(section.title)
                    val folded = key in collapsed
                    item(key = "mh_${section.title}") {
                        SectionHeading(section, sectionValue(section), folded, highlight = dropCategory == section.title, menu = categoryMenu(section.title, section.rows)) { toggle(key) }
                    }
                    if (!folded) {
                        items(section.rows, key = { "m_${it.item.id}" }) { row ->
                            val cost = costs[row.item.card.name]
                            val rowKey = "m_${row.item.id}"
                            val editing = rowEditing(row, rowKey)
                            dragged(rowKey) {
                                if (compact) DeckCompactRow(row, priceType, emptySet(), cost, editing) { onRow(row) }
                                else DeckCardRowView(row, priceType, emptySet(), cost, editing) { onRow(row) }
                            }
                        }
                    }
                }
            }
        }
    }

    showing?.let { card -> deck?.let { d -> RuleZeroDialog(d, card) { showing = null } } }
    tokens.firstOrNull { it.key == openToken }?.let { t ->
        TokenDialog(t, onPick = { c.settings.setDeckTokenPick(deckId, t.key, it) }) { openToken = null }
    }
    selected?.let { row ->
        val key = DeckPrintings.nameKey(row.item.card.name)
        val inDeck = !row.item.maybeboard
        DeckCardDialog(
            row, priceType, if (inDeck) tagsOf(row) else emptySet(), if (inDeck) saltCard?.combosOf(row.item.card.name).orEmpty() else emptyList(),
            original = matched.original[row.item.id],
            archidektCard = archidektRows.firstOrNull { it.item.id == row.item.id }?.item,
            cost = costs[row.item.card.name],
            copies = if (inDeck && myPrintings && !DeckToCollection.isBasic(key)) owned.filter { DeckPrintings.nameKey(it.item.card.name) == key } else emptyList(),
            binders = binders,
            picked = picks[key],
            onPick = { value ->
                c.settings.setDeckPick(deckId, key, value)
                selected = null
            },
            onRemove = if (editable) {
                {
                    selected = null
                    edit("Removed ${row.item.card.name}") { c.deckEditor.remove(deckId, listOf(row.item.lineUid)) }
                }
            } else if (row.item.addedInApp) {
                {
                    selected = null
                    scope.launch { c.decks.removeAddedCard(row.item.id) }
                }
            } else null,
            editControls = if (!editable) null else {
                {
                    val uid = row.item.lineUid
                    val auto by produceState<String?>(null, row.item.card.scryfallId) { value = c.deckEditor.autoCategories(listOf(row.item.card.scryfallId))[row.item.card.scryfallId] }
                    CardEditControls(
                        category = row.item.category,
                        autoCategory = auto,
                        maybeboard = row.item.maybeboard,
                        quantity = row.item.quantity,
                        onPickCategory = {
                            selected = null
                            categoryFor = row to auto
                        },
                        onBoard = { maybe ->
                            selected = null
                            edit(if (maybe) "Put ${row.item.card.name} in the maybeboard" else "Put ${row.item.card.name} in the deck") { c.deckEditor.toBoard(deckId, listOf(uid), maybe) }
                        },
                        onQuantity = { n ->
                            selected = null
                            edit("${row.item.card.name}: $n " + if (n == 1) "copy" else "copies") { c.deckEditor.setQuantity(deckId, uid, n) }
                        },
                    )
                }
            },
        ) { selected = null }
    }
    if (addingToCollection) {
        deck?.let { d ->
            AddDeckToCollectionDialog(d, onDismiss = { addingToCollection = false }) { onlyMissing, skipBasics, choice ->
                addingToCollection = false
                scope.launch {
                    val binderId = c.repo.resolve(choice)
                    val (n, undo) = c.decks.addDeckToCollection(deckId, onlyMissing, skipBasics, binderId)
                    val where = choice.newName ?: binderName(binderId, c.db.binderDao().all())
                    if (n == 0) snackbar.showSnackbar("Nothing to add: you already own every card")
                    else if (snackbar.showUndo("Added $n card(s) to $where")) undo()
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${deck?.name ?: "deck"}?") },
            text = {
                Text(
                    when {
                        deck?.local == true -> "This deck only exists in this app (and on your other phone after a sync), so it's gone for good. You can undo right after."
                        waiting > 0 -> "The deck is only removed from this app; it stays on Archidekt. Its $waiting edit(s) not sent to Archidekt yet are lost."
                        else -> "The deck is only removed from this app; it stays on Archidekt and Commander Salt."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        if (deck?.local == true) c.deckEditor.deleteLocal(deckId) else c.decks.delete(deckId)
                        nav.popBackStack()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    // ---- Editing dialogs (since 1.46) ----
    val categoryCounts = remember(archidektRows, maybeRows, categoryInfo) {
        val counts = (archidektRows + maybeRows).filter { it.item.category != DeckToCollection.ADDED_IN_APP && !it.item.commander }
            .groupBy { it.item.category }.mapValues { (_, l) -> l.sumOf { it.item.quantity } }
        categoryChoices(counts.keys + categoryInfo.map { it.name }).filter { !it.equals(DeckEdits.COMMANDER, true) }.map { it to (counts[it] ?: 0) }
    }
    categoryFor?.let { (row, auto) ->
        val uid = row.item.lineUid
        CategoryPickerSheet(
            "Category of ${row.item.card.name}",
            categoryCounts.filter { it.first.lowercase() !in hidden || it.first.equals(row.item.category, true) },
            onDismiss = { categoryFor = null },
            onNew = { categoryFor = null; namingFor = listOf(uid) },
            current = row.item.category,
            autoLabel = "Auto" + (auto?.let { " ($it)" } ?: ""),
        ) { to ->
            categoryFor = null
            edit("Moved ${row.item.card.name}" + if (to == DeckEditor.AUTO) " to its auto category" else " to $to") { c.deckEditor.move(deckId, listOf(uid), to) }
        }
    }
    movingTo?.let { uids ->
        CategoryPickerSheet(
            "Move ${uids.size} card(s) to",
            categoryCounts.filter { it.first.lowercase() !in hidden },
            onDismiss = { movingTo = null },
            onNew = { movingTo = null; namingFor = uids },
        ) { to ->
            movingTo = null
            selection = emptyList()
            edit("Moved ${uids.size} card(s)" + if (to == DeckEditor.AUTO) " to their auto categories" else " to $to") { c.deckEditor.move(deckId, uids, to) }
        }
    }
    namingFor?.let { uids ->
        TextInputDialog("New category", "Category name", "", "Move", onDismiss = { namingFor = null }) { name ->
            namingFor = null
            selection = emptyList()
            edit("Moved ${uids.size} card(s) to $name") { c.deckEditor.move(deckId, uids, name) }
        }
    }
    renaming?.let { old ->
        TextInputDialog("Rename \"$old\"", "Category name", old, "Rename", onDismiss = { renaming = null }) { name ->
            renaming = null
            if (name != old) edit("Renamed $old to $name") { c.deckEditor.renameCategory(deckId, old, name) }
        }
    }
    deletingCategory?.let { name ->
        val count = (archidektRows + maybeRows).filter { it.item.category.equals(name, true) }.sumOf { it.item.quantity }
        DeleteCategoryDialog(name, count, categoryCounts.map { it.first }.filterNot { it.equals(name, true) }, onDismiss = { deletingCategory = null }, onArchidekt = deck?.local == false) { to ->
            deletingCategory = null
            edit("Deleted the category $name") { c.deckEditor.deleteCategory(deckId, name, to) }
        }
    }
    if (confirmAuto) {
        AlertDialog(
            onDismissRequest = { confirmAuto = false },
            title = { Text("Auto-categorise all cards?") },
            text = { Text("Every card (not the commander or the maybeboard) goes into Archidekt's auto category for it; cards without one go by card type. You can undo right after.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAuto = false
                    edit("Put every card in its auto category") { c.deckEditor.autoCategoriseAll(deckId) }
                }) { Text("Auto-categorise") }
            },
            dismissButton = { TextButton(onClick = { confirmAuto = false }) { Text("Cancel") } },
        )
    }
    if (confirmDetach) {
        AlertDialog(
            onDismissRequest = { confirmDetach = false },
            title = { Text("Make ${deck?.name ?: "it"} a local deck?") },
            text = {
                Text(
                    "The deck becomes a local deck: you can change it freely here, and it's no longer reloaded from Archidekt " +
                        "(the deck on Archidekt isn't changed). Its scores stay until the list changes." +
                        if (waiting > 0) " Its $waiting edit(s) not sent to Archidekt yet stay only in the local deck." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDetach = false
                    scope.launch {
                        val id = c.deckEditor.detach(deckId)
                        nav.navigate("deck/$id") { popUpTo("deck/{id}") { inclusive = true } }
                    }
                }) { Text("Make it local") }
            },
            dismissButton = { TextButton(onClick = { confirmDetach = false }) { Text("Cancel") } },
        )
    }
    if (renameDeck) {
        TextInputDialog("Rename deck", "Deck name", deck?.name.orEmpty(), "Rename", onDismiss = { renameDeck = false }) { name ->
            renameDeck = false
            scope.launch { c.deckEditor.renameLocal(deckId, name) }
        }
    }
    if (sendingDeck) {
        deck?.let { d ->
            val decksAll by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
            val own = decksAll.filter { !it.local && it.owner.equals(account.username, true) }
            SendToArchidektDialog(d, own, onDismiss = { sendingDeck = false }) { id, message ->
                sendingDeck = false
                c.repo.reportImport(message)
                nav.navigate("deck/$id") { popUpTo("deck/{id}") { inclusive = true } }
            }
        }
    }
    if (folderDialog) {
        deck?.let { d ->
            FolderVisibilityDialog(d, onDismiss = { folderDialog = false }) { message ->
                folderDialog = false
                scope.launch { snackbar.showSnackbar(message) }
            }
        }
    }
}

@Composable
private fun DeckHeader(deck: Deck, cardCount: Int, value: Double, priceType: PriceType) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeckArt(deck, Modifier.fillMaxWidth().height(150.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorPips(deck.colorIdentity, size = 14)
            Spacer(Modifier.width(8.dp))
            Text(deck.commanders, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "${cardCount.takeIf { it > 0 } ?: deck.cardCount} cards · ${Fmt.money(value)} (${priceType.short})" + if (deck.owner.isNotBlank()) " · by ${deck.owner}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScoresCard(deck: Deck, source: PowerSource, busy: Boolean, onRetry: () -> Unit, onRescore: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (!deck.scored) {
                Text("Not scored yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    deck.scoreError?.let { "Commander Salt couldn't score this deck: $it" }
                        ?: "Commander Salt scores the deck when it's imported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!busy) TextButton(onClick = onRetry) { Text("Try again") }
                return@Column
            }
            Row(Modifier.fillMaxWidth()) {
                ScoreColumn("Power level", deck.power(source)?.let { Scores.power(it, source) }, "out of 10", Modifier.weight(1f))
                ScoreColumn("Realistic bracket", deck.bracketRealistic?.toString(), Brackets.name(deck.bracketRealistic), Modifier.weight(1f))
                ScoreColumn("Baseline bracket", deck.bracketBaseline?.toString(), Brackets.name(deck.bracketBaseline), Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            val extras = listOfNotNull(deck.saltPercent?.let { "Salt ${it.roundToInt()}%" }, deck.archetype)
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Realistic: how the deck actually plays. Baseline: WotC's bracket rules to the letter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (source.external) {
                val power = deck.power(source)
                val error = deck.powerError(source)
                val sv = deck.scrollVaultReading.takeIf { source == PowerSource.SCROLLVAULT && power != null }
                val other = deck.powerLevel?.let { " (Commander Salt: ${Scores.power(it)})" } ?: ""
                Text(
                    when {
                        power != null -> "Power level from ${source.site}" + (sv?.margin?.let { " (±${Scores.power(it)})" } ?: "") +
                            (deck.powerAt(source)?.let { ", ${Fmt.dateTime(it)}" } ?: "") + other
                        error != null -> "${source.site} couldn't rate this deck ($error); refresh the bracket and power level to try again." + other
                        else -> "Waiting for ${source.site}'s power level…$other"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (power == null && error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                sv?.let { r ->
                    val facts = listOfNotNull(
                        r.typicalWin?.let { "typical win turn $it" + (r.earliestWin?.let { e -> " (earliest $e)" } ?: "") },
                        r.bracket?.let { "its bracket $it" + (r.borderline?.let { b -> ", borderline $b" } ?: "") },
                    )
                    if (facts.isNotEmpty()) Text("ScrollVault: " + facts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            deck.scoredAt?.let {
                Text("Scored by Commander Salt, ${Fmt.dateTime(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            deck.scoreError?.let {
                Text(
                    "The last update couldn't be scored ($it), so these are the earlier scores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!busy) {
                TextButton(onClick = onRescore, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh bracket and power level")
                }
            }
        }
    }
}

@Composable
private fun ScoreColumn(label: String, value: String?, caption: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text(value ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(caption ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A card row's look while editing (since 1.46): [selected] marks it (null when nothing is being selected), a long
 * press calls [onLongClick], and [handle] is drawn at the end (the drag handle).
 */
internal data class RowEditing(val selected: Boolean? = null, val onLongClick: (() -> Unit)? = null, val handle: (@Composable () -> Unit)? = null)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.rowClicks(editing: RowEditing?, onClick: () -> Unit): Modifier = composed {
    val selectedColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    this.background(if (editing?.selected == true) selectedColor else Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = editing?.onLongClick)
}

@Composable
private fun SelectMark(editing: RowEditing?) {
    val selected = editing?.selected ?: return
    Icon(
        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        if (selected) "Selected" else "Not selected",
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(end = 8.dp).size(20.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeckCardRowView(row: DeckCardRow, priceType: PriceType, tags: Set<BracketTag>, cost: String?, editing: RowEditing? = null, onClick: () -> Unit) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).rowClicks(editing, onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectMark(editing)
        CardThumb(item.card.imageUrl, width = 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                (if (item.quantity > 1) "${item.quantity}× " else "") + item.card.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The mana cost where the set and number were (since 1.43); they're in the card's window.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ManaCostView(cost, modifier = Modifier.align(Alignment.CenterVertically))
                FinishTag(item.card, item.finish)
                if (item.addedInApp) Tag("ADDED IN APP")
                BracketTag.entries.filter { it in tags }.forEach { t ->
                    val (bg, fg) = tagColors(t)
                    Tag(t.label, bg, fg)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(unit?.let { it * item.quantity }), style = MaterialTheme.typography.bodyMedium)
            TrendBadge(row.trend, style = MaterialTheme.typography.labelSmall)
        }
        editing?.handle?.invoke()
    }
}

/** How a maybeboard heading is remembered as folded, apart from a deck section with the same title. */
private fun maybeKey(title: String) = "maybeboard:$title"

/** The section of the cards in categories switched off in "Manage categories" (since 1.47), and its "opened" key. */
internal const val HIDDEN_TITLE = "Hidden categories"
private const val HIDDEN_OPEN = "\u0000hidden-open"

/** The deck page's "Games" section is open (with the Stats' and Tokens' keys). Since 1.47. */
private const val GAMES_OPEN = "games"

/**
 * A section's title and card count, with what its cards are worth (since 1.43); tapping it folds the
 * section away or opens it again. Since 1.42.
 */
@Composable
internal fun SectionHeading(
    section: DeckSection,
    value: Double?,
    folded: Boolean,
    count: Int? = null,
    highlight: Boolean = false,
    menu: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(4.dp))
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onToggle)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${section.title} (${count ?: section.count})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(Fmt.money(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            menu?.invoke()
            Icon(
                if (folded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                if (folded) "Show ${section.title}" else "Hide ${section.title}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp).size(20.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * How the compact view marks a card's bracket tags after its name: in full, shortened, or as dots,
 * whichever still leaves the whole name readable. Since 1.43.
 */
object DeckMarks {
    enum class Level { FULL, SHORT, DOTS }

    fun full(t: BracketTag) = if (t == BracketTag.GAME_CHANGER) "GC" else t.label

    fun short(t: BracketTag) = when (t) {
        BracketTag.GAME_CHANGER -> "GC"
        BracketTag.COMBO, BracketTag.OTHER_COMBO -> "C2"
        BracketTag.COMBO_3, BracketTag.OTHER_COMBO_3 -> "C3+"
        BracketTag.EXTRA_TURN -> "TURN"
        BracketTag.LAND_DENIAL -> "LD"
        BracketTag.TUTOR -> "TUTOR"
        BracketTag.FAST_MANA -> "FAST"
    }

    /**
     * The fullest marks that fit next to the whole name in [available] width (all in pixels): the name
     * is never shortened for them; when even dots don't fit, the name is cut off with "…".
     */
    fun level(available: Int, name: Int, extras: Int, full: Int, short: Int): Level = when {
        name + extras + full <= available -> Level.FULL
        name + extras + short <= available -> Level.SHORT
        else -> Level.DOTS
    }
}

/** A card being dragged to another category: its line, list key and category, and how far it moved. Since 1.46. */
/**
 * A deck row that can be dragged: [lift] is how far it has moved while it's being dragged (null otherwise). The
 * row is lifted in the same box it always sits in: wrapping it in a new one when the drag began made the row (and
 * its drag handle) start over, which cancelled the drag at once (fixed in 1.47).
 */
@Composable
internal fun LiftableRow(lift: Float?, content: @Composable () -> Unit) {
    val lifted = if (lift == null) Modifier else Modifier.zIndex(1f).graphicsLayer { translationY = lift; shadowElevation = 12f }
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))
    Box(lifted) { content() }
}

private data class DragInfo(val uid: String, val key: String, val category: String, val name: String, val dy: Float = 0f)

private val MarkLabelStyle = TextStyle(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold)

/**
 * A tag's colours: red for WotC's bracket rules (and combos that count for the bracket), blue for combos that
 * don't (since 1.46), grey for tutors and fast mana.
 */
@Composable
internal fun tagColors(t: BracketTag): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when {
        t.otherCombo -> if (dark) Color(0xFF0F4C81) to Color(0xFFD6E6FF) else Color(0xFFD6E6FF) to Color(0xFF0A2F55)
        t.strict -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** The dot a tag shrinks to when a long name needs the room. */
@Composable
internal fun dotColor(t: BracketTag): Color = when {
    t.otherCombo -> OtherComboDot
    t.strict -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

private val OtherComboDot = Color(0xFF4A8FE0)
private val FoilMark = Color(0xFFB083E8)

/** The card's name followed by its marks: ✦ foil, + added in the app, then its bracket tags (see [DeckMarks]). */
@Composable
private fun NameWithMarks(name: String, tags: Set<BracketTag>, foil: Boolean, added: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val nameStyle = MaterialTheme.typography.bodyMedium
    val markStyle = MaterialTheme.typography.labelMedium
    val ordered = BracketTag.entries.filter { it in tags }
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth
        val level = remember(name, ordered, foil, added, available, nameStyle, density) {
            fun w(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            val chip = with(density) { 10.dp.roundToPx() }
            DeckMarks.level(
                available,
                name = w(name, nameStyle),
                extras = (if (foil) w(" ✦", markStyle) else 0) + (if (added) w(" +", markStyle) else 0),
                full = ordered.sumOf { w(DeckMarks.full(it), MarkLabelStyle) + chip },
                short = ordered.sumOf { w(DeckMarks.short(it), MarkLabelStyle) + chip },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (foil) Text(" ✦", style = markStyle, color = FoilMark, maxLines = 1)
            if (added) Text(" +", style = markStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
            ordered.forEach { t ->
                if (level == DeckMarks.Level.DOTS) {
                    Box(Modifier.padding(start = 4.dp).size(7.dp).clip(CircleShape).background(dotColor(t)))
                } else {
                    val (bg, fg) = tagColors(t)
                    Text(
                        if (level == DeckMarks.Level.FULL) DeckMarks.full(t) else DeckMarks.short(t),
                        style = MarkLabelStyle,
                        maxLines = 1,
                        softWrap = false,
                        color = fg,
                        modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(3.dp))
                            .background(bg)
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * One line per card, like Archidekt's list: amount, name and its marks, mana cost, price. The set and
 * number are in the card's window. Since 1.42; on one line since 1.43.
 */
@Composable
internal fun DeckCompactRow(row: DeckCardRow, priceType: PriceType, tags: Set<BracketTag>, cost: String?, editing: RowEditing? = null, onClick: () -> Unit) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Column(Modifier.fillMaxWidth().rowClicks(editing, onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SelectMark(editing)
            Text(
                "${item.quantity}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
            NameWithMarks(item.card.displayName, tags, item.foil, item.addedInApp, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            ManaCostView(cost)
            Text(
                Fmt.money(unit?.let { it * item.quantity }),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 62.dp).padding(start = 6.dp),
            )
            editing?.handle?.invoke()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun AddDeckToCollectionDialog(deck: Deck, onDismiss: () -> Unit, onConfirm: (onlyMissing: Boolean, skipBasics: Boolean, BinderChoice) -> Unit) {
    val c = LocalContext.current.container
    val counts by produceState<DeckCollectionCounts?>(null, deck.archidektId) { value = c.decks.collectionCounts(deck.archidektId) }
    var onlyMissing by remember { mutableStateOf(true) }
    var skipBasics by remember { mutableStateOf(true) }
    var choice by remember { mutableStateOf(BinderChoice(newName = deck.name)) }
    // Adding the same deck again goes into its binder from last time.
    LaunchedEffect(Unit) { c.db.binderDao().byName(deck.name)?.let { choice = BinderChoice(it.id) } }
    val n = counts?.count(onlyMissing, skipBasics)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${deck.name} to the collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Adds the exact printings and finishes from the decklist, as Near Mint English copies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RadioRow("Only cards I don't own yet (any printing counts)" + (counts?.let { " · ${it.count(true, skipBasics)}" } ?: ""), onlyMissing) { onlyMissing = true }
                RadioRow("All cards" + (counts?.let { " · ${it.count(false, skipBasics)}" } ?: ""), !onlyMissing) { onlyMissing = false }
                CheckRow("Skip basic lands", skipBasics) { skipBasics = it }
                BinderPicker("Put them in", choice, { choice = it }, suggestedName = deck.name, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(onlyMissing, skipBasics, choice) }, enabled = n != null && choice.isValid) {
                Text(if (n == null) "Add" else "Add $n card(s)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeckCardDialog(
    row: DeckCardRow,
    priceType: PriceType,
    tags: Set<BracketTag>,
    combos: List<SaltCombo>,
    /** Archidekt's printing, when another one is shown. */
    original: CardRef?,
    archidektCard: DeckCard?,
    cost: String?,
    /** Your copies of the card, to choose which one the deck shows (empty when the deck shows Archidekt's printings). */
    copies: List<CollectionRow>,
    binders: List<Binder>,
    picked: String?,
    onPick: (String?) -> Unit,
    onRemove: (() -> Unit)?,
    editControls: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val c = LocalContext.current.container
    val uriHandler = LocalUriHandler.current
    val item = row.item
    val owned by remember { c.db.collectionDao().observeOwned() }.collectAsStateWithLifecycle(emptyList())
    val ownedHere = owned.firstOrNull { it.scryfallId == item.card.scryfallId }?.qty ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.card.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardThumb(item.card.imageUrl, width = 72, enlargeable = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.card.setName, style = MaterialTheme.typography.bodyMedium)
                        SetLine(item.card)
                        ManaCostView(cost, modifier = Modifier.padding(vertical = 2.dp))
                        Text("${item.types} · ${item.category}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (ownedHere > 0) "$ownedHere of this printing in your collection" else "Not in your collection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { runCatching { uriHandler.openUri(item.card.cardmarketUrl) } }) {
                            Text("Open on Cardmarket")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
                // On a deck that can be edited: its category, deck or maybeboard, and copies (since 1.46).
                editControls?.let {
                    HorizontalDivider()
                    it()
                    HorizontalDivider()
                }
                if (tags.isNotEmpty()) BracketTags(tags)
                combos.forEach { combo -> ComboLine(item.card.name, combo) { runCatching { uriHandler.openUri(it) } } }
                DeckUsageLine(item.card.name, exceptDeck = item.deckId, prefix = "Also in")
                if (original != null) {
                    Text(
                        "Your printing. On Archidekt: ${original.setLabel}" + (archidektCard?.takeIf { it.foil }?.let { " · " + it.card.finishName(it.finish).lowercase() } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Which of your copies the deck shows.
                val options = copies.groupBy { it.item.card.scryfallId to it.item.finish }.values.toList()
                if (options.size > 1 || (options.size == 1 && original == null && options[0][0].item.card.scryfallId != item.card.scryfallId) || picked != null) {
                    HorizontalDivider()
                    Text("Show in this deck", style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        options.forEach { group ->
                            val first = group.first().item
                            val isShown = first.card.scryfallId == item.card.scryfallId && first.finish == item.finish
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(DeckPrintings.pickValue(first.card.scryfallId, first.finish)) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = isShown, onClick = { onPick(DeckPrintings.pickValue(first.card.scryfallId, first.finish)) })
                                CardThumb(first.card.imageUrl, width = 28)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SetLine(first.card)
                                        FinishTag(first.card, first.finish)
                                    }
                                    Text(
                                        "${group.sumOf { it.item.quantity }}× · " + group.map { binderName(it.item.binderId, binders) }.distinct().joinToString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Row {
                        if (picked != DeckPrintings.ARCHIDEKT) TextButton(onClick = { onPick(DeckPrintings.ARCHIDEKT) }) { Text("Use Archidekt's printing") }
                        if (picked != null) TextButton(onClick = { onPick(null) }) { Text("Let the app choose") }
                    }
                }
                HorizontalDivider()
                PriceTable(
                    row.price?.toSet(item.foil) ?: PriceSet(trend = item.card.fallback(item.foil)),
                    priceType,
                    if (item.foil) "Cardmarket prices (${item.card.finishName(item.finish).lowercase()})" else "Cardmarket prices",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = onRemove?.let { remove ->
            { TextButton(onClick = remove) { Text("Remove from deck", color = MaterialTheme.colorScheme.error) } }
        },
    )
}

/** The deck's bracket-relevant cards in a line, with a switch to list only them. */
@Composable
internal fun BracketSummary(tags: Collection<Set<BracketTag>>, combos: List<SaltCombo>, only: Boolean, onOnly: (Boolean) -> Unit) {
    // The two red combo tags count together as "combo pieces"; the blue ones aren't bracket-relevant.
    val counts = BracketTag.entries.filter { it != BracketTag.COMBO_3 && !it.otherCombo }.mapNotNull { t ->
        val n = if (t == BracketTag.COMBO) tags.count { BracketTag.COMBO in it || BracketTag.COMBO_3 in it } else tags.count { t in it }
        n.takeIf { it > 0 }?.let { t to it }
    }
    val counted = combos.count { it.counts }
    Column(Modifier.padding(top = 12.dp)) {
        if (combos.isNotEmpty()) {
            Text(
                "Combos: ${combos.size}" + when {
                    counted == combos.size && counted == 1 -> ", it counts for the bracket"
                    counted == combos.size -> ", all count for the bracket"
                    counted == 0 -> ", none count for the bracket"
                    else -> ", of which $counted count" + (if (counted == 1) "s" else "") + " for the bracket"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (counts.isNotEmpty()) Text(
            "Bracket-relevant: " + counts.joinToString(" · ") { (t, n) -> "$n ${t.plural(n)}" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterChip(selected = only, onClick = { onOnly(!only) }, label = { Text("Only these cards") })
    }
}

private fun BracketTag.plural(n: Int) = when (this) {
    BracketTag.GAME_CHANGER -> if (n == 1) "game changer" else "game changers"
    BracketTag.COMBO, BracketTag.COMBO_3, BracketTag.OTHER_COMBO, BracketTag.OTHER_COMBO_3 -> if (n == 1) "combo piece" else "combo pieces"
    BracketTag.EXTRA_TURN -> if (n == 1) "extra turn" else "extra turns"
    BracketTag.LAND_DENIAL -> "land denial"
    BracketTag.TUTOR -> if (n == 1) "tutor" else "tutors"
    BracketTag.FAST_MANA -> "fast mana"
}

/** A card's bracket tags: WotC's bracket rules in red, tutors and fast mana in grey. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BracketTags(tags: Set<BracketTag>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BracketTag.entries.filter { it in tags }.forEach { t ->
            val (bg, fg) = tagColors(t)
            Tag(t.label, bg, fg)
        }
    }
}

/**
 * One of the card's combos: the other cards, whether it counts for the bracket and what it does, with a
 * link to it on Commander Spellbook. Since 1.46.
 */
@Composable
internal fun ComboLine(cardName: String, combo: SaltCombo, onOpen: ((String) -> Unit)?) {
    val others = combo.cards.filter { SaltCard.key(it) != SaltCard.key(cardName) }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape)
                .background(if (combo.counts) MaterialTheme.colorScheme.error else OtherComboDot)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Combo with " + others.joinToString(" + ") + if (combo.needsBoard) " (needs more on the battlefield)" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                listOfNotNull(
                    if (combo.counts) "Counts for the bracket" else "Doesn't count for the bracket",
                    combo.resultText.takeIf { it.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val uri = combo.spellbookUri
        if (uri != null && onOpen != null) {
            Row(Modifier.clip(RoundedCornerShape(4.dp)).clickable { onOpen(uri) }.padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Spellbook", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.padding(start = 2.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
