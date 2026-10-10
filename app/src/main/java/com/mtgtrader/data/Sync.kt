package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Everything that syncs between phones (not prices or images, which each phone fetches itself),
 * in a form that doesn't depend on either phone's database: rows have no local ids, binders are
 * referred to by their sync id (null = Unsorted), and lists are sorted, so two copies with the
 * same content are equal.
 */
@Serializable
data class SyncData(
    val binders: List<Binder> = emptyList(),
    val collection: List<SyncStack> = emptyList(),
    val trades: List<SyncTrade> = emptyList(),
    val decks: List<SyncDeck> = emptyList(),
    val scans: List<ScannedCard> = emptyList(),
    /** Since 1.16; older versions leave it out (and don't delete anything from it). */
    val wishlist: List<WishlistItem> = emptyList(),
    val deletions: List<SyncDeletion> = emptyList(),
    val prefs: SyncPrefs = SyncPrefs(),
) {
    val isEmpty get() = binders.isEmpty() && collection.isEmpty() && trades.isEmpty() && decks.isEmpty() && scans.isEmpty()

    /** Sorted into the canonical order (see [SyncData]). */
    fun sorted() = copy(
        binders = binders.sortedBy { it.uid },
        collection = collection.sortedBy { it.item.uid },
        trades = trades.map { it.sorted() }.sortedBy { it.trade.uid },
        decks = decks.map { it.sorted() }.sortedBy { it.deck.archidektId },
        scans = scans.sortedBy { it.uid },
        wishlist = wishlist.sortedBy { it.uid },
        deletions = deletions.sortedBy { it.uid },
    )
}

/** A collection stack and the binder it's in. */
@Serializable
data class SyncStack(val item: CollectionItem, val binder: String? = null)

@Serializable
data class SyncTrade(val trade: Trade, val binder: String? = null, val items: List<SyncTradeItem> = emptyList()) {
    fun sorted() = copy(items = items.sortedWith(compareBy({ it.item.side }, { it.item.addedAt }, { it.item.card.scryfallId }, { it.item.foil }, { it.item.etched }, { it.item.condition }, { it.item.language }, { it.item.quantity })))
}

/** A trade line and, for given cards, the binder they came from. */
@Serializable
data class SyncTradeItem(val item: TradeItem, val appliedBinder: String? = null)

@Serializable
data class SyncDeck(val deck: Deck, val cards: List<DeckCard> = emptyList()) {
    fun sorted() = copy(cards = cards.sortedWith(compareBy({ it.card.scryfallId }, { it.foil }, { it.etched }, { it.addedInApp }, { it.category }, { it.quantity }, { it.commander })))
}

@Serializable
data class SyncPrefs(val updatedAt: Long = 0, val values: Map<String, String> = emptyMap())

/** The file kept on Nextcloud. */
@Serializable
data class SyncFile(
    val format: Int = FORMAT,
    val app: String = "MTG Trader",
    val writtenAt: Long = 0,
    val writtenBy: String = "",
    val data: SyncData = SyncData(),
) {
    companion object {
        /**
         * 2 since 1.27: signed and altered copies are stacks of their own, which older versions would
         * merge with the plain ones (losing the marks), so they refuse the file and ask to be updated.
         */
        const val FORMAT = 2
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * The phone that wrote [file], if it still runs a version from before [FORMAT] and isn't this
         * phone: the file isn't the one this phone wrote or read last ([lastEtag]), and it names another
         * device than [me]. Since 1.27.
         */
        fun olderWriter(file: SyncFile, etag: String?, lastEtag: String?, me: String): String? =
            if (file.format < FORMAT && etag != lastEtag && file.writtenBy != me) file.writtenBy.ifBlank { "Another phone" } else null

        fun encode(file: SyncFile): ByteArray {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(json.encodeToString(serializer(), file).toByteArray(Charsets.UTF_8)) }
            return out.toByteArray()
        }

        fun decode(bytes: ByteArray): SyncFile {
            val text = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }.toString(Charsets.UTF_8)
            val file = json.decodeFromString(serializer(), text)
            if (file.format > FORMAT) throw SyncException("The Nextcloud copy was written by a newer version of the app. Update the app on this phone.")
            return file
        }
    }
}

class SyncException(message: String) : Exception(message)

