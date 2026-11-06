// Orders, following the app: reading CardTrader's and Cardmarket's order files (OrderFiles.kt, CardTraderImport.kt),
// finding their cards on Scryfall, adding them to the collection with what they cost (MtgRepository.importOrder) and
// remembering them in the order history (Orders.kt, recordOrders). An order syncs as one row of data.orders.
import { resolveFinish } from "./cards";
import { applyCollectionEdit } from "./collection";
import { parseCondition, parseLanguage } from "./csvimport";
import type { PriceFile } from "./prices";
import { unitPrice, type PricingSetup } from "./pricing";
import { best } from "./prices";
import { addDeletion, bump, collection, compareUid, newUid, type CardRef, type Json, type SyncFile } from "./syncfile";
import { EditError } from "./wishlist";

export const PRECON = "Precon";

export type OrderFormat = "CARDTRADER" | "CARDMARKET_SHIPMENT" | "CARDMARKET_MONTHLY";
export const FORMAT_LABELS: Record<OrderFormat, string> = {
  CARDTRADER: "CardTrader order",
  CARDMARKET_SHIPMENT: "Cardmarket shipment",
  CARDMARKET_MONTHLY: "Cardmarket purchases",
};
export const shopOf = (f: OrderFormat) => (f === "CARDTRADER" ? "CardTrader" : "Cardmarket");

/** One card line of an order file (OrderLine). */
export interface OrderLine {
  setCode: string;
  setName: string;
  name: string;
  number: string;
  quantity: number;
  /** One copy, in EUR. */
  price: number | null;
  condition: string;
  language: string;
  foil: boolean;
  signed: boolean;
  altered: boolean;
  productId: number | null;
  /** False for Cardmarket's monthly file (no finish, language or condition). */
  versionKnown: boolean;
  languageKnown: boolean;
  playset: boolean;
  date: string | null;
  order: string | null;
}

export interface OrderFile {
  format: OrderFormat;
  lines: OrderLine[];
  /** Other games, sleeves, sealed products left out. */
  otherItems: number;
}

export const NOT_AN_ORDER =
  "This isn't an order file the app knows. It reads CardTrader's order export (Excel) and Cardmarket's “ArticlesFromShipment” and “Purchased Articles” files (CSV).";

const line = (l: Partial<OrderLine>): OrderLine => ({
  setCode: "",
  setName: "",
  name: "",
  number: "",
  quantity: 1,
  price: null,
  condition: "NM",
  language: "EN",
  foil: false,
  signed: false,
  altered: false,
  productId: null,
  versionKnown: true,
  languageKnown: true,
  playset: false,
  date: null,
  order: null,
  ...l,
});

const num = (s: string | undefined) => {
  if (s === undefined) return null;
  const n = Number(s.replace(",", "."));
  return s.trim() !== "" && Number.isFinite(n) ? n : null;
};

/** Rows for the same copy merged, quantities added up. */
function merged(lines: OrderLine[]): OrderLine[] {
  const m = new Map<string, OrderLine>();
  for (const l of lines) {
    const k = JSON.stringify({ ...l, quantity: 0 });
    const had = m.get(k);
    if (had) had.quantity += l.quantity;
    else m.set(k, { ...l });
  }
  return [...m.values()];
}

/** CardTrader's conditions on Cardmarket's scale ("Slightly Played" is Excellent). */
export function cardTraderCondition(raw: string | undefined): string {
  switch (raw?.trim().toLowerCase()) {
    case "mint":
      return "MT";
    case "near mint":
      return "NM";
    case "slightly played":
      return "EX";
    case "moderately played":
      return "LP";
    case "played":
      return "PL";
    case "heavily played":
    case "poor":
    case "damaged":
      return "PO";
    default:
      return parseCondition(raw);
  }
}

const CM_CONDITIONS = ["MT", "NM", "EX", "GD", "LP", "PL", "PO"];
const CM_LANGUAGES: Record<number, string> = { 1: "EN", 2: "FR", 3: "DE", 4: "ES", 5: "IT", 6: "ZHS", 7: "JA", 8: "PT", 9: "RU", 10: "KO", 11: "ZHT" };

