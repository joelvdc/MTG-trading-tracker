import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { appSettings } from "@/lib/appsettings";
import { ensureDeckDetails } from "@/lib/deckdetails";
import { putRating, rateCards, ratingStale } from "@/lib/deckrating";
import { syncDecks } from "@/lib/decks";
import { editSync, loadSync, ReadOnlyFile } from "@/lib/store";
import { deckStatsCards } from "@/lib/winturns";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

/** The facts the rating needs that aren't there yet (the card facts, Commander Spellbook). */
const NOT_READY = ["Card facts aren't ready yet.", "Commander Spellbook didn't answer"];

/**
 * Rates a deck with the website's own rating (lib/localratings.ts) and keeps it on the deck, ready for the page to show:
 * a local deck without one, or any deck when the setting has the bracket estimated by the app. A deck whose kept rating
 * is still the one for its list isn't rated again. Nothing is kept while the facts or the combos aren't there.
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
    // The server decides, as the page leaves it there: another list or version, or (with the estimate chosen) Commander
    // Salt's data on a deck from Archidekt, is rated again; nothing else is.
    if (!ratingStale(sd.deck.archidektId < 0, sd.deck.saltCard ?? null, stats, appSettings(file).bracketSource === "APP")) return json({ ok: false });
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
