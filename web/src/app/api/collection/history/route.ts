import { currentAccount, errorResponse, json } from "@/lib/auth";
import { accountStore } from "@/lib/accounts";
import { sourceKey } from "@/lib/nightly";
import { priceType } from "@/lib/prices";
import { source } from "@/lib/pricing";
import { isUser } from "@/lib/session";
import { loadSync } from "@/lib/store";
import { prefs } from "@/lib/syncfile";

export const dynamic = "force-dynamic";

/**
 * The collection's value over time (?binder=<uid>, "~" for Unsorted, none for the whole collection), at the app's
 * price type and source: the days the nightly job saved, and the phone's history sent once by the app.
 */
export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  if (!isUser(a)) return json({ points: [], available: false });
  try {
    const store = accountStore();
    if (!store) return json({ points: [], available: false });
    const { file } = await loadSync(a);
    const p = prefs(file);
    const key = sourceKey(priceType(p.priceType), source(p.priceSource));
    const binder = new URL(req.url).searchParams.get("binder") || null;
    let points = await store.valuePoints(a.accountId, key, binder);
    // Days saved before a source had prices: Cardmarket's value stands in, as on the phone's chart.
    if (points.length === 0 && key.includes("@")) points = await store.valuePoints(a.accountId, priceType(p.priceType), binder);
    return json({ points, available: true });
  } catch (e) {
    return errorResponse(e);
  }
}
