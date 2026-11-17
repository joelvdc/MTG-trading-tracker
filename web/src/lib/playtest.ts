// The goldfish playtester for one Commander deck: the whole game state and every action, as a reducer, so the page
// only renders the state and dispatches actions. Undo is built in (past states are kept).
//
// Pure: no React, no DOM, no server imports, and no Math.random (a seeded generator keeps games repeatable).

export type ZoneId = "library" | "hand" | "battlefield" | "graveyard" | "exile" | "command";

/** The zones in a fixed order, handy for the page and for finding where a card is. */
export const ZONE_IDS: ZoneId[] = ["library", "hand", "battlefield", "graveyard", "exile", "command"];

export interface CardDef {
  /** One card of the deck (from the deck page's data). */
  scryfallId: string;
  name: string;
  /** Front picture. */
  imageUrl: string | null;
  /** Back face picture for double-faced cards, else null. */
  backImageUrl: string | null;
  typeLine: string;
  manaValue: number;
  isLand: boolean;
  commander: boolean;
  /** A token the player made up (no Scryfall card): drawn from these instead of a picture. */
  custom?: { colors: string[]; power: string; toughness: string; text: string };
}

/** A kind of counter on a card: "+1/+1", "-1/-1", "loyalty", "charge", or any name. */
export interface Counter {
  kind: string;
  amount: number;
}

export interface CardInstance {
  /** Unique in the game ("c1", "c2", …; tokens "t1", …). */
  id: string;
  def: CardDef;
  /** Tokens vanish when they leave the battlefield. */
  token: boolean;
  /** A copy made with "copy": the copied card's id (copies are tokens too). */
  copyOf: string | null;
  tapped: boolean;
  /** Showing the back face. */
  flipped: boolean;
  faceDown: boolean;
  counters: Counter[];
  /** Battlefield position in percent of the board (0–100); kept elsewhere too. */
  x: number;
  y: number;
  /** Library top card shown face up ("play with the top card revealed"). */
  revealed: boolean;
}

/** The player's own numbers; the commander tax is per commander card id, in steps of 2. */
export interface PlayerCounters {
  life: number;
  poison: number;
  energy: number;
  experience: number;
  commanderTax: Record<string, number>;
}

export interface LogEntry {
  turn: number;
  text: string;
}

export interface GameState {
  /** Current state of the random generator. */
  seed: number;
  /** Card ids in order; library[0] is the TOP card; hand/graveyard/exile newest last. */
  zones: Record<ZoneId, string[]>;
  cards: Record<string, CardInstance>;
  /** Starts at 1 after the opening hand is kept. */
  turn: number;
  mulligans: number;
  phase: "mulligan" | "bottom" | "playing";
  /** How many cards still go to the bottom in phase "bottom". */
  toBottom: number;
  /** The first mulligan is free (Commander multiplayer rule). */
  freeMulligan: boolean;
  drawOnFirstTurn: boolean;
  player: PlayerCounters;
  /** Newest last, at most 200 entries. */
  log: LogEntry[];
  nextId: number;
  /** Top cards being looked at, if the player is in the middle of a look, scry or surveil. */
  peek: { ids: string[]; mode: "look" | "scry" | "surveil" } | null;
  /**
   * Whether the top card of the library is shown face up. This is a state flag rather than a card flag because it
   * follows whatever is on top: whenever the library changes, the top card's `revealed` field is kept in step.
   */
  topRevealed: boolean;
  /** The deck this game was made from and the quantities, so "restart" can deal the same cards again. */
  deck: { def: CardDef; quantity: number }[];
}

/** A game and the states before it, for undo (at most 100). */
export interface Game {
  state: GameState;
  past: GameState[];
}

/** The player's numbers that actions may change by a delta. */
export type PlayerKey = "life" | "poison" | "energy" | "experience";

