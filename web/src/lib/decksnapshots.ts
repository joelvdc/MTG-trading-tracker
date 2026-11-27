// A deck's snapshots (app 1.124, Deck.snapshots): the whole main deck with a name, and the bracket, win turn and price
// it had at that moment, so it can be compared with another one later or with the deck as it is now. Newest first, at
// most 25. Only main-deck lines are kept (the maybeboard is left out), matched by full card name. Shared with the
// browser.

/** A card of a snapshot: the line's name, copies and printing, and whether it was the commander. */
export interface SnapCard {
  name: string;
  quantity: number;
  /** The printing's set code, in small letters ("" when the card has none). */
  set: string;
  /** The printing's collector number ("" when unknown). */
  number: string;
  commander: boolean;
}

/** One saved snapshot of the deck. */
export interface Snapshot {
  /** 12 random lowercase hex characters. */
  id: string;
  /** 1 to 60 characters, trimmed. */
  name: string;
  /** When it was taken (ms since epoch). */
  at: number;
  /** The main deck at that moment, sorted by name, then set, then number. */
  cards: SnapCard[];
  /** The deck's bracket then, or null. */
  bracket: number | null;
  /** The win turn label shown then ("T11"), or null. */
  winTurn: string | null;
  /** The deck's price in euros then, or null. */
  eur: number | null;
}

export const MAX_SNAPSHOTS = 25;
export const MAX_SNAPSHOT_NAME = 60;

/** Kotlin's compareTo for the strings here, so the app and the website sort a snapshot's cards the same way. */
const cmp = (a: string, b: string) => (a === b ? 0 : a < b ? -1 : 1);

/** A deck line as a snapshot keeps it. */
export interface SnapLine {
  card: { name: string; setCode: string; collectorNumber: string };
  quantity: number;
  commander: boolean;
  maybeboard: boolean;
}

/** The cards of [cards], sorted by name, then set, then number (as the app keeps them). */
function sortCards(cards: SnapCard[]): SnapCard[] {
  return [...cards].sort((a, b) => cmp(a.name, b.name) || cmp(a.set, b.set) || cmp(a.number, b.number));
}

/** The main deck of [lines] as snapshot cards (the maybeboard left out), sorted. */
export function cardsOf(lines: SnapLine[]): SnapCard[] {
  return sortCards(
    lines
      .filter((l) => !l.maybeboard)
      .map((l) => ({
        name: l.card.name,
        quantity: l.quantity,
        set: (l.card.setCode ?? "").toLowerCase(),
        number: l.card.collectorNumber ?? "",
        commander: l.commander,
      })),
  );
}

/** The name a snapshot gets when none is typed: today's UTC date ("2026-11-27"). */
export function defaultSnapshotName(now: number): string {
  return new Date(now).toISOString().slice(0, 10);
}

/** 12 random lowercase hex characters. */
export function newSnapshotId(): string {
  const bytes = new Uint8Array(6);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** The snapshots as kept in the deck's field; unreadable text gives no snapshots. */
export function parseSnapshots(text: string | null | undefined): Snapshot[] {
  if (!text) return [];
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    return [];
  }
  if (!Array.isArray(raw)) return [];
  const out: Snapshot[] = [];
  for (const value of raw) {
    if (!value || typeof value !== "object" || Array.isArray(value)) continue;
    const s = value as { id?: unknown; name?: unknown; at?: unknown; cards?: unknown; bracket?: unknown; winTurn?: unknown; eur?: unknown };
    if (typeof s.id !== "string" || typeof s.name !== "string" || typeof s.at !== "number" || !Number.isFinite(s.at)) continue;
    const cards: SnapCard[] = [];
    if (Array.isArray(s.cards)) {
      for (const value of s.cards) {
        if (!value || typeof value !== "object" || Array.isArray(value)) continue;
        const c = value as { name?: unknown; quantity?: unknown; set?: unknown; number?: unknown; commander?: unknown };
        if (typeof c.name !== "string" || typeof c.quantity !== "number" || !Number.isFinite(c.quantity)) continue;
        cards.push({
          name: c.name,
          quantity: c.quantity,
          set: typeof c.set === "string" ? c.set : "",
          number: typeof c.number === "string" ? c.number : "",
          commander: c.commander === true,
        });
      }
    }
    out.push({
      id: s.id,
      name: s.name,
      at: s.at,
      cards,
      bracket: typeof s.bracket === "number" && Number.isFinite(s.bracket) ? s.bracket : null,
      winTurn: typeof s.winTurn === "string" ? s.winTurn : null,
      eur: typeof s.eur === "number" && Number.isFinite(s.eur) ? s.eur : null,
    });
  }
  return out;
}

