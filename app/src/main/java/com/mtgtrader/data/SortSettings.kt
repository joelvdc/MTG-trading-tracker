package com.mtgtrader.data

/** How copies of a card are kept together by the sort (since 1.107). */
enum class Together(val key: String, val label: String) {
    NAME("name", "All printings of a card"),
    PRINTING("printing", "Only the same printing"),
    OFF("off", "Off: every copy on its own");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: NAME

        /** Only the values it knows (for sync): anything else leaves the setting as it is. */
        fun of(k: String?): Together? = entries.firstOrNull { it.key == k }
    }
}

/** Which copies go to the Decks binder (since 1.107). */
enum class DeckCopies(val key: String, val label: String) {
    BEST("best", "Best copy first"),
    CHEAPEST("cheapest", "Cheapest first");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: BEST

        /** Only the values it knows (for sync): anything else leaves the setting as it is. */
        fun of(k: String?): DeckCopies? = entries.firstOrNull { it.key == k }
    }
}

/**
 * One sort level: its binder (a local id, or a binder uid in the synced form), how many copies it holds
 * (0 on the last level = no limit) and an optional minimum price in euros.
 */
data class SortLevelSetting(val binder: String, val max: Int, val minPrice: Double?)

object SortLevelsCodec {
    const val MAX_LEVELS = 6

    /** Lines "<binder>|<max>|<minPrice or empty>", best first. */
    fun encode(levels: List<SortLevelSetting>): String = levels.take(MAX_LEVELS)
        .joinToString("\n") { "${it.binder}|${it.max}|${it.minPrice?.let { m -> m.toString() }.orEmpty()}" }

    /** Bad lines are skipped (empty binder, max not an int ≥ 0, minPrice not a number ≥ 0 → null); at most 6. */
    fun decode(s: String?): List<SortLevelSetting> = s.orEmpty().split('\n').mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val parts = line.split('|')
        if (parts.size !in 2..3) return@mapNotNull null
        val binder = parts[0].trim().ifEmpty { return@mapNotNull null }
        val max = parts[1].trim().toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
        val min = parts.getOrNull(2)?.trim().orEmpty()
        val minPrice = if (min.isEmpty()) null else min.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() } ?: return@mapNotNull null
        SortLevelSetting(binder, max, minPrice)
    }.take(MAX_LEVELS)

    /**
     * The three-binder setup of 1.104–1.106 as levels: only the binders that are set (non-empty / non-"0"), in the
     * order top, mid, bulk; top and mid get minPrice = floor, bulk null.
     */
    fun legacy(top: String?, mid: String?, bulk: String?, maxTop: Int, maxMid: Int, maxBulk: Int, floor: Double): List<SortLevelSetting> {
        fun set(v: String?) = v?.trim()?.ifEmpty { null }?.takeIf { it != "0" }
        val lowest = floor.takeIf { it.isFinite() && it >= 0 } ?: 0.0
        return listOfNotNull(
            set(top)?.let { SortLevelSetting(it, maxTop.coerceAtLeast(0), lowest) },
            set(mid)?.let { SortLevelSetting(it, maxMid.coerceAtLeast(0), lowest) },
            set(bulk)?.let { SortLevelSetting(it, maxBulk.coerceAtLeast(0), null) },
        )
    }
}
