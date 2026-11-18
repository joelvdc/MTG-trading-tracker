import { archidektId, fetchDeck } from "@/lib/archidekt";
import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { compareDecks, type CompareLine } from "@/lib/deckcompare";
import { previewText } from "@/lib/deckimport";
import { ownedByName, syncDecks } from "@/lib/decks";
import { caseless } from "@/lib/deckshared";
import type { SyncFile } from "@/lib/syncfile";
import { loadSync } from "@/lib/store";
import { EditError } from "@/lib/wishlist";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

type Params = { params: Promise<{ id: string }> };

/** A text field of the body, cut to [max] characters; anything else is "". */
const str = (v: unknown, max = 200) => (typeof v === "string" ? v.slice(0, max) : "");

/** A deck line as the comparison reads it: the main deck only (no maybeboard), card by card. */
const linesOf = (cards: { card: { name: string }; quantity: number; types: string; commander: boolean; maybeboard?: boolean }[]): CompareLine[] =>
  cards.filter((c) => !c.maybeboard).map((c) => ({ name: c.card.name, quantity: c.quantity, types: c.types, commander: c.commander }));

/** The body's shape, as the page sends it. */
type Body = { source?: unknown; link?: unknown; deckId?: unknown; text?: unknown };

/**
 * What the comparison needs from the sync file: this deck's main deck, the collection's counts by name, and the file
 * itself (to read another deck from it).
 */
interface Start {
  id: number;
  mine: CompareLine[];
  /** Cards you own by lower-case name (ownedByName). */
  owned: Map<string, number>;
  file: SyncFile;
}

/**
 * Part of the comparison's result that comes from reading the other deck: its name and owner, the pasted list's
 * lines Scryfall didn't know, and its lines the website couldn't read.
 */
interface Other {
  otherName: string;
  otherOwner: string | null;
  notFound: string[];
  unreadable: string[];
}

/** The login check, the file and this deck's lines: both handlers start here. */
async function start(params: Params): Promise<{ error: ReturnType<typeof json> } | Start> {
  const a = await currentAccount();
  if (!a) return { error: json({ error: "Log in first.", loggedOut: true }, 401) };
  const id = Number((await params.params).id);
  const { file } = await loadSync(a);
  const sd = syncDecks(file).find((d) => d.deck.archidektId === id);
  if (!sd) return { error: json({ error: "That deck isn't in the sync file (any more)." }, 404) };
  return { id, mine: linesOf(sd.cards), owned: ownedByName(file), file };
}

/** The other decks in the sync file (not this one), by name: what the page offers to compare with. */
export async function GET(_req: Request, params: Params) {
  try {
    const s = await start(params);
    if ("error" in s) return s.error;
    const decks = syncDecks(s.file)
      .filter((d) => d.deck.archidektId !== s.id)
      .map((d) => ({ id: d.deck.archidektId, name: d.deck.name }))
      .sort((x, y) => caseless(x.name, y.name));
    return json({ decks });
  } catch (e) {
    return errorResponse(e);
  }
}

/** Reads the deck to compare with from Archidekt (any public link) or a pasted list; a sync-file deck needs no reading. */
async function readOther(body: Body, s: Start): Promise<{ lines: CompareLine[]; other: Other }> {
  if (body.source === "archidekt") {
    const id = archidektId(str(body.link, 500));
    if (!id) throw new EditError("That isn't a link to an Archidekt deck.");
    const deck = await fetchDeck(id);
    // Archidekt's answer already leaves the maybeboard out.
    const lines = deck.cards.map((c) => ({ name: c.name, quantity: c.quantity, types: c.types, commander: c.commander }));
    if (lines.length === 0) throw new EditError("That deck has no cards.");
    return { lines, other: { otherName: deck.name, otherOwner: deck.owner || null, notFound: [], unreadable: [] } };
  }

  if (body.source === "deck") {
    const otherId = typeof body.deckId === "number" ? body.deckId : NaN;
    if (otherId === s.id) throw new EditError("Pick another deck to compare with.");
    const sd = syncDecks(s.file).find((d) => d.deck.archidektId === otherId);
    if (!sd) throw new EditError("That deck isn't in the sync file (any more).");
    return { lines: linesOf(sd.cards), other: { otherName: sd.deck.name, otherOwner: null, notFound: [], unreadable: [] } };
  }

  if (body.source === "text") {
    let p;
    try {
      p = await previewText(str(body.text, 60_000));
    } catch (e) {
      if (e instanceof EditError) throw e;
      throw new EditError("Scryfall couldn't be reached.");
    }
    if (p.result.lines.length === 0) throw new EditError("No cards in that list.");
    const lines: CompareLine[] = [];
    const notFound: string[] = [];
    p.result.lines.forEach((line, i) => {
      // The maybeboard and the sideboard aren't part of the deck being compared.
      if (line.board === "MAYBE") return;
      const found = p.found[i];
      if (found) {
        lines.push({ name: found.card.name, quantity: line.quantity, types: found.typeLine.split(" — ")[0], commander: line.board === "COMMANDER" });
      } else if (!notFound.includes(line.name)) {
        // Each missing card once; two lines of it are one card.
        notFound.push(line.name);
      }
    });
    if (lines.length === 0) throw new EditError("None of the cards were found on Scryfall.");
    return { lines, other: { otherName: "Pasted list", otherOwner: null, notFound, unreadable: p.result.unreadable } };
  }

  throw new EditError("Pick a deck to compare with.");
}

/** Compares this deck with another deck, an Archidekt link or a pasted list. Body: {source, link|deckId|text}. */
export async function POST(req: Request, params: Params) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  try {
    const s = await start(params);
    if ("error" in s) return s.error;
    const body = ((await req.json()) ?? {}) as Body;
    const { lines, other } = await readOther(body, s);
    return json({ ...compareDecks(s.mine, lines, s.owned), ...other });
  } catch (e) {
    return errorResponse(e);
  }
}
