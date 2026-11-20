import { describe, expect, it } from "vitest";
import { applyCollectionEdit } from "@/lib/collection";
import { makeOrder, orderHits, type OrderHit, type OrderedCard, type PurchaseOrder } from "@/lib/orders";
import { pricingSetup } from "@/lib/pricing";
import type { PriceFile } from "@/lib/prices";
import { applyScanEdit, scanRows, scansOf, type ScannedCard } from "@/lib/scans";
import { collection, parse, type CardRef, type Json, type SyncFile } from "@/lib/syncfile";

// App 1.103: the price paid for a scanned card (format 16), and the orders holding a printing in its card window.
const NOW = 1_750_000_000_000;
const MAIN = "b0000000000000000000000000000001";
/** Another binder the app may know; "Unsorted" is a null binder. */
const OTHER = "b0000000000000000000000000000002";
let n = 0;
const uids = () => `a${String(++n).padStart(31, "0")}`;

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

const scan = (uid: string, over: Partial<ScannedCard> = {}): ScannedCard => ({
  id: 0,
  card: bolt,
  foil: false,
  etched: false,
  condition: "NM",
  language: "EN",
  quantity: 1,
  exactPrinting: true,
  scannedAt: NOW - 1000,
  uid,
  updatedAt: NOW - 1000,
  ...over,
});

/** An empty sync file (format 16) with [scans] waiting. */
const file = (scans: ScannedCard[] = []): SyncFile => {
  const f = parse(JSON.stringify({ format: 16, data: { collection: [], binders: [{ id: 0, name: "Main", createdAt: 0, uid: null, updatedAt: 0 }], scans: [] } }));
  // The binder the scans go to.
  f.data.binders = [
    { id: 0, name: "Main", createdAt: 0, uid: MAIN, updatedAt: 0 },
    { id: 0, name: "Other", createdAt: 0, uid: OTHER, updatedAt: 0 },
  ] as unknown as Json;
  f.data.scans = scans as unknown as Json;
  return f;
};

const rates: PriceFile["rates"] = { date: "2026-11-20", perEuro: { DKK: 7.46, USD: 1.05 } };
const setup = () => pricingSetup({}, "trend", null, null, null);
const pileIn = (f: SyncFile, binder: string | null) => collection(f).filter((s) => s.binder === binder && s.item.card.scryfallId === bolt.scryfallId);

describe("a scanned card's purchase price (ScannedCard.purchasePrice, format 16)", () => {
  it("shows the price with the scan list, and null for older files without it", () => {
    const f = file([scan("s1", { quantity: 2, purchasePrice: 1.2, paidTyped: null, paidCurrency: null }), scan("s2")]);
    const rows = scanRows(f, setup(), null);
    expect(rows.find((r) => r.uid === "s1")).toMatchObject({ purchasePrice: 1.2, paidTyped: null, paidCurrency: null });
    expect(rows.find((r) => r.uid === "s2")).toMatchObject({ purchasePrice: null, paidTyped: null, paidCurrency: null });
  });

  it("sets the price paid in euros, in kroner with the rate, and clears it", () => {
    const f = file([scan("s1")]);
    applyScanEdit(f, { type: "update", uid: "s1", paid: { amount: 2.5, currency: "EUR" } }, NOW, null, uids, rates);
    expect(scansOf(f)[0]).toMatchObject({ purchasePrice: 2.5, paidTyped: null, paidCurrency: null });
    applyScanEdit(f, { type: "update", uid: "s1", paid: { amount: 37.3, currency: "DKK" } }, NOW, null, uids, rates);
    // 37.30 kroner at 7.46 to the euro.
    expect(scansOf(f)[0]).toMatchObject({ purchasePrice: 5, paidTyped: 37.3, paidCurrency: "DKK" });
    applyScanEdit(f, { type: "update", uid: "s1", paid: null }, NOW, null, uids, rates);
    expect(scansOf(f)[0]).toMatchObject({ purchasePrice: null, paidTyped: null, paidCurrency: null });
    expect(() => applyScanEdit(f, { type: "update", uid: "s1", paid: { amount: -1, currency: "EUR" } }, NOW, null, uids, rates)).toThrow(/0 or more/);
  });

  it("brings the price back with Undo, and drops a wrong one", () => {
    const f = file([scan("s1", { quantity: 2, purchasePrice: 3.5, paidTyped: 26.11, paidCurrency: "DKK" })]);
    const { removed } = applyScanEdit(f, { type: "discard", uids: ["s1"] }, NOW);
    applyScanEdit(f, { type: "restore", items: removed! }, NOW + 1, null, uids);
    const back = scansOf(f).find((s) => s.uid !== "s1")!;
    expect(back).toMatchObject({ quantity: 2, purchasePrice: 3.5, paidTyped: 26.11, paidCurrency: "DKK" });
    applyScanEdit(f, { type: "restore", items: [scan("s2", { purchasePrice: -2, paidCurrency: "GBP" })] }, NOW + 2, null, uids);
    const bad = scansOf(f).find((s) => s.uid !== back.uid && s.uid !== "s1")!;
    expect(bad).toMatchObject({ purchasePrice: null, paidTyped: null, paidCurrency: null });
  });

  it("sends the price into the binder: averaged into a pile it joins", () => {
    const f = file([scan("s1", { quantity: 1, purchasePrice: 2, paidTyped: null, paidCurrency: null })]);
    // One copy already there, at 4.00 each.
    applyCollectionEdit(f, { type: "add", card: bolt, quantity: 1, binder: MAIN, paid: { amount: 4, currency: "EUR" } }, NOW, rates, uids);
    expect(pileIn(f, MAIN)).toHaveLength(1);
    applyScanEdit(f, { type: "toBinder", uids: ["s1"], binder: MAIN, keep: false }, NOW + 1, null, uids, rates);
    expect(pileIn(f, MAIN).map((s) => [s.item.quantity, s.item.purchasePrice])).toEqual([[2, 3]]);
    expect(scansOf(f)).toHaveLength(0);
  });

  it("gives an empty binder's pile the scan's price", () => {
    const f = file([scan("s1", { quantity: 2, purchasePrice: 1.75, paidTyped: 13.06, paidCurrency: "DKK" })]);
    applyScanEdit(f, { type: "toBinder", uids: ["s1"], binder: null, keep: true }, NOW, null, uids, rates);
    expect(pileIn(f, null).map((s) => [s.item.quantity, s.item.purchasePrice, s.item.paidTyped, s.item.paidCurrency])).toEqual([[2, 1.75, 13.06, "DKK"]]);
    expect(scansOf(f)).toHaveLength(1);
  });

  it("sends a scan without a price as before (the pile keeps its own)", () => {
    const f = file([scan("s1", { quantity: 2 })]);
    applyScanEdit(f, { type: "toBinder", uids: ["s1"], binder: OTHER, keep: false }, NOW, null, uids, rates);
    expect(pileIn(f, OTHER)[0].item).toMatchObject({ quantity: 2, purchasePrice: null });
  });
});

