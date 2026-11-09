// Recommendation types shared by the server (recommend.ts) and the deck page.
import { primaryType } from "./deckshared";
import type { CardRef } from "./syncfile";

export type RecSource = "edhrec" | "recommander";
export const REC_SOURCES: Record<RecSource, string> = { edhrec: "EDHREC", recommander: "recommander.cards" };

export interface RecCard {
  name: string;
  card: CardRef | null;
  typeLine: string;
  cmc: number;
  /** The mana cost, e.g. "{2}{U}" (the front face's for double-faced cards). */
  manaCost: string | null;
  /** recommander.cards' score (0.7 and up). */
  score: number | null;
  /** EDHREC: how much more often the card is played with this commander than elsewhere (−1…1). */
  synergy: number | null;
  /** EDHREC: decks with this commander that play the card, of those that could. */
  decks: number | null;
  potentialDecks: number | null;
  isNew: boolean;
  releasedAt: string | null;
  /** Copies in the collection, any printing (filled in by the website's route). */
  owned?: number;
  /** Whether the card is on the wishlist and not received yet (filled in by the website's route). */
  wished?: boolean;
}

export interface RecSection {
  key: string;
  title: string;
  cards: RecCard[];
}

export interface RecResult {
  source: RecSource;
  fetchedAt: number;
  sections: RecSection[];
  note: string | null;
  /** The deck's list when fetched (recListKey; missing in results kept before 2026-11-09). */
  listKey?: string;
}

/**
 * A short fingerprint of a deck's list (its cards without the maybeboard, and which are commanders): when it's the same,
 * the deck's recommendations needn't be fetched again until they're older than the "up to date" period.
 */
export function recListKey(lines: { name: string; commander: boolean }[]): string {
  const text = lines
    .map((c) => (c.commander ? "*" : "") + c.name.trim().toLowerCase())
    .sort()
    .join("\n");
  // FNV-1a, 32 bits, plus the number of cards: enough to tell a changed list.
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return `${lines.length}-${h.toString(36)}`;
}

/** Where a deck's kept recommendations stand: none, up to date, too old, or fetched for a list that changed since. */
export type RecState = "none" | "fresh" | "old" | "changed";

export function recState(r: RecResult | null | undefined, listKey: string | null | undefined, keepMs: number, now = Date.now()): RecState {
  if (!r) return "none";
  if (!r.listKey || !listKey || r.listKey !== listKey) return "changed";
  return now - r.fetchedAt < keepMs ? "fresh" : "old";
}

/** The "up to date" periods offered in Settings, in days (1 day by default). */
export const REC_KEEP_DAYS = [1, 3, 7, 30] as const;
export const DEFAULT_REC_KEEP_DAYS = 1;

/** [r] with what you own and want as they are now (the marks kept with it are from when it was fetched). */
export function withRecMarks(r: RecResult, owned: Record<string, number>, wished: Set<string>): RecResult {
  return {
    ...r,
    sections: r.sections.map((s) => ({
      ...s,
      cards: s.cards.map((c) => ({ ...c, owned: owned[c.name.toLowerCase()] ?? 0, wished: wished.has(c.name.toLowerCase()) })),
    })),
  };
}

export const inclusionPct = (c: RecCard) => (c.decks != null && c.potentialDecks != null && c.potentialDecks > 0 ? Math.floor((c.decks * 100) / c.potentialDecks) : null);

/** What the source says about a card, in the app's words. */
export const recFacts = (c: RecCard): string => {
  const out: string[] = [];
  const pct = inclusionPct(c);
  if (pct !== null) out.push(`in ${pct}% of decks`);
  if (c.synergy !== null) out.push(`synergy ${c.synergy >= 0 ? "+" : ""}${Math.round(c.synergy * 100)}%`);
  if (c.score !== null) out.push(`score ${c.score.toFixed(2)}`);
  return out.join(" · ");
};

/** The card types to filter recommendations by, as the app's TYPE_FILTERS (RecommendationsScreen.kt). */
export const REC_TYPES: [string, string][] = [
  ["Creature", "Creatures"],
  ["Instant", "Instants"],
  ["Sorcery", "Sorceries"],
  ["Artifact", "Artifacts"],
  ["Enchantment", "Enchantments"],
  ["Planeswalker", "Planeswalkers"],
  ["Land", "Lands"],
  ["Battle", "Battles"],
];

export type RecOwned = "all" | "mine" | "notMine";
export const REC_OWNED: Record<RecOwned, string> = { all: "Owned or not", mine: "I own", notMine: "I don't own" };

/** What the recommendation lists are filtered by (the app's RecFilter). */
export interface RecFilter {
  type: string | null;
  owned: RecOwned;
  newOnly: boolean;
}
export const NO_REC_FILTER: RecFilter = { type: null, owned: "all", newOnly: false };

/** The card's main type, as the app's RecCard.primaryType (front face, before the subtypes). */
export const recType = (c: RecCard) => primaryType(c.typeLine.split(" // ")[0].split(" — ")[0]);

/** Whether a recommendation passes the filter (the app's RecFilter.keep). */
export const recKeep = (f: RecFilter, c: RecCard): boolean =>
  (f.type === null || recType(c) === f.type) && (!f.newOnly || c.isNew) && (f.owned === "all" || (f.owned === "mine") === (c.owned ?? 0) > 0);

/** A card recommended for one or more of your decks (the all-decks page; the app's RecommendationsAllScreen). */
export interface RecAcross {
  card: RecCard;
  /** The decks it's recommended for, in the order the decks were given. */
  deckIds: number[];
}

/** The app's name key: the front face's name, lower case. */
export const recNameKey = (name: string) => name.split(" // ")[0].trim().toLowerCase();

/**
 * Every card recommended for the given decks, once, with the decks it's recommended for, the ones for the most decks
 * first ([several]: only those for two decks or more). A card's owned / wished come from its newest fetch, and the
 * filter ([keep]) is applied to that.
 */
export function recsByCard(results: { deckId: number; result: RecResult }[], keep: (c: RecCard) => boolean, several = false): RecAcross[] {
  const byName = new Map<string, { card: RecCard; at: number; deckIds: number[] }>();
  for (const { deckId, result } of results)
    for (const s of result.sections)
      for (const c of s.cards) {
        const k = recNameKey(c.name);
        const had = byName.get(k);
        if (!had) byName.set(k, { card: c, at: result.fetchedAt, deckIds: [deckId] });
        else {
          if (!had.deckIds.includes(deckId)) had.deckIds.push(deckId);
          if (result.fetchedAt > had.at) Object.assign(had, { card: { ...had.card, owned: c.owned, wished: c.wished }, at: result.fetchedAt });
        }
      }
  // Filtered after merging, so "I own" goes by the newest count.
  const all = [...byName.values()].filter((x) => keep(x.card)).map(({ card, deckIds }) => ({ card, deckIds }));
  return (several ? all.filter((x) => x.deckIds.length > 1) : all).sort((a, b) => b.deckIds.length - a.deckIds.length);
}
