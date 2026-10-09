package com.mtgtrader.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the first sheet of a spreadsheet file into rows of cell texts: old Excel (.xls, as CardTrader
 * exports orders), new Excel (.xlsx) or CSV. Numbers come out without a trailing ".0" ("231", "1.5").
 * Small and dependency-free on purpose: a full Excel library would add megabytes to the app. Since 1.26.
 */
object Spreadsheet {
    class Unreadable(message: String) : Exception(message)

    fun read(bytes: ByteArray): List<List<String>> = when {
        bytes.size >= 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte() ->
            Xls.read(bytes)
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> Xlsx.read(bytes)
        else -> Csv.parse(String(bytes, Charsets.UTF_8).removePrefix("﻿"))
    }.map { row -> row.map { it.trim() } }.filter { row -> row.any { it.isNotEmpty() } }

    internal fun numberText(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite() && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()

    /** Builds rows from (row, column) → text, filling gaps with "". */
    private fun grid(cells: Map<Pair<Int, Int>, String>): List<List<String>> {
        if (cells.isEmpty()) return emptyList()
        val rows = cells.keys.maxOf { it.first } + 1
        val cols = cells.keys.maxOf { it.second } + 1
        return (0 until rows).map { r -> (0 until cols).map { c -> cells[r to c].orEmpty() } }
    }

    /** Excel 97–2003: a "compound file" (a little file system) holding a "Workbook" stream of BIFF8 records. */
    private object Xls {
        private const val END_OF_CHAIN = -2
        private const val FREE = -1

        fun read(bytes: ByteArray): List<List<String>> = grid(cells(workbookStream(bytes)))

        private fun ByteArray.u16(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
        private fun ByteArray.i32(at: Int) = u16(at) or (u16(at + 2) shl 16)

        private fun workbookStream(file: ByteArray): ByteArray {
            if (file.size < 512) throw Unreadable("The file is too short to be an Excel file")
            val sectorSize = 1 shl file.u16(0x1E)
            val miniSectorSize = 1 shl file.u16(0x20)
            val firstDirSector = file.i32(0x30)
            val miniCutoff = file.i32(0x38)
            val firstMiniFat = file.i32(0x3C)
            var difatSector = file.i32(0x44)

            fun sector(n: Int): Int = (n + 1) * sectorSize
            // The sectors holding the allocation table: 109 listed in the header, more in a chain of DIFAT sectors.
            val fatSectors = ArrayList<Int>()
            for (i in 0 until 109) file.i32(0x4C + i * 4).takeIf { it >= 0 }?.let { fatSectors += it }
            var guard = 0
            while (difatSector >= 0 && guard++ < 10_000) {
                val at = sector(difatSector)
                val per = sectorSize / 4 - 1
                for (i in 0 until per) file.i32(at + i * 4).takeIf { it >= 0 }?.let { fatSectors += it }
                difatSector = file.i32(at + per * 4)
            }
            val fat = IntArray(fatSectors.size * sectorSize / 4)
            fatSectors.forEachIndexed { k, s ->
                val at = sector(s)
                if (at + sectorSize > file.size) throw Unreadable("The Excel file is damaged")
                for (i in 0 until sectorSize / 4) fat[k * sectorSize / 4 + i] = file.i32(at + i * 4)
            }
            fun chain(start: Int): List<Int> {
                val out = ArrayList<Int>()
                var s = start
                while (s >= 0 && s != END_OF_CHAIN && s != FREE && out.size <= fat.size) {
                    out += s
                    s = fat.getOrElse(s) { END_OF_CHAIN }
                }
                return out
            }
            fun readChain(start: Int, size: Int): ByteArray {
                val out = java.io.ByteArrayOutputStream()
                for (s in chain(start)) {
                    val at = sector(s)
                    out.write(file, at, minOf(sectorSize, file.size - at))
                }
                return out.toByteArray().copyOf(size)
            }

            // The directory: 128-byte entries; entry 0 is the root, which also owns the mini stream.
            val dir = readChain(firstDirSector, chain(firstDirSector).size * sectorSize)
            data class Entry(val name: String, val type: Int, val start: Int, val size: Int)
            val entries = (0 until dir.size / 128).map { i ->
                val at = i * 128
                val nameBytes = (dir.u16(at + 0x40) - 2).coerceIn(0, 64)
                Entry(String(dir, at, nameBytes, Charsets.UTF_16LE), dir[at + 0x42].toInt(), dir.i32(at + 0x74), dir.i32(at + 0x78))
            }
            val root = entries.firstOrNull { it.type == 5 } ?: throw Unreadable("The Excel file has no contents")
            val book = entries.firstOrNull { it.type == 2 && (it.name == "Workbook" || it.name == "Book") }
                ?: throw Unreadable("The file has no Excel workbook in it")
            if (book.size >= miniCutoff) return readChain(book.start, book.size)

            // Small streams live in the mini stream, in 64-byte pieces chained by the mini allocation table.
            val miniStream = readChain(root.start, root.size)
            val miniFatBytes = readChain(firstMiniFat, chain(firstMiniFat).size * sectorSize)
            val miniFat = IntArray(miniFatBytes.size / 4) { miniFatBytes.i32(it * 4) }
            val out = java.io.ByteArrayOutputStream()
            var s = book.start
            var guard2 = 0
            while (s >= 0 && guard2++ <= miniFat.size) {
                out.write(miniStream, s * miniSectorSize, minOf(miniSectorSize, miniStream.size - s * miniSectorSize))
                s = miniFat.getOrElse(s) { END_OF_CHAIN }
            }
            return out.toByteArray().copyOf(book.size)
        }

        private class Record(val type: Int, val offset: Int, val data: ByteArray)

        private fun records(stream: ByteArray, from: Int = 0): Sequence<Record> = sequence {
            var p = from
            while (p + 4 <= stream.size) {
                val type = stream.u16(p)
                val len = stream.u16(p + 2)
                if (p + 4 + len > stream.size) break
                yield(Record(type, p, stream.copyOfRange(p + 4, p + 4 + len)))
                p += 4 + len
            }
        }

        /** The workbook's shared strings: the SST record and its CONTINUE records, where a string may carry on. */
        private fun sharedStrings(parts: List<ByteArray>): List<String> {
            if (parts.isEmpty()) return emptyList()
            val data = parts.fold(ByteArray(0)) { a, b -> a + b }
            val boundaries = parts.runningFold(0) { acc, b -> acc + b.size }.drop(1).toSet()
            var p = 8 // total and unique counts
            val unique = data.i32(4)
            val out = ArrayList<String>(maxOf(0, unique))
            while (out.size < unique && p + 3 <= data.size) {
                val cch = data.u16(p)
                var flags = data[p + 2].toInt()
                p += 3
                var runs = 0
                var ext = 0
                if (flags and 0x08 != 0) { runs = data.u16(p); p += 2 }
                if (flags and 0x04 != 0) { ext = data.i32(p); p += 4 }
                val sb = StringBuilder(cch)
                for (i in 0 until cch) {
                    // A string cut by a CONTINUE record restarts with a fresh flags byte (8- or 16-bit characters).
                    // (Also before the first character, when the string's header ended exactly at a record's end.)
                    if (p in boundaries) { flags = data[p].toInt(); p += 1 }
                    if (flags and 0x01 != 0) { sb.append(data.u16(p).toChar()); p += 2 } else { sb.append((data[p].toInt() and 0xFF).toChar()); p += 1 }
                }
                p += runs * 4 + ext
                out += sb.toString()
            }
            return out
        }

        private fun unicodeString(d: ByteArray, at: Int): String {
            val cch = d.u16(at)
            val flags = d[at + 2].toInt()
            var p = at + 3
            if (flags and 0x08 != 0) p += 2
            if (flags and 0x04 != 0) p += 4
            return if (flags and 0x01 != 0) String(d, p, cch * 2, Charsets.UTF_16LE) else String(d, p, cch, Charsets.ISO_8859_1)
        }

        private fun rk(v: Int): Double {
            val n = if (v and 0x02 != 0) (v shr 2).toDouble() else java.lang.Double.longBitsToDouble((v.toLong() and 0xFFFFFFFCL) shl 32)
            return if (v and 0x01 != 0) n / 100 else n
        }

        private fun cells(stream: ByteArray): Map<Pair<Int, Int>, String> {
            val sstParts = ArrayList<ByteArray>()
            var sheetOffset: Int? = null
            var inSst = false
            for (r in records(stream)) {
                when (r.type) {
                    0x00FC -> { sstParts += r.data; inSst = true }
                    0x003C -> if (inSst) sstParts += r.data
                    0x0085 -> {
                        inSst = false
                        // The first worksheet (not a chart or macro sheet).
                        if (sheetOffset == null && r.data.size >= 6 && r.data[5].toInt() == 0) sheetOffset = r.data.i32(0)
                    }
                    0x000A -> break // end of the workbook's global part
                    else -> inSst = false
                }
            }
            val sst = sharedStrings(sstParts)
            val start = sheetOffset ?: throw Unreadable("The Excel file has no sheet")
            val cells = HashMap<Pair<Int, Int>, String>()
            var pendingFormula: Pair<Int, Int>? = null
            for (r in records(stream, start).drop(1)) { // drop the sheet's own BOF
                val d = r.data
                when (r.type) {
                    0x000A -> break
                    0x00FD -> sst.getOrNull(d.i32(6))?.let { cells[d.u16(0) to d.u16(2)] = it }
                    0x0203 -> cells[d.u16(0) to d.u16(2)] = numberText(ByteBuffer.wrap(d, 6, 8).order(ByteOrder.LITTLE_ENDIAN).double)
                    0x027E -> cells[d.u16(0) to d.u16(2)] = numberText(rk(d.i32(6)))
                    0x00BD -> {
                        val row = d.u16(0)
                        val first = d.u16(2)
                        val n = (d.size - 6) / 6
                        for (k in 0 until n) cells[row to first + k] = numberText(rk(d.i32(4 + k * 6 + 2)))
                    }
                    0x0204 -> cells[d.u16(0) to d.u16(2)] = unicodeString(d, 6)
                    0x0205 -> if (d[7].toInt() == 0) cells[d.u16(0) to d.u16(2)] = if (d[6].toInt() != 0) "TRUE" else "FALSE"
                    0x0006 -> {
                        val key = d.u16(0) to d.u16(2)
                        if (d.u16(12) == 0xFFFF) {
                            when (d[6].toInt()) {
                                0 -> pendingFormula = key // the text follows in a STRING record
                                1 -> cells[key] = if (d[8].toInt() != 0) "TRUE" else "FALSE"
                            }
                        } else {
                            cells[key] = numberText(ByteBuffer.wrap(d, 6, 8).order(ByteOrder.LITTLE_ENDIAN).double)
                        }
                    }
                    0x0207 -> pendingFormula?.let { cells[it] = unicodeString(d, 0); pendingFormula = null }
                }
            }
            return cells
        }
    }

    /** Excel 2007+: a zip of XML files. */
    private object Xlsx {
        fun read(bytes: ByteArray): List<List<String>> {
            val files = HashMap<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name.endsWith(".xml") || e.name.endsWith(".rels")) files[e.name.removePrefix("/")] = zip.readBytes()
                }
            }
            val shared = files["xl/sharedStrings.xml"]?.let { xml ->
                doc(xml).getElementsByTagName("si").let { list -> (0 until list.length).map { text(list.item(it) as Element) } }
            }.orEmpty()
            val sheet = firstSheet(files) ?: throw Unreadable("The Excel file has no sheet")
            val cells = HashMap<Pair<Int, Int>, String>()
            val list = doc(sheet).getElementsByTagName("c")
            for (i in 0 until list.length) {
                val c = list.item(i) as Element
                val (row, col) = position(c.getAttribute("r")) ?: continue
                val v = c.getElementsByTagName("v").item(0)?.textContent
                cells[row to col] = when (c.getAttribute("t")) {
                    "s" -> v?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> c.getElementsByTagName("is").item(0)?.let { text(it as Element) }.orEmpty()
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    "str", "e" -> v.orEmpty()
                    else -> v?.toDoubleOrNull()?.let(::numberText) ?: v.orEmpty()
                }
            }
            return grid(cells)
        }

