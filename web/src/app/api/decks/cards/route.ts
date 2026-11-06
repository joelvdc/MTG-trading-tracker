import { currentAccount, errorResponse, json } from "@/lib/auth";
import { ruleZeroDecks } from "@/lib/decks";
import { isUser } from "@/lib/session";
import { loadSync } from "@/lib/store";
import { keptWins, parseWin } from "@/lib/winturns";

export const dynamic = "force-dynamic";

/** Every scored deck's bracket and power card data (the app's "Bracket cards / Power level cards of all decks"), with the kept win turns. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const { file } = await loadSync(a);
    const r = ruleZeroDecks(file);
    // The win turns the website kept (for the speed and the app's power level), by deck id.
    const kept = isUser(a) ? await keptWins(a.accountId) : new Map();
    const wins = Object.fromEntries(r.decks.flatMap((d) => (kept.has(d.id) ? [[d.id, parseWin(kept.get(d.id))]] : [])));
    return json({ ...r, wins });
  } catch (e) {
    return errorResponse(e);
  }
}
