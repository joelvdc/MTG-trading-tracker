// Website accounts (step C): user names and passwords, invite codes, logged-in devices, and each account's sync file,
// in the Neon Postgres database (DATABASE_URL). DATABASE_URL=memory keeps everything in memory (tests, local checks).
import { createHash, randomBytes, randomInt, scrypt as scryptCb, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";
import { neon } from "@neondatabase/serverless";
import { daysToDrop, type PricePoint } from "./pricehistory";

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

/**
 * One day's collection value, as the app keeps it (ValueSnapshot): the copies, and the values by the app's
 * ValueHistory keys ("trend", "low@tcgplayer", "pays@cardkingdom", "b:<binder uid>:trend"…; Unsorted is "~").
 */
export interface ValueDay {
  day: string;
  cards: number;
  values: Record<string, number>;
}

/** One point of the value chart. */
export interface ValuePoint {
  day: string;
  value: number;
  cards: number;
}

/** A wishlist price alert that was reached (the app's AlertEvent, without CardTrader). */
export interface AlertEvent {
  wishUid: string;
  cardName: string;
  at: number;
  /** The price in euros, and the alert level in euros at the time. */
  price: number;
  level: number;
  /** What the price was: "cardmarket:low" (PriceAlerts.basis). */
  basis: string;
  /** The printing's set, for "any printing" cards. */
  setCode: string | null;
}

/** Alert history lines are kept a year (plus a day) and at most this many per account. */
export const ALERT_KEEP_MS = 366 * 86_400_000;
export const ALERT_MAX_LINES = 500;

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
  /** The automatic categories (lib/autocategories.ts) for these name keys, in order; shared by every account. */
  tagCategories(keys: string[]): Promise<Map<string, string[]>>;
  /** Replaces the whole table with [rows] (written at [now]). */
  replaceTagCategories(rows: { key: string; categories: string[] }[], now: number): Promise<void>;
  /** How many names the table has, and when it was last rebuilt (null: never). */
  tagCategoryStats(): Promise<{ cards: number; updatedAt: number | null }>;
  /** Saves a day's value; [replace] false keeps a day that is there already (the phone's history sent once). */
  putValueDays(accountId: number, days: ValueDay[], replace: boolean): Promise<void>;
  /** The chart for one key ("trend", "low@tcgplayer"…), for the whole collection or a [binder] uid ("~": Unsorted), oldest first. */
  valuePoints(accountId: number, key: string, binder: string | null): Promise<ValuePoint[]>;
  valueDayCount(accountId: number): Promise<number>;
  /** The alert levels the nightly check last reached, per wishlist uid (the app's WishPrice.alertedLevel). */
  alertLevels(accountId: number): Promise<Map<string, number>>;
  setAlertLevels(accountId: number, levels: Map<string, number>): Promise<void>;
  /** Adds lines to the alert history, then drops lines older than a year and beyond the newest 500. */
  addAlertEvents(accountId: number, events: AlertEvent[], now: number): Promise<void>;
  /** The alert history, newest first. */
  alertEvents(accountId: number): Promise<AlertEvent[]>;
  /** A website job's last run (the nightly job's counts, for /api/health). */
  setJob(name: string, at: number, info: Record<string, unknown>): Promise<void>;
  job(name: string): Promise<{ at: number; info: Record<string, unknown> } | null>;
  /** Takes the job [name] if nobody took it in the last [staleMs] (a lock for jobs that may start twice); true if taken. */
  claimJob(name: string, at: number, staleMs: number): Promise<boolean>;
  /** The decks' win turns worked out on the website (lib/winturns.ts), by deck id. */
  winTurns(accountId: number): Promise<Map<number, StoredWinTurn>>;
  putWinTurn(accountId: number, deckId: number, win: StoredWinTurn): Promise<void>;
  /** Forgets the win turns of decks that are gone. */
  dropWinTurns(accountId: number, deckIds: number[]): Promise<void>;
  /** The account's link for this item, if it has one. */
  share(accountId: number, kind: ShareKind, target: string): Promise<ShareLink | null>;
  /** Adds a link; if the item has one already, that one is kept and answered. */
  addShare(link: ShareLink): Promise<ShareLink>;
  shareByCode(code: string): Promise<ShareLink | null>;
  /** The account's links, newest first. */
  shares(accountId: number): Promise<ShareLink[]>;
  /** False when the account has no such link. */
  setShareOn(accountId: number, code: string, on: boolean): Promise<boolean>;
  removeShare(accountId: number, code: string): Promise<boolean>;
  /** Counts an opening of a shared page. */
  openedShare(code: string, now: number): Promise<void>;
  /** The Commander rules of these cards (lib/legality.ts LegalRule as JSON; null: Scryfall doesn't know it), by name key. */
  legalRules(keys: string[]): Promise<Map<string, { rule: string | null; at: number }>>;
  putLegalRules(rows: { key: string; rule: string | null }[], now: number): Promise<void>;
  emailSettings(accountId: number): Promise<EmailSettings | null>;
  setEmailSettings(accountId: number, s: EmailSettings | null): Promise<void>;
  /** The account whose confirmation or stop link this is. */
  emailByToken(kind: "confirm" | "stop", token: string): Promise<{ accountId: number; settings: EmailSettings } | null>;
  /** Accounts with the weekly email on and confirmed. */
  emailAccounts(): Promise<{ accountId: number; settings: EmailSettings }[]>;
  /** Saves (or replaces) a day's pile prices, then thins out the older days (pricehistory.ts daysToDrop). */
  putPriceDay(accountId: number, day: string, prices: Record<string, number>): Promise<void>;
  /** The saved days from [since] on (all when null), oldest first. */
  priceDays(accountId: number, since?: string | null): Promise<PriceDay[]>;
  /** One pile's saved prices (key: weekly.ts pileKey), oldest first (the card window's chart). */
  priceHistory(accountId: number, key: string): Promise<PricePoint[]>;
  /** The oldest and newest saved day over every account, and how many days are saved (the health check). */
  priceDayStats(): Promise<{ oldest: string | null; newest: string | null; rows: number }>;
}

