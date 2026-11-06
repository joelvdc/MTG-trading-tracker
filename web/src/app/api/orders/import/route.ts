import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { applyCollectionEdit } from "@/lib/collection";
import { importOrderLines, ordersFromFile, recordOrders, type OrderFile } from "@/lib/orders";
import { loadPrices } from "@/lib/pricestore";
import { editSync } from "@/lib/store";
import { binders, type CardRef } from "@/lib/syncfile";
import { checkCard, EditError } from "@/lib/wishlist";

export const dynamic = "force-dynamic";

interface Body {
  file?: OrderFile;
  /** The cards found for the file's lines, by line number (null where none). */
  cards?: (CardRef | null)[];
  /** The line numbers to add to the collection. */
  add?: number[];
  binder?: string | null;
  newBinder?: string | null;
  /** For orders whose file has no date. */
  date?: string;
}

/** Adds the chosen lines of a read order to the collection and remembers the order (the app's order import). */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as Body;
    const file = body?.file;
    if (!file || !Array.isArray(file.lines) || !Array.isArray(body.cards) || body.cards.length !== file.lines.length) {
      return json({ error: "Read the order file first." }, 400);
    }
    const date = typeof body.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(body.date) ? body.date : new Date().toISOString().slice(0, 10);
    const cards = body.cards.map((c) => (c ? checkCard(c) : null));
    const add = [...new Set(Array.isArray(body.add) ? body.add : [])].filter((i) => Number.isInteger(i) && cards[i]);
    const prices = await loadPrices();
    const result = await editSync(a, (f, now) => {
      let binder: string | null = null;
      if (typeof body.newBinder === "string" && body.newBinder.trim()) {
        const name = body.newBinder.trim();
        binder =
          binders(f).find((b) => b.name.trim().toLowerCase() === name.toLowerCase())?.uid ?? applyCollectionEdit(f, { type: "addBinder", name }, now).uid;
      } else if (typeof body.binder === "string") {
        if (!binders(f).some((b) => b.uid === body.binder)) throw new EditError("That binder is gone (deleted on a phone?).");
        binder = body.binder;
      }
      const added = importOrderLines(
        f,
        add.map((i) => ({ card: cards[i]!, line: file.lines[i] })),
        binder,
        now,
        prices?.rates ?? null,
      );
      const newOrders = recordOrders(f, ordersFromFile(file, date, (i) => cards[i] ?? null, now), now);
      return { added, newOrders };
    });
    return json(result);
  } catch (e) {
    return errorResponse(e);
  }
}
