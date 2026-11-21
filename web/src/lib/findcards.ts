// "Find these cards" (the app's FindCards.kt, since 1.104): where the copies of a pasted list (or a deck) are stored,
// grouped by binder so they're emptied one binder at a time, with what's only in other decks and what isn't owned at
// all. findCards is the pure core (the app's FindCards.plan, tested by findcards-cases.json); findInputs builds its
// input from the sync file the way collview.ts and tradebinder.ts read it.
import { barKeys, frontName } from "./collview";
import { parseDeckText } from "./decktext";
import { isBasicName, caseless, type SyncDeck } from "./deckshared";
import { binders as bindersOf, collection, prefs, type Json, type SyncFile } from "./syncfile";
import { nameKey, tradeBinderOf } from "./tradebinder";

/** The Unsorted key in binders and stacks (the app's BinderOrder.UNSORTED). */
export const FIND_UNSORTED = "unsorted";

export interface FindWanted {
  name: string;
  qty: number;
  scryfallId?: string | null;
}

/** A stack of copies in a binder. */
export interface FindStack {
  name: string;
  scryfallId: string;
  set: string;
  number: string;
  foil: boolean;
  etched: boolean;
  binderKey: string;
  binderName: string;
  copies: number;
}

/**
 * A binder (or Unsorted, whose key is [FIND_UNSORTED]): where it stands in the binder bar ([isDeckBinder] and
 * [isTradeBinder] for the order copies are picked in).
 */
export interface FindBinder {
  key: string;
  name: string;
  isDeckBinder: boolean;
  isTradeBinder: boolean;
}

/**
 * How copies are picked when a list needs some of them: the plain binders first, then the trade binder, then deck
 * binders, which hold cards their deck needs.
 */
export function pickOrder(b: FindBinder): number {
  if (!b.isDeckBinder && !b.isTradeBinder) return 0;
  return b.isTradeBinder ? 1 : 2;
}

/** What a deck uses (not its maybeboard): nameKey → copies. */
export interface FindDeck {
  id: number;
  name: string;
  cards: Record<string, number>;
}

export interface FindInput {
  /** One line per card; duplicates of a name are added together. */
  wanted: FindWanted[];
  stacks: FindStack[];
  /** In binder-bar order. */
  binders: FindBinder[];
  decks: FindDeck[];
  /** The deck the list came from: its own use doesn't count against the list. */
  fromDeck: number | null;
  /** Basic lands (including Wastes and Snow-Covered …) are left out. */
  skipBasics: boolean;
}

export interface FindLine {
  lineKey: string;
  name: string;
  copies: number;
  set: string;
  number: string;
  foil: boolean;
  etched: boolean;
  /** These copies are the printing the list names. */
  wantedPrinting: boolean;
}

/** The copies to pull out of one binder. */
export interface FindGroup {
  binderKey: string;
  binderName: string;
  lines: FindLine[];
}

/** Copies the list needs that other decks use. */
export interface FindInDeck {
  name: string;
  copies: number;
  decks: string[];
}

export interface FindMissing {
  name: string;
  copies: number;
}

export interface FindTotal {
  wanted: number;
  found: number;
  inDecks: number;
  notOwned: number;
}

export interface FindResult {
  /** Binders in bar order, only those with copies to find. */
  groups: FindGroup[];
  onlyInDecks: FindInDeck[];
  notOwned: FindMissing[];
  total: FindTotal;
}

/** Every line to tick off. */
export const ticksOf = (r: FindResult): Set<string> => new Set(r.groups.flatMap((g) => g.lines.map((l) => l.lineKey)));

/** A card's name for matching: the front face, lowercased and trimmed ("A // B" → "a"). */
export const findNameKey = (name: string): string => nameKey(name);

/** The key of a line: its name, printing, finish and binder. */
export const findLineKey = (name: string, scryfallId: string, foil: boolean, etched: boolean, binderKey: string) =>
  `${nameKey(name)}|${scryfallId}|${foil}|${etched}|${binderKey}`;

