import { describe, expect, it } from "vitest";
import { edgeSpeed } from "@/components/useDragScroll";

// Scrolling the deck page while dragging a card or category near the window's edge.
describe("edge scrolling while dragging", () => {
  it("scrolls up near the top, down near the bottom, faster closer to the edge", () => {
    expect(edgeSpeed(400, 800)).toBe(0);
    expect(edgeSpeed(100, 800)).toBe(0);
    expect(edgeSpeed(60, 800)).toBeLessThan(0);
    expect(edgeSpeed(0, 800)).toBe(-22);
    expect(edgeSpeed(5, 800)).toBeLessThan(edgeSpeed(60, 800));
    expect(edgeSpeed(750, 800)).toBeGreaterThan(0);
    expect(edgeSpeed(800, 800)).toBe(22);
  });

  it("keeps the edge zone small in a short window", () => {
    expect(edgeSpeed(70, 240)).toBe(0);
    expect(edgeSpeed(30, 240)).toBeLessThan(0);
  });
});
