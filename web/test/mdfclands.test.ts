import { describe, expect, it } from "vitest";
import { backIsLand, isLandSection, mdfcLandCopies } from "@/lib/mdfclands";

describe("backIsLand", () => {
  it("is a land on the back of a spell", () => {
    expect(backIsLand("Instant // Land")).toBe(true);
    expect(backIsLand("Creature — Elf // Land")).toBe(true);
    expect(backIsLand("Legendary Creature — God // Legendary Land")).toBe(true);
  });
  it("is not when there is no back face, or the front is a land too", () => {
    expect(backIsLand("Land")).toBe(false);
    expect(backIsLand("Land // Land")).toBe(false);
    expect(backIsLand("Sorcery")).toBe(false);
  });
  it("is not when the back is not a land", () => {
    expect(backIsLand("Sorcery // Instant")).toBe(false);
    expect(backIsLand("Instant // Creature")).toBe(false);
  });
});

describe("mdfcLandCopies", () => {
  const row = (name: string, quantity: number, typeLine: string | null, maybeboard = false) => ({ name, quantity, typeLine, maybeboard });
  const typeLineOf = (r: { typeLine: string | null }) => r.typeLine;

  it("adds the copies of MDFC lands", () => {
    const rows = [row("Sink into Stupor", 2, "Instant // Land"), row("Amonkhet Raceway", 1, "Artifact // Land"), row("Counterspell", 4, "Instant")];
    expect(mdfcLandCopies(rows, typeLineOf)).toBe(3);
  });

  it("skips the maybeboard", () => {
    const rows = [row("Sink into Stupor", 2, "Instant // Land"), row("Soporific Springs", 3, "Instant // Land", true)];
    expect(mdfcLandCopies(rows, typeLineOf)).toBe(2);
  });

  it("skips cards whose type line is unknown, and plain lands", () => {
    const rows = [row("Unknown", 3, null), row("Island", 5, "Basic Land — Island"), row("Sink into Stupor", 1, "Instant // Land")];
    expect(mdfcLandCopies(rows, typeLineOf)).toBe(1);
  });

  it("takes rows without a maybeboard field", () => {
    const rows = [{ quantity: 2, typeLine: "Instant // Land" }];
    expect(mdfcLandCopies(rows, (r) => r.typeLine)).toBe(2);
  });
});

describe("isLandSection", () => {
  it("finds the Land section, however it is spelled", () => {
    for (const title of ["Land", "Lands", "land", "LANDS", " Land "]) expect(isLandSection(title)).toBe(true);
  });
  it("leaves the other sections alone", () => {
    for (const title of ["Creatures", "Ramp", "Commanders", "Land destruction", "Other"]) expect(isLandSection(title)).toBe(false);
  });
});
