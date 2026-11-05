import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { mergeDetails, toInfo, type DetailsFile } from "@/lib/carddetails";
import { parseCopy } from "@/lib/cardkingdom";
import { resolveFinish } from "@/lib/cards";
import { applyCollectionEdit } from "@/lib/collection";
import { comparator, computeStats, EMPTY_FILTER, matchesFilter, matchesText } from "@/lib/colquery";
import { collectionView, orderBinders, type CollRow } from "@/lib/collview";
import { archidektCsv, fullCsv } from "@/lib/csvexport";
import type { PriceFile } from "@/lib/prices";
import { conditionNote, decodePcts, isApprox, pricingSetup, unitPrice } from "@/lib/pricing";
import { binders, collection, deletions, parse, type CardRef, type SyncFile } from "@/lib/syncfile";
import { verdictOf } from "@/lib/tradeview";
import { EditError } from "@/lib/wishlist";

const appFile = (): SyncFile => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
const NOW = 1_750_000_000_000;
const SOL_STACK = "c0000000000000000000000000000001";
const MAIN = "b0000000000000000000000000000001";
let n = 0;
const uids = () => `e${String(++n).padStart(31, "0")}`;

const bolt: CardRef = {
  scryfallId: "44444444-4444-4444-4444-444444444444",
  name: "Lightning Bolt",
  setCode: "2xm",
  setName: "Double Masters",
  collectorNumber: "117",
  rarity: "uncommon",
  imageUrl: null,
  cardmarketId: 470000,
  fallbackEur: 1.2,
  fallbackEurFoil: 3.4,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
};
const bolt2: CardRef = { ...bolt, scryfallId: "55555555-5555-5555-5555-555555555555", setCode: "m10", setName: "Magic 2010", hasFoil: false };

const stack = (f: SyncFile, uid: string) => collection(f).find((s) => s.item.uid === uid)!;