function cardTrader(rows: string[][], fileName: string | null): OrderFile {
  const headerAt = rows.findIndex((r) => r.some((c) => c.toLowerCase() === "item name"));
  const header = rows[headerAt].map((h) => h.trim().toLowerCase());
  const col = (...names: string[]) => {
    for (const n of names) {
      const i = header.indexOf(n);
      if (i >= 0) return i;
    }
    return -1;
  };
  const iName = col("item name");
  const iSet = col("set code");
  if (iSet < 0) throw new EditError('This doesn\'t look like a CardTrader order (no "Item Name" and "Set Code" columns).');
  const iSetName = col("set name");
  const iNum = col("collector number");
  const iPrice = col("price in eur cents");
  const iQty = col("quantity");
  const iCond = col("condition");
  const iLang = col("language");
  const iFoil = col("foil/reverse", "foil");
  const iSigned = col("signed");
  const iAltered = col("altered");
  const iGame = col("game");
  const flag = (s: string | undefined) => ["1", "true", "yes", "foil"].includes(s?.trim().toLowerCase() ?? "");
  // "cardtrader_order_20260623hxrahx.xls": the day it was placed, and its code.
  const stamp = fileName ? /cardtrader_order_((20\d{2})(\d{2})(\d{2})\w*)/i.exec(fileName) : null;
  const date = stamp ? `${stamp[2]}-${stamp[3]}-${stamp[4]}` : null;
  const out: OrderLine[] = [];
  for (const r of rows.slice(headerAt + 1)) {
    const v = (i: number) => (i >= 0 && r[i]?.trim() ? r[i].trim() : undefined);
    const game = v(iGame);
    if (game !== undefined && !game.toLowerCase().includes("magic")) continue;
    const name = v(iName);
    if (!name) continue;
    const q = num(v(iQty));
    const cents = num(v(iPrice));
    out.push(
      line({
        setCode: v(iSet) ?? "",
        setName: v(iSetName) ?? "",
        name,
        number: v(iNum) ?? "",
        quantity: q !== null && Math.trunc(q) > 0 ? Math.trunc(q) : 1,
        price: cents === null ? null : cents / 100,
        condition: cardTraderCondition(v(iCond)),
        language: parseLanguage(v(iLang)),
        foil: flag(v(iFoil)),
        signed: flag(v(iSigned)),
        altered: flag(v(iAltered)),
        date,
        order: stamp ? stamp[1] : null,
      }),
    );
  }
  return { format: "CARDTRADER", lines: merged(out), otherItems: 0 };
}

function shipment(h: string[], rows: string[][], fileName: string | null): OrderFile {
  const col = (n: string) => h.indexOf(n);
  const iProduct = col("idproduct");
  const iCount = col("groupcount");
  const iPrice = col("price");
  const iLang = col("idlanguage");
  const iCond = col("condition");
  const iFoil = col("isfoil");
  const iSigned = col("issigned");
  const iAltered = col("isaltered");
  const iPlayset = col("isplayset");
  const order = fileName ? (/Shipment\D*(\d{4,})/i.exec(fileName)?.[1] ?? null) : null;
  const out: OrderLine[] = [];
  for (const r of rows) {
    const v = (i: number) => (i >= 0 && r[i]?.trim() ? r[i].trim() : undefined);
    const flag = (i: number) => {
      const s = v(i);
      return s !== undefined && s !== "0" && s.toLowerCase() !== "false";
    };
    const product = Number.parseInt(v(iProduct) ?? "", 10);
    if (!Number.isFinite(product)) continue;
    const playset = flag(iPlayset);
    const g = num(v(iCount));
    const groups = g !== null && Math.trunc(g) > 0 ? Math.trunc(g) : 1;
    const language = CM_LANGUAGES[Number.parseInt(v(iLang) ?? "", 10)] ?? null;
    const price = num(v(iPrice));
    const cond = Number.parseInt(v(iCond) ?? "", 10);
    out.push(
      line({
        quantity: groups * (playset ? 4 : 1),
        // A playset is one article: its price covers the four cards.
        price: price === null ? null : playset ? price / 4 : price,
        condition: CM_CONDITIONS[cond - 1] ?? "NM",
        language: language ?? "EN",
        foil: flag(iFoil),
        signed: flag(iSigned),
        altered: flag(iAltered),
        productId: product,
        languageKnown: language !== null,
        playset,
        order,
      }),
    );
  }
  return { format: "CARDMARKET_SHIPMENT", lines: merged(out), otherItems: 0 };
}

