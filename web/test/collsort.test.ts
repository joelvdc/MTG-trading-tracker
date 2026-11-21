import { readFileSync } from "node:fs";
import crypto from "node:crypto";
import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  DEFAULT_SORT_RULES,
  epochDay,
  isOld,
  logLine,
  planSort,
  REST_DAYS,
  LOG_DAYS,
  SORT_LOG_PREF,
  SORT_SETTLED_PREF,
  SORT_SKIPS_PREF,
  sortApply,
  sortDecksOf,
  sortInputs,
  liveLevels,
  sortLevelsOf,
  sortLogOf,
  sortSettingsOf,
  sortSettingsPrefs,
  sortSetUp,
  sortSettledOf,
  sortSkipsOf,
  sortStartOver,
  sortSuggest,
  type SortInput,
  type SortMove,
  type SortSettings,
} from "@/lib/collsort";
import { applyCollectionEdit } from "@/lib/collection";
import { finishOf } from "@/lib/pricing";
import { kindKey, kindOf } from "@/lib/tradebinder";
import { binders, collection, parse, prefs, setPrefs, type CardRef, type SyncFile, type SyncStack } from "@/lib/syncfile";

// The same cases as the app's sort tests (Release1107SortTest.kt).
interface Move {
  key: string;
  name: string;
  from: string;
  to: string;
  copies: number;
  price: number | null;
  score: number;
  reason: string;
}
const cases = JSON.parse(readFileSync(path.join(__dirname, "fixtures", "collsort-cases.json"), "utf8")) as {
  name: string;
  input: SortInput;
  expected: { moves: Move[]; after: Record<string, number>; heldBack: number; weekUsed: number; budget: number; deckShort: number };
}[];

const line2 = (m: { from: string; to: string; key: string; copies: number }) => `${m.from}>${m.to}:${m.key}:${m.copies}`;

describe("sorting the collection (CollectionSortPlanner)", () => {
  it("has enough cases", () => expect(cases.length).toBeGreaterThanOrEqual(27));

  for (const c of cases)
    it(c.name, () => {
      const got = planSort(c.input);
      expect(got.moves.map(line2)).toEqual(c.expected.moves.map(line2));
      expect(got.moves.map((m) => m.reason)).toEqual(c.expected.moves.map((m) => m.reason));
      for (const want of c.expected.moves) {
        const move = got.moves.find((m) => m.key === want.key && m.from === want.from && m.to === want.to)!;
        expect(move.name).toBe(want.name);
        expect(move.copies).toBe(want.copies);
        expect(move.price).toBe(want.price);
        expect(move.score).toBeCloseTo(want.score, 9);
        expect(move.reason).toBe(want.reason);
        expect(move.reason.length).toBeGreaterThan(0);
      }
      expect(got.after).toEqual(c.expected.after);
      expect(got.heldBack).toBe(c.expected.heldBack);
      expect(got.weekUsed).toBe(c.expected.weekUsed);
      expect(got.budget).toBe(c.expected.budget);
      expect(got.deckShort).toBe(c.expected.deckShort);
    });

  it("writes the moves' reasons in the account's currency when it is given one", () => {
    const withReason = cases.find((c) => c.expected.moves.some((m) => m.reason.includes("€")))!;
    expect(withReason).toBeTruthy();
    const inKroner = planSort(withReason.input, (eur) => `${(eur * 7.46).toFixed(2)} kr.`);
    expect(inKroner.moves.map(line2)).toEqual(withReason.expected.moves.map(line2));
    for (const m of inKroner.moves) expect(m.reason).not.toContain("€");
    // The same moves and the same reasons, in kroner where the euros were.
    expect(inKroner.moves.map((m) => m.reason)).toEqual(planSort(withReason.input).moves.map((m) => m.reason.replace(/€([\d.]+)/g, (_, n) => `${(Number(n) * 7.46).toFixed(2)} kr.`)));
  });
});

