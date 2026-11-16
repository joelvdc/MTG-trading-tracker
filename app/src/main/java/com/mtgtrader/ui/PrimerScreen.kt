package com.mtgtrader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import kotlinx.coroutines.launch

/**
 * A deck's primer on its own page (since 1.98): the whole text, in the larger type the deck page hasn't room for, so
 * it can be read at the table. Editing opens the same dialog the deck page used to, and saving offers Undo here.
 * A deck without one says what a primer is for and offers to write it, so every deck can have one, Archidekt's too.
 */
@Composable
fun PrimerScreen(nav: NavController, deckId: Long) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deck by remember { c.db.deckDao().observe(deckId) }.collectAsStateWithLifecycle(null)
    var editing by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Primer")
                        deck?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.safePopBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit the primer") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val d = deck
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val primer = d.primer.orEmpty()
        if (primer.isBlank()) {
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "No primer yet. Write how the deck plays: its game plan, mulligans, key cards.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { editing = true }) { Text("Write a primer") }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            ) {
                PrimerBody(primer)
            }
        }
    }

    if (editing) {
        PrimerEditorDialog(deck?.primer, onDismiss = { editing = false }) { text ->
            editing = false
            scope.launch {
                val undo = c.deckEditor.setPrimer(deckId, text)
                if (snackbar.showUndo("Primer saved")) undo()
            }
        }
    }
}
