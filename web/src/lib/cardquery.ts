// A deck "syntax filter" in Scryfall's search syntax (https://scryfall.com/docs/syntax), answered locally for the
// common keys. Keys it can't answer are listed by unsupportedKeys and ignored by matches (the server route
// /api/cards/query asks Scryfall for those). No server-only code: this runs in the browser too.

export interface QueryCard {
  name: string; // "Fire // Ice" for split/double-faced
  typeLine: string;
  oracleText: string; // all faces' text joined with "\n"
  manaCost: string; // "{1}{U} // {2}{U}"
  mv: number;
  colors: string[];
  colorIdentity: string[];
  power: string | null;
  toughness: string | null;
  loyalty: string | null;
  rarity: string | null; // "common" | "uncommon" | "rare" | "mythic" | null
  setCode: string;
  commander: boolean;
  gameChanger: boolean;
  priceEur: number | null;
  category: string; // the deck line's category
  tags?: string[];
}

/** null = this term can't be answered locally. */
type Test = (c: QueryCard) => boolean | null;
type Node = { k: "and" | "or"; items: Node[] } | { k: "not"; item: Node } | { k: "term"; test: Test };

export interface ParsedQuery {
  root: Node;
  unsupported: string[];
}

export interface QueryError {
  error: string;
}

export function isQueryError(r: ParsedQuery | QueryError): r is QueryError {
  return "error" in r;
}

class SyntaxProblem extends Error {}
const fail = (message: string): never => {
  throw new SyntaxProblem(message);
};

// ---- Tokens -------------------------------------------------------------------------------------------------

type Token = { t: "("; neg: boolean } | { t: ")" } | { t: "or" } | { t: "term"; neg: boolean; text: string };

function tokenize(text: string): Token[] {
  const out: Token[] = [];
  const n = text.length;
  let i = 0;
  while (i < n) {
    if (/\s/.test(text[i])) {
      i++;
      continue;
    }
    let neg = false;
    if (text[i] === "-" && i + 1 < n && !/\s/.test(text[i + 1])) {
      neg = true;
      i++;
    }
    if (text[i] === "(") {
      out.push({ t: "(", neg });
      i++;
      continue;
    }
    if (text[i] === ")") {
      if (neg) fail("A - must be followed by a search term.");
      out.push({ t: ")" });
      i++;
      continue;
    }
    const start = i;
    while (i < n && !/\s/.test(text[i]) && text[i] !== ")") {
      if (text[i] === '"') {
        i++;
        while (i < n && text[i] !== '"') i += text[i] === "\\" ? 2 : 1;
        if (i >= n) fail(`Missing closing " after ${text.slice(start).slice(0, 30)}`);
      }
      i++;
    }
    const word = text.slice(start, i);
    const lower = word.toLowerCase();
    if (!neg && lower === "or") out.push({ t: "or" });
    else if (!neg && lower === "and") continue;
    else out.push({ t: "term", neg, text: word });
  }
  return out;
}

// ---- Values -------------------------------------------------------------------------------------------------

