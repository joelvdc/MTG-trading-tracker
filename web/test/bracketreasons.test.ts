import { describe, expect, it } from "vitest";
import { bracketReasons, decodeSalt, GROUP_TITLES, type SaltCard, type SaltCombo } from "@/lib/deckshared";

function saltCard(over: Partial<SaltCard> = {}): SaltCard {
  const base = decodeSalt(
    JSON.stringify({ gameChangers: [], twoCardCombos: [], earlyCombos: [], extraTurns: [], massLandDenial: [], axes: {}, notes: [], combos: [] }),
  )!;
  return { ...base, ...over };
}

/** A combo of SaltCard.combos; the rating's own data for one of Commander Salt's combos. */
function combo(cards: string[], over: Partial<SaltCombo> = {}): SaltCombo {
  return { cards, counts: cards.length === 2, results: [], needsBoard: false, spellbookUri: null, prerequisites: [], ...over };
}

describe("bracketReasons", () => {
  it("uses the rating's own lines when it has some", () => {
    const card = saltCard({ source: "app", bracket: 3, bracketWhy: ["2 Game Changers: Rhystic Study, Smothering Tithe"] });
    expect(bracketReasons(card, 3)).toEqual(["2 Game Changers: Rhystic Study, Smothering Tithe"]);
  });

  it("has nothing without a bracket", () => {
    expect(bracketReasons(saltCard({ gameChangers: ["Rhystic Study"] }), null)).toEqual([]);
  });

  it("says so for bracket 2 or less", () => {
    expect(bracketReasons(saltCard(), 2)).toEqual(["No Game Changers or two-card combos"]);
    expect(bracketReasons(saltCard({ gameChangers: ["Rhystic Study"] }), 1)).toEqual(["No Game Changers or two-card combos"]);
  });

  it("names Commander Salt's two-card combos for bracket 3, with what the rating knows of them", () => {
    const card = saltCard({
      twoCardCombos: [
        ["Late A", "Late B"],
        ["Quick A", "Quick B"],
        ["Dud A", "Dud B"],
        ["Plain A", "Plain B"],
      ],
      combos: [
        combo(["Late A", "Late B"], { wins: true, manaValue: 9 }),
        combo(["Quick A", "Quick B"], { wins: true, manaValue: 6 }),
        combo(["Dud A", "Dud B"], { wins: false, manaValue: 4 }),
      ],
      gameChangers: ["Rhystic Study"],
    });
    expect(bracketReasons(card, 3)).toEqual([
      "Late A + Late B: wins, mana value 9",
      "Quick A + Quick B: wins, mana value 6",
      "Dud A + Dud B: doesn't win",
      "and 1 more two-card combos",
      "1 Game Changer: Rhystic Study",
    ]);
  });

  it("just names the combos for bracket 3 without the rating's data, and counts the tutors", () => {
    const card = saltCard({
      twoCardCombos: [["A", "B"], ["C", "D"]],
      gameChangers: ["X", "Y"],
      tutors: ["Vampiric Tutor", "Demonic Tutor", "Mystical Tutor", "Worldly Tutor", "Enlightened Tutor"],
    });
    expect(bracketReasons(card, 3)).toEqual(["A + B", "C + D", "2 Game Changers: X, Y", "5 tutors"]);
    // Fewer than five tutors aren't a reason.
    expect(bracketReasons(saltCard({ tutors: ["Vampiric Tutor", "Demonic Tutor"] }), 3)).toEqual([]);
  });

  it("names Commander Salt's early combos for bracket 4, then the Game Changers, land denial and extra turns", () => {
    const card = saltCard({
      earlyCombos: [
        ["Thopter Foundry", "Sword of the Meek"],
        ["Dockside Extortionist", "Temur Sabertooth"],
        ["A", "B"],
      ],
      combos: [combo(["Thopter Foundry", "Sword of the Meek"], { wins: true, manaValue: 6 })],
      gameChangers: ["Axe of the Gargoyle", "Rhystic Study"],
      massLandDenial: ["Armageddon"],
      extraTurns: ["Time Warp", "Nexus of Fate", "Temporal Manipulation"],
    });
    expect(bracketReasons(card, 4)).toEqual([
      "Thopter Foundry + Sword of the Meek: wins, mana value 6",
      "Dockside Extortionist + Temur Sabertooth",
      "A + B",
      "2 Game Changers: Axe of the Gargoyle, Rhystic Study",
      "Mass land denial: Armageddon",
      "3 extra-turn cards: Time Warp, Nexus of Fate, Temporal Manipulation",
    ]);
  });

  it("lists the first three early combos of bracket 4, then how many more", () => {
    const card = saltCard({ earlyCombos: [["A", "B"], ["C", "D"], ["E", "F"], ["G", "H"]] });
    expect(bracketReasons(card, 4)).toEqual(["A + B", "C + D", "E + F", "and 1 more early combos"]);
  });

  it("prints a mana value only when it isn't whole", () => {
    const card = saltCard({ twoCardCombos: [["A", "B"]], combos: [combo(["A", "B"], { wins: true, manaValue: 5.5 })] });
    expect(bracketReasons(card, 3)).toEqual(["A + B: wins, mana value 5.5"]);
  });

  it("titles the group of other users' decks", () => {
    expect(GROUP_TITLES.OTHERS).toBe("Archidekt (other users)");
  });
});
