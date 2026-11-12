import { errorResponse, json } from "@/lib/auth";
import { loggedOut, needStore, phoneAccount } from "@/lib/accountweb";
import { makeShare, NotSynced, shareUrl } from "@/lib/shareweb";
import { decode } from "@/lib/syncfile";

export const dynamic = "force-dynamic";

/**
 * A phone's Share button (app 1.89): {kind: "deck"|"game"|"binder"|"collection"|"wishlist" (app 1.91), target} → {url, on: true}. The
 * item's link is made, or switched on again; 404 when the website doesn't have the item (the phone syncs first).
 */
export async function POST(req: Request) {
  const p = await phoneAccount(req);
  if (!p) return loggedOut();
  try {
    const body = (await req.json().catch(() => ({}))) as { kind?: unknown; target?: unknown };
    const store = needStore();
    const got = await store.getSync(p.account.id);
    if (!got) return json({ error: "This account has no collection on the website yet. Sync first." }, 404);
    const { link } = await makeShare(store, p.account.id, decode(got.bytes), String(body.kind ?? ""), String(body.target ?? ""), Date.now());
    return json({ url: shareUrl(req, link.code), on: true });
  } catch (e) {
    if (e instanceof NotSynced) return json({ error: e.message }, 404);
    return errorResponse(e);
  }
}
