import { errorResponse, fromOurPages, json, nextcloudLogin } from "@/lib/auth";
import { fields, logBrowserIn, needStore } from "@/lib/accountweb";
import { getFile } from "@/lib/nextcloud";
import { seal } from "@/lib/session";
import { NoFolder, NoSyncFile, SYNC_FILE } from "@/lib/store";
import { decode } from "@/lib/syncfile";
import { createFromNextcloud } from "@/lib/users";

export const dynamic = "force-dynamic";

/** How long the sealed Nextcloud app password for the nightly backup stays readable (it is revoked on Nextcloud instead). */
const BACKUP_SEAL_MS = 100 * 365 * 86_400_000;

/**
 * Someone logged in with Nextcloud makes their website account (the first one made is the owner's): a copy of the
 * current sync file goes into it, and the Nextcloud login stays as the place for the nightly backup. The browser is
 * then logged in with the account. Nothing on Nextcloud is changed.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const nc = await nextcloudLogin();
  if (!nc) return json({ error: "Log in with Nextcloud first.", loggedOut: true }, 401);
  try {
    const { username, password } = await fields(req, ["username", "password"]);
    if (!nc.folder) throw new NoFolder();
    const remote = await getFile(nc, SYNC_FILE);
    if (!remote) throw new NoSyncFile();
    decode(remote.bytes); // Only a file the website can read is moved.
    const store = needStore();
    const now = Date.now();
    const a = await createFromNextcloud(store, username, password, now);
    await store.putSync(a.id, remote.bytes, null, now);
    await store.setBackupTarget(a.id, {
      server: nc.server,
      loginName: nc.loginName,
      userId: nc.userId,
      sealedPassword: seal(nc.appPassword, BACKUP_SEAL_MS, now),
      folder: nc.folder,
    });
    await logBrowserIn(store, a, req, now);
    return json({ ok: true, admin: a.admin, bytes: remote.bytes.length });
  } catch (e) {
    return errorResponse(e);
  }
}
