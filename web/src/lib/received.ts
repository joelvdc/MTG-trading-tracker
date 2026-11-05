// The wishlist's received flow, following the app (MtgRepository.receiveWishlist, receiveOwned, unreceiveWishlist,
// clearReceived and wishToCollection): received cards stay on the wishlist as a record until cleared, and can add
// their copies to the collection.
import { resolveFinish } from "./cards";
import { applyCollectionEdit, type Paid } from "./collection";
import type { PriceFile } from "./prices";
import type { Finish } from "./pricing";
import { addDeletion, bump, collection, compareUid, type CardRef, type Json, newUid, prefs, type SyncFile, type WishlistItem, wishlist } from "./syncfile";
import { checkCard, EditError, ownedFor, sameName } from "./wishlist";

/** What the app writes in receivedFrom when the received copies were added to the collection (WishOrders.ADDED). */
export const ADDED = "added to the collection";
/** Cards added offline in the app, without a printing yet (PendingCard). */
const PENDING_PREFIX = "00000000-0000-0000-0000-";

export type ReceivedEdit =
  /** Marks wanted cards as received; with the app's "add received cards to the collection" setting on, adds them too. */
  | { type: "receive"; uids: string[] }
  /** "Mark all ordered cards as received". */
  | { type: "receiveOrdered" }
  /** "Mark the cards I now own as received". */
  | { type: "receiveOwned" }
  /** "Back on the wishlist". */
  | { type: "unreceive"; uids: string[] }
  /** "Clear received cards". */
  | { type: "clearReceived" }
  /** A received card into the collection with the details picked; it leaves the wishlist (the app's wishToCollection). */
  | {
      type: "toCollection";
      uid: string;
      card: CardRef;
      finish: Finish;
      condition: string;
      language: string;
      quantity: number;
      binder: string | null;
      paid: Paid;
    };

export const RECEIVED_TYPES = ["receive", "receiveOrdered", "receiveOwned", "unreceive", "clearReceived", "toCollection"];

function setList(file: SyncFile, items: WishlistItem[]): void {
  items.sort((a, b) => compareUid(a.uid, b.uid));
  file.data.wishlist = items as unknown as Json;
}

/** The app's "add received cards to the collection" setting (synced). */
export function receivedToCollection(file: SyncFile): boolean {
  return prefs(file).receivedToCollection === "true";
}

/** A received copy of [w]: an entry of its own with [n] copies, the rest staying wanted (the app's split). */
function splitOff(items: WishlistItem[], w: WishlistItem, n: number, now: number, makeUid: () => string): WishlistItem {
  const got: WishlistItem = { ...w, id: 0, uid: makeUid(), quantity: n, updatedAt: now };
  w.quantity -= n;
  w.ownedAtAdd += n;
  w.updatedAt = bump(w.updatedAt, now);
  items.push(got);
  return got;
}

/** Applies [edit] at [now]. Returns how many cards it changed. */
export function applyReceived(
  file: SyncFile,
  edit: ReceivedEdit,
  now: number,
  rates: PriceFile["rates"] | null = null,
  makeUid: () => string = newUid,
): number {
  const items = wishlist(file);
  const wanted = items.filter((w) => w.receivedAt === null);
  const receive = (list: WishlistItem[]) => {
    const adding = receivedToCollection(file);
    for (const w of list) {
      const add = adding && !w.card.scryfallId.startsWith(PENDING_PREFIX) && w.receivedFrom === null;
      w.receivedAt = now;
      if (add) {
        w.receivedFrom = ADDED;
        const finish = resolveFinish(w.card, w.foil ? "FOIL" : "NONFOIL");
        applyCollectionEdit(file, { type: "add", card: w.card, quantity: w.quantity, finish, condition: "NM", language: "EN", binder: null }, now, rates, makeUid);
      }
      w.updatedAt = bump(w.updatedAt, now);
    }
    setList(file, items);
    return list.length;
  };
  switch (edit.type) {
    case "receive": {
      if (!Array.isArray(edit.uids) || edit.uids.length === 0) throw new EditError("No cards chosen.");
      return receive(wanted.filter((w) => edit.uids.includes(w.uid!)));
    }
    case "receiveOrdered":
      return receive(wanted.filter((w) => w.orderedAt !== null));
    case "receiveOwned": {
      let n = 0;
      for (const w of [...wanted]) {
        const got = Math.min(w.quantity, Math.max(0, ownedFor(file, w.card, w.foil, w.anyPrinting) - w.ownedAtAdd));
        if (got <= 0) continue;
        n += got;
        if (got >= w.quantity) {
          w.receivedAt = now;
          w.updatedAt = bump(w.updatedAt, now);
        } else {
          splitOff(items, w, got, now, makeUid).receivedAt = now;
        }
      }
      setList(file, items);
      return n;
    }
    case "unreceive": {
      if (!Array.isArray(edit.uids)) throw new EditError("No cards chosen.");
      const back = items.filter((w) => w.receivedAt !== null && edit.uids.includes(w.uid!));
      for (const w of back) {
        w.receivedAt = null;
        w.updatedAt = bump(w.updatedAt, now);
      }
      setList(file, items);
      return back.length;
    }
    case "clearReceived": {
      const gone = items.filter((w) => w.receivedAt !== null);
      setList(
        file,
        items.filter((w) => w.receivedAt === null),
      );
      for (const w of gone) addDeletion(file, w.uid!, bump(w.updatedAt, now));
      return gone.length;
    }
    case "toCollection": {
      const w = items.find((x) => x.uid === edit.uid);
      if (!w) throw new EditError("That card isn't on the wishlist any more.");
      if (w.receivedAt === null) throw new EditError("Mark the card as received first.");
      if (w.receivedFrom === ADDED) throw new EditError("These copies were added to the collection already.");
      const card = checkCard(edit.card);
      if (!sameName(card.name, w.card.name)) throw new EditError("That's another card.");
      const finish = resolveFinish(card, edit.finish);
      const before = collection(file).map((s) => ({ uid: s.item.uid, qty: s.item.quantity }));
      const { uid } = applyCollectionEdit(
        file,
        { type: "add", card, quantity: edit.quantity, finish, condition: edit.condition, language: edit.language, binder: edit.binder },
        now,
        rates,
        makeUid,
      );
      // The stack's purchase price becomes the average with what these copies cost (PurchasePrices.joined).
      const s = collection(file).find((x) => x.item.uid === uid)!;
      const had = before.find((b) => b.uid === uid)?.qty ?? 0;
      if (edit.paid) {
        const oldEur = s.item.purchasePrice;
        const oldTyped = s.item.paidTyped;
        const oldCur = s.item.paidCurrency;
        applyCollectionEdit(file, { type: "update", uid: uid!, paid: edit.paid }, now, rates, makeUid);
        if (had > 0 && oldEur != null) {
          const avg = (a: number, b: number) => Math.round(((had * a + edit.quantity * b) / (had + edit.quantity)) * 100) / 100;
          s.item.purchasePrice = avg(oldEur, s.item.purchasePrice!);
          if (oldCur && oldCur === s.item.paidCurrency && oldTyped != null && s.item.paidTyped != null) {
            // Both typed in the same currency: that amount is averaged too.
            s.item.paidTyped = avg(oldTyped, s.item.paidTyped);
          } else {
            s.item.paidTyped = null;
            s.item.paidCurrency = null;
          }
        }
      }
      setList(
        file,
        items.filter((x) => x !== w),
      );
      addDeletion(file, w.uid!, bump(w.updatedAt, now));
      return 1;
    }
  }
  throw new EditError("Unknown change.");
}
