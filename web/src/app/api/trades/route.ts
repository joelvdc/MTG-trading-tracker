import { currentAccount, errorResponse, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { setupFor } from "@/lib/collview";
import { loadPrices } from "@/lib/pricestore";
import { loadSync } from "@/lib/store";
import { tradesView } from "@/lib/tradeview";

export const dynamic = "force-dynamic";

/** The trades, read only. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    return json(tradesView(file, setupFor(file, prices, details, ck), prices));
  } catch (e) {
    return errorResponse(e);
  }
}
