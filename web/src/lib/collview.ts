// What the collection pages show, worked out on the server from the sync file and the price files: every stack
// with its price as the app shows it (CollectionRow.unitPrice), the binders in the app's order, and the decks.
import { needsLookup, type CardInfo, type DetailsFile } from "./carddetails";
import type { CkFile } from "./cardkingdom";
import { currency, rate, type Currency } from "./money";
import { priceType, PRICE_TYPE_LABELS, type PriceFile, type PriceType } from "./prices";
import {
  cardKingdomPays,
  cardmarketPrice,
  cardmarketSet,
  conditionNote,
  finishOf,
  isApprox,
  priceAt,
  pricingSetup,
  SOURCE_LABELS,
  sourceUrl,
  unitPrice,
  type Finish,
  type PricingSetup,
  type Source,
} from "./pricing";
import { scanCount } from "./scans";
import { binders as bindersOf, canWrite, collection, prefs, wishlist as wishlistOf, type CardRef, type Json, type SyncFile } from "./syncfile";

export interface CollRow {
  uid: string;
  card: CardRef;
  finish: Finish;
  condition: string;
  language: string;
  quantity: number;
  addedAt: number;
  /** The binder's uid; null for Unsorted. */
  binder: string | null;
  notes: string | null;
  purchasePrice: number | null;
  paidTyped: number | null;
  paidCurrency: string | null;
  signed: boolean;
  altered: boolean;
  /** One copy, in euros, from the app's price source (null: no price). */
  unitEur: number | null;
  /** Cardmarket's price of one copy in the app's price type, in euros (the full CSV export's price column). */
  cmEur: number | null;
  /** The price is Cardmarket's standing in for the chosen source. */
  approx: boolean;
  /** "EX 85%" when lowered for the condition. */
  condNote: string | null;
  /** Cardmarket's trend against its 30-day average, one copy, in euros and percent (the app's PriceChange). */
  changeEur: number | null;
  changePct: number | null;
  /** One copy at every source (null: no price there) and what Card Kingdom pays, in euros (the stats' source comparison). */
  srcEur: { cardmarket: number | null; tcgplayer: number | null; cardkingdom: number | null; ckPays: number | null };
  /** Colours, type… from Scryfall; null until looked up. */
  info: Omit<CardInfo, "u" | "tu" | "at"> | null;
}

export interface CollBinder {
  uid: string;
  name: string;
}

export interface CollDeck {
  name: string;
  owner: string;
  /** Front-face names in lower case, maybeboard left out, with copies. */
  cards: Record<string, number>;
}

export interface CollectionView {
  rows: CollRow[];
  binders: CollBinder[];
  /** The binder bar in the app's order (BinderOrder): "unsorted", "wishlist" and "b:<uid>" keys. */
  bar: string[];
  /** Copies on the wishlist not yet received (the Wishlist chip's number). */
  wishCount: number;
  /** Copies waiting in the scan list (the Scanned chip's number; the chip shows only when there are some). */
  scanCount: number;
  decks: CollDeck[];
  currency: Currency;
  perEuro: number;
  priceType: PriceType;
  priceTypeLabel: string;
  source: Source;
  sourceLabel: string;
  /** "Prices from …" line. */
  pricesNote: string;
  /** The exchange rates are there (TCGplayer's and Card Kingdom's dollars need them). */
  ratesKnown: boolean;
  /** Printings without details yet (the page looks them up, 75 at a time). */
  missing: string[];
  readOnly: boolean;
  format: number;
}

/** One stack's other prices, for its card window. */
export interface CardPrices {
  cardmarket: (number | null)[] | null;
  sources: { source: Source; eur: number | null; url: string | null }[];
  cardKingdomPays: number | null;
  /** One copy of this pile (printing, finish, condition) as the nightly job saved it, oldest first (the chart). */
  history?: { day: string; eur: number }[];
}

