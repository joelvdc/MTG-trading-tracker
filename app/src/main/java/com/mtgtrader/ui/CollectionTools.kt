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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mtgtrader.data.CONDITIONS
import com.mtgtrader.data.CollectionFilter
import com.mtgtrader.data.ColorMatch
import com.mtgtrader.data.DeckFilter
import com.mtgtrader.data.Finish
import com.mtgtrader.data.SortField
import com.mtgtrader.data.SortLevel
import com.mtgtrader.data.SortSpec

private val PRESETS = listOf(
    "Name" to SortSpec(listOf(SortLevel(SortField.NAME))),
    "Color, then name" to SortSpec(listOf(SortLevel(SortField.COLOR), SortLevel(SortField.NAME))),
    "Set and number" to SortSpec(listOf(SortLevel(SortField.NUMBER))),
    "Type, mana value, name" to SortSpec(listOf(SortLevel(SortField.TYPE), SortLevel(SortField.MANA_VALUE), SortLevel(SortField.NAME))),
    "Most valuable" to SortSpec(listOf(SortLevel(SortField.VALUE))),
    "Newest" to SortSpec(listOf(SortLevel(SortField.RECENT))),
)

/** Sorting in layers ("Color, then Name"), each with its own direction. Since 1.18. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortDialog(initial: SortSpec, onDismiss: () -> Unit, onSave: (SortSpec) -> Unit) {
    var levels by remember { mutableStateOf(initial.levels) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESETS.forEach { (label, spec) ->
                        FilterChip(selected = levels == spec.levels, onClick = { levels = spec.levels }, label = { Text(label) })
                    }
                }
                HorizontalDivider()
                levels.forEachIndexed { i, level ->
                    Text(if (i == 0) "Sort by" else "then by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DropdownSelector(
                            "Field", level.field, SortField.entries.filter { f -> f == level.field || levels.none { it.field == f } }, { it.label },
                            { f -> levels = levels.toMutableList().also { it[i] = SortLevel(f) } }, Modifier.weight(1f),
                        )
                        if (levels.size > 1) IconButton(onClick = { levels = levels.toMutableList().also { it.removeAt(i) } }) { Icon(Icons.Default.Close, "Remove level") }
                    }
                    DropdownSelector(
                        "Order", level.reversed, listOf(false, true), { if (it) level.field.backward else level.field.forward },
                        { r -> levels = levels.toMutableList().also { it[i] = level.copy(reversed = r) } }, Modifier.fillMaxWidth(),
                    )
                }
                if (levels.size < 3) {
                    TextButton(onClick = {
                        val next = SortField.entries.first { f -> levels.none { it.field == f } }
                        levels = levels + SortLevel(next)
                    }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add a level")
                    }
                }
                Text(
                    "Color, type and mana value need the card details the app gets from Scryfall once; cards still without them go last.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(SortSpec(levels)) }) { Text("Sort") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A set the collection holds cards of, for the filter's set list. */
data class OwnedSet(val code: String, val name: String, val cards: Int)

private val COLOR_CHIPS = listOf('W' to "White", 'U' to "Blue", 'B' to "Black", 'R' to "Red", 'G' to "Green", 'C' to "Colorless", 'M' to "Multicolor")

