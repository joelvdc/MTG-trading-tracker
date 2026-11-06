import { currentAccount, errorResponse, json } from "@/lib/auth";
import { tokenPrintings } from "@/lib/scryfall";

export const dynamic = "force-dynamic";

/** Every paper printing of a token, by its oracle id (newest first), to pick the one a deck shows. */
export async function GET(req: Request) {
  if (!(await currentAccount())) return json({ error: "Log in first.", loggedOut: true }, 401);
  const oracle = new URL(req.url).searchParams.get("oracle")?.trim() ?? "";
  if (!/^[0-9a-f-]{36}$/.test(oracle)) return json({ results: [] });
  try {
    return json({ results: await tokenPrintings(oracle) });
  } catch (e) {
    return errorResponse(e);
  }
}
