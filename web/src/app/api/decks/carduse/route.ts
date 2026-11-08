import { currentAccount, errorResponse, json } from "@/lib/auth";
import { cardUse } from "@/lib/decks";
import { loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";

/** ?name=…: the decks that use a card and how many copies you own (a recommended card's window). */
export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const name = new URL(req.url).searchParams.get("name") ?? "";
    if (!name.trim()) return json({ error: "No card name." }, 400);
    const { file } = await loadSync(a);
    return json(cardUse(file, name.trim()));
  } catch (e) {
    return errorResponse(e);
  }
}
