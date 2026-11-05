// What the wishlist page shows, worked out on the server from the sync file and the price file.
import { currency, rate, toEur, type Currency } from "./money";
import { priceOf, priceType, PRICE_TYPE_LABELS, type PriceFile } from "./prices";
import { canWrite, prefs, type CardRef, type SyncFile, wishlist } from "./syncfile";
import { ownedFor } from "./wishlist";

export interface WishRow {
  uid: string;
  card: CardRef;
  foil: boolean;
  quantity: number;
  anyPrinting: boolean;
  notes: string | null;
  addedAt: number;
  orderedAt: number | null;
  receivedAt: number | null;
  receivedFrom: string | null;
  alertBelow: number | null;
  alertCurrency: string | null;
  /** One copy, in euros. */
  unitEur: number | null;
  trendPct: number | null;
  /** Copies got since the card was put on the list (the app's "Got N since adding"). */
  gotSince: number;
  /** The price is at or under the alert level. */
  alertHit: boolean;
  /** The alert level in euros (null without an alert, or when its currency's rate is unknown). */
  alertEur: number | null;
}

export interface WishView {
  rows: WishRow[];
  currency: Currency;
  /** How many of [currency] one euro buys. */
  perEuro: number;
  priceTypeLabel: string;
  /** Set when the app is set to another price source than Cardmarket, which the website doesn't have yet. */
  sourceNote: string | null;
  pricesDate: string | null;
  readOnly: boolean;
  format: number;
  writtenAt: number;
  writtenBy: string;
}

export function wishView(file: SyncFile, prices: PriceFile | null): WishView {
  const p = prefs(file);
  const type = priceType(p.priceType);
  const cur = currency(p.currency);
  const source = p.priceSource ?? "cardmarket";
  const rows = wishlist(file)
    .filter((w) => w.uid)
    .map((w): WishRow => {
      const price = priceOf(w.card, w.foil, type, prices);
      const owned = ownedFor(file, w.card, w.foil, w.anyPrinting);
      const level = w.alertBelow != null ? toEur(w.alertBelow, w.alertCurrency, prices?.rates ?? null) : null;
      return {
        uid: w.uid!,
        card: w.card,
        foil: w.foil,
        quantity: w.quantity,
        anyPrinting: w.anyPrinting,
        notes: w.notes,
        addedAt: w.addedAt,
        orderedAt: w.orderedAt,
        receivedAt: w.receivedAt,
        receivedFrom: w.receivedFrom,
        alertBelow: w.alertBelow,
        alertCurrency: w.alertCurrency,
        unitEur: price.eur,
        trendPct: price.trendPct,
        gotSince: Math.max(0, owned - w.ownedAtAdd),
        alertHit: level != null && price.eur != null && price.eur <= level + 0.0001,
        alertEur: level,
      };
    });
  return {
    rows,
    currency: cur,
    perEuro: rate(cur, prices?.rates ?? null) ?? 1,
    priceTypeLabel: PRICE_TYPE_LABELS[type],
    sourceNote: source.toLowerCase() === "cardmarket" ? null : "The app uses another price source; the website shows Cardmarket's prices.",
    pricesDate: prices?.createdAt ?? null,
    readOnly: !canWrite(file),
    format: file.format,
    writtenAt: file.writtenAt,
    writtenBy: file.writtenBy,
  };
}
