// The weekly email (Settings → Weekly email): the collection's value against a week ago, the week's biggest gainers
// and losers among the cards owned, wishlist cards that reached their alert price, and changes in the decks (game
// changers added or removed, new Commander legality problems). Everything in the owner's price source and currency.
// The nightly job keeps each pile's price for 9 days (pilePrices) so the week's change can be worked out.
import type { AlertEvent, EmailSettings, PriceDay, ValuePoint } from "./accounts";
import type { CkFile } from "./cardkingdom";
import type { DetailsFile } from "./carddetails";
import { sortPlacesOf, SORT_PLACES, sortSettledOf, sortSuggest, UNSORTED_KEY, PLACE_LABELS, type SortPlace } from "./collsort";
import { collectionView } from "./collview";
import { syncDecks } from "./decks";
import { checkLegality, ruleKey, type LegalRule } from "./legality";
import { format, type Currency } from "./money";
import type { PriceFile } from "./prices";
import { prefs, type Json, type SyncFile } from "./syncfile";

/** Days of card prices the nightly job keeps (a week back, plus slack for a missed night). */
export const DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const DAY_MS = 86_400_000;
const TOP = 5;
/** Most moves the email lists (the rest wait for the sort page). */
export const SORT_MAIL_MOVES = 10;

/** A pile's key in the daily prices: printing, finish and condition (the price depends on all three). */
export const pileKey = (scryfallId: string, finish: string, condition: string) => `${scryfallId}|${finish}|${condition}`;

/** Every owned pile's price (one copy, in euros, from the owner's price source), for the nightly job to keep. */
export function pilePrices(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): Record<string, number> {
  const out: Record<string, number> = {};
  for (const r of collectionView(file, prices, details, ck).rows) if (r.unitEur != null) out[pileKey(r.card.scryfallId, r.finish, r.condition)] = Math.round(r.unitEur * 100) / 100;
  return out;
}

/** The day ("2026-11-12") in Copenhagen, and its weekday (0 Monday … 6 Sunday). */
export function copenhagenDay(now: number): { day: string; weekday: number } {
  const parts = new Intl.DateTimeFormat("en-GB", { timeZone: "Europe/Copenhagen", year: "numeric", month: "2-digit", day: "2-digit", weekday: "short" }).formatToParts(new Date(now));
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "";
  const weekday = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].indexOf(get("weekday"));
  return { day: `${get("year")}-${get("month")}-${get("day")}`, weekday };
}

const isoDay = (ms: number) => new Date(ms).toISOString().slice(0, 10);

/** The newest saved entry at least [days] days before [today] (and at most 3 days older than that). */
function weekBefore<T extends { day: string }>(list: T[], today: string, days = 7): T | null {
  const t = Date.parse(`${today}T00:00:00Z`);
  const latest = isoDay(t - days * DAY_MS);
  const earliest = isoDay(t - (days + 3) * DAY_MS);
  return [...list].reverse().find((x) => x.day <= latest && x.day >= earliest) ?? null;
}

export interface Mover {
  name: string;
  set: string;
  finish: string;
  copies: number;
  before: number;
  now: number;
}

export interface DeckChange {
  deck: string;
  added: string[];
  removed: string[];
  problems: string[];
}

/** One move the sort suggests: a card going from one place to another ("Bulk → Not bulk"). */
export interface SortWeekMove {
  name: string;
  from: string;
  to: string;
  copies: number;
}

/** What the email says, before it is written out (tests check this). */
export interface WeeklyReport {
  currency: Currency;
  perEuro: number;
  priceLine: string;
  value: { now: number; before: number | null; cards: number };
  /** Null until a week of daily prices has been saved. */
  gainers: Mover[] | null;
  losers: Mover[] | null;
  alerts: AlertEvent[];
  decks: DeckChange[];
  /** The moves the sort suggests this week (up to [SORT_MAIL_MOVES]); null when the sort isn't set up and settled. */
  sort: { moves: SortWeekMove[] } | null;
  /** Kept for next week's comparison. */
  state: Record<string, Json>;
}

