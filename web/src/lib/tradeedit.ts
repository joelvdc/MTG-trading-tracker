// Making and changing trades on the website (2026-11-13), with the app's rules (MtgRepository's trade functions and
// SyncStore's trade fields): lines merge like the app's addToTrade, prices are captured when a card is added (the app's
// snapshot), completing a trade adds the received cards to a binder and takes the given ones out (removeFromCollection),
// and undoing reverses exactly that. A trade travels whole in the sync file: every change moves its updatedAt on.
import { CONDITIONS, LANGUAGES, finishesOf, resolveFinish } from "./cards";
import { applyCollectionEdit, MAX_QUANTITY, takeCopies } from "./collection";
import { best, PRICE_TYPES, type PriceFile } from "./prices";
import { cardmarketSet, type Finish } from "./pricing";
import { addDeletion, binders, bump, compareUid, deletions, newUid, type CardRef, type Json, type SyncFile } from "./syncfile";
import { checkCard, EditError } from "./wishlist";

export type Side = "GET" | "GIVE";

/** Cardmarket's six figures as captured (the app's PriceSet). */
export type PriceSetJ = Record<(typeof PRICE_TYPES)[number], number | null>;

/** A trade line as the app syncs it (TradeItem with id, tradeId and appliedBinderId 0), field order kept. */
export interface TradeItemJ {
  id: number;
  tradeId: number;
  side: Side;
  card: CardRef;
  foil: boolean;
  condition: string;
  language: string;
  quantity: number;
  prices: PriceSetJ;
  customPrice: number | null;
  appliedDelta: number;
  addedAt: number;
  etched: boolean;
  appliedBinderId: number;
  customTyped: number | null;
  customCurrency: string | null;
}

export interface TradeJ {
  id: number;
  createdAt: number;
  partner: string;
  notes: string;
  applied: boolean;
  appliedAt: number | null;
  binderId: number;
  uid: string;
  updatedAt: number;
}

export interface SyncTradeJ {
  trade: TradeJ;
  /** The binder the received cards went into (null: Unsorted). */
  binder: string | null;
  items: { item: TradeItemJ; appliedBinder: string | null }[];
}

export type Paid = { amount: number; currency: string } | null;

export type TradeEdit =
  | { type: "new"; partner?: string; notes?: string }
  | { type: "update"; uid: string; partner?: string; notes?: string }
  | { type: "addLine"; uid: string; side: Side; card: CardRef; finish: Finish; condition?: string; language?: string; quantity?: number }
  | { type: "updateLine"; uid: string; line: string; quantity?: number; condition?: string; language?: string; finish?: Finish; custom?: Paid }
  | { type: "removeLine"; uid: string; line: string }
  | { type: "refreshPrices"; uid: string }
  /** Completes the trade: received cards into [binder] (null: Unsorted), given cards out of the collection. */
  | { type: "apply"; uid: string; binder: string | null }
  | { type: "undo"; uid: string }
  | { type: "remove"; uid: string }
  /** Puts a deleted trade back exactly as it was (the Undo after deleting). */
  | { type: "restore"; trade: SyncTradeJ };

export interface TradeEditResult {
  /** The trade the edit was about (a new trade's uid). */
  uid: string | null;
  /** Completing: given copies that weren't in the collection. */
  missing?: number;
  /** Deleting: the trade as it was, for the Undo. */
  removed?: SyncTradeJ;
}

const CURRENCIES = ["EUR", "DKK", "USD"];
const MAX_TEXT = 500;

export function trades(file: SyncFile): SyncTradeJ[] {
  return ((file.data.trades as Json[] | undefined) ?? []) as unknown as SyncTradeJ[];
}

function setTrades(file: SyncFile, list: SyncTradeJ[]): void {
  list.sort((a, b) => compareUid(a.trade.uid, b.trade.uid));
  file.data.trades = list as unknown as Json;
}

const cmp = (a: string | number | boolean, b: string | number | boolean) => (a < b ? -1 : a > b ? 1 : 0);

