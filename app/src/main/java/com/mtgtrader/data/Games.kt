package com.mtgtrader.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Someone who plays Commander games with the owner (since 1.47): a friend, or the owner ([me]). Synced, so both
 * phones know the same players.
 */
@Serializable
@Entity(tableName = "players", indices = [Index("uid")])
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The owner of the app. */
    val me: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Sync identity and last change; filled in by database triggers (see [SyncSchema]). */
    val uid: String? = null,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

/**
 * A friend's deck (since 1.47): a name and a commander (or two partners) found on Scryfall, and optionally its
 * Archidekt link. The owner's own decks are the app's decks.
 */
@Serializable
@Entity(tableName = "player_decks", indices = [Index("uid")])
data class FriendDeck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The [Player.uid] of the friend it belongs to. */
    val playerUid: String,
    val name: String,
    val commander: String,
    val scryfallId: String? = null,
    val partner: String? = null,
    val partnerScryfallId: String? = null,
    /** WUBRG order, e.g. "BRG". */
    val colorIdentity: String = "",
    val archidektUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val uid: String? = null,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

/**
 * A finished (or added by hand) game (since 1.47). Its seats and turn times are kept as JSON ([seatList],
 * [turnList]), so a game syncs as one item.
 */
@Serializable
@Entity(tableName = "games", indices = [Index("uid")])
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    /** Playing time without pauses. */
    val durationMs: Long,
    val startingLife: Int = 40,
    /** Whether turns were counted ([rounds] and the turn times are known). */
    val turnsOn: Boolean = false,
    /** Rounds played (the round the game ended in), with turns on. */
    val rounds: Int? = null,
    /** How the game was won ([WinType.key]), when told. */
    val winType: String? = null,
    val note: String? = null,
    /** Nobody won. */
    val draw: Boolean = false,
    /** Added by hand afterwards, not played with the tracker. */
    val manual: Boolean = false,
    /** [GameSeat] JSON. */
    val seats: String,
    /** [TurnTime] JSON, with turns on. */
    val turns: String? = null,
    val uid: String? = null,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
) {
    val seatList: List<GameSeat> get() = Games.decodeSeats(seats)
    val turnList: List<TurnTime> get() = Games.decodeTurns(turns)
    val winner: GameSeat? get() = if (draw) null else seatList.firstOrNull { it.place == 1 }
}

/** One player of a game: who, with which deck (and its scores then), and how it went. */
@Serializable
data class GameSeat(
    /** 0 = the first seat, clockwise from there. */
    val seat: Int,
    val playerUid: String? = null,
    val name: String,
    val me: Boolean = false,
    /** The app's deck ([Deck.archidektId]) the owner played. */
    val deckId: Long? = null,
    /** The [FriendDeck.uid] a friend played. */
    val friendDeckUid: String? = null,
    val deckName: String = "",
    val commander: String = "",
    val scryfallId: String? = null,
    val colorIdentity: String = "",
    /** The deck's bracket and power level when the game was played (Commander Salt). */
    val bracket: Int? = null,
    val power: Double? = null,
    /** This player took the first turn. */
    val started: Boolean = false,
    /** 1 = won; null for a draw or when not known. */
    val place: Int? = null,
    /** The round they went out in, with turns on. */
    val outRound: Int? = null,
    /** How they went out ([OutReason.key]). */
    val outReason: String? = null,
    val finalLife: Int? = null,
)

@Serializable
data class TurnTime(val round: Int, val seat: Int, val ms: Long)

/**
 * A game paused to be continued later, here or on another phone (since 1.51): the [LiveGame] as JSON, synced, so
 * every phone's Games tab lists it until one of them continues it (which takes it off the list everywhere).
 */
