import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { createDeck, NO_LOOKUPS, newEmptyDeck } from "@/lib/deckedit";
import { preconLines, previewText, textLines } from "@/lib/deckimport";
import { textCardCount } from "@/lib/decktext";
import { decksView } from "@/lib/decks";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { EditError } from "@/lib/wishlist";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

/** The decks list (the app's Decks tab). */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    return json(decksView(file, prices, details, ck));
  } catch (e) {
    return errorResponse(e);
  }
}

interface Body {
  action?: string;
  name?: string;
  text?: string;
  useTags?: boolean;
  commander?: string | null;
  fileName?: string;
}

const str = (v: unknown, max = 200) => (typeof v === "string" ? v.slice(0, max) : "");

/**
 * New local decks: "new" (empty), "text-preview" (what a pasted list holds), "from-text" (a deck from a pasted list)
 * and "from-precon" (a deck from MTGJSON's preconstructed decks). Answers with the new deck's id.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = ((await req.json()) ?? {}) as Body;
    switch (body.action) {
      case "new":
        return json({ id: await editSync(a, (f, now) => newEmptyDeck(f, str(body.name), now)) });
      case "text-preview": {
        const p = await previewText(str(body.text, 60_000));
        return json({
          cards: textCardCount(p.result),
          lines: p.result.lines.length,
          exact: p.exact,
          byName: p.byName,
          notFound: p.notFound,
          unreadable: p.result.unreadable,
          commanders: p.commanders,
          hasTags: p.result.lines.some((l) => l.tags.length > 0),
          names: [...new Set(p.found.flatMap((c) => (c ? [c.card.name] : [])))].sort(),
        });
      }
      case "from-text": {
        const p = await previewText(str(body.text, 60_000));
        if (p.result.lines.length === 0) throw new EditError("No cards in that list.");
        const { lines, identity } = await textLines(p, body.useTags !== false, body.commander ? str(body.commander) : null);
        if (lines.length === 0) throw new EditError("None of the cards were found on Scryfall.");
        const id = await editSync(a, (f, now) => createDeck(f, str(body.name), lines, "Pasted decklist", identity, now, NO_LOOKUPS));
        return json({ id });
      }
      case "from-precon": {
        const p = await preconLines(str(body.fileName, 300));
        if (p.lines.length === 0) throw new EditError("None of the deck's cards were found on Scryfall.");
        const id = await editSync(a, (f, now) => createDeck(f, str(body.name) || p.name, p.lines, p.origin, p.identity, now, NO_LOOKUPS));
        return json({ id });
      }
      default:
        throw new EditError("Unknown action.");
    }
  } catch (e) {
    return errorResponse(e);
  }
}
