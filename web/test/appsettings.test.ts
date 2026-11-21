import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { appSettings, applySettings } from "@/lib/appsettings";
import { parse, prefs, setPrefs, type SyncFile } from "@/lib/syncfile";
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

  it("changes the power level source and your Archidekt username like the app", () => {
    const f = appFile();
    applySettings(f, { powerSource: "EDH_POWER_LEVEL", ownArchidektUser: "  joel " }, NOW);
    expect(prefs(f)).toMatchObject({ powerSource: "EDH_POWER_LEVEL", ownArchidektUser: "joel" });
    expect(appSettings(f)).toMatchObject({ powerSource: "EDH_POWER_LEVEL", ownArchidektUser: "joel" });
    expect(() => applySettings(f, { powerSource: "MOXFIELD" }, NOW)).toThrow(EditError);
    expect(() => applySettings(f, { ownArchidektUser: "two words" }, NOW)).toThrow(EditError);
  });

  it("shows a stored ScrollVault power source as Commander Salt, leaving the stored value alone", () => {
    const f = appFile();
    // An older phone (up to app 1.104) may still have it.
    setPrefs(f, { powerSource: "SCROLLVAULT" }, NOW);
    expect(appSettings(f).powerSource).toBe("COMMANDER_SALT");
    expect(prefs(f).powerSource).toBe("SCROLLVAULT");
    // The website can't set it any more.
    expect(() => applySettings(f, { powerSource: "SCROLLVAULT" }, NOW)).toThrow(EditError);
    expect(prefs(f).powerSource).toBe("SCROLLVAULT");
  });

  it("leaves the file alone when nothing changes", () => {
    const f = appFile();
    applySettings(f, { currency: "DKK" }, NOW);
    expect((f.data.prefs as { updatedAt: number }).updatedAt).toBe(1700000000000);
  });
});
