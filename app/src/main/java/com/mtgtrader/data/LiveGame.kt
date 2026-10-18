package com.mtgtrader.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The animation on a panel at low life (since 1.47). */
enum class LowLifeStyle(val key: String, val label: String) {
    GHOST("ghost", "A ghost drifting up"),
    EDGE("edge", "A pulsing dark red edge"),
    CRACK("crack", "A cracking life total"),
    OFF("off", "None");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: GHOST
    }
}

/**
 * A player at the table during a game (since 1.47). [cmd] is the commander damage taken, by the seat (and, for
 * partners, "seat:2" for the second commander) it came from. [casts] counts casts from the command zone, for
 * the commander tax.
 */
@Serializable
data class LiveSeat(
    val seat: Int,
    val playerUid: String? = null,
    val name: String,
    val me: Boolean = false,
    val deckId: Long? = null,
    val friendDeckUid: String? = null,
    val deckName: String = "",
    val commander: String = "",
    val scryfallId: String? = null,
    /** A second commander (partners), whose damage counts on its own. */
    val partner: String? = null,
    val colorIdentity: String = "",
    val bracket: Int? = null,
    val power: Double? = null,
    /** The panel's background picture (the commander's art, or a random commander's). */
    val artUrl: String? = null,
    /** The panel's plain colour ([LiveGames.PALETTE] index). */
    val colour: Int = 0,
    val life: Int,
    val cmd: Map<String, Int> = emptyMap(),
    val poison: Int = 0,
    val casts: Int = 0,
    val partnerCasts: Int = 0,
    val counters: Map<String, Int> = emptyMap(),
    val out: Boolean = false,
    val outRound: Int? = null,
    val outReason: String? = null,
    /** When they went out (game time), to place players who went out in the same round. */
    val outAtMs: Long? = null,
    /** "No" to "Dead?": the state it was answered in ([LiveGames.deathKey]); asked again when that changes. */
    val notDead: String? = null,
) {
    /** The commander tax: {2} more for each earlier cast from the command zone (rule 903.8). */
    val tax: Int get() = casts * 2
    val partnerTax: Int get() = partnerCasts * 2
    val maxCommanderDamage: Int get() = cmd.values.maxOrNull() ?: 0
}

/** The game being played, kept on this phone (not synced) until it ends. Since 1.47. */
@Serializable
data class LiveGame(
    val uid: String,
    val startedAt: Long,
    val startingLife: Int = 40,
    val commanderDamage: Boolean = true,
    val turnsOn: Boolean = false,
    val clock: Boolean = true,
    /** Extra counters switched on for this game ([LiveGames.EXTRA_COUNTERS] keys, or "custom:Name"). */
    val counters: List<String> = emptyList(),
    val seats: List<LiveSeat>,
    val firstSeat: Int = 0,
    val active: Int = 0,
    val round: Int = 1,
    /** Game time (see [elapsed]) when the current turn began. */
    val turnStartMs: Long = 0,
    val turns: List<TurnTime> = emptyList(),
    /** Wall-clock time the game was paused at, while paused. */
    val pausedAt: Long? = null,
    /** Time spent paused before [pausedAt]. */
    val pausedMs: Long = 0,
    val monarch: Int? = null,
    val initiative: Int? = null,
    /** "day", "night" or null before it becomes day. */
    val dayNight: String? = null,
) {
    /** Playing time without pauses, at wall-clock time [now]. */
    fun elapsed(now: Long): Long = (now - startedAt - pausedMs - (pausedAt?.let { now - it } ?: 0)).coerceAtLeast(0)
    fun turnElapsed(now: Long): Long = (elapsed(now) - turnStartMs).coerceAtLeast(0)
    val paused: Boolean get() = pausedAt != null
    val alive: List<LiveSeat> get() = seats.filter { !it.out }
}

/** The rules of the tracker, apart from the screen, so they can be tested. Since 1.47. */
object LiveGames {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(g: LiveGame): String = json.encodeToString(LiveGame.serializer(), g)
    fun decode(s: String?): LiveGame? = s?.let { runCatching { json.decodeFromString(LiveGame.serializer(), it) }.getOrNull() }

    /** Six colours that go together and stay apart (also for colour blindness), with white text on them. */
    val PALETTE = listOf(0xFF1F5FA8, 0xFFB8471A, 0xFF12806A, 0xFF8E3B86, 0xFFA06A00, 0xFF4A5363)

