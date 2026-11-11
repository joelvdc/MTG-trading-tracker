// Is a deck legal in Commander? The app's DeckLegality.kt does the same, and both are tested against the shared cases in
// test/fixtures/legality-cases.json, so the phone and the website always agree. No server-only code here: the deck page
// and the shared pages use it in the browser too.

/** What the check needs to know about a card (by its front-face name), taken from its Scryfall card (ruleOf). */
export interface LegalRule {
  /** Colour identity in WUBRG order, "" for colourless. */
  ci: string;
  /** The front face's type line. */
  type: string;
  /** Scryfall's Commander legality: "legal", "banned", "not_legal" or "restricted". */
  legal: string;
  /** Copies a deck may have: 1, 7 or 9 for "up to … cards named", 0 for any number (basic lands, Relentless Rats…). */
  copies: number;
  /** May be a commander on its own: a legendary creature, or "can be your commander". */
  command: boolean;
  /**
   * How it may pair with a second commander: "partner", "with:<other name>", "group:<partner group>", "friends",
   * "choose-bg", "bg", "companion" (Doctor's companion), "doctor" (Time Lord Doctor).
   */
  pair: string[];
}

export type ProblemKind = "commander" | "pair" | "count" | "banned" | "notlegal" | "colour" | "copies";

export interface LegalProblem {
  kind: ProblemKind;
  /** One line for the deck page. */
  text: string;
  /** The cards it's about, by name as the deck has them, alphabetically. */
  cards: string[];
}

export interface Legality {
  problems: LegalProblem[];
  /** Cards whose rules weren't known (not looked up yet, or Scryfall doesn't know them): left out of the check. */
  unchecked: string[];
}

/** A deck line as the check needs it (the maybeboard is left out by the caller). */
export interface LegalLine {
  name: string;
  quantity: number;
  commander: boolean;
}

/** "Fire // Ice" → "fire": cards are checked by front-face name. */
export const ruleKey = (name: string) => name.split(" // ")[0].trim().toLowerCase();

const WUBRG = "WUBRG";
const NUMBERS: Record<string, number> = { two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10 };

/** The parts of a Scryfall card the rules are read from. */
export interface ScryRuleCard {
  name: string;
  type_line?: string;
  oracle_text?: string;
  color_identity?: string[];
  keywords?: string[];
  legalities?: Record<string, string>;
  card_faces?: { name?: string; type_line?: string; oracle_text?: string }[];
}

