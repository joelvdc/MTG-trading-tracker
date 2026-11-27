"use client";

import { useEffect, useState } from "react";
import type { SyncDeck } from "@/lib/deckshared";
import { api } from "./api";

/** Where the last undoable deck change is kept, so it survives moving from a deck's page to the decks list. */
const KEY = "mtg.deckUndo";
/** How long an undo is offered. */
const LIFE_MS = 30_000;

export interface UndoEntry {
  deckId: number;
  /** The whole deck as the server saved it before the change (lib/deckshared's SyncDeck). */
  snapshot: SyncDeck;
  /** What happened, for the bar: "Deck deleted", "Copies changed"… */
  label: string;
  at: number;
}

// The entry of this page (also when the browser keeps no session storage), and who wants to hear about changes.
let memory: UndoEntry | null = null;
const listeners = new Set<() => void>();

function notify(): void {
  for (const fn of listeners) fn();
}

/** The browser's session storage, when it has one it lets us use. */
function store(): Storage | null {
  try {
    return (globalThis as { sessionStorage?: Storage }).sessionStorage ?? null;
  } catch {
    return null;
  }
}

/** Whether [value] is a saved deck (a SyncDeck: a deck with its cards). */
export function snapshotOf(value: unknown): SyncDeck | null {
  if (!value || typeof value !== "object") return null;
  const d = value as { deck?: unknown; cards?: unknown };
  return d.deck && typeof d.deck === "object" && Array.isArray(d.cards) ? (value as SyncDeck) : null;
}

/** The entry kept in session storage, if it still makes sense. */
function kept(): UndoEntry | null {
  const s = store();
  if (!s) return null;
  try {
    const raw = s.getItem(KEY);
    if (!raw) return null;
    const e = JSON.parse(raw) as Partial<UndoEntry>;
    const snapshot = snapshotOf(e.snapshot);
    if (!snapshot || typeof e.deckId !== "number" || typeof e.label !== "string" || typeof e.at !== "number") return null;
    return { deckId: e.deckId, snapshot, label: e.label, at: e.at };
  } catch {
    // Storage that can't be read (or holds something else): no undo to offer.
    return null;
  }
}

/** Offers an undo of [deckId] back to [snapshot], replacing any older one. */
export function offerUndo(deckId: number, snapshot: SyncDeck, label: string): void {
  const entry: UndoEntry = { deckId, snapshot, label, at: Date.now() };
  memory = entry;
  const s = store();
  if (s) {
    try {
      s.setItem(KEY, JSON.stringify(entry));
    } catch {
      // No room in session storage: the entry stays in memory for this page.
    }
  }
  notify();
}

/** The change that can still be undone now, or null (older than 30 seconds, or none). */
export function currentUndo(now = Date.now()): UndoEntry | null {
  const entry = memory ?? kept();
  return entry && now - entry.at < LIFE_MS ? entry : null;
}

/** Stops offering the undo. */
export function clearUndo(): void {
  memory = null;
  const s = store();
  if (s) {
    try {
      s.removeItem(KEY);
    } catch {
      // Nothing to clear.
    }
  }
  notify();
}

/** Calls [fn] whenever the undo changes; returns the way to stop hearing about it. */
export function subscribeUndo(fn: () => void): () => void {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
}

/** What happened, in a few words for the bar, from a deck action's body. */
export function labelFor(body: Record<string, unknown>): string {
  switch (body.action) {
    case "set-quantity":
      return "Copies changed";
    case "printing":
      return "Printing changed";
    case "remove":
      return "Removed";
    case "move":
      return "Moved";
    case "auto-all":
      return "Cards auto-categorised";
    case "add-card":
      return "Card added";
    case "add-maybe":
      return "Added to the maybeboard";
    case "category-order":
      return "Categories reordered";
    case "rename-category":
      return "Category renamed";
    case "delete-category":
      return "Category deleted";
    case "add-category":
      return "Category added";
    case "hide-category":
      return "Category hidden or shown";
    case "rename":
      return "Deck renamed";
    case "delete":
      return "Deck deleted";
    case "retire":
      return "Deck retired or brought back";
    case "bulk-quantity":
      return "Copies changed";
    case "bulk-finish":
      return "Finish changed";
    case "bulk-printing":
      return "Printings changed";
    case "paste":
      return "Cards added";
    case "set-primer":
      return "Primer changed";
    case "set-card-note":
      return "Card note changed";
    case "snapshot-take":
      return "Snapshot taken";
    case "snapshot-rename":
      return "Snapshot renamed";
    case "snapshot-delete":
      return "Snapshot deleted";
    case "add-package":
      return "Package added";
    case "remove-package":
      return "Package removed";
    default:
      return "Deck changed";
  }
}

/**
 * The bar that offers the last deck change back ("Deck changed. Undo Dismiss"), on a deck's page (only that deck's
 * changes) or on the decks list (any). It counts down by itself and disappears after 30 seconds.
 */
export function UndoBar({ deckId, onRestored }: { deckId?: number; onRestored: (restoredDeckId: number) => void | Promise<void> }) {
  // The store is read at [now]: 0 until the page is on screen, so the server's answer and the browser's first one agree.
  const [now, setNow] = useState(0);
  const [busy, setBusy] = useState(false);
  // The server's complaint about this undo (kept with its time, so an older one never shows under a newer bar).
  const [failure, setFailure] = useState<{ at: number; text: string } | null>(null);

  useEffect(() => {
    const tick = () => setNow(Date.now());
    tick();
    return subscribeUndo(tick);
  }, []);

  const entry = now ? currentUndo(now) : null;
  // While an undo is on offer, look again every second, so the bar goes when the 30 seconds are over.
  useEffect(() => {
    if (!entry) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [entry]);

  if (!entry || (deckId !== undefined && deckId !== entry.deckId)) return null;

  async function undo() {
    if (!entry) return;
    setBusy(true);
    setFailure(null);
    const r = await api<{ ok?: boolean }>(`/api/decks/${entry.deckId}`, { action: "restore", snapshot: entry.snapshot });
    setBusy(false);
    if (!r.ok) {
      setFailure({ at: entry.at, text: r.data.error ?? "That did not work." });
      return;
    }
    clearUndo();
    await onRestored(entry.deckId);
  }

  return (
    <>
      <div className="bar ok" role="status">
        {entry.label}.{" "}
        <button className="link" disabled={busy} onClick={undo}>
          Undo
        </button>{" "}
        <button
          className="link"
          disabled={busy}
          onClick={() => {
            setFailure(null);
            clearUndo();
          }}
        >
          Dismiss
        </button>
      </div>
      {failure && failure.at === entry.at && <div className="bar error">{failure.text}</div>}
    </>
  );
}
