import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { setupFor } from "@/lib/collview";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { applyTradeEdit, type TradeEdit } from "@/lib/tradeedit";
import { tradesView } from "@/lib/tradeview";

export const dynamic = "force-dynamic";

/** The trades. */
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

/** One change ({ edit: TradeEdit }); answers with all trades as they are now, the trade's uid and, completing, the copies not found. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { edit?: TradeEdit };
    if (!body?.edit || typeof body.edit !== "object") return json({ error: "No change sent." }, 400);
    const [prices, details, ck] = await Promise.all([loadPrices(), loadDetails(), loadCardKingdom()]);
    const { file, result } = await editSync(a, (f, now) => ({ file: f, result: applyTradeEdit(f, body.edit!, now, prices) }));
    return json({ ...tradesView(file, setupFor(file, prices, details, ck), prices), editedUid: result.uid, missing: result.missing ?? null, removed: result.removed ?? null });
  } catch (e) {
    return errorResponse(e);
  }
}
