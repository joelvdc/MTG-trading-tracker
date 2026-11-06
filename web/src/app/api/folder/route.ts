import { cookies } from "next/headers";
import { currentAccount, nextcloudLogin, errorResponse, fromOurPages, json } from "@/lib/auth";
import { cleanFolder } from "@/lib/config";
import { browse } from "@/lib/nextcloud";
import { type Account, cookieOptions, SESSION_COOKIE, SESSION_DAYS, seal } from "@/lib/session";
import { SYNC_FILE } from "@/lib/store";

export const dynamic = "force-dynamic";

/** Sets the sync folder for this login (Settings). Only a folder that holds the app's sync file is accepted. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await nextcloudLogin();
  if (!a) return json({ error: (await currentAccount()) ? "Only with a Nextcloud login." : "Log in first.", loggedOut: !(await currentAccount()) }, 401);
  try {
    const body = (await req.json().catch(() => ({}))) as { folder?: string };
    const folder = cleanFolder(body.folder);
    if (!folder) return json({ error: "That folder name can't be used." }, 400);
    const { files } = await browse(a, folder);
    if (!files.includes(SYNC_FILE)) {
      return json({ error: "That folder has no sync file from the app. Pick the folder chosen in the app under Settings → Sync with Nextcloud." }, 400);
    }
    const next: Account = { ...a, folder };
    (await cookies()).set(SESSION_COOKIE, seal(next, SESSION_DAYS * 86_400_000), cookieOptions(SESSION_DAYS * 86_400));
    return json({ ok: true, folder });
  } catch (e) {
    return errorResponse(e);
  }
}
