import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { syncDecks } from "@/lib/decks";
import { findFromDeck, findFromText, findInFile, findNameKey, type FindGroup, type FindResult, type FindWanted } from "@/lib/findcards";
import { loadSync } from "@/lib/store";
import type { SyncFile } from "@/lib/syncfile";

export const dynamic = "force-dynamic";

export type FindView = FindResult & { fromDeck: number | null; deckName: string | null };

/** Keeps only the wanted copies of [names] (the deck page's selected cards). */
function onlyNames(wanted: FindWanted[], names: string[]): FindWanted[] {
  const keep = new Set(names.map((n) => findNameKey(n)));
  return wanted.filter((w) => keep.has(findNameKey(w.name)));
}

/** The result with only the named lines of each binder (a group left empty is dropped). */
function trimmed(result: FindResult, names: string[]): FindResult {
  const keep = new Set(names.map((n) => findNameKey(n)));
  const groups: FindGroup[] = result.groups
    .map((g) => ({ ...g, lines: g.lines.filter((l) => keep.has(findNameKey(l.name))) }))
    .filter((g) => g.lines.length > 0);
  const onlyInDecks = result.onlyInDecks.filter((l) => keep.has(findNameKey(l.name)));
  const notOwned = result.notOwned.filter((l) => keep.has(findNameKey(l.name)));
  return {
    groups,
    onlyInDecks,
    notOwned,
    total: {
      wanted: groups.reduce((n, g) => n + g.lines.reduce((m, l) => m + l.copies, 0), 0) + onlyInDecks.reduce((n, l) => n + l.copies, 0) + notOwned.reduce((n, l) => n + l.copies, 0),
      found: groups.reduce((n, g) => n + g.lines.reduce((m, l) => m + l.copies, 0), 0),
      inDecks: onlyInDecks.reduce((n, l) => n + l.copies, 0),
      notOwned: notOwned.reduce((n, l) => n + l.copies, 0),
    },
  };
}

/**
 * The result for a pasted list ([text]) or a deck ([deck], its own binders' use not counted against it), optionally
 * only for the named cards of that deck ([names]). Read only: it never writes the sync file.
 */
function findView(file: SyncFile, body: { text?: string; deck?: number; names?: string[]; skipBasics: boolean }): FindView | { error: string } {
  const skipBasics = body.skipBasics !== false;
  if (body.deck !== undefined) {
    const wanted = findFromDeck(file, body.deck);
    if (!wanted) return { error: "That deck isn't in the sync file (any more)." };
    const name = deckNameOf(file, body.deck);
    if (body.names && body.names.length > 0) return { ...trimmed(findInFile(file, onlyNames(wanted, body.names), body.deck, skipBasics), body.names), fromDeck: body.deck, deckName: name };
    return { ...findInFile(file, wanted, body.deck, skipBasics), fromDeck: body.deck, deckName: name };
  }
  const text = body.text ?? "";
  if (body.names && body.names.length > 0) return { ...trimmed(findInFile(file, findFromText(text), null, skipBasics), body.names), fromDeck: null, deckName: null };
  return { ...findInFile(file, text, null, skipBasics), fromDeck: null, deckName: null };
}

function deckNameOf(file: SyncFile, id: number): string | null {
  return syncDecks(file).find((d) => d.deck?.archidektId === id)?.deck?.name ?? null;
}

const isText = (v: unknown): v is string => typeof v === "string" && v.length <= 200_000;

/** The names as the deck page sends them: a list, or one "|"-joined string (the ?names= parameter). */
function namesOf(v: unknown): string[] | null {
  if (v === undefined || v === null) return [];
  const list = typeof v === "string" ? v.split("|") : Array.isArray(v) ? v : null;
  if (!list || list.length > 2000) return null;
  const out: string[] = [];
  for (const n of list) {
    if (typeof n !== "string") return null;
    const name = n.trim();
    if (name.length > 0 && name.length <= 300) out.push(name);
  }
  return out;
}

export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { text?: unknown; deck?: unknown; names?: unknown; skipBasics?: unknown };
    const names = namesOf(body.names);
    if (names === null) return json({ error: "Those card names can't be used." }, 400);
    if (body.text !== undefined && !isText(body.text)) return json({ error: "That list is too long." }, 400);
    let deck: number | undefined;
    if (body.deck !== undefined && body.deck !== null && body.deck !== "") {
      const n = Number(body.deck);
      if (!Number.isInteger(n) || n < 0) return json({ error: "That deck can't be used." }, 400);
      deck = n;
    }
    const text = body.text as string | undefined;
    if (deck === undefined && !(text ?? "").trim()) return json({ error: "Paste a list or pick a deck." }, 400);
    const { file } = await loadSync(a);
    const view = findView(file, { text, deck, names, skipBasics: body.skipBasics !== false });
    if ("error" in view) return json({ error: view.error }, 404);
    return json(view);
  } catch (e) {
    return errorResponse(e);
  }
}
