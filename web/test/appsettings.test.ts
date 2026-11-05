import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { appSettings, applySettings } from "@/lib/appsettings";
import { parse, prefs, type SyncFile } from "@/lib/syncfile";
import { EditError } from "@/lib/wishlist";

const appFile = (): SyncFile => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
const NOW = 1_750_000_000_000;

describe("the app's synced settings", () => {
  it("reads them with the app's defaults", () => {
    expect(appSettings(appFile())).toMatchObject({
      priceSource: "cardmarket",
      priceType: "trend",
      currency: "DKK",
      conditionAdjust: false,
      tolerancePct: 5,
      alertPriceType: "low",
      wishSections: true,
      receivedToCollection: false,
      receivedClear: "never",
    });
  });

  it("writes them in the app's format and moves the settings' time stamp on", () => {
    const f = appFile();
    applySettings(f, { currency: "EUR", conditionAdjust: true, conditionPcts: { EX: 80 } as Record<string, number>, tolerancePct: 10, wishSections: false }, NOW);
    const p = prefs(f);
    expect(p).toMatchObject({ currency: "EUR", conditionAdjust: "true", tolerancePct: "10", wishSections: "false" });
    expect(p.conditionPcts).toBe("MT=100,NM=100,EX=80,GD=70,LP=60,PL=45,PO=30");
    expect((f.data.prefs as { updatedAt: number }).updatedAt).toBe(NOW);
    // Values the app wouldn't take are refused.
    expect(() => applySettings(f, { currency: "GBP" }, NOW)).toThrow(EditError);
    expect(() => applySettings(f, { tolerancePct: 50 }, NOW)).toThrow(EditError);
  });

  it("leaves the file alone when nothing changes", () => {
    const f = appFile();
    applySettings(f, { currency: "DKK" }, NOW);
    expect((f.data.prefs as { updatedAt: number }).updatedAt).toBe(1700000000000);
  });
});
