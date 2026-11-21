// Sort the collection (the app's CollectionSort.kt, since 1.104): splits the collection into three binders (Collection
// binder, Not bulk, Bulk) and Unsorted, with the best cards first. Value and how much Commander players want a card
// (EDHREC rank) say where it goes. Once the first sort is done it keeps the result steady: a card gives up its place
// only to one that's clearly better, rests eight weeks after a move, and only so many copies move a week.
//
// planSort is the pure core (the app's CollectionSortPlanner.plan, tested by collsort-cases.json); sortSuggest,
// sortSetUp, sortApply and sortStartOver read and write the sync file the way tradebinder.ts does. The rules, the
// settled flag, the declined moves, the log and the three binders' uids are synced settings (since app 1.104):
// sortMaxTop, sortMaxMid, sortMaxBulk, sortFloor, sortMix, sortWeekly, sortSettled, sortSkips, sortLog, sortTop,
// sortMid, sortBulk.
import type { CkFile } from "./cardkingdom";
import type { DetailsFile } from "./carddetails";
import { applyCollectionEdit } from "./collection";
import { decksOf } from "./collview";
import { format } from "./money";
import type { PriceFile } from "./prices";
import { priceType } from "./prices";
import { cardmarketPrice, finishOf, pricingSetup, unitPrice } from "./pricing";
import { binders as bindersOf, collection, prefs, setPrefs, type CollectionItem, type SyncFile } from "./syncfile";
import { kindKey, kindOf, nameKey, popularity, why } from "./tradebinder";

/** The three places the collection is sorted into; Unsorted holds the rest. */
export type SortPlace = "top" | "mid" | "bulk";
/** Unsorted's key in the plan: never synced, it's the app's built-in Unsorted (no binder). */
export const UNSORTED_KEY = "unsorted";
/** Any binder that isn't one of the four places (a deck's, the trade binder, …): never touched, only counted. */
export const OTHER_KEY = "other";

/** The places in the order they're filled in, and the order moves are listed in. */
export const SORT_PLACES: readonly SortPlace[] = ["top", "mid", "bulk"];
/** The places in order, highest first (the app's LEVELS). */
export const SORT_LEVELS: readonly string[] = ["top", "mid", "bulk", UNSORTED_KEY];
/** What each place's binder is called until the owner names it. */
export const PLACE_LABELS: Record<SortPlace, string> = { top: "Collection binder", mid: "Not bulk", bulk: "Bulk" };

/** The synced settings' keys: the three binders by uid. */
export const SORT_PREF: Record<SortPlace, string> = { top: "sortTop", mid: "sortMid", bulk: "sortBulk" };
export const SORT_RULES_PREF = { maxTop: "sortMaxTop", maxMid: "sortMaxMid", maxBulk: "sortMaxBulk", floor: "sortFloor", mix: "sortMix", weekly: "sortWeekly" } as const;
export const SORT_SETTLED_PREF = "sortSettled";
export const SORT_SKIPS_PREF = "sortSkips";
export const SORT_LOG_PREF = "sortLog";

/** A moved card rests this many days (eight weeks). */
export const REST_DAYS = 56;
/** Log lines older than this many days are forgotten. */
export const LOG_DAYS = 70;

/** How many copies each place may hold, and how the sort weighs cards. */
export interface SortRules {
  maxTop: number;
  maxMid: number;
  /** 0 = as many as it takes. */
  maxBulk: number;
  /** Under this price a card is always bulk (the same money as the trade binder's minimum). */
  floor: number;
  /** 0 = by value only, 50 = like the trade binder, 100 = popularity counts most. */
  mix: number;
  /** Most copies that may move in seven days once the first sort is done. */
  weekly: number;
}

export const DEFAULT_SORT_RULES: SortRules = { maxTop: 200, maxMid: 1000, maxBulk: 0, floor: 0.5, mix: 50, weekly: 10 };

/** One card's copies in one place: a stack of the collection. [fixed] = signed or altered. */
export interface SortStack {
  key: string;
  name: string;
  /** A place's key, [UNSORTED_KEY], or [OTHER_KEY] (a deck's binder, the trade binder…). */
  place: string;
  copies: number;
  price?: number | null;
  rank?: number | null;
  fixed?: boolean;
}

