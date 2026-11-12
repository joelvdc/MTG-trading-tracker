// Saved views on the Collection page (website only, 2026-11-13): a name for the binder picked, the search, the filter,
// the sort, the columns, Group printings and list or picture view. Kept with the account (table saved_views), so they
// are there on every computer; the sync file and the phones don't see them.
import { readColumns, type ColumnId } from "./collcolumns";
import { EMPTY_FILTER, SORT_FIELDS, type Filter, type SortLevel } from "./colquery";

export interface SavedView {
  id: string;
  name: string;
  /** "" everything, "~" Unsorted, otherwise a binder's uid. */
  binder: string;
  search: string;
  filter: Filter;
  sorts: SortLevel[];
  columns: ColumnId[];
  grouped: boolean;
  layout: "list" | "images";
}

export const MAX_VIEWS = 30;
export const MAX_NAME = 40;

const str = (v: unknown, max: number) => (typeof v === "string" ? v.slice(0, max) : "");

/** One view from what a browser sent (or the database held), cleaned; null when it can't be a view. */
export function cleanView(v: unknown): SavedView | null {
  if (!v || typeof v !== "object") return null;
  const o = v as Record<string, unknown>;
  const name = str(o.name, MAX_NAME).trim();
  const id = str(o.id, 40);
  if (!name || !/^[A-Za-z0-9_-]{1,40}$/.test(id)) return null;
  const sorts = (Array.isArray(o.sorts) ? o.sorts : [])
    .filter((s): s is SortLevel => !!s && typeof s === "object" && SORT_FIELDS.some((f) => f.field === (s as SortLevel).field))
    .slice(0, 3)
    .map((s) => ({ field: s.field, reversed: !!s.reversed }));
  const filter = o.filter && typeof o.filter === "object" ? { ...EMPTY_FILTER, ...(o.filter as Partial<Filter>) } : EMPTY_FILTER;
  return {
    id,
    name,
    binder: str(o.binder, 40),
    search: str(o.search, 100),
    filter,
    sorts: sorts.length ? sorts : [{ field: "NAME", reversed: false }],
    columns: readColumns(o.columns),
    grouped: o.grouped === true,
    layout: o.layout === "images" ? "images" : "list",
  };
}

/** A whole list, cleaned: known views only, each id once, at most [MAX_VIEWS]. */
export function cleanViews(list: unknown): SavedView[] {
  const out: SavedView[] = [];
  for (const v of Array.isArray(list) ? list : []) {
    const c = cleanView(v);
    if (c && !out.some((x) => x.id === c.id) && out.length < MAX_VIEWS) out.push(c);
  }
  return out;
}
