// The nightly job (/api/cron/nightly, 06:30 UTC, after the price jobs), for every account with a collection:
// - the day's collection value, with the same numbers and keys as the app's ValueHistory.snapshotValues, so the
//   phone's history (sent once by the app) and the website's days make one chart, and each pile's price (9 days,
//   for the weekly email's gainers and losers);
// - the wishlist's price alerts against Cardmarket at the alert price type (the app's PriceAlerts rules), with
//   the levels reached written to the account's alert history. CardTrader is left to the phones.
import { accountStore, type AlertEvent, type ValueDay } from "./accounts";
import { addDetails, loadDetails, type DetailsFile } from "./carddetails";
import { loadCardKingdom, type CkFile } from "./cardkingdom";
import { displayName } from "./colquery";
import { USER_AGENT } from "./config";
import { scryFetch } from "./scrylimit";
import { toEur } from "./money";
import { loadPrices } from "./pricestore";
import { PRICE_TYPES, priceType, type PriceFile, type PriceType } from "./prices";
import { cardKingdomPays, cardmarketPrice, finishOf, priceAt, pricingSetup, type Source } from "./pricing";
import { binders, collection, decode, prefs, wishlist, type CardRef, type SyncFile } from "./syncfile";
import { pilePrices, PRICE_DAYS_KEPT } from "./weekly";

const SOURCES: Source[] = ["cardmarket", "tcgplayer", "cardkingdom"];

/** "trend" for Cardmarket, "trend@tcgplayer" for the others (ValueHistory.sourceKey). */
export const sourceKey = (type: PriceType, source: Source) => (source === "cardmarket" ? type : `${type}@${source}`);
/** What Card Kingdom would pay for the lot (its buylist). */
export const CK_PAYS = "pays@cardkingdom";
/** A binder's entry; Unsorted is "~" (the app's binder 0). */
export const binderKey = (binder: string, what: string) => `b:${binder}:${what}`;
export const UNSORTED = "~";

/** The day's numbers for [file] (ValueHistory.snapshotValues): totals per source and price type, and per binder. */
export function snapshotValues(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): { cards: number; values: Record<string, number> } {
  const setup = pricingSetup(prefs(file), "trend", prices, details, ck);
  const known = new Set(binders(file).flatMap((b) => (b.uid ? [b.uid] : [])));
  const rows = collection(file).map((s) => {
    const it = s.item;
    const finish = finishOf(it.foil, it.etched);
    const cm = Object.fromEntries(PRICE_TYPES.map((t) => [t, cardmarketPrice(it.card, it.foil, t, prices)])) as Record<PriceType, number | null>;
    return { id: it.card.scryfallId, finish, condition: it.condition, quantity: it.quantity, cm, binder: s.binder && known.has(s.binder) ? s.binder : UNSORTED };
  });
  type R = (typeof rows)[number];
  const at = (r: R, s: Source, t: PriceType) => priceAt(setup, s, r.id, r.finish, r.condition, r.cm[t]);
  // Only sources with prices for some card get a value (the dollar rate is needed too); Cardmarket always.
  const sources = SOURCES.filter((s) => s === "cardmarket" || rows.some((r) => at(r, s, "trend") != null));
  const out: Record<string, number> = {};
  const totals = (rs: R[], key: (k: string) => string) => {
    for (const s of sources)
      for (const t of PRICE_TYPES) out[key(sourceKey(t, s))] = rs.reduce((n, r) => n + (at(r, s, t) ?? at(r, "cardmarket", t) ?? 0) * r.quantity, 0);
    if (sources.includes("cardkingdom")) out[key(CK_PAYS)] = rs.reduce((n, r) => n + (cardKingdomPays(setup, r.id, r.finish) ?? 0) * r.quantity, 0);
  };
  totals(rows, (k) => k);
  // Every binder, an empty one too, so its chart drops to zero instead of stopping.
  for (const b of new Set([...known, UNSORTED, ...rows.map((r) => r.binder)])) {
    const rs = rows.filter((r) => r.binder === b);
    out[binderKey(b, "cards")] = rs.reduce((n, r) => n + r.quantity, 0);
    totals(rs, (k) => binderKey(b, k));
  }
  return { cards: rows.reduce((n, r) => n + r.quantity, 0), values: out };
}

// ---- Price alerts (PriceAlerts) ----

/** One printing a wished card can be bought as (WishPrinting). */
export interface WishPrinting {
  id: string;
  set: string;
  cm: number | null;
  cmFoil: number | null;
  nonfoil: boolean;
  foil: boolean;
  etched: boolean;
}

export const printingOf = (c: CardRef): WishPrinting => ({ id: c.scryfallId, set: c.setCode, cm: c.cardmarketId, cmFoil: c.cardmarketFoilId, nonfoil: c.hasNonFoil, foil: c.hasFoil, etched: c.hasEtched });

