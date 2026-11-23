import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { loadCardKingdom } from "@/lib/cardkingdom";
import { loadDetails } from "@/lib/carddetails";
import { deckAnswer } from "@/lib/deckanswer";
import { ensureDeckDetails, type DeckInfo } from "@/lib/deckdetails";
import { diffLines, recordHistory } from "@/lib/deckhistory";
import { parsePackages, type DeckPackage } from "@/lib/deckpackages";
import { nameKey } from "@/lib/decknotes";
import { missingPlan, ownedByName, syncDecks } from "@/lib/decks";
import { loadPrices } from "@/lib/pricestore";
import { editSync, loadSync } from "@/lib/store";
import { prefs } from "@/lib/syncfile";
import { applyEdit, EditError } from "@/lib/wishlist";
import { FINISHES, resolveFinish } from "@/lib/cards";
import {
  addCard,
  addCategory,
  addPasted,
  addToMaybeboard,
  autoFor,
  setCardNote,
  setCategoryOrder,
  setPrimer,
  AUTO,
  COMMANDER,
  copyDeck,
  deleteCategory,
  deleteDeck,
  fallbackCategory,
  MAYBEBOARD,
  restoreDeck,
  setRetired,
  detachDeck,
  inOrder,
  moveLines,
  removeLines,
  renameCategory,
  renameDeck,
  setCategoryHidden,
  setFinishes,
  setPick,
  setPrintings,
  setPrinting,
  setQuantities,
  setQuantity,
  setTokenPick,
  type Lookups,
  type NewLine,
} from "@/lib/deckedit";
import { isBasicName, type DeckCard, type SyncDeck } from "@/lib/deckshared";
import type { Finish } from "@/lib/pricing";
import type { CardRef } from "@/lib/syncfile";
import { autoCategories } from "@/lib/autocategories";
import { loadFromArchidekt, updateOne } from "@/lib/deckload";
import { lookupCollectionFull, type LookedUp } from "@/lib/scryfall";
import { deckToText, type ExportFormat } from "@/lib/deckexport";
import { previewText, textLines } from "@/lib/deckimport";
import { textCardCount } from "@/lib/decktext";

export const dynamic = "force-dynamic";
// Reading a deck from Archidekt can take a while, so up to two minutes.
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
    const answer = id === null ? null : await deckAnswer(a, file, id, prices, details, ck);
    if (!answer) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    return json(answer);
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
  /** Undo: the deck as it was before an edit, as that edit's answer's `before` had it. */
  snapshot?: SyncDeck;
  /** Bulk printing changes: each line's new printing. */
  changes?: { uid: string; card: CardRef }[];
  /** A pasted decklist, and the board and categories it goes into. */
  text?: string;
  board?: string;
  useTags?: boolean;
  /** Copying the deck as text: the format, its categories and its maybeboard. */
  format?: string;
  categories?: boolean;
  maybeboard?: boolean;
  /** The primer, or one card's note. */
  primer?: string;
  note?: string;
  /** A saved package to add to or remove from the deck. */
  packageId?: string;
};

const str = (v: unknown, max = 200): string => (typeof v === "string" ? v.slice(0, max) : "");
const uidList = (b: Body): string[] => (Array.isArray(b.uids) ? b.uids.filter((u) => typeof u === "string").slice(0, 2000) : typeof b.uid === "string" ? [b.uid] : []);
const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();
/** A card's front face, lower case: how pasted lists are matched against the deck's lines. */
const front = (name: string) => name.split(" // ")[0].trim().toLowerCase();
const FORMATS: ExportFormat[] = ["moxfield", "archidekt", "arena", "plain"];

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

/** What an edit answers with: a new deck's id, a new line's uid, and the deck as it was before the edit (for undo). */
type EditResult = { id?: number; uid?: string; before?: SyncDeck | null; added?: number; skipped?: string[]; notFound?: string[]; removed?: number };

/** The deck's main lines as the change history compares them. */
const mainLines = (cards: DeckCard[]) => cards.map((c) => ({ name: c.card.name, quantity: c.quantity, maybeboard: c.maybeboard }));

/**
 * Records what an edit did to the deck's main lines (Deck.history, source "website"). A deck that wasn't there before
 * (a restored deletion) records nothing: it comes back as it was, its own history included.
 */
function recordDeckChange(before: SyncDeck | null, after: SyncDeck | null, now: number): void {
  if (!before || !after) return;
  const change = diffLines(mainLines(before?.cards ?? []), mainLines(after.cards));
  if (Object.keys(change.added).length === 0 && Object.keys(change.removed).length === 0) return;
  after.deck.history = recordHistory(after.deck.history, now, "website", change);
}

