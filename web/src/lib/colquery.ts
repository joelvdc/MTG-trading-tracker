// Search, filter, sort and statistics for the collection pages, ported from the app (CollectionScreen's search field,
// CollectionQuery.kt CollectionFilter/SortSpec, CollectionStats.kt). Runs in the browser on the rows the server sent.
import type { CollDeck, CollRow } from "./collview";
import { conditionName, languageName } from "./cards";
import { SOURCE_LABELS, type Finish, type Source } from "./pricing";

/** A finish's name as the app writes it (CardRef.finishName): "Normal", "Foil", "Surge foil", "Etched foil". */
export function finishName(row: Pick<CollRow, "finish" | "card">): string {
  if (row.finish === "NONFOIL") return "Normal";
  if (row.finish === "ETCHED") return "Etched foil";
  const t = row.card.foilType;
  if (!t) return "Foil";
  if (t === "serialized") return "Serialized";
  const base = t.replace(/foil$/, "");
  return base.charAt(0).toUpperCase() + base.slice(1) + " foil";
}

export function displayName(card: { name: string; flavorName: string | null }): string {
  return card.flavorName ? `${card.flavorName} (${card.name})` : card.name;
}

/** The app's search field: name, flavour name, set code (whole), set name, foil type, "signed", "altered". */
export function matchesText(r: CollRow, text: string): boolean {
  const f = text.trim().toLowerCase();
  if (!f) return true;
  const c = r.card;
  return (
    c.name.toLowerCase().includes(f) ||
    (c.flavorName ?? "").toLowerCase().includes(f) ||
    c.setCode.toLowerCase() === f ||
    c.setName.toLowerCase().includes(f) ||
    (r.finish !== "NONFOIL" && finishName(r).toLowerCase().includes(f)) ||
    (r.signed && f === "signed") ||
    (r.altered && f === "altered")
  );
}

export type ColorMatch = "ANY" | "EXACT" | "WITHIN" | "IDENTITY";
export type DeckFilter = "ANY" | "IN" | "OUT";
export type MarkFilter = "SIGNED" | "ALTERED" | "PLAIN";
/** Two-faced and two-part cards (the app's FaceKind, 1.90). */
export type FaceKind = "MDFC" | "TRANSFORM" | "SPLIT";

export const FACE_KINDS: [FaceKind, string][] = [
  ["MDFC", "MDFC"],
  ["TRANSFORM", "Transforming"],
  ["SPLIT", "Split / Adventure"],
];

/** The app's FaceKind.of: the kind from Scryfall's layout; null for an ordinary card or an unknown layout. */
export function faceKind(layout: string | null | undefined): FaceKind | null {
  switch (layout) {
    case "modal_dfc":
      return "MDFC";
    case "transform":
    case "meld":
      return "TRANSFORM";
    case "split":
    case "adventure":
    case "flip":
    case "prepare":
      return "SPLIT";
    default:
      return null;
  }
}

export const COLOR_MATCH_LABELS: Record<ColorMatch, string> = {
  ANY: "Has any of them",
  EXACT: "Exactly these",
  WITHIN: "Fits in this color identity",
  IDENTITY: "Exactly this color identity",
};

/** The collection's filter (CollectionFilter): all set parts must match; within a part, any of the values does. */
export interface Filter {
  /** W, U, B, R, G; C = colorless; M = multicolor. */
  colors: string[];
  colorMatch: ColorMatch;
  types: string[];
  rarities: string[];
  sets: string[];
  finishes: Finish[];
  conditions: string[];
  languages: string[];
  /** One copy's price, in euros. */
  minPrice: number | null;
  maxPrice: number | null;
  decks: DeckFilter;
  marks: MarkFilter[];
  /** MDFC, transforming, split (since 1.90; filters saved before have none). */
  faces: FaceKind[];
}

export const EMPTY_FILTER: Filter = {
  colors: [],
  colorMatch: "ANY",
  types: [],
  rarities: [],
  sets: [],
  finishes: [],
  conditions: [],
  languages: [],
  minPrice: null,
  maxPrice: null,
  decks: "ANY",
  marks: [],
  faces: [],
};

