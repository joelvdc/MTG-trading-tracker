// The website's own rating of local decks, on the server: what the deck's cards are known to be (cardfacts.ts), the
// combos Commander Spellbook finds (spellbook.ts) and the rating itself (localratings.ts), then whether a deck's kept
// rating is still the one for its list and how it is put on the deck. Server only.
import { accountStore } from "./accounts";
import { cardFactsByName } from "./cardfacts";
import { syncDecks } from "./decks";
import { decodeSalt, type SaltCard } from "./deckshared";
import type { StatsCard } from "./deckstats";
import { rateDeck, RATING_VERSION, ratingListKey } from "./localratings";
import { findCombos } from "./spellbook";
import { bump, type Json, type SyncFile } from "./syncfile";

/**
 * The rating of the deck's cards, as the app's SaltCard. Throws when the card facts aren't built yet, so a deck is
 * never rated from nothing, and when Commander Spellbook doesn't answer.
 */
export async function rateCards(cards: StatsCard[], identity: string | null): Promise<SaltCard> {
  const store = accountStore();
  if (!store) throw new Error("Card facts aren't ready yet.");
  const stats = (await store.cardFactStats()) ?? { cards: 0, updatedAt: null };
  if (!stats.cards) throw new Error("Card facts aren't ready yet.");
  const facts = await cardFactsByName([...new Set(cards.map((c) => c.name))]);
  const combos = await findCombos(
    cards.filter((c) => c.commander).map((c) => c.name),
    cards.filter((c) => !c.commander).map((c) => ({ name: c.name, quantity: c.quantity })),
  );
  return rateDeck(cards, facts, combos, identity);
}

/**
 * Whether a deck's kept rating is out of date: only for a local deck that has none, or has the website's own rating of
 * another list (or another rating version). Commander Salt's data (a deck made local from Archidekt) is never stale.
 */
export function ratingStale(local: boolean, saltCard: string | null, stats: StatsCard[]): boolean {
  if (!local) return false;
  const card = decodeSalt(saltCard);
  if (!card) return true;
  if (card.source !== "app") return false;
  return card.listKey !== ratingListKey(stats) || card.ratingVersion !== RATING_VERSION;
}

/**
 * Puts the website's rating on a local deck (like putScores in deckload.ts). Its own bracket and salt become the
 * deck's; Commander Salt's power level, salt percentage and archetype stay as they were. False when there's no such
 * local deck.
 */
export function putRating(file: SyncFile, id: number, card: SaltCard, now: number): boolean {
  const all = syncDecks(file);
  const where = all.findIndex((d) => d.deck.archidektId === id);
  if (where < 0 || id >= 0) return false;
  const sd = all[where];
  const d = sd.deck;
  d.saltCard = JSON.stringify(card);
  d.bracketBaseline = card.bracket ?? null;
  d.bracketRealistic = null;
  d.scoredAt = now;
  d.scoreError = null;
  d.updatedAt = bump(d.updatedAt ?? 0, now);
  file.data.decks = all.map((x, i) => (i === where ? { deck: d, cards: x.cards } : x)) as unknown as Json;
  return true;
}
