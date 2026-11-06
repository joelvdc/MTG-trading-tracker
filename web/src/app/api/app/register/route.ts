import { errorResponse, json } from "@/lib/auth";
import { fields, needStore } from "@/lib/accountweb";
import { addDevice, register } from "@/lib/users";

export const dynamic = "force-dynamic";

/** The app makes a new account with an invite code ({invite, username, password, device}) and is logged in with it. */
export async function POST(req: Request) {
  try {
    const { invite, username, password, device } = await fields(req, ["invite", "username", "password", "device"]);
    const store = needStore();
    const now = Date.now();
    const a = await register(store, invite, username, password, now);
    const { token } = await addDevice(store, a, `App · ${device || "phone"}`, now);
    return json({ token, username: a.username });
  } catch (e) {
    return errorResponse(e);
  }
}
