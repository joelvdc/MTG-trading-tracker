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
/**
 * How much room the answer gets. A thinking model's reasoning counts against max_tokens, so a small budget can end the
 * answer before a word of it is written: generous for both lengths.
 */
const MAX_TOKENS = { SHORT: 8000, FULL: 16000 } as const;

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

/** A message or a stream chunk's delta, as the answer's `choices[0]` carries it. */
type ChatFields = Record<string, unknown>;
type ChatChoice = { message?: ChatFields; delta?: ChatFields; finish_reason?: unknown };
type ChatBody = { choices?: ChatChoice[]; usage?: unknown; model?: unknown };

/** What one chat answer held, whether it came as one JSON body or as streamed chunks. */
type ChatAnswer = {
  text: string;
  finishReason: string | null;
  messageKeys: string[];
  hasReasoning: boolean;
  usage: unknown;
  model: string | null;
};

const NO_ANSWER: ChatAnswer = { text: "", finishReason: null, messageKeys: [], hasReasoning: false, usage: undefined, model: null };

/** The keys a message or delta had, for the diagnostic line; never its text. */
function keysOf(fields: unknown): string[] {
  return fields !== null && typeof fields === "object" ? Object.keys(fields as ChatFields) : [];
}

/** A usage number, or null when the answer carried none (or something else than a number). */
function numberIn(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

/** The answer's completion tokens from `usage`, for the dialog's "took X s, Y tokens" line; null when not told. */
function outputTokensOf(usage: unknown): number | null {
  if (usage === null || typeof usage !== "object") return null;
  return numberIn((usage as ChatFields).completion_tokens);
}

/**
 * The tokens a thinking model spent reasoning: OpenAI's `completion_tokens_details.reasoning_tokens` first, then the
 * plainer `reasoning_tokens` some providers send beside `completion_tokens`; null when the answer said neither.
 */
function reasoningTokensOf(usage: unknown): number | null {
  if (usage === null || typeof usage !== "object") return null;
  const fields = usage as ChatFields;
  const details = fields.completion_tokens_details;
  if (details !== null && typeof details === "object") {
    const nested = numberIn((details as ChatFields).reasoning_tokens);
    if (nested !== null) return nested;
  }
  return numberIn(fields.reasoning_tokens);
}

/** The reasoning a thinking model may put beside the answer, under either name Go uses. */
function reasoningOf(fields: ChatFields | undefined): string {
  if (!fields) return "";
  for (const name of ["reasoning_content", "reasoning"]) {
    const value = fields[name];
    if (typeof value === "string" && value.trim() !== "") return value;
  }
  return "";
}

/** `content` as a string, or as parts (GPT-5 style: `{ type: "text", text }` or `{ text }`) whose texts are joined. */
function contentText(content: unknown): string {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return "";
  return content
    .map((part) => {
      if (typeof part === "string") return part;
      if (part === null || typeof part !== "object") return "";
      const text = (part as { text?: unknown }).text;
      return typeof text === "string" ? text : "";
    })
    .join("");
}

/**
 * The answer in a response body: the whole body as one JSON object, or the `data:` lines of a streamed answer whose
 * chunks each carry the next piece in `choices[0].delta.content`.
 */
function readAnswer(raw: string): ChatAnswer {
  if (raw.trim() === "") return NO_ANSWER;
  try {
    const body = JSON.parse(raw) as ChatBody | null;
    if (body !== null && typeof body === "object" && !Array.isArray(body)) {
      const choice = Array.isArray(body.choices) ? body.choices[0] : undefined;
      const message = choice?.message;
      return {
        text: contentText(message?.content),
        finishReason: typeof choice?.finish_reason === "string" ? choice.finish_reason : null,
        messageKeys: keysOf(message),
        hasReasoning: reasoningOf(message) !== "",
        usage: body.usage,
        model: typeof body.model === "string" ? body.model : null,
      };
    }
  } catch {
    // Not one JSON body: it may be a streamed answer, read below.
  }
  let streamed = false;
  let text = "";
  let finishReason: string | null = null;
  let messageKeys: string[] = [];
  let hasReasoning = false;
  let usage: unknown;
  let model: string | null = null;
  for (const line of raw.split(/\r?\n/)) {
    const data = /^\s*data:\s?(.*)$/.exec(line);
    if (!data) continue;
    streamed = true;
    const payload = data[1].trim();
    if (payload === "" || payload === "[DONE]") continue;
    let chunk: ChatBody | null;
    try {
      chunk = JSON.parse(payload) as ChatBody | null;
    } catch {
      continue;
    }
    if (chunk === null || typeof chunk !== "object") continue;
    const choice = Array.isArray(chunk.choices) ? chunk.choices[0] : undefined;
    // The first chunk's delta names the fields the answer came in, which is what the diagnostic line reports.
    if (messageKeys.length === 0) messageKeys = keysOf(choice?.delta);
    text += contentText(choice?.delta?.content);
    if (reasoningOf(choice?.delta) !== "") hasReasoning = true;
    if (typeof choice?.finish_reason === "string" && choice.finish_reason !== "") finishReason = choice.finish_reason;
    if (chunk.usage !== undefined) usage = chunk.usage;
    if (typeof chunk.model === "string") model = chunk.model;
  }
  return streamed ? { text, finishReason, messageKeys, hasReasoning, usage, model } : NO_ANSWER;
}

/** A usage object with only its numbers kept, its shape as it came: no text ever leaves in a diagnostic line. */
function usageNumbers(value: unknown): unknown {
  if (typeof value === "number") return value;
  if (value === null || typeof value !== "object") return undefined;
  if (Array.isArray(value)) {
    const items = value.map(usageNumbers).filter((v) => v !== undefined);
    return items.length > 0 ? items : undefined;
  }
  const out: Record<string, unknown> = {};
  for (const [name, v] of Object.entries(value as Record<string, unknown>)) {
    const n = usageNumbers(v);
    if (n !== undefined) out[name] = n;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

/**
 * One line saying why an answer came back empty, so a failure on the live site can be read from the logs: the status,
 * the body's type and length, how it ended, which fields it had, whether reasoning was there, the usage numbers and the
 * model. Never the key, the prompt or any of the answer's text.
 */
function warnEmpty(status: number, contentType: string, bodyLength: number, answer: ChatAnswer): void {
  console.warn(
    `aiprimer empty: ${JSON.stringify({
      status,
      contentType,
      bodyLength,
      finishReason: answer.finishReason,
      messageKeys: answer.messageKeys,
      hasReasoning: answer.hasReasoning,
      usage: usageNumbers(answer.usage),
      model: answer.model,
    })}`,
  );
}

/** What one primer request gave back: the text for the editor, and the numbers the dialog shows beside it. */
export interface PrimerAnswer {
  text: string;
  /** Seconds from just before the request to the whole answer being read, to one decimal. */
  seconds: number;
  /** The answer's completion tokens; null when Go sent no usage. */
  outputTokens: number | null;
  /** The tokens spent thinking, when the model reports them; null otherwise. */
  reasoningTokens: number | null;
}

/** One chat request for a primer, cleaned for the editor; its answer, or an EditError saying what went wrong. */
export async function writePrimer(input: PrimerInput, options: AiOptions, model: string, fetchFn: typeof fetch = fetch): Promise<PrimerAnswer> {
  const key = apiKey();
  if (!key) throw new EditError("The AI isn't set up on this website.");
  const prompt = buildPrompt(input, options);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ANSWER_TIMEOUT_MS);
  // The dialog tells the owner how long a primer took: the clock starts as the request leaves, and stops once the whole
  // body has been read, so the wait it saw and the number it shows are the same.
  const startedAt = Date.now();
  try {
    let res: Response;
    try {
      res = await fetchFn(CHAT_URL, {
        method: "POST",
        // Go refuses a chat without a session header (400): each primer is its own one-question conversation, so a new
        // random id per call, carrying nothing about the account, the deck or the owner.
        headers: { ...headers(key), "Content-Type": "application/json", "x-opencode-session": `primer-${crypto.randomUUID()}` },
        body: JSON.stringify({
          model: model || DEFAULT_MODEL,
          messages: [
            { role: "system", content: prompt.system },
            { role: "user", content: prompt.user },
          ],
          temperature: 0.7,
          max_tokens: MAX_TOKENS[options.length],
          // Read the answer whole: a streamed one is still handled below, in case Go streams anyway.
          stream: false,
        }),
        signal: controller.signal,
      });
    } catch {
      if (controller.signal.aborted) throw new EditError("The AI took too long; try again.");
      throw new EditError("The AI didn't answer.");
    }
    if (!res.ok) throw statusError(res.status);
    const contentType = res.headers.get("content-type") ?? "";
    let raw: string;
    try {
      raw = await res.text();
    } catch {
      if (controller.signal.aborted) throw new EditError("The AI took too long; try again.");
      throw new EditError("The AI didn't answer.");
    }
    const seconds = Math.round((Date.now() - startedAt) / 100) / 10;
    const answer = readAnswer(raw);
    const text = cleanPrimer(answer.text);
    if (!text) {
      warnEmpty(res.status, contentType, raw.length, answer);
      if (answer.finishReason === "length") {
        throw new EditError("The AI used all its room for thinking and wrote nothing; try again, or pick Short or fewer sections.");
      }
      throw new EditError("The AI's answer was empty.");
    }
    return { text, seconds, outputTokens: outputTokensOf(answer.usage), reasoningTokens: reasoningTokensOf(answer.usage) };
  } finally {
    clearTimeout(timer);
  }
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
