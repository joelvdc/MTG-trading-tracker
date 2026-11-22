import { errorResponse, fromOurPages, json } from "@/lib/auth";
import { fields, needStore } from "@/lib/accountweb";
import { sendMail } from "@/lib/mailer";
import { requestReset } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * "Forgot password?": {who} is the account's email address (or its user name). A link goes by email when there is a
 * confirmed account with it. The answer is the same either way, so nobody learns which addresses have an account
 * (or which emails the website sent).
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const { who } = await fields(req, ["who"]);
    await requestReset(needStore(), who, Date.now(), sendMail);
    return json({ ok: true });
  } catch (e) {
    console.error("Forgot password: failed:", e);
    return json({ ok: true });
  }
}
