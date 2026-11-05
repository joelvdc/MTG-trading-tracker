// Collection and binder edits, following the app's rules (MtgRepository.addToCollection / updateCollectionItem /
// saveCollectionEdit, and Sync.kt's clean): one stack per printing, finish, condition, language, binder and marks;
// a change that makes two stacks the same merges them; deleted stacks are recorded so every phone deletes them.
import { CONDITIONS, FINISHES, LANGUAGES, finishesOf, resolveFinish } from "./cards";
import type { PriceFile } from "./prices";
import type { Finish } from "./pricing";
import {
  addDeletion,
  binders as bindersOf,
  bump,
  collection,
  prefs,
  setPref,
  compareUid,
  type Binder,
  type CardRef,
  type CollectionItem,
  type Json,
  newUid,
  type SyncFile,
  type SyncStack,
} from "./syncfile";
import { checkCard, EditError, sameName } from "./wishlist";

export const MAX_QUANTITY = 9999;
const CURRENCIES = ["EUR", "DKK", "USD"];

/** What one copy cost: an amount in a currency, or null for "not known". */
export type Paid = { amount: number; currency: string } | null;

export interface StackFields {
  finish?: Finish;
  condition?: string;
  language?: string;
  /** The binder's uid; null for Unsorted. */
  binder?: string | null;
  signed?: boolean;
  altered?: boolean;
}

export type CollectionEdit =
  | ({ type: "add"; card: CardRef; quantity: number; paid?: Paid } & StackFields)
  | ({
      type: "update";
      uid: string;
      quantity?: number;
      notes?: string | null;
      paid?: Paid;
      /** Another printing of the same card. */
      card?: CardRef;
      /** With a new [binder]: how many copies go there (the rest stay); all of them when left out. */
      move?: number;
      /**
       * With changed signed/altered marks on several copies: how many copies keep the old marks (the app's keepMarks);
       * the others become a pile of their own with the new marks.
       */
      keepMarks?: number;
    } & StackFields)
  | { type: "remove"; uid: string }
  | { type: "bulk"; uids: string[]; binder?: string | null; condition?: string; language?: string; paid?: Paid; remove?: boolean }
  | { type: "addBinder"; name: string }
  | { type: "renameBinder"; uid: string; name: string }
  /** Every card of [from] (null: Unsorted) into [into], then [from] deleted if [deleteSource] (the app's mergeBinder). */
  | { type: "mergeBinder"; from: string | null; into: string | null; deleteSource: boolean }
  /** Deletes a binder; its cards go to Unsorted, or with [deleteCards] out of the collection (the app's deleteBinder). */
  | { type: "deleteBinder"; uid: string; deleteCards: boolean }
  /** The binder bar's order ("unsorted", "wishlist", "b:<uid>"), the app's "Arrange binders"; empty: the usual order. */
  | { type: "binderOrder"; keys: string[] };

export interface EditResult {
  /** The stack the edit ended in (after merging), or the new binder's uid. */
  uid: string | null;
}

function checkQuantity(q: unknown): number {
  if (typeof q !== "number" || !Number.isInteger(q) || q < 1 || q > MAX_QUANTITY) {
    throw new EditError(`Copies must be a whole number from 1 to ${MAX_QUANTITY}.`);
  }
  return q;
}

function checkCondition(c: unknown): string {
  if (typeof c !== "string" || !CONDITIONS.some((x) => x[0] === c)) throw new EditError("Unknown condition.");
  return c;
}

function checkLanguage(l: unknown): string {
  if (typeof l !== "string" || !LANGUAGES.some((x) => x[0] === l)) throw new EditError("Unknown language.");
  return l;
}

function checkFinish(f: unknown): Finish {
  if (typeof f !== "string" || !FINISHES.includes(f as Finish)) throw new EditError("Unknown finish.");
  return f as Finish;
}

function checkBinder(file: SyncFile, b: unknown): string | null {
  if (b === null) return null;
  if (typeof b !== "string" || !bindersOf(file).some((x) => x.uid === b)) throw new EditError("That binder doesn't exist any more.");
  return b;
}

/** Cents, like the app's TypedPrice.cents. */
function cents(v: number): number {
  return Math.round(v * 100) / 100;
}

