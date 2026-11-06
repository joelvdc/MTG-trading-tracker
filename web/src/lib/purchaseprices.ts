// Purchase prices for cards already in the collection, from order files (the app's PurchasePrices.kt): each order line
// is matched to the piles it bought, and a pile gets the average paid, weighted by copies. Nothing is looked up online:
// Cardmarket's lines carry the product number the collection stores per printing, CardTrader's the set and number.
import { cleanNumber, tokenNumber, type OrderLine } from "./orders";
import type { PriceFile } from "./prices";
import { bump, collection, type CollectionItem, type SyncFile } from "./syncfile";

export type Change = "NEW" | "AVERAGED" | "REPLACED" | "KEPT";

/** "Sol Ring (V.2)" and "Sol Ring" compare equal; so do "Æther Vial" and "Aether Vial". */
export function normalName(name: string): string {
  const clean = (n: string) => n.replace(/\s*\([^)]*\)\s*$/, "").trim();
  return clean(clean(name))
    .toLowerCase()
    .replace(/æ/g, "ae")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

/** A card's name and each face's: Cardmarket's older files write "A / B" where Scryfall has "A // B". */
export function names(name: string): Set<string> {
  return new Set([name, ...name.split(/ \/\/? /)].map(normalName).filter((n) => n !== ""));
}

const LIST_SETS = new Set(["mb1", "mb2", "fmb1", "plist", "plist2", "plst", "ulst", "cmb1", "cmb2"]);
const BASICS = ["plains", "island", "swamp", "mountain", "forest", "wastes"];

export const isBasicLandName = (name: string) => [...names(name)].some((n) => BASICS.includes(n) || BASICS.includes(n.replace(/^snow covered /, "")));
export const isListSet = (code: string) => LIST_SETS.has(code.toLowerCase());

/** Whether CardTrader's set code [shop] is Scryfall's [own] (OrderFiles.sameSet). */
export function sameSet(shop: string, own: string): boolean {
  const a = shop.toLowerCase();
  const b = own.toLowerCase();
  const sorted = (s: string) => [...s].sort().join("");
  if (a === b || sorted(a) === sorted(b)) return true;
  if (a.length === b.length + 1 && (a.startsWith(b) || a.endsWith(b))) return true;
  if (b.startsWith("p") && a === `pre${b.slice(1)}`) return true;
  return LIST_SETS.has(a) && LIST_SETS.has(b);
}

/** "044" and "44", "546e" and "546", "APC-105" and "105" are the same number. */
export function plainNumber(n: string): string {
  const after = n.slice(n.lastIndexOf("-") + 1);
  return after.replace(/\D/g, "").replace(/^0+/, "");
}

interface Found {
  stacks: CollectionItem[];
  sure: boolean;
  why: string;
}

class Index {
  byProduct = new Map<number, CollectionItem[]>();
  bySetNumber = new Map<string, CollectionItem[]>();
  byName = new Map<string, CollectionItem[]>();
  static key = (set: string, num: string) => `${set.toLowerCase()}|${num.toLowerCase()}`;

  constructor(stacks: CollectionItem[]) {
    const add = <K>(m: Map<K, CollectionItem[]>, k: K, s: CollectionItem) => {
      const l = m.get(k) ?? [];
      l.push(s);
      m.set(k, l);
    };
    for (const s of stacks) {
      if (s.card.cardmarketId != null) add(this.byProduct, s.card.cardmarketId, s);
      if (s.card.cardmarketFoilId != null && s.card.cardmarketFoilId !== s.card.cardmarketId) add(this.byProduct, s.card.cardmarketFoilId, s);
      add(this.bySetNumber, Index.key(s.card.setCode, s.card.collectorNumber), s);
      for (const n of names(s.card.name)) add(this.byName, n, s);
    }
  }

  named(name: string): CollectionItem[] {
    const out = new Map<string, CollectionItem>();
    for (const n of names(name)) for (const s of this.byName.get(n) ?? []) out.set(s.uid!, s);
    return [...out.values()];
  }
}

const finishWord = (foil: boolean) => (foil ? "foil" : "non-foil");

