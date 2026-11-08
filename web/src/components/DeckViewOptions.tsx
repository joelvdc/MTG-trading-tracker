"use client";

import { useEffect, useRef, useState } from "react";
import { GROUP_LABELS, type DeckGroup, type DeckSort } from "@/lib/decklayout";

const KEY = "mtg.deckView";

/** What the deck page shows and how it lays the cards out, remembered per browser (not per deck). */
export interface DeckPrefs {
  cost: boolean;
  price: boolean;
  owned: boolean;
  sort: DeckSort;
  descending: boolean;
  group: DeckGroup;
  /** Where the maybeboard goes: a third column beside the deck, or under it. */
  maybe: "SIDE" | "BELOW";
  /** Whether the maybeboard is split into groups the way the deck is (Group by). */
  maybeSplit: boolean;
}

export const DEFAULT_PREFS: DeckPrefs = { cost: true, price: true, owned: true, sort: "MANA_VALUE", descending: false, group: "CATEGORY", maybe: "SIDE", maybeSplit: false };

/** Reads the remembered choices; anything missing or unreadable keeps its default. */
export function parsePrefs(text: string | null): DeckPrefs {
  try {
    const p = JSON.parse(text ?? "{}") as Partial<DeckPrefs>;
    return {
      cost: typeof p.cost === "boolean" ? p.cost : DEFAULT_PREFS.cost,
      price: typeof p.price === "boolean" ? p.price : DEFAULT_PREFS.price,
      owned: typeof p.owned === "boolean" ? p.owned : DEFAULT_PREFS.owned,
      sort: p.sort === "NAME" || p.sort === "MANA_VALUE" ? p.sort : DEFAULT_PREFS.sort,
      descending: typeof p.descending === "boolean" ? p.descending : DEFAULT_PREFS.descending,
      group: p.group && p.group in GROUP_LABELS ? p.group : DEFAULT_PREFS.group,
      maybe: p.maybe === "BELOW" || p.maybe === "SIDE" ? p.maybe : DEFAULT_PREFS.maybe,
      maybeSplit: typeof p.maybeSplit === "boolean" ? p.maybeSplit : DEFAULT_PREFS.maybeSplit,
    };
  } catch {
    return DEFAULT_PREFS;
  }
}

export function useDeckPrefs(): [DeckPrefs, (change: Partial<DeckPrefs>) => void] {
  const [prefs, setPrefs] = useState<DeckPrefs>(DEFAULT_PREFS);
  useEffect(() => {
    try {
      setPrefs(parsePrefs(localStorage.getItem(KEY)));
    } catch {
      // Storage unavailable: the defaults stay.
    }
  }, []);
  return [
    prefs,
    (change) => {
      const next = { ...prefs, ...change };
      setPrefs(next);
      try {
        localStorage.setItem(KEY, JSON.stringify(next));
      } catch {
        // Not remembered; fine.
      }
    },
  ];
}

/** The ⚙ button and its small menu (like Archidekt's "Enabled columns"): which columns show, and the card on hover. */
export function ViewOptionsMenu({
  prefs,
  onChange,
  hoverOn,
  onHover,
}: {
  prefs: DeckPrefs;
  onChange: (change: Partial<DeckPrefs>) => void;
  hoverOn: boolean;
  onHover: (on: boolean) => void;
}) {
  const box = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    // A click elsewhere (or Esc) closes the menu.
    const away = (e: Event) => {
      if (box.current?.open && !box.current.contains(e.target as Node)) box.current.open = false;
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape" && box.current) box.current.open = false;
    };
    document.addEventListener("mousedown", away);
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("mousedown", away);
      document.removeEventListener("keydown", key);
    };
  }, []);
  const item = (label: string, on: boolean, set: (v: boolean) => void) => (
    <label className="check small">
      <input type="checkbox" checked={on} onChange={(e) => set(e.target.checked)} />
      <span>{label}</span>
    </label>
  );
  return (
    <details className="menu view-options" ref={box}>
      <summary className="btn small-btn" aria-label="View options" title="View options">
        ⚙
      </summary>
      <div className="menu-items options">
        <strong className="small">Enabled columns</strong>
        {item("Mana cost", prefs.cost, (v) => onChange({ cost: v }))}
        {item("Price", prefs.price, (v) => onChange({ price: v }))}
        {item("Collection info", prefs.owned, (v) => onChange({ owned: v }))}
        <strong className="small">Maybeboard</strong>
        {(
          [
            ["SIDE", "Side column"],
            ["BELOW", "Below the deck"],
          ] as const
        ).map(([v, label]) => (
          <label key={v} className="check small">
            <input type="radio" name="deck-maybe" checked={prefs.maybe === v} onChange={() => onChange({ maybe: v })} />
            <span>{label}</span>
          </label>
        ))}
        {item("Split into groups", prefs.maybeSplit, (v) => onChange({ maybeSplit: v }))}
        <strong className="small">Pictures</strong>
        {item("Card on hover", hoverOn, onHover)}
      </div>
    </details>
  );
}

/** A panel fixed to the right of the screen (full width on a phone), closed with its button or Esc. */
export function SidePanel({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") close.current();
    };
    document.addEventListener("keydown", key);
    return () => document.removeEventListener("keydown", key);
  }, []);
  return (
    <aside className="side-panel" aria-label={title}>
      <div className="side-panel-head">
        <h2>{title}</h2>
        <button className="btn small-btn" onClick={onClose} aria-label={`Close ${title}`}>
          Close
        </button>
      </div>
      <div className="side-panel-body">{children}</div>
    </aside>
  );
}