/** The sort's default settings (what the newer app writes when nothing else is known). */
const DEFAULT_SETTINGS: SortSettings = { levels: [{ uid: "l1", max: 200, minPrice: 0.5 }, { uid: "l2", max: 1000, minPrice: 0.5 }, { uid: "l3", max: 0, minPrice: null }], decks: null, mix: 50, weekly: 10, together: "name", outlierRatio: 3, outlierGap: 5, deckCopies: "best" };

/** The setup page's settings: the defaults unless given. */
const SETTINGS = { mix: 50, weekly: 10, together: "name" as const, outlierRatio: 3, outlierGap: 5, deckCopies: "best" as const };

describe("the sort's synced settings", () => {
  it("reads and writes the settings, the app's defaults where a phone hasn't sent them", () => {
    expect(sortSettingsOf({})).toEqual({ ...DEFAULT_SETTINGS, levels: [] });
    const s: SortSettings = { levels: [{ uid: "L1", max: 50, minPrice: 1.25 }, { uid: "L2", max: 300, minPrice: null }], decks: "D", mix: 80, weekly: 3, together: "printing", outlierRatio: 4, outlierGap: 2.5, deckCopies: "cheapest" };
    expect(sortSettingsOf(sortSettingsPrefs(s))).toEqual(s);
    expect(sortSettingsOf(sortSettingsPrefs({ ...DEFAULT_SETTINGS, levels: [] })).levels).toEqual([]);
  });

  it("reads the older app's three binders when sortLevels is missing or empty", () => {
    const old = { sortTop: "t1", sortMid: "m1", sortBulk: "b1", sortMaxTop: "40", sortMaxMid: "400", sortMaxBulk: "4000", sortFloor: "0.75" };
    expect(sortSettingsOf(old).levels).toEqual([
      { uid: "t1", max: 40, minPrice: 0.75 },
      { uid: "m1", max: 400, minPrice: 0.75 },
      { uid: "b1", max: 4000, minPrice: null },
    ]);
    // Only the binders that are set up, in that order; missing numbers take the old defaults (200 / 1000 / 0 / 0.5).
    expect(sortSettingsOf({ ...old, sortMid: "" }).levels).toEqual([
      { uid: "t1", max: 40, minPrice: 0.75 },
      { uid: "b1", max: 4000, minPrice: null },
    ]);
    // A bad number takes the default its level had.
    const bad = { sortTop: "t1", sortMaxTop: "x", sortMid: "m1", sortMaxMid: "-2", sortBulk: "b1", sortMaxBulk: "4000", sortFloor: "-1" };
    expect(sortSettingsOf(bad).levels).toEqual([
      { uid: "t1", max: 200, minPrice: 0.5 },
      { uid: "m1", max: 1000, minPrice: 0.5 },
      { uid: "b1", max: 4000, minPrice: null },
    ]);
    // An empty binder is dropped.
    expect(sortSettingsOf({ ...old, sortTop: "" }).levels.map((l) => l.uid)).toEqual(["m1", "b1"]);
    // At most six levels.
    expect(sortSettingsOf({ sortLevels: "a|1|\nb|1|\nc|1|\nd|1|\ne|1|\nf|1|\ng|1|" }).levels).toHaveLength(6);
    // sortLevels wins over the old keys.
    const both = { ...old, sortLevels: "x|1|\ny|2|1.5" };
    expect(sortSettingsOf(both).levels).toEqual([{ uid: "x", max: 1, minPrice: null }, { uid: "y", max: 2, minPrice: 1.5 }]);
  });

  it("leaves out values out of range, like the trade binder's", () => {
    const bad = { ...sortSettingsPrefs(DEFAULT_SETTINGS), sortMix: "200", sortWeekly: "0", sortOutlierRatio: "0.5", sortOutlierGap: "-1", sortTogether: "always", sortDeckCopies: "newest" };
    expect(sortSettingsOf(bad)).toEqual(DEFAULT_SETTINGS);
    // A broken line is left out; a missing minimum price is none.
    const lines = { sortLevels: "a|10|\nb|x|1\nc|20|abc\n|5|1", sortWeekly: "4" };
    expect(sortSettingsOf(lines).levels).toEqual([{ uid: "a", max: 10, minPrice: null }, { uid: "c", max: 20, minPrice: null }]);
  });

  it("writes the older app's keys only with exactly three levels", () => {
    const three = sortSettingsPrefs({ ...DEFAULT_SETTINGS, decks: "D" });
    expect(three).toMatchObject({ sortLevels: "l1|200|0.5\nl2|1000|0.5\nl3|0|", sortDecks: "D", sortTop: "l1", sortMid: "l2", sortBulk: "l3", sortMaxTop: "200", sortMaxMid: "1000", sortMaxBulk: "0", sortFloor: "0.5", sortMix: "50" });
    const two = sortSettingsPrefs({ ...DEFAULT_SETTINGS, levels: DEFAULT_SETTINGS.levels.slice(0, 2) });
    expect(two).not.toHaveProperty("sortTop");
    expect(two).not.toHaveProperty("sortFloor");
    expect(sortSettingsPrefs({ ...DEFAULT_SETTINGS, levels: [], decks: null }).sortDecks).toBe("");
  });

  it("reads the settled flag, the skips and the log", () => {
    const p = { sortSettled: "true", sortSkips: "a>l1\nb>l2", sortLog: "19990|c|2|0" };
    expect(sortSettledOf(p)).toBe(true);
    expect([...sortSkipsOf(p)]).toEqual(["a>l1", "b>l2"]);
    expect(sortLogOf(p)).toEqual(["19990|c|2|0"]);
  });

  it("keeps the log in UTC days and forgets lines older than 70 days", () => {
    expect(epochDay(20000 * 86_400_000 + 5)).toBe(20000);
    expect(logLine(20000, "k", 2, true)).toBe("20000|k|2|1");
    // 56 days is still resting, 70 days is forgotten.
    expect(isOld(logLine(20000 - REST_DAYS + 1, "k", 1, false), 20000)).toBe(false);
    expect(isOld(logLine(20000 - LOG_DAYS, "k", 1, false), 20000)).toBe(false);
    expect(isOld(logLine(20000 - LOG_DAYS - 1, "k", 1, false), 20000)).toBe(true);
  });

  it("has the app's defaults", () => {
    expect(DEFAULT_SORT_RULES.levels.map((l) => [l.key, l.max, l.minPrice])).toEqual([["l1", 200, 0.5], ["l2", 1000, 0.5], ["l3", 0, null]]);
    expect(DEFAULT_SORT_RULES).toMatchObject({ mix: 50, weekly: 10, together: "name", outlierRatio: 3, outlierGap: 5, deckCopies: "best" });
  });
});

