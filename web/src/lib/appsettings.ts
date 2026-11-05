// The app's synced settings the website can change (Settings.kt's synced values), with the values the app accepts.
import { CONDITIONS } from "./cards";
import { DEFAULT_CONDITION_PCTS, decodePcts } from "./pricing";
import { prefs, setPrefs, type SyncFile } from "./syncfile";
import { EditError } from "./wishlist";

export const PRICE_SOURCES: [string, string][] = [
  ["cardmarket", "Cardmarket"],
  ["tcgplayer", "TCGplayer"],
  ["cardkingdom", "Card Kingdom"],
];
/** The app's PriceType, in its order. */
export const PRICE_TYPE_CHOICES: [string, string][] = [
  ["trend", "Trend price"],
  ["avg", "Average sell price"],
  ["avg30", "30-day average"],
  ["avg7", "7-day average"],
  ["avg1", "1-day average"],
  ["low", "Lowest listing"],
];
export const CURRENCY_CHOICES: [string, string][] = [
  ["EUR", "Euro (€)"],
  ["DKK", "Danish kroner (kr.)"],
  ["USD", "US dollar ($)"],
];
/** The app's ReceivedClear. */
export const RECEIVED_CLEAR: [string, string][] = [
  ["never", "Never (keep them until you clear them)"],
  ["1m", "After 1 month"],
  ["3m", "After 3 months"],
  ["6m", "After 6 months"],
  ["1y", "After 1 year"],
];

export interface AppSettings {
  priceSource: string;
  priceType: string;
  currency: string;
  conditionAdjust: boolean;
  /** Condition code → percent of the near mint price. */
  conditionPcts: Record<string, number>;
  tolerancePct: number;
  alertPriceType: string;
  wishSections: boolean;
  receivedToCollection: boolean;
  receivedClear: string;
}

const keys = (list: [string, string][]) => list.map(([k]) => k);

/** The settings as the app reads them (its defaults where a value is missing). */
export function appSettings(file: SyncFile): AppSettings {
  const p = prefs(file);
  const pick = (v: string | undefined, list: [string, string][], dflt: string) => (v && keys(list).includes(v) ? v : dflt);
  const tol = Number.parseInt(p.tolerancePct ?? "", 10);
  return {
    priceSource: pick(p.priceSource, PRICE_SOURCES, "cardmarket"),
    priceType: pick(p.priceType, PRICE_TYPE_CHOICES, "trend"),
    currency: pick(p.currency, CURRENCY_CHOICES, "EUR"),
    conditionAdjust: p.conditionAdjust === "true",
    conditionPcts: decodePcts(p.conditionPcts),
    tolerancePct: Number.isFinite(tol) ? tol : 5,
    alertPriceType: pick(p.alertPriceType, PRICE_TYPE_CHOICES, "low"),
    wishSections: p.wishSections !== "false",
    receivedToCollection: p.receivedToCollection === "true",
    receivedClear: pick(p.receivedClear, RECEIVED_CLEAR, "never"),
  };
}

/** Saves [changes] in the app's format; refuses values the app wouldn't take. */
export function applySettings(file: SyncFile, changes: Partial<AppSettings>, now: number): void {
  const out: Record<string, string> = {};
  const choice = (key: keyof AppSettings, list: [string, string][]) => {
    const v = changes[key];
    if (v === undefined) return;
    if (typeof v !== "string" || !keys(list).includes(v)) throw new EditError(`Unknown value for ${key}.`);
    out[key] = v;
  };
  const flag = (key: keyof AppSettings) => {
    const v = changes[key];
    if (v === undefined) return;
    if (typeof v !== "boolean") throw new EditError(`${key} must be on or off.`);
    out[key] = String(v);
  };
  choice("priceSource", PRICE_SOURCES);
  choice("priceType", PRICE_TYPE_CHOICES);
  choice("currency", CURRENCY_CHOICES);
  choice("alertPriceType", PRICE_TYPE_CHOICES);
  choice("receivedClear", RECEIVED_CLEAR);
  flag("conditionAdjust");
  flag("wishSections");
  flag("receivedToCollection");
  if (changes.tolerancePct !== undefined) {
    const t = changes.tolerancePct;
    if (!Number.isInteger(t) || t < 0 || t > 20) throw new EditError("The fair trade margin is 0 to 20%.");
    out.tolerancePct = String(t);
  }
  if (changes.conditionPcts !== undefined) {
    const pcts = changes.conditionPcts;
    if (typeof pcts !== "object" || pcts === null) throw new EditError("Unknown condition percentages.");
    const codes = CONDITIONS.map(([c]) => c).filter((c) => c in DEFAULT_CONDITION_PCTS);
    // The app's encodePcts: every condition, "MT=100,NM=100,EX=85,…".
    out.conditionPcts = codes
      .map((c) => {
        const v = pcts[c] ?? DEFAULT_CONDITION_PCTS[c];
        if (!Number.isInteger(v) || v < 0 || v > 100) throw new EditError("Condition percentages are 0 to 100.");
        return `${c}=${v}`;
      })
      .join(",");
  }
  const before = prefs(file);
  const changed = Object.fromEntries(Object.entries(out).filter(([k, v]) => before[k] !== v));
  if (Object.keys(changed).length > 0) setPrefs(file, changed, now);
}