/** A suggested move of [copies] of a card from one place to another, and why. */
export interface SortMove {
  key: string;
  name: string;
  from: string;
  to: string;
  copies: number;
  price: number | null;
  score: number;
  reason: string;
}

/** This move, turned down: it isn't suggested again ("<kindKey>><placeKey>"). */
export const skipKeyOf = (m: Pick<SortMove, "key" | "to">) => `${m.key}>${m.to}`;

/** What a sort would do, how many copies each place holds afterwards, and what waits for next week. */
export interface SortPlan {
  moves: SortMove[];
  after: Record<string, number>;
  /** Copies whose move waits for next week (the weekly cap). */
  heldBack: number;
  /** Copies that moved in the last seven days, against the cap. */
  weekUsed: number;
  /** How many copies may move now (-1 before the first sort, when there's no cap). */
  budget: number;
}

/** The planner's input, as the shared cases give it. */
export interface SortInput {
  stacks: SortStack[];
  /** Per nameKey (front face, lowercased, trimmed): copies the decks use. */
  deckNeeds: Record<string, number>;
  rules: SortRules;
  skips: string[];
  log: string[];
  settled: boolean;
  /** The UTC epoch day. */
  today: number;
}

const money = (eur: number) => format(eur, "EUR");

/** The synced rules (the app's defaults where a phone hasn't sent them, checked like the trade binder's). */
export function sortRulesOf(p: Record<string, string>): SortRules {
  const int = (key: string, d: number, ok: (n: number) => boolean) => {
    const n = Number.parseInt(p[key] ?? "", 10);
    return Number.isFinite(n) && ok(n) ? n : d;
  };
  const floor = Number.parseFloat(p[SORT_RULES_PREF.floor] ?? "");
  return {
    maxTop: int(SORT_RULES_PREF.maxTop, DEFAULT_SORT_RULES.maxTop, (n) => n >= 0),
    maxMid: int(SORT_RULES_PREF.maxMid, DEFAULT_SORT_RULES.maxMid, (n) => n >= 0),
    maxBulk: int(SORT_RULES_PREF.maxBulk, DEFAULT_SORT_RULES.maxBulk, (n) => n >= 0),
    floor: Number.isFinite(floor) && floor >= 0 ? floor : DEFAULT_SORT_RULES.floor,
    mix: int(SORT_RULES_PREF.mix, DEFAULT_SORT_RULES.mix, (n) => n >= 0 && n <= 100),
    weekly: int(SORT_RULES_PREF.weekly, DEFAULT_SORT_RULES.weekly, (n) => n >= 1),
  };
}

/** The rules as the app writes them. */
export function sortRulesPrefs(r: SortRules): Record<string, string> {
  return {
    [SORT_RULES_PREF.maxTop]: String(r.maxTop),
    [SORT_RULES_PREF.maxMid]: String(r.maxMid),
    [SORT_RULES_PREF.maxBulk]: String(r.maxBulk),
    [SORT_RULES_PREF.floor]: String(r.floor),
    [SORT_RULES_PREF.mix]: String(r.mix),
    [SORT_RULES_PREF.weekly]: String(r.weekly),
  };
}

/** The three binders' uids, as far as they're set up (null: that place isn't set up). */
export function sortPlacesOf(p: Record<string, string>): Record<SortPlace, string | null> {
  return { top: p[SORT_PREF.top] || null, mid: p[SORT_PREF.mid] || null, bulk: p[SORT_PREF.bulk] || null };
}

/** True once a sort was applied. */
export const sortSettledOf = (p: Record<string, string>): boolean => p[SORT_SETTLED_PREF] === "true";

/** The declined moves ("<kindKey>><placeKey>"). */
export const sortSkipsOf = (p: Record<string, string>): Set<string> => new Set((p[SORT_SKIPS_PREF] ?? "").split("\n").filter(Boolean));

