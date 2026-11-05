import { currentAccount, errorResponse, json } from "@/lib/auth";
import { searchNames } from "@/lib/scryfall";

export const dynamic = "force-dynamic";

export async function GET(req: Request) {
  if (!(await currentAccount())) return json({ error: "Log in first.", loggedOut: true }, 401);
  const q = new URL(req.url).searchParams.get("q")?.trim() ?? "";
  if (q.length < 2 || q.length > 100) return json({ results: [] });
  try {
    return json({ results: await searchNames(q) });
  } catch (e) {
    return errorResponse(e);
  }
}
