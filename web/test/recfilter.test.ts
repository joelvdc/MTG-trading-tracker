import { describe, expect, it } from "vitest";
import { NO_REC_FILTER, recKeep, recsByCard, recType, type RecCard } from "@/lib/recshared";

// The recommendation filters, as the app's RecFilter.keep (RecommendationsScreen.kt).
const rec = (name: string, typeLine: string, isNew = false, owned = 0): RecCard => ({
  name,
  card: null,
  typeLine,
  cmc: 2,
  manaCost: null,
  score: null,
  synergy: null,
  decks: null,
  potentialDecks: null,
  isNew,
  releasedAt: null,
  owned,
});

const cards = [
  rec("Sol Ring", "Artifact", false, 2),
  rec("Esper Sentinel", "Artifact Creature — Human Soldier", true),
  rec("Fable of the Mirror-Breaker // Reflection of Kiki-Jiki", "Enchantment — Saga // Enchantment Creature — Goblin Shaman"),
  rec("Command Tower", "Land", true, 1),
];
const names = (f: Partial<typeof NO_REC_FILTER>) => cards.filter((c) => recKeep({ ...NO_REC_FILTER, ...f }, c)).map((c) => c.name.split(" // ")[0]);

describe("recommendation filters", () => {
  it("keep everything by default", () => {
    expect(names({})).toHaveLength(4);
  });

  it("filter on new cards and on owning the card", () => {
    expect(names({ newOnly: true })).toEqual(["Esper Sentinel", "Command Tower"]);
    expect(names({ owned: "mine" })).toEqual(["Sol Ring", "Command Tower"]);
    expect(names({ owned: "notMine" })).toEqual(["Esper Sentinel", "Fable of the Mirror-Breaker"]);
    expect(names({ owned: "mine", newOnly: true })).toEqual(["Command Tower"]);
  });

  it("filter on the main type of the front face, creatures first", () => {
    expect(recType(cards[1])).toBe("Creature");
    expect(recType(cards[2])).toBe("Enchantment");
    expect(names({ type: "Artifact" })).toEqual(["Sol Ring"]);
    expect(names({ type: "Creature" })).toEqual(["Esper Sentinel"]);
    expect(names({ type: "Land" })).toEqual(["Command Tower"]);
  });
});

describe("recommendations for all decks (recsByCard)", () => {
  const result = (fetchedAt: number, ...names: [string, number?][]) => ({
    source: "edhrec" as const,
    fetchedAt,
    note: null,
    sections: [{ key: "a", title: "A", cards: names.map(([n, owned]) => rec(n, "Artifact", false, owned ?? 0)) }],
  });
  const all = [
    { deckId: 1, result: result(100, ["Sol Ring", 1], ["Mind Stone"]) },
    { deckId: -2, result: result(200, ["Mind Stone", 3], ["Fellwar Stone"], ["Sol Ring", 2]) },
    { deckId: 3, result: result(50, ["Mind Stone"], ["Sol Ring // Back"]) },
  ];

  it("lists every card once with its decks, the most decks first", () => {
    const by = recsByCard(all, () => true);
    expect(by.map((x) => [x.card.name, x.deckIds])).toEqual([
      ["Sol Ring", [1, -2, 3]],
      ["Mind Stone", [1, -2, 3]],
      ["Fellwar Stone", [-2]],
    ]);
    // What you own comes from the newest fetch.
    expect(by[0].card.owned).toBe(2);
    expect(by[1].card.owned).toBe(3);
  });

  it("keeps only cards for several decks, and filters", () => {
    expect(recsByCard(all, () => true, true).map((x) => x.card.name)).toEqual(["Sol Ring", "Mind Stone"]);
    expect(recsByCard(all, (c) => recKeep({ ...NO_REC_FILTER, owned: "notMine" }, c)).map((x) => [x.card.name, x.deckIds])).toEqual([
      ["Fellwar Stone", [-2]],
    ]);
  });
});
