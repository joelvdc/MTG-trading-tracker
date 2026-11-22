// Settings → Two-step login: an authenticator app's codes, asked at login (website and app) once it's on. The secret
// is kept sealed with SESSION_SECRET and only ever shown once, while it is being set up.
import { needStore } from "@/lib/accountweb";
import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { isUser } from "@/lib/session";
import { confirmTwoFactor, startTwoFactor, stopTwoFactor } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Whether this account has two-step login on, and how many recovery codes it has left. */
export async function GET() {
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const tf = await needStore().twoFactor(a.accountId);
    return json({ on: !!tf?.enabled, recoveryLeft: tf?.enabled ? tf.recovery.length : 0 });
  } catch (e) {
    return errorResponse(e);
  }
}

/**
 * {action: "start"}: a new secret and its QR code → {secret, otpauth, qr}; {action: "confirm", code}: turns it on and
 * answers the {codes} to keep; {action: "off", password, code}: turns it off.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const store = needStore();
    const account = await store.byId(a.accountId);
    if (!account) return json({ error: "Log in first.", loggedOut: true }, 401);
    const body = (await req.json().catch(() => ({}))) as { action?: unknown; code?: unknown; password?: unknown };
    const code = typeof body.code === "string" ? body.code.slice(0, 40) : "";
    const password = typeof body.password === "string" ? body.password : "";
    const now = Date.now();
    if (body.action === "start") return json(await startTwoFactor(store, account, now));
    if (body.action === "confirm") return json({ codes: await confirmTwoFactor(store, account, code, now) });
    if (body.action === "off") {
      await stopTwoFactor(store, account, password, code, now);
      return json({ ok: true });
    }
    return json({ error: "Unknown action." }, 400);
  } catch (e) {
    return errorResponse(e);
  }
}
