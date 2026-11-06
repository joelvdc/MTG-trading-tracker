// When the deck can cast its commander and has its colours: the app's ManaOddsCalc (app: data/ManaOdds.kt), line for
// line, with Kotlin's random numbers (krandom.ts) so both give the same numbers for the same cards. See the app for
// the model (Frank Karsten's goldfishing: free mulligan, London mulligans, a land a turn, rocks, mana creatures and
// land ramp spells up to 4 mana).
import { fetches, isBasic, isLand, landFaces, landTypes, FACE_BREAK, type StatsCard } from "./deckstats";
import { KRandom, shuffled } from "./krandom";

export interface CastOdds {
  name: string;
  manaCost: string;
  manaValue: number;
  /** The chance (whole percent) it can be cast by each turn, turn 1 first. */
  byTurn: number[];
}

export interface ManaOdds {
  games: number;
  commanders: CastOdds[];
  /** Each colour of the identity with the chance it's there by each turn. */
  colours: [string, number[]][];
  /** All colours together; null for decks of one colour or none. */
  allColours: number[] | null;
  lands: number;
  rocks: number;
  creatures: number;
  ramp: number;
}

export const GAMES = 10_000;
export const TURNS = 7;

const COLOURS = "WUBRG";
export const BASIC_TYPES: [string, string][] = [
  ["plains", "W"],
  ["island", "U"],
  ["swamp", "B"],
  ["mountain", "R"],
  ["forest", "G"],
];
const SYMBOL = /\{([^}]+)\}/g;
const TAP_ADD = /^(?:\{[^}]+\}, )?\{T\}[^:.]*: Add/im;
const RAMP_SEARCH = /search your library for (up to )?(a|an|one|two)[^.]*(land|plains|island|swamp|mountain|forest) cards?[^.]*onto the battlefield/i;
const TAP_ADD_LINE = /^\{T\}: Add (.*)$/gim;
const MANA_SYMBOL = /\{[WUBRGC]\}/g;
const HASTE_LINE = /^haste\b/im;
const LAND_TAP_ADD_LINE = /^\{T\}(?:, [^:]*)?: Add (.*)$/gim;
const DEPLETION = /enters tapped with (\w+) depletion counters/i;
const SACRIFICE_LANDS = /when this land enters, sacrifice (\w+) (?:untapped )?lands/i;
const CONDITIONAL = /activate only if|spend this mana only/i;
const BASICS = "plains|island|swamp|mountain|forest";
const SLOW_LAND = /enters (?:the battlefield )?tapped unless you control (\w+) or more other lands/;
const FAST_LAND = /enters (?:the battlefield )?tapped unless you control (\w+) or fewer other lands/;
const BUGBEAR = /if you control (\w+) or more other lands, this land enters (?:the battlefield )?tapped/;
const BATTLE_LAND = /enters (?:the battlefield )?tapped unless you control (\w+) or more basic lands/;
const CHECK_LAND = new RegExp(`enters (?:the battlefield )?tapped unless you control an? (${BASICS})(?:,? or (?:an? )?(${BASICS}))?\\b`);
const LEGEND_LAND = /enters (?:the battlefield )?tapped unless you control a legendary creature/;
const WALKER_LAND = /enters (?:the battlefield )?tapped unless you control a planeswalker/;
const TYPE_COUNT_LAND = new RegExp(`enters (?:the battlefield )?tapped unless you control (\\w+) or more other (${BASICS})s\\b`);
const LOW_LIFE_LAND = /enters (?:the battlefield )?tapped unless a player has (\w+) or less life/;
const REVEAL_LAND = new RegExp(`you may reveal an? (${BASICS})(?: or (?:an? )?(${BASICS}))? card from your hand`);
const PAY_LIFE = /you may pay \d+ life\. if you don't, it enters (?:the battlefield )?tapped/;

const NUMBERS: Record<string, number> = { a: 1, an: 1, one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10 };

