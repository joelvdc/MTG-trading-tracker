import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { wishView } from "@/lib/view";
import { applyReceived, RECEIVED_TYPES, type ReceivedEdit } from "@/lib/received";
import { applyEdit, type WishEdit } from "@/lib/wishlist";

export const dynamic = "force-dynamic";

export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, prices] = await Promise.all([loadSync(a), loadPrices()]);
    return json(wishView(file, prices));
  } catch (e) {
    return errorResponse(e);
  }
}

/** One edit ({ edit: WishEdit }); answers with the whole wishlist as it is now. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { edit?: WishEdit | ReceivedEdit };
    if (!body?.edit || typeof body.edit !== "object") return json({ error: "No change sent." }, 400);
    const prices = await loadPrices();
    const e = body.edit;
    const file = await editSync(a, (f, now) => {
      if (RECEIVED_TYPES.includes(e.type)) applyReceived(f, e as ReceivedEdit, now, prices?.rates ?? null);
      else applyEdit(f, e as WishEdit, now);
      return f;
    });
    return json(wishView(file, prices));
  } catch (e) {
    return errorResponse(e);
  }
}
