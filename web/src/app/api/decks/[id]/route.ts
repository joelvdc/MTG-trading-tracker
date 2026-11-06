import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { ensureDeckDetails, statsCard, type DeckInfo } from "@/lib/deckdetails";
import { deckView, missingPlan, ownedByName, syncDecks } from "@/lib/decks";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { applyEdit, EditError } from "@/lib/wishlist";
import { FINISHES, resolveFinish } from "@/lib/cards";
import {
  addCard,
  addCategory,
  AUTO,
  copyDeck,
  deleteCategory,
  deleteDeck,
  detachDeck,
  inOrder,
  moveLines,
  removeLines,
  renameCategory,
  renameDeck,
  setCategoryHidden,
  setPick,
  setQuantity,
  setTokenPick,
  type Lookups,
} from "@/lib/deckedit";
import type { DeckCard } from "@/lib/deckshared";
import type { Finish } from "@/lib/pricing";
import type { CardRef } from "@/lib/syncfile";
import { autoCategories } from "@/lib/autocategories";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

type Params = { params: Promise<{ id: string }> };

function deckId(raw: string): number | null {
  const n = Number(raw);
  return Number.isSafeInteger(n) ? n : null;
}

/**
 * One deck: its lines with prices and owned/missing copies, and the stats cards (its lines with Scryfall's mana cost,
 * rules text and tokens, looked up on the first visit) the page works the stats out from.
 */
export async function GET(_req: Request, { params }: Params) {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const id = deckId((await params).id);
    const [{ file }, prices, details, ck] = await Promise.all([loadSync(a), loadPrices(), loadDetails(), loadCardKingdom()]);
    const view = id === null ? null : deckView(file, id, prices, details, ck);
    if (!view) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    const synced = syncDecks(file).find((d) => d.deck.archidektId === id)!;
    const lines = synced.cards.filter((c) => !c.maybeboard);
    let info: Awaited<ReturnType<typeof ensureDeckDetails>> = {};
    let detailsError: string | null = null;
    try {
      info = await ensureDeckDetails(lines.map((c) => c.card.scryfallId));
    } catch (e) {
      detailsError = e instanceof Error ? e.message : "Scryfall couldn't be reached.";
    }
    const stats = lines.map((c) =>
      statsCard({ name: c.card.name, scryfallId: c.card.scryfallId, quantity: c.quantity, cmc: c.cmc, types: c.types, commander: c.commander }, info[c.card.scryfallId]),
    );
    const tokenInfo: Record<string, { oi: string | null; pt: string | null }> = {};
    for (const s of stats) for (const t of s.tokens ?? []) if (info[t.id]) tokenInfo[t.id] = { oi: info[t.id].oi, pt: info[t.id].pt };
    // The Commander Salt data as the app keeps it, for the win turn (its combos) and the app's power level.
    return json({ ...view, stats, tokenInfo, detailsError, saltCard: synced.deck.saltCard ?? null });
  } catch (e) {
    return errorResponse(e);
  }
}

type Body = {
  action?: string;
  skipBasics?: boolean;
  uid?: string;
  uids?: string[];
  quantity?: number;
  category?: string | null;
  from?: string;
  to?: string;
  name?: string;
  moveTo?: string | null;
  hidden?: boolean;
  value?: string | null;
  key?: string;
  card?: CardRef;
  finish?: Finish;
};

const str = (v: unknown, max = 200): string => (typeof v === "string" ? v.slice(0, max) : "");
const uidList = (b: Body): string[] => (Array.isArray(b.uids) ? b.uids.filter((u) => typeof u === "string").slice(0, 2000) : typeof b.uid === "string" ? [b.uid] : []);

/** What an edit needs looked up before the sync file is opened: colour identities and a card's facts (Scryfall). */
async function lookups(lines: DeckCard[], body: Body): Promise<{ look: Lookups; facts: Record<string, DeckInfo> }> {
  const action = body.action ?? "";
  const ids = lines.map((c) => c.card.scryfallId);
  if (body.card?.scryfallId) ids.push(body.card.scryfallId);
  let facts: Record<string, DeckInfo> = {};
  try {
    facts = await ensureDeckDetails(ids);
  } catch {
    facts = {};
  }
  // Auto categories (Archidekt's, else Scryfall's tags) for the cards this action may put in theirs.
  const sameName = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();
  const uids = new Set(uidList(body));
  const needs: DeckCard[] =
    action === "move" && body.category === null
      ? lines.filter((c) => uids.has(c.lineUid))
      : action === "delete-category" && !body.moveTo
        ? lines.filter((c) => sameName(c.category, str(body.name)))
        : action === "auto-all"
          ? lines.filter((c) => !c.commander && !c.maybeboard)
          : [];
  const cards = needs.map((c) => ({ scryfallId: c.card.scryfallId, name: c.card.name }));
  if (action === "add-card" && body.card?.scryfallId && !str(body.category)) cards.push({ scryfallId: body.card.scryfallId, name: str(body.card.name) });
  const auto = cards.length ? await autoCategories(cards) : new Map();
  const identity = (list: string[]) => (list.every((i) => facts[i]) ? inOrder(list.map((i) => facts[i].i)) : null);
  return { look: { identity, auto }, facts };
}

