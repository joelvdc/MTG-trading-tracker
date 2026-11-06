import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { orderedFormats, parseCardRules, rulingDate } from "@/lib/cardrules";

const fixture = JSON.parse(readFileSync(path.join(__dirname, "fixtures", "card-rules.json"), "utf8"));

describe("card rules, legalities and rulings", () => {
  it("reads every face of a double-faced card", () => {
    const r = parseCardRules(fixture.card, fixture.rulings);
    expect(r.faces).toHaveLength(2);
    expect(r.faces[0]).toMatchObject({ name: "Delver of Secrets", manaCost: "{U}", typeLine: "Creature — Human Wizard", power: "1", toughness: "1", loyalty: null });
    expect(r.faces[1]).toMatchObject({ name: "Insectile Aberration", manaCost: "", oracleText: "Flying", power: "3", toughness: "2" });
  });

  it("reads a single-faced card from the top level", () => {
    const r = parseCardRules(fixture.single, null);
    expect(r.faces).toEqual([
      { name: "Jace Beleren", manaCost: "{1}{U}{U}", typeLine: "Legendary Planeswalker — Jace", oracleText: "+2: Each player draws a card.", power: null, toughness: null, loyalty: "3", defense: null },
    ]);
    expect(r.rulings).toEqual([]);
  });

  it("keeps the four known statuses and puts the main formats first", () => {
    const r = parseCardRules(fixture.card, fixture.rulings);
    expect(r.legalities.odd).toBeUndefined();
    expect(r.legalities.vintage).toBe("restricted");
    expect(r.legalities.pauper).toBe("banned");
    expect(orderedFormats(r.legalities)).toEqual(["commander", "standard", "modern", "vintage", "pauper", "oldschool"]);
  });

  it("lists the rulings newest first and skips empty ones", () => {
    const r = parseCardRules(fixture.card, fixture.rulings);
    expect(r.rulings.map((x) => x.text)).toEqual(["Newest ruling.", "Middle ruling.", "Older ruling."]);
    expect(rulingDate("2024-06-14")).toBe("14 Jun 2024");
  });
});
