import { json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { accountStore } from "@/lib/accounts";
import { loadPrices } from "@/lib/pricestore";
import { WEB_FORMAT } from "@/lib/syncfile";
import { fromArchidekt } from "@/lib/autocategories";

/** Cultivate (C21), whose Archidekt category is Ramp: for ?archidekt=1. */
const CULTIVATE = "a78f5d3f-74d6-4ec1-89c4-e24639d3f4ba";

export const dynamic = "force-dynamic";

const hours = (ms: number | null | undefined) => (ms ? Math.round((Date.now() - ms) / 3_600_000) : null);

/** For the weekly health check: no secrets, no owner data. With ?archidekt=1 also one Archidekt card lookup. */
export async function GET(req: Request) {
  const store = accountStore();
  const [prices, details, ck, accounts] = await Promise.all([
    loadPrices(),
    loadDetails(),
    loadCardKingdom(),
    store ? store.count().catch(() => null) : Promise.resolve(null),
  ]);
  const cardCategories = store ? await store.countCardCategories().catch(() => null) : null;
  let archidekt: string | undefined;
  if (new URL(req.url).searchParams.get("archidekt") === "1") {
    archidekt = await fromArchidekt([CULTIVATE], Date.now())
      .then((r) => r[0]?.category ?? "no answer for Cultivate")
      .catch((e) => (e instanceof Error ? e.message : "failed"));
  }
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
    // Printings with a looked-up auto category (Archidekt / Scryfall tags), kept in the database.
    cardCategories,
    ...(archidekt !== undefined ? { archidekt } : {}),
    configured: Boolean(process.env.SESSION_SECRET && process.env.NEXTCLOUD_SERVER && process.env.ALLOWED_USERS),
  });
}
