// Website accounts (step C): user names and passwords, invite codes, logged-in devices, and each account's sync file,
// in the Neon Postgres database (DATABASE_URL). DATABASE_URL=memory keeps everything in memory (tests, local checks).
import { createHash, randomBytes, randomInt, scrypt as scryptCb, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";
import { neon } from "@neondatabase/serverless";

const scrypt = promisify(scryptCb) as (password: string, salt: Buffer, keylen: number, options: { N: number; r: number; p: number; maxmem: number }) => Promise<Buffer>;

export interface UserAccount {
  id: number;
  username: string;
  admin: boolean;
  createdAt: number;
}

export interface Device {
  id: string;
  accountId: number;
  name: string;
  createdAt: number;
  lastUsed: number;
}

export interface Invite {
  code: string;
  createdAt: number;
  usedBy: string | null;
  usedAt: number | null;
}

/** An account's sync file: the same gzip bytes as sync.json.gz, and its version (what the ETag was on Nextcloud). */
export interface StoredSync {
  bytes: Uint8Array;
  version: number;
  writtenAt: number;
}

/** Where the account's file is copied at night (the Nextcloud login, its app password sealed with SESSION_SECRET). */
export interface BackupTarget {
  server: string;
  loginName: string;
  userId: string;
  sealedPassword: string;
  folder: string;
}

export interface AccountStore {
  count(): Promise<number>;
  create(username: string, passwordHash: string, admin: boolean, now: number): Promise<UserAccount | null>;
  byName(username: string): Promise<(UserAccount & { passwordHash: string; failures: number; lockedUntil: number }) | null>;
  byId(id: number): Promise<UserAccount | null>;
  list(): Promise<UserAccount[]>;
  setPassword(id: number, passwordHash: string): Promise<void>;
  setFailures(id: number, failures: number, lockedUntil: number): Promise<void>;
  addInvite(code: string, createdBy: number, now: number): Promise<void>;
  invites(): Promise<Invite[]>;
  /** Marks an unused invite as used; false if it doesn't exist or was used already. */
  useInvite(code: string, username: string, now: number): Promise<boolean>;
  addDevice(id: string, accountId: number, tokenHash: string, name: string, now: number): Promise<void>;
  deviceByToken(tokenHash: string): Promise<Device | null>;
  device(id: string): Promise<Device | null>;
  devices(accountId: number): Promise<Device[]>;
  touchDevice(id: string, now: number): Promise<void>;
  removeDevice(id: string): Promise<void>;
  removeDevices(accountId: number): Promise<void>;
  getSync(accountId: number): Promise<StoredSync | null>;
  /** Saves only if the stored version is still [ifVersion] (null: only if there is none yet); the new version, or null. */
  putSync(accountId: number, bytes: Uint8Array, ifVersion: number | null, now: number): Promise<number | null>;
  /** Keeps [bytes] as the day's backup unless that day has one; keeps the newest [keep]. */
  backupOnce(accountId: number, day: string, bytes: Uint8Array, keep: number): Promise<void>;
  backups(accountId: number): Promise<string[]>;
  backup(accountId: number, day: string): Promise<Uint8Array | null>;
  backupTarget(accountId: number): Promise<BackupTarget | null>;
  setBackupTarget(accountId: number, target: BackupTarget | null): Promise<void>;
  /** Accounts with a backup target, for the nightly copy. */
  withBackupTargets(): Promise<{ accountId: number; target: BackupTarget }[]>;
}

// ---- Passwords, tokens, codes ----

const SCRYPT = { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };

export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(16);
  const hash = await scrypt(password.normalize("NFC"), salt, 32, SCRYPT);
  return `scrypt$${salt.toString("base64")}$${hash.toString("base64")}`;
}

export async function checkPassword(password: string, stored: string): Promise<boolean> {
  const [kind, salt, hash] = stored.split("$");
  if (kind !== "scrypt" || !salt || !hash) return false;
  const want = Buffer.from(hash, "base64");
  const got = await scrypt(password.normalize("NFC"), Buffer.from(salt, "base64"), want.length, SCRYPT);
  return got.length === want.length && timingSafeEqual(got, want);
}

/** A device's login token (shown to it once) and the hash kept in the database. */
export function newToken(): { token: string; hash: string } {
  const token = randomBytes(32).toString("base64url");
  return { token, hash: tokenHash(token) };
}

export function tokenHash(token: string): string {
  return createHash("sha256").update(token, "utf8").digest("base64url");
}

export function newId(): string {
  return randomBytes(12).toString("base64url");
}

const CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

