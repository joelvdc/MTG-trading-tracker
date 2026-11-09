// Card details the sync file doesn't hold (colours, type, mana value, EDHREC rank, release date, Game Changer) and
// TCGplayer's prices, from Scryfall, as the app keeps them in its CardInfo table (CardInfo.kt toInfo, PriceSources.kt
// tcgplayerRows). Kept in one private Vercel Blob for every card the website has looked up; the daily job refreshes
// the TCGplayer prices.
import { gunzipSync, gzipSync } from "node:zlib";
import { get, put } from "@vercel/blob";
import { USER_AGENT } from "./config";
import { scryFetch } from "./scrylimit";

export const DETAILS_BLOB = "cards/details.json.gz";
/** Scryfall's /cards/collection takes at most this many cards per request. */
export const BATCH = 75;

/** One printing's details, short keys to keep the file small. */
export interface CardInfo {
  /** Colours in WUBRG order ("BG"; "" for colourless). */
  c: string;
  /** Colour identity in WUBRG order. */
  i: string;
  /** Type line, faces joined with " // ". */
  t: string;
  /** Mana value. */
  v: number;
  /** EDHREC rank (1 = most played), or null. */
  r: number | null;
  /** Release date of the printing ("2021-04-23"). */
  d: string;
  /** On Wizards' Game Changers list. */
  g: boolean;
  /** TCGplayer market prices in US dollars: normal, foil, etched. */
  u: [number | null, number | null, number | null];
  /** The printing's TCGplayer page (Scryfall's purchase link), or null. */
  tu: string | null;
  /** When it was looked up (ms). */
  at: number;
}

export interface DetailsFile {
  madeAt: number;
  cards: Record<string, CardInfo>;
}

interface ScryFace {
  colors?: string[];
  type_line?: string;
}

export interface ScryDetail {
  id: string;
  colors?: string[];
  color_identity?: string[];
  type_line?: string;
  card_faces?: ScryFace[];
  cmc?: number;
  edhrec_rank?: number;
  released_at?: string;
  game_changer?: boolean;
  prices?: { usd?: string | null; usd_foil?: string | null; usd_etched?: string | null };
  purchase_uris?: { tcgplayer?: string };
}

const ORDER = "WUBRG";

/** The app's MtgColors.normalize: the colours in WUBRG order. */
export function normalizeColors(colors: string[]): string {
  return [...ORDER].filter((c) => colors.includes(c)).join("");
}

function usd(s: string | null | undefined): number | null {
  const n = s != null && s !== "" ? Number(s) : NaN;
  return Number.isFinite(n) && n > 0 ? n : null;
}

export function toInfo(c: ScryDetail, now: number): CardInfo {
  const faces = c.card_faces ?? [];
  return {
    c: normalizeColors(c.colors ?? faces.flatMap((f) => f.colors ?? [])),
    i: normalizeColors(c.color_identity ?? []),
    t: c.type_line || faces.map((f) => f.type_line ?? "").join(" // "),
    v: c.cmc ?? 0,
    r: c.edhrec_rank ?? null,
    d: c.released_at ?? "",
    g: c.game_changer === true,
    u: [usd(c.prices?.usd), usd(c.prices?.usd_foil), usd(c.prices?.usd_etched)],
    tu: c.purchase_uris?.tcgplayer ?? null,
    at: now,
  };
}

