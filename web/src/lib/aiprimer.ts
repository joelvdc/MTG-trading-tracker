// The AI side of a deck's primer: the models OpenCode Go offers and one chat request per primer. Server only: the key
// (OPENCODE_API_KEY) never leaves this file, and a failure becomes an EditError the route shows as its message.
import { DEFAULT_MODEL, buildPrompt, cleanPrimer, type AiOptions, type PrimerInput } from "./aiprimershared";
import { USER_AGENT } from "./config";
import { deckView, type DeckViewExtras } from "./decks";
import { allCombos, bracketName, comboResultText, POWER_SOURCES, sections } from "./deckshared";
import type { SyncFile } from "./syncfile";
import { EditError } from "./wishlist";

const BASE = "https://opencode.ai/inference/go";
const MODELS_URL = `${BASE}/v1/models`;
const CHAT_URL = `${BASE}/openai/v1/chat/completions`;

/** The model list rarely changes and Go asks for slow callers to be kind: one look an hour per instance. */
const MODELS_TTL_MS = 3_600_000;
/** Vercel's function may run for 120 s (the route's maxDuration); the request itself gives up before that. */
const ANSWER_TIMEOUT_MS = 100_000;
const MODELS_TIMEOUT_MS = 30_000;

let modelsCache: { at: number; models: string[] } | null = null;

function apiKey(): string {
  return (process.env.OPENCODE_API_KEY ?? "").trim();
}

/** Whether the website has a key at all (the dialog then still offers the default model). */
export function aiConfigured(): boolean {
  return apiKey() !== "";
}

function headers(key: string): Record<string, string> {
  return { Authorization: `Bearer ${key}`, "User-Agent": USER_AGENT, Accept: "application/json" };
}

function statusError(status: number): EditError {
  if (status === 401 || status === 403) return new EditError("The AI key isn't accepted.");
  if (status === 429) return new EditError("OpenCode Go's usage limit is reached; try again later.");
  return new EditError(`The AI didn't answer (${status}).`);
}

/** [DEFAULT_MODEL] first, then the rest alphabetically, without duplicates. */
function sortModels(ids: string[]): string[] {
  return [...new Set(ids)].sort((a, b) => (a === DEFAULT_MODEL ? -1 : b === DEFAULT_MODEL ? 1 : a < b ? -1 : a > b ? 1 : 0));
}

/** OpenCode Go's text models, the default first; kept in memory for an hour. */
export async function listModels(fetchFn: typeof fetch = fetch): Promise<string[]> {
  if (modelsCache && Date.now() - modelsCache.at < MODELS_TTL_MS) return [...modelsCache.models];
  const key = apiKey();
  if (!key) throw new EditError("The AI isn't set up on this website.");
  let res: Response;
  try {
    res = await fetchFn(MODELS_URL, { headers: headers(key), cache: "no-store", signal: AbortSignal.timeout(MODELS_TIMEOUT_MS) });
  } catch {
    throw new EditError("The AI didn't answer.");
  }
  if (!res.ok) throw statusError(res.status);
  let ids: string[] = [];
  try {
    const body = (await res.json()) as { data?: { id?: unknown }[] };
    ids = (body.data ?? []).flatMap((m) => (typeof m?.id === "string" && m.id.trim() !== "" ? [m.id.trim()] : []));
  } catch {
    // An empty answer is still a reachable Go; the health check then says it has no default model.
    ids = [];
  }
  const models = sortModels(ids);
  modelsCache = { at: Date.now(), models };
  return [...models];
}

/** One chat request for a primer, cleaned for the editor; its answer, or an EditError saying what went wrong. */
export async function writePrimer(input: PrimerInput, options: AiOptions, model: string, fetchFn: typeof fetch = fetch): Promise<string> {
  const key = apiKey();
  if (!key) throw new EditError("The AI isn't set up on this website.");
  const prompt = buildPrompt(input, options);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ANSWER_TIMEOUT_MS);
  let res: Response;
  try {
    res = await fetchFn(CHAT_URL, {
      method: "POST",
      headers: { ...headers(key), "Content-Type": "application/json" },
      body: JSON.stringify({
        model: model || DEFAULT_MODEL,
        messages: [
          { role: "system", content: prompt.system },
          { role: "user", content: prompt.user },
        ],
        temperature: 0.7,
        max_tokens: options.length === "SHORT" ? 1500 : 4000,
      }),
      signal: controller.signal,
    });
  } catch {
    if (controller.signal.aborted) throw new EditError("The AI took too long; try again.");
    throw new EditError("The AI didn't answer.");
  } finally {
    clearTimeout(timer);
  }
  if (!res.ok) throw statusError(res.status);
  let content: unknown = null;
  try {
    const body = (await res.json()) as { choices?: { message?: { content?: unknown } }[] };
    content = body.choices?.[0]?.message?.content;
  } catch {
    content = null;
  }
  const text = typeof content === "string" ? cleanPrimer(content) : null;
  if (!text) throw new EditError("The AI's answer was empty.");
  return text;
}

/**
 * A deck as the prompt sees it: its commanders, its main deck by category, its roles, bracket, power, combos and the
 * owner's card notes. No prices and nothing about the collection. Pass the same extras the deck GET route does, so the
 * roles line is filled in.
 */
export function deckPrimerInput(file: SyncFile, id: number, extras: DeckViewExtras): PrimerInput | null {
  const view = deckView(file, id, null, null, null, extras);
  if (!view) return null;
  const main = view.rows.filter((r) => !r.maybeboard);
  const d = view.deck;
  const combos = (view.salt ? allCombos(view.salt) : []).map((c) => {
    const what = comboResultText(c);
    return what ? `${c.cards.join(" + ")}: ${what}` : c.cards.join(" + ");
  });
  return {
    deckName: d.name,
    commanders: main.filter((r) => r.commander).map((r) => r.name),
    sections: sections(main, "CATEGORY").map((s) => ({ title: s.title, cards: s.rows.map((r) => ({ name: r.name, quantity: r.quantity })) })),
    roles: view.roles.map((r) => `${r.role} ${r.count}`).join(" · "),
    bracket: d.bracketRealistic != null ? `Bracket ${d.bracketRealistic}${bracketName(d.bracketRealistic) ? ` (${bracketName(d.bracketRealistic)})` : ""}` : null,
    power: view.power != null ? `Power ${view.power.toFixed(1)} (${POWER_SOURCES[view.powerSource].label})` : null,
    combos,
    // The notes of the cards the primer is about; the maybeboard's own notes would name cards the list doesn't have.
    notes: main.flatMap((r) => (r.note ? [{ name: r.name, note: r.note }] : [])),
  };
}