/** "Fire // Ice" → "fire" (decks and the stats compare front-face names). */
export function frontName(name: string): string {
  return name.split(" // ")[0].toLowerCase();
}

export const BAR_UNSORTED = "unsorted";
export const BAR_WISHLIST = "wishlist";
/** The scan list's chip (not a place in the binder order: the app has its scans in a tab of their own). */
export const BAR_SCANS = "scans";

/**
 * The app's BinderOrder.arrange: every place in the binder bar, those in [saved] first as arranged, then the rest in
 * the usual order (Unsorted, the wishlist, then the binders by name). The app also puts its trade binder before the
 * other binders; which binder that is doesn't sync yet.
 */
export function barKeys(list: CollBinder[], saved: string | undefined): string[] {
  const usual = [BAR_UNSORTED, BAR_WISHLIST, ...[...list].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())).map((b) => `b:${b.uid}`)];
  const known = new Set(usual);
  const kept = [...new Set((saved ?? "").split(",").map((k) => k.trim()).filter((k) => known.has(k)))];
  return [...kept, ...usual.filter((k) => !kept.includes(k))];
}

/** The app's BinderOrder.arrange for the binders (Unsorted and the wishlist aren't binders here). */
export function orderBinders(list: CollBinder[], saved: string | undefined): CollBinder[] {
  const keys = (saved ?? "").split(",").map((k) => k.trim()).filter(Boolean);
  const byName = [...list].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  const pos = (b: CollBinder) => {
    const i = keys.indexOf(`b:${b.uid}`);
    return i < 0 ? Number.MAX_SAFE_INTEGER : i;
  };
  return byName.map((b, n) => ({ b, n })).sort((x, y) => pos(x.b) - pos(y.b) || x.n - y.n).map((x) => x.b);
}

/** The decks' cards by name, for "in a deck" and the trade binder; retired decks don't count (app 1.83) unless [withRetired]. */
export function decksOf(file: SyncFile, withRetired = false): CollDeck[] {
  const decks = ((file.data.decks as Json[] | undefined) ?? []).filter((d) => withRetired || !(d as { deck?: { retired?: boolean } }).deck?.retired);
  return decks.map((d) => {
    const deck = (d as { deck?: { name?: string; owner?: string } }).deck ?? {};
    const cards: Record<string, number> = {};
    for (const c of ((d as { cards?: Json[] }).cards ?? []) as { card?: { name?: string }; quantity?: number; maybeboard?: boolean }[]) {
      if (c.maybeboard || !c.card?.name) continue;
      const k = frontName(c.card.name);
      cards[k] = (cards[k] ?? 0) + (c.quantity ?? 1);
    }
    return { name: deck.name ?? "", owner: deck.owner ?? "", cards };
  });
}

export function setupFor(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): PricingSetup {
  const p = prefs(file);
  return pricingSetup(p, priceType(p.priceType), prices, details, ck);
}

