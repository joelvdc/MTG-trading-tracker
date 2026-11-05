// The app's Nextcloud sync file (`sync.json.gz`), as the Android app writes it (app: data/Sync.kt).
//
// The website edits the file in place: it keeps every object it reads as it is (including fields this code doesn't
// know about) and only changes the fields it edits, so it can't drop data written by a newer app.
import { gunzipSync, gzipSync } from "node:zlib";

/** The newest file format this website understands (the app's SyncFile.FORMAT). */
export const WEB_FORMAT = 12;
/** How the website signs the files it writes (`writtenBy`). */
export const WRITER = "MTG Trader website";
/** Deletions older than this are forgotten by the phones (SyncMerge.KEEP_DELETIONS_MS). */
export const KEEP_DELETIONS_MS = 180 * 24 * 60 * 60 * 1000;

export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
export type Obj = { [key: string]: Json };

/** A printing, as the app keeps it (CardRef). */
export interface CardRef {
  scryfallId: string;
  name: string;
  setCode: string;
  setName: string;
  collectorNumber: string;
  rarity: string;
  imageUrl: string | null;
  cardmarketId: number | null;
  fallbackEur: number | null;
  fallbackEurFoil: number | null;
  hasNonFoil: boolean;
  hasFoil: boolean;
  foilType: string | null;
  hasEtched: boolean;
  flavorName: string | null;
  cardmarketFoilId: number | null;
}

/** A wishlist entry (WishlistItem). */
export interface WishlistItem {
  id: number;
  card: CardRef;
  foil: boolean;
  quantity: number;
  anyPrinting: boolean;
  notes: string | null;
  addedAt: number;
  ownedAtAdd: number;
  orderedAt: number | null;
  alertBelow: number | null;
  alertCurrency: string | null;
  receivedAt: number | null;
  receivedFrom: string | null;
  uid: string | null;
  updatedAt: number;
}

export interface SyncDeletion {
  uid: string;
  deletedAt: number;
}

/** A collection stack (the app's CollectionItem; ids are local to each phone, so 0 here). */
export interface CollectionItem {
  id: number;
  card: CardRef;
  foil: boolean;
  condition: string;
  language: string;
  quantity: number;
  addedAt: number;
  /** Only ever true together with [foil]. */
  etched: boolean;
  binderId: number;
  uid: string | null;
  updatedAt: number;
  notes: string | null;
  /** What one copy cost, in euros. */
  purchasePrice: number | null;
  signed: boolean;
  altered: boolean;
  /** The purchase price as typed, when typed in another currency than euros. */
  paidTyped: number | null;
  paidCurrency: string | null;
}

/** A collection stack and the uid of the binder it's in (null: Unsorted) (SyncStack). */
export interface SyncStack {
  item: CollectionItem;
  binder: string | null;
}

export interface Binder {
  id: number;
  name: string;
  createdAt: number;
  uid: string | null;
  updatedAt: number;
}

export interface SyncData extends Obj {}

export interface SyncFile {
  format: number;
  app: string;
  writtenAt: number;
  writtenBy: string;
  data: Obj;
}

export class SyncFormatError extends Error {}

export function decode(bytes: Uint8Array): SyncFile {
  const text = gunzipSync(bytes).toString("utf8");
  return parse(text);
}

export function parse(text: string): SyncFile {
  const file = JSON.parse(text) as SyncFile;
  if (typeof file !== "object" || file === null || typeof file.data !== "object" || file.data === null) {
    throw new SyncFormatError("This isn't an MTG Trader sync file.");
  }
  if (typeof file.format !== "number") throw new SyncFormatError("The sync file has no format number.");
  return file;
}

export function encode(file: SyncFile): Buffer {
  return gzipSync(Buffer.from(JSON.stringify(file), "utf8"));
}

/** Whether the website may write this file: not when a newer app wrote it in a format the website doesn't know. */
export function canWrite(file: SyncFile): boolean {
  return file.format <= WEB_FORMAT;
}

export function wishlist(file: SyncFile): WishlistItem[] {
  return ((file.data.wishlist as Json[] | undefined) ?? []) as unknown as WishlistItem[];
}

export function collection(file: SyncFile): SyncStack[] {
  return ((file.data.collection as Json[] | undefined) ?? []) as unknown as SyncStack[];
}

export function binders(file: SyncFile): Binder[] {
  return ((file.data.binders as Json[] | undefined) ?? []) as unknown as Binder[];
}

export function deletions(file: SyncFile): SyncDeletion[] {
  return ((file.data.deletions as Json[] | undefined) ?? []) as unknown as SyncDeletion[];
}

/** The app's synced settings (SyncPrefs.values): "priceType", "priceSource", "currency"… */
export function prefs(file: SyncFile): Record<string, string> {
  const p = file.data.prefs as { values?: Record<string, string> } | undefined;
  return p?.values ?? {};
}

/** A new item id like the app's: 32 lowercase hex characters (lower(hex(randomblob(16)))). */
export function newUid(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** The time stamp for an edited item: now, but always later than its last change (the app's BUMP). */
export function bump(updatedAt: number, now: number): number {
  return Math.max(now, updatedAt + 1);
}

/**
 * Records that [uid] was deleted at [now] (replacing an older record of it), drops deletions the phones have
 * forgotten, and keeps the list sorted by uid like the app does.
 */
export function addDeletion(file: SyncFile, uid: string, now: number): void {
  const cutoff = now - KEEP_DELETIONS_MS;
  const list = deletions(file).filter((d) => d.uid !== uid && d.deletedAt >= cutoff);
  list.push({ uid, deletedAt: now });
  list.sort((a, b) => compareUid(a.uid, b.uid));
  file.data.deletions = list as unknown as Json;
}

/**
 * Changes one of the app's synced settings. The phones take the newest settings as a whole (SyncPrefs.updatedAt), so
 * the time stamp moves on with it.
 */
export function setPref(file: SyncFile, key: string, value: string, now: number): void {
  const p = (file.data.prefs as { updatedAt?: number; values?: Record<string, string> } | undefined) ?? {};
  const out = { ...p, updatedAt: bump(p.updatedAt ?? 0, now), values: { ...(p.values ?? {}), [key]: value } };
  file.data.prefs = out as unknown as Json;
}

/** Kotlin's String.compareTo for the ASCII ids used here. */
export function compareUid(a: string | null, b: string | null): number {
  if (a === b) return 0;
  if (a === null) return -1;
  if (b === null) return 1;
  return a < b ? -1 : 1;
}

/** Marks the file as written by the website at [now]. The format stays as it was, so phones see no change of version. */
export function stamp(file: SyncFile, now: number): void {
  file.writtenAt = now;
  file.writtenBy = WRITER;
}