/** Lines naming the same card added together; the first one says which printing is wanted. */
const merged = (wanted: FindWanted[]): { w: FindWanted; printing: string | null }[] => {
  const sums = new Map<string, { w: FindWanted; printing: string | null }>();
  for (const w of wanted) {
    const key = nameKey(w.name);
    const prev = sums.get(key);
    sums.set(
      key,
      prev === undefined
        ? { w: { name: w.name, qty: w.qty, scryfallId: null }, printing: w.scryfallId ?? null }
        : { w: { ...prev.w, qty: prev.w.qty + w.qty }, printing: prev.printing ?? w.scryfallId ?? null },
    );
  }
  return [...sums.values()];
};

/** Names by Kotlin's String.CASE_INSENSITIVE_ORDER, then set, then number (the app's linesOrder). */
const linesOrder = (a: FindLine, b: FindLine) => caseless(a.name, b.name) || (a.set < b.set ? -1 : a.set > b.set ? 1 : 0) || (a.number < b.number ? -1 : a.number > b.number ? 1 : 0);

/** The pure core (FindCards.plan): which copies to pull out, binder by binder, and what's missing. */
export function findCards(input: FindInput): FindResult {
  const decks = input.decks ?? [];
  const barIndex = new Map<string, number>();
  input.binders.forEach((b, i) => {
    if (!barIndex.has(b.key)) barIndex.set(b.key, i);
  });
  const binderByKey = new Map<string, FindBinder>();
  for (const b of input.binders) if (!binderByKey.has(b.key)) binderByKey.set(b.key, b);

  interface Pick {
    stack: FindStack;
    copies: number;
    wanted: boolean;
  }

  const groups = new Map<number, FindGroup>();
  const onlyInDecks: FindInDeck[] = [];
  const notOwned: FindMissing[] = [];
  let wantedTotal = 0;
  let found = 0;
  let inDecksTotal = 0;
  let notOwnedTotal = 0;

  // Duplicates of a name are added together; the first line says which printing is wanted.
  for (const { w, printing } of merged(input.wanted)) {
    const key = nameKey(w.name);
    const name = w.name;
    const qty = w.qty;
    if (input.skipBasics && isBasicName(key)) continue;
    wantedTotal += qty;

    const ownedStacks = input.stacks.filter((s) => nameKey(s.name) === key);
    const owned = ownedStacks.reduce((n, s) => n + s.copies, 0);
    const otherUse = decks.filter((d) => d.id !== input.fromDeck).reduce((n, d) => n + (d.cards[key] ?? 0), 0);
    const available = Math.max(0, owned - otherUse);
    const take = Math.min(qty, available);

    // A printing the list names is only preferred when it's a printing we know.
    const wantedId = printing !== null && printing !== undefined && ownedStacks.some((s) => s.scryfallId === printing) ? printing : null;
    const isWanted = (s: FindStack) => wantedId !== null && s.scryfallId === wantedId;
    // The wanted printing first, then plain binders, the trade binder, deck binders; bar order; bigger first.
    const order = [...ownedStacks].sort(
      (a, b) =>
        Number(isWanted(b)) - Number(isWanted(a)) ||
        pickOrder(binderByKey.get(a.binderKey) ?? { key: a.binderKey, name: a.binderName, isDeckBinder: false, isTradeBinder: false }) -
          pickOrder(binderByKey.get(b.binderKey) ?? { key: b.binderKey, name: b.binderName, isDeckBinder: false, isTradeBinder: false }) ||
        (barIndex.get(a.binderKey) ?? Number.MAX_SAFE_INTEGER) - (barIndex.get(b.binderKey) ?? Number.MAX_SAFE_INTEGER) ||
        b.copies - a.copies,
    );
    const picks: Pick[] = [];
    let left = take;
    for (const s of order) {
      if (left <= 0) break;
      // Each stack gives all its copies: a name is worked out once, so nothing else has picked from it.
      const n = Math.min(left, s.copies);
      if (n <= 0) continue;
      picks.push({ stack: s, copies: n, wanted: isWanted(s) });
      left -= n;
    }
    found += take;

    // One line per printing and binder, in name order.
    const byBinder = new Map<string, Pick[]>();
    for (const p of picks) byBinder.set(p.stack.binderKey, [...(byBinder.get(p.stack.binderKey) ?? []), p]);
    for (const [binderKey, binderPicks] of byBinder) {
      const i = barIndex.get(binderKey) ?? Number.MAX_SAFE_INTEGER;
      const b = binderByKey.get(binderKey);
      const byPrinting = new Map<string, Pick[]>();
      for (const p of binderPicks) {
        const id = `${p.stack.scryfallId}|${p.stack.foil}|${p.stack.etched}`;
        byPrinting.set(id, [...(byPrinting.get(id) ?? []), p]);
      }
      const lines: FindLine[] = [...byPrinting.values()]
        .map((list) => {
          const s = list[0].stack;
          return {
            lineKey: findLineKey(s.name, s.scryfallId, s.foil, s.etched, binderKey),
            name: s.name,
            copies: list.reduce((n, p) => n + p.copies, 0),
            set: s.set,
            number: s.number,
            foil: s.foil,
            etched: s.etched,
            wantedPrinting: list.some((p) => p.wanted),
          };
        })
        .sort(linesOrder);
      const old = groups.get(i);
      groups.set(i, {
        binderKey,
        binderName: old?.binderName ?? b?.name ?? binderPicks[0].stack.binderName,
        lines: [...(old?.lines ?? []), ...lines].sort(linesOrder),
      });
    }

    // What's missing: the other decks' copies first, then what isn't owned at all.
    const rest = qty - take;
    const inDecks = Math.min(rest, Math.max(0, owned - take));
    if (inDecks > 0) {
      const names = [...new Set(decks.filter((d) => d.id !== input.fromDeck && (d.cards[key] ?? 0) > 0).map((d) => d.name))].sort(caseless);
      onlyInDecks.push({ name, copies: inDecks, decks: names });
      inDecksTotal += inDecks;
    }
    if (rest - inDecks > 0) {
      notOwned.push({ name, copies: rest - inDecks });
      notOwnedTotal += rest - inDecks;
    }
  }

  return {
    groups: [...groups.entries()].sort((a, b) => a[0] - b[0]).map(([, g]) => g),
    onlyInDecks,
    notOwned,
    // Wanted counts the lands left out too (as the app does); they're just not looked for.
    total: { wanted: wantedTotal, found, inDecks: inDecksTotal, notOwned: notOwnedTotal },
  };
}

