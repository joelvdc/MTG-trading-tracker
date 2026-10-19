package com.mtgtrader.data

import android.content.Context
import androidx.room.withTransaction
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** Who sits where at the table, and the game's options: the New game screen's choices, kept for a rematch. Since 1.47. */
@Serializable
data class TableSetup(
    val seats: List<SeatChoice> = emptyList(),
    val startingLife: Int = 40,
    val commanderDamage: Boolean = true,
    val turnsOn: Boolean = false,
    val clock: Boolean = true,
    /** [LiveGames.EXTRA_COUNTERS] keys, table items and "custom:Name"s switched on. */
    val counters: List<String> = emptyList(),
    /** Who starts: a seat, or null for a random pick. */
    val firstSeat: Int? = null,
    /** The game's own settings (since 1.48; see [LiveGame]). */
    val cmdLifeLoss: Boolean = true,
    val whenDown: String = WhenDown.ASK.key,
    val ends: Boolean = false,
    val logOn: Boolean = true,
)

/** A seat's player and deck in the setup. */
@Serializable
data class SeatChoice(
    val playerUid: String? = null,
    val name: String = "",
    val me: Boolean = false,
    val deckId: Long? = null,
    val friendDeckUid: String? = null,
    /** A picked random background, kept for the game. */
    val artUrl: String? = null,
    val colour: Int? = null,
)

/** A random commander's art for a panel without a commander of its own. */
@Serializable
data class CommanderArt(val name: String, val url: String)

/**
 * The life tracker's records (since 1.47): the game being played (kept on this phone, with undo), paused games,
 * players, friends' decks and finished games (in the database, synced), the last table for a rematch, and a small
 * store of random commanders' art for backgrounds when offline.
 */