const card = (name: string, id = name): CardRef => ({
  // A Scryfall id made from the name (the collection only takes ids that look like Scryfall's).
  scryfallId: `${crypto.createHash("md5").update(id).digest("hex").replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, "$1-$2-$3-$4-$5")}`,
  name,
  setCode: "abc",
  setName: "Set",
  collectorNumber: "1",
  rarity: "rare",
  imageUrl: null,
  cardmarketId: null,
  fallbackEur: 1,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: false,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
});

const appFile = () => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));

/** The sample file with a few more piles: two more Sol Rings and a card of its own in Unsorted. */
function file(): SyncFile {
  const f = appFile();
  const existing = collection(f)[0].item;
  applyCollectionEdit(f, { type: "add", card: existing.card, quantity: 2 }, 2000);
  applyCollectionEdit(f, { type: "add", card: card("Cheap Bear"), quantity: 4 }, 2000);
  return f;
}

/** The kind key of a stack (what the trade binder calls a copy-kind). */
const stackKindOf = (s: SyncStack) => kindKey(kindOf({ ...s.item, finish: finishOf(s.item.foil, s.item.etched) }));

/** The example file's own card kind (Sol Ring, its first stack). */
const EXISTING = "11111111-1111-1111-1111-111111111111|false|false|NM|EN";

