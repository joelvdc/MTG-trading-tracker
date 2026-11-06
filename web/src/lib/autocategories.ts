// Automatic deck categories (Ramp, Draw, Removal…) for cards added to local decks, like the app's "Auto categories for
// new cards" (DeckEditor.autoCategories): Archidekt's own category for the card (its public card search, no login);
// when Archidekt has none, the Scryfall tagger tags Archidekt sends along; for cards Archidekt doesn't know yet, a
// Scryfall tag search. Without any of those, the card goes by its card type (lib/deckedit.ts fallbackCategory).
// Every printing is looked up once and kept in the database (table card_categories), shared by all accounts.
import { accountStore, type CardCategory } from "./accounts";
import { USER_AGENT } from "./config";

export type AutoMap = Map<string, { cardId: number | null; category: string | null }>;

/** A card to categorise: its printing and name (the name is only used for the Scryfall search). */
export interface AutoCard {
  scryfallId: string;
  name: string;
}

const ARCHIDEKT = "https://archidekt.com";
const SCRYFALL = "https://api.scryfall.com";
const DAY = 86_400_000;
/** How long a looked-up category is kept: Archidekt's answers change rarely; cards it didn't know are asked again soon. */
const KEEP_MS: Record<CardCategory["source"], number> = { archidekt: 60 * DAY, tags: 60 * DAY, none: 60 * DAY, scryfall: 7 * DAY };
/** At most this many new printings are looked up per request (a big pasted deck); the rest go by card type this time. */
const MAX_NEW = 400;
/** At most this many cards unknown to Archidekt go to the Scryfall tag search per request. */
const MAX_SCRYFALL = 30;

/**
 * Scryfall tagger tags (as Archidekt sends them, with spaces, or Scryfall's with hyphens) → Archidekt's category names,
 * in order: the first group a card has wins (Cultivate is ramp and a tutor: Ramp).
 */
export const TAG_GROUPS: { category: string; tags: string[] }[] = [
  { category: "Ramp", tags: ["ramp"] },
  { category: "Removal", tags: ["removal", "sweeper", "board wipe", "counterspell"] },
  { category: "Draw", tags: ["draw", "card advantage"] },
  { category: "Tutor", tags: ["tutor"] },
  { category: "Recursion", tags: ["recursion"] },
  { category: "Protection", tags: ["protection", "gives protection"] },
];

const norm = (t: string) => t.trim().toLowerCase().replace(/-/g, " ");

/** The category for a card's tags, or null. */
export function categoryFromTags(tags: string[]): string | null {
  const have = new Set(tags.map(norm));
  return TAG_GROUPS.find((g) => g.tags.some((t) => have.has(t)))?.category ?? null;
}

type Fetch = typeof fetch;

interface ArchidektCard {
  id?: number;
  uid?: string;
  oracleCard?: { defaultCategory?: string | null; oTags?: string[] | null; inheritedTags?: string[] | null };
}

