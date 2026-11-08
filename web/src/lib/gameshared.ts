// The app's recorded Commander games (data/Games.kt, GameStats.kt), shared by the server and the browser: the types,
// the app's stats (ported as they are, so the numbers match the phone's), the CSV export, and the website's extras
// (overview tiles, games per month, how games were won, the starting player's results, a period choice).

/** One player of a game (GameSeat). */
export interface GameSeat {
  /** 0 = the first seat, clockwise from there. */
  seat: number;
  playerUid?: string | null;
  name: string;
  me?: boolean;
  /** The app's deck (Deck.archidektId) the owner played. */
  deckId?: number | null;
  /** The friend's deck (FriendDeck.uid) played. */
  friendDeckUid?: string | null;
  deckName?: string;
  commander?: string;
  scryfallId?: string | null;
  colorIdentity?: string;
  bracket?: number | null;
  power?: number | null;
  started?: boolean;
  /** 1 = won; null for a draw or when not known. */
  place?: number | null;
  outRound?: number | null;
  outReason?: string | null;
  finalLife?: number | null;
}

export interface TurnTime {
  round: number;
  seat: number;
  ms: number;
}

/** A recorded game as the website's pages get it: the app's GameRecord with its seats and turns read. */
export interface Game {
  uid: string;
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
  seats: GameSeat[];
  turns: TurnTime[];
}

/** Someone who plays with the owner (Player). */
export interface Player {
  id: number;
  name: string;
  me: boolean;
  createdAt: number;
  uid: string | null;
  updatedAt: number;
}

/** A friend's deck (FriendDeck). */
export interface FriendDeck {
  id: number;
  playerUid: string;
  name: string;
  commander: string;
  scryfallId: string | null;
  partner: string | null;
  partnerScryfallId: string | null;
  colorIdentity: string;
  archidektUrl: string | null;
  createdAt: number;
  uid: string | null;
  updatedAt: number;
}

/** A guest's commander and partner, found on Scryfall (GuestCommander). */
export interface GuestCommander {
  commander: string;
  scryfallId: string | null;
  partner: string | null;
  colorIdentity: string;
}

/** Who sits in a seat and with which deck (the app's SeatChoice, without the tracker's art and colour). */
export interface SeatChoice {
  playerUid: string | null;
  name: string;
  me: boolean;
  deckId: number | null;
  friendDeckUid: string | null;
  guest: GuestCommander | null;
}

/** One of the owner's decks, as a seat can play it. */
export interface MyDeck {
  id: number;
  name: string;
  commanders: string;
  scryfallId: string | null;
  colorIdentity: string;
  bracket: number | null;
  power: number | null;
  /** Retired decks (app 1.83) aren't offered for new seats; a seat that has one keeps it. */
  retired?: boolean;
}

export interface PausedLine {
  uid: string;
  pausedAt: number;
  startedAt: number | null;
  names: string[];
}

/** The Games page's data (/api/games). */
export interface GamesView {
  games: Game[];
  players: Player[];
  friendDecks: FriendDeck[];
  myDecks: MyDeck[];
  paused: PausedLine[];
  readOnly: boolean;
}

/** How a game was won (WinType). */
export const WIN_TYPES: [string, string][] = [
  ["combat", "Combat"],
  ["commander", "Commander damage"],
  ["combo", "Combo"],
  ["drain", "Drain / burn"],
  ["poison", "Poison"],
  ["alt", "Alternative win"],
  ["conceded", "Everyone conceded"],
];

/** Why a player went out (OutReason). */
export const OUT_REASONS: [string, string][] = [
  ["life", "life"],
  ["commander", "commander damage"],
  ["poison", "poison"],
  ["conceded", "conceded"],
  ["other", "other"],
];

export function winTypeLabel(k: string | null | undefined): string | null {
  return WIN_TYPES.find((w) => w[0] === k)?.[1] ?? null;
}

export function outReasonLabel(k: string | null | undefined): string | null {
  return OUT_REASONS.find((w) => w[0] === k)?.[1] ?? null;
}

