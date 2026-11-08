import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { collectionView } from "@/lib/collview";
import { createDeck, NO_LOOKUPS } from "@/lib/deckedit";
import { syncDecks } from "@/lib/decks";
import { pricingSetup } from "@/lib/pricing";
import { applyScanEdit, scanCount, scanRows, scansOf, type DeckLookups, type ScannedCard } from "@/lib/scans";
import { binders, collection, deletions, parse, type CardRef, type Json, type SyncFile } from "@/lib/syncfile";
import { EditError } from "@/lib/wishlist";

const NOW = 1_750_000_000_000;
const MAIN = "b0000000000000000000000000000001";
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
const boltM10: CardRef = { ...bolt, scryfallId: "55555555-5555-5555-5555-555555555555", setCode: "m10", setName: "Magic 2010", collectorNumber: "146", hasFoil: false };

const scan = (uid: string, over: Partial<ScannedCard> = {}): ScannedCard => ({
  id: 0,
  card: bolt,
  foil: false,
  etched: false,
  condition: "NM",
  language: "EN",
  quantity: 1,
  exactPrinting: true,
  scannedAt: NOW - 1000,
  uid,
  updatedAt: NOW - 1000,
  ...over,
});

/** The app's example file with two scans waiting. */
function file(): SyncFile {
  const f = parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
  f.data.scans = [scan("s1", { quantity: 2 }), scan("s2", { foil: true, exactPrinting: false, condition: "EX" })] as unknown as Json;
  return f;
}

const look: DeckLookups = { facts: { [bolt.scryfallId]: { typeLine: "Instant", cmc: 1 } }, look: { ...NO_LOOKUPS, auto: new Map([[bolt.scryfallId, { cardId: null, category: "Removal" }]]) } };

describe("the scan list (the app's Scan tab)", () => {
  it("counts the waiting copies for the Scanned chip and lists them with prices", () => {
    const f = file();
    expect(scanCount(f)).toBe(3);
    expect(collectionView(f, null, null, null).scanCount).toBe(3);
    const rows = scanRows(f, pricingSetup({}, "trend", null, null, null), null);
    expect(rows.map((r) => [r.uid, r.finish, r.quantity, r.exactPrinting])).toEqual([
      ["s1", "NONFOIL", 2, true],
      ["s2", "FOIL", 1, false],
    ]);
    expect(rows[0].unitEur).toBe(1.2);
  });

  it("fixes a scan: another printing, finish, condition and copies; a picked printing is no longer a guess", () => {
    const f = file();
    applyScanEdit(f, { type: "update", uid: "s2", card: boltM10, condition: "NM", quantity: 3 }, NOW);
    const s = scansOf(f).find((x) => x.uid === "s2")!;
    expect(s).toMatchObject({ card: { setCode: "m10" }, foil: false, condition: "NM", quantity: 3, exactPrinting: true });
    expect(s.updatedAt).toBe(NOW);
    expect(() => applyScanEdit(f, { type: "update", uid: "s2", card: { ...bolt, name: "Shock" } }, NOW)).toThrow(EditError);
    expect(() => applyScanEdit(f, { type: "update", uid: "s2", finish: "FOIL" }, NOW)).toThrow(EditError);
  });

  it("discards with deletions for the phones, and Undo brings the cards back under new uids", () => {
    const f = file();
    const { removed } = applyScanEdit(f, { type: "discard", uids: ["s1"] }, NOW);
    expect(scansOf(f).map((s) => s.uid)).toEqual(["s2"]);
    expect(deletions(f).some((d) => d.uid === "s1")).toBe(true);
    applyScanEdit(f, { type: "restore", items: removed! }, NOW + 1, null, uids);
    const back = scansOf(f).find((s) => s.uid !== "s2")!;
    expect(back.uid).not.toBe("s1");
    expect(back).toMatchObject({ quantity: 2, card: { scryfallId: bolt.scryfallId } });
  });

  it("sends scans to a binder (joining the same pile) and takes them off the list unless kept", () => {
    const f = file();
    applyScanEdit(f, { type: "toBinder", uids: ["s1"], binder: MAIN, keep: false }, NOW, null, uids);
    applyScanEdit(f, { type: "toBinder", uids: ["s1x"].concat("s2"), binder: null, keep: true }, NOW, null, uids);
    const bolts = collection(f).filter((s) => s.item.card.scryfallId === bolt.scryfallId);
    expect(bolts.map((s) => [s.binder, s.item.quantity, s.item.foil, s.item.condition])).toEqual(
      expect.arrayContaining([
        [MAIN, 2, false, "NM"],
        [null, 1, true, "EX"],
      ]),
    );
    expect(scansOf(f).map((s) => s.uid)).toEqual(["s2"]);
    // Again into the main binder: the same pile grows.
    f.data.scans = [scan("s3")] as unknown as Json;
    applyScanEdit(f, { type: "toBinder", uids: ["s3"], binder: MAIN, keep: false }, NOW, null, uids);
    expect(collection(f).find((s) => s.binder === MAIN && s.item.card.scryfallId === bolt.scryfallId)!.item.quantity).toBe(3);
  });

  it("makes a new binder for them", () => {
    const f = file();
    applyScanEdit(f, { type: "toBinder", uids: ["s1", "s2"], binder: null, newBinder: "Fresh pulls", keep: false }, NOW, null, uids);
    const b = binders(f).find((x) => x.name === "Fresh pulls")!;
    expect(collection(f).filter((s) => s.binder === b.uid).reduce((q, s) => q + s.item.quantity, 0)).toBe(3);
    expect(scanCount(f)).toBe(0);
  });

  it("makes a new local deck from scans, one line per printing and finish, in auto categories", () => {
    const f = file();
    const { deckId } = applyScanEdit(f, { type: "toDeck", uids: ["s1", "s2"], target: { newName: "Burn" }, keep: false }, NOW, look);
    const sd = syncDecks(f).find((d) => d.deck.archidektId === deckId)!;
    expect(deckId).toBeLessThan(0);
    expect(sd.deck).toMatchObject({ name: "Burn", origin: "From 3 scanned cards", cardCount: 3 });
    expect(sd.cards.map((c) => [c.quantity, c.foil, c.category, c.types, c.cmc])).toEqual(
      expect.arrayContaining([
        [2, false, "Removal", "Instant", 1],
        [1, true, "Removal", "Instant", 1],
      ]),
    );
    expect(scanCount(f)).toBe(0);
  });

  it("adds scans to a local deck but not to a deck from Archidekt", () => {
    const f = file();
    const id = createDeck(f, "Mine", [], null, "", NOW, NO_LOOKUPS);
    applyScanEdit(f, { type: "toDeck", uids: ["s1"], target: { deckId: id }, keep: true }, NOW, look);
    expect(syncDecks(f).find((d) => d.deck.archidektId === id)!.cards.map((c) => [c.card.name, c.quantity, c.category])).toEqual([["Lightning Bolt", 2, "Removal"]]);
    expect(scanCount(f)).toBe(3);
    expect(() => applyScanEdit(f, { type: "toDeck", uids: ["s1"], target: { deckId: 10710772 }, keep: false }, NOW, look)).toThrow(/Archidekt/);
  });

  it("says so when the scans were sent on a phone meanwhile", () => {
    expect(() => applyScanEdit(file(), { type: "discard", uids: ["gone"] }, NOW)).toThrow(/any more/);
  });
});