/** Archidekt's answer for these printings (60 per request, as the app asks); printings it doesn't know are left out. */
export async function fromArchidekt(ids: string[], now: number, f: Fetch = fetch): Promise<CardCategory[]> {
  const out: CardCategory[] = [];
  for (let i = 0; i < ids.length; i += 60) {
    const chunk = ids.slice(i, i + 60);
    let next: string | null = `${ARCHIDEKT}/api/cards/v2/?uids=${chunk.join(",")}&allEditions&includeTokens&includeEmblems&includeArtCards&includeOversized&includeDigital`;
    for (let pages = 0; next && pages < 5; pages++) {
      const r = await f(next, { headers: { "User-Agent": USER_AGENT, Accept: "application/json" }, signal: AbortSignal.timeout(15_000), cache: "no-store" });
      if (!r.ok) throw new Error(`Archidekt answered ${r.status} looking up cards.`);
      const j = (await r.json()) as { results?: ArchidektCard[]; next?: string | null };
      for (const c of j.results ?? []) {
        if (!c.uid || typeof c.id !== "number" || !chunk.includes(c.uid)) continue;
        const own = c.oracleCard?.defaultCategory?.trim() || null;
        const tagged = own ? null : categoryFromTags([...(c.oracleCard?.oTags ?? []), ...(c.oracleCard?.inheritedTags ?? [])]);
        out.push({ scryfallId: c.uid, archidektId: c.id, category: own ?? tagged, source: own ? "archidekt" : tagged ? "tags" : "none", checkedAt: now });
      }
      // Archidekt's "next" links say http://.
      next = j.next ? j.next.replace(/^http:\/\//, "https://") : null;
    }
  }
  return out;
}

const quoteName = (n: string) => `!"${n.replace(/["\\]/g, " ").trim()}"`;
const pause = () => new Promise((r) => setTimeout(r, 100)); // Scryfall asks for 50–100 ms between requests.

/** For cards Archidekt doesn't know (yet): which tag group Scryfall's search puts them in, by name. */
export async function fromScryfallTags(cards: AutoCard[], now: number, f: Fetch = fetch): Promise<CardCategory[]> {
  const byName = new Map<string, string | null>();
  for (let i = 0; i < cards.length; i += 15) {
    const names = [...new Set(cards.slice(i, i + 15).map((c) => c.name))];
    for (const g of TAG_GROUPS) {
      const left = names.filter((n) => !byName.get(n.toLowerCase()));
      if (left.length === 0) break;
      const q = `(${left.map(quoteName).join(" or ")}) (${g.tags.map((t) => `otag:${t.replace(/ /g, "-")}`).join(" or ")})`;
      await pause();
      const r = await f(`${SCRYFALL}/cards/search?${new URLSearchParams({ q, unique: "cards" })}`, {
        headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
        signal: AbortSignal.timeout(15_000),
        cache: "no-store",
      });
      if (r.status === 404) continue; // no card of these is in the group
      if (!r.ok) throw new Error(`Scryfall search failed (HTTP ${r.status}).`);
      const j = (await r.json()) as { data?: { name?: string }[] };
      for (const c of j.data ?? []) if (c.name && !byName.get(c.name.toLowerCase())) byName.set(c.name.toLowerCase(), g.category);
    }
  }
  return cards.map((c) => {
    const category = byName.get(c.name.toLowerCase()) ?? null;
    // "scryfall" even without a category: Archidekt is asked again within a week, it may know the card by then.
    return { scryfallId: c.scryfallId, archidektId: null, category, source: "scryfall", checkedAt: now } as CardCategory;
  });
}

/**
 * The automatic category (and Archidekt's card id) for each printing: from the database when looked up recently,
 * otherwise from Archidekt, then Scryfall. A failed lookup leaves cards out (they go by card type) and isn't kept.
 */
export async function autoCategories(cards: AutoCard[], now = Date.now(), f: Fetch = fetch): Promise<AutoMap> {
  const want = new Map<string, AutoCard>();
  for (const c of cards) if (c.scryfallId && !want.has(c.scryfallId)) want.set(c.scryfallId, c);
  const out: AutoMap = new Map();
  if (want.size === 0) return out;
  const store = accountStore();
  const known = new Map<string, CardCategory>();
  if (store) {
    try {
      for (const r of await store.cardCategories([...want.keys()])) known.set(r.scryfallId, r);
    } catch {
      // No database right now: look everything up.
    }
  }
  const fresh = (r: CardCategory | undefined) => r && r.checkedAt > now - KEEP_MS[r.source];
  const todo = [...want.keys()].filter((id) => !fresh(known.get(id))).slice(0, MAX_NEW);
  const found: CardCategory[] = [];
  if (todo.length) {
    try {
      const archidekt = await fromArchidekt(todo, now, f);
      found.push(...archidekt);
      const seen = new Set(archidekt.map((r) => r.scryfallId));
      const unknown = todo.filter((id) => !seen.has(id)).map((id) => want.get(id)!);
      if (unknown.length) found.push(...(await fromScryfallTags(unknown.slice(0, MAX_SCRYFALL), now, f)));
    } catch (e) {
      console.warn(`Auto categories: ${e instanceof Error ? e.message : e}`);
    }
    if (store && found.length) await store.putCardCategories(found).catch(() => undefined);
  }
  for (const r of [...known.values(), ...found]) out.set(r.scryfallId, { cardId: r.archidektId, category: r.category });
  return out;
}
