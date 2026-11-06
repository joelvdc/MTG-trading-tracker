import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { readSpreadsheet } from "@/lib/spreadsheet";

// The app's own test files (app/src/test/resources/cardtrader).
const resource = (name: string) => new Uint8Array(readFileSync(path.join(__dirname, "..", "..", "app", "src", "test", "resources", "cardtrader", name)));

describe("spreadsheets (the app's Spreadsheet.kt)", () => {
  const header = [
    "Game", "Set Released At", "Set Name", "Set Code", "Item Name", "Price in EUR Cents", "Quantity", "Condition",
    "Language", "Foil/Reverse", "Signed", "Altered", "First Edition", "Collector Number",
  ];

  it("reads old Excel files", () => {
    const rows = readSpreadsheet(resource("order_sample.xls"));
    expect(rows[0]).toEqual(header);
    expect(rows).toHaveLength(11);
    expect(rows[1]).toEqual(["Magic: the Gathering", "2026-04-24", "Secrets of Strixhaven", "SOS", "Echocasting Symposium", "231", "1", "Near Mint", "en", "0", "0", "0", "", "044"]);
    expect(rows[4][13]).toBe("T 04/02");
  });

  it("reads new Excel files the same way", () => {
    expect(readSpreadsheet(resource("order_sample.xlsx"))).toEqual(readSpreadsheet(resource("order_sample.xls")));
  });

  it("reads strings spread over several records", () => {
    const rows = readSpreadsheet(resource("big.xls"));
    expect(rows).toHaveLength(601);
    for (let i = 1; i <= 600; i++) {
      expect(rows[i][0]).toBe(`Lórien Revealed ☆ ${String(i).padStart(4, "0")} ${"x".repeat(i % 37)}`.trim());
      expect(rows[i][1]).toBe(String(i));
    }
  });

  it("reads CSV too", () => {
    expect(readSpreadsheet(new TextEncoder().encode("Item Name,Set Code,Collector Number\nBrainstorm,ICE,61\n"))[1]).toEqual(["Brainstorm", "ICE", "61"]);
  });
});