/** Sets the purchase price as the app does (euros, plus the amount as typed in kroner or dollars). */
function setPaid(item: CollectionItem, paid: Paid, rates: PriceFile["rates"] | null): void {
  if (paid === null) {
    item.purchasePrice = null;
    item.paidTyped = null;
    item.paidCurrency = null;
    return;
  }
  const { amount, currency } = paid;
  if (typeof amount !== "number" || !Number.isFinite(amount) || amount < 0 || amount > 1e7) throw new EditError("The purchase price must be 0 or more.");
  if (!CURRENCIES.includes(currency)) throw new EditError("Unknown currency.");
  if (currency === "EUR") {
    item.purchasePrice = cents(amount);
    item.paidTyped = null;
    item.paidCurrency = null;
    return;
  }
  const rate = rates?.perEuro[currency] ?? (currency === "DKK" ? 7.46038 : null);
  if (!rate) throw new EditError(`No exchange rate for ${currency} yet; enter the price in euros.`);
  item.purchasePrice = cents(amount / rate);
  item.paidTyped = amount;
  item.paidCurrency = currency;
}

function finishOf(item: CollectionItem): Finish {
  return item.etched ? "ETCHED" : item.foil ? "FOIL" : "NONFOIL";
}

function setFinish(item: CollectionItem, f: Finish): void {
  item.foil = f !== "NONFOIL";
  item.etched = f === "ETCHED";
}

/** The stack key: the same printing, finish, condition, language, binder and marks is one stack. */
function sameStack(a: SyncStack, b: SyncStack): boolean {
  return (
    a.item.card.scryfallId === b.item.card.scryfallId &&
    a.item.foil === b.item.foil &&
    a.item.etched === b.item.etched &&
    a.item.condition === b.item.condition &&
    a.item.language === b.item.language &&
    a.binder === b.binder &&
    a.item.signed === b.item.signed &&
    a.item.altered === b.item.altered
  );
}

function setList(file: SyncFile, stacks: SyncStack[]): void {
  stacks.sort((a, b) => compareUid(a.item.uid, b.item.uid));
  file.data.collection = stacks as unknown as Json;
}

function newItem(card: CardRef, now: number, uid: string): CollectionItem {
  return {
    id: 0,
    card,
    foil: false,
    condition: "NM",
    language: "EN",
    quantity: 1,
    addedAt: now,
    etched: false,
    binderId: 0,
    uid,
    updatedAt: now,
    notes: null,
    purchasePrice: null,
    signed: false,
    altered: false,
    paidTyped: null,
    paidCurrency: null,
  };
}

/**
 * When [s] is now the same stack as another one, the other one gets its copies and [s] goes (the app's
 * updateCollectionItem: the existing stack keeps its notes and purchase price). Returns the stack that's left.
 */
function mergeIfSame(file: SyncFile, stacks: SyncStack[], s: SyncStack, now: number): SyncStack {
  const clash = stacks.find((o) => o !== s && sameStack(o, s));
  if (!clash) return s;
  clash.item.quantity = Math.min(MAX_QUANTITY, clash.item.quantity + s.item.quantity);
  clash.item.updatedAt = bump(clash.item.updatedAt, now);
  stacks.splice(stacks.indexOf(s), 1);
  addDeletion(file, s.item.uid!, bump(s.item.updatedAt, now));
  return clash;
}

function removeStack(file: SyncFile, stacks: SyncStack[], uid: string, now: number): void {
  const i = stacks.findIndex((x) => x.item.uid === uid);
  if (i < 0) return; // Already gone (deleted on a phone).
  const [gone] = stacks.splice(i, 1);
  addDeletion(file, uid, bump(gone.item.updatedAt, now));
}

/** Takes a binder out of the list and records the deletion, so every phone deletes it too. */
function deleteBinder(file: SyncFile, uid: string, now: number): void {
  const list = bindersOf(file);
  const i = list.findIndex((b) => b.uid === uid);
  if (i < 0) return;
  const [gone] = list.splice(i, 1);
  file.data.binders = list as unknown as Json;
  addDeletion(file, uid, bump(gone.updatedAt, now));
}

function checkBinderName(file: SyncFile, name: unknown, except?: string): string {
  const n = typeof name === "string" ? name.trim().replace(/\s+/g, " ") : "";
  if (!n || n.length > 60) throw new EditError("A binder name needs 1 to 60 characters.");
  const low = n.toLowerCase();
  if (["unsorted", "all", "wishlist"].includes(low)) throw new EditError(`"${n}" is taken by the app.`);
  // Sync folds binders with the same name into one, so names stay unique.
  if (bindersOf(file).some((b) => b.uid !== except && b.name.trim().toLowerCase() === low)) throw new EditError(`There's already a binder called "${n}".`);
  return n;
}

