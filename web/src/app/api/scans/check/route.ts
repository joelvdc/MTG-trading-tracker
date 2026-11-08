import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { applyCollectionEdit } from "@/lib/collection";
import { addBought, ordersFromFile, recordOrders, type Candidate, type OrderFile } from "@/lib/orders";
import { additions, checkPackage, scanUse } from "@/lib/packagecheck";
import { loadPrices } from "@/lib/pricestore";
import { scansOf, useScans } from "@/lib/scans";
import { editSync, loadSync } from "@/lib/store";
import { binders } from "@/lib/syncfile";
import { checkCard, EditError } from "@/lib/wishlist";

export const dynamic = "force-dynamic";

interface Body {
  /** "check" compares; "add" also adds to the collection, takes the used scans off the list and records the order. */
  action?: "check" | "add";
  /** The order as /api/orders/read gave it. */
  file?: OrderFile;
  candidates?: Candidate[];
  /** Lines marked "It's here". */
  accepted?: number[];
  /** Also add the scanned cards that aren't in the order. */
  extras?: boolean;
  binder?: string | null;
  newBinder?: string | null;
  /** For orders whose file has no date. */
  date?: string;
}

/** The candidates as sent, with their cards checked (a page can't slip in a made-up card). */
function cleanCandidates(file: OrderFile, list: Candidate[]): Candidate[] {
  if (!Array.isArray(list) || list.length !== file.lines.length) throw new EditError("Read the order file first.");
  return list.map((c, i) => ({
    index: i,
    line: file.lines[i],
    card: c?.card ? checkCard(c.card) : null,
    token: c?.token === true,
    basicLand: c?.basicLand === true,
    guessed: c?.guessed === true,
  }));
}

/** Checks the scanned cards against an order read with /api/orders/read (the app's Check an order), and adds them. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as Body;
    const file = body?.file;
    if (!file || !Array.isArray(file.lines) || file.lines.length > 2000) return json({ error: "Read the order file first." }, 400);
    const cands = cleanCandidates(file, body.candidates ?? []);
    const accepted = new Set((Array.isArray(body.accepted) ? body.accepted : []).filter((i) => Number.isInteger(i)));
    if (body.action !== "add") {
      const { file: sync } = await loadSync(a);
      const result = checkPackage(cands, scansOf(sync).filter((s) => s.uid), accepted);
      return json({ result, binders: binders(sync).map((b) => ({ uid: b.uid, name: b.name })) });
    }
    const extras = body.extras !== false;
    const date = typeof body.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(body.date) ? body.date : new Date().toISOString().slice(0, 10);
    const prices = await loadPrices();
    const answer = await editSync(a, (f, now) => {
      // Checked again on the file as it is now, so a scan sent on a phone meanwhile isn't used twice.
      const result = checkPackage(cands, scansOf(f).filter((s) => s.uid), accepted);
      let binder: string | null = null;
      if (typeof body.newBinder === "string" && body.newBinder.trim()) {
        const name = body.newBinder.trim();
        binder = binders(f).find((b) => b.name.trim().toLowerCase() === name.toLowerCase())?.uid ?? applyCollectionEdit(f, { type: "addBinder", name }, now).uid;
      } else if (typeof body.binder === "string") {
        if (!binders(f).some((b) => b.uid === body.binder)) throw new EditError("That binder is gone (deleted on a phone?).");
        binder = body.binder;
      }
      let added = 0;
      for (const x of additions(result, extras)) {
        addBought(
          f,
          { card: x.card, foil: x.finish !== "NONFOIL", finish: x.finish, condition: x.condition, language: x.language, quantity: x.quantity, binder, signed: x.signed, altered: x.altered, price: x.price },
          now,
          prices?.rates ?? null,
        );
        added += x.quantity;
      }
      if (added === 0) throw new EditError("Nothing to add: no ordered card was scanned.");
      useScans(f, scanUse(result, extras), now);
      const newOrders = recordOrders(f, ordersFromFile(file, date, (i) => cands[i]?.card ?? null, now), now);
      return { added, newOrders };
    });
    return json(answer);
  } catch (e) {
    return errorResponse(e);
  }
}
