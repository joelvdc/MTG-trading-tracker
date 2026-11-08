// The recorded games in the sync file (the app's data/Games.kt, GameStore.kt): reading them for the Games page, and
// the changes the website makes, following the app's rules (GameDetailScreen, "Add a past game", PlayersScreen). The
// life tracker itself stays on the phone. Raw objects are edited in place, so fields a newer app adds are kept.
import { syncDecks } from "./decks";
import {
  changeSeat,
  choiceOf,
  gameSeatOf,
  seatDeck,
  winTypeLabel,
  type FriendDeck,
  type Game,
  type GameSeat,
  type GamesView,
  type GuestCommander,
  type MyDeck,
  type PausedLine,
  type Player,
  type SeatChoice,
  type TurnTime,
} from "./gameshared";
import { addDeletion, bump, canWrite, compareUid, newUid, type Json, type SyncFile } from "./syncfile";
import { EditError } from "./wishlist";

/** A game as the app syncs it (GameRecord): its seats and turns are JSON text. */
export interface GameRecord {
  id: number;
  startedAt: number;
  endedAt: number;
  durationMs: number;
  startingLife: number;
  turnsOn: boolean;
  rounds: number | null;
  winType: string | null;
  note: string | null;
  draw: boolean;
  manual: boolean;
  seats: string;
  turns: string | null;
  uid: string | null;
  updatedAt: number;
}

interface PausedGame {
  id: number;
  pausedAt: number;
  game: string;
  uid: string | null;
  updatedAt: number;
}

const list = <T>(file: SyncFile, key: string): T[] => ((file.data[key] as Json[] | undefined) ?? []) as unknown as T[];
export const gameRecords = (file: SyncFile) => list<GameRecord>(file, "games");
export const players = (file: SyncFile) => list<Player>(file, "players");
export const friendDecks = (file: SyncFile) => list<FriendDeck>(file, "friendDecks");
const pausedGames = (file: SyncFile) => list<PausedGame>(file, "pausedGames");

function setList<T extends { uid: string | null }>(file: SyncFile, key: string, items: T[]): void {
  items.sort((a, b) => compareUid(a.uid, b.uid));
  file.data[key] = items as unknown as Json;
}

function parseJson<T>(text: string | null | undefined, fallback: T): T {
  if (!text) return fallback;
  try {
    return (JSON.parse(text) as T) ?? fallback;
  } catch {
    return fallback;
  }
}

/** The GameSeat defaults the app leaves out of its JSON (encodeDefaults = false). */
const SEAT_DEFAULTS: Record<string, unknown> = {
  playerUid: null,
  me: false,
  deckId: null,
  friendDeckUid: null,
  deckName: "",
  commander: "",
  scryfallId: null,
  colorIdentity: "",
  bracket: null,
  power: null,
  started: false,
  place: null,
  outRound: null,
  outReason: null,
  finalLife: null,
};

/** Seats as the app writes them (Games.encodeSeats): fields at their default value left out. */
export function encodeSeats(seats: GameSeat[]): string {
  return JSON.stringify(
    seats.map((s) => {
      const o: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(s)) if (v !== undefined && !(k in SEAT_DEFAULTS && SEAT_DEFAULTS[k] === v)) o[k] = v;
      return o;
    }),
  );
}

export function gameOf(r: GameRecord): Game {
  return {
    uid: r.uid!,
    startedAt: r.startedAt,
    endedAt: r.endedAt,
    durationMs: r.durationMs ?? 0,
    startingLife: r.startingLife ?? 40,
    turnsOn: !!r.turnsOn,
    rounds: r.rounds ?? null,
    winType: r.winType ?? null,
    note: r.note ?? null,
    draw: !!r.draw,
    manual: !!r.manual,
    seats: parseJson<GameSeat[]>(r.seats, []).filter((s) => s && typeof s === "object" && typeof s.name === "string"),
    turns: parseJson<TurnTime[]>(r.turns, []),
  };
}

/** Every recorded game, newest first. */
export function gamesOf(file: SyncFile): Game[] {
  return gameRecords(file)
    .filter((r) => r.uid)
    .map(gameOf)
    .sort((a, b) => b.startedAt - a.startedAt || compareUid(a.uid, b.uid));
}

