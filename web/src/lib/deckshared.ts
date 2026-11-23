// What the decks pages share between the server and the browser (no server-only code here): the app's deck types,
// scores, bracket tags, grouping and the missing-cards plan. The server side is decks.ts.
import type { HistoryEntry } from "./deckhistory";
import type { RoleCount } from "./deckroles";
import type { Currency } from "./money";
import type { PriceType } from "./prices";
import type { Finish } from "./pricing";
import type { CardRef, Json } from "./syncfile";

/** A deck as the app syncs it (Deck). Fields the website doesn't use are kept as they are. */
export interface Deck {
  archidektId: number;
  name: string;
  owner: string;
  commanders: string;
  commanderScryfallId: string | null;
  artUrl: string | null;
  colorIdentity: string;
  cardCount: number;
  importedAt: number;
  saltId: string | null;
  powerLevel: number | null;
  bracketRealistic: number | null;
  bracketBaseline: number | null;
  saltPercent: number | null;
  archetype: string | null;
  scoredAt: number | null;
  scoreError: string | null;
  archidektUpdatedAt: number | null;
  updatedAt: number;
  edhPowerLevel: number | null;
  edhPowerAt: number | null;
  edhPowerError: string | null;
  saltCard: string | null;
  svPowerLevel: number | null;
  scrollVault: string | null;
  svAt: number | null;
  svError: string | null;
  folderId: number | null;
  folderName: string | null;
  visibility: string | null;
  categoryInfo: string | null;
  origin: string | null;
  /** Retired (app 1.83): kept with its games, out of the groups, the seat pickers, updates and collection checks. */
  retired?: boolean;
  /** The deck's own category order, one name per line (Deck.categoryOrder, app 1.86); the rest alphabetically. */
  categoryOrder?: string | null;
  /** The deck's primer, plain text with light markdown (Deck.primer, app 1.97); null when never set, "" when cleared. */
  primer?: string | null;
  /** Card notes as JSON, `{ "<nameKey>": "<note>" }` (Deck.cardNotes, app 1.97); null when there are none. */
  cardNotes?: string | null;
  /** When the primer or the card notes last changed (Deck.notesAt, app 1.97); null when never. */
  notesAt?: number | null;
  /** The change history as JSON, newest first (Deck.history, app 1.97); null when there is none. */
  history?: string | null;
}

/** One line of a deck (DeckCard). */
export interface DeckCard {
  id: number;
  deckId: number;
  card: CardRef;
  quantity: number;
  category: string;
  types: string;
  cmc: number;
  commander: boolean;
  foil: boolean;
  etched: boolean;
  gameChanger: boolean;
  addedInApp: boolean;
  maybeboard: boolean;
  categories: string;
  archidektCardId: number | null;
  relationId: number | null;
  label: string | null;
  lineUid: string;
}

export interface SyncDeck {
  deck: Deck;
  cards: DeckCard[];
}

// ---- Scores ------------------------------------------------------------------------------------------------------

export type PowerSource = "COMMANDER_SALT" | "EDH_POWER_LEVEL" | "APP";
export const POWER_SOURCES: Record<PowerSource, { label: string; site: string }> = {
  COMMANDER_SALT: { label: "Commander Salt", site: "commandersalt.com" },
  EDH_POWER_LEVEL: { label: "EDH Power Level", site: "edhpowerlevel.com" },
  APP: { label: "This app", site: "the app's win turn" },
};

/** SCROLLVAULT (a stored setting until app 1.104) falls back to Commander Salt. */
export function powerSource(key: string | undefined): PowerSource {
  return key && key in POWER_SOURCES ? (key as PowerSource) : "COMMANDER_SALT";
}

/** The power level from the chosen source; the app's own (from the win turn) isn't synced, so null here. */
export function powerOf(d: Deck, s: PowerSource): number | null {
  switch (s) {
    case "COMMANDER_SALT":
      return d.powerLevel ?? null;
    case "EDH_POWER_LEVEL":
      return d.edhPowerLevel ?? null;
    default:
      return null;
  }
}

