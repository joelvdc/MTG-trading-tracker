// Comparing your deck with another one by card name (printings are ignored): what to add, what to remove and what
// both decks have. The lines can come from anywhere (the sync file, Archidekt, a pasted list). Pure code, also used
// in the browser.
import { caseless, primaryType, TYPE_ORDER } from "./deckshared";

/** One line of a deck, whatever its source (the sync file, Archidekt or a pasted list). */
export interface CompareLine {
  name: string;
  quantity: number;
  /** The card's types, like "Legendary Creature". */
  types: string;
  commander: boolean;
}

/** A card in the comparison's lists. */
export interface CompareCard {
  name: string;
  /** Copies to add, to remove, or held by both decks. */
  quantity: number;
  /** primaryType of the card's types: "Creature", "Land", ... or "Other". */
  type: string;
  /** Copies in the collection, in any printing. */
  owned: number;
}

export interface DeckComparison {
  add: CompareCard[];
  remove: CompareCard[];
  both: CompareCard[];
  /** Total copies in each deck, commanders included. */
  mineCount: number;
  otherCount: number;
  commanders: { mine: string[]; other: string[]; differ: boolean };
}

export interface CompareGroup {
  type: string;
  /** Total copies in the group. */
  count: number;
  cards: CompareCard[];
}

/** A card's key: its front face, trimmed and lower case ("Fire // Ice" and "FIRE " give "fire"). */
export function compareKey(name: string): string {
  return name.split(" // ")[0].trim().toLowerCase();
}

/** What one side of the comparison knows about a card: its copies and the first line seen for it. */
interface Side {
  quantity: number;
  name: string;
  types: string;
  commander: boolean;
}

/** One side of the comparison: the cards by key (in the order first seen) and their total copies. */
interface SideDeck {
  cards: Map<string, Side>;
  count: number;
}

/** A deck's lines added up per compareKey. Lines with a quantity of 0 or less are left out; the first line of a card
 *  gives its name, its types and whether it is a commander. */
function sideDeck(lines: CompareLine[]): SideDeck {
  const cards = new Map<string, Side>();
  let count = 0;
  for (const l of lines) {
    if (l.quantity <= 0) continue;
    count += l.quantity;
    const key = compareKey(l.name);
    const seen = cards.get(key);
    if (seen) seen.quantity += l.quantity;
    // Several printings of one card are one card: the first line's name and types are kept.
    else cards.set(key, { quantity: l.quantity, name: l.name, types: l.types, commander: l.commander });
    // Any line of the card on a side with the commander mark makes it a commander there.
    if (l.commander) cards.get(key)!.commander = true;
  }
  return { cards, count };
}

/** The collection's counts per compareKey, from the counts by lower-case full name: "fire // ice" counts for "fire". */
function ownedByKey(owned: Map<string, number>): Map<string, number> {
  const out = new Map<string, number>();
  for (const [name, n] of owned) {
    const key = compareKey(name);
    out.set(key, (out.get(key) ?? 0) + n);
  }
  return out;
}

/**
 * Compares two decks by card name, ignoring printings. For each card the copies both decks have go in "both"; the
 * copies one deck has and the other doesn't go in "add" (the other deck's) or "remove" (yours). A commander on either
 * side is never asked to be added or removed.
 */
export function compareDecks(mine: CompareLine[], other: CompareLine[], owned: Map<string, number>): DeckComparison {
  const a = sideDeck(mine);
  const b = sideDeck(other);
  const have = ownedByKey(owned);

  // Every card of either deck, in the order mine first came in, then the other deck's new ones.
  const keys = [...a.cards.keys(), ...[...b.cards.keys()].filter((k) => !a.cards.has(k))];

  const add: CompareCard[] = [];
  const remove: CompareCard[] = [];
  const both: CompareCard[] = [];
  const mineCommanders: string[] = [];
  const otherCommanders: string[] = [];
  const mineCommanderKeys = new Set<string>();
  const otherCommanderKeys = new Set<string>();

  const card = (name: string, types: string, quantity: number, key: string): CompareCard => ({
    name,
    quantity,
    type: primaryType(types),
    owned: have.get(key) ?? 0,
  });

  for (const key of keys) {
    const m = a.cards.get(key);
    const o = b.cards.get(key);
    if (m?.commander) mineCommanderKeys.add(key);
    if (o?.commander) otherCommanderKeys.add(key);
    const inMine = m?.quantity ?? 0;
    const inOther = o?.quantity ?? 0;

    // The copies both decks have. My deck's name and types are used for it.
    const shared = Math.min(inMine, inOther);
    if (shared > 0) both.push(card(m?.name ?? o!.name, m?.types ?? o!.types, shared, key));

    // A commander isn't asked for: one deck's commander can't go in the other's main deck.
    if (m?.commander || o?.commander) continue;

    if (inOther > inMine) add.push(card(o!.name, o!.types, inOther - inMine, key));
    else if (inMine > inOther) remove.push(card(m!.name, m!.types, inMine - inOther, key));
  }

  // The commander names in the order first seen, one per card.
  for (const key of a.cards.keys()) if (mineCommanderKeys.has(key)) mineCommanders.push(a.cards.get(key)!.name);
  for (const key of b.cards.keys()) if (otherCommanderKeys.has(key)) otherCommanders.push(b.cards.get(key)!.name);

  // A pasted list without commanders can't differ from yours: only two known sets of commanders can.
  const differ = mineCommanders.length > 0 && otherCommanders.length > 0 && !sameKeys(mineCommanderKeys, otherCommanderKeys);

  const byName = (x: CompareCard, y: CompareCard) => caseless(x.name, y.name);
  return {
    add: add.sort(byName),
    remove: remove.sort(byName),
    both: both.sort(byName),
    mineCount: a.count,
    otherCount: b.count,
    commanders: { mine: mineCommanders, other: otherCommanders, differ },
  };
}

/** Whether two sets hold the same keys. */
function sameKeys(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false;
  for (const k of a) if (!b.has(k)) return false;
  return true;
}

/** The cards grouped by type, in TYPE_ORDER and then "Other", leaving out empty groups; the cards' order is kept. */
export function cardsByType(cards: CompareCard[]): CompareGroup[] {
  const groups = new Map<string, CompareCard[]>();
  for (const c of cards) {
    const list = groups.get(c.type);
    if (list) list.push(c);
    else groups.set(c.type, [c]);
  }
  const order = [...TYPE_ORDER, "Other"];
  const pos = (t: string) => (order.includes(t) ? order.indexOf(t) : order.length);
  return [...groups.entries()]
    .sort((x, y) => pos(x[0]) - pos(y[0]))
    .map(([type, list]) => ({ type, count: list.reduce((n, c) => n + c.quantity, 0), cards: list }));
}

/** The cards as a list to copy: one "<quantity> <name>" per line, in the order given. */
export function compareText(cards: CompareCard[]): string {
  return cards.map((c) => `${c.quantity} ${c.name}`).join("\n");
}
