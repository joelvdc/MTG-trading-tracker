// Editing local decks in the sync file (the app's DeckEditor and DeckEdits): quantities, categories, the commander,
// the maybeboard, adding cards, and making, copying, detaching, renaming and deleting decks. Only local decks
// (archidektId below zero) are edited, as in the app since 1.78; decks from Archidekt are edited there.
//
// Sync (Sync.kt DeckMerge): a deck's decklist comes from the copy with the newest (archidektUpdatedAt, importedAt,
// updatedAt); a local deck has no archidektUpdatedAt, so an edit wins by moving its updatedAt on, like the app's
// database triggers do. Its lines are written whole; each keeps its lineUid.
import { primaryType, type Deck, type DeckCard, type SyncDeck } from "./deckshared";
import { addDeletion, bump, prefs, setPrefs, type CardRef, type Json, type SyncFile } from "./syncfile";
import { EditError } from "./wishlist";

export const MAYBEBOARD = "Maybeboard";
export const COMMANDER = "Commander";
/** Stands for "each card's auto category" when moving cards (the app's DeckEditor.AUTO). */
export const AUTO = "\u0000auto";
const MAX_QUANTITY = 999;
const COLOURS = "WUBRG";

const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();

function syncDecks(file: SyncFile): SyncDeck[] {
  return ((file.data.decks as Json[] | undefined) ?? []) as unknown as SyncDeck[];
}

/** A deck's category as the app keeps it in categoryInfo (ArchidektCategory; defaults left out). */
export interface Category {
  name: string;
  included?: boolean;
  id?: number | null;
}

export function categories(info: string | null | undefined): Category[] {
  if (!info) return [];
  try {
    const j = JSON.parse(info);
    return Array.isArray(j) ? j.filter((c) => c && typeof c.name === "string") : [];
  } catch {
    return [];
  }
}

export function encodeCategories(list: Category[]): string | null {
  if (list.length === 0) return null;
  return JSON.stringify(
    list.map((c) => {
      const o: Category = { name: c.name };
      if (c.included === false) o.included = false;
      if (c.id != null) o.id = c.id;
      return o;
    }),
  );
}

/** Whether a card whose first category is [category] counts in the deck (DeckEdits.included). */
export function included(category: string, info: Category[]): boolean {
  return !same(category, MAYBEBOARD) && info.find((c) => same(c.name, category))?.included !== false;
}

/** The category of a card without an auto category: its card type (DeckEditor.fallbackCategory). */
export const fallbackCategory = (types: string) => primaryType(types);

/** Every category of a line, [category] first (DeckCard.categoryList). */
export function categoryList(c: DeckCard): string[] {
  const l = (c.categories ?? "").split("\n").filter((x) => x.trim());
  return l.length ? l : [c.category];
}

/** The line moved to [to] as its first category, keeping its other ones (DeckCard.inCategory). */
function inCategory(c: DeckCard, to: string, maybe: boolean): DeckCard {
  const rest = categoryList(c)
    .slice(1)
    .filter((x) => !same(x, to) && !same(x, MAYBEBOARD));
  return { ...c, category: to, categories: [to, ...rest].join("\n"), maybeboard: maybe };
}

/** Kotlin's compareTo for strings and booleans, for SyncDeck.sorted. */
const cmp = (a: string | number | boolean, b: string | number | boolean) => (a === b ? 0 : a < b ? -1 : 1);

/** The order the app writes a deck's lines in (SyncDeck.sorted), which is also the order it reads them back. */
export function sortLines(lines: DeckCard[]): DeckCard[] {
  return [...lines].sort(
    (a, b) =>
      cmp(a.card.scryfallId, b.card.scryfallId) ||
      cmp(a.foil, b.foil) ||
      cmp(a.etched, b.etched) ||
      cmp(a.addedInApp, b.addedInApp) ||
      cmp(a.category, b.category) ||
      cmp(a.quantity, b.quantity) ||
      cmp(a.commander, b.commander),
  );
}

/**
 * What an edit needs from outside: commanders' colour identity (null when unknown) and auto categories by Scryfall id
 * (Archidekt's, else Scryfall's tags: lib/autocategories.ts; cards without one go by card type).
 */
export interface Lookups {
  identity: (scryfallIds: string[]) => string | null;
  auto: Map<string, { cardId: number | null; category: string | null; extra?: string[] }>;
}

/**
 * Where a card goes "in its auto category": its first auto category (not [skip], the category being deleted) and its
 * other ones as extra categories; by card type when it has none.
 */
