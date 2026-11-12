import { describe, expect, it } from "vitest";
import { groupRows, range, same } from "@/lib/collgroup";
import { comparator, type SortLevel } from "@/lib/colquery";
import type { CollRow } from "@/lib/collview";
import type { CardRef } from "@/lib/syncfile";

// Group printings on the website's collection table: one line per card name.

const card = (name: string, setCode: string, num: string): CardRef => ({
  scryfallId: `${setCode}-${num}`,
  name,
  setCode,
  setName: setCode.toUpperCase() + " set",
  collectorNumber: num,
  rarity: "rare",
  imageUrl: null,
  cardmarketId: 1,
  fallbackEur: 1,
  fallbackEurFoil: null,
  hasNonFoil: true,
  hasFoil: false,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
});

function row(uid: string, name: string, setCode: string, o: Partial<CollRow> = {}): CollRow {
  return {
    uid,
    card: card(name, setCode, "1"),
    finish: "NONFOIL",
    condition: "NM",
    language: "EN",
    quantity: 1,
    addedAt: 0,
    binder: null,
    notes: null,
    purchasePrice: null,
    paidTyped: null,
    paidCurrency: null,
    signed: false,
    altered: false,
    unitEur: 1,
    cmEur: 1,
    approx: false,
    condNote: null,
    changeEur: null,
    changePct: null,
    srcEur: { cardmarket: 1, tcgplayer: null, cardkingdom: null, ckPays: null },
    info: { c: "R", i: "R", t: "Instant", v: 1, r: 1, d: "1993-08-05", g: false, l: "normal" },
    ...o,
  };
}

const sorted = (rows: CollRow[], levels: SortLevel[]) => {
  const cmp = comparator(levels);
  return groupRows([...rows].sort(cmp), cmp);
};

const boltA = row("b1", "Lightning Bolt", "m10", { quantity: 2, unitEur: 1.5, addedAt: 10, purchasePrice: 1, binder: "x" });
const boltB = row("b2", "Lightning Bolt", "2xm", { quantity: 1, unitEur: 4, addedAt: 30, condition: "EX", changePct: 5, changeEur: 0.2 });
const boltC = row("b3", "lightning bolt", "sta", { quantity: 1, unitEur: null, addedAt: 20 });
const sol = row("s1", "Sol Ring", "c21", { quantity: 3, unitEur: 2 });

describe("groupRows", () => {
  it("puts every printing of a name on one line, case aside", () => {
    const g = sorted([boltA, sol, boltB, boltC], [{ field: "NAME", reversed: false }]);
    expect(g.map((x) => x.key)).toEqual(["lightning bolt", "sol ring"]);
    expect(g[0].rows.map((r) => r.uid).sort()).toEqual(["b1", "b2", "b3"]);
    expect(g[0].printings).toBe(3);
    expect(g[1].rows).toHaveLength(1);
  });

  it("adds up copies and value, and gives the price range", () => {
    const [bolt] = sorted([boltA, boltB, boltC], [{ field: "NAME", reversed: false }]);
    expect(bolt.sum.quantity).toBe(4);
    expect(bolt.minEur).toBe(1.5);
    expect(bolt.maxEur).toBe(4);
    expect(bolt.totalEur).toBe(2 * 1.5 + 4);
    // Only the pile with a purchase price counts towards profit.
    expect(bolt.profitEur).toBe(2 * (1.5 - 1));
    expect(bolt.sum.addedAt).toBe(30);
    // The piles' changes differ, so the group has none.
    expect(bolt.sum.changePct).toBeNull();
  });

  it("sorts the lines by their totals", () => {
    const byCopies = sorted([boltA, boltB, boltC, sol], [{ field: "QUANTITY", reversed: false }]);
    expect(byCopies.map((x) => x.key)).toEqual(["lightning bolt", "sol ring"]);
    const byValue = sorted([boltA, boltB, boltC, sol], [{ field: "VALUE", reversed: false }]);
    expect(byValue[0].key).toBe("lightning bolt"); // its dearest copy (4) beats Sol Ring's 2
    const newest = sorted([boltA, sol], [{ field: "RECENT", reversed: false }]);
    expect(newest.map((x) => x.key)).toEqual(["lightning bolt", "sol ring"]);
  });

  it("keeps the piles in the table's order inside a group", () => {
    const [bolt] = sorted([boltA, boltB, boltC], [{ field: "VALUE", reversed: false }]);
    expect(bolt.rows.map((r) => r.uid)).toEqual(["b2", "b1", "b3"]);
  });

  it("has no profit when no pile has a purchase price", () => {
    const [s] = sorted([sol], [{ field: "NAME", reversed: false }]);
    expect(s.profitEur).toBeNull();
  });
});

describe("same and range", () => {
  it("gives the shared value or undefined", () => {
    expect(same([boltA, boltC], (r) => r.condition)).toBe("NM");
    expect(same([boltA, boltB], (r) => r.condition)).toBeUndefined();
    expect(same([boltA, boltC], (r) => r.notes)).toBeNull();
  });

  it("ignores missing values", () => {
    expect(range([null, 3, 1])).toEqual({ min: 1, max: 3 });
    expect(range([null])).toBeNull();
  });
});
