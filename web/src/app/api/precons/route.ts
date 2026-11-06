import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { applyCollectionEdit } from "@/lib/collection";
import { USER_AGENT } from "@/lib/config";
import { addBought, makeOrder, PRECON, recordOrders } from "@/lib/orders";
import { deckCards, deckUrl, LIST_URL, orderedOf, parseList, searchPrecons, split, type PreconInfo } from "@/lib/precons";
import { loadPrices } from "@/lib/pricestore";
import { lookupCollection } from "@/lib/scryfall";
import { editSync } from "@/lib/store";
import { binders } from "@/lib/syncfile";
import { EditError } from "@/lib/wishlist";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

let listCache: { at: number; decks: PreconInfo[] } | null = null;

async function mtgjson(url: string): Promise<unknown> {
  const r = await fetch(url, { headers: { "User-Agent": USER_AGENT, Accept: "application/json" }, signal: AbortSignal.timeout(30_000), cache: "no-store" });
  if (r.status === 404) throw new EditError("MTGJSON doesn't have that deck.");
  if (!r.ok) throw new Error(`MTGJSON answered ${r.status}.`);
  return r.json();
}

async function decks(): Promise<PreconInfo[]> {
  if (!listCache || Date.now() - listCache.at > 12 * 3600_000) listCache = { at: Date.now(), decks: parseList(await mtgjson(LIST_URL)) };
  return listCache.decks;
}

/** A deck's cards with today's price of one copy (Scryfall's euro price, the deck's finish else the other, like the app). */
async function pricedDeck(fileName: string) {
  const info = (await decks()).find((d) => d.fileName === fileName);
  if (!info) throw new EditError("That deck isn't in MTGJSON's list.");
  const deck = deckCards(await mtgjson(deckUrl(fileName)));
  const found = new Map((await lookupCollection(deck.cards.map((c) => ({ id: c.scryfallId! })))).map((c) => [c.scryfallId, c]));
  const cards = deck.cards.flatMap((d) => {
    const card = found.get(d.scryfallId!);
    if (!card) return [];
    const worth = (d.isFoil ? card.fallbackEurFoil : card.fallbackEur) ?? (d.isFoil ? card.fallbackEur : card.fallbackEurFoil) ?? null;
    return [{ card, deck: d, worth }];
  });
  return { info, cards };
}

/** ?q= searches the Commander precons; ?deck= gives one deck's cards and what they are worth. */
export async function GET(req: Request) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const u = new URL(req.url);
    const deck = u.searchParams.get("deck");
    if (deck) {
      const { info, cards } = await pricedDeck(deck);
      return json({
        info,
        count: cards.reduce((n, c) => n + c.deck.count, 0),
        worth: cards.reduce((n, c) => n + (c.worth ?? 0) * c.deck.count, 0),
        cards: cards.map((c) => ({ name: c.card.name, quantity: c.deck.count, foil: c.deck.isFoil, worth: c.worth })),
      });
    }
    const q = (u.searchParams.get("q") ?? "").slice(0, 100);
    const all = searchPrecons(await decks(), q);
    return json({ decks: all.slice(0, 80), more: Math.max(0, all.length - 80) });
  } catch (e) {
    return errorResponse(e);
  }
}

interface AddBody {
  deck?: string;
  /** What the deck cost, as typed. */
  paid?: { amount: number; currency: string };
  date?: string;
  binder?: string | null;
  newBinder?: string | null;
}

/** Adds a precon's cards (no basic lands), each with its share of the price, and remembers the deck as an order. */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = (await req.json()) as AddBody;
    if (typeof body?.deck !== "string") return json({ error: "Pick a deck first." }, 400);
    const amount = body.paid?.amount;
    const cur = body.paid?.currency ?? "EUR";
    if (typeof amount !== "number" || !Number.isFinite(amount) || amount < 0 || amount > 1e6) return json({ error: "Enter what the deck cost." }, 400);
    if (!["EUR", "DKK", "USD"].includes(cur)) return json({ error: "Unknown currency." }, 400);
    const date = typeof body.date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(body.date) ? body.date : new Date().toISOString().slice(0, 10);
    const [{ info, cards }, prices] = await Promise.all([pricedDeck(body.deck), loadPrices()]);
    if (cards.length === 0) return json({ error: "Scryfall doesn't know this deck's cards." }, 400);
    const rate = cur === "EUR" ? 1 : (prices?.rates.perEuro[cur] ?? (cur === "DKK" ? 7.46038 : null));
    if (!rate) return json({ error: `No exchange rate for ${cur} yet; enter the price in euros.` }, 400);
    const shares = split(amount / rate, cards);
    // As typed in kroner or dollars: split the same way, so those add up too.
    const typedShares = cur === "EUR" ? null : new Map(split(amount, cards).map((s) => [`${s.card.scryfallId}|${s.finish}`, s.price]));
    const added = await editSync(a, (f, now) => {
      let binder: string | null = null;
      if (typeof body.newBinder === "string" && body.newBinder.trim()) {
        const name = body.newBinder.trim();
        binder = binders(f).find((b) => b.name.trim().toLowerCase() === name.toLowerCase())?.uid ?? applyCollectionEdit(f, { type: "addBinder", name }, now).uid;
      } else if (typeof body.binder === "string") {
        if (!binders(f).some((b) => b.uid === body.binder)) throw new EditError("That binder is gone (deleted on a phone?).");
        binder = body.binder;
      }
      let n = 0;
      for (const s of shares) {
        const t = typedShares?.get(`${s.card.scryfallId}|${s.finish}`);
        addBought(
          f,
          { card: s.card, foil: s.finish !== "NONFOIL", condition: "NM", language: "EN", quantity: s.quantity, binder, price: s.price, typed: t != null ? { amount: t, currency: cur } : null },
          now,
          prices?.rates ?? null,
        );
        n += s.quantity;
      }
      // Its "number" is the deck and the day, so the same deck bought again later is an order of its own.
      recordOrders(f, [makeOrder(PRECON, `${info.name} ${date}`, info.name, date, shares.map(orderedOf), now)], now);
      return n;
    });
    const top = shares.slice(0, 3).map((s) => s.card.name);
    return json({ added, deck: info.name, top });
  } catch (e) {
    return errorResponse(e);
  }
}
