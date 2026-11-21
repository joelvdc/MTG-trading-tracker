// Sort the collection (the app's CollectionSort.kt, since 1.104; the second version, 1.107): splits the collection into
// one to six levels (the owner's own binders, best first), an optional Decks binder and Unsorted, with the best cards
// first. Value and how much Commander players want a card (EDHREC rank) say where it goes. Once the first sort is done
// it keeps the result steady: a card gives up its place only to one that's clearly better, rests eight weeks after a
// move, and only so many copies move a week. Copies of a card may go together (all printings of it, or one printing),
// except a copy that's much more valuable, which goes its own way. The copies the active decks use go to "decks".
//
// planSort is the pure core (the app's CollectionSortPlanner.plan, tested by collsort-cases.json); sortSuggest,
// sortSetUp, sortApply and sortStartOver read and write the sync file the way tradebinder.ts does. The settings, the
// settled flag, the declined moves, the log and the binders' uids are synced settings (since app 1.104, extended in
// 1.107): sortLevels ("<uid>|<max>|<minPrice>", best first), sortDecks, sortTogether, sortOutlierRatio, sortOutlierGap,
// sortDeckCopies, sortMix, sortWeekly, sortSettled, sortSkips, sortLog. sortSetUp also writes the older app versions'
// settings (sortTop, sortMid, sortBulk, sortMaxTop, sortMaxMid, sortMaxBulk, sortFloor) while there are three levels.
import type { CkFile } from "./cardkingdom";
import type { DetailsFile } from "./carddetails";
import { applyCollectionEdit } from "./collection";
import { activeDecksOf } from "./collview";
import { format } from "./money";
import type { PriceFile } from "./prices";
import { priceType } from "./prices";
import { cardmarketPrice, finishOf, pricingSetup, unitPrice } from "./pricing";
import { binders as bindersOf, collection, prefs, setPrefs, type CollectionItem, type SyncFile } from "./syncfile";
import { kindKey, kindOf, nameKey, popularity, why } from "./tradebinder";

/** Unsorted's key in the plan: never synced, it's the app's built-in Unsorted (no binder). */
export const UNSORTED_KEY = "unsorted";
/** Any binder that isn't one of the places (a deck's, the trade binder, …): never touched, only counted. */
export const OTHER_KEY = "other";
/** The Decks binder's key in the plan. */
export const DECKS_KEY = "decks";
/** A level's key: "l1" is the best one. */
export const levelKey = (i: number) => `l${i + 1}`;

/** Whether [place] is one of the levels ("l1", "l2"…). */
export const isLevelKey = (place: string) => /^l[0-9]+$/.test(place);

/** The places in order, from the highest (Decks) down (the app's ORDER); [idx] looks a place up in it. */
export function orderOf(levelCount: number): string[] {
  return [DECKS_KEY, ...Array.from({ length: Math.max(0, levelCount) }, (_, i) => levelKey(i)), UNSORTED_KEY];
}
export const idx = (place: string, order: readonly string[]): number => {
  const i = order.indexOf(place);
  return i < 0 ? order.length : i;
};

/** How copies of a card go: each alone, each printing alone, or every printing of the card name together. */
export type SortTogether = "off" | "printing" | "name";

/** Which copies the decks take: the most valuable, or the cheapest. */
export type SortDeckCopies = "best" | "cheapest";

/** The synced settings' keys. */
export const SORT_LEVELS_PREF = "sortLevels";
export const SORT_DECKS_PREF = "sortDecks";
export const SORT_TOGETHER_PREF = "sortTogether";
export const SORT_OUTLIER_RATIO_PREF = "sortOutlierRatio";
export const SORT_OUTLIER_GAP_PREF = "sortOutlierGap";
export const SORT_DECK_COPIES_PREF = "sortDeckCopies";
export const SORT_MIX_PREF = "sortMix";
export const SORT_WEEKLY_PREF = "sortWeekly";
export const SORT_SETTLED_PREF = "sortSettled";
export const SORT_SKIPS_PREF = "sortSkips";
export const SORT_LOG_PREF = "sortLog";
/** The older app versions' settings (up to 1.106), still written while there are exactly three levels. */
export const SORT_OLD_LEVEL_PREF = { top: "sortTop", mid: "sortMid", bulk: "sortBulk" } as const;
export const SORT_OLD_MAX_PREF = { maxTop: "sortMaxTop", maxMid: "sortMaxMid", maxBulk: "sortMaxBulk" } as const;
export const SORT_OLD_FLOOR_PREF = "sortFloor";

/** A moved card rests this many days (eight weeks). */
export const REST_DAYS = 56;
/** Log lines older than this many days are forgotten. */
export const LOG_DAYS = 70;

/** One level: how many copies its binder holds, and the cheapest card it takes. */
export interface SortLevelRule {
  key: string;
  /** Copies it holds; 0 on the LAST level = no limit, 0 elsewhere = holds nothing. */
  max: number;
  /** Under this price (or without a price) a card never goes to this level (the trade binder's minimum's money). */
  minPrice: number | null;
}

/** How the sort works: the levels, how copies go together, how it weighs cards and how far it goes. */
export interface SortRules {
  levels: SortLevelRule[];
  /** 0 = by value only, 50 = like the trade binder, 100 = popularity counts most. */
  mix: number;
  /** Most copies that may move in seven days once the first sort is done. */
  weekly: number;
  together: SortTogether;
  /** A copy this many times as valuable as the others is much more valuable … */
  outlierRatio: number;
  /** … and that many euros or more above them. */
  outlierGap: number;
  deckCopies: SortDeckCopies;
}

