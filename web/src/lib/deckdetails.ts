// The card details deck stats need and the collection's details store (carddetails.ts) doesn't keep: mana cost, the
// mana a card makes, rules text, tokens, power/toughness and loyalty (the app's CardInfo fields since 1.43–1.55, made
// by CardInfo.kt toInfo). Kept in their own private Vercel Blob, filled when a deck page asks for cards it doesn't
// have yet; rules text hardly changes, so entries are looked up again only after a while.
import { gunzipSync, gzipSync } from "node:zlib";
import { get, put } from "@vercel/blob";
import { BATCH, fetchScryfall, normalizeColors, validIds, type ScryDetail } from "./carddetails";
import { FACE_BREAK, type StatsCard, type TokenPart } from "./deckstats";

export const DECK_DETAILS_BLOB = "cards/deckdetails.json.gz";
/** Entries older than this are looked up again when a deck shows them. */
export const REFRESH_MS = 60 * 24 * 60 * 60 * 1000;

/** One printing's deck details; short keys to keep the file small. */
export interface DeckInfo {
  /** Type line, faces joined with " // ". */
  t: string;
  /** Mana value. */
  v: number;
  /** Colour identity, WUBRG order. */
  i: string;
  /** Mana cost ("{2}{B}", faces joined with " // "); "" for none. */
  m: string;
  /** Mana it makes, WUBRGC order. */
  p: string;
  /** Rules text, faces joined with FACE_BREAK. */
  o: string | null;
  /** Tokens and helper cards it makes. */
  k: TokenPart[] | null;
  /** Power/toughness of the front face. */
  pt: string | null;
  /** Starting loyalty ("" for a planeswalker without one, null for other cards). */
  l: string | null;
  /** Scryfall's oracle id (a token's, to tell printings of the same token apart). */
  oi: string | null;
  /** On the Game Changers list. */
  g: boolean;
  at: number;
}

export interface DeckDetailsFile {
  madeAt: number;
  cards: Record<string, DeckInfo>;
}

interface Face {
  type_line?: string;
  mana_cost?: string;
  oracle_text?: string;
  power?: string;
  toughness?: string;
  loyalty?: string;
  oracle_id?: string;
  colors?: string[];
}

export interface ScryDeckCard extends ScryDetail {
  oracle_id?: string;
  mana_cost?: string;
  produced_mana?: string[];
  oracle_text?: string;
  power?: string;
  toughness?: string;
  loyalty?: string;
  card_faces?: Face[];
  all_parts?: { id: string; component: string; name: string; type_line: string }[];
}

/** The app's TokenPart.of: tokens among the linked parts, and helper cards ("Card" combo pieces: The Monarch…). */
export function tokenParts(c: ScryDeckCard): TokenPart[] | null {
  const seen = new Set<string>();
  const out: TokenPart[] = [];
  for (const p of c.all_parts ?? []) {
    if (p.id === c.id) continue;
    const helper = p.component === "combo_piece" && (p.type_line ?? "").split(" // ").every((t) => t === "Card");
    if (p.component !== "token" && !helper) continue;
    if (seen.has(p.id)) continue;
    seen.add(p.id);
    out.push({ id: p.id, name: p.name, typeLine: p.type_line ?? "" });
  }
  return out.length ? out : null;
}

/** The app's ScryCard.toInfo for the deck fields. */
export function toDeckInfo(c: ScryDeckCard, now: number): DeckInfo {
  const faces = c.card_faces ?? [];
  const typeLine = c.type_line || faces.map((f) => f.type_line ?? "").join(" // ");
  let manaCost: string;
  if (c.mana_cost !== undefined && (c.mana_cost !== "" || faces.length === 0)) manaCost = c.mana_cost;
  else {
    const f = faces.map((x) => x.mana_cost ?? "");
    manaCost = f.some((x) => x !== "") ? f.join(" // ") : "";
  }
  const produced = [..."WUBRGC"].filter((x) => (c.produced_mana ?? []).includes(x)).join("");
  const oracle = c.oracle_text ?? (faces.length ? faces.map((f) => f.oracle_text ?? "").join(FACE_BREAK) : null);
  const power = c.power ?? faces[0]?.power;
  const pt = power !== undefined ? `${power}/${c.toughness ?? faces[0]?.toughness ?? ""}` : null;
  const loyalty = c.loyalty ?? faces[0]?.loyalty ?? ((c.type_line || faces[0]?.type_line || "").includes("Planeswalker") ? "" : null);
  return {
    t: typeLine,
    v: c.cmc ?? 0,
    i: normalizeColors(c.color_identity ?? []),
    m: manaCost,
    p: produced,
    o: oracle,
    k: tokenParts(c),
    pt,
    l: loyalty,
    oi: c.oracle_id ?? faces[0]?.oracle_id ?? null,
    g: c.game_changer === true,
    at: now,
  };
}

