// A small index of every Commander precon, for the precons page's tiles: MTGJSON's deck list has no commanders or
// colours, so they are read from each deck once and kept in one private Vercel Blob, added to day by day.
import { gunzipSync, gzipSync } from "node:zlib";
import { get, put } from "@vercel/blob";
import { mtgjson, preconList } from "./preconfetch";
import { deckList, deckUrl, type PreconInfo } from "./precons";
import { lookupCollectionFull } from "./scryfall";

export const INDEX_BLOB = "precons/index.json.gz";
export const SETLIST_URL = "https://mtgjson.com/api/v5/SetList.json";

export interface PreconEntry {
  fileName: string;
  name: string;
  code: string;
  setName: string | null;
  releaseDate: string | null;
  commanders: { name: string; scryfallId: string }[];
  /** In WUBRG order; "" when colourless or unknown. */
  colorIdentity: string;
  /** Cards in the commander and main board, counts added up, basic lands included. */
  cards: number;
}

export interface PreconIndex {
  madeAt: number;
  listCount: number;
  decks: PreconEntry[];
}

const COLORS = "WUBRG";

/** SetList.json's set code (upper case) → set name. */
export function setNames(json: unknown): Map<string, string> {
  const data = (json as { data?: unknown[] })?.data ?? [];
  const out = new Map<string, string>();
  for (const s of data.map((d) => d as Record<string, unknown>)) {
    const code = typeof s.code === "string" ? s.code.toUpperCase() : "";
    if (code && typeof s.name === "string") out.set(code, s.name);
  }
  return out;
}

/** A deck's commanders from its file: each name once, in file order, without ones Scryfall has no id for. */
function commandersOf(deckJson: unknown): { name: string; scryfallId: string }[] {
  const d = ((deckJson as { data?: Record<string, unknown> })?.data ?? {}) as Record<string, unknown>;
  const list = Array.isArray(d.commander) ? (d.commander as Record<string, unknown>[]) : [];
  const out: { name: string; scryfallId: string }[] = [];
  const seen = new Set<string>();
  for (const c of list) {
    const name = typeof c.name === "string" ? c.name : "";
    const id = ((c.identifiers ?? {}) as Record<string, unknown>).scryfallId;
    if (!name || typeof id !== "string" || !id || seen.has(name)) continue;
    seen.add(name);
    out.push({ name, scryfallId: id });
  }
  return out;
}

/** An index entry for one deck, without its colours (they need Scryfall's answer). */
export function entryOf(info: PreconInfo, deckJson: unknown, setName: string | null): Omit<PreconEntry, "colorIdentity"> {
  const deck = deckList(deckJson);
  return {
    fileName: info.fileName,
    name: info.name,
    code: info.code,
    setName,
    releaseDate: info.releaseDate,
    commanders: commandersOf(deckJson),
    cards: deck.cards.reduce((n, c) => n + c.count, 0),
  };
}

/** The colours in [letters] together, in WUBRG order ("" for none). */
export function identityOf(letters: Iterable<string>): string {
  const seen = new Set<string>();
  for (const part of letters) for (const ch of part.toUpperCase()) if (COLORS.includes(ch)) seen.add(ch);
  return [...COLORS].filter((c) => seen.has(c)).join("");
}

/** Newest release first, then name (as parseList). */
const newestFirst = (a: PreconEntry, b: PreconEntry) => (b.releaseDate ?? "").localeCompare(a.releaseDate ?? "") || a.name.localeCompare(b.name);

/** The old entries still in [list], with [fresh] added or replacing theirs; sorted newest first. */
export function mergeIndex(old: PreconEntry[], list: PreconInfo[], fresh: PreconEntry[]): PreconEntry[] {
  const named = new Set(list.map((d) => d.fileName));
  const by = new Map<string, PreconEntry>();
  for (const e of old) if (named.has(e.fileName)) by.set(e.fileName, e);
  for (const e of fresh) if (named.has(e.fileName)) by.set(e.fileName, e);
  return [...by.values()].sort(newestFirst);
}

