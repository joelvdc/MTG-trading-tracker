package com.mtgtrader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.mtgtrader.R

/**
 * Mana costs as Scryfall writes them ("{2}{B}{B}", "{1}{G/W}{G/W}", "{2}{B} // {B}"), split into the
 * symbols drawn on deck pages. Since 1.43.
 */
object ManaCost {
    private val symbol = Regex("\\{([^}]+)\\}")

    /** The symbols of each face, e.g. [["2", "B"], ["B"]]; empty for a card without a cost (a land). */
    fun faces(cost: String?): List<List<String>> =
        cost.orEmpty().split(" // ").map { face -> symbol.findAll(face).map { it.groupValues[1].uppercase() }.toList() }
            .filter { it.isNotEmpty() }
}

/** The Mana font by Andrew Gioia (SIL Open Font License; see assets/licenses/mana-font.txt). */
private val ManaFont = FontFamily(Font(R.font.mana))

private val ManaTint = mapOf(
    "W" to Color(0xFFF0F2C0), "U" to Color(0xFFB5CDE3), "B" to Color(0xFFACA29A), "R" to Color(0xFFDB8664), "G" to Color(0xFF93B483),
)
private val GenericTint = Color(0xFFBEB9B2)
private val SymbolInk = Color(0xFF111111)

/** The font's character for a symbol, or null when it has none (then the symbol is written out). */
private fun glyph(s: String): String? = when (s) {
    "W" -> ""
    "U" -> ""
    "B" -> ""
    "R" -> ""
    "G" -> ""
    "C" -> ""
    "X" -> ""
    "P" -> ""
    "S" -> ""
    else -> s.toIntOrNull()?.takeIf { it in 0..10 }?.let { (0xe605 + it).toChar().toString() }
}

private fun symbolStyle(size: TextUnit, font: FontFamily? = ManaFont) = TextStyle(
    fontFamily = font,
    fontSize = size,
    lineHeight = size,
    color = SymbolInk,
    fontWeight = if (font == null) FontWeight.Bold else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** One symbol on its coloured circle, as Archidekt and Scryfall show them; hybrids are split in two. */
@Composable
private fun ManaSymbol(s: String, size: Dp) {
    val density = LocalDensity.current
    val big = with(density) { (size * 0.68f).toSp() }
    val small = with(density) { (size * 0.44f).toSp() }
    val parts = s.split('/').filter { it.isNotEmpty() }
    val colours = parts.filter { it != "P" }
    when {
        // Phyrexian ({B/P}): the Phyrexian mark on the colour's circle.
        parts.size >= 2 && parts.last() == "P" && colours.size == 1 ->
            Box(Modifier.size(size).clip(CircleShape).background(ManaTint[colours[0]] ?: GenericTint), contentAlignment = Alignment.Center) {
                Text(glyph("P")!!, style = symbolStyle(big))
            }
        // Hybrid ({G/W}, {2/W}, {W/U/P}): the circle split corner to corner, a small symbol in each half.
        colours.size >= 2 -> Box(Modifier.size(size)) {
            val first = ManaTint[colours[0]] ?: GenericTint
            val second = ManaTint[colours[1]] ?: GenericTint
            Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
                drawRect(second)
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(this@Canvas.size.width, 0f); lineTo(0f, this@Canvas.size.height); close() }, first)
            }
            SymbolText(colours[0], small, Modifier.align(Alignment.TopStart).padding(start = size * 0.12f, top = size * 0.02f))
            SymbolText(colours[1], small, Modifier.align(Alignment.BottomEnd).padding(end = size * 0.12f, bottom = size * 0.02f))
        }
        else -> Box(Modifier.size(size).clip(CircleShape).background(ManaTint[s] ?: GenericTint), contentAlignment = Alignment.Center) {
            SymbolText(s, if (glyph(s) == null && s.length > 1) small else big)
        }
    }
}

@Composable
private fun SymbolText(s: String, size: TextUnit, modifier: Modifier = Modifier) {
    val g = glyph(s)
    if (g != null) Text(g, style = symbolStyle(size), modifier = modifier)
    else Text(s, style = symbolStyle(size * 0.8f, font = null), modifier = modifier)
}

/** A card's mana cost as symbols; a double-faced card's faces with a "/" between them. Nothing for a land. */
@Composable
fun ManaCostView(cost: String?, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    val faces = ManaCost.faces(cost)
    if (faces.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
        faces.forEachIndexed { i, face ->
            if (i > 0) Text(" / ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            face.forEach { ManaSymbol(it, size) }
        }
    }
}
