// Commander Spellbook's combo finder (backend.commanderspellbook.com), for a deck's local rating: the combos whose cards
// are all in the list. Only the fields the rating and the rule-zero card need are kept.
const BASE = "https://backend.commanderspellbook.com";
const USER_AGENT = "MTGTrader/1.0 (+https://mtgtrader.vercel.app)";

/** One combo Commander Spellbook found in the deck. */
export interface SpellbookCombo {
  id: string;
  /** Its cards' names, in the order the combo lists them. */
  cards: string[];
  /** What it produces, as Commander Spellbook names it ("Win the game", "Infinite lifeloss"…). */
  features: string[];
  notablePrerequisites: string;
  easyPrerequisites: string;
  bracketTag: string | null;
  salt: number | null;
}

type J = Record<string, unknown>;
const obj = (v: unknown): J => (v && typeof v === "object" && !Array.isArray(v) ? (v as J) : {});
const list = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
const str = (v: unknown): string => (typeof v === "string" ? v : "");
const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);

/** The variants of a "find my combos" answer mapped to SpellbookCombo. */
export function parseCombos(body: unknown): SpellbookCombo[] {
  const included = list(obj(obj(body).results).included);
  return included.map((v) => {
    const c = obj(v);
    return {
      id: str(c.id),
      cards: list(c.uses).map((u) => str(obj(obj(u).card).name)),
      features: list(c.produces).map((p) => str(obj(obj(p).feature).name)),
      notablePrerequisites: str(c.notablePrerequisites),
      easyPrerequisites: str(c.easyPrerequisites),
      bracketTag: str(c.bracketTag) || null,
      salt: num(c.salt),
    };
  });
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
