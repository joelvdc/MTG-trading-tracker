import { json } from "@/lib/auth";
import { loadPrices } from "@/lib/pricestore";
import { WEB_FORMAT } from "@/lib/syncfile";

export const dynamic = "force-dynamic";

/** For the weekly health check: no secrets, no owner data. */
export async function GET() {
  const prices = await loadPrices();
  const ageHours = prices ? Math.round((Date.now() - prices.madeAt) / 3_600_000) : null;
  return json({
    ok: true,
    syncFormat: WEB_FORMAT,
    pricesCreatedAt: prices?.createdAt ?? null,
    pricesAgeHours: ageHours,
    pricesFresh: ageHours !== null && ageHours < 48,
    ratesDate: prices?.rates.date ?? null,
    configured: Boolean(process.env.SESSION_SECRET && process.env.NEXTCLOUD_SERVER && process.env.ALLOWED_USERS),
  });
}
