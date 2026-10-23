package com.mtgtrader.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A deck's results in the recorded games (since 1.47). */
data class DeckResult(
    val key: String,
    val name: String,
    val commander: String,
    val scryfallId: String?,
    val colorIdentity: String,
    /** The owner's own deck (one of the app's decks). */
    val mine: Boolean,
    val deckId: Long?,
    val games: Int,
    val wins: Int,
    val avgMs: Long,
    val avgWinMs: Long?,
    /** Average rounds of its games played with turns on. */
    val avgRounds: Double?,
    /** The round it usually wins in (the median), from games with turns on. */
    val usualWinRound: Int?,
    val avgPlace: Double?,
    /** How its wins came, most first. */
    val winTypes: List<Pair<WinType, Int>>,
    val startedGames: Int,
    val startedWins: Int,
    /** Its bracket and power level in its latest game. */
    val bracket: Int?,
    val power: Double?,
    /** The decks it played against, on average. */
    val oppBracket: Double?,
    val oppPower: Double?,
    val lastPlayed: Long,
) {
    /** The seats where no deck and no commander was picked, together (since 1.58 called "Anonymous"). */
    val anonymous get() = key == GameStats.ANONYMOUS_KEY
    val losses get() = games - wins
    val winRate get() = if (games == 0) 0.0 else wins.toDouble() / games
}

/** A player's results. */
data class PlayerResult(val key: String, val name: String, val me: Boolean, val games: Int, val wins: Int, val avgPlace: Double?, val startedGames: Int, val startedWins: Int) {
    val winRate get() = if (games == 0) 0.0 else wins.toDouble() / games
}

/** The owner against one other player: in games both played, who finished ahead. */
data class HeadToHead(val key: String, val name: String, val games: Int, val ahead: Int, val behind: Int)

/** How the decks table can be sorted. */
enum class DeckOrder(val label: String) {
    GAMES("Most played"),
    WIN_RATE("Win rate"),
    WINS("Wins"),
    LENGTH("Longest games"),
    PLACE("Average place"),
    RECENT("Last played"),
}

object GameStats {
    /** The key of the seats without a deck or commander ([deckKey]). */
    const val ANONYMOUS_KEY = "n:|"

    /** One key per deck: the app's deck, a friend's deck, or else its commander and name (a guest's commander, since 1.58). */
    fun deckKey(s: GameSeat): String = s.deckId?.let { "d:$it" } ?: s.friendDeckUid?.let { "f:$it" } ?: "n:${s.commander.lowercase()}|${s.deckName.lowercase()}"

    /** One key per player: their sync id, or else their name. */
    fun playerKey(s: GameSeat): String = s.playerUid ?: "n:${s.name.trim().lowercase()}"

    private fun won(g: GameRecord, s: GameSeat) = !g.draw && s.place == 1

    fun decks(games: List<GameRecord>): List<DeckResult> {
        val rows = games.flatMap { g -> g.seatList.map { g to it } }
        return rows.groupBy { deckKey(it.second) }.map { (key, list) ->
            val latest = list.maxBy { it.first.startedAt }
            val wins = list.filter { (g, s) -> won(g, s) }
            val withTurns = list.filter { it.first.turnsOn && it.first.rounds != null }
            val winRounds = wins.mapNotNull { (g, _) -> g.rounds.takeIf { g.turnsOn } }.sorted()
            val opponents = list.flatMap { (g, s) -> g.seatList.filter { it.seat != s.seat } }
            val places = list.mapNotNull { it.second.place }
            DeckResult(
                key = key,
                name = latest.second.deckName.ifBlank { latest.second.commander }.ifBlank { "Anonymous" },
                commander = latest.second.commander,
                scryfallId = latest.second.scryfallId,
                colorIdentity = latest.second.colorIdentity,
                mine = latest.second.me,
                deckId = latest.second.deckId,
                games = list.size,
                wins = wins.size,
                avgMs = list.map { it.first.durationMs }.average().toLong(),
                avgWinMs = wins.takeIf { it.isNotEmpty() }?.map { it.first.durationMs }?.average()?.toLong(),
                avgRounds = withTurns.takeIf { it.isNotEmpty() }?.map { it.first.rounds!!.toDouble() }?.average(),
                usualWinRound = winRounds.takeIf { it.isNotEmpty() }?.get((winRounds.size - 1) / 2),
                avgPlace = places.takeIf { it.isNotEmpty() }?.average(),
                winTypes = wins.mapNotNull { WinType.fromKey(it.first.winType) }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second },
                startedGames = list.count { it.second.started },
                startedWins = wins.count { it.second.started },
                bracket = latest.second.bracket,
                power = latest.second.power,
                oppBracket = opponents.mapNotNull { it.bracket?.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
                oppPower = opponents.mapNotNull { it.power }.takeIf { it.isNotEmpty() }?.average(),
                lastPlayed = latest.first.startedAt,
            )
        }
    }