/** Cardmarket's [type] price of a printing in the wished finish, in euros; null when none or not made so (PriceAlerts.priceOf). */
export function printingPrice(p: WishPrinting, foil: boolean, type: PriceType, prices: PriceFile | null): number | null {
  const made = foil ? p.foil || p.etched : p.nonfoil;
  if (!made) return null;
  const id = foil ? (p.cmFoil ?? p.cm) : p.cm;
  const row = id != null ? prices?.products[String(id)] : undefined;
  const v = row ? row[(foil ? 6 : 0) + PRICE_TYPES.indexOf(type)] : null;
  return v != null && v > 0 ? v : null;
}

/** Whether a level was reached, and the level remembered afterwards (PriceAlerts.step). */
export function step(level: number | null, best: number | null, alertedLevel: number | null, key: number | null = level): { notify: boolean; alertedLevel: number | null } {
  if (level === null) return { notify: false, alertedLevel: null };
  if (best === null) return { notify: false, alertedLevel };
  if (best <= level + 0.0001) return { notify: alertedLevel !== key, alertedLevel: key };
  return { notify: false, alertedLevel: null };
}

/** Looks up a card's paper printings by name ("any printing" cards); null when it can't. */
export type PrintingsOf = (name: string) => Promise<WishPrinting[] | null>;

const PENDING = "00000000-0000-0000-0000-";

/**
 * Checks every wishlist card with an alert level (not ordered or received): the cheapest printing's price against
 * the level. [levels] are the levels reached at the last check; the answer has the new ones and the lines to add.
 */
export async function checkAlerts(file: SyncFile, prices: PriceFile | null, levels: Map<string, number>, now: number, printingsOf: PrintingsOf): Promise<{ events: AlertEvent[]; levels: Map<string, number> }> {
  const type = priceType(prefs(file).alertPriceType ?? "low");
  const basis = `cardmarket:${type}`;
  const events: AlertEvent[] = [];
  const next = new Map<string, number>();
  for (const w of wishlist(file)) {
    if (!w.uid || w.alertBelow == null || w.orderedAt != null || w.receivedAt != null || w.card.scryfallId.startsWith(PENDING)) continue;
    const level = toEur(w.alertBelow, w.alertCurrency, prices?.rates ?? null);
    const printings = (w.anyPrinting ? await printingsOf(w.card.name) : null) ?? [printingOf(w.card)];
    let best: { price: number; set: string } | null = null;
    for (const p of printings) {
      const v = printingPrice(p, w.foil, type, prices);
      if (v !== null && (best === null || v < best.price)) best = { price: v, set: p.set };
    }
    const s = step(level, best?.price ?? null, levels.get(w.uid) ?? null, w.alertBelow);
    if (s.notify && best) {
      events.push({ wishUid: w.uid, cardName: displayName(w.card), at: now, price: best.price, level: level ?? best.price, basis, setCode: w.anyPrinting ? best.set : null });
    }
    if (s.alertedLevel !== null) next.set(w.uid, s.alertedLevel);
  }
  return { events, levels: next };
}

type Fetch = typeof fetch;

/** Scryfall's paper printings of [name] (as the app's ScryfallApi.prints), at most 5 pages; null when the lookup fails. */
export function scryfallPrintings(f: Fetch = scryFetch): PrintingsOf {
  const cache = new Map<string, WishPrinting[] | null>();
  const exact = async (name: string): Promise<WishPrinting[] | null> => {
    const out: WishPrinting[] = [];
    const q = new URLSearchParams({ q: `!"${name}" game:paper`, unique: "prints", order: "released", dir: "desc", include_extras: "true" });
    let next: string | null = `https://api.scryfall.com/cards/search?${q}`;
    for (let page = 0; next && page < 5; page++) {
      await new Promise((r) => setTimeout(r, 100));
      const r = await f(next, { headers: { "User-Agent": USER_AGENT, Accept: "application/json" }, signal: AbortSignal.timeout(20_000), cache: "no-store" });
      if (r.status === 404) break;
      if (!r.ok) return null;
      const j = (await r.json()) as { data?: { id: string; set: string; cardmarket_id?: number; finishes?: string[]; digital?: boolean }[]; has_more?: boolean; next_page?: string };
      for (const c of j.data ?? []) {
        if (c.digital) continue;
        const fin = c.finishes ?? [];
        out.push({ id: c.id, set: c.set, cm: c.cardmarket_id ?? null, cmFoil: null, nonfoil: fin.includes("nonfoil") || fin.length === 0, foil: fin.includes("foil"), etched: fin.includes("etched") });
      }
      next = j.has_more && j.next_page ? j.next_page : null;
    }
    return out;
  };
  return async (name) => {
    if (cache.has(name)) return cache.get(name)!;
    let got: WishPrinting[] | null = null;
    try {
      got = await exact(name);
      // Double-faced cards: the exact search may only know the front face's name.
      if (got && got.length === 0 && name.includes(" // ")) got = await exact(name.split(" // ")[0]);
    } catch {
      got = null;
    }
    const use = got && got.length > 0 ? got : null;
    cache.set(name, use);
    return use;
  };
}

