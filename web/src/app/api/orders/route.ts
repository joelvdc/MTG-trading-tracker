import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { applyOrderEdit, type OrderEdit } from "@/lib/orders";
import { ordersView } from "@/lib/ordersview";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";

/** The order history with what each order is worth now. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    return json(ordersView(file, prices, details, ck));
  } catch (e) {
    return errorResponse(e);
  }
}

/** One change to an order ({ edit }): its date, or deleting it. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { edit?: OrderEdit };
    if (!body?.edit || typeof body.edit !== "object") return json({ error: "No change sent." }, 400);
    const [prices, details, ck] = await Promise.all([loadPrices(), loadDetails(), loadCardKingdom()]);
    const file = await editSync(a, (f, now) => {
      applyOrderEdit(f, body.edit!, now);
      return f;
    });
    return json(ordersView(file, prices, details, ck));
  } catch (e) {
    return errorResponse(e);
  }
}
