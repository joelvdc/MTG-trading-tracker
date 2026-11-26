// The counters a card enters the battlefield with, read from its front face: a planeswalker's loyalty, a saga's first
// lore counter, a battle's defense, "This creature enters with three +1/+1 counters on it", and Vanishing, Fading,
// Modular and Graft. Counters whose number depends on the game (X, "for each", "equal to") are left to the player.
//
// Pure: the playtester page works these out once per card when it builds the deck.
import { FACE_BREAK } from "./deckstats";

export interface StartCounter {
  kind: string;
  amount: number;
}

const NUMBERS: Record<string, number> = {
  a: 1, an: 1, one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10,
  eleven: 11, twelve: 12,
};

const KEYWORDS: Record<string, string> = { vanishing: "time", fading: "fade", modular: "+1/+1", graft: "+1/+1" };

function escape(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function amountOf(word: string): number | null {
  const lower = word.toLowerCase();
  if (/^\d+$/.test(lower)) return Number(lower);
  return NUMBERS[lower] ?? null;
}

/** Whether the front face is a saga (its lore counter goes up each turn). */
export function isSaga(typeLine: string): boolean {
  const front = typeLine.split(" // ")[0];
  const subtypes = front.split(" — ")[1] ?? "";
  return subtypes.split(/\s+/).includes("Saga");
}

/** The counters the card's front face enters with, the same kind added up; empty when none. */
export function startCounters(card: {
  name: string;
  typeLine: string;
  oracleText?: string | null;
  loyalty?: string | null;
  defense?: string | null;
}): StartCounter[] {
  const out: StartCounter[] = [];
  const add = (kind: string, amount: number) => {
    if (amount <= 0) return;
    const found = out.find((c) => c.kind === kind);
    if (found) found.amount += amount;
    else out.push({ kind, amount });
  };

  const front = card.typeLine.split(" // ")[0];
  const types = (front.split(" — ")[0] ?? "").split(/\s+/);
  if (types.includes("Planeswalker") && /^\d+$/.test((card.loyalty ?? "").trim())) add("loyalty", Number(card.loyalty));
  if (types.includes("Battle") && /^\d+$/.test((card.defense ?? "").trim())) add("defense", Number(card.defense));
  if (isSaga(card.typeLine)) add("lore", 1);

  const text = (card.oracleText ?? "").split(FACE_BREAK)[0].replace(/\([^)]*\)/g, "").replace(/−/g, "-");
  const names = [card.name.split(" // ")[0], card.name.split(" // ")[0].split(",")[0]].map((n) => escape(n.trim()));
  const subject = `(?:this [a-z]+|${[...new Set(names)].join("|")})`;
  const enters = new RegExp(
    `(?:^|[.!] )${subject} enters(?: the battlefield)?(?: tapped)? with (\\w+) ([+-]\\d+/[+-]\\d+|[a-z]+) counters? on it(?! for each)`,
    "i",
  );
  for (const line of text.split("\n")) {
    const trimmed = line.trim();
    for (const part of trimmed.split(/,\s*/)) {
      const keyword = /^(vanishing|fading|modular|graft) (\d+)$/i.exec(part.trim());
      if (keyword) add(KEYWORDS[keyword[1].toLowerCase()], Number(keyword[2]));
    }
    const found = enters.exec(trimmed);
    if (found) {
      const amount = amountOf(found[1]);
      if (amount !== null) add(found[2].toLowerCase(), amount);
    }
  }
  return out;
}

/** "3 loyalty, 1 lore" for the log. */
export function describeCounters(counters: StartCounter[]): string {
  return counters.map((c) => `${c.amount} ${c.kind}`).join(", ");
}
