import { listModels, websiteAccesses, type AiAccess } from "@/lib/aiprimer";
import { AI_PROVIDERS, type AiProvider } from "@/lib/aiprimershared";
import { json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { accountStore } from "@/lib/accounts";
import { loadPreconIndex } from "@/lib/preconindex";
import { loadPrices } from "@/lib/pricestore";
import { mailReady } from "@/lib/mailer";
import { serviceNotes } from "@/lib/servicewatch";
import { WEB_FORMAT } from "@/lib/syncfile";


export const dynamic = "force-dynamic";

const hours = (ms: number | null | undefined) => (ms ? Math.round((Date.now() - ms) / 3_600_000) : null);

const AI_CHECK_JOB = "ai-check";
const AI_CHECK_MS = 3_600_000;

type AiServiceHealth = { provider: AiProvider; reachable: boolean; hasDefault: boolean };

/** One website key: whether its service answers and lists its own default model. */
async function serviceHealth(access: AiAccess): Promise<AiServiceHealth> {
  try {
    const models = await listModels(access);
    return { provider: access.provider, reachable: true, hasDefault: models.includes(AI_PROVIDERS[access.provider].defaultModel) };
  } catch {
    // The service didn't answer: that is kept for the hour too, so the health check doesn't wait on it every time.
    return { provider: access.provider, reachable: false, hasDefault: false };
  }
}

/**
 * Whether every key the website has answers and lists its service's default model, looked at most once an hour across
 * the instances (the last answer is in the database). Only the model lists are asked; no chat address is ever called
 * from here. The answer's provider, reachable and hasDefault are the first service's (the one the owner starts on).
 */
async function aiHealth(store: ReturnType<typeof accountStore>) {
  // The website's own keys, which only the owner's account may use: the health check has no account.
  const accesses = websiteAccesses();
  if (accesses.length === 0) return { configured: false, provider: null, reachable: false, hasDefault: false, services: [], checkedAgeHours: null };
  const wanted = accesses.map((a) => a.provider);
  const last = store ? await store.job(AI_CHECK_JOB).catch(() => null) : null;
  // A kept answer is only reused when it was about the same services, in the same order: another key checks again.
  const held = Array.isArray(last?.info.services) ? (last!.info.services as { provider?: unknown; reachable?: unknown; hasDefault?: unknown }[]) : null;
  const same = held !== null && held.length === wanted.length && held.every((s, i) => s !== null && typeof s === "object" && s.provider === wanted[i]);
  if (last && held !== null && same && Date.now() - last.at <= AI_CHECK_MS) {
    const services = held.map((s, i) => ({ provider: wanted[i], reachable: s.reachable === true, hasDefault: s.hasDefault === true }));
    return {
      configured: true,
      provider: services[0].provider,
      reachable: services[0].reachable,
      hasDefault: services[0].hasDefault,
      services,
      checkedAgeHours: hours(last.at),
    };
  }
  const services = await Promise.all(accesses.map((access) => serviceHealth(access)));
  const at = Date.now();
  if (store) await store.setJob(AI_CHECK_JOB, at, { services }).catch(() => undefined);
  return { configured: true, provider: services[0].provider, reachable: services[0].reachable, hasDefault: services[0].hasDefault, services, checkedAgeHours: 0 };
}

/** For the weekly health check: no secrets, no owner data. */
export async function GET() {
  const store = accountStore();
  const [prices, details, ck, accounts, precons] = await Promise.all([
    loadPrices(),
    loadDetails(),
    loadCardKingdom(),
    store ? store.count().catch(() => null) : Promise.resolve(null),
    loadPreconIndex(),
  ]);
  const tags = store ? await store.tagCategoryStats().catch(() => null) : null;
  const nightly = store ? await store.job("nightly").catch(() => null) : null;
  const winturns = store ? await store.job("winturns").catch(() => null) : null;
  const weekly = store ? await store.job("weekly").catch(() => null) : null;
  const priceDays = store ? await store.priceDayStats().catch(() => null) : null;
  const viewAccounts = store ? await store.savedViewAccounts().catch(() => null) : null;
  const ai = await aiHealth(store);
  const ageHours = hours(prices?.madeAt);
  const detailsAge = hours(details.madeAt);
  const ckAge = hours(ck?.madeAt);
  const preconsAge = hours(precons?.madeAt);
  return json({
    ok: true,
    syncFormat: WEB_FORMAT,
    pricesCreatedAt: prices?.createdAt ?? null,
    pricesAgeHours: ageHours,
    pricesFresh: ageHours !== null && ageHours < 48,
    ratesDate: prices?.rates.date ?? null,
    cardDetails: Object.keys(details.cards).length,
    cardDetailsAgeHours: detailsAge,
    cardKingdomRows: ck ? Object.keys(ck.rows).length : 0,
    cardKingdomListDate: ck?.listDate ?? null,
    cardKingdomAgeHours: ckAge,
    cardKingdomFresh: ckAge !== null && ckAge < 48,
    // The precon index (the precons page's tiles): how many decks it has, its age, and how many of MTGJSON's list it lacks.
    preconIndexDecks: precons?.decks.length ?? 0,
    preconIndexAgeHours: preconsAge,
    preconIndexMissing: precons ? precons.listCount - precons.decks.length : null,
    preconIndexFresh: preconsAge !== null && preconsAge < 48,
    // The accounts database (step C): reachable, and how many accounts (no names).
    database: accounts !== null,
    accounts,
    // The auto categories table (Scryfall's card tags, rebuilt weekly): how many cards, and its age.
    categoryCards: tags?.cards ?? null,
    categoriesAgeHours: hours(tags?.updatedAt),
    categoriesFresh: tags?.updatedAt ? Date.now() - tags.updatedAt < 9 * 86_400_000 : false,
    // The nightly job (step D): when it last ran and its counts (no names).
    nightlyAgeHours: hours(nightly?.at),
    nightlyFresh: nightly ? Date.now() - nightly.at < 36 * 3_600_000 : false,
    nightly: nightly?.info ?? null,
    // The daily win-turn job (decks' win turns worked out on the website): when it last ran and its counts.
    winTurnsAgeHours: hours(winturns?.at),
    winTurns: winturns?.info ?? null,
    // The weekly email (app 1.89): whether sending is set up (Resend key and sender), and the daily job's last run.
    emailReady: mailReady(),
    weeklyAgeHours: hours(weekly?.at),
    weeklyFresh: weekly ? Date.now() - weekly.at < 36 * 3_600_000 : false,
    weekly: weekly?.info ?? null,
    // Each pile's daily price (since 2026-11-13 kept up to a year, for the card window's chart): saved days over all
    // accounts, the oldest (moves back over time) and the newest (should be today or yesterday).
    priceHistoryRows: priceDays?.rows ?? null,
    priceHistoryOldest: priceDays?.oldest ?? null,
    priceHistoryNewest: priceDays?.newest ?? null,
    // The Collection page's saved views (2026-11-13): the table answers (null: it doesn't), and how many accounts use it.
    savedViewAccounts: viewAccounts,
    // The AI deck primers (Mistral and/or OpenCode Go, the website's own keys): set up, which services, each one answering
    // and whether its model list names its default model; the top-level fields are the first service's.
    ai,
    // EDHREC and Archidekt aren't fetched by the health check (their terms forbid automated requests): how the website's
    // own requests for people went (since app 1.105).
    services: await serviceNotes(Date.now(), store),
    configured: Boolean(process.env.SESSION_SECRET && process.env.NEXTCLOUD_SERVER && process.env.ALLOWED_USERS),
  });
}