/** A printing Scryfall doesn't know: an empty entry so it isn't asked for again and again. */
export function unknownDeckInfo(now: number): DeckInfo {
  return { t: "", v: 0, i: "", m: "", p: "", o: null, k: null, pt: null, l: null, oi: null, g: false, at: now };
}

let cache: { file: DeckDetailsFile; at: number } | null = null;
const CACHE_MS = 10 * 60 * 1000;

export async function loadDeckDetails(fresh = false): Promise<DeckDetailsFile> {
  if (!fresh && cache && Date.now() - cache.at < CACHE_MS) return cache.file;
  try {
    const res = await get(DECK_DETAILS_BLOB, { access: "private", useCache: false });
    if (!res || !res.stream) return cache?.file ?? { madeAt: 0, cards: {} };
    const gz = Buffer.from(await new Response(res.stream).arrayBuffer());
    const file = JSON.parse(gunzipSync(gz).toString("utf8")) as DeckDetailsFile;
    cache = { file, at: Date.now() };
    return file;
  } catch {
    return cache?.file ?? { madeAt: 0, cards: {} };
  }
}

async function saveDeckDetails(file: DeckDetailsFile): Promise<void> {
  const body = gzipSync(Buffer.from(JSON.stringify(file), "utf8"));
  await put(DECK_DETAILS_BLOB, body, { access: "private", allowOverwrite: true, addRandomSuffix: false, contentType: "application/gzip" });
  cache = { file, at: Date.now() };
}

/** Adds Scryfall's answer for [ids] to [file]; printings Scryfall didn't send get an empty entry. */
export function mergeDeckDetails(file: DeckDetailsFile, ids: string[], found: ScryDeckCard[], now: number): void {
  const got = new Set<string>();
  for (const c of found) {
    file.cards[c.id] = toDeckInfo(c, now);
    got.add(c.id);
  }
  for (const id of ids) if (!got.has(id)) file.cards[id] = unknownDeckInfo(now);
  file.madeAt = now;
}

/**
 * The details of [ids], looking up on Scryfall (75 a request, 100 ms apart) the ones the store doesn't have or has
 * had for long, until [deadline]; then the tokens those cards make, for their names and power/toughness. Returns
 * the details known (some may still be missing when time ran out).
 */
export async function ensureDeckDetails(ids: string[], deadline = Date.now() + 20_000): Promise<Record<string, DeckInfo>> {
  const wanted = validIds(ids);
  let file = await loadDeckDetails();
  const now = Date.now();
  const stale = (id: string) => !file.cards[id] || now - file.cards[id].at > REFRESH_MS;
  if (wanted.some(stale)) {
    file = await loadDeckDetails(true);
    let changed = false;
    const lookUp = async (list: string[]) => {
      for (let n = 0; n < list.length && Date.now() < deadline; n += BATCH) {
        const chunk = list.slice(n, n + BATCH);
        const found = (await fetchScryfall(chunk)) as ScryDeckCard[];
        mergeDeckDetails(file, chunk, found, Date.now());
        changed = true;
        if (n + BATCH < list.length) await new Promise((r) => setTimeout(r, 100));
      }
    };
    await lookUp(wanted.filter(stale));
    const tokenIds = [...new Set(wanted.flatMap((id) => (file.cards[id]?.k ?? []).map((t) => t.id)))].filter((id) => !file.cards[id]);
    await lookUp(validIds(tokenIds));
    if (changed) await saveDeckDetails(file);
  }
  const out: Record<string, DeckInfo> = {};
  for (const id of wanted) {
    if (file.cards[id]) out[id] = file.cards[id];
    for (const t of file.cards[id]?.k ?? []) if (file.cards[t.id]) out[t.id] = file.cards[t.id];
  }
  return out;
}

/** A deck line with its details as the stats take it (the app's CardInfoDao.deckStats row). */
export function statsCard(
  line: { name: string; scryfallId: string; quantity: number; cmc: number; types: string; commander: boolean },
  d: DeckInfo | undefined,
): StatsCard {
  const known = d && d.t !== "";
  return {
    name: line.name,
    scryfallId: line.scryfallId,
    quantity: line.quantity,
    cmc: line.cmc,
    types: line.types,
    commander: line.commander,
    typeLine: known ? d.t : null,
    manaCost: known ? d.m : null,
    producedMana: known ? d.p : null,
    oracleText: known ? d.o : null,
    tokens: known ? d.k : null,
    powerToughness: known ? d.pt : null,
    loyalty: known ? d.l : null,
  };
}
