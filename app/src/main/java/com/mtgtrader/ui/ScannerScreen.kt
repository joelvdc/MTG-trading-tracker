package com.mtgtrader.ui

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.produceState
import com.mtgtrader.data.PriceSet
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mtgtrader.container
import com.mtgtrader.data.AddResult
import com.mtgtrader.data.CardRef
import com.mtgtrader.data.CardTarget
import com.mtgtrader.data.Finish
import com.mtgtrader.data.MtgRepository
import com.mtgtrader.data.PriceType
import com.mtgtrader.scan.CardRecognizer
import com.mtgtrader.scan.CardTextAnalyzer
import com.mtgtrader.scan.Identified
import com.mtgtrader.scan.ScanClues
import com.mtgtrader.scan.ScanGuide
import com.mtgtrader.scan.SetSymbolMatcher
import com.mtgtrader.data.LANGUAGES
import com.mtgtrader.data.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontWeight as FW
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class ScannedEntry(
    val card: CardRef,
    val finish: Finish,
    val language: String,
    val result: AddResult,
    /** Its Cardmarket prices; the list shows the one chosen in Settings. */
    val prices: PriceSet,
    /** False when only the name was readable, so the printing is Scryfall's default guess. */
    val exactPrinting: Boolean,
    /** The printing was told apart by its set symbol (see [SetSymbolMatcher]). Since 1.16. */
    val bySymbol: Boolean = false,
) {
    fun unitPrice(type: PriceType): Double? = prices.best(type) ?: card.fallback(finish.foil)
}

/**
 * Drives recognition: a card is added once it has been identified on two consecutive reads,
 * and the same card is not added again until it has left the frame.
 */
class ScanController(
    private val repo: MtgRepository,
    private val recognizer: CardRecognizer,
    private val target: CardTarget,
    private val scope: CoroutineScope,
    private val symbols: SetSymbolMatcher? = null,
    private val settings: Settings? = null,
) {
    var status by mutableStateOf("Hold a card inside the frame")

    /** The language every scanned card gets ("" = as printed on the card, else English); kept between scans. */
    var language by mutableStateOf(settings?.scanLanguage ?: "")
        private set

    fun chooseLanguage(code: String) {
        language = code
        settings?.scanLanguage = code
    }
    private var symbolFor: String? = null
    var foil by mutableStateOf(false)
    var autoAdd by mutableStateOf(true)
    var pending by mutableStateOf<Pair<Identified, String?>?>(null)
    val added = mutableStateListOf<ScannedEntry>()
    var onAdded: () -> Unit = {}

    private var busy = false
    private var candidateId: String? = null
    private var hits = 0
    private var lastAddedId: String? = null
    private var lastSeen = 0L

    val analyzer = CardTextAnalyzer { clues -> onClues(clues) }

    /** Called on the main thread for every analysed frame. */
    private fun onClues(clues: ScanClues) {
        if (busy) return
        val now = SystemClock.elapsedRealtime()
        if (!clues.hasAnything) {
            if (now - lastSeen > 1500) {
                lastAddedId = null
                candidateId = null
                hits = 0
                if (pending == null) status = "Hold a card inside the frame"
            }
            return
        }
        lastSeen = now
        busy = true
        analyzer.paused.set(true)
        scope.launch {
            try {
                // A chosen language also lets set code + number alone identify a non-English card.
                val found = recognizer.identifyPrinting(if (clues.language == null && language.isNotEmpty()) clues.copy(language = language) else clues)
                if (found == null) {
                    status = "Reading… ${clues.names.firstOrNull() ?: clues.setCode ?: ""}"
                    return@launch
                }
                val card = found.card
                lastSeen = SystemClock.elapsedRealtime()
                // Only the name was read: cut the set symbol out of a coming frame to tell the printings apart.
                if (!found.exactPrinting && symbols != null && card.id != symbolFor) {
                    symbolFor = card.id
                    analyzer.symbol = null
                    analyzer.symbolWanted.set(true)
                }
                if (card.id == candidateId) hits++ else {
                    candidateId = card.id
                    hits = 1
                }
                if (hits >= 2 && card.id != lastAddedId) {
                    lastAddedId = card.id
                    if (autoAdd) add(found, clues.language) else {
                        pending = found to clues.language
                        status = "Found ${card.displayName} — tap Add"
                    }
                } else if (card.id == lastAddedId && autoAdd) {
                    status = "${card.displayName} added — show the next card"
                }
            } finally {
                busy = false
                analyzer.paused.set(false)
            }
        }
    }

    suspend fun add(found: Identified, language: String?) {
        val card = found.card
        val ref = card.toRef()
        // Foil-only printings (e.g. surge or etched foils) resolve to their foil finish by themselves.
        val f = ref.resolveFinish(if (foil) Finish.FOIL else Finish.NONFOIL)
        val lang = this.language.ifEmpty { null } ?: language ?: "EN"
        val result = repo.add(target, ref, f, lang, found.exactPrinting) ?: return
        val entry = ScannedEntry(ref, f, lang, result, repo.snapshot(ref, f.foil), found.exactPrinting)
        added.add(0, entry)
        pending = null
        status = "Added ${card.displayName} (${card.set.uppercase()})"
        onAdded()
        if (!found.exactPrinting) matchSymbol(entry)
    }

    /** Swaps a guessed printing for the one whose set symbol was photographed, when that's clear. */
    private fun matchSymbol(e: ScannedEntry) {
        val matcher = symbols ?: return
        scope.launch {
            var waited = 0
            while (analyzer.symbol == null && waited < 2500) {
                delay(100)
                waited += 100
            }
            val photo = analyzer.symbol?.second ?: return@launch
            val match = runCatching { matcher.match(e.card.name, photo, e.card.scryfallId) }.getOrNull() ?: return@launch
            val i = added.indexOf(e)
            if (i < 0) return@launch // undone or changed by hand meanwhile
            if (match.card.id == e.card.scryfallId) added[i] = e.copy(bySymbol = true)
            else {
                changePrinting(e, match.card.toRef(), e.finish, bySymbol = true)
                status = "${match.card.displayName}: ${match.card.setName} (recognised by its set symbol)"
            }
        }
    }

    fun addAgain(e: ScannedEntry) = scope.launch {
        val result = repo.add(target, e.card, e.finish, e.language, e.exactPrinting) ?: return@launch
        added.add(0, e.copy(result = result))
        onAdded()
    }

    /** Another printing or finish picked for a scanned card: the copy is swapped and stays in the list, in place. */
    fun changePrinting(e: ScannedEntry, card: CardRef, finish: Finish, bySymbol: Boolean = false) = scope.launch {
        val result = repo.changeAddedPrinting(e.result, target, card, finish, e.language) ?: return@launch
        val f = card.resolveFinish(finish)
        val updated = e.copy(card = card, finish = f, result = result, prices = repo.snapshot(card, f.foil), exactPrinting = true, bySymbol = bySymbol)
        val i = added.indexOf(e)
        if (i >= 0) added[i] = updated else added.add(0, updated)
    }

    fun undo(e: ScannedEntry) = scope.launch {
        repo.undoAdd(e.result)
        added.remove(e)
        if (lastAddedId == e.card.scryfallId) lastAddedId = null
    }
}

