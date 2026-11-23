package com.mtgtrader.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import com.mtgtrader.container
import com.mtgtrader.data.Deck
import com.mtgtrader.data.SaltSum
import com.mtgtrader.data.WinOdds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The bracket card full screen, for showing the table: the same drawing as the printed card ([CardSheets]), so the
 * screen, the PDF and the website match. The screen stays on at full brightness. The only rule-zero card since 1.116.
 */
@Composable
fun RuleZeroDialog(deck: Deck, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            (view.parent as? DialogWindowProvider)?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
            onDispose { view.keepScreenOn = false }
        }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val card = rememberPrintCard(deck)
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    deck.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                IconButton(
                    onClick = { card?.let { scope.launch { saveCard(context, it)?.let { f -> shareCard(context, f, deck) } } } },
                    enabled = card != null,
                ) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                if (card == null) CircularProgressIndicator(color = Color.White) else BracketCardImage(card)
            }
        }
    }
}

/** Everything [deck]'s bracket card shows, worked out again when its rating or lines change; null until it's known. */
@Composable
internal fun rememberPrintCard(deck: Deck): PrintCard? {
    val context = LocalContext.current
    val card by produceState<PrintCard?>(null, deck.archidektId, deck.saltCard, deck.updatedAt) {
        value = printCard(context, deck)
    }
    return card
}

/** The bracket card as drawn for print, as large as fits the space it's given. */
@Composable
fun BracketCardImage(card: PrintCard, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val ratio = CardSheets.CARD_W / CardSheets.CARD_H
        val widthDp = if (constraints.hasBoundedHeight) min(maxWidth.value, maxHeight.value * ratio) else maxWidth.value
        val widthPx = with(density) { widthDp.dp.toPx() }.roundToInt().coerceAtLeast(1)
        val bitmap = remember(card, widthPx) { CardSheets.cardBitmap(card, widthPx).asImageBitmap() }
        Image(bitmap, "Bracket card for ${card.deck.name}", Modifier.width(widthDp.dp).aspectRatio(ratio))
    }
}

/** The deck's win turn (since 1.52), kept on the phone or worked out in the background; null for decks without a commander. */
@Composable
internal fun rememberWinOdds(deck: Deck): androidx.compose.runtime.State<WinOdds?> {
    val c = LocalContext.current.container
    return produceState<WinOdds?>(null, deck.archidektId, deck.saltCard, deck.colorIdentity) {
        if (deck.commanders.isNotBlank()) value = runCatching { c.winOdds.forDeck(deck) }.getOrNull()
    }
}

/**
 * The deck's salt sum (see [SaltSum]), from the EDHREC salt file on the phone: worked out again whenever [key] changes
 * (its lines, say). Null until it's known, or when none of its cards has a salt score. Since 1.115.
 */
@Composable
fun rememberSaltSum(deckId: Long, key: Any?): SaltSum? {
    val c = LocalContext.current.container
    val sum by produceState<SaltSum?>(null, deckId, key) {
        value = runCatching { c.saltScores.sumFor(c.db.deckDao().cards(deckId)) }.getOrNull()
    }
    return sum
}

// ---- Colours ----------------------------------------------------------------------------------

internal val HEADER_TINT = Color(0xCC26215C)

/** Fixed tile colours per bracket (light tints with dark text, readable on light and dark themes). */
internal fun bracketColors(b: Int?): Pair<Color, Color> = when (b) {
    1 -> Color(0xFFE1F5EE) to Color(0xFF085041)
    2 -> Color(0xFFEAF3DE) to Color(0xFF27500A)
    3 -> Color(0xFFFAEEDA) to Color(0xFF633806)
    4 -> Color(0xFFFAECE7) to Color(0xFF712B13)
    5 -> Color(0xFFFCEBEB) to Color(0xFF791F1F)
    else -> Color(0xFFF1EFE8) to Color(0xFF444441)
}

/** Salt tile colours by level: Mild like bracket 2, Moderate like 3, High like 4 (unknown grey). */
internal fun saltColors(level: String?): Pair<Color, Color> = bracketColors(
    when (level) {
        "Mild" -> 2
        "Moderate" -> 3
        "High" -> 4
        else -> null
    }
)

// ---- Sharing ----------------------------------------------------------------------------------

/** Saves the card as a PNG in the share folder. */
private suspend fun saveCard(context: Context, card: PrintCard): File? = withContext(Dispatchers.Default) {
    runCatching {
        val bitmap = CardSheets.cardBitmap(card, 1260)
        val dir = File(context.filesDir, "rulezero").apply { mkdirs() }
        File(dir, "${card.deck.archidektId}_bracket.png").also { f ->
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }.getOrNull()
}

private fun shareCard(context: Context, file: File, deck: Deck) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "${deck.name}: bracket card")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share bracket card"))
}
