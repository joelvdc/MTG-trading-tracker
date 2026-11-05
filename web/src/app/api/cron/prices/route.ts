import { json } from "@/lib/auth";
import { buildPrices } from "@/lib/pricestore";

export const dynamic = "force-dynamic";
export const maxDuration = 120;

/** Vercel runs this once a day (vercel.json) and sends the CRON_SECRET, so nobody else can start it. */
export async function GET(req: Request) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.get("authorization") !== `Bearer ${secret}`) return json({ error: "Not allowed." }, 401);
  try {
    return json({ ok: true, ...(await buildPrices()) });
  } catch (e) {
    console.error(e);
    return json({ ok: false, error: (e as Error).message }, 500);
  }
}