@Composable
fun ScannerScreen(nav: NavController, target: CardTarget) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val priceType by c.settings.priceType.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    val controller = remember { ScanController(c.repo, CardRecognizer(c.scryfall), target, scope, c.symbols, c.settings) }
    controller.onAdded = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    DisposableEffect(Unit) { onDispose { controller.analyzer.close() } }

    // Keep the screen awake while scanning.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var torch by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<ScannedEntry?>(null) }

    choosing?.let { e ->
        PrintingPicker(
            e, priceType,
            onPick = { card, finish ->
                choosing = null
                if (card.scryfallId != e.card.scryfallId || card.resolveFinish(finish) != e.finish) controller.changePrinting(e, card, finish)
            },
            onDismiss = { choosing = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Scan cards")
                        Text(rememberTargetLabel(target), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { nav.popBackStack(); nav.openSearch(target) }) { Icon(Icons.Default.Search, "Search instead") }
                    IconButton(onClick = { torch = !torch }) {
                        Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, "Torch")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { nav.popBackStack() },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(if (controller.added.isEmpty()) "Done" else "Done · ${controller.added.size} scanned") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (hasPermission) {
                Box(Modifier.fillMaxWidth(0.86f).align(Alignment.CenterHorizontally).aspectRatio(3f / 4f)) {
                    CameraPreview(controller.analyzer, torch)
                }
            } else {
                EmptyState(
                    "Camera permission needed",
                    "Allow camera access to scan cards, or use search instead.",
                )
                TextButton(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Allow camera")
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = controller.foil, onClick = { controller.foil = !controller.foil }, label = { Text("Foil") })
                FilterChip(selected = controller.autoAdd, onClick = { controller.autoAdd = !controller.autoAdd }, label = { Text("Auto-add") })
                Box {
                    var langMenu by remember { mutableStateOf(false) }
                    FilterChip(
                        selected = controller.language.isNotEmpty(),
                        onClick = { langMenu = true },
                        label = { Text(if (controller.language.isEmpty()) "Language: auto" else "Language: ${controller.language}") },
                    )
                    DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                        (listOf("" to "As printed on the card") + LANGUAGES).forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name, fontWeight = if (code == controller.language) FW.Bold else null) },
                                onClick = { langMenu = false; controller.chooseLanguage(code) },
                            )
                        }
                    }
                }
            }
            Text(
                controller.status,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                textAlign = TextAlign.End,
            )
            controller.pending?.let { (found, lang) ->
                val card = found.card
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardThumb(card.image, enlargeable = true)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(card.displayName, style = MaterialTheme.typography.titleSmall)
                            SetLine(card.toRef())
                        }
                        Button(onClick = { scope.launch { controller.add(found, lang) } }) { Text("Add") }
                    }
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(controller.added, key = { System.identityHashCode(it) }) { e ->
                    Card(
                        onClick = { choosing = e },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardThumb(e.card.imageUrl, width = 32)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.card.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SetLine(e.card)
                                    FinishTag(e.card, e.finish)
                                    if (e.language != "EN") Tag(e.language)
                                }
                                if (e.bySymbol) {
                                    Text(
                                        "Set recognised by its symbol · tap if wrong",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else if (!e.exactPrinting) {
                                    Text(
                                        "Choose printing",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(Fmt.money(e.unitPrice(priceType)), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { controller.addAgain(e) }) { Icon(Icons.Default.Add, "Add another copy") }
                            IconButton(onClick = { controller.undo(e) }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Every printing of a scanned card, with pictures and prices, to pick the right one (and whether
 * it's foil) without leaving the scanner. Printings that don't exist in the chosen finish say which
 * one they'd get, e.g. a surge-foil-only promo.
 */
@Composable
private fun PrintingPicker(entry: ScannedEntry, priceType: PriceType, onPick: (CardRef, Finish) -> Unit, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    var finish by remember { mutableStateOf(entry.finish) }
    val prints by produceState<List<CardRef>?>(null, entry.card.name) {
        value = runCatching { c.scryfall.prints(entry.card.name).map { it.toRef() } }.getOrDefault(emptyList())
    }
    val prices by produceState<Map<String, Double?>>(emptyMap(), prints, finish) {
        value = prints.orEmpty().associate { ref ->
            val f = ref.resolveFinish(finish)
            ref.scryfallId to (c.repo.snapshot(ref, f.foil).best(priceType) ?: ref.fallback(f.foil))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which ${entry.card.name}?") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Finish.NONFOIL to "Normal", Finish.FOIL to "Foil", Finish.ETCHED to "Etched").forEach { (f, label) ->
                        FilterChip(selected = finish == f, onClick = { finish = f }, label = { Text(label) })
                    }
                }
                Text(
                    "Tap the printing you have" + if (entry.finish != finish) " (or the highlighted one to only change the finish)." else ".",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                val list = prints
                when {
                    list == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    list.isEmpty() -> Text("Couldn't load the printings. Check the connection and try again.")
                    else -> LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), modifier = Modifier.heightIn(max = 480.dp)) {
                        items(list, key = { it.scryfallId }) { ref ->
                            val isCurrent = ref.scryfallId == entry.card.scryfallId
                            val f = ref.resolveFinish(finish)
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { onPick(ref, finish) }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CardThumb(ref.imageUrl, width = 92)
                                Text(ref.setName, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                Text(ref.setLabel, style = MaterialTheme.typography.labelSmall)
                                if (f != finish || f.foil) {
                                    Text(
                                        ref.finishName(f) + if (f != finish) " only" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (f != finish) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Text(Fmt.money(prices[ref.scryfallId]), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
@Composable
private fun CameraPreview(analyzer: ImageAnalysis.Analyzer, torch: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val selector4x3 = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val analysisSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build()
            val preview = Preview.Builder().setResolutionSelector(selector4x3).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(analysisSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, analyzer)
            try {
                p.unbindAll()
                camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                camera = null
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { pos ->
                        val point = previewView.meteringPointFactory.createPoint(pos.x, pos.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    }
                },
        ) {
            val g = ScanGuide.boxFor(size.width, size.height)
            val dim = Color.Black.copy(alpha = 0.45f)
            drawRect(dim, Offset.Zero, GSize(size.width, g.top))
            drawRect(dim, Offset(0f, g.bottom), GSize(size.width, size.height - g.bottom))
            drawRect(dim, Offset(0f, g.top), GSize(g.left, g.height))
            drawRect(dim, Offset(g.right, g.top), GSize(size.width - g.right, g.height))
            drawRoundRect(
                Color.White,
                Offset(g.left, g.top),
                GSize(g.width, g.height),
                CornerRadius(12f, 12f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
