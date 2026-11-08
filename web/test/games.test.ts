import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  deckResults,
  firstPlayer,
  gamesCsv,
  gamesOfDeck,
  headToHead,
  inPeriod,
  myWinRate,
  overview,
  perMonth,
  playerResults,
  sortDecks,
  winsByType,
  type Game,
  type GameSeat,
  type SeatChoice,
} from "@/lib/gameshared";
import { applyGameEdit, encodeSeats, friendDecks, gameRecords, gamesView, players, type GameRecord } from "@/lib/games";
import { deletions, parse, type Json, type SyncFile } from "@/lib/syncfile";
import { EditError } from "@/lib/wishlist";

const NOW = Date.UTC(2026, 10, 8, 12);
const DAY = 86_400_000;
let n = 0;
const uids = () => `u${String(++n).padStart(31, "0")}`;

const ME = "p0000000000000000000000000000001";
const ANNA = "p0000000000000000000000000000002";
const BO = "p0000000000000000000000000000003";
const ANNA_DECK = "f0000000000000000000000000000001";

const seat = (s: Partial<GameSeat> & { seat: number; name: string }): GameSeat => s;

function record(uid: string, daysAgo: number, seats: GameSeat[], over: Partial<GameRecord> = {}): GameRecord {
  const at = NOW - daysAgo * DAY;
  return {
    id: 0,
    startedAt: at,
    endedAt: at + 60 * 60_000,
    durationMs: 60 * 60_000,
    startingLife: 40,
    turnsOn: false,
    rounds: null,
    winType: null,
    note: null,
    draw: false,
    manual: false,
    seats: encodeSeats(seats),
    turns: null,
    uid,
    updatedAt: at,
    ...over,
  };
}

/** The app's example file with three players, a friend's deck, three games and a paused game. */
function file(): SyncFile {
  const f = parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
  f.data.players = [
    { id: 0, name: "Joel", me: true, createdAt: 1, uid: ME, updatedAt: 1 },
    { id: 0, name: "Anna", me: false, createdAt: 1, uid: ANNA, updatedAt: 1 },
    { id: 0, name: "Bo", me: false, createdAt: 1, uid: BO, updatedAt: 1 },
  ] as unknown as Json;
  f.data.friendDecks = [
    { id: 0, playerUid: ANNA, name: "Atraxa", commander: "Atraxa, Praetors' Voice", scryfallId: null, partner: null, partnerScryfallId: null, colorIdentity: "WUBG", archidektUrl: null, createdAt: 1, uid: ANNA_DECK, updatedAt: 1 },
  ] as unknown as Json;
  const me = (place: number | null, started = false) => seat({ seat: 0, playerUid: ME, name: "Joel", me: true, deckId: 10710772, deckName: "Vohar Danse", commander: "Vohar", colorIdentity: "UB", bracket: 3, power: 7, place, started });
  const anna = (place: number | null, started = false) => seat({ seat: 1, playerUid: ANNA, name: "Anna", friendDeckUid: ANNA_DECK, deckName: "Atraxa", commander: "Atraxa, Praetors' Voice", colorIdentity: "WUBG", place, started });
  const bo = (place: number | null, started = false) => seat({ seat: 2, playerUid: BO, name: "Bo", place, started });
  f.data.games = [
    record("g1", 200, [me(1, true), anna(2), bo(3)], { turnsOn: true, rounds: 8, winType: "combo", durationMs: 90 * 60_000 }),
    record("g2", 40, [me(2), anna(1, true), bo(3)], { turnsOn: true, rounds: 10, winType: "combat" }),
    record("g3", 10, [me(1), anna(3), bo(2, true)], { turnsOn: true, rounds: 6, winType: "combo", durationMs: 30 * 60_000, note: 'Thoracle, "again"' }),
  ] as unknown as Json;
  f.data.pausedGames = [{ id: 0, pausedAt: NOW - DAY, game: JSON.stringify({ uid: "x", startedAt: NOW - DAY - 3600_000, seats: [{ seat: 0, name: "Joel" }, { seat: 1, name: "Bo" }] }), clockStopped: false, uid: "pg1", updatedAt: NOW - DAY }] as unknown as Json;
  return f;
}

const games = () => gamesView(file()).games;
const mine: SeatChoice = { playerUid: ME, name: "", me: true, deckId: 10710772, friendDeckUid: null, guest: null };
const guest = (name: string, commander?: string): SeatChoice => ({ playerUid: null, name, me: false, deckId: null, friendDeckUid: null, guest: commander ? { commander, scryfallId: null, partner: null, colorIdentity: "R" } : null });