/** The rules of a card from its Scryfall card. */
export function ruleOf(c: ScryRuleCard): LegalRule {
  const faces = c.card_faces ?? [];
  const type = faces[0]?.type_line ?? c.type_line ?? "";
  const text = [c.oracle_text ?? "", ...faces.map((f) => f.oracle_text ?? "")].join("\n");
  const ci = WUBRG.split("").filter((x) => (c.color_identity ?? []).includes(x)).join("");
  let copies = 1;
  if (/\bBasic\b/.test(type) || /a deck can have any number of cards named/i.test(text)) copies = 0;
  else {
    const m = /a deck can have up to (\w+) cards named/i.exec(text);
    if (m) copies = NUMBERS[m[1].toLowerCase()] ?? (Number(m[1]) || 1);
  }
  const lines = text.split("\n").map((l) => l.trim());
  const pair: string[] = [];
  for (const l of lines) {
    let m: RegExpExecArray | null;
    if (/^Partner(\s*\(|$)/.test(l)) pair.push("partner");
    else if ((m = /^Partner with ([^(]+?)\s*(\(|$)/.exec(l))) pair.push(`with:${ruleKey(m[1])}`);
    else if ((m = /^Partner\s*[—-]\s*([^(]+?)\s*(\(|$)/.exec(l))) pair.push(`group:${m[1].trim().toLowerCase()}`);
    else if (/^Friends forever(\s*\(|$)/i.test(l)) pair.push("friends");
    else if (/^Choose a Background(\s*\(|$)/i.test(l)) pair.push("choose-bg");
    else if (/^Doctor['’]s companion(\s*\(|$)/i.test(l)) pair.push("companion");
  }
  if (/\bBackground\b/.test(type)) pair.push("bg");
  if (/\bTime Lord Doctor\b/.test(type)) pair.push("doctor");
  const command = (/\bLegendary\b/.test(type) && /\bCreature\b/.test(type)) || /can be your commander/i.test(text);
  return { ci, type, legal: c.legalities?.commander ?? "legal", copies, command, pair: [...new Set(pair)] };
}

/** Whether two commanders may lead a deck together (they are [a] and [b], with their names). */
export function canPair(a: LegalRule, aName: string, b: LegalRule, bName: string): boolean {
  const has = (r: LegalRule, t: string) => r.pair.includes(t);
  if (has(a, "partner") && has(b, "partner")) return true;
  if (has(a, `with:${ruleKey(bName)}`) && has(b, `with:${ruleKey(aName)}`)) return true;
  if (a.pair.some((t) => t.startsWith("group:") && has(b, t))) return true;
  if (has(a, "friends") && has(b, "friends")) return true;
  if ((has(a, "choose-bg") && has(b, "bg")) || (has(b, "choose-bg") && has(a, "bg"))) return true;
  if ((has(a, "companion") && has(b, "doctor")) || (has(b, "companion") && has(a, "doctor"))) return true;
  return false;
}

const byName = (a: string, b: string) => a.toLowerCase().localeCompare(b.toLowerCase());

/** Checks a deck's main lines against Commander's deck rules. */
export function checkLegality(lines: LegalLine[], rules: Map<string, LegalRule>): Legality {
  const problems: LegalProblem[] = [];
  const unchecked = new Set<string>();
  // The same card on several lines counts once, by front-face name; the name shown is the first line's.
  const cards = new Map<string, { name: string; quantity: number; commander: boolean; rule: LegalRule | null }>();
  for (const l of lines) {
    if (l.quantity <= 0) continue;
    const k = ruleKey(l.name);
    const c = cards.get(k);
    if (c) {
      c.quantity += l.quantity;
      c.commander ||= l.commander;
    } else cards.set(k, { name: l.name, quantity: l.quantity, commander: l.commander, rule: rules.get(k) ?? null });
  }
  for (const c of cards.values()) if (!c.rule) unchecked.add(c.name);

  // The commanders.
  const commanders = [...cards.values()].filter((c) => c.commander);
  if (commanders.length === 0) problems.push({ kind: "commander", text: "No commander is marked.", cards: [] });
  else if (commanders.length > 2)
    problems.push({ kind: "commander", text: `${commanders.length} commanders are marked; a deck has one, or two that may pair up.`, cards: commanders.map((c) => c.name).sort(byName) });
  else if (commanders.length === 1) {
    const c = commanders[0];
    if (c.rule && !c.rule.command) problems.push({ kind: "commander", text: `${c.name} can't be a commander.`, cards: [c.name] });
  } else {
    const [a, b] = commanders;
    if (a.rule && b.rule) {
      // A Background may only lead next to a commander that chooses one.
      const alone = (x: typeof a, y: typeof a) => !x.rule!.command && !(x.rule!.pair.includes("bg") && y.rule!.pair.includes("choose-bg"));
      const bad = [a, b].filter((x) => alone(x, x === a ? b : a));
      if (bad.length) for (const x of bad.sort((p, q) => byName(p.name, q.name))) problems.push({ kind: "commander", text: `${x.name} can't be a commander.`, cards: [x.name] });
      else if (!canPair(a.rule, a.name, b.rule, b.name))
        problems.push({ kind: "pair", text: `${a.name} and ${b.name} can't be commanders together.`, cards: [a.name, b.name].sort(byName) });
    }
  }

  // Exactly 100 cards, the commanders included.
  const total = [...cards.values()].reduce((n, c) => n + c.quantity, 0);
  if (total !== 100) problems.push({ kind: "count", text: `The deck has ${total} cards; a Commander deck has exactly 100.`, cards: [] });

  const listed = (kind: ProblemKind, label: string, pick: (c: { name: string; quantity: number; rule: LegalRule | null }) => string | null) => {
    const names = [...cards.values()].map(pick).filter((n): n is string => n !== null).sort(byName);
    if (names.length) problems.push({ kind, text: `${label}: ${names.join(", ")}.`, cards: [...cards.values()].filter((c) => pick(c) !== null).map((c) => c.name).sort(byName) });
  };
  listed("banned", "Banned in Commander", (c) => (c.rule?.legal === "banned" ? c.name : null));
  listed("notlegal", "Not legal in Commander", (c) => (c.rule && c.rule.legal !== "legal" && c.rule.legal !== "banned" ? c.name : null));

  // The commanders' colours (only when every commander's are known).
  if (commanders.length > 0 && commanders.every((c) => c.rule)) {
    const allowed = new Set(commanders.flatMap((c) => c.rule!.ci.split("")));
    listed("colour", "Outside the commander's colours", (c) => (c.rule && c.rule.ci.split("").some((x) => !allowed.has(x)) ? c.name : null));
  }

  // One copy of each card, except basic lands and cards that allow more.
  const BASICS = /^(snow-covered )?(plains|island|swamp|mountain|forest|wastes)$/;
  listed("copies", "More copies than allowed", (c) => {
    const limit = c.rule ? c.rule.copies : BASICS.test(ruleKey(c.name)) ? 0 : 1;
    return limit > 0 && c.quantity > limit ? `${c.name} ×${c.quantity}` : null;
  });

  return { problems, unchecked: [...unchecked].sort(byName) };
}
