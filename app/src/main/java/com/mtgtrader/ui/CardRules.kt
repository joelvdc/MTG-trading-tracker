package com.mtgtrader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mtgtrader.container
import com.mtgtrader.data.CardRules
import com.mtgtrader.data.RuleFace
import com.mtgtrader.data.toRuleFaces

/** The tabs under a card window's picture and text. Since 1.81. */
enum class CardTab(val label: String) { VERSIONS("Versions"), RULINGS("Rulings") }

/** What a card window knows about a card's text and rules: [faces] as soon as anything is known, [rules] once complete. */
data class CardRulesState(val faces: List<RuleFace> = emptyList(), val rules: CardRules? = null, val loading: Boolean = true)

/**
 * The card text, legalities and rulings for a printing: the details saved on the phone at once, then the saved file,
 * then Scryfall's answer when the file is missing or over 30 days old. Never throws; offline it keeps what it has.
 */
@Composable
fun rememberCardRules(scryfallId: String?, cardName: String): CardRulesState {
    val c = LocalContext.current.container
    val state by produceState(CardRulesState(), scryfallId) {
        value = CardRulesState()
        if (scryfallId == null || scryfallId.startsWith("00000000-")) {
            value = CardRulesState(loading = false)
            return@produceState
        }
        val local = runCatching { c.db.cardInfoDao().getMany(listOf(scryfallId)).firstOrNull()?.toRuleFaces(cardName) }.getOrNull().orEmpty()
        val cached = c.cardRules.cached(scryfallId)
        value = CardRulesState(cached?.faces ?: local, cached, loading = cached == null || !c.cardRules.isFresh(cached))
        if (value.loading) {
            val rules = c.cardRules.get(scryfallId)
            value = CardRulesState(rules?.faces ?: value.faces, rules, loading = false)
        }
    }
    return state
}

/** The Versions and Rulings tabs. */
@Composable
fun CardTabs(tab: CardTab, onTab: (CardTab) -> Unit) {
    TabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
        CardTab.entries.forEach { t ->
            Tab(selected = t == tab, onClick = { onTab(t) }, text = { Text(t.label) })
        }
    }
}

private val symbolPattern = Regex("\\{([^}]+)\\}")

/** Text with Scryfall's {T}, {2}{U} written as mana symbols in the line. */
@Composable
fun RulesText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val symbolSize = 15.dp
    val size = with(LocalDensity.current) { symbolSize.toSp() }
    val symbols = remember(text) { symbolPattern.findAll(text).map { it.groupValues[1].uppercase() }.toSet() }
    val annotated = remember(text) {
        buildAnnotatedString {
            var last = 0
            for (m in symbolPattern.findAll(text)) {
                append(text.substring(last, m.range.first))
                appendInlineContent(m.groupValues[1].uppercase(), m.value)
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
    val inline = symbols.associateWith { s ->
        InlineTextContent(Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)) { ManaSymbol(s, symbolSize) }
    }
    Text(annotated, modifier, style = style, inlineContent = inline)
}

/** Under a card's picture: each face's name, cost, type, rules text and power/toughness, a thin line between faces. */
@Composable
fun CardTextBlock(faces: List<RuleFace>, modifier: Modifier = Modifier) {
    if (faces.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        faces.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                ManaCostView(f.manaCost)
            }
            if (f.typeLine.isNotEmpty()) Text(f.typeLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            f.oracleText.split("\n").filter { it.isNotBlank() }.forEach { RulesText(it, Modifier.padding(top = 2.dp)) }
            val numbers = listOfNotNull(f.powerToughness, f.loyalty?.let { "Loyalty $it" }, f.defense?.let { "Defense $it" })
            if (numbers.isNotEmpty()) {
                Text(numbers.joinToString(" · "), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val mainFormats = listOf("commander", "standard", "pioneer", "modern", "legacy", "vintage", "pauper")
private val otherFormats = listOf(
    "brawl", "historic", "timeless", "alchemy", "explorer", "oathbreaker", "penny", "paupercommander", "predh", "duel",
    "oldschool", "premodern", "future", "gladiator",
)
private val formatNames = mapOf(
    "paupercommander" to "Pauper Commander", "oldschool" to "Old School", "predh" to "PreDH", "standardbrawl" to "Standard Brawl",
    "premodern" to "Premodern",
)
private val amber = Color(0xFFE0A100)

private fun formatName(key: String) = formatNames[key] ?: key.replaceFirstChar { it.uppercase() }

private fun statusWord(status: String) = when (status) {
    "legal" -> "Legal"
    "not_legal" -> "Not legal"
    "banned" -> "Banned"
    "restricted" -> "Restricted"
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun statusColor(status: String) = when (status) {
    "legal" -> TrendColors.up
    "banned" -> TrendColors.down
    "restricted" -> amber
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** A small box "Commander · Legal" tinted by the status; the word is there too, so colour isn't the only sign. */
@Composable
private fun LegalityChip(label: String, status: String) {
    val color = statusColor(status)
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.16f), border = BorderStroke(1.dp, color.copy(alpha = 0.6f))) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** The Rulings tab: the card's legality in each format, then Scryfall's rulings, newest first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulingsTab(state: CardRulesState, modifier: Modifier = Modifier) {
    val rules = state.rules
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rules == null) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
            } else {
                Text("Rulings need an internet connection the first time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        val main = mainFormats.filter { it in rules.legalities }
        val others = otherFormats.filter { it in rules.legalities } + (rules.legalities.keys - mainFormats.toSet() - otherFormats.toSet()).sorted()
        for (group in listOf(main, others)) {
            if (group.isEmpty()) continue
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.forEach { key ->
                    val status = rules.legalities.getValue(key)
                    LegalityChip("${formatName(key)} · ${statusWord(status)}", status)
                }
            }
        }
        if (rules.legalities.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("legal", "banned", "restricted", "not_legal").forEach { LegalityChip(statusWord(it), it) }
            }
        }
        HorizontalDivider()
        if (rules.rulings.isEmpty()) {
            Text("No rulings for this card.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rules.rulings.forEach { r ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(orderDate(r.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RulesText(r.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
