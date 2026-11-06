import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import type { OrderLine } from "@/lib/orders";
import { applyPlan, plan, sameSet, plainNumber } from "@/lib/purchaseprices";
import { collection, parse, type CardRef, type CollectionItem, type Json, type SyncFile } from "@/lib/syncfile";

// The app's Release132Test.PurchasePricesTest, with the same cases.
const NOW = 1_750_000_000_000;
let next = 0;

const card = (name: string, product: number | null = null, foilProduct: number | null = null, set = "abc", num = "1"): CardRef => ({
  scryfallId: `00000000-0000-0000-0000-${String(++next).padStart(12, "0")}`,
  name,
  setCode: set,
  setName: set.toUpperCase(),
  collectorNumber: num,
  rarity: "rare",
  imageUrl: null,
  cardmarketId: product,
  fallbackEur: null,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: foilProduct,
});

const stack = (c: CardRef, o: { qty?: number; foil?: boolean; lang?: string; price?: number | null; cond?: string } = {}): CollectionItem => ({
  id: 0,
  card: c,
  foil: o.foil ?? false,
  condition: o.cond ?? "NM",
  language: o.lang ?? "EN",
  quantity: o.qty ?? 1,
  addedAt: 1,
  etched: false,
  binderId: 0,
  uid: `c${String(++next).padStart(31, "0")}`,
  updatedAt: 1,
  notes: null,
  purchasePrice: o.price ?? null,
  signed: false,
  altered: false,
  paidTyped: null,
  paidCurrency: null,
});

function fileWith(items: CollectionItem[]): SyncFile {
  const f = parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
  f.data.collection = items.map((item) => ({ item, binder: null })) as unknown as Json;
  return f;
}

const base: OrderLine = {
  setCode: "",
  setName: "",
  name: "",
  number: "",
  quantity: 1,
  price: null,
  condition: "NM",
  language: "EN",
  foil: false,
  signed: false,
  altered: false,
  productId: null,
  versionKnown: true,
  languageKnown: true,
  playset: false,
  date: null,
  order: null,
};
const cm = (product: number, price: number, o: { qty?: number; name?: string; monthly?: boolean; foil?: boolean; lang?: string } = {}): OrderLine => ({
  ...base,
  name: o.name ?? "",
  quantity: o.qty ?? 1,
  price,
  language: o.lang ?? "EN",
  foil: o.foil ?? false,
  productId: product,
  versionKnown: !(o.monthly ?? true),
});
const ct = (name: string, set: string, number: string, price: number, lang = "EN"): OrderLine => ({ ...base, setCode: set, setName: `Set ${set}`, name, number, price, language: lang });

