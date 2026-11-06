// Card search on Scryfall, turned into the app's CardRef (Scryfall.kt toRef).
import { parseCardRules, type CardRules } from "./cardrules";
import { USER_AGENT } from "./config";
import type { CardRef } from "./syncfile";

const API = "https://api.scryfall.com";

/** The app's FoilTypes.pick: a named special foil, else any promo type ending in "foil". */
const FOIL_TYPES = [
  "serialized", "surgefoil", "galaxyfoil", "textured", "gilded", "oilslick", "stepandcompleat", "neonink", "confettifoil",
  "halofoil", "doublerainbow", "raisedfoil", "rainbowfoil", "fracturefoil", "ripplefoil", "silverfoil", "manafoil",
  "dragonscalefoil", "singularityfoil", "cosmicfoil",
];

export function pickFoilType(promoTypes: string[] = []): string | null {
  return FOIL_TYPES.find((t) => promoTypes.includes(t)) ?? promoTypes.find((t) => t.endsWith("foil") && t !== "foil") ?? null;
}

interface ScryCard {
  id: string;
  name: string;
  set: string;
  set_name?: string;
  collector_number?: string;
  rarity?: string;
  released_at?: string;
  cardmarket_id?: number;
  image_uris?: { normal?: string; small?: string };
  card_faces?: { image_uris?: { normal?: string; small?: string } }[];
  prices?: { eur?: string | null; eur_foil?: string | null };
  finishes?: string[];
  promo_types?: string[];
  flavor_name?: string;
  digital?: boolean;
  type_line?: string;
  layout?: string;
  cmc?: number;
  color_identity?: string[];
}

export interface FoundCard {
  card: CardRef;
  released: string;
  typeLine: string;
}

export function toCardRef(c: ScryCard): CardRef {
  const finishes = c.finishes ?? [];
  const num = (s: string | null | undefined) => (s != null && s !== "" && !isNaN(Number(s)) ? Number(s) : null);
  return {
    scryfallId: c.id,
    name: c.name,
    setCode: c.set,
    setName: c.set_name ?? "",
    collectorNumber: c.collector_number ?? "",
    rarity: c.rarity ?? "",
    imageUrl: c.image_uris?.normal ?? c.card_faces?.[0]?.image_uris?.normal ?? null,
    cardmarketId: c.cardmarket_id ?? null,
    fallbackEur: num(c.prices?.eur),
    fallbackEurFoil: num(c.prices?.eur_foil),
    hasNonFoil: finishes.includes("nonfoil") || finishes.length === 0,
    hasFoil: finishes.includes("foil"),
    foilType: pickFoilType(c.promo_types),
    hasEtched: finishes.includes("etched"),
    flavorName: c.flavor_name ?? null,
    cardmarketFoilId: null,
  };
}

async function search(q: string, unique: "cards" | "prints"): Promise<FoundCard[]> {
  const url = `${API}/cards/search?${new URLSearchParams({ q: `${q} game:paper`, unique, order: unique === "prints" ? "released" : "name" })}`;
  const r = await fetch(url, {
    headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
    signal: AbortSignal.timeout(15_000),
    next: { revalidate: 3600 },
  });
  if (r.status === 404) return [];
  if (!r.ok) throw new Error(`Scryfall search failed (HTTP ${r.status}).`);
  const j = (await r.json()) as { data?: ScryCard[] };
  return (j.data ?? [])
    .filter((c) => !c.digital)
    .slice(0, 60)
    .map((c) => ({ card: toCardRef(c), released: c.released_at ?? "", typeLine: c.type_line ?? "" }));
}

