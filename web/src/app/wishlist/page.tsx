"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AddDialog, type AddRequest } from "@/components/AddDialog";
import { api, formatDate } from "@/components/api";
import { EditDialog } from "@/components/EditDialog";
import { WishRowItem } from "@/components/WishRowItem";
import { format } from "@/lib/money";
import type { WishRow, WishView } from "@/lib/view";
import type { WishEdit } from "@/lib/wishlist";

type Sort = "name" | "value" | "added" | "set";
const SORT_KEY = "mtgtrader.wishlist.sort";
const SORTS: { value: Sort; label: string }[] = [
  { value: "name", label: "Name" },
  { value: "value", label: "Value (high first)" },
  { value: "added", label: "Date added (newest first)" },
  { value: "set", label: "Set" },
];

function byName(a: WishRow, b: WishRow) {
  return a.card.name.localeCompare(b.card.name, "en", { sensitivity: "base" });
}

function sorter(sort: Sort): (a: WishRow, b: WishRow) => number {
  switch (sort) {
    case "value":
      return (a, b) => (b.unitEur ?? -1) * b.quantity - (a.unitEur ?? -1) * a.quantity || byName(a, b);
    case "added":
      return (a, b) => b.addedAt - a.addedAt || byName(a, b);
    case "set":
      return (a, b) =>
        a.card.setCode.localeCompare(b.card.setCode) ||
        a.card.collectorNumber.localeCompare(b.card.collectorNumber, "en", { numeric: true }) ||
        byName(a, b);
    default:
      return byName;
  }
}

export default function WishlistPage() {
  const [view, setView] = useState<WishView | null>(null);
  const [folder, setFolder] = useState("");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<Sort>("name");
  const [editing, setEditing] = useState<WishRow | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(SORT_KEY);
      if (SORTS.some((s) => s.value === saved)) setSort(saved as Sort);
    } catch {
      // Storage may be unavailable; the default sort is fine.
    }
  }, []);

  const goLogin = useCallback(() => window.location.replace("/login"), []);

  const load = useCallback(async () => {
    setLoadError(null);
    const [s, w] = await Promise.all([
      api<{ loggedIn: boolean; folder?: string }>("/api/session"),
      api<WishView>("/api/wishlist"),
    ]);
    if (!s.data.loggedIn || w.status === 401 || w.data.loggedOut) return goLogin();
    if (!s.data.folder || (w.data as { noFolder?: boolean }).noFolder) return window.location.replace("/settings");
    setFolder(s.data.folder ?? "");
    if (w.ok && Array.isArray(w.data.rows)) setView(w.data);
    else setLoadError(w.data.error ?? "Could not load the wishlist.");
  }, [goLogin]);

  useEffect(() => {
    load();
  }, [load]);

  function changeSort(s: Sort) {
    setSort(s);
    try {
      localStorage.setItem(SORT_KEY, s);
    } catch {
      // Not remembered; fine.
    }
  }

  /** Sends one edit; returns true when it worked. The old view stays on an error. */
  async function edit(e: WishEdit): Promise<boolean> {
    setBusy(true);
    setMessage(null);
    const r = await api<WishView>("/api/wishlist", { edit: e });
    setBusy(false);
    if (r.status === 401 || r.data.loggedOut) {
      goLogin();
      return false;
    }
    if (r.ok && Array.isArray(r.data.rows)) {
      setView(r.data);
      return true;
    }
    setMessage(r.data.error ?? "That change did not work.");
    return false;
  }

  async function logout() {
    await api("/api/logout", {});
    goLogin();
  }

  const sections = useMemo(() => {
    if (!view) return null;
    const q = search.trim().toLowerCase();
    const rows = view.rows.filter(
      (r) =>
        !q ||
        [r.card.name, r.card.flavorName ?? "", r.card.setCode, r.card.setName].some((t) => t.toLowerCase().includes(q)),
    );
    const cmp = sorter(sort);
    return {
      toBuy: rows.filter((r) => r.receivedAt == null && r.orderedAt == null).sort(cmp),
      ordered: rows.filter((r) => r.receivedAt == null && r.orderedAt != null).sort(cmp),
      received: rows.filter((r) => r.receivedAt != null).sort((a, b) => b.receivedAt! - a.receivedAt!),
    };
  }, [view, search, sort]);

  if (!view || !sections) {
    return (
      <main className="page">
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
    );
  }

  const open = view.rows.filter((r) => r.receivedAt == null);
  const wanted = open.reduce((n, r) => n + r.quantity, 0);
  const orderedCount = open.filter((r) => r.orderedAt != null).reduce((n, r) => n + r.quantity, 0);
  const toBuyEur = open.filter((r) => r.orderedAt == null).reduce((n, r) => n + (r.unitEur ?? 0) * r.quantity, 0);
  const locked = view.readOnly || busy;

  const list = (title: string, rows: WishRow[]) =>
    rows.length === 0 ? null : (
      <section>
        <h2 className="section-head">
          {title} <span className="muted">({rows.reduce((n, r) => n + r.quantity, 0)})</span>
        </h2>
        <ul className="rows">
          {rows.map((r) => (
            <WishRowItem
              key={r.uid}
              row={r}
              currency={view.currency}
              perEuro={view.perEuro}
              disabled={locked}
              onOpen={setEditing}
              onToggleOrdered={(row, ordered) => edit({ type: "update", uid: row.uid, ordered })}
            />
          ))}
        </ul>
      </section>
    );

  return (
    <>
      <header className="topbar">
        <div className="topbar-in">
          <div>
            <strong>MTG Trader · Wishlist</strong>
            {folder && <div className="muted small">{folder}</div>}
          </div>
          <a className="btn" href="/settings">
            Settings
          </a>
        </div>
      </header>
      <main className="page">
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
        <p className="summary">
          {wanted} cards wanted · {orderedCount} ordered · {format(toBuyEur * view.perEuro, view.currency)} still to buy (
          {view.priceTypeLabel})
        </p>
        {view.sourceNote && <p className="muted small">{view.sourceNote}</p>}
        <p className="muted small">
          {view.pricesDate
            ? `Prices from Cardmarket, ${
                isNaN(Date.parse(view.pricesDate)) ? view.pricesDate : formatDate(Date.parse(view.pricesDate))
              }`
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
          <select aria-label="Sort" value={sort} onChange={(e) => changeSort(e.target.value as Sort)}>
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
          <button className="btn primary" disabled={locked} onClick={() => setAdding(true)}>
            Add card
          </button>
        </div>

        {list("To buy", sections.toBuy)}
        {list("Ordered", sections.ordered)}
        {list("Received", sections.received)}
        {view.rows.length === 0 && <p className="muted center">The wishlist is empty.</p>}
        {view.rows.length > 0 &&
          sections.toBuy.length + sections.ordered.length + sections.received.length === 0 && (
            <p className="muted center">No cards match your search.</p>
          )}
      </main>

      {editing && (
        <EditDialog
          row={editing}
          viewCurrency={view.currency}
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
