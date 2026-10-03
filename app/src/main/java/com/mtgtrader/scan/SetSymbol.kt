package com.mtgtrader.scan

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * An expansion symbol's outline, filled in and scaled into an [SetSymbol.N]×N grid (centred, aspect
 * kept), so a photo of the symbol can be compared with Scryfall's SVG of each candidate set.
 * Only the outline counts: rarity colours and inner details don't survive a phone camera.
 */
class Silhouette(val cells: BooleanArray, val aspect: Float) {
    val filled get() = cells.count { it }
}

object SetSymbol {
    const val N = 28

    /**
     * The symbol in a photo crop (luminance 0–255, row by row) taken around the right end of the
     * type line: the dark shape (every symbol has a black outline) that's tallest and furthest
     * right, filled in. Null when nothing symbol-like is there.
     */
    fun fromPhoto(luma: IntArray, w: Int, h: Int): Silhouette? = candidates(luma, w, h).firstOrNull()

    /**
     * The symbol cut out a few ways, for the matcher to try each: dark shapes at Otsu's split of the
     * type line (enough on the light type-line box of newer frames) and at the darkest few percent
     * of the crop; and whatever a closed bright ring encloses (the 90s frames ring their black
     * symbols in white, on a dark textured background).
     */
    fun candidates(luma: IntArray, w: Int, h: Int): List<Silhouette> {
        if (w < 8 || h < 8) return emptyList()
        val sorted = luma.sortedArray()
        fun pct(p: Double) = sorted[(sorted.size * p).toInt().coerceAtMost(sorted.size - 1)]
        // Otsu's threshold comes from the middle rows (the type line itself), not the frame around it.
        // Each also with small gaps closed (broken outlines), and without (shapes close to the type line's border).
        val dark = (listOf(otsu(luma.copyOfRange(w * (h / 5), w * (h - h / 5)))) + listOf(0.06, 0.12, 0.2).map { pct(it) + 1 })
            .distinct().flatMap { t ->
                val m = BooleanArray(w * h) { luma[it] < t }
                listOfNotNull(pick(m, w, h, checkContrast = true)?.let { dilate(it, w, h) }, pick(dilate(m, w, h), w, h, checkContrast = true))
            }
        val ringed = listOf(0.8, 0.88, 0.94).map { pct(it) }.distinct().mapNotNull { t ->
            val ring = dilate(BooleanArray(w * h) { luma[it] >= t }, w, h)
            val filled = fillHoles(ring, w, h)
            pick(BooleanArray(w * h) { filled[it] && !ring[it] }, w, h, checkContrast = false)?.let { dilate(it, w, h) }
        }
        return (dark.map { erode(fillHoles(it, w, h), w, h) } + ringed.map { fillHoles(it, w, h) }).mapNotNull { normalize(it, w, h) }
    }

    /** The symbol among the mask's shapes: the tallest one furthest right, with pieces right next to it. */
    private fun pick(mask: BooleanArray, w: Int, h: Int, checkContrast: Boolean): BooleanArray? {
        val n = mask.count { it }
        // Low contrast: nothing printed there (or a blurred frame).
        if (checkContrast && (n < w * h / 50 || n > w * h * 3 / 4)) return null
        val labels = IntArray(w * h)
        val comps = components(mask, w, h, labels)
            // Not the frame or the type line's border (they run off the crop), nor specks.
            .filter { it.top > 0 && it.bottom < h - 1 && it.left > 0 && it.right < w - 1 && it.width <= w * 0.6f && it.size >= 6 }
        val best = comps
            .filter { it.height >= h * 0.3f && it.width >= 3 && it.size >= 12 }
            // Type-line text ends before the symbol; the symbol is the tall shape on the right.
            .maxByOrNull { it.height * (0.6f + it.cx / w) } ?: return null
        // Symbols in parts (Tempest's cloud and lightning) count as one.
        val reach = best.height * 0.25f
        val parts = comps.filter { c ->
            c === best || (c.left <= best.right + reach && c.right >= best.left - reach && c.top <= best.bottom + reach && c.bottom >= best.top - reach &&
                c.height <= best.height * 1.2f && c.size >= best.size * 0.12f)
        }.map { it.label }.toSet()
        return BooleanArray(w * h) { labels[it] in parts }
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int) = BooleanArray(w * h) { i ->
        val x = i % w; val y = i / w
        m[i] || (x > 0 && m[i - 1]) || (x < w - 1 && m[i + 1]) || (y > 0 && m[i - w]) || (y < h - 1 && m[i + w])
    }

