"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/components/api";
import { CollAddDialog } from "@/components/CollAddDialog";
import { CollCardDialog } from "@/components/CollCardDialog";
import { CollGrid } from "@/components/CollGrid";
import { CollTable } from "@/components/CollTable";
import { Dialog } from "@/components/Dialog";
import { ExportDialog } from "@/components/ExportDialog";
import { FilterDialog } from "@/components/FilterDialog";
import { TopBar } from "@/components/Nav";
import { CONDITIONS, LANGUAGES } from "@/lib/cards";
import type { CollectionEdit } from "@/lib/collection";
import type { CollectionView, CollRow } from "@/lib/collview";
import {
  comparator,
  deckCardNames,
  EMPTY_FILTER,
  filterCount,
  matchesFilter,
  matchesText,
  SORT_FIELDS,
  type Filter,
  type SortField,
  type SortLevel,
} from "@/lib/colquery";
import { format } from "@/lib/money";

type Layout = "list" | "images";
/** "" = everything, "~" = Unsorted, otherwise a binder's uid. */
type BinderPick = string;

const KEY = "mtg.collection";
const PER_PAGE = [25, 50, 100];
const DEFAULT_SORT: SortLevel = { field: "NAME", reversed: false };

interface Saved {
  sort?: SortLevel;
  layout?: Layout;
  binder?: BinderPick;
  filter?: Filter;
  perPage?: number;
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
  const [sort, setSort] = useState<SortLevel>(DEFAULT_SORT);
  const [layout, setLayout] = useState<Layout>("list");
  const [binder, setBinder] = useState<BinderPick>("");
  const [filter, setFilter] = useState<Filter>(EMPTY_FILTER);
  const [perPage, setPerPage] = useState(50);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [open, setOpen] = useState<CollRow | null>(null);
  const [adding, setAdding] = useState(false);
  const [filtering, setFiltering] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [naming, setNaming] = useState<{ uid: string | null; name: string } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const lookedUp = useRef(false);

