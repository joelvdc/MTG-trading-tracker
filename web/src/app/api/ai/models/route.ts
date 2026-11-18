// The AI models the deck page's "Write with AI" dialog offers: the account's service (one of the website's keys for the
// owner's account, its own key for any other), its list with the default model first.
import { accountStore } from "@/lib/accounts";
import { accessFor, listModels, websiteServices } from "@/lib/aiprimer";
import { AI_PROVIDERS, DEFAULT_MODEL } from "@/lib/aiprimershared";
import { currentAccount, json } from "@/lib/auth";
import { isUser } from "@/lib/session";

export const dynamic = "force-dynamic";

export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a || !isUser(a)) return json({ error: "Log in first.", loggedOut: true }, 401);
  // Only the owner's account can pick a service: ?provider= is ignored for every other account.
  const wanted = new URL(req.url).searchParams.get("provider");
  const access = await accessFor(a, accountStore(), Date.now(), wanted);
  // The dialog then asks the account to save a key, or says the website has none that the owner's account could use.
  if (!access) return json({ configured: false, needsKey: !a.admin, models: [], defaultModel: DEFAULT_MODEL });
  const defaultModel = AI_PROVIDERS[access.provider].defaultModel;
  const provider = AI_PROVIDERS[access.provider].label;
  // Which service this list is, and (for the owner's account) every service it can switch between.
  const service = access.provider;
  const services = a.admin ? websiteServices() : [];
  try {
    return json({ configured: true, needsKey: false, provider, models: await listModels(access), defaultModel, service, services });
  } catch (e) {
    // The dialog can still offer the default model, and says why the list isn't there.
    return json({
      configured: true,
      needsKey: false,
      provider,
      models: [defaultModel],
      defaultModel,
      service,
      services,
      error: e instanceof Error ? e.message : "The AI isn't reachable right now.",
    });
  }
}