export const FILTER_TYPES = ["Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle", "Legendary"];
export const FILTER_RARITIES: [string, string][] = [
  ["common", "Common"],
  ["uncommon", "Uncommon"],
  ["rare", "Rare"],
  ["mythic", "Mythic"],
  ["special", "Special"],
];

/** How many parts of the filter are set (the number on the filter button). */
export function filterCount(f: Filter): number {
  return [
    f.colors.length > 0,
    f.types.length > 0,
    f.rarities.length > 0,
    f.sets.length > 0,
    f.finishes.length > 0,
    f.conditions.length > 0,
    f.languages.length > 0,
    f.minPrice != null || f.maxPrice != null,
    f.decks !== "ANY",
    f.marks.length > 0,
    (f.faces ?? []).length > 0,
  ].filter(Boolean).length;
}

/** Lower-case front-face names of every card in a deck (maybeboard left out). */
export function deckCardNames(decks: CollDeck[]): Set<string> {
  const out = new Set<string>();
  for (const d of decks) for (const k of Object.keys(d.cards)) out.add(k);
  return out;
}

export function front(r: CollRow): string {
  return r.card.name.split(" // ")[0].toLowerCase();
}

/** How a card (by front-face name) is used in the decks, for the Decks, In decks and Spare columns. */
export interface DeckUse {
  /** The decks that use it, by name. */
  decks: string[];
  /** Copies the decks use, all together. */
  copies: number;
  /** Copies owned, every printing and binder together. */
  owned: number;
}

const NO_USE: DeckUse = { decks: [], copies: 0, owned: 0 };

/** Every card name's use in [decks] (retired decks and maybeboards are already left out) against the whole collection. */
export function deckUse(rows: CollRow[], decks: CollDeck[]): Map<string, DeckUse> {
  const out = new Map<string, DeckUse>();
  for (const r of rows) {
    const k = front(r);
    const u = out.get(k) ?? { decks: [], copies: 0, owned: 0 };
    u.owned += r.quantity;
    out.set(k, u);
  }
  for (const d of decks) {
    for (const [k, n] of Object.entries(d.cards)) {
      const u = out.get(k);
      if (!u) continue;
      u.copies += n;
      if (d.name && !u.decks.includes(d.name)) u.decks.push(d.name);
    }
  }
  for (const u of out.values()) u.decks.sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
  return out;
}

export function useOf(use: Map<string, DeckUse> | undefined, r: CollRow): DeckUse {
  return use?.get(front(r)) ?? NO_USE;
}

const WUBRG = "WUBRG";

function colorsMatch(f: Filter, r: CollRow): boolean {
  const info = r.info;
  if (!info) return false;
  const wanted = new Set(f.colors.filter((c) => WUBRG.includes(c)));
  const has = [...info.c];
  switch (f.colorMatch) {
    case "ANY":
      return (
        (wanted.size > 0 && has.some((c) => wanted.has(c))) ||
        (f.colors.includes("C") && has.length === 0) ||
        (f.colors.includes("M") && has.length > 1)
      );
    case "EXACT":
      if (f.colors.includes("C") && wanted.size === 0) return has.length === 0;
      return has.length === wanted.size && has.every((c) => wanted.has(c));
    case "WITHIN":
      return [...info.i].every((c) => wanted.has(c));
    case "IDENTITY": {
      const id = [...info.i];
      return id.length === wanted.size && id.every((c) => wanted.has(c));
    }
  }
}