export function myDecks(file: SyncFile): MyDeck[] {
  return syncDecks(file)
    .map((d) => ({
      id: d.deck.archidektId,
      name: d.deck.name,
      commanders: d.deck.commanders ?? "",
      scryfallId: d.deck.commanderScryfallId ?? null,
      colorIdentity: d.deck.colorIdentity ?? "",
      bracket: d.deck.bracketRealistic ?? null,
      power: d.deck.powerLevel ?? null,
      retired: !!d.deck.retired,
    }))
    .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
}

export function gamesView(file: SyncFile): GamesView {
  const paused: PausedLine[] = pausedGames(file)
    .filter((p) => p.uid)
    .map((p) => {
      const live = parseJson<{ startedAt?: number; seats?: { name?: string }[] }>(p.game, {});
      return { uid: p.uid!, pausedAt: p.pausedAt, startedAt: live.startedAt ?? null, names: (live.seats ?? []).map((s) => s.name ?? "").filter(Boolean) };
    })
    .sort((a, b) => b.pausedAt - a.pausedAt);
  return {
    games: gamesOf(file),
    players: players(file)
      .filter((p) => p.uid)
      .sort((a, b) => Number(b.me) - Number(a.me) || a.name.toLowerCase().localeCompare(b.name.toLowerCase())),
    friendDecks: friendDecks(file)
      .filter((d) => d.uid)
      .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())),
    myDecks: myDecks(file),
    paused,
    readOnly: !canWrite(file),
  };
}

// ---- Changes -----------------------------------------------------------------------------------------------------

export interface SeatInput {
  /** The seat changed (an existing game); a new game's seats go in order. */
  seat?: number;
  choice: SeatChoice;
  place: number | null;
}

export interface FriendDeckInput {
  uid: string | null;
  playerUid: string;
  name: string;
  commander: string;
  scryfallId: string | null;
  partner: string | null;
  partnerScryfallId: string | null;
  colorIdentity: string;
  archidektUrl: string | null;
}

export type GameEdit =
  /** A changed game (uid) or a past game added by hand (uid null, with [startedAt]). */
  | {
      type: "saveGame";
      uid: string | null;
      startedAt?: number;
      minutes: number | null;
      rounds: number | null;
      draw: boolean;
      winType: string | null;
      note: string | null;
      seats: SeatInput[];
    }
  | { type: "deleteGame"; uid: string }
  /** Undo of a delete: the game comes back under a new uid (so no phone's deletion takes it again). */
  | { type: "restoreGame"; game: GameRecord }
  | { type: "dropPaused"; uid: string }
  | { type: "addPlayer"; name: string }
  | { type: "renamePlayer"; uid: string; name: string }
  /** A friend and their decks; their past games keep their names. */
  | { type: "deletePlayer"; uid: string }
  | { type: "saveFriendDeck"; deck: FriendDeckInput }
  | { type: "deleteFriendDeck"; uid: string };

export interface GameEditResult {
  /** The game saved (a new game's uid). */
  uid?: string;
  /** A deleted game, for Undo. */
  removed?: GameRecord;
}

const MAX_SEATS = 8;
const MAX_TEXT = 200;
const WUBRG = "WUBRG";

function text(v: unknown, what: string, max = MAX_TEXT, required = true): string {
  const s = typeof v === "string" ? v.trim() : "";
  if (required && !s) throw new EditError(`Type ${what}.`);
  if (s.length > max) throw new EditError(`${what[0].toUpperCase()}${what.slice(1)} is too long.`);
  return s;
}

function optText(v: unknown, max = MAX_TEXT): string | null {
  return typeof v === "string" && v.trim() ? v.trim().slice(0, max) : null;
}

function wholeOrNull(v: unknown, min: number, max: number, what: string): number | null {
  if (v === null || v === undefined || v === "") return null;
  if (typeof v !== "number" || !Number.isInteger(v) || v < min || v > max) throw new EditError(`${what} must be a whole number from ${min} to ${max}.`);
  return v;
}