/** The app's WinOddsCalc.number: "two" → 2, "3" → 3, anything else 0. */
export function number(s: string | null | undefined): number {
  if (s == null) return 0;
  const l = s.toLowerCase();
  if (l in NUMBERS) return NUMBERS[l];
  return /^[+-]?\d+$/.test(l) ? parseInt(l, 10) : 0;
}

/** Kotlin's substringBefore. */
export function before(s: string, d: string): string {
  const i = s.indexOf(d);
  return i < 0 ? s : s.slice(0, i);
}

/** The most mana one "{T}: Add …" ability makes: {C}{C} or {R}{R} is 2, "two mana in any combination" 2. */
export function manaAmount(text: string, landLine = false): number {
  const line = landLine ? LAND_TAP_ADD_LINE : TAP_ADD_LINE;
  let best: number | null = null;
  for (const m of text.matchAll(line)) {
    let n: number;
    if (landLine && CONDITIONAL.test(m[1])) n = 1;
    else {
      const what = before(before(m[1], "."), " or ");
      const lw = what.toLowerCase();
      if (lw.startsWith("two mana")) n = 2;
      else if (lw.startsWith("three mana")) n = 3;
      else {
        const v = /^(?:\{[WUBRGC]\})+/.exec(what)?.[0];
        n = v !== undefined ? [...v.matchAll(MANA_SYMBOL)].length : 1;
      }
    }
    best = best === null ? n : Math.max(best, n);
  }
  return best === null ? 1 : Math.max(1, best);
}

/** A mana cost as generic mana and coloured pips, each pip the colours that can pay it. */
export interface Cost {
  generic: number;
  pips: Set<string>[];
}

export const NO_COST: Cost = { generic: 0, pips: [] };

export function manaValue(c: Cost): number {
  return c.generic + c.pips.length;
}

/**
 * Reads "{2}{B}{G/U}{2/W}{B/P}{X}": hybrids can be paid with either colour, {2/W} with its colour, Phyrexian mana is
 * left out (life pays it), X counts as 0 and snow as generic.
 */
export function cost(text: string | null | undefined): Cost {
  let generic = 0;
  const pips: Set<string>[] = [];
  for (const m of before(text ?? "", " // ").matchAll(SYMBOL)) {
    const s = m[1].toUpperCase();
    if (/^[+-]?\d+$/.test(s)) generic += parseInt(s, 10);
    else if (s.length === 1 && (COLOURS.includes(s) || s === "C")) pips.push(new Set([s]));
    else if (s === "S") generic += 1;
    else if (s.includes("/")) {
      const parts = s.split("/");
      if (parts.includes("P")) continue;
      const colours = new Set(parts.filter((p) => p.length === 1 && (COLOURS.includes(p) || p === "C")));
      if (colours.size > 0) pips.push(colours);
    }
  }
  return { generic, pips };
}

export type Kind = "LAND" | "ROCK" | "CREATURE" | "RAMP" | "SPELL";

/** When a land that enters tapped comes in untapped (the app's Untap). */
export interface Untap {
  min: number;
  max: number;
  basics: number;
  types: Set<string>;
  reveal: boolean;
  ofTypes: number;
  legendary: boolean;
  planeswalker: boolean;
  lowLife: number | null;
}

function untapOf(u: Partial<Untap>): Untap {
  return {
    min: 0,
    max: Number.MAX_SAFE_INTEGER,
    basics: 0,
    types: new Set(),
    reveal: false,
    ofTypes: 0,
    legendary: false,
    planeswalker: false,
    lowLife: null,
    ...u,
  };
}

/** What a card does for the mana (the app's ManaOddsCalc.Card); also used by the win turn. */
export interface MCard {
  kind: Kind;
  colours: Set<string>;
  tapped: boolean;
  cost: Cost;
  amount: number;
  toHand: boolean;
  haste: boolean;
  uses: number;
  sacrificesLands: number;
  untap: Untap | null;
  types: Set<string>;
  basic: boolean;
}

export function mcard(kind: Kind, colours: Set<string>, tapped: boolean, c: Cost, amount: number, o: Partial<MCard> = {}): MCard {
  return { kind, colours, tapped, cost: c, amount, toHand: false, haste: false, uses: 0, sacrificesLands: 0, untap: null, types: new Set(), basic: false, ...o };
}

