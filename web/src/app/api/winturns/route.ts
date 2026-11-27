import { errorResponse, json } from "@/lib/auth";
import { loggedOut, phoneAccount } from "@/lib/accountweb";
import { keepPhoneWinTurns, phoneWinTurns } from "@/lib/winturns";
import { WIN_ODDS_VERSION } from "@/lib/winodds-js/version";

export const dynamic = "force-dynamic";
// Working out the decks' win turns reads card details, and keeping a phone's results does too.
export const maxDuration = 60;

/**
 * The decks' win turns for phones (app 1.123): GET answers the website's kept results, POST takes the ones the phone
 * worked out itself, so neither side plays a deck the other already has (lib/winturns.ts).
 */
export async function GET(req: Request) {
  const p = await phoneAccount(req);
  if (!p) return loggedOut();
  try {
    return json({ version: WIN_ODDS_VERSION, decks: await phoneWinTurns(p.account.id) });
  } catch (e) {
    return errorResponse(e);
  }
}

export async function POST(req: Request) {
  const p = await phoneAccount(req);
  if (!p) return loggedOut();
  try {
    const body = (await req.json().catch(() => null)) as { results?: unknown } | null;
    if (!body) return json({ error: "That isn't a win turn." }, 400);
    return json({ kept: await keepPhoneWinTurns(p.account.id, body.results) });
  } catch (e) {
    return errorResponse(e);
  }
}
