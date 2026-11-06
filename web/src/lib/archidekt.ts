// Reading public decks from Archidekt, as the app does (Archidekt.kt ArchidektApi, DeckLinks): the same JSON API
// Archidekt's own website uses, no login. Requests go at least 1.5 seconds apart (Archidekt starts limiting at about
// 40 a minute; the app's ArchidektGuard), and the owner's remote switch (remote-switches.json, archidektDecks) turns
// it off here as on the phones.
import { USER_AGENT } from "./config";
import { primaryType } from "./deckshared";
import { EditError } from "./wishlist";

type Fetch = typeof fetch;

/** One line of an Archidekt decklist, before the card is looked up on Scryfall (ArchidektCard). */
export interface ArchidektCard {
  scryfallId: string;
  name: string;
  setCode: string;
  setName: string;
  collectorNumber: string;
  quantity: number;
  category: string;
  types: string;
  cmc: number;
  commander: boolean;
  foil: boolean;
  etched: boolean;
  gameChanger: boolean;
  colorIdentity: string[];
  /** All its categories, [category] first. */
  categories: string[];
  relationId: number | null;
  archidektCardId: number | null;
  label: string | null;
}

export interface ArchidektCategory {
  name: string;
  includedInDeck: boolean;
  id: number | null;
}

export interface ArchidektDeck {
  id: number;
  name: string;
  owner: string;
  artUrl: string | null;
  /** Only the cards in the deck itself. */
  cards: ArchidektCard[];
  updatedAt: number | null;
  /** The cards in categories left out of the deck (the maybeboard, a sideboard…). */
  maybeboard: ArchidektCard[];
  folderId: number | null;
  visibility: string | null;
  categories: ArchidektCategory[];
}

/** A public deck in an Archidekt user's deck list (ArchidektDeckSummary). */
export interface ArchidektDeckSummary {
  id: number;
  name: string;
  size: number;
  artUrl: string | null;
  colors: string;
  commanderFormat: boolean;
  folder: string | null;
  updatedAt: number | null;
}

const deckUrl = /archidekt\.com\/(?:api\/)?decks\/(\d+)/i;
const userUrl = /archidekt\.com\/(?:u|user|users)\/([A-Za-z0-9_.-]+)/i;
const userName = /^@?([A-Za-z0-9_.-]{2,})$/;

/** The deck id in a link like "https://archidekt.com/decks/20263351/auntie_plague" (also inside shared text), or a bare id (DeckLinks.archidektId). */
export function archidektId(text: string): number | null {
  const t = text.trim();
  const m = deckUrl.exec(t);
  if (m) return Number(m[1]);
  return /^\d+$/.test(t) && Number.isSafeInteger(Number(t)) ? Number(t) : null;
}

/** The username in a profile link like "https://archidekt.com/u/joelvdc", or a bare username (DeckLinks.archidektUser). */
export function archidektUser(text: string): string | null {
  const t = text.trim();
  const m = userUrl.exec(t);
  if (m) return /^\d+$/.test(m[1]) ? null : m[1];
  if (t.includes("/") || t.includes(" ")) return null;
  return userName.exec(t)?.[1] ?? null;
}

/** Archidekt's dates ("2026-10-01T18:20:24.479000Z") as milliseconds (DeckSorting.parseTime). */
export function parseTime(s: string | null | undefined): number | null {
  if (!s) return null;
  const t = Date.parse(s.replace(/(\.\d{3})\d+/, "$1"));
  return Number.isNaN(t) ? null : t;
}

const VISIBILITY = { private: "private", unlisted: "unlisted", public: "public" } as const;

type J = Record<string, unknown>;
const obj = (v: unknown): J => (v && typeof v === "object" && !Array.isArray(v) ? (v as J) : {});
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
const str = (v: unknown, d = ""): string => (typeof v === "string" ? v : d);
const numOr = (v: unknown, d: number | null): number | null => (typeof v === "number" && Number.isFinite(v) ? v : d);
const blankNull = (v: unknown): string | null => (typeof v === "string" && v.trim() ? v : null);

