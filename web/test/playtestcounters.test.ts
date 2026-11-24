import { describe, expect, it } from "vitest";
import { counterKind, counterLabel, DEFAULT_COUNTERS, MAX_COUNTERS, parseCounters, withCounter } from "../src/lib/playtestcounters";

describe("the quick counter list", () => {
  it("starts with the four usual counters", () => {
    expect(parseCounters(null)).toEqual(["+1/+1", "-1/-1", "loyalty", "charge"]);
    expect(parseCounters("not json")).toEqual(DEFAULT_COUNTERS);
    expect(parseCounters('{"a":1}')).toEqual(DEFAULT_COUNTERS);
  });

  it("keeps an edited list, even an empty one, without doubles", () => {
    expect(parseCounters("[]")).toEqual([]);
    expect(parseCounters('["Shield","shield ","Loyalty",3,"Shield"]')).toEqual(["Shield", "shield", "loyalty"]);
  });

  it("names kinds the way the engine does", () => {
    expect(counterKind("  Loyalty ")).toBe("loyalty");
    expect(counterKind("CHARGE")).toBe("charge");
    expect(counterKind("\u22121/\u22121")).toBe("-1/-1");
    expect(counterKind("   ")).toBe("");
    expect(counterLabel("-1/-1")).toBe("\u22121/\u22121");
    expect(counterLabel("loyalty")).toBe("Loyalty");
    expect(counterLabel("Oil")).toBe("Oil");
  });

  it("adds a kind once, and not past the limit", () => {
    const list = withCounter(DEFAULT_COUNTERS, "Oil");
    expect(list).toEqual([...DEFAULT_COUNTERS, "Oil"]);
    expect(withCounter(list, "Oil")).toBe(list);
    expect(withCounter(list, "Loyalty")).toBe(list);
    const full = Array.from({ length: MAX_COUNTERS }, (_, i) => `K${i}`);
    expect(withCounter(full, "More")).toBe(full);
  });
});
