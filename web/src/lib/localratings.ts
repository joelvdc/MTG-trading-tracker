// The website's own rating of a deck made in the app ("local decks"), which Commander Salt can't score: the same kind
// of rule-zero card it makes (SaltCard, deckshared.ts) — brackets with their reasons, the effects, the combos and the
// salt. Pure functions: the caller fetches the facts and the combos (spellbook.ts) and calls rateDeck. Every tunable
// number is in RATING, so the rules can be tuned without touching the logic.
//
// The four axes (interaction, consistency, efficiency, manabase) were tried and didn't predict the power level, so
// they're no longer worked out: axes stays empty and the "how it plays" bars stay hidden.
import { isBasic, type StatsCard } from "./deckstats";
import { saltKey, type SaltCard, type SaltCombo } from "./deckshared";
import type { SpellbookCombo } from "./spellbook";

export const RATING_VERSION = 1;

export const RATING = {
  bracket: { tutorsFor3: 5, extraTurnsFor4: 3, gcFor4: 4, earlyMv: 8 },
  salt: { mild: 10, moderate: 20, spicy: 35 }, // deck total below → "Mild", "Moderate", "Spicy", else "Salty"
};

/** What the deck's cards are known to be (Scryfall's tags, Game Changer, salt; accounts.ts keeps the same shape). */
export interface CardFact {
  tags: string[];
  gameChanger: boolean;
  salt: number | null;
}

// Mass land denial in the bracket's sense: a card that destroys lands, not a planeswalker tagged for it (Liliana,
// Dreadhorde General).
const MLD_EXCLUDED = /Planeswalker/;
// Infinite damage, life loss, mill, turns, mana, life, lifegain or creature tokens win the game; a hyphenated word may
// come before the result ("Infinite self-mill").
const WINS =
  /win the game|lose the game|infinite (?:[\w-]+ )*(?:[\w-]+-)?(?:damage|life ?loss|mill|turns?|combat|mana|life|lifegain|creature tokens?)\b/i;

const round1 = (x: number) => Math.round(x * 10) / 10;
const typeLine = (c: StatsCard) => c.typeLine ?? c.types;

/** The card's fact (a card missing from the map has none). */
function factOf(c: StatsCard, facts: Map<string, CardFact>): CardFact {
  return facts.get(c.name) ?? { tags: [], gameChanger: false, salt: null };
}

function has(c: StatsCard, tag: string, facts: Map<string, CardFact>): boolean {
  return factOf(c, facts).tags.includes(tag);
}

const fastManaCopy = (c: StatsCard, facts: Map<string, CardFact>) => c.cmc <= 1 && (has(c, "mana-rock", facts) || has(c, "mana-dork", facts));

/** A card that denies lands for the bracket: everything tagged for it except planeswalkers. */
const mldCopy = (c: StatsCard, facts: Map<string, CardFact>) => has(c, "mass-land-denial", facts) && !MLD_EXCLUDED.test(typeLine(c));

// ---- Combos -------------------------------------------------------------------------------------------------------

/** The words of a feature name, lowercased, one per hyphen ("Infinite colorless mana" → ["infinite", "colorless", "mana"]). */
const words = (name: string) => name.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);

// One label per feature, the first match winning, as Commander Salt's results read them.
const RESULT_LABELS: [RegExp, string][] = [
  [/^win the game$/i, "win-game"],
  [/creature tokens?/i, "infinite-tokens"],
  [/damage/i, "infinite-damage"],
  [/enters? the battlefield|(?<![\w-])ETB\b/i, "infinite-etb"],
  [/death|dies|(?<![\w-])LTB\b/i, "infinite-ltb"],
];

/** The feature's label: its words hyphenated as they are ("Infinite lifeloss" → infinite-lifeloss …). */
export function featureLabel(name: string): string {
  const w = words(name);
  const known = RESULT_LABELS.find(([re]) => re.test(name.trim()));
  return known ? known[1] : w.join("-");
}

/** The labels a combo's features give it, each once, in the order the features come. */
export function comboResults(combo: SpellbookCombo): string[] {
  return [...new Set(combo.features.map(featureLabel).filter((l) => l))];
}

/** Whether a combo's features win the game by themselves. */
export function comboWins(combo: SpellbookCombo): boolean {
  return combo.features.some((f) => WINS.test(f));
}

const lines = (text: string) =>
  text
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l);