/**
 * Deck actions. Every deck: "missing-to-wishlist" (the cards the collection lacks go on the wishlist, any printing),
 * "copy", "detach" (a deck from Archidekt becomes a local deck), "hide-category", "pick" (the printing you own to
 * show), "token-pick" (a token's printing), "delete" (never on Archidekt itself). Local decks only: "set-quantity", "remove", "move", "auto-all",
 * "add-card", "rename-category", "delete-category", "add-category", "rename".
 */
export async function POST(req: Request, { params }: Params) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const id = deckId((await params).id);
    if (id === null) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    const body = ((await req.json()) ?? {}) as Body;
    if (body.action === "missing-to-wishlist") {
      const added = await editSync(a, (f, now) => {
        const sd = syncDecks(f).find((d) => d.deck.archidektId === id);
        if (!sd) return 0;
        const lines = sd.cards.filter((c) => !c.maybeboard).map((c) => ({ c, name: c.card.name, quantity: c.quantity }));
        const plan = missingPlan(lines, ownedByName(f), body.skipBasics !== false);
        for (const [{ c }, n] of plan) applyEdit(f, { type: "add", card: c.card, foil: c.foil, quantity: n, anyPrinting: true }, now);
        return plan.reduce((s, [, n]) => s + n, 0);
      });
      return json({ added });
    }
    const { file } = await loadSync(a);
    const current = syncDecks(file).find((d) => d.deck.archidektId === id);
    if (!current) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    const { look, facts } = await lookups(current.cards, body);
    const result = await editSync(a, (f, now): { id?: number; uid?: string } => {
      switch (body.action) {
        case "set-quantity":
          setQuantity(f, id, str(body.uid, 100), Number(body.quantity) || 0, now, look);
          return {};
        case "remove":
          removeLines(f, id, uidList(body), now, look);
          return {};
        case "move":
          moveLines(f, id, uidList(body), body.category === null ? AUTO : str(body.category), now, look);
          return {};
        case "auto-all": {
          // Every card (not the commander or the maybeboard) into its auto category (DeckEditor.autoCategoriseAll).
          const sd = syncDecks(f).find((d) => d.deck.archidektId === id);
          moveLines(f, id, (sd?.cards ?? []).filter((c) => !c.commander && !c.maybeboard).map((c) => c.lineUid), AUTO, now, look);
          return {};
        }
        case "add-card": {
          const card = body.card;
          if (!card || typeof card.scryfallId !== "string" || typeof card.name !== "string") throw new EditError("Pick a card first.");
          const fin = resolveFinish(card, FINISHES.includes(body.finish as Finish) ? (body.finish as Finish) : "NONFOIL");
          const info = facts[card.scryfallId];
          const uid = addCard(f, id, card, fin !== "NONFOIL", fin === "ETCHED", Number(body.quantity) || 1, str(body.category) || null, info ? { typeLine: info.t, cmc: info.v } : null, now, look);
          return { uid };
        }
        case "rename-category":
          renameCategory(f, id, str(body.from), str(body.to), now, look);
          return {};
        case "delete-category":
          deleteCategory(f, id, str(body.name), body.moveTo ? str(body.moveTo) : null, now, look);
          return {};
        case "add-category":
          addCategory(f, id, str(body.name), now);
          return {};
        case "hide-category":
          setCategoryHidden(f, id, str(body.name), body.hidden === true, now);
          return {};
        case "pick":
          setPick(f, id, str(body.name), typeof body.value === "string" ? str(body.value) : null, now);
          return {};
        case "token-pick":
          setTokenPick(f, id, str(body.key), typeof body.value === "string" && /^[0-9a-f-]{36}$/.test(body.value) ? body.value : null, now);
          return {};
        case "rename":
          renameDeck(f, id, str(body.name), now);
          return {};
        case "delete":
          deleteDeck(f, id, now);
          return {};
        case "copy":
          return { id: copyDeck(f, id, str(body.name) || `Copy of ${current.deck.name}`, now) };
        case "detach":
          return { id: detachDeck(f, id, now) };
        default:
          throw new EditError("Unknown action.");
      }
    });
    return json({ ok: true, ...result });
  } catch (e) {
    return errorResponse(e);
  }
}
