// Scoring an Archidekt deck on Commander Salt (commandersalt.com), as the app does (CommanderSalt.kt): it has no
// official API, these are the calls its own website makes. Its import often fails on the first try and works on the
// next, so it's retried; if every try fails, an earlier import's scores are used when there are any. The rule-zero card
// data (the deck's saltCard) is read by the app's own SaltCardParser, compiled to JavaScript (web/winodds-kt).
import { createHash } from "node:crypto";
import { USER_AGENT } from "./config";
import { saltCardJson } from "./winodds-js/winodds-kt.mjs";

type Fetch = typeof fetch;
const BASE = "https://api.commandersalt.com";
const RETRY_WAITS_MS = [0, 2_000, 4_000, 8_000];

/** What Commander Salt says about a deck (SaltScores). */
export interface SaltScores {
  saltId: string;
  powerLevel: number | null;
  bracketRealistic: number | null;
  bracketBaseline: number | null;
  saltPercent: number | null;
  archetype: string | null;
  /** The app's SaltCard JSON; null if it couldn't be read. */
  saltCard: string | null;
}

const complete = (s: SaltScores) => s.powerLevel !== null && s.bracketRealistic !== null;

/** Commander Salt's id for an Archidekt deck: the MD5 of the deck's Archidekt API address. */
export function saltDeckId(archidektId: number): string {
  return createHash("md5").update(`https://archidekt.com/api/decks/${archidektId}/`).digest("hex");
}

type J = Record<string, unknown>;
const obj = (v: unknown): J => (v && typeof v === "object" && !Array.isArray(v) ? (v as J) : {});
const n = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
/** Kotlin's roundToInt (half up, as Math.round for these positive numbers). */
const round = (v: number | null) => (v === null ? null : Math.round(v));

/** A deck answer (CommanderSaltApi.parse). */
export function parseSalt(body: string): SaltScores {
  if (!body.trim() || body.trim() === "null") throw new Error("Commander Salt doesn't know this deck");
  let root: unknown;
  try {
    root = JSON.parse(body);
  } catch {
    throw new Error("Commander Salt sent something unexpected");
  }
  if (!root || typeof root !== "object" || Array.isArray(root)) throw new Error("Commander Salt sent something unexpected");
  const d = root as J;
  const details = obj(d.details);
  const b = obj(details.brackets);
  const archetype = typeof d.archetypeLabel === "string" && d.archetypeLabel.trim() ? d.archetypeLabel : null;
  return {
    saltId: typeof d.id === "string" ? d.id : "",
    powerLevel: n(d.powerLevelRating),
    bracketRealistic: round(n(b.csBracket) ?? n(d.bracketRating)),
    bracketBaseline: round(n(b.wotcBracket)),
    saltPercent: n(obj(obj(obj(details.salt).profile).headline).saltPercentage),
    archetype,
    saltCard: saltCardJson(body),
  };
}

/** The server's own explanation ({"message": "ERROR: …"}) when it gives one. */
export function saltError(body: string, code: number): string {
  try {
    const m = obj(JSON.parse(body)).message;
    if (typeof m === "string" && m.replace(/^ERROR:/, "").trim()) return m.replace(/^ERROR:/, "").trim();
  } catch {
    // Not JSON.
  }
  return `Commander Salt error ${code}`;
}

async function call(url: string, method: "GET" | "POST", f: Fetch): Promise<string> {
  const r = await f(url, { method, headers: { Accept: "application/json", "User-Agent": USER_AGENT }, signal: AbortSignal.timeout(45_000), cache: "no-store" });
  const body = await r.text();
  if (!r.ok) throw new Error(saltError(body, r.status));
  return body;
}

/** Scores from an earlier import, or null if Commander Salt doesn't know the deck. */
async function existing(saltId: string, f: Fetch): Promise<SaltScores | null> {
  try {
    const s = parseSalt(await call(`${BASE}/decks?id=${encodeURIComponent(saltId)}`, "GET", f));
    return complete(s) ? s : null;
  } catch {
    return null;
  }
}

/**
 * Imports (or re-imports) the Archidekt deck, which makes Commander Salt score its current list (CommanderSaltApi.score).
 * Throws when it can't, with Commander Salt's reason.
 */
export async function scoreDeck(archidektId: number, f: Fetch = fetch, sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))): Promise<SaltScores> {
  const url = `${BASE}/decks?url=${encodeURIComponent(`https://archidekt.com/decks/${archidektId}`)}`;
  let error: unknown = null;
  for (const wait of RETRY_WAITS_MS) {
    if (wait > 0) await sleep(wait);
    try {
      const scores = parseSalt(await call(url, "POST", f));
      if (complete(scores)) return scores;
      return (await existing(scores.saltId || saltDeckId(archidektId), f)) ?? scores;
    } catch (e) {
      error = e;
    }
  }
  const old = await existing(saltDeckId(archidektId), f);
  if (old) return old;
  throw error instanceof Error ? error : new Error("Commander Salt couldn't score the deck");
}
