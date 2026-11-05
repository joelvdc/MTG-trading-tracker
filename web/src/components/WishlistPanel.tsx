"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AddDialog, type AddRequest } from "@/components/AddDialog";
import { api, formatDate } from "@/components/api";
import { EditDialog } from "@/components/EditDialog";
import { ViewSwitch, type Layout } from "@/components/ViewSwitch";
import { WishGrid } from "@/components/WishGrid";
import { type Section, WishTable } from "@/components/WishTable";
import { compareRows, firstDirection, type Sort, SORT_KEY, SORT_LABELS, type SortKey } from "@/components/wishText";
import { format } from "@/lib/money";
import type { WishRow, WishView } from "@/lib/view";
import type { WishEdit } from "@/lib/wishlist";

const DEFAULT_SORT: Sort = { key: "name", desc: false };

/** The wishlist, shown in the collection when its "Wishlist" chip is picked (as in the app). */
export function WishlistPanel({
  layout,
  onLayout,
  hover,
  onHover,
  onLoggedOut,
  onChanged,
}: {
  layout: Layout;
  onLayout: (l: Layout) => void;
  hover: boolean;
  onHover: (on: boolean) => void;
  onLoggedOut: () => void;
  /** After a change, so the collection can update the Wishlist chip's number. */
  onChanged: () => void;
}) {
  const [view, setView] = useState<WishView | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<Sort>(DEFAULT_SORT);
  const [editing, setEditing] = useState<WishRow | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(SORT_KEY) ?? "null") as Sort | null;
      if (saved && saved.key in SORT_LABELS && typeof saved.desc === "boolean") setSort(saved);
    } catch {
      // Storage may be unavailable; sorted by name is fine.
    }
  }, []);

  const load = useCallback(async () => {
    setLoadError(null);
    const w = await api<WishView>("/api/wishlist");
    if (w.status === 401 || w.data.loggedOut) return onLoggedOut();
    if (w.ok && Array.isArray(w.data.rows)) setView(w.data);
    else setLoadError(w.data.error ?? "Could not load the wishlist.");
  }, [onLoggedOut]);

  useEffect(() => {
    load();
  }, [load]);

  function changeSort(next: Sort) {
    setSort(next);
    try {
      localStorage.setItem(SORT_KEY, JSON.stringify(next));
    } catch {
      // Not remembered; fine.
    }
  }

  /** A column title: the same column turns the order round, another one starts in its usual direction. */
  function sortBy(key: SortKey) {
    changeSort(key === sort.key ? { key, desc: !sort.desc } : { key, desc: firstDirection(key) });
  }

  /** Sends one edit; returns true when it worked. The old view stays on an error. */
  async function edit(e: WishEdit): Promise<boolean> {
    setBusy(true);
    setMessage(null);
    const r = await api<WishView>("/api/wishlist", { edit: e });
    setBusy(false);
    if (r.status === 401 || r.data.loggedOut) {
      onLoggedOut();
      return false;
    }
    if (r.ok && Array.isArray(r.data.rows)) {
      setView(r.data);
      onChanged();
      return true;
    }
    setMessage(r.data.error ?? "That change did not work.");
    return false;
  }

  const sections = useMemo(() => {
    if (!view) return null;
    const q = search.trim().toLowerCase();
    const rows = view.rows.filter(
      (r) => !q || [r.card.name, r.card.flavorName ?? "", r.card.setCode, r.card.setName].some((t) => t.toLowerCase().includes(q)),
    );
    const cmp = compareRows(sort);
    const out: Section[] = [
      { title: "To buy", rows: rows.filter((r) => r.receivedAt == null && r.orderedAt == null).sort(cmp) },
      { title: "Ordered", rows: rows.filter((r) => r.receivedAt == null && r.orderedAt != null).sort(cmp) },
      { title: "Received", rows: rows.filter((r) => r.receivedAt != null).sort((a, b) => b.receivedAt! - a.receivedAt!) },
    ];
    return out;
  }, [view, search, sort]);

  if (!view || !sections) {
    return loadError ? (
      <div className="stack">
        <div className="bar error">{loadError}</div>
        <div className="inline">
          <button className="btn" onClick={load}>
            Try again
          </button>
        </div>
      </div>
    ) : (
      <p className="muted center">Loading the wishlist…</p>
    );
  }

  const open = view.rows.filter((r) => r.receivedAt == null);
  const wanted = open.reduce((n, r) => n + r.quantity, 0);
  const orderedCount = open.filter((r) => r.orderedAt != null).reduce((n, r) => n + r.quantity, 0);
  const toBuyEur = open.filter((r) => r.orderedAt == null).reduce((n, r) => n + (r.unitEur ?? 0) * r.quantity, 0);
  const locked = view.readOnly || busy;

  return (
    <>
      {message && (
        <div className="bar error" role="alert">
          {message}
          <button className="link" onClick={() => setMessage(null)}>
            Dismiss
          </button>
        </div>
      )}
      <p className="summary">
        {wanted} cards wanted · {orderedCount} ordered · {format(toBuyEur * view.perEuro, view.currency)} still to buy (
        {view.priceTypeLabel})
      </p>
      {view.sourceNote && <p className="muted small">{view.sourceNote}</p>}
      <p className="muted small">
        {view.pricesDate
          ? `Prices from Cardmarket, ${isNaN(Date.parse(view.pricesDate)) ? view.pricesDate : formatDate(Date.parse(view.pricesDate))}`
          : "Prices: Scryfall's saved prices (the daily price file isn't there yet)"}
      </p>

      <div className="controls">
        <input
          type="search"
          aria-label="Search the wishlist"
          placeholder="Search name or set"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {layout === "images" && (
          <select
            aria-label="Sort"
            value={`${sort.key}:${sort.desc ? "desc" : "asc"}`}
            onChange={(e) => {
              const [key, dir] = e.target.value.split(":");
              changeSort({ key: key as SortKey, desc: dir === "desc" });
            }}
          >
            {(Object.keys(SORT_LABELS) as SortKey[]).flatMap((key) =>
              [firstDirection(key), !firstDirection(key)].map((desc) => (
                <option key={`${key}:${desc}`} value={`${key}:${desc ? "desc" : "asc"}`}>
                  {SORT_LABELS[key]} {desc ? "↓" : "↑"}
                </option>
              )),
            )}
          </select>
        )}
        <ViewSwitch layout={layout} onLayout={onLayout} hover={hover} onHover={onHover} />
        <button className="btn primary" disabled={locked} onClick={() => setAdding(true)}>
          Add card
        </button>
      </div>

      {layout === "list" ? (
        <WishTable
          sections={sections}
          currency={view.currency}
          perEuro={view.perEuro}
          disabled={locked}
          sort={sort}
          onSort={sortBy}
          onOpen={setEditing}
          onToggleOrdered={(row, ordered) => edit({ type: "update", uid: row.uid, ordered })}
          hover={hover}
        />
      ) : (
        <WishGrid sections={sections} currency={view.currency} perEuro={view.perEuro} disabled={locked} onOpen={setEditing} />
      )}
      {view.rows.length === 0 && <p className="muted center">The wishlist is empty.</p>}
      {view.rows.length > 0 && sections.every((x) => x.rows.length === 0) && <p className="muted center">No cards match your search.</p>}

      {editing && (
        <EditDialog
          row={editing}
          viewCurrency={view.currency}
          perEuro={view.perEuro}
          busy={busy}
          onClose={() => setEditing(null)}
          onSave={async (e) => {
            if (await edit(e)) setEditing(null);
          }}
          onRemove={async (uid) => {
            if (await edit({ type: "remove", uid })) setEditing(null);
          }}
        />
      )}
      {adding && (
        <AddDialog
          currency={view.currency}
          perEuro={view.perEuro}
          busy={busy}
          onClose={() => setAdding(false)}
          onAdd={async (req: AddRequest) => {
            if (await edit({ type: "add", ...req })) setAdding(false);
          }}
        />
      )}
    </>
  );
}
