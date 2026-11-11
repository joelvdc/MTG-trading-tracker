import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { needStore } from "@/lib/accountweb";
import { isUser } from "@/lib/session";
import { makeShare, NotSynced, shareLines, shareUrl } from "@/lib/shareweb";
import { validCode } from "@/lib/shares";
import { loadSync, NoAccountFile } from "@/lib/store";

export const dynamic = "force-dynamic";

/** Settings → Shared links: the account's links, with what each shows (null when the item is gone). */
export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const store = needStore();
    const links = await store.shares(a.accountId);
    const file = await loadSync(a).then((r) => r.file, (e) => (e instanceof NoAccountFile ? null : Promise.reject(e)));
    return json({ links: shareLines(req, links, file) });
  } catch (e) {
    return errorResponse(e);
  }
}

/**
 * {action: "make", kind, target}: the item's link (made, or switched on again); {action: "on", code, on}: switches a
 * link on or off; {action: "delete", code}: forgets it (its address stops working for good).
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const store = needStore();
    const body = (await req.json().catch(() => ({}))) as { action?: string; kind?: string; target?: string; code?: string; on?: boolean };
    if (body.action === "make") {
      const { file } = await loadSync(a);
      const { link, turnedOn } = await makeShare(store, a.accountId, file, String(body.kind ?? ""), String(body.target ?? ""), Date.now());
      return json({ url: shareUrl(req, link.code), code: link.code, on: true, turnedOn });
    }
    const code = String(body.code ?? "");
    if (!validCode(code)) return json({ error: "No such link." }, 404);
    if (body.action === "on") {
      if (!(await store.setShareOn(a.accountId, code, body.on === true))) return json({ error: "No such link." }, 404);
      return json({ ok: true });
    }
    if (body.action === "delete") {
      if (!(await store.removeShare(a.accountId, code))) return json({ error: "No such link." }, 404);
      return json({ ok: true });
    }
    return json({ error: "Unknown action." }, 400);
  } catch (e) {
    if (e instanceof NotSynced) return json({ error: e.message }, 404);
    return errorResponse(e);
  }
}