describe("the app's game stats", () => {
  it("gives each deck its results the way the phone does", () => {
    const d = deckResults(games());
    const vohar = d.find((x) => x.deckId === 10710772)!;
    expect(vohar).toMatchObject({ name: "Vohar Danse", mine: true, games: 3, wins: 2, losses: 1, avgPlace: 4 / 3, avgRounds: 8, usualWinRound: 6, startedGames: 1, startedWins: 1, bracket: 3, power: 7 });
    expect(vohar.avgMs).toBe(60 * 60_000);
    expect(vohar.avgWinMs).toBe(60 * 60_000);
    expect(vohar.winTypes).toEqual([["combo", 2]]);
    expect(vohar.winRate).toBeCloseTo(2 / 3);
    // Bo plays without a deck: the anonymous pile.
    expect(d.find((x) => x.anonymous)).toMatchObject({ name: "Anonymous", games: 3, wins: 0 });
    expect(sortDecks(d, "WIN_RATE").map((x) => x.name)).toEqual(["Vohar Danse", "Atraxa", "Anonymous"]);
    expect(sortDecks(d, "PLACE")[0].name).toBe("Vohar Danse");
  });

  it("gives players, head to head and the owner's win rate", () => {
    const g = games();
    expect(playerResults(g).map((p) => [p.name, p.games, p.wins, p.me])).toEqual([
      ["Joel", 3, 2, true],
      ["Anna", 3, 1, false],
      ["Bo", 3, 0, false],
    ]);
    expect(headToHead(g).map((h) => [h.name, h.games, h.ahead, h.behind])).toEqual([
      ["Anna", 3, 2, 1],
      ["Bo", 3, 3, 0],
    ]);
    expect(myWinRate(g)).toBeCloseTo(2 / 3);
    expect(gamesOfDeck(g, 10710772).map((x) => x.uid)).toEqual(["g3", "g2", "g1"]);
  });

  it("exports the history like the app's CSV", () => {
    const csv = gamesCsv(games()).split("\n");
    expect(csv[0]).toBe("Date,Game,Minutes,Rounds,Player,Deck,Commander,Place,Won,Started,Out in round,Out by,Final life,Bracket,Power level,Win by,Note");
    expect(csv).toHaveLength(1 + 9 + 1);
    expect(csv[1]).toContain(",g1,90,8,Joel,Vohar Danse,Vohar,1,yes,yes,,,,3,7.0,Combo,");
    expect(csv[9]).toContain(`"Thoracle, ""again"""`);
  });
});

describe("the website's extra stats", () => {
  it("filters by period and sums up the games", () => {
    const g = games();
    expect(inPeriod(g, "QUARTER", NOW).map((x) => x.uid)).toEqual(["g3", "g2"]);
    expect(inPeriod(g, "YEAR", NOW)).toHaveLength(3);
    expect(overview(inPeriod(g, "QUARTER", NOW))).toEqual({ games: 2, myWinRate: 0.5, avgMs: 45 * 60_000, avgRounds: 8 });
  });

  it("counts games per month, how they were won and the starting player's wins", () => {
    const g = games();
    const months = perMonth(g);
    expect(months.reduce((a, m) => a + m.games, 0)).toBe(3);
    expect(months.length).toBeGreaterThanOrEqual(7);
    expect(winsByType(g)).toEqual([
      { key: "combo", label: "Combo", games: 2 },
      { key: "combat", label: "Combat", games: 1 },
    ]);
    expect(firstPlayer(g)).toEqual({ games: 3, wins: 2, fairShare: 1 / 3 });
  });
});

