import { cookies } from "next/headers";
import { errorResponse, fromOurPages, json } from "@/lib/auth";
import { nextcloudServer } from "@/lib/config";
import { startLogin } from "@/lib/nextcloud";
import { cookieOptions, LOGIN_COOKIE, LOGIN_MINUTES, seal, type PendingLogin } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Starts Nextcloud's login: the page opens the returned address, then asks /api/login/poll until it's approved. */
export async function POST(req: Request) {
  // The sync folder is picked after logging in (Settings), from the owner's own Nextcloud folders.
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const start = await startLogin(nextcloudServer());
    const pending: PendingLogin = { pollToken: start.pollToken, pollEndpoint: start.pollEndpoint };
    (await cookies()).set(LOGIN_COOKIE, seal(pending, LOGIN_MINUTES * 60_000), cookieOptions(LOGIN_MINUTES * 60));
    return json({ loginUrl: start.loginUrl });
  } catch (e) {
    return errorResponse(e);
  }
}