/** The moves done ("<day>|<kindKey>|<copies>|<from unsorted>"). */
export const sortLogOf = (p: Record<string, string>): string[] => (p[SORT_LOG_PREF] ?? "").split("\n").filter(Boolean);

/** The day of [now], counted in UTC days since 1970, as the log keeps it. */
export const epochDay = (now: number): number => Math.floor(now / 86_400_000);

/** A log line for [copies] of [key] moving on [day], from Unsorted or from a place. */
export const logLine = (day: number, key: string, copies: number, fromUnsorted: boolean) => `${day}|${key}|${copies}|${fromUnsorted ? 1 : 0}`;

/** Whether a log line is from before the last [LOG_DAYS] days, so it can be forgotten. */
export function isOld(line: string, today: number): boolean {
  const day = Number.parseInt(line.slice(0, Math.max(0, line.indexOf("|"))), 10);
  return (Number.isFinite(day) ? day : 0) < today - LOG_DAYS;
}

/** A copy's appeal: its value, plus more for cards Commander players want when [mix] is high. */
export function sortScore(price: number | null | undefined, rank: number | null | undefined, mix: number): number {
  return (price ?? 0) * (1 + popularity(rank ?? null) * Math.pow(mix / 50.0, 2));
}

/** One copy, on its way to a place. */
interface Unit {
  key: string;
  name: string;
  place: string;
  price: number | null;
  rank: number | null;
  score: number;
  fixed: boolean;
}

/** One pass of filling and grouping, with what's needed to weigh and cut its moves. */
interface Run {
  /** Every unit's final place (held copies included). */
  assigned: Map<Unit, string>;
  /** The copies that stay where they are. */
  pinned: Map<Unit, string>;
  moves: SortMove[];
}

const byKey = (a: { key: string }, b: { key: string }) => (a.key < b.key ? -1 : a.key > b.key ? 1 : 0);

/** The most valuable copies first; ties by place (highest first) and then by card (the app's ByValue). */
function byValue(a: Unit, b: Unit): number {
  return (b.price ?? -1) - (a.price ?? -1) || SORT_LEVELS.indexOf(a.place) - SORT_LEVELS.indexOf(b.place) || byKey(a, b);
}

/** Moves from the highest place first, then to the highest, then the most valuable card, then by card. */
function moveOrder(a: SortMove, b: SortMove): number {
  return (
    SORT_LEVELS.indexOf(a.from) - SORT_LEVELS.indexOf(b.from) ||
    SORT_LEVELS.indexOf(a.to) - SORT_LEVELS.indexOf(b.to) ||
    b.score - a.score ||
    byKey(a, b)
  );
}

/**
 * The pure core (CollectionSortPlanner.plan): where every copy goes, from the stacks, the decks' needs, the rules, the
 * declined moves, the log and whether the first sort is done.
 */
