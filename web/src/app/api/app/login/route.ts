import { errorResponse, json } from "@/lib/auth";
import { fields, needStore } from "@/lib/accountweb";
import { addDevice, checkLogin } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * The app logs in with a website account (Settings → Sync): {username, password, device} gives a token the phone keeps
 * and sends as "Authorization: Bearer <token>". No cookies, so other sites can't use it.
 */
export async function POST(req: Request) {
  try {
    const { username, password, device } = await fields(req, ["username", "password", "device"]);
    const store = needStore();
    const now = Date.now();
    const a = await checkLogin(store, username, password, now);
    const { token } = await addDevice(store, a, `App · ${device || "phone"}`, now);
    return json({ token, username: a.username });
  } catch (e) {
    return errorResponse(e);
  }
}
