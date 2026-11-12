import { describe, expect, it } from "vitest";
import { daysToDrop, weekOf, weeklySince } from "@/lib/pricehistory";

describe("price history", () => {
  it("finds a day's Monday", () => {
    expect(weekOf("2026-11-13")).toBe("2026-11-09"); // a Friday
    expect(weekOf("2026-11-09")).toBe("2026-11-09");
    expect(weekOf("2026-11-15")).toBe("2026-11-09"); // a Sunday
  });

  it("keeps every day for 3 months, one a week up to a year, nothing older", () => {
    const days = ["2025-11-01", "2026-03-02", "2026-03-03", "2026-03-10", "2026-08-01", "2026-11-12", "2026-11-13"];
    expect(daysToDrop(days, "2026-11-13")).toEqual(["2025-11-01", "2026-03-03"]);
  });

  it("keeps all of the last 92 days", () => {
    const days = Array.from({ length: 92 }, (_, i) => new Date(Date.UTC(2026, 10, 13) - i * 86_400_000).toISOString().slice(0, 10));
    expect(daysToDrop(days, "2026-11-13")).toEqual([]);
  });

  it("gives the weekly email 11 days", () => {
    expect(weeklySince("2026-11-13")).toBe("2026-11-02");
  });
});
