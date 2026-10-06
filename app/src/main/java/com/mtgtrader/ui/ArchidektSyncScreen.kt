package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.BuildConfig
import com.mtgtrader.container
import com.mtgtrader.data.ArchidektCodes
import com.mtgtrader.data.ArchidektPlanner
import com.mtgtrader.data.ArchidektSync
import com.mtgtrader.data.CONDITIONS
import com.mtgtrader.data.CardKey
import com.mtgtrader.data.Choice
import com.mtgtrader.data.Decision
import com.mtgtrader.data.KeyChange
import com.mtgtrader.data.Plan
import com.mtgtrader.data.PullTo
import com.mtgtrader.data.ReportLine
import com.mtgtrader.data.SyncReport
import com.mtgtrader.data.TagMode
import kotlinx.coroutines.launch

/** Settings → Archidekt collection: two-way sync of the collection with Archidekt. Since 1.21. */
@Composable
fun ArchidektSyncScreen(nav: NavController) {
    val c = LocalContext.current.container
    val s by c.archidekt.status.collectAsStateWithLifecycle()
    val nextcloud by c.sync.status.collectAsStateWithLifecycle()
    var login by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<SyncReport>?>(null) }
    var cardByCard by remember { mutableStateOf<Plan?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf<Plan?>(null) }
    var confirmFirst by remember { mutableStateOf<Choice?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Archidekt collection") },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Keeps your Archidekt collection the same as the app's: cards added, removed or changed on either side go to the other " +
                    "when you sync. Archidekt holds your whole collection (it has no binders); cards that come from Archidekt go to the binder chosen below.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "This uses the same connection as Archidekt's website, which isn't an official interface: if Archidekt changes it, syncing may stop working until the app is updated.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!s.connected) {
                Button(onClick = { login = true }) { Text("Log in to Archidekt") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Logged in as ${s.username}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { c.archidekt.logOut() }, enabled = !s.running) { Text("Log out") }
                }
                Text(
                    if (nextcloud.connected && nextcloud.ready) "Shared with your other phones through Nextcloud: each sync runs a Nextcloud sync before and after, and only one phone syncs with Archidekt at a time."
                    else "This phone only. To sync with Archidekt from several phones, connect them all to Nextcloud sync first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (s.lastSyncAt == 0L) "Not synced yet." else "Last synced: ${Fmt.dateTime(s.lastSyncAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val unsynced by androidx.compose.runtime.produceState<Int?>(null, s.lastSyncAt, s.running) { value = c.archidekt.unsyncedCopies() }
                unsynced?.takeIf { it > 0 && !s.running }?.let { n ->
                    Text(
                        "$n card(s) changed since this phone's last sync with Archidekt." +
                            if (s.auto) " They go to Archidekt when you leave the app, or now with Sync now." else " Tap Sync now to send them (automatic sync is off).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (s.running) {
                    Text(s.progress ?: "Syncing…", color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                s.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { c.archidekt.syncNow() }, enabled = !s.running) { Text(if (s.ready) "Sync now" else "First sync…") }
                if (!s.ready && !s.running && s.review == null) {
                    Text(
                        "The first sync compares both collections. If one is empty, it gets the other's cards; if they differ, you choose what happens. " +
                            "Nothing changes before a backup is made.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            s.review?.let { plan ->
                ReviewCard(
                    plan,
                    onShow = { reviewing = plan },
                    onFirst = { confirmFirst = it },
                    onCardByCard = { cardByCard = plan },
                    onApprove = { c.archidekt.syncNow(approved = plan.appRemoved to plan.archRemoved) },
                    onLater = { c.archidekt.dismissReview() },
                )
            }
            if (s.conflicts.isNotEmpty() && s.review == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${s.conflicts.size} card(s) changed on both sides", fontWeight = FontWeight.SemiBold)
                        Text("Everything else was synced. These wait until you decide which change to keep.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { resolving = true }, enabled = !s.running) { Text("Decide…") }
                    }
                }
            }

            s.lastReport?.let { r ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Last sync", style = MaterialTheme.typography.titleMedium)
                ReportView(r, maxLines = 30)
                TextButton(onClick = { history = c.archidekt.history() }) { Text("History") }
            }

            if (s.connected) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Cards from Archidekt go to", style = MaterialTheme.typography.titleMedium)
                PullTo.entries.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { c.archidekt.setPullTo(p) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = s.pullTo == p, onClick = { c.archidekt.setPullTo(p) })
                        Text(p.label)
                    }
                }
                Text(
                    "When a card is edited on Archidekt (say, its condition), its copies stay in the binders they were in. Cards removed on Archidekt " +
                        "leave the app from the trade binder first, then Unsorted, other binders, and deck binders last.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Binders as Archidekt labels", style = MaterialTheme.typography.titleMedium)
                TagMode.entries.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { c.archidekt.setTagMode(t) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = s.tagMode == t, onClick = { c.archidekt.setTagMode(t) })
                        Column {
                            Text(t.label)
                            Text(t.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Labels only go from the app to Archidekt. Your other Archidekt labels are left alone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(
                    title = "Sync automatically",
                    body = "When you open the app (at most once an hour) and after you leave it if the collection changed. " +
                        "If a sync would remove more than ${ArchidektPlanner.REMOVAL_LIMIT} cards from either side, or cards changed on both sides, it waits for you here.",
                    checked = s.auto,
                    enabled = s.ready,
                    onChange = { c.archidekt.setAuto(it) },
                )
                SwitchRow(
                    title = "Only on Wi-Fi",
                    body = "Automatic syncs wait for Wi-Fi. “Sync now” always works.",
                    checked = s.wifiOnly,
                    enabled = s.auto && s.ready,
                    onChange = { c.archidekt.setWifiOnly(it) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Conditions", style = MaterialTheme.typography.titleMedium)
            Text(
                "The app uses Cardmarket's grades, Archidekt TCGplayer's. They're matched like this:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CONDITIONS.forEach { (code, name) ->
                val arch = ArchidektCodes.archCondition(code)
                Text("$name ($code) → ${ArchidektCodes.conditionNames[arch]} ($arch)", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text("From Archidekt:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            ArchidektCodes.toAppCondition.forEach { (arch, app) ->
                Text("${ArchidektCodes.conditionNames[arch]} ($arch) → ${CONDITIONS.firstOrNull { it.first == app }?.second} ($app)", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Two app grades can share one Archidekt grade, so for example changing a card from Excellent to Light Played in the app doesn't change it on Archidekt. " +
                    "Purchase prices are copied as plain numbers: Archidekt shows them in the currency set in your Archidekt account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (login) LoginDialog(onDone = { login = false })
    history?.let { h -> HistoryDialog(h, onDismiss = { history = null }) }
    reviewing?.let { p -> ChangesDialog(p, onDismiss = { reviewing = null }) }
    confirmFirst?.let { choice ->
        val plan = s.review
        AlertDialog(
            onDismissRequest = { confirmFirst = null },
            title = { Text(if (choice == Choice.APP) "The app replaces Archidekt?" else "Archidekt replaces the app?") },
            text = {
                Text(
                    if (choice == Choice.APP) "Archidekt's collection becomes the same as the app's: cards only on Archidekt are removed there, and counts are set to the app's."
                    else "The app's collection becomes the same as Archidekt's: cards only in the app are removed from it, and counts are set to Archidekt's. A backup is made first.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFirst = null
                    if (plan != null) c.archidekt.syncNow(decisions = plan.conflicts.associate { it.key to Decision(choice, it.app, it.arch) })
                }) { Text("Go ahead") }
            },
            dismissButton = { TextButton(onClick = { confirmFirst = null }) { Text("Cancel") } },
        )
    }
    cardByCard?.let { plan ->
        DecideDialog(
            title = "Decide card by card",
            intro = "Cards that are the same on both sides are left alone. For each card that differs, pick which count to keep.",
            changes = plan.conflicts,
            first = true,
            onDismiss = { cardByCard = null },
        ) { decisions ->
            cardByCard = null
            c.archidekt.syncNow(decisions = decisions)
        }
    }
    if (resolving) {
        DecideDialog(
            title = "Changed on both sides",
            intro = "These cards changed in the app and on Archidekt since the last sync. “Keep both changes” adds up what each side added or removed.",
            changes = s.conflicts,
            first = false,
            onDismiss = { resolving = false },
        ) { decisions ->
            resolving = false
            c.archidekt.syncNow(decisions = decisions)
        }
    }
}

@Composable
private fun ReviewCard(plan: Plan, onShow: () -> Unit, onFirst: (Choice) -> Unit, onCardByCard: () -> Unit, onApprove: () -> Unit, onLater: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (plan.firstNeedsChoice) {
                Text("First sync: the collections differ", fontWeight = FontWeight.SemiBold)
                Text(
                    "The app has ${plan.appTotal} cards and Archidekt ${plan.archTotal}; ${plan.conflicts.size} card(s) differ. " +
                        "The other ${plan.all.size - plan.conflicts.size} match. Nothing has been changed yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { onFirst(Choice.APP) }, modifier = Modifier.fillMaxWidth()) { Text("The app replaces Archidekt") }
                Button(onClick = { onFirst(Choice.ARCHIDEKT) }, modifier = Modifier.fillMaxWidth()) { Text("Archidekt replaces the app") }
                OutlinedButton(onClick = onCardByCard, modifier = Modifier.fillMaxWidth()) { Text("Decide card by card…") }
                TextButton(onClick = onLater) { Text("Not now") }
            } else {
                Text("This sync would remove a lot of cards", fontWeight = FontWeight.SemiBold)
                Text(
                    "${plan.appRemoved} copies from the app and ${plan.archRemoved} from Archidekt (and add ${plan.appAdded} to the app, ${plan.archAdded} to Archidekt). " +
                        "Have a look before going ahead; nothing has been changed yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShow) { Text("Show changes") }
                    Button(onClick = onApprove) { Text("Go ahead") }
                }
                TextButton(onClick = onLater) { Text("Not now") }
            }
        }
    }
}

/** One card's line: what each side holds. */
private fun changeText(ch: KeyChange): String = ArchidektSync.describe(ch.label, ch.key)

@Composable
private fun DecideDialog(
    title: String,
    intro: String,
    changes: List<KeyChange>,
    first: Boolean,
    onDismiss: () -> Unit,
    onApply: (Map<CardKey, Decision>) -> Unit,
) {
    val picks = remember { mutableStateMapOf<CardKey, Choice>() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { onApply(changes.mapNotNull { ch -> picks[ch.key]?.let { ch.key to Decision(it, ch.app, ch.arch) } }.toMap()) },
                        enabled = picks.isNotEmpty(),
                    ) { Text("Apply") }
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(intro, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { changes.forEach { picks[it.key] = Choice.APP } }) { Text("All: app") }
                        TextButton(onClick = { changes.forEach { picks[it.key] = Choice.ARCHIDEKT } }) { Text("All: Archidekt") }
                    }
                    Text("${picks.size} of ${changes.size} decided. Cards you leave open stay as they are and are asked again next time.", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(changes, key = { it.key.toString() }) { ch ->
                        Column {
                            Text(changeText(ch), fontWeight = FontWeight.SemiBold)
                            Text(
                                "App: ${ch.app} · Archidekt: ${ch.arch}" + (ch.snap?.let { " · last sync: $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val options = if (first) listOf(Choice.APP, Choice.ARCHIDEKT) else Choice.entries
                                options.forEach { opt ->
                                    val label = when (opt) {
                                        Choice.APP -> "App (${ch.app})"
                                        Choice.ARCHIDEKT -> "Archidekt (${ch.arch})"
                                        Choice.BOTH -> "Both (${maxOf(0, ch.app + ch.arch - (ch.snap ?: 0))})"
                                    }
                                    FilterChip(selected = picks[ch.key] == opt, onClick = { picks[ch.key] = opt }, label = { Text(label) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangesDialog(plan: Plan, onDismiss: () -> Unit) {
    val changes = remember(plan) { plan.changes.sortedBy { minOf(it.appDelta, it.archDelta) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    Text("What this sync would do", style = MaterialTheme.typography.titleLarge)
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(changes, key = { it.key.toString() }) { ch ->
                        Column {
                            Text(changeText(ch))
                            val parts = listOfNotNull(
                                ch.appDelta.takeIf { it != 0 }?.let { (if (it > 0) "+$it" else "−${-it}") + " in the app" },
                                ch.archDelta.takeIf { it != 0 }?.let { (if (it > 0) "+$it" else "−${-it}") + " on Archidekt" },
                                "changed on both sides, waits for you".takeIf { ch.conflict },
                                "purchase price".takeIf { ch.setAppPrice || ch.setArchPrice },
                            )
                            Text(
                                parts.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ch.appDelta < 0 || ch.archDelta < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportView(r: SyncReport, maxLines: Int) {
    Text("${Fmt.dateTime(r.at)} · ${r.summary}", fontWeight = FontWeight.SemiBold)
    r.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    r.lines.take(maxLines).forEach { l ->
        Text(
            l.text,
            style = MaterialTheme.typography.bodySmall,
            color = when (l.kind) {
                ReportLine.Kind.FAILED -> MaterialTheme.colorScheme.error
                ReportLine.Kind.CONFLICT -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
    if (r.lines.size > maxLines) Text("…and ${r.lines.size - maxLines} more", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HistoryDialog(history: List<SyncReport>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    Text("Archidekt sync history", style = MaterialTheme.typography.titleLarge)
                }
                if (history.isEmpty()) Text("No syncs yet.", Modifier.padding(16.dp))
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history) { r ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { ReportView(r, maxLines = 200) }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginDialog(onDone: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(c.settings.archidektUser) }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(c.archidekt.server) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text("Log in to Archidekt") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user, { user = it }, label = { Text("User name or e-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (BuildConfig.DEBUG) {
                    OutlinedTextField(server, { server = it }, label = { Text("Server (debug builds)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "The password is only used to log in and isn't stored: the app keeps Archidekt's login token, encrypted on this phone. " +
                        "Logging in with Google or another account? Set a password in your Archidekt account settings first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        if (BuildConfig.DEBUG) c.archidekt.server = server
                        c.archidekt.logIn(user, password)
                        onDone()
                    } catch (e: Exception) {
                        error = e.message ?: e.javaClass.simpleName
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy && user.isNotBlank() && password.isNotEmpty()) { Text("Log in") }
        },
        dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text("Cancel") } },
    )
}
