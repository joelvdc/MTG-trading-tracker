import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { parseOrderRows, type OrderLine } from "@/lib/orders";
import { loadPrices } from "@/lib/pricestore";
import { applyPlan, plan } from "@/lib/purchaseprices";
import { readSpreadsheet, Unreadable } from "@/lib/spreadsheet";
import { editSync, loadSync } from "@/lib/store";
import { EditError } from "@/lib/wishlist";

export const dynamic = "force-dynamic";

interface Body {
  files?: { data: string; name: string }[];
  overwrite?: boolean;
  /** Per line number, the piles ticked for a line the files don't pin to one pile. */
  choices?: Record<number, string[]>;
  /** False: only show what would change. */
  apply?: boolean;
}

/** Purchase prices for owned cards from order files (the app's "Purchase prices from orders"). */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as Body;
    const files = Array.isArray(body?.files) ? body.files.slice(0, 20) : [];
    if (files.length === 0) return json({ error: "Pick one or more order files." }, 400);
    if (files.reduce((n, f) => n + (typeof f.data === "string" ? f.data.length : 0), 0) > 11_000_000) return json({ error: "The files are bigger than 8 MB together." }, 400);
    const lines: OrderLine[] = [];
    for (const f of files) {
      try {
        lines.push(...parseOrderRows(readSpreadsheet(new Uint8Array(Buffer.from(String(f.data), "base64"))), String(f.name ?? "")).lines);
      } catch (e) {
        if (e instanceof Unreadable || e instanceof EditError) return json({ error: `${f.name}: ${e.message}` }, 400);
        throw e;
      }
    }
    const overwrite = body.overwrite === true;
    const choices = body.choices && typeof body.choices === "object" ? body.choices : {};
    if (body.apply !== true) {
      const { file } = await loadSync(a);
      return json({ plan: plan(file, lines, overwrite, choices) });
    }
    const prices = await loadPrices();
    const changed = await editSync(a, (f, now) => applyPlan(f, plan(f, lines, overwrite, choices), now, prices?.rates ?? null));
    return json({ changed });
  } catch (e) {
    return errorResponse(e);
  }
}
