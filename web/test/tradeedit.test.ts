import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { applyCollectionEdit } from "@/lib/collection";
import { setupFor } from "@/lib/collview";
import { collection, deletions, parse, type CardRef, type SyncFile } from "@/lib/syncfile";
import { tradesCsv } from "@/lib/tradecsv";
import { applyTradeEdit, lineKeys, snapshot, trades } from "@/lib/tradeedit";
import { tradesView } from "@/lib/tradeview";
import { EditError } from "@/lib/wishlist";

// Trades on the website (2026-11-13), against a sync file as the app writes it (it has a trade with Bob).

const appFile = (): SyncFile => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
const NOW = 1_750_000_000_000;
const BOB = "t0000000000000000000000000000001";
const MAIN = "b0000000000000000000000000000001";
const SOL_ID = "11111111-1111-1111-1111-111111111111";

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

let n = 0;
const uids = () => `e${String(++n).padStart(31, "0")}`;

function newTrade(file: SyncFile): string {
  return applyTradeEdit(file, { type: "new", partner: "Anna" }, NOW, null, uids).uid!;
}

describe("trades on the website", () => {
  it("make new trades with the app's fields, in the app's order", () => {
    const file = appFile();
    const uid = newTrade(file);
    applyTradeEdit(file, { type: "addLine", uid, side: "GET", card: bolt, finish: "FOIL" }, NOW, null);
    const t = trades(file).find((x) => x.trade.uid === uid)!;
    const bob = trades(file).find((x) => x.trade.uid === BOB)!;
    expect(Object.keys(t)).toEqual(Object.keys(bob));
    expect(Object.keys(t.trade)).toEqual(Object.keys(bob.trade));
    expect(Object.keys(t.items[0])).toEqual(Object.keys(bob.items[0]));
    expect(Object.keys(t.items[0].item)).toEqual(Object.keys(bob.items[0].item));
    expect(Object.keys(t.items[0].item.prices)).toEqual(Object.keys(bob.items[0].item.prices));
    expect(t.items[0].item).toMatchObject({ side: "GET", foil: true, etched: false, quantity: 1, condition: "NM", language: "EN", appliedDelta: 0, addedAt: NOW });
    // No price file: Scryfall's saved foil price stands in as the trend, like the app's snapshot.
    expect(t.items[0].item.prices.trend).toBe(3.4);
    expect(trades(file).map((x) => x.trade.uid)).toEqual([...trades(file).map((x) => x.trade.uid)].sort());
  });

  it("add more copies to the same line, like the app", () => {
    const file = appFile();
    applyTradeEdit(file, { type: "addLine", uid: BOB, side: "GET", card: bolt, finish: "NONFOIL", quantity: 2 }, NOW, null);
    applyTradeEdit(file, { type: "addLine", uid: BOB, side: "GET", card: bolt, finish: "NONFOIL", condition: "EX" }, NOW + 1, null);
    const lines = trades(file).find((x) => x.trade.uid === BOB)!.items.filter((i) => i.item.card.name === "Lightning Bolt");
    expect(lines).toHaveLength(1);
    expect(lines[0].item.quantity).toBe(3);
  });

  it("change a line: copies, condition, finish (with that finish's prices) and an agreed price in kroner", () => {
    const file = appFile();
    const t0 = trades(file).find((x) => x.trade.uid === BOB)!;
    const solKey = lineKeys(t0)[t0.items.findIndex((i) => i.item.card.name === "Sol Ring")];
    applyTradeEdit(file, { type: "updateLine", uid: BOB, line: solKey, quantity: 2, condition: "LP", custom: { amount: 74.6038, currency: "DKK" } }, NOW, null);
    const t = trades(file).find((x) => x.trade.uid === BOB)!;
    const sol = t.items.find((i) => i.item.card.name === "Sol Ring")!.item;
    expect(sol).toMatchObject({ quantity: 2, condition: "LP", customPrice: 10, customTyped: 74.6038, customCurrency: "DKK" });
    expect(t.trade.updatedAt).toBe(NOW);
    // Sol Ring has no foil.
    const key = lineKeys(t)[t.items.findIndex((i) => i.item.card.name === "Sol Ring")];
    expect(() => applyTradeEdit(file, { type: "updateLine", uid: BOB, line: key, finish: "FOIL" }, NOW, null)).toThrow(EditError);
  });

  it("remove a line and refuse a line that's gone", () => {
    const file = appFile();
    const t = trades(file).find((x) => x.trade.uid === BOB)!;
    const key = lineKeys(t)[0];
    applyTradeEdit(file, { type: "removeLine", uid: BOB, line: key }, NOW, null);
    expect(trades(file).find((x) => x.trade.uid === BOB)!.items).toHaveLength(1);
    expect(() => applyTradeEdit(file, { type: "removeLine", uid: BOB, line: key }, NOW, null)).toThrow(EditError);
  });

  it("complete: received cards into the binder, given cards out of the collection, and undo puts it all back", () => {
    const file = appFile();
    const before = JSON.parse(JSON.stringify(collection(file)));
    const r = applyTradeEdit(file, { type: "apply", uid: BOB, binder: MAIN }, NOW, null, uids);
    expect(r.missing).toBe(0);
    const t = trades(file).find((x) => x.trade.uid === BOB)!;
    expect(t.trade).toMatchObject({ applied: true, appliedAt: NOW });
    expect(t.binder).toBe(MAIN);
    const get = t.items.find((i) => i.item.side === "GET")!;
    const give = t.items.find((i) => i.item.side === "GIVE")!;
    expect(get.item.appliedDelta).toBe(1);
    expect(give.item.appliedDelta).toBe(-1);
    expect(give.appliedBinder).toBe(MAIN);
    // The only Sol Ring went (its stack is deleted and recorded); Rhystic Study came in.
    expect(collection(file).some((s) => s.item.card.scryfallId === SOL_ID)).toBe(false);
    expect(deletions(file).some((d) => d.uid === "c0000000000000000000000000000001")).toBe(true);
    const rhystic = collection(file).find((s) => s.item.card.name === "Rhystic Study")!;
    expect(rhystic.binder).toBe(MAIN);
    // Completed trades can't be changed until undone.
    expect(() => applyTradeEdit(file, { type: "addLine", uid: BOB, side: "GET", card: bolt, finish: "NONFOIL" }, NOW, null)).toThrow(EditError);

    applyTradeEdit(file, { type: "undo", uid: BOB }, NOW + 5, null, () => "c0000000000000000000000000000009");
    const back = trades(file).find((x) => x.trade.uid === BOB)!;
    expect(back.trade).toMatchObject({ applied: false, appliedAt: null });
    expect(back.items.every((i) => i.item.appliedDelta === 0 && i.appliedBinder === null)).toBe(true);
    const now = collection(file);
    expect(now.some((s) => s.item.card.name === "Rhystic Study")).toBe(false);
    const sol = now.find((s) => s.item.card.scryfallId === SOL_ID)!;
    expect(sol.binder).toBe(MAIN);
    expect(sol.item.quantity).toBe(before[0].item.quantity);
  });

  it("count given copies that aren't in the collection, and take plain copies in the exact condition first", () => {
    const file = appFile();
    // A signed Sol Ring in Unsorted and a plain one in Main: the plain one goes first.
    applyCollectionEdit(file, { type: "add", card: trades(file)[0].items[1].item.card, quantity: 1, binder: null, signed: true }, NOW, null, () => "c0000000000000000000000000000005");
    const t = trades(file).find((x) => x.trade.uid === BOB)!;
    const key = lineKeys(t)[t.items.findIndex((i) => i.item.side === "GIVE")];
    applyTradeEdit(file, { type: "updateLine", uid: BOB, line: key, quantity: 3 }, NOW, null);
    const r = applyTradeEdit(file, { type: "apply", uid: BOB, binder: null }, NOW, null, uids);
    expect(r.missing).toBe(1);
    const give = trades(file).find((x) => x.trade.uid === BOB)!.items.find((i) => i.item.side === "GIVE")!;
    expect(give.item.appliedDelta).toBe(-2);
    expect(give.appliedBinder).toBe(MAIN);
  });

  it("delete with a deletion record, and restore it newer than the deletion", () => {
    const file = appFile();
    const r = applyTradeEdit(file, { type: "remove", uid: BOB }, NOW, null);
    expect(trades(file)).toHaveLength(0);
    expect(deletions(file).find((d) => d.uid === BOB)!.deletedAt).toBe(NOW);
    applyTradeEdit(file, { type: "restore", trade: r.removed! }, NOW + 10, null);
    const back = trades(file).find((x) => x.trade.uid === BOB)!;
    expect(back.trade.updatedAt).toBeGreaterThan(NOW);
    expect(back.trade.partner).toBe("Bob");
    expect(deletions(file).some((d) => d.uid === BOB)).toBe(false);
  });

  it("refresh prices from the price file", () => {
    const file = appFile();
    const prices = { createdAt: "", madeAt: 0, rates: { date: "", perEuro: { DKK: 7.46, USD: 1.1 } }, products: { "556001": [2, 2.1, 1.8, 2, 2, 2, null, null, null, null, null, null] } } as never;
    expect(snapshot(trades(file)[0].items[1].item.card, false, prices)).toEqual({ trend: 2, avg: 2.1, low: 1.8, avg1: 2, avg7: 2, avg30: 2 });
    applyTradeEdit(file, { type: "refreshPrices", uid: BOB }, NOW, prices);
    const sol = trades(file)[0].items.find((i) => i.item.card.name === "Sol Ring")!.item;
    expect(sol.prices.trend).toBe(2);
  });

  it("show line keys, binders and the agreed prices; export the app's CSV columns", () => {
    const file = appFile();
    const v = tradesView(file, setupFor(file, null, null, null), null);
    const bob = v.trades[0];
    expect(bob.lines.map((l) => l.key)).toEqual(lineKeys(trades(file)[0]));
    expect(bob.lines.find((l) => l.side === "GET")).toMatchObject({ custom: true, customEur: 26.81, customTyped: 200, customCurrency: "DKK", unitEur: 26.81 });
    expect(v.binders).toEqual([{ uid: MAIN, name: "Main" }]);
    expect(v.priceTypeShort).toBe("Trend");
    const csv = tradesCsv(v).split("\n");
    expect(csv[0]).toBe(
      "Trade started,Partner,Applied to collection,Side,Name,Set code,Collector number,Finish,Condition,Language,Quantity,Unit price EUR (Trend),Custom price,Line total EUR,Scryfall ID",
    );
    expect(csv[1]).toContain(",Bob,no,received,Rhystic Study,JMP,175,normal,NM,EN,1,26.81,26.81,26.81,22222222-2222-2222-2222-222222222222");
  });
});
