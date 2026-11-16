// Saved groups of cards (app 1.97, the synced pref deckPackages): made from a deck's cards or pasted text, added to or
// removed from local decks, and shown with the decks that use them. Shared with the browser.

import { nameKey } from "./decknotes";
import type { SyncDeck } from "./deckshared";

export const MAX_PACKAGES = 100;
export const MAX_PACKAGE_CARDS = 200;
export const MAX_PACKAGE_NAME = 60;
const MAX_QUANTITY = 999;

export interface DeckPackageCard {
  name: string;
  scryfallId: string;
  quantity: number;
}

export interface DeckPackage {
  id: string;
  name: string;
  cards: DeckPackageCard[];
}

function cleanCard(raw: unknown): DeckPackageCard | null {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const card = raw as { name?: unknown; scryfallId?: unknown; quantity?: unknown };
  if (typeof card.name !== "string" || typeof card.scryfallId !== "string") return null;
  const name = card.name.trim();
  const scryfallId = card.scryfallId.trim();
  if (!name || !scryfallId) return null;
  const quantity = typeof card.quantity === "number" && Number.isFinite(card.quantity) ? Math.floor(card.quantity) : 1;
  return { name, scryfallId, quantity: Math.max(1, Math.min(MAX_QUANTITY, quantity)) };
}

/** A package cleaned to the app's limits: name trimmed to 60 characters, at most 200 cards. */
export function normalisePackage(raw: unknown): DeckPackage | null {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const pkg = raw as { id?: unknown; name?: unknown; cards?: unknown };
  if (typeof pkg.id !== "string" || !pkg.id) return null;
  if (typeof pkg.name !== "string") return null;
  const cards = (Array.isArray(pkg.cards) ? pkg.cards : [])
    .map(cleanCard)
    .filter((card): card is DeckPackageCard => card !== null)
    .slice(0, MAX_PACKAGE_CARDS);
  return { id: pkg.id, name: pkg.name.trim().slice(0, MAX_PACKAGE_NAME), cards };
}

/** The packages as kept in the pref; unreadable text gives none. */
export function parsePackages(json: string | null | undefined): DeckPackage[] {
  if (!json) return [];
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch {
    return [];
  }
  if (!Array.isArray(raw)) return [];
  return raw
    .map(normalisePackage)
    .filter((pkg): pkg is DeckPackage => pkg !== null)
    .slice(0, MAX_PACKAGES);
}

/** The packages as the synced field's text, cleaned to the limits ("[]" when there are none). */
export function encodePackages(packages: DeckPackage[]): string {
  const out = packages.map(normalisePackage).filter((pkg): pkg is DeckPackage => pkg !== null);
  return JSON.stringify(out.slice(0, MAX_PACKAGES));
}

/**
 * The decks using a package (the app's "Used in"): the decks whose main cards hold all of it, and those holding some
 * of it. Cards are matched by name, ignoring case.
 */
export function packageUse(pkg: DeckPackage, decks: SyncDeck[]): { full: number[]; partly: number[] } {
  const keys = [...new Set(pkg.cards.map((card) => nameKey(card.name)))];
  const full: number[] = [];
  const partly: number[] = [];
  if (keys.length === 0) return { full, partly };
  for (const { deck, cards } of decks) {
    const have = new Set(cards.filter((card) => !card.maybeboard).map((card) => nameKey(card.card.name)));
    const count = keys.filter((key) => have.has(key)).length;
    if (count === keys.length) full.push(deck.archidektId);
    else if (count > 0) partly.push(deck.archidektId);
  }
  return { full, partly };
}
