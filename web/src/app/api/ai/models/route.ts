// The AI models the deck page's "Write with AI" dialog offers: OpenCode Go's list, the default first.
import { aiConfigured, listModels } from "@/lib/aiprimer";
import { DEFAULT_MODEL } from "@/lib/aiprimershared";
import { currentAccount, json } from "@/lib/auth";

export const dynamic = "force-dynamic";

export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  if (!aiConfigured()) return json({ configured: false, models: [], defaultModel: DEFAULT_MODEL });
  try {
    return json({ configured: true, models: await listModels(), defaultModel: DEFAULT_MODEL });
  } catch (e) {
    // The dialog can still offer the default model, and says why the list isn't there.
    return json({
      configured: true,
      models: [DEFAULT_MODEL],
      defaultModel: DEFAULT_MODEL,
      error: e instanceof Error ? e.message : "The AI isn't reachable right now.",
    });
  }
}
