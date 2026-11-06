// Preconstructed Commander decks from MTGJSON's deck lists (the app's Precons.kt): adding one splits the price paid
// over its cards by what each is worth today, so the cards add up to what the deck cost; basic lands aren't added.
import { resolveFinish } from "./cards";
import type { Finish } from "./pricing";
import type { OrderedCard } from "./orders";
import type { CardRef } from "./syncfile";

export const LIST_URL = "https://mtgjson.com/api/v5/DeckList.json";
export const deckUrl = (fileName: string) => `https://mtgjson.com/api/v5/decks/${encodeURIComponent(fileName)}.json`;
export const TYPE = "Commander Deck";
/** A card worth less than this still weighs this much, so no card comes out at nothing. */
export const FLOOR = 0.05;

export interface PreconInfo {
  code: string;
  fileName: string;
  name: string;
  releaseDate: string | null;
}

export interface PreconCard {
  name: string;
  count: number;
  isFoil: boolean;
  setCode: string;
  number: string;
  scryfallId: string | null;
  types: string[];
  supertypes: string[];
}

const BASICS = ["plains", "island", "swamp", "mountain", "forest", "wastes"];

export function isBasicLand(c: PreconCard): boolean {
  if (c.supertypes.includes("Basic") && c.types.includes("Land")) return true;
  const names = [c.name, ...c.name.split(/ \/\/? /)].map((n) => n.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim());
  return names.some((n) => BASICS.includes(n) || BASICS.includes(n.replace(/^snow covered /, "")));
}

/** The Commander decks in MTGJSON's list, newest first. */
export function parseList(json: unknown): PreconInfo[] {
  const data = (json as { data?: unknown[] })?.data ?? [];
  return data
    .map((d) => d as Record<string, unknown>)
    .filter((d) => d.type === TYPE && typeof d.fileName === "string" && typeof d.name === "string")
    .map((d) => ({ code: String(d.code ?? ""), fileName: d.fileName as string, name: d.name as string, releaseDate: typeof d.releaseDate === "string" ? d.releaseDate : null }))
    .sort((a, b) => (b.releaseDate ?? "").localeCompare(a.releaseDate ?? "") || a.name.localeCompare(b.name));
}

/** Decks whose name or set code holds every word of [query]. */
export function searchPrecons(all: PreconInfo[], query: string): PreconInfo[] {
  const words = query.toLowerCase().split(" ").filter((w) => w.trim());
  return all.filter((d) => words.every((w) => d.name.toLowerCase().includes(w) || w === d.code.toLowerCase()));
}

function toCard(c: Record<string, unknown>): PreconCard {
  const ids = (c.identifiers ?? {}) as Record<string, unknown>;
  return {
    name: String(c.name ?? ""),
    count: typeof c.count === "number" && c.count > 0 ? c.count : 1,
    isFoil: c.isFoil === true,
    setCode: String(c.setCode ?? ""),
    number: String(c.number ?? ""),
    scryfallId: typeof ids.scryfallId === "string" ? ids.scryfallId : null,
    types: Array.isArray(c.types) ? c.types.map(String) : [],
    supertypes: Array.isArray(c.supertypes) ? c.supertypes.map(String) : [],
  };
}

/** The deck's name and its cards without basic lands, each printing and finish once with its count. */
export function deckCards(json: unknown): { name: string; releaseDate: string | null; cards: PreconCard[] } {
  const d = ((json as { data?: Record<string, unknown> })?.data ?? {}) as Record<string, unknown>;
  const all = ["commander", "mainBoard", "sideBoard"].flatMap((k) => (Array.isArray(d[k]) ? (d[k] as Record<string, unknown>[]) : [])).map(toCard);
  const by = new Map<string, PreconCard>();
  for (const c of all) {
    if (isBasicLand(c) || !c.scryfallId) continue;
    const k = `${c.scryfallId}|${c.isFoil}`;
    const had = by.get(k);
    if (had) had.count += c.count;
    else by.set(k, { ...c });
  }
  return { name: String(d.name ?? ""), releaseDate: typeof d.releaseDate === "string" ? d.releaseDate : null, cards: [...by.values()] };
}

/**
 * The whole deck to make a local deck from (DeckEditor.fromPrecon): commander and main board, basic lands too (the
 * sideboard holds extras, not deck cards), each printing and finish once with its count.
 */
export function deckList(json: unknown): { name: string; releaseDate: string | null; commanders: string[]; cards: PreconCard[] } {
  const d = ((json as { data?: Record<string, unknown> })?.data ?? {}) as Record<string, unknown>;
  const part = (k: string) => (Array.isArray(d[k]) ? (d[k] as Record<string, unknown>[]) : []).map(toCard);
  const commanders = part("commander");
  const by = new Map<string, PreconCard>();
  for (const c of [...commanders, ...part("mainBoard")]) {
    if (!c.scryfallId) continue;
    const k = `${c.scryfallId}|${c.isFoil}`;
    const had = by.get(k);
    if (had) had.count += c.count;
    else by.set(k, { ...c });
  }
  return { name: String(d.name ?? ""), releaseDate: typeof d.releaseDate === "string" ? d.releaseDate : null, commanders: [...new Set(commanders.map((c) => c.name))], cards: [...by.values()] };
}

export interface PricedCard {
  card: CardRef;
  finish: Finish;
  quantity: number;
  /** Its share of the deck's price, per copy. */
  price: number;
  worth: number;
}

const cents = (v: number) => Math.round(v * 100) / 100;

/**
 * Splits [paid] over the cards (each with today's price of one copy, null when unknown) by worth, [FLOOR] the least
 * a copy weighs. Shares are whole cents; the cents lost or gained by rounding go to the most valuable card there is
 * one of, so the shares add up to [paid].
 */
export function split(paid: number, cards: { card: CardRef; deck: PreconCard; worth: number | null }[]): PricedCard[] {
  if (cards.length === 0) return [];
  const weight = (w: number | null) => Math.max(w ?? 0, FLOOR);
  const total = cards.reduce((n, c) => n + weight(c.worth) * c.deck.count, 0);
  const shares = cards
    .map((c) => ({
      card: c.card,
      finish: resolveFinish(c.card, c.deck.isFoil ? "FOIL" : "NONFOIL"),
      quantity: c.deck.count,
      price: cents((paid * weight(c.worth)) / total),
      worth: c.worth ?? 0,
    }))
    .sort((a, b) => b.worth - a.worth);
  const off = cents(paid - shares.reduce((n, s) => n + s.price * s.quantity, 0));
  const taker = shares.find((s) => s.quantity === 1) ?? shares[0];
  taker.price = cents(Math.max(0, taker.price + off / taker.quantity));
  return shares;
}

/** A share as an order line: priced as a near mint English copy. */
export function orderedOf(p: PricedCard): OrderedCard {
  const foil = p.finish !== "NONFOIL";
  return {
    name: p.card.name,
    setCode: p.card.setCode,
    number: p.card.collectorNumber,
    quantity: p.quantity,
    price: p.price,
    foil,
    condition: "NM",
    language: "EN",
    scryfallId: p.card.scryfallId,
    cardmarketId: (foil ? p.card.cardmarketFoilId : null) ?? p.card.cardmarketId,
  };
}
