import { accountStore } from "@/lib/accounts";
import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { ensureDeckDetails } from "@/lib/deckdetails";
import { buildPreconDeck } from "@/lib/precondeck";
import { isUser } from "@/lib/session";
import { loadSync } from "@/lib/store";
import { winSource } from "@/lib/winturns";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

/** The largest answer kept (the app's JSON for a deck is a few KB). */
const MAX_RESULT = 200_000;

/**
 * Keeps the win turn a precon's page worked out in the browser, so every account sees it at once (the cards are the
 * same for everyone): only when [key] is still the key of the precon's list (lib/winturns.ts). Body: {fileName, key,
 * result} (the app's JSON answer).
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  if (!isUser(a)) return json({ kept: false });
  try {
    const body = ((await req.json()) ?? {}) as { fileName?: unknown; key?: unknown; result?: unknown };
    if (typeof body.fileName !== "string" || !body.fileName || typeof body.key !== "string" || typeof body.result !== "string" || body.result.length > MAX_RESULT)
      return json({ error: "That isn't a win turn." }, 400);
    try {
      JSON.parse(body.result);
    } catch {
      return json({ error: "That isn't a win turn." }, 400);
    }
    const fileName = body.fileName.slice(0, 300);
    const { file } = await loadSync(a);
    // The deck is built in memory, in a copy of the sync file: nothing is saved.
    const { sd } = await buildPreconDeck(file, fileName);
    if (!sd) return json({ error: "That deck isn't in MTGJSON's list." }, 404);
    const info = await ensureDeckDetails(sd.cards.filter((c) => !c.maybeboard).map((c) => c.card.scryfallId));
    const source = winSource(sd, info);
    if (!source || source.key !== body.key) return json({ kept: false });
    await accountStore()?.putPreconWinTurn(fileName, { key: body.key, result: body.result, at: Date.now() });
    return json({ kept: true });
  } catch (e) {
    return errorResponse(e);
  }
}
