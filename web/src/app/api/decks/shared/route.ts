import { currentAccount, errorResponse, json } from "@/lib/auth";
import { sharedCards } from "@/lib/decks";
import { loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";

/** Cards in more than one deck, with how many copies you own (the app's "Cards in several decks"). */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const { file } = await loadSync(a);
    return json({ cards: sharedCards(file) });
  } catch (e) {
    return errorResponse(e);
  }
}
