// One copy's price as the app works it out (PriceSources.kt Pricing): from the price source chosen in the app's
// Settings (Cardmarket, TCGplayer or Card Kingdom), else Cardmarket's (shown with "≈"), lowered for the copy's
// condition when the app's "condition-adjusted values" setting is on.
import type { CardInfo, DetailsFile } from "./carddetails";
import type { CkFile } from "./cardkingdom";
import { PRICE_TYPES, best, type PriceFile, type PriceType } from "./prices";
import type { CardRef } from "./syncfile";

export type Finish = "NONFOIL" | "FOIL" | "ETCHED";
export type Source = "cardmarket" | "tcgplayer" | "cardkingdom";

export const SOURCE_LABELS: Record<Source, string> = { cardmarket: "Cardmarket", tcgplayer: "TCGplayer", cardkingdom: "Card Kingdom" };

export function finishOf(foil: boolean, etched: boolean): Finish {
  return etched ? "ETCHED" : foil ? "FOIL" : "NONFOIL";
}

export function source(key: string | undefined): Source {
  return key === "tcgplayer" || key === "cardkingdom" ? key : "cardmarket";
}

/** The app's Settings.DEFAULT_CONDITION_PCTS. */
export const DEFAULT_CONDITION_PCTS: Record<string, number> = { MT: 100, NM: 100, EX: 85, GD: 70, LP: 60, PL: 45, PO: 30 };

/** The app's decodePcts ("EX=85,GD=70"; conditions it doesn't name keep their default). */
export function decodePcts(s: string | undefined): Record<string, number> {
  const out = { ...DEFAULT_CONDITION_PCTS };
  for (const part of (s ?? "").split(",")) {
    const kv = part.split("=");
    if (kv.length !== 2) continue;
    const k = kv[0].trim().toUpperCase();
    const v = Number.parseInt(kv[1].trim(), 10);
    if (k in DEFAULT_CONDITION_PCTS && Number.isFinite(v)) out[k] = Math.min(100, Math.max(0, v));
  }
  return out;
}

/** Card Kingdom's grade for the app's (by way of the TCGplayer scale Archidekt uses: Pricing.ckCondition). */
export function ckCondition(condition: string): "nm" | "ex" | "vg" | "g" {
  const arch: Record<string, string> = { MT: "NM", NM: "NM", EX: "LP", LP: "LP", GD: "MP", PL: "HP", PO: "D" };
  const a = arch[condition.toUpperCase()] ?? "NM";
  return a === "NM" ? "nm" : a === "LP" ? "ex" : a === "MP" ? "vg" : "g";
}

export interface PricingSetup {
  source: Source;
  type: PriceType;
  /** Percent per condition while the app's setting is on, else null. */
  conditionPct: Record<string, number> | null;
  /** How many US dollars one euro buys, or null. */
  usdPerEuro: number | null;
  prices: PriceFile | null;
  details: DetailsFile | null;
  ck: CkFile | null;
}

/** The setup from the app's synced settings and the website's price files. */
export function pricingSetup(
  prefs: Record<string, string>,
  type: PriceType,
  prices: PriceFile | null,
  details: DetailsFile | null,
  ck: CkFile | null,
): PricingSetup {
  return {
    source: source(prefs.priceSource),
    type,
    conditionPct: prefs.conditionAdjust === "true" ? decodePcts(prefs.conditionPcts) : null,
    usdPerEuro: prices?.rates.perEuro.USD ?? null,
    prices,
    details,
    ck,
  };
}

/** Cardmarket's six prices for one finish of a printing (trend, avg, low, avg1, avg7, avg30), or null. */
export function cardmarketSet(card: CardRef, foil: boolean, prices: PriceFile | null): (number | null)[] | null {
  const id = foil ? (card.cardmarketFoilId ?? card.cardmarketId) : card.cardmarketId;
  const row = id != null ? prices?.products[String(id)] : undefined;
  return row ? (foil ? row.slice(6, 12) : row.slice(0, 6)) : null;
}

