// Commander Spellbook's combo finder (backend.commanderspellbook.com), for a deck's local rating: the combos whose cards
// are all in the list. Only the fields the rating and the rule-zero card need are kept.
const BASE = "https://backend.commanderspellbook.com";
const USER_AGENT = "MTGTrader/1.0 (+https://mtgtrader.vercel.app)";

/** One combo Commander Spellbook found in the deck. */
export interface SpellbookCombo {
  id: string;
  /** Its cards' names, in the order the combo lists them. */
  cards: string[];
  /**
   * Where each card must be when the combo starts, one entry per card of cards and in the same order: its zone codes
   * joined ("H" hand, "B" battlefield, "C" command zone, "G" graveyard, "L" library, "E" exile), so "HB" means either.
   */
  zones: string[];
  /**
   * The pieces it needs besides its cards, as Commander Spellbook names them ("Man-Land that Enters Untapped"): one
   * entry per piece, so a template needed twice is listed twice.
   */
  templates: string[];
  /** What it produces, as Commander Spellbook names it ("Win the game", "Infinite lifeloss"…). */
  features: string[];
  notablePrerequisites: string;
  easyPrerequisites: string;
  bracketTag: string | null;
  /** The mana the whole combo needs, as Commander Spellbook writes it ("{4}{W}"); empty when it needs none. */
  manaNeeded: string;
  /** The combo's steps as Commander Spellbook writes them, usually one step per line. */
  steps: string;
  /**
   * The card each card has to be exiled by, one entry per card of cards and in the same order ("Isochron Scepter"),
   * or "" when it doesn't need exiling.
   */
  exiledBy: string[];
  salt: number | null;
}

type J = Record<string, unknown>;
const obj = (v: unknown): J => (v && typeof v === "object" && !Array.isArray(v) ? (v as J) : {});
const list = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
const str = (v: unknown): string => (typeof v === "string" ? v : "");
const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);

// A use's exile state when the combo needs that card in exile: "exiled by Isochron Scepter" (or "exiled with").
const EXILED_BY = /^exiled (?:by|with) (.+?)\.?$/i;

/** The card a use's exile state names, or "" when it names none. */
const exiledByOf = (v: unknown): string => (EXILED_BY.exec(str(v).trim())?.[1] ?? "").trim();

/** The variants of a "find my combos" answer mapped to SpellbookCombo. */
export function parseCombos(body: unknown): SpellbookCombo[] {
  const included = list(obj(obj(body).results).included);
  return included.map((v) => {
    const c = obj(v);
    return {
      id: str(c.id),
      cards: list(c.uses).map((u) => str(obj(obj(u).card).name)),
      zones: list(c.uses).map((u) => list(obj(u).zoneLocations).map((z) => str(z)).join("")),
      templates: list(c.requires).flatMap((r) => {
        const name = str(obj(obj(r).template).name);
        const n = num(obj(r).quantity) ?? 1;
        return name ? Array.from({ length: Math.max(1, Math.round(n)) }, () => name) : [];
      }),
      features: list(c.produces).map((p) => str(obj(obj(p).feature).name)),
      notablePrerequisites: str(c.notablePrerequisites),
      easyPrerequisites: str(c.easyPrerequisites),
      bracketTag: str(c.bracketTag) || null,
      manaNeeded: str(c.manaNeeded),
      steps: str(c.description),
      exiledBy: list(c.uses).map((u) => exiledByOf(obj(u).exileCardState)),
      salt: num(c.salt),
    };
  });
}

/** A card's names to look for in the steps, lowercased: its full name, its front face and that face before a comma. */
function stepNames(card: string): string[] {
  const full = card.toLowerCase();
  const front = full.split(" // ")[0] ?? full;
  const short = front.split(", ")[0] ?? front;
  return [...new Set([full, front, short])].filter((n) => n.length >= 3);
}

/** Whether a step says the card enters ("Ondu Spiritdancer enters", "Kiki-Jiki's ETB", "Kiki-Jiki ETB"). */
const saysEnters = (line: string, name: string) =>
  line.includes(`${name} enters`) || line.includes(`${name}'s etb`) || line.includes(`${name} etb`);

/**
 * The combo's cards whose entering (not casting) starts it, so making them leave and enter again (blinking) also
 * works: only cards that must start off the battlefield (their zones have no "B"), and only when a step says they
 * enter.
 */
export function reentering(cards: string[], zones: string[], steps: string): string[] {
  const lines = steps.toLowerCase().split(/\r?\n/);
  const out: string[] = [];
  cards.forEach((card, i) => {
    const zone = zones[i] ?? "";
    if (!zone || zone.includes("B")) return;
    const names = stepNames(card);
    const enters = names.some((n) => lines.some((l) => saysEnters(l, n)));
    // Casting it and then having it enter counts too, when the "it enters" shares its step or follows in the next.
    const cast = names.some((n) =>
      lines.some((l, j) => l.includes(`cast ${n}`) && (l.includes("it enters") || (lines[j + 1] ?? "").includes("it enters"))),
    );
    if (enters || cast) out.push(card);
  });
  return out;
}

/**
 * The combos in a deck: [commanders] are the commander names, [main] the rest with their quantities. Throws when
 * Commander Spellbook doesn't answer.
 */
export async function findCombos(commanders: string[], main: { name: string; quantity: number }[], f: typeof fetch = fetch): Promise<SpellbookCombo[]> {
  const r = await f(`${BASE}/find-my-combos`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json", "User-Agent": USER_AGENT },
    body: JSON.stringify({
      commanders: commanders.map((name) => ({ card: name.trim() })),
      main: main.map((m) => ({ card: m.name.trim(), quantity: m.quantity })),
    }),
    signal: AbortSignal.timeout(20_000),
    cache: "no-store",
  });
  if (!r.ok) throw new Error(`Commander Spellbook didn't answer (HTTP ${r.status}).`);
  return parseCombos(await r.json());
}