export const DEFAULT_SORT_RULES: SortRules = {
  levels: [
    { key: levelKey(0), max: 200, minPrice: 0.5 },
    { key: levelKey(1), max: 1000, minPrice: 0.5 },
    { key: levelKey(2), max: 0, minPrice: null },
  ],
  mix: 50,
  weekly: 10,
  together: "name",
  outlierRatio: 3,
  outlierGap: 5,
  deckCopies: "best",
};

/** One card's copies in one place: a stack of the collection. [fixed] = signed or altered. */
export interface SortStack {
  key: string;
  name: string;
  /** A level's key, [DECKS_KEY], [UNSORTED_KEY], or [OTHER_KEY] (a deck's binder, the trade binder…). */
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
  /** Every level's key, [DECKS_KEY] and [UNSORTED_KEY], 0 when empty. */
  after: Record<string, number>;
  /** Copies whose move waits for next week (the weekly cap). */
  heldBack: number;
  /** Copies that moved in the last seven days, against the cap. */
  weekUsed: number;
  /** How many copies may move now (-1 before the first sort, or when the cap is ignored). */
  budget: number;
  /** Copies the active decks need that you don't own. */
  deckShort: number;
}

/** The planner's input, as the shared cases give it. */
export interface SortInput {
  stacks: SortStack[];
  /** Per nameKey (front face, lowercased, trimmed): copies the active decks use. */
  deckNeeds: Record<string, number>;
  rules: SortRules;
  skips: string[];
  log: string[];
  settled: boolean;
  /** The UTC epoch day. */
  today: number;
  /** A Decks binder is set up: the decks' copies go there. */
  decksPlace?: boolean;
  /** Ignore the weekly limit for this run. */
  ignoreCap?: boolean;
}

const euroMoney = (eur: number) => format(eur, "EUR");

/**
 * How a move's reason writes money: euros unless the account shows its prices in another currency (the pages pass
 * `(eur) => format(eur * perEuro, currency)`, as they do for every price).
 */
export type MoneyFormat = (eur: number) => string;

/** A level as the owner sets it up: its binder by uid, how many copies and the cheapest card it takes. */
export interface SortLevelSetting {
  uid: string;
  max: number;
  minPrice: number | null;
}

/** The sort's synced settings, as the setup page shows them. */
export interface SortSettings {
  levels: SortLevelSetting[];
  /** The Decks binder's uid (null: no Decks place). */
  decks: string | null;
  mix: number;
  weekly: number;
  together: SortTogether;
  outlierRatio: number;
  outlierGap: number;
  deckCopies: SortDeckCopies;
}

/** A line of [SORT_LEVELS_PREF]: "<uid>|<max>|<minPrice or empty>". */
const levelLine = (l: SortLevelSetting) => `${l.uid}|${l.max}|${l.minPrice === null ? "" : String(l.minPrice)}`;

const togetherOf = (v: string | undefined): SortTogether | null => (v === "off" || v === "printing" || v === "name" ? v : null);
const deckCopiesOf = (v: string | undefined): SortDeckCopies | null => (v === "best" || v === "cheapest" ? v : null);

/**
 * The sort's settings (the app's defaults where a phone hasn't sent them, checked like the trade binder's). Before
 * 1.107 there were only sortTop, sortMid and sortBulk with sortMaxTop, sortMaxMid, sortMaxBulk and sortFloor: when
 * sortLevels is missing or empty those make the levels.
 */
export function sortSettingsOf(p: Record<string, string>): SortSettings {
  const int = (key: string, d: number, ok: (n: number) => boolean) => {
    const n = Number.parseInt(p[key] ?? "", 10);
    return Number.isFinite(n) && ok(n) ? n : d;
  };
  const levels: SortLevelSetting[] = [];
  for (const line of (p[SORT_LEVELS_PREF] ?? "").split("\n").slice(0, 6)) {
    if (!line.trim()) continue;
    const [uid, max, min] = line.split("|");
    if (!uid) continue;
    const n = Number.parseInt(max ?? "", 10);
    if (!Number.isFinite(n) || n < 0) continue;
    const minPrice = min === undefined || min.trim() === "" ? null : Number.parseFloat(min);
    levels.push({ uid, max: n, minPrice: minPrice !== null && Number.isFinite(minPrice) && minPrice >= 0 ? minPrice : null });
  }
  if (levels.length === 0) {
    // An older app's settings: its three binders, as far as they're set up, in that order.
    const d = DEFAULT_SORT_RULES;
    const old = [
      { uid: SORT_OLD_LEVEL_PREF.top, max: SORT_OLD_MAX_PREF.maxTop, minPrice: SORT_OLD_FLOOR_PREF },
      { uid: SORT_OLD_LEVEL_PREF.mid, max: SORT_OLD_MAX_PREF.maxMid, minPrice: SORT_OLD_FLOOR_PREF },
      { uid: SORT_OLD_LEVEL_PREF.bulk, max: SORT_OLD_MAX_PREF.maxBulk, minPrice: null },
    ];
    for (const o of old) {
      const uid = (p[o.uid] ?? "").trim();
      if (!uid) continue;
      const def = d.levels[old.indexOf(o)];
      const max = int(o.max, def.max, (n) => n >= 0);
      let minPrice: number | null = null;
      if (o.minPrice !== null) {
        const floor = Number.parseFloat(p[o.minPrice] ?? "");
        minPrice = Number.isFinite(floor) && floor >= 0 ? floor : def.minPrice;
      }
      levels.push({ uid, max, minPrice });
    }
  }
  const ratio = Number.parseFloat(p[SORT_OUTLIER_RATIO_PREF] ?? "");
  const gap = Number.parseFloat(p[SORT_OUTLIER_GAP_PREF] ?? "");
  return {
    levels,
    decks: p[SORT_DECKS_PREF] || null,
    mix: int(SORT_MIX_PREF, DEFAULT_SORT_RULES.mix, (n) => n >= 0 && n <= 100),
    weekly: int(SORT_WEEKLY_PREF, DEFAULT_SORT_RULES.weekly, (n) => n >= 1),
    together: togetherOf(p[SORT_TOGETHER_PREF]) ?? DEFAULT_SORT_RULES.together,
    outlierRatio: Number.isFinite(ratio) && ratio >= 1 ? ratio : DEFAULT_SORT_RULES.outlierRatio,
    outlierGap: Number.isFinite(gap) && gap >= 0 ? gap : DEFAULT_SORT_RULES.outlierGap,
    deckCopies: deckCopiesOf(p[SORT_DECK_COPIES_PREF]) ?? DEFAULT_SORT_RULES.deckCopies,
  };
}

