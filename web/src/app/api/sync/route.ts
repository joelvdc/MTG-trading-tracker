import { errorResponse, json } from "@/lib/auth";
import { loggedOut, needStore, phoneAccount } from "@/lib/accountweb";
import { KEEP_BACKUPS } from "@/lib/store";
import { decode } from "@/lib/syncfile";

export const dynamic = "force-dynamic";

/** The largest sync file accepted (the owner's is a few hundred KB). */
const MAX_BYTES = 20 * 1024 * 1024;

const tag = (version: number) => `"v${version}"`;
const versionOf = (header: string | null) => {
  const m = /^(?:W\/)?"v(\d+)"$/.exec((header ?? "").trim());
  return m ? Number(m[1]) : null;
};

/**
 * The account's sync file for the app, like the file on Nextcloud: the same gzip bytes, an ETag that changes with
 * every save, 304 for If-None-Match with the current ETag, 404 when the account has none yet.
 */
export async function GET(req: Request) {
  const p = await phoneAccount(req);
  if (!p) return loggedOut();
  try {
    const got = await needStore().getSync(p.account.id);
    if (!got) return json({ error: "No sync file yet." }, 404);
    const headers = { ETag: tag(got.version), "Cache-Control": "no-store" };
    if (versionOf(req.headers.get("if-none-match")) === got.version) return new Response(null, { status: 304, headers });
    return new Response(Buffer.from(got.bytes), { headers: { ...headers, "Content-Type": "application/gzip" } });
  } catch (e) {
    return errorResponse(e);
  }
}

/**
 * Saves the app's file: with If-Match only if it's still that version, with If-None-Match: * only if there's none
 * yet (412 otherwise, as on Nextcloud, so the app reads it again and merges). Answers the new ETag.
 */
export async function PUT(req: Request) {
  const p = await phoneAccount(req);
  if (!p) return loggedOut();
  try {
    const bytes = new Uint8Array(await req.arrayBuffer());
    if (bytes.length === 0 || bytes.length > MAX_BYTES) return json({ error: "That isn't a sync file." }, 400);
    try {
      decode(bytes);
    } catch {
      return json({ error: "That isn't a sync file." }, 400);
    }
    const store = needStore();
    const ifMatch = req.headers.get("if-match");
    const onlyNew = (req.headers.get("if-none-match") ?? "").trim() === "*";
    const current = await store.getSync(p.account.id);
    let expected: number | null;
    if (ifMatch !== null) {
      expected = versionOf(ifMatch);
      if (expected === null) return json({ error: "Changed meanwhile." }, 412);
    } else if (onlyNew) expected = null;
    else expected = current?.version ?? null;
    const now = Date.now();
    // The day's safety copy (the last 10 days with changes, under Settings → Backups), before the first save of a day.
    if (current && current.version === expected) await store.backupOnce(p.account.id, new Date(now).toISOString().slice(0, 10), current.bytes, KEEP_BACKUPS);
    const version = await store.putSync(p.account.id, bytes, expected, now);
    if (version === null) return json({ error: "Changed meanwhile." }, 412);
    return new Response(null, { status: expected === null ? 201 : 204, headers: { ETag: tag(version), "Cache-Control": "no-store" } });
  } catch (e) {
    return errorResponse(e);
  }
}
