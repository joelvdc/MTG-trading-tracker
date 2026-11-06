// MTGJSON's list of preconstructed decks and one deck's file, fetched for the precon dialogs (server only).
import { USER_AGENT } from "./config";
import { LIST_URL, parseList, type PreconInfo } from "./precons";
import { EditError } from "./wishlist";

let listCache: { at: number; decks: PreconInfo[] } | null = null;

export async function mtgjson(url: string): Promise<unknown> {
  const r = await fetch(url, { headers: { "User-Agent": USER_AGENT, Accept: "application/json" }, signal: AbortSignal.timeout(30_000), cache: "no-store" });
  if (r.status === 404) throw new EditError("MTGJSON doesn't have that deck.");
  if (!r.ok) throw new Error(`MTGJSON answered ${r.status}.`);
  return r.json();
}

/** The Commander decks in MTGJSON's list (kept for 12 hours). */
export async function preconList(): Promise<PreconInfo[]> {
  if (!listCache || Date.now() - listCache.at > 12 * 3600_000) listCache = { at: Date.now(), decks: parseList(await mtgjson(LIST_URL)) };
  return listCache.decks;
}