/** The stacks in a binder (null: Unsorted), as [name, copies]. */
const pilesOf = (f: SyncFile, uid: string | null) => collection(f).filter((s) => s.binder === uid).map((s) => [s.item.card.name, s.item.quantity] as const);

describe("the sort on the sync file", () => {
  it("makes the binders and remembers them with the settings", () => {
    const f = file();
    const settings = sortSetUp(f, [{ choice: { newName: "Collection binder" }, max: 50, minPrice: 0.5 }, { choice: { newName: "Not bulk" }, max: 1000, minPrice: 0.5 }], null, { ...SETTINGS, weekly: 4 }, 3000);
    const names = binders(f).map((b) => b.name);
    expect(names).toContain("Collection binder");
    expect(names).toContain("Not bulk");
    expect(settings.levels).toHaveLength(2);
    expect(settings.decks).toBeNull();
    const p = prefs(f);
    expect(p.sortLevels).toBe(`${settings.levels[0].uid}|50|0.5\n${settings.levels[1].uid}|1000|0.5`);
    // Two levels: the older app versions' keys aren't written (they only knew three).
    expect(p.sortTop).toBeUndefined();
    expect(sortSettingsOf(p)).toEqual(settings);
    expect(sortLevelsOf(f).map((l) => [l.key, l.name, l.max])).toEqual([["l1", "Collection binder", 50], ["l2", "Not bulk", 1000]]);
    // Three levels do write them, so the older phones keep their sort.
    sortSetUp(f, [...sortLevelsOf(f).map((l) => ({ choice: { uid: l.uid }, max: l.max, minPrice: l.minPrice })), { choice: { newName: "Bulk" }, max: 0, minPrice: null }], { newName: "Decks" }, SETTINGS, 3001);
    const three = prefs(f).sortLevels.split("\n");
    expect(prefs(f).sortTop).toBe(three[0].split("|")[0]);
    expect(prefs(f).sortMid).toBe(three[1].split("|")[0]);
    expect(prefs(f).sortBulk).toBe(three[2].split("|")[0]);
    expect(prefs(f).sortDecks).toBe(sortDecksOf(f)!.uid);
    expect(prefs(f).sortFloor).toBe("0.5");
    expect(prefs(f).sortMaxBulk).toBe("0");
  });

  it("refuses settings that can't work, and makes no binder for them", () => {
    const f = file();
    sortSetUp(f, [{ choice: { newName: "A" }, max: 10, minPrice: null }], null, SETTINGS, 3000);
    const before = binders(f).length;
    expect(() => sortSetUp(f, [], null, SETTINGS, 3000)).toThrow(/at least one level/);
    expect(() => sortSetUp(f, Array.from({ length: 7 }, (_, i) => ({ choice: { newName: `L${i}` }, max: 10, minPrice: null })), null, SETTINGS, 3000)).toThrow(/six/);
    expect(() => sortSetUp(f, [{ choice: { newName: "B" }, max: 0, minPrice: null }, { choice: { newName: "C" }, max: 10, minPrice: null }], null, SETTINGS, 3000)).toThrow(/last level/);
    expect(() => sortSetUp(f, [{ choice: { newName: "D" }, max: -1, minPrice: null }], null, SETTINGS, 3000)).toThrow(/can't be below 0/);
    const main = binders(f)[0].uid!;
    expect(() => sortSetUp(f, [{ choice: { uid: main }, max: 10, minPrice: null }], { uid: main }, SETTINGS, 3000)).toThrow(/same binder/);
    expect(binders(f)).toHaveLength(before);
  });

  it("suggests the moves from the file, priced as the collection shows it", () => {
    const f = appFile();
    const existing = collection(f)[0].item;
    // The example file's price list has no prices, so its saved price (€1.50) does for Sol Ring; Cheap Bear has none.
    applyCollectionEdit(f, { type: "add", card: existing.card, quantity: 2 }, 2000);
    applyCollectionEdit(f, { type: "add", card: card("Cheap Bear"), quantity: 4 }, 2000);
    sortSetUp(f, [{ choice: { newName: "Collection binder" }, max: 1, minPrice: 0.5 }, { choice: { newName: "Not bulk" }, max: 1000, minPrice: 0.5 }, { choice: { newName: "Bulk" }, max: 0, minPrice: null }], null, { ...SETTINGS, together: "off" }, 3000);
    const { plan, levels, decks } = sortSuggest(f, null, null, null, 3000);
    expect(levels.map((l) => l.name)).toEqual(["Collection binder", "Not bulk", "Bulk"]);
    expect(decks).toBeNull();
    expect(plan.budget).toBe(-1);
    // The deck (Vohar Danse) uses one Sol Ring: one copy stays home; the rest go by value (the minimum is on the first
    // two levels only, so a cheap card still fits Not bulk).
    expect(plan.moves.map((m) => [m.name, m.from, m.to, m.copies])).toEqual([
      ["Sol Ring", "unsorted", "l1", 1],
      ["Sol Ring", "unsorted", "l2", 1],
      ["Cheap Bear", "unsorted", "l2", 4],
    ]);
    expect(plan.after).toEqual({ l1: 1, l2: 5, l3: 0, decks: 0, unsorted: 0 });
  });

  it("moves the accepted copies to the decks and the levels, writes the log and the skips, and settles", () => {
    const f = file();
    const settings = sortSetUp(f, [{ choice: { newName: "Collection binder" }, max: 200, minPrice: 0.5 }, { choice: { newName: "Not bulk" }, max: 1000, minPrice: 0.5 }, { choice: { newName: "Bulk" }, max: 0, minPrice: null }], { newName: "Decks" }, SETTINGS, 3000);
    const [l1, , l3] = settings.levels.map((l) => l.uid);
    const decks = settings.decks!;
    const day = epochDay(4000 * 86_400_000);
    // The moves come from the plan: their kind keys are the stacks' (the trade binder's CardKind). The deck's Sol Ring
    // comes from Unsorted and the dearest one goes up to the first level; the second level is refused for it (it waits).
    const key = EXISTING;
    const bearKey = stackKindOf(collection(f).find((s) => s.item.card.name === "Cheap Bear" && s.binder === null)!);
    const toDeck: SortMove = { key, name: "Sol Ring", from: "unsorted", to: "decks", copies: 1, price: 1.5, score: 1.5, reason: "for your decks" };
    const up: SortMove = { key, name: "Sol Ring", from: "unsorted", to: "l1", copies: 1, price: 1.5, score: 1.5, reason: "€1.50" };
    const declined: SortMove = { ...up, copies: 2, to: "l2" };
    sortApply(f, [toDeck, up], [declined], 4000 * 86_400_000);
    expect(pilesOf(f, l1).map(([n, q]) => [n, q])).toEqual([["Sol Ring", 1]]);
    expect(pilesOf(f, decks).map(([n, q]) => [n, q])).toEqual([["Sol Ring", 1]]);
    expect(pilesOf(f, null).map(([n, q]) => [n, q])).toEqual([["Cheap Bear", 4]]);
    const p = prefs(f);
    expect(p[SORT_SETTLED_PREF]).toBe("true");
    // Into Decks and out of Unsorted never count against the weekly limit; both moves are logged.
    expect(sortLogOf(p)).toEqual([`${day}|${key}|1|1`, `${day}|${key}|1|1`]);
    expect([...sortSkipsOf(p)]).toEqual([`${key}>l2`]);

    // A decline alone leaves the log (and the settled flag) as it is.
    sortApply(f, [], [declined], 4001 * 86_400_000);
    expect(sortLogOf(prefs(f))).toHaveLength(2);
    expect(sortLogOf(prefs(f))).toEqual(sortLogOf(p));
    expect(p[SORT_SETTLED_PREF]).toBe("true");

    // Out of Decks and out of the first level, and into Decks: the deck swaps Sol Ring for Cheap Bear (a move that
    // counts neither way), and the Sol Ring in the first level goes to Bulk.
    const swap: SortMove = { key, name: "Sol Ring", from: "decks", to: "l3", copies: 1, price: 1.5, score: 1.5, reason: "no longer in an active deck" };
    const intoDecks: SortMove = { key: bearKey, name: "Cheap Bear", from: "unsorted", to: "decks", copies: 1, price: 1, score: 1, reason: "for your decks" };
    const down: SortMove = { key, name: "Sol Ring", from: "l1", to: "l3", copies: 1, price: 1.5, score: 1.5, reason: "made room for more valuable cards" };
    sortApply(f, [intoDecks, swap, down], [], 4002 * 86_400_000);
    expect(pilesOf(f, decks).map(([n, q]) => [n, q])).toEqual([["Cheap Bear", 1]]);
    expect(pilesOf(f, l1)).toHaveLength(0);
    expect(pilesOf(f, l3).map(([n, q]) => [n, q])).toEqual([["Sol Ring", 2]]);
    // From Decks and into Decks don't count (1); the move out of the first level does (0). The card leaves before
    // another moves in, so the move out of the first level is logged first.
    expect(sortLogOf(prefs(f)).slice(2)).toEqual([
      `${epochDay(4002 * 86_400_000)}|${key}|1|1`,
      `${epochDay(4002 * 86_400_000)}|${key}|1|0`,
      `${epochDay(4002 * 86_400_000)}|${bearKey}|1|1`,
    ]);
  });

  it("moves copies to Unsorted, and forgets old log lines when it writes", () => {
    const f = file();
    const settings = sortSetUp(f, [{ choice: { newName: "Bulk" }, max: 0, minPrice: null }], null, SETTINGS, 3000);
    const bulk = settings.levels[0].uid;
    setPrefs(f, { [SORT_LOG_PREF]: logLine(epochDay(4000 * 86_400_000) - 80, "old", 1, false) }, 3000);
    // The one Cheap Bear pile that isn't in a deck's binder (the example file has one).
    const bear = collection(f).find((s) => s.item.card.name === "Cheap Bear" && s.binder === null)!;
    const bearKey = stackKindOf(bear);
    const move: SortMove = { key: bearKey, name: "Cheap Bear", from: "unsorted", to: "l1", copies: 1, price: 0.2, score: 0.2, reason: "€0.20" };
    sortApply(f, [move], [], 4000 * 86_400_000);
    expect(pilesOf(f, null).map(([n, q]) => [n, q])).toContainEqual(["Cheap Bear", 3]);
    expect(sortLogOf(prefs(f))).toEqual([logLine(epochDay(4000 * 86_400_000), move.key, 1, true)]);

    // And back again: from the Bulk binder to Unsorted (no binder).
    const back: SortMove = { ...move, from: "l1", to: "unsorted", reason: "no room" };
    sortApply(f, [back], [], 5000 * 86_400_000);
    expect(pilesOf(f, bulk)).toHaveLength(0);
    expect(pilesOf(f, null).map(([n, q]) => [n, q])).toContainEqual(["Cheap Bear", 4]);
  });

  it("reads the older app's declined moves as the levels they are now", () => {
    const f = file();
    sortSetUp(f, [{ choice: { newName: "Collection binder" }, max: 200, minPrice: 0.5 }, { choice: { newName: "Not bulk" }, max: 1000, minPrice: 0.5 }, { choice: { newName: "Bulk" }, max: 0, minPrice: null }], null, SETTINGS, 3000);
    setPrefs(f, { [SORT_SKIPS_PREF]: "a>top\nb>mid\nc>bulk\nd>unsorted\ne>decks" }, 3000);
    expect(sortInputs(f, [], 4000).skips).toEqual(["a>l1", "b>l2", "c>l3", "d>unsorted", "e>decks"]);
    // Two levels: the old bulk is the last one.
    sortSetUp(f, [{ choice: { newName: "A" }, max: 200, minPrice: 0.5 }, { choice: { newName: "B" }, max: 0, minPrice: null }], null, SETTINGS, 3001);
    expect(sortInputs(f, [], 4000).skips.slice(2, 3)).toEqual(["c>l2"]);
    // The settings themselves keep the old keys as they were (nothing is rewritten).
    expect(prefs(f)[SORT_SKIPS_PREF]).toBe("a>top\nb>mid\nc>bulk\nd>unsorted\ne>decks");
  });

  it("leaves out a level whose binder was deleted, and keys the rest by the levels that are there", () => {
    const f = file();
    const settings = sortSetUp(f, [{ choice: { newName: "Collection binder" }, max: 200, minPrice: 0.5 }, { choice: { newName: "Not bulk" }, max: 1000, minPrice: 0.5 }, { choice: { newName: "Bulk" }, max: 0, minPrice: null }], null, SETTINGS, 3000);
    const [l1, l2, l3] = settings.levels.map((l) => l.uid);
    // The middle binder goes: the two left are the levels, "l2" now being the third one.
    applyCollectionEdit(f, { type: "deleteBinder", uid: l2, deleteCards: false }, 3001);
    expect(liveLevels(f).map((l) => l.uid)).toEqual([l1, l3]);
    expect(sortLevelsOf(f).map((l) => [l.key, l.uid])).toEqual([["l1", l1], ["l2", l3]]);
    const inputs = sortInputs(f, [], 4000);
    expect(inputs.rules.levels).toEqual([
      { key: "l1", max: 200, minPrice: 0.5 },
      { key: "l2", max: 0, minPrice: null },
    ]);
    // The stacks' places follow: what was in the third binder is "l2" now, and nothing is "l3".
    expect(sortSuggest(f, null, null, null, 4000).plan.after).not.toHaveProperty("l3");
    expect(Object.keys(sortSuggest(f, null, null, null, 4000).plan.after).sort()).toEqual(["decks", "l1", "l2", "unsorted"]);

    // A move to "l2" goes to the third binder (the one that's still there).
    const bear = collection(f).find((s) => s.item.card.name === "Cheap Bear" && s.binder === null)!;
    const move: SortMove = { key: stackKindOf(bear), name: "Cheap Bear", from: "unsorted", to: "l2", copies: 1, price: 0.2, score: 0.2, reason: "€0.20" };
    sortApply(f, [move], [], 4000 * 86_400_000);
    expect(pilesOf(f, l3).map(([n, q]) => [n, q])).toContainEqual(["Cheap Bear", 1]);
    expect(pilesOf(f, l1)).toHaveLength(0);
  });

  it("starts over: no settle, no log, no skips", () => {
    const f = file();
    setPrefs(f, { [SORT_SETTLED_PREF]: "true", [SORT_LOG_PREF]: "19990|k|1|0", [SORT_SKIPS_PREF]: "k>l1" }, 3000);
    sortStartOver(f, 4000);
    const p = prefs(f);
    expect(p[SORT_SETTLED_PREF]).toBe("false");
    expect(p[SORT_LOG_PREF]).toBe("");
    expect(p[SORT_SKIPS_PREF]).toBe("");
    expect(sortSettledOf(p)).toBe(false);
    expect(sortSkipsOf(p).size).toBe(0);
    expect(sortInputs(f, [], 4000).settled).toBe(false);
  });
});
