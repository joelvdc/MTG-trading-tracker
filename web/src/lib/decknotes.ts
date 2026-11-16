// A deck's primer and card notes (app 1.97, Deck.primer and Deck.cardNotes): the Archidekt description turned into a
// light-markdown primer, and the notes per card as the JSON object the app keeps. Shared with the browser.

export const MAX_PRIMER = 20_000;
export const MAX_NOTE = 500;

/** A card's name as the app matches it (the whole name, "A // B" included). */
export function nameKey(name: string): string {
  return name.trim().toLowerCase();
}

interface OpAttributes {
  bold?: unknown;
  header?: unknown;
  list?: unknown;
}

function attributesOf(raw: unknown): OpAttributes {
  return raw && typeof raw === "object" && !Array.isArray(raw) ? (raw as OpAttributes) : {};
}

/** The prefix a Quill line gets from its attributes: a header, a bullet or a numbered line. */
function prefixOf(attributes: OpAttributes): string {
  const header = attributes.header;
  if (typeof header === "number" && Number.isInteger(header) && header >= 1 && header <= 6) return "#".repeat(header) + " ";
  if (attributes.list === "bullet") return "- ";
  if (attributes.list === "ordered") return "1. ";
  return "";
}

/** The text of a Quill delta's ops, with bold as **text** and the headers and lists the primer understands. */
function textOfOps(ops: unknown[]): string {
  const lines: string[] = [];
  let line = "";
  const endLine = (attributes: OpAttributes) => {
    lines.push((line.length > 0 ? prefixOf(attributes) : "") + line);
    line = "";
  };
  for (const raw of ops) {
    const op = raw && typeof raw === "object" && !Array.isArray(raw) ? (raw as { insert?: unknown; attributes?: unknown }) : {};
    if (typeof op.insert !== "string") continue;
    const attributes = attributesOf(op.attributes);
    const pieces = op.insert.split("\n");
    for (let i = 0; i < pieces.length; i++) {
      const piece = pieces[i];
      if (piece !== "") line += attributes.bold === true ? `**${piece}**` : piece;
      // Every \n ends the line it is in; the text after the last one is the line still open.
      if (i < pieces.length - 1) endLine(attributes);
    }
  }
  if (line.length > 0) lines.push(line);
  return lines.join("\n");
}

/** The ops of an Archidekt description when it is a Quill delta, else null. */
function opsOf(description: string): unknown[] | null {
  try {
    const raw = JSON.parse(description) as unknown;
    if (raw && typeof raw === "object" && !Array.isArray(raw)) {
      const ops = (raw as { ops?: unknown }).ops;
      if (Array.isArray(ops)) return ops;
    }
  } catch {
    // Not JSON: the description is its own text.
  }
  return null;
}

/**
 * Archidekt's description as a primer (DeckNotes.primerFromArchidekt): a Quill delta's text with headers, bullets and
 * bold, or the description itself; trailing spaces and long runs of blank lines go, and it is cut to MAX_PRIMER.
 */
export function primerFromArchidekt(description: string | null | undefined): string | null {
  if (description == null || description.trim() === "") return null;
  const ops = opsOf(description);
  const text = ops ? textOfOps(ops) : description;
  const cleaned = text
    .split("\n")
    .map((line) => line.trimEnd())
    .join("\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
  if (cleaned === "") return null;
  return cleaned.slice(0, MAX_PRIMER);
}

/** The notes as the app keeps them, by nameKey: unreadable text gives no notes. */
export function parseCardNotes(json: string | null | undefined): Record<string, string> {
  if (!json) return {};
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch {
    return {};
  }
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    if (typeof value !== "string") continue;
    const note = value.trim().slice(0, MAX_NOTE);
    if (note) out[nameKey(key)] = note;
  }
  return Object.fromEntries(Object.keys(out).sort().map((k) => [k, out[k]]));
}

/** The notes as the synced field's text: trimmed, keys sorted and empty notes left out; null when there are none. */
export function encodeCardNotes(notes: Record<string, string>): string | null {
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(notes)) {
    const note = value.trim().slice(0, MAX_NOTE);
    if (note) out[nameKey(key)] = note;
  }
  const keys = Object.keys(out).sort();
  if (keys.length === 0) return null;
  return JSON.stringify(Object.fromEntries(keys.map((k) => [k, out[k]])));
}
