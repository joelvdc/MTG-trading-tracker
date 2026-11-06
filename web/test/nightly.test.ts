import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { accountStore, resetMemoryStore } from "@/lib/accounts";
import type { DetailsFile } from "@/lib/carddetails";
import type { CkFile } from "@/lib/cardkingdom";
import { checkAlerts, runNightly, scryfallPrintings, snapshotValues, step, type NightlyDeps } from "@/lib/nightly";
import type { PriceFile } from "@/lib/prices";
import { encode, type CardRef, type SyncFile } from "@/lib/syncfile";

const NOW = Date.UTC(2026, 10, 7, 6, 30);

const card = (id: string, cm: number | null, over: Partial<CardRef> = {}): CardRef => ({
  scryfallId: id,
  name: `Card ${id}`,
  setCode: "abc",
  setName: "Set",
  collectorNumber: "1",
  rarity: "rare",
  imageUrl: null,
  cardmarketId: cm,
  fallbackEur: null,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
  ...over,
});

// Cardmarket rows: trend, avg, low, avg1, avg7, avg30, then the same for foil.
const prices: PriceFile = {
  createdAt: "2026-11-07T02:00:00",
  madeAt: NOW,
  rates: { date: "2026-11-06", perEuro: { USD: 1.25, DKK: 7.46 } },
  products: {
    "1": [10, 9, 8, 9, 9, 9, 20, 19, 18, 19, 19, 19],
    "2": [2, 2, 1.5, 2, 2, 2, null, null, null, null, null, null],
  },
} as unknown as PriceFile;

const stack = (c: CardRef, quantity: number, binder: string | null, over: Record<string, unknown> = {}) => ({
  item: { id: 0, card: c, foil: false, condition: "NM", language: "en", quantity, addedAt: 0, etched: false, binderId: 0, uid: `s${c.scryfallId}${binder}`, updatedAt: 0, notes: null, purchasePrice: null, signed: false, altered: false, paidTyped: null, paidCurrency: null, ...over },
  binder,
});

const wish = (c: CardRef, over: Record<string, unknown> = {}) => ({
  id: 0, card: c, foil: false, quantity: 1, anyPrinting: false, notes: null, addedAt: 0, ownedAtAdd: 0, orderedAt: null, alertBelow: null, alertCurrency: null, receivedAt: null, receivedFrom: null, uid: `w${c.scryfallId}`, updatedAt: 0, ...over,
});

function file(data: Record<string, unknown>, prefs: Record<string, string> = {}): SyncFile {
  return { format: 12, app: "test", writtenAt: 0, writtenBy: "test", data: { prefs: { updatedAt: 0, values: prefs }, ...data } } as unknown as SyncFile;
}

const noDetails: DetailsFile = { madeAt: 0, cards: {} } as unknown as DetailsFile;

describe("the day's value (ValueHistory.snapshotValues)", () => {
  it("totals per price type and binder, Unsorted as ~, an empty binder at zero", () => {
    const f = file({
      binders: [{ id: 0, name: "Trade", createdAt: 0, uid: "B1", updatedAt: 0 }, { id: 0, name: "Empty", createdAt: 0, uid: "B2", updatedAt: 0 }],
      collection: [stack(card("1", 1), 2, "B1"), stack(card("2", 2), 3, null), stack(card("1", 1), 1, null, { foil: true })],
    });
    const { cards, values } = snapshotValues(f, prices, noDetails, null);
    expect(cards).toBe(6);
    expect(values.trend).toBeCloseTo(2 * 10 + 3 * 2 + 20);
    expect(values.low).toBeCloseTo(2 * 8 + 3 * 1.5 + 18);
    expect(values["b:B1:trend"]).toBeCloseTo(20);
    expect(values["b:B1:cards"]).toBe(2);
    expect(values["b:~:cards"]).toBe(4);
    expect(values["b:B2:trend"]).toBe(0);
    // No TCGplayer or Card Kingdom prices: no values for them.
    expect(Object.keys(values).some((k) => k.includes("@"))).toBe(false);
  });

  it("other sources in euros, Cardmarket's price for cards they lack, and Card Kingdom's buylist", () => {
    const f = file({ collection: [stack(card("1", 1), 1, null), stack(card("2", 2), 1, null)] });
    const ck = { listDate: "", madeAt: NOW, rows: { "1|N": [12.5, 5, 12.5, null, null, null, ""] } } as unknown as CkFile;
    const { values } = snapshotValues(f, prices, noDetails, ck);
    expect(values["trend@cardkingdom"]).toBeCloseTo(10 + 2);
    expect(values["low@cardkingdom"]).toBeCloseTo(10 + 1.5);
    expect(values["pays@cardkingdom"]).toBeCloseTo(4);
  });

  it("follows the condition setting like the app", () => {
    const f = file({ collection: [stack(card("1", 1), 1, null, { condition: "EX" })] }, { conditionAdjust: "true", conditionPcts: "EX=50" });
    expect(snapshotValues(f, prices, noDetails, null).values.trend).toBeCloseTo(5);
  });
});

