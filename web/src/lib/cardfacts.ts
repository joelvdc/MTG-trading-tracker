// Card facts by name for the website's deck rating (2026-11-23): the Scryfall Tagger tags the rating reads (removal,
// sweepers, tutors, ramp…), whether a card is a Game Changer (Commander's bracket rules), and its EDHREC salt score
// (how much other players dislike playing against it). The tags and Game Changers come from Scryfall, the salt from
// MTGJSON's AtomicCards, which a weekly GitHub job slims down onto the "card-salt" branch (MTGJSON's own file is 50+ MB).
// The table is card_facts (lib/accounts.ts), rebuilt weekly by /api/cron/categories; a later job rates local decks with it.
import { gunzipSync } from "node:zlib";
import { accountStore, type CardFact } from "./accounts";
import { nameKey, REBUILD_AFTER_MS } from "./autocategories";
import { USER_AGENT } from "./config";
import { scryFetch } from "./scrylimit";

export type { CardFact };

/** The Tagger tags kept (all exist on Scryfall; a card has whichever of them it is tagged with). */
export const FACT_TAGS = ["removal", "sweeper", "counterspell", "draw", "tutor", "ramp", "mana-rock", "mana-dork", "extra-turn", "mass-land-denial"] as const;

/** Where the weekly GitHub job (.github/workflows/card-salt.yml) keeps the salt scores, gzipped JSON by card name. */
export const SALT_URL = "https://raw.githubusercontent.com/joelvdc/MTG-trading-tracker/card-salt/card-salt.json.gz";

const SCRYFALL = "https://api.scryfall.com";

const frontKey = (name: string) => nameKey(name.split(" // ")[0]);

type Fetch = typeof fetch;
const pause = () => new Promise((r) => setTimeout(r, 100)); // Scryfall asks for 50–100 ms between requests.

/** A page of a card search, one at a time until has_more is false (404: no card matches). */
async function searchCards(f: Fetch, query: string, what: string, deadline: number): Promise<{ name: string }[]> {
  const out: { name: string }[] = [];
  let next: string | null = `${SCRYFALL}/cards/search?${new URLSearchParams({ q: query, unique: "cards", order: "name" })}`;
  while (next) {
    if (Date.now() > deadline) throw new Error("Scryfall's card searches took too long; the old table is kept.");
    await pause();
    const r = await f(next, { headers: { "User-Agent": USER_AGENT, Accept: "application/json" }, signal: AbortSignal.timeout(20_000), cache: "no-store" });
    if (r.status === 404) return out; // no card has this tag (any more)
    if (!r.ok) throw new Error(`Scryfall's search for ${what} failed (HTTP ${r.status}).`);
    const j = (await r.json()) as { data?: { name?: string }[]; has_more?: boolean; next_page?: string };
    for (const c of j.data ?? []) if (c.name) out.push({ name: c.name });
    next = j.has_more && j.next_page ? j.next_page : null;
  }
  return out;
}

/** The salt scores by name, from the card-salt branch; null when they can't be had (the table is built without salt). */
async function fetchSalt(f: Fetch, deadline: number): Promise<Map<string, number> | null> {
  try {
    if (Date.now() > deadline) throw new Error("no time left");
    const r = await f(SALT_URL, { headers: { "User-Agent": USER_AGENT }, signal: AbortSignal.timeout(20_000), cache: "no-store" });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const doc = JSON.parse(gunzipSync(new Uint8Array(await r.arrayBuffer())).toString("utf8")) as Record<string, unknown>;
    const m = new Map<string, number>();
    for (const [name, salt] of Object.entries(doc)) if (typeof salt === "number" && Number.isFinite(salt)) m.set(name, salt);
    return m;
  } catch (e) {
    console.warn(`Card facts: the salt scores couldn't be read (${e instanceof Error ? e.message : e}); every card gets no salt this time.`);
    return null;
  }
}

/**
 * Every card's facts from Scryfall (one paged search per tag, plus one for Game Changers; lands are kept, unlike the
 * auto categories) and its salt score from the card-salt branch. Fails as a whole if a Scryfall search fails, so a
 * half-built table never replaces the last good one; salt that can't be read only leaves the salt out.
 */
export async function fetchCardFacts(f: Fetch = scryFetch, deadline = Date.now() + 240_000): Promise<{ name: string; fact: CardFact }[]> {
  const byName = new Map<string, { name: string; tags: string[]; gameChanger: boolean }>();
  const row = (name: string) => byName.get(nameKey(name)) ?? { name, tags: [], gameChanger: false };
  for (const tag of FACT_TAGS) {
    for (const c of await searchCards(f, `otag:${tag}`, tag, deadline)) {
      const r = row(c.name);
      if (!r.tags.includes(tag)) r.tags.push(tag);
      byName.set(nameKey(c.name), r);
    }
  }
  for (const c of await searchCards(f, "is:gamechanger", "the Game Changers", deadline)) {
    const r = row(c.name);
    r.gameChanger = true;
    byName.set(nameKey(c.name), r);
  }
  const salt = await fetchSalt(f, deadline);
  return [...byName.values()].map((r) => ({
    name: r.name,
    fact: { tags: [...r.tags], gameChanger: r.gameChanger, salt: salt?.get(r.name) ?? (r.name.includes(" // ") ? (salt?.get(r.name.split(" // ")[0]) ?? null) : null) },
  }));
}

/**
 * The weekly job: rebuilds the facts table from Scryfall and the salt scores when it is older than a week (or empty).
 * Rows are by name; a double-faced card also gets one under its front face ("fire" for "Fire // Ice") unless that name
 * has a card of its own.
 */
export async function rebuildCardFacts(now = Date.now(), f: Fetch = scryFetch, deadline = now + 270_000): Promise<{ rebuilt: boolean; cards: number; updatedAt: number | null }> {
  const store = accountStore();
  if (!store) throw new Error("No database.");
  const before = await store.cardFactStats();
  if (before.cards > 0 && before.updatedAt !== null && before.updatedAt > now - REBUILD_AFTER_MS) return { rebuilt: false, ...before };
  const rows = await fetchCardFacts(f, deadline);
  if (rows.length === 0) throw new Error("Scryfall's card searches found no cards; the old table is kept.");
  const keyed = new Map<string, CardFact>();
  for (const r of rows) keyed.set(nameKey(r.name), r.fact);
  for (const r of rows) if (r.name.includes(" // ") && !keyed.has(frontKey(r.name))) keyed.set(frontKey(r.name), { ...r.fact, tags: [...r.fact.tags] });
  await store.replaceCardFacts([...keyed].map(([key, fact]) => ({ key, fact })), now);
  return { rebuilt: true, ...(await store.cardFactStats()) };
}

/**
 * The facts of each name, keyed by the name as asked (a double-faced card: its full name, then its front face). Names
 * the table doesn't have are left out.
 */
export async function cardFactsByName(names: string[]): Promise<Map<string, CardFact>> {
  const out = new Map<string, CardFact>();
  const store = accountStore();
  if (!store || names.length === 0) return out;
  const found = await store.cardFacts([...new Set(names.flatMap((n) => [nameKey(n), frontKey(n)]))]);
  for (const n of names) {
    const fact = found.get(nameKey(n)) ?? found.get(frontKey(n));
    if (fact) out.set(n, fact);
  }
  return out;
}