// ---- The phone's history (POST /api/app/valuehistory) ----

/** One day from the app, checked: "2026-11-07", a card count, and number values by ValueHistory key. */
export function cleanDay(d: unknown): ValueDay | null {
  if (!d || typeof d !== "object") return null;
  const { day, cards, values } = d as { day?: unknown; cards?: unknown; values?: unknown };
  if (typeof day !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(day)) return null;
  if (typeof cards !== "number" || !Number.isFinite(cards) || cards < 0) return null;
  if (!values || typeof values !== "object" || Array.isArray(values)) return null;
  const out: Record<string, number> = {};
  for (const [k, v] of Object.entries(values)) if (k.length <= 120 && typeof v === "number" && Number.isFinite(v)) out[k] = v;
  return { day, cards: Math.round(cards), values: out };
}

// ---- The job ----

export interface NightlyResult {
  day: string;
  accounts: number;
  values: number;
  alertsChecked: number;
  alertsReached: number;
  failed: number;
}

export interface NightlyDeps {
  prices: () => Promise<PriceFile | null>;
  details: () => Promise<DetailsFile>;
  ck: () => Promise<CkFile | null>;
  /** Looks up card details the shared file lacks (TCGplayer's prices); a batch at a time. */
  addDetails: (ids: string[]) => Promise<unknown>;
  printingsOf: PrintingsOf;
}

const defaultDeps = (): NightlyDeps => ({ prices: loadPrices, details: () => loadDetails(true), ck: loadCardKingdom, addDetails, printingsOf: scryfallPrintings() });

/** Runs the job for every account; one account failing doesn't stop the others. The counts go to the jobs table. */
export async function runNightly(now = Date.now(), deps: NightlyDeps = defaultDeps(), deadline = now + 240_000): Promise<NightlyResult> {
  const store = accountStore();
  if (!store) throw new Error("No database.");
  const day = new Date(now).toISOString().slice(0, 10);
  const prices = await deps.prices();
  const ck = await deps.ck();
  let details = await deps.details();
  const files: { id: number; file: SyncFile }[] = [];
  let failed = 0;
  for (const a of await store.list()) {
    try {
      const sync = await store.getSync(a.id);
      if (sync) files.push({ id: a.id, file: decode(sync.bytes) });
    } catch (e) {
      console.error(`Nightly: account ${a.id}'s file can't be read:`, e);
      failed++;
    }
  }
  // Card details (TCGplayer's prices) for cards nobody has opened on the website yet.
  const missing = [...new Set(files.flatMap(({ file }) => collection(file).map((s) => s.item.card.scryfallId)))].filter((id) => !details.cards[id] && !id.startsWith(PENDING));
  if (missing.length > 0) {
    try {
      for (let i = 0; i < missing.length && i < 40 * 75 && Date.now() < deadline - 120_000; i += 75) await deps.addDetails(missing.slice(i, i + 75));
      details = await deps.details();
    } catch (e) {
      console.warn("Nightly: card details lookup failed:", e);
    }
  }
  const result: NightlyResult = { day, accounts: files.length, values: 0, alertsChecked: 0, alertsReached: 0, failed };
  for (const { id, file } of files) {
    try {
      if (prices) {
        const { cards, values } = snapshotValues(file, prices, details, ck);
        if (cards > 0) {
          const d: ValueDay = { day, cards, values };
          await store.putValueDays(id, [d], true);
          result.values++;
          // Each pile's price, for the weekly email's gainers and losers.
          await store.putPriceDay(id, day, pilePrices(file, prices, details, ck), PRICE_DAYS_KEPT);
        }
      }
      if (prices && Date.now() < deadline) {
        const before = await store.alertLevels(id);
        const { events, levels } = await checkAlerts(file, prices, before, now, deps.printingsOf);
        await store.setAlertLevels(id, levels);
        await store.addAlertEvents(id, events, now);
        result.alertsChecked += wishlist(file).filter((w) => w.alertBelow != null).length;
        result.alertsReached += events.length;
      }
    } catch (e) {
      console.error(`Nightly: account ${id} failed:`, e);
      result.failed++;
    }
  }
  await store.setJob("nightly", now, { ...result, pricesCreatedAt: prices?.createdAt ?? null });
  return result;
}