export function autoFor(look: Lookups, scryfallId: string, types: string, skip: string | null = null): { to: string; extra: string[] } {
  const a = look.auto.get(scryfallId);
  const all = [a?.category, ...(a?.extra ?? [])].filter((x): x is string => !!x && !(skip !== null && same(x, skip)));
  return all.length ? { to: all[0], extra: all.slice(1) } : { to: fallbackCategory(types), extra: [] };
}

export const NO_LOOKUPS: Lookups = { identity: () => null, auto: new Map() };

/** The colours of [identities] (e.g. ["G","B"], "BG") in WUBRG order. */
export function inOrder(identities: Iterable<string>): string {
  const all = new Set([...identities].join(""));
  return [...COLOURS].filter((c) => all.has(c)).join("");
}

function find(file: SyncFile, id: number): SyncDeck {
  const sd = syncDecks(file).find((d) => d.deck.archidektId === id);
  if (!sd) throw new EditError("That deck isn't in the sync file (any more).");
  return sd;
}

function local(file: SyncFile, id: number): SyncDeck {
  const sd = find(file, id);
  if (sd.deck.archidektId >= 0) throw new EditError("Decks from Archidekt are edited on Archidekt. Make it a local deck first.");
  return sd;
}

/** Keeps the deck's card count and commander(s) in step with its lines (DeckEditor.refreshSummary). */
function refreshSummary(deck: Deck, lines: DeckCard[], look: Lookups): void {
  const main = lines.filter((c) => !c.maybeboard);
  const commanders = main.filter((c) => c.commander);
  const names = [...new Set(commanders.map((c) => c.card.name))].join(" & ");
  deck.cardCount = main.reduce((s, c) => s + c.quantity, 0);
  if (names !== deck.commanders) {
    deck.colorIdentity = commanders.length === 0 ? "" : (look.identity(commanders.map((c) => c.card.scryfallId)) ?? deck.colorIdentity);
  }
  deck.commanders = names;
  deck.commanderScryfallId = commanders[0]?.card.scryfallId ?? null;
}

/** Replaces the deck's lines (DeckEditor.change): sorted like the app, summary refreshed, updatedAt moved on. */
function save(sd: SyncDeck, lines: DeckCard[], now: number, look: Lookups): void {
  sd.cards = sortLines(lines.map((c) => ({ ...c, id: 0, deckId: sd.deck.archidektId })));
  refreshSummary(sd.deck, sd.cards, look);
  sd.deck.updatedAt = bump(sd.deck.updatedAt ?? 0, now);
}

function touch(sd: SyncDeck, now: number): void {
  sd.deck.updatedAt = bump(sd.deck.updatedAt ?? 0, now);
}

// ---- Line edits ----------------------------------------------------------------------------------------------------

export function setQuantity(file: SyncFile, id: number, uid: string, quantity: number, now: number, look: Lookups): void {
  const sd = local(file, id);
  const q = Math.min(MAX_QUANTITY, Math.floor(quantity));
  if (!sd.cards.some((c) => c.lineUid === uid)) throw new EditError("That card isn't in the deck any more.");
  save(sd, q <= 0 ? sd.cards.filter((c) => c.lineUid !== uid) : sd.cards.map((c) => (c.lineUid === uid ? { ...c, quantity: q } : c)), now, look);
}

export function removeLines(file: SyncFile, id: number, uids: string[], now: number, look: Lookups): void {
  const sd = local(file, id);
  const gone = new Set(uids);
  save(sd, sd.cards.filter((c) => !gone.has(c.lineUid)), now, look);
}

function moved(sd: SyncDeck, uids: Set<string>, category: string, look: Lookups, skip: string | null = null): DeckCard[] {
  const info = categories(sd.deck.categoryInfo);
  return sd.cards.map((c) => {
    if (!uids.has(c.lineUid)) return c;
    const auto = category === AUTO ? autoFor(look, c.card.scryfallId, c.types, skip) : null;
    const to = auto ? auto.to : category;
    const line = inCategory(c, to, !included(to, info));
    if (auto?.extra.length) {
      // Its other auto categories go along (after the first), as on Archidekt.
      const list = categoryList(line);
      const merged = [list[0], ...auto.extra, ...list.slice(1)].filter((x, i, all) => !(skip !== null && same(x, skip)) && all.findIndex((y) => same(x, y)) === i);
      line.categories = merged.join("\n");
    }
    return { ...line, commander: same(to, COMMANDER), addedInApp: false };
  });
}

/**
 * Moves lines to [category] (DeckEditor.move): [AUTO] for each card's auto category, [MAYBEBOARD] (or another
 * category left out of the deck) to the maybeboard, [COMMANDER] to make them the commander.
 */
