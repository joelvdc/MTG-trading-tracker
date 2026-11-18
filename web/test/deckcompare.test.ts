import { describe, expect, it } from "vitest";
import { cardsByType, compareDecks, compareKey, compareText, type CompareCard, type CompareLine } from "@/lib/deckcompare";

/** A deck line; a plain card unless the options say otherwise. */
const line = (name: string, quantity = 1, types = "Instant", commander = false): CompareLine => ({ name, quantity, types, commander });

/** A commander's line. */
const commander = (name: string, types = "Legendary Creature"): CompareLine => line(name, 1, types, true);

/** Just a card's name and copies, for the short expectations below. */
const short = (cards: CompareCard[]) => cards.map((c) => `${c.quantity} ${c.name}`);

describe("compareKey", () => {
  it("takes the front face, trimmed and lower case", () => {
    expect(compareKey("Fire // Ice")).toBe("fire");
    expect(compareKey("FIRE ")).toBe("fire");
    expect(compareKey(" Sol  Ring ")).toBe("sol  ring");
    expect(compareKey("Fire/Ice")).toBe("fire/ice");
  });
});

describe("comparing a deck with another", () => {
  it("counts different printings of a card on separate lines as one card", () => {
    // Two printings of Sol Ring and a foil one: three copies in my deck, which the other deck has one of.
    const mine = [line("Sol Ring", 1, "Artifact"), line("Sol Ring", 1, "Artifact"), line("Sol Ring", 1, "Artifact")];
    const other = [line("Sol Ring", 1, "Artifact")];
    const r = compareDecks(mine, other, new Map());
    expect(short(r.both)).toEqual(["1 Sol Ring"]);
    expect(short(r.remove)).toEqual(["2 Sol Ring"]);
    expect(short(r.add)).toEqual([]);
    expect(r.mineCount).toBe(3);
    expect(r.otherCount).toBe(1);
  });

  it("matches Fire // Ice with Fire, in any letter case", () => {
    const mine = [line("Fire // Ice", 1, "Instant"), line("FIRE ", 1, "Instant")];
    const other = [line("fire", 3, "Instant")];
    const r = compareDecks(mine, other, new Map());
    // Two copies in mine, three in the other: one to add. My deck's name is used.
    expect(short(r.both)).toEqual(["2 Fire // Ice"]);
    expect(short(r.add)).toEqual(["1 fire"]);
    expect(short(r.remove)).toEqual([]);
  });

  it("adds the copies the other deck has more of", () => {
    const mine = [line("Forest", 3, "Basic Land")];
    const other = [line("Forest", 5, "Basic Land")];
    const r = compareDecks(mine, other, new Map());
    expect(short(r.add)).toEqual(["2 Forest"]);
    expect(short(r.both)).toEqual(["3 Forest"]);
    expect(short(r.remove)).toEqual([]);
  });

  it("removes the copies my deck has more of", () => {
    const mine = [line("Forest", 5, "Basic Land")];
    const other = [line("Forest", 3, "Basic Land")];
    const r = compareDecks(mine, other, new Map());
    expect(short(r.remove)).toEqual(["2 Forest"]);
    expect(short(r.both)).toEqual(["3 Forest"]);
    expect(short(r.add)).toEqual([]);
  });

  it("never adds or removes a different commander, and says the commanders differ", () => {
    const mine = [commander("Kenrith, the Returned King"), line("Sol Ring", 1, "Artifact")];
    const other = [commander("Atraxa, Praetors' Voice"), line("Sol Ring", 1, "Artifact")];
    const r = compareDecks(mine, other, new Map());
    expect(r.commanders).toEqual({ mine: ["Kenrith, the Returned King"], other: ["Atraxa, Praetors' Voice"], differ: true });
    expect(short(r.add)).toEqual([]);
    expect(short(r.remove)).toEqual([]);
    expect(short(r.both)).toEqual(["1 Sol Ring"]);
  });

  it("counts my commander in the other deck's main list as both", () => {
    const mine = [commander("Kenrith, the Returned King")];
    // The other deck plays it without the commander mark, as one of its 99.
    const other = [line("Kenrith, the Returned King", 1, "Legendary Creature")];
    const r = compareDecks(mine, other, new Map());
    expect(short(r.both)).toEqual(["1 Kenrith, the Returned King"]);
    expect(short(r.add)).toEqual([]);
    expect(short(r.remove)).toEqual([]);
    // Only mine is known to be a commander: the other list gives none.
    expect(r.commanders).toEqual({ mine: ["Kenrith, the Returned King"], other: [], differ: false });
  });

  it("takes a pasted list without commanders as not differing, and keeps my commander", () => {
    const mine = [commander("Kenrith, the Returned King"), line("Sol Ring", 1, "Artifact"), line("Cultivate", 1, "Sorcery")];
    const pasted = [line("Sol Ring", 1, "Artifact")];
    const r = compareDecks(mine, pasted, new Map());
    expect(r.commanders).toEqual({ mine: ["Kenrith, the Returned King"], other: [], differ: false });
    // My commander isn't asked to be removed, even though the pasted list doesn't have it.
    expect(short(r.remove)).toEqual(["1 Cultivate"]);
    expect(short(r.add)).toEqual([]);
    expect(short(r.both)).toEqual(["1 Sol Ring"]);
  });

  it("puts the same commander on both sides in both, without a difference", () => {
    const mine = [commander("Kenrith, the Returned King"), line("Sol Ring", 1, "Artifact")];
    const other = [commander("kenrith, the returned king"), line("Sol Ring", 1, "Artifact")];
    const r = compareDecks(mine, other, new Map());
    expect(short(r.both)).toEqual(["1 Kenrith, the Returned King", "1 Sol Ring"]);
    expect(short(r.add)).toEqual([]);
    expect(short(r.remove)).toEqual([]);
    expect(r.commanders.differ).toBe(false);
    expect(r.commanders.mine).toEqual(["Kenrith, the Returned King"]);
    expect(r.commanders.other).toEqual(["kenrith, the returned king"]);
  });

  it("adds the owned counts up across a double-faced name", () => {
    const mine = [line("Fire // Ice", 1, "Instant")];
    const other = [line("Fire // Ice", 3, "Instant")];
    // The collection knows the full name in small letters: its copies count for "fire".
    const owned = new Map([["fire // ice", 2]]);
    const r = compareDecks(mine, other, owned);
    expect(short(r.add)).toEqual(["2 Fire // Ice"]);
    expect(r.add[0].owned).toBe(2);
    expect(r.both[0].owned).toBe(2);
    // Another printing of the card, counted apart, adds up to the same key.
    const split = new Map([
      ["fire // ice", 1],
      ["Fire // Ice", 2],
    ]);
    expect(compareDecks(mine, other, split).add[0].owned).toBe(3);
    // A card the collection doesn't have is on 0.
    expect(compareDecks(mine, [line("Sol Ring", 1, "Artifact")], owned).add[0].owned).toBe(0);
  });

  it("ignores lines with a quantity of 0 or less", () => {
    const mine = [line("Sol Ring", 0, "Artifact"), line("Cultivate", -1, "Sorcery"), line("Forest", 2, "Basic Land")];
    const other = [line("Forest", 2, "Basic Land"), line("Sol Ring", 0, "Artifact")];
    const r = compareDecks(mine, other, new Map());
    expect(r.mineCount).toBe(2);
    expect(r.otherCount).toBe(2);
    expect(short(r.add)).toEqual([]);
    expect(short(r.remove)).toEqual([]);
    expect(short(r.both)).toEqual(["2 Forest"]);
  });

  it("counts the copies in each deck, commanders included", () => {
    const mine = [commander("Kenrith, the Returned King"), line("Sol Ring", 2, "Artifact"), line("Forest", 3, "Basic Land")];
    const other = [commander("Atraxa, Praetors' Voice"), line("Sol Ring", 1, "Artifact")];
    const r = compareDecks(mine, other, new Map());
    expect(r.mineCount).toBe(6);
    expect(r.otherCount).toBe(2);
    // An empty deck has nothing.
    const empty = compareDecks([], [], new Map());
    expect([empty.mineCount, empty.otherCount]).toEqual([0, 0]);
    expect([empty.add, empty.remove, empty.both]).toEqual([[], [], []]);
  });

  it("sorts the lists by name, ignoring case, and gives the cards their types and owners", () => {
    const owned = new Map([["sol ring", 4]]);
    const mine = [commander("Kenrith, the Returned King"), line("sol Ring", 1, "Artifact"), line("Arcane Signet", 1, "Artifact"), line("Zulaport Cutthroat", 1, "Creature")];
    const other = [
      commander("Atraxa, Praetors' Voice"),
      line("Sol Ring", 1, "Artifact"),
      line("Beast Within", 1, "Instant"),
      line("Cultivate", 2, "Sorcery"),
      line("Zulaport Cutthroat", 1, "Creature"),
    ];
    const r = compareDecks(mine, other, owned);
    expect(short(r.add)).toEqual(["1 Beast Within", "2 Cultivate"]);
    expect(short(r.remove)).toEqual(["1 Arcane Signet"]);
    // My deck's name and types are used for both.
    expect(short(r.both)).toEqual(["1 sol Ring", "1 Zulaport Cutthroat"]);
    expect(r.add.map((c) => c.type)).toEqual(["Instant", "Sorcery"]);
    expect(r.both.map((c) => c.type)).toEqual(["Artifact", "Creature"]);
    // The first line seen gives the name: "sol Ring" stays as I wrote it.
    expect(r.both[0].owned).toBe(4);
    expect(r.add[0].owned).toBe(0);
  });
});