/**
 * Two phones' copies of a deck. The decklist (and the rest read from Archidekt with it) comes from
 * the copy loaded from the newer Archidekt version, so an old list can't come back just because the
 * other phone changed something on its copy later, like a score or a power level rating. Each score
 * (Commander Salt, edhpowerlevel.com, ScrollVault) comes from whichever copy got it last, as long
 * as it was made after that list was loaded; older ones belong to an older list.
 */
object DeckMerge {
    private val newerList = compareBy<SyncDeck>(
        { it.deck.archidektUpdatedAt ?: Long.MIN_VALUE },
        { it.deck.importedAt },
        // Same Archidekt version: the later change, e.g. cards added in the app.
        { it.deck.updatedAt },
    )

    /** [b] wins a complete tie. */
    fun merge(a: SyncDeck, b: SyncDeck): SyncDeck {
        val (list, other) = if (newerList.compare(b, a) >= 0) b to a else a to b
        val d = list.deck
        val o = other.deck
        val since = d.importedAt
        fun pick(ownAt: Long?, theirAt: Long?): Deck =
            if (theirAt != null && theirAt >= since && theirAt > (ownAt ?: Long.MIN_VALUE)) o else d
        val salt = pick(d.scoredAt, o.scoredAt)
        val edh = pick(d.edhPowerAt, o.edhPowerAt)
        val sv = pick(d.svAt, o.svAt)
        val merged = d.copy(
            saltId = salt.saltId,
            powerLevel = salt.powerLevel,
            bracketRealistic = salt.bracketRealistic,
            bracketBaseline = salt.bracketBaseline,
            saltPercent = salt.saltPercent,
            archetype = salt.archetype,
            scoredAt = salt.scoredAt,
            scoreError = salt.scoreError,
            saltCard = salt.saltCard,
            edhPowerLevel = edh.edhPowerLevel,
            edhPowerAt = edh.edhPowerAt,
            edhPowerError = edh.edhPowerError,
            svPowerLevel = sv.svPowerLevel,
            scrollVault = sv.scrollVault,
            svAt = sv.svAt,
            svError = sv.svError,
            updatedAt = maxOf(d.updatedAt, o.updatedAt),
        )
        return SyncDeck(merged, list.cards)
    }
}

/** What to do the first time this phone syncs and both it and Nextcloud already hold data. */
enum class FirstSync(val label: String, val explanation: String) {
    MERGE("Merge both", "Keep everything from this phone and from Nextcloud. The same card in the same binder is kept once."),
    USE_REMOTE("Use the Nextcloud copy", "Replace what's on this phone with what's on Nextcloud."),
    USE_LOCAL("Start from this phone", "Replace what's on Nextcloud (and so on your other phones) with what's on this phone."),
}

/**
 * Merges this phone's data with the copy on Nextcloud, item by item: the most recently changed
 * version of an item wins, and a deletion wins over changes made before it. Both phones arrive at
 * the same result, so syncing again changes nothing.
 */
object SyncMerge {
    /** Deletions are remembered this long; a phone that's been offline longer may bring items back. */
    const val KEEP_DELETIONS_MS = 180L * 24 * 60 * 60 * 1000

    fun deckKey(archidektId: Long) = "deck:$archidektId"

    fun merge(local: SyncData, remote: SyncData?, now: Long, first: FirstSync? = null): SyncData {
        if (remote == null) return clean(local, now)
        return when (first) {
            FirstSync.USE_REMOTE -> clean(remote, now)
            // Everything only on Nextcloud is deleted, so other phones drop it too.
            FirstSync.USE_LOCAL -> {
                val keep = uids(local)
                val gone = uids(remote).filter { it !in keep }.map { SyncDeletion(it, now) }
                clean(local.copy(deletions = local.deletions + gone), now)
            }
            FirstSync.MERGE, null -> clean(combine(local, remote), now)
        }
    }

    private fun uids(d: SyncData): Set<String> = buildSet {
        d.binders.forEach { b -> b.uid?.let(::add) }
        d.collection.forEach { s -> s.item.uid?.let(::add) }
        d.trades.forEach { t -> t.trade.uid?.let(::add) }
        d.scans.forEach { s -> s.uid?.let(::add) }
        d.wishlist.forEach { w -> w.uid?.let(::add) }
        d.decks.forEach { add(deckKey(it.deck.archidektId)) }
    }

