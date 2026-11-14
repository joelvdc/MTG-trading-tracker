package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgtrader.container
import com.mtgtrader.data.FirstChoice
import com.mtgtrader.data.FirstSync
import com.mtgtrader.data.WebAccountClient
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.IOException

/** The "Sync" part of Settings: an MTG Trader account on the website (since 1.79; Nextcloud sync ended in 1.82). */
@Composable
fun SyncSection() {
    val c = LocalContext.current.container
    val s by c.sync.status.collectAsStateWithLifecycle()
    var loggingIn by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (!s.connected) {
        if (s.nextcloudEnded) NextcloudEndedCard(onLogIn = { loggingIn = true }, onForget = { scope.launch { c.sync.disconnect() } })
        Text(
            "Keep your collection, decks, trades and settings the same on all your phones and on ${WebAccountClient.HOST}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { loggingIn = true }) { Text("Log in to my MTG Trader account") }
    } else {
        Text("Logged in as ${s.user} on ${s.server}")
        when {
            s.running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Syncing…", color = MaterialTheme.colorScheme.primary)
            }
            s.lastSyncAt > 0 -> Text("Last synced: ${Fmt.dateTime(s.lastSyncAt)}")
            !s.ready -> Text("Not synced yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        s.olderPhone?.let { older ->
            Spacer(Modifier.height(4.dp))
            Text(olderPhoneNote(older.name), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = c.sync::forgetOlderPhone, contentPadding = PaddingValues(0.dp)) { Text("I don't use that phone any more") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.ready) {
                Button(onClick = { c.sync.syncNow() }, enabled = !s.running) { Text("Sync now") }
            } else {
                Button(onClick = { c.appScope.launch { c.sync.prepareFirst() } }, enabled = !s.running && s.firstChoice == null) {
                    Text("Start syncing")
                }
            }
            OutlinedButton(onClick = { confirmDisconnect = true }, enabled = !s.running) { Text("Log out") }
        }
        Spacer(Modifier.height(12.dp))
        SwitchRow(
            title = "Sync automatically",
            body = "After changes, when you open or leave the app, and hourly.",
            checked = s.autoSync,
            onChange = c.sync::setAutoSync,
        )
        SwitchRow(
            title = "Only on Wi-Fi",
            body = "Automatic syncs wait for Wi-Fi.",
            checked = s.wifiOnly,
            enabled = s.autoSync,
            onChange = c.sync::setWifiOnly,
        )
    }

    if (loggingIn) AccountDialog(switching = s.nextcloudEnded, onDone = { loggingIn = false })
    s.firstChoice?.let { choice ->
        FirstSyncDialog(choice, "your account", onChoose = c.sync::chooseFirst, onLater = c.sync::cancelFirstChoice)
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Log out of your account?") },
            text = { Text("This phone stops syncing. Its data stays on the phone, and the copy in your account stays there for your other phones.") },
            confirmButton = {
                TextButton(onClick = { confirmDisconnect = false; scope.launch { c.sync.disconnect() } }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Logs in to an MTG Trader account on the website, or makes one with an invite code from the owner;
 * then the first sync follows. [switching]: this phone synced with Nextcloud (until 1.81) and moves over. Since 1.79.
 */
@Composable
private fun AccountDialog(switching: Boolean, onDone: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var newAccount by rememberSaveable { mutableStateOf(false) }
    var invite by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val problem = when {
        newAccount && password.isNotEmpty() && password.length < 10 -> "At least 10 characters."
        newAccount && again.isNotEmpty() && again != password -> "The two passwords don't match."
        else -> null
    }
    val ready = user.isNotBlank() && password.isNotEmpty() && (!newAccount || (invite.isNotBlank() && password.length >= 10 && again == password))

    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text(if (newAccount) "New MTG Trader account" else "MTG Trader account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    (if (newAccount) "You need an invite code from the owner of the website. " else "The account you made on ${WebAccountClient.HOST}. ") +
                        (if (switching) "This phone then syncs with your account instead of Nextcloud." else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (newAccount) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = invite, onValueChange = { invite = it; error = null }, label = { Text("Invite code") },
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user, onValueChange = { user = it; error = null }, label = { Text("User name") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = null },
                    label = { Text(if (newAccount) "Password (at least 10 characters)" else "Password") },
                    singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (newAccount) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = again, onValueChange = { again = it; error = null }, label = { Text("The password again") },
                        singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                problem?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Logging in…", color = MaterialTheme.colorScheme.primary)
                    }
                }
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                if (!busy) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (newAccount) "I have an account already" else "Make a new account (with an invite code)",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { newAccount = !newAccount; error = null }.padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && ready,
                onClick = {
                    busy = true
                    scope.launch {
                        // In the app scope, so leaving the screen doesn't stop the first sync.
                        val r = c.appScope.async {
                            runCatching {
                                if (newAccount) c.sync.registerWeb(invite, user, password) else c.sync.loginWeb(user, password)
                            }
                        }.await()
                        busy = false
                        r.onSuccess { onDone() }.onFailure { error = message(it) }
                    }
                },
            ) { Text(if (newAccount) "Make account" else "Log in") }
        },
        dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text("Cancel") } },
    )
}