export function planSort(input: SortInput): SortPlan {
  const { rules, settled, today } = input;
  const skips = new Set(input.skips ?? []);
  const needs = input.deckNeeds ?? {};
  const log = input.log ?? [];
  const max: Record<string, number> = { top: rules.maxTop, mid: rules.maxMid, bulk: rules.maxBulk };

  // One unit per copy; cards in other binders (a deck's, the trade binder) only count for the decks' needs.
  const units: Unit[] = [];
  for (const s of input.stacks) {
    if (s.place === OTHER_KEY) continue;
    const price = s.price ?? null;
    const rank = s.rank ?? null;
    const score = sortScore(price, rank, rules.mix);
    for (let i = Math.max(0, Math.floor(s.copies)); i > 0; i--) units.push({ key: s.key, name: s.name, place: s.place, price, rank, score, fixed: s.fixed === true });
  }

  // Held units: signed and altered copies, and the copies the decks still need (the most valuable ones).
  const pinned = new Map<Unit, string>();
  for (const u of units) if (u.fixed) pinned.set(u, u.place);
  const outside = new Map<string, number>();
  for (const s of input.stacks) if (s.place === OTHER_KEY) outside.set(nameKey(s.name), (outside.get(nameKey(s.name)) ?? 0) + s.copies);
  for (const [name, need] of Object.entries(needs)) {
    const reserve = Math.max(0, need - (outside.get(name) ?? 0));
    if (reserve <= 0) continue;
    units
      .filter((u) => nameKey(u.name) === name)
      .sort(byValue)
      .slice(0, reserve)
      .forEach((u) => pinned.set(u, u.place));
  }

  // Cards that moved less than eight weeks ago rest where they are.
  if (settled) {
    const recent = new Set<string>();
    for (const line of log) {
      const p = line.split("|");
      const day = Number.parseInt(p[0] ?? "", 10);
      if (Number.isFinite(day) && day > today - REST_DAYS) recent.add(p[1]);
    }
    for (const u of units) if (u.place !== UNSORTED_KEY && recent.has(u.key)) pinned.set(u, u.place);
  }

  // What moved in the last seven days, and how many copies may move now.
  let weekUsed = 0;
  if (settled) {
    for (const line of log) {
      const p = line.split("|");
      const day = Number.parseInt(p[0] ?? "", 10);
      const copies = Number.parseInt(p[2] ?? "", 10);
      if (!Number.isFinite(day) || !Number.isFinite(copies)) continue;
      if (today - day < 7 && p[3] === "0") weekUsed += copies;
    }
  }
  const budget = settled ? Math.max(0, rules.weekly - weekUsed) : -1;

  /** Fills the places and works out the moves; [held] also holds the copies the weekly cap keeps back. */
  const fill = (held: Map<Unit, string>): Run => {
    const filled = new Map<Unit, string>();
    const m = settled ? 1.25 : 1.0;
    const heldCount = new Map<string, number>();
    for (const place of held.values()) heldCount.set(place, (heldCount.get(place) ?? 0) + 1);
    for (const place of SORT_PLACES) {
      // A bulk binder with no limit takes every candidate; the others take as many as they hold.
      const unlimited = place === "bulk" && max[place] === 0;
      const limit = Math.max(0, max[place] - (heldCount.get(place) ?? 0));
      if (limit === 0 && !unlimited) continue;
      const candidates: { u: Unit; adj: number }[] = [];
      for (const u of units) {
        if (filled.has(u) || held.has(u)) continue;
        if (skips.has(`${u.key}>${place}`)) continue;
        // The floor: under it (or without a price) a card is never taken by the top places.
        if (place !== "bulk") {
          const price = u.price;
          const qualifies = price !== null && (price >= rules.floor || (settled && u.place === place && price >= rules.floor / m));
          if (!qualifies) continue;
        }
        candidates.push({ u, adj: u.place === place ? u.score * m : u.score });
      }
      // The copies already in the place come first when their appeal is equal (even held ones).
      candidates.sort((a, b) => b.adj - a.adj || Number(b.u.place === place) - Number(a.u.place === place) || byKey(a.u, b.u));
      const take = unlimited ? candidates.length : Math.min(limit, candidates.length);
      for (let i = 0; i < take; i++) filled.set(candidates[i].u, place);
    }
    // What's left goes to Unsorted, unless that was turned down: then it stays where it is.
    for (const u of units) if (!filled.has(u) && !held.has(u) && !skips.has(`${u.key}>${UNSORTED_KEY}`)) filled.set(u, UNSORTED_KEY);
    const assigned = new Map([...filled, ...held]);
    return { assigned, pinned: held, moves: groupMoves(units, assigned, rules) };
  };

  let run = fill(pinned);
  let heldBack = 0;
  if (settled) {
    // The cap: the most important moves stay as far as they fit; the copies cut wait for next week and stay where
    // they are while the places are filled again.
    const waiting = new Set<Unit>();
    const countCut = (cut: Unit[]) => {
      for (const u of cut) {
        if (waiting.has(u)) continue;
        waiting.add(u);
        heldBack++;
      }
    };
    for (let round = 0; round < 20; round++) {
      const capped = run.moves.filter((mv) => mv.from !== UNSORTED_KEY);
      if (capped.reduce((n, mv) => n + mv.copies, 0) <= budget) break;
      const kept = keepMostImportant(run, units, capped, budget);
      if (kept.cut.length === 0) break;
      countCut(kept.cut);
      run = fill(pinsWith(run, kept.cut));
    }
    // After twenty rounds the moves that fit still go; the copies left over wait for next week.
    const capped = run.moves.filter((mv) => mv.from !== UNSORTED_KEY);
    if (capped.reduce((n, mv) => n + mv.copies, 0) > budget) {
      const kept = keepMostImportant(run, units, capped, budget);
      if (kept.cut.length === 0) {
        run = { ...run, moves: kept.moves };
      } else {
        countCut(kept.cut);
        // The cut copies stay where they are.
        const assigned = new Map(run.assigned);
        for (const u of kept.cut) assigned.set(u, u.place);
        run = { assigned, pinned: run.pinned, moves: kept.moves };
      }
    }
  }

  const after: Record<string, number> = { top: 0, mid: 0, bulk: 0, unsorted: 0 };
  for (const u of units) {
    const place = run.assigned.get(u) ?? u.place;
    after[place] = (after[place] ?? 0) + 1;
  }
  return { moves: run.moves, after, heldBack, weekUsed, budget };
}

