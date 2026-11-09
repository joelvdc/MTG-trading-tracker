import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { ownedByName } from "@/lib/decks";
import { wishlist } from "@/lib/syncfile";
import { loadSync } from "@/lib/store";

export const dynamic = "force-dynamic";

const MAX_NAMES = 20_000;

/**
 * { names }: which of these cards you own (copies, any printing) and want (wishlist, not received), so recommendations
 * kept in the browser show what you own now rather than when they were fetched. Lower-case names in both answers.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = ((await req.json().catch(() => null)) ?? {}) as { names?: unknown };
    const names = Array.isArray(body.names) ? body.names.filter((n): n is string => typeof n === "string").slice(0, MAX_NAMES) : [];
    const { file } = await loadSync(a);
    const all = ownedByName(file);
    const wanted = new Set(wishlist(file).filter((w) => w.receivedAt == null).map((w) => w.card.name.toLowerCase()));
    const owned: Record<string, number> = {};
    const wished: string[] = [];
    for (const n of new Set(names.map((x) => x.toLowerCase()))) {
      const k = all.get(n);
      if (k) owned[n] = k;
      if (wanted.has(n)) wished.push(n);
    }
    return json({ owned, wished });
  } catch (e) {
    return errorResponse(e);
  }
}
