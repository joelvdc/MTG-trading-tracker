import { errorResponse, fromOurPages, json } from "@/lib/auth";
import { fields, logBrowserIn, needStore } from "@/lib/accountweb";
import { checkLogin } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Logs this browser in with a website account's user name and password. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const { username, password } = await fields(req, ["username", "password"]);
    const store = needStore();
    const now = Date.now();
    const a = await checkLogin(store, username, password, now);
    await logBrowserIn(store, a, req, now);
    return json({ ok: true });
  } catch (e) {
    return errorResponse(e);
  }
}