/** The lowest score among the copies of [place], or null when it holds none. */
function lowest(run: Run, units: Unit[], place: string): number | null {
  let low: number | null = null;
  for (const u of units) {
    if ((run.assigned.get(u) ?? u.place) !== place) continue;
    if (low === null || u.score < low) low = u.score;
  }
  return low;
}

/**
 * The moves that fit in [budget], the most important first (ties by card), and the copies they cut. How much a move
 * matters: how far apart its card's appeal is from the lowest score among the copies of the higher of its two places
 * (0 when that place holds nothing).
 */
function keepMostImportant(run: Run, units: Unit[], moves: SortMove[], budget: number): { moves: SortMove[]; cut: Unit[] } {
  const importance = (mv: SortMove) => {
    const higher = Math.max(0, Math.min(SORT_LEVELS.indexOf(mv.from), SORT_LEVELS.indexOf(mv.to)));
    const ref = lowest(run, units, SORT_LEVELS[higher]) ?? 0;
    return Math.abs(Math.log((mv.score + 0.01) / (ref + 0.01)));
  };
  // The copies of a move: the same card, from the same place, going to the same place.
  const copiesOf = (mv: SortMove) => units.filter((u) => u.key === mv.key && u.place === mv.from && run.assigned.get(u) === mv.to);
  // The copies that wait for next week: the least valuable, as many as were cut.
  const cutUnits = (mv: SortMove, keep: number) => copiesOf(mv).sort((a, b) => a.score - b.score || byKey(a, b)).slice(0, mv.copies - keep);
  let left = budget;
  const kept: SortMove[] = [];
  const cut: Unit[] = [];
  for (const mv of [...moves].sort((a, b) => importance(b) - importance(a) || byKey(a, b))) {
    const take = Math.min(left, mv.copies);
    left -= take;
    if (take > 0) kept.push({ ...mv, copies: take });
    if (take < mv.copies) cut.push(...cutUnits(mv, take));
  }
  return { moves: kept.sort(moveOrder), cut };
}

/** These copies stay where they are for now. */
function pinsWith(run: Run, extra: Unit[]): Map<Unit, string> {
  const out = new Map(run.pinned);
  for (const u of extra) out.set(u, u.place);
  return out;
}

/**
 * Groups the copies that change place by card and from/to, with their reason: from the highest place first, then to,
 * then the most valuable card.
 */
function groupMoves(units: Unit[], finalPlace: Map<Unit, string>, rules: SortRules): SortMove[] {
  const byMove = new Map<string, { key: string; from: string; to: string; list: Unit[] }>();
  for (const u of units) {
    const to = finalPlace.get(u) ?? u.place;
    if (to === u.place) continue;
    const id = `${u.key}\u0000${u.place}\u0000${to}`;
    const entry = byMove.get(id) ?? { key: u.key, from: u.place, to, list: [] };
    entry.list.push(u);
    byMove.set(id, entry);
  }
  const moves = [...byMove.values()].map(({ key, from, to, list }): SortMove => {
    const first = list[0];
    const reason =
      to === UNSORTED_KEY
        ? "Bulk is full"
        : // Moving down a card under the floor (or without a price): the floor says why.
          SORT_LEVELS.indexOf(to) > SORT_LEVELS.indexOf(from) && (first.price === null || first.price < rules.floor)
          ? `under ${money(rules.floor)}`
          : SORT_LEVELS.indexOf(to) < SORT_LEVELS.indexOf(from)
            ? why(first.price, first.rank, null, money)
            : "made room for more valuable cards";
    return { key, name: first.name, from, to, copies: list.length, price: first.price, score: first.score, reason };
  });
  return moves.sort(moveOrder);
}

