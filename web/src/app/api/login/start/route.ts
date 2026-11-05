import { cookies } from "next/headers";
import { errorResponse, fromOurPages, json } from "@/lib/auth";
import { cleanFolder, DEFAULT_FOLDER, nextcloudServer } from "@/lib/config";
import { startLogin } from "@/lib/nextcloud";
import { cookieOptions, LOGIN_COOKIE, LOGIN_MINUTES, seal, type PendingLogin } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Starts Nextcloud's login: the page opens the returned address, then asks /api/login/poll until it's approved. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const body = (await req.json().catch(() => ({}))) as { folder?: string };
    const folder = cleanFolder(body.folder ?? DEFAULT_FOLDER);
    if (!folder) return json({ error: "That folder name can't be used." }, 400);
    const start = await startLogin(nextcloudServer());
    const pending: PendingLogin = { pollToken: start.pollToken, pollEndpoint: start.pollEndpoint, folder };
    (await cookies()).set(LOGIN_COOKIE, seal(pending, LOGIN_MINUTES * 60_000), cookieOptions(LOGIN_MINUTES * 60));
    return json({ loginUrl: start.loginUrl });
  } catch (e) {
    return errorResponse(e);
  }
}
