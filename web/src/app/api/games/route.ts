import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { applyGameEdit, gamesView, lookupCommander, type GameEdit } from "@/lib/games";
import { fuzzyNamed } from "@/lib/scryfall";
import { editSync, loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";

/** The recorded games, players and friends' decks (the app's Games tab). */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const { file } = await loadSync(a);
    return json(gamesView(file));
  } catch (e) {
    return errorResponse(e);
  }
}

/**
 * One change ({ edit: GameEdit }), answered with the games as they are now and what the change gave back; or
 * { lookup: { commander, partner } }: a commander found on Scryfall, for a friend's deck or a guest.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { edit?: GameEdit; lookup?: { commander?: string; partner?: string | null } };
    if (body?.lookup) {
      const c = String(body.lookup.commander ?? "").slice(0, 120);
      if (!c.trim()) return json({ error: "Type the commander." }, 400);
      const p = body.lookup.partner ? String(body.lookup.partner).slice(0, 120) : null;
      return json({ found: await lookupCommander(c, p, fuzzyNamed) });
    }
    const edit = body?.edit;
    if (!edit || typeof edit !== "object") return json({ error: "No change sent." }, 400);
    const { file, result } = await editSync(a, (f, now) => ({ file: f, result: applyGameEdit(f, edit, now) }));
    return json({ ...gamesView(file), result });
  } catch (e) {
    return errorResponse(e);
  }
}
