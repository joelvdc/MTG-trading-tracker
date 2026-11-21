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
  sortInputs,
  sortLogOf,
  sortPlacesOf,
  sortRulesOf,
  sortRulesPrefs,
  sortSetUp,
  sortSettledOf,
  sortSkipsOf,
  sortStartOver,
  sortSuggest,
  type SortInput,
  type SortMove,
  type SortRules,
} from "@/lib/collsort";
import { applyCollectionEdit } from "@/lib/collection";
import { finishOf } from "@/lib/pricing";
import { kindKey, kindOf } from "@/lib/tradebinder";
import { binders, collection, parse, prefs, setPrefs, type CardRef, type SyncFile } from "@/lib/syncfile";

// The same cases as the app's Release1104SortTest.kt.
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
  expected: { moves: Move[]; after: Record<string, number>; heldBack: number; weekUsed: number; budget: number };
}[];

const line = (m: { from: string; to: string; key: string; copies: number }) => `${m.from}>${m.to}:${m.key}:${m.copies}`;

describe("sorting the collection (CollectionSortPlanner)", () => {
  it("has enough cases", () => expect(cases.length).toBeGreaterThanOrEqual(12));

  for (const c of cases)
    it(c.name, () => {
      const got = planSort(c.input);
      expect(got.moves.map(line)).toEqual(c.expected.moves.map(line));
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
    });

  it("writes the moves' reasons in the account's currency when it is given one", () => {
    const withReason = cases.find((c) => c.expected.moves.some((m) => m.reason.includes("€")))!;
    expect(withReason).toBeTruthy();
    const inKroner = planSort(withReason.input, (eur) => `${(eur * 7.46).toFixed(2)} kr.`);
    expect(inKroner.moves.map(line)).toEqual(withReason.expected.moves.map(line));
    for (const m of inKroner.moves) expect(m.reason).not.toContain("€");
    // The same moves and the same reasons, in kroner where the euros were.
    expect(inKroner.moves.map((m) => m.reason)).toEqual(planSort(withReason.input).moves.map((m) => m.reason.replace(/€([\d.]+)/g, (_, n) => `${(Number(n) * 7.46).toFixed(2)} kr.`)));
  });
});