describe("collection edits", () => {
  it("adds a stack in the app's shape, and more copies of the same stack go onto it", () => {
    const f = appFile();
    const { uid } = applyCollectionEdit(f, { type: "add", card: bolt, quantity: 2, finish: "FOIL", condition: "EX", language: "DE", binder: MAIN }, NOW, null, uids);
    const s = stack(f, uid!);
    expect(s.binder).toBe(MAIN);
    expect(s.item).toMatchObject({ id: 0, binderId: 0, foil: true, etched: false, condition: "EX", language: "DE", quantity: 2, addedAt: NOW, updatedAt: NOW, signed: false });
    const again = applyCollectionEdit(f, { type: "add", card: bolt, quantity: 3, finish: "FOIL", condition: "EX", language: "DE", binder: MAIN }, NOW + 5, null, uids);
    expect(again.uid).toBe(uid);
    expect(stack(f, uid!).item.quantity).toBe(5);
    // Signed copies are a stack of their own.
    const signed = applyCollectionEdit(f, { type: "add", card: bolt, quantity: 1, finish: "FOIL", condition: "EX", language: "DE", binder: MAIN, signed: true }, NOW, null, uids);
    expect(signed.uid).not.toBe(uid);
    // The list stays sorted by uid, like the app writes it.
    const list = collection(f).map((s) => s.item.uid!);
    expect([...list].sort()).toEqual(list);
  });

  it("refuses unknown finishes, conditions, binders and quantities", () => {
    const f = appFile();
    const add = (x: object) => () => applyCollectionEdit(f, { type: "add", card: bolt, quantity: 1, ...x }, NOW, null, uids);
    expect(add({ finish: "ETCHED" })).toThrow(EditError);
    expect(add({ condition: "XX" })).toThrow(EditError);
    expect(add({ binder: "nope" })).toThrow(EditError);
    expect(add({ quantity: 0 })).toThrow(EditError);
  });

  it("merges a stack into the one it now equals, and records the deletion", () => {
    const f = appFile();
    const a = applyCollectionEdit(f, { type: "add", card: bolt, quantity: 2, binder: null }, NOW, null, uids).uid!;
    const b = applyCollectionEdit(f, { type: "add", card: bolt, quantity: 1, condition: "LP", binder: null }, NOW, null, uids).uid!;
    const kept = applyCollectionEdit(f, { type: "update", uid: b, condition: "NM" }, NOW + 10, null, uids).uid;
    expect(kept).toBe(a);
    expect(stack(f, a).item.quantity).toBe(3);
    expect(stack(f, a).item.updatedAt).toBe(NOW + 10);
    expect(collection(f).some((s) => s.item.uid === b)).toBe(false);
    expect(deletions(f).find((d) => d.uid === b)?.deletedAt).toBeGreaterThanOrEqual(NOW + 10);
  });

  it("moves part of a stack to another binder", () => {
    const f = appFile();
    applyCollectionEdit(f, { type: "update", uid: SOL_STACK, quantity: 4 }, NOW, null, uids);
    const moved = applyCollectionEdit(f, { type: "update", uid: SOL_STACK, binder: null, move: 1 }, NOW + 1, null, uids).uid!;
    expect(stack(f, SOL_STACK).item.quantity).toBe(3);
    expect(stack(f, SOL_STACK).binder).toBe(MAIN);
    expect(stack(f, moved).item.quantity).toBe(1);
    expect(stack(f, moved).binder).toBeNull();
    // The rest follows: merges into the stack already in Unsorted.
    const all = applyCollectionEdit(f, { type: "update", uid: SOL_STACK, binder: null }, NOW + 2, null, uids).uid;
    expect(all).toBe(moved);
    expect(stack(f, moved).item.quantity).toBe(4);
  });

  it("keeps the purchase price as the app does (euros, plus what was typed)", () => {
    const f = appFile();
    const rates: PriceFile["rates"] = { date: "2026-11-05", perEuro: { DKK: 7.46, USD: 1.08 } };
    applyCollectionEdit(f, { type: "update", uid: SOL_STACK, paid: { amount: 74.6, currency: "DKK" } }, NOW, rates, uids);
    expect(stack(f, SOL_STACK).item).toMatchObject({ purchasePrice: 10, paidTyped: 74.6, paidCurrency: "DKK" });
    applyCollectionEdit(f, { type: "update", uid: SOL_STACK, paid: { amount: 2.5, currency: "EUR" } }, NOW, rates, uids);
    expect(stack(f, SOL_STACK).item).toMatchObject({ purchasePrice: 2.5, paidTyped: null, paidCurrency: null });
    applyCollectionEdit(f, { type: "update", uid: SOL_STACK, paid: null, notes: "  bent corner " }, NOW, rates, uids);
    expect(stack(f, SOL_STACK).item).toMatchObject({ purchasePrice: null, notes: "bent corner" });
  });

  it("changes the printing, keeping the finish where it can", () => {
    const f = appFile();
    const a = applyCollectionEdit(f, { type: "add", card: bolt, quantity: 1, finish: "FOIL" }, NOW, null, uids).uid!;
    applyCollectionEdit(f, { type: "update", uid: a, card: bolt2 }, NOW, null, uids);
    expect(stack(f, a).item.card.setCode).toBe("m10");
    expect(stack(f, a).item.foil).toBe(false);
    expect(() => applyCollectionEdit(f, { type: "update", uid: a, card: { ...bolt, name: "Shock" } }, NOW, null, uids)).toThrow(EditError);
    expect(resolveFinish({ hasNonFoil: false, hasFoil: true, hasEtched: false }, "NONFOIL")).toBe("FOIL");
  });

  it("removes stacks and does several at once", () => {
    const f = appFile();
    const a = applyCollectionEdit(f, { type: "add", card: bolt, quantity: 1 }, NOW, null, uids).uid!;
    const b = applyCollectionEdit(f, { type: "add", card: bolt2, quantity: 1 }, NOW, null, uids).uid!;
    applyCollectionEdit(f, { type: "bulk", uids: [a, b], binder: MAIN, condition: "GD" }, NOW, null, uids);
    expect(stack(f, a)).toMatchObject({ binder: MAIN, item: { condition: "GD" } });
    applyCollectionEdit(f, { type: "bulk", uids: [a, b], remove: true }, NOW, null, uids);
    expect(collection(f).map((s) => s.item.uid)).toEqual([SOL_STACK]);
    expect(deletions(f).some((d) => d.uid === a)).toBe(true);
    // Removing what a phone removed already is fine.
    expect(() => applyCollectionEdit(f, { type: "remove", uid: a }, NOW, null, uids)).not.toThrow();
  });

  it("makes and renames binders with unique names", () => {
    const f = appFile();
    const { uid } = applyCollectionEdit(f, { type: "addBinder", name: "  Trade   binder " }, NOW, null, uids);
    expect(binders(f).find((b) => b.uid === uid)).toMatchObject({ id: 0, name: "Trade binder", createdAt: NOW, updatedAt: NOW });
    expect(() => applyCollectionEdit(f, { type: "addBinder", name: "main" }, NOW, null, uids)).toThrow(EditError);
    expect(() => applyCollectionEdit(f, { type: "addBinder", name: "Unsorted" }, NOW, null, uids)).toThrow(EditError);
    applyCollectionEdit(f, { type: "renameBinder", uid: uid!, name: "Trades" }, NOW + 1, null, uids);
    expect(binders(f).find((b) => b.uid === uid)?.name).toBe("Trades");
    expect(() => applyCollectionEdit(f, { type: "renameBinder", uid: uid!, name: "Main" }, NOW, null, uids)).toThrow(EditError);
  });
});