function monthly(h: string[], rows: string[][]): OrderFile {
  const col = (n: string) => h.indexOf(n);
  const iProduct = col("product id");
  const iName = col("article");
  const iSet = col("expansion");
  const iCategory = col("category");
  const iAmount = col("amount");
  const iValue = col("article value");
  const iOrder = col("shipment nr.");
  const iDate = col("date of purchase");
  let other = 0;
  const out: OrderLine[] = [];
  for (const r of rows) {
    const v = (i: number) => (i >= 0 && r[i]?.trim() ? r[i].trim() : undefined);
    const product = Number.parseInt(v(iProduct) ?? "", 10);
    if (!Number.isFinite(product)) continue;
    const cat = v(iCategory);
    if (cat !== undefined && cat.toLowerCase() !== "magic single") {
      other++;
      continue;
    }
    const a = num(v(iAmount));
    out.push(
      line({
        setName: v(iSet) ?? "",
        name: v(iName) ?? "",
        quantity: a !== null && Math.trunc(a) > 0 ? Math.trunc(a) : 1,
        price: num(v(iValue)),
        productId: product,
        versionKnown: false,
        date: v(iDate)?.slice(0, 10) ?? null,
        order: v(iOrder) ?? null,
      }),
    );
  }
  return { format: "CARDMARKET_MONTHLY", lines: merged(out), otherItems: other };
}

/** Recognises the file by its column names (OrderFiles.parse). */
export function parseOrderRows(rows: string[][], fileName: string | null = null): OrderFile {
  for (let at = 0; at < rows.length; at++) {
    const h = rows[at].map((c) => c.trim().toLowerCase());
    if (h.includes("idproduct")) return shipment(h, rows.slice(at + 1), fileName);
    if (h.includes("product id") && h.includes("article value")) return monthly(h, rows.slice(at + 1));
    if (h.includes("item name")) return cardTrader(rows, fileName);
  }
  throw new EditError(NOT_AN_ORDER);
}

// ---- Finding the cards ----

/** "T 04/02" (token 4 of 2) → "4". */
export function tokenNumber(n: string): string | null {
  return /^T\s*0*(\d+[a-z]?)\s*(\/.*)?$/i.exec(n.trim())?.[1] ?? null;
}
/** "044" → "44". */
export const cleanNumber = (n: string) => n.trim().replace(/^0+(?=\d)/, "");
/** "Qarsi Revenant (Borderless)" → "Qarsi Revenant". */
export const cleanName = (n: string) => n.replace(/\s*\([^)]*\)\s*$/, "").trim();

export function firstLookup(l: OrderLine): [string, string] | null {
  const set = l.setCode.toLowerCase();
  if (!set) return null;
  const t = tokenNumber(l.number);
  if (t) return [`t${set}`, t];
  const n = cleanNumber(l.number);
  return n ? [set, n] : null;
}

/** CardTrader's sub-set codes Scryfall files under the main set: "CTDM" → "tdm". */
export const alternativeSet = (code: string) => (code.length === 4 && code.toLowerCase().startsWith("c") ? code.toLowerCase().slice(1) : null);

export interface Found {
  card: CardRef;
  typeLine: string;
  layout: string;
}

export interface Candidate {
  index: number;
  line: OrderLine;
  card: CardRef | null;
  token: boolean;
  basicLand: boolean;
  /** Matched by name only: the printing may not be the one bought. */
  guessed: boolean;
}

export const isToken = (c: Found) => ["token", "double_faced_token", "emblem"].includes(c.layout) || c.typeLine.startsWith("Token");
export const isBasicLand = (c: Found) => c.typeLine.includes("Basic") && c.typeLine.includes("Land");

/** Where the cards are looked up; Scryfall on the server, fakes in the tests. */
export interface Lookup {
  collection(ids: object[]): Promise<Found[]>;
  byCardmarketId(id: number): Promise<Found | null>;
  fuzzy(name: string): Promise<Found | null>;
  search(q: string): Promise<Found[]>;
}

