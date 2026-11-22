import { errorResponse, json } from "@/lib/auth";
import { fields, needStore } from "@/lib/accountweb";
import { mailReady, sendMail } from "@/lib/mailer";
import { addDevice, register } from "@/lib/users";
import { accountConfirmMail } from "@/lib/weeklyjob";

export const dynamic = "force-dynamic";

/** The app makes a new account with an invite code ({invite, username, password, email, device}) and is logged in with it. */
export async function POST(req: Request) {
  try {
    const { invite, username, password, email, device } = await fields(req, ["invite", "username", "password", "email", "device"]);
    const store = needStore();
    const now = Date.now();
    const a = await register(store, invite, username, password, email, now);
    const s = await store.emailSettings(a.id);
    const { token } = await addDevice(store, a, `App · ${device || "phone"}`, now);
    // The confirmation link is a courtesy: a mail that doesn't go doesn't undo the sign-up (it can be resent later).
    if (s && mailReady()) {
      try {
        await sendMail({ to: s.address, ...accountConfirmMail(a.username, s.address, s.confirmToken!) });
      } catch (e) {
        console.error("Sign-up email: failed:", e);
      }
    }
    return json({ token, username: a.username });
  } catch (e) {
    return errorResponse(e);
  }
}