/** "K7QD-M2XP": easy to read out and type (no 0/O or 1/I). */
export function newCode(): string {
  const pick = () => Array.from({ length: 4 }, () => CODE_LETTERS[randomInt(CODE_LETTERS.length)]).join("");
  return `${pick()}-${pick()}`;
}

/** A one-time password for a reset: three code groups. */
export function newTempPassword(): string {
  return `${newCode()}-${newCode().slice(0, 4)}`;
}

export function normalizeCode(code: string): string {
  return code.trim().toUpperCase().replace(/[^A-Z0-9]/g, "").replace(/^(.{4})(.{4})$/, "$1-$2");
}

/** User names: 3–32 letters, digits, dots, dashes or underscores; compared without case. */
export function cleanUsername(name: string): string | null {
  const n = name.trim().toLowerCase();
  return /^[a-z0-9._-]{3,32}$/.test(n) ? n : null;
}

export function passwordProblem(password: string): string | null {
  if (password.length < 10) return "The password needs at least 10 characters.";
  if (password.length > 200) return "That password is too long.";
  return null;
}

// ---- In memory ----

export function memoryStore(): AccountStore {
  const accounts: (UserAccount & { passwordHash: string; failures: number; lockedUntil: number })[] = [];
  const invites: (Invite & { createdBy: number })[] = [];
  const devices: (Device & { tokenHash: string })[] = [];
  const syncs = new Map<number, StoredSync>();
  const backups = new Map<number, Map<string, Uint8Array>>();
  const targets = new Map<number, BackupTarget>();
  const plain = (a: UserAccount): UserAccount => ({ id: a.id, username: a.username, admin: a.admin, createdAt: a.createdAt });
  const dev = (d: Device & { tokenHash: string }): Device => ({ id: d.id, accountId: d.accountId, name: d.name, createdAt: d.createdAt, lastUsed: d.lastUsed });
  return {
    count: async () => accounts.length,
    create: async (username, passwordHash, admin, now) => {
      if (accounts.some((a) => a.username === username)) return null;
      const a = { id: accounts.length + 1, username, admin, createdAt: now, passwordHash, failures: 0, lockedUntil: 0 };
      accounts.push(a);
      return plain(a);
    },
    byName: async (username) => {
      const a = accounts.find((x) => x.username === username);
      return a ? { ...a } : null;
    },
    byId: async (id) => {
      const a = accounts.find((x) => x.id === id);
      return a ? plain(a) : null;
    },
    list: async () => accounts.map(plain),
    setPassword: async (id, hash) => {
      const a = accounts.find((x) => x.id === id);
      if (a) Object.assign(a, { passwordHash: hash, failures: 0, lockedUntil: 0 });
    },
    setFailures: async (id, failures, lockedUntil) => {
      const a = accounts.find((x) => x.id === id);
      if (a) Object.assign(a, { failures, lockedUntil });
    },
    addInvite: async (code, createdBy, now) => {
      invites.push({ code, createdBy, createdAt: now, usedBy: null, usedAt: null });
    },
    invites: async () => invites.map(({ code, createdAt, usedBy, usedAt }) => ({ code, createdAt, usedBy, usedAt })),
    useInvite: async (code, username, now) => {
      const i = invites.find((x) => x.code === code && x.usedBy === null);
      if (!i) return false;
      Object.assign(i, { usedBy: username, usedAt: now });
      return true;
    },
    addDevice: async (id, accountId, tokenHash, name, now) => {
      devices.push({ id, accountId, tokenHash, name, createdAt: now, lastUsed: now });
    },
    deviceByToken: async (hash) => {
      const d = devices.find((x) => x.tokenHash === hash);
      return d ? dev(d) : null;
    },
    device: async (id) => {
      const d = devices.find((x) => x.id === id);
      return d ? dev(d) : null;
    },
    devices: async (accountId) => devices.filter((d) => d.accountId === accountId).map(dev),
    touchDevice: async (id, now) => {
      const d = devices.find((x) => x.id === id);
      // Like the database: written at most every 10 minutes.
      if (d && d.lastUsed < now - 600_000) d.lastUsed = now;
    },
    removeDevice: async (id) => {
      const i = devices.findIndex((x) => x.id === id);
      if (i >= 0) devices.splice(i, 1);
    },
    removeDevices: async (accountId) => {
      for (let i = devices.length - 1; i >= 0; i--) if (devices[i].accountId === accountId) devices.splice(i, 1);
    },
    getSync: async (id) => syncs.get(id) ?? null,
    putSync: async (id, bytes, ifVersion, now) => {
      const cur = syncs.get(id);
      if ((cur?.version ?? null) !== ifVersion) return null;
      const version = (cur?.version ?? 0) + 1;
      syncs.set(id, { bytes, version, writtenAt: now });
      return version;
    },
    backupOnce: async (id, day, bytes, keep) => {
      const m = backups.get(id) ?? new Map<string, Uint8Array>();
      backups.set(id, m);
      if (m.has(day)) return;
      m.set(day, bytes);
      const days = [...m.keys()].sort();
      for (const old of days.slice(0, Math.max(0, days.length - keep))) m.delete(old);
    },
    backups: async (id) => [...(backups.get(id)?.keys() ?? [])].sort().reverse(),
    backup: async (id, day) => backups.get(id)?.get(day) ?? null,
    backupTarget: async (id) => targets.get(id) ?? null,
    setBackupTarget: async (id, t) => {
      if (t) targets.set(id, t);
      else targets.delete(id);
    },
    withBackupTargets: async () => [...targets.entries()].map(([accountId, target]) => ({ accountId, target })),
  };
}