/**
 * The filter button's dialog: the things awkward to type (colours, types, rarity, sets, finish,
 * condition, language, price, deck use). [count] shows how many cards a filter would leave.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterDialog(
    initial: CollectionFilter,
    sets: List<OwnedSet>,
    languages: List<String>,
    count: (CollectionFilter) -> Int,
    onDismiss: () -> Unit,
    onApply: (CollectionFilter) -> Unit,
) {
    var f by remember { mutableStateOf(initial) }
    var setQuery by remember { mutableStateOf("") }
    var minText by remember { mutableStateOf(initial.minPrice?.let { "%.2f".format(it) } ?: "") }
    var maxText by remember { mutableStateOf(initial.maxPrice?.let { "%.2f".format(it) } ?: "") }
    fun <T> Set<T>.toggle(v: T) = if (v in this) this - v else this + v
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Part("Color")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    COLOR_CHIPS.forEach { (ch, label) -> FilterChip(selected = ch in f.colors, onClick = { f = f.copy(colors = f.colors.toggle(ch)) }, label = { Text(label) }) }
                }
                if (f.colors.isNotEmpty()) {
                    ColorMatch.entries.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable { f = f.copy(colorMatch = m) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = f.colorMatch == m, onClick = { f = f.copy(colorMatch = m) })
                            Text(m.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Part("Type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CollectionFilter.TYPES.forEach { t -> FilterChip(selected = t in f.types, onClick = { f = f.copy(types = f.types.toggle(t)) }, label = { Text(t) }) }
                }
                Part("Rarity")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CollectionFilter.RARITIES.forEach { (k, label) -> FilterChip(selected = k in f.rarities, onClick = { f = f.copy(rarities = f.rarities.toggle(k)) }, label = { Text(label) }) }
                }
                Part("Set")
                if (f.sets.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        f.sets.forEach { code ->
                            InputChip(
                                selected = true, onClick = { f = f.copy(sets = f.sets - code) },
                                label = { Text(sets.firstOrNull { it.code == code }?.name ?: code.uppercase()) },
                                trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = setQuery, onValueChange = { setQuery = it }, placeholder = { Text("Find a set (name or code)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                val q = setQuery.trim()
                if (q.isNotEmpty()) {
                    sets.filter { (it.name.contains(q, true) || it.code.equals(q, true)) && it.code !in f.sets }.take(8).forEach { s ->
                        Text(
                            "${s.name} (${s.code.uppercase()}) · ${s.cards}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { f = f.copy(sets = f.sets + s.code); setQuery = "" }.padding(vertical = 8.dp),
                        )
                    }
                }
                Part("Finish")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Finish.NONFOIL to "Normal", Finish.FOIL to "Foil", Finish.ETCHED to "Etched").forEach { (fin, label) ->
                        FilterChip(selected = fin in f.finishes, onClick = { f = f.copy(finishes = f.finishes.toggle(fin)) }, label = { Text(label) })
                    }
                }
                Part("Condition")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CONDITIONS.forEach { (k, _) -> FilterChip(selected = k in f.conditions, onClick = { f = f.copy(conditions = f.conditions.toggle(k)) }, label = { Text(k) }) }
                }
                if (languages.size > 1) {
                    Part("Language")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        languages.forEach { l -> FilterChip(selected = l in f.languages, onClick = { f = f.copy(languages = f.languages.toggle(l)) }, label = { Text(l) }) }
                    }
                }
                Part("Value per card (€)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText, onValueChange = { minText = it; f = f.copy(minPrice = Fmt.parseMoney(it)) }, label = { Text("From") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxText, onValueChange = { maxText = it; f = f.copy(maxPrice = Fmt.parseMoney(it)) }, label = { Text("To") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
                Part("Decks")
                DeckFilter.entries.forEach { d ->
                    Row(Modifier.fillMaxWidth().clickable { f = f.copy(decks = d) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = f.decks == d, onClick = { f = f.copy(decks = d) })
                        Text(d.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(f) }) { Text("Show ${"%,d".format(count(f))} cards") } },
        dismissButton = {
            Row {
                TextButton(onClick = { f = CollectionFilter(); minText = ""; maxText = "" }, enabled = !f.isEmpty) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun Part(title: String) {
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** The active filter, as removable chips under the search field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveFilterChips(f: CollectionFilter, sets: List<OwnedSet>, onChange: (CollectionFilter) -> Unit, onEdit: () -> Unit) {
    val chips = buildList<Pair<String, CollectionFilter>> {
        if (f.colors.isNotEmpty()) {
            val names = COLOR_CHIPS.filter { it.first in f.colors }.joinToString(", ") { it.second }
            add((if (f.colorMatch == ColorMatch.ANY) names else "${f.colorMatch.label}: $names") to f.copy(colors = emptySet()))
        }
        if (f.types.isNotEmpty()) add(f.types.joinToString(", ") to f.copy(types = emptySet()))
        if (f.rarities.isNotEmpty()) add(CollectionFilter.RARITIES.filter { it.first in f.rarities }.joinToString(", ") { it.second } to f.copy(rarities = emptySet()))
        if (f.sets.isNotEmpty()) add(f.sets.joinToString(", ") { code -> sets.firstOrNull { it.code == code }?.code?.uppercase() ?: code.uppercase() } to f.copy(sets = emptySet()))
        if (f.finishes.isNotEmpty()) add(f.finishes.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase).replace("Nonfoil", "Normal") } to f.copy(finishes = emptySet()))
        if (f.conditions.isNotEmpty()) add(f.conditions.joinToString(", ") to f.copy(conditions = emptySet()))
        if (f.languages.isNotEmpty()) add(f.languages.joinToString(", ") to f.copy(languages = emptySet()))
        if (f.minPrice != null || f.maxPrice != null) {
            add(
                when {
                    f.maxPrice == null -> "€%.2f and up".format(f.minPrice)
                    f.minPrice == null -> "Up to €%.2f".format(f.maxPrice)
                    else -> "€%.2f–€%.2f".format(f.minPrice, f.maxPrice)
                } to f.copy(minPrice = null, maxPrice = null)
            )
        }
        if (f.decks != DeckFilter.ANY) add((if (f.decks == DeckFilter.IN_DECKS) "In my decks" else "In no deck") to f.copy(decks = DeckFilter.ANY))
    }
    if (chips.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { (label, without) ->
            InputChip(
                selected = true, onClick = onEdit, label = { Text(label, maxLines = 1) },
                trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp).clickable { onChange(without) }) },
            )
        }
        if (chips.size > 1) AssistChip(onClick = { onChange(CollectionFilter()) }, label = { Text("Clear all") })
    }
}
