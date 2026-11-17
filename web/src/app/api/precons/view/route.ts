// One Commander precon as its deck page shows it: the same answer as GET /api/decks/[id], with the user's prices and
// owned marks. The deck is built in memory and never saved, so nothing here can change the collection.
import { accountStore } from "@/lib/accounts";
import { currentAccount, errorResponse, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { deckAnswer } from "@/lib/deckanswer";
import { ensureDeckDetails } from "@/lib/deckdetails";
import { buildPreconDeck } from "@/lib/precondeck";
import { preconList } from "@/lib/preconfetch";
import { loadPrices } from "@/lib/pricestore";
import { loadSync } from "@/lib/store";
import { parseWin, winSource } from "@/lib/winturns";

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
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    // A copy of the sync file: the deck lives in it for this answer only and is never written anywhere.
    const { name, file: f, id, sd } = await buildPreconDeck(file, fileName);
    const answer = await deckAnswer(a, f, id, prices, details, ck, { winTurn: false, games: false });
    // The win turn kept for this precon on the website (the cards are the same for every account), if its list still
    // matches; a failure here must not break the page.
    let winTurn: { key: string; win: ReturnType<typeof parseWin> } | null = null;
    try {
      const deckInfo = sd ? await ensureDeckDetails(sd.cards.filter((c) => !c.maybeboard).map((c) => c.card.scryfallId)) : {};
      const source = sd ? winSource(sd, deckInfo) : null;
      const stored = await accountStore()?.preconWinTurn(fileName);
      if (stored && source && stored.key === source.key) winTurn = { key: source.key, win: parseWin(stored) };
    } catch {
      winTurn = null;
    }
    const info = (await preconList()).find((d) => d.fileName === fileName);
    return json({
      ...(answer ?? {}),
      readOnly: true,
      winTurn,
      // A precon has no Commander Salt score: its power comes from the app's win turn.
      powerSource: "APP",
      power: null,
      precon: { fileName, name, code: info?.code ?? "", releaseDate: info?.releaseDate ?? null },
    });
  } catch (e) {
    return errorResponse(e);
  }
}
