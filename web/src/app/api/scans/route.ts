import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { autoCategories } from "@/lib/autocategories";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { orderBinders, setupFor } from "@/lib/collview";
import { ensureDeckDetails } from "@/lib/deckdetails";
import { inOrder, type CardFacts } from "@/lib/deckedit";
import { syncDecks } from "@/lib/decks";
import { currency, rate } from "@/lib/money";
import { PRICE_TYPE_LABELS, type PriceFile } from "@/lib/prices";
import { loadPrices } from "@/lib/pricestore";
import { applyScanEdit, scanRows, scansOf, type DeckLookups, type ScanEdit, type ScanView } from "@/lib/scans";
import { editSync, loadSync } from "@/lib/store";
import { binders, canWrite, prefs, type SyncFile } from "@/lib/syncfile";
import type { DetailsFile } from "@/lib/carddetails";
import type { CkFile } from "@/lib/cardkingdom";

export const dynamic = "force-dynamic";

function scanView(file: SyncFile, prices: PriceFile | null, details: DetailsFile | null, ck: CkFile | null): ScanView {
  const p = prefs(file);
  const setup = setupFor(file, prices, details, ck);
  const cur = currency(p.currency);
  return {
    rows: scanRows(file, setup, prices),
    binders: orderBinders(
      binders(file)
        .filter((b) => b.uid)
        .map((b) => ({ uid: b.uid!, name: b.name })),
      p.binderOrder,
    ),
    decks: syncDecks(file)
      .filter((d) => d.deck.archidektId < 0 && !d.deck.retired)
      .map((d) => ({ id: d.deck.archidektId, name: d.deck.name }))
      .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase())),
    currency: cur,
    perEuro: rate(cur, prices?.rates ?? null) ?? 1,
    priceTypeLabel: PRICE_TYPE_LABELS[setup.type],
    readOnly: !canWrite(file),
  };
}

export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    return json(scanView(file, prices, details, ck));
  } catch (e) {
    return errorResponse(e);
  }
}

/** What sending scans to a deck needs from Scryfall and the auto categories, looked up before the file is opened. */
async function deckLookups(file: SyncFile, uids: string[]): Promise<DeckLookups> {
  const want = new Set(uids);
  const chosen = scansOf(file).filter((s) => s.uid && want.has(s.uid));
  const ids = [...new Set(chosen.map((s) => s.card.scryfallId))];
  let info: Record<string, { t: string; v: number; i: string }> = {};
  try {
    info = (await ensureDeckDetails(ids)) as typeof info;
  } catch {
    info = {};
  }
  const facts: Record<string, CardFacts> = {};
  for (const id of ids) if (info[id]) facts[id] = { typeLine: info[id].t, cmc: info[id].v };
  const auto = await autoCategories(chosen.map((s) => ({ scryfallId: s.card.scryfallId, name: s.card.name })));
  return { facts, look: { identity: (list) => (list.every((i) => info[i]) ? inOrder(list.map((i) => info[i].i)) : null), auto } };
}

/** One change ({ edit: ScanEdit }); answers with the scan list as it is now, plus what the change gave back. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as { edit?: ScanEdit };
    const edit = body?.edit;
    if (!edit || typeof edit !== "object") return json({ error: "No change sent." }, 400);
    const [prices, details, ck] = await Promise.all([loadPrices(), loadDetails(), loadCardKingdom()]);
    let deck: DeckLookups | null = null;
    if (edit.type === "toDeck") deck = await deckLookups((await loadSync(a)).file, Array.isArray(edit.uids) ? edit.uids : []);
    const { file, result } = await editSync(a, (f, now) => ({ file: f, result: applyScanEdit(f, edit, now, deck) }));
    return json({ ...scanView(file, prices, details, ck), result });
  } catch (e) {
    return errorResponse(e);
  }
}