export type Action =
  | { type: "keep" }
  | { type: "mulligan" }
  | { type: "bottom"; id: string }
  | { type: "draw"; count?: number }
  | { type: "shuffle" }
  | { type: "mill"; count: number }
  | { type: "exileTop"; count: number; faceDown?: boolean }
  | { type: "revealTop"; on: boolean }
  | { type: "peek"; count: number; mode: "look" | "scry" | "surveil" }
  | { type: "peekDone"; top: string[]; bottom: string[]; graveyard: string[] }
  | { type: "peekCancel" }
  | { type: "move"; id: string; to: ZoneId; position?: "top" | "bottom" | number; x?: number; y?: number }
  | { type: "position"; id: string; x: number; y: number }
  | { type: "tutor"; id: string; to: ZoneId; position?: "top" | "bottom" }
  | { type: "tap"; id: string }
  | { type: "untapAll" }
  | { type: "tapAll" }
  | { type: "proliferate" }
  | { type: "flip"; id: string }
  | { type: "faceDown"; id: string }
  | { type: "counter"; id: string; kind: string; delta: number }
  | { type: "copy"; id: string }
  | { type: "token"; def: CardDef; count: number }
  | { type: "player"; key: PlayerKey; delta: number }
  | { type: "tax"; id: string; delta: number }
  | { type: "nextTurn" }
  | { type: "restart"; seed: number }
  | { type: "undo" }
  /** Several actions as one step: one undo takes them all back (a group inside a group is ignored). */
  | { type: "group"; actions: Action[] };

const MAX_LOG = 200;
/** How many states undo can go back. */
export const MAX_PAST = 100;

const PLAYER_LABELS: Record<PlayerKey, string> = { life: "Life", poison: "Poison", energy: "Energy", experience: "Experience" };

// ---------------------------------------------------------------------------------------------- random numbers

/**
 * A small deterministic generator (mulberry32): one step from [seed] to a value in [0, 1) and the next seed.
 * Always feed the returned seed back in, so a game repeats exactly from the same seed.
 */
export function rng(seed: number): { value: number; seed: number } {
  const next = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(next ^ (next >>> 15), 1 | next);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  const value = ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  return { value, seed: next >>> 0 };
}

/** Fisher–Yates: the ids in a new order, with the generator's seed after the shuffle. */
export function shuffleIds(ids: string[], seed: number): { ids: string[]; seed: number } {
  const out = ids.slice();
  let current = seed;
  for (let i = out.length - 1; i > 0; i--) {
    const step = rng(current);
    current = step.seed;
    const j = Math.floor(step.value * (i + 1));
    const swap = out[i];
    out[i] = out[j];
    out[j] = swap;
  }
  return { ids: out, seed: current };
}

// ---------------------------------------------------------------------------------------------- small helpers

function clampPercent(n: number): number {
  return Math.min(100, Math.max(0, n));
}

/** A whole number of cards, 1 or more. */
function isCount(n: number): boolean {
  return Number.isInteger(n) && n >= 1;
}

function isZoneId(value: string): value is ZoneId {
  return (ZONE_IDS as string[]).includes(value);
}

function ordinal(n: number): string {
  const mod100 = n % 100;
  if (mod100 >= 11 && mod100 <= 13) return `${n}th`;
  const mod10 = n % 10;
  if (mod10 === 1) return `${n}st`;
  if (mod10 === 2) return `${n}nd`;
  if (mod10 === 3) return `${n}rd`;
  return `${n}th`;
}

function addLog(state: GameState, text: string): void {
  state.log.push({ turn: state.turn, text });
  if (state.log.length > MAX_LOG) state.log.splice(0, state.log.length - MAX_LOG);
}

/** A card's name for the log: a face-down card stays hidden. */
function displayName(state: GameState, id: string): string {
  const card = state.cards[id];
  if (!card) return "a card";
  return card.faceDown ? "a face-down card" : card.def.name;
}

function zoneLabel(zone: ZoneId): string {
  return zone === "command" ? "command zone" : zone;
}

function zoneOf(state: GameState, id: string): ZoneId | null {
  for (const zone of ZONE_IDS) if (state.zones[zone].includes(id)) return zone;
  return null;
}

function removeFromZone(state: GameState, id: string): void {
  for (const zone of ZONE_IDS) {
    const list = state.zones[zone];
    const at = list.indexOf(id);
    if (at >= 0) {
      list.splice(at, 1);
      return;
    }
  }
}

