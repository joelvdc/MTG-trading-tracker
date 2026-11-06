import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { applyImport, identifiers, keyCards, readCsv } from "@/lib/csvimport";
import { collectionView } from "@/lib/collview";
import { loadPrices } from "@/lib/pricestore";
import { lookupCollection } from "@/lib/scryfall";
import { editSync } from "@/lib/store";
import { binders } from "@/lib/syncfile";

export const dynamic = "force-dynamic";
export const maxDuration = 120;

const MAX_LINES = 20_000;

/** Imports a collection CSV ({ text, binder }: binder uid for lines without one, null for Unsorted). */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { text?: unknown; binder?: unknown };
    if (typeof body?.text !== "string" || body.text.length > 8_000_000) return json({ error: "Send a CSV file of at most 8 MB." }, 400);
    const lines = readCsv(body.text);
    if (lines.length === 0) return json({ error: "No cards found: the file needs a header line with a Name, Scryfall ID or Set code + Collector number column." }, 400);
    if (lines.length > MAX_LINES) return json({ error: `At most ${MAX_LINES} lines at a time.` }, 400);
    const cards = keyCards(await lookupCollection(identifiers(lines)));
    const [prices, details, ck] = await Promise.all([loadPrices(), loadDetails(), loadCardKingdom()]);
    const { file, result } = await editSync(a, (f, now) => {
      const binder = typeof body.binder === "string" && binders(f).some((b) => b.uid === body.binder) ? body.binder : null;
      return { file: f, result: applyImport(f, lines, cards, binder, now, prices?.rates ?? null) };
    });
    return json({ ...collectionView(file, prices, details, ck), import: result });
  } catch (e) {
    return errorResponse(e);
  }
}