describe("changing games on the website", () => {
  it("adds a past game by hand, with a guest's commander and the owner's deck scores of today", () => {
    const f = file();
    const at = NOW - 2 * DAY;
    const { uid } = applyGameEdit(
      f,
      { type: "saveGame", uid: null, startedAt: at, minutes: 75, rounds: 9, draw: false, winType: "commander", note: " ", seats: [{ choice: mine, place: 2 }, { choice: guest("Kim", "Krenko, Mob Boss"), place: 1 }] },
      NOW,
      uids,
    );
    const r = gameRecords(f).find((x) => x.uid === uid)!;
    expect(r).toMatchObject({ startedAt: at, durationMs: 75 * 60_000, endedAt: at + 75 * 60_000, manual: true, turnsOn: true, rounds: 9, winType: "commander", note: null, draw: false, updatedAt: NOW });
    const seats = JSON.parse(r.seats);
    expect(seats[0]).toEqual({ seat: 0, playerUid: ME, name: "Joel", me: true, deckId: 10710772, deckName: "Vohar Danse", commander: "Vohar", colorIdentity: "UB", place: 2 });
    expect(seats[1]).toEqual({ seat: 1, name: "Kim", commander: "Krenko, Mob Boss", colorIdentity: "R", place: 1 });
    expect(gamesView(f).games[0].uid).toBe(uid);
  });

  it("changes a recorded game: places, draw, players and decks; the same deck keeps the day's scores", () => {
    const f = file();
    applyGameEdit(
      f,
      {
        type: "saveGame",
        uid: "g1",
        minutes: null,
        rounds: null,
        draw: false,
        winType: "poison",
        note: "fixed",
        seats: [
          { seat: 0, choice: mine, place: 2 },
          { seat: 2, choice: { playerUid: BO, name: "", me: false, deckId: null, friendDeckUid: null, guest: null }, place: 1 },
        ],
      },
      NOW,
      uids,
    );
    const g = gamesView(f).games.find((x) => x.uid === "g1")!;
    expect(g.seats.map((s) => [s.name, s.place])).toEqual([
      ["Joel", 2],
      ["Anna", 2],
      ["Bo", 1],
    ]);
    expect(g.seats[0]).toMatchObject({ bracket: 3, power: 7, started: true });
    expect(g).toMatchObject({ winType: "poison", note: "fixed", durationMs: 90 * 60_000, rounds: null, turnsOn: true });
    // Nobody in place 1: a draw, and no way of winning.
    applyGameEdit(f, { type: "saveGame", uid: "g1", minutes: 20, rounds: 4, draw: false, winType: "combo", note: null, seats: [{ seat: 2, choice: { playerUid: BO, name: "", me: false, deckId: null, friendDeckUid: null, guest: null }, place: 3 }] }, NOW + 1, uids);
    expect(gamesView(f).games.find((x) => x.uid === "g1")).toMatchObject({ draw: true, winType: null, durationMs: 20 * 60_000, rounds: 4 });
    expect(() => applyGameEdit(f, { type: "saveGame", uid: "g1", minutes: null, rounds: null, draw: false, winType: null, note: null, seats: [{ seat: 0, choice: mine, place: 7 }, { seat: 1, choice: mine, place: 1 }] }, NOW, uids)).toThrow(EditError);
  });

  it("deletes a game for every phone, and Undo brings it back under a new uid", () => {
    const f = file();
    const { removed } = applyGameEdit(f, { type: "deleteGame", uid: "g2" }, NOW, uids);
    expect(gameRecords(f).map((r) => r.uid)).not.toContain("g2");
    expect(deletions(f).some((d) => d.uid === "g2")).toBe(true);
    const { uid } = applyGameEdit(f, { type: "restoreGame", game: removed! }, NOW + 1, uids);
    expect(gamesView(f).games.find((g) => g.uid === uid)).toMatchObject({ winType: "combat", rounds: 10 });
  });

  it("drops a paused game and shows who was playing", () => {
    const f = file();
    expect(gamesView(f).paused).toEqual([{ uid: "pg1", pausedAt: NOW - DAY, startedAt: NOW - DAY - 3600_000, names: ["Joel", "Bo"] }]);
    applyGameEdit(f, { type: "dropPaused", uid: "pg1" }, NOW, uids);
    expect(gamesView(f).paused).toEqual([]);
    expect(deletions(f).some((d) => d.uid === "pg1")).toBe(true);
  });

  it("adds, renames and deletes players and friends' decks; past games keep their names", () => {
    const f = file();
    applyGameEdit(f, { type: "addPlayer", name: " Cleo " }, NOW, uids);
    const cleo = players(f).find((p) => p.name === "Cleo")!;
    applyGameEdit(f, { type: "renamePlayer", uid: cleo.uid!, name: "Cleo B" }, NOW, uids);
    applyGameEdit(
      f,
      { type: "saveFriendDeck", deck: { uid: null, playerUid: cleo.uid!, name: "", commander: "Krenko, Mob Boss", scryfallId: null, partner: null, partnerScryfallId: null, colorIdentity: "r", archidektUrl: null } },
      NOW,
      uids,
    );
    expect(friendDecks(f).find((d) => d.playerUid === cleo.uid)).toMatchObject({ name: "Krenko, Mob Boss", colorIdentity: "R" });
    expect(() => applyGameEdit(f, { type: "deletePlayer", uid: ME }, NOW, uids)).toThrow(/yourself/);
    applyGameEdit(f, { type: "deletePlayer", uid: ANNA }, NOW, uids);
    expect(players(f).map((p) => p.name)).toEqual(expect.not.arrayContaining(["Anna"]));
    expect(friendDecks(f).some((d) => d.uid === ANNA_DECK)).toBe(false);
    expect(deletions(f).filter((d) => d.uid === ANNA || d.uid === ANNA_DECK)).toHaveLength(2);
    expect(gamesView(f).games.every((g) => g.seats.some((s) => s.name === "Anna"))).toBe(true);
  });

  it("makes the owner's player when there is none yet", () => {
    const f = file();
    f.data.players = [] as unknown as Json;
    applyGameEdit(f, { type: "saveGame", uid: null, startedAt: NOW - DAY, minutes: 30, rounds: null, draw: true, winType: "combo", note: null, seats: [{ choice: { ...mine, playerUid: null }, place: null }, { choice: guest(""), place: null }] }, NOW, uids);
    const me = players(f).find((p) => p.me)!;
    expect(me.name).toBe("Me");
    const g = gamesView(f).games[0];
    expect(g).toMatchObject({ draw: true, winType: null });
    expect(g.seats.map((s) => [s.name, s.playerUid ?? null])).toEqual([
      ["Me", me.uid],
      ["Player 2", null],
    ]);
  });
});
