// One deck's answer for its page (GET /api/decks/[id]): the lines with prices and owned/missing copies, the stats cards,
// the maybeboard's categories, the win turn kept here and the recorded games. Shared with a precon's read-only page,
// which builds the deck in memory and never saves it. Server only.
import { autoCategories, categoriesByName } from "./autocategories";
import type { DetailsFile } from "./carddetails";
import type { CkFile } from "./cardkingdom";
import { ensureDeckDetails, statsCard } from "./deckdetails";
import { maybeCategoryOf } from "./deckedit";
import { parseCardTags } from "./deckroles";
import { deckView, syncDecks } from "./decks";
import { gamesOf } from "./games";
import { gamesOfDeck } from "./gameshared";
import { deckLegality } from "./legalstore";
import type { PriceFile } from "./prices";
import { isUser, type Account } from "./session";
import { prefs, type SyncFile } from "./syncfile";
import { deckStatsCards, keptWins, parseWin } from "./winturns";

export interface DeckAnswerOptions {
  /** Include the win turn kept for the deck on the website (the default); false answers null without reading them. */
  winTurn?: boolean;
  /** Include the deck's recorded games (the default); false answers []. */
  games?: boolean;
}

/**
 * A deck as its page needs it, or null when the deck isn't in the sync file. [prices], [details] and [ck] are the
 * website's price lists (null when one couldn't be read).
 */
export async function deckAnswer(
  a: Account,
  file: SyncFile,
  id: number,
  prices: PriceFile | null,
  details: DetailsFile,
  ck: CkFile | null,
  opts: DeckAnswerOptions = {},
): Promise<Record<string, unknown> | null> {
  const synced = syncDecks(file).find((d) => d.deck.archidektId === id) ?? null;
  // The cards' own tags and their automatic categories, for the role line and the deck search; none when either fails.
  const cardTags = parseCardTags(prefs(file).cardTags);
  let autoByName: Record<string, string[]> = {};
  if (synced) {
    try {
      autoByName = await categoriesByName([...new Set(synced.cards.map((c) => c.card.name))]);
    } catch {
      autoByName = {};
    }
  }
  const view = !synced ? null : deckView(file, id, prices, details, ck, { cardTags, auto: autoByName });
  if (!view || !synced) return null;
  let info: Awaited<ReturnType<typeof ensureDeckDetails>> = {};
  let detailsError: string | null = null;
  try {
    // The maybeboard's cards too: their lines show the mana cost and the filter reads their rules text.
    info = await ensureDeckDetails([...new Set(synced.cards.map((c) => c.card.scryfallId))]);
  } catch (e) {
    detailsError = e instanceof Error ? e.message : "Scryfall couldn't be reached.";
  }
  const stats = deckStatsCards(synced, info);
  // The maybeboard's cards with their details, apart: the stats and the win turn leave them out.
  const maybeStats = synced.cards
    .filter((c) => c.maybeboard)
    .map((c) => statsCard({ name: c.card.name, scryfallId: c.card.scryfallId, quantity: c.quantity, cmc: c.cmc, types: c.types, commander: false }, info[c.card.scryfallId]));
  // The category each maybeboard line shows under when the maybeboard is split (display only; autoCategories
  // answers an empty map when the table can't be read, and the lines then go by card type).
  const maybeLines = synced.cards.filter((c) => c.maybeboard);
  const auto = maybeLines.length ? await autoCategories(maybeLines.map((c) => ({ scryfallId: c.card.scryfallId, name: c.card.name }))) : new Map();
  const maybeCategories = Object.fromEntries(maybeLines.map((c) => [c.lineUid, maybeCategoryOf(c, auto)]));
  const tokenInfo: Record<string, { oi: string | null; pt: string | null }> = {};
  // Scryfall's layout per printing, to tell which cards have a back face to flip to.
  const layouts: Record<string, string> = {};
  for (const s of [...stats, ...maybeStats]) if (info[s.scryfallId]?.y) layouts[s.scryfallId] = info[s.scryfallId].y!;
  for (const s of stats) for (const t of s.tokens ?? []) if (info[t.id]) tokenInfo[t.id] = { oi: info[t.id].oi, pt: info[t.id].pt };
  // The win turn kept on the website for this deck, if there is one (lib/winturns.ts); the page checks its key.
  const kept = opts.winTurn !== false && isUser(a) ? (await keptWins(a.accountId)).get(id) : undefined;
  const winTurn = kept ? { key: kept.key, win: parseWin(kept) } : null;
  // Commander legality, looking up the rules of cards the website doesn't know yet (a few seconds at most).
  const legality = await deckLegality(synced, true, Date.now(), Date.now() + 6_000).catch(() => null);
  const games = opts.games !== false ? gamesOfDeck(gamesOf(file), id) : [];
  return { ...view, stats, maybeStats, maybeCategories, tokenInfo, layouts, detailsError, saltCard: synced.deck.saltCard ?? null, winTurn, games, legality };
}