    fun sortDecks(decks: List<DeckResult>, order: DeckOrder): List<DeckResult> = when (order) {
        DeckOrder.GAMES -> decks.sortedWith(compareByDescending<DeckResult> { it.games }.thenByDescending { it.winRate })
        DeckOrder.WIN_RATE -> decks.sortedWith(compareByDescending<DeckResult> { it.winRate }.thenByDescending { it.games })
        DeckOrder.WINS -> decks.sortedWith(compareByDescending<DeckResult> { it.wins }.thenByDescending { it.winRate })
        DeckOrder.LENGTH -> decks.sortedByDescending { it.avgMs }
        DeckOrder.PLACE -> decks.sortedBy { it.avgPlace ?: Double.MAX_VALUE }
        DeckOrder.RECENT -> decks.sortedByDescending { it.lastPlayed }
    }

    fun players(games: List<GameRecord>): List<PlayerResult> {
        val rows = games.flatMap { g -> g.seatList.map { g to it } }
        return rows.groupBy { playerKey(it.second) }.map { (key, list) ->
            val latest = list.maxBy { it.first.startedAt }.second
            val wins = list.filter { (g, s) -> won(g, s) }
            PlayerResult(
                key = key,
                name = latest.name,
                me = latest.me,
                games = list.size,
                wins = wins.size,
                avgPlace = list.mapNotNull { it.second.place }.takeIf { it.isNotEmpty() }?.average(),
                startedGames = list.count { it.second.started },
                startedWins = wins.count { it.second.started },
            )
        }.sortedWith(compareByDescending<PlayerResult> { it.me }.thenByDescending { it.games })
    }

    /** The owner against each other player: who finished ahead (a draw or unknown places count for neither). */
    fun headToHead(games: List<GameRecord>): List<HeadToHead> {
        val out = HashMap<String, HeadToHead>()
        for (g in games) {
            val seats = g.seatList
            val me = seats.firstOrNull { it.me } ?: continue
            for (o in seats) {
                if (o.me) continue
                val key = playerKey(o)
                val was = out[key] ?: HeadToHead(key, o.name, 0, 0, 0)
                val mine = me.place ?: Int.MAX_VALUE
                val theirs = o.place ?: Int.MAX_VALUE
                out[key] = was.copy(
                    name = o.name,
                    games = was.games + 1,
                    ahead = was.ahead + if (mine < theirs) 1 else 0,
                    behind = was.behind + if (theirs < mine) 1 else 0,
                )
            }
        }
        return out.values.sortedByDescending { it.games }
    }

    /** How often the owner won, of the games they played. */
    fun myWinRate(games: List<GameRecord>): Double? {
        val mine = games.mapNotNull { g -> g.seatList.firstOrNull { it.me }?.let { g to it } }
        if (mine.isEmpty()) return null
        return mine.count { (g, s) -> won(g, s) }.toDouble() / mine.size
    }

    /** The games a deck of the app took part in, newest first. */
    fun gamesOfDeck(games: List<GameRecord>, deckId: Long): List<GameRecord> =
        games.filter { g -> g.seatList.any { it.deckId == deckId } }.sortedByDescending { it.startedAt }

    /** The game history as a spreadsheet (CSV): one line per player per game. */
    fun csv(games: List<GameRecord>): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        fun q(s: String?) = s.orEmpty().let { if (it.any { c -> c == ',' || c == '"' || c == '\n' }) "\"" + it.replace("\"", "\"\"") + "\"" else it }
        val head = "Date,Game,Minutes,Rounds,Player,Deck,Commander,Place,Won,Started,Out in round,Out by,Final life,Bracket,Power level,Win by,Note"
        val lines = games.sortedBy { it.startedAt }.flatMap { g ->
            g.seatList.sortedBy { it.place ?: Int.MAX_VALUE }.map { s ->
                listOf(
                    date.format(Date(g.startedAt)), g.uid.orEmpty().take(8), (g.durationMs / 60_000).toString(), g.rounds?.toString().orEmpty(),
                    q(s.name), q(s.deckName), q(s.commander), s.place?.toString().orEmpty(), if (won(g, s)) "yes" else "no",
                    if (s.started) "yes" else "no", s.outRound?.toString().orEmpty(), OutReason.fromKey(s.outReason)?.label.orEmpty(),
                    s.finalLife?.toString().orEmpty(), s.bracket?.toString().orEmpty(), s.power?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                    WinType.fromKey(g.winType)?.label.orEmpty(), q(g.note),
                ).joinToString(",")
            }
        }
        return (listOf(head) + lines).joinToString("\n") + "\n"
    }
}