function saltCombo(combo: SpellbookCombo, cmc: Map<string, number>): SaltCombo {
  return {
    cards: combo.cards,
    counts: combo.cards.length === 2,
    results: comboResults(combo),
    needsBoard: false,
    spellbookUri: `https://commanderspellbook.com/combo/${combo.id}/`,
    prerequisites: [...lines(combo.notablePrerequisites), ...lines(combo.easyPrerequisites)],
    manaValue: combo.cards.reduce((s, n) => s + (cmc.get(saltKey(n)) ?? 0), 0),
    wins: comboWins(combo),
  };
}

// ---- Bracket ------------------------------------------------------------------------------------------------------

const comboName = (c: SaltCombo) => c.cards.join(" + ");
const mv = (c: SaltCombo) => c.manaValue ?? 0;
const earlyLine = (c: SaltCombo) => `${comboName(c)}: wins, mana value ${mv(c)}`;
const comboLine = (c: SaltCombo) => (c.wins ? `${comboName(c)}: wins, mana value ${mv(c)}` : `${comboName(c)}: doesn't win`);

/** The first three lines, then "and <k> more …" (only when the third isn't the last). */
function atMostThree(combos: SaltCombo[], line: (c: SaltCombo) => string, more: string): string[] {
  const shown = combos.slice(0, 3).map(line);
  if (combos.length > 3) shown.push(`and ${combos.length - 3} more ${more}`);
  return shown;
}

interface BracketPlan {
  bracket: number;
  /** The lines of the rules that reached it, strongest first. */
  why: string[];
}

function bracketOf(
  all: SaltCombo[],
  gameChangers: string[],
  mld: string[],
  extraTurns: string[],
  tutors: number,
): BracketPlan {
  const b = RATING.bracket;
  const early = all.filter((c) => c.counts && c.wins === true && c.manaValue != null && c.manaValue <= b.earlyMv);
  const twoCard = all.filter((c) => c.counts);
  let bracket = 2;
  if (gameChangers.length >= b.gcFor4) bracket = Math.max(bracket, 4);
  else if (gameChangers.length >= 1) bracket = Math.max(bracket, 3);
  if (early.length > 0) bracket = Math.max(bracket, 4);
  else if (twoCard.length > 0) bracket = Math.max(bracket, 3);
  if (mld.length > 0) bracket = Math.max(bracket, 4);
  if (extraTurns.length >= b.extraTurnsFor4) bracket = Math.max(bracket, 4);
  if (tutors >= b.tutorsFor3) bracket = Math.max(bracket, 3);
  bracket = Math.min(4, Math.max(2, bracket));

  const why: string[] = [];
  if (bracket < 3) return { bracket, why: ["No Game Changers or two-card combos"] };
  if (bracket === 4) {
    if (early.length > 0) why.push(...atMostThree(early, earlyLine, "early combos"));
    if (gameChangers.length > 0) {
      why.push(
        gameChangers.length === 1 ? `1 Game Changer: ${gameChangers[0]}` : `${gameChangers.length} Game Changers: ${gameChangers.join(", ")}`,
      );
    }
    if (mld.length > 0) why.push(`Mass land denial: ${mld.join(", ")}`);
    if (extraTurns.length >= b.extraTurnsFor4) why.push(`${extraTurns.length} extra-turn cards: ${extraTurns.join(", ")}`);
  } else {
    if (twoCard.length > 0) why.push(...atMostThree(twoCard, comboLine, "two-card combos"));
    if (gameChangers.length > 0) why.push(`${gameChangers.length} Game Changer${gameChangers.length === 1 ? "" : "s"}: ${gameChangers.join(", ")}`);
    if (tutors >= b.tutorsFor3) why.push(`${tutors} tutors`);
  }
  return { bracket, why };
}

// ---- Salt ---------------------------------------------------------------------------------------------------------

function saltOf(cards: StatsCard[], facts: Map<string, CardFact>): { total: number | null; saltiest: { name: string; salt: number }[] } {
  let total: number | null = null;
  const rated: { name: string; salt: number }[] = [];
  for (const c of cards) {
    const s = factOf(c, facts).salt;
    if (s === null || isBasic(c)) continue;
    total = (total ?? 0) + c.quantity * s;
    rated.push({ name: c.name, salt: s });
  }
  if (total === null) return { total: null, saltiest: [] };
  const saltiest = rated.sort((a, b) => b.salt - a.salt || (a.name < b.name ? -1 : a.name > b.name ? 1 : 0)).slice(0, 5);
  return { total: round1(total), saltiest };
}

