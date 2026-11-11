// Shared links (/s/<code>): read-only pages of a deck, a game, a binder or the whole collection that anyone with the
// address can open. They show the owner's prices in their chosen price source, never purchase prices, notes or anything
// else private, and are always worked out from the sync file as it is now. Switched off links answer "off".
import { randomBytes } from "node:crypto";
import type { CkFile } from "./cardkingdom";
import type { DetailsFile } from "./carddetails";
import { collectionView, type CollectionView } from "./collview";
import { deckView, syncDecks } from "./decks";
import { bracketName, POWER_SOURCES, sections } from "./deckshared";
import { gamesOf } from "./games";
import type { Game } from "./gameshared";
import type { Legality } from "./legality";
import type { Currency } from "./money";
import type { PriceFile } from "./prices";
import type { Finish } from "./pricing";
import { binders, type SyncFile } from "./syncfile";
import type { ShareKind, ShareLink } from "./accounts";

export const SHARE_KINDS: ShareKind[] = ["deck", "game", "binder", "collection"];
/** The binder target that means Unsorted. */
export const UNSORTED_TARGET = "~";

/** A new link's code: 22 random URL-safe characters (128 bits), so nobody can guess one. */
export function newShareCode(): string {
  return randomBytes(16).toString("base64url");
}

export function validCode(code: string): boolean {
  return /^[A-Za-z0-9_-]{16,40}$/.test(code);
}

/** The prices as every shared page states them. */
export interface SharedPrices {
  currency: Currency;
  perEuro: number;
  /** "Prices: Cardmarket trend, in EUR · price lists from Cardmarket 2026-11-12". */
  line: string;
}

/** One pile of a shared binder or collection (no purchase price, notes or date added). */
export interface SharedPile {
  name: string;
  set: string;
  setName: string;
  number: string;
  finish: Finish;
  condition: string;
  language: string;
  quantity: number;
  signed: boolean;
  altered: boolean;
  /** The binder's name ("Unsorted" when it has none). */
  binder: string;
  unitEur: number | null;
  approx: boolean;
  image: string | null;
}

export interface SharedDeckLine {
  name: string;
  quantity: number;
  set: string;
  number: string;
  finish: Finish;
  gameChanger: boolean;
  unitEur: number | null;
  image: string | null;
}

export type SharedView =
  | { kind: "deck"; owner: string; title: string; prices: SharedPrices; deck: SharedDeck }
  | { kind: "collection" | "binder"; owner: string; title: string; prices: SharedPrices; piles: SharedPile[]; binders: string[] }
  | { kind: "game"; owner: string; title: string; game: Game };

export interface SharedDeck {
  name: string;
  commanders: string;
  colorIdentity: string;
  cover: string | null;
  cards: number;
  bracket: string | null;
  power: string | null;
  sections: { title: string; lines: SharedDeckLine[] }[];
  legality: Legality | null;
}

export function pricesOf(v: Pick<CollectionView, "currency" | "perEuro" | "sourceLabel" | "priceTypeLabel" | "pricesNote">): SharedPrices {
  const note = v.pricesNote.replace(/^Price lists from /, "price lists from ").replace(/^Prices: /, "");
  return { currency: v.currency, perEuro: v.perEuro, line: `Prices: ${v.sourceLabel} ${v.priceTypeLabel.toLowerCase()}, in ${v.currency} · ${note}` };
}

/** Whether the account's file has the item a link would show. */
export function hasTarget(file: SyncFile, kind: ShareKind, target: string): boolean {
  switch (kind) {
    case "collection":
      return target === "";
    case "binder":
      return target === UNSORTED_TARGET || binders(file).some((b) => b.uid === target);
    case "deck":
      return /^-?\d+$/.test(target) && syncDecks(file).some((d) => d.deck.archidektId === Number(target));
    case "game":
      return gamesOf(file).some((g) => g.uid === target);
  }
}

