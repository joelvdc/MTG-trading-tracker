import { errorResponse, fromOurPages, json } from "@/lib/auth";
import { fields, needStore } from "@/lib/accountweb";
import { finishReset } from "@/lib/users";

export const dynamic = "force-dynamic";

/** The "Forgot password?" email's link: {token, password} sets the new password (a link works once, for an hour). */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const { token, password } = await fields(req, ["token", "password"]);
    await finishReset(needStore(), token, password, Date.now());
    return json({ ok: true });
  } catch (e) {
    return errorResponse(e);
  }
}
