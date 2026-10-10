package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * What Archidekt and the app compare on: a printing in a finish, condition and language, with
 * condition and language in Archidekt's terms (the app's grades map onto Archidekt's coarser ones,
 * see [ArchidektCodes]), and whether the copies are signed or altered (since 1.27; on Archidekt the
 * "Signed" and "Altered" labels, see [MarkTags]). Binders don't count: Archidekt holds the whole
 * collection. Since 1.21.
 */
@Serializable
data class CardKey(
    val scryfallId: String,
    val finish: Finish,
    val condition: String,
    val language: String,
    val signed: Boolean = false,
    val altered: Boolean = false,
) {
    val marks get() = Marks(signed, altered)

    override fun toString() = "$scryfallId|${finish.name}|$condition|$language" + (if (signed) "|signed" else "") + (if (altered) "|altered" else "")

    companion object {
        fun of(item: CollectionItem) = CardKey(
            item.card.scryfallId, item.finish,
            ArchidektCodes.archCondition(item.condition), ArchidektCodes.archLanguage(item.language),
            item.signed, item.altered,
        )

        fun of(e: ArchidektEntry, markTags: MarkTags = MarkTags()) =
            CardKey(e.scryfallId, e.finish, e.condition, e.language, markTags.signed(e), markTags.altered(e))
    }
}

/**
 * Archidekt has no signed or altered field: entries of such copies carry a "Signed" or "Altered"
 * label, which the app reads and sets (whatever the binder label setting). Since 1.27.
 */
data class MarkTags(val signedIds: Set<Long> = emptySet(), val alteredIds: Set<Long> = emptySet()) {
    fun signed(e: ArchidektEntry) = e.tags.any { it in signedIds }
    fun altered(e: ArchidektEntry) = e.tags.any { it in alteredIds }

    /** The label ids an entry of [key] carries for its marks. */
    fun idsFor(key: CardKey): List<Long> =
        (if (key.signed) signedIds.take(1) else emptyList()) + (if (key.altered) alteredIds.take(1) else emptyList())

    companion object {
        const val SIGNED = "Signed"
        const val ALTERED = "Altered"
        val NAMES = setOf(SIGNED.lowercase(), ALTERED.lowercase())

        fun of(tags: List<ArchidektTag>) = MarkTags(
            tags.filter { it.name.trim().equals(SIGNED, true) }.map { it.id }.toSortedSet(),
            tags.filter { it.name.trim().equals(ALTERED, true) }.map { it.id }.toSortedSet(),
        )
    }
}

/** What to call a card in reports. */
@Serializable
data class CardLabel(val name: String = "", val setCode: String = "", val number: String = "")

/** What both sides agreed on for one [CardKey] at the end of the last sync. */
@Serializable
data class SnapEntry(
    val key: CardKey,
    val quantity: Int,
    val price: Double? = null,
    /** The Archidekt entries holding these copies, to recognise an entry edited on Archidekt. */
    val entryIds: List<Long> = emptyList(),
    val label: CardLabel = CardLabel(),
)

/** The last agreed state with one Archidekt account; shared between phones through Nextcloud. */
@Serializable
data class ArchidektState(
    val format: Int = 1,
    val userId: Long,
    val savedAt: Long = 0,
    val savedBy: String = "",
    val entries: List<SnapEntry> = emptyList(),
)

/** The app's side of one [CardKey]: all its copies, in any binder. */
data class AppSide(val quantity: Int, val price: Double?, val label: CardLabel)

/** How the user settled one card that changed on both sides (or differs on the first sync). */
enum class Choice(val label: String) { APP("Keep the app's"), ARCHIDEKT("Keep Archidekt's"), BOTH("Keep both changes") }

/** A [Choice], valid while both sides still hold what the user saw. */
data class Decision(val choice: Choice, val app: Int, val arch: Int)

/**
 * What happens to one card: the app should end up with [appTarget] copies and Archidekt with
 * [archTarget]. A [conflict] waits for the user and changes neither.
 */