export function powerError(d: Deck, s: PowerSource): string | null {
  switch (s) {
    case "COMMANDER_SALT":
      return d.scoreError ?? null;
    case "EDH_POWER_LEVEL":
      return d.edhPowerError ?? null;
    default:
      return null;
  }
}

/** WotC's names for the five Commander brackets. */
export function bracketName(b: number | null | undefined): string | null {
  return b === 1 ? "Exhibition" : b === 2 ? "Core" : b === 3 ? "Upgraded" : b === 4 ? "Optimized" : b === 5 ? "cEDH" : null;
}

/** The bracket shown first (the higher of realistic and baseline) and the other one, labelled; null fields when unscored. */
export function shownBracket(
  realistic: number | null | undefined,
  baseline: number | null | undefined,
): { main: number | null; other: { label: "realistic" | "baseline"; value: number } | null } {
  const r = realistic ?? null;
  const b = baseline ?? null;
  if (r == null && b == null) return { main: null, other: null };
  if (b == null) return { main: r, other: null };
  if (r == null) return { main: b, other: null };
  return b > r ? { main: b, other: { label: "realistic", value: r } } : { main: r, other: { label: "baseline", value: b } };
}

/** A deck's cover: its featured art from Archidekt, or its commander's art. */
export function coverUrl(d: Deck): string | null {
  const id = d.commanderScryfallId;
  return d.artUrl ?? (id && id.length > 2 ? `https://cards.scryfall.io/art_crop/front/${id[0]}/${id[1]}/${id}.jpg` : null);
}

export interface SaltCombo {
  cards: string[];
  counts: boolean;
  results: string[];
  needsBoard: boolean;
  spellbookUri: string | null;
  prerequisites: string[];
  /** The combo's cards' mana values added up; null when not known (Commander Salt doesn't say). */
  manaValue?: number | null;
  /** Whether it wins the game by itself; null when not known. */
  wins?: boolean | null;
}

/** What the rule-zero cards show from Commander Salt (the app's SaltCard JSON, defaults filled in). */
export interface SaltCard {
  /** The app's SaltCard.CURRENT; a salt card without it is old data. null when the text has none. */
  version?: number | null;
  gameChangers: string[];
  twoCardCombos: string[][];
  earlyCombos: string[][];
  extraTurns: string[];
  massLandDenial: string[];
  axes: Record<string, number>;
  notes: { label: string; why: string }[];
  playStyle: string | null;
  saltIntensity: string | null;
  saltDriver: string | null;
  manaFixing: number | null;
  onCurve: number | null;
  manaQuality: number | null;
  effects: Record<string, number>;
  wincons: string[];
  tutors: string[];
  fastMana: string[];
  combos: SaltCombo[];
  /** Who scored it: "app" for the website's own local-deck rating (localratings.ts); null or missing for Commander Salt. */
  source?: "app" | null;
  /** The bracket the website's rating gives (2 to 4); null when not rated by the app. */
  bracket?: number | null;
  /** Why that bracket, one line per rule that reached it (localratings.ts). */
  bracketWhy?: string[];
  /** The deck's salt total, from the cards' salt; null when no card has one. */
  salt?: number | null;
  /** The saltiest cards, highest first. */
  saltiest?: { name: string; salt: number }[];
  /** The list the rating was made for (ratingListKey); null when not rated by the app. */
  listKey?: string | null;
  /** The rating's version (RATING_VERSION), so a changed formula can be spotted. */
  ratingVersion?: number | null;
}