export function moveLines(file: SyncFile, id: number, uids: string[], category: string, now: number, look: Lookups): void {
  const sd = local(file, id);
  const to = category === AUTO ? AUTO : category.trim();
  if (!to) throw new EditError("Give the category a name.");
  save(sd, moved(sd, new Set(uids), to, look), now, look);
}

// ---- Categories ----------------------------------------------------------------------------------------------------

function hidden(file: SyncFile): Record<string, Record<string, string>> {
  try {
    const j = JSON.parse(prefs(file).deckHidden ?? "{}");
    return j && typeof j === "object" ? j : {};
  } catch {
    return {};
  }
}

function setHidden(file: SyncFile, change: (h: Record<string, Record<string, string>>) => boolean, now: number): void {
  const h = hidden(file);
  if (!change(h)) return;
  for (const k of Object.keys(h)) if (Object.keys(h[k]).length === 0) delete h[k];
  setPrefs(file, { deckHidden: JSON.stringify(h) }, now);
}

/** Shows or hides a category on the deck page (Settings.setDeckHidden; synced). Works for every deck. */
export function setCategoryHidden(file: SyncFile, id: number, category: string, hide: boolean, now: number): void {
  find(file, id);
  const key = category.toLowerCase();
  setHidden(
    file,
    (h) => {
      const forDeck = { ...(h[String(id)] ?? {}) };
      if (hide === key in forDeck) return false;
      if (hide) forDeck[key] = category;
      else delete forDeck[key];
      h[String(id)] = forDeck;
      return true;
    },
    now,
  );
}

/** Renames a category; its cards move along and it stays hidden if it was (DeckEditor.renameCategory). */
export function renameCategory(file: SyncFile, id: number, from: string, toRaw: string, now: number, look: Lookups): void {
  const sd = local(file, id);
  const to = toRaw.trim();
  if (!to) throw new EditError("Give the category a name.");
  if (same(to, COMMANDER) || same(from, COMMANDER)) throw new EditError("The Commander category can't be renamed.");
  const info = categories(sd.deck.categoryInfo);
  const old = info.find((c) => same(c.name, from));
  const kept = info.filter((c) => !same(c.name, from) && !same(c.name, to));
  const now2 = [...kept, { name: to, included: old?.included ?? true, id: old?.id ?? null }];
  sd.deck.categoryInfo = encodeCategories(now2);
  if (hidden(file)[String(id)]?.[from.toLowerCase()] !== undefined) {
    setCategoryHidden(file, id, from, false, now);
    setCategoryHidden(file, id, to, true, now);
  }
  const lines = sd.cards.map((c) => (same(c.category, from) ? inCategory(c, to, !included(to, now2)) : c));
  save(sd, lines, now, look);
}

/** Deletes a category: its cards go to [moveTo], or to their auto categories (DeckEditor.deleteCategory). */
export function deleteCategory(file: SyncFile, id: number, name: string, moveTo: string | null, now: number, look: Lookups): void {
  const sd = local(file, id);
  const uids = new Set(sd.cards.filter((c) => same(c.category, name)).map((c) => c.lineUid));
  // To their auto categories: the next one when the deleted category was one of them (Cultivate: Ramp deleted, Tutor).
  const lines = moved(sd, uids, moveTo?.trim() ? moveTo.trim() : AUTO, look, name);
  sd.deck.categoryInfo = encodeCategories(categories(sd.deck.categoryInfo).filter((c) => !same(c.name, name)));
  setCategoryHidden(file, id, name, false, now);
  save(sd, lines, now, look);
}

/** A new, empty category (DeckEditor.addCategory). */
export function addCategory(file: SyncFile, id: number, raw: string, now: number): void {
  const sd = local(file, id);
  const name = raw.trim();
  if (!name) throw new EditError("Give the category a name.");
  const info = categories(sd.deck.categoryInfo);
  if (info.some((c) => same(c.name, name)) || same(name, COMMANDER) || same(name, MAYBEBOARD)) return;
  sd.deck.categoryInfo = encodeCategories([...info, { name }]);
  touch(sd, now);
}

// ---- Adding cards --------------------------------------------------------------------------------------------------

/** What Scryfall says about a card being added: its type line and mana value. */
export interface CardFacts {
  typeLine: string;
  cmc: number;
}

/**
 * Adds a card (DeckEditor.addCard): next to the same printing and finish if it's in the deck already, else into
 * [category], its auto category or the category of its card type. Returns the line's uid.
 */
