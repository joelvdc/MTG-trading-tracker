import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { fields, needStore } from "@/lib/accountweb";
import { revoke } from "@/lib/nextcloud";
import { isUser, unseal } from "@/lib/session";
import { AccountError, changePassword, makeInvite, resetPassword } from "@/lib/users";

export const dynamic = "force-dynamic";

async function me() {
  const a = await currentAccount();
  if (!a || !isUser(a)) return null;
  const store = needStore();
  const account = await store.byId(a.accountId);
  return account ? { session: a, account, store } : null;
}

/** The account: its devices and backup place; for the owner also every account and the invite codes. */
export async function GET() {
  try {
    const m = await me();
    if (!m) return json({ error: "Log in with your account first.", loggedOut: true }, 401);
    const { store, account, session } = m;
    const target = await store.backupTarget(account.id);
    const sync = await store.getSync(account.id);
    return json({
      username: account.username,
      admin: account.admin,
      device: session.device,
      devices: await store.devices(account.id),
      backup: target ? { server: target.server, folder: target.folder } : null,
      syncedAt: sync?.writtenAt ?? null,
      ...(account.admin ? { accounts: await store.list(), invites: await store.invites() } : {}),
    });
  } catch (e) {
    return errorResponse(e);
  }
}

/** action: "password" {current, next}; "logout-device" {id}; "backup-off"; owner only: "invite", "reset" {id}. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const m = await me();
    if (!m) return json({ error: "Log in with your account first.", loggedOut: true }, 401);
    const { store, account, session } = m;
    const f = await fields(req, ["action", "current", "next", "id"]);
    const now = Date.now();
    switch (f.action) {
      case "password":
        await changePassword(store, account, f.current, f.next, session.device);
        return json({ ok: true });
      case "logout-device": {
        const d = await store.device(f.id);
        if (!d || d.accountId !== account.id) throw new AccountError("No such device.");
        await store.removeDevice(d.id);
        return json({ ok: true });
      }
      case "backup-off": {
        // The nightly copy stops and the website's app password on Nextcloud is cancelled.
        const old = await store.backupTarget(account.id);
        await store.setBackupTarget(account.id, null);
        const appPassword = old ? unseal<string>(old.sealedPassword, now) : null;
        if (old && appPassword) await revoke({ server: old.server, loginName: old.loginName, userId: old.userId, appPassword, folder: old.folder });
        return json({ ok: true });
      }
      case "invite":
        return json({ ok: true, code: await makeInvite(store, account, now) });
      case "reset": {
        const id = Number(f.id);
        if (id === account.id) throw new AccountError("Change your own password above instead.");
        return json({ ok: true, password: await resetPassword(store, account, id) });
      }
      default:
        return json({ error: "Unknown action." }, 400);
    }
  } catch (e) {
    return errorResponse(e);
  }
}
