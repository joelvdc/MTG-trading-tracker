// The daily price file in Vercel Blob (private): made by the cron job, read by the pages.
import { gunzipSync, gzipSync } from "node:zlib";
import { get, put } from "@vercel/blob";
import { CARDMARKET_URL, compactGuide, ECB_URL, FALLBACK_RATES, parseEcb, PRICE_BLOB, type PriceFile } from "./prices";
import { USER_AGENT } from "./config";

let cache: { file: PriceFile; at: number } | null = null;
const CACHE_MS = 10 * 60 * 1000;

/** The newest price file, or null if there's none yet (the pages then use Scryfall's prices saved with each card). */
export async function loadPrices(): Promise<PriceFile | null> {
  if (cache && Date.now() - cache.at < CACHE_MS) return cache.file;
  try {
    const res = await get(PRICE_BLOB, { access: "private", useCache: false });
    if (!res || !res.stream) return cache?.file ?? null;
    const gz = Buffer.from(await new Response(res.stream).arrayBuffer());
    const file = JSON.parse(gunzipSync(gz).toString("utf8")) as PriceFile;
    cache = { file, at: Date.now() };
    return file;
  } catch {
    return cache?.file ?? null;
  }
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
  await put(PRICE_BLOB, body, { access: "private", allowOverwrite: true, addRandomSuffix: false, contentType: "application/gzip" });
  cache = { file, at: Date.now() };
  return { products: Object.keys(products).length, bytes: body.length, createdAt, ratesDate: rates.date };
}
