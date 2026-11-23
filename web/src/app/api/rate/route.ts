import { errorResponse, json } from "@/lib/auth";
import { statsCard } from "@/lib/deckdetails";
import { rateCards } from "@/lib/deckrating";
import type { SaltCard } from "@/lib/deckshared";
import type { StatsCard } from "@/lib/deckstats";
import { ratingListKey } from "@/lib/localratings";

export const dynamic = "force-dynamic";
export const maxDuration = 30;

/** The largest deck rated (a Commander deck is about 100 lines). */
const MAX_LINES = 250;
const MAX_NAME = 200;
const MAX_QUANTITY = 99;
/** How long an answer is kept for the same list, and how many are. */
const KEEP_MS = 6 * 60 * 60_000;
const KEEP = 200;

/** An answer already given for a list, so the same deck doesn't ask Commander Spellbook again. */
const answers = new Map<string, { at: number; saltCard: string }>();
const NOT_READY = ["Card facts aren't ready yet.", "Commander Spellbook didn't answer"];

interface Line {
  name: string;
  quantity: number;
  cmc: number;
  types: string;
  commander: boolean;
}

const str = (v: unknown): string => (typeof v === "string" ? v : "");
const num = (v: unknown): number => (typeof v === "number" && Number.isFinite(v) ? v : 0);

function keep(key: string, saltCard: string, now: number): void {
  for (const [k, v] of answers) if (now - v.at > KEEP_MS) answers.delete(k);
  answers.delete(key);
  answers.set(key, { at: now, saltCard });
  while (answers.size > KEEP) answers.delete(answers.keys().next().value!);
}

/**
 * The website's rating of a list, for the phone app (no login): body `{ cards: [{ name, quantity, cmc, types,
 * commander }], identity }`, answered with the app's SaltCard JSON. Only the app asks (the header), at most 250 lines.
 */
export async function POST(req: Request) {
  if (req.headers.get("x-requested-with") !== "mtgtrader") return json({ error: "Not allowed." }, 403);
  let body: { cards?: unknown; identity?: unknown };
  try {
    body = ((await req.json()) ?? {}) as { cards?: unknown; identity?: unknown };
  } catch {
    return json({ error: "That isn't a decklist." }, 400);
  }
  const raw = body.cards;
  if (!Array.isArray(raw) || raw.length === 0) return json({ error: "That isn't a decklist." }, 400);
  if (raw.length > MAX_LINES) return json({ error: `A deck has at most ${MAX_LINES} lines.` }, 400);
  const lines: Line[] = [];
  for (const v of raw) {
    const c = (v && typeof v === "object" ? v : {}) as Record<string, unknown>;
    const name = str(c.name).trim();
    const quantity = num(c.quantity);
    if (!name || name.length > MAX_NAME) return json({ error: "A card needs a name." }, 400);
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY) return json({ error: "A card needs 1 to 99 copies." }, 400);
    lines.push({ name, quantity, cmc: num(c.cmc), types: str(c.types).slice(0, MAX_NAME), commander: c.commander === true });
  }
  const identity = typeof body.identity === "string" ? body.identity.slice(0, 10) : null;
  try {
    const stats: StatsCard[] = lines.map((l) =>
      statsCard({ name: l.name, scryfallId: "", quantity: l.quantity, cmc: l.cmc, types: l.types, commander: l.commander }, undefined),
    );
    const key = `${ratingListKey(stats)}|${identity ?? ""}`;
    const now = Date.now();
    const kept = answers.get(key);
    if (kept && now - kept.at <= KEEP_MS) return json({ saltCard: kept.saltCard });
    const card: SaltCard = await rateCards(stats, identity);
    const saltCard = JSON.stringify(card);
    keep(key, saltCard, now);
    return json({ saltCard });
  } catch (e) {
    const message = e instanceof Error ? e.message : "";
    if (NOT_READY.some((m) => message.startsWith(m))) return json({ error: message }, 503);
    return errorResponse(e);
  }
}