        private fun firstSheet(files: Map<String, ByteArray>): ByteArray? {
            // The workbook lists its sheets in order; the relationships file says which part each one is.
            val workbook = files["xl/workbook.xml"]?.let(::doc)
            val rels = files["xl/_rels/workbook.xml.rels"]?.let(::doc)
            val firstId = workbook?.getElementsByTagName("sheet")?.item(0)?.let { (it as Element).getAttribute("r:id") }
            val target = rels?.getElementsByTagName("Relationship")?.let { l ->
                (0 until l.length).map { l.item(it) as Element }.firstOrNull { it.getAttribute("Id") == firstId }?.getAttribute("Target")
            }
            target?.let { t -> files[if (t.startsWith("/")) t.removePrefix("/") else "xl/$t"]?.let { return it } }
            return files.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.sorted().firstOrNull()?.let { files[it] }
        }

        private fun doc(xml: ByteArray) = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setExpandEntityReferences(false)
        }.newDocumentBuilder().parse(ByteArrayInputStream(xml))

        /** The visible text of a shared or inline string: its <t> parts, without phonetic hints. */
        private fun text(e: Element): String {
            val ts = e.getElementsByTagName("t")
            return (0 until ts.length).map { ts.item(it) }.filter { it.parentNode?.nodeName != "rPh" }.joinToString("") { it.textContent }
        }

        /** "B3" → (2, 1). */
        private fun position(ref: String): Pair<Int, Int>? {
            val letters = ref.takeWhile { it.isLetter() }
            val row = ref.drop(letters.length).toIntOrNull() ?: return null
            if (letters.isEmpty()) return null
            val col = letters.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
            return row - 1 to col
        }
    }
}