function version(line: OrderLine, printing: CollectionItem[], foil = line.foil): Found {
  const finish = printing.filter((s) => s.foil === foil);
  if (finish.length === 0) return { stacks: printing, sure: false, why: `Bought as ${finishWord(foil)}, in the collection as ${finishWord(!foil)}` };
  const m = finish.filter((s) => s.signed === line.signed && s.altered === line.altered);
  const marks = m.length ? m : finish;
  const language = line.languageKnown ? marks.filter((s) => s.language === line.language) : marks;
  if (language.length === 0) {
    return { stacks: marks, sure: false, why: `Bought in ${line.language}, in the collection in ${[...new Set(marks.map((s) => s.language))].join(", ")}` };
  }
  const cond = language.filter((s) => s.condition === line.condition);
  return { stacks: cond.length ? cond : language, sure: true, why: "" };
}

function otherPrinting(line: OrderLine, index: Index): Found {
  // Basic lands are owned in a dozen printings; the name alone says nothing.
  if (!line.name || isBasicLandName(line.name)) return { stacks: [], sure: false, why: "" };
  const named = index.named(line.name);
  return { stacks: named, sure: false, why: named.length ? "You own this card as another printing" : "" };
}

function find(line: OrderLine, index: Index): Found {
  const product = line.productId;
  if (product !== null) {
    const printing = index.byProduct.get(product) ?? [];
    if (printing.length === 0) return otherPrinting(line, index);
    if (!line.versionKnown) {
      // Cardmarket's monthly file says neither finish nor language: fine while only one version is owned.
      const versions = new Set(printing.map((s) => JSON.stringify([s.foil, s.etched, s.language, s.signed, s.altered])));
      return versions.size === 1
        ? { stacks: printing, sure: true, why: "" }
        : { stacks: printing, sure: false, why: "You own this printing in several versions, and the file doesn't say which was bought" };
    }
    // A product that only exists in foil (Cardmarket lists some foils apart) is foil whatever the flag says.
    const foilOnly = printing.every((s) => s.card.cardmarketFoilId === product && s.card.cardmarketId !== product);
    return version(line, printing, line.foil || foilOnly);
  }
  const set = line.setCode.toLowerCase();
  const token = tokenNumber(line.number);
  const want = names(line.name);
  const exact = (index.bySetNumber.get(token !== null ? Index.key(`t${set}`, token) : Index.key(set, cleanNumber(line.number))) ?? []).filter((s) =>
    [...names(s.card.name)].some((n) => want.has(n)),
  );
  let printing = exact;
  if (printing.length === 0) {
    // CardTrader's own codes for a set's collector, promo or The List cards.
    const related = index.named(line.name).filter((s) => sameSet(set, s.card.setCode));
    const num = plainNumber(line.number);
    const sameNumber = related.filter((s) => num !== "" && plainNumber(s.card.collectorNumber) === num);
    printing = isListSet(set) && sameNumber.length === 0 ? related : sameNumber;
  }
  if (printing.length === 0) return otherPrinting(line, index);
  return version(line, printing);
}

export interface PlanRow {
  uid: string;
  name: string;
  setCode: string;
  number: string;
  foil: boolean;
  condition: string;
  language: string;
  quantity: number;
  bought: number;
  /** What the orders say one copy cost (EUR). */
  paid: number;
  had: number | null;
  newPrice: number;
  change: Change;
}

export interface OpenLine {
  index: number;
  label: string;
  why: string;
  candidates: { uid: string; text: string }[];
}

export interface Plan {
  rows: PlanRow[];
  open: OpenLine[];
  notOwned: string[];
  withoutPrice: number;
}

const cents = (v: number) => Math.round(v * 100) / 100;

const lineLabel = (l: OrderLine) =>
  `${l.quantity > 1 ? `${l.quantity}× ` : ""}${l.name || (l.productId !== null ? `Cardmarket product ${l.productId}` : "Unknown card")}${l.setCode ? ` (${l.setCode.toUpperCase()}${l.number ? ` ${l.number}` : ""})` : ""}${l.foil ? ", foil" : ""}${l.price != null ? `, €${l.price.toFixed(2)}` : ""}`;

const stackText = (s: CollectionItem) =>
  `${s.quantity}× ${s.card.name} (${s.card.setCode.toUpperCase()} #${s.card.collectorNumber}${s.foil ? ", foil" : ""}, ${s.condition}, ${s.language}${s.purchasePrice != null ? `, paid €${s.purchasePrice.toFixed(2)}` : ""})`;

