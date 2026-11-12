// How long the nightly job keeps each pile's daily price (since 2026-11-13, for the card window's price chart): every
// day for the last 3 months, then one day a week (the first saved day of each week) up to a year; older days go.
const DAY_MS = 86_400_000;
/** Every day is kept this long. */
export const DAILY_DAYS = 92;
/** Nothing older than this is kept. */
export const KEPT_DAYS = 366;

const ms = (day: string) => Date.parse(`${day}T00:00:00Z`);

/** The Monday of a day's week, as "2026-11-09". */
export function weekOf(day: string): string {
  const t = ms(day);
  const weekday = (new Date(t).getUTCDay() + 6) % 7; // 0 Monday … 6 Sunday
  return new Date(t - weekday * DAY_MS).toISOString().slice(0, 10);
}

/** The saved days ([days], any order) that should go, seen from [today]. */
export function daysToDrop(days: string[], today: string): string[] {
  const t = ms(today);
  const drop: string[] = [];
  const firstOfWeek = new Map<string, string>();
  for (const d of [...days].sort()) {
    const age = (t - ms(d)) / DAY_MS;
    if (age >= KEPT_DAYS) {
      drop.push(d);
      continue;
    }
    if (age < DAILY_DAYS) continue;
    const w = weekOf(d);
    if (firstOfWeek.has(w)) drop.push(d);
    else firstOfWeek.set(w, d);
  }
  return drop;
}

/** The first day the weekly email needs (it compares with a week back, at most 3 days older). */
export function weeklySince(today: string): string {
  return new Date(ms(today) - 11 * DAY_MS).toISOString().slice(0, 10);
}

export interface PricePoint {
  day: string;
  eur: number;
}