export function collectionView(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): CollectionView {
  const p = prefs(file);
  const setup = setupFor(file, prices, details, ck);
  const known = new Set(bindersOf(file).map((b) => b.uid));
  const missing = new Set<string>();
  const rows = collection(file)
    .filter((s) => s.item.uid)
    .map((s): CollRow => {
      const it = s.item;
      const finish = finishOf(it.foil, it.etched);
      const cm = cardmarketPrice(it.card, it.foil, setup.type, prices);
      const set = cardmarketSet(it.card, it.foil, prices);
      const trend = set?.[0] ?? null;
      const avg30 = set?.[5] ?? null;
      const change = trend != null && avg30 != null && avg30 > 0 ? trend - avg30 : null;
      const d = details?.cards[it.card.scryfallId];
      if (needsLookup(d)) missing.add(it.card.scryfallId);
      return {
        uid: it.uid!,
        card: it.card,
        finish,
        condition: it.condition,
        language: it.language,
        quantity: it.quantity,
        addedAt: it.addedAt,
        binder: s.binder && known.has(s.binder) ? s.binder : null,
        notes: it.notes ?? null,
        purchasePrice: it.purchasePrice ?? null,
        paidTyped: it.paidTyped ?? null,
        paidCurrency: it.paidCurrency ?? null,
        signed: !!it.signed,
        altered: !!it.altered,
        unitEur: unitPrice(setup, it.card.scryfallId, finish, it.condition, cm),
        cmEur: cm,
        approx: isApprox(setup, it.card.scryfallId, finish, it.condition),
        condNote: conditionNote(setup, it.card.scryfallId, finish, it.condition),
        srcEur: {
          cardmarket: priceAt(setup, "cardmarket", it.card.scryfallId, finish, it.condition, cm),
          tcgplayer: priceAt(setup, "tcgplayer", it.card.scryfallId, finish, it.condition, cm),
          cardkingdom: priceAt(setup, "cardkingdom", it.card.scryfallId, finish, it.condition, cm),
          ckPays: cardKingdomPays(setup, it.card.scryfallId, finish),
        },
        changeEur: change,
        changePct: change != null ? (change / avg30!) * 100 : null,
        info: d ? { c: d.c, i: d.i, t: d.t, v: d.v, r: d.r, d: d.d, g: d.g, l: d.l } : null,
      };
    });
  const cur = currency(p.currency);
  const dates = [
    prices?.createdAt ? `Cardmarket ${prices.createdAt.slice(0, 10)}` : null,
    setup.source === "cardkingdom" && ck ? `Card Kingdom ${ck.listDate.slice(0, 10)}` : null,
    setup.source === "tcgplayer" && details?.madeAt ? `TCGplayer ${new Date(details.madeAt).toISOString().slice(0, 10)}` : null,
  ].filter(Boolean);
  return {
    rows,
    binders: orderBinders(
      bindersOf(file)
        .filter((b) => b.uid)
        .map((b) => ({ uid: b.uid!, name: b.name })),
      p.binderOrder,
    ),
    bar: barKeys(
      bindersOf(file)
        .filter((b) => b.uid)
        .map((b) => ({ uid: b.uid!, name: b.name })),
      p.binderOrder,
    ),
    wishCount: wishlistOf(file)
      .filter((w) => w.receivedAt == null)
      .reduce((n, w) => n + w.quantity, 0),
    scanCount: scanCount(file),
    decks: decksOf(file),
    currency: cur,
    perEuro: rate(cur, prices?.rates ?? null) ?? 1,
    priceType: setup.type,
    priceTypeLabel: PRICE_TYPE_LABELS[setup.type],
    source: setup.source,
    sourceLabel: SOURCE_LABELS[setup.source],
    ratesKnown: !!prices?.rates?.perEuro?.USD,
    pricesNote: dates.length ? `Price lists from ${dates.join(", ")}` : "Prices: Scryfall's saved prices (the daily price file isn't there yet)",
    missing: [...missing],
    readOnly: !canWrite(file),
    format: file.format,
  };
}

/** The prices in one stack's card window: Cardmarket's table and the other sources. */
export function cardPrices(setup: PricingSetup, card: CardRef, finish: Finish, condition: string): CardPrices {
  const foil = finish !== "NONFOIL";
  const cm = cardmarketPrice(card, foil, setup.type, setup.prices);
  const sources: Source[] = ["cardmarket", "tcgplayer", "cardkingdom"];
  return {
    cardmarket: cardmarketSet(card, foil, setup.prices),
    sources: sources.map((s) => ({
      source: s,
      eur: priceAt(setup, s, card.scryfallId, finish, condition, cm),
      url: s === "cardmarket" ? null : sourceUrl(setup, s, card.scryfallId, finish),
    })),
    cardKingdomPays: cardKingdomPays(setup, card.scryfallId, finish),
  };
}
