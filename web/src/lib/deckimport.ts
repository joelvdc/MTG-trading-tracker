// New local decks from a pasted decklist or a preconstructed deck (the app's DeckEditor.previewText, fromText and
// fromPrecon): the cards are looked up on Scryfall and put in their auto category (lib/autocategories.ts: Archidekt's,
// else Scryfall's tags), or the category of their card type. Server only.
import { COMMANDER, fallbackCategory, inOrder, MAYBEBOARD, type NewLine } from "./deckedit";
import { hasTags, parseDeckText, type TextLine, type TextResult } from "./decktext";
import { deckList, deckUrl } from "./precons";
import { mtgjson, preconList } from "./preconfetch";
import { lookupCollectionFull, type LookedUp } from "./scryfall";
import { EditError } from "./wishlist";
import { autoCategories, type AutoMap } from "./autocategories";

const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();
const NO_AUTO: AutoMap = new Map();

export interface TextPreview {
  result: TextResult;
  /** The card found for each line (by index), or null. */
  found: (LookedUp | null)[];
  exact: number;
  byName: number;
  notFound: string[];
  commanders: string[];
}

const ident = (l: TextLine) => (l.setCode && l.number ? { set: l.setCode, collector_number: l.number } : { name: l.name });

/** What a pasted list holds, looked up on Scryfall (by set and number where given, else by name). */
export async function previewText(text: string): Promise<TextPreview> {
  const result = parseDeckText(text);
  if (result.lines.length > 500) throw new EditError("That list is too long (500 lines at most).");
  const keys = new Map<string, object>();
  for (const l of result.lines) keys.set(JSON.stringify(ident(l)), ident(l));
  const cards = await lookupCollectionFull([...keys.values()]);
  const bySetNumber = new Map(cards.map((c) => [`${c.card.setCode.toLowerCase()}|${c.card.collectorNumber.toLowerCase()}`, c]));
  const nameMatch = (name: string): LookedUp | undefined =>
    cards.find((c) => same(c.card.name, name)) ?? cards.find((c) => same(c.card.name.split(" // ")[0], name.split(" // ")[0]));
  let exact = 0;
  let byName = 0;
  const notFound: string[] = [];
  const found = result.lines.map((l) => {
    const bySet = l.setCode && l.number ? bySetNumber.get(`${l.setCode}|${l.number.toLowerCase()}`) : undefined;
    const c = bySet ?? nameMatch(l.name);
    if (!c) notFound.push(l.name);
    else if (bySet) exact++;
    else byName++;
    return c ?? null;
  });
  const commanders = result.lines.flatMap((l, i) => (l.board === "COMMANDER" && found[i] ? [found[i]!.card.name] : []));
  return { result, found, exact, byName, notFound, commanders };
}

function identityOf(cards: LookedUp[], commanders: Set<string>): string {
  return inOrder(cards.filter((c) => commanders.has(c.card.name)).flatMap((c) => c.colorIdentity));
}

function lineFor(sc: LookedUp, quantity: number, foil: boolean, etched: boolean, category: string, commander: boolean, cardId: number | null, extra: string[] = []): NewLine {
  return {
    card: sc.card,
    quantity,
    category,
    types: sc.typeLine.split(" — ")[0],
    cmc: sc.cmc,
    commander,
    foil,
    etched,
    gameChanger: false,
    addedInApp: false,
    maybeboard: same(category, MAYBEBOARD),
    categories: [category, ...extra.filter((x) => !same(x, category))].join("\n"),
    archidektCardId: cardId,
    label: null,
  };
}

/** The lines for a pasted list; categories from its tags ([useTags]) or the card types. */
export async function textLines(preview: TextPreview, useTags: boolean, commanderOverride: string | null) {
  const names = new Set(commanderOverride ? [commanderOverride] : preview.commanders);
  const tags = useTags && hasTags(preview.result);
  // Cards without a category of their own go into their auto category (like the app's DeckEditor.importText).
  const auto = tags ? NO_AUTO : await autoCategories(preview.found.flatMap((c) => (c ? [{ scryfallId: c.card.scryfallId, name: c.card.name }] : [])));
  const lines = preview.result.lines.flatMap((l, i): NewLine[] => {
    const sc = preview.found[i];
    if (!sc) return [];
    const commander = names.has(sc.card.name);
    const category = commander
      ? COMMANDER
      : l.board === "MAYBE"
        ? MAYBEBOARD
        : tags && l.tags.length
          ? l.tags[0]
          : (auto.get(sc.card.scryfallId)?.category ?? fallbackCategory(sc.typeLine.split(" — ")[0]));
    return [lineFor(sc, l.quantity, l.foil, l.etched, category, commander, auto.get(sc.card.scryfallId)?.cardId ?? null, tags ? l.tags.slice(1) : [])];
  });
  const found = preview.found.filter((c): c is LookedUp => c !== null);
  return { lines, identity: identityOf(found, names) };
}

/** What a precon deck becomes: its name, origin and lines (with basic lands). */
export async function preconLines(fileName: string) {
  const info = (await preconList()).find((d) => d.fileName === fileName);
  if (!info) throw new EditError("That deck isn't in MTGJSON's list.");
  const deck = deckList(await mtgjson(deckUrl(fileName)));
  const found = new Map((await lookupCollectionFull(deck.cards.map((c) => ({ id: c.scryfallId! })))).map((c) => [c.card.scryfallId, c]));
  const auto = await autoCategories([...found.values()].map((c) => ({ scryfallId: c.card.scryfallId, name: c.card.name })));
  const commanders = new Set(deck.commanders);
  const lines = deck.cards.flatMap((c): NewLine[] => {
    const sc = found.get(c.scryfallId!);
    if (!sc) return [];
    const commander = commanders.has(c.name);
    const category = commander ? COMMANDER : (auto.get(sc.card.scryfallId)?.category ?? fallbackCategory(sc.typeLine.split(" — ")[0]));
    return [lineFor(sc, c.count, c.isFoil, false, category, commander, auto.get(sc.card.scryfallId)?.cardId ?? null)];
  });
  const name = deck.name || info.name;
  const origin = `Precon: ${name}` + (deck.releaseDate ? ` (${deck.releaseDate.slice(0, 4)})` : "");
  return { name, origin, lines, identity: identityOf([...found.values()], commanders) };
}
