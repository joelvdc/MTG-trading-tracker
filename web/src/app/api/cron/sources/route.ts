import { json } from "@/lib/auth";
import { buildCardKingdom } from "@/lib/cardkingdom";
import { refreshDetails } from "@/lib/carddetails";
import { buildPreconIndex } from "@/lib/preconindex";

export const dynamic = "force-dynamic";
export const maxDuration = 300;

/**
 * Vercel runs this once a day (vercel.json) with the CRON_SECRET: Card Kingdom's prices (the GitHub copy), fresh
 * Scryfall details and TCGplayer prices for every card the website has looked up, and the precon index's missing decks.
 */
export async function GET(req: Request) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.get("authorization") !== `Bearer ${secret}`) return json({ error: "Not allowed." }, 401);
  const start = Date.now();
  const out: Record<string, unknown> = {};
  let ok = true;
  try {
    out.cardKingdom = await buildCardKingdom();
  } catch (e) {
    console.error(e);
    ok = false;
    out.cardKingdomError = (e as Error).message;
  }
  try {
    out.precons = await buildPreconIndex(Date.now() + 50_000);
  } catch (e) {
    console.error(e);
    ok = false;
    out.preconsError = (e as Error).message;
  }
  try {
    out.details = await refreshDetails(Math.min(Date.now() + 240_000, start + 280_000));
  } catch (e) {
    console.error(e);
    ok = false;
    out.detailsError = (e as Error).message;
  }
  return json({ ok, ...out }, ok ? 200 : 500);
}
