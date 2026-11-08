// Recommendations for a deck (the app's Recommendations.kt): EDHREC's card lists for the commander, or
// recommander.cards' picks for the commander and the decklist, each card with its Scryfall printing (for the picture,
// the price and adding it) and whether it's new (first printed in the last year). Cards already in the deck are left
// out. Server only, apart from the types.
import { USER_AGENT } from "./config";
import { primaryType } from "./deckshared";
import { toCardRef } from "./scryfall";
import type { RecCard, RecResult, RecSection, RecSource } from "./recshared";

export * from "./recshared";

export class RecError extends Error {}

const recPrimaryType = (c: RecCard) => primaryType(c.typeLine.split(" // ")[0].split(" — ")[0]);

// ---- RecCategories: recommander.cards only gives names and scores, so the categories come from the card's text ----

const FUNCTION: [string, string][] = [
  ["tutors", "Tutors"],
  ["mass-removal", "Mass Removal"],
  ["spot-removal", "Spot Removal"],
  ["ramp", "Ramp"],
  ["card-advantage", "Card Advantage"],
];
const TYPES: [string, string][] = [
  ["Creature", "Creatures"],
  ["Artifact", "Artifacts"],
  ["Enchantment", "Enchantments"],
  ["Instant", "Instants"],
  ["Sorcery", "Sorceries"],
  ["Planeswalker", "Planeswalkers"],
  ["Battle", "Battles"],
];

const clean = (text: string) => text.toLowerCase().replace(/\([^)]*\)/g, "");
const MASS = /destroy all|exile all|destroy each|exile each (creature|nonland|artifact|enchantment|other)|all creatures get -|each creature gets -|damage to each creature|return all [^.]* to (their|its) owners?'? hands?/;
const SPOT =
  /destroy target|exile target (creature|artifact|enchantment|permanent|planeswalker|nonland|attacking|blocking|tapped|nontoken|card from a graveyard)|damage to (any target|target creature|target planeswalker|target attacking)|target creature gets -\d|target creature an opponent controls gets -|fights? (target|another target|up to one target)|return target (creature|nonland permanent|permanent|artifact|enchantment) to its owner's hand/;
const RAMP = /add \{|add one mana|add (two|three|x) mana|adds? an additional|mana of any (color|type)|onto the battlefield tapped|play an additional land|put a land card from your hand onto the battlefield/;
const DRAW = /draws? (a|an|one|two|three|four|five|x|that many|cards equal|\d+) cards?|\binvestigate\b|draw cards equal|exile the top [^.]* (you may|until end of turn,? you may) (play|cast)|look at the top [^.]* put [^.]* into your hand/;
const TUTOR = /search your library for ([^.]*)/g;
const LAND_SEARCH = /\b(land|forest|island|swamp|mountain|plains|gate|desert)\b/;

/** The function category of a card (RecCategories.function), or null. */
export function recFunction(typeLine: string, text: string): string | null {
  const t = clean(text);
  const tutored = [...t.matchAll(TUTOR)].map((m) => m[1]);
  // A search for lands only is ramp (or fixing), not a tutor.
  if (tutored.some((s) => !LAND_SEARCH.test(s.split(" card")[0]))) return "tutors";
  if (MASS.test(t)) return "mass-removal";
  if (SPOT.test(t)) return "spot-removal";
  const land = typeLine.includes("Land");
  if (!land && (RAMP.test(t) || (tutored.length > 0 && t.includes("onto the battlefield")))) return "ramp";
  if (DRAW.test(t)) return "card-advantage";
  return null;
}

const PLAIN_LAND =
  /^(\{t\}(, pay \d+ life)?: add [^.]*\.?|.*enters (the battlefield )?tapped.*|as .* enters.*choose a (color|basic land type).*|\{t\}, sacrifice .*: search your library for [^.]*land.*|.*you may pay \d+ life.*|.*unless you control.*)$/;

/** "utility-lands" (lands that do more than make mana) or "land" (RecCategories.landKind). */
export function landKind(text: string): string {
  const lines = clean(text)
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l);
  return lines.every((l) => PLAIN_LAND.test(l)) ? "land" : "utility-lands";
}

// ---- EDHREC ----------------------------------------------------------------------------------------------------------

/** "Baba Lysaga, Night Witch" → "baba-lysaga-night-witch"; partners are joined in alphabetical order. */
export function edhrecSlug(commanders: string[]): string {
  return commanders
    .map((name) =>
      name
        .split(" // ")[0]
        .normalize("NFD")
        .replace(/\p{M}/gu, "")
        .toLowerCase()
        .replace(/[^a-z0-9 -]/g, "")
        .trim()
        .replace(/[ -]+/g, "-"),
    )
    .sort()
    .join("-");
}

