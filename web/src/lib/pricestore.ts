// The daily price file in Vercel Blob (private): made by the cron job, read by the pages.
import { gunzipSync, gzipSync } from "node:zlib";
import { get, head, put } from "@vercel/blob";
import { blobCache, countDownload, quotedEtag, sameEtag } from "./blobcache";
import { CARDMARKET_URL, compactGuide, ECB_URL, FALLBACK_RATES, parseEcb, PRICE_BLOB, type PriceFile } from "./prices";
import { USER_AGENT } from "./config";

// Kept in memory, downloaded again only when it changed (lib/blobcache.ts, 2026-11-27).
const cache = blobCache<PriceFile>(PRICE_BLOB);

/** The newest price file, or null if there's none yet (the pages then use Scryfall's prices saved with each card). */
export async function loadPrices(): Promise<PriceFile | null> {
  return cache.load();
}

export { sameEtag };

/**
 * The price file exactly as stored (gzipped JSON, about 2 MB), for the phones (app 1.81): null when there's none yet,
 * "same" when it's still the version with [etag].
 */
export async function priceBlob(etag: string | null): Promise<{ stream: ReadableStream<Uint8Array>; etag: string; size: number } | "same" | null> {
  // Its version first (a few bytes): a phone that has it needs no download (2026-11-27).
  const h = await head(PRICE_BLOB).catch(() => null);
  if (!h) return null;
  if (sameEtag(h.etag, etag)) return "same";
  const res = await get(PRICE_BLOB, { access: "private", useCache: false });
  if (!res || res.statusCode !== 200) return null;
  void countDownload(PRICE_BLOB, res.blob.size);
  return { stream: res.stream, etag: quotedEtag(res.blob.etag), size: res.blob.size };
}

/** Downloads today's guide and rates and saves the compact file. Returns its size and product count. */
export async function buildPrices(): Promise<{ products: number; bytes: number; createdAt: string; ratesDate: string }> {
  const headers = { "User-Agent": USER_AGENT };
  const guideRes = await fetch(CARDMARKET_URL, { headers, cache: "no-store", signal: AbortSignal.timeout(120_000) });
  if (!guideRes.ok) throw new Error(`Cardmarket's price guide: HTTP ${guideRes.status}`);
  const { createdAt, products } = compactGuide(await guideRes.json());
  if (Object.keys(products).length < 10_000) throw new Error("Cardmarket's price guide looks incomplete; kept yesterday's prices.");
  let rates = FALLBACK_RATES;
  try {
    const ecb = await fetch(ECB_URL, { headers, cache: "no-store", signal: AbortSignal.timeout(30_000) });
    rates = (ecb.ok && parseEcb(await ecb.text())) || (await loadPrices())?.rates || FALLBACK_RATES;
  } catch {
    rates = (await loadPrices())?.rates ?? FALLBACK_RATES;
  }
  const file: PriceFile = { createdAt, madeAt: Date.now(), rates, products };
  const body = gzipSync(Buffer.from(JSON.stringify(file), "utf8"));
  const r = await put(PRICE_BLOB, body, { access: "private", allowOverwrite: true, addRandomSuffix: false, contentType: "application/gzip" });
  cache.keep(file, r.etag ?? null);
  return { products: Object.keys(products).length, bytes: body.length, createdAt, ratesDate: rates.date };
}
