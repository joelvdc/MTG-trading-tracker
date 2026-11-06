import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { matchOrder, parseOrderRows, type Lookup } from "@/lib/orders";
import { byCardmarketId, fuzzyNamed, lookupCollectionFull, searchExact } from "@/lib/scryfall";
import { readSpreadsheet, Unreadable } from "@/lib/spreadsheet";
import { loadSync } from "@/lib/store";
import { binders, collection } from "@/lib/syncfile";

export const dynamic = "force-dynamic";
export const maxDuration = 300;

const scryfall: Lookup = { collection: lookupCollectionFull, byCardmarketId, fuzzy: fuzzyNamed, search: searchExact };

/** Reads an order file ({ data: base64, name }) and finds its cards on Scryfall; nothing is saved yet. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { data?: unknown; name?: unknown };
    if (typeof body?.data !== "string" || body.data.length > 11_000_000) return json({ error: "Send an order file of at most 8 MB." }, 400);
    const name = typeof body.name === "string" ? body.name.slice(0, 200) : null;
    let rows: string[][];
    try {
      rows = readSpreadsheet(new Uint8Array(Buffer.from(body.data, "base64")));
    } catch (e) {
      if (e instanceof Unreadable) return json({ error: e.message }, 400);
      throw e;
    }
    const file = parseOrderRows(rows, name);
    if (file.lines.length > 2000) return json({ error: "That order has more than 2000 lines; split it up." }, 400);
    const [candidates, { file: sync }] = await Promise.all([matchOrder(file, scryfall), loadSync(a)]);
    // How many of each printing are owned already.
    const owned: Record<string, number> = {};
    for (const s of collection(sync)) owned[s.item.card.scryfallId] = (owned[s.item.card.scryfallId] ?? 0) + s.item.quantity;
    return json({ file, candidates, owned, binders: binders(sync).map((b) => ({ uid: b.uid, name: b.name })) });
  } catch (e) {
    return errorResponse(e);
  }
}
