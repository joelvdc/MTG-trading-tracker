// The collection table's columns (since app 1.90): which can be shown, their groups, and the choice remembered per
// browser (the Columns dialog). Name is always shown, first after the tick box.
import type { SortField } from "./colquery";

export type ColumnId =
  | "qty"
  | "set"
  | "number"
  | "rarity"
  | "type"
  | "mv"
  | "colors"
  | "identity"
  | "faces"
  | "edhrec"
  | "gc"
  | "cond"
  | "lang"
  | "finish"
  | "marks"
  | "binder"
  | "added"
  | "notes"
  | "price"
  | "total"
  | "change"
  | "changeMoney"
  | "paid"
  | "profit"
  | "cardmarket"
  | "tcgplayer"
  | "cardkingdom"
  | "ckPays"
  | "decks"
  | "inDecks"
  | "spare";

export type ColumnGroup = "Card" | "Your copy" | "Prices" | "Decks";

export interface ColumnDef {
  id: ColumnId;
  /** The column title. */
  label: string;
  /** What it shows, in the Columns dialog. */
  hint: string;
  group: ColumnGroup;
  /** Clicking the title sorts by this. */
  field?: SortField;
  /** Table cell class: "num" right-aligns, "center" centres. */
  className?: string;
}

export const COLUMNS: ColumnDef[] = [
  { id: "qty", label: "Qty", hint: "Copies in this pile", group: "Your copy", field: "QUANTITY", className: "num" },
  { id: "set", label: "Set", hint: "The set's name", group: "Card", field: "SET" },
  { id: "number", label: "No.", hint: "Collector number", group: "Card", field: "NUMBER", className: "num" },
  { id: "rarity", label: "Rarity", hint: "Common, uncommon, rare, mythic", group: "Card", field: "RARITY" },
  { id: "type", label: "Type", hint: "The type line", group: "Card", field: "TYPE" },
  { id: "mv", label: "MV", hint: "Mana value", group: "Card", field: "MANA_VALUE", className: "num" },
  { id: "colors", label: "Colors", hint: "The card's colors", group: "Card", field: "COLOR", className: "center" },
  { id: "identity", label: "Identity", hint: "Color identity (Commander)", group: "Card", className: "center" },
  { id: "faces", label: "Faces", hint: "MDFC, transforming or split / adventure", group: "Card" },
  { id: "edhrec", label: "EDHREC", hint: "EDHREC rank (1 = most played in Commander)", group: "Card", field: "EDHREC", className: "num" },
  { id: "gc", label: "GC", hint: "On the Game Changers list", group: "Card", className: "center" },
  { id: "cond", label: "Cond.", hint: "Condition", group: "Your copy", className: "center" },
  { id: "lang", label: "Lang.", hint: "Language", group: "Your copy", className: "center" },
  { id: "finish", label: "Finish", hint: "Normal, foil, etched…", group: "Your copy" },
  { id: "marks", label: "Marks", hint: "Signed or altered", group: "Your copy" },
  { id: "binder", label: "Binder", hint: "Where the pile is (only when every binder is shown)", group: "Your copy" },
  { id: "added", label: "Added", hint: "Date added", group: "Your copy", field: "RECENT", className: "num" },
  { id: "notes", label: "Notes", hint: "Your notes on the pile", group: "Your copy" },
  { id: "price", label: "Price", hint: "One copy, from your price source", group: "Prices", field: "VALUE", className: "num" },
  { id: "total", label: "Total", hint: "Price × copies", group: "Prices", className: "num" },
  { id: "change", label: "Change", hint: "Cardmarket's trend against its 30-day average, in %", group: "Prices", field: "CHANGE_PCT", className: "num" },
  { id: "changeMoney", label: "Change", hint: "The same change in money, one copy", group: "Prices", field: "CHANGE_MONEY", className: "num" },
  { id: "paid", label: "Paid", hint: "What you paid for one copy", group: "Prices", className: "num" },
  { id: "profit", label: "Profit", hint: "Price minus what you paid, all copies", group: "Prices", className: "num" },
  { id: "cardmarket", label: "Cardmarket", hint: "One copy at Cardmarket", group: "Prices", className: "num" },
  { id: "tcgplayer", label: "TCGplayer", hint: "One copy at TCGplayer", group: "Prices", className: "num" },
  { id: "cardkingdom", label: "Card Kingdom", hint: "One copy at Card Kingdom", group: "Prices", className: "num" },
  { id: "ckPays", label: "CK pays", hint: "What Card Kingdom pays for one copy", group: "Prices", className: "num" },
  { id: "decks", label: "Decks", hint: "How many of your decks use the card (point at it for their names)", group: "Decks", field: "DECKS", className: "num" },
  { id: "inDecks", label: "In decks", hint: "Copies your decks use, all together", group: "Decks", className: "num" },
  { id: "spare", label: "Spare", hint: "Copies you own (every printing) minus copies your decks use", group: "Decks", field: "SPARE", className: "num" },
];

export const COLUMN_GROUPS: ColumnGroup[] = ["Card", "Your copy", "Prices", "Decks"];

/** What the table showed before columns could be chosen. */
export const DEFAULT_COLUMNS: ColumnId[] = ["qty", "set", "cond", "lang", "binder", "price", "total", "change", "added"];

const BY_ID = new Map(COLUMNS.map((c) => [c.id, c]));

export function columnDef(id: ColumnId): ColumnDef {
  return BY_ID.get(id)!;
}

/** A saved choice, cleaned: known columns only, each once; the default when nothing usable was saved. */
export function readColumns(saved: unknown): ColumnId[] {
  if (!Array.isArray(saved)) return DEFAULT_COLUMNS;
  const out: ColumnId[] = [];
  for (const x of saved) if (typeof x === "string" && BY_ID.has(x as ColumnId) && !out.includes(x as ColumnId)) out.push(x as ColumnId);
  // An empty list is a choice too (only the name); a list of nothing usable is not.
  return out.length > 0 || saved.length === 0 ? out : DEFAULT_COLUMNS;
}
