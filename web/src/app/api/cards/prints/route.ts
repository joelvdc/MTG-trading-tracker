import { currentAccount, errorResponse, json } from "@/lib/auth";
import { loadPrices } from "@/lib/pricestore";
import { priceOf } from "@/lib/prices";
import { printings } from "@/lib/scryfall";

export const dynamic = "force-dynamic";

/** Every paper printing of a card, with its Cardmarket trend price for each finish (euros). */
export async function GET(req: Request) {
  if (!(await currentAccount())) return json({ error: "Log in first.", loggedOut: true }, 401);
  const name = new URL(req.url).searchParams.get("name")?.trim() ?? "";
  if (!name || name.length > 200) return json({ results: [] });
  try {
    const [found, prices] = await Promise.all([printings(name), loadPrices()]);
    return json({
      results: found.map((f) => ({
        ...f,
        eur: f.card.hasNonFoil ? priceOf(f.card, false, "trend", prices).eur : null,
        eurFoil: f.card.hasFoil || f.card.hasEtched ? priceOf(f.card, true, "trend", prices).eur : null,
      })),
    });
  } catch (e) {
    return errorResponse(e);
  }
}
