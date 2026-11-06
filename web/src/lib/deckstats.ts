// The deck page's stats, as the app works them out (app: data/DeckStats.kt): the mana curve, the colours in the
// cards' costs, the mana the deck makes and the tokens it makes.

/** Between the faces of a double-faced card's rules text (the app's CardInfo.FACE_BREAK). */
export const FACE_BREAK = "\n//\n";

/** A token or helper card (The Monarch…) a card makes (the app's TokenPart). */
export interface TokenPart {
  id: string;
  name: string;
  typeLine: string;
}

/** One of a deck's cards with what the stats need (the app's StatsCard). */
export interface StatsCard {
  name: string;
  scryfallId: string;
  quantity: number;
  cmc: number;
  /** Archidekt's card types, used until Scryfall's type line is known. */
  types: string;
  commander: boolean;
  typeLine: string | null;
  manaCost: string | null;
  producedMana: string | null;
  oracleText: string | null;
  tokens: TokenPart[] | null;
  powerToughness?: string | null;
  loyalty?: string | null;
}

function faceTypes(c: StatsCard): string[] {
  return (c.typeLine ? c.typeLine : c.types).split(" // ");
}

/** Whether a face is a land: types before the dash, e.g. "Legendary Land — Desert". */
function landFace(type: string): boolean {
  return type.split(" — ")[0].split(" ").includes("Land");
}

/** A land on either face, so spell // land cards (Malakir Rebirth) count as lands. */
export function isLand(c: StatsCard): boolean {
  return faceTypes(c).some(landFace);
}

/** The land faces' type lines and rules text. */
export function landFaces(c: StatsCard): [string, string][] {
  const texts = (c.oracleText ?? "").split(FACE_BREAK);
  const out: [string, string][] = [];
  faceTypes(c).forEach((t, i) => {
    if (landFace(t)) out.push([t, texts[i] ?? ""]);
  });
  return out;
}

/** Land types of its land faces, e.g. Swamp and Forest for Overgrown Tomb. */
export function landTypes(c: StatsCard): Set<string> {
  const out = new Set<string>();
  for (const [t] of landFaces(c)) {
    const i = t.indexOf(" — ");
    if (i < 0) continue;
    for (const w of t.slice(i + 3).split(" ")) if (w) out.add(w);
  }
  return out;
}

export function isBasic(c: StatsCard): boolean {
  return landFaces(c).some(([t]) => t.split(" — ")[0].split(" ").includes("Basic"));
}

export interface ManaCurve {
  /** Cards per mana value 0 to 7+ (lands left out). */
  counts: number[];
  spells: number;
  average: number;
}

export interface ColourCount {
  /** One of WUBRGC. */
  colour: string;
  count: number;
}

export interface SourceLine {
  name: string;
  quantity: number;
  /** The deck's lands a fetch land counts for this colour through. */
  finds: string[];
}

export interface ColourSources {
  colour: string;
  count: number;
  cards: SourceLine[];
}

export interface DeckToken {
  /** The printing shown. */
  id: string;
  name: string;
  /** With power and toughness when known ("Insect 1/1"). */
  label: string;
  makers: string[];
}

const COLOURS = "WUBRGC";
const BASIC_TYPES = ["Plains", "Island", "Swamp", "Mountain", "Forest"];
const SYMBOL = /\{([^}]+)\}/g;
const SEARCH = /search your library for ([^.]*)/gi;

export function curve(cards: StatsCard[]): ManaCurve {
  const spells = cards.filter((c) => !isLand(c));
  const counts = [0, 0, 0, 0, 0, 0, 0, 0];
  for (const c of spells) counts[Math.min(7, Math.max(0, Math.trunc(c.cmc)))] += c.quantity;
  const n = spells.reduce((s, c) => s + c.quantity, 0);
  return { counts, spells: n, average: n === 0 ? 0 : spells.reduce((s, c) => s + c.cmc * c.quantity, 0) / n };
}

/**
 * Coloured mana symbols in the cards' costs (every face that has one), copies counted. A hybrid symbol ({B/G})
 * counts for both its colours, a Phyrexian one ({B/P}) for its colour; generic mana doesn't count.
 */
export function costs(cards: StatsCard[]): ColourCount[] {
  const n = new Array(COLOURS.length).fill(0);
  for (const c of cards) {
    for (const m of (c.manaCost ?? "").matchAll(SYMBOL)) {
      const set = new Set(
        m[1]
          .toUpperCase()
          .split("/")
          .filter((p) => p.length === 1 && COLOURS.includes(p)),
      );
      for (const ch of set) n[COLOURS.indexOf(ch)] += c.quantity;
    }
  }
  return [...COLOURS].map((ch, i) => ({ colour: ch, count: n[i] })).filter((x) => x.count > 0);
}