    /** Counters that can be switched on for a game; commander damage, poison and the tax are always there. */
    val EXTRA_COUNTERS = linkedMapOf("energy" to "Energy", "experience" to "Experience", "rad" to "Rad", "storm" to "Storm")
    /** Table items that can be switched on. */
    const val MONARCH = "monarch"
    const val INITIATIVE = "initiative"
    const val DAY_NIGHT = "daynight"
    val TABLE_ITEMS = linkedMapOf(MONARCH to "Monarch", INITIATIVE to "Initiative", DAY_NIGHT to "Day / night")
    const val CUSTOM = "custom:"

    fun counterLabel(key: String) = EXTRA_COUNTERS[key] ?: TABLE_ITEMS[key] ?: key.removePrefix(CUSTOM)

    /** Out at 21 commander damage from one commander (rule 903.10a) and at ten poison counters (704.5c). */
    const val COMMANDER_LIMIT = 21
    const val POISON_LIMIT = 10

    /** The key of commander damage from [seat]'s commander ([second]: its partner). */
    fun cmdKey(seat: Int, second: Boolean = false) = if (second) "$seat:2" else "$seat"

    /** What would take [s] out, if anything: life at 0 or below, ten poison, or 21 damage from one commander. */
    fun outCause(s: LiveSeat): OutReason? = when {
        s.maxCommanderDamage >= COMMANDER_LIMIT -> OutReason.COMMANDER
        s.poison >= POISON_LIMIT -> OutReason.POISON
        s.life <= 0 -> OutReason.LIFE
        else -> null
    }

    /** The state "Dead?" is asked in; a "No" holds until it changes. */
    fun deathKey(s: LiveSeat) = "${s.life}/${s.poison}/${s.maxCommanderDamage}"

    /** Whether to ask "Dead?" on [s]'s panel. */
    fun asksDead(s: LiveSeat): Boolean = !s.out && outCause(s) != null && s.notDead != deathKey(s)

    /** Low life: below a quarter of the starting life (below 10 at 40), and still above 0. */
    fun lowLife(s: LiveSeat, startingLife: Int): Boolean = !s.out && s.life > 0 && s.life * 4 < startingLife

    private fun LiveGame.seat(i: Int, f: (LiveSeat) -> LiveSeat) = copy(seats = seats.map { if (it.seat == i) f(it) else it })

    fun changeLife(g: LiveGame, seat: Int, by: Int): LiveGame = g.seat(seat) { it.copy(life = it.life + by) }

    /** Commander damage [by] to [seat] from [from]'s commander; it's life lost too (rule 903.10a counts it). */
    fun commanderDamage(g: LiveGame, seat: Int, from: String, by: Int): LiveGame = g.seat(seat) { s ->
        val now = ((s.cmd[from] ?: 0) + by).coerceAtLeast(0)
        val real = now - (s.cmd[from] ?: 0)
        s.copy(cmd = if (now == 0) s.cmd - from else s.cmd + (from to now), life = s.life - real)
    }

    fun poison(g: LiveGame, seat: Int, by: Int): LiveGame = g.seat(seat) { it.copy(poison = (it.poison + by).coerceAtLeast(0)) }

    fun casts(g: LiveGame, seat: Int, by: Int, partner: Boolean = false): LiveGame = g.seat(seat) {
        if (partner) it.copy(partnerCasts = (it.partnerCasts + by).coerceAtLeast(0)) else it.copy(casts = (it.casts + by).coerceAtLeast(0))
    }

    fun counter(g: LiveGame, seat: Int, key: String, by: Int): LiveGame = g.seat(seat) { s ->
        val now = ((s.counters[key] ?: 0) + by).coerceAtLeast(0)
        s.copy(counters = if (now == 0) s.counters - key else s.counters + (key to now))
    }

    /** "Yes" to "Dead?" (or out by hand): out in the current round, with the cause. */
    fun knockOut(g: LiveGame, seat: Int, now: Long, reason: OutReason? = null): LiveGame {
        val after = g.seat(seat) { s ->
            s.copy(out = true, outRound = g.round, outReason = (reason ?: outCause(s) ?: OutReason.OTHER).key, outAtMs = g.elapsed(now), notDead = null)
        }
        // The turn passes on when the player whose turn it was goes out; the monarch and initiative stay where they are.
        return if (g.turnsOn && g.active == seat && after.alive.isNotEmpty()) nextTurn(after, now) else after
    }

    /** "No" to "Dead?": not asked again until something changes. */
    fun notDead(g: LiveGame, seat: Int): LiveGame = g.seat(seat) { it.copy(notDead = deathKey(it)) }

    /** "Undo" on an out player's panel: back in the game. */
    fun bringBack(g: LiveGame, seat: Int): LiveGame = g.seat(seat) { it.copy(out = false, outRound = null, outReason = null, outAtMs = null, notDead = deathKey(it)) }

