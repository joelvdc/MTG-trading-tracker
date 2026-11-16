// A deck's change history (app 1.97, Deck.history): one entry a day with the cards added and removed, newest first.
// Only main-deck lines count, matched by full card name with their quantities added up. Shared with the browser.

export interface HistoryEntry {
  /** The UTC date of the change, "YYYY-MM-DD". */
  day: string;
  /** Where the change came from: "app", "website" and/or "archidekt". */
  sources: string[];
  added: Record<string, number>;
  removed: Record<string, number>;
}

/** A deck line as the history compares it. */
export interface HistoryLine {
  name: string;
  quantity: number;
  maybeboard: boolean;
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

function countsOf(raw: unknown): Record<string, number> {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
  return sortedCounts(raw as Record<string, number>);
}

/** The history as kept in the deck's field; unreadable text gives no entries. */
export function parseHistory(text: string | null | undefined): HistoryEntry[] {
  if (!text) return [];
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    return [];
  }
  if (!Array.isArray(raw)) return [];
  const out: HistoryEntry[] = [];
  for (const value of raw) {
    if (!value || typeof value !== "object" || Array.isArray(value)) continue;
    const entry = value as { day?: unknown; sources?: unknown; added?: unknown; removed?: unknown };
    if (typeof entry.day !== "string") continue;
    const sources = Array.isArray(entry.sources) ? entry.sources.filter((s): s is string => typeof s === "string") : [];
    out.push({ day: entry.day, sources, added: countsOf(entry.added), removed: countsOf(entry.removed) });
  }
  return out;
}

/** What changed between two decklists: per full card name, the rise or fall of its copies (main deck only). */
export function diffLines(before: HistoryLine[], after: HistoryLine[]): { added: Record<string, number>; removed: Record<string, number> } {
  const totals = (lines: HistoryLine[]) => {
    const out = new Map<string, number>();
    for (const line of lines) {
      if (line.maybeboard) continue;
      out.set(line.name, (out.get(line.name) ?? 0) + line.quantity);
    }
    return out;
  };
  const old = totals(before);
  const now = totals(after);
  const added: Record<string, number> = {};
  const removed: Record<string, number> = {};
  for (const name of new Set([...old.keys(), ...now.keys()])) {
    const delta = (now.get(name) ?? 0) - (old.get(name) ?? 0);
    if (delta > 0) added[name] = delta;
    else if (delta < 0) removed[name] = -delta;
  }
  return { added: sortedCounts(added), removed: sortedCounts(removed) };
}

/**
 * Adds a change to a deck's history (DeckHistory.record): today's entry is combined with the change (sources kept in
 * order), a new day goes in front, and at most 100 days are kept. With no change the history is returned as it was.
 */
export function recordHistory(
  history: string | null | undefined,
  now: number,
  source: string,
  change: { added: Record<string, number>; removed: Record<string, number> },
): string | null {
  const added = sortedCounts(change.added);
  const removed = sortedCounts(change.removed);
  if (Object.keys(added).length === 0 && Object.keys(removed).length === 0) return history ?? null;
  const day = new Date(now).toISOString().slice(0, 10);
  const list = parseHistory(history);
  const first = list[0];
  if (first && first.day === day) {
    const addedNow: Record<string, number> = {};
    const removedNow: Record<string, number> = {};
    const names = new Set([...Object.keys(first.added), ...Object.keys(first.removed), ...Object.keys(added), ...Object.keys(removed)]);
    for (const name of names) {
      const net = (first.added[name] ?? 0) - (first.removed[name] ?? 0) + (added[name] ?? 0) - (removed[name] ?? 0);
      if (net > 0) addedNow[name] = net;
      else if (net < 0) removedNow[name] = -net;
    }
    const sources = [...first.sources];
    if (!sources.includes(source)) sources.push(source);
    const combined: HistoryEntry = { day, sources, added: sortedCounts(addedNow), removed: sortedCounts(removedNow) };
    // A day that ends up with nothing left (a change and its undo) leaves no entry behind.
    list.splice(0, 1, ...(Object.keys(combined.added).length === 0 && Object.keys(combined.removed).length === 0 ? [] : [combined]));
  } else {
    list.unshift({ day, sources: [source], added, removed });
  }
  const kept = list.slice(0, 100);
  if (kept.length === 0) return null;
  // Keys in the app's order: day, sources, added, removed; the maps' keys in plain string order.
  return JSON.stringify(kept.map((entry) => ({ day: entry.day, sources: entry.sources, added: sortedCounts(entry.added), removed: sortedCounts(entry.removed) })));
}
