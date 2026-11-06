import { errorResponse, fromOurPages, json } from "@/lib/auth";
import { fields, logBrowserIn, needStore } from "@/lib/accountweb";
import { register } from "@/lib/users";

export const dynamic = "force-dynamic";

/** A new account with an invite code from the owner; this browser is then logged in with it. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const { invite, username, password } = await fields(req, ["invite", "username", "password"]);
    const store = needStore();
    const now = Date.now();
    const a = await register(store, invite, username, password, now);
    await logBrowserIn(store, a, req, now);
    return json({ ok: true });
  } catch (e) {
    return errorResponse(e);
  }
}
