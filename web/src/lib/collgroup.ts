// The collection table's "Group printings" (website only): every pile of the same card name on one line, whatever its
// set, finish, condition or binder. Filters run first, so a group holds only the piles that are shown.
import type { CollRow } from "./collview";

export interface CollGroup {
  /** The card's name in lower case. */
  key: string;
  /** Its piles, in the table's sort order. */
  rows: CollRow[];
  /**
   * A stand-in pile for sorting the groups with the usual comparator: copies added up, the highest price, the newest
   * date, the change only when every pile has the same one; the card itself from the first pile.
   */
  sum: CollRow;
  /** Different printings (set and collector number). */
  printings: number;
  /** Cheapest and dearest copy at the price source (null: no pile has a price). */
  minEur: number | null;
  maxEur: number | null;
  /** All copies at their price (piles without a price count as nothing, like the summary line). */
  totalEur: number;
  /** Price minus paid, all copies of the piles that have both (null: none has). */
  profitEur: number | null;
}

/** The value every pile shares, or undefined when they differ. */
export function same<T>(rows: CollRow[], f: (r: CollRow) => T): T | undefined {
  const first = f(rows[0]);
  return rows.every((r) => f(r) === first) ? first : undefined;
}

/** Lowest and highest of the values that are there; null when none is. */
export function range(values: (number | null)[]): { min: number; max: number } | null {
  const v = values.filter((x): x is number => x != null);
  return v.length ? { min: Math.min(...v), max: Math.max(...v) } : null;
}

/**
 * The piles grouped by card name. [rows] must already be sorted with [cmp]: each group keeps that order inside, and the
 * groups are sorted with [cmp] on their stand-in piles.
 */
export function groupRows(rows: CollRow[], cmp: (a: CollRow, b: CollRow) => number): CollGroup[] {
  const byKey = new Map<string, CollRow[]>();
  for (const r of rows) {
    const k = r.card.name.toLowerCase();
    const list = byKey.get(k);
    if (list) list.push(r);
    else byKey.set(k, [r]);
  }
  const groups = [...byKey.entries()].map(([key, list]): CollGroup => {
    const first = list[0];
    const prices = range(list.map((r) => r.unitEur));
    const withProfit = list.filter((r) => r.purchasePrice != null && r.unitEur != null);
    const changePct = same(list, (r) => r.changePct);
    const changeEur = same(list, (r) => r.changeEur);
    return {
      key,
      rows: list,
      sum: {
        ...first,
        uid: `group:${key}`,
        quantity: list.reduce((n, r) => n + r.quantity, 0),
        addedAt: Math.max(...list.map((r) => r.addedAt)),
        unitEur: prices?.max ?? null,
        changePct: changePct === undefined ? null : changePct,
        changeEur: changeEur === undefined ? null : changeEur,
      },
      printings: new Set(list.map((r) => `${r.card.setCode.toLowerCase()}#${r.card.collectorNumber}`)).size,
      minEur: prices?.min ?? null,
      maxEur: prices?.max ?? null,
      totalEur: list.reduce((n, r) => n + (r.unitEur ?? 0) * r.quantity, 0),
      profitEur: withProfit.length ? withProfit.reduce((n, r) => n + (r.unitEur! - r.purchasePrice!) * r.quantity, 0) : null,
    };
  });
  return groups.sort((a, b) => cmp(a.sum, b.sum));
}