export function decodeSalt(text: string | null | undefined): SaltCard | null {
  if (!text) return null;
  try {
    const j = JSON.parse(text) as Partial<SaltCard>;
    return {
      version: j.version ?? null,
      gameChangers: j.gameChangers ?? [],
      twoCardCombos: j.twoCardCombos ?? [],
      earlyCombos: j.earlyCombos ?? [],
      extraTurns: j.extraTurns ?? [],
      massLandDenial: j.massLandDenial ?? [],
      axes: j.axes ?? {},
      notes: (j.notes ?? []).map((n) => ({ label: n.label ?? "", why: n.why ?? "" })),
      playStyle: j.playStyle ?? null,
      saltIntensity: j.saltIntensity ?? null,
      saltDriver: j.saltDriver ?? null,
      manaFixing: j.manaFixing ?? null,
      onCurve: j.onCurve ?? null,
      manaQuality: j.manaQuality ?? null,
      effects: j.effects ?? {},
      wincons: j.wincons ?? [],
      tutors: j.tutors ?? [],
      fastMana: j.fastMana ?? [],
      combos: (j.combos ?? []).map((c) => ({
        cards: c.cards ?? [],
        counts: c.counts ?? false,
        results: c.results ?? [],
        needsBoard: c.needsBoard ?? false,
        spellbookUri: c.spellbookUri ?? null,
        prerequisites: c.prerequisites ?? [],
        manaValue: c.manaValue ?? null,
        wins: c.wins ?? null,
      })),
      source: j.source === "app" ? "app" : null,
      bracket: j.bracket ?? null,
      bracketWhy: j.bracketWhy ?? [],
      salt: j.salt ?? null,
      saltiest: (j.saltiest ?? []).map((s) => ({ name: s.name ?? "", salt: s.salt ?? 0 })),
      listKey: j.listKey ?? null,
      ratingVersion: j.ratingVersion ?? null,
    };
  } catch {
    return null;
  }
}

/** Cards are matched by their front face, case-insensitively (SaltCard.key). */
export function saltKey(name: string): string {
  return name.split(" // ")[0].trim().toLowerCase();
}

/** What a combo does, in words ("infinite ETB, infinite life loss"; SaltCombo.resultText). */
export function comboResultText(c: SaltCombo): string {
  return c.results
    .map((r) =>
      r === "win-game"
        ? "wins the game"
        : r === "opponents-locked"
          ? "locks opponents"
          : r
              .split("-")
              .map((w) => (w === "etb" || w === "ltb" ? w.toUpperCase() : w === "lifeloss" ? "life loss" : w === "lifegain" ? "life gain" : w))
              .join(" "),
    )
    .join(", ");
}

/** Every combo, those that count for the bracket first (SaltCard.allCombos). */
export function allCombos(s: SaltCard): SaltCombo[] {
  const seen = new Set<string>();
  const counted: string[][] = [];
  for (const c of [...s.twoCardCombos, ...s.earlyCombos]) {
    const k = JSON.stringify(c);
    if (!seen.has(k)) {
      seen.add(k);
      counted.push(c);
    }
  }
  const setKey = (cards: string[]) => [...new Set(cards.map(saltKey))].sort().join("|");
  const known = new Set(s.combos.map((c) => setKey(c.cards)));
  const all = [
    ...s.combos,
    ...counted.filter((c) => !known.has(setKey(c))).map((cards) => ({ cards, counts: true, results: [], needsBoard: false, spellbookUri: null, prerequisites: [] })),
  ];
  return all.map((c, i) => ({ c, i })).sort((a, b) => Number(b.c.counts) - Number(a.c.counts) || a.c.cards.length - b.c.cards.length || a.i - b.i).map((x) => x.c);
}

// ---- Why that bracket ----------------------------------------------------------------------------------------------

/** The combo's cards, lowercased and sorted: how a combo is matched against SaltCard.combos. */
const comboKey = (cards: string[]) => [...new Set(cards.map(saltKey))].sort().join("|");

/** A mana value as it is written: a whole number without decimals ("6", "5.5"). */
const manaText = (mv: number) => String(mv);

