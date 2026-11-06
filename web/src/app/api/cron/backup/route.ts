import { json } from "@/lib/auth";
import { accountStore } from "@/lib/accounts";
import { unseal } from "@/lib/session";
import { nextcloudBackup } from "@/lib/store";

export const dynamic = "force-dynamic";
export const maxDuration = 120;

/**
 * Once a night (vercel.json): each account with a Nextcloud backup place gets today's copy of its sync file in
 * <folder>/web-backups/ there. Vercel sends the CRON_SECRET, so nobody else can start it.
 */
export async function GET(req: Request) {
  const secret = process.env.CRON_SECRET;
  if (!secret || req.headers.get("authorization") !== `Bearer ${secret}`) return json({ error: "Not allowed." }, 401);
  const store = accountStore();
  if (!store) return json({ ok: true, accounts: 0 });
  const now = Date.now();
  let copied = 0;
  const failed: number[] = [];
  for (const { accountId, target } of await store.withBackupTargets()) {
    try {
      const sync = await store.getSync(accountId);
      const appPassword = unseal<string>(target.sealedPassword, now);
      if (!sync || !appPassword) continue;
      await nextcloudBackup({ server: target.server, loginName: target.loginName, userId: target.userId, appPassword, folder: target.folder }, sync.bytes, now);
      copied++;
    } catch (e) {
      console.error(`Nightly backup of account ${accountId} failed:`, e);
      failed.push(accountId);
    }
  }
  return json({ ok: failed.length === 0, copied, failed });
}