/** The lands on the battlefield as an "enters tapped unless …" land sees them. */
export interface LandsOut {
  count: number;
  basics: number;
  types: Set<string>;
  typeCounts: Map<string, number>;
  legendary: boolean;
  planeswalker: boolean;
  lowestLife: number;
}

export function landsOut(o: Partial<LandsOut> & { count: number; basics: number; types: Set<string> }): LandsOut {
  return { typeCounts: new Map(), legendary: false, planeswalker: false, lowestLife: 40, ...o };
}

/** The basic land types a land of these colours has (a ramp spell's basics). */
export function basicTypes(colours: Set<string>): Set<string> {
  return new Set(BASIC_TYPES.filter(([, c]) => colours.has(c)).map(([t]) => t));
}

/**
 * The colour of the basic a ramp spell fetches, from the [colours] it could fetch: one no source in [have] makes yet,
 * else the one the fewest make (WUBRG order on a tie). {C} stays {C}.
 */
export function rampColour(colours: Set<string>, have: Set<string>[]): Set<string> {
  const choices = [...COLOURS].filter((c) => colours.has(c));
  if (choices.length === 0) return colours;
  let best = choices[0];
  let bestN = Infinity;
  for (const c of choices) {
    const n = have.filter((h) => h.has(c)).length;
    if (n < bestN) {
      best = c;
      bestN = n;
    }
  }
  return new Set([best]);
}

/** A mana source on the battlefield: what it makes, how much, and from which turn until which. */
interface Source {
  colours: Set<string>;
  amount: number;
  from: number;
  until: number;
  land: boolean;
  types: Set<string>;
  basic: boolean;
}

const FOREVER = Number.MAX_SAFE_INTEGER;

/** A pool of mana: each source's colours and how much it makes. */
export type Pool = [Set<string>, number][];

export function calculate(cards: StatsCard[], identity: string, games = GAMES, turns = TURNS, random: KRandom = new KRandom(46)): ManaOdds | null {
  const commanders = cards.filter((c) => c.commander);
  const library = cards.filter((c) => !c.commander);
  if (library.reduce((s, c) => s + c.quantity, 0) < 8) return null;
  const own = new Set([...identity].filter((c) => COLOURS.includes(c)));
  const lands = library.filter(isLand);
  const deck: MCard[] = [];
  for (const c of library) {
    const card = classify(c, own, lands);
    for (let i = 0; i < Math.max(0, c.quantity); i++) deck.push(card);
  }
  const goals = commanders.map((c) => [c, cost(c.manaCost)] as const);
  const castBy = goals.map(() => new Array(turns + 1).fill(0));
  const colourBy = new Map([...own].map((c) => [c, new Array(turns + 1).fill(0)]));
  const allBy = new Array(turns + 1).fill(0);
  for (let g = 0; g < games; g++) {
    const [hand, rest] = keepHand(deck, random, (x) => x);
    const sources: Source[] = [];
    const castTurn = new Array(goals.length).fill(0);
    const seen = new Set<string>();
    let draw = 0;
    for (let turn = 1; turn <= turns; turn++) {
      if (draw < rest.length) hand.push(rest[draw++]);
      const out = goals.filter((_, i) => castTurn[i] !== 0).map(([c]) => c.typeLine ?? "");
      playLand(
        hand,
        sources,
        turn,
        goals.filter((_, i) => castTurn[i] === 0).map(([, c]) => c),
        out,
      );
      const ready = sources.filter((s) => s.from <= turn && turn <= s.until);
      for (const s of ready) for (const c of s.colours) seen.add(c);
      for (const colour of own) if (seen.has(colour)) colourBy.get(colour)![turn]++;
      if (own.size > 0 && [...own].every((c) => seen.has(c))) allBy[turn]++;
      goals.forEach(([, c], i) => {
        if (castTurn[i] === 0 && canPay(ready.map((s) => [s.colours, s.amount]), c)) castTurn[i] = turn;
        if (castTurn[i] !== 0) castBy[i][turn]++;
      });
      castRamp(hand, sources, ready, turn, own);
    }
  }
  const percent = (counts: number[]) => {
    const out: number[] = [];
    for (let t = 1; t <= turns; t++) {
      const p = Math.round((100 * counts[t]) / games);
      out.push(p === 100 && counts[t] < games ? 99 : p);
    }
    return out;
  };
  return {
    games,
    commanders: goals.map(([c, k], i) => ({ name: c.name, manaCost: c.manaCost ?? "", manaValue: manaValue(k), byTurn: percent(castBy[i]) })),
    colours: [...COLOURS].filter((c) => own.has(c)).map((c) => [c, percent(colourBy.get(c)!)] as [string, number[]]),
    allColours: own.size > 1 ? percent(allBy) : null,
    lands: deck.filter((c) => c.kind === "LAND").length,
    rocks: deck.filter((c) => c.kind === "ROCK").length,
    creatures: deck.filter((c) => c.kind === "CREATURE").length,
    ramp: deck.filter((c) => c.kind === "RAMP").length,
  };
}