/** Scryfall's art crop of a card (GameStore.artCrop). */
export function artCrop(id: string | null | undefined): string | null {
  return id && id.length > 2 ? `https://cards.scryfall.io/art_crop/front/${id[0]}/${id[1]}/${id}.jpg` : null;
}

/** "1 h 14 min", "52 min", "45 s" (Games.duration). */
export function duration(ms: number): string {
  const min = Math.floor(ms / 60_000);
  if (min >= 60) return `${Math.floor(min / 60)} h ${String(min % 60).padStart(2, "0")} min`;
  if (min >= 1) return `${min} min`;
  return `${Math.floor(ms / 1000)} s`;
}

/** "1:42" or "1:02:05" (Games.clock). */
export function clock(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000));
  const two = (n: number) => String(n).padStart(2, "0");
  return s >= 3600 ? `${Math.floor(s / 3600)}:${two(Math.floor(s / 60) % 60)}:${two(s % 60)}` : `${Math.floor(s / 60)}:${two(s % 60)}`;
}

export function ordinal(n: number): string {
  return n === 1 ? "1st" : n === 2 ? "2nd" : n === 3 ? "3rd" : `${n}th`;
}

export function winner(g: Game): GameSeat | null {
  return g.draw ? null : (g.seats.find((s) => s.place === 1) ?? null);
}

// ---- The app's GameStats -----------------------------------------------------------------------------------------

/** A deck's results (DeckResult). */
export interface DeckResult {
  key: string;
  name: string;
  commander: string;
  scryfallId: string | null;
  colorIdentity: string;
  mine: boolean;
  deckId: number | null;
  games: number;
  wins: number;
  losses: number;
  winRate: number;
  avgMs: number;
  avgWinMs: number | null;
  avgRounds: number | null;
  usualWinRound: number | null;
  avgPlace: number | null;
  /** How its wins came, most first: [WinType key, count]. */
  winTypes: [string, number][];
  startedGames: number;
  startedWins: number;
  bracket: number | null;
  power: number | null;
  oppBracket: number | null;
  oppPower: number | null;
  lastPlayed: number;
  anonymous: boolean;
  /** Games one of the owner's decks was lent to a friend or guest in, and how many of those it won. */
  lentGames: number;
  lentWins: number;
}

export interface PlayerResult {
  key: string;
  name: string;
  me: boolean;
  games: number;
  wins: number;
  winRate: number;
  avgPlace: number | null;
  startedGames: number;
  startedWins: number;
}

export interface HeadToHead {
  key: string;
  name: string;
  games: number;
  ahead: number;
  behind: number;
}

/** The key of the seats without a deck or commander (GameStats.ANONYMOUS_KEY). */
export const ANONYMOUS_KEY = "n:|";

/** One key per deck: the app's deck, a friend's deck, or else its commander and name (GameStats.deckKey). */
export function deckKey(s: GameSeat): string {
  if (s.deckId != null) return `d:${s.deckId}`;
  if (s.friendDeckUid != null) return `f:${s.friendDeckUid}`;
  return `n:${(s.commander ?? "").toLowerCase()}|${(s.deckName ?? "").toLowerCase()}`;
}

/** One key per player: their sync id, or else their name (GameStats.playerKey). */
export function playerKey(s: GameSeat): string {
  return s.playerUid ?? `n:${s.name.trim().toLowerCase()}`;
}

const won = (g: Game, s: GameSeat) => !g.draw && s.place === 1;

/** One of the owner's decks played by someone else (app 1.82: friends and guests can borrow them). */
export function lent(s: GameSeat): boolean {
  return s.deckId != null && !s.me;
}
const avg = (xs: number[]): number | null => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : null);
/** Kotlin's List<Long>.average().toLong(): rounds towards zero. */
const avgLong = (xs: number[]): number => (xs.length ? Math.trunc(xs.reduce((a, b) => a + b, 0) / xs.length) : 0);

