// Golden tallies for the win turn: the exact counts (games won per win turn, by combo, and so on) that the app's
// WinOdds.kt compiled to JavaScript gives for the fixture deck (web/test/fixtures/winodds-small.json), batches 0..3,
// 400 games each. They were recorded from the build from before the win turn was made faster.
//
// What this guards: making the win turn faster must not change a single game's answer. Same deck, same Commander Salt
// data, same batch and same game count must still win on the same turns, by the same combos, as before. If this test
// fails without a rules change, the speed work changed an answer and must be fixed, not the fixture.
//
// How to re-record (only when the rules change on purpose, which also raises WinOddsCalc.VERSION, exported as
// WIN_ODDS_VERSION in ../src/lib/winodds-js/version.ts): run
//   winBatchJson(JSON.stringify(fixture.cards), fixture.identity, fixture.saltCard, b, 400)
// for b = 0..3 against the new build (src/lib/winodds-js/winodds-kt.mjs), JSON.parse each tally, and save
//   { "version": <the new WIN_ODDS_VERSION>, "games": 400, "tallies": [<the four tallies>] }
// as pretty-printed JSON (2 spaces) in web/test/fixtures/winodds-tallies.json. The version check below then passes
// again, and a failure after that means the recording and the build disagree.
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import type { StatsCard } from "../src/lib/deckstats";
import { WIN_ODDS_VERSION } from "../src/lib/winodds-js/version";
import { winBatchJson } from "../src/lib/winodds-js/winodds-kt.mjs";

type FixtureCard = Omit<StatsCard, "tokens"> & { tokens: string | null };
type Golden = { version: number; games: number; tallies: unknown[] };

const fixture = JSON.parse(readFileSync(new URL("./fixtures/winodds-small.json", import.meta.url), "utf8")) as {
  identity: string;
  saltCard: string | null;
  cards: FixtureCard[];
};
const golden = JSON.parse(readFileSync(new URL("./fixtures/winodds-tallies.json", import.meta.url), "utf8")) as Golden;
const input = JSON.stringify(fixture.cards);

describe("win turn golden tallies (the deck's games, exactly as they were before the speed work)", () => {
  it("was recorded for the current rules version", () => {
    expect(golden.version, "the rules changed: re-record web/test/fixtures/winodds-tallies.json").toBe(WIN_ODDS_VERSION);
  });

  for (let b = 0; b < 4; b++) {
    it(`plays batch ${b}'s games exactly as before`, () => {
      const tally = winBatchJson(input, fixture.identity, fixture.saltCard, b, golden.games);
      expect(JSON.parse(tally)).toEqual(golden.tallies[b]);
    }, 120_000);
  }
});
