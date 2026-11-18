// A deck's primer, written by AI: the server side of the deck page's "Write with AI" dialog. The owner's account uses
// the website's key, any other account its own saved one. It answers with the text only; the page puts it in its editor
// and saves it with the deck's set-primer action.
import { accountStore } from "@/lib/accounts";
import { accessFor, deckPrimerInput, writePrimer } from "@/lib/aiprimer";
import { AI_PROVIDERS, DEFAULT_SECTIONS, FOCUS_LABELS, MAX_PER_DAY, SECTION_LABELS, type AiFocus, type AiLength, type AiOptions, type AiSection } from "@/lib/aiprimershared";
import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { categoriesByName } from "@/lib/autocategories";
import { syncDecks } from "@/lib/decks";
import { parseCardTags } from "@/lib/deckroles";
import { isUser } from "@/lib/session";
import { loadSync } from "@/lib/store";
import { prefs } from "@/lib/syncfile";
import { EditError } from "@/lib/wishlist";

export const dynamic = "force-dynamic";
// One AI answer may take up to 100 s (lib/aiprimer.ts), so the function may run a little longer.
export const maxDuration = 120;

type Params = { params: Promise<{ id: string }> };

function deckId(raw: string): number | null {
  const n = Number(raw);
  return Number.isSafeInteger(n) ? n : null;
}

type Body = { model?: unknown; focus?: unknown; length?: unknown; sections?: unknown; startFrom?: unknown };

/** A model id the service could know; anything else falls back to the service's default. The service decides what it serves. */
function modelOf(value: unknown, fallback: string): string {
  const s = typeof value === "string" ? value.trim() : "";
  return s !== "" && s.length <= 100 && /^~?[A-Za-z0-9][A-Za-z0-9._:/+-]*$/.test(s) ? s : fallback;
}

function focusOf(value: unknown): AiFocus {
  return typeof value === "string" && value in FOCUS_LABELS ? (value as AiFocus) : "CASUAL";
}

function lengthOf(value: unknown): AiLength {
  return value === "SHORT" || value === "FULL" ? value : "FULL";
}

/** The wanted sections, in the order asked, without duplicates or unknown names; the default set when none is left. */
function sectionsOf(value: unknown): AiSection[] {
  const known = new Set(Object.keys(SECTION_LABELS));
  const asked = Array.isArray(value) ? value.filter((s): s is AiSection => typeof s === "string" && known.has(s)) : [];
  const out = [...new Set(asked)];
  return out.length > 0 ? out : [...DEFAULT_SECTIONS];
}

function startFromOf(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const text = value.slice(0, 20_000);
  return text.trim() === "" ? null : text;
}

/** The day the daily limit counts in, as the app's jobs do: UTC. */
const utcDay = (at: number) => new Date(at).toISOString().slice(0, 10);

/**
 * Writes one primer: the body's model, focus, length, sections and start-from text, checked and defaulted; the deck
 * looked up like the deck GET route does; one AI request, then the count of the day goes up by one. Nothing is saved
 * to the deck: the page reviews the text and saves it itself.
 */
export async function POST(req: Request, { params }: Params) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const id = deckId((await params).id);
    if (id === null) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    // Whose key the request runs on, decided before anything of the deck is read.
    const store = accountStore();
    const access = await accessFor(a, store);
    if (!access) throw new EditError(a.admin ? "The AI isn't set up on this website." : "Add your own AI key under Settings → AI key first.");
    const body = ((await req.json()) ?? {}) as Body;
    const options: AiOptions = {
      focus: focusOf(body.focus),
      length: lengthOf(body.length),
      sections: sectionsOf(body.sections),
      startFrom: startFromOf(body.startFrom),
    };
    const model = modelOf(body.model, AI_PROVIDERS[access.provider].defaultModel);

    // One account's count for the UTC day; only answers that came back are counted.
    const jobKey = `aiprimer:${a.accountId}:${utcDay(Date.now())}`;
    const last = store ? await store.job(jobKey).catch(() => null) : null;
    const counted = last?.info.count;
    const used = typeof counted === "number" && Number.isFinite(counted) ? Math.max(0, Math.floor(counted)) : 0;
    if (used >= MAX_PER_DAY) throw new EditError(`You've written ${MAX_PER_DAY} primers with AI today; more tomorrow.`);

    const { file } = await loadSync(a);
    const synced = syncDecks(file).find((d) => d.deck.archidektId === id);
    if (!synced) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    // The cards' own tags and their automatic categories, as the deck GET route passes them, so the roles line is filled.
    const cardTags = parseCardTags(prefs(file).cardTags);
    let autoByName: Record<string, string[]> = {};
    try {
      autoByName = await categoriesByName([...new Set(synced.cards.map((c) => c.card.name))]);
    } catch {
      autoByName = {};
    }
    const input = deckPrimerInput(file, id, { cardTags, auto: autoByName });
    if (!input) return json({ error: "That deck isn't in the sync file (any more)." }, 404);

    const answer = await writePrimer(access, input, options, model);
    const count = used + 1;
    // A count that can't be saved shouldn't throw away an answer the owner paid for.
    if (store) await store.setJob(jobKey, Date.now(), { count }).catch(() => undefined);
    // The numbers beside the text let the dialog show how long the primer took and what it cost in tokens.
    return json({
      text: answer.text,
      model,
      left: MAX_PER_DAY - count,
      seconds: answer.seconds,
      outputTokens: answer.outputTokens,
      reasoningTokens: answer.reasoningTokens,
    });
  } catch (e) {
    return errorResponse(e);
  }
}