/** The snapshots as the deck's field keeps them (keys in the app's order); null when there are none. */
function encodeSnapshots(list: Snapshot[]): string | null {
  if (list.length === 0) return null;
  return JSON.stringify(
    list.map((s) => ({
      id: s.id,
      name: s.name,
      at: s.at,
      cards: s.cards.map((c) => ({ name: c.name, quantity: c.quantity, set: c.set, number: c.number, commander: c.commander })),
      bracket: s.bracket,
      winTurn: s.winTurn,
      eur: s.eur,
    })),
  );
}

/** A name as snapshots keep it: trimmed, cut to 60 characters, the UTC date when nothing is left. */
function cleanName(raw: string, now: number): string {
  return raw.trim().slice(0, MAX_SNAPSHOT_NAME) || defaultSnapshotName(now);
}

/** The name a snapshot gets when it is renamed: trimmed and cut; an empty one keeps the name it had. */
function renamedTo(raw: string, old: string): string {
  return raw.trim().slice(0, MAX_SNAPSHOT_NAME) || old;
}

/**
 * Takes a snapshot of [cards] (DeckSnapshots.take): [name] goes in front, the oldest one drops off beyond 25. [id] is
 * only for tests; otherwise the snapshot gets a new one.
 */
export function takeSnapshot(
  text: string | null | undefined,
  now: number,
  name: string,
  cards: SnapCard[],
  bracket: number | null,
  winTurn: string | null,
  eur: number | null,
  id: string = newSnapshotId(),
): string | null {
  const list = parseSnapshots(text);
  list.unshift({ id, name: cleanName(name, now), at: now, cards: sortCards(cards), bracket, winTurn, eur });
  return encodeSnapshots(list.slice(0, MAX_SNAPSHOTS));
}

/** Gives the snapshot [snapId] another name (DeckSnapshots.rename); the list itself stays as it is. */
export function renameSnapshot(text: string | null | undefined, snapId: string, name: string): string | null {
  const list = parseSnapshots(text).map((s) => (s.id === snapId ? { ...s, name: renamedTo(name, s.name) } : s));
  return encodeSnapshots(list);
}

/** Drops the snapshot [snapId] (DeckSnapshots.delete); null when no snapshot is left. */
export function deleteSnapshot(text: string | null | undefined, snapId: string): string | null {
  return encodeSnapshots(parseSnapshots(text).filter((s) => s.id !== snapId));
}

/** The same keys in plain string order (Kotlin's sorted()), with only positive counts. */
function sortedCounts(map: Record<string, number>): Record<string, number> {
  const out: Record<string, number> = {};
  for (const key of Object.keys(map).sort()) {
    const n = map[key];
    if (Number.isFinite(n) && n > 0) out[key] = n;
  }
  return out;
}

/** What changed between two card lists: per full card name, the rise or fall of its copies (as the change history has it). */
export function compareSnapshots(oldCards: SnapCard[], newCards: SnapCard[]): { added: Record<string, number>; removed: Record<string, number> } {
  const totals = (cards: SnapCard[]) => {
    const out = new Map<string, number>();
    for (const c of cards) out.set(c.name, (out.get(c.name) ?? 0) + c.quantity);
    return out;
  };
  const before = totals(oldCards);
  const after = totals(newCards);
  const added: Record<string, number> = {};
  const removed: Record<string, number> = {};
  for (const name of new Set([...before.keys(), ...after.keys()])) {
    const delta = (after.get(name) ?? 0) - (before.get(name) ?? 0);
    if (delta > 0) added[name] = delta;
    else if (delta < 0) removed[name] = -delta;
  }
  return { added: sortedCounts(added), removed: sortedCounts(removed) };
}

/** A snapshot as a decklist to paste or make a deck from: "<quantity> <name> (<SET>) <number>", commanders first. */
export function decklistText(snapshot: Snapshot): string {
  const line = (c: SnapCard) => `${c.quantity} ${c.name}${c.set ? ` (${c.set.toUpperCase()})` : ""}${c.number ? ` ${c.number}` : ""}`;
  return [...snapshot.cards.filter((c) => c.commander), ...snapshot.cards.filter((c) => !c.commander)].map(line).join("\n");
}