export function classify(c: StatsCard, own: Set<string>, lands: StatsCard[]): MCard {
  const text = c.oracleText ?? "";
  if (isLand(c)) {
    const lf = landFaces(c);
    const faces = lf
      .map(([, t]) => t)
      .join("\n")
      .toLowerCase();
    let colours = new Set([...(c.producedMana ?? "")].filter((x) => own.has(x) || x === "C"));
    const fetch = colours.size === 0 && faces.includes("search your library");
    if (fetch) colours = new Set([...fetches(c, lands).keys()].filter((x) => own.has(x)));
    const untap = fetch ? null : untapCondition(faces);
    const tapped = fetch
      ? faces.includes("tapped")
      : (faces.includes("enters tapped") || faces.includes("enters the battlefield tapped")) && (!faces.includes("unless") || untap !== null) && !PAY_LIFE.test(faces);
    const own0 = lf[0]?.[1] ?? "";
    const dep = DEPLETION.exec(own0);
    const sac = SACRIFICE_LANDS.exec(own0);
    return mcard("LAND", colours.size > 0 ? colours : new Set(["C"]), tapped, NO_COST, manaAmount(own0, true), {
      uses: dep ? number(dep[1]) : 0,
      sacrificesLands: sac ? number(sac[1]) : 0,
      untap,
      types: new Set([...landTypes(c)].map((t) => t.toLowerCase())),
      basic: isBasic(c),
    });
  }
  // The face it's cast as (a battle's creature back isn't a mana creature).
  const type = before(c.typeLine ? c.typeLine : c.types, " // ");
  const face = text.split(FACE_BREAK)[0];
  const produced = new Set([...(c.producedMana ?? "")].filter((x) => own.has(x) || x === "C"));
  if (produced.size > 0 && c.cmc <= 4 && TAP_ADD.test(face) && (type.includes("Artifact") || type.includes("Creature"))) {
    const creature = type.includes("Creature");
    const amount = /Add \{C\}\{C\}/.test(face) ? 2 : manaAmount(face);
    return mcard(creature ? "CREATURE" : "ROCK", produced, false, cost(c.manaCost), amount, { haste: creature && HASTE_LINE.test(face) });
  }
  // A Siege's lands when it enters count as a ramp spell's.
  if (c.cmc <= 4 && (type.includes("Sorcery") || type.includes("Instant") || type.includes("Battle"))) {
    const m = RAMP_SEARCH.exec(face);
    if (m) {
      const what = m[0].toLowerCase();
      const named = new Set(BASIC_TYPES.filter(([t]) => what.includes(t)).map(([, col]) => col).filter((x) => own.has(x)));
      const colours = named.size > 0 ? named : own.size > 0 ? new Set(own) : new Set(["C"]);
      // "Put one onto the battlefield tapped and the other into your hand" (Cultivate); else up to two lands.
      const split = what.includes("put one onto the battlefield");
      const n = !split && m[2].toLowerCase() === "two" ? 2 : 1;
      return mcard("RAMP", colours, what.includes("tapped") || face.toLowerCase().includes("tapped"), cost(c.manaCost), n, { toHand: split });
    }
  }
  return mcard("SPELL", new Set(), false, NO_COST, 0);
}