/** An empty entry for a printing Scryfall no longer knows, so it isn't asked for again and again (like the app). */
export function unknownInfo(now: number): CardInfo {
  return { c: "", i: "", t: "", v: 0, r: null, d: "", g: false, u: [null, null, null], tu: null, at: now };
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export function validIds(ids: unknown): string[] {
  if (!Array.isArray(ids)) return [];
  return [...new Set(ids.filter((x): x is string => typeof x === "string" && UUID.test(x)))];
}

/** Looks up to [BATCH] printings on Scryfall. Printings it doesn't know are left out. */
export async function fetchScryfall(ids: string[]): Promise<ScryDetail[]> {
  if (ids.length === 0) return [];
  const r = await scryFetch("https://api.scryfall.com/cards/collection", {
    method: "POST",
    headers: { "User-Agent": USER_AGENT, Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ identifiers: ids.slice(0, BATCH).map((id) => ({ id })) }),
    cache: "no-store",
    signal: AbortSignal.timeout(30_000),
  });
  if (!r.ok) throw new Error(`Scryfall's card lookup failed (HTTP ${r.status}).`);
  const j = (await r.json()) as { data?: ScryDetail[] };
  return j.data ?? [];
}

let cache: { file: DetailsFile; at: number } | null = null;
const CACHE_MS = 10 * 60 * 1000;

export async function loadDetails(fresh = false): Promise<DetailsFile> {
  if (!fresh && cache && Date.now() - cache.at < CACHE_MS) return cache.file;
  try {
    const res = await get(DETAILS_BLOB, { access: "private", useCache: false });
    if (!res || !res.stream) return cache?.file ?? { madeAt: 0, cards: {} };
    const gz = Buffer.from(await new Response(res.stream).arrayBuffer());
    const file = JSON.parse(gunzipSync(gz).toString("utf8")) as DetailsFile;
    cache = { file, at: Date.now() };
    return file;
  } catch {
    return cache?.file ?? { madeAt: 0, cards: {} };
  }
}

async function saveDetails(file: DetailsFile): Promise<void> {
  const body = gzipSync(Buffer.from(JSON.stringify(file), "utf8"));
  await put(DETAILS_BLOB, body, { access: "private", allowOverwrite: true, addRandomSuffix: false, contentType: "application/gzip" });
  cache = { file, at: Date.now() };
}

/** Adds Scryfall's answer for [ids] to [file]; printings Scryfall didn't send get an empty entry. */
export function mergeDetails(file: DetailsFile, ids: string[], found: ScryDetail[], now: number): void {
  const got = new Set<string>();
  for (const c of found) {
    file.cards[c.id] = toInfo(c, now);
    got.add(c.id);
  }
  for (const id of ids) if (!got.has(id)) file.cards[id] = unknownInfo(now);
  file.madeAt = now;
}

/** Looks up the printings in [ids] the store doesn't have yet (at most one Scryfall request) and saves them. */
export async function addDetails(ids: string[]): Promise<Record<string, CardInfo>> {
  const file = await loadDetails(true);
  const missing = ids.filter((id) => !file.cards[id]).slice(0, BATCH);
  if (missing.length > 0) {
    const found = await fetchScryfall(missing);
    mergeDetails(file, missing, found, Date.now());
    await saveDetails(file);
  }
  const out: Record<string, CardInfo> = {};
  for (const id of ids) if (file.cards[id]) out[id] = file.cards[id];
  return out;
}

/**
 * The daily job: looks every stored printing up again (fresh TCGplayer prices, new EDHREC ranks and Game Changers),
 * [BATCH] at a time, 150 ms apart (Scryfall asks for no more than 10 requests a second).
 */
export async function refreshDetails(deadline: number): Promise<{ cards: number; refreshed: number }> {
  const file = await loadDetails(true);
  const ids = Object.keys(file.cards).sort((a, b) => file.cards[a].at - file.cards[b].at);
  let refreshed = 0;
  for (let n = 0; n < ids.length && Date.now() < deadline; n += BATCH) {
    const chunk = ids.slice(n, n + BATCH);
    const found = await fetchScryfall(chunk);
    const now = Date.now();
    for (const c of found) file.cards[c.id] = toInfo(c, now);
    refreshed += chunk.length;
    await new Promise((r) => setTimeout(r, 150));
  }
  if (refreshed > 0) {
    file.madeAt = Date.now();
    await saveDetails(file);
  }
  return { cards: ids.length, refreshed };
}