function groupBy<T>(list: T[], key: (t: T) => string): Map<string, T[]> {
  const m = new Map<string, T[]>();
  for (const t of list) {
    const k = key(t);
    const l = m.get(k);
    if (l) l.push(t);
    else m.set(k, [t]);
  }
  return m;
}

/** The first of the latest (Kotlin's maxBy keeps the first of equals). */
function latestOf<T>(list: T[], at: (t: T) => number): T {
  let best = list[0];
  for (const t of list) if (at(t) > at(best)) best = t;
  return best;
}

/** Kotlin's sortedByDescending on counts, stable (equal counts keep the order they were met in). */
function countsDesc(keys: string[]): [string, number][] {
  const m = new Map<string, number>();
  for (const k of keys) m.set(k, (m.get(k) ?? 0) + 1);
  return [...m.entries()].sort((a, b) => b[1] - a[1]);
}

/** Every deck's results (GameStats.decks). */
export function deckResults(games: Game[]): DeckResult[] {
  const rows = games.flatMap((g) => g.seats.map((s) => [g, s] as const));
  return [...groupBy(rows, (r) => deckKey(r[1])).entries()].map(([key, list]) => {
    const latest = latestOf(list, (r) => r[0].startedAt);
    const ls = latest[1];
    const wins = list.filter(([g, s]) => won(g, s));
    const withTurns = list.filter(([g]) => g.turnsOn && g.rounds != null);
    const winRounds = wins
      .map(([g]) => (g.turnsOn ? g.rounds : null))
      .filter((r): r is number => r != null)
      .sort((a, b) => a - b);
    const opponents = list.flatMap(([g, s]) => g.seats.filter((o) => o.seat !== s.seat));
    const places = list.map(([, s]) => s.place).filter((p): p is number => p != null);
    const oppB = opponents.map((o) => o.bracket).filter((b): b is number => b != null);
    const oppP = opponents.map((o) => o.power).filter((p): p is number => p != null);
    return {
      key,
      name: [ls.deckName, ls.commander].find((x) => x?.trim()) ?? "Anonymous",
      commander: ls.commander ?? "",
      scryfallId: ls.scryfallId ?? null,
      colorIdentity: ls.colorIdentity ?? "",
      // One of the owner's decks, whoever played it.
      mine: ls.deckId != null,
      deckId: ls.deckId ?? null,
      games: list.length,
      wins: wins.length,
      losses: list.length - wins.length,
      winRate: list.length ? wins.length / list.length : 0,
      avgMs: avgLong(list.map(([g]) => g.durationMs)),
      avgWinMs: wins.length ? avgLong(wins.map(([g]) => g.durationMs)) : null,
      avgRounds: avg(withTurns.map(([g]) => g.rounds!)),
      usualWinRound: winRounds.length ? winRounds[Math.floor((winRounds.length - 1) / 2)] : null,
      avgPlace: avg(places),
      winTypes: countsDesc(wins.map(([g]) => g.winType).filter((k): k is string => !!winTypeLabel(k))),
      startedGames: list.filter(([, s]) => s.started).length,
      startedWins: wins.filter(([, s]) => s.started).length,
      bracket: ls.bracket ?? null,
      power: ls.power ?? null,
      oppBracket: avg(oppB),
      oppPower: avg(oppP),
      lastPlayed: latest[0].startedAt,
      anonymous: key === ANONYMOUS_KEY,
      lentGames: list.filter(([, s]) => lent(s)).length,
      lentWins: wins.filter(([, s]) => lent(s)).length,
    };
  });
}

export type DeckOrder = "GAMES" | "WIN_RATE" | "WINS" | "LENGTH" | "PLACE" | "RECENT";

/** The app's deck sort orders (GameStats.sortDecks); Kotlin's sorts are stable, like JavaScript's. */
export function sortDecks(decks: DeckResult[], order: DeckOrder): DeckResult[] {
  const d = [...decks];
  switch (order) {
    case "GAMES":
      return d.sort((a, b) => b.games - a.games || b.winRate - a.winRate);
    case "WIN_RATE":
      return d.sort((a, b) => b.winRate - a.winRate || b.games - a.games);
    case "WINS":
      return d.sort((a, b) => b.wins - a.wins || b.winRate - a.winRate);
    case "LENGTH":
      return d.sort((a, b) => b.avgMs - a.avgMs);
    case "PLACE":
      return d.sort((a, b) => (a.avgPlace ?? Number.MAX_VALUE) - (b.avgPlace ?? Number.MAX_VALUE));
    case "RECENT":
      return d.sort((a, b) => b.lastPlayed - a.lastPlayed);
  }
}

