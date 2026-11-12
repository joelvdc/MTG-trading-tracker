"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/components/api";
import { CollAddDialog } from "@/components/CollAddDialog";
import { CollCardDialog } from "@/components/CollCardDialog";
import { CollGrid } from "@/components/CollGrid";
import { CollTable } from "@/components/CollTable";
import { ColumnsDialog } from "@/components/ColumnsDialog";
import { ArrangeDialog, DeleteBinderDialog, MergeDialog, PaidDialog, SortDialog, sortText } from "@/components/CollectionDialogs";
import { Dialog } from "@/components/Dialog";
import { ExportDialog } from "@/components/ExportDialog";
import { FilterDialog } from "@/components/FilterDialog";
import { ImportDialog } from "@/components/ImportDialog";
import { PreconDialog } from "@/components/PreconDialog";
import { PrintCollectionDialog } from "@/components/PrintLists";
import { ShareDialog } from "@/components/SharePanel";
import { useHoverSetting } from "@/components/HoverCard";
import { TopBar } from "@/components/Nav";
import { ViewSwitch, type Layout } from "@/components/ViewSwitch";
import { ViewsMenu } from "@/components/ViewsMenu";
import { ScansPanel } from "@/components/ScansPanel";
import { WishlistPanel } from "@/components/WishlistPanel";
import { CONDITIONS, LANGUAGES } from "@/lib/cards";
import type { CollectionEdit } from "@/lib/collection";
import { DEFAULT_COLUMNS, readColumns, type ColumnId } from "@/lib/collcolumns";
import { groupRows } from "@/lib/collgroup";
import type { SavedView } from "@/lib/savedviews";
import { BAR_SCANS, BAR_UNSORTED, BAR_WISHLIST, barKeys, type CollectionView, type CollRow } from "@/lib/collview";
import {
  comparator,
  deckCardNames,
  deckUse,
  EMPTY_FILTER,
  filterCount,
  matchesFilter,
  matchesText,
  SORT_FIELDS,
  stacksText,
  type Filter,
  type SortLevel,
} from "@/lib/colquery";
import { format } from "@/lib/money";

/** "" = everything, "~" = Unsorted, "wishlist" = the wishlist, otherwise a binder's uid. */
type BinderPick = string;

const KEY = "mtg.collection";
const PER_PAGE = [25, 50, 100];
const DEFAULT_SORT: SortLevel = { field: "NAME", reversed: false };

interface Saved {
  /** One level (before sorting in levels); read when [sorts] isn't there. */
  sort?: SortLevel;
  sorts?: SortLevel[];
  layout?: Layout;
  binder?: BinderPick;
  filter?: Filter;
  perPage?: number;
  /** The table's columns (since 1.90). */
  columns?: ColumnId[];
  /** Group printings: one table line per card name (website only). */
  grouped?: boolean;
}

function readSaved(): Saved {
  try {
    return (JSON.parse(localStorage.getItem(KEY) ?? "{}") as Saved) ?? {};
  } catch {
    return {};
  }
}

function writeSaved(s: Saved) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ ...readSaved(), ...s }));
  } catch {
    // Not remembered; fine.
  }
}

/** A filter from the address (the stats page links here with one), on top of an empty filter. */
function filterFrom(text: string | null): Filter | null {
  if (!text) return null;
  try {
    const f = JSON.parse(text) as Partial<Filter>;
    return { ...EMPTY_FILTER, ...f };
  } catch {
    return null;
  }
}

