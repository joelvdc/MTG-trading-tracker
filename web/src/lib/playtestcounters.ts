// The counters the playtester's card menu offers with − and + ("the quick list"). The player edits the list; the
// browser remembers it. Pure: the page does the storing.

/** The quick list until the player changes it (the engine's own kinds). */
export const DEFAULT_COUNTERS = ["+1/+1", "-1/-1", "loyalty", "charge"];

/** At most this many kinds in the quick list, so the card menu stays short enough. */
export const MAX_COUNTERS = 12;

/**
 * The engine's kind for a typed counter name: trimmed, "Loyalty" and "Charge" in any case are the engine's own
 * "loyalty" and "charge", and a typographic minus counts as "-1/-1". Empty when nothing usable was typed.
 */
export function counterKind(name: string): string {
  const text = name.trim().slice(0, 24).replace(/−/g, "-");
  const lower = text.toLowerCase();
  if (lower === "loyalty" || lower === "charge") return lower;
  return text;
}

/** How a kind is shown in the menu. */
export function counterLabel(kind: string): string {
  if (kind === "-1/-1") return "−1/−1";
  if (kind === "loyalty") return "Loyalty";
  if (kind === "charge") return "Charge";
  return kind;
}

/** The list with [kind] at the end, unless it is already there (or the list is full). */
export function withCounter(list: string[], name: string): string[] {
  const kind = counterKind(name);
  if (kind === "" || list.includes(kind) || list.length >= MAX_COUNTERS) return list;
  return [...list, kind];
}

/** The remembered list: the defaults when nothing (or nothing readable) was stored. */
export function parseCounters(raw: string | null): string[] {
  if (raw === null) return DEFAULT_COUNTERS.slice();
  try {
    const value: unknown = JSON.parse(raw);
    if (!Array.isArray(value)) return DEFAULT_COUNTERS.slice();
    let list: string[] = [];
    for (const item of value) if (typeof item === "string") list = withCounter(list, item);
    return list;
  } catch {
    return DEFAULT_COUNTERS.slice();
  }
}
