import { currentAccount, errorResponse, json } from "@/lib/auth";
import { cardFactsByName, type CardFact } from "@/lib/cardfacts";
import { ruleZeroDecks } from "@/lib/decks";
import { saltSumOf } from "@/lib/localratings";
import { isUser } from "@/lib/session";
import { loadSync } from "@/lib/store";
import { keptWins, parseWin } from "@/lib/winturns";

export const dynamic = "force-dynamic";

/** Every score and bracket card of all decks (the app's "Bracket cards / Power level cards of all decks"), with the kept win turns. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const { file } = await loadSync(a);
    const r = ruleZeroDecks(file);
    // The salt sum of each deck (lib/localratings.ts), from one reading of the facts table for every deck's cards; null
    // when it can't be read, or for a deck whose cards have no facts.
    let facts = new Map<string, CardFact>();
    try {
      const names = [...new Set(r.decks.flatMap((d) => d.stats.map((c) => c.name)))];
      facts = await cardFactsByName(names);
    } catch {
      facts = new Map();
    }
    const saltSums = Object.fromEntries(
      r.decks.map((d) => [d.id, facts.size > 0 ? saltSumOf(d.stats, facts) : null]),
    );
    const { decks, ...rest } = r;
    // The win turns the website kept (for the speed and the app's power level), by deck id.
    const kept = isUser(a) ? await keptWins(a.accountId) : new Map();
    const wins = Object.fromEntries(decks.flatMap((d) => (kept.has(d.id) ? [[d.id, parseWin(kept.get(d.id))]] : [])));
    return json({ ...rest, decks: decks.map(({ stats, ...d }) => ({ ...d, saltSum: saltSums[d.id] ?? null })), wins });
  } catch (e) {
    return errorResponse(e);
  }
}