const prices: PriceFile = {
  createdAt: "2026-11-05T02:00:00",
  madeAt: NOW,
  rates: { date: "2026-11-05", perEuro: { USD: 1.25, DKK: 7.46 } },
  products: {
    "556001": [2, 1.8, 1.5, 2, 2, 1.6, null, null, null, null, null, null],
  },
};

describe("prices as the app shows them", () => {
  const details: DetailsFile = { madeAt: NOW, cards: {} };
  mergeDetails(
    details,
    ["11111111-1111-1111-1111-111111111111"],
    [{ id: "11111111-1111-1111-1111-111111111111", colors: [], color_identity: [], type_line: "Artifact", cmc: 1, edhrec_rank: 1, released_at: "2021-04-23", game_changer: true, prices: { usd: "2.50" }, purchase_uris: { tcgplayer: "https://tcg/x" } }],
    NOW,
  );
  const ck = parseCopy("#card-kingdom\t2026-11-05 05:10:03\n11111111-1111-1111-1111-111111111111\tN\t3.75\t1.25\t3.75\t3.00\t2.50\t1.25\tmtg/c21/sol-ring\n", NOW);

  it("follows the chosen source, else Cardmarket with ≈", () => {
    const id = "11111111-1111-1111-1111-111111111111";
    const cm = 2;
    const tcg = pricingSetup({ priceSource: "tcgplayer" }, "trend", prices, details, ck);
    expect(unitPrice(tcg, id, "NONFOIL", "NM", cm)).toBeCloseTo(2);
    expect(unitPrice(tcg, id, "FOIL", "NM", 5)).toBe(5);
    expect(isApprox(tcg, id, "FOIL", "NM")).toBe(true);
    const kingdom = pricingSetup({ priceSource: "cardkingdom" }, "trend", prices, details, ck);
    // Card Kingdom has a price per condition: EX is its "ex", GD its "vg".
    expect(unitPrice(kingdom, id, "NONFOIL", "EX", cm)).toBeCloseTo(2.4);
    expect(unitPrice(kingdom, id, "NONFOIL", "GD", cm)).toBeCloseTo(2);
  });

  it("lowers prices for the condition when the app's setting is on", () => {
    expect(decodePcts("EX=80,XX=1")).toMatchObject({ EX: 80, GD: 70 });
    const p = pricingSetup({ conditionAdjust: "true", conditionPcts: "EX=80" }, "trend", prices, details, ck);
    expect(unitPrice(p, "x", "NONFOIL", "EX", 10)).toBeCloseTo(8);
    expect(conditionNote(p, "x", "NONFOIL", "EX")).toBe("EX 80%");
    expect(conditionNote(p, "x", "NONFOIL", "NM")).toBeNull();
    const off = pricingSetup({ conditionPcts: "EX=80" }, "trend", prices, details, ck);
    expect(unitPrice(off, "x", "NONFOIL", "EX", 10)).toBe(10);
  });

  it("builds the page's rows with details and Cardmarket's change", () => {
    const v = collectionView(appFile(), prices, details, ck);
    expect(v.rows).toHaveLength(1);
    const r = v.rows[0];
    expect(r).toMatchObject({ binder: MAIN, finish: "NONFOIL", unitEur: 2, cmEur: 2, approx: false });
    expect(r.changeEur).toBeCloseTo(0.4);
    expect(r.changePct).toBeCloseTo(25);
    expect(r.info).toMatchObject({ t: "Artifact", g: true, r: 1 });
    expect(v.missing).toEqual([]);
    expect(v.binders).toEqual([{ uid: MAIN, name: "Main" }]);
    expect(collectionView(appFile(), prices, { madeAt: 0, cards: {} }, null).missing).toEqual(["11111111-1111-1111-1111-111111111111"]);
  });

  it("reads Scryfall's details like the app", () => {
    const i = toInfo({ id: "x", card_faces: [{ colors: ["U"], type_line: "Creature — Human" }, { colors: ["B"], type_line: "Creature — Horror" }], color_identity: ["B", "U"], cmc: 2 }, NOW);
    expect(i).toMatchObject({ c: "UB", i: "UB", t: "Creature — Human // Creature — Horror", v: 2, g: false });
  });
});

