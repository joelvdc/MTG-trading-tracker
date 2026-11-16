package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtgtrader.container
import com.mtgtrader.data.DeckCard
import com.mtgtrader.data.DeckHistory
import com.mtgtrader.data.DeckNotes
import com.mtgtrader.data.DeckPackage
import com.mtgtrader.data.DeckPackages
import com.mtgtrader.data.DeckRoles
import com.mtgtrader.data.PackageCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ---- What the deck's cards do (spec 5) -----------------------------------------------------------

/**
 * The line under a deck's header (since 1.97): its lands and how many cards ramp, draw, remove and so on
 * ([DeckRoles.counts]). A count outside its guideline shows in the app's warning amber, one inside it in the
 * success green; tapping a role (Lands aside) searches the deck for `tag:<role>`, so the cards behind it show.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleCountsLine(counts: List<DeckRoles.RoleCount>, onRole: (String) -> Unit, modifier: Modifier = Modifier) {
    if (counts.isEmpty()) return
    FlowRow(modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        counts.forEachIndexed { i, count ->
            val color = when (count.status) {
                "low", "high" -> WarningAmber
                "ok" -> TrendColors.up
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            // The separator belongs to the item before it, so a wrapped line doesn't start with a "·".
            val label = "${count.role} ${count.count}" + if (i < counts.lastIndex) " ·" else ""
            val style = MaterialTheme.typography.bodySmall
            if (count.role == "Lands") Text(label, style = style, color = color)
            else Text(label, style = style, color = color, modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onRole(count.role) })
        }
    }
}

// ---- The primer (spec 3) -------------------------------------------------------------------------

/**
 * The primer's first non-empty line with its writing marks taken off (# headings, - bullets, **bold**), for the one
 * line the deck page shows it in. Null when there is nothing to read: no primer, or only blank lines.
 */
internal fun primerFirstLine(primer: String?): String? =
    primer.orEmpty().lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.let { it.removePrefix("- ").trimStart('#').replace("**", "").trim() }
        ?.takeIf { it.isNotEmpty() }

/**
 * The deck's primer on its page, as one compact row since 1.98: a mark, "Primer: " and its first line (or "Add a
 * primer" when there is none), and a chevron saying it opens. The primer itself has its own page ([PrimerScreen]),
 * with room to be read at the table; this row only says there is one and what it starts with.
 */
