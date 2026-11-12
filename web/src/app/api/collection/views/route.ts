import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { accountStore } from "@/lib/accounts";
import { cleanViews, MAX_VIEWS } from "@/lib/savedviews";
import { isUser } from "@/lib/session";

export const dynamic = "force-dynamic";

/** The account's saved views on the Collection page. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  const store = accountStore();
  if (!isUser(a) || !store) return json({ views: [], available: false });
  try {
    return json({ views: cleanViews(await store.savedViews(a.accountId)), available: true });
  } catch (e) {
    return errorResponse(e);
  }
}

/** Replaces the whole list ({ views }); answers with it as saved. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  const store = accountStore();
  if (!isUser(a) || !store) return json({ error: "Saved views need a website account." }, 400);
  try {
    const body = (await req.json().catch(() => ({}))) as { views?: unknown };
    if (Array.isArray(body.views) && body.views.length > MAX_VIEWS) return json({ error: `At most ${MAX_VIEWS} saved views.` }, 400);
    const views = cleanViews(body.views);
    await store.setSavedViews(a.accountId, views);
    return json({ views });
  } catch (e) {
    return errorResponse(e);
  }
}
