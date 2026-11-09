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
  addToMaybeboard,
  setCategoryOrder,
  AUTO,
  copyDeck,
  deleteCategory,
  deleteDeck,
  setRetired,
  detachDeck,
  inOrder,
  maybeCategoryOf,
  moveLines,
  removeLines,
  renameCategory,
  renameDeck,
  setCategoryHidden,
  setPick,
  setPrinting,
  setQuantity,
  setTokenPick,
  type Lookups,
} from "@/lib/deckedit";
import type { DeckCard } from "@/lib/deckshared";
import type { Finish } from "@/lib/pricing";
import type { CardRef } from "@/lib/syncfile";
import { autoCategories } from "@/lib/autocategories";
import { loadFromArchidekt, scoreAgain, updateOne } from "@/lib/deckload";
import { isUser } from "@/lib/session";
import { deckStatsCards, keptWins, parseWin } from "@/lib/winturns";
import { gamesOf } from "@/lib/games";
import { gamesOfDeck } from "@/lib/gameshared";

export const dynamic = "force-dynamic";
// Commander Salt can take a while (it retries its import), so up to two minutes.
export const maxDuration = 120;

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
    let info: Awaited<ReturnType<typeof ensureDeckDetails>> = {};
    let detailsError: string | null = null;
    try {
      // The maybeboard's cards too: their lines show the mana cost and the filter reads their rules text.
      info = await ensureDeckDetails([...new Set(synced.cards.map((c) => c.card.scryfallId))]);
    } catch (e) {
      detailsError = e instanceof Error ? e.message : "Scryfall couldn't be reached.";
    }
    const stats = deckStatsCards(synced, info);
    // The maybeboard's cards with their details, apart: the stats and the win turn leave them out.
    const maybeStats = synced.cards
      .filter((c) => c.maybeboard)
      .map((c) => statsCard({ name: c.card.name, scryfallId: c.card.scryfallId, quantity: c.quantity, cmc: c.cmc, types: c.types, commander: false }, info[c.card.scryfallId]));
    // The category each maybeboard line shows under when the maybeboard is split (display only; autoCategories
    // answers an empty map when the table can't be read, and the lines then go by card type).
    const maybeLines = synced.cards.filter((c) => c.maybeboard);
    const auto = maybeLines.length ? await autoCategories(maybeLines.map((c) => ({ scryfallId: c.card.scryfallId, name: c.card.name }))) : new Map();
    const maybeCategories = Object.fromEntries(maybeLines.map((c) => [c.lineUid, maybeCategoryOf(c, auto)]));
    const tokenInfo: Record<string, { oi: string | null; pt: string | null }> = {};
    // Scryfall's layout per printing, to tell which cards have a back face to flip to.
    const layouts: Record<string, string> = {};
    for (const s of [...stats, ...maybeStats]) if (info[s.scryfallId]?.y) layouts[s.scryfallId] = info[s.scryfallId].y!;
    for (const s of stats) for (const t of s.tokens ?? []) if (info[t.id]) tokenInfo[t.id] = { oi: info[t.id].oi, pt: info[t.id].pt };
    // The Commander Salt data as the app keeps it, for the win turn (its combos) and the app's power level.
    // The win turn kept on the website for this list, if there is one (lib/winturns.ts); the page checks its key.
    const kept = isUser(a) && id !== null ? (await keptWins(a.accountId)).get(id) : undefined;
    const winTurn = kept ? { key: kept.key, win: parseWin(kept) } : null;
    return json({ ...view, stats, maybeStats, maybeCategories, tokenInfo, layouts, detailsError, saltCard: synced.deck.saltCard ?? null, winTurn, games: gamesOfDeck(gamesOf(file), id!) });
  } catch (e) {
    return errorResponse(e);
  }
}

type Body = {
  action?: string;
  need?: string;
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
  retired?: boolean;
  value?: string | null;
  key?: string;
  card?: CardRef;
  finish?: Finish;
  names?: string[];
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
 * Deck actions. Decks from Archidekt: "reload" (the list), "refresh" (the list and Commander Salt), "rescore"
 * (Commander Salt only), "update" (one deck of "Update all decks", with its [need]). Every deck: "missing-to-wishlist" (the cards the collection lacks go on the wishlist, any printing),
 * "copy", "detach" (a deck from Archidekt becomes a local deck), "hide-category", "pick" (the printing you own to
 * show), "token-pick" (a token's printing), "delete" (never on Archidekt itself), "category-order" (the deck's own
 * category order), "add-maybe" (into the maybeboard; a deck from Archidekt's local one), "remove" (on a deck from
 * Archidekt only its local maybeboard's cards). Local decks only: "set-quantity", "printing" (another printing of a line's card), "remove", "move", "auto-all",
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
    // Decks from Archidekt: read again from there (with or without Commander Salt), or one step of "Update all decks".
    if (body.action === "reload" || body.action === "refresh" || body.action === "rescore" || body.action === "update") {
      if (id < 0) throw new EditError("A local deck isn't on Archidekt.");
      if (body.action === "rescore") return json({ ok: true, scoreError: await scoreAgain(a, id) });
      if (body.action === "update") {
        const need = body.need === "changed" || body.need === "unscored" ? body.need : "check";
        return json({ ok: true, ...(await updateOne(a, id, need)) });
      }
      const r = await loadFromArchidekt(a, id, { score: body.action === "refresh" });
      return json({ ok: true, scoreError: r.scoreError });
    }
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
        case "printing": {
          const card = body.card;
          if (!card || typeof card.scryfallId !== "string" || typeof card.name !== "string") throw new EditError("Pick a printing first.");
          setPrinting(f, id, str(body.uid, 100), card, FINISHES.includes(body.finish as Finish) ? (body.finish as Finish) : null, now, look);
          return {};
        }
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
        case "add-maybe": {
          // Into the maybeboard: a local deck's own, a deck from Archidekt's local one (app 1.86).
          const card = body.card;
          if (!card || typeof card.scryfallId !== "string" || typeof card.name !== "string") throw new EditError("Pick a card first.");
          const fin = resolveFinish(card, FINISHES.includes(body.finish as Finish) ? (body.finish as Finish) : "NONFOIL");
          const info = facts[card.scryfallId];
          return { uid: addToMaybeboard(f, id, card, fin !== "NONFOIL", fin === "ETCHED", info ? { typeLine: info.t, cmc: info.v } : null, now, look) };
        }
        case "category-order":
          setCategoryOrder(f, id, Array.isArray(body.names) ? body.names.filter((n) => typeof n === "string").map((n) => n.slice(0, 200)).slice(0, 300) : [], now);
          return {};
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
        case "retire":
          setRetired(f, id, body.retired !== false, now);
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