describe("grouping and copying a comparison", () => {
  const card = (name: string, quantity: number, type: string): CompareCard => ({ name, quantity, type, owned: 0 });

  it("groups the cards by type in TYPE_ORDER, then Other, with their counts", () => {
    const cards = [
      card("Cultivate", 2, "Sorcery"),
      card("Sol Ring", 1, "Artifact"),
      card("Arcane Signet", 1, "Artifact"),
      card("Beast Within", 1, "Instant"),
      card("Forest", 5, "Land"),
      card("Atraxa, Praetors' Voice", 1, "Creature"),
      card("Arcane Denial", 1, "Other"),
    ];
    const groups = cardsByType(cards);
    expect(groups.map((g) => g.type)).toEqual(["Creature", "Land", "Instant", "Sorcery", "Artifact", "Other"]);
    expect(groups.map((g) => g.count)).toEqual([1, 5, 1, 2, 2, 1]);
    // The cards keep the order they were given in.
    expect(short(groups[4].cards)).toEqual(["1 Sol Ring", "1 Arcane Signet"]);
    // A type not in TYPE_ORDER goes last, and empty groups are left out.
    expect(cardsByType([card("Ranger's Guile", 1, "Custom")]).map((g) => g.type)).toEqual(["Custom"]);
    expect(cardsByType([])).toEqual([]);
  });

  it("writes the cards as a list to copy", () => {
    expect(compareText([card("Sol Ring", 2, "Artifact"), card("Cultivate", 1, "Sorcery")])).toBe("2 Sol Ring\n1 Cultivate");
    expect(compareText([])).toBe("");
  });
});