data class KeyChange(
    val key: CardKey,
    val label: CardLabel,
    val app: Int,
    val arch: Int,
    val snap: Int?,
    val appTarget: Int,
    val archTarget: Int,
    val conflict: Boolean = false,
    /** The purchase price the side(s) below should get. */
    val price: Double? = null,
    val setAppPrice: Boolean = false,
    val setArchPrice: Boolean = false,
    /** Copies that moved here on Archidekt (the same entry, edited) from this key: they keep their binders in the app. */
    val movedFrom: CardKey? = null,
) {
    val appDelta get() = appTarget - app
    val archDelta get() = archTarget - arch
    val doesSomething get() = conflict || appDelta != 0 || archDelta != 0 || setAppPrice || setArchPrice
}

data class Plan(
    val first: Boolean,
    /** Every card both sides hold or held, with what happens to it. */
    val all: List<KeyChange>,
    /** Removals beyond the safe limit that the user has to look at first. */
    val needsReview: Boolean,
    /** The user settled some cards; any left open wait as ordinary conflicts. */
    val decided: Boolean = false,
) {
    val changes get() = all.filter { it.doesSomething }
    val conflicts get() = all.filter { it.conflict }
    val appAdded get() = all.sumOf { maxOf(0, it.appDelta) }
    val appRemoved get() = all.sumOf { maxOf(0, -it.appDelta) }
    val archAdded get() = all.sumOf { maxOf(0, it.archDelta) }
    val archRemoved get() = all.sumOf { maxOf(0, -it.archDelta) }
    val appTotal get() = all.sumOf { it.app }
    val archTotal get() = all.sumOf { it.arch }
    val changesApp get() = all.any { !it.conflict && (it.appDelta != 0 || it.setAppPrice) }
    val changesArchidekt get() = all.any { !it.conflict && (it.archDelta != 0 || it.setArchPrice) }
    /** The first sync can't go ahead on its own: both sides hold cards and they differ. */
    val firstNeedsChoice get() = first && conflicts.isNotEmpty() && !decided
    val waitsForUser get() = needsReview || firstNeedsChoice
}

/**
 * Three-way comparison of the app, Archidekt and what they agreed on last time ([snap]): a side
 * that differs from the agreement changed, and its change goes to the other side. Both sides
 * changed the same card differently: a conflict for the user, unless [decisions] settle it. On the
 * first sync (no [snap]) one empty side takes the other's cards; otherwise differing cards are
 * conflicts the user settles (app replaces Archidekt, Archidekt replaces the app, or card by card).
 */
object ArchidektPlanner {
    /** Removing more copies than this from a side waits for the user to look first. */
    const val REMOVAL_LIMIT = 20

    fun samePrice(a: Double?, b: Double?) = if (a == null || b == null) a == b else abs(a - b) < 0.005