export interface WeeklyInput {
  file: SyncFile;
  prices: PriceFile | null;
  details: DetailsFile | null;
  ck: CkFile | null;
  priceDays: PriceDay[];
  /** The value chart for the owner's source and price type (whole collection). */
  valuePoints: ValuePoint[];
  alerts: AlertEvent[];
  /** The Commander rules known for the decks' cards, by name key. */
  rules: Map<string, LegalRule>;
  /** Lower-case front-face names on Wizards' Game Changers list (null: the list couldn't be read). */
  gameChangers: Set<string> | null;
  lastState: Record<string, Json>;
  now: number;
}

type DeckState = { gc: string[]; problems: string[] };

export function weeklyReport(i: WeeklyInput): WeeklyReport {
  const cv = collectionView(i.file, i.prices, i.details, i.ck);
  const today = isoDay(i.now);
  const value = cv.rows.reduce((n, r) => n + (r.unitEur ?? 0) * r.quantity, 0);
  const cards = cv.rows.reduce((n, r) => n + r.quantity, 0);
  const beforePoint = weekBefore(i.valuePoints, today);

  // The week's movers: each pile now against its price a week ago (same printing, finish and condition).
  const old = weekBefore(i.priceDays, today);
  let gainers: Mover[] | null = null;
  let losers: Mover[] | null = null;
  if (old) {
    const byKey = new Map<string, Mover>();
    for (const r of cv.rows) {
      if (r.unitEur == null) continue;
      const k = pileKey(r.card.scryfallId, r.finish, r.condition);
      const was = old.prices[k];
      if (was == null) continue;
      const m = byKey.get(k);
      if (m) m.copies += r.quantity;
      else byKey.set(k, { name: r.card.name, set: r.card.setCode.toUpperCase(), finish: r.finish, copies: r.quantity, before: was, now: Math.round(r.unitEur * 100) / 100 });
    }
    const moved = [...byKey.values()].filter((m) => Math.abs(m.now - m.before) >= 0.05);
    gainers = moved.filter((m) => m.now > m.before).sort((a, b) => b.now - b.before - (a.now - a.before) || a.name.localeCompare(b.name)).slice(0, TOP);
    losers = moved.filter((m) => m.now < m.before).sort((a, b) => a.now - a.before - (b.now - b.before) || a.name.localeCompare(b.name)).slice(0, TOP);
  }

  // The decks: game changers and legality problems against last week's email.
  const lastDecks = ((i.lastState.decks ?? {}) as unknown as Record<string, DeckState>) ?? {};
  const first = !i.lastState.decks;
  const nowDecks: Record<string, DeckState> = {};
  const decks: DeckChange[] = [];
  for (const sd of syncDecks(i.file)) {
    if (sd.deck.retired) continue;
    const main = sd.cards.filter((c) => !c.maybeboard);
    const gc = [...new Set(main.filter((c) => (i.gameChangers ? i.gameChangers.has(ruleKey(c.card.name)) : c.gameChanger)).map((c) => c.card.name))].sort();
    const legal = checkLegality(main.map((c) => ({ name: c.card.name, quantity: c.quantity, commander: c.commander })), i.rules);
    const problems = legal.problems.map((p) => p.text);
    const id = String(sd.deck.archidektId);
    nowDecks[id] = { gc, problems };
    const was = lastDecks[id];
    // New decks and the first email: the problems there are now; afterwards only what changed.
    const added = was ? gc.filter((n) => !was.gc.includes(n)) : [];
    const removed = was ? was.gc.filter((n) => !gc.includes(n)) : [];
    const fresh = was ? problems.filter((p) => !was.problems.includes(p)) : first || !was ? problems : [];
    if (added.length || removed.length || fresh.length) decks.push({ deck: sd.deck.name, added, removed, problems: fresh });
  }
  decks.sort((a, b) => a.deck.localeCompare(b.deck));

  // The sort's moves for this week: only once the first sort is done and the three binders are there.
  const p = prefs(i.file);
  const places = sortPlacesOf(p);
  const sortReady = sortSettledOf(p) && SORT_PLACES.every((place) => places[place] !== null);
  let sort: WeeklyReport["sort"] = null;
  if (sortReady) {
    // The moves' reasons in the owner's currency, as the sort page shows them (DKK has a standing rate with no file).
    const eurTo = cv.currency === "EUR" ? 1 : (i.prices?.rates.perEuro[cv.currency] ?? (cv.currency === "DKK" ? 7.46038 : 1));
    const moneyIn = (eur: number) => format(eur * eurTo, cv.currency);
    const { plan, binders } = sortSuggest(i.file, i.prices, i.details, i.ck, i.now, moneyIn);
    if (plan.moves.length > 0) {
      const nameOf = (place: string) => (place === UNSORTED_KEY ? "Unsorted" : binders[place as SortPlace]?.name ?? PLACE_LABELS[place as SortPlace]);
      sort = { moves: plan.moves.slice(0, SORT_MAIL_MOVES).map((m) => ({ name: m.name, from: nameOf(m.from), to: nameOf(m.to), copies: m.copies })) };
    }
  }

  const since = i.now - 7 * DAY_MS;
  return {
    currency: cv.currency,
    perEuro: cv.perEuro,
    priceLine: `${cv.sourceLabel} ${cv.priceTypeLabel.toLowerCase()}, in ${cv.currency}`,
    value: { now: value, before: beforePoint ? beforePoint.value : null, cards },
    gainers,
    losers,
    alerts: i.alerts.filter((a) => a.at >= since).sort((a, b) => b.at - a.at),
    decks,
    sort,
    state: { decks: nowDecks as unknown as Json },
  };
}