export function matchesFilter(f: Filter, r: CollRow, deckNames: Set<string>): boolean {
  if (f.colors.length > 0 && !colorsMatch(f, r)) return false;
  if (f.types.length > 0) {
    const t = r.info?.t;
    if (!t || !f.types.some((x) => t.includes(x))) return false;
  }
  if ((f.faces ?? []).length > 0) {
    const k = faceKind(r.info?.l);
    if (!k || !f.faces.includes(k)) return false;
  }
  if (f.rarities.length > 0 && !f.rarities.includes(r.card.rarity)) return false;
  if (f.sets.length > 0 && !f.sets.includes(r.card.setCode.toLowerCase())) return false;
  if (f.finishes.length > 0 && !f.finishes.includes(r.finish)) return false;
  if (f.conditions.length > 0 && !f.conditions.includes(r.condition)) return false;
  if (f.languages.length > 0 && !f.languages.includes(r.language)) return false;
  if (
    f.marks.length > 0 &&
    !((f.marks.includes("SIGNED") && r.signed) || (f.marks.includes("ALTERED") && r.altered) || (f.marks.includes("PLAIN") && !r.signed && !r.altered))
  ) {
    return false;
  }
  if (f.minPrice != null || f.maxPrice != null) {
    const p = r.unitEur;
    if (p == null) return false;
    if (f.minPrice != null && p < f.minPrice) return false;
    if (f.maxPrice != null && p > f.maxPrice) return false;
  }
  const inDeck = deckNames.has(front(r));
  return f.decks === "ANY" || (f.decks === "IN" ? inDeck : !inDeck);
}

// ---- Sorting (SortSpec) ----

export type SortField =
  | "NAME"
  | "COLOR"
  | "TYPE"
  | "MANA_VALUE"
  | "RARITY"
  | "SET"
  | "NUMBER"
  | "VALUE"
  | "RECENT"
  | "QUANTITY"
  | "CHANGE_PCT"
  | "CHANGE_MONEY"
  | "DECKS"
  | "SPARE"
  | "EDHREC";

export const SORT_FIELDS: { field: SortField; label: string; forward: string; backward: string }[] = [
  { field: "NAME", label: "Name", forward: "A to Z", backward: "Z to A" },
  { field: "COLOR", label: "Color", forward: "W, U, B, R, G, multicolor, colorless, lands", backward: "Reversed" },
  { field: "TYPE", label: "Type", forward: "Creatures first", backward: "Lands first" },
  { field: "MANA_VALUE", label: "Mana value", forward: "Low to high", backward: "High to low" },
  { field: "RARITY", label: "Rarity", forward: "Mythic first", backward: "Common first" },
  { field: "SET", label: "Set", forward: "A to Z", backward: "Z to A" },
  { field: "NUMBER", label: "Collector number", forward: "Low to high", backward: "High to low" },
  { field: "VALUE", label: "Value per card", forward: "Highest first", backward: "Lowest first" },
  { field: "RECENT", label: "Date added", forward: "Newest first", backward: "Oldest first" },
  { field: "QUANTITY", label: "Copies", forward: "Most first", backward: "Fewest first" },
  { field: "CHANGE_PCT", label: "Change in %", forward: "Biggest rise first", backward: "Biggest fall first" },
  { field: "CHANGE_MONEY", label: "Change in money", forward: "Biggest rise first", backward: "Biggest fall first" },
  { field: "DECKS", label: "Decks using it", forward: "Most first", backward: "Fewest first" },
  { field: "SPARE", label: "Spare copies", forward: "Most first", backward: "Fewest first" },
  { field: "EDHREC", label: "EDHREC rank", forward: "Most played first", backward: "Least played first" },
];

export interface SortLevel {
  field: SortField;
  reversed: boolean;
}

const TYPE_ORDER = ["Creature", "Planeswalker", "Battle", "Land", "Instant", "Sorcery", "Artifact", "Enchantment", "Kindred"];
const RARITY = ["mythic", "rare", "uncommon", "common", "special", "bonus"];

export function typeRank(typeLine: string | null | undefined): number {
  if (typeLine == null) return 99;
  const words = typeLine.split(" // ")[0].split(" — ")[0].split(" ");
  const primary = TYPE_ORDER.find((t) => words.includes(t));
  return primary ? TYPE_ORDER.indexOf(primary) : 98;
}

/** MtgColors.sortKey: white, blue, black, red, green, then multicoloured, colourless, lands last. */
export function colorKey(r: CollRow): number {
  const info = r.info;
  if (!info) return 9999;
  if (info.t.split(" // ")[0].includes("Land")) return 9000;
  if (info.c.length === 0) return 8000;
  if (info.c.length === 1) return WUBRG.indexOf(info.c);
  return 100 * info.c.length + [...info.c].reduce((n, c) => n + (1 << WUBRG.indexOf(c)), 0);
}

