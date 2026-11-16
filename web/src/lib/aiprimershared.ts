// The AI deck primer's options, the text it is written from and the prompt sent to OpenCode Go. Shared with the
// browser (the "Write with AI" dialog on a deck page), so nothing server-only goes here.

export type AiFocus = "CASUAL" | "UPGRADED" | "HIGH" | "CEDH";
export type AiLength = "SHORT" | "FULL";
export type AiSection = "plan" | "mulligan" | "keycards" | "howto" | "weak" | "upgrades" | "rulezero";

/** What the dialog calls each section, and the heading the AI writes for it. */
export const SECTION_LABELS: Record<AiSection, string> = {
  plan: "Game plan",
  mulligan: "Mulligan guide",
  keycards: "Key cards and combos",
  howto: "How to play it",
  weak: "Weak spots",
  upgrades: "Upgrade ideas",
  rulezero: "Rule-zero talk",
};

/** What the dialog calls each focus. */
export const FOCUS_LABELS: Record<AiFocus, string> = {
  CASUAL: "Casual",
  UPGRADED: "Upgraded",
  HIGH: "High power",
  CEDH: "cEDH",
};

/** What each focus means for the advice, in one sentence. */
const FOCUS_MEANING: Record<AiFocus, string> = {
  CASUAL: "fun and fair games",
  UPGRADED: "a tuned but social deck",
  HIGH: "strong and efficient play",
  CEDH: "the fastest wins and interaction",
};

/** Every section but the upgrade ideas, which only make sense when the owner asks for them. */
export const DEFAULT_SECTIONS: AiSection[] = ["plan", "mulligan", "keycards", "howto", "weak", "rulezero"];

/** The model OpenCode Go lists first, and the one used when the dialog doesn't pick another. */
export const DEFAULT_MODEL = "deepseek-v4.1-flash";

/** How many primers one account may write with AI in a UTC day. */
export const MAX_PER_DAY = 20;

export interface AiOptions {
  focus: AiFocus;
  length: AiLength;
  sections: AiSection[];
  /** The owner's primer as it is now, to improve instead of starting from nothing; null when there is none. */
  startFrom: string | null;
}

/** One line of the decklist the prompt shows. */
export interface PrimerCard {
  name: string;
  quantity: number;
}

/** A deck's own words, with no prices and nothing about the collection. */
export interface PrimerInput {
  deckName: string;
  commanders: string[];
  /** The main deck by category, the commander section first (lib/deckshared.ts sections). */
  sections: { title: string; cards: PrimerCard[] }[];
  /** "Lands 37 · Ramp 11 · Draw 9". */
  roles: string;
  bracket: string | null;
  power: string | null;
  /** One line each, like "Sol Ring + X: infinite mana". */
  combos: string[];
  notes: { name: string; note: string }[];
}

const SYSTEM_PROMPT = [
  "You write a Commander deck primer for the deck's owner.",
  "Write plain text with only these marks: lines starting with # or ## for headings, lines starting with - for bullets, and **bold** inside a line.",
  "No tables, no links, no HTML and no code blocks.",
  "Write in English.",
  "Be concrete about these cards: name them and say what they do in this deck.",
  "Don't invent cards that aren't in the list (upgrade ideas excepted).",
].join(" ");

/** The prompt for one primer: the system text fixes the format, the user text says what to write about. */
export function buildPrompt(input: PrimerInput, options: AiOptions): { system: string; user: string } {
  const parts: string[] = [];
  parts.push(`Focus: ${FOCUS_LABELS[options.focus]} — write the advice for ${FOCUS_MEANING[options.focus]}.`);
  parts.push(options.length === "SHORT" ? "Length: about 250–400 words." : "Length: about 700–1200 words.");
  const wanted = options.sections.length > 0 ? options.sections : DEFAULT_SECTIONS;
  parts.push(`Write these sections in this order:\n${wanted.map((s) => `## ${SECTION_LABELS[s]}`).join("\n")}`);

  const deck: string[] = [`Deck: ${input.deckName}`];
  if (input.commanders.length > 0) deck.push(`Commanders: ${input.commanders.join(", ")}`);
  if (input.bracket) deck.push(`Bracket: ${input.bracket}`);
  if (input.power) deck.push(`Power: ${input.power}`);
  if (input.roles) deck.push(`Roles: ${input.roles}`);
  if (input.combos.length > 0) deck.push(`Combos:\n${input.combos.map((c) => `- ${c}`).join("\n")}`);
  if (input.notes.length > 0) deck.push(`The owner's card notes:\n${input.notes.map((n) => `- ${n.name}: ${n.note}`).join("\n")}`);
  const list = input.sections.map((s) => `### ${s.title}\n${s.cards.map((c) => `${c.quantity} ${c.name}`).join("\n")}`).join("\n\n");
  deck.push(`Decklist:\n${list}`);
  parts.push(deck.join("\n"));

  if (options.startFrom && options.startFrom.trim() !== "") {
    parts.push(`Start from the owner's current primer below: keep what's right, improve and fill in the rest.\n\n${options.startFrom.trim()}`);
  }
  return { system: SYSTEM_PROMPT, user: parts.join("\n\n") };
}

/**
 * An AI answer cleaned for the primer editor: no code fences or HTML, the primer's bullet and heading marks, no
 * trailing spaces, no long blank runs, at most 20,000 characters. Null when nothing is left.
 */
export function cleanPrimer(text: string): string | null {
  // Fences are how chat models wrap text in a code block; a primer has no code.
  let out = text
    .split("\n")
    .filter((line) => !/^\s*```/.test(line))
    .map((line) => line.replace(/^(\s*)[*+] /, "$1- ").replace(/^(\s*)#{4,}\s*/, "$1### "))
    .join("\n");
  // Models sometimes wrap a word in HTML tags; the primer has no HTML.
  out = out
    .replace(/<[^>]*>/g, "")
    .split("\n")
    .map((line) => line.trimEnd())
    .join("\n");
  out = out.replace(/\n{3,}/g, "\n\n").trim();
  if (out === "") return null;
  return out.slice(0, 20_000);
}