/** What a shared link opens (the share pages, /s/<code>). */
export type ShareKind = "deck" | "game" | "binder" | "collection" | "wishlist";

/**
 * A shared link: a page anyone with the address can open without logging in. [target] is the deck id, the game uid,
 * the binder uid ("~": Unsorted) or "" for the whole collection. Switched off links keep their code, so switching one
 * on again brings the same address back.
 */
export interface ShareLink {
  code: string;
  accountId: number;
  kind: ShareKind;
  target: string;
  on: boolean;
  createdAt: number;
  opened: number;
  lastOpened: number | null;
}

type Json = null | boolean | number | string | Json[] | { [key: string]: Json };

/** The weekly email of an account (Settings → Weekly email). [day]: 0 Monday … 6 Sunday. */
export interface EmailSettings {
  address: string;
  confirmed: boolean;
  enabled: boolean;
  day: number;
  /** The token of the confirmation link (null once confirmed). */
  confirmToken: string | null;
  /** The token of every email's "Stop these emails" link. */
  stopToken: string;
  lastSent: number | null;
  /** What last week's email saw (the decks' game changers and problems), to tell what changed. */
  state: Record<string, Json>;
}

/** One day's price of every pile the account owns: "<scryfallId>|<finish>|<condition>" → one copy in euros. */
export interface PriceDay {
  day: string;
  prices: Record<string, number>;
}

