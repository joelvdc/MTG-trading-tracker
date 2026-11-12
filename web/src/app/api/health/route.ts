import { json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { accountStore } from "@/lib/accounts";
import { loadPrices } from "@/lib/pricestore";
import { mailReady } from "@/lib/mailer";
import { WEB_FORMAT } from "@/lib/syncfile";


export const dynamic = "force-dynamic";

const hours = (ms: number | null | undefined) => (ms ? Math.round((Date.now() - ms) / 3_600_000) : null);

/** For the weekly health check: no secrets, no owner data. */
export async function GET() {
  const store = accountStore();
  const [prices, details, ck, accounts] = await Promise.all([
    loadPrices(),
    loadDetails(),
    loadCardKingdom(),
    store ? store.count().catch(() => null) : Promise.resolve(null),
  ]);
  const tags = store ? await store.tagCategoryStats().catch(() => null) : null;
  const nightly = store ? await store.job("nightly").catch(() => null) : null;
  const winturns = store ? await store.job("winturns").catch(() => null) : null;
  const weekly = store ? await store.job("weekly").catch(() => null) : null;
  const priceDays = store ? await store.priceDayStats().catch(() => null) : null;
  const ageHours = hours(prices?.madeAt);
  const detailsAge = hours(details.madeAt);
  const ckAge = hours(ck?.madeAt);
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
    configured: Boolean(process.env.SESSION_SECRET && process.env.NEXTCLOUD_SERVER && process.env.ALLOWED_USERS),
  });
}
