import { describe, expect, it } from "vitest";
import { NO_REC_FILTER, recKeep, recType, type RecCard } from "@/lib/recshared";

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