function row(over: Partial<CollRow>): CollRow {
  return {
    uid: "u",
    card: bolt,
    finish: "NONFOIL",
    condition: "NM",
    language: "EN",
    quantity: 1,
    addedAt: NOW,
    binder: null,
    notes: null,
    purchasePrice: null,
    paidTyped: null,
    paidCurrency: null,
    signed: false,
    altered: false,
    unitEur: 1,
    cmEur: 1,
    approx: false,
    condNote: null,
    changeEur: null,
    changePct: null,
    info: { c: "R", i: "R", t: "Instant", v: 1, r: 5, d: "2020-08-07", g: false },
    ...over,
  };
}

describe("searching, filtering, sorting and stats", () => {
  const sol = row({ uid: "s", card: { ...bolt, name: "Sol Ring", setCode: "c21", setName: "Commander 2021" }, unitEur: 2, quantity: 2, info: { c: "", i: "", t: "Artifact", v: 1, r: 1, d: "2021-04-23", g: true } });
  const boltRow = row({ uid: "b", finish: "FOIL", card: { ...bolt, foilType: "surgefoil" }, signed: true, changeEur: 0.5, changePct: 50 });
  const forest = row({ uid: "f", card: { ...bolt, name: "Forest", rarity: "common" }, unitEur: null, info: { c: "", i: "", t: "Basic Land — Forest", v: 0, r: null, d: "1993-08-05", g: false } });
  const rows = [sol, boltRow, forest];

  it("searches like the app's search field", () => {
    expect(rows.filter((r) => matchesText(r, "surge")).map((r) => r.uid)).toEqual(["b"]);
    expect(rows.filter((r) => matchesText(r, "C21")).map((r) => r.uid)).toEqual(["s"]);
    expect(rows.filter((r) => matchesText(r, "signed")).map((r) => r.uid)).toEqual(["b"]);
  });

  it("filters by colours, type, price and decks", () => {
    const decks = new Set(["sol ring"]);
    const f = (x: object) => rows.filter((r) => matchesFilter({ ...EMPTY_FILTER, ...x }, r, decks)).map((r) => r.uid);
    expect(f({ colors: ["R"] })).toEqual(["b"]);
    expect(f({ colors: ["C"], colorMatch: "EXACT" })).toEqual(["s", "f"]);
    expect(f({ types: ["Land"] })).toEqual(["f"]);
    expect(f({ minPrice: 1.5 })).toEqual(["s"]);
    expect(f({ decks: "IN" })).toEqual(["s"]);
    expect(f({ decks: "OUT" })).toEqual(["b", "f"]);
    expect(f({ marks: ["PLAIN"] })).toEqual(["s", "f"]);
  });

  it("sorts in the app's orders", () => {
    const by = (field: string, reversed = false) => [...rows].sort(comparator([{ field: field as never, reversed }])).map((r) => r.uid);
    expect(by("COLOR")).toEqual(["b", "s", "f"]);
    expect(by("VALUE")).toEqual(["s", "b", "f"]);
    expect(by("TYPE")).toEqual(["f", "b", "s"]);
    expect(by("CHANGE_PCT")).toEqual(["b", "f", "s"]);
    expect(by("NAME", true)).toEqual(["s", "b", "f"]);
  });

  it("counts the stats", () => {
    const s = computeStats(rows, new Map([[null, "Unsorted"]]), new Set(["sol ring"]));
    expect(s.copies).toBe(4);
    expect(s.value).toBe(5);
    expect(s.unpriced).toBe(1);
    expect(s.colors.map((e) => [e.key, e.copies])).toEqual([["R", 1], ["C", 2], ["L", 1]]);
    expect(s.gameChangers.map((c) => c.row.uid)).toEqual(["s"]);
    expect(s.edhrecTop[0].note).toBe("#1 on EDHREC");
    expect(s.inDecks.copies).toBe(2);
    expect(s.risers.map((c) => c.row.uid)).toEqual(["b"]);
    expect(s.oldest?.row.uid).toBe("f");
    expect(s.curve[1].copies).toBe(3);
  });
});

