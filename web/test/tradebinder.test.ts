import { describe, expect, it } from "vitest";
import type { CollRow } from "@/lib/collview";
import { collection, prefs, type CardRef, type SyncFile } from "@/lib/syncfile";
import { applyTrade, DEFAULT_RULES, forgetDeclined, kindKey, plan, planInputs, rulesOf, rulesPrefs, skipsOf, tradeBinderOf, type PlanInput } from "@/lib/tradebinder";

const card = (name: string, id = name.toLowerCase().replace(/\W/g, "")): CardRef => ({
  scryfallId: id,
  name,
  setCode: "abc",
  setName: "Set",
  collectorNumber: "1",
  rarity: "rare",
  imageUrl: null,
  cardmarketId: null,
  fallbackEur: null,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: false,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
});

let n = 0;
function row(name: string, price: number | null, o: { rank?: number; qty?: number; type?: string; binder?: string | null; id?: string; signed?: boolean } = {}): CollRow {
  return {
    uid: `u${n++}`,
    card: card(name, o.id),
    finish: "NONFOIL",
    condition: "NM",
    language: "EN",
    quantity: o.qty ?? 1,
    addedAt: 0,
    binder: o.binder ?? null,
    notes: null,
    purchasePrice: null,
    paidTyped: null,
    paidCurrency: null,
    signed: o.signed ?? false,
    altered: false,
    unitEur: price,
    cmEur: price,
    approx: false,
    condNote: null,
    changeEur: null,
    changePct: null,
    srcEur: { cardmarket: price, tcgplayer: null, cardkingdom: null, ckPays: null },
    info: { c: "", i: "", t: o.type ?? "Creature", v: 1, r: o.rank ?? null, d: "", g: false },
  };
}

const money = (eur: number) => `€${eur.toFixed(2)}`;
const input = (rows: CollRow[], over: Partial<PlanInput> = {}): PlanInput => ({
  rows,
  deckNeeds: new Map(),
  wishlist: new Set(),
  binder: "TB",
  rules: { ...DEFAULT_RULES, maxCards: 3, keepOne: true, minValue: 1 },
  skipped: new Set(),
  cardmarket: true,
  money,
  ...over,
});

// The app's TradeBinderPlannerTest (Release118Test) and the signed-copies test (Release127Test), on the website.
describe("the trade binder plan (TradeBinderPlanner)", () => {
  it("only spare, valuable copies, by score", () => {
    const rows = [
      row("Sol Ring", 2, { rank: 1, qty: 3 }),
      row("Bulk Bear", 0.2, { qty: 5 }),
      row("Popular", 3, { rank: 50, qty: 2 }),
      row("Obscure", 3.5, { rank: 20_000, qty: 2 }),
      row("Wanted", 10, { qty: 2 }),
      row("Forest", 5, { type: "Basic Land — Forest", qty: 9 }),
    ];
    const changes = plan(input(rows, { deckNeeds: new Map([["sol ring", 2]]), wishlist: new Set(["wanted"]), binder: "X" }));
    expect(changes.every((c) => c.add)).toBe(true);
    expect(changes.map((c) => c.card.name).sort()).toEqual(["Obscure", "Popular", "Sol Ring"]);
    expect(changes[0].card.name).toBe("Popular");
    expect(changes[0].reason).toBe("€3.00 · top 100 on EDHREC");
    expect(changes.reduce((s, c) => s + c.copies, 0)).toBe(3);
  });

  it("takes out what decks need now, and remembers declined suggestions", () => {
    const rows = [row("Sol Ring", 2, { rank: 1, binder: "TB", id: "sr" }), row("Sol Ring", 2, { rank: 1, id: "sr" }), row("Popular", 3, { rank: 50, qty: 2 })];
    const needs = new Map([["sol ring", 2]]);
    const changes = plan(input(rows, { deckNeeds: needs }));
    const out = changes.filter((c) => !c.add);
    expect(out).toHaveLength(1);
    expect(out[0].card.name).toBe("Sol Ring");
    expect(out[0].reason).toContain("decks use");
    const again = plan(input(rows, { deckNeeds: needs, skipped: new Set([out[0].skipKey, changes.find((c) => c.add)!.skipKey]) }));
    expect(again).toEqual([]);
  });

  it("never suggests signed copies, with the app's skip keys", () => {
    const rows = [row("Brainstorm", 5, { qty: 4, id: "bs" }), row("Brainstorm", 5, { signed: true, id: "bs" })];
    const changes = plan(input(rows, { rules: { ...DEFAULT_RULES, minValue: 1 } }));
    expect(changes).toHaveLength(1);
    expect(changes[0].copies).toBe(4);
    expect(changes[0].key).toBe("bs|false|false|NM|EN");
    expect(kindKey({ ...changes[0].kind, signed: true })).toBe("bs|false|false|NM|EN|signed");
  });

  it("reads and writes the rules as synced settings", () => {
    expect(rulesOf({})).toEqual(DEFAULT_RULES);
    const r = { maxCards: 50, keepOne: false, minValue: 2.5, keepBestForDecks: false, keepBestAlways: true };
    expect(rulesOf(rulesPrefs(r))).toEqual(r);
  });
});

