package com.mtgtrader.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mtgtrader.container
import com.mtgtrader.data.Deck
import com.mtgtrader.data.DeckGroup
import com.mtgtrader.data.DeckSorting
import com.mtgtrader.data.PowerSource
import com.mtgtrader.data.RuleZeroCard
import com.mtgtrader.data.SaltCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** Which cards go in the PDF. Since 1.65. */
enum class PrintChoice(val label: String) {
    BRACKET("Bracket"), POWER("Power level"), BOTH("Both");

    val bracket get() = this != POWER
    val power get() = this != BRACKET
}

/** What the PDF will hold for [decks]: the decks with each kind of card, and the pages. */
data class PrintPlan(val bracket: List<Deck>, val power: List<Deck>) {
    val cards get() = bracket.size + power.size
    val pages get() = CardSheets.pages(bracket, power).size

    companion object {
        /** Power level cards only for decks the chosen source has rated. */
        fun of(decks: List<Deck>, choice: PrintChoice, source: PowerSource) = PrintPlan(
            if (choice.bracket) decks else emptyList(),
            if (choice.power) decks.filter { it.power(source) != null } else emptyList(),
        )
    }
}

/** A finished PDF and what it was made from (so it's made again only when the choices change). */
private data class MadePdf(val file: File, val pages: Int, val key: List<Any>)

/**
 * The bracket and power level cards of the chosen decks as an A4 PDF, to cut out at the size of a Magic card
 * (since 1.65): preview it, print it, or share it (to Signal's Note to Self, for example).
 */