// ---- The sync file -------------------------------------------------------------------------------------------------

const syncDecks = (file: SyncFile): SyncDeck[] => ((file.data.decks as Json[] | undefined) ?? []) as unknown as SyncDeck[];

/**
 * The binders in the binder bar's order, with the trade binder and deck binders marked: the trade binder is the one
 * the trade binder page works with (the remembered one, else one called "Trade binder"), and a binder named after a
 * deck (any case, a retired deck's too, as the trade binder's apply reads it) holds that deck's cards.
 */
export function findBinders(file: SyncFile): FindBinder[] {
  const all = bindersOf(file).filter((b) => b.uid);
  if (all.length === 0) return [];
  const trade = tradeBinderOf(file);
  const deckNames = new Set(syncDecks(file).map((d) => (d.deck?.name ?? "").toLowerCase()));
  const byUid = new Map(all.map((b) => [b.uid!, b]));
  // The bar as the collection page shows it: the synced order first, then Unsorted, the wishlist and the binders.
  const bar = barKeys(
    all.map((b) => ({ uid: b.uid!, name: b.name })),
    prefs(file).binderOrder,
  );
  const out: FindBinder[] = [];
  for (const key of bar) {
    // Not storage: what's on the wishlist isn't owned.
    if (key === "wishlist") continue;
    if (key === FIND_UNSORTED) {
      out.push({ key, name: "Unsorted", isDeckBinder: false, isTradeBinder: false });
      continue;
    }
    const b = key.startsWith("b:") ? byUid.get(key.slice(2)) : undefined;
    if (!b) continue;
    out.push({ key, name: b.name, isDeckBinder: deckNames.has(b.name.toLowerCase()), isTradeBinder: b.uid === trade });
  }
  return out;
}