/** The first three combos as lines, then "and <k> more …" (only when the third isn't the last). */
function atMostThree(combos: string[][], line: (cards: string[]) => string, more: string): string[] {
  const shown = combos.slice(0, 3).map(line);
  if (combos.length > 3) shown.push(`and ${combos.length - 3} more ${more}`);
  return shown;
}

/** "1 Game Changer: X" or "N Game Changers: X, Y". */
const gameChangerLine = (names: string[]) => (names.length === 1 ? `1 Game Changer: ${names[0]}` : `${names.length} Game Changers: ${names.join(", ")}`);

/**
 * Why the bracket card shows [bracket]: the rating's own lines when it has them, else worked out from Commander
 * Salt's lists in localratings.ts's words. Empty when there's no bracket or nothing to say.
 */
export function bracketReasons(data: SaltCard, bracket: number | null): string[] {
  const own = (data.bracketWhy ?? []).map((l) => l.trim()).filter(Boolean);
  if (own.length > 0) return own;
  if (bracket == null) return [];
  if (bracket <= 2) return ["No Game Changers or two-card combos"];
  const out: string[] = [];
  if (bracket === 4) {
    // Commander Salt's own early combos: their mana value when the rating knows it.
    const earlyLine = (cards: string[]) => {
      const c = comboData(data, cards);
      return c?.manaValue != null ? `${cards.join(" + ")}: wins, mana value ${manaText(c.manaValue)}` : cards.join(" + ");
    };
    out.push(...atMostThree(data.earlyCombos, earlyLine, "early combos"));
    if (data.gameChangers.length > 0) out.push(gameChangerLine(data.gameChangers));
    if (data.massLandDenial.length > 0) out.push(`Mass land denial: ${data.massLandDenial.join(", ")}`);
    if (data.extraTurns.length >= 3) out.push(`${data.extraTurns.length} extra-turn cards: ${data.extraTurns.join(", ")}`);
  } else {
    // Commander Salt's two-card combos: whether they win and their mana value when the rating knows them.
    const twoCardLine = (cards: string[]) => {
      const c = comboData(data, cards);
      if (c?.wins === true && c.manaValue != null) return `${cards.join(" + ")}: wins, mana value ${manaText(c.manaValue)}`;
      if (c?.wins === false) return `${cards.join(" + ")}: doesn't win`;
      return cards.join(" + ");
    };
    out.push(...atMostThree(data.twoCardCombos, twoCardLine, "two-card combos"));
    if (data.gameChangers.length > 0) out.push(gameChangerLine(data.gameChangers));
    if (data.tutors.length >= 5) out.push(`${data.tutors.length} tutors`);
  }
  return out;
}

/** What a combo's own data adds to its line on the bracket card ("wins, mana value 5"; null when the rating didn't know). */
export function comboNote(c: SaltCombo): string | null {
  if (c.wins == null && c.manaValue == null) return null;
  const mv = c.manaValue != null ? `mana value ${c.manaValue}` : null;
  if (c.wins === true) return mv ? `wins · ${mv}` : "wins";
  return mv;
}

/** The combo's entry in SaltCard.combos (matched by the set of its card names). */
export function comboData(data: SaltCard, cards: string[]): SaltCombo | null {
  const k = comboKey(cards);
  return data.combos.find((c) => comboKey(c.cards) === k) ?? null;
}