@Composable
fun PrintCardsScreen(nav: NavController) {
    val context = LocalContext.current
    val c = context.container
    val all by remember { c.db.deckDao().observeAll() }.collectAsStateWithLifecycle(null)
    val source by c.settings.powerSource.collectAsStateWithLifecycle()
    val account by c.archidekt.status.collectAsStateWithLifecycle()
    val owner = account.username ?: c.settings.archidektUser.takeIf { it.isNotBlank() }
    // Decks scored before their card details were kept get them now.
    LaunchedEffect(Unit) { c.decks.fillMissingCardData() }
    val decks = all?.let { list ->
        remember(list, owner, source, com.mtgtrader.data.AppPower.readings) {
            DeckSorting.sort(list, c.settings.deckSort, c.settings.deckSortReversed, source)
                .filter { DeckGroup.of(it, owner) != DeckGroup.OTHERS && it.scored }
        }
    }
    var choice by remember { mutableStateOf(PrintChoice.entries.firstOrNull { it.name == c.settings.printCards } ?: PrintChoice.BOTH) }
    var inkSaver by remember { mutableStateOf(c.settings.printInkSaver) }
    var unticked by remember { mutableStateOf(emptySet<Long>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<MadePdf?>(null) }
    var preview by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val chosen = decks.orEmpty().filter { it.archidektId !in unticked }
    val plan = PrintPlan.of(chosen, choice, source)

    /** Makes the PDF (or uses the one already made from the same choices), then does [then] with it. */
    fun withPdf(then: (MadePdf) -> Unit) {
        val key = listOf(choice, inkSaver, source, plan.bracket.map { it.archidektId to it.saltCard.hashCode() }, plan.power.map { it.archidektId })
        made?.takeIf { it.key == key && it.file.exists() }?.let { return then(it) }
        scope.launch {
            busy = true
            error = null
            try {
                val pages = makePdf(context, plan, source, inkSaver)
                val pdf = MadePdf(pages.first, pages.second, key)
                made = pdf
                then(pdf)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = "Couldn't make the PDF: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    val pdf = made
    if (preview && pdf != null) {
        BackHandler { preview = false }
        val pages = remember(pdf) { PdfPages(pdf.file) }
        DisposableEffect(pages) { onDispose { pages.close() } }
        PdfPreview(
            pageCount = pdf.pages,
            render = { page, width -> pages.render(page, width) },
            onBack = { preview = false },
            onPrint = { printPdf(context, pdf.file, pdf.pages) },
            onShare = { sharePdf(context, pdf.file) },
        )
        return
    }

    PrintPicker(
        decks = decks,
        source = source,
        choice = choice,
        onChoice = { choice = it; c.settings.printCards = it.name },
        inkSaver = inkSaver,
        onInkSaver = { inkSaver = it; c.settings.printInkSaver = it },
        unticked = unticked,
        onUnticked = { unticked = it },
        busy = busy,
        error = error,
        onBack = { nav.safePopBackStack() },
        onPreview = { withPdf { preview = true } },
        onPrint = { withPdf { printPdf(context, it.file, it.pages) } },
        onShare = { withPdf { sharePdf(context, it.file) } },
    )
}

/** The choices: which cards, ink saver, and which decks; then Preview, Print or Share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPicker(
    decks: List<Deck>?,
    source: PowerSource,
    choice: PrintChoice,
    onChoice: (PrintChoice) -> Unit,
    inkSaver: Boolean,
    onInkSaver: (Boolean) -> Unit,
    unticked: Set<Long>,
    onUnticked: (Set<Long>) -> Unit,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
) {
    val chosen = decks.orEmpty().filter { it.archidektId !in unticked }
    val plan = PrintPlan.of(chosen, choice, source)
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Print cards as PDF") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "${chosen.size} decks · ${plan.cards} cards · ${plan.pages} A4 " + if (plan.pages == 1) "page" else "pages",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val unrated = if (choice.power) chosen.filter { it.power(source) == null } else emptyList()
                    Text(
                        listOfNotNull(
                            if (choice == PrintChoice.BOTH) "Bracket cards first, then power level cards." else null,
                            "Each card is 63 × 88 mm, like a Magic card: print at 100% (actual size).",
                            unrated.takeIf { it.isNotEmpty() }?.let { list -> "No power level from ${source.site} yet: " + list.joinToString(", ") { it.name } + "." },
                        ).joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
                    Spacer(Modifier.height(10.dp))
                    if (busy) {
                        Text("Making the PDF…", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    }
                    val enabled = !busy && plan.cards > 0
                    Button(onClick = onPreview, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Visibility, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Preview")
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onPrint, enabled = enabled, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Print")
                        }
                        OutlinedButton(onClick = onShare, enabled = enabled, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (decks == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item(key = "choices") {
                Column {
                    Text("Cards", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        PrintChoice.entries.forEachIndexed { i, option ->
                            SegmentedButton(
                                selected = option == choice,
                                onClick = { onChoice(option) },
                                shape = SegmentedButtonDefaults.itemShape(i, PrintChoice.entries.size),
                            ) { Text(option.label, maxLines = 1) }
                        }
                    }
                    Row(Modifier.padding(top = 10.dp).clickable { onInkSaver(!inkSaver) }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ink saver", style = MaterialTheme.typography.bodyLarge)
                            Text("Light headers without the commander art", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = inkSaver, onCheckedChange = onInkSaver)
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Decks", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onUnticked(emptySet()) }) { Text("Select all") }
                        TextButton(onClick = { onUnticked(decks.map { it.archidektId }.toSet()) }) { Text("None") }
                    }
                }
            }
            if (decks.isEmpty()) {
                item(key = "empty") { EmptyState("No scored decks yet", "Your own decks get these cards once Commander Salt has scored them.") }
            }
            items(decks, key = { it.archidektId }) { deck ->
                val ticked = deck.archidektId !in unticked
                val toggle = { onUnticked(if (ticked) unticked + deck.archidektId else unticked - deck.archidektId) }
                Row(Modifier.fillMaxWidth().height(52.dp).clickable(onClick = toggle), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ticked, onCheckedChange = { toggle() })
                    Column(Modifier.weight(1f)) {
                        Text(deck.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(deck.commanders, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(6.dp))
                    ColorPips(deck.colorIdentity, size = 10)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        listOfNotNull(deck.bracketRealistic?.let { "B$it" }, deck.power(source)?.let { Scores.power(it, source) }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The PDF's pages as pictures: swipe between pages, pinch to zoom; then Print or Share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreview(
    pageCount: Int,
    render: suspend (page: Int, widthPx: Int) -> ImageBitmap?,
    onBack: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
) {
    val pager = rememberPagerState { pageCount }
    var zoomed by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Preview · page ${pager.currentPage + 1} of $pageCount") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the choices") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        (if (pageCount > 1) "Swipe for the next page, pinch to zoom in." else "Pinch to zoom in."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onPrint, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Print")
                        }
                        Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share PDF")
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color(0xFF2B2930))) {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = !zoomed) { page ->
                PreviewPage(page, render, onZoomed = { if (pager.currentPage == page) zoomed = it })
            }
            if (pageCount > 1) {
                Row(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pageCount) { i ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.35f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPage(page: Int, render: suspend (Int, Int) -> ImageBitmap?, onZoomed: (Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.TopCenter) {
        val density = LocalDensity.current
        // Twice the screen's width, so zooming in stays sharp.
        val widthPx = with(density) { (maxWidth.toPx() * 2).toInt() }.coerceIn(600, 2400)
        val image by produceState<ImageBitmap?>(null, page, widthPx) { value = render(page, widthPx) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.fillMaxWidth().aspectRatio(CardSheets.PAGE_W / CardSheets.PAGE_H).shadow(6.dp).background(Color.White)
                .pointerInput(Unit) {
                    // Two fingers zoom; once zoomed, one finger moves the page (and the pager stops swiping).
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1 || scale > 1f) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                val max = Offset(size.width * (scale - 1) / 2, size.height * (scale - 1) / 2)
                                offset = if (scale == 1f) Offset.Zero else (offset + event.calculatePan() * scale).let {
                                    Offset(it.x.coerceIn(-max.x, max.x), it.y.coerceIn(-max.y, max.y))
                                }
                                onZoomed(scale > 1f)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            contentAlignment = Alignment.Center,
        ) {
            image?.let { Image(it, "Page ${page + 1}", Modifier.fillMaxSize()) } ?: CircularProgressIndicator()
        }
    }
}

// ---- Making, printing and sharing the PDF ---------------------------------------------------------

/** Writes the PDF of [plan] (bracket pages first) to the share folder; returns the file and its page count. */
private suspend fun makePdf(context: Context, plan: PrintPlan, source: PowerSource, inkSaver: Boolean): Pair<File, Int> {
    val c = context.container
    val wins = (plan.bracket).associate { deck ->
        deck.archidektId to if (deck.commanders.isBlank()) null else runCatching { c.winOdds.forDeck(deck) }.getOrNull()
    }
    val art = if (inkSaver) emptyMap() else (plan.bracket + plan.power).distinctBy { it.archidektId }.associate { deck ->
        deck.archidektId to deck.coverUrl?.let { loadArt(context, it) }
    }
    fun card(kind: RuleZeroCard, deck: Deck) =
        PrintCard(kind, deck, SaltCard.decode(deck.saltCard), wins[deck.archidektId], source, art[deck.archidektId])
    val pages = CardSheets.pages(plan.bracket.map { card(RuleZeroCard.BRACKET, it) }, plan.power.map { card(RuleZeroCard.POWER, it) })
    return withContext(Dispatchers.Default) {
        val dir = File(context.filesDir, "rulezero").apply { mkdirs() }
        dir.listFiles { f -> f.name.endsWith(".pdf") }?.forEach { it.delete() }
        val file = File(dir, "MTG-Trader-cards-${LocalDate.now()}.pdf")
        CardSheets.writePdf(file, pages, inkSaver)
        file to pages.size
    }
}

/** The deck's cover art as a software bitmap (for drawing into the PDF); null when it can't be had. */
private suspend fun loadArt(context: Context, url: String): Bitmap? = runCatching {
    val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(900).build())
    (result as? SuccessResult)?.drawable?.toBitmap()
}.getOrNull()

private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share the cards"))
}

/** Opens Android's print screen with the PDF (A4, as made). */
private fun printPdf(context: Context, file: File, pages: Int) {
    val activity = context.findActivity() ?: return
    val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback, extras: Bundle?,
        ) {
            if (cancellationSignal.isCanceled) return callback.onLayoutCancelled()
            val info = PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build()
            callback.onLayoutFinished(info, oldAttributes == null)
        }

        override fun onWrite(pageRanges: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
            try {
                file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
    manager.print("MTG Trader cards", adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
}

/** Draws the PDF's pages with Android's own PDF reader, one at a time. */
private class PdfPages(file: File) : Closeable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val mutex = Mutex()
    private var closed = false

    suspend fun render(index: Int, widthPx: Int): ImageBitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed || index !in 0 until renderer.pageCount) return@withContext null
            renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(widthPx, widthPx * page.height / page.width, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        }
    }

    override fun close() {
        // In the background, after the page being drawn (if any): that page finishes on the main thread, so waiting
        // for it here would freeze the screen.
        CoroutineScope(Dispatchers.IO).launch { mutex.withLock { closed = true; renderer.close(); fd.close() } }
    }
}