const MAX_SLOW_LOOKUPS = 150;

/** Finds every line's printing (OrderFiles.match / CardTraderOrder.match). */
export async function matchOrder(file: OrderFile, look: Lookup): Promise<Candidate[]> {
  const found = new Map<number, [Found, boolean]>();
  let slow = 0;
  if (file.format === "CARDTRADER") {
    const key = (s: string, n: string) => `${s.toLowerCase()}|${n.toLowerCase()}`;
    const batch = async (lookups: Map<number, [string, string]>) => {
      if (lookups.size === 0) return;
      const ids = [...new Map([...lookups.values()].map(([s, n]) => [key(s, n), { set: s, collector_number: n }])).values()];
      const byKey = new Map((await look.collection(ids)).map((c) => [key(c.card.setCode, c.card.collectorNumber), c]));
      for (const [i, [s, n]] of lookups) {
        const c = byKey.get(key(s, n));
        if (c) found.set(i, [c, false]);
      }
    };
    await batch(new Map(file.lines.flatMap((l, i) => (firstLookup(l) ? [[i, firstLookup(l)!] as [number, [string, string]]] : []))));
    await batch(
      new Map(
        file.lines.flatMap((l, i) => {
          const alt = alternativeSet(l.setCode);
          return found.has(i) || !alt ? [] : [[i, [alt, cleanNumber(l.number)]] as [number, [string, string]]];
        }),
      ),
    );
    for (const [i, l] of file.lines.entries()) {
      if (found.has(i) || slow++ >= MAX_SLOW_LOOKUPS) continue;
      const name = cleanName(l.name).split(" // ")[0];
      const set = l.setCode.toLowerCase();
      const sets = [set, alternativeSet(l.setCode), `t${set}`].filter((x): x is string => !!x && x !== "t");
      const inSet = await look.search(`!"${name.replace(/"/g, "")}" (${sets.map((s) => `e:${s}`).join(" or ")}) include:extras unique:prints game:paper`).catch(() => []);
      const n = tokenNumber(l.number) ?? cleanNumber(l.number);
      const exact = inSet.find((c) => n !== "" && (c.card.collectorNumber === n || c.card.collectorNumber.endsWith(`-${n}`)));
      const pick = exact ?? inSet[0];
      if (pick) found.set(i, [pick, !exact]);
      else {
        const f = await look.fuzzy(name).catch(() => null);
        if (f) found.set(i, [f, true]);
      }
    }
  } else {
    const ids = [...new Set(file.lines.map((l) => l.productId).filter((x): x is number => x !== null))];
    const byId = new Map<number, [Found, boolean]>();
    for (const id of ids) {
      if (slow++ >= MAX_SLOW_LOOKUPS * 2) break;
      const exact = await look.byCardmarketId(id).catch(() => null);
      if (exact) byId.set(id, [exact, false]);
      else {
        const name = file.lines.find((l) => l.productId === id)?.name ?? "";
        const clean = cleanName(name).split(" // ")[0].split(" / ")[0];
        if (clean) {
          const f = await look.fuzzy(clean).catch(() => null);
          if (f) byId.set(id, [f, true]);
        }
      }
    }
    file.lines.forEach((l, i) => {
      const hit = l.productId !== null ? byId.get(l.productId) : undefined;
      if (hit) found.set(i, hit);
    });
  }
  return file.lines.map((l, i) => {
    const hit = found.get(i);
    const c = hit?.[0];
    const named =
      c && !hit[1] && file.format !== "CARDTRADER"
        ? { ...l, name: l.name || c.card.name, setCode: c.card.setCode.toUpperCase(), setName: l.setName || c.card.setName, number: c.card.collectorNumber }
        : l;
    return {
      index: i,
      line: named,
      card: c?.card ?? null,
      token: c ? isToken(c) : tokenNumber(l.number) !== null,
      basicLand: c ? isBasicLand(c) : false,
      guessed: hit?.[1] ?? false,
    };
  });
}

// ---- The order history ----