/** When a land that enters tapped comes in untapped, from its lowercased text; null when it doesn't say. */
export function untapCondition(text: string): Untap | null {
  const n = (m: RegExpExecArray) => number(m[1]);
  const types = (m: RegExpExecArray) => new Set(m.slice(1).filter((x): x is string => !!x));
  let m: RegExpExecArray | null;
  if (LEGEND_LAND.exec(text)) return untapOf({ legendary: true });
  if (WALKER_LAND.exec(text)) return untapOf({ planeswalker: true });
  if ((m = TYPE_COUNT_LAND.exec(text))) return untapOf({ types: new Set([m[2]]), ofTypes: n(m) });
  if ((m = LOW_LIFE_LAND.exec(text))) return untapOf({ lowLife: n(m) });
  if ((m = SLOW_LAND.exec(text))) return untapOf({ min: n(m) });
  if ((m = FAST_LAND.exec(text))) return untapOf({ max: n(m) });
  if ((m = BUGBEAR.exec(text))) return untapOf({ max: n(m) - 1 });
  if ((m = BATTLE_LAND.exec(text))) return untapOf({ basics: n(m) });
  if ((m = CHECK_LAND.exec(text))) return untapOf({ types: types(m) });
  if ((m = REVEAL_LAND.exec(text))) return untapOf({ types: types(m), reveal: true });
  return null;
}

/** Whether [land] comes in untapped now, with [out] on the battlefield and [hand] (without it) to reveal from. */
export function entersUntapped(land: MCard, out: LandsOut, hand: MCard[]): boolean {
  if (!land.tapped) return true;
  const u = land.untap;
  if (!u) return false;
  if (out.count < u.min || out.count > u.max || out.basics < u.basics) return false;
  if ((u.legendary && !out.legendary) || (u.planeswalker && !out.planeswalker)) return false;
  if (u.lowLife !== null && out.lowestLife > u.lowLife) return false;
  if (u.types.size === 0) return true;
  if (u.ofTypes > 0) {
    let n = 0;
    for (const t of u.types) n += out.typeCounts.get(t) ?? 0;
    return n >= u.ofTypes;
  }
  return u.reveal ? hand.some((h) => h.kind === "LAND" && [...h.types].some((t) => u.types.has(t))) : [...out.types].some((t) => u.types.has(t));
}

/** The mana a turn spends from [pool] on [costs], dearest first: whether a land's mana would be used now. */
export function spend(pool: Pool, costs: Cost[]): number {
  let left = pool;
  let spent = 0;
  const sorted = [...costs].sort((a, b) => manaValue(b) - manaValue(a));
  for (const c of sorted) {
    const l = pay(left, c);
    if (l === null) continue;
    left = l;
    spent += manaValue(c);
  }
  return spent;
}

function minus(a: Set<string>, ...b: (Set<string> | string)[]): number {
  let n = 0;
  outer: for (const x of a) {
    for (const s of b) if (typeof s === "string" ? s === x : s.has(x)) continue outer;
    n++;
  }
  return n;
}

/**
 * The land to play from [lands]: one that adds a missing colour ({C} isn't one), untapped when its mana would be used
 * this turn on [costs] from [pool], else one that enters tapped (always-tapped lands before those that could come in
 * untapped later). With a [sink] an untapped land's mana is always used.
 */
