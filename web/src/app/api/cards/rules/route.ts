import { currentAccount, errorResponse, json } from "@/lib/auth";
import { rulesOf } from "@/lib/scryfall";

export const dynamic = "force-dynamic";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** A card's rules text (every face), format legalities and rulings, by Scryfall id. */
export async function GET(req: Request) {
  if (!(await currentAccount())) return json({ error: "Log in first.", loggedOut: true }, 401);
  const id = new URL(req.url).searchParams.get("id")?.trim() ?? "";
  if (!UUID.test(id)) return json({ error: "Which card?" }, 400);
  try {
    const rules = await rulesOf(id.toLowerCase());
    return rules ? json(rules) : json({ error: "Scryfall doesn't know that card." }, 404);
  } catch (e) {
    return errorResponse(e);
  }
}