/** Adds a card to a zone; the library takes "top" (the default), "bottom" or a number of cards from the top. */
function addToZone(state: GameState, id: string, zone: ZoneId, position?: "top" | "bottom" | number): void {
  if (zone === "library") {
    const length = state.zones.library.length;
    let at = 0;
    if (position === "bottom") at = length;
    else if (typeof position === "number") at = Math.min(length, Math.max(0, Math.round(position)));
    state.zones.library.splice(at, 0, id);
    return;
  }
  state.zones[zone].push(id);
}

/** Where a card enters the board: the player's spot, or a free-ish one that cascades by 3% (lands below). */
function battlefieldSpot(state: GameState, def: CardDef, x?: number, y?: number): { x: number; y: number } {
  const step = (state.zones.battlefield.length * 3) % 80;
  const spotX = x ?? 10 + step;
  const spotY = y ?? (def.isLand ? 70 + (step % 20) : 10 + (step % 40));
  return { x: clampPercent(spotX), y: clampPercent(spotY) };
}

/** The library top card's `revealed` field follows the game's topRevealed flag. */
function syncRevealed(state: GameState): void {
  const top = state.zones.library[0];
  for (const id of Object.keys(state.cards)) {
    const want = state.topRevealed && id === top;
    const card = state.cards[id];
    if (card.revealed !== want) card.revealed = want;
  }
}

/** A copy of the state that is safe to change without touching the previous state (for undo). */
function cloneState(state: GameState): GameState {
  const zones = {} as Record<ZoneId, string[]>;
  for (const zone of ZONE_IDS) zones[zone] = state.zones[zone].slice();
  const cards: Record<string, CardInstance> = {};
  for (const [id, card] of Object.entries(state.cards)) {
    cards[id] = { ...card, counters: card.counters.map((c) => ({ ...c })) };
  }
  return {
    ...state,
    zones,
    cards,
    player: { ...state.player, commanderTax: { ...state.player.commanderTax } },
    log: state.log.slice(),
    deck: state.deck.slice(),
    peek: state.peek ? { ids: state.peek.ids.slice(), mode: state.peek.mode } : null,
  };
}

/** The game with the new state, the old one kept for undo (at most MAX_PAST states). */
function commit(game: Game, next: GameState): Game {
  syncRevealed(next);
  const past = game.past.concat([game.state]);
  if (past.length > MAX_PAST) past.splice(0, past.length - MAX_PAST);
  return { state: next, past };
}

/** Draws from the top of the library, stopping when it runs out; returns how many were drawn. */
function drawCards(state: GameState, count: number, log = true): number {
  let drawn = 0;
  for (let i = 0; i < count && state.zones.library.length > 0; i++) {
    const id = state.zones.library.shift() as string;
    state.zones.hand.push(id);
    drawn += 1;
  }
  if (log && drawn > 0) addLog(state, `Drew ${drawn} card${drawn === 1 ? "" : "s"}.`);
  return drawn;
}

/** Turn 1: the opening hand is settled, and on the draw the first card comes along. */
function startPlaying(state: GameState): void {
  state.phase = "playing";
  state.toBottom = 0;
  state.turn = 1;
  addLog(state, "Turn 1.");
  if (state.drawOnFirstTurn) drawCards(state, 1);
}

/** Adds or removes counters; +1/+1 and -1/-1 counters cancel each other out as in the rules. */
function changeCounters(card: CardInstance, kind: string, delta: number): void {
  let remaining = delta;
  const opposite = kind === "+1/+1" ? "-1/-1" : kind === "-1/-1" ? "+1/+1" : null;
  if (opposite && remaining > 0) {
    const found = card.counters.find((c) => c.kind === opposite);
    const cancelled = found ? Math.min(remaining, Math.max(0, found.amount)) : 0;
    if (found && cancelled > 0) {
      found.amount -= cancelled;
      remaining -= cancelled;
      if (found.amount <= 0) card.counters = card.counters.filter((c) => c !== found);
    }
  }
  const own = card.counters.find((c) => c.kind === kind);
  const amount = (own ? own.amount : 0) + remaining;
  if (amount <= 0) card.counters = card.counters.filter((c) => c.kind !== kind);
  else if (own) own.amount = amount;
  else card.counters.push({ kind, amount });
}