/**
 * Deck actions. Decks from Archidekt: "reload" and "refresh" (read the list from Archidekt again; both do the same
 * now), "update" (one deck of "Update all decks", with its [need]). Every deck: "missing-to-wishlist"
 * (the cards the collection lacks go on the wishlist, any printing), "copy", "detach" (a deck from Archidekt becomes a
 * local deck), "hide-category", "pick" (the printing you own to show), "token-pick" (a token's printing), "delete"
 * (never on Archidekt itself), "category-order" (the deck's own category order), "add-maybe" (into the maybeboard; a
 * deck from Archidekt's local one), "remove" (on a deck from Archidekt only its local maybeboard's cards), "retire",
 * "export" (its cards as text to copy: Moxfield's, Archidekt's, Arena's or plain) and "restore" (undo: the deck back
 * as an earlier answer's `before` had it, also when it was deleted since). Local decks only: "set-quantity", "printing"
 * (another printing of a line's card), "remove", "move", "auto-all", "add-card", "rename-category", "delete-category",
 * "add-category", "rename", the bulk edits "bulk-quantity", "bulk-finish" and "bulk-printing", and "paste-preview"
 * (what a pasted list holds, and which of its cards the deck has already) with "paste" (the list into the deck).
 * Every edit but "copy", "detach", "pick", "token-pick", "hide-category" and "restore" answers with the deck as it was
 * before it (`before`), which "restore" then puts back. "hide-category" leaves it out: whether a category is shown is a
 * separate setting, so putting the deck back would not show it again. Restoring a deck that is gone works before the
 * deck's own check.
 */
