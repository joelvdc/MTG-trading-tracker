import { after } from "next/server";
import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { createDeck, NO_LOOKUPS, newEmptyDeck } from "@/lib/deckedit";
import { preconLines, previewText, textLines } from "@/lib/deckimport";
import { textCardCount } from "@/lib/decktext";
import { decksView, syncDecks } from "@/lib/decks";
import { archidektId, archidektUser, userDecks } from "@/lib/archidekt";
import { archidektDates, loadFromArchidekt, updatePlan } from "@/lib/deckload";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { EditError } from "@/lib/wishlist";
import { isUser } from "@/lib/session";
import { keptWins, parseWin, runWinTurns } from "@/lib/winturns";

export const dynamic = "force-dynamic";
// The win turns of changed decks are worked out after the answer, for up to about four and a half minutes.
export const maxDuration = 300;

/**
 * The decks list (the app's Decks tab). With "This app" as the power level source, each deck's power comes from the
 * win turn the website kept for it. Afterwards the website works out the win turns of decks whose list changed
 * (lib/winturns.ts; one run per account at a time, the daily job does the rest).
 */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    const view = decksView(file, prices, details, ck);
    if (isUser(a)) {
      const kept = await keptWins(a.accountId);
      if (view.powerSource === "APP") for (const d of view.decks) d.power = parseWin(kept.get(d.id))?.power.power ?? null;
      const started = Date.now();
      after(() => runWinTurns(a.accountId, started + 270_000).catch((e) => console.warn(`Win turns: ${e instanceof Error ? e.message : e}`)));
    }
    return json(view);
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
  username?: string;
  link?: string;
  local?: boolean;
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
      // Archidekt (as the app's Decks tab): a user's public decks to pick from, importing one, and the decks
      // "Update all decks" goes through (one call each from the page).
      case "archidekt-decks": {
        const user = archidektUser(str(body.username));
        if (!user) throw new EditError("That isn't an Archidekt username or profile link.");
        const [list, { file }] = await Promise.all([userDecks(user), loadSync(a)]);
        const have = new Set(syncDecks(file).map((d) => d.deck.archidektId));
        return json({ username: user, decks: list.map((d) => ({ ...d, imported: have.has(d.id) })) });
      }
      case "archidekt-import": {
        const id = archidektId(str(body.link, 500));
        if (id === null) throw new EditError("That isn't a link to an Archidekt deck.");
        return json(await loadFromArchidekt(a, id, { score: true, local: body.local === true }));
      }
      case "archidekt-check": {
        const { file } = await loadSync(a);
        return json({ decks: updatePlan(file, await archidektDates(file)) });
      }
      default:
        throw new EditError("Unknown action.");
    }
  } catch (e) {
    return errorResponse(e);
  }
}