/** Whether [given] is exactly [expected]: every id once, nothing else. */
function sameIds(expected: string[], given: string[]): boolean {
  if (given.length !== expected.length) return false;
  const want = new Set(expected);
  if (want.size !== expected.length) return false;
  const seen = new Set<string>();
  for (const id of given) {
    if (!want.has(id) || seen.has(id)) return false;
    seen.add(id);
  }
  return true;
}

// ---------------------------------------------------------------------------------------------- a new game

/**
 * A new game: an instance per card (quantity copies each), the commander in the command zone, the rest shuffled
 * into the library and 7 drawn. It starts in the mulligan phase.
 */
export function newGame(
  cards: { def: CardDef; quantity: number }[],
  opts: { seed: number; freeMulligan: boolean; drawOnFirstTurn: boolean },
): Game {
  const zones: Record<ZoneId, string[]> = { library: [], hand: [], battlefield: [], graveyard: [], exile: [], command: [] };
  const instances: Record<string, CardInstance> = {};
  const commanderTax: Record<string, number> = {};
  let nextId = 1;
  for (const entry of cards) {
    const quantity = Math.max(0, Math.floor(entry.quantity));
    for (let i = 0; i < quantity; i++) {
      const id = `c${nextId++}`;
      instances[id] = {
        id,
        def: entry.def,
        token: false,
        copyOf: null,
        tapped: false,
        flipped: false,
        faceDown: false,
        counters: [],
        x: 0,
        y: 0,
        revealed: false,
      };
      if (entry.def.commander) {
        zones.command.push(id);
        commanderTax[id] = 0;
      } else {
        zones.library.push(id);
      }
    }
  }

  const state: GameState = {
    seed: opts.seed,
    zones,
    cards: instances,
    turn: 0,
    mulligans: 0,
    phase: "mulligan",
    toBottom: 0,
    freeMulligan: opts.freeMulligan,
    drawOnFirstTurn: opts.drawOnFirstTurn,
    player: { life: 40, poison: 0, energy: 0, experience: 0, commanderTax },
    log: [],
    nextId,
    peek: null,
    topRevealed: false,
    deck: cards.map((entry) => ({ def: entry.def, quantity: entry.quantity })),
  };

  const shuffled = shuffleIds(state.zones.library, state.seed);
  state.zones.library = shuffled.ids;
  state.seed = shuffled.seed;
  const drawn = drawCards(state, 7, false);
  addLog(state, `Shuffled and drew ${drawn} card${drawn === 1 ? "" : "s"}.`);
  return { state, past: [] };
}

// ---------------------------------------------------------------------------------------------- the actions

/**
 * Applies one action. Invalid actions (unknown ids, the wrong phase, an empty library) and actions that change
 * nothing return the same game object, and nothing ever throws.
 */
