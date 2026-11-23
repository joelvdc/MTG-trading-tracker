import { currentAccount, errorResponse, json } from "@/lib/auth";
import { categoriesByName } from "@/lib/autocategories";
import { cardFactsByName, type CardFact } from "@/lib/cardfacts";
import { loadDeckDetails, type DeckDetailsFile } from "@/lib/deckdetails";
import { parseCardTags } from "@/lib/deckroles";
import { ruleZeroDecks, syncDecks } from "@/lib/decks";
import { saltSumOf } from "@/lib/localratings";
import { isUser } from "@/lib/session";
import { loadSync } from "@/lib/store";
import { prefs } from "@/lib/syncfile";
import { keptWins, parseWin } from "@/lib/winturns";

export const dynamic = "force-dynamic";

/** The bracket cards of all own decks (the app's "Bracket cards of all decks"), with the kept win turns. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const { file } = await loadSync(a);
    // The roles lines read the cards' own tags, the automatic categories and Scryfall's type lines (none when either
    // can't be read: the lands then count from Archidekt's types).
    const names = [...new Set(syncDecks(file).flatMap((d) => d.cards.map((c) => c.card.name)))];
    let auto: Record<string, string[]> = {};
    try {
      auto = await categoriesByName(names);
    } catch {
      auto = {};
    }
    let details: DeckDetailsFile = { madeAt: 0, cards: {} };
    try {
      details = await loadDeckDetails();
    } catch {
      details = { madeAt: 0, cards: {} };
    }
    const r = ruleZeroDecks(file, { cardTags: parseCardTags(prefs(file).cardTags), auto, typeLine: (id) => details.cards[id]?.t ?? null });
    // The salt sum of each deck (lib/localratings.ts), from one reading of the facts table for every deck's cards; null
    // when it can't be read, or for a deck whose cards have no facts.
    let facts = new Map<string, CardFact>();
    try {
      facts = await cardFactsByName([...new Set(r.decks.flatMap((d) => d.stats.map((c) => c.name)))]);
    } catch {
      facts = new Map();
    }
    const kept = isUser(a) ? await keptWins(a.accountId) : new Map();
    const wins = Object.fromEntries(r.decks.flatMap((d) => (kept.has(d.id) ? [[d.id, parseWin(kept.get(d.id))]] : [])));
    return json({
      decks: r.decks.map(({ stats, ...d }) => ({ ...d, saltSum: facts.size > 0 ? saltSumOf(stats, facts) : null })),
      unrated: r.unrated,
      wins,
    });
  } catch (e) {
    return errorResponse(e);
  }
}