@Serializable
@Entity(tableName = "paused_games", indices = [Index("uid")])
data class PausedGame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pausedAt: Long,
    /** [LiveGame] JSON, its clock stopped at [pausedAt]. */
    val game: String,
    /** The clock had been stopped by hand before the game was paused, so it stays stopped when the game goes on. */
    val clockStopped: Boolean = false,
    val uid: String? = null,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
) {
    val live: LiveGame? get() = LiveGames.decode(game)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM players ORDER BY me DESC, name COLLATE NOCASE")
    fun observePlayers(): Flow<List<Player>>

    @Query("SELECT * FROM players")
    suspend fun players(): List<Player>

    @Insert
    suspend fun insertPlayer(p: Player): Long

    @Update
    suspend fun updatePlayer(p: Player)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deletePlayer(id: Long)

    @Query("SELECT * FROM player_decks ORDER BY name COLLATE NOCASE")
    fun observeFriendDecks(): Flow<List<FriendDeck>>

    @Query("SELECT * FROM player_decks")
    suspend fun friendDecks(): List<FriendDeck>

    @Insert
    suspend fun insertFriendDeck(d: FriendDeck): Long

    @Update
    suspend fun updateFriendDeck(d: FriendDeck)

    @Query("DELETE FROM player_decks WHERE id = :id")
    suspend fun deleteFriendDeck(id: Long)

    @Query("SELECT * FROM games ORDER BY startedAt DESC")
    fun observeGames(): Flow<List<GameRecord>>

    @Query("SELECT * FROM games")
    suspend fun games(): List<GameRecord>

    @Query("SELECT * FROM games WHERE uid = :uid")
    suspend fun game(uid: String): GameRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(g: GameRecord): Long

    @Update
    suspend fun updateGame(g: GameRecord)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteGame(id: Long)

    @Query("SELECT * FROM paused_games ORDER BY pausedAt DESC")
    fun observePausedGames(): Flow<List<PausedGame>>

    @Insert
    suspend fun insertPausedGame(p: PausedGame): Long

    @Update
    suspend fun updatePausedGame(p: PausedGame)

    @Query("DELETE FROM paused_games WHERE id = :id")
    suspend fun deletePausedGame(id: Long)
}

/** How a game was won (told at its end, optional). */
enum class WinType(val key: String, val label: String) {
    COMBAT("combat", "Combat"),
    COMMANDER("commander", "Commander damage"),
    COMBO("combo", "Combo"),
    POISON("poison", "Poison"),
    ALTERNATIVE("alt", "Alternative win"),
    CONCEDED("conceded", "Everyone conceded");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k }
    }
}

/** Why a player went out: set by the tracker from what took them out, or picked by hand. */
enum class OutReason(val key: String, val label: String) {
    LIFE("life", "life"),
    COMMANDER("commander", "commander damage"),
    POISON("poison", "poison"),
    CONCEDED("conceded", "conceded"),
    OTHER("other", "other");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k }
    }
}

object Games {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val seatsSerializer = ListSerializer(GameSeat.serializer())
    private val turnsSerializer = ListSerializer(TurnTime.serializer())

    fun encodeSeats(s: List<GameSeat>): String = json.encodeToString(seatsSerializer, s)
    fun decodeSeats(s: String?): List<GameSeat> = s?.let { runCatching { json.decodeFromString(seatsSerializer, it) }.getOrNull() }.orEmpty()
    fun encodeTurns(t: List<TurnTime>): String? = t.takeIf { it.isNotEmpty() }?.let { json.encodeToString(turnsSerializer, it) }
    fun decodeTurns(s: String?): List<TurnTime> = s?.let { runCatching { json.decodeFromString(turnsSerializer, it) }.getOrNull() }.orEmpty()

    /** "1 h 14 min", "52 min", "45 s". */
    fun duration(ms: Long): String {
        val min = ms / 60_000
        return when {
            min >= 60 -> "${min / 60} h ${"%02d".format(min % 60)} min"
            min >= 1 -> "$min min"
            else -> "${ms / 1000} s"
        }
    }

    /** "1:42" or "1:02:05", for clocks. */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
