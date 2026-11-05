import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { applyCollectionEdit } from "@/lib/collection";
import { ADDED, applyReceived } from "@/lib/received";
import { collection, deletions, parse, setPref, wishlist, type SyncFile } from "@/lib/syncfile";
import { EditError } from "@/lib/wishlist";

const appFile = (): SyncFile => parse(readFileSync(path.join(__dirname, "fixtures", "app-sync.json"), "utf8"));
const NOW = 1_750_000_000_000;
const SOL = "a0000000000000000000000000000001";
const RHYSTIC = "a0000000000000000000000000000002";
const RIFT = "a0000000000000000000000000000003";
const SOL_STACK = "c0000000000000000000000000000001";
let n = 0;
const uids = () => `e${String(++n).padStart(31, "0")}`;
const wish = (f: SyncFile, uid: string) => wishlist(f).find((w) => w.uid === uid);

describe("the wishlist's received flow", () => {
  it("marks cards received, adding them to the collection only when the app's setting says so", () => {
    const f = appFile();
    expect(applyReceived(f, { type: "receive", uids: [SOL] }, NOW, null, uids)).toBe(1);
    expect(wish(f, SOL)).toMatchObject({ receivedAt: NOW, receivedFrom: null });
    expect(collection(f)).toHaveLength(1);

    const g = appFile();
    setPref(g, "receivedToCollection", "true", NOW);
    applyReceived(g, { type: "receiveOrdered" }, NOW, null, uids);
    expect(wish(g, RHYSTIC)).toMatchObject({ receivedAt: NOW, receivedFrom: ADDED });
    const added = collection(g).find((s) => s.item.card.name === "Rhystic Study")!;
    expect(added).toMatchObject({ binder: null, item: { quantity: 1, condition: "NM", language: "EN", foil: true } });
    expect(wish(g, SOL)?.receivedAt).toBeNull();
  });

  it("marks the copies got since adding as received, splitting a card got only partly", () => {
    const f = appFile();
    // Sol Ring: 2 wanted, 1 owned when added; one more copy now.
    applyCollectionEdit(f, { type: "add", card: collection(f)[0].item.card, quantity: 1, binder: null }, NOW, null, uids);
    expect(applyReceived(f, { type: "receiveOwned" }, NOW, null, uids)).toBe(1);
    const sol = wishlist(f).filter((w) => w.card.name === "Sol Ring");
    expect(sol.map((w) => [w.quantity, w.receivedAt])).toEqual(expect.arrayContaining([[1, null], [1, NOW]]));
    expect(wish(f, SOL)).toMatchObject({ quantity: 1, ownedAtAdd: 2, receivedAt: null });
  });

  it("puts cards back on the wishlist and clears received ones for every phone", () => {
    const f = appFile();
    applyReceived(f, { type: "unreceive", uids: [RIFT] }, NOW, null, uids);
    expect(wish(f, RIFT)?.receivedAt).toBeNull();
    applyReceived(f, { type: "receive", uids: [SOL] }, NOW, null, uids);
    expect(applyReceived(f, { type: "clearReceived" }, NOW + 1, null, uids)).toBe(1);
    expect(wish(f, SOL)).toBeUndefined();
    expect(deletions(f).some((d) => d.uid === SOL)).toBe(true);
  });

  it("moves a received card into the collection, averaging the purchase price with the pile it joins", () => {
    const f = appFile();
    const sol = collection(f).find((s) => s.item.uid === SOL_STACK)!;
    applyCollectionEdit(f, { type: "update", uid: SOL_STACK, paid: { amount: 1, currency: "EUR" } }, NOW, null, uids);
    applyReceived(f, { type: "receive", uids: [SOL] }, NOW, null, uids);
    applyReceived(
      f,
      {
        type: "toCollection",
        uid: SOL,
        card: sol.item.card,
        finish: "NONFOIL",
        condition: sol.item.condition,
        language: sol.item.language,
        quantity: 1,
        binder: sol.binder,
        paid: { amount: 3, currency: "EUR" },
      },
      NOW + 1,
      null,
      uids,
    );
    const after = collection(f).find((s) => s.item.uid === SOL_STACK)!;
    expect(after.item).toMatchObject({ quantity: 2, purchasePrice: 2 });
    expect(wish(f, SOL)).toBeUndefined();
    expect(deletions(f).some((d) => d.uid === SOL)).toBe(true);
    // A card still wanted isn't moved.
    expect(() =>
      applyReceived(f, { type: "toCollection", uid: RHYSTIC, card: sol.item.card, finish: "NONFOIL", condition: "NM", language: "EN", quantity: 1, binder: null, paid: null }, NOW, null, uids),
    ).toThrow(EditError);
  });
});
