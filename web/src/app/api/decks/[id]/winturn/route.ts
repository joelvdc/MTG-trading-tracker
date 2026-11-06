import { accountStore } from "@/lib/accounts";
import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { ensureDeckDetails } from "@/lib/deckdetails";
import { syncDecks } from "@/lib/decks";
import { isUser } from "@/lib/session";
import { loadSync } from "@/lib/store";
import { winSource } from "@/lib/winturns";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

/** The largest answer kept (the app's JSON for a deck is a few KB). */
const MAX_RESULT = 200_000;

/**
 * Keeps the win turn the deck page worked out in the browser, so the other pages and devices have it: only when
 * [key] is still the key of the deck's list (lib/winturns.ts). Body: {key, result} (the app's JSON answer).
 */
export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  if (!isUser(a)) return json({ kept: false });
  try {
    const id = Number((await params).id);
    const body = ((await req.json()) ?? {}) as { key?: unknown; result?: unknown };
    if (typeof body.key !== "string" || typeof body.result !== "string" || body.result.length > MAX_RESULT) return json({ error: "That isn't a win turn." }, 400);
    try {
      JSON.parse(body.result);
    } catch {
      return json({ error: "That isn't a win turn." }, 400);
    }
    const { file } = await loadSync(a);
    const sd = syncDecks(file).find((d) => d.deck.archidektId === id);
    if (!sd) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    const info = await ensureDeckDetails(sd.cards.filter((c) => !c.maybeboard).map((c) => c.card.scryfallId));
    const source = winSource(sd, info);
    if (!source || source.key !== body.key) return json({ kept: false });
    await accountStore()?.putWinTurn(a.accountId, id, { key: body.key, result: body.result, at: Date.now() });
    return json({ kept: true });
  } catch (e) {
    return errorResponse(e);
  }
}
