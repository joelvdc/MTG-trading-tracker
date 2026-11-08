// The app's Scan tab list (ScansScreen): cards scanned on a phone wait in the sync file's "scans" until they go to a
// binder, a deck, or are discarded. The website shows them in the collection ("Scanned" chip) and does the same,
// following the app's rules (MtgRepository.scansToCollection, DeckEditor.addScans / fromScans). The camera stays on
// the phone; "To trade" waits for trades on the website.
import { CONDITIONS, finishesOf, LANGUAGES, resolveFinish } from "./cards";
import { applyCollectionEdit } from "./collection";
import { addCard, createDeck, fallbackCategory, type CardFacts, type Lookups, type NewLine } from "./deckedit";
import type { SyncDeck } from "./deckshared";
import type { Currency } from "./money";
import type { PriceFile } from "./prices";
import { cardmarketPrice, conditionNote, finishOf, isApprox, unitPrice, type Finish, type PricingSetup } from "./pricing";
import { addDeletion, bump, compareUid, newUid, type CardRef, type Json, type SyncFile } from "./syncfile";
import { checkCard, EditError, sameName } from "./wishlist";

/** A scanned card as the app keeps it (ScannedCard; ids are local to each phone, so 0 here). */
export interface ScannedCard {
  id: number;
  card: CardRef;
  foil: boolean;
  etched: boolean;
  condition: string;
  language: string;
  quantity: number;
  /** False when only the name was readable, so the printing is Scryfall's default guess. */
  exactPrinting: boolean;
  scannedAt: number;
  uid: string | null;
  updatedAt: number;
}

/** One line of the website's scan list. */
export interface ScanRow {
  uid: string;
  card: CardRef;
  finish: Finish;
  condition: string;
  language: string;
  quantity: number;
  exactPrinting: boolean;
  scannedAt: number;
  /** One copy, in euros, from the app's price source (null: no price). */
  unitEur: number | null;
  approx: boolean;
  condNote: string | null;
}

/** The scan list as the website's Scanned panel gets it (/api/scans). */
export interface ScanView {
  rows: ScanRow[];
  binders: { uid: string; name: string }[];
  /** Local decks (the ones the website can add to), by name. */
  decks: { id: number; name: string }[];
  currency: Currency;
  perEuro: number;
  priceTypeLabel: string;
  readOnly: boolean;
}

const MAX_QUANTITY = 9999;
/** The most scans one change may touch. */
const MAX_AT_ONCE = 2000;

export function scansOf(file: SyncFile): ScannedCard[] {
  return ((file.data.scans as Json[] | undefined) ?? []) as unknown as ScannedCard[];
}

function setScans(file: SyncFile, list: ScannedCard[]): void {
  list.sort((a, b) => compareUid(a.uid, b.uid));
  file.data.scans = list as unknown as Json;
}

/** Copies waiting in the scan list (the Scanned chip's number). */
export function scanCount(file: SyncFile): number {
  return scansOf(file)
    .filter((s) => s.uid)
    .reduce((n, s) => n + (s.quantity ?? 1), 0);
}

/** The scan list with prices as the app shows them (ScanRow.unitPrice), newest scans first. */
export function scanRows(file: SyncFile, setup: PricingSetup, prices: PriceFile | null): ScanRow[] {
  return scansOf(file)
    .filter((s) => s.uid)
    .map((s): ScanRow => {
      const finish = finishOf(s.foil, s.etched);
      const cm = cardmarketPrice(s.card, s.foil, setup.type, prices);
      return {
        uid: s.uid!,
        card: s.card,
        finish,
        condition: s.condition ?? "NM",
        language: s.language ?? "EN",
        quantity: s.quantity ?? 1,
        exactPrinting: s.exactPrinting !== false,
        scannedAt: s.scannedAt ?? 0,
        unitEur: unitPrice(setup, s.card.scryfallId, finish, s.condition ?? "NM", cm),
        approx: isApprox(setup, s.card.scryfallId, finish, s.condition ?? "NM"),
        condNote: conditionNote(setup, s.card.scryfallId, finish, s.condition ?? "NM"),
      };
    })
    .sort((a, b) => b.scannedAt - a.scannedAt || compareUid(a.uid, b.uid));
}

/** Where scans go in a deck: an existing local deck, or a new local deck with [name]. */
export type DeckTarget = { deckId: number } | { newName: string };

