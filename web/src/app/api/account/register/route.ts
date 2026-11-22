import { errorResponse, fromOurPages, json } from "@/lib/auth";
import { fields, logBrowserIn, needStore } from "@/lib/accountweb";
import { mailReady, sendMail } from "@/lib/mailer";
import { register } from "@/lib/users";
import { accountConfirmMail } from "@/lib/weeklyjob";

export const dynamic = "force-dynamic";

/** A new account with an invite code and an email address from the owner; this browser is then logged in with it. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const { invite, username, password, email } = await fields(req, ["invite", "username", "password", "email"]);
    const store = needStore();
    const now = Date.now();
    const a = await register(store, invite, username, password, email, now);
    const s = await store.emailSettings(a.id);
    await logBrowserIn(store, a, req, now);
    // The confirmation link is a courtesy: a mail that doesn't go doesn't undo the sign-up (it can be resent later).
    if (s && mailReady()) {
      try {
        await sendMail({ to: s.address, ...accountConfirmMail(a.username, s.address, s.confirmToken!) });
      } catch (e) {
        console.error("Sign-up email: failed:", e);
      }
    }
    return json({ ok: true });
  } catch (e) {
    return errorResponse(e);
  }
}
