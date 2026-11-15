// A deck's cards as text to copy elsewhere: Moxfield's export ("1 Sol Ring (CMR) 263 *F* #Ramp"), Archidekt's
// ("1x Sol Ring (cmr) 263 [Ramp]"), MTG Arena's (with "Commander", "Deck" and "Sideboard" headings) and a plain list
// ("1 Sol Ring"). lib/decktext.ts reads them back. Pure code, also used in the browser.
import { caseless, type DeckCard } from "./deckshared";

export type ExportFormat = "moxfield" | "archidekt" | "arena" | "plain";

export interface ExportOptions {
  /** Add each line's categories (Moxfield's tags, Archidekt's categories). */
  categories: boolean;
  /** Add the maybeboard as a last section. */
  maybeboard: boolean;
}

/** The heading above each section; Arena calls the maybeboard its sideboard. */
const HEADINGS: Record<ExportFormat, { commander: string; deck: string; maybe: string }> = {
  moxfield: { commander: "Commander", deck: "Deck", maybe: "Maybeboard" },
  archidekt: { commander: "Commander", deck: "Deck", maybe: "Maybeboard" },
  arena: { commander: "Commander", deck: "Deck", maybe: "Sideboard" },
  plain: { commander: "Commander", deck: "Deck", maybe: "Maybeboard" },
};

/** A line's categories, from its categories field (one per line), else from its category (DeckCard.categoryList). */
function categoriesOf(c: DeckCard): string[] {
  const list = (c.categories ?? "")
    .split("\n")
    .map((x) => x.trim())
    .filter((x) => x);
  return list.length ? list : c.category ? [c.category] : [];
}

/** One line: quantity, name, printing, finish and categories, as [format] writes them. */
function line(c: DeckCard, format: ExportFormat, withCategories: boolean): string {
  if (format === "plain") return `${c.quantity} ${c.card.name}`;
  // Moxfield writes the set code in capitals, Archidekt in small letters.
  const set = format === "archidekt" ? c.card.setCode.toLowerCase() : c.card.setCode.toUpperCase();
  const number = c.card.collectorNumber ? ` ${c.card.collectorNumber}` : "";
  const head = `${c.quantity}${format === "archidekt" ? "x" : ""} ${c.card.name} (${set})${number}`;
  // Arena's lists have no finish marks and no categories.
  if (format === "arena") return head;
  const finish = c.etched ? " *E*" : c.foil ? " *F*" : "";
  const tags = withCategories ? categoriesOf(c) : [];
  // Moxfield's tags can't hold spaces: "Card Draw" is #Card_Draw.
  return format === "archidekt" ? `${head}${finish}${tags.length ? ` [${tags.join(",")}]` : ""}` : `${head}${finish}${tags.map((t) => ` #${t.replace(/\s+/g, "_")}`).join("")}`;
}

/**
 * A deck's cards as text: the commander, the main deck and (when asked) the maybeboard, each under its heading with
 * one blank line between the sections. The lines come sorted by name; lines of the same card and printing stay apart.
 */
export function deckToText(cards: DeckCard[], format: ExportFormat, opts: ExportOptions): string {
  const headings = HEADINGS[format];
  const byName = (a: DeckCard, b: DeckCard) => caseless(a.card.name, b.card.name);
  const sections: string[] = [];
  const add = (heading: string, list: DeckCard[]) => {
    if (list.length) sections.push([heading, ...list.sort(byName).map((c) => line(c, format, opts.categories))].join("\n"));
  };
  add(headings.commander, cards.filter((c) => c.commander && !c.maybeboard));
  add(headings.deck, cards.filter((c) => !c.commander && !c.maybeboard));
  add(headings.maybe, opts.maybeboard ? cards.filter((c) => c.maybeboard) : []);
  return sections.join("\n\n");
}