// ---- The orders holding a printing ----

const order = (id: number, uid: string | null, date: string, cards: OrderedCard[], shop = "Cardmarket"): PurchaseOrder =>
  ({ ...makeOrder(shop, null, "Cardmarket shipment", date, cards, NOW), id, uid });

const card = (over: Partial<OrderedCard> = {}): OrderedCard => ({ name: "Lightning Bolt", quantity: 1, price: 1, condition: "NM", scryfallId: bolt.scryfallId, ...over });
const brief = (h: OrderHit[]) => h.map((x) => [x.uid, x.quantity, x.price, x.finishKnown]);

describe("the orders holding a printing (Orders.hits)", () => {
  it("matches the finish, and an unknown finish matches both", () => {
    const orders = [
      order(1, "o1", "2026-01-10", [card({ foil: true })]),
      order(2, "o2", "2026-01-11", [card({ foil: false })]),
      // Cardmarket's monthly file: no condition, so the finish isn't known.
      order(3, "o3", "2026-01-12", [card({ condition: null, foil: false })]),
    ];
    expect(orderHits(orders, bolt.scryfallId, false).map((h) => [h.uid, h.finishKnown])).toEqual([
      ["o3", false],
      ["o2", true],
    ]);
    expect(orderHits(orders, bolt.scryfallId, true).map((h) => [h.uid, h.finishKnown])).toEqual([
      ["o3", false],
      ["o1", true],
    ]);
  });

  it("adds the lines of one order together, price averaged by copies", () => {
    const orders = [
      order(1, "o1", "2026-01-10", [card({ quantity: 2, price: 1 }), card({ quantity: 1, price: 4 }), card({ quantity: 1, price: null }), card({ foil: true, price: 9 })]),
    ];
    // 2 at 1.00 and 1 at 4.00: (2 + 4) / 3 = 2.00; the unpriced line counts in the copies only.
    expect(brief(orderHits(orders, bolt.scryfallId, false))).toEqual([["o1", 4, 2, true]]);
    expect(brief(orderHits(orders, bolt.scryfallId, true))).toEqual([["o1", 1, 9, true]]);
    const unpriced = [order(1, "o1", "2026-01-10", [card({ price: null }), card({ quantity: 2, price: null })])];
    expect(brief(orderHits(unpriced, bolt.scryfallId, false))).toEqual([["o1", 3, null, true]]);
  });

  it("puts the newest first, then the higher id, and leaves out orders without a uid", () => {
    const orders = [
      order(3, "o3", "2026-03-01", [card()]),
      order(5, "o5", "2026-05-02", [card()]),
      order(4, "o4", "2026-05-02", [card()]),
      order(9, null, "2026-06-01", [card()]),
    ];
    expect(orderHits(orders, bolt.scryfallId, false).map((h) => h.uid)).toEqual(["o5", "o4", "o3"]);
  });

  it("finds nothing for another printing", () => {
    const orders = [order(1, "o1", "2026-01-10", [card({ scryfallId: "99999999-9999-9999-9999-999999999999" })])];
    expect(orderHits(orders, bolt.scryfallId, false)).toEqual([]);
  });
});
