import { currentAccount, errorResponse, json } from "@/lib/auth";
import { syncDecks } from "@/lib/decks";
import { RecError, recommendations, type RecSource } from "@/lib/recommend";
import { loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

type Params = { params: Promise<{ id: string }> };

/** ?source=edhrec|recommander: the deck's recommendations, fetched fresh (the browser keeps them for the visit). */
export async function GET(req: Request, { params }: Params) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const id = Number((await params).id);
    const source: RecSource = new URL(req.url).searchParams.get("source") === "recommander" ? "recommander" : "edhrec";
    const { file } = await loadSync(a);
    const sd = syncDecks(file).find((d) => d.deck.archidektId === id);
    if (!sd) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    const lines = sd.cards.filter((c) => !c.maybeboard).map((c) => ({ name: c.card.name, commander: c.commander }));
    return json(await recommendations(source, lines, sd.deck.commanders, sd.deck.colorIdentity));
  } catch (e) {
    if (e instanceof RecError) return json({ error: e.message }, 502);
    return errorResponse(e);
  }
}