const nocase = (a: string, b: string) => {
  const x = a.toLowerCase();
  const y = b.toLowerCase();
  return x < y ? -1 : x > y ? 1 : 0;
};

function number(n: string): number {
  const d = n.replace(/\D/g, "");
  return d ? Number.parseInt(d, 10) : Number.MAX_SAFE_INTEGER;
}

type Cmp = (a: CollRow, b: CollRow) => number;

function fieldCmp(f: SortField, use?: Map<string, DeckUse>): Cmp {
  switch (f) {
    case "NAME":
      return (a, b) => nocase(a.card.name, b.card.name);
    case "COLOR":
      return (a, b) => colorKey(a) - colorKey(b);
    case "TYPE":
      return (a, b) => typeRank(a.info?.t) - typeRank(b.info?.t);
    case "MANA_VALUE":
      return (a, b) => (a.info?.v ?? 99) - (b.info?.v ?? 99);
    case "RARITY": {
      const r = (x: CollRow) => (RARITY.indexOf(x.card.rarity) < 0 ? 99 : RARITY.indexOf(x.card.rarity));
      return (a, b) => r(a) - r(b);
    }
    case "SET":
      return (a, b) => nocase(a.card.setName, b.card.setName);
    case "NUMBER":
      return (a, b) => (a.card.setName < b.card.setName ? -1 : a.card.setName > b.card.setName ? 1 : number(a.card.collectorNumber) - number(b.card.collectorNumber));
    case "VALUE":
      return (a, b) => (b.unitEur ?? -1) - (a.unitEur ?? -1);
    case "RECENT":
      return (a, b) => b.addedAt - a.addedAt;
    case "QUANTITY":
      return (a, b) => b.quantity - a.quantity;
    case "CHANGE_PCT":
    case "CHANGE_MONEY":
      return changeCmp(f, false);
    case "DECKS":
      return (a, b) => useOf(use, b).decks.length - useOf(use, a).decks.length;
    case "SPARE": {
      const spare = (r: CollRow) => useOf(use, r).owned - useOf(use, r).copies;
      return (a, b) => spare(b) - spare(a);
    }
    case "EDHREC":
      return (a, b) => (a.info?.r ?? Number.MAX_SAFE_INTEGER) - (b.info?.r ?? Number.MAX_SAFE_INTEGER);
  }
}

/** Biggest rise (or fall) first; cards without a change go last either way. */
function changeCmp(f: SortField, fallersFirst: boolean): Cmp {
  const v = (r: CollRow) => (f === "CHANGE_PCT" ? r.changePct : r.changeEur);
  return (a, b) => {
    const na = v(a) == null ? 1 : 0;
    const nb = v(b) == null ? 1 : 0;
    if (na !== nb) return na - nb;
    const x = v(a) ?? 0;
    const y = v(b) ?? 0;
    return fallersFirst ? x - y : y - x;
  };
}

/** Sorts in layers; the name breaks remaining ties. [use]: the decks' use of each card (for Decks and Spare). */
export function comparator(levels: SortLevel[], use?: Map<string, DeckUse>): Cmp {
  const parts: Cmp[] = levels.map((l) => {
    if (l.field === "CHANGE_PCT" || l.field === "CHANGE_MONEY") return changeCmp(l.field, l.reversed);
    const c = fieldCmp(l.field, use);
    return l.reversed ? (a, b) => c(b, a) : c;
  });
  parts.push((a, b) => nocase(a.card.name, b.card.name));
  return (a, b) => {
    for (const p of parts) {
      const d = p(a, b);
      if (d !== 0) return d;
    }
    return 0;
  };
}

/** The piles as a plain list, "2 Sol Ring (C21 #263, foil)" per line (the app's stacksText). */
export function stacksText(rows: CollRow[]): string {
  return rows
    .map((r) => {
      const extra = [`${r.card.setCode.toUpperCase()} #${r.card.collectorNumber}`];
      if (r.finish !== "NONFOIL") extra.push(finishName(r).toLowerCase());
      return `${r.quantity} ${r.card.name} (${extra.join(", ")})`;
    })
    .join("\n");
}

// ---- Value by price source (the app's sourceTotals) ----

