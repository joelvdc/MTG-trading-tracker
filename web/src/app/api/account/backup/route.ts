import { cookies } from "next/headers";
import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { needStore } from "@/lib/accountweb";
import { allowedUsers, nextcloudServer } from "@/lib/config";
import { makeFolder, pollLogin, revoke, sameOrigin, startLogin, userId } from "@/lib/nextcloud";
import { cookieOptions, isUser, LOGIN_COOKIE, LOGIN_MINUTES, seal, unseal, type NextcloudLogin } from "@/lib/session";
import { nextcloudBackup } from "@/lib/store";

export const dynamic = "force-dynamic";

/** How long the sealed Nextcloud app password for the nightly backup stays readable (it is revoked on Nextcloud instead). */
const BACKUP_SEAL_MS = 100 * 365 * 86_400_000;

/** A Nextcloud approval under way for the backup: Nextcloud's poll address and token, and the folder asked for. */
interface PendingBackup {
  pollToken: string;
  pollEndpoint: string;
  folder: string;
  accountId: number;
}

/** A Nextcloud folder path as typed: "MTG Trader", "Backups/MTG"; no ".." or empty parts. */
function cleanFolder(raw: unknown): string {
  const parts = (typeof raw === "string" ? raw : "")
    .split("/")
    .map((p) => p.trim())
    .filter(Boolean);
  if (parts.length === 0 || parts.length > 8 || parts.some((p) => p === "." || p === ".." || p.length > 100)) throw new Error("bad folder");
  return parts.join("/");
}

/**
 * Connecting Nextcloud as the nightly backup place of a website account (Settings → Nextcloud backup). Nextcloud is
 * only ever a backup: this never logs anyone in. "start" {folder}: Nextcloud's approval page opens; "poll": once
 * approved, the folder is made if needed, today's copy is written there at once (so a problem shows now, not at
 * night) and the place is kept for the nightly job.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in with your account first.", loggedOut: true }, 401);
  const jar = await cookies();
  try {
    const body = ((await req.json()) ?? {}) as { action?: string; folder?: string };
    if (body.action === "start") {
      let folder: string;
      try {
        folder = cleanFolder(body.folder);
      } catch {
        return json({ error: "Type the Nextcloud folder for the backups, e.g. MTG Trader." }, 400);
      }
      const start = await startLogin(nextcloudServer());
      const pending: PendingBackup = { pollToken: start.pollToken, pollEndpoint: start.pollEndpoint, folder, accountId: a.accountId };
      jar.set(LOGIN_COOKIE, seal(pending, LOGIN_MINUTES * 60_000), cookieOptions(LOGIN_MINUTES * 60));
      return json({ loginUrl: start.loginUrl });
    }
    if (body.action === "poll") {
      const pending = unseal<PendingBackup>(jar.get(LOGIN_COOKIE)?.value);
      const server = nextcloudServer();
      if (!pending || pending.accountId !== a.accountId || !sameOrigin(pending.pollEndpoint, server)) return json({ status: "expired" });
      const got = await pollLogin(pending.pollEndpoint, pending.pollToken);
      if (!got) return json({ status: "waiting" });
      jar.delete(LOGIN_COOKIE);
      const id = await userId(server, got);
      const nc: NextcloudLogin = { server, loginName: got.loginName, userId: id, appPassword: got.appPassword, folder: pending.folder };
      if (!allowedUsers().includes(id)) {
        await revoke(nc);
        return json({ status: "refused", message: `The Nextcloud user "${id}" isn't allowed to keep backups here.` });
      }
      const store = needStore();
      const now = Date.now();
      try {
        await makeFolder(nc, "");
        const sync = await store.getSync(a.accountId);
        if (sync) await nextcloudBackup(nc, sync.bytes, now);
      } catch (e) {
        await revoke(nc);
        throw e;
      }
      // A backup place connected before: its app password is cancelled on Nextcloud.
      const old = await store.backupTarget(a.accountId);
      await store.setBackupTarget(a.accountId, { server, loginName: got.loginName, userId: id, sealedPassword: seal(got.appPassword, BACKUP_SEAL_MS, now), folder: pending.folder });
      if (old) {
        const oldPassword = unseal<string>(old.sealedPassword, now);
        if (oldPassword && oldPassword !== got.appPassword) await revoke({ server: old.server, loginName: old.loginName, userId: old.userId, appPassword: oldPassword, folder: old.folder });
      }
      return json({ status: "done", folder: pending.folder });
    }
    if (body.action === "cancel") {
      jar.delete(LOGIN_COOKIE);
      return json({ ok: true });
    }
    return json({ error: "Unknown action." }, 400);
  } catch (e) {
    return errorResponse(e);
  }
}
