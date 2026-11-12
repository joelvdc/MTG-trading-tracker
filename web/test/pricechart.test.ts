import { describe, expect, it } from "vitest";
import { lastDays } from "@/components/PriceChart";

describe("price chart periods", () => {
  it("keeps the days from the period before the newest one", () => {
    const pts = ["2026-08-01", "2026-10-13", "2026-10-20", "2026-11-13"].map((day, i) => ({ day, eur: i }));
    expect(lastDays(pts, 31).map((p) => p.day)).toEqual(["2026-10-13", "2026-10-20", "2026-11-13"]);
    expect(lastDays(pts, 366)).toHaveLength(4);
    expect(lastDays([], 31)).toEqual([]);
  });
});
