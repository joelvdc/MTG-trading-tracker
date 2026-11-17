import { describe, expect, it } from "vitest";
import { shownBracket } from "@/lib/deckshared";

describe("shownBracket", () => {
  it("puts the realistic bracket in front when it is higher, the baseline under it", () => {
    expect(shownBracket(3, 2)).toEqual({ main: 3, other: { label: "baseline", value: 2 } });
  });

  it("puts the baseline in front when it is higher, the realistic under it", () => {
    expect(shownBracket(2, 3)).toEqual({ main: 3, other: { label: "realistic", value: 2 } });
  });

  it("keeps the realistic in front when the two are equal", () => {
    expect(shownBracket(3, 3)).toEqual({ main: 3, other: { label: "baseline", value: 3 } });
  });

  it("shows the one bracket known on its own", () => {
    expect(shownBracket(null, 2)).toEqual({ main: 2, other: null });
    expect(shownBracket(2, null)).toEqual({ main: 2, other: null });
    expect(shownBracket(undefined, 4)).toEqual({ main: 4, other: null });
  });

  it("has nothing to show when unscored", () => {
    expect(shownBracket(null, null)).toEqual({ main: null, other: null });
    expect(shownBracket(undefined, undefined)).toEqual({ main: null, other: null });
  });
});
