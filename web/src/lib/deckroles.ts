// A card's own tags and the roles a deck's cards fill (app 1.97, DeckRoles): Ramp, Draw, Removal, Wipes, Tutor,
// Protection, Recursion. Own tags win; otherwise the deck categories and the website's automatic categories decide.
// Shared with the browser.

import { nameKey } from "./decknotes";

export const ROLES = ["Ramp", "Draw", "Removal", "Wipes", "Tutor", "Protection", "Recursion"] as const;
export type Role = (typeof ROLES)[number];

/** The deck category (or auto category) names that make a role, lower case as the app compares them. */
const ROLE_OF: Record<string, Role> = {
  ramp: "Ramp",
  "mana rock": "Ramp",
  "mana rocks": "Ramp",
  mana: "Ramp",
  draw: "Draw",
  "card draw": "Draw",
  "card advantage": "Draw",
  removal: "Removal",
  "spot removal": "Removal",
  interaction: "Removal",
  counterspell: "Removal",
  counterspells: "Removal",
  wipe: "Wipes",
  wipes: "Wipes",
  "board wipe": "Wipes",
  "board wipes": "Wipes",
  sweeper: "Wipes",
  sweepers: "Wipes",
  wrath: "Wipes",
  wraths: "Wipes",
  tutor: "Tutor",
  tutors: "Tutor",
  protection: "Protection",
  recursion: "Recursion",
};

export const MAX_TAG = 30;
export const MAX_TAGS = 10;

/** A card's tags as the pref keeps them: trimmed, at most 30 characters, no duplicates ignoring case, at most 10. */
export function normaliseTags(tags: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of tags) {
    if (typeof raw !== "string") continue;
    const tag = raw.trim().slice(0, MAX_TAG);
    if (!tag) continue;
    const key = tag.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(tag);
    if (out.length === MAX_TAGS) break;
  }
  return out;
}

/**
 * The role tags of a card (DeckRoles.tagsOf): its own tags as spelled when it has any, else the roles its categories
 * and automatic categories name, in the roles' order.
 */
export function tagsOf(name: string, categories: string[], ownTags: string[] | null | undefined, auto: string[]): string[] {
  void name;
  if (ownTags) return [...ownTags];
  const found = new Set<string>();
  for (const raw of [...auto, ...categories]) {
    if (typeof raw !== "string") continue;
    const role = ROLE_OF[raw.trim().toLowerCase()];
    if (role) found.add(role);
  }
  return ROLES.filter((role) => found.has(role));
}

/** A card as the role counts see it. */
export interface RoleCard {
  name: string;
  types: string;
  quantity: number;
  commander: boolean;
  maybeboard: boolean;
  categories: string[];
  ownTags: string[] | null;
  auto: string[];
}

export type RoleStatus = "low" | "ok" | "high" | "none";

export interface RoleCount {
  role: string;
  count: number;
  status: RoleStatus;
}

/** The guideline ranges under the deck header: below is low, above is high, both ends included in ok. */
const GUIDELINES: Record<string, [number, number]> = {
  Lands: [35, 38],
  Ramp: [10, 12],
  Draw: [10, 12],
  Removal: [8, 12],
  Wipes: [2, 4],
};

function statusOf(role: string, count: number): RoleStatus {
  const guide = GUIDELINES[role];
  if (!guide) return "none";
  return count < guide[0] ? "low" : count > guide[1] ? "high" : "ok";
}

/**
 * Lands, the roles and a deck's other own tags with their copy counts (DeckRoles.counts): maybeboard and commanders
 * left out; Lands, Ramp, Draw and Removal always, the other roles when they have cards, then up to five own tags.
 */
export function roleCounts(cards: RoleCard[]): RoleCount[] {
  const main = cards.filter((card) => !card.maybeboard && !card.commander);
  const countOf = (role: string) =>
    main
      .filter((card) => tagsOf(card.name, card.categories, card.ownTags, card.auto).some((tag) => tag.toLowerCase() === role.toLowerCase()))
      .reduce((sum, card) => sum + card.quantity, 0);
  const lands = main.filter((card) => card.types.split(/\s+/).includes("Land")).reduce((sum, card) => sum + card.quantity, 0);
  const item = (role: string, count: number): RoleCount => ({ role, count, status: statusOf(role, count) });
  const out: RoleCount[] = [item("Lands", lands), item("Ramp", countOf("Ramp")), item("Draw", countOf("Draw")), item("Removal", countOf("Removal"))];
  for (const role of ["Wipes", "Tutor", "Protection", "Recursion"]) {
    const count = countOf(role);
    if (count > 0) out.push(item(role, count));
  }
  // Own tags that aren't a role, counted per tag ignoring case and shown with the first spelling met.
  const other = new Map<string, { role: string; count: number }>();
  for (const card of main) {
    const seen = new Set<string>();
    for (const raw of card.ownTags ?? []) {
      if (typeof raw !== "string") continue;
      const tag = raw.trim();
      if (!tag || ROLES.some((role) => role.toLowerCase() === tag.toLowerCase())) continue;
      const key = tag.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      const found = other.get(key) ?? { role: tag, count: 0 };
      found.count += card.quantity;
      other.set(key, found);
    }
  }
  const extras = [...other.values()]
    .filter((tag) => tag.count > 0)
    .sort((a, b) => b.count - a.count || (a.role.toLowerCase() < b.role.toLowerCase() ? -1 : a.role.toLowerCase() > b.role.toLowerCase() ? 1 : 0))
    .slice(0, 5);
  for (const tag of extras) out.push({ role: tag.role, count: tag.count, status: "none" });
  return out;
}

/** The card tags as the pref keeps them, by nameKey; unreadable text gives none. */
export function parseCardTags(json: string | null | undefined): Record<string, string[]> {
  if (!json) return {};
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch {
    return {};
  }
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
  const out: Record<string, string[]> = {};
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    if (!Array.isArray(value)) continue;
    const tags = normaliseTags(value.filter((tag): tag is string => typeof tag === "string"));
    if (tags.length > 0) out[nameKey(key)] = tags;
  }
  return Object.fromEntries(Object.keys(out).sort().map((k) => [k, out[k]]));
}

/** The card tags as the synced field's text, keys sorted and cards without tags left out ("{}" when there are none). */
export function encodeCardTags(tags: Record<string, string[]>): string {
  const out: Record<string, string[]> = {};
  for (const [key, value] of Object.entries(tags)) {
    const list = normaliseTags(value);
    if (list.length > 0) out[nameKey(key)] = list;
  }
  return JSON.stringify(Object.fromEntries(Object.keys(out).sort().map((k) => [k, out[k]])));
}
