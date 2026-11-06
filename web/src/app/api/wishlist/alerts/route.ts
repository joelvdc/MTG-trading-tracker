import { currentAccount, errorResponse, json } from "@/lib/auth";
import { accountStore } from "@/lib/accounts";
import { isUser } from "@/lib/session";

export const dynamic = "force-dynamic";

/** The wishlist's price alerts reached at night (lib/nightly.ts), newest first. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  if (!isUser(a)) return json({ events: [], available: false });
  try {
    const store = accountStore();
    return json({ events: store ? await store.alertEvents(a.accountId) : [], available: !!store });
  } catch (e) {
    return errorResponse(e);
  }
}