/** SyncTrade.sorted: lines by side, time added, printing, finish, condition, language, copies. */
function sortItems(t: SyncTradeJ): void {
  t.items.sort((x, y) => {
    const a = x.item;
    const b = y.item;
    return (
      cmp(a.side, b.side) ||
      cmp(a.addedAt, b.addedAt) ||
      cmp(a.card.scryfallId, b.card.scryfallId) ||
      cmp(a.foil, b.foil) ||
      cmp(a.etched, b.etched) ||
      cmp(a.condition, b.condition) ||
      cmp(a.language, b.language) ||
      cmp(a.quantity, b.quantity)
    );
  });
}

/** Each line's key for edits: what tells it apart (the same line is never there twice; "#2" if it ever is). */
export function lineKeys(t: SyncTradeJ): string[] {
  const seen = new Map<string, number>();
  return t.items.map(({ item: i }) => {
    const k = `${i.side}|${i.addedAt}|${i.card.scryfallId}|${i.foil}|${i.etched}|${i.condition}|${i.language}`;
    const n = (seen.get(k) ?? 0) + 1;
    seen.set(k, n);
    return n === 1 ? k : `${k}#${n}`;
  });
}

const finishOf = (i: Pick<TradeItemJ, "foil" | "etched">): Finish => (i.etched ? "ETCHED" : i.foil ? "FOIL" : "NONFOIL");

/** The app's snapshot: Cardmarket's figures for the printing and finish, or Scryfall's saved price as the trend. */
export function snapshot(card: CardRef, foil: boolean, prices: PriceFile | null): PriceSetJ {
  const set = cardmarketSet(card, foil, prices);
  if (set && best(set, "trend") != null) return Object.fromEntries(PRICE_TYPES.map((t, i) => [t, set[i] ?? null])) as PriceSetJ;
  const out = Object.fromEntries(PRICE_TYPES.map((t) => [t, null])) as PriceSetJ;
  out.trend = (foil ? card.fallbackEurFoil : card.fallbackEur) ?? null;
  return out;
}

function text(v: unknown, what: string): string {
  if (typeof v !== "string") throw new EditError(`The ${what} couldn't be read.`);
  const t = v.trim();
  if (t.length > MAX_TEXT) throw new EditError(`The ${what} can have at most ${MAX_TEXT} characters.`);
  return t;
}

function quantity(q: unknown): number {
  if (typeof q !== "number" || !Number.isInteger(q) || q < 1 || q > MAX_QUANTITY) throw new EditError(`Copies must be a whole number from 1 to ${MAX_QUANTITY}.`);
  return q;
}

function condition(c: unknown): string {
  if (typeof c !== "string" || !CONDITIONS.some(([k]) => k === c)) throw new EditError("Unknown condition.");
  return c;
}

function language(l: unknown): string {
  if (typeof l !== "string" || !LANGUAGES.some(([k]) => k === l)) throw new EditError("Unknown language.");
  return l;
}

function finish(f: unknown): Finish {
  if (f !== "NONFOIL" && f !== "FOIL" && f !== "ETCHED") throw new EditError("Unknown finish.");
  return f;
}

/** The agreed price as the app keeps it: euros, plus the amount as typed in kroner or dollars. */
function setCustom(i: TradeItemJ, custom: Paid, rates: PriceFile["rates"] | null): void {
  if (custom === null) {
    i.customPrice = null;
    i.customTyped = null;
    i.customCurrency = null;
    return;
  }
  const { amount, currency } = custom;
  if (typeof amount !== "number" || !Number.isFinite(amount) || amount < 0 || amount > 1e7) throw new EditError("The agreed price must be 0 or more.");
  if (!CURRENCIES.includes(currency)) throw new EditError("Unknown currency.");
  if (currency === "EUR") {
    i.customPrice = Math.round(amount * 100) / 100;
    i.customTyped = null;
    i.customCurrency = null;
    return;
  }
  const rate = rates?.perEuro[currency] ?? (currency === "DKK" ? 7.46038 : null);
  if (!rate) throw new EditError(`No exchange rate for ${currency} yet; enter the price in euros.`);
  i.customPrice = Math.round((amount / rate) * 100) / 100;
  i.customTyped = amount;
  i.customCurrency = currency;
}

function binderOrNull(file: SyncFile, b: unknown): string | null {
  if (b === null || b === undefined || b === "") return null;
  if (typeof b !== "string" || !binders(file).some((x) => x.uid === b)) throw new EditError("That binder isn't there any more (deleted on a phone?).");
  return b;
}

