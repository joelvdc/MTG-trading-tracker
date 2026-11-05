import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { addDetails, BATCH, validIds } from "@/lib/carddetails";

export const dynamic = "force-dynamic";

/**
 * Looks up colours, type, mana value… for up to 75 printings ({ ids }) on Scryfall, once: the answers are kept for
 * every later visit. The page sends the printings it's missing a batch at a time.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  if (!(await currentAccount())) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { ids?: unknown };
    const ids = validIds(body?.ids).slice(0, BATCH);
    return json({ cards: await addDetails(ids) });
  } catch (e) {
    return errorResponse(e);
  }
}
