import { after } from "next/server";
import { accountStore } from "@/lib/accounts";
import { json } from "@/lib/auth";
import { runAllWinTurns } from "@/lib/winturns";

export const dynamic = "force-dynamic";
export const maxDuration = 300;

/** How many times a run hands the rest to a new one (each about four and a half minutes, three to six decks). */
const MAX_LINKS = 12;

/**
 * Every day (vercel.json, 07:00 UTC) with the CRON_SECRET: works out the win turn of every deck whose list changed
 * (lib/winturns.ts). It answers at once and works afterwards; when time runs out with decks left, it starts itself
 * again for the rest (?link=n), up to MAX_LINKS times.
 */
export async function GET(req: Request) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.get("authorization") !== `Bearer ${secret}`) return json({ error: "Not allowed." }, 401);
  const url = new URL(req.url);
  const link = Math.max(0, Math.min(MAX_LINKS, Number(url.searchParams.get("link")) || 0));
  const started = Date.now();
  after(async () => {
    try {
      const r = await runAllWinTurns(started + 270_000);
      await accountStore()?.setJob("winturns", Date.now(), { ...r, link });
      if (r.left > 0 && r.done > 0 && link < MAX_LINKS) {
        const next = new URL(url.pathname, url.origin);
        next.searchParams.set("link", String(link + 1));
        await fetch(next, { headers: { authorization: `Bearer ${secret}` }, signal: AbortSignal.timeout(20_000) }).catch((e) => console.warn(`Win turns: next run didn't start: ${e}`));
      }
    } catch (e) {
      console.error("Win turns job failed:", e);
    }
  });
  return json({ ok: true, started: true, link });
}