/** One card line of a remembered order (OrderedCard); written without its default values, like the app. */
export interface OrderedCard {
  name: string;
  setCode?: string;
  number?: string;
  quantity?: number;
  price?: number | null;
  foil?: boolean;
  condition?: string | null;
  language?: string | null;
  scryfallId?: string | null;
  cardmarketId?: number | null;
}

/** An order as it syncs (PurchaseOrder). */
export interface PurchaseOrder {
  id: number;
  shop: string;
  number: string | null;
  title: string;
  /** yyyy-MM-dd */
  date: string;
  /** What the cards cost together, in EUR. */
  paid: number;
  cards: number;
  /** The cards, as JSON. */
  lines: string;
  addedAt: number;
  uid: string | null;
  updatedAt: number;
}

const cents = (v: number) => Math.round(v * 100) / 100;

export function orderLines(o: PurchaseOrder): OrderedCard[] {
  try {
    const v = JSON.parse(o.lines);
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
}

function compact(c: OrderedCard): OrderedCard {
  const out: OrderedCard = { name: c.name };
  if (c.setCode) out.setCode = c.setCode;
  if (c.number) out.number = c.number;
  if (c.quantity !== undefined && c.quantity !== 1) out.quantity = c.quantity;
  if (c.price != null) out.price = c.price;
  if (c.foil) out.foil = true;
  if (c.condition != null) out.condition = c.condition;
  if (c.language != null) out.language = c.language;
  if (c.scryfallId != null) out.scryfallId = c.scryfallId;
  if (c.cardmarketId != null) out.cardmarketId = c.cardmarketId;
  return out;
}

/** Orders.of: the totals worked out from the cards. */
export function makeOrder(shop: string, number: string | null, title: string, date: string, cards: OrderedCard[], now: number): PurchaseOrder {
  const list = cards.map(compact);
  return {
    id: 0,
    shop,
    number,
    title,
    date,
    paid: cents(list.reduce((n, c) => n + (c.price ?? 0) * (c.quantity ?? 1), 0)),
    cards: list.reduce((n, c) => n + (c.quantity ?? 1), 0),
    lines: JSON.stringify(list),
    addedAt: now,
    uid: null,
    updatedAt: now,
  };
}

/** Orders.card: the product to price a line by. */
export function orderedCard(l: OrderLine, card: CardRef | null): OrderedCard {
  return {
    name: card?.name ?? (l.name || (l.productId !== null ? `Cardmarket product ${l.productId}` : "Unknown card")),
    setCode: card?.setCode ?? l.setCode.toLowerCase(),
    number: card?.collectorNumber ?? l.number,
    quantity: l.quantity,
    price: l.price,
    foil: l.foil,
    condition: l.versionKnown ? l.condition : null,
    language: l.versionKnown && l.languageKnown ? l.language : null,
    scryfallId: card?.scryfallId ?? null,
    cardmarketId: l.productId ?? (card ? ((l.foil ? card.cardmarketFoilId : null) ?? card.cardmarketId) : null),
  };
}

/** The orders in a file: one, or for Cardmarket's monthly file one per shipment (Orders.fromFile). */
export function ordersFromFile(file: OrderFile, date: string, cardOf: (index: number) => CardRef | null, now: number): PurchaseOrder[] {
  const groups = new Map<string | null, number[]>();
  file.lines.forEach((l, i) => {
    const g = groups.get(l.order) ?? [];
    g.push(i);
    groups.set(l.order, g);
  });
  return [...groups.entries()].map(([number, idx]) => {
    const first = file.lines[idx[0]];
    const title = (file.format === "CARDTRADER" ? "CardTrader order" : "Cardmarket shipment") + (number ? ` ${number}` : "");
    return makeOrder(shopOf(file.format), number, title, first.date ?? date, idx.map((i) => orderedCard(file.lines[i], cardOf(i))), now);
  });
}

export function ordersOf(file: SyncFile): PurchaseOrder[] {
  return ((file.data.orders as Json[] | undefined) ?? []) as unknown as PurchaseOrder[];
}

function setOrders(file: SyncFile, list: PurchaseOrder[]): void {
  list.sort((a, b) => compareUid(a.uid, b.uid));
  file.data.orders = list as unknown as Json;
}

/** Whether [b] is [a] read again: the shop's number, or without one the same day, cards and amount (Orders.same). */
function same(a: PurchaseOrder, b: PurchaseOrder): boolean {
  if (a.shop !== b.shop) return false;
  if (a.number !== null || b.number !== null) return a.number === b.number;
  return a.date === b.date && a.cards === b.cards && a.paid === b.paid;
}

/** Remembers [orders]; one read before is replaced, keeping its sync identity. Returns how many are new. */
export function recordOrders(file: SyncFile, orders: PurchaseOrder[], now: number, makeUid: () => string = newUid): number {
  const list = ordersOf(file);
  let added = 0;
  for (const o of orders) {
    if (o.cards <= 0) continue;
    const old = list.find((x) => same(x, o));
    if (!old) {
      list.push({ ...o, uid: makeUid(), addedAt: now, updatedAt: now });
      added++;
    } else if (old.title !== o.title || old.date !== o.date || old.paid !== o.paid || old.cards !== o.cards || old.lines !== o.lines) {
      Object.assign(old, { title: o.title, date: o.date, paid: o.paid, cards: o.cards, lines: o.lines, updatedAt: bump(old.updatedAt, now) });
    }
  }
  setOrders(file, list);
  return added;
}

export type OrderEdit = { type: "delete"; uid: string } | { type: "date"; uid: string; date: string };

export function applyOrderEdit(file: SyncFile, edit: OrderEdit, now: number): void {
  const list = ordersOf(file);
  const o = list.find((x) => x.uid === edit.uid);
  if (!o) throw new EditError("That order isn't there any more (changed on a phone?).");
  if (edit.type === "delete") {
    setOrders(
      file,
      list.filter((x) => x !== o),
    );
    addDeletion(file, o.uid!, bump(o.updatedAt, now));
    return;
  }
  if (edit.type === "date") {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(edit.date) || Number.isNaN(Date.parse(edit.date))) throw new EditError("A date looks like 2026-11-06.");
    o.date = edit.date;
    o.updatedAt = bump(o.updatedAt, now);
    setOrders(file, list);
    return;
  }
  throw new EditError("Unknown change.");
}