/**
 * What a fetch land finds in the deck, by colour: its land faces' "search your library for …" read as which lands
 * qualify (basic or not, which land types), then the colours those make.
 */
export function fetches(card: StatsCard, lands: StatsCard[]): Map<string, string[]> {
  const out = new Map<string, Set<string>>();
  for (const [, text] of landFaces(card)) {
    for (const m of text.matchAll(SEARCH)) {
      const what = m[1];
      const types = BASIC_TYPES.filter((t) => new RegExp(`\\b${t}\\b`).test(what));
      if (types.length === 0 && !/\bland\b/i.test(what)) continue;
      const basic = /\bbasic\b/i.test(what);
      for (const l of lands) {
        if (l.name === card.name) continue;
        if (basic && !isBasic(l)) continue;
        if (types.length > 0) {
          const lt = landTypes(l);
          if (!types.some((t) => lt.has(t))) continue;
        }
        for (const ch of l.producedMana ?? "") {
          if (!out.has(ch)) out.set(ch, new Set());
          out.get(ch)!.add(l.name);
        }
      }
    }
  }
  const res = new Map<string, string[]>();
  for (const [k, v] of out) res.set(k, [...v].sort());
  return res;
}

/**
 * The sources of each colour, in WUBRG then colourless order, colours without sources left out. [identity] is the
 * commander's colour identity (null for a deck without a commander, where every colour counts). With [landsOnly] only
 * lands count; without [colourless] the colourless sources are left out.
 */
export function sources(cards: StatsCard[], identity: string | null, landsOnly: boolean, colourless = true): ColourSources[] {
  const lands = cards.filter(isLand);
  const lines = new Map<string, SourceLine[]>([...COLOURS].map((c) => [c, []]));
  for (const c of cards) {
    const land = isLand(c);
    if (!land && landsOnly) continue;
    const own = new Set(c.producedMana ?? "");
    const found = land ? fetches(c, lands) : new Map<string, string[]>();
    for (const colour of COLOURS) {
      if (colour !== "C" && identity !== null && !identity.includes(colour)) continue;
      const finds = found.get(colour) ?? [];
      if (own.has(colour) || finds.length > 0) lines.get(colour)!.push({ name: c.name, quantity: c.quantity, finds: own.has(colour) ? [] : finds });
    }
  }
  const out: ColourSources[] = [];
  for (const [colour, l] of lines) {
    if (l.length === 0 || (!colourless && colour === "C")) continue;
    const byName = new Map<string, SourceLine>();
    for (const s of l) {
      const had = byName.get(s.name);
      if (had) had.quantity += s.quantity;
      else byName.set(s.name, { ...s });
    }
    const cards = [...byName.values()].sort((a, b) => (a.name.toLowerCase() < b.name.toLowerCase() ? -1 : a.name.toLowerCase() > b.name.toLowerCase() ? 1 : 0));
    out.push({ colour, cards, count: cards.reduce((s, x) => s + x.quantity, 0) });
  }
  return out;
}

/**
 * The deck's tokens, one per token (by Scryfall's oracle id when [oracleIds] knows it, else by name and type), sorted
 * by name, each with the cards that make it. [pt] gives a token's power and toughness for its label.
 */
export function tokens(cards: StatsCard[], oracleIds: Record<string, string | undefined> = {}, pt: Record<string, string | null | undefined> = {}): DeckToken[] {
  const parts = new Map<string, { part: TokenPart; makers: Set<string> }>();
  for (const c of cards) {
    for (const p of c.tokens ?? []) {
      if (!parts.has(p.id)) parts.set(p.id, { part: p, makers: new Set() });
      parts.get(p.id)!.makers.add(c.name);
    }
  }
  const groups = new Map<string, { part: TokenPart; makers: Set<string> }[]>();
  for (const e of parts.values()) {
    const key = oracleIds[e.part.id] ?? `${e.part.name}|${e.part.typeLine}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(e);
  }
  const out: DeckToken[] = [];
  for (const same of groups.values()) {
    const first = same[0].part;
    const p = pt[first.id];
    const makers = [...new Set(same.flatMap((e) => [...e.makers]))].sort();
    out.push({ id: first.id, name: first.name, label: first.name + (p ? ` ${p}` : ""), makers });
  }
  return out.sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()) || a.label.localeCompare(b.label));
}

/** Scryfall's picture of a printing (a token's too), made from its id. */
export function scryfallImage(id: string, size: "normal" | "small" = "normal"): string {
  return `https://cards.scryfall.io/${size}/front/${id[0]}/${id[1]}/${id}.jpg`;
}
