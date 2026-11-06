import { describe, expect, it } from "vitest";
import { deckCards, orderedOf, parseList, searchPrecons, split } from "@/lib/precons";
import type { CardRef } from "@/lib/syncfile";

// The app's Release133Test.PreconsTest, with the same data.
const list = {
  meta: {},
  data: [
    { code: "MKC", fileName: "DeepClueSea_MKC", name: "Deep Clue Sea", releaseDate: "2024-02-09", type: "Commander Deck" },
    { code: "FRC", fileName: "MultiverseReforged_FRC", name: "Multiverse Reforged", releaseDate: "2026-10-02", type: "Commander Deck" },
    { code: "10E", fileName: "ArcanisSGuile_10E", name: "Arcanis's Guile", releaseDate: "2007-07-13", type: "Theme Deck" },
  ],
};

const card = (name: string, count: number, isFoil: boolean, setCode: string, number: string, id: string, types: string[], supertypes: string[]) => ({
  name,
  count,
  isFoil,
  setCode,
  number,
  identifiers: { scryfallId: id },
  types,
  supertypes,
});
const deck = {
  data: {
    name: "Deep Clue Sea",
    code: "MKC",
    releaseDate: "2024-02-09",
    type: "Commander Deck",
    commander: [card("Morska, Undersea Sleuth", 1, true, "MKC", "3", "morska", ["Creature"], ["Legendary"])],
    mainBoard: [
      card("Bennie Bracks, Zoologist", 1, false, "MKC", "57", "bennie", ["Creature"], ["Legendary"]),
      card("Sol Ring", 1, false, "MKC", "238", "ring", ["Artifact"], []),
      card("Island", 7, false, "MKM", "274", "island", ["Land"], ["Basic"]),
      card("Magnifying Glass", 2, false, "MKM", "255", "glass", ["Artifact"], []),
    ],
    sideBoard: [],
    tokens: [{ name: "Clue" }],
  },
};

const ref = (name: string, id: string): CardRef => ({
  scryfallId: id,
  name,
  setCode: "mkc",
  setName: "MKC",
  collectorNumber: "1",
  rarity: "rare",
  imageUrl: null,
  cardmarketId: 1,
  fallbackEur: null,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: 2,
});

describe("precons (the app's Precons.kt)", () => {
  it("lists Commander decks newest first and searches them", () => {
    const decks = parseList(list);
    expect(decks.map((d) => d.name)).toEqual(["Multiverse Reforged", "Deep Clue Sea"]);
    expect(searchPrecons(decks, "clue").map((d) => d.name)).toEqual(["Deep Clue Sea"]);
    expect(searchPrecons(decks, "mkc").map((d) => d.name)).toEqual(["Deep Clue Sea"]);
    expect(searchPrecons(decks, " ")).toHaveLength(2);
  });

  it("takes a deck's cards without basic lands", () => {
    const { cards } = deckCards(deck);
    expect(cards.map((c) => c.name)).toEqual(["Morska, Undersea Sleuth", "Bennie Bracks, Zoologist", "Sol Ring", "Magnifying Glass"]);
    expect(cards[0].isFoil).toBe(true);
    expect(cards[3].count).toBe(2);
  });

  it("splits the price by worth so it adds up", () => {
    const { cards } = deckCards(deck);
    const worth: Record<string, number | null> = { morska: 1, bennie: 8, ring: 1, glass: null };
    const shares = split(46.92, cards.map((d) => ({ card: ref(d.name, d.scryfallId!), deck: d, worth: worth[d.scryfallId!] })));
    expect(shares.reduce((n, s) => n + s.price * s.quantity, 0)).toBeCloseTo(46.92, 9);
    const by = Object.fromEntries(shares.map((s) => [s.card.name, s]));
    expect(by["Bennie Bracks, Zoologist"].price / by["Sol Ring"].price).toBeCloseTo(8, 1);
    expect(by["Magnifying Glass"].price).toBeGreaterThan(0);
    expect(by["Magnifying Glass"].quantity).toBe(2);
    expect(by["Morska, Undersea Sleuth"].finish).toBe("FOIL");
    expect(orderedOf(by["Sol Ring"])).toMatchObject({ condition: "NM", language: "EN", cardmarketId: 1 });
    expect(orderedOf(by["Morska, Undersea Sleuth"]).cardmarketId).toBe(2);
    expect(split(10, [])).toEqual([]);
  });
});