interface EdhEntry {
  id: string | null;
  name: string;
  synergy: number | null;
  decks: number | null;
  potential: number | null;
}

/** EDHREC's commander page (json.edhrec.com): its card lists as [tag, title, cards]. */
export function parseEdhrec(body: unknown): [string, string, EdhEntry[]][] {
  const lists = (body as { container?: { json_dict?: { cardlists?: unknown } } })?.container?.json_dict?.cardlists;
  if (!Array.isArray(lists)) throw new RecError("EDHREC has no card lists for this commander");
  const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : null);
  return lists.flatMap((l): [string, string, EdhEntry[]][] => {
    const o = l as Record<string, unknown>;
    if (typeof o?.header !== "string") return [];
    const title = o.header;
    const tag = typeof o.tag === "string" ? o.tag : title.toLowerCase();
    const cards = (Array.isArray(o.cardviews) ? o.cardviews : []).flatMap((v): EdhEntry[] => {
      const c = v as Record<string, unknown>;
      if (typeof c?.name !== "string") return [];
      return [{ id: typeof c.id === "string" ? c.id : null, name: c.name, synergy: num(c.synergy), decks: num(c.num_decks) === null ? null : Math.trunc(c.num_decks as number), potential: num(c.potential_decks) === null ? null : Math.trunc(c.potential_decks as number) }];
    });
    return [[tag, title, cards]];
  });
}

async function edhrecPage(commanders: string[]) {
  const r = await fetch(`https://json.edhrec.com/pages/commanders/${edhrecSlug(commanders)}.json`, {
    headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
    signal: AbortSignal.timeout(20_000),
    cache: "no-store",
  });
  if (r.status === 404 || r.status === 403) throw new RecError(`EDHREC has no page for ${commanders.join(" & ")} yet`);
  if (!r.ok) throw new RecError(`EDHREC answered ${r.status}`);
  return parseEdhrec(await r.json());
}

// ---- recommander.cards ---------------------------------------------------------------------------------------------

interface Rec {
  oracleId: string;
  name: string;
  score: number;
}

