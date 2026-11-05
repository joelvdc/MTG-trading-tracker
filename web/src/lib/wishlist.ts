// Wishlist edits, following the app's rules (app: MtgRepository.addToWishlist/ownedFor, the wishlist dialog, and
// the sync triggers in SyncSchema.kt that stamp uid and updatedAt).
import {
  addDeletion,
  bump,
  collection,
  compareUid,
  type CardRef,
  type Json,
  newUid,
  type SyncFile,
  type WishlistItem,
  wishlist,
} from "./syncfile";

export type WishEdit =
  | { type: "add"; card: CardRef; foil: boolean; quantity: number; anyPrinting: boolean }
  | {
      type: "update";
      uid: string;
      quantity?: number;
      notes?: string | null;
      anyPrinting?: boolean;
      foil?: boolean;
      /** A price level in [alertCurrency], or null for no alert. */
      alertBelow?: number | null;
      alertCurrency?: string | null;
      ordered?: boolean;
    }
  | { type: "remove"; uid: string };

export class EditError extends Error {}

const MAX_QUANTITY = 999;

/** SQLite's NOCASE: ASCII letters compared without case. */
function sameName(a: string, b: string): boolean {
  const fold = (s: string) => s.replace(/[A-Z]/g, (c) => c.toLowerCase());
  return fold(a) === fold(b);
}

/** Copies of the card already in the collection (the app's ownedFor). */
export function ownedFor(file: SyncFile, card: CardRef, foil: boolean, anyPrinting: boolean): number {
  let n = 0;
  for (const s of collection(file)) {
    const c = s.item.card;
    if (anyPrinting ? sameName(c.name, card.name) : c.scryfallId === card.scryfallId && s.item.foil === foil) n += s.item.quantity;
  }
  return n;
}

function checkQuantity(q: number): number {
  if (!Number.isInteger(q) || q < 1 || q > MAX_QUANTITY) throw new EditError(`Quantity must be a whole number from 1 to ${MAX_QUANTITY}.`);
  return q;
}

function checkCard(card: CardRef): CardRef {
  const ok =
    card &&
    typeof card.scryfallId === "string" &&
    /^[0-9a-f-]{36}$/.test(card.scryfallId) &&
    typeof card.name === "string" &&
    card.name.length > 0 &&
    typeof card.setCode === "string";
  if (!ok) throw new EditError("That card couldn't be read.");
  // Exactly the app's fields, in the app's order.
  return {
    scryfallId: card.scryfallId,
    name: card.name,
    setCode: card.setCode,
    setName: card.setName ?? "",
    collectorNumber: card.collectorNumber ?? "",
    rarity: card.rarity ?? "",
    imageUrl: card.imageUrl ?? null,
    cardmarketId: card.cardmarketId ?? null,
    fallbackEur: card.fallbackEur ?? null,
    fallbackEurFoil: card.fallbackEurFoil ?? null,
    hasNonFoil: !!card.hasNonFoil,
    hasFoil: !!card.hasFoil,
    foilType: card.foilType ?? null,
    hasEtched: !!card.hasEtched,
    flavorName: card.flavorName ?? null,
    cardmarketFoilId: card.cardmarketFoilId ?? null,
  };
}

function setList(file: SyncFile, items: WishlistItem[]): void {
  items.sort((a, b) => compareUid(a.uid, b.uid));
  file.data.wishlist = items as unknown as Json;
}

/** Applies [edit] to [file] at [now]. Returns the uid of the entry it changed. ([makeUid] is for tests.) */
export function applyEdit(file: SyncFile, edit: WishEdit, now: number, makeUid: () => string = newUid): string {
  const items = wishlist(file);
  switch (edit.type) {
    case "add": {
      const card = checkCard(edit.card);
      const qty = checkQuantity(edit.quantity);
      const foil = !!edit.foil;
      // Like the app: more copies on a wanted entry for the same name, if it takes this printing and finish.
      const same = items.find(
        (w) =>
          w.receivedAt === null &&
          sameName(w.card.name, card.name) &&
          (w.anyPrinting || (w.card.scryfallId === card.scryfallId && w.foil === foil)),
      );
      if (same) {
        same.quantity = Math.min(MAX_QUANTITY, same.quantity + qty);
        same.updatedAt = bump(same.updatedAt, now);
        setList(file, items);
        return same.uid!;
      }
      const anyPrinting = edit.anyPrinting !== false;
      const item: WishlistItem = {
        id: 0,
        card,
        foil,
        quantity: qty,
        anyPrinting,
        notes: null,
        addedAt: now,
        ownedAtAdd: ownedFor(file, card, foil, anyPrinting),
        orderedAt: null,
        alertBelow: null,
        alertCurrency: null,
        receivedAt: null,
        receivedFrom: null,
        uid: makeUid(),
        updatedAt: now,
      };
      items.push(item);
      setList(file, items);
      return item.uid!;
    }
    case "update": {
      const w = items.find((x) => x.uid === edit.uid);
      if (!w) throw new EditError("That card isn't on the wishlist any more.");
      if (w.receivedAt !== null) throw new EditError("Received cards can only be changed in the app.");
      if (edit.quantity !== undefined) w.quantity = checkQuantity(edit.quantity);
      if (edit.notes !== undefined) {
        const n = (edit.notes ?? "").trim();
        if (n.length > 500) throw new EditError("Notes can be at most 500 characters.");
        w.notes = n === "" ? null : n;
      }
      if (edit.anyPrinting !== undefined) w.anyPrinting = !!edit.anyPrinting;
      if (edit.foil !== undefined) {
        if (edit.foil && !(w.card.hasFoil || w.card.hasEtched)) throw new EditError("This printing has no foil.");
        if (!edit.foil && !w.card.hasNonFoil) throw new EditError("This printing is only made in foil.");
        w.foil = !!edit.foil;
      }
      if (edit.alertBelow !== undefined) {
        if (edit.alertBelow === null) {
          w.alertBelow = null;
          w.alertCurrency = null;
        } else {
          if (!(typeof edit.alertBelow === "number" && edit.alertBelow > 0 && edit.alertBelow < 1e7)) throw new EditError("The alert level must be more than 0.");
          const cur = edit.alertCurrency ?? "EUR";
          if (!["EUR", "DKK", "USD"].includes(cur)) throw new EditError("Unknown currency.");
          w.alertBelow = edit.alertBelow;
          w.alertCurrency = cur;
        }
      }
      if (edit.ordered !== undefined) w.orderedAt = edit.ordered ? (w.orderedAt ?? now) : null;
      w.updatedAt = bump(w.updatedAt, now);
      setList(file, items);
      return w.uid!;
    }
    case "remove": {
      const i = items.findIndex((x) => x.uid === edit.uid);
      if (i < 0) return edit.uid; // Already gone (removed on a phone): nothing to do.
      const [gone] = items.splice(i, 1);
      setList(file, items);
      // Later than the entry's last change even if a phone's clock runs ahead, so the deletion wins on every phone.
      addDeletion(file, edit.uid, bump(gone.updatedAt, now));
      return edit.uid;
    }
  }
}