// ---- Writing it out ----

const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

export interface WeeklyMail {
  subject: string;
  text: string;
  html: string;
}

/** The email: plain text and HTML with the same content. [site] is the website's address, [stop] the stop link. */
export function weeklyMail(r: WeeklyReport, username: string, site: string, stop: string, now: number): WeeklyMail {
  const money = (eur: number) => format(eur * r.perEuro, r.currency);
  const signed = (eur: number) => `${eur >= 0 ? "+" : "−"}${money(Math.abs(eur))}`;
  const pct = (a: number, b: number) => (b > 0 ? ` (${a >= b ? "+" : "−"}${Math.abs(((a - b) / b) * 100).toFixed(1)}%)` : "");
  const finish = (f: string) => (f === "FOIL" ? ", foil" : f === "ETCHED" ? ", etched" : "");
  const day = new Date(now).toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric", timeZone: "Europe/Copenhagen" });
  const change = r.value.before != null ? r.value.now - r.value.before : null;
  const subject = `MTG Trader weekly: collection ${money(r.value.now)}${change != null ? ` (${signed(change)})` : ""}`;

  type Block = { title: string; lines: string[]; link?: { label: string; url: string } };
  const blocks: Block[] = [];
  blocks.push({
    title: "Your collection",
    lines: [
      `${r.value.cards} cards worth ${money(r.value.now)}.`,
      r.value.before != null ? `A week ago: ${money(r.value.before)}; ${signed(change!)}${pct(r.value.now, r.value.before)}.` : "The value a week ago isn't known yet.",
    ],
  });
  const mover = (m: Mover) => `${m.name} (${m.set}${finish(m.finish)}): ${money(m.before)} → ${money(m.now)}, ${signed(m.now - m.before)}${pct(m.now, m.before)} · you own ${m.copies}`;
  if (r.gainers === null) blocks.push({ title: "Gainers and losers", lines: ["These start next week, once the website has a week of daily prices."] });
  else {
    blocks.push({ title: "Up this week", lines: r.gainers.length ? r.gainers.map(mover) : ["No card you own went up this week."] });
    blocks.push({ title: "Down this week", lines: r.losers!.length ? r.losers!.map(mover) : ["No card you own went down this week."] });
  }
  if (r.alerts.length)
    blocks.push({ title: "Wishlist cards at your price", lines: r.alerts.map((a) => `${a.cardName}${a.setCode ? ` (${a.setCode.toUpperCase()})` : ""}: ${money(a.price)}, your alert ${money(a.level)}`) });
  if (r.decks.length)
    blocks.push({
      title: "Your decks",
      lines: r.decks.flatMap((d) => [
        ...(d.added.length ? [`${d.deck}: new game changer${d.added.length > 1 ? "s" : ""} ${d.added.join(", ")}; the bracket may have changed (refresh the deck to see).`] : []),
        ...(d.removed.length ? [`${d.deck}: no longer game changer${d.removed.length > 1 ? "s" : ""}: ${d.removed.join(", ")}.`] : []),
        ...d.problems.map((p) => `${d.deck}: ${p}`),
      ]),
    });
  if (r.sort?.moves.length)
    blocks.push({
      title: `Sort the collection: ${r.sort.moves.length} move${r.sort.moves.length === 1 ? "" : "s"} this week`,
      lines: r.sort.moves.map((m) => `${m.name}${m.copies > 1 ? ` (${m.copies} copies)` : ""}: ${m.from} → ${m.to}`),
      link: { label: "Open the sort", url: `${site}/sort` },
    });

  const foot = `Prices: ${r.priceLine}. Sent to ${username} on ${day}.`;
  const text = [
    `MTG Trader, the week of ${day}`,
    "",
    ...blocks.flatMap((b) => [b.title.toUpperCase(), ...b.lines.map((l) => `- ${l}`), ...(b.link ? [`- ${b.link.label}: ${b.link.url}`] : []), ""]),
    `Open MTG Trader: ${site}`,
    foot,
    `Stop these emails: ${stop}`,
  ].join("\n");
  const html = `<!doctype html><html><body style="margin:0;padding:0;background:#f4f2f7">
<div style="max-width:620px;margin:0 auto;padding:20px;font-family:Arial,Helvetica,sans-serif;color:#1f1a2e;font-size:15px;line-height:1.45">
<h1 style="font-size:20px;margin:0 0 4px">MTG <span style="color:#b07d10">Trader</span>, the week of ${esc(day)}</h1>
${blocks
  .map(
    (b) => `<h2 style="font-size:16px;margin:18px 0 6px;color:#5b3fa0">${esc(b.title)}</h2>
<ul style="margin:0;padding-left:20px">${b.lines.map((l) => `<li style="margin:3px 0">${esc(l)}</li>`).join("")}</ul>${
      b.link ? `\n<p style="margin:6px 0 0"><a href="${esc(b.link.url)}" style="color:#5b3fa0;font-weight:600">${esc(b.link.label)}</a></p>` : ""
    }`,
  )
  .join("\n")}
<p style="margin:22px 0 8px"><a href="${esc(site)}" style="background:#5b3fa0;color:#fff;padding:9px 16px;border-radius:6px;text-decoration:none">Open MTG Trader</a></p>
<p style="color:#6b6478;font-size:12px;margin-top:18px">${esc(foot)} <a href="${esc(stop)}" style="color:#6b6478">Stop these emails</a>.</p>
</div></body></html>`;
  return { subject, text, html };
}

/** A new settings row for [address] (not confirmed yet, weekly on [day]). */
export function newEmailSettings(address: string, day: number, token: () => string, had: EmailSettings | null): EmailSettings {
  const same = had && had.address.toLowerCase() === address.toLowerCase();
  return {
    address,
    confirmed: !!(same && had!.confirmed),
    enabled: true,
    day,
    confirmToken: same && had!.confirmed ? null : token(),
    stopToken: had?.stopToken ?? token(),
    lastSent: had?.lastSent ?? null,
    state: had?.state ?? {},
  };
}

/** A sensible email address (the provider checks the rest). */
export function cleanAddress(s: string): string | null {
  const a = s.trim();
  return a.length <= 200 && /^[^\s@<>,;"]+@[^\s@<>,;"]+\.[^\s@<>,;"]{2,}$/.test(a) ? a : null;
}