const colours = (s: unknown) => (typeof s === "string" ? [...WUBRG].filter((c) => s.toUpperCase().includes(c)).join("") : "");
const scryId = (v: unknown) => (typeof v === "string" && /^[0-9a-f-]{36}$/.test(v) ? v : null);

/** The owner as a player, made when first needed ("Me", as the app does). */
function mePlayer(file: SyncFile, now: number, makeUid: () => string): Player {
  const all = players(file);
  const me = all.find((p) => p.me && p.uid);
  if (me) return me;
  const p: Player = { id: 0, name: "Me", me: true, createdAt: now, uid: makeUid(), updatedAt: now };
  setList(file, "players", [...all, p]);
  return p;
}

/** A checked seat choice: the player's current name, decks that exist, a guest's commander only for a guest. */
function checkChoice(file: SyncFile, raw: SeatChoice, i: number, now: number, makeUid: () => string): SeatChoice {
  if (!raw || typeof raw !== "object") throw new EditError("A seat has no player.");
  let ch: SeatChoice;
  if (raw.me) {
    const me = mePlayer(file, now, makeUid);
    ch = { playerUid: me.uid, name: me.name, me: true, deckId: null, friendDeckUid: null, guest: null };
  } else if (raw.playerUid) {
    const p = players(file).find((x) => x.uid === raw.playerUid);
    if (!p) throw new EditError("That player isn't there any more (deleted on a phone?).");
    ch = { playerUid: p.uid, name: p.name, me: !!p.me, deckId: null, friendDeckUid: null, guest: null };
  } else {
    ch = { playerUid: null, name: optText(raw.name, 60) ?? `Player ${i + 1}`, me: false, deckId: null, friendDeckUid: null, guest: null };
  }
  if (raw.deckId != null) {
    if (!myDecks(file).some((d) => d.id === raw.deckId)) throw new EditError("That deck isn't there any more.");
    ch.deckId = raw.deckId;
  } else if (raw.friendDeckUid) {
    if (!friendDecks(file).some((d) => d.uid === raw.friendDeckUid)) throw new EditError("That friend's deck isn't there any more.");
    ch.friendDeckUid = raw.friendDeckUid;
  } else if (raw.guest && ch.playerUid == null) {
    const commander = optText(raw.guest.commander, 120);
    if (commander) ch.guest = { commander, scryfallId: scryId(raw.guest.scryfallId), partner: optText(raw.guest.partner, 120), colorIdentity: colours(raw.guest.colorIdentity) };
  }
  return ch;
}

/** Whether a seat choice sent is the seat as recorded (the page sends it back unchanged). */
function sameChoice(a: SeatChoice, b: SeatChoice): boolean {
  if (!a || typeof a !== "object") return false;
  const key = (c: SeatChoice) =>
    JSON.stringify([c.playerUid ?? null, !!c.me, c.deckId ?? null, c.friendDeckUid ?? null, c.playerUid ? "" : (c.name ?? "").trim(), c.guest ? [c.guest.commander, c.guest.partner ?? null] : null]);
  return key(a) === key(b);
}

function checkPlaces(seats: SeatInput[], players: number): void {
  for (const s of seats) wholeOrNull(s.place, 1, players, "A place");
}