export async function POST(req: Request, { params }: Params) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const id = deckId((await params).id);
    if (id === null) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    const body = ((await req.json()) ?? {}) as Body;
    // Decks from Archidekt: read again from there, or one step of "Update all decks".
    if (body.action === "reload" || body.action === "refresh" || body.action === "update") {
      if (id < 0) throw new EditError("A local deck isn't on Archidekt.");
      if (body.action === "update") {
        const need = body.need === "changed" ? body.need : "check";
        return json({ ok: true, ...(await updateOne(a, id, need)) });
      }
      await loadFromArchidekt(a, id, {});
      return json({ ok: true });
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
    // Undo of a deletion: the deck isn't there any more, so this comes before the check that it is.
    if (body.action === "restore") {
      const snapshot = body.snapshot;
      if (!snapshot || typeof snapshot !== "object" || !snapshot.deck || typeof snapshot.deck.archidektId !== "number" || !Array.isArray(snapshot.cards)) {
        throw new EditError("That change can't be undone any more.");
      }
      if (snapshot.deck.archidektId !== id) throw new EditError("That change belongs to another deck.");
      await editSync(a, (f, now) => {
        const before = structuredClone(syncDecks(f).find((d) => d.deck.archidektId === id) ?? null);
        restoreDeck(f, snapshot, now);
        recordDeckChange(before, syncDecks(f).find((d) => d.deck.archidektId === id) ?? null, now);
      });
      return json({ ok: true });
    }
    if (!current) return json({ error: "That deck isn't in the sync file (any more)." }, 404);
    // What a pasted list holds, and which of its cards the deck has already (the page warns about those).
    if (body.action === "paste-preview") {
      const p = await previewText(str(body.text, 60_000));
      const maybe = body.board === "MAYBE";
      const have = new Set(current.cards.filter((c) => c.maybeboard === maybe).map((c) => front(c.card.name)));
      const names = [...new Set(p.found.flatMap((c) => (c ? [c.card.name] : [])))].sort();
      return json({
        cards: textCardCount(p.result),
        lines: p.result.lines.length,
        exact: p.exact,
        byName: p.byName,
        notFound: p.notFound,
        unreadable: p.result.unreadable,
        commanders: p.commanders,
        hasTags: p.result.lines.some((l) => l.tags.length > 0),
        names,
        inDeck: names.filter((n) => !isBasicName(n) && have.has(front(n))),
      });
    }
    // The deck's cards as text to copy elsewhere (also a deck from Archidekt's).
    if (body.action === "export") {
      const format = FORMATS.includes(body.format as ExportFormat) ? (body.format as ExportFormat) : "moxfield";
      return json({ text: deckToText(current.cards, format, { categories: body.categories !== false, maybeboard: body.maybeboard !== false }) });
    }
    // Adding a package: its cards and their printings are looked up before the file is opened, like a pasted list.
    let packageAdd: { pkg: DeckPackage; found: Map<string, LookedUp>; auto: Awaited<ReturnType<typeof autoCategories>> } | null = null;
    if (body.action === "add-package") {
      if (current.deck.archidektId >= 0) throw new EditError("Decks from Archidekt are edited on Archidekt. Make it a local deck first.");
      const pkg = parsePackages(prefs(file).deckPackages).find((p) => p.id === str(body.packageId, 200));
      if (!pkg) throw new EditError("That package isn't there any more.");
      let looked: LookedUp[] = [];
      try {
        looked = await lookupCollectionFull(pkg.cards.map((c) => ({ id: c.scryfallId })));
      } catch {
        throw new EditError("Scryfall couldn't be reached.");
      }
      packageAdd = {
        pkg,
        found: new Map(looked.map((l) => [l.card.scryfallId, l])),
        auto: await autoCategories(pkg.cards.map((c) => ({ scryfallId: c.scryfallId, name: c.name }))),
      };
    }
    const { look, facts } = await lookups(current.cards, body);
    // A pasted list is looked up on Scryfall before the file is opened, so the edit itself stays quick.
    let pasted: { lines: NewLine[]; notFound: string[] } | null = null;
    if (body.action === "paste") {
      const p = await previewText(str(body.text, 60_000));
      const { lines } = await textLines(p, body.useTags !== false, null);
      if (lines.length === 0) throw new EditError("None of the cards were found on Scryfall.");
      const maybe = body.board === "MAYBE";
      pasted = {
        notFound: p.notFound,
        lines: lines.map((l): NewLine => {
          if (maybe) return { ...l, category: MAYBEBOARD, categories: MAYBEBOARD, maybeboard: true, commander: false };
          // Into the main deck: a pasted "Commander" section (or a board left out of the deck) goes in as normal cards,
          // in the category of their card type.
          const to = same(l.category, COMMANDER) || same(l.category, MAYBEBOARD) ? fallbackCategory(l.types) : l.category;
          const rest = (l.categories ?? "").split("\n").filter((x) => x.trim() && !same(x, l.category));
          return { ...l, category: to, categories: [to, ...rest].join("\n"), maybeboard: false, commander: false };
        }),
      };
    }
    const result = await editSync(a, (f, now): EditResult => {
      // The deck as it was before this edit, for undo: any line edit, category change, rename, retire or delete.
      const before = structuredClone(syncDecks(f).find((d) => d.deck.archidektId === id) ?? null);
      const edit = (): EditResult => {
        switch (body.action) {
          case "set-quantity":
            setQuantity(f, id, str(body.uid, 100), Number(body.quantity) || 0, now, look);
            return { before };
          case "bulk-quantity":
            setQuantities(f, id, uidList(body), Number(body.quantity) || 0, now, look);
            return { before };
          case "bulk-finish":
            if (!FINISHES.includes(body.finish as Finish)) throw new EditError("Pick a finish first.");
            setFinishes(f, id, uidList(body), body.finish as Finish, now, look);
            return { before };
          case "bulk-printing": {
            const changes = (Array.isArray(body.changes) ? body.changes : [])
              .filter((x) => !!x && typeof x.uid === "string" && !!x.card && typeof x.card.scryfallId === "string" && typeof x.card.name === "string")
              .slice(0, 2000)
              .map((x) => ({ uid: x.uid.slice(0, 100), card: x.card }));
            setPrintings(f, id, changes, now, look);
            return { before };
          }
          case "paste": {
            const r = addPasted(f, id, pasted!.lines, now, look);
            return { added: r.added, skipped: r.skipped, notFound: pasted!.notFound, before };
          }
          case "printing": {
            const card = body.card;
            if (!card || typeof card.scryfallId !== "string" || typeof card.name !== "string") throw new EditError("Pick a printing first.");
            setPrinting(f, id, str(body.uid, 100), card, FINISHES.includes(body.finish as Finish) ? (body.finish as Finish) : null, now, look);
            return { before };
          }
          case "remove":
            removeLines(f, id, uidList(body), now, look);
            return { before };
          case "move":
            moveLines(f, id, uidList(body), body.category === null ? AUTO : str(body.category), now, look);
            return { before };
          case "auto-all": {
            // Every card (not the commander or the maybeboard) into its auto category (DeckEditor.autoCategoriseAll).
            const sd = syncDecks(f).find((d) => d.deck.archidektId === id);
            moveLines(f, id, (sd?.cards ?? []).filter((c) => !c.commander && !c.maybeboard).map((c) => c.lineUid), AUTO, now, look);
            return { before };
          }
          case "add-card": {
            const card = body.card;
            if (!card || typeof card.scryfallId !== "string" || typeof card.name !== "string") throw new EditError("Pick a card first.");
            const fin = resolveFinish(card, FINISHES.includes(body.finish as Finish) ? (body.finish as Finish) : "NONFOIL");
            const info = facts[card.scryfallId];
            const uid = addCard(f, id, card, fin !== "NONFOIL", fin === "ETCHED", Number(body.quantity) || 1, str(body.category) || null, info ? { typeLine: info.t, cmc: info.v } : null, now, look);
            return { uid, before };
          }
          case "add-maybe": {
            // Into the maybeboard: a local deck's own, a deck from Archidekt's local one (app 1.86).
            const card = body.card;
            if (!card || typeof card.scryfallId !== "string" || typeof card.name !== "string") throw new EditError("Pick a card first.");
            const fin = resolveFinish(card, FINISHES.includes(body.finish as Finish) ? (body.finish as Finish) : "NONFOIL");
            const info = facts[card.scryfallId];
            return { uid: addToMaybeboard(f, id, card, fin !== "NONFOIL", fin === "ETCHED", info ? { typeLine: info.t, cmc: info.v } : null, now, look), before };
          }
          case "category-order":
            setCategoryOrder(f, id, Array.isArray(body.names) ? body.names.filter((n) => typeof n === "string").map((n) => n.slice(0, 200)).slice(0, 300) : [], now);
            return { before };
          case "rename-category":
            renameCategory(f, id, str(body.from), str(body.to), now, look);
            return { before };
          case "delete-category":
            deleteCategory(f, id, str(body.name), body.moveTo ? str(body.moveTo) : null, now, look);
            return { before };
          case "add-category":
            addCategory(f, id, str(body.name), now);
            return { before };
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
            return { before };
          case "delete":
            deleteDeck(f, id, now);
            return { before };
          case "retire":
            setRetired(f, id, body.retired !== false, now);
            return { before };
          case "set-primer":
            setPrimer(f, id, typeof body.primer === "string" ? body.primer : "", now);
            return { before };
          case "set-card-note":
            setCardNote(f, id, str(body.name, 200), typeof body.note === "string" ? body.note : "", now);
            return { before };
          case "add-package": {
            const { pkg, found, auto } = packageAdd!;
            const sd = syncDecks(f).find((d) => d.deck.archidektId === id);
            if (!sd) throw new EditError("That deck isn't in the sync file (any more).");
            const here = new Set(sd.cards.filter((c) => !c.maybeboard).map((c) => nameKey(c.card.name)));
            const skipped: string[] = [];
            const lines: NewLine[] = [];
            for (const card of pkg.cards) {
              if (here.has(nameKey(card.name))) {
                if (!skipped.some((name) => same(name, card.name))) skipped.push(card.name);
                continue;
              }
              const looked = found.get(card.scryfallId);
              if (!looked) continue;
              const types = looked.typeLine.split(" — ")[0];
              const category = autoFor({ identity: look.identity, auto }, looked.card.scryfallId, types);
              lines.push({
                card: looked.card,
                quantity: card.quantity,
                category: category.to,
                types,
                cmc: looked.cmc,
                commander: false,
                foil: false,
                etched: false,
                gameChanger: false,
                addedInApp: false,
                maybeboard: false,
                categories: [category.to, ...category.extra].join("\n"),
                archidektCardId: null,
                label: null,
              });
            }
            if (lines.length === 0) return { added: 0, skipped, before };
            const r = addPasted(f, id, lines, now, look);
            return { added: r.added, skipped, before };
          }
          case "remove-package": {
            const pkg = parsePackages(prefs(f).deckPackages).find((p) => p.id === str(body.packageId, 200));
            if (!pkg) throw new EditError("That package isn't there any more.");
            const sd = syncDecks(f).find((d) => d.deck.archidektId === id);
            if (!sd) throw new EditError("That deck isn't in the sync file (any more).");
            const keys = new Set(pkg.cards.map((card) => nameKey(card.name)));
            const gone = sd.cards.filter((c) => !c.maybeboard && !c.commander && keys.has(nameKey(c.card.name)));
            removeLines(f, id, gone.map((c) => c.lineUid), now, look);
            return { removed: gone.reduce((sum, c) => sum + c.quantity, 0), before };
          }
          case "copy":
            return { id: copyDeck(f, id, str(body.name) || `Copy of ${current.deck.name}`, now) };
          case "detach":
            return { id: detachDeck(f, id, now) };
          default:
            throw new EditError("Unknown action.");
        }
      };
      const result = edit();
      // One history entry for whatever this action did to the deck's main lines (an undo included).
      recordDeckChange(before, syncDecks(f).find((d) => d.deck.archidektId === id) ?? null, now);
      return result;
    });
    return json({ ok: true, ...result });
  } catch (e) {
    return errorResponse(e);
  }
}
