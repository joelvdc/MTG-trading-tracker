import { describe, expect, it } from "vitest";
import { decodeSalt, tagFloor, tagName, tagText } from "../src/lib/deckshared";

// 1.120: Commander Spellbook's bracket tags, and the combo fields kept since rating version 4.
describe("combo bracket tags", () => {
  it("sets the lowest bracket of each tag's range as the floor", () => {
    expect(["R", "S", "P", "O", "C", "E", "B", null, "X"].map(tagFloor)).toEqual([4, 3, 3, 2, 2, 1, 0, 0, 0]);
  });

  it("names the tags", () => {
    expect(tagName("S")).toBe("Spicy");
    expect(tagText("S")).toBe("Spicy (bracket 3-4)");
    expect(tagText("R")).toBe("Ruthless (bracket 4+)");
    expect(tagText("E")).toBe("Exhibition (any bracket)");
    expect(tagName(null)).toBeNull();
    expect(tagText("X")).toBeNull();
  });

  it("decodes the new combo fields and leaves them out of older data", () => {
    const card = decodeSalt(
      JSON.stringify({
        combos: [
          {
            cards: ["A", "B"],
            bracketTag: "S",
            zones: ["H", "B"],
            manaNeeded: "{4}{W}",
            reenters: ["A"],
            exiledBy: ["", "Isochron Scepter"],
          },
        ],
      }),
    )!;
    expect(card.combos[0]).toMatchObject({
      bracketTag: "S",
      zones: ["H", "B"],
      manaNeeded: "{4}{W}",
      reenters: ["A"],
      exiledBy: ["", "Isochron Scepter"],
    });
    const old = decodeSalt(JSON.stringify({ combos: [{ cards: ["A", "B"] }] }))!;
    const keys = ["bracketTag", "zones", "manaNeeded", "reenters", "exiledBy"];
    expect(keys.some((k) => k in old.combos[0])).toBe(false);
  });
});