/** Cards whose name matches [text] (one printing each). */
export function searchNames(text: string): Promise<FoundCard[]> {
  const t = text.replace(/["\\]/g, " ").trim();
  return search(`name:"${t}"`, "cards");
}

/** Every paper printing of the card named exactly [name], newest first. */
export function printings(name: string): Promise<FoundCard[]> {
  return search(`!"${name.replace(/["\\]/g, " ").trim()}"`, "prints");
}

/** A card's rules text, legalities and rulings by Scryfall id; null when Scryfall doesn't know the card. Cached for a day. */
export async function rulesOf(id: string): Promise<CardRules | null> {
  const get = async (path: string) => {
    const r = await fetch(`${API}${path}`, {
      headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
      signal: AbortSignal.timeout(15_000),
      next: { revalidate: 86_400 },
    });
    if (r.status === 404) return null;
    if (!r.ok) throw new Error(`Scryfall lookup failed (HTTP ${r.status}).`);
    return r.json();
  };
  const [card, rulings] = await Promise.all([get(`/cards/${id}`), get(`/cards/${id}/rulings`)]);
  return card ? parseCardRules(card, rulings) : null;
}

/** Every paper printing of a token (or card) by its oracle id, newest first (the app's printsOfOracle). */
export async function tokenPrintings(oracleId: string): Promise<{ id: string; setName: string; collectorNumber: string }[]> {
  const q = new URLSearchParams({ q: `oracleid:${oracleId} game:paper`, unique: "prints", order: "released", dir: "desc", include_extras: "true" });
  const j = (await getJson(`${API}/cards/search?${q}`)) as { data?: ScryCard[] } | null;
  return (j?.data ?? []).filter((c) => !c.digital).map((c) => ({ id: c.id, setName: c.set_name ?? "", collectorNumber: c.collector_number ?? "" }));
}

/** A card as an order needs it: the app's CardRef plus what tells tokens and basic lands apart. */
export interface LookedUp {
  card: CardRef;
  typeLine: string;
  layout: string;
  cmc: number;
  colorIdentity: string[];
}

const looked = (c: ScryCard): LookedUp => ({
  card: toCardRef(c),
  typeLine: c.type_line ?? "",
  layout: c.layout ?? "",
  cmc: c.cmc ?? 0,
  colorIdentity: c.color_identity ?? [],
});

async function getJson(url: string, init: RequestInit = {}): Promise<unknown | null> {
  const r = await fetch(url, {
    ...init,
    headers: { "User-Agent": USER_AGENT, Accept: "application/json", ...(init.body ? { "Content-Type": "application/json" } : {}) },
    signal: AbortSignal.timeout(20_000),
    cache: "no-store",
  });
  if (r.status === 404) return null;
  if (!r.ok) throw new Error(`Scryfall lookup failed (HTTP ${r.status}).`);
  return r.json();
}

const pause = () => new Promise((r) => setTimeout(r, 100)); // Scryfall asks for 50–100 ms between requests.

/** Scryfall's /cards/collection, 75 identifiers per request (the app's ScryfallApi.collection); unknown ones are left out. */
export async function lookupCollectionFull(identifiers: object[]): Promise<LookedUp[]> {
  const out: LookedUp[] = [];
  for (let i = 0; i < identifiers.length; i += 75) {
    if (i > 0) await pause();
    const j = (await getJson(`${API}/cards/collection`, { method: "POST", body: JSON.stringify({ identifiers: identifiers.slice(i, i + 75) }) })) as { data?: ScryCard[] } | null;
    for (const c of j?.data ?? []) out.push(looked(c));
  }
  return out;
}

export async function lookupCollection(identifiers: object[]): Promise<CardRef[]> {
  return (await lookupCollectionFull(identifiers)).map((l) => l.card);
}

/** The printing Cardmarket sells as product [id], if Scryfall knows it. */
export async function byCardmarketId(id: number): Promise<LookedUp | null> {
  await pause();
  const c = (await getJson(`${API}/cards/cardmarket/${id}`)) as ScryCard | null;
  return c ? looked(c) : null;
}

/** Scryfall's best guess for a name. */
export async function fuzzyNamed(name: string): Promise<LookedUp | null> {
  await pause();
  const c = (await getJson(`${API}/cards/named?${new URLSearchParams({ fuzzy: name })}`)) as ScryCard | null;
  return c ? looked(c) : null;
}

/** A Scryfall search as written ([q] as is), first page. */
export async function searchExact(q: string): Promise<LookedUp[]> {
  await pause();
  const j = (await getJson(`${API}/cards/search?${new URLSearchParams({ q })}`)) as { data?: ScryCard[] } | null;
  return (j?.data ?? []).map(looked);
}