export function act(game: Game, action: Action): Game {
  const state = game.state;
  const playing = state.phase === "playing";

  switch (action.type) {
    case "undo": {
      if (game.past.length === 0) return game;
      return { state: game.past[game.past.length - 1], past: game.past.slice(0, -1) };
    }

    case "restart": {
      const fresh = newGame(state.deck, {
        seed: action.seed,
        freeMulligan: state.freeMulligan,
        drawOnFirstTurn: state.drawOnFirstTurn,
      });
      return { state: fresh.state, past: [] };
    }

    case "group": {
      if (!Array.isArray(action.actions)) return game;
      // A group inside a group, undo and restart make no sense here: they are left out.
      const steps = action.actions.filter((a) => a.type !== "group" && a.type !== "undo" && a.type !== "restart");
      let inner: Game = { state, past: [] };
      for (const step of steps) inner = act(inner, step);
      if (inner.state === state) return game;
      // Moving cards around the board is not an undo step (as for one "position"): only anything else is.
      const movingOnly = steps.every((a) => a.type === "position");
      return movingOnly ? { state: inner.state, past: game.past } : commit(game, inner.state);
    }

    case "keep": {
      if (state.phase !== "mulligan") return game;
      const next = cloneState(state);
      const debt = next.mulligans - (next.freeMulligan ? 1 : 0);
      if (debt > 0) {
        // London mulligan: still 7 cards, but this many go to the bottom before playing.
        next.phase = "bottom";
        next.toBottom = debt;
        addLog(next, `Kept the hand and put ${debt} card${debt === 1 ? "" : "s"} on the bottom.`);
      } else {
        addLog(next, "Kept the hand.");
        startPlaying(next);
      }
      return commit(game, next);
    }

    case "mulligan": {
      if (state.phase !== "mulligan") return game;
      const next = cloneState(state);
      next.mulligans += 1;
      // The whole hand goes back, then the library is shuffled and a fresh 7 is drawn.
      next.zones.library.push(...next.zones.hand);
      next.zones.hand = [];
      const shuffled = shuffleIds(next.zones.library, next.seed);
      next.zones.library = shuffled.ids;
      next.seed = shuffled.seed;
      const drawn = drawCards(next, 7, false);
      addLog(next, `Mulligan to ${drawn} (${ordinal(next.mulligans)} mulligan).`);
      return commit(game, next);
    }

    case "bottom": {
      if (state.phase !== "bottom" || !state.zones.hand.includes(action.id)) return game;
      const next = cloneState(state);
      removeFromZone(next, action.id);
      next.zones.library.push(action.id);
      next.toBottom -= 1;
      addLog(next, `Put ${displayName(next, action.id)} on the bottom of the library.`);
      if (next.toBottom <= 0) startPlaying(next);
      return commit(game, next);
    }

    case "draw": {
      if (!playing) return game;
      const count = action.count ?? 1;
      if (!isCount(count) || state.zones.library.length === 0) return game;
      const next = cloneState(state);
      drawCards(next, count);
      return commit(game, next);
    }

    case "shuffle": {
      if (!playing || state.zones.library.length === 0) return game;
      const next = cloneState(state);
      const shuffled = shuffleIds(next.zones.library, next.seed);
      next.zones.library = shuffled.ids;
      next.seed = shuffled.seed;
      addLog(next, "Shuffled the library.");
      return commit(game, next);
    }

    case "mill": {
      if (!playing || !isCount(action.count) || state.zones.library.length === 0) return game;
      const next = cloneState(state);
      let milled = 0;
      while (milled < action.count && next.zones.library.length > 0) {
        next.zones.graveyard.push(next.zones.library.shift() as string);
        milled += 1;
      }
      addLog(next, `Milled ${milled} card${milled === 1 ? "" : "s"}.`);
      return commit(game, next);
    }

    case "exileTop": {
      if (!playing || !isCount(action.count) || state.zones.library.length === 0) return game;
      const next = cloneState(state);
      let exiled = 0;
      while (exiled < action.count && next.zones.library.length > 0) {
        const id = next.zones.library.shift() as string;
        if (action.faceDown) next.cards[id].faceDown = true;
        next.zones.exile.push(id);
        exiled += 1;
      }
      addLog(next, `Exiled ${exiled} card${exiled === 1 ? "" : "s"}${action.faceDown ? " face down" : ""}.`);
      return commit(game, next);
    }

    case "revealTop": {
      if (!playing || state.topRevealed === action.on) return game;
      const next = cloneState(state);
      next.topRevealed = action.on;
      addLog(next, action.on ? "Revealed the top card of the library." : "Stopped revealing the top card of the library.");
      return commit(game, next);
    }

    case "peek": {
      if (!playing || state.peek !== null || !isCount(action.count) || state.zones.library.length === 0) return game;
      const next = cloneState(state);
      const count = Math.min(action.count, next.zones.library.length);
      next.peek = { ids: next.zones.library.slice(0, count), mode: action.mode };
      const label =
        action.mode === "look"
          ? `Looking at the top ${count} card${count === 1 ? "" : "s"}.`
          : action.mode === "scry"
            ? `Scry ${count}.`
            : `Surveil ${count}.`;
      addLog(next, label);
      return commit(game, next);
    }

    case "peekDone": {
      if (!playing || state.peek === null) return game;
      const peeked = state.peek.ids;
      const given = [...action.top, ...action.bottom, ...action.graveyard];
      if (!sameIds(peeked, given)) return game;
      const next = cloneState(state);
      const mode = (next.peek as { ids: string[]; mode: "look" | "scry" | "surveil" }).mode;
      const rest = next.zones.library.filter((id) => !peeked.includes(id));
      next.zones.library = [...action.top, ...rest, ...action.bottom];
      next.zones.graveyard.push(...action.graveyard);
      next.peek = null;
      const parts: string[] = [];
      if (action.top.length > 0) parts.push(`${action.top.length} on top`);
      if (action.bottom.length > 0) parts.push(`${action.bottom.length} on the bottom`);
      if (action.graveyard.length > 0) parts.push(`${action.graveyard.length} to the graveyard`);
      const label = mode === "surveil" ? "Surveilled" : mode === "scry" ? "Scry" : "Looked at";
      addLog(next, `${label} ${peeked.length}: ${parts.join(", ")}.`);
      return commit(game, next);
    }

    case "peekCancel": {
      if (!playing || state.peek === null) return game;
      const next = cloneState(state);
      next.peek = null;
      addLog(next, "Stopped looking at the top of the library.");
      return commit(game, next);
    }

    case "move": {
      if (!playing || !isZoneId(action.to)) return game;
      const from = zoneOf(state, action.id);
      if (from === null) return game;
      // Two places on the board are the "position" action; a board-to-board "move" is not a move.
      if (from === "battlefield" && action.to === "battlefield") return game;
      const next = cloneState(state);
      const card = next.cards[action.id];
      removeFromZone(next, action.id);

      if (from === "battlefield" && card.token) {
        // A token that leaves the battlefield stops existing.
        delete next.cards[action.id];
        addLog(next, `${card.def.name} left the battlefield and vanished.`);
        return commit(game, next);
      }
      if (from === "battlefield") {
        // It is a new object now: untapped, no counters, face up and front side.
        card.tapped = false;
        card.counters = [];
        card.flipped = false;
        card.faceDown = false;
      }
      if (from === "command" && action.to === "battlefield" && card.def.commander) {
        // The tax is raised per cast, not per trip back: the log shows the tax this cast cost.
        const tax = next.player.commanderTax[action.id] ?? 0;
        addLog(next, `Cast ${displayName(next, action.id)} from the command zone (tax +${tax}).`);
        next.player.commanderTax[action.id] = tax + 2;
      }
      if (action.to === "battlefield") {
        const spot = battlefieldSpot(next, card.def, action.x, action.y);
        card.x = spot.x;
        card.y = spot.y;
      }
      addToZone(next, action.id, action.to, action.position);
      addLog(next, `${displayName(next, action.id)} → ${zoneLabel(action.to)}.`);
      return commit(game, next);
    }

    case "position": {
      if (!playing || zoneOf(state, action.id) !== "battlefield") return game;
      const x = clampPercent(action.x);
      const y = clampPercent(action.y);
      const card = state.cards[action.id];
      if (card.x === x && card.y === y) return game;
      const next = cloneState(state);
      next.cards[action.id].x = x;
      next.cards[action.id].y = y;
      syncRevealed(next);
      // Dragging a card around the board is not an undo step (and not worth a log line).
      return { state: next, past: game.past };
    }

    case "tutor": {
      if (!playing || !isZoneId(action.to) || zoneOf(state, action.id) !== "library") return game;
      const next = cloneState(state);
      const card = next.cards[action.id];
      removeFromZone(next, action.id);
      // The commander tax is raised by casting from the command zone only ("move" does that), not by a search.
      if (action.to === "battlefield") {
        const spot = battlefieldSpot(next, card.def);
        card.x = spot.x;
        card.y = spot.y;
      }
      addToZone(next, action.id, action.to, action.position);
      // Searching the library leaves it shuffled, unless the card is put back in the library itself.
      if (action.to !== "library") {
        const shuffled = shuffleIds(next.zones.library, next.seed);
        next.zones.library = shuffled.ids;
        next.seed = shuffled.seed;
      }
      addLog(next, `Searched for ${card.def.name}.`);
      return commit(game, next);
    }

    case "tap": {
      if (!playing || zoneOf(state, action.id) !== "battlefield") return game;
      const next = cloneState(state);
      const card = next.cards[action.id];
      card.tapped = !card.tapped;
      addLog(next, `${card.tapped ? "Tapped" : "Untapped"} ${displayName(next, action.id)}.`);
      return commit(game, next);
    }

    case "untapAll": {
      if (!playing) return game;
      const tapped = state.zones.battlefield.filter((id) => state.cards[id].tapped);
      if (tapped.length === 0) return game;
      const next = cloneState(state);
      for (const id of tapped) next.cards[id].tapped = false;
      addLog(next, "Untapped everything.");
      return commit(game, next);
    }

    case "tapAll": {
      if (!playing) return game;
      const untapped = state.zones.battlefield.filter((id) => !state.cards[id].tapped);
      if (untapped.length === 0) return game;
      const next = cloneState(state);
      for (const id of untapped) next.cards[id].tapped = true;
      addLog(next, "Tapped everything.");
      return commit(game, next);
    }

    case "proliferate": {
      if (!playing) return game;
      const withCounters = state.zones.battlefield.filter((id) =>
        state.cards[id].counters.some((counter) => counter.amount > 0),
      );
      if (withCounters.length === 0) return game;
      const next = cloneState(state);
      for (const id of withCounters) {
        const card = next.cards[id];
        // The kinds first: adding a counter may change (and reorder) the card's own list.
        const kinds = card.counters.filter((counter) => counter.amount > 0).map((counter) => counter.kind);
        for (const kind of kinds) changeCounters(card, kind, 1);
      }
      const n = withCounters.length;
      addLog(next, `Proliferated (${n} permanent${n === 1 ? "" : "s"}).`);
      return commit(game, next);
    }

    case "flip": {
      if (!playing || zoneOf(state, action.id) !== "battlefield") return game;
      if (!state.cards[action.id].def.backImageUrl) return game;
      const next = cloneState(state);
      const card = next.cards[action.id];
      card.flipped = !card.flipped;
      addLog(next, `${card.def.name} flipped to its ${card.flipped ? "back" : "front"} face.`);
      return commit(game, next);
    }

    case "faceDown": {
      if (!playing || zoneOf(state, action.id) !== "battlefield") return game;
      const next = cloneState(state);
      const card = next.cards[action.id];
      card.faceDown = !card.faceDown;
      addLog(next, `Turned ${card.def.name} face ${card.faceDown ? "down" : "up"}.`);
      return commit(game, next);
    }

    case "counter": {
      if (!playing || zoneOf(state, action.id) !== "battlefield") return game;
      if (!action.kind || !Number.isInteger(action.delta) || action.delta === 0) return game;
      const next = cloneState(state);
      const card = next.cards[action.id];
      changeCounters(card, action.kind, action.delta);
      const total = card.counters.filter((c) => c.kind === action.kind).reduce((sum, c) => sum + c.amount, 0);
      const name = displayName(next, action.id);
      if (action.delta > 0) {
        addLog(next, `${action.kind} counter on ${name} (now ${total}).`);
      } else {
        const gone = -action.delta;
        addLog(next, `Removed ${gone} ${action.kind} counter${gone === 1 ? "" : "s"} from ${name}${total > 0 ? ` (now ${total})` : ""}.`);
      }
      return commit(game, next);
    }

    case "copy": {
      if (!playing || zoneOf(state, action.id) !== "battlefield") return game;
      const next = cloneState(state);
      const origin = next.cards[action.id];
      const id = `t${next.nextId++}`;
      next.cards[id] = {
        id,
        def: origin.def,
        token: true,
        copyOf: action.id,
        tapped: false,
        flipped: origin.flipped,
        faceDown: false,
        counters: [],
        x: clampPercent(origin.x + 3),
        y: clampPercent(origin.y + 3),
        revealed: false,
      };
      next.zones.battlefield.push(id);
      addLog(next, `Created a token copy of ${origin.def.name}.`);
      return commit(game, next);
    }

    case "token": {
      if (!playing || !Number.isFinite(action.count)) return game;
      const count = Math.floor(action.count);
      if (count < 1) return game;
      const made = Math.min(count, 20);
      const next = cloneState(state);
      for (let i = 0; i < made; i++) {
        const id = `t${next.nextId++}`;
        const spot = battlefieldSpot(next, action.def);
        next.cards[id] = {
          id,
          def: action.def,
          token: true,
          copyOf: null,
          tapped: false,
          flipped: false,
          faceDown: false,
          counters: [],
          x: spot.x,
          y: spot.y,
          revealed: false,
        };
        next.zones.battlefield.push(id);
      }
      addLog(next, `Created ${made} ${action.def.name} token${made === 1 ? "" : "s"}.`);
      return commit(game, next);
    }

    case "player": {
      if (!playing || !Number.isInteger(action.delta) || action.delta === 0) return game;
      const key = action.key;
      const before = state.player[key];
      let value = before + action.delta;
      if ((key === "poison" || key === "energy" || key === "experience") && value < 0) value = 0;
      if (value === before) return game;
      const next = cloneState(state);
      next.player[key] = value;
      addLog(next, `${PLAYER_LABELS[key]} ${before} → ${value}.`);
      return commit(game, next);
    }

    case "tax": {
      if (!playing || !Number.isInteger(action.delta)) return game;
      const card = state.cards[action.id];
      if (!card || !card.def.commander) return game;
      const before = state.player.commanderTax[action.id] ?? 0;
      const value = Math.max(0, before + action.delta);
      if (value === before) return game;
      const next = cloneState(state);
      next.player.commanderTax[action.id] = value;
      addLog(next, `Commander tax for ${card.def.name} is now ${value}.`);
      return commit(game, next);
    }

    case "nextTurn": {
      if (!playing) return game;
      const next = cloneState(state);
      next.turn += 1;
      for (const id of next.zones.battlefield) next.cards[id].tapped = false;
      addLog(next, `Turn ${next.turn}.`);
      drawCards(next, 1);
      return commit(game, next);
    }

    default:
      return game;
  }
}

