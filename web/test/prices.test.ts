import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { compactGuide } from "@/lib/prices";
import { sameEtag } from "@/lib/pricestore";

// The app reads this file in Release181Test (PriceGuideRepository.readWebPrices): the website must make the same.
const appFixture = path.join(__dirname, "..", "..", "app", "src", "test", "resources", "webprices", "prices.json");

describe("the price file the phones download (app 1.81)", () => {
  it("is what the app's test reads", () => {
    const guide = {
      createdAt: "2026-11-06T02:42:30+0100",
      priceGuides: [
        { idProduct: 1001, trend: 1.25, avg: 1.4, low: 0.9, avg1: 1.3, avg7: 1.35, avg30: 1.2 },
        { idProduct: 1002, "trend-foil": 12.5, "avg-foil": 13, "low-foil": 10, "avg1-foil": 12, "avg7-foil": 12.25, "avg30-foil": 12.75 },
        { idProduct: 1003, trend: 0.02, avg: 0.03, low: 0.02, avg1: 0, avg7: 0.02, avg30: 0.02, "trend-foil": 0.1, "low-foil": 0.05, "avg1-foil": 0.1, "avg7-foil": 0.1, "avg30-foil": 0.1 },
        { idProduct: 1004 },
      ],
    };
    const app = JSON.parse(readFileSync(appFixture, "utf8"));
    const made = compactGuide(guide);
    expect(made.createdAt).toBe(app.createdAt);
    expect(made.products).toEqual(app.products);
    expect(Object.keys(app)).toEqual(["createdAt", "madeAt", "rates", "products"]);
  });

  it("compares ETags", () => {
    expect(sameEtag('"abc"', "abc")).toBe(true);
    expect(sameEtag('W/"abc"', '"abc"')).toBe(true);
    expect(sameEtag('"abc"', '"abd"')).toBe(false);
    expect(sameEtag(null, '"abc"')).toBe(false);
  });
});
