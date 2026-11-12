import { describe, expect, it } from "vitest";
import { deckCardNames, deckUse, EMPTY_FILTER, filterCount, matchesFilter, type Filter } from "@/lib/colquery";
import type { CollDeck, CollRow } from "@/lib/collview";

// The website's Spare choice in the Filter window (2026-11-13): more copies owned (every printing) than the decks use.

function row(uid: string, name: string, quantity: number): CollRow {
  return {
    uid,
    card: { scryfallId: uid, name, setCode: "c21", setName: "Commander 2021", collectorNumber: "1", rarity: "rare", imageUrl: null, cardmarketId: 1, fallbackEur: 1, fallbackEurFoil: null, hasNonFoil: true, hasFoil: false, foilType: null, hasEtched: false, flavorName: null, cardmarketFoilId: null },
    finish: "NONFOIL", condition: "NM", language: "EN", quantity, addedAt: 0, binder: null, notes: null, purchasePrice: null, paidTyped: null, paidCurrency: null,
    signed: false, altered: false, unitEur: 1, cmEur: 1, approx: false, condNote: null, changeEur: null, changePct: null,
    srcEur: { cardmarket: 1, tcgplayer: null, cardkingdom: null, ckPays: null }, info: null,
  };
}

const solA = row("s1", "Sol Ring", 1);
const solB = row("s2", "Sol Ring", 2); // 3 Sol Rings, 2 used
const arcane = row("a1", "Arcane Signet", 1); // 1 owned, 1 used
const bolt = row("b1", "Lightning Bolt", 1); // in no deck
const rows = [solA, solB, arcane, bolt];
const decks: CollDeck[] = [
  { name: "One", owner: "", cards: { "sol ring": 1, "arcane signet": 1 } },
  { name: "Two", owner: "", cards: { "sol ring": 1 } },
];
const use = deckUse(rows, decks);
const names = deckCardNames(decks);
const spare: Filter = { ...EMPTY_FILTER, decks: "SPARE" };

describe("Spare filter", () => {
  it("keeps cards owned more often than the decks use them, every printing counted", () => {
    expect(rows.filter((r) => matchesFilter(spare, r, names, use)).map((r) => r.uid)).toEqual(["s1", "s2", "b1"]);
  });

  it("counts as one filter part", () => {
    expect(filterCount(spare)).toBe(1);
  });

  it("still has In decks and No deck", () => {
    expect(rows.filter((r) => matchesFilter({ ...EMPTY_FILTER, decks: "OUT" }, r, names, use)).map((r) => r.uid)).toEqual(["b1"]);
    expect(rows.filter((r) => matchesFilter({ ...EMPTY_FILTER, decks: "IN" }, r, names, use)).map((r) => r.uid)).toEqual(["s1", "s2", "a1"]);
  });
});
