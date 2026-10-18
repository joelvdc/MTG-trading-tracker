package com.mtgtrader.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One of a deck's categories on Archidekt: whether it counts in the deck (the maybeboard doesn't). Since 1.46. */
@Serializable
data class ArchidektCategory(val name: String, val included: Boolean = true, val id: Long? = null)

/**
 * A change to one line of an Archidekt deck that this phone made and hasn't sent to Archidekt yet (since 1.46).
 * It names the line by its [DeckCard.lineUid]; what's sent is the line as it is when sending (so several edits to
 * one card go as one), or its removal when the line is gone. [relationId] and [archidektCardId] are the line's on
 * Archidekt when the edit was made, needed to remove it. Kept on this phone only: the other phone gets the
 * changed deck through the sync and leaves sending to this one.
 */
@Entity(tableName = "deck_edits", indices = [Index(value = ["deckId", "lineUid"], unique = true)])
data class DeckEdit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val lineUid: String,
    val relationId: Long?,
    val archidektCardId: Long?,
    /** The card's name, for reporting an edit Archidekt refused. */
    val name: String,
    /** The line's categories on Archidekt when the edit was made (one per line), sent with a removal. */
    val categories: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** How many edits a deck has waiting. */
data class DeckEditCount(val deckId: Long, val count: Int)

@Dao
interface DeckEditDao {
    @Query("SELECT * FROM deck_edits WHERE deckId = :deckId ORDER BY createdAt, id")
    suspend fun forDeck(deckId: Long): List<DeckEdit>

    @Query("SELECT deckId, COUNT(*) AS count FROM deck_edits GROUP BY deckId")
    fun observeCounts(): Flow<List<DeckEditCount>>

    @Query("SELECT DISTINCT deckId FROM deck_edits")
    suspend fun deckIds(): List<Long>

    @Query("SELECT COUNT(*) FROM deck_edits WHERE deckId = :deckId")
    suspend fun count(deckId: Long): Int

    /** Keeps the first edit of a line (its [DeckEdit.relationId] is the one Archidekt knows). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(edits: List<DeckEdit>)

    @Query("DELETE FROM deck_edits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM deck_edits WHERE deckId = :deckId")
    suspend fun clear(deckId: Long)

    @Query("UPDATE deck_edits SET deckId = :to WHERE deckId = :from")
    suspend fun moveDeck(from: Long, to: Long)
}

/** Helpers for a deck's categories and the edits made in the app. Since 1.46. */
object DeckEdits {
    const val MAYBEBOARD = "Maybeboard"
    const val COMMANDER = "Commander"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val listSerializer = ListSerializer(ArchidektCategory.serializer())

    fun categories(info: String?): List<ArchidektCategory> =
        info?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()

    fun encode(categories: List<ArchidektCategory>): String? = categories.takeIf { it.isNotEmpty() }?.let { json.encodeToString(listSerializer, it) }

    /** Whether a card whose first category is [category] counts in the deck (Archidekt's maybeboard and others left out don't). */
    fun included(category: String, info: List<ArchidektCategory>): Boolean =
        !category.equals(MAYBEBOARD, true) && info.firstOrNull { it.name.equals(category, true) }?.included != false

    /** A new local deck's id: below zero (Archidekt's are above), and different on every phone. */
    fun newLocalId(now: Long = System.currentTimeMillis()): Long = -now
}