    private fun erode(m: BooleanArray, w: Int, h: Int) = BooleanArray(w * h) { i ->
        val x = i % w; val y = i / w
        m[i] && (x == 0 || m[i - 1]) && (x == w - 1 || m[i + 1]) && (y == 0 || m[i - w]) && (y == h - 1 || m[i + w])
    }

    /** The symbol as Scryfall draws it: an alpha mask (0–255) of the rendered SVG. */
    fun fromAlpha(alpha: IntArray, w: Int, h: Int): Silhouette? {
        val mask = BooleanArray(w * h) { alpha[it] > 96 }
        if (mask.none { it }) return null
        return normalize(fillHoles(mask, w, h), w, h)
    }

    /** 0–1: how well two outlines overlap, lowered when their proportions differ. */
    fun similarity(a: Silhouette, b: Silhouette): Float {
        var both = 0
        var either = 0
        for (i in a.cells.indices) {
            if (a.cells[i] && b.cells[i]) both++
            if (a.cells[i] || b.cells[i]) either++
        }
        if (either == 0) return 0f
        val iou = both.toFloat() / either
        val shape = 1f - min(1f, abs(ln(a.aspect / b.aspect)).toFloat())
        return iou * (0.5f + 0.5f * shape)
    }

    /** Crops the mask to its shape and scales it into the N×N grid, centred. */
    fun normalize(mask: BooleanArray, w: Int, h: Int): Silhouette? {
        var l = w; var r = -1; var t = h; var b = -1
        for (y in 0 until h) for (x in 0 until w) if (mask[y * w + x]) {
            l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y)
        }
        if (r < 0) return null
        val bw = r - l + 1
        val bh = b - t + 1
        val scale = N.toFloat() / max(bw, bh)
        val ox = (N - bw * scale) / 2
        val oy = (N - bh * scale) / 2
        val cells = BooleanArray(N * N)
        for (gy in 0 until N) for (gx in 0 until N) {
            // Sample the source pixel under the grid cell's centre.
            val sx = ((gx + 0.5f - ox) / scale).toInt() + l
            val sy = ((gy + 0.5f - oy) / scale).toInt() + t
            if (sx in l..r && sy in t..b && mask[sy * w + sx]) cells[gy * N + gx] = true
        }
        return Silhouette(cells, bw.toFloat() / bh)
    }

    /** Fills enclosed holes: whatever the outside (reached from the border) doesn't touch becomes part of the shape. */
    fun fillHoles(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val outside = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        fun push(i: Int) { if (!mask[i] && !outside[i]) { outside[i] = true; stack.addLast(i) } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return BooleanArray(w * h) { !outside[it] }
    }

    /** Otsu's threshold: the grey level that best splits the pixels into dark and light. */
    fun otsu(luma: IntArray): Int {
        val hist = IntArray(256)
        luma.forEach { hist[it.coerceIn(0, 255)]++ }
        val total = luma.size.toDouble()
        val sumAll = (0..255).sumOf { it * hist[it].toDouble() }
        var sumB = 0.0
        var wB = 0.0
        var best = 0.0
        var threshold = 128
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; threshold = i + 1 }
        }
        return threshold
    }

    class Component(val label: Int, val size: Int, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left + 1
        val height get() = bottom - top + 1
        val cx get() = (left + right) / 2f
    }

    /** 8-connected shapes of the mask; [labels] gets each pixel's shape (1-based, 0 = background). */
    fun components(mask: BooleanArray, w: Int, h: Int, labels: IntArray): List<Component> {
        val out = mutableListOf<Component>()
        val stack = ArrayDeque<Int>()
        var next = 0
        for (start in mask.indices) {
            if (!mask[start] || labels[start] != 0) continue
            next++
            var size = 0; var l = w; var r = 0; var t = h; var b = 0
            labels[start] = next
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val x = i % w
                val y = i / w
                size++
                l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (mask[j] && labels[j] == 0) { labels[j] = next; stack.addLast(j) }
                }
            }
            out += Component(next, size, l, t, r, b)
        }
        return out
    }
}
