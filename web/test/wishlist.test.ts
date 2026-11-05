import { readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { canWrite, decode, deletions, encode, parse, stamp, wishlist, type CardRef, type SyncFile } from "@/lib/syncfile";
import { applyEdit, EditError, ownedFor } from "@/lib/wishlist";

const fixtures = path.join(__dirname, "fixtures");
/** A sync file as the app writes it (made by the app's WebSyncFixtureTest). */
const appText = () => readFileSync(path.join(fixtures, "app-sync.json"), "utf8");
const appFile = (): SyncFile => parse(appText());

const NOW = 1_750_000_000_000;
const bolt: CardRef = {
  scryfallId: "44444444-4444-4444-4444-444444444444",
  name: "Lightning Bolt",
  setCode: "2xm",
  setName: "Double Masters",
  collectorNumber: "117",
  rarity: "uncommon",
  imageUrl: "https://cards.scryfall.io/normal/front/4.jpg",
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
const SOL = "a0000000000000000000000000000001";
const RHYSTIC = "a0000000000000000000000000000002";
const RIFT = "a0000000000000000000000000000003";

/** The edits whose result the app's WebSyncFixtureTest checks on the phone side. */
function webEdits(file: SyncFile): void {
  applyEdit(file, { type: "add", card: bolt, foil: false, quantity: 4, anyPrinting: true }, NOW, () => "f0000000000000000000000000000001");
  applyEdit(file, { type: "update", uid: SOL, quantity: 3, ordered: true, alertBelow: 2.5, alertCurrency: "EUR" }, NOW);
  applyEdit(file, { type: "remove", uid: RHYSTIC }, NOW);
  stamp(file, NOW);
}

describe("the app's sync file", () => {
  it("reads and writes back unchanged", () => {
    const text = appText();
    const back = decode(encode(parse(text)));
    expect(JSON.stringify(back)).toBe(JSON.stringify(JSON.parse(text)));
  });

  it("is a format the website can write", () => {
    expect(canWrite(appFile())).toBe(true);
    expect(canWrite({ ...appFile(), format: 99 })).toBe(false);
  });

  it("keeps fields the website doesn't know", () => {
    const file = appFile();
    (file.data.wishlist as any)[0].somethingNew = { kept: true };
    (file.data as any).newSection = [1, 2];
    applyEdit(file, { type: "update", uid: SOL, quantity: 5 }, NOW);
    const back = decode(encode(file));
    expect((back.data.wishlist as any)[0].somethingNew).toEqual({ kept: true });
    expect((back.data as any).newSection).toEqual([1, 2]);
  });
});

describe("wishlist edits", () => {
  it("match the file the app's test checks (web/test/fixtures/web-edited-sync.json)", () => {
    const file = appFile();
    webEdits(file);
    const text = JSON.stringify(file) + "\n";
    const golden = path.join(fixtures, "web-edited-sync.json");
    if (process.env.WRITE_WEB_FIXTURES === "1") writeFileSync(golden, text);
    expect(text).toBe(readFileSync(golden, "utf8"));
  });

  it("add a new entry with the app's fields and order", () => {
    const file = appFile();
    const uid = applyEdit(file, { type: "add", card: bolt, foil: true, quantity: 2, anyPrinting: false }, NOW);
    expect(uid).toMatch(/^[0-9a-f]{32}$/);
    const item = wishlist(file).find((w) => w.uid === uid)!;
    expect(Object.keys(item)).toEqual(Object.keys(wishlist(appFile())[0]));
    expect(Object.keys(item.card)).toEqual(Object.keys(wishlist(appFile())[0].card));
    expect(item).toMatchObject({ id: 0, foil: true, quantity: 2, anyPrinting: false, addedAt: NOW, updatedAt: NOW, ownedAtAdd: 0 });
    const uids = wishlist(file).map((w) => w.uid!);
    expect(uids).toEqual([...uids].sort());
  });

  it("add copies to a wanted entry for the same name", () => {
    const file = appFile();
    const uid = applyEdit(file, { type: "add", card: { ...bolt, name: "sol ring", scryfallId: "55555555-5555-5555-5555-555555555555" }, foil: true, quantity: 1, anyPrinting: true }, NOW);
    expect(uid).toBe(SOL);
    expect(wishlist(file).find((w) => w.uid === SOL)!.quantity).toBe(3);
  });

  it("count copies already owned when adding (any printing: by name)", () => {
    const file = appFile();
    expect(ownedFor(file, { ...bolt, name: "Sol Ring" }, false, true)).toBe(1);
    expect(ownedFor(file, { ...bolt, name: "Sol Ring" }, false, false)).toBe(0);
  });

  it("keep the first order date when ticked again, and clear it when unticked", () => {
    const file = appFile();
    applyEdit(file, { type: "update", uid: RHYSTIC, ordered: true }, NOW);
    expect(wishlist(file).find((w) => w.uid === RHYSTIC)!.orderedAt).toBe(1_700_000_300_000);
    applyEdit(file, { type: "update", uid: RHYSTIC, ordered: false }, NOW);
    expect(wishlist(file).find((w) => w.uid === RHYSTIC)!.orderedAt).toBeNull();
  });

  it("stamp a change later than the entry's last one, even with a clock behind", () => {
    const file = appFile();
    applyEdit(file, { type: "update", uid: SOL, notes: "x" }, 5);
    expect(wishlist(file).find((w) => w.uid === SOL)!.updatedAt).toBe(1_700_000_100_001);
  });

  it("record a removal so phones delete it too", () => {
    const file = appFile();
    applyEdit(file, { type: "remove", uid: RHYSTIC }, NOW);
    expect(wishlist(file).some((w) => w.uid === RHYSTIC)).toBe(false);
    expect(deletions(file)).toContainEqual({ uid: RHYSTIC, deletedAt: NOW });
  });

  it("leave received cards to the app", () => {
    expect(() => applyEdit(appFile(), { type: "update", uid: RIFT, quantity: 2 }, NOW)).toThrow(EditError);
  });

  it("refuse bad values", () => {
    const file = appFile();
    expect(() => applyEdit(file, { type: "update", uid: SOL, quantity: 0 }, NOW)).toThrow(EditError);
    expect(() => applyEdit(file, { type: "update", uid: SOL, alertBelow: -1 }, NOW)).toThrow(EditError);
    expect(() => applyEdit(file, { type: "update", uid: SOL, alertBelow: 1, alertCurrency: "GBP" }, NOW)).toThrow(EditError);
    expect(() => applyEdit(file, { type: "add", card: { ...bolt, scryfallId: "../x" }, foil: false, quantity: 1, anyPrinting: true }, NOW)).toThrow(EditError);
  });
});