/** A deck from Archidekt's answer (ArchidektApi.parse). */
export function parseDeck(body: unknown): ArchidektDeck {
  const d = obj(body);
  const cats = arr(d.categories).map((c) => obj(c));
  const excluded = new Set(cats.filter((c) => c.includedInDeck === false).map((c) => str(c.name)));
  const premier = new Set([...cats.filter((c) => c.isPremier === true).map((c) => str(c.name)), "Commander"]);
  const all: ArchidektCard[] = [];
  for (const raw of arr(d.cards)) {
    const e = obj(raw);
    const card = obj(e.card);
    const o = obj(card.oracleCard);
    const uid = str(card.uid);
    if (uid.length < 2 || !str(o.name)) continue;
    const lineCats = arr(e.categories).filter((x): x is string => typeof x === "string");
    const types = arr(o.types).filter((x): x is string => typeof x === "string").join(" ");
    const primary = lineCats[0] ?? (typeof o.defaultCategory === "string" ? o.defaultCategory : null) ?? primaryType(types);
    const modifier = str(e.modifier, "Normal").toLowerCase();
    const edition = obj(card.edition);
    all.push({
      scryfallId: uid,
      name: str(o.name),
      setCode: str(edition.editioncode),
      setName: str(edition.editionname),
      collectorNumber: str(card.collectorNumber),
      quantity: numOr(e.quantity, 1)!,
      category: primary,
      types,
      cmc: numOr(o.cmc, 0)!,
      commander: !excluded.has(primary) && lineCats.some((c) => premier.has(c)),
      foil: modifier === "foil" || modifier === "etched",
      etched: modifier === "etched",
      gameChanger: o.gameChanger === true,
      colorIdentity: arr(o.colorIdentity).filter((x): x is string => typeof x === "string"),
      categories: lineCats.length ? lineCats : [primary],
      relationId: numOr(e.id, null),
      archidektCardId: numOr(card.id, null),
      label: typeof e.label === "string" ? e.label : null,
    });
  }
  const parent = d.parentFolder;
  const folderId = typeof parent === "number" ? parent : typeof parent === "string" && /^\d+$/.test(parent) ? Number(parent) : numOr(obj(parent).id, null);
  return {
    id: numOr(d.id, 0)!,
    name: str(d.name),
    owner: str(obj(d.owner).username),
    artUrl: blankNull(d.customFeatured) ?? blankNull(d.featured),
    cards: all.filter((c) => !excluded.has(c.category)),
    updatedAt: parseTime(str(d.updatedAt) || null),
    maybeboard: all.filter((c) => excluded.has(c.category)),
    folderId,
    visibility: d.private === true ? VISIBILITY.private : d.unlisted === true ? VISIBILITY.unlisted : VISIBILITY.public,
    categories: cats.map((c) => ({ name: str(c.name), includedInDeck: c.includedInDeck !== false, id: numOr(c.id, null) })),
  };
}

/** The commanders' colour identity in WUBRG order (ArchidektDeck.colorIdentity). */
export function identityOf(deck: ArchidektDeck): string {
  const names = new Set(deck.cards.filter((c) => c.commander).flatMap((c) => c.colorIdentity));
  return (
    [
      ["White", "W"],
      ["Blue", "U"],
      ["Black", "B"],
      ["Red", "R"],
      ["Green", "G"],
    ] as const
  )
    .filter(([n]) => names.has(n))
    .map(([, c]) => c)
    .join("");
}

