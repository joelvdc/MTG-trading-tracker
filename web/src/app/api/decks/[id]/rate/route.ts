import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { ensureDeckDetails } from "@/lib/deckdetails";
import { putRating, rateCards } from "@/lib/deckrating";
import { syncDecks } from "@/lib/decks";
import { editSync, loadSync, ReadOnlyFile } from "@/lib/store";
import { deckStatsCards } from "@/lib/winturns";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

/** The facts the rating needs that aren't there yet (the card facts, Commander Spellbook). */
const NOT_READY = ["Card facts aren't ready yet.", "Commander Spellbook didn't answer"];

/**
 * Rates a local deck with the website's own rating (lib/localratings.ts) and keeps it on the deck, ready for the
 * page to show. Nothing is kept while the facts or the combos aren't there.
 */
export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const id = Number((await params).id);
    const { file } = await loadSync(a);
    const sd = syncDecks(file).find((d) => d.deck.archidektId === id);
    if (!sd) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    // Only the main deck is rated (the maybeboard never is, as in the stats and the win turn).
    const info = await ensureDeckDetails(sd.cards.filter((c) => !c.maybeboard).map((c) => c.card.scryfallId));
    const stats = deckStatsCards(sd, info);
    const card = await rateCards(stats, sd.deck.colorIdentity || null);
    try {
      const kept = await editSync(a, (f, now) => putRating(f, id, card, now));
      return json({ ok: kept });
    } catch (e) {
      if (e instanceof ReadOnlyFile) return json({ ok: false });
      throw e;
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : "";
    if (NOT_READY.some((m) => message.startsWith(m))) return json({ error: message }, 503);
    return errorResponse(e);
  }
}
