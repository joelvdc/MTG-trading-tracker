import { currentAccount, errorResponse, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { cardPrices, setupFor } from "@/lib/collview";
import { loadPrices } from "@/lib/pricestore";
import { loadSync } from "@/lib/store";
import { collection } from "@/lib/syncfile";
import { finishOf } from "@/lib/pricing";

export const dynamic = "force-dynamic";

/** The prices in a stack's card window (?uid=…): Cardmarket's six figures and the other sources'. */
export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  const uid = new URL(req.url).searchParams.get("uid") ?? "";
  try {
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    const s = collection(file).find((x) => x.item.uid === uid);
    if (!s) return json({ error: "Those cards aren't in the collection any more." }, 404);
    const setup = setupFor(file, prices, details, ck);
    return json(cardPrices(setup, s.item.card, finishOf(s.item.foil, s.item.etched), s.item.condition));
  } catch (e) {
    return errorResponse(e);
  }
}