export function chooseLand<T>(
  lands: T[],
  mana: (t: T) => MCard,
  untapped: (t: T) => boolean,
  have: Set<string>,
  pool: Pool,
  costs: Cost[],
  last: (t: T) => boolean = () => false,
  sink = false,
): T {
  const up = new Map<T, boolean>();
  for (const l of lands) if (!up.has(l)) up.set(l, untapped(l));
  const now = spend(pool, costs);
  const needed = lands.some((l) => up.get(l)! && (sink || spend([...pool, [mana(l).colours, mana(l).amount]], costs) > now));
  const b = (x: boolean) => (x ? 1 : 0);
  const key = (t: T): number[] => {
    const u = up.get(t)!;
    const m = mana(t);
    return [b(needed ? !u : u), b(last(t)), b(!u && m.untap !== null), -minus(m.colours, have, "C"), -minus(m.colours, "C")];
  };
  let best = lands[0];
  let bestKey = key(best);
  for (let i = 1; i < lands.length; i++) {
    const k = key(lands[i]);
    for (let j = 0; j < k.length; j++) {
      if (k[j] < bestKey[j]) {
        best = lands[i];
        bestKey = k;
        break;
      }
      if (k[j] > bestKey[j]) break;
    }
  }
  return best;
}

/** Kotlin's MutableList.remove(element): the first equal one. */
export function removeOne<T>(list: T[], x: T): boolean {
  const i = list.indexOf(x);
  if (i < 0) return false;
  list.splice(i, 1);
  return true;
}

function minBy<T>(list: T[], f: (t: T) => number): T {
  let best = list[0];
  let bv = f(best);
  for (let i = 1; i < list.length; i++) {
    const v = f(list[i]);
    if (v < bv) {
      best = list[i];
      bv = v;
    }
  }
  return best;
}

/**
 * Karsten's Commander mulligans: a free one, then London mulligans bottoming towards three lands. [mana] tells what
 * each card does for the mana.
 */
export function keepHand<T>(deck: T[], random: KRandom, mana: (t: T) => MCard): [T[], T[]] {
  let hand: T[] = [];
  let rest: T[] = [];
  const isL = (t: T) => mana(t).kind === "LAND";
  for (let attempt = 0; attempt <= 4; attempt++) {
    const s = shuffled(deck, random);
    hand = s.slice(0, 7);
    rest = s.slice(7);
    const lands = hand.filter(isL).length;
    if (attempt === 0 && [0, 1, 2, 6, 7].includes(lands)) continue;
    if (attempt === 1 && [0, 1, 6, 7].includes(lands)) continue;
    for (let r = 0; r < Math.max(0, attempt - 1); r++) {
      const l = hand.filter(isL);
      const sp = hand.filter((t) => !isL(t));
      if (l.length > 3) removeOne(hand, minBy(l, (t) => mana(t).colours.size));
      else if (sp.length > 0) removeOne(hand, sp[0]);
      else removeOne(hand, minBy(l, (t) => mana(t).colours.size));
    }
    const kept = hand.filter(isL).length;
    if (attempt === 2 && [0, 1, 5, 6].includes(kept)) continue;
    if (attempt === 3 && [0, 1, 5].includes(kept)) continue;
    break;
  }
  return [hand, rest];
}

function playLand(hand: MCard[], sources: Source[], turn: number, waiting: Cost[], commandersOut: string[] = []): void {
  // Lotus Field waits until there are lands to sacrifice to it.
  const landCount = sources.filter((s) => s.land && turn < s.until).length;
  const lands = hand.filter((c) => c.kind === "LAND" && landCount >= c.sacrificesLands);
  if (lands.length === 0) return;
  const have = new Set(sources.flatMap((s) => [...s.colours]));
  const l = sources.filter((s) => s.land && turn <= s.until);
  const allTypes = l.flatMap((s) => [...s.types]);
  const counts = new Map<string, number>();
  for (const t of allTypes) counts.set(t, (counts.get(t) ?? 0) + 1);
  const out = landsOut({
    count: l.length,
    basics: l.filter((s) => s.basic).length,
    types: new Set(allTypes),
    typeCounts: counts,
    legendary: commandersOut.some((t) => t.includes("Legendary") && t.includes("Creature")),
    planeswalker: commandersOut.some((t) => t.includes("Planeswalker")),
  });
  const untapped = (c: MCard) => {
    const h = [...hand];
    removeOne(h, c);
    return entersUntapped(c, out, h);
  };
  const pool: Pool = sources.filter((s) => s.from <= turn && turn <= s.until).map((s) => [s.colours, s.amount]);
  const costs = [...waiting, ...hand.filter((c) => c.kind === "ROCK" || c.kind === "CREATURE" || c.kind === "RAMP").map((c) => c.cost)];
  const land = chooseLand(lands, (x) => x, untapped, have, pool, costs);
  const up = untapped(land);
  removeOne(hand, land);
  // The lands sacrificed to it are tapped for mana first.
  for (let i = 0; i < land.sacrificesLands; i++) {
    const cand = sources.filter((s) => s.land && turn < s.until);
    if (cand.length > 0) minBy(cand, (s) => s.colours.size).until = turn;
  }
  const from = up ? turn : turn + 1;
  sources.push({ colours: land.colours, amount: land.amount, from, until: land.uses > 0 ? from + land.uses - 1 : FOREVER, land: true, types: land.types, basic: land.basic });
}

