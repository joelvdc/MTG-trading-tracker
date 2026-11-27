// Card Kingdom's prices, from the slimmed-down copy of its price list that this repository's daily GitHub job makes
// (the app reads the same copy: PriceSources.kt readCardKingdomCopy). Kept as one private Vercel Blob.
import { gunzipSync, gzipSync } from "node:zlib";
import { get, head, put } from "@vercel/blob";
import { blobCache, countDownload, quotedEtag, sameEtag } from "./blobcache";
import { USER_AGENT } from "./config";

export const CARD_KINGDOM_COPY_URL =
  "https://raw.githubusercontent.com/joelvdc/MTG-trading-tracker/card-kingdom-prices/cardkingdom.tsv.gz";
export const CK_BLOB = "prices/cardkingdom.json.gz";

/** retail, buy, nm, ex, vg, g (US dollars, null where missing), then the card's page path on cardkingdom.com. */
export type CkRow = [number | null, number | null, number | null, number | null, number | null, number | null, string];

export interface CkFile {
  /** The date Card Kingdom made the list. */
  listDate: string;
  madeAt: number;
  /** "<scryfall id>|N" or "|F" → the row. Etched foils have no entry (as in the app). */
  rows: Record<string, CkRow>;
}

/** "#card-kingdom<TAB><date>", then per line scryfall_id, F or N, retail, buy, nm, ex, vg, g, url path. */
export function parseCopy(text: string, now: number): CkFile {
  const lines = text.split("\n");
  const first = lines[0] ?? "";
  if (!first.startsWith("#card-kingdom")) throw new Error("Not a Card Kingdom copy.");
  const rows: CkFile["rows"] = {};
  for (let n = 1; n < lines.length; n++) {
    const f = lines[n].replace(/\r$/, "").split("\t");
    if (f.length < 9 || !f[0]) continue;
    const p = (i: number) => {
      const v = Number(f[i]);
      return f[i] !== "" && Number.isFinite(v) && v > 0 ? v : null;
    };
    rows[`${f[0]}|${f[1] === "F" ? "F" : "N"}`] = [p(2), p(3), p(4), p(5), p(6), p(7), f[8] ?? ""];
  }
  return { listDate: first.split("\t")[1]?.trim() ?? "", madeAt: now, rows };
}

// Kept in memory, downloaded again only when it changed (lib/blobcache.ts, 2026-11-27).
const cache = blobCache<CkFile>(CK_BLOB);

export async function loadCardKingdom(): Promise<CkFile | null> {
  return cache.load();
}

/** The daily job: downloads the copy and keeps it as the Blob. */
export async function buildCardKingdom(): Promise<{ rows: number; listDate: string; bytes: number }> {
  const r = await fetch(CARD_KINGDOM_COPY_URL, { headers: { "User-Agent": USER_AGENT }, cache: "no-store", signal: AbortSignal.timeout(60_000) });
  if (!r.ok) throw new Error(`Card Kingdom copy: HTTP ${r.status}`);
  const text = gunzipSync(Buffer.from(await r.arrayBuffer())).toString("utf8");
  const file = parseCopy(text, Date.now());
  const count = Object.keys(file.rows).length;
  if (count < 10_000) throw new Error("The Card Kingdom copy looks incomplete; kept yesterday's prices.");
  const body = gzipSync(Buffer.from(JSON.stringify(file), "utf8"));
  const saved = await put(CK_BLOB, body, { access: "private", allowOverwrite: true, addRandomSuffix: false, contentType: "application/gzip" });
  cache.keep(file, saved.etag ?? null);
  return { rows: count, listDate: file.listDate, bytes: body.length };
}

/** Older than this, the phones get their Card Kingdom prices elsewhere (the daily job stopped). */
export const PHONE_MAX_AGE_MS = 48 * 3600_000;

/**
 * The Card Kingdom file exactly as stored (gzipped JSON, [CkFile]), for the phones (app 1.93, /api/app/cardkingdom):
 * "same" when the phone already has it (its ETag), "stale" when the daily job hasn't refreshed it for
 * [PHONE_MAX_AGE_MS], null when there's none yet.
 */
export async function cardKingdomBlob(
  etag: string | null,
  now = Date.now(),
): Promise<{ stream: ReadableStream<Uint8Array>; etag: string; size: number } | "same" | "stale" | null> {
  // Its version first (a few bytes): a phone that has it, or a file too old, needs no download (2026-11-27).
  const h = await head(CK_BLOB).catch(() => null);
  if (!h) return null;
  if (now - h.uploadedAt.getTime() > PHONE_MAX_AGE_MS) return "stale";
  if (sameEtag(h.etag, etag)) return "same";
  const res = await get(CK_BLOB, { access: "private", useCache: false });
  if (!res || res.statusCode !== 200) return null;
  void countDownload(CK_BLOB, res.blob.size, now);
  return { stream: res.stream, etag: quotedEtag(res.blob.etag), size: res.blob.size };
}