export default function CollectionPage() {
  const [view, setView] = useState<CollectionView | null>(null);
  const [folder, setFolder] = useState("");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [lookup, setLookup] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [sorts, setSorts] = useState<SortLevel[]>([DEFAULT_SORT]);
  const [layout, setLayout] = useState<Layout>("list");
  const [hover, setHover] = useHoverSetting();
  const [binder, setBinder] = useState<BinderPick>("");
  const [filter, setFilter] = useState<Filter>(EMPTY_FILTER);
  const [perPage, setPerPage] = useState(50);
  const [columns, setColumns] = useState<ColumnId[]>(DEFAULT_COLUMNS);
  const [choosingColumns, setChoosingColumns] = useState(false);
  const [grouped, setGrouped] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [views, setViews] = useState<SavedView[]>([]);
  const [viewsOn, setViewsOn] = useState(false);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [open, setOpen] = useState<CollRow | null>(null);
  const [adding, setAdding] = useState(false);
  const [filtering, setFiltering] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [precon, setPrecon] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [naming, setNaming] = useState<{ uid: string | null; name: string } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [sorting, setSorting] = useState(false);
  const [merging, setMerging] = useState(false);
  const [deletingBinder, setDeletingBinder] = useState(false);
  const [arranging, setArranging] = useState(false);
  const [pricing, setPricing] = useState(false);
  const [copied, setCopied] = useState(false);
  const lookedUp = useRef(false);

  useEffect(() => {
    const s = readSaved();
    const known = (l: SortLevel | undefined) => !!l && SORT_FIELDS.some((f) => f.field === l.field);
    if (Array.isArray(s.sorts) && s.sorts.length > 0 && s.sorts.length <= 3 && s.sorts.every(known)) setSorts(s.sorts);
    else if (known(s.sort)) setSorts([s.sort!]);
    if (s.layout === "images") setLayout("images");
    if (s.perPage && PER_PAGE.includes(s.perPage)) setPerPage(s.perPage);
    if (s.columns) setColumns(readColumns(s.columns));
    if (s.grouped === true) setGrouped(true);
    const params = new URLSearchParams(window.location.search);
    const fromUrl = filterFrom(params.get("filter"));
    const binderUrl = params.get("binder");
    // The deck pages link here with a card name to search for.
    const searchUrl = params.get("search");
    if (fromUrl || binderUrl != null || searchUrl != null) {
      setFilter(fromUrl ?? EMPTY_FILTER);
      setBinder(binderUrl ?? "");
      setSearch(searchUrl ?? "");
      window.history.replaceState(null, "", "/collection");
    } else {
      if (typeof s.binder === "string") setBinder(s.binder);
      if (s.filter) setFilter({ ...EMPTY_FILTER, ...s.filter });
    }
  }, []);

  const goLogin = useCallback(() => window.location.replace("/login"), []);

  /** Looks up colours, types… for printings the website hasn't seen yet, 75 at a time, then reloads. */
  const fillDetails = useCallback(async (missing: string[]) => {
    for (let i = 0; i < missing.length; i += 75) {
      setLookup(`Looking up card details: ${Math.min(i + 75, missing.length)} of ${missing.length}…`);
      const r = await api<{ cards?: unknown }>("/api/cards/details", { ids: missing.slice(i, i + 75) });
      if (!r.ok) {
        setLookup(null);
        setMessage("Some card details could not be looked up; colours and types may be missing for now.");
        return false;
      }
    }
    setLookup(null);
    return true;
  }, []);

  const load = useCallback(async () => {
    setLoadError(null);
    const [s, c] = await Promise.all([
      api<{ loggedIn: boolean; folder?: string }>("/api/session"),
      api<CollectionView & { noFolder?: boolean }>("/api/collection"),
    ]);
    if (!s.data.loggedIn || c.status === 401 || c.data.loggedOut) return goLogin();
    if (!s.data.folder || c.data.noFolder) return window.location.replace("/settings");
    setFolder(s.data.folder ?? "");
    if (!c.ok || !Array.isArray(c.data.rows)) {
      setLoadError(c.data.error ?? "Could not load the collection.");
      return;
    }
    setView(c.data);
    if (c.data.missing.length > 0 && !lookedUp.current) {
      lookedUp.current = true;
      if (await fillDetails(c.data.missing)) {
        const again = await api<CollectionView>("/api/collection");
        if (again.ok && Array.isArray(again.data.rows)) setView(again.data);
      }
    }
  }, [goLogin, fillDetails]);

  useEffect(() => {
    load();
    api<{ views?: SavedView[]; available?: boolean }>("/api/collection/views").then((r) => {
      if (r.ok && Array.isArray(r.data.views)) {
        setViews(r.data.views);
        setViewsOn(r.data.available !== false);
      }
    });
  }, [load]);

  /** Stores the whole list of saved views; false (with a message) when that didn't work. */
  async function saveViews(list: SavedView[]): Promise<boolean> {
    const r = await api<{ views?: SavedView[] }>("/api/collection/views", { views: list });
    if (r.ok && Array.isArray(r.data.views)) {
      setViews(r.data.views);
      return true;
    }
    setMessage(r.data.error ?? "The views could not be saved.");
    return false;
  }

  /** Sends one edit; returns the uid it ended in, or null when it didn't work (the old view stays). */
  async function edit(e: CollectionEdit): Promise<string | null> {
    setBusy(true);
    setMessage(null);
    const r = await api<CollectionView & { editedUid?: string }>("/api/collection", { edit: e });
    setBusy(false);
    if (r.status === 401 || r.data.loggedOut) {
      goLogin();
      return null;
    }
    if (r.ok && Array.isArray(r.data.rows)) {
      setView(r.data);
      return r.data.editedUid ?? "";
    }
    setMessage(r.data.error ?? "That change did not work.");
    return null;
  }

  /** After a wishlist change: fetch the collection again so the Wishlist chip's number is right. */
  const refreshCounts = useCallback(async () => {
    const c = await api<CollectionView>("/api/collection");
    if (c.ok && Array.isArray(c.data.rows)) setView(c.data);
  }, []);

  function changeSort(next: SortLevel[]) {
    setSorts(next);
    setPage(0);
    writeSaved({ sorts: next });
  }
  function changeLayout(next: Layout) {
    setLayout(next);
    writeSaved({ layout: next });
  }
  function changeBinder(next: BinderPick) {
    setBinder(next);
    setSelected(new Set());
    setPage(0);
    writeSaved({ binder: next });
  }
  function changeFilter(next: Filter) {
    setFilter(next);
    setPage(0);
    writeSaved({ filter: next });
  }
  function changeGrouped(next: boolean) {
    setGrouped(next);
    setExpanded(new Set());
    setPage(0);
    writeSaved({ grouped: next });
  }
  /** Opens a saved view: everything it holds at once. */
  function applyView(v: SavedView) {
    changeBinder(v.binder);
    setSearch(v.search);
    changeFilter(v.filter);
    changeSort(v.sorts);
    setColumns(v.columns);
    writeSaved({ columns: v.columns });
    changeGrouped(v.grouped);
    changeLayout(v.layout);
    setNotice(`View “${v.name}”.`);
  }

  const deckNames = useMemo(() => deckCardNames(view?.decks ?? []), [view]);
  /** Each card's use in the decks against the whole collection (the Decks, In decks and Spare columns and sorts). */
  const use = useMemo(() => deckUse(view?.rows ?? [], view?.decks ?? []), [view]);

  const binderName = useCallback(
    (uid: string | null) => (uid ? (view?.binders.find((b) => b.uid === uid)?.name ?? "Unsorted") : "Unsorted"),
    [view],
  );

  /** The binder picked still exists (it may have been deleted on a phone). */
  const pick: BinderPick =
    view && binder && binder !== "~" && binder !== BAR_WISHLIST && binder !== BAR_SCANS && !view.binders.some((b) => b.uid === binder) ? "" : binder;
  const isWish = pick === BAR_WISHLIST;
  const isScans = pick === BAR_SCANS;

  const inBinder = useMemo(() => {
    if (!view) return [];
    if (pick === "" || pick === BAR_WISHLIST || pick === BAR_SCANS) return view.rows;
    const want = pick === "~" ? null : pick;
    return view.rows.filter((r) => r.binder === want);
  }, [view, pick]);

  const rows = useMemo(() => {
    const words = search.trim();
    const out = inBinder.filter((r) => (!words || matchesText(r, words)) && matchesFilter(filter, r, deckNames, use));
    return out.sort(comparator(sorts, use));
  }, [inBinder, search, filter, deckNames, sorts, use]);

  /** Group printings (the table only; the picture view keeps one tile per pile). */
  const groups = useMemo(() => (grouped && layout === "list" ? groupRows(rows, comparator(sorts, use)) : null), [grouped, layout, rows, sorts, use]);

  // A selection only holds piles that are still there and still shown.
  const shownUids = useMemo(() => new Set(rows.map((r) => r.uid)), [rows]);
  const picked = useMemo(() => [...selected].filter((u) => shownUids.has(u)), [selected, shownUids]);

  const counts = useMemo(() => {
    const m = new Map<string, number>();
    for (const r of view?.rows ?? []) m.set(r.binder ?? "~", (m.get(r.binder ?? "~") ?? 0) + r.quantity);
    return m;
  }, [view]);

  async function logout() {
    await api("/api/logout", {});
    goLogin();
  }

  if (!view) {
    return (
      <>
        <TopBar page="collection" folder={folder} />
        <main className="page wide">
          {loadError ? (
            <div className="stack">
              <div className="bar error">{loadError}</div>
              <div className="inline">
                <button className="btn" onClick={load}>
                  Try again
                </button>
                <a className="btn" href="/settings">
                  Settings
                </a>
                <button className="btn" onClick={logout}>
                  Log out
                </button>
              </div>
            </div>
          ) : (
            <p className="muted center">Loading…</p>
          )}
        </main>
      </>
    );
  }

  const money = (eur: number) => format(eur * view.perEuro, view.currency);
  // With Group printings, the pages count lines (card names), not piles.
  const lines = groups ? groups.length : rows.length;
  const pages = Math.max(1, Math.ceil(lines / perPage));
  const pageAt = Math.min(page, pages - 1);
  const pageGroups = groups ? groups.slice(pageAt * perPage, (pageAt + 1) * perPage) : null;
  const pageRows = pageGroups ? pageGroups.flatMap((g) => g.rows) : rows.slice(pageAt * perPage, (pageAt + 1) * perPage);
  const copies = rows.reduce((n, r) => n + r.quantity, 0);
  const value = rows.reduce((n, r) => n + (r.unitEur ?? 0) * r.quantity, 0);
  const locked = view.readOnly || busy;
  const nFilter = filterCount(filter);
  // The Binder dropdown: Unsorted and the binders in the bar's order (Arrange).
  const binderChoices = view.bar.flatMap((k) =>
    k === BAR_UNSORTED
      ? [{ key: "~", name: "Unsorted", n: counts.get("~") ?? 0 }]
      : view.binders.filter((b) => `b:${b.uid}` === k).map((b) => ({ key: b.uid, name: b.name, n: counts.get(b.uid) ?? 0 })),
  );
  const binderPicked = pick !== "" && !isWish && !isScans;
  const sort = sorts[0];
  const sets = (() => {
    const m = new Map<string, { code: string; name: string; copies: number }>();
    for (const r of inBinder) {
      const k = r.card.setCode.toLowerCase();
      const e = m.get(k) ?? { code: k, name: r.card.setName, copies: 0 };
      e.copies += r.quantity;
      m.set(k, e);
    }
    return [...m.values()].sort((a, b) => b.copies - a.copies || a.name.localeCompare(b.name));
  })();
  const binderLabel = pick === "" ? "collection" : pick === "~" ? "Unsorted" : binderName(pick);
  const toggle = (uid: string) =>
    setSelected((s) => {
      const n = new Set(s);
      if (n.has(uid)) n.delete(uid);
      else n.add(uid);
      return n;
    });

  async function bulk(e: Omit<Extract<CollectionEdit, { type: "bulk" }>, "type" | "uids">) {
    if ((await edit({ type: "bulk", uids: picked, ...e })) != null) setSelected(new Set());
  }

  return (
    <>
      <TopBar page="collection" folder={folder} />
      <main className={picked.length > 0 ? "page wide with-bulk" : "page wide"}>
        {view.readOnly && (
          <div className="bar warn">
            Read only: the app on a phone is newer than this website (sync format {view.format}). Changes are switched off.
          </div>
        )}
        {notice && (
          <div className="bar ok" role="status">
            {notice}
            <button className="link" onClick={() => setNotice(null)}>
              Dismiss
            </button>
          </div>
        )}
        {message && (
          <div className="bar error" role="alert">
            {message}
            <button className="link" onClick={() => setMessage(null)}>
              Dismiss
            </button>
          </div>
        )}
        {lookup && <div className="bar">{lookup}</div>}

        <div className="binder-bar" role="tablist" aria-label="Collection">
          {[
            { key: "", name: "All", n: [...counts.values()].reduce((a, b) => a + b, 0), star: false },
            { key: BAR_WISHLIST, name: "Wishlist", n: view.wishCount, star: true },
            // The app's Scan tab list; shown while scans wait (or while it's open).
            ...(view.scanCount > 0 || pick === BAR_SCANS ? [{ key: BAR_SCANS, name: "Scanned", n: view.scanCount, star: false }] : []),
          ].map((b) => (
            <button
              key={b.key || "all"}
              role="tab"
              aria-selected={pick === b.key}
              className={pick === b.key ? "chip on" : "chip"}
              onClick={() => changeBinder(b.key)}
            >
              {b.star && <span aria-hidden="true">★ </span>}
              {b.name} <span className="count">{b.n}</span>
            </button>
          ))}
          {/* Unsorted and the binders, in the order Arrange sets (the phones' binder bar). */}
          <select
            aria-label="Open a binder"
            className={binderPicked ? "chip binder-pick on" : "chip binder-pick"}
            value={binderPicked ? pick : ""}
            onChange={(e) => e.target.value && changeBinder(e.target.value)}
          >
            <option value="" disabled>
              Binder…
            </option>
            {binderChoices.map((b) => (
              <option key={b.key} value={b.key}>
                {b.name} ({b.n})
              </option>
            ))}
          </select>
          <button className="chip add" disabled={locked} onClick={() => setNaming({ uid: null, name: "" })}>
            ＋ Binder
          </button>
          {pick !== "" && pick !== "~" && !isWish && !isScans && (
            <>
              <button className="chip add" disabled={locked} onClick={() => setNaming({ uid: pick, name: binderName(pick) })}>
                Rename
              </button>
              <button className="chip add" disabled={locked} onClick={() => setMerging(true)}>
                Merge…
              </button>
              <button className="chip add" disabled={locked} onClick={() => setDeletingBinder(true)}>
                Delete…
              </button>
            </>
          )}
          {pick === "~" && (
            <button className="chip add" disabled={locked || (counts.get("~") ?? 0) === 0} onClick={() => setMerging(true)}>
              Move all to…
            </button>
          )}
          <button className="chip add" disabled={locked} onClick={() => setArranging(true)}>
            Arrange
          </button>
        </div>

        {isScans ? (
          <ScansPanel layout={layout} onLayout={changeLayout} hover={hover} onHover={setHover} onLoggedOut={goLogin} onChanged={refreshCounts} />
        ) : isWish ? (
          <WishlistPanel
            layout={layout}
            onLayout={changeLayout}
            hover={hover}
            onHover={setHover}
            onLoggedOut={goLogin}
            onChanged={refreshCounts}
          />
        ) : (
          <>
        <p className="summary">
          {copies} cards · {money(value)} ({view.priceTypeLabel}, {view.sourceLabel})
        </p>
        <p className="muted small">{view.pricesNote}</p>

        <div className="controls">
          <input
            type="search"
            aria-label="Search the collection"
            placeholder="Search name, set or number"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
          <button className={nFilter ? "btn on" : "btn"} onClick={() => setFiltering(true)}>
            Filter{nFilter ? ` (${nFilter})` : ""}
          </button>
          <button className={sorts.length > 1 ? "btn on" : "btn"} title={`Sorted by ${sortText(sorts)}`} onClick={() => setSorting(true)}>
            Sort{sorts.length > 1 ? ` (${sorts.length})` : ""}
          </button>
          {viewsOn && (
            <ViewsMenu
              views={views}
              current={{ binder: pick, search: search.trim(), filter, sorts, columns, grouped, layout }}
              onApply={applyView}
              onSave={saveViews}
            />
          )}
          <ViewSwitch layout={layout} onLayout={changeLayout} hover={hover} onHover={setHover} />
          {layout === "list" && (
            <button className={columns.join() !== DEFAULT_COLUMNS.join() ? "btn on" : "btn"} title="Choose the table's columns" onClick={() => setChoosingColumns(true)}>
              Columns
            </button>
          )}
          {layout === "list" && (
            <button
              className={grouped ? "btn on" : "btn"}
              aria-pressed={grouped}
              title="One line per card name, whatever its set, finish, condition or binder; click a line to see its piles"
              onClick={() => changeGrouped(!grouped)}
            >
              Group printings
            </button>
          )}
          <button className="btn primary" disabled={locked} onClick={() => setAdding(true)}>
            Add card
          </button>
          <a className="btn" href={pick ? `/collection/stats?binder=${encodeURIComponent(pick)}` : "/collection/stats"}>
            Stats
          </a>
          <a className="btn" href="/tradebinder">
            Trade binder
          </a>
          <button className="btn" onClick={() => setExporting(true)}>
            Export CSV
          </button>
          <button className="btn" disabled={rows.length === 0} onClick={() => setPrinting(true)}>
            Print
          </button>
          <button className="btn" onClick={() => setSharing(true)} title={pick === "" ? "Share the whole collection" : `Share ${binderLabel}`}>
            Share
          </button>
          {locked ? (
            <button className="btn" disabled>
              Import ▾
            </button>
          ) : (
            <details className="menu">
              <summary className="btn">Import ▾</summary>
              <div className="menu-items" onClick={(e) => ((e.currentTarget.parentElement as HTMLDetailsElement).open = false)}>
                <button onClick={() => setImporting(true)}>CSV file…</button>
                <button onClick={() => setPrecon(true)}>Preconstructed deck…</button>
              </div>
            </details>
          )}
        </div>

        <p className="muted small showing">
          Showing {groups ? `${groups.length} card names (` : ""}
          {nFilter > 0 || search.trim() ? `${rows.length} of ${inBinder.length}` : rows.length} piles{groups ? ")" : ""}{" "}
          {pick === "" ? "in the whole collection" : pick === "~" ? "not in a binder" : `in “${binderLabel}”`}, sorted by {sortText(sorts)}.
        </p>
        {nFilter > 0 && (
          <p className="small">
            Filtered: {rows.length} of {inBinder.length} piles.{" "}
            <button className="link" onClick={() => changeFilter(EMPTY_FILTER)}>
              Clear the filter
            </button>
          </p>
        )}

        {picked.length > 0 && (
          <div className="bulk-bar" role="region" aria-label="Selected cards">
            <strong>{picked.length} selected</strong>
            <select aria-label="Move the selected cards" disabled={locked} value="" onChange={(e) => e.target.value && bulk({ binder: e.target.value === "~" ? null : e.target.value })}>
              <option value="">Move to…</option>
              <option value="~">Unsorted</option>
              {view.binders.map((b) => (
                <option key={b.uid} value={b.uid}>
                  {b.name}
                </option>
              ))}
            </select>
            <select aria-label="Set the condition" disabled={locked} value="" onChange={(e) => e.target.value && bulk({ condition: e.target.value })}>
              <option value="">Condition…</option>
              {CONDITIONS.map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
            <select aria-label="Set the language" disabled={locked} value="" onChange={(e) => e.target.value && bulk({ language: e.target.value })}>
              <option value="">Language…</option>
              {LANGUAGES.map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
            <button className="btn" disabled={locked} onClick={() => setPricing(true)}>
              Purchase price…
            </button>
            <button
              className="btn"
              onClick={async () => {
                const text = stacksText(rows.filter((r) => picked.includes(r.uid)));
                try {
                  await navigator.clipboard.writeText(text);
                  setCopied(true);
                  setTimeout(() => setCopied(false), 2000);
                } catch {
                  setMessage("The browser didn't allow copying.");
                }
              }}
            >
              {copied ? "Copied ✓" : "Copy as list"}
            </button>
            {confirmDelete ? (
              <>
                <span>Delete {picked.length} piles?</span>
                <button
                  className="btn danger"
                  disabled={locked}
                  onClick={async () => {
                    setConfirmDelete(false);
                    await bulk({ remove: true });
                  }}
                >
                  Yes, delete
                </button>
                <button className="btn" onClick={() => setConfirmDelete(false)}>
                  No
                </button>
              </>
            ) : (
              <button className="btn danger" disabled={locked} onClick={() => setConfirmDelete(true)}>
                Delete
              </button>
            )}
            <span className="spacer" />
            <button className="link" onClick={() => setSelected(new Set())}>
              Clear selection
            </button>
          </div>
        )}

        {layout === "list" ? (
          <CollTable
            rows={pageRows}
            groups={pageGroups ?? undefined}
            expanded={expanded}
            onExpand={(key) =>
              setExpanded((s) => {
                const n = new Set(s);
                if (n.has(key)) n.delete(key);
                else n.add(key);
                return n;
              })
            }
            onToggleMany={(uids, on) =>
              setSelected((s) => {
                const n = new Set(s);
                for (const u of uids) {
                  if (on) n.add(u);
                  else n.delete(u);
                }
                return n;
              })
            }
            columns={columns}
            use={use}
            currency={view.currency}
            perEuro={view.perEuro}
            binderName={binderName}
            showBinder={pick === ""}
            selected={selected}
            disabled={locked}
            sort={sort}
            onSort={(field) => changeSort([field === sort.field && sorts.length === 1 ? { field, reversed: !sort.reversed } : { field, reversed: false }])}
            onToggle={toggle}
            onToggleAll={(on) => setSelected(on ? new Set([...selected, ...pageRows.map((r) => r.uid)]) : new Set())}
            onOpen={setOpen}
            hover={hover}
          />
        ) : (
          <CollGrid rows={pageRows} currency={view.currency} perEuro={view.perEuro} disabled={busy} onOpen={setOpen} />
        )}
        {rows.length > 0 && (
          <div className="pager">
            <label className="inline small">
              <span className="muted">Rows per page</span>
              <select
                aria-label="Rows per page"
                value={perPage}
                onChange={(e) => {
                  const n = Number(e.target.value);
                  setPerPage(n);
                  setPage(0);
                  writeSaved({ perPage: n });
                }}
              >
                {PER_PAGE.map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
            <span className="spacer" />
            <span className="muted small">
              {pageAt * perPage + 1}–{Math.min((pageAt + 1) * perPage, lines)} of {lines}
            </span>
            <button className="btn" aria-label="Previous page" disabled={pageAt === 0} onClick={() => { setPage(pageAt - 1); window.scrollTo(0, 0); }}>
              ‹ Previous
            </button>
            <button className="btn" aria-label="Next page" disabled={pageAt >= pages - 1} onClick={() => { setPage(pageAt + 1); window.scrollTo(0, 0); }}>
              Next ›
            </button>
          </div>
        )}
        {view.rows.length === 0 && <p className="muted center">The collection is empty.</p>}
        {view.rows.length > 0 && rows.length === 0 && <p className="muted center">No cards match.</p>}
          </>
        )}
      </main>

      {open && (
        <CollCardDialog
          key={open.uid}
          row={view.rows.find((r) => r.uid === open.uid) ?? open}
          view={view}
          busy={busy}
          onClose={() => setOpen(null)}
          onSave={async (e) => {
            if ((await edit(e)) != null) setOpen(null);
          }}
          onRemove={async (uid) => {
            if ((await edit({ type: "remove", uid })) != null) setOpen(null);
          }}
          onOpenOther={setOpen}
        />
      )}
      {adding && (
        <CollAddDialog
          currency={view.currency}
          perEuro={view.perEuro}
          binders={view.binders}
          binder={pick === "" || pick === "~" ? null : pick}
          busy={busy}
          onClose={() => setAdding(false)}
          onAdd={async (e) => {
            if ((await edit(e)) != null) setAdding(false);
          }}
        />
      )}
      {choosingColumns && (
        <ColumnsDialog
          columns={columns}
          onClose={() => setChoosingColumns(false)}
          onSave={(c) => {
            setColumns(c);
            writeSaved({ columns: c });
            setChoosingColumns(false);
          }}
        />
      )}
      {filtering && (
        <FilterDialog
          filter={filter}
          sets={sets}
          currency={view.currency}
          perEuro={view.perEuro}
          onClose={() => setFiltering(false)}
          onApply={(f) => {
            changeFilter(f);
            setFiltering(false);
          }}
        />
      )}
      {exporting && (
        <ExportDialog rows={rows} binders={view.binders} priceType={view.priceType} binderLabel={binderLabel} onClose={() => setExporting(false)} />
      )}
      {sharing && !isWish && !isScans && (
        <ShareDialog
          kind={pick === "" ? "collection" : "binder"}
          target={pick === "" ? "" : pick}
          what={pick === "" ? "your whole collection (not your wishlist, orders or trades)" : `the binder ${binderLabel}`}
          onClose={() => setSharing(false)}
        />
      )}
      {printing && !isWish && !isScans && (
        <PrintCollectionDialog
          rows={rows}
          title={pick === "" ? "Collection" : binderLabel}
          filterText={[search.trim() && `Filtered: ${search.trim()}`, nFilter > 0 && "Filters on"].filter(Boolean).join(" · ")}
          pricesNote={view.pricesNote}
          money={money}
          onClose={() => setPrinting(false)}
        />
      )}
      {importing && (
        <ImportDialog
          binders={view.binders}
          startBinder={pick !== "" && pick !== "~" && view.binders.some((b) => b.uid === pick) ? pick : null}
          onDone={(v, text) => {
            setView(v);
            setImporting(false);
            setNotice(text);
          }}
          onClose={() => setImporting(false)}
        />
      )}
      {precon && (
        <PreconDialog
          // Starts with the binder on show: Unsorted, or a binder (not the whole collection, the wishlist or the scans).
          binder={pick === "~" ? "" : view.binders.some((b) => b.uid === pick) ? pick : undefined}
          onClose={() => setPrecon(false)}
          onDone={(text) => {
            setPrecon(false);
            setNotice(text);
            load();
          }}
        />
      )}
      {sorting && (
        <SortDialog
          initial={sorts}
          onClose={() => setSorting(false)}
          onSave={(levels) => {
            changeSort(levels);
            setSorting(false);
          }}
        />
      )}
      {merging && (pick === "~" || view.binders.some((b) => b.uid === pick)) && (
        <MergeDialog
          from={pick === "~" ? null : pick}
          fromName={binderLabel}
          copies={counts.get(pick) ?? 0}
          binders={view.binders}
          busy={busy}
          onClose={() => setMerging(false)}
          onSave={async (into, deleteSource) => {
            const from = pick === "~" ? null : pick;
            if ((await edit({ type: "mergeBinder", from, into, deleteSource })) != null) {
              setMerging(false);
              changeBinder(into ?? "~");
            }
          }}
        />
      )}
      {deletingBinder && view.binders.some((b) => b.uid === pick) && (
        <DeleteBinderDialog
          name={binderLabel}
          copies={counts.get(pick) ?? 0}
          busy={busy}
          onClose={() => setDeletingBinder(false)}
          onSave={async (deleteCards) => {
            if ((await edit({ type: "deleteBinder", uid: pick, deleteCards })) != null) {
              setDeletingBinder(false);
              changeBinder("");
            }
          }}
        />
      )}
      {arranging && (
        <ArrangeDialog
          keys={view.bar}
          usual={barKeys(view.binders, undefined)}
          binders={view.binders}
          busy={busy}
          onClose={() => setArranging(false)}
          onSave={async (keys) => {
            if ((await edit({ type: "binderOrder", keys })) != null) setArranging(false);
          }}
        />
      )}
      {pricing && (
        <PaidDialog
          piles={picked.length}
          currency={view.currency}
          busy={busy}
          onClose={() => setPricing(false)}
          onSave={async (paid) => {
            setPricing(false);
            await bulk({ paid });
          }}
        />
      )}
      {naming && (
        <NameDialog
          title={naming.uid ? "Rename the binder" : "New binder"}
          start={naming.name}
          busy={busy}
          onClose={() => setNaming(null)}
          onSave={async (name) => {
            const uid = await edit(naming.uid ? { type: "renameBinder", uid: naming.uid, name } : { type: "addBinder", name });
            if (uid != null) {
              setNaming(null);
              if (!naming.uid && uid) changeBinder(uid);
            }
          }}
        />
      )}
    </>
  );
}

function NameDialog({
  title,
  start,
  busy,
  onSave,
  onClose,
}: {
  title: string;
  start: string;
  busy: boolean;
  onSave: (name: string) => void;
  onClose: () => void;
}) {
  const [name, setName] = useState(start);
  const ok = name.trim().length >= 1 && name.trim().length <= 60 && name.trim() !== start;
  return (
    <Dialog title={title} onClose={onClose}>
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (ok) onSave(name.trim());
        }}
      >
        <label className="field">
          <span>Name</span>
          <input autoFocus maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={busy || !ok}>
            Save
          </button>
        </div>
      </form>
    </Dialog>
  );
}
