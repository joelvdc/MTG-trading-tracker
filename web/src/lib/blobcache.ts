// The gzipped JSON files in Vercel Blob that the pages read (prices, card details, the precon index), kept in memory
// and downloaded again only when they changed (2026-11-27). Until then every running copy of the website downloaded
// each file again every 10 minutes although they change about once a day, which used most of the free plan's Blob
// transfer. Now a copy asks for the file's version (a "head", a few bytes) at most every [CHECK_MS] and downloads it
// only when that changed. Each real download is counted per day ([blobDownloads], shown by /api/health).
import { gunzipSync } from "node:zlib";
import { get, head } from "@vercel/blob";
import { accountStore } from "./accounts";

/** How long a kept file is used before its version is checked again. */
export const CHECK_MS = 10 * 60 * 1000;

/** Whether two ETags name the same version (ignoring quotes and a weak "W/" mark). */
export function sameEtag(a: string | null | undefined, b: string | null | undefined): boolean {
  const norm = (e: string) => e.trim().replace(/^W\//, "").replace(/^"|"$/g, "");
  return !!a && !!b && norm(a) === norm(b);
}

/** An ETag as the phones get it: quoted, without a weak mark. */
export const quotedEtag = (etag: string) => `"${etag.replace(/^W\//, "").replace(/^"|"$/g, "")}"`;

export interface BlobCache<T> {
  /** The file, or null when there's none (or it can't be read and none is kept); [fresh] checks its version now. */
  load(fresh?: boolean, now?: number): Promise<T | null>;
  /** Keeps [file] as version [etag], right after saving it. */
  keep(file: T, etag: string | null, now?: number): void;
  /** Forgets the kept file (tests). */
  reset(): void;
}

export function blobCache<T>(path: string): BlobCache<T> {
  let kept: { file: T; etag: string | null; checked: number } | null = null;
  return {
    async load(fresh = false, now = Date.now()) {
      if (!fresh && kept && now - kept.checked < CHECK_MS) return kept.file;
      try {
        if (kept?.etag) {
          const h = await head(path).catch(() => null);
          if (h && sameEtag(h.etag, kept.etag)) {
            kept.checked = now;
            return kept.file;
          }
        }
        const res = await get(path, { access: "private", useCache: false });
        if (!res || !res.stream) return kept?.file ?? null;
        const gz = Buffer.from(await new Response(res.stream).arrayBuffer());
        const file = JSON.parse(gunzipSync(gz).toString("utf8")) as T;
        kept = { file, etag: res.blob?.etag ?? null, checked: now };
        void countDownload(path, gz.length, now);
        return file;
      } catch {
        return kept?.file ?? null;
      }
    },
    keep(file, etag, now = Date.now()) {
      kept = { file, etag, checked: now };
    },
    reset() {
      kept = null;
    },
  };
}

/** The day's Blob downloads by the pages and the phones: how many, how many bytes, and per file. */
export interface BlobDownloads {
  day: string;
  count: number;
  bytes: number;
  files: Record<string, number>;
}

const JOB = "blobdownloads";

/** Counts one download of [path] ([bytes]) for today; never fails the page. */
export async function countDownload(path: string, bytes: number, now = Date.now()): Promise<void> {
  try {
    const store = accountStore();
    if (!store) return;
    const day = new Date(now).toISOString().slice(0, 10);
    const old = (await store.job(JOB))?.info as unknown as BlobDownloads | undefined;
    const d: BlobDownloads = old?.day === day ? old : { day, count: 0, bytes: 0, files: {} };
    d.count++;
    d.bytes += bytes;
    d.files[path] = (d.files[path] ?? 0) + 1;
    await store.setJob(JOB, now, d as unknown as Record<string, unknown>);
  } catch {
    // Only a statistic.
  }
}

/** Today's downloads so far (none yet today: zero). */
export async function blobDownloads(now = Date.now()): Promise<BlobDownloads | null> {
  const store = accountStore();
  if (!store) return null;
  const day = new Date(now).toISOString().slice(0, 10);
  const d = (await store.job(JOB).catch(() => null))?.info as unknown as BlobDownloads | undefined;
  return d?.day === day ? d : { day, count: 0, bytes: 0, files: {} };
}
