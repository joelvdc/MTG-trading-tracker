import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import type { StatsCard } from "../src/lib/deckstats";
import { fingerprint, winDamageNote, winInput, winWindow, type WinResult } from "../src/lib/winodds";
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

  it("fingerprints change with the deck", () => {
    expect(fingerprint("a")).toBe(fingerprint("a"));
    expect(fingerprint("a")).not.toBe(fingerprint("b"));
  });
});
