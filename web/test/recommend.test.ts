import { describe, expect, it } from "vitest";
import { edhrecSlug, landKind, parseEdhrec, recFunction } from "@/lib/recommend";

// The app's Release118Test cases.
describe("recommendations (the app's Recommendations.kt)", () => {
  it("puts cards in recommander.cards' function categories from their text", () => {
    expect(recFunction("Sorcery", "Search your library for a card, put that card into your hand, then shuffle.")).toBe("tutors");
    expect(recFunction("Sorcery", "Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.")).toBe("ramp");
    expect(recFunction("Artifact", "{T}: Add {C}{C}.")).toBe("ramp");
    expect(recFunction("Sorcery", "Destroy all creatures. They can't be regenerated.")).toBe("mass-removal");
    expect(recFunction("Instant", "Destroy target creature or planeswalker.")).toBe("spot-removal");
    expect(recFunction("Sorcery", "Draw two cards.")).toBe("card-advantage");
    expect(recFunction("Creature — Bear", "")).toBeNull();
    expect(landKind("({T}: Add {B} or {G}.)\nThis land enters tapped.")).toBe("land");
    expect(landKind("{T}: Add {C}.\n{2}{B}, {T}: Each opponent loses 1 life.")).toBe("utility-lands");
  });

  it("makes EDHREC's commander address and reads its card lists", () => {
    expect(edhrecSlug(["Baba Lysaga, Night Witch"])).toBe("baba-lysaga-night-witch");
    expect(edhrecSlug(["Tymna the Weaver", "Kraum, Ludevic's Opus"])).toBe("kraum-ludevics-opus-tymna-the-weaver");
    expect(edhrecSlug(["Jötun Grunt"])).toBe("jotun-grunt");
    const page = parseEdhrec({
      container: {
        json_dict: {
          cardlists: [
            { header: "New Cards", tag: "newcards", cardviews: [{ name: "Fresh", id: "abc", synergy: 0.4, num_decks: 12, potential_decks: 269 }] },
            { header: "Creatures", tag: "creatures", cardviews: [{ name: "Elf" }] },
          ],
        },
      },
    });
    expect(page.map((s) => s[0])).toEqual(["newcards", "creatures"]);
    expect(page[0][2][0].potential).toBe(269);
    expect(() => parseEdhrec({})).toThrow();
  });
});
