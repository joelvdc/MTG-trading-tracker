package com.mtgtrader.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.mtgtrader.data.SaltSum
import com.mtgtrader.data.saltTotal
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
import com.mtgtrader.data.DeckText
import com.mtgtrader.data.PriceSet
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.QueryCard
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Style
import com.mtgtrader.data.DeckOwned
import com.mtgtrader.data.DeckOwnership
import com.mtgtrader.data.DeckPrintings
import com.mtgtrader.data.DeckToCollection
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CardQuery
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
import com.mtgtrader.data.MdfcLands
import com.mtgtrader.data.DeckLegality
import com.mtgtrader.data.LegalLine
import com.mtgtrader.data.LegalRule
import com.mtgtrader.data.Legality
import com.mtgtrader.data.DeckNotes
import com.mtgtrader.data.DeckPackage
import com.mtgtrader.data.DeckRoles
import com.mtgtrader.data.PackageCard
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.History
import java.util.UUID

@Composable
fun DeckScreen(nav: NavController, deckId: Long) {
    val context = LocalContext.current
    val c = context.container
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
    val groupBy by c.settings.deckGroup.collectAsStateWithLifecycle()
    val cardSort by c.settings.deckCardSort.collectAsStateWithLifecycle()
    val descending by c.settings.deckDescending.collectAsStateWithLifecycle()
    var layoutOptions by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var copyingText by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DeckCardRow?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRetire by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    // The packages, the change history and tagging a selection (since 1.97).
    var packagesOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var bulkTagging by remember { mutableStateOf(false) }
    var packaging by remember { mutableStateOf(false) }
    // The Commander legality of the main deck. The rules kept on the phone show at once when they cover every card;
    // otherwise (null) it says it's checking until Scryfall has answered, offline or not.
    val legalLines = remember(archidektRows) { archidektRows.map { LegalLine(it.item.card.name, it.item.quantity, it.item.commander) } }
    val legalIds = remember(archidektRows) { archidektRows.associate { it.item.card.name to it.item.card.scryfallId } }
    var legalRules by remember { mutableStateOf<Map<String, LegalRule>?>(null) }
    LaunchedEffect(legalIds) {
        if (legalIds.isEmpty()) return@LaunchedEffect
        if (c.legalRules.hasAll(legalIds.keys)) legalRules = c.legalRules.saved(legalIds.keys)
        legalRules = c.legalRules.forDeck(legalIds)
    }
    val legality = remember(legalRules, legalLines) { legalRules?.let { DeckLegality.checkLegality(legalLines, it) } }

    val matched = remember(archidektRows, owned, binders, picks, myPrintings, deck?.name) {
        if (!myPrintings) DeckPrintings.Result(archidektRows, emptyMap())
        else {
            val deckBinder = binders.firstOrNull { b -> deck?.name?.let { it.trim().equals(b.name.trim(), true) } == true }?.id
            val trade = c.settings.tradeBinderId.takeIf { id -> binders.any { it.id == id } } ?: binders.firstOrNull { it.name.equals("Trade binder", true) }?.id
            DeckPrintings.match(archidektRows, owned, deckBinder, trade, picks)
        }
    }
    val rows = matched.rows
    // "Show cards I own" (since 1.118): green for the deck's own printing, blue for another one, by Archidekt's printings.
    val showOwned by c.settings.deckShowOwned.collectAsStateWithLifecycle()
    val ownership = remember(showOwned, archidektRows, maybeRows, owned) {
        if (showOwned) DeckOwnership.of(archidektRows + maybeRows, owned) else emptyMap()
    }

    // A card tapped in the primer page (since 1.99): it opens in this page's own card window, and closing that window
    // goes back to the primer. The name waits in this page's saved state, which the primer page fills before popping.
    // The entry is taken once, at this page's first composition: while the primer is on top, nav.currentBackStackEntry
    // is the primer's own, so asking for it again would miss this page's saved state.
    val ownEntry = remember { nav.currentBackStackEntry }
    val primerCardFlow = remember(ownEntry) {
        ownEntry?.savedStateHandle?.getStateFlow<String?>("primerCard", null) ?: MutableStateFlow<String?>(null)
    }
    val primerCard by primerCardFlow.collectAsStateWithLifecycle(null)
    var backToPrimer by remember { mutableStateOf(false) }
    LaunchedEffect(primerCard, rows, maybeRows) {
        val name = primerCard ?: return@LaunchedEffect
        // Nothing until the deck's lines are in, and nothing at all when no line is that card.
        if (rows.isEmpty() && maybeRows.isEmpty()) return@LaunchedEffect
        val row = rows.firstOrNull { DeckNotes.linkMatches(name, it.item.card.name) }
            ?: maybeRows.firstOrNull { DeckNotes.linkMatches(name, it.item.card.name) }
            ?: return@LaunchedEffect
        selected = row
        backToPrimer = true
        ownEntry?.savedStateHandle?.set("primerCard", null)
    }
    LaunchedEffect(selected) {
        // The window closed (an edit in it ran first): back to the primer it was opened from.
        if (selected == null && backToPrimer) {
            backToPrimer = false
            nav.navigate("deck/$deckId/primer")
        }
    }

    // Searching inside the deck (since 1.96), with Scryfall's search words answered on the phone as far as it can
    // (CardQuery). The field sits under the top bar, so it's in the same place whatever the list's scroll position.
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    // The cards' own tags and their notes (since 1.97), for the roles line, the search and the card window.
    val cardTags by c.settings.cardTags.collectAsStateWithLifecycle()
    val cardNotes = remember(deck?.cardNotes) { DeckNotes.parseCardNotes(deck?.cardNotes) }
    // The website's automatic categories by Scryfall id, asked for once whenever the deck's cards change; none while
    // offline (DeckEditor.autoCategories remembers what it has already been told).
    val cardNames = remember(archidektRows, maybeRows) { (archidektRows + maybeRows).associate { it.item.card.scryfallId to it.item.card.name } }
    var autoCats by remember { mutableStateOf(emptyMap<String, List<String>>()) }
    LaunchedEffect(cardNames) { autoCats = runCatching { c.deckEditor.autoCategories(cardNames) }.getOrDefault(emptyMap<String, List<String>>()) }
    // What the deck's cards do for it, in the line under its header (DeckRoles.counts). Lands count from Scryfall's
    // type line (the stats cards' already-loaded details, as the Land heading's MDFC count does), the stored types
    // until it's known.
    val typeLineById = remember(statCards) { statCards.mapNotNull { c -> c.typeLine?.let { c.scryfallId to it } }.toMap() }
    val roleCounts = remember(archidektRows, cardTags, autoCats, typeLineById) {
        DeckRoles.counts(archidektRows.map { row ->
            val item = row.item
            DeckRoles.RoleCard(
                name = item.card.name,
                types = item.types,
                quantity = item.quantity,
                commander = item.commander,
                maybeboard = item.maybeboard,
                categories = item.categoryList,
                ownTags = cardTags[DeckNotes.nameKey(item.card.name)],
                auto = autoCats[item.card.scryfallId].orEmpty(),
                typeLine = typeLineById[item.card.scryfallId],
            )
        })
    }
    // What the tag dialogs offer: the role names and the user's own tags.
    val tagSuggestions = remember(cardTags) { (DeckRoles.ROLES + cardTags.values.flatten()).distinctBy { it.lowercase() } }
    // The card details the search reads (type, rules text, colours, power…): loaded once per change of the printings.
    val searchIds = remember(rows, maybeRows) { (rows + maybeRows).map { it.item.card.scryfallId }.distinct() }
    val searchWanted = searching && searchText.isNotBlank()
    val searchDetails by produceState(emptyMap<String, CardInfo>(), searchIds, searchWanted) {
        value = if (!searchWanted) emptyMap() else runCatching { c.cardDetails.forIds(searchIds) }.getOrDefault(emptyMap())
    }
    val searchQuery = remember(searchText) { searchText.trim().takeIf { it.isNotEmpty() }?.let(CardQuery::parse) }
    // Each line once, by its uid: a search over a long deck stays quick and keeps the same answer for the same line.
    val searchMatches: Map<String, Boolean> = remember(searchQuery, searchDetails, rows, maybeRows, priceType, cardTags, autoCats) {
        val q = searchQuery as? CardQuery.Query ?: return@remember emptyMap()
        (rows + maybeRows).associate { row ->
            val name = row.item.card.name
            row.item.lineUid to CardQuery.matches(
                q,
                queryCard(row, searchDetails[row.item.card.scryfallId], priceType, cardTags[DeckNotes.nameKey(name)], autoCats[row.item.card.scryfallId].orEmpty()),
            )
        }
    }
    // A filter that didn't parse changes nothing (its message shows under the field instead).
    val searchError = (searchQuery as? CardQuery.Error)?.error
    val filtering = searchQuery is CardQuery.Query && searchMatches.isNotEmpty()
    // While a search that parsed is on, the list shows only the cards (the sections), nothing above them.
    val searchActive = searching && filtering
    val shownRows = remember(rows, searchMatches, filtering) { if (!filtering) rows else rows.filter { searchMatches[it.item.lineUid] == true } }
    val shownMaybeRows = remember(maybeRows, searchMatches, filtering) { if (!filtering) maybeRows else maybeRows.filter { searchMatches[it.item.lineUid] == true } }
    // "N of M cards" (by quantity) for the deck while a filter that parsed is on, and the words only Scryfall can answer.
    val searchCounts = if (searchQuery is CardQuery.Query && searchText.isNotBlank()) {
        shownRows.sumOf { it.item.quantity } to rows.sumOf { it.item.quantity }
    } else null
    val searchUnsupported = (searchQuery as? CardQuery.Query)?.let { CardQuery.unsupportedKeys(it) }.orEmpty()
    fun closeSearch() {
        searching = false
        searchText = ""
    }

    var addingToCollection by remember { mutableStateOf(false) }
    var onlyBracket by rememberSaveable { mutableStateOf(false) }
    // Bracket-relevant cards come from the deck's rating (the website's estimate since 1.116).
    val saltCard = remember(deck?.saltCard) { SaltCard.decode(deck?.saltCard) }
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
    // The deck's own category order (set on the website, since 1.86); the rest alphabetically.
    val categoryOrder = remember(deck?.categoryOrder) { DeckGrouping.categoryOrder(deck?.categoryOrder) }
    val allSections = remember(shownRows, groupBy, cardSort, descending, onlyBracket, tagged, costs, categoryOrder) {
        DeckGrouping.sections(if (onlyBracket) shownRows.filter { it.item.id in tagged } else shownRows, groupBy, cardSort, descending, categoryOrder) {
            DeckGrouping.costColours(costs[it.item.card.name])
        }
    }
    val sections = remember(allSections, hidden, groupBy) {
        if (groupBy != DeckGroupBy.CATEGORY) allSections else allSections.filter { it.title.lowercase() !in hidden }
    }
    val hiddenRows = remember(allSections, sections) {
        (allSections - sections.toSet()).flatMap { s -> s.rows }.sortedWith(compareBy({ it.item.category.lowercase() }, { it.item.card.name }))
    }
    // The maybeboard (and any other category left out of the deck on Archidekt), by its categories.
    val maybeSections = remember(shownMaybeRows, cardSort, descending, categoryOrder) { DeckGrouping.sections(shownMaybeRows, DeckGroupBy.CATEGORY, cardSort, descending, categoryOrder) }
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
    // Editing (since 1.46): local decks only since 1.78; decks from Archidekt are edited on archidekt.com.
    val ownDeck = deck?.local == true
    val editable = ownDeck
    // Changes from before 1.78 that never reached Archidekt: a reload would overwrite them, so it asks first.
    val waitingMap by c.deckEditor.waiting.collectAsStateWithLifecycle(emptyMap())
    val waiting = waitingMap[deckId] ?: 0
    var confirmReload by remember { mutableStateOf(false) }
    fun reloadNow() {
        if (!c.decks.refresh(deckId)) scope.launch { snackbar.showSnackbar("Wait for the current import to finish") }
    }
    fun refresh() {
        if (waiting > 0) confirmReload = true else reloadNow()
    }
    var selection by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var movingTo by remember { mutableStateOf<List<String>?>(null) }
    var categoryFor by remember { mutableStateOf<Pair<DeckCardRow, String?>?>(null) }
    var namingFor by remember { mutableStateOf<List<String>?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deletingCategory by remember { mutableStateOf<String?>(null) }
    var renameDeck by remember { mutableStateOf(false) }
    var confirmDetach by remember { mutableStateOf(false) }
    var confirmAuto by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    val listState = rememberLazyListState()
    val categoryInfo = remember(deck?.categoryInfo) { DeckEdits.categories(deck?.categoryInfo) }
    LaunchedEffect(deckId, ownDeck) {
        if (ownDeck) c.deckEditor.prepare(deckId)?.let { snackbar.showSnackbar(it) }
    }
    // Every deck's bracket and salt can be the website's estimate (Commander Salt can't score local decks, and since
    // 1.115 its brackets can come from the app for any deck): asked for when the deck's list changes (its lines from
    // the database, so another phone's sync counts too).
    LaunchedEffect(deckId, archidektRows.size, archidektRows.sumOf { it.item.quantity }) {
        runCatching { c.decks.rateLocal(deckId) }
    }
    BackHandler(enabled = searching) { closeSearch() }
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
                    onTag = { bulkTagging = true },
                    onPackage = { packaging = true },
                )
            } else TopAppBar(
                title = { Text(deck?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { if (searching) closeSearch() else searching = true }) { Icon(Icons.Default.Search, "Search this deck") }
                    IconButton(onClick = { c.settings.setDeckCompact(!compact) }) {
                        if (compact) Icon(Icons.Default.ViewList, "Show cards with pictures") else Icon(Icons.Default.ViewHeadline, "Compact view")
                    }
                    if (deck?.local != true) IconButton(onClick = ::refresh, enabled = job == null) { Icon(Icons.Default.Refresh, "Reload decklist from Archidekt") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    // The menu under headings (since 1.96). Each heading keeps at least one item on every deck, so none
                    // is ever left with nothing under it.
                    ScreenMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val local = deck?.local == true
                        val anyOpen = shownKeys.any { it !in collapsed }
                        MenuHeading("Deck")
                        if (!local) DropdownMenuItem(
                            text = { Text("Reload decklist from Archidekt") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = job == null,
                            onClick = { menu = false; refresh() },
                        )
                        // The primer and the packages hold on every deck, Archidekt's included (since 1.97).
                        DropdownMenuItem(
                            text = { Text("Primer…") },
                            leadingIcon = { Icon(Icons.Default.Article, null) },
                            onClick = { menu = false; nav.navigate("deck/$deckId/primer") },
                        )
                        DropdownMenuItem(
                            text = { Text("Packages…") },
                            leadingIcon = { Icon(Icons.Default.Inventory2, null) },
                            onClick = { menu = false; packagesOpen = true },
                        )
                        // Since 1.104: where this deck's cards are stored, binder by binder.
                        DropdownMenuItem(
                            text = { Text("Find these cards…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            onClick = { menu = false; nav.navigate("findcards?deck=$deckId") },
                        )
                        // Editing (since 1.46).
                        if (local) {
                            DropdownMenuItem(
                                text = { Text("Rename deck") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menu = false; renameDeck = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Paste cards…") },
                                leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                                onClick = { menu = false; pasting = true },
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
                            text = { Text("Make it a local deck (to edit here)") },
                            leadingIcon = { Icon(Icons.Default.LinkOff, null) },
                            onClick = { menu = false; confirmDetach = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to collection…") },
                            leadingIcon = { Icon(Icons.Default.LibraryAdd, null) },
                            onClick = { menu = false; addingToCollection = true },
                        )
                        MenuHeading("View")
                        DropdownMenuItem(
                            text = { Text("Search this deck") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            onClick = { menu = false; searching = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Group and sort…") },
                            leadingIcon = { Icon(Icons.Default.ViewList, null) },
                            onClick = { menu = false; layoutOptions = true },
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
                        DropdownMenuItem(
                            text = { Text("Show cards I own") },
                            leadingIcon = { Icon(Icons.Default.LibraryAddCheck, null) },
                            trailingIcon = { if (showOwned) Icon(Icons.Default.Check, "On") },
                            onClick = { menu = false; c.settings.setDeckShowOwned(!showOwned) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (anyOpen) "Collapse all sections" else "Expand all sections") },
                            leadingIcon = { Icon(if (anyOpen) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore, null) },
                            onClick = {
                                menu = false
                                c.settings.setDeckCollapsed(if (anyOpen) collapsed + shownKeys else collapsed - shownKeys.toSet())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (myPrintings) "Show Archidekt's printings" else "Show the printings I own") },
                            leadingIcon = { Icon(Icons.Default.Style, null) },
                            onClick = { menu = false; c.settings.setDeckShowsMyPrintings(deckId, !myPrintings) },
                        )
                        MenuHeading("Share")
                        DropdownMenuItem(
                            text = { Text("Copy deck as text…") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            enabled = deck != null,
                            onClick = { menu = false; copyingText = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Share a link to this deck") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            enabled = !sharing && deck != null,
                            onClick = {
                                menu = false
                                sharing = true
                                scope.launch {
                                    shareLink(context, c, "deck", deckId.toString(), "MTG Trader: ${deck?.name.orEmpty()}")?.let { snackbar.showSnackbar(it) }
                                    sharing = false
                                }
                            },
                        )
                        MenuHeading("More")
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
                            text = { Text("Change history") },
                            leadingIcon = { Icon(Icons.Default.History, null) },
                            onClick = { menu = false; historyOpen = true },
                        )
                        deck?.takeIf { !it.local }?.let { d ->
                            DropdownMenuItem(
                                text = { Text("Open on Archidekt") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = { menu = false; runCatching { uriHandler.openUri(d.archidektUrl) } },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (deck?.retired == true) "Bring back" else "Retire deck…") },
                            leadingIcon = { Icon(if (deck?.retired == true) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                            onClick = {
                                menu = false
                                if (deck?.retired == true) scope.launch { c.db.deckDao().setRetired(deckId, false) } else confirmRetire = true
                            },
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
            // Only local decks take new cards: decks from Archidekt are read only (since 1.85).
            if (deck?.local == true) {
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
        // Once the typing settles, the matching cards come into view (the list holds only cards while searching).
        LaunchedEffect(searchText) {
            if (!searchActive) return@LaunchedEffect
            delay(300)
            listState.scrollToItem(0)
        }
        // The search field sits above the list, so it's in the same place whatever the list's scroll position.
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (searching) {
                DeckSearchBar(
                    text = searchText,
                    onChange = { searchText = it },
                    error = searchError,
                    counts = searchCounts,
                    unsupported = searchUnsupported,
                    onClose = ::closeSearch,
                )
            }
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp)) {
                if (!searchActive) {
                    job?.takeIf { it.deckId == deckId }?.let { j -> item(key = "job") { Box(Modifier.padding(bottom = 8.dp)) { JobCard(j) } } }
                    if (waiting > 0) {
                        item(key = "waiting") { UnsentBanner(waiting, onKeep = { confirmDetach = true }, onDiscard = { confirmReload = true }) }
                    }
                    if (d.retired) item(key = "retired") {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Retired",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { scope.launch { c.db.deckDao().setRetired(deckId, false) } }) { Text("Bring back") }
                        }
                    }
                    item(key = "header") { DeckHeader(d, rows.sumOf { it.item.quantity }, value, priceType) }
                    // What the deck's cards do for it (since 1.97), then its primer: both under the header, on every deck.
                    if (archidektRows.isNotEmpty()) {
                        item(key = "roles") {
                            RoleCountsLine(
                                roleCounts,
                                onRole = { role ->
                                    val tag = role.lowercase()
                                    searchText = if (" " in tag) "tag:\"$tag\"" else "tag:$tag"
                                    searching = true
                                },
                            )
                        }
                    }
                    item(key = "primer") { PrimerRow(d.primer, onOpen = { nav.navigate("deck/$deckId/primer") }) }
                    item(key = "rulezero") {
                        Button(onClick = { showing = true }, enabled = d.rated, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Icon(Icons.Default.Shield, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bracket card")
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
                        val sum = rememberSaltSum(d.archidektId, archidektRows.size to archidektRows.sumOf { it.item.quantity })
                        ScoresCard(d, sum, busy = job != null) {
                            if (!c.decks.rescore(deckId)) scope.launch { snackbar.showSnackbar("Wait for the current import to finish") }
                        }
                    }
                    if (archidektRows.isNotEmpty()) item(key = "legality") { LegalityCard(legality) }
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
                    // The deck's games from the life tracker (since 1.47), folded until opened; above the tokens since 1.58.
                    if (deckGames.isNotEmpty()) {
                        val gamesFolded = GAMES_OPEN !in statsOpen
                        item(key = "games_h") { StatsHeading("Games (${deckGames.size})", gamesFolded) { toggleStats(GAMES_OPEN) } }
                        if (!gamesFolded) item(key = "games_card") {
                            val r = remember(deckGames) { com.mtgtrader.data.GameStats.decks(deckGames).firstOrNull { it.deckId == deckId } }
                            r?.let { DeckGamesCard(it, deckGames, deckId) { g -> g.uid?.let { uid -> nav.navigate("games/game/$uid") } } }
                        }
                    }
                    if (tokens.isNotEmpty()) {
                        val tokensFolded = DeckStats.TOKENS !in statsOpen
                        item(key = "tokens_h") { StatsHeading("Tokens (${tokens.size})", tokensFolded) { toggleStats(DeckStats.TOKENS) } }
                        if (!tokensFolded) {
                            tokens.chunked(3).forEachIndexed { i, row -> item(key = "tokens_$i") { TokenRow(row) { openToken = it.key } } }
                        }
                    }
                }
                sections.forEach { section ->
                    val folded = section.title in collapsed
                    item(key = "h_${section.title}") {
                        // The Land heading also counts the MDFC lands sitting in the deck's other sections (since 1.100).
                        val mdfc = if (MdfcLands.isLandSection(section.title)) MdfcLands.copies(statCards, section.rows.map { it.item.card.scryfallId }.toSet()) else 0
                        SectionHeading(section, sectionValue(section), folded, mdfc = mdfc, highlight = dropCategory == section.title, menu = categoryMenu(section.title, section.rows)) { toggle(section.title) }
                    }
                    // Cards "Added in app" on an editable deck: into the deck, or to Archidekt's maybeboard (since 1.46).
                    if (editable && section.title == DeckToCollection.ADDED_IN_APP && !folded) {
                        item(key = "added_actions") {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = {
                                    edit("Put ${section.count} card(s) in the deck") { c.deckEditor.move(deckId, section.rows.map { it.item.lineUid }, DeckEditor.AUTO) }
                                }, modifier = Modifier.weight(1f)) { Text("Into the deck") }
                                OutlinedButton(onClick = {
                                    edit("Put ${section.count} card(s) in the maybeboard") { c.deckEditor.addedToMaybeboard(deckId) }
                                }, modifier = Modifier.weight(1f)) { Text("To maybeboard") }
                            }
                        }
                    }
                    if (!folded) {
                        items(section.rows, key = { "c_${it.item.id}" }) { row ->
                            val cost = costs[row.item.card.name]
                            val key = "c_${row.item.id}"
                            val editing = rowEditing(row, key)
                            dragged(key) {
                                if (compact) DeckCompactRow(row, priceType, tagged[row.item.id].orEmpty(), cost, editing, note = DeckNotes.nameKey(row.item.card.name) in cardNotes, owned = ownership[row.item.id]) { onRow(row) }
                                else DeckCardRowView(row, priceType, tagged[row.item.id].orEmpty(), cost, editing, note = DeckNotes.nameKey(row.item.card.name) in cardNotes, owned = ownership[row.item.id]) { onRow(row) }
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
                                if (compact) DeckCompactRow(row, priceType, tagged[row.item.id].orEmpty(), cost, editing, note = DeckNotes.nameKey(row.item.card.name) in cardNotes, owned = ownership[row.item.id]) { onRow(row) }
                                else DeckCardRowView(row, priceType, tagged[row.item.id].orEmpty(), cost, editing, note = DeckNotes.nameKey(row.item.card.name) in cardNotes, owned = ownership[row.item.id]) { onRow(row) }
                            }
                        }
                    }
                }
                if (showMaybeboard) {
                    item(key = "maybeboard") {
                        Column(Modifier.padding(top = 20.dp)) {
                            // Only a divider since 1.95: the maybeboard's own folding sections follow it.
                            HorizontalDivider()
                            if (shownMaybeRows.isEmpty()) Text("No maybeboard cards.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
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
                                    if (compact) DeckCompactRow(row, priceType, emptySet(), cost, editing, note = DeckNotes.nameKey(row.item.card.name) in cardNotes, owned = ownership[row.item.id]) { onRow(row) }
                                    else DeckCardRowView(row, priceType, emptySet(), cost, editing, note = DeckNotes.nameKey(row.item.card.name) in cardNotes, owned = ownership[row.item.id]) { onRow(row) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showing) deck?.let { d -> RuleZeroDialog(d) { showing = false } }
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
                    val auto by produceState<String?>(null, row.item.card.scryfallId) { value = c.deckEditor.autoCategories(mapOf(row.item.card.scryfallId to row.item.card.name))[row.item.card.scryfallId]?.firstOrNull() }
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
            note = cardNotes[DeckNotes.nameKey(row.item.card.name)],
            ownTags = cardTags[DeckNotes.nameKey(row.item.card.name)].orEmpty(),
            autoTags = DeckRoles.tagsOf(row.item.card.name, row.item.categoryList, null, autoCats[row.item.card.scryfallId].orEmpty()),
            tagSuggestions = tagSuggestions,
            onNote = { text ->
                edit(if (text.isBlank()) "Note removed" else "Note saved") { c.deckEditor.setCardNote(deckId, row.item.card.name, text) }
            },
            onSetTags = { tags -> c.settings.setCardTags(row.item.card.name, tags) },
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
    if (layoutOptions) {
        DeckLayoutDialog(
            groupBy, cardSort, descending,
            onApply = { g, s, d -> layoutOptions = false; c.settings.setDeckLayout(g, s, d) },
            onDismiss = { layoutOptions = false },
        )
    }
    // The deck as text, to copy or share (since 1.96).
    if (copyingText) {
        val exportLines = remember(archidektRows, maybeRows) {
            (archidektRows + maybeRows).map { row ->
                val item = row.item
                DeckText.ExportLine(
                    quantity = item.quantity,
                    name = item.card.name,
                    setCode = item.card.setCode,
                    number = item.card.collectorNumber,
                    foil = item.foil,
                    etched = item.etched,
                    categories = item.categories.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(item.category) },
                    commander = item.commander,
                    maybeboard = item.maybeboard,
                )
            }
        }
        CopyDeckTextDialog(exportLines, deck?.name.orEmpty(), onDismiss = { copyingText = false }) {
            scope.launch { snackbar.showSnackbar("Deck copied as text") }
        }
    }
    // Cards pasted as text into a local deck (since 1.96).
    if (pasting) {
        PasteCardsDialog(deckId, onDismiss = { pasting = false }) { message, undo ->
            pasting = false
            scope.launch { if (snackbar.showUndo(message)) undo() }
        }
    }
    if (confirmRetire) {
        AlertDialog(
            onDismissRequest = { confirmRetire = false },
            title = { Text("Retire ${deck?.name ?: "deck"}?") },
            text = {
                Text(
                    "The deck moves to Retired on the Decks tab, with its page, games and stats. It leaves the seat pickers, " +
                        "\"Update all decks\" and the collection checks (\"in a deck\" marks, the trade binder's \"keep for my decks\"). " +
                        "Bring it back from ⋮ at any time.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRetire = false; scope.launch { c.db.deckDao().setRetired(deckId, true) } }) { Text("Retire") }
            },
            dismissButton = { TextButton(onClick = { confirmRetire = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${deck?.name ?: "deck"}?") },
            text = {
                Text(
                    // A deck with games: retiring keeps its page and stats (since 1.83).
                    (if (deckGames.isNotEmpty() && deck?.retired != true) "It played ${deckGames.size} recorded game(s). The games stay either way, " +
                        "but retiring the deck keeps its page and stats too.\n\n" else "") +
                    when {
                        deck?.local == true -> "This deck only exists in this app (and on your other phone after a sync), so it's gone for good. You can undo right after."
                        waiting > 0 -> "The deck is only removed from this app; it stays on Archidekt. Its $waiting change(s) that never reached Archidekt are lost."
                        else -> "The deck is only removed from this app; it stays on Archidekt."
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
            dismissButton = {
                Row {
                    if (deckGames.isNotEmpty() && deck?.retired != true) {
                        TextButton(onClick = { confirmDelete = false; scope.launch { c.db.deckDao().setRetired(deckId, true) } }) { Text("Retire instead") }
                    }
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                }
            },
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
        DeleteCategoryDialog(name, count, categoryCounts.map { it.first }.filterNot { it.equals(name, true) }, onDismiss = { deletingCategory = null }) { to ->
            deletingCategory = null
            edit("Deleted the category $name") { c.deckEditor.deleteCategory(deckId, name, to) }
        }
    }
    if (confirmAuto) {
        AlertDialog(
            onDismissRequest = { confirmAuto = false },
            title = { Text("Auto-categorise all cards?") },
            text = { Text("Every card (not the commander or the maybeboard) goes into its auto category from the website; cards without one go by card type. You can undo right after.") },
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
                        if (waiting > 0) " Its $waiting change(s) that never reached Archidekt are kept in the local deck." else ""
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
    if (confirmReload) {
        AlertDialog(
            onDismissRequest = { confirmReload = false },
            title = { Text("Discard $waiting change(s)?") },
            text = {
                Text(
                    "They were made on this phone before version 1.78 and never reached Archidekt. Reloading the deck from " +
                        "Archidekt replaces them with Archidekt's list. To keep them instead, make it a local deck."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReload = false
                    scope.launch {
                        c.deckEditor.discardWaiting(deckId)
                        reloadNow()
                    }
                }) { Text("Discard and reload", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReload = false }) { Text("Cancel") } },
        )
    }

    // ---- The packages, the change history and tagging a selection (since 1.97) ----
    if (packagesOpen) {
        DeckPackagesSheet(
            local = ownDeck,
            onDismiss = { packagesOpen = false },
            onAdd = { pkg ->
                packagesOpen = false
                scope.launch {
                    val (undo, result) = c.deckEditor.addPackage(deckId, pkg)
                    val message = when {
                        result.added == 0 && result.notFound.isEmpty() -> "Nothing to add: the deck has every card"
                        else -> "${result.added} card(s) added, ${result.skipped} already in the deck" +
                            if (result.notFound.isNotEmpty()) ", ${result.notFound.size} not found" else ""
                    }
                    if (snackbar.showUndo(message)) undo()
                }
            },
            onRemove = { pkg ->
                packagesOpen = false
                scope.launch {
                    val (undo, n) = c.deckEditor.removePackage(deckId, pkg)
                    if (n == 0) snackbar.showSnackbar("None of its cards are in the deck")
                    else if (snackbar.showUndo("$n card(s) removed")) undo()
                }
            },
            onMessage = { message ->
                packagesOpen = false
                scope.launch { snackbar.showSnackbar(message) }
            },
        )
    }
    if (historyOpen) ChangeHistorySheet(deck?.history, onDismiss = { historyOpen = false })
    if (bulkTagging) {
        val chosen = (archidektRows + maybeRows).filter { it.item.lineUid in selection }
        val names = chosen.map { it.item.card.name }.distinct()
        BulkTagDialog(names.size, tagSuggestions, onDismiss = { bulkTagging = false }) { add, remove ->
            bulkTagging = false
            c.settings.bulkTag(names, add, remove)
            scope.launch {
                snackbar.showSnackbar(
                    if (add != null) "Tagged ${names.size} card(s) with $add"
                    else "Removed the tag ${remove.orEmpty()} from ${names.size} card(s)",
                )
            }
        }
    }
    if (packaging) {
        val cards = (archidektRows + maybeRows).filter { it.item.lineUid in selection }
            .map { PackageCard(it.item.card.name, it.item.card.scryfallId, it.item.quantity) }
        SaveAsPackageDialog(cards, onDismiss = { packaging = false }) { name ->
            packaging = false
            selection = emptyList()
            c.settings.savePackage(DeckPackage(UUID.randomUUID().toString(), name, cards))
            scope.launch { snackbar.showSnackbar("Package saved") }
        }
    }
}

/**
 * The deck's search (since 1.96): Scryfall's search words, answered on the phone as far as [CardQuery] can, with its
 * error or how many of the deck's cards match. It sits under the top bar, above the list.
 */
@Composable
private fun DeckSearchBar(
    text: String,
    onChange: (String) -> Unit,
    error: String?,
    /** The cards shown and the deck's cards (by quantity), while a filter that parsed is on. */
    counts: Pair<Int, Int>?,
    /** The words only Scryfall can answer (otag:, art:…), which the search leaves out. */
    unsupported: List<String>,
    onClose: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    // Focus and keyboard straight away when the field opens (since 1.96), so the search needs no second tap.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            placeholder = { Text("Search (eg: t:creature o:draw mv<=2)") },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            trailingIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close search") } },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp, top = 2.dp))
        } else if (counts != null) {
            Text(
                "${counts.first} of ${counts.second} cards",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
        if (unsupported.isNotEmpty()) {
            Text(
                "Not searched in the app: " + unsupported.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

/**
 * A deck line as the deck search sees it ([QueryCard]), from the line and its details from Scryfall when they're
 * known. Its tags are its categories plus the roles it counts as (since 1.97), so `tag:ramp` finds it either way.
 */
private fun queryCard(row: DeckCardRow, info: CardInfo?, priceType: PriceType, ownTags: List<String>?, auto: List<String>): QueryCard {
    val item = row.item
    val pt = info?.powerToughness?.split("/")
    val categories = item.categoryList
    return QueryCard(
        name = item.card.name,
        typeLine = info?.typeLine?.takeIf { it.isNotBlank() } ?: item.types,
        oracleText = info?.oracleText.orEmpty(),
        manaCost = info?.manaCost.orEmpty(),
        mv = item.cmc,
        colors = info?.colors.orEmpty().map { it.toString() },
        colorIdentity = info?.colorIdentity.orEmpty().map { it.toString() },
        power = pt?.getOrNull(0)?.takeIf { it.isNotBlank() },
        toughness = pt?.getOrNull(1)?.takeIf { it.isNotBlank() },
        loyalty = info?.loyalty?.takeIf { it.isNotBlank() },
        rarity = item.card.rarity.takeIf { it.isNotBlank() },
        setCode = item.card.setCode,
        commander = item.commander,
        gameChanger = item.gameChanger,
        priceEur = row.unitPrice(priceType),
        category = item.category,
        tags = (categories + DeckRoles.tagsOf(item.card.name, categories, ownTags, auto)).distinct(),
    )
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
        // When it was last changed (since 1.95): Archidekt's date, or a local deck's last change; as on the website.
        deck.modifiedAt?.let {
            Text("Modified ${Fmt.date(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The deck's Commander legality: "Commander legal ✓", or each problem on its own line; null while it's being checked. */
@Composable
private fun LegalityCard(legality: Legality?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                legality == null -> Text("Checking legality…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                legality.problems.isEmpty() -> Text("Commander legal ✓", style = MaterialTheme.typography.titleSmall)
                else -> {
                    Text("Not Commander legal", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    legality.problems.forEach { Text(it.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (legality != null && legality.unchecked.isNotEmpty()) {
                val n = legality.unchecked.size
                Text("Couldn't check $n card" + (if (n == 1) "" else "s") + ".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The deck's scores (since 1.116): its estimated bracket, its salt sum and the turn it wins by, then when it was rated. */
@Composable
private fun ScoresCard(deck: Deck, saltSum: SaltSum?, busy: Boolean, onRescore: () -> Unit) {
    val win by rememberWinOdds(deck)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                ScoreColumn("Bracket", deck.bracket?.toString(), Brackets.name(deck.bracket), Modifier.weight(1f))
                ScoreColumn("Salt", saltSum?.let { saltTotal(it.total) }, saltSum?.level, Modifier.weight(1f))
                ScoreColumn(
                    "Wins by",
                    win?.label ?: if (deck.commanders.isBlank()) null else "…",
                    win?.firstWin?.let { "from turn $it" },
                    Modifier.weight(1f),
                )
            }
            // When it was rated, with the refresh button on the same line.
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (deck.rated) deck.scoredAt?.let { "Rated ${Fmt.date(it)}" } ?: "" else "Not rated yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!busy) {
                    TextButton(onClick = onRescore) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
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

/** The green and blue of "Show cards I own" (since 1.118), the website's collection tracker colours. */
private val OWNED_GREEN = Color(0xFF2E9E5B)
private val OWNED_BLUE = Color(0xFF6B8FD4)

private val DeckOwned?.tinted get() = this == DeckOwned.EXACT || this == DeckOwned.OTHER

/** A soft green or blue background. */
private fun Modifier.ownedTint(owned: DeckOwned?): Modifier = when (owned) {
    DeckOwned.EXACT -> background(OWNED_GREEN.copy(alpha = 0.22f))
    DeckOwned.OTHER -> background(OWNED_BLUE.copy(alpha = 0.22f))
    else -> this
}

/** A little room inside every row while "Show cards I own" is on, so text doesn't touch a tint's edges and stays lined up. */
private fun Modifier.ownedRoom(owned: DeckOwned?): Modifier = if (owned != null) padding(horizontal = 6.dp) else this

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
internal fun DeckCardRowView(
    row: DeckCardRow,
    priceType: PriceType,
    tags: Set<BracketTag>,
    cost: String?,
    editing: RowEditing? = null,
    /** The card has a note in its window (since 1.97). */
    note: Boolean = false,
    /** What the collection holds of it, when "Show cards I own" is on (since 1.118). */
    owned: DeckOwned? = null,
    onClick: () -> Unit,
) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).ownedTint(owned).rowClicks(editing, onClick).ownedRoom(owned).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectMark(editing)
        CardThumb(item.card.imageUrl, width = 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (item.quantity > 1) "${item.quantity}× " else "") + item.card.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (note) NoteIcon(Modifier.padding(start = 4.dp))
            }
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
 * section away or opens it again. Since 1.42. The Land section adds the copies of the modal double-faced
 * lands outside it ("Land (37, 40 with MDFCs)", since 1.100) when [mdfc] is above 0.
 */
@Composable
internal fun SectionHeading(
    section: DeckSection,
    value: Double?,
    folded: Boolean,
    count: Int? = null,
    mdfc: Int = 0,
    highlight: Boolean = false,
    menu: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit,
) {
    val n = count ?: section.count
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(4.dp))
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onToggle)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (mdfc > 0) "${section.title} ($n, ${n + mdfc} with MDFCs)" else "${section.title} ($n)",
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

/** The card's name followed by its marks: ✦ foil, + added in the app, a note mark (since 1.97), then its bracket tags. */
@Composable
private fun NameWithMarks(name: String, tags: Set<BracketTag>, foil: Boolean, added: Boolean, modifier: Modifier = Modifier, note: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val nameStyle = MaterialTheme.typography.bodyMedium
    val markStyle = MaterialTheme.typography.labelMedium
    val ordered = BracketTag.entries.filter { it in tags }
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth
        val level = remember(name, ordered, foil, added, note, available, nameStyle, density) {
            fun w(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            val chip = with(density) { 10.dp.roundToPx() }
            DeckMarks.level(
                available,
                name = w(name, nameStyle),
                extras = (if (foil) w(" ✦", markStyle) else 0) + (if (added) w(" +", markStyle) else 0) + (if (note) with(density) { 18.dp.roundToPx() } else 0),
                full = ordered.sumOf { w(DeckMarks.full(it), MarkLabelStyle) + chip },
                short = ordered.sumOf { w(DeckMarks.short(it), MarkLabelStyle) + chip },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (note) NoteIcon(Modifier.padding(start = 4.dp))
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
 * number are in the card's window. Since 1.42; on one line since 1.43. [note]: the card has a note (since 1.97).
 */
@Composable
internal fun DeckCompactRow(
    row: DeckCardRow,
    priceType: PriceType,
    tags: Set<BracketTag>,
    cost: String?,
    editing: RowEditing? = null,
    note: Boolean = false,
    owned: DeckOwned? = null,
    onClick: () -> Unit,
) {
    val item = row.item
    val unit = row.unitPrice(priceType)
    Column(Modifier.fillMaxWidth().then(if (owned.tinted) Modifier.clip(RoundedCornerShape(6.dp)) else Modifier).ownedTint(owned).rowClicks(editing, onClick).ownedRoom(owned)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SelectMark(editing)
            Text(
                "${item.quantity}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
            NameWithMarks(item.card.displayName, tags, item.foil, item.addedInApp, Modifier.weight(1f), note)
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
    // A deck made from a precon gets all its cards, not just the ones you don't own yet (since 1.107).
    var onlyMissing by remember { mutableStateOf(!deck.fromPrecon) }
    var skipBasics by remember { mutableStateOf(true) }
    var choice by remember { mutableStateOf(BinderChoice(newName = deck.name)) }
    // Adding the same deck again goes into its binder from last time, else into the sort's Decks binder (since 1.107).
    LaunchedEffect(Unit) {
        val binders = c.db.binderDao()
        val same = binders.byName(deck.name)?.id
        val decks = c.settings.sortDecksId.takeIf { it > 0 }?.let { binders.get(it) }
        choice = when {
            same != null -> BinderChoice(same)
            decks != null -> BinderChoice(decks.id)
            else -> choice
        }
    }
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
    /** The deck's note on this card, its own tags and the roles its categories give it (since 1.97). */
    note: String? = null,
    ownTags: List<String> = emptyList(),
    autoTags: List<String> = emptyList(),
    tagSuggestions: List<String> = emptyList(),
    onNote: ((String) -> Unit)? = null,
    onSetTags: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val c = LocalContext.current.container
    val uriHandler = LocalUriHandler.current
    val item = row.item
    val owned by remember { c.db.collectionDao().observeOwned() }.collectAsStateWithLifecycle(emptyList())
    val ownedHere = owned.firstOrNull { it.scryfallId == item.card.scryfallId }?.qty ?: 0
    val rules = rememberCardRules(item.card.scryfallId, item.card.name)
    var tab by remember { mutableStateOf(CardTab.VERSIONS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.card.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                CardTextBlock(rules.faces)
                // The card's note and its tags (since 1.97): both work on every deck, Archidekt's included, and the
                // tags follow the card into every deck.
                onNote?.let { CardNoteSection(note = note, onSave = it) }
                onSetTags?.let { CardTagsSection(ownTags = ownTags, autoTags = autoTags, suggestions = tagSuggestions, onSetTags = it) }
                CardTabs(tab) { tab = it }
                if (tab == CardTab.RULINGS) RulingsTab(rules)
                if (tab == CardTab.VERSIONS) {
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