describe("CSV files", () => {
  it("writes the app's full format", () => {
    const csv = fullCsv(
      [row({ quantity: 3, binder: "b1", purchasePrice: 10, paidTyped: 74.6, paidCurrency: "DKK", notes: "a, b", condition: "LP", language: "DE", cmEur: 1.5 })],
      [{ uid: "b1", name: "Main" }],
      "trend",
    );
    const [head, line] = csv.trim().split("\n");
    expect(head.split(",")).toHaveLength(20);
    expect(head).toContain("Price EUR (Trend)");
    expect(line).toBe('Main,binder,Lightning Bolt,2XM,Double Masters,117,normal,uncommon,3,44444444-4444-4444-4444-444444444444,light_played,de,1.50,10.00,EUR,"a, b",false,false,74.60,DKK');
  });

  it("writes Archidekt's 18 columns with binders merged", () => {
    const csv = archidektCsv([row({ quantity: 2, binder: "x" }), row({ quantity: 1, binder: null }), row({ finish: "FOIL" })]);
    const lines = csv.trim().split("\n");
    expect(lines).toHaveLength(3);
    const f = lines[1].split(",");
    expect(f).toHaveLength(18);
    expect([f[0], f[1], f[4], f[6], f[8], f[13], f[14]]).toEqual(["Lightning Bolt", "2XM", "normal", "3", "44444444-4444-4444-4444-444444444444", "near_mint", "en"]);
  });
});

describe("binders and trades", () => {
  it("orders binders as arranged in the app, the rest by name", () => {
    const list = [
      { uid: "a", name: "Zed" },
      { uid: "b", name: "Alpha" },
      { uid: "c", name: "Mid" },
    ];
    expect(orderBinders(list, "unsorted,b:c,wishlist").map((b) => b.uid)).toEqual(["c", "b", "a"]);
  });

  it("judges a trade with the app's margin", () => {
    expect(verdictOf(10, 10.4, 5).verdict).toBe("FAIR");
    expect(verdictOf(10, 12, 5).verdict).toBe("FAVORS_YOU");
    expect(verdictOf(12, 10, 5).verdict).toBe("FAVORS_THEM");
    expect(verdictOf(0, 0, 5).verdict).toBe("EMPTY");
  });
});