    /** Union of both, keeping the newer version of items on both sides (Nextcloud's on a tie). */
    private fun combine(local: SyncData, remote: SyncData): SyncData {
        fun <T> newest(a: List<T>, b: List<T>, key: (T) -> String?, at: (T) -> Long): List<T> {
            val out = LinkedHashMap<String, T>()
            for (x in a) out[key(x) ?: continue] = x
            for (x in b) {
                val k = key(x) ?: continue
                val mine = out[k]
                if (mine == null || at(x) >= at(mine)) out[k] = x
            }
            return out.values.toList()
        }
        val deletions = (local.deletions + remote.deletions).groupBy { it.uid }.map { (_, d) -> d.maxBy { it.deletedAt } }
        return SyncData(
            binders = newest(local.binders, remote.binders, { it.uid }, { it.updatedAt }),
            collection = newest(local.collection, remote.collection, { it.item.uid }, { it.item.updatedAt }),
            trades = newest(local.trades, remote.trades, { it.trade.uid }, { it.trade.updatedAt }),
            decks = (local.decks + remote.decks).groupBy { it.deck.archidektId }.map { (_, copies) ->
                // Nextcloud's copy second, so it wins a complete tie like everywhere else.
                if (copies.size == 1) copies[0] else DeckMerge.merge(copies[0], copies[1])
            },
            scans = newest(local.scans, remote.scans, { it.uid }, { it.updatedAt }),
            wishlist = newest(local.wishlist, remote.wishlist, { it.uid }, { it.updatedAt }),
            deletions = deletions,
            prefs = if (remote.prefs.updatedAt >= local.prefs.updatedAt) remote.prefs else local.prefs,
        )
    }

    /**
     * Applies deletions, folds binders with the same name into one, keeps one stack per card and
     * binder, points references to missing binders at Unsorted and forgets old deletions.
     */
    private fun clean(d: SyncData, now: Long): SyncData {
        val deleted = d.deletions.associate { it.uid to it.deletedAt }.toMutableMap()
        val revived = mutableSetOf<String>()
        fun alive(uid: String?, updatedAt: Long): Boolean {
            if (uid == null) return false
            val at = deleted[uid] ?: return true
            return (updatedAt > at).also { if (it) revived += uid }
        }
        fun bury(uid: String?, updatedAt: Long) {
            if (uid == null) return
            deleted[uid] = maxOf(deleted[uid] ?: 0, now, updatedAt)
            revived -= uid
        }

        // Binders: one per name (the oldest), the others' cards move into it.
        val remap = HashMap<String, String>()
        val binders = d.binders.filter { alive(it.uid, it.updatedAt) }
            .groupBy { it.name.trim().lowercase() }
            .map { (_, same) ->
                val keep = same.minWith(compareBy<Binder>({ it.createdAt }, { it.uid }))
                for (b in same) if (b !== keep) { remap[b.uid!!] = keep.uid!!; bury(b.uid, b.updatedAt) }
                keep
            }
        val binderIds = binders.mapNotNull { it.uid }.toSet()
        fun binder(uid: String?): String? = uid?.let { remap[it] ?: it }?.takeIf { it in binderIds }

        // Collection: one stack per printing, finish, condition, language and binder (the newest).
        val collection = d.collection.filter { alive(it.item.uid, it.item.updatedAt) }
            .map { it.copy(binder = binder(it.binder)) }
            .groupBy { s -> listOf(s.item.card.scryfallId, s.item.foil, s.item.etched, s.item.condition, s.item.language, s.binder) }
            .map { (_, same) ->
                val keep = same.maxWith(compareBy<SyncStack>({ it.item.updatedAt }, { it.item.uid }))
                for (s in same) if (s !== keep) bury(s.item.uid, s.item.updatedAt)
                keep
            }

        val trades = d.trades.filter { alive(it.trade.uid, it.trade.updatedAt) }
            .map { t -> t.copy(binder = binder(t.binder), items = t.items.map { it.copy(appliedBinder = binder(it.appliedBinder)) }) }
        val decks = d.decks.filter { alive(deckKey(it.deck.archidektId), it.deck.updatedAt) }
        val scans = d.scans.filter { alive(it.uid, it.updatedAt) }
        val wishlist = d.wishlist.filter { alive(it.uid, it.updatedAt) }

        val cutoff = now - KEEP_DELETIONS_MS
        val deletions = deleted.filter { (uid, at) -> uid !in revived && at >= cutoff }.map { (uid, at) -> SyncDeletion(uid, at) }
        return SyncData(binders, collection, trades, decks, scans, wishlist, deletions, d.prefs).sorted()
    }
}
