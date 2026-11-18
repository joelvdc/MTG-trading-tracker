// Settings → AI key: an account's own key for the AI deck primers (OpenCode Go, Mistral or OpenRouter). The owner's
// account uses the website's key instead, so it never saves one. A key is only ever answered as its last four characters.
import { aiConfigured, checkAiKey, cleanAiKey, openAiKey, sealAiKey, websiteAccess } from "@/lib/aiprimer";
import { AI_PROVIDERS, isAiProvider, type AiProvider } from "@/lib/aiprimershared";
import { needStore } from "@/lib/accountweb";
import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { isUser } from "@/lib/session";
import { EditError } from "@/lib/wishlist";

export const dynamic = "force-dynamic";

/** Which key this account writes with, and the one it has saved (never the key itself). */
export async function GET() {
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    // The owner's account uses the website's key, so a saved key of its own means nothing.
    const row = a.admin ? null : await needStore().aiKey(a.accountId);
    const key = row ? openAiKey(row.sealedKey) : null;
    // A row whose service this website doesn't know, or whose key SESSION_SECRET can no longer open, is unreadable.
    const saved =
      row !== null && isAiProvider(row.provider) && key !== null
        ? { provider: row.provider, label: AI_PROVIDERS[row.provider].label, last4: key.slice(-4), savedAt: row.savedAt }
        : null;
    // The service the website's own key belongs to (the owner's account writes with it), or null when there is none.
    const website = websiteAccess();
    return json({
      usesWebsiteKey: a.admin,
      websiteKeySet: aiConfigured(),
      // What the panel calls the website's key: Mistral when that is set up, otherwise OpenCode Go.
      websiteProvider: website === null ? null : AI_PROVIDERS[website.provider].label,
      providers: (Object.keys(AI_PROVIDERS) as AiProvider[]).map((id) => ({ id, label: AI_PROVIDERS[id].label })),
      saved,
      unreadable: row !== null && saved === null,
    });
  } catch (e) {
    return errorResponse(e);
  }
}

/** {action: "save", provider, key}: checks the key with the service, then keeps it sealed; {action: "remove"}: forgets it. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const store = needStore();
    const body = (await req.json().catch(() => ({}))) as { action?: unknown; provider?: unknown; key?: unknown };
    const now = Date.now();
    if (body.action === "save") {
      if (a.admin) throw new EditError("Your account uses the website's own key.");
      if (!isAiProvider(body.provider)) throw new EditError("Pick OpenCode Go, Mistral or OpenRouter.");
      const provider = body.provider;
      const key = cleanAiKey(body.key);
      if (!key) throw new EditError("That doesn't look like an API key.");
      // A key the service refuses is not kept, so a wrong one is never stored.
      await checkAiKey({ provider, key, shared: false });
      await store.setAiKey(a.accountId, { provider, sealedKey: sealAiKey(key, now), savedAt: now });
      return json({ note: `Saved. ${AI_PROVIDERS[provider].label} accepted the key.` });
    }
    if (body.action === "remove") {
      await store.setAiKey(a.accountId, null);
      return json({ note: "Your key is removed." });
    }
    return json({ error: "Unknown action." }, 400);
  } catch (e) {
    return errorResponse(e);
  }
}