function file(): SyncFile {
  const stack = (uid: string, name: string, qty: number, binder: string | null, id?: string) => ({
    item: { id: 0, card: card(name, id), foil: false, condition: "NM", language: "EN", quantity: qty, addedAt: 0, etched: false, binderId: 0, uid, updatedAt: 0, notes: null, purchasePrice: null, signed: false, altered: false, paidTyped: null, paidCurrency: null },
    binder,
  });
  return {
    format: 12,
    app: "test",
    writtenAt: 0,
    writtenBy: "test",
    data: {
      prefs: { updatedAt: 0, values: {} },
      binders: [{ id: 0, name: "Atraxa", createdAt: 0, uid: "DECK", updatedAt: 0 }],
      decks: [{ deck: { name: "Atraxa" }, cards: [{ card: { name: "Sol Ring" }, quantity: 1 }, { card: { name: "Other" }, quantity: 1, maybeboard: true }] }],
      wishlist: [{ card: { name: "Wanted // Back" }, receivedAt: null }],
      collection: [stack("a", "Sol Ring", 2, "DECK", "sr"), stack("b", "Sol Ring", 1, null, "sr"), stack("c", "Popular", 2, null)],
    },
  } as unknown as SyncFile;
}

describe("applying the trade binder (TradeBinder.apply)", () => {
  it("reads deck needs and the wishlist from the file", () => {
    const { deckNeeds, wishlist } = planInputs(file());
    expect(Object.fromEntries(deckNeeds)).toEqual({ "sol ring": 1 });
    expect([...wishlist]).toEqual(["wanted"]);
  });

  it("makes the binder, takes copies from Unsorted before a deck's binder, and remembers declines", () => {
    const f = file();
    const uid = applyTrade(
      f,
      [{ key: "sr|false|false|NM|EN", add: true, copies: 2 }],
      [{ key: "popular|false|false|NM|EN", add: true, copies: 1 }],
      1000,
      null,
    );
    expect(tradeBinderOf(f)).toBe(uid);
    expect(prefs(f).tradeBinder).toBe(uid);
    const where = collection(f).map((s) => [s.binder, s.item.card.name, s.item.quantity]);
    // The Unsorted copy first, then one from the deck's binder.
    expect(where).toContainEqual([uid, "Sol Ring", 2]);
    expect(where).toContainEqual(["DECK", "Sol Ring", 1]);
    expect([...skipsOf(prefs(f))]).toEqual(["add|popular|false|false|NM|EN"]);
    // Taking out goes to Unsorted.
    applyTrade(f, [{ key: "sr|false|false|NM|EN", add: false, copies: 1 }], [], 2000, null);
    expect(collection(f).map((s) => [s.binder, s.item.quantity])).toContainEqual([null, 1]);
    forgetDeclined(f, 3000);
    expect(skipsOf(prefs(f)).size).toBe(0);
  });
});
