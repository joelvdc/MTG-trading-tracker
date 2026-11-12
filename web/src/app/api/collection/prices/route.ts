import { currentAccount, errorResponse, json } from "@/lib/auth";
import { accountStore } from "@/lib/accounts";
import { isUser } from "@/lib/session";
import { pileKey } from "@/lib/weekly";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { cardPrices, setupFor } from "@/lib/collview";
import { loadPrices } from "@/lib/pricestore";
import { loadSync } from "@/lib/store";
import { collection } from "@/lib/syncfile";
import { finishOf } from "@/lib/pricing";

export const dynamic = "force-dynamic";

/** The prices in a stack's card window (?uid=…): Cardmarket's six figures, the other sources', and its saved daily prices. */
export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  const uid = new URL(req.url).searchParams.get("uid") ?? "";
  try {
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    const s = collection(file).find((x) => x.item.uid === uid);
    if (!s) return json({ error: "Those cards aren't in the collection any more." }, 404);
    const setup = setupFor(file, prices, details, ck);
    const finish = finishOf(s.item.foil, s.item.etched);
    const store = accountStore();
    const history = store && isUser(a) ? await store.priceHistory(a.accountId, pileKey(s.item.card.scryfallId, finish, s.item.condition)).catch(() => []) : [];
    return json({ ...cardPrices(setup, s.item.card, finish, s.item.condition), history });
  } catch (e) {
    return errorResponse(e);
  }
}