/** Every stack of the collection with its binder's name (Unsorted included), in the bar's order of binders. */
export function findStacks(file: SyncFile, inBar: FindBinder[] = findBinders(file)): FindStack[] {
  const byKey = new Map(inBar.map((b) => [b.key, b]));
  const known = new Set(bindersOf(file).map((b) => b.uid));
  const out: FindStack[] = [];
  for (const s of collection(file)) {
    const it = s.item;
    if (!it.card) continue;
    const key = s.binder && known.has(s.binder) ? `b:${s.binder}` : FIND_UNSORTED;
    out.push({
      name: it.card.name,
      scryfallId: it.card.scryfallId,
      set: it.card.setCode ?? "",
      number: it.card.collectorNumber ?? "",
      foil: !!it.foil,
      etched: !!it.etched,
      binderKey: key,
      binderName: byKey.get(key)?.name ?? "Unsorted",
      copies: it.quantity,
    });
  }
  return out;
}

/**
 * What each deck uses (not the maybeboard, commanders included), by nameKey. Retired decks are left out, like the
 * trade binder does (their cards are spare), except the deck being looked up itself.
 */
export function findDecks(file: SyncFile, fromDeck: number | null = null): FindDeck[] {
  const out: FindDeck[] = [];
  for (const { deck, cards } of syncDecks(file)) {
    const id = deck.archidektId;
    if (deck.retired && id !== fromDeck) continue;
    const use: Record<string, number> = {};
    for (const c of cards ?? []) {
      if (c.maybeboard || !c.card?.name) continue;
      const k = frontName(c.card.name).trim();
      use[k] = (use[k] ?? 0) + (c.quantity ?? 1);
    }
    out.push({ id, name: deck.name ?? "", cards: use });
  }
  return out;
}

/** The cards of a pasted list (its maybeboard and sideboard left out: the deck itself is what's needed). */
export function findFromText(text: string): FindWanted[] {
  return parseDeckText(text)
    .lines.filter((l) => l.board !== "MAYBE")
    .map((l) => ({ name: l.name, qty: l.quantity, scryfallId: null }));
}

/**
 * A deck's own cards (commanders and main deck; not the maybeboard), one line per card in the deck's own order, the
 * first line saying the printing it needs. Null when there's no such deck.
 */
export function findFromDeck(file: SyncFile, id: number): FindWanted[] | null {
  const deck = syncDecks(file).find((d) => d.deck?.archidektId === id);
  if (!deck) return null;
  const wanted = new Map<string, FindWanted>();
  for (const c of deck.cards ?? []) {
    if (c.maybeboard || !c.card?.name) continue;
    const key = nameKey(c.card.name);
    const prev = wanted.get(key);
    wanted.set(key, { name: c.card.name, qty: (prev?.qty ?? 0) + (c.quantity ?? 1), scryfallId: prev?.scryfallId ?? c.card.scryfallId ?? null });
  }
  return [...wanted.values()];
}

/**
 * The input from the sync file: the wanted list (a pasted text, or a deck's cards), the collection's stacks, the
 * binders in the bar's order and what every deck uses.
 */
export function findInputs(file: SyncFile, wanted: FindWanted[] | string, fromDeck: number | null = null, skipBasics = true): FindInput {
  const list = typeof wanted === "string" ? findFromText(wanted) : wanted;
  const bar = findBinders(file);
  return { wanted: list, stacks: findStacks(file, bar), binders: bar, decks: findDecks(file, fromDeck), fromDeck, skipBasics };
}

/** The result for a pasted list or a deck, straight from the sync file. */
export function findInFile(file: SyncFile, wanted: FindWanted[] | string, fromDeck: number | null = null, skipBasics = true): FindResult {
  return findCards(findInputs(file, wanted, fromDeck, skipBasics));
}
