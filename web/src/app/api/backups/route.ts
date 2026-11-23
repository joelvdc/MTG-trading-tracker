import { currentAccount, errorResponse, json } from "@/lib/auth";
import { getFile, listFiles } from "@/lib/nextcloud";
import { accountStore } from "@/lib/accounts";
import { isUser } from "@/lib/session";
import { BACKUP_FOLDER, loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";

const NAME = /^sync-\d{4}-\d{2}-\d{2}\.json\.gz$/;

/** The file name of a download of the file as it is now: "mtg-trader-2026-11-23.json.gz" (UTC). */
function nowName(now: number): string {
  return `mtg-trader-${new Date(now).toISOString().slice(0, 10)}.json.gz`;
}

/** [bytes] as a download called [name]. */
function download(bytes: Uint8Array, name: string): Response {
  return new Response(Buffer.from(bytes), {
    headers: { "Content-Type": "application/gzip", "Content-Disposition": `attachment; filename="${name}"`, "Cache-Control": "no-store" },
  });
}

/**
 * The website's daily backups of the sync file (web-backups/ in the sync folder), newest first; with ?name=, that
 * backup as a download. ?name=now is the file as it is now. Restoring one is done in the app (Settings → Backups →
 * Restore from a file).
 */
export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const name = new URL(req.url).searchParams.get("name");
    if (isUser(a)) {
      const store = accountStore()!;
      if (name === "now") {
        const got = await store.getSync(a.accountId);
        if (!got) return json({ error: "No data yet." }, 404);
        return download(got.bytes, nowName(Date.now()));
      }
      // An account's backups are kept by the website itself (the last 3 days with changes).
      if (name === null) return json({ backups: (await store.backups(a.accountId)).map((d) => `sync-${d}.json.gz`), folder: "the website" });
      const day = NAME.test(name) ? name.slice(5, 15) : null;
      const bytes = day ? await store.backup(a.accountId, day) : null;
      if (!bytes) return json({ error: "No such backup." }, 404);
      return download(bytes, name);
    }
    if (name === "now") return download((await loadSync(a)).bytes, nowName(Date.now()));
    if (name === null) {
      const names = (await listFiles(a, BACKUP_FOLDER)).filter((n) => NAME.test(n)).sort().reverse();
      return json({ backups: names, folder: `${a.folder}/${BACKUP_FOLDER}` });
    }
    if (!NAME.test(name)) return json({ error: "No such backup." }, 404);
    const f = await getFile(a, `${BACKUP_FOLDER}/${name}`);
    if (!f) return json({ error: "That backup is gone." }, 404);
    return download(f.bytes, name);
  } catch (e) {
    return errorResponse(e);
  }
}
