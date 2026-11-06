import { json } from "@/lib/auth";
import { rebuildTagCategories } from "@/lib/autocategories";

export const dynamic = "force-dynamic";
export const maxDuration = 300;

/**
 * Vercel runs this every day (vercel.json) with the CRON_SECRET; it rebuilds the auto categories table from
 * Scryfall's card tags when it is a week old (or empty) and does nothing the other days.
 */
export async function GET(req: Request) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.get("authorization") !== `Bearer ${secret}`) return json({ error: "Not allowed." }, 401);
  try {
    return json({ ok: true, ...(await rebuildTagCategories()) });
  } catch (e) {
    console.error(e);
    return json({ ok: false, error: (e as Error).message }, 500);
  }
}