class GameStore(
    private val context: Context,
    private val db: AppDatabase,
    private val scryfall: ScryfallApi,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("games", Context.MODE_PRIVATE)
    private val dao = db.gameDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val players: Flow<List<Player>> = dao.observePlayers()
    val friendDecks: Flow<List<FriendDeck>> = dao.observeFriendDecks()
    val games: Flow<List<GameRecord>> = dao.observeGames()

    // ---- The game being played ---------------------------------------------------------------

    private val _live = MutableStateFlow(LiveGames.decode(prefs.getString(K_LIVE, null)))
    val live: StateFlow<LiveGame?> = _live
    private val undo = ArrayDeque<LiveGame>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private fun save(g: LiveGame?) {
        _live.value = g
        prefs.edit().putString(K_LIVE, g?.let(LiveGames::encode)).apply()
    }

    /**
     * Changes the game; [undoable] changes can be taken back (the last [UNDO_STEPS]). Since 1.48 every change is
     * settled too ([LiveGames.settle]): written to the log, and players who are down go out when the game says so.
     */
    fun update(undoable: Boolean = true, change: (LiveGame) -> LiveGame) {
        val g = _live.value ?: return
        val next = LiveGames.settle(g, change(g), System.currentTimeMillis())
        if (next == g) return
        if (undoable) {
            undo.addLast(g)
            while (undo.size > UNDO_STEPS) undo.removeFirst()
            _canUndo.value = true
        }
        save(next)
    }

    fun undo(): Boolean {
        val prev = undo.removeLastOrNull() ?: return false
        _canUndo.value = undo.isNotEmpty()
        // The clock keeps running: only the players' state goes back.
        val now = _live.value
        save(if (now == null) prev else prev.copy(pausedAt = now.pausedAt, pausedMs = now.pausedMs))
        return true
    }

    fun start(game: LiveGame, setup: TableSetup) {
        undo.clear()
        _canUndo.value = false
        save(game)
        lastTable = setup
    }

    /** Saves the game being played as a finished game and clears it. */
    suspend fun finish(record: GameRecord): GameRecord {
        val id = dao.insertGame(record)
        undo.clear()
        _canUndo.value = false
        save(null)
        return record.copy(id = id)
    }

    /** Drops the game being played without saving it. */
    fun abandon() {
        undo.clear()
        _canUndo.value = false
        save(null)
    }

    // ---- Paused games (since 1.51) -----------------------------------------------------------------

    /** Games paused to be continued later, here or on another phone (synced), the last paused first. */
    val pausedGames: Flow<List<PausedGame>> = dao.observePausedGames()

    /** Stops the game being played and its clock, and puts it with the paused games; the tracker then closes. */
    suspend fun pauseAndLeave() {
        val g = _live.value ?: return
        val now = System.currentTimeMillis()
        dao.insertPausedGame(PausedGame(pausedAt = now, game = LiveGames.encode(LiveGames.pause(g, now)), clockStopped = g.paused, uid = g.uid))
        abandon()
    }

    /**
     * Makes [p] the game being played again, its clock going on (unless it had been stopped by hand), and takes it
     * off the paused games, on the other phones too once they sync. False if the game can't be read.
     */
    suspend fun continuePaused(p: PausedGame): Boolean {
        val g = p.live ?: return false
        dao.deletePausedGame(p.id)
        undo.clear()
        _canUndo.value = false
        save(if (p.clockStopped) g else LiveGames.resume(g, System.currentTimeMillis()))
        return true
    }

    /** Drops a paused game without saving it (on every phone). */
    suspend fun dropPaused(p: PausedGame): UndoAction {
        dao.deletePausedGame(p.id)
        return { dao.insertPausedGame(p.copy(id = 0)) }
    }

    /** Keeps [setup] as the table a rematch starts from (also after players or decks change during a game). */
    fun keepTable(setup: TableSetup) {
        lastTable = setup
    }

    var lastTable: TableSetup?
        get() = prefs.getString(K_TABLE, null)?.let { runCatching { json.decodeFromString(TableSetup.serializer(), it) }.getOrNull() }
        private set(v) = prefs.edit().putString(K_TABLE, v?.let { json.encodeToString(TableSetup.serializer(), it) }).apply()

    // ---- Players and friends' decks ------------------------------------------------------------

    /** The owner as a player, made when first needed ("Me", renamable). */
    suspend fun me(): Player {
        dao.players().firstOrNull { it.me }?.let { return it }
        val id = dao.insertPlayer(Player(name = "Me", me = true))
        return dao.players().first { it.id == id }
    }

    suspend fun addPlayer(name: String): Player {
        val id = dao.insertPlayer(Player(name = name.trim()))
        return dao.players().first { it.id == id }
    }

    suspend fun renamePlayer(p: Player, name: String) = dao.updatePlayer(p.copy(name = name.trim()))

    /** Deletes a friend and their decks (their past games keep their names). */
    suspend fun deletePlayer(p: Player): UndoAction {
        val decks = dao.friendDecks().filter { it.playerUid == p.uid }
        db.withTransaction {
            decks.forEach { dao.deleteFriendDeck(it.id) }
            dao.deletePlayer(p.id)
        }
        return {
            db.withTransaction {
                dao.insertPlayer(p.copy(id = 0))
                decks.forEach { dao.insertFriendDeck(it.copy(id = 0)) }
            }
        }
    }

    suspend fun saveFriendDeck(d: FriendDeck): FriendDeck {
        if (d.id != 0L) {
            dao.updateFriendDeck(d)
            return d
        }
        val id = dao.insertFriendDeck(d)
        return dao.friendDecks().first { it.id == id }
    }

    suspend fun deleteFriendDeck(d: FriendDeck): UndoAction {
        dao.deleteFriendDeck(d.id)
        return { dao.insertFriendDeck(d.copy(id = 0)) }
    }

    /** A friend's deck from its commander's name, looked up on Scryfall (with its partner, if given). */
    suspend fun friendDeckFor(playerUid: String, name: String, commander: String, partner: String?, archidektUrl: String?): FriendDeck {
        val main = scryfall.fuzzy(commander)
        val second = partner?.takeIf { it.isNotBlank() }?.let { scryfall.fuzzy(it) }
        val colours = (main?.colorIdentity.orEmpty() + second?.colorIdentity.orEmpty()).toSet()
        return FriendDeck(
            playerUid = playerUid,
            name = name.ifBlank { main?.name ?: commander },
            commander = main?.name ?: commander.trim(),
            scryfallId = main?.id,
            partner = second?.name ?: partner?.takeIf { it.isNotBlank() }?.trim(),
            partnerScryfallId = second?.id,
            colorIdentity = MtgColors.ORDER.filter { it.toString() in colours },
            archidektUrl = archidektUrl?.takeIf { it.isNotBlank() }?.trim(),
        )
    }

    // ---- Finished games --------------------------------------------------------------------------

    /** Saves a changed or added game (a new one gets an id of its own). */
    suspend fun saveGame(g: GameRecord) {
        if (g.id != 0L) dao.updateGame(g) else dao.insertGame(g.copy(uid = g.uid ?: UUID.randomUUID().toString()))
    }

    suspend fun deleteGame(g: GameRecord): UndoAction {
        dao.deleteGame(g.id)
        return { dao.insertGame(g.copy(id = 0)) }
    }

    suspend fun game(uid: String): GameRecord? = dao.game(uid)

    // ---- Random commanders' art ----------------------------------------------------------------

    private val artLock = Mutex()
    private val artSerializer = ListSerializer(CommanderArt.serializer())

    /** The store of random commanders' art (about [ART_STORE] of them), kept on the phone for when it's offline. */
    fun storedArt(): List<CommanderArt> =
        prefs.getString(K_ART, null)?.let { runCatching { json.decodeFromString(artSerializer, it) }.getOrNull() }.orEmpty()

    /**
     * Tops the store up to [ART_STORE] from Scryfall (`/cards/random?q=is:commander`), and every week swaps a few for
     * new ones; their pictures are downloaded now so they show offline.
     */
    fun refreshArt() {
        scope.launch {
            artLock.withLock {
                var list = storedArt()
                val stale = System.currentTimeMillis() - prefs.getLong(K_ART_AT, 0) > ART_REFRESH_MS
                if (stale && list.size >= ART_STORE) list = list.drop(ART_SWAP)
                if (list.size >= ART_STORE) return@withLock
                val got = list.toMutableList()
                var tries = 0
                while (got.size < ART_STORE && tries < ART_STORE * 2) {
                    tries++
                    val card = runCatching { scryfall.random("is:commander") }.getOrNull() ?: break
                    val url = card.imageUris?.artCrop ?: card.cardFaces?.firstOrNull()?.imageUris?.artCrop ?: continue
                    if (got.any { it.name == card.name }) continue
                    got += CommanderArt(card.name, url)
                    context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
                    delay(150)
                }
                prefs.edit().putString(K_ART, json.encodeToString(artSerializer, got)).putLong(K_ART_AT, System.currentTimeMillis()).apply()
            }
        }
    }

    /** [count] random arts, none of them in [except]. */
    fun randomArt(count: Int, except: Set<String> = emptySet()): List<CommanderArt> =
        storedArt().filter { it.url !in except }.shuffled().take(count)

    companion object {
        private const val K_LIVE = "live"
        private const val K_TABLE = "lastTable"
        private const val K_ART = "commanderArt"
        private const val K_ART_AT = "commanderArtAt"
        const val UNDO_STEPS = 50
        const val ART_STORE = 20
        private const val ART_SWAP = 5
        private const val ART_REFRESH_MS = 7L * 24 * 60 * 60 * 1000

        /** A commander's art crop on Scryfall's image server, from its card id. */
        fun artCrop(scryfallId: String?): String? =
            scryfallId?.takeIf { it.length > 2 }?.let { id -> "https://cards.scryfall.io/art_crop/front/${id[0]}/${id[1]}/$id.jpg" }
    }
}
