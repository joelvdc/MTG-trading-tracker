import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import type { StatsCard } from "../src/lib/deckstats";
import { fingerprint, winDamageNote, winInput, winKey, winWindow, type WinResult } from "../src/lib/winodds";
import { winOddsJson } from "../src/lib/winodds-js/winodds-kt.mjs";

type FixtureCard = Omit<StatsCard, "tokens"> & { tokens: string | null };
const fixture = JSON.parse(readFileSync(new URL("./fixtures/winodds-small.json", import.meta.url), "utf8")) as {
  identity: string;
  saltCard: string | null;
  cards: FixtureCard[];
  app: { odds: unknown; power: number; exact: number };
};
const cards: StatsCard[] = fixture.cards.map((c) => ({ ...c, tokens: c.tokens ? JSON.parse(c.tokens) : null }));

describe("win turn (the app's WinOdds.kt compiled to JavaScript)", () => {
  it("answers what the app answers for the same deck", () => {
    const w = JSON.parse(winOddsJson(winInput(cards), fixture.identity, fixture.saltCard, 500)) as WinResult;
    expect(w.odds).toEqual(fixture.app.odds);
    expect(w.power.power).toBe(fixture.app.power);
    expect(w.power.exact).toBe(fixture.app.exact);
  }, 60_000);

  it("keeps the deck's cards as the app does (tokens as a JSON list, or none)", () => {
    const parsed = (cs: FixtureCard[]) => cs.map((c) => ({ ...c, tokens: c.tokens ? JSON.parse(c.tokens) : null }));
    expect(parsed(JSON.parse(winInput(cards)))).toEqual(parsed(fixture.cards));
    expect(JSON.parse(winInput([{ ...cards[0], tokens: [] }]))[0].tokens).toBeNull();
  });

  it("needs a commander", () => {
    const none = cards.map((c) => ({ ...c, commander: false }));
    expect(winOddsJson(winInput(none), fixture.identity, null, 50)).toBe("null");
  });

  it("shows the app's turns and notes", () => {
    const w = JSON.parse(winOddsJson(winInput(cards), fixture.identity, fixture.saltCard, 500)) as WinResult;
    expect(winWindow(w)[0]).toBe(w.windowStart);
    expect(winWindow(w).length).toBeGreaterThanOrEqual(8);
    expect(winDamageNote(w)).toMatch(/^Damage: [a-z]/);
  }, 60_000);

  it("keeps the win turn when only Commander Salt's other scores change", () => {
    const combo = { cards: ["Thassa's Oracle", "Demonic Consultation"], counts: true, results: ["win-game"] };
    const salt = (extra: object) => JSON.stringify({ combos: [combo], twoCardCombos: [combo.cards], axes: { speed: 0.4, interaction: 0.2 }, ...extra });
    const key = (s: string | null) => winKey("[]", "UB", s);
    const base = key(salt({ playStyle: "Focused", notes: [] }));
    // Notes, play style, salt, effects: same key (a refresh with the same cards is instant).
    expect(key(salt({ playStyle: "Optimized", notes: [{ text: "Redundant combo wins" }], saltIntensity: "High", effects: { wipes: 3 } }))).toBe(base);
    // The ratings in another order: same key.
    expect(key(JSON.stringify({ combos: [combo], twoCardCombos: [combo.cards], axes: { interaction: 0.2, speed: 0.4 } }))).toBe(base);
    // A combo or a rating (the power level reads them) changes it.
    expect(key(salt({ combos: [] }))).not.toBe(base);
    expect(key(salt({ axes: { speed: 0.5, interaction: 0.2 } }))).not.toBe(base);
    // Not scored, or another colour identity: another key.
    expect(key(null)).not.toBe(base);
    expect(winKey("[]", "U", salt({}))).not.toBe(winKey("[]", "UB", salt({})));
  });

  it("fingerprints change with the deck", () => {
    expect(fingerprint("a")).toBe(fingerprint("a"));
    expect(fingerprint("a")).not.toBe(fingerprint("b"));
  });
});