/** Applies [edit] to [file] at [now]. ([makeUid] is for tests.) */
export function applyCollectionEdit(
  file: SyncFile,
  edit: CollectionEdit,
  now: number,
  rates: PriceFile["rates"] | null = null,
  makeUid: () => string = newUid,
): EditResult {
  const stacks = collection(file);
  switch (edit.type) {
    case "add": {
      const card = checkCard(edit.card);
      const qty = checkQuantity(edit.quantity);
      const finish = checkFinish(edit.finish ?? "NONFOIL");
      if (!finishesOf(card).includes(finish)) throw new EditError("This printing isn't made in that finish.");
      const s: SyncStack = { item: newItem(card, now, makeUid()), binder: checkBinder(file, edit.binder ?? null) };
      setFinish(s.item, finish);
      s.item.condition = checkCondition(edit.condition ?? "NM");
      s.item.language = checkLanguage(edit.language ?? "EN");
      s.item.signed = edit.signed === true;
      s.item.altered = edit.altered === true;
      s.item.quantity = qty;
      if (edit.paid !== undefined) setPaid(s.item, edit.paid, rates);
      // Like the app: more copies on the same stack if there is one (it keeps its own purchase price).
      const same = stacks.find((o) => sameStack(o, s));
      if (same) {
        same.item.quantity = Math.min(MAX_QUANTITY, same.item.quantity + qty);
        same.item.updatedAt = bump(same.item.updatedAt, now);
        setList(file, stacks);
        return { uid: same.item.uid };
      }
      stacks.push(s);
      setList(file, stacks);
      return { uid: s.item.uid };
    }
    case "update": {
      const s = stacks.find((x) => x.item.uid === edit.uid);
      if (!s) throw new EditError("Those cards aren't in the collection any more (changed on a phone?).");
      const it = s.item;
      const before = { signed: it.signed, altered: it.altered };
      if (edit.card !== undefined) {
        const card = checkCard(edit.card);
        if (!sameName(card.name, it.card.name)) throw new EditError("That's another card, not a printing of this one.");
        if (card.scryfallId !== it.card.scryfallId) {
          it.card = card;
          setFinish(it, resolveFinish(card, edit.finish !== undefined ? checkFinish(edit.finish) : finishOf(it)));
        }
      }
      if (edit.finish !== undefined) {
        const f = checkFinish(edit.finish);
        if (f !== finishOf(it) && !finishesOf(it.card).includes(f)) throw new EditError("This printing isn't made in that finish.");
        setFinish(it, f);
      }
      if (edit.quantity !== undefined) it.quantity = checkQuantity(edit.quantity);
      if (edit.condition !== undefined) it.condition = checkCondition(edit.condition);
      if (edit.language !== undefined) it.language = checkLanguage(edit.language);
      if (edit.signed !== undefined) it.signed = edit.signed === true;
      if (edit.altered !== undefined) it.altered = edit.altered === true;
      if (edit.notes !== undefined) {
        const n = (edit.notes ?? "").trim();
        if (n.length > 1000) throw new EditError("Notes can be at most 1000 characters.");
        it.notes = n === "" ? null : n;
      }
      if (edit.paid !== undefined) setPaid(it, edit.paid, rates);
      it.updatedAt = bump(it.updatedAt, now);
      if (edit.keepMarks !== undefined && (it.signed !== before.signed || it.altered !== before.altered)) {
        // Only some copies are signed/altered: this pile keeps the old marks, the others become a pile of their own
        // (the app's saveCollectionEdit with keepMarks). A binder change moves both.
        const keep = checkQuantity(edit.keepMarks);
        if (keep >= it.quantity) throw new EditError("At least one copy has to get the new marks.");
        if (edit.binder !== undefined) s.binder = checkBinder(file, edit.binder);
        const marked: SyncStack = { item: { ...it, uid: makeUid(), quantity: it.quantity - keep, updatedAt: now }, binder: s.binder };
        it.signed = before.signed;
        it.altered = before.altered;
        it.quantity = keep;
        stacks.push(marked);
        mergeIfSame(file, stacks, s, now);
        const there = mergeIfSame(file, stacks, marked, now);
        setList(file, stacks);
        return { uid: there.item.uid };
      }
      if (edit.binder !== undefined) {
        const to = checkBinder(file, edit.binder);
        const n = edit.move === undefined ? it.quantity : checkQuantity(edit.move);
        if (to !== s.binder && n < it.quantity) {
          // Part of the stack moves: a stack of [n] copies in the new binder (the app's addToCollection).
          it.quantity -= n;
          const moved: SyncStack = { item: { ...newItem(it.card, now, makeUid()) }, binder: to };
          setFinish(moved.item, finishOf(it));
          Object.assign(moved.item, { condition: it.condition, language: it.language, signed: it.signed, altered: it.altered, quantity: n });
          stacks.push(moved);
          const left = mergeIfSame(file, stacks, s, now);
          const there = mergeIfSame(file, stacks, moved, now);
          setList(file, stacks);
          return { uid: (there ?? left).item.uid };
        }
        s.binder = to;
      }
      const kept = mergeIfSame(file, stacks, s, now);
      setList(file, stacks);
      return { uid: kept.item.uid };
    }
    case "remove": {
      removeStack(file, stacks, edit.uid, now);
      setList(file, stacks);
      return { uid: null };
    }
    case "bulk": {
      if (!Array.isArray(edit.uids) || edit.uids.length === 0) throw new EditError("No cards chosen.");
      if (edit.uids.length > 5000) throw new EditError("Too many cards at once.");
      const to = edit.binder !== undefined ? checkBinder(file, edit.binder) : undefined;
      const cond = edit.condition !== undefined ? checkCondition(edit.condition) : undefined;
      const lang = edit.language !== undefined ? checkLanguage(edit.language) : undefined;
      // A wrong price stops the whole change before any pile is touched.
      if (edit.paid !== undefined) setPaid({} as CollectionItem, edit.paid, rates);
      for (const uid of new Set(edit.uids)) {
        if (edit.remove) {
          removeStack(file, stacks, uid, now);
          continue;
        }
        const s = stacks.find((x) => x.item.uid === uid);
        if (!s) continue; // Gone meanwhile.
        if (to !== undefined) s.binder = to;
        if (cond !== undefined) s.item.condition = cond;
        if (lang !== undefined) s.item.language = lang;
        if (edit.paid !== undefined) setPaid(s.item, edit.paid, rates);
        s.item.updatedAt = bump(s.item.updatedAt, now);
        mergeIfSame(file, stacks, s, now);
      }
      setList(file, stacks);
      return { uid: null };
    }
    case "addBinder": {
      const name = checkBinderName(file, edit.name);
      const list = bindersOf(file);
      const b: Binder = { id: 0, name, createdAt: now, uid: makeUid(), updatedAt: now };
      list.push(b);
      list.sort((x, y) => compareUid(x.uid, y.uid));
      file.data.binders = list as unknown as Json;
      return { uid: b.uid };
    }
    case "renameBinder": {
      const list = bindersOf(file);
      const b = list.find((x) => x.uid === edit.uid);
      if (!b) throw new EditError("That binder doesn't exist any more.");
      b.name = checkBinderName(file, edit.name, b.uid!);
      b.updatedAt = bump(b.updatedAt, now);
      file.data.binders = list as unknown as Json;
      return { uid: b.uid };
    }
    case "mergeBinder": {
      const from = checkBinder(file, edit.from);
      const into = checkBinder(file, edit.into);
      if (from === into) throw new EditError("Pick another binder to move the cards to.");
      for (const s of stacks.filter((x) => x.binder === from)) {
        s.binder = into;
        s.item.updatedAt = bump(s.item.updatedAt, now);
        mergeIfSame(file, stacks, s, now);
      }
      setList(file, stacks);
      if (edit.deleteSource && from !== null) deleteBinder(file, from, now);
      return { uid: into };
    }
    case "deleteBinder": {
      const uid = checkBinder(file, edit.uid);
      if (uid === null) throw new EditError("Unsorted can't be deleted.");
      for (const s of stacks.filter((x) => x.binder === uid)) {
        if (edit.deleteCards) {
          removeStack(file, stacks, s.item.uid!, now);
          continue;
        }
        s.binder = null;
        s.item.updatedAt = bump(s.item.updatedAt, now);
        mergeIfSame(file, stacks, s, now);
      }
      setList(file, stacks);
      deleteBinder(file, uid, now);
      return { uid: null };
    }
    case "binderOrder": {
      if (!Array.isArray(edit.keys) || edit.keys.length > 1000) throw new EditError("That order doesn't fit.");
      const known = new Set(["unsorted", "wishlist", ...bindersOf(file).map((b) => `b:${b.uid}`)]);
      const keys = [...new Set(edit.keys.filter((k) => typeof k === "string" && known.has(k)))];
      if ((prefs(file).binderOrder ?? "") !== keys.join(",")) setPref(file, "binderOrder", keys.join(","), now);
      return { uid: null };
    }
  }
  throw new EditError("Unknown change.");
}