/** Cardmarket's price of one copy in [type] (the app's CollectionRow.cardmarketPrice). */
export function cardmarketPrice(card: CardRef, foil: boolean, type: PriceType, prices: PriceFile | null): number | null {
  const set = cardmarketSet(card, foil, prices);
  return (set ? best(set, type) : null) ?? (foil ? card.fallbackEurFoil : card.fallbackEur) ?? null;
}

function factor(p: PricingSetup, condition: string): number {
  const pct = p.conditionPct?.[condition];
  return pct == null ? 1 : Math.min(100, Math.max(0, pct)) / 100;
}

function toEur(p: PricingSetup, usd: number | null | undefined): number | null {
  if (usd == null || usd <= 0 || !p.usdPerEuro) return null;
  return usd / p.usdPerEuro;
}

function info(p: PricingSetup, id: string): CardInfo | undefined {
  return p.details?.cards[id];
}

function ckRow(p: PricingSetup, id: string, finish: Finish) {
  if (finish === "ETCHED") return undefined;
  return p.ck?.rows[`${id}|${finish === "FOIL" ? "F" : "N"}`];
}

/** One copy's price at [s], in euros; null when that source has none. [cm] is Cardmarket's price for the copy. */
export function priceAt(p: PricingSetup, s: Source, id: string, finish: Finish, condition: string, cm: number | null): number | null {
  if (s === "cardmarket") return cm == null ? null : cm * factor(p, condition);
  if (s === "tcgplayer") {
    const u = info(p, id)?.u;
    const usd = u ? u[finish === "NONFOIL" ? 0 : finish === "FOIL" ? 1 : 2] : null;
    const eur = toEur(p, usd);
    return eur == null ? null : eur * factor(p, condition);
  }
  const r = ckRow(p, id, finish);
  if (!r) return null;
  const byGrade = { nm: r[2], ex: r[3], vg: r[4], g: r[5] }[ckCondition(condition)];
  return toEur(p, byGrade ?? r[0]);
}

/** The price of one copy from the chosen source, or Cardmarket's when that source has none (Pricing.unit). */
export function unitPrice(p: PricingSetup, id: string, finish: Finish, condition: string, cm: number | null): number | null {
  return priceAt(p, p.source, id, finish, condition, cm) ?? (cm == null ? null : cm * factor(p, condition));
}

/** True when [unitPrice] fell back to Cardmarket's price (shown with "≈"). */
export function isApprox(p: PricingSetup, id: string, finish: Finish, condition: string): boolean {
  return p.source !== "cardmarket" && priceAt(p, p.source, id, finish, condition, null) == null;
}

/** "EX 85%" when the price was lowered for the copy's condition (Pricing.adjustedPct); else null. */
export function conditionNote(p: PricingSetup, id: string, finish: Finish, condition: string): string | null {
  const pct = p.conditionPct?.[condition];
  if (pct == null || pct === 100) return null;
  if (p.source === "cardkingdom" && priceAt(p, "cardkingdom", id, finish, condition, null) != null) return null;
  return `${condition} ${Math.min(100, Math.max(0, pct))}%`;
}

/** The card's page at TCGplayer or Card Kingdom, when known. */
export function sourceUrl(p: PricingSetup, s: Source, id: string, finish: Finish): string | null {
  if (s === "tcgplayer") return info(p, id)?.tu ?? null;
  if (s === "cardkingdom") {
    const r = ckRow(p, id, finish) ?? ckRow(p, id, "NONFOIL") ?? ckRow(p, id, "FOIL");
    return r && r[6] ? `https://www.cardkingdom.com/${r[6]}` : null;
  }
  return null;
}

/** What Card Kingdom pays for one copy (its buylist), in euros. */
export function cardKingdomPays(p: PricingSetup, id: string, finish: Finish): number | null {
  return toEur(p, ckRow(p, id, finish)?.[1]);
}

export { PRICE_TYPES };
