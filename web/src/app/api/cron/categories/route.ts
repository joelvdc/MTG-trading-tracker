import { json } from "@/lib/auth";
import { rebuildTagCategories } from "@/lib/autocategories";
import { rebuildCardFacts } from "@/lib/cardfacts";

export const dynamic = "force-dynamic";
export const maxDuration = 300;

/**
 * Vercel runs this every day (vercel.json) with the CRON_SECRET; it rebuilds the auto categories table from
 * Scryfall's card tags when it is a week old (or empty) and does nothing the other days. The card facts table
 * (lib/cardfacts.ts) is rebuilt on those other days instead, so a day does one of the two, both within the 300 s; a
 * failure in the facts never fails the categories.
 */
export async function GET(req: Request) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.get("authorization") !== `Bearer ${secret}`) return json({ error: "Not allowed." }, 401);
  let categories: { rebuilt: boolean; cards: number; updatedAt: number | null };
  try {
    categories = await rebuildTagCategories();
  } catch (e) {
    console.error(e);
    return json({ ok: false, error: (e as Error).message }, 500);
  }
  // The categories were rebuilt: that filled the 300 s already, so the facts wait for tomorrow's run.
  if (categories.rebuilt) return json({ ok: true, categories });
  try {
    return json({ ok: true, categories, facts: await rebuildCardFacts() });
  } catch (e) {
    console.error(e);
    return json({ ok: true, categories, facts: { error: (e as Error).message } });
  }
}