function saveGame(file: SyncFile, e: Extract<GameEdit, { type: "saveGame" }>, now: number, makeUid: () => string): GameEditResult {
  if (!Array.isArray(e.seats) || e.seats.length > MAX_SEATS) throw new EditError(`A game has 2 to ${MAX_SEATS} players.`);
  const minutes = wholeOrNull(e.minutes, 0, 24 * 60, "Minutes");
  const rounds = wholeOrNull(e.rounds, 1, 99, "Turns");
  const winType = e.winType && winTypeLabel(e.winType) ? e.winType : null;
  const note = optText(e.note, 1000);
  const decks = myDecks(file);
  const all = gameRecords(file);
  const result = (seats: GameSeat[]) => {
    // One of the owner's decks is played at one seat only (it can be lent to a friend or guest).
    const ids = seats.map((s) => s.deckId).filter((d): d is number => d != null);
    if (new Set(ids).size !== ids.length) throw new EditError("One of your decks is at two seats.");
    return { draw: !!e.draw || !seats.some((s) => s.place === 1) };
  };

  if (e.uid) {
    const r = all.find((x) => x.uid === e.uid);
    if (!r) throw new EditError("That game isn't there any more (deleted on a phone?).");
    const old = gameOf(r).seats;
    checkPlaces(e.seats, old.length);
    const seats = old.map((s) => {
      const inp = e.seats.find((x) => x.seat === s.seat);
      if (!inp) return s;
      // The same player and deck as recorded: only the place changes (even when that player or deck is deleted since).
      if (sameChoice(inp.choice, choiceOf(s))) return { ...s, place: inp.place ?? null };
      const ch = checkChoice(file, inp.choice, s.seat, now, makeUid);
      return { ...changeSeat(s, ch, seatDeck(ch, decks, friendDecks(file))), place: inp.place ?? null };
    });
    const { draw } = result(seats);
    const ms = minutes != null ? minutes * 60_000 : r.durationMs;
    Object.assign(r, {
      seats: encodeSeats(seats),
      draw,
      winType: draw ? null : winType,
      note,
      durationMs: ms,
      endedAt: r.startedAt + ms,
      rounds,
      turnsOn: !!r.turnsOn || rounds != null,
      updatedAt: bump(r.updatedAt ?? 0, now),
    });
    setList(file, "games", all);
    return { uid: r.uid! };
  }

  if (e.seats.length < 2) throw new EditError(`A game has 2 to ${MAX_SEATS} players.`);
  checkPlaces(e.seats, e.seats.length);
  const at = e.startedAt;
  if (typeof at !== "number" || !Number.isFinite(at) || at < Date.UTC(1993, 0, 1) || at > now + 86_400_000) throw new EditError("Pick the day the game was played.");
  const seats = e.seats.map((inp, i) => {
    const ch = checkChoice(file, inp.choice, i, now, makeUid);
    return { ...gameSeatOf(i, ch, seatDeck(ch, decks, friendDecks(file))), place: inp.place ?? null };
  });
  const { draw } = result(seats);
  const ms = (minutes ?? 0) * 60_000;
  const uid = makeUid();
  const r: GameRecord = {
    id: 0,
    startedAt: at,
    endedAt: at + ms,
    durationMs: ms,
    startingLife: 40,
    turnsOn: rounds != null,
    rounds,
    winType: draw ? null : winType,
    note,
    draw,
    manual: true,
    seats: encodeSeats(seats),
    turns: null,
    uid,
    updatedAt: now,
  };
  setList(file, "games", [...gameRecords(file), r]);
  return { uid };
}

function saveFriendDeck(file: SyncFile, d: FriendDeckInput, now: number, makeUid: () => string): void {
  if (!d || typeof d !== "object") throw new EditError("No deck sent.");
  const owner = players(file).find((p) => p.uid === d.playerUid);
  if (!owner || owner.me) throw new EditError("That player isn't there any more.");
  const commander = text(d.commander, "the commander", 120);
  const fields = {
    playerUid: owner.uid!,
    name: optText(d.name, 120) ?? commander,
    commander,
    scryfallId: scryId(d.scryfallId),
    partner: optText(d.partner, 120),
    partnerScryfallId: scryId(d.partnerScryfallId),
    colorIdentity: colours(d.colorIdentity),
    archidektUrl: optText(d.archidektUrl, 300),
  };
  const all = friendDecks(file);
  if (d.uid) {
    const x = all.find((f) => f.uid === d.uid);
    if (!x) throw new EditError("That deck isn't there any more.");
    Object.assign(x, fields, { updatedAt: bump(x.updatedAt ?? 0, now) });
    setList(file, "friendDecks", all);
  } else {
    setList(file, "friendDecks", [...all, { id: 0, ...fields, createdAt: now, uid: makeUid(), updatedAt: now }]);
  }
}

