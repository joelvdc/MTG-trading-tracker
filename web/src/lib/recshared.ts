// Recommendation types shared by the server (recommend.ts) and the deck page.
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
