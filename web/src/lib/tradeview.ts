// The trades, read only, as the app shows them: each side's cards with the prices captured when they were added
// (or a price agreed by hand), and the balance with the app's fairness margin (Models.kt TradeItem.unitPrice, Balance).
import { currency, rate, type Currency } from "./money";
import { best, priceType, PRICE_TYPE_LABELS, PRICE_TYPES, type PriceFile } from "./prices";
import { finishOf, unitPrice, type Finish, type PricingSetup } from "./pricing";
import { prefs, type CardRef, type Json, type SyncFile } from "./syncfile";

export interface TradeLine {
  side: "GET" | "GIVE";
  card: CardRef;
  finish: Finish;
  condition: string;
  language: string;
  quantity: number;
  /** One copy in euros (the agreed price when there is one). */
  unitEur: number | null;
  custom: boolean;
}

export type Verdict = "EMPTY" | "FAIR" | "FAVORS_YOU" | "FAVORS_THEM";

export interface TradeCard {
  uid: string;
  createdAt: number;
  partner: string;
  notes: string;
  applied: boolean;
  appliedAt: number | null;
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
  tolerancePct: number;
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
  };
}

interface SyncTrade {
  trade: { uid?: string | null; createdAt?: number; partner?: string; notes?: string; applied?: boolean; appliedAt?: number | null };
  items?: SyncTradeItem[];
}

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
  const trades = (((file.data.trades as Json[] | undefined) ?? []) as unknown as SyncTrade[])
    .filter((t) => t.trade?.uid)
    .map((t): TradeCard => {
      const lines = (t.items ?? []).map(({ item: i }): TradeLine => {
        const finish = finishOf(i.foil, !!i.etched);
        const set = PRICE_TYPES.map((k) => i.prices?.[k] ?? null);
        const cm = best(set, type) ?? (i.foil ? i.card.fallbackEurFoil : i.card.fallbackEur) ?? null;
        const custom = i.customPrice != null;
        return {
          side: i.side === "GET" ? "GET" : "GIVE",
          card: i.card,
          finish,
          condition: i.condition ?? "NM",
          language: i.language ?? "EN",
          quantity: i.quantity ?? 1,
          unitEur: custom ? i.customPrice! : unitPrice(setup, i.card.scryfallId, finish, i.condition ?? "NM", cm),
          custom,
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
        lines,
        give,
        get,
        ...verdictOf(give, get, tolerancePct),
      };
    })
    .sort((a, b) => b.createdAt - a.createdAt);
  const cur = currency(p.currency);
  return { trades, currency: cur, perEuro: rate(cur, prices?.rates ?? null) ?? 1, priceTypeLabel: PRICE_TYPE_LABELS[type], tolerancePct };
}