export type BracketTag = "GAME_CHANGER" | "COMBO" | "COMBO_3" | "EXTRA_TURN" | "LAND_DENIAL" | "OTHER_COMBO" | "OTHER_COMBO_3" | "TUTOR" | "FAST_MANA";
const TAG_ORDER: BracketTag[] = ["GAME_CHANGER", "COMBO", "COMBO_3", "EXTRA_TURN", "LAND_DENIAL", "OTHER_COMBO", "OTHER_COMBO_3", "TUTOR", "FAST_MANA"];
export const TAG_LABELS: Record<BracketTag, { label: string; strict: boolean }> = {
  GAME_CHANGER: { label: "GAME CHANGER", strict: true },
  COMBO: { label: "COMBO 2", strict: true },
  COMBO_3: { label: "COMBO 3+", strict: true },
  EXTRA_TURN: { label: "EXTRA TURN", strict: true },
  LAND_DENIAL: { label: "LAND DENIAL", strict: true },
  OTHER_COMBO: { label: "COMBO 2", strict: false },
  OTHER_COMBO_3: { label: "COMBO 3+", strict: false },
  TUTOR: { label: "TUTOR", strict: false },
  FAST_MANA: { label: "FAST MANA", strict: false },
};

/** The bracket-relevant tags of each card, by saltKey (SaltCard.tagsByCard). */
export function tagsByCard(s: SaltCard): Map<string, BracketTag[]> {
  const m = new Map<string, Set<BracketTag>>();
  const tag = (names: string[], t: BracketTag) => {
    for (const n of names) {
      const k = saltKey(n);
      if (!m.has(k)) m.set(k, new Set());
      m.get(k)!.add(t);
    }
  };
  tag(s.gameChangers, "GAME_CHANGER");
  for (const c of allCombos(s)) tag(c.cards, c.counts ? (c.cards.length <= 2 ? "COMBO" : "COMBO_3") : c.cards.length <= 2 ? "OTHER_COMBO" : "OTHER_COMBO_3");
  for (const t of m.values()) {
    if (t.has("COMBO")) t.delete("COMBO_3");
    if (t.has("OTHER_COMBO")) t.delete("OTHER_COMBO_3");
  }
  tag(s.extraTurns, "EXTRA_TURN");
  tag(s.massLandDenial, "LAND_DENIAL");
  tag(s.tutors, "TUTOR");
  tag(s.fastMana, "FAST_MANA");
  const out = new Map<string, BracketTag[]>();
  for (const [k, v] of m) out.set(k, TAG_ORDER.filter((t) => v.has(t)));
  return out;
}

// ---- Grouping ----------------------------------------------------------------------------------------------------

/** Order of type sections, as deckbuilders list them. A card goes under the first type it has. */
export const TYPE_ORDER = ["Creature", "Planeswalker", "Battle", "Land", "Instant", "Sorcery", "Artifact", "Enchantment", "Kindred"];
const PLURAL: Record<string, string> = {
  Creature: "Creatures",
  Planeswalker: "Planeswalkers",
  Battle: "Battles",
  Land: "Lands",
  Instant: "Instants",
  Sorcery: "Sorceries",
  Artifact: "Artifacts",
  Enchantment: "Enchantments",
  Kindred: "Kindred",
  Other: "Other",
};

export function primaryType(types: string): string {
  const words = types.split(" ");
  return TYPE_ORDER.find((t) => words.includes(t)) ?? "Other";
}

/** Kotlin's String.CASE_INSENSITIVE_ORDER for these names. */
export function caseless(a: string, b: string): number {
  const x = a.toLowerCase();
  const y = b.toLowerCase();
  return x < y ? -1 : x > y ? 1 : 0;
}

export type GroupBy = "CATEGORY" | "TYPE";

export interface Section<T> {
  title: string;
  rows: T[];
}