    /** Ends the current turn: the next player still in, clockwise; a new round when it passes the first player's seat. */
    fun nextTurn(g: LiveGame, now: Long): LiveGame {
        val order = g.seats.map { it.seat }
        val n = order.size
        val at = order.indexOf(g.active).coerceAtLeast(0)
        var next = g.active
        var steps = 0
        for (step in 1..n) {
            val candidate = order[(at + step) % n]
            if (g.seats.first { it.seat == candidate }.out) continue
            next = candidate
            steps = step
            break
        }
        val round = if (steps > 0 && passesFirst(order, at, steps, g.firstSeat)) g.round + 1 else g.round
        val elapsed = g.elapsed(now)
        val record = TurnTime(g.round, g.active, (elapsed - g.turnStartMs).coerceAtLeast(0))
        return g.copy(active = next, round = round, turnStartMs = elapsed, turns = g.turns + record)
    }

    /** Whether moving [steps] seats on from index [at] reaches or passes the first player's seat. */
    private fun passesFirst(order: List<Int>, at: Int, steps: Int, first: Int): Boolean {
        val firstIdx = order.indexOf(first).coerceAtLeast(0)
        val n = order.size
        val dist = (firstIdx - at + n) % n
        return dist in 1..steps
    }

    fun pause(g: LiveGame, now: Long): LiveGame = if (g.paused) g else g.copy(pausedAt = now)
    fun resume(g: LiveGame, now: Long): LiveGame = g.pausedAt?.let { g.copy(pausedAt = null, pausedMs = g.pausedMs + (now - it)) } ?: g

    fun setMonarch(g: LiveGame, seat: Int?): LiveGame = g.copy(monarch = seat)
    fun setInitiative(g: LiveGame, seat: Int?): LiveGame = g.copy(initiative = seat)
    fun toggleDayNight(g: LiveGame): LiveGame = g.copy(dayNight = when (g.dayNight) { null -> "day"; "day" -> "night"; else -> "day" })
    fun clearDayNight(g: LiveGame): LiveGame = g.copy(dayNight = null)

    /**
     * The places when the game ends: players still in share the top (the winner first when there's one), then
     * those who went out, the last one out highest. [winnerSeat] null with more than one player still in is a draw.
     */
    fun places(g: LiveGame, winnerSeat: Int?): Map<Int, Int?> {
        val alive = g.seats.filter { !it.out }
        val out = g.seats.filter { it.out }.sortedWith(compareByDescending<LiveSeat> { it.outRound ?: 0 }.thenByDescending { it.outAtMs ?: 0 })
        val result = HashMap<Int, Int?>()
        val winner = winnerSeat ?: alive.singleOrNull()?.seat
        if (winner != null) {
            result[winner] = 1
            var place = 2
            for (s in alive.filter { it.seat != winner }) result[s.seat] = place++
            for (s in out) result[s.seat] = place++
        } else {
            // A draw: the players still in share no place; those out are placed after them.
            for (s in alive) result[s.seat] = null
            var place = alive.size + 1
            for (s in out) result[s.seat] = place++
        }
        return result
    }

    /** The record of a game that ended at [now]. */
    fun record(g: LiveGame, now: Long, winnerSeat: Int?, winType: WinType?, note: String?): GameRecord {
        val places = places(g, winnerSeat)
        val draw = winnerSeat == null && g.alive.size != 1
        val finalTurns = if (g.turnsOn) g.turns + TurnTime(g.round, g.active, g.turnElapsed(now)) else emptyList()
        return GameRecord(
            startedAt = g.startedAt,
            endedAt = now,
            durationMs = g.elapsed(now),
            startingLife = g.startingLife,
            turnsOn = g.turnsOn,
            rounds = if (g.turnsOn) g.round else null,
            winType = winType?.key,
            note = note?.takeIf { it.isNotBlank() },
            draw = draw,
            seats = Games.encodeSeats(g.seats.map { s ->
                GameSeat(
                    seat = s.seat, playerUid = s.playerUid, name = s.name, me = s.me, deckId = s.deckId, friendDeckUid = s.friendDeckUid,
                    deckName = s.deckName, commander = listOfNotNull(s.commander.ifBlank { null }, s.partner).joinToString(" & "),
                    scryfallId = s.scryfallId, colorIdentity = s.colorIdentity, bracket = s.bracket, power = s.power,
                    started = s.seat == g.firstSeat, place = places[s.seat], outRound = s.outRound, outReason = s.outReason, finalLife = s.life,
                )
            }),
            turns = Games.encodeTurns(finalTurns),
            uid = g.uid,
        )
    }
}