/** recommander.cards' picks (above 0.7, up to 200); with [candidates] (card names), only among those. */
async function recommander(commanders: string[], deck: string[], candidates: string[] | null = null): Promise<Rec[]> {
  const payload: Record<string, unknown> = { card_format: "name", commander: commanders[0] };
  if (commanders[1]) payload.partner = commanders[1];
  payload.deck = deck.slice(0, 500);
  if (candidates) payload.candidate_step = { source: "explicit", parameters: candidates.slice(0, 1000) };
  const r = await fetch("https://api.recommander.cards/public-release/api/decks/recommend/top", {
    method: "POST",
    headers: { "User-Agent": USER_AGENT, Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(30_000),
    cache: "no-store",
  });
  let o: { result_code?: string; data?: { recommendations?: unknown[] } } | null = null;
  try {
    o = await r.json();
  } catch {
    o = null;
  }
  const code = o?.result_code;
  if (code !== "success") {
    if (code === "error_invalid_cards") throw new RecError("recommander.cards doesn't know this commander yet");
    if (code === "error_rate_limited") throw new RecError("recommander.cards is busy; try again in a minute");
    if (code === "error_booting" || code === "error_model_loading") throw new RecError("recommander.cards is starting up; try again shortly");
    if (!r.ok) throw new RecError(`recommander.cards answered ${r.status}`);
    throw new RecError("recommander.cards sent something unexpected");
  }
  return (o?.data?.recommendations ?? []).flatMap((e): Rec[] => {
    const c = e as Record<string, unknown>;
    if (typeof c?.oracle_id !== "string" || typeof c?.name !== "string") return [];
    return [{ oracleId: c.oracle_id, name: c.name, score: typeof c.score === "number" ? c.score : 0 }];
  });
}

// ---- Scryfall --------------------------------------------------------------------------------------------------------

interface ScryRec {
  id: string;
  oracle_id?: string;
  name: string;
  set: string;
  type_line?: string;
  mana_cost?: string;
  card_faces?: { type_line?: string; oracle_text?: string; mana_cost?: string; image_uris?: { normal?: string } }[];
  oracle_text?: string;
  cmc?: number;
  reprint?: boolean;
  released_at?: string;
  edhrec_rank?: number;
  digital?: boolean;
}

const fullText = (c: ScryRec) => c.oracle_text ?? (c.card_faces ?? []).map((f) => f.oracle_text ?? "").join("\n");
const pause = () => new Promise((r) => setTimeout(r, 100));

async function scryJson(url: string, body?: unknown): Promise<unknown | null> {
  const r = await fetch(url, {
    method: body ? "POST" : "GET",
    headers: { "User-Agent": USER_AGENT, Accept: "application/json", ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(20_000),
    cache: "no-store",
  });
  if (r.status === 404) return null;
  if (!r.ok) throw new RecError(`Scryfall answered ${r.status}`);
  return r.json();
}

async function scryCollection(identifiers: object[]): Promise<ScryRec[]> {
  const out: ScryRec[] = [];
  for (let i = 0; i < identifiers.length; i += 75) {
    if (i > 0) await pause();
    const j = (await scryJson("https://api.scryfall.com/cards/collection", { identifiers: identifiers.slice(i, i + 75) })) as { data?: ScryRec[] } | null;
    out.push(...(j?.data ?? []));
  }
  return out;
}

/** Every card of a search, up to [maxPages] pages (ScryfallApi.searchAll). */
async function searchAll(query: string, maxPages = 6): Promise<ScryRec[]> {
  const out: ScryRec[] = [];
  let next: string | null = `https://api.scryfall.com/cards/search?${new URLSearchParams({ q: `${query} game:paper`, unique: "cards" })}`;
  for (let pages = 0; next && pages < maxPages; pages++) {
    if (pages > 0) await pause();
    const j = (await scryJson(next)) as { data?: ScryRec[]; has_more?: boolean; next_page?: string } | null;
    if (!j) break;
    out.push(...(j.data ?? []));
    next = j.has_more && j.next_page ? j.next_page : null;
  }
  return out;
}

// ---- Recommendations ---------------------------------------------------------------------------------------------------

const key = (name: string) => name.split(" // ")[0].trim().toLowerCase();

/** A year ago as yyyy-mm-dd. */
function newSince(now: number): string {
  const d = new Date(now);
  d.setUTCFullYear(d.getUTCFullYear() - 1);
  return d.toISOString().slice(0, 10);
}

function withScry(base: RecCard, s: ScryRec | undefined, since: string): RecCard {
  if (!s) return base;
  return {
    ...base,
    name: s.name,
    card: toCardRef(s as Parameters<typeof toCardRef>[0]),
    typeLine: s.type_line || (s.card_faces ?? []).map((f) => f.type_line ?? "").join(" // "),
    cmc: s.cmc ?? 0,
    isNew: !s.reprint && (s.released_at ?? "") >= since,
    releasedAt: s.released_at ?? null,
  };
}

const blank = (name: string, o: Partial<RecCard> = {}): RecCard => ({
  name,
  card: null,
  typeLine: "",
  cmc: 0,
  manaCost: null,
  score: null,
  synergy: null,
  decks: null,
  potentialDecks: null,
  isNew: false,
  releasedAt: null,
  ...o,
});

async function fromEdhrec(commanders: string[], inDeck: Set<string>, now: number): Promise<RecResult> {
  const page = await edhrecPage(commanders);
  const ids = [...new Set(page.flatMap(([, , e]) => e.flatMap((x) => (x.id ? [x.id] : []))))];
  const sc = new Map((await scryCollection(ids.map((id) => ({ id })))).map((c) => [c.id, c]));
  const since = newSince(now);
  const card = (e: EdhEntry) => withScry(blank(e.name, { synergy: e.synergy, decks: e.decks, potentialDecks: e.potential }), e.id ? sc.get(e.id) : undefined, since);
  const sections = page
    .map(([tag, title, entries]): RecSection => ({ key: tag, title, cards: entries.filter((e) => !inDeck.has(key(e.name))).map(card) }))
    .filter((s) => s.cards.length > 0);
  // EDHREC shows five new cards; every recommended card first printed in the last year is listed.
  const edhNew = sections.find((s) => s.key === "newcards")?.cards ?? [];
  const seen = new Set<string>();
  const recent = [...edhNew, ...sections.flatMap((s) => s.cards).filter((c) => c.isNew)]
    .filter((c) => !seen.has(c.name) && (seen.add(c.name), true))
    .sort((a, b) => (b.synergy ?? 0) - (a.synergy ?? 0));
  const rest = sections.filter((s) => s.key !== "newcards");
  return { source: "edhrec", fetchedAt: now, sections: recent.length ? [{ key: "newcards", title: "New Cards (recent releases)", cards: recent }, ...rest] : rest, note: null };
}

async function fromRecommander(commanders: string[], deck: string[], colorIdentity: string, inDeck: Set<string>, now: number): Promise<RecResult> {
  const recs = (await recommander(commanders, deck)).filter((r) => !inDeck.has(key(r.name)));
  const since = newSince(now);
  let newFailed = false;
  let recent: Rec[] = [];
  try {
    const ci = (colorIdentity || "c").toLowerCase();
    const names = (await searchAll(`f:commander id<=${ci} not:reprint date>=${since} -is:digital`)).map((c) => c.name);
    recent = names.length ? (await recommander(commanders, deck, names)).filter((r) => !inDeck.has(key(r.name))) : [];
  } catch {
    newFailed = true;
  }
  const ids = [...new Set([...recs, ...recent].map((r) => r.oracleId))];
  const sc = new Map((await scryCollection(ids.map((oracle_id) => ({ oracle_id })))).flatMap((c) => (c.oracle_id ? [[c.oracle_id, c] as const] : [])));
  const card = (r: Rec): [RecCard, ScryRec | undefined] => [withScry(blank(r.name, { score: r.score }), sc.get(r.oracleId), since), sc.get(r.oracleId)];
  const all = recs.map(card);
  const sections: RecSection[] = [{ key: "top", title: "Top Recommendations", cards: all.slice(0, 30).map((p) => p[0]) }];
  const seen = new Set<string>();
  const newCards = [...recent.map(card).map((p) => p[0]), ...all.map((p) => p[0]).filter((c) => c.isNew)].filter((c) => !seen.has(c.name) && (seen.add(c.name), true));
  if (newCards.length) sections.push({ key: "new-cards", title: "New Cards", cards: [...newCards].sort((a, b) => (b.score ?? 0) - (a.score ?? 0)) });
  const byFunction = new Map<string | null, RecCard[]>();
  for (const [c, s] of all) {
    const k = s ? recFunction(c.typeLine, fullText(s)) : null;
    byFunction.set(k, [...(byFunction.get(k) ?? []), c]);
  }
  for (const [k, title] of FUNCTION) {
    const l = byFunction.get(k);
    if (l) sections.push({ key: k, title, cards: l });
  }
  const staples = all.filter(([c, s]) => (s?.edhrec_rank ?? Number.MAX_SAFE_INTEGER) <= 300 && !c.typeLine.includes("Land"));
  if (staples.length) sections.push({ key: "staples", title: "General Staples", cards: staples.map((p) => p[0]) });
  const isLand = (c: RecCard) => c.typeLine.split(" // ")[0].includes("Land");
  const nonLand = all.filter(([c]) => !isLand(c));
  for (const [type, title] of TYPES) {
    const l = nonLand.filter(([c]) => recPrimaryType(c) === type);
    if (l.length) sections.push({ key: type.toLowerCase(), title, cards: l.map((p) => p[0]) });
  }
  const lands = all.filter(([c]) => isLand(c));
  const utility = lands.filter(([, s]) => landKind(s ? fullText(s) : "") === "utility-lands");
  const plain = lands.filter(([, s]) => landKind(s ? fullText(s) : "") === "land");
  if (utility.length) sections.push({ key: "utility-lands", title: "Utility Lands", cards: utility.map((p) => p[0]) });
  if (plain.length) sections.push({ key: "land", title: "Lands", cards: plain.map((p) => p[0]) });
  return { source: "recommander", fetchedAt: now, sections, note: newFailed ? "The new cards couldn't be fetched this time; refresh to try again." : null };
}

/** A deck's recommendations (Recommendations.fetch): [lines] are its cards without the maybeboard. */
export async function recommendations(
  source: RecSource,
  lines: { name: string; commander: boolean }[],
  deckCommanders: string,
  colorIdentity: string,
  now = Date.now(),
): Promise<RecResult> {
  const named = lines.filter((c) => c.commander).map((c) => c.name);
  const commanders = named.length ? named : deckCommanders.split(" & ").filter((x) => x.trim());
  if (commanders.length === 0) throw new RecError("The deck has no commander to recommend cards for.");
  const inDeck = new Set(lines.map((c) => key(c.name)));
  return source === "edhrec"
    ? fromEdhrec(commanders, inDeck, now)
    : fromRecommander(
        commanders,
        lines.filter((c) => !c.commander).map((c) => c.name),
        colorIdentity,
        inDeck,
        now,
      );
}
