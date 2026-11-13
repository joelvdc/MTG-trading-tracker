import { errorResponse, json } from "@/lib/auth";
import { loggedOut, phoneAccount } from "@/lib/accountweb";
import { loadDetails, PHONE_MAX_IDS, tcgplayerFor, validIds } from "@/lib/carddetails";

export const dynamic = "force-dynamic";

/**
 * TCGplayer prices for phones logged in to an account (app 1.93): {"ids":[Scryfall ids]} → {"cards":{id:{u,tu,at}}}
 * for the printings the website keeps (lib/carddetails.ts, refreshed by the daily job). No Scryfall request: the
 * phone asks Scryfall itself for the ones left out or too old.
 */
export async function POST(req: Request) {
  const p = await phoneAccount(req);
  if (!p) return loggedOut();
  try {
    const body = (await req.json().catch(() => null)) as { ids?: unknown } | null;
    const raw = body?.ids;
    if (!Array.isArray(raw) || raw.length > PHONE_MAX_IDS) return json({ error: `Send up to ${PHONE_MAX_IDS} card ids.` }, 400);
    return json({ cards: tcgplayerFor(await loadDetails(), validIds(raw)) });
  } catch (e) {
    return errorResponse(e);
  }
}
