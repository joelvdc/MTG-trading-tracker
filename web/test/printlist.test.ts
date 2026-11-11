import { describe, expect, it } from "vitest";
import type { CollRow } from "@/lib/collview";
import type { DeckRow } from "@/lib/deckshared";
import { collectionPrintList, deckPrintList } from "@/lib/printlist";

const money = (eur: number) => `€${eur.toFixed(2)}`;

const card = (name: string, set = "abc", num = "1") => ({ name, flavorName: null, setCode: set, collectorNumber: num });

const coll = (name: string, quantity: number, unitEur: number | null, extra: Partial<CollRow> = {}) =>
  ({
    uid: name,
    card: card(name),
    finish: "NONFOIL",
    condition: "NM",
    language: "EN",
    quantity,
    unitEur,
    approx: false,
    purchasePrice: null,
    ...extra,
  }) as unknown as CollRow;

const deckRow = (name: string, quantity: number, unitEur: number | null, extra: Partial<DeckRow> = {}) =>
  ({ lineUid: name, name, card: card(name, "xyz", "7"), quantity, unitEur, ...extra }) as unknown as DeckRow;

describe("collectionPrintList", () => {
  it("fills the lines and adds up copies, value and what was paid", () => {
    const list = collectionPrintList(
      [coll("Sol Ring", 2, 1.5, { purchasePrice: 1, finish: "FOIL" }), coll("Island", 3, null, { approx: true }), coll("Opt", 1, 0.25, { approx: true, finish: "ETCHED" })],
      money,
    );
    expect(list.copies).toBe(6);
    expect(list.valueEur).toBeCloseTo(3.25);
    expect(list.paidEur).toBeCloseTo(2);
    expect(list.lines[0]).toMatchObject({ set: "ABC", number: "1", finish: "Foil", price: "€1.50", total: "€3.00", paid: "€1.00", paidTotal: "€2.00" });
    expect(list.lines[1]).toMatchObject({ finish: "Non-foil", price: "–", total: "–", paid: "–", paidTotal: "–" });
    expect(list.lines[2]).toMatchObject({ finish: "Etched", price: "≈€0.25" });
  });

  it("is empty for no rows", () => {
    expect(collectionPrintList([], money)).toEqual({ lines: [], copies: 0, valueEur: 0, paidEur: 0 });
  });
});

describe("deckPrintList", () => {
  it("counts each section and the deck, rows without a price count 0", () => {
    const list = deckPrintList(
      [
        { title: "Commander", rows: [deckRow("Atraxa", 1, 5)] },
        { title: "Ramp", rows: [deckRow("Sol Ring", 1, 2), deckRow("Forest", 10, null)] },
      ],
      money,
    );
    expect(list.copies).toBe(12);
    expect(list.valueEur).toBe(7);
    expect(list.sections.map((s) => [s.title, s.copies])).toEqual([
      ["Commander", 1],
      ["Ramp", 11],
    ]);
    expect(list.sections[1].lines[1]).toMatchObject({ set: "XYZ", number: "7", price: "–", total: "–" });
  });
});