function find(file: SyncFile, list: SyncTradeJ[], uid: string): SyncTradeJ {
  const t = list.find((x) => x.trade.uid === uid);
  if (!t) throw new EditError("That trade isn't there any more (deleted on a phone?).");
  return t;
}

function unlocked(t: SyncTradeJ): void {
  if (t.trade.applied) throw new EditError("This trade is completed. Undo it first to change its cards.");
}

function lineOf(t: SyncTradeJ, key: string): TradeItemJ {
  const i = lineKeys(t).indexOf(key);
  if (i < 0) throw new EditError("That card isn't in the trade any more (changed on a phone?).");
  return t.items[i].item;
}

export function applyTradeEdit(
  file: SyncFile,
  edit: TradeEdit,
  now: number,
  prices: PriceFile | null,
  makeUid: () => string = newUid,
): TradeEditResult {
  const list = trades(file);
  const touch = (t: SyncTradeJ) => {
    t.trade.updatedAt = bump(t.trade.updatedAt, now);
    sortItems(t);
    setTrades(file, list);
  };
  switch (edit.type) {
    case "new": {
      const t: SyncTradeJ = {
        trade: {
          id: 0,
          createdAt: now,
          partner: edit.partner === undefined ? "" : text(edit.partner, "partner's name"),
          notes: edit.notes === undefined ? "" : text(edit.notes, "notes"),
          applied: false,
          appliedAt: null,
          binderId: 0,
          uid: makeUid(),
          updatedAt: now,
        },
        binder: null,
        items: [],
      };
      list.push(t);
      setTrades(file, list);
      return { uid: t.trade.uid };
    }
    case "update": {
      const t = find(file, list, edit.uid);
      if (edit.partner !== undefined) t.trade.partner = text(edit.partner, "partner's name");
      if (edit.notes !== undefined) t.trade.notes = text(edit.notes, "notes");
      touch(t);
      return { uid: t.trade.uid };
    }
    case "addLine": {
      const t = find(file, list, edit.uid);
      unlocked(t);
      if (edit.side !== "GET" && edit.side !== "GIVE") throw new EditError("Unknown side.");
      const card = checkCard(edit.card);
      const f = resolveFinish(card, finish(edit.finish ?? "NONFOIL"));
      const lang = language(edit.language ?? "EN");
      const cond = condition(edit.condition ?? "NM");
      const qty = quantity(edit.quantity ?? 1);
      // Like the app's addToTrade: the same card, finish and language on the same side gets more copies.
      const same = t.items.find(
        ({ item: i }) => i.side === edit.side && i.card.scryfallId === card.scryfallId && i.foil === (f !== "NONFOIL") && i.etched === (f === "ETCHED") && i.language === lang,
      );
      if (same) same.item.quantity = Math.min(MAX_QUANTITY, same.item.quantity + qty);
      else {
        // A unique time added, so two cards added at once keep their order and keys.
        const used = new Set(t.items.map((x) => x.item.addedAt));
        let at = now;
        while (used.has(at)) at++;
        t.items.push({
          item: {
            id: 0,
            tradeId: 0,
            side: edit.side,
            card,
            foil: f !== "NONFOIL",
            condition: cond,
            language: lang,
            quantity: qty,
            prices: snapshot(card, f !== "NONFOIL", prices),
            customPrice: null,
            appliedDelta: 0,
            addedAt: at,
            etched: f === "ETCHED",
            appliedBinderId: 0,
            customTyped: null,
            customCurrency: null,
          },
          appliedBinder: null,
        });
      }
      touch(t);
      return { uid: t.trade.uid };
    }
    case "updateLine": {
      const t = find(file, list, edit.uid);
      unlocked(t);
      const i = lineOf(t, edit.line);
      if (edit.quantity !== undefined) i.quantity = quantity(edit.quantity);
      if (edit.condition !== undefined) i.condition = condition(edit.condition);
      if (edit.language !== undefined) i.language = language(edit.language);
      if (edit.finish !== undefined) {
        const f = finish(edit.finish);
        if (!finishesOf(i.card).includes(f)) throw new EditError("This printing isn't made in that finish.");
        const foilChanged = i.foil !== (f !== "NONFOIL");
        i.foil = f !== "NONFOIL";
        i.etched = f === "ETCHED";
        // The app's updateTradeItem: a new foil-ness gets that finish's prices.
        if (foilChanged) i.prices = snapshot(i.card, i.foil, prices);
      }
      if (edit.custom !== undefined) setCustom(i, edit.custom, prices?.rates ?? null);
      touch(t);
      return { uid: t.trade.uid };
    }
    case "removeLine": {
      const t = find(file, list, edit.uid);
      unlocked(t);
      const at = lineKeys(t).indexOf(edit.line);
      if (at < 0) throw new EditError("That card isn't in the trade any more (changed on a phone?).");
      t.items.splice(at, 1);
      touch(t);
      return { uid: t.trade.uid };
    }
    case "refreshPrices": {
      const t = find(file, list, edit.uid);
      unlocked(t);
      for (const { item: i } of t.items) i.prices = snapshot(i.card, i.foil, prices);
      touch(t);
      return { uid: t.trade.uid };
    }
    case "apply": {
      const t = find(file, list, edit.uid);
      if (t.trade.applied) return { uid: t.trade.uid, missing: 0 };
      const into = binderOrNull(file, edit.binder);
      let missing = 0;
      for (const line of t.items) {
        const i = line.item;
        if (i.side === "GET") {
          applyCollectionEdit(file, { type: "add", card: i.card, quantity: i.quantity, finish: finishOf(i), condition: i.condition, language: i.language, binder: into }, now, null, makeUid);
          i.appliedDelta = i.quantity;
        } else {
          const r = takeCopies(file, i.card.scryfallId, finishOf(i), i.condition, i.language, i.quantity, null, now);
          missing += i.quantity - r.removed;
          i.appliedDelta = -r.removed;
          line.appliedBinder = r.from;
        }
      }
      t.trade.applied = true;
      t.trade.appliedAt = now;
      t.binder = into;
      touch(t);
      return { uid: t.trade.uid, missing };
    }
    case "undo": {
      const t = find(file, list, edit.uid);
      if (!t.trade.applied) return { uid: t.trade.uid };
      const known = new Set(binders(file).map((b) => b.uid));
      for (const line of t.items) {
        const i = line.item;
        if (i.appliedDelta > 0) takeCopies(file, i.card.scryfallId, finishOf(i), i.condition, i.language, i.appliedDelta, t.binder, now);
        else if (i.appliedDelta < 0) {
          const back = line.appliedBinder && known.has(line.appliedBinder) ? line.appliedBinder : null;
          applyCollectionEdit(file, { type: "add", card: i.card, quantity: -i.appliedDelta, finish: finishOf(i), condition: i.condition, language: i.language, binder: back }, now, null, makeUid);
        }
        i.appliedDelta = 0;
        line.appliedBinder = null;
      }
      t.trade.applied = false;
      t.trade.appliedAt = null;
      touch(t);
      return { uid: t.trade.uid };
    }
    case "remove": {
      const at = list.findIndex((x) => x.trade.uid === edit.uid);
      if (at < 0) return { uid: null }; // Already gone.
      const [gone] = list.splice(at, 1);
      setTrades(file, list);
      addDeletion(file, gone.trade.uid, bump(gone.trade.updatedAt, now));
      return { uid: gone.trade.uid, removed: JSON.parse(JSON.stringify(gone)) as SyncTradeJ };
    }
    case "restore": {
      const t = edit.trade;
      if (!t?.trade?.uid || !/^[0-9A-Za-z_-]{1,64}$/.test(t.trade.uid) || !Array.isArray(t.items)) throw new EditError("That trade couldn't be read.");
      if (list.some((x) => x.trade.uid === t.trade.uid)) return { uid: t.trade.uid };
      // Newer than its deletion, so every phone takes it back.
      const gone = deletions(file).find((d) => d.uid === t.trade.uid);
      const restored: SyncTradeJ = { ...t, trade: { ...t.trade, updatedAt: bump(Math.max(t.trade.updatedAt, gone?.deletedAt ?? 0), now) } };
      file.data.deletions = deletions(file).filter((d) => d.uid !== t.trade.uid) as unknown as Json;
      for (const line of restored.items) line.item.card = checkCard(line.item.card);
      list.push(restored);
      sortItems(restored);
      setTrades(file, list);
      return { uid: t.trade.uid };
    }
  }
}