/** The settings as the app writes them (the older app versions' keys too, while there are exactly three levels). */
export function sortSettingsPrefs(s: SortSettings): Record<string, string> {
  const values: Record<string, string> = {
    [SORT_LEVELS_PREF]: s.levels.map(levelLine).join("\n"),
    [SORT_DECKS_PREF]: s.decks ?? "",
    [SORT_MIX_PREF]: String(s.mix),
    [SORT_WEEKLY_PREF]: String(s.weekly),
    [SORT_TOGETHER_PREF]: s.together,
    [SORT_OUTLIER_RATIO_PREF]: String(s.outlierRatio),
    [SORT_OUTLIER_GAP_PREF]: String(s.outlierGap),
    [SORT_DECK_COPIES_PREF]: s.deckCopies,
  };
  if (s.levels.length === 3) {
    const [l1, l2, l3] = s.levels;
    values[SORT_OLD_LEVEL_PREF.top] = l1.uid;
    values[SORT_OLD_LEVEL_PREF.mid] = l2.uid;
    values[SORT_OLD_LEVEL_PREF.bulk] = l3.uid;
    values[SORT_OLD_MAX_PREF.maxTop] = String(l1.max);
    values[SORT_OLD_MAX_PREF.maxMid] = String(l2.max);
    values[SORT_OLD_MAX_PREF.maxBulk] = String(l3.max);
    values[SORT_OLD_FLOOR_PREF] = String(l1.minPrice ?? 0);
  }
  return values;
}

/**
 * The settings' levels whose binder still exists, in order: a binder that was deleted since it was picked is gone,
 * and the levels after it move up (so "l2" is always the second level that's there).
 */
export function liveLevels(file: SyncFile): SortLevelSetting[] {
  const known = new Set(bindersOf(file).map((b) => b.uid));
  return sortSettingsOf(prefs(file)).levels.filter((l) => known.has(l.uid));
}

/** The planner's rules from the settings (level keys by position). */
export function sortRulesFrom(s: SortSettings): SortRules {
  return {
    levels: s.levels.map((l, i) => ({ key: levelKey(i), max: l.max, minPrice: l.minPrice })),
    mix: s.mix,
    weekly: s.weekly,
    together: s.together,
    outlierRatio: s.outlierRatio,
    outlierGap: s.outlierGap,
    deckCopies: s.deckCopies,
  };
}

/** True once a sort was applied. */
export const sortSettledOf = (p: Record<string, string>): boolean => p[SORT_SETTLED_PREF] === "true";

/** The declined moves ("<kindKey>><placeKey>"). */
export const sortSkipsOf = (p: Record<string, string>): Set<string> => new Set((p[SORT_SKIPS_PREF] ?? "").split("\n").filter(Boolean));

/** The moves done ("<day>|<kindKey>|<copies>|<exempt>"). */
export const sortLogOf = (p: Record<string, string>): string[] => (p[SORT_LOG_PREF] ?? "").split("\n").filter(Boolean);

/** The day of [now], counted in UTC days since 1970, as the log keeps it. */
export const epochDay = (now: number): number => Math.floor(now / 86_400_000);

/** A log line for [copies] of [key] moving on [day]; [exempt]: it doesn't count against the weekly limit. */
export const logLine = (day: number, key: string, copies: number, exempt: boolean) => `${day}|${key}|${copies}|${exempt ? 1 : 0}`;

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

