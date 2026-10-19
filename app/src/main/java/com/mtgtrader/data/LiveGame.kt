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

/** What a player's panel shows (since 1.48). */
enum class CardLayout(val key: String, val label: String) {
    DEFAULT("default", "Default"),
    NAME_ONLY("name", "Name only"),
    BIG("big", "Big font");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: DEFAULT
    }
}

/** What happens when a player is down: 0 life, ten poison or 21 commander damage (since 1.48). */
enum class WhenDown(val key: String, val label: String) {
    ASK("ask", "Ask \"Dead?\""),
    AUTO("auto", "Out at once"),
    OFF("off", "Nothing");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: ASK
    }
}

/**
 * A line of the game's log (since 1.48), at game time [at]: what changed for [seat] ([kind]: life, cmd, poison, tax,
 * counter, out, back, turn, monarch, initiative, day, start), by how much and to what. Changes in a row to the same
 * thing are merged into one line.
 */
@Serializable
data class LogEntry(
    val at: Long,
    val seat: Int? = null,
    val kind: String,
    val by: Int = 0,
    val total: Int = 0,
    /** Commander damage: the [LiveGames.cmdKey] it came from. */
    val from: String? = null,
    /** A counter's key, the tax's commander ("2": the partner), the reason someone went out, or "day"/"night". */
    val key: String? = null,
)

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
    /** Two commanders whose damage counts apart, switched by hand (since 1.48); null: as the deck has them. */
    val partners: Boolean? = null,
    /** This player's own choice of background (since 1.48): true the commander's picture, false the colour; null: as set for all. */
    val picture: Boolean? = null,
    /** A darker picture and stronger labels, for reading the panel more easily (since 1.48). */
    val contrast: Boolean = false,
) {
    val hasPartners: Boolean get() = partners ?: (partner != null)
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
    /** Commander damage also takes life (rule 903.10a's usual way; since 1.48 it can be switched off). */
    val cmdLifeLoss: Boolean = true,
    /** What happens when a player is down ([WhenDown] key; since 1.48). */
    val whenDown: String = WhenDown.ASK.key,
    /** For 4 and 6 players: one player at each end of the table, the others along the sides (since 1.48). */
    val ends: Boolean = false,
    /** The mana pool (W, U, B, R, G, C) and the storm count, for the whole table (since 1.48). */
    val mana: Map<String, Int> = emptyMap(),
    val storm: Int = 0,
    /** The game's log (since 1.48), oldest first; [logOn] off keeps none. */
    val log: List<LogEntry> = emptyList(),
    val logOn: Boolean = true,
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

    /**
     * Six soft colours that go together and stay apart, with dark text on them, as Lifetap's (since 1.48): each
     * panel fades from [PALETTE] to [PALETTE_END].
     */
    val PALETTE = listOf(0xFFC4D4F8, 0xFFF8D3C2, 0xFFC8EDD9, 0xFFDDCCF3, 0xFFF3E4B5, 0xFFF3C9D7)
    val PALETTE_END = listOf(0xFFB3C9D4, 0xFFE3BBA8, 0xFFA9D2C5, 0xFFC3B3DC, 0xFFDCC896, 0xFFD8AFC0)

    /** A player's counters besides poison and the tax; all of them can be used in any game (since 1.48). */
    val EXTRA_COUNTERS = linkedMapOf("energy" to "Energy", "experience" to "Experience", "rad" to "Rad", "treasure" to "Treasure", "storm" to "Storm")
    /** The city's blessing (ascend): a player has it or not. */
    const val ASCEND = "ascend"
    /** Table items that can be switched on. */
    const val MONARCH = "monarch"
    const val INITIATIVE = "initiative"
    const val DAY_NIGHT = "daynight"
    val TABLE_ITEMS = linkedMapOf(MONARCH to "Monarch", INITIATIVE to "Initiative", DAY_NIGHT to "Day / night")
    const val CUSTOM = "custom:"

    fun counterLabel(key: String) = EXTRA_COUNTERS[key] ?: TABLE_ITEMS[key] ?: if (key == ASCEND) "City's blessing" else key.removePrefix(CUSTOM)

    /** The mana pool's colours, in WUBRG order and colourless. */
    val MANA = listOf("W", "U", "B", "R", "G", "C")

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

    /** Commander damage [by] to [seat] from [from]'s commander; it's life lost too (rule 903.10a), unless switched off. */
    fun commanderDamage(g: LiveGame, seat: Int, from: String, by: Int): LiveGame = g.seat(seat) { s ->
        val now = ((s.cmd[from] ?: 0) + by).coerceAtLeast(0)
        val real = now - (s.cmd[from] ?: 0)
        s.copy(cmd = if (now == 0) s.cmd - from else s.cmd + (from to now), life = if (g.cmdLifeLoss) s.life - real else s.life)
    }

    /** Partner commanders for [seat] on or off: their damage is counted for each commander apart. */
    fun setPartners(g: LiveGame, seat: Int, on: Boolean): LiveGame = g.seat(seat) { it.copy(partners = on) }

    /** The city's blessing for [seat] (it's kept for the rest of the game, but can be taken back by mistake). */
    fun toggleAscend(g: LiveGame, seat: Int): LiveGame = g.seat(seat) { s ->
        s.copy(counters = if (ASCEND in s.counters) s.counters - ASCEND else s.counters + (ASCEND to 1))
    }

    fun setBackground(g: LiveGame, seat: Int, picture: Boolean?, colour: Int? = null): LiveGame =
        g.seat(seat) { it.copy(picture = picture, colour = colour ?: it.colour) }

    fun setContrast(g: LiveGame, seat: Int, on: Boolean): LiveGame = g.seat(seat) { it.copy(contrast = on) }

    /** The mana pool: [by] more (or less) of [colour]. */
    fun mana(g: LiveGame, colour: String, by: Int): LiveGame {
        val now = ((g.mana[colour] ?: 0) + by).coerceAtLeast(0)
        return g.copy(mana = if (now == 0) g.mana - colour else g.mana + (colour to now))
    }

    fun storm(g: LiveGame, by: Int): LiveGame = g.copy(storm = (g.storm + by).coerceAtLeast(0))

    fun emptyPool(g: LiveGame): LiveGame = g.copy(mana = emptyMap(), storm = 0)

    /** Whether [s] is asked "Dead?" in this game: only when the game asks, and not after a "No" in the same state. */
    fun asksDead(g: LiveGame, s: LiveSeat): Boolean = g.whenDown == WhenDown.ASK.key && asksDead(s)

    // ---- After every change: out at once (when the game says so), and the log ----

    /** Changes in a row to the same thing within this time are one line of the log. */
    const val LOG_MERGE_MS = 5_000L
    const val LOG_MAX = 1_000

    /**
     * [after] as it should be kept, coming from [before] at wall-clock time [now]: players who are down go out at
     * once if the game says so, and the changes are written to the log.
     */
    fun settle(before: LiveGame, after: LiveGame, now: Long): LiveGame {
        var g = after
        if (g.whenDown == WhenDown.AUTO.key) {
            g.seats.filter { asksDead(it) }.forEach { s -> g = knockOut(g, s.seat, now) }
        }
        if (!g.logOn) return g
        val lines = changes(before, g, g.elapsed(now))
        if (lines.isEmpty()) return g
        var log = g.log
        lines.forEach { log = append(log, it) }
        return g.copy(log = log.takeLast(LOG_MAX))
    }

    /** What changed from [a] to [b], as log lines at game time [at]. */
    fun changes(a: LiveGame, b: LiveGame, at: Long): List<LogEntry> {
        val out = mutableListOf<LogEntry>()
        for (s in b.seats) {
            val p = a.seats.firstOrNull { it.seat == s.seat } ?: continue
            var cmdTotal = 0
            (p.cmd.keys + s.cmd.keys).sorted().forEach { k ->
                val d = (s.cmd[k] ?: 0) - (p.cmd[k] ?: 0)
                if (d != 0) out += LogEntry(at, s.seat, "cmd", d, s.cmd[k] ?: 0, from = k)
                cmdTotal += d
            }
            val life = s.life - p.life + if (b.cmdLifeLoss) cmdTotal else 0
            if (life != 0) out += LogEntry(at, s.seat, "life", life, s.life)
            if (s.poison != p.poison) out += LogEntry(at, s.seat, "poison", s.poison - p.poison, s.poison)
            if (s.casts != p.casts) out += LogEntry(at, s.seat, "tax", (s.casts - p.casts) * 2, s.tax, key = "1")
            if (s.partnerCasts != p.partnerCasts) out += LogEntry(at, s.seat, "tax", (s.partnerCasts - p.partnerCasts) * 2, s.partnerTax, key = "2")
            (p.counters.keys + s.counters.keys).sorted().forEach { k ->
                val d = (s.counters[k] ?: 0) - (p.counters[k] ?: 0)
                if (d != 0) out += LogEntry(at, s.seat, "counter", d, s.counters[k] ?: 0, key = k)
            }
            if (s.out && !p.out) out += LogEntry(at, s.seat, "out", key = s.outReason)
            if (!s.out && p.out) out += LogEntry(at, s.seat, "back")
        }
        if (b.monarch != a.monarch) out += LogEntry(at, b.monarch, "monarch")
        if (b.initiative != a.initiative) out += LogEntry(at, b.initiative, "initiative")
        if (b.dayNight != a.dayNight && b.dayNight != null) out += LogEntry(at, null, "day", key = b.dayNight)
        if (b.turnsOn && b.turns.size > a.turns.size) out += LogEntry(at, b.active, "turn", total = b.round)
        return out
    }

    /** [log] with [e] added, merged into the last line when it's more of the same just after it. */
    fun append(log: List<LogEntry>, e: LogEntry): List<LogEntry> {
        val last = log.lastOrNull()
        val same = last != null && e.kind in MERGED && last.kind == e.kind && last.seat == e.seat && last.from == e.from && last.key == e.key &&
            e.at - last.at < LOG_MERGE_MS
        if (!same) return log + e
        val by = last!!.by + e.by
        return if (by == 0) log.dropLast(1) else log.dropLast(1) + e.copy(by = by)
    }

    private val MERGED = setOf("life", "cmd", "poison", "tax", "counter")

    /** A log line in words: "Joel −3 (37 → 34)", "Mads: 6 commander damage from Atraxa (6 in all)". */
    fun logText(e: LogEntry, g: LiveGame): String {
        val s = g.seats.firstOrNull { it.seat == e.seat }
        val name = s?.name ?: "?"
        fun signed(n: Int) = if (n > 0) "+$n" else "−${-n}"
        return when (e.kind) {
            "life" -> "$name ${signed(e.by)}  (${e.total - e.by} → ${e.total})"
            "cmd" -> {
                val fromSeat = e.from?.substringBefore(':')?.toIntOrNull()
                val o = g.seats.firstOrNull { it.seat == fromSeat }
                val commander = if (e.from?.endsWith(":2") == true) o?.partner else o?.commander?.ifBlank { null }
                val who = if (fromSeat == e.seat) "their own ${commander ?: "commander"}" else commander?.let { "$it (${o?.name})" } ?: o?.name ?: "?"
                "$name: ${signed(e.by)} commander damage from $who, ${e.total} in all"
            }
            "poison" -> "$name: ${signed(e.by)} poison, ${e.total} in all"
            "tax" -> "$name: commander tax ${signed(e.by)}" + (if (e.key == "2") " (${s?.partner})" else "") + ", now +${e.total}"
            "counter" -> if (e.key == ASCEND) (if (e.by > 0) "$name has the city's blessing" else "$name: city's blessing taken back")
                else "$name: ${signed(e.by)} ${counterLabel(e.key.orEmpty()).lowercase()}, ${e.total} in all"
            "out" -> "$name is out" + (OutReason.fromKey(e.key)?.let { " (${it.label.lowercase()})" } ?: "")
            "back" -> "$name is back in"
            "turn" -> "Round ${e.total} · $name's turn"
            "monarch" -> if (s == null) "Nobody is the monarch" else "$name is the monarch"
            "initiative" -> if (s == null) "Nobody has the initiative" else "$name has the initiative"
            "day" -> "It's ${e.key}"
            "start" -> "Game started" + (s?.let { " · $name starts" } ?: "")
            else -> e.kind
        }
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