// ---- Postgres (Neon) ----

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS accounts (
     id SERIAL PRIMARY KEY,
     username TEXT NOT NULL UNIQUE,
     password_hash TEXT NOT NULL,
     admin BOOLEAN NOT NULL DEFAULT FALSE,
     failures INTEGER NOT NULL DEFAULT 0,
     locked_until BIGINT NOT NULL DEFAULT 0,
     created_at BIGINT NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS invites (
     code TEXT PRIMARY KEY,
     created_by INTEGER NOT NULL REFERENCES accounts(id),
     created_at BIGINT NOT NULL,
     used_by TEXT,
     used_at BIGINT)`,
  `CREATE TABLE IF NOT EXISTS devices (
     id TEXT PRIMARY KEY,
     account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
     token_hash TEXT NOT NULL UNIQUE,
     name TEXT NOT NULL,
     created_at BIGINT NOT NULL,
     last_used BIGINT NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS sync_files (
     account_id INTEGER PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
     body TEXT NOT NULL,
     version BIGINT NOT NULL,
     written_at BIGINT NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS backups (
     account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
     day TEXT NOT NULL,
     body TEXT NOT NULL,
     PRIMARY KEY (account_id, day))`,
  `CREATE TABLE IF NOT EXISTS backup_targets (
     account_id INTEGER PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
     target JSONB NOT NULL)`,
];

export type Row = Record<string, unknown>;
const num = (v: unknown) => Number(v);
const b64 = (bytes: Uint8Array) => Buffer.from(bytes).toString("base64");
const unb64 = (s: unknown) => new Uint8Array(Buffer.from(String(s), "base64"));

export function postgresStore(url: string): AccountStore {
  const sql = neon(url);
  return sqlStore(async (text, params) => (await sql.query(text, params)) as Row[]);
}

/** The store on any Postgres: [run] sends one query with $1… parameters (Neon on Vercel, PGlite in the tests). */
export function sqlStore(run: (text: string, params: unknown[]) => Promise<Row[]>): AccountStore {
  let ready: Promise<void> | null = null;
  const q = async (text: string, params: unknown[] = []): Promise<Row[]> => {
    ready ??= (async () => {
      for (const s of SCHEMA) await run(s, []);
    })().catch((e) => {
      ready = null;
      throw e;
    });
    await ready;
    return run(text, params);
  };
  const acc = (r: Row): UserAccount => ({ id: num(r.id), username: String(r.username), admin: Boolean(r.admin), createdAt: num(r.created_at) });
  const dev = (r: Row): Device => ({ id: String(r.id), accountId: num(r.account_id), name: String(r.name), createdAt: num(r.created_at), lastUsed: num(r.last_used) });
  return {
    count: async () => num((await q("SELECT COUNT(*) AS n FROM accounts"))[0].n),
    create: async (username, hash, admin, now) => {
      const r = await q("INSERT INTO accounts (username, password_hash, admin, created_at) VALUES ($1, $2, $3, $4) ON CONFLICT (username) DO NOTHING RETURNING *", [username, hash, admin, now]);
      return r[0] ? acc(r[0]) : null;
    },
    byName: async (username) => {
      const r = (await q("SELECT * FROM accounts WHERE username = $1", [username]))[0];
      return r ? { ...acc(r), passwordHash: String(r.password_hash), failures: num(r.failures), lockedUntil: num(r.locked_until) } : null;
    },
    byId: async (id) => {
      const r = (await q("SELECT * FROM accounts WHERE id = $1", [id]))[0];
      return r ? acc(r) : null;
    },
    list: async () => (await q("SELECT * FROM accounts ORDER BY id")).map(acc),
    setPassword: async (id, hash) => void (await q("UPDATE accounts SET password_hash = $2, failures = 0, locked_until = 0 WHERE id = $1", [id, hash])),
    setFailures: async (id, failures, lockedUntil) => void (await q("UPDATE accounts SET failures = $2, locked_until = $3 WHERE id = $1", [id, failures, lockedUntil])),
    addInvite: async (code, createdBy, now) => void (await q("INSERT INTO invites (code, created_by, created_at) VALUES ($1, $2, $3)", [code, createdBy, now])),
    invites: async () =>
      (await q("SELECT * FROM invites ORDER BY created_at DESC")).map((r) => ({
        code: String(r.code),
        createdAt: num(r.created_at),
        usedBy: r.used_by === null ? null : String(r.used_by),
        usedAt: r.used_at === null ? null : num(r.used_at),
      })),
    useInvite: async (code, username, now) => (await q("UPDATE invites SET used_by = $2, used_at = $3 WHERE code = $1 AND used_by IS NULL RETURNING code", [code, username, now])).length === 1,
    addDevice: async (id, accountId, hash, name, now) =>
      void (await q("INSERT INTO devices (id, account_id, token_hash, name, created_at, last_used) VALUES ($1, $2, $3, $4, $5, $5)", [id, accountId, hash, name, now])),
    deviceByToken: async (hash) => {
      const r = (await q("SELECT * FROM devices WHERE token_hash = $1", [hash]))[0];
      return r ? dev(r) : null;
    },
    device: async (id) => {
      const r = (await q("SELECT * FROM devices WHERE id = $1", [id]))[0];
      return r ? dev(r) : null;
    },
    devices: async (accountId) => (await q("SELECT * FROM devices WHERE account_id = $1 ORDER BY last_used DESC", [accountId])).map(dev),
    touchDevice: async (id, now) => void (await q("UPDATE devices SET last_used = $2::bigint WHERE id = $1 AND last_used < $2::bigint - 600000", [id, now])),
    removeDevice: async (id) => void (await q("DELETE FROM devices WHERE id = $1", [id])),
    removeDevices: async (accountId) => void (await q("DELETE FROM devices WHERE account_id = $1", [accountId])),
    getSync: async (id) => {
      const r = (await q("SELECT * FROM sync_files WHERE account_id = $1", [id]))[0];
      return r ? { bytes: unb64(r.body), version: num(r.version), writtenAt: num(r.written_at) } : null;
    },
    putSync: async (id, bytes, ifVersion, now) => {
      const r =
        ifVersion === null
          ? await q("INSERT INTO sync_files (account_id, body, version, written_at) VALUES ($1, $2, 1, $3) ON CONFLICT (account_id) DO NOTHING RETURNING version", [id, b64(bytes), now])
          : await q("UPDATE sync_files SET body = $2, version = version + 1, written_at = $4 WHERE account_id = $1 AND version = $3 RETURNING version", [id, b64(bytes), ifVersion, now]);
      return r[0] ? num(r[0].version) : null;
    },
    backupOnce: async (id, day, bytes, keep) => {
      await q("INSERT INTO backups (account_id, day, body) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING", [id, day, b64(bytes)]);
      await q("DELETE FROM backups WHERE account_id = $1 AND day NOT IN (SELECT day FROM backups WHERE account_id = $1 ORDER BY day DESC LIMIT $2)", [id, keep]);
    },
    backups: async (id) => (await q("SELECT day FROM backups WHERE account_id = $1 ORDER BY day DESC", [id])).map((r) => String(r.day)),
    backup: async (id, day) => {
      const r = (await q("SELECT body FROM backups WHERE account_id = $1 AND day = $2", [id, day]))[0];
      return r ? unb64(r.body) : null;
    },
    backupTarget: async (id) => {
      const r = (await q("SELECT target FROM backup_targets WHERE account_id = $1", [id]))[0];
      return r ? (r.target as BackupTarget) : null;
    },
    setBackupTarget: async (id, t) =>
      void (t
        ? await q("INSERT INTO backup_targets (account_id, target) VALUES ($1, $2) ON CONFLICT (account_id) DO UPDATE SET target = EXCLUDED.target", [id, JSON.stringify(t)])
        : await q("DELETE FROM backup_targets WHERE account_id = $1", [id])),
    withBackupTargets: async () => (await q("SELECT account_id, target FROM backup_targets")).map((r) => ({ accountId: num(r.account_id), target: r.target as BackupTarget })),
  };
}

let store: AccountStore | null = null;

/** The account store for DATABASE_URL (null while the website has no database). */
export function accountStore(): AccountStore | null {
  const url = (process.env.DATABASE_URL ?? "").trim();
  if (!url) return null;
  store ??= url === "memory" ? memoryStore() : postgresStore(url);
  return store;
}

/** For tests: start again with an empty memory store. */
export function resetMemoryStore(): void {
  store = null;
}
