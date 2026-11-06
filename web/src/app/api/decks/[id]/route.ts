import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { ensureDeckDetails, statsCard } from "@/lib/deckdetails";
import { deckView, missingPlan, ownedByName, syncDecks } from "@/lib/decks";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { applyEdit } from "@/lib/wishlist";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

type Params = { params: Promise<{ id: string }> };

function deckId(raw: string): number | null {
  const n = Number(raw);
  return Number.isSafeInteger(n) ? n : null;
}

/**
 * One deck: its lines with prices and owned/missing copies, and the stats cards (its lines with Scryfall's mana cost,
 * rules text and tokens, looked up on the first visit) the page works the stats out from.
 */
export async function GET(_req: Request, { params }: Params) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const id = deckId((await params).id);
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    const view = id === null ? null : deckView(file, id, prices, details, ck);
    if (!view) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    const lines = syncDecks(file).find((d) => d.deck.archidektId === id)!.cards.filter((c) => !c.maybeboard);
    let info: Awaited<ReturnType<typeof ensureDeckDetails>> = {};
    let detailsError: string | null = null;
    try {
      info = await ensureDeckDetails(lines.map((c) => c.card.scryfallId));
    } catch (e) {
      detailsError = e instanceof Error ? e.message : "Scryfall couldn't be reached.";
    }
    const stats = lines.map((c) =>
      statsCard({ name: c.card.name, scryfallId: c.card.scryfallId, quantity: c.quantity, cmc: c.cmc, types: c.types, commander: c.commander }, info[c.card.scryfallId]),
    );
    const tokenInfo: Record<string, { oi: string | null; pt: string | null }> = {};
    for (const s of stats) for (const t of s.tokens ?? []) if (info[t.id]) tokenInfo[t.id] = { oi: info[t.id].oi, pt: info[t.id].pt };
    return json({ ...view, stats, tokenInfo, detailsError });
  } catch (e) {
    return errorResponse(e);
  }
}

/** { action: "missing-to-wishlist", skipBasics }: the cards the collection lacks go on the wishlist (any printing). */
export async function POST(req: Request, { params }: Params) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const id = deckId((await params).id);
    const body = (await req.json()) as { action?: string; skipBasics?: boolean };
    if (body?.action !== "missing-to-wishlist") return json({ error: "Unknown action." }, 400);
    const added = await editSync(a, (f, now) => {
      const sd = syncDecks(f).find((d) => d.deck.archidektId === id);
      if (!sd) return 0;
      const lines = sd.cards.filter((c) => !c.maybeboard).map((c) => ({ c, name: c.card.name, quantity: c.quantity }));
      const plan = missingPlan(lines, ownedByName(f), body.skipBasics !== false);
      for (const [{ c }, n] of plan) applyEdit(f, { type: "add", card: c.card, foil: c.foil, quantity: n, anyPrinting: true }, now);
      return plan.reduce((s, [, n]) => s + n, 0);
    });
    return json({ added });
  } catch (e) {
    return errorResponse(e);
  }
}