// ---------------------------------------------------------------------------------------------- the numbers

/** n choose k, kept small enough not to overflow for deck sizes. */
function choose(n: number, k: number): number {
  if (n < 0 || k < 0 || k > n) return 0;
  const steps = Math.min(k, n - k);
  let result = 1;
  for (let i = 0; i < steps; i++) result = (result * (n - i)) / (i + 1);
  return result;
}

/** The hypergeometric chance of exactly [k] lands in a hand of [handSize] from a library with [lands] lands. */
export function landOdds(librarySize: number, lands: number, handSize: number, k: number): number {
  if (librarySize <= 0 || lands < 0 || lands > librarySize || handSize < 0 || !Number.isFinite(k)) return 0;
  if (k < 0 || k > lands || handSize - k > librarySize - lands) return 0;
  const total = choose(librarySize, handSize);
  if (total === 0) return 0;
  return (choose(lands, k) * choose(librarySize - lands, handSize - k)) / total;
}

/** The chance that at least one of [copies] copies is among the first [cardsSeen] cards (opening 7 + draws). */
export function chanceByTurn(deckSize: number, copies: number, cardsSeen: number): number {
  if (deckSize <= 0 || copies <= 0 || cardsSeen <= 0) return 0;
  const deck = Math.floor(deckSize);
  const want = Math.min(Math.floor(copies), deck);
  const seen = Math.min(Math.floor(cardsSeen), deck);
  const total = choose(deck, seen);
  if (total === 0) return 0;
  return 1 - choose(deck - want, seen) / total;
}
