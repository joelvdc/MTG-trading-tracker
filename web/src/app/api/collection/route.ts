import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { applyCollectionEdit, type CollectionEdit } from "@/lib/collection";
import { collectionView } from "@/lib/collview";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";

export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    return json(collectionView(file, prices, details, ck));
  } catch (e) {
    return errorResponse(e);
  }
}

/** One edit ({ edit: CollectionEdit }); answers with the whole collection as it is now, and the uid the edit ended in. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { edit?: CollectionEdit };
    if (!body?.edit || typeof body.edit !== "object") return json({ error: "No change sent." }, 400);
    const [prices, details, ck] = await Promise.all([loadPrices(), loadDetails(), loadCardKingdom()]);
    const { file, uid } = await editSync(a, (f, now) => ({ file: f, uid: applyCollectionEdit(f, body.edit!, now, prices?.rates ?? null).uid }));
    return json({ ...collectionView(file, prices, details, ck), editedUid: uid });
  } catch (e) {
    return errorResponse(e);
  }
}
