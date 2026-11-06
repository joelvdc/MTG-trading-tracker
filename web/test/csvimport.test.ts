import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { applyImport, identifiers, keyCards, parseCondition, parseCsv, parseLanguage, readCsv } from "@/lib/csvimport";
import { binders, collection, parse, type CardRef, type SyncFile } from "@/lib/syncfile";

const appFile = (): SyncFile => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
const NOW = 1_750_000_000_000;
let n = 0;
const uids = () => `f${String(++n).padStart(31, "0")}`;

const bolt: CardRef = {
  scryfallId: "44444444-4444-4444-4444-444444444444",
  name: "Lightning Bolt",
  setCode: "2xm",
  setName: "Double Masters",
  collectorNumber: "117",
  rarity: "uncommon",
  imageUrl: null,
  cardmarketId: 470000,
  fallbackEur: 1.2,
  fallbackEurFoil: 3.4,
  hasNonFoil: true,
  hasFoil: true,
  foilType: null,
  hasEtched: false,
  flavorName: null,
  cardmarketFoilId: null,
};
const fable: CardRef = { ...bolt, scryfallId: "66666666-6666-6666-6666-666666666666", name: "Fable of the Mirror-Breaker // Reflection of Kiki-Jiki", setCode: "neo", collectorNumber: "141", hasNonFoil: false };

describe("CSV import", () => {
  it("parses quoted fields and semicolons like the app", () => {
    expect(parseCsv('a,"b, c","say ""hi"""\r\n1,2,3', ",")).toEqual([["a", "b, c", 'say "hi"'], ["1", "2", "3"]]);
    expect(parseCondition("Lightly Played")).toBe("LP");
    expect(parseCondition("near_mint")).toBe("NM");
    expect(parseLanguage("German")).toBe("DE");
    expect(parseLanguage("jp")).toBe("JA");
    const lines = readCsv("Name;Quantity;Foil\nLightning Bolt;3;foil\n");
    expect(lines).toMatchObject([{ key: "n:lightning bolt", qty: 3, finish: "FOIL" }]);
  });

  it("reads ManaBox-style columns and looks every printing up once", () => {
    const csv = [
      "Name,Set code,Collector number,Foil,Quantity,Condition,Language,Binder Name,Purchase price,Notes,Signed",
      "Lightning Bolt,2XM,117,normal,2,near_mint,English,Trades,1.50,from a friend,",
      "Lightning Bolt,2XM,117,foil,1,lightly_played,German,,,,yes",
      "Fable of the Mirror-Breaker,,,normal,1,,,Unsorted,,,",
      "Nonexistent Card,,,,4,,,,,,",
    ].join("\n");
    const lines = readCsv(csv);
    expect(lines).toHaveLength(4);
    expect(identifiers(lines)).toEqual([{ set: "2xm", collector_number: "117" }, { name: "Fable of the Mirror-Breaker" }, { name: "Nonexistent Card" }]);

    const f = appFile();
    const before = collection(f).length;
    const r = applyImport(f, lines, keyCards([bolt, fable]), null, NOW, null, uids);
    expect(r).toMatchObject({ imported: 4, notFound: 4, missing: ["Nonexistent Card"], newBinders: ["Trades"] });
    const trades = binders(f).find((b) => b.name === "Trades")!;
    const added = collection(f).slice().filter((s) => s.item.addedAt === NOW);
    expect(added).toHaveLength(3);
    const traded = added.find((s) => s.binder === trades.uid)!;
    expect(traded.item).toMatchObject({ quantity: 2, condition: "NM", language: "EN", purchasePrice: 1.5, notes: "from a friend", foil: false });
    const signed = added.find((s) => s.item.signed)!;
    expect(signed.item).toMatchObject({ foil: true, condition: "LP", language: "DE" });
    expect(signed.binder).toBeNull();
    // A printing only made in foil comes in as foil (the app's resolveFinish); "Unsorted" is Unsorted.
    const fab = added.find((s) => s.item.card.scryfallId === fable.scryfallId)!;
    expect(fab.item.foil).toBe(true);
    expect(fab.binder).toBeNull();
    expect(collection(f).length).toBe(before + 3);
  });

  it("puts lines without a binder in the chosen binder and keeps an existing pile's price", () => {
    const f = appFile();
    const main = binders(f)[0].uid!;
    const lines = readCsv("Scryfall ID,Quantity,Purchase price\n44444444-4444-4444-4444-444444444444,1,2\n44444444-4444-4444-4444-444444444444,1,9\n");
    const r = applyImport(f, lines, keyCards([bolt]), main, NOW, null, uids);
    expect(r.imported).toBe(2);
    const pile = collection(f).filter((s) => s.item.card.scryfallId === bolt.scryfallId && s.binder === main);
    expect(pile).toHaveLength(1);
    expect(pile[0].item).toMatchObject({ quantity: 2, purchasePrice: 2 });
  });
});