/** Commander(s) first, then the categories alphabetically or the card types in TYPE_ORDER; cards by name (DeckGrouping.sections). */
export function sections<T extends { name: string; commander: boolean; category: string; types: string }>(rows: T[], by: GroupBy): Section<T>[] {
  const commanders = rows.filter((r) => r.commander);
  const rest = rows.filter((r) => !r.commander);
  const byName = (a: T, b: T) => caseless(a.name, b.name);
  const groups = new Map<string, T[]>();
  for (const r of rest) {
    const k = by === "CATEGORY" ? r.category : primaryType(r.types);
    if (!groups.has(k)) groups.set(k, []);
    groups.get(k)!.push(r);
  }
  let list: [string, T[]][];
  if (by === "CATEGORY") {
    // A TreeMap with CASE_INSENSITIVE_ORDER: categories differing only in case end up together, under the first name.
    const merged = new Map<string, [string, T[]]>();
    for (const [k, v] of groups) {
      const low = k.toLowerCase();
      if (merged.has(low)) merged.get(low)![1].push(...v);
      else merged.set(low, [k, [...v]]);
    }
    list = [...merged.values()].sort((a, b) => caseless(a[0], b[0]));
  } else {
    const pos = (t: string) => (TYPE_ORDER.indexOf(t) < 0 ? TYPE_ORDER.length : TYPE_ORDER.indexOf(t));
    list = [...groups.entries()].sort((a, b) => pos(a[0]) - pos(b[0])).map(([t, l]) => [PLURAL[t] ?? t, l]);
  }
  const out: Section<T>[] = [];
  if (commanders.length > 0) out.push({ title: commanders.length > 1 ? "Commanders" : "Commander", rows: [...commanders].sort(byName) });
  for (const [title, l] of list) out.push({ title, rows: [...l].sort(byName) });
  return out;
}

// ---- Owned and missing -------------------------------------------------------------------------------------------

const BASIC_NAMES = new Set(
  ["Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes"].flatMap((b) => [b, `Snow-Covered ${b}`]).map((n) => n.toLowerCase()),
);

export function isBasicName(name: string): boolean {
  return BASIC_NAMES.has(name.toLowerCase());
}

/**
 * How many copies of each line are missing (DeckToCollection.plan with onlyMissing): copies you own in any printing
 * count once, across the deck's lines, in the order given.
 */
export function missingPlan<T extends { name: string; quantity: number }>(lines: T[], owned: Map<string, number>, skipBasics: boolean): [T, number][] {
  const left = new Map(owned);
  const out: [T, number][] = [];
  for (const c of lines) {
    if (skipBasics && isBasicName(c.name)) continue;
    const key = c.name.toLowerCase();
    const have = left.get(key) ?? 0;
    const used = Math.min(have, c.quantity);
    left.set(key, have - used);
    const n = c.quantity - used;
    if (n > 0) out.push([c, n]);
  }
  return out;
}

// ---- The views ---------------------------------------------------------------------------------------------------

export type DeckGroup = "OWN" | "OTHERS" | "LOCAL" | "RETIRED";
export const GROUP_TITLES: Record<DeckGroup, string> = {
  OWN: "Archidekt",
  OTHERS: "Archidekt (other users)",
  LOCAL: "MTG Trader",
  RETIRED: "Retired",
};

export interface DeckSummary {
  id: number;
  name: string;
  owner: string;
  commanders: string;
  cover: string | null;
  colorIdentity: string;
  cardCount: number;
  group: DeckGroup;
  /** Recorded games it played (filled in by /api/decks). */
  games?: number;
  /** Commander legality problems found (filled in by /api/decks); null while some cards' rules aren't known. */
  legalProblems?: number | null;
  folder: string | null;
  origin: string | null;
  bracketRealistic: number | null;
  bracketBaseline: number | null;
  power: number | null;
  saltPercent: number | null;
  archetype: string | null;
  archidektUpdatedAt: number | null;
  /** For the "Last modified" sort: Archidekt's date for an Archidekt deck, the last change anywhere for a local one (the app's Deck.modifiedAt). */
  modifiedAt: number | null;
  /** The deck's cards at their printings, in euros (maybeboard left out). */
  valueEur: number;
  /** Copies missing from the collection (any printing counts; basics left out). */
  missing: number;
  /** The list's fingerprint for recommendations (recListKey). */
  recKey: string;
}

