import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import type { StatsCard } from "@/lib/deckstats";
import { KRandom } from "@/lib/krandom";
import { calculate, canPay, cost } from "@/lib/manaodds";

// The app's own numbers for the owner's Auntie Plague (printed by the app's ManaOddsCalc with Random(46)), so the
// website shows exactly what the phone shows.
const auntie = (): StatsCard[] =>
  (JSON.parse(readFileSync(path.join(__dirname, "fixtures", "auntie_plague_stats.json"), "utf8")) as StatsCard[]).map((c) => ({ ...c, tokens: null }));

describe("Kotlin's random numbers", () => {
  it("match kotlin.random.Random(46)", () => {
    const r = new KRandom(46);
    const ints = [...Array(5)].map(() => r.nextInt());
    const small = [...Array(5)].map(() => r.nextIntUntil(37));
    const pow2 = [...Array(3)].map(() => r.nextIntUntil(64));
    expect([...ints, ...small, ...pow2]).toEqual([-1238577221, 823548388, -2059038989, 1865200717, 1884613437, 29, 16, 24, 5, 23, 6, 22, 53]);
    expect(r.nextDouble()).toBe(0.4235153900805244);
  });
});

describe("mana odds (the app's ManaOddsCalc)", () => {
  it("reads costs and pays pips", () => {
    const c = cost("{2}{B}{G/U}{2/W}{B/P}{X}");
    expect(c.generic).toBe(2);
    expect(c.pips.map((p) => [...p])).toEqual([["B"], ["G", "U"], ["W"]]);
    expect(cost("{1}{G} // {3}{U}").generic).toBe(1);
    const k = cost("{1}{B}{R}{G}");
    const brg: [Set<string>, number][] = [
      [new Set(["B"]), 1],
      [new Set(["R", "G"]), 1],
      [new Set(["G"]), 1],
      [new Set(["C"]), 1],
    ];
    expect(canPay(brg, k)).toBe(true);
    expect(canPay([[new Set(["B"]), 1], [new Set(["G"]), 1], [new Set(["G"]), 1], [new Set(["C"]), 1]], k)).toBe(false);
    expect(canPay([[new Set(["C"]), 2], [new Set(["B", "R", "G"]), 1]], cost("{2}{B}"))).toBe(true);
  });

  it("gives the app's numbers for Auntie Plague", () => {
    const odds = calculate(auntie(), "BRG")!;
    expect(odds.commanders.map((c) => c.byTurn)).toEqual([[0, 0, 40, 87, 92, 94, 96]]);
    expect(odds.colours.map((c) => c[1])).toEqual([
      [18, 91, 98, 99, 99, 99, 99],
      [15, 81, 94, 96, 97, 98, 98],
      [20, 89, 97, 98, 98, 99, 99],
    ]);
    expect(odds.allColours).toEqual([7, 67, 89, 93, 95, 96, 97]);
    expect([odds.lands, odds.rocks, odds.creatures, odds.ramp]).toEqual([38, 2, 3, 3]);
  });
});
