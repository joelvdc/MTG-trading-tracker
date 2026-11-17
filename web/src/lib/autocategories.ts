// Automatic deck categories (Ramp, Removal, Draw…) for cards added to local decks, from the website's own table of
// categories (card_tags), which a weekly job (/api/cron/categories) builds from Scryfall's card tags: every card
// tagged "ramp" is Ramp, and so on. A card can have several; the first in CATEGORY_ORDER decides its section, the
// others go along as extra categories on the deck line (as on Archidekt). Cards without a matching tag go by card type
// (lib/deckedit.ts fallbackCategory), and a land never gets a tag category at all: it goes in the Land category.
// The website doesn't ask Archidekt (owner's decision, 2026-11-06).
import { accountStore } from "./accounts";
import { USER_AGENT } from "./config";
import { frontIsLand } from "./deckedit";
import { scryFetch } from "./scrylimit";

/** A card's auto categories by Scryfall printing id: [category] first, then the [extra] ones. */
export type AutoMap = Map<string, { cardId: number | null; category: string | null; extra?: string[] }>;

/** A card to categorise: its printing and name (the table is by name, so every printing shares it). */
export interface AutoCard {
  scryfallId: string;
  name: string;
}

/**
 * The categories in their order (the first a card has decides its section; Cultivate is ramp and a tutor: Ramp), with
 * the Scryfall tags that put a card in each (Archidekt's names; Removal takes board wipes and counterspells too).
 */
export const TAG_GROUPS: { category: string; tags: string[] }[] = [
  { category: "Ramp", tags: ["ramp"] },
  { category: "Removal", tags: ["removal", "sweeper", "counterspell"] },
  { category: "Draw", tags: ["draw"] },
  { category: "Tutor", tags: ["tutor"] },
  { category: "Recursion", tags: ["recursion"] },
  { category: "Protection", tags: ["protection"] },
];
export const CATEGORY_ORDER = TAG_GROUPS.map((g) => g.category);

const SCRYFALL = "https://api.scryfall.com";
/** The table is rebuilt when it is older than this (the job runs daily and skips the other days). */
export const REBUILD_AFTER_MS = 6 * 86_400_000;
/** When the rule "a land never gets a tag category" came in: a table built before it is rebuilt once, at once. */
export const RULES_SINCE = Date.UTC(2026, 10, 17, 12, 0);

/** How a name is looked up: lower case; for "A // B" also "a" (some lists name only the front face). */
export const nameKey = (name: string) => name.trim().toLowerCase();
const frontKey = (name: string) => nameKey(name.split(" // ")[0]);

/** The categories for each card, from the website's table. Cards it doesn't have are left out (they go by card type). */
export async function autoCategories(cards: AutoCard[]): Promise<AutoMap> {
  const out: AutoMap = new Map();
  const store = accountStore();
  if (!store || cards.length === 0) return out;
  const keys = [...new Set(cards.flatMap((c) => [nameKey(c.name), frontKey(c.name)]))];
  let found: Map<string, string[]>;
  try {
    found = await store.tagCategories(keys);
  } catch (e) {
    console.warn(`Auto categories: ${e instanceof Error ? e.message : e}`);
    return out;
  }
  for (const c of cards) {
    const cats = found.get(nameKey(c.name)) ?? found.get(frontKey(c.name));
    if (cats?.length) out.set(c.scryfallId, { cardId: null, category: cats[0], extra: cats.slice(1) });
  }
  return out;
}

/** The most names one call to /api/cards/categories may ask for (the app sends them in chunks). */
export const MAX_NAMES = 500;

/**
 * The categories of each name, keyed by the name as asked (for the app's "Auto categories for new cards", since
 * 1.81, so the phone and the website agree). Names the table doesn't have are left out.
 */
export async function categoriesByName(names: string[]): Promise<Record<string, string[]>> {
  const store = accountStore();
  const out: Record<string, string[]> = {};
  if (!store || names.length === 0) return out;
  const found = await store.tagCategories([...new Set(names.flatMap((n) => [nameKey(n), frontKey(n)]))]);
  for (const n of names) {
    const cats = found.get(nameKey(n)) ?? found.get(frontKey(n));
    if (cats?.length) out[n] = cats;
  }
  return out;
}