export interface DecksView {
  decks: DeckSummary[];
  currency: Currency;
  perEuro: number;
  powerSource: PowerSource;
  powerLabel: string;
  sort: string;
  sortReversed: boolean;
  readOnly: boolean;
  /** Your Archidekt username (Settings), to import your decks from. */
  archidektUser: string | null;
}

/** One pile of a card in the collection (a printing, finish, condition and binder). */
export interface HeldPile {
  scryfallId: string;
  setCode: string;
  setName: string;
  collectorNumber: string;
  finish: Finish;
  condition: string;
  /** The binder's name; "Unsorted" when it has none. */
  binder: string;
  quantity: number;
}

/** Another deck that has the card: its copies in the main deck, or on its maybeboard. */
export interface DeckUse {
  deckId: number;
  deckName: string;
  quantity: number;
  maybeboard: boolean;
}

/** One line on the deck page. */
export interface DeckRow {
  lineUid: string;
  name: string;
  /** The printing shown: one you own when "your printings" is on (DeckPrintings), else the deck's. */
  card: CardRef;
  /** The deck's own printing, when another one is shown. */
  original: CardRef | null;
  finish: Finish;
  quantity: number;
  category: string;
  categories: string[];
  types: string;
  cmc: number;
  commander: boolean;
  gameChanger: boolean;
  addedInApp: boolean;
  maybeboard: boolean;
  unitEur: number | null;
  /** Copies owned in any printing. */
  owned: number;
  /** Copies owned of the printing and finish shown on the line (the same Scryfall id and finish). */
  exact: number;
  /** Every pile of the card in the collection, for the "Collection tracker" box; empty for basics. */
  held: HeldPile[];
  /** The other decks that have the card (by name; basics left out), in deck-name order. */
  otherDecks: DeckUse[];
  /** Copies this line still needs (any printing counts, shared across lines); 0 for basics. */
  missing: number;
  tags: BracketTag[];
  /** The printings you own of the card, to pick the one shown ("scryfallId|FINISH"); empty for basics and the maybeboard. */
  choices: { value: string; label: string; quantity: number }[];
  /** The pick for this card: a choice's value, "archidekt" for the deck's own printing, or null to let the site choose. */
  picked: string | null;
  /** The card's role tags (DeckRoles.tagsOf), for the role line and the deck search's tag: key. */
  roleTags: string[];
  /** The card's note (Deck.cardNotes), or null. */
  note: string | null;
}

/** A category of the deck: from its lines and its categoryInfo (which keeps empty ones and those left out). */
export interface DeckCategory {
  name: string;
  /** False for categories left out of the deck (the maybeboard, a sideboard…). */
  included: boolean;
  cards: number;
}

export interface DeckView {
  id: number;
  local: boolean;
  deck: Omit<Deck, "saltCard" | "scrollVault" | "categoryInfo">;
  cover: string | null;
  group: DeckGroup;
  /** When the deck last changed, as the list's "Last modified" sort has it (the app's Deck.modifiedAt); null when unknown. */
  modifiedAt: number | null;
  salt: SaltCard | null;
  rows: DeckRow[];
  /** Lower-case names of the categories switched off in "Manage categories" (synced). */
  hidden: string[];
  /** The printing picked for each of its tokens (the token's key → Scryfall id; synced as deckTokenPicks). */
  tokenPicks: Record<string, string>;
  categories: DeckCategory[];
  /** The deck's primer (plain text), or null. */
  primer: string | null;
  /** Its change history, newest first. */
  history: HistoryEntry[];
  /** Lands and the roles under the deck header (DeckRoles.counts). */
  roles: RoleCount[];
  /** Whether the deck is shown in the printings you own (the app's "Show in my printings"). */
  myPrintings: boolean;
  currency: Currency;
  perEuro: number;
  priceType: PriceType;
  priceTypeLabel: string;
  powerSource: PowerSource;
  power: number | null;
  powerError: string | null;
  readOnly: boolean;
  /** The list's fingerprint for recommendations (recListKey). */
  recKey: string;
}
