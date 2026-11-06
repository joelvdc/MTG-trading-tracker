// What the decks pages share between the server and the browser (no server-only code here): the app's deck types,
// scores, bracket tags, grouping and the missing-cards plan. The server side is decks.ts.
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

export type PowerSource = "COMMANDER_SALT" | "EDH_POWER_LEVEL" | "SCROLLVAULT" | "APP";
export const POWER_SOURCES: Record<PowerSource, { label: string; site: string }> = {
  COMMANDER_SALT: { label: "Commander Salt", site: "commandersalt.com" },
  EDH_POWER_LEVEL: { label: "EDH Power Level", site: "edhpowerlevel.com" },
  SCROLLVAULT: { label: "ScrollVault", site: "scrollvault.net" },
  APP: { label: "This app", site: "the app's win turn" },
};

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
    case "SCROLLVAULT":
      return d.svPowerLevel ?? null;
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
    case "SCROLLVAULT":
      return d.svError ?? null;
    default:
      return null;
  }
}

/** WotC's names for the five Commander brackets. */
export function bracketName(b: number | null | undefined): string | null {
  return b === 1 ? "Exhibition" : b === 2 ? "Core" : b === 3 ? "Upgraded" : b === 4 ? "Optimized" : b === 5 ? "cEDH" : null;
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
}

/** What the rule-zero cards show from Commander Salt (the app's SaltCard JSON, defaults filled in). */
export interface SaltCard {
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
}

export function decodeSalt(text: string | null | undefined): SaltCard | null {
  if (!text) return null;
  try {
    const j = JSON.parse(text) as Partial<SaltCard>;
    return {
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
      })),
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

export type DeckGroup = "OWN" | "OTHERS" | "LOCAL";
export const GROUP_TITLES: Record<DeckGroup, string> = {
  OWN: "Archidekt",
  OTHERS: "Archidekt (read only)",
  LOCAL: "Local · in the app, synced between your phones",
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
  folder: string | null;
  origin: string | null;
  bracketRealistic: number | null;
  bracketBaseline: number | null;
  power: number | null;
  saltPercent: number | null;
  archetype: string | null;
  archidektUpdatedAt: number | null;
  /** The deck's cards at their printings, in euros (maybeboard left out). */
  valueEur: number;
  /** Copies missing from the collection (any printing counts; basics left out). */
  missing: number;
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
  /** Copies this line still needs (any printing counts, shared across lines); 0 for basics. */
  missing: number;
  tags: BracketTag[];
  /** The printings you own of the card, to pick the one shown ("scryfallId|FINISH"); empty for basics and the maybeboard. */
  choices: { value: string; label: string; quantity: number }[];
  /** The pick for this card: a choice's value, "archidekt" for the deck's own printing, or null to let the site choose. */
  picked: string | null;
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
  salt: SaltCard | null;
  scrollVault: Record<string, Json> | null;
  rows: DeckRow[];
  /** Lower-case names of the categories switched off in "Manage categories" (synced). */
  hidden: string[];
  /** The printing picked for each of its tokens (the token's key → Scryfall id; synced as deckTokenPicks). */
  tokenPicks: Record<string, string>;
  categories: DeckCategory[];
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
}
