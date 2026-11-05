// Amounts are kept in euros and shown in the app's chosen currency (Currency.kt).
import { FALLBACK_RATES, type PriceFile } from "./prices";

export const CURRENCIES = ["EUR", "DKK", "USD"] as const;
export type Currency = (typeof CURRENCIES)[number];

export function currency(key: string | undefined): Currency {
  return (CURRENCIES as readonly string[]).includes(key ?? "") ? (key as Currency) : "EUR";
}

/** How many of [c] one euro buys; null while unknown. */
export function rate(c: Currency, rates: PriceFile["rates"] | null): number | null {
  if (c === "EUR") return 1;
  return rates?.perEuro[c] ?? FALLBACK_RATES.perEuro[c] ?? null;
}

/** "€1.50", "11.19 kr.", "$1.62". */
export function format(amount: number, c: Currency): string {
  const n = amount.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  if (c === "DKK") return `${n} kr.`;
  return (c === "USD" ? "$" : "€") + n;
}

/** An amount typed in [c] (an alert level), in euros; null while the rate is unknown. */
export function toEur(amount: number, c: string | null, rates: PriceFile["rates"] | null): number | null {
  const r = rate(currency(c ?? "EUR"), rates);
  return r ? amount / r : null;
}
