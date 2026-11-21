import { describe, expect, it } from "vitest";
import { COLUMNS } from "@/lib/collcolumns";
import {
  comparator,
  deckCardNames,
  deckChoices,
  deckUse,
  EMPTY_FILTER,
  filterCount,
  matchesFilter,
  SORT_FIELDS,
  type Filter,
} from "@/lib/colquery";
import type { CollDeck, CollRow } from "@/lib/collview";

// The collection's Deck column and "Used by deck" filter (website, 2026-11-21): the active decks by name.

function row(uid: string, name: string, quantity = 1): CollRow {
  return {
    uid,
    card: { scryfallId: uid, name, setCode: "c21", setName: "Commander 2021", collectorNumber: "1", rarity: "rare", imageUrl: null, cardmarketId: 1, fallbackEur: 1, fallbackEurFoil: null, hasNonFoil: true, hasFoil: false, foilType: null, hasEtched: false, flavorName: null, cardmarketFoilId: null },
    finish: "NONFOIL", condition: "NM", language: "EN", quantity, addedAt: 0, binder: null, notes: null, purchasePrice: null, paidTyped: null, paidCurrency: null,
    signed: false, altered: false, unitEur: 1, cmEur: 1, approx: false, condNote: null, changeEur: null, changePct: null,
    srcEur: { cardmarket: 1, tcgplayer: null, cardkingdom: null, ckPays: null }, info: null,
  };
}

const sol = row("s", "Sol Ring");
const bolt = row("b", "Lightning Bolt");
const arcane = row("a", "Arcane Signet");
const forest = row("f", "Forest");
const rows = [sol, bolt, arcane, forest];
const decks: CollDeck[] = [
  { name: "Zoo", owner: "", cards: { "lightning bolt": 4, "forest": 10 }, id: 1 },
  { name: "Auntie Plague", owner: "", cards: { "sol ring": 1 }, id: -2 },
  { name: "Lightning", owner: "", cards: { "arcane signet": 1, "lightning bolt": 1 }, id: 3 },
];
const use = deckUse(rows, decks);
const names = deckCardNames(decks);

const shown = (f: Partial<Filter>) => rows.filter((r) => matchesFilter({ ...EMPTY_FILTER, ...f }, r, names, use)).map((r) => r.uid);

describe("the Deck column", () => {
  it("is offered in the Decks group and sorts by the first deck's name", () => {
    const col = COLUMNS.find((c) => c.id === "deckNames")!;
    expect(col).toMatchObject({ label: "Deck", group: "Decks", field: "DECK" });
    expect(SORT_FIELDS.some((f) => f.field === "DECK")).toBe(true);
  });

  it("lists each card's active decks A to Z, and none for a card in no deck", () => {
    expect(use.get("lightning bolt")!.decks).toEqual(["Lightning", "Zoo"]);
    expect(use.get("sol ring")!.decks).toEqual(["Auntie Plague"]);
    expect(use.get("forest")!.decks).toEqual(["Zoo"]);
    expect(deckChoices(decks)).toEqual(["Auntie Plague", "Lightning", "Zoo"]);
  });

  it("sorts by the first deck name, cards in no deck last either way", () => {
    const spare = row("x", "Counterspell");
    const all = [...rows, spare];
    const useAll = deckUse(all, decks);
    const keys = (reversed: boolean) => [...all].sort(comparator([{ field: "DECK", reversed }], useAll)).map((r) => r.uid);
    // Auntie Plague (Sol Ring), Lightning (Arcane Signet, Bolt), Zoo (Forest); the card in no deck last.
    expect(keys(false)).toEqual(["s", "a", "b", "f", "x"]);
    // Reversed, Z to A (a tie within one deck reads Z to A too), and a card in no deck still last.
    expect(keys(true)).toEqual(["f", "a", "b", "s", "x"]);
  });
});

describe("the Used by deck filter", () => {
  it("keeps only the cards the named deck uses, any case", () => {
    expect(shown({ deck: "zoo" })).toEqual(["b", "f"]);
    expect(shown({ deck: "ZOO" })).toEqual(["b", "f"]);
    expect(shown({ deck: "AUNTIE PLAGUE" })).toEqual(["s"]);
    expect(shown({ deck: "Lightning" })).toEqual(["b", "a"]);
    expect(shown({ deck: "No such deck" })).toEqual([]);
  });

  it("gives every card when no deck is picked", () => {
    expect(shown({ deck: null })).toEqual(["s", "b", "a", "f"]);
    expect(shown({})).toEqual(["s", "b", "a", "f"]);
  });

  it("still works with the other deck choices", () => {
    expect(shown({ deck: "Lightning", decks: "IN" })).toEqual(["b", "a"]);
    expect(shown({ deck: "Zoo", decks: "IN" })).toEqual(["b", "f"]);
    // Spare with one deck: the cards it uses that you own more copies of than it uses.
    const more = [sol, row("s2", "Sol Ring"), bolt, arcane, forest];
    const useMore = deckUse(more, decks);
    const spareSol = more.filter((r) => matchesFilter({ ...EMPTY_FILTER, decks: "SPARE", deck: "auntie plague" }, r, names, useMore)).map((r) => r.uid);
    expect(spareSol).toEqual(["s", "s2"]);
  });

  it("counts as one part of the filter", () => {
    expect(filterCount({ ...EMPTY_FILTER, deck: "Zoo" })).toBe(1);
    expect(filterCount(EMPTY_FILTER)).toBe(0);
  });

  it("takes a filter saved before the deck choice (no deck: every card)", () => {
    // What an old saved view or filter holds: no "deck" at all.
    const old = { colors: [], colorMatch: "ANY", types: [], rarities: [], sets: [], finishes: [], conditions: [], languages: [], minPrice: null, maxPrice: null, decks: "ANY", marks: [], faces: [] } as Filter;
    expect(old.deck).toBeUndefined();
    expect(rows.filter((r) => matchesFilter(old, r, names, use)).map((r) => r.uid)).toEqual(["s", "b", "a", "f"]);
    expect(filterCount(old)).toBe(0);
  });

  it("lets every card through when the decks' use isn't there", () => {
    expect(rows.filter((r) => matchesFilter({ ...EMPTY_FILTER, deck: "Zoo" }, r, names)).map((r) => r.uid)).toEqual(["s", "b", "a", "f"]);
  });
});