describe("the sort's synced settings", () => {
  it("reads and writes the rules, the app's defaults where a phone hasn't sent them", () => {
    expect(sortRulesOf({})).toEqual(DEFAULT_SORT_RULES);
    const r: SortRules = { maxTop: 50, maxMid: 300, maxBulk: 20, floor: 1.25, mix: 80, weekly: 3 };
    expect(sortRulesOf(sortRulesPrefs(r))).toEqual(r);
  });

  it("leaves out numbers out of range, like the trade binder's", () => {
    const bad = { ...sortRulesPrefs({ maxTop: 50, maxMid: 300, maxBulk: 20, floor: 1.25, mix: 80, weekly: 3 }), sortMix: "200", sortWeekly: "0", sortFloor: "-1", sortMaxMid: "x" };
    expect(sortRulesOf(bad)).toEqual({ ...DEFAULT_SORT_RULES, maxTop: 50, maxBulk: 20 });
  });

  it("reads the binders, the settled flag, the skips and the log", () => {
    const p = { sortTop: "t1", sortMid: "m1", sortBulk: "", sortSettled: "true", sortSkips: "a>top\nb>mid", sortLog: "19990|c|2|0" };
    expect(sortPlacesOf(p)).toEqual({ top: "t1", mid: "m1", bulk: null });
    expect(sortSettledOf(p)).toBe(true);
    expect([...sortSkipsOf(p)]).toEqual(["a>top", "b>mid"]);
    expect(sortLogOf(p)).toEqual(["19990|c|2|0"]);
    expect(sortPlacesOf({})).toEqual({ top: null, mid: null, bulk: null });
  });

  it("keeps the log in UTC days and forgets lines older than 70 days", () => {
    expect(epochDay(20000 * 86_400_000 + 5)).toBe(20000);
    expect(logLine(20000, "k", 2, true)).toBe("20000|k|2|1");
    // 56 days is still resting, 70 days is forgotten.
    expect(isOld(logLine(20000 - REST_DAYS + 1, "k", 1, false), 20000)).toBe(false);
    expect(isOld(logLine(20000 - LOG_DAYS, "k", 1, false), 20000)).toBe(false);
    expect(isOld(logLine(20000 - LOG_DAYS - 1, "k", 1, false), 20000)).toBe(true);
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

/** The example file's own card kind (Sol Ring, its first stack). */
const EXISTING = "11111111-1111-1111-1111-111111111111|false|false|NM|EN";

/** The sample file with a few more piles: two more Sol Rings and a card of its own in Unsorted. */
function file(): SyncFile {
  const f = appFile();
  const existing = collection(f)[0].item;
  applyCollectionEdit(f, { type: "add", card: existing.card, quantity: 2 }, 2000);
  applyCollectionEdit(f, { type: "add", card: card("Cheap Bear"), quantity: 4 }, 2000);
  return f;
}

/** The kind key of a stack (what the trade binder calls a copy-kind). */
const stackKindOf = (item: { card: CardRef; foil: boolean; etched: boolean; condition: string; language: string; signed: boolean; altered: boolean }) =>
  kindKey(kindOf({ ...item, finish: finishOf(item.foil, item.etched) }));

/** The stacks in a binder (null: Unsorted), as [name, copies]. */
const pilesOf = (f: SyncFile, uid: string | null) => collection(f).filter((s) => s.binder === uid).map((s) => [s.item.card.name, s.item.quantity] as const);

describe("the sort on the sync file", () => {
  it("makes the binders and remembers them with the rules", () => {
    const f = file();
    const uids = sortSetUp(f, { top: { newName: "Collection binder" }, mid: { newName: "Not bulk" } }, { ...DEFAULT_SORT_RULES, maxTop: 50 }, 3000);
    expect(uids.top).not.toBeNull();
    expect(uids.mid).not.toBeNull();
    expect(uids.bulk).toBeNull();
    expect(binders(f).map((b) => b.name)).toContain("Collection binder");
    expect(binders(f).map((b) => b.name)).toContain("Not bulk");
    const p = prefs(f);
    expect(p.sortTop).toBe(uids.top);
    expect(p.sortMid).toBe(uids.mid);
    expect("sortBulk" in p).toBe(false);
    expect(sortRulesOf(p).maxTop).toBe(50);
    // The two new binders are the places; Bulk isn't set up.
    expect(sortPlacesOf(p)).toEqual({ top: uids.top, mid: uids.mid, bulk: null });
  });

  it("uses an existing binder by uid, and refuses one that's gone", () => {
    const f = file();
    const main = binders(f)[0].uid!;
    const uids = sortSetUp(f, { top: { uid: main }, mid: { uid: "gone" } }, DEFAULT_SORT_RULES, 3000);
    expect(uids).toEqual({ top: main, mid: null, bulk: null });
    expect(prefs(f).sortMid).toBeUndefined();
  });

  it("suggests the moves from the file, priced as the collection shows it", () => {
    const f = appFile();
    const existing = collection(f)[0].item;
    // The example file's price list has no prices, so its saved price (€1.50) does for Sol Ring; Cheap Bear has
    // none: no price is never under the floor either.
    applyCollectionEdit(f, { type: "add", card: existing.card, quantity: 2 }, 2000);
    applyCollectionEdit(f, { type: "add", card: card("Cheap Bear"), quantity: 4 }, 2000);
    sortSetUp(f, { top: { newName: "Collection binder" } }, { ...DEFAULT_SORT_RULES, maxTop: 1, maxMid: 0 }, 3000);
    const { plan, binders: places } = sortSuggest(f, null, null, null, 3000);
    expect(places.top?.name).toBe("Collection binder");
    expect(plan.budget).toBe(-1);
    // The deck (Vohar Danse) uses one Sol Ring: one copy stays home, the rest go by value.
    expect(plan.moves.map((m) => [m.name, m.from, m.to, m.copies])).toEqual([
      ["Sol Ring", "unsorted", "top", 1],
      ["Sol Ring", "unsorted", "bulk", 1],
      ["Cheap Bear", "unsorted", "bulk", 4],
    ]);
    expect(plan.after).toEqual({ top: 1, mid: 0, bulk: 5, unsorted: 0 });
  });

  it("moves the accepted copies, writes the log and the skips, and settles", () => {
    const f = file();
    const uids = sortSetUp(f, { top: { newName: "Collection binder" }, mid: { newName: "Not bulk" } }, DEFAULT_SORT_RULES, 3000);
    const day = epochDay(4000);
    const key = EXISTING;
    const top: SortMove = { key, name: "Sol Ring", from: "unsorted", to: "top", copies: 2, price: 1.5, score: 1.5, reason: "€1.50" };
    const declined: SortMove = { key, name: "Sol Ring", from: "unsorted", to: "bulk", copies: 5, price: 1.5, score: 1.5, reason: "€1.50" };
    sortApply(f, [top], [declined], 4000);
    expect(pilesOf(f, uids.top!)).toEqual([["Sol Ring", 2]]);
    const p = prefs(f);
    expect(p[SORT_SETTLED_PREF]).toBe("true");
    expect(sortLogOf(p)).toEqual([`${day}|${key}|2|1`]);
    expect([...sortSkipsOf(p)]).toEqual([`${key}>bulk`]);

    // A decline alone leaves the log (and the settled flag) as it is.
    sortApply(f, [], [declined], 5000);
    expect(sortLogOf(prefs(f))).toHaveLength(1);
    expect(p[SORT_SETTLED_PREF]).toBe("true");

    // Out of the top binder again ("made room for more valuable cards"): one copy to Not bulk, one to Unsorted.
    const down: SortMove = { key, name: "Sol Ring", from: "top", to: "mid", copies: 1, price: 1.5, score: 1.5, reason: "made room for more valuable cards" };
    const out: SortMove = { key, name: "Sol Ring", from: "top", to: "unsorted", copies: 1, price: 1.5, score: 1.5, reason: "Bulk is full" };
    sortApply(f, [down, out], [], 6000);
    expect(pilesOf(f, uids.mid!)).toEqual([["Sol Ring", 1]]);
    expect(pilesOf(f, uids.top!)).toHaveLength(0);
    expect(pilesOf(f, null).map(([n, q]) => [n, q])).toContainEqual(["Sol Ring", 1]);
    expect(sortLogOf(prefs(f))).toEqual([`${day}|${key}|2|1`, `${epochDay(6000)}|${key}|1|0`, `${epochDay(6000)}|${key}|1|0`]);
  });

  it("moves copies to Unsorted, and forgets old log lines when it writes", () => {
    const f = file();
    sortSetUp(f, { bulk: { newName: "Bulk" } }, DEFAULT_SORT_RULES, 3000);
    setPrefs(f, { [SORT_LOG_PREF]: logLine(epochDay(4000) - 80, "old", 1, false) }, 3000);
    const bear = collection(f).find((s) => s.item.card.name === "Cheap Bear")!.item;
    const bearKey = stackKindOf(bear);
    const move: SortMove = { key: bearKey, name: "Cheap Bear", from: "unsorted", to: "bulk", copies: 1, price: 0.2, score: 0.2, reason: "€0.20" };
    sortApply(f, [move], [], 4000);
    expect(pilesOf(f, null).map(([n, q]) => [n, q])).toContainEqual(["Cheap Bear", 3]);
    expect(sortLogOf(prefs(f))).toEqual([logLine(epochDay(4000), move.key, 1, true)]);

    // And back again: from the bulk binder to Unsorted (no binder).
    const back: SortMove = { ...move, from: "bulk", to: "unsorted", reason: "Bulk is full" };
    sortApply(f, [back], [], 5000);
    expect(pilesOf(f, binders(f).find((b) => b.name === "Bulk")!.uid!)).toHaveLength(0);
    expect(pilesOf(f, null).map(([n, q]) => [n, q])).toContainEqual(["Cheap Bear", 4]);
  });

  it("starts over: no settle, no log, no skips", () => {
    const f = file();
    setPrefs(f, { [SORT_SETTLED_PREF]: "true", [SORT_LOG_PREF]: "19990|k|1|0", [SORT_SKIPS_PREF]: "k>top" }, 3000);
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