describe("purchase prices from orders (the app's PurchasePrices)", () => {
  it("averages by copies", () => {
    const snap = stack(card("Snapcaster Mage", 10));
    const p = plan(fileWith([snap]), [cm(10, 28), cm(10, 46), cm(10, 25, { qty: 2 })], false);
    expect(p.rows).toHaveLength(1);
    expect(p.rows[0]).toMatchObject({ bought: 4, newPrice: 31, change: "NEW" });
    expect(p.open).toEqual([]);
    expect(p.notOwned).toEqual([]);
  });

  it("keeps, averages or replaces prices the piles had", () => {
    const covered = stack(card("Covered", 20), { price: 3 });
    const more = stack(card("More", 21), { qty: 4, price: 2 });
    const lines = [cm(20, 5), cm(21, 4)];
    const keep = plan(fileWith([covered, more]), lines, false);
    expect(keep.rows.find((r) => r.uid === covered.uid)).toMatchObject({ change: "KEPT", newPrice: 3 });
    expect(keep.rows.find((r) => r.uid === more.uid)).toMatchObject({ change: "AVERAGED", newPrice: 2.5 });
    const over = plan(fileWith([covered, more]), lines, true);
    expect(over.rows.map((r) => [r.newPrice, r.change])).toEqual([
      [5, "REPLACED"],
      [4, "REPLACED"],
    ]);
    expect(plan(fileWith([covered]), [cm(20, 3)], true).rows[0].change).toBe("KEPT");
  });

  it("asks when the monthly file can't tell versions apart", () => {
    const c = card("Godless Shrine", 30);
    const english = stack(c);
    const portuguese = stack(c, { lang: "PT" });
    const grave = card("Watery Grave", 31);
    const other = stack(grave);
    const otherToo = stack(grave, { cond: "EX" });
    const lines = [cm(30, 9, { name: "Godless Shrine" }), cm(31, 12, { name: "Watery Grave" }), cm(99, 45, { qty: 3, name: "Force of Will" })];
    const f = fileWith([english, portuguese, other, otherToo]);
    const p = plan(f, lines, false);
    expect(new Set(p.rows.map((r) => r.uid))).toEqual(new Set([other.uid, otherToo.uid]));
    expect(p.open).toHaveLength(1);
    expect(p.open[0].index).toBe(0);
    expect(new Set(p.open[0].candidates.map((c) => c.uid))).toEqual(new Set([english.uid, portuguese.uid]));
    expect(p.notOwned[0]).toContain("Force of Will");
    const chosen = plan(f, lines, false, { 0: [portuguese.uid!] });
    expect(chosen.rows.find((r) => r.uid === portuguese.uid)?.newPrice).toBe(9);
    expect(chosen.rows.some((r) => r.uid === english.uid)).toBe(false);
  });

  it("uses the shipment file's finish and language", () => {
    const c = card("Jaheira", 40);
    const plain = stack(c);
    const foil = stack(c, { foil: true });
    const italian = stack(card("Trinisphere", 41), { lang: "IT" });
    const foilOnly = stack(card("Scroll", 50, 51), { foil: true });
    const lines = [cm(40, 1.95, { monthly: false }), cm(40, 4.11, { monthly: false, foil: true }), cm(41, 6.95, { monthly: false }), cm(51, 3, { monthly: false })];
    const p = plan(fileWith([plain, foil, italian, foilOnly]), lines, false);
    expect(p.rows.find((r) => r.uid === plain.uid)?.newPrice).toBe(1.95);
    expect(p.rows.find((r) => r.uid === foil.uid)?.newPrice).toBe(4.11);
    expect(p.rows.find((r) => r.uid === foilOnly.uid)?.newPrice).toBe(3);
    expect(p.open[0].candidates.map((c) => c.uid)).toEqual([italian.uid]);
    expect(p.open[0].why).toContain("Bought in EN");
  });

  it("finds CardTrader lines by set and number, and The List by name", () => {
    const exact = stack(card("Echocasting Symposium", null, null, "sos", "44"));
    const listed = stack(card("Wargate", null, null, "plst", "ARB-129"));
    const retro = stack(card("Putrefy", null, null, "rvr", "378"));
    const p = plan(fileWith([exact, listed, retro]), [ct("Echocasting Symposium", "SOS", "044", 2.31), ct("Wargate", "PLIST", "129", 1.5), ct("Putrefy (Retro)", "CRVR", "378", 0.9)], false);
    expect(p.rows.map((r) => r.name).sort()).toEqual(["Echocasting Symposium", "Putrefy", "Wargate"]);
    expect(sameSet("CRVR", "rvr")).toBe(true);
    expect(sameSet("predgm", "pdgm")).toBe(true);
    expect(plainNumber("APC-105")).toBe("105");
  });

  it("sets the prices it planned", () => {
    const more = stack(card("More", 21), { qty: 4, price: 2 });
    const fresh = stack(card("Fresh", 22));
    const f = fileWith([more, fresh]);
    const p = plan(f, [cm(21, 4), cm(22, 1.25)], false);
    expect(applyPlan(f, p, NOW, null)).toBe(2);
    const items = collection(f).map((s) => s.item);
    expect(items.find((i) => i.uid === more.uid)).toMatchObject({ purchasePrice: 2.5, updatedAt: NOW });
    expect(items.find((i) => i.uid === fresh.uid)?.purchasePrice).toBe(1.25);
  });
});