/** One page of a user's deck list and the next page's address (ArchidektApi.parseDeckList); private decks left out. */
export function parseDeckList(body: unknown): { decks: ArchidektDeckSummary[]; next: string | null } {
  const b = obj(body);
  const decks = arr(b.results)
    .map((x) => obj(x))
    .filter((d) => d.private !== true)
    .map((d) => {
      const colors = obj(d.colors);
      return {
        id: numOr(d.id, 0)!,
        name: str(d.name),
        size: numOr(d.size, 0)!,
        artUrl: blankNull(d.customFeatured) ?? blankNull(d.featured),
        colors: [..."WUBRG"].filter((c) => (numOr(colors[c], 0) ?? 0) > 0).join(""),
        commanderFormat: d.deckFormat === 3,
        folder: blankNull(d.parentFolderName),
        updatedAt: parseTime(str(d.updatedAt) || null),
      };
    });
  return { decks, next: typeof b.next === "string" && b.next ? b.next : null };
}

// ---- Requests ----

export const SWITCHES_URL = "https://raw.githubusercontent.com/joelvdc/MTG-trading-tracker/main/remote-switches.json";
let switches: { decks: boolean; message: string; at: number } | null = null;

/** Whether reading decks from Archidekt is switched on (remote-switches.json, read at most once an hour; on if unreadable). */
async function decksAllowed(f: Fetch): Promise<{ decks: boolean; message: string }> {
  if (switches && Date.now() - switches.at < 3_600_000) return switches;
  try {
    const r = await f(SWITCHES_URL, { signal: AbortSignal.timeout(5_000), cache: "no-store" });
    const j = r.ok ? obj(await r.json()) : null;
    switches = { decks: j ? j.archidektDecks !== false : (switches?.decks ?? true), message: j ? str(j.message).trim() : (switches?.message ?? ""), at: Date.now() };
  } catch {
    switches = { decks: switches?.decks ?? true, message: switches?.message ?? "", at: Date.now() };
  }
  return switches;
}

let nextAt = 0;
/** Waits for this request's turn: at least 1.5 seconds after the previous one (within this server instance). */
async function pace(): Promise<void> {
  const now = Date.now();
  const at = Math.max(now, nextAt);
  nextAt = at + 1_500;
  if (at > now) await new Promise((r) => setTimeout(r, at - now));
}

/** Archidekt's problem in words for the page (shown as an edit's error). */
export class ArchidektError extends EditError {}

async function get(url: string, f: Fetch, notFound: string): Promise<unknown> {
  const s = await decksAllowed(f);
  if (!s.decks) throw new ArchidektError(`Reading decks from Archidekt is switched off in MTG Trader for now.${s.message ? ` ${s.message}` : ""}`);
  await pace();
  let r: Response;
  try {
    r = await f(url, { headers: { Accept: "application/json", "User-Agent": USER_AGENT }, signal: AbortSignal.timeout(30_000), cache: "no-store" });
  } catch {
    throw new ArchidektError("Archidekt couldn't be reached.");
  }
  if (r.status === 404 || r.status === 403) throw new ArchidektError(notFound);
  if (!r.ok) throw new ArchidektError(`Archidekt answered ${r.status}.`);
  return r.json();
}

/** A public or unlisted deck. */
export async function fetchDeck(id: number, f: Fetch = fetch): Promise<ArchidektDeck> {
  return parseDeck(await get(`https://archidekt.com/api/decks/${id}/`, f, `Archidekt deck ${id} doesn't exist or isn't public.`));
}

/** A user's public decks, most recently updated first (up to 20 pages of 50). */
export async function userDecks(username: string, f: Fetch = fetch): Promise<ArchidektDeckSummary[]> {
  const out: ArchidektDeckSummary[] = [];
  let url: string | null = `https://archidekt.com/api/decks/v3/?ownerUsername=${encodeURIComponent(username)}&pageSize=50`;
  for (let pages = 0; url && pages < 20; pages++) {
    const page = parseDeckList(await get(url, f, `Archidekt has no user ${username}.`));
    out.push(...page.decks);
    // Archidekt's "next" links use http://.
    url = page.next?.replace(/^http:\/\//, "https://") ?? null;
  }
  return out;
}

/** For tests: forget the switches and the pacing. */
export function resetArchidekt(): void {
  switches = null;
  nextAt = 0;
}
