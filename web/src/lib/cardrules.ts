// A card's rules text, format legality and rulings, from Scryfall's card and rulings JSON. Shared by the server route
// (/api/cards/rules) and the card windows; no server-only code here.

export interface RulesFace {
  name: string;
  manaCost: string;
  typeLine: string;
  oracleText: string;
  power: string | null;
  toughness: string | null;
  loyalty: string | null;
  defense: string | null;
}

export type Legality = "legal" | "not_legal" | "banned" | "restricted";

export interface Ruling {
  /** The day Scryfall published it, "YYYY-MM-DD". */
  date: string;
  text: string;
}

export interface CardRules {
  faces: RulesFace[];
  legalities: Record<string, Legality>;
  rulings: Ruling[];
}

/** The formats shown first, in this order; Scryfall's other ones follow alphabetically. */
export const MAIN_FORMATS = ["commander", "standard", "pioneer", "modern", "legacy", "vintage", "pauper"];

export const LEGALITY_LABELS: Record<Legality, string> = { legal: "Legal", banned: "Banned", restricted: "Restricted", not_legal: "Not legal" };

interface ScryFace {
  name?: string;
  mana_cost?: string;
  type_line?: string;
  oracle_text?: string;
  power?: string;
  toughness?: string;
  loyalty?: string;
  defense?: string;
}

interface ScryRulesCard extends ScryFace {
  card_faces?: ScryFace[];
  legalities?: Record<string, string>;
}

const face = (f: ScryFace, fallback: ScryFace): RulesFace => ({
  name: f.name ?? fallback.name ?? "",
  manaCost: f.mana_cost ?? "",
  typeLine: f.type_line ?? fallback.type_line ?? "",
  oracleText: f.oracle_text ?? "",
  power: f.power ?? null,
  toughness: f.toughness ?? null,
  loyalty: f.loyalty ?? null,
  defense: f.defense ?? null,
});

/** Scryfall's /cards/{id} and /cards/{id}/rulings answers turned into what the card windows show. */
export function parseCardRules(card: ScryRulesCard, rulings: { data?: { published_at?: string; comment?: string }[] } | null): CardRules {
  const faces = card.card_faces?.length ? card.card_faces.map((f) => face(f, card)) : [face(card, card)];
  const legalities: Record<string, Legality> = {};
  for (const [format, status] of Object.entries(card.legalities ?? {})) {
    if (status === "legal" || status === "not_legal" || status === "banned" || status === "restricted") legalities[format] = status;
  }
  const list = (rulings?.data ?? [])
    .filter((r) => r.comment)
    .map((r) => ({ date: r.published_at ?? "", text: r.comment ?? "" }))
    .sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
  return { faces, legalities, rulings: list };
}

/** The formats in the order shown: the main ones first, then the rest alphabetically. */
export function orderedFormats(legalities: Record<string, Legality>): string[] {
  const main = MAIN_FORMATS.filter((f) => f in legalities);
  const rest = Object.keys(legalities)
    .filter((f) => !MAIN_FORMATS.includes(f))
    .sort();
  return [...main, ...rest];
}

/** "oldschool" → "Oldschool", "paupercommander" → "Paupercommander": Scryfall's keys have no word breaks to use. */
export const formatName = (key: string): string => key.charAt(0).toUpperCase() + key.slice(1);

/** A ruling's day like the rest of the site ("5 Nov 2026"). */
export function rulingDate(iso: string): string {
  const d = new Date(`${iso}T12:00:00Z`);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });
}
