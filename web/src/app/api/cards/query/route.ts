import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { scryfallBatches } from "@/lib/cardquery";
import { USER_AGENT } from "@/lib/config";
import { scryFetch } from "@/lib/scrylimit";

export const dynamic = "force-dynamic";

const MAX_NAMES = 250;
const pause = () => new Promise((r) => setTimeout(r, 100)); // Scryfall asks for 50–100 ms between requests.

class BadQuery extends Error {}

/** One Scryfall search page; null for "no cards found". */
async function page(url: string): Promise<{ data?: { name: string }[]; has_more?: boolean; next_page?: string } | null> {
  const r = await scryFetch(url, { headers: { "User-Agent": USER_AGENT, Accept: "application/json" }, signal: AbortSignal.timeout(20_000), cache: "no-store" });
  if (r.status === 404) return null;
  if (r.status === 400) {
    const j = (await r.json().catch(() => null)) as { details?: string } | null;
    throw new BadQuery(j?.details ?? "Scryfall couldn't read that search.");
  }
  if (!r.ok) throw new Error(`Scryfall search failed (HTTP ${r.status}).`);
  return r.json();
}

/** Which of the given card names match a Scryfall search (for the filter keys the browser can't answer itself). */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  if (!(await currentAccount())) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { query?: unknown; names?: unknown };
    const query = typeof body?.query === "string" ? body.query.trim() : "";
    const names = Array.isArray(body?.names) ? body.names : [];
    if (!query || query.length > 300) return json({ error: "Type a search of up to 300 characters." }, 400);
    if (names.length > MAX_NAMES || !names.every((n) => typeof n === "string" && n.length > 0 && n.length <= 200)) return json({ error: "Too many cards, or a bad card name." }, 400);
    const found = new Set<string>();
    let first = true;
    for (const q of scryfallBatches(query, names as string[])) {
      let url: string | undefined = `https://api.scryfall.com/cards/search?${new URLSearchParams({ q, unique: "cards" })}`;
      for (let pages = 0; url && pages < 10; pages++) {
        if (!first) await pause();
        first = false;
        const j = await page(url);
        for (const c of j?.data ?? []) found.add(c.name);
        url = j?.has_more ? j.next_page : undefined;
      }
    }
    return json({ names: [...found] });
  } catch (e) {
    if (e instanceof BadQuery) return json({ error: e.message }, 400);
    return errorResponse(e);
  }
}
