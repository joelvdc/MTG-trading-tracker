import { json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { accountStore } from "@/lib/accounts";
import { loadPrices } from "@/lib/pricestore";
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
    configured: Boolean(process.env.SESSION_SECRET && process.env.NEXTCLOUD_SERVER && process.env.ALLOWED_USERS),
  });
}