/** Copies that go together, with the price and score that level's floor and order use. */
interface Group {
  units: Unit[];
  /** Null when every copy has no price. */
  price: number | null;
  score: number;
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
function byValue(a: Unit, b: Unit, order: readonly string[]): number {
  return (b.price ?? -1) - (a.price ?? -1) || idx(a.place, order) - idx(b.place, order) || byKey(a, b);
}

/** The decks' copies by what they're worth (a copy without a price: -1), dearest first; once [settled], a copy already in Decks is worth a quarter more. */
function byWorthDesc(a: Unit, b: Unit, settled: boolean): number {
  const worth = (u: Unit) => (u.price ?? -1) * (settled && u.place === DECKS_KEY ? 1.25 : 1);
  return worth(b) - worth(a);
}

/** The decks' copies by what they cost (a copy without a price: 0), cheapest first; once [settled], a copy already in Decks costs a quarter less. */
function byWorthAsc(a: Unit, b: Unit, settled: boolean): number {
  const worth = (u: Unit) => (u.price ?? 0) / (settled && u.place === DECKS_KEY ? 1.25 : 1);
  return worth(a) - worth(b);
}

/** Moves from the highest place first, then to the highest, then the most valuable card, then by card. */
function moveOrder(a: SortMove, b: SortMove, order: readonly string[]): number {
  return idx(a.from, order) - idx(b.from, order) || idx(a.to, order) - idx(b.to, order) || b.score - a.score || byKey(a, b);
}

/** The lower median of some prices (null counts as 0): the sorted list's index (n - 1) / 2. */
function lowerMedian(prices: (number | null)[]): number {
  const sorted = prices.map((x) => x ?? 0).sort((x, y) => x - y);
  return sorted[Math.floor((sorted.length - 1) / 2)] ?? 0;
}

/** Whether [a] goes in before [b]: the better appeal, then the copies already here, then by card. */
function comesBefore(a: { adjusted: number; anchored: boolean; key: string }, b: { adjusted: number; anchored: boolean; key: string }): boolean {
  if (a.adjusted !== b.adjusted) return a.adjusted > b.adjusted;
  if (a.anchored !== b.anchored) return a.anchored;
  return a.key < b.key;
}

/**
 * The pure core (CollectionSortPlanner.plan): where every copy goes, from the stacks, the decks' needs, the rules, the
 * declined moves, the log and whether the first sort is done. [moneyFormat] writes the moves' reasons (euros by default).
 */
export function planSort(input: SortInput, moneyFormat: MoneyFormat = euroMoney): SortPlan {
  const { rules, settled, today } = input;
  const skips = new Set(input.skips ?? []);
  const needs = input.deckNeeds ?? {};
  const log = input.log ?? [];
  const levels = rules.levels;
  const last = levels.length - 1;
  const order = orderOf(levels.length);
  const decksPlace = input.decksPlace === true;
  const ignoreCap = input.ignoreCap === true;

  // One unit per copy; cards in other binders (a deck's, the trade binder) only count for the decks' needs.
  const units: Unit[] = [];
  for (const s of input.stacks) {
    if (s.place === OTHER_KEY) continue;
    const price = s.price ?? null;
    const rank = s.rank ?? null;
    const score = sortScore(price, rank, rules.mix);
    for (let i = Math.max(0, Math.floor(s.copies)); i > 0; i--) units.push({ key: s.key, name: s.name, place: s.place, price, rank, score, fixed: s.fixed === true });
  }

  // Pinned: the signed and altered copies stay where they are.
  const pinned = new Map<Unit, string>();
  for (const u of units) if (u.fixed) pinned.set(u, u.place);

  // The decks' needs. Copies already in other binders (a deck's own) count for them.
  const outside = new Map<string, number>();
  for (const s of input.stacks) if (s.place === OTHER_KEY) outside.set(nameKey(s.name), (outside.get(nameKey(s.name)) ?? 0) + s.copies);
  const deckUnits = new Set<Unit>();
  let deckShort = 0;
  for (const [name, need] of Object.entries(needs)) {
    let reserve = Math.max(0, need - (outside.get(name) ?? 0));
    if (reserve <= 0) continue;
    const same = units.filter((u) => nameKey(u.name) === name);
    if (!decksPlace) {
      same
        .sort((a, b) => byValue(a, b, order))
        .slice(0, reserve)
        .forEach((u) => pinned.set(u, u.place));
      deckShort += Math.max(0, reserve - same.length);
      continue;
    }
    // A signed copy already in Decks does for one of them.
    for (const u of same) if (u.fixed && u.place === DECKS_KEY && reserve > 0) reserve = Math.max(0, reserve - 1);
    // The most valuable copies (or the cheapest), then the copy already in Decks, then the highest place, then by card.
    const byDeckPlace = (a: Unit, b: Unit) => Number(b.place === DECKS_KEY) - Number(a.place === DECKS_KEY) || idx(a.place, order) - idx(b.place, order) || byKey(a, b);
    const candidates = same
      .filter((u) => !u.fixed && !pinned.has(u))
      .sort((a, b) => (rules.deckCopies === "best" ? byWorthDesc(a, b, settled) : byWorthAsc(a, b, settled)) || byDeckPlace(a, b));
    for (const u of candidates.slice(0, reserve)) {
      deckUnits.add(u);
      pinned.set(u, DECKS_KEY);
    }
    deckShort += Math.max(0, reserve - candidates.length);
  }

  // Cards that moved less than eight weeks ago rest where they are (in a level; a deck's copies have their place).
  if (settled) {
    const recent = new Set<string>();
    for (const line of log) {
      const p = line.split("|");
      const day = Number.parseInt(p[0] ?? "", 10);
      if (Number.isFinite(day) && day > today - REST_DAYS) recent.add(p[1]);
    }
    for (const u of units) if (isLevelKey(u.place) && !pinned.has(u) && recent.has(u.key)) pinned.set(u, u.place);
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
  const budget = settled && !ignoreCap ? Math.max(0, rules.weekly - weekUsed) : -1;

  /** Fills the levels and works out the moves; [held] also holds the copies the weekly cap keeps back. */
  const fill = (held: Map<Unit, string>): Run => {
    /** Copies that go together, in the order their first copy comes in. */
    interface Pack {
      units: Unit[];
      key: string;
      price: number | null;
      score: number;
      /** This level's score for them: a quarter more when some of them are already there (once settled). */
      adjusted: number;
      anchored: boolean;
    }
    const packs: Pack[] = [];
    const packAt = new Map<string, Pack>();
    for (const u of units) {
      if (held.has(u) || deckUnits.has(u)) continue;
      const id = rules.together === "off" ? `${u.key}\u0000${packs.length}` : rules.together === "printing" ? u.key : nameKey(u.name);
      const pack = packAt.get(id);
      if (pack) pack.units.push(u);
      else {
        const made: Pack = { units: [u], key: u.key, price: null, score: 0, adjusted: 0, anchored: false };
        packAt.set(id, made);
        packs.push(made);
      }
    }
    // Much more valuable copies are outliers: they go their own way, as packs of their own, right after the pack they
    // left (in the order their first copy comes in).
    const pool: Pack[] = [];
    for (const pack of packs) {
      if (rules.together === "off" || pack.units.length < 2) {
        pool.push(pack);
        continue;
      }
      const usual = lowerMedian(pack.units.map((u) => u.price));
      const own = new Map<string, Pack>();
      for (const u of pack.units) {
        const outlier = u.price !== null && u.price >= rules.outlierRatio * usual && u.price - usual >= rules.outlierGap;
        if (!outlier) continue;
        const o = own.get(u.key) ?? { units: [], key: u.key, price: null, score: 0, adjusted: 0, anchored: false };
        o.units.push(u);
        own.set(u.key, o);
      }
      const left = { ...pack, units: pack.units.filter((u) => ![...own.values()].some((o) => o.units.includes(u))) };
      pool.push(left, ...own.values());
    }
    for (const pack of pool) {
      pack.price = pack.units.length === 0 || pack.units.every((u) => u.price === null) ? null : lowerMedian(pack.units.map((u) => u.price));
      pack.score = pack.price === null ? 0 : sortScore(pack.price, pack.units[0].rank, rules.mix);
    }
    const filled = new Map<Unit, string>();
    const taken = new Set<Pack>();
    const m = settled ? 1.25 : 1.0;
    // The copies already in a level (signed ones, the decks' copies) fill it first: they leave less room for others.
    const heldIn = new Map<string, number>();
    for (const place of held.values()) heldIn.set(place, (heldIn.get(place) ?? 0) + 1);
    for (let i = 0; i < levels.length; i++) {
      const rule = levels[i];
      const place = levelKey(i);
      const unlimited = i === last && rule.max === 0;
      const limit = Math.max(0, rule.max - (heldIn.get(place) ?? 0));
      if (limit === 0 && !unlimited) continue;
      // What may go in: a pack whose copies weren't all turned down for this level, and that is worth it.
      const here: Pack[] = [];
      for (const pack of pool) {
        if (pack.units.length === 0 || taken.has(pack)) continue;
        if (pack.units.some((u) => skips.has(`${u.key}>${place}`))) continue;
        const anchored = pack.units.some((u) => u.place === place);
        if (rule.minPrice !== null) {
          const price = pack.price;
          if (price === null || !(price >= rule.minPrice || (settled && anchored && price >= rule.minPrice / m))) continue;
        }
        pack.anchored = anchored;
        pack.adjusted = anchored ? pack.score * m : pack.score;
        here.push(pack);
      }
      // The best appeal first, then the copies already here, then by card.
      here.sort((a, b) => (comesBefore(a, b) ? -1 : comesBefore(b, a) ? 1 : 0));
      let used = 0;
      const chosen: Pack[] = [];
      for (const pack of here) {
        const size = pack.units.length;
        if (unlimited || used + size <= limit) {
          chosen.push(pack);
          taken.add(pack);
          used += size;
          continue;
        }
        // A pack with a copy already in this level and copies of its own to bring makes room for them: without its
        // newcomers it would have fitted, and the packs not of this level, taken last, make the room.
        if (!settled || !pack.anchored) continue;
        const incumbents = pack.units.filter((u) => u.place === place).length;
        if (incumbents === pack.units.length || used + incumbents > limit) continue;
        const room = limit - used;
        const turnedOut: Pack[] = [];
        let freed = 0;
        for (let k = chosen.length - 1; k >= 0 && freed + room < size; k--) {
          if (chosen[k].anchored) continue;
          turnedOut.push(chosen[k]);
          freed += chosen[k].units.length;
        }
        if (freed + room < size) continue;
        for (const p of turnedOut) {
          chosen.splice(chosen.indexOf(p), 1);
          taken.delete(p);
          used -= p.units.length;
        }
        chosen.push(pack);
        taken.add(pack);
        used += size;
      }
      for (const pack of chosen) for (const u of pack.units) filled.set(u, place);
    }
    // What's left goes to Unsorted, unless that was turned down: then it stays where it is.
    for (const u of units) if (!filled.has(u) && !held.has(u) && !deckUnits.has(u) && !skips.has(`${u.key}>${UNSORTED_KEY}`)) filled.set(u, UNSORTED_KEY);
    const assigned = new Map([...filled, ...held]);
    for (const u of deckUnits) assigned.set(u, DECKS_KEY);
    return { assigned, pinned: held, moves: groupMoves(units, assigned, rules, order, moneyFormat) };
  };

  let run = fill(pinned);
  let heldBack = 0;
  if (budget >= 0) {
    // The cap: the most important moves stay as far as they fit; the copies cut wait for next week and stay where
    // they are while the levels are filled again.
    const waiting = new Set<Unit>();
    const countCut = (cut: Unit[]) => {
      for (const u of cut) {
        if (waiting.has(u)) continue;
        waiting.add(u);
        heldBack++;
      }
    };
    const cappedOf = (r: Run) => r.moves.filter((mv) => isLevelKey(mv.from) && mv.to !== DECKS_KEY);
    const cappedTotal = (r: Run) => cappedOf(r).reduce((n, mv) => n + mv.copies, 0);
    for (let round = 0; round < 20; round++) {
      if (cappedTotal(run) <= budget) break;
      const kept = keepMostImportant(run, units, cappedOf(run), budget, order);
      if (kept.cut.length === 0) break;
      countCut(kept.cut);
      run = fill(pinsWith(run, kept.cut));
    }
    // After twenty rounds the moves that fit still go; the copies left over wait for next week.
    if (cappedTotal(run) > budget) {
      const kept = keepMostImportant(run, units, cappedOf(run), budget, order);
      if (kept.cut.length === 0) {
        run = { ...run, moves: groupMoves(units, run.assigned, rules, order, moneyFormat) };
      } else {
        countCut(kept.cut);
        // The cut copies stay where they are.
        const assigned = new Map(run.assigned);
        for (const u of kept.cut) assigned.set(u, u.place);
        run = { assigned, pinned: run.pinned, moves: groupMoves(units, assigned, rules, order, moneyFormat) };
      }
    }
  }

  const after: Record<string, number> = {};
  for (const place of [...levels.map((_, i) => levelKey(i)), DECKS_KEY, UNSORTED_KEY]) after[place] = 0;
  for (const u of units) {
    const place = run.assigned.get(u) ?? u.place;
    after[place] = (after[place] ?? 0) + 1;
  }
  return { moves: run.moves, after, heldBack, weekUsed, budget, deckShort };
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
function keepMostImportant(run: Run, units: Unit[], moves: SortMove[], budget: number, order: readonly string[]): { moves: SortMove[]; cut: Unit[] } {
  const importance = (mv: SortMove) => {
    const higher = Math.max(0, Math.min(idx(mv.from, order), idx(mv.to, order)));
    const ref = lowest(run, units, order[higher]) ?? 0;
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
  return { moves: kept.sort((a, b) => moveOrder(a, b, order)), cut };
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
function groupMoves(units: Unit[], finalPlace: Map<Unit, string>, rules: SortRules, order: readonly string[], money: MoneyFormat): SortMove[] {
  const byMove = new Map<string, { key: string; from: string; to: string; list: Unit[] }>();
  for (const u of units) {
    const to = finalPlace.get(u) ?? u.place;
    if (to === u.place) continue;
    const id = `${u.key}\u0000${u.place}\u0000${to}`;
    const entry = byMove.get(id) ?? { key: u.key, from: u.place, to, list: [] };
    entry.list.push(u);
    byMove.set(id, entry);
  }
  const nameOf = (u: Unit) => nameKey(u.name);
  // The cards whose copies leave Decks (they went to a better copy), and whose go in.
  const leavingDeck = new Set(units.filter((u) => u.place === DECKS_KEY && (finalPlace.get(u) ?? u.place) !== DECKS_KEY).map(nameOf));
  const intoDeck = new Set(units.filter((u) => u.place !== DECKS_KEY && finalPlace.get(u) === DECKS_KEY).map(nameOf));
  const levelAt = (place: string): SortLevelRule | null => {
    for (let i = 0; i < rules.levels.length; i++) if (levelKey(i) === place) return rules.levels[i];
    return null;
  };
  const moves = [...byMove.values()].map(({ key, from, to, list }): SortMove => {
    const first = list[0];
    const name = nameOf(first);
    const reason =
      to === DECKS_KEY
        ? leavingDeck.has(name)
          ? "upgrade for your decks"
          : "for your decks"
        : from === DECKS_KEY
          ? (intoDeck.has(name) ? "swapped for a better copy" : "no longer in an active deck") + (to === UNSORTED_KEY ? " · no room" : "")
          : to === UNSORTED_KEY
            ? "no room"
            : // Moving down a card under the level's minimum (or without a price): the minimum says why.
              (() => {
                const rule = levelAt(from);
                if (idx(to, order) > idx(from, order) && rule && rule.minPrice !== null && (first.price === null || first.price < rule.minPrice))
                  return `under ${money(rule.minPrice)}`;
                if (idx(to, order) < idx(from, order)) return why(first.price, first.rank, null, money);
                return "made room for more valuable cards";
              })();
    return { key, name: first.name, from, to, copies: list.length, price: first.price, score: first.score, reason };
  });
  return moves.sort((a, b) => moveOrder(a, b, order));
}

// ---- The sync file -------------------------------------------------------------------------------------------------

/** The place each binder is, by uid: every live level's (by its position among them) and Decks'. */
function placeOfUid(levels: SortLevelSetting[], decks: string | null): Map<string, string> {
  const out = new Map<string, string>();
  levels.forEach((l, i) => out.set(l.uid, levelKey(i)));
  if (decks) out.set(decks, DECKS_KEY);
  return out;
}

/** A collection stack's item, as the trade binder's CardKind reads it (finish, condition, language, marks). */
type SortableItem = Pick<CollectionItem, "card" | "foil" | "etched" | "condition" | "language" | "signed" | "altered">;

/** The kind key of a collection stack (the trade binder's CardKind.key). */
const stackKind = (item: SortableItem): string => kindKey(kindOf({ ...item, finish: finishOf(item.foil, item.etched) }));

/** Every stack as the sort sees it: its price as the collection shows it, its EDHREC rank from the details. */
function sortStacks(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): SortStack[] {
  const setup = pricingSetup(prefs(file), priceType(prefs(file).priceType), prices, details, ck);
  const places = placeOfUid(liveLevels(file), sortSettingsOf(prefs(file)).decks);
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
      // Only Unsorted, the levels and Decks take part; every other binder (a deck's, the trade binder) is left alone.
      place: s.binder === null || !known.has(s.binder) ? UNSORTED_KEY : (places.get(s.binder) ?? OTHER_KEY),
      copies: it.quantity,
      price: unitPrice(setup, it.card.scryfallId, finish, it.condition, cm),
      rank: details?.cards[it.card.scryfallId]?.r ?? null,
      fixed: !!(it.signed || it.altered),
    });
  }
  return out;
}

/** A declined move from before 1.107 ("<key>>top", ">mid", ">bulk") says the level it now is. */
function skipMigration(skips: Iterable<string>, levelCount: number): string[] {
  const rename = (place: string) => (place === "top" ? levelKey(0) : place === "mid" ? levelKey(1) : place === "bulk" ? levelKey(Math.max(0, levelCount - 1)) : place);
  return [...skips].map((k) => {
    const at = k.lastIndexOf(">");
    return at < 0 ? k : k.slice(0, at + 1) + rename(k.slice(at + 1));
  });
}

/** The planner's input from the file: the active decks' needs and the synced settings. */
export function sortInputs(file: SyncFile, stacks: SortStack[], now: number): SortInput {
  const p = prefs(file);
  const settings = sortSettingsOf(p);
  // Only the levels whose binder is there: a deleted binder's level is gone, and the others move up.
  const rules = sortRulesFrom({ ...settings, levels: liveLevels(file) });
  const deckNeeds: Record<string, number> = {};
  for (const d of activeDecksOf(file)) for (const [name, n] of Object.entries(d.cards)) deckNeeds[nameKey(name)] = (deckNeeds[nameKey(name)] ?? 0) + n;
  const known = new Set(bindersOf(file).map((b) => b.uid));
  return {
    stacks,
    deckNeeds,
    rules,
    skips: skipMigration(sortSkipsOf(p), rules.levels.length),
    log: sortLogOf(p),
    settled: sortSettledOf(p),
    today: epochDay(now),
    decksPlace: !!settings.decks && known.has(settings.decks),
    ignoreCap: false,
  };
}

/** A binder with its uid and name. */
export interface SortBinder {
  uid: string;
  name: string;
}

/** The levels that exist, best first, with their binder's name and how many copies and the cheapest card it takes. */
export function sortLevelsOf(file: SyncFile): (SortBinder & { key: string; max: number; minPrice: number | null })[] {
  const list = bindersOf(file);
  return liveLevels(file).map((l, i) => {
    const key = levelKey(i);
    const b = list.find((x) => x.uid === l.uid && x.uid);
    return b?.uid ? { key, uid: b.uid, name: b.name, max: l.max, minPrice: l.minPrice } : null;
  }).filter((l): l is SortBinder & { key: string; max: number; minPrice: number | null } => l !== null);
}

/** The Decks binder, when there is one. */
export function sortDecksOf(file: SyncFile): SortBinder | null {
  const uid = sortSettingsOf(prefs(file)).decks;
  const b = uid === null ? undefined : bindersOf(file).find((x) => x.uid === uid && x.uid);
  return b?.uid ? { uid: b.uid, name: b.name } : null;
}

/** What the sort's page and the weekly email need: the plan, what the sort has to send it from and to, and the settings. */
export interface SortSuggestion {
  plan: SortPlan;
  levels: (SortBinder & { key: string; max: number; minPrice: number | null })[];
  decks: SortBinder | null;
  settings: SortSettings;
  /** The account has an Archidekt username, so it knows which followed decks are its own. */
  ownUserSet: boolean;
}

/**
 * The sort's suggestion from the sync file: the stacks (priced as the collection shows them), the active decks' needs
 * and the synced settings, with the binders of the levels and Decks. [moneyFormat] writes the moves' reasons (euros by
 * default); [ignoreCap] ignores the weekly limit for this run.
 */
export function sortSuggest(
  file: SyncFile,
  prices: PriceFile | null,
  details: DetailsFile | null,
  ck?: CkFile | null,
  now: number = Date.now(),
  moneyFormat: MoneyFormat = euroMoney,
  ignoreCap = false,
): SortSuggestion {
  const p = prefs(file);
  const input = { ...sortInputs(file, sortStacks(file, prices, details, ck ?? null), now), ignoreCap };
  return {
    plan: planSort(input, moneyFormat),
    levels: sortLevelsOf(file),
    decks: sortDecksOf(file),
    settings: sortSettingsOf(p),
    ownUserSet: !!(p.ownArchidektUser ?? "").trim(),
  };
}

/** Where a place points: an existing binder, or a new one to make. */
export type SortPlaceChoice = { uid: string } | { newName: string };

/** The settings' values the setup page sends (the binders come apart). */
export interface SortSettingsInput {
  mix: number;
  weekly: number;
  together: SortTogether;
  outlierRatio: number;
  outlierGap: number;
  deckCopies: SortDeckCopies;
}

/**
 * Makes the chosen binders and remembers the settings: a binder that isn't there yet is made the way the collection
 * page's "New binder…" makes one. Throws an Error (for the page to show) when the settings can't work: no level or
 * more than six, the same binder twice, a negative limit, or a level that holds nothing before the last one.
 */
export function sortSetUp(
  file: SyncFile,
  levels: { choice: SortPlaceChoice; max: number; minPrice: number | null }[],
  decks: SortPlaceChoice | null,
  rest: SortSettingsInput,
  now: number,
): SortSettings {
  if (levels.length === 0) throw new Error("Set up at least one level.");
  if (levels.length > 6) throw new Error("There can be at most six levels.");
  for (let i = 0; i < levels.length; i++) {
    const l = levels[i];
    if (!Number.isInteger(l.max) || l.max < 0) throw new Error("The copies a level holds can't be below 0.");
    if (l.max === 0 && i < levels.length - 1) throw new Error(`Level ${i + 1} can't hold nothing: only the last level may have no limit.`);
  }
  const used = [...levels.map((l) => ("uid" in l.choice ? l.choice.uid : null)), decks && "uid" in decks ? decks.uid : null].filter((u): u is string => u !== null).map((u) => u.toLowerCase());
  if (new Set(used).size !== used.length) throw new Error("Two places can't use the same binder.");
  const levelsMade = levels.map((l) => ({ choice: l.choice, max: l.max, minPrice: l.minPrice }));
  if (decks) levelsMade.push({ choice: decks, max: 0, minPrice: null });
  // A binder that's gone since it was picked is as good as none: it becomes no level (or no Decks place).
  const known = new Set(bindersOf(file).map((b) => b.uid));
  // A new binder is made first; the checks below need a binder that stays (and leave a new name alone when they fail).
  const made = levelsMade.map((l) => {
    if ("uid" in l.choice) return { uid: l.choice.uid, max: l.max, minPrice: l.minPrice };
    const uid = applyCollectionEdit(file, { type: "addBinder", name: l.choice.newName.trim() }, now).uid ?? "";
    if (uid) known.add(uid);
    return { uid, max: l.max, minPrice: l.minPrice };
  });
  const kept = (l: { uid: string; max: number; minPrice: number | null }) => l.uid !== "" && known.has(l.uid);
  const levelSettings: SortLevelSetting[] = made.slice(0, levels.length).filter(kept).map(({ uid, max, minPrice }) => ({ uid, max, minPrice }));
  const decksUid = decks && kept(made[levels.length]) ? made[levels.length].uid : null;
  if (levelSettings.length === 0) throw new Error("Set up at least one level.");
  // A no-limit level whose binder has gone leaves a last level with no limit: the check above made sure that's the last too.
  if (levels[levels.length - 1].max === 0 && levelSettings[levelSettings.length - 1].max !== 0) throw new Error("The level with no limit must be the last one.");
  const settings: SortSettings = { levels: levelSettings, decks: decksUid, ...rest };
  setPrefs(file, sortSettingsPrefs(settings), now);
  return settings;
}

/**
 * Moves the [accepted] copies (Unsorted = no binder) and remembers the [declined] moves. Once anything is accepted,
 * a card gives up its place only to one that's clearly better. The log forgets lines older than ten weeks.
 */
export function sortApply(file: SyncFile, accepted: SortMove[], declined: SortMove[], now: number): void {
  const settings = sortSettingsOf(prefs(file));
  const known = new Set(bindersOf(file).map((b) => b.uid && b.uid));
  const levels = liveLevels(file).map((l) => l.uid);
  const order = orderOf(levels.length);
  const today = epochDay(now);
  const binderOf = (place: string): string | null | undefined => {
    if (place === UNSORTED_KEY) return null;
    if (place === DECKS_KEY) return settings.decks && known.has(settings.decks) ? settings.decks : undefined;
    if (!isLevelKey(place)) return undefined;
    return levels[Number.parseInt(place.slice(1), 10) - 1];
  };
  const lines: string[] = [];
  // A card moves out of a place before another card moves into it.
  const at = (place: string) => idx(place, order);
  const sequence = [...accepted].sort((a, b) => at(a.from) - at(b.from) || at(a.to) - at(b.to));
  for (const move of sequence) {
    // Unsorted is null (no binder), a place that isn't set up is undefined.
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
    // Out of Unsorted, out of Decks, and into Decks, the cap doesn't count.
    if (left < move.copies) lines.push(logLine(today, move.key, move.copies - left, move.from === UNSORTED_KEY || move.from === DECKS_KEY || move.to === DECKS_KEY));
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
