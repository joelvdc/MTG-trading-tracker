import { currentAccount, errorResponse, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { decksView } from "@/lib/decks";
import { loadPrices } from "@/lib/pricestore";
import { loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";

/** The decks list (the app's Decks tab). */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    return json(decksView(file, prices, details, ck));
  } catch (e) {
    return errorResponse(e);
  }
}
