import { accountStore } from "@/lib/accounts";
import { errorResponse, json } from "@/lib/auth";
import { categoriesByName, MAX_NAMES } from "@/lib/autocategories";

export const dynamic = "force-dynamic";

/**
 * The website's auto categories (Ramp, Removal, Draw…, from Scryfall's card tags) for card names, for the app's
 * "Auto categories for new cards" (since app 1.81). Needs no login: the table holds only public card data.
 * POST {"names": [...]} (at most MAX_NAMES) → {"categories": {name: [first, others…]}}.
 */
export async function POST(req: Request) {
  let names: unknown;
  try {
    names = ((await req.json()) as { names?: unknown }).names;
  } catch {
    return json({ error: "Send {\"names\": [...]}." }, 400);
  }
  if (!Array.isArray(names) || names.length > MAX_NAMES || names.some((n) => typeof n !== "string" || n.length > 200))
    return json({ error: `Send at most ${MAX_NAMES} card names.` }, 400);
  try {
    // Before the table's first build, say "not yet" rather than "no categories", so the app asks again later
    // instead of remembering empty answers.
    const store = accountStore();
    const built = store ? ((await store.tagCategoryStats())?.cards ?? 0) > 0 : false;
    if (!built) return json({ error: "The auto categories aren't built yet; try again later." }, 503);
    return json({ categories: await categoriesByName(names as string[]) });
  } catch (e) {
    return errorResponse(e);
  }
}