/** Casts the cheapest mana makers that can be paid for with what's left this turn. */
function castRamp(hand: MCard[], sources: Source[], ready: Source[], turn: number, own: Set<string>): void {
  void own;
  let pool: Pool = ready.map((s) => [s.colours, s.amount]);
  const makers = hand.filter((c) => c.kind === "ROCK" || c.kind === "CREATURE" || c.kind === "RAMP").sort((a, b) => manaValue(a.cost) - manaValue(b.cost));
  for (const card of makers) {
    const left = pay(pool, card.cost);
    if (left === null) continue;
    pool = left;
    removeOne(hand, card);
    if (card.kind === "RAMP") {
      for (let i = 0; i < card.amount; i++) {
        const colour = rampColour(
          card.colours,
          sources.map((s) => s.colours),
        );
        sources.push({ colours: colour, amount: 1, from: card.tapped ? turn + 1 : turn, until: FOREVER, land: true, types: basicTypes(colour), basic: true });
      }
      if (card.toHand) {
        const colour = rampColour(card.colours, [...sources.map((s) => s.colours), ...hand.filter((c) => c.kind === "LAND").map((c) => c.colours)]);
        hand.push(mcard("LAND", colour, false, NO_COST, 1, { types: basicTypes(colour), basic: true }));
      }
    } else if (card.kind === "CREATURE") {
      sources.push({ colours: card.colours, amount: card.amount, from: card.haste ? turn : turn + 1, until: FOREVER, land: false, types: new Set(), basic: false });
    } else {
      sources.push({ colours: card.colours, amount: card.amount, from: turn, until: FOREVER, land: false, types: new Set(), basic: false });
    }
  }
}

export function canPay(sources: Pool, c: Cost): boolean {
  return pay(sources, c) !== null;
}

/** What's left of [sources] after paying [c], or null when it can't be paid (coloured pips first). */
export function pay(sources: Pool, c: Cost): Pool | null {
  // Not enough mana at all: no need to try (this is called a lot by the win turn).
  let total = 0;
  for (const s of sources) total += s[1];
  const mv = manaValue(c);
  if (total < mv) return null;
  const left = sources.map((s, i) => [s, i] as const).sort((a, b) => a[0][0].size - b[0][0].size || a[1] - b[1]).map((x) => x[0]);
  const amounts = left.map((s) => s[1]);
  const pips = c.pips.map((p, i) => [p, i] as const).sort((a, b) => a[0].size - b[0].size || a[1] - b[1]).map((x) => x[0]);
  const match = (i: number): boolean => {
    if (i === pips.length) return true;
    for (let j = 0; j < left.length; j++) {
      if (amounts[j] > 0) {
        let any = false;
        for (const x of left[j][0])
          if (pips[i].has(x)) {
            any = true;
            break;
          }
        if (any) {
          amounts[j]--;
          if (match(i + 1)) return true;
          amounts[j]++;
        }
      }
    }
    return false;
  };
  if (!match(0)) return null;
  let generic = c.generic;
  for (let j = 0; j < left.length; j++) {
    const use = Math.min(generic, amounts[j]);
    amounts[j] -= use;
    generic -= use;
  }
  if (generic > 0) return null;
  const out: Pool = [];
  for (let j = 0; j < left.length; j++) if (amounts[j] > 0) out.push([left[j][0], amounts[j]]);
  return out;
}
