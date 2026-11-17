// One Commander precon as its deck page shows it: the same answer as GET /api/decks/[id], with the user's prices and
// owned marks. The deck is built in memory and never saved, so nothing here can change the collection.
import { currentAccount, errorResponse, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { deckAnswer } from "@/lib/deckanswer";
import { createDeck, NO_LOOKUPS } from "@/lib/deckedit";
import { cachedPreconLines } from "@/lib/deckimport";
import { preconList } from "@/lib/preconfetch";
import { loadPrices } from "@/lib/pricestore";
import { loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";
// The deck's cards are looked up on Scryfall the first time, which can take a while.
export const maxDuration = 60;

/** ?deck=<MTGJSON's file name>: one precon's deck page, read only. */
export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const fileName = (new URL(req.url).searchParams.get("deck") ?? "").slice(0, 300);
    if (!fileName) return json({ error: "Pick a deck first." }, 400);
    const p = await cachedPreconLines(fileName);
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    // A copy of the sync file: the deck lives in it for this answer only and is never written anywhere.
    const f = structuredClone(file);
    const id = createDeck(f, p.name, p.lines, p.origin, p.identity, Date.now(), NO_LOOKUPS);
    const answer = await deckAnswer(a, f, id, prices, details, ck, { winTurn: false, games: false });
    const info = (await preconList()).find((d) => d.fileName === fileName);
    return json({ ...(answer ?? {}), readOnly: true, precon: { fileName, name: p.name, code: info?.code ?? "", releaseDate: info?.releaseDate ?? null } });
  } catch (e) {
    return errorResponse(e);
  }
}