// ---- Adding bought cards ----

/**
 * Adds copies bought for [price] each (EUR) to the collection; a pile they join gets the average purchase price
 * (MtgRepository.addBought with PurchasePrices.joinedTyped: a pile priced in kroner or dollars stays in its currency).
 */
export function addBought(
  file: SyncFile,
  b: { card: CardRef; foil: boolean; condition: string; language: string; quantity: number; binder: string | null; signed?: boolean; altered?: boolean; price: number | null; typed?: { amount: number; currency: string } | null },
  now: number,
  rates: PriceFile["rates"] | null,
  makeUid: () => string = newUid,
): string | null {
  const before = new Map(collection(file).map((s) => [s.item.uid, s.item.quantity]));
  const { uid } = applyCollectionEdit(
    file,
    {
      type: "add",
      card: b.card,
      quantity: b.quantity,
      finish: resolveFinish(b.card, b.foil ? "FOIL" : "NONFOIL"),
      condition: b.condition,
      language: b.language,
      binder: b.binder,
      signed: b.signed === true,
      altered: b.altered === true,
    },
    now,
    rates,
    makeUid,
  );
  if (b.price === null) return uid;
  const it = collection(file).find((s) => s.item.uid === uid)?.item;
  if (!it) return uid;
  const had = before.get(uid) ?? 0;
  const paid = cents(b.price);
  const typed = b.typed && b.typed.currency !== "EUR" ? b.typed : null;
  if (had <= 0 || it.purchasePrice == null) {
    it.purchasePrice = paid;
    it.paidTyped = typed ? cents(typed.amount) : null;
    it.paidCurrency = typed ? typed.currency : null;
    return uid;
  }
  const avg = (x: number, y: number) => cents((had * x + b.quantity * y) / (had + b.quantity));
  const eur = avg(it.purchasePrice, paid);
  if (it.paidCurrency && it.paidTyped != null && typed && typed.currency === it.paidCurrency) {
    it.paidTyped = avg(it.paidTyped, typed.amount);
  } else if (it.paidCurrency && it.paidTyped != null) {
    // The pile's price was typed in kroner or dollars: it stays in that currency, these copies converted at today's rate.
    const rate = rates?.perEuro[it.paidCurrency] ?? (it.paidCurrency === "DKK" ? 7.46038 : null);
    if (rate) it.paidTyped = avg(it.paidTyped, paid * rate);
    else {
      it.paidTyped = null;
      it.paidCurrency = null;
    }
  }
  it.purchasePrice = eur;
  return uid;
}

