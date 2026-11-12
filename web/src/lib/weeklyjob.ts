// The weekly email job (/api/cron/weekly, every morning; each account gets its email on the weekday it chose) and the
// emails around it: the confirmation link, and "Send a test email now" in Settings.
import { accountStore, newId, type AccountStore, type EmailSettings } from "./accounts";
import { loadCardKingdom, type CkFile } from "./cardkingdom";
import { loadDetails, type DetailsFile } from "./carddetails";
import { setupFor } from "./collview";
import { USER_AGENT } from "./config";
import { syncDecks } from "./decks";
import { ruleKey } from "./legality";
import { deckLines, rulesFor } from "./legalstore";
import { sendMail, mailReady, type Send } from "./mailer";
import { sourceKey } from "./nightly";
import type { PriceFile } from "./prices";
import { loadPrices } from "./pricestore";
import { weeklySince } from "./pricehistory";
import { scryFetch } from "./scrylimit";
import { decode } from "./syncfile";
import { copenhagenDay, DAY_NAMES, weeklyMail, weeklyReport } from "./weekly";

/** The website's own address, for the links in emails. */
export function siteUrl(): string {
  return (process.env.SITE_URL ?? "https://mtgtrader.vercel.app").trim().replace(/\/+$/, "");
}

export const stopUrl = (s: EmailSettings) => `${siteUrl()}/api/email/stop?t=${encodeURIComponent(s.stopToken)}`;
export const confirmUrl = (token: string) => `${siteUrl()}/api/email/confirm?t=${encodeURIComponent(token)}`;
export const newEmailToken = () => newId() + newId();

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;

/** Wizards' Game Changers, from Scryfall (is:gamechanger), as front-face name keys; null when Scryfall didn't answer. */
export async function gameChangerNames(f: Fetch = scryFetch): Promise<Set<string> | null> {
  const names = new Set<string>();
  let next: string | null = `https://api.scryfall.com/cards/search?${new URLSearchParams({ q: "is:gamechanger", unique: "cards" })}`;
  try {
    for (let page = 0; next && page < 4; page++) {
      const r = await f(next, { headers: { Accept: "application/json", "User-Agent": USER_AGENT } });
      if (!r.ok) return null;
      const body = (await r.json()) as { data?: { name: string }[]; has_more?: boolean; next_page?: string };
      for (const c of body.data ?? []) names.add(ruleKey(c.name));
      next = body.has_more ? (body.next_page ?? null) : null;
    }
  } catch {
    return null;
  }
  return names.size ? names : null;
}

export interface WeeklyDeps {
  prices: () => Promise<PriceFile | null>;
  details: () => Promise<DetailsFile | null>;
  ck: () => Promise<CkFile | null>;
  gameChangers: () => Promise<Set<string> | null>;
  send: Send;
  /** Looks up Commander rules for the decks' cards (lib/legalstore.ts rulesFor). */
  rules: typeof rulesFor;
}

export const defaultWeeklyDeps = (): WeeklyDeps => ({ prices: loadPrices, details: () => loadDetails(), ck: loadCardKingdom, gameChangers: () => gameChangerNames(), send: sendMail, rules: rulesFor });

/** The account's weekly email as it would be sent now, and the state to keep for next week. */
export async function buildWeekly(store: AccountStore, accountId: number, settings: EmailSettings, deps: WeeklyDeps, now: number, shared?: { prices: PriceFile | null; details: DetailsFile | null; ck: CkFile | null; gameChangers: Set<string> | null }) {
  const [acc, got] = await Promise.all([store.byId(accountId), store.getSync(accountId)]);
  if (!acc || !got) return null;
  const file = decode(got.bytes);
  const s = shared ?? { prices: await deps.prices(), details: await deps.details(), ck: await deps.ck(), gameChangers: await deps.gameChangers() };
  const setup = setupFor(file, s.prices, s.details, s.ck);
  const lines = syncDecks(file).filter((d) => !d.deck.retired).flatMap(deckLines);
  const [priceDays, valuePoints, alerts, rules] = await Promise.all([
    store.priceDays(accountId, weeklySince(copenhagenDay(now).day)),
    store.valuePoints(accountId, sourceKey(setup.type, setup.source), null),
    store.alertEvents(accountId),
    deps.rules(lines, true, now, now + 15_000),
  ]);
  const report = weeklyReport({ file, prices: s.prices, details: s.details, ck: s.ck, priceDays, valuePoints, alerts, rules, gameChangers: s.gameChangers, lastState: settings.state, now });
  return { report, mail: weeklyMail(report, acc.username, siteUrl(), stopUrl(settings), now) };
}

export interface WeeklyResult {
  weekday: string;
  due: number;
  sent: number;
  failed: number;
  mailReady: boolean;
}

/** Sends today's weekly emails (Copenhagen's weekday); an account that had one in the last 20 hours is skipped. */
export async function runWeekly(now = Date.now(), deps: WeeklyDeps = defaultWeeklyDeps(), ready = mailReady()): Promise<WeeklyResult> {
  const store = accountStore();
  if (!store) throw new Error("No database.");
  const { weekday } = copenhagenDay(now);
  const due = (await store.emailAccounts()).filter(({ settings }) => settings.day === weekday && (settings.lastSent == null || now - settings.lastSent > 20 * 3_600_000));
  const result: WeeklyResult = { weekday: DAY_NAMES[weekday], due: due.length, sent: 0, failed: 0, mailReady: ready };
  if (ready && due.length) {
    const shared = { prices: await deps.prices(), details: await deps.details(), ck: await deps.ck(), gameChangers: await deps.gameChangers() };
    for (const { accountId, settings } of due) {
      try {
        const built = await buildWeekly(store, accountId, settings, deps, now, shared);
        if (!built) continue;
        await deps.send({ to: settings.address, ...built.mail, unsubscribe: stopUrl(settings) });
        await store.setEmailSettings(accountId, { ...settings, lastSent: now, state: built.report.state });
        result.sent++;
      } catch (e) {
        console.error(`Weekly email: account ${accountId} failed:`, e);
        result.failed++;
      }
    }
  }
  await store.setJob("weekly", now, { ...result });
  return result;
}

/** The email with the confirmation link. */
export function confirmMail(username: string, address: string, token: string, day: number) {
  const link = confirmUrl(token);
  const text = [
    `Hello ${username},`,
    "",
    `Confirm that MTG Trader may send its weekly email to ${address} (every ${DAY_NAMES[day]}):`,
    link,
    "",
    "If you didn't ask for this, just ignore this email: nothing will be sent.",
  ].join("\n");
  const html = `<!doctype html><html><body style="font-family:Arial,Helvetica,sans-serif;color:#1f1a2e;font-size:15px;line-height:1.45;max-width:560px;margin:0 auto;padding:20px">
<p>Hello ${username.replace(/[<>&"]/g, "")},</p>
<p>Confirm that MTG Trader may send its weekly email to ${address.replace(/[<>&"]/g, "")} (every ${DAY_NAMES[day]}):</p>
<p><a href="${link}" style="background:#5b3fa0;color:#fff;padding:9px 16px;border-radius:6px;text-decoration:none">Confirm</a></p>
<p style="color:#6b6478;font-size:12px">If you didn't ask for this, just ignore this email: nothing will be sent.</p>
</body></html>`;
  return { subject: "Confirm your MTG Trader weekly email", text, html };
}
