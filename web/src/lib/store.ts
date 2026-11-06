// Reading and changing the sync file safely: an account's file in the database (step C), or (until the move) the file
// on Nextcloud.
import { accountStore } from "./accounts";
import { getFile, listFiles, makeFolder, putFile, putPlain, deleteFile, NextcloudError } from "./nextcloud";
import { type Account, isUser, type NextcloudLogin } from "./session";
import { canWrite, decode, encode, stamp, type SyncFile } from "./syncfile";

export const SYNC_FILE = "sync.json.gz";
export const BACKUP_FOLDER = "web-backups";
export const KEEP_BACKUPS = 10;
const ATTEMPTS = 4;

export class NoSyncFile extends Error {
  constructor() {
    super("There's no sync file in this folder yet. Turn on Settings → Sync → Nextcloud in the app first (or check the folder name).");
  }
}

export class NoFolder extends Error {
  constructor() {
    super("Pick your sync folder under Settings first.");
  }
}

export class ReadOnlyFile extends Error {
  constructor(format: number) {
    super(`The sync file was written by a newer version of the app (format ${format}). The website shows it but can't change it until it's updated.`);
  }
}

export class NoAccountFile extends Error {
  constructor() {
    super("This account has no collection yet. Log in with it in the app (Settings → Sync) to send the phone's data here.");
  }
}

function store() {
  const s = accountStore();
  if (!s) throw new Error("The website has no database (DATABASE_URL).");
  return s;
}

export async function loadSync(a: Account): Promise<{ file: SyncFile; etag: string | null; bytes: Uint8Array }> {
  if (isUser(a)) {
    const got = await store().getSync(a.accountId);
    if (!got) throw new NoAccountFile();
    return { file: decode(got.bytes), etag: String(got.version), bytes: got.bytes };
  }
  if (!a.folder) throw new NoFolder();
  const remote = await getFile(a, SYNC_FILE);
  if (!remote) throw new NoSyncFile();
  return { file: decode(remote.bytes), etag: remote.etag, bytes: remote.bytes };
}

/** "sync-2026-11-05.json.gz" for the day of [now] (UTC). */
export function backupName(now: number): string {
  return `sync-${new Date(now).toISOString().slice(0, 10)}.json.gz`;
}

/**
 * Keeps a copy of the file as it was before the website's first change of the day, in web-backups/ next to it,
 * and keeps the newest [KEEP_BACKUPS] of them.
 */
async function backupOnce(a: Account, bytes: Uint8Array, now: number): Promise<void> {
  if (isUser(a)) return store().backupOnce(a.accountId, new Date(now).toISOString().slice(0, 10), bytes, KEEP_BACKUPS);
  const name = backupName(now);
  const existing = (await listFiles(a, BACKUP_FOLDER)).filter((n) => /^sync-\d{4}-\d{2}-\d{2}\.json\.gz$/.test(n)).sort();
  if (existing.includes(name)) return;
  if (existing.length === 0) await makeFolder(a, BACKUP_FOLDER);
  await putPlain(a, `${BACKUP_FOLDER}/${name}`, bytes);
  const all = [...existing, name].sort();
  for (const old of all.slice(0, Math.max(0, all.length - KEEP_BACKUPS))) await deleteFile(a, `${BACKUP_FOLDER}/${old}`);
}

/**
 * Reads the newest sync file, lets [change] edit it and saves it, only if no phone wrote the file in the meantime;
 * otherwise reads it again and redoes the change (a few times). [change] must work from the file it's given.
 */
export async function editSync<T>(a: Account, change: (file: SyncFile, now: number) => T, clock: () => number = Date.now): Promise<T> {
  for (let attempt = 1; attempt <= ATTEMPTS; attempt++) {
    const { file, etag, bytes } = await loadSync(a);
    if (!canWrite(file)) throw new ReadOnlyFile(file.format);
    const now = clock();
    await backupOnce(a, bytes, now);
    const result = change(file, now);
    stamp(file, now);
    if (isUser(a)) {
      if ((await store().putSync(a.accountId, encode(file), etag === null ? null : Number(etag), now)) !== null) return result;
      continue;
    }
    const put = await putFile(a, SYNC_FILE, encode(file), etag);
    if (put.ok) return result;
  }
  throw new NextcloudError("The sync file kept changing while saving (a phone was syncing). Try again in a moment.");
}

/** The nightly copy of an account's file to its Nextcloud folder (web-backups/, one per day, the newest 10). */
export async function nextcloudBackup(nc: NextcloudLogin, bytes: Uint8Array, now: number): Promise<void> {
  await backupOnce(nc, bytes, now);
}