export type ScanEdit =
  /** Fixes a scan: another printing of the same card, finish, condition, language, copies. */
  | { type: "update"; uid: string; card?: CardRef; finish?: Finish; condition?: string; language?: string; quantity?: number }
  /** Takes scans off the list; answers with what was taken, for Undo. */
  | { type: "discard"; uids: string[] }
  /** Undo of a discard: the scans come back (with new uids, so no phone's deletion takes them again). */
  | { type: "restore"; items: ScannedCard[] }
  /** Into the collection: a binder's uid, null for Unsorted, or a new binder with [newBinder]. */
  | { type: "toBinder"; uids: string[]; binder: string | null; newBinder?: string; keep: boolean }
  | { type: "toDeck"; uids: string[]; target: DeckTarget; keep: boolean };

export interface ScanEditResult {
  /** The scans taken off by a discard (for Undo). */
  removed?: ScannedCard[];
  /** The deck the cards went to (a new deck's id). */
  deckId?: number;
  /** How many copies went somewhere. */
  copies?: number;
}

/** What "toDeck" needs looked up first: each card's type line and mana value, and the auto categories. */
export interface DeckLookups {
  facts: Record<string, CardFacts>;
  look: Lookups;
}

function pickUids(list: ScannedCard[], uids: unknown): ScannedCard[] {
  if (!Array.isArray(uids) || uids.length === 0) throw new EditError("No scanned cards chosen.");
  if (uids.length > MAX_AT_ONCE) throw new EditError("Too many cards at once.");
  const want = new Set(uids.filter((u) => typeof u === "string"));
  const found = list.filter((s) => s.uid && want.has(s.uid));
  if (found.length === 0) throw new EditError("Those scanned cards aren't in the list any more (sent on a phone?).");
  return found;
}

/** Takes [gone] off the list and records the deletions, so every phone drops them too. */
function drop(file: SyncFile, list: ScannedCard[], gone: ScannedCard[], now: number): void {
  const out = new Set(gone);
  for (const s of gone) addDeletion(file, s.uid!, bump(s.updatedAt ?? 0, now));
  setScans(
    file,
    list.filter((s) => !out.has(s)),
  );
}

function checkQuantity(q: unknown): number {
  if (typeof q !== "number" || !Number.isInteger(q) || q < 1 || q > MAX_QUANTITY) throw new EditError(`Copies must be a whole number from 1 to ${MAX_QUANTITY}.`);
  return q;
}

/** The lines of a new deck made from scans (DeckEditor.fromScans): one per printing and finish. */
export function deckLinesFromScans(scans: ScannedCard[], d: DeckLookups): NewLine[] {
  const groups = new Map<string, ScannedCard[]>();
  for (const s of scans) {
    const k = `${s.card.scryfallId}|${s.foil}|${s.etched}`;
    groups.set(k, [...(groups.get(k) ?? []), s]);
  }
  return [...groups.values()].map((same) => {
    const first = same[0];
    const f = d.facts[first.card.scryfallId];
    const types = (f?.typeLine ?? "").split(" — ")[0];
    const a = d.look.auto.get(first.card.scryfallId);
    const auto = [a?.category, ...(a?.extra ?? [])].filter((x): x is string => !!x);
    const cats = auto.length ? auto : [fallbackCategory(types)];
    return {
      card: first.card,
      quantity: Math.min(MAX_QUANTITY, same.reduce((n, s) => n + (s.quantity ?? 1), 0)),
      category: cats[0],
      categories: cats.join("\n"),
      types,
      cmc: f?.cmc ?? 0,
      commander: false,
      foil: first.foil,
      etched: first.etched,
      gameChanger: false,
      addedInApp: false,
      maybeboard: false,
      archidektCardId: null,
      label: null,
    };
  });
}

