import { currentAccount, errorResponse, json } from "@/lib/auth";
import { ruleZeroDecks } from "@/lib/decks";
import { loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";

/** Every scored deck's bracket and power card data (the app's "Bracket cards / Power level cards of all decks"). */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const { file } = await loadSync(a);
    return json(ruleZeroDecks(file));
  } catch (e) {
    return errorResponse(e);
  }
}
