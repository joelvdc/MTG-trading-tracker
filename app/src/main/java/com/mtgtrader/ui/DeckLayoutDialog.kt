package com.mtgtrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.mtgtrader.data.DeckCardSort
import com.mtgtrader.data.DeckGroupBy

/**
 * The deck page's view options (since 1.85, like the website's deck page): group by category, card type, mana value
 * or colour, and sort each section by mana value or name, optionally descending. Kept on this phone.
 */
@Composable
fun DeckLayoutDialog(
    group: DeckGroupBy,
    sort: DeckCardSort,
    descending: Boolean,
    onApply: (DeckGroupBy, DeckCardSort, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var g by remember { mutableStateOf(group) }
    var s by remember { mutableStateOf(sort) }
    var d by remember { mutableStateOf(descending) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group and sort") },
        text = {
            Column {
                Text("Group by", style = MaterialTheme.typography.titleSmall)
                DeckGroupBy.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { g = option }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = g == option, onClick = { g = option })
                        Text(option.label)
                    }
                }
                Text("Sort by", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                DeckCardSort.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { s = option }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = s == option, onClick = { s = option })
                        Text(option.label)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp).clickable { d = !d }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = d, onCheckedChange = { d = it })
                    Text("Descending")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(g, s, d) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