const unquote = (s: string): string => (s.length >= 2 && s.startsWith('"') && s.endsWith('"') ? s.slice(1, -1).replace(/\\(["\\])/g, "$1") : s);

type Op = ":" | "=" | "!=" | "<" | "<=" | ">" | ">=";

function compare(a: number, op: Op, b: number): boolean {
  switch (op) {
    case ":":
    case "=":
      return a === b;
    case "!=":
      return a !== b;
    case "<":
      return a < b;
    case "<=":
      return a <= b;
    case ">":
      return a > b;
    default:
      return a >= b;
  }
}

const COLOR_LETTERS = "wubrg";
const NAMED_COLORS: Record<string, string> = {
  white: "w", blue: "u", black: "b", red: "r", green: "g",
  azorius: "wu", dimir: "ub", rakdos: "br", gruul: "rg", selesnya: "gw", orzhov: "wb", izzet: "ur", golgari: "bg", boros: "rw", simic: "gu",
  bant: "gwu", esper: "wub", grixis: "ubr", jund: "brg", naya: "rgw",
  abzan: "wbg", jeskai: "urw", sultai: "bgu", mardu: "rwb", temur: "gur",
};

/** The colour set a query value means; "multi" for multicolor, empty set for colorless. */
function colorSet(key: string, value: string): Set<string> | "multi" {
  const v = value.toLowerCase();
  if (v === "m" || v === "multicolor" || v === "multicolour") return "multi";
  if (v === "c" || v === "colorless" || v === "colourless") return new Set();
  const letters = NAMED_COLORS[v] ?? v;
  if (![...letters].every((l) => COLOR_LETTERS.includes(l))) fail(`"${value}" isn't a colour (use w u b r g c, or a name like azorius).`);
  return new Set(letters);
}

function colorTest(key: string, op: Op, value: string, field: (c: QueryCard) => string[]): Test {
  const q = colorSet(key, value);
  if (q === "multi") return (c) => new Set(field(c)).size >= 2;
  // "c:" means "at least these colours" ("c:c" colorless only), "id:" means "fits inside this identity".
  const eff: Op = op === ":" ? (key === "id" || key === "identity" ? "<=" : q.size === 0 ? "=" : ">=") : op;
  return (c) => {
    const a = new Set(field(c).map((x) => x.toLowerCase()));
    const sub = [...a].every((x) => q.has(x)); // a is inside q
    const sup = [...q].every((x) => a.has(x)); // a covers q
    switch (eff) {
      case "=":
        return sub && sup;
      case "!=":
        return !(sub && sup);
      case "<=":
        return sub;
      case ">=":
        return sup;
      case "<":
        return sub && !sup;
      default:
        return sup && !sub;
    }
  };
}

const stat = (s: string | null): number | null => {
  if (s == null) return null;
  const n = parseFloat(s);
  return isNaN(n) ? (s.includes("*") ? 0 : null) : n;
};

const NUMERIC: Record<string, (c: QueryCard) => number | null> = {
  mv: (c) => c.mv,
  cmc: (c) => c.mv,
  pow: (c) => stat(c.power),
  power: (c) => stat(c.power),
  tou: (c) => stat(c.toughness),
  toughness: (c) => stat(c.toughness),
  loy: (c) => stat(c.loyalty),
  loyalty: (c) => stat(c.loyalty),
  eur: (c) => c.priceEur,
  price: (c) => c.priceEur,
};

function numberTest(key: string, op: Op, value: string): Test {
  const get = NUMERIC[key];
  const v = value.toLowerCase();
  if ((v === "even" || v === "odd") && (key === "mv" || key === "cmc")) {
    const wantEven = v === "even";
    return (c) => Number.isInteger(c.mv) && (c.mv % 2 === 0) === wantEven;
  }
  if (/^-?\d+(\.\d+)?$/.test(v)) {
    const b = Number(v);
    return (c) => {
      const a = get(c);
      return a != null && compare(a, op, b);
    };
  }
  const other = NUMERIC[v]; // pow>tou
  if (other && !["eur", "price"].includes(v)) {
    return (c) => {
      const a = get(c);
      const b = other(c);
      return a != null && b != null && compare(a, op, b);
    };
  }
  return fail(`Expected a number after ${key}${op} (not "${value}").`);
}

const RARITIES = ["common", "uncommon", "rare", "mythic"];
const RARITY_LETTERS: Record<string, string> = { c: "common", u: "uncommon", r: "rare", m: "mythic" };

function rarityTest(op: Op, value: string): Test {
  const v = value.toLowerCase();
  const rank = RARITIES.indexOf(RARITY_LETTERS[v] ?? v);
  if (rank < 0) fail(`"${value}" isn't a rarity (common, uncommon, rare, mythic).`);
  return (c) => {
    const a = c.rarity ? RARITIES.indexOf(c.rarity.toLowerCase()) : -1;
    return a >= 0 && compare(a, op, rank);
  };
}

/** Mana symbols of a cost or query: "{2}{W}{W}" and "2WW" both give ["2","W","W"]. */
function symbols(s: string): string[] {
  const out: string[] = [];
  for (const m of s.matchAll(/\{([^}]*)\}|(\d+|[a-zA-Z])/g)) out.push((m[1] ?? m[2]).toUpperCase());
  return out;
}

function manaTest(op: Op, value: string): Test {
  if (op !== ":" && op !== "=") fail("m: only works with : or =.");
  const want = symbols(value);
  if (want.length === 0) fail(`"${value}" has no mana symbols (like {G}{G}).`);
  return (c) => {
    const have = symbols(c.manaCost);
    const left = [...have];
    return want.every((s) => {
      const i = left.indexOf(s);
      if (i < 0) return false;
      left.splice(i, 1);
      return true;
    });
  };
}

const containsTest = (get: (c: QueryCard) => string, value: string): Test => {
  const v = value.toLowerCase();
  return (c) => get(c).toLowerCase().includes(v);
};

function textOnly(key: string, op: Op) {
  if (op !== ":" && op !== "=") fail(`${key}: only works with : (like ${key}:word).`);
}

const UNSUPPORTED: Test = () => null;

// ---- Terms --------------------------------------------------------------------------------------------------

const KEY_TERM = /^([a-zA-Z_]+)(>=|<=|!=|:|=|<|>)([\s\S]*)$/;

function compileTerm(text: string, unsupported: Set<string>): Test {
  if (text.startsWith("!")) {
    const exact = unquote(text.slice(1)).toLowerCase();
    if (!exact) fail("Nothing after !.");
    return (c) => c.name.toLowerCase() === exact;
  }
  const m = KEY_TERM.exec(text);
  if (!m) return containsTest((c) => c.name, unquote(text));
  const key = m[1].toLowerCase();
  const op = m[2] as Op;
  const raw = m[3];
  const value = unquote(raw);
  if (value === "") return fail(`Nothing after ${m[1]}${op}`);
  switch (key) {
    case "name":
      textOnly(key, op);
      return containsTest((c) => c.name, value);
    case "o":
    case "oracle": {
      textOnly(key, op);
      const v = value.toLowerCase();
      return (c) => c.oracleText.toLowerCase().includes(v.replace(/~/g, c.name.toLowerCase()));
    }
    case "t":
    case "type":
      textOnly(key, op);
      return containsTest((c) => c.typeLine, value);
    case "c":
    case "color":
    case "colour":
      return colorTest("c", op, value, (c) => c.colors);
    case "id":
    case "identity":
    case "ci":
      return colorTest("id", op, value, (c) => c.colorIdentity);
    case "mv":
    case "cmc":
    case "pow":
    case "power":
    case "tou":
    case "toughness":
    case "loy":
    case "loyalty":
    case "eur":
    case "price":
      return numberTest(key, op, value);
    case "r":
    case "rarity":
      return rarityTest(op, value);
    case "s":
    case "set":
    case "e":
    case "edition": {
      textOnly(key, op);
      const v = value.toLowerCase();
      return (c) => c.setCode.toLowerCase() === v;
    }
    case "m":
    case "mana":
      return manaTest(op, value);
    case "cat":
    case "category":
      textOnly(key, op);
      return containsTest((c) => c.category, value);
    case "tag": {
      textOnly(key, op);
      const v = value.toLowerCase();
      return (c) => (c.tags ?? []).some((t) => t.toLowerCase().includes(v));
    }
    case "is": {
      const v = value.toLowerCase();
      if (v === "commander") return (c) => c.commander;
      if (v === "gamechanger") return (c) => c.gameChanger;
      if (v === "split" || v === "dfc") return (c) => c.name.includes("//");
      unsupported.add(`is:${v}`);
      return UNSUPPORTED;
    }
    default:
      unsupported.add(key);
      return UNSUPPORTED;
  }
}

// ---- Parser -------------------------------------------------------------------------------------------------

/** Parses a filter. Errors are short plain-English messages ready to show next to the field. */
export function parseQuery(text: string): ParsedQuery | QueryError {
  try {
    const tokens = tokenize(text);
    const unsupported = new Set<string>();
    let pos = 0;
    let last = "";

    const parseOr = (): Node => {
      const parts = [parseAnd()];
      while (tokens[pos]?.t === "or") {
        pos++;
        if (pos >= tokens.length || tokens[pos].t === ")") fail("Nothing after or.");
        parts.push(parseAnd());
      }
      return parts.length === 1 ? parts[0] : { k: "or", items: parts };
    };

    const parseAnd = (): Node => {
      const items: Node[] = [];
      for (;;) {
        const t = tokens[pos];
        if (!t || t.t === ")" || t.t === "or") break;
        pos++;
        let node: Node;
        if (t.t === "(") {
          if (tokens[pos]?.t === ")") fail("Empty ( ).");
          node = parseOr();
          if (tokens[pos]?.t !== ")") fail(`Missing ) after ${last ? `"${last}"` : "("}`);
          pos++;
        } else {
          last = t.text;
          node = { k: "term", test: compileTerm(last, unsupported) };
        }
        items.push(t.neg ? { k: "not", item: node } : node);
      }
      if (items.length === 0) fail(tokens[pos]?.t === "or" ? "Nothing before or." : "Nothing to search for here.");
      return items.length === 1 ? items[0] : { k: "and", items };
    };

    if (tokens.length === 0) return { root: { k: "and", items: [] }, unsupported: [] };
    const root = parseOr();
    if (pos < tokens.length) fail(`Unexpected ) after ${last ? `"${last}"` : "the start"} (no matching "(").`);
    return { root, unsupported: [...unsupported].sort() };
  } catch (e) {
    if (e instanceof SyntaxProblem) return { error: e.message };
    throw e;
  }
}

function evaluate(n: Node, c: QueryCard): boolean | null {
  switch (n.k) {
    case "term":
      return n.test(c);
    case "not": {
      const r = evaluate(n.item, c);
      return r === null ? null : !r;
    }
    case "and": {
      let unknown = false;
      for (const i of n.items) {
        const r = evaluate(i, c);
        if (r === false) return false;
        if (r === null) unknown = true;
      }
      return unknown ? null : true;
    }
    default: {
      let unknown = false;
      for (const i of n.items) {
        const r = evaluate(i, c);
        if (r === true) return true;
        if (r === null) unknown = true;
      }
      return unknown ? null : false;
    }
  }
}

/** Whether the card fits the filter. Terms this library can't answer (see unsupportedKeys) count as "unknown", which doesn't exclude a card. */
export function matches(q: ParsedQuery, c: QueryCard): boolean {
  return evaluate(q.root, c) !== false;
}

/** The keys (and unknown `is:` values) in the filter that only Scryfall can answer. */
export function unsupportedKeys(q: ParsedQuery): string[] {
  return q.unsupported;
}

// ---- Asking Scryfall (for the unsupported keys) --------------------------------------------------------------

const quoteName = (n: string): string => `!"${n.replace(/[\\"]/g, "\\$&")}"`;

/**
 * Scryfall `q` strings that check [query] against [names] in groups: `(query) (!"A" or !"B")`, each short enough
 * for its URL (encoded) to stay under [maxLength] characters (a single very long name still gets its own group).
 */
export function scryfallBatches(query: string, names: string[], maxLength = 1500): string[] {
  const head = `(${query.trim()}) (`;
  const budget = maxLength - "https://api.scryfall.com/cards/search?unique=cards&q=".length;
  const size = (s: string) => encodeURIComponent(s).length;
  const out: string[] = [];
  let group: string[] = [];
  let used = size(head) + size(")");
  const flush = () => {
    if (group.length) out.push(head + group.join(" or ") + ")");
    group = [];
    used = size(head) + size(")");
  };
  for (const name of [...new Set(names)]) {
    const part = quoteName(name);
    const add = size(part) + (group.length ? size(" or ") : 0);
    if (group.length && used + add > budget) flush();
    group.push(part);
    used += size(part) + (group.length > 1 ? size(" or ") : 0);
  }
  flush();
  return out;
}
