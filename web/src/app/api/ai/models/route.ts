// The AI models the deck page's "Write with AI" dialog offers: the account's service (the website's key for the owner's
// account, its own key for any other), its list with the default model first.
import { accountStore } from "@/lib/accounts";
import { accessFor, listModels } from "@/lib/aiprimer";
import { AI_PROVIDERS, DEFAULT_MODEL } from "@/lib/aiprimershared";
import { currentAccount, json } from "@/lib/auth";
import { isUser } from "@/lib/session";

export const dynamic = "force-dynamic";

export async function GET() {
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  const access = await accessFor(a, accountStore());
  // The dialog then asks the account to save a key, or says the website has none that the owner's account could use.
  if (!access) return json({ configured: false, needsKey: !a.admin, models: [], defaultModel: DEFAULT_MODEL });
  const defaultModel = AI_PROVIDERS[access.provider].defaultModel;
  const provider = AI_PROVIDERS[access.provider].label;
  try {
    return json({ configured: true, needsKey: false, provider, models: await listModels(access), defaultModel });
  } catch (e) {
    // The dialog can still offer the default model, and says why the list isn't there.
    return json({
      configured: true,
      needsKey: false,
      provider,
      models: [defaultModel],
      defaultModel,
      error: e instanceof Error ? e.message : "The AI isn't reachable right now.",
    });
  }
}