type Fetch = typeof fetch;
const pause = () => new Promise((r) => setTimeout(r, 100)); // Scryfall asks for 50–100 ms between requests.

/**
 * Every card's categories from Scryfall: one tag search per category (paged, 175 cards a page), in CATEGORY_ORDER.
 * A card whose front face is a land is left out (Evolving Wilds is tagged "tutor", but lands go by card type), so a
 * modal card with a spell on the front ("Sorcery // Land") keeps its tags. Fails as a whole if any page fails, so a
 * half-built table never replaces the last good one.
 */
export async function fetchTagCategories(f: Fetch = scryFetch, deadline = Date.now() + 240_000): Promise<{ name: string; categories: string[] }[]> {
  const byKey = new Map<string, { name: string; categories: string[] }>();
  for (const g of TAG_GROUPS) {
    const q = g.tags.map((t) => `otag:${t}`).join(" or ");
    let next: string | null = `${SCRYFALL}/cards/search?${new URLSearchParams({ q: `(${q})`, unique: "cards", order: "name" })}`;
    while (next) {
      if (Date.now() > deadline) throw new Error("Scryfall's tag searches took too long; the old table is kept.");
      await pause();
      const r = await f(next, { headers: { "User-Agent": USER_AGENT, Accept: "application/json" }, signal: AbortSignal.timeout(20_000), cache: "no-store" });
      if (r.status === 404) break; // no card has this tag (any more)
      if (!r.ok) throw new Error(`Scryfall's tag search for ${g.category} failed (HTTP ${r.status}).`);
      const j = (await r.json()) as { data?: { name?: string; type_line?: string }[]; has_more?: boolean; next_page?: string };
      for (const c of j.data ?? []) {
        if (!c.name) continue;
        if (frontIsLand(c.type_line ?? "")) continue; // a land goes by card type, never by a tag
        const row = byKey.get(nameKey(c.name)) ?? { name: c.name, categories: [] };
        if (!row.categories.includes(g.category)) row.categories.push(g.category);
        byKey.set(nameKey(c.name), row);
      }
      next = j.has_more && j.next_page ? j.next_page : null;
    }
  }
  return [...byKey.values()];
}

/**
 * The weekly job: rebuilds the table from Scryfall when it is older than REBUILD_AFTER_MS (or empty), and once more
 * right after a change of the rules (RULES_SINCE: lands stopped getting tag categories). Rows are by name; a
 * double-faced card also gets one under its front face.
 */
export async function rebuildTagCategories(now = Date.now(), f: Fetch = scryFetch, deadline = now + 270_000): Promise<{ rebuilt: boolean; cards: number; updatedAt: number | null }> {
  const store = accountStore();
  if (!store) throw new Error("No database.");
  const before = await store.tagCategoryStats();
  // A table built before the rules changed has the old answers in it (Evolving Wilds as Tutor), so rebuild it once,
  // as soon as the clock is past RULES_SINCE; after that rebuild its updatedAt is later than RULES_SINCE.
  const beforeRules = before.updatedAt !== null && before.updatedAt < RULES_SINCE && now >= RULES_SINCE;
  if (!beforeRules && before.cards > 0 && before.updatedAt !== null && before.updatedAt > now - REBUILD_AFTER_MS) return { rebuilt: false, ...before };
  const rows = await fetchTagCategories(f, deadline);
  if (rows.length === 0) throw new Error("Scryfall's tag searches found no cards; the old table is kept.");
  const keyed = new Map<string, string[]>();
  for (const r of rows) keyed.set(nameKey(r.name), r.categories);
  for (const r of rows) if (r.name.includes(" // ") && !keyed.has(frontKey(r.name))) keyed.set(frontKey(r.name), r.categories);
  await store.replaceTagCategories([...keyed].map(([key, categories]) => ({ key, categories })), now);
  return { rebuilt: true, ...(await store.tagCategoryStats()) };
}
