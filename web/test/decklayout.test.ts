import { describe, expect, it } from "vitest";
import { parsePrefs, DEFAULT_PREFS } from "@/components/DeckViewOptions";
import { backImageUrl, costColours, hasBackFace, layoutSections, sortRows } from "@/lib/decklayout";

const line = (name: string, cmc: number, over: Partial<{ commander: boolean; category: string; types: string; cost: string }> = {}) => ({
  name,
  cmc,
  commander: over.commander ?? false,
  category: over.category ?? "Ramp",
  types: over.types ?? "Creature",
  cost: over.cost ?? "",
});
type Line = ReturnType<typeof line>;
const colours = (r: Line) => costColours(r.cost);

describe("sortRows", () => {
  const rows = [line("Beta", 3), line("alpha", 3), line("Gamma", 1)];
  it("sorts by mana value, then name", () => {
    expect(sortRows(rows, "MANA_VALUE", false).map((r) => r.name)).toEqual(["Gamma", "alpha", "Beta"]);
  });
  it("sorts by name, ignoring case", () => {
    expect(sortRows(rows, "NAME", false).map((r) => r.name)).toEqual(["alpha", "Beta", "Gamma"]);
  });
  it("turns either order round when descending", () => {
    expect(sortRows(rows, "MANA_VALUE", true).map((r) => r.name)).toEqual(["Beta", "alpha", "Gamma"]);
    expect(sortRows(rows, "NAME", true).map((r) => r.name)).toEqual(["Gamma", "Beta", "alpha"]);
  });
});

describe("costColours", () => {
  it("finds colours in WUBRG order, hybrids and both faces included", () => {
    expect(costColours("{2}{G}{W/U}")).toBe("WUG");
    expect(costColours("{1}{B} // {R}")).toBe("BR");
    expect(costColours("{3}")).toBe("");
    expect(costColours(null)).toBe("");
  });
});

describe("layoutSections", () => {
  const rows = [
    line("Sol Ring", 1),
    line("Bolt", 1, { cost: "{R}" }),
    line("Wrath", 4, { cost: "{2}{W}{W}" }),
    line("Fireball", 4, { cost: "{X}{R}" }),
    line("Charm", 3, { cost: "{W}{U}{B}" }),
    line("Atraxa", 4, { commander: true, cost: "{G}{W}{U}{B}" }),
  ];
  it("groups by mana value with the commander first and each group sorted", () => {
    const s = layoutSections(rows, "MANA_VALUE", "NAME", false, colours);
    expect(s.map((x) => x.title)).toEqual(["Commander", "Mana value 1", "Mana value 3", "Mana value 4"]);
    expect(s[1].rows.map((r) => r.name)).toEqual(["Bolt", "Sol Ring"]);
    expect(s[3].rows.map((r) => r.name)).toEqual(["Fireball", "Wrath"]);
  });
  it("groups by colour: WUBRG, then multicolour, then colourless", () => {
    const s = layoutSections(rows, "COLOUR", "MANA_VALUE", false, colours);
    expect(s.map((x) => x.title)).toEqual(["Commander", "White", "Red", "Multicolour", "Colourless"]);
    expect(s[3].rows.map((r) => r.name)).toEqual(["Charm"]);
  });
  it("sorts descending within every group", () => {
    const s = layoutSections(rows, "COLOUR", "NAME", true, colours);
    expect(s.find((x) => x.title === "Red")!.rows.map((r) => r.name)).toEqual(["Fireball", "Bolt"]);
  });
  it("keeps the category and type groupings, sorted as asked", () => {
    const s = layoutSections([line("B", 2), line("A", 5)], "CATEGORY", "MANA_VALUE", true, colours);
    expect(s).toHaveLength(1);
    expect(s[0].rows.map((r) => r.name)).toEqual(["A", "B"]);
  });
});

describe("back faces", () => {
  it("uses the layout when known", () => {
    expect(hasBackFace("transform", "Creature // Creature")).toBe(true);
    expect(hasBackFace("modal_dfc", null)).toBe(true);
    expect(hasBackFace("split", "Instant // Instant")).toBe(false);
    expect(hasBackFace("adventure", "Creature — Elf // Instant — Adventure")).toBe(false);
    expect(hasBackFace("normal", "Creature")).toBe(false);
  });
  it("falls back to a two-faced type line without Adventure", () => {
    expect(hasBackFace(undefined, "Instant // Land")).toBe(true);
    expect(hasBackFace(undefined, "Creature — Elf // Instant — Adventure")).toBe(false);
    expect(hasBackFace(undefined, "Creature")).toBe(false);
  });
  it("swaps front for back in the picture address", () => {
    expect(backImageUrl("https://cards.scryfall.io/normal/front/a/b/ab.jpg?1")).toBe("https://cards.scryfall.io/normal/back/a/b/ab.jpg?1");
  });
});

describe("parsePrefs", () => {
  it("defaults everything on, mana value order, categories", () => {
    expect(parsePrefs(null)).toEqual(DEFAULT_PREFS);
    expect(parsePrefs("not json")).toEqual(DEFAULT_PREFS);
  });
  it("reads what was saved and ignores junk", () => {
    const p = parsePrefs(JSON.stringify({ cost: false, sort: "NAME", descending: true, group: "COLOUR", price: "x", owned: false }));
    expect(p).toEqual({ cost: false, price: true, owned: false, sort: "NAME", descending: true, group: "COLOUR", maybe: "SIDE" });
    expect(parsePrefs(JSON.stringify({ group: "Bogus" })).group).toBe("CATEGORY");
    expect(parsePrefs(JSON.stringify({ maybe: "BELOW" })).maybe).toBe("BELOW");
    expect(parsePrefs(JSON.stringify({ maybe: "Elsewhere" })).maybe).toBe("SIDE");
  });
});
