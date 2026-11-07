// How the deck page lays its cards out: grouping by category, card type, mana value or colour, sorting within a group,
// and which cards have a back face to flip to. Pure helpers, used by the deck page and its card windows.
import { caseless, sections, type GroupBy, type Section } from "./deckshared";

export type DeckGroup = GroupBy | "MANA_VALUE" | "COLOUR";
export type DeckSort = "MANA_VALUE" | "NAME";

export const GROUP_LABELS: Record<DeckGroup, string> = { CATEGORY: "Categories", TYPE: "Card type", MANA_VALUE: "Mana value", COLOUR: "Colour" };

interface Line {
  name: string;
  cmc: number;
  commander: boolean;
  category: string;
  types: string;
}

/** Sorts a group's lines by mana value (then name) or by name; [descending] turns it round. */
export function sortRows<T extends Line>(rows: T[], by: DeckSort, descending: boolean): T[] {
  const cmp = (a: T, b: T) => (by === "MANA_VALUE" ? a.cmc - b.cmc || caseless(a.name, b.name) : caseless(a.name, b.name));
  return [...rows].sort((a, b) => (descending ? cmp(b, a) : cmp(a, b)));
}

const COLOUR_ORDER = ["W", "U", "B", "R", "G"];
const COLOUR_TITLES: Record<string, string> = { W: "White", U: "Blue", B: "Black", R: "Red", G: "Green" };
export const MULTICOLOUR = "Multicolour";
export const COLOURLESS = "Colourless";

/** The colours in a mana cost ("{2}{W}{U/B}" gives "WUB", in WUBRG order); faces of a double-faced card count together. */
export function costColours(cost: string | null | undefined): string {
  const found = new Set<string>();
  for (const m of (cost ?? "").matchAll(/\{([^}]+)\}/g)) for (const p of m[1].toUpperCase().split("/")) if (COLOUR_ORDER.includes(p)) found.add(p);
  return COLOUR_ORDER.filter((c) => found.has(c)).join("");
}

/** The group title of a colour string: one colour by name, several as Multicolour, none as Colourless. */
export function colourTitle(colours: string): string {
  return colours.length === 0 ? COLOURLESS : colours.length === 1 ? COLOUR_TITLES[colours] : MULTICOLOUR;
}

/**
 * The deck's lines in groups, each sorted. Commander(s) come first (as in the app), then the groups: categories
 * alphabetically, card types in the app's order, mana values rising, or colours in WUBRG order, then multicolour and
 * colourless. [colours] gives a line's colours (e.g. from its mana cost).
 */
export function layoutSections<T extends Line>(rows: T[], group: DeckGroup, sort: DeckSort, descending: boolean, colours: (row: T) => string): Section<T>[] {
  let out: Section<T>[];
  if (group === "CATEGORY" || group === "TYPE") out = sections(rows, group);
  else {
    out = [];
    const commanders = rows.filter((r) => r.commander);
    if (commanders.length > 0) out.push({ title: commanders.length > 1 ? "Commanders" : "Commander", rows: commanders });
    const groups = new Map<string, { order: number; rows: T[] }>();
    for (const r of rows.filter((x) => !x.commander)) {
      let title: string;
      let order: number;
      if (group === "MANA_VALUE") {
        const v = Math.max(0, Math.floor(r.cmc));
        title = `Mana value ${v}`;
        order = v;
      } else {
        const c = colours(r);
        title = colourTitle(c);
        order = c.length === 1 ? COLOUR_ORDER.indexOf(c) : c.length === 0 ? COLOUR_ORDER.length + 1 : COLOUR_ORDER.length;
      }
      if (!groups.has(title)) groups.set(title, { order, rows: [] });
      groups.get(title)!.rows.push(r);
    }
    for (const [title, g] of [...groups.entries()].sort((a, b) => a[1].order - b[1].order)) out.push({ title, rows: g.rows });
  }
  return out.map((s) => ({ title: s.title, rows: sortRows(s.rows, sort, descending) }));
}

const DOUBLE_FACED = new Set(["transform", "modal_dfc", "meld", "double_faced_token", "reversible_card"]);

/**
 * Whether the card's picture has a back face. With Scryfall's layout it is exact; without (not looked up yet, or the
 * collection, which doesn't keep it) a type line with two faces is taken as a hint, and the picture falls back to the
 * front when no back turns up (split and adventure cards have none).
 */
export function hasBackFace(layout: string | null | undefined, typeLine: string | null | undefined): boolean {
  if (layout) return DOUBLE_FACED.has(layout);
  const t = typeLine ?? "";
  return t.includes(" // ") && !t.includes("Adventure");
}

/** The picture of the back face: Scryfall's image address with /front/ replaced by /back/. */
export function backImageUrl(url: string): string {
  return url.replace("/front/", "/back/");
}