/** Applies [edit] to [file] at [now] ([makeUid] is for tests). */
export function applyGameEdit(file: SyncFile, edit: GameEdit, now: number, makeUid: () => string = newUid): GameEditResult {
  switch (edit.type) {
    case "saveGame":
      return saveGame(file, edit, now, makeUid);
    case "deleteGame": {
      const all = gameRecords(file);
      const r = all.find((x) => x.uid === edit.uid);
      if (!r) throw new EditError("That game isn't there any more.");
      addDeletion(file, r.uid!, bump(r.updatedAt ?? 0, now));
      setList(
        file,
        "games",
        all.filter((x) => x !== r),
      );
      return { removed: { ...r } };
    }
    case "restoreGame": {
      const g = edit.game;
      if (!g || typeof g !== "object" || typeof g.seats !== "string" || typeof g.startedAt !== "number") throw new EditError("Nothing to bring back.");
      const uid = makeUid();
      setList(file, "games", [...gameRecords(file), { ...g, id: 0, uid, updatedAt: now }]);
      return { uid };
    }
    case "dropPaused": {
      const all = pausedGames(file);
      const p = all.find((x) => x.uid === edit.uid);
      if (!p) throw new EditError("That paused game isn't there any more (continued on a phone?).");
      addDeletion(file, p.uid!, bump(p.updatedAt ?? 0, now));
      setList(
        file,
        "pausedGames",
        all.filter((x) => x !== p),
      );
      return {};
    }
    case "addPlayer": {
      const name = text(edit.name, "a name", 60);
      setList(file, "players", [...players(file), { id: 0, name, me: false, createdAt: now, uid: makeUid(), updatedAt: now }]);
      return {};
    }
    case "renamePlayer": {
      const all = players(file);
      const p = all.find((x) => x.uid === edit.uid);
      if (!p) throw new EditError("That player isn't there any more.");
      p.name = text(edit.name, "a name", 60);
      p.updatedAt = bump(p.updatedAt ?? 0, now);
      setList(file, "players", all);
      return {};
    }
    case "deletePlayer": {
      const all = players(file);
      const p = all.find((x) => x.uid === edit.uid);
      if (!p) throw new EditError("That player isn't there any more.");
      if (p.me) throw new EditError("You can rename yourself, not delete yourself.");
      const decks = friendDecks(file);
      const theirs = decks.filter((d) => d.playerUid === p.uid);
      for (const d of theirs) addDeletion(file, d.uid!, bump(d.updatedAt ?? 0, now));
      addDeletion(file, p.uid!, bump(p.updatedAt ?? 0, now));
      setList(
        file,
        "friendDecks",
        decks.filter((d) => !theirs.includes(d)),
      );
      setList(
        file,
        "players",
        all.filter((x) => x !== p),
      );
      return {};
    }
    case "saveFriendDeck":
      saveFriendDeck(file, edit.deck, now, makeUid);
      return {};
    case "deleteFriendDeck": {
      const all = friendDecks(file);
      const d = all.find((x) => x.uid === edit.uid);
      if (!d) throw new EditError("That deck isn't there any more.");
      addDeletion(file, d.uid!, bump(d.updatedAt ?? 0, now));
      setList(
        file,
        "friendDecks",
        all.filter((x) => x !== d),
      );
      return {};
    }
  }
  throw new EditError("Unknown change.");
}

/** A guest's or friend's commander (and partner) as Scryfall names them, with their colours (GameStore.guestCommander). */
export async function lookupCommander(
  commander: string,
  partner: string | null,
  fuzzy: (name: string) => Promise<{ card: { name: string; scryfallId: string }; colorIdentity: string[] } | null>,
): Promise<GuestCommander & { partnerScryfallId: string | null }> {
  const c = commander.trim();
  const p = partner?.trim() || null;
  const main = c ? await fuzzy(c).catch(() => null) : null;
  const second = p ? await fuzzy(p).catch(() => null) : null;
  const all = new Set([...(main?.colorIdentity ?? []), ...(second?.colorIdentity ?? [])]);
  return {
    commander: main?.card.name ?? c,
    scryfallId: main?.card.scryfallId ?? null,
    partner: second?.card.name ?? p,
    partnerScryfallId: second?.card.scryfallId ?? null,
    colorIdentity: [...WUBRG].filter((x) => all.has(x)).join(""),
  };
}
