import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { checkLegality, ruleOf, type LegalLine, type LegalRule } from "@/lib/legality";

// The same cases as the app's DeckLegalityTest.kt.
const cases = JSON.parse(readFileSync(path.join(__dirname, "fixtures", "legality-cases.json"), "utf8")) as {
  cards: { scryfall: Parameters<typeof ruleOf>[0]; rule: LegalRule }[];
  rules: Record<string, LegalRule>;
  decks: { about: string; lines: LegalLine[]; problems: { kind: string; cards: string[] }[]; unchecked: string[] }[];
};

describe("Commander legality", () => {
  for (const c of cases.cards)
    it(`reads the rules of ${c.scryfall.name}`, () => {
      expect(ruleOf(c.scryfall)).toEqual(c.rule);
    });

  const rules = new Map(Object.entries(cases.rules));
  for (const d of cases.decks)
    it(`checks ${d.about}`, () => {
      const r = checkLegality(d.lines, rules);
      expect(r.problems.map((p) => ({ kind: p.kind, cards: p.cards }))).toEqual(d.problems);
      expect(r.unchecked).toEqual(d.unchecked);
      for (const p of r.problems) expect(p.text.length).toBeGreaterThan(0);
    });

  it("words the problems for the deck page", () => {
    const r = checkLegality(cases.decks[2].lines, rules);
    expect(r.problems.map((p) => p.text)).toEqual([
      "Banned in Commander: Black Lotus.",
      "Not legal in Commander: Chaos Orb.",
      "Outside the commander's colours: Lightning Bolt, Mountain.",
      "More copies than allowed: Sol Ring ×2.",
    ]);
  });
});
