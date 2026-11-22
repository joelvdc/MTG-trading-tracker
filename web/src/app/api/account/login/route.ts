import { errorResponse, fromOurPages, json } from "@/lib/auth";
import { fields, logBrowserIn, needStore } from "@/lib/accountweb";
import { loginStep } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Logs this browser in with a website account's user name and password (and, with two-step login on, its code). */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const { username, password, code } = await fields(req, ["username", "password", "code"]);
    const store = needStore();
    const now = Date.now();
    // The page asks for the code in a second try; a wrong code is counted from one try to the next.
    const a = await loginStep(store, username, password, code, now);
    if (!a) return json({ needCode: true });
    await logBrowserIn(store, a, req, now);
    return json({ ok: true });
  } catch (e) {
    return errorResponse(e);
  }
}