private fun message(e: Throwable): String = when (e) {
    is IOException -> "Couldn't reach the server: ${e.message ?: e.javaClass.simpleName}"
    else -> e.message ?: "Something went wrong"
}

@Composable
private fun FirstSyncDialog(choice: FirstChoice, place: String, onChoose: (FirstSync) -> Unit, onLater: () -> Unit) {
    var pick by rememberSaveable { mutableStateOf(FirstSync.MERGE) }
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Both have data") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("This phone: ${choice.phone}\n${place.replaceFirstChar { it.uppercase() }}: ${choice.nextcloud}")
                Spacer(Modifier.height(8.dp))
                FirstSync.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { pick = f }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = pick == f, onClick = { pick = f })
                        Column {
                            Text(f.label(place))
                            Text(f.explanation(place), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(pick) }) { Text("Start syncing") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
}

/**
 * On a phone that synced with Nextcloud before 1.82: sync now goes through the MTG Trader account, which holds the
 * same data. Logging in moves this phone over (the first sync merges, nothing on Nextcloud is deleted).
 */
@Composable
internal fun NextcloudEndedCard(onLogIn: () -> Unit, onForget: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Nextcloud sync has ended: log in with your MTG Trader account", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "This phone isn't syncing any more. Log in with your account to carry on; nothing on Nextcloud is deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLogIn) { Text("Switch to my account") }
                TextButton(onClick = onForget) { Text("Forget Nextcloud") }
            }
        }
    }
}

/** The note in Settings while [name] still runs a version from before 1.27. */
internal fun olderPhoneNote(name: String) =
    "“$name” has an older version of MTG Trader: it can't sync until you update the app there. Its changes stay on that phone and sync after the update."

/**
 * Shown once, anywhere in the app, when Nextcloud sync finds another phone still on a version from
 * before 1.27 (signed and altered cards). Since 1.27.
 */
@Composable
fun OlderPhoneDialog() {
    val c = LocalContext.current.container
    val s by c.sync.status.collectAsStateWithLifecycle()
    val older = s.olderPhone?.takeUnless { it.acknowledged } ?: return
    AlertDialog(
        onDismissRequest = c.sync::acknowledgeOlderPhone,
        title = { Text("Update MTG Trader on your other phone") },
        text = {
            Text(
                "“${older.name}” last synced with an older version of MTG Trader. This version keeps signed and altered cards apart, " +
                    "which older versions can't read, so that phone stops syncing and asks to be updated.\n\n" +
                    "Nothing is lost: what changed on it syncs as soon as it has the new version.",
            )
        },
        confirmButton = { TextButton(onClick = c.sync::acknowledgeOlderPhone) { Text("OK") } },
    )
}