@Composable
fun PrimerRow(primer: String?, onOpen: () -> Unit) {
    val first = remember(primer) { primerFirstLine(primer) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Article, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        if (first == null) {
            Text(
                "Add a primer",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Primer: ") }
                    append(first)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The primer in full, as its page shows it: larger than the app's small text, so it can be read at the table, with
 * its headings, bullets, bold pieces and paragraphs. Nothing is folded here; the page scrolls instead.
 */
@Composable
fun PrimerBody(primer: String, modifier: Modifier = Modifier) {
    val lines = remember(primer) { primer.split("\n") }
    Column(modifier) { lines.forEach { PrimerLine(it) } }
}

/** One line of the primer: a heading, a bullet, a paragraph, or the gap of an empty line. */
@Composable
private fun PrimerLine(line: String) {
    when {
        line.isBlank() -> Spacer(Modifier.height(8.dp))
        line.startsWith("### ") -> PrimerHeading(line.removePrefix("### "), 3)
        line.startsWith("## ") -> PrimerHeading(line.removePrefix("## "), 2)
        line.startsWith("# ") -> PrimerHeading(line.removePrefix("# "), 1)
        line.startsWith("- ") -> Row(Modifier.padding(vertical = 2.dp)) {
            Text("•", style = MaterialTheme.typography.bodyLarge)
            Text(primerMarkup(line.removePrefix("- ")), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
        }
        else -> Text(primerMarkup(line), style = MaterialTheme.typography.bodyLarge)
    }
}

/** A primer heading; [level] 1, 2 or 3 (the only heading sizes the primer has). */
@Composable
private fun PrimerHeading(text: String, level: Int) {
    Text(
        primerMarkup(text),
        style = when (level) {
            1 -> MaterialTheme.typography.headlineSmall
            2 -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.titleMedium
        },
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * [line] with its `**bold**` pieces bold. Nothing else of the markdown counts: an unclosed `**` stays as it is
 * written, so a primer always reads as the user typed it.
 */
private fun primerMarkup(line: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        val open = line.indexOf("**", i)
        val close = if (open < 0) -1 else line.indexOf("**", open + 2)
        if (open < 0 || close < 0) {
            append(line.substring(i))
            return@buildAnnotatedString
        }
        append(line.substring(i, open))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(open + 2, close)) }
        i = close + 2
    }
}

/**
 * Writing the deck's primer (since 1.97). The field keeps to [DeckNotes.MAX_PRIMER] characters; the light markdown
 * the primer page renders (# headings, - bullets, **bold**) is written as plain text here.
 */
@Composable
fun PrimerEditorDialog(primer: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(primer.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Primer") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(DeckNotes.MAX_PRIMER) },
                    placeholder = { Text("How the deck plays, its combos, what to look for…\n# Heading\n- A bullet\n**Bold**") },
                    minLines = 8,
                    maxLines = 16,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${text.length} / ${DeckNotes.MAX_PRIMER}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- Notes and tags in a card's window (spec 5) --------------------------------------------------

/** The small note mark after a deck card's name (since 1.97): this card has a note in its window. */
@Composable
internal fun NoteIcon(modifier: Modifier = Modifier) {
    Icon(Icons.Default.StickyNote2, "Has a note", modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A card's note in its window (since 1.97): the note, or a pencil to write one, and a one-to-three line field while
 * it's being changed. An empty note takes it away ([com.mtgtrader.data.DeckEditor.setCardNote]), so the mark by its
 * name goes too.
 */
@Composable
fun CardNoteSection(note: String?, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    // Follows the deck's note, so an undo (or a sync from the website) shows here too.
    var text by remember(note) { mutableStateOf(note.orEmpty()) }
    if (!editing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Note", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { text = note.orEmpty(); editing = true }) {
                Icon(Icons.Default.Edit, if (note.isNullOrBlank()) "Add a note" else "Change the note", Modifier.size(18.dp))
            }
        }
        Text(
            note ?: "No note yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (note == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Note", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(DeckNotes.MAX_NOTE) },
            placeholder = { Text("Why it's in the deck, what to remember when you play it…") },
            minLines = 1,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editing = false; onSave(text) }) { Text("Save") }
            TextButton(onClick = { text = note.orEmpty(); editing = false }) { Text("Cancel") }
        }
    }
}

/**
 * A card's tags in its window (since 1.97): its own tags, which hold in every deck and come off with their ✕, or,
 * when it has none of its own, the roles its categories and the website give it, shown lighter and unchangeable
 * because typing a tag of its own is what changes them. "Add tag" offers the role names and the user's other tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardTagsSection(ownTags: List<String>, autoTags: List<String>, suggestions: List<String>, onSetTags: (List<String>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tags", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (ownTags.isEmpty()) {
                autoTags.forEach { Tag(it, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                ownTags.forEach { tag ->
                    AssistChip(
                        onClick = { onSetTags(ownTags - tag) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(Icons.Default.Close, "Remove the tag $tag", Modifier.size(16.dp)) },
                    )
                }
            }
            AssistChip(
                onClick = { adding = true },
                label = { Text("Add tag") },
                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
            )
        }
        val caption = when {
            ownTags.isNotEmpty() -> "Your tags apply in every deck"
            autoTags.isNotEmpty() -> "Automatic: tag it yourself to change"
            else -> "No tags yet"
        }
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (adding) {
        AddTagDialog(title = "Add a tag", suggestions = suggestions, onDismiss = { adding = false }) { tag ->
            adding = false
            onSetTags(ownTags + tag)
        }
    }
}

// ---- Tagging (spec 5) ----------------------------------------------------------------------------

/** One tag, typed or picked from [suggestions]: the role names and the user's other tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTagDialog(title: String, suggestions: List<String>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(DeckRoles.MAX_TAG) },
                    label = { Text("Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SuggestionChips(suggestions) { text = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A tag for all the selected cards at once (since 1.97): added to each of them, or taken off each of them. Tags are
 * kept per card name ([com.mtgtrader.data.Settings.bulkTag]), so they hold in every deck.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkTagDialog(count: Int, suggestions: List<String>, onDismiss: () -> Unit, onApply: (add: String?, remove: String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag " + if (count == 1) "1 card" else "$count cards") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(DeckRoles.MAX_TAG) },
                    label = { Text("Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SuggestionChips(suggestions) { text = it }
                Text(
                    "Your tags apply in every deck, so they count in this deck's roles too.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onApply(null, text.trim()) }, enabled = text.isNotBlank()) { Text("Remove tag") }
                TextButton(onClick = { onApply(text.trim(), null) }, enabled = text.isNotBlank()) { Text("Add tag") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The tags worth tapping: the role names and the user's own tags, each once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChips(suggestions: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        suggestions.forEach { suggestion -> AssistChip(onClick = { onPick(suggestion) }, label = { Text(suggestion) }) }
    }
}

// ---- Packages (spec 6) ---------------------------------------------------------------------------

/** Saving the selected cards as a package (since 1.97), which can be added to decks later. */
@Composable
fun SaveAsPackageDialog(cards: List<PackageCard>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as package") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(DeckPackages.MAX_NAME) },
                    label = { Text("Package name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${cards.size} card" + (if (cards.size == 1) "" else "s") + ": " + cards.joinToString(", ") { if (it.quantity > 1) "${it.quantity}× ${it.name}" else it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank() && cards.isNotEmpty()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The saved packages (since 1.97): each one's name, its cards, where it's used, and what can be done with it. Adding
 * to and removing from the deck need a local deck (the others belong to Archidekt); making, renaming and deleting a
 * package never do, because the packages are the user's own ([com.mtgtrader.data.Settings.deckPackages]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeckPackagesSheet(
    local: Boolean,
    onDismiss: () -> Unit,
    onAdd: (DeckPackage) -> Unit,
    onRemove: (DeckPackage) -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = LocalContext.current.container
    val packages by c.settings.deckPackages.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<DeckPackage?>(null) }
    var deleting by remember { mutableStateOf<DeckPackage?>(null) }
    var creating by remember { mutableStateOf(false) }
    // Every deck's lines, for "used in": read once when the sheet opens, since a package can be in any deck.
    var lines by remember { mutableStateOf<Map<Long, List<DeckCard>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        lines = runCatching {
            withContext(Dispatchers.IO) {
                val dao = c.db.deckDao()
                dao.all().associate { deck -> deck.archidektId to dao.allLines(deck.archidektId) }
            }
        }.getOrDefault(emptyMap<Long, List<DeckCard>>())
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().heightIn(max = menuMaxHeight()).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                "Packages",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 2.dp),
            )
            Text(
                "Saved groups of cards to add to a deck, e.g. a mana rock suite.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
            )
            if (packages.isEmpty()) {
                Text(
                    "No packages yet. Save one from a selection of cards, or from pasted text below.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                )
            }
            packages.forEach { pkg ->
                PackageRow(
                    pkg = pkg,
                    use = remember(pkg, lines) { DeckPackages.use(pkg, lines) },
                    local = local,
                    open = open == pkg.id,
                    onToggle = { open = if (open == pkg.id) null else pkg.id },
                    onAdd = { onAdd(pkg) },
                    onRemove = { onRemove(pkg) },
                    onRename = { renaming = pkg },
                    onDelete = { deleting = pkg },
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            ListItem(
                headlineContent = { Text("New package from text…", color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().clickable { creating = true },
            )
        }
    }
    renaming?.let { pkg ->
        TextInputDialog("Rename \"${pkg.name}\"", "Package name", pkg.name, "Rename", onDismiss = { renaming = null }) { name ->
            renaming = null
            c.settings.renamePackage(pkg.id, name)
        }
    }
    deleting?.let { pkg ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${pkg.name}\"?") },
            text = { Text("The package goes from your packages, on your other phone too. The cards in your decks stay.") },
            confirmButton = {
                TextButton(onClick = { deleting = null; c.settings.deletePackage(pkg.id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    if (creating) {
        NewPackageFromTextDialog(onDismiss = { creating = false }) { message ->
            creating = false
            onMessage(message)
        }
    }
}

/** One package in the sheet: its name, count and use, its cards when opened, and what can be done with it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackageRow(
    pkg: DeckPackage,
    use: DeckPackages.Use,
    local: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(onClick = onToggle).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (open) "Hide the cards" else "Show the cards",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(pkg.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${pkg.cards.size} card" + (if (pkg.cards.size == 1) "" else "s") + " · Used in: " + useText(use),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (open) {
            Text(
                pkg.cards.joinToString(", ") { if (it.quantity > 1) "${it.quantity}× ${it.name}" else it.name }.ifEmpty { "No cards." },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 28.dp, bottom = 2.dp),
            )
        }
        FlowRow(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (local) {
                TextButton(onClick = onAdd) { Text("Add to this deck") }
                TextButton(onClick = onRemove) { Text("Remove from this deck") }
            }
            TextButton(onClick = onRename) { Text("Rename") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

/** "2 decks fully, 1 partly" for a package, or that no deck holds it yet. */
private fun useText(use: DeckPackages.Use): String {
    if (use.full.isEmpty() && use.partly.isEmpty()) return "none yet"
    return buildList {
        if (use.full.isNotEmpty()) add("${use.full.size} deck" + (if (use.full.size == 1) "" else "s") + " fully")
        if (use.partly.isNotEmpty()) add("${use.partly.size} partly")
    }.joinToString(", ")
}

/**
 * A new package from pasted text (since 1.97): the same formats as pasting cards into a deck are read
 * ([com.mtgtrader.data.DeckEditor.packageFromText]), and the cards Scryfall couldn't find are counted in the message.
 */
@Composable
fun NewPackageFromTextDialog(onDismiss: () -> Unit, onSaved: (message: String) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New package from text") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(DeckPackages.MAX_NAME) },
                    label = { Text("Package name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("One card per line, e.g. 1 Sol Ring. Moxfield, Archidekt and Arena lists work too.") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Looking the cards up…", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val packageName = name
                    val body = text
                    scope.launch {
                        busy = true
                        error = null
                        runCatching { c.deckEditor.packageFromText(packageName, body) }
                            .onSuccess { (pkg, notFound) ->
                                busy = false
                                if (pkg.cards.isEmpty()) {
                                    error = "Scryfall didn't find any of these cards."
                                    return@onSuccess
                                }
                                c.settings.savePackage(pkg)
                                onSaved(
                                    "Saved ${pkg.cards.size} card" + (if (pkg.cards.size == 1) "" else "s") +
                                        if (notFound.isNotEmpty()) ", ${notFound.size} not found" else "",
                                )
                            }
                            .onFailure {
                                busy = false
                                error = "Couldn't look the cards up: " + (it.message ?: "no internet?")
                            }
                    }
                },
                enabled = name.isNotBlank() && text.isNotBlank() && !busy,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

// ---- Change history (spec 4) ---------------------------------------------------------------------

/**
 * The deck's changes (since 1.97), newest first: the day, where they were made, and the cards that came in (green)
 * and went out (red). Both the app and the website write them, so a change made there shows here too.
 */
@Composable
fun ChangeHistorySheet(history: String?, onDismiss: () -> Unit) {
    val entries = remember(history) { DeckHistory.parse(history) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().heightIn(max = menuMaxHeight()).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text("Change history", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
            if (entries.isEmpty()) {
                Text(
                    "No changes recorded yet. Changes made from version 1.97 on show here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entries.forEach { entry ->
                val sources = entry.sources.joinToString(" · ") { sourceName(it) }
                Text(
                    dayText(entry.day) + (if (sources.isEmpty()) "" else " · $sources"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                entry.added.forEach { (name, count) ->
                    Text("+$count $name", style = MaterialTheme.typography.bodyMedium, color = TrendColors.up)
                }
                entry.removed.forEach { (name, count) ->
                    Text("\u2212$count $name", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** "16 Nov 2026" from a history entry's day ("2026-11-16"); the text itself when it isn't a date. */
private fun dayText(day: String): String =
    runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(day)

/** Where a change was made, as the sheet shows it: "Archidekt" for its reloads, else the name as it is. */
private fun sourceName(source: String): String = if (source == DeckHistory.SOURCE_ARCHIDEKT) "Archidekt" else source