/** Every player's results, the owner first (GameStats.players). */
export function playerResults(games: Game[]): PlayerResult[] {
  const rows = games.flatMap((g) => g.seats.map((s) => [g, s] as const));
  return [...groupBy(rows, (r) => playerKey(r[1])).entries()]
    .map(([key, list]): PlayerResult => {
      const latest = latestOf(list, (r) => r[0].startedAt)[1];
      const wins = list.filter(([g, s]) => won(g, s));
      return {
        key,
        name: latest.name,
        me: !!latest.me,
        games: list.length,
        wins: wins.length,
        winRate: list.length ? wins.length / list.length : 0,
        avgPlace: avg(list.map(([, s]) => s.place).filter((p): p is number => p != null)),
        startedGames: list.filter(([, s]) => s.started).length,
        startedWins: wins.filter(([, s]) => s.started).length,
      };
    })
    .sort((a, b) => Number(b.me) - Number(a.me) || b.games - a.games);
}

/** The owner against each other player: who finished ahead (GameStats.headToHead). */
export function headToHead(games: Game[]): HeadToHead[] {
  const out = new Map<string, HeadToHead>();
  for (const g of games) {
    const me = g.seats.find((s) => s.me);
    if (!me) continue;
    for (const o of g.seats) {
      if (o.me) continue;
      const key = playerKey(o);
      const was = out.get(key) ?? { key, name: o.name, games: 0, ahead: 0, behind: 0 };
      const mine = me.place ?? Number.MAX_SAFE_INTEGER;
      const theirs = o.place ?? Number.MAX_SAFE_INTEGER;
      out.set(key, { key, name: o.name, games: was.games + 1, ahead: was.ahead + (mine < theirs ? 1 : 0), behind: was.behind + (theirs < mine ? 1 : 0) });
    }
  }
  return [...out.values()].sort((a, b) => b.games - a.games);
}

/** How often the owner won, of the games they played (GameStats.myWinRate). */
export function myWinRate(games: Game[]): number | null {
  const mine = games.map((g) => [g, g.seats.find((s) => s.me)] as const).filter((x) => x[1]);
  if (!mine.length) return null;
  return mine.filter(([g, s]) => won(g, s!)).length / mine.length;
}

/** The games one of the owner's decks took part in, newest first (GameStats.gamesOfDeck). */
export function gamesOfDeck(games: Game[], deckId: number): Game[] {
  return games.filter((g) => g.seats.some((s) => s.deckId === deckId)).sort((a, b) => b.startedAt - a.startedAt);
}