/** Applies [edit] to [file] at [now]. [deck] is needed for "toDeck". ([makeUid] is for tests.) */
export function applyScanEdit(file: SyncFile, edit: ScanEdit, now: number, deck: DeckLookups | null = null, makeUid: () => string = newUid): ScanEditResult {
  const list = scansOf(file);
  switch (edit.type) {
    case "update": {
      const s = list.find((x) => x.uid === edit.uid);
      if (!s) throw new EditError("That scanned card isn't in the list any more (sent on a phone?).");
      if (edit.card !== undefined) {
        const card = checkCard(edit.card);
        if (!sameName(card.name, s.card.name)) throw new EditError("That's another card, not a printing of this one.");
        if (card.scryfallId !== s.card.scryfallId) {
          s.card = card;
          s.exactPrinting = true;
          const f = resolveFinish(card, edit.finish ?? finishOf(s.foil, s.etched));
          s.foil = f !== "NONFOIL";
          s.etched = f === "ETCHED";
        }
      }
      if (edit.finish !== undefined) {
        if (!finishesOf(s.card).includes(edit.finish)) throw new EditError("This printing isn't made in that finish.");
        s.foil = edit.finish !== "NONFOIL";
        s.etched = edit.finish === "ETCHED";
      }
      if (edit.condition !== undefined) {
        if (!CONDITIONS.some((c) => c[0] === edit.condition)) throw new EditError("Unknown condition.");
        s.condition = edit.condition;
      }
      if (edit.language !== undefined) {
        if (!LANGUAGES.some((l) => l[0] === edit.language)) throw new EditError("Unknown language.");
        s.language = edit.language;
      }
      if (edit.quantity !== undefined) s.quantity = checkQuantity(edit.quantity);
      s.updatedAt = bump(s.updatedAt ?? 0, now);
      setScans(file, list);
      return {};
    }
    case "discard": {
      const gone = pickUids(list, edit.uids);
      drop(file, list, gone, now);
      return { removed: gone.map((s) => ({ ...s })) };
    }
    case "restore": {
      if (!Array.isArray(edit.items) || edit.items.length === 0 || edit.items.length > MAX_AT_ONCE) throw new EditError("Nothing to bring back.");
      for (const it of edit.items) {
        const card = checkCard(it.card);
        const f = resolveFinish(card, finishOf(!!it.foil, !!it.etched));
        list.push({
          id: 0,
          card,
          foil: f !== "NONFOIL",
          etched: f === "ETCHED",
          condition: CONDITIONS.some((c) => c[0] === it.condition) ? it.condition : "NM",
          language: LANGUAGES.some((l) => l[0] === it.language) ? it.language : "EN",
          quantity: Number.isInteger(it.quantity) && it.quantity >= 1 && it.quantity <= MAX_QUANTITY ? it.quantity : 1,
          exactPrinting: it.exactPrinting !== false,
          scannedAt: typeof it.scannedAt === "number" ? it.scannedAt : now,
          uid: makeUid(),
          updatedAt: now,
        });
      }
      setScans(file, list);
      return {};
    }
    case "toBinder": {
      const chosen = pickUids(list, edit.uids);
      const binder = edit.newBinder?.trim() ? applyCollectionEdit(file, { type: "addBinder", name: edit.newBinder }, now, null, makeUid).uid : edit.binder;
      // The app's scansToCollection: each scan joins the same pile if there is one (addToCollection).
      for (const s of chosen) {
        applyCollectionEdit(
          file,
          {
            type: "add",
            card: s.card,
            quantity: Math.min(MAX_QUANTITY, s.quantity ?? 1),
            finish: resolveFinish(s.card, finishOf(s.foil, s.etched)),
            condition: s.condition ?? "NM",
            language: LANGUAGES.some((l) => l[0] === s.language) ? s.language : "EN",
            binder,
          },
          now,
          null,
          makeUid,
        );
      }
      if (!edit.keep) drop(file, scansOf(file), chosen.map((c) => scansOf(file).find((x) => x.uid === c.uid)!).filter(Boolean), now);
      return { copies: chosen.reduce((n, s) => n + (s.quantity ?? 1), 0) };
    }
    case "toDeck": {
      if (!deck) throw new EditError("The cards couldn't be looked up; try again.");
      const chosen = pickUids(list, edit.uids);
      let deckId: number;
      if ("newName" in edit.target) {
        const name = String(edit.target.newName ?? "").trim();
        if (!name) throw new EditError("Give the new deck a name.");
        const copies = chosen.reduce((n, s) => n + (s.quantity ?? 1), 0);
        deckId = createDeck(file, name, deckLinesFromScans(chosen, deck), `From ${copies} scanned cards`, "", now, deck.look);
      } else {
        deckId = Number(edit.target.deckId);
        const sd = ((file.data.decks as Json[] | undefined) ?? []) as unknown as SyncDeck[];
        if (!sd.some((d) => d.deck.archidektId === deckId)) throw new EditError("That deck isn't there any more.");
        if (deckId >= 0) throw new EditError("Decks from Archidekt are edited on Archidekt. Make it a local deck first (on the deck's page).");
        // DeckEditor.addScans: each scan next to the same printing and finish, else into its auto category.
        for (const s of chosen) addCard(file, deckId, s.card, s.foil, s.etched, s.quantity ?? 1, null, deck.facts[s.card.scryfallId] ?? null, now, deck.look);
      }
      if (!edit.keep) drop(file, scansOf(file), chosen.map((c) => scansOf(file).find((x) => x.uid === c.uid)!).filter(Boolean), now);
      return { deckId, copies: chosen.reduce((n, s) => n + (s.quantity ?? 1), 0) };
    }
  }
  throw new EditError("Unknown change.");
}
