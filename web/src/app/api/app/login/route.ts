import { errorResponse, json } from "@/lib/auth";
import { fields, needStore } from "@/lib/accountweb";
import { addDevice, loginStep } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * The app logs in with a website account (Settings → Sync): {username, password, code, device} gives a token the phone
 * keeps and sends as "Authorization: Bearer <token>". With two-step login on, {needCode: true} comes first (no token)
 * and the phone asks for the code. No cookies, so other sites can't use it.
 */
export async function POST(req: Request) {
  try {
    const { username, password, code, device } = await fields(req, ["username", "password", "code", "device"]);
    const store = needStore();
    const now = Date.now();
    // A wrong code is counted from one try to the next (the phone asks again with the same password).
    const a = await loginStep(store, username, password, code, now);
    if (!a) return json({ needCode: true });
    const { token } = await addDevice(store, a, `App · ${device || "phone"}`, now);
    return json({ token, username: a.username });
  } catch (e) {
    return errorResponse(e);
  }
}