export function addCard(
  file: SyncFile,
  id: number,
  card: CardRef,
  foil: boolean,
  etched: boolean,
  quantity: number,
  category: string | null,
  facts: CardFacts | null,
  now: number,
  look: Lookups,
): string {
  const sd = local(file, id);
  const q = Math.max(1, Math.min(MAX_QUANTITY, Math.floor(quantity)));
  const there = sd.cards.find((c) => !c.maybeboard && c.card.scryfallId === card.scryfallId && c.foil === foil && c.etched === etched && !c.addedInApp);
  if (there && !category) {
    save(sd, sd.cards.map((c) => (c === there ? { ...c, quantity: Math.min(MAX_QUANTITY, c.quantity + q) } : c)), now, look);
    return there.lineUid;
  }
  const types = (facts?.typeLine ?? "").split(" — ")[0];
  const auto = category?.trim() ? null : autoFor(look, card.scryfallId, types);
  const to = category?.trim() || auto!.to;
  const line: DeckCard = {
    id: 0,
    deckId: id,
    card,
    quantity: q,
    category: to,
    types,
    cmc: facts?.cmc ?? 0,
    commander: same(to, COMMANDER),
    foil,
    etched,
    gameChanger: false,
    addedInApp: false,
    maybeboard: !included(to, categories(sd.deck.categoryInfo)),
    categories: auto?.extra.length ? [to, ...auto.extra].join("\n") : "",
    archidektCardId: look.auto.get(card.scryfallId)?.cardId ?? null,
    relationId: null,
    label: null,
    lineUid: crypto.randomUUID(),
  };
  save(sd, [...sd.cards, line], now, look);
  return line.lineUid;
}

/** The printing you own to show for a card (Settings.setDeckPick; "scryfallId|FINISH", "archidekt" or null). Every deck. */
export function setPick(file: SyncFile, id: number, name: string, value: string | null, now: number): void {
  find(file, id);
  const key = name.split(" // ")[0].trim().toLowerCase();
  let all: Record<string, Record<string, string>> = {};
  try {
    const j = JSON.parse(prefs(file).deckPicks ?? "{}");
    if (j && typeof j === "object") all = j;
  } catch {
    all = {};
  }
  const forDeck = { ...(all[String(id)] ?? {}) };
  if (value === null) delete forDeck[key];
  else forDeck[key] = value;
  if (Object.keys(forDeck).length === 0) delete all[String(id)];
  else all[String(id)] = forDeck;
  setPrefs(file, { deckPicks: JSON.stringify(all) }, now);
}

/** The printing to show for one of the deck's tokens (Settings.setDeckTokenPick); null for the cards' own. */
export function setTokenPick(file: SyncFile, id: number, key: string, value: string | null, now: number): void {
  find(file, id);
  if (!key) throw new EditError("Which token?");
  let all: Record<string, Record<string, string>> = {};
  try {
    const j = JSON.parse(prefs(file).deckTokenPicks ?? "{}");
    if (j && typeof j === "object") all = j;
  } catch {
    all = {};
  }
  const forDeck = { ...(all[String(id)] ?? {}) };
  if (value === null) delete forDeck[key];
  else forDeck[key] = value;
  if (Object.keys(forDeck).length === 0) delete all[String(id)];
  else all[String(id)] = forDeck;
  setPrefs(file, { deckTokenPicks: JSON.stringify(all) }, now);
}

// ---- Decks ---------------------------------------------------------------------------------------------------------

export function renameDeck(file: SyncFile, id: number, raw: string, now: number): void {
  const sd = local(file, id);
  const name = raw.trim();
  if (!name) throw new EditError("Give the deck a name.");
  sd.deck.name = name.slice(0, 200);
  touch(sd, now);
}

/**
 * Deletes a deck for good (DeckRepository.delete: local decks and decks from Archidekt alike, which stay on Archidekt);
 * the phones drop it at their next sync.
 */
export function deleteDeck(file: SyncFile, id: number, now: number): void {
  find(file, id);
  file.data.decks = syncDecks(file).filter((d) => d.deck.archidektId !== id) as unknown as Json;
  addDeletion(file, `deck:${id}`, now);
}

