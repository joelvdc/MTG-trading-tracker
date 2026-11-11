import { json } from "@/lib/auth";
import { runWeekly } from "@/lib/weeklyjob";

export const dynamic = "force-dynamic";
export const maxDuration = 300;

/**
 * Every morning (vercel.json, 07:30 UTC, after the nightly job): the weekly emails of the accounts that chose today
 * (lib/weeklyjob.ts). Vercel sends the CRON_SECRET, so nobody else can start it.
 */
export async function GET(req: Request) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.get("authorization") !== `Bearer ${secret}`) return json({ error: "Not allowed." }, 401);
  try {
    const r = await runWeekly();
    return json({ ok: r.failed === 0, ...r });
  } catch (e) {
    console.error("Weekly email job failed:", e);
    return json({ ok: false, error: e instanceof Error ? e.message : String(e) }, 500);
  }
}