// ---- The sync file -------------------------------------------------------------------------------------------------

/** The place each of the three binders is, by uid. */
function placeOfPlace(file: SyncFile): Map<string, string> {
  const uids = sortPlacesOf(prefs(file));
  const out = new Map<string, string>();
  for (const place of SORT_PLACES) if (uids[place]) out.set(uids[place]!, place);
  return out;
}

/** A collection stack's item, as the trade binder's CardKind reads it (finish, condition, language, marks). */
type SortableItem = Pick<CollectionItem, "card" | "foil" | "etched" | "condition" | "language" | "signed" | "altered">;

/** The kind key of a collection stack (the trade binder's CardKind.key). */
const stackKind = (item: SortableItem): string => kindKey(kindOf({ ...item, finish: finishOf(item.foil, item.etched) }));

/** Every stack as the sort sees it: its price as the collection shows it, its EDHREC rank from the details. */
function sortStacks(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): SortStack[] {
  const setup = pricingSetup(prefs(file), priceType(prefs(file).priceType), prices, details, ck);
  const places = placeOfPlace(file);
  const known = new Set(bindersOf(file).map((b) => b.uid));
  const out: SortStack[] = [];
  for (const s of collection(file)) {
    const it = s.item;
    if (!it.card) continue;
    const finish = finishOf(it.foil, it.etched);
    const cm = cardmarketPrice(it.card, it.foil, setup.type, setup.prices);
    out.push({
      key: stackKind(it),
      name: it.card.name,
      // Only Unsorted and the three binders take part; every other binder (a deck's, the trade binder) is left alone.
      place: s.binder === null || !known.has(s.binder) ? UNSORTED_KEY : (places.get(s.binder) ?? OTHER_KEY),
      copies: it.quantity,
      price: unitPrice(setup, it.card.scryfallId, finish, it.condition, cm),
      rank: details?.cards[it.card.scryfallId]?.r ?? null,
      fixed: !!(it.signed || it.altered),
    });
  }
  return out;
}

/** The planner's input from the file: the decks' needs (as for the trade binder) and the synced settings. */
export function sortInputs(file: SyncFile, stacks: SortStack[], now: number): SortInput {
  const p = prefs(file);
  const deckNeeds: Record<string, number> = {};
  for (const d of decksOf(file)) for (const [name, n] of Object.entries(d.cards)) deckNeeds[nameKey(name)] = (deckNeeds[nameKey(name)] ?? 0) + n;
  return { stacks, deckNeeds, rules: sortRulesOf(p), skips: [...sortSkipsOf(p)], log: sortLogOf(p), settled: sortSettledOf(p), today: epochDay(now) };
}

/** The three place binders that exist, by uid and name. */
export function sortBindersOf(file: SyncFile): Record<SortPlace, { uid: string; name: string } | null> {
  const uids = sortPlacesOf(prefs(file));
  const list = bindersOf(file);
  const of = (uid: string | null) => {
    const b = uid === null ? undefined : list.find((x) => x.uid === uid && x.uid);
    return b?.uid ? { uid: b.uid, name: b.name } : null;
  };
  return { top: of(uids.top), mid: of(uids.mid), bulk: of(uids.bulk) };
}

/**
 * The sort's suggestion from the sync file: the stacks (priced as the collection shows them), the decks' needs and
 * the synced rules, skips and log, with the binders of the places.
 */
