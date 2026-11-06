// Recommendation types shared by the server (recommend.ts) and the deck page.
import type { CardRef } from "./syncfile";

export type RecSource = "edhrec" | "recommander";
export const REC_SOURCES: Record<RecSource, string> = { edhrec: "EDHREC", recommander: "recommander.cards" };

export interface RecCard {
  name: string;
  card: CardRef | null;
  typeLine: string;
  cmc: number;
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