    fun plan(
        app: Map<CardKey, AppSide>,
        arch: Map<CardKey, List<ArchidektEntry>>,
        snap: Map<CardKey, SnapEntry>?,
        decisions: Map<CardKey, Decision> = emptyMap(),
        approvedRemovals: Pair<Int, Int>? = null,
    ): Plan {
        val first = snap == null
        val appEmpty = app.values.sumOf { it.quantity } == 0
        val archEmpty = arch.values.sumOf { e -> e.sumOf { it.quantity } } == 0
        val keys = (app.keys + arch.keys + snap.orEmpty().keys).toSortedSet(compareBy({ it.scryfallId }, { it.finish }, { it.condition }, { it.language }, { it.signed }, { it.altered }))

        val out = keys.map { key ->
            val appSide = app[key]
            val entries = arch[key].orEmpty()
            val a = appSide?.quantity ?: 0
            val r = entries.sumOf { it.quantity }
            val s = snap?.let { it[key]?.quantity ?: 0 }
            val label = appSide?.label?.takeIf { it.name.isNotEmpty() }
                ?: entries.firstOrNull()?.let { CardLabel(it.name, it.setCode, it.number) }
                ?: snap?.get(key)?.label ?: CardLabel()
            val d = decisions[key]?.takeIf { it.app == a && it.arch == r }
            var choice: Choice? = null
            var appTarget = a
            var archTarget = r
            var conflict = false
            when {
                a == r -> {}
                s == null && archEmpty -> archTarget = a
                s == null && appEmpty -> appTarget = r
                s != null && a == s -> appTarget = r
                s != null && r == s -> archTarget = a
                d != null -> {
                    choice = d.choice
                    when (d.choice) {
                        Choice.APP -> archTarget = a
                        Choice.ARCHIDEKT -> appTarget = r
                        // On the first sync there's nothing to add up: keep the app's.
                        Choice.BOTH -> if (s == null) archTarget = a else maxOf(0, a + r - s).let { appTarget = it; archTarget = it }
                    }
                }
                else -> conflict = true
            }

            // Purchase price, for cards that end up on both sides.
            var price: Double? = null
            var setApp = false
            var setArch = false
            val pa = appSide?.price
            val pr = entries.firstNotNullOfOrNull { it.purchasePrice }
            if (!conflict && appTarget > 0 && !samePrice(pa, pr)) {
                val ps = snap?.get(key)?.price
                val fromArch = when {
                    s == null -> (pa == null) || ((appEmpty || choice == Choice.ARCHIDEKT) && pr != null)
                    a == 0 -> true
                    else -> samePrice(pa, ps) && !samePrice(pr, ps)
                }
                if (fromArch) { price = pr; setApp = true } else { price = pa; setArch = archTarget > 0 }
            }
            KeyChange(key, label, a, r, s, appTarget, archTarget, conflict, price, setApp, setArch)
        }

        // An Archidekt entry whose printing, finish, condition or language was edited shows up as
        // copies leaving one key and arriving at another: link the two, so the copies keep their binders.
        val withMoves = if (snap == null) out else {
            val entryWasAt = HashMap<Long, CardKey>()
            for (e in snap.values) for (id in e.entryIds) entryWasAt[id] = e.key
            val leaving = out.filter { it.appDelta < 0 }.associateBy { it.key }
            out.map { c ->
                if (c.appDelta <= 0) return@map c
                val from = arch[c.key].orEmpty().firstNotNullOfOrNull { e -> entryWasAt[e.id]?.takeIf { it != c.key && it in leaving } }
                if (from != null) c.copy(movedFrom = from) else c
            }
        }

        val appRemoved = withMoves.sumOf { maxOf(0, -it.appDelta) }
        val archRemoved = withMoves.sumOf { maxOf(0, -it.archDelta) }
        // Copies that only change condition, language or finish (same printing) aren't really removed.
        fun reallyRemoved(delta: (KeyChange) -> Int) = withMoves.groupBy { it.key.scryfallId }.values.sumOf { g -> maxOf(0, -g.sumOf(delta)) }
        val tooMany = reallyRemoved { it.appDelta } > REMOVAL_LIMIT || reallyRemoved { it.archDelta } > REMOVAL_LIMIT
        val approved = approvedRemovals != null && appRemoved <= approvedRemovals.first && archRemoved <= approvedRemovals.second
        // The first sync with one empty side is a copy the user asked for; otherwise big removals wait.
        val oneSided = first && (appEmpty || archEmpty)
        return Plan(first, withMoves, needsReview = tooMany && !approved && !oneSided && !(first && decisions.isNotEmpty()), decided = decisions.isNotEmpty())
    }

    /** The agreement after a sync: every card both sides now hold the same, plus the old agreement for cards still in conflict or that failed. */
    fun newSnapshot(
        plan: Plan,
        archAfter: Map<CardKey, List<ArchidektEntry>>,
        failed: Set<CardKey>,
        old: Map<CardKey, SnapEntry>?,
        appPrices: Map<CardKey, Double?>,
    ): List<SnapEntry> = plan.all.mapNotNull { c ->
        if (c.conflict || c.key in failed) return@mapNotNull old?.get(c.key)
        val qty = c.appTarget
        if (qty <= 0) return@mapNotNull null
        val price = if (c.setAppPrice || c.setArchPrice) c.price else appPrices[c.key] ?: archAfter[c.key]?.firstNotNullOfOrNull { it.purchasePrice }
        SnapEntry(c.key, qty, price, archAfter[c.key].orEmpty().map { it.id }, c.label)
    }
}