export function saltIntensity(total: number | null): string | null {
  if (total === null) return null;
  const t = RATING.salt;
  return total < t.mild ? "Mild" : total < t.moderate ? "Moderate" : total < t.spicy ? "Spicy" : "Salty";
}

// ---- The list key -------------------------------------------------------------------------------------------------

/** A short stable fingerprint of a deck's list (its cards with quantities, sorted), for "rate this list". */
export function ratingListKey(cards: StatsCard[]): string {
  const text = cards
    .map((c) => `${c.quantity} ${saltKey(c.name).replace(/\s+/g, " ")}${c.commander ? "*" : ""}`)
    .sort()
    .join("\n");
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h.toString(16).padStart(8, "0");
}

// ---- The rating ---------------------------------------------------------------------------------------------------

/** The deck's rating as the app's SaltCard, with the website's own fields (source, bracket, salt…). */
export function rateDeck(cards: StatsCard[], facts: Map<string, CardFact>, combos: SpellbookCombo[], identity: string | null): SaltCard {
  void identity; // Kept for the caller's sake: the axes were the only thing that needed it.
  const nameOf = (c: StatsCard) => c.name;
  const tagged = (tag: string) => cards.filter((c) => has(c, tag, facts)).map(nameOf);

  const gameChangers = cards.filter((c) => factOf(c, facts).gameChanger).map(nameOf).sort();
  const extraTurns = tagged("extra-turn");
  const massLandDenial = cards.filter((c) => mldCopy(c, facts)).map(nameOf);
  const tutors = cards.filter((c) => has(c, "tutor", facts) && !has(c, "ramp", facts)).map(nameOf);
  const fastMana = cards.filter((c) => fastManaCopy(c, facts)).map(nameOf);

  const copies = (tag: string, keep: (c: StatsCard) => boolean = () => true) =>
    cards.filter((c) => keep(c) && has(c, tag, facts)).reduce((s, c) => s + c.quantity, 0);
  const counters = copies("counterspell");
  const spotRemoval = copies("removal", (c) => !has(c, "sweeper", facts));
  const boardWipes = copies("sweeper");
  const extraTurnCopies = copies("extra-turn");
  const mldCopies = cards.filter((c) => mldCopy(c, facts)).reduce((s, c) => s + c.quantity, 0);
  const tutorCopies = cards.filter((c) => has(c, "tutor", facts) && !has(c, "ramp", facts)).reduce((s, c) => s + c.quantity, 0);
  const fastManaCopies = cards.filter((c) => fastManaCopy(c, facts)).reduce((s, c) => s + c.quantity, 0);

  const cmc = new Map<string, number>();
  for (const c of cards) {
    const k = saltKey(c.name);
    if (!cmc.has(k)) cmc.set(k, c.cmc);
  }
  const all = combos.map((c) => saltCombo(c, cmc)).sort((a, b) => Number(b.counts) - Number(a.counts) || b.cards.length - a.cards.length);
  const twoCardCombos = all.filter((c) => c.counts).map((c) => c.cards);
  const earlyCombos = all
    .filter((c) => c.counts && c.wins === true && c.manaValue != null && c.manaValue <= RATING.bracket.earlyMv)
    .map((c) => c.cards);
  const winningTwo = all.filter((c) => c.counts && c.wins === true);

  const { bracket, why } = bracketOf(all, gameChangers, massLandDenial, extraTurns, tutorCopies);

  const effects: Record<string, number> = {};
  const put = (key: string, n: number) => {
    if (n > 0) effects[key] = n;
  };
  put("fastmana", fastManaCopies);
  put("tutors", tutorCopies);
  put("counters", counters);
  put("spotRemoval", spotRemoval);
  put("boardWipes", boardWipes);
  put("extraTurns", extraTurnCopies);
  put("mld", mldCopies);
  put("combos", winningTwo.length);

  const salt = saltOf(cards, facts);

  return {
    gameChangers,
    twoCardCombos,
    earlyCombos,
    extraTurns,
    massLandDenial,
    axes: {},
    notes: [],
    playStyle: null,
    saltIntensity: saltIntensity(salt.total),
    saltDriver: salt.saltiest[0]?.name ?? null,
    manaFixing: null,
    onCurve: null,
    manaQuality: null,
    effects,
    wincons: [],
    tutors,
    fastMana,
    combos: all,
    source: "app",
    bracket,
    bracketWhy: why,
    salt: salt.total,
    saltiest: salt.saltiest,
    listKey: ratingListKey(cards),
    ratingVersion: RATING_VERSION,
  };
}