describe("price alerts (PriceAlerts)", () => {
  it("step: once at the level, then quiet until the price is above it again", () => {
    expect(step(5, 4, null)).toEqual({ notify: true, alertedLevel: 5 });
    expect(step(5, 4, 5)).toEqual({ notify: false, alertedLevel: 5 });
    expect(step(5, 6, 5)).toEqual({ notify: false, alertedLevel: null });
    expect(step(5, null, 5)).toEqual({ notify: false, alertedLevel: 5 });
    expect(step(null, 1, 5)).toEqual({ notify: false, alertedLevel: null });
  });

  it("checks at the alert price type, in the level's currency, and skips ordered cards", async () => {
    const f = file(
      {
        wishlist: [
          wish(card("1", 1), { alertBelow: 8 }), // low 8: reached
          wish(card("2", 2), { alertBelow: 10, alertCurrency: "DKK" }), // 10 DKK = 1.34 €, low 1.5: not reached
          wish(card("3", 1), { alertBelow: 100, orderedAt: 1 }),
        ],
      },
      { alertPriceType: "low" },
    );
    const none = async () => null;
    const first = await checkAlerts(f, prices, new Map(), NOW, none);
    expect(first.events).toEqual([{ wishUid: "w1", cardName: "Card 1", at: NOW, price: 8, level: 8, basis: "cardmarket:low", setCode: null }]);
    expect(Object.fromEntries(first.levels)).toEqual({ w1: 8 });
    const again = await checkAlerts(f, prices, first.levels, NOW + 1, none);
    expect(again.events).toEqual([]);
    expect(Object.fromEntries(again.levels)).toEqual({ w1: 8 });
  });

  it("any printing: the cheapest printing in the wished finish", async () => {
    const f = file({ wishlist: [wish(card("1", 1), { alertBelow: 3, anyPrinting: true })] }, { alertPriceType: "trend" });
    const printings = async () => [
      { id: "1", set: "abc", cm: 1, cmFoil: null, nonfoil: true, foil: true, etched: false },
      { id: "2", set: "xyz", cm: 2, cmFoil: null, nonfoil: true, foil: false, etched: false },
    ];
    const r = await checkAlerts(f, prices, new Map(), NOW, printings);
    expect(r.events[0]).toMatchObject({ price: 2, setCode: "xyz", basis: "cardmarket:trend" });
  });

  it("looks printings up on Scryfall like the app (paper only, double-faced by front face)", async () => {
    const urls: string[] = [];
    const fake = (async (url: string) => {
      urls.push(decodeURIComponent(url));
      if (urls.length === 1) return new Response("{}", { status: 404 });
      return Response.json({ data: [{ id: "a", set: "s1", cardmarket_id: 5, finishes: ["nonfoil", "foil"] }, { id: "b", set: "s2", digital: true }], has_more: false });
    }) as typeof fetch;
    const got = await scryfallPrintings(fake)("Front // Back");
    expect(urls[1]).toContain('!"Front"+game:paper');
    expect(got).toEqual([{ id: "a", set: "s1", cm: 5, cmFoil: null, nonfoil: true, foil: true, etched: false }]);
  });
});

describe("the nightly job", () => {
  beforeEach(() => {
    process.env.DATABASE_URL = "memory";
    resetMemoryStore();
  });
  afterEach(() => {
    delete process.env.DATABASE_URL;
    resetMemoryStore();
  });

  it("saves every account's day and alerts, and its counts", async () => {
    const store = accountStore()!;
    const a = (await store.create("joel", "h", true, NOW))!;
    await store.create("empty", "h", false, NOW);
    const f = file({ collection: [stack(card("1", 1), 2, null)], wishlist: [wish(card("1", 1), { alertBelow: 9 })] });
    await store.putSync(a.id, new Uint8Array(encode(f)), null, NOW);
    const looked: string[][] = [];
    const deps: NightlyDeps = {
      prices: async () => prices,
      details: async () => noDetails,
      ck: async () => null,
      addDetails: async (ids) => void looked.push(ids),
      printingsOf: async () => null,
    };
    const r = await runNightly(NOW, deps);
    expect(r).toEqual({ day: "2026-11-07", accounts: 1, values: 1, alertsChecked: 1, alertsReached: 1, failed: 0 });
    expect(looked).toEqual([["1"]]);
    expect(await store.valuePoints(a.id, "trend", null)).toEqual([{ day: "2026-11-07", value: 20, cards: 2 }]);
    expect((await store.alertEvents(a.id)).map((e) => e.price)).toEqual([8]);
    expect((await store.job("nightly"))?.info).toMatchObject({ accounts: 1, alertsReached: 1 });
  });
});

describe("the phone's value history", () => {
  it("takes well-formed days only", async () => {
    const { cleanDay } = await import("@/lib/nightly");
    expect(cleanDay({ day: "2026-01-02", cards: 3.4, values: { trend: 1.5, "b:~:trend": 1, bad: "x" } })).toEqual({ day: "2026-01-02", cards: 3, values: { trend: 1.5, "b:~:trend": 1 } });
    expect(cleanDay({ day: "2 Jan", cards: 1, values: {} })).toBeNull();
    expect(cleanDay({ day: "2026-01-02", cards: -1, values: {} })).toBeNull();
    expect(cleanDay(null)).toBeNull();
  });
});