/** The item's name for the Settings list and the page title: "Deck: Atraxa", "Binder: Trade binder"… (null: gone). */
export function shareTitle(file: SyncFile, kind: ShareKind, target: string): string | null {
  switch (kind) {
    case "collection":
      return "Collection";
    case "binder":
      if (target === UNSORTED_TARGET) return "Unsorted";
      return binders(file).find((b) => b.uid === target)?.name ?? null;
    case "deck":
      return syncDecks(file).find((d) => String(d.deck.archidektId) === target)?.deck.name ?? null;
    case "game": {
      const g = gamesOf(file).find((x) => x.uid === target);
      if (!g) return null;
      const day = new Date(g.startedAt).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "Europe/Copenhagen" });
      const w = g.draw ? null : g.seats.find((s) => s.place === 1);
      return `Game of ${day}${g.draw ? ", a draw" : w ? `, won by ${w.name}` : ""}`;
    }
  }
}

/** What a shared page shows; null when the item is gone. [legality] is worked out by the caller for decks. */
export function sharedView(
  file: SyncFile,
  link: Pick<ShareLink, "kind" | "target">,
  owner: string,
  prices: PriceFile | null,
  details: DetailsFile | null,
  ck: CkFile | null,
  legality: Legality | null = null,
): SharedView | null {
  const title = shareTitle(file, link.kind, link.target);
  if (title === null) return null;
  if (link.kind === "game") {
    const g = gamesOf(file).find((x) => x.uid === link.target)!;
    // The note may be private; the turn times stay (the page shows only the totals).
    return { kind: "game", owner, title, game: { ...g, note: null } };
  }
  const cv = collectionView(file, prices, details, ck);
  const p = pricesOf(cv);
  if (link.kind === "deck") {
    const v = deckView(file, Number(link.target), prices, details, ck);
    if (!v) return null;
    const main = v.rows.filter((r) => !r.maybeboard);
    const d = v.deck;
    return {
      kind: "deck",
      owner,
      title,
      prices: p,
      deck: {
        name: d.name,
        commanders: d.commanders,
        colorIdentity: d.colorIdentity,
        cover: v.cover,
        cards: main.reduce((n, r) => n + r.quantity, 0),
        bracket: d.bracketRealistic != null ? `Bracket ${d.bracketRealistic}${bracketName(d.bracketRealistic) ? ` (${bracketName(d.bracketRealistic)})` : ""}` : null,
        power: v.power != null ? `Power ${v.power.toFixed(1)} (${POWER_SOURCES[v.powerSource].label})` : null,
        sections: sections(main, "CATEGORY").map((s) => ({
          title: s.title,
          lines: s.rows.map((r) => ({
            name: r.name,
            quantity: r.quantity,
            set: r.card.setCode,
            number: r.card.collectorNumber,
            finish: r.finish,
            gameChanger: r.gameChanger,
            unitEur: r.unitEur,
            image: r.card.imageUrl,
          })),
        })),
        legality,
      },
    };
  }
  const names = new Map(cv.binders.map((b) => [b.uid, b.name]));
  const rows = link.kind === "collection" ? cv.rows : cv.rows.filter((r) => (link.target === UNSORTED_TARGET ? r.binder === null : r.binder === link.target));
  return {
    kind: link.kind,
    owner,
    title,
    prices: p,
    binders: link.kind === "collection" ? ["Unsorted", ...cv.binders.map((b) => b.name)] : [],
    piles: rows.map((r) => ({
      name: r.card.name,
      set: r.card.setCode,
      setName: r.card.setName,
      number: r.card.collectorNumber,
      finish: r.finish,
      condition: r.condition,
      language: r.language,
      quantity: r.quantity,
      signed: r.signed,
      altered: r.altered,
      binder: (r.binder && names.get(r.binder)) || "Unsorted",
      unitEur: r.unitEur,
      approx: r.approx,
      image: r.card.imageUrl,
    })),
  };
}