/** The game history as a spreadsheet: one line per player per game (GameStats.csv); local time like the phone. */
export function gamesCsv(games: Game[]): string {
  const two = (n: number) => String(n).padStart(2, "0");
  const date = (ms: number) => {
    const d = new Date(ms);
    return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())} ${two(d.getHours())}:${two(d.getMinutes())}`;
  };
  const q = (s: string | null | undefined) => {
    const t = s ?? "";
    return /[,"\n]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
  };
  const head = "Date,Game,Minutes,Rounds,Player,Deck,Commander,Place,Won,Started,Out in round,Out by,Final life,Bracket,Power level,Win by,Note";
  const lines = [...games]
    .sort((a, b) => a.startedAt - b.startedAt)
    .flatMap((g) =>
      [...g.seats]
        .sort((a, b) => (a.place ?? Number.MAX_SAFE_INTEGER) - (b.place ?? Number.MAX_SAFE_INTEGER))
        .map((s) =>
          [
            date(g.startedAt),
            g.uid.slice(0, 8),
            String(Math.floor(g.durationMs / 60_000)),
            g.rounds?.toString() ?? "",
            q(s.name),
            q(s.deckName),
            q(s.commander),
            s.place?.toString() ?? "",
            won(g, s) ? "yes" : "no",
            s.started ? "yes" : "no",
            s.outRound?.toString() ?? "",
            outReasonLabel(s.outReason) ?? "",
            s.finalLife?.toString() ?? "",
            s.bracket?.toString() ?? "",
            s.power != null ? s.power.toFixed(1) : "",
            winTypeLabel(g.winType) ?? "",
            q(g.note),
          ].join(","),
        ),
    );
  return [head, ...lines].join("\n") + "\n";
}

// ---- The website's extras ----------------------------------------------------------------------------------------

export type Period = "ALL" | "YEAR" | "QUARTER";
export const PERIODS: [Period, string][] = [
  ["ALL", "All time"],
  ["YEAR", "Last 12 months"],
  ["QUARTER", "Last 3 months"],
];

/** The games started in [period] up to [now]. */
export function inPeriod(games: Game[], period: Period, now: number): Game[] {
  if (period === "ALL") return games;
  const from = new Date(now);
  from.setMonth(from.getMonth() - (period === "YEAR" ? 12 : 3));
  return games.filter((g) => g.startedAt >= from.getTime());
}

export interface Overview {
  games: number;
  myWinRate: number | null;
  avgMs: number | null;
  /** Of the games with turns counted (or typed). */
  avgRounds: number | null;
}

export function overview(games: Game[]): Overview {
  return {
    games: games.length,
    myWinRate: myWinRate(games),
    avgMs: games.length ? avgLong(games.map((g) => g.durationMs)) : null,
    avgRounds: avg(games.filter((g) => g.rounds != null).map((g) => g.rounds!)),
  };
}

/** Games per calendar month (local time), oldest first, months without games included; at most the last [max]. */
export function perMonth(games: Game[], max = 24): { month: string; label: string; games: number }[] {
  if (!games.length) return [];
  const key = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  const counts = new Map<string, number>();
  for (const g of games) {
    const k = key(new Date(g.startedAt));
    counts.set(k, (counts.get(k) ?? 0) + 1);
  }
  const first = new Date(Math.min(...games.map((g) => g.startedAt)));
  const last = new Date(Math.max(...games.map((g) => g.startedAt)));
  const out: { month: string; label: string; games: number }[] = [];
  const d = new Date(first.getFullYear(), first.getMonth(), 1);
  while (d.getFullYear() < last.getFullYear() || (d.getFullYear() === last.getFullYear() && d.getMonth() <= last.getMonth())) {
    const k = key(d);
    out.push({ month: k, label: d.toLocaleString("en-GB", { month: "short", year: "2-digit" }), games: counts.get(k) ?? 0 });
    d.setMonth(d.getMonth() + 1);
  }
  return out.slice(-max);
}

/** How the won games were won, most first; games won without saying how are counted as "not told". */
export function winsByType(games: Game[]): { key: string | null; label: string; games: number }[] {
  const wonGames = games.filter((g) => !g.draw && g.seats.some((s) => s.place === 1));
  const told = countsDesc(wonGames.map((g) => g.winType).filter((k): k is string => !!winTypeLabel(k))).map(([k, n]) => ({ key: k as string | null, label: winTypeLabel(k)!, games: n }));
  const untold = wonGames.filter((g) => !winTypeLabel(g.winType)).length;
  return untold ? [...told, { key: null, label: "Not told", games: untold }] : told;
}

export interface FirstPlayer {
  /** Games where the starting player and the winner are known. */
  games: number;
  /** Of those, won by the player who started. */
  wins: number;
  /** What an equal chance would give: the average of 1 / players over those games. */
  fairShare: number;
}

/** How the player who took the first turn did, in games with a starter and a winner. */
export function firstPlayer(games: Game[]): FirstPlayer {
  const known = games.filter((g) => g.seats.some((s) => s.started) && winner(g));
  return {
    games: known.length,
    wins: known.filter((g) => winner(g)!.started).length,
    fairShare: known.length ? known.reduce((a, g) => a + 1 / g.seats.length, 0) / known.length : 0,
  };
}

// ---- Seats -------------------------------------------------------------------------------------------------------

/** A seat's deck as the app knows it (SeatDeck). */
export interface SeatDeck {
  name: string;
  commander: string;
  partner: string | null;
  scryfallId: string | null;
  colorIdentity: string;
  bracket: number | null;
  power: number | null;
}

/** The deck a seat choice plays (the app's seatDeck). */
export function seatDeck(ch: SeatChoice, decks: MyDeck[], friendDecks: FriendDeck[]): SeatDeck | null {
  if (ch.deckId != null) {
    const d = decks.find((x) => x.id === ch.deckId);
    if (!d) return null;
    const names = d.commanders
      .split(" & ")
      .map((s) => s.trim())
      .filter(Boolean);
    return { name: d.name, commander: names[0] ?? "", partner: names[1] ?? null, scryfallId: d.scryfallId, colorIdentity: d.colorIdentity, bracket: d.bracket, power: d.power };
  }
  if (ch.friendDeckUid != null) {
    const f = friendDecks.find((x) => x.uid === ch.friendDeckUid);
    if (!f) return null;
    return { name: f.name, commander: f.commander, partner: f.partner, scryfallId: f.scryfallId, colorIdentity: f.colorIdentity, bracket: null, power: null };
  }
  if (ch.guest && ch.playerUid == null) {
    const g = ch.guest;
    return { name: "", commander: g.commander, partner: g.partner, scryfallId: g.scryfallId, colorIdentity: g.colorIdentity, bracket: null, power: null };
  }
  return null;
}

/** A recorded seat as a choice, to change it (the app's seatChoiceOf(GameSeat)). */
export function choiceOf(s: GameSeat): SeatChoice {
  const names = (s.commander ?? "").split(" & ");
  const guest =
    s.playerUid != null || s.deckId != null || s.friendDeckUid != null || !(names[0] ?? "").trim()
      ? null
      : { commander: names[0], scryfallId: s.scryfallId ?? null, partner: names[1] ?? null, colorIdentity: s.colorIdentity ?? "" };
  return { playerUid: s.playerUid ?? null, name: s.name, me: !!s.me, deckId: s.deckId ?? null, friendDeckUid: s.friendDeckUid ?? null, guest };
}

/** A recorded game's seat for [ch] in seat [i] playing [d] (the app's gameSeatOf). */
export function gameSeatOf(i: number, ch: SeatChoice, d: SeatDeck | null): GameSeat {
  return {
    seat: i,
    playerUid: ch.playerUid,
    name: ch.name.trim() || `Player ${i + 1}`,
    me: ch.me,
    deckId: ch.deckId,
    friendDeckUid: ch.friendDeckUid,
    deckName: d?.name ?? "",
    commander: [d?.commander || null, d?.partner].filter((x): x is string => !!x).join(" & "),
    scryfallId: d?.scryfallId ?? null,
    colorIdentity: d?.colorIdentity ?? "",
    bracket: d?.bracket ?? null,
    power: d?.power ?? null,
  };
}

/**
 * [old] with the player and deck of [ch] playing [d] (the app's changeSeat): how it went stays; the same deck keeps
 * its scores from the day, another deck brings today's.
 */
export function changeSeat(old: GameSeat, ch: SeatChoice, d: SeatDeck | null): GameSeat {
  const n = gameSeatOf(old.seat, ch, d);
  const player: GameSeat = { ...old, playerUid: n.playerUid, name: n.name, me: n.me };
  if (deckKey(n) === deckKey(old)) return player;
  return {
    ...player,
    deckId: n.deckId,
    friendDeckUid: n.friendDeckUid,
    deckName: n.deckName,
    commander: n.commander,
    scryfallId: n.scryfallId,
    colorIdentity: n.colorIdentity,
    bracket: n.bracket,
    power: n.power,
  };
}