export interface SourceTotal {
  /** null: what Card Kingdom would pay. */
  source: Source | null;
  label: string;
  value: number;
  /** Copies the source has a price for. */
  covered: number;
  copies: number;
}

/** The cards' value at every source (Cardmarket's price for cards a source has none for), and what Card Kingdom pays. */
export function sourceTotals(rows: CollRow[]): SourceTotal[] {
  const copies = rows.reduce((n, r) => n + r.quantity, 0);
  const sources: Source[] = ["cardmarket", "tcgplayer", "cardkingdom"];
  const out: SourceTotal[] = sources.map((s) => ({
    source: s,
    label: SOURCE_LABELS[s],
    value: rows.reduce((n, r) => n + (r.srcEur[s] ?? r.srcEur.cardmarket ?? 0) * r.quantity, 0),
    covered: s === "cardmarket" ? copies : rows.filter((r) => r.srcEur[s] != null).reduce((n, r) => n + r.quantity, 0),
    copies,
  }));
  out.push({
    source: null,
    label: "Card Kingdom would pay",
    value: rows.reduce((n, r) => n + (r.srcEur.ckPays ?? 0) * r.quantity, 0),
    covered: rows.filter((r) => r.srcEur.ckPays != null).reduce((n, r) => n + r.quantity, 0),
    copies,
  });
  return out;
}

// ---- Statistics (CollectionStats.compute) ----

export interface StatEntry {
  key: string;
  label: string;
  copies: number;
  value: number;
  /** The collection filter that shows exactly these cards, or null. */
  filter: Partial<Filter> | null;
  /** Sets only: the copies' latest change (trend against the 30-day average) together; null when none has one. */
  change?: number | null;
}

export interface StatCard {
  row: CollRow;
  note: string;
}

export interface Stats {
  copies: number;
  uniqueCards: number;
  printings: number;
  value: number;
  foilCopies: number;
  unpriced: number;
  withoutDetails: number;
  paid: number;
  paidCopies: number;
  paidWorth: number;
  colors: StatEntry[];
  identities: StatEntry[];
  rarities: StatEntry[];
  types: StatEntry[];
  curve: StatEntry[];
  sets: StatEntry[];
  years: StatEntry[];
  finishes: StatEntry[];
  conditions: StatEntry[];
  languages: StatEntry[];
  binders: StatEntry[];
  inDecks: StatEntry;
  notInDecks: StatEntry;
  gameChangers: StatCard[];
  edhrecTop: StatCard[];
  mostValuable: StatCard[];
  risers: StatCard[];
  fallers: StatCard[];
  oldest: StatCard | null;
}

export const COLOR_GROUPS: [string, string][] = [
  ["W", "White"],
  ["U", "Blue"],
  ["B", "Black"],
  ["R", "Red"],
  ["G", "Green"],
  ["M", "Multicolor"],
  ["C", "Colorless"],
  ["L", "Lands"],
];

export const IDENTITY_NAMES: Record<string, string> = {
  "": "Colorless", W: "Mono-White", U: "Mono-Blue", B: "Mono-Black", R: "Mono-Red", G: "Mono-Green",
  WU: "Azorius", UB: "Dimir", BR: "Rakdos", RG: "Gruul", WG: "Selesnya",
  WB: "Orzhov", UR: "Izzet", BG: "Golgari", WR: "Boros", UG: "Simic",
  WUB: "Esper", UBR: "Grixis", BRG: "Jund", WRG: "Naya", WUG: "Bant",
  WBG: "Abzan", WUR: "Jeskai", UBG: "Sultai", WBR: "Mardu", URG: "Temur",
  UBRG: "Glint-Eye (no white)", WBRG: "Dune-Brood (no blue)", WURG: "Ink-Treader (no black)",
  WUBG: "Witch-Maw (no red)", WUBR: "Yore-Tiller (no green)", WUBRG: "Five colors",
};

const STAT_TYPES = ["Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Battle", "Land"];
const RARITY_ORDER = ["common", "uncommon", "rare", "mythic", "special", "bonus"];

