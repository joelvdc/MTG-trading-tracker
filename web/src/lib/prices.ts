// Prices: once a day the website makes one compact file from Cardmarket's public price guide and the European
// Central Bank's exchange rates (as the app's PriceGuideRepository and Currency.kt read them), kept in Vercel Blob.
import type { CardRef } from "./syncfile";

export const CARDMARKET_URL = "https://downloads.s3.cardmarket.com/productCatalog/priceGuide/price_guide_1.json";
export const ECB_URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";
export const PRICE_BLOB = "prices/latest.json.gz";

/** The app's PriceType keys, in the order they're stored per finish. */
export const PRICE_TYPES = ["trend", "avg", "low", "avg1", "avg7", "avg30"] as const;
export type PriceType = (typeof PRICE_TYPES)[number];

export interface PriceFile {
  /** Cardmarket's own time stamp for the guide. */
  createdAt: string;
  /** When the website made this file (ms). */
  madeAt: number;
  /** ECB reference rates: how much of each currency one euro buys, and their date. */
  rates: { date: string; perEuro: Record<string, number> };
  /** Cardmarket product id → [trend, avg, low, avg1, avg7, avg30, then the same for foil]; null where missing. */
  products: Record<string, (number | null)[]>;
}

type Guide = { createdAt?: string; priceGuides?: Record<string, number | null | undefined>[] };

export function compactGuide(guide: Guide): Pick<PriceFile, "createdAt" | "products"> {
  const products: PriceFile["products"] = {};
  for (const g of guide.priceGuides ?? []) {
    const id = g.idProduct;
    if (typeof id !== "number") continue;
    const row = [...PRICE_TYPES.map((t) => g[t] ?? null), ...PRICE_TYPES.map((t) => g[`${t}-foil`] ?? null)];
    if (row.some((v) => v !== null)) products[id] = row.map((v) => (typeof v === "number" ? v : null));
  }
  return { createdAt: guide.createdAt ?? "", products };
}

/** The ECB's eurofxref-daily.xml (the app's ExchangeRates.parseEcb). */
export function parseEcb(xml: string): PriceFile["rates"] | null {
  const date = /time=['"](\d{4}-\d{2}-\d{2})['"]/.exec(xml)?.[1];
  if (!date) return null;
  const perEuro: Record<string, number> = {};
  for (const m of xml.matchAll(/currency=['"]([A-Z]{3})['"]\s+rate=['"]([0-9.]+)['"]/g)) {
    const v = Number(m[2]);
    if (v > 0) perEuro[m[1]] = v;
  }
  return Object.keys(perEuro).length ? { date, perEuro } : null;
}

/** The krone is pegged to the euro: good enough until the first rates arrive (the app's FALLBACK). */
export const FALLBACK_RATES: PriceFile["rates"] = { date: "", perEuro: { DKK: 7.46038 } };

/** The app's PriceSet.best: the chosen figure, else the most stable one there is. */
export function best(set: (number | null)[], type: PriceType): number | null {
  const at = (t: PriceType) => set[PRICE_TYPES.indexOf(t)] ?? null;
  return at(type) ?? at("trend") ?? at("avg") ?? at("avg30") ?? at("avg7") ?? at("avg1") ?? at("low");
}

export interface CardPrice {
  /** In euros; null when neither Cardmarket nor Scryfall has one. */
  eur: number | null;
  /** Cardmarket's trend against its 30-day average, in percent. */
  trendPct: number | null;
}

/** One copy's price, as the app works it out for a wishlist row (Cardmarket's guide, else Scryfall's EUR). */
export function priceOf(card: CardRef, foil: boolean, type: PriceType, prices: PriceFile | null): CardPrice {
  const id = foil ? (card.cardmarketFoilId ?? card.cardmarketId) : card.cardmarketId;
  const row = id != null ? prices?.products[String(id)] : undefined;
  const set = row ? (foil ? row.slice(6, 12) : row.slice(0, 6)) : null;
  const fromGuide = set ? best(set, type) : null;
  const trend = set?.[0] ?? null;
  const avg30 = set?.[5] ?? null;
  return {
    eur: fromGuide ?? (foil ? card.fallbackEurFoil : card.fallbackEur) ?? null,
    trendPct: trend != null && avg30 != null && avg30 > 0 ? ((trend - avg30) / avg30) * 100 : null,
  };
}

export function priceType(key: string | undefined): PriceType {
  return (PRICE_TYPES as readonly string[]).includes(key ?? "") ? (key as PriceType) : "trend";
}

export const PRICE_TYPE_LABELS: Record<PriceType, string> = {
  trend: "Trend",
  avg: "Average",
  low: "Low",
  avg1: "1-day avg",
  avg7: "7-day avg",
  avg30: "30-day avg",
};
