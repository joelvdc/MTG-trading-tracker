package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The collection as a CSV file for Archidekt's importer, with its "ManaBox – Single folder" preset (since 1.78, when
 * the app stopped syncing the collection with Archidekt). The preset reads 18 columns by position, skipping the first
 * line: 1 name, 2 set code, 3 set name, 4 collector number, 5 foil, 7 quantity, 9 Scryfall ID, 14 condition and 15
 * language; the others are filled in where the app knows them and are ignored by Archidekt.
 *
 * Archidekt has no binders, so copies of the same printing, finish, condition, language and marks are one line. With
 * "only cards added since the last export", a line holds the copies more than at this phone's last whole-collection
 * export for Archidekt ([Snapshot], a small file on the phone): new cards and extra copies. Removed cards aren't in
 * the file; they're removed on Archidekt by hand.
 */
object ArchidektExport {
    data class Line(
        val name: String,
        val setCode: String,
        val setName: String,
        val number: String,
        val rarity: String,
        val scryfallId: String,
        val finish: Finish,
        val condition: String,
        val language: String,
        val signed: Boolean,
        val altered: Boolean,
        val quantity: Int,
    ) {
        val key get() = listOf(scryfallId, finish.name, condition, language, signed, altered).joinToString("|")
    }

    /** What the last export for Archidekt held: copies per [Line.key], and when. */
    @Serializable
    data class Snapshot(val at: Long, val quantities: Map<String, Int>)

    val HEADER = listOf(
        "Name", "Set code", "Set name", "Collector number", "Foil", "Rarity", "Quantity", "ManaBox ID", "Scryfall ID",
        "Purchase price", "Misprint", "Altered", "Signed", "Condition", "Language", "Purchase price currency",
        "Binder Name", "Binder Type",
    )

    /** The collection's stacks as lines, one per printing, finish, condition, language and marks (binders merged). */
    fun lines(items: List<CollectionItem>): List<Line> =
        items.groupBy { listOf(it.card.scryfallId, it.finish, it.condition, it.language, it.signed, it.altered) }.map { (_, same) ->
            val i = same.first()
            Line(
                i.card.name, i.card.setCode, i.card.setName, i.card.collectorNumber, i.card.rarity, i.card.scryfallId,
                i.finish, i.condition, i.language, i.signed, i.altered, same.sumOf { it.quantity },
            )
        }.sortedWith(compareBy({ it.name.lowercase() }, { it.setCode }, { it.number }))

    /** The copies in [now] beyond those in [last]: new lines and extra copies. */
    fun since(now: List<Line>, last: Snapshot?): List<Line> {
        if (last == null) return now
        return now.mapNotNull { l ->
            val more = l.quantity - (last.quantities[l.key] ?: 0)
            if (more > 0) l.copy(quantity = more) else null
        }
    }

    fun csv(lines: List<Line>): String = buildString {
        appendLine(Csv.row(*HEADER.toTypedArray()))
        for (l in lines) {
            appendLine(
                Csv.row(
                    l.name, l.setCode.uppercase(), l.setName, l.number, manaBoxFinish(l.finish), l.rarity, l.quantity, "", l.scryfallId,
                    "", "false", l.altered, l.signed, manaBoxCondition(l.condition), l.language.lowercase(), "", "", "",
                )
            )
        }
    }

    fun snapshotOf(lines: List<Line>, at: Long = System.currentTimeMillis()) = Snapshot(at, lines.associate { it.key to it.quantity })

    private val json = Json { ignoreUnknownKeys = true }
    private fun file(dir: File) = File(dir, "archidekt-export.json")

    /** This phone's last whole-collection export for Archidekt, or null. */
    fun lastExport(dir: File): Snapshot? = runCatching { json.decodeFromString(Snapshot.serializer(), file(dir).readText()) }.getOrNull()

    fun saveExport(dir: File, snapshot: Snapshot) {
        val tmp = File(dir, "archidekt-export.json.tmp")
        tmp.writeText(json.encodeToString(Snapshot.serializer(), snapshot))
        tmp.renameTo(file(dir)).takeIf { it } ?: run { file(dir).delete(); tmp.renameTo(file(dir)) }
    }
}
