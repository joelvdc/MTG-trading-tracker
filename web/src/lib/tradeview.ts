// The trades as the app shows them (made and changed on the website since 2026-11-13, lib/tradeedit.ts): each side's cards with the prices captured when they were added
// (or a price agreed by hand), and the balance with the app's fairness margin (Models.kt TradeItem.unitPrice, Balance).
import { currency, rate, type Currency } from "./money";
import { best, priceType, PRICE_TYPE_LABELS, PRICE_TYPES, type PriceFile } from "./prices";
import { finishOf, unitPrice, type Finish, type PricingSetup } from "./pricing";
import { binders, canWrite, prefs, type CardRef, type Json, type SyncFile } from "./syncfile";
import { lineKeys, type SyncTradeJ } from "./tradeedit";

export interface TradeLine {
  /** What edits name the line by (tradeedit.ts lineKeys). */
  key: string;
  side: "GET" | "GIVE";
  card: CardRef;
  finish: Finish;
  condition: string;
  language: string;
  quantity: number;
  /** One copy in euros (the agreed price when there is one). */
  unitEur: number | null;
  custom: boolean;
  /** The agreed price in euros, and as typed in kroner or dollars. */
  customEur: number | null;
  customTyped: number | null;
  customCurrency: string | null;
  /** Completed: copies added (+) or taken out (−). */
  appliedDelta: number;
}

export type Verdict = "EMPTY" | "FAIR" | "FAVORS_YOU" | "FAVORS_THEM";

export interface TradeCard {
  uid: string;
  createdAt: number;
  partner: string;
  notes: string;
  applied: boolean;
  appliedAt: number | null;
  /** Completed: the binder the received cards went into. */
  binderName: string | null;
  lines: TradeLine[];
  give: number;
  get: number;
  /** The difference against the bigger side, in percent (positive: you get more). */
  pct: number;
  verdict: Verdict;
}

export interface TradesView {
  trades: TradeCard[];
  currency: Currency;
  perEuro: number;
  priceTypeLabel: string;
  /** The app's short name of the price type, for the CSV ("Trend"). */
  priceTypeShort: string;
  tolerancePct: number;
  binders: { uid: string; name: string }[];
  readOnly: boolean;
}

interface SyncTradeItem {
  item: {
    side: string;
    card: CardRef;
    foil: boolean;
    etched?: boolean;
    condition?: string;
    language?: string;
    quantity?: number;
    prices?: Record<string, number | null>;
    customPrice?: number | null;
    customTyped?: number | null;
    customCurrency?: string | null;
    appliedDelta?: number;
  };
}

interface SyncTrade {
  binder?: string | null;
  trade: { uid?: string | null; createdAt?: number; partner?: string; notes?: string; applied?: boolean; appliedAt?: number | null };
  items?: SyncTradeItem[];
}

/** The app's PriceType.short, for the CSV's price column. */
const PRICE_TYPE_SHORT: Record<string, string> = { trend: "Trend", avg: "Average", avg30: "30-day avg", avg7: "7-day avg", avg1: "1-day avg", low: "Low" };

export function verdictOf(give: number, get: number, tolerancePct: number): { pct: number; verdict: Verdict } {
  const m = Math.max(give, get);
  const pct = m > 0 ? ((get - give) / m) * 100 : 0;
  if (give === 0 && get === 0) return { pct, verdict: "EMPTY" };
  if (Math.abs(pct) <= tolerancePct) return { pct, verdict: "FAIR" };
  return { pct, verdict: get > give ? "FAVORS_YOU" : "FAVORS_THEM" };
}

export function tradesView(file: SyncFile, setup: PricingSetup, prices: PriceFile | null): TradesView {
  const p = prefs(file);
  const type = priceType(p.priceType);
  const tol = Number.parseInt(p.tolerancePct ?? "", 10);
  const tolerancePct = Number.isFinite(tol) ? tol : 5;
  const names = new Map(binders(file).flatMap((b) => (b.uid ? [[b.uid, b.name] as const] : [])));
  const trades = (((file.data.trades as Json[] | undefined) ?? []) as unknown as SyncTrade[])
    .filter((t) => t.trade?.uid)
    .map((t): TradeCard => {
      const keys = lineKeys({ ...(t as unknown as SyncTradeJ), items: (t.items ?? []) as unknown as SyncTradeJ["items"] });
      const lines = (t.items ?? []).map(({ item: i }, n): TradeLine => {
        const finish = finishOf(i.foil, !!i.etched);
        const set = PRICE_TYPES.map((k) => i.prices?.[k] ?? null);
        const cm = best(set, type) ?? (i.foil ? i.card.fallbackEurFoil : i.card.fallbackEur) ?? null;
        const custom = i.customPrice != null;
        return {
          key: keys[n],
          side: i.side === "GET" ? "GET" : "GIVE",
          card: i.card,
          finish,
          condition: i.condition ?? "NM",
          language: i.language ?? "EN",
          quantity: i.quantity ?? 1,
          unitEur: custom ? i.customPrice! : unitPrice(setup, i.card.scryfallId, finish, i.condition ?? "NM", cm),
          custom,
          customEur: i.customPrice ?? null,
          customTyped: i.customTyped ?? null,
          customCurrency: i.customCurrency ?? null,
          appliedDelta: i.appliedDelta ?? 0,
        };
      });
      const total = (side: string) => lines.filter((l) => l.side === side).reduce((n, l) => n + (l.unitEur ?? 0) * l.quantity, 0);
      const give = total("GIVE");
      const get = total("GET");
      return {
        uid: t.trade.uid!,
        createdAt: t.trade.createdAt ?? 0,
        partner: t.trade.partner ?? "",
        notes: t.trade.notes ?? "",
        applied: !!t.trade.applied,
        appliedAt: t.trade.appliedAt ?? null,
        binderName: t.trade.applied ? (t.binder ? (names.get(t.binder) ?? "Unsorted") : "Unsorted") : null,
        lines,
        give,
        get,
        ...verdictOf(give, get, tolerancePct),
      };
    })
    .sort((a, b) => b.createdAt - a.createdAt);
  const cur = currency(p.currency);
  return {
    trades,
    currency: cur,
    perEuro: rate(cur, prices?.rates ?? null) ?? 1,
    priceTypeLabel: PRICE_TYPE_LABELS[type],
    priceTypeShort: PRICE_TYPE_SHORT[type],
    tolerancePct,
    binders: [...names.entries()].map(([uid, name]) => ({ uid, name })).sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())),
    readOnly: !canWrite(file),
  };
}
