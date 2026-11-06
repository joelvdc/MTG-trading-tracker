import { json } from "@/lib/auth";
import { runNightly } from "@/lib/nightly";

export const dynamic = "force-dynamic";
export const maxDuration = 300;

/**
 * Once a night (vercel.json, 06:30 UTC, after the price jobs): every account's collection value for the day and its
 * wishlist price alerts (lib/nightly.ts). Vercel sends the CRON_SECRET, so nobody else can start it.
 */
export async function GET(req: Request) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.get("authorization") !== `Bearer ${secret}`) return json({ error: "Not allowed." }, 401);
  try {
    const r = await runNightly();
    return json({ ok: r.failed === 0, ...r });
  } catch (e) {
    console.error("Nightly job failed:", e);
    return json({ ok: false, error: e instanceof Error ? e.message : String(e) }, 500);
  }
}