export function sortSuggest(
  file: SyncFile,
  prices: PriceFile | null,
  details: DetailsFile | null,
  ck?: CkFile | null,
  now: number = Date.now(),
): { plan: SortPlan; binders: Record<SortPlace, { uid: string; name: string } | null> } {
  return { plan: planSort(sortInputs(file, sortStacks(file, prices, details, ck ?? null), now)), binders: sortBindersOf(file) };
}

/** Where a place points: an existing binder, or a new one to make. */
export type SortPlaceChoice = { uid: string } | { newName: string };

/**
 * Makes the chosen binders and remembers the rules: a binder that isn't there yet is made the way the collection page's
 * "New binder…" makes one. Answers the three uids.
 */
export function sortSetUp(file: SyncFile, places: Partial<Record<SortPlace, SortPlaceChoice>>, rules: SortRules, now: number): Record<SortPlace, string | null> {
  const before = sortPlacesOf(prefs(file));
  const uids: Record<SortPlace, string | null> = { top: before.top, mid: before.mid, bulk: before.bulk };
  for (const place of SORT_PLACES) {
    const choice = places[place];
    if (!choice) continue;
    if ("uid" in choice) {
      uids[place] = bindersOf(file).some((b) => b.uid === choice.uid) ? choice.uid : null;
      continue;
    }
    const name = choice.newName.trim();
    uids[place] = applyCollectionEdit(file, { type: "addBinder", name }, now).uid;
  }
  const values: Record<string, string> = { ...sortRulesPrefs(rules) };
  for (const place of SORT_PLACES) if (uids[place]) values[SORT_PREF[place]] = uids[place]!;
  setPrefs(file, values, now);
  return uids;
}

/**
 * Moves the [accepted] copies (Unsorted = no binder) and remembers the [declined] moves. Once anything is accepted,
 * a card gives up its place only to one that's clearly better. The log forgets lines older than ten weeks.
 */
export function sortApply(file: SyncFile, accepted: SortMove[], declined: SortMove[], now: number): void {
  const uids = sortPlacesOf(prefs(file));
  const today = epochDay(now);
  const binderOf = (place: string): string | null | undefined => (place === UNSORTED_KEY ? null : (uids[place as SortPlace] ?? undefined));
  const lines: string[] = [];
  // A card moves out of a place before another card moves into it.
  const order = [...accepted].sort((a, b) => SORT_LEVELS.indexOf(a.from) - SORT_LEVELS.indexOf(b.from));
  for (const move of order) {
    const from = binderOf(move.from);
    const to = binderOf(move.to);
    if (from === undefined || to === undefined || from === to) continue;
    let left = move.copies;
    // The stacks of that kind in the binder it comes from, the biggest first.
    const stacks = collection(file)
      .filter((s) => s.item.uid && s.binder === from && stackKind(s.item) === move.key)
      .sort((a, b) => b.item.quantity - a.item.quantity);
    for (const s of stacks) {
      if (left <= 0) break;
      const n = Math.min(left, s.item.quantity);
      applyCollectionEdit(file, { type: "update", uid: s.item.uid!, binder: to, move: n }, now);
      left -= n;
    }
    if (left < move.copies) lines.push(logLine(today, move.key, move.copies - left, move.from === UNSORTED_KEY));
  }
  const values: Record<string, string> = {};
  if (lines.length > 0) values[SORT_SETTLED_PREF] = "true";
  if (declined.length > 0) {
    const skips = sortSkipsOf(prefs(file));
    for (const d of declined) skips.add(skipKeyOf(d));
    values[SORT_SKIPS_PREF] = [...skips].sort().join("\n");
  }
  if (lines.length > 0 || declined.length > 0) {
    values[SORT_LOG_PREF] = [...sortLogOf(prefs(file)), ...lines].filter((l) => !isOld(l, today)).join("\n");
  }
  if (Object.keys(values).length > 0) setPrefs(file, values, now);
}

/** Forgets the first sort, the rest periods and the turned-down moves. */
export function sortStartOver(file: SyncFile, now: number): void {
  setPrefs(file, { [SORT_SETTLED_PREF]: "false", [SORT_LOG_PREF]: "", [SORT_SKIPS_PREF]: "" }, now);
}
