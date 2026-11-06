// A decklist pasted as text (the app's DeckText.kt): Moxfield's export ("1 Sol Ring (CMR) 263 *F* #Ramp"),
// Archidekt's ("1x Sol Ring (cmr) 263 [Ramp]"), MTG Arena's (with "Commander" and "Deck" headings) and plain lists
// ("1 Sol Ring"). Headings such as "Commander", "Sideboard" or "Maybeboard" say where the following cards go.
// Pure code, also used in the browser.

export type Board = "MAIN" | "COMMANDER" | "MAYBE";

export interface TextLine {
  quantity: number;
  name: string;
  setCode: string | null;
  number: string | null;
  foil: boolean;
  etched: boolean;
  /** Moxfield's tags or Archidekt's categories, in their order. */
  tags: string[];
  board: Board;
}

export interface TextResult {
  lines: TextLine[];
  unreadable: string[];
}

export const hasTags = (r: TextResult) => r.lines.some((l) => l.tags.length > 0);
export const textCardCount = (r: TextResult) => r.lines.filter((l) => l.board !== "MAYBE").reduce((s, l) => s + l.quantity, 0);

const HEADING = /^(?:\/\/\s*)?(commanders?|deck|main|mainboard|main deck|sideboard|maybeboard|considering|companions?|tokens?|about)\s*:?\s*(?:\(\d+\))?$/i;
const QUANTITY = /^(\d+)\s*[xX]?\s+(.+)$/;
const HASH_TAG = /(?:^|\s)#!?([^\s#]+)/g;
const BRACKET = /\[([^\]]*)]/g;
const FINISH_MARK = /\s\*([A-Za-z]{1,3})\*/g;
const SET_NUMBER = /\s\(([A-Za-z0-9]{2,8})\)(?:\s+(\S+))?\s*$/;

export function parseDeckText(text: string): TextResult {
  const lines: TextLine[] = [];
  const unreadable: string[] = [];
  let board: Board = "MAIN";
  let sawHeading = false;
  const blocks: number[][] = [[]];
  for (const raw of text.split(/\r\n|\n|\r/)) {
    const line = raw.trim();
    if (line === "") {
      if (blocks[blocks.length - 1].length > 0) blocks.push([]);
      continue;
    }
    if (line.toLowerCase().startsWith("name ") && lines.length === 0 && !sawHeading) continue;
    const m = HEADING.exec(line);
    if (m) {
      if (lines.length === 0) sawHeading = true;
      const h = m[1].toLowerCase();
      board =
        h === "commander" || h === "commanders"
          ? "COMMANDER"
          : ["sideboard", "maybeboard", "considering", "tokens", "token", "about"].includes(h)
            ? "MAYBE"
            : "MAIN";
      continue;
    }
    if (line.startsWith("//") || line.startsWith("#")) continue;
    const parsed = readLine(line, board);
    if (!parsed) unreadable.push(line);
    else {
      blocks[blocks.length - 1].push(lines.length);
      lines.push(parsed);
    }
  }
  // Moxfield's text starts without a heading: a first block of one or two cards set apart by a blank line is the commander.
  if (!sawHeading && !lines.some((l) => l.board === "COMMANDER")) {
    const first = blocks.find((b) => b.length > 0);
    if (first && first.length <= 2 && blocks.filter((b) => b.length > 0).length > 1 && first.every((i) => lines[i].quantity === 1)) {
      for (const i of first) lines[i] = { ...lines[i], board: "COMMANDER" };
    }
  }
  return { lines, unreadable };
}

function readLine(line: string, board: Board): TextLine | null {
  const m = QUANTITY.exec(line);
  if (!m) return null;
  const qty = Number(m[1]);
  if (!Number.isInteger(qty) || qty < 1 || qty > 999) return null;
  let rest = " " + m[2];
  const tags: string[] = [];
  let lineBoard = board;
  for (const b of rest.matchAll(BRACKET)) {
    for (const c of b[1].split(",").map((x) => x.trim()).filter((x) => x)) {
      const name = c.split("{")[0].trim();
      if (c.includes("{noDeck}") && lineBoard === "MAIN") lineBoard = "MAYBE";
      if (name.toLowerCase() === "commander" && lineBoard === "MAIN") lineBoard = "COMMANDER";
      if (name) tags.push(name);
    }
  }
  rest = rest.replace(BRACKET, " ");
  for (const h of rest.matchAll(HASH_TAG)) tags.push(h[1]);
  rest = rest.replace(HASH_TAG, " ");
  let foil = false;
  let etched = false;
  for (const f of rest.matchAll(FINISH_MARK)) {
    if (f[1].toUpperCase() === "E") etched = true;
    else foil = true;
  }
  rest = rest.replace(FINISH_MARK, " ").trimEnd();
  let set: string | null = null;
  let number: string | null = null;
  const s = SET_NUMBER.exec(rest);
  if (s) {
    set = s[1].toLowerCase();
    number = s[2] ? s[2] : null;
    rest = rest.slice(0, s.index);
  }
  const name = rest.trim();
  if (!name) return null;
  return { quantity: qty, name, setCode: set, number, foil: foil || etched, etched, tags: [...new Set(tags)], board: lineBoard };
}
