import { cookies } from "next/headers";
import { errorResponse, fromOurPages, json } from "@/lib/auth";
import { allowedUsers, nextcloudServer } from "@/lib/config";
import { pollLogin, revoke, sameOrigin, userId } from "@/lib/nextcloud";
import { type Account, cookieOptions, LOGIN_COOKIE, type PendingLogin, SESSION_COOKIE, SESSION_DAYS, seal, unseal } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Checks whether the owner approved the login on Nextcloud; when so, saves the login in the session cookie. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const jar = await cookies();
  const pending = unseal<PendingLogin>(jar.get(LOGIN_COOKIE)?.value);
  if (!pending) return json({ status: "expired" });
  try {
    const server = nextcloudServer();
    if (!sameOrigin(pending.pollEndpoint, server)) return json({ status: "expired" });
    const got = await pollLogin(pending.pollEndpoint, pending.pollToken);
    if (!got) return json({ status: "waiting" });
    jar.delete(LOGIN_COOKIE);
    const id = await userId(server, got);
    const account: Account = { server, loginName: got.loginName, userId: id, appPassword: got.appPassword, folder: pending.folder };
    if (!allowedUsers().includes(id)) {
      await revoke(account);
      console.warn(`Login refused for Nextcloud user "${id}" (not in ALLOWED_USERS).`);
      return json({ status: "refused", message: `The Nextcloud user "${id}" isn't allowed on this website.` });
    }
    jar.set(SESSION_COOKIE, seal(account, SESSION_DAYS * 86_400_000), cookieOptions(SESSION_DAYS * 86_400));
    return json({ status: "done" });
  } catch (e) {
    return errorResponse(e);
  }
}