  useEffect(() => {
    const s = readSaved();
    if (s.sort && SORT_FIELDS.some((f) => f.field === s.sort!.field)) setSort(s.sort);
    if (s.layout === "images") setLayout("images");
    if (s.perPage && PER_PAGE.includes(s.perPage)) setPerPage(s.perPage);
    const params = new URLSearchParams(window.location.search);
    const fromUrl = filterFrom(params.get("filter"));
    const binderUrl = params.get("binder");
    if (fromUrl || binderUrl != null) {
      setFilter(fromUrl ?? EMPTY_FILTER);
      setBinder(binderUrl ?? "");
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
  }, [load]);

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

  function changeSort(next: SortLevel) {
    setSort(next);
    setPage(0);
    writeSaved({ sort: next });
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

  const deckNames = useMemo(() => deckCardNames(view?.decks ?? []), [view]);

  const binderName = useCallback(
    (uid: string | null) => (uid ? (view?.binders.find((b) => b.uid === uid)?.name ?? "Unsorted") : "Unsorted"),
    [view],
  );

  /** The binder picked still exists (it may have been deleted on a phone). */
  const pick: BinderPick = view && binder && binder !== "~" && !view.binders.some((b) => b.uid === binder) ? "" : binder;

  const inBinder = useMemo(() => {
    if (!view) return [];
    if (pick === "") return view.rows;
    const want = pick === "~" ? null : pick;
    return view.rows.filter((r) => r.binder === want);
  }, [view, pick]);

  const rows = useMemo(() => {
    const words = search.trim();
    const out = inBinder.filter((r) => (!words || matchesText(r, words)) && matchesFilter(filter, r, deckNames));
    return out.sort(comparator([sort]));
  }, [inBinder, search, filter, deckNames, sort]);

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
  const pages = Math.max(1, Math.ceil(rows.length / perPage));
  const pageAt = Math.min(page, pages - 1);
  const pageRows = rows.slice(pageAt * perPage, (pageAt + 1) * perPage);
  const copies = rows.reduce((n, r) => n + r.quantity, 0);
  const value = rows.reduce((n, r) => n + (r.unitEur ?? 0) * r.quantity, 0);
  const locked = view.readOnly || busy;
  const nFilter = filterCount(filter);
  const sortInfo = SORT_FIELDS.find((f) => f.field === sort.field) ?? SORT_FIELDS[0];
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
        {message && (
          <div className="bar error" role="alert">
            {message}
            <button className="link" onClick={() => setMessage(null)}>
              Dismiss
            </button>
          </div>
        )}
        {lookup && <div className="bar">{lookup}</div>}

        <div className="binder-bar" role="tablist" aria-label="Binders">
          {[
            { key: "", name: "All", n: [...counts.values()].reduce((a, b) => a + b, 0) },
            { key: "~", name: "Unsorted", n: counts.get("~") ?? 0 },
            ...view.binders.map((b) => ({ key: b.uid, name: b.name, n: counts.get(b.uid) ?? 0 })),
          ].map((b) => (
            <button
              key={b.key || "all"}
              role="tab"
              aria-selected={pick === b.key}
              className={pick === b.key ? "chip on" : "chip"}
              onClick={() => changeBinder(b.key)}
            >
              {b.name} <span className="count">{b.n}</span>
            </button>
          ))}
          <button className="chip add" disabled={locked} onClick={() => setNaming({ uid: null, name: "" })}>
            ＋ Binder
          </button>
          {pick !== "" && pick !== "~" && (
            <button className="chip add" disabled={locked} onClick={() => setNaming({ uid: pick, name: binderName(pick) })}>
              Rename
            </button>
          )}
        </div>

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
          {layout === "images" && (
          <select
            aria-label="Sort"
            title={sort.reversed ? sortInfo.backward : sortInfo.forward}
            value={`${sort.field}:${sort.reversed ? "r" : "f"}`}
            onChange={(e) => {
              const [field, dir] = e.target.value.split(":");
              changeSort({ field: field as SortField, reversed: dir === "r" });
            }}
          >
            {SORT_FIELDS.flatMap((f) =>
              [false, true].map((rev) => (
                <option key={`${f.field}:${rev}`} value={`${f.field}:${rev ? "r" : "f"}`}>
                  {f.label}: {rev ? f.backward : f.forward}
                </option>
              )),
            )}
          </select>
          )}
          <div className="seg" role="group" aria-label="View">
            <button
              className={layout === "list" ? "on" : undefined}
              aria-pressed={layout === "list"}
              aria-label="View as list"
              title="View as list"
              onClick={() => changeLayout("list")}
            >
              <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                <circle cx="5" cy="6" r="1.6" fill="currentColor" />
                <circle cx="5" cy="12" r="1.6" fill="currentColor" />
                <circle cx="5" cy="18" r="1.6" fill="currentColor" />
                <path d="M9.5 6h10M9.5 12h10M9.5 18h10" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
              </svg>
            </button>
            <button
              className={layout === "images" ? "on" : undefined}
              aria-pressed={layout === "images"}
              aria-label="View as images"
              title="View as images"
              onClick={() => changeLayout("images")}
            >
              <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                <rect x="9" y="3" width="11" height="15" rx="2" fill="currentColor" opacity="0.55" transform="rotate(12 14.5 10.5)" />
                <rect x="4" y="5" width="11" height="15" rx="2" fill="currentColor" />
              </svg>
            </button>
          </div>
          <button className="btn primary" disabled={locked} onClick={() => setAdding(true)}>
            Add card
          </button>
          <a className="btn" href={pick ? `/collection/stats?binder=${encodeURIComponent(pick)}` : "/collection/stats"}>
            Stats
          </a>
          <button className="btn" onClick={() => setExporting(true)}>
            Export CSV
          </button>
        </div>

        <p className="muted small showing">
          Showing {nFilter > 0 || search.trim() ? `${rows.length} of ${inBinder.length}` : rows.length} piles{" "}
          {pick === "" ? "in the whole collection" : pick === "~" ? "not in a binder" : `in “${binderLabel}”`}, sorted by{" "}
          {sortInfo.label.toLowerCase()} ({(sort.reversed ? sortInfo.backward : sortInfo.forward).toLowerCase()}).
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
            currency={view.currency}
            perEuro={view.perEuro}
            binderName={binderName}
            showBinder={pick === ""}
            selected={selected}
            disabled={locked}
            sort={sort}
            onSort={(field) => changeSort(field === sort.field ? { field, reversed: !sort.reversed } : { field, reversed: false })}
            onToggle={toggle}
            onToggleAll={(on) => setSelected(on ? new Set([...selected, ...pageRows.map((r) => r.uid)]) : new Set())}
            onOpen={setOpen}
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
              {pageAt * perPage + 1}–{Math.min((pageAt + 1) * perPage, rows.length)} of {rows.length}
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