export function computeStats(rows: CollRow[], binderNames: Map<string | null, string>, deckNames: Set<string>): Stats {
  const price = (r: CollRow) => r.unitEur ?? 0;
  const value = (r: CollRow) => price(r) * r.quantity;
  const sumQ = (rs: CollRow[]) => rs.reduce((n, r) => n + r.quantity, 0);
  const sumV = (rs: CollRow[]) => rs.reduce((n, r) => n + value(r), 0);

  function group(
    key: (r: CollRow) => string | null,
    label: (k: string) => string = (k) => k,
    filter: (k: string) => Partial<Filter> | null = () => null,
    order?: string[],
  ): StatEntry[] {
    const m = new Map<string, CollRow[]>();
    for (const r of rows) {
      const k = key(r);
      if (k == null) continue;
      const list = m.get(k);
      if (list) list.push(r);
      else m.set(k, [r]);
    }
    const out = [...m.entries()].map(([k, rs]) => ({ key: k, label: label(k), copies: sumQ(rs), value: sumV(rs), filter: filter(k) }));
    if (order) {
      const pos = (k: string) => (order.indexOf(k) < 0 ? Number.MAX_SAFE_INTEGER : order.indexOf(k));
      return out.sort((a, b) => pos(a.key) - pos(b.key));
    }
    return out.sort((a, b) => b.copies - a.copies);
  }

  const colors = group(
    (r) => {
      const info = r.info;
      if (!info) return null;
      if (info.t.includes("Land") && info.c.length === 0) return "L";
      if (info.c.length === 0) return "C";
      if (info.c.length > 1) return "M";
      return info.c;
    },
    (k) => COLOR_GROUPS.find((g) => g[0] === k)?.[1] ?? k,
    (k): Partial<Filter> =>
      k === "L" ? { types: ["Land"] } : k === "M" ? { colors: ["M"] } : k === "C" ? { colors: ["C"], colorMatch: "EXACT" } : { colors: [k], colorMatch: "EXACT" },
    COLOR_GROUPS.map((g) => g[0]),
  );

  const identities = group(
    (r) => (r.info ? r.info.i : null),
    (k) => IDENTITY_NAMES[k] ?? k,
    (k) => ({ colors: k === "" ? ["C"] : [...k], colorMatch: k === "" ? "EXACT" : "IDENTITY" }),
  );

  const rarities = group(
    (r) => r.card.rarity.toLowerCase() || null,
    (k) => k.charAt(0).toUpperCase() + k.slice(1),
    (k) => ({ rarities: [k] }),
    RARITY_ORDER,
  );

  const types: StatEntry[] = [];
  for (const t of STAT_TYPES) {
    const rs = rows.filter((r) => r.info?.t.includes(t));
    if (rs.length) types.push({ key: t, label: t === "Sorcery" ? "Sorceries" : `${t}s`, copies: sumQ(rs), value: sumV(rs), filter: { types: [t] } });
  }

  const nonLand = rows.filter((r) => r.info && !r.info.t.includes("Land"));
  const curve: StatEntry[] = [];
  for (let mv = 0; mv <= 7; mv++) {
    const rs = nonLand.filter((r) => {
      const c = Math.trunc(r.info!.v);
      return mv === 7 ? c >= 7 : c === mv;
    });
    curve.push({ key: String(mv), label: mv === 7 ? "7+" : String(mv), copies: sumQ(rs), value: sumV(rs), filter: null });
  }

  const setMap = new Map<string, CollRow[]>();
  for (const r of rows) {
    const k = r.card.setCode.toLowerCase();
    setMap.set(k, [...(setMap.get(k) ?? []), r]);
  }
  const sets = [...setMap.entries()].map(([code, rs]) => ({
    key: code,
    label: rs[0].card.setName || code.toUpperCase(),
    copies: sumQ(rs),
    value: sumV(rs),
    filter: { sets: [code] } as Partial<Filter>,
    // ValueHistory.bySet: the sum of the copies' latest changes (null when none of them has one).
    change: rs.some((r) => r.changeEur != null) ? rs.reduce((s, r) => s + (r.changeEur ?? 0) * r.quantity, 0) : null,
  }));

  const years = group((r) => (r.info?.d ? r.info.d.slice(0, 4) : null)).sort((a, b) => (a.key < b.key ? -1 : 1));

  const finishes = group(
    (r) => r.finish,
    (k) => (k === "NONFOIL" ? "Non-foil" : k === "FOIL" ? "Foil" : "Etched"),
    (k) => ({ finishes: [k as Finish] }),
    ["NONFOIL", "FOIL", "ETCHED"],
  );
  const conditions = group(
    (r) => r.condition,
    conditionName,
    (k) => ({ conditions: [k] }),
    ["MT", "NM", "EX", "GD", "LP", "PL", "PO"],
  );
  const languages = group((r) => r.language, languageName, (k) => ({ languages: [k] }));
  const binders = group((r) => r.binder ?? "", (k) => binderNames.get(k === "" ? null : k) ?? "Unsorted").sort((a, b) => b.value - a.value);

  const used = rows.filter((r) => deckNames.has(front(r)));
  const unused = rows.filter((r) => !deckNames.has(front(r)));

  const byName = new Map<string, CollRow[]>();
  for (const r of rows) byName.set(front(r), [...(byName.get(front(r)) ?? []), r]);
  const maxBy = (rs: CollRow[]) => rs.reduce((m, r) => (price(r) > price(m) ? r : m), rs[0]);

  const gameChangers = [...byName.values()]
    .filter((rs) => rs.some((r) => r.info?.g))
    .map((rs) => ({ row: maxBy(rs), note: `${sumQ(rs)}×` }))
    .sort((a, b) => nocase(a.row.card.name, b.row.card.name));
  const edhrecTop = [...byName.values()]
    .map((rs) => ({ rank: rs.find((r) => r.info?.r != null)?.info?.r ?? null, rs }))
    .filter((x): x is { rank: number; rs: CollRow[] } => x.rank != null)
    .sort((a, b) => a.rank - b.rank)
    .slice(0, 10)
    .map(({ rank, rs }) => ({ row: maxBy(rs), note: `#${rank} on EDHREC` }));
  const mostValuable = [...rows].sort((a, b) => price(b) - price(a)).slice(0, 10).map((r) => ({ row: r, note: `${r.quantity}×` }));
  const withChange = rows.filter((r) => r.changeEur != null && r.changePct != null);
  const risers = withChange
    .filter((r) => r.changeEur! > 0)
    .sort((a, b) => b.changeEur! * b.quantity - a.changeEur! * a.quantity)
    .slice(0, 10)
    .map((r) => ({ row: r, note: `▲ ${r.changePct!.toFixed(1)}%` }));
  const fallers = withChange
    .filter((r) => r.changeEur! < 0)
    .sort((a, b) => a.changeEur! * a.quantity - b.changeEur! * b.quantity)
    .slice(0, 10)
    .map((r) => ({ row: r, note: `▼ ${Math.abs(r.changePct!).toFixed(1)}%` }));
  const dated = rows.filter((r) => r.info?.d).sort((a, b) => (a.info!.d < b.info!.d ? -1 : 1));
  const oldest = dated.length ? { row: dated[0], note: dated[0].info!.d.slice(0, 4) } : null;

  const paidRows = rows.filter((r) => r.purchasePrice != null);

  return {
    copies: sumQ(rows),
    uniqueCards: byName.size,
    printings: new Set(rows.map((r) => r.card.scryfallId)).size,
    value: sumV(rows),
    foilCopies: sumQ(rows.filter((r) => r.finish !== "NONFOIL")),
    unpriced: sumQ(rows.filter((r) => r.unitEur == null)),
    withoutDetails: sumQ(rows.filter((r) => r.info == null)),
    paid: paidRows.reduce((n, r) => n + r.purchasePrice! * r.quantity, 0),
    paidCopies: sumQ(paidRows),
    paidWorth: sumV(paidRows),
    colors,
    identities,
    rarities,
    types,
    curve,
    sets,
    years,
    finishes,
    conditions,
    languages,
    binders,
    inDecks: { key: "in", label: "In a deck", copies: sumQ(used), value: sumV(used), filter: { decks: "IN" } },
    notInDecks: { key: "out", label: "In no deck", copies: sumQ(unused), value: sumV(unused), filter: { decks: "OUT" } },
    gameChangers,
    edhrecTop,
    mostValuable,
    risers,
    fallers,
    oldest,
  };
}
