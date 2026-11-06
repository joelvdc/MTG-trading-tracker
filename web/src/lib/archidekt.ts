// Archidekt's card lookup (the app's ArchidektCollection.printings): each printing's id on Archidekt and its auto
// category ("Ramp", "Removal"…), used to put a card added to a local deck in the category Archidekt would.
import { USER_AGENT } from "./config";

const BASE = "https://archidekt.com";

export interface ArchidektPrinting {
  cardId: number;
  category: string | null;
}

const cache = new Map<string, ArchidektPrinting | null>();

/**
 * The printings Archidekt knows among [scryfallIds], 60 per request (and at most 5 pages each). Looked up once per
 * card while the server runs. Fails quietly: then cards go into the category of their card type, as in the app.
 */
export async function archidektPrintings(scryfallIds: string[]): Promise<Map<string, ArchidektPrinting>> {
  const missing = [...new Set(scryfallIds)].filter((id) => !cache.has(id));
  try {
    for (let i = 0; i < missing.length; i += 60) {
      const chunk = missing.slice(i, i + 60);
      let next: string | null = `${BASE}/api/cards/v2/?uids=${chunk.join(",")}&allEditions&includeTokens&includeEmblems&includeArtCards&includeOversized&includeDigital`;
      for (let pages = 0; next && pages < 5; pages++) {
        const r = await fetch(next, { headers: { "User-Agent": USER_AGENT, Accept: "application/json" }, signal: AbortSignal.timeout(15_000), cache: "no-store" });
        if (!r.ok) throw new Error(`Archidekt answered ${r.status} looking up cards`);
        const j = (await r.json()) as { results?: { uid?: string; id?: number; oracleCard?: { defaultCategory?: string | null } }[]; next?: string | null };
        for (const c of j.results ?? []) {
          if (typeof c.uid !== "string" || typeof c.id !== "number") continue;
          const auto = c.oracleCard?.defaultCategory?.trim();
          cache.set(c.uid, { cardId: c.id, category: auto ? auto : null });
        }
        next = j.next ? j.next.replace(/^http:\/\//, "https://") : null;
      }
      for (const id of chunk) if (!cache.has(id)) cache.set(id, null);
    }
  } catch (e) {
    console.warn("Archidekt card lookup failed:", e instanceof Error ? e.message : e);
  }
  const out = new Map<string, ArchidektPrinting>();
  for (const id of scryfallIds) {
    const p = cache.get(id);
    if (p) out.set(id, p);
  }
  return out;
}