/** A deck with the app's defaults for everything not given. */
export function newDeck(id: number, now: number, d: Partial<Deck>): Deck {
  return {
    archidektId: id,
    name: "New deck",
    owner: "",
    commanders: "",
    commanderScryfallId: null,
    artUrl: null,
    colorIdentity: "",
    cardCount: 0,
    importedAt: now,
    saltId: null,
    powerLevel: null,
    bracketRealistic: null,
    bracketBaseline: null,
    saltPercent: null,
    archetype: null,
    scoredAt: null,
    scoreError: null,
    archidektUpdatedAt: null,
    updatedAt: now,
    edhPowerLevel: null,
    edhPowerAt: null,
    edhPowerError: null,
    saltCard: null,
    svPowerLevel: null,
    scrollVault: null,
    svAt: null,
    svError: null,
    folderId: null,
    folderName: null,
    visibility: null,
    categoryInfo: null,
    origin: null,
    ...d,
  };
}

/** A new local deck's id: below zero and unused (DeckEdits.newLocalId). */
function newLocalId(file: SyncFile, now: number): number {
  const used = new Set(syncDecks(file).map((d) => d.deck.archidektId));
  let id = -now;
  while (used.has(id)) id--;
  return id;
}

export function addDeck(file: SyncFile, sd: SyncDeck): void {
  const list = [...syncDecks(file), sd].sort((a, b) => a.deck.archidektId - b.deck.archidektId);
  file.data.decks = list as unknown as Json;
}

/** A line for a new deck (DeckEditor.lineFor and the copies in createLocal). */
export type NewLine = Omit<DeckCard, "id" | "deckId" | "lineUid" | "relationId"> & { lineUid?: string };

/** Saves a new local deck with [lines] (DeckEditor.createLocal); returns its id. */
export function createDeck(
  file: SyncFile,
  name: string,
  lines: NewLine[],
  origin: string | null,
  identity: string | null,
  now: number,
  look: Lookups,
  extra: Partial<Deck> = {},
  keepUids = false,
): number {
  const id = newLocalId(file, now);
  const cards = sortLines(
    lines.map((l) => ({ ...l, id: 0, deckId: id, relationId: null, lineUid: keepUids && l.lineUid ? l.lineUid : crypto.randomUUID() }) as DeckCard),
  );
  const main = cards.filter((c) => !c.maybeboard);
  const commanders = main.filter((c) => c.commander);
  const deck = newDeck(id, now, {
    name: name.trim().slice(0, 200) || "New deck",
    commanders: [...new Set(commanders.map((c) => c.card.name))].join(" & "),
    commanderScryfallId: commanders[0]?.card.scryfallId ?? null,
    colorIdentity: identity ?? (commanders.length ? (look.identity(commanders.map((c) => c.card.scryfallId)) ?? "") : ""),
    cardCount: main.reduce((s, c) => s + c.quantity, 0),
    origin,
    ...extra,
  });
  addDeck(file, { deck, cards });
  return id;
}

export function newEmptyDeck(file: SyncFile, name: string, now: number): number {
  return createDeck(file, name, [], "Made on the website", "", now, NO_LOOKUPS);
}

/** A local copy of any deck: its cards, categories and maybeboard, not its scores (DeckEditor.copyOf). */
export function copyDeck(file: SyncFile, id: number, name: string, now: number): number {
  const src = find(file, id);
  return createDeck(file, name, src.cards, `Copy of ${src.deck.name}`, src.deck.colorIdentity, now, NO_LOOKUPS, {
    artUrl: src.deck.artUrl,
    categoryInfo: src.deck.categoryInfo,
  });
}

/** Turns a deck from Archidekt into a local deck, no longer linked or reloaded (DeckEditor.detach); returns its new id. */
export function detachDeck(file: SyncFile, id: number, now: number): number {
  const src = find(file, id);
  if (src.deck.archidektId < 0) return id;
  const newId = newLocalId(file, now);
  const deck: Deck = {
    ...src.deck,
    archidektId: newId,
    owner: "",
    origin: `Detached from Archidekt (${src.deck.owner.trim() ? src.deck.owner : `deck ${src.deck.archidektId}`})`,
    archidektUpdatedAt: null,
    folderId: null,
    folderName: null,
    visibility: null,
    updatedAt: bump(src.deck.updatedAt ?? 0, now),
  };
  const cards = sortLines(
    src.cards.map((c) => ({ ...c, id: 0, deckId: newId, relationId: null, addedInApp: false, lineUid: c.lineUid || crypto.randomUUID() })),
  );
  file.data.decks = syncDecks(file).filter((d) => d.deck.archidektId !== id) as unknown as Json;
  addDeck(file, { deck, cards });
  addDeletion(file, `deck:${id}`, now);
  const h = hidden(file);
  if (h[String(id)]) {
    setHidden(
      file,
      (x) => {
        x[String(newId)] = x[String(id)];
        delete x[String(id)];
        return true;
      },
      now,
    );
  }
  return newId;
}