/** Adds the chosen lines of an order (MtgRepository.importOrder); returns how many copies were added. */
export function importOrderLines(
  file: SyncFile,
  picks: { card: CardRef; line: OrderLine }[],
  binder: string | null,
  now: number,
  rates: PriceFile["rates"] | null,
  makeUid: () => string = newUid,
): number {
  let added = 0;
  for (const { card, line: l } of picks) {
    addBought(file, { card, foil: l.foil, condition: l.condition, language: l.language, quantity: l.quantity, binder, signed: l.signed, altered: l.altered, price: l.price }, now, rates, makeUid);
    added += l.quantity;
  }
  return added;
}

// ---- What orders are worth now ----

export interface OrderWorth {
  value: number;
  paid: number;
  priced: number;
  unpriced: number;
}

/** Orders.worth: copies without a price today are left out of both sides. */
export function worthOf(cards: OrderedCard[], p: PricingSetup): OrderWorth {
  const w: OrderWorth = { value: 0, paid: 0, priced: 0, unpriced: 0 };
  for (const c of cards) {
    const q = c.quantity ?? 1;
    const foil = c.foil === true;
    const row = c.cardmarketId != null ? p.prices?.products[String(c.cardmarketId)] : undefined;
    const set = row ? (foil ? row.slice(6, 12) : row.slice(0, 6)) : null;
    const cm = set ? best(set, p.type) : null;
    const cond = c.condition ?? "NM";
    const unit = c.scryfallId ? unitPrice(p, c.scryfallId, foil ? "FOIL" : "NONFOIL", cond, cm) : cm == null ? null : cm * ((p.conditionPct?.[cond] ?? 100) / 100);
    if (unit == null) w.unpriced += q;
    else {
      w.value += unit * q;
      w.paid += (c.price ?? 0) * q;
      w.priced += q;
    }
  }
  return w;
}


// ---- The orders page ----

export interface OrderRowLine {
  name: string;
  setCode: string;
  number: string;
  quantity: number;
  price: number | null;
  foil: boolean;
  condition: string | null;
  language: string | null;
  scryfallId: string | null;
  /** One copy now, in EUR; null when unknown. */
  now: number | null;
}

export interface OrderRow {
  uid: string;
  shop: string;
  number: string | null;
  title: string;
  date: string;
  paid: number;
  cards: number;
  worth: OrderWorth;
  lines: OrderRowLine[];
}

export interface OrdersView {
  orders: OrderRow[];
  currency: string;
  perEuro: number;
  priceTypeLabel: string;
  readOnly: boolean;
}

/** Newest first, like the app's order history. */
export function orderRows(file: SyncFile, p: PricingSetup): OrderRow[] {
  return ordersOf(file)
    .filter((o) => o.uid)
    .slice()
    .sort((a, b) => b.date.localeCompare(a.date) || b.addedAt - a.addedAt)
    .map((o) => {
      const cards = orderLines(o);
      return {
        uid: o.uid!,
        shop: o.shop,
        number: o.number,
        title: o.title,
        date: o.date,
        paid: o.paid,
        cards: o.cards,
        worth: worthOf(cards, p),
        lines: cards.map((c) => {
          const w = worthOf([{ ...c, quantity: 1 }], p);
          return {
            name: c.name,
            setCode: c.setCode ?? "",
            number: c.number ?? "",
            quantity: c.quantity ?? 1,
            price: c.price ?? null,
            foil: c.foil === true,
            condition: c.condition ?? null,
            language: c.language ?? null,
            scryfallId: c.scryfallId ?? null,
            now: w.priced > 0 ? w.value : null,
          };
        }),
      };
    });
}
