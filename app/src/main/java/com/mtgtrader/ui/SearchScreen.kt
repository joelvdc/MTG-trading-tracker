package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.PriceEntity
import com.mtgtrader.data.PriceType
import com.mtgtrader.data.ScryCard
import com.mtgtrader.data.Side
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun targetLabel(t: CardTarget) = when (t) {
    is CardTarget.TradeSide -> if (t.side == Side.GET) "Adding to: You get" else "Adding to: You give"
    CardTarget.Collection -> "Adding to: Collection"
    else -> "Choose the printing"
}

@Composable
fun SearchScreen(nav: NavController, target: CardTarget, initialQuery: String?) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf(initialQuery ?: "") }
    var selectedName by rememberSaveable { mutableStateOf(initialQuery) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var prints by remember { mutableStateOf<List<ScryCard>?>(null) }
    var priceMap by remember { mutableStateOf(emptyMap<Int, PriceEntity>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (initialQuery == null) focus.requestFocus() }

    LaunchedEffect(query, selectedName) {
        if (selectedName != null) return@LaunchedEffect
        val q = query.trim()
        if (q.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        try {
            suggestions = c.scryfall.autocomplete(q)
            error = null
        } catch (e: Exception) {
            error = "Can't reach Scryfall — check your connection."
        }
    }

    LaunchedEffect(selectedName) {
        val name = selectedName
        if (name == null) {
            prints = null
            return@LaunchedEffect
        }
        loading = true
        prints = null
        try {
            val p = c.scryfall.prints(name)
            priceMap = c.prices.pricesFor(p.mapNotNull { it.cardmarketId })
            prints = p
            error = null
        } catch (e: Exception) {
            error = "Can't reach Scryfall — check your connection."
        } finally {
            loading = false
        }
    }

    fun add(card: ScryCard, foil: Boolean) = scope.launch {
        c.repo.add(target, card.toRef(), foil)
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar("Added ${card.name} (${card.set.uppercase()})${if (foil) " foil" else ""}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (target.isReplace) "Change printing" else "Add card")
                        Text(targetLabel(target), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!target.isReplace) {
                        IconButton(onClick = {
                            nav.popBackStack()
                            nav.openScanner(target)
                        }) { Icon(Icons.Default.CameraAlt, "Scan instead") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; selectedName = null },
                placeholder = { Text("Card name") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; selectedName = null }) { Icon(Icons.Default.Clear, "Clear") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { suggestions.firstOrNull()?.let { query = it; selectedName = it } }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).focusRequester(focus),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val p = prints
            when {
                selectedName == null -> LazyColumn {
                    items(suggestions) { name ->
                        ListItem(
                            headlineContent = { Text(name) },
                            modifier = Modifier.clickable { query = name; selectedName = name },
                        )
                        HorizontalDivider()
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                p != null && p.isEmpty() -> EmptyState("No paper printings found", "Try a different spelling.")
                p != null -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "${p.size} printing(s) · prices: Cardmarket ${priceType.label.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(p, key = { it.id }) { card ->
                        PrintRow(
                            card = card,
                            price = priceMap[card.cardmarketId],
                            priceType = priceType,
                            replaceMode = target.isReplace,
                            onPick = {
                                scope.launch {
                                    c.repo.replacePrinting(target, card.toRef())
                                    nav.popBackStack()
                                }
                            },
                            onAdd = { foil -> add(card, foil) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintRow(
    card: ScryCard,
    price: PriceEntity?,
    priceType: PriceType,
    replaceMode: Boolean,
    onPick: () -> Unit,
    onAdd: (foil: Boolean) -> Unit,
) {
    val ref = card.toRef()
    fun priceOf(foil: Boolean) = price?.toSet(foil)?.best(priceType) ?: ref.fallback(foil)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = if (replaceMode) Modifier.clickable(onClick = onPick) else Modifier,
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardThumb(card.image, width = 60, enlargeable = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(card.setName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SetLine(ref, " · ${card.rarity}${card.releasedAt?.let { " · ${it.take(4)}" } ?: ""}")
                if (card.lang != "en") Text("Language: ${card.lang.uppercase()}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ref.hasNonFoil) {
                        if (replaceMode) Text("Normal ${Fmt.money(priceOf(false))}", style = MaterialTheme.typography.bodyMedium)
                        else AssistChip(
                            onClick = { onAdd(false) },
                            label = { Text(Fmt.money(priceOf(false))) },
                            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.width(18.dp)) },
                        )
                    }
                    if (ref.hasFoil) {
                        if (replaceMode) Text("Foil ${Fmt.money(priceOf(true))}", style = MaterialTheme.typography.bodyMedium, color = FoilColor)
                        else AssistChip(
                            onClick = { onAdd(true) },
                            label = { Text("Foil ${Fmt.money(priceOf(true))}") },
                            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.width(18.dp)) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = FoilColor, leadingIconContentColor = FoilColor),
                        )
                    }
                }
            }
        }
    }
}