/**
 * What [lines] would do to the collection. [choices] holds, per line number, the piles ticked for an open line. With
 * [overwrite] a pile's price is replaced by the orders'; without, it stays, except that a pile with more copies than were
 * bought gets the weighted average (4 copies at 2.00 and an order for 1 copy at 4.00 make 2.50).
 */
export function plan(file: SyncFile, lines: OrderLine[], overwrite: boolean, choices: Record<number, string[]> = {}): Plan {
  const stacks = collection(file).map((s) => s.item);
  const index = new Index(stacks);
  const perStack = new Map<string, OrderLine[]>();
  const open: OpenLine[] = [];
  const notOwned: string[] = [];
  let withoutPrice = 0;
  lines.forEach((line, i) => {
    if (line.price == null) {
      withoutPrice++;
      return;
    }
    const f = find(line, index);
    let bought: CollectionItem[];
    if (f.sure) bought = f.stacks;
    else if (f.stacks.length === 0) {
      notOwned.push(lineLabel(line));
      bought = [];
    } else {
      open.push({ index: i, label: lineLabel(line), why: f.why, candidates: f.stacks.map((s) => ({ uid: s.uid!, text: stackText(s) })) });
      const ticked = new Set(choices[i] ?? []);
      bought = f.stacks.filter((s) => ticked.has(s.uid!));
    }
    for (const s of bought) {
      const l = perStack.get(s.uid!) ?? [];
      l.push(line);
      perStack.set(s.uid!, l);
    }
  });
  const byUid = new Map(stacks.map((s) => [s.uid!, s]));
  const rows: PlanRow[] = [...perStack.entries()].map(([uid, purchases]) => {
    const item = byUid.get(uid)!;
    const bought = purchases.reduce((n, l) => n + l.quantity, 0);
    const paid = purchases.reduce((n, l) => n + l.quantity * l.price!, 0) / bought;
    const had = item.purchasePrice ?? null;
    let price: number;
    let change: Change;
    if (had === null) [price, change] = [paid, "NEW"];
    else if (overwrite) [price, change] = [paid, "REPLACED"];
    else if (bought < item.quantity) [price, change] = [((item.quantity - bought) * had + bought * paid) / item.quantity, "AVERAGED"];
    else [price, change] = [had, "KEPT"];
    const rounded = cents(price);
    return {
      uid,
      name: item.card.name,
      setCode: item.card.setCode,
      number: item.card.collectorNumber,
      foil: item.foil,
      condition: item.condition,
      language: item.language,
      quantity: item.quantity,
      bought,
      paid: cents(paid),
      had,
      newPrice: rounded,
      change: had !== null && Math.abs(rounded - had) < 0.005 ? "KEPT" : change,
    };
  });
  rows.sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  return { rows, open, notOwned, withoutPrice };
}

/**
 * Sets the planned prices (MtgRepository.setPurchasePrices). A new or replaced price is in euros; an averaged one in a
 * pile priced in kroner or dollars stays in that currency, these copies converted at today's rate. Returns how many changed.
 */
export function applyPlan(file: SyncFile, p: Plan, now: number, rates: PriceFile["rates"] | null): number {
  let changed = 0;
  const items = new Map(collection(file).map((s) => [s.item.uid!, s.item]));
  for (const r of p.rows) {
    if (r.change === "KEPT") continue;
    const it = items.get(r.uid);
    if (!it) continue;
    if (r.change === "AVERAGED" && it.paidCurrency && it.paidTyped != null) {
      const rate = rates?.perEuro[it.paidCurrency] ?? (it.paidCurrency === "DKK" ? 7.46038 : null);
      const rest = it.quantity - r.bought;
      it.paidTyped = rate ? cents((rest * it.paidTyped + r.bought * r.paid * rate) / it.quantity) : null;
      if (it.paidTyped === null) it.paidCurrency = null;
    } else {
      it.paidTyped = null;
      it.paidCurrency = null;
    }
    it.purchasePrice = r.newPrice;
    it.updatedAt = bump(it.updatedAt, now);
    changed++;
  }
  return changed;
}