/** A deck's win turn: [key] names the decklist it belongs to; [result] is the app's JSON answer ("null": none). */
export interface StoredWinTurn {
  key: string;
  result: string;
  at: number;
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
  let tags = new Map<string, string[]>();
  let tagsAt: number | null = null;
  const valueDays = new Map<number, Map<string, ValueDay>>();
  const levels = new Map<number, Map<string, number>>();
  const alerts = new Map<number, AlertEvent[]>();
  const jobs = new Map<string, { at: number; info: Record<string, unknown> }>();
  const wins = new Map<number, Map<number, StoredWinTurn>>();
  const shareList: ShareLink[] = [];
  const rules = new Map<string, { rule: string | null; at: number }>();
  const emails = new Map<number, EmailSettings>();
  const priceDays = new Map<number, Map<string, Record<string, number>>>();
  const copyEmail = (s: EmailSettings): EmailSettings => ({ ...s, state: JSON.parse(JSON.stringify(s.state)) });
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
    tagCategories: async (keys) => new Map(keys.flatMap((k) => (tags.has(k) ? [[k, [...tags.get(k)!]] as [string, string[]]] : []))),
    replaceTagCategories: async (rows, now) => {
      tags = new Map(rows.map((r) => [r.key, [...r.categories]]));
      tagsAt = now;
    },
    tagCategoryStats: async () => ({ cards: tags.size, updatedAt: tags.size ? tagsAt : null }),
    putValueDays: async (id, days, replace) => {
      const m = valueDays.get(id) ?? new Map<string, ValueDay>();
      valueDays.set(id, m);
      for (const d of days) if (replace || !m.has(d.day)) m.set(d.day, { day: d.day, cards: d.cards, values: { ...d.values } });
    },
    valuePoints: async (id, key, binder) =>
      [...(valueDays.get(id)?.values() ?? [])]
        .sort((a, b) => a.day.localeCompare(b.day))
        .flatMap((d) => {
          const v = d.values[binder === null ? key : `b:${binder}:${key}`];
          if (v === undefined) return [];
          return [{ day: d.day, value: v, cards: binder === null ? d.cards : (d.values[`b:${binder}:cards`] ?? 0) }];
        }),
    valueDayCount: async (id) => valueDays.get(id)?.size ?? 0,
    alertLevels: async (id) => new Map(levels.get(id) ?? []),
    setAlertLevels: async (id, l) => void levels.set(id, new Map(l)),
    addAlertEvents: async (id, events, now) => {
      const all = [...events, ...(alerts.get(id) ?? [])].filter((e) => now - e.at <= ALERT_KEEP_MS).sort((a, b) => b.at - a.at);
      alerts.set(id, all.slice(0, ALERT_MAX_LINES));
    },
    alertEvents: async (id) => [...(alerts.get(id) ?? [])],
    setJob: async (name, at, info) => void jobs.set(name, { at, info }),
    job: async (name) => jobs.get(name) ?? null,
    claimJob: async (name, at, staleMs) => {
      const j = jobs.get(name);
      if (j && j.at > at - staleMs) return false;
      jobs.set(name, { at, info: {} });
      return true;
    },
    winTurns: async (id) => new Map(wins.get(id) ?? []),
    putWinTurn: async (id, deckId, w) => {
      if (!wins.has(id)) wins.set(id, new Map());
      wins.get(id)!.set(deckId, { ...w });
    },
    dropWinTurns: async (id, deckIds) => deckIds.forEach((d) => wins.get(id)?.delete(d)),
    share: async (id, kind, target) => {
      const l = shareList.find((x) => x.accountId === id && x.kind === kind && x.target === target);
      return l ? { ...l } : null;
    },
    addShare: async (link) => {
      const had = shareList.find((x) => x.accountId === link.accountId && x.kind === link.kind && x.target === link.target);
      if (had) return { ...had };
      shareList.push({ ...link });
      return { ...link };
    },
    shareByCode: async (code) => {
      const l = shareList.find((x) => x.code === code);
      return l ? { ...l } : null;
    },
    shares: async (id) => shareList.filter((x) => x.accountId === id).sort((a, b) => b.createdAt - a.createdAt).map((x) => ({ ...x })),
    setShareOn: async (id, code, on) => {
      const l = shareList.find((x) => x.accountId === id && x.code === code);
      if (l) l.on = on;
      return !!l;
    },
    removeShare: async (id, code) => {
      const i = shareList.findIndex((x) => x.accountId === id && x.code === code);
      if (i >= 0) shareList.splice(i, 1);
      return i >= 0;
    },
    openedShare: async (code, now) => {
      const l = shareList.find((x) => x.code === code);
      if (l) Object.assign(l, { opened: l.opened + 1, lastOpened: now });
    },
    legalRules: async (keys) => new Map(keys.flatMap((k) => (rules.has(k) ? [[k, { ...rules.get(k)! }] as [string, { rule: string | null; at: number }]] : []))),
    putLegalRules: async (rows, now) => rows.forEach((r) => rules.set(r.key, { rule: r.rule, at: now })),
    emailSettings: async (id) => (emails.has(id) ? copyEmail(emails.get(id)!) : null),
    setEmailSettings: async (id, s) => void (s ? emails.set(id, copyEmail(s)) : emails.delete(id)),
    emailByToken: async (kind, token) => {
      for (const [accountId, s] of emails) if (token && (kind === "confirm" ? s.confirmToken : s.stopToken) === token) return { accountId, settings: copyEmail(s) };
      return null;
    },
    emailAccounts: async () => [...emails.entries()].filter(([, s]) => s.enabled && s.confirmed).map(([accountId, s]) => ({ accountId, settings: copyEmail(s) })),
    putPriceDay: async (id, day, prices) => {
      const m = priceDays.get(id) ?? new Map<string, Record<string, number>>();
      priceDays.set(id, m);
      m.set(day, { ...prices });
      for (const old of daysToDrop([...m.keys()], day)) m.delete(old);
    },
    priceDays: async (id, since) =>
      [...(priceDays.get(id)?.entries() ?? [])]
        .filter(([day]) => !since || day >= since)
        .sort((a, b) => a[0].localeCompare(b[0]))
        .map(([day, prices]) => ({ day, prices: { ...prices } })),
    priceHistory: async (id, key) =>
      [...(priceDays.get(id)?.entries() ?? [])]
        .filter(([, p]) => p[key] != null)
        .sort((a, b) => a[0].localeCompare(b[0]))
        .map(([day, p]) => ({ day, eur: p[key] })),
    priceDayStats: async () => {
      const days = [...priceDays.values()].flatMap((m) => [...m.keys()]).sort();
      return { oldest: days[0] ?? null, newest: days[days.length - 1] ?? null, rows: days.length };
    },
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
  // card_categories held Archidekt's answers for a few hours on 2026-11-06; replaced by card_tags (Scryfall only).
  `DROP TABLE IF EXISTS card_categories`,
  `CREATE TABLE IF NOT EXISTS card_tags (
     name_key TEXT PRIMARY KEY,
     categories TEXT NOT NULL,
     updated_at BIGINT NOT NULL)`,
  // Step D (2026-11-06): the value over time, price alerts reached at night, and the website jobs' last runs.
  `CREATE TABLE IF NOT EXISTS value_history (
     account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
     day TEXT NOT NULL,
     cards INTEGER NOT NULL,
     vals JSONB NOT NULL,
     PRIMARY KEY (account_id, day))`,
  `CREATE TABLE IF NOT EXISTS alert_levels (
     account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
     wish_uid TEXT NOT NULL,
     level DOUBLE PRECISION NOT NULL,
     PRIMARY KEY (account_id, wish_uid))`,
  `CREATE TABLE IF NOT EXISTS alert_events (
     id BIGSERIAL PRIMARY KEY,
     account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
     at BIGINT NOT NULL,
     event JSONB NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS jobs (
     name TEXT PRIMARY KEY,
     at BIGINT NOT NULL,
     info JSONB NOT NULL)`,
  // 2026-11-07: decks' win turns, worked out on the website without opening the deck.
  `CREATE TABLE IF NOT EXISTS win_turns (
     account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
     deck_id BIGINT NOT NULL,
     list_key TEXT NOT NULL,
     result TEXT NOT NULL,
     at BIGINT NOT NULL,
     PRIMARY KEY (account_id, deck_id))`,
  // 2026-11-12: shared links, the cards' Commander rules, the weekly email and the daily card prices behind it.
  `CREATE TABLE IF NOT EXISTS share_links (
     code TEXT PRIMARY KEY,
     account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
     kind TEXT NOT NULL,
     target TEXT NOT NULL,
     is_on BOOLEAN NOT NULL,
     created_at BIGINT NOT NULL,
     opened INTEGER NOT NULL DEFAULT 0,
     last_opened BIGINT,
     UNIQUE (account_id, kind, target))`,
  `CREATE TABLE IF NOT EXISTS legal_rules (
     name_key TEXT PRIMARY KEY,
     rule TEXT,
     at BIGINT NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS email_settings (
     account_id INTEGER PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
     settings JSONB NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS card_price_days (
     account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
     day TEXT NOT NULL,
     prices JSONB NOT NULL,
     PRIMARY KEY (account_id, day))`,
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
    tagCategories: async (keys) =>
      new Map(
        keys.length === 0
          ? []
          : (await q("SELECT name_key, categories FROM card_tags WHERE name_key = ANY($1::text[])", [keys])).map((r) => [
              String(r.name_key),
              String(r.categories).split("\n").filter(Boolean),
            ]),
      ),
    replaceTagCategories: async (rows, now) => {
      for (let i = 0; i < rows.length; i += 2000) {
        const part = rows.slice(i, i + 2000);
        await q(
          `INSERT INTO card_tags (name_key, categories, updated_at)
           SELECT k, c, $3::bigint FROM unnest($1::text[], $2::text[]) AS t(k, c)
           ON CONFLICT (name_key) DO UPDATE SET categories = EXCLUDED.categories, updated_at = EXCLUDED.updated_at`,
          [part.map((r) => r.key), part.map((r) => r.categories.join("\n")), now],
        );
      }
      await q("DELETE FROM card_tags WHERE updated_at < $1::bigint", [now]);
    },
    tagCategoryStats: async () => {
      const r = (await q("SELECT COUNT(*) AS n, MAX(updated_at) AS at FROM card_tags"))[0];
      return { cards: num(r.n), updatedAt: r.at === null ? null : num(r.at) };
    },
    putValueDays: async (id, days, replace) => {
      for (let i = 0; i < days.length; i += 500) {
        const part = days.slice(i, i + 500);
        await q(
          `INSERT INTO value_history (account_id, day, cards, vals)
           SELECT $1::int, d, c, v::jsonb FROM unnest($2::text[], $3::int[], $4::text[]) AS t(d, c, v)
           ON CONFLICT (account_id, day) DO ${replace ? "UPDATE SET cards = EXCLUDED.cards, vals = EXCLUDED.vals" : "NOTHING"}`,
          [id, part.map((d) => d.day), part.map((d) => Math.round(d.cards)), part.map((d) => JSON.stringify(d.values))],
        );
      }
    },
    valuePoints: async (id, key, binder) => {
      const k = binder === null ? key : `b:${binder}:${key}`;
      const rows = await q(
        `SELECT day, cards, (vals->>$2)::float8 AS v, (vals->>$3)::float8 AS c FROM value_history
         WHERE account_id = $1 AND vals->>$2 IS NOT NULL ORDER BY day`,
        [id, k, binder === null ? "-" : `b:${binder}:cards`],
      );
      return rows.map((r) => ({ day: String(r.day), value: num(r.v), cards: binder === null ? num(r.cards) : r.c === null ? 0 : num(r.c) }));
    },
    valueDayCount: async (id) => num((await q("SELECT COUNT(*) AS n FROM value_history WHERE account_id = $1", [id]))[0].n),
    alertLevels: async (id) => new Map((await q("SELECT wish_uid, level FROM alert_levels WHERE account_id = $1", [id])).map((r) => [String(r.wish_uid), num(r.level)])),
    setAlertLevels: async (id, l) => {
      await q("DELETE FROM alert_levels WHERE account_id = $1", [id]);
      if (l.size)
        await q("INSERT INTO alert_levels (account_id, wish_uid, level) SELECT $1::int, u, v FROM unnest($2::text[], $3::float8[]) AS t(u, v)", [id, [...l.keys()], [...l.values()]]);
    },
    addAlertEvents: async (id, events, now) => {
      if (events.length)
        await q("INSERT INTO alert_events (account_id, at, event) SELECT $1::int, a, e::jsonb FROM unnest($2::bigint[], $3::text[]) AS t(a, e)", [
          id,
          events.map((e) => e.at),
          events.map((e) => JSON.stringify(e)),
        ]);
      await q(
        `DELETE FROM alert_events WHERE account_id = $1 AND (at < $2::bigint
           OR id NOT IN (SELECT id FROM alert_events WHERE account_id = $1 ORDER BY at DESC, id DESC LIMIT $3))`,
        [id, now - ALERT_KEEP_MS, ALERT_MAX_LINES],
      );
    },
    alertEvents: async (id) => (await q("SELECT event FROM alert_events WHERE account_id = $1 ORDER BY at DESC, id DESC", [id])).map((r) => r.event as AlertEvent),
    setJob: async (name, at, info) =>
      void (await q("INSERT INTO jobs (name, at, info) VALUES ($1, $2, $3::jsonb) ON CONFLICT (name) DO UPDATE SET at = EXCLUDED.at, info = EXCLUDED.info", [name, at, JSON.stringify(info)])),
    job: async (name) => {
      const r = (await q("SELECT at, info FROM jobs WHERE name = $1", [name]))[0];
      return r ? { at: num(r.at), info: r.info as Record<string, unknown> } : null;
    },
    claimJob: async (name, at, staleMs) =>
      (
        await q(
          "INSERT INTO jobs (name, at, info) VALUES ($1, $2::bigint, '{}'::jsonb) ON CONFLICT (name) DO UPDATE SET at = EXCLUDED.at, info = EXCLUDED.info WHERE jobs.at <= $2::bigint - $3::bigint RETURNING name",
          [name, at, staleMs],
        )
      ).length === 1,
    winTurns: async (id) =>
      new Map(
        (await q("SELECT deck_id, list_key, result, at FROM win_turns WHERE account_id = $1", [id])).map((r) => [num(r.deck_id), { key: String(r.list_key), result: String(r.result), at: num(r.at) }]),
      ),
    putWinTurn: async (id, deckId, w) =>
      void (await q(
        "INSERT INTO win_turns (account_id, deck_id, list_key, result, at) VALUES ($1, $2, $3, $4, $5) ON CONFLICT (account_id, deck_id) DO UPDATE SET list_key = EXCLUDED.list_key, result = EXCLUDED.result, at = EXCLUDED.at",
        [id, deckId, w.key, w.result, w.at],
      )),
    dropWinTurns: async (id, deckIds) => {
      if (deckIds.length) await q("DELETE FROM win_turns WHERE account_id = $1 AND deck_id = ANY($2::bigint[])", [id, deckIds]);
    },
    share: async (id, kind, target) => {
      const r = (await q("SELECT * FROM share_links WHERE account_id = $1 AND kind = $2 AND target = $3", [id, kind, target]))[0];
      return r ? shareRow(r) : null;
    },
    addShare: async (l) => {
      await q(
        "INSERT INTO share_links (code, account_id, kind, target, is_on, created_at) VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT (account_id, kind, target) DO NOTHING",
        [l.code, l.accountId, l.kind, l.target, l.on, l.createdAt],
      );
      return shareRow((await q("SELECT * FROM share_links WHERE account_id = $1 AND kind = $2 AND target = $3", [l.accountId, l.kind, l.target]))[0]);
    },
    shareByCode: async (code) => {
      const r = (await q("SELECT * FROM share_links WHERE code = $1", [code]))[0];
      return r ? shareRow(r) : null;
    },
    shares: async (id) => (await q("SELECT * FROM share_links WHERE account_id = $1 ORDER BY created_at DESC", [id])).map(shareRow),
    setShareOn: async (id, code, on) => (await q("UPDATE share_links SET is_on = $3 WHERE account_id = $1 AND code = $2 RETURNING code", [id, code, on])).length === 1,
    removeShare: async (id, code) => (await q("DELETE FROM share_links WHERE account_id = $1 AND code = $2 RETURNING code", [id, code])).length === 1,
    openedShare: async (code, now) => void (await q("UPDATE share_links SET opened = opened + 1, last_opened = $2 WHERE code = $1", [code, now])),
    legalRules: async (keys) =>
      new Map(
        keys.length === 0
          ? []
          : (await q("SELECT name_key, rule, at FROM legal_rules WHERE name_key = ANY($1::text[])", [keys])).map((r) => [
              String(r.name_key),
              { rule: r.rule === null ? null : String(r.rule), at: num(r.at) },
            ]),
      ),
    putLegalRules: async (rows, now) => {
      for (let i = 0; i < rows.length; i += 1000) {
        const part = rows.slice(i, i + 1000);
        await q(
          `INSERT INTO legal_rules (name_key, rule, at) SELECT k, r, $3::bigint FROM unnest($1::text[], $2::text[]) AS t(k, r)
           ON CONFLICT (name_key) DO UPDATE SET rule = EXCLUDED.rule, at = EXCLUDED.at`,
          [part.map((r) => r.key), part.map((r) => r.rule), now],
        );
      }
    },
    emailSettings: async (id) => {
      const r = (await q("SELECT settings FROM email_settings WHERE account_id = $1", [id]))[0];
      return r ? (r.settings as EmailSettings) : null;
    },
    setEmailSettings: async (id, s) =>
      void (s
        ? await q("INSERT INTO email_settings (account_id, settings) VALUES ($1, $2::jsonb) ON CONFLICT (account_id) DO UPDATE SET settings = EXCLUDED.settings", [id, JSON.stringify(s)])
        : await q("DELETE FROM email_settings WHERE account_id = $1", [id])),
    emailByToken: async (kind, token) => {
      if (!token) return null;
      const r = (await q("SELECT account_id, settings FROM email_settings WHERE settings->>$1 = $2", [kind === "confirm" ? "confirmToken" : "stopToken", token]))[0];
      return r ? { accountId: num(r.account_id), settings: r.settings as EmailSettings } : null;
    },
    emailAccounts: async () =>
      (await q("SELECT account_id, settings FROM email_settings WHERE (settings->>'enabled')::boolean AND (settings->>'confirmed')::boolean ORDER BY account_id")).map((r) => ({
        accountId: num(r.account_id),
        settings: r.settings as EmailSettings,
      })),
    putPriceDay: async (id, day, prices) => {
      await q("INSERT INTO card_price_days (account_id, day, prices) VALUES ($1, $2, $3::jsonb) ON CONFLICT (account_id, day) DO UPDATE SET prices = EXCLUDED.prices", [id, day, JSON.stringify(prices)]);
      const days = (await q("SELECT day FROM card_price_days WHERE account_id = $1", [id])).map((r) => String(r.day));
      const drop = daysToDrop(days, day);
      if (drop.length) await q("DELETE FROM card_price_days WHERE account_id = $1 AND day = ANY($2::text[])", [id, drop]);
    },
    priceDays: async (id, since) =>
      (await q("SELECT day, prices FROM card_price_days WHERE account_id = $1 AND day >= $2 ORDER BY day", [id, since ?? ""])).map((r) => ({
        day: String(r.day),
        prices: r.prices as Record<string, number>,
      })),
    priceHistory: async (id, key) =>
      (await q("SELECT day, prices->>$2 AS eur FROM card_price_days WHERE account_id = $1 AND prices->>$2 IS NOT NULL ORDER BY day", [id, key])).map((r) => ({
        day: String(r.day),
        eur: Number(r.eur),
      })),
    priceDayStats: async () => {
      const r = (await q("SELECT MIN(day) AS oldest, MAX(day) AS newest, COUNT(*) AS n FROM card_price_days"))[0];
      return { oldest: r.oldest == null ? null : String(r.oldest), newest: r.newest == null ? null : String(r.newest), rows: num(r.n) };
    },
  };
}

const shareRow = (r: Row): ShareLink => ({
  code: String(r.code),
  accountId: num(r.account_id),
  kind: String(r.kind) as ShareKind,
  target: String(r.target),
  on: Boolean(r.is_on),
  createdAt: num(r.created_at),
  opened: num(r.opened),
  lastOpened: r.last_opened === null || r.last_opened === undefined ? null : num(r.last_opened),
});

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
