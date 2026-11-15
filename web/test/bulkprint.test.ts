import { describe, expect, it } from "vitest";
import { pickPrinting, type PrintChoice } from "@/lib/bulkprint";

/** A printing with the flags it needs; a flag left out means the printing has that finish. */
const print = (
  id: string,
  released: string,
  eur: number | null,
  eurFoil: number | null,
  flags: { nonfoil?: boolean; foil?: boolean; etched?: boolean } = {},
) => ({
  card: { scryfallId: id, hasNonFoil: flags.nonfoil, hasFoil: flags.foil, hasEtched: flags.etched },
  released,
  eur,
  eurFoil,
});

const NONE = new Set<string>();
const pick = (prints: ReturnType<typeof print>[], choice: PrintChoice, foil = false, owned = NONE, current = "none") => pickPrinting(prints, choice, foil, owned, current);

const prints = [print("a", "2019-01-01", 5, 9), print("b", "2022-06-01", 2, 12), print("c", "2020-03-01", 3, 7)];

describe("pickPrinting", () => {
  it("CHEAPEST takes the lowest price for the line's finish", () => {
    expect(pick(prints, "CHEAPEST")).toBe("b");
    expect(pick(prints, "CHEAPEST", true)).toBe("c");
  });

  it("CHEAPEST skips printings without a price", () => {
    const list = [print("free", "2021-01-01", null, null), print("cheap", "2020-01-01", 1, 3)];
    expect(pick(list, "CHEAPEST")).toBe("cheap");
    expect(pick([print("x", "2021-01-01", null, null)], "CHEAPEST")).toBeNull();
  });

  it("CHEAPEST keeps the first of equally cheap printings", () => {
    const list = [print("first", "2021-01-01", 2, 4), print("second", "2022-01-01", 2, 4)];
    expect(pick(list, "CHEAPEST")).toBe("first");
  });

  it("NEWEST and OLDEST go by the release date", () => {
    expect(pick(prints, "NEWEST")).toBe("b");
    expect(pick(prints, "OLDEST")).toBe("a");
  });

  it("NEWEST and OLDEST keep the first on the same release date", () => {
    const list = [print("first", "2020-01-01", 1, 1), print("second", "2020-01-01", 2, 2)];
    expect(pick(list, "NEWEST")).toBe("first");
    expect(pick(list, "OLDEST")).toBe("first");
  });

  it("OWNED takes the newest printing you own", () => {
    expect(pick(prints, "OWNED", false, new Set(["a", "c"]))).toBe("c");
  });

  it("OWNED answers null when you own none of the printings", () => {
    expect(pick(prints, "OWNED", false, NONE)).toBeNull();
    expect(pick(prints, "OWNED", false, new Set(["elsewhere"]))).toBeNull();
  });

  it("only counts printings in the line's finish", () => {
    // The cheapest one has neither foil nor etched, so a foil line skips it.
    const list = [print("plain", "2021-01-01", 1, 1, { nonfoil: true, foil: false, etched: false }), print("shiny", "2020-01-01", 4, 4, { nonfoil: false, foil: true })];
    expect(pick(list, "CHEAPEST")).toBe("plain");
    expect(pick(list, "CHEAPEST", true)).toBe("shiny");
    // Etched counts as foil; a flag the list leaves out counts as available.
    expect(pick([print("etched", "2021-01-01", 1, 2, { etched: true, nonfoil: false, foil: false })], "NEWEST", true)).toBe("etched");
    expect(pick([print("plain", "2021-01-01", 1, 2, { nonfoil: true })], "NEWEST", true)).toBe("plain");
  });

  it("answers null when nothing fits", () => {
    const list = [print("plain", "2021-01-01", 1, 1, { nonfoil: true, foil: false, etched: false })];
    expect(pick(list, "NEWEST", true)).toBeNull();
    expect(pick([], "NEWEST")).toBeNull();
  });

  it("answers null when the best printing is the line's printing already", () => {
    expect(pick(prints, "NEWEST", false, NONE, "b")).toBeNull();
    expect(pick(prints, "CHEAPEST", true, NONE, "c")).toBeNull();
  });
});