/** The index's decks, plus any deck in [list] it doesn't have yet as a bare entry (nothing known about it). */
export function browseList(index: PreconIndex | null, list: PreconInfo[]): PreconEntry[] {
  const decks = index?.decks ?? [];
  const had = new Set(decks.map((e) => e.fileName));
  const bare = list
    .filter((d) => !had.has(d.fileName))
    .map((d) => ({ fileName: d.fileName, name: d.name, code: d.code, setName: null, releaseDate: d.releaseDate, commanders: [], colorIdentity: "", cards: 0 }));
  return [...decks, ...bare].sort(newestFirst);
}

let cache: { file: PreconIndex; at: number } | null = null;
const CACHE_MS = 10 * 60 * 1000;

/** The stored index, or null when there's none yet or it can't be read. */
export async function loadPreconIndex(): Promise<PreconIndex | null> {
  if (cache && Date.now() - cache.at < CACHE_MS) return cache.file;
  try {
    const res = await get(INDEX_BLOB, { access: "private", useCache: false });
    if (!res || !res.stream) return cache?.file ?? null;
    const gz = Buffer.from(await new Response(res.stream).arrayBuffer());
    const file = JSON.parse(gunzipSync(gz).toString("utf8")) as PreconIndex;
    cache = { file, at: Date.now() };
    return file;
  } catch {
    return cache?.file ?? null;
  }
}

async function saveIndex(file: PreconIndex): Promise<void> {
  const body = gzipSync(Buffer.from(JSON.stringify(file), "utf8"));
  await put(INDEX_BLOB, body, { access: "private", allowOverwrite: true, addRandomSuffix: false, contentType: "application/gzip" });
  cache = { file, at: Date.now() };
}

/**
 * The daily job: adds an entry for every deck the index doesn't have yet, four decks at a time until [deadline], and
 * stores the index (also when nothing was added, so its age shows the job ran). A deck that fails is skipped and
 * tried again tomorrow.
 */
export async function buildPreconIndex(deadline: number): Promise<{ decks: number; added: number; missing: number }> {
  const list = await preconList();
  const old = (await loadPreconIndex())?.decks ?? [];
  const had = new Set(old.map((e) => e.fileName));
  const todo = list.filter((d) => !had.has(d.fileName));
  const names = todo.length > 0 ? setNames(await mtgjson(SETLIST_URL)) : new Map<string, string>();
  const fresh: Omit<PreconEntry, "colorIdentity">[] = [];
  // Four workers share the todo decks, each taking the next one while there's time left.
  const queue = [...todo];
  const worker = async () => {
    while (Date.now() < deadline) {
      const info = queue.shift();
      if (!info) return;
      try {
        fresh.push(entryOf(info, await mtgjson(deckUrl(info.fileName)), names.get(info.code.toUpperCase()) ?? null));
      } catch (e) {
        console.error(e);
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(4, queue.length) }, worker));
  // One Scryfall request (it batches by itself) for the new decks' commanders, for their colours.
  const ids = [...new Set(fresh.flatMap((e) => e.commanders.map((c) => c.scryfallId)))];
  const found = ids.length > 0 ? await lookupCollectionFull(ids.map((id) => ({ id }))) : [];
  const identity = new Map(found.map((l) => [l.card.scryfallId, identityOf(l.colorIdentity)]));
  const added: PreconEntry[] = fresh.map((e) => ({ ...e, colorIdentity: identityOf(e.commanders.map((c) => identity.get(c.scryfallId) ?? "")) }));
  const decks = mergeIndex(old, list, added);
  await saveIndex({ madeAt: Date.now(), listCount: list.length, decks });
  return { decks: decks.length, added: added.length, missing: list.length - decks.length };
}

/** A card's art, cropped to the picture, on Scryfall's image server. */
export function artUrl(scryfallId: string): string {
  return `https://cards.scryfall.io/art_crop/front/${scryfallId[0]}/${scryfallId[1]}/${scryfallId}.jpg`;
}
